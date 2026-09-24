package com.antigrav.bignotes.core.math.spatial

import com.antigrav.bignotes.core.math.spatial.detector.ExponentDetector
import com.antigrav.bignotes.core.model.PencilTone
import com.antigrav.bignotes.core.model.Stroke
import com.antigrav.bignotes.core.model.StrokePoint
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class ExponentDetectorTest {

    private fun makeLineStroke(x1: Float, y1: Float, x2: Float, y2: Float): Stroke {
        val pts = listOf(
            StrokePoint(x1, y1, 0.5f, 1000L),
            StrokePoint((x1 + x2) * 0.5f, (y1 + y2) * 0.5f, 0.5f, 1010L),
            StrokePoint(x2, y2, 0.5f, 1020L)
        )
        return Stroke(
            id = UUID.randomUUID().toString(),
            points = pts,
            tone = PencilTone.INK_BLACK,
            baseWidth = 3f
        )
    }

    @Test
    fun testDetectExponentPair() {
        // Base '2': X=[100..130], Y=[200..250] (height = 50, centerY = 225)
        val baseStroke = makeLineStroke(100f, 200f, 130f, 250f)

        // Exponent '3': X=[135..155], Y=[185..215] (height = 30, centerY = 200)
        val expStroke = makeLineStroke(135f, 185f, 155f, 215f)

        val segments = ExponentDetector.segmentExponents(listOf(baseStroke, expStroke))

        assertEquals(1, segments.size)
        assertTrue(segments[0] is ExponentDetector.MathSegment.Power)

        val power = segments[0] as ExponentDetector.MathSegment.Power
        assertEquals(1, power.baseStrokes.size)
        assertEquals(baseStroke.id, power.baseStrokes[0].id)
        assertEquals(1, power.exponentStrokes.size)
        assertEquals(expStroke.id, power.exponentStrokes[0].id)
    }

    @Test
    fun testFlatDigitsAreNotDetectedAsExponent() {
        // '23': Both on baseline Y=[200..250] with same height
        val two = makeLineStroke(100f, 200f, 130f, 250f)
        val three = makeLineStroke(145f, 200f, 175f, 250f)

        val segments = ExponentDetector.segmentExponents(listOf(two, three))

        assertEquals(1, segments.size)
        assertTrue("Flat numbers should be a single Normal segment", segments[0] is ExponentDetector.MathSegment.Normal)
    }

    @Test
    fun testMultiCharacterExponent() {
        // Base '2': X=[100..130], Y=[200..250]
        val base = makeLineStroke(100f, 200f, 130f, 250f)

        // Exponent '1': X=[135..150], Y=[185..215]
        val exp1 = makeLineStroke(135f, 185f, 150f, 215f)
        // Exponent '0': X=[155..170], Y=[185..215]
        val exp2 = makeLineStroke(155f, 185f, 170f, 215f)

        val segments = ExponentDetector.segmentExponents(listOf(base, exp1, exp2))

        assertEquals(1, segments.size)
        assertTrue(segments[0] is ExponentDetector.MathSegment.Power)

        val power = segments[0] as ExponentDetector.MathSegment.Power
        assertEquals(1, power.baseStrokes.size)
        assertEquals(2, power.exponentStrokes.size)
    }

    @Test
    fun testPlusSignIsNotDetectedAsExponent() {
        // '2 + 3': '2' at Y=[200..250], '+' at Y=[215..235] (centered), '3' at Y=[200..250]
        val base2 = makeLineStroke(100f, 200f, 130f, 250f)
        val plusHoriz = makeLineStroke(140f, 225f, 160f, 226f)
        val plusVert = makeLineStroke(150f, 215f, 150f, 235f)
        val base3 = makeLineStroke(170f, 200f, 200f, 250f)

        val segments = ExponentDetector.segmentExponents(listOf(base2, plusHoriz, plusVert, base3))

        // Must not contain any Power segments
        val hasPower = segments.any { it is ExponentDetector.MathSegment.Power }
        assertFalse("Centered plus sign must NOT be detected as an exponent", hasPower)
    }
}
