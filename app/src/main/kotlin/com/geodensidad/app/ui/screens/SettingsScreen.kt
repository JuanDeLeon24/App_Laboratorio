package com.geodensidad.app.ui.screens

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.geodensidad.app.AppContainer
import com.geodensidad.app.BuildConfig
import com.geodensidad.app.data.AppDatabase
import com.geodensidad.app.domain.AppSettings
import com.geodensidad.app.ui.appViewModel
import com.geodensidad.app.ui.components.*
import com.geodensidad.app.ui.theme.Condor
import com.geodensidad.core.backup.BackupArchive
import com.geodensidad.core.calc.ConoArenaLegacy
import com.geodensidad.core.number.DecimalText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class SettingsViewModel(private val c: AppContainer) : ViewModel() {
    val settings = c.settings.flow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())
    val template = c.db.templates().observeActive().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val templates = c.db.templates().observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val message = MutableStateFlow<String?>(null)
    fun update(t: (AppSettings) -> AppSettings) = viewModelScope.launch { c.settings.update(t) }
    fun publish(inst: String, title: String, code: String, ver: String, date: String, logo: Uri?, remove: Boolean) = viewModelScope.launch {
        runCatching { c.templates.publish(inst, title, code, ver, date, logo, remove) }.onSuccess { message.value = "Plantilla v$it publicada. Los informes emitidos conservan su versión." }.onFailure { message.value = it.message }
    }
}

@Composable
fun SettingsScreen(nav: NavController, back: () -> Unit) {
    val vm = appViewModel { c, _ -> SettingsViewModel(c) }
    val s by vm.settings.collectAsStateWithLifecycle()
    val t by vm.template.collectAsStateWithLifecycle()
    val all by vm.templates.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    ErrorSnack(vm.message.collectAsStateWithLifecycle().value, { vm.message.value = null }, snack)
    val locPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.any { it }) vm.update { it.copy(gpsEnabled = true) } else vm.message.value = "Permiso de ubicación denegado: las fotos se guardarán sin coordenadas."
    }
    Scaffold(topBar = { AppBar("Configuración y versiones", null, back) }, snackbarHost = { SnackbarHost(snack) }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
            SectionCard("Responsable y captura", Icons.Default.Person) {
                var name by remember(s.responsible) { mutableStateOf(s.responsible) }
                TextInput("Nombre del responsable local", name, { name = it; vm.update { st -> st.copy(responsible = it) } }, supporting = "Se usa para atribuir acciones en el historial. No es autenticación.")
                var bulk by remember(s.defaultBulk) { mutableStateOf(DecimalText.storedToEditText(s.defaultBulk)) }
                NumberInput("Parámetro bulk predeterminado", bulk, "×1000 kg/m³", { bulk = it; DecimalText.toCanonicalOrNull(it)?.let { c -> vm.update { st -> st.copy(defaultBulk = c) } } }, symbol = "I")
            }
            SectionCard("Informes y fotografías", Icons.Default.PhotoCamera) {
                Text("Fotografías por página en el anexo", style = MaterialTheme.typography.labelLarge)
                Row { listOf(1, 2).forEach { n -> FilterChip(s.photosPerPage == n, { vm.update { it.copy(photosPerPage = n) } }, { Text("$n por página") }, Modifier.padding(end = 8.dp)) } }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(s.gpsEnabled, { on -> if (on) locPerm.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)) else vm.update { it.copy(gpsEnabled = false) } })
                    Spacer(Modifier.width(8.dp)); Text("Registrar ubicación GPS al tomar fotos (con su consentimiento)")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(s.includeCoordinatesInReports, { on -> vm.update { it.copy(includeCoordinatesInReports = on) } }); Spacer(Modifier.width(8.dp)); Text("Incluir coordenadas en los pies de los informes")
                }
            }
            t?.let { tpl -> TemplateSection(vm, tpl) }
            SectionCard("Versiones", Icons.Default.Info) {
                KeyValue("Aplicación", "${BuildConfig.VERSION_NAME} (código ${BuildConfig.VERSION_CODE})")
                KeyValue("Paquete", BuildConfig.APPLICATION_ID)
                KeyValue("Base de datos", "v${AppDatabase.VERSION}")
                KeyValue("Motor de cálculo", "${ConoArenaLegacy.ENGINE_FULL_ID} (perfil heredado, wc = 0,02 no validado)")
                KeyValue("Plantilla PDF activa", t?.version ?: "—")
                KeyValue("Formato de respaldo", "v${BackupArchive.FORMAT}")
                KeyValue("Historial de plantillas", all.joinToString { it.version })
                Text("Funcionamiento 100 % local y sin cuenta. No hay sincronización en esta versión.", style = MaterialTheme.typography.bodySmall, color = Condor.Steel)
            }
        }
    }
}

@Composable
private fun TemplateSection(vm: SettingsViewModel, t: com.geodensidad.app.data.TemplateVersionEntity) {
    var inst by remember(t.id) { mutableStateOf(t.institution) }; var title by remember(t.id) { mutableStateOf(t.formTitle) }
    var code by remember(t.id) { mutableStateOf(t.docCode) }; var ver by remember(t.id) { mutableStateOf(t.docVersion) }; var date by remember(t.id) { mutableStateOf(t.docDate) }
    var logo by remember(t.id) { mutableStateOf<Uri?>(null) }; var remove by remember(t.id) { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { it?.let { u -> logo = u; remove = false } }
    SectionCard("Plantilla de informe (v${t.version})", Icons.Default.Description, initiallyExpanded = false) {
        Text("Logotipo actual (extraído del formato Excel de origen o cargado por el usuario):", style = MaterialTheme.typography.bodySmall)
        when {
            logo != null -> AsyncImage(logo, "Logotipo nuevo", contentScale = ContentScale.Fit, modifier = Modifier.height(64.dp))
            remove || t.logoPath == null -> Text("Sin logotipo: se usará un encabezado tipográfico neutro.", color = Condor.Steel)
            else -> AsyncImage(File(t.logoPath), "Logotipo", contentScale = ContentScale.Fit, modifier = Modifier.height(64.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text("Cambiar logotipo") }
            OutlinedButton(onClick = { remove = true; logo = null }) { Text("Quitar logotipo") }
        }
        TextInput("Institución", inst, { inst = it }); TextInput("Título del formato", title, { title = it }, singleLine = false)
        TextInput("Código documental", code, { code = it }); TextInput("Versión documental", ver, { ver = it }); TextInput("Fecha documental", date, { date = it })
        Text("El Excel de origen no traía código, versión ni fecha documental: complételos según su sistema de gestión.", style = MaterialTheme.typography.bodySmall, color = Condor.Warn)
        Button(onClick = { vm.publish(inst, title, code, ver, date, logo, remove) }, Modifier.fillMaxWidth()) { Text("Publicar nueva versión de plantilla") }
        Text("Publicar crea una versión nueva; los informes ya emitidos no cambian.", style = MaterialTheme.typography.bodySmall, color = Condor.Steel)
    }
}
