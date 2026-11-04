package org.asyncttwiz.protocol

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable

/** Win/loss is always relative to author, never relative to the receiving screen. */
@Serializable enum class SuggestedResult { WIN, LOSS, DRAW }
/** How a play ended, as the table and the game-over notice show it: [author]'s [outcome] (LOB-111). [note] is an optional
 * reason; [byRules] (I-D53): the result the rules gave. */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable data class ResultSuggestion(val id: String, val author: String, val outcome: SuggestedResult,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val note: String = "",
    @EncodeDefault(EncodeDefault.Mode.NEVER) val byRules: Boolean = false) {
    /** The result from [viewer]'s side. */
    fun outcomeFor(viewer: String): SuggestedResult = if (viewer == author || outcome == SuggestedResult.DRAW) outcome
        else if (outcome == SuggestedResult.WIN) SuggestedResult.LOSS else SuggestedResult.WIN
}
object ResultNotes {
    const val MAX_LENGTH = 200
    /** Already trimmed, at most [MAX_LENGTH] characters, one line without control characters. */
    fun valid(note: String): Boolean = note == note.trim() && note.length <= MAX_LENGTH &&
        note.none { it.isISOControl() } && DeckValidation.text(note)
}

object GameResultRules {
    /** The lobby preparation after [play] ends by request [requestId]: nobody ready, the starter alternated if chosen. */
    fun next(prepared: LobbyPreparation, play: PlayState, requestId: String): LobbyPreparation {
        // The persisted preparation is independent of the mutable play board and saved deck sources.
        // Do not introduce a new starter default or sample Random during this transition. Alternating
        // hands the start to the player who did not start this play (its resolved starter, after Random).
        val starter = if (!prepared.alternateStarter) prepared.starter
            else prepared.players.map { it.accountId }.singleOrNull { it != play.starter.accountId }?.let(::StarterChoice) ?: prepared.starter
        return prepared.copy(roundId = CommandIds.uuidV5(requestId, "preparation-round"),
            players = prepared.players.map { it.copy(ready = false) }, starter = starter)
    }
}
