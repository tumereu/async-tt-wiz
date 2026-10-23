package org.asyncttwiz.android

import org.asyncttwiz.protocol.*

/** Captured at gesture/dialog creation. Never rebuild a stale action from the latest game. */
data class ResultTarget(val gameId: String, val playId: String)
data class ResultAcceptanceTarget(val play: ResultTarget, val suggestionId: String)

/** I22 supplies this only after playback presents the preceding board then the singleton S group.
 * Receipt of a server suggestion is intentionally insufficient to produce a prompt. */
data class ResultPlaybackGate(val play: ResultTarget, val suggestionId: String)
data class ResultPresentation(val target: ResultAcceptanceTarget, val text: String, val canAccept: Boolean)

object ResultActions {
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
