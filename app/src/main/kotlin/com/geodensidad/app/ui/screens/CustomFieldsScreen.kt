package com.geodensidad.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.geodensidad.app.AppContainer
import com.geodensidad.app.data.CustomFieldEntity
import com.geodensidad.app.domain.*
import com.geodensidad.app.ui.appViewModel
import com.geodensidad.app.ui.components.*
import com.geodensidad.app.ui.theme.Condor
import com.geodensidad.core.number.DecimalText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

class CustomFieldsViewModel(private val c: AppContainer) : ViewModel() {
    val fields = c.db.customFields().observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val message = MutableStateFlow<String?>(null)
    fun save(f: CustomFieldEntity, onOk: () -> Unit) = viewModelScope.launch { runCatching { c.repo.saveCustomField(f) }.onSuccess { onOk() }.onFailure { message.value = it.message } }
}

@Composable
fun CustomFieldsScreen(back: () -> Unit) {
    val vm = appViewModel { c, _ -> CustomFieldsViewModel(c) }
    val fields by vm.fields.collectAsStateWithLifecycle()
    var edit by remember { mutableStateOf<CustomFieldEntity?>(null) }
    var create by remember { mutableStateOf(false) }
    val snack = remember { SnackbarHostState() }
    ErrorSnack(vm.message.collectAsStateWithLifecycle().value, { vm.message.value = null }, snack)
    Scaffold(topBar = { AppBar("Campos personalizados", "Información complementaria sin cambiar el código", back) },
        floatingActionButton = { ExtendedFloatingActionButton(onClick = { create = true }, icon = { Icon(Icons.Default.Add, null) }, text = { Text("Nuevo campo") }) },
        snackbarHost = { SnackbarHost(snack) }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
            item { Banner("Los campos personalizados no modifican el motor de cálculo. Los valores se guardan por identificador estable: cambiar la etiqueta no afecta a los datos. Los campos usados no se eliminan, se archivan.", Condor.Steel) }
            if (fields.isEmpty()) item { EmptyState(Icons.Default.DynamicForm, "Sin campos", "Ej.: temperatura ambiente, operador del cono, condición de la superficie.") }
            items(fields, key = { it.id }) { f ->
                ListItem(headlineContent = { Text("${f.label}${if (f.required) " *" else ""}") },
                    supportingContent = { Text("${f.key} · ${CustomFieldType.valueOf(f.type).label}${f.unit.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""} · ${CustomFieldEntityType.valueOf(f.entity).label} · v${f.version}" +
                        "${if (f.includeInPdf) " · en PDF" else ""}${if (f.includeInTable) " · en tablas" else ""}") },
                    trailingContent = { if (f.archived) Pill("Archivado", Icons.Default.Archive, Condor.Slate) }, modifier = Modifier.clickable { edit = f })
                HorizontalDivider()
            }
        }
    }
    if (create || edit != null) FieldDialog(edit, fields.size, { create = false; edit = null }) { vm.save(it) { create = false; edit = null } }
}

@Composable
private fun FieldDialog(f: CustomFieldEntity?, count: Int, dismiss: () -> Unit, save: (CustomFieldEntity) -> Unit) {
    var key by remember { mutableStateOf(f?.key ?: "") }; var label by remember { mutableStateOf(f?.label ?: "") }
    var desc by remember { mutableStateOf(f?.description ?: "") }; var type by remember { mutableStateOf(f?.type?.let { CustomFieldType.valueOf(it) } ?: CustomFieldType.TEXT) }
    var entity by remember { mutableStateOf(f?.entity?.let { CustomFieldEntityType.valueOf(it) } ?: CustomFieldEntityType.SAMPLE) }
    var unit by remember { mutableStateOf(f?.unit ?: "") }; var required by remember { mutableStateOf(f?.required ?: false) }
    var options by remember { mutableStateOf(f?.optionsJson?.let { runCatching { AppJson.decodeFromString(ListSerializer(String.serializer()), it).joinToString(", ") }.getOrNull() } ?: "") }
    var min by remember { mutableStateOf(DecimalText.storedToEditText(f?.minValue)) }; var max by remember { mutableStateOf(DecimalText.storedToEditText(f?.maxValue)) }
    var order by remember { mutableStateOf((f?.sortOrder ?: (count + 1)).toString()) }
    var pdf by remember { mutableStateOf(f?.includeInPdf ?: true) }; var table by remember { mutableStateOf(f?.includeInTable ?: false) }
    var arch by remember { mutableStateOf(f?.archived ?: false) }
    FormDialog(if (f == null) "Nuevo campo" else "Editar campo", dismiss, {
        save(CustomFieldEntity(f?.id ?: newId(), key.trim(), entity.name, label.trim(), desc, type.name, unit, required,
            AppJson.encodeToString(ListSerializer(String.serializer()), options.split(',').map { it.trim() }.filter { it.isNotEmpty() }),
            DecimalText.toCanonicalOrNull(min), DecimalText.toCanonicalOrNull(max), order.toIntOrNull() ?: 0, pdf, table, f?.version ?: 1, arch, Clock.nowMillis()))
    }, key.isNotBlank() && label.isNotBlank()) {
        TextInput("Identificador estable *", key, { key = it.lowercase() }, enabled = f == null, supporting = "Minúsculas y guion bajo; no cambia nunca (ej. temperatura_ambiente)")
        TextInput("Etiqueta *", label, { label = it }); TextInput("Descripción", desc, { desc = it })
        Dropdown("Tipo", CustomFieldType.entries, type, { it.label }, { it?.let { t -> type = t } }, allowNone = false, enabled = f == null)
        Text("Entidad: ${entity.label}", style = MaterialTheme.typography.bodyMedium)
        if (type == CustomFieldType.NUMBER || type == CustomFieldType.TEXT) TextInput("Unidad", unit, { unit = it })
        if (type == CustomFieldType.CHOICE) TextInput("Opciones (separadas por coma)", options, { options = it })
        if (type == CustomFieldType.NUMBER) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberInput("Mínimo", min, unit, { min = it }, Modifier.weight(1f)); NumberInput("Máximo", max, unit, { max = it }, Modifier.weight(1f))
        }
        TextInput("Orden", order, { order = it.filter(Char::isDigit) })
        listOf(Triple("Obligatorio para completar", required) { v: Boolean -> required = v }, Triple("Incluir en PDF", pdf) { v: Boolean -> pdf = v },
            Triple("Incluir en tablas del informe global", table) { v: Boolean -> table = v }, Triple("Archivado", arch) { v: Boolean -> arch = v }).forEach { (t, v, set) ->
            Row(verticalAlignment = Alignment.CenterVertically) { Switch(v, set); Spacer(Modifier.width(8.dp)); Text(t) }
        }
    }
}
