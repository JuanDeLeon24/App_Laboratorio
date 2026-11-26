package com.geodensidad.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.geodensidad.app.report.Trazo
import com.geodensidad.app.ui.theme.Condor

/** Recuadro para firmar con el dedo. Los puntos se guardan normalizados (0..1) para no depender del tamaño de pantalla. */
@Composable
fun PadFirma(trazos: SnapshotStateList<Trazo>, modifier: Modifier = Modifier) {
    var actual by remember { mutableStateOf<List<Pair<Float, Float>>>(emptyList()) }
    var tam by remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }
    fun norm(o: Offset) = (o.x / tam.width).coerceIn(0f, 1f) to (o.y / tam.height).coerceIn(0f, 1f)
    Box(modifier.background(Color.White, RoundedCornerShape(8.dp)).border(1.dp, Condor.Slate, RoundedCornerShape(8.dp))) {
        Canvas(Modifier.fillMaxSize().testTag("pad_firma")
            .pointerInput(Unit) { detectTapGestures { o -> trazos.add(listOf(norm(o))) } }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { o -> actual = listOf(norm(o)) },
                    onDrag = { ch, _ -> actual = actual + norm(ch.position) },
                    onDragEnd = { if (actual.isNotEmpty()) trazos.add(actual); actual = emptyList() },
                    onDragCancel = { if (actual.isNotEmpty()) trazos.add(actual); actual = emptyList() },
                )
            }) {
            tam = size
            // línea guía
            drawLine(Condor.Mist, Offset(size.width * 0.06f, size.height * 0.78f), Offset(size.width * 0.94f, size.height * 0.78f), strokeWidth = 2f)
            val estilo = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            (trazos + listOf(actual)).forEach { t ->
                if (t.size == 1) drawCircle(Condor.NavyDeep, 3.5f, Offset(t[0].first * size.width, t[0].second * size.height))
                else if (t.size > 1) {
                    val p = Path(); p.moveTo(t[0].first * size.width, t[0].second * size.height)
                    t.drop(1).forEach { p.lineTo(it.first * size.width, it.second * size.height) }
                    drawPath(p, Condor.NavyDeep, style = estilo)
                }
            }
        }
        if (trazos.isEmpty() && actual.isEmpty()) Text("Firme aquí con el dedo", color = Condor.Slate, modifier = Modifier.align(Alignment.Center))
    }
}

/**
 * Diálogo de firma de una persona: nombre, recuadro, Borrar (empezar de nuevo), Omitir y Aceptar.
 * Ocupa casi toda la pantalla para firmar con comodidad (también en horizontal).
 */
@Composable
fun DialogoFirma(
    titulo: String, nombreInicial: String, paso: String, textoAceptar: String,
    onAceptar: (nombre: String, trazos: List<Trazo>) -> Unit, onOmitir: (nombre: String) -> Unit, onCancelar: () -> Unit,
) {
    val trazos = remember { mutableStateListOf<Trazo>() }
    var nombre by remember { mutableStateOf(nombreInicial) }
    Dialog(onDismissRequest = onCancelar, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
        Surface(shape = RoundedCornerShape(12.dp), color = Color.White, modifier = Modifier.fillMaxWidth(0.96f).fillMaxHeight(0.9f).widthIn(max = 900.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(paso, style = MaterialTheme.typography.labelMedium, color = Condor.Steel)
                Text(titulo, style = MaterialTheme.typography.titleLarge, color = Condor.NavyDeep)
                OutlinedTextField(nombre, { nombre = it }, label = { Text("Nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                PadFirma(trazos, Modifier.fillMaxWidth().weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { trazos.clear() }, enabled = trazos.isNotEmpty(), modifier = Modifier.testTag("firma_borrar")) {
                        Icon(Icons.Default.Delete, null); Spacer(Modifier.width(4.dp)); Text("Borrar")
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { onOmitir(nombre) }, modifier = Modifier.testTag("firma_omitir")) { Text("Sin firma") }
                    Button(onClick = { onAceptar(nombre, trazos.toList()) }, enabled = trazos.isNotEmpty(), modifier = Modifier.testTag("firma_aceptar")) { Text(textoAceptar) }
                }
            }
        }
    }
}
