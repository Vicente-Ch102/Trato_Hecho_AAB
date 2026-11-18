package com.example.tratohecho.data.repository

import com.example.tratohecho.data.remote.CatalogBody
import com.example.tratohecho.data.remote.CatalogDto
import com.example.tratohecho.data.remote.CommercialApi
import kotlinx.coroutines.flow.MutableStateFlow

/** El backend no tiene un repositorio de tipos de unidad; su catálogo sí es REST. */
class CatalogRepository(private val api: CommercialApi) {
    val items = MutableStateFlow<List<CatalogDto>>(emptyList())
    suspend fun refresh() { items.value = api.catalog() }
    suspend fun save(id: String?, name: String, description: String?, type: String, price: Double, active: Boolean) {
        require(name.isNotBlank()) { "El nombre es obligatorio." }
        require(type in listOf("PRODUCTO", "SERVICIO")) { "Tipo no válido." }
        require(price >= 0) { "El precio no puede ser negativo." }
        val body = CatalogBody(name.trim(), description?.takeIf { it.isNotBlank() }, type, price, active)
        if (id == null) api.createCatalog(body) else api.updateCatalog(id, body)
        refresh()
    }
    suspend fun toggle(item: CatalogDto) = save(item.id, item.name, item.description, item.itemType, item.defaultPrice, !item.active)
}
