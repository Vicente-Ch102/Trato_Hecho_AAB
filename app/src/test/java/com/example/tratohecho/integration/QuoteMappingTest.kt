package com.example.tratohecho.integration

import com.example.tratohecho.data.model.*
import com.example.tratohecho.data.remote.QuoteMapping
import org.junit.Assert.*
import org.junit.Test

class QuoteMappingTest {
    private fun cot(impuesto: Double = 19.0, descuento: Double = 10.0) = Cotizacion(
        clienteNombre = "Cliente", fecha = "2026-11-17", vigenciaDias = 15,
        moneda = "CLP", descuentoPct = descuento, impuestoPct = impuesto
    )
    private val lineas = listOf(ItemCotizacion(cotizacionId = 3, descripcion = "Mantenimiento", cantidad = 2.0, unidad = "HR", precioUnitario = 10000.0, catalogItemRemoteId = "uuid-catalog"))

    @Test fun mapeaDescuentoUnidadImpuestoYVigencia() {
        val impuesto = Impuesto(nombre = "IVA", porcentaje = 19.0, remoteId = "uuid-impuesto")
        val request = QuoteMapping.toBody(cot(), lineas, "uuid-cliente", listOf(impuesto))
        assertEquals("uuid-cliente", request.customerId)
        assertEquals("2026-12-02", request.validUntil)
        assertEquals(9000.0, request.items.single().unitPrice, 0.0001)
        assertEquals("uuid-impuesto", request.items.single().taxId)
        assertEquals("uuid-catalog", request.items.single().catalogItemId)
        assertEquals("Unidad: HR", request.items.single().description)
    }

    @Test fun impuestoCeroNoRequiereCatalogo() {
        val request = QuoteMapping.toBody(cot(impuesto = 0.0, descuento = 0.0), lineas, "cliente", emptyList())
        assertNull(request.items.single().taxId)
        assertEquals(10000.0, request.items.single().unitPrice, 0.0001)
    }

    @Test(expected = IllegalArgumentException::class)
    fun noSimulaImpuestoSiNoEstaConfigurado() {
        QuoteMapping.toBody(cot(), lineas, "cliente", emptyList())
    }

    @Test fun statusDelBackend() {
        assertEquals(EstadoCotizacion.ACEPTADA, QuoteMapping.parseStatus("ACEPTADA"))
        assertEquals(listOf("ENVIADA", "ACEPTADA"), QuoteMapping.nextStatus("BORRADOR", EstadoCotizacion.ACEPTADA))
    }
}
