package com.antigrav.bignotes.core.model

import kotlin.math.roundToInt

/**
 * Live configuration parameters for fountain pen ink antialiasing, softening, and outward capillary bleed.
 * All spatial parameters are relative to the stroke's nominal radius.
 */
data class InkBleedConfig(
    val bleedRatio: Float = 0.98f,        // Relative bleed width: 98% of nominal radius
    val bleedPower: Float = 3.9f,         // Bleed falloff curve power: 3.9
    val coreShiftRatio: Float = -0.25f,   // Relative core boundary shift: -25% of nominal radius
    val bleedBaseOpacity: Float = 0.37f,  // Starting opacity at bleed origin: 0.37
    val aaWidth: Float = 0.50f,           // Screen-space anti-aliasing pixel band multiplier: 0.50px
    val paperGrainMix: Float = 0.46f,     // Paper tooth texture influence: 0.46
    val quadMarginRatio: Float = 0.68f    // Extra tessellation polygon margin: 68% of nominal radius
) {
    fun toParamString(): String {
        val bleedPct = (bleedRatio * 100f).roundToInt()
        val shiftPct = (coreShiftRatio * 100f).roundToInt()
        val marginPct = (quadMarginRatio * 100f).roundToInt()
        val shiftSign = if (shiftPct >= 0) "+$shiftPct" else "$shiftPct"
        return "Bleed=${bleedPct}%, Power=${String.format("%.1f", bleedPower)}, Shift=${shiftSign}%, BaseOp=${String.format("%.2f", bleedBaseOpacity)}, AA=${String.format("%.2f", aaWidth)}px, Margin=${marginPct}%, Grain=${String.format("%.2f", paperGrainMix)}"
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
            quadMarginRatio = 0.40f
        )

        val NATURAL_PEN = InkBleedConfig(
            bleedRatio = 0.98f,
            bleedPower = 3.9f,
            coreShiftRatio = -0.25f,
            bleedBaseOpacity = 0.37f,
            aaWidth = 0.50f,
            paperGrainMix = 0.46f,
            quadMarginRatio = 0.68f
        )

        val VELVET_BLEED = InkBleedConfig(
            bleedRatio = 0.45f,
            bleedPower = 1.8f,
            coreShiftRatio = 0.05f,
            bleedBaseOpacity = 0.95f,
            aaWidth = 1.5f,
            paperGrainMix = 0.20f,
            quadMarginRatio = 0.80f
        )

        val HEAVY_INK = InkBleedConfig(
            bleedRatio = 0.75f,
            bleedPower = 1.4f,
            coreShiftRatio = 0.10f,
            bleedBaseOpacity = 0.85f,
            aaWidth = 1.5f,
            paperGrainMix = 0.35f,
            quadMarginRatio = 1.00f
        )

        val WATERCOLOR_WASH = InkBleedConfig(
            bleedRatio = 1.20f,
            bleedPower = 1.0f,
            coreShiftRatio = -0.10f,
            bleedBaseOpacity = 0.60f,
            aaWidth = 2.0f,
            paperGrainMix = 0.40f,
            quadMarginRatio = 1.50f
        )
    }
}
