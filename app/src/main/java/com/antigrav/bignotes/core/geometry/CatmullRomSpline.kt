package com.antigrav.bignotes.core.geometry

import com.antigrav.bignotes.core.model.StrokePoint
import kotlin.math.hypot
import kotlin.math.max

object CatmullRomSpline {
    /**
     * Interpolates a sequence of StrokePoints with a Centripetal/Chordal Catmull-Rom spline.
     * Generates intermediate points spaced by stepDistance for smooth tessellation.
     */
    fun interpolate(points: List<StrokePoint>, stepDistance: Float = 2.0f): List<StrokePoint> {
        if (points.size < 2) return points
        if (points.size == 2) {
            return interpolateLinear(points[0], points[1], stepDistance)
        }

        val result = mutableListOf<StrokePoint>()
        result.add(points.first())

        val count = points.size
        for (i in 0 until count - 1) {
            val p0 = if (i > 0) points[i - 1] else points[0]
            val p1 = points[i]
            val p2 = points[i + 1]
            val p3 = if (i + 2 < count) points[i + 2] else p2

            val segmentLength = hypot(p2.x - p1.x, p2.y - p1.y)
            if (segmentLength < 1e-3f) continue
            val steps = max(1, (segmentLength / stepDistance).toInt())

            for (s in 1..steps) {
                val t = s.toFloat() / steps.toFloat()
                val interpolated = evaluate(p0, p1, p2, p3, t)
                result.add(interpolated)
            }
        }

        return result
    }

    private fun interpolateLinear(p0: StrokePoint, p1: StrokePoint, stepDistance: Float): List<StrokePoint> {
        val dist = hypot(p1.x - p0.x, p1.y - p0.y)
        val steps = max(1, (dist / stepDistance).toInt())
        val list = ArrayList<StrokePoint>(steps + 1)
        for (s in 0..steps) {
            val t = s.toFloat() / steps.toFloat()
            list.add(
                StrokePoint(
                    x = p0.x + (p1.x - p0.x) * t,
                    y = p0.y + (p1.y - p0.y) * t,
                    pressure = p0.pressure + (p1.pressure - p0.pressure) * t,
                    timeMillis = (p0.timeMillis + (p1.timeMillis - p0.timeMillis) * t).toLong()
                )
            )
        }
        return list
    }

    private fun evaluate(
        p0: StrokePoint,
        p1: StrokePoint,
        p2: StrokePoint,
        p3: StrokePoint,
        t: Float
    ): StrokePoint {
        val t2 = t * t
        val t3 = t2 * t

        // Standard Catmull-Rom matrix:
        // q(t) = 0.5 * ((2*p1) + (-p0 + p2)*t + (2*p0 - 5*p1 + 4*p2 - p3)*t^2 + (-p0 + 3*p1 - 3*p2 + p3)*t^3)
        val f1 = -t3 + 2.0f * t2 - t
        val f2 = 3.0f * t3 - 5.0f * t2 + 2.0f
        val f3 = -3.0f * t3 + 4.0f * t2 + t
        val f4 = t3 - t2

        val x = 0.5f * (p0.x * f1 + p1.x * f2 + p2.x * f3 + p3.x * f4)
        val y = 0.5f * (p0.y * f1 + p1.y * f2 + p2.y * f3 + p3.y * f4)
        val pressure = (0.5f * (p0.pressure * f1 + p1.pressure * f2 + p2.pressure * f3 + p3.pressure * f4)).coerceIn(0.0f, 1.0f)
        val time = (0.5f * (p0.timeMillis * f1 + p1.timeMillis * f2 + p2.timeMillis * f3 + p3.timeMillis * f4)).toLong()

        return StrokePoint(x, y, pressure, time)
    }
}

