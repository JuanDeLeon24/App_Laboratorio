package com.geodensidad.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * Esquema Room v1. Reglas:
 *  - UUID internos inmutables como clave primaria.
 *  - Claves foráneas con RESTRICT: los registros referenciados no se eliminan, se archivan.
 *  - Las columnas "cache_*" de samples son derivadas de la revisión vigente (recalculables).
 * Diccionario de datos completo: docs/DICCIONARIO_DE_DATOS.md
 */

@Entity(tableName = "projects", indices = [Index(value = ["code"])])
data class ProjectEntity(
    @PrimaryKey val id: String,
    val code: String,
    val name: String,
    val client: String = "",
    val contractor: String = "",
    val location: String = "",
    val description: String = "",
    val responsibles: String = "",
    val status: String = "ACTIVE",
    @ColumnInfo(name = "is_demo") val isDemo: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "activities",
    foreignKeys = [ForeignKey(entity = ProjectEntity::class, parentColumns = ["id"], childColumns = ["project_id"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("project_id"), Index("status")],
)
data class ActivityEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "project_id") val projectId: String,
    val name: String,
    val description: String = "",
    @ColumnInfo(name = "test_type") val testType: String = "CONO_ARENA",
    @ColumnInfo(name = "default_material_id") val defaultMaterialId: String? = null,
    @ColumnInfo(name = "default_calibration_id") val defaultCalibrationId: String? = null,
    @ColumnInfo(name = "default_lab_ref_id") val defaultLabRefId: String? = null,
    @ColumnInfo(name = "default_criterion_id") val defaultCriterionId: String? = null,
    @ColumnInfo(name = "default_bulk") val defaultBulk: String? = null,
    @ColumnInfo(name = "sample_prefix") val samplePrefix: String = "DEN",
    val status: String = "OPEN",
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "last_opened_at") val lastOpenedAt: Long = 0,
)

@Entity(
    tableName = "journeys",
    foreignKeys = [ForeignKey(entity = ActivityEntity::class, parentColumns = ["id"], childColumns = ["activity_id"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("activity_id"), Index("work_date")],
)
data class JourneyEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "activity_id") val activityId: String,
    @ColumnInfo(name = "work_date") val workDate: String,
    val responsible: String = "",
    @ColumnInfo(name = "work_front") val workFront: String = "",
    val observations: String = "",
    val weather: String = "",
    val status: String = "OPEN",
    @ColumnInfo(name = "custom_json") val customJson: String = "{}",
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "lots",
    foreignKeys = [ForeignKey(entity = ActivityEntity::class, parentColumns = ["id"], childColumns = ["activity_id"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("activity_id"), Index(value = ["activity_id", "code"], unique = true)],
)
data class LotEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "activity_id") val activityId: String,
    val code: String,
    val description: String = "",
    @ColumnInfo(name = "material_id") val materialId: String? = null,
    val section: String = "",
    @ColumnInfo(name = "fill_part") val fillPart: String = "",
    @ColumnInfo(name = "lab_ref_id") val labRefId: String? = null,
    @ColumnInfo(name = "criterion_id") val criterionId: String? = null,
    @ColumnInfo(name = "review_status") val reviewStatus: String = "OPEN",
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "samples",
    foreignKeys = [
        ForeignKey(entity = ActivityEntity::class, parentColumns = ["id"], childColumns = ["activity_id"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = JourneyEntity::class, parentColumns = ["id"], childColumns = ["journey_id"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = LotEntity::class, parentColumns = ["id"], childColumns = ["lot_id"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("activity_id"), Index("journey_id"), Index("lot_id"), Index("status"), Index(value = ["activity_id", "code"], unique = true), Index("abscisa_m")],
)
data class SampleEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "activity_id") val activityId: String,
    @ColumnInfo(name = "journey_id") val journeyId: String,
    @ColumnInfo(name = "lot_id") val lotId: String? = null,
    val code: String,
    val seq: Int,
    val status: String,
    @ColumnInfo(name = "current_revision_id") val currentRevisionId: String,
    @ColumnInfo(name = "revision_number") val revisionNumber: Int,
    @ColumnInfo(name = "test_datetime") val testDateTime: String? = null,
    @ColumnInfo(name = "abscisa_text") val abscisaText: String? = null,
    @ColumnInfo(name = "abscisa_m") val abscisaMeters: Double? = null,
    val side: String? = null,
    val layer: String? = null,
    @ColumnInfo(name = "material_id") val materialId: String? = null,
    @ColumnInfo(name = "lab_ref_id") val labRefId: String? = null,
    @ColumnInfo(name = "calibration_id") val calibrationId: String? = null,
    @ColumnInfo(name = "cache_p") val cacheP: Double? = null,
    @ColumnInfo(name = "cache_s") val cacheS: Double? = null,
    @ColumnInfo(name = "cache_l") val cacheL: Double? = null,
    @ColumnInfo(name = "cache_r") val cacheR: Double? = null,
    @ColumnInfo(name = "cache_q") val cacheQ: Double? = null,
    @ColumnInfo(name = "cache_blocking") val cacheBlocking: Int = 0,
    @ColumnInfo(name = "void_reason") val voidReason: String? = null,
    @ColumnInfo(name = "lot_excluded") val lotExcluded: Boolean = false,
    @ColumnInfo(name = "lot_exclusion_reason") val lotExclusionReason: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "sample_revisions",
    foreignKeys = [ForeignKey(entity = SampleEntity::class, parentColumns = ["id"], childColumns = ["sample_id"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("sample_id"), Index(value = ["sample_id", "number"], unique = true)],
)
data class SampleRevisionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "sample_id") val sampleId: String,
    val number: Int,
    val status: String,
    /** Formulario completo (SampleForm) en JSON con decimales canónicos. */
    @ColumnInfo(name = "data_json") val dataJson: String,
    /** Resultado completo del motor (LegacyResult) en JSON: instantánea de cálculo. */
    @ColumnInfo(name = "calc_json") val calcJson: String? = null,
    @ColumnInfo(name = "calibration_snapshot") val calibrationSnapshot: String? = null,
    @ColumnInfo(name = "lab_ref_snapshot") val labRefSnapshot: String? = null,
    @ColumnInfo(name = "engine_version") val engineVersion: String,
    val reason: String? = null,
    @ColumnInfo(name = "created_by") val createdBy: String = "",
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "reviewed_by") val reviewedBy: String? = null,
    @ColumnInfo(name = "reviewed_at") val reviewedAt: Long? = null,
    @ColumnInfo(name = "approved_by") val approvedBy: String? = null,
    @ColumnInfo(name = "approved_at") val approvedAt: Long? = null,
    val locked: Boolean = false,
    val superseded: Boolean = false,
)

@Entity(tableName = "materials")
data class MaterialEntity(@PrimaryKey val id: String, val code: String, val name: String, val description: String = "", val archived: Boolean = false, @ColumnInfo(name = "updated_at") val updatedAt: Long)

@Entity(tableName = "equipment")
data class EquipmentEntity(@PrimaryKey val id: String, val code: String, val name: String, val serial: String = "", val archived: Boolean = false, @ColumnInfo(name = "updated_at") val updatedAt: Long)

@Entity(
    tableName = "calibrations",
    foreignKeys = [ForeignKey(entity = EquipmentEntity::class, parentColumns = ["id"], childColumns = ["equipment_id"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("equipment_id")],
)
data class CalibrationEntity(
    @PrimaryKey val id: String,
    val code: String,
    @ColumnInfo(name = "equipment_id") val equipmentId: String,
    val date: String,
    @ColumnInfo(name = "valid_until") val validUntil: String? = null,
    @ColumnInfo(name = "cone_constant_g") val coneConstantG: String,
    @ColumnInfo(name = "sand_density") val sandDensity: String,
    val notes: String = "",
    val archived: Boolean = false,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(tableName = "lab_references")
data class LabReferenceEntity(
    @PrimaryKey val id: String,
    val code: String,
    @ColumnInfo(name = "material_id") val materialId: String? = null,
    @ColumnInfo(name = "test_id") val testId: String = "",
    @ColumnInfo(name = "max_dry_density") val maxDryDensity: String,
    @ColumnInfo(name = "density_unit") val densityUnit: String = "kg/m³",
    @ColumnInfo(name = "optimum_moisture") val optimumMoisture: String?,
    @ColumnInfo(name = "document_path") val documentPath: String? = null,
    val notes: String = "",
    val archived: Boolean = false,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(tableName = "people")
data class PersonEntity(@PrimaryKey val id: String, val name: String, val role: String = "", val archived: Boolean = false, @ColumnInfo(name = "updated_at") val updatedAt: Long)

@Entity(tableName = "criteria")
data class CriterionEntity(
    @PrimaryKey val id: String,
    val code: String,
    val version: String,
    /** StatisticalCriterion serializado. */
    val json: String,
    val validated: Boolean,
    val archived: Boolean = false,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(tableName = "template_versions", indices = [Index(value = ["version"], unique = true)])
data class TemplateVersionEntity(
    @PrimaryKey val id: String,
    val version: String,
    val institution: String,
    @ColumnInfo(name = "form_title") val formTitle: String,
    @ColumnInfo(name = "doc_code") val docCode: String,
    @ColumnInfo(name = "doc_version") val docVersion: String,
    @ColumnInfo(name = "doc_date") val docDate: String,
    @ColumnInfo(name = "logo_path") val logoPath: String?,
    @ColumnInfo(name = "logo_w") val logoW: Int = 0,
    @ColumnInfo(name = "logo_h") val logoH: Int = 0,
    @ColumnInfo(name = "formula_path") val formulaPath: String?,
    @ColumnInfo(name = "formula_w") val formulaW: Int = 0,
    @ColumnInfo(name = "formula_h") val formulaH: Int = 0,
    val active: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(tableName = "custom_fields", indices = [Index(value = ["field_key"], unique = true)])
data class CustomFieldEntity(
    @PrimaryKey val id: String,
    /** Identificador estable: los valores se guardan por esta clave, no por la etiqueta. */
    @ColumnInfo(name = "field_key") val key: String,
    val entity: String,
    val label: String,
    val description: String = "",
    val type: String,
    val unit: String = "",
    val required: Boolean = false,
    @ColumnInfo(name = "options_json") val optionsJson: String = "[]",
    @ColumnInfo(name = "min_value") val minValue: String? = null,
    @ColumnInfo(name = "max_value") val maxValue: String? = null,
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
    @ColumnInfo(name = "include_pdf") val includeInPdf: Boolean = true,
    @ColumnInfo(name = "include_table") val includeInTable: Boolean = false,
    val version: Int = 1,
    val archived: Boolean = false,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "photos",
    foreignKeys = [
        ForeignKey(entity = ActivityEntity::class, parentColumns = ["id"], childColumns = ["activity_id"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = SampleEntity::class, parentColumns = ["id"], childColumns = ["sample_id"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = JourneyEntity::class, parentColumns = ["id"], childColumns = ["journey_id"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("sample_id"), Index("journey_id"), Index("activity_id"), Index("sha256")],
)
data class PhotoEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "activity_id") val activityId: String,
    @ColumnInfo(name = "sample_id") val sampleId: String? = null,
    @ColumnInfo(name = "journey_id") val journeyId: String? = null,
    val origin: String,
    @ColumnInfo(name = "original_path") val originalPath: String,
    @ColumnInfo(name = "thumb_path") val thumbPath: String,
    @ColumnInfo(name = "pdf_path") val pdfPath: String,
    @ColumnInfo(name = "pdf_w") val pdfW: Int,
    @ColumnInfo(name = "pdf_h") val pdfH: Int,
    val sha256: String,
    val mime: String,
    val width: Int,
    val height: Int,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long,
    @ColumnInfo(name = "exif_orientation") val exifOrientation: Int = 1,
    val rotation: Int = 0,
    /** PhotoTimes serializado: todas las fechas por separado. */
    @ColumnInfo(name = "times_json") val timesJson: String,
    /** PhotoCaption.Fields serializado: datos propios de la fotografía. */
    @ColumnInfo(name = "caption_json") val captionJson: String = "{}",
    @ColumnInfo(name = "include_report") val includeInReport: Boolean = true,
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val archived: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "reports",
    foreignKeys = [ForeignKey(entity = ActivityEntity::class, parentColumns = ["id"], childColumns = ["activity_id"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("activity_id"), Index("sample_id")])
data class ReportEntity(
    @PrimaryKey val id: String,
    val code: String,
    val type: String,
    @ColumnInfo(name = "activity_id") val activityId: String,
    @ColumnInfo(name = "sample_id") val sampleId: String? = null,
    @ColumnInfo(name = "revision_id") val revisionId: String? = null,
    @ColumnInfo(name = "issued_at") val issuedAt: Long,
    @ColumnInfo(name = "issued_at_text") val issuedAtText: String,
    @ColumnInfo(name = "issued_by") val issuedBy: String?,
    val draft: Boolean,
    @ColumnInfo(name = "template_version") val templateVersion: String,
    @ColumnInfo(name = "engine_version") val engineVersion: String,
    @ColumnInfo(name = "file_path") val filePath: String,
    @ColumnInfo(name = "snapshot_path") val snapshotPath: String,
    val sha256: String,
    @ColumnInfo(name = "page_count") val pageCount: Int,
    @ColumnInfo(name = "scope_text") val scopeText: String,
)

@Entity(tableName = "audit_events", indices = [Index("entity_id"), Index("at")])
data class AuditEntity(
    @PrimaryKey val id: String,
    val at: Long,
    val actor: String,
    @ColumnInfo(name = "entity_type") val entityType: String,
    @ColumnInfo(name = "entity_id") val entityId: String,
    val action: String,
    val detail: String = "",
)
