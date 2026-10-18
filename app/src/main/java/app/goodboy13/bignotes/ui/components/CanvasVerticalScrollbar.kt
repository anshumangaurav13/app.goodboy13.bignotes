package app.goodboy13.bignotes.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun CanvasVerticalScrollbar(
    isScrolling: Boolean,
    scrollFraction: Float,
    thumbRatio: Float,
    currentScrollWorldY: Float,
    worldPageHeight: Float = 1750f,
    onScrollToFraction: (Float) -> Unit,
    onScrubbingFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isScrubbing by remember { mutableStateOf(false) }
    var isVisible by remember { mutableStateOf(false) }

    // Visibility debounce: stays visible while scrolling or scrubbing, fades out 1000ms after idle
    LaunchedEffect(isScrolling, isScrubbing) {
        if (isScrolling || isScrubbing) {
            isVisible = true
        } else {
            delay(1000L)
            isVisible = false
        }
    }

    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (isVisible) 150 else 350,
            easing = FastOutSlowInEasing
        ),
        label = "scrollbarAlpha"
    )

    if (alpha <= 0.005f && !isScrubbing) {
        return
    }

    val density = LocalDensity.current
    val minThumbHeightPx = with(density) { 48.dp.toPx() }
    val barWidth by animateDpAsState(
        targetValue = if (isScrubbing) 9.dp else 5.dp,
        animationSpec = tween(durationMillis = 150),
        label = "scrollbarWidth"
    )

    var trackHeightPx by remember { mutableFloatStateOf(0f) }

    val thumbHeightPx = (trackHeightPx * thumbRatio).coerceIn(minThumbHeightPx, maxOf(minThumbHeightPx, trackHeightPx))
    val maxThumbOffset = (trackHeightPx - thumbHeightPx).coerceAtLeast(0f)
    val thumbOffsetY = (scrollFraction * maxThumbOffset).coerceIn(0f, maxThumbOffset)

    val pageNumber = (currentScrollWorldY / worldPageHeight.coerceAtLeast(100f)).toInt().coerceAtLeast(0) + 1

    val currentMaxThumbOffset by rememberUpdatedState(maxThumbOffset)
    val currentThumbHeightPx by rememberUpdatedState(thumbHeightPx)
    val currentOnScrollToFraction by rememberUpdatedState(onScrollToFraction)
    val currentOnScrubbingFinished by rememberUpdatedState(onScrubbingFinished)

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(44.dp)
            .alpha(alpha)
            .onGloballyPositioned { coordinates ->
                trackHeightPx = coordinates.size.height.toFloat()
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // Strictly interact with finger touch. Let stylus write through cleanly!
                    if (down.type == PointerType.Touch) {
                        down.consume()
                        isScrubbing = true
                        val startY = down.position.y
                        val maxOffset = currentMaxThumbOffset
                        val thumbH = currentThumbHeightPx
                        val initialFraction = if (maxOffset > 0f) {
                            ((startY - thumbH * 0.5f) / maxOffset).coerceIn(0f, 1f)
                        } else 0f
                        currentOnScrollToFraction(initialFraction)

                        while (true) {
                            val event = awaitPointerEvent()
                            val drag = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!drag.pressed) {
                                break
                            }
                            drag.consume()
                            val curY = drag.position.y
                            val curMax = currentMaxThumbOffset
                            val curH = currentThumbHeightPx
                            val fraction = if (curMax > 0f) {
                                ((curY - curH * 0.5f) / curMax).coerceIn(0f, 1f)
                            } else 0f
                            currentOnScrollToFraction(fraction)
                        }
                        isScrubbing = false
                        currentOnScrubbingFinished()
                    }
                }
            }
    ) {
        // Track line
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 6.dp)
                .fillMaxHeight()
                .width(2.dp)
                .background(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (isScrubbing) 0.12f else 0.05f),
                    shape = CircleShape
                )
        )

        // Draggable Scrollbar Thumb
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 4.dp)
                .offset { IntOffset(0, thumbOffsetY.roundToInt()) }
                .height(with(density) { thumbHeightPx.toDp() })
                .width(barWidth)
                .shadow(
                    elevation = if (isScrubbing) 3.dp else 1.dp,
                    shape = CircleShape
                )
                .background(
                    color = if (isScrubbing) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.40f)
                    },
                    shape = CircleShape
                )
        )

        // Floating Page Number Badge (shown while actively scrubbing)
        AnimatedVisibility(
            visible = isScrubbing,
            enter = fadeIn(animationSpec = tween(120)),
            exit = fadeOut(animationSpec = tween(150)),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 22.dp)
                .offset {
                    val badgeCenterY = (thumbOffsetY + thumbHeightPx * 0.5f - with(density) { 14.dp.toPx() }).roundToInt()
                    IntOffset(0, badgeCenterY.coerceAtLeast(0))
                }
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.88f),
                shadowElevation = 4.dp
            ) {
                Text(
                    text = "$pageNumber",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.inverseOnSurface
                    ),
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                )
            }
        }
    }
}
