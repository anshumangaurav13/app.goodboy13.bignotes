package app.goodboy13.bignotes.ui.toolbar

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
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

/**
 * Dual-Arc Radial Satellite Flyout for Inking Colors & Stroke Sizes (or Eraser Sizes).
 *
 * Blooms radially outward from the active tool button with staggered follow-through between
 * the inner arc and outer arc, tactile press compression, and symmetric collapse on dismissal.
 */
@Composable
fun RadialColorSizeFlyout(
    visible: Boolean,
    anchorCenterPx: androidx.compose.ui.geometry.Offset,
    dockEdge: DockEdge,
    paletteColors: List<InkColorItem>,
    selectedColorIndex: Int,
    onColorSelect: (Int) -> Unit,
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

    LaunchedEffect(visible, reducedMotion) {
        if (visible) {
            if (!reducedMotion) delay(30L)
            outerTargetBloomed = true
        } else {
            outerTargetBloomed = false
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

    // Directional angles based on dock edge
    val (centerAngleDeg, arcSpanDeg) = when (dockEdge) {
        DockEdge.TOP -> 90f to 130f     // Downward into canvas
        DockEdge.BOTTOM -> 270f to 130f // Upward into canvas
        DockEdge.LEFT -> 0f to 130f     // Rightward into canvas
        DockEdge.RIGHT -> 180f to 130f  // Leftward into canvas
    }

    val startAngleDeg = centerAngleDeg - arcSpanDeg / 2f

    val innerRadiusPx = with(density) { 72.dp.toPx() } * innerBloomProgress
    val outerRadiusPx = with(density) { 128.dp.toPx() } * outerBloomProgress

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        if (isEraser) {
            // Inner Arc: 6 Eraser Size Options
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
                        androidx.compose.foundation.BorderStroke(2.5.dp, MaterialTheme.colorScheme.primary)
                    } else {
                        androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
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
            // Inner Arc: 6 Color Swatches
            paletteColors.forEachIndexed { index, colorItem ->
                val angle = startAngleDeg + index * (arcSpanDeg / (paletteColors.size - 1).coerceAtLeast(1))
                val rad = Math.toRadians(angle.toDouble())
                val dx = (innerRadiusPx * cos(rad)).toFloat()
                val dy = (innerRadiusPx * sin(rad)).toFloat()

                val sizeDp = 30.dp
                val halfSizePx = with(density) { (sizeDp / 2).toPx() }

                val itemXPx = (anchorCenterPx.x + dx).coerceIn(halfSizePx + 8f, maxOf(halfSizePx + 8f, screenWidthPx - halfSizePx - 8f))
                val itemYPx = (anchorCenterPx.y + dy).coerceIn(halfSizePx + 8f, maxOf(halfSizePx + 8f, screenHeightPx - halfSizePx - 8f))

                val isSelected = index == selectedColorIndex
                val interactionSource = remember { MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()

                Surface(
                    shape = CircleShape,
                    color = colorItem.color,
                    shadowElevation = 5.dp,
                    border = if (isSelected) {
                        androidx.compose.foundation.BorderStroke(2.5.dp, MaterialTheme.colorScheme.primary)
                    } else {
                        androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.65f))
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
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            enabled = visible
                        ) {
                            onColorSelect(index)
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isSelected) {
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

            // Outer Arc: 6 Stroke Sizes (staggered behind inner arc)
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
                        androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                    } else {
                        androidx.compose.foundation.BorderStroke(0.8.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
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
        }
    }
}
