package com.example.tratohecho.ui.screens.facturacion

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.tratohecho.data.model.*
import com.example.tratohecho.data.remote.InvoiceTransitions
import com.example.tratohecho.ui.screens.comun.EstadoChip
import com.example.tratohecho.ui.screens.comun.Page
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun FacturacionScreen(cotizaciones: List<Cotizacion>, onSelect: (Long) -> Unit, onBack: () -> Unit) {
    val aceptadas = cotizaciones.filter { it.estado == EstadoCotizacion.ACEPTADA || it.estado == EstadoCotizacion.FACTURADA }
    Page("Cotizaciones aceptadas", onBack) {
        if (aceptadas.isEmpty()) Text("No hay cotizaciones aceptadas disponibles.")
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(aceptadas, key = { it.id }) { c ->
                Card(onClick = { onSelect(c.id) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("COT-${c.id}", style = MaterialTheme.typography.titleMedium)
                            EstadoChip(c.estado)
                        }
                        Text(c.clienteNombre)
                        Text(if (c.remoteId == null) "Solo local · requiere sincronización" else c.fecha,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
fun FacturacionDetalleScreen(
    cotizacion: Cotizacion?, ordenes: List<OrdenCompra>, facturas: List<Factura>,
    goOrden: () -> Unit, goFactura: () -> Unit,
    onStatus: suspend (Factura, String) -> String?,
    onUpdate: suspend (Factura, String?, String) -> String?,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<Long?>(null) }
    var due by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    Page("Detalle facturación", onBack) {
        if (cotizacion == null) {
            Text("No hay cotización seleccionada")
            return@Page
        }
        Text("COT-${cotizacion.id}", style = MaterialTheme.typography.headlineSmall)
        Text(cotizacion.clienteNombre)
        EstadoChip(cotizacion.estado)
        if (cotizacion.remoteId == null) Text("Cotización local: sincronízala antes de generar facturas.", color = MaterialTheme.colorScheme.error)
        HorizontalDivider()
        Text("Órdenes de compra (solo en este dispositivo)", style = MaterialTheme.typography.titleMedium)
        val ocCot = ordenes.filter { it.cotizacionId == cotizacion.id }
        if (ocCot.isEmpty()) Text("Sin órdenes registradas")
        ocCot.forEach { oc ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(oc.numero, style = MaterialTheme.typography.titleSmall)
                    Text(oc.fecha)
                    if (oc.observaciones.isNotBlank()) Text(oc.observaciones, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        OutlinedButton(onClick = goOrden, modifier = Modifier.fillMaxWidth()) { Text("+ Registrar OC local") }

        Text("Facturas del backend", style = MaterialTheme.typography.titleMedium)
        val facCot = facturas.filter { it.cotizacionId == cotizacion.id }
        if (facCot.isEmpty()) Text("Sin facturas registradas")
        facCot.forEach { fac ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(fac.numero, style = MaterialTheme.typography.titleSmall)
                    Text("Fecha: ${fac.fecha} · Estado: ${fac.estado}")
                    if (fac.observaciones.isNotBlank()) Text(fac.observaciones, style = MaterialTheme.typography.bodySmall)
                    if (fac.remoteId == null) {
                        Text("Registro antiguo solo local · no se puede gestionar en el servidor")
                    } else {
                        InvoiceTransitions.allowed(fac.estado).forEach { next ->
                            OutlinedButton(enabled = !busy, onClick = {
                                scope.launch {
                                    busy = true
                                    try { error = onStatus(fac, next) } finally { busy = false }
                                }
                            }, modifier = Modifier.fillMaxWidth()) {
                                Text(when (next) { "PAGADA" -> "Marcar PAGADA"; "EMITIDA" -> "Emitir factura"; else -> "Anular factura" })
                            }
                        }
                        if (fac.estado == "BORRADOR" && editing == fac.id) {
                            OutlinedTextField(due, { due = it }, label = { Text("Vencimiento AAAA-MM-DD (opcional)") }, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(notes, { notes = it }, label = { Text("Observaciones") }, modifier = Modifier.fillMaxWidth())
                            Button(enabled = !busy, onClick = {
                                scope.launch {
                                    busy = true
                                    try {
                                        error = onUpdate(fac, due.takeIf { it.isNotBlank() }, notes)
                                        if (error == null) editing = null
                                    } finally { busy = false }
                                }
                            }, modifier = Modifier.fillMaxWidth()) { Text("Actualizar factura") }
                        } else if (fac.estado == "BORRADOR") {
                            TextButton(onClick = { editing = fac.id; due = fac.vencimiento.orEmpty(); notes = fac.observaciones }) {
                                Text("Editar vencimiento y notas")
                            }
                        }
                    }
                }
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = goFactura, enabled = cotizacion.remoteId != null && !busy && facCot.none { it.remoteId != null && it.estado != "ANULADA" }, modifier = Modifier.fillMaxWidth()) {
            Text("+ Crear factura en el backend")
        }
    }
}

@Composable
fun CrearFacturaScreen(
    cotizacionId: Long,
    onSave: suspend (Long, String, String?, String) -> String?,
    onBack: () -> Unit
) {
    var fecha by remember { mutableStateOf(LocalDate.now(ZoneId.of("America/Santiago")).toString()) }
    var vence by remember { mutableStateOf("") }
    var notas by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    Page("Crear factura", onBack) {
        Text("Cotización: COT-$cotizacionId")
        Text("El folio lo asigna Billing Service; no se ingresa manualmente.")
        OutlinedTextField(fecha, { fecha = it }, label = { Text("Emisión AAAA-MM-DD") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(vence, { vence = it }, label = { Text("Vencimiento AAAA-MM-DD (opcional)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(notas, { notas = it }, label = { Text("Observaciones") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(enabled = !busy && fecha.isNotBlank(), onClick = {
            scope.launch {
                busy = true
                try {
                    error = onSave(cotizacionId, fecha, vence.takeIf { it.isNotBlank() }, notas)
                    if (error == null) onBack()
                } finally { busy = false }
            }
        }, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Creando factura..." else "Crear factura en servidor") }
    }
}

@Composable
fun DocumentoFacturacionScreen(
    titulo: String, numeroLabel: String, cotizacionId: Long,
    onSave: (Long, String, String, String) -> String?, onBack: () -> Unit
) {
    var numero by remember { mutableStateOf("") }
    var fecha by remember { mutableStateOf("") }
    var observaciones by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    Page(titulo, onBack) {
        Text("Cotización: COT-$cotizacionId")
        Text("Solo registro local: el backend actual no dispone de endpoint de órdenes de compra.")
        OutlinedTextField(numero, { numero = it }, label = { Text("$numeroLabel *") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(fecha, { fecha = it }, label = { Text("Fecha (AAAA-MM-DD) *") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(observaciones, { observaciones = it }, label = { Text("Observaciones") }, minLines = 2, modifier = Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = {
            error = onSave(cotizacionId, numero, fecha, observaciones)
            if (error == null) onBack()
        }, modifier = Modifier.fillMaxWidth()) { Text("Guardar localmente") }
    }
}
