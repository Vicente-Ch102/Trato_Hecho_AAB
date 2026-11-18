package com.example.tratohecho.data.remote

import java.util.UUID

/** Genera claves repetibles para los POST con soporte de idempotencia. */
object Idempotency {
    fun key(kind: String, tenant: Long, localId: Long): String =
        UUID.nameUUIDFromBytes("tratohecho:$kind:$tenant:$localId".toByteArray(Charsets.UTF_8)).toString()
}
