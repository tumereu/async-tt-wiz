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
import org.asyncttwiz.automatum.EngineBatch
import org.asyncttwiz.automatum.EngineCall
import org.asyncttwiz.automatum.ManaPicks
import org.asyncttwiz.automatum.SharedFlow
import org.asyncttwiz.automatum.Unmappable
import org.asyncttwiz.protocol.*
import java.util.UUID

/**
 * Priority, responding, resolution and triggers through Automatum (integration spec §3 I2b) as shared flows (I-D11):
 * each step goes out as its own automated batch as it happens and the open decision is kept in the game, so both players
 * follow it and either may answer (D11). The player holding priority with something on the Stack is offered "Respond"
 * (cast from the hand, [CastController]) or "Let it resolve" ([pass]); the deciding player answers in the panel; the other
 * player may pass or choose for them after confirming. While a decision is open nothing else changes the game (D32);
 * looking stays free and the eye hides the panel (I-D10).
 *
 * [view] is the shown board's flow, set by the panel's host once worked out ([show]).
 */
internal class SharedFlowController(
    private val viewer: String,
    private val current: () -> PlayState?,
    private val send: ((GameAction.PlayBatch, String) -> Boolean)?,
    private val tags: () -> List<MtgTag?>,
    private val fail: (String?) -> Unit,
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
        val flow = flowOf(play) ?: return false
        if (flow.priority != player) { fail("${if (player == viewer) "You don't" else "They don't"} hold priority now."); return false }
        responding = null
        return sendCall(play) { flow.pass(player) }
    }

    override fun answer(answer: Answer): Boolean {
        val play = current() ?: return false
        val flow = flowOf(play) ?: return false
        val open = flow.decision ?: return false
        if (open.id.value != shownDecision) { fail("The table changed: look at the choice again."); return false }
        return sendCall(play) { flow.answer(answer) }
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
    override val paid: Boolean get() = view?.pays(mana) == true
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

    /** The flow of [play]'s board: the shown one, or worked out now when the board moved on. */
    private fun flowOf(play: PlayState): SharedFlow? {
        view?.takeIf { it.play.board === play.board }?.let { return it }
        return AutomatumBoards.sharedFlow(play, tags()) ?: run { fail("Automatum can't take over this board."); null }
    }

    /** Sends one engine call as one automated batch (I-D11, SYS-010) planned on [play]. */
    private fun sendCall(play: PlayState, perform: () -> EngineCall): Boolean {
        val route = send ?: run { fail("Automatum isn't available here."); return false }
        val call = try { perform() } catch (problem: IllegalArgumentException) {
            AppLog.info("automatum", "Answer refused: ${problem.message?.take(200)}")
            fail(CastController.NOT_ALLOWED); return false
        } catch (problem: IllegalStateException) {
            AppLog.warn("automatum", "Shared flow couldn't go on: ${problem.message?.take(200)}")
            fail("Automatum couldn't go on from here."); return false
        }
        val requestId = UUID.randomUUID().toString()
        val command = GameCommand(requestId, GameScope(play.gameId, playId = play.id),
            GameAction.PlayBatch(listOf(PlayAction.PassAttention)), UUID.randomUUID().toString(), "1")
        val plan = try { EngineBatch.plan(call, viewer, command) } catch (problem: Unmappable) {
            AppLog.warn("automatum", "Shared flow step couldn't be planned: ${problem.message?.take(200)}")
            fail("Automatum couldn't send that."); return false
        }
        fail(null)
        picked = emptyList()
        if (plan.actions.isEmpty()) return true
        if (!route(plan.action, requestId)) { fail("Action could not be queued. Reconnect and try again."); return false }
        return true
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
    }
}
