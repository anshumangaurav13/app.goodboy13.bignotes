package app.goodboy13.bignotes.core.model

import app.goodboy13.bignotes.ui.theme.NotebookThemePreset

enum class PaperColor(
    val id: String,
    val displayName: String,
    val colorHex: Long,
    val r: Float,
    val g: Float,
    val b: Float,
    val a: Float = 1.0f,
    val isDark: Boolean = false
) {
    WHITE(
        id = "white",
        displayName = "Pure White",
        colorHex = 0xFFFFFFFF,
        r = 1.0f,
        g = 1.0f,
        b = 1.0f,
        isDark = false
    ),
    BLACK(
        id = "black",
        displayName = "Pure Black",
        colorHex = 0xFF121212,
        r = 0.0706f,
        g = 0.0706f,
        b = 0.0706f,
        isDark = true
    ),
    PARCHMENT(
        id = "parchment",
        displayName = "Parchment",
        colorHex = 0xFFFDF6E3,
        r = 0.99216f,
        g = 0.96471f,
        b = 0.89020f,
        isDark = false
    ),
    DARK_CHARCOAL(
        id = "dark_charcoal",
        displayName = "Dark Charcoal",
        colorHex = 0xFF202326,
        r = 0.12549f,
        g = 0.13725f,
        b = 0.14902f,
        isDark = true
    ),
    IVORY_VELLUM(
        id = "ivory_vellum",
        displayName = "Ivory Vellum",
        colorHex = 0xFFFBF7EE,
        r = 0.9843f,
        g = 0.9686f,
        b = 0.9333f,
        isDark = false
    ),
    SADDLE_DARK(
        id = "saddle_dark",
        displayName = "Saddle Noir",
        colorHex = 0xFF181512,
        r = 0.0941f,
        g = 0.0824f,
        b = 0.0706f,
        isDark = true
    ),
    FIELD_CREAM(
        id = "field_cream",
        displayName = "Field Journal Cream",
        colorHex = 0xFFF7F4EB,
        r = 0.9686f,
        g = 0.9568f,
        b = 0.9215f,
        isDark = false
    ),
    ESPRESSO_DARK(
        id = "espresso_dark",
        displayName = "Espresso Charcoal",
        colorHex = 0xFF151412,
        r = 0.0824f,
        g = 0.0784f,
        b = 0.0706f,
        isDark = true
    ),
    ARCHIVAL_PARCHMENT(
        id = "archival_parchment",
        displayName = "Archival Parchment",
        colorHex = 0xFFFAF4E6,
        r = 0.9804f,
        g = 0.9569f,
        b = 0.9020f,
        isDark = false
    ),
    OXFORD_DARK(
        id = "oxford_dark",
        displayName = "Codex Noir",
        colorHex = 0xFF141215,
        r = 0.0784f,
        g = 0.0706f,
        b = 0.0824f,
        isDark = true
    );

    val colorInt: Int
        get() = colorHex.toInt()

    val hexString: String
        get() = String.format("#%06X", 0xFFFFFF and colorInt)

    companion object {
        fun fromId(id: String?): PaperColor {
            return entries.find { it.id == id } ?: WHITE
        }

        fun forTheme(preset: NotebookThemePreset, isDark: Boolean): PaperColor {
            return when (preset) {
                NotebookThemePreset.CLASSIC_SADDLE -> if (isDark) SADDLE_DARK else IVORY_VELLUM
                NotebookThemePreset.TRAVELERS_ESPRESSO -> if (isDark) ESPRESSO_DARK else FIELD_CREAM
                NotebookThemePreset.VINTAGE_OXFORD -> if (isDark) OXFORD_DARK else ARCHIVAL_PARCHMENT
                NotebookThemePreset.STUDIO_CLEAN -> if (isDark) BLACK else WHITE
            }
        }

        fun forTheme(isDark: Boolean): PaperColor {
            return forTheme(NotebookThemePreset.CLASSIC_SADDLE, isDark)
        }

        fun forTheme(presetName: String, isDark: Boolean): PaperColor {
            val preset = NotebookThemePreset.fromId(presetName)
            return forTheme(preset, isDark)
        }
    }
}
