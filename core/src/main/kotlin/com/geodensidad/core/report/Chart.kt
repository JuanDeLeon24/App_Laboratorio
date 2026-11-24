package com.geodensidad.core.report

import com.geodensidad.core.number.Fmt
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

@Serializable
enum class SeriesKind { POINTS, BARS }

@Serializable
data class ChartPoint(val x: Double, val y: Double, val label: String? = null)

@Serializable
data class ChartSeries(val name: String, val kind: SeriesKind, val points: List<ChartPoint>, val color: Int, val barWidth: Double = 1.0)

/** Línea de referencia: SOLO debe provenir de un criterio seleccionado, nunca decorativa. */
@Serializable
data class RefLine(val y: Double, val label: String, val color: Int = RColor.BAD)

@Serializable
data class ChartSpec(
    val title: String,
    val xLabel: String,
    val yLabel: String,
    val series: List<ChartSeries>,
    val refLines: List<RefLine> = emptyList(),
    /** Etiquetas categóricas para el eje x (p. ej. códigos de muestra) por valor x entero. */
    val xCategories: Map<Int, String>? = null,
    val footnote: String? = null,
    val xDecimals: Int = 0,
    val yDecimals: Int = 1,
)

object NiceScale {
    fun ticks(minV: Double, maxV: Double, maxTicks: Int = 6, integer: Boolean = false): List<Double> {
        var lo = minV; var hi = maxV
        if (lo == hi) { lo -= 1.0; hi += 1.0 }
        val range = nice(hi - lo, false)
        val step = nice(range / (maxTicks - 1), true).let { if (integer) maxOf(1.0, it) else it }
        val start = floor(lo / step) * step
        val end = ceil(hi / step) * step
        val out = mutableListOf<Double>()
        var v = start
        while (v <= end + step * 0.5) { out += if (abs(v) < step * 1e-9) 0.0 else v; v += step }
        return out
    }
    private fun nice(x: Double, round: Boolean): Double {
        val exp = floor(log10(x))
        val f = x / 10.0.pow(exp)
        val nf = if (round) when { f < 1.5 -> 1.0; f < 3 -> 2.0; f < 7 -> 5.0; else -> 10.0 }
        else when { f <= 1 -> 1.0; f <= 2 -> 2.0; f <= 5 -> 5.0; else -> 10.0 }
        return nf * 10.0.pow(exp)
    }
}

object ChartRenderer {
    fun draw(spec: ChartSpec, x: Float, y: Float, w: Float, h: Float, m: TextMeasurer, emit: (Cmd) -> Unit) {
        val ts = TextStyle(8.5f, bold = true, color = RColor.NAVY)
        val ax = TextStyle(6.5f, color = RColor.STEEL)
        emit(Cmd.Rect(x, y, w, h, RColor.WHITE, RColor.MIST, 0.6f))
        emit(Cmd.Text(spec.title, x + 6f, y + 12f, ts))
        val pts = spec.series.flatMap { it.points }
        val plotL = x + 46f; val plotR = x + w - 12f
        val plotT = y + 22f + if (spec.series.size > 1 || spec.refLines.isNotEmpty()) 10f else 0f
        val plotB = y + h - 30f - if (spec.footnote != null) 9f else 0f
        if (pts.isEmpty()) {
            emit(Cmd.Text("Sin observaciones para el filtro aplicado", x + w / 2 - 70f, y + h / 2, ax))
            return
        }
        val bars = spec.series.filter { it.kind == SeriesKind.BARS }
        var xMin = pts.minOf { it.x }; var xMax = pts.maxOf { it.x }
        if (bars.isNotEmpty()) { xMax += bars.maxOf { it.barWidth } }
        if (xMin == xMax) { xMin -= 1; xMax += 1 } else if (bars.isEmpty()) { val pad = (xMax - xMin) * 0.04; xMin -= pad; xMax += pad }
        val yVals = pts.map { it.y } + spec.refLines.map { it.y } + if (bars.isNotEmpty()) listOf(0.0) else emptyList()
        val yt = NiceScale.ticks(yVals.min(), yVals.max(), 6, integer = spec.yDecimals == 0)
        val yMin = yt.first(); val yMax = yt.last()
        fun px(v: Double) = (plotL + (v - xMin) / (xMax - xMin) * (plotR - plotL)).toFloat()
        fun py(v: Double) = (plotB - (v - yMin) / (yMax - yMin) * (plotB - plotT)).toFloat()
        // rejilla y ejes
        yt.forEach { t ->
            val yy = py(t)
            emit(Cmd.Line(plotL, yy, plotR, yy, RColor.MIST, 0.4f))
            val lbl = Fmt.num(t, spec.yDecimals)
            emit(Cmd.Text(lbl, plotL - 3f - m.textWidth(lbl, ax), yy + 2.2f, ax))
        }
        emit(Cmd.Line(plotL, plotT, plotL, plotB, RColor.SLATE, 0.6f))
        emit(Cmd.Line(plotL, plotB, plotR, plotB, RColor.SLATE, 0.6f))
        val cats = spec.xCategories
        if (cats != null) {
            val step = maxOf(1, ceil(cats.size / 14.0).toInt())
            cats.entries.sortedBy { it.key }.filterIndexed { i, _ -> i % step == 0 }.forEach { (k, lbl) ->
                val xx = px(k.toDouble())
                emit(Cmd.Line(xx, plotB, xx, plotB + 2f, RColor.SLATE, 0.4f))
                emit(Cmd.Text(lbl, xx - m.textWidth(lbl, ax) / 2, plotB + 9f, ax))
            }
        } else {
            NiceScale.ticks(xMin, xMax, 7).filter { it >= xMin && it <= xMax }.forEach { t ->
                val xx = px(t)
                emit(Cmd.Line(xx, plotB, xx, plotB + 2f, RColor.SLATE, 0.4f))
                val lbl = Fmt.num(t, spec.xDecimals)
                emit(Cmd.Text(lbl, xx - m.textWidth(lbl, ax) / 2, plotB + 9f, ax))
            }
        }
        emit(Cmd.Text(spec.xLabel, (plotL + plotR) / 2 - m.textWidth(spec.xLabel, ax) / 2, plotB + 19f, ax.copy(bold = true)))
        emit(Cmd.TextRot(spec.yLabel, x + 9f, (plotT + plotB) / 2 + m.textWidth(spec.yLabel, ax) / 2, ax.copy(bold = true), -90f))
        // series
        spec.series.forEach { s ->
            when (s.kind) {
                SeriesKind.BARS -> s.points.forEach { p ->
                    val x1 = px(p.x); val x2 = px(p.x + s.barWidth)
                    val top = py(p.y)
                    emit(Cmd.Rect(x1 + 1f, top, x2 - x1 - 2f, py(0.0) - top, s.color, RColor.NAVY_DEEP, 0.3f))
                    if (p.y > 0) emit(Cmd.Text(Fmt.num(p.y, 0), (x1 + x2) / 2 - 2f, top - 2f, ax))
                }
                SeriesKind.POINTS -> s.points.forEach { p -> emit(Cmd.Circle(px(p.x), py(p.y), 2.4f, s.color)) }
            }
        }
        spec.refLines.forEach { r ->
            val yy = py(r.y)
            emit(Cmd.Line(plotL, yy, plotR, yy, r.color, 0.8f, dashed = true))
        }
        // leyenda
        var lx = x + 50f
        val ly = y + 24f
        spec.series.filter { spec.series.size > 1 }.forEach { s ->
            emit(Cmd.Circle(lx, ly - 2f, 2.4f, s.color)); emit(Cmd.Text(s.name, lx + 5f, ly, ax)); lx += m.textWidth(s.name, ax) + 18f
        }
        spec.refLines.forEach { r ->
            emit(Cmd.Line(lx - 3f, ly - 2f, lx + 7f, ly - 2f, r.color, 0.8f, dashed = true))
            emit(Cmd.Text(r.label, lx + 10f, ly, ax)); lx += m.textWidth(r.label, ax) + 22f
        }
        spec.footnote?.let { emit(Cmd.Text(it, x + 6f, y + h - 5f, ax)) }
    }
}
