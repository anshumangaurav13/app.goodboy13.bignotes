package app.goodboy13.bignotes.core.eraser

import app.goodboy13.bignotes.core.model.Stroke
import app.goodboy13.bignotes.core.model.StrokePoint
import java.util.UUID

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

    /**
     * Precision Eraser: Clips stroke points inside the eraser circle.
     * Divides the surviving points into sub-strokes while preserving tone and baseWidth attributes.
     */
    fun clipStrokeByCircle(stroke: Stroke, cx: Float, cy: Float, radius: Float): List<Stroke> {
        val radSq = radius * radius
        val subStrokes = mutableListOf<Stroke>()
        var currentCluster = mutableListOf<StrokePoint>()

        for (pt in stroke.points) {
            val dx = pt.x - cx
            val dy = pt.y - cy
            val isInside = (dx * dx + dy * dy) <= radSq
            if (!isInside) {
                currentCluster.add(pt)
            } else {
                if (currentCluster.isNotEmpty()) {
                    subStrokes.add(
                        stroke.copy(
                            id = UUID.randomUUID().toString(),
                            points = currentCluster.toList()
                        )
                    )
                    currentCluster = mutableListOf()
                }
            }
        }
        if (currentCluster.isNotEmpty()) {
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

