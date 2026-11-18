package com.example.tratohecho.data.repository

import androidx.room.withTransaction
import com.example.tratohecho.data.database.*
import com.example.tratohecho.data.model.*
import com.example.tratohecho.data.remote.*
import kotlinx.coroutines.CancellationException
import java.time.LocalDate

class CotizacionRepository(
    private val database: AppDatabase,
    private val dao: CotizacionDao,
    private val items: ItemDao,
    private val clienteDao: ClienteDao,
    private val adminDao: AdminDao,
    private val api: CommercialApi
) {
    fun observar(tenant: Long) = dao.observar(tenant)
    fun observarUna(id: Long) = dao.observarUna(id)
    fun items(id: Long) = items.observar(id)
    suspend fun obtener(id: Long) = dao.obtener(id)
    suspend fun obtenerItems(id: Long) = items.obtener(id)

    suspend fun guardarConItems(cotizacion: Cotizacion, detalle: List<ItemCotizacion>, online: Boolean): Long {
        val id = database.withTransaction {
            val value = dao.guardar(cotizacion.copy(sync = EstadoSync.PENDIENTE))
            items.eliminarDeCotizacion(value)
            detalle.forEach { items.guardar(it.copy(id = 0, cotizacionId = value)) }
            value
        }
        if (online) push(id)
        return id
    }

    suspend fun cambiarEstado(id: Long, estado: EstadoCotizacion) {
        val quote = dao.obtener(id) ?: return
        dao.guardar(quote.copy(estado = estado, sync = EstadoSync.PENDIENTE))
        push(id)
    }

    private suspend fun push(id: Long) {
        val quote = dao.obtener(id) ?: return
        try {
            dao.cambiarSync(id, EstadoSync.SINCRONIZANDO)
            val client = clienteDao.obtener(quote.clienteId)
            val customerUuid = client?.remoteId ?: error("Primero sincroniza el cliente de esta cotización.")
            val body = QuoteMapping.toBody(quote, items.obtener(id), customerUuid, adminDao.impuestosLista(quote.tenantId))
            // El servicio solo permite editar borradores. No actualizar una cotización ENVIADA.
            var remote = if (quote.remoteId == null) {
                api.createQuote(Idempotency.key("cotizacion", quote.tenantId, id), body)
            } else {
                val current = api.quote(quote.remoteId)
                if (current.status == "BORRADOR" && quote.estado == EstadoCotizacion.BORRADOR)
                    api.updateQuote(current.id, body) else current
            }
            // Persistir UUID antes del PATCH: evita duplicados si falla la transición.
            dao.guardar(quote.copy(remoteId = remote.id, sync = EstadoSync.SINCRONIZANDO))
            for (next in QuoteMapping.nextStatus(remote.status, quote.estado)) {
                remote = api.status(remote.id, QuoteStatusBody(next))
            }
            dao.guardar(quote.copy(remoteId = remote.id, sync = EstadoSync.SINCRONIZADO))
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { dao.cambiarSync(id, EstadoSync.ERROR) }
    }

    suspend fun sincronizarPendientes(tenant: Long, owner: String = "") {
        dao.pendientes(tenant).forEach { push(it.id) }
        api.quotes().forEach { remote ->
            val current = dao.porRemoteId(tenant, remote.id)
            if (current != null && current.sync != EstadoSync.SINCRONIZADO) return@forEach
            val client = clienteDao.porRemoteId(tenant, remote.customerId) ?: return@forEach
            val date = remote.createdAt?.take(10) ?: LocalDate.now().toString()
            val days = remote.validUntil?.let { runCatching { java.time.temporal.ChronoUnit.DAYS.between(LocalDate.parse(date), LocalDate.parse(it)).toInt().coerceAtLeast(1) }.getOrNull() } ?: 15
            val cotizacion = Cotizacion(
                id = current?.id ?: 0, tenantId = tenant,
                propietarioCorreo = current?.propietarioCorreo.orEmpty(),
                clienteId = client.id, clienteNombre = client.nombre,
                fecha = current?.fecha?.ifBlank { date } ?: date,
                vigenciaDias = days, moneda = remote.currency,
                tipoCambio = current?.tipoCambio ?: 1.0,
                // Los unitPrice remotos ya están descontados, no volver a aplicar descuento.
                descuentoPct = 0.0,
                impuestoPct = remote.items.firstOrNull()?.taxRate ?: 0.0,
                observaciones = remote.notes.orEmpty(),
                estado = if (current?.estado == EstadoCotizacion.FACTURADA && remote.status == "ACEPTADA") EstadoCotizacion.FACTURADA else QuoteMapping.parseStatus(remote.status),
                remoteId = remote.id, sync = EstadoSync.SINCRONIZADO
            )
            database.withTransaction {
                val localId = dao.guardar(cotizacion)
                items.eliminarDeCotizacion(localId)
                remote.items.forEach { line ->
                    items.guardar(ItemCotizacion(
                        cotizacionId = localId, descripcion = line.itemName,
                        cantidad = line.quantity, precioUnitario = line.unitPrice,
                        unidad = line.description?.removePrefix("Unidad: ")?.takeIf { it != line.description } ?: "UN",
                        catalogItemRemoteId = line.catalogItemId
                    ))
                }
            }
        }
    }

    suspend fun cargarSeguimientos(id: Long): List<FollowUpDto> {
        val uuid = dao.obtener(id)?.remoteId ?: return emptyList()
        return api.followUps(uuid)
    }

    suspend fun registrarSeguimiento(id: Long, channel: String, note: String): FollowUpDto {
        require(channel in setOf("TELEFONO", "CORREO", "WHATSAPP", "REUNION", "OTRO")) { "Canal no válido." }
        require(note.isNotBlank() && note.length <= 3000) { "Escribe una nota de hasta 3000 caracteres." }
        val uuid = dao.obtener(id)?.remoteId ?: error("Primero sincroniza esta cotización.")
        return api.addFollowUp(uuid, FollowUpBody(channel, note))
    }

    suspend fun reintentar(id: Long, online: Boolean) {
        if (!online) { dao.cambiarSync(id, EstadoSync.PENDIENTE); return }
        push(id)
    }
}
