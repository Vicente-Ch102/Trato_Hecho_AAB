package com.example.tratohecho

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.example.tratohecho.data.database.AppDatabase
import com.example.tratohecho.data.datastore.SessionDataStore
import com.example.tratohecho.data.remote.BackendClient
import com.example.tratohecho.data.repository.*
import com.example.tratohecho.data.security.TokenVault
import com.example.tratohecho.ui.navigation.AppNavGraph
import com.example.tratohecho.ui.theme.TratoHechoTheme
import com.example.tratohecho.ui.viewmodel.ViewModelFactory
import com.example.tratohecho.util.NetworkObserver

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = AppDatabase.getInstance(applicationContext)
        val session = SessionDataStore(applicationContext)
        val vault = TokenVault(applicationContext)
        val client = BackendClient(vault)
        val clientes = ClienteRepository(database.clienteDao(), client.customers)
        val cotizaciones = CotizacionRepository(database, database.cotizacionDao(), database.itemDao(),
            database.clienteDao(), database.adminDao(), client.commercial)
        val admin = AdminRepository(database.adminDao(), client.commercial, client.identity)
        val facturacion = FacturacionRepository(database.facturacionDao(), database.cotizacionDao(), client.billing)
        val catalogo = CatalogRepository(client.commercial)
        val sync = SyncCoordinator(admin, clientes, cotizaciones, facturacion, catalogo)

        val factory = ViewModelFactory(
            session = session, auth = AuthRepository(session, vault, client.auth),
            clientes = clientes, cotizaciones = cotizaciones, admin = admin,
            facturacion = facturacion, indicadores = IndicadorRepository(), catalogo = catalogo, sync = sync
        )
        setContent {
            TratoHechoTheme {
                val observer = remember { NetworkObserver(applicationContext) }
                val estaOnline by observer.estaConectado.collectAsState(initial = true)
                AppNavGraph(factory = factory, estaOnline = estaOnline)
            }
        }
    }
}
