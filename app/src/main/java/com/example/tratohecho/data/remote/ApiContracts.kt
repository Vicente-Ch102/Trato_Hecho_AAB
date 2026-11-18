package com.example.tratohecho.data.remote

/** DTOs alineados con los controladores Spring Boot proporcionados en el proyecto. */
data class LoginBody(val email: String, val password: String)
data class RefreshBody(val refreshToken: String)
data class AuthUserDto(val id: String, val name: String, val email: String, val role: String, val enabled: Boolean = true)
data class AuthDto(val accessToken: String, val refreshToken: String, val tokenType: String, val expiresIn: Long, val user: AuthUserDto)
data class UserDto(val id: String, val name: String, val email: String, val role: String, val enabled: Boolean)
data class CreateUserBody(val name: String, val email: String, val password: String, val role: String)
data class UpdateUserBody(val name: String, val email: String, val role: String, val enabled: Boolean)

data class CustomerBody(val businessName: String, val rut: String, val contactName: String?, val email: String?, val phone: String?, val address: String?, val notes: String? = null)
data class CustomerDto(val id: String, val businessName: String, val rut: String, val contactName: String?, val email: String?, val phone: String?, val address: String?, val notes: String?, val status: String)

data class TaxBody(val name: String, val rate: Double, val active: Boolean)
data class TaxDto(val id: String, val name: String, val rate: Double, val active: Boolean)
data class CatalogBody(val name: String, val description: String?, val itemType: String, val defaultPrice: Double, val active: Boolean)
data class CatalogDto(val id: String, val name: String, val description: String?, val itemType: String, val defaultPrice: Double, val active: Boolean)

data class QuoteLineBody(val catalogItemId: String? = null, val itemName: String, val description: String?, val quantity: Double, val unitPrice: Double, val taxId: String?)
data class QuoteBody(val customerId: String, val currency: String, val validUntil: String?, val notes: String?, val items: List<QuoteLineBody>)
data class QuoteStatusBody(val status: String)
data class QuoteLineDto(val id: String, val catalogItemId: String?, val itemName: String, val description: String?, val quantity: Double, val unitPrice: Double, val taxName: String?, val taxRate: Double)
data class QuoteDto(val id: String, val customerId: String, val code: String, val status: String, val currency: String, val validUntil: String?, val notes: String?, val items: List<QuoteLineDto>, val subtotal: Double, val taxAmount: Double, val total: Double, val createdAt: String?)
data class FollowUpBody(val channel: String, val note: String, val nextContactAt: String? = null, val outcome: String? = null)
data class FollowUpDto(val id: String, val quoteId: String, val channel: String, val note: String, val nextContactAt: String?, val outcome: String?, val createdAt: String?)

data class CreateInvoiceBody(val quoteId: String, val issuedDate: String, val dueDate: String? = null, val notes: String? = null)
data class UpdateInvoiceBody(val dueDate: String? = null, val notes: String? = null)
data class InvoiceStatusBody(val status: String)
data class InvoiceDto(val id: String, val quoteId: String, val folio: String, val issuedDate: String, val dueDate: String?, val currency: String, val subtotal: Double, val taxAmount: Double, val total: Double, val status: String, val notes: String?)
