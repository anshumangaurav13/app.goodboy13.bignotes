package app.goodboy13.bignotes.core.geometry

import app.goodboy13.bignotes.core.model.StrokePoint
import app.goodboy13.bignotes.core.model.StrokeStabilizationConfig
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.pow

/**
 * Physical stroke stabilizer and digitizer jitter filter.
 * Inspired by Google Keep / Google Ink (ink-stroke-modeler).
 *
 * Implements a cascaded two-stage physical filtering architecture:
 * 1. Adaptive Wobble Smoother: Eliminates high-frequency digitizer quantization
 *    noise and sensor grid jitter at slow speeds, dynamically opening to pass-through
 *    at higher speeds to prevent corner cutting and trajectory lag.
 * 2. Critically Damped Spring-Mass-Damper: Simulates the stylus tip as a physical mass
 *    tethered to the filtered anchor with exact analytical closed-form integration,
 *    guaranteeing zero numerical divergence and zero overshoot regardless of sampling rate.
 *
 * Operates strictly with nanosecond timestamps (timeNanos) to prevent millisecond
 * truncation artifacts on high-polling (120Hz - 360Hz) digitizers.
 */
class StrokeStabilizer(
    var config: StrokeStabilizationConfig = StrokeStabilizationConfig()
) {
    private var hasStarted = false

    // Raw input tracking
    private var lastRawX = 0f
    private var lastRawY = 0f
    private var lastRawPressure = 0f
    private var lastTimeNanos = 0L

    // Stage 1: Adaptive Wobble Filter Anchor
    private var anchorX = 0f
    private var anchorY = 0f
    private var smoothedSpeedWorldPerSec = 0f

    // Stage 2: Critically Damped Spring-Mass-Damper Virtual Pen Tip
    private var tipX = 0f
    private var tipY = 0f
    private var tipVx = 0f
    private var tipVy = 0f

    // Pressure Filter
    private var smoothedPressure = 0.5f

    /**
     * Initializes the stabilizer with the initial touchdown coordinate.
     */
    fun start(x: Float, y: Float, pressure: Float, timeNanos: Long): StrokePoint {
        hasStarted = true
        lastRawX = x
        lastRawY = y
        lastRawPressure = pressure
        lastTimeNanos = timeNanos

        anchorX = x
        anchorY = y
        smoothedSpeedWorldPerSec = 0f

        tipX = x
        tipY = y
        tipVx = 0f
        tipVy = 0f

        smoothedPressure = pressure.coerceIn(0.01f, 1.0f)

        return StrokePoint(
            x = x,
            y = y,
            pressure = smoothedPressure,
            timeMillis = timeNanos / 1_000_000L,
            timeNanos = timeNanos
        )
    }

    /**
     * Updates the stabilizer with an incoming digitizer position.
     * All time intervals are computed strictly in nanoseconds.
     */
    fun update(rawX: Float, rawY: Float, rawPressure: Float, timeNanos: Long): StrokePoint {
        if (!hasStarted) {
            return start(rawX, rawY, rawPressure, timeNanos)
        }

        val level = config.level.coerceIn(0.0f, 1.0f)
        if (!config.enabled || level <= 0.001f) {
            lastRawX = rawX
            lastRawY = rawY
            lastRawPressure = rawPressure
            lastTimeNanos = timeNanos
            tipX = rawX
            tipY = rawY
            tipVx = 0f
            tipVy = 0f
            smoothedPressure = rawPressure
            return StrokePoint(rawX, rawY, rawPressure, timeNanos / 1_000_000L, timeNanos)
        }

        // Strict monotonic positive delta-t in nanoseconds
        val dtNanos = if (lastTimeNanos > 0L && timeNanos > lastTimeNanos) {
            timeNanos - lastTimeNanos
        } else {
            1_000_000L // 1ms fallback for non-monotonic hardware timestamps
        }
        val dtSec = (dtNanos.toFloat() / 1_000_000_000.0f).coerceIn(0.0005f, 0.050f)

        // Calculate instantaneous physical movement speed in world units per second
        val rawDist = hypot((rawX - lastRawX).toDouble(), (rawY - lastRawY).toDouble()).toFloat()
        val instSpeed = rawDist / dtSec
        smoothedSpeedWorldPerSec = smoothedSpeedWorldPerSec * 0.70f + instSpeed * 0.30f

        // Speed factor: 0.0 at rest/slow drawing, approaches 1.0 during rapid handwriting/flicks
        // Slow speed is where digitizer quantization tremor is most destructive
        val speedFactor = (smoothedSpeedWorldPerSec / (smoothedSpeedWorldPerSec + 600f)).coerceIn(0.0f, 1.0f)

        // Stage 1: Adaptive Wobble Smoother
        // Suppresses quantization stair-stepping without adding lag to fast strokes
        val tauWobble = (0.012f + 0.038f * level) * (1.0f - 0.75f * speedFactor)
        val alphaWobble = (1.0f - exp(-dtSec / tauWobble)).coerceIn(0.05f, 1.0f)
        anchorX += alphaWobble * (rawX - anchorX)
        anchorY += alphaWobble * (rawY - anchorY)

        // Stage 2: Critically Damped Physical Spring-Mass-Damper
        // Natural frequency omega (rad/s): lower omega = stronger smoothing / heavier mass
        // At level 0.5 (Google Keep baseline), omega ~ 55 rad/s (tau ~ 18ms)
        val baseOmega = 18.0f + 150.0f * (1.0f - level).pow(1.5f)
        // Speed boost: tighten spring during rapid flicks to eliminate tracking delay
        val effOmega = baseOmega * (1.0f + 1.25f * speedFactor)

        // Exact analytical closed-form step for critically damped harmonic oscillator:
        // x(t) = (x0 + (v0 + omega*x0)*t) * exp(-omega*t)
        // v(t) = (v0 - omega*(v0 + omega*x0)*t) * exp(-omega*t)
        val dx = tipX - anchorX
        val dy = tipY - anchorY
        val decay = exp(-effOmega * dtSec)
        val tempX = tipVx + effOmega * dx
        val tempY = tipVy + effOmega * dy

        tipX = anchorX + (dx + tempX * dtSec) * decay
        tipY = anchorY + (dy + tempY * dtSec) * decay
        tipVx = (tipVx - effOmega * tempX * dtSec) * decay
        tipVy = (tipVy - effOmega * tempY * dtSec) * decay

        // Stage 3: Smooth noisy pressure sensor reports
        val tauPressure = 0.015f + 0.035f * level
        val alphaPressure = (1.0f - exp(-dtSec / tauPressure)).coerceIn(0.05f, 1.0f)
        smoothedPressure += alphaPressure * (rawPressure - smoothedPressure)

        lastRawX = rawX
        lastRawY = rawY
        lastRawPressure = rawPressure
        lastTimeNanos = timeNanos

        return StrokePoint(
            x = tipX,
            y = tipY,
            pressure = smoothedPressure.coerceIn(0.01f, 1.0f),
            timeMillis = timeNanos / 1_000_000L,
            timeNanos = timeNanos
        )
    }

    /**
     * Finalizes the stroke upon digitizer lift (ACTION_UP).
     * Performs catch-up integration steps anchoring directly to the final release coordinate
     * so the tail of the stroke smoothly converges to the exact physical release point.
     */
    fun finish(rawX: Float, rawY: Float, rawPressure: Float, timeNanos: Long): List<StrokePoint> {
        if (!hasStarted) {
            return listOf(StrokePoint(rawX, rawY, rawPressure, timeNanos / 1_000_000L, timeNanos))
        }

        val level = config.level.coerceIn(0.0f, 1.0f)
        if (!config.enabled || level <= 0.001f) {
            reset()
            return listOf(StrokePoint(rawX, rawY, rawPressure, timeNanos / 1_000_000L, timeNanos))
        }

        val remainingDist = hypot((rawX - tipX).toDouble(), (rawY - tipY).toDouble()).toFloat()
        val result = mutableListOf<StrokePoint>()

        if (remainingDist > 0.5f) {
            // Rapid catch-up integration towards the exact release point
            val catchupOmega = 120.0f
            val substepDt = 0.006f
            val steps = if (remainingDist > 4.0f) 3 else 2

            for (s in 1..steps) {
                val dx = tipX - rawX
                val dy = tipY - rawY
                val decay = exp(-catchupOmega * substepDt)
                val tempX = tipVx + catchupOmega * dx
                val tempY = tipVy + catchupOmega * dy
                tipX = rawX + (dx + tempX * substepDt) * decay
                tipY = rawY + (dy + tempY * substepDt) * decay
                tipVx = (tipVx - catchupOmega * tempX * substepDt) * decay
                tipVy = (tipVy - catchupOmega * tempY * substepDt) * decay

                val tNanos = lastTimeNanos + (s * 2_000_000L)
                result.add(
                    StrokePoint(
                        x = tipX,
                        y = tipY,
                        pressure = smoothedPressure.coerceIn(0.01f, 1.0f),
                        timeMillis = tNanos / 1_000_000L,
                        timeNanos = tNanos
                    )
                )
            }
        }

        // Hard guarantee: the stroke terminates exactly at the user's release position
        val endPoint = StrokePoint(
            x = rawX,
            y = rawY,
            pressure = rawPressure.coerceIn(0.01f, 1.0f),
            timeMillis = timeNanos / 1_000_000L,
            timeNanos = timeNanos
        )
        result.add(endPoint)

        reset()
        return result
    }

    /**
     * Resets the stabilizer state.
     */
    fun reset() {
        hasStarted = false
        lastRawX = 0f
        lastRawY = 0f
        lastRawPressure = 0f
        lastTimeNanos = 0L
        anchorX = 0f
        anchorY = 0f
        smoothedSpeedWorldPerSec = 0f
        tipX = 0f
        tipY = 0f
        tipVx = 0f
        tipVy = 0f
        smoothedPressure = 0.5f
    }
}
