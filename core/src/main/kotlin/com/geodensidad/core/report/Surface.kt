package com.geodensidad.core.report

/** Colores ARGB. Paleta institucional derivada del logotipo de Construcciones El Cóndor S.A. */
object RColor {
    const val BLACK = 0xFF1A1C1E.toInt()
    const val WHITE = 0xFFFFFFFF.toInt()
    const val NAVY = 0xFF0B294B.toInt()
    const val NAVY_DEEP = 0xFF041B37.toInt()
    const val STEEL = 0xFF435667.toInt()
    const val SLATE = 0xFF6E7F8F.toInt()
    const val SKY = 0xFFB5CCEB.toInt()
    const val MIST = 0xFFD1DCE5.toInt()
    const val ROW_ALT = 0xFFF2F5F8.toInt()
    const val GRID = 0xFFC5CED6.toInt()
    const val OK = 0xFF1B6E3A.toInt()
    const val BAD = 0xFFA4262C.toInt()
    const val WARN = 0xFF8A5A00.toInt()
    const val WATERMARK = 0x30A4262C
}

data class TextStyle(val size: Float, val bold: Boolean = false, val color: Int = RColor.BLACK)

interface TextMeasurer {
    fun textWidth(text: String, style: TextStyle): Float
}

/**
 * Superficie de dibujo de páginas PDF. Origen arriba-izquierda, eje y hacia abajo, unidades en puntos.
 * Implementaciones: Canvas de Android (PdfDocument y pantalla) y PDFBox (pruebas JVM).
 */
interface PdfSurface : TextMeasurer {
    fun beginPage(width: Float, height: Float)
    fun endPage()
    fun drawText(text: String, x: Float, baselineY: Float, style: TextStyle)
    fun drawTextRotated(text: String, x: Float, y: Float, style: TextStyle, degrees: Float)
    fun drawLine(x1: Float, y1: Float, x2: Float, y2: Float, color: Int, width: Float, dashed: Boolean = false)
    fun drawRect(x: Float, y: Float, w: Float, h: Float, fill: Int?, stroke: Int?, strokeWidth: Float = 0.5f)
    fun drawCircle(cx: Float, cy: Float, r: Float, fill: Int)
    /** Dibuja la imagen ajustada exactamente al rectángulo (el llamador conserva la proporción). */
    fun drawImage(path: String, x: Float, y: Float, w: Float, h: Float)
}

sealed interface Cmd {
    data class Text(val text: String, val x: Float, val y: Float, val style: TextStyle) : Cmd
    data class TextRot(val text: String, val x: Float, val y: Float, val style: TextStyle, val deg: Float) : Cmd
    data class Line(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val color: Int, val width: Float, val dashed: Boolean = false) : Cmd
    data class Rect(val x: Float, val y: Float, val w: Float, val h: Float, val fill: Int?, val stroke: Int?, val sw: Float = 0.5f) : Cmd
    data class Circle(val cx: Float, val cy: Float, val r: Float, val fill: Int) : Cmd
    data class Image(val path: String, val x: Float, val y: Float, val w: Float, val h: Float) : Cmd
}

fun Cmd.renderTo(s: PdfSurface) {
    when (this) {
        is Cmd.Text -> s.drawText(text, x, y, style)
        is Cmd.TextRot -> s.drawTextRotated(text, x, y, style, deg)
        is Cmd.Line -> s.drawLine(x1, y1, x2, y2, color, width, dashed)
        is Cmd.Rect -> s.drawRect(x, y, w, h, fill, stroke, sw)
        is Cmd.Circle -> s.drawCircle(cx, cy, r, fill)
        is Cmd.Image -> s.drawImage(path, x, y, w, h)
    }
}

class Page(val width: Float, val height: Float) { val cmds = mutableListOf<Cmd>() }

class PageList(val pages: List<Page>) {
    fun render(surface: PdfSurface, onPage: (Int, Int) -> Unit = { _, _ -> }) {
        pages.forEachIndexed { i, p ->
            onPage(i + 1, pages.size)
            surface.beginPage(p.width, p.height)
            p.cmds.forEach { it.renderTo(surface) }
            surface.endPage()
        }
    }
}

/** Ajuste de texto por palabras con corte de palabras demasiado largas. */
object Wrap {
    fun lines(text: String, maxWidth: Float, style: TextStyle, m: TextMeasurer): List<String> {
        val out = mutableListOf<String>()
        for (para in text.split('\n')) {
            if (para.isBlank()) { out += ""; continue }
            var line = ""
            for (word in para.split(' ').filter { it.isNotEmpty() }) {
                val candidate = if (line.isEmpty()) word else "$line $word"
                if (m.textWidth(candidate, style) <= maxWidth) { line = candidate; continue }
                if (line.isNotEmpty()) out += line
                if (m.textWidth(word, style) <= maxWidth) { line = word; continue }
                // palabra más larga que el ancho: se corta por caracteres
                var chunk = ""
                for (ch in word) {
                    if (m.textWidth(chunk + ch, style) > maxWidth && chunk.isNotEmpty()) { out += chunk; chunk = "" }
                    chunk += ch
                }
                line = chunk
            }
            out += line
        }
        return out
    }
}
