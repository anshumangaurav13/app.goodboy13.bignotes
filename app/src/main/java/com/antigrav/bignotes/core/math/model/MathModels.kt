package com.antigrav.bignotes.core.math.model

import android.graphics.RectF
import com.antigrav.bignotes.core.model.Stroke

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
