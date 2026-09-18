package com.antigrav.bignotes.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Bidirectional swipe-to-reveal container:
 * - Swiping left reveals the red Delete button with 3-second blinking confirmation.
 * - Swiping right reveals a custom action (e.g. PDF / Markdown Export) on the left.
 */
@Composable
fun SwipeToRevealItem(
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    onActionRight: (() -> Unit)? = null,
    actionRightIcon: ImageVector = Icons.Default.Share,
    actionRightColor: Color = Color(0xFF0284C7),
    revealWidthDp: Int = 48,
    shape: RoundedCornerShape = RoundedCornerShape(6.dp),
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val revealWidthPx = with(density) { revealWidthDp.dp.toPx() }
    val offsetX = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()

    val isDeleteRevealed = offsetX.value < -1f
    val isActionRevealed = offsetX.value > 1f
    val isRevealed = isDeleteRevealed || isActionRevealed

    var isConfirmingDelete by remember { mutableStateOf(false) }

    // If delete button is closed, reset confirmation state
    LaunchedEffect(isDeleteRevealed) {
        if (!isDeleteRevealed) {
            isConfirmingDelete = false
        }
    }

    // 3-second countdown when delete confirmation begins
    LaunchedEffect(isConfirmingDelete) {
        if (isConfirmingDelete) {
            delay(3000L)
            coroutineScope.launch {
                offsetX.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                )
                isConfirmingDelete = false
            }
        }
    }

    // Pulsing blink animation for confirmation state (flashing at ~240ms cycle)
    val infiniteTransition = rememberInfiniteTransition(label = "deleteBlink")
    val blinkAlpha by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 0.20f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 240, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blinkAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
    ) {
        // Red Delete button behind (revealed by swiping left)
        if (isDeleteRevealed) {
            val bgAlpha = if (isConfirmingDelete) blinkAlpha else 1.0f
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color(0xFFE53935).copy(alpha = bgAlpha))
                    .clickable {
                        if (!isConfirmingDelete) {
                            // First tap: start blinking for 3 seconds to await confirmation
                            isConfirmingDelete = true
                        } else {
                            // Second tap within 3 seconds: confirm delete!
                            isConfirmingDelete = false
                            coroutineScope.launch {
                                offsetX.snapTo(0f)
                                onDelete()
                            }
                        }
                    },
                contentAlignment = Alignment.CenterEnd
            ) {
                Box(
                    modifier = Modifier
                        .width(revealWidthDp.dp)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isConfirmingDelete) Icons.Default.DeleteForever else Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = Color.White,
                        modifier = Modifier
                            .size(22.dp)
                            .then(if (isConfirmingDelete) Modifier.alpha(blinkAlpha) else Modifier)
                    )
                }
            }
        }

        // Custom Action button behind (e.g. PDF Export revealed by swiping right)
        if (isActionRevealed && onActionRight != null) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(actionRightColor)
                    .clickable {
                        coroutineScope.launch {
                            offsetX.animateTo(0f)
                            onActionRight()
                        }
                    },
                contentAlignment = Alignment.CenterStart
            ) {
                Box(
                    modifier = Modifier
                        .width(revealWidthDp.dp)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = actionRightIcon,
                        contentDescription = "Action",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        // Foreground content with solid surface background
        Surface(
            shape = shape,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(onActionRight) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { _, dragAmount ->
                            isConfirmingDelete = false
                            val minOffset = -revealWidthPx
                            val maxOffset = if (onActionRight != null) revealWidthPx else 0f
                            val newOffset = (offsetX.value + dragAmount).coerceIn(minOffset, maxOffset)
                            coroutineScope.launch {
                                offsetX.snapTo(newOffset)
                            }
                        },
                        onDragEnd = {
                            coroutineScope.launch {
                                if (offsetX.value < -revealWidthPx / 2) {
                                    // Snap open delete on right
                                    offsetX.animateTo(
                                        targetValue = -revealWidthPx,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessLow
                                        )
                                    )
                                } else if (onActionRight != null && offsetX.value > revealWidthPx / 2) {
                                    // Snap open action on left
                                    offsetX.animateTo(
                                        targetValue = revealWidthPx,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessLow
                                        )
                                    )
                                } else {
                                    // Snap closed
                                    offsetX.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                            stiffness = Spring.StiffnessMedium
                                        )
                                    )
                                }
                            }
                        },
                        onDragCancel = {
                            isConfirmingDelete = false
                            coroutineScope.launch {
                                offsetX.animateTo(0f)
                            }
                        }
                    )
                }
        ) {
            Box {
                content()
                // If open in either direction, tapping on content closes without triggering note/folder selection
                if (isRevealed) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable {
                                isConfirmingDelete = false
                                coroutineScope.launch {
                                    offsetX.animateTo(0f)
                                }
                            }
                    )
                }
            }
        }
    }
}
