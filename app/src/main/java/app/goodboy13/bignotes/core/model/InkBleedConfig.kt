package app.goodboy13.bignotes.core.model

import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * Live configuration parameters for fountain pen ink antialiasing, softening, and outward capillary bleed.
 * All spatial parameters are relative to the stroke's nominal radius.
 */
data class InkBleedConfig(
    val bleedRatio: Float = 0.98f,        // Relative bleed width: 98% of nominal radius
    val bleedPower: Float = 1.90f,        // Bleed falloff curve power: 1.90
    val coreShiftRatio: Float = -0.25f,   // Relative core boundary shift: -25% of nominal radius
    val bleedBaseOpacity: Float = 0.66f,  // Starting opacity at bleed origin: 0.66
    val aaWidth: Float = 0.50f,           // Screen-space anti-aliasing pixel band multiplier: 0.50px
    val paperGrainMix: Float = 0.46f,     // Paper tooth texture influence: 0.46
    val quadMarginRatio: Float = 0.68f,   // Extra tessellation polygon margin: 68% of nominal radius

    // Physical Wet-Ink Optics
    val inkDilution: Float = 0.20f,       // Single-dab pigment dilution
    val fiberFeathering: Float = 0.46f,   // Capillary feathering into cellulose paper fibers
    val meniscusRim: Float = 0.20f,       // Surface tension / coffee-ring perimeter darkening
    val wetSheen: Float = 0.48f,          // Reserved for specular sheen (inactive in subtractive ink shader)
    val chromaHalo: Float = 0.22f         // Chromatographic dye separation halo at fringe
) {
    fun clamped(): InkBleedConfig = copy(
        bleedRatio = bleedRatio.coerceIn(0.0f, 1.50f),
        bleedPower = bleedPower.coerceIn(0.50f, 4.50f),
        coreShiftRatio = coreShiftRatio.coerceIn(-0.50f, 0.50f),
        bleedBaseOpacity = bleedBaseOpacity.coerceIn(0.0f, 1.0f),
        aaWidth = aaWidth.coerceIn(0.10f, 2.50f),
        paperGrainMix = paperGrainMix.coerceIn(0.0f, 1.0f),
        quadMarginRatio = quadMarginRatio.coerceIn(0.20f, 1.60f),
        inkDilution = inkDilution.coerceIn(0.02f, 1.0f),
        fiberFeathering = fiberFeathering.coerceIn(0.0f, 1.0f),
        meniscusRim = meniscusRim.coerceIn(0.0f, 1.0f),
        wetSheen = wetSheen.coerceIn(0.0f, 1.0f),
        chromaHalo = chromaHalo.coerceIn(0.0f, 1.0f)
    )

    fun toJsonString(): String = JSONObject().apply {
        put("bleedRatio", bleedRatio.toDouble())
        put("bleedPower", bleedPower.toDouble())
        put("coreShiftRatio", coreShiftRatio.toDouble())
        put("bleedBaseOpacity", bleedBaseOpacity.toDouble())
        put("aaWidth", aaWidth.toDouble())
        put("paperGrainMix", paperGrainMix.toDouble())
        put("quadMarginRatio", quadMarginRatio.toDouble())
        put("inkDilution", inkDilution.toDouble())
        put("fiberFeathering", fiberFeathering.toDouble())
        put("meniscusRim", meniscusRim.toDouble())
        put("wetSheen", wetSheen.toDouble())
        put("chromaHalo", chromaHalo.toDouble())
    }.toString()

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

        val PRESETS: List<Pair<String, InkBleedConfig>> = listOf(
            "Default" to DEFAULT,
            "Crisp AA" to CRISP_AA,
            "Natural" to NATURAL_PEN,
            "Velvet" to VELVET_BLEED,
            "Heavy" to HEAVY_INK,
            "Wash" to WATERCOLOR_WASH
        )

        fun fromJsonString(raw: String?): InkBleedConfig {
            if (raw.isNullOrBlank()) return DEFAULT
            return try {
                val obj = JSONObject(raw)
                InkBleedConfig(
                    bleedRatio = obj.optDouble("bleedRatio", DEFAULT.bleedRatio.toDouble()).toFloat(),
                    bleedPower = obj.optDouble("bleedPower", DEFAULT.bleedPower.toDouble()).toFloat(),
                    coreShiftRatio = obj.optDouble("coreShiftRatio", DEFAULT.coreShiftRatio.toDouble()).toFloat(),
                    bleedBaseOpacity = obj.optDouble("bleedBaseOpacity", DEFAULT.bleedBaseOpacity.toDouble()).toFloat(),
                    aaWidth = obj.optDouble("aaWidth", DEFAULT.aaWidth.toDouble()).toFloat(),
                    paperGrainMix = obj.optDouble("paperGrainMix", DEFAULT.paperGrainMix.toDouble()).toFloat(),
                    quadMarginRatio = obj.optDouble("quadMarginRatio", DEFAULT.quadMarginRatio.toDouble()).toFloat(),
                    inkDilution = obj.optDouble("inkDilution", DEFAULT.inkDilution.toDouble()).toFloat(),
                    fiberFeathering = obj.optDouble("fiberFeathering", DEFAULT.fiberFeathering.toDouble()).toFloat(),
                    meniscusRim = obj.optDouble("meniscusRim", DEFAULT.meniscusRim.toDouble()).toFloat(),
                    wetSheen = obj.optDouble("wetSheen", DEFAULT.wetSheen.toDouble()).toFloat(),
                    chromaHalo = obj.optDouble("chromaHalo", DEFAULT.chromaHalo.toDouble()).toFloat()
                ).clamped()
            } catch (_: Exception) {
                DEFAULT
            }
        }
    }
}
