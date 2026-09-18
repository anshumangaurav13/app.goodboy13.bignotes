package com.antigrav.bignotes.ui.toolbar

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
            .height(50.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Sidebar Toggle Button (Fullscreen / Unfullscreen)
            IconButton(
                onClick = onToggleSidebar,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = if (isSidebarVisible) Icons.Default.Fullscreen else Icons.Default.FullscreenExit,
                    contentDescription = if (isSidebarVisible) "Fullscreen Canvas" else "Exit Fullscreen (Show Sidebar)",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(2.dp))

            // Note Breadcrumb & Title (In-place editable on tap)
            if (isEditingTitle && onRenameCurrentNote != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    Text(
                        text = "$folderTitle / ",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Normal,
                            fontSize = 13.sp
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
                        modifier = Modifier.widthIn(min = 100.dp, max = 220.dp),
                        textStyle = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
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
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$folderTitle / ",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Normal,
                            fontSize = 13.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        text = noteTitle,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Pushes all following tools to the right
            Spacer(modifier = Modifier.weight(1f))

            // Sliding Row: all tools horizontally scrollable so they never get clipped on narrow screens
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 2-Row Palette Grid: 6 Colors on Top, 6 Sizes on Bottom
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 2.dp)
                ) {
                    // Top Row: 6 Colors
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        paletteColors.forEachIndexed { index, item ->
                            val isSelected = (index == selectedColorIndex) && (activeTool == ActiveTool.PENCIL || activeTool == ActiveTool.FOUNTAIN_PEN || activeTool == ActiveTool.HIGHLIGHTER)
                            Box(
                                modifier = Modifier
                                    .size(19.dp)
                                    .clip(CircleShape)
                                    .background(item.color)
                                    .clickable {
                                        if (activeTool != ActiveTool.PENCIL && activeTool != ActiveTool.FOUNTAIN_PEN && activeTool != ActiveTool.HIGHLIGHTER) {
                                            onToolChange(ActiveTool.FOUNTAIN_PEN)
                                        }
                                        onColorSelect(index)
                                    }
                                    .then(
                                        if (isSelected) {
                                            Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                        } else {
                                            Modifier.border(0.8.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.30f), CircleShape)
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .size(4.5.dp)
                                            .clip(CircleShape)
                                            .background(if (item.color.luminance() > 0.5f) Color.Black else Color.White)
                                    )
                                }
                            }
                        }
                    }

                    // Bottom Row: 6 Sizes
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        STROKE_SIZES.forEach { size ->
                            val isSelected = kotlin.math.abs(strokeSize - size) < 0.1f
                            val dotDiameterDp = when (size.roundToInt()) {
                                2 -> 3.0.dp
                                3 -> 4.5.dp
                                5 -> 6.5.dp
                                7 -> 8.5.dp
                                9 -> 10.5.dp
                                17 -> 13.0.dp
                                else -> ((size / 17.0f) * 10.0f + 3.0f).dp.coerceIn(3.dp, 13.dp)
                            }
                            val dotColor = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.70f)
                            }
                            Box(
                                modifier = Modifier
                                    .size(19.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f)
                                    )
                                    .then(
                                        if (isSelected) {
                                            Modifier.border(1.2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                        } else {
                                            Modifier.border(0.8.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f), CircleShape)
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
                        .height(22.dp)
                        .padding(horizontal = 3.dp),
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                )

                // Tool Buttons (Pen, Pencil, Highlighter, Eraser with Mode Toggle)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // Pen Tool (Default: Solid ink)
                    val isPenActive = activeTool == ActiveTool.FOUNTAIN_PEN
                    IconButton(
                        onClick = { onToolChange(ActiveTool.FOUNTAIN_PEN) },
                        colors = if (isPenActive) {
                            IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        } else IconButtonDefaults.iconButtonColors(),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = CustomIcons.FountainPen,
                            contentDescription = "Pen (Ink)",
                            modifier = Modifier.size(18.dp),
                            tint = if (isPenActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }

                    // Pencil Tool (Graphite lead with paper tooth grain)
                    val isPencilActive = activeTool == ActiveTool.PENCIL
                    IconButton(
                        onClick = { onToolChange(ActiveTool.PENCIL) },
                        colors = if (isPencilActive) {
                            IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        } else IconButtonDefaults.iconButtonColors(),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = CustomIcons.Pencil,
                            contentDescription = "Pencil (Graphite)",
                            modifier = Modifier.size(18.dp),
                            tint = if (isPencilActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }

                    // Highlighter Tool (Glowing fluorescent ink)
                    val isHighlighterActive = activeTool == ActiveTool.HIGHLIGHTER
                    IconButton(
                        onClick = { onToolChange(ActiveTool.HIGHLIGHTER) },
                        colors = if (isHighlighterActive) {
                            IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        } else IconButtonDefaults.iconButtonColors(),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = CustomIcons.Highlighter,
                            contentDescription = "Highlighter (Glowing)",
                            modifier = Modifier.size(18.dp),
                            tint = if (isHighlighterActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }

                    // Eraser Tool with Mode Switch (Precision vs Stroke)
                    val isPrecision = activeTool == ActiveTool.PRECISION_ERASER
                    val isStrokeEraser = activeTool == ActiveTool.WHOLE_STROKE_ERASER
                    val isEraserActive = isPrecision || isStrokeEraser

                    IconButton(
                        onClick = {
                            if (!isEraserActive) {
                                onToolChange(lastEraserMode)
                            } else {
                                // Toggle between Precision and Whole Stroke eraser
                                val next = if (isPrecision) ActiveTool.WHOLE_STROKE_ERASER else ActiveTool.PRECISION_ERASER
                                lastEraserMode = next
                                onToolChange(next)
                            }
                        },
                        colors = if (isEraserActive) {
                            IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        } else IconButtonDefaults.iconButtonColors(),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isStrokeEraser) Icons.Default.DeleteSweep else Icons.Default.AutoFixNormal,
                                contentDescription = if (isStrokeEraser) "Stroke Eraser (Tap to toggle)" else "Precision Eraser (Tap to toggle)",
                                modifier = Modifier.size(17.dp),
                                tint = if (isEraserActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                            if (isEraserActive) {
                                Text(
                                    text = if (isStrokeEraser) "S" else "P",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, fontWeight = FontWeight.Black),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .offset(x = 3.dp, y = 2.dp)
                                )
                            }
                        }
                    }

                    // Lasso Tool
                    val isLassoActive = activeTool == ActiveTool.LASSO
                    IconButton(
                        onClick = { onToolChange(ActiveTool.LASSO) },
                        colors = if (isLassoActive) {
                            IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        } else IconButtonDefaults.iconButtonColors(),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Gesture,
                            contentDescription = "Lasso Selection",
                            modifier = Modifier.size(17.dp),
                            tint = if (isLassoActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }

                    // Insert Image Tool
                    if (onInsertImage != null) {
                        IconButton(
                            onClick = onInsertImage,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddPhotoAlternate,
                                contentDescription = "Insert Image",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }

                    // Share / Export Note
                    if (onExportNote != null) {
                        IconButton(
                            onClick = onExportNote,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share & Export Note",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }

                    VerticalDivider(
                        modifier = Modifier
                            .height(18.dp)
                            .padding(horizontal = 2.dp),
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                    )

                    // Undo
                    IconButton(
                        onClick = onUndo,
                        enabled = canUndo,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Undo,
                            contentDescription = "Undo",
                            tint = if (canUndo) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Redo
                    IconButton(
                        onClick = onRedo,
                        enabled = canRedo,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Redo,
                            contentDescription = "Redo",
                            tint = if (canRedo) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Zoom Indicator (100% - 200%)
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.padding(start = 2.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "${(zoomScale * 100).roundToInt()}%",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
