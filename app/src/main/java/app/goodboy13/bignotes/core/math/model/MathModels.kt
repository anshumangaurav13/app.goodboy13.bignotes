package app.goodboy13.bignotes.core.math.model

import android.graphics.RectF
import app.goodboy13.bignotes.core.model.Stroke

/**
 * Result of evaluating a mathematical expression.
 */
sealed class MathResult {
    data class Success(
        val expression: String,
        val value: Double,
        val formattedResult: String
    ) : MathResult()

    data class Error(
        val expression: String,
        val message: String
    ) : MathResult()
}

/**
 * UI state for the floating math result overlay.
 */
data class MathOverlayState(
    val expression: String,
    val result: MathResult,
    val worldBounds: RectF,
    val sourceStrokes: List<Stroke>
)
