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
    // 6-color palette slots:
    // Slot 0: Primary Ink
    // Slot 1: Secondary / Slate
    // Slot 2: Red / Coral / Pink
    // Slot 3: Blue / Cyan / Sky
    // Slot 4: Green / Emerald / Mint
    // Slot 5: Amber / Yellow / Gold

    fun getColors(preset: AppThemePreset, isDark: Boolean = false): List<InkColorItem> {
        return when (preset) {
                AppThemePreset.DEFAULT -> listOf(
                    InkColorItem(0, "Ink Black", Color(0xFF0F172A)),
                    InkColorItem(1, "Slate", Color(0xFF475569)),
                    InkColorItem(2, "Crimson", Color(0xFFB91C1C)),
                    InkColorItem(3, "Royal Blue", Color(0xFF1D4ED8)),
                    InkColorItem(4, "Emerald", Color(0xFF047857)),
                    InkColorItem(5, "Amber", Color(0xFFB45309))
                )
                AppThemePreset.MONOKAI -> listOf(
                    InkColorItem(0, "Charcoal", Color(0xFF272822)),
                    InkColorItem(1, "Warm Gray", Color(0xFF75715E)),
                    InkColorItem(2, "Crimson", Color(0xFFD71858)),
                    InkColorItem(3, "Ocean", Color(0xFF0288D1)),
                    InkColorItem(4, "Dark Green", Color(0xFF388E3C)),
                    InkColorItem(5, "Dark Orange", Color(0xFFE65100))
                )
                AppThemePreset.GRUVBOX -> listOf(
                    InkColorItem(0, "Dark", Color(0xFF282828)),
                    InkColorItem(1, "Gray", Color(0xFF7C6F64)),
                    InkColorItem(2, "Dark Red", Color(0xFFCC241D)),
                    InkColorItem(3, "Dark Blue", Color(0xFF076678)),
                    InkColorItem(4, "Dark Green", Color(0xFF79740E)),
                    InkColorItem(5, "Dark Yellow", Color(0xFFB57614))
                )
                AppThemePreset.SOLARIZED -> listOf(
                    InkColorItem(0, "Base01", Color(0xFF586E75)),
                    InkColorItem(1, "Base00", Color(0xFF657B83)),
                    InkColorItem(2, "Red", Color(0xFFDC322F)),
                    InkColorItem(3, "Blue", Color(0xFF268BD2)),
                    InkColorItem(4, "Green", Color(0xFF859900)),
                    InkColorItem(5, "Yellow", Color(0xFFB58900))
                )
                AppThemePreset.NORD -> listOf(
                    InkColorItem(0, "Polar 0", Color(0xFF2E3440)),
                    InkColorItem(1, "Polar 3", Color(0xFF4C566A)),
                    InkColorItem(2, "Aurora Red", Color(0xFFBF616A)),
                    InkColorItem(3, "Frost Blue", Color(0xFF5E81AC)),
                    InkColorItem(4, "Aurora Green", Color(0xFFA3BE8C)),
                    InkColorItem(5, "Aurora Yellow", Color(0xFFEBCB8B))
                )
            }
        }

    /**
     * Converts a stroke's color when theme or dark mode changes.
     */
    fun mapColor(
        previousColorRgb: Long,
        paletteIndex: Int,
        oldPreset: AppThemePreset,
        oldIsDark: Boolean,
        newPreset: AppThemePreset,
        newIsDark: Boolean
    ): Long {
        val newPalette = getColors(newPreset, newIsDark)
        if (paletteIndex in newPalette.indices) {
            return newPalette[paletteIndex].colorRgb
        }
        // Fallback: match by closest color in old palette
        val oldPalette = getColors(oldPreset, oldIsDark)
        val matchedIndex = oldPalette.indexOfFirst { (it.colorRgb and 0xFFFFFFL) == (previousColorRgb and 0xFFFFFFL) }
        return if (matchedIndex in newPalette.indices) {
            newPalette[matchedIndex].colorRgb
        } else {
            newPalette[0].colorRgb
        }
    }

    private fun hlItem(slot: Int, name: String, color: Color): InkColorItem {
        val aInt = 0xFF // Solid continuous stroke, no opacity
        val rgbInt = color.toArgb() and 0x00FFFFFF
        val argb = ((aInt.toLong() and 0xFFL) shl 24) or (rgbInt.toLong() and 0xFFFFFFL)
        return InkColorItem(slot, name, color, argb)
    }

    /**
     * Curated 3-color highlighter triplet per theme preset.
     * Slot 0: Primary warm highlight (Yellow / Gold / Amber / Honey)
     * Slot 1: Cool / fresh highlight (Mint / Lime / Cyan / Aqua)
     * Slot 2: Accent highlight (Pink / Magenta / Coral / Rose)
     */
    fun getHighlighterColors(preset: AppThemePreset, isDark: Boolean = false): List<InkColorItem> {
        return when (preset) {
            AppThemePreset.DEFAULT -> listOf(
                hlItem(0, "Canary Yellow", Color(0xFFFFEE33)),
                hlItem(1, "Mint Green", Color(0xFF5EEAD4)),
                hlItem(2, "Coral Pink", Color(0xFFFB7185))
            )
            AppThemePreset.MONOKAI -> listOf(
                hlItem(0, "Monokai Canary", Color(0xFFFFE043)),
                hlItem(1, "Monokai Lime", Color(0xFFA6E22E)),
                hlItem(2, "Monokai Hot Pink", Color(0xFFF92672))
            )
            AppThemePreset.GRUVBOX -> listOf(
                hlItem(0, "Gruvbox Yellow", Color(0xFFFABD2F)),
                hlItem(1, "Gruvbox Olive", Color(0xFFA0B920)),
                hlItem(2, "Gruvbox Orange", Color(0xFFFA7C20))
            )
            AppThemePreset.SOLARIZED -> listOf(
                hlItem(0, "Solarized Amber", Color(0xFFD3A000)),
                hlItem(1, "Solarized Cyan", Color(0xFF2AA198)),
                hlItem(2, "Solarized Magenta", Color(0xFFD33682))
            )
            AppThemePreset.NORD -> listOf(
                hlItem(0, "Nord Sun", Color(0xFFEBCB8B)),
                hlItem(1, "Nord Aurora", Color(0xFFA3BE8C)),
                hlItem(2, "Nord Frost", Color(0xFF88C0D0))
            )
        }
    }
}
