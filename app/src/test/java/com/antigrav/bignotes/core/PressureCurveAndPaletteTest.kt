package com.antigrav.bignotes.core

import com.antigrav.bignotes.core.model.BezierControlPoints
import com.antigrav.bignotes.core.model.PressureCurveDefaults
import com.antigrav.bignotes.core.model.PressureCurveType
import com.antigrav.bignotes.ui.theme.AppThemePreset
import com.antigrav.bignotes.ui.theme.InkPalette
import org.junit.Assert.*
import org.junit.Test

class PressureCurveAndPaletteTest {

    @Test
    fun testPressureCurveDefault_matchesScreenshotGeometry() {
        val standard = PressureCurveDefaults.STANDARD
        assertEquals(0.85f, standard.cp1x, 0.001f)
        assertEquals(0.52f, standard.cp1y, 0.001f)
        assertEquals(0.58f, standard.cp2x, 0.001f)
        assertEquals(0.77f, standard.cp2y, 0.001f)

        val defaultBezier = BezierControlPoints()
        assertEquals(0.85f, defaultBezier.cp1x, 0.001f)
        assertEquals(0.52f, defaultBezier.cp1y, 0.001f)
        assertEquals(0.58f, defaultBezier.cp2x, 0.001f)
        assertEquals(0.77f, defaultBezier.cp2y, 0.001f)

        // Monotonicity check across sample domain [0, 1]
        var lastY = -1.0f
        for (i in 0..100) {
            val x = i / 100.0f
            val y = standard.solveY(x)
            assertTrue("Curve must be strictly monotonic non-decreasing at x=", y >= lastY)
            assertTrue("Output must be bounded [0, 1]", y in 0.0f..1.0f)
            lastY = y
        }

        // Endpoint verification
        assertEquals(0.0f, standard.solveY(0.0f), 0.001f)
        assertEquals(1.0f, standard.solveY(1.0f), 0.001f)
    }

    @Test
    fun testInkPaletteDefault_royalBlueAndCohesivePalette() {
        // Canonical colors (used for both light and inverted dark mode via GL shader)
        val colors = InkPalette.getColors(AppThemePreset.DEFAULT)
        assertEquals(6, colors.size)
        // Slot 3 is Royal Blue (#1D4ED8)
        assertEquals(0xFF1D4ED8L, colors[3].colorRgb)
        assertEquals("Royal Blue", colors[3].name)

        // Highlighter palette: 3 curated colors
        val hlColors = InkPalette.getHighlighterColors(AppThemePreset.DEFAULT)
        assertEquals(3, hlColors.size)
        assertEquals("Canary Yellow", hlColors[0].name)
        assertEquals("Mint Green", hlColors[1].name)
        assertEquals("Coral Pink", hlColors[2].name)
    }

    @Test
    fun testVelocityCurveDefaults() {
        val calligraphic = com.antigrav.bignotes.core.model.VelocityCurveDefaults.CALLIGRAPHIC
        assertEquals(0.45f, calligraphic.cp1x, 0.001f)
        assertEquals(0.15f, calligraphic.cp1y, 0.001f)
        assertEquals(0.35f, calligraphic.cp2x, 0.001f)
        assertEquals(0.90f, calligraphic.cp2y, 0.001f)

        // Endpoints
        assertEquals(0.0f, calligraphic.solveY(0.0f), 0.001f)
        assertEquals(1.0f, calligraphic.solveY(1.0f), 0.001f)

        val subtle = com.antigrav.bignotes.core.model.VelocityCurveDefaults.SUBTLE
        assertEquals(0.0f, subtle.solveY(0.0f), 0.001f)
        assertEquals(1.0f, subtle.solveY(1.0f), 0.001f)
    }
}
