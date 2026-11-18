package com.example.tratohecho.data.repository

import com.example.tratohecho.data.database.CotizacionDao
import com.example.tratohecho.data.database.FacturacionDao
import com.example.tratohecho.data.model.*
import com.example.tratohecho.data.remote.*
import java.time.LocalDate

class FacturacionRepository(
    private val dao: FacturacionDao,
    private val cotizacionDao: CotizacionDao,
    private val api: BillingApi
) {
    fun ordenes(t: Long) = dao.ordenes(t)
    fun facturas(t: Long) = dao.facturas(t)
    // Las órdenes de compra NO cuentan con endpoint en el backend entregado.
    suspend fun guardarOrden(x: OrdenCompra) = dao.guardarOrden(x)

    suspend fun refrescar(tenant: Long) {
        api.invoices().forEach { dto -> guardarRemota(tenant, dto) }
    }

    private suspend fun guardarRemota(tenant: Long, remote: InvoiceDto): Factura? {
        val quote = cotizacionDao.porRemoteId(tenant, remote.quoteId) ?: return null
        val previous = dao.facturaRemota(tenant, remote.id)
        val invoice = Factura(id = previous?.id ?: 0, tenantId = tenant,
            cotizacionId = quote.id, numero = remote.folio, fecha = remote.issuedDate,
            observaciones = remote.notes.orEmpty(), remoteId = remote.id,
            estado = remote.status, vencimiento = remote.dueDate)
        dao.guardarFactura(invoice)
        return invoice
    }

    suspend fun crearFactura(tenant: Long, cotizacionId: Long, fecha: String, vence: String?, notes: String): Factura {
        val quote = cotizacionDao.obtener(cotizacionId) ?: error("Cotización no encontrada.")
        require(quote.tenantId == tenant) { "Cotización fuera del tenant actual." }
        require(quote.estado == EstadoCotizacion.ACEPTADA || quote.estado == EstadoCotizacion.FACTURADA) {
            "Solo se pueden facturar cotizaciones aceptadas."
        }
        val uuid = quote.remoteId ?: error("La cotización debe sincronizarse antes de facturar.")
        LocalDate.parse(fecha)
        vence?.let { LocalDate.parse(it) }
        // Billing no ofrece Idempotency-Key. Consultar antes evita duplicados en reintentos.
        val existing = api.invoices().firstOrNull { it.quoteId == uuid && it.status != "ANULADA" }
        if (existing != null) {
            guardarRemota(tenant, existing)
            error("Ya existe la factura ${existing.folio} para esta cotización.")
        }
        val remote = api.createInvoice(CreateInvoiceBody(uuid, fecha, vence, notes.takeIf { it.isNotBlank() }))
        return guardarRemota(tenant, remote) ?: error("No fue posible asociar la factura con la cotización local.")
    }

    suspend fun cambiarEstado(tenant: Long, facturaId: Long, nuevoEstado: String) {
        val factura = dao.facturaPorId(facturaId) ?: error("Factura no encontrada.")
        require(factura.tenantId == tenant) { "Factura fuera del tenant actual." }
        val uuid = factura.remoteId ?: error("Esta factura local no está asociada al backend.")
        require(InvoiceTransitions.canMove(factura.estado, nuevoEstado)) { "Transición no permitida: ${factura.estado} a $nuevoEstado" }
        val remote = api.invoiceStatus(uuid, InvoiceStatusBody(nuevoEstado))
        guardarRemota(tenant, remote)
    }

    suspend fun actualizar(tenant: Long, facturaId: Long, vence: String?, notes: String) {
        val factura = dao.facturaPorId(facturaId) ?: error("Factura no encontrada.")
        require(factura.tenantId == tenant) { "Factura fuera del tenant actual." }
        val uuid = factura.remoteId ?: error("Esta factura local no está asociada al backend.")
        vence?.let { LocalDate.parse(it) }
        val remote = api.updateInvoice(uuid, UpdateInvoiceBody(vence, notes))
        guardarRemota(tenant, remote)
    }
}
