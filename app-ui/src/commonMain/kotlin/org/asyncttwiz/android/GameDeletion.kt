package org.asyncttwiz.android

import org.asyncttwiz.protocol.GameLifecycle
import org.asyncttwiz.protocol.GameSummary

/** Personal confirmation captures an incarnation and actor, never a mutable selected-row lookup. */
data class GameDeletion(val game: GameSummary, val actorId: String) {
    val title get() = "Delete ${game.gameName}?"
    val warning get() = "This removes the shared game with ${game.opponent(actorId).username} for both players. There is no restore."
    fun matches(current: GameSummary?, actor: String?) = actor == actorId &&
        actorId in listOf(game.inviter.accountId, game.recipient.accountId) &&
        game.lifecycle == GameLifecycle.ACTIVE && game.playId != null &&
        current?.id == game.id && current.lifecycle == GameLifecycle.ACTIVE && current.playId == game.playId
}
