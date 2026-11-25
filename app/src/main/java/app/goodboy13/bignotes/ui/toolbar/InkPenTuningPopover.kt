package app.goodboy13.bignotes.ui.toolbar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.goodboy13.bignotes.core.model.BezierControlPoints
import app.goodboy13.bignotes.core.model.InkBleedConfig
import app.goodboy13.bignotes.core.model.PressureCurveDefaults
import app.goodboy13.bignotes.core.model.VelocityCurveDefaults
import app.goodboy13.bignotes.ui.theme.BigNotesMotion
import app.goodboy13.bignotes.ui.theme.rememberReducedMotion
import java.util.Locale
import kotlin.math.roundToInt

private enum class TuningTab(val label: String) {
    INK("Ink & Bleed"),
    FEEL("Pen & Feel")
}

/**
 * Non-modal floating tuning popover anchored next to [FloatingNoteToolbar].
 * Because there is no fullscreen scrim, touches outside the card go straight to the canvas
 * so the user can sketch and inspect ink & stabilization tweaks side-by-side in real time.
 */
@Composable
fun InkPenTuningPopover(
    visible: Boolean,
    toolbarOffsetPx: Offset,
    toolbarSizePx: IntSize,
    dockEdge: DockEdge,
    inkBleedConfig: InkBleedConfig,
    onInkBleedConfigChange: (InkBleedConfig) -> Unit,
    strokeStabilizationEnabled: Boolean,
    onStrokeStabilizationEnabledChange: (Boolean) -> Unit,
    strokeStabilizationLevel: Float,
    onStrokeStabilizationLevelChange: (Float) -> Unit,
    pressureBezier: BezierControlPoints,
    onPressureBezierChange: (BezierControlPoints) -> Unit,
    velocityBezier: BezierControlPoints,
    onVelocityBezierChange: (BezierControlPoints) -> Unit,
    onOpenAdvancedSettings: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val reducedMotion = rememberReducedMotion()
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

    var activeTab by remember { mutableStateOf(TuningTab.INK) }

    val cardWidthDp = 304.dp
    val maxCardHeightDp = 416.dp
    val cardWidthPx = with(density) { cardWidthDp.toPx() }
    val cardHeightPx = with(density) { maxCardHeightDp.toPx() }
    val gapPx = with(density) { 10.dp.toPx() }
    val edgePadPx = with(density) { 10.dp.toPx() }

    val targetX = when (dockEdge) {
        DockEdge.LEFT -> toolbarOffsetPx.x + toolbarSizePx.width + gapPx
        DockEdge.RIGHT -> toolbarOffsetPx.x - cardWidthPx - gapPx
        else -> toolbarOffsetPx.x + toolbarSizePx.width * 0.5f - cardWidthPx * 0.5f
    }.coerceIn(edgePadPx, maxOf(edgePadPx, screenWidthPx - cardWidthPx - edgePadPx))

    val targetY = when (dockEdge) {
        DockEdge.TOP -> toolbarOffsetPx.y + toolbarSizePx.height + gapPx
        DockEdge.BOTTOM -> (toolbarOffsetPx.y - cardHeightPx - gapPx)
        else -> toolbarOffsetPx.y
    }.coerceIn(edgePadPx, maxOf(edgePadPx, screenHeightPx - cardHeightPx - edgePadPx))

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(BigNotesMotion.enterTween(160, reducedMotion)) +
            scaleIn(initialScale = 0.94f, animationSpec = BigNotesMotion.enterTween(170, reducedMotion)),
        exit = fadeOut(BigNotesMotion.exitTween(120, reducedMotion)) +
            scaleOut(targetScale = 0.96f, animationSpec = BigNotesMotion.exitTween(120, reducedMotion)),
        modifier = modifier.offset { IntOffset(targetX.roundToInt(), targetY.roundToInt()) }
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)),
            shadowElevation = 10.dp,
            tonalElevation = 4.dp,
            modifier = Modifier
                .width(cardWidthDp)
                .heightIn(max = maxCardHeightDp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Header: Tabs + Settings shortcut + Close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TuningTab.entries.forEach { tab ->
                            val selected = activeTab == tab
                            val bg by animateColorAsState(
                                targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                animationSpec = BigNotesMotion.snappyTween(140, reducedMotion),
                                label = "tuningTabBg"
                            )
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = bg,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { activeTab = tab }
                            ) {
                                Text(
                                    text = tab.label,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 12.sp
                                    ),
                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onOpenAdvancedSettings,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "All Pen Settings",
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Tuning Popover",
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.14f)
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (activeTab == TuningTab.INK) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "PRESETS",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                            )
                            if (inkBleedConfig != InkBleedConfig.DEFAULT) {
                                Text(
                                    text = "Reset",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { onInkBleedConfigChange(InkBleedConfig.DEFAULT) }
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            InkBleedConfig.PRESETS.forEach { (label, preset) ->
                                val selected = inkBleedConfig == preset
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                    border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onInkBleedConfigChange(preset) }
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 11.sp
                                        ),
                                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }

                        PopoverSlider("Dilution", inkBleedConfig.inkDilution, 0.05f..1.00f, "${(inkBleedConfig.inkDilution * 100f).roundToInt()}%") {
                            onInkBleedConfigChange(inkBleedConfig.copy(inkDilution = it))
                        }
                        PopoverSlider("Bleed Width", inkBleedConfig.bleedRatio, 0.00f..1.50f, "${(inkBleedConfig.bleedRatio * 100f).roundToInt()}%") {
                            onInkBleedConfigChange(inkBleedConfig.copy(bleedRatio = it))
                        }
                        PopoverSlider("Bleed Opacity", inkBleedConfig.bleedBaseOpacity, 0.00f..1.00f, "${(inkBleedConfig.bleedBaseOpacity * 100f).roundToInt()}%") {
                            onInkBleedConfigChange(inkBleedConfig.copy(bleedBaseOpacity = it))
                        }
                        PopoverSlider("Bleed Falloff", inkBleedConfig.bleedPower, 0.50f..4.50f, String.format(Locale.US, "%.2f", inkBleedConfig.bleedPower)) {
                            onInkBleedConfigChange(inkBleedConfig.copy(bleedPower = it))
                        }
                        val shiftPct = (inkBleedConfig.coreShiftRatio * 100f).roundToInt()
                        PopoverSlider("Core Shift", inkBleedConfig.coreShiftRatio, -0.50f..0.50f, if (shiftPct >= 0) "+$shiftPct%" else "$shiftPct%") {
                            onInkBleedConfigChange(inkBleedConfig.copy(coreShiftRatio = it))
                        }
                        PopoverSlider("Meniscus Rim", inkBleedConfig.meniscusRim, 0.00f..1.00f, "${(inkBleedConfig.meniscusRim * 100f).roundToInt()}%") {
                            onInkBleedConfigChange(inkBleedConfig.copy(meniscusRim = it))
                        }
                        PopoverSlider("Chroma Halo", inkBleedConfig.chromaHalo, 0.00f..1.00f, "${(inkBleedConfig.chromaHalo * 100f).roundToInt()}%") {
                            onInkBleedConfigChange(inkBleedConfig.copy(chromaHalo = it))
                        }
                        PopoverSlider("Fiber Feathering", inkBleedConfig.fiberFeathering, 0.00f..1.00f, "${(inkBleedConfig.fiberFeathering * 100f).roundToInt()}%") {
                            onInkBleedConfigChange(inkBleedConfig.copy(fiberFeathering = it))
                        }
                        PopoverSlider("Paper Grain Mix", inkBleedConfig.paperGrainMix, 0.00f..1.00f, "${(inkBleedConfig.paperGrainMix * 100f).roundToInt()}%") {
                            onInkBleedConfigChange(inkBleedConfig.copy(paperGrainMix = it))
                        }
                        PopoverSlider("Edge AA", inkBleedConfig.aaWidth, 0.10f..2.50f, String.format(Locale.US, "%.2fpx", inkBleedConfig.aaWidth)) {
                            onInkBleedConfigChange(inkBleedConfig.copy(aaWidth = it))
                        }
                        PopoverSlider("Quad Margin", inkBleedConfig.quadMarginRatio, 0.20f..1.60f, "${(inkBleedConfig.quadMarginRatio * 100f).roundToInt()}%") {
                            onInkBleedConfigChange(inkBleedConfig.copy(quadMarginRatio = it))
                        }
                    } else {
                        // PEN & FEEL TAB
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Stroke Stabilization",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Switch(
                                checked = strokeStabilizationEnabled,
                                onCheckedChange = onStrokeStabilizationEnabledChange,
                                modifier = Modifier.scale(0.75f)
                            )
                        }
                        if (strokeStabilizationEnabled) {
                            PopoverSlider(
                                label = "Smoothing Amount",
                                value = strokeStabilizationLevel,
                                valueRange = 0.0f..1.0f,
                                formattedValue = "${(strokeStabilizationLevel * 100f).roundToInt()}%",
                                onValueChange = onStrokeStabilizationLevelChange
                            )
                        }

                        Text(
                            text = "PRESSURE CURVE",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                "Standard" to PressureCurveDefaults.STANDARD,
                                "Soft" to PressureCurveDefaults.SOFT,
                                "Firm" to PressureCurveDefaults.FIRM,
                                "Linear" to PressureCurveDefaults.LINEAR
                            ).forEach { (name, curve) ->
                                val selected = pressureBezier == curve
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                    border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onPressureBezierChange(curve) }
                                ) {
                                    Text(
                                        text = name,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 10.5.sp
                                        ),
                                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                        modifier = Modifier.padding(vertical = 6.dp).wrapContentWidth(Alignment.CenterHorizontally)
                                    )
                                }
                            }
                        }

                        Text(
                            text = "VELOCITY THINNING",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                "Calligraphy" to VelocityCurveDefaults.CALLIGRAPHIC,
                                "Subtle" to VelocityCurveDefaults.SUBTLE,
                                "Linear" to VelocityCurveDefaults.LINEAR,
                                "Off" to VelocityCurveDefaults.OFF
                            ).forEach { (name, curve) ->
                                val selected = velocityBezier == curve
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                    border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onVelocityBezierChange(curve) }
                                ) {
                                    Text(
                                        text = name,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 10.5.sp
                                        ),
                                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                        modifier = Modifier.padding(vertical = 6.dp).wrapContentWidth(Alignment.CenterHorizontally)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PopoverSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    formattedValue: String,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
            )
            Text(
                text = formattedValue,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.fillMaxWidth().height(22.dp)
        )
    }
}
