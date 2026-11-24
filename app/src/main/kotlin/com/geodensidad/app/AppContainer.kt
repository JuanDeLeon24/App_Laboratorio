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
import com.geodensidad.app.report.AndroidPdfWriter
import com.geodensidad.app.report.PdfWriterTarget
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

    /** Fábrica del escritor PDF (inyectable en pruebas; en la app, PdfDocument nativo). */
    var pdfWriterFactory: () -> PdfWriterTarget = { AndroidPdfWriter() }

    /** Servicios ligados a la instancia de base abierta; se recrean si la base se cierra y reabre. */
    private class Services(val repo: GeoRepository, val evidence: EvidenceRepository, val templates: TemplateService, val reports: ReportService, val demo: DemoLoader)
    @Volatile private var services: Services? = null
    private fun services(): Services = services ?: synchronized(this) {
        services ?: run {
            val r = GeoRepository(db, settings)
            val t = TemplateService(context, r)
            Services(r, EvidenceRepository(context, r), t, ReportService(context, r, t, settings) { pdfWriterFactory() }, DemoLoader(r))
        }.also { services = it }
    }
    val repo get() = services().repo
    val evidence get() = services().evidence
    val templates get() = services().templates
    val reports get() = services().reports
    val demo get() = services().demo
    val backups by lazy { BackupService(context, { db }, { closeDb() }) }

    fun closeDb() = synchronized(this) { _db?.close(); _db = null; services = null }

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
