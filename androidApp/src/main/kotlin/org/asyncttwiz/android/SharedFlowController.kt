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
import org.asyncttwiz.automatum.AutomatumLog
import org.asyncttwiz.automatum.CombatFlow
import org.asyncttwiz.automatum.CombatRefused
import org.asyncttwiz.automatum.EngineBatch
import org.asyncttwiz.automatum.EngineCall
import org.asyncttwiz.automatum.ManaPicks
import org.asyncttwiz.automatum.SharedFlow
import org.asyncttwiz.automatum.StepFlow
import org.asyncttwiz.automatum.Unmappable
import org.asyncttwiz.protocol.*

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
    /** "Respond" was tapped (#2: it holds the viewer's pause, [PauseController.holdForReaction]). */
    private val onRespond: () -> Unit = {},
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
    val targeting: Boolean get() = answering is Decision.Targets && view?.play?.board === current()?.board

    fun toggleHidden() { hidden = !hidden }

    /** "Respond": the prompt shrinks to a bar while the player casts from the hand. */
    fun respond(play: PlayState) { onRespond(); responding = play.currentNodeId; hidden = false }
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
     * I-D41 (STK-037, STK-038): the pause on the board [node] is over: [holder] passes priority, then Automatum goes on, as
     * one batch from this device (the one whose step made the pause, or the holder's own). Quiet: nothing is said when it
     * can't be sent (the table moved on, another answer is being worked out). False when nothing was begun (busy, the
     * table moved on); [sent] then says, once the pass is worked out, whether it went out (finalreview-combat #6: with
     * the work off the main thread the return value can't tell).
     */
    fun autoPass(holder: String, node: String, sent: (Boolean) -> Unit = {}): Boolean {
        val play = current()?.takeIf { it.currentNodeId == node } ?: return false
        if (Automatum.flowOpen(play.board)) return false
        return sendCall(play, quiet = true, sent = sent) { flow ->
            if (flow.holder != holder) throw Refused(null)
            flow.pass(holder)
        }
    }

    /** I-D40: a step the viewer asked for that would act for the other player, shown for confirmation ([confirmActing]);
     * null when none waits. */
    var acting by mutableStateOf<ActingFor?>(null); private set

    /**
     * The step button, the step arrow and the phase dialog with Automatum on (TRN-057): the player holding priority
     * passes [until] as far as asked, whoever pressed it (TRN-004, D11), then Automatum goes on to the next point where a
     * player can act. With attackers or blockers awaited it declares what the combat lanes hold ("End turn" and a step
     * after combat attack with nothing instead). With something on the Stack [StepFlow.Until.Pass] is "Resolve": the
     * holder passes as with Let it resolve (I-D46 d); anything further is refused (STK-029), as while a choice is open.
     * When that would act for the other player nothing is sent: [acting] asks the viewer first (I-D40) and
     * [confirmActing] goes on ([confirmed]: the player the viewer agreed to act for).
     */
    fun step(until: StepFlow.Until, confirmed: String? = null): Boolean {
        val play = current() ?: return false
        if (Automatum.flowOpen(play.board)) { fail(BUSY); return false }
        // I-D46 d: with something on the Stack the step arrow is "Resolve": its holder passes, as Let it resolve does
        val stacked = CombatStackRules.stack(play.board).cards.isNotEmpty()
        if (stacked && until != StepFlow.Until.Pass) { fail(STACK_WAITING); return false }
        // TRN-027, TRN-028: the opening (mulligans) is the host's setup; the engine's game begins after it (CR 103.8)
        if (play.board.turn.number == "1" && PlayerStatus.anyOpening(play.board)) { fail(OPENING); return false }
        acting = null
        return sendCall(play) { flow ->
            if (stacked) {
                val holder = flow.priority ?: throw Refused(STACK_WAITING)
                if (holder != viewer && holder != confirmed) throw Ask(ActingFor(play.board, holder, until, ActingFor.Kind.Pass(priority = true)))
                return@sendCall flow.pass(holder)
            }
            val holder = flow.holder
            val declaring = if (holder == null) flow.declaring else null
            val active = flow.state.turn.active.value
            val actor = holder ?: declaring ?: throw Refused(NOTHING_TO_PASS)
            val declares = holder == null && !(declaring == active && until != StepFlow.Until.Pass)
            // I-D40: acting for the other player asks first
            if (actor != viewer && actor != confirmed) throw Ask(ActingFor(play.board, actor, until, when {
                !declares -> ActingFor.Kind.Pass(priority = holder != null)
                declaring == active -> ActingFor.Kind.DeclareAttackers(CombatFlow.lanes(play.board.combat).first.size)
                else -> ActingFor.Kind.DeclareBlockers(CombatFlow.lanes(play.board.combat).second.map { it.blockerId }.distinct().size)
            }))
            when {
                holder != null -> flow.step(holder, until)
                !declares -> flow.step(active, until)
                else -> flow.declare()
            }
        }
    }

    /** I-D40: the viewer confirmed [acting]: the step goes on for that player, unless the table changed meanwhile. */
    fun confirmActing(): Boolean {
        val asked = acting ?: return false
        acting = null
        if (current()?.board !== asked.board) { fail("The table changed: try again."); return false }
        return asked.go?.invoke(asked.player) ?: step(asked.until, confirmed = asked.player)
    }

    /** I-D40: asks the viewer to confirm [asked], a step for the other player made by another route (No blocks). */
    fun ask(asked: ActingFor) { fail(null); acting = asked }

    /** I-D40: the viewer cancelled [acting]; nothing is sent. */
    fun cancelActing() { acting = null }

    override fun answer(answer: Answer): Boolean = answerWith(answer, emptyList())

    /** I-D48: a two-area choice's answer and the orders the engine then asks of the same cards, as one step
     * ([SharedFlow.answer]). */
    override fun answerWith(answer: Answer, then: List<Answer.Objects>): Boolean {
        val play = current() ?: return false
        val open = view?.takeIf { it.play.board === play.board }?.decision
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

    /** The Stack entries that are legal targets of the open decision the viewer answers, top first. */
    fun stackTargets(play: PlayState): List<String> {
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
    private fun flowOf(play: PlayState): SharedFlow =
        view?.takeIf { it.play.board === play.board } ?: AutomatumBoards.sharedFlow(play, tags()) ?: throw Refused(TAKE_OVER)

    /** A refusal with its message for the player (null: none). */
    private class Refused(message: String?) : Exception(message)

    /** I-D40: the call would act for the other player; the viewer is asked first. */
    private class Ask(val acting: ActingFor) : Exception()

    private sealed interface Outcome {
        class Send(val board: BoardState, val batch: GameAction.PlayBatch, val requestId: String) : Outcome
        class Fail(val message: String?) : Outcome
        class Confirm(val acting: ActingFor) : Outcome
        data object Empty : Outcome
    }

    /**
     * Works one engine call out on [work] (SYS-025) from [play]'s flow and sends it as one automated batch (I-D11,
     * SYS-010) planned on [play], unless the board moved on meanwhile. True when it was taken (with
     * [AutomatumWork.Inline]: what the result says). [sent]: whether a batch went out, once known (on the main thread).
     */
    private fun sendCall(play: PlayState, quiet: Boolean = false, sent: (Boolean) -> Unit = {}, perform: (SharedFlow) -> EngineCall): Boolean {
        val fail: (String?) -> Unit = if (quiet) { _ -> } else this.fail
        val route = send ?: run { fail("Automatum isn't available here."); return false }
        if (busy) return false
        busy = true
        var result = true
        work.run({
            try {
                val call = perform(flowOf(play))
                val requestId = DeviceRandom.uuid()
                val command = GameCommand(requestId, GameScope(play.gameId, playId = play.id),
                    GameAction.PlayBatch(listOf(PlayAction.PassAttention)), DeviceRandom.uuid(), "1")
                val plan = EngineBatch.plan(call, viewer, command)
                if (plan.actions.isEmpty()) Outcome.Empty else Outcome.Send(play.board, plan.action, requestId)
            } catch (ask: Ask) {
                Outcome.Confirm(ask.acting)
            } catch (refused: Refused) {
                Outcome.Fail(refused.message).also { refused.message?.let { AutomatumLog.refusal(play.gameId, "shared flow", it) } }
            } catch (refused: CombatRefused) {
                AutomatumLog.refusal(play.gameId, "combat", refused.reason)
                Outcome.Fail(refused.reason)
            } catch (problem: IllegalArgumentException) {
                AppLog.info("automatum", "Answer refused: ${problem.message?.take(200)}")
                AutomatumLog.refusal(play.gameId, "shared flow", problem.message.orEmpty())
                Outcome.Fail(CastController.NOT_ALLOWED)
            } catch (problem: Unmappable) {
                AppLog.warn("automatum", "Shared flow step couldn't be planned: ${problem.message?.take(200)}")
                Outcome.Fail("Automatum couldn't send that.")
            } catch (problem: Exception) {
                AppLog.warn("automatum", "Shared flow couldn't go on: ${problem::class.java.simpleName}: ${problem.message?.take(200)}")
                AutomatumLog.refusal(play.gameId, "shared flow", "${problem::class.java.simpleName}: ${problem.message}")
                Outcome.Fail("Automatum couldn't go on from here.")
            }
        }) { outcome ->
            busy = false
            result = when (outcome) {
                is Outcome.Fail -> { outcome.message?.let(fail); false }
                is Outcome.Confirm -> { fail(null); acting = outcome.acting.takeIf { current()?.board === it.board }; false }
                Outcome.Empty -> { fail(null); picked = emptyList(); true }
                is Outcome.Send -> when {
                    current()?.board !== outcome.board -> { fail("The table changed: try again."); false }
                    !route(outcome.batch, outcome.requestId) -> { fail("Action could not be queued. Reconnect and try again."); false }
                    else -> { fail(null); picked = emptyList(); true }
                }
            }
            sent(outcome is Outcome.Send && result)
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
        private val PUBLIC_ZONES = setOf(arcanum.api.Zone.Battlefield, arcanum.api.Zone.Stack, arcanum.api.Zone.Graveyard,
            arcanum.api.Zone.Exile, arcanum.api.Zone.Command)
        const val BUSY = "Automatum is waiting for a choice. Answer it, or switch Automatum off, first."
        const val STACK_WAITING = "Let the Stack resolve before changing the step."
        /** TRN-027: with Automatum on the first turn begins once both players are Ready. */
        const val OPENING = "The game begins once both players are Ready."
        const val TAKE_OVER = "Automatum can't take over this board."
        const val NOTHING_TO_PASS = "Automatum couldn't go on from here."
        /** TRN-057: steps and turns change only through Automatum while it is on. */
        const val STEPS_FORWARD = "With Automatum on, the turn moves on through Automatum: use Next step or End turn."
    }
}

/**
 * I-D40: a step a player asked for that acts for the other [player] (who holds priority or must declare), waiting for
 * the asking player's confirmation ([SharedFlowController.acting]); planned on [board], so it goes on only while the
 * table is still that board. [go] does it once confirmed (given the player agreed to); null: the step button's step.
 */
internal class ActingFor(val board: BoardState, val player: String, val until: StepFlow.Until, val kind: Kind,
    val go: ((String) -> Boolean)? = null) {
    sealed interface Kind {
        /** [player] passes as far as [until] asks: holding [priority], or else with their attack awaited (declared empty). */
        data class Pass(val priority: Boolean) : Kind
        /** [player]'s attack is declared as the combat lanes hold it: [count] attackers. */
        data class DeclareAttackers(val count: Int) : Kind
        /** [player]'s blocks are declared as the combat lanes hold them: [count] blockers. */
        data class DeclareBlockers(val count: Int) : Kind
        /** "No blocks" (CMB-034): the blocks in the lanes are cleared and [player] declares no blocks. */
        data object NoBlocks : Kind
    }
}
