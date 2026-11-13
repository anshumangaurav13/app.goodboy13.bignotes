package app.goodboy13.bignotes.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

enum class NotebookThemePreset(
    val id: String,
    val displayName: String,
    val subtitle: String
) {
    CLASSIC_SADDLE(
        id = "classic_saddle",
        displayName = "Classic Saddle Folio",
        subtitle = "Warm cognac leather, cream saddle stitching, walnut & brass dock"
    ),
    TRAVELERS_ESPRESSO(
        id = "travelers_espresso",
        displayName = "Traveler's Espresso",
        subtitle = "Dark oiled espresso leather, brass rivets, rugged field journal"
    ),
    VINTAGE_OXFORD(
        id = "vintage_oxford",
        displayName = "Vintage Oxford Archivist",
        subtitle = "Deep cordovan leather, blind debossing & antique brass corners"
    ),
    STUDIO_CLEAN(
        id = "studio_clean",
        displayName = "Studio Minimalist",
        subtitle = "Modern clean canvas with neutral glassmorphic toolbar"
    );

    companion object {
        fun fromId(id: String?): NotebookThemePreset {
            return entries.find { it.id == id } ?: CLASSIC_SADDLE
        }
    }
}

data class ThemeConfig(
    val isDark: Boolean = false,
    val preset: NotebookThemePreset = NotebookThemePreset.CLASSIC_SADDLE
)

data class LeatherThemeStyle(
    val leatherColor: Color,
    val leatherBorderColor: Color,
    val stitchColor: Color,
    val metalAccentColor: Color,
    val ribbonColor: Color? = null,
    val toolbarBackground: Color,
    val toolbarBorder: Color
)

fun getLeatherThemeStyle(preset: NotebookThemePreset, isDark: Boolean): LeatherThemeStyle {
    return when (preset) {
        NotebookThemePreset.CLASSIC_SADDLE -> if (!isDark) {
            LeatherThemeStyle(
                leatherColor = Color(0xFF8B4513),
                leatherBorderColor = Color(0xFF5C2C09),
                stitchColor = Color(0xFFF6E7D2),
                metalAccentColor = Color(0xFFD4AF37),
                ribbonColor = Color(0xFF8B1E2B),
                toolbarBackground = Color(0xFF3E2312),
                toolbarBorder = Color(0xFFC59D5F)
            )
        } else {
            LeatherThemeStyle(
                leatherColor = Color(0xFF261A12),
                leatherBorderColor = Color(0xFF170F0A),
                stitchColor = Color(0xFFB09579),
                metalAccentColor = Color(0xFFE2B772),
                ribbonColor = Color(0xFF6B1520),
                toolbarBackground = Color(0xFF21160F),
                toolbarBorder = Color(0xFF8C6D40)
            )
        }

        NotebookThemePreset.TRAVELERS_ESPRESSO -> if (!isDark) {
            LeatherThemeStyle(
                leatherColor = Color(0xFF2E241E),
                leatherBorderColor = Color(0xFF1A1410),
                stitchColor = Color(0xFFD2C7B8),
                metalAccentColor = Color(0xFFA67C52),
                ribbonColor = null,
                toolbarBackground = Color(0xFF2B211A),
                toolbarBorder = Color(0xFF8B6F4E)
            )
        } else {
            LeatherThemeStyle(
                leatherColor = Color(0xFF191613),
                leatherBorderColor = Color(0xFF0F0D0B),
                stitchColor = Color(0xFF8C8072),
                metalAccentColor = Color(0xFF8E7155),
                ribbonColor = null,
                toolbarBackground = Color(0xFF191512),
                toolbarBorder = Color(0xFF5A4938)
            )
        }

        NotebookThemePreset.VINTAGE_OXFORD -> if (!isDark) {
            LeatherThemeStyle(
                leatherColor = Color(0xFF4A1525),
                leatherBorderColor = Color(0xFF2D0B16),
                stitchColor = Color(0xFFEADBCE),
                metalAccentColor = Color(0xFFC5A059),
                ribbonColor = Color(0xFF1B365D),
                toolbarBackground = Color(0xFF330E1A),
                toolbarBorder = Color(0xFFD4AF37)
            )
        } else {
            LeatherThemeStyle(
                leatherColor = Color(0xFF240E16),
                leatherBorderColor = Color(0xFF14070C),
                stitchColor = Color(0xFFA3897C),
                metalAccentColor = Color(0xFFD4AF37),
                ribbonColor = Color(0xFF12233D),
                toolbarBackground = Color(0xFF1C0A11),
                toolbarBorder = Color(0xFF9E7E32)
            )
        }

        NotebookThemePreset.STUDIO_CLEAN -> if (!isDark) {
            LeatherThemeStyle(
                leatherColor = Color(0xFFFFFFFF),
                leatherBorderColor = Color(0xFFE2E8F0),
                stitchColor = Color(0xFFCBD5E1),
                metalAccentColor = Color(0xFF1E88E5),
                ribbonColor = null,
                toolbarBackground = Color(0xFFFFFFFF),
                toolbarBorder = Color(0xFFE2E8F0)
            )
        } else {
            LeatherThemeStyle(
                leatherColor = Color(0xFF1E1E22),
                leatherBorderColor = Color(0xFF28282E),
                stitchColor = Color(0xFF3F3F46),
                metalAccentColor = Color(0xFF64B5F6),
                ribbonColor = null,
                toolbarBackground = Color(0xFF1E1E22),
                toolbarBorder = Color(0xFF3F3F46)
            )
        }
    }
}

// 1. Classic Saddle Folio Schemes
private val ClassicSaddleLight = lightColorScheme(
    primary = Color(0xFF8B4513),
    secondary = Color(0xFFB47814),
    tertiary = Color(0xFF2E5A27),
    background = Color(0xFFF4ECE1),
    surface = Color(0xFFF9F5EC),
    surfaceVariant = Color(0xFFEBE0CE),
    onPrimary = Color.White,
    onBackground = Color(0xFF2C1E14),
    onSurface = Color(0xFF2C1E14),
    outline = Color(0xFFC7B199)
)

private val ClassicSaddleDark = darkColorScheme(
    primary = Color(0xFFD49757),
    secondary = Color(0xFFE2B772),
    tertiary = Color(0xFF82C777),
    background = Color(0xFF18130F),
    surface = Color(0xFF231C16),
    surfaceVariant = Color(0xFF332820),
    onPrimary = Color(0xFF201309),
    onBackground = Color(0xFFEDE0D1),
    onSurface = Color(0xFFEDE0D1),
    outline = Color(0xFF5A4638)
)

// 2. Traveler's Espresso Journal Schemes
private val TravelersEspressoLight = lightColorScheme(
    primary = Color(0xFF3D2C1F),
    secondary = Color(0xFF9A3412),
    tertiary = Color(0xFF1E40AF),
    background = Color(0xFFECE7DF),
    surface = Color(0xFFF5F1E8),
    surfaceVariant = Color(0xFFE0D8CB),
    onPrimary = Color.White,
    onBackground = Color(0xFF1C1917),
    onSurface = Color(0xFF1C1917),
    outline = Color(0xFFA89A8B)
)

private val TravelersEspressoDark = darkColorScheme(
    primary = Color(0xFFBCA290),
    secondary = Color(0xFFFB923C),
    tertiary = Color(0xFF60A5FA),
    background = Color(0xFF13110F),
    surface = Color(0xFF1E1A17),
    surfaceVariant = Color(0xFF2D2722),
    onPrimary = Color(0xFF1C140E),
    onBackground = Color(0xFFE7E5E4),
    onSurface = Color(0xFFE7E5E4),
    outline = Color(0xFF4C423A)
)

// 3. Vintage Oxford Archivist Schemes
private val VintageOxfordLight = lightColorScheme(
    primary = Color(0xFF60182E),
    secondary = Color(0xFF926F34),
    tertiary = Color(0xFF003153),
    background = Color(0xFFF5EEE3),
    surface = Color(0xFFFAF4E8),
    surfaceVariant = Color(0xFFEADBCA),
    onPrimary = Color.White,
    onBackground = Color(0xFF231219),
    onSurface = Color(0xFF231219),
    outline = Color(0xFFC3B0A0)
)

private val VintageOxfordDark = darkColorScheme(
    primary = Color(0xFFD9728B),
    secondary = Color(0xFFE5BE64),
    tertiary = Color(0xFF67E8F9),
    background = Color(0xFF160F13),
    surface = Color(0xFF22161E),
    surfaceVariant = Color(0xFF33202C),
    onPrimary = Color(0xFF230D16),
    onBackground = Color(0xFFF3E7ED),
    onSurface = Color(0xFFF3E7ED),
    outline = Color(0xFF533749)
)

// 4. Studio Clean Minimalist Schemes
private val StudioCleanLight = lightColorScheme(
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

private val StudioCleanDark = darkColorScheme(
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

fun getThemeColorScheme(config: ThemeConfig): ColorScheme {
    return when (config.preset) {
        NotebookThemePreset.CLASSIC_SADDLE -> if (config.isDark) ClassicSaddleDark else ClassicSaddleLight
        NotebookThemePreset.TRAVELERS_ESPRESSO -> if (config.isDark) TravelersEspressoDark else TravelersEspressoLight
        NotebookThemePreset.VINTAGE_OXFORD -> if (config.isDark) VintageOxfordDark else VintageOxfordLight
        NotebookThemePreset.STUDIO_CLEAN -> if (config.isDark) StudioCleanDark else StudioCleanLight
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
