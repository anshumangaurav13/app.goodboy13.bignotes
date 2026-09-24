package com.antigrav.bignotes.core.math.spatial

import com.antigrav.bignotes.core.math.spatial.detector.FractionDetector
import com.antigrav.bignotes.core.model.PencilTone
import com.antigrav.bignotes.core.model.Stroke
import com.antigrav.bignotes.core.model.StrokePoint
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class FractionDetectorTest {

    private fun makeLineStroke(x1: Float, y1: Float, x2: Float, y2: Float, width: Float = 3f): Stroke {
        val pts = listOf(
            StrokePoint(x1, y1, 0.5f, 1000L),
            StrokePoint((x1 + x2) * 0.5f, (y1 + y2) * 0.5f, 0.5f, 1010L),
            StrokePoint(x2, y2, 0.5f, 1020L)
        )
        return Stroke(
            id = UUID.randomUUID().toString(),
            points = pts,
            tone = PencilTone.INK_BLACK,
            baseWidth = width
        )
    }

    @Test
    fun testDetectVerticalFraction() {
        // Numerator: '10' at X=[110..140], Y=[150..190]
        val numStroke = makeLineStroke(110f, 150f, 140f, 190f)

        // Fraction bar: horizontal line from X=100 to X=160 at Y=200
        val barStroke = makeLineStroke(100f, 200f, 160f, 201f)

        // Denominator: '2' at X=[115..135], Y=[215..250]
        val denStroke = makeLineStroke(115f, 215f, 135f, 250f)

        val strokes = listOf(numStroke, barStroke, denStroke)
        val fraction = FractionDetector.detectFraction(strokes)

        assertNotNull("Fraction must be detected", fraction)
        assertEquals(barStroke.id, fraction!!.barStroke.id)
        assertEquals(1, fraction.numeratorStrokes.size)
        assertEquals(numStroke.id, fraction.numeratorStrokes[0].id)
        assertEquals(1, fraction.denominatorStrokes.size)
        assertEquals(denStroke.id, fraction.denominatorStrokes[0].id)
        assertTrue(fraction.prefixStrokes.isEmpty())
        assertTrue(fraction.suffixStrokes.isEmpty())
    }

    @Test
    fun testFractionWithPrefixAndSuffix() {
        // Prefix: '5 +' at X=[20..70], Y=[180..220]
        val prefixStroke = makeLineStroke(20f, 180f, 70f, 220f)

        // Numerator: '12' at X=[110..150], Y=[140..185]
        val numStroke = makeLineStroke(110f, 140f, 150f, 185f)

        // Fraction bar: at X=[100..160], Y=200
        val barStroke = makeLineStroke(100f, 200f, 160f, 201f)

        // Denominator: '4' at X=[115..145], Y=[215..260]
        val denStroke = makeLineStroke(115f, 215f, 145f, 260f)

        // Suffix: '=' at X=[190..230], Y=[195..205]
        val suffixStroke = makeLineStroke(190f, 195f, 230f, 205f)

        val strokes = listOf(prefixStroke, numStroke, barStroke, denStroke, suffixStroke)
        val fraction = FractionDetector.detectFraction(strokes)

        assertNotNull(fraction)
        assertEquals(1, fraction!!.prefixStrokes.size)
        assertEquals(prefixStroke.id, fraction.prefixStrokes[0].id)
        assertEquals(1, fraction.suffixStrokes.size)
        assertEquals(suffixStroke.id, fraction.suffixStrokes[0].id)
    }

    @Test
    fun testMinusSignIsNotDetectedAsFraction() {
        // Expression: '10 - 2' all on the same baseline Y=[180..220]
        val tenStroke = makeLineStroke(50f, 180f, 80f, 220f)
        val minusStroke = makeLineStroke(95f, 200f, 125f, 201f) // horizontal bar
        val twoStroke = makeLineStroke(140f, 180f, 170f, 220f)

        val strokes = listOf(tenStroke, minusStroke, twoStroke)
        val fraction = FractionDetector.detectFraction(strokes)

        assertNull("Minus sign on baseline should NOT be detected as fraction bar", fraction)
    }

    @Test
    fun testEqualSignIsNotDetectedAsFraction() {
        // Equal sign: two parallel bars at Y=200 and Y=212
        val eq1 = makeLineStroke(100f, 200f, 140f, 201f)
        val eq2 = makeLineStroke(100f, 212f, 140f, 213f)
        val num = makeLineStroke(50f, 180f, 80f, 220f)

        val strokes = listOf(num, eq1, eq2)
        val fraction = FractionDetector.detectFraction(strokes)

        assertNull("Equal sign bars must not trigger fraction detection", fraction)
    }

    @Test
    fun testPlusSignIsNotDetectedAsFraction() {
        // Plus sign: horizontal line crossed by a vertical line
        val plusHoriz = makeLineStroke(100f, 200f, 130f, 201f)
        val plusVert = makeLineStroke(115f, 185f, 115f, 215f)
        val num = makeLineStroke(50f, 180f, 80f, 220f)
        val num2 = makeLineStroke(150f, 180f, 180f, 220f)

        val strokes = listOf(num, plusHoriz, plusVert, num2)
        val fraction = FractionDetector.detectFraction(strokes)

        assertNull("Plus sign must never be detected as a fraction bar", fraction)
    }
}
