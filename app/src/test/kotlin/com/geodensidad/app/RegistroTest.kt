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
