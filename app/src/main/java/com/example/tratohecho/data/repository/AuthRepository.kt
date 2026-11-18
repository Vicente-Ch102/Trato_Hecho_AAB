package com.example.tratohecho.data.repository

import com.example.tratohecho.data.datastore.SessionDataStore
import com.example.tratohecho.data.model.Perfil
import com.example.tratohecho.data.remote.*
import com.example.tratohecho.data.security.TokenProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AuthRepository(
    private val session: SessionDataStore,
    private val tokens: TokenProvider,
    private val api: IdentityApi
) {
    suspend fun login(email: String, password: String) {
        val response = try {
            android.util.Log.d(
                "TRATOHECHO_AUTH",
                "Iniciando solicitud de autenticación"
            )

            api.login(LoginBody(email.trim(), password))
        } catch (e: Exception) {
            android.util.Log.e(
                "TRATOHECHO_AUTH",
                "Error durante la solicitud de login: ${e.javaClass.simpleName}",
                e
            )
            throw e
        }

        android.util.Log.d(
            "TRATOHECHO_AUTH",
            "Respuesta de autenticación recibida correctamente"
        )
        check(response.user.enabled) { "La cuenta está deshabilitada." }
        val uuid = JwtMetadata.tenantUuid(response.accessToken)
        val perfil = when (response.user.role) {
            "ADMIN" -> Perfil.ADMINISTRADOR
            "VENDEDOR" -> Perfil.USUARIO
            "FACTURACION" -> Perfil.FACTURACION
            else -> error("Rol no admitido por la aplicación: ${response.user.role}")
        }
        tokens.store(response.accessToken, response.refreshToken)
        session.login(response.user.name, response.user.email, perfil, JwtMetadata.localTenantKey(uuid), uuid)
    }

    /** La sesión solo se restablece tras obtener un JWT válido con /auth/refresh. */
    suspend fun restore(): Boolean = withContext(Dispatchers.IO) {
        val refresh = tokens.refreshToken() ?: run { session.logout(); return@withContext false }
        val response = try { api.refresh(RefreshBody(refresh)).execute() }
            catch (_: java.io.IOException) { return@withContext true /* acceso offline a Room */ }
        val body = response.takeIf { it.isSuccessful }?.body()
        if (body == null || !body.user.enabled) {
            tokens.clear()
            session.logout()
            return@withContext false
        }
        val uuid = runCatching { JwtMetadata.tenantUuid(body.accessToken) }.getOrNull()
        val perfil = when (body.user.role) {
            "ADMIN" -> Perfil.ADMINISTRADOR
            "VENDEDOR" -> Perfil.USUARIO
            "FACTURACION" -> Perfil.FACTURACION
            else -> null
        }
        if (uuid == null || perfil == null) {
            tokens.clear()
            session.logout()
            return@withContext false
        }
        tokens.store(body.accessToken, body.refreshToken)
        session.login(body.user.name, body.user.email, perfil, JwtMetadata.localTenantKey(uuid), uuid)
        true
    }

    suspend fun logout() {
        val refresh = tokens.refreshToken()
        // Invalidar remotamente si está disponible; siempre cerrar la sesión local.
        try { if (refresh != null) api.logout(RefreshBody(refresh)) } catch (_: Exception) { }
        finally { tokens.clear(); session.logout() }
    }
}
