package com.antigrav.bignotes.core.geometry

import com.antigrav.bignotes.core.model.StrokePoint
import kotlin.math.*

sealed class RecognizedShape {
    abstract fun toStrokePoints(basePressure: Float = 0.5f, timestamp: Long = System.currentTimeMillis()): List<StrokePoint>
    abstract fun adjustWithDrag(dragPoint: StrokePoint): RecognizedShape

    data class Line(
        val startX: Float,
        val startY: Float,
        val endX: Float,
        val endY: Float,
        val isSnappedToAngle: Boolean = false
    ) : RecognizedShape() {
        override fun toStrokePoints(basePressure: Float, timestamp: Long): List<StrokePoint> {
            val dx = endX - startX
            val dy = endY - startY
            val len = hypot(dx, dy)
            if (len < 1e-3f) return listOf(StrokePoint(startX, startY, basePressure, timestamp))
            val step = 3.0f
            val count = maxOf(2, (len / step).toInt())
            val list = ArrayList<StrokePoint>(count + 1)
            for (i in 0..count) {
                val t = i.toFloat() / count.toFloat()
                list.add(StrokePoint(startX + dx * t, startY + dy * t, basePressure, timestamp + i * 5L))
            }
            return list
        }

        override fun adjustWithDrag(dragPoint: StrokePoint): RecognizedShape {
            val (snappedX, snappedY, snapped) = snapAngle(startX, startY, dragPoint.x, dragPoint.y)
            return copy(endX = snappedX, endY = snappedY, isSnappedToAngle = snapped)
        }
    }

    data class Arrow(
        val startX: Float,
        val startY: Float,
        val tipX: Float,
        val tipY: Float,
        val headLength: Float = 28f,
        val headAngleRad: Float = (Math.PI / 6.0).toFloat() // 30 degrees
    ) : RecognizedShape() {
        override fun toStrokePoints(basePressure: Float, timestamp: Long): List<StrokePoint> {
            val dx = tipX - startX
            val dy = tipY - startY
            val len = hypot(dx, dy)
            if (len < 1e-3f) return listOf(StrokePoint(startX, startY, basePressure, timestamp))
            val list = ArrayList<StrokePoint>()
            // Shaft from start to tip
            val step = 3.0f
            val shaftCount = maxOf(2, (len / step).toInt())
            var tOffset = timestamp
            for (i in 0..shaftCount) {
                val t = i.toFloat() / shaftCount.toFloat()
                list.add(StrokePoint(startX + dx * t, startY + dy * t, basePressure, tOffset))
                tOffset += 5L
            }

            // Arrowhead wings
            val shaftAngle = atan2(dy, dx)
            val effHeadLen = minOf(headLength, len * 0.35f).coerceAtLeast(10f)

            // Wing 1 (left)
            val wing1Angle = shaftAngle + Math.PI.toFloat() - headAngleRad
            val w1X = tipX + effHeadLen * cos(wing1Angle)
            val w1Y = tipY + effHeadLen * sin(wing1Angle)
            val wingSteps = maxOf(2, (effHeadLen / step).toInt())
            for (i in 1..wingSteps) {
                val t = i.toFloat() / wingSteps.toFloat()
                list.add(StrokePoint(tipX + (w1X - tipX) * t, tipY + (w1Y - tipY) * t, basePressure, tOffset))
                tOffset += 5L
            }
            // Retrace back to tip
            for (i in 1..wingSteps) {
                val t = i.toFloat() / wingSteps.toFloat()
                list.add(StrokePoint(w1X + (tipX - w1X) * t, w1Y + (tipY - w1Y) * t, basePressure, tOffset))
                tOffset += 5L
            }

            // Wing 2 (right)
            val wing2Angle = shaftAngle + Math.PI.toFloat() + headAngleRad
            val w2X = tipX + effHeadLen * cos(wing2Angle)
            val w2Y = tipY + effHeadLen * sin(wing2Angle)
            for (i in 1..wingSteps) {
                val t = i.toFloat() / wingSteps.toFloat()
                list.add(StrokePoint(tipX + (w2X - tipX) * t, tipY + (w2Y - tipY) * t, basePressure, tOffset))
                tOffset += 5L
            }
            return list
        }

        override fun adjustWithDrag(dragPoint: StrokePoint): RecognizedShape {
            val (snappedX, snappedY, _) = snapAngle(startX, startY, dragPoint.x, dragPoint.y)
            return copy(tipX = snappedX, tipY = snappedY)
        }
    }

    data class Circle(
        val centerX: Float,
        val centerY: Float,
        val radius: Float
    ) : RecognizedShape() {
        override fun toStrokePoints(basePressure: Float, timestamp: Long): List<StrokePoint> {
            val circumference = 2.0f * Math.PI.toFloat() * radius
            val step = 3.0f
            val count = maxOf(24, (circumference / step).toInt())
            val list = ArrayList<StrokePoint>(count + 2)
            var tOffset = timestamp
            for (i in 0..count) {
                val theta = (2.0f * Math.PI.toFloat() * i) / count.toFloat()
                val px = centerX + radius * cos(theta)
                val py = centerY + radius * sin(theta)
                list.add(StrokePoint(px, py, basePressure, tOffset))
                tOffset += 5L
            }
            return list
        }

        override fun adjustWithDrag(dragPoint: StrokePoint): RecognizedShape {
            val newR = hypot(dragPoint.x - centerX, dragPoint.y - centerY).coerceAtLeast(6f)
            return copy(radius = newR)
        }
    }

    data class Ellipse(
        val centerX: Float,
        val centerY: Float,
        val radiusX: Float,
        val radiusY: Float,
        val rotationRadians: Float = 0f
    ) : RecognizedShape() {
        override fun toStrokePoints(basePressure: Float, timestamp: Long): List<StrokePoint> {
            val approxPerimeter = Math.PI.toFloat() * (3.0f * (radiusX + radiusY) - sqrt((3.0f * radiusX + radiusY) * (radiusX + 3.0f * radiusY)))
            val step = 3.0f
            val count = maxOf(24, (approxPerimeter / step).toInt())
            val list = ArrayList<StrokePoint>(count + 2)
            val cosRot = cos(rotationRadians)
            val sinRot = sin(rotationRadians)
            var tOffset = timestamp
            for (i in 0..count) {
                val theta = (2.0f * Math.PI.toFloat() * i) / count.toFloat()
                val ux = radiusX * cos(theta)
                val uy = radiusY * sin(theta)
                val px = centerX + ux * cosRot - uy * sinRot
                val py = centerY + ux * sinRot + uy * cosRot
                list.add(StrokePoint(px, py, basePressure, tOffset))
                tOffset += 5L
            }
            return list
        }

        override fun adjustWithDrag(dragPoint: StrokePoint): RecognizedShape {
            val dx = dragPoint.x - centerX
            val dy = dragPoint.y - centerY
            val cosRot = cos(-rotationRadians)
            val sinRot = sin(-rotationRadians)
            val localX = abs(dx * cosRot - dy * sinRot).coerceAtLeast(8f)
            val localY = abs(dx * sinRot + dy * cosRot).coerceAtLeast(8f)
            return copy(radiusX = localX, radiusY = localY)
        }
    }

    data class Rectangle(
        val p0X: Float, val p0Y: Float,
        val p1X: Float, val p1Y: Float,
        val p2X: Float, val p2Y: Float,
        val p3X: Float, val p3Y: Float,
        val isSquare: Boolean = false,
        val rotationRadians: Float = 0f,
        val baseWidth: Float = hypot(p1X - p0X, p1Y - p0Y),
        val baseHeight: Float = hypot(p2X - p1X, p2Y - p1Y),
        val centerX: Float = (p0X + p1X + p2X + p3X) * 0.25f,
        val centerY: Float = (p0Y + p1Y + p2Y + p3Y) * 0.25f,
        val initialDragAngle: Float? = null,
        val initialDragDist: Float? = null,
        val initialRotation: Float = rotationRadians,
        val isClockwise: Boolean = true
    ) : RecognizedShape() {
        override fun toStrokePoints(basePressure: Float, timestamp: Long): List<StrokePoint> {
            val list = ArrayList<StrokePoint>()
            var tOffset = timestamp
            val corners = listOf(
                Pair(p0X, p0Y), Pair(p1X, p1Y), Pair(p2X, p2Y), Pair(p3X, p3Y), Pair(p0X, p0Y)
            )
            val step = 3.0f
            for (side in 0..3) {
                val (x1, y1) = corners[side]
                val (x2, y2) = corners[side + 1]
                val len = hypot(x2 - x1, y2 - y1)
                val steps = maxOf(1, (len / step).toInt())
                for (i in 0 until steps) {
                    val t = i.toFloat() / steps.toFloat()
                    list.add(StrokePoint(x1 + (x2 - x1) * t, y1 + (y2 - y1) * t, basePressure, tOffset))
                    tOffset += 5L
                }
            }
            list.add(StrokePoint(p0X, p0Y, basePressure, tOffset))
            return list
        }

        override fun adjustWithDrag(dragPoint: StrokePoint): RecognizedShape {
            val dx = dragPoint.x - centerX
            val dy = dragPoint.y - centerY
            val curDist = hypot(dx, dy)
            if (curDist < 1e-2f) return this

            val curAngle = atan2(dy, dx)

            val baseAngle = initialDragAngle ?: curAngle
            val baseDist = initialDragDist ?: curDist.coerceAtLeast(10f)
            val baseRot = initialRotation

            val deltaAngle = curAngle - baseAngle
            val rawRot = baseRot + deltaAngle
            val snappedRot = snapRectangleAngle(rawRot)

            val scale = (curDist / baseDist).coerceIn(0.1f, 10.0f)
            var w = maxOf(16f, baseWidth * scale)
            var h = maxOf(16f, baseHeight * scale)
            if (isSquare) {
                val side = maxOf(w, h)
                w = side
                h = side
            }

            val uX = cos(snappedRot)
            val uY = sin(snappedRot)
            val vX = if (isClockwise) -sin(snappedRot) else sin(snappedRot)
            val vY = if (isClockwise) cos(snappedRot) else -cos(snappedRot)

            val hw = w * 0.5f
            val hh = h * 0.5f

            val nP0X = centerX - hw * uX - hh * vX
            val nP0Y = centerY - hw * uY - hh * vY
            val nP1X = centerX + hw * uX - hh * vX
            val nP1Y = centerY + hw * uY - hh * vY
            val nP2X = centerX + hw * uX + hh * vX
            val nP2Y = centerY + hw * uY + hh * vY
            val nP3X = centerX - hw * uX + hh * vX
            val nP3Y = centerY - hw * uY + hh * vY

            return copy(
                p0X = nP0X, p0Y = nP0Y,
                p1X = nP1X, p1Y = nP1Y,
                p2X = nP2X, p2Y = nP2Y,
                p3X = nP3X, p3Y = nP3Y,
                rotationRadians = snappedRot,
                baseWidth = baseWidth,
                baseHeight = baseHeight,
                centerX = centerX,
                centerY = centerY,
                initialDragAngle = baseAngle,
                initialDragDist = baseDist,
                initialRotation = baseRot,
                isClockwise = isClockwise
            )
        }
    }

    data class Triangle(
        val p0X: Float, val p0Y: Float,
        val p1X: Float, val p1Y: Float,
        val p2X: Float, val p2Y: Float
    ) : RecognizedShape() {
        override fun toStrokePoints(basePressure: Float, timestamp: Long): List<StrokePoint> {
            val list = ArrayList<StrokePoint>()
            var tOffset = timestamp
            val corners = listOf(
                Pair(p0X, p0Y), Pair(p1X, p1Y), Pair(p2X, p2Y), Pair(p0X, p0Y)
            )
            val step = 3.0f
            for (side in 0..2) {
                val (x1, y1) = corners[side]
                val (x2, y2) = corners[side + 1]
                val len = hypot(x2 - x1, y2 - y1)
                val steps = maxOf(1, (len / step).toInt())
                for (i in 0 until steps) {
                    val t = i.toFloat() / steps.toFloat()
                    list.add(StrokePoint(x1 + (x2 - x1) * t, y1 + (y2 - y1) * t, basePressure, tOffset))
                    tOffset += 5L
                }
            }
            list.add(StrokePoint(p0X, p0Y, basePressure, tOffset))
            return list
        }

        override fun adjustWithDrag(dragPoint: StrokePoint): RecognizedShape {
            return copy(p2X = dragPoint.x, p2Y = dragPoint.y)
        }
    }
}

object ShapeRecognizer {

    fun recognize(points: List<StrokePoint>): RecognizedShape? {
        if (points.size < 6) return null

        val n = points.size
        var arcLength = 0f
        for (i in 0 until n - 1) {
            arcLength += hypot(points[i + 1].x - points[i].x, points[i + 1].y - points[i].y)
        }
        if (arcLength < 25f) return null

        val pStart = points.first()
        val pEnd = points.last()
        val chordDist = hypot(pEnd.x - pStart.x, pEnd.y - pStart.y)

        // Check if user drew a straight line or arrow
        if (chordDist / arcLength >= 0.86f) {
            var maxDev = 0f
            for (i in 1 until n - 1) {
                val dev = perpendicularDistance(points[i].x, points[i].y, pStart.x, pStart.y, pEnd.x, pEnd.y)
                if (dev > maxDev) maxDev = dev
            }
            if (maxDev <= maxOf(20f, chordDist * 0.12f)) {
                val (snappedX, snappedY, snapped) = snapAngle(pStart.x, pStart.y, pEnd.x, pEnd.y)
                return RecognizedShape.Line(pStart.x, pStart.y, snappedX, snappedY, snapped)
            }
        }

        // Check for Arrow: Straight shaft with back-folding arrowhead barb near the end
        val arrowShape = checkArrow(points, arcLength)
        if (arrowShape != null) {
            return arrowShape
        }

        // Closed loop check (pStart and pEnd are close relative to perimeter)
        val loopClosingRatio = chordDist / arcLength
        if (loopClosingRatio <= 0.28f) {
            // 1. Polygon recognition: Rectangle, Square, Triangle via RDP (requires straight edges)
            val polygon = checkPolygon(points, arcLength)
            if (polygon != null) {
                return polygon
            }

            // 2. Centroid and radial variance for smooth curved loops
            var sumX = 0f
            var sumY = 0f
            for (pt in points) {
                sumX += pt.x
                sumY += pt.y
            }
            val cx = sumX / n.toFloat()
            val cy = sumY / n.toFloat()

            var sumR = 0f
            val rads = FloatArray(n)
            for (i in 0 until n) {
                val r = hypot(points[i].x - cx, points[i].y - cy)
                rads[i] = r
                sumR += r
            }
            val meanR = sumR / n.toFloat()
            if (meanR > 8f) {
                var variance = 0f
                for (r in rads) {
                    val d = r - meanR
                    variance += d * d
                }
                val stdDev = sqrt(variance / n.toFloat())
                val cov = stdDev / meanR

                // Circle recognition (low radial variance)
                if (cov <= 0.14f) {
                    return RecognizedShape.Circle(cx, cy, meanR)
                }

                // Ellipse recognition
                if (cov <= 0.32f) {
                    val ellipse = fitEllipse(points, cx, cy)
                    if (ellipse != null) {
                        return ellipse
                    }
                }
            }
        }

        return null
    }


    private fun checkArrow(points: List<StrokePoint>, arcLength: Float): RecognizedShape.Arrow? {
        val n = points.size
        if (n < 12) return null

        var maxDistIdx = 0
        var maxDist = 0f
        val p0 = points.first()
        for (i in 1 until n) {
            val d = hypot(points[i].x - p0.x, points[i].y - p0.y)
            if (d > maxDist) {
                maxDist = d
                maxDistIdx = i
            }
        }

        if (maxDistIdx < (n * 0.65).toInt() || maxDistIdx >= n - 2) return null
        val pTip = points[maxDistIdx]

        val shaftChord = hypot(pTip.x - p0.x, pTip.y - p0.y)
        if (shaftChord < 30f) return null

        var shaftArc = 0f
        for (i in 0 until maxDistIdx) {
            shaftArc += hypot(points[i + 1].x - points[i].x, points[i + 1].y - points[i].y)
        }
        if (shaftChord / shaftArc < 0.84f) return null

        val pAfter = points.last()
        val backDist = hypot(pAfter.x - pTip.x, pAfter.y - pTip.y)
        if (backDist < 8f || backDist > shaftChord * 0.50f) return null

        val shaftAngle = atan2(pTip.y - p0.y, pTip.x - p0.x)
        val barbAngle = atan2(pAfter.y - pTip.y, pAfter.x - pTip.x)
        var diff = abs(shaftAngle - barbAngle)
        if (diff > Math.PI.toFloat()) diff = 2.0f * Math.PI.toFloat() - diff

        if (diff >= (Math.PI * 0.55).toFloat()) {
            val headLen = minOf(backDist * 1.2f, shaftChord * 0.28f).coerceIn(12f, 40f)
            val (snappedTipX, snappedTipY, _) = snapAngle(p0.x, p0.y, pTip.x, pTip.y)
            return RecognizedShape.Arrow(p0.x, p0.y, snappedTipX, snappedTipY, headLen)
        }

        return null
    }

    private fun fitEllipse(points: List<StrokePoint>, cx: Float, cy: Float): RecognizedShape.Ellipse? {
        val n = points.size
        var mxx = 0.0
        var myy = 0.0
        var mxy = 0.0
        for (pt in points) {
            val dx = (pt.x - cx).toDouble()
            val dy = (pt.y - cy).toDouble()
            mxx += dx * dx
            myy += dy * dy
            mxy += dx * dy
        }
        mxx /= n.toDouble()
        myy /= n.toDouble()
        mxy /= n.toDouble()

        val delta = sqrt((mxx - myy) * (mxx - myy) + 4.0 * mxy * mxy)
        val l1 = (mxx + myy + delta) / 2.0
        val l2 = (mxx + myy - delta) / 2.0
        if (l1 <= 1e-4 || l2 <= 1e-4) return null

        val a = 2.0 * sqrt(l1)
        val b = 2.0 * sqrt(l2)
        if (b / a < 0.20) return null

        val angle = 0.5 * atan2(2.0 * mxy, mxx - myy)
        var angleSnapped = angle.toFloat()
        val deg = Math.toDegrees(angle).toFloat()
        if (abs(deg) <= 8f || abs(abs(deg) - 180f) <= 8f) angleSnapped = 0f
        else if (abs(abs(deg) - 90f) <= 8f) angleSnapped = (Math.PI / 2.0).toFloat()

        return RecognizedShape.Ellipse(cx, cy, a.toFloat(), b.toFloat(), angleSnapped)
    }

    private fun checkPolygon(points: List<StrokePoint>, arcLength: Float): RecognizedShape? {
        for (factor in listOf(0.04f, 0.06f, 0.08f)) {
            val epsilon = arcLength * factor
            val simplified = RdpFilter.simplify(points, epsilon)

            if (simplified.size == 4) {
                val v0 = simplified[0]
                val v1 = simplified[1]
                val v2 = simplified[2]
                val d01 = hypot(v1.x - v0.x, v1.y - v0.y)
                val d12 = hypot(v2.x - v1.x, v2.y - v1.y)
                val d20 = hypot(v0.x - v2.x, v0.y - v2.y)
                val minEdge = minOf(d01, d12, d20)
                if (minEdge >= arcLength * 0.12f && areEdgesStraight(points, simplified)) {
                    return RecognizedShape.Triangle(v0.x, v0.y, v1.x, v1.y, v2.x, v2.y)
                }
            }

            if (simplified.size == 5) {
                val v0 = simplified[0]
                val v1 = simplified[1]
                val v2 = simplified[2]
                val v3 = simplified[3]

                if (!areEdgesStraight(points, simplified)) {
                    continue
                }

                val a0 = cornerAngle(v3.x, v3.y, v0.x, v0.y, v1.x, v1.y)
                val a1 = cornerAngle(v0.x, v0.y, v1.x, v1.y, v2.x, v2.y)
                val a2 = cornerAngle(v1.x, v1.y, v2.x, v2.y, v3.x, v3.y)
                val a3 = cornerAngle(v2.x, v2.y, v3.x, v3.y, v0.x, v0.y)

                val rightAngleTol = 28f
                val isRect = abs(a0 - 90f) <= rightAngleTol &&
                        abs(a1 - 90f) <= rightAngleTol &&
                        abs(a2 - 90f) <= rightAngleTol &&
                        abs(a3 - 90f) <= rightAngleTol


                if (isRect) {
                    val cx = (v0.x + v1.x + v2.x + v3.x) * 0.25f
                    val cy = (v0.y + v1.y + v2.y + v3.y) * 0.25f

                    val e0Len = hypot(v1.x - v0.x, v1.y - v0.y)
                    val e1Len = hypot(v2.x - v1.x, v2.y - v1.y)
                    val e2Len = hypot(v3.x - v2.x, v3.y - v2.y)
                    val e3Len = hypot(v0.x - v3.x, v0.y - v3.y)

                    var w = maxOf(16f, (e0Len + e2Len) * 0.5f)
                    var h = maxOf(16f, (e1Len + e3Len) * 0.5f)

                    val isSquare = abs(w - h) / maxOf(w, h) <= 0.18f
                    if (isSquare) {
                        val side = (w + h) * 0.5f
                        w = side
                        h = side
                    }

                    // Calculate orientation angle of primary edge (v0 -> v1)
                    val dir01X = v1.x - v0.x
                    val dir01Y = v1.y - v0.y
                    val rawAngle = atan2(dir01Y, dir01X)
                    val snappedAngle = snapRectangleAngle(rawAngle)

                    // 2D cross product to detect clockwise vs counter-clockwise winding
                    val cross = (v1.x - v0.x) * (v2.y - v1.y) - (v1.y - v0.y) * (v2.x - v1.x)
                    val isClockwise = cross >= 0f

                    val uX = cos(snappedAngle)
                    val uY = sin(snappedAngle)
                    val vX = if (isClockwise) -sin(snappedAngle) else sin(snappedAngle)
                    val vY = if (isClockwise) cos(snappedAngle) else -cos(snappedAngle)

                    val hw = w * 0.5f
                    val hh = h * 0.5f

                    val p0X = cx - hw * uX - hh * vX
                    val p0Y = cy - hw * uY - hh * vY
                    val p1X = cx + hw * uX - hh * vX
                    val p1Y = cy + hw * uY - hh * vY
                    val p2X = cx + hw * uX + hh * vX
                    val p2Y = cy + hw * uY + hh * vY
                    val p3X = cx - hw * uX + hh * vX
                    val p3Y = cy - hw * uY + hh * vY

                    return RecognizedShape.Rectangle(
                        p0X = p0X, p0Y = p0Y,
                        p1X = p1X, p1Y = p1Y,
                        p2X = p2X, p2Y = p2Y,
                        p3X = p3X, p3Y = p3Y,
                        isSquare = isSquare,
                        rotationRadians = snappedAngle,
                        baseWidth = w,
                        baseHeight = h,
                        centerX = cx,
                        centerY = cy,
                        isClockwise = isClockwise
                    )
                }
            }
        }
        return null
    }

    private fun cornerAngle(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float): Float {
        val v1x = x1 - x2
        val v1y = y1 - y2
        val v2x = x3 - x2
        val v2y = y3 - y2
        val dot = v1x * v2x + v1y * v2y
        val len1 = hypot(v1x, v1y)
        val len2 = hypot(v2x, v2y)
        if (len1 < 1e-4f || len2 < 1e-4f) return 90f
        val cosVal = (dot / (len1 * len2)).coerceIn(-1.0f, 1.0f)
        return Math.toDegrees(acos(cosVal.toDouble())).toFloat()
    }

    private fun areEdgesStraight(points: List<StrokePoint>, corners: List<StrokePoint>): Boolean {
        var startIdx = 0
        for (c in 0 until corners.size - 1) {
            val c1 = corners[c]
            val c2 = corners[c + 1]
            val edgeLen = hypot(c2.x - c1.x, c2.y - c1.y)
            if (edgeLen < 5f) continue

            var i1 = -1
            var i2 = -1
            for (pIdx in startIdx until points.size) {
                if (i1 == -1 && points[pIdx] === c1) {
                    i1 = pIdx
                } else if (i1 != -1 && points[pIdx] === c2) {
                    i2 = pIdx
                    break
                }
            }
            if (i1 == -1) i1 = startIdx
            if (i2 == -1) i2 = minOf(points.size - 1, i1 + 5)
            startIdx = i2

            var maxDev = 0f
            for (pIdx in i1..i2) {
                val pt = points[pIdx]
                val dev = perpendicularDistance(pt.x, pt.y, c1.x, c1.y, c2.x, c2.y)
                if (dev > maxDev) maxDev = dev
            }

            if (maxDev > maxOf(10f, edgeLen * 0.10f)) {
                return false
            }
        }
        return true
    }
}

internal fun perpendicularDistance(px: Float, py: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
    val dx = x2 - x1
    val dy = y2 - y1
    val lenSq = dx * dx + dy * dy
    if (lenSq < 1e-6f) return hypot(px - x1, py - y1)
    return abs(dx * (y1 - py) - (x1 - px) * dy) / sqrt(lenSq)
}

internal fun snapAngle(x1: Float, y1: Float, x2: Float, y2: Float): Triple<Float, Float, Boolean> {
    val dx = x2 - x1
    val dy = y2 - y1
    val dist = hypot(dx, dy)
    if (dist < 1e-3f) return Triple(x2, y2, false)

    val angleRad = atan2(dy, dx)
    var angleDeg = Math.toDegrees(angleRad.toDouble()).toFloat()
    if (angleDeg < 0f) angleDeg += 360f

    // Check horizontal (0, 180, 360)
    val tol = 7.0f
    if (angleDeg <= tol || angleDeg >= 360f - tol) {
        return Triple(x1 + dist, y1, true)
    }
    if (abs(angleDeg - 180f) <= tol) {
        return Triple(x1 - dist, y1, true)
    }

    // Check vertical (90, 270)
    if (abs(angleDeg - 90f) <= tol) {
        return Triple(x1, y1 + dist, true)
    }
    if (abs(angleDeg - 270f) <= tol) {
        return Triple(x1, y1 - dist, true)
    }

    // Check 45-degree diagonals (45, 135, 225, 315)
    val diagTol = 6.0f
    for (targetDeg in listOf(45f, 135f, 225f, 315f)) {
        if (abs(angleDeg - targetDeg) <= diagTol) {
            val rad = Math.toRadians(targetDeg.toDouble()).toFloat()
            return Triple(x1 + dist * cos(rad), y1 + dist * sin(rad), true)
        }
    }

    return Triple(x2, y2, false)
}

internal fun snapRectangleAngle(rawAngleRad: Float): Float {
    var deg = Math.toDegrees(rawAngleRad.toDouble()).toFloat() % 360f
    if (deg < 0f) deg += 360f

    val tol = 7.0f
    val diagTol = 5.0f

    // 0 / 360 deg
    if (deg <= tol || deg >= 360f - tol) return 0f
    // 90 deg
    if (abs(deg - 90f) <= tol) return (Math.PI / 2.0).toFloat()
    // 180 deg
    if (abs(deg - 180f) <= tol) return Math.PI.toFloat()
    // 270 deg
    if (abs(deg - 270f) <= tol) return (3.0 * Math.PI / 2.0).toFloat()

    // 45 deg
    if (abs(deg - 45f) <= diagTol) return (Math.PI / 4.0).toFloat()
    // 135 deg
    if (abs(deg - 135f) <= diagTol) return (3.0 * Math.PI / 4.0).toFloat()
    // 225 deg
    if (abs(deg - 225f) <= diagTol) return (5.0 * Math.PI / 4.0).toFloat()
    // 315 deg
    if (abs(deg - 315f) <= diagTol) return (7.0 * Math.PI / 4.0).toFloat()

    return rawAngleRad
}


