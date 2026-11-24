package com.geodensidad.app.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.room.withTransaction
import com.geodensidad.app.BuildConfig
import com.geodensidad.app.data.AppDatabase
import com.geodensidad.app.domain.Clock
import com.geodensidad.app.domain.DomainException
import com.geodensidad.core.backup.BackupArchive
import com.geodensidad.core.backup.BackupManifest
import com.geodensidad.core.calc.ConoArenaLegacy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Respaldo completo y restauración controlada.
 * El respaldo NO está cifrado: cualquiera con el archivo puede leer los datos y fotografías.
 */
class BackupService(private val context: Context, private val dbProvider: () -> AppDatabase, private val closeDb: () -> Unit) {

    /** Directorios de datos incluidos (relativos a filesDir). */
    private val dataDirs = listOf("evidence/original", "evidence/thumb", "evidence/pdf", "reports", "template", "labdocs", "datastore")

    data class RestorePlan(val staging: File, val manifest: BackupManifest, val summary: List<Pair<String, String>>, val warnings: List<String>)

    private fun stamp() = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())

    suspend fun exportTo(out: OutputStream, onProgress: (String) -> Unit = {}): BackupManifest = withContext(Dispatchers.IO) {
        val db = dbProvider()
        onProgress("Consolidando base de datos")
        runCatching { db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() } }
        val work = File(context.cacheDir, "backup-work-${stamp()}").apply { mkdirs() }
        try {
            val dbFile = context.getDatabasePath(AppDatabase.NAME)
            val counts = HashMap<String, Int>()
            // Copia consistente: con el bloqueo de escritura tomado se copian juntos base y WAL.
            db.withTransaction {
                dbFile.copyTo(File(work, AppDatabase.NAME), overwrite = true)
                File(dbFile.path + "-wal").takeIf { it.exists() && it.length() > 0 }?.copyTo(File(work, AppDatabase.NAME + "-wal"), overwrite = true)
                counts["proyectos"] = db.projects().count(); counts["muestras"] = db.samples().count()
                counts["fotografias"] = db.photos().count(); counts["informes"] = db.reports().count()
            }
            val entries = sortedMapOf<String, File>()
            File(work, AppDatabase.NAME).let { entries["db/${it.name}"] = it }
            File(work, AppDatabase.NAME + "-wal").takeIf { it.exists() }?.let { entries["db/${it.name}"] = it }
            onProgress("Reuniendo archivos")
            for (d in dataDirs) {
                val base = File(context.filesDir, d)
                if (!base.exists()) continue
                base.walkTopDown().filter { it.isFile && !it.name.endsWith(".part") }.forEach { f ->
                    entries["files/" + f.relativeTo(context.filesDir).invariantSeparatorsPath] = f
                }
            }
            onProgress("Escribiendo respaldo (${entries.size} archivos)")
            BackupArchive.write(out, entries) { files ->
                BackupManifest(BackupArchive.FORMAT, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, AppDatabase.VERSION, ConoArenaLegacy.ENGINE_FULL_ID,
                    BuildConfig.TEMPLATE_BASE_VERSION, Clock.isoNow(), encrypted = false, counts = counts, sourceFilesDir = context.filesDir.absolutePath, files = files)
            }
        } finally { work.deleteRecursively() }
    }

    suspend fun exportToUri(uri: Uri, onProgress: (String) -> Unit): BackupManifest = withContext(Dispatchers.IO) {
        if (context.filesDir.usableSpace < 50L * 1024 * 1024) throw DomainException("Almacenamiento insuficiente para preparar el respaldo")
        context.contentResolver.openOutputStream(uri, "w")?.use { exportTo(it, onProgress) } ?: throw DomainException("No se pudo abrir el destino")
    }

    /** Respaldo automático local (antes de restaurar). Se guarda en files/backups/auto. */
    suspend fun exportAuto(prefix: String): File = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "backups/auto").apply { mkdirs() }
        val f = File(dir, "$prefix-${stamp()}.geodensidad.zip")
        f.outputStream().use { exportTo(it) }
        f
    }

    /** Fases 1–7: copiar, leer manifiesto, verificar integridad, validar base y referencias en área temporal. */
    suspend fun prepareRestore(uri: Uri): RestorePlan = withContext(Dispatchers.IO) {
        val root = File(context.cacheDir, "restore").apply { deleteRecursively(); mkdirs() }
        val staging = File(root, "staging").apply { mkdirs() }
        val manifest = context.contentResolver.openInputStream(uri)?.use { BackupArchive.extractAndVerify(it, staging) } ?: throw DomainException("No se pudo abrir el archivo")
        validateStaging(staging, manifest)
    }

    fun validateStaging(staging: File, manifest: BackupManifest): RestorePlan {
        if (manifest.dbSchemaVersion > AppDatabase.VERSION) throw DomainException("El respaldo usa base de datos v${manifest.dbSchemaVersion}; esta versión de la app admite hasta v${AppDatabase.VERSION}. Actualice la aplicación.")
        val dbFile = File(staging, "db/${AppDatabase.NAME}")
        if (!dbFile.exists()) throw DomainException("El respaldo no contiene la base de datos")
        val warnings = mutableListOf<String>()
        val summary = mutableListOf<Pair<String, String>>()
        val oldDir = manifest.sourceFilesDir
        val newDir = context.filesDir.absolutePath
        SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READWRITE).use { sdb ->
            sdb.rawQuery("PRAGMA integrity_check", null).use { c -> c.moveToFirst(); if (c.getString(0) != "ok") throw DomainException("La base de datos del respaldo está dañada: ${c.getString(0)}") }
            if (sdb.version > AppDatabase.VERSION) throw DomainException("Versión de base de datos no soportada: ${sdb.version}")
            if (sdb.version < 1) throw DomainException("La base de datos del respaldo no tiene versión válida")
            sdb.rawQuery("PRAGMA foreign_key_check", null).use { c -> if (c.count > 0) warnings += "Se detectaron ${c.count} referencias inconsistentes en la base del respaldo." }
            fun count(t: String) = sdb.rawQuery("SELECT COUNT(*) FROM $t", null).use { it.moveToFirst(); it.getInt(0) }
            summary += "Creado" to manifest.createdAt
            summary += "Versión de la app que lo creó" to "${manifest.appVersionName} (${manifest.appVersionCode})"
            summary += "Base de datos" to "v${sdb.version}${if (sdb.version < AppDatabase.VERSION) " (se migrará a v${AppDatabase.VERSION})" else ""}"
            summary += "Proyectos" to count("projects").toString(); summary += "Actividades" to count("activities").toString()
            summary += "Muestras" to count("samples").toString(); summary += "Revisiones" to count("sample_revisions").toString()
            summary += "Fotografías" to count("photos").toString(); summary += "Informes emitidos" to count("reports").toString()
            summary += "Archivos" to manifest.files.size.toString()
            summary += "Cifrado" to "NO (el archivo es legible por quien lo tenga)"
            // Reubicación de rutas absolutas al directorio de datos de esta instalación
            if (oldDir != null && oldDir != newDir) {
                val cols = listOf("photos" to listOf("original_path", "thumb_path", "pdf_path"), "reports" to listOf("file_path", "snapshot_path"),
                    "template_versions" to listOf("logo_path", "formula_path"), "lab_references" to listOf("document_path"))
                sdb.beginTransaction()
                try {
                    for ((t, cs) in cols) for (c in cs) sdb.execSQL("UPDATE $t SET $c = ? || substr($c, ?) WHERE $c LIKE ?", arrayOf(newDir, oldDir.length + 1, "$oldDir%"))
                    sdb.setTransactionSuccessful()
                } finally { sdb.endTransaction() }
            }
            // Verificación de referencias a archivos
            val missing = mutableListOf<String>()
            fun checkPaths(sql: String) = sdb.rawQuery(sql, null).use { c ->
                while (c.moveToNext()) for (i in 0 until c.columnCount) c.getString(i)?.let { p ->
                    val rel = p.removePrefix("$newDir/")
                    if (rel != p && !File(staging, "files/$rel").exists()) missing += rel
                }
            }
            checkPaths("SELECT original_path, pdf_path FROM photos WHERE archived = 0")
            checkPaths("SELECT file_path FROM reports")
            if (missing.isNotEmpty()) warnings += "${missing.size} archivo(s) referenciados no están en el respaldo (p. ej. ${missing.first()})."
        }
        return RestorePlan(staging, manifest, summary, warnings)
    }

    /**
     * Fases 9–10: respaldo previo automático y sustitución controlada del conjunto local.
     * Si falla la sustitución, se intenta devolver los datos anteriores.
     */
    suspend fun applyRestore(plan: RestorePlan): File = withContext(Dispatchers.IO) {
        val pre = exportAuto("pre-restauracion")
        closeDb()
        val dbFile = context.getDatabasePath(AppDatabase.NAME)
        val ts = stamp()
        val old = File(context.filesDir, "restore-old-$ts").apply { mkdirs() }
        val moved = mutableListOf<Pair<File, File>>()
        try {
            for (suffix in listOf("", "-wal", "-shm", "-journal")) {
                val f = File(dbFile.path + suffix)
                if (f.exists()) { val dst = File(old, "db" + suffix); if (!f.renameTo(dst)) throw DomainException("No se pudo apartar ${f.name}"); moved += f to dst }
            }
            for (d in dataDirs) {
                val f = File(context.filesDir, d)
                if (f.exists()) { val dst = File(old, d.replace('/', '_')); if (!f.renameTo(dst)) throw DomainException("No se pudo apartar $d"); moved += f to dst }
            }
            File(plan.staging, "db/${AppDatabase.NAME}").copyTo(dbFile, overwrite = true)
            File(plan.staging, "db/${AppDatabase.NAME}-wal").takeIf { it.exists() }?.copyTo(File(dbFile.path + "-wal"), overwrite = true)
            File(plan.staging, "files").takeIf { it.exists() }?.copyRecursively(context.filesDir, overwrite = true)
            old.deleteRecursively()
            plan.staging.parentFile?.deleteRecursively()
            pre
        } catch (e: Exception) {
            // Reversión: se eliminan los datos parcialmente copiados y se devuelven los anteriores
            for (suffix in listOf("", "-wal", "-shm")) File(dbFile.path + suffix).delete()
            for (d in dataDirs) File(context.filesDir, d).deleteRecursively()
            moved.forEach { (orig, tmp) -> tmp.renameTo(orig) }
            throw DomainException("La restauración falló y se devolvieron los datos anteriores: ${e.message}. Respaldo previo: ${pre.name}")
        }
    }
}
