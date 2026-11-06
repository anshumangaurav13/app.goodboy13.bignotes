package app.goodboy13.bignotes.core.gl

import android.graphics.PointF
import app.goodboy13.bignotes.core.eraser.EraserSession
import app.goodboy13.bignotes.core.geometry.DabGenerator
import app.goodboy13.bignotes.core.geometry.RecognizedShape
import app.goodboy13.bignotes.core.model.EraserMode
import app.goodboy13.bignotes.core.model.StrokePoint

/**
 * Explicit state model for canvas touch interaction.
 *
 * Eliminates concurrent, conflicting boolean flags by guaranteeing the canvas digitizer
 * is in exactly one primary interaction state at any point in time.
 */
sealed class CanvasTouchState {
    /** Canvas is at rest; no fingers or styluses actively engaged in gestures. */
    object Idle : CanvasTouchState()

    /** Stylus or single-touch drawing freehand ink strokes. */
    data class Inking(
        val activePoints: MutableList<StrokePoint> = mutableListOf(),
        var dabGenerator: DabGenerator? = null,
        var anchorScreenX: Float = 0f,
        var anchorScreenY: Float = 0f
    ) : CanvasTouchState()

    /** Stylus holding a recognized QuickShape (line, circle, rectangle, etc.) with live drag deformation. */
    data class HoldingQuickShape(
        var shape: RecognizedShape,
        val points: MutableList<StrokePoint>
    ) : CanvasTouchState()

    /** Stylus or eraser tip actively erasing strokes via whole-stroke or precision splitting. */
    data class Erasing(
        val session: EraserSession,
        val mode: EraserMode,
        val isHoldToErase: Boolean = false
    ) : CanvasTouchState()

    /** Drawing lasso perimeter to select strokes. */
    data class LassoTracing(
        val lassoPoints: MutableList<PointF>
    ) : CanvasTouchState()

    /** Dragging an active lasso stroke selection across the canvas. */
    data class SelectionDragging(
        val startWorldX: Float,
        val startWorldY: Float,
        var currentDx: Float = 0f,
        var currentDy: Float = 0f,
        var hasMoved: Boolean = false
    ) : CanvasTouchState()

    /** Eyedropper tool sampling rendered colors. */
    data class ColorSampling(
        val screenX: Float,
        val screenY: Float,
        var lastSampledColor: Long = 0L
    ) : CanvasTouchState()

    /** Multi-touch or single-finger pan, pinch-zoom, and rotation navigation. */
    data class FingerNavigation(
        var activePointerCount: Int = 1,
        val pointerDownPositions: MutableMap<Int, PointF> = mutableMapOf(),
        var gestureMaxFingers: Int = 1,
        var maxPointersInGesture: Int = 1,
        var gestureStartTime: Long = 0L,
        var totalMovementDistance: Float = 0f,
        var isMultiFingerTapDisqualified: Boolean = false,
        var lastTouchX: Float = 0f,
        var lastTouchY: Float = 0f,
        var lastMidX: Float = 0f,
        var lastMidY: Float = 0f,
        var initialPinchDist: Float = 0f,
        var initialPinchZoom: Float = 1.0f,
        var initialPinchAngle: Float = 0f,
        var initialPinchRotation: Float = 0f
    ) : CanvasTouchState()
}
