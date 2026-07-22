package dev.codex.mihomometer.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import dev.codex.mihomometer.settings.AppThemeMode

private val LightColorScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF16615A),
    onPrimary = Color.White,
    secondary = Color(0xFF6A5ACD),
    tertiary = Color(0xFFC44E31),
    background = Color(0xFFF7F8FA),
    surface = Color.White,
    onSurface = Color(0xFF171A1F),
)

private val DarkColorScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFF78D6CA),
    onPrimary = Color(0xFF003731),
    secondary = Color(0xFFC5BFFF),
    tertiary = Color(0xFFFFB5A0),
    background = Color(0xFF101214),
    surface = Color(0xFF1A1D21),
    onSurface = Color(0xFFE7EAEE),
)

@Composable
fun MihomoMeterTheme(
    themeMode: AppThemeMode,
    content: @Composable () -> Unit,
) {
    val useDarkTheme = when (themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (useDarkTheme) DarkColorScheme else LightColorScheme,
        content = content,
    )
}
