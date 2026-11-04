package org.asyncttwiz.automatum

import arcanum.api.CardDefinition
import arcanum.engine.Engine
import arcanum.engine.Outcome
import arcanum.engine.change.Change
import arcanum.engine.decision.Decision
import arcanum.engine.decision.FilledObject
import arcanum.engine.input.Input
import arcanum.engine.state.GameState
import arcanum.engine.state.LoggedChange
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import org.asyncttwiz.protocol.PlayState

/**
 * One engine call as ATW hosts it on today's model (integration spec §2): the engine state built from [state]
 * ([EngineState.from]), [perform] with a player's input, and every host decision answered inside the same call (D25, D60;
 * [EngineRun]); a step another player decides in their own view ([Decision.OtherView], D31) has its identities filled
 * from ATW's card data, and the call is recorded for reports from its first input on (I-D43). [changes] are everything
 * it did, in order, for [EngineBatch.plan] to turn into one ATW batch.
 *
 * Engine-responsiveness R4a: a game on engine state runs an [EngineRun] on its own state instead; this is today's model
 * only.
 */
class EngineCall private constructor(
    val state: PlayState,
    val definitions: Map<String, CardDefinition>,
    val viewer: String?,
    val log: List<LoggedChange>,
    start: GameState,
    seeds: () -> String,
) : EngineRun(start, state.board, state.gameId, CardIds.of(state.board), seeds) {
    constructor(state: PlayState, definitions: Map<String, CardDefinition>, viewer: String? = null,
        log: List<LoggedChange> = emptyList(), seeds: () -> String) :
        this(state, definitions, viewer, log, built(state, definitions, viewer, log), seeds)

    /** A change the host makes itself before the input (priority settled while ATW's steps change by hand,
     * [HostPriority]); it goes out with the call's batch. */
    internal fun host(change: Change) {
        applyHost(change)
        recording?.add(CallStep.Host(change)) ?: hostSteps.add(CallStep.Host(change))
    }

    /** I-D43: the call's steps are recorded ([AutomatumLog]) from its first input on; host changes made before it are kept
     * until then. */
    private var recording: CallRecording? = null
    private val hostSteps = mutableListOf<CallStep>()

    /** The number [AutomatumLog] gave this call, once it performed an input. */
    val logNumber: Int? get() = recording?.number

    override fun performed(input: Input): Outcome {
        val recorded = recording ?: AutomatumLog.begin(this, hostSteps.toList()).also { recording = it }
        recorded.add(CallStep.Perform(input))
        return Engine.perform(working, input, recorded.trace)
    }

    /** The identity of [id] from ATW's card data. */
    override fun filled(id: ObjectId): FilledObject {
        val card = (id as? ObjectId.Card)?.card ?: error("Not a card: $id")
        val atw = hidden[card] ?: ids.atw(card)
        val played = state.board.cards.singleOrNull { it.id == atw } ?: error("No card $atw to fill $id")
        // I-D21: a card the device couldn't read plays with its printed values and manual abilities
        val definition = CardLedger.of(played.ledger).definition ?: definitions[played.definition]
            ?: PrintedCards.definition(state, played) ?: throw Unsupported(listOf("A card has no card data"))
        return FilledObject(id, definition)
    }

    private companion object {
        /** The engine state of [state]'s board; I-D43: the board of the latest call is a report's latest board, also
         * when the engine can't take it. */
        fun built(state: PlayState, definitions: Map<String, CardDefinition>, viewer: String?, log: List<LoggedChange>): GameState {
            AutomatumLog.context(state, definitions, viewer, log)
            return EngineState.from(state, definitions, viewer, log)
        }
    }
}

/** A shuffle the host answered: [player]'s library in [order] (ATW card ids, top first) from [seed], as ATW's
 * `ShuffleLibrary` with that seed orders it. */
internal data class ShuffleAnswer(val player: PlayerId, val seed: String, val order: List<String>)
