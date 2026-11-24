package com.geodensidad.app.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.geodensidad.app.AppContainer
import com.geodensidad.app.data.PhotoEntity
import com.geodensidad.app.domain.AppJson
import com.geodensidad.app.domain.Clock
import com.geodensidad.app.ui.Routes
import com.geodensidad.app.ui.appViewModel
import com.geodensidad.app.ui.components.*
import com.geodensidad.app.ui.theme.Condor
import com.geodensidad.core.photo.CaptureTime
import com.geodensidad.core.photo.CaptureTimeSource
import com.geodensidad.core.photo.PhotoCaption
import com.geodensidad.core.photo.PhotoTimes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class PhotoViewModel(private val c: AppContainer, h: SavedStateHandle) : ViewModel() {
    val id: String = h["id"]!!
    val photo = c.db.photos().observeOne(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val message = MutableStateFlow<String?>(null)
    val inherited = MutableStateFlow(PhotoCaption.Fields())
    val sampleCode = MutableStateFlow<String?>(null)
    val integrity = MutableStateFlow<Boolean?>(null)

    init {
        viewModelScope.launch {
            val p = c.db.photos().get(id) ?: return@launch
            p.sampleId?.let { sid ->
                val b = c.repo.bundle(sid)
                sampleCode.value = b.sample.code
                inherited.value = PhotoCaption.Fields(abscisa = b.form.abscisa.ifBlank { null }, side = b.form.side.ifBlank { null }, layer = b.form.layer.ifBlank { null })
            }
            integrity.value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { c.evidence.verify(p) }
        }
    }
    private fun run(block: suspend () -> Unit) = viewModelScope.launch { runCatching { block() }.onFailure { message.value = it.message } }
    fun saveCaption(f: PhotoCaption.Fields, include: Boolean) = run { c.evidence.updateCaption(id, f, include); message.value = "Pie guardado" }
    fun rotate(d: Int) = run { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { c.evidence.rotate(id, d) } }
    fun setSource(src: CaptureTimeSource, local: String? = null, offset: String? = null, reason: String = "") = run { c.evidence.setManualTime(id, local, offset, reason, src) }
    fun move(up: Boolean) = run { c.evidence.move(id, up) }
    fun archive(reason: String, done: () -> Unit) = run { c.evidence.archive(id, reason); done() }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PhotoScreen(back: () -> Unit) {
    val vm = appViewModel { c, h -> PhotoViewModel(c, h) }
    val p by vm.photo.collectAsStateWithLifecycle()
    val inherited by vm.inherited.collectAsStateWithLifecycle()
    val code by vm.sampleCode.collectAsStateWithLifecycle()
    val ok by vm.integrity.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    ErrorSnack(vm.message.collectAsStateWithLifecycle().value, { vm.message.value = null }, snack)
    var manual by remember { mutableStateOf(false) }
    var archive by remember { mutableStateOf(false) }
    Scaffold(topBar = { AppBar("Fotografía", code?.let { "Muestra $it" }, back) {
        IconButton(onClick = { vm.move(true) }) { Icon(Icons.Default.ArrowUpward, "Subir en el orden") }
        IconButton(onClick = { vm.move(false) }) { Icon(Icons.Default.ArrowDownward, "Bajar en el orden") }
        IconButton(onClick = { archive = true }) { Icon(Icons.Default.Archive, "Archivar") }
    } }, snackbarHost = { SnackbarHost(snack) }) { pad ->
        val photo = p ?: return@Scaffold
        val times = AppJson.decodeFromString(PhotoTimes.serializer(), photo.timesJson)
        val own0 = runCatching { AppJson.decodeFromString(PhotoCaption.Fields.serializer(), photo.captionJson) }.getOrDefault(PhotoCaption.Fields())
        var title by remember(photo.id) { mutableStateOf(own0.title ?: "") }
        var desc by remember(photo.id) { mutableStateOf(own0.description ?: "") }
        var obs by remember(photo.id) { mutableStateOf(own0.observation ?: "") }
        var abs by remember(photo.id) { mutableStateOf(own0.abscisa ?: "") }
        var side by remember(photo.id) { mutableStateOf(own0.side ?: "") }
        var layer by remember(photo.id) { mutableStateOf(own0.layer ?: "") }
        var resp by remember(photo.id) { mutableStateOf(own0.responsible ?: "") }
        var tags by remember(photo.id) { mutableStateOf(own0.tags.joinToString(", ")) }
        var extraK by remember(photo.id) { mutableStateOf(own0.extra.firstOrNull()?.first ?: "") }
        var extraV by remember(photo.id) { mutableStateOf(own0.extra.firstOrNull()?.second ?: "") }
        var include by remember(photo.id) { mutableStateOf(photo.includeInReport) }
        val fields = PhotoCaption.Fields(title.ifBlank { null }, desc.ifBlank { null }, obs.ifBlank { null }, abs.ifBlank { null }, side.ifBlank { null }, layer.ifBlank { null }, resp.ifBlank { null },
            tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }, if (extraK.isNotBlank()) listOf(extraK to extraV) else emptyList())
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AsyncImage(ImageRequest.Builder(LocalContext.current).data(File(photo.pdfPath)).memoryCacheKey("${photo.id}-${photo.updatedAt}").diskCacheKey("${photo.id}-${photo.updatedAt}").build(),
                "Fotografía", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.rotate(-90) }) { Icon(Icons.Default.RotateLeft, null); Text(" Rotar") }
                OutlinedButton(onClick = { vm.rotate(90) }) { Icon(Icons.Default.RotateRight, null); Text(" Rotar") }
            }
            Text("La rotación se aplica a la miniatura y a la versión para PDF; el original no se modifica.", style = MaterialTheme.typography.bodySmall, color = Condor.Steel)
            Row(verticalAlignment = Alignment.CenterVertically) { Switch(include, { include = it }); Spacer(Modifier.width(8.dp)); Text("Incluir en informes PDF") }
            SectionCard("Fecha y hora de captura", Icons.Default.Schedule) {
                val d = CaptureTime.display(times)
                KeyValue("Mostrada en el pie", d.text + (d.note?.let { " ($it)" } ?: ""))
                KeyValue("Hora del dispositivo (app)", times.appCaptureAt?.let { it.replace('T', ' ') } ?: "—")
                KeyValue("EXIF original", times.exifLocal?.let { it.replace('T', ' ') + (times.exifOffset?.let { o -> " $o" } ?: " (sin zona horaria)") } ?: "No disponible")
                KeyValue("Importación", times.importedAt.replace('T', ' '))
                KeyValue("Declarada manualmente", times.manualLocal?.let { "${it.replace('T', ' ')} ${times.manualOffset ?: "(sin zona)"} · motivo: ${times.manualReason}" } ?: "—")
                Text("La hora del dispositivo no es una hora verificada externamente. La fecha de importación nunca sustituye a la de captura.", style = MaterialTheme.typography.bodySmall, color = Condor.Steel)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (times.appCaptureAt != null) FilterChip(times.selected == CaptureTimeSource.APP_DEVICE, { vm.setSource(CaptureTimeSource.APP_DEVICE) }, { Text("Dispositivo") })
                    if (times.exifLocal != null) FilterChip(times.selected == CaptureTimeSource.EXIF, { vm.setSource(CaptureTimeSource.EXIF) }, { Text("EXIF") })
                    FilterChip(times.selected == CaptureTimeSource.MANUAL, { manual = true }, { Text("Manual…") })
                    FilterChip(times.selected == CaptureTimeSource.UNKNOWN, { vm.setSource(CaptureTimeSource.UNKNOWN) }, { Text("No disponible") })
                }
            }
            SectionCard("Pie de fotografía", Icons.Default.Subtitles) {
                Text("Datos propuestos desde la muestra (se usan si la foto no tiene valor propio): ${listOfNotNull(inherited.abscisa, inherited.side?.let { "costado $it" }, inherited.layer?.let { "capa $it" }).joinToString(" · ").ifBlank { "ninguno" }}",
                    style = MaterialTheme.typography.bodySmall, color = Condor.Steel)
                TextInput("Título", title, { title = it }); TextInput("Descripción", desc, { desc = it }, singleLine = false)
                TextInput("Observación", obs, { obs = it }, singleLine = false)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(abs, { abs = it }, label = { Text("Abscisa propia") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(side, { side = it }, label = { Text("Costado") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(layer, { layer = it }, label = { Text("Capa") }, singleLine = true, modifier = Modifier.weight(0.7f))
                }
                TextInput("Responsable", resp, { resp = it }); TextInput("Etiquetas (separadas por coma)", tags, { tags = it })
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(extraK, { extraK = it }, label = { Text("Campo adicional") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(extraV, { extraV = it }, label = { Text("Valor") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Text("Vista previa del pie", style = MaterialTheme.typography.labelLarge)
                PhotoCaption.lines(photo.sortOrder, times, code, fields, inherited, null).forEachIndexed { i, l -> Text(l, style = if (i == 0) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodySmall) }
                Button(onClick = { vm.saveCaption(fields, include) }, Modifier.fillMaxWidth()) { Icon(Icons.Default.Save, null); Text(" Guardar pie e inclusión") }
            }
            SectionCard("Integridad y metadatos", Icons.Default.Fingerprint, initiallyExpanded = false) {
                KeyValue("Origen", if (photo.origin == "CAMERA") "Cámara (desde la app)" else "Importada de la galería")
                KeyValue("Dimensiones", "${photo.width} × ${photo.height} px"); KeyValue("Tamaño", "${photo.sizeBytes / 1024} KB"); KeyValue("Tipo", photo.mime)
                KeyValue("SHA-256", photo.sha256); KeyValue("Integridad del original", when (ok) { true -> "Coincide con el hash registrado"; false -> "NO coincide o falta el archivo"; null -> "Verificando…" })
                KeyValue("Ubicación GPS", if (photo.latitude != null) "%.6f, %.6f".format(photo.latitude, photo.longitude) else "No registrada")
                Text("El hash permite comprobar integridad respecto de esta referencia; no acredita por sí solo autenticidad ni el momento real de captura.", style = MaterialTheme.typography.bodySmall, color = Condor.Steel)
            }
        }
    }
    if (manual) ManualDateDialog({ manual = false }) { l, o, r -> vm.setSource(CaptureTimeSource.MANUAL, l, o, r); manual = false }
    if (archive) ReasonDialog("Archivar fotografía", "Dejará de mostrarse y se retirará de los informes. El original se conserva y se incluye en los respaldos.", confirm = "Archivar", onConfirm = { vm.archive(it, back) }, onDismiss = { archive = false })
}

@Composable
private fun ManualDateDialog(onDismiss: () -> Unit, onSave: (String, String?, String) -> Unit) {
    var date by remember { mutableStateOf("") }
    var time by remember { mutableStateOf("") }
    var off by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    val ldt = runCatching { java.time.LocalDateTime.parse("${date}T${time.padEnd(8, ':').let { if (time.length == 5) "$time:00" else time }}") }.getOrNull()
    val offOk = off.isBlank() || runCatching { java.time.ZoneOffset.of(off) }.isSuccess
    FormDialog("Fecha declarada manualmente", onDismiss, { onSave(ldt.toString(), off.ifBlank { null }, reason) }, ldt != null && offOk && reason.isNotBlank()) {
        Text("Se conservan los valores originales; el pie indicará «fecha declarada manualmente».", style = MaterialTheme.typography.bodySmall)
        TextInput("Fecha (AAAA-MM-DD)", date, { date = it }); TextInput("Hora (HH:MM o HH:MM:SS)", time, { time = it })
        TextInput("Desfase UTC (opcional, ej. -05:00)", off, { off = it }, supporting = if (offOk) "Déjelo vacío si no se conoce: no se inventará zona horaria" else "Desfase no válido")
        TextInput("Motivo *", reason, { reason = it }, singleLine = false)
    }
}

class JourneyPhotosViewModel(private val c: AppContainer, h: SavedStateHandle) : ViewModel() {
    val activityId: String = h["activityId"]!!
    val journeyId: String = h["journeyId"]!!
    val photos = c.db.photos().observeByJourney(journeyId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val message = MutableStateFlow<String?>(null)
    var pending: String? = h["pending"]; private set
    private val handle = h
    fun newFile(): File = c.evidence.newCameraFile().also { pending = it.path; handle["pending"] = it.path }
    fun camera(ok: Boolean) = viewModelScope.launch {
        val f = pending?.let(::File) ?: return@launch
        pending = null; handle["pending"] = null
        if (!ok || !f.exists() || f.length() == 0L) { f.delete(); return@launch }
        c.evidence.importFromCamera(f, activityId, null, journeyId, null).errors.firstOrNull()?.let { message.value = it }
    }
    fun gallery(uris: List<Uri>) = viewModelScope.launch {
        val r = c.evidence.importFromUris(uris, activityId, null, journeyId)
        if (r.errors.isNotEmpty()) message.value = r.errors.joinToString("\n")
    }
}

@Composable
fun JourneyPhotosScreen(nav: NavController, back: () -> Unit) {
    val vm = appViewModel { c, h -> JourneyPhotosViewModel(c, h) }
    val photos by vm.photos.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val snack = remember { SnackbarHostState() }
    ErrorSnack(vm.message.collectAsStateWithLifecycle().value, { vm.message.value = null }, snack)
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { vm.camera(it) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { vm.gallery(it) }
    Scaffold(topBar = { AppBar("Evidencias de la jornada", "${photos.size} fotografías", back) {
        IconButton(onClick = { camera.launch(FileProvider.getUriForFile(ctx, ctx.packageName + ".files", vm.newFile())) }) { Icon(Icons.Default.PhotoCamera, "Cámara") }
        IconButton(onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Icon(Icons.Default.PhotoLibrary, "Galería") }
    } }, snackbarHost = { SnackbarHost(snack) }) { pad ->
        if (photos.isEmpty()) Box(Modifier.padding(pad)) { EmptyState(Icons.Default.PhotoLibrary, "Sin fotografías", "Fotografías generales de la jornada (no asociadas a una muestra).") }
        LazyVerticalGrid(GridCells.Adaptive(110.dp), Modifier.padding(pad), contentPadding = PaddingValues(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(photos, key = { it.id }) { p -> AsyncImage(File(p.thumbPath), "Fotografía", contentScale = ContentScale.Crop, modifier = Modifier.aspectRatio(1f).clickable { nav.navigate(Routes.photo(p.id)) }) }
        }
    }
}

