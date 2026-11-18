package com.example.tratohecho.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.tratohecho.data.datastore.SessionDataStore
import com.example.tratohecho.data.repository.*

class ViewModelFactory(
    private val session: SessionDataStore,
    private val auth: AuthRepository,
    private val clientes: ClienteRepository,
    private val cotizaciones: CotizacionRepository,
    private val admin: AdminRepository,
    private val facturacion: FacturacionRepository,
    private val indicadores: IndicadorRepository,
    private val catalogo: CatalogRepository,
    val sync: SyncCoordinator
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return when {
            modelClass.isAssignableFrom(AuthViewModel::class.java) -> AuthViewModel(session, auth)
            modelClass.isAssignableFrom(ClienteViewModel::class.java) -> ClienteViewModel(clientes)
            modelClass.isAssignableFrom(CotizacionViewModel::class.java) -> CotizacionViewModel(cotizaciones)
            modelClass.isAssignableFrom(AdminViewModel::class.java) -> AdminViewModel(admin)
            modelClass.isAssignableFrom(FacturacionViewModel::class.java) -> FacturacionViewModel(facturacion)
            modelClass.isAssignableFrom(IndicadoresViewModel::class.java) -> IndicadoresViewModel(indicadores)
            modelClass.isAssignableFrom(CatalogViewModel::class.java) -> CatalogViewModel(catalogo)
            else -> throw IllegalArgumentException("ViewModel desconocido: ${modelClass.name}")
        } as T
    }
}
