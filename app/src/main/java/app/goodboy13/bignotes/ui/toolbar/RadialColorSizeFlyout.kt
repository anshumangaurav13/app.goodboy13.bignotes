package app.goodboy13.bignotes.ui.toolbar

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.goodboy13.bignotes.ui.theme.BigNotesMotion
import app.goodboy13.bignotes.ui.theme.InkColorItem
import app.goodboy13.bignotes.ui.theme.rememberReducedMotion
import app.goodboy13.bignotes.ui.theme.tactileScale
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val FLYOUT_STROKE_SIZES = listOf(2f, 3f, 5f, 7f, 9f, 17f)
val ERASER_SIZES = listOf(16f, 24f, 36f, 48f, 64f, 80f)

private val QUICK_CUSTOM_COLORS = listOf(
    0xFF7C3AEDL, 0xFFEC4899L, 0xFF0EA5E9L, 0xFF14B8A6L,
    0xFF84CC16L, 0xFFF97316L, 0xFF8B5CF6L, 0xFF6366F1L,
    0xFF334155L, 0xFF991B1BL, 0xFF166534L, 0xFF1E3A8AL
)

/**
 * Dual-Arc Radial Satellite Flyout for Inking Colors (including editable Custom swatch & Eyedropper)
 * & Stroke Sizes (or Eraser Sizes).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RadialColorSizeFlyout(
    visible: Boolean,
    anchorCenterPx: androidx.compose.ui.geometry.Offset,
    dockEdge: DockEdge,
    paletteColors: List<InkColorItem>,
    selectedColorIndex: Int,
    onColorSelect: (Int) -> Unit,
    onCustomColorSelected: ((Long) -> Unit)? = null,
    onActivateEyedropper: (() -> Unit)? = null,
    strokeSize: Float,
    onStrokeSizeChange: (Float) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    isEraser: Boolean = false,
    eraserRadius: Float = 36f,
    onEraserRadiusChange: (Float) -> Unit = {}
) {
    val reducedMotion = rememberReducedMotion()
    var outerTargetBloomed by remember { mutableStateOf(false) }
    var showCustomPicker by remember { mutableStateOf(false) }

    LaunchedEffect(visible, reducedMotion) {
        if (visible) {
            if (!reducedMotion) delay(30L)
            outerTargetBloomed = true
        } else {
            outerTargetBloomed = false
            showCustomPicker = false
        }
    }

    val innerBloomProgress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = if (reducedMotion) snap() else spring(
            stiffness = if (visible) Spring.StiffnessMediumLow else Spring.StiffnessMedium,
            dampingRatio = if (visible) 0.72f else 0.90f
        ),
        label = "RadialInnerBloomAnimation"
    )

    val outerBloomProgress by animateFloatAsState(
        targetValue = if (outerTargetBloomed) 1f else 0f,
        animationSpec = if (reducedMotion) snap() else spring(
            stiffness = if (outerTargetBloomed) Spring.StiffnessMediumLow else Spring.StiffnessMedium,
            dampingRatio = if (outerTargetBloomed) 0.70f else 0.92f
        ),
        label = "RadialOuterBloomAnimation"
    )

    if (!visible && innerBloomProgress <= 0.01f && outerBloomProgress <= 0.01f) {
        return
    }

    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

    val (centerAngleDeg, arcSpanDeg) = when (dockEdge) {
        DockEdge.TOP -> 90f to 136f
        DockEdge.BOTTOM -> 270f to 136f
        DockEdge.LEFT -> 0f to 136f
        DockEdge.RIGHT -> 180f to 136f
    }

    val startAngleDeg = centerAngleDeg - arcSpanDeg / 2f

    val innerRadiusPx = with(density) { 74.dp.toPx() } * innerBloomProgress
    val outerRadiusPx = with(density) { 130.dp.toPx() } * outerBloomProgress

    Box(modifier = modifier.fillMaxSize()) {
        if (isEraser) {
            ERASER_SIZES.forEachIndexed { index, sizeVal ->
                val angle = startAngleDeg + index * (arcSpanDeg / (ERASER_SIZES.size - 1).coerceAtLeast(1))
                val rad = Math.toRadians(angle.toDouble())
                val dx = (innerRadiusPx * cos(rad)).toFloat()
                val dy = (innerRadiusPx * sin(rad)).toFloat()

                val sizeDp = 32.dp
                val halfSizePx = with(density) { (sizeDp / 2).toPx() }

                val itemXPx = (anchorCenterPx.x + dx).coerceIn(halfSizePx + 8f, maxOf(halfSizePx + 8f, screenWidthPx - halfSizePx - 8f))
                val itemYPx = (anchorCenterPx.y + dy).coerceIn(halfSizePx + 8f, maxOf(halfSizePx + 8f, screenHeightPx - halfSizePx - 8f))

                val isSelected = kotlin.math.abs(eraserRadius - sizeVal) < 2f
                val interactionSource = remember { MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()

                val previewDotDp = when (index) {
                    0 -> 4.dp
                    1 -> 6.dp
                    2 -> 9.dp
                    3 -> 12.dp
                    4 -> 15.dp
                    else -> 18.dp
                }

                val containerColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    animationSpec = BigNotesMotion.snappyTween(reducedMotion = reducedMotion),
                    label = "eraserOptionBg"
                )

                Surface(
                    shape = CircleShape,
                    color = containerColor,
                    shadowElevation = 5.dp,
                    border = if (isSelected) {
                        BorderStroke(2.5.dp, MaterialTheme.colorScheme.primary)
                    } else {
                        BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                    },
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (itemXPx - halfSizePx).roundToInt(),
                                (itemYPx - halfSizePx).roundToInt()
                            )
                        }
                        .size(sizeDp)
                        .tactileScale(
                            isPressed = isPressed,
                            isSelected = isSelected,
                            selectedScale = 1.08f,
                            reducedMotion = reducedMotion
                        )
                        .alpha(innerBloomProgress.coerceIn(0f, 1f))
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            enabled = visible
                        ) {
                            onEraserRadiusChange(sizeVal)
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Box(
                            modifier = Modifier
                                .size(previewDotDp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                        )
                    }
                }
            }
        } else {
            // Inner Arc: Palette swatches + optional Eyedropper satellite node
            val innerTotalItems = paletteColors.size + if (onActivateEyedropper != null) 1 else 0
            paletteColors.forEachIndexed { index, colorItem ->
                val angle = startAngleDeg + index * (arcSpanDeg / (innerTotalItems - 1).coerceAtLeast(1))
                val rad = Math.toRadians(angle.toDouble())
                val dx = (innerRadiusPx * cos(rad)).toFloat()
                val dy = (innerRadiusPx * sin(rad)).toFloat()

                val sizeDp = 30.dp
                val halfSizePx = with(density) { (sizeDp / 2).toPx() }

                val itemXPx = (anchorCenterPx.x + dx).coerceIn(halfSizePx + 8f, maxOf(halfSizePx + 8f, screenWidthPx - halfSizePx - 8f))
                val itemYPx = (anchorCenterPx.y + dy).coerceIn(halfSizePx + 8f, maxOf(halfSizePx + 8f, screenHeightPx - halfSizePx - 8f))

                val isSelected = index == selectedColorIndex
                val isCustomSlot = index == paletteColors.lastIndex && onCustomColorSelected != null
                val interactionSource = remember { MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()

                Surface(
                    shape = CircleShape,
                    color = colorItem.color,
                    shadowElevation = 5.dp,
                    border = if (isSelected) {
                        BorderStroke(2.5.dp, MaterialTheme.colorScheme.primary)
                    } else {
                        BorderStroke(1.dp, Color.White.copy(alpha = 0.65f))
                    },
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (itemXPx - halfSizePx).roundToInt(),
                                (itemYPx - halfSizePx).roundToInt()
                            )
                        }
                        .size(sizeDp)
                        .tactileScale(
                            isPressed = isPressed,
                            isSelected = isSelected,
                            selectedScale = 1.09f,
                            reducedMotion = reducedMotion
                        )
                        .alpha(innerBloomProgress.coerceIn(0f, 1f))
                        .combinedClickable(
                            interactionSource = interactionSource,
                            indication = null,
                            enabled = visible,
                            onClick = {
                                if (isCustomSlot && isSelected) {
                                    showCustomPicker = !showCustomPicker
                                } else {
                                    onColorSelect(index)
                                }
                            },
                            onLongClick = if (isCustomSlot) {
                                {
                                    onColorSelect(index)
                                    showCustomPicker = true
                                }
                            } else null
                        )
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isCustomSlot && !isSelected) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Custom Color",
                                tint = if (colorItem.color.luminance() > 0.5f) Color.Black.copy(alpha = 0.75f) else Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(13.dp)
                            )
                        } else if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(if (colorItem.color.luminance() > 0.5f) Color.Black else Color.White)
                            )
                        }
                    }
                }
            }

            // Eyedropper Satellite Node at end of inner color arc
            if (onActivateEyedropper != null) {
                val eyedropperIdx = paletteColors.size
                val angle = startAngleDeg + eyedropperIdx * (arcSpanDeg / (innerTotalItems - 1).coerceAtLeast(1))
                val rad = Math.toRadians(angle.toDouble())
                val dx = (innerRadiusPx * cos(rad)).toFloat()
                val dy = (innerRadiusPx * sin(rad)).toFloat()

                val sizeDp = 30.dp
                val halfSizePx = with(density) { (sizeDp / 2).toPx() }
                val itemXPx = (anchorCenterPx.x + dx).coerceIn(halfSizePx + 8f, maxOf(halfSizePx + 8f, screenWidthPx - halfSizePx - 8f))
                val itemYPx = (anchorCenterPx.y + dy).coerceIn(halfSizePx + 8f, maxOf(halfSizePx + 8f, screenHeightPx - halfSizePx - 8f))

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 5.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (itemXPx - halfSizePx).roundToInt(),
                                (itemYPx - halfSizePx).roundToInt()
                            )
                        }
                        .size(sizeDp)
                        .alpha(innerBloomProgress.coerceIn(0f, 1f))
                        .clickable(enabled = visible) {
                            onDismiss()
                            onActivateEyedropper()
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Colorize,
                            contentDescription = "Sample Color from Canvas",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Outer Arc: 6 Stroke Sizes
            FLYOUT_STROKE_SIZES.forEachIndexed { index, sizeVal ->
                val angle = startAngleDeg + index * (arcSpanDeg / (FLYOUT_STROKE_SIZES.size - 1).coerceAtLeast(1))
                val rad = Math.toRadians(angle.toDouble())
                val dx = (outerRadiusPx * cos(rad)).toFloat()
                val dy = (outerRadiusPx * sin(rad)).toFloat()

                val sizeDp = 34.dp
                val halfSizePx = with(density) { (sizeDp / 2).toPx() }

                val itemXPx = (anchorCenterPx.x + dx).coerceIn(halfSizePx + 8f, maxOf(halfSizePx + 8f, screenWidthPx - halfSizePx - 8f))
                val itemYPx = (anchorCenterPx.y + dy).coerceIn(halfSizePx + 8f, maxOf(halfSizePx + 8f, screenHeightPx - halfSizePx - 8f))

                val isSelected = kotlin.math.abs(strokeSize - sizeVal) < 0.1f
                val interactionSource = remember { MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()

                val dotDiameterDp = when (sizeVal.roundToInt()) {
                    2 -> 3.5.dp
                    3 -> 5.5.dp
                    5 -> 8.0.dp
                    7 -> 10.5.dp
                    9 -> 13.0.dp
                    17 -> 16.0.dp
                    else -> ((sizeVal / 17.0f) * 12.5f + 3.5f).dp.coerceIn(3.5.dp, 16.0.dp)
                }

                val containerColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    animationSpec = BigNotesMotion.snappyTween(reducedMotion = reducedMotion),
                    label = "sizeOptionBg"
                )

                Surface(
                    shape = CircleShape,
                    color = containerColor,
                    shadowElevation = 6.dp,
                    border = if (isSelected) {
                        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                    } else {
                        BorderStroke(0.8.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    },
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (itemXPx - halfSizePx).roundToInt(),
                                (itemYPx - halfSizePx).roundToInt()
                            )
                        }
                        .size(sizeDp)
                        .tactileScale(
                            isPressed = isPressed,
                            isSelected = isSelected,
                            selectedScale = 1.08f,
                            reducedMotion = reducedMotion
                        )
                        .alpha(outerBloomProgress.coerceIn(0f, 1f))
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            enabled = visible
                        ) {
                            onStrokeSizeChange(sizeVal)
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Box(
                            modifier = Modifier
                                .size(dotDiameterDp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                                )
                        )
                    }
                }
            }

            // Compact Custom Color Picker Popover
            if (showCustomPicker && onCustomColorSelected != null) {
                val activeCustomColor = paletteColors.lastOrNull()?.color ?: Color(0xFF7C3AED)
                var hue by remember(activeCustomColor) {
                    val hsv = FloatArray(3)
                    android.graphics.Color.colorToHSV(activeCustomColor.toArgb(), hsv)
                    mutableFloatStateOf(hsv[0])
                }
                var sat by remember(activeCustomColor) {
                    val hsv = FloatArray(3)
                    android.graphics.Color.colorToHSV(activeCustomColor.toArgb(), hsv)
                    mutableFloatStateOf(hsv[1].coerceAtLeast(0.15f))
                }
                var value by remember(activeCustomColor) {
                    val hsv = FloatArray(3)
                    android.graphics.Color.colorToHSV(activeCustomColor.toArgb(), hsv)
                    mutableFloatStateOf(hsv[2].coerceAtLeast(0.15f))
                }
                val previewInt = android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value))
                val cardW = with(density) { 232.dp.toPx() }
                val cardH = with(density) { 210.dp.toPx() }
                val offsetX = when (dockEdge) {
                    DockEdge.LEFT -> anchorCenterPx.x + with(density) { 168.dp.toPx() }
                    DockEdge.RIGHT -> anchorCenterPx.x - cardW - with(density) { 168.dp.toPx() }
                    else -> (anchorCenterPx.x - cardW * 0.5f)
                }.coerceIn(12f, maxOf(12f, screenWidthPx - cardW - 12f))
                val offsetY = when (dockEdge) {
                    DockEdge.BOTTOM -> anchorCenterPx.y - cardH - with(density) { 164.dp.toPx() }
                    else -> anchorCenterPx.y + with(density) { 164.dp.toPx() }
                }.coerceIn(12f, maxOf(12f, screenHeightPx - cardH - 12f))

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                    shadowElevation = 10.dp,
                    modifier = Modifier
                        .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                        .width(232.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Custom Color",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(Color(previewInt))
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        // Quick swatches row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            QUICK_CUSTOM_COLORS.take(6).forEach { argb ->
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(Color(argb.toInt()))
                                        .clickable {
                                            onCustomColorSelected(argb)
                                            showCustomPicker = false
                                        }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Hue", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
                        Slider(
                            value = hue,
                            onValueChange = {
                                hue = it
                                val c = android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value))
                                onCustomColorSelected((c.toLong() and 0x00FFFFFFL) or 0xFF000000L)
                            },
                            valueRange = 0f..360f,
                            modifier = Modifier.fillMaxWidth().height(24.dp)
                        )
                        Text("Saturation", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
                        Slider(
                            value = sat,
                            onValueChange = {
                                sat = it
                                val c = android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value))
                                onCustomColorSelected((c.toLong() and 0x00FFFFFFL) or 0xFF000000L)
                            },
                            valueRange = 0f..1f,
                            modifier = Modifier.fillMaxWidth().height(24.dp)
                        )
                        Text("Brightness", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
                        Slider(
                            value = value,
                            onValueChange = {
                                value = it
                                val c = android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value))
                                onCustomColorSelected((c.toLong() and 0x00FFFFFFL) or 0xFF000000L)
                            },
                            valueRange = 0f..1f,
                            modifier = Modifier.fillMaxWidth().height(24.dp)
                        )
                    }
                }
            }
        }
    }
}
