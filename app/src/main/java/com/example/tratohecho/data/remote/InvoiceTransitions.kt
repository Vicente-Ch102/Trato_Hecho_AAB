package com.example.tratohecho.data.remote

/** Replica exactamente InvoiceStatusRules de Billing Service para validaciones de UI. */
object InvoiceTransitions {
    fun allowed(status: String): List<String> = when (status) {
        "BORRADOR" -> listOf("EMITIDA", "ANULADA")
        "EMITIDA" -> listOf("PAGADA", "ANULADA")
        else -> emptyList()
    }
    fun canMove(from: String, to: String): Boolean = to in allowed(from)
}
