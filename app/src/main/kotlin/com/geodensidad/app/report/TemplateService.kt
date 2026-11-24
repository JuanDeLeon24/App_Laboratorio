package com.geodensidad.app.report

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.room.withTransaction
import com.geodensidad.app.BuildConfig
import com.geodensidad.app.data.TemplateVersionEntity
import com.geodensidad.app.domain.Clock
import com.geodensidad.app.domain.DomainException
import com.geodensidad.app.domain.GeoRepository
import com.geodensidad.app.domain.newId
import com.geodensidad.core.report.TemplateInfo
import java.io.File

/**
 * Plantilla documental versionada. Cada versión guarda copias inmutables de sus imágenes en
 * files/template/<versión>/; cambiar el logotipo crea una versión nueva y no altera informes emitidos.
 */
class TemplateService(private val context: Context, private val repo: GeoRepository) {
    private val db = repo.db

    /** Crea la plantilla 1.0.0 con las dos imágenes extraídas del Excel de origen (assets/template). */
    suspend fun ensureSeed() {
        if (db.templates().count() > 0) return
        val v = BuildConfig.TEMPLATE_BASE_VERSION
        val dir = File(context.filesDir, "template/$v").apply { mkdirs() }
        fun copyAsset(name: String): File? = runCatching {
            val f = File(dir, name); context.assets.open("template/$name").use { i -> f.outputStream().use { i.copyTo(it) } }; f
        }.getOrNull()
        val logo = copyAsset("logo.png"); val formula = copyAsset("formula_s.png")
        val (lw, lh) = logo?.let { dims(it) } ?: (0 to 0)
        val (fw, fh) = formula?.let { dims(it) } ?: (0 to 0)
        db.templates().insert(TemplateVersionEntity(newId(), v, "Construcciones El Cóndor S.A.",
            "DENSIDAD O MASA UNITARIA DEL SUELO EN EL TERRENO – MÉTODO DEL CONO DE ARENA (I.N.V. E – 161)",
            "(por definir)", "(por definir)", "(por definir)", logo?.path, lw, lh, formula?.path, fw, fh, true, Clock.nowMillis()))
    }

    private fun dims(f: File): Pair<Int, Int> {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o); return o.outWidth to o.outHeight
    }

    fun toInfo(t: TemplateVersionEntity) = TemplateInfo(t.version, t.institution, t.formTitle, t.docCode, t.docVersion, t.docDate,
        t.logoPath?.takeIf { File(it).exists() }, t.logoW, t.logoH, t.formulaPath?.takeIf { File(it).exists() }, t.formulaW, t.formulaH)

    /** Publica una nueva versión de plantilla. [newLogo] null = conservar el actual; removeLogo = encabezado tipográfico. */
    suspend fun publish(institution: String, formTitle: String, docCode: String, docVersion: String, docDate: String, newLogo: Uri?, removeLogo: Boolean): String {
        val cur = db.templates().active() ?: throw DomainException("No hay plantilla activa")
        val parts = cur.version.split('.').mapNotNull { it.toIntOrNull() }
        val next = "${parts.getOrElse(0) { 1 }}.${parts.getOrElse(1) { 0 } + 1}.0"
        val dir = File(context.filesDir, "template/$next").apply { mkdirs() }
        var logoPath: String? = null; var lw = 0; var lh = 0
        if (newLogo != null) {
            val f = File(dir, "logo")
            context.contentResolver.openInputStream(newLogo)?.use { i -> f.outputStream().use { i.copyTo(it) } } ?: throw DomainException("No se pudo leer la imagen")
            val (w, h) = dims(f); if (w <= 0) { f.delete(); throw DomainException("El archivo no es una imagen válida") }
            logoPath = f.path; lw = w; lh = h
        } else if (!removeLogo && cur.logoPath != null && File(cur.logoPath).exists()) {
            val f = File(dir, File(cur.logoPath).name); File(cur.logoPath).copyTo(f, true); logoPath = f.path; lw = cur.logoW; lh = cur.logoH
        }
        val formula = cur.formulaPath?.let { p -> File(p).takeIf { it.exists() }?.let { val f = File(dir, it.name); it.copyTo(f, true); f.path } }
        val id = newId()
        db.withTransaction {
            db.templates().deactivateAll()
            db.templates().insert(TemplateVersionEntity(id, next, institution, formTitle, docCode, docVersion, docDate, logoPath, lw, lh, formula, cur.formulaW, cur.formulaH, true, Clock.nowMillis()))
            repo.audit("TEMPLATE", id, "Nueva versión de plantilla", "${cur.version} → $next")
        }
        return next
    }
}
