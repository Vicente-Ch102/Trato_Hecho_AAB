package com.example.tratohecho.integration

import com.example.tratohecho.data.remote.Idempotency
import com.example.tratohecho.data.remote.JwtMetadata
import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

class JwtAndIdempotencyTest {
    @Test fun extraeTenantUuidDeClaimNoVerificado() {
        val uuid = "123e4567-e89b-12d3-a456-426614174000"
        val payload = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"tenantId\":\"$uuid\"}".toByteArray())
        assertEquals(uuid, JwtMetadata.tenantUuid("header.$payload.signature"))
        assertEquals(JwtMetadata.localTenantKey(uuid), JwtMetadata.localTenantKey(uuid))
    }

    @Test fun clavesDeterministasNoSeCruzanEntreTenants() {
        assertEquals(Idempotency.key("cliente", 1L, 42L), Idempotency.key("cliente", 1L, 42L))
        assertNotEquals(Idempotency.key("cliente", 1L, 42L), Idempotency.key("cliente", 2L, 42L))
        assertNotEquals(Idempotency.key("cotizacion", 1L, 42L), Idempotency.key("cliente", 1L, 42L))
    }
}
