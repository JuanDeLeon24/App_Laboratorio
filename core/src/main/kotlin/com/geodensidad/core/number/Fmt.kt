package com.geodensidad.core.number

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Formato de presentación en español: coma decimal y punto como separador de miles.
 * Implementado sin depender del Locale del sistema para que el resultado sea reproducible.
 * El redondeo es solo de presentación (HALF_UP); nunca se aplica a valores intermedios.
 */
object Fmt {
    fun num(value: Double?, decimals: Int, grouping: Boolean = true): String {
        if (value == null || !value.isFinite()) return "—"
        val bd = BigDecimal(value.toString()).setScale(decimals, RoundingMode.HALF_UP)
        val negative = bd.signum() < 0
        val plain = bd.abs().toPlainString()
        val intPart = plain.substringBefore('.')
        val fracPart = if (decimals > 0) plain.substringAfter('.', "") else ""
        val groupedInt = if (grouping) intPart.reversed().chunked(3).joinToString(".").reversed() else intPart
        val body = if (fracPart.isNotEmpty()) "$groupedInt,$fracPart" else groupedInt
        return if (negative && bd.signum() != 0) "-$body" else body
    }

    /** Fracción (0,06) mostrada como porcentaje ("6,00 %"). */
    fun fractionAsPercent(fraction: Double?, decimals: Int = 2): String =
        if (fraction == null || !fraction.isFinite()) "—" else "${num(fraction * 100.0, decimals)} %"

    fun withUnit(value: Double?, decimals: Int, unit: String): String =
        if (value == null || !value.isFinite()) "—" else "${num(value, decimals)} $unit"
}
