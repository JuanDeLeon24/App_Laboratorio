package com.geodensidad.app.evidence

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.room.withTransaction
import com.geodensidad.app.data.PhotoEntity
import com.geodensidad.app.domain.AppJson
import com.geodensidad.app.domain.Clock
import com.geodensidad.app.domain.DomainException
import com.geodensidad.app.domain.GeoRepository
import com.geodensidad.app.domain.newId
import com.geodensidad.core.backup.BackupArchive
import com.geodensidad.core.photo.CaptureTime
import com.geodensidad.core.photo.CaptureTimeSource
import com.geodensidad.core.photo.PhotoCaption
import com.geodensidad.core.photo.PhotoTimes
import java.io.File
import java.io.IOException

/**
 * Evidencias fotográficas. El ORIGINAL se copia sin modificar al almacenamiento interno de la app
 * (files/evidence/original) y nunca se altera. Rotaciones se aplican solo a derivados
 * (miniatura y versión optimizada para PDF).
 */
class EvidenceRepository(private val context: Context, private val repo: GeoRepository) {
    private val db = repo.db
    private val root get() = File(context.filesDir, "evidence")
    private fun dir(name: String) = File(root, name).apply { mkdirs() }

    /** Archivo temporal para la cámara; sobrevive a la muerte del proceso (está en cache/camera). */
    fun newCameraFile(): File = File(context.cacheDir, "camera").apply { mkdirs() }.let { File(it, "captura-${newId()}.jpg") }

    data class ImportResult(val imported: List<String>, val errors: List<String>)

    suspend fun importFromCamera(file: File, activityId: String, sampleId: String?, journeyId: String?, location: Pair<Double, Double>?): ImportResult {
        val captureAt = Clock.isoNow()
        return try {
            val id = importStream(file.inputStream(), activityId, sampleId, journeyId, fromCamera = true, appCaptureAt = captureAt, location = location)
            file.delete()
            ImportResult(listOf(id), emptyList())
        } catch (e: Exception) { ImportResult(emptyList(), listOf(e.message ?: "Error al importar la fotografía")) }
    }

    suspend fun importFromUris(uris: List<Uri>, activityId: String, sampleId: String?, journeyId: String?): ImportResult {
        val ok = mutableListOf<String>(); val errs = mutableListOf<String>()
        for ((i, u) in uris.withIndex()) {
            try {
                val input = context.contentResolver.openInputStream(u) ?: throw IOException("No se pudo abrir el archivo")
                ok += importStream(input, activityId, sampleId, journeyId, fromCamera = false, appCaptureAt = null, location = null)
            } catch (e: Exception) { errs += "Imagen ${i + 1}: ${e.message ?: "error"}" }
        }
        return ImportResult(ok, errs)
    }

    private fun checkSpace(bytesNeeded: Long) {
        if (context.filesDir.usableSpace < bytesNeeded * 3 + 20L * 1024 * 1024) throw DomainException("Almacenamiento insuficiente para guardar la fotografía")
    }

    private suspend fun importStream(input: java.io.InputStream, activityId: String, sampleId: String?, journeyId: String?, fromCamera: Boolean, appCaptureAt: String?, location: Pair<Double, Double>?): String {
        val id = newId()
        val tmp = File(dir("tmp"), "$id.part")
        try {
            input.use { inp -> tmp.outputStream().use { inp.copyTo(it) } }
            checkSpace(tmp.length())
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(tmp.path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw DomainException("El archivo no es una imagen válida o está dañado")
            if (bounds.outWidth.toLong() * bounds.outHeight > 120_000_000L) throw DomainException("Imagen demasiado grande (más de 120 megapíxeles)")
            val mime = bounds.outMimeType ?: "image/*"
            val ext = when (mime) { "image/png" -> "png"; "image/webp" -> "webp"; "image/heif", "image/heic" -> "heic"; else -> "jpg" }
            val sha = BackupArchive.sha256(tmp)
            val exif = runCatching { ExifInterface(tmp.path) }.getOrNull()
            val orientation = exif?.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) ?: 1
            val exifLocal = CaptureTime.parseExifDateTime(exif?.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL))?.toString()
            val exifOffset = CaptureTime.parseExifOffset(exif?.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL))?.id
            val original = File(dir("original"), "$id.$ext")
            if (!tmp.renameTo(original)) { tmp.copyTo(original); tmp.delete() }
            val (pdf, pw, ph) = derive(original, File(dir("pdf"), "$id.jpg"), 1800, orientation, 0, 85)
            derive(original, File(dir("thumb"), "$id.jpg"), 360, orientation, 0, 80)
            val (w, h) = if (orientation in listOf(5, 6, 7, 8)) bounds.outHeight to bounds.outWidth else bounds.outWidth to bounds.outHeight
            val times = PhotoTimes(appCaptureAt = appCaptureAt, exifLocal = exifLocal, exifOffset = exifOffset, importedAt = Clock.isoNow(),
                selected = CaptureTime.defaultSource(fromCamera, exifLocal))
            val now = Clock.nowMillis()
            db.withTransaction {
                val order = (sampleId?.let { db.photos().maxOrder(it) } ?: 0) + 1
                db.photos().insert(PhotoEntity(id, activityId, sampleId, journeyId, if (fromCamera) "CAMERA" else "IMPORT", original.path, File(dir("thumb"), "$id.jpg").path,
                    pdf.path, pw, ph, sha, mime, w, h, original.length(), orientation, 0, AppJson.encodeToString(PhotoTimes.serializer(), times), "{}",
                    true, order, location?.first, location?.second, false, now, now))
                repo.audit("PHOTO", id, "Incorporación", "${if (fromCamera) "Cámara" else "Galería"}; SHA-256 $sha; fecha mostrada: ${times.selected.label}")
                sampleId?.let { repo.audit("SAMPLE", it, "Fotografía añadida", id) }
            }
            return id
        } finally { tmp.delete() }
    }

    /** Genera un derivado JPEG con orientación corregida y rotación de usuario, sin deformar. */
    fun derive(original: File, out: File, maxSide: Int, exifOrientation: Int, userRotation: Int, quality: Int): Triple<File, Int, Int> {
        val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(original.path, b)
        var sample = 1
        while (maxOf(b.outWidth, b.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val bmp = BitmapFactory.decodeFile(original.path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: throw DomainException("No se pudo decodificar la imagen (archivo dañado o formato no soportado)")
        val m = Matrix()
        when (exifOrientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
        }
        if (userRotation % 360 != 0) m.postRotate(userRotation.toFloat())
        val scale = minOf(1f, maxSide.toFloat() / maxOf(bmp.width, bmp.height))
        m.postScale(scale, scale)
        val outBmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        out.parentFile?.mkdirs()
        out.outputStream().use { outBmp.compress(Bitmap.CompressFormat.JPEG, quality, it) }
        val res = Triple(out, outBmp.width, outBmp.height)
        if (outBmp !== bmp) outBmp.recycle()
        bmp.recycle()
        return res
    }

    suspend fun rotate(photoId: String, delta: Int) {
        val p = db.photos().get(photoId) ?: return
        val rot = ((p.rotation + delta) % 360 + 360) % 360
        val orig = File(p.originalPath)
        val (_, pw, ph) = derive(orig, File(p.pdfPath), 1800, p.exifOrientation, rot, 85)
        derive(orig, File(p.thumbPath), 360, p.exifOrientation, rot, 80)
        db.photos().update(p.copy(rotation = rot, pdfW = pw, pdfH = ph, updatedAt = Clock.nowMillis()))
        repo.audit("PHOTO", photoId, "Rotación de derivados", "$rot° (el original no se modifica)")
    }

    suspend fun updateCaption(photoId: String, fields: PhotoCaption.Fields, include: Boolean) {
        val p = db.photos().get(photoId) ?: return
        db.photos().update(p.copy(captionJson = AppJson.encodeToString(PhotoCaption.Fields.serializer(), fields), includeInReport = include, updatedAt = Clock.nowMillis()))
        if (p.includeInReport != include) repo.audit("PHOTO", photoId, if (include) "Incluida en informes" else "Retirada de informes", "El original se conserva")
    }

    /** Corrección manual de la fecha: conserva todos los valores originales y registra el motivo. */
    suspend fun setManualTime(photoId: String, localIso: String?, offset: String?, reason: String, source: CaptureTimeSource) {
        val p = db.photos().get(photoId) ?: return
        val t = AppJson.decodeFromString(PhotoTimes.serializer(), p.timesJson)
        if (source == CaptureTimeSource.MANUAL && (localIso == null || reason.isBlank())) throw DomainException("Indique fecha y motivo de la corrección manual")
        if (source == CaptureTimeSource.EXIF && t.exifLocal == null) throw DomainException("La fotografía no tiene fecha EXIF interpretable")
        if (source == CaptureTimeSource.APP_DEVICE && t.appCaptureAt == null) throw DomainException("La fotografía no fue tomada desde la aplicación")
        val n = if (source == CaptureTimeSource.MANUAL) t.copy(manualLocal = localIso, manualOffset = offset, manualReason = reason, selected = source) else t.copy(selected = source)
        db.photos().update(p.copy(timesJson = AppJson.encodeToString(PhotoTimes.serializer(), n), updatedAt = Clock.nowMillis()))
        repo.audit("PHOTO", photoId, "Cambio de fecha mostrada", "${t.selected.label} → ${n.selected.label}${if (source == CaptureTimeSource.MANUAL) ": $localIso ${offset ?: "(sin zona)"}; motivo: $reason" else ""}")
    }

    suspend fun move(photoId: String, up: Boolean) {
        val p = db.photos().get(photoId) ?: return
        val list = p.sampleId?.let { db.photos().listBySample(it) } ?: return
        val idx = list.indexOfFirst { it.id == photoId }
        val other = list.getOrNull(if (up) idx - 1 else idx + 1) ?: return
        db.withTransaction {
            db.photos().update(p.copy(sortOrder = other.sortOrder))
            db.photos().update(other.copy(sortOrder = p.sortOrder))
        }
        // si comparten orden, se normaliza
        val fixed = db.photos().listBySample(p.sampleId)
        if (fixed.map { it.sortOrder }.distinct().size != fixed.size) db.withTransaction { fixed.forEachIndexed { i, x -> db.photos().update(x.copy(sortOrder = i + 1)) } }
    }

    /** Archiva (no borra) la fotografía: deja de mostrarse pero el original se conserva en respaldos. */
    suspend fun archive(photoId: String, reason: String) {
        val p = db.photos().get(photoId) ?: return
        db.photos().update(p.copy(archived = true, includeInReport = false, updatedAt = Clock.nowMillis()))
        repo.audit("PHOTO", photoId, "Archivada", reason)
    }

    /** Verifica la integridad del original frente al hash registrado. */
    fun verify(p: PhotoEntity): Boolean = File(p.originalPath).let { it.exists() && BackupArchive.sha256(it) == p.sha256 }
}
