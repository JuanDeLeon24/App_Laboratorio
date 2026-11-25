package com.geodensidad.app

import androidx.test.core.app.ApplicationProvider
import com.geodensidad.app.data.*
import com.geodensidad.core.demo.ReferenceSamples
import com.geodensidad.core.report.PlanillaBuilder
import com.geodensidad.core.stats.Descriptive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(RobolectricTestRunner::class)
class RegistroTest {
    private val c get() = ApplicationProvider.getApplicationContext<GeoDensidadApp>().container

    @Test fun diezDensidadesDelExcel() {
        val r = registroDelExcel()
        val res = r.densidades.map { r.calcular(it) }
        res.forEachIndexed { k, x ->
            assertEquals(ReferenceSamples.expectedP[k], x.value("P")!!, 5.1e-7)
            assertEquals(ReferenceSamples.expectedS[k], x.value("S")!!, 5.1e-7)
        }
        assertEquals(89.907877, Descriptive.of(res.map { it.value("S")!! }).mean!!, 5.1e-7)
    }

    /** Valores del Excel 'Densidades_toyo (1).xlsx': L1 = 0,061 y Q = 2395 (resultados guardados por Excel). */
    @Test fun excelActualizado() {
        val base = registroDelExcel().copy(q = "2395")
        val r = base.conDensidades(base.densidades.mapIndexed { k, d -> if (k == 0) d.copy(l = "0.061") else d })
        val res = r.densidades.map { r.calcular(it) }
        val pExcel = listOf(2292.577232302752, 2311.554430718544, 2320.713486681072, 2326.3029525344086, 2350.6987089569375, 2168.9736504396687, 2193.2035844365437, 2159.2540518210126, 2127.3899025226783, 2178.9521927512183)
        val sExcel = listOf(95.7234752527245, 96.51584261872836, 96.89826666726815, 97.13164728744921, 98.15025924663622, 90.56257413109265, 91.57426239818554, 90.15674537874791, 88.82630073163583, 90.97921472865212)
        res.forEachIndexed { k, x -> assertEquals(pExcel[k], x.value("P")!!, 1e-9); assertEquals(sExcel[k], x.value("S")!!, 1e-9) }
    }

    @Test fun sinQNoHayCompactacion() {
        val r = registroDelExcel().copy(q = null)
        val x = r.calcular(r.densidades[0])
        assertNotNull(x.value("P")); assertNull(x.value("S"))
    }

    @Test fun datoFaltanteNoEsCero() {
        val r = registroDelExcel().let { it.conDensidades(listOf(it.densidades[0].copy(j = null))) }
        val x = r.calcular(r.densidades[0])
        assertNull(x.value("S")); assertNotNull(x.value("G"))
    }

    @Test fun guardarYLeer() = runBlocking {
        val r = registroDelExcel()
        c.db.registros().guardar(r)
        c.closeDb()
        val leido = c.db.registros().obtener(r.id)!!
        assertEquals(10, leido.densidades.size)
        assertEquals("7139", leido.densidades[0].a)
        assertEquals("0.06", leido.densidades[0].l)
        assertEquals(1, c.db.registros().todos().first().size)
    }

    @Test fun pdfConFormaDePlanilla() = runBlocking {
        c.pdfWriterFactory = { PdfBoxWriter() }
        val f = c.pdf.generar(registroDelExcel())
        val t = Loader.loadPDF(f).use { PDFTextStripper().getText(it) }
        assertTrue(t.contains("Peso frasco y arena inicial"))
        assertTrue(t.contains("2.294,97")); assertTrue(t.contains("91,98"))
        assertTrue(t.contains("89,91 %"))
        assertTrue(t.contains("NO CUMPLE"))
        assertTrue(t.contains("MÉTODO DEL CONO DE ARENA"))
        val out = File(System.getProperty("geodensidad.outDir") ?: "build/ejemplos").apply { mkdirs() }
        f.copyTo(File(out, "planilla_registro_ejemplo.pdf"), true)
        Unit
    }

    @Test fun resumenConCriterioDelFormato() {
        val r = registroDelExcel()
        val cols = r.densidades.mapIndexed { i, d -> PlanillaBuilder.Columna(i + 1, "", "", "", "", null, null, null, null, null, null, null, null, r.calcular(d)) }
        val (kv, _) = PlanillaBuilder.resumen(cols.take(5), 2495.0)
        assertEquals("2.320,85 kg/m³", kv.first { it.first.startsWith("Dm") }.second)
        assertEquals("20,47 kg/m³", kv.first { it.first.startsWith("s ") }.second)
        assertEquals("0,685", kv.first { it.first.startsWith("k") }.second)
    }

    @Test fun respaldoYRestauracion() = runBlocking {
        val r = registroDelExcel()
        c.db.registros().guardar(r)
        val zip = ByteArrayOutputStream().also { c.respaldo.exportar(it) }.toByteArray()
        c.db.registros().guardar(Registro(registro = "posterior"))
        assertEquals(2, c.db.registros().contar())
        val n = c.respaldo.restaurar(zip.inputStream())
        assertEquals(1, n)
        assertEquals(1, c.db.registros().contar())
        assertEquals(10, c.db.registros().obtener(r.id)!!.densidades.size)
        try { c.respaldo.restaurar("no es zip".byteInputStream()); fail() } catch (e: Exception) {}
        Unit
    }
}

/** Reproduce el PDF del usuario: L = "0,061" (decimal) y Q = "2.395" (punto de miles) deben dar lo mismo que el Excel. */
@RunWith(RobolectricTestRunner::class)
class DigitacionComoExcelTest {
    @Test fun lDecimalYQConPuntoDeMiles() {
        val l = (com.geodensidad.app.ui.interpretar("l", "0,061") as com.geodensidad.core.number.ParsedDecimal.Valid).value
        val q = (com.geodensidad.app.ui.interpretar("q", "2.395") as com.geodensidad.core.number.ParsedDecimal.Valid).value
        val f = (com.geodensidad.app.ui.interpretar("f", "1.396") as com.geodensidad.core.number.ParsedDecimal.Valid).value
        assertEquals(0.061, l.toDouble(), 0.0); assertEquals(2395.0, q.toDouble(), 0.0); assertEquals(1.396, f.toDouble(), 0.0)
        val base = registroDelExcel().copy(q = q.toPlainString())
        val r = base.conDensidades(listOf(base.densidades[0].copy(l = l.toPlainString(), f = f.toPlainString())))
        val x = r.calcular(r.densidades[0])
        assertEquals(2292.577232302752, x.value("P")!!, 1e-9)
        assertEquals(95.7234752527245, x.value("S")!!, 1e-9)
    }
}
