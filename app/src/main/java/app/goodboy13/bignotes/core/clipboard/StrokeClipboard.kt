package app.goodboy13.bignotes.core.clipboard

import app.goodboy13.bignotes.core.model.Stroke

/**
 * Explicit state model for application stroke clipboard.
 */
sealed class ClipboardContent {
    object Empty : ClipboardContent()
    data class Copied(val strokes: List<Stroke>) : ClipboardContent()
    data class Cut(val strokes: List<Stroke>) : ClipboardContent()
}

/**
 * Global application-level clipboard storing strokes for Cut, Copy, and Paste operations.
 * Governed by an explicit [ClipboardContent] state machine.
 */
object StrokeClipboard {
    private var state: ClipboardContent = ClipboardContent.Empty

    val currentState: ClipboardContent
        get() = state

    val hasContent: Boolean
        get() = state !is ClipboardContent.Empty

    val isCutOperation: Boolean
        get() = state is ClipboardContent.Cut

    val count: Int
        get() = when (val s = state) {
            is ClipboardContent.Empty -> 0
            is ClipboardContent.Copied -> s.strokes.size
            is ClipboardContent.Cut -> s.strokes.size
        }

    fun copy(strokes: List<Stroke>, isCut: Boolean = false) {
        if (strokes.isEmpty()) return
        val cloned = strokes.map { it.copy() }
        state = if (isCut) ClipboardContent.Cut(cloned) else ClipboardContent.Copied(cloned)
    }

    fun getStrokes(): List<Stroke> = when (val s = state) {
        is ClipboardContent.Empty -> emptyList()
        is ClipboardContent.Copied -> s.strokes
        is ClipboardContent.Cut -> s.strokes
    }

    fun onPasted() {
        if (state is ClipboardContent.Cut) {
            clear()
        }
    }

    fun clear() {
        state = ClipboardContent.Empty
    }
}
