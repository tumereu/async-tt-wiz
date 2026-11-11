package org.asyncttwiz.android

import androidx.compose.runtime.*
import org.asyncttwiz.protocol.*

/** LOB-039; TRN-065 (owner, 2026-11-09): not while an OK on the last result is still owed (both are still at its table). */
fun setupCancellationAllowed(game: GameSummary, actor: String?): Boolean =
    game.lifecycle == GameLifecycle.LOBBY && game.resultNotice == null && (game.inviter.accountId == actor ||
        (game.recipient.accountId == actor && game.invitationState == InvitationState.ACCEPTED))

/** Personal, captured UI intent only. No lifecycle prediction or outcome-as-state. */
class SetupCancellation {
    var context by mutableStateOf<GameSummary?>(null)
        private set
    var confirmation by mutableStateOf<GameSummary?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun context(game: GameSummary) { context = game; confirmation = null; error = null }
    fun request(game: GameSummary) { context = null; confirmation = game; error = null }
    fun dismissContext() { context = null }
    fun clearError() { error = null }
    fun dismiss() { confirmation = null; error = null }
    fun clear() { dismissContext(); dismiss() }
    fun accepted(game: GameSummary) {
        fun stale(captured: GameSummary?) = captured?.id == game.id &&
            (game.lifecycle != GameLifecycle.LOBBY || captured.preparationRound != game.preparationRound)
        if (stale(context)) dismissContext()
        if (stale(confirmation)) dismiss()
    }
    fun confirm(submit: (GameSummary) -> Boolean) {
        val captured = confirmation ?: return
        if (submit(captured)) dismiss()
        else error = "This game changed or is disconnected. Reconnect and try again."
    }
}
