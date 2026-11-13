package org.asyncttwiz.android

import org.asyncttwiz.protocol.Faults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import arcanum.engine.Engine
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.decision.ManaUse
import arcanum.engine.input.Input
import arcanum.engine.state.GameState
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.state.TargetRef
import org.asyncttwiz.automatum.AutomatumLog
import org.asyncttwiz.automatum.boundary.CastAnswer
import org.asyncttwiz.automatum.boundary.CastChange
import org.asyncttwiz.automatum.boundary.CastChoices
import org.asyncttwiz.automatum.boundary.CastFlowValue
import org.asyncttwiz.automatum.boundary.CastOp
import org.asyncttwiz.automatum.boundary.CastRequest
import org.asyncttwiz.automatum.boundary.HandChoiceWire
import org.asyncttwiz.automatum.boundary.ManaBelongs
import org.asyncttwiz.automatum.HandCasts
import org.asyncttwiz.automatum.ManaPicks
import org.asyncttwiz.automatum.ManaWays
import org.asyncttwiz.protocol.*
import org.asyncttwiz.appui.newSingleThreadExecutor
import org.asyncttwiz.appui.postToMain
import org.asyncttwiz.appui.simpleClassName

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
        private val worker = newSingleThreadExecutor("automatum", daemon = true)
        override fun <T> run(work: () -> T, done: (T) -> Unit) {
            worker.execute { val result = work(); postToMain { done(result) } }
        }
        override fun <A, B> staged(first: () -> A, shown: (A) -> Unit, next: (A) -> (() -> B)?, done: (B) -> Unit) {
            worker.execute {
                val a = first()
                postToMain { shown(a) }
                val second = next(a) ?: return@execute
                val b = second()
                postToMain { done(b) }
            }
        }
    }
}

/**
 * Casting from the hand through Automatum (integration spec §3 I2a): one private flow at a time (I-D11). Its engine
 * calls stay on this device and go out as the player's own inputs ([engineSend]) when the cast is complete; Undo or Back
 * drops it, sending nothing. While it is open nothing else changes the game ([open] blocks the toolkit's actions, D32);
 * looking stays free (I-D10).
 *
 * Web client step 4e: the flow runs as a session where the engine is ([port]: in-process on [work], off the main thread in
 * the app, SYS-025; in the browser the engine's Web Worker beside the game's device), and the panel draws the plain data
 * each step answers ([flow], a [CastShown]), never the flow itself. Meanwhile [busy] is true, the panel shows it and takes
 * no input. A step's result applies only if nothing replaced it (Cancel, another step).
 *
 * [current]: the board the player acts on, null while they can't act (the timeline reviewed, offline); the cast follows
 * only that board, never one shown in review. [fail]: a message for the player.
 */
class CastController(
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
    /** HIS-110 (owner, 2026-11-07): the other player's reveal panel is open: nothing is cast until its OK. */
    private val revealing: () -> Boolean = { false },
    /** STK-025, HIS-099 (owner problem report RC3J4K): how many times a board already shown was taken back (an undo or
     * rewind, a branch, an own event refused), and the last one's words ([EngineGame.takeBacks]). */
    private val takeBacks: () -> Long = { 0L },
    private val lastTakeBack: () -> String = { "" },
    /** Web client step 4e: where the cast's flow session runs: in-process on [work] unless said otherwise ([TableCastPort]: the
     * browser's engine worker when the game's device is there). */
    private val port: CastPort = LocalCastPort(viewer) { w, d -> work.run(w, d) },
    /** TBL-130: the stacks of identical battlefield cards on a board as the table lays them out (Autotap takes the topmost). */
    private val stacks: (TableBoard) -> List<List<String>> = { TopmostSources.stacks(BattlefieldLayout.groups(it, viewer)) },
) : FlowQuestions {
    /** The take-backs counted when the open cast began ([takeBacks]): a later one took the table back behind it. */
    private var startedAt = 0L
    /** The open cast's session where the flow runs (web client step 4e). */
    private var session = 0L
    /** The open cast as the panel draws it, null when none. */
    var flow by mutableStateOf<CastShown?>(null); private set
    /** The plays the engine offers for the card being cast (its ways: faces, alternative costs). */
    var ways by mutableStateOf<List<Input>>(emptyList()); private set
    /** STK-024 (owner 2026-11-07): the mana uses that pay the cast ([payment]): Autotap's suggestion while the player
     * hasn't changed it ([custom]) with Autotap on, else the sources the player tapped on the table. */
    override var mana by mutableStateOf<List<ManaUse>>(emptyList()); private set
    /** Whether [mana] pays [payment] (true when the cast has none). */
    override var paid by mutableStateOf(false); private set
    /** STK-024 (owner 2026-11-07): the payment the open cast comes to ([HandCasts.ahead]: the open one, or the one it reaches
     * with the questions before it answered as the chips are worked out), shown in the panel while it is open; null when
     * it has none. */
    var payment by mutableStateOf<Decision.ManaPayment?>(null); private set
    /** The engine objects among [payment]'s sources that lie on the battlefield (tapped on the table). */
    private var fieldSources: Set<ObjectId> = emptySet()
    /** Whether the player changed the payment by tapping sources: with Autotap on, what they picked stays and what they took
     * out isn't used again while Autotap fills in the rest ([taps]); with it off, only what they picked pays. */
    var custom by mutableStateOf(false); private set
    /** STK-024 (owner 2026-11-08, 2026-11-09): the sources the player tapped in this cast (with their ways), the ways chosen with a
     * source's corner chip, and the sources taken out. */
    private var taps = ManaTaps()
    /** ZON-080, ZON-082 (owner 2026-11-07): a hand card's cast panel's ways and extras (the way and extras chosen in it);
     * null for any other cast (a chip's, a card menu's). */
    var hand by mutableStateOf<org.asyncttwiz.automatum.HandCastOptions?>(null); private set
    /** A hand card's cast panel opened before its first question while it has ways or extras to choose: its targets
     * wait for Cast (the panel isn't a target bar yet). */
    var choosing by mutableStateOf(false); private set
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

    /** STK-023 (owner playtest 2026-11-05 evening): the most each announced number of the open cast (an X, open or
     * answered) may be so that the cast can still be paid now, by decision ([HandCasts.mostAnnounced]: below its least
     * when even that can't be paid). */
    private var announcedMost by mutableStateOf<Map<arcanum.engine.decision.DecisionId, HandCasts.Announceable>>(emptyMap())

    override fun most(decision: Decision): Int? {
        val own = super.most(decision)
        val payable = announcedMost[decision.id]?.most ?: return own
        return own?.let { minOf(it, payable) } ?: payable
    }

    /** STK-052: a value of an X known not to be payable (a gap a reduction leaves) greys Cast at the X step. */
    override fun payable(decision: Decision, value: Int): Boolean = announcedMost[decision.id]?.unpayable?.contains(value) != true

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
    /** The open question when it is picked on the table: targets, or (D1) a choice of battlefield permanents, such as a
     * creature to sacrifice ([TablePick]). */
    val pick: TablePick? get() {
        val f = flow ?: return null
        val decision = f.pending ?: return null
        val candidates = selection?.candidates
        pickOf?.let { (of, known, pick) -> if (of === decision && known == candidates) return pick }
        return TablePick.of(decision, f.state, candidates).also { pickOf = Triple(decision, candidates, it) }
    }
    private var pickOf: Triple<Decision, List<TargetRef>?, TablePick?>? = null
    /** Castability §3: what may be picked in the open choice given [picked], worked out where the flow runs ([reselect]): the
     * tap's own response is immediate, the glows follow. */
    private var selection by mutableStateOf<arcanum.engine.Selection?>(null)
    /** The picks [selection] was worked out for. */
    private var selectionFor by mutableStateOf<List<TargetRef>>(emptyList())
    private var selectionRun = 0
    /** Castability §3: Done (or None) can be pressed: [picked] is a selection that can be paid; false while that is worked out. */
    val pickDone: Boolean get() {
        val open = pick ?: return false
        return picked.size >= open.min && selectionFor == picked && selection?.done != false
    }
    /** The open question is picked on the table ([pick]): the panel is a slim bar. */
    val targeting: Boolean get() = pick != null && !choosing
    /** STK-024 (owner 2026-11-07): the open panel takes the table's mana sources: a tap on one adds it to the payment or
     * takes it out ([tapSource]). */
    val paying: Boolean get() = flow != null && !targeting && payment != null

    /** A new session for a cast about to begin ([starting] set with it). */
    private fun opening(cardId: String, text: String?) {
        session = nextSession()
        starting = cardId; startedAt = takeBacks()
        hidden = false
        abilityText = text
    }

    /** The player's choices a step carries ([CastChoices]). */
    private fun choices() = CastChoices(autotap, mana, custom, taps.wire())

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
        if (revealing()) { fail(REVEAL_BUSY); return false }
        if (play.flowOpen) { fail(SharedFlowController.BUSY); return false }
        if (inHandRow(play, cardId, viewer) != true) { fail(NOT_YOURS); return false }
        val autotapOn = autotap
        opening(cardId, null)
        return step(CastOp.Start(cardId, way, extras, autotapOn), play)
    }

    /**
     * ZON-079, ZON-080 (owner 2026-11-07): a tap on the raised hand card [cardId] (a double-tap, a drag, the "Cast…" chip):
     * a card without any choice (one complete way asking nothing before its payment) is cast at once as [start] does;
     * otherwise the cast panel opens with its ways and extras ([hand]) and its questions, and stops before the payment so
     * the player can see and change what pays. [known]: its chips when already worked out. False with a message when it
     * can't be begun.
     */
    fun startFromHand(cardId: String, known: org.asyncttwiz.automatum.HandCastChips? = null): Boolean {
        val play = current() ?: return false
        if (open) { fail(BUSY); return false }
        if (revealing()) { fail(REVEAL_BUSY); return false }
        if (play.flowOpen) { fail(SharedFlowController.BUSY); return false }
        if (inHandRow(play, cardId, viewer) != true) { fail(NOT_YOURS); return false }
        val autotapOn = autotap
        opening(cardId, null)
        return step(CastOp.FromHand(cardId, known, autotapOn), play)
    }

    /** ZON-080 (owner 2026-11-07): a tap on way chip [index] of a hand card's cast panel: the cast goes on that way, its
     * extras off, keeping the answers that still fit. A way the player can't pay for now does nothing. */
    fun handWay(index: Int): Boolean = handChoice { o ->
        if (index == o.way || o.ways.getOrNull(index)?.affordable != true) null else index to emptyList()
    }

    /** ZON-082: a tap on extra chip [index] of a hand card's cast panel turns it on or off (a repeatable one counts up);
     * one the player can't pay for now is never turned on. */
    fun handExtra(index: Int): Boolean = handChoice { o ->
        val chip = o.extras.getOrNull(index) ?: return@handChoice null
        val now = o.times.getOrElse(index) { 0 }
        val next = if (chip.repeatable) now + 1 else if (now > 0) 0 else 1
        if (next > now && !chip.affordable) null else o.way to o.times.toMutableList().also { it[index] = next }
    }

    /** ZON-082: the small "−" of a repeatable extra in the cast panel: once fewer. */
    fun handLess(index: Int): Boolean = handChoice { o ->
        val now = o.times.getOrElse(index) { 0 }
        if (now <= 0) null else o.way to o.times.toMutableList().also { it[index] = now - 1 }
    }

    private fun handChoice(choose: (org.asyncttwiz.automatum.HandCastOptions) -> Pair<Int, List<Int>>?): Boolean {
        flow ?: return false
        val o = hand ?: return false
        if (busy) return false
        val (way, times) = choose(o) ?: return false
        val play = current() ?: return false
        return step(CastOp.HandChoice(way, times, choices()), play)
    }

    /**
     * Begins what a card menu offers (§3 I4, [org.asyncttwiz.automatum.CardPlays]): [input] (a cast one way, a land play,
     * activating an ability or taking a special action) of the card [cardId], through the same panel as a cast, its
     * [text] shown for an ability. False with a message when it can't be begun.
     */
    fun begin(cardId: String, input: Input, text: String? = null): Boolean {
        val play = current() ?: return false
        if (open) { fail(BUSY); return false }
        if (revealing()) { fail(REVEAL_BUSY); return false }
        if (play.flowOpen) { fail(SharedFlowController.BUSY); return false }
        val autotapOn = autotap
        opening(cardId, text.takeIf { input is Input.Activate })
        return step(CastOp.Begin(cardId, input, autotapOn), play)
    }

    /** Answers the open question. */
    override fun answer(answer: Answer): Boolean {
        AppLog.info("decision", "Cast answer tapped: ${answer.toString().take(400)}")
        return update(CastChange.Give(answer))
    }
    override val engine: GameState? get() = flow?.state ?: null.also { Faults.report("CastController", "engine state read with no cast open") }
    override fun atwCard(id: ObjectId): String? = flow?.atwCard(id)
    override fun listedAnswers(): List<Answer> = listed
    /** I-D27: the cast runs on full information; the panel names only what the caster may see. */
    override fun shown(id: ObjectId): Boolean {
        val open = flow ?: return false
        return id is ObjectId.StackItem || id in visible || org.asyncttwiz.automatum.LastKnown.public(open.state, id)
    }

    /** Changes the answer of an earlier section (a toggle above the current one). */
    fun change(index: Int, answer: Answer): Boolean = update(CastChange.Change(index, answer))

    /** Opens the decision of an earlier section again (its answer and the later ones are dropped): "Change" on a
     * section whose answer is chosen on the table (targets). */
    fun back(index: Int): Boolean = update(CastChange.Back(index))

    /** Another way of casting the card (face, alternative cost), keeping the choices that still fit. */
    fun way(input: Input): Boolean = update(CastChange.Way(input))

    /** A tap on a table item while targets are chosen (I-D5: players by their life squares). True when it was taken;
     * false when it isn't a legal target (the table shakes it, I-D46 b) or nothing is targeted now. */
    fun tapTarget(item: TableItem): Boolean {
        val f = flow ?: return false
        val pick = pick ?: return false
        if (busy) return true
        val target = targetOf(f, pick.options, item) ?: return false
        fail(null)
        val max = pick.max
        if (max == 1 && pick.min <= 1) return answer(pick.answer(listOf(target)))
        picked = if (target in picked) picked - target else picked + target
        reselect(f)
        return true
    }

    /** Castability §3: the same for a choice of cards in the panel. */
    override fun selectionOf(chosen: List<ObjectId>, done: (arcanum.engine.Selection?) -> Unit) {
        val f = flow ?: return done(null)
        port.ask(CastRequest(session, f.version, op = CastOp.Selection(chosen.map { TargetRef.Object(it) })), null, { null }) { answer ->
            done((answer as? CastAnswer.Selected)?.selection?.selection())
        }
    }

    /** Castability §3: selecting or unselecting re-evaluates every candidate where the flow runs. With as many picked as the
     * choice takes and those payable, it is confirmed. */
    private fun reselect(f: CastShown) {
        val now = picked
        val run = ++selectionRun
        port.ask(CastRequest(session, f.version, op = CastOp.Selection(now)), null, { null }) { answer ->
            val result = (answer as? CastAnswer.Selected)?.selection?.selection()
            if (run == selectionRun && flow === f && picked == now && result != null) {
                selection = result
                selectionFor = now
                val max = pick?.max
                if (max != null && now.size >= max && result.done) confirmTargets()
            }
        }
    }

    /** Confirms the targets (or cards, D1) picked for a question that takes several. */
    fun confirmTargets(): Boolean {
        val open = pick
        if (open == null || !pickDone) return false
        return answer(open.answer(picked))
    }

    /** STK-024 (owner 2026-11-08, partial override): adds a mana source to the cast's payment or takes it out (a pool or life chip in
     * the panel, or a source used with other objects). With Autotap on, a source you pick stays and Autotap fills in the rest; one you
     * take out isn't used again in this cast. With it off, only your picks pay. The tapped sources are a set: their colours are
     * worked out together so they pay whenever they can, whatever order they were tapped in ([ManaTaps]). */
    override fun toggleMana(group: List<ManaUse>) {
        val key = ManaPicks.key(group.first())
        tapWays(group.filter { it in (payment?.options ?: group) }.ifEmpty { group }, "chip ${ManaWays.label(group.first())}", ManaBelongs.Key(key))
    }

    /** STK-024 (owner playtest 2026-11-09): a tap on a source whose uses are [ways] ([belongs]: the payment's uses of it): one paying
     * is taken out, one not paying is added with its sensible way ([ManaTaps.tap]), worked out where the flow runs. */
    private fun tapWays(ways: List<ManaUse>, what: String, belongs: ManaBelongs) {
        flow ?: return
        payment ?: return
        if (busy) return
        val current = mana
        val autotapOn = autotap
        val before = taps
        custom = true
        step(CastOp.Tap(ways, belongs, what, current, before.wire(), autotapOn), null)
    }

    /** STK-024 (owner playtest 2026-11-09): [way] chosen with the corner chip of the table card [cardId] paying the cast: it pays that
     * way from now on, kept exactly, and the rest is worked out around it. */
    fun chooseWay(cardId: String, way: ManaUse) {
        flow ?: return
        payment ?: return
        if (busy || !paying) return
        val autotapOn = autotap
        val before = taps
        val mine = waysOf(cardId)
        custom = true
        step(CastOp.ChooseWay(cardId, way, mine, before.wire(), autotapOn), null)
    }

    /** STK-024: the ways the table card [cardId] can pay the open payment (its mana abilities and colours, convoke), in the engine's
     * order; empty when it pays none. */
    fun waysOf(cardId: String): List<ManaUse> = payment?.options.orEmpty().filter { tableSource(it) == cardId }

    /** STK-024: the way the table card [cardId] pays the open payment now, null when it doesn't. */
    fun paidWith(cardId: String): ManaUse? = if (!paying) null else mana.firstOrNull { tableSource(it) == cardId }

    /** STK-024 (owner 2026-11-07): a tap on the battlefield card [cardId] while the panel is open ([paying]): when it is a
     * source the payment can use, it is added to the payment (its sensible way) or taken out; false when it isn't one. */
    fun tapSource(cardId: String): Boolean {
        if (!paying) return false
        val ways = waysOf(cardId)
        if (ways.isEmpty()) {
            // A source used with other objects (the creature an altar sacrifices): one pick per object set
            val group = groupOf(cardId) ?: return false
            AppLog.info("cast", "Payment: source $cardId tapped, paying before ${paySources()}")
            toggleMana(group)
            return true
        }
        AppLog.info("cast", "Payment: source $cardId tapped (${ways.size} ways), paying before ${paySources()}")
        tapWays(ways, "source $cardId", ManaBelongs.Uses(ways))
        return true
    }

    /** The payment's uses tapped by tapping [cardId] on the table: one of its mana abilities, or another way to pay with it
     * alone (convoke, improvise). A source used with other objects (the creature an altar sacrifices) has several: the first
     * not paying yet, else the one paying. */
    private fun groupOf(cardId: String): List<ManaUse>? {
        val decision = payment ?: return null
        val groups = ManaPicks.groups(decision).filter { group -> tableCard(group.first()) == cardId }
        return groups.firstOrNull { group -> mana.none { it in group } } ?: groups.firstOrNull()
    }

    /** The table card [use] is tapped on: a battlefield source's (by [fieldSources]), or the one card of another way to pay. */
    private fun tableCard(use: ManaUse): String? = when (use) {
        is ManaUse.Ability -> atw(use.source).takeIf { use.source in fieldSources }
        is ManaUse.Special -> use.objects.singleOrNull()?.let(::atw)
        else -> null
    }

    /** STK-024: the table card [use] is tapped on, null for what is a chip in the panel: pool mana, life, a way with several cards,
     * a source in the hand, and a source used with other objects (each of those is a pick of its own). */
    fun tableSource(use: ManaUse): String? {
        if (payment == null) return null
        if (use is ManaUse.Ability && use.answers.any { it is Answer.Objects }) return null
        return tableCard(use)
    }

    /** STK-024 (owner 2026-11-07): the sources that pay the open panel's cast now (outlined in amber on the table). */
    fun paySources(): Set<String> {
        if (!paying) return emptySet()
        return mana.mapNotNull { tableCard(it) }.toSet() + mana.filterIsInstance<ManaUse.Special>().flatMap { it.objects.mapNotNull(::atw) }
    }

    /** STK-024: the table cards a tap adds to the open panel's payment (or takes out). */
    fun payableSources(): Set<String> {
        if (!paying) return emptySet()
        return payment?.options.orEmpty().mapNotNull { tableCard(it) }.toSet()
    }

    /** "Cast": pays with the chosen mana (when the cast is at its payment) and sends the whole flow. In a hand card's
     * panel still choosing its way, it goes on to the targets first. Nothing is sent while the player can't act (the
     * timeline reviewed, offline): the cast stays open. */
    fun cast(): Boolean {
        val f = flow ?: return false
        if (busy) return false
        if (choosing) {
            choosing = false
            if (pick != null) return true
        }
        current() ?: run { fail(CANT_ACT); return false }
        if (f.pending !is Decision.ManaPayment && !f.complete) return false
        return step(CastOp.Cast(autotap, custom, mana), null)
    }

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

    /** Undo or Back (STK-025): the whole cast is dropped; nothing was sent (I-D11). A step still being worked out is
     * dropped with it. */
    fun cancel(how: String = "cancelled") {
        notice = null
        // I-D43: how each cast flow ended
        if (flow != null || starting != null) {
            (flow?.gameId ?: current()?.gameId)?.let { AutomatumLog.flow(it, "cast", how, flow?.cardId ?: starting.orEmpty()) }
            // Web client step 4e: the session ends where the flow runs
            port.ask(CastRequest(session, op = CastOp.Close(how)), null, { null }) {}
        }
        generation++
        busy = false; starting = null
        flow = null; ways = emptyList(); mana = emptyList(); paid = false; picked = emptyList(); hidden = false; abilityText = null
        payment = null; fieldSources = emptySet(); custom = false; hand = null; choosing = false
        taps = ManaTaps(); selection = null; selectionFor = emptyList(); selectionRun++
        announcedMost = emptyMap()
        listed = emptyList(); visible = emptySet()
    }

    fun toggleHidden() { hidden = !hidden }

    /** The table items that glow as legal targets of the open question: cards by id (and the Graveyard or Exile pile
     * holding one, opened to pick it), players by their life square. */
    fun glow(play: TableBoard): Set<String> {
        val f = flow ?: return emptySet()
        val pick = pick ?: return emptySet()
        return targetGlow(play, pick.options.map(f::ref))
    }

    /**
     * STK-047 (owner 2026-11-11, which pick is which): the text of what is cast or activated with the phrase picked now and
     * what was chosen for the phrases before ([org.asyncttwiz.automatum.PickText]); null without a pick on the table or a
     * text to show it in. Worked out once per pick and picks so far.
     */
    fun pickText(play: TableBoard): org.asyncttwiz.automatum.PickText? {
        val f = flow ?: return null
        val pick = pick ?: return null
        pickTextOf?.let { (of, now, text) -> if (of === f && now == picked) return text }
        val costs = f.steps.mapNotNull { (d, a) ->
            (d as? Decision.ChooseObjects)?.cost?.let { at -> at to (a as? Answer.Objects)?.chosen.orEmpty() } }
        val announcing = org.asyncttwiz.automatum.PickText.announcing(f.continuation, pick.decision)
        val text = announcing?.let {
            runCatching { org.asyncttwiz.automatum.PickText.of(f.state, it, pick.decision, costs, picked, rulesTextOf(play, f.cardId)) }
                .onFailure { problem -> AppLog.warn("cast", "No pick text for ${pick.decision.id.value}: $problem") }.getOrNull()
        }
        if (pickTextOf?.first !== f) AppLog.info("cast", "Pick text for ${pick.decision.id.value} (${pick.decision.kind}" +
            "${(pick.decision as? Decision.ChooseObjects)?.cost?.let { ", cost part $it" } ?: ""}): " +
            (text?.let { t -> "now \"${t.now}\", ${t.parts.size} parts, roles ${t.roles.map { it.second }}" } ?: "none") +
            ", earlier cost choices ${costs.size}")
        pickTextOf = Triple(f, picked, text)
        return text
    }
    private var pickTextOf: Triple<CastShown, List<TargetRef>, org.asyncttwiz.automatum.PickText?>? = null

    /** STK-047 (owner 2026-11-11): what each table card picked so far in the open cast is for ("Target", "Sacrificed",
     * "Equip"), by its card id, for its chip on the table. */
    fun pickRoles(play: TableBoard): Map<String, String> {
        val f = flow ?: return emptyMap()
        if (!targeting) return emptyMap()
        return pickText(play)?.roles.orEmpty().mapNotNull { (ref, role) -> (f.ref(ref) as? MtgRef.Card)?.cardId?.let { it to role } }.toMap()
    }

    /** STK-047: the table items picked so far for a question that takes several, drawn as picked. */
    fun pickedGlow(play: TableBoard): Set<String> {
        val f = flow ?: return emptySet()
        if (pick == null) return emptySet()
        return targetPicks(play, picked.map(f::ref))
    }

    /** The Stack entries that are legal targets of the open question, top first (picked in Manage Stack). */
    fun stackTargets(play: TableBoard): List<String> {
        val f = flow ?: return emptyList()
        val pick = pick ?: return emptyList()
        return targetEntries(play, pick.options.map(f::ref))
    }

    /**
     * The board the player acts on may have changed under the open cast (D32: the other player acted, an Undo): the cast
     * is worked out again on it, keeping the choices that still fit, the player's own mana tapping included while its
     * sources are still there; dropped with a message when it can't be cast there any more. Only the board the player acts on counts: while the timeline is reviewed or the connection
     * is down nothing changes.
     */
    fun follow() {
        val f = flow ?: return
        // STK-025, HIS-099 (owner problem report RC3J4K): an undo or rewind (the other player's accepted, a branch) took the
        // table back behind where the cast began: it is dropped at once, as Back or Undo drop it, even where the card could
        // be cast again
        if (takeBacks() != startedAt) {
            AppLog.info("undo", "Open cast of ${current()?.let { cardName(it, f.cardId) } ?: f.cardId} dropped: the table went back " +
                "behind where it began (${lastTakeBack()})")
            drop(TABLE_CHANGED)
            return
        }
        // Checked again once the step being worked out is done
        if (busy) return
        val play = current() ?: return
        if (f.gameId != play.gameId) return
        if (f.board === play.board) return
        step(CastOp.Follow(choices(), hand?.let { HandChoiceWire(it.way, it.times) }), play)
    }

    /**
     * Sends [op] for the open session ([version]: the flow shown; none when it opens the session) where the flow runs, made on
     * [play] (the board the player acts on, null when the step doesn't read it), and applies its answer unless something
     * replaced it meanwhile. True when it was taken (in-process at once: what the answer says).
     */
    private fun step(op: CastOp, play: TableBoard?, version: Long? = flow?.version): Boolean {
        val run = ++generation
        busy = true
        var result = true
        val base = flow
        val opening = op is CastOp.Start || op is CastOp.FromHand || op is CastOp.Begin
        val request = CastRequest(session, if (opening) null else version, play?.values?.number, op)
        port.ask(request, play, { current()?.let(stacks) }) { answer ->
            if (run != generation) return@ask
            busy = false
            result = apply(answer, play, base)
        }
        return result
    }

    /** [value] on the board it runs on: [play] (the step's) when [CastFlowValue.fresh], else [base]'s. */
    private fun shownOf(value: CastFlowValue, play: TableBoard?, base: CastShown?): CastShown {
        val on = if (value.fresh) requireNotNull(play) { "A fresh flow without the step's board" }
            else requireNotNull(base) { "A flow step without the flow shown" }.play
        return CastShown(value, on) { port.state(value, on) }
    }

    private fun apply(answer: CastAnswer, play: TableBoard?, base: CastShown?): Boolean = when (answer) {
        is CastAnswer.Show -> {
            val p = answer.panel
            val shown = shownOf(p.flow, play, base)
            starting = null
            notice = null
            p.ways?.let { ways = it }
            if (p.ways != null) AutomatumLog.flow(shown.gameId, "cast", "started", shown.cardId)
            listed = p.listed; visible = p.visible
            flow = shown; mana = p.mana; paid = p.paid; picked = emptyList()
            announcedMost = p.most.associate { it.decision to HandCasts.Announceable(it.most, it.unpayable) }
            selection = p.selection?.selection(); selectionFor = emptyList(); selectionRun++
            payment = p.payment; fieldSources = p.fieldSources
            // With Autotap off the picks are what pays, carried to a payment whose cost changed (STK-024)
            if (!autotap && custom) taps.chosen.filter { it in p.mana }.let { kept ->
                taps = ManaTaps(tapped = p.mana.filterNot { it in kept }, chosen = kept)
            }
            p.hand?.let { hand = it }
            p.choosing?.let { choosing = it }
            if (p.clear) fail(null)
            follow()
            true
        }
        is CastAnswer.Mana -> {
            if (base != null && flow === base) { mana = answer.mana; paid = answer.paid; taps = answer.taps.taps() }
            follow()
            true
        }
        is CastAnswer.Engine -> sendEngine(answer, shownOf(answer.ready, play, base))
        is CastAnswer.Drop -> { drop(answer.message); false }
        is CastAnswer.Refuse -> { notice = answer.message; follow(); false }
        is CastAnswer.Selected, CastAnswer.None -> true
    }

    /** I-D56 step C: sends a complete cast's own inputs on the board the player acts on now (worked out again when it
     * moved on meanwhile); the device shows it at once (its own pending event). */
    private fun sendEngine(planned: CastAnswer.Engine, ready: CastShown): Boolean {
        val play = current() ?: run { showReady(ready); fail(CANT_ACT); return false }
        if (play.board !== ready.board) {
            starting = null
            return step(CastOp.Replan(autotap), play, ready.version)
        }
        val card = ready.cardId
        cancel("sent")
        val route = engineSend ?: run { fail("Casting isn't available here."); return false }
        val refusal = route(planned.inputs) ?: return true
        AutomatumLog.refusal(play.gameId, "cast", "$refusal: ${planned.inputs}")
        // I-D56 §5: a cast that would show hidden cards while the line is tentative waits; its card shakes
        shake(card)
        fail(if (refusal == org.asyncttwiz.protocol.game.GameDevice.REVEALS_HIDDEN) WAIT_TO_CATCH_UP else null)
        return false
    }

    /** [done] (complete) shown ready to cast, after a send that couldn't go out yet. */
    private fun showReady(done: CastShown) {
        starting = null
        if (flow == null || flow?.cardId == done.cardId) { flow = done; mana = emptyList(); paid = false; picked = emptyList() }
    }

    /** Applies [change] to the open cast: shown with its open question, or sent when nothing is left to decide. */
    private fun update(change: CastChange): Boolean {
        flow ?: return false
        if (busy) return false
        val c = choices()
        // An answer goes on past a hand card's way: its targets are picked on the table from here
        choosing = false
        return step(CastOp.Update(change, c), null)
    }

    private fun targetOf(f: CastShown, options: List<TargetRef>, item: TableItem): TargetRef? {
        if (item.kind == TableItemKind.LIFE) return options.firstOrNull { it == TargetRef.Player(PlayerId(item.owner ?: return null)) }
        val ids = listOfNotNull(item.sourceId, item.cardId)
        return options.firstOrNull { target ->
            when (val ref = f.ref(target)) {
                is MtgRef.Card -> ref.cardId in ids
                is MtgRef.StackItem -> f.board.stackEffects.any { it.id in ids && it.item?.id == ref }
                is MtgRef.Player -> false
            }
        }
    }

    companion object {
        const val BUSY = "Finish the cast first, or drop it with Undo."
        const val TABLE_CHANGED = CastSessions.TABLE_CHANGED
        /** I-D56 §5: the hold text of an action refused because it would show hidden cards while the line is tentative. */
        const val WAIT_TO_CATCH_UP = "Wait for the other player to catch up: this would show you hidden cards or a random result."

        /** Whether a dropped cast's [message] is shown by shaking its card (a refusal) rather than said (a failure). */
        fun visual(message: String) = message.endsWith("can't be cast now.") || message.endsWith("can't be played now.") ||
            message.endsWith("that can't be done now.") || message.startsWith("Not enough mana") || message.startsWith("The table changed")
        const val CANT_ACT = "You can't cast right now (reviewing the timeline, or offline). The cast stays open."
        /** I-D27: the same for every card in the other player's hand, whatever it is. */
        const val NOT_YOURS = "Only its owner can cast that card."
        const val NOT_ALLOWED = CastSessions.NOT_ALLOWED
        /** STK-024: a payment that can't be made with the mana chosen. */
        fun notPaid(payment: Decision.ManaPayment) = CastSessions.notPaid(payment)
        const val FAILED = CastSessions.FAILED
        const val TAKE_OVER = "Automatum can't take over this board:"

        /** The table's cast sessions, numbered across the page (web client step 4e: the engine side keys them by game). */
        private val sessions = org.asyncttwiz.appui.AtomicLong()
        private fun nextSession(): Long = sessions.incrementAndGet()

        /** The ATW card of an engine object, when it is a card. */
        private fun atw(id: ObjectId): String? = (id as? ObjectId.Card)?.card?.value

        fun cardName(play: TableBoard, cardId: String): String = CastSessions.cardName(play, cardId)

        fun land(play: TableBoard, cardId: String): Boolean = CastSessions.land(play, cardId)

        /** Whether [cardId] is in [viewer]'s own hand. */
        fun ownHand(play: TableBoard, cardId: String, viewer: String): Boolean {
            val card = play.board.cards.singleOrNull { it.id == cardId } ?: return false
            return play.board.containers.any { it.id == card.containerId && it.zone == PlayZone.HAND && it.accountId == viewer }
        }

        /** Whether [cardId] is in [viewer]'s hand row (ZON-079): their own hand, or a card from another zone the row shows
         * ([HandRow.cards]); null when Automatum hasn't worked [play] out for [viewer] (not known yet). */
        fun inHandRow(play: TableBoard, cardId: String, viewer: String): Boolean? {
            if (ownHand(play, cardId, viewer)) return true
            if (AutomatumBoards.cached(play, viewer) == null) return null
            return HandRow.cards(play, viewer).any { it.cardId == cardId }
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

/**
 * Web client step 4e: an open cast as the cast panel and its target bar draw it: the plain data its session's last step
 * answered ([value]) on the board it runs on ([play]), never the flow itself. Its engine state ([state]) is the flow's own
 * in-process, else the page's copy of [play]'s with the flow's changes, worked out once when first read.
 */
class CastShown(val value: CastFlowValue, val play: TableBoard, state: () -> GameState) {
    /** The engine state the cast is at now (full information; the panel names only what the caster may see, I-D27). */
    val state: GameState by lazy(state)
    /** The session's version of this flow. */
    val version: Long get() = value.version
    /** The ATW card being cast or played, or whose ability is activated. */
    val cardId: String get() = value.cardId
    /** [Input.Cast], [Input.PlayLand] or [Input.Activate]. */
    val begin: Input get() = value.begin
    /** Whether this flow activates an ability or takes a special action (not a cast or land play). */
    val activation: Boolean get() = begin is Input.Activate
    /** The open question of the cast itself, null when it is complete. */
    val pending: Decision? get() = value.pending
    val complete: Boolean get() = pending == null
    /** Each answered decision with its answer, in order. */
    val steps: List<Pair<Decision, Answer>> = value.steps.map { it.decision to it.answer }
    /** The engine's open continuation (the announcement the open question is part of). */
    val continuation: arcanum.engine.decision.Continuation? get() = value.continuation
    /** The board the flow runs on. */
    val board: BoardState get() = play.board
    val gameId: String get() = play.gameId

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

/** The table items that glow for the legal targets [refs] (I-D6): cards by id, a Graveyard or Exile pile holding one
 * (tapped, it opens to pick from, i12review #14), Stack entries by id, and the Stack itself when an entry below the top
 * is one (tapped, Manage Stack opens to pick from), players by their life square. */
fun targetGlow(play: TableBoard, refs: List<MtgRef>): Set<String> = refs.flatMap { ref ->
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

/** STK-047: the table items drawn as picked for the picked targets [refs]: cards by id, Stack entries by id, players by
 * their life square (never the pile or the Stack holding one: it may hold other targets still to pick). */
fun targetPicks(play: TableBoard, refs: List<MtgRef>): Set<String> = refs.flatMapTo(HashSet()) { ref ->
    val entries = play.board.stackEffects.filter { it.item?.id == ref }.map { it.id }
    if (entries.isNotEmpty()) return@flatMapTo entries
    when (ref) {
        is MtgRef.Player -> listOf("life/${ref.accountId}")
        is MtgRef.Card -> listOf(ref.cardId)
        is MtgRef.StackItem -> emptyList()
    }
}

/** The Stack entries that are legal targets among [refs]: a spell by its card (its item's object), an ability by its
 * item (I-D6), top first. */
fun targetEntries(play: TableBoard, refs: List<MtgRef>): List<String> {
    val wanted = refs.filter { it !is MtgRef.Player }.toSet()
    val entries = play.board.stackEffects.associateBy { it.id }
    return CombatStackRules.stack(play.board).cards.filter { id -> entries[id]?.item?.id in wanted }
}
