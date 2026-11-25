package com.geodensidad.core.xlsx

import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory

/** Valor de una celda leída: texto (si es texto) o número (si es número). Las fórmulas se leen por su valor guardado. */
data class Celda(val texto: String?, val numero: Double?) {
    val comoTexto: String get() = texto ?: numero?.let { n -> if (n == Math.floor(n) && !n.isInfinite() && Math.abs(n) < 1e15) n.toLong().toString() else n.toString() } ?: ""
}

data class Hoja(val nombre: String, val celdas: Map<String, Celda>) {
    operator fun get(ref: String): Celda? = celdas[ref]
}

class XlsxException(msg: String) : Exception(msg)

/** Lector mínimo de .xlsx (Office Open XML): libro, hojas, cadenas compartidas y valores guardados. */
object XlsxLector {
    private const val MAX_ENTRADA = 40L * 1024 * 1024

    fun leer(input: InputStream): List<Hoja> {
        val archivos = HashMap<String, ByteArray>()
        try {
            ZipInputStream(input.buffered()).use { z ->
                while (true) {
                    val e = z.nextEntry ?: break
                    if (e.isDirectory) continue
                    val n = e.name.trimStart('/')
                    if (n.endsWith(".xml") || n.endsWith(".rels")) {
                        val bytes = z.readNBytesLimit(MAX_ENTRADA) ?: throw XlsxException("El archivo es demasiado grande")
                        archivos[n] = bytes
                    }
                }
            }
        } catch (e: XlsxException) { throw e } catch (e: Exception) { throw XlsxException("No es un archivo Excel (.xlsx) válido") }
        val libro = archivos["xl/workbook.xml"] ?: throw XlsxException("No es un archivo Excel (.xlsx) válido")
        val rels = archivos["xl/_rels/workbook.xml.rels"]?.let { relaciones(it) } ?: emptyMap()
        val compartidas = archivos["xl/sharedStrings.xml"]?.let { cadenas(it) } ?: emptyList()
        val doc = dom(libro)
        val hojas = doc.getElementsByTagNameNS("*", "sheet")
        return (0 until hojas.length).mapNotNull { i ->
            val s = hojas.item(i) as Element
            val nombre = s.getAttribute("name")
            val rid = s.getAttributeNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id")
            val destino = rels[rid] ?: return@mapNotNull null
            val ruta = if (destino.startsWith("/")) destino.trimStart('/') else "xl/" + destino.removePrefix("./")
            archivos[ruta]?.let { Hoja(nombre, celdas(it, compartidas)) }
        }
    }

    private fun ZipInputStream.readNBytesLimit(max: Long): ByteArray? {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(64 * 1024)
        var total = 0L
        while (true) { val n = read(buf); if (n < 0) break; total += n; if (total > max) return null; out.write(buf, 0, n) }
        return out.toByteArray()
    }

    private fun dom(bytes: ByteArray) = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        isExpandEntityReferences = false
    }.newDocumentBuilder().parse(ByteArrayInputStream(bytes))

    private fun relaciones(bytes: ByteArray): Map<String, String> {
        val l = dom(bytes).getElementsByTagNameNS("*", "Relationship")
        return (0 until l.length).associate { (l.item(it) as Element).let { e -> e.getAttribute("Id") to e.getAttribute("Target") } }
    }

    private fun texto(e: Element): String {
        val ts = e.getElementsByTagNameNS("*", "t")
        return (0 until ts.length).joinToString("") { ts.item(it).textContent }
    }

    private fun cadenas(bytes: ByteArray): List<String> {
        val si = dom(bytes).getElementsByTagNameNS("*", "si")
        return (0 until si.length).map { texto(si.item(it) as Element) }
    }

    private fun celdas(bytes: ByteArray, compartidas: List<String>): Map<String, Celda> {
        val cs = dom(bytes).getElementsByTagNameNS("*", "c")
        val out = HashMap<String, Celda>()
        for (i in 0 until cs.length) {
            val c = cs.item(i) as Element
            val ref = c.getAttribute("r").uppercase()
            val tipo = c.getAttribute("t")
            val v = c.getElementsByTagNameNS("*", "v").item(0)?.textContent
            val celda = when (tipo) {
                "s" -> v?.toIntOrNull()?.let { compartidas.getOrNull(it) }?.let { Celda(it, null) }
                "str" -> v?.let { Celda(it, null) }
                "inlineStr" -> Celda(texto(c), null)
                "b" -> v?.let { Celda(if (it == "1") "VERDADERO" else "FALSO", null) }
                "e" -> null
                else -> v?.toDoubleOrNull()?.takeIf { it.isFinite() }?.let { Celda(null, it) }
            }
            if (celda != null && ref.isNotEmpty()) out[ref] = celda
        }
        return out
    }
}

/** Contenido de una celda a escribir. */
sealed interface Valor {
    data class Texto(val s: String) : Valor
    data class Numero(val n: Double) : Valor
    /** Fórmula de Excel (sin '=') con su valor guardado para que se vea aunque Excel no recalcule. */
    data class Formula(val f: String, val cache: Double?) : Valor
}

/** Estilos disponibles en el libro generado. */
enum class Estilo(val id: Int) { NORMAL(0), TITULO(1), ETIQUETA(2), DATO(3), DATO_NUM(4), CALCULADO(5), DATO_DEC(6), CALCULADO_DEC(7) }

data class CeldaEscrita(val ref: String, val valor: Valor, val estilo: Estilo = Estilo.NORMAL)

data class HojaEscrita(val nombre: String, val celdas: List<CeldaEscrita>, val anchos: Map<Int, Double> = emptyMap(), val combinadas: List<String> = emptyList())

/** Escritor mínimo de .xlsx compatible con Excel, LibreOffice y Google Sheets. */
object XlsxEscritor {
    fun escribir(out: OutputStream, hojas: List<HojaEscrita>) {
        require(hojas.isNotEmpty())
        ZipOutputStream(out.buffered()).use { z ->
            fun put(n: String, s: String) { z.putNextEntry(ZipEntry(n)); z.write(s.toByteArray(Charsets.UTF_8)); z.closeEntry() }
            put("[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""" +
                hojas.indices.joinToString("") { """<Override PartName="/xl/worksheets/sheet${it + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""" } + "</Types>")
            put("_rels/.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
            put("xl/workbook.xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>""" +
                hojas.mapIndexed { i, h -> """<sheet name="${esc(h.nombre.take(31))}" sheetId="${i + 1}" r:id="rId${i + 1}"/>""" }.joinToString("") +
                """</sheets><calcPr calcId="191029" fullCalcOnLoad="1"/></workbook>""")
            put("xl/_rels/workbook.xml.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
                hojas.indices.joinToString("") { """<Relationship Id="rId${it + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet${it + 1}.xml"/>""" } +
                """<Relationship Id="rId${hojas.size + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>""")
            put("xl/styles.xml", ESTILOS)
            hojas.forEachIndexed { i, h -> put("xl/worksheets/sheet${i + 1}.xml", hoja(h)) }
        }
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
        .filter { it == '\t' || it == '\n' || it == '\r' || it.code >= 0x20 }

    fun columna(ref: String) = ref.takeWhile { it.isLetter() }.fold(0) { a, ch -> a * 26 + (ch.uppercaseChar() - 'A' + 1) }
    fun fila(ref: String) = ref.dropWhile { it.isLetter() }.toInt()

    private fun num(d: Double) = if (d == Math.floor(d) && Math.abs(d) < 1e15) d.toLong().toString() else d.toString()

    private fun hoja(h: HojaEscrita): String {
        val sb = StringBuilder("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""")
        if (h.anchos.isNotEmpty()) {
            sb.append("<cols>")
            h.anchos.toSortedMap().forEach { (c, w) -> sb.append("""<col min="$c" max="$c" width="$w" customWidth="1"/>""") }
            sb.append("</cols>")
        }
        sb.append("<sheetData>")
        h.celdas.groupBy { fila(it.ref) }.toSortedMap().forEach { (r, cs) ->
            sb.append("""<row r="$r">""")
            cs.sortedBy { columna(it.ref) }.forEach { c ->
                val s = c.estilo.id
                when (val v = c.valor) {
                    is Valor.Texto -> sb.append("""<c r="${c.ref}" s="$s" t="inlineStr"><is><t xml:space="preserve">${esc(v.s)}</t></is></c>""")
                    is Valor.Numero -> sb.append("""<c r="${c.ref}" s="$s"><v>${num(v.n)}</v></c>""")
                    is Valor.Formula -> sb.append("""<c r="${c.ref}" s="$s"><f>${esc(v.f)}</f>${v.cache?.takeIf { it.isFinite() }?.let { "<v>${num(it)}</v>" } ?: ""}</c>""")
                }
            }
            sb.append("</row>")
        }
        sb.append("</sheetData>")
        if (h.combinadas.isNotEmpty()) sb.append("""<mergeCells count="${h.combinadas.size}">""" + h.combinadas.joinToString("") { """<mergeCell ref="$it"/>""" } + "</mergeCells>")
        sb.append("""<pageMargins left="0.4" right="0.4" top="0.5" bottom="0.5" header="0.3" footer="0.3"/><pageSetup paperSize="9" orientation="portrait" fitToHeight="0"/></worksheet>""")
        return sb.toString()
    }

    private val ESTILOS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
<numFmts count="2"><numFmt numFmtId="164" formatCode="#,##0.00"/><numFmt numFmtId="165" formatCode="0.0000"/></numFmts>
<fonts count="3"><font><sz val="10"/><name val="Arial"/></font><font><b/><sz val="11"/><color rgb="FF0B294B"/><name val="Arial"/></font><font><b/><sz val="9"/><color rgb="FF435667"/><name val="Arial"/></font></fonts>
<fills count="3"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FFD1DCE5"/><bgColor indexed="64"/></patternFill></fill></fills>
<borders count="2"><border><left/><right/><top/><bottom/><diagonal/></border><border><left style="thin"><color rgb="FF6E7F8F"/></left><right style="thin"><color rgb="FF6E7F8F"/></right><top style="thin"><color rgb="FF6E7F8F"/></top><bottom style="thin"><color rgb="FF6E7F8F"/></bottom><diagonal/></border></borders>
<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
<cellXfs count="8">
<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
<xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"><alignment wrapText="1" horizontal="center" vertical="center"/></xf>
<xf numFmtId="0" fontId="2" fillId="0" borderId="1" xfId="0" applyFont="1" applyBorder="1"><alignment wrapText="1" vertical="center"/></xf>
<xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyBorder="1"><alignment horizontal="center"/></xf>
<xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyBorder="1"/>
<xf numFmtId="164" fontId="0" fillId="2" borderId="1" xfId="0" applyNumberFormat="1" applyFill="1" applyBorder="1"/>
<xf numFmtId="165" fontId="0" fillId="0" borderId="1" xfId="0" applyNumberFormat="1" applyBorder="1"/>
<xf numFmtId="165" fontId="0" fillId="2" borderId="1" xfId="0" applyNumberFormat="1" applyFill="1" applyBorder="1"/>
</cellXfs>
</styleSheet>"""
}
