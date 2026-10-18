package app.goodboy13.bignotes.ui.canvas

import android.graphics.PointF
import android.graphics.RectF
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.AutoFixNormal
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.*
import app.goodboy13.bignotes.ui.theme.CustomIcons
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import app.goodboy13.bignotes.core.math.model.MathOverlayState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import app.goodboy13.bignotes.core.export.PageFormat
import app.goodboy13.bignotes.core.export.PdfPagePaginator
import app.goodboy13.bignotes.core.gl.SketchCanvasView
import app.goodboy13.bignotes.core.model.ActiveTool
import app.goodboy13.bignotes.core.model.PencilTone
import app.goodboy13.bignotes.core.model.Stroke
import app.goodboy13.bignotes.core.model.BezierControlPoints
import app.goodboy13.bignotes.core.model.HoverCursorOrientation
import app.goodboy13.bignotes.ui.components.CanvasVerticalScrollbar

private data class ReticleState(
    val screenX: Float = 0f,
    val screenY: Float = 0f,
    val radius: Float = 0f,
    val isVisible: Boolean = false
)

private data class HoverReticleState(
    val screenX: Float = 0f,
    val screenY: Float = 0f,
    val radius: Float = 0f,
    val tool: ActiveTool = ActiveTool.FOUNTAIN_PEN,
    val isPrecisionEraser: Boolean = false,
    val isVisible: Boolean = false
)

@Composable
fun BigNotesCanvasView(
    activeTool: ActiveTool,
    strokeSize: Float,
    eraserRadius: Float = 36f,
    holdToEraseEnabled: Boolean = true,
    holdToEraseDelayMs: Long = 350L,
    hoverCursorEnabled: Boolean = true,
    hoverCursorOrientation: HoverCursorOrientation = HoverCursorOrientation.UP_RIGHT,
    basePageWidth: Float,
    viewOffsetX: Float = 0f,
    viewOffsetY: Float = 0f,
    bezierConfig: BezierControlPoints = app.goodboy13.bignotes.core.model.PressureCurveDefaults.STANDARD,
    velocityBezierConfig: BezierControlPoints = app.goodboy13.bignotes.core.model.VelocityCurveDefaults.CALLIGRAPHIC,
    velocityThinning: Float = 0.60f,
    pageFormat: PageFormat = PageFormat.A4,
    showPageSeparators: Boolean = true,
    inkBleedConfig: app.goodboy13.bignotes.core.model.InkBleedConfig = app.goodboy13.bignotes.core.model.InkBleedConfig.DEFAULT,
    isCanvasEnabled: Boolean = true,
    hasFolders: Boolean = true,
    onAddNote: () -> Unit = {},
    onAddFolder: () -> Unit = {},
    onUndoRedoStateChanged: (canUndo: Boolean, canRedo: Boolean) -> Unit,
    onZoomChanged: (Float) -> Unit,
    onCanvasBound: (SketchCanvasView) -> Unit,
    onStrokesChanged: ((List<Stroke>) -> Unit)? = null,
    onStrokeStarted: (() -> String)? = null,
    onPenContactChanged: ((Boolean) -> Unit)? = null,
    selectedImage: app.goodboy13.bignotes.core.model.CanvasImage? = null,
    onSelectedImageChange: ((app.goodboy13.bignotes.core.model.CanvasImage?) -> Unit)? = null,
    activeSearchHit: app.goodboy13.bignotes.core.search.model.SearchHit? = null,
    onDismissSearchHit: (() -> Unit)? = null,
    onCopySelectedStrokesAsText: ((List<Stroke>) -> Unit)? = null,
    onSolveMathSelectedStrokes: ((List<Stroke>) -> Unit)? = null,
    mathOverlayState: MathOverlayState? = null,
    onInsertMathAnswer: (() -> Unit)? = null,
    onCopyMathAnswer: (() -> Unit)? = null,
    onDismissMathOverlay: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var reticle by remember { mutableStateOf(ReticleState()) }
    var hoverReticle by remember { mutableStateOf(HoverReticleState()) }
    var canvasViewRef by remember { mutableStateOf<SketchCanvasView?>(null) }
    var lassoPoints by remember { mutableStateOf<List<PointF>>(emptyList()) }
    var selectedStrokes by remember { mutableStateOf<List<Stroke>>(emptyList()) }
    var selectionBounds by remember { mutableStateOf<RectF?>(null) }
    var selectionDragDx by remember { mutableFloatStateOf(0f) }
    var selectionDragDy by remember { mutableFloatStateOf(0f) }
    var viewportTick by remember { mutableIntStateOf(0) }
    var isScrolling by remember { mutableStateOf(false) }

    var internalSelectedImage by remember { mutableStateOf<app.goodboy13.bignotes.core.model.CanvasImage?>(null) }
    val effectiveSelectedImage = selectedImage ?: internalSelectedImage
    val updateSelectedImage: (app.goodboy13.bignotes.core.model.CanvasImage?) -> Unit = { img ->
        internalSelectedImage = img
        onSelectedImageChange?.invoke(img)
    }

    val worldPageHeight = remember(pageFormat) {
        PdfPagePaginator.getWorldPageHeight(pageFormat)
    }

    LaunchedEffect(activeSearchHit) {
        activeSearchHit?.let { hit ->
            canvasViewRef?.scrollToWorldY(hit.minY)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { context ->
                SketchCanvasView(context).apply {
                    this.activeTool = activeTool
                    this.bezierConfig = bezierConfig
                    this.velocityBezierConfig = velocityBezierConfig
                    this.velocityThinning = velocityThinning
                    this.onStrokeStarted = onStrokeStarted
                    renderer.currentTone = PencilTone.INK_BLACK
                    renderer.currentBaseWidth = strokeSize
                    renderer.basePageWidth = basePageWidth
                    renderer.viewOffsetX = viewOffsetX
                    renderer.viewOffsetY = viewOffsetY
                    this.showPageSeparators = showPageSeparators
                    this.inkBleedConfig = inkBleedConfig
                    this.worldPageHeight = worldPageHeight
                    this.eraserRadius = eraserRadius
                    this.holdToEraseEnabled = holdToEraseEnabled
                    this.holdToEraseDelayMs = holdToEraseDelayMs

                    this.onUndoRedoStateChanged = onUndoRedoStateChanged
                    this.onViewportChanged = { zoom, _, _, _ ->
                        onZoomChanged(zoom)
                        viewportTick++
                    }
                    this.onScrollActiveChanged = { scrolling ->
                        isScrolling = scrolling
                    }
                    this.onStrokesChanged = onStrokesChanged
                    this.onPenContactChanged = onPenContactChanged
                    this.onEraserReticleChanged = { sx, sy, radius, _, isVisible ->
                        reticle = ReticleState(sx, sy, radius, isVisible)
                    }
                    this.onHoverReticleChanged = { sx, sy, radius, tool, isPrecision, isVisible ->
                        hoverReticle = HoverReticleState(sx, sy, radius, tool, isPrecision, isVisible)
                    }
                    this.onLassoPathChanged = { pts ->
                        lassoPoints = pts
                    }
                    this.onSelectionChanged = { strokes, bounds ->
                        selectedStrokes = strokes
                        selectionBounds = bounds
                        selectionDragDx = 0f
                        selectionDragDy = 0f
                    }
                    this.onSelectionDragDelta = { dx, dy ->
                        selectionDragDx = dx
                        selectionDragDy = dy
                    }
                    this.onImageTapped = { img ->
                        updateSelectedImage(img)
                    }
                    this.onImagesChanged = { imgs ->
                        if (effectiveSelectedImage != null && imgs.none { it.id == effectiveSelectedImage.id }) {
                            updateSelectedImage(null)
                        }
                    }

                    canvasViewRef = this
                    onCanvasBound(this)
                    this.isEnabled = isCanvasEnabled
                }
            },
            update = { view ->
                if (view.isEnabled != isCanvasEnabled) {
                    view.isEnabled = isCanvasEnabled
                }
                view.activeTool = activeTool
                view.bezierConfig = bezierConfig
                view.velocityBezierConfig = velocityBezierConfig
                view.velocityThinning = velocityThinning
                view.onStrokeStarted = onStrokeStarted
                view.renderer.currentBaseWidth = strokeSize
                view.eraserRadius = eraserRadius
                view.holdToEraseEnabled = holdToEraseEnabled
                view.holdToEraseDelayMs = holdToEraseDelayMs
                if (basePageWidth > 50f && view.renderer.basePageWidth != basePageWidth) {
                    view.renderer.basePageWidth = basePageWidth
                    view.requestRedraw()
                }
                if (view.viewOffsetX != viewOffsetX || view.viewOffsetY != viewOffsetY) {
                    view.updateViewportOffsets(viewOffsetX, viewOffsetY, anchorTopRight = true)
                }
                if (view.showPageSeparators != showPageSeparators) {
                    view.showPageSeparators = showPageSeparators
                }
                if (view.inkBleedConfig != inkBleedConfig) {
                    view.inkBleedConfig = inkBleedConfig
                }
                if (view.worldPageHeight != worldPageHeight) {
                    view.worldPageHeight = worldPageHeight
                }
                view.onStrokesChanged = onStrokesChanged
                view.onPenContactChanged = onPenContactChanged
            },
            modifier = Modifier.fillMaxSize()
        )

        // 1. Eraser Cursor Reticle Overlay
        if (reticle.isVisible && reticle.radius > 0f) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val centerOffset = Offset(reticle.screenX, reticle.screenY)
                val outerRadius = reticle.radius
                // High-contrast dual stroke: dark outer border + light inner border for visibility on any paper background
                drawCircle(
                    color = Color.Black.copy(alpha = 0.65f),
                    radius = outerRadius,
                    center = centerOffset,
                    style = DrawStroke(width = 2.dp.toPx())
                )
                if (outerRadius > 2.dp.toPx()) {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.85f),
                        radius = outerRadius - 1.dp.toPx(),
                        center = centerOffset,
                        style = DrawStroke(width = 1.dp.toPx())
                    )
                }
                // Center point indicator
                drawCircle(
                    color = Color.Black.copy(alpha = 0.75f),
                    radius = 2.dp.toPx(),
                    center = centerOffset
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.85f),
                    radius = 1.dp.toPx(),
                    center = centerOffset
                )
            }
        }

        // 1b. Live Hover Tool Logo Cursor Overlay (Tip aligned directly at target point, just like a mouse cursor)
        if (hoverReticle.isVisible && hoverCursorEnabled) {
            val isEraser = hoverReticle.isPrecisionEraser ||
                    hoverReticle.tool == ActiveTool.PRECISION_ERASER ||
                    hoverReticle.tool == ActiveTool.WHOLE_STROKE_ERASER

            // Rotation angle: 4 diagonal orientations for all non-eraser tools, standard 45° for erasers
            val rotationDegrees = if (isEraser) 45f else hoverCursorOrientation.degrees

            Canvas(modifier = Modifier.fillMaxSize()) {
                withTransform({
                    translate(hoverReticle.screenX, hoverReticle.screenY)
                    rotate(rotationDegrees, pivot = Offset.Zero)
                }) {
                    drawHoverToolCursor(
                        tool = hoverReticle.tool,
                        isPrecisionEraser = hoverReticle.isPrecisionEraser
                    )
                }
            }
        } else if (hoverReticle.isVisible && !hoverCursorEnabled && !hoverReticle.isPrecisionEraser) {
            // When hover logo cursor is disabled, show subtle pinpoint landing micro-dot for drawing tools
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(hoverReticle.screenX, hoverReticle.screenY)
                drawCircle(color = Color.Black.copy(alpha = 0.65f), radius = 2.dp.toPx(), center = center)
                drawCircle(color = Color.White.copy(alpha = 0.90f), radius = 1.2.dp.toPx(), center = center)
            }
        }

        // 2. Active Lasso Trail Overlay
        if (lassoPoints.size >= 2) {
            val view = canvasViewRef
            if (view != null) {
                @Suppress("UNUSED_VARIABLE")
                val tick = viewportTick
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val path = Path()
                    var first = true
                    for (pt in lassoPoints) {
                        val (sx, sy) = view.worldToScreen(pt.x, pt.y)
                        if (first) {
                            path.moveTo(sx, sy)
                            first = false
                        } else {
                            path.lineTo(sx, sy)
                        }
                    }
                    drawPath(
                        path = path,
                        color = Color(0xFF1E88E5),
                        style = DrawStroke(
                            width = 2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                        )
                    )
                }
            }
        }

        // 3. Lasso Selection Marquee & Floating Action Pill
        val bounds = selectionBounds
        val view = canvasViewRef
        if (bounds != null && selectedStrokes.isNotEmpty() && view != null) {
            @Suppress("UNUSED_VARIABLE")
            val tick = viewportTick
            val (sLeft, sTop) = view.worldToScreen(bounds.left + selectionDragDx, bounds.top + selectionDragDy)
            val (sRight, sBottom) = view.worldToScreen(bounds.right + selectionDragDx, bounds.bottom + selectionDragDy)

            val left = minOf(sLeft, sRight)
            val right = maxOf(sLeft, sRight)
            val top = minOf(sTop, sBottom)
            val bottom = maxOf(sTop, sBottom)
            val boxWidth = right - left
            val boxHeight = bottom - top

            Canvas(modifier = Modifier.fillMaxSize()) {
                // Subtle shaded interior
                drawRect(
                    color = Color(0xFF1E88E5).copy(alpha = 0.07f),
                    topLeft = Offset(left, top),
                    size = Size(boxWidth, boxHeight)
                )

                // Dashed marquee border
                drawRect(
                    color = Color(0xFF1E88E5),
                    topLeft = Offset(left, top),
                    size = Size(boxWidth, boxHeight),
                    style = DrawStroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f)
                    )
                )

                // Corner anchor circles
                val handleRadius = 4.dp.toPx()
                val corners = listOf(
                    Offset(left, top),
                    Offset(right, top),
                    Offset(left, bottom),
                    Offset(right, bottom)
                )
                for (corner in corners) {
                    drawCircle(color = Color.White, radius = handleRadius, center = corner)
                    drawCircle(
                        color = Color(0xFF1E88E5),
                        radius = handleRadius,
                        center = corner,
                        style = DrawStroke(width = 1.5.dp.toPx())
                    )
                }
            }

            // Floating action pill centered above (or below) selection box
            val density = LocalDensity.current
            val screenConfig = LocalConfiguration.current
            val pillHeightDp = 36.dp
            val pillHeightPx = with(density) { pillHeightDp.toPx() }
            val pillY = if (top > pillHeightPx + 14f) {
                top - pillHeightPx - 8f
            } else {
                bottom + 8f
            }
            val pillCenterX = (left + right) * 0.5f
            val pillXDp = with(density) { (pillCenterX - 75f).toDp() }
            val pillYDp = with(density) { pillY.toDp() }

            val minXDp = 8.dp
            val maxXDp = (screenConfig.screenWidthDp - 170).dp
            val clampedXDp = pillXDp.coerceIn(minXDp, maxOf(minXDp, maxXDp))
            val clampedYDp = pillYDp.coerceAtLeast(8.dp)

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp),
                tonalElevation = 6.dp,
                shadowElevation = 4.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                modifier = Modifier
                    .offset(x = clampedXDp, y = clampedYDp)
                    .height(pillHeightDp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.padding(horizontal = 6.dp)
                ) {
                    IconButton(
                        onClick = { onCopySelectedStrokesAsText?.invoke(selectedStrokes) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.TextFields,
                            contentDescription = "Copy as Text",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    if (onSolveMathSelectedStrokes != null) {
                        IconButton(
                            onClick = { onSolveMathSelectedStrokes.invoke(selectedStrokes) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Calculate,
                                contentDescription = "Solve Math",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = { view.duplicateSelection() },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Duplicate Selection",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    IconButton(
                        onClick = { view.deleteSelection() },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Selection",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    IconButton(
                        onClick = { view.clearSelection() },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Deselect",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // 3.4. Math Result Overlay
        MathResultOverlay(
            overlayState = mathOverlayState,
            canvasView = canvasViewRef,
            viewportTick = viewportTick,
            onInsertAnswer = { onInsertMathAnswer?.invoke() },
            onCopyAnswer = { onCopyMathAnswer?.invoke() },
            onDismiss = { onDismissMathOverlay?.invoke() }
        )

        // 3.5. Image Manipulation and Cropping Overlay
        ImageManipulationOverlay(
            selectedImage = effectiveSelectedImage,
            canvasView = canvasViewRef,
            viewportTick = viewportTick,
            onUpdateImageLive = { updated ->
                updateSelectedImage(updated)
                canvasViewRef?.updateImageLive(updated)
            },
            onUpdateImage = { prev, curr ->
                updateSelectedImage(curr)
                canvasViewRef?.updateImage(prev, curr)
            },
            onDeleteImage = { img ->
                canvasViewRef?.removeImage(img.id)
                updateSelectedImage(null)
            },
            onDuplicateImage = { img ->
                val duplicated = img.copy(
                    id = java.util.UUID.randomUUID().toString(),
                    centerX = img.centerX + 40f,
                    centerY = img.centerY + 40f,
                    zIndex = System.currentTimeMillis()
                )
                canvasViewRef?.addImage(duplicated)
                updateSelectedImage(duplicated)
            },
            onDeselect = {
                updateSelectedImage(null)
            }
        )

        // 3.8. Active Search Hit Visual Highlight Box
        val searchView = canvasViewRef
        val hit = activeSearchHit
        if (searchView != null && hit != null && isCanvasEnabled) {
            @Suppress("UNUSED_VARIABLE")
            val tick = viewportTick
            val (screenMinX, screenMinY) = searchView.worldToScreen(hit.minX, hit.minY)
            val (screenMaxX, screenMaxY) = searchView.worldToScreen(hit.maxX, hit.maxY)
            val left = minOf(screenMinX, screenMaxX) - 10f
            val top = minOf(screenMinY, screenMaxY) - 8f
            val right = maxOf(screenMinX, screenMaxX) + 10f
            val bottom = maxOf(screenMinY, screenMaxY) + 8f
            val boxWidth = maxOf(20f, right - left)
            val boxHeight = maxOf(20f, bottom - top)

            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRoundRect(
                    color = Color(0xFFFFB74D).copy(alpha = 0.28f),
                    topLeft = Offset(left, top),
                    size = Size(boxWidth, boxHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx(), 10.dp.toPx())
                )
                drawRoundRect(
                    color = Color(0xFFF57C00).copy(alpha = 0.85f),
                    topLeft = Offset(left, top),
                    size = Size(boxWidth, boxHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx(), 10.dp.toPx()),
                    style = DrawStroke(
                        width = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 10f), 0f)
                    )
                )
            }

            // Floating badge above the matched handwriting cluster
            val density = LocalDensity.current
            val badgeXDp = with(density) { left.coerceAtLeast(8f).toDp() }
            val badgeYDp = with(density) { (top - 36f).coerceAtLeast(viewOffsetY + 8f).toDp() }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, Color(0xFFF57C00).copy(alpha = 0.6f)),
                shadowElevation = 6.dp,
                modifier = Modifier.offset(x = badgeXDp, y = badgeYDp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = Color(0xFFF57C00),
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = hit.matchedText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(
                        onClick = { onDismissSearchHit?.invoke() },
                        modifier = Modifier.size(18.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss Highlight",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }

        // 4. Vertical Fast Scrollbar Overlay on Right Edge
        val canvasView = canvasViewRef
        if (canvasView != null && isCanvasEnabled) {
            @Suppress("UNUSED_VARIABLE")
            val tick = viewportTick
            val scrollInfo = canvasView.getVerticalScrollInfo()
            val topOffsetDp = with(LocalDensity.current) { viewOffsetY.toDp() + 8.dp }

            CanvasVerticalScrollbar(
                isScrolling = isScrolling,
                scrollFraction = scrollInfo.scrollFraction,
                thumbRatio = scrollInfo.thumbRatio,
                currentScrollWorldY = scrollInfo.currentScrollWorldY,
                worldPageHeight = worldPageHeight,
                onScrollToFraction = { fraction ->
                    canvasView.scrollToFraction(fraction)
                },
                onScrubbingFinished = {
                    canvasView.finishScrollScrubbing()
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = topOffsetDp, bottom = 16.dp)
            )
        }

        // 5. Disabled Canvas Empty State / Call-to-Action Placeholder
        if (!isCanvasEnabled) {
            val contentPaddingStart = with(LocalDensity.current) { viewOffsetX.toDp() }
            val contentPaddingTop = with(LocalDensity.current) { viewOffsetY.toDp() }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = contentPaddingStart, top = contentPaddingTop),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .padding(24.dp)
                        .widthIn(max = 380.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        if (!hasFolders) {
                            Icon(
                                imageVector = Icons.Default.FolderOff,
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "No Folders Created",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Create a folder first to organize and start taking notes.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Button(
                                onClick = onAddFolder,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CreateNewFolder,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("New Folder")
                            }
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.NoteAdd,
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "No Active Note",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Create a new note to start writing and drawing.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Button(
                                onClick = onAddNote,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.NoteAdd,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Tap to create a new note")
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Draws the high-precision live stylus hover cursor, designed with the exact contact tip
 * aligned at (0, 0) and the tool body angled up-and-to-the-right at 45 degrees, matching
 * natural hand-held digitizer pens and desktop cursors.
 */
private fun DrawScope.drawHoverToolCursor(
    tool: ActiveTool,
    isPrecisionEraser: Boolean
) {
    // 1. Soft drop shadow beneath the cursor
    val shadowOffset = 1.5.dp.toPx()
    withTransform({
        translate(shadowOffset, shadowOffset)
    }) {
        val shadowAlpha = 0.20f
        when {
            isPrecisionEraser || tool == ActiveTool.PRECISION_ERASER || tool == ActiveTool.WHOLE_STROKE_ERASER -> {
                val p = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(-3.5.dp.toPx(), -4.5.dp.toPx())
                    lineTo(-4.dp.toPx(), -23.dp.toPx())
                    lineTo(4.dp.toPx(), -23.dp.toPx())
                    lineTo(3.5.dp.toPx(), -4.5.dp.toPx())
                    close()
                }
                drawPath(p, Color.Black.copy(alpha = shadowAlpha))
            }
            tool == ActiveTool.PENCIL -> {
                val p = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(-1.2.dp.toPx(), -4.5.dp.toPx())
                    lineTo(-3.2.dp.toPx(), -10.dp.toPx())
                    lineTo(-3.2.dp.toPx(), -22.dp.toPx())
                    lineTo(-3.0.dp.toPx(), -25.dp.toPx())
                    lineTo(3.0.dp.toPx(), -25.dp.toPx())
                    lineTo(3.2.dp.toPx(), -22.dp.toPx())
                    lineTo(3.2.dp.toPx(), -10.dp.toPx())
                    lineTo(1.2.dp.toPx(), -4.5.dp.toPx())
                    close()
                }
                drawPath(p, Color.Black.copy(alpha = shadowAlpha))
            }
            tool == ActiveTool.HIGHLIGHTER -> {
                val p = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(-4.dp.toPx(), -2.2.dp.toPx())
                    lineTo(-3.5.dp.toPx(), -8.dp.toPx())
                    lineTo(-4.5.dp.toPx(), -10.dp.toPx())
                    lineTo(-4.5.dp.toPx(), -25.dp.toPx())
                    lineTo(4.5.dp.toPx(), -25.dp.toPx())
                    lineTo(4.5.dp.toPx(), -10.dp.toPx())
                    lineTo(2.5.dp.toPx(), -8.dp.toPx())
                    close()
                }
                drawPath(p, Color.Black.copy(alpha = shadowAlpha))
            }
            tool == ActiveTool.LASSO -> {
                drawCircle(Color.Black.copy(alpha = shadowAlpha), radius = 3.dp.toPx(), center = Offset.Zero)
            }
            else -> {
                val p = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(-2.2.dp.toPx(), -5.5.dp.toPx())
                    cubicTo(-4.8.dp.toPx(), -9.dp.toPx(), -4.2.dp.toPx(), -13.5.dp.toPx(), -2.6.dp.toPx(), -16.dp.toPx())
                    lineTo(-2.6.dp.toPx(), -25.dp.toPx())
                    lineTo(2.6.dp.toPx(), -25.dp.toPx())
                    lineTo(2.6.dp.toPx(), -16.dp.toPx())
                    cubicTo(4.2.dp.toPx(), -13.5.dp.toPx(), 4.8.dp.toPx(), -9.dp.toPx(), 2.2.dp.toPx(), -5.5.dp.toPx())
                    close()
                }
                drawPath(p, Color.Black.copy(alpha = shadowAlpha))
            }
        }
    }

    // 2. High-contrast silhouette and tool-specific detailed rendering
    when {
        isPrecisionEraser || tool == ActiveTool.PRECISION_ERASER || tool == ActiveTool.WHOLE_STROKE_ERASER -> {
            val eraserOutline = Path().apply {
                moveTo(0f, 0f)
                lineTo(-3.5.dp.toPx(), -4.5.dp.toPx())
                lineTo(-4.dp.toPx(), -23.dp.toPx())
                lineTo(4.dp.toPx(), -23.dp.toPx())
                lineTo(3.5.dp.toPx(), -4.5.dp.toPx())
                close()
            }
            // High-contrast dual outline
            drawPath(eraserOutline, color = Color.Black.copy(alpha = 0.85f), style = DrawStroke(width = 2.4.dp.toPx(), join = StrokeJoin.Round))
            drawPath(eraserOutline, color = Color.White.copy(alpha = 0.95f), style = DrawStroke(width = 1.2.dp.toPx(), join = StrokeJoin.Round))

            // White vinyl eraser tip
            val eraserTip = Path().apply {
                moveTo(0f, 0f)
                lineTo(-3.5.dp.toPx(), -4.5.dp.toPx())
                lineTo(-3.8.dp.toPx(), -9.dp.toPx())
                lineTo(3.8.dp.toPx(), -9.dp.toPx())
                lineTo(3.5.dp.toPx(), -4.5.dp.toPx())
                close()
            }
            drawPath(eraserTip, color = Color(0xFFF8FAFC))

            // Protective sleeve
            val sleevePath = Path().apply {
                moveTo(-3.8.dp.toPx(), -9.dp.toPx())
                lineTo(-4.dp.toPx(), -23.dp.toPx())
                lineTo(4.dp.toPx(), -23.dp.toPx())
                lineTo(3.8.dp.toPx(), -9.dp.toPx())
                close()
            }
            drawPath(sleevePath, color = Color(0xFF0284C7))
            // Accent stripe on sleeve
            drawLine(
                color = Color.White,
                start = Offset(-3.9.dp.toPx(), -13.dp.toPx()),
                end = Offset(3.9.dp.toPx(), -13.dp.toPx()),
                strokeWidth = 1.5.dp.toPx()
            )
        }

        tool == ActiveTool.PENCIL -> {
            val pencilOutline = Path().apply {
                moveTo(0f, 0f)
                lineTo(-1.2.dp.toPx(), -4.5.dp.toPx())
                lineTo(-3.2.dp.toPx(), -10.dp.toPx())
                lineTo(-3.2.dp.toPx(), -22.dp.toPx())
                lineTo(-3.0.dp.toPx(), -25.dp.toPx())
                lineTo(3.0.dp.toPx(), -25.dp.toPx())
                lineTo(3.2.dp.toPx(), -22.dp.toPx())
                lineTo(3.2.dp.toPx(), -10.dp.toPx())
                lineTo(1.2.dp.toPx(), -4.5.dp.toPx())
                close()
            }
            drawPath(pencilOutline, color = Color.Black.copy(alpha = 0.85f), style = DrawStroke(width = 2.4.dp.toPx(), join = StrokeJoin.Round))
            drawPath(pencilOutline, color = Color.White.copy(alpha = 0.95f), style = DrawStroke(width = 1.2.dp.toPx(), join = StrokeJoin.Round))

            // Graphite lead tip
            val leadPath = Path().apply {
                moveTo(0f, 0f)
                lineTo(-1.2.dp.toPx(), -4.5.dp.toPx())
                lineTo(1.2.dp.toPx(), -4.5.dp.toPx())
                close()
            }
            drawPath(leadPath, color = Color(0xFF263238))

            // Sharpened cedar wood cone
            val woodPath = Path().apply {
                moveTo(-1.2.dp.toPx(), -4.5.dp.toPx())
                lineTo(-3.2.dp.toPx(), -10.dp.toPx())
                lineTo(3.2.dp.toPx(), -10.dp.toPx())
                lineTo(1.2.dp.toPx(), -4.5.dp.toPx())
                close()
            }
            drawPath(woodPath, color = Color(0xFFF0D6A7))

            // Hexagonal yellow pencil body
            val bodyPath = Path().apply {
                moveTo(-3.2.dp.toPx(), -10.dp.toPx())
                lineTo(-3.2.dp.toPx(), -20.dp.toPx())
                lineTo(3.2.dp.toPx(), -20.dp.toPx())
                lineTo(3.2.dp.toPx(), -10.dp.toPx())
                close()
            }
            drawPath(bodyPath, color = Color(0xFFFFB300))
            drawLine(
                color = Color(0xFFFFE082),
                start = Offset(0f, -10.dp.toPx()),
                end = Offset(0f, -20.dp.toPx()),
                strokeWidth = 2.dp.toPx()
            )

            // Metal ferrule
            val ferrulePath = Path().apply {
                moveTo(-3.2.dp.toPx(), -20.dp.toPx())
                lineTo(-3.2.dp.toPx(), -22.5.dp.toPx())
                lineTo(3.2.dp.toPx(), -22.5.dp.toPx())
                lineTo(3.2.dp.toPx(), -20.dp.toPx())
                close()
            }
            drawPath(ferrulePath, color = Color(0xFFCFD8DC))

            // Pink eraser dome
            val eraserDome = Path().apply {
                moveTo(-3.0.dp.toPx(), -22.5.dp.toPx())
                lineTo(-2.8.dp.toPx(), -25.dp.toPx())
                lineTo(2.8.dp.toPx(), -25.dp.toPx())
                lineTo(3.0.dp.toPx(), -22.5.dp.toPx())
                close()
            }
            drawPath(eraserDome, color = Color(0xFFF48FB1))
        }

        tool == ActiveTool.HIGHLIGHTER -> {
            val chiselOutline = Path().apply {
                moveTo(0f, 0f)
                lineTo(-4.dp.toPx(), -2.2.dp.toPx())
                lineTo(-3.5.dp.toPx(), -8.dp.toPx())
                lineTo(-4.5.dp.toPx(), -10.dp.toPx())
                lineTo(-4.5.dp.toPx(), -25.dp.toPx())
                lineTo(4.5.dp.toPx(), -25.dp.toPx())
                lineTo(4.5.dp.toPx(), -10.dp.toPx())
                lineTo(2.5.dp.toPx(), -8.dp.toPx())
                close()
            }
            drawPath(chiselOutline, color = Color.Black.copy(alpha = 0.85f), style = DrawStroke(width = 2.4.dp.toPx(), join = StrokeJoin.Round))
            drawPath(chiselOutline, color = Color.White.copy(alpha = 0.95f), style = DrawStroke(width = 1.2.dp.toPx(), join = StrokeJoin.Round))

            // Chisel felt nib
            val chiselNib = Path().apply {
                moveTo(0f, 0f)
                lineTo(-4.dp.toPx(), -2.2.dp.toPx())
                lineTo(-3.5.dp.toPx(), -8.dp.toPx())
                lineTo(2.5.dp.toPx(), -8.dp.toPx())
                close()
            }
            drawPath(chiselNib, color = Color(0xFFEEFF41))

            // Collar
            val collarPath = Path().apply {
                moveTo(-3.5.dp.toPx(), -8.dp.toPx())
                lineTo(-4.5.dp.toPx(), -11.dp.toPx())
                lineTo(4.5.dp.toPx(), -11.dp.toPx())
                lineTo(2.5.dp.toPx(), -8.dp.toPx())
                close()
            }
            drawPath(collarPath, color = Color(0xFF212121))

            // Wide marker barrel
            val barrelPath = Path().apply {
                moveTo(-4.5.dp.toPx(), -11.dp.toPx())
                lineTo(-4.5.dp.toPx(), -25.dp.toPx())
                lineTo(4.5.dp.toPx(), -25.dp.toPx())
                lineTo(4.5.dp.toPx(), -11.dp.toPx())
                close()
            }
            drawPath(barrelPath, color = Color(0xFFF5F5F5))
            drawLine(
                color = Color(0xFFEEFF41),
                start = Offset(0f, -12.dp.toPx()),
                end = Offset(0f, -24.dp.toPx()),
                strokeWidth = 3.dp.toPx()
            )
        }

        tool == ActiveTool.LASSO -> {
            val lassoLoop = Path().apply {
                moveTo(0f, 0f)
                cubicTo(
                    -5.dp.toPx(), -8.dp.toPx(),
                    -7.dp.toPx(), -18.dp.toPx(),
                    0f, -22.dp.toPx()
                )
                cubicTo(
                    7.dp.toPx(), -18.dp.toPx(),
                    5.dp.toPx(), -8.dp.toPx(),
                    0f, 0f
                )
            }
            drawPath(
                lassoLoop,
                color = Color.Black.copy(alpha = 0.85f),
                style = DrawStroke(width = 2.4.dp.toPx(), join = StrokeJoin.Round)
            )
            drawPath(
                lassoLoop,
                color = Color(0xFF0284C7),
                style = DrawStroke(
                    width = 1.3.dp.toPx(),
                    join = StrokeJoin.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))
                )
            )
            // Knot dot at contact point
            drawCircle(Color.Black.copy(alpha = 0.85f), radius = 2.4.dp.toPx(), center = Offset.Zero)
            drawCircle(Color.White, radius = 1.2.dp.toPx(), center = Offset.Zero)
        }

        else -> {
            // FOUNTAIN_PEN (and default)
            val nibOutline = Path().apply {
                moveTo(0f, 0f)
                lineTo(-2.2.dp.toPx(), -5.5.dp.toPx())
                cubicTo(-4.8.dp.toPx(), -9.dp.toPx(), -4.2.dp.toPx(), -13.5.dp.toPx(), -2.6.dp.toPx(), -16.dp.toPx())
                lineTo(-2.6.dp.toPx(), -25.dp.toPx())
                lineTo(2.6.dp.toPx(), -25.dp.toPx())
                lineTo(2.6.dp.toPx(), -16.dp.toPx())
                cubicTo(4.2.dp.toPx(), -13.5.dp.toPx(), 4.8.dp.toPx(), -9.dp.toPx(), 2.2.dp.toPx(), -5.5.dp.toPx())
                close()
            }
            drawPath(nibOutline, color = Color.Black.copy(alpha = 0.85f), style = DrawStroke(width = 2.4.dp.toPx(), join = StrokeJoin.Round))
            drawPath(nibOutline, color = Color.White.copy(alpha = 0.95f), style = DrawStroke(width = 1.2.dp.toPx(), join = StrokeJoin.Round))

            // Nib blade
            val bladePath = Path().apply {
                moveTo(0f, 0f)
                lineTo(-2.2.dp.toPx(), -5.5.dp.toPx())
                cubicTo(-4.8.dp.toPx(), -9.dp.toPx(), -4.2.dp.toPx(), -13.5.dp.toPx(), -2.6.dp.toPx(), -16.dp.toPx())
                lineTo(2.6.dp.toPx(), -16.dp.toPx())
                cubicTo(4.2.dp.toPx(), -13.5.dp.toPx(), 4.8.dp.toPx(), -9.dp.toPx(), 2.2.dp.toPx(), -5.5.dp.toPx())
                close()
            }
            drawPath(bladePath, color = Color(0xFFECEFF1))

            // Grip section
            val gripPath = Path().apply {
                moveTo(-2.6.dp.toPx(), -16.dp.toPx())
                lineTo(-2.6.dp.toPx(), -25.dp.toPx())
                lineTo(2.6.dp.toPx(), -25.dp.toPx())
                lineTo(2.6.dp.toPx(), -16.dp.toPx())
                close()
            }
            drawPath(gripPath, color = Color(0xFF263238))

            // Gold collar band
            drawLine(
                color = Color(0xFFFFD54F),
                start = Offset(-2.6.dp.toPx(), -16.dp.toPx()),
                end = Offset(2.6.dp.toPx(), -16.dp.toPx()),
                strokeWidth = 1.8.dp.toPx()
            )

            // Central slit and breather hole
            drawLine(
                color = Color(0xFF37474F),
                start = Offset(0f, 0f),
                end = Offset(0f, -8.dp.toPx()),
                strokeWidth = 0.9.dp.toPx()
            )
            drawCircle(
                color = Color(0xFF37474F),
                radius = 1.1.dp.toPx(),
                center = Offset(0f, -8.5.dp.toPx())
            )
        }
    }

    // 3. Pinpoint microdot at the exact tip coordinate (0, 0)
    drawCircle(color = Color.Black.copy(alpha = 0.75f), radius = 1.8.dp.toPx(), center = Offset.Zero)
    drawCircle(color = Color.White.copy(alpha = 0.95f), radius = 1.0.dp.toPx(), center = Offset.Zero)
}
