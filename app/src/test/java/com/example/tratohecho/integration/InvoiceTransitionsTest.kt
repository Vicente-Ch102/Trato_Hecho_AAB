package com.example.tratohecho.integration

import com.example.tratohecho.data.remote.InvoiceTransitions
import org.junit.Assert.*
import org.junit.Test

class InvoiceTransitionsTest {
    @Test fun borradorSoloEmiteOAnula() {
        assertEquals(listOf("EMITIDA", "ANULADA"), InvoiceTransitions.allowed("BORRADOR"))
        assertFalse(InvoiceTransitions.canMove("BORRADOR", "PAGADA"))
    }
    @Test fun emitidaSoloPagaOAnula() {
        assertTrue(InvoiceTransitions.canMove("EMITIDA", "PAGADA"))
        assertTrue(InvoiceTransitions.canMove("EMITIDA", "ANULADA"))
        assertFalse(InvoiceTransitions.canMove("EMITIDA", "BORRADOR"))
    }
    @Test fun pagadaYAnuladaSonTerminales() {
        assertTrue(InvoiceTransitions.allowed("PAGADA").isEmpty())
        assertTrue(InvoiceTransitions.allowed("ANULADA").isEmpty())
        assertFalse(InvoiceTransitions.canMove("PAGADA", "EMITIDA"))
    }
}
