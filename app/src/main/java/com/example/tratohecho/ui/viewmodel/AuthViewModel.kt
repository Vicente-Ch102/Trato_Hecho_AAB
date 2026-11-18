package com.example.tratohecho.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tratohecho.data.datastore.Session
import com.example.tratohecho.data.datastore.SessionDataStore
import com.example.tratohecho.data.remote.BackendMessages
import com.example.tratohecho.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AuthViewModel(private val ds: SessionDataStore, private val auth: AuthRepository) : ViewModel() {
    val session = ds.session.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Session())
    val ready = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            try {
                if (ds.session.first().logged) auth.restore()
            } finally { ready.value = true }
        }
    }

    /** null = login correcto; texto = mensaje que debe mostrar la pantalla. */
    suspend fun login(email: String, password: String): String? = try {
        auth.login(email, password)
        null
    } catch (error: Exception) { BackendMessages.from(error) }

    fun logout() = viewModelScope.launch { auth.logout() }
}
