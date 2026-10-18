package app.goodboy13.bignotes.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

data class ThemeConfig(
    val isDark: Boolean = false
)

private val DefaultDarkColorScheme = darkColorScheme(
    primary = Color(0xFF1E88E5),
    secondary = Color(0xFF64B5F6),
    tertiary = Color(0xFF26A69A),
    background = Color(0xFF141416),
    surface = Color(0xFF1E1E22),
    surfaceVariant = Color(0xFF28282E),
    onPrimary = Color.White,
    onBackground = Color(0xFFE4E4E7),
    onSurface = Color(0xFFE4E4E7),
    outline = Color(0xFF3F3F46)
)

private val DefaultLightColorScheme = lightColorScheme(
    primary = Color(0xFF1E88E5),
    secondary = Color(0xFF0288D1),
    tertiary = Color(0xFF00897B),
    background = Color(0xFFF8F9FA),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF1F3F5),
    onPrimary = Color.White,
    onBackground = Color(0xFF1E1E24),
    onSurface = Color(0xFF1E1E24),
    outline = Color(0xFFE2E8F0)
)

fun getThemeColorScheme(config: ThemeConfig): ColorScheme {
    return if (config.isDark) DefaultDarkColorScheme else DefaultLightColorScheme
}

@Composable
fun BigNotesTheme(
    themeConfig: ThemeConfig,
    content: @Composable () -> Unit
) {
    val colorScheme = getThemeColorScheme(themeConfig)

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
