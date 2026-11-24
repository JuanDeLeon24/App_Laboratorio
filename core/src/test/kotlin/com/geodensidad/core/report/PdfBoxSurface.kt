package com.geodensidad.core.report

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType0Font
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import org.apache.pdfbox.util.Matrix
import java.io.File

/** Superficie PDFBox SOLO para pruebas JVM y generación de ejemplos; la app usa android.graphics.pdf. */
class PdfBoxSurface(fontDir: File) : PdfSurface, AutoCloseable {
    val doc = PDDocument()
    private val regular = PDType0Font.load(doc, File(fontDir, "DejaVuSans.ttf"))
    private val bold = PDType0Font.load(doc, File(fontDir, "DejaVuSans-Bold.ttf"))
    private var cs: PDPageContentStream? = null
    private var h = 0f
    private val images = HashMap<String, PDImageXObject>()

    private fun font(s: TextStyle) = if (s.bold) bold else regular
    override fun textWidth(text: String, style: TextStyle) = font(style).getStringWidth(text) / 1000f * style.size

    override fun beginPage(width: Float, height: Float) {
        val p = PDPage(PDRectangle(width, height)); doc.addPage(p); h = height
        cs = PDPageContentStream(doc, p)
    }
    override fun endPage() { cs?.close(); cs = null }

    private fun color(argb: Int, fill: Boolean) {
        val c = cs!!
        val a = (argb ushr 24) and 0xFF
        val gs = PDExtendedGraphicsState()
        if (fill) gs.nonStrokingAlphaConstant = a / 255f else gs.strokingAlphaConstant = a / 255f
        c.setGraphicsStateParameters(gs)
        val r = (argb shr 16) and 0xFF; val g = (argb shr 8) and 0xFF; val b = argb and 0xFF
        if (fill) c.setNonStrokingColor(r / 255f, g / 255f, b / 255f) else c.setStrokingColor(r / 255f, g / 255f, b / 255f)
    }

    override fun drawText(text: String, x: Float, baselineY: Float, style: TextStyle) {
        val c = cs!!; color(style.color, true)
        c.beginText(); c.setFont(font(style), style.size); c.newLineAtOffset(x, h - baselineY); c.showText(text); c.endText()
    }
    override fun drawTextRotated(text: String, x: Float, y: Float, style: TextStyle, degrees: Float) {
        val c = cs!!; color(style.color, true)
        c.beginText(); c.setFont(font(style), style.size)
        c.setTextMatrix(Matrix.getRotateInstance(Math.toRadians(-degrees.toDouble()), x, h - y))
        c.showText(text); c.endText()
    }
    override fun drawLine(x1: Float, y1: Float, x2: Float, y2: Float, color: Int, width: Float, dashed: Boolean) {
        val c = cs!!; color(color, false); c.setLineWidth(width)
        if (dashed) c.setLineDashPattern(floatArrayOf(3f, 2f), 0f) else c.setLineDashPattern(floatArrayOf(), 0f)
        c.moveTo(x1, h - y1); c.lineTo(x2, h - y2); c.stroke()
        c.setLineDashPattern(floatArrayOf(), 0f)
    }
    override fun drawRect(x: Float, y: Float, w: Float, h2: Float, fill: Int?, stroke: Int?, strokeWidth: Float) {
        val c = cs!!
        if (fill != null) { color(fill, true); c.addRect(x, h - y - h2, w, h2); c.fill() }
        if (stroke != null) { color(stroke, false); c.setLineWidth(strokeWidth); c.addRect(x, h - y - h2, w, h2); c.stroke() }
    }
    override fun drawCircle(cx: Float, cy: Float, r: Float, fill: Int) {
        val c = cs!!; color(fill, true)
        val k = 0.5523f * r; val y = h - cy
        c.moveTo(cx - r, y); c.curveTo(cx - r, y + k, cx - k, y + r, cx, y + r); c.curveTo(cx + k, y + r, cx + r, y + k, cx + r, y)
        c.curveTo(cx + r, y - k, cx + k, y - r, cx, y - r); c.curveTo(cx - k, y - r, cx - r, y - k, cx - r, y); c.fill()
    }
    override fun drawImage(path: String, x: Float, y: Float, w: Float, h2: Float) {
        val img = images.getOrPut(path) { PDImageXObject.createFromFile(path, doc) }
        color(0xFFFFFFFF.toInt(), true)
        cs!!.drawImage(img, x, h - y - h2, w, h2)
    }
    fun save(f: File) { f.parentFile.mkdirs(); doc.save(f) }
    override fun close() = doc.close()
}
