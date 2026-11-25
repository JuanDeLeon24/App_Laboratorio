package com.geodensidad.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.geodensidad.app.AppContainer
import com.geodensidad.app.data.Registro
import com.geodensidad.app.data.calcular
import com.geodensidad.app.data.conDensidades
import com.geodensidad.app.data.densidades
import com.geodensidad.app.ui.components.*
import com.geodensidad.app.ui.theme.Condor
import com.geodensidad.core.number.Fmt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class RegistrosViewModel(private val c: AppContainer) : ViewModel() {
    val registros = c.db.registros().todos().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val mensaje = MutableStateFlow<String?>(null)

    fun nuevo(abrir: (String) -> Unit) = viewModelScope.launch {
        val r = Registro(fecha = LocalDate.now().toString()).conDensidades(listOf(com.geodensidad.app.data.Densidad()))
        c.db.registros().guardar(r); abrir(r.id)
    }

    /** Copia solo el encabezado y los datos comunes (I, Q, R); no copia mediciones. */
    fun duplicarEncabezado(r: Registro, abrir: (String) -> Unit) = viewModelScope.launch {
        val n = Registro(proyecto = r.proyecto, localizacion = r.localizacion, procedencia = r.procedencia, lado = r.lado,
            fecha = LocalDate.now().toString(), parteRelleno = r.parteRelleno, lote = r.lote, i = r.i, q = r.q, r = r.r,
            elaboro = r.elaboro, aprobo = r.aprobo).conDensidades(listOf(com.geodensidad.app.data.Densidad()))
        c.db.registros().guardar(n); abrir(n.id)
    }

    fun eliminar(r: Registro) = viewModelScope.launch { c.db.registros().eliminar(r) }

    val recibido = c.archivoRecibido

    fun importarExcel(uri: android.net.Uri) = viewModelScope.launch {
        c.archivoRecibido.value = null
        runCatching { c.context.contentResolver.openInputStream(uri)!!.use { c.intercambio.importar(it) } }
            .onSuccess { mensaje.value = it.texto }
            .onFailure { mensaje.value = "No se importó: ${it.message}" }
    }

    fun exportarExcel(uri: android.net.Uri, lista: List<Registro>) = viewModelScope.launch {
        runCatching { c.context.contentResolver.openOutputStream(uri, "w")!!.use { c.intercambio.exportar(lista, it) } }
            .onSuccess { mensaje.value = "Excel guardado con ${lista.size} registro(s)" }
            .onFailure { mensaje.value = "No se exportó: ${it.message}" }
    }

    fun exportar(uri: android.net.Uri) = viewModelScope.launch {
        runCatching { c.respaldo.exportar(uri) }.onSuccess { mensaje.value = "Respaldo guardado ($it registros). El archivo no está cifrado." }
            .onFailure { mensaje.value = "No se pudo guardar: ${it.message}" }
    }

    fun restaurar(uri: android.net.Uri) = viewModelScope.launch {
        runCatching { c.context.contentResolver.openInputStream(uri)!!.use { c.respaldo.restaurar(it) } }
            .onSuccess { mensaje.value = "Respaldo restaurado: $it registros" }
            .onFailure { mensaje.value = "No se restauró: ${it.message}" }
    }
}

@Composable
fun RegistrosScreen(abrir: (String) -> Unit) {
    val vm = appViewModel { c, _ -> RegistrosViewModel(c) }
    val lista by vm.registros.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    ErrorSnack(vm.mensaje.collectAsStateWithLifecycle().value, { vm.mensaje.value = null }, snack)
    var menu by remember { mutableStateOf(false) }
    var confirmarRestaurar by remember { mutableStateOf<android.net.Uri?>(null) }
    var eliminar by remember { mutableStateOf<Registro?>(null) }
    var buscar by remember { mutableStateOf("") }
    val guardar = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { it?.let(vm::exportar) }
    val abrirArchivo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { confirmarRestaurar = it }
    val filtrada = lista.filter { r -> buscar.isBlank() || listOf(r.registro, r.proyecto, r.localizacion, r.lote, r.fecha).any { it.contains(buscar, true) } }
    val xlsx = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    val importar = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::importarExcel) }
    val exportar = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(xlsx)) { u -> u?.let { vm.exportarExcel(it, filtrada) } }
    val recibido by vm.recibido.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            AppBar("GeoDensidad", "Densidad en el terreno – cono de arena") {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Menú") }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text("Importar Excel (.xlsx)") }, leadingIcon = { Icon(Icons.Default.FileOpen, null) },
                        onClick = { menu = false; importar.launch(arrayOf(xlsx, "application/octet-stream", "*/*")) })
                    DropdownMenuItem(text = { Text(if (buscar.isBlank()) "Exportar todos a Excel (${filtrada.size})" else "Exportar los ${filtrada.size} de la búsqueda a Excel") },
                        leadingIcon = { Icon(Icons.Default.TableView, null) }, enabled = filtrada.isNotEmpty(),
                        onClick = { menu = false; exportar.launch("Densidades-${if (buscar.isBlank()) LocalDate.now().toString() else buscar.replace(Regex("[^A-Za-z0-9-]"), "_")}.xlsx") })
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text("Guardar respaldo") }, leadingIcon = { Icon(Icons.Default.SaveAlt, null) },
                        onClick = { menu = false; guardar.launch("GeoDensidad-respaldo-${LocalDate.now()}.zip") })
                    DropdownMenuItem(text = { Text("Restaurar respaldo") }, leadingIcon = { Icon(Icons.Default.Restore, null) },
                        onClick = { menu = false; abrirArchivo.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) })
                }
            }
        },
        floatingActionButton = { LabeledFab("Nuevo registro", Icons.Default.Add, { vm.nuevo(abrir) }) },
        snackbarHost = { SnackbarHost(snack) },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize().imePadding(), contentAlignment = androidx.compose.ui.Alignment.TopCenter) {
        LazyColumn(Modifier.widthIn(max = 760.dp).fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
            item {
                OutlinedTextField(buscar, { buscar = it }, Modifier.fillMaxWidth().padding(12.dp), placeholder = { Text("Buscar por registro, proyecto, lote o fecha") },
                    leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true)
            }
            if (lista.isEmpty()) item { EmptyState(Icons.Default.Description, "Sin registros", "Toque «Nuevo registro». Cada registro es una hoja del formato con hasta 10 densidades.") }
            items(filtrada, key = { it.id }) { r ->
                val dens = r.densidades
                val comp = dens.mapNotNull { r.calcular(it).value("S") }
                var m by remember { mutableStateOf(false) }
                ListItem(
                    headlineContent = { Text("Registro ${r.registro.ifBlank { "sin número" }} · ${r.fecha}", fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text(listOf(r.proyecto, r.localizacion, r.lote.takeIf { it.isNotBlank() }?.let { "Lote $it" }).filterNotNull().filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "Sin encabezado" }) },
                    trailingContent = {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                                Text("${dens.size} densidades", style = MaterialTheme.typography.labelMedium, color = Condor.Steel)
                                Text(if (comp.isEmpty()) "—" else "${Fmt.num(comp.average(), 2)} %", style = MaterialTheme.typography.titleSmall, color = Condor.Navy)
                            }
                            IconButton(onClick = { m = true }) { Icon(Icons.Default.MoreVert, "Opciones del registro") }
                            DropdownMenu(m, { m = false }) {
                                DropdownMenuItem(text = { Text("Nuevo registro con este encabezado") }, onClick = { m = false; vm.duplicarEncabezado(r, abrir) })
                                DropdownMenuItem(text = { Text("Eliminar") }, onClick = { m = false; eliminar = r })
                            }
                        }
                    },
                    modifier = Modifier.clickable { abrir(r.id) },
                )
                HorizontalDivider()
            }
        }
        }
    }
    confirmarRestaurar?.let { uri ->
        ConfirmDialog("Restaurar respaldo", "Los registros actuales se reemplazarán por los del archivo. Antes se guarda una copia automática dentro de la app.", "Restaurar", true,
            onConfirm = { vm.restaurar(uri) }, onDismiss = { confirmarRestaurar = null })
    }
    recibido?.let { uri ->
        ConfirmDialog("Importar Excel", "¿Importar las hojas 'Cono de arena' de este archivo? Si un registro ya existe (exportado desde GeoDensidad), se actualiza.", "Importar",
            onConfirm = { vm.importarExcel(uri) }, onDismiss = { vm.recibido.value = null })
    }
    eliminar?.let { r ->
        ConfirmDialog("Eliminar registro", "Se eliminará el registro ${r.registro.ifBlank { "sin número" }} con sus ${r.densidades.size} densidades. Esta acción no se puede deshacer.", "Eliminar", true,
            onConfirm = { vm.eliminar(r) }, onDismiss = { eliminar = null })
    }
}
