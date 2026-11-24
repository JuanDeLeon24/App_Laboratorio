package com.geodensidad.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.geodensidad.app.data.*
import com.geodensidad.app.domain.ActivityStatus
import com.geodensidad.app.domain.Clock
import com.geodensidad.app.domain.SampleStatus
import com.geodensidad.app.report.GlobalFilter
import com.geodensidad.app.ui.Routes
import com.geodensidad.app.ui.appViewModel
import com.geodensidad.app.ui.components.*
import com.geodensidad.app.ui.theme.Condor
import com.geodensidad.core.criteria.LotEvaluationStatus
import com.geodensidad.core.number.DecimalText
import com.geodensidad.core.number.Fmt
import java.time.LocalDate

@Composable
fun ActivityScreen(nav: NavController, back: () -> Unit) {
    val vm = appViewModel { c, h -> ActivityViewModel(c, h) }
    val act by vm.activity.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    var editDefaults by remember { mutableStateOf(false) }
    val snack = remember { SnackbarHostState() }
    ErrorSnack(vm.message.collectAsStateWithLifecycle().value, { vm.message.value = null }, snack)
    val tabs = listOf("Muestras" to Icons.Default.Science, "Jornadas" to Icons.Default.CalendarMonth, "Lotes" to Icons.Default.Inventory, "Panel" to Icons.Default.Insights, "Informes" to Icons.Default.PictureAsPdf)
    Scaffold(
        topBar = {
            Column {
                AppBar(act?.name ?: "Actividad", act?.let { "Estado: ${ActivityStatus.of(it.status).label}" }, back) {
                    IconButton(onClick = { editDefaults = true }) { Icon(Icons.Default.Tune, "Configuración predeterminada") }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Estado de la actividad") }
                    DropdownMenu(menu, { menu = false }) {
                        ActivityStatus.entries.forEach { s -> DropdownMenuItem(text = { Text("Marcar como ${s.label.lowercase()}") }, onClick = { menu = false; vm.setStatus(s.name) }) }
                        DropdownMenuItem(text = { Text("Historial de la actividad") }, onClick = { menu = false; nav.navigate(Routes.history(vm.id)) })
                    }
                }
                ScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) {
                    tabs.forEachIndexed { i, (t, ic) -> Tab(tab == i, { tab = i }, text = { Text(t) }, icon = { Icon(ic, null) }) }
                }
            }
        },
        snackbarHost = { SnackbarHost(snack) },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (act?.status == "CLOSED" || act?.status == "ARCHIVED") Banner("Actividad ${ActivityStatus.of(act!!.status).label.lowercase()}: sus datos se conservan.", Condor.Steel)
            when (tab) {
                0 -> SamplesTab(vm, nav)
                1 -> JourneysTab(vm, nav)
                2 -> LotsTab(vm, nav)
                3 -> DashboardTab(vm)
                4 -> ReportsTab(vm, nav)
            }
        }
    }
    if (editDefaults) act?.let { DefaultsDialog(vm, it) { editDefaults = false } }
}

@Composable
private fun SamplesTab(vm: ActivityViewModel, nav: NavController) {
    val samples by vm.samples.collectAsStateWithLifecycle()
    val journeys by vm.journeys.collectAsStateWithLifecycle()
    val lots by vm.lots.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var filter by remember { mutableStateOf(setOf<SampleStatus>()) }
    var chooseJourney by remember { mutableStateOf(false) }
    var needJourney by remember { mutableStateOf(false) }
    val lotCodes = lots.associate { it.id to it.code }
    val jDates = journeys.associate { it.id to it.workDate }
    val shown = samples.filter { s ->
        (filter.isEmpty() || SampleStatus.of(s.status) in filter) &&
            (query.isBlank() || listOfNotNull(s.code, s.abscisaText, s.side, s.layer, lotCodes[s.lotId]).any { it.contains(query, true) })
    }
    Scaffold(floatingActionButton = {
        ExtendedFloatingActionButton(onClick = { if (busy) Unit else if (journeys.isEmpty()) needJourney = true else if (journeys.size == 1) vm.newSample(journeys.first().id) { nav.navigate(Routes.sample(it)) } else chooseJourney = true },
            icon = { Icon(Icons.Default.Add, null) }, text = { Text("Nueva muestra") }, expanded = !busy)
    }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
            item {
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(12.dp, 8.dp), placeholder = { Text("Buscar por código, abscisa, costado, capa o lote") },
                    leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true)
                Box(Modifier.padding(horizontal = 12.dp)) { ChipSelector(SampleStatus.entries, filter, { "${it.label} (${samples.count { s -> s.status == it.name }})" }) { filter = if (it in filter) filter - it else filter + it } }
                Text("${shown.size} de ${samples.size} muestras", style = MaterialTheme.typography.labelMedium, color = Condor.Steel, modifier = Modifier.padding(start = 16.dp, top = 4.dp))
            }
            if (samples.isEmpty()) item { EmptyState(Icons.Default.Science, "Sin muestras", if (journeys.isEmpty()) "Primero registre una jornada (fecha, responsable y frente)." else "Use «Nueva muestra» para capturar un ensayo.") }
            items(shown, key = { it.id }) { s ->
                ListItem(
                    headlineContent = { Text("${s.code}  ·  r${s.revisionNumber}", fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text(listOfNotNull(s.abscisaText, s.side?.let { "Costado $it" }, s.layer?.let { "Capa $it" }, Clock.displayDate(jDates[s.journeyId]), s.lotId?.let { "Lote ${lotCodes[it]}" }).joinToString(" · ")) },
                    trailingContent = {
                        Column(horizontalAlignment = Alignment.End) {
                            StatusChip(SampleStatus.of(s.status))
                            Text(s.cacheS?.let { "${Fmt.num(it, 2)} %" } ?: if (s.cacheBlocking > 0) "Con errores" else "Sin resultado", style = MaterialTheme.typography.labelLarge,
                                color = if (s.cacheBlocking > 0) Condor.Bad else Condor.NavyDeep)
                        }
                    },
                    modifier = Modifier.clickable { nav.navigate(Routes.sample(s.id)) },
                )
                HorizontalDivider()
            }
        }
    }
    if (needJourney) ConfirmDialog("Se requiere una jornada", "Cada muestra pertenece a una jornada. ¿Crear la jornada de hoy (${Clock.displayDate(LocalDate.now().toString())})?", "Crear jornada",
        onConfirm = { vm.saveJourney(null, LocalDate.now().toString(), "", "", "", "") { j -> vm.newSample(j) { nav.navigate(Routes.sample(it)) } } }, onDismiss = { needJourney = false })
    if (chooseJourney) AlertDialog(onDismissRequest = { chooseJourney = false }, title = { Text("¿En qué jornada?") }, text = {
        Column { journeys.take(8).forEach { j -> ListItem(headlineContent = { Text(Clock.displayDate(j.workDate)) }, supportingContent = { Text(listOf(j.responsible, j.workFront).filter { it.isNotBlank() }.joinToString(" · ")) },
            modifier = Modifier.clickable { chooseJourney = false; vm.newSample(j.id) { nav.navigate(Routes.sample(it)) } }) } }
    }, confirmButton = {}, dismissButton = { TextButton({ chooseJourney = false }) { Text("Cancelar") } })
}

@Composable
private fun JourneysTab(vm: ActivityViewModel, nav: NavController) {
    val journeys by vm.journeys.collectAsStateWithLifecycle()
    val samples by vm.samples.collectAsStateWithLifecycle()
    var edit by remember { mutableStateOf<JourneyEntity?>(null) }
    var create by remember { mutableStateOf(false) }
    Scaffold(floatingActionButton = { ExtendedFloatingActionButton(onClick = { create = true }, icon = { Icon(Icons.Default.Add, null) }, text = { Text("Nueva jornada") }) }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
            if (journeys.isEmpty()) item { EmptyState(Icons.Default.CalendarMonth, "Sin jornadas", "Registre la fecha de trabajo, el responsable y el frente.") }
            items(journeys, key = { it.id }) { j ->
                ListItem(
                    headlineContent = { Text(Clock.displayDate(j.workDate), fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text(listOf(j.responsible, j.workFront, j.weather, "${samples.count { it.journeyId == j.id }} muestras").filter { it.isNotBlank() }.joinToString(" · ")) },
                    leadingContent = { Icon(if (j.status == "OPEN") Icons.Default.EventAvailable else Icons.Default.EventBusy, null, tint = Condor.Navy) },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { nav.navigate(Routes.journeyPhotos(vm.id, j.id)) }) { Icon(Icons.Default.PhotoLibrary, "Evidencias de la jornada") }
                            IconButton(onClick = { edit = j }) { Icon(Icons.Default.Edit, "Editar") }
                        }
                    },
                )
                HorizontalDivider()
            }
        }
    }
    if (create || edit != null) JourneyDialog(edit, onDismiss = { create = false; edit = null }, onClose = { edit?.let(vm::closeJourney); edit = null }) { d, r, f, w, o ->
        vm.saveJourney(edit, d, r, f, w, o); create = false; edit = null
    }
}

@Composable
private fun JourneyDialog(j: JourneyEntity?, onDismiss: () -> Unit, onClose: () -> Unit, onSave: (String, String, String, String, String) -> Unit) {
    var date by remember { mutableStateOf(j?.workDate ?: LocalDate.now().toString()) }
    var resp by remember { mutableStateOf(j?.responsible ?: "") }
    var front by remember { mutableStateOf(j?.workFront ?: "") }
    var weather by remember { mutableStateOf(j?.weather ?: "") }
    var obs by remember { mutableStateOf(j?.observations ?: "") }
    val dateOk = runCatching { LocalDate.parse(date) }.isSuccess
    FormDialog(if (j == null) "Nueva jornada" else "Editar jornada", onDismiss, { onSave(date, resp, front, weather, obs) }, dateOk) {
        OutlinedTextField(date, { date = it }, label = { Text("Fecha de trabajo (AAAA-MM-DD)") }, isError = !dateOk, singleLine = true, modifier = Modifier.fillMaxWidth(),
            supportingText = { Text(if (dateOk) Clock.displayDate(date) else "Formato AAAA-MM-DD") })
        TextInput("Responsable", resp, { resp = it }); TextInput("Frente de trabajo", front, { front = it })
        TextInput("Condiciones ambientales (opcional)", weather, { weather = it }); TextInput("Observaciones", obs, { obs = it }, singleLine = false, minLines = 2)
        if (j != null) TextButton(onClick = onClose) { Text(if (j.status == "OPEN") "Cerrar jornada" else "Reabrir jornada") }
    }
}

@Composable
private fun LotsTab(vm: ActivityViewModel, nav: NavController) {
    val lots by vm.lots.collectAsStateWithLifecycle()
    val evals by vm.lotEvaluations.collectAsStateWithLifecycle()
    var create by remember { mutableStateOf(false) }
    Scaffold(floatingActionButton = { ExtendedFloatingActionButton(onClick = { create = true }, icon = { Icon(Icons.Default.Add, null) }, text = { Text("Nuevo lote") }) }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
            item { Banner("Un lote es un conjunto EXPLÍCITO de muestras que se evalúa estadísticamente. Puede abarcar varias jornadas; una muestra pertenece a un solo lote.", Condor.Steel) }
            if (lots.isEmpty()) item { EmptyState(Icons.Default.Inventory, "Sin lotes", "Cree un lote y asígnele muestras para evaluarlas en conjunto.") }
            items(lots, key = { it.id }) { l ->
                val e = evals[l.id]
                ListItem(
                    headlineContent = { Text("Lote ${l.code}", fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text((e?.let { "${it.included} incluidas · ${it.excluded} excluidas · ${it.pending} pendientes\n${it.status.label}" } ?: "Evaluando…") + (l.description.takeIf { it.isNotBlank() }?.let { "\n$it" } ?: "")) },
                    leadingContent = { Icon(Icons.Default.Inventory, null, tint = Condor.Navy) },
                    trailingContent = { e?.let { LotStatusIcon(it.status) } },
                    modifier = Modifier.clickable { nav.navigate(Routes.lot(l.id)) },
                )
                HorizontalDivider()
            }
        }
    }
    if (create) {
        var code by remember { mutableStateOf("L-%02d".format(lots.size + 1)) }
        var desc by remember { mutableStateOf("") }
        FormDialog("Nuevo lote", { create = false }, { vm.newLot(code, desc) { create = false; nav.navigate(Routes.lot(it)) } }, code.isNotBlank()) {
            TextInput("Código *", code, { code = it }); TextInput("Descripción / tramo", desc, { desc = it })
        }
    }
}

@Composable
fun LotStatusIcon(s: LotEvaluationStatus) {
    when (s) {
        LotEvaluationStatus.NOT_EVALUABLE -> Pill("No evaluable", Icons.Default.HelpOutline, Condor.Slate)
        LotEvaluationStatus.PROVISIONAL_MEETS -> Pill("Provisional: cumple", Icons.Default.CheckCircleOutline, Condor.Ok)
        LotEvaluationStatus.PROVISIONAL_FAILS -> Pill("Provisional: no cumple", Icons.Default.HighlightOff, Condor.Bad)
        LotEvaluationStatus.MEETS -> Pill("Cumple", Icons.Default.CheckCircle, Condor.Ok)
        LotEvaluationStatus.FAILS -> Pill("No cumple", Icons.Default.Cancel, Condor.Bad)
    }
}

@Composable
private fun DashboardTab(vm: ActivityViewModel) {
    val d by vm.dashboard.collectAsStateWithLifecycle()
    val samples by vm.samples.collectAsStateWithLifecycle()
    val journeys by vm.journeys.collectAsStateWithLifecycle()
    val lots by vm.lots.collectAsStateWithLifecycle()
    val inc = d.records.filter { it.included }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            SectionCard("Indicadores de la actividad", Icons.Default.Insights) {
                KeyValue("Muestras registradas", samples.size.toString())
                SampleStatus.entries.forEach { s -> KeyValue("· ${s.label}", samples.count { it.status == s.name }.toString()) }
                KeyValue("Jornadas", journeys.size.toString()); KeyValue("Lotes", lots.size.toString())
                KeyValue("Pendientes (borrador o con errores)", samples.count { it.status == "DRAFT" || it.cacheBlocking > 0 }.toString())
                KeyValue("Incluidas en estadísticas", "${inc.size} (no incluidas: ${d.records.size - inc.size})")
                KeyValue("Criterio para umbrales", d.criterionLabel)
            }
        }
        if (d.groups.isEmpty()) item { EmptyState(Icons.Default.QueryStats, "Sin observaciones vigentes", "Las estadísticas usan solo muestras completas, revisadas o aprobadas, sin errores.") }
        items(d.groups) { g ->
            SectionCard(g.title, Icons.Default.Functions) {
                g.pairs.forEach { (k, v) -> KeyValue(k, v) }
                g.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Condor.Steel) }
            }
        }
        items(d.charts) { ch ->
            Card(Modifier.fillMaxWidth().padding(12.dp, 6.dp)) { ChartView(ch, Modifier.padding(4.dp)) }
        }
        item { Text("Los gráficos muestran solo observaciones incluidas; la línea de referencia proviene del criterio seleccionado. Con menos de 5 observaciones no se construye distribución.",
            style = MaterialTheme.typography.bodySmall, color = Condor.Steel, modifier = Modifier.padding(16.dp)) }
    }
}

@Composable
private fun ReportsTab(vm: ActivityViewModel, nav: NavController) {
    val reports by vm.reports.collectAsStateWithLifecycle()
    val journeys by vm.journeys.collectAsStateWithLifecycle()
    val lots by vm.lots.collectAsStateWithLifecycle()
    val materials by vm.materials.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    var from by rememberSaveable { mutableStateOf("") }
    var to by rememberSaveable { mutableStateOf("") }
    var js by remember { mutableStateOf(setOf<JourneyEntity>()) }
    var ls by remember { mutableStateOf(setOf<LotEntity>()) }
    var ms by remember { mutableStateOf(setOf<MaterialEntity>()) }
    var sts by remember { mutableStateOf(SampleStatus.entries.toSet()) }
    var photos by rememberSaveable { mutableStateOf(true) }
    var obs by rememberSaveable { mutableStateOf("") }
    val fromOk = from.isBlank() || runCatching { LocalDate.parse(from) }.isSuccess
    val toOk = to.isBlank() || runCatching { LocalDate.parse(to) }.isSuccess
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            SectionCard("Informe global: alcance y filtros", Icons.Default.FilterAlt) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(from, { from = it }, label = { Text("Desde (AAAA-MM-DD)") }, isError = !fromOk, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(to, { to = it }, label = { Text("Hasta (AAAA-MM-DD)") }, isError = !toOk, singleLine = true, modifier = Modifier.weight(1f))
                }
                if (journeys.isNotEmpty()) { Text("Jornadas (vacío = todas)", style = MaterialTheme.typography.labelLarge); ChipSelector(journeys, js, { Clock.displayDate(it.workDate) }) { js = if (it in js) js - it else js + it } }
                if (lots.isNotEmpty()) { Text("Lotes (vacío = todos)", style = MaterialTheme.typography.labelLarge); ChipSelector(lots, ls, { it.code }) { ls = if (it in ls) ls - it else ls + it } }
                if (materials.isNotEmpty()) { Text("Materiales (vacío = todos)", style = MaterialTheme.typography.labelLarge); ChipSelector(materials.filter { !it.archived }, ms, { it.name }) { ms = if (it in ms) ms - it else ms + it } }
                Text("Estados incluidos en la tabla", style = MaterialTheme.typography.labelLarge)
                ChipSelector(SampleStatus.entries, sts, { it.label }) { sts = if (it in sts) sts - it else sts + it }
                Row(verticalAlignment = Alignment.CenterVertically) { Switch(photos, { photos = it }); Spacer(Modifier.width(8.dp)); Text("Incluir anexo fotográfico (fotos marcadas para informe)") }
                TextInput("Observaciones del informe", obs, { obs = it }, singleLine = false, minLines = 2)
                Text("Las muestras en borrador o con errores se listan pero no entran en los cálculos; se explica su exclusión.", style = MaterialTheme.typography.bodySmall, color = Condor.Steel)
                if (progress != null) {
                    LinearProgressIndicator(progress = { progress!!.fraction }, Modifier.fillMaxWidth())
                    Row(verticalAlignment = Alignment.CenterVertically) { Text(progress!!.message, Modifier.weight(1f)); TextButton(onClick = vm::cancelReport) { Text("Cancelar") } }
                } else BusyButton("Generar PDF global", false, {
                    vm.generateGlobal(GlobalFilter(vm.id, from.ifBlank { null }, to.ifBlank { null }, js.map { it.id }.toSet(), ls.map { it.id }.toSet(), ms.map { it.id }.toSet(), sts, photos, obs)) { nav.navigate(Routes.report(it)) }
                }, Modifier.fillMaxWidth(), Icons.Default.PictureAsPdf, enabled = fromOk && toOk && sts.isNotEmpty())
            }
        }
        item { Text("Informes emitidos (${reports.size})", style = MaterialTheme.typography.titleSmall, color = Condor.Steel, modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 4.dp)) }
        if (reports.isEmpty()) item { EmptyState(Icons.Default.PictureAsPdf, "Sin informes", "Los informes emitidos se conservan con su instantánea y hash; no se regeneran.") }
        items(reports, key = { it.id }) { r -> ReportRow(r) { nav.navigate(Routes.report(r.id)) } }
    }
}

@Composable
fun ReportRow(r: ReportEntity, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(r.code, fontWeight = FontWeight.SemiBold) },
        supportingContent = { Text("${if (r.type == "GLOBAL") "Global" else "Individual"} · ${r.issuedAtText} · ${r.pageCount} pág. · plantilla v${r.templateVersion}\n${r.scopeText}", maxLines = 3) },
        leadingContent = { Icon(Icons.Default.PictureAsPdf, null, tint = if (r.draft) Condor.Warn else Condor.Navy) },
        trailingContent = { if (r.draft) Pill("BORRADOR", null, Condor.Warn) },
        modifier = Modifier.clickable(onClick = onClick),
    )
    HorizontalDivider()
}

@Composable
private fun DefaultsDialog(vm: ActivityViewModel, a: ActivityEntity, onDismiss: () -> Unit) {
    val materials by vm.materials.collectAsStateWithLifecycle()
    val cals by vm.calibrations.collectAsStateWithLifecycle()
    val labs by vm.labRefs.collectAsStateWithLifecycle()
    val crits by vm.criteria.collectAsStateWithLifecycle()
    var name by remember { mutableStateOf(a.name) }
    var desc by remember { mutableStateOf(a.description) }
    var mat by remember { mutableStateOf(a.defaultMaterialId) }
    var cal by remember { mutableStateOf(a.defaultCalibrationId) }
    var lab by remember { mutableStateOf(a.defaultLabRefId) }
    var crit by remember { mutableStateOf(a.defaultCriterionId) }
    var bulk by remember { mutableStateOf(DecimalText.storedToEditText(a.defaultBulk)) }
    var prefix by remember { mutableStateOf(a.samplePrefix) }
    FormDialog("Configuración de la actividad", onDismiss, {
        vm.saveDefaults(a.copy(name = name, description = desc, defaultMaterialId = mat, defaultCalibrationId = cal, defaultLabRefId = lab, defaultCriterionId = crit,
            defaultBulk = DecimalText.toCanonicalOrNull(bulk), samplePrefix = prefix.trim().ifBlank { "DEN" }.uppercase())); onDismiss()
    }, name.isNotBlank()) {
        TextInput("Nombre", name, { name = it }); TextInput("Descripción", desc, { desc = it })
        TextInput("Prefijo de código de muestra", prefix, { prefix = it }, supporting = "Ej.: DEN → DEN-0001")
        Text("Valores propuestos para muestras nuevas (cada muestra conserva los suyos):", style = MaterialTheme.typography.bodySmall, color = Condor.Steel)
        Dropdown("Material", materials.filter { !it.archived }, materials.firstOrNull { it.id == mat }, { "${it.code} – ${it.name}" }, { mat = it?.id })
        Dropdown("Calibración", cals.filter { !it.archived }, cals.firstOrNull { it.id == cal }, { "${it.code} (${it.date})" }, { cal = it?.id })
        Dropdown("Referencia de laboratorio", labs.filter { !it.archived }, labs.firstOrNull { it.id == lab }, { it.code }, { lab = it?.id })
        Dropdown("Criterio de evaluación", crits.filter { !it.archived }, crits.firstOrNull { it.id == crit }, { "${it.code} v${it.version}${if (!it.validated) " (no validado)" else ""}" }, { crit = it?.id })
        NumberInput("Parámetro bulk", bulk, "(×1000 kg/m³)", { bulk = it }, symbol = "I", hint = "Valor heredado del Excel: 2,667")
    }
}
