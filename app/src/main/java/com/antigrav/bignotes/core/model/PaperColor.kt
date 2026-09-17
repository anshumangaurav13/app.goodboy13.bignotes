package com.antigrav.bignotes.core.model

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
    MONOKAI_LIGHT(
        id = "monokai_light",
        displayName = "Monokai Light",
        colorHex = 0xFFFAF9F6,
        r = 0.9804f,
        g = 0.9765f,
        b = 0.9647f,
        isDark = false
    ),
    MONOKAI_DARK(
        id = "monokai_dark",
        displayName = "Monokai Dark",
        colorHex = 0xFF272822,
        r = 0.1529f,
        g = 0.1569f,
        b = 0.1333f,
        isDark = true
    ),
    GRUVBOX_LIGHT(
        id = "gruvbox_light",
        displayName = "Gruvbox Light",
        colorHex = 0xFFFBF1C7,
        r = 0.9843f,
        g = 0.9451f,
        b = 0.7804f,
        isDark = false
    ),
    GRUVBOX_DARK(
        id = "gruvbox_dark",
        displayName = "Gruvbox Dark",
        colorHex = 0xFF282828,
        r = 0.1569f,
        g = 0.1569f,
        b = 0.1569f,
        isDark = true
    ),
    SOLARIZED_LIGHT(
        id = "solarized_light",
        displayName = "Solarized Light",
        colorHex = 0xFFFDF6E3,
        r = 0.9922f,
        g = 0.9647f,
        b = 0.8902f,
        isDark = false
    ),
    SOLARIZED_DARK(
        id = "solarized_dark",
        displayName = "Solarized Dark",
        colorHex = 0xFF002B36,
        r = 0.0f,
        g = 0.1686f,
        b = 0.2118f,
        isDark = true
    ),
    NORD_LIGHT(
        id = "nord_light",
        displayName = "Nord Light",
        colorHex = 0xFFECEFF4,
        r = 0.9255f,
        g = 0.9373f,
        b = 0.9569f,
        isDark = false
    ),
    NORD_DARK(
        id = "nord_dark",
        displayName = "Nord Dark",
        colorHex = 0xFF2E3440,
        r = 0.1804f,
        g = 0.2039f,
        b = 0.2510f,
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

        fun forTheme(presetName: String, isDark: Boolean): PaperColor {
            return when (presetName.uppercase()) {
                "MONOKAI" -> if (isDark) MONOKAI_DARK else MONOKAI_LIGHT
                "GRUVBOX" -> if (isDark) GRUVBOX_DARK else GRUVBOX_LIGHT
                "SOLARIZED" -> if (isDark) SOLARIZED_DARK else SOLARIZED_LIGHT
                "NORD" -> if (isDark) NORD_DARK else NORD_LIGHT
                else -> if (isDark) BLACK else WHITE
            }
        }
    }
}
