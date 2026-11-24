package com.geodensidad.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Colorimetría de Construcciones El Cóndor S.A., muestreada del logotipo del formato de origen:
 * azul marino #0B294B, marino profundo #041B37, acero #435667, pizarra #6E7F8F,
 * celeste #B5CCEB y niebla #D1DCE5. Tema claro de alto contraste para uso a pleno sol.
 */
object Condor {
    val Navy = Color(0xFF0B294B)
    val NavyDeep = Color(0xFF041B37)
    val Steel = Color(0xFF435667)
    val Slate = Color(0xFF6E7F8F)
    val Sky = Color(0xFFB5CCEB)
    val Mist = Color(0xFFD1DCE5)
    val Paper = Color(0xFFF6F8FA)
    val Ok = Color(0xFF1B6E3A)
    val Bad = Color(0xFFA4262C)
    val Warn = Color(0xFF8A5A00)
}

private val scheme = lightColorScheme(
    primary = Condor.Navy, onPrimary = Color.White,
    primaryContainer = Condor.Sky, onPrimaryContainer = Condor.NavyDeep,
    secondary = Condor.Steel, onSecondary = Color.White,
    secondaryContainer = Condor.Mist, onSecondaryContainer = Condor.NavyDeep,
    tertiary = Condor.Slate, onTertiary = Color.White,
    background = Condor.Paper, onBackground = Color(0xFF15191E),
    surface = Color.White, onSurface = Color(0xFF15191E),
    surfaceVariant = Condor.Mist, onSurfaceVariant = Condor.Steel,
    surfaceContainer = Color(0xFFEEF2F6), surfaceContainerHigh = Color(0xFFE6ECF2), surfaceContainerLow = Color(0xFFF7F9FB),
    outline = Condor.Slate, outlineVariant = Color(0xFFC5CED6),
    error = Condor.Bad, onError = Color.White, errorContainer = Color(0xFFFADADB), onErrorContainer = Color(0xFF410006),
)

private val base = Typography()
private val typography = base.copy(
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.copy(fontSize = 16.sp),
)

@Composable
fun GeoTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = scheme, typography = typography, content = content)

val Mono = TextStyle(fontWeight = FontWeight.Medium)
