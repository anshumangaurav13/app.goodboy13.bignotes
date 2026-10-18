package app.goodboy13.bignotes.core.math

import app.goodboy13.bignotes.core.math.font.VectorStrokeFont
import app.goodboy13.bignotes.core.model.PencilTone
import org.junit.Assert.*
import org.junit.Test

class VectorStrokeFontTest {

    @Test
    fun testGenerateStrokesForDigits() {
        val strokes = VectorStrokeFont.generateStrokesForText(
            text = "120",
            startX = 100f,
            centerY = 200f,
            targetHeight = 40f,
            baseWidth = 3f,
            tone = PencilTone.INK_BLACK
        )

        assertFalse("Strokes should not be empty", strokes.isEmpty())
        for (stroke in strokes) {
            assertTrue("Each stroke must have points", stroke.points.size >= 2)
            assertEquals(3f, stroke.baseWidth, 1e-3f)
            assertEquals(PencilTone.INK_BLACK, stroke.tone)
            // Stroke should be positioned roughly around Y=200 with height 40
            assertTrue(stroke.minY >= 160f)
            assertTrue(stroke.maxY <= 240f)
        }
    }

    @Test
    fun testGenerateStrokesForNegativeDecimal() {
        val strokes = VectorStrokeFont.generateStrokesForText(
            text = "-42.5",
            startX = 50f,
            centerY = 150f,
            targetHeight = 30f
        )

        assertTrue(strokes.isNotEmpty())
        assertTrue("X coordinates should advance to the right", strokes.last().maxX > strokes.first().minX)
    }

    @Test
    fun testEmptyOrBlankText() {
        val strokes = VectorStrokeFont.generateStrokesForText(
            text = "   ",
            startX = 0f,
            centerY = 0f,
            targetHeight = 30f
        )
        assertTrue(strokes.isEmpty())
    }
}
