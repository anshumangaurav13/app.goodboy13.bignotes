package com.antigrav.bignotes.core.search.clustering

import com.antigrav.bignotes.core.model.Stroke
import com.antigrav.bignotes.core.search.model.StrokeCluster
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Spatially and temporally groups handwritten strokes into line-level or phrase-level clusters.
 * This partitions raw canvas strokes so they can be processed by digital ink recognition
 * and mapped back to precise canvas bounding boxes for search highlighting.
 */
object StrokeClusterer {

    private const val MAX_HORIZONTAL_GAP = 160f
    private const val MAX_VERTICAL_GAP_TOLERANCE = 25f
    private const val VERTICAL_CENTER_FACTOR = 0.85f
    private const val MIN_STROKE_HEIGHT = 45f

    fun clusterStrokes(noteId: String, strokes: List<Stroke>): List<StrokeCluster> {
        // Filter out non-ink strokes (highlighters or zero/single-point strokes)
        val candidateStrokes = strokes.filter { stroke ->
            !stroke.isHighlighter && stroke.points.size >= 2
        }

        if (candidateStrokes.isEmpty()) {
            return emptyList()
        }

        val n = candidateStrokes.size
        val parent = IntArray(n) { it }

        fun find(i: Int): Int {
            var root = i
            while (root != parent[root]) {
                root = parent[root]
            }
            var curr = i
            while (curr != root) {
                val next = parent[curr]
                parent[curr] = root
                curr = next
            }
            return root
        }

        fun union(i: Int, j: Int) {
            val rootI = find(i)
            val rootJ = find(j)
            if (rootI != rootJ) {
                parent[rootJ] = rootI
            }
        }

        // Cache stroke bounds and metrics for fast matching
        val metrics = candidateStrokes.map { stroke ->
            val h = max(12f, stroke.maxY - stroke.minY)
            val w = max(12f, stroke.maxX - stroke.minX)
            val cy = (stroke.minY + stroke.maxY) * 0.5f
            val cx = (stroke.minX + stroke.maxX) * 0.5f
            StrokeMetric(stroke, cy, cx, h, w)
        }

        // Compare strokes to link adjacent strokes on the same line
        for (i in 0 until n) {
            val a = metrics[i]
            for (j in i + 1 until n) {
                val b = metrics[j]

                // Check vertical overlap or vertical center distance
                val verticalOverlap = min(a.stroke.maxY, b.stroke.maxY) - max(a.stroke.minY, b.stroke.minY)
                val verticalClose = verticalOverlap >= -MAX_VERTICAL_GAP_TOLERANCE
                val centerDy = abs(a.cy - b.cy)
                val maxAllowedCenterDy = max(max(a.h, b.h), MIN_STROKE_HEIGHT) * VERTICAL_CENTER_FACTOR

                if (verticalClose && centerDy <= maxAllowedCenterDy) {
                    // Check horizontal proximity
                    val horizontalGap = max(
                        0f,
                        max(a.stroke.minX, b.stroke.minX) - min(a.stroke.maxX, b.stroke.maxX)
                    )

                    if (horizontalGap <= MAX_HORIZONTAL_GAP) {
                        union(i, j)
                    }
                }
            }
        }

        // Group into components
        val groups = mutableMapOf<Int, MutableList<StrokeMetric>>()
        for (i in 0 until n) {
            val root = find(i)
            groups.getOrPut(root) { mutableListOf() }.add(metrics[i])
        }

        // Convert each group to a StrokeCluster
        val clusters = mutableListOf<StrokeCluster>()
        var clusterIdx = 0

        for ((_, group) in groups) {
            if (group.isEmpty()) continue

            // Sort strokes within cluster by timestamp if available, else by X coordinate
            val sortedStrokes = group.map { it.stroke }.sortedWith { s1, s2 ->
                val t1 = s1.points.firstOrNull()?.timeMillis ?: 0L
                val t2 = s2.points.firstOrNull()?.timeMillis ?: 0L
                if (t1 > 0L && t2 > 0L && t1 != t2) {
                    t1.compareTo(t2)
                } else {
                    s1.minX.compareTo(s2.minX)
                }
            }

            var cMinX = Float.POSITIVE_INFINITY
            var cMinY = Float.POSITIVE_INFINITY
            var cMaxX = Float.NEGATIVE_INFINITY
            var cMaxY = Float.NEGATIVE_INFINITY
            var totalTime = 0L
            var timeCount = 0

            for (s in sortedStrokes) {
                if (s.minX < cMinX) cMinX = s.minX
                if (s.minY < cMinY) cMinY = s.minY
                if (s.maxX > cMaxX) cMaxX = s.maxX
                if (s.maxY > cMaxY) cMaxY = s.maxY
                val t = s.points.firstOrNull()?.timeMillis ?: 0L
                if (t > 0L) {
                    totalTime += t
                    timeCount++
                }
            }

            val avgTime = if (timeCount > 0) totalTime / timeCount else System.currentTimeMillis()
            val firstStrokeId = sortedStrokes.first().id.take(8)
            val clusterId = "${noteId}_c${clusterIdx}_$firstStrokeId"
            clusterIdx++

            clusters.add(
                StrokeCluster(
                    clusterId = clusterId,
                    noteId = noteId,
                    strokes = sortedStrokes,
                    minX = if (cMinX.isInfinite()) 0f else cMinX,
                    minY = if (cMinY.isInfinite()) 0f else cMinY,
                    maxX = if (cMaxX.isInfinite()) 0f else cMaxX,
                    maxY = if (cMaxY.isInfinite()) 0f else cMaxY,
                    averageTimestamp = avgTime
                )
            )
        }

        // Sort clusters vertically from top to bottom
        clusters.sortBy { it.minY }
        return clusters
    }

    private data class StrokeMetric(
        val stroke: Stroke,
        val cy: Float,
        val cx: Float,
        val h: Float,
        val w: Float
    )
}
