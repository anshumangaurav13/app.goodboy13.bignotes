package app.goodboy13.bignotes.core.geometry

import app.goodboy13.bignotes.core.model.StrokeStabilizationConfig
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.hypot
import kotlin.math.sqrt

class StrokeStabilizerTest {

    @Test
    fun testHighFrequencyJitterSuppression() {
        val stabilizer = StrokeStabilizer(StrokeStabilizationConfig(enabled = true, level = 0.50f))
        val n = 60
        val baseTimeNanos = 1_000_000_000L
        val stepNanos = 8_333_333L // ~120Hz

        val rawPoints = mutableListOf<Triple<Float, Float, Long>>()
        var rawYSum = 0f
        var rawYSqSum = 0f

        for (i in 0 until n) {
            val x = i * 2.0f
            // Low-quality stylus capacitive noise: ±3.0px jitter alternating on Y axis
            val jitter = if (i % 2 == 0) 3.0f else -3.0f
            val y = 100.0f + jitter
            val t = baseTimeNanos + i * stepNanos
            rawPoints.add(Triple(x, y, t))
            rawYSum += y
            rawYSqSum += y * y
        }

        val rawMean = rawYSum / n
        val rawVar = (rawYSqSum / n) - (rawMean * rawMean)

        val stabilizedPoints = mutableListOf<Float>()
        val first = rawPoints.first()
        val startPt = stabilizer.start(first.first, first.second, 0.5f, first.third)
        stabilizedPoints.add(startPt.y)

        for (i in 1 until n) {
            val p = rawPoints[i]
            val pt = stabilizer.update(p.first, p.second, 0.5f, p.third)
            stabilizedPoints.add(pt.y)
        }

        // Exclude first 5 warm-up points
        val sub = stabilizedPoints.drop(5)
        val stabMean = sub.sum() / sub.size
        val stabVar = (sub.sumOf { (it - stabMean).toDouble() * (it - stabMean).toDouble() } / sub.size).toFloat()

        // Jitter variance should be drastically reduced (at least 75% noise reduction)
        assertTrue("Stabilized variance ($stabVar) should be much lower than raw jitter variance ($rawVar)", stabVar < rawVar * 0.25f)
    }

    @Test
    fun testStrokeEndCatchUpExactness() {
        val stabilizer = StrokeStabilizer(StrokeStabilizationConfig(enabled = true, level = 0.60f))
        val baseTimeNanos = 1_000_000_000L
        stabilizer.start(10f, 10f, 0.5f, baseTimeNanos)

        // Rapid movement to create physical lag
        for (i in 1..10) {
            stabilizer.update(10f + i * 20f, 10f + i * 20f, 0.5f, baseTimeNanos + i * 8_000_000L)
        }

        val finalRawX = 250f
        val finalRawY = 250f
        val finalTimeNanos = baseTimeNanos + 100_000_000L

        val endPoints = stabilizer.finish(finalRawX, finalRawY, 0.7f, finalTimeNanos)
        assertFalse(endPoints.isEmpty())

        val lastPoint = endPoints.last()
        assertEquals("Final catchup point X must match final raw coordinate", finalRawX, lastPoint.x, 1e-3f)
        assertEquals("Final catchup point Y must match final raw coordinate", finalRawY, lastPoint.y, 1e-3f)
        assertEquals("Final catchup point timeNanos must match final time", finalTimeNanos, lastPoint.timeNanos)
    }

    @Test
    fun testDisabledBypassPassesRawThrough() {
        val stabilizer = StrokeStabilizer(StrokeStabilizationConfig(enabled = false, level = 0.50f))
        val t0 = 1_000_000_000L
        val p1 = stabilizer.start(15f, 25f, 0.4f, t0)
        assertEquals(15f, p1.x, 1e-4f)
        assertEquals(25f, p1.y, 1e-4f)

        val p2 = stabilizer.update(45.5f, 65.5f, 0.8f, t0 + 10_000_000L)
        assertEquals(45.5f, p2.x, 1e-4f)
        assertEquals(65.5f, p2.y, 1e-4f)
        assertEquals(0.8f, p2.pressure, 1e-4f)
    }

    @Test
    fun testZeroLevelPassesRawThrough() {
        val stabilizer = StrokeStabilizer(StrokeStabilizationConfig(enabled = true, level = 0.0f))
        val t0 = 1_000_000_000L
        stabilizer.start(10f, 20f, 0.5f, t0)
        val p = stabilizer.update(50f, 60f, 0.7f, t0 + 8_000_000L)
        assertEquals(50f, p.x, 1e-4f)
        assertEquals(60f, p.y, 1e-4f)
    }

    @Test
    fun testPressureNoiseSmoothing() {
        val stabilizer = StrokeStabilizer(StrokeStabilizationConfig(enabled = true, level = 0.50f))
        val t0 = 1_000_000_000L
        stabilizer.start(0f, 0f, 0.5f, t0)

        // Jittery pressure: alternating 0.2f and 0.8f
        val pressures = mutableListOf<Float>()
        for (i in 1..20) {
            val noisyP = if (i % 2 == 0) 0.8f else 0.2f
            val pt = stabilizer.update(i * 5f, 0f, noisyP, t0 + i * 8_000_000L)
            pressures.add(pt.pressure)
        }

        // Pressure differences between adjacent samples should be smoothed
        val maxDelta = pressures.zipWithNext { a, b -> kotlin.math.abs(a - b) }.maxOrNull() ?: 1.0f
        assertTrue("Pressure delta ($maxDelta) should be significantly less than raw 0.6f jump", maxDelta < 0.35f)
    }

    @Test
    fun testNanosecondIntervalRobustness() {
        val stabilizer = StrokeStabilizer(StrokeStabilizationConfig(enabled = true, level = 0.50f))
        val t0 = 1_000_000_000L
        stabilizer.start(0f, 0f, 0.5f, t0)

        // Extremely small dt (100 microseconds), 0 dt (duplicate), and larger dt (30ms)
        val p1 = stabilizer.update(1f, 1f, 0.5f, t0 + 100_000L) // 0.1ms
        assertFalse(p1.x.isNaN())
        assertFalse(p1.y.isNaN())

        val p2 = stabilizer.update(2f, 2f, 0.5f, t0 + 100_000L) // duplicate timestamp (0ms delta)
        assertFalse(p2.x.isNaN())
        assertFalse(p2.y.isNaN())

        val p3 = stabilizer.update(10f, 10f, 0.5f, t0 + 30_000_000L) // 30ms delta
        assertFalse(p3.x.isNaN())
        assertFalse(p3.y.isNaN())
    }
}
