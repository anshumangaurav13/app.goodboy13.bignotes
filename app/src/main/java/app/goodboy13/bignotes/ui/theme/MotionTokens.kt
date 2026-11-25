package app.goodboy13.bignotes.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext

/**
 * Centralized UI motion tokens grounded in Disney's 12 Animation Principles:
 * - Natural deceleration (EaseOutNatural) for entering panels & popovers
 * - Snappy detents (EaseSnappy) for toggles & selections
 * - Expressive spring physics for tactile drag/release & radial bloom
 * - Reduced-motion respect via Android's global ANIMATOR_DURATION_SCALE
 */
object BigNotesMotion {
    // Timing budgets (kept strictly <= 300ms for navigational/tool UI)
    const val MICRO_MS = 140
    const val STANDARD_MS = 220
    const val CONTAINER_MS = 250
    const val CALM_PULSE_MS = 650

    // Production easing curves
    val EaseOutNatural = CubicBezierEasing(0.16f, 1.0f, 0.30f, 1.0f)
    val EaseSnappy = CubicBezierEasing(0.40f, 0.0f, 0.20f, 1.0f)
    val EaseInNatural = CubicBezierEasing(0.70f, 0.0f, 0.84f, 0.0f)
    val EaseOvershoot = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1.0f)

    fun <T> snappyTween(durationMs: Int = MICRO_MS, reducedMotion: Boolean = false): FiniteAnimationSpec<T> =
        if (reducedMotion) snap() else tween(durationMillis = durationMs, easing = EaseSnappy)

    fun <T> enterTween(durationMs: Int = STANDARD_MS, reducedMotion: Boolean = false): FiniteAnimationSpec<T> =
        if (reducedMotion) snap() else tween(durationMillis = durationMs, easing = EaseOutNatural)

    fun <T> exitTween(durationMs: Int = MICRO_MS, reducedMotion: Boolean = false): FiniteAnimationSpec<T> =
        if (reducedMotion) snap() else tween(durationMillis = durationMs, easing = EaseInNatural)

    fun <T> tactileSpring(
        dampingRatio: Float = 0.76f,
        stiffness: Float = Spring.StiffnessMediumLow,
        reducedMotion: Boolean = false
    ): FiniteAnimationSpec<T> =
        if (reducedMotion) snap() else spring(dampingRatio = dampingRatio, stiffness = stiffness)
}

/**
 * Reads the system animator duration scale. When 0f, animations collapse to instant/opacity-safe transitions.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        try {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1.0f
            ) == 0.0f
        } catch (_: Exception) {
            false
        }
    }
}

/**
 * Applies tactile press compression (Squash & Stretch) and optional selection overshoot.
 */
@Composable
fun Modifier.tactileScale(
    isPressed: Boolean,
    isSelected: Boolean = false,
    pressedScale: Float = 0.90f,
    selectedScale: Float = 1.06f,
    reducedMotion: Boolean = rememberReducedMotion()
): Modifier {
    val targetScale = when {
        isPressed -> pressedScale
        isSelected -> selectedScale
        else -> 1.0f
    }
    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = if (reducedMotion) snap() else spring(
            dampingRatio = 0.58f,
            stiffness = Spring.StiffnessMedium
        ),
        label = "tactileScale"
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
