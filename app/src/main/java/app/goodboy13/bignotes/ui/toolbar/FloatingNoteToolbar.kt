package app.goodboy13.bignotes.ui.toolbar

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import app.goodboy13.bignotes.ui.theme.CustomIcons
import app.goodboy13.bignotes.ui.theme.InkColorItem
import kotlin.math.roundToInt

enum class DockEdge {
    TOP, BOTTOM, LEFT, RIGHT;

    val isHorizontal: Boolean get() = this == TOP || this == BOTTOM
    val isVertical: Boolean get() = this == LEFT || this == RIGHT
}

/**
 * Collapsible Floating Toolbar that morphs between horizontal
 * (near top/bottom screen edges) and vertical (near left/right edges).
 *
 * In Collapsed mode: Shows only [Selected writing tool], [Eraser], [Undo/Redo], and [Expand].
 * In Expanded mode: Shows all writing tools, eraser, lasso, insert image, undo/redo, zoom, and collapse.
 */
@Composable
fun FloatingNoteToolbar(
    activeTool: ActiveTool,
    onToolChange: (ActiveTool) -> Unit,
    strokeSize: Float,
    onStrokeSizeChange: (Float) -> Unit,
    paletteColors: List<InkColorItem>,
    selectedColorIndex: Int,
    onColorSelect: (Int) -> Unit,
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
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current

    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

    var dockEdge by remember { mutableStateOf(initialDockEdge) }
    var posFraction by remember { mutableFloatStateOf(initialPosFraction.coerceIn(0.15f, 0.85f)) }
    var isCollapsed by remember { mutableStateOf(initialIsCollapsed) }

    var isDragging by remember { mutableStateOf(false) }
    var liveDragOffset by remember { mutableStateOf(Offset.Zero) }
    var toolbarSizePx by remember { mutableStateOf(IntSize.Zero) }

    var isRadialOpen by remember { mutableStateOf(false) }
    var fountainPenAnchor by remember { mutableStateOf(Offset.Zero) }
    var pencilAnchor by remember { mutableStateOf(Offset.Zero) }
    var highlighterAnchor by remember { mutableStateOf(Offset.Zero) }
    var collapsedToolAnchor by remember { mutableStateOf(Offset.Zero) }

    val activeToolAnchor = if (isCollapsed) {
        collapsedToolAnchor
    } else {
        when (activeTool) {
            ActiveTool.PENCIL -> pencilAnchor
            ActiveTool.HIGHLIGHTER -> highlighterAnchor
            else -> fountainPenAnchor
        }
    }

    // Last used writing tool (for collapsed mode display)
    var lastWritingTool by remember { mutableStateOf(ActiveTool.FOUNTAIN_PEN) }
    if (activeTool == ActiveTool.FOUNTAIN_PEN || activeTool == ActiveTool.PENCIL || activeTool == ActiveTool.HIGHLIGHTER) {
        lastWritingTool = activeTool
    }

    var lastEraserMode by remember { mutableStateOf(ActiveTool.PRECISION_ERASER) }

    // Compute target snapped position
    val snappedTargetOffset = remember(
        dockEdge, posFraction, toolbarSizePx, screenWidthPx, screenHeightPx, sidebarOffsetPx, statusBarHeightPx
    ) {
        val tw = toolbarSizePx.width.toFloat()
        val th = toolbarSizePx.height.toFloat()

        when (dockEdge) {
            DockEdge.TOP -> {
                val clampedX = ((screenWidthPx - tw) * posFraction).coerceIn(
                    sidebarOffsetPx + with(density) { 8.dp.toPx() },
                    screenWidthPx - tw - with(density) { 8.dp.toPx() }
                )
                Offset(clampedX, statusBarHeightPx + with(density) { 10.dp.toPx() })
            }
            DockEdge.BOTTOM -> {
                val clampedX = ((screenWidthPx - tw) * posFraction).coerceIn(
                    sidebarOffsetPx + with(density) { 8.dp.toPx() },
                    screenWidthPx - tw - with(density) { 8.dp.toPx() }
                )
                Offset(clampedX, screenHeightPx - th - with(density) { 18.dp.toPx() })
            }
            DockEdge.LEFT -> {
                val clampedY = ((screenHeightPx - th) * posFraction).coerceIn(
                    statusBarHeightPx + with(density) { 8.dp.toPx() },
                    screenHeightPx - th - with(density) { 8.dp.toPx() }
                )
                Offset(sidebarOffsetPx + with(density) { 10.dp.toPx() }, clampedY)
            }
            DockEdge.RIGHT -> {
                val clampedY = ((screenHeightPx - th) * posFraction).coerceIn(
                    statusBarHeightPx + with(density) { 8.dp.toPx() },
                    screenHeightPx - th - with(density) { 8.dp.toPx() }
                )
                Offset(screenWidthPx - tw - with(density) { 10.dp.toPx() }, clampedY)
            }
        }
    }

    // Smooth position animation when not actively dragging
    val animatedOffset by animateOffsetAsState(
        targetValue = if (isDragging) liveDragOffset else snappedTargetOffset,
        animationSpec = if (isDragging) snap() else spring(
            stiffness = Spring.StiffnessMediumLow,
            dampingRatio = 0.78f
        ),
        label = "FloatingToolbarPositionAnimation"
    )

    Box(modifier = modifier.fillMaxSize()) {
        // Scrim backdrop when radial flyout is open: dismisses flyout on outside tap
        if (isRadialOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { isRadialOpen = false })
                    }
            )
        }

        // Floating Toolbar Pill
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
            if (dockEdge.isHorizontal) {
                // Horizontal Toolbar Layout (Top / Bottom)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    // Drag Handle
                    DragHandle(
                        isHorizontal = true,
                        onDragStart = {
                            isDragging = true
                            liveDragOffset = animatedOffset
                            isRadialOpen = false
                        },
                        onDragDelta = { delta ->
                            val tw = toolbarSizePx.width.toFloat()
                            val th = toolbarSizePx.height.toFloat()
                            liveDragOffset = Offset(
                                (liveDragOffset.x + delta.x).coerceIn(0f, screenWidthPx - tw),
                                (liveDragOffset.y + delta.y).coerceIn(0f, screenHeightPx - th)
                            )
                        },
                        onDragEnd = {
                            isDragging = false
                            val tw = toolbarSizePx.width.toFloat()
                            val th = toolbarSizePx.height.toFloat()
                            val centerX = liveDragOffset.x + tw / 2f
                            val centerY = liveDragOffset.y + th / 2f

                            val distTop = centerY
                            val distBottom = screenHeightPx - centerY
                            val distLeft = centerX
                            val distRight = screenWidthPx - centerX

                            val minDist = minOf(distTop, distBottom, distLeft, distRight)
                            val newEdge = when (minDist) {
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
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    if (isCollapsed) {
                        // COLLAPSED MODE: [Selected Tool], [Eraser], [Undo], [Redo], [Expand]
                        WritingToolButton(
                            tool = lastWritingTool,
                            isActive = activeTool == lastWritingTool,
                            currentColor = paletteColors.getOrNull(selectedColorIndex)?.color ?: Color.Black,
                            onToolClick = {
                                if (activeTool != lastWritingTool) {
                                    onToolChange(lastWritingTool)
                                } else {
                                    isRadialOpen = !isRadialOpen
                                }
                            },
                            onPositioned = { collapsedToolAnchor = it }
                        )

                        Spacer(modifier = Modifier.width(4.dp))

                        EraserButton(
                            activeTool = activeTool,
                            lastEraserMode = lastEraserMode,
                            onModeSwitch = { next ->
                                lastEraserMode = next
                                onToolChange(next)
                            }
                        )

                        Spacer(modifier = Modifier.width(4.dp))

                        UndoRedoButtons(
                            canUndo = canUndo,
                            canRedo = canRedo,
                            onUndo = onUndo,
                            onRedo = onRedo,
                            isHorizontal = true
                        )

                        Spacer(modifier = Modifier.width(4.dp))

                        // Expand Button
                        IconButton(
                            onClick = {
                                isCollapsed = false
                                onDockStateChanged(dockEdge, posFraction, false)
                            },
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
                        // EXPANDED MODE: All tools, image, undo/redo, zoom, collapse
                        WritingToolButton(
                            tool = ActiveTool.FOUNTAIN_PEN,
                            isActive = activeTool == ActiveTool.FOUNTAIN_PEN,
                            currentColor = paletteColors.getOrNull(selectedColorIndex)?.color ?: Color.Black,
                            onToolClick = {
                                if (activeTool != ActiveTool.FOUNTAIN_PEN) {
                                    onToolChange(ActiveTool.FOUNTAIN_PEN)
                                } else {
                                    isRadialOpen = !isRadialOpen
                                }
                            },
                            onPositioned = { fountainPenAnchor = it }
                        )

                        WritingToolButton(
                            tool = ActiveTool.PENCIL,
                            isActive = activeTool == ActiveTool.PENCIL,
                            currentColor = paletteColors.getOrNull(selectedColorIndex)?.color ?: Color.Black,
                            onToolClick = {
                                if (activeTool != ActiveTool.PENCIL) {
                                    onToolChange(ActiveTool.PENCIL)
                                } else {
                                    isRadialOpen = !isRadialOpen
                                }
                            },
                            onPositioned = { pencilAnchor = it }
                        )

                        WritingToolButton(
                            tool = ActiveTool.HIGHLIGHTER,
                            isActive = activeTool == ActiveTool.HIGHLIGHTER,
                            currentColor = paletteColors.getOrNull(selectedColorIndex)?.color ?: Color.Yellow,
                            onToolClick = {
                                if (activeTool != ActiveTool.HIGHLIGHTER) {
                                    onToolChange(ActiveTool.HIGHLIGHTER)
                                } else {
                                    isRadialOpen = !isRadialOpen
                                }
                            },
                            onPositioned = { highlighterAnchor = it }
                        )

                        EraserButton(
                            activeTool = activeTool,
                            lastEraserMode = lastEraserMode,
                            onModeSwitch = { next ->
                                lastEraserMode = next
                                onToolChange(next)
                            }
                        )

                        // Lasso
                        val isLasso = activeTool == ActiveTool.LASSO
                        IconButton(
                            onClick = {
                                isRadialOpen = false
                                onToolChange(ActiveTool.LASSO)
                            },
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

                        VerticalDivider(
                            modifier = Modifier
                                .height(22.dp)
                                .padding(horizontal = 4.dp),
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.20f)
                        )

                        UndoRedoButtons(
                            canUndo = canUndo,
                            canRedo = canRedo,
                            onUndo = onUndo,
                            onRedo = onRedo,
                            isHorizontal = true
                        )

                        // Zoom Indicator
                        Spacer(modifier = Modifier.width(2.dp))
                        ZoomIndicator(zoomScale = zoomScale, onResetZoom = onResetZoom)

                        Spacer(modifier = Modifier.width(2.dp))

                        // Collapse Button
                        IconButton(
                            onClick = {
                                isCollapsed = true
                                isRadialOpen = false
                                onDockStateChanged(dockEdge, posFraction, true)
                            },
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
            } else {
                // Vertical Toolbar Layout (Left / Right)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                ) {
                    // Drag Handle
                    DragHandle(
                        isHorizontal = false,
                        onDragStart = {
                            isDragging = true
                            liveDragOffset = animatedOffset
                            isRadialOpen = false
                        },
                        onDragDelta = { delta ->
                            val tw = toolbarSizePx.width.toFloat()
                            val th = toolbarSizePx.height.toFloat()
                            liveDragOffset = Offset(
                                (liveDragOffset.x + delta.x).coerceIn(0f, screenWidthPx - tw),
                                (liveDragOffset.y + delta.y).coerceIn(0f, screenHeightPx - th)
                            )
                        },
                        onDragEnd = {
                            isDragging = false
                            val tw = toolbarSizePx.width.toFloat()
                            val th = toolbarSizePx.height.toFloat()
                            val centerX = liveDragOffset.x + tw / 2f
                            val centerY = liveDragOffset.y + th / 2f

                            val distTop = centerY
                            val distBottom = screenHeightPx - centerY
                            val distLeft = centerX
                            val distRight = screenWidthPx - centerX

                            val minDist = minOf(distTop, distBottom, distLeft, distRight)
                            val newEdge = when (minDist) {
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
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    if (isCollapsed) {
                        // COLLAPSED MODE VERTICAL: [Selected Tool], [Eraser], [Undo], [Redo], [Expand]
                        WritingToolButton(
                            tool = lastWritingTool,
                            isActive = activeTool == lastWritingTool,
                            currentColor = paletteColors.getOrNull(selectedColorIndex)?.color ?: Color.Black,
                            onToolClick = {
                                if (activeTool != lastWritingTool) {
                                    onToolChange(lastWritingTool)
                                } else {
                                    isRadialOpen = !isRadialOpen
                                }
                            },
                            onPositioned = { collapsedToolAnchor = it }
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        EraserButton(
                            activeTool = activeTool,
                            lastEraserMode = lastEraserMode,
                            onModeSwitch = { next ->
                                lastEraserMode = next
                                onToolChange(next)
                            }
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        UndoRedoButtons(
                            canUndo = canUndo,
                            canRedo = canRedo,
                            onUndo = onUndo,
                            onRedo = onRedo,
                            isHorizontal = false
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Expand Button
                        IconButton(
                            onClick = {
                                isCollapsed = false
                                onDockStateChanged(dockEdge, posFraction, false)
                            },
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
                        // EXPANDED MODE VERTICAL
                        WritingToolButton(
                            tool = ActiveTool.FOUNTAIN_PEN,
                            isActive = activeTool == ActiveTool.FOUNTAIN_PEN,
                            currentColor = paletteColors.getOrNull(selectedColorIndex)?.color ?: Color.Black,
                            onToolClick = {
                                if (activeTool != ActiveTool.FOUNTAIN_PEN) {
                                    onToolChange(ActiveTool.FOUNTAIN_PEN)
                                } else {
                                    isRadialOpen = !isRadialOpen
                                }
                            },
                            onPositioned = { fountainPenAnchor = it }
                        )

                        WritingToolButton(
                            tool = ActiveTool.PENCIL,
                            isActive = activeTool == ActiveTool.PENCIL,
                            currentColor = paletteColors.getOrNull(selectedColorIndex)?.color ?: Color.Black,
                            onToolClick = {
                                if (activeTool != ActiveTool.PENCIL) {
                                    onToolChange(ActiveTool.PENCIL)
                                } else {
                                    isRadialOpen = !isRadialOpen
                                }
                            },
                            onPositioned = { pencilAnchor = it }
                        )

                        WritingToolButton(
                            tool = ActiveTool.HIGHLIGHTER,
                            isActive = activeTool == ActiveTool.HIGHLIGHTER,
                            currentColor = paletteColors.getOrNull(selectedColorIndex)?.color ?: Color.Yellow,
                            onToolClick = {
                                if (activeTool != ActiveTool.HIGHLIGHTER) {
                                    onToolChange(ActiveTool.HIGHLIGHTER)
                                } else {
                                    isRadialOpen = !isRadialOpen
                                }
                            },
                            onPositioned = { highlighterAnchor = it }
                        )

                        EraserButton(
                            activeTool = activeTool,
                            lastEraserMode = lastEraserMode,
                            onModeSwitch = { next ->
                                lastEraserMode = next
                                onToolChange(next)
                            }
                        )

                        // Lasso
                        val isLasso = activeTool == ActiveTool.LASSO
                        IconButton(
                            onClick = {
                                isRadialOpen = false
                                onToolChange(ActiveTool.LASSO)
                            },
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

                        HorizontalDivider(
                            modifier = Modifier
                                .width(22.dp)
                                .padding(vertical = 4.dp),
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.20f)
                        )

                        UndoRedoButtons(
                            canUndo = canUndo,
                            canRedo = canRedo,
                            onUndo = onUndo,
                            onRedo = onRedo,
                            isHorizontal = false
                        )

                        // Zoom Indicator
                        Spacer(modifier = Modifier.height(2.dp))
                        ZoomIndicator(zoomScale = zoomScale, onResetZoom = onResetZoom)

                        Spacer(modifier = Modifier.height(2.dp))

                        // Collapse Button
                        IconButton(
                            onClick = {
                                isCollapsed = true
                                isRadialOpen = false
                                onDockStateChanged(dockEdge, posFraction, true)
                            },
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
            }
        }

        // Radial Dual-Arc Satellite Color/Size Flyout
        if (isRadialOpen && activeToolAnchor != Offset.Zero) {
            RadialColorSizeFlyout(
                anchorCenterPx = activeToolAnchor,
                dockEdge = dockEdge,
                paletteColors = paletteColors,
                selectedColorIndex = selectedColorIndex,
                onColorSelect = { idx ->
                    onColorSelect(idx)
                },
                strokeSize = strokeSize,
                onStrokeSizeChange = { sz ->
                    onStrokeSizeChange(sz)
                },
                onDismiss = { isRadialOpen = false }
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

@Composable
private fun WritingToolButton(
    tool: ActiveTool,
    isActive: Boolean,
    currentColor: Color,
    onToolClick: () -> Unit,
    onPositioned: (Offset) -> Unit
) {
    val icon = when (tool) {
        ActiveTool.FOUNTAIN_PEN -> CustomIcons.FountainPen
        ActiveTool.PENCIL -> CustomIcons.Pencil
        ActiveTool.HIGHLIGHTER -> CustomIcons.Highlighter
        else -> CustomIcons.FountainPen
    }

    IconButton(
        onClick = onToolClick,
        colors = if (isActive) {
            IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                contentColor = MaterialTheme.colorScheme.primary
            )
        } else IconButtonDefaults.iconButtonColors(),
        modifier = Modifier
            .size(36.dp)
            .onGloballyPositioned { coords ->
                val bounds = coords.boundsInRoot()
                onPositioned(Offset(bounds.left + bounds.width / 2f, bounds.top + bounds.height / 2f))
            }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = tool.name,
                modifier = Modifier.size(20.dp),
                tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )

            // Active Color Pip Dot
            if (isActive) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(currentColor)
                        .align(Alignment.BottomEnd)
                        .offset(x = 2.dp, y = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun EraserButton(
    activeTool: ActiveTool,
    lastEraserMode: ActiveTool,
    onModeSwitch: (ActiveTool) -> Unit
) {
    val isPrecision = activeTool == ActiveTool.PRECISION_ERASER
    val isStrokeEraser = activeTool == ActiveTool.WHOLE_STROKE_ERASER
    val isEraserActive = isPrecision || isStrokeEraser

    IconButton(
        onClick = {
            if (!isEraserActive) {
                onModeSwitch(lastEraserMode)
            } else {
                val nextMode = if (isStrokeEraser) ActiveTool.PRECISION_ERASER else ActiveTool.WHOLE_STROKE_ERASER
                onModeSwitch(nextMode)
            }
        },
        colors = if (isEraserActive) {
            IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                contentColor = MaterialTheme.colorScheme.primary
            )
        } else IconButtonDefaults.iconButtonColors(),
        modifier = Modifier.size(36.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (isStrokeEraser) Icons.Default.DeleteSweep else Icons.Default.AutoFixNormal,
                contentDescription = if (isStrokeEraser) "Stroke Eraser" else "Partial Eraser",
                modifier = Modifier.size(20.dp),
                tint = if (isEraserActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            if (isEraserActive) {
                Text(
                    text = if (isStrokeEraser) "S" else "P",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Black),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 3.dp, y = 2.dp)
                )
            }
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
