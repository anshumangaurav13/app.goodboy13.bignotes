package com.antigrav.bignotes.core.eraser

import android.graphics.PointF
import com.antigrav.bignotes.core.model.Stroke
import com.antigrav.bignotes.core.spatial.AabbBvhTree

/**
 * Spatial index for strokes backed by a Dynamic AABB Tree (Bounding Volume Hierarchy).
 *
 * Provides scale-independent, zero-allocation spatial querying for the Precision/Stroke Eraser,
 * Lasso selection, and Eyedropper color sampling.
 */
class SpatialStrokeIndex(
    @Suppress("UNUSED_PARAMETER") cellSize: Float = 128f
) {
    private val tree = AabbBvhTree(initialCapacity = 512)

    val size: Int get() = tree.size()

    fun get(strokeId: String): Stroke? = tree.get(strokeId)

    fun rebuild(strokes: Collection<Stroke>) {
        tree.rebuild(strokes)
    }

    fun insert(stroke: Stroke) {
        tree.insert(stroke)
    }

    fun remove(strokeId: String) {
        tree.remove(strokeId)
    }

    fun queryCircle(cx: Float, cy: Float, radius: Float): List<Stroke> {
        return tree.queryCircle(cx, cy, radius)
    }

    fun queryRect(minX: Float, minY: Float, maxX: Float, maxY: Float): List<Stroke> {
        return tree.queryRect(minX, minY, maxX, maxY)
    }

    fun queryPolygon(poly: List<PointF>): List<Stroke> {
        return tree.queryPolygon(poly)
    }

    fun clear() {
        tree.clear()
    }

    companion object {
        fun isPointInPolygon(px: Float, py: Float, poly: List<PointF>): Boolean {
            return AabbBvhTree.isPointInPolygon(px, py, poly)
        }
    }
}

