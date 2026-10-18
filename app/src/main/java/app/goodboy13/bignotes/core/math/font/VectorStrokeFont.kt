package app.goodboy13.bignotes.core.math.font

import app.goodboy13.bignotes.core.model.PencilTone
import app.goodboy13.bignotes.core.model.Stroke
import app.goodboy13.bignotes.core.model.StrokePoint
import java.util.UUID

/**
 * Synthesizes vector [Stroke] objects for math result characters (digits, symbols).
 * Generates smooth, native strokes that integrate directly with BigNotes' OpenGL tessellator,
 * spatial index, and undo/redo history.
 */
object VectorStrokeFont {

    private data class Glyph(
        val widthRatio: Float,
        val strokePolylines: List<List<Pair<Float, Float>>>
    )

    private val GLYPHS: Map<Char, Glyph> = mapOf(
        '0' to Glyph(
            widthRatio = 0.60f,
            strokePolylines = listOf(
                listOf(
                    0.50f to 0.05f, 0.25f to 0.12f, 0.12f to 0.35f, 0.12f to 0.65f,
                    0.25f to 0.88f, 0.50f to 0.95f, 0.75f to 0.88f, 0.88f to 0.65f,
                    0.88f to 0.35f, 0.75f to 0.12f, 0.50f to 0.05f
                )
            )
        ),
        '1' to Glyph(
            widthRatio = 0.35f,
            strokePolylines = listOf(
                listOf(0.20f to 0.25f, 0.50f to 0.05f, 0.50f to 0.95f)
            )
        ),
        '2' to Glyph(
            widthRatio = 0.60f,
            strokePolylines = listOf(
                listOf(
                    0.15f to 0.25f, 0.25f to 0.08f, 0.50f to 0.05f, 0.75f to 0.10f,
                    0.88f to 0.25f, 0.75f to 0.45f, 0.50f to 0.65f, 0.15f to 0.95f,
                    0.88f to 0.95f
                )
            )
        ),
        '3' to Glyph(
            widthRatio = 0.60f,
            strokePolylines = listOf(
                listOf(
                    0.15f to 0.10f, 0.85f to 0.10f, 0.45f to 0.48f, 0.70f to 0.52f,
                    0.88f to 0.68f, 0.80f to 0.88f, 0.50f to 0.95f, 0.15f to 0.88f
                )
            )
        ),
        '4' to Glyph(
            widthRatio = 0.60f,
            strokePolylines = listOf(
                listOf(0.72f to 0.05f, 0.72f to 0.95f),
                listOf(0.68f to 0.65f, 0.12f to 0.65f, 0.55f to 0.05f)
            )
        ),
        '5' to Glyph(
            widthRatio = 0.60f,
            strokePolylines = listOf(
                listOf(
                    0.85f to 0.10f, 0.20f to 0.10f, 0.18f to 0.48f, 0.45f to 0.44f,
                    0.75f to 0.50f, 0.88f to 0.68f, 0.78f to 0.88f, 0.50f to 0.95f,
                    0.15f to 0.88f
                )
            )
        ),
        '6' to Glyph(
            widthRatio = 0.60f,
            strokePolylines = listOf(
                listOf(
                    0.80f to 0.12f, 0.45f to 0.25f, 0.18f to 0.55f, 0.18f to 0.80f,
                    0.40f to 0.95f, 0.75f to 0.90f, 0.88f to 0.72f, 0.75f to 0.52f,
                    0.45f to 0.50f, 0.18f to 0.60f
                )
            )
        ),
        '7' to Glyph(
            widthRatio = 0.55f,
            strokePolylines = listOf(
                listOf(0.12f to 0.08f, 0.88f to 0.08f, 0.35f to 0.95f)
            )
        ),
        '8' to Glyph(
            widthRatio = 0.60f,
            strokePolylines = listOf(
                listOf(
                    0.50f to 0.50f, 0.25f to 0.35f, 0.25f to 0.18f, 0.50f to 0.05f,
                    0.75f to 0.18f, 0.75f to 0.35f, 0.50f to 0.50f, 0.18f to 0.68f,
                    0.18f to 0.85f, 0.50f to 0.95f, 0.82f to 0.85f, 0.82f to 0.68f,
                    0.50f to 0.50f
                )
            )
        ),
        '9' to Glyph(
            widthRatio = 0.60f,
            strokePolylines = listOf(
                listOf(
                    0.82f to 0.45f, 0.55f to 0.50f, 0.25f to 0.48f, 0.12f to 0.28f,
                    0.25f to 0.10f, 0.60f to 0.05f, 0.82f to 0.20f, 0.82f to 0.75f,
                    0.55f to 0.95f, 0.25f to 0.90f
                )
            )
        ),
        '.' to Glyph(
            widthRatio = 0.25f,
            strokePolylines = listOf(
                listOf(0.50f to 0.88f, 0.50f to 0.94f)
            )
        ),
        '-' to Glyph(
            widthRatio = 0.45f,
            strokePolylines = listOf(
                listOf(0.12f to 0.50f, 0.88f to 0.50f)
            )
        ),
        '+' to Glyph(
            widthRatio = 0.55f,
            strokePolylines = listOf(
                listOf(0.12f to 0.50f, 0.88f to 0.50f),
                listOf(0.50f to 0.18f, 0.50f to 0.82f)
            )
        ),
        '=' to Glyph(
            widthRatio = 0.50f,
            strokePolylines = listOf(
                listOf(0.12f to 0.38f, 0.88f to 0.38f),
                listOf(0.12f to 0.62f, 0.88f to 0.62f)
            )
        ),
        ' ' to Glyph(
            widthRatio = 0.35f,
            strokePolylines = emptyList()
        ),
        'E' to Glyph(
            widthRatio = 0.55f,
            strokePolylines = listOf(
                listOf(0.85f to 0.10f, 0.20f to 0.10f, 0.20f to 0.95f, 0.85f to 0.95f),
                listOf(0.20f to 0.52f, 0.70f to 0.52f)
            )
        )
    )

    /**
     * Generates a list of vector [Stroke] objects for the given [text] string.
     *
     * @param text The text to render (e.g. "= 120" or "42").
     * @param startX Starting X coordinate in canvas world space.
     * @param centerY Vertical center coordinate in canvas world space.
     * @param targetHeight Height of the rendered characters in world space.
     * @param baseWidth Base stroke width in world space.
     * @param tone The pencil tone to use.
     * @param colorRgb The color RGB value to use.
     * @param isInk Whether the stroke is treated as ink.
     */
    fun generateStrokesForText(
        text: String,
        startX: Float,
        centerY: Float,
        targetHeight: Float,
        baseWidth: Float = 4.0f,
        tone: PencilTone = PencilTone.INK_BLACK,
        colorRgb: Long = tone.colorRgb,
        isInk: Boolean = true
    ): List<Stroke> {
        if (text.isBlank()) return emptyList()

        val strokes = mutableListOf<Stroke>()
        val topY = centerY - targetHeight * 0.5f
        var currentX = startX
        val charSpacing = targetHeight * 0.16f
        val now = System.currentTimeMillis()

        for (char in text) {
            val glyph = GLYPHS[char] ?: GLYPHS[char.uppercaseChar()] ?: Glyph(
                widthRatio = 0.40f,
                strokePolylines = listOf(listOf(0.2f to 0.1f, 0.8f to 0.9f))
            )

            val glyphWidth = targetHeight * glyph.widthRatio

            for (polyline in glyph.strokePolylines) {
                if (polyline.size < 2) continue

                // Interpolate points along polyline segments to ensure smooth rendering
                val points = interpolatePolyline(
                    polyline = polyline,
                    originX = currentX,
                    originY = topY,
                    width = glyphWidth,
                    height = targetHeight,
                    baseTime = now
                )

                if (points.isNotEmpty()) {
                    strokes.add(
                        Stroke(
                            id = UUID.randomUUID().toString(),
                            points = points,
                            tone = tone,
                            baseWidth = baseWidth,
                            zIndex = System.currentTimeMillis(),
                            colorRgb = colorRgb,
                            isInk = isInk,
                            isHighlighter = false
                        )
                    )
                }
            }

            currentX += glyphWidth + charSpacing
        }

        return strokes
    }

    /**
     * Interpolates intermediate points along normalized polyline segments so that
     * Catmull-Rom spline tessellation curves naturally without linear pinching.
     */
    private fun interpolatePolyline(
        polyline: List<Pair<Float, Float>>,
        originX: Float,
        originY: Float,
        width: Float,
        height: Float,
        baseTime: Long
    ): List<StrokePoint> {
        val points = mutableListOf<StrokePoint>()
        val stepPx = maxOf(4.0f, height * 0.08f)
        var timeOffset = 0L

        for (i in 0 until polyline.size - 1) {
            val (nx1, ny1) = polyline[i]
            val (nx2, ny2) = polyline[i + 1]

            val x1 = originX + nx1 * width
            val y1 = originY + ny1 * height
            val x2 = originX + nx2 * width
            val y2 = originY + ny2 * height

            val dx = x2 - x1
            val dy = y2 - y1
            val dist = kotlin.math.hypot(dx, dy)
            val steps = maxOf(1, kotlin.math.ceil(dist / stepPx).toInt())

            for (s in 0 until steps) {
                val t = s.toFloat() / steps
                val px = x1 + dx * t
                val py = y1 + dy * t
                points.add(StrokePoint(px, py, 0.55f, baseTime + timeOffset))
                timeOffset += 5L
            }
        }

        // Add the terminal point
        val (lastNx, lastNy) = polyline.last()
        points.add(
            StrokePoint(
                originX + lastNx * width,
                originY + lastNy * height,
                0.55f,
                baseTime + timeOffset
            )
        )

        return points
    }
}
