package com.geodensidad.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.geodensidad.app.AppContainer
import com.geodensidad.app.MainActivity
import com.geodensidad.app.backup.BackupService
import com.geodensidad.app.ui.appViewModel
import com.geodensidad.app.ui.components.*
import com.geodensidad.app.ui.theme.Condor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BackupViewModel(private val c: AppContainer) : ViewModel() {
    val busy = MutableStateFlow<String?>(null)
    val message = MutableStateFlow<String?>(null)
    val plan = MutableStateFlow<BackupService.RestorePlan?>(null)
    val done = MutableStateFlow(false)
    fun autoBackups(): List<File> = File(c.context.filesDir, "backups/auto").listFiles()?.sortedByDescending { it.lastModified() }.orEmpty()

    fun export(uri: Uri) {
        if (busy.value != null) return
        busy.value = "Preparando respaldo"
        viewModelScope.launch {
            runCatching { c.backups.exportToUri(uri) { busy.value = it } }
                .onSuccess { m -> message.value = "Respaldo creado: ${m.files.size} archivos, ${m.counts["muestras"]} muestras, ${m.counts["fotografias"]} fotografías. NO cifrado." }
                .onFailure { message.value = "No se pudo crear el respaldo: ${it.message}" }
            busy.value = null
        }
    }

    fun prepare(uri: Uri) {
        if (busy.value != null) return
        busy.value = "Verificando respaldo (manifiesto, integridad, versión)"
        viewModelScope.launch {
            runCatching { c.backups.prepareRestore(uri) }.onSuccess { plan.value = it }.onFailure { message.value = "Respaldo rechazado: ${it.message}" }
            busy.value = null
        }
    }

    fun apply() {
        val p = plan.value ?: return
        plan.value = null
        busy.value = "Creando respaldo previo y restaurando"
        viewModelScope.launch {
            runCatching { c.backups.applyRestore(p) }.onSuccess { done.value = true }.onFailure { message.value = it.message }
            busy.value = null
        }
    }

    fun restart(ctx: Context) {
        ctx.startActivity(Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        Runtime.getRuntime().exit(0)
    }
}

@Composable
fun BackupScreen(back: () -> Unit) {
    val vm = appViewModel { c, _ -> BackupViewModel(c) }
    val busy by vm.busy.collectAsStateWithLifecycle()
    val plan by vm.plan.collectAsStateWithLifecycle()
    val done by vm.done.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val snack = remember { SnackbarHostState() }
    ErrorSnack(vm.message.collectAsStateWithLifecycle().value, { vm.message.value = null }, snack)
    val name = "GeoDensidad-respaldo-${SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())}.zip"
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { it?.let(vm::export) }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::prepare) }
    Scaffold(topBar = { AppBar("Respaldos", "Copia completa y restauración", back) }, snackbarHost = { SnackbarHost(snack) }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
            Banner("El respaldo NO está cifrado: contiene datos, fotografías e informes legibles por quien tenga el archivo. Guárdelo en un lugar controlado.", Condor.Bad, Icons.Default.LockOpen)
            busy?.let { Column(Modifier.padding(16.dp)) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(it, Modifier.padding(top = 6.dp)) } }
            SectionCard("Crear respaldo completo", Icons.Default.Backup) {
                Text("Incluye base de datos (con revisiones, historial, campos, criterios e instantáneas), fotografías originales y derivadas, informes emitidos, recursos de plantilla, configuración y un manifiesto con versiones y hashes SHA-256.")
                BusyButton("Guardar respaldo…", busy != null, { create.launch(name) }, Modifier.fillMaxWidth(), Icons.Default.SaveAlt)
            }
            SectionCard("Restaurar desde archivo", Icons.Default.Restore) {
                Text("Se verifica el manifiesto, la versión, la integridad de cada archivo y las referencias en un área temporal. Antes de reemplazar se crea un respaldo automático de los datos actuales. La restauración REEMPLAZA el conjunto local (no fusiona).")
                OutlinedButton(onClick = { open.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }, enabled = busy == null, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.FolderOpen, null); Text(" Seleccionar respaldo…") }
            }
            SectionCard("Respaldos automáticos previos a restaurar", Icons.Default.History, initiallyExpanded = false) {
                val autos = remember(done, busy) { vm.autoBackups() }
                if (autos.isEmpty()) Text("Ninguno.")
                autos.forEach { KeyValue(it.name, "${it.length() / 1024} KB") }
                Text("Se guardan dentro de la app (files/backups/auto). Para conservarlos fuera del dispositivo, cree un respaldo completo.", style = MaterialTheme.typography.bodySmall, color = Condor.Steel)
            }
            SectionCard("Qué protege cada situación", Icons.Default.Info, initiallyExpanded = false) {
                KeyValue("Actualizar la app", "Conserva los datos (mismo paquete y firma).")
                KeyValue("Desinstalar", "BORRA los datos locales. Solo se recuperan con un respaldo.")
                KeyValue("Borrar datos de la app", "BORRA todo. Solo se recupera con un respaldo.")
                KeyValue("Pérdida del dispositivo", "Solo se recupera lo que esté en un respaldo externo.")
                KeyValue("Restaurar", "Reemplaza los datos actuales por los del respaldo (con copia previa).")
            }
        }
    }
    plan?.let { p ->
        AlertDialog(onDismissRequest = { vm.plan.value = null }, title = { Text("Confirmar restauración") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                p.summary.forEach { (k, v) -> KeyValue(k, v) }
                p.warnings.forEach { Text("⚠ $it", color = Condor.Warn) }
                Text("\nLos datos actuales serán reemplazados. Se creará antes un respaldo automático. La aplicación se reiniciará.", color = Condor.Bad)
            }
        }, confirmButton = { TextButton(onClick = vm::apply) { Text("Restaurar", color = Condor.Bad) } }, dismissButton = { TextButton(onClick = { vm.plan.value = null }) { Text("Cancelar") } })
    }
    if (done) AlertDialog(onDismissRequest = {}, title = { Text("Restauración completada") }, text = { Text("La aplicación se reiniciará para abrir los datos restaurados.") },
        confirmButton = { TextButton(onClick = { vm.restart(ctx) }) { Text("Reiniciar") } })
}
