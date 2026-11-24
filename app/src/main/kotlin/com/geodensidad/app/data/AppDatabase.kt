package com.geodensidad.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Database(
    entities = [
        ProjectEntity::class, ActivityEntity::class, JourneyEntity::class, LotEntity::class, SampleEntity::class,
        SampleRevisionEntity::class, MaterialEntity::class, EquipmentEntity::class, CalibrationEntity::class,
        LabReferenceEntity::class, PersonEntity::class, CriterionEntity::class, TemplateVersionEntity::class,
        CustomFieldEntity::class, PhotoEntity::class, ReportEntity::class, AuditEntity::class,
    ],
    version = AppDatabase.VERSION,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projects(): ProjectDao
    abstract fun activities(): ActivityDao
    abstract fun journeys(): JourneyDao
    abstract fun lots(): LotDao
    abstract fun samples(): SampleDao
    abstract fun revisions(): RevisionDao
    abstract fun catalogs(): CatalogDao
    abstract fun templates(): TemplateDao
    abstract fun customFields(): CustomFieldDao
    abstract fun photos(): PhotoDao
    abstract fun reports(): ReportDao
    abstract fun audit(): AuditDao

    companion object {
        const val VERSION = 1
        const val NAME = "geodensidad.db"

        /**
         * Rutas de migración explícitas. Cada cambio de esquema debe añadir aquí una Migration(n, n+1)
         * probada. NUNCA se usa fallbackToDestructiveMigration: perdería datos de campo.
         */
        val MIGRATIONS: Array<Migration> = arrayOf()

        fun build(context: Context, name: String = NAME): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, name)
                .addMigrations(*MIGRATIONS)
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .build()
    }
}

/** Resultado de la comprobación previa a abrir la base de datos. */
sealed interface DbCheck {
    data object Fresh : DbCheck
    data object UpToDate : DbCheck
    data class NeedsMigration(val from: Int, val backupDir: File) : DbCheck
    data class Incompatible(val found: Int, val supported: Int) : DbCheck
    data class Unreadable(val message: String) : DbCheck
}

/**
 * Guardián de la base de datos: lee user_version SIN abrir Room.
 *  - Si la base es de una versión superior (app más antigua que los datos) no se abre.
 *  - Si requiere migración, copia antes base + WAL + SHM a backups/pre-migracion.
 */
object DbGuard {
    fun check(context: Context, name: String = AppDatabase.NAME, supported: Int = AppDatabase.VERSION): DbCheck {
        val dbFile = context.getDatabasePath(name)
        if (!dbFile.exists()) return DbCheck.Fresh
        val version = try {
            SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY).use { it.version }
        } catch (e: Exception) {
            return DbCheck.Unreadable(e.message ?: e.javaClass.simpleName)
        }
        return when {
            version > supported -> DbCheck.Incompatible(version, supported)
            version in 1 until supported -> DbCheck.NeedsMigration(version, preMigrationCopy(context, dbFile, version))
            else -> DbCheck.UpToDate
        }
    }

    fun preMigrationCopy(context: Context, dbFile: File, fromVersion: Int): File {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val dir = File(context.filesDir, "backups/pre-migracion/v$fromVersion-$stamp").apply { mkdirs() }
        for (suffix in listOf("", "-wal", "-shm")) {
            val f = File(dbFile.path + suffix)
            if (f.exists()) f.copyTo(File(dir, f.name), overwrite = false)
        }
        return dir
    }
}
