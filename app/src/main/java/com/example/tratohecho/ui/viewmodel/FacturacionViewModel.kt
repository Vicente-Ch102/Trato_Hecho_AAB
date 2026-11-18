package com.example.tratohecho.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tratohecho.data.model.*
import com.example.tratohecho.data.remote.BackendMessages
import com.example.tratohecho.data.repository.FacturacionRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class FacturacionViewModel(private val repo: FacturacionRepository) : ViewModel() {
    private val tenantId = MutableStateFlow(1L)
    val ordenes = tenantId.flatMapLatest(repo::ordenes).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val facturas = tenantId.flatMapLatest(repo::facturas).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val error = MutableStateFlow<String?>(null)
    fun setTenant(id: Long) { tenantId.value = id }
    fun refrescar() = viewModelScope.launch {
        error.value = try { repo.refrescar(tenantId.value); null } catch (e: Exception) { BackendMessages.from(e) }
    }

    fun guardarOrden(cotizacionId: Long, numero: String, fecha: String, observaciones: String): String? {
        if (cotizacionId <= 0 || numero.isBlank() || !fecha.matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
            return "Completa número y fecha con formato AAAA-MM-DD"
        viewModelScope.launch { repo.guardarOrden(OrdenCompra(tenantId = tenantId.value, cotizacionId = cotizacionId,
            numero = numero.trim(), fecha = fecha.trim(), observaciones = observaciones.trim())) }
        return null
    }

    suspend fun crearFactura(cotizacionId: Long, fecha: String, vence: String?, notes: String): String? = try {
        repo.crearFactura(tenantId.value, cotizacionId, fecha, vence, notes)
        null
    } catch (e: Exception) { BackendMessages.from(e) }

    suspend fun cambiarEstado(factura: Factura, estado: String): String? = try {
        repo.cambiarEstado(tenantId.value, factura.id, estado)
        null
    } catch (e: Exception) { BackendMessages.from(e) }

    suspend fun actualizar(factura: Factura, vence: String?, notes: String): String? = try {
        repo.actualizar(tenantId.value, factura.id, vence, notes)
        null
    } catch (e: Exception) { BackendMessages.from(e) }
}
