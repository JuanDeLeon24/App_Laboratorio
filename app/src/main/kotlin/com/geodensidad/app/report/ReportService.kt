package com.geodensidad.app.report

import android.content.Context
import com.geodensidad.app.BuildConfig
import com.geodensidad.app.data.*
import com.geodensidad.app.domain.*
import com.geodensidad.core.calc.ConoArenaLegacy
import com.geodensidad.core.calc.LegacyResult
import com.geodensidad.core.criteria.LotEvaluationStatus
import com.geodensidad.core.backup.BackupArchive
import com.geodensidad.core.number.DecimalText
import com.geodensidad.core.number.Fmt
import com.geodensidad.core.photo.PhotoCaption
import com.geodensidad.core.photo.PhotoTimes
import com.geodensidad.core.report.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.coroutines.coroutineContext

data class GlobalFilter(
    val activityId: String,
    val dateFrom: String? = null,
    val dateTo: String? = null,
    val journeyIds: Set<String> = emptySet(),
    val lotIds: Set<String> = emptySet(),
    val materialIds: Set<String> = emptySet(),
    val statuses: Set<SampleStatus> = SampleStatus.entries.toSet(),
    val includePhotos: Boolean = true,
    val observations: String = "",
)

@Serializable
data class SampleReportSnapshot(val data: SampleReportData, val form: SampleForm, val result: LegacyResult, val revisionId: String, val revisionNumber: Int)

/**
 * Emisión de informes. Cada emisión guarda: PDF, instantánea JSON de los datos usados, hash SHA-256
 * y registro en la tabla reports. Reabrir un informe muestra el archivo emitido; nunca se regenera.
 */
class ReportService(
    private val context: Context, private val repo: GeoRepository, private val templates: TemplateService, private val settings: SettingsRepository,
    private val writerFactory: () -> PdfWriterTarget = { AndroidPdfWriter() },
) {
    private val db = repo.db
    private val json = Json { prettyPrint = true; encodeDefaults = true }
    private val dir get() = File(context.filesDir, "reports").apply { mkdirs() }

    fun interface Progress { fun update(message: String, fraction: Float) }

    private suspend fun template() = templates.toInfo(db.templates().active() ?: run { templates.ensureSeed(); db.templates().active()!! })

    private suspend fun nextCode(activityId: String, project: ProjectEntity, suffix: String): String {
        val n = db.reports().countByActivity(activityId) + 1
        return "INF-${project.code.ifBlank { "PRY" }}-$suffix-${n.toString().padStart(3, '0')}"
    }

    private suspend fun captionInherited(form: SampleForm) = PhotoCaption.Fields(abscisa = form.abscisa.ifBlank { null }, side = form.side.ifBlank { null }, layer = form.layer.ifBlank { null })

    private suspend fun photoBlocks(photos: List<PhotoEntity>, codeFor: suspend (PhotoEntity) -> String?, inheritedFor: suspend (PhotoEntity) -> PhotoCaption.Fields): List<PhotoBlock> {
        val coords = settings.current().includeCoordinatesInReports
        return photos.filter { it.includeInReport && !it.archived && File(it.pdfPath).exists() }.mapIndexed { i, p ->
            val times = AppJson.decodeFromString(PhotoTimes.serializer(), p.timesJson)
            val own = runCatching { AppJson.decodeFromString(PhotoCaption.Fields.serializer(), p.captionJson) }.getOrDefault(PhotoCaption.Fields())
            val c = if (coords && p.latitude != null && p.longitude != null) "%.6f, %.6f".format(java.util.Locale.US, p.latitude, p.longitude) else null
            PhotoBlock(p.pdfPath, p.pdfW, p.pdfH, PhotoCaption.lines(i + 1, times, codeFor(p), own, inheritedFor(p), c))
        }
    }

    suspend fun sampleReport(sampleId: String, progress: Progress): ReportEntity = withContext(Dispatchers.Default) {
        progress.update("Preparando datos", 0.05f)
        val b = repo.bundle(sampleId)
        val s = b.sample; val f = b.form; val r = b.result; val rev = b.revision
        val act = db.activities().get(s.activityId)!!; val proj = db.projects().get(act.projectId)!!
        val journey = db.journeys().get(s.journeyId)
        val lot = s.lotId?.let { db.lots().get(it) }
        val st = SampleStatus.of(s.status)
        val cal = rev.calibrationSnapshot?.let { AppJson.decodeFromString(CalibrationSnapshot.serializer(), it) }
        val lab = rev.labRefSnapshot?.let { AppJson.decodeFromString(LabRefSnapshot.serializer(), it) }
        val material = f.materialId?.let { db.catalogs().material(it) }
        val crit = repo.criterion(lot?.criterionId ?: act.defaultCriterionId)
        val customDefs = db.customFields().listAll(CustomFieldEntityType.SAMPLE.name).filter { it.includeInPdf && !it.archived }
        val customPairs = customDefs.mapNotNull { d -> f.custom[d.key]?.takeIf { it.isNotBlank() }?.let { d.label to CustomFieldFormat.display(d, it) } }
        val photos = photoBlocks(db.photos().listBySample(sampleId), { s.code }, { captionInherited(f) })
        val reportId = newId()
        val now = Clock.nowMillis()
        val code = nextCode(act.id, proj, "${s.code}-R${rev.number}")
        val tech = when {
            r.compactionPercent == null -> "No determinado: resultado no disponible."
            else -> "Compactación individual ${Fmt.num(r.compactionPercent, 2)} %; Di = ${Fmt.num(r.correctedDryDensity, 2)} kg/m³. " +
                (crit?.let { "Referencia individual α·100 = ${Fmt.num(it.alpha * 100, 1)} % (${if (it.validated) "criterio validado" else "criterio no validado"}): " +
                    if (r.compactionPercent!! >= it.alpha * 100) "alcanza el umbral." else "no alcanza el umbral." } ?: "Sin criterio seleccionado.") +
                " El dictamen estadístico corresponde al lote."
        }
        val data = SampleReportData(
            template = template(),
            issue = IssueInfo(reportId, code, Clock.display(now), settings.current().responsible.ifBlank { null }, proj.code.ifBlank { proj.name.take(16) },
                st == SampleStatus.DRAFT, rev.engineVersion, BuildConfig.VERSION_NAME),
            sampleCode = s.code, revisionLabel = "Revisión ${rev.number}${rev.reason?.let { " (motivo: $it)" } ?: ""}",
            documentStatus = st.label + (if (st == SampleStatus.VOID) " – ${s.voidReason}" else "") + (rev.approvedBy?.let { " por $it, ${Clock.display(rev.approvedAt!!)}" } ?: ""),
            projectPairs = listOf("Proyecto" to "${proj.code} – ${proj.name}${if (proj.isDemo) " (DEMOSTRATIVO)" else ""}", "Actividad" to act.name, "Cliente / entidad" to proj.client, "Contratista" to proj.contractor),
            journeyPairs = listOf("Jornada" to Clock.displayDate(journey?.workDate), "Responsable" to (journey?.responsible ?: ""), "Frente de trabajo" to (journey?.workFront ?: ""),
                "Fecha y hora del ensayo" to (f.testDateTime?.replace('T', ' ') ?: "—")),
            locationPairs = listOf("Abscisa" to f.abscisa, "Costado" to f.side, "Capa" to f.layer, "Espesor" to (f.thickness?.let { "${it.replace('.', ',')} ${f.thicknessUnit}" } ?: ""),
                "Parte del relleno" to f.fillPart, "Procedencia" to f.origin, "Localización" to f.location, "Material" to (material?.name ?: ""), "Lote" to (lot?.code ?: "Sin lote")),
            measurementRows = ReportRows.measurementRows(f.toInputs()),
            resultRows = ReportRows.resultRows(r),
            calibrationPairs = listOf("Calibración" to (cal?.code ?: "Sin calibración de catálogo"), "Equipo" to (cal?.equipment ?: ""), "Fecha" to (cal?.date ?: ""),
                "Vigencia" to (cal?.validUntil ?: ""), "Constante del cono (D) usada" to Fmt.withUnit(DecimalText.storedToDouble(f.d), 1, "g"),
                "Densidad de la arena (F) usada" to Fmt.withUnit(DecimalText.storedToDouble(f.f), 3, "g/cm³")),
            labRefPairs = listOf("Referencia" to (lab?.code ?: "Sin referencia de catálogo"), "Ensayo" to (lab?.testId ?: f.labTestId)) + ReportRows.labRows(f.toInputs()),
            criterionLines = listOf(crit?.let { "Criterio de lote: ${it.code} v${it.version} – ${it.expression}. " + (if (it.validated) "Criterio validado (${it.sourceDocument ?: ""})." else "Evaluación provisional; criterio no validado.") } ?: "Sin criterio de evaluación seleccionado.",
                "Perfil de cálculo: ${r.profile.id}; wc = ${Fmt.num(r.profile.wc, 2)} (${if (r.profile.validated) "validado" else "supuesto pendiente de validación técnica"})."),
            technicalStatus = tech,
            warnings = r.issues.map { "${if (it.severity.name == "BLOCKING") "Error" else "Advertencia"} (${it.field}): ${it.message}" } +
                listOf("La reproducción aritmética del Excel no certifica la validez normativa del método.") +
                (if (proj.isDemo) listOf("DATOS DEMOSTRATIVOS: no corresponden a un ensayo real.") else emptyList()),
            observations = f.observations, customPairs = customPairs,
            elaboratedBy = f.elaboratedBy.ifBlank { null }, approvedBy = rev.approvedBy,
            photos = photos, photosPerPage = settings.current().photosPerPage,
        )
        val entity = write(reportId, code, "SAMPLE", act.id, s.id, rev.id, now, data.issue, rev.engineVersion, "Muestra ${s.code}, revisión ${rev.number}",
            { m -> ReportRenderer.sample(data, m) }, json.encodeToString(SampleReportSnapshot.serializer(), SampleReportSnapshot(data, f, r, rev.id, rev.number)), progress)
        repo.audit("SAMPLE", s.id, "Emisión de informe", "${entity.code} (revisión ${rev.number}, ${if (entity.draft) "BORRADOR" else st.label}); SHA-256 ${entity.sha256}")
        entity
    }

    suspend fun records(filter: GlobalFilter): List<Pair<SampleEntity, SampleRecord>> {
        val samples = db.samples().listByActivity(filter.activityId).filter { s ->
            val date = s.testDateTime?.take(10)
            (filter.dateFrom == null || (date != null && date >= filter.dateFrom)) &&
                (filter.dateTo == null || (date != null && date <= filter.dateTo)) &&
                (filter.journeyIds.isEmpty() || s.journeyId in filter.journeyIds) &&
                (filter.lotIds.isEmpty() || s.lotId in filter.lotIds) &&
                (filter.materialIds.isEmpty() || s.materialId in filter.materialIds) &&
                SampleStatus.of(s.status) in filter.statuses
        }
        val mats = HashMap<String, String>(); val lots = db.lots().listByActivity(filter.activityId).associateBy { it.id }
        val labs = HashMap<String, String>()
        val tableDefs = db.customFields().listAll(CustomFieldEntityType.SAMPLE.name).filter { it.includeInTable && !it.archived }
        return samples.map { s ->
            val st = SampleStatus.of(s.status)
            val extras = if (tableDefs.isEmpty()) emptyList() else {
                val form = repo.decodeForm(db.revisions().get(s.currentRevisionId)!!)
                tableDefs.map { d -> form.custom[d.key]?.let { CustomFieldFormat.display(d, it) } ?: "—" }
            }
            s to SampleRecord(
                id = s.id, code = s.code, sequence = s.seq, dateText = Clock.displayDate(s.testDateTime), abscisaText = s.abscisaText, abscisaMeters = s.abscisaMeters,
                side = s.side, layer = s.layer, material = s.materialId?.let { id -> mats.getOrPut(id) { db.catalogs().material(id)?.name ?: "?" } },
                lotCode = s.lotId?.let { lots[it]?.code }, statusLabel = st.label, eligible = st.eligibleForCalc && s.cacheBlocking == 0,
                p = s.cacheP, s = s.cacheS, moisture = s.cacheL, optimumMoisture = s.cacheR, de = s.cacheQ,
                labRefKey = s.labRefId?.let { id -> labs.getOrPut(id) { db.catalogs().labRef(id)?.code ?: "?" } } ?: s.cacheQ?.let { "De ${Fmt.num(it, 1)}" },
                revision = s.revisionNumber, extraColumns = extras,
            )
        }
    }

    suspend fun globalReport(filter: GlobalFilter, progress: Progress): ReportEntity = withContext(Dispatchers.Default) {
        progress.update("Preparando datos", 0.03f)
        val act = db.activities().get(filter.activityId)!!; val proj = db.projects().get(act.projectId)!!
        val pairs = records(filter)
        val recs = pairs.map { it.second }
        val crit = repo.criterion(act.defaultCriterionId)
        coroutineContext.ensureActive()
        progress.update("Evaluando lotes", 0.1f)
        val lotIds = pairs.mapNotNull { it.first.lotId }.distinct()
        val lots = lotIds.mapNotNull { db.lots().get(it) }.sortedBy { it.code }.map { lot ->
            val e = repo.evaluateLot(lot.id)
            val lc = repo.criterion(lot.criterionId)
            LotBlock("Lote ${lot.code}${lot.description.takeIf { it.isNotBlank() }?.let { " – $it" } ?: ""}", listOf(
                "Registradas / incluidas / excluidas / pendientes" to "${e.registered} / ${e.included} / ${e.excluded} / ${e.pending}",
                "Criterio" to (lc?.let { "${it.code} v${it.version}: ${it.expression}" } ?: "Sin criterio"),
                "Dm (media de Di, n = ${e.stats.n})" to Fmt.withUnit(e.stats.mean, 2, "kg/m³"),
                "s (desviación estándar muestral)" to (e.stats.sampleSd?.let { Fmt.withUnit(it, 2, "kg/m³") } ?: "No disponible"),
                "k(n)" to (e.k?.let { Fmt.num(it, 3) } ?: "No definido para n = ${e.stats.n}"),
                "De" to Fmt.withUnit(e.de, 1, "kg/m³"),
                "Dm − k·s" to Fmt.withUnit(e.leftSide, 2, "kg/m³"), "α·De" to Fmt.withUnit(e.rightSide, 2, "kg/m³"),
                "Compactación media del lote" to Fmt.withUnit(e.compactionStats.mean, 2, "%"),
                "Di individuales ≥ α·De" to (e.individualAboveThreshold?.let { "$it de ${e.included} (indicador descriptivo; no es aceptación del lote)" } ?: "No aplica"),
            ) + e.excludedDetail.map { "Excluida ${it.first}" to it.second },
                e.status.label, when (e.status) { LotEvaluationStatus.MEETS, LotEvaluationStatus.PROVISIONAL_MEETS -> true; LotEvaluationStatus.FAILS, LotEvaluationStatus.PROVISIONAL_FAILS -> false; else -> null },
                e.reasons + (if (pairs.count { it.first.lotId == lot.id } < db.samples().listByLot(lot.id).size) listOf("El lote se evalúa completo aunque el filtro muestre solo parte de sus muestras.") else emptyList()))
        }
        val inc = recs.filter { it.included }
        val revs = pairs.mapNotNull { db.revisions().get(it.first.currentRevisionId) }
        val cals = revs.mapNotNull { it.calibrationSnapshot }.distinct().map { AppJson.decodeFromString(CalibrationSnapshot.serializer(), it) }.distinctBy { it.id to it.coneConstantG to it.sandDensity }
        val labsUsed = revs.mapNotNull { it.labRefSnapshot }.distinct().map { AppJson.decodeFromString(LabRefSnapshot.serializer(), it) }.distinctBy { it.id to it.maxDryDensity to it.optimumMoisture }
        val journeys = db.journeys().listByActivity(act.id).associateBy { it.id }
        val photos = if (!filter.includePhotos) emptyList() else {
            val ids = pairs.map { it.first.id }.toSet()
            val bySample = pairs.associate { it.first.id to it.first }
            val forms = HashMap<String, SampleForm>()
            photoBlocks(db.photos().listByActivity(act.id).filter { it.sampleId in ids }.sortedWith(compareBy({ bySample[it.sampleId]?.seq ?: 0 }, { it.sortOrder })),
                { p -> bySample[p.sampleId]?.code }, { p -> captionInherited(forms.getOrPut(p.sampleId!!) { repo.decodeForm(db.revisions().get(bySample[p.sampleId]!!.currentRevisionId)!!) }) })
        }
        val tableDefs = db.customFields().listAll(CustomFieldEntityType.SAMPLE.name).filter { it.includeInTable && !it.archived }
        val reportId = newId(); val now = Clock.nowMillis()
        val code = nextCode(act.id, proj, "GLOBAL")
        val scopeFilters = buildList {
            filter.dateFrom?.let { add("Desde ${Clock.displayDate(it)}") }; filter.dateTo?.let { add("Hasta ${Clock.displayDate(it)}") }
            if (filter.journeyIds.isNotEmpty()) add("Jornadas: " + filter.journeyIds.mapNotNull { journeys[it]?.workDate?.let(Clock::displayDate) }.joinToString())
            if (filter.lotIds.isNotEmpty()) add("Lotes: " + filter.lotIds.mapNotNull { db.lots().get(it)?.code }.joinToString())
            if (filter.materialIds.isNotEmpty()) add("Materiales: " + filter.materialIds.mapNotNull { db.catalogs().material(it)?.name }.joinToString())
            if (filter.statuses.size < SampleStatus.entries.size) add("Estados: " + filter.statuses.joinToString { it.label })
        }.ifEmpty { listOf("Actividad completa, sin filtros") }.joinToString("; ")
        val data = GlobalReportData(
            template = template(),
            issue = IssueInfo(reportId, code, Clock.display(now), settings.current().responsible.ifBlank { null }, proj.code.ifBlank { proj.name.take(16) }, false, ConoArenaLegacy.ENGINE_FULL_ID, BuildConfig.VERSION_NAME),
            title = "Informe global – ${act.name}${if (proj.isDemo) " (DATOS DEMOSTRATIVOS)" else ""}",
            scopePairs = listOf("Proyecto" to "${proj.code} – ${proj.name}", "Actividad" to act.name, "Cliente / entidad" to proj.client, "Contratista" to proj.contractor, "Filtros aplicados" to scopeFilters, "Muestras en el alcance" to recs.size.toString()),
            executiveSummary = buildList {
                add("${inc.size} de ${recs.size} muestras del alcance se incluyen en los cálculos; ${recs.size - inc.size} no se incluyen (ver sección 8).")
                if (inc.isNotEmpty()) add("Compactación de las incluidas: media ${Fmt.num(inc.map { it.s!! }.average(), 2)} %, mínima ${Fmt.num(inc.minOf { it.s!! }, 2)} %, máxima ${Fmt.num(inc.maxOf { it.s!! }, 2)} % (sin mezclar con estadísticos de lote).")
                add("Grupos compatibles (material y referencia de laboratorio): ${GlobalAssembler.groups(recs).size}.")
                lots.forEach { add("${it.title}: ${it.statusText}") }
            },
            coveragePairs = GlobalAssembler.coverage(recs),
            sampleColumns = GlobalAssembler.baseColumns + tableDefs.map { it.label + (it.unit.takeIf { u -> u.isNotBlank() }?.let { u -> " ($u)" } ?: "") },
            sampleRows = GlobalAssembler.rows(recs), statGroups = GlobalAssembler.groups(recs), charts = GlobalAssembler.charts(recs, crit), lots = lots,
            notIncludedRows = GlobalAssembler.notIncluded(recs),
            warnings = buildList {
                add("La reproducción aritmética del Excel no certifica la validez normativa del método. wc = 0,02 y el parámetro I son supuestos heredados pendientes de validación técnica.")
                if (crit == null || !crit.validated) add("Las evaluaciones de lote son PROVISIONALES: el criterio no está validado. No constituyen certificación de conformidad.")
                add("Los estadísticos se calculan por grupos compatibles; no se mezclan materiales ni referencias de laboratorio distintas.")
                if (proj.isDemo) add("DATOS DEMOSTRATIVOS: no corresponden a ensayos reales.")
            },
            observations = filter.observations,
            calibrationRows = cals.map { listOf(it.code, it.equipment, it.date, Fmt.num(DecimalText.storedToDouble(it.coneConstantG), 1), Fmt.num(DecimalText.storedToDouble(it.sandDensity), 3)) },
            labRefRows = labsUsed.map { listOf(it.code, it.material ?: "", it.testId, Fmt.num(DecimalText.storedToDouble(it.maxDryDensity), 1), it.optimumMoisture?.let { o -> Fmt.num(DecimalText.storedToDouble(o)!! * 100, 2) } ?: "—") },
            photos = photos, photosPerPage = settings.current().photosPerPage,
        )
        val entity = write(reportId, code, "GLOBAL", act.id, null, null, now, data.issue, ConoArenaLegacy.ENGINE_FULL_ID, scopeFilters,
            { m -> ReportRenderer.global(data, m) }, json.encodeToString(GlobalReportData.serializer(), data), progress)
        repo.audit("ACTIVITY", act.id, "Emisión de informe global", "${entity.code}; ${recs.size} muestras; SHA-256 ${entity.sha256}")
        entity
    }

    private suspend fun write(id: String, code: String, type: String, activityId: String, sampleId: String?, revisionId: String?, now: Long, issue: IssueInfo, engine: String, scope: String,
                              build: (TextMeasurer) -> PageList, snapshot: String, progress: Progress): ReportEntity {
        if (context.filesDir.usableSpace < 30L * 1024 * 1024) throw DomainException("Almacenamiento insuficiente para generar el informe")
        val pdf = File(dir, "$id.pdf"); val tmp = File(dir, "$id.pdf.part"); val snap = File(dir, "$id.json")
        val writer = writerFactory()
        val job = coroutineContext[kotlinx.coroutines.Job]
        try {
            progress.update("Componiendo páginas", 0.2f)
            val pages = build(writer)
            pages.render(writer) { i, n ->
                job?.ensureActive()
                progress.update("Página $i de $n", 0.2f + 0.7f * i / n)
            }
            progress.update("Guardando", 0.95f)
            tmp.outputStream().use { writer.writeTo(it) }
            if (!tmp.renameTo(pdf)) throw DomainException("No se pudo guardar el PDF")
            snap.writeText(snapshot)
            val t = db.templates().active()!!
            val e = ReportEntity(id, code, type, activityId, sampleId, revisionId, now, issue.issuedAtText, issue.issuedBy, issue.draft, t.version, engine, pdf.path, snap.path, BackupArchive.sha256(pdf), pages.pages.size, scope)
            db.reports().insert(e)
            progress.update("Listo", 1f)
            return e
        } catch (e: Throwable) {
            tmp.delete(); pdf.delete(); snap.delete(); throw e
        } finally { writer.close() }
    }
}

object CustomFieldFormat {
    fun display(d: CustomFieldEntity, raw: String): String = when (CustomFieldType.valueOf(d.type)) {
        CustomFieldType.NUMBER -> (DecimalText.storedToDouble(raw)?.let { Fmt.num(it, 4).trimEnd('0').trimEnd(',') } ?: raw) + (if (d.unit.isNotBlank()) " ${d.unit}" else "")
        CustomFieldType.PERCENT -> (DecimalText.storedToDouble(raw)?.let { Fmt.num(it * 100, 2) } ?: raw) + " %"
        CustomFieldType.BOOLEAN -> if (raw == "true") "Sí" else "No"
        CustomFieldType.DATE -> Clock.displayDate(raw)
        else -> raw + (if (d.unit.isNotBlank() && d.type == CustomFieldType.TEXT.name) "" else "")
    }
}
