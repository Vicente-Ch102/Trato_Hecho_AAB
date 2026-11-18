package com.example.tratohecho.data.repository

import com.example.tratohecho.data.model.Perfil
import com.example.tratohecho.data.remote.BackendMessages
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Un único ciclo ordenado: impuestos -> clientes -> cotizaciones -> facturas. */
class SyncCoordinator(
    private val admin: AdminRepository,
    private val clients: ClienteRepository,
    private val quotes: CotizacionRepository,
    private val invoices: FacturacionRepository,
    private val catalog: CatalogRepository
) {
    val lastError = MutableStateFlow<String?>(null)
    private val lock = Mutex()

    suspend fun sincronizar(tenant: Long, correo: String, perfil: Perfil) = lock.withLock {
        val errors = mutableListOf<String>()
        suspend fun step(label: String, operation: suspend () -> Unit) {
            try { operation() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { errors += "$label: ${BackendMessages.from(e)}" }
        }
        step("Impuestos y usuarios") { admin.refrescar(tenant, perfil == Perfil.ADMINISTRADOR) }
        step("Catálogo") { catalog.refresh() }
        step("Clientes") { clients.sincronizarPendientes(tenant) }
        step("Cotizaciones") { quotes.sincronizarPendientes(tenant, correo) }
        if (perfil in setOf(Perfil.ADMINISTRADOR, Perfil.FACTURACION))
            step("Facturas") { invoices.refrescar(tenant) }
        lastError.value = errors.joinToString("\n").ifBlank { null }
    }
}
