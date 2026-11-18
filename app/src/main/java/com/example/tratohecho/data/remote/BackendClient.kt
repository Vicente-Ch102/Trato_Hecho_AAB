package com.example.tratohecho.data.remote

import com.example.tratohecho.BuildConfig
import com.example.tratohecho.data.security.TokenProvider
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class BearerInterceptor(private val tokens: TokenProvider) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = tokens.accessToken()
        val request = chain.request().newBuilder().apply {
            if (!token.isNullOrBlank()) header("Authorization", "Bearer $token")
        }.build()
        return chain.proceed(request)
    }
}

class RefreshAuthenticator(private val tokens: TokenProvider, private val identity: IdentityApi) : Authenticator {
    private val lock = Any()
    override fun authenticate(route: Route?, response: Response): Request? {
        if (responseCount(response) >= 2) return null
        synchronized(lock) {
            val current = tokens.accessToken() ?: return null
            val original = response.request().header("Authorization")
            if (original != "Bearer $current") {
                return response.request().newBuilder().header("Authorization", "Bearer $current").build()
            }
            val refresh = tokens.refreshToken() ?: return null
            val result = try { identity.refresh(RefreshBody(refresh)).execute() }
                catch (_: java.io.IOException) { return null } // conserva la sesión para uso offline
            val fresh = result.takeIf { it.isSuccessful }?.body()
            if (fresh == null) {
                if (result.code() in 400..499) tokens.clear()
                return null
            }
            tokens.store(fresh.accessToken, fresh.refreshToken)
            return response.request().newBuilder().header("Authorization", "Bearer ${fresh.accessToken}").build()
        }
    }
    private fun responseCount(response: Response): Int {
        var count = 1
        var previous = response.priorResponse()
        while (previous != null) { count++; previous = previous.priorResponse() }
        return count
    }
}

/** En Android Emulator: 10.0.2.2 -> PC Windows que ejecuta Docker Compose. */
class BackendClient(tokens: TokenProvider, private val host: String = BuildConfig.BACKEND_HOST) {
    private val plain = OkHttpClient.Builder()
        .proxy(java.net.Proxy.NO_PROXY)
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
    private fun <T> create(port: Int, type: Class<T>, http: OkHttpClient): T =
        Retrofit.Builder().baseUrl("http://$host:$port/").client(http)
            .addConverterFactory(GsonConverterFactory.create()).build().create(type)

    private val identityPublic = create(8081, IdentityApi::class.java, plain)
    private val secured = plain.newBuilder().addInterceptor(BearerInterceptor(tokens))
        .authenticator(RefreshAuthenticator(tokens, identityPublic)).build()

    // Login/refresh/logout sin necesidad de token previo.
    val auth: IdentityApi = identityPublic
    val identity: IdentityApi = create(8081, IdentityApi::class.java, secured)
    val customers: CustomersApi = create(8082, CustomersApi::class.java, secured)
    val commercial: CommercialApi = create(8083, CommercialApi::class.java, secured)
    val billing: BillingApi = create(8084, BillingApi::class.java, secured)
}
