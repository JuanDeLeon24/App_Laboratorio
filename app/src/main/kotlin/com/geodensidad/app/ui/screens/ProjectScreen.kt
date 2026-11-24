package com.geodensidad.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.geodensidad.app.AppContainer
import com.geodensidad.app.domain.ActivityStatus
import com.geodensidad.app.domain.SampleStatus
import com.geodensidad.app.ui.Routes
import com.geodensidad.app.ui.appViewModel
import com.geodensidad.app.ui.components.*
import com.geodensidad.app.ui.theme.Condor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProjectViewModel(private val c: AppContainer, h: SavedStateHandle) : ViewModel() {
    val id: String = h["id"]!!
    val project = c.db.projects().observeOne(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val activities = c.db.activities().observeByProject(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val counts = c.db.samples().observeProjectStatusCounts(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val busy = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)

    fun createActivity(name: String, desc: String, onDone: (String) -> Unit) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch { runCatching { c.repo.createActivity(id, name, desc) }.onSuccess(onDone).onFailure { message.value = it.message }; busy.value = false }
    }

    fun save(code: String, name: String, client: String, contractor: String, loc: String, desc: String, resp: String) = viewModelScope.launch {
        project.value?.let { c.repo.updateProject(it.copy(code = code, name = name, client = client, contractor = contractor, location = loc, description = desc, responsibles = resp)) }
    }

    fun archive(archived: Boolean) = viewModelScope.launch { project.value?.let { c.repo.updateProject(it.copy(status = if (archived) "ARCHIVED" else "ACTIVE")) } }
}

@Composable
fun ProjectScreen(nav: NavController, back: () -> Unit) {
    val vm = appViewModel { c, h -> ProjectViewModel(c, h) }
    val p by vm.project.collectAsStateWithLifecycle()
    val acts by vm.activities.collectAsStateWithLifecycle()
    val counts by vm.counts.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var edit by remember { mutableStateOf(false) }
    var newAct by remember { mutableStateOf(false) }
    var confirmArchive by remember { mutableStateOf(false) }
    val snack = remember { SnackbarHostState() }
    ErrorSnack(vm.message.collectAsStateWithLifecycle().value, { vm.message.value = null }, snack)
    Scaffold(
        topBar = { AppBar(p?.name ?: "Proyecto", p?.code, back) {
            IconButton(onClick = { edit = true }) { Icon(Icons.Default.Edit, "Editar proyecto") }
            IconButton(onClick = { confirmArchive = true }) { Icon(if (p?.status == "ARCHIVED") Icons.Default.Unarchive else Icons.Default.Archive, "Archivar") }
        } },
        floatingActionButton = { ExtendedFloatingActionButton(onClick = { newAct = true }, icon = { Icon(Icons.Default.Add, null) }, text = { Text("Nueva actividad") }) },
        snackbarHost = { SnackbarHost(snack) },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
            p?.let { proj ->
                if (proj.isDemo) item { Banner("Proyecto DEMOSTRATIVO: sus datos no corresponden a ensayos reales.", Condor.Warn, Icons.Default.Science) }
                if (proj.status == "ARCHIVED") item { Banner("Proyecto archivado: sus datos se conservan.", Condor.Steel, Icons.Default.Archive) }
                item {
                    SectionCard("Resumen del proyecto", Icons.Default.Dashboard) {
                        KeyValue("Cliente / entidad", proj.client.ifBlank { "—" }); KeyValue("Contratista", proj.contractor.ifBlank { "—" })
                        KeyValue("Ubicación", proj.location.ifBlank { "—" }); KeyValue("Responsables", proj.responsibles.ifBlank { "—" })
                        KeyValue("Actividades", acts.size.toString())
                        KeyValue("Muestras", counts.sumOf { it.n }.toString())
                        SampleStatus.entries.forEach { s -> counts.firstOrNull { it.status == s.name }?.let { KeyValue("· ${s.label}", it.n.toString()) } }
                        if (proj.description.isNotBlank()) Text(proj.description, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            item { Text("Actividades", style = MaterialTheme.typography.titleSmall, color = Condor.Steel, modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 4.dp)) }
            if (acts.isEmpty()) item { EmptyState(Icons.Default.Assignment, "Sin actividades", "Cree una actividad, por ejemplo «Toma de densidades para granulares».") }
            items(acts, key = { it.id }) { a ->
                ListItem(headlineContent = { Text(a.name) }, supportingContent = { Text(a.description.ifBlank { "Cono de arena" }, maxLines = 2) },
                    leadingContent = { Icon(Icons.Default.Assignment, null, tint = Condor.Navy) },
                    trailingContent = { Pill(ActivityStatus.of(a.status).label, null, if (a.status == "OPEN") Condor.Navy else Condor.Slate) },
                    modifier = Modifier.clickable { nav.navigate(Routes.activity(a.id)) })
                HorizontalDivider()
            }
        }
    }
    if (edit) ProjectDialog(p, false, { edit = false }) { code, name, client, contractor, loc, desc, resp -> vm.save(code, name, client, contractor, loc, desc, resp); edit = false }
    if (newAct) {
        var name by remember { mutableStateOf("Toma de densidades para granulares") }
        var desc by remember { mutableStateOf("") }
        FormDialog("Nueva actividad", { newAct = false }, { vm.createActivity(name, desc) { newAct = false; nav.navigate(Routes.activity(it)) } }, name.isNotBlank() && !busy) {
            TextInput("Nombre *", name, { name = it }); TextInput("Descripción", desc, { desc = it }, singleLine = false, minLines = 2)
            Text("Tipo de ensayo: densidad en el terreno – cono de arena (motor cono-arena-legacy).", style = MaterialTheme.typography.bodySmall, color = Condor.Steel)
        }
    }
    if (confirmArchive) ConfirmDialog(if (p?.status == "ARCHIVED") "Reactivar proyecto" else "Archivar proyecto", "Archivar no elimina datos: el proyecto se oculta de la lista principal y se conserva en respaldos.",
        onConfirm = { vm.archive(p?.status != "ARCHIVED") }, onDismiss = { confirmArchive = false })
}
