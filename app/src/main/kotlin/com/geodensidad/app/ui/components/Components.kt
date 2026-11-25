package com.geodensidad.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.geodensidad.app.ui.theme.Condor
import com.geodensidad.core.number.DecimalText
import com.geodensidad.core.number.ParsedDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBar(title: String, subtitle: String? = null, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(
        title = {
            Column {
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                subtitle?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = Condor.Sky, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        },
        navigationIcon = { onBack?.let { IconButton(onClick = it) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } } },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Condor.Navy, titleContentColor = Color.White, navigationIconContentColor = Color.White, actionIconContentColor = Color.White),
    )
}

@Composable
fun Pill(text: String, icon: ImageVector?, color: Color, modifier: Modifier = Modifier) {
    Surface(color = color.copy(alpha = 0.12f), contentColor = color, shape = RoundedCornerShape(50), border = BorderStroke(1.dp, color.copy(alpha = 0.5f)), modifier = modifier) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            icon?.let { Icon(it, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)) }
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun SectionCard(title: String, icon: ImageVector, initiallyExpanded: Boolean = true, badge: String? = null, content: @Composable ColumnScope.() -> Unit) {
    var expanded by rememberSaveable(title) { mutableStateOf(initiallyExpanded) }
    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Condor.Navy)
            Spacer(Modifier.width(10.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, color = Condor.NavyDeep, modifier = Modifier.weight(1f))
            badge?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = Condor.Steel); Spacer(Modifier.width(8.dp)) }
            Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, if (expanded) "Contraer" else "Expandir")
        }
        AnimatedVisibility(expanded) { Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content) }
    }
}

@Composable
fun TextInput(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, singleLine: Boolean = true, supporting: String? = null, minLines: Int = 1) {
    OutlinedTextField(value, onChange, label = { Text(label) }, enabled = enabled, singleLine = singleLine, minLines = minLines, modifier = modifier.fillMaxWidth(),
        supportingText = supporting?.let { { Text(it) } }, keyboardOptions = KeyboardOptions(imeAction = if (singleLine) ImeAction.Next else ImeAction.Default))
}

/**
 * Campo numérico: teclado decimal, unidad visible, error junto al campo y eco de la interpretación.
 * Vacío = dato faltante (nunca cero).
 */
@Composable
fun NumberInput(
    label: String, text: String, unit: String, onChange: (String) -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, error: String? = null, hint: String? = null, symbol: String? = null,
) {
    val parsed = DecimalText.parse(text)
    val parseError = (parsed as? ParsedDecimal.Invalid)?.message
    val shownError = parseError ?: error
    OutlinedTextField(
        value = text, onValueChange = { v -> onChange(v.filter { it.isDigit() || it == ',' || it == '.' || it == '-' }) },
        label = { Text(if (symbol != null) "$symbol · $label" else label) }, enabled = enabled, singleLine = true,
        suffix = { Text(unit, color = Condor.Steel) }, isError = shownError != null,
        supportingText = { Text(shownError ?: hint ?: if (parsed is ParsedDecimal.Empty) "Dato faltante" else "", color = if (shownError != null) MaterialTheme.colorScheme.error else Condor.Slate) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
        modifier = modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> Dropdown(label: String, options: List<T>, selected: T?, display: (T) -> String, onSelect: (T?) -> Unit, enabled: Boolean = true, allowNone: Boolean = true, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(open, { if (enabled) open = it }, modifier) {
        OutlinedTextField(selected?.let(display) ?: "—", {}, readOnly = true, enabled = enabled, label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) }, modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable))
        ExposedDropdownMenu(open, { open = false }) {
            if (allowNone) DropdownMenuItem(text = { Text("— Ninguno —") }, onClick = { onSelect(null); open = false })
            options.forEach { o -> DropdownMenuItem(text = { Text(display(o)) }, onClick = { onSelect(o); open = false }) }
        }
    }
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String = "Confirmar", destructive: Boolean = false, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(text) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirm, color = if (destructive) Condor.Bad else Condor.Navy) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

/** Diálogo que exige un motivo (anulación, nueva revisión, exclusión, corrección manual). */
@Composable
fun ReasonDialog(title: String, text: String, label: String = "Motivo", confirm: String = "Aceptar", onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var reason by rememberSaveable { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(text); TextInput(label, reason, { reason = it }, singleLine = false, minLines = 2) } },
        confirmButton = { TextButton(enabled = reason.isNotBlank(), onClick = { onConfirm(reason.trim()); onDismiss() }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

@Composable
fun FormDialog(title: String, onDismiss: () -> Unit, onSave: () -> Unit, saveEnabled: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp), content = content) },
        confirmButton = { TextButton(enabled = saveEnabled, onClick = onSave) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

@Composable
fun KeyValue(key: String, value: String, modifier: Modifier = Modifier, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Row(modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(key, style = MaterialTheme.typography.bodyMedium, color = Condor.Steel, modifier = Modifier.weight(0.45f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = valueColor, modifier = Modifier.weight(0.55f))
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, text: String, action: (@Composable () -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, Modifier.size(48.dp), tint = Condor.Slate)
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Condor.Steel)
        action?.invoke()
    }
}

@Composable
fun Banner(text: String, color: Color = Condor.Warn, icon: ImageVector = Icons.Default.Info) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).background(color.copy(alpha = 0.10f), RoundedCornerShape(8.dp)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = color); Spacer(Modifier.width(8.dp)); Text(text, color = color, style = MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChipSelector(options: List<T>, selected: Set<T>, label: (T) -> String, onToggle: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { o -> FilterChip(selected = o in selected, onClick = { onToggle(o) }, label = { Text(label(o)) },
            leadingIcon = if (o in selected) { { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) } } else null) }
    }
}

@Composable
fun ErrorSnack(message: String?, onDone: () -> Unit, host: SnackbarHostState) {
    LaunchedEffect(message) { message?.let { host.showSnackbar(it, withDismissAction = true, duration = SnackbarDuration.Long); onDone() } }
}

@Composable
fun rememberToast(): (String) -> Unit {
    val ctx = LocalContext.current
    return remember { { msg: String -> android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_LONG).show() } }
}

/** Botón protegido contra doble pulsación: se deshabilita mientras la acción está en curso. */
@Composable
fun BusyButton(text: String, busy: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true) {
    Button(onClick = onClick, enabled = enabled && !busy, modifier = modifier.heightIn(min = 48.dp)) {
        if (busy) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White); Spacer(Modifier.width(8.dp)) }
        else icon?.let { Icon(it, null); Spacer(Modifier.width(6.dp)) }
        Text(text)
    }
}

/** FAB extendido con etiqueta accesible (Material 3 oculta el texto del FAB a los lectores de pantalla). */
@Composable
fun LabeledFab(text: String, icon: ImageVector, onClick: () -> Unit, expanded: Boolean = true) {
    ExtendedFloatingActionButton(onClick = onClick, icon = { Icon(icon, null) }, text = { Text(text) }, expanded = expanded,
        modifier = Modifier.semantics { contentDescription = text })
}
