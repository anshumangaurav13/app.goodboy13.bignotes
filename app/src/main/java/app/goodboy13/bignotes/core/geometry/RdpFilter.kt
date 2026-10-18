package app.goodboy13.bignotes.core.geometry

import app.goodboy13.bignotes.core.model.StrokePoint
import kotlin.math.abs
import kotlin.math.sqrt

object RdpFilter {
    /**
     * Ramer-Douglas-Peucker algorithm to reduce redundant digitizer sample points within epsilon tolerance.
     */
    fun simplify(points: List<StrokePoint>, epsilon: Float = 0.8f): List<StrokePoint> {
        if (points.size <= 2) return points
        val keep = BooleanArray(points.size) { false }
        keep[0] = true
        keep[points.size - 1] = true

        simplifySection(points, 0, points.size - 1, epsilon, keep)

        val result = ArrayList<StrokePoint>(points.size)
        for (i in points.indices) {
            if (keep[i]) {
                result.add(points[i])
            }
        }
        return result
    }

    private fun simplifySection(
        points: List<StrokePoint>,
        start: Int,
        end: Int,
        epsilon: Float,
        keep: BooleanArray
    ) {
        if (end <= start + 1) return

        var maxDist = 0.0f
        var maxIndex = start

        val p1 = points[start]
        val p2 = points[end]

        val dx = p2.x - p1.x
        val dy = p2.y - p1.y
        val segLenSq = dx * dx + dy * dy

        for (i in start + 1 until end) {
            val p = points[i]
            val dist = if (segLenSq < 1e-6f) {
                val ex = p.x - p1.x
                val ey = p.y - p1.y
                sqrt(ex * ex + ey * ey)
            } else {
                // Perpendicular distance = |(p2.x - p1.x)*(p1.y - p.y) - (p1.x - p.x)*(p2.y - p1.y)| / sqrt(segLenSq)
                abs(dx * (p1.y - p.y) - (p1.x - p.x) * dy) / sqrt(segLenSq)
            }

            if (dist > maxDist) {
                maxDist = dist
                maxIndex = i
            }
        }

        if (maxDist > epsilon) {
            keep[maxIndex] = true
            simplifySection(points, start, maxIndex, epsilon, keep)
            simplifySection(points, maxIndex, end, epsilon, keep)
        }
    }
}

