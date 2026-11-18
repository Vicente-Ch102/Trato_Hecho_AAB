package com.example.tratohecho.integration

import com.example.tratohecho.data.remote.*
import com.example.tratohecho.data.security.TokenProvider
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class BackendHttpContractTest {
    private lateinit var server: MockWebServer
    private class FakeTokens : TokenProvider {
        var access = "old"
        var refresh = "refresh-old"
        override fun accessToken() = access
        override fun refreshToken() = refresh
        override fun store(access: String, refresh: String) { this.access = access; this.refresh = refresh }
        override fun clear() { access = ""; refresh = "" }
    }
    private fun <T> api(clazz: Class<T>, client: OkHttpClient = OkHttpClient()): T =
        Retrofit.Builder().baseUrl(server.url("/")).client(client)
            .addConverterFactory(GsonConverterFactory.create()).build().create(clazz)

    @Before fun setup() { server = MockWebServer(); server.start() }
    @After fun shutdown() { server.shutdown() }

    @Test fun cabeceraJwtYContratoDeClientes() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201).setBody(
            """{"id":"uuid-c","businessName":"Empresa","rut":"76123456-7","status":"ACTIVE"}"""))
        val tokens = FakeTokens()
        val client = OkHttpClient.Builder().addInterceptor(BearerInterceptor(tokens)).build()
        val customer = api(CustomersApi::class.java, client).create("stable-key", CustomerBody("Empresa", "76123456-7", null, null, null, null))
        assertEquals("uuid-c", customer.id)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/clientes", request.path)
        assertEquals("Bearer old", request.getHeader("Authorization"))
        assertEquals("stable-key", request.getHeader("Idempotency-Key"))
        assertTrue(request.body.readUtf8().contains("\"businessName\":\"Empresa\""))
    }

    @Test fun refresca401YSoloReintentaConTokenNuevo() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path == "/api/v1/auth/refresh" -> MockResponse().setBody(
                    """{"accessToken":"new","refreshToken":"refresh-new","tokenType":"Bearer","expiresIn":900,"user":{"id":"id","name":"Admin","email":"admin@example.com","role":"ADMIN","enabled":true}}""")
                request.path == "/api/v1/clientes" && request.getHeader("Authorization") == "Bearer new" -> MockResponse().setBody("[]")
                request.path == "/api/v1/clientes" -> MockResponse().setResponseCode(401)
                else -> MockResponse().setResponseCode(404)
            }
        }
        val tokens = FakeTokens()
        val identity = api(IdentityApi::class.java)
        val client = OkHttpClient.Builder().addInterceptor(BearerInterceptor(tokens))
            .authenticator(RefreshAuthenticator(tokens, identity)).build()
        assertTrue(api(CustomersApi::class.java, client).customers().isEmpty())
        assertEquals("new", tokens.accessToken())
        assertEquals("refresh-new", tokens.refreshToken())
        assertEquals(3, server.requestCount)
    }

    @Test fun contratoDeFactura() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201).setBody(
            """{"id":"uuid-f","quoteId":"uuid-q","folio":"FAC-1","issuedDate":"2026-11-17","currency":"CLP","subtotal":100,"taxAmount":19,"total":119,"status":"BORRADOR"}"""))
        val invoice = api(BillingApi::class.java).createInvoice(CreateInvoiceBody("uuid-q", "2026-11-17", null, "Prueba"))
        assertEquals("BORRADOR", invoice.status)
        val req = server.takeRequest()
        assertEquals("/api/v1/facturas", req.path)
        assertTrue(req.body.readUtf8().contains("\"quoteId\":\"uuid-q\""))
    }
}
