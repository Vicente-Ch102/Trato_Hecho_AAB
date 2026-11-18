package com.example.tratohecho.integration

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.tratohecho.data.database.AppDatabase
import com.example.tratohecho.data.model.Cliente
import com.example.tratohecho.data.model.Cotizacion
import com.example.tratohecho.data.model.EstadoSync
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomTenantIsolationTest {
    private lateinit var db: AppDatabase

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), AppDatabase::class.java
        ).build()
    }
    @After fun tearDown() { db.close() }

    @Test fun clientesDeDistintosTenantsNoSeMezclan() = runBlocking {
        val dao = db.clienteDao()
        dao.guardar(Cliente(tenantId = 101, nombre = "Cliente A", rut = "11111111-1", remoteId = "uuid-a"))
        dao.guardar(Cliente(tenantId = 202, nombre = "Cliente B", rut = "22222222-2", remoteId = "uuid-b"))
        assertEquals(listOf("Cliente A"), dao.observar(101).first().map { it.nombre })
        assertEquals(listOf("Cliente B"), dao.observar(202).first().map { it.nombre })
        assertNull(dao.porRemoteId(202, "uuid-a"))
    }

    @Test fun uuidRemotoMantieneIdLocalAlActualizar() = runBlocking {
        val dao = db.clienteDao()
        val id = dao.guardar(Cliente(tenantId = 101, nombre = "Original", rut = "11111111-1", remoteId = "uuid-a"))
        val existing = dao.porRemoteId(101, "uuid-a")!!
        dao.guardar(existing.copy(nombre = "Actualizado", sync = EstadoSync.SINCRONIZADO))
        assertEquals(id, dao.porRemoteId(101, "uuid-a")!!.id)
        assertEquals("Actualizado", dao.obtener(id)!!.nombre)
    }

    @Test fun cotizacionesSeFiltranPorTenant() = runBlocking {
        val dao = db.cotizacionDao()
        dao.guardar(Cotizacion(tenantId = 1, clienteNombre = "A", remoteId = "quote-a"))
        dao.guardar(Cotizacion(tenantId = 2, clienteNombre = "B", remoteId = "quote-b"))
        assertEquals(1, dao.observar(1).first().size)
        assertNull(dao.porRemoteId(1, "quote-b"))
    }
}
