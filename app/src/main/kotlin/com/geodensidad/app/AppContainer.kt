package com.geodensidad.app

import android.content.Context
import com.geodensidad.app.backup.BackupService
import com.geodensidad.app.data.AppDatabase
import com.geodensidad.app.data.DbCheck
import com.geodensidad.app.data.DbGuard
import com.geodensidad.app.domain.DemoLoader
import com.geodensidad.app.domain.GeoRepository
import com.geodensidad.app.domain.SettingsRepository
import com.geodensidad.app.evidence.EvidenceRepository
import com.geodensidad.app.report.ReportService
import com.geodensidad.app.report.TemplateService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext

/**
 * Inyección de dependencias manual (constructor injection) con un contenedor por proceso.
 * Decisión documentada en docs/ARQUITECTURA.md: evita generación de código adicional y
 * mantiene explícito el ciclo de vida de la base de datos (necesario para restaurar).
 */
class AppContainer(val context: Context) {
    /** Ámbito de aplicación: guardados que deben terminar aunque la pantalla se cierre. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val settings = SettingsRepository(context)

    @Volatile private var _db: AppDatabase? = null
    val db: AppDatabase get() = _db ?: synchronized(this) { _db ?: AppDatabase.build(context).also { _db = it } }

    val repo by lazy { GeoRepository(db, settings) }
    val evidence by lazy { EvidenceRepository(context, repo) }
    val templates by lazy { TemplateService(context, repo) }
    val reports by lazy { ReportService(context, repo, templates, settings) }
    val backups by lazy { BackupService(context, { db }, { closeDb() }) }
    val demo by lazy { DemoLoader(repo) }

    fun closeDb() = synchronized(this) { _db?.close(); _db = null }

    /** Comprueba la versión de la base ANTES de abrir Room y fuerza la apertura (migraciones). */
    suspend fun open(): DbCheck = withContext(Dispatchers.IO) {
        val check = DbGuard.check(context)
        if (check is DbCheck.Incompatible || check is DbCheck.Unreadable) return@withContext check
        db.openHelper.writableDatabase
        repo.ensureCriteriaSeed()
        templates.ensureSeed()
        check
    }
}
