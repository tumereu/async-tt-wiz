package org.asyncttwiz.android

import org.asyncttwiz.protocol.*

/** Captured at gesture/dialog creation. Never rebuild a stale action from the latest game. */
data class ResultTarget(val gameId: String, val playId: String)
data class ResultAcceptanceTarget(val play: ResultTarget, val suggestionId: String)

/** Group-playback presentation marker. The pending-outcome lock instead uses the live endpoint. */
data class ResultPlaybackGate(val play: ResultTarget, val suggestionId: String)
data class ResultPresentation(val target: ResultAcceptanceTarget, val text: String, val canAccept: Boolean,
    /** The suggester's optional reason, shown to both players. */
    val note: String = "",
    /** I-D53: the rules ended the game; [text] is the result from the viewer's side and OK confirms it. */
    val byRules: Boolean = false)

/** Typing aid for the optional result reason: one line, at most [ResultNotes.MAX_LENGTH] characters. */
object ResultNoteInput {
    fun typed(value: String): String = value.map { if (it.isISOControl()) ' ' else it }.joinToString("")
        .take(ResultNotes.MAX_LENGTH).let { if (it.lastOrNull()?.isHighSurrogate() == true) it.dropLast(1) else it }
    /** What is sent: trimmed and always valid, so a stray character never blocks the suggestion. */
    fun clean(value: String): String = typed(value).trim().takeIf(ResultNotes::valid).orEmpty()
}

object ResultActions {




    /** [pendingPresentation] of the board the table draws. */
    fun pendingPresentation(game: GameSummary, displayed: TableBoard?, viewer: String): ResultPresentation? {
        val target = capture(game, displayed) ?: return null
        val suggestion = displayed?.board?.resultSuggestion ?: return null
        return presentation(game, displayed, viewer, ResultPlaybackGate(target, suggestion.id))
    }



    /** [capture] of the board the table draws. */
    fun capture(game: GameSummary, play: TableBoard?): ResultTarget? = play?.takeIf {
        game.lifecycle == GameLifecycle.ACTIVE && game.id == it.gameId && game.playId == it.playId
    }?.let { ResultTarget(it.gameId, it.playId) }


    /** [presentation] of the board the table draws. */
    fun presentation(game: GameSummary, current: TableBoard?, viewer: String,
        gate: ResultPlaybackGate?): ResultPresentation? {
        val target = capture(game, current) ?: return null
        if (current == null) return null
        val suggestion = current.board.resultSuggestion ?: return null
        if (gate?.play != target || gate.suggestionId != suggestion.id || viewer !in current.board.players.map { it.accountId }) return null
        // I-D53: the rules' result, from the viewer's side; either player confirms it
        if (suggestion.byRules) return ResultPresentation(ResultAcceptanceTarget(target, suggestion.id),
            byRulesResult(suggestion.outcomeFor(viewer)), canAccept = true, byRules = true)
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
