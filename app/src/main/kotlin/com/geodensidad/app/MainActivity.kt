package com.geodensidad.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.geodensidad.app.data.DbCheck
import com.geodensidad.app.ui.AppNavHost
import com.geodensidad.app.ui.theme.Condor
import com.geodensidad.app.ui.theme.GeoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as GeoDensidadApp).container
        setContent {
            GeoTheme {
                var state by remember { mutableStateOf<Result<DbCheck>?>(null) }
                LaunchedEffect(Unit) { state = runCatching { container.open() } }
                when (val s = state) {
                    null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    else -> {
                        val check = s.getOrNull()
                        when {
                            s.isFailure -> Diagnostic("No se pudo abrir o migrar la base de datos", (s.exceptionOrNull()?.message ?: "Error desconocido") +
                                "\n\nLos archivos de datos NO se han borrado. Antes de migrar se guardó una copia en files/backups/pre-migracion. Contacte al soporte con este mensaje.")
                            check is DbCheck.Incompatible -> Diagnostic("Base de datos de una versión más reciente",
                                "Los datos fueron creados por una versión de GeoDensidad con base de datos v${check.found}; esta instalación admite hasta v${check.supported}. " +
                                    "Para no dañarlos, la base no se abrió. Instale la versión más reciente de la aplicación. No se ofrece retroceso de versión.")
                            check is DbCheck.Unreadable -> Diagnostic("Base de datos ilegible", "${check.message}\n\nLos archivos se conservan sin modificar. Puede restaurar un respaldo desde otra instalación.")
                            else -> AppNavHost()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Diagnostic(title: String, text: String) {
    Column(Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Spacer(Modifier.height(40.dp))
        Icon(Icons.Default.ReportProblem, null, tint = Condor.Bad, modifier = Modifier.size(56.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, color = Condor.NavyDeep)
        Text(text, style = MaterialTheme.typography.bodyLarge)
        Text("Versión de la app: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · Base de datos v${BuildConfig.DB_SCHEMA_VERSION}", style = MaterialTheme.typography.bodySmall, color = Condor.Steel)
    }
}
