package com.geodensidad.core.report

import kotlinx.serialization.Serializable

typealias KV = Pair<String, String>

/** Plantilla documental versionada. Las rutas de imagen apuntan a copias inmutables por versión. */
@Serializable
data class TemplateInfo(
    val templateVersion: String,
    val institution: String,
    val formTitle: String,
    val docCode: String,
    val docVersion: String,
    val docDate: String,
    val logoPath: String? = null,
    val logoPxW: Int = 0,
    val logoPxH: Int = 0,
    /** Imagen de la fórmula de desviación estándar del formato de origen ('Picture 1'). */
    val formulaImagePath: String? = null,
    val formulaPxW: Int = 0,
    val formulaPxH: Int = 0,
)

@Serializable
data class IssueInfo(
    val reportId: String,
    val reportCode: String,
    /** Fecha y hora de EMISIÓN del informe (distinta de la fecha de captura de las fotos). */
    val issuedAtText: String,
    val issuedBy: String?,
    val projectShort: String,
    val draft: Boolean,
    val engine: String,
    val appVersion: String,
)

@Serializable
data class PhotoBlock(val path: String, val pxW: Int, val pxH: Int, val caption: List<String>)

@Serializable
data class SampleReportData(
    val template: TemplateInfo,
    val issue: IssueInfo,
    val sampleCode: String,
    val revisionLabel: String,
    val documentStatus: String,
    val projectPairs: List<KV>,
    val journeyPairs: List<KV>,
    val locationPairs: List<KV>,
    /** filas: símbolo, descripción, valor, unidad */
    val measurementRows: List<List<String>>,
    /** filas: símbolo, descripción, valor, unidad, fórmula */
    val resultRows: List<List<String>>,
    val calibrationPairs: List<KV>,
    val labRefPairs: List<KV>,
    val criterionLines: List<String>,
    val technicalStatus: String,
    val warnings: List<String>,
    val observations: String?,
    val customPairs: List<KV>,
    val elaboratedBy: String?,
    val approvedBy: String?,
    val photos: List<PhotoBlock>,
    val photosPerPage: Int = 2,
)

@Serializable
data class LotBlock(val title: String, val pairs: List<KV>, val statusText: String, val favorable: Boolean?, val notes: List<String>)

@Serializable
data class StatGroup(val title: String, val pairs: List<KV>, val note: String? = null)

@Serializable
data class GlobalReportData(
    val template: TemplateInfo,
    val issue: IssueInfo,
    val title: String,
    val scopePairs: List<KV>,
    val executiveSummary: List<String>,
    val coveragePairs: List<KV>,
    val sampleColumns: List<String>,
    val sampleRows: List<List<String>>,
    val statGroups: List<StatGroup>,
    val charts: List<ChartSpec>,
    val lots: List<LotBlock>,
    /** filas: código, estado, motivo */
    val notIncludedRows: List<List<String>>,
    val warnings: List<String>,
    val observations: String?,
    val calibrationRows: List<List<String>>,
    val labRefRows: List<List<String>>,
    val photos: List<PhotoBlock>,
    val photosPerPage: Int = 2,
)
