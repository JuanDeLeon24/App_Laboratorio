package com.geodensidad.app

import com.geodensidad.core.backup.BackupArchive
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Emisión de informes por ReportService (datos, almacenamiento, hash, instantánea, inmutabilidad).
 * El backend PdfDocument nativo de Android no está disponible en Robolectric: se inyecta PDFBox.
 */
@RunWith(RobolectricTestRunner::class)
class ReportServiceTest {
    private lateinit var c: AppContainer
    @Before fun setup() { c = TestSupport.freshContainer(); c.pdfWriterFactory = { PdfBoxWriter() } }

    @Test fun emisionIndividualYGlobalConservanArchivoEHash() = runBlocking {
        c.demo.load()
        val act = c.db.activities().listOpenIds().first()
        val s = c.db.samples().listByActivity(act).first()
        val img = TestSupport.jpeg(File(c.context.cacheDir, "ext/a.jpg"), 640, 480)
        c.evidence.importFromUris(listOf(android.net.Uri.fromFile(img)), act, s.id, null)
        val r = c.reports.sampleReport(s.id) { _, _ -> }
        val f = File(r.filePath)
        assertTrue(f.exists()); assertTrue(f.length() > 1000)
        assertEquals(r.sha256, BackupArchive.sha256(f))
        assertTrue(File(r.snapshotPath).readText().contains("DEN-0001"))
        assertFalse("muestra completa: no es borrador", r.draft)
        assertTrue(f.readBytes().copyOf(5).decodeToString() == "%PDF-")
        val g = c.reports.globalReport(com.geodensidad.app.report.GlobalFilter(act)) { _, _ -> }
        assertTrue(File(g.filePath).exists()); assertTrue(g.pageCount >= 3)
        assertTrue(File(g.snapshotPath).readText().contains("89,91"))
        // reimprimir no regenera: modificar datos no cambia el archivo emitido
        val before = BackupArchive.sha256(File(r.filePath))
        c.repo.returnToDraft(s.id); c.repo.saveForm(s.id, c.repo.bundle(s.id).form.copy(observations = "cambio posterior"))
        assertEquals(before, BackupArchive.sha256(File(r.filePath)))
        val r2 = c.reports.sampleReport(s.id) { _, _ -> }
        assertTrue(r2.draft); assertNotEquals(r.code, r2.code)
    }
}
