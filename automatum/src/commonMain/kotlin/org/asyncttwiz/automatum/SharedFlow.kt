package org.asyncttwiz.automatum

import arcanum.api.CardDefinition
import arcanum.engine.Engine
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.decision.ManaUse
import arcanum.engine.input.Input
import arcanum.engine.setup.result
import arcanum.engine.state.GameState
import arcanum.engine.state.LoggedChange
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.state.StackItem
import arcanum.engine.state.TargetRef
import org.asyncttwiz.protocol.MtgRef
import org.asyncttwiz.protocol.PlayState

/**
 * Priority, resolution and triggers through the engine as **shared flows** (integration spec I-D8, I-D11, §3 I2): each
 * engine call is sent as its own batch as it happens, the open decision is kept in the game (the continuation record),
 * and either player may answer it (D11). Built on [play]'s board with full information (I-D17, I-D27: the client holds
 * every card; what a player is shown is the app's business), so [Engine.shouldPassFor] can pass for either player.
 *
 * [pass] ("Let it resolve") and [answer] each give the [EngineCall] to plan into one batch ([EngineBatch.plan]); both
 * then pass automatically for whoever the engine says should ([autoPass], I-D3, I-D18), so with nothing to respond with
 * a spell resolves at once. Steps and turns still change by hand (I3): the host never passes on an empty Stack, which
 * would end the step.
 */
class SharedFlow(
    val play: PlayState,
    val definitions: Map<String, CardDefinition>,
    val log: List<LoggedChange>,
    /** Fresh seeds for random results the engine asks for (D25). */
    private val seeds: () -> String,
) {
    private fun call(): EngineCall = EngineCall(play, definitions, EngineState.flowViewer(play.board), log, seeds)
        .also(HostPriority::settle)

    /** The board as the engine sees it, with priority settled (no change of [play] is made by looking). */
    private val view: EngineCall = call()

    /** The engine state of the board (names, options of the open decision). */
    val state: GameState get() = view.working

    /** The open decision of the board's shared flow, or null when none is open. */
    val decision: Decision? get() = view.pending?.decision

    /** Who holds priority with something on the Stack and nothing to decide: the player who may respond or let the top
     * resolve. Null otherwise (an empty Stack: steps change by hand until I3). */
    val priority: String? get() = state.takeIf { it.pending == null && it.stack.isNotEmpty() && it.result == null }
        ?.priority?.holder?.value

    /** The top of the Stack, which resolves next (CR 405.5); null when the Stack is empty. */
    val top: StackItem? get() = state.stack.lastOrNull()?.let { state.stackItems[it] }

    /** The printed text of [item]'s ability (a spell's text, an ability's line), when it has one. */
    fun text(item: StackItem): String? = CastFlow.text(item.ability)

    /** [player] passes priority ("Let it resolve", CR 117.3d), then the automatic passes; both passing in a row resolves
     * the top of the Stack (CR 117.4). Anyone may pass for [player] (D11). */
    fun pass(player: String): EngineCall = call().apply {
        perform(Input.PassPriority(PlayerId(player)))
        autoPass(this)
    }

    /**
     * Answers the open decision, then the automatic passes.
     * @throws IllegalArgumentException when the engine refuses [answer] (nothing changes).
     */
    fun answer(answer: Answer): EngineCall {
        val open = requireNotNull(decision) { "Nothing to answer" }
        return call().apply {
            perform(Input.Answer(open.id, answer))
            autoPass(this)
        }
    }

    /** The answers the engine lists for the open decision (a working sample for choices of several, §7.0). */
    fun listedAnswers(): List<Answer> {
        val open = decision ?: return emptyList()
        return Engine.options(state, open.player).filterIsInstance<Input.Answer>().filter { it.decision == open.id }.map { it.answer }
    }

    /** Whether paying the open [Decision.ManaPayment] with [uses] completes it. */
    fun pays(uses: List<ManaUse>): Boolean {
        val open = decision as? Decision.ManaPayment ?: return false
        return try {
            Engine.perform(state, Input.Answer(open.id, Answer.ManaPayment(uses)))
            true
        } catch (_: IllegalArgumentException) {
            false
        } catch (_: IllegalStateException) {
            false
        }
    }

    /** The objects whose identity [player] may be shown now (D60, I-D27): public cards and their own. */
    fun visible(player: String): Set<ObjectId> = Engine.visible(state, PlayerId(player))

    /** The ATW reference of an engine object or player (target glow, labels). */
    fun ref(target: TargetRef): MtgRef = view.ids.ref(target)

    /** The ATW card of an engine object, when it is a card. */
    fun atwCard(id: ObjectId): String? = (id as? ObjectId.Card)?.let { view.ids.atw(it.card) }

    companion object {
        /** More passes than any Stack needs; a guard against a host loop. */
        private const val MAX_PASSES = 500

        /**
         * Passes priority for whoever holds it while [Engine.shouldPassFor] says so (I-D3, I-D18, each player's own
         * "Skip when I have no choice"), stopping at a decision, an empty Stack (steps change by hand until I3) or the
         * end of the game. The passes are automatic: the history folds them into the batch's visible event (I-D22).
         */
        fun autoPass(call: EngineCall) {
            repeat(MAX_PASSES) {
                val s = call.working
                if (s.pending != null || s.stack.isEmpty() || s.result != null) return
                val holder = s.priority.holder ?: return
                if (!Engine.shouldPassFor(s, holder)) return
                call.perform(Input.PassPriority(holder), automatic = true)
            }
        }
    }
}
