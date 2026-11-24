package com.geodensidad.core.photo

import org.junit.Assert.*
import org.junit.Test

class CaptureTimeTest {
    private val imported = "2026-11-25T08:00:00-05:00"

    @Test fun camaraUsaHoraDelDispositivo() {
        val t = PhotoTimes(appCaptureAt = "2026-11-24T09:42:18-05:00", importedAt = imported, selected = CaptureTime.defaultSource(true, null))
        assertEquals("24/11/2026 09:42:18 UTC−05:00", CaptureTime.display(t).text)
    }
    @Test fun exifConZona() {
        val local = CaptureTime.parseExifDateTime("2026:11:24 09:42:18")!!.toString()
        val t = PhotoTimes(exifLocal = local, exifOffset = "-05:00", importedAt = imported, selected = CaptureTime.defaultSource(false, local))
        assertEquals(CaptureTimeSource.EXIF, t.selected)
        assertEquals("24/11/2026 09:42:18 UTC−05:00", CaptureTime.display(t).text)
    }
    @Test fun exifSinZonaNoInventaDesfase() {
        val local = CaptureTime.parseExifDateTime("2026:11:24 09:42:18")!!.toString()
        val t = PhotoTimes(exifLocal = local, importedAt = imported, selected = CaptureTimeSource.EXIF)
        val d = CaptureTime.display(t)
        assertEquals("24/11/2026 09:42:18", d.text)
        assertFalse(d.text.contains("UTC"))
        val lines = PhotoCaption.lines(1, t, null, PhotoCaption.Fields(), PhotoCaption.Fields(), null)
        assertTrue(lines[0].contains("EXIF sin zona horaria"))
    }
    @Test fun sinExifNoUsaFechaDeImportacion() {
        assertNull(CaptureTime.parseExifDateTime(null))
        assertNull(CaptureTime.parseExifDateTime("0000:00:00 00:00:00"))
        assertNull(CaptureTime.parseExifDateTime("basura"))
        val t = PhotoTimes(importedAt = imported, selected = CaptureTime.defaultSource(false, null))
        assertEquals(CaptureTimeSource.UNKNOWN, t.selected)
        assertEquals("Fecha de captura no disponible", CaptureTime.display(t).text)
        assertFalse(CaptureTime.display(t).text.contains("25/11/2026"))
    }
    @Test fun fechaManualSeIndica() {
        val t = PhotoTimes(importedAt = imported, manualLocal = "2026-11-20T10:00", manualReason = "Foto de cámara externa", selected = CaptureTimeSource.MANUAL)
        val lines = PhotoCaption.lines(3, t, "DEN-0007", PhotoCaption.Fields(description = "Vista del hueco"), PhotoCaption.Fields(abscisa = "PR 003+150", side = "derecho"), null)
        assertEquals("Fotografía 03 · 20/11/2026 10:00:00 (fecha declarada manualmente)", lines[0])
        assertEquals("Muestra DEN-0007 · PR 003+150 · Costado derecho", lines[1])
        assertTrue(lines.contains("Descripción: Vista del hueco"))
    }
    @Test fun datosPropiosPrevalecenSobreHeredados() {
        val t = PhotoTimes(importedAt = imported, selected = CaptureTimeSource.UNKNOWN)
        val lines = PhotoCaption.lines(1, t, "M1", PhotoCaption.Fields(abscisa = "K1+000"), PhotoCaption.Fields(abscisa = "K2+000", layer = "2"), null)
        assertEquals("Muestra M1 · K1+000 · Capa 2", lines[1])
    }
}
