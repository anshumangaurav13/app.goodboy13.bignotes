package com.antigrav.bignotes.core.export

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import com.antigrav.bignotes.core.model.PaperColor
import com.antigrav.bignotes.core.model.Stroke
import com.antigrav.bignotes.core.model.StrokePoint
import com.antigrav.bignotes.ui.theme.AppThemePreset
import com.antigrav.bignotes.ui.theme.InkPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

object PdfExporter {

    private fun resolvePdfStrokeColor(stroke: Stroke): Int {
        val rgb = stroke.colorRgb
        return (rgb or 0xFF000000L).toInt()
    }

    suspend fun exportNoteToPdf(
        context: Context,
        noteTitle: String,
        strokes: List<Stroke>,
        paperColor: PaperColor = PaperColor.WHITE,
        options: PdfExportOptions = PdfExportOptions(),
        images: List<com.antigrav.bignotes.core.model.CanvasImage> = emptyList()
    ): File = withContext(Dispatchers.IO) {
        val exportDir = File(context.cacheDir, "exports").apply { if (!exists()) mkdirs() }
        val sanitizedTitle = noteTitle.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifEmpty { "BigNote" }
        val outputFile = File(exportDir, "${sanitizedTitle}_${System.currentTimeMillis()}.pdf")

        val pdfDocument = PdfDocument()
        val pages = PdfPagePaginator.paginate(strokes, options.pageFormat)
        val totalPages = pages.size

        val pageFormat = options.pageFormat
        val widthPt = pageFormat.widthPt
        val heightPt = pageFormat.heightPt
        val marginPt = PdfPagePaginator.MARGIN_PT

        val usableWidthPt = widthPt - (2 * marginPt)
        val scale = usableWidthPt / PdfPagePaginator.CANONICAL_WIDTH

        // Paint allocations
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE // Always white background regardless of settings
        }
        val vectorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 9f
            color = Color.DKGRAY
            textAlign = Paint.Align.RIGHT
        }

        val rasterizer = if (options.renderMode == PdfRenderMode.RASTER_300DPI || options.renderMode == PdfRenderMode.HYBRID) {
            try {
                GlPageRasterizer(context)
            } catch (e: Throwable) {
                android.util.Log.e("PdfExporter", "Failed to initialize GlPageRasterizer, falling back to CPU", e)
                null
            }
        } else null

        try {
            for (partition in pages) {
                val pageInfo = PdfDocument.PageInfo.Builder(widthPt, heightPt, partition.pageIndex + 1).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas = page.canvas

                // 1. Draw pure white background
                canvas.drawRect(0f, 0f, widthPt.toFloat(), heightPt.toFloat(), bgPaint)

                // 2. Render strokes with default slate light mode colors (clipped cleanly to printable bounds)
                canvas.save()
                canvas.clipRect(0f, marginPt, widthPt.toFloat(), heightPt - marginPt)

                // 2.1 Draw images beneath strokes for Vector/Hybrid CPU fallback
                if (images.isNotEmpty() && (options.renderMode != PdfRenderMode.RASTER_300DPI || rasterizer == null)) {
                    drawCanvasImages(canvas, images, partition.worldTopY, marginPt, scale, context)
                }

                when (options.renderMode) {
                    PdfRenderMode.HYBRID -> {
                        // Vector Ink Pen + High-Res Textured Graphite Pencil Layer
                        val inkStrokes = partition.strokes.filter { it.isInk || it.tone.isInk }
                        val pencilStrokes = partition.strokes.filter { !(it.isInk || it.tone.isInk) }

                        // Render solid fountain pen strokes as scalable native PDF vectors
                        drawVectorStrokes(canvas, inkStrokes, partition.worldTopY, marginPt, scale, vectorPaint, circlePaint, alphaOverride = 255)

                        // Render pencil strokes on high-res textured raster with exact canvas fidelity
                        if (pencilStrokes.isNotEmpty()) {
                            if (rasterizer != null) {
                                rasterizer.renderPagePartition(
                                    canvas, partition, pageFormat, ::resolvePdfStrokeColor,
                                    filterStroke = { !(it.isInk || it.tone.isInk) },
                                    clearWhite = false
                                )
                            } else {
                                drawPencilRasterLayer(canvas, pencilStrokes, partition.worldTopY, marginPt, scale, widthPt, heightPt)
                            }
                        }
                    }
                    PdfRenderMode.VECTOR -> {
                        // Vector Only: Scalable vector paths for all strokes
                        val inkStrokes = partition.strokes.filter { it.isInk || it.tone.isInk }
                        val pencilStrokes = partition.strokes.filter { !(it.isInk || it.tone.isInk) }

                        drawVectorStrokes(canvas, inkStrokes, partition.worldTopY, marginPt, scale, vectorPaint, circlePaint, alphaOverride = 255)
                        drawVectorStrokes(canvas, pencilStrokes, partition.worldTopY, marginPt, scale, vectorPaint, circlePaint, alphaOverride = 130)
                    }
                    PdfRenderMode.RASTER_300DPI -> {
                        // High-Res 300 DPI Full Page Rasterization with 100% canvas parity
                        if (rasterizer != null) {
                            rasterizer.renderPagePartition(
                                canvas, partition, pageFormat, ::resolvePdfStrokeColor,
                                clearWhite = true,
                                images = images
                            )
                        } else {
                            drawFullPageRaster(canvas, partition.strokes, partition.worldTopY, marginPt, scale, widthPt, heightPt)
                        }
                    }
                }
                canvas.restore()

                // 3. Page Number Footer
                if (totalPages > 1) {
                    canvas.drawText(
                        "Page ${partition.pageIndex + 1} of $totalPages",
                        widthPt - marginPt,
                        heightPt - (marginPt * 0.45f),
                        footerPaint
                    )
                }

                pdfDocument.finishPage(page)
            }
        } finally {
            rasterizer?.close()
        }

        FileOutputStream(outputFile).use { out ->
            pdfDocument.writeTo(out)
        }
        pdfDocument.close()

        outputFile
    }

    private fun drawVectorStrokes(
        canvas: Canvas,
        strokes: List<Stroke>,
        worldTopY: Float,
        marginPt: Float,
        scale: Float,
        linePaint: Paint,
        circlePaint: Paint,
        alphaOverride: Int
    ) {
        val sortedStrokes = strokes.sortedBy { if (it.isHighlighter) 0 else 1 }
        for (stroke in sortedStrokes) {
            val points = stroke.points
            if (points.isEmpty()) continue

            val strokeColor = resolvePdfStrokeColor(stroke)
            val r = Color.red(strokeColor)
            val g = Color.green(strokeColor)
            val b = Color.blue(strokeColor)
            val effAlpha = alphaOverride
            val colorWithAlpha = Color.argb(effAlpha, r, g, b)

            linePaint.color = colorWithAlpha
            circlePaint.color = colorWithAlpha

            val effBaseWidth = if (stroke.isHighlighter) stroke.baseWidth * 2.5f else stroke.baseWidth
            val baseWidthScaled = effBaseWidth * scale

            if (points.size == 1) {
                val pt = points[0]
                val cx = marginPt + pt.x * scale
                val cy = marginPt + (pt.y - worldTopY) * scale
                val radius = if (stroke.isHighlighter) {
                    baseWidthScaled * 0.5f
                } else if (stroke.isInk) {
                    val p = pt.pressure.coerceIn(0f, 1f)
                    val curve = p * p * 0.45f + p * 0.55f
                    val factor = 0.30f + 0.70f * curve
                    max(baseWidthScaled * 0.5f * 0.30f, baseWidthScaled * 0.5f * factor)
                } else {
                    max(0.5f, baseWidthScaled * pt.pressure * 0.5f)
                }
                canvas.drawCircle(cx, cy, radius, circlePaint)
                continue
            }

            for (i in 0 until points.size - 1) {
                val p0 = points[i]
                val p1 = points[i + 1]

                val x0 = marginPt + p0.x * scale
                val y0 = marginPt + (p0.y - worldTopY) * scale
                val x1 = marginPt + p1.x * scale
                val y1 = marginPt + (p1.y - worldTopY) * scale

                val avgPressure = (p0.pressure + p1.pressure) * 0.5f
                val strokeW = if (stroke.isHighlighter) {
                    baseWidthScaled
                } else if (stroke.isInk) {
                    val p = avgPressure.coerceIn(0f, 1f)
                    val curve = p * p * 0.45f + p * 0.55f
                    val factor = 0.30f + 0.70f * curve
                    max(baseWidthScaled * 0.30f, baseWidthScaled * factor)
                } else {
                    max(0.5f, baseWidthScaled * avgPressure)
                }

                linePaint.strokeWidth = strokeW
                canvas.drawLine(x0, y0, x1, y1, linePaint)
            }
        }
    }

    private fun drawPencilRasterLayer(
        pageCanvas: Canvas,
        strokes: List<Stroke>,
        worldTopY: Float,
        marginPt: Float,
        scale: Float,
        widthPt: Int,
        heightPt: Int
    ) {
        val rasterDpiScale = 2.0f // 144 DPI print raster resolution
        val bmpWidth = (widthPt * rasterDpiScale).toInt().coerceAtLeast(1)
        val bmpHeight = (heightPt * rasterDpiScale).toInt().coerceAtLeast(1)

        val pencilBitmap = Bitmap.createBitmap(bmpWidth, bmpHeight, Bitmap.Config.ARGB_8888)
        val bmpCanvas = Canvas(pencilBitmap)

        val pencilPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        val effectiveScale = scale * rasterDpiScale
        val effMargin = marginPt * rasterDpiScale

        val sortedStrokes = strokes.sortedBy { if (it.isHighlighter) 0 else 1 }
        for (stroke in sortedStrokes) {
            val points = stroke.points
            if (points.isEmpty()) continue

            val strokeColor = resolvePdfStrokeColor(stroke)
            val r = Color.red(strokeColor)
            val g = Color.green(strokeColor)
            val b = Color.blue(strokeColor)

            val effAlpha = if (stroke.isHighlighter) 90 else 128
            pencilPaint.color = Color.argb(effAlpha, r, g, b)
            val effBaseWidth = if (stroke.isHighlighter) stroke.baseWidth * 2.5f else stroke.baseWidth
            val baseWidthScaled = effBaseWidth * effectiveScale

            for (i in 0 until points.size - 1) {
                val p0 = points[i]
                val p1 = points[i + 1]

                val x0 = effMargin + p0.x * effectiveScale
                val y0 = effMargin + (p0.y - worldTopY) * effectiveScale
                val x1 = effMargin + p1.x * effectiveScale
                val y1 = effMargin + (p1.y - worldTopY) * effectiveScale

                val avgP = (p0.pressure + p1.pressure) * 0.5f
                pencilPaint.strokeWidth = max(0.75f, baseWidthScaled * avgP)

                bmpCanvas.drawLine(x0, y0, x1, y1, pencilPaint)
            }
        }

        pageCanvas.drawBitmap(pencilBitmap, null, Rect(0, 0, widthPt, heightPt), null)
        pencilBitmap.recycle()
    }

    private fun drawFullPageRaster(
        pageCanvas: Canvas,
        strokes: List<Stroke>,
        worldTopY: Float,
        marginPt: Float,
        scale: Float,
        widthPt: Int,
        heightPt: Int
    ) {
        val rasterScale = 4.167f // 300 DPI (300 / 72)
        val bmpWidth = (widthPt * rasterScale).toInt().coerceAtLeast(1)
        val bmpHeight = (heightPt * rasterScale).toInt().coerceAtLeast(1)

        val fullBitmap = Bitmap.createBitmap(bmpWidth, bmpHeight, Bitmap.Config.ARGB_8888)
        val bmpCanvas = Canvas(fullBitmap)
        bmpCanvas.drawColor(Color.WHITE)

        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        val effScale = scale * rasterScale
        val effMargin = marginPt * rasterScale

        val sortedStrokes = strokes.sortedBy { if (it.isHighlighter) 0 else 1 }
        for (stroke in sortedStrokes) {
            val points = stroke.points
            if (points.isEmpty()) continue

            val strokeColor = resolvePdfStrokeColor(stroke)
            val isInk = stroke.isInk || stroke.tone.isInk
            val alpha = if (stroke.isHighlighter || isInk) 255 else 128
            strokePaint.color = Color.argb(alpha, Color.red(strokeColor), Color.green(strokeColor), Color.blue(strokeColor))

            val effBaseWidth = if (stroke.isHighlighter) stroke.baseWidth * 2.5f else stroke.baseWidth
            val baseWidthScaled = effBaseWidth * effScale

            for (i in 0 until points.size - 1) {
                val p0 = points[i]
                val p1 = points[i + 1]

                val x0 = effMargin + p0.x * effScale
                val y0 = effMargin + (p0.y - worldTopY) * effScale
                val x1 = effMargin + p1.x * effScale
                val y1 = effMargin + (p1.y - worldTopY) * effScale

                val avgP = (p0.pressure + p1.pressure) * 0.5f
                strokePaint.strokeWidth = max(1.0f, baseWidthScaled * avgP)

                bmpCanvas.drawLine(x0, y0, x1, y1, strokePaint)
            }
        }

        pageCanvas.drawBitmap(fullBitmap, null, Rect(0, 0, widthPt, heightPt), null)
        fullBitmap.recycle()
    }

    private fun drawCanvasImages(
        canvas: Canvas,
        images: List<com.antigrav.bignotes.core.model.CanvasImage>,
        worldTopY: Float,
        marginPt: Float,
        scale: Float,
        context: Context
    ) {
        val mediaDir = File(context.filesDir, "bignotes_media")
        val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        for (img in images.sortedBy { it.zIndex }) {
            val file = File(mediaDir, img.localFileName)
            if (!file.exists()) continue
            val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: continue

            val cx = marginPt + img.centerX * scale
            val cy = marginPt + (img.centerY - worldTopY) * scale
            val w = img.width * scale
            val h = img.height * scale

            canvas.save()
            canvas.translate(cx, cy)
            if (img.rotationDegrees != 0f) {
                canvas.rotate(img.rotationDegrees)
            }

            val srcL = (img.cropLeft * bitmap.width).toInt().coerceIn(0, bitmap.width)
            val srcT = (img.cropTop * bitmap.height).toInt().coerceIn(0, bitmap.height)
            val srcR = (img.cropRight * bitmap.width).toInt().coerceIn(srcL, bitmap.width)
            val srcB = (img.cropBottom * bitmap.height).toInt().coerceIn(srcT, bitmap.height)
            val srcRect = Rect(srcL, srcT, srcR, srcB)

            val dstRect = RectF(-w * 0.5f, -h * 0.5f, w * 0.5f, h * 0.5f)
            canvas.drawBitmap(bitmap, srcRect, dstRect, imagePaint)

            canvas.restore()
            bitmap.recycle()
        }
    }
}
