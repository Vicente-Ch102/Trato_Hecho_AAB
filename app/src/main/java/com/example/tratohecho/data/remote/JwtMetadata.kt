package com.example.tratohecho.data.remote

import com.google.gson.JsonParser
import java.util.Base64
import java.util.UUID

/** Metadatos de conveniencia: NO sustituyen la validación JWT efectuada en Spring Boot. */
object JwtMetadata {
    fun tenantUuid(token: String): String {
        val payload = token.split('.').getOrNull(1) ?: error("Token JWT inválido")
        val json = String(Base64.getUrlDecoder().decode(payload), Charsets.UTF_8)
        return UUID.fromString(JsonParser.parseString(json).asJsonObject.get("tenantId").asString).toString()
    }

    // ID estable solo para particionar Room. Nunca se envía como ID de tenant al servidor.
    fun localTenantKey(uuid: String): Long {
        val id = UUID.fromString(uuid)
        return (id.mostSignificantBits xor id.leastSignificantBits).let { if (it == 0L) Long.MIN_VALUE else it }
    }
}

object BackendMessages {
    fun from(error: Throwable): String = when (error) {
        is retrofit2.HttpException -> when (error.code()) {
            400 -> "Datos inválidos; revisa los campos y sus valores."
            401 -> "Sesión vencida. Vuelve a iniciar sesión."
            403 -> "El usuario no tiene permisos para esta operación."
            404 -> "El registro no existe en el servidor."
            409 -> "Conflicto de estado o registro duplicado en el servidor."
            422 -> "El servidor rechazó los datos enviados."
            else -> "Error HTTP ${error.code()}."
        }
        is java.io.IOException -> "Sin conexión al backend. Revisa Docker, la dirección IP y la red."
        else -> error.message ?: "No fue posible completar la operación."
    }
}

