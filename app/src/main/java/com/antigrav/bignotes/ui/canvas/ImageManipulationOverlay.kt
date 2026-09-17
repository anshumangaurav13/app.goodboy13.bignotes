package com.antigrav.bignotes.ui.canvas

import android.graphics.PointF
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigrav.bignotes.core.gl.SketchCanvasView
import com.antigrav.bignotes.core.model.CanvasImage
import kotlin.math.*

private enum class DragMode {
    NONE, MOVE, SCALE, ROTATE, CROP_HANDLE
}

@Composable
fun ImageManipulationOverlay(
    selectedImage: CanvasImage?,
    canvasView: SketchCanvasView?,
    viewportTick: Int,
    onUpdateImageLive: (CanvasImage) -> Unit,
    onUpdateImage: (previous: CanvasImage, current: CanvasImage) -> Unit,
    onDeleteImage: (CanvasImage) -> Unit,
    onDuplicateImage: (CanvasImage) -> Unit,
    onDeselect: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (selectedImage == null || canvasView == null) return

    val currentSelectedImage by rememberUpdatedState(selectedImage)
    val currentCanvasView by rememberUpdatedState(canvasView)

    @Suppress("UNUSED_VARIABLE")
    val tick = viewportTick

    var isCropMode by remember { mutableStateOf(false) }
    var dragMode by remember { mutableStateOf(DragMode.NONE) }
    var activeHandleIndex by remember { mutableIntStateOf(-1) }
    var dragStartImage by remember { mutableStateOf(selectedImage) }
    var dragStartWorldPoint by remember { mutableStateOf(PointF(0f, 0f)) }
    var dragStartScreenPoint by remember { mutableStateOf(Offset.Zero) }

    // Crop UV relative to current image [0..1]
    var cropLeft by remember(selectedImage.id, isCropMode) { mutableFloatStateOf(0f) }
    var cropTop by remember(selectedImage.id, isCropMode) { mutableFloatStateOf(0f) }
    var cropRight by remember(selectedImage.id, isCropMode) { mutableFloatStateOf(1f) }
    var cropBottom by remember(selectedImage.id, isCropMode) { mutableFloatStateOf(1f) }

    val density = LocalDensity.current
    val handleTouchRadiusPx = with(density) { 36.dp.toPx() }
    val rotationStemDistPx = with(density) { 32.dp.toPx() }

    // World corner points
    val hw = selectedImage.width * 0.5f
    val hh = selectedImage.height * 0.5f
    val rad = Math.toRadians(selectedImage.rotationDegrees.toDouble())
    val cosR = cos(rad).toFloat()
    val sinR = sin(rad).toFloat()

    fun rotateRelX(lx: Float, ly: Float): Float = selectedImage.centerX + lx * cosR - ly * sinR
    fun rotateRelY(lx: Float, ly: Float): Float = selectedImage.centerY + lx * sinR + ly * cosR

    val wTL = PointF(rotateRelX(-hw, -hh), rotateRelY(-hw, -hh))
    val wTR = PointF(rotateRelX(hw, -hh), rotateRelY(hw, -hh))
    val wBR = PointF(rotateRelX(hw, hh), rotateRelY(hw, hh))
    val wBL = PointF(rotateRelX(-hw, hh), rotateRelY(-hw, hh))
    val wTopMid = PointF(rotateRelX(0f, -hh), rotateRelY(0f, -hh))

    val (sTLx, sTLy) = canvasView.worldToScreen(wTL.x, wTL.y)
    val (sTRx, sTRy) = canvasView.worldToScreen(wTR.x, wTR.y)
    val (sBRx, sBRy) = canvasView.worldToScreen(wBR.x, wBR.y)
    val (sBLx, sBLy) = canvasView.worldToScreen(wBL.x, wBL.y)
    val (sTMx, sTMy) = canvasView.worldToScreen(wTopMid.x, wTopMid.y)
    val (sCx, sCy) = canvasView.worldToScreen(selectedImage.centerX, selectedImage.centerY)

    val sTL = Offset(sTLx, sTLy)
    val sTR = Offset(sTRx, sTRy)
    val sBR = Offset(sBRx, sBRy)
    val sBL = Offset(sBLx, sBLy)
    val sTM = Offset(sTMx, sTMy)
    val sCenter = Offset(sCx, sCy)

    // Rotation handle screen position
    val topNormal = (sTM - sCenter)
    val topNormalLen = topNormal.getDistance()
    val rotDir = if (topNormalLen > 0.001f) topNormal / topNormalLen else Offset(0f, -1f)
    val sRot = sTM + rotDir * rotationStemDistPx

    val cornerScreenOffsets = listOf(sTL, sTR, sBR, sBL)

    // Bounding screen AABB for toolbar placement
    val allScreenX = listOf(sTL.x, sTR.x, sBR.x, sBL.x, sRot.x)
    val allScreenY = listOf(sTL.y, sTR.y, sBR.y, sBL.y, sRot.y)
    val minBoxX = allScreenX.minOrNull() ?: 0f
    val maxBoxX = allScreenX.maxOrNull() ?: 0f
    val minBoxY = allScreenY.minOrNull() ?: 0f
    val maxBoxY = allScreenY.maxOrNull() ?: 0f

    val primaryColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(selectedImage.id, isCropMode) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    dragStartScreenPoint = down.position
                    val img = currentSelectedImage
                    val view = currentCanvasView
                    dragStartImage = img
                    val (wx, wy) = view.screenToWorld(down.position.x, down.position.y)
                    dragStartWorldPoint = PointF(wx, wy)

                    // Compute fresh live screen positions to ensure 100% grab reliability
                    val liveHw = img.width * 0.5f
                    val liveHh = img.height * 0.5f
                    val liveRad = Math.toRadians(img.rotationDegrees.toDouble())
                    val liveCosR = cos(liveRad).toFloat()
                    val liveSinR = sin(liveRad).toFloat()

                    fun liveRotX(lx: Float, ly: Float) = img.centerX + lx * liveCosR - ly * liveSinR
                    fun liveRotY(lx: Float, ly: Float) = img.centerY + lx * liveSinR + ly * liveCosR

                    if (!isCropMode) {
                        val liveWTM = PointF(liveRotX(0f, -liveHh), liveRotY(0f, -liveHh))
                        val (liveTMx, liveTMy) = view.worldToScreen(liveWTM.x, liveWTM.y)
                        val (liveCx, liveCy) = view.worldToScreen(img.centerX, img.centerY)
                        val liveTM = Offset(liveTMx, liveTMy)
                        val liveCenter = Offset(liveCx, liveCy)
                        val liveNorm = liveTM - liveCenter
                        val liveNormLen = liveNorm.getDistance()
                        val liveDir = if (liveNormLen > 0.001f) liveNorm / liveNormLen else Offset(0f, -1f)
                        val liveRot = liveTM + liveDir * rotationStemDistPx

                        // 1. Check rotation handle
                        if ((down.position - liveRot).getDistance() <= handleTouchRadiusPx) {
                            dragMode = DragMode.ROTATE
                        } else {
                            // 2. Check 4 corner scale handles
                            val liveCorners = listOf(
                                view.worldToScreen(liveRotX(-liveHw, -liveHh), liveRotY(-liveHw, -liveHh)).let { Offset(it.first, it.second) },
                                view.worldToScreen(liveRotX(liveHw, -liveHh), liveRotY(liveHw, -liveHh)).let { Offset(it.first, it.second) },
                                view.worldToScreen(liveRotX(liveHw, liveHh), liveRotY(liveHw, liveHh)).let { Offset(it.first, it.second) },
                                view.worldToScreen(liveRotX(-liveHw, liveHh), liveRotY(-liveHw, liveHh)).let { Offset(it.first, it.second) }
                            )

                            var hitCorner = -1
                            for (i in liveCorners.indices) {
                                if ((down.position - liveCorners[i]).getDistance() <= handleTouchRadiusPx) {
                                    hitCorner = i
                                    break
                                }
                            }

                            if (hitCorner >= 0) {
                                dragMode = DragMode.SCALE
                                activeHandleIndex = hitCorner
                            } else if (img.containsPoint(wx, wy, padding = 16f)) {
                                dragMode = DragMode.MOVE
                            } else {
                                dragMode = DragMode.NONE
                            }
                        }
                    } else {
                        // Crop mode handles in screen space
                        val cl = -liveHw + img.width * cropLeft
                        val cr = -liveHw + img.width * cropRight
                        val ct = -liveHh + img.height * cropTop
                        val cb = -liveHh + img.height * cropBottom

                        val sCTL = view.worldToScreen(liveRotX(cl, ct), liveRotY(cl, ct)).let { Offset(it.first, it.second) }
                        val sCTR = view.worldToScreen(liveRotX(cr, ct), liveRotY(cr, ct)).let { Offset(it.first, it.second) }
                        val sCBR = view.worldToScreen(liveRotX(cr, cb), liveRotY(cr, cb)).let { Offset(it.first, it.second) }
                        val sCBL = view.worldToScreen(liveRotX(cl, cb), liveRotY(cl, cb)).let { Offset(it.first, it.second) }

                        val sCLeft = (sCTL + sCBL) * 0.5f
                        val sCTop = (sCTL + sCTR) * 0.5f
                        val sCRight = (sCTR + sCBR) * 0.5f
                        val sCBottom = (sCBL + sCBR) * 0.5f

                        when {
                            (down.position - sCTL).getDistance() <= handleTouchRadiusPx -> { dragMode = DragMode.CROP_HANDLE; activeHandleIndex = 4 }
                            (down.position - sCTR).getDistance() <= handleTouchRadiusPx -> { dragMode = DragMode.CROP_HANDLE; activeHandleIndex = 5 }
                            (down.position - sCBR).getDistance() <= handleTouchRadiusPx -> { dragMode = DragMode.CROP_HANDLE; activeHandleIndex = 6 }
                            (down.position - sCBL).getDistance() <= handleTouchRadiusPx -> { dragMode = DragMode.CROP_HANDLE; activeHandleIndex = 7 }
                            (down.position - sCLeft).getDistance() <= handleTouchRadiusPx -> { dragMode = DragMode.CROP_HANDLE; activeHandleIndex = 0 }
                            (down.position - sCTop).getDistance() <= handleTouchRadiusPx -> { dragMode = DragMode.CROP_HANDLE; activeHandleIndex = 1 }
                            (down.position - sCRight).getDistance() <= handleTouchRadiusPx -> { dragMode = DragMode.CROP_HANDLE; activeHandleIndex = 2 }
                            (down.position - sCBottom).getDistance() <= handleTouchRadiusPx -> { dragMode = DragMode.CROP_HANDLE; activeHandleIndex = 3 }
                            else -> { dragMode = DragMode.NONE }
                        }
                    }

                    var twoFingerBaseImage = img
                    var twoFingerBaseDist = 0f
                    var twoFingerBaseAngle = 0f
                    var twoFingerBaseCentroid = Offset.Zero
                    var isTwoFingerActive = false
                    var currentLiveImage = img
                    var hasMoved = false

                    while (true) {
                        val event = awaitPointerEvent()
                        val activePointers = event.changes.filter { it.pressed }
                        if (activePointers.isEmpty()) break

                        if (!isCropMode && activePointers.size >= 2) {
                            // 2-FINGER MULTI-TOUCH: Scale (Pinch) + Rotate (Twist) + Pan
                            val p1 = activePointers[0].position
                            val p2 = activePointers[1].position
                            val curDist = (p2 - p1).getDistance()
                            val curAngle = atan2((p2.y - p1.y).toDouble(), (p2.x - p1.x).toDouble()).toFloat()
                            val curCentroid = (p1 + p2) * 0.5f

                            if (!isTwoFingerActive) {
                                isTwoFingerActive = true
                                twoFingerBaseImage = currentLiveImage
                                twoFingerBaseDist = max(curDist, 10f)
                                twoFingerBaseAngle = curAngle
                                twoFingerBaseCentroid = curCentroid
                            } else {
                                hasMoved = true
                                val scaleFactor = (curDist / twoFingerBaseDist).coerceIn(0.05f, 20.0f)
                                val newW = (twoFingerBaseImage.width * scaleFactor).coerceIn(30f, 10000f)
                                val newH = (twoFingerBaseImage.height * scaleFactor).coerceIn(30f, 10000f)

                                val angleDiff = curAngle - twoFingerBaseAngle
                                var newDeg = twoFingerBaseImage.rotationDegrees + Math.toDegrees(angleDiff.toDouble()).toFloat()
                                newDeg = ((newDeg % 360f) + 360f) % 360f

                                val snapTargets = floatArrayOf(0f, 90f, 180f, 270f, 360f)
                                for (snap in snapTargets) {
                                    if (abs(newDeg - snap) <= 3.0f) {
                                        newDeg = if (snap == 360f) 0f else snap
                                        break
                                    }
                                }

                                val (baseWx, baseWy) = view.screenToWorld(twoFingerBaseCentroid.x, twoFingerBaseCentroid.y)
                                val (curWx, curWy) = view.screenToWorld(curCentroid.x, curCentroid.y)
                                val newCx = twoFingerBaseImage.centerX + (curWx - baseWx)
                                val newCy = twoFingerBaseImage.centerY + (curWy - baseWy)

                                val updated = twoFingerBaseImage.copy(
                                    centerX = newCx,
                                    centerY = newCy,
                                    width = newW,
                                    height = newH,
                                    rotationDegrees = newDeg
                                )
                                currentLiveImage = updated
                                onUpdateImageLive(updated)
                            }
                            event.changes.forEach { it.consume() }
                        } else if (activePointers.size == 1 && !isTwoFingerActive) {
                            val change = activePointers[0]
                            val curScreen = change.position
                            val (curWx, curWy) = view.screenToWorld(curScreen.x, curScreen.y)

                            when (dragMode) {
                                DragMode.MOVE -> {
                                    change.consume()
                                    hasMoved = true
                                    val dx = curWx - dragStartWorldPoint.x
                                    val dy = curWy - dragStartWorldPoint.y
                                    val updated = dragStartImage.copy(
                                        centerX = dragStartImage.centerX + dx,
                                        centerY = dragStartImage.centerY + dy
                                    )
                                    currentLiveImage = updated
                                    onUpdateImageLive(updated)
                                }
                                DragMode.SCALE -> {
                                    change.consume()
                                    hasMoved = true
                                    val startDist = hypot(dragStartWorldPoint.x - dragStartImage.centerX, dragStartWorldPoint.y - dragStartImage.centerY).coerceAtLeast(10f)
                                    val curDist = hypot(curWx - dragStartImage.centerX, curWy - dragStartImage.centerY).coerceAtLeast(10f)
                                    val scaleFactor = (curDist / startDist).coerceIn(0.05f, 20.0f)
                                    val newW = (dragStartImage.width * scaleFactor).coerceIn(30f, 10000f)
                                    val newH = (dragStartImage.height * scaleFactor).coerceIn(30f, 10000f)
                                    val updated = dragStartImage.copy(width = newW, height = newH)
                                    currentLiveImage = updated
                                    onUpdateImageLive(updated)
                                }
                                DragMode.ROTATE -> {
                                    change.consume()
                                    hasMoved = true
                                    val dx = curWx - dragStartImage.centerX
                                    val dy = curWy - dragStartImage.centerY
                                    val angleRad = atan2(dx.toDouble(), -dy.toDouble())
                                    var deg = Math.toDegrees(angleRad).toFloat()
                                    if (deg < 0f) deg += 360f

                                    // Angle snapping to 0, 45, 90, 135, 180, 225, 270, 315
                                    val snapTargets = floatArrayOf(0f, 45f, 90f, 135f, 180f, 225f, 270f, 315f, 360f)
                                    for (snap in snapTargets) {
                                        if (abs(deg - snap) <= 5.0f) {
                                            deg = if (snap == 360f) 0f else snap
                                            break
                                        }
                                    }

                                    val updated = dragStartImage.copy(rotationDegrees = deg)
                                    currentLiveImage = updated
                                    onUpdateImageLive(updated)
                                }
                                DragMode.CROP_HANDLE -> {
                                    change.consume()
                                    hasMoved = true
                                    val curHw = dragStartImage.width * 0.5f
                                    val curHh = dragStartImage.height * 0.5f
                                    val dx = curWx - dragStartImage.centerX
                                    val dy = curWy - dragStartImage.centerY
                                    val localX = dx * cosR + dy * sinR
                                    val localY = -dx * sinR + dy * cosR

                                    val normU = ((localX + curHw) / dragStartImage.width).coerceIn(0f, 1f)
                                    val normV = ((localY + curHh) / dragStartImage.height).coerceIn(0f, 1f)

                                    when (activeHandleIndex) {
                                        0 -> cropLeft = normU.coerceAtMost(cropRight - 0.05f)
                                        1 -> cropTop = normV.coerceAtMost(cropBottom - 0.05f)
                                        2 -> cropRight = normU.coerceAtLeast(cropLeft + 0.05f)
                                        3 -> cropBottom = normV.coerceAtLeast(cropTop + 0.05f)
                                        4 -> {
                                            cropLeft = normU.coerceAtMost(cropRight - 0.05f)
                                            cropTop = normV.coerceAtMost(cropBottom - 0.05f)
                                        }
                                        5 -> {
                                            cropRight = normU.coerceAtLeast(cropLeft + 0.05f)
                                            cropTop = normV.coerceAtMost(cropBottom - 0.05f)
                                        }
                                        6 -> {
                                            cropRight = normU.coerceAtLeast(cropLeft + 0.05f)
                                            cropBottom = normV.coerceAtLeast(cropTop + 0.05f)
                                        }
                                        7 -> {
                                            cropLeft = normU.coerceAtMost(cropRight - 0.05f)
                                            cropBottom = normV.coerceAtLeast(cropTop + 0.05f)
                                        }
                                    }
                                }
                                DragMode.NONE -> {
                                    // Outside image
                                }
                            }
                        }
                    }

                    // Gesture finished: commit
                    if (isTwoFingerActive) {
                        if (currentLiveImage != dragStartImage) {
                            onUpdateImage(dragStartImage, currentLiveImage)
                        }
                    } else if (dragMode == DragMode.MOVE || dragMode == DragMode.SCALE || dragMode == DragMode.ROTATE) {
                        if (currentLiveImage != dragStartImage) {
                            onUpdateImage(dragStartImage, currentLiveImage)
                        }
                    } else if (dragMode == DragMode.NONE && !isCropMode && !hasMoved) {
                        onDeselect()
                    }
                    dragMode = DragMode.NONE
                }
            }
    ) {
        // 1. Render Selection Box and Handles
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (!isCropMode) {
                // Outer quad outline
                val quadPath = Path().apply {
                    moveTo(sTL.x, sTL.y)
                    lineTo(sTR.x, sTR.y)
                    lineTo(sBR.x, sBR.y)
                    lineTo(sBL.x, sBL.y)
                    close()
                }

                // Subtle blue fill
                drawPath(
                    path = quadPath,
                    color = primaryColor.copy(alpha = 0.06f)
                )

                // Dashed border
                drawPath(
                    path = quadPath,
                    color = primaryColor,
                    style = DrawStroke(
                        width = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                    )
                )

                // Rotation Stem Line
                drawLine(
                    color = primaryColor,
                    start = sTM,
                    end = sRot,
                    strokeWidth = 1.5.dp.toPx()
                )

                // Rotation Handle (Circle with rotation symbol)
                drawCircle(
                    color = Color.White,
                    radius = 8.dp.toPx(),
                    center = sRot
                )
                drawCircle(
                    color = primaryColor,
                    radius = 8.dp.toPx(),
                    center = sRot,
                    style = DrawStroke(width = 2.dp.toPx())
                )
                drawCircle(
                    color = primaryColor,
                    radius = 3.dp.toPx(),
                    center = sRot
                )

                // 4 Corner Scale Handles
                val cornerRadius = 6.dp.toPx()
                for (c in cornerScreenOffsets) {
                    drawCircle(
                        color = Color.White,
                        radius = cornerRadius,
                        center = c
                    )
                    drawCircle(
                        color = primaryColor,
                        radius = cornerRadius,
                        center = c,
                        style = DrawStroke(width = 2.dp.toPx())
                    )
                }
            } else {
                // CROP MODE: Draw Rule-of-Thirds Grid & Corner Grips
                val effW = selectedImage.width
                val effH = selectedImage.height
                val curHw = effW * 0.5f
                val curHh = effH * 0.5f

                // World corners of the active crop sub-rectangle
                val cl = -curHw + effW * cropLeft
                val cr = -curHw + effW * cropRight
                val ct = -curHh + effH * cropTop
                val cb = -curHh + effH * cropBottom

                val cropTLw = PointF(rotateRelX(cl, ct), rotateRelY(cl, ct))
                val cropTRw = PointF(rotateRelX(cr, ct), rotateRelY(cr, ct))
                val cropBRw = PointF(rotateRelX(cr, cb), rotateRelY(cr, cb))
                val cropBLw = PointF(rotateRelX(cl, cb), rotateRelY(cl, cb))

                val (cTLx, cTLy) = canvasView.worldToScreen(cropTLw.x, cropTLw.y)
                val (cTRx, cTRy) = canvasView.worldToScreen(cropTRw.x, cropTRw.y)
                val (cBRx, cBRy) = canvasView.worldToScreen(cropBRw.x, cropBRw.y)
                val (cBLx, cBLy) = canvasView.worldToScreen(cropBLw.x, cropBLw.y)

                val cTL = Offset(cTLx, cTLy)
                val cTR = Offset(cTRx, cTRy)
                val cBR = Offset(cBRx, cBRy)
                val cBL = Offset(cBLx, cBLy)

                val cropPath = Path().apply {
                    moveTo(cTL.x, cTL.y)
                    lineTo(cTR.x, cTR.y)
                    lineTo(cBR.x, cBR.y)
                    lineTo(cBL.x, cBL.y)
                    close()
                }

                // Solid white crop border with shadow
                drawPath(
                    path = cropPath,
                    color = Color.White,
                    style = DrawStroke(width = 2.dp.toPx())
                )

                // Rule-of-thirds lines inside crop box
                for (f in listOf(1f / 3f, 2f / 3f)) {
                    val pTop = cTL + (cTR - cTL) * f
                    val pBottom = cBL + (cBR - cBL) * f
                    drawLine(Color.White.copy(alpha = 0.45f), pTop, pBottom, strokeWidth = 1.dp.toPx())

                    val pLeft = cTL + (cBL - cTL) * f
                    val pRight = cTR + (cBR - cTR) * f
                    drawLine(Color.White.copy(alpha = 0.45f), pLeft, pRight, strokeWidth = 1.dp.toPx())
                }

                // Bold L-bracket grips on 4 corners
                val bracketLen = 16.dp.toPx()
                val bracketStroke = 3.5.dp.toPx()
                fun drawBracket(corner: Offset, d1: Offset, d2: Offset) {
                    val dir1 = d1 / max(1f, d1.getDistance())
                    val dir2 = d2 / max(1f, d2.getDistance())
                    drawLine(Color.White, corner, corner + dir1 * bracketLen, strokeWidth = bracketStroke)
                    drawLine(Color.White, corner, corner + dir2 * bracketLen, strokeWidth = bracketStroke)
                }
                drawBracket(cTL, cTR - cTL, cBL - cTL)
                drawBracket(cTR, cTL - cTR, cBR - cTR)
                drawBracket(cBR, cBL - cBR, cTR - cBR)
                drawBracket(cBL, cBR - cBL, cTL - cBL)
            }
        }

        // 2. Floating Action Pill
        val screenConfig = LocalConfiguration.current
        val pillHeightDp = 40.dp
        val pillHeightPx = with(density) { pillHeightDp.toPx() }

        // Position pill centered above the image, or below if close to top
        val pillCenterScreenX = (minBoxX + maxBoxX) * 0.5f
        val pillScreenY = if (minBoxY > pillHeightPx + 20f) {
            minBoxY - pillHeightPx - 12f
        } else {
            maxBoxY + 14f
        }

        val pillXDp = with(density) { (pillCenterScreenX - 90f).toDp() }
        val pillYDp = with(density) { pillScreenY.toDp() }
        val minXDp = 12.dp
        val maxXDp = (screenConfig.screenWidthDp - 220).dp
        val clampedXDp = pillXDp.coerceIn(minXDp, maxOf(minXDp, maxXDp))
        val clampedYDp = pillYDp.coerceIn(12.dp, (screenConfig.screenHeightDp - 60).dp)

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp),
            tonalElevation = 6.dp,
            shadowElevation = 6.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
            modifier = Modifier
                .offset(x = clampedXDp, y = clampedYDp)
                .height(pillHeightDp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                if (!isCropMode) {
                    // Crop Button
                    IconButton(
                        onClick = { isCropMode = true },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Crop,
                            contentDescription = "Crop Image",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(17.dp)
                        )
                    }

                    // Duplicate Button
                    IconButton(
                        onClick = { onDuplicateImage(selectedImage) },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Duplicate Image",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Delete Button
                    IconButton(
                        onClick = { onDeleteImage(selectedImage) },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Image",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(17.dp)
                        )
                    }

                    // Deselect Button
                    IconButton(
                        onClick = onDeselect,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Deselect",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.size(17.dp)
                        )
                    }
                } else {
                    // CROP MODE TOOLBAR
                    // Cancel Crop
                    IconButton(
                        onClick = {
                            // Revert crop
                            cropLeft = selectedImage.cropLeft
                            cropTop = selectedImage.cropTop
                            cropRight = selectedImage.cropRight
                            cropBottom = selectedImage.cropBottom
                            isCropMode = false
                        },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel Crop",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            modifier = Modifier.size(17.dp)
                        )
                    }

                    // Reset Crop Button
                    TextButton(
                        onClick = {
                            val spanU = max(0.001f, selectedImage.cropRight - selectedImage.cropLeft)
                            val spanV = max(0.001f, selectedImage.cropBottom - selectedImage.cropTop)
                            if (spanU < 0.999f || spanV < 0.999f) {
                                val fullW = selectedImage.width / spanU
                                val fullH = selectedImage.height / spanV
                                val localShiftX = (((selectedImage.cropLeft + selectedImage.cropRight) * 0.5f) - 0.5f) * fullW
                                val localShiftY = (((selectedImage.cropTop + selectedImage.cropBottom) * 0.5f) - 0.5f) * fullH
                                val radD = Math.toRadians(selectedImage.rotationDegrees.toDouble())
                                val cosD = cos(radD).toFloat()
                                val sinD = sin(radD).toFloat()
                                val origCenterX = selectedImage.centerX - (localShiftX * cosD - localShiftY * sinD)
                                val origCenterY = selectedImage.centerY - (localShiftX * sinD + localShiftY * cosD)

                                val resetImg = selectedImage.copy(
                                    centerX = origCenterX,
                                    centerY = origCenterY,
                                    width = fullW,
                                    height = fullH,
                                    cropLeft = 0f,
                                    cropTop = 0f,
                                    cropRight = 1f,
                                    cropBottom = 1f
                                )
                                onUpdateImage(selectedImage, resetImg)
                            }
                            cropLeft = 0f
                            cropTop = 0f
                            cropRight = 1f
                            cropBottom = 1f
                        },
                        contentPadding = PaddingValues(horizontal = 6.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text(
                            text = "Reset",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Done Button
                    Button(
                        onClick = {
                            val prevSpanU = max(0.001f, selectedImage.cropRight - selectedImage.cropLeft)
                            val prevSpanV = max(0.001f, selectedImage.cropBottom - selectedImage.cropTop)

                            val finalCropLeft = selectedImage.cropLeft + cropLeft * prevSpanU
                            val finalCropRight = selectedImage.cropLeft + cropRight * prevSpanU
                            val finalCropTop = selectedImage.cropTop + cropTop * prevSpanV
                            val finalCropBottom = selectedImage.cropTop + cropBottom * prevSpanV

                            val cropSpanU = max(0.05f, cropRight - cropLeft)
                            val cropSpanV = max(0.05f, cropBottom - cropTop)
                            val newW = selectedImage.width * cropSpanU
                            val newH = selectedImage.height * cropSpanV

                            val curHw = selectedImage.width * 0.5f
                            val curHh = selectedImage.height * 0.5f
                            val localCropCenterX = -curHw + selectedImage.width * ((cropLeft + cropRight) * 0.5f)
                            val localCropCenterY = -curHh + selectedImage.height * ((cropTop + cropBottom) * 0.5f)

                            val radD = Math.toRadians(selectedImage.rotationDegrees.toDouble())
                            val cosD = cos(radD).toFloat()
                            val sinD = sin(radD).toFloat()

                            val newCenterX = selectedImage.centerX + localCropCenterX * cosD - localCropCenterY * sinD
                            val newCenterY = selectedImage.centerY + localCropCenterX * sinD + localCropCenterY * cosD

                            val croppedImage = selectedImage.copy(
                                centerX = newCenterX,
                                centerY = newCenterY,
                                width = newW,
                                height = newH,
                                cropLeft = finalCropLeft.coerceIn(0f, 1f),
                                cropTop = finalCropTop.coerceIn(0f, 1f),
                                cropRight = finalCropRight.coerceIn(0f, 1f),
                                cropBottom = finalCropBottom.coerceIn(0f, 1f)
                            )
                            onUpdateImage(selectedImage, croppedImage)
                            isCropMode = false
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Done Crop",
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Done",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp)
                        )
                    }
                }
            }
        }
    }
}
