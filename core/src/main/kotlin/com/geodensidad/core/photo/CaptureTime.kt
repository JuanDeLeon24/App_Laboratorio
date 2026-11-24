package com.geodensidad.core.photo

import kotlinx.serialization.Serializable
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Fuente de la fecha/hora mostrada en el pie de una fotografía. */
@Serializable
enum class CaptureTimeSource(val label: String) {
    APP_DEVICE("Hora del dispositivo al capturar"),
    EXIF("Fecha EXIF original"),
    MANUAL("Fecha declarada manualmente"),
    UNKNOWN("Fecha de captura no disponible"),
}

/**
 * Registro separado de todas las fechas de una fotografía.
 * Los instantes con zona conocida se guardan como ISO-8601 con desfase; las fechas EXIF sin
 * zona se guardan como fecha-hora local SIN inventar desfase.
 */
@Serializable
data class PhotoTimes(
    /** Hora del dispositivo cuando la app tomó la foto (ISO con desfase). */
    val appCaptureAt: String? = null,
    /** EXIF DateTimeOriginal tal como fecha-hora local "yyyy-MM-ddTHH:mm:ss". */
    val exifLocal: String? = null,
    /** EXIF OffsetTimeOriginal ("-05:00") si existía. */
    val exifOffset: String? = null,
    /** Momento de importación (ISO con desfase). */
    val importedAt: String,
    /** Fecha declarada manualmente (local, con desfase opcional). */
    val manualLocal: String? = null,
    val manualOffset: String? = null,
    val manualReason: String? = null,
    val selected: CaptureTimeSource,
)

object CaptureTime {
    private val exifFmt = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")
    private val display = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")

    /** Interpreta EXIF DateTimeOriginal; devuelve null si está vacío, en ceros o mal formado. */
    fun parseExifDateTime(raw: String?): LocalDateTime? {
        val t = raw?.trim()?.trimEnd('\u0000').orEmpty()
        if (t.isEmpty() || t.startsWith("0000")) return null
        return runCatching { LocalDateTime.parse(t.take(19), exifFmt) }.getOrNull()
    }

    /** Interpreta EXIF OffsetTimeOriginal ("+01:00"); null si no hay o no es válido. */
    fun parseExifOffset(raw: String?): ZoneOffset? {
        val t = raw?.trim()?.trimEnd('\u0000').orEmpty()
        if (t.isEmpty()) return null
        return runCatching { ZoneOffset.of(t) }.getOrNull()
    }

    /** Comportamiento predeterminado de selección de fuente. */
    fun defaultSource(fromCamera: Boolean, exifLocal: String?): CaptureTimeSource = when {
        fromCamera -> CaptureTimeSource.APP_DEVICE
        exifLocal != null -> CaptureTimeSource.EXIF
        else -> CaptureTimeSource.UNKNOWN
    }

    data class Display(val text: String, val note: String?)

    /** Texto de fecha/hora para el pie. Nunca sustituye por la fecha de importación. */
    fun display(times: PhotoTimes): Display = when (times.selected) {
        CaptureTimeSource.APP_DEVICE -> times.appCaptureAt?.let {
            val odt = OffsetDateTime.parse(it)
            Display("${odt.format(display)} ${utc(odt.offset)}", "hora del dispositivo")
        } ?: Display("Fecha de captura no disponible", null)
        CaptureTimeSource.EXIF -> times.exifLocal?.let {
            val ldt = LocalDateTime.parse(it)
            val off = times.exifOffset?.let { o -> runCatching { ZoneOffset.of(o) }.getOrNull() }
            if (off != null) Display("${ldt.format(display)} ${utc(off)}", "fecha EXIF")
            else Display(ldt.format(display), "fecha EXIF sin zona horaria")
        } ?: Display("Fecha de captura no disponible", null)
        CaptureTimeSource.MANUAL -> times.manualLocal?.let {
            val ldt = LocalDateTime.parse(it)
            val off = times.manualOffset?.let { o -> runCatching { ZoneOffset.of(o) }.getOrNull() }
            Display(ldt.format(display) + (off?.let { o -> " " + utc(o) } ?: ""), "fecha declarada manualmente")
        } ?: Display("Fecha de captura no disponible", null)
        CaptureTimeSource.UNKNOWN -> Display("Fecha de captura no disponible", null)
    }

    fun utc(offset: ZoneOffset): String {
        if (offset.totalSeconds == 0) return "UTC"
        val id = offset.id // "+05:00" / "-05:00"
        return "UTC" + id.replace("-", "−")
    }
}

/** Composición del pie de cada fotografía en el informe. */
object PhotoCaption {
    @Serializable
    data class Fields(
        val title: String? = null,
        val description: String? = null,
        val observation: String? = null,
        val abscisa: String? = null,
        val side: String? = null,
        val layer: String? = null,
        val responsible: String? = null,
        val tags: List<String> = emptyList(),
        val extra: List<Pair<String, String>> = emptyList(),
    )

    /**
     * @param inherited datos propuestos desde la muestra (abscisa, costado, capa) que se usan
     *        solo si la fotografía no tiene un valor propio.
     */
    fun lines(number: Int, times: PhotoTimes, sampleCode: String?, own: Fields, inherited: Fields, coordinates: String?): List<String> {
        val d = CaptureTime.display(times)
        val first = buildString {
            append("Fotografía ${number.toString().padStart(2, '0')} · ${d.text}")
            if (times.selected == CaptureTimeSource.MANUAL) append(" (fecha declarada manualmente)")
            else if (d.note == "fecha EXIF sin zona horaria") append(" (EXIF sin zona horaria)")
        }
        val out = mutableListOf(first)
        val abs = own.abscisa ?: inherited.abscisa
        val side = own.side ?: inherited.side
        val layer = own.layer ?: inherited.layer
        val loc = listOfNotNull(sampleCode?.let { "Muestra $it" }, abs?.let { it }, side?.let { "Costado $it" }, layer?.let { "Capa $it" })
        if (loc.isNotEmpty()) out += loc.joinToString(" · ")
        own.title?.takeIf { it.isNotBlank() }?.let { out += "Título: $it" }
        own.description?.takeIf { it.isNotBlank() }?.let { out += "Descripción: $it" }
        own.observation?.takeIf { it.isNotBlank() }?.let { out += "Observación: $it" }
        own.responsible?.takeIf { it.isNotBlank() }?.let { out += "Responsable: $it" }
        if (own.tags.isNotEmpty()) out += "Etiquetas: ${own.tags.joinToString(", ")}"
        own.extra.filter { it.second.isNotBlank() }.forEach { out += "${it.first}: ${it.second}" }
        coordinates?.let { out += "Coordenadas: $it" }
        return out
    }
}
