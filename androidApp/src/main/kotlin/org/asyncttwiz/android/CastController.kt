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
import org.asyncttwiz.automatum.CastFlow
import org.asyncttwiz.automatum.ManaPicks
import org.asyncttwiz.protocol.*
import java.util.concurrent.Executors

/**
 * Where Automatum's engine work for the player's own actions runs (SYS-025): [Background] on one worker thread with
 * the results back on the main thread in order (the app), [Inline] at once on the caller's thread (tests).
 */
interface AutomatumWork {
    /** Runs [work], then [done] with its result on the main thread. */
    fun <T> run(work: () -> T, done: (T) -> Unit)

    /** Runs [first], then [shown] with its result on the main thread while the work [next] gives for it (null: none)
     * goes on at once (nothing queued meanwhile runs in between, I-D47), then [done] with that. */
    fun <A, B> staged(first: () -> A, shown: (A) -> Unit, next: (A) -> (() -> B)?, done: (B) -> Unit) =
        run(first) { a -> shown(a); next(a)?.let { run(it, done) } }

    object Inline : AutomatumWork {
        override fun <T> run(work: () -> T, done: (T) -> Unit) = done(work())
    }

    object Background : AutomatumWork {
        private val worker = Executors.newSingleThreadExecutor { Thread(it, "automatum").apply { isDaemon = true } }
        private val main by lazy { Handler(Looper.getMainLooper()) }
        override fun <T> run(work: () -> T, done: (T) -> Unit) {
            worker.execute { val result = work(); main.post { done(result) } }
        }
        override fun <A, B> staged(first: () -> A, shown: (A) -> Unit, next: (A) -> (() -> B)?, done: (B) -> Unit) {
            worker.execute {
                val a = first()
                main.post { shown(a) }
                val second = next(a) ?: return@execute
                val b = second()
                main.post { done(b) }
            }
        }
    }
}

/**
 * Casting from the hand through Automatum (integration spec §3 I2a): one private flow at a time (I-D11). Its engine
 * calls stay on this device ([CastFlow]) and go out as the player's own inputs ([engineSend]) when the cast is complete; "Cancel" or Back
 * drops it, sending nothing. While it is open nothing else changes the game ([open] blocks the toolkit's actions, D32);
 * looking stays free (I-D10).
 *
 * The engine work of each step runs on [work] (off the main thread in the app, SYS-025): meanwhile [busy] is true, the
 * panel shows it and takes no input. A step's result applies only if nothing replaced it (Cancel, another step).
 *
 * [current]: the board the player acts on, null while they can't act (the timeline reviewed, offline); the cast follows
 * only that board, never one shown in review. [fail]: a message for the player.
 */
internal class CastController(
    private val viewer: String,
    private val current: () -> TableBoard?,
    private val fail: (String?) -> Unit,
    private val work: AutomatumWork = AutomatumWork.Inline,
    /** The pictures of tokens a cast makes (§4.6). */
    private val tokenArt: org.asyncttwiz.automatum.TokenArt = org.asyncttwiz.automatum.TokenArt.None,
    /** UI review round 2 #4 (I-D46 b): shakes a card on the table, for a cast that can't go on ([visual]). */
    private val shake: (String) -> Unit = {},
    /** I-D56 step C: sends the complete cast as the player's own inputs (null: sent; else why not), and the device does
     * what follows by itself (null: casting isn't available here). */
    private val engineSend: ((List<Input>) -> String?)? = null,
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
    /** Why the last step in the open panel was refused ("Not enough mana to pay {5}{R}."), shown inside the panel (UI
     * review round 2 #4: no message over the table); null when none. */
    var notice by mutableStateOf<String?>(null); private set
    /** "Autotap mana sources" (I-D47, Settings → Automatum, on by default): a payment is made with the engine's suggestion
     * (mana in the pool first) and never asked; off, the mana section lets the player choose the sources. */
    var autotap by mutableStateOf(true)

    /** The text of the ability being activated (§3 I4), shown in the panel instead of the card's whole text; null for
     * a cast or land play. */
    var abilityText by mutableStateOf<String?>(null); private set

    /** The card being cast while the engine begins the cast (before [flow] is there). */
    private var starting by mutableStateOf<String?>(null)
    private var listed: List<Answer> = emptyList()
    private var visible: Set<ObjectId> = emptySet()
    private var generation = 0

    val open: Boolean get() = flow != null || starting != null
    /** The card being cast (or played, or whose ability is activated) while a cast is [open]. */
    val castCard: String? get() = flow?.cardId ?: starting
    /** The open question is targets, chosen on the table: the panel is a slim bar. */
    val targeting: Boolean get() = flow?.pending is Decision.Targets

    /**
     * Casts (or plays, a land) the hand card [cardId] [way] (the first way the engine offers when null, ZON-004) with the
     * optional additional costs [extras] chosen with its cost chips (I-D50, ZON-080: times each is paid, in order; the
     * cast never asks them). False with a message when it can't be begun: a cast is already open, the card is in the
     * other player's hand (I-D27: nothing about it is read or shown), or (once worked out) the engine doesn't allow it
     * now (I-D28).
     */
    fun start(cardId: String, way: Input? = null, extras: List<Int> = emptyList()): Boolean {
        val play = current() ?: return false
        if (open) { fail(BUSY); return false }
        // A shared flow waits for an answer (D32): nothing is cast meanwhile
        if (play.flowOpen) { fail(SharedFlowController.BUSY); return false }
        if (!ownHand(play, cardId, viewer)) { fail(NOT_YOURS); return false }
        val autotapOn = autotap
        starting = cardId
        hidden = false
        abilityText = null
        return step {
            val plays = AutomatumBoards.handPlays(play, viewer)
            val offered = plays.of(cardId)
            val name = cardName(play, cardId)
            val begin = way ?: offered.firstOrNull()
            if (begin == null) return@step Step.Drop(if (land(play, cardId)) "$name can't be played now." else "$name can't be cast now.")
            val flow = try {
                CastFlow.start(source(play), viewer, begin, seeds(), extras = extras)
            } catch (problem: IllegalArgumentException) {
                return@step Step.Drop("$name can't be cast now.")
            }
            // I-D50: the way was chosen with the cost chips: the panel has no "Cast as"
            settle(flow, auto = true, autotapOn, keep = null, ways = listOf(begin))
        }
    }

    /**
     * Begins what a card menu offers (§3 I4, [org.asyncttwiz.automatum.CardPlays]): [input] (a cast one way, a land play,
     * activating an ability or taking a special action) of the card [cardId], through the same panel as a cast, its
     * [text] shown for an ability. False with a message when it can't be begun.
     */
    fun begin(cardId: String, input: Input, text: String? = null): Boolean {
        val play = current() ?: return false
        if (open) { fail(BUSY); return false }
        if (play.flowOpen) { fail(SharedFlowController.BUSY); return false }
        val autotapOn = autotap
        starting = cardId
        hidden = false
        abilityText = text.takeIf { input is Input.Activate }
        return step {
            val ways = if (input is Input.Activate) listOf(input)
                else AutomatumBoards.handPlays(play, viewer).of(cardId).takeIf { input in it } ?: listOf(input)
            val name = cardName(play, cardId)
            val flow = try {
                CastFlow.start(source(play), viewer, input, seeds())
            } catch (problem: IllegalArgumentException) {
                return@step Step.Drop(if (input is Input.Activate) "$name: that can't be done now." else "$name can't be cast now.")
            }
            settle(flow, auto = true, autotapOn, keep = null, ways = ways)
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

    /** A tap on a table item while targets are chosen (I-D5: players by their life squares). True when it was taken;
     * false when it isn't a legal target (the table shakes it, I-D46 b) or nothing is targeted now. */
    fun tapTarget(item: TableItem): Boolean {
        val f = flow ?: return false
        val decision = f.pending as? Decision.Targets ?: return false
        if (busy) return true
        val target = targetOf(f, decision, item) ?: return false
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
        val autotapOn = autotap
        val picked = (f.pending as? Decision.ManaPayment)?.let { payment -> if (autotapOn) payment.suggestion else mana }
        if (picked == null && !f.complete) return false
        return step { complete(f, autotapOn, picked) ?: Step.Refuse(NOT_ALLOWED) }
    }

    /** "Cancel" or Back: the whole cast is dropped; nothing was sent (I-D11). A step still being worked out is dropped
     * with it. */
    /**
     * Drops the cast with [message]: one saying the card can't be cast, played or paid for now, or that the table changed
     * under it, shakes the card instead of a message (UI review round 2 #4, I-D46 b: Inspect says why); genuine failures
     * (offline, can't be worked out or sent) keep their message.
     */
    private fun drop(message: String) {
        val card = castCard
        cancel("dropped: $message")
        if (card != null && visual(message)) { fail(null); shake(card) } else fail(message)
    }

    fun cancel(how: String = "cancelled") {
        notice = null
        // I-D43: how each cast flow ended
        if (flow != null || starting != null) (flow?.source?.gameId ?: current()?.gameId)?.let {
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
    fun glow(play: TableBoard): Set<String> {
        val f = flow ?: return emptySet()
        val decision = f.pending as? Decision.Targets ?: return emptySet()
        return targetGlow(play, decision.options.map(f::ref))
    }

    /** The Stack entries that are legal targets of the open question, top first (picked in Manage Stack). */
    fun stackTargets(play: TableBoard): List<String> {
        val f = flow ?: return emptyList()
        val decision = f.pending as? Decision.Targets ?: return emptyList()
        return targetEntries(play, decision.options.map(f::ref))
    }

    /**
     * The board the player acts on may have changed under the open cast (D32: the other player acted, an Undo): the cast
     * is worked out again on it, keeping the choices that still fit, the player's own mana tapping included while its
     * sources are still there; dropped with a message when it can't be cast there any more. Only the board the player acts on counts: while the timeline is reviewed or the connection
     * is down nothing changes.
     */
    fun follow() {
        val f = flow ?: return
        // Checked again once the step being worked out is done
        if (busy) return
        val play = current() ?: return
        if (f.source.gameId != play.gameId) return
        if (f.board === play.board) return
        val keep = mana
        val autotapOn = autotap
        step {
            try {
                settle(f.on(source(play)), auto = false, autotapOn, keep, clear = false)
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
        /** I-D56 step C: the complete [flow]'s own [inputs], made on [board]. */
        class Engine(val flow: CastFlow, val inputs: List<Input>, val board: BoardState) : Step
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
        fun guarded(work: () -> Step): Step = try { work() } catch (problem: Exception) {
            AppLog.warn("automatum", "Cast step failed: ${problem::class.java.simpleName}: ${problem.message?.take(200)}", problem)
            current()?.gameId?.let { AutomatumLog.refusal(it, "cast", "${problem::class.java.simpleName}: ${problem.message}") }
            Step.Drop(FAILED)
        }
        this.work.run({ guarded(work) }) { outcome ->
            if (run != generation) return@run
            busy = false
            result = apply(outcome)
        }
        return result
    }

    private fun apply(outcome: Step): Boolean = when (outcome) {
        is Step.Show -> {
            starting = null
            notice = null
            outcome.ways?.let { ways = it }
            if (outcome.ways != null) AutomatumLog.flow(outcome.flow.source.gameId, "cast", "started", outcome.flow.cardId)
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
        is Step.Engine -> sendEngine(outcome)
        is Step.Drop -> { drop(outcome.message); false }
        is Step.Refuse -> { notice = outcome.message; follow(); false }
    }

    /** I-D56 step C: sends a complete cast's own inputs on the board the player acts on now (worked out again when it
     * moved on meanwhile); the device shows it at once (its own pending event). */
    private fun sendEngine(planned: Step.Engine): Boolean {
        val play = current() ?: run { showReady(planned.flow); fail(CANT_ACT); return false }
        if (play.board !== planned.board) {
                val autotapOn = autotap
            starting = null
            return step {
                val ready = try { planned.flow.on(source(play)) } catch (_: Exception) { null }
                ready?.let { complete(it, autotapOn) } ?: Step.Drop(TABLE_CHANGED)
            }
        }
        val card = planned.flow.cardId
        cancel("sent")
        val route = engineSend ?: run { fail("Casting isn't available here."); return false }
        val refusal = route(planned.inputs) ?: return true
        AutomatumLog.refusal(play.gameId, "cast", refusal)
        // I-D56 §5: a cast that would show hidden cards while the line is tentative waits; its card shakes
        shake(card)
        fail(if (refusal == org.asyncttwiz.protocol.game.GameDevice.REVEALS_HIDDEN) WAIT_TO_CATCH_UP else null)
        return false
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
        val autotapOn = autotap
        return step {
            val next = try { change(f) } catch (problem: IllegalArgumentException) {
                AppLog.info("automatum", "Cast step refused: ${problem.message?.take(200)}")
                AutomatumLog.refusal(f.source.gameId, "cast step", problem.message.orEmpty())
                return@step Step.Refuse(NOT_ALLOWED)
            }
            settle(next, auto = true, autotapOn, keep = null, refuseShort = true)
        }
    }

    /** What [next] leads to (worked out off the main thread): sent at once when nothing is left to decide, or only a
     * payment, made with the engine's suggestion with Autotap on (I-D47: the panel has no mana selection then; a payment
     * the sources can't make is refused, [refuseShort], or drops a cast just begun); else shown with its open question,
     * the mana picked being [keep] while all of it is still offered (the player's own tapping, STK-025) or else the
     * suggestion. */
    private fun settle(next: CastFlow, auto: Boolean, autotapOn: Boolean, keep: List<ManaUse>?, ways: List<Input>? = null,
        clear: Boolean = true, refuseShort: Boolean = false): Step {
        val payment = next.pending as? Decision.ManaPayment
        if (auto && (next.complete || (payment != null && autotapOn))) complete(next, autotapOn)?.let { return it }
        if (auto && payment != null && autotapOn) {
            val message = "Not enough mana to pay ${payment.cost}."
            return if (refuseShort) Step.Refuse(message) else Step.Drop(message)
        }
        val mana = keep?.takeIf { k -> payment != null && k.isNotEmpty() && k.all { it in payment.options } } ?: payment?.suggestion.orEmpty()
        val pending = next.pending
        val listed = if (pending != null && pending !is Decision.Targets && pending !is Decision.ManaPayment) next.listedAnswers() else emptyList()
        return Step.Show(next, mana, payment != null && next.pays(mana), listed, Engine.visible(next.state, PlayerId(viewer)), ways, clear)
    }

    /**
     * [next] completed and on its way out, or null when it isn't complete (a payment the suggestion doesn't make, with
     * [autotapOn]): its own inputs ([Step.Engine]).
     */
    private fun complete(next: CastFlow, autotapOn: Boolean, picked: List<ManaUse>? = null): Step? {
        val payment = next.pending as? Decision.ManaPayment
        val uses = when {
            next.complete -> null
            payment != null && picked != null -> picked
            payment != null && autotapOn && payment.suggestion.isNotEmpty() && next.pays(payment.suggestion) -> payment.suggestion
            else -> return null
        }
        val sending = try { next.sending(uses) } catch (_: IllegalArgumentException) { return null }
        return Step.Engine(sending.flow, sending.cast.inputs.toList(), next.board)
    }

    private fun targetOf(f: CastFlow, decision: Decision.Targets, item: TableItem): TargetRef? {
        if (item.kind == TableItemKind.LIFE) return decision.options.firstOrNull { it == TargetRef.Player(PlayerId(item.owner ?: return null)) }
        val ids = listOfNotNull(item.sourceId, item.cardId)
        return decision.options.firstOrNull { target ->
            when (val ref = f.ref(target)) {
                is MtgRef.Card -> ref.cardId in ids
                is MtgRef.StackItem -> f.board.stackEffects.any { it.id in ids && it.item?.id == ref }
                is MtgRef.Player -> false
            }
        }
    }

    private fun seeds() = CastFlow.Seeds { DeviceRandom.seed() }

    /** Where a cast on [play] runs (worked out on [work]): the game's own engine state (engine-responsiveness R4a). */
    private fun source(play: TableBoard): CastFlow.Source {
        val engine = requireNotNull(play.engine) { "No board to cast on" }
        return CastFlow.Source.engine(engine.state, play.board, play.gameId)
    }

    companion object {
        const val BUSY = "Finish or cancel the cast first."
        const val TABLE_CHANGED = "The table changed: cast it again."
        /** I-D56 §5: the hold text of an action refused because it would show hidden cards while the line is tentative. */
        const val WAIT_TO_CATCH_UP = "Wait for the other player to catch up: this would show you hidden cards or a random result."

        /** Whether a dropped cast's [message] is shown by shaking its card (a refusal) rather than said (a failure). */
        fun visual(message: String) = message.endsWith("can't be cast now.") || message.endsWith("can't be played now.") ||
            message.endsWith("that can't be done now.") || message.startsWith("Not enough mana") || message.startsWith("The table changed")
        const val CANT_ACT = "You can't cast right now (reviewing the timeline, or offline). The cast stays open."
        /** I-D27: the same for every card in the other player's hand, whatever it is. */
        const val NOT_YOURS = "Only its owner can cast that card."
        const val NOT_ALLOWED = "Automatum doesn't allow that choice."
        const val FAILED = "Automatum couldn't work that cast out."
        const val TAKE_OVER = "Automatum can't take over this board:"

        fun cardName(play: TableBoard, cardId: String): String {
            val card = play.board.cards.singleOrNull { it.id == cardId } ?: return "That card"
            return play.definitions.singleOrNull { it.digest == card.definition }?.definition?.faces
                ?.let { faces -> faces.singleOrNull { it.faceId == card.faceId } ?: faces.firstOrNull() }?.name ?: "That card"
        }

        fun land(play: TableBoard, cardId: String): Boolean {
            val card = play.board.cards.singleOrNull { it.id == cardId } ?: return false
            val front = play.definitions.singleOrNull { it.digest == card.definition }?.definition?.faces?.firstOrNull()
            return "Land" in StackContexts.types(front?.typeLine.orEmpty())
        }

        /** Whether [cardId] is in [viewer]'s own hand. */
        fun ownHand(play: TableBoard, cardId: String, viewer: String): Boolean {
            val card = play.board.cards.singleOrNull { it.id == cardId } ?: return false
            return play.board.containers.any { it.id == card.containerId && it.zone == PlayZone.HAND && it.accountId == viewer }
        }

        /** The hand menu's Play with Automatum on (ZON-007): "Play" for a land, "Cast" for anything else, and "Cast" for a
         * card [viewer] can't see face up, whatever it is (I-D27). */
        fun playLabel(play: TableBoard, cardId: String, viewer: String): String {
            val card = play.board.cards.singleOrNull { it.id == cardId } ?: return "Cast"
            if (!CardVisibility.isVisibleFaceUp(play.board, card, viewer)) return "Cast"
            return if (land(play, cardId)) "Play" else "Cast"
        }
    }
}

/** The table items that glow for the legal targets [refs] (I-D6): cards by id, a Graveyard or Exile pile holding one
 * (tapped, it opens to pick from, i12review #14), Stack entries by id, and the Stack itself when an entry below the top
 * is one (tapped, Manage Stack opens to pick from), players by their life square. */
internal fun targetGlow(play: TableBoard, refs: List<MtgRef>): Set<String> = refs.flatMap { ref ->
    // UI review round 2 #4 (decided for the owner): a spell on the Stack glows as its entry only, never as its real card
    // (in a hand, STK-001) or the pile holding it
    val entries = play.board.stackEffects.filter { it.item?.id == ref }.map { it.id }
    if (entries.isNotEmpty()) return@flatMap entries
    when (ref) {
        is MtgRef.Player -> listOf("life/${ref.accountId}")
        is MtgRef.Card -> listOf(ref.cardId) + listOfNotNull(play.board.cards.singleOrNull { it.id == ref.cardId }?.containerId
            ?.takeIf { id -> play.board.containers.any { it.id == id && it.zone in PublicPiles.zones } })
        is MtgRef.StackItem -> emptyList()
    }
}.toSet() + listOfNotNull(CombatStackRules.stack(play.board).takeIf { stack ->
    targetEntries(play, refs).any { it != stack.cards.firstOrNull() } }?.id)

/** The Stack entries that are legal targets among [refs]: a spell by its card (its item's object), an ability by its
 * item (I-D6), top first. */
internal fun targetEntries(play: TableBoard, refs: List<MtgRef>): List<String> {
    val wanted = refs.filter { it !is MtgRef.Player }.toSet()
    val entries = play.board.stackEffects.associateBy { it.id }
    return CombatStackRules.stack(play.board).cards.filter { id -> entries[id]?.item?.id in wanted }
}
