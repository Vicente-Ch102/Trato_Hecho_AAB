package com.example.tratohecho.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tratohecho.data.remote.BackendMessages
import com.example.tratohecho.data.remote.CatalogDto
import com.example.tratohecho.data.repository.CatalogRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class CatalogViewModel(private val repo: CatalogRepository) : ViewModel() {
    val items = repo.items
    val error = MutableStateFlow<String?>(null)
    fun refrescar() = viewModelScope.launch { error.value = try { repo.refresh(); null } catch (e: Exception) { BackendMessages.from(e) } }
    suspend fun guardar(base: CatalogDto?, name: String, desc: String?, type: String, price: String, active: Boolean): String? {
        val value = price.replace(',', '.').toDoubleOrNull() ?: return "Precio inválido"
        return try { repo.save(base?.id, name, desc, type, value, active); null }
        catch (e: Exception) { BackendMessages.from(e) }
    }
    fun alternar(item: CatalogDto) = viewModelScope.launch {
        error.value = try { repo.toggle(item); null } catch (e: Exception) { BackendMessages.from(e) }
    }
}
