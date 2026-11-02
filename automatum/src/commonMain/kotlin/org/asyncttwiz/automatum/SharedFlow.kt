package org.asyncttwiz.automatum

import arcanum.api.CardDefinition
import arcanum.api.TurnStep
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
 * [pass] ("Let it resolve"), [step] (the step button, §3 I3) and [answer] each give the [EngineCall] to plan into one
 * batch ([EngineBatch.plan]); all then pass automatically for whoever the engine says should ([StepFlow.automatic],
 * I-D3, I-D18), so with nothing to respond with a spell resolves at once, and on an empty Stack the steps go on (with
 * their turn-based actions and triggers) up to the next point where a player can act (I-D35).
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
     * resolve. Null otherwise (on an empty Stack the step button passes, [step]). */
    val priority: String? get() = state.takeIf { it.pending == null && it.stack.isNotEmpty() && it.result == null }
        ?.priority?.holder?.value

    /** Who holds priority now with nothing to decide (an empty Stack too), or null. */
    val holder: String? get() = state.takeIf { it.pending == null && it.result == null }?.priority?.holder?.value

    /** The player who declares attackers or blockers now (CR 508.1, 509.1): nobody holds priority until they do; null
     * when no declaration is awaited. */
    val declaring: String? get() = state.takeIf { it.pending == null && it.result == null && it.priority.holder == null }?.let { s ->
        Engine.options(s, s.turn.active).firstOrNull { it is Input.DeclareAttackers }?.let { s.turn.active.value }
            ?: s.players.firstOrNull { p -> Engine.options(s, p.id).any { it is Input.DeclareBlockers } }?.id?.value
    }

    /** The top of the Stack, which resolves next (CR 405.5); null when the Stack is empty. */
    val top: StackItem? get() = state.stack.lastOrNull()?.let { state.stackItems[it] }

    /** The printed text of [item]'s ability (a spell's text, an ability's line), when it has one. */
    fun text(item: StackItem): String? = CastFlow.text(item.ability)

    /** [player] passes priority ("Let it resolve", CR 117.3d), then the automatic passes; both passing in a row resolves
     * the top of the Stack (CR 117.4). Anyone may pass for [player] (D11). */
    fun pass(player: String): EngineCall = call().apply {
        perform(Input.PassPriority(PlayerId(player)))
        StepFlow.automatic(this)
    }

    /**
     * The step button with Automatum on (§3 I3, TRN-007): [player], who holds priority, passes as far as [until] asks
     * ([StepFlow.pass]), then the automatic passes. Anyone may pass for the player who holds priority (D11).
     * @throws IllegalArgumentException when [player] doesn't hold priority or a decision is open (nothing changes).
     */
    fun step(player: String, until: StepFlow.Until): EngineCall = call().apply { StepFlow.pass(this, player, until) }

    /**
     * Declares what ATW's lanes hold (the step button in the declare attackers or blockers step, CR 508.1, 509.1), then
     * the automatic passes.
     * @throws CombatRefused when the engine refuses the declaration (nothing changes).
     */
    fun declare(): EngineCall = call().apply {
        val (attacks, blocks) = CombatFlow.lanes(play.board.combat)
        if (Engine.options(working, working.turn.active).any { it is Input.DeclareAttackers }) CombatFlow.declareAttackers(this, attacks)
        else CombatFlow.declareBlockers(this, blocks)
    }

    /**
     * Answers the open decision, then the automatic passes.
     * @throws IllegalArgumentException when the engine refuses [answer] (nothing changes).
     */
    fun answer(answer: Answer): EngineCall {
        val open = requireNotNull(decision) { "Nothing to answer" }
        return call().apply {
            perform(Input.Answer(open.id, answer))
            StepFlow.automatic(this)
        }
    }

    /**
     * Answers the open division of combat damage with [shares] (null: the suggestion, I-D29), then the host's automatic
     * passes through the steps ([CombatFlow.divide]): the damage is dealt and the game goes on to the next point where a
     * player could act. A division a player gave early for the decision after it is applied on the way (I-D33).
     * @throws IllegalArgumentException when no division is open or the engine refuses [shares] (nothing changes).
     */
    fun divide(shares: Map<MtgRef, Int>?): EngineCall = call().apply { CombatFlow.divide(this, shares) }

    /** The open division of combat damage in ATW's terms, or null (also null for a division of a spell's damage). */
    val division: DamageDivision? get() {
        val d = decision as? Decision.Divide ?: return null
        val combat = state.combat ?: return null
        if (state.turn.step != TurnStep.CombatDamage || (combat.attackers.none { it.id == d.source } && combat.blockers.none { it.id == d.source }))
            return null
        return CombatFlow.division(view)
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
}
