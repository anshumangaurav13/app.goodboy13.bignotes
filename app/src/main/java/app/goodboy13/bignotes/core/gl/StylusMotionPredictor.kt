package app.goodboy13.bignotes.core.gl

import app.goodboy13.bignotes.core.model.StrokePoint
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * High-precision 2nd-order kinematic motion predictor for active stylus inking.
 * Predicts stylus position 10-15ms ahead specifically for the ephemeral front-buffer layer,
 * closing the physical touch-to-glass latency gap without distorting committed stroke geometry.
 */
class StylusMotionPredictor(
    private val lookaheadMs: Float = 12.0f,
    private val maxPredictionDistanceScreenPx: Float = 25.0f
) {
    private var p0: StrokePoint? = null
    private var p1: StrokePoint? = null
    private var p2: StrokePoint? = null

    fun reset() {
        p0 = null
        p1 = null
        p2 = null
    }

    fun addPoint(pt: StrokePoint) {
        p0 = p1
        p1 = p2
        p2 = pt
    }

    /**
     * Predicts the next stroke point [lookaheadMs] into the future based on
     * 2nd-order Taylor series expansion (position + velocity * dt + 0.5 * accel * dt^2).
     * Includes curvature damping to prevent overshoot on sharp corners.
     */
    fun predict(): StrokePoint? {
        val curr = p2 ?: return null
        val prev = p1 ?: return null
        val prevPrev = p0

        val dt1 = (curr.timeMillis - prev.timeMillis).toFloat().coerceIn(1.0f, 40.0f)
        val vx2 = (curr.x - prev.x) / dt1
        val vy2 = (curr.y - prev.y) / dt1
        val speed2 = hypot(vx2.toDouble(), vy2.toDouble()).toFloat()

        if (speed2 < 0.05f) {
            // Stylus is nearly stationary; no prediction needed
            return null
        }

        var ax = 0f
        var ay = 0f

        if (prevPrev != null) {
            val dt0 = (prev.timeMillis - prevPrev.timeMillis).toFloat().coerceIn(1.0f, 40.0f)
            val vx1 = (prev.x - prevPrev.x) / dt0
            val vy1 = (prev.y - prevPrev.y) / dt0

            // Curvature damping: if dot product is negative or small, stylus is sharply turning
            val dot = vx1 * vx2 + vy1 * vy2
            val speed1 = hypot(vx1.toDouble(), vy1.toDouble()).toFloat()
            val cosAngle = if (speed1 > 1e-4f && speed2 > 1e-4f) dot / (speed1 * speed2) else 1.0f

            if (cosAngle < 0.3f) {
                // Sharp turn; do not extrapolate in wrong direction
                return null
            }

            val dtAvg = (dt0 + dt1) * 0.5f
            ax = ((vx2 - vx1) / dtAvg).coerceIn(-0.01f, 0.01f)
            ay = ((vy2 - vy1) / dtAvg).coerceIn(-0.01f, 0.01f)
        }

        val dtPred = lookaheadMs
        var predDx = vx2 * dtPred + 0.5f * ax * dtPred * dtPred
        var predDy = vy2 * dtPred + 0.5f * ay * dtPred * dtPred

        val predDist = hypot(predDx.toDouble(), predDy.toDouble()).toFloat()
        if (predDist > maxPredictionDistanceScreenPx) {
            val scale = maxPredictionDistanceScreenPx / predDist
            predDx *= scale
            predDy *= scale
        }

        val predX = curr.x + predDx
        val predY = curr.y + predDy
        val predTime = curr.timeMillis + lookaheadMs.toLong()

        return StrokePoint(
            x = predX,
            y = predY,
            pressure = curr.pressure,
            timeMillis = predTime
        )
    }
}
