package com.example.tratohecho.data.repository

import com.example.tratohecho.data.database.ClienteDao
import com.example.tratohecho.data.model.Cliente
import com.example.tratohecho.data.model.EstadoSync
import com.example.tratohecho.data.remote.*
import kotlinx.coroutines.CancellationException

/** Room es la fuente de UI; el servidor solo confirma la sincronización tras una respuesta real. */
class ClienteRepository(private val dao: ClienteDao, private val api: CustomersApi) {
    fun observar(tenant: Long) = dao.observar(tenant)
    suspend fun obtener(id: Long) = dao.obtener(id)

    suspend fun guardar(x: Cliente, online: Boolean): Long {
        val id = dao.guardar(x.copy(sync = EstadoSync.PENDIENTE))
        if (online) push(id)
        return id
    }

    suspend fun eliminar(x: Cliente) {
        // No descartar silenciosamente una baja cuando falta conectividad.
        if (x.remoteId != null) api.deactivate(x.remoteId)
        dao.eliminar(x)
    }

    private fun body(x: Cliente): CustomerBody = CustomerBody(
        businessName = x.nombre.trim(), rut = x.rut.trim(),
        contactName = x.contacto.takeIf { it.isNotBlank() },
        email = x.correo.takeIf { it.isNotBlank() },
        phone = x.telefono.takeIf { it.isNotBlank() },
        address = x.direccion.takeIf { it.isNotBlank() }
    )

    private suspend fun push(id: Long) {
        val item = dao.obtener(id) ?: return
        try {
            dao.cambiarSync(id, EstadoSync.SINCRONIZANDO)
            val result = if (item.remoteId == null) {
                api.create(Idempotency.key("cliente", item.tenantId, item.id), body(item))
            } else api.update(item.remoteId, body(item))
            dao.guardar(item.copy(remoteId = result.id, sync = EstadoSync.SINCRONIZADO))
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { dao.cambiarSync(id, EstadoSync.ERROR) }
    }

    suspend fun sincronizarPendientes(tenant: Long) {
        dao.pendientes(tenant).forEach { push(it.id) }
        // Una falla al descargar nunca se traduce en un estado SINCRONIZADO falso.
        api.customers().filter { it.status == "ACTIVE" }.forEach { remote ->
            val current = dao.porRemoteId(tenant, remote.id)
            if (current?.sync != null && current.sync != EstadoSync.SINCRONIZADO) return@forEach
            dao.guardar(Cliente(
                id = current?.id ?: 0,
                tenantId = tenant,
                propietarioCorreo = current?.propietarioCorreo.orEmpty(),
                nombre = remote.businessName,
                rut = remote.rut,
                contacto = remote.contactName.orEmpty(),
                correo = remote.email.orEmpty(),
                telefono = remote.phone.orEmpty(),
                direccion = remote.address.orEmpty(),
                remoteId = remote.id,
                sync = EstadoSync.SINCRONIZADO
            ))
        }
    }

    suspend fun reintentar(id: Long, online: Boolean) {
        if (!online) { dao.cambiarSync(id, EstadoSync.PENDIENTE); return }
        push(id)
    }
}
