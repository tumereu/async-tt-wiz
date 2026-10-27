package org.asyncttwiz.android

import org.asyncttwiz.protocol.*

/** Captured at gesture/dialog creation. Never rebuild a stale action from the latest game. */
data class ResultTarget(val gameId: String, val playId: String)
data class ResultAcceptanceTarget(val play: ResultTarget, val suggestionId: String)

/** Group-playback presentation marker. The pending-outcome lock instead uses the live endpoint. */
data class ResultPlaybackGate(val play: ResultTarget, val suggestionId: String)
data class ResultPresentation(val target: ResultAcceptanceTarget, val text: String, val canAccept: Boolean)

object ResultActions {
    /** The live accepted endpoint, never the board at the personal review cursor, owns the lock.
     * A local proposal also locks immediately while its ordinary prediction is in flight. */
    fun blocksGameActions(game: GameSummary, accepted: PlayState?, predicted: PlayState? = accepted): Boolean =
        listOf(accepted, predicted).any { capture(game, it) != null && it?.board?.resultSuggestion != null }

    fun pendingPresentation(game: GameSummary, accepted: PlayState?, viewer: String): ResultPresentation? {
        val target = capture(game, accepted) ?: return null
        val suggestion = accepted?.board?.resultSuggestion ?: return null
        return presentation(game, accepted, viewer, ResultPlaybackGate(target, suggestion.id))
    }

    fun capture(game: GameSummary, play: PlayState?): ResultTarget? = play?.takeIf {
        game.lifecycle == GameLifecycle.ACTIVE && game.id == it.gameId && game.playId == it.id
    }?.let { ResultTarget(it.gameId, it.id) }

    fun presentation(game: GameSummary, current: PlayState?, viewer: String,
        gate: ResultPlaybackGate?): ResultPresentation? {
        val target = capture(game, current) ?: return null
        val suggestion = current!!.board.resultSuggestion ?: return null
        if (gate?.play != target || gate.suggestionId != suggestion.id || viewer !in current.board.players.map { it.accountId }) return null
        val author = if (suggestion.author == viewer) "You" else
            listOf(game.inviter, game.recipient).find { it.accountId == suggestion.author }?.username ?: return null
        val text = when (suggestion.outcome) {
            SuggestedResult.WIN -> "$author proposed ${if (suggestion.author == viewer) "your" else "their"} win."
            SuggestedResult.LOSS -> "$author proposed ${if (suggestion.author == viewer) "your" else "their"} loss."
            SuggestedResult.DRAW -> "$author proposed a draw."
        }
        return ResultPresentation(ResultAcceptanceTarget(target, suggestion.id), text, suggestion.author != viewer)
    }
}
