package com.geodensidad.core.calc

import com.geodensidad.core.demo.ReferenceSamples
import com.geodensidad.core.stats.Descriptive
import org.junit.Assert.*
import org.junit.Test

class ConoArenaLegacyTest {
    // Valores esperados publicados con 6 decimales → tolerancia de media unidad del último decimal.
    private val tol6 = 5.1e-7

    @Test fun diezMuestrasDeReferencia() {
        for (k in 0 until 10) {
            val r = ConoArenaLegacy.calculate(ReferenceSamples.inputs(k))
            assertTrue("muestra ${k + 1} sin bloqueos: ${r.blocking}", r.blocking.isEmpty())
            assertEquals("P muestra ${k + 1}", ReferenceSamples.expectedP[k], r.value("P")!!, tol6)
            assertEquals("S muestra ${k + 1}", ReferenceSamples.expectedS[k], r.value("S")!!, tol6)
            assertTrue(r.isComplete)
        }
    }

    @Test fun intermediosMuestra1() {
        val r = ConoArenaLegacy.calculate(ReferenceSamples.inputs(0))
        assertEquals(3822.0, r.value("C")!!, 1e-9)
        assertEquals(2087.0, r.value("E")!!, 1e-9)
        assertEquals(1494.9856733524357, r.value("G")!!, 1e-9)
        assertEquals(11.099079588521928, r.value("K")!!, 1e-12)
        assertEquals(2470.9266890273116, r.value("M")!!, 1e-9)
        assertEquals(0.06499390986601705, r.value("N")!!, 1e-14)
        assertEquals(2331.062914176709, r.value("O")!!, 1e-9)
        assertEquals(2294.972396679278, r.value("P")!!, 1e-9)
        assertEquals(91.98286159035183, r.value("S")!!, 1e-11)
        assertEquals((0.06 - 0.0668) * 100, r.value("ΔW")!!, 1e-12)
        // trazabilidad: cada resultado conserva fórmula y sustitución
        val p = r.step("P")!!
        assertTrue(p.formula.startsWith("P = "))
        assertNotNull(p.substituted)
        assertEquals(listOf("O", "K", "I"), p.dependsOn)
    }

    @Test fun promediosYDesviacion() {
        val ps = (0 until 10).map { ConoArenaLegacy.calculate(ReferenceSamples.inputs(it)).value("P")!! }
        val ss = (0 until 10).map { ConoArenaLegacy.calculate(ReferenceSamples.inputs(it)).value("S")!! }
        assertEquals(2243.201536, Descriptive.of(ps).mean!!, tol6)
        assertEquals(89.907877, Descriptive.of(ss).mean!!, tol6)
        val five = Descriptive.of(ps.take(5))
        assertEquals(5, five.n)
        assertEquals(2320.848395, five.mean!!, tol6)
        assertEquals(20.474110, five.sampleSd!!, tol6)
        // El Excel ('Cono de arena'!M33) promediaba solo 8 muestras: 89,31 %, distinto del promedio de las 10.
        assertEquals(89.306055, Descriptive.of(ss.drop(2)).mean!!, tol6)
    }

    private fun base() = ReferenceSamples.inputs(0)

    @Test fun datoFaltanteNoEsCero() {
        val r = ConoArenaLegacy.calculate(base().copy(j = null))
        assertNull(r.value("K")); assertNull(r.value("M")); assertNull(r.value("P")); assertNull(r.value("S"))
        assertTrue(r.step("K")!!.unavailableReason!!.contains("J"))
        assertNotNull(r.value("G")) // lo independiente se sigue calculando
        assertFalse(r.isComplete)
        val zero = ConoArenaLegacy.calculate(base().copy(j = 0.0))
        assertTrue(zero.blocking.any { it.field == "J" })
        assertNull(zero.value("K"))
    }

    @Test fun masaInicialMenorOIgualQueFinal() {
        val r = ConoArenaLegacy.calculate(base().copy(a = 3317.0))
        assertTrue(r.blocking.any { it.field == "A" })
        assertNull(r.value("C")); assertNull(r.value("S"))
    }

    @Test fun arenaEnHuecoNoPositiva() {
        val r = ConoArenaLegacy.calculate(base().copy(d = 3822.0))
        assertTrue(r.blocking.any { it.field == "E" })
        assertNull(r.value("E")); assertNull(r.value("G"))
    }

    @Test fun divisoresNoPositivos() {
        for (c in listOf(base().copy(f = 0.0), base().copy(f = -1.0), base().copy(i = 0.0), base().copy(q = 0.0), base().copy(q = -5.0))) {
            val r = ConoArenaLegacy.calculate(c)
            assertTrue(r.blocking.isNotEmpty())
            assertNull(r.value("S"))
            r.steps.forEach { s -> s.value?.let { assertTrue(it.isFinite()) } }
        }
    }

    @Test fun retenidoNegativoOIncompatible() {
        assertTrue(ConoArenaLegacy.calculate(base().copy(h = -1.0)).blocking.any { it.field == "H" })
        val eq = ConoArenaLegacy.calculate(base().copy(h = 3694.0))
        assertTrue(eq.blocking.any { it.field == "H" })
        assertNull(eq.value("N")); assertNull(eq.value("P"))
        val zeroH = ConoArenaLegacy.calculate(base().copy(h = 0.0))
        assertEquals(0.0, zeroH.value("K")!!, 0.0)
        assertEquals(zeroH.value("O")!!, zeroH.value("P")!!, 1e-9) // sin retenido no hay corrección
    }

    @Test fun denominadorDePFisicamenteInvalido() {
        // O·K/(I·1000) ≥ 100 → denominador ≤ 0
        val r = ConoArenaLegacy.calculate(base().copy(i = 0.2))
        assertNull(r.value("P"))
        assertTrue(r.blocking.any { it.field == "P" })
    }

    @Test fun nuncaResultadosNoFinitos() {
        val extremes = listOf(1e-300, 1e300, Double.MAX_VALUE, Double.MIN_VALUE)
        for (x in extremes) {
            val r = ConoArenaLegacy.calculate(base().copy(f = x, j = x.coerceAtLeast(5000.0)))
            r.steps.forEach { s -> s.value?.let { assertTrue("${s.symbol}=$it", it.isFinite()) } }
        }
    }

    @Test fun advertenciaHumedadEnEscalaEquivocada() {
        val r = ConoArenaLegacy.calculate(base().copy(l = 6.0)) // 600 %: se digitó 6 como fracción
        assertTrue(r.warnings.any { it.field == "L" })
    }
}
