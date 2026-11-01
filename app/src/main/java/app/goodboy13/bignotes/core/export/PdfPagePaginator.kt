package app.goodboy13.bignotes.core.export

import app.goodboy13.bignotes.core.model.Stroke
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
    const val MARGIN_PT = 0.0f // Baseline full bleed (0 pt) for 1:1 parity with in-app canvas
    const val CANONICAL_WIDTH = 1200.0f

    fun getWorldPageHeight(pageFormat: PageFormat, marginPt: Float = MARGIN_PT): Float {
        val usableWidthPt = (pageFormat.widthPt - (2 * marginPt)).coerceAtLeast(10f)
        val usableHeightPt = (pageFormat.heightPt - (2 * marginPt)).coerceAtLeast(10f)
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
     * Paginates strokes with strict 1:1 parity with the in-app canvas.
     * Every page k spans exactly [k * worldPageHeight, (k + 1) * worldPageHeight].
     * Any stroke crossing a page boundary appears on both adjacent pages and is
     * cleanly cut off at the boundary during rendering, guaranteeing that the exported
     * PDF matches the notebook canvas exactly with zero coordinate shifting.
     */
    fun paginate(
        strokes: List<Stroke>,
        pageFormat: PageFormat,
        marginPt: Float = MARGIN_PT,
        images: List<app.goodboy13.bignotes.core.model.CanvasImage> = emptyList()
    ): List<PdfPagePartition> {
        val validStrokes = strokes.filter { it.points.isNotEmpty() }
        val worldPageHeight = getWorldPageHeight(pageFormat, marginPt)

        if (validStrokes.isEmpty() && images.isEmpty()) {
            return listOf(
                PdfPagePartition(
                    pageIndex = 0,
                    worldTopY = 0f,
                    worldBottomY = worldPageHeight,
                    strokes = emptyList()
                )
            )
        }

        val strokeBoxes = validStrokes.map { calculateStrokeBounds(it) }
        val maxStrokeY = if (strokeBoxes.isNotEmpty()) strokeBoxes.maxOf { it.maxY } else 0f
        val maxImageY = if (images.isNotEmpty()) images.maxOf { it.centerY + it.height * 0.5f } else 0f
        val maxContentY = maxOf(maxStrokeY, maxImageY)

        val totalPages = maxOf(1, kotlin.math.ceil(maxContentY / worldPageHeight).toInt())

        val pages = ArrayList<PdfPagePartition>(totalPages)
        for (pageIndex in 0 until totalPages) {
            val pageTopY = pageIndex * worldPageHeight
            val pageBottomY = (pageIndex + 1) * worldPageHeight

            // Include strokes that intersect this page's vertical interval [pageTopY, pageBottomY]
            val pageStrokes = strokeBoxes
                .filter { box -> box.maxY > pageTopY && box.minY < pageBottomY }
                .map { it.stroke }

            pages.add(
                PdfPagePartition(
                    pageIndex = pageIndex,
                    worldTopY = pageTopY,
                    worldBottomY = pageBottomY,
                    strokes = pageStrokes
                )
            )
        }

        return pages
    }
}
