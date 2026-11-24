package com.geodensidad.core.criteria

import com.geodensidad.core.calc.ConoArenaLegacy
import com.geodensidad.core.demo.ReferenceSamples
import org.junit.Assert.*
import org.junit.Test

class LotEvaluatorTest {
    private fun obs(range: IntRange, de: Double = 2495.0, mat: String = "granular", lab: String = "LAB-1") = range.map { i ->
        val r = ConoArenaLegacy.calculate(ReferenceSamples.inputs(i))
        LotObservation("id$i", "M${i + 1}", r.value("P"), de, r.value("S"), mat, lab, documentStatusEligible = true, excluded = false)
    }
    private val c = StatisticalCriterion.LEGACY

    @Test fun cincoMuestrasCoherentes() {
        val e = LotEvaluator.evaluate(obs(0..4), c)
        assertEquals(5, e.included); assertEquals(5, e.stats.n)
        assertEquals(2320.848395, e.stats.mean!!, 5.1e-7)
        assertEquals(20.474110, e.stats.sampleSd!!, 5.1e-7)
        assertEquals(0.685, e.k!!, 0.0)
        assertEquals(2320.848395 - 0.685 * 20.474110, e.leftSide!!, 1e-5)
        assertEquals(0.95 * 2495, e.rightSide!!, 1e-9)
        // 2306,8 < 2370,25 → no cumple, provisional porque el criterio no está validado
        assertEquals(LotEvaluationStatus.PROVISIONAL_FAILS, e.status)
        assertFalse(e.criterionValidated)
    }

    @Test fun diezMuestrasUsaKDeDiez() {
        val e = LotEvaluator.evaluate(obs(0..9), c)
        assertEquals(10, e.stats.n); assertEquals(0.437, e.k!!, 0.0)
        assertEquals(2243.201536, e.stats.mean!!, 5.1e-7)
        assertEquals(89.907877, e.compactionStats.mean!!, 5.1e-7)
    }

    @Test fun coeficienteInexistenteNoSeInterpola() {
        val e = LotEvaluator.evaluate(obs(0..3), c)
        assertEquals(LotEvaluationStatus.NOT_EVALUABLE, e.status)
        assertNull(e.k)
        assertTrue(e.reasons.any { it.contains("n = 4") })
        val e11 = LotEvaluator.evaluate(obs(0..9) + obs(0..0).map { it.copy(sampleId = "x") }, c)
        assertEquals(LotEvaluationStatus.NOT_EVALUABLE, e11.status)
    }

    @Test fun unaYDosObservaciones() {
        val one = LotEvaluator.evaluate(obs(0..0), c)
        assertEquals(1, one.stats.n); assertNull(one.stats.sampleSd); assertEquals(LotEvaluationStatus.NOT_EVALUABLE, one.status)
        val two = LotEvaluator.evaluate(obs(0..1), c)
        assertNotNull(two.stats.sampleSd); assertEquals(LotEvaluationStatus.NOT_EVALUABLE, two.status)
    }

    @Test fun vacio() {
        val e = LotEvaluator.evaluate(emptyList(), c)
        assertEquals(0, e.included); assertEquals(LotEvaluationStatus.NOT_EVALUABLE, e.status)
    }

    @Test fun exclusionesCuentanExactamente() {
        val o = obs(0..5).mapIndexed { i, x -> if (i == 5) x.copy(excluded = true, exclusionReason = "Ensayo repetido") else x }
        val e = LotEvaluator.evaluate(o, c)
        assertEquals(6, e.registered); assertEquals(5, e.included); assertEquals(1, e.excluded)
        assertEquals(5, e.stats.n); assertEquals(0.685, e.k!!, 0.0)
        assertEquals("Ensayo repetido", e.excludedDetail.single().second)
    }

    @Test fun borradoresSonPendientes() {
        val o = obs(0..5).mapIndexed { i, x -> if (i == 0) x.copy(documentStatusEligible = false) else x }
        val e = LotEvaluator.evaluate(o, c)
        assertEquals(1, e.pending); assertEquals(5, e.included)
    }

    @Test fun poblacionesIncompatibles() {
        val mixedDe = obs(0..2) + obs(3..5, de = 2400.0, lab = "LAB-2")
        val e = LotEvaluator.evaluate(mixedDe, c)
        assertEquals(LotEvaluationStatus.NOT_EVALUABLE, e.status)
        assertTrue(e.reasons.any { it.contains("incompatible") })
        assertNull(e.individualAboveThreshold)
        val mixedMat = obs(0..2) + obs(3..5, mat = "otro")
        assertEquals(LotEvaluationStatus.NOT_EVALUABLE, LotEvaluator.evaluate(mixedMat, c).status)
    }

    @Test fun criterioValidadoNoEsProvisional() {
        val e = LotEvaluator.evaluate(obs(0..4), c.copy(validated = true, alpha = 0.90))
        assertEquals(LotEvaluationStatus.MEETS, e.status)
    }

    @Test fun sinCriterio() {
        assertEquals(LotEvaluationStatus.NOT_EVALUABLE, LotEvaluator.evaluate(obs(0..4), null).status)
    }
}
