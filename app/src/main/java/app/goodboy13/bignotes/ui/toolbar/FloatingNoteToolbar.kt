package app.goodboy13.bignotes.ui.toolbar

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.goodboy13.bignotes.core.model.ActiveTool
import app.goodboy13.bignotes.core.model.BezierControlPoints
import app.goodboy13.bignotes.core.model.InkBleedConfig
import app.goodboy13.bignotes.core.model.PressureCurveDefaults
import app.goodboy13.bignotes.core.model.VelocityCurveDefaults
import app.goodboy13.bignotes.ui.theme.BigNotesMotion
import app.goodboy13.bignotes.ui.theme.CustomIcons
import app.goodboy13.bignotes.ui.theme.InkColorItem
import app.goodboy13.bignotes.ui.theme.rememberReducedMotion
import app.goodboy13.bignotes.ui.theme.tactileScale
import kotlin.math.roundToInt

enum class DockEdge {
    TOP, BOTTOM, LEFT, RIGHT;

    val isHorizontal: Boolean get() = this == TOP || this == BOTTOM
    val isVertical: Boolean get() = this == LEFT || this == RIGHT
}

/**
 * Collapsible Floating Toolbar that morphs cleanly between horizontal (TOP/BOTTOM)
 * and vertical (LEFT/RIGHT) layouts via a shared [ToolbarContent] body.
 */
@Composable
fun FloatingNoteToolbar(
    activeTool: ActiveTool,
    onToolChange: (ActiveTool) -> Unit,
    strokeSize: Float,
    onStrokeSizeChange: (Float) -> Unit,
    eraserRadius: Float = 36f,
    onEraserRadiusChange: (Float) -> Unit = {},
    paletteColors: List<InkColorItem>,
    selectedColorIndex: Int,
    onColorSelect: (Int) -> Unit,
    onCustomColorSelected: ((Long) -> Unit)? = null,
    onActivateEyedropper: (() -> Unit)? = null,
    zoomScale: Float,
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onResetZoom: (() -> Unit)? = null,
    onInsertImage: (() -> Unit)? = null,
    initialDockEdge: DockEdge = DockEdge.TOP,
    initialPosFraction: Float = 0.5f,
    initialIsCollapsed: Boolean = false,
    onDockStateChanged: (edge: DockEdge, posFraction: Float, isCollapsed: Boolean) -> Unit = { _, _, _ -> },
    sidebarOffsetPx: Float = 0f,
    statusBarHeightPx: Float = 0f,
    inkBleedConfig: InkBleedConfig = InkBleedConfig.DEFAULT,
    onInkBleedConfigChange: ((InkBleedConfig) -> Unit)? = null,
    strokeStabilizationEnabled: Boolean = true,
    onStrokeStabilizationEnabledChange: ((Boolean) -> Unit)? = null,
    strokeStabilizationLevel: Float = 0.50f,
    onStrokeStabilizationLevelChange: ((Float) -> Unit)? = null,
    pressureBezier: BezierControlPoints = PressureCurveDefaults.STANDARD,
    onPressureBezierChange: ((BezierControlPoints) -> Unit)? = null,
    velocityBezier: BezierControlPoints = VelocityCurveDefaults.CALLIGRAPHIC,
    onVelocityBezierChange: ((VelocityControlPointsAlias) -> Unit)? = null,
    onOpenPenSettings: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val reducedMotion = rememberReducedMotion()

    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

    var dockEdge by remember { mutableStateOf(initialDockEdge) }
    var posFraction by remember { mutableFloatStateOf(initialPosFraction.coerceIn(0.15f, 0.85f)) }
    var isCollapsed by remember { mutableStateOf(initialIsCollapsed) }

    var isDragging by remember { mutableStateOf(false) }
    var liveDragOffset by remember { mutableStateOf(Offset.Zero) }
    var toolbarSizePx by remember { mutableStateOf(IntSize.Zero) }

    var isRadialOpen by remember { mutableStateOf(false) }
    var isTuningOpen by remember { mutableStateOf(false) }

    var fountainPenAnchor by remember { mutableStateOf(Offset.Zero) }
    var pencilAnchor by remember { mutableStateOf(Offset.Zero) }
    var highlighterAnchor by remember { mutableStateOf(Offset.Zero) }
    var eraserAnchor by remember { mutableStateOf(Offset.Zero) }
    var collapsedToolAnchor by remember { mutableStateOf(Offset.Zero) }

    val activeToolAnchor = if (isCollapsed) {
        collapsedToolAnchor
    } else {
        when (activeTool) {
            ActiveTool.PENCIL -> pencilAnchor
            ActiveTool.HIGHLIGHTER -> highlighterAnchor
            ActiveTool.PRECISION_ERASER -> eraserAnchor
            else -> fountainPenAnchor
        }
    }

    var lastWritingTool by remember { mutableStateOf(ActiveTool.FOUNTAIN_PEN) }
    if (activeTool == ActiveTool.FOUNTAIN_PEN || activeTool == ActiveTool.PENCIL || activeTool == ActiveTool.HIGHLIGHTER) {
        lastWritingTool = activeTool
    }

    var lastEraserMode by remember { mutableStateOf(ActiveTool.PRECISION_ERASER) }

    val snappedTargetOffset = remember(
        dockEdge, posFraction, toolbarSizePx, screenWidthPx, screenHeightPx, sidebarOffsetPx, statusBarHeightPx
    ) {
        val tw = toolbarSizePx.width.toFloat()
        val th = toolbarSizePx.height.toFloat()
        val edgePad = with(density) { 8.dp.toPx() }

        when (dockEdge) {
            DockEdge.TOP -> {
                val minX = sidebarOffsetPx + edgePad
                val maxX = maxOf(minX, screenWidthPx - tw - edgePad)
                val clampedX = ((screenWidthPx - tw) * posFraction).coerceIn(minX, maxX)
                Offset(clampedX, statusBarHeightPx + with(density) { 10.dp.toPx() })
            }
            DockEdge.BOTTOM -> {
                val minX = sidebarOffsetPx + edgePad
                val maxX = maxOf(minX, screenWidthPx - tw - edgePad)
                val clampedX = ((screenWidthPx - tw) * posFraction).coerceIn(minX, maxX)
                Offset(clampedX, maxOf(edgePad, screenHeightPx - th - with(density) { 18.dp.toPx() }))
            }
            DockEdge.LEFT -> {
                val minY = statusBarHeightPx + edgePad
                val maxY = maxOf(minY, screenHeightPx - th - edgePad)
                val clampedY = ((screenHeightPx - th) * posFraction).coerceIn(minY, maxY)
                Offset(sidebarOffsetPx + with(density) { 10.dp.toPx() }, clampedY)
            }
            DockEdge.RIGHT -> {
                val minY = statusBarHeightPx + edgePad
                val maxY = maxOf(minY, screenHeightPx - th - edgePad)
                val clampedY = ((screenHeightPx - th) * posFraction).coerceIn(minY, maxY)
                Offset(maxOf(edgePad, screenWidthPx - tw - with(density) { 10.dp.toPx() }), clampedY)
            }
        }
    }

    val animatedOffset by animateOffsetAsState(
        targetValue = if (isDragging) liveDragOffset else snappedTargetOffset,
        animationSpec = if (isDragging || reducedMotion) snap() else spring(
            stiffness = Spring.StiffnessMedium,
            dampingRatio = 0.82f
        ),
        label = "FloatingToolbarPositionAnimation"
    )

    var lastValidAnchor by remember { mutableStateOf(Offset.Zero) }
    if (activeToolAnchor != Offset.Zero) {
        lastValidAnchor = activeToolAnchor
    }

    val onDragStartHandler = {
        isDragging = true
        liveDragOffset = animatedOffset
        isRadialOpen = false
        isTuningOpen = false
    }

    val onDragDeltaHandler: (Offset) -> Unit = { delta ->
        val tw = toolbarSizePx.width.toFloat()
        val th = toolbarSizePx.height.toFloat()
        liveDragOffset = Offset(
            (liveDragOffset.x + delta.x).coerceIn(0f, screenWidthPx - tw),
            (liveDragOffset.y + delta.y).coerceIn(0f, screenHeightPx - th)
        )
    }

    val onDragEndHandler = {
        isDragging = false
        val tw = toolbarSizePx.width.toFloat()
        val th = toolbarSizePx.height.toFloat()
        val centerX = liveDragOffset.x + tw / 2f
        val centerY = liveDragOffset.y + th / 2f

        val distTop = centerY
        val distBottom = screenHeightPx - centerY
        val distLeft = centerX
        val distRight = screenWidthPx - centerX

        val newEdge = when (minOf(distTop, distBottom, distLeft, distRight)) {
            distTop -> DockEdge.TOP
            distBottom -> DockEdge.BOTTOM
            distLeft -> DockEdge.LEFT
            else -> DockEdge.RIGHT
        }
        dockEdge = newEdge
        posFraction = if (newEdge.isHorizontal) {
            (centerX / screenWidthPx).coerceIn(0.15f, 0.85f)
        } else {
            (centerY / screenHeightPx).coerceIn(0.15f, 0.85f)
        }
        onDockStateChanged(dockEdge, posFraction, isCollapsed)
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Staging scrim backdrop ONLY for radial flyout (tuning popover stays non-modal so user can draw beside it!)
        AnimatedVisibility(
            visible = isRadialOpen,
            enter = fadeIn(animationSpec = BigNotesMotion.enterTween(160, reducedMotion)),
            exit = fadeOut(animationSpec = BigNotesMotion.exitTween(120, reducedMotion))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.14f))
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { isRadialOpen = false })
                    }
            )
        }

        // Unified Floating Toolbar Pill
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.20f)),
            shadowElevation = 8.dp,
            tonalElevation = 3.dp,
            modifier = Modifier
                .offset {
                    IntOffset(
                        animatedOffset.x.roundToInt(),
                        animatedOffset.y.roundToInt()
                    )
                }
                .onSizeChanged { toolbarSizePx = it }
        ) {
            Box(
                modifier = Modifier.animateContentSize(
                    animationSpec = BigNotesMotion.tactileSpring(
                        dampingRatio = 0.82f,
                        stiffness = Spring.StiffnessMedium,
                        reducedMotion = reducedMotion
                    )
                )
            ) {
                val isHorizontal = dockEdge.isHorizontal
                val innerContent: @Composable () -> Unit = {
                    DragHandle(
                        isHorizontal = isHorizontal,
                        onDragStart = onDragStartHandler,
                        onDragDelta = onDragDeltaHandler,
                        onDragEnd = onDragEndHandler
                    )

                    if (isHorizontal) Spacer(modifier = Modifier.width(4.dp))
                    else Spacer(modifier = Modifier.height(4.dp))

                    ToolbarBody(
                        isHorizontal = isHorizontal,
                        isCollapsed = isCollapsed,
                        activeTool = activeTool,
                        lastWritingTool = lastWritingTool,
                        lastEraserMode = lastEraserMode,
                        paletteColors = paletteColors,
                        selectedColorIndex = selectedColorIndex,
                        canUndo = canUndo,
                        canRedo = canRedo,
                        zoomScale = zoomScale,
                        isTuningOpen = isTuningOpen,
                        hasTuningControls = onInkBleedConfigChange != null,
                        onToolChange = { tool ->
                            isRadialOpen = false
                            onToolChange(tool)
                        },
                        onSelectOrToggleRadial = { tool ->
                            isTuningOpen = false
                            if (activeTool != tool) {
                                onToolChange(tool)
                            } else {
                                isRadialOpen = !isRadialOpen
                            }
                        },
                        onEraserModeSwitch = { next ->
                            lastEraserMode = next
                            isTuningOpen = false
                            onToolChange(next)
                        },
                        onToggleRadial = {
                            isTuningOpen = false
                            isRadialOpen = !isRadialOpen
                        },
                        onOpenTuning = {
                            isRadialOpen = false
                            if (onInkBleedConfigChange != null) {
                                isTuningOpen = !isTuningOpen
                            } else {
                                onOpenPenSettings?.invoke()
                            }
                        },
                        onLongClickTool = {
                            isRadialOpen = false
                            if (onInkBleedConfigChange != null) {
                                isTuningOpen = true
                            } else {
                                onOpenPenSettings?.invoke()
                            }
                        },
                        onCollapsedAnchor = { collapsedToolAnchor = it },
                        onFountainAnchor = { fountainPenAnchor = it },
                        onPencilAnchor = { pencilAnchor = it },
                        onHighlighterAnchor = { highlighterAnchor = it },
                        onEraserAnchor = { eraserAnchor = it },
                        onUndo = onUndo,
                        onRedo = onRedo,
                        onResetZoom = onResetZoom,
                        onInsertImage = onInsertImage,
                        onSetCollapsed = { collapsed ->
                            isCollapsed = collapsed
                            isRadialOpen = false
                            if (collapsed) isTuningOpen = false
                            onDockStateChanged(dockEdge, posFraction, collapsed)
                        }
                    )
                }

                if (isHorizontal) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        innerContent()
                    }
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                    ) {
                        innerContent()
                    }
                }
            }
        }

        // Anchored Non-Modal Live Canvas Tuning Popover (Ink & Bleed + Pen & Feel)
        if (onInkBleedConfigChange != null && toolbarSizePx != IntSize.Zero) {
            InkPenTuningPopover(
                visible = isTuningOpen && !isRadialOpen,
                toolbarOffsetPx = animatedOffset,
                toolbarSizePx = toolbarSizePx,
                dockEdge = dockEdge,
                inkBleedConfig = inkBleedConfig,
                onInkBleedConfigChange = onInkBleedConfigChange,
                strokeStabilizationEnabled = strokeStabilizationEnabled,
                onStrokeStabilizationEnabledChange = { onStrokeStabilizationEnabledChange?.invoke(it) },
                strokeStabilizationLevel = strokeStabilizationLevel,
                onStrokeStabilizationLevelChange = { onStrokeStabilizationLevelChange?.invoke(it) },
                pressureBezier = pressureBezier,
                onPressureBezierChange = { onPressureBezierChange?.invoke(it) },
                velocityBezier = velocityBezier,
                onVelocityBezierChange = { onVelocityBezierChange?.invoke(it) },
                onOpenAdvancedSettings = {
                    isTuningOpen = false
                    onOpenPenSettings?.invoke()
                },
                onDismiss = { isTuningOpen = false }
            )
        }

        // Radial Dual-Arc Satellite Color/Size Flyout
        if (lastValidAnchor != Offset.Zero) {
            RadialColorSizeFlyout(
                visible = isRadialOpen && activeToolAnchor != Offset.Zero,
                anchorCenterPx = lastValidAnchor,
                dockEdge = dockEdge,
                paletteColors = paletteColors,
                selectedColorIndex = selectedColorIndex,
                onColorSelect = onColorSelect,
                onCustomColorSelected = onCustomColorSelected,
                onActivateEyedropper = onActivateEyedropper,
                strokeSize = strokeSize,
                onStrokeSizeChange = onStrokeSizeChange,
                isEraser = activeTool == ActiveTool.PRECISION_ERASER,
                eraserRadius = eraserRadius,
                onEraserRadiusChange = onEraserRadiusChange,
                onDismiss = { isRadialOpen = false }
            )
        }
    }
}

private typealias VelocityControlPointsAlias = BezierControlPoints

@Composable
private fun ToolbarBody(
    isHorizontal: Boolean,
    isCollapsed: Boolean,
    activeTool: ActiveTool,
    lastWritingTool: ActiveTool,
    lastEraserMode: ActiveTool,
    paletteColors: List<InkColorItem>,
    selectedColorIndex: Int,
    canUndo: Boolean,
    canRedo: Boolean,
    zoomScale: Float,
    isTuningOpen: Boolean,
    hasTuningControls: Boolean,
    onToolChange: (ActiveTool) -> Unit,
    onSelectOrToggleRadial: (ActiveTool) -> Unit,
    onEraserModeSwitch: (ActiveTool) -> Unit,
    onToggleRadial: () -> Unit,
    onOpenTuning: () -> Unit,
    onLongClickTool: () -> Unit,
    onCollapsedAnchor: (Offset) -> Unit,
    onFountainAnchor: (Offset) -> Unit,
    onPencilAnchor: (Offset) -> Unit,
    onHighlighterAnchor: (Offset) -> Unit,
    onEraserAnchor: (Offset) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onResetZoom: (() -> Unit)?,
    onInsertImage: (() -> Unit)?,
    onSetCollapsed: (Boolean) -> Unit
) {
    val gapModifier = if (isHorizontal) Modifier.width(4.dp) else Modifier.height(4.dp)
    val smallGapModifier = if (isHorizontal) Modifier.width(2.dp) else Modifier.height(2.dp)

    if (isCollapsed) {
        WritingToolButton(
            tool = lastWritingTool,
            isActive = activeTool == lastWritingTool,
            currentColor = paletteColors.getOrNull(selectedColorIndex)?.color ?: Color.Black,
            onToolClick = { onSelectOrToggleRadial(lastWritingTool) },
            onPositioned = onCollapsedAnchor,
            onLongClick = onLongClickTool
        )

        Spacer(modifier = gapModifier)

        EraserButton(
            activeTool = activeTool,
            lastEraserMode = lastEraserMode,
            onModeSwitch = onEraserModeSwitch,
            onPositioned = onEraserAnchor,
            onToggleRadial = onToggleRadial
        )

        Spacer(modifier = gapModifier)

        UndoRedoButtons(
            canUndo = canUndo,
            canRedo = canRedo,
            onUndo = onUndo,
            onRedo = onRedo,
            isHorizontal = isHorizontal
        )

        Spacer(modifier = gapModifier)

        IconButton(
            onClick = { onSetCollapsed(false) },
            modifier = Modifier.size(34.dp)
        ) {
            Icon(
                imageVector = Icons.Default.UnfoldMore,
                contentDescription = "Expand Toolbar",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.size(19.dp)
            )
        }
    } else {
        WritingToolButton(
            tool = ActiveTool.FOUNTAIN_PEN,
            isActive = activeTool == ActiveTool.FOUNTAIN_PEN,
            currentColor = paletteColors.getOrNull(selectedColorIndex)?.color ?: Color.Black,
            onToolClick = { onSelectOrToggleRadial(ActiveTool.FOUNTAIN_PEN) },
            onPositioned = onFountainAnchor,
            onLongClick = onLongClickTool
        )

        WritingToolButton(
            tool = ActiveTool.PENCIL,
            isActive = activeTool == ActiveTool.PENCIL,
            currentColor = paletteColors.getOrNull(selectedColorIndex)?.color ?: Color.Black,
            onToolClick = { onSelectOrToggleRadial(ActiveTool.PENCIL) },
            onPositioned = onPencilAnchor,
            onLongClick = onLongClickTool
        )

        WritingToolButton(
            tool = ActiveTool.HIGHLIGHTER,
            isActive = activeTool == ActiveTool.HIGHLIGHTER,
            currentColor = paletteColors.getOrNull(selectedColorIndex)?.color ?: Color.Yellow,
            onToolClick = { onSelectOrToggleRadial(ActiveTool.HIGHLIGHTER) },
            onPositioned = onHighlighterAnchor,
            onLongClick = onLongClickTool
        )

        EraserButton(
            activeTool = activeTool,
            lastEraserMode = lastEraserMode,
            onModeSwitch = onEraserModeSwitch,
            onPositioned = onEraserAnchor,
            onToggleRadial = onToggleRadial
        )

        // Lasso
        val isLasso = activeTool == ActiveTool.LASSO
        IconButton(
            onClick = { onToolChange(ActiveTool.LASSO) },
            colors = if (isLasso) {
                IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                    contentColor = MaterialTheme.colorScheme.primary
                )
            } else IconButtonDefaults.iconButtonColors(),
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Gesture,
                contentDescription = "Lasso Selection",
                modifier = Modifier.size(20.dp),
                tint = if (isLasso) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }

        // Ink & Feel Live Popover Toggle
        if (hasTuningControls) {
            IconButton(
                onClick = onOpenTuning,
                colors = if (isTuningOpen) {
                    IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                } else IconButtonDefaults.iconButtonColors(),
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "Ink & Pen Feel",
                    modifier = Modifier.size(19.dp),
                    tint = if (isTuningOpen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }

        // Insert Image
        if (onInsertImage != null) {
            IconButton(
                onClick = onInsertImage,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AddPhotoAlternate,
                    contentDescription = "Insert Image",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }

        if (isHorizontal) {
            VerticalDivider(
                modifier = Modifier.height(22.dp).padding(horizontal = 4.dp),
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.20f)
            )
        } else {
            HorizontalDivider(
                modifier = Modifier.width(22.dp).padding(vertical = 4.dp),
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.20f)
            )
        }

        UndoRedoButtons(
            canUndo = canUndo,
            canRedo = canRedo,
            onUndo = onUndo,
            onRedo = onRedo,
            isHorizontal = isHorizontal
        )

        Spacer(modifier = smallGapModifier)
        ZoomIndicator(zoomScale = zoomScale, onResetZoom = onResetZoom)
        Spacer(modifier = smallGapModifier)

        IconButton(
            onClick = { onSetCollapsed(true) },
            modifier = Modifier.size(34.dp)
        ) {
            Icon(
                imageVector = Icons.Default.UnfoldLess,
                contentDescription = "Collapse Toolbar",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                modifier = Modifier.size(19.dp)
            )
        }
    }
}

@Composable
private fun DragHandle(
    isHorizontal: Boolean,
    onDragStart: () -> Unit,
    onDragDelta: (Offset) -> Unit,
    onDragEnd: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(if (isHorizontal) 20.dp else 36.dp, if (isHorizontal) 36.dp else 20.dp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { onDragStart() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDragDelta(dragAmount)
                    },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.DragIndicator,
            contentDescription = "Drag to dock",
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.40f),
            modifier = Modifier.size(18.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WritingToolButton(
    tool: ActiveTool,
    isActive: Boolean,
    currentColor: Color,
    onToolClick: () -> Unit,
    onPositioned: (Offset) -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    val reducedMotion = rememberReducedMotion()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val icon = when (tool) {
        ActiveTool.FOUNTAIN_PEN -> CustomIcons.FountainPen
        ActiveTool.PENCIL -> CustomIcons.Pencil
        ActiveTool.HIGHLIGHTER -> CustomIcons.Highlighter
        else -> CustomIcons.FountainPen
    }

    val containerBg by animateColorAsState(
        targetValue = if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent,
        animationSpec = BigNotesMotion.snappyTween(reducedMotion = reducedMotion),
        label = "writingToolBg"
    )
    val iconTint by animateColorAsState(
        targetValue = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        animationSpec = BigNotesMotion.snappyTween(reducedMotion = reducedMotion),
        label = "writingToolTint"
    )
    val pipScale by animateFloatAsState(
        targetValue = if (isActive) 1f else 0f,
        animationSpec = BigNotesMotion.tactileSpring(
            dampingRatio = 0.55f,
            stiffness = Spring.StiffnessMedium,
            reducedMotion = reducedMotion
        ),
        label = "writingToolPipScale"
    )

    Box(
        modifier = Modifier
            .size(36.dp)
            .tactileScale(
                isPressed = isPressed,
                isSelected = isActive,
                pressedScale = 0.88f,
                selectedScale = 1.05f,
                reducedMotion = reducedMotion
            )
            .clip(CircleShape)
            .background(containerBg)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true),
                onClick = onToolClick,
                onLongClick = onLongClick
            )
            .onGloballyPositioned { coords ->
                val bounds = coords.boundsInRoot()
                onPositioned(Offset(bounds.left + bounds.width / 2f, bounds.top + bounds.height / 2f))
            },
        contentAlignment = Alignment.Center
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = tool.name,
                modifier = Modifier.size(20.dp),
                tint = iconTint
            )

            // Active Color Pip Dot (secondary action pop)
            if (pipScale > 0.01f) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .align(Alignment.BottomEnd)
                        .offset(x = 2.dp, y = 2.dp)
                        .graphicsLayer {
                            scaleX = pipScale
                            scaleY = pipScale
                        }
                        .clip(CircleShape)
                        .background(currentColor)
                )
            }
        }
    }
}

@Composable
private fun EraserButton(
    activeTool: ActiveTool,
    lastEraserMode: ActiveTool,
    onModeSwitch: (ActiveTool) -> Unit,
    onPositioned: (Offset) -> Unit = {},
    onToggleRadial: () -> Unit = {}
) {
    val reducedMotion = rememberReducedMotion()
    val isPrecision = activeTool == ActiveTool.PRECISION_ERASER
    val isStrokeEraser = activeTool == ActiveTool.WHOLE_STROKE_ERASER
    val isEraserActive = isPrecision || isStrokeEraser
    var isPressed by remember { mutableStateOf(false) }

    val containerBg by animateColorAsState(
        targetValue = if (isEraserActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent,
        animationSpec = BigNotesMotion.snappyTween(reducedMotion = reducedMotion),
        label = "eraserBg"
    )
    val iconTint by animateColorAsState(
        targetValue = if (isEraserActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        animationSpec = BigNotesMotion.snappyTween(reducedMotion = reducedMotion),
        label = "eraserTint"
    )

    Box(
        modifier = Modifier
            .size(36.dp)
            .tactileScale(
                isPressed = isPressed,
                isSelected = isEraserActive,
                pressedScale = 0.88f,
                selectedScale = 1.05f,
                reducedMotion = reducedMotion
            )
            .clip(CircleShape)
            .background(containerBg)
            .onGloballyPositioned { coords ->
                val bounds = coords.boundsInRoot()
                onPositioned(Offset(bounds.left + bounds.width / 2f, bounds.top + bounds.height / 2f))
            }
            .pointerInput(isEraserActive, isPrecision, lastEraserMode) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                    },
                    onTap = {
                        if (!isEraserActive) {
                            onModeSwitch(lastEraserMode)
                        } else if (isPrecision) {
                            onToggleRadial()
                        } else {
                            onModeSwitch(ActiveTool.PRECISION_ERASER)
                        }
                    },
                    onLongPress = {
                        val nextMode = if (isStrokeEraser) ActiveTool.PRECISION_ERASER else ActiveTool.WHOLE_STROKE_ERASER
                        onModeSwitch(nextMode)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isStrokeEraser) Icons.Default.DeleteSweep else Icons.Default.AutoFixNormal,
            contentDescription = if (isStrokeEraser) "Stroke Eraser (Long press to switch)" else "Partial Eraser (Tap for sizes, long press to switch)",
            modifier = Modifier.size(20.dp),
            tint = iconTint
        )
        if (isEraserActive) {
            Text(
                text = if (isStrokeEraser) "S" else "P",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Black),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = (-3).dp, y = (-2).dp)
            )
        }
    }
}

@Composable
private fun UndoRedoButtons(
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    isHorizontal: Boolean
) {
    if (isHorizontal) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onUndo,
                enabled = canUndo,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Undo,
                    contentDescription = "Undo",
                    tint = if (canUndo) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    modifier = Modifier.size(17.dp)
                )
            }

            IconButton(
                onClick = onRedo,
                enabled = canRedo,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Redo,
                    contentDescription = "Redo",
                    tint = if (canRedo) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    modifier = Modifier.size(17.dp)
                )
            }
        }
    } else {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(
                onClick = onUndo,
                enabled = canUndo,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Undo,
                    contentDescription = "Undo",
                    tint = if (canUndo) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    modifier = Modifier.size(17.dp)
                )
            }

            IconButton(
                onClick = onRedo,
                enabled = canRedo,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Redo,
                    contentDescription = "Redo",
                    tint = if (canRedo) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    modifier = Modifier.size(17.dp)
                )
            }
        }
    }
}

@Composable
private fun ZoomIndicator(
    zoomScale: Float,
    onResetZoom: (() -> Unit)?
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.20f)),
        modifier = Modifier
            .then(
                if (onResetZoom != null) Modifier.clickable { onResetZoom() } else Modifier
            )
            .padding(horizontal = 2.dp)
    ) {
        Text(
            text = "${(zoomScale * 100f).roundToInt()}%",
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.5.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
        )
    }
}
