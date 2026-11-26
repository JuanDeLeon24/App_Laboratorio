package com.geodensidad.app.report

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import java.io.File

/** Trazo de firma: lista de puntos (x, y) normalizados 0..1 respecto del recuadro de firma. */
typealias Trazo = List<Pair<Float, Float>>

/** Firmas manuscritas por registro: files/firmas/<idRegistro>-<rol>.png (rol = elaboro | aprobo). */
class Firmas(private val context: Context) {
    enum class Rol(val archivo: String, val titulo: String) { ELABORO("elaboro", "Elaboró"), APROBO("aprobo", "Aprobó / recibe") }

    private fun dir() = File(context.filesDir, "firmas").apply { mkdirs() }
    fun archivo(registroId: String, rol: Rol) = File(dir(), "$registroId-${rol.archivo}.png")
    fun existe(registroId: String, rol: Rol) = archivo(registroId, rol).exists()

    /** (ruta, ancho, alto) para el PDF, o null si no hay firma guardada. */
    fun paraPdf(registroId: String, rol: Rol): Triple<String, Int, Int>? {
        val f = archivo(registroId, rol).takeIf { it.exists() } ?: return null
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        return if (o.outWidth > 0) Triple(f.path, o.outWidth, o.outHeight) else null
    }

    fun borrar(registroId: String, rol: Rol) { archivo(registroId, rol).delete() }

    /**
     * Dibuja los trazos en una imagen PNG con fondo transparente y la recorta al área firmada
     * (con margen), para que en el PDF ocupe bien el recuadro.
     */
    fun guardar(registroId: String, rol: Rol, trazos: List<Trazo>, anchoPx: Int = 900, altoPx: Int = 360): File? {
        val validos = trazos.filter { it.isNotEmpty() }
        if (validos.isEmpty()) { borrar(registroId, rol); return null }
        val xs = validos.flatten().map { it.first }; val ys = validos.flatten().map { it.second }
        val margen = 0.04f
        val x0 = (xs.min() - margen).coerceAtLeast(0f); val x1 = (xs.max() + margen).coerceAtMost(1f)
        val y0 = (ys.min() - margen).coerceAtLeast(0f); val y1 = (ys.max() + margen).coerceAtMost(1f)
        val w = ((x1 - x0) * anchoPx).toInt().coerceAtLeast(40); val h = ((y1 - y0) * altoPx).toInt().coerceAtLeast(40)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF0B1F3A.toInt(); style = Paint.Style.STROKE; strokeWidth = 5f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }
        val c = Canvas(bmp)
        fun px(p: Pair<Float, Float>) = (p.first - x0) * anchoPx to (p.second - y0) * altoPx
        for (t in validos) {
            if (t.size == 1) { val (x, y) = px(t[0]); c.drawCircle(x, y, 3f, paint.apply { style = Paint.Style.FILL }); paint.style = Paint.Style.STROKE; continue }
            val path = Path(); val (sx, sy) = px(t[0]); path.moveTo(sx, sy)
            t.drop(1).forEach { val (x, y) = px(it); path.lineTo(x, y) }
            c.drawPath(path, paint)
        }
        val f = archivo(registroId, rol)
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        return f
    }
}
