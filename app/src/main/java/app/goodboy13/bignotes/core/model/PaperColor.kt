package app.goodboy13.bignotes.core.model

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
    );

    val colorInt: Int
        get() = colorHex.toInt()

    val hexString: String
        get() = String.format("#%06X", 0xFFFFFF and colorInt)

    companion object {
        fun fromId(id: String?): PaperColor {
            return entries.find { it.id == id } ?: WHITE
        }

        fun forTheme(isDark: Boolean): PaperColor {
            return if (isDark) BLACK else WHITE
        }

        fun forTheme(presetName: String, isDark: Boolean): PaperColor {
            return forTheme(isDark)
        }
    }
}
