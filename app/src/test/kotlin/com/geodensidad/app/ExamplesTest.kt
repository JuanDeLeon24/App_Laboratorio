package com.geodensidad.app

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** Genera artefactos de EJEMPLO con datos demostrativos: respaldo completo e informes vía ReportService. */
@RunWith(RobolectricTestRunner::class)
class ExamplesTest {
    @Test fun generarRespaldoEInformesDeEjemplo() = runBlocking {
        val c = TestSupport.freshContainer()
        c.pdfWriterFactory = { PdfBoxWriter() }
        c.demo.load()
        val act = c.db.activities().listOpenIds().first()
        val samples = c.db.samples().listByActivity(act)
        val img = TestSupport.jpeg(File(c.context.cacheDir, "ext/demo.jpg"), 1200, 900)
        c.evidence.importFromUris(listOf(android.net.Uri.fromFile(img)), act, samples[0].id, null)
        c.repo.markReviewed(samples[0].id, "Revisor demostrativo"); c.repo.approve(samples[0].id, "Aprobador demostrativo")
        val out = File(System.getProperty("geodensidad.outDir") ?: "build/ejemplos").apply { mkdirs() }
        val r = c.reports.sampleReport(samples[0].id) { _, _ -> }
        File(r.filePath).copyTo(File(out, "app_informe_individual_DEN-0001_aprobado.pdf"), true)
        val g = c.reports.globalReport(com.geodensidad.app.report.GlobalFilter(act)) { _, _ -> }
        File(g.filePath).copyTo(File(out, "app_informe_global_demo.pdf"), true)
        val zip = File(out, "respaldo_demostrativo.geodensidad.zip")
        zip.outputStream().use { c.backups.exportTo(it) }
        assertTrue(zip.length() > 10_000)
    }
}
