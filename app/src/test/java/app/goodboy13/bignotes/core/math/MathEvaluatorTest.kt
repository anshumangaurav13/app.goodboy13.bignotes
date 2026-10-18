package app.goodboy13.bignotes.core.math

import app.goodboy13.bignotes.core.math.evaluator.MathEvaluator
import app.goodboy13.bignotes.core.math.model.MathResult
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sqrt

class MathEvaluatorTest {

    @Test
    fun testBasicArithmetic() {
        val r1 = MathEvaluator.evaluate("2 + 3")
        assertTrue(r1 is MathResult.Success)
        assertEquals(5.0, (r1 as MathResult.Success).value, 1e-9)
        assertEquals("5", r1.formattedResult)

        val r2 = MathEvaluator.evaluate("10 - 4.5")
        assertTrue(r2 is MathResult.Success)
        assertEquals(5.5, (r2 as MathResult.Success).value, 1e-9)
        assertEquals("5.5", r2.formattedResult)

        val r3 = MathEvaluator.evaluate("6 * 7")
        assertTrue(r3 is MathResult.Success)
        assertEquals(42.0, (r3 as MathResult.Success).value, 1e-9)
        assertEquals("42", r3.formattedResult)

        val r4 = MathEvaluator.evaluate("15 / 4")
        assertTrue(r4 is MathResult.Success)
        assertEquals(3.75, (r4 as MathResult.Success).value, 1e-9)
        assertEquals("3.75", r4.formattedResult)
    }

    @Test
    fun testOperatorPrecedenceAndParentheses() {
        val r1 = MathEvaluator.evaluate("2 + 3 * 4")
        assertTrue(r1 is MathResult.Success)
        assertEquals(14.0, (r1 as MathResult.Success).value, 1e-9)

        val r2 = MathEvaluator.evaluate("(2 + 3) * 4")
        assertTrue(r2 is MathResult.Success)
        assertEquals(20.0, (r2 as MathResult.Success).value, 1e-9)

        val r3 = MathEvaluator.evaluate("100 / (2 * 5) + 1")
        assertTrue(r3 is MathResult.Success)
        assertEquals(11.0, (r3 as MathResult.Success).value, 1e-9)
    }

    @Test
    fun testPowersAndNegativeNumbers() {
        val r1 = MathEvaluator.evaluate("2 ^ 3")
        assertTrue(r1 is MathResult.Success)
        assertEquals(8.0, (r1 as MathResult.Success).value, 1e-9)

        val r2 = MathEvaluator.evaluate("-5 + 12")
        assertTrue(r2 is MathResult.Success)
        assertEquals(7.0, (r2 as MathResult.Success).value, 1e-9)

        val r3 = MathEvaluator.evaluate("4 * -2")
        assertTrue(r3 is MathResult.Success)
        assertEquals(-8.0, (r3 as MathResult.Success).value, 1e-9)
    }

    @Test
    fun testScientificFunctionsAndConstants() {
        val r1 = MathEvaluator.evaluate("sqrt(144)")
        assertTrue(r1 is MathResult.Success)
        assertEquals(12.0, (r1 as MathResult.Success).value, 1e-9)

        val r2 = MathEvaluator.evaluate("sin(0)")
        assertTrue(r2 is MathResult.Success)
        assertEquals(0.0, (r2 as MathResult.Success).value, 1e-9)

        val r3 = MathEvaluator.evaluate("cos(0) + pi")
        assertTrue(r3 is MathResult.Success)
        assertEquals(1.0 + PI, (r3 as MathResult.Success).value, 1e-9)

        val r4 = MathEvaluator.evaluate("abs(-42)")
        assertTrue(r4 is MathResult.Success)
        assertEquals(42.0, (r4 as MathResult.Success).value, 1e-9)
    }

    @Test
    fun testDivisionByZeroAndErrors() {
        val r1 = MathEvaluator.evaluate("10 / 0")
        assertTrue(r1 is MathResult.Error)

        val r2 = MathEvaluator.evaluate("(2 + 3")
        assertTrue(r2 is MathResult.Error)

        val r3 = MathEvaluator.evaluate("")
        assertTrue(r3 is MathResult.Error)
    }
}
