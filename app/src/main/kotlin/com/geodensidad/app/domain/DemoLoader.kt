package com.geodensidad.app.domain

import com.geodensidad.app.data.CalibrationEntity
import com.geodensidad.app.data.EquipmentEntity
import com.geodensidad.app.data.LabReferenceEntity
import com.geodensidad.app.data.MaterialEntity
import com.geodensidad.core.criteria.StatisticalCriterion
import com.geodensidad.core.demo.ReferenceSamples

/**
 * Carga, SOLO por acción explícita del usuario, un proyecto marcado como DEMOSTRATIVO con las
 * diez muestras de referencia del Excel. Nunca se mezcla con proyectos reales: todo queda
 * identificado con el prefijo "DEMO" y el indicador is_demo.
 */
class DemoLoader(private val repo: GeoRepository) {
    suspend fun load(): String {
        val now = Clock.nowMillis()
        val mat = MaterialEntity(newId(), "DEMO-GRAN", "Granular (DEMO)", "Material demostrativo", updatedAt = now)
        repo.saveMaterial(mat)
        val e1 = EquipmentEntity(newId(), "DEMO-CONO-01", "Cono de arena 1 (DEMO)", updatedAt = now)
        val e2 = EquipmentEntity(newId(), "DEMO-CONO-02", "Cono de arena 2 (DEMO)", updatedAt = now)
        repo.saveEquipment(e1); repo.saveEquipment(e2)
        val c1 = CalibrationEntity(newId(), "DEMO-CAL-01", e1.id, "2026-11-01", null, "1735", "1.396", "Valores de las muestras 1–5 del Excel", updatedAt = now)
        val c2 = CalibrationEntity(newId(), "DEMO-CAL-02", e2.id, "2026-11-15", null, "1774", "1.408", "Valores de las muestras 6–10 del Excel", updatedAt = now)
        repo.saveCalibration(c1); repo.saveCalibration(c2)
        val lab = LabReferenceEntity(newId(), "DEMO-PROCTOR-01", mat.id, "Proctor demostrativo", ReferenceSamples.Q, optimumMoisture = ReferenceSamples.R, notes = "Q y R del Excel de origen", updatedAt = now)
        repo.saveLabRef(lab)
        val pid = repo.createProject("DEMO", "DEMO – Vías de acceso (datos demostrativos)", "Entidad demostrativa", "Contratista demostrativo", "Sin ubicación real",
            "Proyecto con las 10 muestras de referencia del archivo de origen. No corresponde a ensayos reales.", isDemo = true)
        val aid = repo.createActivity(pid, "Toma de densidades para granulares (DEMO)", "Reproduce las diez muestras de la hoja 'Cono de arena'.", StatisticalCriterion.LEGACY.id)
        val act = repo.db.activities().get(aid)!!
        repo.updateActivity(act.copy(defaultMaterialId = mat.id, defaultLabRefId = lab.id, defaultBulk = ReferenceSamples.I))
        val jid = repo.createJourney(aid, "2026-11-24", "Responsable demostrativo", "Frente demostrativo")
        val l1 = repo.createLot(aid, "DEMO-L01", "Muestras 1 a 5")
        val l2 = repo.createLot(aid, "DEMO-L02", "Muestras 6 a 10")
        val abs = listOf("PR 003+150", "PR 003+175", "PR 003+200", "PR 003+225", "PR 003+250", "PR 004+010", "PR 004+040", "PR 004+070", "PR 004+100", "PR 004+130")
        for (k in 0 until 10) {
            val form = SampleForm(
                testDateTime = "2026-11-24T%02d:%02d:00".format(8 + k / 2, (k % 2) * 30), origin = "Fuente demostrativa", abscisa = abs[k], side = if (k % 2 == 0) "Derecho" else "Izquierdo",
                layer = "2", thickness = "20", fillPart = "Núcleo", materialId = mat.id, equipmentId = if (k < 5) e1.id else e2.id, calibrationId = if (k < 5) c1.id else c2.id,
                labRefId = lab.id, a = ReferenceSamples.A[k], b = ReferenceSamples.B[k], d = ReferenceSamples.D[k], f = ReferenceSamples.F[k], h = ReferenceSamples.H[k],
                i = ReferenceSamples.I, j = ReferenceSamples.J[k], l = ReferenceSamples.L[k], q = ReferenceSamples.Q, r = ReferenceSamples.R, labTestId = lab.testId,
                observations = "Muestra ${k + 1} del archivo de origen (DEMOSTRATIVA).", elaboratedBy = "Responsable demostrativo",
            )
            val sid = repo.createSample(aid, jid, if (k < 5) l1 else l2, form)
            repo.saveForm(sid, form)
            repo.markComplete(sid)
        }
        return pid
    }
}
