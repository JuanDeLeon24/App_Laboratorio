package com.geodensidad.core.number

import java.math.BigDecimal

/**
 * Interpretación de números escritos por el usuario en la interfaz en español.
 *
 * Reglas (documentadas en docs/FORMULAS.md):
 *  - Se admite un único separador decimal: coma (preferido) o punto.
 *  - No se admiten separadores de miles: "1.234,5" o "1 234" se rechazan por ambiguos.
 *  - Texto vacío significa "dato faltante" y NUNCA se convierte en cero.
 */
sealed interface ParsedDecimal {
    data object Empty : ParsedDecimal
    data class Valid(val value: BigDecimal) : ParsedDecimal
    data class Invalid(val message: String) : ParsedDecimal
}

object DecimalText {
    private val pattern = Regex("^[-+]?\\d*([.,]\\d*)?$")

    fun parse(raw: String?): ParsedDecimal {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return ParsedDecimal.Empty
        if (text.contains(' ') || text.contains(' ')) {
            return ParsedDecimal.Invalid("No use espacios ni separadores de miles")
        }
        if (text.contains(',') && text.contains('.')) {
            return ParsedDecimal.Invalid("Use un solo separador decimal (coma) y sin separador de miles")
        }
        if (text.count { it == ',' || it == '.' } > 1) {
            return ParsedDecimal.Invalid("Número con más de un separador")
        }
        if (!pattern.matches(text) || text.none { it.isDigit() }) {
            return ParsedDecimal.Invalid("Número no válido")
        }
        return try {
            val normalized = text.replace(',', '.').let { if (it.startsWith('.')) "0$it" else it }
                .let { if (it.endsWith('.')) it.dropLast(1) else it }
            ParsedDecimal.Valid(BigDecimal(normalized))
        } catch (e: NumberFormatException) {
            ParsedDecimal.Invalid("Número no válido")
        }
    }

    private val miles = Regex("^-?\\d{1,3}(\\.\\d{3})+(,\\d*)?$")

    /**
     * Interpretación según el campo. En campos de valores grandes (masas en g, densidad máxima en kg/m³)
     * un punto seguido de grupos de 3 cifras es separador de miles: "2.395" = 2395 y "7.139,5" = 7139,5.
     * En campos pequeños (F, I, L, R) el punto es decimal: "1.396" = 1,396.
     */
    fun parseCampo(raw: String?, valoresGrandes: Boolean): ParsedDecimal {
        val t = raw?.trim().orEmpty()
        return if (valoresGrandes && miles.matches(t)) parse(t.replace(".", "")) else parse(t)
    }

    /** Representación canónica de almacenamiento: decimal plano con punto, sin exponente. */
    fun canonical(value: BigDecimal): String = value.stripTrailingZeros().toPlainString().let { if (it == "-0") "0" else it }

    /** Convierte texto canónico almacenado a Double (o null si falta). */
    fun storedToDouble(stored: String?): Double? =
        stored?.takeIf { it.isNotBlank() }?.let { runCatching { BigDecimal(it).toDouble() }.getOrNull() }

    /** Convierte la entrada de usuario a texto canónico; null si vacío o inválido. */
    fun toCanonicalOrNull(raw: String?): String? = (parse(raw) as? ParsedDecimal.Valid)?.let { canonical(it.value) }

    /** Humedad digitada en porcentaje ("6" o "6,0") a fracción canónica ("0.06"). */
    fun percentTextToFraction(raw: String?): ParsedDecimal = when (val p = parse(raw)) {
        is ParsedDecimal.Valid -> ParsedDecimal.Valid(p.value.movePointLeft(2))
        else -> p
    }

    /** Fracción almacenada ("0.06") a texto de porcentaje para edición ("6"). */
    fun fractionToPercentText(stored: String?): String =
        stored?.takeIf { it.isNotBlank() }?.let { runCatching { canonical(BigDecimal(it).movePointRight(2)).replace('.', ',') }.getOrNull() }
            ?: ""

    /** Valor almacenado canónico a texto de edición con coma decimal. */
    fun storedToEditText(stored: String?): String = stored?.replace('.', ',') ?: ""
}
