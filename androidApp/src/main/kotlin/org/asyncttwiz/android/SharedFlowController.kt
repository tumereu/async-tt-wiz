package org.asyncttwiz.android

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
import org.asyncttwiz.automatum.CombatRefused
import org.asyncttwiz.automatum.EngineBatch
import org.asyncttwiz.automatum.EngineCall
import org.asyncttwiz.automatum.ManaPicks
import org.asyncttwiz.automatum.SharedFlow
import org.asyncttwiz.automatum.StepFlow
import org.asyncttwiz.automatum.Unmappable
import org.asyncttwiz.protocol.*
import java.util.UUID

/**
 * Priority, responding, resolution, triggers and the steps of the turn through Automatum (integration spec §3 I2b, I3)
 * as shared flows (I-D11): each step goes out as its own automated batch as it happens and the open decision is kept in
 * the game, so both players follow it and either may answer (D11). The player holding priority with something on the
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
    private val current: () -> PlayState?,
    private val send: ((GameAction.PlayBatch, String) -> Boolean)?,
    private val tags: () -> List<MtgTag?>,
    private val fail: (String?) -> Unit,
    private val work: AutomatumWork = AutomatumWork.Inline,
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

    /** The open decision is targets the viewer picks on the table. */
    val targeting: Boolean get() = answering is Decision.Targets

    fun toggleHidden() { hidden = !hidden }

    /** "Respond": the prompt shrinks to a bar while the player casts from the hand. */
    fun respond(play: PlayState) { responding = play.currentNodeId; hidden = false }
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
     * The step button, the step arrow and the phase dialog with Automatum on (TRN-057): the player holding priority
     * passes [until] as far as asked, whoever pressed it (TRN-004, D11), then Automatum goes on to the next point where a
     * player can act. With attackers or blockers awaited it declares what the combat lanes hold ("End turn" and a step
     * after combat attack with nothing instead). Refused while the Stack holds anything (STK-029) or a choice is open.
     */
    fun step(until: StepFlow.Until): Boolean {
        val play = current() ?: return false
        if (Automatum.flowOpen(play.board)) { fail(BUSY); return false }
        if (CombatStackRules.stack(play.board).cards.isNotEmpty()) { fail(STACK_WAITING); return false }
        return sendCall(play) { flow ->
            val holder = flow.holder
            val declaring = if (holder == null) flow.declaring else null
            val active = flow.state.turn.active.value
            when {
                holder != null -> flow.step(holder, until)
                declaring == null -> throw Refused(NOTHING_TO_PASS)
                declaring == active && until != StepFlow.Until.Pass -> flow.step(active, until)
                else -> flow.declare()
            }
        }
    }

    override fun answer(answer: Answer): Boolean {
        val play = current() ?: return false
        val open = view?.takeIf { it.play.board === play.board }?.decision
        if (open != null && open.id.value != shownDecision) { fail("The table changed: look at the choice again."); return false }
        return sendCall(play) { flow ->
            val decision = flow.decision ?: throw Refused(null)
            if (decision.id.value != shownDecision) throw Refused("The table changed: look at the choice again.")
            flow.answer(answer)
        }
    }

    /** A tap on a table item while the open decision asks for targets (I-D5: players by their life squares). */
    fun tapTarget(item: TableItem): Boolean {
        val decision = answering as? Decision.Targets ?: return false
        val flow = view ?: return false
        val target = targetOf(flow, decision, item) ?: return true.also { fail("That isn't a legal target.") }
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
    fun glow(play: PlayState): Set<String> {
        val flow = view ?: return emptySet()
        val decision = answering as? Decision.Targets ?: return emptySet()
        return targetGlow(play, decision.options.map(flow::ref))
    }

    override val engine: GameState get() = view!!.state
    override fun atwCard(id: ObjectId): String? = view?.atwCard(id)
    override fun listedAnswers(): List<Answer> = view?.listedAnswers().orEmpty()
    /** Worked out once per flow and tapping (one engine call each, never per recomposition, SYS-025). */
    private var paidFor: Triple<SharedFlow, List<ManaUse>, Boolean>? = null
    override val paid: Boolean get() {
        val flow = view ?: return false
        paidFor?.takeIf { it.first === flow && it.second === mana }?.let { return it.third }
        return flow.pays(mana).also { paidFor = Triple(flow, mana, it) }
    }
    /** The tapped sources are a set: their colours are worked out together ([ManaPicks], as in a cast). */
    override fun toggleMana(group: List<ManaUse>) {
        val flow = view ?: return
        val decision = flow.decision as? Decision.ManaPayment ?: return
        mana = ManaPicks.toggle(decision, mana, group, flow::pays)
    }

    /** I-D27: the viewer sees what the rules let them see; the deciding player also the cards their own choice offers
     * (a search). Someone choosing for the other player sees that player's hidden cards only as hidden cards. */
    override fun shown(id: ObjectId): Boolean {
        val flow = view ?: return false
        if (flow.decision?.player?.value == viewer) return true
        val known = visible ?: flow.visible(viewer).also { visible = it }
        return id is ObjectId.StackItem || id in known
    }

    /** The flow of [play]'s board: the shown one, or worked out now when the board moved on (blocking: on [work]). */
    private fun flowOf(play: PlayState): SharedFlow =
        view?.takeIf { it.play.board === play.board } ?: AutomatumBoards.sharedFlow(play, tags()) ?: throw Refused(TAKE_OVER)

    /** A refusal with its message for the player (null: none). */
    private class Refused(message: String?) : Exception(message)

    private sealed interface Outcome {
        class Send(val board: BoardState, val batch: GameAction.PlayBatch, val requestId: String) : Outcome
        class Fail(val message: String?) : Outcome
        data object Empty : Outcome
    }

    /**
     * Works one engine call out on [work] (SYS-025) from [play]'s flow and sends it as one automated batch (I-D11,
     * SYS-010) planned on [play], unless the board moved on meanwhile. True when it was taken (with
     * [AutomatumWork.Inline]: what the result says).
     */
    private fun sendCall(play: PlayState, perform: (SharedFlow) -> EngineCall): Boolean {
        val route = send ?: run { fail("Automatum isn't available here."); return false }
        if (busy) return false
        busy = true
        var result = true
        work.run({
            try {
                val call = perform(flowOf(play))
                val requestId = UUID.randomUUID().toString()
                val command = GameCommand(requestId, GameScope(play.gameId, playId = play.id),
                    GameAction.PlayBatch(listOf(PlayAction.PassAttention)), UUID.randomUUID().toString(), "1")
                val plan = EngineBatch.plan(call, viewer, command)
                if (plan.actions.isEmpty()) Outcome.Empty else Outcome.Send(play.board, plan.action, requestId)
            } catch (refused: Refused) {
                Outcome.Fail(refused.message)
            } catch (refused: CombatRefused) {
                Outcome.Fail(refused.reason)
            } catch (problem: IllegalArgumentException) {
                AppLog.info("automatum", "Answer refused: ${problem.message?.take(200)}")
                Outcome.Fail(CastController.NOT_ALLOWED)
            } catch (problem: Unmappable) {
                AppLog.warn("automatum", "Shared flow step couldn't be planned: ${problem.message?.take(200)}")
                Outcome.Fail("Automatum couldn't send that.")
            } catch (problem: Exception) {
                AppLog.warn("automatum", "Shared flow couldn't go on: ${problem::class.java.simpleName}: ${problem.message?.take(200)}")
                Outcome.Fail("Automatum couldn't go on from here.")
            }
        }) { outcome ->
            busy = false
            result = when (outcome) {
                is Outcome.Fail -> { outcome.message?.let(fail); false }
                Outcome.Empty -> { fail(null); picked = emptyList(); true }
                is Outcome.Send -> when {
                    current()?.board !== outcome.board -> { fail("The table changed: try again."); false }
                    !route(outcome.batch, outcome.requestId) -> { fail("Action could not be queued. Reconnect and try again."); false }
                    else -> { fail(null); picked = emptyList(); true }
                }
            }
        }
        return result
    }

    private fun targetOf(flow: SharedFlow, decision: Decision.Targets, item: TableItem): TargetRef? {
        if (item.kind == TableItemKind.LIFE) return decision.options.firstOrNull { it == TargetRef.Player(PlayerId(item.owner ?: return null)) }
        val ids = listOfNotNull(item.sourceId, item.cardId)
        return decision.options.firstOrNull { target ->
            when (val ref = flow.ref(target)) {
                is MtgRef.Card -> ref.cardId in ids
                is MtgRef.StackItem -> flow.play.board.stackEffects.any { it.id in ids && it.item?.id == ref }
                is MtgRef.Player -> false
            }
        }
    }

    companion object {
        const val BUSY = "Automatum is waiting for a choice. Answer it, or switch Automatum off, first."
        const val STACK_WAITING = "Let the Stack resolve before changing the step."
        const val TAKE_OVER = "Automatum can't take over this board."
        const val NOTHING_TO_PASS = "Automatum couldn't go on from here."
        /** TRN-057: steps and turns change only through Automatum while it is on. */
        const val STEPS_FORWARD = "With Automatum on, the turn moves on through Automatum: use Next step or End turn."
    }
}
