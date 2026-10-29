package org.asyncttwiz.android

import org.asyncttwiz.protocol.*

/** Captured at gesture/dialog creation. Never rebuild a stale action from the latest game. */
data class ResultTarget(val gameId: String, val playId: String)
data class ResultAcceptanceTarget(val play: ResultTarget, val suggestionId: String)

/** Group-playback presentation marker. The pending-outcome lock instead uses the live endpoint. */
data class ResultPlaybackGate(val play: ResultTarget, val suggestionId: String)
data class ResultPresentation(val target: ResultAcceptanceTarget, val text: String, val canAccept: Boolean,
    /** The suggester's optional reason, shown to both players. */
    val note: String = "")

/** Typing aid for the optional result reason: one line, at most [ResultNotes.MAX_LENGTH] characters. */
object ResultNoteInput {
    fun typed(value: String): String = value.map { if (it.isISOControl()) ' ' else it }.joinToString("")
        .take(ResultNotes.MAX_LENGTH).let { if (it.lastOrNull()?.isHighSurrogate() == true) it.dropLast(1) else it }
    /** What is sent: trimmed and always valid, so a stray character never blocks the suggestion. */
    fun clean(value: String): String = typed(value).trim().takeIf(ResultNotes::valid).orEmpty()
}

object ResultActions {
    /** The live accepted endpoint, never the board at the personal review cursor, owns the lock.
     * A local proposal also locks immediately while its ordinary prediction is in flight. */
    fun blocksGameActions(game: GameSummary, accepted: PlayState?, predicted: PlayState? = accepted): Boolean =
        listOf(accepted, predicted).any { capture(game, it) != null && it?.board?.resultSuggestion != null }

    /** At the live end the live endpoint owns the lock. In the timeline only a displayed board that already
     * includes the suggestion locks; a suggestion still "about to come", or on another branch, does not. */
    fun blocksAt(game: GameSummary, accepted: PlayState?, predicted: PlayState?, displayed: PlayState?, reviewing: Boolean): Boolean =
        if (reviewing) capture(game, displayed) != null && displayed?.board?.resultSuggestion != null
        else blocksGameActions(game, accepted, predicted)

    /** Visibility belongs to the displayed replayed board, not the live action lock. */
    fun pendingPresentation(game: GameSummary, displayed: PlayState?, viewer: String): ResultPresentation? {
        val target = capture(game, displayed) ?: return null
        val suggestion = displayed?.board?.resultSuggestion ?: return null
        return presentation(game, displayed, viewer, ResultPlaybackGate(target, suggestion.id))
    }

    /** A paused timeline at the live end is actionable too; earlier/catch-up frames are not. */
    fun canResolve(game: GameSummary, displayed: PlayState?, live: PlayState?, position: HistoryPosition?): Boolean {
        val target = capture(game, displayed) ?: return false
        if (target != capture(game, live) || displayed == null || live == null || position == null) return false
        return !position.playing && position.cursorId == live.currentNodeId && displayed.currentNodeId == live.currentNodeId &&
            displayed.board.resultSuggestion != null && displayed.board.resultSuggestion == live.board.resultSuggestion
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
        return ResultPresentation(ResultAcceptanceTarget(target, suggestion.id), text, suggestion.author != viewer, suggestion.note)
    }
}
