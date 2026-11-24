package com.geodensidad.app

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Recorrido de la interfaz real (Compose) bajo Robolectric: crear proyecto → actividad →
 * jornada → muestra → capturar mediciones → ver resultado. Guarda capturas de pantalla.
 */
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

    private fun waitText(t: String, substring: Boolean = true) {
        try { rule.waitUntil(20_000) { org.robolectric.shadows.ShadowLooper.idleMainLooper(300, java.util.concurrent.TimeUnit.MILLISECONDS); (rule.onAllNodesWithText(t, substring = substring).fetchSemanticsNodes().isNotEmpty() || rule.onAllNodesWithContentDescription(t).fetchSemanticsNodes().isNotEmpty()) } }
        catch (e: Throwable) { throw AssertionError("No apareció «$t». Pantalla:\n" + runCatching { rule.onRoot(useUnmergedTree = true).printToString(maxDepth = 60) }.getOrDefault("?"), e) }
    }

    @Test fun recorridoPrincipal() {
        waitText("Nuevo proyecto")
        shot("01_inicio")
        // Proyecto, actividad y jornada por el repositorio; la captura se recorre por la interfaz.
        val c = TestSupport.app.container
        val sid = kotlinx.coroutines.runBlocking {
            val p = c.repo.createProject("VA-01", "Vías de acceso", "Entidad de prueba")
            val a = c.repo.createActivity(p, "Toma de densidades para granulares")
            c.repo.createSample(a, c.repo.createJourney(a))
        }
        waitText("Vías de acceso")
        rule.onNodeWithText("VA-01 · Entidad de prueba").performClick()
        waitText("Toma de densidades para granulares")
        shot("02_proyecto")
        rule.onNodeWithText("Toma de densidades para granulares").performClick()
        waitText("DEN-0001")
        shot("03_actividad")
        rule.onNodeWithText("DEN-0001", substring = true).performClick()
        waitText("Revisión 1")
        fun num(label: String, v: String) {
            val m = hasText(label, substring = true) and hasSetTextAction()
            rule.onAllNodes(hasScrollToNodeAction())[0].performScrollToNode(m)
            rule.onNode(m).performTextReplacement(v)
        }
        num("PR *", "PR 003+150")
        num("D · Constante del cono", "1735"); num("F · Densidad de la arena", "1,396")
        num("A · Peso frasco y arena inicial", "7139"); num("B · Peso frasco y arena restante", "3317"); num("J · Peso material extraído", "3694"); num("H · Material retenido", "410"); num("L · Humedad", "6")
        num("Q · Densidad máxima", "2495"); num("R · Humedad óptima", "6,68")
        rule.onAllNodes(hasScrollToNodeAction())[0].performScrollToIndex(0)
        waitText("91,98 %")
        rule.onNodeWithText("2.294,97 kg/m³").assertExists()
        rule.onAllNodes(hasScrollToNodeAction())[0].performScrollToNode(hasText("Densidad seca corregida (Di)"))
        shot("04_muestra_resultado")
        rule.onAllNodes(hasScrollToNodeAction())[0].performScrollToNode(hasText("Resultados y trazabilidad")); rule.onNodeWithText("Resultados y trazabilidad").performClick()
        rule.onAllNodes(hasScrollToNodeAction())[0].performScrollToNode(hasText("P  Densidad seca corregida", substring = true)); rule.onNodeWithText("P  Densidad seca corregida", substring = true).performClick()
        rule.onNodeWithText("P = [O × (100 − K)] / [100 − ((O × K) / (I × 1000))]").assertExists()
        shot("05_trazabilidad")
        // el guardado automático persiste en la base
        waitText("Guardado ")
        val saved = kotlinx.coroutines.runBlocking { TestSupport.app.container.db.samples().count() }
        assertTrue(saved == 1)
    }

    @Test fun panelYLoteConDatosDemostrativos() {
        kotlinx.coroutines.runBlocking { TestSupport.app.container.demo.load() }
        waitText("DEMO – Vías de acceso")
        shot("06_inicio_demo")
        rule.onNodeWithText("Toma de densidades para granulares (DEMO)").performClick()
        waitText("DEN-0001")
        shot("07_lista_muestras")
        rule.onNodeWithText("Lotes").performClick()
        waitText("Provisional: no cumple")
        shot("08_lotes")
        rule.onNodeWithText("Panel").performClick()
        waitText("Compactación media")
        rule.onNodeWithText("89,91 %", substring = true).assertExists()
        shot("09_panel")
    }
}
