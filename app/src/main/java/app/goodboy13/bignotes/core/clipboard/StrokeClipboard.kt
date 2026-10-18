package app.goodboy13.bignotes.core.clipboard

import app.goodboy13.bignotes.core.model.Stroke

/**
 * Global application-level clipboard storing strokes for Cut, Copy, and Paste operations.
 * Persists across page switches and allows pasting into any canvas page.
 */
object StrokeClipboard {
    private var clipboardStrokes: List<Stroke> = emptyList()
    var isCutOperation: Boolean = false
        private set

    val hasContent: Boolean
        get() = clipboardStrokes.isNotEmpty()

    val count: Int
        get() = clipboardStrokes.size

    fun copy(strokes: List<Stroke>, isCut: Boolean = false) {
        if (strokes.isEmpty()) return
        clipboardStrokes = strokes.map { it.copy() }
        isCutOperation = isCut
    }

    fun getStrokes(): List<Stroke> = clipboardStrokes

    fun onPasted() {
        if (isCutOperation) {
            clear()
        }
    }

    fun clear() {
        clipboardStrokes = emptyList()
        isCutOperation = false
    }
}

