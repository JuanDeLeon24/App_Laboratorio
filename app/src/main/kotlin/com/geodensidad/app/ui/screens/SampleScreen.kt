package com.geodensidad.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.geodensidad.app.data.CustomFieldEntity
import com.geodensidad.app.domain.*
import com.geodensidad.app.ui.Routes
import com.geodensidad.app.ui.appViewModel
import com.geodensidad.app.ui.components.*
import com.geodensidad.app.ui.theme.Condor
import com.geodensidad.core.calc.InputDefs
import com.geodensidad.core.calc.LegacyResult
import com.geodensidad.core.calc.Severity
import com.geodensidad.core.location.Abscisa
import com.geodensidad.core.number.Fmt
import com.geodensidad.core.report.ReportRows
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.serializer
import java.io.File

@Composable
fun SampleScreen(nav: NavController, back: () -> Unit) {
    val vm = appViewModel { c, h -> SampleViewModel(c, h) }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val s by vm.sample.collectAsStateWithLifecycle()
    val form by vm.form.collectAsStateWithLifecycle()
    val texts by vm.texts.collectAsStateWithLifecycle()
    val result by vm.result.collectAsStateWithLifecycle()
    val rev by vm.currentRevision.collectAsStateWithLifecycle()
    val save by vm.saveState.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val photos by vm.photos.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    ErrorSnack(vm.message.collectAsStateWithLifecycle().value, { vm.message.value = null }, snack)
    var menu by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<String?>(null) }
    val status = SampleStatus.of(s?.status ?: "DRAFT")
    val editable = rev?.locked == false && status != SampleStatus.VOID

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> vm.onCameraResult(ok, ctx) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { uris -> vm.importUris(uris) }
    val goBack: () -> Unit = { scope.launch { vm.flush(); back() } }

    Scaffold(
        topBar = {
            AppBar(s?.code ?: "Muestra", "Revisión ${rev?.number ?: "-"} · ${saveLabel(save)}", goBack) {
                IconButton(onClick = { vm.generatePdf { nav.navigate(Routes.report(it)) } }, enabled = progress == null) { Icon(Icons.Default.PictureAsPdf, "Generar PDF individual") }
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Acciones") }
                DropdownMenu(menu, { menu = false }) {
                    fun item(t: String, icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean = true, a: () -> Unit) =
                        @Composable { DropdownMenuItem(text = { Text(t) }, leadingIcon = { Icon(icon, null) }, enabled = enabled && !busy, onClick = { menu = false; a() }) }
                    item("Marcar como completa", Icons.Default.TaskAlt, status == SampleStatus.DRAFT) { vm.complete() }()
                    item("Registrar revisión", Icons.Default.FactCheck, status == SampleStatus.COMPLETE) { dialog = "review" }()
                    item("Aprobar (bloquea la revisión)", Icons.Default.Verified, status == SampleStatus.REVIEWED) { dialog = "approve" }()
                    item("Devolver a borrador", Icons.Default.Undo, status == SampleStatus.COMPLETE || status == SampleStatus.REVIEWED) { vm.toDraft() }()
                    item("Nueva revisión", Icons.Default.NoteAdd, rev?.locked == true) { dialog = "revision" }()
                    item("Duplicar datos contextuales", Icons.Default.ContentCopy) { dialog = "duplicate" }()
                    item("Anular muestra", Icons.Default.Block, status != SampleStatus.VOID) { dialog = "void" }()
                    item("Historial", Icons.Default.History) { nav.navigate(Routes.history(vm.id)) }()
                }
            }
        },
        snackbarHost = { SnackbarHost(snack) },
    ) { pad ->
        val f = form
        if (f == null) { Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }; return@Scaffold }
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(bottom = 48.dp)) {
            item {
                Row(Modifier.padding(12.dp, 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusChip(status)
                    result?.let { r -> if (r.blocking.isNotEmpty()) Pill("${r.blocking.size} error(es)", Icons.Default.Error, Condor.Bad) else if (r.warnings.isNotEmpty()) Pill("${r.warnings.size} advertencia(s)", Icons.Default.Warning, Condor.Warn) }
                }
            }
            if (rev?.locked == true) item { Banner("Revisión ${rev!!.number} aprobada por ${rev!!.approvedBy}: bloqueada. Para corregir, use «Nueva revisión» (se exige motivo y se conserva esta).", Condor.Ok, Icons.Default.Lock) }
            if (status == SampleStatus.VOID) item { Banner("Muestra anulada: ${s?.voidReason}. La información se conserva.", Condor.Bad, Icons.Default.Block) }
            progress?.let { p -> item {
                Column(Modifier.padding(12.dp)) { LinearProgressIndicator(progress = { p.fraction }, Modifier.fillMaxWidth()); Row(verticalAlignment = Alignment.CenterVertically) { Text(p.message, Modifier.weight(1f)); TextButton(onClick = vm::cancelPdf) { Text("Cancelar") } } }
            } }
            item { ResultsSummary(result) }
            item { IdentificationSection(vm, f, editable) }
            item { LocationSection(vm, f, texts, editable) }
            item { EquipmentSection(vm, f, texts, editable, result) }
            item { MeasurementSection(vm, texts, editable, result) }
            item { LabSection(vm, f, texts, editable, result) }
            item { CustomSection(vm, f, texts, editable) }
            item { TraceSection(result) }
            item {
                SectionCard("Evidencias fotográficas", Icons.Default.PhotoCamera, badge = "${photos.size}") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BusyButton("Cámara", busy, {
                            val file = vm.newCameraFile()
                            val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", file)
                            runCatching { camera.launch(uri) }.onFailure { vm.message.value = "No hay una aplicación de cámara disponible" }
                        }, Modifier.weight(1f), Icons.Default.PhotoCamera)
                        OutlinedButton(onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, Modifier.weight(1f).heightIn(min = 48.dp), enabled = !busy) {
                            Icon(Icons.Default.PhotoLibrary, null); Spacer(Modifier.width(6.dp)); Text("Galería")
                        }
                    }
                    Text("Los originales se copian al almacenamiento de la app y no se modifican. Toque una foto para editar su pie, fecha e inclusión en informes.", style = MaterialTheme.typography.bodySmall, color = Condor.Steel)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(photos, key = { it.id }) { p ->
                            Box(Modifier.size(104.dp).clip(RoundedCornerShape(8.dp)).clickable { nav.navigate(Routes.photo(p.id)) }) {
                                AsyncImage(File(p.thumbPath), "Fotografía", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                if (!p.includeInReport) Surface(color = Condor.Bad.copy(alpha = 0.85f), modifier = Modifier.align(Alignment.BottomStart)) { Text("No va al PDF", color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(4.dp, 2.dp)) }
                            }
                        }
                    }
                }
            }
            item {
                SectionCard("Observaciones y responsables", Icons.Default.Notes) {
                    TextInput("Observaciones", f.observations, { v -> vm.update { it.copy(observations = v) } }, enabled = editable, singleLine = false, minLines = 3)
                    TextInput("Elaboró", f.elaboratedBy, { v -> vm.update { it.copy(elaboratedBy = v) } }, enabled = editable)
                    rev?.reviewedBy?.let { KeyValue("Revisó", "$it · ${Clock.display(rev!!.reviewedAt!!)}") }
                    rev?.approvedBy?.let { KeyValue("Aprobó", "$it · ${Clock.display(rev!!.approvedAt!!)}") }
                    Text("El nombre del responsable es atribución operativa local, no una firma ni autenticación.", style = MaterialTheme.typography.bodySmall, color = Condor.Steel)
                }
            }
            item { RevisionsSection(vm, nav) }
        }
    }
    when (dialog) {
        "review", "approve" -> {
            var name by remember { mutableStateOf("") }
            LaunchedEffect(Unit) { name = vm.reviewerDefault() }
            FormDialog(if (dialog == "review") "Registrar revisión" else "Aprobar revisión ${rev?.number}", { dialog = null }, {
                if (dialog == "review") vm.review(name) else vm.approve(name); dialog = null
            }, name.isNotBlank()) {
                if (dialog == "approve") Text("La aprobación bloquea esta revisión. Cualquier cambio posterior requerirá una nueva revisión con motivo. La aprobación documental es independiente del resultado técnico.")
                TextInput("Responsable", name, { name = it })
            }
        }
        "revision" -> ReasonDialog("Nueva revisión", "Se creará la revisión ${(rev?.number ?: 0) + 1} como borrador con los mismos datos. La revisión ${rev?.number} aprobada y sus informes se conservan.", confirm = "Crear", onConfirm = vm::newRevision, onDismiss = { dialog = null })
        "void" -> ReasonDialog("Anular muestra", "La muestra se marcará como anulada. No se elimina ninguna información.", confirm = "Anular", onConfirm = vm::void, onDismiss = { dialog = null })
        "duplicate" -> {
            var sel by remember { mutableStateOf(setOf(DuplicableField.JOURNEY, DuplicableField.ORIGIN, DuplicableField.LOCATION, DuplicableField.SIDE, DuplicableField.LAYER, DuplicableField.THICKNESS, DuplicableField.FILL_PART, DuplicableField.MATERIAL, DuplicableField.CALIBRATION, DuplicableField.LAB_REF)) }
            FormDialog("Duplicar muestra", { dialog = null }, { vm.duplicate(sel) { dialog = null; nav.navigate(Routes.sample(it)) } }) {
                Text("Se copian solo los datos contextuales marcados. Nunca se copian mediciones, resultados, fotografías ni aprobaciones.", style = MaterialTheme.typography.bodySmall)
                DuplicableField.entries.forEach { d -> Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(d in sel, { sel = if (it) sel + d else sel - d }); Text(d.label) } }
            }
        }
    }
}

private fun saveLabel(s: SaveState) = when (s) {
    SaveState.Idle -> "Sin cambios"; SaveState.Pending -> "Guardando…"; is SaveState.Saved -> "Guardado ${Clock.display(s.at).substring(11, 19)}"; is SaveState.Failed -> "Error al guardar: ${s.msg}"
}

@Composable
private fun ResultsSummary(r: LegacyResult?) {
    Card(Modifier.fillMaxWidth().padding(12.dp, 4.dp), colors = CardDefaults.cardColors(containerColor = Condor.Navy)) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("Densidad seca corregida (Di)", style = MaterialTheme.typography.labelMedium, color = Condor.Sky)
                Text(r?.correctedDryDensity?.let { "${Fmt.num(it, 2)} kg/m³" } ?: "No disponible", style = MaterialTheme.typography.titleLarge, color = androidx.compose.ui.graphics.Color.White)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text("Compactación", style = MaterialTheme.typography.labelMedium, color = Condor.Sky)
                Text(r?.compactionPercent?.let { "${Fmt.num(it, 2)} %" } ?: "No disponible", style = MaterialTheme.typography.titleLarge, color = androidx.compose.ui.graphics.Color.White)
            }
        }
        if (r != null && r.compactionPercent == null) Text(r.step("S")?.unavailableReason ?: "", color = Condor.Sky, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp))
    }
}

@Composable
private fun IdentificationSection(vm: SampleViewModel, f: SampleForm, editable: Boolean) {
    val journeys by vm.journeys.collectAsStateWithLifecycle()
    val lots by vm.lots.collectAsStateWithLifecycle()
    val s by vm.sample.collectAsStateWithLifecycle()
    SectionCard("Identificación", Icons.Default.Badge) {
        TextInput("Fecha y hora del ensayo (AAAA-MM-DDTHH:MM)", f.testDateTime ?: "", { v -> vm.update { it.copy(testDateTime = v.ifBlank { null }) } }, enabled = editable,
            supporting = runCatching { java.time.LocalDateTime.parse(f.testDateTime) }.fold({ "Interpretado: ${Clock.displayDate(f.testDateTime)} ${f.testDateTime?.substring(11, 16)}" }, { "Formato esperado 2026-11-24T09:30" }))
        Dropdown("Jornada", journeys, journeys.firstOrNull { it.id == s?.journeyId }, { "${Clock.displayDate(it.workDate)} · ${it.workFront.ifBlank { it.responsible }}" }, { it?.let { j -> vm.moveJourney(j.id) } }, enabled = editable, allowNone = false)
        Dropdown("Lote de evaluación", lots, lots.firstOrNull { it.id == s?.lotId }, { it.code }, { vm.assignLot(it?.id) })
        Text("El lote puede cambiarse también en muestras aprobadas: no modifica sus datos y queda registrado en el historial.", style = MaterialTheme.typography.bodySmall, color = Condor.Steel)
        TextInput("Procedencia", f.origin, { v -> vm.update { it.copy(origin = v) } }, enabled = editable)
        TextInput("Localización", f.location, { v -> vm.update { it.copy(location = v) } }, enabled = editable)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LocationSection(vm: SampleViewModel, f: SampleForm, texts: Map<String, String>, editable: Boolean) {
    SectionCard("Localización", Icons.Default.Place) {
        val m = Abscisa.toMeters(f.abscisa)
        TextInput("Abscisa *", f.abscisa, { v -> vm.update { it.copy(abscisa = v) } }, enabled = editable,
            supporting = if (f.abscisa.isBlank()) "Ej.: PR 003+150" else m?.let { "Valor numérico: ${Fmt.num(it, 2)} m (${Abscisa.formatMeters(it)})" } ?: "Sin valor numérico (se conserva el texto tal cual)")
        Text("Costado", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Izquierdo", "Eje", "Derecho").forEach { o -> FilterChip(f.side == o, { if (editable) vm.update { it.copy(side = if (it.side == o) "" else o) } }, label = { Text(o) }, enabled = editable) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(f.layer, { v -> vm.update { it.copy(layer = v) } }, label = { Text("Capa No.") }, enabled = editable, singleLine = true, modifier = Modifier.weight(1f))
            NumberInput("Espesor", texts["thickness"] ?: "", f.thicknessUnit, { vm.setNumber("thickness", it) }, Modifier.weight(1f), editable)
        }
        TextInput("Parte del relleno", f.fillPart, { v -> vm.update { it.copy(fillPart = v) } }, enabled = editable, supporting = "Ej.: Núcleo, Corona")
    }
}

private fun fieldError(r: LegacyResult?, sym: String) = r?.issues?.filter { it.field == sym && it.severity == Severity.BLOCKING }?.joinToString("; ") { it.message }?.ifBlank { null }
private fun fieldWarn(r: LegacyResult?, sym: String) = r?.issues?.filter { it.field == sym && it.severity == Severity.WARNING }?.joinToString("; ") { it.message }?.ifBlank { null }

@Composable
private fun EquipmentSection(vm: SampleViewModel, f: SampleForm, texts: Map<String, String>, editable: Boolean, r: LegacyResult?) {
    val cals by vm.calibrations.collectAsStateWithLifecycle()
    val eqs by vm.equipment.collectAsStateWithLifecycle()
    SectionCard("Equipo y calibración", Icons.Default.Build) {
        Dropdown("Calibración (propone D y F)", cals.filter { !it.archived || it.id == f.calibrationId }, cals.firstOrNull { it.id == f.calibrationId },
            { c -> "${c.code} · ${eqs.firstOrNull { it.id == c.equipmentId }?.code ?: ""} · ${c.date}" }, { vm.applyCalibration(it?.id) }, enabled = editable)
        cals.firstOrNull { it.id == f.calibrationId }?.validUntil?.let { v -> if (v < java.time.LocalDate.now().toString()) Banner("Calibración vencida el ${Clock.displayDate(v)}", Condor.Warn) }
        NumberInput(InputDefs.get("D").name, texts["d"] ?: "", "g", { vm.setNumber("d", it) }, enabled = editable, symbol = "D", error = fieldError(r, "D"))
        NumberInput(InputDefs.get("F").name, texts["f"] ?: "", "g/cm³", { vm.setNumber("f", it) }, enabled = editable, symbol = "F", error = fieldError(r, "F"), hint = fieldWarn(r, "F"))
        NumberInput(InputDefs.get("I").name, texts["i"] ?: "", "×1000 kg/m³", { vm.setNumber("i", it) }, enabled = editable, symbol = "I", error = fieldError(r, "I"),
            hint = fieldWarn(r, "I") ?: "Interpretación heredada pendiente de revisión técnica")
    }
}

@Composable
private fun MeasurementSection(vm: SampleViewModel, texts: Map<String, String>, editable: Boolean, r: LegacyResult?) {
    SectionCard("Mediciones", Icons.Default.Scale) {
        NumberInput(InputDefs.get("A").name, texts["a"] ?: "", "g", { vm.setNumber("a", it) }, enabled = editable, symbol = "A", error = fieldError(r, "A"))
        NumberInput(InputDefs.get("B").name, texts["b"] ?: "", "g", { vm.setNumber("b", it) }, enabled = editable, symbol = "B", error = fieldError(r, "B"),
            hint = r?.value("C")?.let { "Arena usada C = ${Fmt.num(it, 1)} g; en el hueco E = ${Fmt.num(r.value("E"), 1)} g" })
        NumberInput(InputDefs.get("J").name, texts["j"] ?: "", "g", { vm.setNumber("j", it) }, enabled = editable, symbol = "J", error = fieldError(r, "J"))
        NumberInput(InputDefs.get("H").name, texts["h"] ?: "", "g", { vm.setNumber("h", it) }, enabled = editable, symbol = "H", error = fieldError(r, "H"),
            hint = fieldWarn(r, "K") ?: r?.value("K")?.let { "Retenido K = ${Fmt.num(it, 2)} %" })
        NumberInput("${InputDefs.get("L").name} (en porcentaje)", texts["l"] ?: "", "%", { vm.setNumber("l", it) }, enabled = editable, symbol = "L", error = fieldError(r, "L"),
            hint = fieldWarn(r, "L") ?: "Digite 6 para 6 % (se guarda como 0,06)")
    }
}

@Composable
private fun LabSection(vm: SampleViewModel, f: SampleForm, texts: Map<String, String>, editable: Boolean, r: LegacyResult?) {
    val labs by vm.labRefs.collectAsStateWithLifecycle()
    val mats by vm.materials.collectAsStateWithLifecycle()
    SectionCard("Referencia de laboratorio y material", Icons.Default.Biotech) {
        Dropdown("Material", mats.filter { !it.archived || it.id == f.materialId }, mats.firstOrNull { it.id == f.materialId }, { "${it.code} – ${it.name}" }, { m -> vm.update { it.copy(materialId = m?.id) } }, enabled = editable)
        Dropdown("Referencia (propone Q y R)", labs.filter { !it.archived || it.id == f.labRefId }, labs.firstOrNull { it.id == f.labRefId }, { "${it.code} · ${it.testId}" }, { vm.applyLabRef(it?.id) }, enabled = editable)
        TextInput("Identificación del ensayo de laboratorio", f.labTestId, { v -> vm.update { it.copy(labTestId = v) } }, enabled = editable)
        NumberInput(InputDefs.get("Q").name, texts["q"] ?: "", "kg/m³", { vm.setNumber("q", it) }, enabled = editable, symbol = "Q", error = fieldError(r, "Q"))
        NumberInput("Humedad óptima (en porcentaje)", texts["r"] ?: "", "%", { vm.setNumber("r", it) }, enabled = editable, symbol = "R", error = fieldError(r, "R"),
            hint = r?.value("ΔW")?.let { "Diferencia con la humedad de campo: ${Fmt.num(it, 2)} puntos %" })
    }
}

@Composable
private fun CustomSection(vm: SampleViewModel, f: SampleForm, texts: Map<String, String>, editable: Boolean) {
    val defs by vm.customFields.collectAsStateWithLifecycle()
    if (defs.isEmpty()) return
    SectionCard("Información complementaria", Icons.Default.DynamicForm, badge = "${defs.size}") {
        defs.forEach { d -> CustomFieldInput(d, texts["c:${d.key}"] ?: f.custom[d.key] ?: "", editable) { vm.setCustom(d, it) } }
    }
}

@Composable
fun CustomFieldInput(d: CustomFieldEntity, value: String, editable: Boolean, onChange: (String) -> Unit) {
    val label = d.label + if (d.required) " *" else ""
    when (CustomFieldType.valueOf(d.type)) {
        CustomFieldType.TEXT -> TextInput(label, value, onChange, enabled = editable, supporting = d.description.ifBlank { null })
        CustomFieldType.NUMBER, CustomFieldType.PERCENT -> {
            val pct = d.type == CustomFieldType.PERCENT.name
            val shown = if (pct) com.geodensidad.core.number.DecimalText.fractionToPercentText(value) else value.replace('.', ',')
            var text by remember(d.key) { mutableStateOf(shown) }
            val v = com.geodensidad.core.number.DecimalText.storedToDouble(value)
            val minV = com.geodensidad.core.number.DecimalText.storedToDouble(d.minValue); val maxV = com.geodensidad.core.number.DecimalText.storedToDouble(d.maxValue)
            val range = if (v != null && ((minV != null && v < minV) || (maxV != null && v > maxV))) "Fuera de límites [${d.minValue ?: "−∞"}, ${d.maxValue ?: "∞"}]" else null
            NumberInput(label, text, if (pct) "%" else d.unit, { t ->
                text = t
                val p = if (pct) com.geodensidad.core.number.DecimalText.percentTextToFraction(t) else com.geodensidad.core.number.DecimalText.parse(t)
                onChange((p as? com.geodensidad.core.number.ParsedDecimal.Valid)?.let { com.geodensidad.core.number.DecimalText.canonical(it.value) } ?: "")
            }, enabled = editable, error = range, hint = d.description.ifBlank { null })
        }
        CustomFieldType.DATE -> TextInput("$label (AAAA-MM-DD)", value, onChange, enabled = editable, supporting = if (value.isBlank() || runCatching { java.time.LocalDate.parse(value) }.isSuccess) d.description.ifBlank { null } else "Fecha no válida")
        CustomFieldType.BOOLEAN -> Row(verticalAlignment = Alignment.CenterVertically) { Switch(value == "true", { onChange(if (it) "true" else "false") }, enabled = editable); Spacer(Modifier.width(8.dp)); Text(label) }
        CustomFieldType.CHOICE -> {
            val opts = runCatching { AppJson.decodeFromString(kotlinx.serialization.builtins.ListSerializer(String.serializer()), d.optionsJson) }.getOrDefault(emptyList<String>())
            Dropdown(label, opts, value.ifBlank { null }, { it }, { onChange(it ?: "") }, enabled = editable)
        }
    }
}

@Composable
private fun TraceSection(r: LegacyResult?) {
    SectionCard("Resultados y trazabilidad", Icons.Default.Functions, initiallyExpanded = false) {
        if (r == null) return@SectionCard
        r.blocking.forEach { Text("✖ ${it.field}: ${it.message}", color = Condor.Bad, style = MaterialTheme.typography.bodyMedium) }
        r.warnings.forEach { Text("⚠ ${it.field}: ${it.message}", color = Condor.Warn, style = MaterialTheme.typography.bodyMedium) }
        Text("Motor ${r.engine} · perfil ${r.profile.id} (wc = ${Fmt.num(r.profile.wc, 2)}, pendiente de validación técnica). Sin redondeos intermedios.", style = MaterialTheme.typography.bodySmall, color = Condor.Steel)
        r.steps.forEach { st ->
            var open by remember(st.symbol) { mutableStateOf(false) }
            Surface(shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth().clickable { open = !open }) {
                Column(Modifier.padding(10.dp)) {
                    Row {
                        Text("${st.symbol}  ${st.name}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text(if (st.value != null) "${ReportRows.resultValue(st.symbol, st.value)} ${if (st.symbol == "N") "%" else st.unit}" else "No disponible", fontWeight = FontWeight.SemiBold,
                            color = if (st.value == null) Condor.Bad else Condor.NavyDeep)
                    }
                    if (open) {
                        Text(st.formula, style = MaterialTheme.typography.bodySmall, color = Condor.Navy)
                        st.substituted?.let { Text("= $it", style = MaterialTheme.typography.bodySmall) }
                        st.value?.let { Text("= ${it} (valor completo, sin redondear)", style = MaterialTheme.typography.bodySmall, color = Condor.Steel) }
                        st.unavailableReason?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Condor.Bad) }
                        Text("Depende de: ${st.dependsOn.joinToString()}", style = MaterialTheme.typography.labelSmall, color = Condor.Slate)
                    }
                }
            }
        }
    }
}

@Composable
private fun RevisionsSection(vm: SampleViewModel, nav: NavController) {
    val revs by vm.revisions.collectAsStateWithLifecycle()
    val reports by vm.reports.collectAsStateWithLifecycle()
    SectionCard("Revisiones e informes", Icons.Default.History, initiallyExpanded = false, badge = "${revs.size} rev. · ${reports.size} inf.") {
        revs.forEach { r ->
            KeyValue("Revisión ${r.number}${if (r.superseded) " (sustituida)" else ""}", "${SampleStatus.of(r.status).label}${if (r.locked) " · bloqueada" else ""} · ${r.engineVersion}" + (r.reason?.let { "\nMotivo: $it" } ?: ""))
        }
        reports.forEach { r -> ReportRow(r) { nav.navigate(Routes.report(r.id)) } }
        TextButton(onClick = { nav.navigate(Routes.history(vm.id)) }) { Icon(Icons.Default.History, null); Text(" Ver historial completo") }
    }
}
