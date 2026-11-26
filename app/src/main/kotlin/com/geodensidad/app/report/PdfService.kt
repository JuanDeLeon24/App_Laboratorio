package com.geodensidad.app.report

import android.content.Context
import android.graphics.BitmapFactory
import com.geodensidad.app.BuildConfig
import com.geodensidad.app.data.Densidad
import com.geodensidad.app.data.Registro
import com.geodensidad.app.data.calcular
import com.geodensidad.app.data.conDensidades
import com.geodensidad.app.data.densidades
import com.geodensidad.core.calc.ConoArenaLegacy
import com.geodensidad.core.number.DecimalText
import com.geodensidad.core.report.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/** Genera el PDF de un registro con la forma de la hoja 'Cono de arena'. */
class PdfService(private val context: Context, private val writerFactory: () -> PdfWriterTarget) {

    private fun asset(name: String): Triple<String, Int, Int>? = runCatching {
        val f = File(context.filesDir, "plantilla/$name")
        if (!f.exists()) { f.parentFile?.mkdirs(); context.assets.open("template/$name").use { i -> f.outputStream().use { i.copyTo(it) } } }
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        Triple(f.path, o.outWidth, o.outHeight)
    }.getOrNull()

    fun columnas(r: Registro): List<PlanillaBuilder.Columna> = r.densidades.mapIndexed { idx, d: Densidad ->
        PlanillaBuilder.Columna(idx + 1, d.capa, d.espesor, d.pr, d.costado,
            DecimalText.storedToDouble(d.a), DecimalText.storedToDouble(d.b), DecimalText.storedToDouble(d.d), DecimalText.storedToDouble(d.f),
            DecimalText.storedToDouble(d.h), DecimalText.storedToDouble(r.i), DecimalText.storedToDouble(d.j), DecimalText.storedToDouble(d.l), r.calcular(d))
    }

    /** @param conFirmas incluye las firmas manuscritas guardadas del registro (si existen). */
    suspend fun generar(r: Registro, conFirmas: Boolean = false): File = withContext(Dispatchers.Default) {
        val logo = asset("logo.png")
        val now = OffsetDateTime.now()
        val code = "REG-${r.registro.ifBlank { "SN" }}-${now.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))}"
        val cols = columnas(r)
        val q = DecimalText.storedToDouble(r.q); val rr = DecimalText.storedToDouble(r.r)
        val (resumen, notas) = PlanillaBuilder.resumen(cols, q)
        val data = PlanillaData(
            template = TemplateInfo("1.1.0", "Construcciones El Cóndor S.A.",
                "DENSIDAD O MASA UNITARIA DEL SUELO EN EL TERRENO MÉTODO DEL CONO DE ARENA I.N.V. E – 161", "", "", "",
                logo?.first, logo?.second ?: 0, logo?.third ?: 0),
            issue = IssueInfo(code, code, now.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")), null, r.proyecto.take(20), false,
                ConoArenaLegacy.ENGINE_FULL_ID, BuildConfig.VERSION_NAME),
            encabezado = listOf("Proyecto" to r.proyecto, "Localización" to r.localizacion, "Procedencia" to r.procedencia, "PR inicial" to r.prInicial,
                "PR final" to r.prFinal, "Lado" to r.lado, "Fecha" to r.fecha, "Parte del relleno" to r.parteRelleno, "Lote" to r.lote, "Registro" to r.registro),
            columnas = cols.map { it.numero.toString() },
            filas = PlanillaBuilder.filas(cols, q, rr),
            resumen = resumen, notas = notas, observaciones = r.observaciones, elaboro = r.elaboro, aprobo = r.aprobo,
            firmaElaboro = if (conFirmas) Firmas(context).paraPdf(r.id, Firmas.Rol.ELABORO) else null,
            firmaAprobo = if (conFirmas) Firmas(context).paraPdf(r.id, Firmas.Rol.APROBO) else null,
        )
        val dir = File(context.filesDir, "pdf").apply { mkdirs() }
        val out = File(dir, "$code-${System.currentTimeMillis() % 1000}.pdf")
        val w = writerFactory()
        try {
            val pages = PlanillaRenderer.render(data, w)
            pages.render(w)
            out.outputStream().use { w.writeTo(it) }
        } finally { w.close() }
        out
    }
}
