package com.antigrav.bignotes.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

data class InkColorItem(
    val slotIndex: Int,
    val name: String,
    val color: Color,
    val colorRgb: Long = (color.toArgb().toLong() and 0xFFFFFFFFL)
)

object InkPalette {
    // 6-color standard palette:
    // Slot 0: Ink Black
    // Slot 1: Slate
    // Slot 2: Crimson
    // Slot 3: Royal Blue
    // Slot 4: Emerald
    // Slot 5: Amber
    private val StandardInkColors = listOf(
        InkColorItem(0, "Ink Black", Color(0xFF0F172A)),
        InkColorItem(1, "Slate", Color(0xFF475569)),
        InkColorItem(2, "Crimson", Color(0xFFB91C1C)),
        InkColorItem(3, "Royal Blue", Color(0xFF1D4ED8)),
        InkColorItem(4, "Emerald", Color(0xFF047857)),
        InkColorItem(5, "Amber", Color(0xFFB45309))
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

    fun getColors(isDark: Boolean = false): List<InkColorItem> = StandardInkColors

    fun getHighlighterColors(isDark: Boolean = false): List<InkColorItem> = StandardHighlighterColors

    /**
     * Converts a stroke's color when dark mode changes.
     */
    fun mapColor(
        previousColorRgb: Long,
        paletteIndex: Int,
        oldIsDark: Boolean,
        newIsDark: Boolean
    ): Long {
        val palette = getColors(newIsDark)
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
