package app.goodboy13.bignotes.core.geometry

import app.goodboy13.bignotes.core.model.StrokePoint
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class ShapeRecognizerTest {

    @Test
    fun testRecognizeHorizontalLine_snapsToAngle() {
        val points = mutableListOf<StrokePoint>()
        // Draw slightly wavy horizontal line from (100, 200) to (500, 204)
        for (i in 0..40) {
            val t = i / 40.0f
            val x = 100f + 400f * t
            val noise = if (i == 0 || i == 40) 0f else if (i % 2 == 0) 1.5f else -1.5f
            val y = 200f + 4f * t + noise
            points.add(StrokePoint(x, y, 0.5f, 1000L + i * 10L))
        }


        val shape = ShapeRecognizer.recognize(points)
        assertNotNull("Must recognize line", shape)
        assertTrue("Must be RecognizedShape.Line", shape is RecognizedShape.Line)
        val line = shape as RecognizedShape.Line
        assertEquals(100f, line.startX, 0.01f)
        assertEquals(200f, line.startY, 0.01f)
        assertTrue("Must snap to horizontal angle", line.isSnappedToAngle)
        assertEquals(200f, line.endY, 0.01f) // Y snapped to horizontal!
    }

    @Test
    fun testRecognizeCircle() {
        val points = mutableListOf<StrokePoint>()
        val cx = 300f
        val cy = 400f
        val r = 80f
        // Trace a slightly noisy circle
        for (i in 0..60) {
            val theta = (2.0 * Math.PI * i) / 60.0
            val noise = if (i % 3 == 0) 2.0f else -1.5f
            val rad = r + noise
            val x = (cx + rad * cos(theta)).toFloat()
            val y = (cy + rad * sin(theta)).toFloat()
            points.add(StrokePoint(x, y, 0.5f, 1000L + i * 10L))
        }

        val shape = ShapeRecognizer.recognize(points)
        assertNotNull("Must recognize circle", shape)
        assertTrue("Must be RecognizedShape.Circle", shape is RecognizedShape.Circle)
        val circle = shape as RecognizedShape.Circle
        assertEquals(cx, circle.centerX, 5f)
        assertEquals(cy, circle.centerY, 5f)
        assertEquals(r, circle.radius, 4f)
    }

    @Test
    fun testRecognizeRectangleAndSquare() {
        val points = mutableListOf<StrokePoint>()
        // Draw square: (100,100) -> (200,100) -> (200,200) -> (100,200) -> (100,100)
        fun addSide(x1: Float, y1: Float, x2: Float, y2: Float, steps: Int) {
            for (i in 0 until steps) {
                val t = i / steps.toFloat()
                points.add(StrokePoint(x1 + (x2 - x1) * t, y1 + (y2 - y1) * t, 0.5f, 1000L))
            }
        }
        addSide(100f, 100f, 200f, 100f, 15)
        addSide(200f, 100f, 200f, 200f, 15)
        addSide(200f, 200f, 100f, 200f, 15)
        addSide(100f, 200f, 100f, 100f, 15)
        points.add(StrokePoint(100f, 100f, 0.5f, 1000L))

        val shape = ShapeRecognizer.recognize(points)
        assertNotNull("Must recognize rectangle", shape)
        assertTrue("Must be RecognizedShape.Rectangle", shape is RecognizedShape.Rectangle)
        val rect = shape as RecognizedShape.Rectangle
        assertTrue("Must be identified as square", rect.isSquare)
        assertEquals(100f, rect.p0X, 5f)
        assertEquals(100f, rect.p0Y, 5f)
        assertEquals(200f, rect.p2X, 5f)
        assertEquals(200f, rect.p2Y, 5f)
    }

    @Test
    fun testRecognizeTriangle() {
        val points = mutableListOf<StrokePoint>()
        // Draw triangle: (100, 300) -> (200, 100) -> (300, 300) -> (100, 300)
        fun addSide(x1: Float, y1: Float, x2: Float, y2: Float, steps: Int) {
            for (i in 0 until steps) {
                val t = i / steps.toFloat()
                points.add(StrokePoint(x1 + (x2 - x1) * t, y1 + (y2 - y1) * t, 0.5f, 1000L))
            }
        }
        addSide(100f, 300f, 200f, 100f, 20)
        addSide(200f, 100f, 300f, 300f, 20)
        addSide(300f, 300f, 100f, 300f, 20)
        points.add(StrokePoint(100f, 300f, 0.5f, 1000L))

        val shape = ShapeRecognizer.recognize(points)
        assertNotNull("Must recognize triangle", shape)
        assertTrue("Must be RecognizedShape.Triangle", shape is RecognizedShape.Triangle)
    }

    @Test
    fun testRecognizeArrow() {
        val points = mutableListOf<StrokePoint>()
        // Straight line from (100, 200) to (400, 200)
        for (i in 0..30) {
            val t = i / 30f
            points.add(StrokePoint(100f + 300f * t, 200f, 0.5f, 1000L + i * 10L))
        }
        // Barb folding back towards (370, 180)
        for (i in 1..8) {
            val t = i / 8f
            points.add(StrokePoint(400f - 30f * t, 200f - 20f * t, 0.5f, 1300L + i * 10L))
        }

        val shape = ShapeRecognizer.recognize(points)
        assertNotNull("Must recognize arrow", shape)
        assertTrue("Must be RecognizedShape.Arrow", shape is RecognizedShape.Arrow)
        val arrow = shape as RecognizedShape.Arrow
        assertEquals(100f, arrow.startX, 1f)
        assertEquals(400f, arrow.tipX, 5f)
    }

    @Test
    fun testRejectWavyHandwriting() {
        val points = mutableListOf<StrokePoint>()
        // Sine wave (e.g. cursive 'm' or squiggly handwriting)
        for (i in 0..50) {
            val x = i * 6f
            val y = (sin(i * 0.5) * 40.0).toFloat()
            points.add(StrokePoint(x, y, 0.5f, 1000L + i * 10L))
        }
        val shape = ShapeRecognizer.recognize(points)
        assertNull("Handwriting scribble must not be recognized as a geometric shape", shape)
    }

    @Test
    fun testAdjustWithDrag_circleScales() {
        val circle = RecognizedShape.Circle(100f, 100f, 50f)
        val adjusted = circle.adjustWithDrag(StrokePoint(100f, 200f, 0.5f, 0L)) as RecognizedShape.Circle
        assertEquals(100f, adjusted.centerX, 0.01f)
        assertEquals(100f, adjusted.centerY, 0.01f)
        assertEquals(100f, adjusted.radius, 0.01f) // Radius expanded from 50 to 100!
    }

    @Test
    fun testRecognizeRotatedRectangle() {
        val points = mutableListOf<StrokePoint>()
        // Draw rectangle rotated at 45 degrees (PI / 4)
        // Center: (200, 200), width: 120, height: 60
        val cx = 200f
        val cy = 200f
        val hw = 60f
        val hh = 30f
        val angle = (Math.PI / 4.0).toFloat()
        val cosA = cos(angle)
        val sinA = sin(angle)
        // Direction vectors
        val uX = cosA; val uY = sinA
        val vX = -sinA; val vY = cosA

        val p0X = cx - hw * uX - hh * vX
        val p0Y = cy - hw * uY - hh * vY
        val p1X = cx + hw * uX - hh * vX
        val p1Y = cy + hw * uY - hh * vY
        val p2X = cx + hw * uX + hh * vX
        val p2Y = cy + hw * uY + hh * vY
        val p3X = cx - hw * uX + hh * vX
        val p3Y = cy - hw * uY + hh * vY

        fun addSide(x1: Float, y1: Float, x2: Float, y2: Float, steps: Int) {
            for (i in 0 until steps) {
                val t = i / steps.toFloat()
                points.add(StrokePoint(x1 + (x2 - x1) * t, y1 + (y2 - y1) * t, 0.5f, 1000L))
            }
        }
        addSide(p0X, p0Y, p1X, p1Y, 20)
        addSide(p1X, p1Y, p2X, p2Y, 15)
        addSide(p2X, p2Y, p3X, p3Y, 20)
        addSide(p3X, p3Y, p0X, p0Y, 15)
        points.add(StrokePoint(p0X, p0Y, 0.5f, 1000L))

        val shape = ShapeRecognizer.recognize(points)
        assertNotNull("Must recognize rotated rectangle", shape)
        assertTrue("Must be RecognizedShape.Rectangle", shape is RecognizedShape.Rectangle)
        val rect = shape as RecognizedShape.Rectangle
        assertEquals(angle, rect.rotationRadians, 0.15f)
        assertEquals(cx, rect.centerX, 5f)
        assertEquals(cy, rect.centerY, 5f)
    }

    @Test
    fun testRectangleAdjustWithDrag_rotatesAndScales() {
        // Create an axis-aligned rectangle centered at (200, 200), width=100, height=60
        val rect = RecognizedShape.Rectangle(
            p0X = 150f, p0Y = 170f,
            p1X = 250f, p1Y = 170f,
            p2X = 250f, p2Y = 230f,
            p3X = 150f, p3Y = 230f,
            isSquare = false,
            rotationRadians = 0f,
            baseWidth = 100f,
            baseHeight = 60f,
            centerX = 200f,
            centerY = 200f
        )

        // First drag point sets reference: stylus at (250, 200) -> angle 0 rad, dist = 50
        val drag1 = rect.adjustWithDrag(StrokePoint(250f, 200f, 0.5f, 0L)) as RecognizedShape.Rectangle
        assertEquals(0f, drag1.rotationRadians, 0.05f)

        // Drag to (200, 250) -> angle +PI/2 rad, dist = 50. Rectangle rotates 90 degrees!
        val drag2 = drag1.adjustWithDrag(StrokePoint(200f, 250f, 0.5f, 10L)) as RecognizedShape.Rectangle
        val expectedAngle = (Math.PI / 2.0).toFloat()
        assertEquals(expectedAngle, drag2.rotationRadians, 0.05f)
        assertEquals(200f, drag2.centerX, 0.01f)
        assertEquals(200f, drag2.centerY, 0.01f)
    }
}

