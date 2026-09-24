package com.antigrav.bignotes.ui.toolbar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.ViewSidebar
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigrav.bignotes.core.model.ActiveTool
import com.antigrav.bignotes.ui.components.InPlaceRenameField
import com.antigrav.bignotes.ui.theme.CustomIcons
import com.antigrav.bignotes.ui.theme.InkColorItem
import kotlin.math.roundToInt

val STROKE_SIZES = listOf(2.0f, 3.0f, 5.0f, 7.0f, 9.0f, 17.0f)

@Composable
fun NoteToolbar(
    isSidebarVisible: Boolean,
    onToggleSidebar: () -> Unit,
    folderTitle: String,
    noteTitle: String,
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
    onRenameCurrentNote: ((String) -> Unit)? = null,
    onInsertImage: (() -> Unit)? = null,
    onExportNote: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var lastEraserMode by remember { mutableStateOf(ActiveTool.PRECISION_ERASER) }
    var isEditingTitle by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp)
        ) {
            // 1. Left: Sidebar Toggle + Title
            Row(
                modifier = Modifier.align(Alignment.CenterStart),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onToggleSidebar,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (isSidebarVisible) Icons.Default.Fullscreen else Icons.Default.FullscreenExit,
                        contentDescription = if (isSidebarVisible) "Fullscreen Canvas" else "Exit Fullscreen",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                        modifier = Modifier.size(21.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Note Breadcrumb & Title
                if (isEditingTitle && onRenameCurrentNote != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 2.dp)
                    ) {
                        Text(
                            text = "$folderTitle / ",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Normal,
                                fontSize = 14.5.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        InPlaceRenameField(
                            initialText = noteTitle,
                            onCommit = { newTitle ->
                                onRenameCurrentNote(newTitle)
                                isEditingTitle = false
                            },
                            onCancel = { isEditingTitle = false },
                            modifier = Modifier.widthIn(min = 100.dp, max = 200.dp),
                            textStyle = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.5.sp
                            )
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .then(
                                if (onRenameCurrentNote != null) {
                                    Modifier.clickable { isEditingTitle = true }
                                } else Modifier
                            )
                            .padding(horizontal = 4.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$folderTitle / ",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Normal,
                                fontSize = 14.5.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Text(
                            text = noteTitle,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.5.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // 2. Right: Horizontally Scrollable Sliding Panel (Palette -> Tools -> Image -> Undo/Redo -> Zoom)
            val showPalette = activeTool != ActiveTool.LASSO

            Row(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // A. Palette (2x6 Grid of Squares: 6 Colors on Top, 6 Sizes on Bottom) - To the left of the tools
                if (showPalette) {
                    val isEraserActive = activeTool == ActiveTool.PRECISION_ERASER || activeTool == ActiveTool.WHOLE_STROKE_ERASER

                    Column(
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 3.dp)
                    ) {
                        // Top Row: 6 Color Squares
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.5.dp)
                        ) {
                            paletteColors.forEachIndexed { index, item ->
                                val isSelected = (index == selectedColorIndex) && !isEraserActive
                                val squareShape = RoundedCornerShape(4.dp)
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(squareShape)
                                        .background(if (isEraserActive) item.color.copy(alpha = 0.35f) else item.color)
                                        .clickable {
                                            if (isEraserActive) {
                                                onToolChange(ActiveTool.FOUNTAIN_PEN)
                                            }
                                            onColorSelect(index)
                                        }
                                        .then(
                                            if (isSelected) {
                                                Modifier.border(1.8.dp, MaterialTheme.colorScheme.primary, squareShape)
                                            } else {
                                                Modifier.border(0.8.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), squareShape)
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Box(
                                            modifier = Modifier
                                                .size(5.dp)
                                                .clip(RoundedCornerShape(1.5.dp))
                                                .background(if (item.color.luminance() > 0.5f) Color.Black else Color.White)
                                        )
                                    }
                                }
                            }
                        }

                        // Bottom Row: 6 Size Squares (Controls stroke size & eraser size)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.5.dp)
                        ) {
                            STROKE_SIZES.forEach { size ->
                                val isSelected = kotlin.math.abs(strokeSize - size) < 0.1f
                                val squareShape = RoundedCornerShape(4.dp)
                                val dotDiameterDp = when (size.roundToInt()) {
                                    2 -> 3.2.dp
                                    3 -> 4.8.dp
                                    5 -> 6.8.dp
                                    7 -> 8.8.dp
                                    9 -> 10.5.dp
                                    17 -> 12.5.dp
                                    else -> ((size / 17.0f) * 9.5f + 3.0f).dp.coerceIn(3.0.dp, 12.5.dp)
                                }
                                val dotColor = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.70f)
                                }
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(squareShape)
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                             else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f)
                                        )
                                        .then(
                                            if (isSelected) {
                                                Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, squareShape)
                                            } else {
                                                Modifier.border(0.8.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.20f), squareShape)
                                            }
                                        )
                                        .clickable { onStrokeSizeChange(size) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(dotDiameterDp)
                                            .clip(CircleShape)
                                            .background(dotColor)
                                    )
                                }
                            }
                        }
                    }

                    VerticalDivider(
                        modifier = Modifier
                            .height(24.dp)
                            .padding(horizontal = 4.dp),
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                    )
                }

                // B. Tools (Pen, Pencil, Highlighter, Eraser, Lasso) - To the left of the image button
                // Pen
                val isPenActive = activeTool == ActiveTool.FOUNTAIN_PEN
                IconButton(
                    onClick = { onToolChange(ActiveTool.FOUNTAIN_PEN) },
                    colors = if (isPenActive) {
                        IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    } else IconButtonDefaults.iconButtonColors(),
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = CustomIcons.FountainPen,
                        contentDescription = "Pen",
                        modifier = Modifier.size(20.dp),
                        tint = if (isPenActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }

                // Pencil
                val isPencilActive = activeTool == ActiveTool.PENCIL
                IconButton(
                    onClick = { onToolChange(ActiveTool.PENCIL) },
                    colors = if (isPencilActive) {
                        IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    } else IconButtonDefaults.iconButtonColors(),
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = CustomIcons.Pencil,
                        contentDescription = "Pencil",
                        modifier = Modifier.size(20.dp),
                        tint = if (isPencilActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }

                // Highlighter
                val isHighlighterActive = activeTool == ActiveTool.HIGHLIGHTER
                IconButton(
                    onClick = { onToolChange(ActiveTool.HIGHLIGHTER) },
                    colors = if (isHighlighterActive) {
                        IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    } else IconButtonDefaults.iconButtonColors(),
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = CustomIcons.Highlighter,
                        contentDescription = "Highlighter",
                        modifier = Modifier.size(20.dp),
                        tint = if (isHighlighterActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }

                // Eraser Toggle Switch (Tap switches between Partial and Whole Stroke)
                val isPrecision = activeTool == ActiveTool.PRECISION_ERASER
                val isStrokeEraser = activeTool == ActiveTool.WHOLE_STROKE_ERASER
                val isEraserActive = isPrecision || isStrokeEraser

                IconButton(
                    onClick = {
                        if (!isEraserActive) {
                            onToolChange(lastEraserMode)
                        } else {
                            val nextMode = if (isStrokeEraser) ActiveTool.PRECISION_ERASER else ActiveTool.WHOLE_STROKE_ERASER
                            lastEraserMode = nextMode
                            onToolChange(nextMode)
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
                            contentDescription = if (isStrokeEraser) "Stroke Eraser (Tap to switch to partial)" else "Partial Eraser (Tap to switch to stroke)",
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

                // Lasso
                val isLassoActive = activeTool == ActiveTool.LASSO
                IconButton(
                    onClick = { onToolChange(ActiveTool.LASSO) },
                    colors = if (isLassoActive) {
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
                        tint = if (isLassoActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }

                // C. Insert Image Tool (to the right of the tools)
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
                        .height(24.dp)
                        .padding(horizontal = 4.dp),
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                )

                // D. Undo & Redo
                IconButton(
                    onClick = onUndo,
                    enabled = canUndo,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Undo,
                        contentDescription = "Undo",
                        tint = if (canUndo) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = onRedo,
                    enabled = canRedo,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Redo,
                        contentDescription = "Redo",
                        tint = if (canRedo) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        modifier = Modifier.size(18.dp)
                    )
                }

                // E. Interactive Zoom Indicator (Tap to reset to 100%)
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .padding(start = 3.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .then(
                            if (onResetZoom != null) {
                                Modifier.clickable(onClick = onResetZoom)
                            } else Modifier
                        )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.5.dp)
                    ) {
                        Text(
                            text = "${(zoomScale * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
