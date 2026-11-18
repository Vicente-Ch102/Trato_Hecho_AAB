package com.example.tratohecho.data.remote

import com.example.tratohecho.data.model.Cotizacion
import com.example.tratohecho.data.model.EstadoCotizacion
import com.example.tratohecho.data.model.Impuesto
import com.example.tratohecho.data.model.ItemCotizacion
import java.time.LocalDate

/** Diferencias reales: el backend no recibe unidad, descuento o tipo de cambio como campos propios. */
object QuoteMapping {
    fun toBody(
        quote: Cotizacion,
        lines: List<ItemCotizacion>,
        customerUuid: String,
        taxes: List<Impuesto>
    ): QuoteBody {
        require(lines.isNotEmpty()) { "La cotización requiere al menos un ítem." }
        val taxUuid = if (quote.impuestoPct == 0.0) null else (
            taxes.firstOrNull { it.activo && it.remoteId != null && kotlin.math.abs(it.porcentaje - quote.impuestoPct) < 0.0001 }?.remoteId
                ?: throw IllegalArgumentException("Sincroniza un impuesto de ${quote.impuestoPct}% antes de subir la cotización.")
        )
        val descuento = (1.0 - quote.descuentoPct / 100.0).coerceIn(0.0, 1.0)
        val validUntil = quote.fecha.takeIf { it.isNotBlank() }
            ?.let { LocalDate.parse(it).plusDays(quote.vigenciaDias.toLong()).toString() }
        return QuoteBody(
            customerId = customerUuid, currency = quote.moneda, validUntil = validUntil,
            notes = quote.observaciones.takeIf { it.isNotBlank() },
            items = lines.map {
                QuoteLineBody(
                    catalogItemId = it.catalogItemRemoteId,
                    itemName = it.descripcion.take(160),
                    description = "Unidad: ${it.unidad}".take(500),
                    quantity = it.cantidad,
                    unitPrice = it.precioUnitario * descuento,
                    taxId = taxUuid
                )
            }
        )
    }

    fun parseStatus(status: String): EstadoCotizacion = when (status) {
        "BORRADOR" -> EstadoCotizacion.BORRADOR
        "ENVIADA" -> EstadoCotizacion.ENVIADA
        "ACEPTADA" -> EstadoCotizacion.ACEPTADA
        "RECHAZADA", "VENCIDA" -> EstadoCotizacion.RECHAZADA
        else -> throw IllegalArgumentException("Estado remoto desconocido: $status")
    }

    fun nextStatus(remote: String, requested: EstadoCotizacion): List<String> = when {
        remote == "BORRADOR" && requested == EstadoCotizacion.ENVIADA -> listOf("ENVIADA")
        remote == "BORRADOR" && requested == EstadoCotizacion.ACEPTADA -> listOf("ENVIADA", "ACEPTADA")
        remote == "ENVIADA" && requested == EstadoCotizacion.ACEPTADA -> listOf("ACEPTADA")
        remote == "ENVIADA" && requested == EstadoCotizacion.RECHAZADA -> listOf("RECHAZADA")
        remote == requested.name || requested == EstadoCotizacion.FACTURADA -> emptyList()
        else -> throw IllegalStateException("Transición de cotización no permitida: $remote → $requested")
    }
}
