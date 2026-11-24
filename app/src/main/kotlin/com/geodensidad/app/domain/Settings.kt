package com.geodensidad.app.domain

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.settingsStore by preferencesDataStore(name = "settings")

data class AppSettings(
    /** Nombre del responsable local: atribución operativa, NO autenticación. */
    val responsible: String = "",
    val photosPerPage: Int = 2,
    val gpsEnabled: Boolean = false,
    val includeCoordinatesInReports: Boolean = false,
    val defaultBulk: String = "2.667",
)

class SettingsRepository(private val context: Context) {
    private object K {
        val responsible = stringPreferencesKey("responsible")
        val photosPerPage = intPreferencesKey("photos_per_page")
        val gps = booleanPreferencesKey("gps_enabled")
        val coords = booleanPreferencesKey("coords_in_reports")
        val bulk = stringPreferencesKey("default_bulk")
    }

    val flow: Flow<AppSettings> = context.settingsStore.data.map { p ->
        AppSettings(
            responsible = p[K.responsible] ?: "",
            photosPerPage = p[K.photosPerPage] ?: 2,
            gpsEnabled = p[K.gps] ?: false,
            includeCoordinatesInReports = p[K.coords] ?: false,
            defaultBulk = p[K.bulk] ?: "2.667",
        )
    }

    suspend fun current() = flow.first()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        val n = transform(current())
        context.settingsStore.edit { p ->
            p[K.responsible] = n.responsible
            p[K.photosPerPage] = n.photosPerPage.coerceIn(1, 2)
            p[K.gps] = n.gpsEnabled
            p[K.coords] = n.includeCoordinatesInReports
            p[K.bulk] = n.defaultBulk
        }
    }
}
