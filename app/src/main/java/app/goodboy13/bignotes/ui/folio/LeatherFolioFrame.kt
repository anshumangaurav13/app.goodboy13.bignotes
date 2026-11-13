package app.goodboy13.bignotes.ui.folio

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.goodboy13.bignotes.ui.theme.NotebookThemePreset
import app.goodboy13.bignotes.ui.theme.getLeatherThemeStyle

/**
 * Tactile Leather Notebook Folio Frame overlay.
 * Renders decorative physical leather details (saddle stitching, brass rivets,
 * corner guards, elastic spine cord, ribbon bookmark) framing the active note area.
 *
 * Designed to have ZERO touch consumption: all stylus & gesture events pass
 * straight through to the underlying SketchCanvasView with zero latency.
 */
@Composable
fun LeatherFolioFrame(
    preset: NotebookThemePreset,
    isDark: Boolean,
    sidebarOffsetPx: Float = 0f,
    statusBarHeightDp: Dp = 0.dp,
    modifier: Modifier = Modifier
) {
    if (preset == NotebookThemePreset.STUDIO_CLEAN) {
        return // Clean minimal canvas mode has no leather folio
    }

    val style = getLeatherThemeStyle(preset, isDark)

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        val left = sidebarOffsetPx
        val top = 0f
        val right = w
        val bottom = h

        val noteWidth = right - left
        val noteHeight = bottom - top

        if (noteWidth <= 40f || noteHeight <= 40f) return@Canvas

        when (preset) {
            NotebookThemePreset.CLASSIC_SADDLE -> {
                drawClassicSaddleFolio(
                    left = left,
                    top = top,
                    right = right,
                    bottom = bottom,
                    style = style,
                    isDark = isDark
                )
            }
            NotebookThemePreset.TRAVELERS_ESPRESSO -> {
                drawTravelersEspressoFolio(
                    left = left,
                    top = top,
                    right = right,
                    bottom = bottom,
                    style = style,
                    isDark = isDark
                )
            }
            NotebookThemePreset.VINTAGE_OXFORD -> {
                drawVintageOxfordFolio(
                    left = left,
                    top = top,
                    right = right,
                    bottom = bottom,
                    style = style,
                    isDark = isDark
                )
            }
            NotebookThemePreset.STUDIO_CLEAN -> Unit
        }
    }
}

/**
 * Classic Saddle Folio:
 * - Cognac leather edge rim with subtle gradient
 * - Linen saddle stitching (dashed line with debossed shadow)
 * - Silk ribbon bookmark hanging from top-right
 */
private fun DrawScope.drawClassicSaddleFolio(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    style: app.goodboy13.bignotes.ui.theme.LeatherThemeStyle,
    isDark: Boolean
) {
    val bezelThickness = 10f
    val stitchOffset = 18f

    // 1. Outer edge leather bezel (subtle leather rim around note)
    // Left edge (spine fold)
    drawRect(
        brush = Brush.horizontalGradient(
            colors = listOf(style.leatherBorderColor, style.leatherColor.copy(alpha = 0.45f), Color.Transparent),
            startX = left,
            endX = left + bezelThickness * 2f
        ),
        topLeft = Offset(left, top),
        size = Size(bezelThickness * 2f, bottom - top)
    )

    // Top edge
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(style.leatherBorderColor.copy(alpha = 0.6f), Color.Transparent),
            startY = top,
            endY = top + bezelThickness
        ),
        topLeft = Offset(left, top),
        size = Size(right - left, bezelThickness)
    )

    // Right edge
    drawRect(
        brush = Brush.horizontalGradient(
            colors = listOf(Color.Transparent, style.leatherColor.copy(alpha = 0.35f), style.leatherBorderColor.copy(alpha = 0.6f)),
            startX = right - bezelThickness * 1.5f,
            endX = right
        ),
        topLeft = Offset(right - bezelThickness * 1.5f, top),
        size = Size(bezelThickness * 1.5f, bottom - top)
    )

    // Bottom edge
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color.Transparent, style.leatherBorderColor.copy(alpha = 0.6f)),
            startY = bottom - bezelThickness,
            endY = bottom
        ),
        topLeft = Offset(left, bottom - bezelThickness),
        size = Size(right - left, bezelThickness)
    )

    // 2. Saddle Stitching (cream linen thread, dashed pattern)
    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f)
    val stitchShadowColor = if (isDark) Color(0x66000000) else Color(0x403A1F0D)

    val stitchPath = Path().apply {
        moveTo(left + stitchOffset, top + stitchOffset)
        lineTo(right - stitchOffset, top + stitchOffset)
        lineTo(right - stitchOffset, bottom - stitchOffset)
        lineTo(left + stitchOffset, bottom - stitchOffset)
        close()
    }

    // Stitch debossed shadow
    drawPath(
        path = stitchPath,
        color = stitchShadowColor,
        style = Stroke(width = 2.5f, pathEffect = dashEffect)
    )

    // Stitch thread stroke
    drawPath(
        path = stitchPath,
        color = style.stitchColor.copy(alpha = 0.85f),
        style = Stroke(width = 1.8f, pathEffect = dashEffect)
    )

    // 3. Silk Ribbon Bookmark (swallowtail hanging from top right)
    style.ribbonColor?.let { ribbonColor ->
        val ribbonW = 24f
        val ribbonH = 46f
        val ribbonX = (right - 100f).coerceAtLeast(left + 60f)
        val notchDepth = 8f

        val ribbonPath = Path().apply {
            moveTo(ribbonX, top)
            lineTo(ribbonX + ribbonW, top)
            lineTo(ribbonX + ribbonW, top + ribbonH)
            lineTo(ribbonX + ribbonW / 2f, top + ribbonH - notchDepth)
            lineTo(ribbonX, top + ribbonH)
            close()
        }

        // Ribbon shadow
        drawPath(
            path = ribbonPath,
            color = Color(0x33000000),
            style = Fill
        )

        // Ribbon body
        drawPath(
            path = ribbonPath,
            brush = Brush.verticalGradient(
                colors = listOf(ribbonColor, ribbonColor.copy(alpha = 0.85f)),
                startY = top,
                endY = top + ribbonH
            ),
            style = Fill
        )

        // Ribbon gold border trim
        drawPath(
            path = ribbonPath,
            color = style.metalAccentColor.copy(alpha = 0.70f),
            style = Stroke(width = 1.2f)
        )
    }
}

/**
 * Traveler's Espresso Journal:
 * - Dark oiled leather trim
 * - Elastic spine retention cord on the left margin
 * - Weathered brass rivets at cord anchors
 */
private fun DrawScope.drawTravelersEspressoFolio(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    style: app.goodboy13.bignotes.ui.theme.LeatherThemeStyle,
    isDark: Boolean
) {
    // 1. Dark oiled leather edge gradients
    val spineRimW = 16f

    drawRect(
        brush = Brush.horizontalGradient(
            colors = listOf(style.leatherBorderColor, style.leatherColor.copy(alpha = 0.5f), Color.Transparent),
            startX = left,
            endX = left + spineRimW
        ),
        topLeft = Offset(left, top),
        size = Size(spineRimW, bottom - top)
    )

    // Outer subtle border
    drawRect(
        color = style.leatherBorderColor.copy(alpha = 0.4f),
        topLeft = Offset(left, top),
        size = Size(right - left, bottom - top),
        style = Stroke(width = 3f)
    )

    // 2. Elastic Spine Cord (vertical cord holding the pages along the spine)
    val cordX = left + 14f
    val cordRadius = 3f

    // Cord shadow
    drawLine(
        color = Color(0x44000000),
        start = Offset(cordX + 1.5f, top),
        end = Offset(cordX + 1.5f, bottom),
        strokeWidth = 4.5f,
        cap = StrokeCap.Round
    )

    // Elastic cord body
    drawLine(
        brush = Brush.horizontalGradient(
            colors = listOf(Color(0xFF1E1B18), Color(0xFF38322B), Color(0xFF1E1B18)),
            startX = cordX - cordRadius,
            endX = cordX + cordRadius
        ),
        start = Offset(cordX, top),
        end = Offset(cordX, bottom),
        strokeWidth = 3.5f,
        cap = StrokeCap.Round
    )

    // 3. Antique Brass Rivets / Eyelets at top and bottom of cord
    val rivetAnchors = listOf(
        Offset(cordX, top + 24f),
        Offset(cordX, bottom - 24f)
    )

    for (center in rivetAnchors) {
        // Outer brass ring
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(style.metalAccentColor, Color(0xFF5A4429)),
                center = center,
                radius = 7f
            ),
            radius = 7f,
            center = center
        )
        // Brass highlight rim
        drawCircle(
            color = Color(0xFFE2C48E),
            radius = 6f,
            center = center,
            style = Stroke(width = 1f)
        )
        // Center eyelet hole
        drawCircle(
            color = Color(0xFF141210),
            radius = 3f,
            center = center
        )
    }

    // Top-right and bottom-right corner rivets
    val cornerRivets = listOf(
        Offset(right - 20f, top + 20f),
        Offset(right - 20f, bottom - 20f)
    )
    for (center in cornerRivets) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(style.metalAccentColor, Color(0xFF4A3822)),
                center = center,
                radius = 5.5f
            ),
            radius = 5.5f,
            center = center
        )
        drawCircle(
            color = Color(0xFF141210),
            radius = 2f,
            center = center
        )
    }
}

/**
 * Vintage Oxford Archivist:
 * - Cordovan/oxblood leather frame
 * - Blind debossed double pinstripe framing rule
 * - Antique brass ornate corner guards in the 4 corners
 */
private fun DrawScope.drawVintageOxfordFolio(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    style: app.goodboy13.bignotes.ui.theme.LeatherThemeStyle,
    isDark: Boolean
) {
    val margin = 14f
    val innerMargin = 19f

    // 1. Blind Debossed Double Pinstripe Framing
    val shadowColor = if (isDark) Color(0x88000000) else Color(0x351F0A14)
    val highlightColor = if (isDark) Color(0x22FFFFFF) else Color(0x2BFFAABB)

    // Outer debossed groove
    drawRect(
        color = shadowColor,
        topLeft = Offset(left + margin + 1f, top + margin + 1f),
        size = Size(right - left - (margin * 2f), bottom - top - (margin * 2f)),
        style = Stroke(width = 1.5f)
    )
    drawRect(
        color = highlightColor,
        topLeft = Offset(left + margin, top + margin),
        size = Size(right - left - (margin * 2f), bottom - top - (margin * 2f)),
        style = Stroke(width = 1.2f)
    )

    // Inner fine rule
    drawRect(
        color = style.metalAccentColor.copy(alpha = 0.45f),
        topLeft = Offset(left + innerMargin, top + innerMargin),
        size = Size(right - left - (innerMargin * 2f), bottom - top - (innerMargin * 2f)),
        style = Stroke(width = 0.8f)
    )

    // 2. Antique Brass Corner Guards (Ornate L-shaped brackets in 4 corners)
    val guardArm = 38f
    val guardThick = 6f
    val brassBrush = Brush.linearGradient(
        colors = listOf(Color(0xFFE5BE64), style.metalAccentColor, Color(0xFF6B5120))
    )

    // Top-Left Guard
    val tlX = left + margin
    val tlY = top + margin
    val tlPath = Path().apply {
        moveTo(tlX, tlY)
        lineTo(tlX + guardArm, tlY)
        lineTo(tlX + guardArm - guardThick, tlY + guardThick)
        lineTo(tlX + guardThick, tlY + guardThick)
        lineTo(tlX + guardThick, tlY + guardArm - guardThick)
        lineTo(tlX, tlY + guardArm)
        close()
    }
    drawPath(tlPath, brassBrush, style = Fill)
    drawCircle(Color(0xFF2E200C), radius = 2f, center = Offset(tlX + 10f, tlY + 10f))

    // Top-Right Guard
    val trX = right - margin
    val trY = top + margin
    val trPath = Path().apply {
        moveTo(trX, trY)
        lineTo(trX - guardArm, trY)
        lineTo(trX - guardArm + guardThick, trY + guardThick)
        lineTo(trX - guardThick, trY + guardThick)
        lineTo(trX - guardThick, trY + guardArm - guardThick)
        lineTo(trX, trY + guardArm)
        close()
    }
    drawPath(trPath, brassBrush, style = Fill)
    drawCircle(Color(0xFF2E200C), radius = 2f, center = Offset(trX - 10f, trY + 10f))

    // Bottom-Left Guard
    val blX = left + margin
    val blY = bottom - margin
    val blPath = Path().apply {
        moveTo(blX, blY)
        lineTo(blX + guardArm, blY)
        lineTo(blX + guardArm - guardThick, blY - guardThick)
        lineTo(blX + guardThick, blY - guardThick)
        lineTo(blX + guardThick, blY - guardArm + guardThick)
        lineTo(blX, blY - guardArm)
        close()
    }
    drawPath(blPath, brassBrush, style = Fill)
    drawCircle(Color(0xFF2E200C), radius = 2f, center = Offset(blX + 10f, blY - 10f))

    // Bottom-Right Guard
    val brX = right - margin
    val brY = bottom - margin
    val brPath = Path().apply {
        moveTo(brX, brY)
        lineTo(brX - guardArm, brY)
        lineTo(brX - guardArm + guardThick, brY - guardThick)
        lineTo(brX - guardThick, brY - guardThick)
        lineTo(brX - guardThick, brY - guardArm + guardThick)
        lineTo(brX, brY - guardArm)
        close()
    }
    drawPath(brPath, brassBrush, style = Fill)
    drawCircle(Color(0xFF2E200C), radius = 2f, center = Offset(brX - 10f, brY - 10f))
}
