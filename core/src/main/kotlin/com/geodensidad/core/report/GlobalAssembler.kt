package com.geodensidad.core.report

import com.geodensidad.core.criteria.StatisticalCriterion
import com.geodensidad.core.number.Fmt
import com.geodensidad.core.stats.Descriptive
import com.geodensidad.core.stats.Histogram
import kotlinx.serialization.Serializable

/** Fila plana de una muestra para estadísticas, tablas y gráficos (revisión vigente). */
@Serializable
data class SampleRecord(
    val id: String,
    val code: String,
    val sequence: Int,
    val dateText: String,
    val abscisaText: String?,
    val abscisaMeters: Double?,
    val side: String?,
    val layer: String?,
    val material: String?,
    val lotCode: String?,
    val statusLabel: String,
    /** Estado documental apto para cálculos (completa, revisada o aprobada). */
    val eligible: Boolean,
    val p: Double?,
    val s: Double?,
    val moisture: Double?,
    val optimumMoisture: Double?,
    val de: Double?,
    val labRefKey: String?,
    val revision: Int,
    val extraColumns: List<String> = emptyList(),
) {
    val included get() = eligible && p != null && s != null
    val notIncludedReason get() = when {
        !eligible -> "Estado documental «$statusLabel»: no se incluye en cálculos"
        p == null || s == null -> "Sin resultado vigente (datos incompletos o inválidos)"
        else -> null
    }
}

object GlobalAssembler {
    val baseColumns = listOf("Muestra", "Fecha", "Abscisa", "Costado", "Capa", "Material", "Lote", "Di (kg/m³)", "Comp. (%)", "Humedad (%)", "Estado")

    fun rows(records: List<SampleRecord>): List<List<String>> = records.map { r ->
        listOf(
            "${r.code} (r${r.revision})", r.dateText, r.abscisaText ?: "—", r.side ?: "—", r.layer ?: "—", r.material ?: "—", r.lotCode ?: "—",
            Fmt.num(r.p, 2), Fmt.num(r.s, 2), r.moisture?.let { Fmt.num(it * 100, 2) } ?: "—", r.statusLabel,
        ) + r.extraColumns
    }

    fun coverage(records: List<SampleRecord>): List<KV> {
        val incl = records.count { it.included }
        return listOf(
            "Registradas en el alcance" to records.size.toString(),
            "Completas / revisadas / aprobadas" to records.count { it.eligible }.toString(),
            "Incluidas en cálculos" to incl.toString(),
            "No incluidas (pendientes, anuladas o sin resultado)" to (records.size - incl).toString(),
        )
    }

    fun notIncluded(records: List<SampleRecord>) = records.filter { !it.included }.map { listOf(it.code, it.statusLabel, it.notIncludedReason ?: "") }

    /** Grupos compatibles: mismo material y misma referencia de laboratorio. Nunca se mezclan. */
    fun groups(records: List<SampleRecord>): List<StatGroup> =
        records.filter { it.included }.groupBy { (it.material ?: "Sin material") to (it.labRefKey ?: "Sin referencia") }
            .map { (key, rs) ->
                val p = Descriptive.of(rs.map { it.p!! })
                val s = Descriptive.of(rs.map { it.s!! })
                val dw = rs.mapNotNull { r -> if (r.moisture != null && r.optimumMoisture != null) (r.moisture - r.optimumMoisture) * 100 else null }
                val w = Descriptive.of(rs.mapNotNull { it.moisture?.times(100) })
                val dws = Descriptive.of(dw)
                StatGroup(
                    title = "Material: ${key.first} · Referencia: ${key.second} (n = ${p.n})",
                    pairs = listOf(
                        "Observaciones (n)" to p.n.toString(),
                        "Di media" to Fmt.withUnit(p.mean, 2, "kg/m³"),
                        "Di mínima / máxima" to "${Fmt.num(p.min, 2)} / ${Fmt.num(p.max, 2)} kg/m³",
                        "Di desviación estándar (s)" to (p.sampleSd?.let { Fmt.withUnit(it, 2, "kg/m³") } ?: "No disponible (n < 2)"),
                        "Di coeficiente de variación" to (p.cvPercent?.let { "${Fmt.num(it, 2)} %" } ?: "No disponible"),
                        "Compactación media" to Fmt.withUnit(s.mean, 2, "%"),
                        "Compactación mínima / máxima" to "${Fmt.num(s.min, 2)} / ${Fmt.num(s.max, 2)} %",
                        "Compactación s" to (s.sampleSd?.let { Fmt.withUnit(it, 2, "%") } ?: "No disponible (n < 2)"),
                        "Humedad media" to (w.mean?.let { Fmt.withUnit(it, 2, "%") } ?: "—"),
                        "Diferencia media con humedad óptima" to (dws.mean?.let { "${Fmt.num(it, 2)} puntos % (n = ${dws.n})" } ?: "—"),
                    ),
                    note = "Estadísticos calculados sobre las mismas ${p.n} observaciones; no se promedian porcentajes de lotes.",
                )
            }

    /**
     * Gráficos mínimos. La línea de referencia solo se dibuja si hay un criterio seleccionado
     * y todas las observaciones comparten la misma De.
     */
    fun charts(records: List<SampleRecord>, criterion: StatisticalCriterion?): List<ChartSpec> {
        val inc = records.filter { it.included }.sortedBy { it.sequence }
        val n = inc.size
        val excludedCount = records.size - n
        val foot = "n = $n observaciones incluidas; $excludedCount no incluidas."
        val sameDe = inc.mapNotNull { it.de }.distinct().size == 1 && inc.all { it.de != null }
        val ref = if (criterion != null && sameDe && n > 0) listOf(RefLine(criterion.alpha * 100, "α·De = ${Fmt.num(criterion.alpha * 100, 1)} % (${criterion.code} v${criterion.version}${if (!criterion.validated) ", no validado" else ""})")) else emptyList()
        val out = mutableListOf<ChartSpec>()
        out += ChartSpec(
            title = "Compactación por secuencia de ensayo", xLabel = "Muestra (orden de ensayo)", yLabel = "Compactación (%)",
            series = listOf(ChartSeries("Compactación", SeriesKind.POINTS, inc.mapIndexed { i, r -> ChartPoint(i + 1.0, r.s!!, r.code) }, RColor.NAVY)),
            refLines = ref, xCategories = inc.mapIndexed { i, r -> (i + 1) to r.code }.toMap(), footnote = foot,
        )
        val withAbs = inc.filter { it.abscisaMeters != null }
        if (withAbs.isNotEmpty()) out += ChartSpec(
            title = "Compactación por abscisa", xLabel = "Abscisa (m)", yLabel = "Compactación (%)",
            series = listOf(ChartSeries("Compactación", SeriesKind.POINTS, withAbs.map { ChartPoint(it.abscisaMeters!!, it.s!!, it.code) }, RColor.NAVY)),
            refLines = ref, footnote = "n = ${withAbs.size} con abscisa numérica interpretable; ${n - withAbs.size} sin abscisa numérica.",
        )
        val hist = Histogram.build(inc.map { it.s!! }, 1.0)
        out += if (hist != null) ChartSpec(
            title = "Distribución de la compactación (intervalos de 1 %)", xLabel = "Compactación (%)", yLabel = "Frecuencia",
            series = listOf(ChartSeries("Frecuencia", SeriesKind.BARS, hist.map { ChartPoint(it.from, it.count.toDouble()) }, RColor.SKY, 1.0)),
            footnote = foot, xDecimals = 0, yDecimals = 0,
        ) else ChartSpec(
            title = "Compactación: valores individuales (n < 5, no se construye distribución)", xLabel = "Compactación (%)", yLabel = "Observación",
            series = listOf(ChartSeries("Valor", SeriesKind.POINTS, inc.mapIndexed { i, r -> ChartPoint(r.s!!, i + 1.0) }, RColor.NAVY)),
            footnote = foot, xDecimals = 1, yDecimals = 0,
        )
        val withW = inc.filter { it.moisture != null }
        out += ChartSpec(
            title = "Humedad de campo frente a humedad óptima", xLabel = "Muestra (orden de ensayo)", yLabel = "Humedad (%)",
            series = listOf(
                ChartSeries("Humedad de campo", SeriesKind.POINTS, withW.mapIndexed { i, r -> ChartPoint(i + 1.0, r.moisture!! * 100) }, RColor.NAVY),
                ChartSeries("Humedad óptima", SeriesKind.POINTS, withW.mapIndexedNotNull { i, r -> r.optimumMoisture?.let { ChartPoint(i + 1.0, it * 100) } }, RColor.WARN),
            ),
            xCategories = withW.mapIndexed { i, r -> (i + 1) to r.code }.toMap(), footnote = "n = ${withW.size}.", yDecimals = 1,
        )
        return out
    }
}
