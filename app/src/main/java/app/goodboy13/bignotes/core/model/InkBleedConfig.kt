package app.goodboy13.bignotes.core.model

import kotlin.math.roundToInt

/**
 * Live configuration parameters for fountain pen ink antialiasing, softening, and outward capillary bleed.
 * All spatial parameters are relative to the stroke's nominal radius.
 */
data class InkBleedConfig(
    val bleedRatio: Float = 0.72f,        // Relative bleed width: 72% of nominal radius
    val bleedPower: Float = 3.20f,        // Bleed falloff curve power: 3.2
    val coreShiftRatio: Float = -0.20f,   // Relative core boundary shift: -20% of nominal radius
    val bleedBaseOpacity: Float = 0.36f,  // Starting opacity at bleed origin: 0.36
    val aaWidth: Float = 0.55f,           // Screen-space anti-aliasing pixel band multiplier: 0.55px
    val paperGrainMix: Float = 0.60f,     // Paper tooth texture influence: 0.60
    val quadMarginRatio: Float = 0.72f,   // Extra tessellation polygon margin: 72% of nominal radius

    // Physical Wet-Ink Optics
    val inkDilution: Float = 0.20f,       // Single-dab pigment dilution (22% per dab -> 95% solid single line, 99.8% crossover)
    val fiberFeathering: Float = 0.60f,   // Capillary feathering into cellulose paper fibers
    val meniscusRim: Float = 0.38f,       // Surface tension / coffee-ring perimeter darkening
    val wetSheen: Float = 0.80f,          // Liquid wet specular sheen along centerline
    val chromaHalo: Float = 0.80f         // Chromatographic dye separation halo at fringe
) {
    fun toParamString(): String {
        val bleedPct = (bleedRatio * 100f).roundToInt()
        val shiftPct = (coreShiftRatio * 100f).roundToInt()
        val marginPct = (quadMarginRatio * 100f).roundToInt()
        val shiftSign = if (shiftPct >= 0) "+$shiftPct" else "$shiftPct"
        return "Bleed=${bleedPct}%, Power=${String.format("%.1f", bleedPower)}, Shift=${shiftSign}%, BaseOp=${String.format("%.2f", bleedBaseOpacity)}, AA=${String.format("%.2f", aaWidth)}px, Margin=${marginPct}%, Grain=${String.format("%.2f", paperGrainMix)}, Dilution=${String.format("%.2f", inkDilution)}, Feather=${String.format("%.2f", fiberFeathering)}, Rim=${String.format("%.2f", meniscusRim)}, Sheen=${String.format("%.2f", wetSheen)}, Halo=${String.format("%.2f", chromaHalo)}"
    }

    companion object {
        val DEFAULT = InkBleedConfig()

        val CRISP_AA = InkBleedConfig(
            bleedRatio = 0.0f,
            bleedPower = 2.0f,
            coreShiftRatio = 0.0f,
            bleedBaseOpacity = 1.0f,
            aaWidth = 1.25f,
            paperGrainMix = 0.0f,
            quadMarginRatio = 0.40f,
            inkDilution = 0.10f,
            fiberFeathering = 0.0f,
            meniscusRim = 0.0f,
            wetSheen = 0.0f,
            chromaHalo = 0.0f
        )

        val NATURAL_PEN = InkBleedConfig(
            bleedRatio = 0.98f,
            bleedPower = 3.9f,
            coreShiftRatio = -0.25f,
            bleedBaseOpacity = 0.37f,
            aaWidth = 0.50f,
            paperGrainMix = 0.46f,
            quadMarginRatio = 0.68f,
            inkDilution = 0.08f,
            fiberFeathering = 0.20f,
            meniscusRim = 0.20f,
            wetSheen = 0.25f,
            chromaHalo = 0.20f
        )

        val VELVET_BLEED = InkBleedConfig(
            bleedRatio = 0.45f,
            bleedPower = 1.8f,
            coreShiftRatio = 0.05f,
            bleedBaseOpacity = 0.95f,
            aaWidth = 1.5f,
            paperGrainMix = 0.20f,
            quadMarginRatio = 0.80f,
            inkDilution = 0.10f,
            fiberFeathering = 0.25f,
            meniscusRim = 0.30f,
            wetSheen = 0.35f,
            chromaHalo = 0.25f
        )

        val HEAVY_INK = InkBleedConfig(
            bleedRatio = 0.75f,
            bleedPower = 1.4f,
            coreShiftRatio = 0.10f,
            bleedBaseOpacity = 0.85f,
            aaWidth = 1.5f,
            paperGrainMix = 0.35f,
            quadMarginRatio = 1.00f,
            inkDilution = 0.12f,
            fiberFeathering = 0.40f,
            meniscusRim = 0.45f,
            wetSheen = 0.50f,
            chromaHalo = 0.40f
        )

        val WATERCOLOR_WASH = InkBleedConfig(
            bleedRatio = 1.20f,
            bleedPower = 1.0f,
            coreShiftRatio = -0.10f,
            bleedBaseOpacity = 0.60f,
            aaWidth = 2.0f,
            paperGrainMix = 0.40f,
            quadMarginRatio = 1.50f,
            inkDilution = 0.04f,
            fiberFeathering = 0.50f,
            meniscusRim = 0.50f,
            wetSheen = 0.20f,
            chromaHalo = 0.50f
        )
    }
}
