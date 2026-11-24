package com.geodensidad.app

import com.geodensidad.app.data.CustomFieldEntity
import com.geodensidad.app.domain.*
import com.geodensidad.core.criteria.LotEvaluationStatus
import com.geodensidad.core.demo.ReferenceSamples
import com.geodensidad.core.stats.Descriptive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Flujos de extremo a extremo sobre la base Room real (SQLite nativo de Robolectric). */
@RunWith(RobolectricTestRunner::class)
class WorkflowTest {
    private lateinit var c: AppContainer
    @Before fun setup() { c = TestSupport.freshContainer() }

    @Test fun dosActividadesAlternandoConservanBorradores() = runBlocking {
        val r = c.repo
        val p = r.createProject("VA", "Vías de acceso")
        val a1 = r.createActivity(p, "Densidades granulares"); val a2 = r.createActivity(p, "Densidades subrasante")
        val j1 = r.createJourney(a1, "2026-11-24"); val j2 = r.createJourney(a2, "2026-11-24")
        val s1 = r.createSample(a1, j1); val s2 = r.createSample(a2, j2)
        r.saveForm(s1, r.bundle(s1).form.copy(abscisa = "PR 003+150", a = "7139"))
        r.saveForm(s2, r.bundle(s2).form.copy(abscisa = "K1+000", b = "2373"))
        r.saveForm(s1, r.bundle(s1).form.copy(b = "3317"))
        TestSupport.reopen(c) // cerrar y abrir: los borradores persisten
        val b1 = c.repo.bundle(s1); val b2 = c.repo.bundle(s2)
        assertEquals("7139", b1.form.a); assertEquals("3317", b1.form.b); assertEquals("PR 003+150", b1.form.abscisa)
        assertEquals("2373", b2.form.b); assertNull(b2.form.a) // faltante sigue siendo faltante, no cero
        assertEquals(SampleStatus.DRAFT.name, b1.sample.status)
        assertEquals(3150.0, b1.sample.abscisaMeters!!, 0.0)
        assertEquals(2, c.db.activities().observeOpen().first().size)
    }

    @Test fun diezMuestrasDeReferenciaYPromedio() = runBlocking {
        val pid = c.demo.load()
        val act = c.db.activities().observeByProject(pid).first().single()
        val samples = c.db.samples().listByActivity(act.id)
        assertEquals(10, samples.size)
        samples.forEachIndexed { k, s ->
            assertEquals(SampleStatus.COMPLETE.name, s.status)
            assertEquals(ReferenceSamples.expectedP[k], s.cacheP!!, 5.1e-7)
            assertEquals(ReferenceSamples.expectedS[k], s.cacheS!!, 5.1e-7)
        }
        assertEquals(89.907877, Descriptive.of(samples.map { it.cacheS!! }).mean!!, 5.1e-7)
        assertTrue(c.db.projects().get(pid)!!.isDemo)
        // lotes explícitos: cinco y cinco, sin mezclar poblaciones
        val lots = c.db.lots().listByActivity(act.id)
        val e1 = c.repo.evaluateLot(lots.first { it.code == "DEMO-L01" }.id)
        assertEquals(5, e1.included); assertEquals(5, e1.stats.n)
        assertEquals(2320.848395, e1.stats.mean!!, 5.1e-7); assertEquals(20.474110, e1.stats.sampleSd!!, 5.1e-7)
        assertEquals(LotEvaluationStatus.PROVISIONAL_FAILS, e1.status)
        // exclusión con motivo: n cambia a 4 → k no definido → no evaluable (sin interpolar)
        c.repo.setExclusion(samples[0].id, true, "Ensayo repetido")
        val e2 = c.repo.evaluateLot(lots.first { it.code == "DEMO-L01" }.id)
        assertEquals(4, e2.stats.n); assertEquals(1, e2.excluded); assertEquals(LotEvaluationStatus.NOT_EVALUABLE, e2.status)
    }

    @Test fun aprobacionBloqueaYNuevaRevisionConservaAnterior() = runBlocking {
        val r = c.repo
        val p = r.createProject("P", "Proyecto"); val a = r.createActivity(p, "Act"); val j = r.createJourney(a)
        val s = r.createSample(a, j)
        val form = SampleForm(abscisa = "K0+100", a = "7139", b = "3317", d = "1735", f = "1.396", h = "410", i = "2.667", j = "3694", l = "0.06", q = "2495", r = "0.0668")
        r.saveForm(s, form)
        try { r.approve(s, "X"); fail("no debe aprobar un borrador") } catch (e: DomainException) {}
        r.markComplete(s); r.markReviewed(s, "Revisor"); r.approve(s, "Aprobador")
        val approved = r.bundle(s)
        assertTrue(approved.revision.locked)
        try { r.saveForm(s, form.copy(a = "7000")); fail("una revisión aprobada no se sobrescribe") } catch (e: DomainException) {}
        try { r.createRevision(s, " "); fail("se exige motivo") } catch (e: DomainException) {}
        assertEquals(2, r.createRevision(s, "Corrección de masa A"))
        r.saveForm(s, r.bundle(s).form.copy(a = "7140"))
        val revs = c.db.revisions().listBySample(s)
        assertEquals(2, revs.size)
        val old = revs.first { it.number == 1 }
        assertTrue(old.locked); assertTrue(old.superseded); assertEquals("Aprobador", old.approvedBy)
        assertEquals("7139", r.decodeForm(old).a) // la anterior queda intacta
        assertEquals(91.982862, r.decodeResult(old).value("S")!!, 5.1e-7)
        assertEquals("7140", r.bundle(s).form.a)
        assertEquals(SampleStatus.DRAFT.name, r.bundle(s).sample.status)
        val audit = c.db.audit().observeFor(s).first().map { it.action }
        assertTrue(audit.contains("Nueva revisión")); assertTrue(audit.contains("Cambio de estado"))
    }

    @Test fun edicionDeCompletaVuelveABorradorYEntradaInvalidaNoDejaResultado() = runBlocking {
        val r = c.repo
        val p = r.createProject("P", "Proyecto"); val a = r.createActivity(p, "Act"); val j = r.createJourney(a)
        val s = r.createSample(a, j)
        val form = SampleForm(abscisa = "K0+100", a = "7139", b = "3317", d = "1735", f = "1.396", h = "410", i = "2.667", j = "3694", l = "0.06", q = "2495", r = "0.0668")
        r.saveForm(s, form); r.markComplete(s)
        val res = r.saveForm(s, form.copy(a = "3000")) // A < B
        assertNull(res.value("S"))
        val b = r.bundle(s)
        assertEquals(SampleStatus.DRAFT.name, b.sample.status)
        assertNull(b.sample.cacheS) // no se conserva el resultado anterior como vigente
        assertTrue(b.sample.cacheBlocking > 0)
        try { r.markComplete(s); fail() } catch (e: DomainException) { assertTrue(e.message!!.contains("A")) }
    }

    @Test fun duplicarNoCopiaMedicionesNiFotos() = runBlocking {
        val r = c.repo
        val p = r.createProject("P", "Proyecto"); val a = r.createActivity(p, "Act"); val j = r.createJourney(a)
        val s = r.createSample(a, j)
        r.saveForm(s, r.bundle(s).form.copy(abscisa = "K0+100", side = "Derecho", layer = "2", a = "7139", b = "3317", d = "1735", f = "1.396"))
        val d = r.duplicate(s, setOf(DuplicableField.SIDE, DuplicableField.LAYER, DuplicableField.CALIBRATION))
        val f = r.bundle(d).form
        assertEquals("Derecho", f.side); assertEquals("2", f.layer); assertEquals("1735", f.d)
        assertNull(f.a); assertNull(f.b); assertEquals("", f.abscisa)
        assertEquals(SampleStatus.DRAFT.name, r.bundle(d).sample.status)
        assertTrue(c.db.photos().listBySample(d).isEmpty())
    }

    @Test fun campoPersonalizadoObligatorioPersisteYBloquea() = runBlocking {
        val r = c.repo
        r.saveCustomField(CustomFieldEntity(newId(), "temperatura_ambiente", "SAMPLE", "Temperatura ambiente", type = "NUMBER", unit = "°C", required = true, updatedAt = 0))
        val p = r.createProject("P", "Proyecto"); val a = r.createActivity(p, "Act"); val j = r.createJourney(a)
        val s = r.createSample(a, j)
        val form = SampleForm(abscisa = "K0+100", a = "7139", b = "3317", d = "1735", f = "1.396", h = "410", i = "2.667", j = "3694", l = "0.06", q = "2495", r = "0.0668")
        r.saveForm(s, form)
        try { r.markComplete(s); fail() } catch (e: DomainException) { assertTrue(e.message!!.contains("Temperatura ambiente")) }
        r.saveForm(s, form.copy(custom = mapOf("temperatura_ambiente" to "24.5")))
        r.markComplete(s)
        // cambiar la etiqueta no rompe los datos (clave estable) y versiona la definición
        val def = c.db.customFields().byKey("temperatura_ambiente")!!
        r.saveCustomField(def.copy(label = "T. ambiente"))
        assertEquals(2, c.db.customFields().byKey("temperatura_ambiente")!!.version)
        TestSupport.reopen(c)
        assertEquals("24.5", c.repo.bundle(s).form.custom["temperatura_ambiente"])
    }

    @Test fun cambiarCatalogoNoAlteraMuestrasExistentes() = runBlocking {
        val pid = c.demo.load()
        val act = c.db.activities().observeByProject(pid).first().single()
        val s = c.db.samples().listByActivity(act.id).first()
        val before = c.repo.bundle(s.id)
        val cal = c.db.catalogs().calibration(before.form.calibrationId!!)!!
        c.repo.saveCalibration(cal.copy(coneConstantG = "1800"))
        val after = c.repo.bundle(s.id)
        assertEquals("1735", after.form.d)
        assertEquals(before.result.value("S")!!, after.result.value("S")!!, 0.0)
        assertTrue(after.revision.calibrationSnapshot!!.contains("1735"))
    }

    @Test fun loteUnicoPorMuestraYRegistro() = runBlocking {
        val r = c.repo
        val p = r.createProject("P", "Proyecto"); val a = r.createActivity(p, "Act"); val j = r.createJourney(a)
        val s = r.createSample(a, j)
        val l1 = r.createLot(a, "L1"); val l2 = r.createLot(a, "L2")
        r.assignLot(s, l1); r.assignLot(s, l2)
        assertEquals(l2, c.db.samples().get(s)!!.lotId)
        assertTrue(c.db.samples().listByLot(l1).isEmpty())
        try { r.createLot(a, "l1"); fail("código duplicado") } catch (e: DomainException) {}
        val other = r.createActivity(p, "Otra")
        val lx = r.createLot(other, "LX")
        try { r.assignLot(s, lx); fail("lote de otra actividad") } catch (e: DomainException) {}
    }
}
