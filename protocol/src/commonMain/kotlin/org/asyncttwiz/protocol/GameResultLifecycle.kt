package org.asyncttwiz.protocol

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable

/** Win/loss is always relative to author, never relative to the receiving screen. */
@Serializable enum class SuggestedResult { WIN, LOSS, DRAW }
/** [note] is the author's optional reason shown to both players (rules v25); empty is omitted on the wire. */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable data class ResultSuggestion(val id: String, val author: String, val outcome: SuggestedResult,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val note: String = "")
/** Relevant from/to tuple for history replay and selective Undo integration; no board snapshot. */
@Serializable data class ResultSuggestionChange(val from: ResultSuggestion?, val to: ResultSuggestion?)

object ResultNotes {
    const val MAX_LENGTH = 200
    /** Already trimmed, at most [MAX_LENGTH] characters, one line without control characters. */
    fun valid(note: String): Boolean = note == note.trim() && note.length <= MAX_LENGTH &&
        note.none { it.isISOControl() } && DeckValidation.text(note)
}

data class ResultAcceptance(val preparation: LobbyPreparation? = null, val reason: String? = null)

object GameResultRules {
    /** Acceptance is a lifecycle request, deliberately not a PlayAction/history mutation. */
    fun accept(game: GameSummary, play: PlayState?, prepared: LobbyPreparation, actor: String,
        command: GameCommand): ResultAcceptance {
        fun reject(reason: String) = ResultAcceptance(reason = reason)
        val action = command.action as? GameAction.AcceptResult ?: return reject("invalidInput")
        if (!GameWire.valid(command)) return reject("invalidInput")
        if (actor !in listOf(game.inviter.accountId, game.recipient.accountId)) return reject("accessDenied")
        if (game.lifecycle != GameLifecycle.ACTIVE || play == null) return reject("lifecycleEnded")
        if (command.scope.gameId != game.id || command.scope.playId != game.playId || play.id != game.playId ||
            play.gameId != game.id || play.preparationRound != game.preparationRound ||
            prepared.gameId != game.id || prepared.roundId != play.preparationRound) return reject("inapplicable")
        val suggestion = play.board.resultSuggestion ?: return reject("inapplicable")
        if (action.suggestionId != suggestion.id) return reject("inapplicable")
        if (actor == suggestion.author) return reject("otherPlayerRequired")
        // The persisted preparation is independent of the mutable play board and saved deck sources.
        // Do not introduce a new starter default or sample Random during this transition.
        return ResultAcceptance(prepared.copy(roundId = CommandIds.uuidV5(command.requestId, "preparation-round"),
            players = prepared.players.map { it.copy(ready = false) }))
    }
}
