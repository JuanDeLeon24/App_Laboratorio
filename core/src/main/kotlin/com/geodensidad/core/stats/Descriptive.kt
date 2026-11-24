package com.geodensidad.core.stats

import kotlinx.serialization.Serializable
import kotlin.math.sqrt

/**
 * Estadística descriptiva sobre un conjunto EXPLÍCITO de observaciones.
 * n, media, mínimo, máximo y desviación se calculan siempre sobre la misma población.
 * Para n < 2 la desviación estándar muestral es "no disponible" (null), nunca cero.
 */
@Serializable
data class Descriptive(
    val n: Int,
    val mean: Double?,
    val min: Double?,
    val max: Double?,
    val sampleSd: Double?,
    /** Coeficiente de variación en % (sd/media×100); null si no aplica. */
    val cvPercent: Double?,
) {
    companion object {
        fun of(values: List<Double>): Descriptive {
            val v = values.filter { it.isFinite() }
            require(v.size == values.size) { "Se recibieron valores no finitos" }
            val n = v.size
            if (n == 0) return Descriptive(0, null, null, null, null, null)
            val mean = v.sum() / n
            val sd = if (n >= 2) sqrt(v.sumOf { (it - mean) * (it - mean) } / (n - 1)) else null
            val cv = if (sd != null && mean > 0) sd / mean * 100.0 else null
            return Descriptive(n, mean, v.min(), v.max(), sd, cv)
        }
    }
}

/** Histograma honesto: solo se construye con n ≥ minN; en otro caso se devuelve null. */
object Histogram {
    data class Bin(val from: Double, val to: Double, val count: Int)
    fun build(values: List<Double>, binWidth: Double, minN: Int = 5): List<Bin>? {
        if (values.size < minN || binWidth <= 0) return null
        val lo = kotlin.math.floor(values.min() / binWidth) * binWidth
        val hi = kotlin.math.ceil(values.max() / binWidth) * binWidth
        val count = maxOf(1, ((hi - lo) / binWidth).toInt().let { if (lo + it * binWidth < hi) it + 1 else it })
        val bins = (0 until maxOf(count, 1)).map { Bin(lo + it * binWidth, lo + (it + 1) * binWidth, 0) }.toMutableList()
        for (x in values) {
            val idx = ((x - lo) / binWidth).toInt().coerceIn(0, bins.lastIndex)
            bins[idx] = bins[idx].copy(count = bins[idx].count + 1)
        }
        return bins
    }
}
