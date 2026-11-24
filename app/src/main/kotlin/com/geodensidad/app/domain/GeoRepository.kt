package com.geodensidad.app.domain

import androidx.room.withTransaction
import com.geodensidad.app.data.*
import com.geodensidad.core.calc.ConoArenaLegacy
import com.geodensidad.core.calc.LegacyResult
import com.geodensidad.core.criteria.LotEvaluation
import com.geodensidad.core.criteria.LotEvaluationStatus
import com.geodensidad.core.criteria.LotEvaluator
import com.geodensidad.core.criteria.LotObservation
import com.geodensidad.core.criteria.StatisticalCriterion
import com.geodensidad.core.location.Abscisa
import com.geodensidad.core.number.DecimalText
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class DomainException(message: String) : Exception(message)

/**
 * Repositorio principal: proyectos, actividades, jornadas, muestras, revisiones, lotes y catálogos.
 * Toda operación que modifica varias tablas se ejecuta en una transacción e incluye su evento de auditoría.
 */
class GeoRepository(val db: AppDatabase, private val settings: SettingsRepository) {

    private suspend fun actor() = settings.current().responsible.ifBlank { "Usuario local (sin nombre)" }

    suspend fun audit(type: String, id: String, action: String, detail: String = "", actor: String? = null) {
        db.audit().insert(AuditEntity(newId(), Clock.nowMillis(), actor ?: actor(), type, id, action, detail))
    }

    // ---------------- Semilla de configuración (no son datos de ejemplo) ----------------
    suspend fun ensureCriteriaSeed() {
        if (db.catalogs().criteriaCount() == 0) {
            val c = StatisticalCriterion.LEGACY
            db.catalogs().upsertCriterion(CriterionEntity(c.id, c.code, c.version, AppJson.encodeToString(StatisticalCriterion.serializer(), c), c.validated, updatedAt = Clock.nowMillis()))
        }
    }

    // ---------------- Proyectos ----------------
    suspend fun createProject(code: String, name: String, client: String = "", contractor: String = "", location: String = "", description: String = "", responsibles: String = "", isDemo: Boolean = false): String {
        require(name.isNotBlank()) { "El nombre del proyecto es obligatorio" }
        val now = Clock.nowMillis(); val id = newId()
        db.withTransaction {
            db.projects().insert(ProjectEntity(id, code.trim(), name.trim(), client, contractor, location, description, responsibles, "ACTIVE", isDemo, now, now))
            audit("PROJECT", id, "Alta", "Proyecto ${code.trim()} – ${name.trim()}${if (isDemo) " (DEMOSTRATIVO)" else ""}")
        }
        return id
    }

    suspend fun updateProject(p: ProjectEntity) = db.withTransaction {
        db.projects().update(p.copy(updatedAt = Clock.nowMillis())); audit("PROJECT", p.id, "Modificación", p.name)
    }

    // ---------------- Actividades ----------------
    suspend fun createActivity(projectId: String, name: String, description: String = "", defaultCriterionId: String? = StatisticalCriterion.LEGACY.id): String {
        require(name.isNotBlank()) { "El nombre de la actividad es obligatorio" }
        val now = Clock.nowMillis(); val id = newId()
        db.withTransaction {
            db.activities().insert(ActivityEntity(id, projectId, name.trim(), description, defaultCriterionId = defaultCriterionId,
                defaultBulk = settings.current().defaultBulk, createdAt = now, updatedAt = now, lastOpenedAt = now))
            audit("ACTIVITY", id, "Alta", name)
        }
        return id
    }

    suspend fun updateActivity(a: ActivityEntity) = db.withTransaction {
        val old = db.activities().get(a.id)
        db.activities().update(a.copy(updatedAt = Clock.nowMillis()))
        if (old?.status != a.status) audit("ACTIVITY", a.id, "Cambio de estado", "${ActivityStatus.of(old?.status ?: "").label} → ${ActivityStatus.of(a.status).label}")
        else audit("ACTIVITY", a.id, "Modificación", a.name)
    }

    suspend fun touchActivity(id: String) = db.activities().touch(id, Clock.nowMillis())

    // ---------------- Jornadas ----------------
    suspend fun createJourney(activityId: String, workDate: String = LocalDate.now(Clock.zone()).toString(), responsible: String? = null, workFront: String = "", observations: String = "", weather: String = ""): String {
        val now = Clock.nowMillis(); val id = newId()
        db.withTransaction {
            db.journeys().insert(JourneyEntity(id, activityId, workDate, responsible ?: settings.current().responsible, workFront, observations, weather, createdAt = now, updatedAt = now))
            audit("JOURNEY", id, "Alta", "Jornada $workDate")
        }
        return id
    }

    suspend fun updateJourney(j: JourneyEntity) = db.withTransaction { db.journeys().update(j.copy(updatedAt = Clock.nowMillis())); audit("JOURNEY", j.id, "Modificación", j.workDate) }

    // ---------------- Muestras ----------------
    suspend fun createSample(activityId: String, journeyId: String, lotId: String? = null, base: SampleForm? = null): String {
        val act = db.activities().get(activityId) ?: throw DomainException("Actividad inexistente")
        val s = settings.current()
        var form = base ?: SampleForm()
        if (base == null) {
            form = form.copy(i = act.defaultBulk ?: s.defaultBulk, elaboratedBy = s.responsible, materialId = act.defaultMaterialId)
            act.defaultCalibrationId?.let { form = applyCalibration(form, it) }
            act.defaultLabRefId?.let { form = applyLabRef(form, it) }
        }
        form = form.copy(testDateTime = form.testDateTime ?: LocalDateTime.now(Clock.zone()).withNano(0).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
        val now = Clock.nowMillis(); val id = newId(); val revId = newId()
        db.withTransaction {
            val seq = db.samples().maxSeq(activityId) + 1
            var code = "${act.samplePrefix}-${seq.toString().padStart(4, '0')}"
            var bump = seq
            while (db.samples().countCode(activityId, code) > 0) { bump++; code = "${act.samplePrefix}-${bump.toString().padStart(4, '0')}" }
            val result = ConoArenaLegacy.calculate(form.toInputs())
            db.samples().insert(SampleEntity(id, activityId, journeyId, lotId, code, seq, SampleStatus.DRAFT.name, revId, 1, createdAt = now, updatedAt = now).withCache(form, result))
            db.revisions().insert(SampleRevisionEntity(revId, id, 1, SampleStatus.DRAFT.name, AppJson.encodeToString(SampleForm.serializer(), form),
                AppJson.encodeToString(LegacyResult.serializer(), result), calibrationSnapshot(form), labRefSnapshot(form), ConoArenaLegacy.ENGINE_FULL_ID,
                createdBy = actor(), createdAt = now, updatedAt = now))
            audit("SAMPLE", id, "Alta", "Muestra $code (revisión 1, borrador)")
        }
        return id
    }

    private fun SampleEntity.withCache(form: SampleForm, r: LegacyResult) = copy(
        testDateTime = form.testDateTime, abscisaText = form.abscisa.ifBlank { null }, abscisaMeters = Abscisa.toMeters(form.abscisa),
        side = form.side.ifBlank { null }, layer = form.layer.ifBlank { null }, materialId = form.materialId, labRefId = form.labRefId,
        calibrationId = form.calibrationId, cacheP = r.value("P"), cacheS = r.value("S"), cacheL = DecimalText.storedToDouble(form.l),
        cacheR = DecimalText.storedToDouble(form.r), cacheQ = DecimalText.storedToDouble(form.q), cacheBlocking = r.blocking.size,
    )

    suspend fun applyCalibration(form: SampleForm, calibrationId: String): SampleForm {
        val c = db.catalogs().calibration(calibrationId) ?: return form
        return form.copy(calibrationId = c.id, equipmentId = c.equipmentId, d = c.coneConstantG, f = c.sandDensity)
    }

    suspend fun applyLabRef(form: SampleForm, labRefId: String): SampleForm {
        val l = db.catalogs().labRef(labRefId) ?: return form
        return form.copy(labRefId = l.id, q = l.maxDryDensity, r = l.optimumMoisture, labTestId = l.testId, materialId = form.materialId ?: l.materialId)
    }

    private suspend fun calibrationSnapshot(form: SampleForm): String? = form.calibrationId?.let { id ->
        db.catalogs().calibration(id)?.let { c ->
            val eq = db.catalogs().equipmentOne(c.equipmentId)
            AppJson.encodeToString(CalibrationSnapshot.serializer(), CalibrationSnapshot(c.id, c.code, eq?.let { "${it.code} – ${it.name}" } ?: "", c.date, c.validUntil, c.coneConstantG, c.sandDensity))
        }
    }

    private suspend fun labRefSnapshot(form: SampleForm): String? = form.labRefId?.let { id ->
        db.catalogs().labRef(id)?.let { l ->
            AppJson.encodeToString(LabRefSnapshot.serializer(), LabRefSnapshot(l.id, l.code, l.materialId?.let { db.catalogs().material(it)?.name }, l.testId, l.maxDryDensity, l.optimumMoisture))
        }
    }

    data class SampleBundle(val sample: SampleEntity, val revision: SampleRevisionEntity, val form: SampleForm, val result: LegacyResult)

    suspend fun bundle(sampleId: String): SampleBundle {
        val s = db.samples().get(sampleId) ?: throw DomainException("Muestra inexistente")
        val r = db.revisions().get(s.currentRevisionId) ?: throw DomainException("Revisión inexistente")
        return SampleBundle(s, r, decodeForm(r), decodeResult(r))
    }

    fun decodeForm(r: SampleRevisionEntity) = AppJson.decodeFromString(SampleForm.serializer(), r.dataJson)

    /** Para revisiones bloqueadas se usa SIEMPRE la instantánea guardada; nunca se recalcula. */
    fun decodeResult(r: SampleRevisionEntity): LegacyResult =
        r.calcJson?.let { AppJson.decodeFromString(LegacyResult.serializer(), it) } ?: ConoArenaLegacy.calculate(decodeForm(r).toInputs())

    /**
     * Guarda el formulario de la revisión vigente (guardado automático).
     * Rechaza revisiones bloqueadas (aprobadas) o anuladas. Editar una muestra completa o revisada
     * la devuelve a borrador, porque la revisión registrada ya no corresponde a los datos.
     */
    suspend fun saveForm(sampleId: String, form: SampleForm): LegacyResult {
        val result = ConoArenaLegacy.calculate(form.toInputs())
        db.withTransaction {
            val s = db.samples().get(sampleId) ?: throw DomainException("Muestra inexistente")
            val rev = db.revisions().get(s.currentRevisionId)!!
            if (rev.locked) throw DomainException("La revisión ${rev.number} está aprobada y bloqueada. Cree una nueva revisión para modificarla.")
            if (s.status == SampleStatus.VOID.name) throw DomainException("La muestra está anulada")
            val oldForm = decodeForm(rev)
            if (oldForm == form) return@withTransaction
            val now = Clock.nowMillis()
            var status = SampleStatus.of(rev.status)
            if (status == SampleStatus.COMPLETE || status == SampleStatus.REVIEWED) {
                audit("SAMPLE", sampleId, "Vuelve a borrador", "Se modificaron datos de una muestra ${status.label.lowercase()}")
                status = SampleStatus.DRAFT
            }
            if (oldForm.calibrationId != form.calibrationId || oldForm.d != form.d || oldForm.f != form.f) {
                audit("SAMPLE", sampleId, "Cambio de calibración", "D: ${oldForm.d ?: "—"} → ${form.d ?: "—"}; F: ${oldForm.f ?: "—"} → ${form.f ?: "—"}")
            }
            if (oldForm.labRefId != form.labRefId || oldForm.q != form.q) {
                audit("SAMPLE", sampleId, "Cambio de referencia de laboratorio", "Q: ${oldForm.q ?: "—"} → ${form.q ?: "—"}")
            }
            db.revisions().update(rev.copy(status = status.name, dataJson = AppJson.encodeToString(SampleForm.serializer(), form),
                calcJson = AppJson.encodeToString(LegacyResult.serializer(), result), calibrationSnapshot = calibrationSnapshot(form),
                labRefSnapshot = labRefSnapshot(form), engineVersion = ConoArenaLegacy.ENGINE_FULL_ID, updatedAt = now,
                reviewedBy = if (status == SampleStatus.DRAFT) null else rev.reviewedBy, reviewedAt = if (status == SampleStatus.DRAFT) null else rev.reviewedAt))
            db.samples().update(s.copy(status = status.name, updatedAt = now).withCache(form, result))
        }
        return result
    }

    /** Errores que impiden marcar como completa (incluye campos personalizados obligatorios). */
    suspend fun completenessErrors(sampleId: String): List<String> {
        val b = bundle(sampleId)
        val errs = mutableListOf<String>()
        b.result.blocking.forEach { errs += "${it.field}: ${it.message}" }
        b.result.steps.filter { it.value == null && it.symbol in setOf("S", "N", "P") && it.unavailableReason?.startsWith("Faltan") == true }
            .forEach { errs += "${it.symbol}: ${it.unavailableReason}" }
        if (b.result.value("S") == null && errs.isEmpty()) errs += "No se pudo calcular la compactación"
        if (b.form.abscisa.isBlank()) errs += "Abscisa: dato obligatorio"
        db.customFields().listAll(CustomFieldEntityType.SAMPLE.name).filter { it.required && !it.archived }.forEach { f ->
            if (b.form.custom[f.key].isNullOrBlank()) errs += "${f.label}: campo obligatorio"
        }
        return errs.distinct()
    }

    suspend fun markComplete(sampleId: String) {
        val errs = completenessErrors(sampleId)
        if (errs.isNotEmpty()) throw DomainException("No se puede marcar como completa:\n• " + errs.joinToString("\n• "))
        changeStatus(sampleId, SampleStatus.COMPLETE, setOf(SampleStatus.DRAFT), "Marcada como completa")
    }

    suspend fun markReviewed(sampleId: String, reviewer: String) {
        require(reviewer.isNotBlank()) { "Indique el responsable de la revisión" }
        changeStatus(sampleId, SampleStatus.REVIEWED, setOf(SampleStatus.COMPLETE), "Revisada por $reviewer") { it.copy(reviewedBy = reviewer, reviewedAt = Clock.nowMillis()) }
    }

    suspend fun approve(sampleId: String, approver: String) {
        require(approver.isNotBlank()) { "Indique el responsable de la aprobación" }
        changeStatus(sampleId, SampleStatus.APPROVED, setOf(SampleStatus.REVIEWED), "Aprobada por $approver (revisión bloqueada)") {
            it.copy(approvedBy = approver, approvedAt = Clock.nowMillis(), locked = true)
        }
    }

    suspend fun returnToDraft(sampleId: String) = changeStatus(sampleId, SampleStatus.DRAFT, setOf(SampleStatus.COMPLETE, SampleStatus.REVIEWED), "Devuelta a borrador") {
        it.copy(reviewedBy = null, reviewedAt = null)
    }

    private suspend fun changeStatus(sampleId: String, to: SampleStatus, from: Set<SampleStatus>, detail: String, revTransform: (SampleRevisionEntity) -> SampleRevisionEntity = { it }) {
        db.withTransaction {
            val s = db.samples().get(sampleId) ?: throw DomainException("Muestra inexistente")
            val cur = SampleStatus.of(s.status)
            if (cur !in from) throw DomainException("Transición no permitida: ${cur.label} → ${to.label}")
            val rev = db.revisions().get(s.currentRevisionId)!!
            if (rev.locked) throw DomainException("Revisión bloqueada")
            val now = Clock.nowMillis()
            db.revisions().update(revTransform(rev.copy(status = to.name, updatedAt = now)))
            db.samples().update(s.copy(status = to.name, updatedAt = now))
            audit("SAMPLE", sampleId, "Cambio de estado", "${cur.label} → ${to.label}. $detail (revisión ${rev.number})")
        }
    }

    /** Nueva revisión de una muestra aprobada: conserva la anterior intacta y exige motivo. */
    suspend fun createRevision(sampleId: String, reason: String): Int {
        if (reason.isBlank()) throw DomainException("El motivo de la nueva revisión es obligatorio")
        var number = 0
        db.withTransaction {
            val s = db.samples().get(sampleId) ?: throw DomainException("Muestra inexistente")
            val rev = db.revisions().get(s.currentRevisionId)!!
            if (!rev.locked) throw DomainException("Solo se crean revisiones nuevas a partir de una revisión aprobada; la vigente es editable.")
            val now = Clock.nowMillis(); val newRev = newId(); number = rev.number + 1
            db.revisions().update(rev.copy(superseded = true))
            db.revisions().insert(rev.copy(id = newRev, number = number, status = SampleStatus.DRAFT.name, reason = reason, createdBy = actor(),
                createdAt = now, updatedAt = now, reviewedBy = null, reviewedAt = null, approvedBy = null, approvedAt = null, locked = false, superseded = false))
            db.samples().update(s.copy(status = SampleStatus.DRAFT.name, currentRevisionId = newRev, revisionNumber = number, updatedAt = now))
            audit("SAMPLE", sampleId, "Nueva revisión", "Revisión $number creada a partir de la ${rev.number} (aprobada, se conserva). Motivo: $reason")
        }
        return number
    }

    suspend fun voidSample(sampleId: String, reason: String) {
        if (reason.isBlank()) throw DomainException("El motivo de anulación es obligatorio")
        db.withTransaction {
            val s = db.samples().get(sampleId) ?: throw DomainException("Muestra inexistente")
            val rev = db.revisions().get(s.currentRevisionId)!!
            if (!rev.locked) db.revisions().update(rev.copy(status = SampleStatus.VOID.name, reason = reason, updatedAt = Clock.nowMillis()))
            db.samples().update(s.copy(status = SampleStatus.VOID.name, voidReason = reason, updatedAt = Clock.nowMillis()))
            audit("SAMPLE", sampleId, "Anulación", "Motivo: $reason. La información se conserva.")
        }
    }

    /** Duplica únicamente los datos contextuales seleccionados; nunca mediciones, resultados, fotos ni aprobación. */
    suspend fun duplicate(sampleId: String, fields: Set<DuplicableField>): String {
        val b = bundle(sampleId)
        val f = b.form
        val act = db.activities().get(b.sample.activityId)!!
        var form = SampleForm(i = f.i ?: act.defaultBulk, elaboratedBy = settings.current().responsible)
        if (DuplicableField.ORIGIN in fields) form = form.copy(origin = f.origin)
        if (DuplicableField.LOCATION in fields) form = form.copy(location = f.location)
        if (DuplicableField.SIDE in fields) form = form.copy(side = f.side)
        if (DuplicableField.LAYER in fields) form = form.copy(layer = f.layer)
        if (DuplicableField.THICKNESS in fields) form = form.copy(thickness = f.thickness, thicknessUnit = f.thicknessUnit)
        if (DuplicableField.FILL_PART in fields) form = form.copy(fillPart = f.fillPart)
        if (DuplicableField.MATERIAL in fields) form = form.copy(materialId = f.materialId)
        if (DuplicableField.CALIBRATION in fields) form = form.copy(calibrationId = f.calibrationId, equipmentId = f.equipmentId, d = f.d, f = f.f)
        if (DuplicableField.LAB_REF in fields) form = form.copy(labRefId = f.labRefId, q = f.q, r = f.r, labTestId = f.labTestId)
        val journey = if (DuplicableField.JOURNEY in fields) b.sample.journeyId else (db.journeys().listByActivity(act.id).firstOrNull()?.id ?: b.sample.journeyId)
        val lot = if (DuplicableField.LOT in fields) b.sample.lotId else null
        val id = createSample(act.id, journey, lot, form)
        audit("SAMPLE", id, "Duplicada", "Datos contextuales copiados de ${b.sample.code}: ${fields.joinToString { it.label }}")
        return id
    }

    suspend fun moveToJourney(sampleId: String, journeyId: String) = db.withTransaction {
        val s = db.samples().get(sampleId)!!
        db.samples().update(s.copy(journeyId = journeyId, updatedAt = Clock.nowMillis()))
        audit("SAMPLE", sampleId, "Cambio de jornada", journeyId)
    }

    // ---------------- Lotes ----------------
    suspend fun createLot(activityId: String, code: String, description: String = "", criterionId: String? = StatisticalCriterion.LEGACY.id): String {
        if (code.isBlank()) throw DomainException("El código del lote es obligatorio")
        if (db.lots().listByActivity(activityId).any { it.code.equals(code.trim(), true) }) throw DomainException("Ya existe un lote con el código $code")
        val now = Clock.nowMillis(); val id = newId()
        db.withTransaction {
            db.lots().insert(LotEntity(id, activityId, code.trim(), description, criterionId = criterionId, createdAt = now, updatedAt = now))
            audit("LOT", id, "Alta", "Lote $code")
        }
        return id
    }

    suspend fun updateLot(l: LotEntity) = db.withTransaction { db.lots().update(l.copy(updatedAt = Clock.nowMillis())); audit("LOT", l.id, "Modificación", l.code) }

    /**
     * Asigna (o retira) una muestra a un lote. Una muestra pertenece como máximo a un lote:
     * reasignarla la retira del lote anterior y queda registrado.
     */
    suspend fun assignLot(sampleId: String, lotId: String?) = db.withTransaction {
        val s = db.samples().get(sampleId)!!
        if (lotId != null) {
            val lot = db.lots().get(lotId) ?: throw DomainException("Lote inexistente")
            if (lot.activityId != s.activityId) throw DomainException("El lote pertenece a otra actividad")
        }
        val prev = s.lotId
        db.samples().update(s.copy(lotId = lotId, lotExcluded = false, lotExclusionReason = null, updatedAt = Clock.nowMillis()))
        val prevCode = prev?.let { db.lots().get(it)?.code }
        val newCode = lotId?.let { db.lots().get(it)?.code }
        audit("SAMPLE", sampleId, "Cambio de lote", "${prevCode ?: "sin lote"} → ${newCode ?: "sin lote"}")
        lotId?.let { audit("LOT", it, "Incorporación", s.code) }
        prev?.let { audit("LOT", it, "Retiro", s.code) }
    }

    suspend fun setExclusion(sampleId: String, excluded: Boolean, reason: String?) {
        if (excluded && reason.isNullOrBlank()) throw DomainException("El motivo de exclusión es obligatorio")
        db.withTransaction {
            val s = db.samples().get(sampleId)!!
            val lot = s.lotId ?: throw DomainException("La muestra no pertenece a un lote")
            db.samples().update(s.copy(lotExcluded = excluded, lotExclusionReason = if (excluded) reason else null, updatedAt = Clock.nowMillis()))
            audit("LOT", lot, if (excluded) "Exclusión" else "Reincorporación", "${s.code}${if (excluded) ": $reason" else ""}")
            audit("SAMPLE", sampleId, if (excluded) "Excluida del lote" else "Reincorporada al lote", reason ?: "")
        }
    }

    suspend fun criterion(id: String?): StatisticalCriterion? = id?.let { db.catalogs().criterion(it) }?.takeIf { !it.archived }
        ?.let { AppJson.decodeFromString(StatisticalCriterion.serializer(), it.json) }

    suspend fun evaluateLot(lotId: String): LotEvaluation {
        val lot = db.lots().get(lotId) ?: throw DomainException("Lote inexistente")
        val samples = db.samples().listByLot(lotId)
        val obs = samples.map { s ->
            LotObservation(s.id, s.code, s.cacheP, s.cacheQ, s.cacheS, s.materialId, s.labRefId, SampleStatus.of(s.status).eligibleForCalc && s.cacheBlocking == 0, s.lotExcluded, s.lotExclusionReason)
        }
        val e = LotEvaluator.evaluate(obs, criterion(lot.criterionId))
        val mismatch = lot.labRefId != null && obs.any { !it.excluded && it.documentStatusEligible && it.labRefKey != lot.labRefId }
        return if (mismatch) e.copy(status = LotEvaluationStatus.NOT_EVALUABLE, leftSide = null, rightSide = null,
            reasons = e.reasons + "Hay muestras incluidas con una referencia de laboratorio distinta de la declarada para el lote.") else e
    }

    // ---------------- Catálogos (no se eliminan: se archivan) ----------------
    suspend fun saveMaterial(m: MaterialEntity) = db.withTransaction { db.catalogs().upsertMaterial(m.copy(updatedAt = Clock.nowMillis())); audit("CATALOG", m.id, "Material guardado", "${m.code} ${m.name}${if (m.archived) " (archivado)" else ""}") }
    suspend fun saveEquipment(e: EquipmentEntity) = db.withTransaction { db.catalogs().upsertEquipment(e.copy(updatedAt = Clock.nowMillis())); audit("CATALOG", e.id, "Equipo guardado", e.code) }
    suspend fun saveCalibration(c: CalibrationEntity) = db.withTransaction {
        if ((DecimalText.storedToDouble(c.sandDensity) ?: 0.0) <= 0 || (DecimalText.storedToDouble(c.coneConstantG) ?: -1.0) < 0) throw DomainException("Constante del cono y densidad de arena deben ser válidas")
        db.catalogs().upsertCalibration(c.copy(updatedAt = Clock.nowMillis()))
        audit("CATALOG", c.id, "Calibración guardada", "${c.code}: D=${c.coneConstantG} g, F=${c.sandDensity} g/cm³. Las muestras existentes conservan sus valores.")
    }
    suspend fun saveLabRef(l: LabReferenceEntity) = db.withTransaction {
        if ((DecimalText.storedToDouble(l.maxDryDensity) ?: 0.0) <= 0) throw DomainException("La densidad seca máxima debe ser positiva")
        db.catalogs().upsertLabRef(l.copy(updatedAt = Clock.nowMillis()))
        audit("CATALOG", l.id, "Referencia de laboratorio guardada", "${l.code}: De=${l.maxDryDensity} kg/m³. Las muestras existentes conservan sus valores.")
    }
    suspend fun savePerson(p: PersonEntity) = db.withTransaction { db.catalogs().upsertPerson(p.copy(updatedAt = Clock.nowMillis())); audit("CATALOG", p.id, "Responsable guardado", p.name) }
    suspend fun saveCriterion(c: StatisticalCriterion, archived: Boolean = false) = db.withTransaction {
        val existing = db.catalogs().criterion(c.id)
        if (existing != null && existing.version == c.version && existing.json != AppJson.encodeToString(StatisticalCriterion.serializer(), c))
            throw DomainException("Para modificar un criterio cree una nueva versión (cambie la versión).")
        db.catalogs().upsertCriterion(CriterionEntity(c.id, c.code, c.version, AppJson.encodeToString(StatisticalCriterion.serializer(), c), c.validated, archived, Clock.nowMillis()))
        audit("CRITERION", c.id, if (existing == null) "Alta de criterio" else "Criterio actualizado", "${c.code} v${c.version}; validado=${c.validated}; fuente=${c.sourceDocument ?: "—"}")
    }

    // ---------------- Campos personalizados ----------------
    suspend fun saveCustomField(f: CustomFieldEntity) = db.withTransaction {
        val key = f.key.trim()
        if (!Regex("^[a-z][a-z0-9_]{1,39}$").matches(key)) throw DomainException("Identificador inválido: use minúsculas, números y guion bajo (ej. temperatura_ambiente)")
        val existing = db.customFields().byKey(key)
        if (existing != null && existing.id != f.id) throw DomainException("Ya existe un campo con el identificador $key")
        val version = if (existing == null) 1 else if (existing.copy(updatedAt = 0) != f.copy(updatedAt = 0, version = existing.version)) existing.version + 1 else existing.version
        db.customFields().upsert(f.copy(key = key, version = version, updatedAt = Clock.nowMillis()))
        audit("CUSTOM_FIELD", f.id, if (existing == null) "Alta" else "Modificación", "$key v$version: ${f.label}${if (f.archived) " (archivado)" else ""}")
    }
}
