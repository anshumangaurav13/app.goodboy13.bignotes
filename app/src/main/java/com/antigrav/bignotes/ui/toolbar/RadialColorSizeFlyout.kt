package com.antigrav.bignotes.ui.toolbar

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.antigrav.bignotes.ui.theme.InkColorItem
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Dual-Arc Radial Satellite Flyout for Inking Colors & Stroke Sizes.
 *
 * Blooms radially outward from the active tool button into open canvas space:
 * - Inner Arc: 6 Color swatches
 * - Outer Arc: 6 Stroke thickness swatches (2pt to 17pt)
 */
@Composable
fun RadialColorSizeFlyout(
    anchorCenterPx: Offset,
    dockEdge: DockEdge,
    paletteColors: List<InkColorItem>,
    selectedColorIndex: Int,
    onColorSelect: (Int) -> Unit,
    strokeSize: Float,
    onStrokeSizeChange: (Float) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    var isBloomed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isBloomed = true
    }

    val bloomProgress by animateFloatAsState(
        targetValue = if (isBloomed) 1f else 0f,
        animationSpec = spring(
            stiffness = Spring.StiffnessMediumLow,
            dampingRatio = 0.72f
        ),
        label = "RadialBloomAnimation"
    )

    // Directional angles based on dock edge
    val (centerAngleDeg, arcSpanDeg) = when (dockEdge) {
        DockEdge.TOP -> 90f to 130f     // Downward into canvas
        DockEdge.BOTTOM -> 270f to 130f // Upward into canvas
        DockEdge.LEFT -> 0f to 130f     // Rightward into canvas
        DockEdge.RIGHT -> 180f to 130f  // Leftward into canvas
    }

    val startAngleDeg = centerAngleDeg - arcSpanDeg / 2f

    val innerRadiusPx = with(density) { 72.dp.toPx() } * bloomProgress
    val outerRadiusPx = with(density) { 128.dp.toPx() } * bloomProgress

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        // Inner Arc: 6 Color Swatches
        paletteColors.forEachIndexed { index, colorItem ->
            val angle = startAngleDeg + index * (arcSpanDeg / (paletteColors.size - 1).coerceAtLeast(1))
            val rad = Math.toRadians(angle.toDouble())
            val dx = (innerRadiusPx * cos(rad)).toFloat()
            val dy = (innerRadiusPx * sin(rad)).toFloat()

            val sizeDp = 30.dp
            val halfSizePx = with(density) { (sizeDp / 2).toPx() }

            val itemXPx = (anchorCenterPx.x + dx).coerceIn(halfSizePx + 8f, screenWidthPx - halfSizePx - 8f)
            val itemYPx = (anchorCenterPx.y + dy).coerceIn(halfSizePx + 8f, screenHeightPx - halfSizePx - 8f)

            val isSelected = index == selectedColorIndex

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
                    .scale(0.35f + 0.65f * bloomProgress)
                    .alpha(bloomProgress.coerceIn(0f, 1f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
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

        // Outer Arc: 6 Stroke Sizes
        STROKE_SIZES.forEachIndexed { index, sizeVal ->
            val angle = startAngleDeg + index * (arcSpanDeg / (STROKE_SIZES.size - 1).coerceAtLeast(1))
            val rad = Math.toRadians(angle.toDouble())
            val dx = (outerRadiusPx * cos(rad)).toFloat()
            val dy = (outerRadiusPx * sin(rad)).toFloat()

            val sizeDp = 34.dp
            val halfSizePx = with(density) { (sizeDp / 2).toPx() }

            val itemXPx = (anchorCenterPx.x + dx).coerceIn(halfSizePx + 8f, screenWidthPx - halfSizePx - 8f)
            val itemYPx = (anchorCenterPx.y + dy).coerceIn(halfSizePx + 8f, screenHeightPx - halfSizePx - 8f)

            val isSelected = kotlin.math.abs(strokeSize - sizeVal) < 0.1f

            val dotDiameterDp = when (sizeVal.roundToInt()) {
                2 -> 3.5.dp
                3 -> 5.5.dp
                5 -> 8.0.dp
                7 -> 10.5.dp
                9 -> 13.0.dp
                17 -> 16.0.dp
                else -> ((sizeVal / 17.0f) * 12.5f + 3.5f).dp.coerceIn(3.5.dp, 16.0.dp)
            }

            Surface(
                shape = CircleShape,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surface
                },
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
                    .scale(0.35f + 0.65f * bloomProgress)
                    .alpha(bloomProgress.coerceIn(0f, 1f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
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
