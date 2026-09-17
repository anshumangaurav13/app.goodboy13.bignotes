package com.antigrav.bignotes.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

enum class AppThemePreset(val displayName: String, val primaryColor: Color, val previewBg: Color) {
    DEFAULT("Default Slate", Color(0xFF1E88E5), Color(0xFF18181B)),
    MONOKAI("Monokai", Color(0xFFA6E22E), Color(0xFF272822)),
    GRUVBOX("Gruvbox", Color(0xFFFE8019), Color(0xFF282828)),
    SOLARIZED("Solarized", Color(0xFF2AA198), Color(0xFF002B36)),
    NORD("Nord", Color(0xFF88C0D0), Color(0xFF2E3440))
}

data class ThemeConfig(
    val isDark: Boolean = false,
    val preset: AppThemePreset = AppThemePreset.DEFAULT
)

fun getThemeColorScheme(config: ThemeConfig): ColorScheme {
    return if (config.isDark) {
        when (config.preset) {
            AppThemePreset.DEFAULT -> darkColorScheme(
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
            AppThemePreset.MONOKAI -> darkColorScheme(
                primary = Color(0xFFA6E22E), // Monokai Green
                secondary = Color(0xFF66D9EF), // Monokai Cyan
                tertiary = Color(0xFFF92672), // Monokai Pink
                background = Color(0xFF1E1F1C),
                surface = Color(0xFF272822),
                surfaceVariant = Color(0xFF383830),
                onPrimary = Color(0xFF1E1F1C),
                onBackground = Color(0xFFF8F8F2),
                onSurface = Color(0xFFF8F8F2),
                outline = Color(0xFF49483E)
            )
            AppThemePreset.GRUVBOX -> darkColorScheme(
                primary = Color(0xFFFE8019), // Gruvbox Orange
                secondary = Color(0xFFFABD2F), // Gruvbox Yellow
                tertiary = Color(0xFFB8BB26), // Gruvbox Green
                background = Color(0xFF1D2021),
                surface = Color(0xFF282828),
                surfaceVariant = Color(0xFF3C3836),
                onPrimary = Color(0xFF1D2021),
                onBackground = Color(0xFFEBDBB2),
                onSurface = Color(0xFFEBDBB2),
                outline = Color(0xFF504945)
            )
            AppThemePreset.SOLARIZED -> darkColorScheme(
                primary = Color(0xFF2AA198), // Solarized Cyan
                secondary = Color(0xFF268BD2), // Solarized Blue
                tertiary = Color(0xFF859900), // Solarized Green
                background = Color(0xFF00212B),
                surface = Color(0xFF073642),
                surfaceVariant = Color(0xFF0A4452),
                onPrimary = Color.White,
                onBackground = Color(0xFFEEE8D5),
                onSurface = Color(0xFF93A1A1),
                outline = Color(0xFF586E75)
            )
            AppThemePreset.NORD -> darkColorScheme(
                primary = Color(0xFF88C0D0), // Nord Frost
                secondary = Color(0xFF81A1C1),
                tertiary = Color(0xFFA3BE8C),
                background = Color(0xFF242933),
                surface = Color(0xFF2E3440),
                surfaceVariant = Color(0xFF3B4252),
                onPrimary = Color(0xFF2E3440),
                onBackground = Color(0xFFECEFF4),
                onSurface = Color(0xFFD8DEE9),
                outline = Color(0xFF4C566A)
            )
        }
    } else {
        when (config.preset) {
            AppThemePreset.DEFAULT -> lightColorScheme(
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
            AppThemePreset.MONOKAI -> lightColorScheme(
                primary = Color(0xFF75715E),
                secondary = Color(0xFFF92672),
                tertiary = Color(0xFFA6E22E),
                background = Color(0xFFFAF9F6),
                surface = Color(0xFFFFFFFF),
                surfaceVariant = Color(0xFFF0EFEA),
                onPrimary = Color.White,
                onBackground = Color(0xFF272822),
                onSurface = Color(0xFF272822),
                outline = Color(0xFFE0DDD5)
            )
            AppThemePreset.GRUVBOX -> lightColorScheme(
                primary = Color(0xFFAF3A03), // Gruvbox Dark Orange
                secondary = Color(0xFFB57614), // Gruvbox Yellow
                tertiary = Color(0xFF79740E), // Gruvbox Green
                background = Color(0xFFFBF1C7), // Gruvbox Light Bg
                surface = Color(0xFFF9F5D7),
                surfaceVariant = Color(0xFFEBDBB2),
                onPrimary = Color.White,
                onBackground = Color(0xFF3C3836),
                onSurface = Color(0xFF3C3836),
                outline = Color(0xFFD5C4A1)
            )
            AppThemePreset.SOLARIZED -> lightColorScheme(
                primary = Color(0xFF268BD2), // Solarized Blue
                secondary = Color(0xFF2AA198), // Solarized Cyan
                tertiary = Color(0xFF859900),
                background = Color(0xFFFDF6E3), // Solarized Base3
                surface = Color(0xFFEEE8D5), // Solarized Base2
                surfaceVariant = Color(0xFFE0D8C3),
                onPrimary = Color.White,
                onBackground = Color(0xFF073642),
                onSurface = Color(0xFF657B83),
                outline = Color(0xFFD3CBBB)
            )
            AppThemePreset.NORD -> lightColorScheme(
                primary = Color(0xFF5E81AC),
                secondary = Color(0xFF88C0D0),
                tertiary = Color(0xFFA3BE8C),
                background = Color(0xFFECEFF4),
                surface = Color(0xFFFFFFFF),
                surfaceVariant = Color(0xFFE5E9F0),
                onPrimary = Color.White,
                onBackground = Color(0xFF2E3440),
                onSurface = Color(0xFF3B4252),
                outline = Color(0xFFD8DEE9)
            )
        }
    }
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
