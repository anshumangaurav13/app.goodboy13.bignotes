package app.goodboy13.bignotes.core.math.spatial.detector

import app.goodboy13.bignotes.core.math.spatial.model.FractionDecomposition
import app.goodboy13.bignotes.core.math.spatial.model.SpatialBounds
import app.goodboy13.bignotes.core.model.Stroke
import kotlin.math.abs

/**
 * Detects handwritten horizontal fraction bars and partitions surrounding strokes
 * into Numerator, Denominator, Prefix, and Suffix groups using unpadded spatial coordinates.
 */
object FractionDetector {

    private const val MIN_BAR_WIDTH = 20.0f
    private const val MIN_ASPECT_RATIO = 2.8f
    private const val MAX_TILT_RATIO = 0.35f
    private const val VERTICAL_TOLERANCE_PX = 8.0f

    fun detectFraction(strokes: List<Stroke>): FractionDecomposition? {
        if (strokes.size < 3) return null

        val boundsMap = strokes.associateWith { SpatialBounds.of(it) }
        val equalSignStrokes = findEqualSignStrokes(strokes, boundsMap)
        val candidateBars = strokes.filter { stroke ->
            stroke !in equalSignStrokes && isCandidateBar(stroke, boundsMap[stroke]!!)
        }.sortedBy { boundsMap[it]!!.minX }

        for (bar in candidateBars) {
            val decomposition = testFractionBar(bar, strokes, boundsMap)
            if (decomposition != null) {
                return decomposition
            }
        }

        return null
    }

    private fun isCandidateBar(stroke: Stroke, b: SpatialBounds): Boolean {
        if (b.width < MIN_BAR_WIDTH) return false

        val aspectRatio = b.width / maxOf(1.0f, b.height)
        if (aspectRatio < MIN_ASPECT_RATIO) return false

        if (stroke.points.size >= 2) {
            val first = stroke.points.first()
            val last = stroke.points.last()
            val dx = abs(last.x - first.x)
            val dy = abs(last.y - first.y)
            if (dy / maxOf(1.0f, dx) > MAX_TILT_RATIO) return false
        }

        return true
    }

    private fun findEqualSignStrokes(strokes: List<Stroke>, boundsMap: Map<Stroke, SpatialBounds>): Set<Stroke> {
        val equalStrokes = mutableSetOf<Stroke>()
        val horizontalStrokes = strokes.filter { s ->
            val b = boundsMap[s] ?: return@filter false
            b.width >= 12f && (b.width / maxOf(1f, b.height)) >= 2.0f
        }

        for (i in horizontalStrokes.indices) {
            val a = horizontalStrokes[i]
            val aB = boundsMap[a]!!
            for (j in i + 1 until horizontalStrokes.size) {
                val b = horizontalStrokes[j]
                val bB = boundsMap[b]!!

                val xOverlap = minOf(aB.maxX, bB.maxX) - maxOf(aB.minX, bB.minX)
                val minW = minOf(aB.width, bB.width)
                if (minW <= 0f) continue

                if (xOverlap / minW >= 0.55f) {
                    val verticalGap = abs(aB.centerY - bB.centerY)
                    if (verticalGap <= maxOf(24.0f, minW * 0.70f)) {
                        equalStrokes.add(a)
                        equalStrokes.add(b)
                    }
                }
            }
        }
        return equalStrokes
    }

    private fun testFractionBar(
        bar: Stroke,
        allStrokes: List<Stroke>,
        boundsMap: Map<Stroke, SpatialBounds>
    ): FractionDecomposition? {
        val barBounds = boundsMap[bar] ?: return null

        // A fraction bar CANNOT have another stroke crossing vertically through it (e.g. '+' or 't')
        val hasCrossingStroke = allStrokes.any { other ->
            if (other.id == bar.id) false
            else {
                val ob = boundsMap[other] ?: return@any false
                ob.minY < barBounds.minY - 2f && ob.maxY > barBounds.maxY + 2f &&
                        ob.centerX >= barBounds.minX && ob.centerX <= barBounds.maxX
            }
        }
        if (hasCrossingStroke) return null

        val padding = maxOf(6.0f, barBounds.width * 0.12f)
        val leftBound = barBounds.minX - padding
        val rightBound = barBounds.maxX + padding

        val numerator = mutableListOf<Stroke>()
        val denominator = mutableListOf<Stroke>()
        val prefix = mutableListOf<Stroke>()
        val suffix = mutableListOf<Stroke>()

        for (s in allStrokes) {
            if (s.id == bar.id) continue
            val b = boundsMap[s] ?: continue

            if (b.maxX < leftBound || (b.centerX < barBounds.minX && b.maxX <= barBounds.minX + 4f)) {
                prefix.add(s)
            } else if (b.minX > rightBound || (b.centerX > barBounds.maxX && b.minX >= barBounds.maxX - 4f)) {
                suffix.add(s)
            } else {
                if (b.maxY <= barBounds.minY + VERTICAL_TOLERANCE_PX || b.centerY < barBounds.centerY) {
                    numerator.add(s)
                } else if (b.minY >= barBounds.maxY - VERTICAL_TOLERANCE_PX || b.centerY > barBounds.centerY) {
                    denominator.add(s)
                }
            }
        }

        if (numerator.isEmpty() || denominator.isEmpty()) {
            return null
        }

        val numOverlap = numerator.any {
            val b = boundsMap[it] ?: return@any false
            b.maxX >= barBounds.minX && b.minX <= barBounds.maxX
        }
        val denOverlap = denominator.any {
            val b = boundsMap[it] ?: return@any false
            b.maxX >= barBounds.minX && b.minX <= barBounds.maxX
        }
        if (!numOverlap || !denOverlap) {
            return null
        }

        return FractionDecomposition(
            barStroke = bar,
            numeratorStrokes = numerator,
            denominatorStrokes = denominator,
            prefixStrokes = prefix,
            suffixStrokes = suffix
        )
    }
}
