package com.geodensidad.app

import android.content.Context
import com.geodensidad.app.data.AppDatabase
import com.geodensidad.app.report.AndroidPdfWriter
import com.geodensidad.app.report.PdfService
import com.geodensidad.app.report.PdfWriterTarget
import com.geodensidad.app.report.Respaldo
import com.geodensidad.app.report.Intercambio
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Dependencias de la app (inyección manual por constructor). */
class AppContainer(val context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var _db: AppDatabase? = null
    val db: AppDatabase get() = _db ?: synchronized(this) { _db ?: AppDatabase.build(context).also { _db = it } }
    fun closeDb() = synchronized(this) { _db?.close(); _db = null }

    var pdfWriterFactory: () -> PdfWriterTarget = { AndroidPdfWriter() }
    val pdf get() = PdfService(context, pdfWriterFactory)
    val respaldo get() = Respaldo(context, { db }, ::closeDb)
    val intercambio get() = Intercambio(context) { db }
    /** Archivo .xlsx recibido desde otra app (Abrir con / Compartir), pendiente de importar. */
    val archivoRecibido = kotlinx.coroutines.flow.MutableStateFlow<android.net.Uri?>(null)
}
