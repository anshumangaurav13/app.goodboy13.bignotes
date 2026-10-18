package app.goodboy13.bignotes.core.math.spatial

import app.goodboy13.bignotes.core.math.evaluator.MathEvaluator
import app.goodboy13.bignotes.core.math.model.MathResult
import app.goodboy13.bignotes.core.math.normalizer.MathNormalizer
import app.goodboy13.bignotes.core.model.PencilTone
import app.goodboy13.bignotes.core.model.Stroke
import app.goodboy13.bignotes.core.model.StrokePoint
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class SpatialMathParserTest {

    private fun makeLine(x1: Float, y1: Float, x2: Float, y2: Float): Stroke {
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
    fun testParseFractionExpression() = runBlocking {
        val num = makeLine(110f, 150f, 140f, 190f)
        val bar = makeLine(100f, 200f, 160f, 201f)
        val den = makeLine(115f, 215f, 135f, 250f)

        val result = SpatialMathParser.parseAndRecognize(listOf(num, bar, den)) { strokes ->
            when (strokes[0].id) {
                num.id -> "10 + 2"
                den.id -> "3"
                else -> "unknown"
            }
        }

        assertEquals("((10 + 2) / (3))", result)
        val normalized = MathNormalizer.normalize(result)
        val evaluated = MathEvaluator.evaluate(normalized)
        assertTrue(evaluated is MathResult.Success)
        assertEquals(4.0, (evaluated as MathResult.Success).value, 1e-9)
    }

    @Test
    fun testParseExponentExpression() = runBlocking {
        val base = makeLine(100f, 200f, 130f, 250f)
        val exp = makeLine(135f, 185f, 155f, 215f)

        val result = SpatialMathParser.parseAndRecognize(listOf(base, exp)) { strokes ->
            when (strokes[0].id) {
                base.id -> "2"
                exp.id -> "3"
                else -> "unknown"
            }
        }

        assertEquals("(2)^(3)", result)
        val normalized = MathNormalizer.normalize(result)
        val evaluated = MathEvaluator.evaluate(normalized)
        assertTrue(evaluated is MathResult.Success)
        assertEquals(8.0, (evaluated as MathResult.Success).value, 1e-9)
    }
}
