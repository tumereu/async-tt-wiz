package org.asyncttwiz.protocol

import kotlinx.serialization.Serializable

/** Win/loss is always relative to author, never relative to the receiving screen. */
@Serializable enum class SuggestedResult { WIN, LOSS, DRAW }
@Serializable data class ResultSuggestion(val id: String, val author: String, val outcome: SuggestedResult)
/** Relevant from/to tuple for history replay and selective Undo integration; no board snapshot. */
@Serializable data class ResultSuggestionChange(val from: ResultSuggestion?, val to: ResultSuggestion?)

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
