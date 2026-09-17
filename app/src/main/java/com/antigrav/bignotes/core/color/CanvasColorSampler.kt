package com.antigrav.bignotes.core.color

import com.antigrav.bignotes.core.eraser.SpatialStrokeIndex
import com.antigrav.bignotes.core.geometry.StrokeTessellator
import com.antigrav.bignotes.core.model.PaperColor
import com.antigrav.bignotes.core.model.Stroke
import kotlin.math.roundToInt
import kotlin.math.sqrt

class StrokeContribution(
    var zIndex: Long = 0L,
    var r: Float = 0f,
    var g: Float = 0f,
    var b: Float = 0f,
    var alpha: Float = 0f
)

object CanvasColorSampler {

    private val pool = Array(32) { StrokeContribution() }
    private val activeContributions = mutableListOf<StrokeContribution>()

    /**
     * Accurately samples the composite visual color visible to the user at world coordinates (wx, wy).
     *
     * Performs true Porter-Duff Over alpha compositing across all intersecting strokes in chronological
     * draw order (bottom-to-top by zIndex) over the canvas paper background color.
     * Takes stroke opacities, pencil tone densities, pressure profiles, radial edge falloff,
     * and multi-stroke overlaps into account.
     */
    @Synchronized
    fun sampleColorAt(
        wx: Float,
        wy: Float,
        zoomScale: Float,
        spatialIndex: SpatialStrokeIndex,
        paperColor: PaperColor
    ): Long {
        val screenTol = 3.5f / zoomScale
        val pickRadius = 32f / zoomScale
        val candidates = spatialIndex.queryCircle(wx, wy, pickRadius)

        if (candidates.isEmpty()) {
            return paperColor.colorHex
        }

        activeContributions.clear()
        var poolIndex = 0

        for (i in candidates.indices) {
            val stroke = candidates[i]
            val pts = stroke.points
            if (pts.isEmpty()) continue

            var minDistSq = Float.MAX_VALUE
            var bestPressure = 0.5f

            if (pts.size == 1) {
                val dx = pts[0].x - wx
                val dy = pts[0].y - wy
                minDistSq = dx * dx + dy * dy
                bestPressure = pts[0].pressure
            } else {
                for (p in 0 until pts.size - 1) {
                    val p1 = pts[p]
                    val p2 = pts[p + 1]
                    val segDx = p2.x - p1.x
                    val segDy = p2.y - p1.y
                    val segLenSq = segDx * segDx + segDy * segDy
                    val t = if (segLenSq > 1e-6f) {
                        (((wx - p1.x) * segDx + (wy - p1.y) * segDy) / segLenSq).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                    val projX = p1.x + t * segDx
                    val projY = p1.y + t * segDy
                    val dx = wx - projX
                    val dy = wy - projY
                    val dSq = dx * dx + dy * dy
                    if (dSq < minDistSq) {
                        minDistSq = dSq
                        bestPressure = p1.pressure + t * (p2.pressure - p1.pressure)
                    }
                }
            }

            val pClamped = bestPressure.coerceIn(0.0f, 1.0f)
            val strokeRadius = if (stroke.isInk) {
                StrokeTessellator.getInkRadius(pClamped, stroke.baseWidth)
            } else {
                (stroke.baseWidth * (0.32f + 0.68f * pClamped) * 0.5f).coerceAtLeast(0.8f)
            }

            val maxReach = strokeRadius + screenTol
            val maxReachSq = maxReach * maxReach
            if (minDistSq > maxReachSq) {
                continue
            }

            val dist = sqrt(minDistSq)
            val aRaw = (((stroke.colorRgb shr 24) and 0xFF).toFloat()) / 255f
            val opacityMultiplier = if (aRaw > 0f) aRaw else 1.0f
            val sr = (((stroke.colorRgb shr 16) and 0xFF).toFloat()) / 255f
            val sg = (((stroke.colorRgb shr 8) and 0xFF).toFloat()) / 255f
            val sb = ((stroke.colorRgb and 0xFF).toFloat()) / 255f

            val strokeAlpha: Float
            if (stroke.isInk) {
                val normDist = dist / maxReach
                val edgeFactor = if (normDist > 0.85f) {
                    1.0f - ((normDist - 0.85f) / 0.15f).coerceIn(0f, 1f)
                } else {
                    1.0f
                }
                strokeAlpha = (opacityMultiplier * edgeFactor).coerceIn(0f, 1f)
            } else {
                val lightness = 0.299f * sr + 0.587f * sg + 0.114f * sb
                val density = (1.0f - lightness).coerceIn(0.0f, 1.0f)
                val baseAlpha = (0.48f + 0.30f * density) * opacityMultiplier
                val dabAlpha = (baseAlpha * (0.12f + 0.88f * pClamped)).coerceIn(0.02f, 1.0f)

                // Effective accumulated opacity across overlapping dabs
                val oneMinusA = (1.0f - dabAlpha * 0.78f).coerceIn(0f, 1f)
                val oneMinusA3 = oneMinusA * oneMinusA * oneMinusA
                val bodyAlpha = ((1.0f - oneMinusA3 * oneMinusA3) * opacityMultiplier).coerceIn(0.02f, 1.0f)

                val normDist = dist / maxReach
                val edgeFalloff = when {
                    normDist <= 0.35f -> 1.0f
                    normDist >= 1.0f -> 0.0f
                    else -> {
                        val t = (1.0f - normDist) / 0.65f
                        t * t * (3f - 2f * t)
                    }
                }
                strokeAlpha = (bodyAlpha * edgeFalloff).coerceIn(0f, 1f)
            }

            if (strokeAlpha > 0.01f) {
                val contrib = if (poolIndex < pool.size) {
                    pool[poolIndex++]
                } else {
                    StrokeContribution()
                }
                contrib.zIndex = stroke.zIndex
                contrib.r = sr
                contrib.g = sg
                contrib.b = sb
                contrib.alpha = strokeAlpha
                activeContributions.add(contrib)
            }
        }

        if (activeContributions.isEmpty()) {
            return paperColor.colorHex
        }

        // Sort bottom-to-top by chronological draw order (zIndex)
        activeContributions.sortBy { it.zIndex }

        // Composite over paper background
        var curR = paperColor.r
        var curG = paperColor.g
        var curB = paperColor.b

        for (c in activeContributions) {
            val a = c.alpha
            curR = c.r * a + curR * (1f - a)
            curG = c.g * a + curG * (1f - a)
            curB = c.b * a + curB * (1f - a)
        }

        val finalR = (curR.coerceIn(0f, 1f) * 255f).roundToInt()
        val finalG = (curG.coerceIn(0f, 1f) * 255f).roundToInt()
        val finalB = (curB.coerceIn(0f, 1f) * 255f).roundToInt()

        return 0xFF000000L or (finalR.toLong() shl 16) or (finalG.toLong() shl 8) or finalB.toLong()
    }
}

