package org.asyncttwiz.android

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.decision.ManaUse
import arcanum.engine.state.GameState
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.state.TargetRef
import org.asyncttwiz.automatum.AutomatumLog
import org.asyncttwiz.automatum.CombatFlow
import org.asyncttwiz.automatum.CombatRefused
import org.asyncttwiz.automatum.EngineRun
import org.asyncttwiz.automatum.ManaPicks
import org.asyncttwiz.automatum.SharedFlow
import org.asyncttwiz.automatum.StepFlow
import org.asyncttwiz.protocol.*

/**
 * Priority, responding, resolution, triggers and the steps of the turn through Automatum (integration spec §3 I2b, I3)
 * as shared flows (I-D11): each step goes out as the viewer's own inputs ([engineSend]) and the open decision is kept in
 * the game, so both players follow it. The player holding priority with something on the
 * Stack is offered "Respond" (cast from the hand, [CastController]) or "Let it resolve" ([pass]); the step button passes
 * on an empty Stack ([step], TRN-057); the deciding player answers in the panel; the other player may pass or choose for
 * them after confirming. While a decision is open nothing else changes the game (D32); looking stays free and the eye
 * hides the panel (I-D10).
 *
 * The engine work of each answer runs on [work] (off the main thread in the app, SYS-025); meanwhile [busy] is true and
 * further answers wait. [view] is the shown board's flow, set by the panel's host once worked out ([show]).
 */
internal class SharedFlowController(
    private val viewer: String,
    private val current: () -> TableBoard?,
    private val fail: (String?) -> Unit,
    private val work: AutomatumWork = AutomatumWork.Inline,
    /** The pictures of tokens a flow makes (§4.6). */
    private val tokenArt: org.asyncttwiz.automatum.TokenArt = org.asyncttwiz.automatum.TokenArt.None,
    /** "Autotap mana sources" (I-D47): the mana of costs to attack or block is paid by itself as they are declared
     * (CMB-037). */
    private val autotap: () -> Boolean = { true },
    /** I-D56 step C: sends a flow's step as the player's own inputs (null: sent; else why not); the device answers the
     * randomness and makes the passes that follow (null: Automatum isn't available here). */
    private val engineSend: ((List<arcanum.engine.input.Input>) -> String?)? = null,
) : FlowQuestions {
    /** The flow of the shown board, null until worked out (or when there is none). */
    var view by mutableStateOf<SharedFlow?>(null); private set
    /** The eye (I-D10): the panel is hidden so the board can be looked at. */
    var hidden by mutableStateOf(false); private set
    /** "Respond" was tapped in this priority window (the board's event): the prompt is a slim bar while the player casts. */
    var responding by mutableStateOf<String?>(null); private set
    /** The decision the viewer confirmed answering for the other player (D11). */
    var choosingFor by mutableStateOf<String?>(null); private set
    /** Targets picked so far for a slot that takes several. */
    var picked by mutableStateOf<List<TargetRef>>(emptyList()); private set
    override var mana by mutableStateOf<List<ManaUse>>(emptyList()); private set
    /** Automatum is working out the last answer (SYS-025): nothing else is taken meanwhile. */
    var busy by mutableStateOf(false); private set
    private var shownDecision: String? = null
    private var visible: Set<ObjectId>? = null

    /** Shows [next], the flow of the board now shown; a new decision starts with its suggestion and the panel shown. */
    fun show(next: SharedFlow?) {
        if (next === view) return
        val id = next?.decision?.id?.value
        if (id != shownDecision) {
            shownDecision = id
            picked = emptyList()
            hidden = false
            mana = (next?.decision as? Decision.ManaPayment)?.suggestion.orEmpty()
            if (choosingFor != id) choosingFor = null
        }
        visible = null
        view = next
    }

    /** The open decision, when the viewer answers it: theirs, or the other player's after "Choose for them". */
    val answering: Decision? get() = view?.decision?.takeIf { it.player.value == viewer || choosingFor == it.id.value }

    /** The open decision is targets the viewer picks on the table, on the board they act on: until the panel has the new
     * board's flow (worked out off the main thread) a tap isn't taken as a target for the previous board's decision. */
    val targeting: Boolean get() = answering is Decision.Targets && view?.board === current()?.board

    fun toggleHidden() { hidden = !hidden }

    /** CMB-033 (owner, 2026-11-05): the division of combat damage open in the band now, with its decision; its OK is the
     * main button ([divisionAction]). Set by its panel. */
    var division by mutableStateOf<Pair<Decision.Divide, DivisionDraft>?>(null)

    /** CMB-033: the open division's OK on the main button (greyed with the reason while the division isn't complete), or
     * null without one. */
    fun divisionAction(): MainAction? {
        val (decision, draft) = division ?: return null
        if (answering?.id != decision.id) return null
        return MainAction("OK", Icons.Outlined.Check, refusal = draft.refusal ?: BUSY.takeIf { busy },
            tooltip = "Deal the damage as divided") { answer(Answer.Division(engineShares(decision, draft.division, draft.shares))) }
    }

    /** "Respond": the prompt shrinks to a bar while the player casts from the hand. */
    fun respond(play: TableBoard) { responding = play.nodeId; hidden = false }
    fun stopResponding() { responding = null }

    /** "Choose for them" confirmed (D11): the viewer answers the other player's open decision. */
    fun chooseFor(decision: Decision) { choosingFor = decision.id.value; hidden = false }

    /** "Let it resolve" ([player] the viewer), or "Pass for them" (D11): [player] passes priority, then the automatic
     * passes (I-D3, I-D18), all as one batch. */
    fun pass(player: String): Boolean {
        val play = current() ?: return false
        responding = null
        return sendCall(play) { flow ->
            if (flow.priority != player) throw Refused("${if (player == viewer) "You don't" else "They don't"} hold priority now.")
            flow.pass(player)
        }
    }

    /**
     * The step arrow with Automatum on (TRN-057): the player holding priority passes (TRN-004, D11); with attackers or
     * blockers awaited it declares what the combat lanes hold. With something on the Stack it is "Resolve": the holder
     * passes as with Let it resolve (I-D46 d).
     */
    fun step(): Boolean {
        val play = current() ?: return false
        stepRefusal(play)?.let { fail(it); return false }
        return sendCall(play) { flow -> pressed(play, flow) }
    }

    /** Why the step button can't act on [play] at all (nothing is worked out then), or null. */
    private fun stepRefusal(play: TableBoard): String? {
        if (play.flowOpen) return BUSY
        // I-D46 d: with something on the Stack the step arrow is "Resolve": its holder passes, as Let it resolve does
        val stacked = CombatStackRules.stack(play.board).cards.isNotEmpty()
        if (stacked && play.board.stackEffects.none { it.item != null }) return STACK_WAITING
        // TRN-027, TRN-028: the opening (mulligans) is the host's setup; the engine's game begins after it (CR 103.8)
        if (play.board.turn.number == "1" && PlayerStatus.anyOpening(play.board)) return OPENING
        return null
    }

    /** The engine call a press of the step button makes on [play]'s flow. */
    private fun pressed(play: TableBoard, flow: SharedFlow): EngineRun {
        if (CombatStackRules.stack(play.board).cards.isNotEmpty()) {
            val holder = flow.priority ?: throw Refused(STACK_WAITING)
            return flow.pass(holder)
        }
        val holder = flow.holder
        val declaring = if (holder == null) flow.declaring else null
        if (holder == null && declaring == null) throw Refused(NOTHING_TO_PASS)
        return if (holder != null) flow.step(holder) else flow.declare(autotap())
    }

    override fun answer(answer: Answer): Boolean = answerWith(answer, emptyList())

    /** I-D48: a two-area choice's answer and the orders the engine then asks of the same cards, as one step
     * ([SharedFlow.answer]). */
    override fun answerWith(answer: Answer, then: List<Answer.Objects>): Boolean {
        val play = current() ?: return false
        val open = view?.takeIf { it.board === play.board }?.decision
        if (open != null && open.id.value != shownDecision) { fail("The table changed: look at the choice again."); return false }
        return sendCall(play) { flow ->
            val decision = flow.decision ?: throw Refused(null)
            if (decision.id.value != shownDecision) throw Refused("The table changed: look at the choice again.")
            // A division of combat damage (I-D29): the damage is dealt and the game goes on through the steps (I3)
            if (answer is Answer.Division && flow.division != null) flow.divide(answer.shares.associate { flow.ref(it.target) to it.amount })
            else flow.answer(answer, then)
        }
    }

    /**
     * Answers the open decision with the early answer given for it (I-D33, #8: given while the batch that opened the
     * decision was on its way), then Automatum goes on, as one batch. The client of the player who made the latest event
     * does it as soon as it shows such a board ([SharedFlowHost]); the panel shows the early answer pre-filled meanwhile.
     */
    fun answerEarly(): Boolean {
        val play = current() ?: return false
        return sendCall(play) { flow ->
            if (flow.early == null) throw Refused(null)
            flow.answerEarly()
        }
    }

    /** A tap on a table item while the open decision asks for targets (I-D5: players by their life squares). */
    fun tapTarget(item: TableItem): Boolean {
        val decision = answering as? Decision.Targets ?: return false
        val flow = view ?: return false
        // UI review round 2 #4 (I-D46 b): not a legal target: false, the table shakes it
        val target = targetOf(flow, decision, item) ?: return false
        fail(null)
        if (decision.max == 1 && decision.min <= 1) return answer(Answer.Targets(listOf(target)))
        picked = if (target in picked) picked - target else picked + target
        if (decision.max != null && picked.size >= decision.max!!) return confirmTargets()
        return true
    }

    fun confirmTargets(): Boolean {
        val decision = answering as? Decision.Targets ?: return false
        if (picked.size < decision.min) return false
        return answer(Answer.Targets(picked))
    }

    /** The table items that glow as legal targets of the open decision the viewer answers. */
    fun glow(play: TableBoard): Set<String> {
        val flow = view ?: return emptySet()
        val decision = answering as? Decision.Targets ?: return emptySet()
        return targetGlow(play, decision.options.map(flow::ref))
    }

    /** The Stack entries that are legal targets of the open decision the viewer answers, top first. */
    fun stackTargets(play: TableBoard): List<String> {
        val flow = view ?: return emptyList()
        val decision = answering as? Decision.Targets ?: return emptyList()
        return targetEntries(play, decision.options.map(flow::ref))
    }

    override val engine: GameState get() = view!!.state
    override fun atwCard(id: ObjectId): String? = view?.atwCard(id)
    /** Worked out with the flow, off the main thread ([SharedFlow.prepared], SYS-025). */
    override fun listedAnswers(): List<Answer> = view?.listedAnswers().orEmpty()
    /** Worked out once per flow and tapping, off the main thread: the suggestion with the flow ([SharedFlow.prepared]),
     * each tapping by [toggleMana] (SYS-025, final cast review #9). */
    private var paidFor: Triple<SharedFlow, List<ManaUse>, Boolean>? = null
    override val paid: Boolean get() {
        val flow = view ?: return false
        paidFor?.takeIf { it.first === flow && it.second === mana }?.let { return it.third }
        return flow.pays(mana).also { paidFor = Triple(flow, mana, it) }
    }
    /** The tapped sources are a set: their colours are worked out together ([ManaPicks], as in a cast), on [work]
     * (SYS-025: up to a few dozen engine calls). */
    override fun toggleMana(group: List<ManaUse>) {
        val flow = view ?: return
        val decision = flow.decision as? Decision.ManaPayment ?: return
        if (busy) return
        val current = mana
        busy = true
        work.run({
            runCatching { ManaPicks.toggle(decision, current, group, flow::pays).let { it to flow.pays(it) } }
                .onFailure { AppLog.warn("automatum", "Mana tapping couldn't be worked out: ${it::class.java.simpleName}") }.getOrNull()
        }) { result ->
            busy = false
            val (uses, pays) = result ?: return@run
            if (view === flow && mana === current) { mana = uses; paidFor = Triple(flow, uses, pays) }
        }
    }

    /** I-D27: the viewer sees what the rules let them see; the deciding player also the cards their own choice offers in
     * zones that aren't public (their library during a search). Objects in public zones are named only when visible to
     * the viewer, so a face-down creature stays hidden from the player dividing damage onto it (#4). Someone choosing for
     * the other player sees that player's hidden cards only as hidden cards. */
    override fun shown(id: ObjectId): Boolean {
        val flow = view ?: return false
        if (id is ObjectId.StackItem) return true
        val known = visible ?: flow.visible(viewer).also { visible = it }
        if (id in known) return true
        val zone = flow.state.obj(id)?.zone
        return flow.decision?.player?.value == viewer && zone != null && zone !in PUBLIC_ZONES
    }

    /** The flow of [play]'s board: the shown one, or worked out now when the board moved on (blocking: on [work]). */
    private fun flowOf(play: TableBoard): SharedFlow =
        view?.takeIf { it.board === play.board } ?: AutomatumBoards.sharedFlow(play) ?: throw Refused(TAKE_OVER)

    /** A refusal with its message for the player (null: none). */
    private class Refused(message: String?) : Exception(message)

    private sealed interface Outcome {
        class Fail(val message: String?) : Outcome
        data object Empty : Outcome
        /** I-D56 step C: the viewer's own [inputs] made on [board]. */
        class Engine(val board: BoardState, val inputs: List<arcanum.engine.input.Input>) : Outcome
    }

    /**
     * Works one engine call out on [work] (SYS-025) from [play]'s flow and sends the viewer's own inputs of it, unless
     * the board moved on meanwhile. True when it was taken (with [AutomatumWork.Inline]: what the result says).
     */
    private fun sendCall(play: TableBoard, perform: (SharedFlow) -> EngineRun): Boolean {
        val route = engineSend ?: run { fail("Automatum isn't available here."); return false }
        if (busy) return false
        busy = true
        var result = true
        work.run({ outcome(play, perform) }) { outcome ->
            busy = false
            result = deliver(outcome, route)
        }
        return result
    }

    /** Works one engine call out from [play]'s flow ([perform]): the viewer's own inputs of it. */
    private fun outcome(play: TableBoard, perform: (SharedFlow) -> EngineRun): Outcome = try {
        val call = perform(flowOf(play))
        // I-D56 step C: only the viewer's own inputs go out; their device folds what follows by itself (the other player's
        // points are assumed, automatic or waited for, never passed for them)
        val own = call.inputs.takeWhile { it !is arcanum.engine.input.Input.MandatoryLoop &&
            !(it is arcanum.engine.input.Input.PassPriority && it.player.value != viewer) }
        if (own.isEmpty()) Outcome.Empty else Outcome.Engine(play.board, own)
    } catch (refused: Refused) {
        Outcome.Fail(refused.message).also { refused.message?.let { AutomatumLog.refusal(play.gameId, "shared flow", it) } }
    } catch (refused: CombatRefused) {
        AutomatumLog.refusal(play.gameId, "combat", refused.reason)
        Outcome.Fail(refused.reason)
    } catch (problem: IllegalArgumentException) {
        AppLog.info("automatum", "Answer refused: ${problem.message?.take(200)}")
        AutomatumLog.refusal(play.gameId, "shared flow", problem.message.orEmpty())
        Outcome.Fail(CastController.NOT_ALLOWED)
    } catch (problem: Exception) {
        AppLog.warn("automatum", "Shared flow couldn't go on: ${problem::class.java.simpleName}: ${problem.message?.take(200)}")
        AutomatumLog.refusal(play.gameId, "shared flow", "${problem::class.java.simpleName}: ${problem.message}")
        Outcome.Fail("Automatum couldn't go on from here.")
    }

    /** [outcome] on the main thread: its inputs go out unless the board moved on meanwhile. True when it was taken. */
    private fun deliver(outcome: Outcome, route: (List<arcanum.engine.input.Input>) -> String?): Boolean = when (outcome) {
        is Outcome.Fail -> { outcome.message?.let(fail); false }
        Outcome.Empty -> { fail(null); picked = emptyList(); true }
        is Outcome.Engine -> when {
            current()?.board !== outcome.board -> { fail("The table changed: try again."); false }
            else -> when (route(outcome.inputs)) {
                null -> { fail(null); picked = emptyList(); true }
                org.asyncttwiz.protocol.game.GameDevice.REVEALS_HIDDEN -> { fail(CastController.WAIT_TO_CATCH_UP); false }
                else -> { fail(CastController.NOT_ALLOWED); false }
            }
        }
    }

    private fun targetOf(flow: SharedFlow, decision: Decision.Targets, item: TableItem): TargetRef? {
        if (item.kind == TableItemKind.LIFE) return decision.options.firstOrNull { it == TargetRef.Player(PlayerId(item.owner ?: return null)) }
        val ids = listOfNotNull(item.sourceId, item.cardId)
        return decision.options.firstOrNull { target ->
            when (val ref = flow.ref(target)) {
                is MtgRef.Card -> ref.cardId in ids
                is MtgRef.StackItem -> flow.board.stackEffects.any { it.id in ids && it.item?.id == ref }
                is MtgRef.Player -> false
            }
        }
    }

    companion object {
        private val PUBLIC_ZONES = setOf(arcanum.api.Zone.Battlefield, arcanum.api.Zone.Stack, arcanum.api.Zone.Graveyard,
            arcanum.api.Zone.Exile, arcanum.api.Zone.Command)
        const val BUSY = "Automatum is waiting for a choice. Answer it first."
        const val STACK_WAITING = "Let the Stack resolve before changing the step."
        /** TRN-027: with Automatum on the first turn begins once both players are Ready. */
        const val OPENING = "The game begins once both players are Ready."
        const val TAKE_OVER = "Automatum can't take over this board."
        const val NOTHING_TO_PASS = "Automatum couldn't go on from here."
        /** TRN-057: steps and turns change only through Automatum while it is on. */
        const val STEPS_FORWARD = "The turn moves on through Automatum: use Next step or End turn."
    }
}

