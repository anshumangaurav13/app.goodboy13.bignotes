package app.goodboy13.bignotes.core.math.spatial.detector

import app.goodboy13.bignotes.core.math.spatial.model.SpatialBounds
import app.goodboy13.bignotes.core.math.spatial.model.SpatialToken
import app.goodboy13.bignotes.core.model.Stroke
import kotlin.math.abs

/**
 * Detects base and superscript exponent stroke clusters in linear or partitioned expressions.
 */
object ExponentDetector {

    private const val MAX_EXPONENT_HEIGHT_RATIO = 0.88f
    private const val MIN_ELEVATION_RATIO = 0.18f
    private const val MIN_BOTTOM_LIFT_RATIO = 0.20f

    sealed class MathSegment {
        data class Normal(val strokes: List<Stroke>) : MathSegment()
        data class Power(val baseStrokes: List<Stroke>, val exponentStrokes: List<Stroke>) : MathSegment()
    }

    fun segmentExponents(strokes: List<Stroke>): List<MathSegment> {
        if (strokes.size < 2) {
            return if (strokes.isEmpty()) emptyList() else listOf(MathSegment.Normal(strokes))
        }

        val tokens = clusterIntoTokens(strokes)
        if (tokens.size < 2) {
            return listOf(MathSegment.Normal(strokes))
        }

        val segments = mutableListOf<MathSegment>()
        var i = 0

        while (i < tokens.size) {
            val baseToken = tokens[i]

            // Check if subsequent token is a superscript exponent
            if (i + 1 < tokens.size && isSuperscript(baseToken, tokens[i + 1])) {
                val expTokens = mutableListOf<SpatialToken>()
                expTokens.add(tokens[i + 1])
                var nextIdx = i + 2

                // Check for multi-character exponents (e.g. 2^10 or x^(a+1))
                while (nextIdx < tokens.size) {
                    val candidate = tokens[nextIdx]
                    val prevExp = expTokens.last()
                    if (isContinuationOfExponent(prevExp, candidate, baseToken)) {
                        expTokens.add(candidate)
                        nextIdx++
                    } else {
                        break
                    }
                }

                val baseStrokes = baseToken.strokes
                val expStrokes = expTokens.flatMap { it.strokes }

                segments.add(MathSegment.Power(baseStrokes, expStrokes))
                i = nextIdx
            } else {
                segments.add(MathSegment.Normal(baseToken.strokes))
                i++
            }
        }

        return mergeAdjacentNormalSegments(segments)
    }

    fun isSuperscript(base: SpatialToken, candidate: SpatialToken): Boolean {
        // 1. Horizontal ordering: candidate must be to the right of base start
        if (candidate.minX < base.minX + base.width * 0.35f) return false

        // Horizontal gap should be reasonable (adjacent)
        val maxAllowedGap = maxOf(24.0f, base.height * 0.75f)
        if (candidate.minX > base.maxX + maxAllowedGap) return false

        // 2. Scale: exponent height is smaller than base height
        val heightRatio = candidate.height / base.height
        if (heightRatio > MAX_EXPONENT_HEIGHT_RATIO) return false

        // 3. Vertical elevation:
        // Exponent center must be strictly in the upper region of the base (well above base midline)
        if (candidate.centerY > base.minY + base.height * 0.45f) return false

        // Exponent bottom baseline must be lifted above base bottom baseline
        val bottomDiff = base.maxY - candidate.maxY
        if (bottomDiff < base.height * 0.25f) return false

        // Exponent top reaches near or above the base top
        val topDiff = candidate.minY - base.minY
        if (topDiff > base.height * 0.15f) return false

        return true
    }

    private fun isContinuationOfExponent(
        prevExp: SpatialToken,
        candidate: SpatialToken,
        originalBase: SpatialToken
    ): Boolean {
        val gap = candidate.minX - prevExp.maxX
        if (gap < -10f || gap > maxOf(20f, prevExp.height * 0.75f)) return false

        val heightDiff = abs(candidate.height - prevExp.height)
        if (heightDiff > maxOf(16f, prevExp.height * 0.6f)) return false

        val centerYDiff = abs(candidate.centerY - prevExp.centerY)
        if (centerYDiff > maxOf(14f, prevExp.height * 0.5f)) return false

        if (candidate.maxY > originalBase.maxY - originalBase.height * MIN_BOTTOM_LIFT_RATIO) return false

        return true
    }

    fun clusterIntoTokens(strokes: List<Stroke>): List<SpatialToken> {
        if (strokes.isEmpty()) return emptyList()

        val boundsList = strokes.map { it to SpatialBounds.of(it) }.sortedBy { it.second.minX }
        val tokenClusters = mutableListOf<MutableList<Pair<Stroke, SpatialBounds>>>()

        for (item in boundsList) {
            if (tokenClusters.isEmpty()) {
                tokenClusters.add(mutableListOf(item))
                continue
            }

            val lastCluster = tokenClusters.last()
            val lastMaxX = lastCluster.maxOf { it.second.maxX }
            val lastAvgHeight = lastCluster.map { it.second.height }.average().toFloat()
            val lastAvgCenterY = lastCluster.map { it.second.centerY }.average().toFloat()

            val gap = item.second.minX - lastMaxX
            val isVerticallyAligned = abs(item.second.centerY - lastAvgCenterY) <= maxOf(10f, lastAvgHeight * 0.35f)

            // Merge into same token only if horizontally overlapping and vertically aligned
            if (gap <= 4f && isVerticallyAligned) {
                lastCluster.add(item)
            } else {
                tokenClusters.add(mutableListOf(item))
            }
        }

        return tokenClusters.map { cluster ->
            SpatialToken.fromStrokes(cluster.map { it.first })
        }
    }

    private fun mergeAdjacentNormalSegments(segments: List<MathSegment>): List<MathSegment> {
        if (segments.size <= 1) return segments
        val merged = mutableListOf<MathSegment>()

        for (seg in segments) {
            val last = merged.lastOrNull()
            if (last is MathSegment.Normal && seg is MathSegment.Normal) {
                merged[merged.size - 1] = MathSegment.Normal(last.strokes + seg.strokes)
            } else {
                merged.add(seg)
            }
        }
        return merged
    }
}
