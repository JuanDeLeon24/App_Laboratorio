package com.geodensidad.app.report

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.geodensidad.core.report.PdfSurface
import com.geodensidad.core.report.TextStyle
import java.io.File
import java.io.OutputStream

/**
 * Dibuja los comandos del motor de composición sobre un Canvas de Android.
 * Se usa tanto para PdfDocument (texto vectorial) como para los gráficos en pantalla.
 */
open class CanvasSurface(var canvas: Canvas?, private val scale: Float = 1f) : PdfSurface {
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val shapePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val regular = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    private val bold = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    protected val pageBitmaps = mutableListOf<Bitmap>()

    private fun setText(style: TextStyle) {
        textPaint.typeface = if (style.bold) bold else regular
        textPaint.textSize = style.size
        textPaint.color = style.color
    }

    override fun textWidth(text: String, style: TextStyle): Float { setText(style); return textPaint.measureText(text) }
    override fun beginPage(width: Float, height: Float) {}
    override fun endPage() {}

    private inline fun draw(block: Canvas.() -> Unit) {
        val c = canvas ?: return
        c.save(); c.scale(scale, scale); c.block(); c.restore()
    }

    override fun drawText(text: String, x: Float, baselineY: Float, style: TextStyle) = draw { setText(style); drawText(text, x, baselineY, textPaint) }
    override fun drawTextRotated(text: String, x: Float, y: Float, style: TextStyle, degrees: Float) = draw {
        setText(style); rotate(degrees, x, y); drawText(text, x, y, textPaint)
    }
    override fun drawLine(x1: Float, y1: Float, x2: Float, y2: Float, color: Int, width: Float, dashed: Boolean) = draw {
        shapePaint.style = Paint.Style.STROKE; shapePaint.color = color; shapePaint.strokeWidth = width
        shapePaint.pathEffect = if (dashed) DashPathEffect(floatArrayOf(3f, 2f), 0f) else null
        drawLine(x1, y1, x2, y2, shapePaint); shapePaint.pathEffect = null
    }
    override fun drawRect(x: Float, y: Float, w: Float, h: Float, fill: Int?, stroke: Int?, strokeWidth: Float) = draw {
        if (fill != null) { shapePaint.style = Paint.Style.FILL; shapePaint.color = fill; drawRect(x, y, x + w, y + h, shapePaint) }
        if (stroke != null) { shapePaint.style = Paint.Style.STROKE; shapePaint.color = stroke; shapePaint.strokeWidth = strokeWidth; drawRect(x, y, x + w, y + h, shapePaint) }
    }
    override fun drawCircle(cx: Float, cy: Float, r: Float, fill: Int) = draw { shapePaint.style = Paint.Style.FILL; shapePaint.color = fill; drawCircle(cx, cy, r, shapePaint) }
    override fun drawImage(path: String, x: Float, y: Float, w: Float, h: Float) {
        val bmp = decodeForBox(path, w * scale, h * scale) ?: run {
            drawRect(x, y, w, h, 0xFFEEEEEE.toInt(), 0xFF999999.toInt(), 0.5f)
            drawText("Imagen no disponible", x + 6f, y + 14f, TextStyle(8f, color = 0xFF993333.toInt()))
            return
        }
        pageBitmaps += bmp
        draw { drawBitmap(bmp, null, RectF(x, y, x + w, y + h), Paint(Paint.FILTER_BITMAP_FLAG)) }
    }

    /** Decodifica con submuestreo (~2,5 px por punto) para no cargar imágenes completas en memoria. */
    private fun decodeForBox(path: String, w: Float, h: Float): Bitmap? = try {
        val f = File(path)
        if (!f.exists()) null else {
            val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, b)
            val target = maxOf(w, h) * 2.5f
            var s = 1
            while (maxOf(b.outWidth, b.outHeight) / (s * 2) >= target) s *= 2
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = s })
        }
    } catch (e: OutOfMemoryError) { null } catch (e: Exception) { null }

    fun recyclePageBitmaps() { pageBitmaps.forEach { it.recycle() }; pageBitmaps.clear() }
}

/** Escritor PDF nativo de Android (android.graphics.pdf.PdfDocument, parte de la plataforma AOSP). */
class AndroidPdfWriter : CanvasSurface(null) {
    private val doc = PdfDocument()
    private var page: PdfDocument.Page? = null
    private var n = 0

    override fun beginPage(width: Float, height: Float) {
        n++
        page = doc.startPage(PdfDocument.PageInfo.Builder(Math.round(width), Math.round(height), n).create())
        canvas = page!!.canvas
    }
    override fun endPage() { page?.let { doc.finishPage(it) }; page = null; canvas = null; recyclePageBitmaps() }
    fun writeTo(out: OutputStream) = doc.writeTo(out)
    fun close() { recyclePageBitmaps(); doc.close() }
}
