package app.goodboy13.bignotes.core.model

import java.util.UUID

/**
 * Represents an inserted image on the infinite canvas.
 * Position and dimensions are in canvas world coordinates.
 * Normalized UV crop coordinates [0..1] allow non-destructive cropping.
 */
data class CanvasImage(
    val id: String = UUID.randomUUID().toString(),
    val localFileName: String,
    val centerX: Float,
    val centerY: Float,
    val width: Float,
    val height: Float,
    val rotationDegrees: Float = 0f,
    val cropLeft: Float = 0f,
    val cropTop: Float = 0f,
    val cropRight: Float = 1f,
    val cropBottom: Float = 1f,
    val zIndex: Long = System.currentTimeMillis()
) {
    fun containsPoint(wx: Float, wy: Float, padding: Float = 0f): Boolean {
        val halfW = (width * 0.5f) + padding
        val halfH = (height * 0.5f) + padding
        val dx = wx - centerX
        val dy = wy - centerY
        if (rotationDegrees == 0f) {
            return kotlin.math.abs(dx) <= halfW && kotlin.math.abs(dy) <= halfH
        }
        val rad = Math.toRadians(-rotationDegrees.toDouble())
        val cosR = kotlin.math.cos(rad).toFloat()
        val sinR = kotlin.math.sin(rad).toFloat()
        val localX = dx * cosR - dy * sinR
        val localY = dx * sinR + dy * cosR
        return kotlin.math.abs(localX) <= halfW && kotlin.math.abs(localY) <= halfH
    }
}
