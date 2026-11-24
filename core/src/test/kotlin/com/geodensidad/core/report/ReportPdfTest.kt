package com.geodensidad.core.report

import com.geodensidad.core.calc.ConoArenaLegacy
import com.geodensidad.core.criteria.LotEvaluator
import com.geodensidad.core.criteria.LotObservation
import com.geodensidad.core.criteria.StatisticalCriterion
import com.geodensidad.core.demo.ReferenceSamples
import com.geodensidad.core.location.Abscisa
import com.geodensidad.core.number.Fmt
import com.geodensidad.core.photo.CaptureTimeSource
import com.geodensidad.core.photo.PhotoCaption
import com.geodensidad.core.photo.PhotoTimes
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Test
import java.awt.Color
import java.awt.Font
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Genera PDFs de ejemplo con datos DEMOSTRATIVOS usando el mismo motor de composición que la app
 * (aquí con superficie PDFBox) y verifica su contenido textual.
 */
class ReportPdfTest {
    companion object {
        lateinit var out: File
        lateinit var fonts: File
        lateinit var landscape: File
        lateinit var portrait: File
        lateinit var template: TemplateInfo

        @BeforeClass @JvmStatic fun setup() {
            out = File(System.getProperty("geodensidad.samplesDir") ?: "build/ejemplos-pdf").apply { mkdirs() }
            fonts = PdfBoxSurface.bundledFonts()
            landscape = photo(1600, 1200, "Foto horizontal de prueba", File(out, "foto_h.jpg"))
            portrait = photo(900, 1600, "Foto vertical de prueba", File(out, "foto_v.jpg"))
            template = TemplateInfo(
                templateVersion = "1.0.0", institution = "Construcciones El Cóndor S.A.",
                formTitle = "DENSIDAD O MASA UNITARIA DEL SUELO EN EL TERRENO – MÉTODO DEL CONO DE ARENA (I.N.V. E – 161)",
                docCode = "(por definir)", docVersion = "(por definir)", docDate = "(por definir)",
                logoPath = PdfBoxSurface.resourceFile("template/logo.png").absolutePath, logoPxW = 333, logoPxH = 120,
                formulaImagePath = PdfBoxSurface.resourceFile("template/formula_s.png").absolutePath, formulaPxW = 314, formulaPxH = 124,
            )
        }

        private fun photo(w: Int, h: Int, label: String, f: File): File {
            val img = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
            val g = img.createGraphics()
            g.color = Color(0xB5, 0xCC, 0xEB); g.fillRect(0, 0, w, h)
            g.color = Color(0x0B, 0x29, 0x4B); g.fillOval(w / 4, h / 4, w / 2, h / 2)
            g.color = Color.WHITE; g.font = Font(Font.SANS_SERIF, Font.BOLD, w / 18); g.drawString(label, w / 12, h / 8)
            g.dispose(); ImageIO.write(img, "jpg", f); return f
        }
    }

    private fun issue(draft: Boolean, code: String) = IssueInfo(
        reportId = "00000000-0000-4000-8000-00000000000${if (draft) 1 else 2}", reportCode = code,
        issuedAtText = "24/11/2026 15:30:05 UTC−05:00", issuedBy = "Ing. Demostración", projectShort = "DEMO-VIAS",
        draft = draft, engine = ConoArenaLegacy.ENGINE_FULL_ID, appVersion = "1.0.0",
    )

    private val absList = listOf("PR 003+150", "PR 003+175", "PR 003+200", "PR 003+225", "PR 003+250", "PR 004+010", "PR 004+040", "PR 004+070", "Box culvert 2", "PR 004+130")

    private fun captionFor(n: Int, sample: String, abs: String, manual: Boolean, unknown: Boolean = false): List<String> {
        val times = when {
            manual -> PhotoTimes(importedAt = "2026-11-24T12:00:00-05:00", manualLocal = "2026-11-24T09:10:00", manualReason = "Cámara externa sin EXIF", selected = CaptureTimeSource.MANUAL)
            unknown -> PhotoTimes(importedAt = "2026-11-24T12:00:00-05:00", selected = CaptureTimeSource.UNKNOWN)
            else -> PhotoTimes(appCaptureAt = "2026-11-24T09:42:18-05:00", importedAt = "2026-11-24T09:42:19-05:00", selected = CaptureTimeSource.APP_DEVICE)
        }
        return PhotoCaption.lines(n, times, sample, PhotoCaption.Fields(description = "Vista del hueco antes de instalar el cono. Ejemplo ilustrativo, no es un registro real.", observation = "Material granular, capa 2."), PhotoCaption.Fields(abscisa = abs, side = "derecho", layer = "2"), null)
    }

    private fun sampleData(k: Int, draft: Boolean, longText: Boolean = false, photos: Int = 2): SampleReportData {
        val inp = ReferenceSamples.inputs(k)
        val r = ConoArenaLegacy.calculate(inp)
        val ps = (0 until photos).map { i ->
            val f = if (i % 2 == 0) landscape else portrait
            PhotoBlock(f.absolutePath, if (f == landscape) 1600 else 900, if (f == landscape) 1200 else 1600, captionFor(i + 1, "DEN-%04d".format(k + 1), absList[k], manual = i == 1, unknown = i == 2))
        }
        val long = if (longText) " Observación extensa para verificar el ajuste de línea: " + "el material presenta gravas angulosas con matriz arenosa, humedad visible y algunas partículas de tamaño superior a ¾″ (ñandú, cigüeña, acción). ".repeat(8) else ""
        return SampleReportData(
            template = template, issue = issue(draft, "INF-DEMO-%04d-R1".format(k + 1)), sampleCode = "DEN-%04d".format(k + 1),
            revisionLabel = "Revisión 1", documentStatus = if (draft) "Borrador" else "Aprobada",
            projectPairs = listOf("Proyecto" to "DEMO – Vías de acceso (datos demostrativos)", "Actividad" to "Toma de densidades para granulares", "Cliente" to "Entidad demostrativa", "Contratista" to "Construcciones El Cóndor S.A."),
            journeyPairs = listOf("Jornada" to "24/11/2026", "Responsable" to "Ing. Demostración", "Frente de trabajo" to "Frente 1"),
            locationPairs = listOf("Abscisa" to absList[k], "Costado" to "Derecho", "Capa" to "2", "Espesor" to "20 cm", "Parte del relleno" to "Núcleo", "Procedencia" to "Cantera demostrativa", "Lote" to "L-01"),
            measurementRows = ReportRows.measurementRows(inp),
            resultRows = ReportRows.resultRows(r),
            calibrationPairs = listOf("Calibración" to "CAL-CONO-01", "Equipo" to "Cono 01", "Fecha" to "01/11/2026", "Constante del cono (D)" to Fmt.withUnit(inp.d, 1, "g"), "Densidad de la arena (F)" to Fmt.withUnit(inp.f, 3, "g/cm³")),
            labRefPairs = listOf("Referencia" to "PROCTOR-DEMO-01") + ReportRows.labRows(inp),
            criterionLines = listOf("Criterio de lote: ${StatisticalCriterion.LEGACY.name} – evaluación provisional; criterio no validado."),
            technicalStatus = "Compactación ${Fmt.num(r.compactionPercent, 2)} % (indicador individual; el dictamen corresponde al lote)",
            warnings = r.warnings.map { it.message } + "wc = 0,02 es un supuesto heredado del Excel pendiente de validación técnica.",
            observations = "Ensayo demostrativo.$long",
            customPairs = listOf("Temperatura ambiente" to "24 °C", "Operador del cono" to "Técnico demostrativo"),
            elaboratedBy = "Ing. Demostración", approvedBy = if (draft) null else "Ing. Revisor Demo",
            photos = ps, photosPerPage = 2,
        )
    }

    private fun render(pages: PageList, name: String): File {
        val f = File(out, name)
        PdfBoxSurface(fonts).use { s -> pages.render(s); s.save(f) }
        return f
    }

    private fun text(f: File) = Loader.loadPDF(f).use { PDFTextStripper().getText(it) }

    @Test fun informeIndividualBorrador() {
        PdfBoxSurface(fonts).use { m ->
            val pages = ReportRenderer.sample(sampleData(0, draft = true), m)
            val f = render(pages, "ejemplo_individual_borrador_DEN-0001.pdf")
            val t = text(f)
            assertTrue(t.contains("BORRADOR"))
            assertTrue(t.contains("2.294,97"))
            assertTrue(t.contains("91,98"))
            assertTrue(t.contains("Página 1 de ${pages.pages.size}"))
            assertTrue(t.contains("MÉTODO DEL CONO DE ARENA"))
            // pie de foto con hora de captura, distinto de la hora de emisión
            assertTrue(t.contains("Fotografía 01 · 24/11/2026 09:42:18 UTC−05:00"))
            assertTrue(t.contains("Emitido 24/11/2026 15:30:05"))
            assertTrue(t.contains("(fecha declarada manualmente)"))
        }
    }

    @Test fun informeIndividualAprobadoSinFotosYTextoLargo() {
        PdfBoxSurface(fonts).use { m ->
            val pages = ReportRenderer.sample(sampleData(5, draft = false, longText = true, photos = 0), m)
            val f = render(pages, "ejemplo_individual_aprobado_DEN-0006.pdf")
            val t = text(f)
            assertFalse(t.contains("BORRADOR"))
            assertFalse(t.contains("Anexo fotográfico"))
            assertTrue(t.contains("ñandú, cigüeña"))
        }
    }

    @Test fun informeIndividualConMuchasFotos() {
        PdfBoxSurface(fonts).use { m ->
            val pages = ReportRenderer.sample(sampleData(2, draft = false, photos = 7), m)
            render(pages, "ejemplo_individual_muchas_fotos_DEN-0003.pdf")
            // 7 fotos a 2 por página → 4 páginas de anexo
            val t = text(File(out, "ejemplo_individual_muchas_fotos_DEN-0003.pdf"))
            for (i in 1..7) assertTrue("foto $i", t.contains("Fotografía %02d".format(i)))
            assertTrue(t.contains("Fecha de captura no disponible"))
            assertTrue(pages.pages.size >= 5)
        }
    }

    private fun records(): List<SampleRecord> = (0 until 10).map { k ->
        val inp = ReferenceSamples.inputs(k); val r = ConoArenaLegacy.calculate(inp)
        SampleRecord("id$k", "DEN-%04d".format(k + 1), k, "24/11/2026", absList[k], Abscisa.toMeters(absList[k]), "Derecho", "2", "Granular demo",
            if (k < 5) "L-01" else "L-02", "Aprobada", true, r.value("P"), r.value("S"), inp.l, inp.r, inp.q, "PROCTOR-DEMO-01", 1)
    } + SampleRecord("id10", "DEN-0011", 10, "25/11/2026", "PR 004+160", 4160.0, "Izquierdo", "2", "Granular demo", null, "Borrador", false, null, null, null, 0.0668, 2495.0, "PROCTOR-DEMO-01", 1)

    @Test fun informeGlobal() {
        val recs = records()
        val crit = StatisticalCriterion.LEGACY
        val lots = recs.filter { it.lotCode != null }.groupBy { it.lotCode!! }.map { (code, rs) ->
            val e = LotEvaluator.evaluate(rs.map { LotObservation(it.id, it.code, it.p, it.de, it.s, it.material, it.labRefKey, it.eligible, false) }, crit)
            LotBlock("Lote $code", listOf(
                "Registradas / incluidas / excluidas" to "${e.registered} / ${e.included} / ${e.excluded}",
                "Dm" to Fmt.withUnit(e.stats.mean, 2, "kg/m³"), "s" to Fmt.withUnit(e.stats.sampleSd, 2, "kg/m³"),
                "k(n)" to Fmt.num(e.k, 3), "Dm − k·s" to Fmt.withUnit(e.leftSide, 2, "kg/m³"), "α·De" to Fmt.withUnit(e.rightSide, 2, "kg/m³"),
                "Di individuales ≥ α·De" to "${e.individualAboveThreshold} de ${e.included} (indicador descriptivo, no es aceptación)",
            ), e.status.label, null, e.reasons)
        }
        val inc = recs.filter { it.included }
        val data = GlobalReportData(
            template = template, issue = issue(false, "INF-DEMO-GLOBAL-0001"), title = "Informe global – Toma de densidades para granulares (DEMOSTRATIVO)",
            scopePairs = listOf("Proyecto" to "DEMO – Vías de acceso", "Actividad" to "Toma de densidades para granulares", "Intervalo de fechas" to "24/11/2026 – 25/11/2026", "Filtros" to "Todos los lotes y materiales; todos los estados"),
            executiveSummary = listOf("${inc.size} de ${recs.size} muestras incluidas en los cálculos.", "Compactación media de las incluidas: ${Fmt.num(inc.map { it.s!! }.average(), 6)} %.", "Las evaluaciones de lote son provisionales: el criterio heredado no está validado."),
            coveragePairs = GlobalAssembler.coverage(recs), sampleColumns = GlobalAssembler.baseColumns, sampleRows = GlobalAssembler.rows(recs),
            statGroups = GlobalAssembler.groups(recs), charts = GlobalAssembler.charts(recs, crit), lots = lots,
            notIncludedRows = GlobalAssembler.notIncluded(recs),
            warnings = listOf("Datos demostrativos.", "El criterio Dm − k·s ≥ 0,95·De es heredado del formato y no está validado."),
            observations = null,
            calibrationRows = listOf(listOf("CAL-CONO-01", "Cono 01", "01/11/2026", "1.735,0", "1,396"), listOf("CAL-CONO-02", "Cono 02", "15/11/2026", "1.774,0", "1,408")),
            labRefRows = listOf(listOf("PROCTOR-DEMO-01", "Granular demo", "Proctor modificado demo", "2.495,0", "6,68")),
            photos = listOf(PhotoBlock(landscape.absolutePath, 1600, 1200, captionFor(1, "DEN-0001", absList[0], false)), PhotoBlock(portrait.absolutePath, 900, 1600, captionFor(2, "DEN-0002", absList[1], true))),
        )
        PdfBoxSurface(fonts).use { m ->
            val pages = ReportRenderer.global(data, m)
            val f = render(pages, "ejemplo_global_actividad.pdf")
            val t = text(f)
            assertTrue(t.contains("89,907877"))
            assertTrue(t.contains("Evaluación provisional"))
            assertTrue(t.contains("DEN-0011"))
            assertTrue(t.contains("Compactación por abscisa"))
            assertTrue(t.contains("Página ${pages.pages.size} de ${pages.pages.size}"))
            File(out, "ejemplo_global_actividad.snapshot.json").writeText(kotlinx.serialization.json.Json { prettyPrint = true }.encodeToString(GlobalReportData.serializer(), data))
        }
    }
}
