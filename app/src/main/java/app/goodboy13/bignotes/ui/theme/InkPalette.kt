package app.goodboy13.bignotes.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

data class InkColorItem(
    val slotIndex: Int,
    val name: String,
    val color: Color,
    val colorRgb: Long = (color.toArgb().toLong() and 0xFFFFFFFFL)
)

object InkPalette {
    const val CUSTOM_INK_SLOT_INDEX = 6
    const val CUSTOM_HIGHLIGHTER_SLOT_INDEX = 3
    const val DEFAULT_CUSTOM_INK_RGB = 0xFF7C3AEDL
    const val DEFAULT_CUSTOM_HIGHLIGHTER_RGB = 0xFFA78BFAL

    // 6-color standard palette + Slot 6 editable custom ink:
    private val StandardInkColors = listOf(
        InkColorItem(0, "Ink Black", Color(0xFF000000)),
        InkColorItem(1, "Slate", Color(0xFF475569)),
        InkColorItem(2, "Crimson", Color(0xFFDC2626)),
        InkColorItem(3, "Royal Blue", Color(0xFF0018C8)),
        InkColorItem(4, "Emerald", Color(0xFF059669)),
        InkColorItem(5, "Amber", Color(0xFFD97706))
    )

    private fun hlItem(slot: Int, name: String, color: Color): InkColorItem {
        val aInt = 0xFF // Solid continuous stroke, no opacity
        val rgbInt = color.toArgb() and 0x00FFFFFF
        val argb = ((aInt.toLong() and 0xFFL) shl 24) or (rgbInt.toLong() and 0xFFFFFFL)
        return InkColorItem(slot, name, color, argb)
    }

    private val StandardHighlighterColors = listOf(
        hlItem(0, "Canary Yellow", Color(0xFFFFEE33)),
        hlItem(1, "Mint Green", Color(0xFF5EEAD4)),
        hlItem(2, "Coral Pink", Color(0xFFFB7185))
    )

    fun getColors(isDark: Boolean = false, customColorRgb: Long? = DEFAULT_CUSTOM_INK_RGB): List<InkColorItem> {
        val custom = customColorRgb ?: DEFAULT_CUSTOM_INK_RGB
        val normalized = (custom and 0x00FFFFFFL) or 0xFF000000L
        return StandardInkColors + InkColorItem(
            slotIndex = CUSTOM_INK_SLOT_INDEX,
            name = "Custom",
            color = Color(normalized.toInt()),
            colorRgb = normalized
        )
    }

    fun getHighlighterColors(
        isDark: Boolean = false,
        customColorRgb: Long? = DEFAULT_CUSTOM_HIGHLIGHTER_RGB
    ): List<InkColorItem> {
        val custom = customColorRgb ?: DEFAULT_CUSTOM_HIGHLIGHTER_RGB
        val normalized = (custom and 0x00FFFFFFL) or 0xFF000000L
        return StandardHighlighterColors + InkColorItem(
            slotIndex = CUSTOM_HIGHLIGHTER_SLOT_INDEX,
            name = "Custom",
            color = Color(normalized.toInt()),
            colorRgb = normalized
        )
    }

    /**
     * Converts a stroke's color when dark mode changes.
     */
    fun mapColor(
        previousColorRgb: Long,
        paletteIndex: Int,
        oldIsDark: Boolean,
        newIsDark: Boolean
    ): Long {
        if (paletteIndex == CUSTOM_INK_SLOT_INDEX) {
            return (previousColorRgb and 0x00FFFFFFL) or 0xFF000000L
        }
        val palette = StandardInkColors
        if (paletteIndex in palette.indices) {
            return palette[paletteIndex].colorRgb
        }
        val matchedIndex = palette.indexOfFirst { (it.colorRgb and 0xFFFFFFL) == (previousColorRgb and 0xFFFFFFL) }
        return if (matchedIndex in palette.indices) {
            palette[matchedIndex].colorRgb
        } else {
            palette[0].colorRgb
        }
    }
}
