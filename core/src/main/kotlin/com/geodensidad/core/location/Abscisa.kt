package com.geodensidad.core.location

/**
 * Abscisa: se conserva siempre el texto original y solo se deriva un valor numérico (metros)
 * cuando el texto es interpretable sin ambigüedad: "PR 003+150", "K3+150,5", "3+150", "3150".
 * Cualquier otra representación (p. ej. "Box Culvert 2") queda sin valor numérico.
 */
object Abscisa {
    private val kmPlus = Regex("^(?:PR|K|KM|ABS)?\\s*\\.?\\s*(\\d{1,4})\\s*\\+\\s*(\\d{1,3}(?:[.,]\\d+)?)$", RegexOption.IGNORE_CASE)
    private val plain = Regex("^\\d{1,7}(?:[.,]\\d+)?$")

    fun toMeters(text: String?): Double? {
        val t = text?.trim().orEmpty()
        if (t.isEmpty()) return null
        kmPlus.matchEntire(t)?.let { m ->
            val km = m.groupValues[1].toDouble()
            val meters = m.groupValues[2].replace(',', '.').toDouble()
            if (meters >= 1000.0) return null
            return km * 1000.0 + meters
        }
        if (plain.matches(t)) return t.replace(',', '.').toDouble()
        return null
    }

    fun formatMeters(m: Double): String {
        val km = (m / 1000).toInt()
        val rest = m - km * 1000
        val restText = if (rest % 1.0 == 0.0) rest.toInt().toString().padStart(3, '0')
        else com.geodensidad.core.number.Fmt.num(rest, 2, grouping = false).padStart(6, '0')
        return "K${km}+$restText"
    }
}
