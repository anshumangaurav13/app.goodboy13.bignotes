package app.goodboy13.bignotes.core.geometry

import app.goodboy13.bignotes.core.model.PencilTone
import app.goodboy13.bignotes.core.model.StrokePoint
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

class TessellatedMesh(
    val vertexBuffer: FloatBuffer,
    val vertexCount: Int
)

class CachedStrokeMesh(
    val data: FloatArray,
    val vertexCount: Int
)

/**
 * Generates equidistant dabs along stroke arc-length.
 */
class DabGenerator(private val stepDistance: Float) {
    private var lastX = 0f
    private var lastY = 0f
    private var lastPressure = 0f
    private var distToNextDab = 0f
    private var hasStarted = false

    fun start(x: Float, y: Float, pressure: Float): List<StrokePoint> {
        lastX = x
        lastY = y
        lastPressure = pressure
        distToNextDab = stepDistance
        hasStarted = true
        return listOf(StrokePoint(x, y, pressure, 0L))
    }

    fun startRaw(x: Float, y: Float, pressure: Float, out: FloatArray, startOffset: Int = 0): Int {
        lastX = x
        lastY = y
        lastPressure = pressure
        distToNextDab = stepDistance
        hasStarted = true
        if (startOffset + 3 <= out.size) {
            out[startOffset] = x
            out[startOffset + 1] = y
            out[startOffset + 2] = pressure
            return 1
        }
        return 0
    }

    fun addPoint(x: Float, y: Float, pressure: Float): List<StrokePoint> {
        if (!hasStarted) return start(x, y, pressure)

        val dx = x - lastX
        val dy = y - lastY
        val segDist = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        if (segDist < 1e-4f) return emptyList()

        val dabs = ArrayList<StrokePoint>()
        var curDist = distToNextDab

        while (curDist <= segDist) {
            val t = curDist / segDist
            val dabX = lastX + dx * t
            val dabY = lastY + dy * t
            val dabP = lastPressure + (pressure - lastPressure) * t
            dabs.add(StrokePoint(dabX, dabY, dabP, 0L))
            curDist += stepDistance
        }

        distToNextDab = curDist - segDist
        lastX = x
        lastY = y
        lastPressure = pressure
        return dabs
    }

    fun addPointRaw(x: Float, y: Float, pressure: Float, out: FloatArray, startOffset: Int = 0): Int {
        if (!hasStarted) return startRaw(x, y, pressure, out, startOffset)

        val dx = x - lastX
        val dy = y - lastY
        val segDist = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        if (segDist < 1e-4f) return 0

        val invSeg = 1.0f / segDist
        var curDist = distToNextDab
        var offset = startOffset
        var addedCount = 0

        while (curDist <= segDist) {
            if (offset + 3 > out.size) break
            val t = curDist * invSeg
            val dabX = lastX + dx * t
            val dabY = lastY + dy * t
            val dabP = lastPressure + (pressure - lastPressure) * t
            out[offset] = dabX
            out[offset + 1] = dabY
            out[offset + 2] = dabP
            offset += 3
            addedCount++
            curDist += stepDistance
        }

        distToNextDab = curDist - segDist
        lastX = x
        lastY = y
        lastPressure = pressure
        return addedCount
    }
}

/**
 * Generates non-overlapping tapered capsule segments incrementally during live front-buffer strokes.
 * Packed stride is [FLOATS_PER_SEGMENT] = 16 floats:
 * [x0, y0, p0, tilt0, orient0, speed0, x1, y1, p1, tilt1, orient1, speed1, m0x, m0y, m1x, m1y]
 */
class LiveSegmentGenerator {
    companion object {
        const val FLOATS_PER_SEGMENT = 16
    }

    private var hasStarted = false
    private var prevPt: StrokePoint? = null
    private var hasPrevTangent = false
    private var prevTx = 0f
    private var prevTy = 0f

    fun startRaw(pt: StrokePoint, out: FloatArray, startOffset: Int = 0): Int {
        hasStarted = true
        prevPt = pt
        hasPrevTangent = false
        prevTx = 0f
        prevTy = 0f
        if (startOffset + FLOATS_PER_SEGMENT > out.size) return 0
        writeSegment(out, startOffset, pt, pt, 0f, 0f, 0f, 0f)
        return 1
    }

    fun addPointRaw(pt: StrokePoint, out: FloatArray, startOffset: Int = 0, isFinal: Boolean = false): Int {
        val prev = prevPt ?: return startRaw(pt, out, startOffset)
        val dx = pt.x - prev.x
        val dy = pt.y - prev.y
        val len = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        if (len < 0.15f) return 0
        if (startOffset + FLOATS_PER_SEGMENT > out.size) return 0

        val tx = dx / len
        val ty = dy / len

        // Joint trim at prev: use incoming tangent if previous segment exists,
        // otherwise bisect against first tangent so touchdown dot meets cleanly.
        val m0x = if (hasPrevTangent) prevTx else tx
        val m0y = if (hasPrevTangent) prevTy else ty
        val m1x = if (isFinal) 0f else tx
        val m1y = if (isFinal) 0f else ty

        writeSegment(out, startOffset, prev, pt, m0x, m0y, m1x, m1y)

        prevPt = pt
        prevTx = tx
        prevTy = ty
        hasPrevTangent = true
        return 1
    }

    private fun writeSegment(
        out: FloatArray,
        offset: Int,
        p0: StrokePoint,
        p1: StrokePoint,
        m0x: Float,
        m0y: Float,
        m1x: Float,
        m1y: Float
    ) {
        out[offset] = p0.x
        out[offset + 1] = p0.y
        out[offset + 2] = p0.pressure
        out[offset + 3] = p0.tilt
        out[offset + 4] = p0.orientation
        out[offset + 5] = p0.speed

        out[offset + 6] = p1.x
        out[offset + 7] = p1.y
        out[offset + 8] = p1.pressure
        out[offset + 9] = p1.tilt
        out[offset + 10] = p1.orientation
        out[offset + 11] = p1.speed

        out[offset + 12] = m0x
        out[offset + 13] = m0y
        out[offset + 14] = m1x
        out[offset + 15] = m1y
    }
}

object StrokeTessellator {
    // 22 floats per vertex:
    // a_position (2), a_segStart (2), a_segEnd (2), a_radii (4),
    // a_color (4), a_tone (1), a_trims (4), a_nib (3)
    const val FLOATS_PER_VERTEX = 22
    const val STRIDE_BYTES = FLOATS_PER_VERTEX * 4
    var activeInkConfig: app.goodboy13.bignotes.core.model.InkBleedConfig = app.goodboy13.bignotes.core.model.InkBleedConfig.DEFAULT

    fun getStepDistance(baseWidth: Float, isInk: Boolean = false, isHighlighter: Boolean = false): Float {
        if (isHighlighter) {
            val nominalRadius = maxOf(5.0f, baseWidth * 2.5f)
            return maxOf(3.0f, nominalRadius * 0.35f)
        } else if (isInk) {
            val effRadius = maxOf(0.6f, baseWidth * 0.25f)
            val step = effRadius * 0.35f
            return maxOf(0.28f, minOf(step, 1.6f))
        } else {
            val effRadius = maxOf(0.7f, baseWidth * 0.30f)
            val step = effRadius * 0.35f
            return maxOf(0.30f, minOf(step, 1.8f))
        }
    }

    private fun getSplineStep(baseWidth: Float, isInk: Boolean, isHighlighter: Boolean): Float {
        return when {
            isHighlighter -> maxOf(3.0f, baseWidth * 0.65f)
            isInk -> maxOf(1.5f, baseWidth * 0.35f).coerceAtMost(6.0f)
            else -> maxOf(1.6f, baseWidth * 0.38f).coerceAtMost(6.5f)
        }
    }

    /**
     * Generates arc-length parameterized dabs from raw stroke points.
     */
    fun generateDabs(points: List<StrokePoint>, baseWidth: Float, isInk: Boolean = false, isHighlighter: Boolean = false): List<StrokePoint> {
        if (points.isEmpty()) return emptyList()
        val stepDistance = getStepDistance(baseWidth, isInk, isHighlighter)

        val smoothedPoints = if (points.size >= 3) {
            val stepDist = if (isHighlighter) maxOf(2.0f, baseWidth * 0.60f) else maxOf(0.8f, baseWidth * 0.20f)
            CatmullRomSpline.interpolate(points, stepDistance = stepDist)
        } else {
            points
        }

        val gen = DabGenerator(stepDistance)
        val dabs = ArrayList<StrokePoint>()
        dabs.addAll(gen.start(smoothedPoints[0].x, smoothedPoints[0].y, smoothedPoints[0].pressure))
        for (i in 1 until smoothedPoints.size) {
            val pt = smoothedPoints[i]
            dabs.addAll(gen.addPoint(pt.x, pt.y, pt.pressure))
        }
        return dabs
    }

    /**
     * Pressure size and thinning behavior for fountain pen.
     */
    fun getInkRadius(pressure: Float, baseWidth: Float): Float {
        val p = pressure.coerceIn(0.0f, 1.0f)
        val pressureCurve = p * p * 0.45f + p * 0.55f
        val radiusFactor = 0.30f + 0.70f * pressureCurve
        val baseRadius = baseWidth * 0.5f
        val minRadius = maxOf(0.4f, baseRadius * 0.30f)
        return maxOf(minRadius, baseRadius * radiusFactor)
    }

    fun getGraphiteDensity(colorRgb: Long): Float {
        val r = (((colorRgb shr 16) and 0xFF).toFloat()) / 255f
        val g = (((colorRgb shr 8) and 0xFF).toFloat()) / 255f
        val b = ((colorRgb and 0xFF).toFloat()) / 255f
        val lightness = 0.299f * r + 0.587f * g + 0.114f * b
        return (1.0f - lightness).coerceIn(0.0f, 1.0f)
    }

    fun resolveColor(tone: PencilTone, colorRgb: Long, isInk: Boolean, isHighlighter: Boolean = false): QuadColor {
        val aRaw = (((colorRgb shr 24) and 0xFF).toFloat()) / 255f
        val opacityMultiplier = if (aRaw > 0f) aRaw else 1.0f
        val r = (((colorRgb shr 16) and 0xFF).toFloat()) / 255f
        val g = (((colorRgb shr 8) and 0xFF).toFloat()) / 255f
        val b = ((colorRgb and 0xFF).toFloat()) / 255f
        if (isHighlighter) {
            return QuadColor(r, g, b, 1.0f)
        }
        if (isInk) {
            return QuadColor(r, g, b, 1.0f)
        }
        val lightness = 0.299f * r + 0.587f * g + 0.114f * b
        val density = (1.0f - lightness).coerceIn(0.0f, 1.0f)
        // Calibrated for single-pass non-overlapping capsule SDFs (preserving multi-stroke buildup)
        val baseAlpha = (0.56f + 0.28f * density) * opacityMultiplier
        return QuadColor(r, g, b, baseAlpha)
    }

    private fun computeRadiiAlphaTone(
        pressure: Float,
        speed: Float,
        tilt: Float,
        baseWidth: Float,
        baseAlpha: Float,
        graphiteToneId: Float,
        isInk: Boolean,
        isHighlighter: Boolean,
        quadMarginRatio: Float
    ): Triple<Float, Float, Float> {
        return when {
            isHighlighter -> {
                val nominalR = (baseWidth * 2.5f * (0.88f + 0.12f * pressure)).coerceAtLeast(6.0f)
                Triple(nominalR, nominalR, 5.5f)
            }
            isInk -> {
                val nominalR = getInkRadius(pressure, baseWidth)
                val anisoMarginScale = 1.0f + 0.20f * tilt.coerceIn(0f, 1f)
                val extraMargin = maxOf(nominalR * quadMarginRatio, 0.75f)
                val quadR = (nominalR + extraMargin) * anisoMarginScale
                Triple(quadR, nominalR, 3.5f)
            }
            else -> {
                val p = pressure.coerceIn(0.0f, 1.0f)
                val nominalR = (baseWidth * (0.32f + 0.68f * p) * 0.5f).coerceAtLeast(0.8f)
                Triple(nominalR, nominalR, graphiteToneId)
            }
        }
    }

    private fun computePointAlpha(
        pressure: Float,
        speed: Float,
        baseAlpha: Float,
        isInk: Boolean,
        isHighlighter: Boolean
    ): Float {
        return when {
            isHighlighter -> baseAlpha
            isInk -> {
                // Velocity dwell factor: slow corners/terminations pool slightly darker, fast strokes crisp
                val dwell = 1.14f - 0.24f * speed.coerceIn(0.0f, 1.0f)
                dwell.coerceIn(0.86f, 1.18f)
            }
            else -> {
                val p = pressure.coerceIn(0.0f, 1.0f)
                (baseAlpha * (0.20f + 0.80f * p)).coerceIn(0.06f, 0.90f)
            }
        }
    }

    private fun emitSegmentQuadArray(
        arr: FloatArray,
        startOffset: Int,
        p0: StrokePoint,
        p1: StrokePoint,
        m0x: Float, m0y: Float,
        m1x: Float, m1y: Float,
        baseWidth: Float,
        r: Float, g: Float, b: Float, baseAlpha: Float,
        graphiteToneId: Float,
        isInk: Boolean,
        isHighlighter: Boolean,
        quadMarginRatio: Float
    ): Int {
        val (R0, rNom0, tone0) = computeRadiiAlphaTone(p0.pressure, p0.speed, p0.tilt, baseWidth, baseAlpha, graphiteToneId, isInk, isHighlighter, quadMarginRatio)
        val (R1, rNom1, _) = computeRadiiAlphaTone(p1.pressure, p1.speed, p1.tilt, baseWidth, baseAlpha, graphiteToneId, isInk, isHighlighter, quadMarginRatio)
        val avgAlpha = 0.5f * (
            computePointAlpha(p0.pressure, p0.speed, baseAlpha, isInk, isHighlighter) +
                computePointAlpha(p1.pressure, p1.speed, baseAlpha, isInk, isHighlighter)
            )
        val maxTilt = maxOf(p0.tilt, p1.tilt).coerceIn(0f, 1f)
        val avgOrient = if (p0.tilt > 0.005f || p1.tilt > 0.005f) {
            val vx = p0.tilt * cos(p0.orientation) + p1.tilt * cos(p1.orientation)
            val vy = p0.tilt * sin(p0.orientation) + p1.tilt * sin(p1.orientation)
            val m = hypot(vx.toDouble(), vy.toDouble()).toFloat()
            if (m > 1e-5f) Pair(vx / m, vy / m) else Pair(1f, 0f)
        } else {
            Pair(1f, 0f)
        }
        val anisoPad = if (isHighlighter) 1.0f else if (isInk) (1.0f + 0.22f * maxTilt) else (1.0f + 1.25f * maxTilt)
        val quadPad = maxOf(R0, R1) * anisoPad + 0.5f

        val dx = p1.x - p0.x
        val dy = p1.y - p0.y
        val len = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        val ux: Float
        val uy: Float
        if (len > 1e-4f) {
            ux = dx / len
            uy = dy / len
        } else {
            ux = 1f
            uy = 0f
        }
        val nx = -uy
        val ny = ux

        val c0x = p0.x - ux * quadPad - nx * quadPad
        val c0y = p0.y - uy * quadPad - ny * quadPad
        val c1x = p1.x + ux * quadPad - nx * quadPad
        val c1y = p1.y + uy * quadPad - ny * quadPad
        val c2x = p1.x + ux * quadPad + nx * quadPad
        val c2y = p1.y + uy * quadPad + ny * quadPad
        val c3x = p0.x - ux * quadPad + nx * quadPad
        val c3y = p0.y - uy * quadPad + ny * quadPad

        var off = startOffset
        off = putVertexArray(arr, off, c0x, c0y, p0.x, p0.y, p1.x, p1.y, R0, R1, rNom0, rNom1, r, g, b, avgAlpha, tone0, m0x, m0y, m1x, m1y, avgOrient.first, avgOrient.second, if (isHighlighter) 0f else maxTilt)
        off = putVertexArray(arr, off, c1x, c1y, p0.x, p0.y, p1.x, p1.y, R0, R1, rNom0, rNom1, r, g, b, avgAlpha, tone0, m0x, m0y, m1x, m1y, avgOrient.first, avgOrient.second, if (isHighlighter) 0f else maxTilt)
        off = putVertexArray(arr, off, c2x, c2y, p0.x, p0.y, p1.x, p1.y, R0, R1, rNom0, rNom1, r, g, b, avgAlpha, tone0, m0x, m0y, m1x, m1y, avgOrient.first, avgOrient.second, if (isHighlighter) 0f else maxTilt)

        off = putVertexArray(arr, off, c0x, c0y, p0.x, p0.y, p1.x, p1.y, R0, R1, rNom0, rNom1, r, g, b, avgAlpha, tone0, m0x, m0y, m1x, m1y, avgOrient.first, avgOrient.second, if (isHighlighter) 0f else maxTilt)
        off = putVertexArray(arr, off, c2x, c2y, p0.x, p0.y, p1.x, p1.y, R0, R1, rNom0, rNom1, r, g, b, avgAlpha, tone0, m0x, m0y, m1x, m1y, avgOrient.first, avgOrient.second, if (isHighlighter) 0f else maxTilt)
        off = putVertexArray(arr, off, c3x, c3y, p0.x, p0.y, p1.x, p1.y, R0, R1, rNom0, rNom1, r, g, b, avgAlpha, tone0, m0x, m0y, m1x, m1y, avgOrient.first, avgOrient.second, if (isHighlighter) 0f else maxTilt)
        return off
    }

    private fun emitSegmentQuadBuffer(
        buf: FloatBuffer,
        p0: StrokePoint,
        p1: StrokePoint,
        m0x: Float, m0y: Float,
        m1x: Float, m1y: Float,
        baseWidth: Float,
        r: Float, g: Float, b: Float, baseAlpha: Float,
        graphiteToneId: Float,
        isInk: Boolean,
        isHighlighter: Boolean,
        quadMarginRatio: Float
    ) {
        val (R0, rNom0, tone0) = computeRadiiAlphaTone(p0.pressure, p0.speed, p0.tilt, baseWidth, baseAlpha, graphiteToneId, isInk, isHighlighter, quadMarginRatio)
        val (R1, rNom1, _) = computeRadiiAlphaTone(p1.pressure, p1.speed, p1.tilt, baseWidth, baseAlpha, graphiteToneId, isInk, isHighlighter, quadMarginRatio)
        val avgAlpha = 0.5f * (
            computePointAlpha(p0.pressure, p0.speed, baseAlpha, isInk, isHighlighter) +
                computePointAlpha(p1.pressure, p1.speed, baseAlpha, isInk, isHighlighter)
            )
        val maxTilt = if (isHighlighter) 0f else maxOf(p0.tilt, p1.tilt).coerceIn(0f, 1f)
        val avgOrient = if (!isHighlighter && (p0.tilt > 0.005f || p1.tilt > 0.005f)) {
            val vx = p0.tilt * cos(p0.orientation) + p1.tilt * cos(p1.orientation)
            val vy = p0.tilt * sin(p0.orientation) + p1.tilt * sin(p1.orientation)
            val m = hypot(vx.toDouble(), vy.toDouble()).toFloat()
            if (m > 1e-5f) Pair(vx / m, vy / m) else Pair(1f, 0f)
        } else {
            Pair(1f, 0f)
        }
        val anisoPad = if (isHighlighter) 1.0f else if (isInk) (1.0f + 0.22f * maxTilt) else (1.0f + 1.25f * maxTilt)
        val quadPad = maxOf(R0, R1) * anisoPad + 0.5f

        val dx = p1.x - p0.x
        val dy = p1.y - p0.y
        val len = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        val ux: Float
        val uy: Float
        if (len > 1e-4f) {
            ux = dx / len
            uy = dy / len
        } else {
            ux = 1f
            uy = 0f
        }
        val nx = -uy
        val ny = ux

        val c0x = p0.x - ux * quadPad - nx * quadPad
        val c0y = p0.y - uy * quadPad - ny * quadPad
        val c1x = p1.x + ux * quadPad - nx * quadPad
        val c1y = p1.y + uy * quadPad - ny * quadPad
        val c2x = p1.x + ux * quadPad + nx * quadPad
        val c2y = p1.y + uy * quadPad + ny * quadPad
        val c3x = p0.x - ux * quadPad + nx * quadPad
        val c3y = p0.y - uy * quadPad + ny * quadPad

        putVertexBuffer(buf, c0x, c0y, p0.x, p0.y, p1.x, p1.y, R0, R1, rNom0, rNom1, r, g, b, avgAlpha, tone0, m0x, m0y, m1x, m1y, avgOrient.first, avgOrient.second, maxTilt)
        putVertexBuffer(buf, c1x, c1y, p0.x, p0.y, p1.x, p1.y, R0, R1, rNom0, rNom1, r, g, b, avgAlpha, tone0, m0x, m0y, m1x, m1y, avgOrient.first, avgOrient.second, maxTilt)
        putVertexBuffer(buf, c2x, c2y, p0.x, p0.y, p1.x, p1.y, R0, R1, rNom0, rNom1, r, g, b, avgAlpha, tone0, m0x, m0y, m1x, m1y, avgOrient.first, avgOrient.second, maxTilt)

        putVertexBuffer(buf, c0x, c0y, p0.x, p0.y, p1.x, p1.y, R0, R1, rNom0, rNom1, r, g, b, avgAlpha, tone0, m0x, m0y, m1x, m1y, avgOrient.first, avgOrient.second, maxTilt)
        putVertexBuffer(buf, c2x, c2y, p0.x, p0.y, p1.x, p1.y, R0, R1, rNom0, rNom1, r, g, b, avgAlpha, tone0, m0x, m0y, m1x, m1y, avgOrient.first, avgOrient.second, maxTilt)
        putVertexBuffer(buf, c3x, c3y, p0.x, p0.y, p1.x, p1.y, R0, R1, rNom0, rNom1, r, g, b, avgAlpha, tone0, m0x, m0y, m1x, m1y, avgOrient.first, avgOrient.second, maxTilt)
    }

    /**
     * Fast stroke tessellation into a FloatArray of tapered capsule segments.
     */
    fun tessellateToCachedMesh(
        points: List<StrokePoint>,
        tone: PencilTone,
        baseWidth: Float,
        colorRgb: Long = tone.colorRgb,
        isInk: Boolean = tone.isInk,
        isHighlighter: Boolean = false
    ): CachedStrokeMesh {
        if (points.isEmpty()) return CachedStrokeMesh(FloatArray(0), 0)

        // Native Rust fast-path: zero GC churn, SIMD accelerated
        if (NativeStrokeTessellator.isAvailable) {
            val flatPoints = FloatArray(points.size * NativeStrokeTessellator.FLOATS_PER_INPUT_POINT)
            var pIdx = 0
            for (i in points.indices) {
                val pt = points[i]
                flatPoints[pIdx++] = pt.x
                flatPoints[pIdx++] = pt.y
                flatPoints[pIdx++] = pt.pressure
                flatPoints[pIdx++] = pt.tilt
                flatPoints[pIdx++] = pt.orientation
                flatPoints[pIdx++] = pt.speed
            }
            val nativeData = NativeStrokeTessellator.nativeTessellateStroke(
                flatPoints,
                points.size,
                baseWidth,
                colorRgb,
                isInk,
                isHighlighter,
                activeInkConfig.quadMarginRatio
            )
            if (nativeData != null && nativeData.isNotEmpty()) {
                val vertexCount = nativeData.size / FLOATS_PER_VERTEX
                return CachedStrokeMesh(nativeData, vertexCount)
            }
        }

        val smoothed = if (points.size >= 3) {
            CatmullRomSpline.interpolate(points, stepDistance = getSplineStep(baseWidth, isInk, isHighlighter))
        } else {
            points
        }

        // Deduplicate near-identical consecutive spline points so segment tangents stay well-defined
        val cleaned = ArrayList<StrokePoint>(smoothed.size)
        cleaned.add(smoothed[0])
        for (i in 1 until smoothed.size) {
            val prev = cleaned[cleaned.size - 1]
            val cur = smoothed[i]
            if (hypot((cur.x - prev.x).toDouble(), (cur.y - prev.y).toDouble()) >= 0.15) {
                cleaned.add(cur)
            }
        }

        val graphiteToneId = if (isHighlighter) 5.5f else if (isInk) 3.5f else getGraphiteDensity(colorRgb)
        val (r, g, b, baseAlpha) = resolveColor(tone, colorRgb, isInk = isInk, isHighlighter = isHighlighter)
        val quadMargin = activeInkConfig.quadMarginRatio

        if (cleaned.size == 1) {
            val data = FloatArray(6 * FLOATS_PER_VERTEX)
            emitSegmentQuadArray(
                data, 0, cleaned[0], cleaned[0], 0f, 0f, 0f, 0f,
                baseWidth, r, g, b, baseAlpha, graphiteToneId, isInk, isHighlighter, quadMargin
            )
            return CachedStrokeMesh(data, 6)
        }

        val segCount = cleaned.size - 1
        val tangentsX = FloatArray(segCount)
        val tangentsY = FloatArray(segCount)
        for (i in 0 until segCount) {
            val dx = cleaned[i + 1].x - cleaned[i].x
            val dy = cleaned[i + 1].y - cleaned[i].y
            val len = hypot(dx.toDouble(), dy.toDouble()).toFloat().coerceAtLeast(1e-4f)
            tangentsX[i] = dx / len
            tangentsY[i] = dy / len
        }

        val mitersX = FloatArray(cleaned.size)
        val mitersY = FloatArray(cleaned.size)
        for (j in 1 until cleaned.size - 1) {
            val sx = tangentsX[j - 1] + tangentsX[j]
            val sy = tangentsY[j - 1] + tangentsY[j]
            val mLen = hypot(sx.toDouble(), sy.toDouble()).toFloat()
            if (mLen > 1e-4f) {
                mitersX[j] = sx / mLen
                mitersY[j] = sy / mLen
            } else {
                mitersX[j] = tangentsX[j]
                mitersY[j] = tangentsY[j]
            }
        }

        val totalVertices = segCount * 6
        val data = FloatArray(totalVertices * FLOATS_PER_VERTEX)
        var offset = 0
        for (i in 0 until segCount) {
            offset = emitSegmentQuadArray(
                data, offset,
                cleaned[i], cleaned[i + 1],
                mitersX[i], mitersY[i],
                mitersX[i + 1], mitersY[i + 1],
                baseWidth, r, g, b, baseAlpha, graphiteToneId, isInk, isHighlighter, quadMargin
            )
        }
        return CachedStrokeMesh(data, totalVertices)
    }

    /**
     * Tessellates an incremental sequence of live front-buffer capsule segments packed at
     * [LiveSegmentGenerator.FLOATS_PER_SEGMENT] stride.
     */
    fun tessellateRawSegments(
        rawSegments: FloatArray,
        segmentCount: Int,
        tone: PencilTone,
        baseWidth: Float,
        targetBuffer: FloatBuffer? = null,
        colorRgb: Long = tone.colorRgb,
        isInk: Boolean = tone.isInk,
        isHighlighter: Boolean = false
    ): TessellatedMesh {
        if (segmentCount <= 0) {
            val emptyBuffer = targetBuffer ?: ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder()).asFloatBuffer()
            emptyBuffer.position(0)
            return TessellatedMesh(emptyBuffer, 0)
        }

        val graphiteToneId = if (isHighlighter) 5.5f else if (isInk) 3.5f else getGraphiteDensity(colorRgb)
        val (r, g, b, baseAlpha) = resolveColor(tone, colorRgb, isInk = isInk, isHighlighter = isHighlighter)
        val quadMargin = activeInkConfig.quadMarginRatio

        val totalVertices = segmentCount * 6
        val floatCount = totalVertices * FLOATS_PER_VERTEX
        val floatBuffer = if (targetBuffer != null && targetBuffer.capacity() >= floatCount) {
            targetBuffer.clear()
            targetBuffer
        } else {
            ByteBuffer.allocateDirect(floatCount * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        }

        var segOff = 0
        for (i in 0 until segmentCount) {
            val p0 = StrokePoint(
                x = rawSegments[segOff],
                y = rawSegments[segOff + 1],
                pressure = rawSegments[segOff + 2],
                timeMillis = 0L,
                tilt = rawSegments[segOff + 3],
                orientation = rawSegments[segOff + 4],
                speed = rawSegments[segOff + 5]
            )
            val p1 = StrokePoint(
                x = rawSegments[segOff + 6],
                y = rawSegments[segOff + 7],
                pressure = rawSegments[segOff + 8],
                timeMillis = 0L,
                tilt = rawSegments[segOff + 9],
                orientation = rawSegments[segOff + 10],
                speed = rawSegments[segOff + 11]
            )
            val m0x = rawSegments[segOff + 12]
            val m0y = rawSegments[segOff + 13]
            val m1x = rawSegments[segOff + 14]
            val m1y = rawSegments[segOff + 15]
            segOff += LiveSegmentGenerator.FLOATS_PER_SEGMENT

            emitSegmentQuadBuffer(
                floatBuffer, p0, p1, m0x, m0y, m1x, m1y,
                baseWidth, r, g, b, baseAlpha, graphiteToneId, isInk, isHighlighter, quadMargin
            )
        }

        floatBuffer.limit(floatCount)
        floatBuffer.position(0)
        return TessellatedMesh(floatBuffer, totalVertices)
    }

    /**
     * Tessellates a StrokePoint list (e.g., QuickShape preview) into capsule segments.
     */
    fun tessellateDabs(
        dabs: List<StrokePoint>,
        tone: PencilTone,
        baseWidth: Float,
        targetBuffer: FloatBuffer? = null,
        colorRgb: Long = tone.colorRgb,
        isInk: Boolean = tone.isInk,
        isHighlighter: Boolean = false
    ): TessellatedMesh {
        if (dabs.isEmpty()) {
            val emptyBuffer = targetBuffer ?: ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder()).asFloatBuffer()
            emptyBuffer.position(0)
            return TessellatedMesh(emptyBuffer, 0)
        }

        val meshData = tessellateToCachedMesh(dabs, tone, baseWidth, colorRgb, isInk, isHighlighter)
        val floatBuffer = if (targetBuffer != null && targetBuffer.capacity() >= meshData.data.size) {
            targetBuffer.clear()
            targetBuffer
        } else {
            ByteBuffer.allocateDirect(meshData.data.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        }
        floatBuffer.put(meshData.data, 0, meshData.data.size)
        floatBuffer.limit(meshData.data.size)
        floatBuffer.position(0)
        return TessellatedMesh(floatBuffer, meshData.vertexCount)
    }

    /**
     * Tessellates raw flat float dabs ([x, y, pressure] strided) as capsule dots.
     */
    fun tessellateRawDabs(
        rawDabs: FloatArray,
        dabCount: Int,
        tone: PencilTone,
        baseWidth: Float,
        targetBuffer: FloatBuffer? = null,
        colorRgb: Long = tone.colorRgb,
        isInk: Boolean = tone.isInk,
        isHighlighter: Boolean = false
    ): TessellatedMesh {
        if (dabCount == 0) {
            val emptyBuffer = targetBuffer ?: ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder()).asFloatBuffer()
            emptyBuffer.position(0)
            return TessellatedMesh(emptyBuffer, 0)
        }

        val graphiteToneId = if (isHighlighter) 5.5f else if (isInk) 3.5f else getGraphiteDensity(colorRgb)
        val (r, g, b, baseAlpha) = resolveColor(tone, colorRgb, isInk = isInk, isHighlighter = isHighlighter)
        val quadMargin = activeInkConfig.quadMarginRatio

        val totalVertices = dabCount * 6
        val floatCount = totalVertices * FLOATS_PER_VERTEX
        val floatBuffer = if (targetBuffer != null && targetBuffer.capacity() >= floatCount) {
            targetBuffer.clear()
            targetBuffer
        } else {
            ByteBuffer.allocateDirect(floatCount * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        }

        var dabOffset = 0
        for (i in 0 until dabCount) {
            val ptX = rawDabs[dabOffset]
            val ptY = rawDabs[dabOffset + 1]
            val ptPressure = rawDabs[dabOffset + 2]
            dabOffset += 3

            val pt = StrokePoint(ptX, ptY, ptPressure, 0L)
            emitSegmentQuadBuffer(
                floatBuffer, pt, pt, 0f, 0f, 0f, 0f,
                baseWidth, r, g, b, baseAlpha, graphiteToneId, isInk, isHighlighter, quadMargin
            )
        }

        floatBuffer.limit(floatCount)
        floatBuffer.position(0)
        return TessellatedMesh(floatBuffer, totalVertices)
    }

    fun tessellateStroke(
        points: List<StrokePoint>,
        tone: PencilTone,
        baseWidth: Float,
        colorRgb: Long = tone.colorRgb,
        isInk: Boolean = tone.isInk
    ): TessellatedMesh {
        return tessellateDabs(points, tone, baseWidth, colorRgb = colorRgb, isInk = isInk)
    }

    inline fun putVertexArray(
        arr: FloatArray,
        offset: Int,
        x: Float, y: Float,
        s0x: Float, s0y: Float,
        s1x: Float, s1y: Float,
        r0: Float, r1: Float, rNom0: Float, rNom1: Float,
        r: Float, g: Float, b: Float, a: Float,
        tone: Float,
        m0x: Float, m0y: Float,
        m1x: Float, m1y: Float,
        nibX: Float, nibY: Float, tilt: Float
    ): Int {
        arr[offset] = x
        arr[offset + 1] = y
        arr[offset + 2] = s0x
        arr[offset + 3] = s0y
        arr[offset + 4] = s1x
        arr[offset + 5] = s1y
        arr[offset + 6] = r0
        arr[offset + 7] = r1
        arr[offset + 8] = rNom0
        arr[offset + 9] = rNom1
        arr[offset + 10] = r
        arr[offset + 11] = g
        arr[offset + 12] = b
        arr[offset + 13] = a
        arr[offset + 14] = tone
        arr[offset + 15] = m0x
        arr[offset + 16] = m0y
        arr[offset + 17] = m1x
        arr[offset + 18] = m1y
        arr[offset + 19] = nibX
        arr[offset + 20] = nibY
        arr[offset + 21] = tilt
        return offset + FLOATS_PER_VERTEX
    }

    private fun putVertexBuffer(
        buffer: FloatBuffer,
        x: Float, y: Float,
        s0x: Float, s0y: Float,
        s1x: Float, s1y: Float,
        r0: Float, r1: Float, rNom0: Float, rNom1: Float,
        r: Float, g: Float, b: Float, a: Float,
        tone: Float,
        m0x: Float, m0y: Float,
        m1x: Float, m1y: Float,
        nibX: Float, nibY: Float, tilt: Float
    ) {
        buffer.put(x)
        buffer.put(y)
        buffer.put(s0x)
        buffer.put(s0y)
        buffer.put(s1x)
        buffer.put(s1y)
        buffer.put(r0)
        buffer.put(r1)
        buffer.put(rNom0)
        buffer.put(rNom1)
        buffer.put(r)
        buffer.put(g)
        buffer.put(b)
        buffer.put(a)
        buffer.put(tone)
        buffer.put(m0x)
        buffer.put(m0y)
        buffer.put(m1x)
        buffer.put(m1y)
        buffer.put(nibX)
        buffer.put(nibY)
        buffer.put(tilt)
    }

    data class QuadColor(val r: Float, val g: Float, val b: Float, val a: Float)
}


