package app.goodboy13.bignotes.core.model

import java.util.UUID

enum class PencilTone(val id: Int, val displayName: String, val colorRgb: Long, val isInk: Boolean = false) {
    HARD(0, "2H", 0xFF383E44, false),
    SOFT(1, "2B", 0xFF1E2226, false),
    LIGHT(2, "Highlight", 0xFFFFFDF5, false),
    INK_BLACK(3, "Carbon Black", 0xFF0F172A, true),
    INK_BLUE(4, "Midnight Blue", 0xFF1D4ED8, true),
    INK_RED(5, "Oxblood Red", 0xFFB91C1C, true)
}

enum class ActiveTool {
    PENCIL,
    FOUNTAIN_PEN,
    HIGHLIGHTER,
    PRECISION_ERASER,
    WHOLE_STROKE_ERASER,
    LASSO,
    COLOR_PICKER
}

enum class GridStyle(val displayName: String) {
    DOTS("Dots"),
    SQUARES("Squares")
}

data class GridConfig(
    val enabled: Boolean = false,
    val style: GridStyle = GridStyle.DOTS,
    val spacingPx: Float = 48f,
    val opacity: Float = 0.25f
)


enum class EraserMode(val displayName: String) {
    PRECISION("Precision Eraser"),
    WHOLE_STROKE("Stroke Eraser")
}

enum class StrokePreset(val sizePx: Float, val eraserRadiusPx: Float, val displayName: String) {
    FINE(3.0f, 16.0f, "3px Fine"),
    MEDIUM(6.0f, 28.0f, "6px Medium"),
    BROAD(12.0f, 50.0f, "12px Broad"),
    CHISEL(24.0f, 84.0f, "24px Chisel")
}

data class StrokePoint(
    val x: Float,
    val y: Float,
    val pressure: Float,
    val timeMillis: Long
)

data class BezierControlPoints(
    val cp1x: Float = 0.85f,
    val cp1y: Float = 0.52f,
    val cp2x: Float = 0.58f,
    val cp2y: Float = 0.77f
) {
    /**
     * Maps raw pressure [0.0, 1.0] through the cubic Bezier curve defined by (0,0), cp1, cp2, (1,1).
     * Solves for t where B_x(t) == rawPressure, then returns B_y(t).
     */
    fun solveY(rawPressure: Float): Float {
        val clamped = rawPressure.coerceIn(0.0f, 1.0f)
        if (clamped <= 0.0f) return 0.0f
        if (clamped >= 1.0f) return 1.0f

        // Initial guess for t
        var t = clamped
        // Newton-Raphson iteration
        for (i in 0 until 8) {
            val oneMinusT = 1.0f - t
            val currentX = 3.0f * oneMinusT * oneMinusT * t * cp1x +
                    3.0f * oneMinusT * t * t * cp2x +
                    t * t * t
            val error = currentX - clamped
            if (kotlin.math.abs(error) < 1e-4f) {
                break
            }
            // Derivative dX/dt
            val dxdt = 3.0f * oneMinusT * oneMinusT * cp1x +
                    6.0f * oneMinusT * t * (cp2x - cp1x) +
                    3.0f * t * t * (1.0f - cp2x)
            if (kotlin.math.abs(dxdt) < 1e-5f) {
                break
            }
            t -= error / dxdt
            t = t.coerceIn(0.0f, 1.0f)
        }

        // Evaluate B_y(t)
        val oneMinusT = 1.0f - t
        val y = 3.0f * oneMinusT * oneMinusT * t * cp1y +
                3.0f * oneMinusT * t * t * cp2y +
                t * t * t
        return y.coerceIn(0.0f, 1.0f)
    }
}

data class Stroke(
    val id: String = UUID.randomUUID().toString(),
    val points: List<StrokePoint>,
    val tone: PencilTone,
    val baseWidth: Float,
    val zIndex: Long = System.currentTimeMillis(),
    val colorRgb: Long = tone.colorRgb,
    val isInk: Boolean = tone.isInk,
    val paletteIndex: Int = 0,
    val isHighlighter: Boolean = false
) {
    val minX: Float
    val minY: Float
    val maxX: Float
    val maxY: Float

    init {
        var mnX = Float.POSITIVE_INFINITY
        var mnY = Float.POSITIVE_INFINITY
        var mxX = Float.NEGATIVE_INFINITY
        var mxY = Float.NEGATIVE_INFINITY
        val pad = if (isHighlighter) maxOf(baseWidth * 6.5f, 36.0f) else maxOf(baseWidth * 2.0f, 12.0f)
        for (i in 0 until points.size) {
            val p = points[i]
            if (p.x < mnX) mnX = p.x
            if (p.y < mnY) mnY = p.y
            if (p.x > mxX) mxX = p.x
            if (p.y > mxY) mxY = p.y
        }
        minX = if (mnX.isInfinite()) 0f else mnX - pad
        minY = if (mnY.isInfinite()) 0f else mnY - pad
        maxX = if (mxX.isInfinite()) 0f else mxX + pad
        maxY = if (mxY.isInfinite()) 0f else mxY + pad
    }
}

enum class PressureCurveType(val displayName: String, val description: String) {
    SOFT("Soft", "Light touch, quick line ramp"),
    STANDARD("Standard", "Balanced natural stylus response"),
    FIRM("Firm", "Deliberate pressure for thick strokes"),
    LINEAR("Linear", "Direct 1:1 hardware pressure")
}

object PressureCurveDefaults {
    val SOFT = BezierControlPoints(cp1x = 0.20f, cp1y = 0.40f, cp2x = 0.25f, cp2y = 1.00f)
    val STANDARD = BezierControlPoints(cp1x = 0.85f, cp1y = 0.52f, cp2x = 0.58f, cp2y = 0.77f)
    val FIRM = BezierControlPoints(cp1x = 0.45f, cp1y = 0.05f, cp2x = 0.70f, cp2y = 0.65f)
    val LINEAR = BezierControlPoints(cp1x = 0.00f, cp1y = 0.00f, cp2x = 1.00f, cp2y = 1.00f)

    fun forType(type: PressureCurveType): BezierControlPoints = when (type) {
        PressureCurveType.SOFT -> SOFT
        PressureCurveType.STANDARD -> STANDARD
        PressureCurveType.FIRM -> FIRM
        PressureCurveType.LINEAR -> LINEAR
    }
}

object VelocityCurveDefaults {
    // Default calligraphic thinning: smooth onset, expressive ramp on fast flicks
    val CALLIGRAPHIC = BezierControlPoints(cp1x = 0.45f, cp1y = 0.15f, cp2x = 0.35f, cp2y = 0.90f)
    val SUBTLE = BezierControlPoints(cp1x = 0.60f, cp1y = 0.10f, cp2x = 0.80f, cp2y = 0.50f)
    val LINEAR = BezierControlPoints(cp1x = 0.00f, cp1y = 0.00f, cp2x = 1.00f, cp2y = 1.00f)
    val OFF = BezierControlPoints(cp1x = 0.50f, cp1y = 0.00f, cp2x = 1.00f, cp2y = 0.00f)
}


