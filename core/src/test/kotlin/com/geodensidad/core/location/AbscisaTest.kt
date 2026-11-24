package com.geodensidad.core.location

import org.junit.Assert.*
import org.junit.Test

class AbscisaTest {
    @Test fun interpretables() {
        assertEquals(3150.0, Abscisa.toMeters("PR 003+150")!!, 0.0)
        assertEquals(3150.5, Abscisa.toMeters("K3+150,5")!!, 1e-9)
        assertEquals(12045.0, Abscisa.toMeters("12+045")!!, 0.0)
        assertEquals(3150.0, Abscisa.toMeters("3150")!!, 0.0)
    }
    @Test fun noInterpretablesNoSeInventan() {
        assertNull(Abscisa.toMeters("Box culvert 2"))
        assertNull(Abscisa.toMeters("K3+1500"))
        assertNull(Abscisa.toMeters(""))
        assertNull(Abscisa.toMeters("PR3+"))
    }
}
