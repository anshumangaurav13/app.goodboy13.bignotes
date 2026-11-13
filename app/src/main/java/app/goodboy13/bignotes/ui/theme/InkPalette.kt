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

    private fun hlItem(slot: Int, name: String, color: Color): InkColorItem {
        val aInt = 0xFF // Continuous stroke, full opacity pigment
        val rgbInt = color.toArgb() and 0x00FFFFFF
        val argb = ((aInt.toLong() and 0xFFL) shl 24) or (rgbInt.toLong() and 0xFFFFFFL)
        return InkColorItem(slot, name, color, argb)
    }

    // 1. Classic Saddle Folio Inks (Wet-Aware: Luminous mid-tones with rich pooling depth)
    private val SaddleLightInks = listOf(
        InkColorItem(0, "Sepia Walnut", Color(0xFF422818)),
        InkColorItem(1, "Bridle Leather", Color(0xFF78452B)),
        InkColorItem(2, "Terracotta Sienna", Color(0xFFC04C06)),
        InkColorItem(3, "Oxford Navy", Color(0xFF1D66BF)),
        InkColorItem(4, "Highland Moss", Color(0xFF3F8236)),
        InkColorItem(5, "Burnished Ochre", Color(0xFFCC8510))
    )

    private val SaddleDarkInks = listOf(
        InkColorItem(0, "Antique Linen", Color(0xFFF2EAE0)),
        InkColorItem(1, "Soft Camel", Color(0xFFDBBF9A)),
        InkColorItem(2, "Warm Sienna", Color(0xFFFF954F)),
        InkColorItem(3, "Sky Indigo", Color(0xFF8AB9F0)),
        InkColorItem(4, "Spring Moss", Color(0xFF90D485)),
        InkColorItem(5, "Amber Ochre", Color(0xFFFFCA5C))
    )

    private val SaddleHighlighters = listOf(
        hlItem(0, "Amber Glow", Color(0xFFFFD166)),
        hlItem(1, "Sage Wash", Color(0xFFA3C9A8)),
        hlItem(2, "Vintage Rose", Color(0xFFE8989E))
    )

    // 2. Traveler's Espresso Journal Inks (Wet-Aware: Crisp expedition tones that resist muddying)
    private val EspressoLightInks = listOf(
        InkColorItem(0, "Carbon Jet", Color(0xFF1C1B1A)),
        InkColorItem(1, "Weathered Slate", Color(0xFF566275)),
        InkColorItem(2, "Venetian Carmine", Color(0xFFC82020)),
        InkColorItem(3, "Indigo Dye", Color(0xFF2865E8)),
        InkColorItem(4, "Field Sage", Color(0xFF208F49)),
        InkColorItem(5, "Raw Copper", Color(0xFFC24615))
    )

    private val EspressoDarkInks = listOf(
        InkColorItem(0, "Crisp Silver", Color(0xFFEBECEF)),
        InkColorItem(1, "Fog Slate", Color(0xFFA8B0BE)),
        InkColorItem(2, "Carmine Rose", Color(0xFFFA7B7B)),
        InkColorItem(3, "Azure Indigo", Color(0xFF70AEFF)),
        InkColorItem(4, "Meadow Sage", Color(0xFF5CE292)),
        InkColorItem(5, "Desert Bronze", Color(0xFFFFA050))
    )

    private val EspressoHighlighters = listOf(
        hlItem(0, "Field Ochre", Color(0xFFF59E0B)),
        hlItem(1, "Alpine Glacier", Color(0xFF38BDF8)),
        hlItem(2, "Desert Clay", Color(0xFFFB7185))
    )

    // 3. Vintage Oxford Archivist Inks (Wet-Aware: Historic illuminated pigments with vivid dye undertones)
    private val OxfordLightInks = listOf(
        InkColorItem(0, "Iron Gall Noir", Color(0xFF321E36)),
        InkColorItem(1, "Antiquarian Grey", Color(0xFF657186)),
        InkColorItem(2, "Oxblood Port", Color(0xFFA81C3F)),
        InkColorItem(3, "Prussian Blue", Color(0xFF0C78B6)),
        InkColorItem(4, "Manuscript Forest", Color(0xFF138855)),
        InkColorItem(5, "Gilded Bronze", Color(0xFFBD8E36))
    )

    private val OxfordDarkInks = listOf(
        InkColorItem(0, "Parchment Pearl", Color(0xFFF0ECF7)),
        InkColorItem(1, "Lilac Grey", Color(0xFFB5C2FD)),
        InkColorItem(2, "Vintage Port", Color(0xFFF983C3)),
        InkColorItem(3, "Cyan Prussian", Color(0xFF7AEFFF)),
        InkColorItem(4, "Mint Forest", Color(0xFF4CE0A6)),
        InkColorItem(5, "Gilded Gold", Color(0xFFFCC83A))
    )

    private val OxfordHighlighters = listOf(
        hlItem(0, "Gold Leaf", Color(0xFFFFD700)),
        hlItem(1, "Illuminated Celadon", Color(0xFF6EE7B7)),
        hlItem(2, "Damask Pink", Color(0xFFFDA4AF))
    )

    // 4. Studio Clean Minimalist Inks
    private val StudioLightInks = listOf(
        InkColorItem(0, "Ink Black", Color(0xFF000000)),
        InkColorItem(1, "Slate", Color(0xFF475569)),
        InkColorItem(2, "Crimson", Color(0xFFDC2626)),
        InkColorItem(3, "Royal Blue", Color(0xFF0018C8)),
        InkColorItem(4, "Emerald", Color(0xFF059669)),
        InkColorItem(5, "Amber", Color(0xFFD97706))
    )

    private val StudioDarkInks = listOf(
        InkColorItem(0, "Pure White", Color(0xFFFFFFFF)),
        InkColorItem(1, "Slate Light", Color(0xFF94A3B8)),
        InkColorItem(2, "Crimson Light", Color(0xFFF87171)),
        InkColorItem(3, "Royal Blue Light", Color(0xFF60A5FA)),
        InkColorItem(4, "Emerald Light", Color(0xFF34D399)),
        InkColorItem(5, "Amber Light", Color(0xFFFBBF24))
    )

    private val StudioHighlighters = listOf(
        hlItem(0, "Canary Yellow", Color(0xFFFFEE33)),
        hlItem(1, "Mint Green", Color(0xFF5EEAD4)),
        hlItem(2, "Coral Pink", Color(0xFFFB7185))
    )

    fun getColors(preset: NotebookThemePreset = NotebookThemePreset.CLASSIC_SADDLE, isDark: Boolean = false): List<InkColorItem> {
        return when (preset) {
            NotebookThemePreset.CLASSIC_SADDLE -> if (isDark) SaddleDarkInks else SaddleLightInks
            NotebookThemePreset.TRAVELERS_ESPRESSO -> if (isDark) EspressoDarkInks else EspressoLightInks
            NotebookThemePreset.VINTAGE_OXFORD -> if (isDark) OxfordDarkInks else OxfordLightInks
            NotebookThemePreset.STUDIO_CLEAN -> if (isDark) StudioDarkInks else StudioLightInks
        }
    }

    fun getColors(isDark: Boolean): List<InkColorItem> = getColors(NotebookThemePreset.CLASSIC_SADDLE, isDark)

    fun getHighlighterColors(preset: NotebookThemePreset = NotebookThemePreset.CLASSIC_SADDLE, isDark: Boolean = false): List<InkColorItem> {
        return when (preset) {
            NotebookThemePreset.CLASSIC_SADDLE -> SaddleHighlighters
            NotebookThemePreset.TRAVELERS_ESPRESSO -> EspressoHighlighters
            NotebookThemePreset.VINTAGE_OXFORD -> OxfordHighlighters
            NotebookThemePreset.STUDIO_CLEAN -> StudioHighlighters
        }
    }

    fun getHighlighterColors(isDark: Boolean): List<InkColorItem> = getHighlighterColors(NotebookThemePreset.CLASSIC_SADDLE, isDark)

    /**
     * Converts a stroke's color when dark mode or preset changes.
     */
    fun mapColor(
        previousColorRgb: Long,
        paletteIndex: Int,
        oldIsDark: Boolean,
        newIsDark: Boolean,
        preset: NotebookThemePreset = NotebookThemePreset.CLASSIC_SADDLE
    ): Long {
        val palette = getColors(preset, newIsDark)
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
