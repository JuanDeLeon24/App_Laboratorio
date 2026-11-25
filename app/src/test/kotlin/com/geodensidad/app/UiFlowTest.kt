package com.geodensidad.app

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Recorrido de la interfaz real: lista → nuevo registro → digitar una densidad → ver C…S. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h914dp-xhdpi")
class UiFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val out = File(System.getProperty("geodensidad.outDir") ?: "build/ejemplos", "capturas").apply { mkdirs() }

    private fun shot(name: String) {
        rule.waitForIdle()
        val v = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { v.draw(android.graphics.Canvas(bmp)) }
        File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun esperar(t: String) = try {
        rule.waitUntil(20_000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper(300, java.util.concurrent.TimeUnit.MILLISECONDS)
            rule.onAllNodesWithText(t, substring = true).fetchSemanticsNodes().isNotEmpty() || rule.onAllNodesWithContentDescription(t).fetchSemanticsNodes().isNotEmpty()
        }
    } catch (e: Throwable) { throw AssertionError("No apareció «$t»:\n" + rule.onRoot().printToString(40), e) }

    private fun lista() = rule.onAllNodes(hasScrollToNodeAction())[0]
    /** Digita con el teclado propio de la app: toca el campo y pulsa las teclas. */
    private fun teclear(sym: String, v: String) {
        lista().performScrollToNode(hasTestTag("campo_$sym"))
        rule.onNodeWithTag("campo_$sym").performClick()
        v.forEach { ch -> rule.onNodeWithTag(if (ch == ',') "tecla_coma" else "tecla_$ch").performClick() }
    }

    private fun escribir(label: String, v: String) {
        val m = hasText(label, substring = true) and hasSetTextAction()
        lista().performScrollToNode(m); rule.onNode(m).performTextReplacement(v)
    }

    @Test fun capturarUnaDensidad() {
        esperar("Nuevo registro")
        shot("01_lista_vacia")
        rule.onNodeWithContentDescription("Nuevo registro").performClick()
        esperar("Encabezado")
        escribir("Proyecto", "Vías de acceso"); escribir("Registro", "7")
        shot("02_encabezado")
        // A y luego "Siguiente" recorre B, D, F, H, I, J, L, Q, R en el orden del Excel
        teclear("A", "7139")
        for ((sym, v) in listOf("B" to "3317", "D" to "1735", "F" to "1,396", "H" to "410")) {
            rule.onNodeWithTag("tecla_siguiente").performClick()
            esperar("Densidad 1 · $sym")
            v.forEach { ch -> rule.onNodeWithTag(if (ch == ',') "tecla_coma" else "tecla_$ch").performClick() }
        }
        rule.onNodeWithTag("tecla_siguiente").performClick(); esperar("Densidad 1 · I") // I ya trae 2,667
        rule.onNodeWithTag("tecla_siguiente").performClick(); esperar("Densidad 1 · J")
        "3694".forEach { rule.onNodeWithTag("tecla_$it").performClick() }
        rule.onNodeWithTag("tecla_siguiente").performClick(); esperar("Densidad 1 · L")
        rule.onNodeWithTag("tecla_0").performClick(); rule.onNodeWithTag("tecla_coma").performClick(); rule.onNodeWithTag("tecla_0").performClick(); rule.onNodeWithTag("tecla_6").performClick()
        shot("03a_teclado_propio")
        lista().performScrollToNode(hasText("No se calcula la compactación", substring = true)) // sin Q todavía no hay S
        rule.onNodeWithTag("tecla_siguiente").performClick(); esperar("Densidad 1 · Q")
        "2495".forEach { rule.onNodeWithTag("tecla_$it").performClick() }
        rule.onNodeWithTag("tecla_siguiente").performClick(); esperar("Densidad 1 · R")
        "6,68".forEach { ch -> rule.onNodeWithTag(if (ch == ',') "tecla_coma" else "tecla_$ch").performClick() }
        rule.onNodeWithTag("tecla_listo").performClick()
        lista().performScrollToNode(hasText("Compactación del terreno", substring = true))
        esperar("91,98 %")
        rule.onNodeWithText("2.294,97 kg/m³").assertExists()
        shot("03_densidad_resultados")
        lista().performScrollToNode(hasText("Agregar")); rule.onNodeWithText("Agregar").performClick()
        esperar("Densidad No. 2")
        lista().performScrollToNode(hasText("Resumen del registro"))
        shot("04_resumen")
        val c = (rule.activity.application as GeoDensidadApp).container
        rule.waitUntil(10_000) { org.robolectric.shadows.ShadowLooper.idleMainLooper(300, java.util.concurrent.TimeUnit.MILLISECONDS); runBlocking { c.db.registros().contar() } == 1 }
        assertEquals("7", runBlocking { c.db.registros().todos().first() }.single().registro)
    }

    @Test fun listaConRegistro() {
        val c = (rule.activity.application as GeoDensidadApp).container
        runBlocking { c.db.registros().guardar(registroDelExcel()) }
        esperar("Registro 1")
        shot("05_lista")
        rule.onNodeWithText("Registro 1", substring = true).performClick()
        esperar("Encabezado")
        lista().performScrollToNode(hasText("Densidad No. 1"))
        shot("06_registro_densidad")
        lista().performScrollToNode(hasText("Resumen del registro"))
        esperar("NO CUMPLE")
        shot("07_registro_resumen")
    }
}
