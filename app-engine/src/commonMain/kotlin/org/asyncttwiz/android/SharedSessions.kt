package org.asyncttwiz.android

import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.decision.ManaUse
import arcanum.engine.input.Input
import org.asyncttwiz.appui.simpleClassName
import org.asyncttwiz.appui.synchronized
import org.asyncttwiz.automatum.AutomatumLog
import org.asyncttwiz.automatum.CombatRefused
import org.asyncttwiz.automatum.EngineRun
import org.asyncttwiz.automatum.ManaPicks
import org.asyncttwiz.automatum.ManaWays
import org.asyncttwiz.automatum.SharedFlow
import org.asyncttwiz.automatum.boundary.EngineLog
import org.asyncttwiz.automatum.boundary.FlowSessions
import org.asyncttwiz.automatum.boundary.ManaBelongs
import org.asyncttwiz.automatum.boundary.SelectionWire
import org.asyncttwiz.automatum.boundary.SharedAnswer
import org.asyncttwiz.automatum.boundary.SharedFlowValue
import org.asyncttwiz.automatum.boundary.SharedOp
import org.asyncttwiz.automatum.boundary.SharedRequest
import org.asyncttwiz.protocol.CombatStackRules

/**
 * Web client step 4e (docs/specs/web-client.md §1 item 3): the engine side of a table's shared flows (priority, resolution,
 * triggers, the open decision of a resolution and its payments, combat's declarations and divisions; integration spec §3 I2b,
 * I3, I-D11) as sessions ([SharedRequest] in, [SharedAnswer] out), where the device is: the browser's engine Web Worker
 * ([DeviceHost]); Android in-process ([LocalSharedPort]). A session's versions are the flows of the boards the page showed
 * ([SharedOp.Show]); a step names the version shown, refused as stale once that isn't kept, and the board it is made on (its
 * own flow when the page shows another, or none). What the panels draw comes as plain data ([SharedFlowValue]); a step's
 * engine work (the answer's run, Autotap's suggestion with TBL-130's topmost sources, the payment around the player's taps,
 * castability's picks, the declarations of combat with their costs' mana) is done here and only the viewer's own inputs go
 * back (I-D56 step C). Every opening, version, step, refusal and end is logged.
 *
 * In-process the page shows a flow at once on its own thread while steps run on the Automatum worker: the sessions' records
 * are kept under a lock, the engine work outside it.
 */
class SharedSessions(private val viewer: String, private val log: EngineLog = EngineBoundary.log) {
    /** A version of a session: the flow of the board numbered [board]. */
    private class Entry(val flow: SharedFlow, val board: Long)

    private val sessions = FlowSessions<Entry>(log)

    private inline fun <T> book(block: () -> T): T = synchronized(this) { block() }

    /** Sessions open now (tests and the log). */
    val open: Int get() = book { sessions.open }

    /**
     * [r] made on [play] (the board [SharedRequest.board] names; null when not named or not held here any more); [stacks]:
     * the stacks of identical battlefield cards on the board the player acts on (TBL-130), null without one.
     */
    fun handle(r: SharedRequest, play: TableBoard?, stacks: () -> List<List<String>>?): SharedAnswer {
        val op = r.op
        val name = op::class.simpleName ?: "step"
        if (op is SharedOp.Close) { book { sessions.close(r.session, op.how) }; return SharedAnswer.None }
        if (r.board != null && play == null) {
            log.log(true, "Shared flow ${r.session}: $name on board ${r.board}, which isn't held here any more: refused as stale")
            return if (op is SharedOp.Selection) SharedAnswer.Selected(r.version ?: 0, null) else SharedAnswer.Refuse(TABLE_CHANGED)
        }
        if (op is SharedOp.Show) return show(r, op, requireNotNull(play) { "A flow shown without its board" }, stacks)
        // The flow the page shows, when the step names it
        val shown = r.version?.let { v ->
            book { sessions.get(r.session, v, name) }.entry ?: return when (op) {
                is SharedOp.Selection -> SharedAnswer.Selected(v, null)
                else -> SharedAnswer.Refuse(TABLE_CHANGED)
            }
        }
        return try {
            when (op) {
                is SharedOp.Tap -> tap(requireNotNull(shown) { "A tap without the flow shown" }, r.version!!, op)
                is SharedOp.ChooseWay -> chooseWay(requireNotNull(shown) { "A way without the flow shown" }, r.version!!, op)
                is SharedOp.Pays -> pays(requireNotNull(shown) { "Paying without the flow shown" }, r.version!!, op.mana)
                is SharedOp.Selection -> selection(requireNotNull(shown) { "Picking without the flow shown" }.flow, r.version!!, op)
                is SharedOp.Declaring -> SharedAnswer.Declaring(flowOf(shown, r.board, play)?.declaring)
                else -> step(r, op, flowOf(shown, r.board, play), play, stacks)
            }
        } catch (problem: Exception) {
            AppLog.warn("automatum", "Shared flow step failed: ${simpleClassName(problem)}: ${problem.message?.take(200)}", problem)
            play?.gameId?.let { AutomatumLog.refusal(it, "shared flow", "${simpleClassName(problem)}: ${problem.message}") }
            SharedAnswer.Refuse(CANT_GO_ON)
        }
    }

    /** The flow of the board numbered [board]: the one shown when it is that board's, else [play]'s own. */
    private fun flowOf(shown: Entry?, board: Long?, play: TableBoard?): SharedFlow? =
        shown?.takeIf { it.board == board || board == null }?.flow ?: play?.values?.sharedFlow()

    /** [play]'s flow as the session's newest version, with the payment's mana ([SharedOp.Show]). */
    private fun show(r: SharedRequest, op: SharedOp.Show, play: TableBoard, stacks: () -> List<List<String>>?): SharedAnswer {
        val values = play.values ?: return SharedAnswer.Refuse(TAKE_OVER)
        val value = values.sharedValue() ?: return SharedAnswer.Refuse(TAKE_OVER)
        val flow = requireNotNull(value.flow) { "A flow's value without its flow" }
        val version = book {
            if (!sessions.isOpen(r.session)) sessions.open(r.session, "shared", "board ${values.number}")
            sessions.put(r.session, Entry(flow, values.number), "shows ${value.decision?.let { "${it.kind} ${it.id.value}" } ?: "no decision"}")
        } ?: return SharedAnswer.Refuse(TABLE_CHANGED)
        val payment = value.decision as? Decision.ManaPayment ?: value.payment
        val kept = op.decision != null && op.decision == value.decision?.id?.value
        // STK-049 (owner gallery review, 2026-11-06): with Autotap on a payment opens with its sources picked, off none; a new
        // board asking the same decision keeps the picks
        val mana = when {
            payment == null -> emptyList()
            kept -> op.keep
            op.autotap -> suggestion(flow, payment, stacks)
            else -> emptyList()
        }
        val paid = payment != null && flow.pays(mana)
        return SharedAnswer.Show(value.copy(version = version, mana = mana, paid = paid).also { it.flow = flow })
    }

    /** STK-024, TBL-130 (owner playtest 2026-11-07): Autotap's suggestion for [payment] of [flow], taking the topmost members
     * of stacks of identical sources ([TopmostMana]); the engine's own when that doesn't pay. */
    private fun suggestion(flow: SharedFlow, payment: Decision.ManaPayment, stacks: () -> List<List<String>>?): List<ManaUse> {
        val groups = stacks() ?: return payment.suggestion
        val topmost = TopmostMana.of(payment.suggestion, payment.options, groups, flow::atwCard)
        return if (topmost == payment.suggestion || flow.pays(topmost)) topmost else payment.suggestion
    }

    /** Castability §3 on [flow] (nothing changes). */
    private fun selection(flow: SharedFlow, version: Long, op: SharedOp.Selection): SharedAnswer {
        val result = runCatching { flow.selection(op.picked) }.onFailure {
            AppLog.warn("automatum", "Selection couldn't be worked out: ${simpleClassName(it)}")
        }.getOrNull()
        return SharedAnswer.Selected(version, result?.let(SelectionWire::of))
    }

    private fun paymentOf(flow: SharedFlow): Decision.ManaPayment? = flow.decision as? Decision.ManaPayment ?: flow.payment

    private fun engineOf(flow: SharedFlow) = ManaTaps.Engine({ pinned, removed, exact -> flow.fill(pinned, removed, exact) }, flow::pays)

    /** STK-024: a tap on a source in the open payment ([ManaTaps.tap]). */
    private fun tap(e: Entry, version: Long, op: SharedOp.Tap): SharedAnswer {
        val decision = paymentOf(e.flow) ?: return SharedAnswer.Refuse(null)
        val belongs: (ManaUse) -> Boolean = when (val b = op.belongs) {
            is ManaBelongs.Key -> { use -> ManaPicks.key(use) == b.key }
            is ManaBelongs.Uses -> { val mine = b.uses.toSet(); { use -> use in mine } }
        }
        val done = ManaTaps.tap(op.taps.taps(), decision, op.current, op.ways, belongs, op.autotap,
            ManaWays.care(e.flow.state, decision.source), engineOf(e.flow))
        return SharedAnswer.Mana(version, done.mana, e.flow.pays(done.mana), done.taps.wire(), "${op.what}, ${done.said}")
    }

    /** STK-024 (owner playtest 2026-11-09): a way chosen with a source's corner chip, kept exactly. */
    private fun chooseWay(e: Entry, version: Long, op: SharedOp.ChooseWay): SharedAnswer {
        val decision = paymentOf(e.flow) ?: return SharedAnswer.Refuse(null)
        val mine = op.mine.toSet()
        val done = ManaTaps.choose(op.taps.taps(), decision, op.way, { it in mine }, op.autotap, engineOf(e.flow))
        return SharedAnswer.Mana(version, done.mana, e.flow.pays(done.mana), done.taps.wire(), "${op.cardId}'s corner chip, ${done.said}")
    }

    private fun pays(e: Entry, version: Long, mana: List<ManaUse>): SharedAnswer =
        SharedAnswer.Mana(version, mana, e.flow.pays(mana), said = "kept picks checked")

    /** A step that sends: worked out on [flow] (the board's), only the viewer's own inputs of it. */
    private fun step(r: SharedRequest, op: SharedOp, flow: SharedFlow?, play: TableBoard?, stacks: () -> List<List<String>>?): SharedAnswer {
        val gameId = play?.gameId
        val name = op::class.simpleName
        return try {
            val f = flow ?: throw Refused(TAKE_OVER)
            val call = when (op) {
                is SharedOp.Pass -> {
                    if (f.priority != op.player) throw Refused("${if (op.player == viewer) "You don't" else "They don't"} hold priority now.")
                    f.pass(op.player)
                }
                is SharedOp.Step -> pressed(f, op.autotap, stacks)
                is SharedOp.Give -> open(f, op.decision) {
                    // A division of combat damage (I-D29): the damage is dealt and the game goes on through the steps (I3)
                    val answer = op.answer
                    if (answer is Answer.Division && f.division != null) f.divide(answer.shares.associate { f.ref(it.target) to it.amount })
                    else f.answer(answer, op.then)
                }
                is SharedOp.Replacements -> open(f, op.decision) { f.answerReplacements(op.preference) }
                is SharedOp.Pay -> open(f, op.decision) { if (f.decision is Decision.May) f.payMay(op.mana) else f.answer(Answer.ManaPayment(op.mana)) }
                is SharedOp.Early -> { if (f.early == null) throw Refused(null); f.answerEarly() }
                else -> error("Not a step: $name")
            }
            // I-D56 step C: only the viewer's own inputs go out; their device folds what follows by itself (the other player's
            // points are assumed, automatic or waited for, never passed for them)
            val own = call.inputs.takeWhile { it !is Input.MandatoryLoop && !(it is Input.PassPriority && it.player.value != viewer) }
            log.log(false, "Shared flow ${r.session}: $name on board ${r.board} (version ${r.version}): ${own.size} own inputs")
            if (own.isEmpty()) SharedAnswer.Empty else SharedAnswer.Send(own)
        } catch (refused: Refused) {
            refused.message?.let { m -> gameId?.let { AutomatumLog.refusal(it, "shared flow", m) } }
            log.log(false, "Shared flow ${r.session}: $name refused: ${refused.message}")
            SharedAnswer.Refuse(refused.message)
        } catch (refused: CombatRefused) {
            gameId?.let { AutomatumLog.refusal(it, "combat", refused.reason) }
            log.log(false, "Shared flow ${r.session}: $name refused by combat: ${refused.reason}")
            SharedAnswer.Refuse(refused.reason)
        } catch (problem: IllegalArgumentException) {
            AppLog.info("automatum", "Answer refused: ${problem.message?.take(200)}")
            gameId?.let { AutomatumLog.refusal(it, "shared flow", problem.message.orEmpty()) }
            SharedAnswer.Refuse(CastSessions.NOT_ALLOWED)
        }
    }

    /** [perform] on [f]'s open decision when it is still [decision] (the one the panel shows). */
    private fun open(f: SharedFlow, decision: String, perform: () -> EngineRun): EngineRun {
        val open = f.decision ?: throw Refused(null)
        if (open.id.value != decision) throw Refused(LOOK_AGAIN)
        return perform()
    }

    /** The engine call a press of the step button makes on [flow]: the holder passes (with something on the Stack it is
     * "Resolve", I-D46 d), or the lanes' declaration is made. */
    private fun pressed(flow: SharedFlow, autotap: Boolean, stacks: () -> List<List<String>>?): EngineRun {
        if (CombatStackRules.stack(flow.board).cards.isNotEmpty()) {
            val holder = flow.priority ?: throw Refused(STACK_WAITING)
            return flow.pass(holder)
        }
        val holder = flow.holder
        val declaring = if (holder == null) flow.declaring else null
        if (holder == null && declaring == null) throw Refused(CANT_GO_ON)
        return if (holder != null) flow.step(holder) else flow.declare(autotap) { suggestion(flow, it, stacks) }
    }

    /** A refusal with its message for the player (null: none). */
    private class Refused(message: String?) : Exception(message)

    companion object {
        const val TABLE_CHANGED = "The table changed: try again."
        const val LOOK_AGAIN = "The table changed: look at the choice again."
        const val STACK_WAITING = "Let the Stack resolve before changing the step."
        const val TAKE_OVER = "Automatum can't take over this board."
        const val CANT_GO_ON = "Automatum couldn't go on from here."
    }
}

/**
 * Web client step 4e: where a table's shared-flow sessions run ([SharedSessions]) as the page sees it: in-process
 * ([LocalSharedPort]: Android, tests, a browser page holding the device itself) or the browser's engine Web Worker beside the
 * game's device ([EngineGame.sharedStep]). Answers come on the page's thread, in the order asked.
 */
interface SharedPort {
    /** Whether the sessions run across the boundary (the worker): a flow is then asked for, never read from the board. */
    val remote: Boolean

    /** Sends [request] made on [play] (the board [SharedRequest.board] names), then [done] with its answer on the main thread:
     * a [SharedOp.Show] at once in-process (its board's flow is worked out first), the other steps on the worker. */
    fun ask(request: SharedRequest, play: TableBoard?, stacks: () -> List<List<String>>?, done: (SharedAnswer) -> Unit)
}

/** The sessions in-process: a flow shown at once on the caller's thread (its board's flow worked out already), the other
 * steps worked out by [run] (the Automatum worker in the app, at once in tests) with the answers on the main thread. */
class LocalSharedPort(viewer: String, private val run: (work: () -> SharedAnswer, done: (SharedAnswer) -> Unit) -> Unit) : SharedPort {
    val sessions = SharedSessions(viewer)
    override val remote: Boolean get() = false

    override fun ask(request: SharedRequest, play: TableBoard?, stacks: () -> List<List<String>>?, done: (SharedAnswer) -> Unit) {
        if (request.op is SharedOp.Show || request.op is SharedOp.Close) done(sessions.handle(request, play, stacks))
        else run({ sessions.handle(request, play, stacks) }, done)
    }
}

/** A table's shared-flow sessions where its game's device is ([game]): in the browser's engine Web Worker when the device is
 * there ([EngineGame.inWorker]), else in-process ([local]). */
class TableSharedPort(private val local: SharedPort, private val game: () -> EngineGame?) : SharedPort {
    private fun worker(): EngineGame? = game()?.takeIf { it.inWorker }
    override val remote: Boolean get() = worker() != null

    override fun ask(request: SharedRequest, play: TableBoard?, stacks: () -> List<List<String>>?, done: (SharedAnswer) -> Unit) {
        val g = worker() ?: return local.ask(request, play, stacks, done)
        val needs = request.op is SharedOp.Show || request.op is SharedOp.Step
        g.sharedStep(if (needs && request.stacks == null) request.copy(stacks = stacks()) else request, done)
    }
}
