package com.geodensidad.core.calc

import com.geodensidad.core.number.Fmt
import kotlinx.serialization.Serializable

/**
 * Motor de cálculo "cono-arena-legacy": reproduce aritméticamente las fórmulas de la hoja
 * 'Cono de arena' del archivo de origen. Reproducir el Excel NO equivale a validez normativa.
 *
 *  - No redondea valores intermedios (IEEE-754 doble precisión).
 *  - Un dato faltante es null y nunca se sustituye por cero.
 *  - Un resultado se marca "no disponible" con su motivo cuando falta una entrada,
 *    una validación bloqueante lo impide o el resultado no es finito.
 */
object ConoArenaLegacy {
    const val ENGINE_ID = "cono-arena-legacy"
    const val ENGINE_VERSION = "1.0.0"
    const val ENGINE_FULL_ID = "$ENGINE_ID-$ENGINE_VERSION"

    fun calculate(input: LegacyInputs, profile: LegacyProfile = LegacyProfile.DEFAULT): LegacyResult {
        val issues = mutableListOf<CalcIssue>()
        val steps = LinkedHashMap<String, TraceStep>()

        fun missing(vararg pairs: Pair<String, Double?>): String? {
            val m = pairs.filter { it.second == null }.map { it.first }
            return if (m.isEmpty()) null else "Faltan datos: ${m.joinToString(", ")}"
        }
        fun blocked(vararg symbols: String): String? {
            val b = symbols.filter { s -> steps[s]?.value == null }
            return if (b.isEmpty()) null else "Depende de resultado no disponible: ${b.joinToString(", ")}"
        }
        fun put(def: StepDef, value: Double?, reason: String?, substituted: String?) {
            val finite = value?.takeIf { it.isFinite() }
            val why = reason ?: if (value != null && !value.isFinite()) "Resultado no finito" else null
            steps[def.symbol] = TraceStep(
                symbol = def.symbol, name = def.name, unit = def.unit, formula = def.formula,
                substituted = if (finite != null) substituted else null,
                value = if (why == null) finite else null, unavailableReason = why, dependsOn = def.dependsOn,
            )
        }
        fun f(v: Double?) = Fmt.num(v, 6, grouping = false)

        val (a, b, d, fSand, h, iBulk, j, l, q, r) = input

        // ---- Validaciones de entrada (bloqueantes) ----
        if (a != null && b != null && a <= b) issues += CalcIssue.blocking("A", "La masa inicial (A) debe ser mayor que la masa final (B)")
        if (d != null && d < 0) issues += CalcIssue.blocking("D", "La constante del cono (D) no puede ser negativa")
        if (fSand != null && fSand <= 0) issues += CalcIssue.blocking("F", "La densidad de la arena (F) debe ser positiva")
        if (h != null && h < 0) issues += CalcIssue.blocking("H", "La masa retenida (H) no puede ser negativa")
        if (iBulk != null && iBulk <= 0) issues += CalcIssue.blocking("I", "El parámetro bulk (I) debe ser positivo")
        if (j != null && j <= 0) issues += CalcIssue.blocking("J", "La masa húmeda extraída (J) debe ser positiva")
        if (h != null && j != null && j > 0 && h >= j) issues += CalcIssue.blocking("H", "La masa retenida (H) debe ser menor que la masa extraída (J)")
        if (l != null && l < 0) issues += CalcIssue.blocking("L", "La humedad no puede ser negativa")
        if (q != null && q <= 0) issues += CalcIssue.blocking("Q", "La densidad seca máxima (Q) debe ser positiva")
        if (r != null && r < 0) issues += CalcIssue.blocking("R", "La humedad óptima no puede ser negativa")
        val blockingFields = issues.filter { it.severity == Severity.BLOCKING }.map { it.field }.toSet()
        fun inputBlock(vararg symbols: String): String? {
            val bad = symbols.filter { it in blockingFields }
            return if (bad.isEmpty()) null else "Entrada inválida: ${bad.joinToString(", ")}"
        }

        // C = A − B
        run {
            val why = missing("A" to a, "B" to b) ?: inputBlock("A")
            put(StepDefs.C, if (why == null) a!! - b!! else null, why, "${f(a)} − ${f(b)}")
        }
        // E = C − D
        run {
            val why = blocked("C") ?: missing("D" to d) ?: inputBlock("D")
            val v = if (why == null) steps["C"]!!.value!! - d!! else null
            val reason = why ?: if (v != null && v <= 0) "La arena en el hueco (E) debe ser positiva; revise A, B y D" else null
            if (why == null && reason != null) issues += CalcIssue.blocking("E", reason)
            put(StepDefs.E, v, reason, "${f(steps["C"]?.value)} − ${f(d)}")
        }
        // G = E / F
        run {
            val why = blocked("E") ?: missing("F" to fSand) ?: inputBlock("F")
            put(StepDefs.G, if (why == null) steps["E"]!!.value!! / fSand!! else null, why, "${f(steps["E"]?.value)} / ${f(fSand)}")
        }
        // K = H / J × 100
        run {
            val why = missing("H" to h, "J" to j) ?: inputBlock("H", "J")
            put(StepDefs.K, if (why == null) h!! / j!! * 100.0 else null, why, "${f(h)} / ${f(j)} × 100")
        }
        // M = J / G × 1000
        run {
            val why = blocked("G") ?: missing("J" to j) ?: inputBlock("J")
            put(StepDefs.M, if (why == null) j!! / steps["G"]!!.value!! * 1000.0 else null, why, "${f(j)} / ${f(steps["G"]?.value)} × 1000")
        }
        // N = ((100 × L) − (wc × K)) / (100 − K)
        run {
            val why = blocked("K") ?: missing("L" to l) ?: inputBlock("L")
            val k = steps["K"]?.value
            put(StepDefs.N, if (why == null) ((100.0 * l!!) - (profile.wc * k!!)) / (100.0 - k) else null, why,
                "((100 × ${f(l)}) − (${f(profile.wc)} × ${f(k)})) / (100 − ${f(k)})")
        }
        // O = M / (1 + L)
        run {
            val why = blocked("M") ?: missing("L" to l) ?: inputBlock("L")
            put(StepDefs.O, if (why == null) steps["M"]!!.value!! / (1.0 + l!!) else null, why, "${f(steps["M"]?.value)} / (1 + ${f(l)})")
        }
        // P = [O × (100 − K)] / [100 − ((O × K) / (I × 1000))]
        run {
            val why = blocked("O", "K") ?: missing("I" to iBulk) ?: inputBlock("I")
            val o = steps["O"]?.value; val k = steps["K"]?.value
            var reason = why
            var v: Double? = null
            if (why == null) {
                val den = 100.0 - ((o!! * k!!) / (iBulk!! * 1000.0))
                if (den <= 0.0 || !den.isFinite()) {
                    reason = "Denominador nulo o físicamente inválido en P (100 − O·K/(I·1000) = ${f(den)})"
                    issues += CalcIssue.blocking("P", reason)
                } else v = (o * (100.0 - k)) / den
            }
            put(StepDefs.P, v, reason, "[${f(o)} × (100 − ${f(k)})] / [100 − ((${f(o)} × ${f(k)}) / (${f(iBulk)} × 1000))]")
        }
        // S = P / Q × 100
        run {
            val why = blocked("P") ?: missing("Q" to q) ?: inputBlock("Q")
            put(StepDefs.S, if (why == null) steps["P"]!!.value!! / q!! * 100.0 else null, why, "${f(steps["P"]?.value)} / ${f(q)} × 100")
        }
        // ΔW = L − R (diferencia respecto de la humedad óptima, puntos porcentuales)
        run {
            val why = missing("L" to l, "R" to r) ?: inputBlock("L", "R")
            put(StepDefs.DW, if (why == null) (l!! - r!!) * 100.0 else null, why, "(${f(l)} − ${f(r)}) × 100")
        }

        // ---- Advertencias de plausibilidad (no bloquean) ----
        if (fSand != null && fSand > 0 && (fSand < 1.2 || fSand > 1.8)) issues += CalcIssue.warning("F", "Densidad de arena fuera del rango habitual 1,2–1,8 g/cm³")
        if (iBulk != null && iBulk > 0 && (iBulk < 2.0 || iBulk > 3.2)) issues += CalcIssue.warning("I", "Parámetro bulk fuera del rango habitual 2,0–3,2")
        if (l != null && l > 0.40) issues += CalcIssue.warning("L", "Humedad superior al 40 %: verifique que se digitó en porcentaje")
        if (l != null && l in 0.0..0.0015 && l > 0) issues += CalcIssue.warning("L", "Humedad muy baja: ¿se digitó como fracción en vez de porcentaje?")
        steps["K"]?.value?.let { if (it > 50) issues += CalcIssue.warning("K", "Retenido en ¾″ superior al 50 %: la corrección puede no ser aplicable") }
        steps["S"]?.value?.let { if (it < 70 || it > 110) issues += CalcIssue.warning("S", "Compactación fuera del rango plausible 70–110 %") }
        steps["G"]?.value?.let { if (it < 300) issues += CalcIssue.warning("G", "Volumen de hueco muy pequeño (< 300 cm³)") }

        return LegacyResult(engine = ENGINE_FULL_ID, profile = profile, steps = steps.values.toList(), issues = issues)
    }
}

/** Entradas del modelo heredado. Humedades como fracción decimal (0,06 = 6 %). */
@Serializable
data class LegacyInputs(
    val a: Double?, val b: Double?, val d: Double?, val f: Double?, val h: Double?,
    val i: Double?, val j: Double?, val l: Double?, val q: Double?, val r: Double?,
)

/** Parámetros del perfil heredado. wc = 0,02 es un supuesto del Excel PENDIENTE de validación técnica. */
@Serializable
data class LegacyProfile(
    val id: String = "perfil-heredado-excel",
    val wc: Double = 0.02,
    val validated: Boolean = false,
    val note: String = "wc = 0,02 tomado del Excel de origen; supuesto pendiente de validación técnica.",
) { companion object { val DEFAULT = LegacyProfile() } }

enum class Severity { BLOCKING, WARNING }

@Serializable
data class CalcIssue(val field: String, val severity: Severity, val message: String) {
    companion object {
        fun blocking(field: String, msg: String) = CalcIssue(field, Severity.BLOCKING, msg)
        fun warning(field: String, msg: String) = CalcIssue(field, Severity.WARNING, msg)
    }
}

@Serializable
data class TraceStep(
    val symbol: String, val name: String, val unit: String, val formula: String,
    val substituted: String?, val value: Double?, val unavailableReason: String?, val dependsOn: List<String>,
)

@Serializable
data class LegacyResult(val engine: String, val profile: LegacyProfile, val steps: List<TraceStep>, val issues: List<CalcIssue>) {
    fun value(symbol: String): Double? = steps.firstOrNull { it.symbol == symbol }?.value
    fun step(symbol: String): TraceStep? = steps.firstOrNull { it.symbol == symbol }
    val blocking get() = issues.filter { it.severity == Severity.BLOCKING }
    val warnings get() = issues.filter { it.severity == Severity.WARNING }
    /** Completo = sin errores bloqueantes y con compactación calculada. */
    val isComplete get() = blocking.isEmpty() && value("S") != null && value("N") != null
    val correctedDryDensity get() = value("P")
    val compactionPercent get() = value("S")
}

data class StepDef(val symbol: String, val name: String, val unit: String, val formula: String, val dependsOn: List<String>)

object StepDefs {
    val C = StepDef("C", "Arena total utilizada", "g", "C = A − B", listOf("A", "B"))
    val E = StepDef("E", "Arena en el hueco", "g", "E = C − D", listOf("C", "D"))
    val G = StepDef("G", "Volumen del hueco", "cm³", "G = E / F", listOf("E", "F"))
    val K = StepDef("K", "Retenido en tamiz ¾″", "% (0–100)", "K = (H / J) × 100", listOf("H", "J"))
    val M = StepDef("M", "Densidad húmeda de campo", "kg/m³", "M = (J / G) × 1000", listOf("J", "G"))
    val N = StepDef("N", "Humedad de partículas finas", "fracción", "N = ((100 × L) − (wc × K)) / (100 − K)", listOf("L", "K", "wc"))
    val O = StepDef("O", "Densidad seca total", "kg/m³", "O = M / (1 + L)", listOf("M", "L"))
    val P = StepDef("P", "Densidad seca corregida de partículas finas (Di)", "kg/m³", "P = [O × (100 − K)] / [100 − ((O × K) / (I × 1000))]", listOf("O", "K", "I"))
    val S = StepDef("S", "Compactación", "% (0–100)", "S = (P / Q) × 100", listOf("P", "Q"))
    val DW = StepDef("ΔW", "Diferencia de humedad respecto de la óptima", "puntos %", "ΔW = (L − R) × 100", listOf("L", "R"))
    val all = listOf(C, E, G, K, M, N, O, P, S, DW)
}

/** Definición de las entradas para documentación, formularios e informes. */
object InputDefs {
    data class Def(val symbol: String, val name: String, val unit: String, val note: String? = null)
    val list = listOf(
        Def("A", "Masa inicial de frasco y arena", "g"),
        Def("B", "Masa final de frasco y arena", "g"),
        Def("D", "Constante del cono", "g"),
        Def("F", "Densidad de la arena", "g/cm³"),
        Def("H", "Masa retenida en tamiz de ¾″", "g"),
        Def("I", "Parámetro bulk (heredado)", "adimensional ×1000 kg/m³",
            "El Excel lo denomina 'gravedad específica bulk' pero indica g/cm³. Se usa numéricamente como I×1000 en kg/m³. Requiere revisión técnica."),
        Def("J", "Masa húmeda del material extraído", "g"),
        Def("L", "Humedad total", "%", "Se digita en porcentaje y se almacena como fracción (6 % → 0,06)."),
        Def("Q", "Densidad seca máxima de laboratorio (De)", "kg/m³"),
        Def("R", "Humedad óptima", "%", "Se digita en porcentaje y se almacena como fracción."),
    )
    fun get(symbol: String) = list.first { it.symbol == symbol }
}
