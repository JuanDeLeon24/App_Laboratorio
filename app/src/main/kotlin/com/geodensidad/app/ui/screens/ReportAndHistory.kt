package com.geodensidad.app.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.geodensidad.app.AppContainer
import com.geodensidad.app.data.ReportEntity
import com.geodensidad.app.domain.Clock
import com.geodensidad.app.ui.appViewModel
import com.geodensidad.app.ui.components.*
import com.geodensidad.app.ui.theme.Condor
import com.geodensidad.core.backup.BackupArchive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ReportViewModel(private val c: AppContainer, h: SavedStateHandle) : ViewModel() {
    val id: String = h["id"]!!
    val report = MutableStateFlow<ReportEntity?>(null)
    val pages = MutableStateFlow<List<Bitmap>>(emptyList())
    val integrity = MutableStateFlow<Boolean?>(null)
    val error = MutableStateFlow<String?>(null)
    init {
        viewModelScope.launch {
            val r = c.db.reports().get(id) ?: run { error.value = "Informe inexistente"; return@launch }
            report.value = r
            withContext(Dispatchers.IO) {
                val f = File(r.filePath)
                if (!f.exists()) { error.value = "El archivo del informe no se encuentra"; return@withContext }
                integrity.value = BackupArchive.sha256(f) == r.sha256
                runCatching {
                    ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                        PdfRenderer(pfd).use { pr ->
                            val out = mutableListOf<Bitmap>()
                            for (i in 0 until minOf(pr.pageCount, 40)) pr.openPage(i).use { p ->
                                val bmp = Bitmap.createBitmap(p.width * 2, p.height * 2, Bitmap.Config.ARGB_8888)
                                bmp.eraseColor(android.graphics.Color.WHITE)
                                p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                out += bmp
                                pages.value = out.toList()
                            }
                        }
                    }
                }.onFailure { error.value = "No se pudo mostrar la vista previa: ${it.message}" }
            }
        }
    }
    override fun onCleared() { pages.value.forEach { it.recycle() }; super.onCleared() }
}

@Composable
fun ReportPreviewScreen(back: () -> Unit) {
    val vm = appViewModel { c, h -> ReportViewModel(c, h) }
    val r by vm.report.collectAsStateWithLifecycle()
    val pages by vm.pages.collectAsStateWithLifecycle()
    val ok by vm.integrity.collectAsStateWithLifecycle()
    val err by vm.error.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    Scaffold(topBar = { AppBar(r?.code ?: "Informe", r?.let { "Emitido ${it.issuedAtText}" }, back) {
        IconButton(onClick = {
            val rep = r ?: return@IconButton
            val share = File(ctx.cacheDir, "share").apply { mkdirs() }.let { File(it, "${rep.code}.pdf") }
            File(rep.filePath).copyTo(share, overwrite = true)
            val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", share)
            ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Compartir informe"))
        }) { Icon(Icons.Default.Share, "Compartir") }
    } }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize().background(Color(0xFFE6ECF2)), contentPadding = PaddingValues(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            r?.let { rep -> item {
                Card { Column(Modifier.padding(12.dp)) {
                    KeyValue("Tipo", if (rep.type == "GLOBAL") "Informe global" else "Informe individual"); KeyValue("Alcance", rep.scopeText)
                    KeyValue("Emitido por", rep.issuedBy ?: "—"); KeyValue("Plantilla / motor", "v${rep.templateVersion} / ${rep.engineVersion}")
                    KeyValue("Páginas", rep.pageCount.toString()); KeyValue("SHA-256", rep.sha256)
                    KeyValue("Integridad del archivo", when (ok) { true -> "Coincide con el hash de emisión"; false -> "NO coincide"; null -> "Verificando…" }, valueColor = if (ok == false) Condor.Bad else Condor.Ok)
                    Text("Esta es la emisión archivada: reabrirla no la regenera con datos actuales. Para reflejar cambios, emita un nuevo informe.", style = MaterialTheme.typography.bodySmall, color = Condor.Steel)
                } }
            } }
            err?.let { item { Banner(it, Condor.Bad) } }
            if (pages.isEmpty() && err == null) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            items(pages) { b -> Image(b.asImageBitmap(), "Página", Modifier.fillMaxWidth().aspectRatio(b.width.toFloat() / b.height)) }
        }
    }
}

class HistoryViewModel(c: AppContainer, h: SavedStateHandle) : ViewModel() {
    val id: String = h["id"]!!
    val events = (if (id == "all") c.db.audit().observeRecent(500) else c.db.audit().observeFor(id)).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

@Composable
fun HistoryScreen(back: () -> Unit) {
    val vm = appViewModel { c, h -> HistoryViewModel(c, h) }
    val ev by vm.events.collectAsStateWithLifecycle()
    Scaffold(topBar = { AppBar("Historial", if (vm.id == "all") "Últimos 500 eventos" else "${ev.size} eventos", back) }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize()) {
            item { Banner("Historial local: la atribución por nombre no es autenticación, y el registro no es inalterable frente a acceso privilegiado al dispositivo.", Condor.Steel) }
            if (ev.isEmpty()) item { EmptyState(Icons.Default.History, "Sin eventos", "") }
            items(ev, key = { it.id }) { e ->
                ListItem(headlineContent = { Text(e.action) }, supportingContent = { Text("${Clock.display(e.at)} · ${e.actor}\n${e.detail}") }, overlineContent = { Text(e.entityType) })
                HorizontalDivider()
            }
        }
    }
}
