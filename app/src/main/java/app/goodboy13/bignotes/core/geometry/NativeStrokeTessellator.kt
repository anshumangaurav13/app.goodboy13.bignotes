package app.goodboy13.bignotes.core.geometry

import android.util.Log

/**
 * High-performance native Rust computational geometry & tessellation bridge.
 * Executes Catmull-Rom spline interpolation, stylus tilt/velocity metadata propagation,
 * and tapered capsule segment SDF mesh packing in compiled native Rust.
 */
object NativeStrokeTessellator {
    private const val TAG = "NativeTessellator"
    const val FLOATS_PER_INPUT_POINT = 6
    val isAvailable: Boolean

    init {
        var loaded = false
        try {
            System.loadLibrary("bignotes_core")
            loaded = true
            Log.i(TAG, "Successfully loaded libbignotes_core.so")
        } catch (t: Throwable) {
            // Graceful fallback for host JVM tests or unsupported targets
            loaded = false
        }
        isAvailable = loaded
    }

    @JvmStatic
    external fun nativeTessellateStroke(
        pointsArray: FloatArray,
        pointCount: Int,
        baseWidth: Float,
        colorRgb: Long,
        isInk: Boolean,
        isHighlighter: Boolean,
        quadMarginRatio: Float
    ): FloatArray?
}
