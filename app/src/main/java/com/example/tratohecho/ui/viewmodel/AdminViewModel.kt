package com.example.tratohecho.ui.viewmodel

import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tratohecho.data.model.*
import com.example.tratohecho.data.remote.BackendMessages
import com.example.tratohecho.data.repository.AdminRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class AdminViewModel(private val repo: AdminRepository) : ViewModel() {
    private val tenantId = MutableStateFlow(1L)
    val unidades = tenantId.flatMapLatest(repo::unidades).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val impuestos = tenantId.flatMapLatest(repo::impuestos).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val usuarios = tenantId.flatMapLatest(repo::usuarios).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val error = MutableStateFlow<String?>(null)
    fun setTenant(id: Long) { tenantId.value = id }

    fun guardarUnidad(base: TipoUnidad?, nombre: String, codigo: String, activo: Boolean): String? {
        if (nombre.isBlank()) return "El nombre es obligatorio"
        if (codigo.isBlank()) return "El código es obligatorio"
        viewModelScope.launch {
            repo.guardarUnidad(TipoUnidad(id = base?.id ?: 0, tenantId = tenantId.value,
                nombre = nombre.trim(), codigo = codigo.trim().uppercase(), activo = activo))
        }
        return null
    }
    fun alternarUnidad(x: TipoUnidad) = viewModelScope.launch { repo.guardarUnidad(x.copy(activo = !x.activo)) }

    suspend fun guardarImpuesto(base: Impuesto?, nombre: String, porcentaje: String, activo: Boolean): String? {
        val p = porcentaje.replace(',', '.').toDoubleOrNull()
        if (nombre.isBlank()) return "El nombre es obligatorio"
        if (p == null || p !in 0.0..100.0) return "Ingresa un porcentaje entre 0 y 100"
        return try {
            repo.guardarImpuesto(Impuesto(id = base?.id ?: 0, tenantId = tenantId.value,
                nombre = nombre.trim(), porcentaje = p, activo = activo, remoteId = base?.remoteId))
            null
        } catch (e: Exception) { BackendMessages.from(e) }
    }
    fun alternarImpuesto(x: Impuesto) = viewModelScope.launch {
        error.value = try { repo.guardarImpuesto(x.copy(activo = !x.activo)); null }
        catch (e: Exception) { BackendMessages.from(e) }
    }

    suspend fun guardarUsuario(base: Usuario?, nombre: String, correo: String,
                               perfil: Perfil, activo: Boolean, supervisor: String, password: String): String? {
        if (nombre.isBlank()) return "El nombre es obligatorio"
        if (!Patterns.EMAIL_ADDRESS.matcher(correo.trim()).matches()) return "Ingresa un correo válido"
        if (perfil == Perfil.SUPERVISOR) return "El backend no implementa el rol Supervisor."
        if (base == null && password.length < 10) return "La contraseña debe tener al menos 10 caracteres"
        return try {
            repo.guardarUsuario(Usuario(id = base?.id ?: 0, tenantId = tenantId.value,
                nombre = nombre.trim(), correo = correo.trim(), perfil = perfil, activo = activo,
                supervisorCorreo = supervisor, remoteId = base?.remoteId), password)
            null
        } catch (e: Exception) { BackendMessages.from(e) }
    }
    fun alternarUsuario(x: Usuario) = viewModelScope.launch {
        error.value = try { repo.guardarUsuario(x.copy(activo = !x.activo)); null }
        catch (e: Exception) { BackendMessages.from(e) }
    }
}
