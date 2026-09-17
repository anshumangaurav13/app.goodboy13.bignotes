package com.antigrav.bignotes.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object CustomIcons {
    /**
     * Classic Fountain Pen Nib icon with distinctive shoulder flair,
     * central breather hole, and fine ink point.
     */
    val FountainPen: ImageVector by lazy {
        ImageVector.Builder(
            name = "FountainPen",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.EvenOdd
            ) {
                // Nib collar & flared shoulders
                moveTo(7.5f, 3.0f)
                lineTo(16.5f, 3.0f)
                lineTo(17.5f, 8.5f)
                curveTo(17.5f, 10.5f, 16.0f, 12.5f, 14.5f, 14.0f)
                lineTo(12.3f, 21.0f)
                lineTo(11.7f, 21.0f)
                lineTo(9.5f, 14.0f)
                curveTo(8.0f, 12.5f, 6.5f, 10.5f, 6.5f, 8.5f)
                close()
                // Central breather hole
                moveTo(12.0f, 13.0f)
                curveTo(12.55f, 13.0f, 13.0f, 12.55f, 13.0f, 12.0f)
                curveTo(13.0f, 11.45f, 12.55f, 11.0f, 12.0f, 11.0f)
                curveTo(11.45f, 11.0f, 11.0f, 11.45f, 11.0f, 12.0f)
                curveTo(11.0f, 12.55f, 11.45f, 13.0f, 12.0f, 13.0f)
                close()
            }
        }.build()
    }

    /**
     * Distinctive Wooden Pencil icon showing faceted shaft, ferrule band, and sharpened lead tip.
     */
    val Pencil: ImageVector by lazy {
        ImageVector.Builder(
            name = "Pencil",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(3.0f, 17.25f)
                lineTo(3.0f, 21.0f)
                lineTo(6.75f, 21.0f)
                lineTo(17.81f, 9.94f)
                lineTo(14.06f, 6.19f)
                lineTo(3.0f, 17.25f)
                close()
                moveTo(20.71f, 7.04f)
                curveTo(21.1f, 6.65f, 21.1f, 6.02f, 20.71f, 5.63f)
                lineTo(18.37f, 3.29f)
                curveTo(17.98f, 2.9f, 17.35f, 2.9f, 16.96f, 3.29f)
                lineTo(15.13f, 5.12f)
                lineTo(18.88f, 8.87f)
                lineTo(20.71f, 7.04f)
                close()
            }
        }.build()
    }

    /**
     * Chisel-tip Highlighter icon showing broad angled wedge nib and marker barrel.
     */
    val Highlighter: ImageVector by lazy {
        ImageVector.Builder(
            name = "Highlighter",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                // Chisel angled nib
                moveTo(3.0f, 21.0f)
                lineTo(8.0f, 21.0f)
                lineTo(9.5f, 17.5f)
                lineTo(4.5f, 17.5f)
                close()
                // Marker collar & barrel
                moveTo(5.5f, 16.5f)
                lineTo(10.5f, 16.5f)
                lineTo(19.2f, 7.8f)
                curveTo(19.6f, 7.4f, 19.6f, 6.8f, 19.2f, 6.4f)
                lineTo(16.6f, 3.8f)
                curveTo(16.2f, 3.4f, 15.6f, 3.4f, 15.2f, 3.8f)
                lineTo(5.5f, 13.5f)
                close()
            }
        }.build()
    }
}
