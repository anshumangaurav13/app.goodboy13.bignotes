package com.antigrav.bignotes.core.history

import com.antigrav.bignotes.core.model.Stroke
import java.util.ArrayDeque

sealed class CanvasAction {
    data class AddStroke(val stroke: Stroke) : CanvasAction()
    data class InsertStrokes(val strokes: List<Stroke>) : CanvasAction()
    data class RemoveStrokes(val strokes: List<Stroke>) : CanvasAction()
    data class ReplaceStrokes(val removed: List<Stroke>, val added: List<Stroke>) : CanvasAction()
    data class ClearCanvas(val previousStrokes: List<Stroke>) : CanvasAction()

    data class AddImage(val image: com.antigrav.bignotes.core.model.CanvasImage) : CanvasAction()
    data class RemoveImage(val image: com.antigrav.bignotes.core.model.CanvasImage) : CanvasAction()
    data class UpdateImage(val previous: com.antigrav.bignotes.core.model.CanvasImage, val current: com.antigrav.bignotes.core.model.CanvasImage) : CanvasAction()
}


class UndoRedoManager(private val maxHistory: Int = 100) {

    private val undoStack = ArrayDeque<CanvasAction>()
    private val redoStack = ArrayDeque<CanvasAction>()

    var onStateChanged: ((canUndo: Boolean, canRedo: Boolean) -> Unit)? = null

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun pushAction(action: CanvasAction) {
        if (undoStack.size >= maxHistory) {
            undoStack.removeFirst()
        }
        undoStack.addLast(action)
        redoStack.clear()
        notifyState()
    }

    /**
     * Applies undo and returns the inverse action to be executed on the canvas state.
     */
    fun undo(): CanvasAction? {
        if (undoStack.isEmpty()) return null
        val action = undoStack.removeLast()
        redoStack.addLast(action)
        notifyState()
        return action
    }

    /**
     * Applies redo and returns the action to be executed on the canvas state.
     */
    fun redo(): CanvasAction? {
        if (redoStack.isEmpty()) return null
        val action = redoStack.removeLast()
        undoStack.addLast(action)
        notifyState()
        return action
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
        notifyState()
    }

    private fun notifyState() {
        onStateChanged?.invoke(canUndo, canRedo)
    }
}

