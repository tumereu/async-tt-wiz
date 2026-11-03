package org.asyncttwiz.android

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import arcanum.engine.Engine
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.decision.ManaUse
import arcanum.engine.input.Input
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.state.TargetRef
import org.asyncttwiz.automatum.AutomatumLog
import org.asyncttwiz.automatum.BatchPlan
import org.asyncttwiz.automatum.CastFlow
import org.asyncttwiz.automatum.ManaPicks
import org.asyncttwiz.automatum.Unmappable
import org.asyncttwiz.automatum.Unsupported
import org.asyncttwiz.protocol.*
import java.util.concurrent.Executors

/**
 * Where Automatum's engine work for the player's own actions runs (SYS-025): [Background] on one worker thread with
 * the results back on the main thread in order (the app), [Inline] at once on the caller's thread (tests).
 */
interface AutomatumWork {
    /** Runs [work], then [done] with its result on the main thread. */
    fun <T> run(work: () -> T, done: (T) -> Unit)

    object Inline : AutomatumWork {
        override fun <T> run(work: () -> T, done: (T) -> Unit) = done(work())
    }

    object Background : AutomatumWork {
        private val worker = Executors.newSingleThreadExecutor { Thread(it, "automatum").apply { isDaemon = true } }
        private val main by lazy { Handler(Looper.getMainLooper()) }
        override fun <T> run(work: () -> T, done: (T) -> Unit) {
            worker.execute { val result = work(); main.post { done(result) } }
        }
    }
}

/**
 * Casting from the hand through Automatum (integration spec §3 I2a): one private flow at a time (I-D11). Its engine
 * calls stay on this device ([CastFlow]) and go out as one batch ([send]) when the cast is complete; "Cancel" or Back
 * drops it, sending nothing. While it is open nothing else changes the game ([open] blocks the toolkit's actions, D32);
 * looking stays free (I-D10).
 *
 * The engine work of each step runs on [work] (off the main thread in the app, SYS-025): meanwhile [busy] is true, the
 * panel shows it and takes no input. A step's result applies only if nothing replaced it (Cancel, another step).
 *
 * [current]: the board the player acts on, null while they can't act (the timeline reviewed, offline); the cast follows
 * only that board, never one shown in review. [send]: one automated batch with its request id; [tags]: the history's
 * tags up to [current] (the engine's event log, D33); [fail]: a message for the player.
 */
internal class CastController(
    private val viewer: String,
    private val current: () -> PlayState?,
    private val send: ((GameAction.PlayBatch, String) -> Boolean)?,
    private val tags: () -> List<MtgTag?>,
    private val fail: (String?) -> Unit,
    private val work: AutomatumWork = AutomatumWork.Inline,
    /** A cast or ability is being begun, before the board it begins on is read (#1: it holds the viewer's pause shown,
     * [PauseController.holdForReaction], so it begins on that point's board). */
    private val beginning: () -> Unit = {},
) : FlowQuestions {
    /** The open cast, null when none. */
    var flow by mutableStateOf<CastFlow?>(null); private set
    /** The plays the engine offers for the card being cast (its ways: faces, alternative costs). */
    var ways by mutableStateOf<List<Input>>(emptyList()); private set
    /** The mana uses picked in the mana section (starting from the engine's suggestion). */
    override var mana by mutableStateOf<List<ManaUse>>(emptyList()); private set
    /** Whether the picked mana pays the open payment (worked out with the pick). */
    override var paid by mutableStateOf(false); private set
    /** Targets picked so far for a slot that takes several. */
    var picked by mutableStateOf<List<TargetRef>>(emptyList()); private set
    /** The eye icon (I-D10): the panel is hidden so the board can be looked at. */
    var hidden by mutableStateOf(false); private set
    /** The engine is working out the last step (SYS-025): the panel shows it and takes no input meanwhile. */
    var busy by mutableStateOf(false); private set
    /** "Accept automatically" (D3, this player's own setting on this device): the mana section is skipped when the
     * engine's suggestion pays. */
    var acceptAutomatically by mutableStateOf(false)

    /** The text of the ability being activated (§3 I4), shown in the panel instead of the card's whole text; null for
     * a cast or land play. */
    var abilityText by mutableStateOf<String?>(null); private set

    /** The card being cast while the engine begins the cast (before [flow] is there). */
    private var starting by mutableStateOf<String?>(null)
    private var listed: List<Answer> = emptyList()
    private var visible: Set<ObjectId> = emptySet()
    private var generation = 0

    val open: Boolean get() = flow != null || starting != null
    /** The open question is targets, chosen on the table: the panel is a slim bar. */
    val targeting: Boolean get() = flow?.pending is Decision.Targets

    /**
     * Casts (or plays, a land) the hand card [cardId] the first way the engine offers (ZON-004, ZON-007). False with a
     * message when it can't be begun: a cast is already open, the card is in the other player's hand (I-D27: nothing
     * about it is read or shown), or (once worked out) the engine doesn't allow it now (I-D28).
     */
    fun start(cardId: String): Boolean {
        beginning()
        val play = current() ?: return false
        if (!play.board.automatum) return false
        if (open) { fail(BUSY); return false }
        // A shared flow waits for an answer (D32): nothing is cast meanwhile
        if (Automatum.flowOpen(play.board)) { fail(SharedFlowController.BUSY); return false }
        if (!ownHand(play, cardId, viewer)) { fail(NOT_YOURS); return false }
        val history = tags()
        val accept = acceptAutomatically
        starting = cardId
        hidden = false
        abilityText = null
        return step {
            val definitions = AutomatumBoards.definitions(play)
            val plays = AutomatumBoards.handPlays(play, viewer) { history }
            val ways = plays.of(cardId)
            val name = cardName(play, cardId)
            if (ways.isEmpty()) return@step Step.Drop(if (plays.unsupported.isNotEmpty()) "$TAKE_OVER ${plays.unsupported.first()}"
                else if (land(play, cardId)) "$name can't be played now." else "$name can't be cast now.")
            val flow = try {
                CastFlow.start(play, definitions, viewer, AutomatumBoards.engineLog(play, definitions, history), ways.first(), seeds())
            } catch (problem: IllegalArgumentException) {
                return@step Step.Drop("$name can't be cast now.")
            } catch (problem: Unsupported) {
                return@step Step.Drop("$TAKE_OVER ${problem.reasons.firstOrNull()}")
            }
            settle(flow, auto = true, accept, keep = null, ways = ways)
        }
    }

    /**
     * Begins what a card menu offers (§3 I4, [org.asyncttwiz.automatum.CardPlays]): [input] (a cast one way, a land play,
     * activating an ability or taking a special action) of the card [cardId], through the same panel as a cast, its
     * [text] shown for an ability. False with a message when it can't be begun.
     */
    fun begin(cardId: String, input: Input, text: String? = null): Boolean {
        beginning()
        val play = current() ?: return false
        if (!play.board.automatum) return false
        if (open) { fail(BUSY); return false }
        if (Automatum.flowOpen(play.board)) { fail(SharedFlowController.BUSY); return false }
        val history = tags()
        val accept = acceptAutomatically
        starting = cardId
        hidden = false
        abilityText = text.takeIf { input is Input.Activate }
        return step {
            val definitions = AutomatumBoards.definitions(play)
            val ways = if (input is Input.Activate) listOf(input)
                else AutomatumBoards.handPlays(play, viewer) { history }.of(cardId).takeIf { input in it } ?: listOf(input)
            val name = cardName(play, cardId)
            val flow = try {
                CastFlow.start(play, definitions, viewer, AutomatumBoards.engineLog(play, definitions, history), input, seeds())
            } catch (problem: IllegalArgumentException) {
                return@step Step.Drop(if (input is Input.Activate) "$name: that can't be done now." else "$name can't be cast now.")
            } catch (problem: Unsupported) {
                return@step Step.Drop("$TAKE_OVER ${problem.reasons.firstOrNull()}")
            }
            settle(flow, auto = true, accept, keep = null, ways = ways)
        }
    }

    /** Answers the open question. */
    override fun answer(answer: Answer): Boolean = update { it.answer(answer) }
    override val engine get() = flow!!.state
    override fun atwCard(id: ObjectId): String? = flow?.atwCard(id)
    override fun listedAnswers(): List<Answer> = listed
    /** I-D27: the cast runs on full information; the panel names only what the caster may see. */
    override fun shown(id: ObjectId): Boolean = flow != null && (id is ObjectId.StackItem || id in visible)

    /** Changes the answer of an earlier section (a toggle above the current one). */
    fun change(index: Int, answer: Answer): Boolean = update { it.change(index, answer) }

    /** Opens the decision of an earlier section again (its answer and the later ones are dropped): "Change" on a
     * section whose answer is chosen on the table (targets). */
    fun back(index: Int): Boolean = update { it.back(index) }

    /** Another way of casting the card (face, alternative cost), keeping the choices that still fit. */
    fun way(input: Input): Boolean = update { it.withBegin(input) }

    /** A tap on a table item while targets are chosen (I-D5: players by their life squares). True when it was taken. */
    fun tapTarget(item: TableItem): Boolean {
        val f = flow ?: return false
        val decision = f.pending as? Decision.Targets ?: return false
        if (busy) return true
        val target = targetOf(f, decision, item) ?: return true.also { fail("That isn't a legal target.") }
        fail(null)
        val max = decision.max
        if (max == 1 && decision.min <= 1) return answer(Answer.Targets(listOf(target)))
        picked = if (target in picked) picked - target else picked + target
        if (max != null && picked.size >= max) return confirmTargets()
        return true
    }

    /** Confirms the targets picked for a slot that takes several. */
    fun confirmTargets(): Boolean {
        val decision = flow?.pending as? Decision.Targets ?: return false
        if (picked.size < decision.min) return false
        return answer(Answer.Targets(picked))
    }

    /** Taps or untaps a mana source in the mana section. The tapped sources are a set: their colours are worked out
     * together so they pay whenever they can, whatever order they were tapped in ([ManaPicks]). */
    override fun toggleMana(group: List<ManaUse>) {
        val f = flow ?: return
        val decision = f.pending as? Decision.ManaPayment ?: return
        if (busy) return
        val current = mana
        step {
            val uses = ManaPicks.toggle(decision, current, group, f::pays)
            Step.Mana(f, uses, f.pays(uses))
        }
    }

    /** "Cast": pays with the picked mana (when the cast is at its payment) and sends the whole flow. Nothing is sent
     * while the player can't act (the timeline reviewed, offline): the cast stays open. */
    fun cast(): Boolean {
        val f = flow ?: return false
        if (busy) return false
        val play = current() ?: run { fail(CANT_ACT); return false }
        if (!play.board.automatum) { cancel("dropped: switched off"); fail(SWITCHED_OFF); return false }
        if (f.pending is Decision.ManaPayment) { val uses = mana; return update { it.answer(Answer.ManaPayment(uses)) } }
        if (!f.complete) return false
        return step { plan(f) }
    }

    /** "Cancel" or Back: the whole cast is dropped; nothing was sent (I-D11). A step still being worked out is dropped
     * with it. */
    fun cancel(how: String = "cancelled") {
        // I-D43: how each cast flow ended
        if (flow != null || starting != null) (flow?.play?.gameId ?: current()?.gameId)?.let {
            AutomatumLog.flow(it, "cast", how, flow?.cardId ?: starting.orEmpty())
        }
        generation++
        busy = false; starting = null
        flow = null; ways = emptyList(); mana = emptyList(); paid = false; picked = emptyList(); hidden = false; abilityText = null
        listed = emptyList(); visible = emptySet()
    }

    fun toggleHidden() { hidden = !hidden }

    /** The table items that glow as legal targets of the open question: cards by id (and the Graveyard or Exile pile
     * holding one, opened to pick it), players by their life square. */
    fun glow(play: PlayState): Set<String> {
        val f = flow ?: return emptySet()
        val decision = f.pending as? Decision.Targets ?: return emptySet()
        return targetGlow(play, decision.options.map(f::ref))
    }

    /** The Stack entries that are legal targets of the open question, top first (picked in Manage Stack). */
    fun stackTargets(play: PlayState): List<String> {
        val f = flow ?: return emptyList()
        val decision = f.pending as? Decision.Targets ?: return emptyList()
        return targetEntries(play, decision.options.map(f::ref))
    }

    /**
     * The board the player acts on may have changed under the open cast (D32: the other player acted, an Undo): the cast
     * is worked out again on it, keeping the choices that still fit, the player's own mana tapping included while its
     * sources are still there; dropped with a message when it can't be cast there any more, or when Automatum was
     * switched off (I-D24). Only the board the player acts on counts: while the timeline is reviewed or the connection
     * is down nothing changes.
     */
    fun follow() {
        val f = flow ?: return
        // Checked again once the step being worked out is done
        if (busy) return
        val play = current() ?: return
        if (f.play.id != play.id) return
        if (!play.board.automatum) { cancel("dropped: switched off"); fail(SWITCHED_OFF); return }
        if (f.play.board === play.board) return
        val history = tags()
        val keep = mana
        val accept = acceptAutomatically
        step {
            try {
                val definitions = AutomatumBoards.definitions(play)
                settle(f.on(play, AutomatumBoards.engineLog(play, definitions, history)), auto = false, accept, keep, clear = false)
            } catch (problem: Exception) {
                Step.Drop("The table changed: ${cardName(play, f.cardId)} can't be cast now.")
            }
        }
    }

    private sealed interface Step {
        /** [flow] with its open question shown; [ways] set when the cast was just begun. */
        class Show(val flow: CastFlow, val mana: List<ManaUse>, val paid: Boolean, val listed: List<Answer>,
            val visible: Set<ObjectId>, val ways: List<Input>?, val clear: Boolean) : Step
        class Mana(val flow: CastFlow, val mana: List<ManaUse>, val paid: Boolean) : Step
        /** The complete [flow] planned as one batch on [board]. */
        class Send(val flow: CastFlow, val plan: BatchPlan, val requestId: String, val board: BoardState) : Step
        /** The cast is dropped with [message]. */
        class Drop(val message: String) : Step
        /** The step is refused with [message]; the cast stays as it was. */
        class Refuse(val message: String) : Step
    }

    /** Runs [work] on [AutomatumWork] and applies its result unless something replaced it meanwhile. True when it was
     * taken (with [AutomatumWork.Inline]: what the result says). */
    private fun step(work: () -> Step): Boolean {
        val run = ++generation
        busy = true
        var result = true
        this.work.run({
            try { work() } catch (problem: Exception) {
                AppLog.warn("automatum", "Cast step failed: ${problem::class.java.simpleName}: ${problem.message?.take(200)}", problem)
                current()?.gameId?.let { AutomatumLog.refusal(it, "cast", "${problem::class.java.simpleName}: ${problem.message}") }
                Step.Drop(FAILED)
            }
        }) { outcome ->
            if (run != generation) return@run
            busy = false
            result = apply(outcome)
        }
        return result
    }

    private fun apply(outcome: Step): Boolean = when (outcome) {
        is Step.Show -> {
            starting = null
            outcome.ways?.let { ways = it }
            if (outcome.ways != null) AutomatumLog.flow(outcome.flow.play.gameId, "cast", "started", outcome.flow.cardId)
            listed = outcome.listed; visible = outcome.visible
            flow = outcome.flow; mana = outcome.mana; paid = outcome.paid; picked = emptyList()
            if (outcome.clear) fail(null)
            follow()
            true
        }
        is Step.Mana -> {
            if (flow === outcome.flow) { mana = outcome.mana; paid = outcome.paid }
            follow()
            true
        }
        is Step.Send -> sendPlanned(outcome)
        is Step.Drop -> { cancel("dropped: ${outcome.message}"); fail(outcome.message); false }
        is Step.Refuse -> { fail(outcome.message); follow(); false }
    }

    /** Sends a planned cast, on the board the player acts on now: worked out again when that board moved on meanwhile;
     * kept, ready to cast, while the player can't act; dropped when Automatum was switched off (I-D24). */
    private fun sendPlanned(planned: Step.Send): Boolean {
        val play = current()
        if (play == null) { showReady(planned.flow); fail(CANT_ACT); return false }
        if (!play.board.automatum) { cancel("dropped: switched off"); fail(SWITCHED_OFF); return false }
        if (play.board !== planned.board) {
            val history = tags()
            starting = null
            return step {
                val ready = try {
                    planned.flow.on(play, AutomatumBoards.engineLog(play, AutomatumBoards.definitions(play), history)).takeIf { it.complete }
                } catch (_: Exception) { null } ?: return@step Step.Drop("The table changed: cast it again.")
                plan(ready)
            }
        }
        val route = send ?: run { cancel("dropped: no route"); fail("Casting isn't available here."); return false }
        cancel("sent")
        if (planned.plan.actions.isEmpty()) return true
        if (!route(planned.plan.action, planned.requestId)) { fail("Action could not be queued. Reconnect and try again."); return false }
        return true
    }

    /** [done] (complete) shown ready to cast, after a send that couldn't go out yet. */
    private fun showReady(done: CastFlow) {
        starting = null
        if (flow == null || flow?.cardId == done.cardId) { flow = done; mana = emptyList(); paid = false; picked = emptyList() }
    }

    /** Applies [change] to the open cast: shown with its open question, or sent when nothing is left to decide. */
    private fun update(change: (CastFlow) -> CastFlow): Boolean {
        val f = flow ?: return false
        if (busy) return false
        val accept = acceptAutomatically
        return step {
            val next = try { change(f) } catch (problem: IllegalArgumentException) {
                AppLog.info("automatum", "Cast step refused: ${problem.message?.take(200)}")
                AutomatumLog.refusal(f.play.gameId, "cast step", problem.message.orEmpty())
                return@step Step.Refuse(NOT_ALLOWED)
            }
            settle(next, auto = true, accept, keep = null)
        }
    }

    /** What [next] leads to (worked out off the main thread): sent at once when nothing is left to decide (or only a
     * payment its suggestion makes, with "Accept automatically" on); else shown with its open question, the mana picked
     * being [keep] while all of it is still offered (the player's own tapping, STK-025) or else the suggestion. */
    private fun settle(next: CastFlow, auto: Boolean, accept: Boolean, keep: List<ManaUse>?, ways: List<Input>? = null,
        clear: Boolean = true): Step {
        val payment = next.pending as? Decision.ManaPayment
        if (auto && next.complete) return plan(next)
        if (auto && payment != null && accept && payment.suggestion.isNotEmpty() && next.pays(payment.suggestion)) {
            try { return plan(next.answer(Answer.ManaPayment(payment.suggestion))) } catch (_: IllegalArgumentException) { }
        }
        val mana = keep?.takeIf { k -> payment != null && k.isNotEmpty() && k.all { it in payment.options } } ?: payment?.suggestion.orEmpty()
        val pending = next.pending
        val listed = if (pending != null && pending !is Decision.Targets && pending !is Decision.ManaPayment) next.listedAnswers() else emptyList()
        return Step.Show(next, mana, payment != null && next.pays(mana), listed, Engine.visible(next.state, PlayerId(viewer)), ways, clear)
    }

    /** The complete [done] as one batch (I-D11, SYS-010) on the board it was worked out on. */
    private fun plan(done: CastFlow): Step {
        val requestId = DeviceRandom.uuid()
        val command = GameCommand(requestId, GameScope(done.play.gameId, playId = done.play.id),
            GameAction.PlayBatch(listOf(PlayAction.PassAttention)), DeviceRandom.uuid(), "1")
        return try { Step.Send(done, done.plan(viewer, command), requestId, done.play.board) } catch (problem: Unmappable) {
            AppLog.warn("automatum", "Cast couldn't be planned: ${problem.message?.take(200)}")
            Step.Drop("Automatum couldn't send that cast.")
        }
    }

    private fun targetOf(f: CastFlow, decision: Decision.Targets, item: TableItem): TargetRef? {
        if (item.kind == TableItemKind.LIFE) return decision.options.firstOrNull { it == TargetRef.Player(PlayerId(item.owner ?: return null)) }
        val play = f.play
        val ids = listOfNotNull(item.sourceId, item.cardId)
        return decision.options.firstOrNull { target ->
            when (val ref = f.ref(target)) {
                is MtgRef.Card -> ref.cardId in ids
                is MtgRef.StackItem -> play.board.stackEffects.any { it.id in ids && it.item?.id == ref }
                is MtgRef.Player -> false
            }
        }
    }

    private fun seeds() = CastFlow.Seeds { DeviceRandom.seed() }

    companion object {
        const val BUSY = "Finish or cancel the cast first."
        const val SWITCHED_OFF = "Automatum was switched off: the cast was dropped."
        const val CANT_ACT = "You can't cast right now (reviewing the timeline, or offline). The cast stays open."
        /** I-D27: the same for every card in the other player's hand, whatever it is. */
        const val NOT_YOURS = "Only its owner can cast that card."
        const val NOT_ALLOWED = "Automatum doesn't allow that choice."
        const val FAILED = "Automatum couldn't work that cast out."
        const val TAKE_OVER = "Automatum can't take over this board:"

        fun cardName(play: PlayState, cardId: String): String {
            val card = play.board.cards.singleOrNull { it.id == cardId } ?: return "That card"
            return play.definitions.singleOrNull { it.digest == card.definition }?.definition?.faces
                ?.let { faces -> faces.singleOrNull { it.faceId == card.faceId } ?: faces.firstOrNull() }?.name ?: "That card"
        }

        fun land(play: PlayState, cardId: String): Boolean {
            val card = play.board.cards.singleOrNull { it.id == cardId } ?: return false
            val front = play.definitions.singleOrNull { it.digest == card.definition }?.definition?.faces?.firstOrNull()
            return "Land" in StackContexts.types(front?.typeLine.orEmpty())
        }

        /** Whether [cardId] is in [viewer]'s own hand. */
        fun ownHand(play: PlayState, cardId: String, viewer: String): Boolean {
            val card = play.board.cards.singleOrNull { it.id == cardId } ?: return false
            return play.board.containers.any { it.id == card.containerId && it.zone == PlayZone.HAND && it.accountId == viewer }
        }

        /** The hand menu's Play with Automatum on (ZON-007): "Play" for a land, "Cast" for anything else, and "Cast" for a
         * card [viewer] can't see face up, whatever it is (I-D27). */
        fun playLabel(play: PlayState, cardId: String, viewer: String): String {
            val card = play.board.cards.singleOrNull { it.id == cardId } ?: return "Cast"
            if (!CardVisibility.isVisibleFaceUp(play.board, card, viewer)) return "Cast"
            return if (land(play, cardId)) "Play" else "Cast"
        }
    }
}

/** The table items that glow for the legal targets [refs] (I-D6): cards by id, a Graveyard or Exile pile holding one
 * (tapped, it opens to pick from, i12review #14), Stack entries by id, and the Stack itself when an entry below the top
 * is one (tapped, Manage Stack opens to pick from), players by their life square. */
internal fun targetGlow(play: PlayState, refs: List<MtgRef>): Set<String> = refs.flatMap { ref -> when (ref) {
    is MtgRef.Player -> listOf("life/${ref.accountId}")
    is MtgRef.Card -> listOf(ref.cardId) + listOfNotNull(play.board.cards.singleOrNull { it.id == ref.cardId }?.containerId
        ?.takeIf { id -> play.board.containers.any { it.id == id && it.zone in PublicPiles.zones } })
    is MtgRef.StackItem -> listOfNotNull(play.board.stackEffects.firstOrNull { it.item?.id == ref }?.id)
} }.toSet() + listOfNotNull(CombatStackRules.stack(play.board).takeIf { stack ->
    targetEntries(play, refs).any { it != stack.cards.firstOrNull() } }?.id)

/** The Stack entries that are legal targets among [refs]: a spell by its card (its item's object), an ability by its
 * item (I-D6), top first. */
internal fun targetEntries(play: PlayState, refs: List<MtgRef>): List<String> {
    val wanted = refs.filter { it !is MtgRef.Player }.toSet()
    val entries = play.board.stackEffects.associateBy { it.id }
    return CombatStackRules.stack(play.board).cards.filter { id -> entries[id]?.item?.id in wanted }
}
