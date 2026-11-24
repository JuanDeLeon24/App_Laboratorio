package com.geodensidad.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.navigation.NavController
import com.geodensidad.app.AppContainer
import com.geodensidad.app.data.ActivityWithProject
import com.geodensidad.app.data.ProjectEntity
import com.geodensidad.app.domain.ActivityStatus
import com.geodensidad.app.ui.Routes
import com.geodensidad.app.ui.appViewModel
import com.geodensidad.app.ui.components.*
import com.geodensidad.app.ui.theme.Condor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(private val c: AppContainer) : ViewModel() {
    val projects = c.db.projects().observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val openActivities = c.db.activities().observeOpen().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val busy = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)

    fun createProject(code: String, name: String, client: String, contractor: String, location: String, onDone: (String) -> Unit) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            runCatching { c.repo.createProject(code, name, client, contractor, location) }.onSuccess(onDone).onFailure { message.value = it.message }
            busy.value = false
        }
    }

    fun loadDemo(onDone: (String) -> Unit) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            runCatching { c.demo.load() }.onSuccess { message.value = "Proyecto DEMOSTRATIVO cargado con 10 muestras de referencia"; onDone(it) }.onFailure { message.value = it.message }
            busy.value = false
        }
    }
}

@Composable
fun HomeScreen(nav: NavController) {
    val vm = appViewModel { c, _ -> HomeViewModel(c) }
    val projects by vm.projects.collectAsStateWithLifecycle()
    val open by vm.openActivities.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val msg by vm.message.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    ErrorSnack(msg, { vm.message.value = null }, snack)
    var newProject by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var confirmDemo by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            AppBar("GeoDensidad", "Ensayos de densidad – cono de arena") {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Menú") }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text("Catálogos") }, leadingIcon = { Icon(Icons.Default.Inventory2, null) }, onClick = { menu = false; nav.navigate(Routes.CATALOGS) })
                    DropdownMenuItem(text = { Text("Campos personalizados") }, leadingIcon = { Icon(Icons.Default.DynamicForm, null) }, onClick = { menu = false; nav.navigate(Routes.FIELDS) })
                    DropdownMenuItem(text = { Text("Respaldos") }, leadingIcon = { Icon(Icons.Default.Backup, null) }, onClick = { menu = false; nav.navigate(Routes.BACKUPS) })
                    DropdownMenuItem(text = { Text("Configuración y versiones") }, leadingIcon = { Icon(Icons.Default.Settings, null) }, onClick = { menu = false; nav.navigate(Routes.SETTINGS) })
                    DropdownMenuItem(text = { Text("Historial reciente") }, leadingIcon = { Icon(Icons.Default.History, null) }, onClick = { menu = false; nav.navigate(Routes.history("all")) })
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text("Cargar proyecto demostrativo") }, leadingIcon = { Icon(Icons.Default.Science, null) }, onClick = { menu = false; confirmDemo = true })
                }
            }
        },
        floatingActionButton = { ExtendedFloatingActionButton(onClick = { newProject = true }, icon = { Icon(Icons.Default.Add, null) }, text = { Text("Nuevo proyecto") }) },
        snackbarHost = { SnackbarHost(snack) },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
            if (open.isNotEmpty()) {
                item { Text("Actividades abiertas", style = MaterialTheme.typography.titleSmall, color = Condor.Steel, modifier = Modifier.padding(start = 16.dp, top = 12.dp)) }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(open, key = { it.id }) { a -> OpenActivityCard(a) { nav.navigate(Routes.activity(a.id)) } }
                    }
                }
            }
            item { Text("Proyectos", style = MaterialTheme.typography.titleSmall, color = Condor.Steel, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)) }
            if (projects.isEmpty()) item {
                EmptyState(Icons.Default.FolderOpen, "Sin proyectos", "Cree un proyecto para empezar. También puede cargar un proyecto demostrativo (separado de los datos reales) desde el menú.")
            }
            items(projects, key = { it.id }) { p -> ProjectRow(p) { nav.navigate(Routes.project(p.id)) } }
        }
    }
    if (newProject) ProjectDialog(null, busy, onDismiss = { newProject = false }) { code, name, client, contractor, loc, _, _ ->
        vm.createProject(code, name, client, contractor, loc) { newProject = false; nav.navigate(Routes.project(it)) }
    }
    if (confirmDemo) ConfirmDialog("Proyecto demostrativo", "Se creará un proyecto identificado como DEMO con las 10 muestras del archivo de origen, catálogos demostrativos y dos lotes. No se mezcla con proyectos reales y puede archivarse.", "Cargar",
        onConfirm = { vm.loadDemo { nav.navigate(Routes.project(it)) } }, onDismiss = { confirmDemo = false })
}

@Composable
private fun OpenActivityCard(a: ActivityWithProject, onClick: () -> Unit) {
    ElevatedCard(Modifier.width(240.dp).clickable(onClick = onClick)) {
        Column(Modifier.padding(12.dp)) {
            Text(a.name, style = MaterialTheme.typography.titleSmall, maxLines = 2)
            Text(a.projectName, style = MaterialTheme.typography.bodySmall, color = Condor.Steel, maxLines = 1)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill(ActivityStatus.of(a.status).label, if (a.status == "OPEN") Icons.Default.PlayCircle else Icons.Default.PauseCircle, Condor.Navy)
                if (a.draftCount > 0) Pill("${a.draftCount} borradores", Icons.Default.EditNote, Condor.Warn)
            }
            Text("${a.sampleCount} muestras", style = MaterialTheme.typography.labelSmall, color = Condor.Steel, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun ProjectRow(p: ProjectEntity, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(p.name) },
        supportingContent = { Text(listOf(p.code, p.client, p.location).filter { it.isNotBlank() }.joinToString(" · ")) },
        leadingContent = { Icon(if (p.isDemo) Icons.Default.Science else Icons.Default.Engineering, null, tint = Condor.Navy) },
        trailingContent = { if (p.isDemo) Pill("DEMO", null, Condor.Warn) },
        modifier = Modifier.clickable(onClick = onClick),
    )
    HorizontalDivider()
}

@Composable
fun ProjectDialog(p: ProjectEntity?, busy: Boolean, onDismiss: () -> Unit, onSave: (String, String, String, String, String, String, String) -> Unit) {
    var code by remember { mutableStateOf(p?.code ?: "") }
    var name by remember { mutableStateOf(p?.name ?: "") }
    var client by remember { mutableStateOf(p?.client ?: "") }
    var contractor by remember { mutableStateOf(p?.contractor ?: "") }
    var loc by remember { mutableStateOf(p?.location ?: "") }
    var desc by remember { mutableStateOf(p?.description ?: "") }
    var resp by remember { mutableStateOf(p?.responsibles ?: "") }
    FormDialog(if (p == null) "Nuevo proyecto" else "Editar proyecto", onDismiss, { onSave(code, name, client, contractor, loc, desc, resp) }, saveEnabled = name.isNotBlank() && !busy) {
        TextInput("Código", code, { code = it })
        TextInput("Nombre *", name, { name = it })
        TextInput("Cliente o entidad", client, { client = it })
        TextInput("Contratista", contractor, { contractor = it })
        TextInput("Ubicación general", loc, { loc = it })
        TextInput("Responsables", resp, { resp = it })
        TextInput("Descripción", desc, { desc = it }, singleLine = false, minLines = 2)
    }
}
