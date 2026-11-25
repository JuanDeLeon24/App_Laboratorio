package com.geodensidad.core.xlsx

import com.geodensidad.core.calc.ConoArenaLegacy
import com.geodensidad.core.calc.LegacyInputs
import com.geodensidad.core.number.DecimalText
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

class PlantillaTest {
    private fun hojas(nombre: String) = javaClass.getResourceAsStream("/excel/$nombre")!!.use { XlsxLector.leer(it) }

    @Test fun importaElExcelDelUsuario() {
        val hs = hojas("Densidades_toyo_1.xlsx")
        assertEquals(listOf("Cono de arena", "Hoja1"), hs.map { it.nombre })
        val plantillas = hs.filter { PlantillaConoArena.esPlantilla(it) }
        assertEquals(1, plantillas.size)
        val d = PlantillaConoArena.leer(plantillas.single())
        assertEquals("Vias de acceso Túnel del Toyo", d.proyecto)
        assertEquals("Material de corte K 3+100", d.procedencia)
        assertEquals("3130", d.prInicial); assertEquals("3220", d.prFinal); assertEquals("Der", d.lado)
        assertEquals("2022-03-09", d.fecha); assertEquals("N/A", d.lote); assertEquals("1", d.registro)
        assertEquals("", d.localizacion) // BUSCARV vacío = 0 → vacío
        assertEquals("2.667", d.i); assertEquals("2395", d.q); assertEquals("0.0668", d.r)
        assertEquals(10, d.columnas.size)
        val c1 = d.columnas[0]
        assertEquals("7139", c1.a); assertEquals("3317", c1.b); assertEquals("1735", c1.d); assertEquals("1.396", c1.f)
        assertEquals("410", c1.h); assertEquals("3694", c1.j); assertEquals("0.061", c1.l)
        assertEquals("1774", d.columnas[5].d); assertEquals("1.408", d.columnas[5].f) // valores guardados de =+C18 etc.
        // con los datos importados, S coincide con lo que Excel tenía guardado en C33
        val s1 = ConoArenaLegacy.calculate(LegacyInputs(7139.0, 3317.0, 1735.0, 1.396, 410.0, 2.667, 3694.0, 0.061, 2395.0, 0.0668)).value("S")!!
        assertEquals(95.7234752527245, s1, 1e-9)
    }

    @Test fun importaElPrimerExcel() {
        val d = PlantillaConoArena.leer(hojas("Densidades_toyo.xlsx").first { PlantillaConoArena.esPlantilla(it) })
        assertEquals("0.06", d.columnas[0].l); assertEquals("2495", d.q)
    }

    @Test fun hojaQueNoEsPlantilla() {
        assertFalse(PlantillaConoArena.esPlantilla(hojas("Densidades_toyo.xlsx").first { it.nombre == "Hoja1" }))
    }

    @Test fun archivoQueNoEsExcel() {
        try { XlsxLector.leer(ByteArrayInputStream("hola".toByteArray())); fail() } catch (e: XlsxException) {}
    }

    @Test fun exportarEImportarIdaYVuelta() {
        val original = PlantillaConoArena.leer(hojas("Densidades_toyo_1.xlsx").first { PlantillaConoArena.esPlantilla(it) }).copy(
            observaciones = "Ensayo con ñ y tildes: áéíóú", elaboro = "Ing. Elaboró", aprobo = "Ing. Aprobó", idApp = "id-123")
        val out = ByteArrayOutputStream()
        XlsxEscritor.escribir(out, listOf(PlantillaConoArena.escribir(original, "Cono de arena"), PlantillaConoArena.escribir(original.copy(registro = "2", idApp = "id-456"), "Cono de arena 2")))
        val dir = File(System.getProperty("geodensidad.samplesDir") ?: "build/ejemplos-pdf").apply { mkdirs() }
        File(dir, "exportado_dos_registros.xlsx").writeBytes(out.toByteArray())
        val leidas = XlsxLector.leer(ByteArrayInputStream(out.toByteArray())).filter { PlantillaConoArena.esPlantilla(it) }.map { PlantillaConoArena.leer(it) }
        assertEquals(2, leidas.size)
        assertEquals(original, leidas[0])
        assertEquals("2", leidas[1].registro); assertEquals("id-456", leidas[1].idApp)
    }
}
