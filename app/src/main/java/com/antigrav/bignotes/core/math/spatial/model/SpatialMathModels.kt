package com.antigrav.bignotes.core.math.spatial.model

import com.antigrav.bignotes.core.model.Stroke

/**
 * Exact geometric bounds derived directly from stroke points (without GPU render padding).
 */
data class SpatialBounds(
    val minX: Float,
    val minY: Float,
    val maxX: Float,
    val maxY: Float
) {
    val width: Float = maxOf(1f, maxX - minX)
    val height: Float = maxOf(1f, maxY - minY)
    val centerX: Float = (minX + maxX) * 0.5f
    val centerY: Float = (minY + maxY) * 0.5f

    companion object {
        fun of(stroke: Stroke): SpatialBounds {
            var mnX = Float.MAX_VALUE
            var mnY = Float.MAX_VALUE
            var mxX = -Float.MAX_VALUE
            var mxY = -Float.MAX_VALUE
            for (p in stroke.points) {
                mnX = minOf(mnX, p.x)
                mnY = minOf(mnY, p.y)
                mxX = maxOf(mxX, p.x)
                mxY = maxOf(mxY, p.y)
            }
            if (mnX == Float.MAX_VALUE) {
                return SpatialBounds(stroke.minX, stroke.minY, stroke.maxX, stroke.maxY)
            }
            return SpatialBounds(mnX, mnY, mxX, mxY)
        }

        fun of(strokes: List<Stroke>): SpatialBounds {
            var mnX = Float.MAX_VALUE
            var mnY = Float.MAX_VALUE
            var mxX = -Float.MAX_VALUE
            var mxY = -Float.MAX_VALUE
            for (s in strokes) {
                for (p in s.points) {
                    mnX = minOf(mnX, p.x)
                    mnY = minOf(mnY, p.y)
                    mxX = maxOf(mxX, p.x)
                    mxY = maxOf(mxY, p.y)
                }
            }
            if (mnX == Float.MAX_VALUE) {
                return SpatialBounds(0f, 0f, 1f, 1f)
            }
            return SpatialBounds(mnX, mnY, mxX, mxY)
        }
    }
}

/**
 * Represents a spatial token (a single character, digit, or connected symbol cluster)
 * with pre-computed geometric metrics.
 */
data class SpatialToken(
    val strokes: List<Stroke>,
    val bounds: SpatialBounds
) {
    val minX: Float get() = bounds.minX
    val minY: Float get() = bounds.minY
    val maxX: Float get() = bounds.maxX
    val maxY: Float get() = bounds.maxY
    val width: Float get() = bounds.width
    val height: Float get() = bounds.height
    val centerX: Float get() = bounds.centerX
    val centerY: Float get() = bounds.centerY

    companion object {
        fun fromStrokes(strokes: List<Stroke>): SpatialToken =
            SpatialToken(strokes, SpatialBounds.of(strokes))
    }
}

/**
 * Result of partitioning strokes around a horizontal fraction bar.
 */
data class FractionDecomposition(
    val barStroke: Stroke,
    val numeratorStrokes: List<Stroke>,
    val denominatorStrokes: List<Stroke>,
    val prefixStrokes: List<Stroke>,
    val suffixStrokes: List<Stroke>
)

/**
 * Result of identifying a base and an attached superscript exponent.
 */
data class ExponentDecomposition(
    val baseTokens: List<SpatialToken>,
    val exponentTokens: List<SpatialToken>
)
