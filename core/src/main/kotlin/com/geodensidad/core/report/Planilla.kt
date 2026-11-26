package com.geodensidad.core.report

import com.geodensidad.core.calc.LegacyResult
import com.geodensidad.core.criteria.LotEvaluator
import com.geodensidad.core.criteria.LotObservation
import com.geodensidad.core.criteria.StatisticalCriterion
import com.geodensidad.core.number.Fmt
import kotlinx.serialization.Serializable

/** Una fila de la planilla: símbolo, descripción tal como en el Excel y un valor por densidad. */
@Serializable
data class FilaPlanilla(val simbolo: String, val nombre: String, val valores: List<String>, val calculada: Boolean)

@Serializable
data class PlanillaData(
    val template: TemplateInfo,
    val issue: IssueInfo,
    val encabezado: List<KV>,
    val columnas: List<String>,
    val filas: List<FilaPlanilla>,
    val resumen: List<KV>,
    val notas: List<String>,
    val observaciones: String,
    val elaboro: String,
    val aprobo: String,
    /** Imágenes PNG de las firmas manuscritas (ruta, ancho, alto en px); null = sin firma. */
    val firmaElaboro: Triple<String, Int, Int>? = null,
    val firmaAprobo: Triple<String, Int, Int>? = null,
)

/** Construye la planilla (filas del Excel) y su resumen a partir de los resultados del motor. */
object PlanillaBuilder {
    /** Entradas digitadas por columna (texto ya formateado) + resultados del motor. */
    data class Columna(
        val numero: Int, val capa: String, val espesor: String, val pr: String, val costado: String,
        val a: Double?, val b: Double?, val d: Double?, val f: Double?, val h: Double?, val i: Double?, val j: Double?, val l: Double?,
        val resultado: LegacyResult,
    )

    fun filas(cols: List<Columna>, q: Double?, r: Double?): List<FilaPlanilla> {
        fun n(v: Double?, dec: Int) = Fmt.num(v, dec)
        fun res(c: Columna, s: String, dec: Int) = c.resultado.value(s)?.let { Fmt.num(it, dec) } ?: "—"
        fun row(sym: String, name: String, calc: Boolean, f: (Columna) -> String) = FilaPlanilla(sym, name, cols.map(f), calc)
        return listOf(
            row("", "Capa No.", false) { it.capa },
            row("", "Espesor capa", false) { it.espesor },
            row("", "PR", false) { it.pr },
            row("", "Costado", false) { it.costado },
            row("A", "Peso frasco y arena inicial (g)", false) { n(it.a, 0) },
            row("B", "Peso frasco y arena restante (g)", false) { n(it.b, 0) },
            row("C", "Peso arena total usada (g) = A − B", true) { res(it, "C", 0) },
            row("D", "Constante del cono (g)", false) { n(it.d, 0) },
            row("E", "Peso arena en el hueco (g) = C − D", true) { res(it, "E", 0) },
            row("F", "Densidad de la arena (g/cm³)", false) { n(it.f, 3) },
            row("G", "Volumen del hueco (cm³) = E / F", true) { res(it, "G", 2) },
            row("H", "Material retenido en 3/4 (g)", false) { n(it.h, 0) },
            row("I", "Gravedad específica bulk (g/cm³)", false) { n(it.i, 3) },
            row("J", "Peso material extraído húmedo (g)", false) { n(it.j, 0) },
            row("K", "% retenido 3/4 = H / J × 100", true) { res(it, "K", 2) },
            row("L", "Humedad (decimal)", false) { n(it.l, 3) },
            row("M", "Densidad húmeda de campo (kg/m³) = J / G × 1000", true) { res(it, "M", 2) },
            row("N", "Humedad part. fina (decimal) = ((100·L) − (0,02·K)) / (100 − K)", true) { res(it, "N", 4) },
            row("O", "Densidad seca de campo muestra total (kg/m³) = M / (1 + L)", true) { res(it, "O", 2) },
            row("P", "Densidad seca corregida part. finas Di (kg/m³)", true) { res(it, "P", 2) },
            row("Q", "Densidad máxima de laboratorio De (kg/m³)", false) { n(q, 0) },
            row("R", "Humedad óptima (decimal)", false) { n(r, 4) },
            row("S", "Compactación del terreno (%) = P / Q × 100", true) { res(it, "S", 2) },
        )
    }

    /** Resumen de la hoja: promedios y criterio Dm − k·s ≥ 0,95·De sobre TODAS las densidades con resultado. */
    fun resumen(cols: List<Columna>, q: Double?): Pair<List<KV>, List<String>> {
        val ok = cols.filter { it.resultado.value("P") != null && it.resultado.value("S") != null }
        val obs = ok.map { LotObservation(it.numero.toString(), it.numero.toString(), it.resultado.value("P"), q, it.resultado.value("S"), "x", "x", true, false) }
        val e = LotEvaluator.evaluate(obs, StatisticalCriterion.LEGACY)
        val kv = listOf(
            "Densidades con resultado (n)" to "${ok.size} de ${cols.size}",
            "Compactación promedio" to (e.compactionStats.mean?.let { "${Fmt.num(it, 2)} %" } ?: "—"),
            "Dm (promedio de Di)" to (e.stats.mean?.let { "${Fmt.num(it, 2)} kg/m³" } ?: "—"),
            "s (desviación estándar)" to (e.stats.sampleSd?.let { "${Fmt.num(it, 2)} kg/m³" } ?: "—"),
            "k (n = ${e.stats.n})" to (e.k?.let { Fmt.num(it, 3) } ?: "— (tabla solo para n de 5 a 10)"),
            "Dm − k·s" to (e.leftSide?.let { "${Fmt.num(it, 2)} kg/m³" } ?: "—"),
            "0,95·De" to (e.rightSide?.let { "${Fmt.num(it, 2)} kg/m³" } ?: q?.let { "${Fmt.num(it * 0.95, 2)} kg/m³" } ?: "—"),
            "Resultado" to when {
                e.leftSide == null -> "No evaluable"
                e.leftSide!! >= e.rightSide!! -> "CUMPLE (Dm − k·s ≥ 0,95·De)"
                else -> "NO CUMPLE (Dm − k·s < 0,95·De)"
            },
        )
        val notas = listOf("Criterio del formato: Dm − k·s ≥ 0,95·De (Art. 220.5.2.2 según el formato; tabla k transcrita del Excel, no verificada contra la norma).")
        return kv to notas
    }
}

object PlanillaRenderer {
    fun render(d: PlanillaData, m: TextMeasurer): PageList {
        val l = Layout(m)
        l.keyValues(d.encabezado, cols = 2)
        val cols = listOf(Layout.Col("", 0.35f), Layout.Col("Densidad No.", 4.2f)) + d.columnas.map { Layout.Col(it, 1f, alignRight = true) }
        val st = TextStyle(if (d.columnas.size > 6) 6.4f else 7.2f)
        val fill = d.filas.map { if (it.calculada) RColor.MIST else null }
        l.table(cols, d.filas.map { listOf(it.simbolo.ifEmpty { " " }, it.nombre) + it.valores.map { v -> v.ifEmpty { " " } } }, st) { i -> fill[i] }
        l.paragraph("Filas sombreadas: calculadas con las fórmulas del formato. Filas en blanco: datos digitados.", Styles.small)
        l.heading("Resumen", 2)
        l.keyValues(d.resumen, cols = 2)
        d.notas.forEach { l.note(it, RColor.STEEL) }
        l.heading("Observaciones", 2)
        l.paragraph(d.observaciones.ifBlank { " " })
        l.space(6f)
        l.firmas(listOf(
            Layout.FirmaCaja("ELABORÓ", d.elaboro, d.firmaElaboro?.first, d.firmaElaboro?.second ?: 0, d.firmaElaboro?.third ?: 0),
            Layout.FirmaCaja("APROBÓ", d.aprobo, d.firmaAprobo?.first, d.firmaAprobo?.second ?: 0, d.firmaAprobo?.third ?: 0),
        ))
        return ReportRenderer.decorate(l, d.template, d.issue)
    }
}
