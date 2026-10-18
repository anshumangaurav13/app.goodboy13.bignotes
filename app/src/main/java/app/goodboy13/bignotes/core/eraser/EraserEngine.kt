package app.goodboy13.bignotes.core.eraser

import app.goodboy13.bignotes.core.model.Stroke
import app.goodboy13.bignotes.core.model.StrokePoint
import java.util.UUID
import kotlin.math.sqrt

object EraserEngine {

    /**
     * Calculates squared distance from point (px, py) to line segment (x1, y1)-(x2, y2).
     */
    fun distSqPointToSegment(px: Float, py: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        val lenSq = dx * dx + dy * dy
        if (lenSq < 1e-6f) {
            val sx = px - x1
            val sy = py - y1
            return sx * sx + sy * sy
        }
        val t = ((px - x1) * dx + (py - y1) * dy) / lenSq
        val clampedT = t.coerceIn(0.0f, 1.0f)
        val projX = x1 + clampedT * dx
        val projY = y1 + clampedT * dy
        val diffX = px - projX
        val diffY = py - projY
        return diffX * diffX + diffY * diffY
    }

    /**
     * Checks if any segment of a stroke intersects the circular eraser area.
     */
    fun doesStrokeIntersectCircle(stroke: Stroke, cx: Float, cy: Float, radius: Float): Boolean {
        val threshold = radius + stroke.baseWidth * 0.5f
        val threshSq = threshold * threshold
        val pts = stroke.points
        if (pts.isEmpty()) return false
        if (pts.size == 1) {
            val dx = pts[0].x - cx
            val dy = pts[0].y - cy
            return (dx * dx + dy * dy) <= threshSq
        }
        for (i in 0 until pts.size - 1) {
            val p1 = pts[i]
            val p2 = pts[i + 1]
            if (distSqPointToSegment(cx, cy, p1.x, p1.y, p2.x, p2.y) <= threshSq) {
                return true
            }
        }
        return false
    }

    private fun interpolate(p1: StrokePoint, p2: StrokePoint, t: Float): StrokePoint {
        val clampedT = t.coerceIn(0.0f, 1.0f)
        return StrokePoint(
            x = p1.x + clampedT * (p2.x - p1.x),
            y = p1.y + clampedT * (p2.y - p1.y),
            pressure = p1.pressure + clampedT * (p2.pressure - p1.pressure),
            timeMillis = p1.timeMillis + (clampedT * (p2.timeMillis - p1.timeMillis)).toLong()
        )
    }

    private fun addPointDeduplicated(list: MutableList<StrokePoint>, pt: StrokePoint) {
        val last = list.lastOrNull()
        if (last != null) {
            val dx = pt.x - last.x
            val dy = pt.y - last.y
            if ((dx * dx + dy * dy) < 1e-4f) {
                return
            }
        }
        list.add(pt)
    }

    /**
     * Precision Vector Eraser:
     * Mathematically intersects every stroke segment with the circular eraser boundary.
     * Computes exact boundary entry/exit points and splits the stroke into clean, continuous sub-strokes
     * that terminate flush against the circular eraser reticle.
     */
    fun clipStrokeByCircle(stroke: Stroke, cx: Float, cy: Float, radius: Float): List<Stroke> {
        val pts = stroke.points
        if (pts.isEmpty()) return emptyList()

        val radSq = radius * radius

        if (pts.size == 1) {
            val dx = pts[0].x - cx
            val dy = pts[0].y - cy
            return if ((dx * dx + dy * dy) <= radSq) emptyList() else listOf(stroke)
        }

        val subStrokes = mutableListOf<Stroke>()
        var currentCluster = mutableListOf<StrokePoint>()

        // Check if initial point is outside
        val firstPt = pts[0]
        val firstDx = firstPt.x - cx
        val firstDy = firstPt.y - cy
        if ((firstDx * firstDx + firstDy * firstDy) > radSq) {
            currentCluster.add(firstPt)
        }

        for (i in 0 until pts.size - 1) {
            val p1 = pts[i]
            val p2 = pts[i + 1]

            val dx = p2.x - p1.x
            val dy = p2.y - p1.y
            val a = dx * dx + dy * dy

            val f1x = p1.x - cx
            val f1y = p1.y - cy
            val p1Inside = (f1x * f1x + f1y * f1y) <= radSq

            val f2x = p2.x - cx
            val f2y = p2.y - cy
            val p2Inside = (f2x * f2x + f2y * f2y) <= radSq

            if (a < 1e-8f) {
                // Degenerate segment (coincident points)
                if (!p2Inside) {
                    addPointDeduplicated(currentCluster, p2)
                }
                continue
            }

            val b = 2.0f * (f1x * dx + f1y * dy)
            val c = (f1x * f1x + f1y * f1y) - radSq
            val discriminant = b * b - 4.0f * a * c

            if (discriminant < 0.0f) {
                // No geometric intersection with infinite line
                if (!p1Inside && !p2Inside) {
                    addPointDeduplicated(currentCluster, p2)
                }
            } else {
                val sqrtDisc = sqrt(discriminant)
                val t1 = (-b - sqrtDisc) / (2.0f * a)
                val t2 = (-b + sqrtDisc) / (2.0f * a)

                // The interval (t1, t2) is inside the circle
                // We determine whether the segment [0, 1] intersects the circle
                if (t2 <= 0.0f || t1 >= 1.0f) {
                    // Intersection is outside the segment
                    if (!p1Inside && !p2Inside) {
                        addPointDeduplicated(currentCluster, p2)
                    }
                } else {
                    // Segment enters and/or exits the circle
                    if (t1 in 0.0f..1.0f && !p1Inside) {
                        // Segment enters the circle at t1
                        val entryPt = interpolate(p1, p2, t1)
                        addPointDeduplicated(currentCluster, entryPt)

                        if (currentCluster.size >= 2) {
                            subStrokes.add(
                                stroke.copy(
                                    id = UUID.randomUUID().toString(),
                                    points = currentCluster.toList()
                                )
                            )
                        }
                        currentCluster = mutableListOf()
                    }

                    if (t2 in 0.0f..1.0f && !p2Inside) {
                        // Segment exits the circle at t2
                        val exitPt = interpolate(p1, p2, t2)
                        addPointDeduplicated(currentCluster, exitPt)
                        addPointDeduplicated(currentCluster, p2)
                    }
                }
            }
        }

        if (currentCluster.size >= 2) {
            subStrokes.add(
                stroke.copy(
                    id = UUID.randomUUID().toString(),
                    points = currentCluster.toList()
                )
            )
        }

        return subStrokes
    }
}
