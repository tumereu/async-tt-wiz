package org.asyncttwiz.android

import org.asyncttwiz.protocol.GameAction
import org.asyncttwiz.protocol.GameScope

enum class UndoIntent { UNDO, REDO;
    val action: GameAction get() = if (this == UNDO) GameAction.Undo else GameAction.Redo
}
data class UndoAvailability(val connected: Boolean, val canUndo: Boolean, val canRedo: Boolean) {
    fun permits(intent: UndoIntent) = connected && if (intent == UndoIntent.UNDO) canUndo else canRedo
}
data class UndoCallbacks(val undo: () -> Unit, val redo: () -> Unit)

/** Native request seam, not a local history stack. The real session must supply readiness and submit
 * through its normal FIFO/request identity/recovery path. No default connected flag/no-op handler.
 */
class UndoController(private val scope: () -> GameScope?, private val availability: () -> UndoAvailability,
    private val submit: (GameScope, UndoIntent) -> Boolean) {
    val callbacks = UndoCallbacks({ request(UndoIntent.UNDO) }, { request(UndoIntent.REDO) })
    fun request(intent: UndoIntent): Boolean {
        if (!availability().permits(intent)) return false
        val current = scope() ?: return false
        if (current.playId == null || current.preparationId != null || current.invitationId != null) return false
        return submit(current, intent)
    }
}

/** Shared pointer arbitration for host tests and non-Compose native input adapters.
 * Hold consumes release even if Redo is unavailable. Cancel never emits Undo.
 */
class UndoGesture(private val callbacks: UndoCallbacks) {
    private var pressed = false
    private var held = false
    fun press() { pressed = true; held = false }
    fun hold() { if (pressed && !held) { held = true; callbacks.redo() } }
    fun release() { if (pressed && !held) callbacks.undo(); cancel() }
    fun cancel() { pressed = false; held = false }
}
