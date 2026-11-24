package com.geodensidad.app.ui.screens

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geodensidad.app.AppContainer
import com.geodensidad.app.data.*
import com.geodensidad.app.domain.SampleStatus
import com.geodensidad.app.report.GlobalFilter
import com.geodensidad.core.criteria.LotEvaluation
import com.geodensidad.core.report.ChartSpec
import com.geodensidad.core.report.GlobalAssembler
import com.geodensidad.core.report.SampleRecord
import com.geodensidad.core.report.StatGroup
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class Dashboard(
    val records: List<SampleRecord> = emptyList(),
    val groups: List<StatGroup> = emptyList(),
    val charts: List<ChartSpec> = emptyList(),
    val criterionLabel: String = "",
)

data class ReportProgress(val message: String, val fraction: Float)

@OptIn(kotlinx.coroutines.FlowPreview::class)
class ActivityViewModel(private val c: AppContainer, h: SavedStateHandle) : ViewModel() {
    val id: String = h["id"]!!
    private fun <T> Flow<T>.state(init: T) = stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), init)
    val activity = c.db.activities().observeOne(id).state(null)
    val samples = c.db.samples().observeByActivity(id).state(emptyList())
    val journeys = c.db.journeys().observeByActivity(id).state(emptyList())
    val lots = c.db.lots().observeByActivity(id).state(emptyList())
    val reports = c.db.reports().observeByActivity(id).state(emptyList())
    val materials = c.db.catalogs().materials().state(emptyList())
    val calibrations = c.db.catalogs().calibrations().state(emptyList())
    val labRefs = c.db.catalogs().labRefs().state(emptyList())
    val criteria = c.db.catalogs().criteria().state(emptyList())
    val lotEvaluations = MutableStateFlow<Map<String, LotEvaluation>>(emptyMap())
    val dashboard = MutableStateFlow(Dashboard())
    val busy = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)
    val progress = MutableStateFlow<ReportProgress?>(null)
    private var reportJob: Job? = null

    init {
        viewModelScope.launch { c.repo.touchActivity(id) }
        viewModelScope.launch {
            samples.combine(lots) { s, l -> s to l }.debounce(250).collect { refreshDerived() }
        }
    }

    private suspend fun refreshDerived() {
        val recs = c.reports.records(GlobalFilter(id)).map { it.second }
        val crit = c.repo.criterion(activity.value?.defaultCriterionId ?: c.db.activities().get(id)?.defaultCriterionId)
        dashboard.value = Dashboard(recs, GlobalAssembler.groups(recs), GlobalAssembler.charts(recs, crit),
            crit?.let { "${it.code} v${it.version}${if (!it.validated) " (no validado)" else ""}" } ?: "Sin criterio seleccionado")
        lotEvaluations.value = c.db.lots().listByActivity(id).associate { it.id to c.repo.evaluateLot(it.id) }
    }

    private fun guarded(block: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch { runCatching { block() }.onFailure { message.value = it.message ?: "Error" }; busy.value = false }
    }

    fun newSample(journeyId: String, onDone: (String) -> Unit) = guarded { onDone(c.repo.createSample(id, journeyId)) }
    fun saveJourney(j: JourneyEntity?, date: String, resp: String, front: String, weather: String, obs: String, onDone: (String) -> Unit = {}) = guarded {
        if (j == null) onDone(c.repo.createJourney(id, date, resp, front, obs, weather))
        else c.repo.updateJourney(j.copy(workDate = date, responsible = resp, workFront = front, weather = weather, observations = obs))
    }
    fun closeJourney(j: JourneyEntity) = guarded { c.repo.updateJourney(j.copy(status = if (j.status == "OPEN") "CLOSED" else "OPEN")) }
    fun newLot(code: String, desc: String, onDone: (String) -> Unit) = guarded { onDone(c.repo.createLot(id, code, desc, activity.value?.defaultCriterionId)) }
    fun setStatus(status: String) = guarded { activity.value?.let { c.repo.updateActivity(it.copy(status = status)) } }
    fun saveDefaults(a: ActivityEntity) = guarded { c.repo.updateActivity(a) }

    fun generateGlobal(filter: GlobalFilter, onDone: (String) -> Unit) {
        if (reportJob?.isActive == true) return
        reportJob = viewModelScope.launch {
            progress.value = ReportProgress("Iniciando", 0f)
            runCatching { c.reports.globalReport(filter) { m, f -> progress.value = ReportProgress(m, f) } }
                .onSuccess { onDone(it.id) }
                .onFailure { if (it !is kotlinx.coroutines.CancellationException) message.value = "No se generó el informe: ${it.message}" else message.value = "Generación cancelada" }
            progress.value = null
        }
    }
    fun cancelReport() { reportJob?.cancel() }

    val statusCounts get() = samples.value.groupingBy { SampleStatus.of(it.status) }.eachCount()
}
