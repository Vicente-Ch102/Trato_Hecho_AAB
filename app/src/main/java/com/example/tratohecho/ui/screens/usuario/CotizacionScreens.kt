package com.example.tratohecho.ui.screens.usuario

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.tratohecho.data.model.*
import com.example.tratohecho.data.remote.CatalogDto
import com.example.tratohecho.data.remote.FollowUpDto
import com.example.tratohecho.ui.screens.comun.*
import com.example.tratohecho.util.CotizacionPdfExporter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

@Composable
fun CotizacionesScreen(
    items: List<Cotizacion>,
    perfil: Perfil,
    correo: String,
    goForm: () -> Unit,
    goSeg: (Long) -> Unit,
    onRetry: (Long) -> Unit,
    onBack: () -> Unit
) {
    val visibles = remember(items, perfil, correo) {
        if (perfil == Perfil.USUARIO) items.filter { it.propietarioCorreo.isBlank() || it.propietarioCorreo == correo } else items
    }
    Page(if (perfil == Perfil.USUARIO) "Mis cotizaciones" else "Cotizaciones", onBack) {
        if (visibles.isEmpty()) Text("No hay cotizaciones para mostrar.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(visibles.size, key = { visibles[it].id }) { index ->
                val x = visibles[index]
                Card(onClick = { goSeg(x.id) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("COT-${x.id}", style = MaterialTheme.typography.titleMedium)
                            EstadoChip(x.estado)
                        }
                        Text(x.clienteNombre)
                        Text("${x.fecha} · ${x.moneda}", style = MaterialTheme.typography.bodySmall)
                        SyncText(x.sync) { onRetry(x.id) }
                    }
                }
            }
        }
        if (perfil == Perfil.USUARIO || perfil == Perfil.ADMINISTRADOR) {
            Button(goForm, Modifier.fillMaxWidth()) { Text("+ Nueva cotización") }
        }
    }
}

@Composable
fun CotizacionFormScreen(
    online: Boolean,
    edicion: Cotizacion?,
    clientePreseleccionadoId: Long?,
    clientes: List<Cliente>,
    propietarioCorreo: String,
    permitAllClients: Boolean = false,
    items: List<ItemCotizacion>,
    error: String?,
    impuestosCatalogo: List<Impuesto>,
    onContinue: (Cliente?, String, String, String, String, String) -> Unit,
    onItem: () -> Unit,
    onRemoveItem: (Int) -> Unit,
    onBack: () -> Unit
) {
    var cliente by remember(edicion?.id, clientePreseleccionadoId, clientes) {
        mutableStateOf(edicion?.let { e -> clientes.firstOrNull { it.id == e.clienteId } } ?: clientes.firstOrNull { it.id == clientePreseleccionadoId })
    }
    var selectorCliente by remember { mutableStateOf(false) }
    var moneda by remember(edicion?.id) { mutableStateOf(edicion?.moneda ?: "CLP") }
    var descuento by remember(edicion?.id) { mutableStateOf(edicion?.descuentoPct?.toString() ?: "0") }
    var impuesto by remember(edicion?.id) { mutableStateOf(edicion?.impuestoPct?.toString() ?: "19") }
    var vigencia by remember(edicion?.id) { mutableStateOf(edicion?.vigenciaDias?.toString() ?: "15") }
    var observaciones by remember(edicion?.id) { mutableStateOf(edicion?.observaciones.orEmpty()) }
    val propios = if (permitAllClients) clientes else clientes.filter { it.propietarioCorreo == propietarioCorreo || it.propietarioCorreo.isBlank() }
    val desc = descuento.replace(',', '.').toDoubleOrNull() ?: 0.0
    val imp = impuesto.replace(',', '.').toDoubleOrNull() ?: 0.0
    val totales = calcularTotales(items, desc, imp)
    val nf = NumberFormat.getNumberInstance(Locale("es", "CL"))

    Page(if (edicion == null) "Nueva cotización" else "Editar borrador", onBack) {
        if (!online) AssistChip(onClick = {}, label = { Text("Offline · el borrador quedará pendiente de sincronización") })

        Box {
            OutlinedButton(onClick = { selectorCliente = true }, modifier = Modifier.fillMaxWidth()) {
                Text(cliente?.nombre ?: "Seleccionar cliente *")
            }
            DropdownMenu(expanded = selectorCliente, onDismissRequest = { selectorCliente = false }) {
                propios.forEach { c -> DropdownMenuItem(text = { Text(c.nombre) }, onClick = { cliente = c; selectorCliente = false }) }
            }
        }
        if (propios.isEmpty()) Text("Primero registra al menos un cliente.", color = MaterialTheme.colorScheme.error)

        Text("Moneda", style = MaterialTheme.typography.titleSmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf("CLP", "USD", "UF").forEach { m ->
                Row { RadioButton(moneda == m, { moneda = m }); Text(m, modifier = Modifier.padding(top = 12.dp)) }
            }
        }

        Text("Ítems", style = MaterialTheme.typography.titleMedium)
        if (items.isEmpty()) Text("Sin ítems agregados")
        items.forEachIndexed { index, item ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(item.descripcion)
                        Text("${item.cantidad} ${item.unidad} × ${nf.format(item.precioUnitario)}")
                    }
                    TextButton(onClick = { onRemoveItem(index) }) { Text("Quitar") }
                }
            }
        }
        OutlinedButton(onClick = onItem, modifier = Modifier.fillMaxWidth()) { Text("+ Agregar ítem") }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(descuento, { descuento = it }, label = { Text("Descuento %") }, modifier = Modifier.weight(1f))
            OutlinedTextField(impuesto, { impuesto = it }, label = { Text("Impuesto %") }, modifier = Modifier.weight(1f))
        }
        if (impuestosCatalogo.isNotEmpty()) {
            Text("Impuestos configurados", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                impuestosCatalogo.take(3).forEach { tax ->
                    AssistChip(onClick = { impuesto = tax.porcentaje.toString() }, label = { Text("${tax.nombre} ${tax.porcentaje}%") })
                }
            }
        }
        OutlinedTextField(vigencia, { vigencia = it }, label = { Text("Vigencia (días)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(observaciones, { observaciones = it }, label = { Text("Observaciones") }, minLines = 2, modifier = Modifier.fillMaxWidth())

        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("Subtotal: ${nf.format(totales.subtotal)} $moneda")
                Text("Descuento: ${nf.format(totales.descuento)} $moneda")
                Text("Impuesto: ${nf.format(totales.impuesto)} $moneda")
                Text("Total: ${nf.format(totales.total)} $moneda", style = MaterialTheme.typography.titleMedium)
            }
        }
        AnimatedVisibility(error != null) { Text(error.orEmpty(), color = MaterialTheme.colorScheme.error) }
        Button(
            onClick = { onContinue(cliente, moneda, descuento, impuesto, vigencia, observaciones) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Continuar") }
    }
}

@Composable
fun ValorizacionScreen(
    meta: com.example.tratohecho.ui.viewmodel.CotizacionDraftMeta?,
    indicadorUsd: IndicadorEconomico?,
    indicadorUf: IndicadorEconomico?,
    online: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    onApply: (Double) -> Boolean,
    onContinue: () -> Unit,
    onBack: () -> Unit
) {
    val indicador = when (meta?.moneda) {
        "USD" -> indicadorUsd
        "UF" -> indicadorUf
        else -> null
    }
    var manual by remember(meta?.moneda, indicador?.valor) {
        mutableStateOf((indicador?.valor ?: meta?.tipoCambio?.takeIf { it > 0 })?.toString().orEmpty())
    }
    val nf = NumberFormat.getNumberInstance(Locale("es", "CL"))
    Page("Moneda y conversión", onBack) {
        if (meta == null) {
            Text("No hay una cotización en preparación")
            return@Page
        }
        Text("Moneda seleccionada: ${meta.moneda}", style = MaterialTheme.typography.titleMedium)
        if (indicador != null) {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Valor del ${meta.moneda}")
                    Text("$ ${nf.format(indicador.valor)} CLP", style = MaterialTheme.typography.headlineSmall)
                    Text("Actualizado: ${indicador.fecha}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        OutlinedTextField(
            value = manual,
            onValueChange = { manual = it },
            label = { Text("Valor ${meta.moneda} en CLP *") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedButton(onClick = onRefresh, enabled = online, modifier = Modifier.fillMaxWidth()) {
            Text("Actualizar desde fuente pública")
        }
        if (!online) Text("Sin conexión: puedes usar el último valor conocido o ingresar uno manualmente.")
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(
            onClick = {
                val valor = manual.replace(',', '.').toDoubleOrNull() ?: 0.0
                if (onApply(valor)) onContinue()
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Aplicar") }
    }
}

@Composable
fun ResumenCotizacionScreen(
    meta: com.example.tratohecho.ui.viewmodel.CotizacionDraftMeta?,
    items: List<ItemCotizacion>,
    onEdit: () -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit
) {
    val nf = NumberFormat.getNumberInstance(Locale("es", "CL"))
    Page("Resumen de cotización", onBack) {
        if (meta == null) {
            Text("No hay una cotización en preparación")
            return@Page
        }
        Text("Cliente", style = MaterialTheme.typography.labelLarge)
        Text(meta.cliente.nombre, style = MaterialTheme.typography.titleMedium)
        Text("Moneda: ${meta.moneda}${if (meta.moneda != "CLP") " (valor ${nf.format(meta.tipoCambio)} CLP)" else ""}")
        HorizontalDivider()
        Text("Ítems", style = MaterialTheme.typography.titleMedium)
        items.forEach { item ->
            Text("• ${item.descripcion} · ${item.cantidad} ${item.unidad} × ${nf.format(item.precioUnitario)}")
        }
        val totales = calcularTotales(items, meta.descuentoPct, meta.impuestoPct)
        HorizontalDivider()
        Text("Subtotal: ${nf.format(totales.subtotal)} ${meta.moneda}")
        Text("Descuento (${meta.descuentoPct}%): ${nf.format(totales.descuento)}")
        Text("Impuestos (${meta.impuestoPct}%): ${nf.format(totales.impuesto)}")
        Text("TOTAL: ${nf.format(totales.total)} ${meta.moneda}", style = MaterialTheme.typography.headlineSmall)
        if (meta.moneda != "CLP") {
            Text("Equivalente referencial: ${nf.format(totales.total * meta.tipoCambio)} CLP", style = MaterialTheme.typography.bodyMedium)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f)) { Text("Editar") }
            Button(onClick = onSave, modifier = Modifier.weight(1f)) { Text("Guardar borrador") }
        }
    }
}

@Composable
fun ItemFormScreen(unidades: List<TipoUnidad>, catalogo: List<CatalogDto> = emptyList(), onSave: (String, String, String, String, String?) -> String?, onBack: () -> Unit) {
    var descripcion by remember { mutableStateOf("") }
    var cantidad by remember { mutableStateOf("") }
    var unidad by remember { mutableStateOf(unidades.firstOrNull()?.codigo.orEmpty()) }
    var selectorUnidad by remember { mutableStateOf(false) }
    var selectorCatalogo by remember { mutableStateOf(false) }
    var catalogUuid by remember { mutableStateOf<String?>(null) }
    var precio by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    Page("Agregar ítem", onBack) {
        if (catalogo.any { it.active }) {
            Box {
                OutlinedButton(onClick = { selectorCatalogo = true }, modifier = Modifier.fillMaxWidth()) { Text("Elegir del catálogo del servidor") }
                DropdownMenu(expanded = selectorCatalogo, onDismissRequest = { selectorCatalogo = false }) {
                    catalogo.filter { it.active }.forEach { item ->
                        DropdownMenuItem(text = { Text("${item.name} · ${item.defaultPrice}") }, onClick = {
                            descripcion = item.name; precio = item.defaultPrice.toString(); catalogUuid = item.id; selectorCatalogo = false
                        })
                    }
                }
            }
        }
        OutlinedTextField(descripcion, { descripcion = it; catalogUuid = null }, label = { Text("Descripción *") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(cantidad, { cantidad = it }, label = { Text("Cantidad *") }, modifier = Modifier.fillMaxWidth())
        Box {
            OutlinedButton(onClick = { selectorUnidad = true }, modifier = Modifier.fillMaxWidth()) {
                Text(if (unidad.isBlank()) "Seleccionar unidad *" else "Unidad: $unidad")
            }
            DropdownMenu(expanded = selectorUnidad, onDismissRequest = { selectorUnidad = false }) {
                unidades.forEach { u -> DropdownMenuItem(text = { Text("${u.nombre} (${u.codigo})") }, onClick = { unidad = u.codigo; selectorUnidad = false }) }
            }
        }
        OutlinedTextField(precio, { precio = it }, label = { Text("Precio unitario *") }, modifier = Modifier.fillMaxWidth())
        AnimatedVisibility(error != null) { Text(error.orEmpty(), color = MaterialTheme.colorScheme.error) }
        Button(onClick = {
            error = onSave(descripcion, cantidad, unidad, precio, catalogUuid)
            if (error == null) onBack()
        }, modifier = Modifier.fillMaxWidth()) { Text("Agregar") }
    }
}

@Composable
fun SeguimientoScreen(
    cotizacion: Cotizacion?,
    items: List<ItemCotizacion>,
    perfil: Perfil,
    seguimientos: List<FollowUpDto> = emptyList(),
    errorRemoto: String? = null,
    onRegistrar: suspend (String, String) -> String? = { _, _ -> "No disponible" },
    onCambiarEstado: suspend (EstadoCotizacion) -> String? = { "No disponible" },
    onEnvio: () -> Unit,
    onEditar: () -> Unit,
    onBack: () -> Unit
) {
    val nf = NumberFormat.getNumberInstance(Locale("es", "CL"))
    val scope = rememberCoroutineScope()
    var channel by remember { mutableStateOf("WHATSAPP") }
    var showChannel by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Page("Detalle y seguimiento", onBack) {
        if (cotizacion == null) {
            Text("Selecciona una cotización")
            return@Page
        }
        Text("COT-${cotizacion.id}", style = MaterialTheme.typography.headlineSmall)
        Text(cotizacion.clienteNombre)
        EstadoChip(cotizacion.estado)
        SyncText(cotizacion.sync)
        HorizontalDivider()
        Text("Detalle", style = MaterialTheme.typography.titleMedium)
        items.forEach { Text("• ${it.descripcion}: ${it.cantidad} ${it.unidad} × ${nf.format(it.precioUnitario)}") }
        val total = calcularTotales(items, cotizacion.descuentoPct, cotizacion.impuestoPct).total
        Text("Total: ${nf.format(total)} ${cotizacion.moneda}", style = MaterialTheme.typography.titleLarge)
        Text("Flujo: Borrador → Enviada → Aceptada/Rechazada → Facturada", style = MaterialTheme.typography.bodySmall)
        if (perfil == Perfil.USUARIO && cotizacion.estado == EstadoCotizacion.BORRADOR) {
            OutlinedButton(onClick = onEditar, modifier = Modifier.fillMaxWidth()) { Text("Editar borrador") }
            OutlinedButton(onClick = onEnvio, modifier = Modifier.fillMaxWidth()) { Text("Documento y envío") }
        } else if (perfil == Perfil.USUARIO && cotizacion.estado == EstadoCotizacion.ENVIADA) {
            OutlinedButton(onClick = onEnvio, modifier = Modifier.fillMaxWidth()) { Text("Ver / guardar documento") }
        }
        if (cotizacion.estado == EstadoCotizacion.ENVIADA && perfil in setOf(Perfil.ADMINISTRADOR, Perfil.USUARIO)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = !loading, onClick = {
                    scope.launch { loading = true; try { error = onCambiarEstado(EstadoCotizacion.ACEPTADA) } finally { loading = false } }
                }, modifier = Modifier.weight(1f)) { Text("Aceptar") }
                OutlinedButton(enabled = !loading, onClick = {
                    scope.launch { loading = true; try { error = onCambiarEstado(EstadoCotizacion.RECHAZADA) } finally { loading = false } }
                }, modifier = Modifier.weight(1f)) { Text("Rechazar") }
            }
        }
        if (cotizacion.remoteId != null) {
            HorizontalDivider()
            Text("Seguimientos registrados en servidor", style = MaterialTheme.typography.titleMedium)
            if (seguimientos.isEmpty()) Text("Sin seguimientos")
            seguimientos.takeLast(8).forEach { seguimiento ->
                Text("${seguimiento.channel}: ${seguimiento.note}", style = MaterialTheme.typography.bodySmall)
            }
            if (perfil in setOf(Perfil.ADMINISTRADOR, Perfil.USUARIO)) {
                Text("Registrar contacto")
                Box {
                    OutlinedButton(onClick = { showChannel = true }, modifier = Modifier.fillMaxWidth()) { Text("Canal: $channel") }
                    DropdownMenu(expanded = showChannel, onDismissRequest = { showChannel = false }) {
                        listOf("WHATSAPP", "CORREO", "TELEFONO", "REUNION", "OTRO").forEach { option ->
                            DropdownMenuItem(text = { Text(option) }, onClick = { channel = option; showChannel = false })
                        }
                    }
                }
                OutlinedTextField(note, { note = it }, label = { Text("Nota del seguimiento") }, modifier = Modifier.fillMaxWidth())
                Button(enabled = !loading && note.isNotBlank(), onClick = {
                    scope.launch {
                        loading = true
                        try { error = onRegistrar(channel, note); if (error == null) note = "" }
                        finally { loading = false }
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("Registrar seguimiento") }
            }
        } else {
            Text("El seguimiento requiere sincronizar la cotización.")
        }
        (error ?: errorRemoto)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
fun EnvioScreen(
    cotizacion: Cotizacion?,
    items: List<ItemCotizacion>,
    destinatarioInicial: String,
    onMarkSent: suspend () -> String?,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var documentoUri by remember { mutableStateOf<Uri?>(null) }
    var destinatario by remember(cotizacion?.id) { mutableStateOf(destinatarioInicial) }
    var asunto by remember(cotizacion?.id) { mutableStateOf("Cotización COT-${cotizacion?.id ?: ""} - Trato Hecho") }
    var mensaje by remember { mutableStateOf("Estimado cliente, adjunto encontrará la cotización solicitada.") }
    var enviado by remember { mutableStateOf(false) }
    var compartido by remember { mutableStateOf(false) }
    var errorEnvio by remember { mutableStateOf<String?>(null) }
    var enviando by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null && cotizacion != null) {
            CotizacionPdfExporter.exportar(context, uri, cotizacion, items)
            documentoUri = uri
        }
    }

    Page(if (enviado) "Confirmación de envío" else "Enviar cotización", onBack) {
        if (cotizacion == null) {
            Text("No hay cotización seleccionada")
            return@Page
        }

        if (enviado) {
            Spacer(Modifier.height(24.dp))
            Text("✓", style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.primary)
            Text("¡Cotización enviada!", style = MaterialTheme.typography.headlineMedium)
            Text("La cotización COT-${cotizacion.id} fue preparada para envío correctamente.")
            Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Volver a mis cotizaciones") }
        } else {
            OutlinedTextField(destinatario, { destinatario = it }, label = { Text("Destinatario *") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(asunto, { asunto = it }, label = { Text("Asunto *") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(mensaje, { mensaje = it }, label = { Text("Mensaje") }, minLines = 3, modifier = Modifier.fillMaxWidth())

            Button(onClick = { create.launch("Cotizacion_COT-${cotizacion.id}.pdf") }, modifier = Modifier.fillMaxWidth()) {
                Text(if (documentoUri == null) "Generar PDF adjunto" else "PDF generado · volver a guardar")
            }
            documentoUri?.let { Text("Documento listo para adjuntar", color = MaterialTheme.colorScheme.primary) }

            Button(
                enabled = documentoUri != null && destinatario.isNotBlank() && asunto.isNotBlank() && !enviando,
                onClick = {
                    scope.launch {
                        enviando = true
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/pdf"
                            putExtra(Intent.EXTRA_EMAIL, arrayOf(destinatario))
                            putExtra(Intent.EXTRA_SUBJECT, asunto)
                            putExtra(Intent.EXTRA_TEXT, mensaje)
                            putExtra(Intent.EXTRA_STREAM, documentoUri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        try {
                            context.startActivity(Intent.createChooser(intent, "Enviar cotización"))
                            compartido = true
                        } catch (e: Exception) {
                            errorEnvio = "No hay una aplicación compatible para compartir el PDF."
                        } finally { enviando = false }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (enviando) "Preparando envío…" else "Abrir aplicación de correo") }
            if (compartido) {
                Text("Abrir correo no confirma el envío. Confirma solo después de enviarlo realmente.")
                Button(enabled = !enviando, onClick = {
                    scope.launch {
                        enviando = true
                        try {
                            errorEnvio = onMarkSent()
                            if (errorEnvio == null) enviado = true
                        } finally { enviando = false }
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("Confirmar que envié la cotización") }
            }
            errorEnvio?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}
