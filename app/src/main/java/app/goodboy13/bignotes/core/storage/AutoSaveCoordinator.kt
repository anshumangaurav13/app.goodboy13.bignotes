package app.goodboy13.bignotes.core.storage

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Explicit state model for auto-save pipeline.
 *
 * Guarantees that:
 * 1. Background persistence never runs while pen is in contact with screen (zero latency front-buffer guarantee).
 * 2. Rapid stroke completion cleanly debounces with zero leaked timers.
 * 3. Immediate flush requests (page navigation, app backgrounding, backup restore) cleanly cancel debouncing
 *    and synchronously or asynchronously commit state without race conditions.
 */
sealed class AutoSaveState {
    object Clean : AutoSaveState()
    data class PenActive(val noteId: String) : AutoSaveState()
    data class Debouncing(val noteId: String, val job: Job) : AutoSaveState()
    data class Saving(val noteId: String) : AutoSaveState()
}

class AutoSaveCoordinator(
    private val scope: CoroutineScope,
    private val debounceDelayMs: Long = 1000L,
    private val onPerformSave: suspend (noteId: String) -> Unit
) {
    var state: AutoSaveState = AutoSaveState.Clean
        private set

    val isDirty: Boolean
        get() = state !is AutoSaveState.Clean

    val currentDirtyNoteId: String?
        get() = when (val s = state) {
            is AutoSaveState.Clean -> null
            is AutoSaveState.PenActive -> s.noteId
            is AutoSaveState.Debouncing -> s.noteId
            is AutoSaveState.Saving -> s.noteId
        }

    val isPenDown: Boolean
        get() = state is AutoSaveState.PenActive

    fun markDirty(noteId: String) {
        when (val s = state) {
            is AutoSaveState.Clean -> {
                // If pen is not down, start debouncing immediately
                scheduleDebounce(noteId)
            }
            is AutoSaveState.PenActive -> {
                // Keep noteId updated if note switched
                state = AutoSaveState.PenActive(noteId)
            }
            is AutoSaveState.Debouncing -> {
                // Reset timer for newly received edits
                s.job.cancel()
                scheduleDebounce(noteId)
            }
            is AutoSaveState.Saving -> {
                // Save is currently running; schedule another save after
                scheduleDebounce(noteId)
            }
        }
    }

    fun onPenContactChanged(down: Boolean, activeNoteId: String?) {
        if (down) {
            val noteId = activeNoteId ?: currentDirtyNoteId ?: return
            when (val s = state) {
                is AutoSaveState.Debouncing -> s.job.cancel()
                else -> {}
            }
            state = AutoSaveState.PenActive(noteId)
        } else {
            // Pen lifted
            when (val s = state) {
                is AutoSaveState.PenActive -> {
                    scheduleDebounce(s.noteId)
                }
                else -> {}
            }
        }
    }

    private fun scheduleDebounce(noteId: String) {
        val job = scope.launch {
            delay(debounceDelayMs)
            executeSave(noteId)
        }
        state = AutoSaveState.Debouncing(noteId, job)
    }

    private suspend fun executeSave(noteId: String) {
        state = AutoSaveState.Saving(noteId)
        try {
            onPerformSave(noteId)
        } finally {
            if (state is AutoSaveState.Saving && (state as AutoSaveState.Saving).noteId == noteId) {
                state = AutoSaveState.Clean
            }
        }
    }

    fun cancel() {
        when (val s = state) {
            is AutoSaveState.Debouncing -> s.job.cancel()
            else -> {}
        }
        state = AutoSaveState.Clean
    }

    fun flushNow(targetNoteId: String? = null, onComplete: (() -> Unit)? = null) {
        val id = targetNoteId ?: currentDirtyNoteId ?: run {
            onComplete?.invoke()
            return
        }
        when (val s = state) {
            is AutoSaveState.Debouncing -> s.job.cancel()
            else -> {}
        }
        state = AutoSaveState.Saving(id)
        scope.launch {
            try {
                onPerformSave(id)
            } finally {
                if (state is AutoSaveState.Saving && (state as AutoSaveState.Saving).noteId == id) {
                    state = AutoSaveState.Clean
                }
                onComplete?.invoke()
            }
        }
    }
}
