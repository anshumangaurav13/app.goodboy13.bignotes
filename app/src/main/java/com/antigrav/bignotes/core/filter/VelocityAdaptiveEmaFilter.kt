package com.antigrav.bignotes.core.filter

import kotlin.math.hypot
import kotlin.math.pow

/**
 * Velocity-Adaptive Exponential Moving Average (EMA) filter for 2D stylus stroke stabilization.
 *
 * Smooths low-frequency hand tremors and digitizer quantization noise during slow, deliberate drawing,
 * while dynamically ramping alpha based on stylus velocity to guarantee low latency and sharp corner fidelity.
 *
 * Supports full user control over:
 * 1. [smoothingLevel]: Base stabilization strength at slow speeds [0.0..1.0].
 * 2. [velocityWeight]: How strongly stylus speed reduces smoothing [0.0..1.0].
 *    0.0 = Pure constant EMA smoothing (velocity has zero effect).
 *    0.5 = Balanced speed catch-up (default).
 *    1.0 = Maximum speed responsiveness, rapidly ramping to raw input on fast flicks.
 *
 * Sampling-rate independent (normalized against 120Hz reference interval dt0 = 8.33ms).
 * Zero heap allocations during continuous stroke filtering.
 */
class VelocityAdaptiveEmaFilter(
    initialSmoothingLevel: Float = 0.50f,
    initialVelocityWeight: Float = 0.50f
) {
    var smoothingLevel: Float = initialSmoothingLevel.coerceIn(0.0f, 1.0f)
        private set
    var velocityWeight: Float = initialVelocityWeight.coerceIn(0.0f, 1.0f)
        private set

    var filteredX: Float = 0f
        private set
    var filteredY: Float = 0f
        private set

    private var lastTimestampMs: Long = -1L
    private var isInitialized: Boolean = false

    /**
     * Resets filter state between strokes.
     */
    fun reset() {
        isInitialized = false
        lastTimestampMs = -1L
        filteredX = 0f
        filteredY = 0f
    }

    /**
     * Sets base smoothing level in [0.0..1.0].
     * 0.0 = Raw input / no smoothing
     * 0.3 = Natural pen stabilization
     * 0.5 = Balanced stabilization (default)
     * 0.7 = Calligraphy smoothing
     * 1.0 = Maximum silky flow
     */
    fun setSmoothing(level: Float) {
        smoothingLevel = level.coerceIn(0.0f, 1.0f)
    }

    /**
     * Sets velocity effect weight in [0.0..1.0].
     * 0.0 = Pure constant EMA (velocity has no effect).
     * 0.5 = Balanced speed catch-up (default).
     * 1.0 = High velocity catch-up (snappy flicks, lowest latency).
     */
    fun setVelocityWeight(weight: Float) {
        velocityWeight = weight.coerceIn(0.0f, 1.0f)
    }

    /**
     * Filters incoming stylus position (x, y) at timestampMs.
     * Results stored in [filteredX] and [filteredY].
     */
    fun filter(x: Float, y: Float, timestampMs: Long) {
        if (!isInitialized) {
            isInitialized = true
            lastTimestampMs = timestampMs
            filteredX = x
            filteredY = y
            return
        }

        if (smoothingLevel <= 0.01f) {
            lastTimestampMs = timestampMs
            filteredX = x
            filteredY = y
            return
        }

        // Time step in seconds. If sub-events share a millisecond, assume 4ms inter-sample interval.
        val dt = if (lastTimestampMs > 0 && timestampMs > lastTimestampMs) {
            ((timestampMs - lastTimestampMs).toFloat() / 1000f).coerceIn(0.001f, 0.050f)
        } else {
            0.004f
        }
        lastTimestampMs = timestampMs

        // Displacement from previous smoothed position
        val dx = x - filteredX
        val dy = y - filteredY
        val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat()

        // Instantaneous stylus velocity in pixels/sec
        val velocity = dist / dt

        // 1. Base alpha at low/zero velocity
        val baseAlpha = (1.0f - (0.94f * smoothingLevel)).coerceIn(0.04f, 1.0f)

        // 2. Velocity scaling reference: governed by velocityWeight
        // When velocityWeight is 1.0 -> refVelocity = 1200 px/s (very quick catch-up)
        // When velocityWeight is 0.5 -> refVelocity = 3800 px/s (natural balance)
        // When velocityWeight is 0.0 -> velocity has 0 effect on targetAlpha
        val targetAlpha: Float = if (velocityWeight <= 0.01f) {
            baseAlpha
        } else {
            val refVelocity = 1200.0f + (1.0f - velocityWeight) * 5200.0f
            val speedFactor = (velocity / refVelocity).coerceIn(0.0f, 1.0f)
            val velocityBoost = (speedFactor * speedFactor) * velocityWeight
            (baseAlpha + (0.98f - baseAlpha) * velocityBoost).coerceIn(0.02f, 1.0f)
        }

        // 3. Frame-rate / sample-rate independence relative to 120Hz (dt0 = 0.00833s)
        val dtRatio = dt / 0.00833f
        val effectiveAlpha = (1.0f - (1.0f - targetAlpha).pow(dtRatio)).coerceIn(0.02f, 1.0f)

        // 4. Exponential Moving Average update
        filteredX += effectiveAlpha * dx
        filteredY += effectiveAlpha * dy
    }
}

