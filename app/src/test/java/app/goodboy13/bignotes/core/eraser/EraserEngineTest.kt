package app.goodboy13.bignotes.core.eraser

import app.goodboy13.bignotes.core.model.PencilTone
import app.goodboy13.bignotes.core.model.Stroke
import app.goodboy13.bignotes.core.model.StrokePoint
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class EraserEngineTest {

    private fun createHorizontalStroke(x1: Float, x2: Float, y: Float): Stroke {
        val pts = listOf(
            StrokePoint(x1, y, 0.5f, 1000L),
            StrokePoint(x2, y, 0.5f, 2000L)
        )
        return Stroke(
            id = "test_stroke",
            points = pts,
            tone = PencilTone.INK_BLACK,
            baseWidth = 4.0f,
            colorRgb = 0xFF000000L,
            paletteIndex = 0,
            isInk = true
        )
    }

    @Test
    fun testClipStrokeByCircle_splitsStrokeThroughCenter() {
        // Line from (0, 50) to (100, 50)
        // Eraser circle at (50, 50) with radius 10
        // Line enters circle at (40, 50) and exits at (60, 50)
        val stroke = createHorizontalStroke(0f, 100f, 50f)
        val result = EraserEngine.clipStrokeByCircle(stroke, 50f, 50f, 10f)

        assertEquals("Stroke through circle should be split into 2 sub-strokes", 2, result.size)

        val sub1 = result[0]
        val sub2 = result[1]

        // Sub-stroke 1 should be from 0 to 40
        assertEquals(2, sub1.points.size)
        assertEquals(0f, sub1.points[0].x, 0.01f)
        assertEquals(40f, sub1.points[1].x, 0.01f)
        assertEquals(50f, sub1.points[1].y, 0.01f)

        // Sub-stroke 2 should be from 60 to 100
        assertEquals(2, sub2.points.size)
        assertEquals(60f, sub2.points[0].x, 0.01f)
        assertEquals(50f, sub2.points[0].y, 0.01f)
        assertEquals(100f, sub2.points[1].x, 0.01f)

        // Attribute preservation
        assertEquals(stroke.tone, sub1.tone)
        assertEquals(stroke.baseWidth, sub1.baseWidth, 0.001f)
        assertEquals(stroke.isInk, sub1.isInk)
        assertEquals(stroke.colorRgb, sub2.colorRgb)
    }

    @Test
    fun testClipStrokeByCircle_entirelyOutside() {
        val stroke = createHorizontalStroke(0f, 20f, 50f)
        val result = EraserEngine.clipStrokeByCircle(stroke, 50f, 50f, 10f)

        assertEquals(1, result.size)
        assertEquals(0f, result[0].points[0].x, 0.01f)
        assertEquals(20f, result[0].points[1].x, 0.01f)
    }

    @Test
    fun testClipStrokeByCircle_entirelyInside() {
        val stroke = createHorizontalStroke(45f, 55f, 50f)
        val result = EraserEngine.clipStrokeByCircle(stroke, 50f, 50f, 10f)

        assertTrue("Stroke entirely inside circle must be erased completely", result.isEmpty())
    }

    @Test
    fun testClipStrokeByCircle_endingInside() {
        // Line from 0 to 50, circle at (50, 50) radius 10
        // Enters at 40
        val stroke = createHorizontalStroke(0f, 50f, 50f)
        val result = EraserEngine.clipStrokeByCircle(stroke, 50f, 50f, 10f)

        assertEquals(1, result.size)
        assertEquals(2, result[0].points.size)
        assertEquals(0f, result[0].points[0].x, 0.01f)
        assertEquals(40f, result[0].points[1].x, 0.01f)
    }

    @Test
    fun testClipStrokeByCircle_startingInside() {
        // Line from 50 to 100, circle at (50, 50) radius 10
        // Exits at 60
        val stroke = createHorizontalStroke(50f, 100f, 50f)
        val result = EraserEngine.clipStrokeByCircle(stroke, 50f, 50f, 10f)

        assertEquals(1, result.size)
        assertEquals(2, result[0].points.size)
        assertEquals(60f, result[0].points[0].x, 0.01f)
        assertEquals(100f, result[0].points[1].x, 0.01f)
    }

    @Test
    fun testDistSqPointToSegment() {
        // Segment (0, 0) to (10, 0)
        // Point (5, 5) -> closest point is (5, 0), distance = 5, distSq = 25
        val distSq = EraserEngine.distSqPointToSegment(5f, 5f, 0f, 0f, 10f, 0f)
        assertEquals(25f, distSq, 0.001f)
    }
}
