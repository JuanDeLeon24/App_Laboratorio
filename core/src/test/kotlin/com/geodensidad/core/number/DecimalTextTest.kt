package com.geodensidad.core.number

import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class DecimalTextTest {
    private fun v(s: String) = (DecimalText.parse(s) as ParsedDecimal.Valid).value
    @Test fun comaDecimal() { assertEquals(BigDecimal("1.396"), v("1,396")); assertEquals(BigDecimal("6.5"), v(" 6,5 ")) }
    @Test fun puntoDecimal() { assertEquals(BigDecimal("2.667"), v("2.667")) }
    @Test fun vacioEsFaltanteNoCero() { assertEquals(ParsedDecimal.Empty, DecimalText.parse("")); assertEquals(ParsedDecimal.Empty, DecimalText.parse(null)) }
    @Test fun ceroEsValor() { assertEquals(0, v("0").signum()) }
    @Test fun ambiguosRechazados() {
        assertTrue(DecimalText.parse("1.234,5") is ParsedDecimal.Invalid)
        assertTrue(DecimalText.parse("1 234") is ParsedDecimal.Invalid)
        assertTrue(DecimalText.parse("1,2,3") is ParsedDecimal.Invalid)
        assertTrue(DecimalText.parse("abc") is ParsedDecimal.Invalid)
        assertTrue(DecimalText.parse(",") is ParsedDecimal.Invalid)
    }
    @Test fun humedadPorcentualYDecimal() {
        assertEquals("0.06", DecimalText.canonical((DecimalText.percentTextToFraction("6") as ParsedDecimal.Valid).value))
        assertEquals("0.0668", DecimalText.canonical((DecimalText.percentTextToFraction("6,68") as ParsedDecimal.Valid).value))
        assertEquals("6", DecimalText.fractionToPercentText("0.06"))
        assertEquals("6,68", DecimalText.fractionToPercentText("0.0668"))
        assertEquals("", DecimalText.fractionToPercentText(null))
    }
    @Test fun formatoEspanol() {
        assertEquals("2.294,97", Fmt.num(2294.972397, 2))
        assertEquals("91,98", Fmt.num(91.982862, 2))
        assertEquals("6,00 %", Fmt.fractionAsPercent(0.06))
        assertEquals("—", Fmt.num(null, 2))
        assertEquals("-0,50", Fmt.num(-0.5, 2))
        assertEquals("—", Fmt.num(Double.NaN, 2))
    }
}
