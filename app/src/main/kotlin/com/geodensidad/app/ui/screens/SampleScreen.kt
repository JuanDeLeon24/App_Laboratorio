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
            item { HeaderSection(vm, f, editable) }
            item { DensitySection(vm, f, texts, editable) }
            item { MeasurementSection(vm, texts, editable, result) }
            item { LabSection(vm, texts, editable, result) }
            item { FooterSection(vm, f, editable) }
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

private fun fieldError(r: LegacyResult?, sym: String) = r?.issues?.filter { it.field == sym && it.severity == Severity.BLOCKING }?.joinToString("; ") { it.message }?.ifBlank { null }
private fun fieldWarn(r: LegacyResult?, sym: String) = r?.issues?.filter { it.field == sym && it.severity == Severity.WARNING }?.joinToString("; ") { it.message }?.ifBlank { null }

/** Encabezado del formato Excel: PROYECTO, LOCALIZACIÓN, PROCEDENCIA, PR INICIAL, PR FINAL, LADO, FECHA, PARTE DEL RELLENO, LOTE, REGISTRO. */
@Composable
private fun HeaderSection(vm: SampleViewModel, f: SampleForm, editable: Boolean) {
    val lots by vm.lots.collectAsStateWithLifecycle()
    val s by vm.sample.collectAsStateWithLifecycle()
    val project by vm.projectName.collectAsStateWithLifecycle()
    SectionCard("Encabezado", Icons.Default.Badge) {
        KeyValue("Proyecto", project)
        TextInput("Localización", f.location, { v -> vm.update { it.copy(location = v) } }, enabled = editable)
        TextInput("Procedencia", f.origin, { v -> vm.update { it.copy(origin = v) } }, enabled = editable)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(f.prInicial, { v -> vm.update { it.copy(prInicial = v) } }, label = { Text("PR inicial") }, enabled = editable, singleLine = true, modifier = Modifier.weight(1f))
            OutlinedTextField(f.prFinal, { v -> vm.update { it.copy(prFinal = v) } }, label = { Text("PR final") }, enabled = editable, singleLine = true, modifier = Modifier.weight(1f))
        }
        TextInput("Lado", f.lado, { v -> vm.update { it.copy(lado = v) } }, enabled = editable)
        val dateOk = f.testDateTime.isNullOrBlank() || runCatching { java.time.LocalDate.parse(f.testDateTime!!.take(10)) }.isSuccess
        TextInput("Fecha (AAAA-MM-DD)", f.testDateTime?.take(10) ?: "", { v -> vm.update { it.copy(testDateTime = v.ifBlank { null }) } }, enabled = editable,
            supporting = if (dateOk) f.testDateTime?.let { Clock.displayDate(it) } else "Fecha no válida")
        TextInput("Parte del relleno", f.fillPart, { v -> vm.update { it.copy(fillPart = v) } }, enabled = editable)
        Dropdown("Lote", lots, lots.firstOrNull { it.id == s?.lotId }, { it.code }, { vm.assignLot(it?.id) })
        TextInput("Registro", f.registro, { v -> vm.update { it.copy(registro = v) } }, enabled = editable)
    }
}

/** Columna de la densidad: DENSIDAD No., CAPA No., ESPESOR CAPA, PR, COSTADO. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DensitySection(vm: SampleViewModel, f: SampleForm, texts: Map<String, String>, editable: Boolean) {
    val s by vm.sample.collectAsStateWithLifecycle()
    SectionCard("Densidad", Icons.Default.Place) {
        KeyValue("Densidad No.", s?.code ?: "—")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(f.layer, { v -> vm.update { it.copy(layer = v) } }, label = { Text("Capa No.") }, enabled = editable, singleLine = true, modifier = Modifier.weight(1f))
            NumberInput("Espesor capa", texts["thickness"] ?: "", f.thicknessUnit, { vm.setNumber("thickness", it) }, Modifier.weight(1f), editable)
        }
        val m = Abscisa.toMeters(f.abscisa)
        TextInput("PR *", f.abscisa, { v -> vm.update { it.copy(abscisa = v) } }, enabled = editable,
            supporting = if (f.abscisa.isBlank()) "Ej.: PR 003+150" else m?.let { "Valor numérico: ${Fmt.num(it, 2)} m" } ?: "Sin valor numérico (se conserva el texto)")
        Text("Costado", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Izquierdo", "Eje", "Derecho").forEach { o -> FilterChip(f.side == o, { if (editable) vm.update { it.copy(side = if (it.side == o) "" else o) } }, label = { Text(o) }, enabled = editable) }
        }
    }
}

/** Filas A, B, D, F, H, I, J, L del formato, en el mismo orden. */
@Composable
private fun MeasurementSection(vm: SampleViewModel, texts: Map<String, String>, editable: Boolean, r: LegacyResult?) {
    @Composable fun field(sym: String, key: String, unit: String, hint: String? = null) =
        NumberInput(InputDefs.get(sym).name, texts[key] ?: "", unit, { vm.setNumber(key, it) }, enabled = editable, symbol = sym, error = fieldError(r, sym), hint = fieldWarn(r, sym) ?: hint)
    SectionCard("Datos del ensayo", Icons.Default.Scale) {
        field("A", "a", "g")
        field("B", "b", "g", r?.value("C")?.let { "C · Peso arena total usada = ${Fmt.num(it, 1)} g" })
        field("D", "d", "g", r?.value("E")?.let { "E · Peso arena en el hueco = ${Fmt.num(it, 1)} g" })
        field("F", "f", "g/cm³", r?.value("G")?.let { "G · Volumen del hueco = ${Fmt.num(it, 2)} cm³" })
        field("H", "h", "g")
        field("I", "i", "g/cm³")
        field("J", "j", "g", r?.value("K")?.let { "K · % retenido 3/4 = ${Fmt.num(it, 2)} %" })
        field("L", "l", "%", "Digite 6 para 6 % (el Excel lo guarda como 0,06)")
    }
}

/** Filas Q y R del formato. */
@Composable
private fun LabSection(vm: SampleViewModel, texts: Map<String, String>, editable: Boolean, r: LegacyResult?) {
    SectionCard("Laboratorio", Icons.Default.Biotech) {
        NumberInput(InputDefs.get("Q").name, texts["q"] ?: "", "kg/m³", { vm.setNumber("q", it) }, enabled = editable, symbol = "Q", error = fieldError(r, "Q"))
        NumberInput("Humedad óptima", texts["r"] ?: "", "%", { vm.setNumber("r", it) }, enabled = editable, symbol = "R", error = fieldError(r, "R"), hint = "Digite 6,68 para 6,68 %")
    }
}

/** Pie del formato: N° IRREGULARIDADES >15mm, OBSERVACIONES, ELABORÓ, APROBÓ. */
@Composable
private fun FooterSection(vm: SampleViewModel, f: SampleForm, editable: Boolean) {
    val rev by vm.currentRevision.collectAsStateWithLifecycle()
    SectionCard("Observaciones y firmas", Icons.Default.Notes) {
        TextInput("N° irregularidades >15 mm", f.irregularidades, { v -> vm.update { it.copy(irregularidades = v) } }, enabled = editable)
        TextInput("Observaciones", f.observations, { v -> vm.update { it.copy(observations = v) } }, enabled = editable, singleLine = false, minLines = 3)
        TextInput("Elaboró (nombre)", f.elaboratedBy, { v -> vm.update { it.copy(elaboratedBy = v) } }, enabled = editable)
        TextInput("Aprobó (nombre)", f.aprobo, { v -> vm.update { it.copy(aprobo = v) } }, enabled = editable)
        rev?.approvedBy?.let { KeyValue("Aprobación registrada", "$it · ${Clock.display(rev!!.approvedAt!!)}") }
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
