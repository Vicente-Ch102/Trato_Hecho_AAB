package com.example.tratohecho.data.repository

import com.example.tratohecho.data.database.AdminDao
import com.example.tratohecho.data.model.*
import com.example.tratohecho.data.remote.*

class AdminRepository(
    private val dao: AdminDao,
    private val commercial: CommercialApi,
    private val identity: IdentityApi
) {
    fun unidades(t: Long) = dao.unidades(t)
    fun impuestos(t: Long) = dao.impuestos(t)
    fun usuarios(t: Long) = dao.usuarios(t)
    suspend fun guardarUnidad(x: TipoUnidad) = dao.guardarUnidad(x)
    suspend fun eliminarUnidad(x: TipoUnidad) = dao.eliminarUnidad(x)

    suspend fun refrescar(tenant: Long, esAdmin: Boolean) {
        commercial.taxes().forEach { remote ->
            val previous = dao.impuestoRemoto(tenant, remote.id)
                ?: dao.impuestosLista(tenant).firstOrNull { it.remoteId == null && it.nombre.equals(remote.name, true) && it.porcentaje == remote.rate }
            dao.guardarImpuesto(Impuesto(id = previous?.id ?: 0, tenantId = tenant,
                nombre = remote.name, porcentaje = remote.rate, activo = remote.active, remoteId = remote.id))
        }
        if (esAdmin) identity.users().forEach { remote ->
            val previous = dao.usuarioRemoto(tenant, remote.id)
            dao.guardarUsuario(Usuario(
                id = previous?.id ?: 0, tenantId = tenant, nombre = remote.name, correo = remote.email,
                perfil = when (remote.role) {
                    "ADMIN" -> Perfil.ADMINISTRADOR
                    "FACTURACION" -> Perfil.FACTURACION
                    else -> Perfil.USUARIO
                }, activo = remote.enabled,
                supervisorCorreo = previous?.supervisorCorreo.orEmpty(), remoteId = remote.id
            ))
        }
    }

    suspend fun guardarImpuesto(x: Impuesto) {
        val body = TaxBody(x.nombre, x.porcentaje, x.activo)
        val response = if (x.remoteId == null) commercial.createTax(body)
            else commercial.updateTax(x.remoteId, body)
        dao.guardarImpuesto(x.copy(remoteId = response.id))
    }

    suspend fun guardarUsuario(x: Usuario, password: String = "") {
        val role = when (x.perfil) {
            Perfil.ADMINISTRADOR -> "ADMIN"
            Perfil.USUARIO -> "VENDEDOR"
            Perfil.FACTURACION -> "FACTURACION"
            Perfil.SUPERVISOR -> error("El backend todavía no admite el rol SUPERVISOR.")
        }
        val response = if (x.remoteId == null) {
            require(password.length >= 10) { "La contraseña del nuevo usuario debe tener al menos 10 caracteres." }
            identity.createUser(CreateUserBody(x.nombre, x.correo, password, role))
        } else identity.updateUser(x.remoteId, UpdateUserBody(x.nombre, x.correo, role, x.activo))
        dao.guardarUsuario(x.copy(remoteId = response.id, activo = response.enabled))
    }
}
