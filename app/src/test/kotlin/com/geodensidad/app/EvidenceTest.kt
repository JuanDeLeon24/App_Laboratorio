package com.geodensidad.app

import androidx.exifinterface.media.ExifInterface
import com.geodensidad.app.domain.AppJson
import com.geodensidad.core.backup.BackupArchive
import com.geodensidad.core.photo.CaptureTime
import com.geodensidad.core.photo.CaptureTimeSource
import com.geodensidad.core.photo.PhotoTimes
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class EvidenceTest {
    private lateinit var c: AppContainer
    private lateinit var activity: String
    private lateinit var sample: String

    @Before fun setup() = runBlocking {
        c = TestSupport.freshContainer()
        val p = c.repo.createProject("P", "Proyecto"); activity = c.repo.createActivity(p, "Act")
        sample = c.repo.createSample(activity, c.repo.createJourney(activity))
    }

    private fun times(id: String) = AppJson.decodeFromString(PhotoTimes.serializer(), runBlocking { c.db.photos().get(id)!! }.timesJson)

    @Test fun camaraUsaHoraDelDispositivoYConservaOriginal() = runBlocking {
        val src = TestSupport.jpeg(c.evidence.newCameraFile(), 1200, 900)
        val hash = BackupArchive.sha256(src)
        val r = c.evidence.importFromCamera(src, activity, sample, null, null)
        assertTrue(r.errors.toString(), r.errors.isEmpty())
        val p = c.db.photos().get(r.imported.single())!!
        assertEquals("CAMERA", p.origin)
        assertEquals(hash, p.sha256)
        assertEquals(hash, BackupArchive.sha256(File(p.originalPath)))
        assertFalse("el temporal de cámara se elimina", src.exists())
        assertTrue(File(p.thumbPath).exists()); assertTrue(File(p.pdfPath).exists())
        val t = times(p.id)
        assertEquals(CaptureTimeSource.APP_DEVICE, t.selected); assertNotNull(t.appCaptureAt)
        assertTrue(c.evidence.verify(p))
    }

    @Test fun galeriaSinExifNoUsaFechaDeImportacion() = runBlocking {
        val f = TestSupport.jpeg(File(c.context.cacheDir, "ext/sin_exif.jpg"), 800, 600)
        val id = c.evidence.importFromUris(listOf(android.net.Uri.fromFile(f)), activity, sample, null).imported.single()
        val t = times(id)
        assertEquals(CaptureTimeSource.UNKNOWN, t.selected)
        assertEquals("Fecha de captura no disponible", CaptureTime.display(t).text)
        assertNotNull(t.importedAt)
    }

    @Test fun exifSinZonaYConOrientacionYArchivoExternoEliminado() = runBlocking {
        val f = TestSupport.jpeg(File(c.context.cacheDir, "ext/exif.jpg"), 1000, 500)
        ExifInterface(f.path).apply {
            setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, "2026:11:24 09:42:18")
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            saveAttributes()
        }
        val id = c.evidence.importFromUris(listOf(android.net.Uri.fromFile(f)), activity, sample, null).imported.single()
        f.delete() // pérdida del archivo externo tras importar
        val p = c.db.photos().get(id)!!
        assertTrue(File(p.originalPath).exists())
        val t = times(id)
        assertEquals(CaptureTimeSource.EXIF, t.selected)
        assertEquals("2026-11-24T09:42:18", t.exifLocal); assertNull(t.exifOffset)
        assertEquals("24/11/2026 09:42:18", CaptureTime.display(t).text)
        // orientación corregida en derivados: 1000x500 girada 90° → vertical
        assertEquals(500, p.width); assertEquals(1000, p.height)
        assertTrue(p.pdfH > p.pdfW)
        // rotación manual solo en derivados; el original no cambia
        val origHash = p.sha256
        c.evidence.rotate(id, 90)
        val p2 = c.db.photos().get(id)!!
        assertTrue(p2.pdfW > p2.pdfH); assertEquals(origHash, BackupArchive.sha256(File(p2.originalPath)))
    }

    @Test fun fechaManualConservaOriginalesYExigeMotivo() = runBlocking {
        val f = TestSupport.jpeg(File(c.context.cacheDir, "ext/m.jpg"), 400, 300)
        val id = c.evidence.importFromUris(listOf(android.net.Uri.fromFile(f)), activity, sample, null).imported.single()
        try { c.evidence.setManualTime(id, "2026-11-20T10:00", null, " ", CaptureTimeSource.MANUAL); fail() } catch (e: Exception) {}
        c.evidence.setManualTime(id, "2026-11-20T10:00", "-05:00", "Cámara externa", CaptureTimeSource.MANUAL)
        val t = times(id)
        assertEquals(CaptureTimeSource.MANUAL, t.selected); assertEquals("Cámara externa", t.manualReason); assertNotNull(t.importedAt)
        assertTrue(CaptureTime.display(t).note!!.contains("manualmente"))
    }

    @Test fun imagenCorruptaSeRechaza() = runBlocking {
        val f = File(c.context.cacheDir, "ext/corrupta.jpg").apply { parentFile!!.mkdirs(); writeBytes(ByteArray(2000) { 7 }) }
        val r = c.evidence.importFromUris(listOf(android.net.Uri.fromFile(f)), activity, sample, null)
        assertTrue(r.imported.isEmpty()); assertEquals(1, r.errors.size)
        assertTrue(c.db.photos().listBySample(sample).isEmpty())
    }

    @Test fun persistenciaTrasReinicioYRetiroDelInforme() = runBlocking {
        val f = TestSupport.jpeg(File(c.context.cacheDir, "ext/p.jpg"), 400, 300)
        val id = c.evidence.importFromUris(listOf(android.net.Uri.fromFile(f)), activity, sample, null).imported.single()
        c.evidence.updateCaption(id, com.geodensidad.core.photo.PhotoCaption.Fields(description = "Vista del hueco"), include = false)
        TestSupport.reopen(c)
        val p = c.db.photos().get(id)!!
        assertFalse(p.includeInReport); assertTrue(File(p.originalPath).exists())
        assertTrue(p.captionJson.contains("Vista del hueco"))
    }
}
