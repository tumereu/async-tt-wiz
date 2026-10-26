package org.asyncttwiz.android

import androidx.compose.runtime.*
import org.asyncttwiz.protocol.*

internal fun setupCancellationAllowed(game: GameSummary, actor: String?): Boolean =
    game.lifecycle == GameLifecycle.LOBBY && (game.inviter.accountId == actor ||
        (game.recipient.accountId == actor && game.invitationState == InvitationState.ACCEPTED))

/** Personal, captured UI intent only. No lifecycle prediction or outcome-as-state. */
internal class SetupCancellation {
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
