package com.geodensidad.app.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geodensidad.app.AppContainer
import com.geodensidad.app.data.CustomFieldEntity
import com.geodensidad.app.data.SampleEntity
import com.geodensidad.app.data.SampleRevisionEntity
import com.geodensidad.app.domain.*
import com.geodensidad.core.calc.ConoArenaLegacy
import com.geodensidad.core.calc.LegacyResult
import com.geodensidad.core.number.DecimalText
import com.geodensidad.core.number.ParsedDecimal
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

sealed interface SaveState { data object Idle : SaveState; data object Pending : SaveState; data class Saved(val at: Long) : SaveState; data class Failed(val msg: String) : SaveState }

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SampleViewModel(private val c: AppContainer, private val h: SavedStateHandle) : ViewModel() {
    val id: String = h["id"]!!
    private fun <T> Flow<T>.state(init: T) = stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), init)
    val sample = c.db.samples().observeOne(id).state(null)
    val revisions = c.db.revisions().observeBySample(id).state(emptyList())
    val photos = c.db.photos().observeBySample(id).state(emptyList())
    val reports = c.db.reports().observeBySample(id).state(emptyList())
    val materials = c.db.catalogs().materials().state(emptyList())
    val equipment = c.db.catalogs().equipment().state(emptyList())
    val calibrations = c.db.catalogs().calibrations().state(emptyList())
    val labRefs = c.db.catalogs().labRefs().state(emptyList())
    val customFields = c.db.customFields().observeActive("SAMPLE").state(emptyList())
    val journeys = sample.filterNotNull().map { it.activityId }.distinctUntilChanged().flatMapLatest { c.db.journeys().observeByActivity(it) }.state(emptyList())
    val lots = sample.filterNotNull().map { it.activityId }.distinctUntilChanged().flatMapLatest { c.db.lots().observeByActivity(it) }.state(emptyList())

    val form = MutableStateFlow<SampleForm?>(null)
    val texts = MutableStateFlow<Map<String, String>>(emptyMap())
    val result = form.filterNotNull().map { ConoArenaLegacy.calculate(it.toInputs()) }.state(null)
    val currentRevision = MutableStateFlow<SampleRevisionEntity?>(null)
    val saveState = MutableStateFlow<SaveState>(SaveState.Idle)
    val message = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)
    val progress = MutableStateFlow<ReportProgress?>(null)
    private var saveJob: Job? = null
    private var reportJob: Job? = null
    private var dirty = false

    var pendingCamera: String?
        get() = h["pendingCamera"]
        set(v) { h["pendingCamera"] = v }

    init { reload() }

    fun reload() = viewModelScope.launch {
        val b = c.repo.bundle(id)
        currentRevision.value = b.revision
        form.value = b.form
        texts.value = textsFrom(b.form)
        c.repo.touchActivity(b.sample.activityId)
    }

    private fun textsFrom(f: SampleForm) = mapOf(
        "a" to DecimalText.storedToEditText(f.a), "b" to DecimalText.storedToEditText(f.b), "d" to DecimalText.storedToEditText(f.d),
        "f" to DecimalText.storedToEditText(f.f), "h" to DecimalText.storedToEditText(f.h), "i" to DecimalText.storedToEditText(f.i),
        "j" to DecimalText.storedToEditText(f.j), "l" to DecimalText.fractionToPercentText(f.l), "q" to DecimalText.storedToEditText(f.q),
        "r" to DecimalText.fractionToPercentText(f.r), "thickness" to DecimalText.storedToEditText(f.thickness),
    ) + f.custom.mapKeys { "c:${it.key}" }

    val editable get() = currentRevision.value?.locked == false && sample.value?.status != SampleStatus.VOID.name

    fun update(transform: (SampleForm) -> SampleForm) {
        val f = form.value ?: return
        if (!editable) { message.value = "La revisión vigente está bloqueada. Cree una nueva revisión para modificarla."; return }
        form.value = transform(f)
        dirty = true
        saveState.value = SaveState.Pending
        saveJob?.cancel()
        saveJob = viewModelScope.launch { delay(600); persist() }
    }

    /** Entrada numérica: inválido o vacío → el valor queda FALTANTE (null) y el resultado deja de estar vigente. */
    fun setNumber(key: String, raw: String) {
        texts.value = texts.value + (key to raw)
        val parsed = if (key == "l" || key == "r") DecimalText.percentTextToFraction(raw) else DecimalText.parse(raw)
        val canon = (parsed as? ParsedDecimal.Valid)?.let { DecimalText.canonical(it.value) }
        update { f ->
            when (key) {
                "a" -> f.copy(a = canon); "b" -> f.copy(b = canon); "d" -> f.copy(d = canon); "f" -> f.copy(f = canon)
                "h" -> f.copy(h = canon); "i" -> f.copy(i = canon); "j" -> f.copy(j = canon); "l" -> f.copy(l = canon)
                "q" -> f.copy(q = canon); "r" -> f.copy(r = canon); "thickness" -> f.copy(thickness = canon)
                else -> f
            }
        }
    }

    fun setCustom(def: CustomFieldEntity, raw: String) {
        texts.value = texts.value + ("c:${def.key}" to raw)
        update { it.copy(custom = if (raw.isBlank()) it.custom - def.key else it.custom + (def.key to raw)) }
    }

    private suspend fun persist() {
        val f = form.value ?: return
        runCatching { c.repo.saveForm(id, f) }
            .onSuccess { dirty = false; saveState.value = SaveState.Saved(Clock.nowMillis()) }
            .onFailure { saveState.value = SaveState.Failed(it.message ?: "Error al guardar") }
    }

    suspend fun flush() { saveJob?.cancel(); if (dirty) persist() }

    fun applyCalibration(calId: String?) = viewModelScope.launch {
        val f = form.value ?: return@launch
        val n = if (calId == null) f.copy(calibrationId = null) else c.repo.applyCalibration(f, calId)
        texts.value = texts.value + ("d" to DecimalText.storedToEditText(n.d)) + ("f" to DecimalText.storedToEditText(n.f))
        update { n }
    }

    fun applyLabRef(labId: String?) = viewModelScope.launch {
        val f = form.value ?: return@launch
        val n = if (labId == null) f.copy(labRefId = null) else c.repo.applyLabRef(f, labId)
        texts.value = texts.value + ("q" to DecimalText.storedToEditText(n.q)) + ("r" to DecimalText.fractionToPercentText(n.r))
        update { n }
    }

    private fun action(block: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch { flush(); runCatching { block() }.onFailure { message.value = it.message }; busy.value = false }
    }

    fun complete() = action { c.repo.markComplete(id); reload(); message.value = "Muestra marcada como completa" }
    fun review(name: String) = action { c.repo.markReviewed(id, name); reload() }
    fun approve(name: String) = action { c.repo.approve(id, name); reload(); message.value = "Revisión aprobada y bloqueada" }
    fun toDraft() = action { c.repo.returnToDraft(id); reload() }
    fun newRevision(reason: String) = action { val n = c.repo.createRevision(id, reason); reload(); message.value = "Revisión $n creada; la anterior se conserva" }
    fun void(reason: String) = action { c.repo.voidSample(id, reason); reload() }
    fun duplicate(fields: Set<DuplicableField>, onDone: (String) -> Unit) = action { onDone(c.repo.duplicate(id, fields)) }
    fun assignLot(lotId: String?) = action { c.repo.assignLot(id, lotId) }
    fun moveJourney(journeyId: String) = action { c.repo.moveToJourney(id, journeyId) }

    fun generatePdf(onDone: (String) -> Unit) {
        if (reportJob?.isActive == true) return
        reportJob = viewModelScope.launch {
            flush()
            progress.value = ReportProgress("Iniciando", 0f)
            runCatching { c.reports.sampleReport(id) { m, f -> progress.value = ReportProgress(m, f) } }
                .onSuccess { onDone(it.id) }.onFailure { message.value = if (it is kotlinx.coroutines.CancellationException) "Generación cancelada" else "No se generó el PDF: ${it.message}" }
            progress.value = null
        }
    }
    fun cancelPdf() { reportJob?.cancel() }

    fun newCameraFile(): File = c.evidence.newCameraFile().also { pendingCamera = it.path }

    fun onCameraResult(success: Boolean, context: Context) {
        val path = pendingCamera ?: return
        pendingCamera = null
        val f = File(path)
        if (!success || !f.exists() || f.length() == 0L) { f.delete(); return }
        val s = sample.value ?: return
        action {
            val loc = lastLocation(context)
            val r = c.evidence.importFromCamera(f, s.activityId, id, null, loc)
            if (r.errors.isNotEmpty()) message.value = r.errors.joinToString("\n")
        }
    }

    fun importUris(uris: List<Uri>) {
        val s = sample.value ?: return
        if (uris.isEmpty()) return
        action {
            val r = c.evidence.importFromUris(uris, s.activityId, id, null)
            message.value = "${r.imported.size} fotografía(s) incorporada(s)" + if (r.errors.isNotEmpty()) "\n" + r.errors.joinToString("\n") else ""
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun lastLocation(context: Context): Pair<Double, Double>? {
        if (!c.settings.current().gpsEnabled) return null
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }?.let { it.latitude to it.longitude }
    }

    suspend fun reviewerDefault() = c.settings.current().responsible

    override fun onCleared() {
        // Guardado final fuera del ciclo de vida de la pantalla para no perder el borrador.
        val f = form.value
        if (dirty && f != null) c.appScope.launch { runCatching { c.repo.saveForm(id, f) } }
        super.onCleared()
    }
}
