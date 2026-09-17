package com.antigrav.bignotes.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.BorderStroke
import com.antigrav.bignotes.core.model.BezierControlPoints
import com.antigrav.bignotes.core.model.GridConfig
import com.antigrav.bignotes.core.model.GridStyle
import com.antigrav.bignotes.core.model.PaperColor
import com.antigrav.bignotes.core.model.PressureCurveDefaults
import com.antigrav.bignotes.core.model.VelocityCurveDefaults
import com.antigrav.bignotes.ui.components.BezierCurveGraph
import com.antigrav.bignotes.ui.theme.InkPalette
import com.antigrav.bignotes.ui.theme.ThemeConfig
import kotlin.math.roundToInt

private data class TestStrokePoint(val x: Float, val y: Float, val width: Float)

enum class SettingsCategory(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val iconBg: Color
) {
    CANVAS_STYLUS(
        "Canvas & Stylus",
        "Grid background, strict stylus, page lines",
        Icons.Default.Gesture,
        Color(0xFF0284C7)
    ),
    PEN_FEEL(
        "Pen Feel & Curves",
        "Pressure curve, velocity thinning, smoothing",
        Icons.Default.Edit,
        Color(0xFF6366F1)
    ),
    GESTURES(
        "Gestures",
        "Multi-finger tap gestures (2-finger undo, 3-finger redo)",
        Icons.Default.TouchApp,
        Color(0xFFF59E0B)
    ),
    APPEARANCE(
        "Appearance",
        "Dark / light mode",
        Icons.Default.Palette,
        Color(0xFFEC4899)
    ),
    BACKUP(
        "Backup & Storage",
        "Auto-snapshot, backup folder, timeline",
        Icons.Default.Folder,
        Color(0xFF10B981)
    )
}

@Composable
fun SettingsDialog(
    themeConfig: ThemeConfig,
    onThemeConfigChange: (ThemeConfig) -> Unit,
    pressureBezier: BezierControlPoints,
    onPressureBezierChange: (BezierControlPoints) -> Unit,
    velocityBezier: BezierControlPoints,
    onVelocityBezierChange: (BezierControlPoints) -> Unit,
    smoothingLevel: Float,
    onSmoothingLevelChange: (Float) -> Unit,
    showPageSeparators: Boolean = true,
    onShowPageSeparatorsChange: (Boolean) -> Unit = {},
    gridConfig: GridConfig = GridConfig(),
    onGridConfigChange: (GridConfig) -> Unit = {},
    strictStylusMode: Boolean = true,
    onStrictStylusModeChange: (Boolean) -> Unit = {},
    multiFingerGesturesEnabled: Boolean = true,
    onMultiFingerGesturesEnabledChange: (Boolean) -> Unit = {},
    shapeSnappingEnabled: Boolean = true,
    onShapeSnappingEnabledChange: (Boolean) -> Unit = {},
    shapeSnapDelayMs: Long = 400L,
    onShapeSnapDelayMsChange: (Long) -> Unit = {},
    backupFolderDisplayName: String = "",
    isBackupFolderConfigured: Boolean = false,
    onSelectBackupFolder: () -> Unit = {},
    lastBackupTimeFormatted: String = "",
    isBackupInProgress: Boolean = false,
    onBackupNow: () -> Unit = {},
    isRestoreInProgress: Boolean = false,
    onRestoreClicked: () -> Unit = {},
    autoBackupEnabled: Boolean = true,
    onAutoBackupEnabledChange: (Boolean) -> Unit = {},
    initialCategory: SettingsCategory? = null,
    onDismiss: () -> Unit
) {
    var currentCategory by remember { mutableStateOf<SettingsCategory?>(initialCategory) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
            modifier = Modifier
                .widthIn(min = 380.dp, max = 520.dp)
                .fillMaxHeight(0.80f)
                .padding(4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header (iOS Style Navigation)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (currentCategory == null) {
                        Text(
                            text = "Settings",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 19.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { currentCategory = null }
                                .padding(vertical = 2.dp, horizontal = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Settings",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                ),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                if (currentCategory != null) {
                    Text(
                        text = currentCategory!!.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Body (Animated between Category List and Detail Page)
                AnimatedContent(
                    targetState = currentCategory,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    label = "SettingsNavigation"
                ) { targetCat ->
                    if (targetCat == null) {
                        // Root Category List
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                        ) {
                            Spacer(modifier = Modifier.height(2.dp))

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    SettingsCategory.entries.forEachIndexed { index, cat ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { currentCategory = cat }
                                                .padding(horizontal = 14.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Category Icon with colored background
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(cat.iconBg),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = cat.icon,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }

                                            Spacer(modifier = Modifier.width(12.dp))

                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = cat.title,
                                                    style = MaterialTheme.typography.bodyMedium.copy(
                                                        fontWeight = FontWeight.SemiBold,
                                                        fontSize = 14.sp
                                                    ),
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = cat.subtitle,
                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                        fontSize = 11.sp
                                                    ),
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }

                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                                                modifier = Modifier.size(13.dp)
                                            )
                                        }

                                        if (index < SettingsCategory.entries.size - 1) {
                                            HorizontalDivider(
                                                modifier = Modifier.padding(start = 62.dp),
                                                thickness = 0.5.dp,
                                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "BigNotes • 120Hz Hardware Accelerated Canvas",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )
                        }
                    } else {
                        // Category Detail View
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                        ) {
                            when (targetCat) {
                                SettingsCategory.CANVAS_STYLUS -> CanvasStylusDetail(
                                    gridConfig = gridConfig,
                                    onGridConfigChange = onGridConfigChange,
                                    strictStylusMode = strictStylusMode,
                                    onStrictStylusModeChange = onStrictStylusModeChange,
                                    shapeSnappingEnabled = shapeSnappingEnabled,
                                    onShapeSnappingEnabledChange = onShapeSnappingEnabledChange,
                                    shapeSnapDelayMs = shapeSnapDelayMs,
                                    onShapeSnapDelayMsChange = onShapeSnapDelayMsChange,
                                    showPageSeparators = showPageSeparators,
                                    onShowPageSeparatorsChange = onShowPageSeparatorsChange
                                )

                                SettingsCategory.PEN_FEEL -> PenFeelDetail(
                                    pressureBezier = pressureBezier,
                                    onPressureBezierChange = onPressureBezierChange,
                                    velocityBezier = velocityBezier,
                                    onVelocityBezierChange = onVelocityBezierChange,
                                    smoothingLevel = smoothingLevel,
                                    onSmoothingLevelChange = onSmoothingLevelChange
                                )
                                SettingsCategory.GESTURES -> GesturesDetail(
                                    multiFingerGesturesEnabled = multiFingerGesturesEnabled,
                                    onMultiFingerGesturesEnabledChange = onMultiFingerGesturesEnabledChange
                                )
                                SettingsCategory.APPEARANCE -> AppearanceDetail(
                                    themeConfig = themeConfig,
                                    onThemeConfigChange = onThemeConfigChange
                                )
                                SettingsCategory.BACKUP -> BackupDetail(
                                    backupFolderDisplayName = backupFolderDisplayName,
                                    isBackupFolderConfigured = isBackupFolderConfigured,
                                    onSelectBackupFolder = onSelectBackupFolder,
                                    lastBackupTimeFormatted = lastBackupTimeFormatted,
                                    isBackupInProgress = isBackupInProgress,
                                    onBackupNow = onBackupNow,
                                    isRestoreInProgress = isRestoreInProgress,
                                    onRestoreClicked = onRestoreClicked,
                                    autoBackupEnabled = autoBackupEnabled,
                                    onAutoBackupEnabledChange = onAutoBackupEnabledChange
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Bottom Done Button
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Done", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.sp))
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            letterSpacing = 1.sp
        ),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 4.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            content = content
        )
    }
}

@Composable
private fun CanvasStylusDetail(
    gridConfig: GridConfig,
    onGridConfigChange: (GridConfig) -> Unit,
    strictStylusMode: Boolean,
    onStrictStylusModeChange: (Boolean) -> Unit,
    shapeSnappingEnabled: Boolean,
    onShapeSnappingEnabledChange: (Boolean) -> Unit,
    shapeSnapDelayMs: Long,
    onShapeSnapDelayMsChange: (Long) -> Unit,
    showPageSeparators: Boolean,
    onShowPageSeparatorsChange: (Boolean) -> Unit
) {

    SectionHeader("GRID BACKGROUND")
    SettingsCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Background Grid",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "High-performance procedural grid",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            Switch(
                checked = gridConfig.enabled,
                onCheckedChange = { onGridConfigChange(gridConfig.copy(enabled = it)) },
                modifier = Modifier.scale(0.8f)
            )
        }

        if (gridConfig.enabled) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GridStyle.entries.forEach { style ->
                    val isSelected = gridConfig.style == style
                    FilterChip(
                        selected = isSelected,
                        onClick = { onGridConfigChange(gridConfig.copy(style = style)) },
                        label = { Text(style.displayName, fontSize = 12.sp) },
                        modifier = Modifier.weight(1f).height(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Spacing", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                Text("${gridConfig.spacingPx.roundToInt()}px", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
            }
            Slider(
                value = gridConfig.spacingPx,
                onValueChange = { onGridConfigChange(gridConfig.copy(spacingPx = it)) },
                valueRange = 24f..96f,
                modifier = Modifier.fillMaxWidth().height(24.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Opacity", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                Text("${(gridConfig.opacity * 100f).roundToInt()}%", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
            }
            Slider(
                value = gridConfig.opacity,
                onValueChange = { onGridConfigChange(gridConfig.copy(opacity = it)) },
                valueRange = 0.05f..0.60f,
                modifier = Modifier.fillMaxWidth().height(24.dp)
            )
        }
    }

    Spacer(modifier = Modifier.height(10.dp))

    SectionHeader("PALM REJECTION & INPUT")
    SettingsCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    text = "Strict Stylus Mode",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Hardware palm rejection: fingers strictly navigate (pan/zoom), never draw ink",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            Switch(
                checked = strictStylusMode,
                onCheckedChange = onStrictStylusModeChange,
                modifier = Modifier.scale(0.8f)
            )
        }
    }

    Spacer(modifier = Modifier.height(10.dp))

    SectionHeader("SHAPE SNAPPING (QUICKSHAPE)")
    SettingsCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    text = "Shape Snapping",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Hold stylus stationary at stroke end to snap into lines, circles, boxes, arrows & triangles",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            Switch(
                checked = shapeSnappingEnabled,
                onCheckedChange = onShapeSnappingEnabledChange,
                modifier = Modifier.scale(0.8f)
            )
        }

        if (shapeSnappingEnabled) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Hold Delay", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                Text("${shapeSnapDelayMs}ms", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
            }
            Slider(
                value = shapeSnapDelayMs.toFloat(),
                onValueChange = { onShapeSnapDelayMsChange(it.roundToInt().toLong()) },
                valueRange = 200f..1000f,
                steps = 15,
                modifier = Modifier.fillMaxWidth().height(24.dp)
            )
        }
    }

    Spacer(modifier = Modifier.height(10.dp))

    SectionHeader("PAGE LAYOUT")

    SettingsCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    text = "Page Separators",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Show horizontal dotted page boundaries for PDF export layout",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            Switch(
                checked = showPageSeparators,
                onShowPageSeparatorsChange,
                modifier = Modifier.scale(0.8f)
            )
        }
    }
}

@Composable
private fun PenFeelDetail(
    pressureBezier: BezierControlPoints,
    onPressureBezierChange: (BezierControlPoints) -> Unit,
    velocityBezier: BezierControlPoints,
    onVelocityBezierChange: (BezierControlPoints) -> Unit,
    smoothingLevel: Float,
    onSmoothingLevelChange: (Float) -> Unit
) {
    var testStrokes by remember { mutableStateOf(listOf<List<TestStrokePoint>>()) }
    var currentTestStroke by remember { mutableStateOf<List<TestStrokePoint>>(emptyList()) }
    var testLastX by remember { mutableFloatStateOf(0f) }
    var testLastY by remember { mutableFloatStateOf(0f) }
    var testLastTime by remember { mutableLongStateOf(0L) }
    var testSpeed by remember { mutableFloatStateOf(0f) }

    SectionHeader("PRESSURE & VELOCITY RESPONSE")
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Pressure Curve",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            BezierCurveGraph(
                config = pressureBezier,
                onConfigChange = onPressureBezierChange,
                presets = listOf(
                    "Standard" to PressureCurveDefaults.STANDARD,
                    "Soft" to PressureCurveDefaults.SOFT,
                    "Firm" to PressureCurveDefaults.FIRM,
                    "Linear" to PressureCurveDefaults.LINEAR
                ),
                handle1Color = Color(0xFF1D4ED8),
                handle2Color = Color(0xFFF97316)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Velocity Thinning",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            BezierCurveGraph(
                config = velocityBezier,
                onConfigChange = onVelocityBezierChange,
                presets = listOf(
                    "Calligraphy" to VelocityCurveDefaults.CALLIGRAPHIC,
                    "Subtle" to VelocityCurveDefaults.SUBTLE,
                    "Linear" to VelocityCurveDefaults.LINEAR,
                    "Off" to VelocityCurveDefaults.OFF
                ),
                handle1Color = Color(0xFF0284C7),
                handle2Color = Color(0xFFE11D48)
            )
        }
    }

    Spacer(modifier = Modifier.height(10.dp))

    SectionHeader("STABILIZATION & SMOOTHING")
    SettingsCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Stroke Smoothing",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${(smoothingLevel * 100).roundToInt()}%",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = smoothingLevel,
            onValueChange = onSmoothingLevelChange,
            valueRange = 0.0f..1.0f,
            modifier = Modifier.fillMaxWidth().height(24.dp)
        )
    }

    Spacer(modifier = Modifier.height(10.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        SectionHeader("INTERACTIVE TEST PAD")
        if (testStrokes.isNotEmpty() || currentTestStroke.isNotEmpty()) {
            Text(
                text = "Clear",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .clickable {
                        testStrokes = emptyList()
                        currentTestStroke = emptyList()
                    }
                    .padding(horizontal = 6.dp)
            )
        }
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .pointerInput(pressureBezier, velocityBezier) {
                awaitPointerEventScope {
                    while (true) {
                        val downEvent = awaitPointerEvent()
                        val down = downEvent.changes.firstOrNull { it.pressed } ?: continue
                        down.consume()
                        testLastX = down.position.x
                        testLastY = down.position.y
                        testLastTime = down.uptimeMillis
                        testSpeed = 0f
                        val rawP = if (down.pressure > 0f) down.pressure else 0.5f
                        val effP = pressureBezier.solveY(rawP)
                        val w = (effP * 12f + 2f).coerceIn(2f, 16f)
                        currentTestStroke = listOf(TestStrokePoint(down.position.x, down.position.y, w))

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                change.consume()
                                if (currentTestStroke.isNotEmpty()) {
                                    testStrokes = testStrokes + listOf(currentTestStroke)
                                    currentTestStroke = emptyList()
                                }
                                break
                            }
                            change.consume()
                            val dt = (change.uptimeMillis - testLastTime).coerceAtLeast(1L).toFloat()
                            val dist = kotlin.math.hypot(
                                (change.position.x - testLastX).toDouble(),
                                (change.position.y - testLastY).toDouble()
                            ).toFloat()
                            val speed = dist / dt
                            testSpeed = testSpeed * 0.40f + speed * 0.60f
                            testLastX = change.position.x
                            testLastY = change.position.y
                            testLastTime = change.uptimeMillis

                            val speedFactor = (testSpeed / (testSpeed + 1.5f)).coerceIn(0.0f, 1.0f)
                            val reduction = velocityBezier.solveY(speedFactor)
                            val thinning = (1.0f - 0.88f * reduction).coerceIn(0.06f, 1.0f)

                            val rawPressure = if (change.pressure > 0f) change.pressure else 0.5f
                            val effectivePressure = (pressureBezier.solveY(rawPressure) * (thinning * thinning)).coerceIn(0.01f, 1.0f)
                            val width = (effectivePressure * 13f + 1.5f).coerceIn(1.5f, 16f)
                            currentTestStroke = currentTestStroke + TestStrokePoint(change.position.x, change.position.y, width)
                        }
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val all = testStrokes + if (currentTestStroke.isNotEmpty()) listOf(currentTestStroke) else emptyList()
            all.forEach { stroke ->
                for (i in 0 until stroke.size - 1) {
                    val p1 = stroke[i]
                    val p2 = stroke[i + 1]
                    drawLine(
                        color = primaryColor,
                        start = Offset(p1.x, p1.y),
                        end = Offset(p2.x, p2.y),
                        strokeWidth = (p1.width + p2.width) * 0.5f,
                        cap = StrokeCap.Round
                    )
                }
            }
        }
    }
}

@Composable
private fun GesturesDetail(
    multiFingerGesturesEnabled: Boolean,
    onMultiFingerGesturesEnabledChange: (Boolean) -> Unit
) {
    SectionHeader("MULTI-TOUCH GESTURES")
    SettingsCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    text = "Tap Shortcuts",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Quick multi-finger tap actions on canvas",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            Switch(
                checked = multiFingerGesturesEnabled,
                onCheckedChange = onMultiFingerGesturesEnabledChange,
                modifier = Modifier.scale(0.8f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        Spacer(modifier = Modifier.height(10.dp))

        // Gesture guide rows
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("2F", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text("Two-Finger Tap", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                Text("Undo last stroke or action with haptic tick", style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("3F", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text("Three-Finger Tap", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                Text("Redo last undone stroke or action with haptic tick", style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
        }
    }
}

@Composable
private fun AppearanceDetail(
    themeConfig: ThemeConfig,
    onThemeConfigChange: (ThemeConfig) -> Unit
) {
    SectionHeader("COLOR MODE")
    SettingsCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (themeConfig.isDark) Icons.Default.DarkMode else Icons.Default.LightMode,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = if (themeConfig.isDark) "Dark Mode" else "Light Mode",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (themeConfig.isDark) "Dark paper canvas" else "Natural light paper canvas",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
            Switch(
                checked = themeConfig.isDark,
                onCheckedChange = { onThemeConfigChange(themeConfig.copy(isDark = it)) },
                modifier = Modifier.scale(0.8f)
            )
        }
    }
}

@Composable
private fun BackupDetail(
    backupFolderDisplayName: String,
    isBackupFolderConfigured: Boolean,
    onSelectBackupFolder: () -> Unit,
    lastBackupTimeFormatted: String,
    isBackupInProgress: Boolean,
    onBackupNow: () -> Unit,
    isRestoreInProgress: Boolean,
    onRestoreClicked: () -> Unit,
    autoBackupEnabled: Boolean,
    onAutoBackupEnabledChange: (Boolean) -> Unit
) {
    SectionHeader("BACKUP LOCATION")
    SettingsCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    tint = if (isBackupFolderConfigured) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Backup Folder",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (isBackupFolderConfigured) backupFolderDisplayName else "No folder selected",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = if (isBackupFolderConfigured) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(
                onClick = onSelectBackupFolder,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = if (isBackupFolderConfigured) "Change" else "Choose",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    text = "Auto-Snapshot",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Automatically create a timestamped snapshot when app is minimized",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            Switch(
                checked = autoBackupEnabled && isBackupFolderConfigured,
                onCheckedChange = onAutoBackupEnabledChange,
                enabled = isBackupFolderConfigured,
                modifier = Modifier.scale(0.8f)
            )
        }
    }

    Spacer(modifier = Modifier.height(10.dp))

    SectionHeader("ACTIONS")
    SettingsCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onBackupNow,
                enabled = isBackupFolderConfigured && !isBackupInProgress && !isRestoreInProgress,
                modifier = Modifier.weight(1f).height(38.dp),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                if (isBackupInProgress) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Saving...", style = MaterialTheme.typography.labelSmall)
                } else {
                    Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Backup Now", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                }
            }

            OutlinedButton(
                onClick = onRestoreClicked,
                enabled = isBackupFolderConfigured && !isBackupInProgress && !isRestoreInProgress,
                modifier = Modifier.weight(1f).height(38.dp),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                Icon(imageVector = Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Timeline", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
            }
        }

        if (lastBackupTimeFormatted.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Last backup: $lastBackupTimeFormatted",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}
