package com.example.tratohecho.data.remote

import retrofit2.Call
import retrofit2.http.*

interface IdentityApi {
    @POST("api/v1/auth/login") suspend fun login(@Body body: LoginBody): AuthDto
    @POST("api/v1/auth/refresh") fun refresh(@Body body: RefreshBody): Call<AuthDto>
    @POST("api/v1/auth/logout") suspend fun logout(@Body body: RefreshBody)
    @GET("api/v1/auth/me") suspend fun me(): UserDto
    @GET("api/v1/usuarios") suspend fun users(): List<UserDto>
    @POST("api/v1/usuarios") suspend fun createUser(@Body body: CreateUserBody): UserDto
    @PUT("api/v1/usuarios/{id}") suspend fun updateUser(@Path("id") id: String, @Body body: UpdateUserBody): UserDto
    @DELETE("api/v1/usuarios/{id}") suspend fun disableUser(@Path("id") id: String)
}

interface CustomersApi {
    @GET("api/v1/clientes") suspend fun customers(): List<CustomerDto>
    @GET("api/v1/clientes/{id}") suspend fun customer(@Path("id") id: String): CustomerDto
    @POST("api/v1/clientes") suspend fun create(@Header("Idempotency-Key") key: String, @Body body: CustomerBody): CustomerDto
    @PUT("api/v1/clientes/{id}") suspend fun update(@Path("id") id: String, @Body body: CustomerBody): CustomerDto
    @DELETE("api/v1/clientes/{id}") suspend fun deactivate(@Path("id") id: String)
}

interface CommercialApi {
    @GET("api/v1/cotizaciones") suspend fun quotes(): List<QuoteDto>
    @GET("api/v1/cotizaciones/{id}") suspend fun quote(@Path("id") id: String): QuoteDto
    @POST("api/v1/cotizaciones") suspend fun createQuote(@Header("Idempotency-Key") key: String, @Body body: QuoteBody): QuoteDto
    @PUT("api/v1/cotizaciones/{id}") suspend fun updateQuote(@Path("id") id: String, @Body body: QuoteBody): QuoteDto
    @PATCH("api/v1/cotizaciones/{id}/estado") suspend fun status(@Path("id") id: String, @Body body: QuoteStatusBody): QuoteDto
    @DELETE("api/v1/cotizaciones/{id}") suspend fun deleteQuote(@Path("id") id: String)
    @GET("api/v1/cotizaciones/{id}/seguimientos") suspend fun followUps(@Path("id") id: String): List<FollowUpDto>
    @POST("api/v1/cotizaciones/{id}/seguimientos") suspend fun addFollowUp(@Path("id") id: String, @Body body: FollowUpBody): FollowUpDto
    @GET("api/v1/catalogos") suspend fun catalog(): List<CatalogDto>
    @POST("api/v1/catalogos") suspend fun createCatalog(@Body body: CatalogBody): CatalogDto
    @PUT("api/v1/catalogos/{id}") suspend fun updateCatalog(@Path("id") id: String, @Body body: CatalogBody): CatalogDto
    @DELETE("api/v1/catalogos/{id}") suspend fun deleteCatalog(@Path("id") id: String)
    @GET("api/v1/impuestos") suspend fun taxes(): List<TaxDto>
    @POST("api/v1/impuestos") suspend fun createTax(@Body body: TaxBody): TaxDto
    @PUT("api/v1/impuestos/{id}") suspend fun updateTax(@Path("id") id: String, @Body body: TaxBody): TaxDto
    @DELETE("api/v1/impuestos/{id}") suspend fun deleteTax(@Path("id") id: String)
}

interface BillingApi {
    @GET("api/v1/facturas") suspend fun invoices(): List<InvoiceDto>
    @GET("api/v1/facturas/{id}") suspend fun invoice(@Path("id") id: String): InvoiceDto
    @POST("api/v1/facturas") suspend fun createInvoice(@Body body: CreateInvoiceBody): InvoiceDto
    @PUT("api/v1/facturas/{id}") suspend fun updateInvoice(@Path("id") id: String, @Body body: UpdateInvoiceBody): InvoiceDto
    @PATCH("api/v1/facturas/{id}/estado") suspend fun invoiceStatus(@Path("id") id: String, @Body body: InvoiceStatusBody): InvoiceDto
    @DELETE("api/v1/facturas/{id}") suspend fun cancelInvoice(@Path("id") id: String)
}
