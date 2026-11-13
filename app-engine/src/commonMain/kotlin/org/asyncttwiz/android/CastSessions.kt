package org.asyncttwiz.android

import arcanum.engine.Engine
import arcanum.engine.Host
import arcanum.engine.change.Change
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.decision.ManaUse
import arcanum.engine.input.Input
import arcanum.engine.state.GameState
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import kotlinx.serialization.json.JsonElement
import org.asyncttwiz.appui.simpleClassName
import org.asyncttwiz.automatum.AutomatumLog
import org.asyncttwiz.automatum.CastFlow
import org.asyncttwiz.automatum.HandCasts
import org.asyncttwiz.automatum.ManaPicks
import org.asyncttwiz.automatum.ManaWays
import org.asyncttwiz.automatum.boundary.AnnouncedMost
import org.asyncttwiz.automatum.boundary.CastAnswer
import org.asyncttwiz.automatum.boundary.CastChange
import org.asyncttwiz.automatum.boundary.CastChoices
import org.asyncttwiz.automatum.boundary.CastFlowValue
import org.asyncttwiz.automatum.boundary.CastOp
import org.asyncttwiz.automatum.boundary.CastPanelValue
import org.asyncttwiz.automatum.boundary.CastRequest
import org.asyncttwiz.automatum.boundary.CastStepValue
import org.asyncttwiz.automatum.boundary.EngineLog
import org.asyncttwiz.automatum.boundary.FlowSessions
import org.asyncttwiz.automatum.boundary.ManaBelongs
import org.asyncttwiz.automatum.boundary.ManaTapsWire
import org.asyncttwiz.automatum.boundary.SelectionWire
import org.asyncttwiz.protocol.Faults

/**
 * Web client step 4e (docs/specs/web-client.md §1 item 3): the engine side of the player's cast, land play and activation
 * flows (integration spec §3 I2a, I-D11) as sessions ([CastRequest] in, [CastAnswer] out): where the device is (the browser's
 * engine Web Worker, [DeviceHost]; Android in-process on the Automatum worker, [LocalCastPort]). Each session keeps a few
 * versions of its flow ([FlowSessions]); everything else the page chose travels with each step. What the cast panel draws is
 * worked out here as plain data ([CastPanelValue]): Autotap's suggestion (TBL-130's topmost sources, STK-024), the payment
 * around the player's own taps, the castability checks (§3), the X's most (STK-023), the engine's listed answers.
 *
 * [encode] writes a flow's changes for the page's copy of its board (the worker: the game's codec); null in-process, where
 * the panel reads the flow's own state ([CastFlowValue.state]). Not thread-safe: one thread.
 */
class CastSessions(private val viewer: String, private val log: EngineLog = EngineBoundary.log,
    private val encode: ((List<Change>) -> JsonElement)? = null) {
    /** A version of a session's flow: the flow, and the flow taken on to its payment ([HandCasts.ahead]). */
    private class Entry(val flow: CastFlow, val ahead: CastFlow?)

    private val sessions = FlowSessions<Entry>(log)

    /** Sessions open now (tests and the log). */
    val open: Int get() = sessions.open

    /**
     * [r] made on [play] (the board [CastRequest.board] names; null when not named or not held here any more); [stacks]: the
     * stacks of identical battlefield cards on the board the player acts on now (TBL-130), null without one.
     */
    fun handle(r: CastRequest, play: TableBoard?, stacks: () -> List<List<String>>?): CastAnswer {
        val op = r.op
        if (op is CastOp.Close) { sessions.close(r.session, op.how); return CastAnswer.None }
        val opening = op is CastOp.Start || op is CastOp.FromHand || op is CastOp.Begin
        if (opening) sessions.open(r.session, "cast", (op as? CastOp.Start)?.cardId ?: (op as? CastOp.FromHand)?.cardId ?: (op as CastOp.Begin).cardId)
        val name = op::class.simpleName ?: "step"
        if (r.board != null && play == null) {
            log.log(true, "Flow session ${r.session}: $name on board ${r.board}, which isn't held here any more: refused as stale")
            sessions.close(r.session, "stale board")
            return CastAnswer.Drop(TABLE_CHANGED)
        }
        val entry = if (opening) null else sessions.get(r.session, r.version, name).let { found ->
            found.entry ?: return if (op is CastOp.Selection) CastAnswer.Selected(r.version ?: 0, null) else CastAnswer.Refuse(FlowSessions.STALE)
        }
        val step = Step(r.session, play, stacks)
        val outcome = try {
            when (op) {
                is CastOp.Start -> step.start(op)
                is CastOp.FromHand -> step.fromHand(op)
                is CastOp.Begin -> step.begin(op)
                is CastOp.HandChoice -> step.handChoice(entry!!.flow, op)
                is CastOp.Update -> step.update(entry!!.flow, op)
                is CastOp.Tap -> step.tap(entry!!, op)
                is CastOp.ChooseWay -> step.chooseWay(entry!!, op)
                is CastOp.Cast -> step.cast(entry!!.flow, op)
                is CastOp.Follow -> step.follow(entry!!.flow, op)
                is CastOp.Replan -> step.replan(entry!!.flow, op)
                is CastOp.Selection -> return selection(entry!!.flow, r.version ?: 0, op)
                is CastOp.Close -> return CastAnswer.None
            }
        } catch (problem: Exception) {
            AppLog.warn("automatum", "Cast step failed: ${simpleClassName(problem)}: ${problem.message?.take(200)}", problem)
            (play?.gameId ?: entry?.flow?.source?.gameId)?.let { AutomatumLog.refusal(it, "cast", "${simpleClassName(problem)}: ${problem.message}") }
            Outcome.Drop(FAILED)
        }
        return answer(r.session, name, r.version, outcome)
    }

    /** Castability §3 on [flow] (nothing changes). */
    private fun selection(flow: CastFlow, version: Long, op: CastOp.Selection): CastAnswer {
        val result = runCatching { flow.selection(op.picked) }.onFailure {
            AppLog.warn("automatum", "Selection couldn't be worked out: ${simpleClassName(it)}")
        }.getOrNull()
        return CastAnswer.Selected(version, result?.let(SelectionWire::of))
    }

    /** [outcome] as the page gets it: a new version for a new flow. */
    private fun answer(session: Long, name: String, version: Long?, outcome: Outcome): CastAnswer = when (outcome) {
        is Outcome.Show -> {
            val v = sessions.put(session, Entry(outcome.flow, outcome.ahead), "$name shows ${outcome.flow.pending?.kind ?: "the complete flow"}")
            if (v == null) CastAnswer.Drop(TABLE_CHANGED) else CastAnswer.Show(outcome.panel(flowValue(outcome.flow, v, outcome.fresh)))
        }
        is Outcome.Mana -> CastAnswer.Mana(version ?: 0, outcome.mana, outcome.paid, outcome.taps.wire())
        is Outcome.Engine -> {
            val v = sessions.put(session, Entry(outcome.flow, null), "$name completes the flow (${outcome.inputs.size} inputs)")
            if (v == null) CastAnswer.Drop(TABLE_CHANGED) else CastAnswer.Engine(flowValue(outcome.flow, v, outcome.fresh), outcome.inputs)
        }
        is Outcome.Drop -> { log.log(false, "Flow session $session: $name drops the flow: ${outcome.message}"); sessions.close(session, "dropped"); CastAnswer.Drop(outcome.message) }
        is Outcome.Refuse -> { log.log(false, "Flow session $session: $name refused: ${outcome.message}"); CastAnswer.Refuse(outcome.message) }
    }

    private fun flowValue(flow: CastFlow, version: Long, fresh: Boolean): CastFlowValue =
        CastFlowValue(version, fresh, flow.cardId, flow.begin, flow.steps.map { (d, a) -> CastStepValue(d, a) }, flow.pending,
            flow.call.pending?.continuation, encode?.invoke(flow.call.changes)).also { if (encode == null) it.state = flow.state }

    /** What a step comes to (worked out by [Step]). */
    private sealed interface Outcome {
        /** [flow] with its open question shown ([panel] makes the page's value); [fresh]: on the step's board. */
        class Show(val flow: CastFlow, val ahead: CastFlow?, val fresh: Boolean, val panel: (CastFlowValue) -> CastPanelValue) : Outcome
        class Mana(val mana: List<ManaUse>, val paid: Boolean, val taps: ManaTaps) : Outcome
        /** The complete [flow]'s own [inputs]. */
        class Engine(val flow: CastFlow, val inputs: List<Input>, val fresh: Boolean) : Outcome
        class Drop(val message: String) : Outcome
        class Refuse(val message: String) : Outcome
    }

    /** One step's work, on [play] (the board the step names) with the stacks of the board acted on ([stacks]). */
    private inner class Step(val session: Long, val play: TableBoard?, val stacks: () -> List<List<String>>?) {
        private fun board(): TableBoard = requireNotNull(play) { "No board to cast on" }

        fun start(op: CastOp.Start): Outcome {
            val play = board()
            val plays = AutomatumBoards.handPlays(play, viewer)
            val offered = plays.of(op.cardId)
            val name = cardName(play, op.cardId)
            val begin = op.way ?: offered.firstOrNull()
                ?: return Outcome.Drop(if (land(play, op.cardId)) "$name can't be played now." else "$name can't be cast now.")
            val flow = try {
                CastFlow.start(source(play), viewer, begin, seeds(), extras = op.extras)
            } catch (_: IllegalArgumentException) {
                return Outcome.Drop("$name can't be cast now.")
            }
            // I-D50: the way was chosen with the cost chips: the panel has no "Cast as"
            return settle(flow, fresh = true, auto = true, op.autotap, keep = null, ways = listOf(begin))
        }

        fun fromHand(op: CastOp.FromHand): Outcome {
            val play = board()
            val cardId = op.cardId
            val name = cardName(play, cardId)
            val cant = if (land(play, cardId)) "$name can't be played now." else "$name can't be cast now."
            val offered = AutomatumBoards.handPlays(play, viewer).of(cardId)
            val definitions = AutomatumBoards.definitions(play)
            val src = source(play)
            val seeds = seeds()
            val chips = op.known?.takeIf { it.cardId == cardId } ?: HandCasts.chips(src, definitions, viewer, cardId, offered, seeds)
                ?: return Outcome.Drop(cant)
            if (!chips.affordable) return Outcome.Drop(cant)
            if (!chips.choice) {
                val chip = chips.chips.first()
                val flow = try { CastFlow.start(src, viewer, chip.input, seeds, extras = chip.times) }
                    catch (_: IllegalArgumentException) { return Outcome.Drop(cant) }
                return settle(flow, fresh = true, auto = true, op.autotap, keep = null, ways = listOf(chip.input))
            }
            // The panel: the first way the player can pay, its extras off
            val first = HandCasts.options(src, definitions, viewer, cardId, offered, 0, emptyList(), seeds) ?: return Outcome.Drop(cant)
            val way = first.ways.indexOfFirst { it.affordable }.coerceAtLeast(0)
            val options = if (way == 0) first else HandCasts.options(src, definitions, viewer, cardId, offered, way, emptyList(), seeds)
                ?: return Outcome.Drop(cant)
            val flow = try { CastFlow.start(src, viewer, options.chosen.input, seeds, extras = options.times) }
                catch (_: IllegalArgumentException) { return Outcome.Drop(cant) }
            return settle(flow, fresh = true, auto = false, op.autotap, keep = null, ways = listOf(options.chosen.input), hand = options,
                choosing = options.ways.size > 1 || options.extras.isNotEmpty())
        }

        fun begin(op: CastOp.Begin): Outcome {
            val play = board()
            val input = op.input
            val ways = if (input is Input.Activate) listOf(input)
                else AutomatumBoards.handPlays(play, viewer).of(op.cardId).takeIf { input in it } ?: listOf(input)
            val name = cardName(play, op.cardId)
            val flow = try {
                CastFlow.start(source(play), viewer, input, seeds())
            } catch (_: IllegalArgumentException) {
                return Outcome.Drop(if (input is Input.Activate) "$name: that can't be done now." else "$name can't be cast now.")
            }
            return settle(flow, fresh = true, auto = true, op.autotap, keep = null, ways = ways)
        }

        fun handChoice(f: CastFlow, op: CastOp.HandChoice): Outcome {
            val play = board()
            val c = op.choices
            val options = HandCasts.options(source(play), AutomatumBoards.definitions(play), viewer, f.cardId,
                AutomatumBoards.handPlays(play, viewer).of(f.cardId), op.way, op.times, seeds()) ?: return Outcome.Refuse(NOT_ALLOWED)
            val next = try { f.withChoice(options.chosen.input, options.times) } catch (_: IllegalArgumentException) {
                return Outcome.Refuse(NOT_ALLOWED)
            }
            return settle(next, fresh = false, auto = false, c.autotap, c.keep, c.custom, hand = options, picks = c.taps.taps())
        }

        fun update(f: CastFlow, op: CastOp.Update): Outcome {
            val c = op.choices
            val next = try {
                when (val change = op.change) {
                    is CastChange.Give -> f.answer(change.answer)
                    is CastChange.Change -> f.change(change.index, change.answer)
                    is CastChange.Back -> f.back(change.index)
                    is CastChange.Way -> f.withBegin(change.input)
                }
            } catch (problem: IllegalArgumentException) {
                AppLog.info("automatum", "Cast step refused: ${problem.message?.take(200)}")
                AutomatumLog.refusal(f.source.gameId, "cast step", problem.message.orEmpty())
                return Outcome.Refuse(NOT_ALLOWED)
            }
            return settle(next, fresh = false, auto = true, c.autotap, c.keep, c.custom, picks = c.taps.taps())
        }

        /** STK-024 (owner playtest 2026-11-09): a tap on a source: one paying is taken out, one not paying is added with its
         * sensible way ([ManaTaps.tap]). */
        fun tap(e: Entry, op: CastOp.Tap): Outcome {
            val at = e.ahead ?: return Outcome.Refuse(NOT_ALLOWED)
            val decision = at.pending as? Decision.ManaPayment ?: return Outcome.Refuse(NOT_ALLOWED)
            val belongs = belongs(op.belongs)
            val outcome = ManaTaps.tap(op.taps.taps(), decision, op.current, op.ways, belongs, op.autotap,
                ManaWays.care(at.state, decision.source), engineOf(at))
            AppLog.info("cast", "Payment: ${op.what} tapped, ${outcome.said}; paying now ${outcome.mana.joinToString { ManaWays.label(it) }}")
            return Outcome.Mana(outcome.mana, at.pays(outcome.mana), outcome.taps)
        }

        /** STK-024 (owner playtest 2026-11-09): a way chosen with a source's corner chip, kept exactly. */
        fun chooseWay(e: Entry, op: CastOp.ChooseWay): Outcome {
            val at = e.ahead ?: return Outcome.Refuse(NOT_ALLOWED)
            val decision = at.pending as? Decision.ManaPayment ?: return Outcome.Refuse(NOT_ALLOWED)
            val mine = op.mine.toSet()
            val outcome = ManaTaps.choose(op.taps.taps(), decision, op.way, { it in mine }, op.autotap, engineOf(at))
            AppLog.info("cast", "Payment: ${op.cardId}'s corner chip, ${outcome.said}; paying now ${outcome.mana.joinToString { ManaWays.label(it) }}")
            return Outcome.Mana(outcome.mana, at.pays(outcome.mana), outcome.taps)
        }

        /** "Cast": pays with the chosen mana (Autotap's suggestion when it follows and nothing is chosen) and completes. */
        fun cast(f: CastFlow, op: CastOp.Cast): Outcome {
            val follows = op.autotap && !op.custom
            val picked = (f.pending as? Decision.ManaPayment)?.let { payment -> if (follows && op.mana.isEmpty()) suggestion(f, payment) else op.mana }
            if (picked == null && !f.complete) return Outcome.Refuse(NOT_ALLOWED)
            return complete(f, op.autotap, picked, fresh = false)
                // A mana ability the player picked asks something as it resolves: the cast goes on to that
                ?: (if (picked != null && f.pays(picked)) settle(f.answer(Answer.ManaPayment(picked)), fresh = false, auto = false, op.autotap, null) else null)
                ?: Outcome.Refuse(if (picked != null) notPaid(f.pending as Decision.ManaPayment) else NOT_ALLOWED)
        }

        /** D32: the flow worked out again on the board named, keeping the choices that still fit. */
        fun follow(f: CastFlow, op: CastOp.Follow): Outcome {
            val play = board()
            val c = op.choices
            return try {
                // A hand card's panel: its ways and extras worked out again on the new board
                val options = op.hand?.let {
                    HandCasts.options(source(play), AutomatumBoards.definitions(play), viewer, f.cardId,
                        AutomatumBoards.handPlays(play, viewer).of(f.cardId), it.way, it.times, seeds())
                }
                settle(f.on(source(play)), fresh = true, auto = false, c.autotap, c.keep, c.custom, clear = false, hand = options, picks = c.taps.taps())
            } catch (_: Exception) {
                Outcome.Drop("The table changed: ${cardName(play, f.cardId)} can't be cast now.")
            }
        }

        /** A complete flow completed again on the board named (its send waited while the board moved on). */
        fun replan(f: CastFlow, op: CastOp.Replan): Outcome {
            val play = board()
            val ready = try { f.on(source(play)) } catch (_: Exception) { null }
            return ready?.let { complete(it, op.autotap, fresh = true) } ?: Outcome.Drop(TABLE_CHANGED)
        }

        private fun belongs(b: ManaBelongs): (ManaUse) -> Boolean = when (b) {
            is ManaBelongs.Key -> { use -> ManaPicks.key(use) == b.key }
            is ManaBelongs.Uses -> { val mine = b.uses.toSet(); { use -> use in mine } }
        }

        /**
         * What [next] leads to: sent at once when nothing is left to decide, or (with [auto]) only a payment that the chosen
         * mana makes: Autotap's suggestion with Autotap on, around the player's own picks ([picks], STK-024), else the sources
         * the player chose ([keep], carried to this payment). Autotap never decides (castability §1.3): a payment it can't
         * make opens the panel for the player to pay by hand. Else shown with its open question, and the payment it comes
         * to ([HandCasts.ahead], STK-024: Autotap's suggestion pre-chosen, or the player's own sources; nothing chosen with
         * Autotap off).
         */
        fun settle(next: CastFlow, fresh: Boolean, auto: Boolean, autotapOn: Boolean, keep: List<ManaUse>?, custom: Boolean = false,
            ways: List<Input>? = null, clear: Boolean = true, picks: ManaTaps = ManaTaps(),
            hand: org.asyncttwiz.automatum.HandCastOptions? = null, choosing: Boolean? = null): Outcome {
            val payment = next.pending as? Decision.ManaPayment
            if (auto && next.complete) complete(next, autotapOn, fresh = fresh)?.let { return it }
            // STK-024 (owner, 2026-11-09): a spell with convoke always opens the panel, also with Autotap on
            if (auto && payment != null && !convokes(next, payment)) {
                val chosen = chosen(next, payment, autotapOn, custom, keep, picks)
                if (chosen.isNotEmpty() && next.pays(chosen)) complete(next, autotapOn, chosen, fresh)?.let { return it }
            }
            val ahead = if (payment != null) next else HandCasts.ahead(next)
            val due = ahead?.pending as? Decision.ManaPayment
            val mana = if (ahead != null && due != null) chosen(ahead, due, autotapOn, custom, keep, picks) else emptyList()
            val pending = next.pending
            val listed = if (pending != null && pending !is Decision.Targets && pending !is Decision.ManaPayment) next.listedAnswers() else emptyList()
            val paid = if (ahead != null && due != null) ahead.pays(mana) else true
            val visible = Host.visible(next.state, PlayerId(viewer))
            val most = mostAnnounced(next)
            val selection = if (pending is Decision.Targets || pending is Decision.ChooseObjects) next.selection(emptyList()) else null
            // STK-024: the engine objects among the payment's sources that lie on the battlefield (tapped on the table)
            val field = if (ahead != null && due != null) (due.options + mana).mapNotNullTo(HashSet()) { use ->
                (use as? ManaUse.Ability)?.source?.takeIf { ahead.state.obj(it)?.zone == arcanum.api.Zone.Battlefield }
            } else emptySet<ObjectId>()
            return Outcome.Show(next, ahead, fresh) { value ->
                CastPanelValue(value, mana, paid, listed, visible, ways, clear,
                    most.map { (id, a) -> AnnouncedMost(id, a.most, a.unpayable) }, due, field, selection?.let(SelectionWire::of), hand, choosing)
            }
        }

        /** STK-024: whether [flow]'s spell has convoke (CR 702.51), its own or granted, whatever creatures there are to tap now. */
        private fun convokes(flow: CastFlow, payment: Decision.ManaPayment): Boolean {
            val tapsCreatures = arcanum.api.Mechanic.PayManaBy(arcanum.api.ManaPaymentWay.TapCreatures)
            if (payment.options.any { it is ManaUse.Special && it.way == arcanum.api.ManaPaymentWay.TapCreatures }) return true
            val spell = payment.source ?: return false
            return Engine.view(flow.state)[spell]?.characteristics?.abilities.orEmpty().any { instance ->
                (instance.ability as? arcanum.api.Ability.Static)?.mechanics.orEmpty().any { it == tapsCreatures }
            }.also { if (it) AppLog.info("cast", "The panel opens for a spell with convoke, no creature to tap") }
        }

        /** The mana chosen for [payment] of [flow]: with Autotap on its suggestion, around the player's picks once they made any;
         * with it off the player's sources [keep] carried to it ([ManaPicks.carry]; none until they tap some). */
        private fun chosen(flow: CastFlow, payment: Decision.ManaPayment, autotapOn: Boolean, custom: Boolean, keep: List<ManaUse>?, picks: ManaTaps): List<ManaUse> = when {
            autotapOn && !custom -> suggestion(flow, payment)
            autotapOn -> ManaTaps.payment(picks, payment, true, engineOf(flow))
            else -> ManaPicks.carry(payment, keep.orEmpty(), flow::pays, flow::fill)
        }

        /** STK-024, TBL-130 (owner playtest 2026-11-07): Autotap's suggestion for [payment] of [flow], taking the topmost members
         * of stacks of identical sources ([TopmostMana]); the engine's own when that doesn't pay. */
        private fun suggestion(flow: CastFlow, payment: Decision.ManaPayment): List<ManaUse> {
            val groups = stacks() ?: return payment.suggestion
            val topmost = TopmostMana.of(payment.suggestion, payment.options, groups, flow::atwCard)
            return if (topmost == payment.suggestion || flow.pays(topmost)) topmost else payment.suggestion
        }

        /** STK-023: the most each announcement of [flow] (open or answered) may be. */
        private fun mostAnnounced(flow: CastFlow): Map<arcanum.engine.decision.DecisionId, HandCasts.Announceable> {
            val most = mutableMapOf<arcanum.engine.decision.DecisionId, HandCasts.Announceable>()
            (flow.pending as? Decision.Announce)?.let { open -> HandCasts.announceable(flow)?.let {
                most[open.id] = it
                if (it.unpayable.isNotEmpty()) AppLog.info("cast", "X up to ${it.most}, can't be paid at ${it.unpayable.sorted()}")
            } }
            flow.steps.forEachIndexed { index, (decision, _) ->
                if (decision is Decision.Announce) runCatching { HandCasts.announceable(flow.back(index)) }.getOrNull()?.let { most[decision.id] = it }
            }
            return most
        }

        /** [next] completed and on its way out, or null when it isn't complete (a payment the suggestion doesn't make, with
         * [autotapOn]): its own inputs. */
        private fun complete(next: CastFlow, autotapOn: Boolean, picked: List<ManaUse>? = null, fresh: Boolean): Outcome? {
            val payment = next.pending as? Decision.ManaPayment
            val uses = when {
                next.complete -> null
                payment != null && picked != null -> picked
                payment != null && autotapOn && payment.suggestion.isNotEmpty() && next.pays(payment.suggestion) -> suggestion(next, payment)
                else -> return null
            }
            val sending = try { next.sending(uses) } catch (_: IllegalArgumentException) { return null }
            return Outcome.Engine(sending.flow, sending.cast.inputs.toList(), fresh)
        }

        private fun engineOf(at: CastFlow) = ManaTaps.Engine({ pinned, removed, exact -> at.fill(pinned, removed, exact) }, at::pays)

        private fun seeds() = CastFlow.Seeds { DeviceRandom.seed() }

        /** Where a cast on [play] runs: the game's own engine state (engine-responsiveness R4a). */
        private fun source(play: TableBoard): CastFlow.Source {
            val engine = requireNotNull(play.engine) { "No board to cast on" }
            return CastFlow.Source.engine(engine.state, play.board, play.gameId)
        }
    }

    companion object {
        const val TABLE_CHANGED = "The table changed: cast it again."
        const val NOT_ALLOWED = "Automatum doesn't allow that choice."
        const val FAILED = "Automatum couldn't work that cast out."
        /** STK-024: a payment that can't be made with the mana chosen. */
        fun notPaid(payment: Decision.ManaPayment) = "Not enough mana to pay ${payment.cost}."

        fun cardName(play: TableBoard, cardId: String): String {
            val card = play.board.cards.singleOrNull { it.id == cardId }
                ?: return Faults.placeholder("CastController", "card to name not on the board", "That card", mapOf("card" to cardId))
            return play.definitions.singleOrNull { it.digest == card.definition }?.definition?.faces
                ?.let { faces -> faces.singleOrNull { it.faceId == card.faceId } ?: faces.firstOrNull() }?.name
                ?: Faults.placeholder("CastController", "card to name without its definition", "That card", mapOf("card" to cardId))
        }

        fun land(play: TableBoard, cardId: String): Boolean {
            val card = play.board.cards.singleOrNull { it.id == cardId } ?: return false
            val front = play.definitions.singleOrNull { it.digest == card.definition }?.definition?.faces?.firstOrNull()
            return "Land" in StackContexts.types(front?.typeLine.orEmpty())
        }
    }
}

/** [ManaTaps] as it crosses, and back. */
fun ManaTaps.wire() = ManaTapsWire(tapped, chosen, outs)
fun ManaTapsWire.taps() = ManaTaps(tapped, chosen, outs)

/** The engine state a cast flow is at, worked out on the page from its board's own copy (web client step 4e): [board]'s
 * engine state with [changes] applied straight, as a new state. */
fun castState(board: GameState, changes: List<Change>): GameState = board.deepCopy().also { s -> changes.forEach(s::apply) }
