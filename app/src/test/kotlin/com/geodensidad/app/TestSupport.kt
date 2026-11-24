package com.geodensidad.app

import androidx.test.core.app.ApplicationProvider
import com.geodensidad.app.domain.SettingsRepository
import kotlinx.coroutines.runBlocking
import java.io.File

object TestSupport {
    val app: GeoDensidadApp get() = ApplicationProvider.getApplicationContext()

    /** Contenedor limpio sobre los archivos reales del contexto de prueba. */
    fun freshContainer(): AppContainer {
        val c = app.container
        c.closeDb()
        app.getDatabasePath("geodensidad.db").parentFile?.listFiles()?.forEach { it.delete() }
        app.filesDir.listFiles()?.filter { it.name != "datastore" }?.forEach { it.deleteRecursively() }
        runBlocking { c.settings.update { it.copy(responsible = "Ing. Prueba") }; c.open() }
        return c
    }

    fun reopen(c: AppContainer): AppContainer { c.closeDb(); runBlocking { c.open() }; return c }

    fun jpeg(f: File, w: Int, h: Int): File {
        val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        val cv = android.graphics.Canvas(bmp)
        cv.drawColor(0xFF0B294B.toInt())
        cv.drawCircle(w / 2f, h / 2f, minOf(w, h) / 4f, android.graphics.Paint().apply { color = 0xFFFFFFFF.toInt() })
        f.parentFile?.mkdirs(); f.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it) }
        return f
    }
}
