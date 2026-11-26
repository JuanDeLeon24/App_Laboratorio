package com.geodensidad.core.report

/** Geometría A4 en puntos. */
object A4 { const val W = 595.28f; const val H = 841.89f }

/**
 * Motor de composición por flujo: coloca bloques de arriba hacia abajo, crea páginas nuevas
 * cuando no caben y mantiene juntos los bloques indivisibles (foto + pie, filas de tabla).
 */
class Layout(
    val m: TextMeasurer,
    val marginX: Float = 40f,
    val top: Float = 112f,
    val bottom: Float = A4.H - 58f,
) {
    val pages = mutableListOf<Page>()
    var y = 0f
    val left get() = marginX
    val width get() = A4.W - 2 * marginX
    val page get() = pages.last()

    init { newPage() }

    fun newPage() { pages += Page(A4.W, A4.H); y = top }

    fun remaining() = bottom - y

    /** Garantiza espacio; si no cabe, salta de página. Devuelve true si saltó. */
    fun ensure(h: Float): Boolean {
        if (y + h > bottom && y > top + 0.1f) { newPage(); return true }
        return false
    }

    fun add(c: Cmd) { page.cmds += c }

    fun space(h: Float) { if (y + h > bottom) newPage() else y += h }

    fun lineHeight(style: TextStyle) = style.size * 1.32f

    fun paragraph(text: String, style: TextStyle = Styles.body, indent: Float = 0f, maxW: Float = width - indent) {
        val lh = lineHeight(style)
        for (line in Wrap.lines(text, maxW, style, m)) {
            ensure(lh)
            add(Cmd.Text(line, left + indent, y + style.size, style))
            y += lh
        }
    }

    fun heading(text: String, level: Int = 1) {
        val style = if (level == 1) Styles.h1 else Styles.h2
        val h = lineHeight(style) + 10f
        ensure(h + 40f) // evita encabezados huérfanos al pie
        y += 4f
        if (level == 1) {
            add(Cmd.Rect(left, y, width, lineHeight(style) + 4f, RColor.NAVY, null))
            add(Cmd.Text(text, left + 6f, y + style.size + 2f, style.copy(color = RColor.WHITE)))
        } else {
            add(Cmd.Text(text, left, y + style.size, style.copy(color = RColor.NAVY)))
            add(Cmd.Line(left, y + lineHeight(style) + 1f, left + width, y + lineHeight(style) + 1f, RColor.SKY, 0.8f))
        }
        y += h
    }

    /** Pares etiqueta/valor en una rejilla de [cols] columnas. */
    fun keyValues(pairs: List<Pair<String, String>>, cols: Int = 2, labelFrac: Float = 0.42f) {
        val colW = width / cols
        val labelW = colW * labelFrac
        val ls = Styles.label; val vs = Styles.body
        pairs.chunked(cols).forEach { row ->
            val cells = row.map { (k, v) ->
                Wrap.lines(k, labelW - 4f, ls, m) to Wrap.lines(v.ifBlank { "—" }, colW - labelW - 6f, vs, m)
            }
            val lines = cells.maxOf { maxOf(it.first.size, it.second.size) }
            val h = lines * lineHeight(vs) + 5f
            ensure(h)
            add(Cmd.Line(left, y + h, left + width, y + h, RColor.MIST, 0.5f))
            cells.forEachIndexed { i, (kl, vl) ->
                val x = left + i * colW
                kl.forEachIndexed { li, t -> add(Cmd.Text(t, x + 2f, y + 2.5f + ls.size + li * lineHeight(vs), ls)) }
                vl.forEachIndexed { li, t -> add(Cmd.Text(t, x + labelW, y + 2.5f + vs.size + li * lineHeight(vs), vs)) }
            }
            y += h
        }
        y += 4f
    }

    data class Col(val title: String, val weight: Float, val alignRight: Boolean = false)

    /** Tabla con encabezado repetido en cada página y filas que no se parten. */
    fun table(cols: List<Col>, rows: List<List<String>>, style: TextStyle = Styles.table, rowFill: (Int) -> Int? = { null }) {
        val total = cols.sumOf { it.weight.toDouble() }.toFloat()
        val widths = cols.map { it.weight / total * width }
        val hs = style.copy(bold = true, color = RColor.WHITE)
        val lh = lineHeight(style)
        fun header() {
            val hl = cols.mapIndexed { i, c -> Wrap.lines(c.title, widths[i] - 4f, hs, m) }
            val h = hl.maxOf { it.size } * lh + 4f
            ensure(h + lh + 4f)
            add(Cmd.Rect(left, y, width, h, RColor.NAVY, null))
            var x = left
            hl.forEachIndexed { i, ls ->
                ls.forEachIndexed { li, t ->
                    val tx = if (cols[i].alignRight) x + widths[i] - 2f - m.textWidth(t, hs) else x + 2f
                    add(Cmd.Text(t, tx, y + 2f + hs.size + li * lh, hs))
                }
                x += widths[i]
            }
            y += h
        }
        header()
        rows.forEachIndexed { ri, row ->
            val cl = row.mapIndexed { i, t -> Wrap.lines(t.ifEmpty { "—" }, widths[i] - 4f, style, m) }
            val h = cl.maxOf { it.size } * lh + 4f
            if (y + h > bottom) { newPage(); header() }
            (rowFill(ri) ?: if (ri % 2 == 1) RColor.ROW_ALT else null)?.let { add(Cmd.Rect(left, y, width, h, it, null)) }
            var x = left
            cl.forEachIndexed { i, ls ->
                ls.forEachIndexed { li, t ->
                    val tx = if (cols[i].alignRight) x + widths[i] - 2f - m.textWidth(t, style) else x + 2f
                    add(Cmd.Text(t, tx, y + 2f + style.size + li * lh, style))
                }
                x += widths[i]
            }
            add(Cmd.Line(left, y + h, left + width, y + h, RColor.GRID, 0.3f))
            y += h
        }
        y += 6f
    }

    /** Fotografía con su pie inmediatamente debajo, siempre en la misma página. */
    fun figure(path: String, pxW: Int, pxH: Int, caption: List<String>, maxH: Float, boxW: Float = width, x0: Float = left) {
        val cs = Styles.caption
        val capLines = caption.flatMapIndexed { i, c -> Wrap.lines(c, boxW - 8f, if (i == 0) cs.copy(bold = true) else cs, m).map { it to (i == 0) } }
        val capH = capLines.size * lineHeight(cs) + 6f
        val ratio = if (pxW > 0 && pxH > 0) pxW.toFloat() / pxH else 4f / 3f
        var w = boxW; var h = w / ratio
        if (h > maxH) { h = maxH; w = h * ratio }
        ensure(h + capH + 6f)
        val ix = x0 + (boxW - w) / 2
        add(Cmd.Rect(ix - 0.5f, y - 0.5f, w + 1f, h + 1f, null, RColor.GRID, 0.5f))
        add(Cmd.Image(path, ix, y, w, h))
        y += h + 3f
        add(Cmd.Rect(x0, y, boxW, capH - 2f, RColor.ROW_ALT, null))
        capLines.forEachIndexed { i, (t, first) ->
            add(Cmd.Text(t, x0 + 4f, y + 2f + cs.size + i * lineHeight(cs), if (first) cs.copy(bold = true, color = RColor.NAVY) else cs))
        }
        y += capH + 8f
    }

    fun chart(spec: ChartSpec, height: Float = 210f) {
        ensure(height + 8f)
        ChartRenderer.draw(spec, left, y, width, height, m) { add(it) }
        y += height + 10f
    }

    fun note(text: String, color: Int = RColor.WARN) {
        val st = Styles.small.copy(color = color)
        val lines = Wrap.lines(text, width - 14f, st, m)
        val h = lines.size * lineHeight(st) + 6f
        ensure(h)
        add(Cmd.Rect(left, y, 3f, h, color, null))
        lines.forEachIndexed { i, t -> add(Cmd.Text(t, left + 8f, y + 3f + st.size + i * lineHeight(st), st)) }
        y += h + 5f
    }

    /** Recuadro de firma: etiqueta, imagen opcional de la firma manuscrita (proporción conservada), línea y nombre. */
    data class FirmaCaja(val etiqueta: String, val nombre: String?, val imagen: String? = null, val pxW: Int = 0, val pxH: Int = 0)

    fun signatures(labels: List<Pair<String, String?>>) = firmas(labels.map { FirmaCaja(it.first, it.second) })

    fun firmas(cajas: List<FirmaCaja>) {
        val h = 92f
        ensure(h)
        val w = width / cajas.size
        cajas.forEachIndexed { i, c ->
            val x = left + i * w
            add(Cmd.Rect(x + 4f, y, w - 8f, h - 6f, null, RColor.GRID, 0.6f))
            add(Cmd.Text(c.etiqueta, x + 10f, y + 13f, Styles.label.copy(bold = true)))
            val lineaY = y + 64f
            if (c.imagen != null && c.pxW > 0 && c.pxH > 0) {
                val maxW = w - 28f; val maxH = 46f
                val r = c.pxW.toFloat() / c.pxH
                var iw = maxW; var ih = iw / r
                if (ih > maxH) { ih = maxH; iw = ih * r }
                add(Cmd.Image(c.imagen, x + 10f + (maxW - iw) / 2, lineaY - ih - 1f, iw, ih))
            }
            add(Cmd.Line(x + 10f, lineaY, x + w - 14f, lineaY, RColor.SLATE, 0.5f))
            add(Cmd.Text("Firma", x + 10f, lineaY + 9f, Styles.small))
            add(Cmd.Text("Nombre: ${c.nombre?.takeIf { it.isNotBlank() } ?: "________________"}", x + 10f, lineaY + 19f, Styles.small))
        }
        y += h + 4f
    }
}

object Styles {
    val h1 = TextStyle(11f, bold = true, color = RColor.WHITE)
    val h2 = TextStyle(10f, bold = true, color = RColor.NAVY)
    val body = TextStyle(8.6f)
    val label = TextStyle(7.8f, color = RColor.STEEL)
    val table = TextStyle(7.2f)
    val caption = TextStyle(7.6f)
    val small = TextStyle(7f, color = RColor.STEEL)
    val title = TextStyle(10.5f, bold = true, color = RColor.NAVY)
}
