package app.goodboy13.bignotes.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class InkBleedConfigTest {

    @Test
    fun `json round trip preserves all presets`() {
        for ((name, preset) in InkBleedConfig.PRESETS) {
            val encoded = preset.toJsonString()
            val decoded = InkBleedConfig.fromJsonString(encoded)
            assertEquals("Preset $name failed JSON round-trip", preset.clamped(), decoded)
        }
    }

    @Test
    fun `invalid or empty json falls back cleanly to default`() {
        assertEquals(InkBleedConfig.DEFAULT, InkBleedConfig.fromJsonString(null))
        assertEquals(InkBleedConfig.DEFAULT, InkBleedConfig.fromJsonString(""))
        assertEquals(InkBleedConfig.DEFAULT, InkBleedConfig.fromJsonString("{not valid json"))
    }

    @Test
    fun `clamped constrains out-of-range values to valid shader bounds`() {
        val extreme = InkBleedConfig(
            bleedRatio = 9.0f,
            bleedPower = -3.0f,
            coreShiftRatio = -2.0f,
            bleedBaseOpacity = 2.0f,
            aaWidth = 0.0f,
            paperGrainMix = -1.0f,
            quadMarginRatio = 5.0f,
            inkDilution = 0.0f,
            fiberFeathering = 2.0f,
            meniscusRim = -0.5f,
            wetSheen = 4.0f,
            chromaHalo = -0.2f
        ).clamped()

        assertEquals(1.50f, extreme.bleedRatio, 1e-5f)
        assertEquals(0.50f, extreme.bleedPower, 1e-5f)
        assertEquals(-0.50f, extreme.coreShiftRatio, 1e-5f)
        assertEquals(1.00f, extreme.bleedBaseOpacity, 1e-5f)
        assertEquals(0.10f, extreme.aaWidth, 1e-5f)
        assertEquals(0.00f, extreme.paperGrainMix, 1e-5f)
        assertEquals(1.60f, extreme.quadMarginRatio, 1e-5f)
        assertEquals(0.02f, extreme.inkDilution, 1e-5f)
        assertEquals(1.00f, extreme.fiberFeathering, 1e-5f)
        assertEquals(0.00f, extreme.meniscusRim, 1e-5f)
        assertEquals(1.00f, extreme.wetSheen, 1e-5f)
        assertEquals(0.00f, extreme.chromaHalo, 1e-5f)
    }
}
