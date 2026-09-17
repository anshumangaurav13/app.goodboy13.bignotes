package com.antigrav.bignotes.core.export

import com.antigrav.bignotes.core.model.Stroke
import kotlin.math.max

data class StrokeBoundingBox(
    val stroke: Stroke,
    val minY: Float,
    val maxY: Float,
    val minX: Float,
    val maxX: Float
)

data class PdfPagePartition(
    val pageIndex: Int,
    val worldTopY: Float,
    val worldBottomY: Float,
    val strokes: List<Stroke>
)

object PdfPagePaginator {
    const val MARGIN_PT = 36.0f // 0.5 inch margins
    const val CANONICAL_WIDTH = 1200.0f

    fun getWorldPageHeight(pageFormat: PageFormat, marginPt: Float = MARGIN_PT): Float {
        val usableWidthPt = pageFormat.widthPt - (2 * marginPt)
        val usableHeightPt = pageFormat.heightPt - (2 * marginPt)
        val scale = usableWidthPt / CANONICAL_WIDTH
        return usableHeightPt / scale
    }

    fun calculateStrokeBounds(stroke: Stroke): StrokeBoundingBox {
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE

        val halfBase = stroke.baseWidth * 0.5f
        for (pt in stroke.points) {
            val r = max(1.0f, halfBase * pt.pressure)
            if (pt.x - r < minX) minX = pt.x - r
            if (pt.x + r > maxX) maxX = pt.x + r
            if (pt.y - r < minY) minY = pt.y - r
            if (pt.y + r > maxY) maxY = pt.y + r
        }

        return StrokeBoundingBox(stroke, minY, maxY, minX, maxX)
    }

    /**
     * Paginates strokes according to the strict page-break overlap rule:
     * "the strokes that overlap the page-break should only appear in the next page."
     *
     * 1. Any stroke spanning across the nominal page break is excluded from the current page.
     * 2. The subsequent page begins at the minimum Y of remaining/deferred strokes,
     *    ensuring the deferred stroke appears completely at the top of the next page without clipping.
     */
    fun paginate(
        strokes: List<Stroke>,
        pageFormat: PageFormat,
        marginPt: Float = MARGIN_PT
    ): List<PdfPagePartition> {
        val validStrokes = strokes.filter { it.points.isNotEmpty() }
        if (validStrokes.isEmpty()) {
            return listOf(
                PdfPagePartition(
                    pageIndex = 0,
                    worldTopY = 0f,
                    worldBottomY = 1600f,
                    strokes = emptyList()
                )
            )
        }

        val strokeBoxes = validStrokes.map { calculateStrokeBounds(it) }
        val remaining = strokeBoxes.sortedBy { it.minY }.toMutableList()

        val worldPageHeight = getWorldPageHeight(pageFormat, marginPt)

        val pages = mutableListOf<PdfPagePartition>()
        var pageIndex = 0

        // Start page at minOf(0f, first stroke minY) so top spacing is preserved
        var currentTopY = minOf(0f, remaining.first().minY)

        while (remaining.isNotEmpty()) {
            val nominalBottomY = currentTopY + worldPageHeight

            val pageStrokes = mutableListOf<Stroke>()
            val toRemove = mutableListOf<StrokeBoundingBox>()

            for (box in remaining) {
                if (box.maxY <= nominalBottomY) {
                    // Completely fits within the current page
                    pageStrokes.add(box.stroke)
                    toRemove.add(box)
                } else if (box.minY < nominalBottomY) {
                    // Overlaps the cutoff line!
                    // Strict User Rule: "strokes that are overlapping on the cutoff line should be repeated on both pages."
                    // Add to current page strokes, but do NOT add to toRemove so it repeats on the next page.
                    pageStrokes.add(box.stroke)
                } else {
                    // Beyond the current page break
                }
            }

            // Guard against infinite loop if no stroke is removed from remaining (e.g., all remaining overlap or exceed page height)
            if (toRemove.isEmpty() && remaining.isNotEmpty()) {
                val first = remaining.first()
                if (!pageStrokes.contains(first.stroke)) {
                    pageStrokes.add(first.stroke)
                }
                toRemove.add(first)
            }

            remaining.removeAll(toRemove)

            pages.add(
                PdfPagePartition(
                    pageIndex = pageIndex,
                    worldTopY = currentTopY,
                    worldBottomY = nominalBottomY,
                    strokes = pageStrokes
                )
            )

            pageIndex++

            if (remaining.isNotEmpty()) {
                // Next page starts at the minimum Y of all remaining strokes
                // (which includes the overlapping strokes that were deferred)
                currentTopY = remaining.minOf { it.minY }
            }
        }

        return pages
    }
}
