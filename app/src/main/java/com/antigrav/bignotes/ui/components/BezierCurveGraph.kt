package com.antigrav.bignotes.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigrav.bignotes.core.model.BezierControlPoints
import java.util.Locale
import kotlin.math.hypot
import kotlin.math.round

@Composable
fun BezierCurveGraph(
    config: BezierControlPoints,
    onConfigChange: (BezierControlPoints) -> Unit,
    modifier: Modifier = Modifier,
    presets: List<Pair<String, BezierControlPoints>> = emptyList(),
    curveColor: Color = MaterialTheme.colorScheme.primary,
    handle1Color: Color = Color(0xFF1D4ED8), // Royal Blue
    handle2Color: Color = Color(0xFFF97316)  // Vivid Orange
) {
    var activeHandle by remember { mutableIntStateOf(0) } // 0 = none, 1 = cp1, 2 = cp2
    val currentConfig by rememberUpdatedState(config)
    val currentOnConfigChange by rememberUpdatedState(onConfigChange)

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Coordinate readout header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .background(handle1Color, CircleShape)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = String.format(Locale.US, "P1:(%.2f,%.2f)", config.cp1x, config.cp1y),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .background(handle2Color, CircleShape)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = String.format(Locale.US, "P2:(%.2f,%.2f)", config.cp2x, config.cp2y),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                )
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Interactive Graph Canvas Area
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.0f)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    RoundedCornerShape(10.dp)
                )
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                    RoundedCornerShape(10.dp)
                )
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            val canvasW = constraints.maxWidth.toFloat()
            val canvasH = constraints.maxHeight.toFloat()
            val pad = 8f
            val graphLeft = pad
            val graphRight = canvasW - pad
            val graphTop = pad
            val graphBottom = canvasH - pad
            val graphW = (graphRight - graphLeft).coerceAtLeast(1f)
            val graphH = (graphBottom - graphTop).coerceAtLeast(1f)

            fun toScreen(nx: Float, ny: Float): Offset {
                val sx = graphLeft + nx.coerceIn(0f, 1f) * graphW
                val sy = graphBottom - ny.coerceIn(0f, 1f) * graphH
                return Offset(sx, sy)
            }

            fun toNormalized(sx: Float, sy: Float): Pair<Float, Float> {
                val nx = ((sx - graphLeft) / graphW).coerceIn(0f, 1f)
                val ny = ((graphBottom - sy) / graphH).coerceIn(0f, 1f)
                return Pair(round(nx * 100f) / 100f, round(ny * 100f) / 100f)
            }

            val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
            val diagonalColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f)
            val anchorColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val cfg = currentConfig
                                val p1Screen = toScreen(cfg.cp1x, cfg.cp1y)
                                val p2Screen = toScreen(cfg.cp2x, cfg.cp2y)
                                val d1 = hypot((offset.x - p1Screen.x).toDouble(), (offset.y - p1Screen.y).toDouble()).toFloat()
                                val d2 = hypot((offset.x - p2Screen.x).toDouble(), (offset.y - p2Screen.y).toDouble()).toFloat()
                                activeHandle = if (d1 <= d2) 1 else 2
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                val (nx, ny) = toNormalized(change.position.x, change.position.y)
                                val cfg = currentConfig
                                if (activeHandle == 1) {
                                    currentOnConfigChange(cfg.copy(cp1x = nx, cp1y = ny))
                                } else if (activeHandle == 2) {
                                    currentOnConfigChange(cfg.copy(cp2x = nx, cp2y = ny))
                                }
                            },
                            onDragEnd = { activeHandle = 0 },
                            onDragCancel = { activeHandle = 0 }
                        )
                    }
            ) {
                // 1. Draw 4x4 coordinate grid (5 horizontal lines, 5 vertical lines)
                val dashEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f)
                for (i in 0..4) {
                    val t = i / 4.0f
                    val y = graphBottom - t * graphH
                    val x = graphLeft + t * graphW

                    // Horizontal grid line
                    drawLine(
                        color = gridColor,
                        start = Offset(graphLeft, y),
                        end = Offset(graphRight, y),
                        strokeWidth = if (i == 0 || i == 4) 1.5f else 1f,
                        pathEffect = if (i == 0 || i == 4) null else dashEffect
                    )

                    // Vertical grid line
                    drawLine(
                        color = gridColor,
                        start = Offset(x, graphTop),
                        end = Offset(x, graphBottom),
                        strokeWidth = if (i == 0 || i == 4) 1.5f else 1f,
                        pathEffect = if (i == 0 || i == 4) null else dashEffect
                    )
                }

                // 2. Diagonal reference line (0,0) -> (1,1)
                drawLine(
                    color = diagonalColor,
                    start = Offset(graphLeft, graphBottom),
                    end = Offset(graphRight, graphTop),
                    strokeWidth = 1.0f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                )

                val startPos = Offset(graphLeft, graphBottom)
                val endPos = Offset(graphRight, graphTop)
                val cp1Pos = toScreen(config.cp1x, config.cp1y)
                val cp2Pos = toScreen(config.cp2x, config.cp2y)

                // 3. Tangent handle lines
                drawLine(
                    color = handle1Color.copy(alpha = 0.65f),
                    start = startPos,
                    end = cp1Pos,
                    strokeWidth = 1.8f
                )
                drawLine(
                    color = handle2Color.copy(alpha = 0.65f),
                    start = endPos,
                    end = cp2Pos,
                    strokeWidth = 1.8f
                )

                // 4. Continuous Cubic Bezier Curve
                val curvePath = Path().apply {
                    moveTo(startPos.x, startPos.y)
                    cubicTo(
                        cp1Pos.x, cp1Pos.y,
                        cp2Pos.x, cp2Pos.y,
                        endPos.x, endPos.y
                    )
                }
                drawPath(
                    path = curvePath,
                    color = curveColor,
                    style = Stroke(width = 3.0.dp.toPx(), cap = StrokeCap.Round)
                )

                // 5. Anchors at (0,0) and (1,1)
                drawCircle(
                    color = anchorColor,
                    radius = 4.dp.toPx(),
                    center = startPos
                )
                drawCircle(
                    color = anchorColor,
                    radius = 4.dp.toPx(),
                    center = endPos
                )

                // 6. Control point draggable handles (knobs)
                // Handle 1 (CP1)
                drawCircle(
                    color = handle1Color,
                    radius = if (activeHandle == 1) 9.dp.toPx() else 7.dp.toPx(),
                    center = cp1Pos
                )
                drawCircle(
                    color = Color.White,
                    radius = 2.5.dp.toPx(),
                    center = cp1Pos
                )

                // Handle 2 (CP2)
                drawCircle(
                    color = handle2Color,
                    radius = if (activeHandle == 2) 9.dp.toPx() else 7.dp.toPx(),
                    center = cp2Pos
                )
                drawCircle(
                    color = Color.White,
                    radius = 2.5.dp.toPx(),
                    center = cp2Pos
                )
            }
        }

        // Preset Chips (2x2 grid for clean, uncrowded layout)
        if (presets.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                for (chunk in presets.chunked(2)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        for ((label, presetConfig) in chunk) {
                            val isSelected = kotlin.math.abs(config.cp1x - presetConfig.cp1x) < 0.03f &&
                                    kotlin.math.abs(config.cp1y - presetConfig.cp1y) < 0.03f &&
                                    kotlin.math.abs(config.cp2x - presetConfig.cp2x) < 0.03f &&
                                    kotlin.math.abs(config.cp2y - presetConfig.cp2y) < 0.03f

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                onClick = { onConfigChange(presetConfig) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(24.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 10.sp
                                        ),
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
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
