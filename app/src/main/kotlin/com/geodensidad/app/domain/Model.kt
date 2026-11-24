package com.geodensidad.app.domain

import com.geodensidad.core.calc.LegacyInputs
import com.geodensidad.core.number.DecimalText
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

val AppJson = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = false }

fun newId(): String = UUID.randomUUID().toString()

object Clock {
    var nowMillis: () -> Long = { System.currentTimeMillis() }
    var zone: () -> ZoneId = { ZoneId.systemDefault() }
    fun nowOffset(): OffsetDateTime = OffsetDateTime.ofInstant(java.time.Instant.ofEpochMilli(nowMillis()), zone())
    fun isoNow(): String = nowOffset().toString()
    private val disp = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
    fun display(millis: Long): String {
        val odt = OffsetDateTime.ofInstant(java.time.Instant.ofEpochMilli(millis), zone())
        return odt.format(disp) + " " + com.geodensidad.core.photo.CaptureTime.utc(odt.offset)
    }
    fun displayDate(iso: String?): String = iso?.takeIf { it.length >= 10 }?.let { "${it.substring(8, 10)}/${it.substring(5, 7)}/${it.substring(0, 4)}" } ?: "—"
}

/** Estados documentales de una muestra (independientes del resultado técnico). */
enum class SampleStatus(val label: String) {
    DRAFT("Borrador"), COMPLETE("Completa"), REVIEWED("Revisada"), APPROVED("Aprobada"), VOID("Anulada");
    val eligibleForCalc get() = this == COMPLETE || this == REVIEWED || this == APPROVED
    companion object { fun of(s: String) = entries.firstOrNull { it.name == s } ?: DRAFT }
}

enum class ActivityStatus(val label: String) {
    OPEN("Abierta"), PAUSED("Pausada"), CLOSED("Cerrada"), ARCHIVED("Archivada");
    companion object { fun of(s: String) = entries.firstOrNull { it.name == s } ?: OPEN }
}

enum class CustomFieldType(val label: String) { TEXT("Texto"), NUMBER("Número"), PERCENT("Porcentaje"), DATE("Fecha"), BOOLEAN("Sí/no"), CHOICE("Lista de opciones") }
enum class CustomFieldEntityType(val label: String) { SAMPLE("Muestra"), JOURNEY("Jornada") }

/**
 * Formulario de una revisión de muestra. Todos los números como texto decimal canónico
 * (punto decimal, sin exponente) o null si faltan. Humedades como FRACCIÓN ("0.06").
 */
@Serializable
data class SampleForm(
    val testDateTime: String? = null,
    val origin: String = "",
    val location: String = "",
    val abscisa: String = "",
    val side: String = "",
    val layer: String = "",
    val thickness: String? = null,
    val thicknessUnit: String = "cm",
    val fillPart: String = "",
    val materialId: String? = null,
    val equipmentId: String? = null,
    val calibrationId: String? = null,
    val labRefId: String? = null,
    val a: String? = null,
    val b: String? = null,
    val d: String? = null,
    val f: String? = null,
    val h: String? = null,
    val i: String? = null,
    val j: String? = null,
    val l: String? = null,
    val q: String? = null,
    val r: String? = null,
    val labTestId: String = "",
    val observations: String = "",
    val elaboratedBy: String = "",
    // Campos del encabezado y pie del formato Excel 'Cono de arena'
    val prInicial: String = "",
    val prFinal: String = "",
    val lado: String = "",
    val registro: String = "",
    val irregularidades: String = "",
    val aprobo: String = "",
    val custom: Map<String, String> = emptyMap(),
) {
    fun toInputs() = LegacyInputs(
        a = DecimalText.storedToDouble(a), b = DecimalText.storedToDouble(b), d = DecimalText.storedToDouble(d),
        f = DecimalText.storedToDouble(f), h = DecimalText.storedToDouble(h), i = DecimalText.storedToDouble(i),
        j = DecimalText.storedToDouble(j), l = DecimalText.storedToDouble(l), q = DecimalText.storedToDouble(q),
        r = DecimalText.storedToDouble(r),
    )
}

/** Campos contextuales que se pueden copiar al duplicar una muestra (nunca mediciones ni resultados). */
enum class DuplicableField(val label: String) {
    HEADER("Encabezado (PR inicial/final, lado, fecha, registro)"), JOURNEY("Jornada"), ORIGIN("Procedencia"), LOCATION("Localización"), SIDE("Costado"), LAYER("Capa"),
    THICKNESS("Espesor"), FILL_PART("Parte del relleno"), MATERIAL("Material"), CALIBRATION("Equipo y calibración"),
    LAB_REF("Referencia de laboratorio"), LOT("Lote"),
}

@Serializable
data class CalibrationSnapshot(val id: String, val code: String, val equipment: String, val date: String, val validUntil: String?, val coneConstantG: String, val sandDensity: String)

@Serializable
data class LabRefSnapshot(val id: String, val code: String, val material: String?, val testId: String, val maxDryDensity: String, val optimumMoisture: String?)
