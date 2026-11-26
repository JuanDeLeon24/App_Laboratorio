package com.geodensidad.app.report

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.room.withTransaction
import com.geodensidad.app.BuildConfig
import com.geodensidad.app.data.AppDatabase
import com.geodensidad.core.backup.BackupArchive
import com.geodensidad.core.backup.BackupException
import com.geodensidad.core.backup.BackupManifest
import com.geodensidad.core.calc.ConoArenaLegacy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.time.OffsetDateTime

/** Respaldo (ZIP con la base de datos y manifiesto con SHA-256) y restauración completa. No cifrado. */
class Respaldo(private val context: Context, private val db: () -> AppDatabase, private val closeDb: () -> Unit) {

    suspend fun exportar(out: OutputStream): Int = withContext(Dispatchers.IO) {
        val d = db()
        runCatching { d.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() } }
        val work = File(context.cacheDir, "respaldo").apply { deleteRecursively(); mkdirs() }
        val dbFile = context.getDatabasePath(AppDatabase.NAME)
        var n = 0
        d.withTransaction {
            dbFile.copyTo(File(work, AppDatabase.NAME), true)
            File(dbFile.path + "-wal").takeIf { it.exists() && it.length() > 0 }?.copyTo(File(work, AppDatabase.NAME + "-wal"), true)
            n = d.registros().contar()
        }
        val entries = work.listFiles()!!.associate { "db/${it.name}" to it } +
            (File(context.filesDir, "firmas").listFiles()?.filter { it.isFile && it.name.endsWith(".png") }?.associate { "firmas/${it.name}" to it } ?: emptyMap())
        BackupArchive.write(out, entries) { files ->
            BackupManifest(BackupArchive.FORMAT, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, AppDatabase.VERSION, ConoArenaLegacy.ENGINE_FULL_ID,
                "1.1.0", OffsetDateTime.now().toString(), counts = mapOf("registros" to n), files = files)
        }
        work.deleteRecursively()
        n
    }

    suspend fun exportar(uri: Uri): Int = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(uri, "w")?.use { exportar(it) } ?: throw BackupException("No se pudo abrir el destino")
    }

    /** Verifica el archivo y REEMPLAZA los registros actuales. Devuelve cuántos registros quedaron. */
    suspend fun restaurar(input: InputStream): Int = withContext(Dispatchers.IO) {
        val staging = File(context.cacheDir, "restaurar").apply { deleteRecursively(); mkdirs() }
        val m = BackupArchive.extractAndVerify(input, staging)
        if (m.dbSchemaVersion > AppDatabase.VERSION) throw BackupException("El respaldo es de una versión más nueva de la app")
        val nuevo = File(staging, "db/${AppDatabase.NAME}")
        if (!nuevo.exists()) throw BackupException("El archivo no contiene registros de GeoDensidad")
        val n = SQLiteDatabase.openDatabase(nuevo.path, null, SQLiteDatabase.OPEN_READWRITE).use { s ->
            s.rawQuery("PRAGMA integrity_check", null).use { c -> c.moveToFirst(); if (c.getString(0) != "ok") throw BackupException("Base de datos dañada") }
            s.rawQuery("SELECT COUNT(*) FROM registros", null).use { it.moveToFirst(); it.getInt(0) }
        }
        // copia de seguridad automática de lo actual antes de reemplazar
        File(context.filesDir, "respaldos").apply { mkdirs() }.let { dir ->
            File(dir, "antes-de-restaurar-${System.currentTimeMillis()}.zip").outputStream().use { exportar(it) }
        }
        closeDb()
        val dbFile = context.getDatabasePath(AppDatabase.NAME)
        listOf("", "-wal", "-shm").forEach { File(dbFile.path + it).delete() }
        nuevo.copyTo(dbFile, true)
        val firmasDir = File(context.filesDir, "firmas")
        File(staging, "firmas").takeIf { it.isDirectory }?.let { origen ->
            firmasDir.deleteRecursively(); firmasDir.mkdirs()
            origen.listFiles()?.forEach { it.copyTo(File(firmasDir, it.name), true) }
        }
        staging.deleteRecursively()
        n
    }
}
