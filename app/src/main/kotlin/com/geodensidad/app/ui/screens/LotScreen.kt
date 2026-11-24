package com.geodensidad.app.ui.screens

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
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.geodensidad.app.AppContainer
import com.geodensidad.app.data.LotEntity
import com.geodensidad.app.data.SampleEntity
import com.geodensidad.app.domain.SampleStatus
import com.geodensidad.app.ui.Routes
import com.geodensidad.app.ui.appViewModel
import com.geodensidad.app.ui.components.*
import com.geodensidad.app.ui.theme.Condor
import com.geodensidad.core.criteria.LotEvaluation
import com.geodensidad.core.number.Fmt
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class LotViewModel(private val c: AppContainer, h: SavedStateHandle) : ViewModel() {
    val id: String = h["id"]!!
    private fun <T> Flow<T>.state(init: T) = stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), init)
    val lot = c.db.lots().observeOne(id).state(null)
    val members = c.db.samples().observeByLot(id).state(emptyList())
    val activitySamples = lot.filterNotNull().map { it.activityId }.distinctUntilChanged().flatMapLatest { c.db.samples().observeByActivity(it) }.state(emptyList())
    val allLots = lot.filterNotNull().map { it.activityId }.distinctUntilChanged().flatMapLatest { c.db.lots().observeByActivity(it) }.state(emptyList())
    val materials = c.db.catalogs().materials().state(emptyList())
    val labRefs = c.db.catalogs().labRefs().state(emptyList())
    val criteria = c.db.catalogs().criteria().state(emptyList())
    val evaluation = MutableStateFlow<LotEvaluation?>(null)
    val message = MutableStateFlow<String?>(null)

    init { viewModelScope.launch { members.combine(lot) { a, b -> a to b }.collect { evaluation.value = runCatching { c.repo.evaluateLot(id) }.getOrNull() } } }
    private fun run(b: suspend () -> Unit) = viewModelScope.launch { runCatching { b() }.onFailure { message.value = it.message } }
    fun save(l: LotEntity) = run { c.repo.updateLot(l) }
    fun add(ids: Collection<String>) = run { ids.forEach { c.repo.assignLot(it, id) } }
    fun remove(s: SampleEntity) = run { c.repo.assignLot(s.id, null) }
    fun exclude(s: SampleEntity, reason: String?) = run { c.repo.setExclusion(s.id, reason != null, reason) }
}

@Composable
fun LotScreen(nav: NavController, back: () -> Unit) {
    val vm = appViewModel { c, h -> LotViewModel(c, h) }
    val lot by vm.lot.collectAsStateWithLifecycle()
    val members by vm.members.collectAsStateWithLifecycle()
    val e by vm.evaluation.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    ErrorSnack(vm.message.collectAsStateWithLifecycle().value, { vm.message.value = null }, snack)
    var edit by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var excluding by remember { mutableStateOf<SampleEntity?>(null) }
    Scaffold(topBar = { AppBar("Lote ${lot?.code ?: ""}", lot?.description, back) {
        IconButton(onClick = { edit = true }) { Icon(Icons.Default.Edit, "Editar lote") }
        IconButton(onClick = { nav.navigate(Routes.history(vm.id)) }) { Icon(Icons.Default.History, "Historial del lote") }
    } }, floatingActionButton = { LabeledFab("Agregar muestras", Icons.Default.PlaylistAdd, { adding = true }) },
        snackbarHost = { SnackbarHost(snack) }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
            e?.let { ev -> item {
                SectionCard("Evaluación del lote", Icons.Default.Rule) {
                    LotStatusIcon(ev.status)
                    KeyValue("Registradas", ev.registered.toString()); KeyValue("Incluidas (n)", ev.included.toString())
                    KeyValue("Excluidas", ev.excluded.toString()); KeyValue("Pendientes", ev.pending.toString())
                    KeyValue("Dm (media de Di)", Fmt.withUnit(ev.stats.mean, 2, "kg/m³"))
                    KeyValue("Mínimo / máximo", "${Fmt.num(ev.stats.min, 2)} / ${Fmt.num(ev.stats.max, 2)} kg/m³")
                    KeyValue("s (desv. estándar muestral)", ev.stats.sampleSd?.let { Fmt.withUnit(it, 2, "kg/m³") } ?: "No disponible (n < 2)")
                    KeyValue("Coef. de variación", ev.stats.cvPercent?.let { "${Fmt.num(it, 2)} %" } ?: "—")
                    KeyValue("k(n)", ev.k?.let { Fmt.num(it, 3) } ?: "No definido para n = ${ev.stats.n}")
                    KeyValue("De", Fmt.withUnit(ev.de, 1, "kg/m³"))
                    KeyValue("Dm − k·s", Fmt.withUnit(ev.leftSide, 2, "kg/m³")); KeyValue("α·De", Fmt.withUnit(ev.rightSide, 2, "kg/m³"))
                    KeyValue("Compactación media", Fmt.withUnit(ev.compactionStats.mean, 2, "%"))
                    KeyValue("Di individuales ≥ α·De", ev.individualAboveThreshold?.let { "$it de ${ev.included}" } ?: "No aplica")
                    Text("El conteo de muestras que superan el umbral es descriptivo y NO equivale a la aceptación estadística del lote.", style = MaterialTheme.typography.bodySmall, color = Condor.Steel)
                    ev.reasons.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = Condor.Warn) }
                    if (!ev.criterionValidated) Banner("Evaluación provisional; criterio no validado. No constituye conformidad certificada.", Condor.Warn)
                }
            } }
            item { Text("Muestras del lote (${members.size})", style = MaterialTheme.typography.titleSmall, color = Condor.Steel, modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 4.dp)) }
            items(members, key = { it.id }) { s ->
                ListItem(
                    headlineContent = { Text("${s.code} · ${s.abscisaText ?: "—"}") },
                    supportingContent = { Text("Di ${Fmt.num(s.cacheP, 2)} kg/m³ · ${Fmt.num(s.cacheS, 2)} %" + (if (s.lotExcluded) "\nExcluida: ${s.lotExclusionReason}" else "")) },
                    leadingContent = { StatusChip(SampleStatus.of(s.status)) },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { if (s.lotExcluded) vm.exclude(s, null) else excluding = s }) { Icon(if (s.lotExcluded) Icons.Default.AddCircleOutline else Icons.Default.RemoveCircleOutline, if (s.lotExcluded) "Reincorporar" else "Excluir con motivo") }
                            IconButton(onClick = { vm.remove(s) }) { Icon(Icons.Default.LinkOff, "Retirar del lote") }
                        }
                    },
                )
                HorizontalDivider()
            }
        }
    }
    excluding?.let { s -> ReasonDialog("Excluir ${s.code} de la evaluación", "La muestra sigue en el lote y en los informes, pero no entra en los cálculos. El motivo queda registrado.", confirm = "Excluir", onConfirm = { vm.exclude(s, it) }, onDismiss = { excluding = null }) }
    if (adding) AddSamplesDialog(vm) { adding = false }
    if (edit) lot?.let { LotEditDialog(vm, it) { edit = false } }
}

@Composable
private fun AddSamplesDialog(vm: LotViewModel, onDismiss: () -> Unit) {
    val all by vm.activitySamples.collectAsStateWithLifecycle()
    val lots by vm.allLots.collectAsStateWithLifecycle()
    val codes = lots.associate { it.id to it.code }
    var sel by remember { mutableStateOf(setOf<String>()) }
    val candidates = all.filter { it.lotId != vm.id }
    FormDialog("Agregar muestras al lote", onDismiss, { vm.add(sel); onDismiss() }, sel.isNotEmpty()) {
        Text("Una muestra pertenece a un único lote: si ya está en otro, se trasladará y quedará registrado.", style = MaterialTheme.typography.bodySmall)
        if (candidates.isEmpty()) Text("No hay otras muestras en la actividad.")
        candidates.forEach { s ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(s.id in sel, { sel = if (it) sel + s.id else sel - s.id })
                Text("${s.code} · ${s.abscisaText ?: "—"} · ${SampleStatus.of(s.status).label}" + (s.lotId?.let { " (en ${codes[it]})" } ?: ""))
            }
        }
    }
}

@Composable
private fun LotEditDialog(vm: LotViewModel, l: LotEntity, onDismiss: () -> Unit) {
    val mats by vm.materials.collectAsStateWithLifecycle()
    val labs by vm.labRefs.collectAsStateWithLifecycle()
    val crits by vm.criteria.collectAsStateWithLifecycle()
    var desc by remember { mutableStateOf(l.description) }
    var section by remember { mutableStateOf(l.section) }
    var part by remember { mutableStateOf(l.fillPart) }
    var mat by remember { mutableStateOf(l.materialId) }
    var lab by remember { mutableStateOf(l.labRefId) }
    var crit by remember { mutableStateOf(l.criterionId) }
    FormDialog("Lote ${l.code}", onDismiss, { vm.save(l.copy(description = desc, section = section, fillPart = part, materialId = mat, labRefId = lab, criterionId = crit)); onDismiss() }) {
        TextInput("Descripción", desc, { desc = it }); TextInput("Tramo o localización", section, { section = it }); TextInput("Parte del relleno", part, { part = it })
        Dropdown("Material", mats, mats.firstOrNull { it.id == mat }, { it.name }, { mat = it?.id })
        Dropdown("Referencia de laboratorio declarada", labs, labs.firstOrNull { it.id == lab }, { it.code }, { lab = it?.id })
        Dropdown("Criterio", crits.filter { !it.archived }, crits.firstOrNull { it.id == crit }, { "${it.code} v${it.version}${if (!it.validated) " (no validado)" else ""}" }, { crit = it?.id })
    }
}
