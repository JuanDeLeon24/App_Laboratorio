package com.geodensidad.core.criteria

import com.geodensidad.core.number.Fmt
import com.geodensidad.core.stats.Descriptive
import kotlinx.serialization.Serializable

/**
 * Criterio estadístico versionado y configurable del tipo  Dm − k(n)·s ≥ α·De.
 * No es una certificación normativa incorporada: mientras validated = false toda evaluación
 * se rotula "evaluación provisional; criterio no validado".
 */
@Serializable
data class StatisticalCriterion(
    val id: String,
    val code: String,
    val version: String,
    val name: String,
    /** Coeficientes k por tamaño exacto n. Sin interpolación ni extrapolación. */
    val kTable: Map<Int, Double>,
    val alpha: Double,
    val legacy: Boolean,
    val validated: Boolean,
    val sourceDocument: String? = null,
    val edition: String? = null,
    val article: String? = null,
    val reviewedBy: String? = null,
    val enabledAt: String? = null,
    val notes: String? = null,
) {
    val expression get() = "Dm − k(n)·s ≥ ${Fmt.num(alpha, 2)}·De"

    companion object {
        /** Tabla heredada del Excel de origen ('Cono de arena'!C43:I44), pendiente de validación. */
        val LEGACY = StatisticalCriterion(
            id = "crit-legacy-excel-220-5-2-2",
            code = "LEGACY-DM-KS",
            version = "1.0.0",
            name = "Criterio heredado del formato Excel (Dm − k·s ≥ 0,95·De)",
            kTable = mapOf(5 to 0.685, 6 to 0.602, 7 to 0.544, 8 to 0.500, 9 to 0.465, 10 to 0.437),
            alpha = 0.95,
            legacy = true,
            validated = false,
            sourceDocument = "Formato Excel 'Densidades' (texto: 'ESPECIFICACIÓN: ARTÍCULO 220.5.2.2')",
            article = "220.5.2.2 (según el formato; no verificado contra la norma)",
            notes = "Tabla k(n) y α = 0,95 transcritos del Excel. En el Excel las referencias al coeficiente estaban rotas (#REF!). Pendiente de contraste con la documentación técnica vigente.",
        )
    }
}

@Serializable
enum class LotEvaluationStatus(val label: String) {
    NOT_EVALUABLE("No evaluable"),
    PROVISIONAL_MEETS("Evaluación provisional: cumple (criterio no validado)"),
    PROVISIONAL_FAILS("Evaluación provisional: no cumple (criterio no validado)"),
    MEETS("Cumple el criterio validado"),
    FAILS("No cumple el criterio validado"),
}

/** Observación de un lote: densidad seca corregida Di y la De de laboratorio con que se calculó. */
@Serializable
data class LotObservation(
    val sampleId: String,
    val sampleCode: String,
    val di: Double?,
    val de: Double?,
    val compactionPercent: Double?,
    val materialKey: String?,
    val labRefKey: String?,
    val documentStatusEligible: Boolean,
    val excluded: Boolean,
    val exclusionReason: String? = null,
)

@Serializable
data class LotEvaluation(
    val status: LotEvaluationStatus,
    val reasons: List<String>,
    val registered: Int,
    val included: Int,
    val excluded: Int,
    val pending: Int,
    val stats: Descriptive,
    val compactionStats: Descriptive,
    val k: Double?,
    val de: Double?,
    val leftSide: Double?,
    val rightSide: Double?,
    val criterionCode: String?,
    val criterionVersion: String?,
    val criterionValidated: Boolean,
    /** Indicador descriptivo: cuántas Di individuales alcanzan α·De. NO es aceptación del lote. */
    val individualAboveThreshold: Int?,
    val excludedDetail: List<Pair<String, String>>,
    val pendingDetail: List<Pair<String, String>>,
)

object LotEvaluator {
    fun evaluate(observations: List<LotObservation>, criterion: StatisticalCriterion?): LotEvaluation {
        val reasons = mutableListOf<String>()
        val excluded = observations.filter { it.excluded }
        val pending = observations.filter { !it.excluded && (!it.documentStatusEligible || it.di == null) }
        val included = observations.filter { !it.excluded && it.documentStatusEligible && it.di != null }
        val di = included.map { it.di!! }
        val stats = Descriptive.of(di)
        val compStats = Descriptive.of(included.mapNotNull { it.compactionPercent })

        val deValues = included.mapNotNull { it.de }.distinct()
        val labRefs = included.map { it.labRefKey }.distinct()
        val materials = included.map { it.materialKey }.distinct()
        val de = deValues.singleOrNull()

        if (included.isEmpty()) reasons += "No hay muestras incluidas con resultado vigente."
        if (deValues.size > 1 || labRefs.size > 1) reasons += "Las muestras incluidas usan referencias de laboratorio distintas (De): población incompatible."
        if (included.any { it.de == null }) reasons += "Hay muestras sin densidad seca máxima de laboratorio."
        if (materials.size > 1) reasons += "Las muestras incluidas corresponden a materiales distintos."
        if (pending.isNotEmpty()) reasons += "${pending.size} muestra(s) pendiente(s) (borrador, anulada o sin resultado) no se incluyen."
        var k: Double? = null
        if (criterion == null) reasons += "No hay criterio de evaluación habilitado para el lote."
        else {
            k = criterion.kTable[stats.n]
            if (k == null && stats.n > 0) reasons += "El criterio no define k para n = ${stats.n}; no se interpola ni extrapola."
            if (stats.sampleSd == null && stats.n > 0) reasons += "Desviación estándar no disponible (n < 2)."
        }

        val incompatible = deValues.size > 1 || labRefs.size > 1 || materials.size > 1 || included.any { it.de == null }
        val canEvaluate = criterion != null && k != null && de != null && stats.mean != null && stats.sampleSd != null && !incompatible
        var left: Double? = null; var right: Double? = null
        val status = if (!canEvaluate) LotEvaluationStatus.NOT_EVALUABLE else {
            left = stats.mean!! - k!! * stats.sampleSd!!
            right = criterion!!.alpha * de!!
            val ok = left >= right
            when {
                criterion.validated && ok -> LotEvaluationStatus.MEETS
                criterion.validated -> LotEvaluationStatus.FAILS
                ok -> LotEvaluationStatus.PROVISIONAL_MEETS
                else -> LotEvaluationStatus.PROVISIONAL_FAILS
            }
        }
        val above = if (criterion != null && de != null && !incompatible) included.count { it.di!! >= criterion.alpha * de } else null
        return LotEvaluation(
            status = status, reasons = reasons, registered = observations.size, included = included.size,
            excluded = excluded.size, pending = pending.size, stats = stats, compactionStats = compStats, k = k, de = de,
            leftSide = left, rightSide = right, criterionCode = criterion?.code, criterionVersion = criterion?.version,
            criterionValidated = criterion?.validated == true, individualAboveThreshold = above,
            excludedDetail = excluded.map { it.sampleCode to (it.exclusionReason ?: "Sin motivo registrado") },
            pendingDetail = pending.map { it.sampleCode to (if (!it.documentStatusEligible) "Estado documental no apto" else "Sin resultado vigente") },
        )
    }
}
