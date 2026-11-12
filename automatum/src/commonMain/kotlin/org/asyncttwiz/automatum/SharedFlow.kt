package org.asyncttwiz.automatum

import org.asyncttwiz.protocol.game.EnginePreferences
import arcanum.api.TurnStep
import arcanum.engine.CastScreen
import arcanum.engine.Engine
import arcanum.engine.Host
import arcanum.engine.Inspect
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.decision.ManaUse
import arcanum.engine.input.Input
import arcanum.engine.setup.result
import arcanum.engine.state.GameState
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.state.StackItem
import arcanum.engine.state.TargetRef
import org.asyncttwiz.protocol.BoardState
import org.asyncttwiz.protocol.MtgRef

/**
 * Priority, resolution and triggers through the engine as **shared flows** (integration spec I-D8, I-D11, §3 I2): each
 * engine call is sent as its own batch as it happens, the open decision is kept in the game (the continuation record),
 * and either player may answer it (D11). Built on [board] with full information (I-D17, I-D27: the client holds
 * every card; what a player is shown is the app's business).
 *
 * [pass] ("Let it resolve"), [step] (the step button, §3 I3) and [answer] each give the [EngineRun] whose own inputs a
 * game on engine state sends ([of]); the device then makes the passes that follow by itself (I-D56 step C).
 */
class SharedFlow private constructor(
    /** The board the flow is on. */
    val board: BoardState,
    /** A fresh run on [board]'s engine state. */
    private val call: () -> EngineRun,
) {
    /** The board as the engine sees it (nothing of [board] is changed by looking). */
    private val view: EngineRun = call()

    /** The engine state of the board (names, options of the open decision). */
    val state: GameState get() = view.working

    /** The open decision of the board's shared flow, or null when none is open. A division given early ([early], I-D33) is
     * its suggestion, so the panel shows it pre-filled. */
    val decision: Decision? by lazy {
        val open = view.pending?.decision
        val given = early
        if (open is Decision.Divide && given is PreAnswer.Division)
            open.copy(suggested = given.shares.map { arcanum.engine.decision.Share(view.ids.target(it.target), it.amount) })
        else open
    }

    /** Who holds priority with something on the Stack and nothing to decide: the player who may respond or let the top
     * resolve. Null otherwise (on an empty Stack the step button passes, [step]). */
    val priority: String? get() = state.takeIf { it.pending == null && it.stack.isNotEmpty() && it.result == null }
        ?.priority?.holder?.value

    /** Who holds priority now with nothing to decide (an empty Stack too), or null. In an untap step nobody holds it
     * (CR 502.4): the active player is the one whose pass ends the step there ([StepFlow.resting]; the first turn
     * begins after the opening). */
    val holder: String? get() = state.takeIf { it.pending == null && it.result == null }?.let { s ->
        s.priority.holder?.value ?: s.turn.active.value.takeIf { StepFlow.resting(s) } }

    /** The player who declares attackers or blockers now (CR 508.1, 509.1): nobody holds priority until they do; null
     * when no declaration is awaited. */
    val declaring: String? get() = state.takeIf { it.pending == null && it.result == null && it.priority.holder == null }?.let { s ->
        Engine.options(s, s.turn.active, EnginePreferences.of(s)).firstOrNull { it is Input.DeclareAttackers }?.let { s.turn.active.value }
            ?: s.players.firstOrNull { p -> Engine.options(s, p.id, EnginePreferences.of(s)).any { it is Input.DeclareBlockers } }?.id?.value
    }

    /** The top of the Stack, which resolves next (CR 405.5); null when the Stack is empty. */
    val top: StackItem? get() = state.stack.lastOrNull()?.let { state.stackItems[it] }

    /** The printed text of [item]'s ability (a spell's text, an ability's line), when it has one. */
    fun text(item: StackItem): String? = CastFlow.text(item.ability)

    /** [player] passes priority ("Let it resolve", CR 117.3d); both passing in a row resolves the top of the Stack
     * (CR 117.4). */
    fun pass(player: String): EngineRun = call().apply { perform(Input.PassPriority(PlayerId(player))) }

    /**
     * The step button with Automatum on (§3 I3, TRN-007): [player], who holds priority, passes ([StepFlow.pass]).
     * @throws IllegalArgumentException when [player] doesn't hold priority or a decision is open (nothing changes).
     */
    fun step(player: String): EngineRun = call().apply { StepFlow.pass(this, player) }

    /**
     * Declares what ATW's lanes hold (the step button in the declare attackers or blockers step, CR 508.1, 509.1), with
     * the attacker's choices ([CombatFlow.withChoices]); with [autotap] the mana of costs to attack or block is paid with the engine's suggestion (CR 508.1h, 509.1d),
     * as [pick] takes it from the payment.
     * @throws CombatRefused when the engine refuses the declaration (nothing changes).
     */
    fun declare(autotap: Boolean = false, pick: (Decision.ManaPayment) -> List<ManaUse> = { it.suggestion }): EngineRun = call().apply {
        val (attacks, blocks) = CombatFlow.lanes(board.combat)
        // CMB-036–CMB-038: with the attacker's choices; CMB-037: with [autotap] the costs' mana is paid by itself (I-D47)
        if (Engine.options(working, working.turn.active, EnginePreferences.of(working)).any { it is Input.DeclareAttackers })
            CombatFlow.declareAttackers(this, CombatFlow.withChoices(attacks, board), autotap, pick)
        else CombatFlow.declareBlockers(this, blocks, autotap, pick)
    }

    /**
     * Answers the open decision.
     * @throws IllegalArgumentException when the engine refuses [answer] (nothing changes).
     */
    fun answer(answer: Answer): EngineRun = answer(answer, emptyList())

    /**
     * Answers the open decision and then the orders that follow from the same choice ([then], I-D48). While the engine next asks the same player to put all the offered cards of the same source in order (a
     * [Decision.ChooseObjects] of all its options, as scry's "Order on top" and "Order on the bottom" after "Put on the
     * bottom", CR 701.22), the order in [then] of exactly those cards answers it in the same call. An order the engine
     * doesn't ask (one card) is left out; whatever is still asked stays open.
     * @throws IllegalArgumentException when the engine refuses [answer] (nothing changes).
     */
    fun answer(answer: Answer, then: List<Answer.Objects>): EngineRun {
        val open = requireNotNull(decision) { "Nothing to answer" }
        return call().apply {
            perform(Input.Answer(open.id, answer))
            val left = then.toMutableList()
            while (left.isNotEmpty()) {
                val next = pending?.decision as? Decision.ChooseObjects ?: break
                if (next.player != open.player || next.source != open.source || next.min != next.options.size) break
                val order = left.firstOrNull { it.chosen.size == next.options.size && it.chosen.toSet() == next.options.toSet() } ?: break
                left.remove(order)
                perform(Input.Answer(next.id, order))
            }
        }
    }

    /**
     * Answers the open "which replacement applies first" (CR 616.1) with the player's preference list [preference] (its
     * options, the first applying first): the first of them applies, and while the engine asks the same player again
     * (CR 616.1f) among effects all in the list, the earliest of them in the list applies in the same call; a new one
     * stays asked ([ReplacementPreference]).
     * @throws IllegalArgumentException when the engine refuses an answer (nothing changes).
     */
    fun answerReplacements(preference: List<arcanum.engine.decision.ReplacementOption>): EngineRun {
        val open = requireNotNull(decision as? Decision.ChooseReplacement) { "No replacement to choose" }
        val first = requireNotNull(ReplacementPreference.preferred(open.options, preference)) { "Not a preference of these effects" }
        return call().apply {
            perform(Input.Answer(open.id, Answer.Replacement(first.index)))
            while (true) {
                val next = pending?.decision as? Decision.ChooseReplacement ?: break
                if (next.player != open.player) break
                val pick = ReplacementPreference.preferred(next.options, preference) ?: break
                perform(Input.Answer(next.id, Answer.Replacement(pick.index)))
            }
        }
    }

    /**
     * Answers the open division of combat damage with [shares] (null: the suggestion, I-D29, [CombatFlow.divide]): the damage
     * is dealt. A division a player gave early for the decision after it is applied on the way (I-D33).
     * @throws IllegalArgumentException when no division is open or the engine refuses [shares] (nothing changes).
     */
    fun divide(shares: Map<MtgRef, Int>?): EngineRun = call().apply { CombatFlow.divide(this, shares) }

    /** The early answer given for the open decision that the engine takes (I-D33, #8: given while the batch that opened
     * the decision was on its way); null when there is none. [answerEarly] answers with it. */
    val early: PreAnswer? by lazy { PreAnswers.forOpen(view) }

    /** Answers the open decision with its [early] answer.
     * @throws IllegalArgumentException when there is none (nothing changes). */
    fun answerEarly(): EngineRun = call().apply { StepFlow.answerEarly(this) }

    /** The open division of combat damage in ATW's terms, or null (also null for a division of a spell's damage). */
    val division: DamageDivision? get() {
        val d = decision as? Decision.Divide ?: return null
        val combat = state.combat ?: return null
        if (state.turn.step != TurnStep.CombatDamage || (combat.attackers.none { it.id == d.source } && combat.blockers.none { it.id == d.source }))
            return null
        return CombatFlow.division(view)
    }

    /** What may be picked in the open choice of objects with [picked] so far, and whether [picked] is a finished selection
     * ([CastScreen.selection]). Blocking: the app runs it on the Automatum worker. */
    fun selection(picked: List<TargetRef>): arcanum.engine.Selection = CastScreen.selection(state, picked)

    /** The answers the engine lists for the open decision (a working sample for choices of several, §7.0); card names
     * and creature types only of objects the deciding player may see ([ShownAnswers], I-D27). Worked out once per flow
     * (final cast review #9: a panel asks on every recomposition). */
    fun listedAnswers(): List<Answer> = listed

    private val listed: List<Answer> by lazy {
        val open = decision ?: return@lazy emptyList()
        ShownAnswers.of(state, open,
            Engine.options(state, open.player, EnginePreferences.of(state)).filterIsInstance<Input.Answer>().filter { it.decision == open.id }.map { it.answer })
    }

    /**
     * STK-049 (owner gallery review, 2026-11-06): the payment the open "you may pay" of a mana cost goes on to (CR 118.12:
     * "unless … pays {R}", "you may pay {2}"), asked with it in one panel whose "Don't pay" answers no; null when the open
     * decision is no such question, its cost can't be paid, or paying it asks nothing (worked out once per flow).
     */
    val payment: Decision.ManaPayment? by lazy {
        val open = decision as? Decision.May ?: return@lazy null
        val cost = open.cost ?: return@lazy null
        if (!open.payable || cost.parts.isEmpty() || cost.parts.any { it !is arcanum.api.CostPart.Mana }) return@lazy null
        try {
            call().run {
                perform(Input.Answer(open.id, Answer.YesNo(true)))
                (pending?.decision as? Decision.ManaPayment)?.takeIf { it.player == open.player }
            }
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: IllegalStateException) {
            null
        }
    }

    /** Says yes to the open "you may pay" and pays its [payment] with [uses], as one step.
     * @throws IllegalArgumentException when the engine refuses either answer (nothing changes). */
    fun payMay(uses: List<ManaUse>): EngineRun {
        val open = requireNotNull(decision as? Decision.May) { "No payment to make" }
        return call().apply {
            perform(Input.Answer(open.id, Answer.YesNo(true)))
            val asked = requireNotNull(pending?.decision as? Decision.ManaPayment) { "No payment asked" }
            perform(Input.Answer(asked.id, Answer.ManaPayment(uses)))
        }
    }

    /** STK-024: the open payment (or the open "you may pay"'s [payment]) around the player's own picks: [pinned] stay, [removed]
     * aren't used, Autotap fills in the rest ([CastScreen.fillPayment]); empty when the rest can't be filled. Each of [exact] stays exactly
     * as given (a way the player chose for a source, STK-024). */
    fun fill(pinned: List<ManaUse>, removed: List<ManaUse>, exact: List<ManaUse> = emptyList()): List<ManaUse> {
        if (decision is Decision.ManaPayment) return CastScreen.fillPayment(state, pinned, removed, exact)
        val open = decision as? Decision.May ?: return emptyList()
        return try {
            call().run {
                perform(Input.Answer(open.id, Answer.YesNo(true)))
                CastScreen.fillPayment(working, pinned, removed, exact)
            }
        } catch (_: IllegalArgumentException) {
            emptyList()
        } catch (_: IllegalStateException) {
            emptyList()
        }
    }

    /** Whether paying the open [Decision.ManaPayment] (or the open "you may pay"'s [payment]) with [uses] completes it; the
     * latest answer is kept, so asking again for the same tapping costs no engine call (#9). */
    fun pays(uses: List<ManaUse>): Boolean {
        lastPays?.takeIf { it.first == uses }?.let { return it.second }
        val open = decision as? Decision.ManaPayment
        if (open == null && payment == null) return false
        val pays = try {
            if (open != null) Engine.perform(state, Input.Answer(open.id, Answer.ManaPayment(uses)), EnginePreferences.of(state)) else payMay(uses)
            true
        } catch (_: IllegalArgumentException) {
            false
        } catch (_: IllegalStateException) {
            false
        }
        lastPays = uses to pays
        return pays
    }

    @kotlin.concurrent.Volatile private var lastPays: Pair<List<ManaUse>, Boolean>? = null

    /** Works out what a panel showing this flow reads ([decision], [listedAnswers], whether the suggested mana pays), so
     * it can be done off the main thread before the flow is shown (SYS-025). Returns this flow. */
    fun prepared(): SharedFlow = also {
        val open = decision
        listedAnswers()
        if (open is Decision.ManaPayment) pays(open.suggestion)
        payment?.let { pays(it.suggestion) }
    }

    /** The objects whose identity [player] may be shown now (D60, I-D27): public cards and their own. */
    fun visible(player: String): Set<ObjectId> = Host.visible(state, PlayerId(player))

    /** Whether [id] is an option of the open choice that no longer exists and was face up as it last existed (an explorer that left the
     * battlefield, CR 701.44c, d): what it was stays public, so it is shown by what it was. */
    fun offeredAfterLeaving(id: ObjectId): Boolean = state.obj(id) == null &&
        (decision as? Decision.ChooseObjects)?.options?.contains(id) == true && Inspect.lastKnown(state, id)?.characteristics?.name != null

    /** The ATW reference of an engine object or player (target glow, labels). */
    fun ref(target: TargetRef): MtgRef = view.ids.ref(target)

    /** The ATW card of an engine object, when it is a card. */
    fun atwCard(id: ObjectId): String? = (id as? ObjectId.Card)?.let { view.ids.atw(it.card) }

    companion object {
        /**
         * The shared flow of a game on engine state (I-D56, engine-responsiveness R4a): [state] is its own state (full
         * information, never changed here) shown as [board]; every step runs on a copy of it ([EngineRun]), and only
         * the player's own inputs of it go out.
         */
        fun of(state: GameState, board: BoardState, gameId: String, seeds: () -> String): SharedFlow =
            SharedFlow(board) { EngineRun.of(state, board, gameId, seeds) }
    }
}
