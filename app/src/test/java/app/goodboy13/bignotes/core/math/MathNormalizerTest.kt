package app.goodboy13.bignotes.core.math

import app.goodboy13.bignotes.core.math.normalizer.MathNormalizer
import org.junit.Assert.*
import org.junit.Test

class MathNormalizerTest {

    @Test
    fun testTrailingEqualsAndQuestionMarks() {
        assertEquals("12 + 4", MathNormalizer.normalize("12 + 4 ="))
        assertEquals("12 + 4", MathNormalizer.normalize("12 + 4 = ="))
        assertEquals("12 + 4", MathNormalizer.normalize(" 12 + 4 =? "))
    }

    @Test
    fun testUnicodeOperators() {
        assertEquals("12 * 4", MathNormalizer.normalize("12 × 4"))
        assertEquals("20 / 5", MathNormalizer.normalize("20 ÷ 5"))
        assertEquals("10 - 3", MathNormalizer.normalize("10 − 3"))
        assertEquals("6 * 7", MathNormalizer.normalize("6 · 7"))
    }

    @Test
    fun testLetterXAsMultiplication() {
        assertEquals("12 * 4", MathNormalizer.normalize("12 x 4"))
        assertEquals("12*4", MathNormalizer.normalize("12x4"))
        assertEquals("15 * 3", MathNormalizer.normalize("15 X 3"))
        assertEquals("(2+3) * 5", MathNormalizer.normalize("(2+3) x 5"))
    }

    @Test
    fun testImplicitMultiplication() {
        assertEquals("2*(3+4)", MathNormalizer.normalize("2(3+4)"))
        assertEquals("(2+3)*(4+5)", MathNormalizer.normalize("(2+3)(4+5)"))
        assertEquals("5*pi", MathNormalizer.normalize("5pi"))
        assertEquals("5*pi", MathNormalizer.normalize("5π"))
    }

    @Test
    fun testSquareRootAndPercentages() {
        assertEquals("sqrt(16)", MathNormalizer.normalize("√16"))
        assertEquals("sqrt(144)", MathNormalizer.normalize("sqrt 144"))
        assertEquals("(50/100)", MathNormalizer.normalize("50%"))
    }

    @Test
    fun testThousandSeparatorsAndDecimalCommas() {
        assertEquals("1000 + 500", MathNormalizer.normalize("1,000 + 500"))
        assertEquals("3.5 * 2", MathNormalizer.normalize("3,5 * 2"))
    }

    @Test
    fun testPlusSignDisambiguation() {
        assertEquals("12 + 4", MathNormalizer.normalize("12 t 4"))
        assertEquals("12 + 4", MathNormalizer.normalize("12 T 4"))
        assertEquals("10 + 2", MathNormalizer.normalize("10t2"))
        assertEquals("15 + 3", MathNormalizer.normalize("15 plus 3"))
        assertEquals("8 + 2", MathNormalizer.normalize("8 † 2"))
        assertEquals("7 + 1", MathNormalizer.normalize("7 ＋ 1"))
    }
}
