package com.geodensidad.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geodensidad.app.ui.theme.Condor
import kotlinx.coroutines.delay

/**
 * Campo numérico que NO abre el teclado del sistema: al tocarlo se activa el teclado propio
 * (solo dígitos y coma). Cuando está activo se desplaza solo para quedar visible sobre el teclado.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CampoNumero(
    simbolo: String, etiqueta: String, unidad: String, texto: String, activo: Boolean, onClick: () -> Unit,
    error: String? = null, ayuda: String? = null, modifier: Modifier = Modifier,
) {
    val requester = remember { BringIntoViewRequester() }
    var alto by remember { mutableIntStateOf(0) }
    val margen = with(LocalDensity.current) { 24.dp.toPx() }
    LaunchedEffect(activo) {
        if (activo) {
            delay(120) // espera a que aparezca el teclado y se reacomode la lista
            requester.bringIntoView(Rect(0f, -margen, 1f, alto + margen))
        }
    }
    val borde = when { error != null -> MaterialTheme.colorScheme.error; activo -> Condor.Navy; else -> Condor.Slate.copy(alpha = 0.6f) }
    Column(modifier.fillMaxWidth().bringIntoViewRequester(requester).onSizeChanged { alto = it.height }) {
        Surface(
            shape = RoundedCornerShape(6.dp), border = BorderStroke(if (activo) 2.dp else 1.dp, borde),
            color = if (activo) Condor.Sky.copy(alpha = 0.25f) else Color.White,
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).testTag("campo_$simbolo")
                .semantics { contentDescription = "$simbolo · $etiqueta: ${texto.ifEmpty { "vacío" }}" },
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                Text("$simbolo · $etiqueta", style = MaterialTheme.typography.labelMedium, color = if (activo) Condor.Navy else Condor.Steel)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(texto.ifEmpty { if (activo) "" else "—" }, style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                        color = if (texto.isEmpty()) Condor.Slate else MaterialTheme.colorScheme.onSurface)
                    if (activo) Box(Modifier.padding(start = 1.dp).width(2.dp).height(22.dp).background(Condor.Navy))
                    Spacer(Modifier.weight(1f))
                    Text(unidad, color = Condor.Steel)
                }
            }
        }
        val pie = error ?: ayuda
        if (pie != null) Text(pie, style = MaterialTheme.typography.bodySmall, color = if (error != null) MaterialTheme.colorScheme.error else Condor.Slate,
            modifier = Modifier.padding(start = 14.dp, top = 2.dp))
    }
}

/** Teclado numérico propio: dígitos, coma (único separador decimal), borrar, anterior, siguiente y listo. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TecladoNumerico(
    titulo: String, onTecla: (Char) -> Unit, onBorrar: () -> Unit, onLimpiar: () -> Unit,
    onAnterior: () -> Unit, onSiguiente: () -> Unit, onListo: () -> Unit, hayAnterior: Boolean, haySiguiente: Boolean,
) {
    val bajo = LocalConfiguration.current.screenHeightDp < 520
    val altoTecla = if (bajo) 40.dp else 52.dp
    Surface(color = Condor.Mist, shadowElevation = 8.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 560.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(titulo, style = MaterialTheme.typography.labelLarge, color = Condor.NavyDeep, modifier = Modifier.weight(1f))
                    TextButton(onClick = onListo, modifier = Modifier.testTag("tecla_listo")) { Text("Listo", fontWeight = FontWeight.Bold) }
                }
                @Composable
                fun RowScope.Tecla(texto: String, tag: String, peso: Float = 1f, color: Color = Color.White, onLong: (() -> Unit)? = null, content: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
                    Surface(shape = RoundedCornerShape(8.dp), color = color, shadowElevation = 1.dp,
                        modifier = Modifier.weight(peso).height(altoTecla).testTag(tag).semantics { contentDescription = texto }
                            .combinedClickable(onClick = onClick, onLongClick = onLong)) {
                        Box(contentAlignment = Alignment.Center) { content?.invoke() ?: Text(texto, fontSize = 22.sp, color = Condor.NavyDeep) }
                    }
                }
                val filas = listOf("789", "456", "123")
                filas.forEachIndexed { i, f ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        f.forEach { ch -> Tecla(ch.toString(), "tecla_$ch") { onTecla(ch) } }
                        when (i) {
                            0 -> Tecla("Borrar", "tecla_borrar", color = Condor.Sky, onLong = onLimpiar, content = { Icon(Icons.AutoMirrored.Filled.Backspace, null, tint = Condor.NavyDeep) }) { onBorrar() }
                            1 -> Tecla("Anterior", "tecla_anterior", color = if (hayAnterior) Condor.Sky else Condor.Mist, content = { Icon(Icons.Default.KeyboardArrowUp, null, tint = Condor.NavyDeep) }) { if (hayAnterior) onAnterior() }
                            else -> Tecla("Limpiar", "tecla_limpiar", color = Condor.Sky, content = { Text("C", fontSize = 20.sp, color = Condor.NavyDeep) }) { onLimpiar() }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Tecla(",", "tecla_coma") { onTecla(',') }
                    Tecla("0", "tecla_0") { onTecla('0') }
                    Tecla(if (haySiguiente) "Siguiente" else "Terminar", "tecla_siguiente", peso = 2f, color = Condor.Navy,
                        content = { Row(verticalAlignment = Alignment.CenterVertically) { Text(if (haySiguiente) "Siguiente" else "Terminar", color = Color.White, fontWeight = FontWeight.SemiBold); Icon(Icons.Default.KeyboardArrowDown, null, tint = Color.White) } }) {
                        if (haySiguiente) onSiguiente() else onListo()
                    }
                }
            }
        }
    }
}

/** Para campos de texto con teclado del sistema: al enfocarlos se desplazan para quedar visibles sobre el teclado. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.visibleAlEnfocar(onEnfocar: () -> Unit = {}): Modifier {
    val requester = remember { BringIntoViewRequester() }
    var enfocado by remember { mutableStateOf(false) }
    LaunchedEffect(enfocado) {
        if (enfocado) { delay(350); requester.bringIntoView(); delay(300); requester.bringIntoView() }
    }
    return this.bringIntoViewRequester(requester).onFocusEvent { if (it.isFocused && !enfocado) onEnfocar(); enfocado = it.isFocused }
}
