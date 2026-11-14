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
import arcanum.engine.state.StackItem
import arcanum.engine.state.TargetRef
import org.asyncttwiz.automatum.ManaPicks
import org.asyncttwiz.automatum.ManaWays
import org.asyncttwiz.automatum.boundary.ManaBelongs
import org.asyncttwiz.automatum.boundary.SharedAnswer
import org.asyncttwiz.automatum.boundary.SharedFlowValue
import org.asyncttwiz.automatum.boundary.SharedOp
import org.asyncttwiz.automatum.boundary.SharedRequest
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
 * further answers wait. [view] is the shown board's flow, set by the panel's host once worked out ([load]).
 *
 * Web client step 4e: the flows run as sessions where the engine is ([port]: in-process, a flow shown at once and its steps on
 * [work]; in the browser the engine's Web Worker beside the game's device), and the panels draw the plain data each answers
 * ([view], a [SharedShown]), never the live flow. A step's answer applies only while nothing replaced what it was made on.
 */
class SharedFlowController(
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
    /** STK-030, HIS-099 (owner problem report RC3J4K): how many times a board already shown was taken back, and the last
     * one's words ([EngineGame.takeBacks]). */
    private val takeBacks: () -> Long = { 0L },
    private val lastTakeBack: () -> String = { "" },
    /** Web client step 4e: where the flows' sessions run: in-process on [work] unless said otherwise ([TableSharedPort]: the
     * browser's engine worker when the game's device is there). */
    private val port: SharedPort = LocalSharedPort(viewer) { w, d -> work.run(w, d) },
    /** TBL-130: the stacks of identical battlefield cards on a board as the table lays them out (Autotap takes the topmost). */
    private val stacks: (TableBoard) -> List<List<String>> = { TopmostSources.stacks(BattlefieldLayout.groups(it, viewer)) },
) : FlowQuestions {
    /** The take-backs counted when the shown choice opened ([takeBacks]). */
    private var shownAt = 0L
    /** The flow of the shown board, null until worked out (or when there is none). */
    var view by mutableStateOf<SharedShown?>(null); private set
    /** The session the shown flows belong to (web client step 4e): opened with the first flow shown, closed with the panel. */
    private var session = 0L
    /** Bumped by each flow asked for or dropped: an older answer isn't shown. */
    private var loading = 0
    /** The eye (I-D10): the panel is hidden so the board can be looked at. */
    var hidden by mutableStateOf(false); private set
    /** "Respond" was tapped in this priority window (the board's event): the prompt is a slim bar while the player casts. */
    var responding by mutableStateOf<String?>(null); private set
    /** The decision the viewer confirmed answering for the other player (D11). */
    var choosingFor by mutableStateOf<String?>(null); private set
    /** Targets picked so far for a slot that takes several. */
    var picked by mutableStateOf<List<TargetRef>>(emptyList()); private set
    override var mana by mutableStateOf<List<ManaUse>>(emptyList()); private set
    /** Whether [mana] pays the open payment (worked out where the flow runs: with the flow, after each tap). */
    override var paid by mutableStateOf(false); private set
    /** STK-024: the sources the player tapped in the open payment (with their ways), the ways chosen with a source's corner chip,
     * and those they took out: Autotap fills in the rest. */
    private var taps = ManaTaps()
    /** Automatum is working out the last answer (SYS-025): nothing else is taken meanwhile. */
    var busy by mutableStateOf(false); private set
    private var shownDecision: String? = null

    /** Whether the flows run across the boundary (the browser's engine worker): the panel's host then asks for a board's flow
     * without working it out here. */
    val remote: Boolean get() = port.remote

    /**
     * Asks for [play]'s flow as its panels draw it, then shows it ([show]); in-process at once (the panel's host has worked
     * the board's flow out, off the main thread). None when the board has no flow Automatum can read.
     */
    fun load(play: TableBoard) {
        val run = ++loading
        if (session == 0L) session = nextSession()
        // Owner problem report RC3J4K: a take-back since the choice opened drops its picks, so the flow opens as a new choice
        val keeping = shownDecision.takeIf { takeBacks() == shownAt }
        val request = SharedRequest(session, board = play.values?.number,
            op = SharedOp.Show(autotap(), keeping, if (keeping != null) mana else emptyList()))
        port.ask(request, play, { stacks(play) }) { answer ->
            if (run != loading) return@ask
            when (answer) {
                is SharedAnswer.Show -> show(SharedShown(answer.value, play))
                else -> { AppLog.info("decision", "No flow to show on board ${request.board}: $answer"); show(null) }
            }
        }
    }

    /** Shows [next], the flow of the board now shown; a new decision starts with its suggestion and the panel shown. */
    fun show(next: SharedShown?) {
        if (next == null) {
            loading++
            if (session != 0L) {
                port.ask(SharedRequest(session, op = SharedOp.Close("no flow shown")), null, { null }) {}
                session = 0L
            }
        }
        if (next === view) return
        // Owner problem report RC3J4K: a take-back (an undo or rewind, a branch) since the choice opened drops what was picked
        // in it, also when the board it went back to asks the same choice again: the choice begins anew
        val back = takeBacks()
        if (back != shownAt) {
            shownAt = back
            if (shownDecision != null) {
                AppLog.info("decision", "Open choice $shownDecision dropped with its picks (${picked.size}): the table went back (${lastTakeBack()})")
                shownDecision = null
                choosingFor = null
                responding = null
            }
        }
        val id = next?.decision?.id?.value
        // What a report needs of a pick gone missing: whether the panel was kept over this board or moved to another choice
        if (id != shownDecision) AppLog.info("decision", "The panel's choice changes on a new board: $shownDecision to $id")
        else if (id != null) AppLog.info("decision", "Choice $id still open on a new board: the panel and its picks stay")
        if (id != shownDecision) {
            shownDecision = id
            picked = emptyList()
            hidden = false
            taps = ManaTaps()
            // STK-049 (owner gallery review, 2026-11-06): with Autotap on a payment opens with its sources picked, off none
            // (worked out with the flow where it runs)
            mana = next?.value?.mana.orEmpty()
            paid = next?.value?.paid ?: false
            if (choosingFor != id) choosingFor = null
        } else if (next != null) {
            if (next.value.mana == mana) paid = next.value.paid else recheckPaid(next)
        }
        view = next
    }

    /** The picks kept on a new board asking the same decision, changed while its flow was worked out: whether they pay there. */
    private fun recheckPaid(next: SharedShown) {
        val now = mana
        port.ask(SharedRequest(session, next.version, next.value.board, SharedOp.Pays(now)), next.play, { null }) { answer ->
            if (view === next && mana === now && answer is SharedAnswer.Mana) paid = answer.paid
        }
    }

    /** The open decision, when the viewer answers it: theirs, or the other player's after "Choose for them". */
    val answering: Decision? get() = view?.decision?.takeIf { it.player.value == viewer || choosingFor == it.id.value }

    /** The open decision the viewer answers when it is picked on the table: targets, or (D1) a choice of battlefield
     * permanents ([TablePick]). */
    val pick: TablePick? get() {
        val decision = answering ?: return null
        val flow = view ?: return null
        pickOf?.let { (of, pick) -> if (of === decision) return pick }
        return TablePick.of(decision, flow.state).also { pickOf = decision to it }
    }
    private var pickOf: Pair<Decision, TablePick?>? = null

    /** The open decision is picked by the viewer on the table ([pick]), on the board they act on: until the panel has the
     * new board's flow (worked out off the main thread) a tap isn't taken as a pick for the previous board's decision;
     * a board asking that very decision is no other decision ([flowOn]). */
    val targeting: Boolean get() = pick != null && current()?.let(::flowOn) != null

    /**
     * STK-030: the flow the panel shows on [play]: that board's own, or, until it is worked out off the main thread
     * ([load]), the flow of the board before when [play] asks the very same open decision (the other player's event beside
     * the game brought a new board): the panel stays on the table and what is picked in it with it. The whole decision is
     * compared, as its id can come again for another question on another line.
     */
    fun flowOn(play: TableBoard): SharedShown? = view?.takeIf { flow ->
        flow.board === play.board || flow.state.pending?.decision?.let { it == play.engine?.state?.pending?.decision } == true
    }

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
        return sendCall(play, SharedOp.Pass(player))
    }

    /**
     * The step arrow with Automatum on (TRN-057): the player holding priority passes (TRN-004, D11); with attackers or
     * blockers awaited it declares what the combat lanes hold. With something on the Stack it is "Resolve": the holder
     * passes as with Let it resolve (I-D46 d).
     */
    fun step(): Boolean {
        val play = current() ?: return false
        stepRefusal(play)?.let { fail(it); return false }
        return sendCall(play, SharedOp.Step(autotap()))
    }

    /**
     * Problem report XGN86Q: who declares attackers or blockers on [play] now (the step arrow then declares what the lanes
     * hold), from the board's own flow: the shown one's, else the board's (in-process worked out now, waiting for the engine
     * thread as before; across the boundary asked of the worker), given to [done] on the main thread. No panel shows at a
     * declaration, so the panel's flow is seldom there to ask.
     */
    fun declaring(play: TableBoard, done: (String?) -> Unit) {
        view?.takeIf { it.board === play.board }?.let { return done(it.declaring) }
        if (!port.remote) return done(AutomatumBoards.sharedValue(play)?.declaring)
        port.ask(SharedRequest(session, board = play.values?.number, op = SharedOp.Declaring), play, { null }) { answer ->
            done((answer as? SharedAnswer.Declaring)?.player)
        }
    }

    /** Why the step button can't act on [play] at all (nothing is worked out then), or null. */
    private fun stepRefusal(play: TableBoard): String? {
        if (play.flowOpen) return BUSY
        // I-D46 d: with something on the Stack the step arrow is "Resolve": its holder passes, as Let it resolve does
        val stacked = CombatStackRules.stack(play.board).cards.isNotEmpty()
        if (stacked && play.board.stackEffects.none { it.item != null }) return STACK_WAITING
        // TRN-027: the opening (mulligans) is the host's setup; the engine's game begins after it (CR 103.8)
        if (play.board.turn.number == "1" && PlayerStatus.anyOpening(play.board)) return OPENING
        return null
    }

    override fun answer(answer: Answer): Boolean = answerWith(answer, emptyList())

    /** I-D48: a two-area choice's answer and the orders the engine then asks of the same cards, as one step
     * ([org.asyncttwiz.automatum.SharedFlow.answer]); a division of combat damage deals it (I-D29). */
    override fun answerWith(answer: Answer, then: List<Answer.Objects>): Boolean =
        answerOpen("$answer then $then") { decision -> SharedOp.Give(decision, answer, then) }

    /** STK-030, CR 616.1: the replacement preference list as one step ([org.asyncttwiz.automatum.SharedFlow.answerReplacements]). */
    override fun answerReplacements(decision: Decision.ChooseReplacement, preference: List<arcanum.engine.decision.ReplacementOption>): Boolean =
        answerOpen("replacements $preference") { open -> SharedOp.Replacements(open, preference) }

    override fun paymentAfter(decision: Decision): Decision.ManaPayment? = view?.takeIf { it.decision?.id == decision.id }?.payment

    /** STK-049: the open payment with the sources picked, or "yes" to the open "you may pay" and its payment, as one step. */
    override fun payMana(): Boolean {
        val uses = mana
        return answerOpen("mana $uses") { decision -> SharedOp.Pay(decision, uses) }
    }

    /** Sends [op] on the open decision the panel shows ([op] gets its id), unless the table changed meanwhile. */
    private fun answerOpen(what: String, op: (String) -> SharedOp): Boolean {
        // Report DH3QX6: each answer tapped, and below what became of it
        AppLog.info("decision", "Answer tapped on $shownDecision: ${what.take(400)}${if (busy) " (still working out the last one)" else ""}")
        val play = current() ?: run { AppLog.info("decision", "Answer not sent: no board"); return false }
        val open = view?.takeIf { it.board === play.board }?.decision
        if (open != null && open.id.value != shownDecision) {
            AppLog.info("decision", "Answer not sent: the panel shows $shownDecision, the board asks ${open.id.value}")
            fail(SharedSessions.LOOK_AGAIN); return false
        }
        // Checked again where the flow runs: the board's open decision must still be the one shown
        return sendCall(play, op(shownDecision.orEmpty()))
    }

    /**
     * Answers the open decision with the early answer given for it (I-D33, #8: given while the batch that opened the
     * decision was on its way), then Automatum goes on, as one batch. The client of the player who made the latest event
     * does it as soon as it shows such a board ([SharedFlowHost]); the panel shows the early answer pre-filled meanwhile.
     */
    fun answerEarly(): Boolean {
        val play = current() ?: return false
        return sendCall(play, SharedOp.Early)
    }

    /** A tap on a table item while the open decision is picked on the table (I-D5: players by their life squares; D1:
     * battlefield permanents chosen as targets are). */
    fun tapTarget(item: TableItem): Boolean {
        val pick = pick ?: return false
        val flow = view ?: return false
        // UI review round 2 #4 (I-D46 b): not a legal target: false, the table shakes it
        val target = targetOf(flow, pick.options, item) ?: return false
        fail(null)
        if (pick.max == 1 && pick.min <= 1) return answer(pick.answer(listOf(target)))
        picked = if (target in picked) picked - target else picked + target
        if (pick.max?.let { picked.size >= it } == true) return confirmTargets()
        return true
    }

    fun confirmTargets(): Boolean {
        val pick = pick ?: return false
        if (!pickDone) return false
        return answer(pick.answer(picked))
    }

    /** Done (or None) can be pressed: as many are picked as the open choice takes. */
    val pickDone: Boolean get() = pick?.let { p -> picked.size >= p.min && p.max.let { it == null || picked.size <= it } } == true

    /** The table items that glow as legal picks of the open decision the viewer answers. */
    fun glow(play: TableBoard): Set<String> {
        val flow = view ?: return emptySet()
        val pick = pick ?: return emptySet()
        return targetGlow(play, pick.options.map(flow::ref))
    }

    /**
     * STK-047, STK-030 (owner 2026-11-11, which pick is which): the text of the trigger (or other announcement) whose targets
     * the viewer picks on the table, with the phrase picked now and what was chosen for the ones before
     * ([org.asyncttwiz.automatum.PickText]); null when the open pick isn't part of one or there is no text. Its words are
     * public: an ability on the Stack is (the source card may have left the battlefield or be hidden).
     */
    fun pickText(play: TableBoard): org.asyncttwiz.automatum.PickText? {
        val flow = view ?: return null
        val pick = pick ?: return null
        pickTextOf?.let { (of, now, text) -> if (of === pick.decision && now == picked) return text }
        val announcing = org.asyncttwiz.automatum.PickText.announcing(flow.state.pending?.continuation, pick.decision)
        val source = when (val what = announcing?.what) {
            is arcanum.engine.decision.Announced.Ability -> what.source
            is arcanum.engine.decision.Announced.Trigger -> what.trigger.source
            is arcanum.engine.decision.Announced.Spell -> what.card
            null -> null
        }
        val text = announcing?.let {
            runCatching { org.asyncttwiz.automatum.PickText.of(flow.state, it, pick.decision, emptyList(), picked,
                source?.let { s -> rulesTextOf(play, atwCard(s)) }) }
                .onFailure { problem -> AppLog.warn("decision", "No pick text for ${pick.decision.id.value}: $problem") }.getOrNull()
        }
        if (pickTextOf?.first !== pick.decision) AppLog.info("decision", "Pick text for ${pick.decision.id.value} (${pick.decision.kind}, " +
            "${if (flow.state.pending?.continuation is arcanum.engine.decision.Continuation.PlacingTriggers) "placing triggers" else "other"}, source $source): " +
            (text?.let { t -> "now \"${t.now}\", ${t.parts.size} parts, roles ${t.roles.map { it.second }}" } ?: "none"))
        pickTextOf = Triple(pick.decision, picked, text)
        return text
    }
    private var pickTextOf: Triple<Decision, List<TargetRef>, org.asyncttwiz.automatum.PickText?>? = null

    /** STK-047 (owner 2026-11-11): what each table card picked so far is for ("Target"), by its card id, for its chip. */
    fun pickRoles(play: TableBoard): Map<String, String> {
        val flow = view ?: return emptyMap()
        return pickText(play)?.roles.orEmpty().mapNotNull { (ref, role) -> (flow.ref(ref) as? MtgRef.Card)?.cardId?.let { it to role } }.toMap()
    }

    /** STK-047: the table items picked so far for a decision that takes several, drawn as picked. */
    fun pickedGlow(play: TableBoard): Set<String> {
        val flow = view ?: return emptySet()
        if (pick == null) return emptySet()
        return targetPicks(play, picked.map(flow::ref))
    }

    /** The Stack entries that are legal targets of the open decision the viewer answers, top first. */
    fun stackTargets(play: TableBoard): List<String> {
        val flow = view ?: return emptyList()
        val pick = pick ?: return emptyList()
        return targetEntries(play, pick.options.map(flow::ref))
    }

    override val engine: GameState? get() = view?.state ?: null.also { Faults.report("SharedFlowController", "engine state read with no flow open") }
    override fun atwCard(id: ObjectId): String? = view?.atwCard(id)
    /** Worked out with the flow where it runs ([SharedFlowValue.listed], SYS-025). */
    override fun listedAnswers(): List<Answer> = view?.value?.listed.orEmpty()

    /** A choice up to a total (CR 701.59a): OK waits for the total, as in a cast or crew; any other choice is as the decision
     * says. Worked out where the flow runs. */
    override fun selectionOf(chosen: List<ObjectId>, done: (arcanum.engine.Selection?) -> Unit) {
        val flow = view?.takeIf { (it.decision as? Decision.ChooseObjects)?.total != null } ?: return done(null)
        port.ask(SharedRequest(session, flow.version, flow.value.board, SharedOp.Selection(chosen.map { TargetRef.Object(it) })),
            flow.play, { null }) { answer -> done((answer as? SharedAnswer.Selected)?.selection?.selection()) }
    }

    /** The tapped sources are a set: their colours are worked out together ([ManaTaps], as in a cast), where the flow runs
     * (SYS-025: up to a few dozen engine calls). A pool or life chip in the bar. */
    override fun toggleMana(group: List<ManaUse>) {
        val key = ManaPicks.key(group.first())
        tapWays(group, "chip ${ManaWays.label(group.first())}", ManaBelongs.Key(key))
    }

    /** STK-049 (owner playtest 2026-11-09): the open payment the viewer makes while something resolves, picked on the table: a
     * [Decision.ManaPayment], or the payment after a "you may pay" ([SharedShown.payment]); null when none is open for them. */
    val payment: Decision.ManaPayment? get() {
        val decision = answering ?: return null
        return decision as? Decision.ManaPayment ?: view?.takeIf { decision is Decision.May }?.payment
    }

    /** STK-049 (owner playtest 2026-11-09): the open payment is made on the table: its sources are tapped there, the panel is a slim
     * bar and Pay is the main button; on the board the viewer acts on ([flowOn]). */
    val paying: Boolean get() = payment != null && current()?.let(::flowOn) != null

    /** STK-024: the table card [use] is tapped on: a battlefield source's mana ability or a way to pay with it alone; null for pool
     * mana, life and a source used with other objects (chips in the bar). */
    fun tableSource(use: ManaUse): String? {
        val flow = view ?: return null
        return when (use) {
            is ManaUse.Ability -> if (use.answers.any { it is Answer.Objects }) null
                else flow.atwCard(use.source).takeIf { flow.state.obj(use.source)?.zone == arcanum.api.Zone.Battlefield }
            is ManaUse.Special -> use.objects.singleOrNull()?.let(flow::atwCard)
            else -> null
        }
    }

    /** STK-024: the ways the table card [cardId] can pay the open payment, in the engine's order. */
    fun waysOf(cardId: String): List<ManaUse> = payment?.options.orEmpty().filter { tableSource(it) == cardId }

    /** STK-024: the way the table card [cardId] pays the open payment now, null when it doesn't. */
    fun paidWith(cardId: String): ManaUse? = if (!paying) null else mana.firstOrNull { tableSource(it) == cardId }

    /** STK-049: the table cards paying the open payment (outlined in amber, in a pile of their own). */
    fun paySources(): Set<String> = if (!paying) emptySet() else mana.mapNotNull(::tableSource).toSet()

    /** STK-049: the table cards a tap adds to the open payment (or takes out). */
    fun payableSources(): Set<String> = if (!paying) emptySet() else payment?.options.orEmpty().mapNotNull(::tableSource).toSet()

    /** STK-049 (owner playtest 2026-11-09): a tap on the battlefield card [cardId] while the viewer pays on the table: a source the
     * payment can use is added (its sensible way, STK-024) or taken out; false when it isn't one. */
    fun tapSource(cardId: String): Boolean {
        if (!paying) return false
        val ways = waysOf(cardId).ifEmpty { return false }
        AppLog.info("decision", "Payment while resolving: source $cardId tapped (${ways.size} ways), paying before ${paySources()}")
        tapWays(ways, "source $cardId", ManaBelongs.Uses(ways))
        return true
    }

    /** STK-024 (owner playtest 2026-11-09): [way] chosen with the corner chip of the table card [cardId]: kept exactly. */
    fun chooseWay(cardId: String, way: ManaUse) {
        val flow = view ?: return
        payment ?: return
        if (busy || !paying) return
        val mine = waysOf(cardId)
        payStep(flow, mana, SharedOp.ChooseWay(cardId, way, mine, mana, taps.wire(), autotap()))
    }

    private fun tapWays(ways: List<ManaUse>, what: String, belongs: ManaBelongs) {
        val flow = view ?: return
        payment ?: return
        if (busy) return
        payStep(flow, mana, SharedOp.Tap(ways, belongs, what, mana, taps.wire(), autotap()))
    }

    /** Works the payment's [op] out where the flow runs and applies it while the same flow and payment are shown. */
    private fun payStep(flow: SharedShown, current: List<ManaUse>, op: SharedOp) {
        busy = true
        port.ask(SharedRequest(session, flow.version, flow.value.board, op), flow.play, { null }) { answer ->
            busy = false
            val done = answer as? SharedAnswer.Mana ?: run {
                AppLog.warn("automatum", "Mana tapping couldn't be worked out: $answer")
                return@ask
            }
            AppLog.info("decision", "Payment while resolving: ${done.said}; paying now ${done.mana.joinToString { ManaWays.label(it) }}" +
                (if (done.paid) ", paid" else ", not paid yet"))
            if (view === flow && mana === current) { mana = done.mana; taps = done.taps.taps(); paid = done.paid }
        }
    }

    /** I-D27: the viewer sees what the rules let them see; the deciding player also the cards their own choice offers in
     * zones that aren't public (their library during a search). An object that left a public zone face up (a trigger's
     * source that died) shows what it was there (CR 608.2h). Objects in public zones are named only when visible to
     * the viewer, so a face-down creature stays hidden from the player dividing damage onto it (#4). Someone choosing for
     * the other player sees that player's hidden cards only as hidden cards. An option that has left (STK-060) shows what it was. */
    override fun shown(id: ObjectId): Boolean {
        val flow = view ?: return false
        if (id is ObjectId.StackItem) return true
        if (id in flow.value.visible) return true
        if (id in flow.value.left) return true
        // A trigger's source that has left the battlefield (3UE2NW): what it was there is public
        if (org.asyncttwiz.automatum.LastKnown.public(flow.state, id)) return true
        val zone = flow.state.obj(id)?.zone
        return flow.decision?.player?.value == viewer && zone != null && zone !in PUBLIC_ZONES
    }

    /**
     * Sends [op], made on [play]'s board, where the flows run and sends the viewer's own inputs it comes to, unless the board
     * moved on meanwhile. True when it was taken (in-process with [AutomatumWork.Inline]: what the result says).
     */
    private fun sendCall(play: TableBoard, op: SharedOp): Boolean {
        val route = engineSend ?: run { fail("Automatum isn't available here."); return false }
        if (busy) { AppLog.info("decision", "Flow step not taken: the last one is still being worked out"); return false }
        busy = true
        var result = true
        val request = SharedRequest(session, view?.version, play.values?.number, op)
        port.ask(request, play, { stacks(play) }) { answer ->
            busy = false
            result = deliver(play, answer, route)
        }
        return result
    }

    /**
     * SYS-011 (CoreLoopTest race, 2026-11-10): whether the board shown now still holds the decision [worked] was worked out
     * on ([GameDevice.sameDecision], the same lanes): the player's own press taken back and made again when the other
     * player's event came first only republished it. The inputs then go out, and the device checks them again on its own
     * board (SYS-057), as a tap made on an older board with the same decision is.
     */
    private fun sameDecision(worked: TableBoard): Boolean {
        val now = current() ?: return false
        val nowEngine = now.engine
        val workedEngine = worked.engine
        val same = now.board.combat == worked.board.combat && nowEngine != null && workedEngine != null &&
            org.asyncttwiz.protocol.game.GameDevice.sameDecision(workedEngine.state, nowEngine.state)
        if (same) AppLog.info("decision", "Flow step worked out on an older board: the same decision on the board shown, sent")
        return same
    }

    /** [answer] to a step made on [play], on the main thread: its inputs go out unless the board moved on meanwhile. True when
     * it was taken. */
    private fun deliver(play: TableBoard, answer: SharedAnswer, route: (List<arcanum.engine.input.Input>) -> String?): Boolean = when (answer) {
        is SharedAnswer.Refuse -> { AppLog.info("decision", "Flow step not sent: ${answer.message}"); answer.message?.let(fail); false }
        SharedAnswer.Empty -> { AppLog.info("decision", "Flow step: nothing to send"); fail(null); picked = emptyList(); true }
        is SharedAnswer.Send -> when {
            current()?.board !== play.board && !sameDecision(play) -> {
                AppLog.info("decision", "Flow step not sent: the board moved on"); fail(SharedSessions.TABLE_CHANGED); false }
            else -> {
                val refusal = route(answer.inputs)
                AppLog.info("decision", "Flow step ${if (refusal == null) "sent" else "refused: ${refusal.take(300)}"}: ${answer.inputs.toString().take(300)}")
                when (refusal) {
                    null -> { fail(null); picked = emptyList(); true }
                    org.asyncttwiz.protocol.game.GameDevice.REVEALS_HIDDEN -> { fail(CastController.WAIT_TO_CATCH_UP); false }
                    else -> { fail(CastController.NOT_ALLOWED); false }
                }
            }
        }
        else -> { AppLog.warn("decision", "Flow step answered unexpectedly: $answer"); false }
    }

    private fun targetOf(flow: SharedShown, options: List<TargetRef>, item: TableItem): TargetRef? {
        if (item.kind == TableItemKind.LIFE) return options.firstOrNull { it == TargetRef.Player(PlayerId(item.owner ?: return null)) }
        val ids = listOfNotNull(item.sourceId, item.cardId)
        return options.firstOrNull { target ->
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
        const val STACK_WAITING = SharedSessions.STACK_WAITING
        /** TRN-027: with Automatum on the first turn begins once both players are Ready. */
        const val OPENING = "The game begins once both players are Ready."
        const val TAKE_OVER = SharedSessions.TAKE_OVER
        const val NOTHING_TO_PASS = SharedSessions.CANT_GO_ON

        /** The table's shared-flow sessions, numbered across the page (web client step 4e: the engine side keys them by game). */
        private val sessions = org.asyncttwiz.appui.AtomicLong()
        private fun nextSession(): Long = sessions.incrementAndGet()
    }
}

/**
 * Web client step 4e: a board's shared flow as the shared panels draw it: the plain data its session answered ([value]) on the
 * board it is of ([play]), never the flow itself. Its engine state ([state]) is the board's own (in-process the flow's copy of
 * it; in the browser the page's copy of the board's), read for names and what is shown, never changed.
 */
class SharedShown(val value: SharedFlowValue, val play: TableBoard) {
    /** The engine state of the board (names, the open decision's options). */
    val state: GameState by lazy { value.flow?.state ?: requireNotNull(play.engine) { "A flow's board without its engine state" }.state }
    /** The session's version of this flow. */
    val version: Long get() = value.version
    /** The board the flow is on. */
    val board: BoardState get() = play.board
    /** The open decision (a division given early as its suggestion, I-D33), or null. */
    val decision: Decision? get() = value.decision
    /** Who holds priority with something on the Stack and nothing to decide; null otherwise. */
    val priority: String? get() = value.priority
    /** Who holds priority now with nothing to decide (an empty Stack too), or null. */
    val holder: String? get() = value.holder
    /** Who declares attackers or blockers now (CR 508.1, 509.1), or null. */
    val declaring: String? get() = value.declaring
    /** The open division of combat damage in ATW's terms, or null. */
    val division: org.asyncttwiz.automatum.DamageDivision? get() = value.division
    /** STK-049: the payment the open "you may pay" of a mana cost goes on to, or null. */
    val payment: Decision.ManaPayment? get() = value.payment
    /** The top of the Stack, which resolves next (CR 405.5); null when the Stack is empty. */
    val top: StackItem? get() = state.stack.lastOrNull()?.let { state.stackItems[it] }

    /** The printed text of [item]'s ability (a spell's text, an ability's line), when it has one. */
    fun text(item: StackItem): String? = org.asyncttwiz.automatum.CastFlow.text(item.ability)

    /** The ATW reference of an engine object or player (target glow, labels). */
    fun ref(target: TargetRef): MtgRef = when (target) {
        is TargetRef.Player -> MtgRef.Player(target.id.value)
        is TargetRef.Object -> when (val id = target.id) {
            is ObjectId.Card -> MtgRef.Card(id.card.value, id.zoneChanges)
            is ObjectId.StackItem -> MtgRef.StackItem(id.id)
        }
    }

    /** The ATW card of an engine object, when it is a card. */
    fun atwCard(id: ObjectId): String? = (id as? ObjectId.Card)?.card?.value
}
