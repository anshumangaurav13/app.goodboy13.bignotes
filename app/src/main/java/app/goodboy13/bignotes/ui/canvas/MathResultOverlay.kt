package app.goodboy13.bignotes.ui.canvas

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.goodboy13.bignotes.core.gl.SketchCanvasView
import app.goodboy13.bignotes.core.math.model.MathOverlayState
import app.goodboy13.bignotes.core.math.model.MathResult

/**
 * Floating interactive card displaying the evaluated mathematical expression and result
 * anchored to the selection bounds in canvas screen space.
 */
@Composable
fun MathResultOverlay(
    overlayState: MathOverlayState?,
    canvasView: SketchCanvasView?,
    viewportTick: Int,
    onInsertAnswer: () -> Unit,
    onCopyAnswer: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (overlayState == null || canvasView == null) return

    @Suppress("UNUSED_VARIABLE")
    val tick = viewportTick
    val density = LocalDensity.current
    val screenConfig = LocalConfiguration.current

    val (sLeft, sTop) = canvasView.worldToScreen(overlayState.worldBounds.left, overlayState.worldBounds.top)
    val (sRight, sBottom) = canvasView.worldToScreen(overlayState.worldBounds.right, overlayState.worldBounds.bottom)

    val left = minOf(sLeft, sRight)
    val right = maxOf(sLeft, sRight)
    val top = minOf(sTop, sBottom)
    val bottom = maxOf(sTop, sBottom)

    val cardHeightDp = 76.dp
    val cardHeightPx = with(density) { cardHeightDp.toPx() }

    // Position above the equation if space allows, otherwise below
    val targetY = if (top > cardHeightPx + 20f) {
        top - cardHeightPx - 12f
    } else {
        bottom + 12f
    }

    val centerX = (left + right) * 0.5f
    val cardWidthDp = 280.dp
    val cardHalfWidthPx = with(density) { (cardWidthDp / 2).toPx() }
    val targetX = centerX - cardHalfWidthPx

    val minXDp = 12.dp
    val maxXDp = (screenConfig.screenWidthDp.dp - cardWidthDp - 12.dp).coerceAtLeast(12.dp)
    val clampedXDp = with(density) { targetX.toDp() }.coerceIn(minXDp, maxXDp)
    val clampedYDp = with(density) { targetY.toDp() }.coerceAtLeast(12.dp)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp),
        tonalElevation = 8.dp,
        shadowElevation = 6.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
        modifier = modifier
            .offset(x = clampedXDp, y = clampedYDp)
            .width(cardWidthDp)
            .height(cardHeightDp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                when (val result = overlayState.result) {
                    is MathResult.Success -> {
                        Text(
                            text = overlayState.expression,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                            maxLines = 1
                        )
                        Text(
                            text = "= ${result.formattedResult}",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            ),
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1
                        )
                    }
                    is MathResult.Error -> {
                        Text(
                            text = overlayState.expression.ifEmpty { "Math Error" },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                            maxLines = 1
                        )
                        Text(
                            text = result.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Action Buttons
            if (overlayState.result is MathResult.Success) {
                FilledTonalIconButton(
                    onClick = onInsertAnswer,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Draw,
                        contentDescription = "Insert as Ink",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                IconButton(
                    onClick = onCopyAnswer,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy Result",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
