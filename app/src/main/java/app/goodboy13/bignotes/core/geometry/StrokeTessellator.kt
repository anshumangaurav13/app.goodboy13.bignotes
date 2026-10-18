package app.goodboy13.bignotes.core.geometry

import app.goodboy13.bignotes.core.model.BezierControlPoints
import app.goodboy13.bignotes.core.model.PencilTone
import app.goodboy13.bignotes.core.model.StrokePoint
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.hypot
import kotlin.math.max

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
 * Can be used incrementally during real-time digitizer events or in a single pass on stroke completion,
 * guaranteeing 100% mathematical parity between in-flight and committed strokes.
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

    fun addPoint(x: Float, y: Float, pressure: Float): List<StrokePoint> {
        if (!hasStarted) return start(x, y, pressure)

        val dx = x - lastX
        val dy = y - lastY
        val segDist = kotlin.math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
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
}

object StrokeTessellator {
    // 10 floats per vertex:
    // a_position (2), a_normal (2), a_radius (1), a_color (4), a_edgeDist/tone (1)
    const val FLOATS_PER_VERTEX = 10
    const val STRIDE_BYTES = FLOATS_PER_VERTEX * 4
    var activeInkConfig: app.goodboy13.bignotes.core.model.InkBleedConfig = app.goodboy13.bignotes.core.model.InkBleedConfig.DEFAULT

    fun getStepDistance(baseWidth: Float, isInk: Boolean = false, isHighlighter: Boolean = false): Float {
        // Step distance needs to be ~15-22% of the minimum effective dab radius
        // to ensure scalloping depth between overlapping discs is < 0.3% of radius,
        // producing a silky-smooth, razor-sharp edge even at 1000% zoom.
        if (isHighlighter) {
            // Highlighter nominal radius is 2.5x baseWidth (5x diameter of regular stroke)
            val nominalRadius = maxOf(5.0f, baseWidth * 2.5f)
            return maxOf(3.0f, nominalRadius * 0.35f)
        }
        val minRadius = if (isInk) {
            maxOf(0.5f, baseWidth * 0.10f)
        } else {
            maxOf(0.6f, baseWidth * 0.15f)
        }
        val step = minRadius * 0.20f
        return if (isInk) maxOf(0.12f, minOf(step, 1.2f)) else maxOf(0.15f, minOf(step, 1.5f))
    }

    /**
     * Generates arc-length parameterized dabs from raw stroke points.
     * Applies Catmull-Rom spline interpolation to eliminate polygonal angular kinks between touch events.
     */
    fun generateDabs(points: List<StrokePoint>, baseWidth: Float, isInk: Boolean = false, isHighlighter: Boolean = false): List<StrokePoint> {
        if (points.isEmpty()) return emptyList()
        val stepDistance = getStepDistance(baseWidth, isInk, isHighlighter)

        // Apply Centripetal Catmull-Rom spline interpolation to eliminate polygonal angular kinks
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

    private fun getToneColor(tone: PencilTone): QuadColor = when (tone) {
        PencilTone.HARD -> QuadColor(0.24f, 0.26f, 0.28f, 0.48f) // #383E44 crisp silvery 2H graphite
        PencilTone.SOFT -> QuadColor(0.12f, 0.13f, 0.15f, 0.72f) // #1E2226 natural velvety 2B graphite
        PencilTone.LIGHT -> QuadColor(1.0f, 0.99f, 0.96f, 0.85f) // #FFFDF5 subtle parchment highlight
        PencilTone.INK_BLACK -> QuadColor(0.07f, 0.08f, 0.09f, 1.0f) // #121417 solid rich carbon black
        PencilTone.INK_BLUE -> QuadColor(0.06f, 0.16f, 0.38f, 1.0f) // #0F2961 solid deep navy blue
        PencilTone.INK_RED -> QuadColor(0.48f, 0.08f, 0.12f, 1.0f) // #7A141F solid deep oxblood red
    }

    /**
     * Pressure size and thinning behavior for fountain pen.
     * Enforces a sensible minimum (at least 35% of base size) so lighter touch or velocity
     * never causes strokes to collapse to zero.
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
            return QuadColor(r, g, b, 1.0f) // Solid continuous stroke, no opacity
        }
        if (isInk) {
            return QuadColor(r, g, b, 1.0f) // Always 100% solid opacity
        }
        val lightness = 0.299f * r + 0.587f * g + 0.114f * b
        val density = (1.0f - lightness).coerceIn(0.0f, 1.0f)
        // 50% softer base alpha for genuine translucent graphite lead behavior
        val baseAlpha = (0.24f + 0.15f * density) * opacityMultiplier
        return QuadColor(r, g, b, baseAlpha)
    }


    /**
     * Fast JVM heap stroke tessellation directly into a FloatArray.
     * Used for mesh caching and multi-buffered rendering with zero native direct-buffer churn.
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
            val flatPoints = FloatArray(points.size * 3)
            var pIdx = 0
            for (i in points.indices) {
                val pt = points[i]
                flatPoints[pIdx++] = pt.x
                flatPoints[pIdx++] = pt.y
                flatPoints[pIdx++] = pt.pressure
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

        val dabs = generateDabs(points, baseWidth, isInk = isInk, isHighlighter = isHighlighter)
        if (dabs.isEmpty()) return CachedStrokeMesh(FloatArray(0), 0)

        val totalVertices = dabs.size * 6
        val data = FloatArray(totalVertices * FLOATS_PER_VERTEX)
        val toneId = if (isHighlighter) 5.5f else if (isInk) 3.0f else getGraphiteDensity(colorRgb)
        val (r, g, b, baseAlpha) = resolveColor(tone, colorRgb, isInk = isInk, isHighlighter = isHighlighter)
        var offset = 0

        for (i in dabs.indices) {
            val pt = dabs[i]
            val radius: Float
            val alpha: Float
            val dabToneId: Float
            if (isHighlighter) {
                val nominalRadius = (baseWidth * 2.5f * (0.88f + 0.12f * pt.pressure)).coerceAtLeast(6.0f)
                radius = nominalRadius
                alpha = baseAlpha
                dabToneId = 5.5f
            } else if (isInk) {
                val nominalRadius = getInkRadius(pt.pressure, baseWidth)
                val extraMargin = maxOf(nominalRadius * activeInkConfig.quadMarginRatio, 2.0f)
                val quadRadius = nominalRadius + extraMargin
                val nominalRatio = (nominalRadius / quadRadius).coerceIn(0.05f, 0.95f)
                radius = quadRadius
                alpha = 1.0f
                dabToneId = 3.0f + nominalRatio
            } else {
                val pressure = pt.pressure.coerceIn(0.0f, 1.0f)
                radius = (baseWidth * (0.32f + 0.68f * pressure) * 0.5f).coerceAtLeast(0.8f)
                alpha = (baseAlpha * (0.12f + 0.88f * pressure)).coerceIn(0.02f, 0.50f)
                dabToneId = toneId
            }

            offset = putVertexArray(data, offset, pt.x, pt.y, -1f, -1f, radius, r, g, b, alpha, dabToneId)
            offset = putVertexArray(data, offset, pt.x, pt.y,  1f, -1f, radius, r, g, b, alpha, dabToneId)
            offset = putVertexArray(data, offset, pt.x, pt.y,  1f,  1f, radius, r, g, b, alpha, dabToneId)

            offset = putVertexArray(data, offset, pt.x, pt.y, -1f, -1f, radius, r, g, b, alpha, dabToneId)
            offset = putVertexArray(data, offset, pt.x, pt.y,  1f,  1f, radius, r, g, b, alpha, dabToneId)
            offset = putVertexArray(data, offset, pt.x, pt.y, -1f,  1f, radius, r, g, b, alpha, dabToneId)
        }

        return CachedStrokeMesh(data, totalVertices)
    }

    /**
     * Tessellates pre-generated dabs into disc quads.
     * Supports passing a reusable target FloatBuffer to eliminate allocations during front buffer rendering.
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

        val toneId = if (isHighlighter) 5.5f else if (isInk) 3.0f else getGraphiteDensity(colorRgb)
        val (r, g, b, baseAlpha) = resolveColor(tone, colorRgb, isInk = isInk, isHighlighter = isHighlighter)

        val totalVertices = dabs.size * 6
        val floatCount = totalVertices * FLOATS_PER_VERTEX
        val floatBuffer = if (targetBuffer != null && targetBuffer.capacity() >= floatCount) {
            targetBuffer.clear()
            targetBuffer
        } else {
            ByteBuffer.allocateDirect(floatCount * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        }

        for (i in dabs.indices) {
            val pt = dabs[i]
            val radius: Float
            val alpha: Float
            val currentToneId: Float
            if (isHighlighter) {
                val nominalRadius = (baseWidth * 2.5f * (0.88f + 0.12f * pt.pressure)).coerceAtLeast(6.0f)
                radius = nominalRadius
                alpha = baseAlpha
                currentToneId = 5.5f
            } else if (isInk) {
                val nominalRadius = getInkRadius(pt.pressure, baseWidth)
                val extraMargin = maxOf(nominalRadius * activeInkConfig.quadMarginRatio, 2.0f)
                val quadRadius = nominalRadius + extraMargin
                val nominalRatio = (nominalRadius / quadRadius).coerceIn(0.05f, 0.95f)
                radius = quadRadius
                alpha = 1.0f
                currentToneId = 3.0f + nominalRatio
            } else {
                val pressure = pt.pressure.coerceIn(0.0f, 1.0f)
                radius = (baseWidth * (0.32f + 0.68f * pressure) * 0.5f).coerceAtLeast(0.8f)
                alpha = (baseAlpha * (0.12f + 0.88f * pressure)).coerceIn(0.02f, 0.50f)
                currentToneId = toneId
            }

            putVertex(floatBuffer, pt.x, pt.y, -1f, -1f, radius, r, g, b, alpha, currentToneId)
            putVertex(floatBuffer, pt.x, pt.y,  1f, -1f, radius, r, g, b, alpha, currentToneId)
            putVertex(floatBuffer, pt.x, pt.y,  1f,  1f, radius, r, g, b, alpha, currentToneId)

            putVertex(floatBuffer, pt.x, pt.y, -1f, -1f, radius, r, g, b, alpha, currentToneId)
            putVertex(floatBuffer, pt.x, pt.y,  1f,  1f, radius, r, g, b, alpha, currentToneId)
            putVertex(floatBuffer, pt.x, pt.y, -1f,  1f, radius, r, g, b, alpha, currentToneId)
        }

        floatBuffer.position(0)
        return TessellatedMesh(floatBuffer, totalVertices)
    }

    /**
     * Tessellates a full stroke by generating arc-length dabs and converting them to disc quads.
     */
    fun tessellateStroke(
        points: List<StrokePoint>,
        tone: PencilTone,
        baseWidth: Float,
        colorRgb: Long = tone.colorRgb,
        isInk: Boolean = tone.isInk
    ): TessellatedMesh {
        val dabs = generateDabs(points, baseWidth, isInk = isInk)
        return tessellateDabs(dabs, tone, baseWidth, colorRgb = colorRgb)
    }

    inline fun putVertexArray(
        arr: FloatArray,
        offset: Int,
        x: Float, y: Float,
        nx: Float, ny: Float,
        radius: Float,
        r: Float, g: Float, b: Float, a: Float,
        tone: Float
    ): Int {
        arr[offset] = x
        arr[offset + 1] = y
        arr[offset + 2] = nx
        arr[offset + 3] = ny
        arr[offset + 4] = radius
        arr[offset + 5] = r
        arr[offset + 6] = g
        arr[offset + 7] = b
        arr[offset + 8] = a
        arr[offset + 9] = tone
        return offset + FLOATS_PER_VERTEX
    }

    private fun putVertex(
        buffer: FloatBuffer,
        x: Float, y: Float,
        nx: Float, ny: Float,
        radius: Float,
        r: Float, g: Float, b: Float, a: Float,
        tone: Float
    ) {
        buffer.put(x)
        buffer.put(y)
        buffer.put(nx)
        buffer.put(ny)
        buffer.put(radius)
        buffer.put(r)
        buffer.put(g)
        buffer.put(b)
        buffer.put(a)
        buffer.put(tone)
    }

    data class QuadColor(val r: Float, val g: Float, val b: Float, val a: Float)
}


