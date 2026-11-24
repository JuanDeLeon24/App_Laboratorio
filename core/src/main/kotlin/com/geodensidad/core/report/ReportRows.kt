package com.geodensidad.core.report

import com.geodensidad.core.calc.InputDefs
import com.geodensidad.core.calc.LegacyInputs
import com.geodensidad.core.calc.LegacyResult
import com.geodensidad.core.number.Fmt

/** Conversión de entradas/resultados del motor a filas de informe (compartido por app y pruebas). */
object ReportRows {
    fun measurementRows(inp: LegacyInputs): List<List<String>> {
        fun row(sym: String, v: Double?, dec: Int, unit: String) = listOf(sym, InputDefs.get(sym).name, Fmt.num(v, dec), unit)
        return listOf(
            row("A", inp.a, 1, "g"), row("B", inp.b, 1, "g"), row("D", inp.d, 1, "g"), row("F", inp.f, 3, "g/cm³"),
            row("H", inp.h, 1, "g"), row("I", inp.i, 3, "(×1000 kg/m³)"), row("J", inp.j, 1, "g"),
            listOf("L", InputDefs.get("L").name, inp.l?.let { Fmt.num(it * 100, 2) } ?: "—", "%"),
        )
    }

    fun labRows(inp: LegacyInputs) = listOf(
        "Densidad seca máxima (De, Q)" to Fmt.withUnit(inp.q, 1, "kg/m³"),
        "Humedad óptima (R)" to Fmt.fractionAsPercent(inp.r),
    )

    private val decimals = mapOf("C" to 1, "E" to 1, "G" to 2, "K" to 2, "M" to 2, "N" to 4, "O" to 2, "P" to 2, "S" to 2, "ΔW" to 2)

    fun resultValue(symbol: String, v: Double?): String = when (symbol) {
        "N" -> if (v == null) "—" else Fmt.num(v * 100, 2)
        else -> Fmt.num(v, decimals[symbol] ?: 2)
    }

    fun resultRows(r: LegacyResult): List<List<String>> = r.steps.map { s ->
        val unit = when (s.symbol) { "N" -> "%"; else -> s.unit }
        listOf(s.symbol, s.name, if (s.value != null) resultValue(s.symbol, s.value) else "No disponible: ${s.unavailableReason}", unit, s.formula)
    }
}
