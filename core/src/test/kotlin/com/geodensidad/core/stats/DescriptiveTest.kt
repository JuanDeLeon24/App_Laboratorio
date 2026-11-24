package com.geodensidad.core.stats

import org.junit.Assert.*
import org.junit.Test

class DescriptiveTest {
    @Test fun vacio() { val d = Descriptive.of(emptyList()); assertEquals(0, d.n); assertNull(d.mean); assertNull(d.sampleSd) }
    @Test fun unaObservacionSinDesviacion() { val d = Descriptive.of(listOf(5.0)); assertEquals(5.0, d.mean!!, 0.0); assertNull(d.sampleSd); assertNull(d.cvPercent) }
    @Test fun dosObservaciones() { val d = Descriptive.of(listOf(1.0, 3.0)); assertEquals(2.0, d.mean!!, 0.0); assertEquals(Math.sqrt(2.0), d.sampleSd!!, 1e-12); assertEquals(1.0, d.min!!, 0.0); assertEquals(3.0, d.max!!, 0.0) }
    @Test(expected = IllegalArgumentException::class) fun rechazaNoFinitos() { Descriptive.of(listOf(1.0, Double.NaN)) }
    @Test fun histogramaHonesto() {
        assertNull(Histogram.build(listOf(90.0, 91.0, 92.0), 1.0))
        val h = Histogram.build(listOf(85.2, 86.9, 87.3, 91.9, 94.2), 2.0)!!
        assertEquals(5, h.sumOf { it.count })
    }
}
