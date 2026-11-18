package com.example.tratohecho.ui.screens.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.tratohecho.data.remote.CatalogDto
import com.example.tratohecho.ui.screens.comun.Page
import kotlinx.coroutines.launch

@Composable
fun CatalogoScreen(
    items: List<CatalogDto>, serverError: String?,
    onSave: suspend (CatalogDto?, String, String?, String, String, Boolean) -> String?,
    onToggle: (CatalogDto) -> Unit, onRefresh: () -> Unit, onBack: () -> Unit
) {
    var editing by remember { mutableStateOf<CatalogDto?>(null) }
    var form by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("SERVICIO") }
    var price by remember { mutableStateOf("") }
    var active by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun edit(x: CatalogDto?) {
        editing = x; form = true; error = null
        name = x?.name.orEmpty(); desc = x?.description.orEmpty()
        type = x?.itemType ?: "SERVICIO"; price = x?.defaultPrice?.toString().orEmpty(); active = x?.active ?: true
    }
    Page(if (form) "Editar catálogo" else "Catálogo de productos y servicios", if (form) ({ form = false }) else onBack) {
        if (!form) {
            serverError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            OutlinedButton(onClick = onRefresh) { Text("Actualizar desde servidor") }
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(items, key = { it.id }) { item ->
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(item.name, style = MaterialTheme.typography.titleMedium)
                            Text("${item.itemType} · ${item.defaultPrice}")
                            item.description?.let { Text(it) }
                            Row {
                                TextButton({ edit(item) }) { Text("Editar") }
                                Switch(item.active, { onToggle(item) })
                            }
                        }
                    }
                }
            }
            Button({ edit(null) }, Modifier.fillMaxWidth()) { Text("+ Nuevo ítem") }
        } else {
            OutlinedTextField(name, { name = it }, label = { Text("Nombre *") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(desc, { desc = it }, label = { Text("Descripción") }, modifier = Modifier.fillMaxWidth())
            Row {
                listOf("SERVICIO", "PRODUCTO").forEach { option ->
                    Row { RadioButton(type == option, { type = option }); Text(option, modifier = Modifier.padding(top = 12.dp)) }
                }
            }
            OutlinedTextField(price, { price = it }, label = { Text("Precio predeterminado *") }, modifier = Modifier.fillMaxWidth())
            Row { Text("Activo", Modifier.weight(1f)); Switch(active, { active = it }) }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = {
                scope.launch {
                    busy = true
                    try {
                        error = onSave(editing, name, desc, type, price, active)
                        if (error == null) form = false
                    } finally { busy = false }
                }
            }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Guardando..." else "Guardar en backend") }
        }
    }
}
