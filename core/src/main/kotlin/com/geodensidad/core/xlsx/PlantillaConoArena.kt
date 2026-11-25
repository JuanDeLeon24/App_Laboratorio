package com.geodensidad.core.xlsx

import com.geodensidad.core.calc.ConoArenaLegacy
import com.geodensidad.core.calc.LegacyInputs
import com.geodensidad.core.number.DecimalText
import java.math.BigDecimal
import java.time.LocalDate

/** Una columna de densidad tal como aparece en la plantilla (números como texto decimal canónico). */
data class ColumnaExcel(
    val capa: String = "", val espesor: String = "", val pr: String = "", val costado: String = "",
    val a: String? = null, val b: String? = null, val d: String? = null, val f: String? = null,
    val h: String? = null, val j: String? = null, val l: String? = null,
)

/** Contenido de una hoja 'Cono de arena'. */
data class HojaDensidades(
    val proyecto: String = "", val localizacion: String = "", val procedencia: String = "", val prInicial: String = "",
    val prFinal: String = "", val lado: String = "", val fecha: String = "", val parteRelleno: String = "", val lote: String = "",
    val registro: String = "", val i: String? = null, val q: String? = null, val r: String? = null,
    val columnas: List<ColumnaExcel> = emptyList(), val observaciones: String = "", val elaboro: String = "", val aprobo: String = "",
    /** Identificador de la app (fila 56) para reconocer un registro ya importado. */
    val idApp: String? = null,
)

/**
 * Correspondencia con las celdas del formato Excel 'DENSIDAD O MASA UNITARIA DEL SUELO EN EL TERRENO
 * MÉTODO DEL CONO DE ARENA I.N.V. E – 161' (hoja 'Cono de arena'). Columnas C a L = densidades 1 a 10.
 */
object PlantillaConoArena {
    const val VERSION_FORMATO = 1
    val COLUMNAS = listOf("C", "D", "E", "F", "G", "H", "I", "J", "K", "L")

    private fun norm(s: String?) = s.orEmpty().uppercase().replace(Regex("\\s+"), " ").trim()

    fun esPlantilla(h: Hoja): Boolean =
        norm(h["B15"]?.texto).startsWith("PESO FRASCO Y ARENA INICIAL") && norm(h["B24"]?.texto).startsWith("PESO MATERIAL EXTRAIDO")

    /** Número guardado por Excel → texto canónico ("6.0999999999999999E-2" → "0.061"). */
    private fun canon(c: Celda?): String? {
        c ?: return null
        c.numero?.let { return DecimalText.canonical(BigDecimal(it.toString())) }
        return c.texto?.let { t -> DecimalText.toCanonicalOrNull(t) }
    }

    /** Texto del encabezado; un 0 numérico viene de un BUSCARV sobre celda vacía y se toma como vacío. */
    private fun txt(c: Celda?): String = when {
        c == null -> ""
        c.texto != null -> c.texto.trim()
        c.numero == 0.0 -> ""
        else -> c.comoTexto
    }

    private fun fecha(c: Celda?): String {
        val n = c?.numero
        return if (n != null && n > 20000 && n < 80000) LocalDate.of(1899, 12, 30).plusDays(n.toLong()).toString() else txt(c)
    }

    fun leer(h: Hoja): HojaDensidades {
        val cols = COLUMNAS.mapNotNull { k ->
            val col = ColumnaExcel(
                capa = txt(h["${k}11"]), espesor = txt(h["${k}12"]), pr = txt(h["${k}13"]), costado = txt(h["${k}14"]),
                a = canon(h["${k}15"]), b = canon(h["${k}16"]), d = canon(h["${k}18"]), f = canon(h["${k}20"]),
                h = canon(h["${k}22"]), j = canon(h["${k}24"]), l = canon(h["${k}26"]),
            )
            col.takeIf { it.a != null || it.b != null || it.h != null || it.j != null || it.l != null }
        }
        return HojaDensidades(
            proyecto = txt(h["B5"]), localizacion = txt(h["E5"]), procedencia = txt(h["B6"]), prInicial = txt(h["E6"]),
            prFinal = txt(h["I6"]), lado = txt(h["L6"]), fecha = fecha(h["B7"]), parteRelleno = txt(h["E7"]), lote = txt(h["I7"]),
            registro = txt(h["L7"]), i = canon(h["C23"]), q = canon(h["C31"]), r = canon(h["C32"]), columnas = cols,
            observaciones = txt(h["A50"]), elaboro = txt(h["C53"]), aprobo = txt(h["H53"]),
            idApp = if (txt(h["A56"]) == "GeoDensidad") txt(h["B56"]).ifBlank { null } else null,
        )
    }

    private fun d(s: String?) = DecimalText.storedToDouble(s)

    fun escribir(hd: HojaDensidades, nombreHoja: String): HojaEscrita {
        val c = mutableListOf<CeldaEscrita>()
        fun t(ref: String, s: String, e: Estilo = Estilo.NORMAL) { if (s.isNotEmpty()) c += CeldaEscrita(ref, Valor.Texto(s), e) }
        fun n(ref: String, v: String?, e: Estilo = Estilo.DATO_NUM) { d(v)?.let { c += CeldaEscrita(ref, Valor.Numero(it), e) } ?: run { c += CeldaEscrita(ref, Valor.Texto(""), e) } }
        fun f(ref: String, formula: String, cache: Double?, e: Estilo = Estilo.CALCULADO) { c += CeldaEscrita(ref, Valor.Formula(formula, cache), e) }

        t("C1", "DENSIDAD O MASA UNITARIA DEL SUELO EN EL TERRENO MÉTODO DEL CONO DE ARENA I.N.V. E – 161", Estilo.TITULO)
        listOf("CODIGO", "VERSIÓN", "FECHA", "PAGINA").forEachIndexed { i, s -> t("J${i + 1}", s, Estilo.ETIQUETA) }
        t("A5", "PROYECTO:", Estilo.ETIQUETA); t("B5", hd.proyecto); t("C5", "LOCALIZACIÓN :", Estilo.ETIQUETA); t("E5", hd.localizacion)
        t("A6", "PROCEDENCIA :", Estilo.ETIQUETA); t("B6", hd.procedencia); t("C6", "PR INICIAL:", Estilo.ETIQUETA); t("E6", hd.prInicial)
        t("H6", "PR FINAL:", Estilo.ETIQUETA); t("I6", hd.prFinal); t("K6", "LADO", Estilo.ETIQUETA); t("L6", hd.lado)
        t("A7", "FECHA:", Estilo.ETIQUETA); t("B7", hd.fecha); t("C7", "PARTE DEL RELLENO:", Estilo.ETIQUETA); t("E7", hd.parteRelleno)
        t("H7", "LOTE:", Estilo.ETIQUETA); t("I7", hd.lote); t("K7", "REGISTRO :", Estilo.ETIQUETA)
        hd.registro.toDoubleOrNull()?.let { c += CeldaEscrita("L7", Valor.Numero(it)) } ?: t("L7", hd.registro)

        val filas = listOf(
            Triple(10, "DENSIDAD No.", ""), Triple(11, "CAPA No.", ""), Triple(12, "ESPESOR CAPA", ""), Triple(13, "PR", ""), Triple(14, "COSTADO", ""),
            Triple(15, "A", "PESO FRASCO Y ARENA INICIAL (g)"), Triple(16, "B", "PESO FRASCO Y ARENA RESTANTE (g)"),
            Triple(17, "C", "PESO ARENA TOTAL USADA (g) --- Formula= A-B"), Triple(18, "D", "CONSTANTE DEL CONO (g)"),
            Triple(19, "E", "PESO ARENA EN EL HUECO (g) --- Formula= C-D"), Triple(20, "F", "DENSIDAD DE LA ARENA (g/cm3)"),
            Triple(21, "G", "VOLUMEN DEL HUECO (cm3) --- Formula= E/F"), Triple(22, "H", "MATERIAL RETENIDO EN 3/4 (g)"),
            Triple(23, "I", "GRAVEDAD  ESPECIFICA BULK (g/cm3)"), Triple(24, "J", "PESO MATERIAL EXTRAIDO HUMEDO  (g)"),
            Triple(25, "K", "% RETENIDO 3/4 --- Formula= H/J*100"), Triple(26, "L", "HUMEDAD (Decimal) --- Formula= Resultado en (%) / 100"),
            Triple(27, "M", "DENSIDAD HUMEDA DE CAMPO (Kg/m3) --- Formula= J/G*1000"),
            Triple(28, "N", "CONTENIDO DE HUMEDAD PART. FINA (Decimal) --- Formula= (((100*(L))-(0,02*K))/(100-K))"),
            Triple(29, "O", "DENSIDAD SECA DE CAMPO DE LA MUESTRA TOTAL  (Kg/mᶟ) --- Formula= (1+L) LUEGO M/L "),
            Triple(30, "P", "DENSIDAD SECA CORREGIDA DE LAS PART. FINAS (Di) (kg/mᶟ) --- Formula= ((O*(100-K))/(100-((O*K)/(I*1000)))"),
            Triple(31, "Q", "DENSIDAD MAXIMA DE LABORATORIO  (De) (kg/m3) "), Triple(32, "R", "HUMEDAD OPTIMA (%)"),
            Triple(33, "S", "COMPACTACION DEL TERRENO (%) --- Formula= P/Q*100"),
        )
        filas.forEach { (fila, a, b) -> if (fila < 15) t("A$fila", a, Estilo.ETIQUETA) else { t("A$fila", a, Estilo.ETIQUETA); t("B$fila", b, Estilo.ETIQUETA) } }

        hd.columnas.take(10).forEachIndexed { idx, col ->
            val k = COLUMNAS[idx]
            val res = ConoArenaLegacy.calculate(LegacyInputs(d(col.a), d(col.b), d(col.d), d(col.f), d(col.h), d(hd.i), d(col.j), d(col.l), d(hd.q), d(hd.r)))
            fun v(s: String) = res.value(s)
            c += CeldaEscrita("${k}10", Valor.Numero(idx + 1.0), Estilo.DATO)
            t("${k}11", col.capa, Estilo.DATO); t("${k}12", col.espesor, Estilo.DATO); t("${k}13", col.pr, Estilo.DATO); t("${k}14", col.costado, Estilo.DATO)
            n("${k}15", col.a); n("${k}16", col.b); f("${k}17", "${k}15-${k}16", v("C"))
            n("${k}18", col.d); f("${k}19", "${k}17-${k}18", v("E"))
            n("${k}20", col.f); f("${k}21", "${k}19/${k}20", v("G"))
            n("${k}22", col.h)
            if (idx == 0) n("C23", hd.i) else f("${k}23", "+${COLUMNAS[idx - 1]}23", d(hd.i), Estilo.DATO_NUM)
            n("${k}24", col.j); f("${k}25", "${k}22/${k}24*100", v("K"))
            n("${k}26", col.l, Estilo.DATO_DEC); f("${k}27", "${k}24/${k}21*1000", v("M"))
            f("${k}28", "(((100*(${k}26))-(0.02*${k}25))/(100-${k}25))", v("N"), Estilo.CALCULADO_DEC)
            f("${k}29", "(${k}27/(1+(${k}26)))", v("O"))
            f("${k}30", "((${k}29*(100-${k}25))/(100-((${k}29*${k}25)/(${k}23*1000))))", v("P"))
            f("${k}33", "${k}30/\$C\$31*100", v("S"))
        }
        n("C31", hd.q); n("C32", hd.r, Estilo.DATO_DEC)

        // Evaluación del formato (con referencias corregidas: mismas n densidades para media, desviación y k)
        val ultima = COLUMNAS[(hd.columnas.size.coerceIn(1, 10)) - 1]
        t("B38", "ESPECIFICACIÓN: ARTÍCULO 220.5.2.2", Estilo.ETIQUETA); t("H38", "Dm - (K*S)", Estilo.ETIQUETA)
        t("D41", "Dm=", Estilo.ETIQUETA); f("E41", "AVERAGE(C30:${ultima}30)", null)
        t("J41", "S=", Estilo.ETIQUETA); f("K41", "STDEV(C30:${ultima}30)", null)
        f("H39", "E41-(INDEX(D44:I44,MATCH(COUNT(C30:${ultima}30),D43:I43,0))*K41)", null)
        t("G40", "0,95 De=", Estilo.ETIQUETA); f("H40", "0.95*C31", null)
        t("C43", "n", Estilo.ETIQUETA); t("C44", "k", Estilo.ETIQUETA)
        listOf(5 to 0.685, 6 to 0.602, 7 to 0.544, 8 to 0.5, 9 to 0.465, 10 to 0.437).forEachIndexed { i, (nn, kk) ->
            val col = listOf("D", "E", "F", "G", "H", "I")[i]
            c += CeldaEscrita("${col}43", Valor.Numero(nn.toDouble()), Estilo.DATO); c += CeldaEscrita("${col}44", Valor.Numero(kk), Estilo.DATO)
        }
        t("A49", "OBSERVACIONES:", Estilo.ETIQUETA); t("A50", hd.observaciones)
        t("A52", "ELABORÓ:", Estilo.ETIQUETA); t("B52", "FIRMA", Estilo.ETIQUETA); t("E52", "APROBÓ:", Estilo.ETIQUETA); t("G52", "FIRMA", Estilo.ETIQUETA)
        t("B53", "NOMBRE", Estilo.ETIQUETA); t("C53", hd.elaboro); t("G53", "NOMBRE", Estilo.ETIQUETA); t("H53", hd.aprobo)
        hd.idApp?.let { t("A56", "GeoDensidad"); t("B56", it); t("C56", "Formato de intercambio v$VERSION_FORMATO (no borrar esta fila)") }
        return HojaEscrita(nombreHoja, c, anchos = mapOf(1 to 5.0, 2 to 46.0) + (3..12).associateWith { 11.0 }, combinadas = listOf("C1:I4", "A50:L51"))
    }
}
