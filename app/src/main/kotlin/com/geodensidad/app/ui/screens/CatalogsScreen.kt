package com.geodensidad.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.geodensidad.app.AppContainer
import com.geodensidad.app.data.*
import com.geodensidad.app.domain.AppJson
import com.geodensidad.app.domain.Clock
import com.geodensidad.app.domain.newId
import com.geodensidad.app.ui.appViewModel
import com.geodensidad.app.ui.components.*
import com.geodensidad.app.ui.theme.Condor
import com.geodensidad.core.criteria.StatisticalCriterion
import com.geodensidad.core.number.DecimalText
import com.geodensidad.core.number.Fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class CatalogsViewModel(private val c: AppContainer) : ViewModel() {
    private fun <T> kotlinx.coroutines.flow.Flow<List<T>>.st() = stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val materials = c.db.catalogs().materials().st()
    val equipment = c.db.catalogs().equipment().st()
    val calibrations = c.db.catalogs().calibrations().st()
    val labRefs = c.db.catalogs().labRefs().st()
    val people = c.db.catalogs().people().st()
    val criteria = c.db.catalogs().criteria().st()
    val message = MutableStateFlow<String?>(null)
    fun run(b: suspend () -> Unit) = viewModelScope.launch { runCatching { b() }.onSuccess { message.value = "Guardado" }.onFailure { message.value = it.message } }
    fun saveMaterial(m: MaterialEntity) = run { c.repo.saveMaterial(m) }
    fun saveEquipment(e: EquipmentEntity) = run { c.repo.saveEquipment(e) }
    fun saveCalibration(x: CalibrationEntity) = run { c.repo.saveCalibration(x) }
    fun saveLabRef(l: LabReferenceEntity, doc: Uri?) = run {
        var x = l
        if (doc != null) withContext(Dispatchers.IO) {
            val dir = File(c.context.filesDir, "labdocs").apply { mkdirs() }
            val f = File(dir, "${l.id}-${Clock.nowMillis()}")
            c.context.contentResolver.openInputStream(doc)?.use { i -> f.outputStream().use { i.copyTo(it) } }
            x = l.copy(documentPath = f.path)
        }
        c.repo.saveLabRef(x)
    }
    fun savePerson(p: PersonEntity) = run { c.repo.savePerson(p) }
    fun saveCriterion(cr: StatisticalCriterion, archived: Boolean) = run { c.repo.saveCriterion(cr, archived) }
}

@Composable
fun CatalogsScreen(back: () -> Unit) {
    val vm = appViewModel { c, _ -> CatalogsViewModel(c) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<Any?>(null) }
    var creating by remember { mutableStateOf(false) }
    val snack = remember { SnackbarHostState() }
    ErrorSnack(vm.message.collectAsStateWithLifecycle().value, { vm.message.value = null }, snack)
    val tabs = listOf("Materiales", "Equipos", "Calibraciones", "Ref. laboratorio", "Responsables", "Criterios")
    val materials by vm.materials.collectAsStateWithLifecycle(); val equipment by vm.equipment.collectAsStateWithLifecycle()
    val cals by vm.calibrations.collectAsStateWithLifecycle(); val labs by vm.labRefs.collectAsStateWithLifecycle()
    val people by vm.people.collectAsStateWithLifecycle(); val crits by vm.criteria.collectAsStateWithLifecycle()
    Scaffold(topBar = { Column { AppBar("Catálogos", "Los cambios no alteran muestras ya registradas", back); ScrollableTabRow(tab, edgePadding = 8.dp) { tabs.forEachIndexed { i, t -> Tab(tab == i, { tab = i }, text = { Text(t) }) } } } },
        floatingActionButton = { LabeledFab("Agregar", Icons.Default.Add, { creating = true }) },
        snackbarHost = { SnackbarHost(snack) }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
            item { Banner("Los registros no se eliminan: se archivan. Cada muestra guarda una instantánea de la calibración y la referencia usadas.", Condor.Steel) }
            fun row(title: String, sub: String, archived: Boolean, item: Any) = @Composable {
                ListItem(headlineContent = { Text(title) }, supportingContent = { Text(sub) }, trailingContent = { if (archived) Pill("Archivado", Icons.Default.Archive, Condor.Slate) },
                    modifier = Modifier.clickable { editing = item }); HorizontalDivider()
            }
            when (tab) {
                0 -> items(materials, key = { it.id }) { row("${it.code} – ${it.name}", it.description, it.archived, it)() }
                1 -> items(equipment, key = { it.id }) { row("${it.code} – ${it.name}", "Serie: ${it.serial.ifBlank { "—" }}", it.archived, it)() }
                2 -> items(cals, key = { it.id }) { c -> row("${c.code} · ${equipment.firstOrNull { it.id == c.equipmentId }?.code ?: ""}", "Fecha ${Clock.displayDate(c.date)}${c.validUntil?.let { " · vigente hasta ${Clock.displayDate(it)}" } ?: ""} · D = ${c.coneConstantG.replace('.', ',')} g · F = ${c.sandDensity.replace('.', ',')} g/cm³", c.archived, c)() }
                3 -> items(labs, key = { it.id }) { l -> row(l.code, "De = ${Fmt.num(DecimalText.storedToDouble(l.maxDryDensity), 1)} kg/m³ · W opt = ${l.optimumMoisture?.let { Fmt.fractionAsPercent(DecimalText.storedToDouble(it)) } ?: "—"} · ${l.testId}${if (l.documentPath != null) " · documento adjunto" else ""}", l.archived, l)() }
                4 -> items(people, key = { it.id }) { row(it.name, it.role, it.archived, it)() }
                5 -> items(crits, key = { it.id }) { c -> val cr = AppJson.decodeFromString(StatisticalCriterion.serializer(), c.json)
                    row("${cr.code} v${cr.version}", "${cr.expression} · ${if (cr.validated) "VALIDADO" else "no validado"}${if (cr.legacy) " · heredado" else ""}", c.archived, c)() }
            }
        }
    }
    val target = editing ?: if (creating) tab else null
    if (target != null) {
        val close = { editing = null; creating = false }
        when {
            target is MaterialEntity || (target == 0 && creating) -> MaterialDialog(target as? MaterialEntity, close) { vm.saveMaterial(it); close() }
            target is EquipmentEntity || (target == 1 && creating) -> EquipmentDialog(target as? EquipmentEntity, close) { vm.saveEquipment(it); close() }
            target is CalibrationEntity || (target == 2 && creating) -> CalibrationDialog(target as? CalibrationEntity, equipment, close) { vm.saveCalibration(it); close() }
            target is LabReferenceEntity || (target == 3 && creating) -> LabRefDialog(target as? LabReferenceEntity, materials, close) { l, d -> vm.saveLabRef(l, d); close() }
            target is PersonEntity || (target == 4 && creating) -> PersonDialog(target as? PersonEntity, close) { vm.savePerson(it); close() }
            target is CriterionEntity || (target == 5 && creating) -> CriterionDialog(target as? CriterionEntity, close) { c, a -> vm.saveCriterion(c, a); close() }
        }
    }
}

@Composable private fun ArchiveSwitch(v: Boolean, on: (Boolean) -> Unit) = Row(verticalAlignment = Alignment.CenterVertically) { Switch(v, on); Spacer(Modifier.width(8.dp)); Text("Archivado") }

@Composable
private fun MaterialDialog(m: MaterialEntity?, dismiss: () -> Unit, save: (MaterialEntity) -> Unit) {
    var code by remember { mutableStateOf(m?.code ?: "") }; var name by remember { mutableStateOf(m?.name ?: "") }
    var desc by remember { mutableStateOf(m?.description ?: "") }; var arch by remember { mutableStateOf(m?.archived ?: false) }
    FormDialog("Material", dismiss, { save(MaterialEntity(m?.id ?: newId(), code, name, desc, arch, Clock.nowMillis())) }, name.isNotBlank()) {
        TextInput("Código", code, { code = it }); TextInput("Nombre *", name, { name = it }); TextInput("Descripción", desc, { desc = it }); ArchiveSwitch(arch) { arch = it }
    }
}

@Composable
private fun EquipmentDialog(e: EquipmentEntity?, dismiss: () -> Unit, save: (EquipmentEntity) -> Unit) {
    var code by remember { mutableStateOf(e?.code ?: "") }; var name by remember { mutableStateOf(e?.name ?: "Cono de arena") }
    var serial by remember { mutableStateOf(e?.serial ?: "") }; var arch by remember { mutableStateOf(e?.archived ?: false) }
    FormDialog("Equipo", dismiss, { save(EquipmentEntity(e?.id ?: newId(), code, name, serial, arch, Clock.nowMillis())) }, code.isNotBlank()) {
        TextInput("Código *", code, { code = it }); TextInput("Nombre", name, { name = it }); TextInput("Número de serie", serial, { serial = it }); ArchiveSwitch(arch) { arch = it }
    }
}

@Composable
private fun CalibrationDialog(c: CalibrationEntity?, equipment: List<EquipmentEntity>, dismiss: () -> Unit, save: (CalibrationEntity) -> Unit) {
    var code by remember { mutableStateOf(c?.code ?: "") }; var eq by remember { mutableStateOf(c?.equipmentId) }
    var date by remember { mutableStateOf(c?.date ?: java.time.LocalDate.now().toString()) }; var valid by remember { mutableStateOf(c?.validUntil ?: "") }
    var d by remember { mutableStateOf(DecimalText.storedToEditText(c?.coneConstantG)) }; var f by remember { mutableStateOf(DecimalText.storedToEditText(c?.sandDensity)) }
    var notes by remember { mutableStateOf(c?.notes ?: "") }; var arch by remember { mutableStateOf(c?.archived ?: false) }
    val dc = DecimalText.toCanonicalOrNull(d); val fc = DecimalText.toCanonicalOrNull(f)
    FormDialog("Calibración", dismiss, { save(CalibrationEntity(c?.id ?: newId(), code, eq!!, date, valid.ifBlank { null }, dc!!, fc!!, notes, arch, Clock.nowMillis())) },
        code.isNotBlank() && eq != null && dc != null && fc != null && runCatching { java.time.LocalDate.parse(date) }.isSuccess) {
        if (equipment.isEmpty()) Text("Primero registre un equipo en la pestaña Equipos.", color = Condor.Bad)
        TextInput("Identificación *", code, { code = it })
        Dropdown("Equipo *", equipment.filter { !it.archived }, equipment.firstOrNull { it.id == eq }, { "${it.code} – ${it.name}" }, { eq = it?.id }, allowNone = false)
        TextInput("Fecha (AAAA-MM-DD) *", date, { date = it }); TextInput("Vigente hasta (opcional)", valid, { valid = it })
        NumberInput("Constante del cono", d, "g", { d = it }, symbol = "D"); NumberInput("Densidad de la arena", f, "g/cm³", { f = it }, symbol = "F")
        TextInput("Notas", notes, { notes = it }); ArchiveSwitch(arch) { arch = it }
        if (c != null) Text("Modificar una calibración no cambia las muestras que ya la usan: conservan sus valores y su instantánea.", style = MaterialTheme.typography.bodySmall, color = Condor.Steel)
    }
}

@Composable
private fun LabRefDialog(l: LabReferenceEntity?, materials: List<MaterialEntity>, dismiss: () -> Unit, save: (LabReferenceEntity, Uri?) -> Unit) {
    var code by remember { mutableStateOf(l?.code ?: "") }; var mat by remember { mutableStateOf(l?.materialId) }
    var test by remember { mutableStateOf(l?.testId ?: "") }; var q by remember { mutableStateOf(DecimalText.storedToEditText(l?.maxDryDensity)) }
    var r by remember { mutableStateOf(DecimalText.fractionToPercentText(l?.optimumMoisture)) }; var notes by remember { mutableStateOf(l?.notes ?: "") }
    var arch by remember { mutableStateOf(l?.archived ?: false) }; var doc by remember { mutableStateOf<Uri?>(null) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { doc = it }
    val qc = DecimalText.toCanonicalOrNull(q)
    val rc = (DecimalText.percentTextToFraction(r) as? com.geodensidad.core.number.ParsedDecimal.Valid)?.let { DecimalText.canonical(it.value) }
    FormDialog("Referencia de laboratorio", dismiss, { save(LabReferenceEntity(l?.id ?: newId(), code, mat, test, qc!!, "kg/m³", rc, l?.documentPath, notes, arch, Clock.nowMillis()), doc) }, code.isNotBlank() && qc != null) {
        TextInput("Identificación *", code, { code = it }); TextInput("Ensayo (p. ej. Proctor modificado No. …)", test, { test = it })
        Dropdown("Material", materials.filter { !it.archived }, materials.firstOrNull { it.id == mat }, { it.name }, { mat = it?.id })
        NumberInput("Densidad seca máxima", q, "kg/m³", { q = it }, symbol = "Q"); NumberInput("Humedad óptima (en porcentaje)", r, "%", { r = it }, symbol = "R", hint = "Digite 6,68 para 6,68 %")
        TextInput("Notas", notes, { notes = it })
        OutlinedButton(onClick = { pick.launch(arrayOf("application/pdf", "image/*")) }) { Icon(Icons.Default.AttachFile, null); Text(if (doc != null) " Documento seleccionado" else if (l?.documentPath != null) " Reemplazar documento adjunto" else " Adjuntar documento (opcional)") }
        ArchiveSwitch(arch) { arch = it }
    }
}

@Composable
private fun PersonDialog(p: PersonEntity?, dismiss: () -> Unit, save: (PersonEntity) -> Unit) {
    var name by remember { mutableStateOf(p?.name ?: "") }; var role by remember { mutableStateOf(p?.role ?: "") }; var arch by remember { mutableStateOf(p?.archived ?: false) }
    FormDialog("Responsable local", dismiss, { save(PersonEntity(p?.id ?: newId(), name, role, arch, Clock.nowMillis())) }, name.isNotBlank()) {
        TextInput("Nombre *", name, { name = it }); TextInput("Cargo / rol", role, { role = it }); ArchiveSwitch(arch) { arch = it }
        Text("Atribución operativa: no es una cuenta ni una firma electrónica.", style = MaterialTheme.typography.bodySmall, color = Condor.Steel)
    }
}

@Composable
private fun CriterionDialog(e: CriterionEntity?, dismiss: () -> Unit, save: (StatisticalCriterion, Boolean) -> Unit) {
    val base = e?.let { AppJson.decodeFromString(StatisticalCriterion.serializer(), it.json) }
    var code by remember { mutableStateOf(base?.code ?: "") }; var version by remember { mutableStateOf(base?.version ?: "1.0.0") }
    var name by remember { mutableStateOf(base?.name ?: "") }; var alpha by remember { mutableStateOf(base?.alpha?.toString()?.replace('.', ',') ?: "") }
    var table by remember { mutableStateOf(base?.kTable?.toSortedMap()?.entries?.joinToString("; ") { "${it.key}=${it.value.toString().replace('.', ',')}" } ?: "") }
    var source by remember { mutableStateOf(base?.sourceDocument ?: "") }; var edition by remember { mutableStateOf(base?.edition ?: "") }
    var article by remember { mutableStateOf(base?.article ?: "") }; var reviewer by remember { mutableStateOf(base?.reviewedBy ?: "") }
    var enabledAt by remember { mutableStateOf(base?.enabledAt ?: "") }; var validated by remember { mutableStateOf(base?.validated ?: false) }
    var arch by remember { mutableStateOf(e?.archived ?: false) }
    val kTable = runCatching { table.split(';').filter { it.isNotBlank() }.associate { p -> val (n, k) = p.split('='); n.trim().toInt() to DecimalText.toCanonicalOrNull(k.trim())!!.toDouble() } }.getOrNull()
    val a = DecimalText.toCanonicalOrNull(alpha)?.toDouble()
    val canValidate = source.isNotBlank() && reviewer.isNotBlank() && enabledAt.isNotBlank()
    FormDialog("Criterio de evaluación", dismiss, {
        save(StatisticalCriterion(base?.id?.takeIf { base.version == version } ?: newId(), code, version, name, kTable!!, a!!, base?.legacy == true && base.version == version, validated && canValidate,
            source.ifBlank { null }, edition.ifBlank { null }, article.ifBlank { null }, reviewer.ifBlank { null }, enabledAt.ifBlank { null }, base?.notes), arch)
    }, code.isNotBlank() && kTable != null && kTable.isNotEmpty() && a != null && a > 0) {
        Text("Esquema: Dm − k(n)·s ≥ α·De. Cambiar coeficientes exige una nueva versión. Sin interpolación: k se aplica solo a los n listados.", style = MaterialTheme.typography.bodySmall)
        TextInput("Código *", code, { code = it }); TextInput("Versión *", version, { version = it }); TextInput("Nombre", name, { name = it })
        NumberInput("Factor α", alpha, "", { alpha = it }, symbol = "α")
        TextInput("Tabla k(n) (ej. 5=0,685; 6=0,602)", table, { table = it }, singleLine = false, supporting = if (kTable == null) "Formato inválido" else "${kTable.size} coeficientes")
        TextInput("Fuente documental", source, { source = it }); TextInput("Edición", edition, { edition = it }); TextInput("Artículo", article, { article = it })
        TextInput("Responsable de la revisión", reviewer, { reviewer = it }); TextInput("Fecha de habilitación (AAAA-MM-DD)", enabledAt, { enabledAt = it })
        Row(verticalAlignment = Alignment.CenterVertically) { Switch(validated, { validated = it }, enabled = canValidate); Spacer(Modifier.width(8.dp)); Text("Validado técnicamente") }
        if (!canValidate) Text("Para marcarlo como validado registre fuente, responsable y fecha de habilitación.", style = MaterialTheme.typography.bodySmall, color = Condor.Warn)
        ArchiveSwitch(arch) { arch = it }
    }
}
