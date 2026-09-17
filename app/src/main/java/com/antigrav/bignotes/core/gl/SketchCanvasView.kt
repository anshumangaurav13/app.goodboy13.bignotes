package com.antigrav.bignotes.core.gl

import android.content.Context
import android.graphics.PointF
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.Choreographer
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.SurfaceView
import android.view.VelocityTracker
import androidx.graphics.lowlatency.GLFrontBufferedRenderer
import androidx.input.motionprediction.MotionEventPredictor
import com.antigrav.bignotes.core.clipboard.StrokeClipboard
import com.antigrav.bignotes.core.color.CanvasColorSampler
import com.antigrav.bignotes.core.eraser.EraserEngine
import com.antigrav.bignotes.core.eraser.SpatialStrokeIndex
import com.antigrav.bignotes.core.geometry.DabGenerator
import com.antigrav.bignotes.core.geometry.StrokeTessellator
import com.antigrav.bignotes.core.filter.*
import com.antigrav.bignotes.core.history.CanvasAction
import com.antigrav.bignotes.core.history.UndoRedoManager
import com.antigrav.bignotes.core.model.*
import java.util.UUID

sealed class CanvasMutation {
    data class Added(val stroke: Stroke) : CanvasMutation()
    data class Inserted(val strokes: List<Stroke>) : CanvasMutation()
    data class Removed(val strokeIds: List<String>) : CanvasMutation()
    data class Replaced(val removedIds: List<String>, val added: List<Stroke>) : CanvasMutation()
    object Cleared : CanvasMutation()
}

data class PageCanvasState(
    val undoRedoManager: UndoRedoManager = UndoRedoManager(maxHistory = 150),
    var panX: Float = 0f,
    var panY: Float = 0f,
    var zoomScale: Float = 1.0f,
    var rotationDegrees: Float = 0f,
    var isFlippedHorizontal: Boolean = false,
    var images: MutableList<com.antigrav.bignotes.core.model.CanvasImage> = mutableListOf()
)

data class VerticalScrollInfo(
    val scrollFraction: Float,
    val thumbRatio: Float,
    val maxScrollWorldY: Float,
    val currentScrollWorldY: Float,
    val effScale: Float,
    val totalWorldHeight: Float,
    val visibleWorldHeight: Float
)

class SketchCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : SurfaceView(context, attrs, defStyleAttr) {

    var bezierConfig = com.antigrav.bignotes.core.model.PressureCurveDefaults.STANDARD
        set(value) {
            field = value
            renderer.updateBezierConfig(value)
        }
    val renderer = SketchCanvasRenderer(context, bezierConfig)
    val spatialIndex = SpatialStrokeIndex(cellSize = 128f)

    private val pageStates = mutableMapOf<String, PageCanvasState>()
    var currentPageId: String = ""
        private set

    val undoRedoManager: UndoRedoManager
        get() = getOrCreatePageState(currentPageId).undoRedoManager

    var onUndoRedoStateChanged: ((canUndo: Boolean, canRedo: Boolean) -> Unit)? = null

    val currentCanUndo: Boolean get() = undoRedoManager.canUndo
    val currentCanRedo: Boolean get() = undoRedoManager.canRedo

    fun getOrCreatePageState(pageId: String): PageCanvasState {
        val pid = pageId.ifEmpty { "default_page" }
        return pageStates.getOrPut(pid) {
            PageCanvasState().also { state ->
                state.undoRedoManager.onStateChanged = { canUndo, canRedo ->
                    if (currentPageId == pid || currentPageId.isEmpty()) {
                        onUndoRedoStateChanged?.invoke(canUndo, canRedo)
                    }
                }
            }
        }
    }

    fun removePageState(pageId: String) {
        pageStates.remove(pageId)
    }

    fun syncCurrentPageStateFromRenderer() {
        if (currentPageId.isNotEmpty()) {
            val state = getOrCreatePageState(currentPageId)
            state.panX = renderer.panX
            state.panY = renderer.panY
            state.zoomScale = renderer.zoomScale
            state.rotationDegrees = renderer.rotationDegrees
            state.isFlippedHorizontal = renderer.isFlippedHorizontal
            state.images = renderer.images.toMutableList()
        }
    }

    private var frontBufferedRenderer: GLFrontBufferedRenderer<FrontBufferPacket>? = null
    private var motionPredictor: MotionEventPredictor? = null

    // Active tool and eraser state
    var activeTool: ActiveTool = ActiveTool.PENCIL
        set(value) {
            if (field != value) {
                field = value
                if (value != ActiveTool.LASSO) {
                    clearSelection()
                }
                if (value != ActiveTool.PRECISION_ERASER && value != ActiveTool.WHOLE_STROKE_ERASER) {
                    onEraserReticleChanged?.invoke(0f, 0f, 0f, EraserMode.PRECISION, false)
                }
            }
        }
    var eraserMode: EraserMode = EraserMode.PRECISION
    var eraserRadius: Float = StrokePreset.MEDIUM.eraserRadiusPx // Screen pixels
    var isZoomLocked: Boolean = false
    var isRotationLocked: Boolean = true
    var isCalibrationActive: Boolean = false

    var strictStylusMode: Boolean = true
    var multiFingerGesturesEnabled: Boolean = true
    var shapeSnappingEnabled: Boolean = true
    var shapeSnapDelayMs: Long = 400L

    var gridConfig: GridConfig
        get() = renderer.gridConfig
        set(value) {
            if (renderer.gridConfig != value) {
                renderer.gridConfig = value
                requestRedraw()
            }
        }

    var paperColor: PaperColor
        get() = renderer.paperColor
        set(value) {
            if (renderer.paperColor != value) {
                renderer.paperColor = value
                requestRedraw()
            }
        }

    var isDarkMode: Boolean
        get() = renderer.isDarkMode
        set(value) {
            if (renderer.isDarkMode != value) {
                renderer.isDarkMode = value
                requestRedraw()
            }
        }

    var basePageWidth: Float
        get() = renderer.basePageWidth
        set(value) {
            if (renderer.basePageWidth != value) {
                renderer.basePageWidth = value
                requestRedraw()
            }
        }

    var showPageSeparators: Boolean
        get() = renderer.showPageSeparators
        set(value) {
            if (renderer.showPageSeparators != value) {
                renderer.showPageSeparators = value
                requestRedraw()
            }
        }

    var inkBleedConfig: com.antigrav.bignotes.core.model.InkBleedConfig
        get() = renderer.inkBleedConfig
        set(value) {
            if (renderer.inkBleedConfig != value) {
                renderer.inkBleedConfig = value
                requestRedraw()
            }
        }

    var worldPageHeight: Float
        get() = renderer.worldPageHeight
        set(value) {
            if (renderer.worldPageHeight != value) {
                renderer.worldPageHeight = value
                requestRedraw()
            }
        }

    var viewOffsetX: Float
        get() = renderer.viewOffsetX
        set(value) {
            updateViewportOffsets(value, renderer.viewOffsetY, anchorTopRight = true)
        }

    var viewOffsetY: Float
        get() = renderer.viewOffsetY
        set(value) {
            updateViewportOffsets(renderer.viewOffsetX, value, anchorTopRight = true)
        }

    fun updateViewportOffsets(newOffsetX: Float, newOffsetY: Float, anchorTopRight: Boolean = true) {
        val oldOffsetX = renderer.viewOffsetX
        val oldOffsetY = renderer.viewOffsetY
        if (oldOffsetX == newOffsetX && oldOffsetY == newOffsetY) return

        val viewW = if (width > 0) width.toFloat() else renderer.currentViewWidth
        if (anchorTopRight && viewW > 50f && renderer.basePageWidth > 50f) {
            val oldVisibleWidth = (viewW - oldOffsetX).coerceAtLeast(50f)
            val oldPageScale = oldVisibleWidth / renderer.basePageWidth
            val newVisibleWidth = (viewW - newOffsetX).coerceAtLeast(50f)
            val newPageScale = newVisibleWidth / renderer.basePageWidth
            val ratio = if (oldPageScale > 0.001f) newPageScale / oldPageScale else 1.0f

            val oldPanX = renderer.panX
            val oldPanY = renderer.panY

            val newPanY = oldPanY * ratio
            val newPanX = viewW - newOffsetX - (viewW - oldOffsetX - oldPanX) * ratio

            val minPanX = if (renderer.zoomScale > 1.001f) -newVisibleWidth * (renderer.zoomScale - 1.0f) else 0f
            renderer.panX = newPanX.coerceIn(minPanX, 0f)
            renderer.panY = newPanY
        }

        renderer.viewOffsetX = newOffsetX
        renderer.viewOffsetY = newOffsetY
        renderer.isFboDirty = true
        scheduleRedrawFrames(2)
        syncCurrentPageStateFromRenderer()
    }


    // Callbacks
    var onCanvasMutation: ((CanvasMutation) -> Unit)? = null
    var onStrokesChanged: ((List<Stroke>) -> Unit)? = null
    var onToolDetected: ((toolName: String, pressure: Float, x: Float, y: Float) -> Unit)? = null
    var onEraserReticleChanged: ((screenX: Float, screenY: Float, radius: Float, mode: EraserMode, isVisible: Boolean) -> Unit)? = null
    var onViewportChanged: ((zoom: Float, panX: Float, panY: Float, rotation: Float) -> Unit)? = null
    var onScrollActiveChanged: ((isScrolling: Boolean) -> Unit)? = null
    var onLassoPathChanged: ((List<PointF>) -> Unit)? = null
    var onSelectionChanged: ((List<Stroke>, RectF?) -> Unit)? = null
    var onSelectionDragDelta: ((Float, Float) -> Unit)? = null
    var onToolChanged: ((ActiveTool) -> Unit)? = null
    var onColorPicked: ((Long) -> Unit)? = null
    var onColorPickerReticleChanged: ((screenX: Float, screenY: Float, colorRgb: Long, isVisible: Boolean) -> Unit)? = null
    var onToastMessage: ((String) -> Unit)? = null
    var onStrokeStarted: (() -> String)? = null
    var onPenContactChanged: ((Boolean) -> Unit)? = null
    var onImageTapped: ((com.antigrav.bignotes.core.model.CanvasImage?) -> Unit)? = null

    // Fountain Pen Velocity Thinning Curve (maps speedFactor [0..1] -> thinning reduction [0..1])
    var velocityBezierConfig: BezierControlPoints = com.antigrav.bignotes.core.model.VelocityCurveDefaults.CALLIGRAPHIC
    var velocityThinning: Float = 0.60f

    // Stylus Velocity Tracker for dynamic pen thinning
    private var lastPointX = 0f
    private var lastPointY = 0f
    private var lastScreenX = 0f
    private var lastScreenY = 0f
    private var lastPointTime = 0L
    private var smoothedStylusSpeed = 0f

    // Velocity-Adaptive EMA Filter for stroke smoothing / stabilization
    var smoothingLevel: Float = 0.0f
        set(value) {
            field = value.coerceIn(0.0f, 1.0f)
            emaFilter.setSmoothing(field)
        }

    var velocityWeight: Float = 0.50f
        set(value) {
            field = value.coerceIn(0.0f, 1.0f)
            emaFilter.setVelocityWeight(field)
        }

    private val emaFilter = com.antigrav.bignotes.core.filter.VelocityAdaptiveEmaFilter(smoothingLevel, velocityWeight)

    // In-flight stroke points
    private val activeStrokePoints = mutableListOf<StrokePoint>()
    private var liveDabGenerator: com.antigrav.bignotes.core.geometry.DabGenerator? = null

    // Lasso & Selection State
    val activeLassoPoints = mutableListOf<PointF>()
    val selectedStrokes = mutableListOf<Stroke>()
    var selectionBounds: RectF? = null
        private set
    var isDraggingSelection = false
        private set
    private var dragStartWorldX = 0f
    private var dragStartWorldY = 0f
    private var totalDragDx = 0f
    private var totalDragDy = 0f
    private var isDragMoved = false

    // Long press detector (Stylus & Finger paste from clipboard)
    private val longPressHandler = Handler(Looper.getMainLooper())
    private var longPressRunnable: Runnable? = null
    private var downScreenX = 0f
    private var downScreenY = 0f
    private var isLongPressConsumed = false
 
    // Shape Snapping ("QuickShape" hold-to-snap)
    private val shapeSnapHandler = Handler(Looper.getMainLooper())
    private var shapeSnapRunnable: Runnable? = null
    var isHoldingSnappedShape: Boolean = false
        private set
    private var currentSnappedShape: com.antigrav.bignotes.core.geometry.RecognizedShape? = null
    private var shapeHoldAnchorSx: Float = 0f
    private var shapeHoldAnchorSy: Float = 0f

    private fun scheduleShapeSnapTimer() {
        shapeSnapRunnable?.let { shapeSnapHandler.removeCallbacks(it) }
        val r = Runnable {
            if (!isHoldingSnappedShape && activeStrokePoints.size >= 6) {
                val recognized = com.antigrav.bignotes.core.geometry.ShapeRecognizer.recognize(activeStrokePoints)
                if (recognized != null) {
                    isHoldingSnappedShape = true
                    currentSnappedShape = recognized
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)

                    val lastPt = activeStrokePoints.lastOrNull()
                    val p = lastPt?.pressure ?: 0.5f
                    val t = lastPt?.timeMillis ?: System.currentTimeMillis()
                    val densePoints = recognized.toStrokePoints(p, t)
                    val shapeDabs = StrokeTessellator.generateDabs(
                        densePoints,
                        renderer.currentBaseWidth,
                        isInk = renderer.isCurrentInk,
                        isHighlighter = renderer.isCurrentHighlighter
                    )
                    frontBufferedRenderer?.renderFrontBufferedLayer(
                        FrontBufferPacket(dabs = shapeDabs, clearBeforeDraw = true)
                    )
                }
            }
        }
        shapeSnapRunnable = r
        shapeSnapHandler.postDelayed(r, shapeSnapDelayMs)
    }


    // Eraser gesture batching
    private var isErasingSession = false
    private val sessionOriginalErasedStrokes = mutableMapOf<String, Stroke>()
    private val sessionLiveSubStrokes = mutableMapOf<String, Stroke>()

    // Finger Pan, Zoom, Multi-touch Gestures & Inertia (architected directly from sketcher)
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var lastMidX = 0f
    private var lastMidY = 0f
    private var lastPinchDist = 0f
    private var initialPinchDist = 0f
    private var initialPinchZoom = 1.0f
    private var initialPinchAngle = 0f
    private var initialPinchRotation = 0f
    private var isFingerDragging = false
    private var gestureStartTime = 0L
    private var maxPointersInGesture = 1
    private var gestureMaxFingers = 1
    private val fingerDownPositions = mutableMapOf<Int, PointF>()
    private var isMultiFingerTapDisqualified = false
    private var totalMovementDistance = 0f
    private var hasScaledInGesture = false
    private var hasMovedPointers = false

    private var velocityTracker: VelocityTracker? = null
    private var inertiaVx = 0f
    private var inertiaVy = 0f
    private var isFlingActive = false
    private var lastFlingFrameNanos = 0L
    private var pendingFocusOnLastStroke = false

    fun panBy(dx: Float, dy: Float) {
        val w = if (width > 0) width.toFloat() else (if (renderer.currentViewWidth > 0f) renderer.currentViewWidth else 1200f)
        val visibleWidth = (w - renderer.viewOffsetX).coerceAtLeast(100f)
        val effScale = renderer.zoomScale
        val minPanX = if (effScale > 1.001f) -visibleWidth * (effScale - 1.0f) else 0f
        renderer.panX = (renderer.panX + dx).coerceIn(minPanX, 0f)
        renderer.panY = (renderer.panY + dy).coerceAtMost(0f)
        syncCurrentPageStateFromRenderer()
        if (kotlin.math.abs(dy) > 0.5f) {
            onScrollActiveChanged?.invoke(true)
        }
    }

    fun zoomAt(focalScreenX: Float, focalScreenY: Float, newZoom: Float) {
        panZoomAndRotate(focalScreenX, focalScreenY, focalScreenX, focalScreenY, newZoom, 0f)
    }

    fun panAndZoom(dx: Float, dy: Float, focalScreenX: Float, focalScreenY: Float, newZoom: Float) {
        panZoomAndRotate(focalScreenX, focalScreenY, focalScreenX + dx, focalScreenY + dy, newZoom, 0f)
    }

    fun panZoomAndRotate(
        prevFocalX: Float,
        prevFocalY: Float,
        curFocalX: Float,
        curFocalY: Float,
        newZoom: Float,
        newRotation: Float
    ) {
        val w = if (width > 0) width.toFloat() else (if (renderer.currentViewWidth > 0f) renderer.currentViewWidth else 1200f)
        val visibleWidth = (w - renderer.viewOffsetX).coerceAtLeast(100f)
        val targetZoom = if (isZoomLocked) renderer.zoomScale else newZoom.coerceIn(1.0f, 2.0f)
        val factor = targetZoom / renderer.zoomScale

        val vx = prevFocalX - renderer.viewOffsetX - renderer.panX
        val vy = prevFocalY - renderer.viewOffsetY - renderer.panY

        val rx = factor * vx
        val ry = factor * vy

        val minPanX = if (targetZoom > 1.001f) -visibleWidth * (targetZoom - 1.0f) else 0f
        renderer.panX = (curFocalX - renderer.viewOffsetX - rx).coerceIn(minPanX, 0f)
        renderer.panY = (curFocalY - renderer.viewOffsetY - ry).coerceAtMost(0f)
        renderer.zoomScale = targetZoom
        renderer.rotationDegrees = 0f
        syncCurrentPageStateFromRenderer()
    }

    private val flingCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!isFlingActive) return
            if (lastFlingFrameNanos == 0L) {
                lastFlingFrameNanos = frameTimeNanos
                Choreographer.getInstance().postFrameCallback(this)
                return
            }
            val dt = ((frameTimeNanos - lastFlingFrameNanos) / 1_000_000_000.0f).coerceIn(0.001f, 0.05f)
            lastFlingFrameNanos = frameTimeNanos

            val w = if (width > 0) width.toFloat() else (if (renderer.currentViewWidth > 0f) renderer.currentViewWidth else 1200f)
            val visibleWidth = (w - renderer.viewOffsetX).coerceAtLeast(100f)
            val minPanX = if (renderer.zoomScale > 1.001f) -visibleWidth * (renderer.zoomScale - 1.0f) else 0f

            renderer.panX = (renderer.panX + inertiaVx * dt).coerceIn(minPanX, 0f)
            renderer.panY = (renderer.panY + inertiaVy * dt).coerceAtMost(0f)
            syncCurrentPageStateFromRenderer()

            if (renderer.panX <= minPanX || renderer.panX >= 0f) {
                inertiaVx = 0f
            }
            if (renderer.panY >= 0f && inertiaVy > 0f) {
                inertiaVy = 0f
            }

            // Quick, smooth deceleration
            val friction = kotlin.math.exp(-10.0f * dt)
            inertiaVx *= friction
            inertiaVy *= friction

            frontBufferedRenderer?.renderMultiBufferedLayer(emptyList())
            onViewportChanged?.invoke(renderer.zoomScale, renderer.panX, renderer.panY, renderer.rotationDegrees)

            val speed = kotlin.math.hypot(inertiaVx.toDouble(), inertiaVy.toDouble())
            if (speed > 60.0) {
                Choreographer.getInstance().postFrameCallback(this)
            } else {
                isFlingActive = false
                renderer.isInteractiveGesture = false
                lastFlingFrameNanos = 0L
                syncCurrentPageStateFromRenderer()
                requestRedraw()
                onViewportChanged?.invoke(renderer.zoomScale, renderer.panX, renderer.panY, renderer.rotationDegrees)
                onScrollActiveChanged?.invoke(false)
            }
        }
    }

    private fun stopFling() {
        if (isFlingActive) {
            isFlingActive = false
            renderer.isInteractiveGesture = false
            lastFlingFrameNanos = 0L
            Choreographer.getInstance().removeFrameCallback(flingCallback)
            syncCurrentPageStateFromRenderer()
            requestRedraw()
            onViewportChanged?.invoke(renderer.zoomScale, renderer.panX, renderer.panY, renderer.rotationDegrees)
            onScrollActiveChanged?.invoke(false)
        }
    }

    private fun startFling(vx: Float, vy: Float) {
        stopFling()
        val w = if (width > 0) width.toFloat() else (if (renderer.currentViewWidth > 0f) renderer.currentViewWidth else 2179f)
        val isZoomed = renderer.zoomScale > 1.001f
        val effectiveVx = if (isZoomed) vx.coerceIn(-3500f, 3500f) else 0f
        val effectiveVy = vy.coerceIn(-3500f, 3500f)
        val speed = kotlin.math.hypot(effectiveVx.toDouble(), effectiveVy.toDouble()).toFloat()
        if (speed > 120f) {
            inertiaVx = effectiveVx
            inertiaVy = effectiveVy
            isFlingActive = true
            renderer.isInteractiveGesture = true
            lastFlingFrameNanos = 0L
            Choreographer.getInstance().postFrameCallback(flingCallback)
            onScrollActiveChanged?.invoke(true)
        }
    }

    fun getVerticalScrollInfo(): VerticalScrollInfo {
        val effScale = (renderer.zoomScale * renderer.pageScale).coerceAtLeast(0.001f)
        val viewH = if (height > 0) height.toFloat() else (if (renderer.currentViewWidth > 0f) renderer.currentViewWidth * 1.5f else 1800f)
        val visibleWorldH = (viewH - renderer.viewOffsetY).coerceAtLeast(100f) / effScale

        val maxStrokeY = renderer.getMaxStrokeY()
        val currentScrollWorldY = (-renderer.panY / effScale).coerceAtLeast(0f)

        val baseDocHeight = maxOf(1800f, visibleWorldH * 1.25f)
        val totalWorldHeight = maxOf(
            baseDocHeight,
            maxStrokeY + 600f,
            currentScrollWorldY + visibleWorldH + 200f
        )

        val maxScrollWorldY = maxOf(1f, totalWorldHeight - visibleWorldH)
        val scrollFraction = (currentScrollWorldY / maxScrollWorldY).coerceIn(0f, 1f)
        val thumbRatio = (visibleWorldH / totalWorldHeight).coerceIn(0.06f, 1.0f)

        return VerticalScrollInfo(
            scrollFraction = scrollFraction,
            thumbRatio = thumbRatio,
            maxScrollWorldY = maxScrollWorldY,
            currentScrollWorldY = currentScrollWorldY,
            effScale = effScale,
            totalWorldHeight = totalWorldHeight,
            visibleWorldHeight = visibleWorldH
        )
    }

    fun scrollToFraction(fraction: Float) {
        val info = getVerticalScrollInfo()
        val targetWorldY = fraction.coerceIn(0f, 1f) * info.maxScrollWorldY
        renderer.panY = (-targetWorldY * info.effScale).coerceAtMost(0f)
        renderer.isInteractiveGesture = true
        syncCurrentPageStateFromRenderer()
        requestInteractiveRedraw()
        onViewportChanged?.invoke(renderer.zoomScale, renderer.panX, renderer.panY, renderer.rotationDegrees)
        onScrollActiveChanged?.invoke(true)
    }

    fun finishScrollScrubbing() {
        renderer.isInteractiveGesture = false
        syncCurrentPageStateFromRenderer()
        requestRedraw()
        onViewportChanged?.invoke(renderer.zoomScale, renderer.panX, renderer.panY, renderer.rotationDegrees)
        onScrollActiveChanged?.invoke(false)
    }

    /**
     * Programmatically scrolls the canvas to position targetWorldY in the upper third of the viewport.
     */
    fun scrollToWorldY(targetWorldY: Float) {
        val effScale = (renderer.zoomScale * renderer.pageScale).coerceAtLeast(0.001f)
        val viewH = if (height > 0) height.toFloat() else (if (renderer.currentViewWidth > 0f) renderer.currentViewWidth * 1.5f else 1800f)
        val targetScreenOffset = viewH * 0.25f
        renderer.panY = (-targetWorldY * effScale + targetScreenOffset).coerceAtMost(0f)
        syncCurrentPageStateFromRenderer()
        requestRedraw()
        onViewportChanged?.invoke(renderer.zoomScale, renderer.panX, renderer.panY, renderer.rotationDegrees)
    }

    init {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            isAutoHandwritingEnabled = false
        }
        frontBufferedRenderer = GLFrontBufferedRenderer(this, renderer)
        try {
            motionPredictor = MotionEventPredictor.newInstance(this)
        } catch (e: Exception) {
            motionPredictor = null
        }

        holder.addCallback(object : android.view.SurfaceHolder.Callback {
            override fun surfaceCreated(holder: android.view.SurfaceHolder) {
                post {
                    renderer.markDirty()
                    scheduleRedrawFrames(3)
                }
            }

            override fun surfaceChanged(holder: android.view.SurfaceHolder, format: Int, w: Int, h: Int) {
                post {
                    if (w > 0) {
                        renderer.currentViewWidth = w.toFloat()
                        val visibleWidth = (w.toFloat() - renderer.viewOffsetX).coerceAtLeast(100f)
                        val minPanX = if (renderer.zoomScale > 1.001f) -visibleWidth * (renderer.zoomScale - 1.0f) else 0f
                        renderer.panX = renderer.panX.coerceIn(minPanX, 0f)
                    }
                    if (pendingFocusOnLastStroke && h > 0) {
                        pendingFocusOnLastStroke = false
                        scrollToLastStroke()
                    }
                    renderer.markDirty()
                    scheduleRedrawFrames(3)
                }
            }

            override fun surfaceDestroyed(holder: android.view.SurfaceHolder) {}
        })
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            renderer.currentViewWidth = w.toFloat()
            val visibleWidth = (w.toFloat() - renderer.viewOffsetX).coerceAtLeast(100f)
            val minPanX = if (renderer.zoomScale > 1.001f) -visibleWidth * (renderer.zoomScale - 1.0f) else 0f
            renderer.panX = renderer.panX.coerceIn(minPanX, 0f)
            if (pendingFocusOnLastStroke) {
                pendingFocusOnLastStroke = false
                scrollToLastStroke()
            }
            renderer.markDirty()
            scheduleRedrawFrames(3)
        }
    }

    /**
     * Converts raw screen pixel coordinates to canvas world coordinates.
     */
    fun screenToWorld(screenX: Float, screenY: Float): Pair<Float, Float> {
        val effectiveScreenX = if (renderer.isFlippedHorizontal) {
            val w = if (width > 0) width.toFloat() else (if (renderer.currentViewWidth > 0f) renderer.currentViewWidth else 1200f)
            w - screenX
        } else {
            screenX
        }
        val effScale = renderer.zoomScale * renderer.pageScale
        val dx = (effectiveScreenX - renderer.viewOffsetX - renderer.panX) / effScale
        val dy = (screenY - renderer.viewOffsetY - renderer.panY) / effScale
        if (renderer.rotationDegrees == 0f) {
            return Pair(dx, dy)
        }
        val rad = Math.toRadians(-renderer.rotationDegrees.toDouble())
        val cos = kotlin.math.cos(rad).toFloat()
        val sin = kotlin.math.sin(rad).toFloat()
        val worldX = dx * cos - dy * sin
        val worldY = dx * sin + dy * cos
        return Pair(worldX, worldY)
    }

    /**
     * Converts canvas world coordinates to raw screen pixel coordinates.
     */
    fun worldToScreen(worldX: Float, worldY: Float): Pair<Float, Float> {
        val (rx, ry) = if (renderer.rotationDegrees == 0f) {
            Pair(worldX, worldY)
        } else {
            val rad = Math.toRadians(renderer.rotationDegrees.toDouble())
            val cos = kotlin.math.cos(rad).toFloat()
            val sin = kotlin.math.sin(rad).toFloat()
            Pair(worldX * cos - worldY * sin, worldX * sin + worldY * cos)
        }
        val effScale = renderer.zoomScale * renderer.pageScale
        val effX = rx * effScale + renderer.viewOffsetX + renderer.panX
        val effY = ry * effScale + renderer.viewOffsetY + renderer.panY
        val screenX = if (renderer.isFlippedHorizontal) {
            val w = if (width > 0) width.toFloat() else (if (renderer.currentViewWidth > 0f) renderer.currentViewWidth else 1200f)
            w - effX
        } else {
            effX
        }
        return Pair(screenX, effY)
    }

    /**
     * Samples the true composite visual color visible to the user at world coordinates (wx, wy).
     * Performs multi-layer Porter-Duff Over alpha compositing over paper background.
     */
    fun sampleColorAt(wx: Float, wy: Float): Long {
        return CanvasColorSampler.sampleColorAt(
            wx = wx,
            wy = wy,
            zoomScale = renderer.zoomScale * renderer.pageScale,
            spatialIndex = spatialIndex,
            paperColor = renderer.paperColor
        )
    }

    private var lastSampledColor: Long = renderer.paperColor.colorHex

    /**
     * Reads the real rendered pixel color from the GPU committedFbo with analytical fallback.
     */
    fun samplePixelAt(screenX: Float, screenY: Float, onSampled: (Long) -> Unit) {
        val fbr = frontBufferedRenderer
        val viewW = width
        val viewH = height

        if (fbr == null || !fbr.isValid() || viewW <= 0 || viewH <= 0) {
            val (wx, wy) = screenToWorld(screenX, screenY)
            val fallback = sampleColorAt(wx, wy)
            lastSampledColor = fallback
            onSampled(fallback)
            return
        }

        fbr.execute {
            val sampled = renderer.sampleRenderedPixel(screenX, screenY, viewW, viewH)
            post {
                lastSampledColor = sampled
                onSampled(sampled)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // COLOR PICKER / EYEDROPPER TOOL PIPELINE
        if (activeTool == ActiveTool.COLOR_PICKER) {
            val sx = event.x
            val sy = event.y
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    val (wx, wy) = screenToWorld(sx, sy)
                    lastSampledColor = sampleColorAt(wx, wy)
                    onColorPickerReticleChanged?.invoke(sx, sy, lastSampledColor, true)
                    samplePixelAt(sx, sy) { sampled ->
                        onColorPickerReticleChanged?.invoke(sx, sy, sampled, true)
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    onColorPickerReticleChanged?.invoke(sx, sy, lastSampledColor, true)
                    samplePixelAt(sx, sy) { sampled ->
                        onColorPickerReticleChanged?.invoke(sx, sy, sampled, true)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    samplePixelAt(sx, sy) { sampled ->
                        onColorPickerReticleChanged?.invoke(sx, sy, sampled, false)
                        onColorPicked?.invoke(sampled)
                        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    }
                }
                MotionEvent.ACTION_CANCEL -> {
                    onColorPickerReticleChanged?.invoke(sx, sy, 0L, false)
                }
            }
            return true
        }

        // Long-press paste detector (Stylus & Finger paste from clipboard)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downScreenX = event.x
                downScreenY = event.y
                isLongPressConsumed = false
                longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
                if (StrokeClipboard.hasContent) {
                    longPressRunnable = Runnable {
                        if (StrokeClipboard.hasContent) {
                            isLongPressConsumed = true
                            activeStrokePoints.clear()
                            liveDabGenerator = null
                            frontBufferedRenderer?.renderFrontBufferedLayer(
                                FrontBufferPacket(clearBeforeDraw = true)
                            )
                            frontBufferedRenderer?.commit()
                            isFingerDragging = false
                            val (wx, wy) = screenToWorld(downScreenX, downScreenY)
                            pasteFromClipboard(wx, wy)
                            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            onToastMessage?.invoke("Pasted ${StrokeClipboard.count} strokes")
                        }
                    }
                    longPressHandler.postDelayed(longPressRunnable!!, 450L)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val dist = kotlin.math.hypot((event.x - downScreenX).toDouble(), (event.y - downScreenY).toDouble())
                if (dist > 16.0 || event.pointerCount > 1) {
                    longPressRunnable?.let {
                        longPressHandler.removeCallbacks(it)
                        longPressRunnable = null
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> {
                longPressRunnable?.let {
                    longPressHandler.removeCallbacks(it)
                    longPressRunnable = null
                }
                if (isLongPressConsumed) {
                    isLongPressConsumed = false
                    return true
                }
            }
        }

        // Check for stylus, mouse, or physical eraser tip
        var stylusPointerIndex = -1
        for (i in 0 until event.pointerCount) {
            val t = event.getToolType(i)
            if (t == MotionEvent.TOOL_TYPE_STYLUS || t == MotionEvent.TOOL_TYPE_ERASER || t == MotionEvent.TOOL_TYPE_MOUSE) {
                stylusPointerIndex = i
                break
            }
        }
        val isEraserTool = activeTool == ActiveTool.PRECISION_ERASER || activeTool == ActiveTool.WHOLE_STROKE_ERASER
        if (!strictStylusMode && stylusPointerIndex == -1 && event.pointerCount == 1) {
            // When strict stylus mode is disabled, permit single finger drawing/erasing
            stylusPointerIndex = 0
        }
        val hasStylus = stylusPointerIndex != -1

        if (hasStylus) {
            // STYLUS DRAWING / ERASING PIPELINE (WITH HARDWARE PALM REJECTION)
            stopFling()
            renderer.isInteractiveGesture = false
            if (isFingerDragging) {
                isFingerDragging = false
                requestInteractiveRedraw()
            }
            motionPredictor?.record(event)

            val sx = event.getX(stylusPointerIndex)
            val sy = event.getY(stylusPointerIndex)
            val sPressure = event.getPressure(stylusPointerIndex)
            val effPressure = bezierConfig.solveY(sPressure.coerceIn(0.0f, 1.0f))

            val toolType = event.getToolType(stylusPointerIndex)
            val isBarrelButtonPressed = (event.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY) != 0
            val isHardwareEraser = toolType == MotionEvent.TOOL_TYPE_ERASER

            val isErasing = isHardwareEraser || isBarrelButtonPressed ||
                    activeTool == ActiveTool.PRECISION_ERASER ||
                    activeTool == ActiveTool.WHOLE_STROKE_ERASER

            val effectiveEraserMode = when {
                activeTool == ActiveTool.WHOLE_STROKE_ERASER -> EraserMode.WHOLE_STROKE
                else -> eraserMode
            }

            val toolLabel = when {
                isErasing && effectiveEraserMode == EraserMode.PRECISION -> "ERASER (PRECISION)"
                isErasing -> "ERASER (STROKE)"
                activeTool == ActiveTool.LASSO -> "STYLUS (LASSO)"
                activeTool == ActiveTool.HIGHLIGHTER -> "STYLUS (HIGHLIGHTER)"
                activeTool == ActiveTool.FOUNTAIN_PEN -> "STYLUS (FOUNTAIN PEN)"
                else -> "STYLUS (GRAPHITE)"
            }
            if (isCalibrationActive) {
                onToolDetected?.invoke(toolLabel, sPressure, sx, sy)
            }

            val (wx, wy) = screenToWorld(sx, sy)

            // LASSO TOOL (STYLUS)
            if (!isErasing && activeTool == ActiveTool.LASSO) {
                val bounds = selectionBounds
                val isInsideSelection = if (bounds != null && selectedStrokes.isNotEmpty()) {
                    val pad = 24f / renderer.zoomScale
                    wx >= bounds.left - pad && wx <= bounds.right + pad &&
                    wy >= bounds.top - pad && wy <= bounds.bottom + pad
                } else false

                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                        parent?.requestDisallowInterceptTouchEvent(true)
                        if (event.actionMasked == MotionEvent.ACTION_DOWN || event.actionIndex == stylusPointerIndex) {
                            if (isInsideSelection) {
                                isDraggingSelection = true
                                dragStartWorldX = wx
                                dragStartWorldY = wy
                                totalDragDx = 0f
                                totalDragDy = 0f
                                isDragMoved = false
                                renderer.updateSelectionDragDelta(0f, 0f)
                                requestInteractiveRedraw()
                            } else {
                                if (selectedStrokes.isNotEmpty()) {
                                    clearSelection()
                                } else {
                                    activeLassoPoints.clear()
                                    activeLassoPoints.add(PointF(wx, wy))
                                    onLassoPathChanged?.invoke(activeLassoPoints.toList())
                                }
                            }
                        }
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (isDraggingSelection) {
                            totalDragDx = wx - dragStartWorldX
                            totalDragDy = wy - dragStartWorldY
                            if (kotlin.math.hypot(totalDragDx.toDouble(), totalDragDy.toDouble()) > 2.0) {
                                isDragMoved = true
                            }
                            renderer.updateSelectionDragDelta(totalDragDx, totalDragDy)
                            requestInteractiveRedraw()
                            onSelectionDragDelta?.invoke(totalDragDx, totalDragDy)
                        } else {
                            val last = activeLassoPoints.lastOrNull()
                            if (last == null || kotlin.math.hypot((wx - last.x).toDouble(), (wy - last.y).toDouble()) > 12f) {
                                activeLassoPoints.add(PointF(wx, wy))
                                onLassoPathChanged?.invoke(activeLassoPoints.toList())
                            }
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionIndex == stylusPointerIndex) {
                            if (isDraggingSelection) {
                                isDraggingSelection = false
                                if (isDragMoved && kotlin.math.hypot(totalDragDx.toDouble(), totalDragDy.toDouble()) > 2.0) {
                                    translateSelectedStrokes(totalDragDx, totalDragDy)
                                } else {
                                    renderer.updateSelectionDragDelta(0f, 0f)
                                    requestRedraw()
                                }
                                totalDragDx = 0f
                                totalDragDy = 0f
                                onSelectionDragDelta?.invoke(0f, 0f)
                            } else if (activeLassoPoints.size >= 3) {
                                val found = spatialIndex.queryPolygon(activeLassoPoints)
                                activeLassoPoints.clear()
                                onLassoPathChanged?.invoke(emptyList())
                                if (found.isNotEmpty()) {
                                    selectedStrokes.clear()
                                    selectedStrokes.addAll(found)
                                    selectionBounds = computeBounds(found)
                                    renderer.setSelectedStrokes(found)
                                    onSelectionChanged?.invoke(selectedStrokes.toList(), selectionBounds)
                                    requestRedraw()
                                } else {
                                    clearSelection()
                                }
                            } else {
                                activeLassoPoints.clear()
                                onLassoPathChanged?.invoke(emptyList())
                            }
                        }
                        return true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        if (isDraggingSelection) {
                            isDraggingSelection = false
                            renderer.updateSelectionDragDelta(0f, 0f)
                            totalDragDx = 0f
                            totalDragDy = 0f
                            onSelectionDragDelta?.invoke(0f, 0f)
                            requestRedraw()
                        } else {
                            activeLassoPoints.clear()
                            onLassoPathChanged?.invoke(emptyList())
                        }
                        return true
                    }
                }
                return true
            }

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    if (event.actionMasked == MotionEvent.ACTION_DOWN || event.actionIndex == stylusPointerIndex) {
                        if (isErasing) {
                            startEraserSession(sx, sy, wx, wy, effectiveEraserMode)
                        } else {
                            startStylusStroke(wx, wy, sx, sy, effPressure, event.eventTime)
                        }
                    }
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    if (isErasing) {
                        performEraserMove(sx, sy, wx, wy, effectiveEraserMode)
                    } else {
                        performPencilMove(event, stylusPointerIndex, effPressure, wx, wy)
                    }
                    return true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                    if (event.actionMasked == MotionEvent.ACTION_UP || event.actionIndex == stylusPointerIndex) {
                        shapeSnapRunnable?.let { shapeSnapHandler.removeCallbacks(it) }
                        shapeSnapRunnable = null

                        if (isErasing) {
                            finalizeEraserSession(effectiveEraserMode)
                        } else if (isHoldingSnappedShape && currentSnappedShape != null) {
                            val lastPoint = activeStrokePoints.lastOrNull()
                            val commitPressure = lastPoint?.pressure ?: effPressure
                            val time = lastPoint?.timeMillis ?: event.eventTime
                            val snappedPoints = currentSnappedShape!!.toStrokePoints(commitPressure, time)
                            activeStrokePoints.clear()
                            activeStrokePoints.addAll(snappedPoints)
                            isHoldingSnappedShape = false
                            currentSnappedShape = null
                            finalizeCurrentStroke()
                        } else {
                            val lastPoint = activeStrokePoints.lastOrNull()
                            val commitPressure = lastPoint?.pressure ?: effPressure
                            if (lastPoint == null) {
                                activeStrokePoints.add(StrokePoint(wx, wy, commitPressure, event.eventTime))
                            } else {
                                val fx: Float
                                val fy: Float
                                if (smoothingLevel > 0.01f) {
                                    emaFilter.filter(wx, wy, event.eventTime)
                                    fx = emaFilter.filteredX
                                    fy = emaFilter.filteredY
                                } else {
                                    fx = wx
                                    fy = wy
                                }
                                val dist = kotlin.math.hypot((fx - lastPoint.x).toDouble(), (fy - lastPoint.y).toDouble()).toFloat()
                                if (dist >= 0.5f) {
                                    val curPt = StrokePoint(fx, fy, commitPressure, event.eventTime)
                                    activeStrokePoints.add(curPt)
                                    val gen = liveDabGenerator
                                    if (gen != null) {
                                        val finalDabs = gen.addPoint(fx, fy, commitPressure)
                                        if (finalDabs.isNotEmpty()) {
                                            frontBufferedRenderer?.renderFrontBufferedLayer(
                                                FrontBufferPacket(dabs = finalDabs, clearBeforeDraw = false)
                                            )
                                        }
                                    }
                                }
                            }
                            finalizeCurrentStroke()
                        }
                    }
                    return true
                }

                MotionEvent.ACTION_CANCEL -> {
                    shapeSnapRunnable?.let { shapeSnapHandler.removeCallbacks(it) }
                    shapeSnapRunnable = null
                    isHoldingSnappedShape = false
                    currentSnappedShape = null
                    if (isErasing) {
                        finalizeEraserSession(effectiveEraserMode)
                    } else {
                        emaFilter.reset()
                        activeStrokePoints.clear()
                        liveDabGenerator = null
                        frontBufferedRenderer?.renderFrontBufferedLayer(
                            FrontBufferPacket(clearBeforeDraw = true)
                        )
                        frontBufferedRenderer?.commit()
                    }
                    return true
                }

            }
            return true
        } else {
            // FINGER NAVIGATION (PAN & ZOOM & MULTI-TOUCH UNDO/REDO)
            if (isCalibrationActive) {
                onToolDetected?.invoke("FINGER (NAVIGATION)", 0f, event.x, event.y)
            }

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    stopFling()
                    renderer.isInteractiveGesture = true
                    velocityTracker?.recycle()
                    velocityTracker = VelocityTracker.obtain()
                    velocityTracker?.addMovement(event)
                    lastTouchX = event.x
                    lastTouchY = event.y
                    lastMidX = event.x
                    lastMidY = event.y
                    isFingerDragging = true
                    gestureStartTime = System.currentTimeMillis()
                    maxPointersInGesture = 1
                    gestureMaxFingers = 1
                    fingerDownPositions.clear()
                    fingerDownPositions[event.getPointerId(0)] = PointF(event.x, event.y)
                    isMultiFingerTapDisqualified = false
                    totalMovementDistance = 0f
                    hasScaledInGesture = false
                    hasMovedPointers = false
                    initialPinchDist = 0f
                    initialPinchZoom = renderer.zoomScale

                    // Immediately clear front buffer to eliminate stale stroke flicker during navigation
                    frontBufferedRenderer?.renderFrontBufferedLayer(
                        FrontBufferPacket(emptyList(), clearBeforeDraw = true)
                    )
                }

                MotionEvent.ACTION_POINTER_DOWN -> {
                    if (isDraggingSelection) {
                        isDraggingSelection = false
                        renderer.updateSelectionDragDelta(0f, 0f)
                        totalDragDx = 0f
                        totalDragDy = 0f
                        onSelectionDragDelta?.invoke(0f, 0f)
                        requestRedraw()
                    }
                    parent?.requestDisallowInterceptTouchEvent(true)
                    stopFling()
                    renderer.isInteractiveGesture = true
                    velocityTracker?.addMovement(event)
                    val actionIdx = event.actionIndex
                    val pid = event.getPointerId(actionIdx)
                    fingerDownPositions[pid] = PointF(event.getX(actionIdx), event.getY(actionIdx))
                    gestureMaxFingers = maxOf(gestureMaxFingers, event.pointerCount)
                    maxPointersInGesture = maxOf(maxPointersInGesture, event.pointerCount)
                    if (event.pointerCount >= 2) {
                        val p0x = event.getX(0)
                        val p0y = event.getY(0)
                        val p1x = event.getX(1)
                        val p1y = event.getY(1)
                        lastMidX = (p0x + p1x) * 0.5f
                        lastMidY = (p0y + p1y) * 0.5f
                        lastTouchX = lastMidX
                        lastTouchY = lastMidY
                        lastPinchDist = kotlin.math.hypot((p1x - p0x).toDouble(), (p1y - p0y).toDouble()).toFloat()
                        initialPinchDist = lastPinchDist
                        initialPinchZoom = renderer.zoomScale
                        initialPinchAngle = Math.toDegrees(kotlin.math.atan2((p1y - p0y).toDouble(), (p1x - p0x).toDouble())).toFloat()
                        initialPinchRotation = renderer.rotationDegrees
                    }
                }

                MotionEvent.ACTION_MOVE -> {
                    if (isDraggingSelection) {
                        if (event.pointerCount == 1) {
                            val (curWx, curWy) = screenToWorld(event.x, event.y)
                            totalDragDx = curWx - dragStartWorldX
                            totalDragDy = curWy - dragStartWorldY
                            if (kotlin.math.hypot(totalDragDx.toDouble(), totalDragDy.toDouble()) > 2f) {
                                isDragMoved = true
                            }
                            renderer.updateSelectionDragDelta(totalDragDx, totalDragDy)
                            requestInteractiveRedraw()
                            onSelectionDragDelta?.invoke(totalDragDx, totalDragDy)
                        }
                        return true
                    }
                    velocityTracker?.addMovement(event)
                    if (!isMultiFingerTapDisqualified) {
                        for (i in 0 until event.pointerCount) {
                            val pId = event.getPointerId(i)
                            val startPos = fingerDownPositions[pId]
                            if (startPos != null) {
                                val d = kotlin.math.hypot((event.getX(i) - startPos.x).toDouble(), (event.getY(i) - startPos.y).toDouble()).toFloat()
                                if (d > 28f) {
                                    isMultiFingerTapDisqualified = true
                                    break
                                }
                            }
                        }
                    }
                    if (isFingerDragging) {
                        renderer.isInteractiveGesture = true
                        if (event.pointerCount == 1) {
                            val curX = event.x
                            val curY = event.y
                            val dx = curX - lastTouchX
                            val dy = curY - lastTouchY
                            val moveDist = kotlin.math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
                            totalMovementDistance += moveDist
                            if (totalMovementDistance > 12f) {
                                hasMovedPointers = true
                            }
                            panBy(dx, dy)
                            lastTouchX = curX
                            lastTouchY = curY
                            requestInteractiveRedraw()
                            onViewportChanged?.invoke(renderer.zoomScale, renderer.panX, renderer.panY, renderer.rotationDegrees)
                        } else if (event.pointerCount >= 2) {
                            val p0x = event.getX(0)
                            val p0y = event.getY(0)
                            val p1x = event.getX(1)
                            val p1y = event.getY(1)
                            val curDist = kotlin.math.hypot((p1x - p0x).toDouble(), (p1y - p0y).toDouble()).toFloat()
                            val curAngle = Math.toDegrees(kotlin.math.atan2((p1y - p0y).toDouble(), (p1x - p0x).toDouble())).toFloat()
                            val curMidX = (p0x + p1x) * 0.5f
                            val curMidY = (p0y + p1y) * 0.5f
                            val dx = curMidX - lastMidX
                            val dy = curMidY - lastMidY
                            val moveDist = kotlin.math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
                            totalMovementDistance += moveDist
                            val distChange = kotlin.math.abs(curDist - initialPinchDist)

                            var deltaAngle = curAngle - initialPinchAngle
                            while (deltaAngle > 180f) deltaAngle -= 360f
                            while (deltaAngle < -180f) deltaAngle += 360f

                            if (distChange > 12f || kotlin.math.abs(deltaAngle) > 3f || totalMovementDistance > 12f) {
                                hasMovedPointers = true
                                hasScaledInGesture = true
                            }

                            val scale = if (initialPinchDist > 5f) curDist / initialPinchDist else 1.0f
                            val targetZoom = initialPinchZoom * scale

                            val targetRotation = if (isRotationLocked) {
                                renderer.rotationDegrees
                            } else {
                                val effectiveDeltaAngle = if (renderer.isFlippedHorizontal) -deltaAngle else deltaAngle
                                var rawRot = initialPinchRotation + effectiveDeltaAngle
                                while (rawRot > 180f) rawRot -= 360f
                                while (rawRot < -180f) rawRot += 360f
                                if (kotlin.math.abs(rawRot) < 2.5f) 0f else rawRot
                            }

                            panZoomAndRotate(lastMidX, lastMidY, curMidX, curMidY, targetZoom, targetRotation)

                            lastMidX = curMidX
                            lastMidY = curMidY
                            lastTouchX = curMidX
                            lastTouchY = curMidY
                            requestInteractiveRedraw()
                            onViewportChanged?.invoke(renderer.zoomScale, renderer.panX, renderer.panY, renderer.rotationDegrees)
                        }
                    }
                }

                MotionEvent.ACTION_POINTER_UP -> {
                    val liftedIndex = event.actionIndex
                    var r0 = -1
                    var r1 = -1
                    for (i in 0 until event.pointerCount) {
                        if (i != liftedIndex) {
                            if (r0 == -1) r0 = i
                            else if (r1 == -1) { r1 = i; break }
                        }
                    }
                    if (r0 != -1 && r1 != -1) {
                        val p0x = event.getX(r0)
                        val p0y = event.getY(r0)
                        val p1x = event.getX(r1)
                        val p1y = event.getY(r1)
                        lastMidX = (p0x + p1x) * 0.5f
                        lastMidY = (p0y + p1y) * 0.5f
                        lastTouchX = lastMidX
                        lastTouchY = lastMidY
                        lastPinchDist = kotlin.math.hypot((p1x - p0x).toDouble(), (p1y - p0y).toDouble()).toFloat()
                        initialPinchDist = lastPinchDist
                        initialPinchZoom = renderer.zoomScale
                        initialPinchAngle = Math.toDegrees(kotlin.math.atan2((p1y - p0y).toDouble(), (p1x - p0x).toDouble())).toFloat()
                        initialPinchRotation = renderer.rotationDegrees
                    } else if (r0 != -1) {
                        lastTouchX = event.getX(r0)
                        lastTouchY = event.getY(r0)
                        lastMidX = lastTouchX
                        lastMidY = lastTouchY
                        initialPinchDist = 0f
                        initialPinchZoom = renderer.zoomScale
                        initialPinchAngle = 0f
                        initialPinchRotation = renderer.rotationDegrees
                    }
                }

                MotionEvent.ACTION_UP -> {
                    if (isDraggingSelection) {
                        isDraggingSelection = false
                        if (isDragMoved && kotlin.math.hypot(totalDragDx.toDouble(), totalDragDy.toDouble()) > 2f) {
                            translateSelectedStrokes(totalDragDx, totalDragDy)
                        } else {
                            renderer.updateSelectionDragDelta(0f, 0f)
                            requestRedraw()
                        }
                        totalDragDx = 0f
                        totalDragDy = 0f
                        onSelectionDragDelta?.invoke(0f, 0f)
                        return true
                    }
                    velocityTracker?.addMovement(event)
                    velocityTracker?.computeCurrentVelocity(1000)
                    val vx = velocityTracker?.xVelocity ?: 0f
                    val vy = velocityTracker?.yVelocity ?: 0f
                    velocityTracker?.recycle()
                    velocityTracker = null

                    isFingerDragging = false
                    val duration = System.currentTimeMillis() - gestureStartTime

                    // Multi-touch tap: strictly requires stationary two/three finger tap with NO excessive dragging
                    val isMultiTap = multiFingerGesturesEnabled && !isMultiFingerTapDisqualified && duration in 45L..450L
                    val finalFingers = maxOf(gestureMaxFingers, maxPointersInGesture)
                    val isSingleTap = finalFingers == 1 && totalMovementDistance < 14f && duration < 350L
                    if (isMultiTap && finalFingers == 2) {
                        renderer.isInteractiveGesture = false
                        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        undo()
                    } else if (isMultiTap && finalFingers == 3) {
                        renderer.isInteractiveGesture = false
                        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        redo()
                    } else if (isSingleTap) {
                        renderer.isInteractiveGesture = false
                        val (wx, wy) = screenToWorld(event.x, event.y)
                        val hitImage = renderer.images.asReversed().find { it.containsPoint(wx, wy) }
                        onImageTapped?.invoke(hitImage)
                    } else {
                        val speed = kotlin.math.hypot(vx.toDouble(), vy.toDouble()).toFloat()
                        if (speed > 150f) {
                            startFling(vx, vy)
                        } else {
                            renderer.isInteractiveGesture = false
                            syncCurrentPageStateFromRenderer()
                            requestRedraw()
                            onViewportChanged?.invoke(renderer.zoomScale, renderer.panX, renderer.panY, renderer.rotationDegrees)
                            onScrollActiveChanged?.invoke(false)
                        }
                    }
                }

                MotionEvent.ACTION_CANCEL -> {
                    if (isDraggingSelection) {
                        isDraggingSelection = false
                        renderer.updateSelectionDragDelta(0f, 0f)
                        totalDragDx = 0f
                        totalDragDy = 0f
                        onSelectionDragDelta?.invoke(0f, 0f)
                        requestRedraw()
                        return true
                    }
                    stopFling()
                    velocityTracker?.recycle()
                    velocityTracker = null
                    isFingerDragging = false
                    syncCurrentPageStateFromRenderer()
                    requestRedraw()
                    onViewportChanged?.invoke(renderer.zoomScale, renderer.panX, renderer.panY, renderer.rotationDegrees)
                    onScrollActiveChanged?.invoke(false)
                }
            }
            return true
        }
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        val isEraser = activeTool == ActiveTool.PRECISION_ERASER || activeTool == ActiveTool.WHOLE_STROKE_ERASER
        if (isEraser) {
            val effMode = if (activeTool == ActiveTool.WHOLE_STROKE_ERASER) EraserMode.WHOLE_STROKE else eraserMode
            when (event.actionMasked) {
                MotionEvent.ACTION_HOVER_MOVE, MotionEvent.ACTION_HOVER_ENTER -> {
                    onEraserReticleChanged?.invoke(event.x, event.y, eraserRadius, effMode, true)
                    return true
                }
                MotionEvent.ACTION_HOVER_EXIT -> {
                    onEraserReticleChanged?.invoke(0f, 0f, 0f, effMode, false)
                    return true
                }
            }
        }
        return super.onGenericMotionEvent(event)
    }

    private fun startStylusStroke(wx: Float, wy: Float, sx: Float, sy: Float, pressure: Float, time: Long) {
        onPenContactChanged?.invoke(true)
        val targetPageId = onStrokeStarted?.invoke()
        if (!targetPageId.isNullOrEmpty() && targetPageId != currentPageId) {
            currentPageId = targetPageId
        }
        emaFilter.reset()
        activeStrokePoints.clear()
        lastPointX = wx
        lastPointY = wy
        lastScreenX = sx
        lastScreenY = sy
        lastPointTime = time
        smoothedStylusSpeed = 0f

        val fx: Float
        val fy: Float
        if (smoothingLevel > 0.01f) {
            emaFilter.filter(wx, wy, time)
            fx = emaFilter.filteredX
            fy = emaFilter.filteredY
        } else {
            fx = wx
            fy = wy
        }
        val pt = StrokePoint(fx, fy, pressure, time)
        activeStrokePoints.add(pt)

        shapeSnapRunnable?.let { shapeSnapHandler.removeCallbacks(it) }
        shapeSnapRunnable = null
        isHoldingSnappedShape = false
        currentSnappedShape = null
        shapeHoldAnchorSx = sx
        shapeHoldAnchorSy = sy
        if (shapeSnappingEnabled) {
            scheduleShapeSnapTimer()
        }

        val isHighlighter = activeTool == ActiveTool.HIGHLIGHTER
        val isInk = if (isHighlighter) false else (activeTool == ActiveTool.FOUNTAIN_PEN || renderer.currentTone.isInk)
        renderer.isCurrentInk = isInk
        renderer.isCurrentHighlighter = isHighlighter
        val stepDist = StrokeTessellator.getStepDistance(renderer.currentBaseWidth, isInk = isInk, isHighlighter = isHighlighter)
        val gen = DabGenerator(stepDist)
        liveDabGenerator = gen
        val dabs = gen.start(fx, fy, pressure)
        frontBufferedRenderer?.renderFrontBufferedLayer(
            FrontBufferPacket(dabs = dabs, clearBeforeDraw = true)
        )
    }

    private fun performPencilMove(
        event: MotionEvent,
        stylusPointerIndex: Int,
        effPressure: Float,
        wx: Float,
        wy: Float
    ) {
        val curSx = event.getX(stylusPointerIndex)
        val curSy = event.getY(stylusPointerIndex)

        if (activeStrokePoints.isEmpty()) {
            startStylusStroke(wx, wy, curSx, curSy, effPressure, event.eventTime)
            return
        }

        if (isHoldingSnappedShape && currentSnappedShape != null) {
            val (curWx, curWy) = screenToWorld(curSx, curSy)
            val lastPoint = activeStrokePoints.lastOrNull()
            val p = lastPoint?.pressure ?: effPressure
            val t = event.eventTime
            val adjusted = currentSnappedShape!!.adjustWithDrag(StrokePoint(curWx, curWy, p, t))
            currentSnappedShape = adjusted
            val densePoints = adjusted.toStrokePoints(p, t)
            val dabs = StrokeTessellator.generateDabs(
                densePoints,
                renderer.currentBaseWidth,
                isInk = renderer.isCurrentInk,
                isHighlighter = renderer.isCurrentHighlighter
            )
            frontBufferedRenderer?.renderFrontBufferedLayer(
                FrontBufferPacket(dabs = dabs, clearBeforeDraw = true)
            )
            return
        }

        val moveDistFromAnchor = kotlin.math.hypot(
            (curSx - shapeHoldAnchorSx).toDouble(),
            (curSy - shapeHoldAnchorSy).toDouble()
        ).toFloat()

        if (moveDistFromAnchor > 14f) {
            shapeHoldAnchorSx = curSx
            shapeHoldAnchorSy = curSy
            if (shapeSnappingEnabled) {
                scheduleShapeSnapTimer()
            }
        }


        val isHighlighter = activeTool == ActiveTool.HIGHLIGHTER
        val isInk = if (isHighlighter) false else (activeTool == ActiveTool.FOUNTAIN_PEN || renderer.currentTone.isInk)
        renderer.isCurrentInk = isInk
        renderer.isCurrentHighlighter = isHighlighter
        val gen = liveDabGenerator ?: run {
                val stepDist = StrokeTessellator.getStepDistance(renderer.currentBaseWidth, isInk = isInk, isHighlighter = isHighlighter)
                val g = DabGenerator(stepDist)
                liveDabGenerator = g
                val first = activeStrokePoints.first()
                g.start(first.x, first.y, first.pressure)
                g
            }

            val newDabs = ArrayList<StrokePoint>()

            // Add digitizer historical points with responsive physical speed tracking and dynamic velocity thinning
            for (h in 0 until event.historySize) {
                val hsx = event.getHistoricalX(stylusPointerIndex, h)
                val hsy = event.getHistoricalY(stylusPointerIndex, h)
                val hTime = event.getHistoricalEventTime(h)
                val (hwx, hwy) = screenToWorld(hsx, hsy)
                val hRawPressure = event.getHistoricalPressure(stylusPointerIndex, h).coerceIn(0.0f, 1.0f)
                val hBakedPressure = bezierConfig.solveY(hRawPressure)

                // Calculate instantaneous velocity in physical screen pixels per millisecond
                val dt = ((hTime - lastPointTime).coerceAtLeast(1L)).toFloat()
                val screenDist = kotlin.math.hypot((hsx - lastScreenX).toDouble(), (hsy - lastScreenY).toDouble()).toFloat()
                val speed = screenDist / dt // screen px/ms
                smoothedStylusSpeed = smoothedStylusSpeed * 0.40f + speed * 0.60f
                lastScreenX = hsx
                lastScreenY = hsy
                lastPointX = hwx
                lastPointY = hwy
                lastPointTime = hTime

                val speedFactor = (smoothedStylusSpeed / (smoothedStylusSpeed + 1.5f)).coerceIn(0.0f, 1.0f)
                val reduction = velocityBezierConfig.solveY(speedFactor)
                val thinning = if (isInk) {
                    (1.0f - 0.88f * reduction).coerceIn(0.06f, 1.0f)
                } else {
                    1.0f
                }
                val finalBakedPressure = (hBakedPressure * (thinning * thinning)).coerceIn(0.01f, 1.0f)

                val fx: Float
                val fy: Float
                if (smoothingLevel > 0.01f) {
                    emaFilter.filter(hwx, hwy, hTime)
                    fx = emaFilter.filteredX
                    fy = emaFilter.filteredY
                } else {
                    fx = hwx
                    fy = hwy
                }
                activeStrokePoints.add(
                    StrokePoint(fx, fy, finalBakedPressure, hTime)
                )
                newDabs.addAll(gen.addPoint(fx, fy, finalBakedPressure))
            }

            // Current event point
            val dt = ((event.eventTime - lastPointTime).coerceAtLeast(1L)).toFloat()
            val screenDist = kotlin.math.hypot((curSx - lastScreenX).toDouble(), (curSy - lastScreenY).toDouble()).toFloat()
            val speed = screenDist / dt
            smoothedStylusSpeed = smoothedStylusSpeed * 0.40f + speed * 0.60f
            lastScreenX = curSx
            lastScreenY = curSy
            lastPointX = wx
            lastPointY = wy
            lastPointTime = event.eventTime

            val speedFactor = (smoothedStylusSpeed / (smoothedStylusSpeed + 1.5f)).coerceIn(0.0f, 1.0f)
            val reduction = velocityBezierConfig.solveY(speedFactor)
            val thinning = if (isHighlighter) {
                1.0f
            } else if (isInk) {
                (1.0f - 0.88f * reduction).coerceIn(0.06f, 1.0f)
            } else {
                1.0f
            }
            val finalEffPressure = (effPressure * (thinning * thinning)).coerceIn(0.01f, 1.0f)

            val curFx: Float
            val curFy: Float
            if (smoothingLevel > 0.01f) {
                emaFilter.filter(wx, wy, event.eventTime)
                curFx = emaFilter.filteredX
                curFy = emaFilter.filteredY
            } else {
                curFx = wx
                curFy = wy
            }
            activeStrokePoints.add(StrokePoint(curFx, curFy, finalEffPressure, event.eventTime))
            newDabs.addAll(gen.addPoint(curFx, curFy, finalEffPressure))

            if (newDabs.isNotEmpty()) {
                frontBufferedRenderer?.renderFrontBufferedLayer(
                    FrontBufferPacket(dabs = newDabs, clearBeforeDraw = false)
                )
            }
    }

    private fun finalizeCurrentStroke() {
        emaFilter.reset()
        onPenContactChanged?.invoke(false)
        if (activeStrokePoints.isNotEmpty()) {
            val isHighlighter = activeTool == ActiveTool.HIGHLIGHTER
            val isInk = if (isHighlighter) false else (activeTool == ActiveTool.FOUNTAIN_PEN || renderer.currentTone.isInk)
            val finalized = Stroke(
                id = UUID.randomUUID().toString(),
                points = activeStrokePoints.toList(),
                tone = renderer.currentTone,
                baseWidth = renderer.currentBaseWidth,
                colorRgb = renderer.currentColorRgb,
                paletteIndex = renderer.currentPaletteIndex,
                isInk = isInk,
                isHighlighter = isHighlighter
            )
            renderer.addCommittedStroke(finalized)
            spatialIndex.insert(finalized)
            undoRedoManager.pushAction(CanvasAction.AddStroke(finalized))

            onCanvasMutation?.invoke(CanvasMutation.Added(finalized))
            onStrokesChanged?.invoke(renderer.getCommittedStrokes())

            activeStrokePoints.clear()
            liveDabGenerator = null

            frontBufferedRenderer?.commit()
        }
    }

    private fun startEraserSession(sx: Float, sy: Float, wx: Float, wy: Float, mode: EraserMode) {
        onPenContactChanged?.invoke(true)
        isErasingSession = true
        sessionOriginalErasedStrokes.clear()
        sessionLiveSubStrokes.clear()
        onEraserReticleChanged?.invoke(sx, sy, eraserRadius, mode, true)
        executeEraserAt(wx, wy, mode)
    }

    private fun performEraserMove(sx: Float, sy: Float, wx: Float, wy: Float, mode: EraserMode) {
        onEraserReticleChanged?.invoke(sx, sy, eraserRadius, mode, true)
        executeEraserAt(wx, wy, mode)
    }

    private fun executeEraserAt(wx: Float, wy: Float, mode: EraserMode) {
        val effScale = renderer.zoomScale * renderer.pageScale
        val worldRadius = eraserRadius / effScale
        val candidates = spatialIndex.queryCircle(wx, wy, worldRadius)
        if (candidates.isEmpty()) return

        var hasModified = false

        if (mode == EraserMode.WHOLE_STROKE) {
            for (stroke in candidates) {
                if (EraserEngine.doesStrokeIntersectCircle(stroke, wx, wy, worldRadius)) {
                    if (stroke.id in sessionLiveSubStrokes) {
                        sessionLiveSubStrokes.remove(stroke.id)
                    } else {
                        sessionOriginalErasedStrokes[stroke.id] = stroke
                    }
                    spatialIndex.remove(stroke.id)
                    renderer.removeStrokes(setOf(stroke.id))
                    hasModified = true
                }
            }
        } else {
            // Precision Eraser
            for (stroke in candidates) {
                if (!EraserEngine.doesStrokeIntersectCircle(stroke, wx, wy, worldRadius)) continue

                val subStrokes = EraserEngine.clipStrokeByCircle(stroke, wx, wy, worldRadius)
                val wasCut = subStrokes.size != 1 || subStrokes[0].points.size != stroke.points.size

                if (wasCut) {
                    if (stroke.id in sessionLiveSubStrokes) {
                        sessionLiveSubStrokes.remove(stroke.id)
                    } else {
                        sessionOriginalErasedStrokes[stroke.id] = stroke
                    }
                    spatialIndex.remove(stroke.id)
                    renderer.removeStrokes(setOf(stroke.id))

                    for (sub in subStrokes) {
                        sessionLiveSubStrokes[sub.id] = sub
                        spatialIndex.insert(sub)
                        renderer.addCommittedStroke(sub)
                    }
                    hasModified = true
                }
            }
        }

        if (hasModified) {
            requestRedraw()
        }
    }

    private fun finalizeEraserSession(mode: EraserMode) {
        onPenContactChanged?.invoke(false)
        isErasingSession = false
        onEraserReticleChanged?.invoke(0f, 0f, 0f, mode, false)

        if (sessionOriginalErasedStrokes.isNotEmpty()) {
            val removedList = sessionOriginalErasedStrokes.values.toList()
            val removedIds = removedList.map { it.id }
            val addedList = sessionLiveSubStrokes.values.toList()
            if (addedList.isNotEmpty()) {
                undoRedoManager.pushAction(
                    CanvasAction.ReplaceStrokes(removedList, addedList)
                )
                onCanvasMutation?.invoke(CanvasMutation.Replaced(removedIds, addedList))
            } else {
                undoRedoManager.pushAction(CanvasAction.RemoveStrokes(removedList))
                onCanvasMutation?.invoke(CanvasMutation.Removed(removedIds))
            }
            onStrokesChanged?.invoke(renderer.getCommittedStrokes())
        }
        sessionOriginalErasedStrokes.clear()
        sessionLiveSubStrokes.clear()
    }

    fun computeBounds(strokes: List<Stroke>): RectF? {
        if (strokes.isEmpty()) return null
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (s in strokes) {
            val pad = s.baseWidth * 0.5f
            for (p in s.points) {
                if (p.x - pad < minX) minX = p.x - pad
                if (p.x + pad > maxX) maxX = p.x + pad
                if (p.y - pad < minY) minY = p.y - pad
                if (p.y + pad > maxY) maxY = p.y + pad
            }
        }
        if (minX > maxX || minY > maxY) return null
        return RectF(minX, minY, maxX, maxY)
    }

    fun clearSelection() {
        if (isDraggingSelection) {
            isDraggingSelection = false
        }
        renderer.clearSelection()
        selectedStrokes.clear()
        selectionBounds = null
        activeLassoPoints.clear()
        totalDragDx = 0f
        totalDragDy = 0f
        onLassoPathChanged?.invoke(emptyList())
        onSelectionChanged?.invoke(emptyList(), null)
        onSelectionDragDelta?.invoke(0f, 0f)
        requestRedraw()
    }

    fun cutSelection() {
        if (selectedStrokes.isEmpty()) return
        val toCut = selectedStrokes.toList()
        StrokeClipboard.copy(toCut, isCut = true)
        val ids = toCut.map { it.id }.toSet()
        renderer.removeStrokes(ids)
        for (id in ids) spatialIndex.remove(id)
        onCanvasMutation?.invoke(CanvasMutation.Removed(ids.toList()))
        undoRedoManager.pushAction(CanvasAction.RemoveStrokes(toCut))
        clearSelection()
        requestRedraw()
        onStrokesChanged?.invoke(renderer.getCommittedStrokes())
    }

    fun copySelection() {
        if (selectedStrokes.isEmpty()) return
        StrokeClipboard.copy(selectedStrokes.toList(), isCut = false)
    }

    fun duplicateSelection() {
        if (selectedStrokes.isEmpty()) return
        val offset = 32f
        val duplicated = selectedStrokes.map { s ->
            s.copy(
                id = UUID.randomUUID().toString(),
                points = s.points.map { pt ->
                    StrokePoint(pt.x + offset, pt.y + offset, pt.pressure, pt.timeMillis)
                }
            )
        }
        renderer.addCommittedStrokes(duplicated)
        for (s in duplicated) spatialIndex.insert(s)
        onCanvasMutation?.invoke(CanvasMutation.Inserted(duplicated))
        undoRedoManager.pushAction(CanvasAction.InsertStrokes(duplicated))

        selectedStrokes.clear()
        selectedStrokes.addAll(duplicated)
        selectionBounds = computeBounds(duplicated)
        renderer.setSelectedStrokes(duplicated)
        onSelectionChanged?.invoke(selectedStrokes.toList(), selectionBounds)

        requestRedraw()
        onStrokesChanged?.invoke(renderer.getCommittedStrokes())
    }

    fun deleteSelection() {
        if (selectedStrokes.isEmpty()) return
        val toDelete = selectedStrokes.toList()
        val ids = toDelete.map { it.id }.toSet()
        renderer.removeStrokes(ids)
        for (id in ids) spatialIndex.remove(id)
        onCanvasMutation?.invoke(CanvasMutation.Removed(ids.toList()))
        undoRedoManager.pushAction(CanvasAction.RemoveStrokes(toDelete))
        clearSelection()
        requestRedraw()
        onStrokesChanged?.invoke(renderer.getCommittedStrokes())
    }

    fun pasteFromClipboard(worldX: Float, worldY: Float) {
        if (!StrokeClipboard.hasContent) return
        val source = StrokeClipboard.getStrokes()
        if (source.isEmpty()) return

        val srcBounds = computeBounds(source) ?: return
        val srcCenterX = (srcBounds.left + srcBounds.right) * 0.5f
        val srcCenterY = (srcBounds.top + srcBounds.bottom) * 0.5f
        val offsetX = worldX - srcCenterX
        val offsetY = worldY - srcCenterY

        val pasted = source.map { s ->
            s.copy(
                id = UUID.randomUUID().toString(),
                points = s.points.map { pt ->
                    StrokePoint(pt.x + offsetX, pt.y + offsetY, pt.pressure, pt.timeMillis)
                }
            )
        }

        renderer.addCommittedStrokes(pasted)
        for (s in pasted) spatialIndex.insert(s)
        onCanvasMutation?.invoke(CanvasMutation.Inserted(pasted))
        undoRedoManager.pushAction(CanvasAction.InsertStrokes(pasted))

        activeTool = ActiveTool.LASSO
        onToolChanged?.invoke(ActiveTool.LASSO)

        selectedStrokes.clear()
        selectedStrokes.addAll(pasted)
        selectionBounds = computeBounds(pasted)
        renderer.setSelectedStrokes(pasted)
        onSelectionChanged?.invoke(selectedStrokes.toList(), selectionBounds)

        StrokeClipboard.onPasted()

        requestRedraw()
        onStrokesChanged?.invoke(renderer.getCommittedStrokes())
    }

    fun translateSelectedStrokes(dx: Float, dy: Float) {
        if (selectedStrokes.isEmpty()) return
        val oldStrokes = selectedStrokes.toList()
        val oldIds = oldStrokes.map { it.id }.toSet()

        val movedStrokes = oldStrokes.map { s ->
            s.copy(
                id = UUID.randomUUID().toString(),
                points = s.points.map { pt ->
                    StrokePoint(pt.x + dx, pt.y + dy, pt.pressure, pt.timeMillis)
                }
            )
        }

        renderer.replaceStrokes(oldIds, movedStrokes)
        for (id in oldIds) spatialIndex.remove(id)
        for (s in movedStrokes) spatialIndex.insert(s)

        undoRedoManager.pushAction(CanvasAction.ReplaceStrokes(oldStrokes, movedStrokes))
        onCanvasMutation?.invoke(CanvasMutation.Replaced(oldIds.toList(), movedStrokes))

        selectedStrokes.clear()
        selectedStrokes.addAll(movedStrokes)
        selectionBounds = computeBounds(movedStrokes)
        renderer.updateSelectionDragDelta(0f, 0f)
        renderer.setSelectedStrokes(movedStrokes)
        onSelectionChanged?.invoke(selectedStrokes.toList(), selectionBounds)

        requestRedraw()
        onStrokesChanged?.invoke(renderer.getCommittedStrokes())
    }

    var onImagesChanged: ((List<com.antigrav.bignotes.core.model.CanvasImage>) -> Unit)? = null

    fun addImage(image: com.antigrav.bignotes.core.model.CanvasImage) {
        renderer.addImage(image)
        undoRedoManager.pushAction(CanvasAction.AddImage(image))
        requestRedraw()
        onImagesChanged?.invoke(renderer.images.toList())
    }

    fun removeImage(imageId: String) {
        val target = renderer.images.find { it.id == imageId } ?: return
        renderer.removeImage(imageId)
        undoRedoManager.pushAction(CanvasAction.RemoveImage(target))
        requestRedraw()
        onImagesChanged?.invoke(renderer.images.toList())
    }

    fun updateImage(previous: com.antigrav.bignotes.core.model.CanvasImage, current: com.antigrav.bignotes.core.model.CanvasImage) {
        renderer.updateImage(current)
        undoRedoManager.pushAction(CanvasAction.UpdateImage(previous, current))
        requestRedraw()
        onImagesChanged?.invoke(renderer.images.toList())
    }

    fun updateImageLive(current: com.antigrav.bignotes.core.model.CanvasImage) {
        renderer.updateImage(current)
        requestInteractiveRedraw()
        onImagesChanged?.invoke(renderer.images.toList())
    }

    fun setImages(images: List<com.antigrav.bignotes.core.model.CanvasImage>) {
        renderer.setImages(images)
        requestRedraw()
        onImagesChanged?.invoke(renderer.images.toList())
    }

    fun getImages(): List<com.antigrav.bignotes.core.model.CanvasImage> = renderer.images.toList()

    fun undo() {
        clearSelection()
        val action = undoRedoManager.undo() ?: return
        when (action) {
            is CanvasAction.AddStroke -> {
                renderer.removeStrokes(setOf(action.stroke.id))
                spatialIndex.remove(action.stroke.id)
                onCanvasMutation?.invoke(CanvasMutation.Removed(listOf(action.stroke.id)))
            }
            is CanvasAction.InsertStrokes -> {
                val ids = action.strokes.map { it.id }.toSet()
                renderer.removeStrokes(ids)
                for (id in ids) spatialIndex.remove(id)
                onCanvasMutation?.invoke(CanvasMutation.Removed(ids.toList()))
            }
            is CanvasAction.RemoveStrokes -> {
                for (s in action.strokes) {
                    renderer.addCommittedStroke(s)
                    spatialIndex.insert(s)
                }
                onCanvasMutation?.invoke(CanvasMutation.Inserted(action.strokes))
            }
            is CanvasAction.ReplaceStrokes -> {
                val addedIds = action.added.map { it.id }.toSet()
                renderer.removeStrokes(addedIds)
                for (id in addedIds) spatialIndex.remove(id)
                for (s in action.removed) {
                    renderer.addCommittedStroke(s)
                    spatialIndex.insert(s)
                }
                onCanvasMutation?.invoke(CanvasMutation.Replaced(addedIds.toList(), action.removed))
            }
            is CanvasAction.ClearCanvas -> {
                for (s in action.previousStrokes) {
                    renderer.addCommittedStroke(s)
                    spatialIndex.insert(s)
                }
                onCanvasMutation?.invoke(CanvasMutation.Inserted(action.previousStrokes))
            }
            is CanvasAction.AddImage -> {
                renderer.removeImage(action.image.id)
                onImagesChanged?.invoke(renderer.images.toList())
            }
            is CanvasAction.RemoveImage -> {
                renderer.addImage(action.image)
                onImagesChanged?.invoke(renderer.images.toList())
            }
            is CanvasAction.UpdateImage -> {
                renderer.updateImage(action.previous)
                onImagesChanged?.invoke(renderer.images.toList())
            }
        }
        requestRedraw()
        onStrokesChanged?.invoke(renderer.getCommittedStrokes())
    }

    fun redo() {
        clearSelection()
        val action = undoRedoManager.redo() ?: return
        when (action) {
            is CanvasAction.AddStroke -> {
                renderer.addCommittedStroke(action.stroke)
                spatialIndex.insert(action.stroke)
                onCanvasMutation?.invoke(CanvasMutation.Added(action.stroke))
            }
            is CanvasAction.InsertStrokes -> {
                renderer.addCommittedStrokes(action.strokes)
                for (s in action.strokes) spatialIndex.insert(s)
                onCanvasMutation?.invoke(CanvasMutation.Inserted(action.strokes))
            }
            is CanvasAction.RemoveStrokes -> {
                val ids = action.strokes.map { it.id }.toSet()
                renderer.removeStrokes(ids)
                for (id in ids) spatialIndex.remove(id)
                onCanvasMutation?.invoke(CanvasMutation.Removed(ids.toList()))
            }
            is CanvasAction.ReplaceStrokes -> {
                val removedIds = action.removed.map { it.id }.toSet()
                renderer.removeStrokes(removedIds)
                for (id in removedIds) spatialIndex.remove(id)
                for (s in action.added) {
                    renderer.addCommittedStroke(s)
                    spatialIndex.insert(s)
                }
                onCanvasMutation?.invoke(CanvasMutation.Replaced(removedIds.toList(), action.added))
            }
            is CanvasAction.ClearCanvas -> {
                renderer.clearCanvas()
                spatialIndex.clear()
                onCanvasMutation?.invoke(CanvasMutation.Cleared)
            }
            is CanvasAction.AddImage -> {
                renderer.addImage(action.image)
                onImagesChanged?.invoke(renderer.images.toList())
            }
            is CanvasAction.RemoveImage -> {
                renderer.removeImage(action.image.id)
                onImagesChanged?.invoke(renderer.images.toList())
            }
            is CanvasAction.UpdateImage -> {
                renderer.updateImage(action.current)
                onImagesChanged?.invoke(renderer.images.toList())
            }
        }
        requestRedraw()
        onStrokesChanged?.invoke(renderer.getCommittedStrokes())
    }


    fun loadPage(
        pageId: String,
        strokes: List<Stroke>,
        initialPanX: Float = 0f,
        initialPanY: Float = 0f,
        initialZoom: Float = 1.0f,
        initialRotation: Float = 0f,
        focusOnLastStroke: Boolean = true,
        images: List<com.antigrav.bignotes.core.model.CanvasImage> = emptyList()
    ) {
        clearSelection()
        syncCurrentPageStateFromRenderer()
        val isNewPage = !pageStates.containsKey(pageId)
        currentPageId = pageId
        val state = pageStates.getOrPut(pageId) {
            var panY = initialPanY
            if (focusOnLastStroke && strokes.isNotEmpty() && panY == 0f) {
                if (height > 0) {
                    val lastPt = strokes.lastOrNull { it.points.isNotEmpty() }?.points?.lastOrNull()
                    if (lastPt != null) {
                        val h = height.toFloat()
                        val visibleHeight = (h - renderer.viewOffsetY).coerceAtLeast(100f)
                        val effScale = initialZoom * renderer.pageScale
                        val targetScreenY = visibleHeight * 0.40f
                        panY = (targetScreenY - lastPt.y * effScale).coerceAtMost(0f)
                    }
                } else {
                    pendingFocusOnLastStroke = true
                }
            }
            PageCanvasState(
                panX = initialPanX,
                panY = panY,
                zoomScale = initialZoom,
                rotationDegrees = initialRotation,
                images = images.toMutableList()
            ).also { s ->
                s.undoRedoManager.onStateChanged = { canUndo, canRedo ->
                    if (currentPageId == pageId) {
                        onUndoRedoStateChanged?.invoke(canUndo, canRedo)
                    }
                }
            }
        }

        if (!isNewPage && images.isNotEmpty() && state.images.isEmpty()) {
            state.images.addAll(images)
        }

        renderer.panX = state.panX
        renderer.panY = state.panY
        renderer.zoomScale = state.zoomScale
        renderer.rotationDegrees = state.rotationDegrees
        renderer.isFlippedHorizontal = state.isFlippedHorizontal

        renderer.setStrokes(strokes)
        renderer.setImages(state.images)
        spatialIndex.rebuild(strokes)
        requestRedraw()

        onImagesChanged?.invoke(renderer.images.toList())
        onViewportChanged?.invoke(renderer.zoomScale, renderer.panX, renderer.panY, renderer.rotationDegrees)
        onUndoRedoStateChanged?.invoke(state.undoRedoManager.canUndo, state.undoRedoManager.canRedo)
    }

    fun scrollToLastStroke() {
        val strokes = renderer.getCommittedStrokes()
        if (strokes.isEmpty()) return
        val lastPt = strokes.lastOrNull { it.points.isNotEmpty() }?.points?.lastOrNull() ?: return
        val h = if (height > 0) height.toFloat() else context.resources.displayMetrics.heightPixels.toFloat()
        val visibleHeight = (h - renderer.viewOffsetY).coerceAtLeast(100f)
        val effScale = renderer.zoomScale * renderer.pageScale
        val targetScreenY = visibleHeight * 0.40f
        renderer.panY = (targetScreenY - lastPt.y * effScale).coerceAtMost(0f)
        syncCurrentPageStateFromRenderer()
        requestRedraw()
        onViewportChanged?.invoke(renderer.zoomScale, renderer.panX, renderer.panY, renderer.rotationDegrees)
    }

    fun loadPageStrokes(strokes: List<Stroke>, images: List<com.antigrav.bignotes.core.model.CanvasImage> = emptyList()) {
        loadPage(currentPageId.ifEmpty { "default_page" }, strokes, images = images)
    }

    private var pendingRedrawFrames = 0
    private val vsyncRedrawCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            frontBufferedRenderer?.renderMultiBufferedLayer(emptyList())
            pendingRedrawFrames--
            if (pendingRedrawFrames > 0) {
                Choreographer.getInstance().postFrameCallback(this)
            }
        }
    }

    fun requestRedraw() {
        renderer.isInteractiveGesture = false
        renderer.markDirty()
        scheduleRedrawFrames(3)
    }

    fun scheduleRedrawFrames(count: Int = 3) {
        val wasIdle = pendingRedrawFrames <= 0
        pendingRedrawFrames = maxOf(pendingRedrawFrames, count)
        if (wasIdle) {
            Choreographer.getInstance().postFrameCallback(vsyncRedrawCallback)
        }
    }

    private fun requestInteractiveRedraw() {
        scheduleRedrawFrames(1)
    }

    fun clearCanvas() {
        val existing = renderer.getCommittedStrokes()
        if (existing.isNotEmpty()) {
            undoRedoManager.pushAction(CanvasAction.ClearCanvas(existing))
        }
        renderer.clearCanvas()
        spatialIndex.clear()
        requestRedraw()
        onCanvasMutation?.invoke(CanvasMutation.Cleared)
        onStrokesChanged?.invoke(emptyList())
    }

    fun resetViewport() {
        renderer.panX = 0f
        renderer.panY = 0f
        renderer.zoomScale = 1.0f
        renderer.rotationDegrees = 0f
        renderer.isFlippedHorizontal = false
        syncCurrentPageStateFromRenderer()
        requestRedraw()
        onViewportChanged?.invoke(renderer.zoomScale, renderer.panX, renderer.panY, renderer.rotationDegrees)
    }

    fun resetRotation() {
        renderer.rotationDegrees = 0f
        syncCurrentPageStateFromRenderer()
        requestRedraw()
        onViewportChanged?.invoke(renderer.zoomScale, renderer.panX, renderer.panY, renderer.rotationDegrees)
    }

    fun resetZoom() {
        renderer.zoomScale = 1.0f
        renderer.panX = 0f
        renderer.panY = 0f
        syncCurrentPageStateFromRenderer()
        requestRedraw()
        onViewportChanged?.invoke(renderer.zoomScale, renderer.panX, renderer.panY, renderer.rotationDegrees)
    }

    fun setCanvasRotation(angle: Float) {
        renderer.rotationDegrees = angle
        syncCurrentPageStateFromRenderer()
        requestRedraw()
        onViewportChanged?.invoke(renderer.zoomScale, renderer.panX, renderer.panY, renderer.rotationDegrees)
    }

    fun toggleHorizontalFlip() {
        renderer.isFlippedHorizontal = !renderer.isFlippedHorizontal
        syncCurrentPageStateFromRenderer()
        requestRedraw()
        onViewportChanged?.invoke(renderer.zoomScale, renderer.panX, renderer.panY, renderer.rotationDegrees)
    }

    fun release() {
        stopFling()
        if (pendingRedrawFrames > 0) {
            pendingRedrawFrames = 0
            Choreographer.getInstance().removeFrameCallback(vsyncRedrawCallback)
        }
        velocityTracker?.recycle()
        velocityTracker = null
        frontBufferedRenderer?.release(true)
        renderer.cleanup()
    }
}

