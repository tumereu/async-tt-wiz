package org.asyncttwiz.protocol.game

import arcanum.api.TurnStep
import arcanum.engine.Engine
import arcanum.engine.decision.Decision
import arcanum.engine.input.Block
import arcanum.engine.input.Input
import arcanum.engine.setup.GameResult
import arcanum.engine.state.GameState
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.turn.LoopKey
import org.asyncttwiz.protocol.EngineRewindRequest
import org.asyncttwiz.protocol.EngineSnapshot
import org.asyncttwiz.protocol.EngineUpdate
import org.asyncttwiz.protocol.GameAction

/**
 * The device core of a game played on engine state (I-D56 §4–§11, step B): what one player's device does with its
 * [GameSession], without the visual UI. Everything it sends is a [GameAction] in [take]'s outbox; everything the server
 * says comes in through [revision] (accepted events and what else changed) and [refused].
 *
 * - **Playback** (§7): events past the local head are buffered and shown one at a time ([step], paced by the UI);
 *   playback stops before an assumed input about the player where their current response mode stops (HIS-095, owner
 *   2026-11-06: the same value as live play's, [PlaySettings.mode]) ([Status.Stopped], [NextStop.stopsAt]), and at the
 *   assumption that took the place of a move of theirs lost in a race ([lateAnswerAt]): the table is live there, and
 *   their answer ([act]) confirms the assumption (its first input the
 *   same, [Acceptance.confirms]: the rest is their next action after it) or branches the game (anything else, a new
 *   line). Elsewhere showing the assumed pass confirms it. Coming back with the other player's events buffered starts
 *   with playback paused ([Status.CatchUp], [play]); the opening (mulligans) is never played back: its events show at
 *   once, up to a stop (§29.1).
 * - **Local-head reports** ([GameAction.EngineSeen]) go out throttled, at once where playback stops or reaches the head.
 * - **The device's own passes** (§6, §7): at the head, where the player gets priority and their mode doesn't stop
 *   there (or an "End turn" goes on), the device passes for them; such passes after the player's own input fold into its
 *   event, as do engine-automatic answers ([WhoActs.automaticAnswer], public forced passes). Under Auto-skip it also
 *   answers questions with only one answer. A new response mode ([settings]) applies from the next point on: the stop
 *   the player stands at, live or in playback, stays until they answer it ([keptAt], owner 2026-11-07).
 * - **Assume or wait** (§3, §4) at the other player's points: engine-automatic answers at once; their pass assumed at
 *   once where the other player's own response mode would pass it ([opponentMode], their Quick judged on their real
 *   hand; owner 2026-11-06) or, at a point the player began ([pointOwner]: the last real input since the step began was
 *   theirs; owner 2026-11-07), where their Assume setting assumes it, else a wait with Assume, whether they
 *   are online or away (HIS-096: presence never decides it); never at their
 *   own main phase; blockers a wait with Assume blockers ([assume]); anything else a real wait, the opening keep
 *   included (TRN-027: never assumed). An assumption whose outcome would show the player a hidden card, or a random
 *   result (whether it would show one can't be told before the result), is not sent (the information rule, §5): a
 *   wait with no button.
 * - **Branches and rewinds** (§8, §10): when the server's main line changes behind the shown state, the device goes
 *   back to the point and says so ([Note.Branched], [Note.TakenBack]); events on the discarded line are gone.
 * - **Undo** ([undo]): free undo is sent at once, else the caller opens the request dialog ([requestUndo]); the other
 *   player answers ([answerRequest], [requestPreview]); while a request is open the game is frozen for both (no inputs
 *   but a concession; playback goes on). An Undo tapped while the player's own action is still on its way waits for it,
 *   then takes it back ([undoTap]).
 * - **Table-only events** ([table]) are made at the head while they apply there, re-made after a stale refusal, else
 *   dropped; staging behind the head stays on the device. Early answers only at the head. A waiting suggestion locks
 *   every other game action once the shown state includes it (TRN-049). The other player's table-only events never
 *   make the player's own events or open draft stale ([GameSession.received]).
 * - **Refusals and drafts:** a refused action of the player's own is told ([Note.Refused]; the device's own passes and
 *   assumptions refused in the expected races are not); a draft dropped because the main line changed under it is told
 *   once ([Note.Refused] with [DRAFT_DROPPED]) and [send] then answers "noDraft".
 *
 * Times are the caller's ([advance], [take] take `now` in milliseconds), so simulations are deterministic.
 */
class GameDevice(
    val session: GameSession,
    settings: PlaySettings,
    localHeads: Map<PlayerId, String>,
    lines: Int,
    request: EngineRewindRequest?,
    private val ids: () -> String,
) {
    val me: PlayerId get() = session.me
    /** What the player sees ([GameSession.model]). */
    val model: GameModel get() = session.model
    /** The other player (two players, I-D56). */
    val opponent: PlayerId = session.model.engine.players.first { it.id != session.me }.id

    /** The player's own settings for this game (I-D56 §13). */
    var settings: PlaySettings = settings
        private set

    /** The other player's current response mode (owner 2026-11-06), as the server last said ([modes]) or their latest
     * event recorded it: the device assumes their pass wherever it would pass. Until known (the server always says it),
     * Normal: nothing assumed beyond what Normal mode passes, and nothing judged on their hand. */
    var opponentMode: ResponseMode = ResponseMode.Normal
        private set

    /** Each player's current response mode by account, as the server says it ([EngineSnapshot.modes], [EngineUpdate.modes]):
     * the other player's is [opponentMode]. */
    fun modes(modes: Map<String, ResponseMode>) { modes[opponent.value]?.let { opponentMode = it } }

    /** The game's assume reveal settings (LOB-135), as the caller last said ([assumeReveals]): the cards the information
     * rule lets through, in this player's assumptions and own actions alike. Until told, nothing ([AssumeReveals.NONE]). */
    var assumeReveals: org.asyncttwiz.protocol.AssumeReveals = org.asyncttwiz.protocol.AssumeReveals.NONE
        private set

    /** The game's assume reveal settings changed (its summary, set by the host): they apply to the points not yet made. */
    fun assumeReveals(value: org.asyncttwiz.protocol.AssumeReveals) {
        if (value == assumeReveals) return
        assumeReveals = value
        reveals.clear()
    }

    /** Each player's local head as the server last said. */
    val localHeads: Map<PlayerId, String> get() = heads.toMap()
    private val heads = localHeads.toMutableMap()

    /** The play's line counter: a branch starts line [lines] + 1. */
    var lines: Int = lines
        private set

    /** The open undo request, if any: the game is frozen while it is (§10). */
    var request: EngineRewindRequest? = request
        private set

    /** Set when the device can't follow the server exactly: the caller loads the game again ([load]). */
    var needsReload: Boolean = false
        private set

    /** HIS-106: the main line's events after the branch point when [load] opened the game where the player left it on a
     * line another player's branch discarded; the caller receives them ([revision]) once the player tapped the centre text there. */
    var branchOnLoad: List<GameEvent> = emptyList()

    /** HIS-108: when [load] opened the game where the player left it before the other player's undo took it back, the
     * undo as its revision brings it (with [branchOnLoad] as the events re-added after its point): the caller receives
     * it ([revision]) once the player tapped the centre text there. */
    var undoOnLoad: EngineUpdate? = null

    /** HIS-111, HIS-099 (owner 2026-11-07): the main line before the session's, loaded from the server on demand
     * ([addPage]), and the states beyond what going back from the shown state reaches; null on a device made without a
     * codec (tests), which then reaches only its session's line. */
    var older: OlderHistory? = null
    /** The game's root (before its first event): nothing is older ([EngineSnapshot.rootId]). */
    var rootId: String? = null

    /** HIS-108: the other player's undo of events the player had been shown went back to their local head: the device
     * reports that head once even though the server has it, which tells the server the undo was shown. */
    private var undoShown = false

    /** Whether playback waits, paused, for the player's Play before it starts ([play] ends it; §29.1). Past the opening
     * only: the opening shows at once ([opening]); any submission of the player's ends it. */
    var catchUp: Boolean = session.buffered.any { it.author != session.me.value }
        private set

    /**
     * HIS-106, HIS-108 (owner 2026-11-07): the caller holds back what the server sent (a branch or the other player's
     * undo that takes back what the player had seen, found on their return or on opening the game) until the player
     * taps the centre text: meanwhile the table stays exactly as they left it, so the device stays where it was too: it
     * stands at the catch-up's pause ([Status.CatchUp]), makes nothing of its own, takes no action of the player's but a
     * concession and reports no local head (the line it holds is no longer the server's).
     */
    var holding: Boolean = false

    /**
     * The measuring clock (nanoseconds; engine-responsiveness R1, SYS-030): when set, the time the information rule's
     * work takes (§5: the state kept from before an event and the checks of what it would show this player) adds up in
     * [informationNanos]. Null (the default, and on the server): nothing is measured.
     */
    var clock: (() -> Long)? = null

    /** TRN-057, STK-053 (reports P2XUER, 3WWPA8): told when the device itself makes a pass of its player's at their own main
     * phase, which only a press of theirs may do: a press lost in a race being made again (SYS-011), or anything else (a
     * fault). An End turn going on isn't told: its press is logged, and passing there is what it does. The host logs it. */
    var ownMainPass: ((why: String) -> Unit)? = null

    /** The information rule's time so far, in nanoseconds, while [clock] is set (the caller takes differences). */
    var informationNanos: Long = 0L
        private set

    /** [work], the information rule's, timed into [informationNanos] while [clock] is set (once when nested). */
    private inline fun <T> informationRule(work: () -> T): T {
        val clock = clock?.takeIf { !informing } ?: return work()
        val start = clock()
        informing = true
        try { return work() } finally { informing = false; informationNanos += clock() - start }
    }
    private var informing = false

    private var now = 0L
    private var endTurn: Int? = null
    /**
     * TRN-017, STK-029, TRN-070 (owner 2026-11-07): the Resolve chain a hold of Resolve or of a Stack wait's Assume began
     * ([startChain]): at the head the device presses Resolve at each of the player's priority points and Assume at each
     * of the other player's (where the information rule allows it: a greyed Assume ends it), whatever the player's
     * response mode, until the Stack is empty, a decision of the player's, a reveal stop (HIS-110), the player's own
     * action, or the other player's real input arriving ([opponentInputs]): that input is never passed over.
     */
    private var chain = false

    /** Counts the other player's real inputs this device received (their own events with an engine input, never an
     * assumption, nor their same event that took the place of this device's own, SYS-077): a Resolve chain begins only
     * when none arrived since its hold was pressed ([startChain]). */
    var opponentInputs: Int = 0
        private set

    /** Whether a Resolve chain goes on ([chain]). */
    val chaining: Boolean get() = chain
    /** Where the End turn going on ([endTurn]) was tapped, and the first event it made (null until it makes one): a
     * branch or a rewind that takes it back ends it ([endTurnTakenBack]). */
    private var endTurnAt: String? = null
    private var endTurnFirst: String? = null
    private val reveals = mutableMapOf<String, Boolean>()
    private val retry = mutableListOf<Retry>()
    /** The player's own opening declarations (Keep, Mulligan, a bottom choice) the server took the other player's
     * declaration before (both declare at the same time, TRN-027): made again on it ([redeclare]). */
    private val redeclaring = mutableListOf<arcanum.engine.decision.Answer>()
    /** The event those declarations were made on: they wait for the other player's event after it (a refusal can come
     * before it). */
    private var redeclareAfter: String? = null
    /** The events of those declarations: their "stale" refusal, still to come, isn't told. */
    private val redeclared = mutableSetOf<String>()
    /** The player's own inputs (and table changes) of each of their recent events, as their action made them, before
     * what followed by itself: made again when the server took the other player's event first ([lostMoves]). */
    private val made = LinkedHashMap<String, Pair<List<Input>, List<TableChange>>>()
    /** The first input the device folded by itself into each of the player's recent actions, right after what they made
     * ([finish], [pressedInAction]). */
    private val foldedPress = LinkedHashMap<String, Input>()
    /** The player's own moves the server took the other player's event before, in order, to be made again on it when
     * the same decision still waits for them there ([remake]). */
    private val remaking = mutableListOf<Lost>()
    /** The events of those moves: their "stale" refusal isn't told ([remake] tells it when it can't make one again). */
    private val lost = mutableSetOf<String>()
    private val confirming = mutableSetOf<String>()
    /** Owner 2026-11-07 (SYS-077): the device's own recent events whose place the other player's same event took
     * ([GameSession.Received.Adopted]), to that event's id. */
    private val adopted = LinkedHashMap<String, String>()

    /** The event that took the place of the device's own event [id] (the same event, made by the other player's device:
     * the board on it is the board on [id], SYS-009), or null. */
    fun adoptedAs(id: String): String? = adopted[id]
    /** The submissions made by the player's own actions (a refusal of one is told, even "stale"). */
    private val byPlayer = mutableSetOf<String>()
    /** Who made this device's own recent events (I-D56 §10, report C9PVLM): true for the player's own action (an End
     * turn's first pass included), false for the device's automatic steps; Undo goes back past the automatic ones. */
    private val origins = LinkedHashMap<String, Boolean>()
    /** This device's recent assumptions made by a press of Assume ([assume]), not by itself ([pressedByItself]). */
    private val assumeTaps = LinkedHashSet<String>()
    /** An End turn waits for its first event: the player's action. */
    private var endTurnMade = false
    /** The head a free undo of this player's landed on: the device makes none of their own inputs there by itself
     * (their mode's passes would carry the game straight past the point again); they act there themselves ([held]). */
    private var holdAt: String? = null
    /** STK-036, HIS-095 (owner 2026-11-07, report WDKHSB): the point (the shown tip) where the player stood at a stop of
     * theirs when they changed their response mode ([settings]): the new mode applies only from the next point on, so
     * there the device makes none of their passes or one-answer answers by itself, and playback stays stopped before the
     * other player's assumption about them made there, whatever the new mode says. Their answer moves the tip on. */
    private var keptAt: String? = null
    /** HIS-107 (owner problem report 2026-11-05): an Undo tapped while something of the player's was still on its way
     * ([undoTap]): the event of the action it takes back. Meanwhile the device makes nothing of its own. */
    private var queuedUndo: String? = null
    /** The targets of the player's own undos shown on this device before the server's rewind came ([undoLocally]), in
     * the order they were sent. */
    private val localUndos = mutableListOf<String>()
    private val outbox = mutableListOf<GameAction>()
    private val notes = mutableListOf<Note>()
    private var reported: String? = null
    private var reportedAt = Long.MIN_VALUE / 2
    /** The report the server refused last ([refused]): [reported] stays it until a revision says it must go again ([reportAgain]). */
    private var reportRefused: String? = null
    private val record = ArrayDeque<String>()

    private class Retry(val table: List<TableChange>, val parent: String)

    // ---- What the player sees

    /** Where the game is for this player. */
    sealed interface Status {
        /** The device can't follow the server: load the game again. */
        data object Reload : Status
        /** Coming back with the other player's events unseen: the catch-up screen, then [play]. */
        data object CatchUp : Status
        /** Playback: [next] is shown next ([step]). */
        data class Playing(val next: GameEvent) : Status
        /** Playback stopped at [event], the other player's assumption of [assumed] for this player: the table is live
         * before it, and the player's answer confirms or branches. [paused]: it stops there only because the player paused
         * playback ([pausing], §27), not by their response mode. */
        data class Stopped(val event: GameEvent, val assumed: Input, val paused: Boolean = false) : Status
        /** A draft is open (a flow of the player's own). */
        data object Drafting : Status
        /** The game waits for this player at the head. */
        data object YourMove : Status
        /** Waiting for the other player: [reason] for the waiting bar, [offer] its Assume button (null: none). */
        data class Waiting(val reason: Wait, val offer: Offer? = null) : Status
        /** The game's [result] (from the shown state): [confirmable] once both local heads reached it (OK, TRN-065);
         * [final] once confirmed, or after a concession or an accepted suggestion. [ending]: how it ended; [endedBy]:
         * the player whose own action made it final (the conceder, the player who accepted, the player who confirmed),
         * who has seen it already (TRN-065: they go on without a second OK). */
        data class Over(val result: GameResult, val confirmable: Boolean, val final: Boolean,
            val ending: org.asyncttwiz.protocol.MatchEnding = org.asyncttwiz.protocol.MatchEnding.ByRules,
            val endedBy: PlayerId? = null) : Status
        /** Nothing to do (before the game starts). */
        data object Idle : Status
    }

    /** Why the device waits for the other player (the waiting bar, §14). */
    sealed interface Wait {
        /** They may respond to [to], on top of the Stack (yours, or their own while they hold priority over it). */
        data class Respond(val to: ObjectId) : Wait
        /** They may act at their priority in [step] with nothing of yours on top of the Stack (a pause, such as after their
         * blocks, or any point where your Assume setting and their mode don't assume their pass, HIS-096). */
        data class MayAct(val step: TurnStep) : Wait
        /** They are declaring blockers. */
        data object Blockers : Wait
        /** They are deciding on a mulligan. */
        data object Keep : Wait
        /** They are choosing ([source]: the card it comes from, null when this player can't see it). */
        data class Choosing(val source: ObjectId?) : Wait
        /** Their own main phase, or their declaration of attackers: their turn to act. */
        data object TheirTurn : Wait
        /** Going on would show you hidden cards, or a random result (the information rule, §5). */
        data object HiddenCards : Wait
        /** Your own events wait for the server. */
        data object Sending : Wait
        /** An undo request is open. */
        data object Request : Wait
        /** A result suggestion waits for an answer (TRN-049). */
        data object Suggestion : Wait
    }

    /** The waiting bar's Assume buttons (§4). */
    sealed interface Offer {
        data object AssumeResolves : Offer
        data object AssumeBlockers : Offer
    }

    /** Something to tell the player once. */
    sealed interface Note {
        /** [by] answered differently than assumed ([discarded]'s first event): what you had been shown after it was
         * taken back ([takenBack]), and of it your own actions ([actions], HIS-097: as Undo counts them, an End turn's
         * first pass included; never your device's own passes and assumptions). */
        data class Branched(val by: PlayerId, val discarded: GameEvent, val takenBack: List<GameEvent>,
            val actions: List<GameEvent> = emptyList()) : Note
        /** [by] took the main line back past what you were shown ([takenBack]). */
        data class TakenBack(val by: PlayerId, val takenBack: List<GameEvent>) : Note
        /** Taking back to [target] needs the other player's approval (an undo request). */
        data class RequestNeeded(val target: String) : Note
        /** The server refused something for [reason]; [on] is the object the refused action was made on (the card cast or
         * played, the source activated, [madeOn]), null for one made on the main button (a pass, a declaration, an
         * answer). */
        data class Refused(val reason: String, val on: ObjectId? = null) : Note
    }

    /** What an action of the player's did. */
    sealed interface Acted {
        /** Sent: [event] is the device's own (pending, shown at once unless made at the head while behind). */
        data class Sent(val event: GameEvent) : Acted
        /** The answer confirmed the other player's assumption: playback goes on past it. */
        data object Confirmed : Acted
        /** Staging behind the head: kept on the device, sent only as the declaration itself (§6). */
        data object Kept : Acted
        /** Made and shown on the host's device while its event is still being finished there (SYS-025: a host that
         * shows the action at [act]'s performed point answers this; a later refusal is told as a note). */
        data object Made : Acted
        /** Not done, for [reason] ([REVEALS_HIDDEN]: wait for the other player to catch up). */
        data class Refused(val reason: String) : Acted
    }

    /** Where the game is for this player now. */
    val status: Status get() {
        if (needsReload) return Status.Reload
        if (holding) return Status.CatchUp
        if (session.drafting) return Status.Drafting
        if (session.behind) {
            val next = session.buffered.first()
            val decision = stopAt(next, settings.mode)
            // HIS-094, HIS-095 (owner playtest 2026-11-06): playback standing at a decision of the player's own has nothing
            // to watch before it: leaving the app, coming back or loading the game again never pauses it for the centre
            // text there ("has made moves"), whatever of the other player's follows; the stop shows, live
            // HIS-110, HIS-112: an open reveal panel or a play shown holds playback where it is (the centre text waits
            // until it is dismissed or done)
            if (catchUp && !opening && decision == null && !viewHolds()) return Status.CatchUp
            val stop = if (pausing) stopAt(next) else decision
            return stop?.let { Status.Stopped(next, it, paused = decision == null) } ?: Status.Playing(next)
        }
        val s = model.engine
        Engine.result(s)?.let { return Status.Over(it, confirmable(), final(), ending(s, it), endedBy()) }
        if (request != null) return Status.Waiting(Wait.Request)
        if (locked) return Status.Waiting(Wait.Suggestion)
        // R3: worked out once per point (the searches below don't run again while nothing they read changed)
        val key = pointKey()
        headStatus?.let { (at, status) -> if (key.same(at)) return status }
        pointsWorkedOut++
        val who = Attention.of(s)
        val status = when {
            who == null -> Status.Idle
            who == me || declares(s) -> Status.YourMove
            else -> when (val point = point(s)) {
                is Point.Wait -> Status.Waiting(point.reason, point.offer)
                else -> Status.Waiting(Wait.Sending)
            }
        }
        headStatus = key to status
        return status
    }

    /**
     * Engine-responsiveness R3: what the device's decisions at the head read besides the engine state at the tip (the
     * tip names it: the state at an event is the same however it was reached, and nothing is decided while a draft is
     * open; its open decision is compared too, by identity, as a cheap guard against a state changed in place): the table
     * state (the other player's table-only events slip in under the device's own pending events without moving the tip),
     * the player's settings, the other player's response mode, the game's assume reveal settings, an End turn going on and
     * the head a free undo held at. While none of it changes, the status at the head ([status]) and the device's own step
     * there ([automaticStep]) stay the same, so each is worked out once per point: a tick at an unchanged point does no
     * engine search.
     */
    private class PointKey(val tip: String, val pending: Any?, val table: TableState, val settings: PlaySettings,
        val endTurn: Int?, val holdAt: String?, val opponentMode: ResponseMode, val reveals: org.asyncttwiz.protocol.AssumeReveals,
        val chain: Boolean) {
        fun same(other: PointKey?): Boolean = other != null && tip == other.tip && pending === other.pending && table === other.table &&
            settings == other.settings && endTurn == other.endTurn && holdAt == other.holdAt && opponentMode == other.opponentMode &&
            reveals == other.reveals && chain == other.chain
    }

    private fun pointKey() = PointKey(session.tip, model.engine.pending, model.table, settings, endTurn, holdAt, opponentMode, assumeReveals, chain)

    /** The status at the head worked out last, with its point ([PointKey]). */
    private var headStatus: Pair<PointKey, Status>? = null
    /** The point where the device's own step was last found to make nothing ([automaticStep]). */
    private var idleAt: PointKey? = null

    /** Tests and measurements (engine-responsiveness R3): how many times the status at the head or the device's own step
     * there was worked out anew (each may search the engine's options); a tick at an unchanged point adds none. */
    var pointsWorkedOut: Int = 0
        private set

    /** Whether this player may declare their opening mulligan or bottom choice now, while the other player's is the
     * open one (simultaneous mulligans in any order, I-D54 §8). */
    private fun declares(s: GameState): Boolean = NextStop.declares(s, me)

    /**
     * Whose move the game is for this player (TRN-058, I-D57): the player the game really waits for, as the server keeps
     * it for the game list and notifications ([NextStop.waitsFor], `engine_seat.waited`), worked out on the main line's
     * head as this device knows it (its own pending events included): the other player than the asker of an open undo
     * request or result suggestion; else, at a result, this player when they haven't been shown it, the other player
     * while they haven't reached it; else this player when the head is their decision or opening declaration, the other
     * player when it is theirs. So a decision stop of this player's behind the head, while the other player plays on
     * assumptions about them, is the other player's move. Null when the game waits for nobody (both have seen a result,
     * or before the start). A draft of the player's own at the head is their move.
     */
    val move: PlayerId? get() {
        request?.let { return if (it.by == me.value) opponent else me }
        // The player's own flow at the head (a cast, a declaration being made)
        if (session.drafting && !session.behind) return me
        val facts = headFacts() ?: return me
        facts.suggestedBy?.let { return if (it == me) opponent else me }
        if (facts.result) return if (session.behind) me else opponent.takeIf { heads[opponent] != session.tip }
        return if (me in facts.waiting) me else opponent.takeIf { it in facts.waiting }
    }

    /** What the main line's head says about whose move it is ([move]): its open result suggestion's author, whether it
     * holds the game's result, the players whose decision or opening declaration it is. */
    private class HeadFacts(val head: String, val suggestedBy: PlayerId?, val result: Boolean, val waiting: Set<PlayerId>)
    private var behindFacts: HeadFacts? = null

    /** [HeadFacts] of the head: of the shown state at the head, else of the head's state worked out once per head (the
     * last worked out while a draft behind the head is open). */
    private fun headFacts(): HeadFacts? {
        fun of(head: String, m: GameModel) = HeadFacts(head, m.table.suggestion?.by, Engine.result(m.engine) != null,
            Attention.of(m.engine).let { who -> players.filter { it == who || NextStop.declares(m.engine, it) }.toSet() })
        if (!session.behind) return of(session.tip, model)
        behindFacts?.takeIf { it.head == session.head }?.let { return it }
        if (session.drafting) return behindFacts
        return of(session.head, session.headModel()).also { behindFacts = it }
    }

    /** A waiting suggestion in the shown state locks every other game action (TRN-049, I-D56 §11). */
    val locked: Boolean get() = model.table.suggestion != null

    /** Whether the line is tentative: an assumption of this player's isn't confirmed yet (§2). */
    fun tentative(): Boolean {
        val after = heads[opponent]?.let(session::after) ?: session.events
        return (after + session.pending).any { it.assumed?.assumedBy == me }
    }

    /** The assumer's indicator (§14): [assumed] unconfirmed assumptions, [since] events since the first; null when
     * there are none. */
    data class Indicator(val assumed: Int, val since: Int)

    fun indicator(): Indicator? {
        val after = (heads[opponent]?.let(session::after) ?: session.events) + session.pending
        val first = after.indexOfFirst { it.assumed?.assumedBy == me }
        if (first < 0) return null
        return Indicator(after.count { it.assumed?.assumedBy == me }, after.size - first - 1)
    }

    /** Whether [event], an assumed input, is confirmed: the other player's local head is past it (§2). */
    fun confirmed(event: GameEvent): Boolean {
        val about = event.assumed?.let { Acceptance.assumedAbout(it, event, players) } ?: return true
        val head = if (about == me) session.localHead else heads[about] ?: return false
        val at = session.indexOf(event.id) ?: return true
        val seen = session.indexOf(head) ?: return true
        return seen >= at
    }

    private val players: List<PlayerId> get() = model.engine.players.map { it.id }

    // ---- Playback

    /** Ends the catch-up pause: playback starts. */
    fun play() { catchUp = false }

    /** HIS-006: the player entered timeline mode while playback ran: it waits paused ([Status.CatchUp]) until [play]. */
    fun holdPlayback() { if (session.behind && !opening && status is Status.Playing) catchUp = true }

    /**
     * HIS-035 (owner playtest 2026-11-05): the player left the app. Playback pauses at the shown point at once, and what
     * arrives meanwhile waits: coming back with the other player's events past the local head starts paused
     * ([Status.CatchUp]) until [play], as a fresh load does; [returned] ends it when there is nothing to catch up on.
     */
    fun away() { catchUp = true }

    /** The player is back and the device has everything the server had: with nothing of the other player's past the
     * local head the game is live again (HIS-006); else it stays paused for Play. */
    fun returned() { if (session.buffered.none { it.author != session.me.value }) catchUp = false }

    /** Whether the shown state is in the opening (mulligans, turn 0): never played back (§29.1). */
    private val opening: Boolean get() = model.engine.turn.number == 0

    /**
     * The player paused playback (HIS-095, a tap on the table): it goes on to the next point where Full control would stop
     * (an assumption about them where they could have acted) and stops there ([Status.Stopped.paused]); their answer
     * there, or playing again, ends the pause. Personal: nothing is sent.
     */
    var pausing: Boolean = false
        set(value) { field = value && session.behind }

    /**
     * Shows the next buffered event unless playback stops before it: the event shown, or null. In the opening (§29.1)
     * every event up to the first turn, a stop or the head shows at once: the last of them is answered.
     */
    fun step(): GameEvent? {
        if (needsReload || holding || (catchUp && !opening) || session.drafting || !session.behind) return null
        if (viewHolds()) return null
        if (stopAt(session.buffered.first()) != null) return null
        var shown: GameEvent
        do shown = session.showNext() while (opening && session.behind && !viewHolds() && stopAt(session.buffered.first()) == null)
        if (!session.behind) { pausing = false; catchUp = false }
        return shown
    }

    // ---- Reveal stops (HIS-110)

    /**
     * HIS-110 (owner playtest 2026-11-07): cards the other player revealed to this player ([by]: who revealed them, never
     * this player) in the shown event [event]: [cards] in the order revealed, and what revealed them, [source] (the card
     * of the spell or ability, null when none is known) and its [sourceName] then. Each reveal is one panel; while one
     * is open ([reveal]) playback holds the events after it, as at a playback stop, until the player dismisses it
     * ([dismissReveal]). Personal: nothing is sent, and the device still makes its own steps at the head.
     */
    data class Reveal(val event: String, val by: PlayerId, val cards: List<arcanum.engine.state.CardId>,
        val source: arcanum.engine.state.CardId?, val sourceName: String?)

    /** HIS-110: the reveal panel open now (the oldest not dismissed), or null. */
    val reveal: Reveal? get() { syncReveals(); return revealsOpen.firstOrNull() }

    /** HIS-110: every reveal not dismissed yet, oldest first (one panel each, in order). */
    val openReveals: List<Reveal> get() { syncReveals(); return revealsOpen.toList() }

    /** HIS-110: the player dismissed the open reveal panel: the next one opens, or playback goes on. True when one was open. */
    fun dismissReveal(): Boolean {
        syncReveals()
        if (revealsOpen.isEmpty()) return false
        revealsOpen.removeAt(0)
        return true
    }

    private val revealsOpen = ArrayList<Reveal>()
    /** The shown events already looked at for reveals (those shown before the device was made count as looked at). */
    private val revealsSeen = HashSet<String>()
    /** The tip [syncReveals] last looked at: the shown line is the same while the tip is (each event names its parent). */
    private var revealsTip: String? = null

    init {
        // A game opened (or loaded again) opens no panel for what was shown before
        val local = session.indexOf(session.localHead) ?: -1
        session.events.take(local + 1).mapTo(revealsSeen) { it.id }
        revealsTip = session.tip
    }

    /** Whether an open reveal panel holds playback (HIS-110). */
    private fun revealHolds(): Boolean { syncReveals(); return revealsOpen.isNotEmpty() }

    /** Whether an open reveal panel (HIS-110) or a play shown (HIS-112) holds the view where it is. */
    private fun viewHolds(): Boolean { syncReveals(); return revealsOpen.isNotEmpty() || showsOpen.isNotEmpty() }

    /**
     * HIS-110, HIS-112: brings the open reveals and plays shown in line with the shown line (the main line up to the
     * local head, then the device's own pending events): one whose event was taken back (an undo, a branch, a rewind, a
     * refusal) closes, and the events newly shown since add the other player's reveals and the plays shown in them, in
     * order.
     */
    private fun syncReveals() {
        val tip = session.tip
        if (tip == revealsTip || session.drafting) return
        revealsTip = tip
        val local = session.indexOf(session.localHead) ?: -1
        val events = session.events
        val line = events.subList(0, local + 1) + session.pending
        if (revealsOpen.isNotEmpty() || showsOpen.isNotEmpty()) {
            val ids = line.mapTo(HashSet()) { it.id }
            val gone = revealsOpen.filter { it.event !in ids }
            revealsOpen.removeAll(gone)
            gone.forEach { revealsSeen.remove(it.event) }
            val goneShows = showsOpen.filter { it.event !in ids }
            showsOpen.removeAll(goneShows)
            goneShows.forEach { revealsSeen.remove(it.event) }
        }
        val fresh = ArrayList<GameEvent>()
        for (i in line.indices.reversed()) {
            val e = line[i]
            if (e.id in revealsSeen) break
            fresh += e
        }
        for (e in fresh.asReversed()) {
            revealsSeen += e.id
            revealsOpen += revealsIn(e, me)
            if (showPlays) showsOpen += momentsIn(e, me)
        }
    }

    // ---- Plays shown (HIS-112)

    /**
     * HIS-112 (owner 2026-11-07, MTG Arena as the reference): a Stack entry put on the Stack in the shown event [event]
     * that this player's table shows large in the middle for a moment before it moves to the Stack: the other player's
     * spells cast, abilities activated and triggers, and this player's own triggers (never their own casts or
     * activations). [by] controls it, [entry] is the engine's object for it on the Stack (the spell's card object, else its
     * Stack item), [kind] how it got there, [card] the spell's card or the ability's source card (null when it has none).
     * While one is open ([show]) the view holds where it is, as at a reveal: the events after it wait and the device makes
     * none of its own; the table ends it ([dismissShow]) when its time is up or the player taps it. Personal: nothing is
     * sent, and the game never waits for it on the other player's phone.
     */
    data class PlayShow(override val event: String, val by: PlayerId, val entry: ObjectId, val kind: PlayKind,
        val card: arcanum.engine.state.CardId?) : ViewMoment

    /** A moment of the shown event [event] that holds this player's view until it is done (HIS-112, HIS-113). */
    sealed interface ViewMoment { val event: String }

    /**
     * HIS-113 (owner, 2026-11-08): a combat holds this player's view as a play shown does, for the same time ([showPlays]),
     * whoever's combat it is, live and in catch-up playback: once when the attackers show and once on the damage (each
     * combat damage step that deals any). The table ends it ([dismissCombatHold]) when its time is up or the player taps the
     * table. Declaring no attackers holds nothing. Personal: nothing is sent, and the game never waits for it.
     */
    data class CombatHold(override val event: String, val kind: CombatMoment) : ViewMoment

    /** What a combat holds the view on (HIS-113). */
    sealed interface CombatMoment {
        data object Attackers : CombatMoment
        data object Damage : CombatMoment
    }

    /** How a shown play got onto the Stack (HIS-112). */
    sealed interface PlayKind {
        data object Cast : PlayKind
        data object Activated : PlayKind
        data object Triggered : PlayKind
    }

    /**
     * HIS-112: whether plays are shown at all (the player's "Show the other player's plays" time is above 0). Off (the
     * default: simulations and the server never show any), the events shown open none; turned off, the open ones close.
     */
    var showPlays: Boolean = false
        set(value) {
            syncReveals()
            field = value
            if (!value) showsOpen.clear()
        }

    /** HIS-112: the play shown now (the oldest moment not done, when it is a play), or null; never while a reveal panel is
     * open (HIS-110 first). */
    val show: PlayShow? get() { syncReveals(); return if (revealsOpen.isNotEmpty()) null else showsOpen.firstOrNull() as? PlayShow }

    /** HIS-113: the combat moment holding the view now (the oldest moment not done, when it is one), or null; never while a
     * reveal panel is open. */
    val combatHold: CombatHold? get() { syncReveals(); return if (revealsOpen.isNotEmpty()) null else showsOpen.firstOrNull() as? CombatHold }

    /** HIS-112: every play not shown to its end yet, oldest first. */
    val openShows: List<PlayShow> get() { syncReveals(); return showsOpen.filterIsInstance<PlayShow>() }

    /** HIS-112: the event of the last play done ([dismissShow]): playback lets the next event follow it at once (the show
     * took the playback delay's place, HIS-021). */
    var showDone: String? = null
        private set

    /**
     * HIS-112: the play shown now is done (its time is up, or the player tapped it): the next one shows, or the view goes
     * on. [which]: only when it is still the one shown (a play taken back meanwhile is gone already). True when one ended.
     */
    fun dismissShow(which: PlayShow? = null): Boolean {
        val open = show ?: return false
        if (which != null && which != open) return false
        showsOpen.removeAt(0)
        showDone = open.event
        return true
    }

    /** HIS-113: the combat moment holding the view now is done (its time is up, or the player tapped the table): the next
     * moment comes, or the view goes on. [which]: only when it is still the one. True when one ended. */
    fun dismissCombatHold(which: CombatHold? = null): Boolean {
        val open = combatHold ?: return false
        if (which != null && which != open) return false
        showsOpen.removeAt(0)
        showDone = open.event
        return true
    }

    private val showsOpen = ArrayList<ViewMoment>()

    /** Whether a play shown holds the view (HIS-112). */
    private fun showHolds(): Boolean { syncReveals(); return showsOpen.isNotEmpty() }

    /**
     * HIS-095 (§29.2, smoke 2026-11-04): the other player's passes reaching a device that showed everything before them (its
     * live game at the head) show at once instead of playing back: events that are only priority passes (what they led to
     * included, such as a spell resolving), with no table change and nowhere playback would stop before them. Never while
     * catching up, drafting or with the device's own events pending. True when it showed any.
     */
    fun showPasses(): Boolean {
        var shown = false
        while (!needsReload && !catchUp && !session.drafting && session.pending.isEmpty() && session.behind && !viewHolds()) {
            val next = session.buffered.first()
            if (!passesOnly(next) || stopAt(next) != null) break
            session.showNext()
            shown = true
        }
        if (shown && !session.behind) pausing = false
        return shown
    }

    private fun passesOnly(event: GameEvent): Boolean {
        val steps = event.engine?.steps ?: return false
        return event.table.isEmpty() && steps.any { it.input != null } && steps.all { it.input == null || it.input is Input.PassPriority }
    }

    /** Plays on at once to the next decision point, never past it (tests and simulations). */
    fun playToStop(): List<GameEvent> {
        catchUp = false
        pausing = false
        return generateSequence { step() }.toList()
    }

    /** The assumed input of [event] where this player's playback stops before it under [mode] (HIS-095: their current
     * response mode, [PlaySettings.mode]), else null (§7); while paused ([pausing]) wherever Full control stops (§27); and
     * always at the assumption that took the place of a move of theirs lost in a race ([lateAnswerAt]). */
    private fun stopAt(event: GameEvent, mode: ResponseMode = if (pausing) ResponseMode.FullControl else settings.mode): Input? {
        val step = event.assumed ?: return null
        if (step.assumedBy == me || Acceptance.assumedAbout(step, event, players) != me) return null
        if (remaking.isNotEmpty() && event.id == lateAnswerAt()) return step.input
        // TRN-017 (owner 2026-11-07): a Resolve chain presses Resolve there: the other player's assumption of that press
        // over the Stack shows (and so is confirmed) instead of stopping
        if (chain && !pausing && step.input is Input.PassPriority && model.engine.stack.isNotEmpty()) return null
        // The stop the player stood at when they changed their mode stays until they answer it ([keptAt])
        if (keeps(event.parentId)) return step.input
        return step.input?.takeIf { NextStop.stopsAt(model.engine, it, me, mode) }
    }

    // ---- The device's own steps

    /**
     * Makes the events the device makes by itself at the head (its player's passes by their mode, engine-automatic
     * answers, assumptions), until the game waits for a player or the server, and re-makes table-only events refused as
     * stale. [now]: the time.
     */
    fun advance(now: Long) {
        this.now = now
        settleQueuedUndo()
        retryTable()
        redeclare()
        steps()
        // The player's own moves lost in a race, compared where the device's own steps leave them, then what follows them
        if (remake()) steps()
    }

    private fun steps() { repeat(MAX_AUTOMATIC_EVENTS) { if (!automaticStep()) return } }

    private fun automaticStep(): Boolean {
        if (needsReload || holding || session.drafting || session.behind || request != null || locked || queuedUndo != null) return false
        // HIS-112: while a play is shown the board stays where it is: the device's own steps (its passes, its assumptions)
        // wait until it is done, so a spell shown isn't resolved under it by this device (the other player's device goes
        // on as ever)
        if (showHolds()) return false
        // R3: nothing to make here was already found at this point (no search again while it lasts)
        val key = pointKey()
        if (key.same(idleAt)) return false
        pointsWorkedOut++
        return decideStep().also { made -> if (!made) idleAt = key }
    }

    /** [automaticStep] worked out: whether the device made its own step at the head. */
    private fun decideStep(): Boolean {
        val s = model.engine
        if (Engine.result(s) != null) return false
        if (chain) chainEnds(s)?.let(::endChain)
        val who = Attention.of(s) ?: return false
        if (who == me || declares(s)) {
            if (held()) return false
            val input = own(s) ?: return false
            if (reveals["${session.tip}:$input"] == true) { endChain("press would show hidden cards"); return false }
            if (input is Input.PassPriority && Stops.stop(s, input.player) && endTurn != s.turn.number) ownMainPass?.invoke("by itself")
            // Spec rule 6 (owner 2026-11-06): the device's own pass is exactly a press of Resolve or the step arrow
            return (act(input, emptyList(), { null }, null, automatic = true) is Acted.Sent).also { made -> if (!made) endChain("press not made") }
        }
        return when (val point = point(s)) {
            is Point.Automatic -> make(point.input, assumed = false) is Acted.Sent
            // ... and its assumption exactly a press of Assume
            is Point.Assume -> assumed(point.input) is Acted.Sent
            // TRN-017, TRN-070: a Resolve chain presses the wait's Assume; a wait without it (greyed: the information rule,
            // TRN-071; or the other player's own decision) ends the chain
            is Point.Wait -> if (chain && point.offer == Offer.AssumeResolves && point.input != null) assumed(point.input) is Acted.Sent
                else { if (chain) endChain("wait ${point.reason}"); false }
        }
    }

    /**
     * TRN-017, STK-029, TRN-070 (owner 2026-11-07): the hold of Resolve, or of a Stack wait's Assume, pressed as its tap
     * when the other player's real inputs this device had received were [since] ([opponentInputs]): the Resolve chain
     * begins ([chain]), unless their input arrived meanwhile or the Stack is empty. Whether it began.
     */
    fun startChain(since: Int): Boolean {
        if (since != opponentInputs || needsReload || model.engine.stack.isEmpty()) return false
        chain = true
        holdAt = null
        remember("chain")
        return true
    }

    /** Ends a Resolve chain going on, for [why] (the device's record). */
    fun endChain(why: String) {
        if (!chain) return
        chain = false
        remember("chain ends: $why")
    }

    /** Why a Resolve chain going on ends on [s] at the head, or null: the Stack empty, a decision of the player's (not
     * answered by the engine itself), or the other player's reveal panel open (HIS-110). */
    private fun chainEnds(s: GameState): String? {
        if (s.stack.isEmpty()) return "stack empty"
        if (reveal != null) return "reveal"
        val d = s.pending?.decision
        if (d != null && d !is Decision.Random && Engine.decider(s, d.player) == me && WhoActs.automaticAnswer(model, d) == null) return "decision"
        return null
    }

    /** Ends the turn for this player (TRN-057): their device passes their priority until the turn ends, declaring no
     * attackers; the other player's points are assumed, automatic or waited for as usual. */
    fun endTurn(now: Long) {
        endChain("end turn")
        endTurn = model.engine.turn.number
        endTurnAt = session.tip
        endTurnFirst = null
        endTurnMade = true
        holdAt = null
        advance(now)
        endTurnMade = false
    }

    /**
     * HIS-097, HIS-084: the main line changed after [at] (a branch there, a rewind to it). An End turn going on was taken
     * back with it when the point it was tapped at is gone or is [at] itself, or its own first event is gone: it ends, so
     * the device passes nothing more for it on the new line (before, it carried the new line to the end of the turn by
     * itself, with the player's later actions "taken back"). A change after its start (an answer at its end step) leaves
     * it going on, as live.
     */
    private fun endTurnTakenBack(at: String) {
        if (endTurn == null) return
        fun gone(id: String) = !session.knows(id) && session.pending.none { it.id == id }
        val tapped = endTurnAt
        if (tapped != null && !gone(tapped) && tapped != at && endTurnFirst?.let(::gone) != true) return
        endTurn = null; endTurnAt = null; endTurnFirst = null
    }

    /** The End turn's first event among [dropped] (the server took another event first; it goes on as SYS-076 says): no
     * longer its first event. */
    private fun endTurnDropped(dropped: List<GameEvent>) {
        if (endTurnFirst != null && dropped.any { it.id == endTurnFirst }) endTurnFirst = null
    }

    /** The player's own input the device makes at the head by itself, or null (§6, §7). */
    private fun own(s: GameState): Input? {
        val d = s.pending?.decision
        if (d != null) {
            if (d is Decision.Random || Engine.decider(s, d.player) != me) return null
            return WhoActs.automaticAnswer(model, d) ?: oneAnswer(s, d)?.takeIf { !keeps(session.tip) }
        }
        val holder = s.priority.holder
        if (holder != null) {
            // An End turn going on passes whatever they could do: no search of their options
            if (endTurn == s.turn.number) return Input.PassPriority(holder)
            // TRN-017: so does a Resolve chain, over the Stack ([chainEnds] ends it once the Stack is empty)
            if (chain) return Input.PassPriority(holder)
            // The stop they stood at when they changed their mode waits for their answer ([keptAt])
            if (keeps(session.tip)) return null
            // One search of the player's options answers both (the engine's own pass and their mode's)
            val check = model.passCheck(holder)
            return Input.PassPriority(holder).takeIf { (check.asSet && !Stops.stop(s, holder)) || modePasses(s, holder, check) }
        }
        if (endTurn == s.turn.number && s.turn.active == me)
            return Engine.options(s, me, model.keptView()).firstOrNull { it is Input.DeclareAttackers && it.attacks.isEmpty() }
        return null
    }

    /** Under Auto-skip, the only answer to [d] (§2). Never the order of triggers (STK-030, owner 2026-11-06): the engine
     * lists one order of the many there are, and asks only when the triggers differ, so their player always orders them. */
    private fun oneAnswer(s: GameState, d: Decision): Input? = if (settings.mode != ResponseMode.AutoSkip || d is Decision.OrderTriggers) null
        else Engine.options(s, d.player, model.keptView()).filterIsInstance<Input.Answer>().filter { it.decision == d.id }.singleOrNull()

    /** Whether this player's device passes for [player] (theirs to decide) on [s] by their mode (§2, [Stops.passes]: never
     * at their own main phases, owner smoke 2026-11-04, STK-036) or an End turn. */
    private fun modePasses(s: GameState, player: PlayerId, check: arcanum.engine.PassCheck? = null): Boolean {
        if (endTurn == s.turn.number) return true
        return Stops.passes(settings.mode, s, player) { check?.ownSkipOn ?: NextStop.passesWithOwnCards(s, player) }
    }

    /** What the device does at the other player's point (§4). */
    private sealed interface Point {
        data class Automatic(val input: Input) : Point
        data class Assume(val input: Input) : Point
        data class Wait(val reason: GameDevice.Wait, val offer: Offer? = null, val input: Input? = null) : Point
    }

    private fun point(s: GameState): Point {
        val d = s.pending?.decision
        if (d != null) {
            if (d !is Decision.Random) WhoActs.automaticAnswer(model, d)?.let { return Point.Automatic(it) }
            // TRN-027, HIS-096 (owner, 2026-11-05): the other player's opening keep is never assumed: their opening is waited for
            if (Engine.options(s, d.player, model.keptView()).any { keep(it) }) return Point.Wait(Wait.Keep)
            return Point.Wait(Wait.Choosing(d.source?.takeIf { it in model.visible(me) }))
        }
        val holder = s.priority.holder
        if (holder != null) {
            if (Stops.forcedPass(model, holder)) return Point.Automatic(Input.PassPriority(holder))
            val pass = Input.PassPriority(holder)
            if (Stops.stop(s, holder) || !assumable(pass)) return Point.Wait(Wait.TheirTurn)
            if (revealing(pass)) return Point.Wait(Wait.HiddenCards)
            // HIS-096 (owner, 2026-11-05): never the other player's presence
            if (assumesAtOnce(s, holder)) return Point.Assume(pass)
            val top = s.stack.lastOrNull()
            val reason = if (top != null) Wait.Respond(top) else Wait.MayAct(s.turn.step)
            return Point.Wait(reason, Offer.AssumeResolves, pass)
        }
        val blocks = Engine.options(s, opponent, model.keptView()).filterIsInstance<Input.DeclareBlockers>()
        if (blocks.isNotEmpty()) {
            val none = Input.DeclareBlockers(opponent, emptyList())
            if (!assumable(none)) return Point.Wait(Wait.Blockers)
            // CMB-040: assumed only from the Assume blockers screen the player opens
            return Point.Wait(Wait.Blockers, Offer.AssumeBlockers, none)
        }
        return Point.Wait(Wait.TheirTurn)
    }

    /**
     * HIS-096 (owner 2026-11-06, 2026-11-07): whether [holder]'s pass on [s] (their priority, not at their own main phase)
     * is assumed at once. Always where [holder]'s own response mode would pass it ([opponentMode]: their Quick judged on
     * their real hand, information they give out by choosing Quick); at a point this player began ([pointOwner] on the
     * line up to the head), also where this player's Assume setting assumes it ([settingAssumes]). At a point [holder]
     * began, the setting doesn't count: where their mode stops, the device waits for them.
     */
    private fun assumesAtOnce(s: GameState, holder: PlayerId): Boolean {
        if (Stops.passes(opponentMode, s, holder) { model.passCheck(holder).ownSkipOn }) return true
        if (pointOwner(session.events + session.pending, s) != me) return false
        return settingAssumes(settings.assume, s, holder, me) { noMana(s, holder) }
    }

    /** Whether [holder] has no mana on [s] as this player's table shows it: nothing in their mana pool and nothing they
     * could activate (no untapped mana source, mana abilities being activations too, and no other ability usable now).
     * The cards in their hand are hidden and don't count. */
    private fun noMana(s: GameState, holder: PlayerId): Boolean =
        s.player(holder).manaPool.isEmpty() && Engine.options(s, holder, model.keptView()).none { it is Input.Activate }

    private fun keep(input: Input): Boolean = when (input) {
        is Input.SetupAnswer -> input.player == opponent && input.answer == arcanum.engine.decision.Answer.Option("Keep")
        is Input.Answer -> input.answer == arcanum.engine.decision.Answer.Option("Keep")
        else -> false
    }

    /** Whether this player may assume [input] of the other player's here (§3: the server's own rules). */
    private fun assumable(input: Input): Boolean =
        try { WhoActs.kind(model, input, me, assumed = true) is WhoActs.Kind.Assumed } catch (_: Refusal) { false }

    /** Whether assuming [input] at the tip would show this player a hidden card (§5): its whole outcome, up to the next
     * decision. Worked out once per point (the probe is taken back). */
    private fun revealing(input: Input): Boolean {
        val key = "${session.tip}:$input"
        reveals[key]?.let { return it }
        if (reveals.size > MAX_PROBES) reveals.clear()
        val shows = informationRule {
            val before = InformationRule.Before.of(model)
            val builder = open()
            try {
                builder.perform(input, assumedBy = me)
                builder.answerRandoms()
                fold(builder, own = false)
                shows(before, builder)
            } catch (_: RuntimeException) {
                false
            } finally {
                session.abandon()
            }
        }
        reveals[key] = shows
        return shows
    }

    // ---- The player's actions

    /**
     * The player's own [input] (and [follow]'s further inputs of their flow, then [table] changes) as one acting point:
     * at the head, or at a decision stop: there the same input as assumed confirms it ([Acceptance.confirms], the server's
     * rule) and what [follow] gives after it is the player's next action, made after the confirmed event (at the head, or
     * at the next assumption about them: confirming or branching it in turn); any other input branches with a new line.
     * A concession at any time (behind the head: a branch, §11).
     *
     * [performed] (SYS-025): called once the player's inputs are made at the head, before what follows by itself is folded
     * in, when what is made so far shows this player nothing the information rule holds back: the model already shows
     * the action, so a host may show it before the event is finished. The player's own passes that follow stop before
     * one that would show a hidden card (the player then holds priority, as their device's own steps do, §5), so after
     * it only what the engine does by itself (a forced pass, an automatic answer) showing a hidden card, or an engine
     * failure, refuses the action (problem report N7FU6Y).
     */
    fun act(input: Input, table: List<TableChange> = emptyList(), performed: (() -> Unit)? = null,
        follow: (GameModel) -> Input? = { null }): Acted = act(input, table, follow, null, performed).also { refusedHere("act", it, input) }

    /** The record says why the player's own action was refused here, what it was and what was open (report DH3QX6). */
    private fun refusedHere(what: String, acted: Acted, input: Input?) {
        if (acted !is Acted.Refused) return
        remember("$what refused: ${acted.reason}")
        remember("$what refused at ${where()}, open ${openDecision()}, input $input")
    }

    /** The open decision in a word and its id, with the cards a choice of objects offers, for the record. */
    private fun openDecision(): String = model.engine.pending?.decision?.let { d ->
        "${d.kind} ${d.id.value}" + ((d as? arcanum.engine.decision.Decision.ChooseObjects)?.options?.let { " $it" } ?: "")
    } ?: "none"

    /** An undo request for the record: by whom, to where; never the player's message, which may hold anything. */
    private fun asked(r: EngineRewindRequest?) = r?.let { "by ${it.by} to ${it.target}" } ?: "none"

    /** Where the device's line stands, in short ids, for the record's lines about going back. */
    private fun where() = "tip ${session.tip.take(8)}, head ${session.head.take(8)}, local ${session.localHead.take(8)}, " +
        "pending ${session.pending.size}, line ${model.table.line.number}/$lines"

    /** [automatic]: the device's own pass or answer for the player (§6), made exactly as their press is (spec rule 6, owner
     * 2026-11-06); only who made it differs: its loss in a race goes quietly (SYS-012) and Undo goes back past it. */
    private fun act(input: Input, table: List<TableChange>, follow: (GameModel) -> Input?, replay: ArrayDeque<Input>?,
        performed: (() -> Unit)? = null, automatic: Boolean = false): Acted {
        if (!automatic) endChain("own action")
        refusal(input)?.let { return Acted.Refused(it) }
        if (!session.behind) return make(input, assumed = false, follow = follow, table = table, player = !automatic, performed = performed)
        val stop = status as? Status.Stopped
        // An answer of the player's own ends a pause (§27): playback goes on by their mode after it
        pausing = false
        if (stop != null && Acceptance.confirms(input, stop.assumed)) return confirmThen(input, table, follow, replay)
        if (stop == null && input !is Input.Concede) return Acted.Refused("playback")
        return make(input, assumed = false, follow = follow, table = table, newLine = lines + 1, player = true)
    }

    /** Confirms the assumption at the stop with [input], then makes what [follow] gives next and [table] (I-D56 §8:
     * "a confirmation followed by new input"). [replay]: [follow]'s queue, when it replays a draft's inputs (the confirmed
     * event's own automatic answers are skipped in it). */
    private fun confirmThen(input: Input, table: List<TableChange>, follow: (GameModel) -> Input?, replay: ArrayDeque<Input>?): Acted {
        val event = session.buffered.first()
        confirm(input)
        if (replay != null) for (folded in event.engine?.steps.orEmpty().drop(1).mapNotNull { it.input })
            if (replay.firstOrNull() == folded) replay.removeAt(0)
        var acted: Acted = Acted.Confirmed
        val next = follow(model)
        if (next != null) acted = goOn(next, follow, replay)
        if (table.isNotEmpty() && acted !is Acted.Refused) {
            val target = if (session.behind) session.headModel() else model
            val again = rebase(target, table) ?: return Acted.Refused("inapplicable")
            this.table(again).let { if (acted == Acted.Confirmed) acted = it }
        }
        return acted
    }

    /** The player's next [input] after a confirmation: at the head, or at the next assumption about them (whatever
     * their mode: an answer that differs from it is a branch). */
    private fun goOn(input: Input, follow: (GameModel) -> Input?, replay: ArrayDeque<Input>?): Acted {
        if (!session.behind) return make(input, assumed = false, follow = follow, player = true)
        val assumed = assumedAboutMe(session.buffered.first()) ?: return Acted.Refused("playback")
        if (Acceptance.confirms(input, assumed)) return confirmThen(input, emptyList(), follow, replay)
        return make(input, assumed = false, follow = follow, newLine = lines + 1, player = true)
    }

    /** The assumed input of [event] when it is the other player's assumption about this player, else null. */
    private fun assumedAboutMe(event: GameEvent): Input? {
        val step = event.assumed ?: return null
        if (step.assumedBy == me || Acceptance.assumedAbout(step, event, players) != me) return null
        return step.input
    }

    private fun refusal(input: Input?): String? = when {
        needsReload -> "reload"
        holding && input !is Input.Concede -> "playback"
        session.drafting -> "drafting"
        input is Input.Concede -> null
        request != null -> REWIND_REQUESTED
        locked && input !is Input.AgreedResult -> "suggestion"
        else -> null
    }

    /** The player's answer equal to the assumed input at their stop: sent (the server confirms it, I-D56 §8), and
     * playback goes on past it. */
    private fun confirm(input: Input) {
        val submission = Submission(ids(), session.localHead, listOf(Act.Perform(input)))
        confirming += submission.id
        send(GameAction.Submit(submission))
        session.showNext()
        pausing = false
    }

    /**
     * Opens a draft for a flow of the player's own (a cast with its choices): at the head, or at a decision stop (on the
     * main line as it is there: [send] confirms the assumption when its first input is the assumed one and makes the
     * rest after it, else sends it as a branch with a new line); null when the player can't act now. The caller
     * performs the flow's inputs on it, then [send]s or [abandon]s. A draft the main line drops under it (a branch, a
     * rewind, another event at the head that doesn't commute with it) is gone: told once, and [send] answers "noDraft".
     */
    fun begin(): EventBuilder? {
        endChain("own action")
        if (refusal(null) != null) return null
        val stop = if (session.behind) status as? Status.Stopped ?: return null else null
        draftBefore = informationRule { if (tentative()) InformationRule.Before.of(model) else null }
        val builder = open()
        openDraft = builder
        draftStop = stop
        return builder
    }

    /** Sends the open draft with what follows it by itself (the player's passes by their mode, automatic answers). */
    fun send(): Acted = sendDraft().also { refusedHere("draft", it, null) }

    private fun sendDraft(): Acted {
        val builder = openDraft ?: return Acted.Refused("noDraft")
        if (builder.finished || !session.drafting) { dropDraft(); return Acted.Refused("noDraft") }
        if (needsReload) { abandon(); return Acted.Refused("reload") }
        val stop = draftStop ?: return finish(builder, own = true, before = draftBefore, player = true)
        // At a decision stop: the same rule as the server's (confirm, then the rest), else a branch made again with
        // its new line first
        val inputs = builder.playerInputs
        val table = builder.tableChanges
        abandon()
        val first = inputs.firstOrNull() ?: return if (table.isEmpty()) Acted.Refused("empty") else this.table(table)
        val replay = ArrayDeque(inputs.drop(1))
        if (session.buffered.firstOrNull()?.id != stop.event.id) return Acted.Refused("playback")
        return act(first, table, { replay.removeFirstOrNull() }, replay)
    }

    /** Takes the open draft back. */
    fun abandon() {
        session.abandon()
        openDraft = null; draftStop = null; draftBefore = null
    }

    /** The open draft is gone from the session (the main line changed under it): forgotten, and told once. */
    private fun dropDraft() {
        if (openDraft == null) return
        openDraft = null; draftStop = null; draftBefore = null
        note(Note.Refused(DRAFT_DROPPED))
    }

    private fun checkDraft() { if (openDraft != null && (openDraft!!.finished || !session.drafting)) dropDraft() }

    private var openDraft: EventBuilder? = null
    private var draftStop: Status.Stopped? = null
    private var draftBefore: InformationRule.Before? = null

    /** Opens a draft at the tip: starting [newLine] (a branch), or the line a rewind left due at the head (§9). */
    private fun open(newLine: Int? = null): EventBuilder {
        val line = newLine ?: if (session.behind) null else Rewind.lineDue(model, lines)
        return session.begin(ids(), line).also { builder ->
            // HIS-042: what a new line's reshuffle moved, and what it was made under (report DH3QX6)
            if (line != null) remember("line $line starts: cards moved per library " + builder.changes
                .filterIsInstance<arcanum.engine.change.Change.Reorder>().map { c -> c.before.indices.count { c.before[it] != c.after[it] } } +
                " at ${where()}, open ${openDecision()}")
        }
    }

    /** Makes [input] (assumed: the other player's, §3) as an event at the tip, or at the local head with [newLine]. */
    private fun make(input: Input, assumed: Boolean, follow: (GameModel) -> Input? = { null }, table: List<TableChange> = emptyList(),
        newLine: Int? = null, player: Boolean = false, performed: (() -> Unit)? = null): Acted {
        // The server's rules (§6): the player's own inputs (or engine-automatic answers), the other player's only assumed
        try {
            val kind = WhoActs.kind(model, input, me, assumed)
            if (kind == WhoActs.Kind.Random) return Acted.Refused("Not the player's input")
        } catch (e: Refusal) { return Acted.Refused(e.reason) }
        val before = informationRule { if (assumed || tentative()) InformationRule.Before.of(model) else null }
        // HIS-101: the player's own press leaving their main phase (their device's own passes there are an End turn's); a
        // move made again after a lost race (SYS-077) comes here as a press too, and keeps its mark
        val press = model.engine.turn.step.takeIf { player && !assumed && input == Input.PassPriority(me) && model.engine.pending == null && Stops.stop(model.engine, me) }
            ?.let { if (it == arcanum.api.TurnStep.PrecombatMain) MainPress.ToCombat else MainPress.ToEndStep }
        val builder = open(newLine)
        try {
            // Inputs worked out on the board shown answer the same decisions after a new line's reorder (problem report
            // GXKJSE: a decision's id doesn't read the libraries' order)
            fun made(input: Input, assumedBy: PlayerId?) {
                builder.perform(input, assumedBy = assumedBy)
                builder.answerRandoms()
            }
            made(input, if (assumed) me else null)
            if (!assumed) while (true) made(follow(model) ?: break, null)
            table.forEach(builder::table)
        } catch (e: RuntimeException) {
            session.abandon()
            return Acted.Refused(e.message ?: "refusedByEngine")
        }
        // SYS-025: what is made so far may be shown now, unless it alone would show this player something the information
        // rule holds back (then the whole event is refused, before anything shows). What follows by itself can't refuse
        // it by that rule any more: the player's own passes stop before one that would show something ([fold])
        if (performed != null && (before == null || !shows(before, builder))) performed()
        return finish(builder, own = !assumed, before = before, player = player, press = press)
    }

    /** Folds what follows by itself into [builder]'s event and sends it, unless it would show this player a hidden card
     * or a random result while [before] is given (the state before it: their line is tentative, or the event assumes):
     * then the draft is taken back unsent (§5). [player]: made by the player's own action (a refusal is told). [press]:
     * the main button press leaving their main phase it records ([GameEvent.press]; an End turn's first pass records its own). */
    private fun finish(builder: EventBuilder, own: Boolean, before: InformationRule.Before?, player: Boolean = false,
        press: MainPress? = null): Acted {
        val inputs = if (player) builder.playerInputs to builder.tableChanges else null
        val madeSoFar = builder.inputs.size
        try { fold(builder, own, guard = before) } catch (e: RuntimeException) { abandon(); return Acted.Refused(e.message ?: "refusedByEngine") }
        // I-D56 §5: while the line is tentative (or this assumes), nothing may show this player a hidden card
        if (before != null && shows(before, builder)) {
            val first = builder.inputs.firstOrNull()
            abandon()
            reveals["${session.tip}:$first"] = true
            return Acted.Refused(REVEALS_HIDDEN)
        }
        openDraft = null; draftStop = null; draftBefore = null
        // Each event records the response mode it was made under; an assumption the other player's (owner 2026-11-06)
        builder.mode = if (own) settings.mode else opponentMode
        val first = builder.inputs.firstOrNull()
        val ended = own && endTurnMade && (first == Input.PassPriority(me) || (first as? Input.DeclareAttackers)?.player == me)
        // HIS-101: only an End turn's first pass records it; one that begins with a declaration is a real input as it is
        builder.press = if (ended && first is Input.PassPriority) MainPress.EndTurn else press
        val folded = builder.inputs.getOrNull(madeSoFar)
        val event = session.sendEvent()
        if (player) byPlayer += event.id
        if (player && folded != null) {
            foldedPress[event.id] = folded
            if (foldedPress.size > MAX_ORIGINS) foldedPress.remove(foldedPress.keys.first())
        }
        if (inputs != null && inputs.first.isNotEmpty()) {
            made[event.id] = inputs
            if (made.size > MAX_ORIGINS) made.remove(made.keys.first())
        }
        if (own) {
            origins[event.id] = player || ended
            if (ended) { endTurnMade = false; endTurnFirst = event.id }
            if (origins.size > MAX_ORIGINS) origins.remove(origins.keys.first())
        }
        send(GameAction.Submit(Submission.of(event)))
        return Acted.Sent(event)
    }

    /**
     * Whether what [builder] made on [before] would show this player something the information rule holds back (§5): a
     * hidden card ([InformationRule.revealing]), or a random result (a coin flip deciding a look would tell the result
     * before it happens if the wait were decided by it; so any outcome with a random result waits, decided without it).
     */
    private fun shows(before: InformationRule.Before, builder: EventBuilder): Boolean = informationRule {
        // The model is [before] with the builder's changes made (its builders are on it): its kept view is the after view
        InformationRule.revealing(before, builder.changes, me, model.view(), assumeReveals).isNotEmpty() ||
            InformationRule.randomResult(builder.engineSteps, before.state, me, assumeReveals)
    }

    /**
     * Folds what follows the event's input by itself into it (I-D35, §6): random answers, engine-automatic answers,
     * public forced passes of either player, and with [own] the player's own passes by their mode and (Auto-skip) their
     * one-answer questions. Stops at anything else, at the game's end, after a whole round of turns at the next stop,
     * at a loop of the player's own yields, and turns a loop of forced passes into the engine's loop choice (CR 104.4b).
     * With [guard] (the state before the event, on a tentative line) it also stops before a pass of the player's own that
     * would show them something the information rule holds back (§5): the pass is taken back and the player holds
     * priority there, as their device's own steps leave it ([automaticStep]), instead of the whole action being refused.
     */
    private fun fold(builder: EventBuilder, own: Boolean, guard: InformationRule.Before? = null) {
        val start = model.engine.turn.number
        val forcedSeen = mutableSetOf<LoopKey>()
        val yielded = mutableSetOf<LoopKey>()
        repeat(MAX_FOLDED) {
            val s = model.engine
            if (Engine.result(s) != null) return
            val d = s.pending?.decision
            if (d != null) {
                if (d is Decision.Random) { builder.answerRandoms(); return@repeat }
                val answer = WhoActs.automaticAnswer(model, d)
                    ?: if (own && Engine.decider(s, d.player) == me) oneAnswer(s, d) else null
                builder.perform(answer ?: return)
                return@repeat
            }
            val holder = s.priority.holder ?: return
            val mine = Engine.decider(s, holder) == me
            // After an assumed input only engine-automatic answers fold: the player's own inputs are their own event
            if (mine && !own) return
            // One search of the holder's options answers both the forced pass and their own mode's (playtest 2026-11-04),
            // kept with the state (the device's next point there reads it again)
            val check = model.passCheck(holder)
            val forced = check.asSet && !Stops.stop(s, holder)
            val yields = mine && !forced && modePasses(s, holder, check)
            if (!forced && !yields) return
            if (s.turn.number >= start + 2 && Stops.stop(s, s.turn.active)) return
            // TRN-060 (CR 104.4b): a pass nobody could avoid is a forced one, or the player's own where they can do nothing
            // but pass: what they hold can't stop the loop (a hand of only lands, owner 2026-11-05)
            val unavoidable = forced || (yields && check.ownSkipOn)
            val key = if (yields || s.stack.isNotEmpty() && unavoidable) Engine.loopKey(s) else null
            if (!unavoidable) forcedSeen.clear()
            else if (key != null && s.stack.isNotEmpty() && !forcedSeen.add(key)) {
                builder.perform(Input.MandatoryLoop)
                return@repeat
            }
            if (yields && !yielded.add(key!!)) return
            val mark = builder.mark()
            builder.perform(Input.PassPriority(holder))
            builder.answerRandoms()
            if (yields && guard != null && shows(guard, builder)) {
                builder.takeBack(mark)
                return
            }
        }
    }

    /** Assumes the other player's answer offered on the waiting bar (§4): their pass, or [blocks] (the
     * Assume blockers screen's, "No blocks" when empty). */
    fun assume(blocks: List<Block> = emptyList()): Acted {
        endChain("own action")
        refusal(null)?.let { return Acted.Refused(it) }
        if (session.behind) return Acted.Refused("playback")
        val point = point(model.engine) as? Point.Wait ?: return Acted.Refused("noOffer")
        val input = when (point.offer) {
            null -> return Acted.Refused("noOffer")
            Offer.AssumeBlockers -> Input.DeclareBlockers(opponent, blocks)
            else -> point.input!!
        }
        return assumed(input).also { acted ->
            if (acted !is Acted.Sent) return@also
            assumeTaps += acted.event.id
            if (assumeTaps.size > MAX_ORIGINS) assumeTaps.remove(assumeTaps.first())
        }
    }

    /** Assumes the other player's [input] at the head (§3): a press of Assume and the device's own assumption alike (spec
     * rule 6, owner 2026-11-06); the event records the other player's response mode. */
    private fun assumed(input: Input): Acted = make(input, assumed = true)

    /**
     * Table-only [changes] (placing your own cards, early answers, suggestions, confirming the result): made at the head
     * while they apply there (§6), shown at once when the player is at the head, else when playback reaches them;
     * staging (the attack and block drafts) behind the head stays on the device ([Acted.Kept]).
     */
    fun table(changes: List<TableChange>): Acted {
        endChain("own action")
        if (needsReload) return Acted.Refused("reload")
        if (holding) return Acted.Refused("playback")
        if (request != null) return Acted.Refused(REWIND_REQUESTED)
        if (locked && changes.any { it !is TableChange.Suggestion && it !is TableChange.ResultConfirmed }) return Acted.Refused("suggestion")
        if (session.behind && changes.any { it is TableChange.AttackDraft || it is TableChange.BlockDraft }) return Acted.Kept
        if (session.behind && changes.any { it is TableChange.EarlyAnswered }) return Acted.Refused("behind")
        if (session.drafting) return Acted.Refused("drafting")
        if (!session.behind) {
            val builder = open()
            builder.mode = settings.mode
            try { changes.forEach(builder::table) } catch (e: RuntimeException) { session.abandon(); return Acted.Refused("inapplicable") }
            val event = session.sendEvent()
            send(GameAction.Submit(Submission.of(event)))
            return Acted.Sent(event)
        }
        // Made at the head, with the values there as the before-values (the player sees the board at their local head)
        val model = session.headModel()
        val again = rebase(model, changes)
        if (again == null) return Acted.Refused("inapplicable")
        val builder = EventBuilder(model, ids(), session.head, me.value)
        builder.mode = settings.mode
        Rewind.lineDue(model, lines)?.let(builder::startLine)
        try { again.forEach(builder::table) } catch (e: RuntimeException) { return Acted.Refused("inapplicable") }
        val event = builder.build()
        send(GameAction.Submit(Submission.of(event)))
        return Acted.Sent(event)
    }

    /** Whether a staging among [changes] (an attack or block draft) is of a declaration [model]'s game still awaits (as
     * the table stages only those): a staging lost in a race and made again after its declaration (made again first,
     * [remake]) stages nothing. */
    private fun staging(model: GameModel, changes: List<TableChange>): Boolean {
        val drafts = changes.filter { it is TableChange.AttackDraft && it.after != null || it is TableChange.BlockDraft && it.after != null }
        if (drafts.isEmpty()) return true
        val s = model.engine
        val options = runCatching { s.players.flatMap { Engine.options(s, it.id) } }.getOrDefault(emptyList())
        return drafts.all { c ->
            if (c is TableChange.AttackDraft) options.any { it is Input.DeclareAttackers } else options.any { it is Input.DeclareBlockers }
        }
    }

    private fun retryTable() {
        if (retry.isEmpty() || session.drafting || needsReload) return
        val ready = retry.filter { it.parent != session.head }
        retry.removeAll(ready)
        for (r in ready) {
            val at = if (session.behind) session.headModel() else model.copy()
            val fixed = rebase(at, r.table) ?: continue
            if (staging(at, fixed)) table(fixed)
        }
    }

    /** [changes] made again on [model]'s table: each with the value there as its before-value, or null when one no
     * longer applies (an early answer's decision gone: its before-value is whatever is there now). */
    private fun rebase(model: GameModel, changes: List<TableChange>): List<TableChange>? = changes.map { c ->
        val t = model.table
        when (c) {
            is TableChange.EarlyAnswered -> c.copy(before = t.earlyAnswers[c.key])
            is TableChange.AttackDraft -> c.copy(before = t.attackDraft)
            is TableChange.BlockDraft -> c.copy(before = t.blockDraft)
            is TableChange.Suggestion -> if (t.suggestion != c.before) return null else c
            is TableChange.ResultConfirmed -> if (t.resultConfirmedBy != c.before) return null else c
            is TableChange.LineMoved -> return null
        }
    }

    /** Concedes (CR 104.3a): at any time; behind the head a branch at the local head (§11). */
    fun concede(): Acted = act(Input.Concede(me))

    /** Suggests ending the game with [result] (TRN-046) and the optional reason [note], at the head. */
    fun suggest(result: GameResult, note: String = ""): Acted =
        table(listOf(TableChange.Suggestion(model.table.suggestion, ResultSuggestion(me, result, note))))

    /** Answers the other player's waiting suggestion: [accept] agrees the result (an engine input), else declines it. */
    fun answerSuggestion(accept: Boolean): Acted {
        val s = model.table.suggestion ?: return Acted.Refused("noSuggestion")
        val clear = listOf(TableChange.Suggestion(s, null))
        return if (accept) act(Input.AgreedResult(s.result), table = clear) else table(clear)
    }

    /** Confirms the result the engine reports once both local heads reached it (TRN-065, §11). */
    fun confirmResult(): Acted {
        val over = status as? Status.Over ?: return Acted.Refused("noResult")
        if (!over.confirmable) return Acted.Refused("waitForCatchUp")
        return table(listOf(TableChange.ResultConfirmed(null, me)))
    }

    /** An early answer of the player's own (I-D33), only while their local head is the head (§6). */
    fun earlyAnswer(key: String, answer: EarlyAnswer?): Acted {
        if (session.behind) return Acted.Refused("behind")
        return table(listOf(TableChange.EarlyAnswered(key, model.table.earlyAnswers[key], answer)))
    }

    /**
     * The player's private settings for this game (§7): they apply to the points not yet played back. A new response
     * mode applies from the next point on (owner 2026-11-07, report WDKHSB): where the player stands at a stop of theirs
     * (live, or a playback stop their mode made), it stays until they answer it ([keptAt]), never passed by itself.
     */
    fun settings(new: PlaySettings) {
        if (new == settings) return
        if (new.mode != settings.mode) when (val now = status) {
            is Status.Stopped -> if (!now.paused) keptAt = session.tip
            Status.YourMove -> keptAt = session.tip
            else -> {}
        }
        settings = new
        reveals.clear()
        send(GameAction.EngineSettings(new))
    }

    // ---- Undo and rewinds (§10)

    /** What an undo would do. */
    sealed interface Undo {
        /** Sent: the server takes the line back to [target]. */
        data class Free(val target: String) : Undo
        /** Needs the other player's approval ([reason]): the caller opens the request dialog with [target] chosen. */
        data class Request(val target: String, val reason: String) : Undo
        /** Nothing to undo now ([reason]). */
        data class Unavailable(val reason: String) : Undo
        /** Waits for the player's own action [event] (and whatever else of theirs is on its way) to reach the server,
         * then takes it back ([undoTap]). */
        data class Queued(val event: String) : Undo
    }

    /** The point just before the player's latest own real input shown (their later assumed inputs go with it): never
     * the device's automatic steps (its passes by the player's mode, engine-automatic answers it made) nor a rewind's
     * line start, so a second Undo goes on back past the player's previous action (report C9PVLM). Passes are implied
     * (§29.2): a pass is never the target, the player's own included, except the first pass of an End turn and a press
     * leaving their own main phase ([undoPoint], HIS-101). */
    fun undoTarget(): String? {
        if (holding) return null
        val shown = session.indexOf(session.localHead) ?: return null
        return session.events.take(shown + 1).lastOrNull(::undoPoint)?.parentId
    }

    /** HIS-084, HIS-101: whether Undo goes back to just before [e]: one of the player's own real inputs, or their press
     * leaving their own main phase, as the record says ([GameEvent.press]). (A branch's note names only the real inputs,
     * HIS-097.) */
    private fun undoPoint(e: GameEvent): Boolean = realInput(e) || (e.author == me.value && e.press != null)

    /** Whether [e] is one of this player's own real inputs (an action of theirs, or a table-only event of theirs). A pass is
     * one only as the first of an End turn, as the record says ([GameEvent.press]). Other events made before this device
     * was loaded are judged by their input: the player's own inputs for the other player there are taken as automatic. */
    private fun realInput(e: GameEvent): Boolean {
        if (e.author != me.value || e.assumed != null) return false
        val input = e.engine?.steps?.firstNotNullOfOrNull { it.input }
        if (input == null) return e.table.any { it !is TableChange.LineMoved }
        if (input is Input.PassPriority) return e.press == MainPress.EndTurn
        origins[e.id]?.let { return it }
        return when (input) {
            is Input.PlayLand -> input.player == me
            is Input.Cast -> input.player == me
            is Input.Activate -> input.player == me
            is Input.DeclareAttackers -> input.player == me
            is Input.DeclareBlockers -> input.player == me
            is Input.SetupAnswer -> input.player == me
            else -> true
        }
    }

    /** Undo to [target] (by default [undoTarget]): sent when free, else [Undo.Request]. A free undo ends the player's
     * moves waiting to be made again after a lost race ([remake]) and table changes waiting to be re-made: they were made
     * on the line it takes back. */
    fun undo(target: String? = undoTarget()): Undo {
        val from = where()
        return undoTo(target).also { remember("undo asked to $target from $from: $it") }
    }

    private fun undoTo(target: String?): Undo {
        endChain("undo")
        if (holding) return Undo.Unavailable("playback")
        if (target == null) return Undo.Unavailable("nothing")
        if (request != null) return Undo.Unavailable(REWIND_REQUESTED)
        if (session.drafting || session.pending.isNotEmpty()) return Undo.Unavailable("sending")
        val after = lineAfter(target) ?: return Undo.Request(target, "unknown")
        if (after.isEmpty()) return Undo.Unavailable("nothing")
        val at = (reach(target) as? Reach.State)?.model ?: return Undo.Request(target, "unknown")
        val reason = Rewind.requestNeeded(at, target, after, me, heads.filterKeys { it != me }.values.toList())
        if (reason != null) return Undo.Request(target, reason)
        forgetRemakes()
        send(GameAction.EngineRewind(target))
        undoLocally(target)
        return Undo.Free(target)
    }

    /**
     * SYS-025 (owner, 2026-11-08): the player's own free undo to [target], sent, shown at once as every own action is:
     * the session takes back what the server's rewind will ([GameSession.undoLocally]); the device then waits for the
     * player at [target] (§10). The rewind, when it comes, finds it done ([revision]); refused, the game loads again.
     */
    private fun undoLocally(target: String) {
        when (val r = session.undoLocally(target)) {
            null -> remember("undo to $target shown when the server's rewind comes")
            GameSession.Received.Reload -> { needsReload = true; remember("reload at own undo to $target: ${followed()}") }
            is GameSession.Received.Changed -> {
                r.dropped.forEach { byPlayer.remove(it.id) }
                localUndos += target
                // The rewind starts a new line, counted as the server will count it (§9): the next event starts it
                lines++
                endTurnTakenBack(target)
                holdAt = session.head
                remember("undo to $target shown: ${r.discarded.size} taken back, ${r.dropped.size} pending")
            }
            else -> {}
        }
    }

    /**
     * The Undo button's tap (HIS-084, HIS-107): [undo] back to just before the player's latest own action. While
     * something of theirs is still on its way to the server (their action's event, their device's own steps after it, a
     * move waiting to be made again after a lost race, SYS-077), the undo waits for it instead ([Undo.Queued]; owner
     * problem report 2026-11-05: an Undo right after Attack was refused): it takes back the action the player last made
     * when they tapped, by its event, never one made or arriving after the tap; the device makes nothing of its own
     * meanwhile. Once everything of theirs is in, the undo is made ([settleQueuedUndo]): free at once, else
     * [Note.RequestNeeded] (the Undo dialog with that point chosen). A move still waiting to be made again is simply not
     * made again.
     */
    fun undoTap(): Undo {
        val from = where()
        return tappedUndo().also { remember("undo tapped from $from: $it") }
    }

    private fun tappedUndo(): Undo {
        endChain("undo")
        if (holding) return Undo.Unavailable("playback")
        if (request != null) return Undo.Unavailable(REWIND_REQUESTED)
        if (session.drafting) return Undo.Unavailable("drafting")
        if (session.pending.isEmpty() && remaking.isEmpty()) return undo()
        sentUndo()?.let { return it }
        val shown = session.indexOf(session.localHead) ?: -1
        val latest = session.pending.lastOrNull(::undoPoint)?.id ?: remaking.lastOrNull()?.event
            ?: session.events.take(shown + 1).lastOrNull(::undoPoint)?.id ?: return Undo.Unavailable("nothing")
        remember("undo queued after $latest")
        queuedUndo = latest
        settleQueuedUndo()
        return Undo.Queued(latest)
    }

    /**
     * SYS-025 (owner, 2026-11-08): the Undo button's tap while the player's latest own action (and their device's own
     * steps after it) is still on its way to the server: when that action was made on the shown head and the undo is free
     * now, the rewind goes out right behind it (the server makes them in order) and the undone board shows at once
     * ([undoLocally]), as every own action does. Null when it isn't so: the undo waits for it instead ([Undo.Queued]).
     */
    private fun sentUndo(): Undo? {
        if (remaking.isNotEmpty() || session.behind) return null
        val latest = session.pending.lastOrNull(::undoPoint) ?: return null
        val target = latest.parentId
        if (target != session.localHead) return null
        val after = (lineAfter(target) ?: return null) + session.pending
        val at = (reach(target) as? Reach.State)?.model ?: return null
        if (Rewind.requestNeeded(at, target, after, me, heads.filterKeys { it != me }.values.toList()) != null) return null
        forgetRemakes()
        send(GameAction.EngineRewind(target))
        undoLocally(target)
        return Undo.Free(target)
    }

    /**
     * The queued Undo ([undoTap]) made once nothing of the player's is on its way any more: the line taken back to just
     * before its action's event (wherever the line went meanwhile: the other player's later input makes it a request, the
     * Undo dialog), or, when the action is a move waiting to be made again ([remake]), that move and those after it not
     * made again. An action that is gone (refused, taken back) leaves nothing to undo: it ends quietly (its refusal was
     * told).
     */
    private fun settleQueuedUndo() {
        val id = queuedUndo ?: return
        if (needsReload) { queuedUndo = null; return }
        val lost = remaking.indexOfFirst { it.event == id }
        if (lost >= 0) {
            remember("undo: ${remaking.size - lost} move(s) not made again")
            repeat(remaking.size - lost) { remaking.removeAt(remaking.lastIndex) }
            queuedUndo = null
            return
        }
        if (session.pending.isNotEmpty() || session.drafting || request != null) return
        queuedUndo = null
        val event = session.events.firstOrNull { it.id == id } ?: run { remember("undo queued: $id gone"); return }
        when (val undo = undo(event.parentId)) {
            is Undo.Request -> note(Note.RequestNeeded(undo.target))
            else -> remember("undo queued: $undo")
        }
    }

    /** The player's moves waiting to be made again ([remake]) and table changes waiting to be re-made ([retryTable])
     * are dropped: their line is being taken back. */
    private fun forgetRemakes() {
        if (remaking.isNotEmpty()) remember("undo: ${remaking.size} move(s) not made again")
        remaking.clear()
        retry.clear()
    }

    /** Whether [at] is still the point [keptAt] names: it, or an event after it with only table-only events (card places,
     * early answers, staging of either player) between them, on the line or pending. */
    private fun keeps(at: String): Boolean {
        val kept = keptAt ?: return false
        if (at == kept) return true
        val after = (session.after(kept) ?: return false) + session.pending
        val upTo = after.indexOfFirst { it.id == at }.takeIf { it >= 0 } ?: return false
        return after.subList(0, upTo + 1).all { e -> e.engine?.steps.orEmpty().none { it.input != null } }
    }

    /** Whether the device holds at a free undo's point ([holdAt]): only table-only events (the other player's card places
     * or early answers, the player's own) came after it, on the line or pending; any input there ends the hold. */
    private fun held(): Boolean {
        val at = holdAt ?: return false
        if (at == session.tip) return true
        val after = (session.after(at) ?: return false) + session.pending
        return after.all { e -> e.engine?.steps.orEmpty().none { it.input != null } }
    }

    /** Asks the other player to approve taking the line back to just after [target], with [message]. */
    fun requestUndo(target: String, message: String = "") {
        remember("undo request asked to $target from ${where()}")
        send(GameAction.EngineRequestRewind(target, message))
    }

    /** The other player's request answered: [accept] takes the line back, else it closes. */
    fun answerRequest(accept: Boolean) {
        remember("undo request answered ${if (accept) "yes" else "no"}: ${asked(request)} at ${where()}")
        send(GameAction.EngineAnswerRewind(accept))
    }

    /** Takes the player's own open request back. */
    fun cancelRequest() {
        remember("undo request cancelled: ${asked(request)}")
        send(GameAction.EngineCancelRewind)
    }

    /** What the request dialog shows the player asked (§10): the board at the target when they have seen it, else at
     * their local head with [unseen] ("and actions you haven't seen yet"); never how many events go. */
    data class Preview(val board: GameModel, val unseen: Boolean, val message: String)

    fun requestPreview(): Preview? {
        val r = request?.takeIf { it.by != me.value } ?: return null
        if (session.drafting) return null
        val target = session.indexOf(r.target)
        val local = session.indexOf(session.localHead) ?: return null
        // HIS-005: a point before this device's line is before its local head too: its board from older history (none
        // until its page is in, [requestNeeds])
        if (target == null && older != null) {
            val old = (reach(r.target) as? Reach.State)?.model ?: return null
            return Preview(old, false, r.message)
        }
        val board = if (target != null && target <= local) when (val at = reach(r.target)) {
            is Reach.State -> at.model
            is Reach.Needs -> return null
            Reach.Unknown -> null
        } else null
        return Preview(board ?: session.stateAt(session.localHead) ?: return null, board == null, r.message)
    }

    /** The page of older history the open undo request's preview needs ([requestPreview]), or null: the next older page
     * while its target is before what this device holds, or the page ending at it when its state needs one. */
    fun requestNeeds(): String? {
        val r = request?.takeIf { it.by != me.value } ?: return null
        if (session.drafting) return null
        if (!session.knows(r.target) && older?.holds(r.target) != true) return olderThrough()
        return (reach(r.target) as? Reach.Needs)?.through
    }

    // ---- The server

    /**
     * An engine revision: its accepted [events] in order ([org.asyncttwiz.protocol.GameRevision.engineEvent] and
     * [EngineUpdate.events]) and [update]: a rewind's target, the local heads, the line counter, the open request.
     */
    fun revision(events: List<GameEvent>, update: EngineUpdate?) {
        remember("in ${events.joinToString(",") { it.id }}${update?.target?.let { " rewind $it" } ?: ""}${update?.confirmed?.let { " confirmed $it" } ?: ""}")
        if (needsReload) return
        if (update != null && update.lines > lines) lines = update.lines
        val target = update?.target
        if (target != null) {
            val by = update?.rewoundBy ?: events.firstOrNull()?.author
            // TRN-017: a rewind changes the line under a Resolve chain: it ends
            endChain("rewind")
            // SYS-025: the player's own undo this device showed already ([undoLocally]): nothing more to take back
            val own = by == me.value && localUndos.firstOrNull() == target
            if (own) localUndos.removeAt(0)
            if (own && events.isEmpty() && (localUndos.isNotEmpty() || session.head == target)) {
                remember("own undo to $target already shown")
                if (localUndos.isEmpty()) session.undone()
            }
            else when (val r = session.rewound(target, events)) {
                GameSession.Received.Reload -> { needsReload = true; remember("reload at rewind $target: ${followed()}") }
                is GameSession.Received.Changed -> {
                    remember("rewind to $target by $by: taken back ${r.takenBack.map { it.id.take(8) }}, " +
                        "discarded ${r.discarded.size}, own pending dropped ${r.dropped.size}")
                    remember("rewind done, now ${where()}, open ${openDecision()}")
                    if (r.takenBack.isNotEmpty() && by != me.value && by != null) {
                        note(Note.TakenBack(PlayerId(by), r.takenBack))
                        undoShown = true
                    }
                    endTurnTakenBack(target)
                }
                else -> {}
            }
            // The player's own undo lands where they act next: their device waits for them there (§10); what it took back
            // isn't made again
            holdAt = if (by == me.value) session.head else null
            if (by == me.value) forgetRemakes()
        } else for (event in events) {
            val r = session.received(event)
            // TRN-017 (owner 2026-11-07): the other player's real input ends a Resolve chain at once, so the chain never
            // presses over it: the device's next step sees it first (their same event taking the place of this device's
            // own, SYS-077, is that press, not a new input)
            if (event.author == opponent.value && event.assumed == null && event.engine?.steps.orEmpty().any { it.input != null } &&
                (r is GameSession.Received.Applied || r is GameSession.Received.Changed)) {
                opponentInputs++
                endChain("their input ${event.id}")
            }
            when (r) {
                GameSession.Received.Reload -> { needsReload = true; remember("reload at ${event.id} on ${event.parentId}: ${followed()}"); break }
                is GameSession.Received.Changed -> {
                    if (r.takenBack.isNotEmpty() && r.discarded.isNotEmpty())
                        note(Note.Branched(PlayerId(event.author), r.discarded.first(), r.takenBack, r.takenBack.filter(::realInput)))
                    endTurnTakenBack(event.parentId)
                }
                is GameSession.Received.Applied -> { endTurnDropped(r.dropped); lostOpening(r.dropped) || lostMoves(r.dropped) { it in byPlayer } }
                // Owner 2026-11-07 (SYS-077): the other player's event is the device's own pending one but for who made it
                // (their assumption of this answer, or their own answer this device assumed): it takes its place, the board
                // and anything open on it untouched; the server drops the pending event as its duplicate
                is GameSession.Received.Adopted -> {
                    remember("adopted ${event.id} for ${r.mine.id}")
                    byPlayer.remove(r.mine.id)
                    if (endTurnFirst == r.mine.id) endTurnFirst = event.id
                    adopted[r.mine.id] = event.id
                    if (adopted.size > MAX_ORIGINS) adopted.remove(adopted.keys.first())
                }
                else -> {}
            }
        }
        // The other player's mode as their latest own event recorded it, then as the server says it now (owner 2026-11-06)
        for (event in events) if (event.author == opponent.value && event.assumed == null) event.mode?.let { opponentMode = it }
        update?.modes?.let(::modes)
        if (update != null) {
            for ((account, head) in update.localHeads) heads[PlayerId(account)] = head
            reportAgain()
            if (request != update.rewindRequest) remember("undo request now: ${asked(update.rewindRequest)} (was ${asked(request)})")
            request = update.rewindRequest
            // The server confirmed where this device branched: it can't follow (the rules agree, so only after a fault)
            if (update.confirmed?.let(session::supersedes) == true) { needsReload = true; remember("reload: ${update.confirmed} superseded") }
        }
        events.forEach { byPlayer.remove(it.id) }
        checkDraft()
        if (needsReload) return
        followServer()
        trim()
        settleQueuedUndo()
    }

    /** The server refused [action] for [reason]. */
    fun refused(action: GameAction, reason: String) {
        remember("refused ${action::class.simpleName} ${(action as? GameAction.Submit)?.submission?.id ?: (action as? GameAction.EngineSeen)?.eventId
            ?: (action as? GameAction.EngineRewind)?.target ?: (action as? GameAction.EngineRequestRewind)?.target} $reason")
        when (action) {
            is GameAction.Submit -> {
                val submission = action.submission
                if (confirming.remove(submission.id)) return
                // SYS-025: an event the player's own undo took back already; the server's answer to the undo follows
                if (session.ghost(submission.id)) return
                val mine = byPlayer.remove(submission.id)
                val refused = session.refused(submission.id)
                if (refused == GameSession.Received.Reload) { needsReload = true; remember("reload at refused ${submission.id}: ${followed()}") }
                if (refused is GameSession.Received.Applied) endTurnDropped(refused.dropped)
                // "newLine": made on a point the other player's assumption about the player follows, without a new line
                // (sent before it reached the device): lost in the same race, answered at that assumption ([remake])
                val race = reason == "stale" || reason == "newLine"
                val again = race &&
                    ((refused is GameSession.Received.Applied && (lostOpening(refused.dropped) ||
                        lostMoves(refused.dropped) { it == submission.id && mine || it in byPlayer })) ||
                        submission.id in redeclared || submission.id in lost)
                redeclared.remove(submission.id)
                lost.remove(submission.id)
                if (submission.acts.isEmpty() && reason == "stale") retry += Retry(submission.table, submission.parentId)
                // The player's own action is never dropped silently (an opening declaration is made again instead); the
                // device's own passes and assumptions lost in the expected races are (I-D56 §8)
                else if ((!race || mine) && !again) note(Note.Refused(reason,
                    madeOn(submission.acts.mapNotNull { (it as? Act.Perform)?.takeIf { p -> !p.assumed }?.input })))
                checkDraft()
            }
            is GameAction.EngineRewind -> {
                // SYS-025: an undo this device showed already isn't the server's: the game loads again
                if (localUndos.remove(action.target)) { needsReload = true; localUndos.clear(); remember("reload: own undo to ${action.target} refused, $reason") }
                note(if (reason == "requestNeeded") Note.RequestNeeded(action.target) else Note.Refused(reason))
            }
            // The player's seat isn't before it there. A refusal changes nothing on the server and brings no revision, so
            // sending it again by itself would only repeat at the speed of the network ([reportAgain] says when it goes again)
            is GameAction.EngineSeen -> if (reported == action.eventId) reportRefused = action.eventId
            else -> note(Note.Refused(reason))
        }
        settleQueuedUndo()
    }

    /**
     * The player's own opening declarations among [dropped] (their own events the server didn't take: the other player's
     * declaration came first, both declaring at the same time, TRN-027), kept to be made again on it ([redeclare]); the
     * events after the first that isn't one are lost as any dropped events are. Whether any were kept.
     */
    private fun lostOpening(dropped: List<GameEvent>): Boolean {
        // The model is back at the point the first of them was made on
        if (dropped.isEmpty() || model.engine.turn.number != 0) return false
        val kept = dropped.asSequence().map { e ->
            val input = e.engine?.steps?.firstNotNullOfOrNull { it.input }
            e to if (e.author != me.value || e.assumed != null) null
            else when (input) {
                is Input.SetupAnswer -> input.answer.takeIf { input.player == me }
                is Input.Answer -> input.answer
                else -> null
            }
        }.takeWhile { it.second != null }.toList()
        if (kept.isNotEmpty() && redeclaring.isEmpty()) redeclareAfter = dropped.first().parentId
        redeclaring += kept.map { it.second!! }
        kept.forEach { redeclared += it.first.id }
        if (redeclared.size > MAX_ORIGINS) redeclared.clear()
        return kept.isNotEmpty()
    }

    /** A move of the player's own ([inputs], [table]) made on [madeOn] (the engine state shown when they made it) on
     * [parent], which the server didn't take: another event came first. [first]: made right on [parent], the point where
     * the server took the other player's event instead. */
    private class Lost(val event: String, val parent: String, val madeOn: GameState, val inputs: List<Input>, val table: List<TableChange>,
        val first: Boolean)

    /**
     * Owner, 2026-11-04 (SYS-011): the player's own moves among [dropped] (their events the server didn't take because the
     * other player's event came first; [mine]: made by the player's action, by event id) past the opening, each with the
     * state it was made on, kept to be made again on the other player's event when the same decision still waits for the
     * player there ([remake]). The device's own steps and assumptions among them are lost as before. Whether any were kept.
     */
    private fun lostMoves(dropped: List<GameEvent>, mine: (String) -> Boolean): Boolean {
        // The model is back at the point the first of them was made on
        val own = dropped.filter { e -> e.author == me.value && e.assumed == null && mine(e.id) && e.id in made }
        if (own.isEmpty()) return false
        val walk = model.copy()
        var kept = false
        for (e in dropped) {
            // Opening declarations are [lostOpening]'s
            if (e in own && walk.engine.turn.number != 0) {
                val (inputs, table) = made.getValue(e.id)
                remaking += Lost(e.id, dropped.first().parentId, walk.engine.deepCopy(), inputs, table, first = e === dropped.first())
                lost += e.id
                kept = true
            }
            try { walk.apply(e) } catch (_: IllegalStateException) { break }
        }
        if (lost.size > MAX_ORIGINS) lost.clear()
        return kept
    }

    /**
     * Makes the player's own moves the server took the other player's event before ([lostMoves]) again, once that event
     * is in and shown: each on the board now when it is still the same decision as where they made it
     * ([sameDecision]), as if just tapped; otherwise it isn't taken and the player is told (SYS-012's note), and the rest
     * (made on it) are dropped with it.
     */
    private fun remake(): Boolean {
        if (remaking.isEmpty() || needsReload || session.drafting || request != null || queuedUndo != null) return false
        val first = remaking.first()
        // Spec rule 6 (owner 2026-11-06): the event the server took instead is the other player's device assuming the
        // player's answer there (a press of Assume): the player is still at that decision, and the move is their answer to
        // it, made at the stop there ([lateAnswerAt]): the same answer confirms the assumption, anything else (a second
        // spell while holding priority in Control) branches (HIS-097)
        var answered = false
        val late = lateAnswerAt()
        if (late != null) {
            // Wait for playback to reach it
            if (!session.behind || session.buffered.first().id != late) return false
            remember("remake ${first.inputs.firstOrNull()}: answers the assumption $late")
            val again = remade(first)
            remaking.removeAt(0)
            if (again !is Acted.Sent && again !is Acted.Confirmed) {
                note(Note.Refused("stale"))
                if (remaking.isNotEmpty()) remember("remake dropped ${remaking.size} more")
                remaking.clear()
                return false
            }
            if (remaking.isEmpty()) return true
            answered = true
        }
        // Wait for the event the server took instead (a refusal can come before it) and for it to be shown; also while the
        // point the moves were made after is still one of the device's own events on its way (a refusal can come before
        // its confirmation too): compared on the board there, before the other player's event, they were dropped
        else if (session.head == first.parent || session.behind || session.pending.any { it.id == first.parent }) return false
        val moves = remaking.toList()
        remaking.clear()
        for ((n, move) in moves.withIndex()) {
            // Each later move is compared where the device's own steps after the one before leave it, as they did on the
            // line it was made on (the other player's passes it assumed, the player's own passes by their mode): a pass
            // To combat and the attack after it are both made again (e2e DamageDivisionTest)
            if (n > 0 || answered) steps()
            val again = if (!sameDecision(move.madeOn, model.engine)) null else {
                remember("remake ${move.inputs.firstOrNull()}")
                remade(move)
            }
            if (again !is Acted.Sent) {
                note(Note.Refused("stale"))
                if (moves.size > n + 1) remember("remake dropped ${moves.size - n - 1} more")
                return n > 0 || answered
            }
        }
        return true
    }

    /** [move] made again on the board now, as if just tapped: its table changes on the table now (a declaration ends the
     * staging as it stands here; the staging, an event of its own lost with it, may not be made again yet: then there is
     * nothing to end); null when one no longer applies. */
    private fun remade(move: Lost): Acted? {
        val table = rebase(model, move.table)?.filterNot { c ->
            c is TableChange.AttackDraft && c.before == c.after || c is TableChange.BlockDraft && c.before == c.after
        } ?: return null
        val rest = ArrayDeque(move.inputs.drop(1))
        (move.inputs.first() as? Input.PassPriority)?.let { pass ->
            if (Stops.stop(model.engine, pass.player)) ownMainPass?.invoke("a press lost in a race, made again")
        }
        return act(move.inputs.first(), table, { rest.removeFirstOrNull() }, null)
    }

    /**
     * Spec rule 6 (owner 2026-11-06): when the first of the player's moves lost in a race ([remaking]) was made right on the
     * point where the server took the other player's assumption about them instead (their device's automatic Assume), that
     * assumption: playback stops there whatever the player's mode ([stopAt]), and the move is made there as their answer
     * ([remake]). Null otherwise.
     */
    private fun lateAnswerAt(): String? {
        val first = remaking.firstOrNull()?.takeIf { it.first } ?: return null
        val next = session.after(first.parent)?.firstOrNull() ?: return null
        return next.id.takeIf { assumedAboutMe(next) != null && next.author == opponent.value }
    }

    /**
     * SYS-077: whether the main line's event right after [parent] is the other player's device's assumption (HIS-096) of
     * [inputs], the player's own one-input answer there (the same input, [Acceptance.same]): what the player meant to do
     * there is done.
     */
    fun assumedAlready(parent: String, inputs: List<Input>): Boolean {
        val input = inputs.singleOrNull() ?: return false
        val next = session.after(parent)?.firstOrNull() ?: return false
        val step = next.assumed ?: return false
        if (next.author != opponent.value || step.assumedBy != opponent || Acceptance.assumedAbout(step, next, players) != me) return false
        return step.input?.let { Acceptance.same(it, input) } == true
    }

    /**
     * HIS-109, SYS-012 (owner problem report E48RDE): whether this device's event right after [parent] on its line (its
     * pending events first, then the main line) is its own automatic press of [input], made by itself: the player's pass
     * by their mode, or its assumption of the other player's answer (never one a tap of theirs made, SYS-011's quick second
     * tap). A tap made on the board at [parent] whose input is that one is the same press, already made there: nothing
     * more to make or tell. Only the event right after [parent] counts, so such a tap never acts at a later point (the
     * next Stack item).
     */
    fun pressedByItself(parent: String, input: Input): Boolean {
        // [parent] itself may be an event of the device's whose place the other player's same event took (SYS-077)
        val at = adoptedAs(parent) ?: parent
        val next = session.pending.firstOrNull { it.parentId == at } ?: session.after(at)?.firstOrNull() ?: return false
        if (next.author != me.value) return false
        val step = next.engine?.steps?.firstOrNull { it.input != null } ?: return false
        val automatic = when (step.assumedBy) {
            null -> origins[next.id] == false
            me -> next.id !in assumeTaps
            else -> false
        }
        return automatic && step.input?.let { Acceptance.same(it, input) } == true
    }

    /**
     * HIS-109, SYS-012 (a full e2e run, 2026-11-08): [pressedByItself] for a tap on the board of the player's own action
     * shown at its performed point (SYS-025), standing on [parent]: whether the first thing the device folded into that
     * action's event by itself is [input]. That is the very press the board offered, already made there. Only for such a
     * board: on the board before the action, the same input meant something else (a pass instead of the action).
     */
    fun pressedInAction(parent: String, input: Input): Boolean {
        val at = adoptedAs(parent) ?: parent
        // The other player's table-only events may have slipped in under the action (they commute)
        val line = session.after(at)?.plus(session.pending) ?: session.pending.dropWhile { it.id != at }.drop(1)
        val action = line.firstOrNull { e -> e.engine?.steps.orEmpty().any { it.input != null } } ?: return false
        return foldedPress[action.id]?.let { Acceptance.same(it, input) } == true
    }

    /**
     * Makes the player's opening declarations the server took the other player's before ([lostOpening]) again: the other
     * player's opening declarations shown at once (both declare at the same time, nothing to play back), then each of
     * the player's own made on them as if just tapped (at a stop where the other player assumed it, it confirms it).
     * Waits for the event the server took instead (made again on the same event, it would only be refused again). When
     * the opening no longer asks it (or it is refused) it isn't taken, and the player is told (SYS-012's note).
     */
    private fun redeclare() {
        if (redeclaring.isEmpty() || needsReload || session.drafting || request != null) return
        if (session.head == redeclareAfter) return
        while (session.behind && !catchUp && model.engine.turn.number == 0 && stopAt(session.buffered.first()) == null) session.showNext()
        if (session.behind && status !is Status.Stopped) return
        val answers = redeclaring.toList()
        redeclaring.clear()
        for (answer in answers) {
            val input = openingInput(answer)
            remember("redeclare $answer${if (input == null) ": no longer asked" else ""}")
            val acted = input?.let { act(it) }
            if (acted !is Acted.Sent && acted !is Acted.Confirmed) { note(Note.Refused("stale")); return }
        }
    }

    /** The input declaring the player's own opening [answer] now: the answer to the open decision when it is theirs,
     * else their declaration ahead of it ([Input.SetupAnswer]); null outside the opening or when neither is listed. */
    fun openingInput(answer: arcanum.engine.decision.Answer): Input? {
        val s = model.engine
        if (s.turn.number != 0) return null
        val d = s.pending?.decision
        if (d != null && Engine.decider(s, d.player) == me) return Input.Answer(d.id, answer)
        return Input.SetupAnswer(me, answer).takeIf { Engine.options(s, me).any { it is Input.SetupAnswer && it.player == me } }
    }

    /** The server moved this player's local head ahead of the shown state (a rewind, or a confirmation it took): the
     * device shows up to it. */
    private fun followServer() {
        val server = heads[me] ?: return
        val at = session.indexOf(server) ?: return
        while (session.behind && (session.indexOf(session.localHead) ?: return) < at && !session.drafting) {
            session.showNext()
            catchUp = false
        }
    }

    /** Forgets events well before both local heads (the device keeps [KEPT_EVENTS] of them for undo and previews); once
     * older history was loaded ([older]) they join it instead, so what was reached stays reachable. */
    private fun trim() {
        val earliest = listOfNotNull(session.localHead, heads[opponent]).mapNotNull(session::indexOf).minOrNull() ?: return
        val cut = earliest - KEPT_EVENTS
        if (cut < 0) return
        val from = session.lineStart
        val moved = session.events.take(cut + 1)
        session.trim(session.events[cut].id)
        older?.trimmed(from, moved)
    }

    // ---- Older history (HIS-111, HIS-099: the timeline and Undo reach the game's first event)

    /** What [reach] finds for a main-line event. */
    sealed interface Reach {
        /** The state there (a copy: the shown state is unchanged). */
        data class State(val model: GameModel) : Reach
        /** Known, but its state needs the page of older history ending at [through] ([addPage]). */
        data class Needs(val through: String) : Reach
        /** Not an event of the main line this device holds (or a draft is open). */
        data object Unknown : Reach
    }

    /** The main line this device holds before its session's line ([OlderHistory.events]), oldest first. */
    val olderEvents: List<GameEvent> get() = older?.events.orEmpty()

    /** Whether [olderEvents] begin with the game's first event (nothing older to load). */
    val olderComplete: Boolean get() = older?.complete == true || session.lineStart == rootId

    /** The event the next older page of the main line ends at, or null when there is nothing older (or no [older]). */
    fun olderThrough(): String? = older?.through(session.lineStart, rootId)

    /** Takes in the server's [page] of older history ([OlderHistory.add]); false when it doesn't fit. */
    fun addPage(page: org.asyncttwiz.protocol.EngineHistoryPage): Boolean {
        val ok = older?.add(page, session.lineStart, rootId) == true
        remember("older page to ${page.through}: ${if (ok) "taken" else "refused"}")
        return ok
    }

    /**
     * The state at main-line event [id] for timeline mode and the Undo dialog: going back from the shown state when that
     * is exact ([GameSession.stateAt]), else from a page of older history ([OlderHistory.stateAt]: a checkpoint and at
     * most a page of events), never from the game's start.
     */
    fun reach(id: String): Reach {
        if (session.drafting) return Reach.Unknown
        if (session.knows(id)) session.stateAt(id)?.let { return Reach.State(it) }
        else if (older?.holds(id) != true) return Reach.Unknown
        val h = older ?: return Reach.Unknown
        if (id == rootId) return Reach.Unknown
        return h.stateAt(id)?.let { Reach.State(it) } ?: Reach.Needs(id)
    }

    /** The main-line events after [target] up to the head, from [olderEvents] on when it is one of them; null when
     * [target] isn't known. */
    fun lineAfter(target: String): List<GameEvent>? {
        session.after(target)?.let { return it }
        val held = olderEvents
        val at = held.indexOfFirst { it.id == target }
        if (at < 0) return null
        return held.subList(at + 1, held.size) + session.events
    }

    /** HIS-084: after the player's own undo to a point before this device's line (a reload brought it here), the device
     * passes nothing for them at the head until they act, as after any undo of theirs. */
    fun holdAtHead() { holdAt = session.head }

    /** What the device sends now (drained): its actions in order, and a local-head report when the shown head moved
     * (at once where playback stops or reaches the head, else at most every [SEEN_EVERY_MS]). */
    fun take(now: Long): List<GameAction> {
        this.now = now
        report(now, urgent = !session.behind || status is Status.Stopped)
        return outbox.toList().also { outbox.clear() }
    }

    /** Reports the shown head when it is ahead of the server's (throttled unless [urgent]). */
    private fun report(now: Long, urgent: Boolean) {
        if (holding) return
        val local = session.localHead
        val server = heads[me]
        // The server's head on one of the device's own events still pending is past anything shown
        val ahead = server == null || (session.pending.none { it.id == server } &&
            (session.indexOf(local) ?: -1) > (session.indexOf(server) ?: -1))
        if (undoShown) {
            undoShown = false
            if (local == server) {
                remember("out EngineSeen $local (undo shown)")
                outbox += GameAction.EngineSeen(local)
                reported = local; reportedAt = now
                return
            }
        }
        if (local != reported && local != server && ahead && (urgent || now - reportedAt >= SEEN_EVERY_MS)) {
            remember("out EngineSeen $local")
            outbox += GameAction.EngineSeen(local)
            reported = local; reportedAt = now
        }
    }

    /** A refused report goes again on the server's word only: a revision since says the player's seat is still before it
     * (its event has reached the server's line since). Only a revision does this and a refused report makes none, so it
     * can't feed itself. */
    private fun reportAgain() {
        val refused = reportRefused ?: return
        val seat = heads[me]?.let(session::indexOf) ?: return
        val at = session.indexOf(refused) ?: return
        if (seat < at && reported == refused) { reported = null; reportRefused = null }
    }

    /** What happened that the player is told once (drained). */
    fun notes(): List<Note> = notes.toList().also { notes.clear() }

    private fun send(action: GameAction) {
        // The server takes assumptions and result confirmations only from a local head it knows (I-D56 §6, §11): the
        // shown head's report goes first
        if (action is GameAction.Submit) { report(now, urgent = true); catchUp = false }
        remember("out ${action::class.simpleName}${(action as? GameAction.Submit)?.submission?.let { " ${it.id} on ${it.parentId}" }
            ?: (action as? GameAction.EngineRewind)?.let { " ${it.target}" } ?: (action as? GameAction.EngineRequestRewind)?.let { " ${it.target}" }
            ?: (action as? GameAction.EngineAnswerRewind)?.let { " ${it.accept}" } ?: ""}")
        outbox += action
    }

    private fun note(note: Note) {
        remember("note ${note::class.simpleName}" + when (note) {
            is Note.Refused -> " ${note.reason}"
            is Note.RequestNeeded -> " ${note.target}"
            is Note.TakenBack -> " by ${note.by.value}: ${note.takenBack.size} events"
            is Note.Branched -> " by ${note.by.value}: ${note.takenBack.size} events"
        })
        notes += note
    }

    private fun confirmable(): Boolean {
        if (session.behind || session.pending.isNotEmpty() || model.table.resultConfirmedBy != null) return false
        return heads.filterKeys { it != me }.values.all { h -> session.after(h)?.all { it.engine == null } == true }
    }

    private fun final(): Boolean = endedBy() != null

    /** The concession on the line, if any. */
    private fun concession(): PlayerId? =
        session.events.firstNotNullOfOrNull { e -> e.engine?.steps.orEmpty().firstNotNullOfOrNull { (it.input as? Input.Concede)?.player } }

    /** The event accepting the other player's suggestion (TRN-047), if any. */
    private fun agreement(): GameEvent? = session.events.lastOrNull { e -> e.engine?.steps.orEmpty().any { it.input is Input.AgreedResult } }

    /** TRN-065 (owner 2026-11-07): who made the result final by their own action: the conceder, the player who accepted a
     * suggestion (it ends the game at once, as a concession does), or the player who confirmed a result the rules gave. */
    private fun endedBy(): PlayerId? = concession() ?: agreement()?.let { PlayerId(it.author) } ?: model.table.resultConfirmedBy

    /** How the game reported [result] on [s] ended (LOB-126's endings). */
    private fun ending(s: GameState, result: GameResult): org.asyncttwiz.protocol.MatchEnding {
        concession()?.let { return org.asyncttwiz.protocol.MatchEnding.Conceded(it.value) }
        agreement()?.let { e -> return org.asyncttwiz.protocol.MatchEnding.Agreed(
            e.table.firstNotNullOfOrNull { (it as? TableChange.Suggestion)?.before?.note }.orEmpty()) }
        if (result is GameResult.Draw) return org.asyncttwiz.protocol.MatchEnding.Draw(
            loop = s.players.any { it.lossReason == arcanum.engine.setup.LossReason.Loop })
        return org.asyncttwiz.protocol.MatchEnding.ByRules
    }

    // ---- Reproduction (I-D43)

    /** Where the device's line stands, for the record of a reload. */
    private fun followed() = "head ${session.head}, tip ${session.tip}, pending ${session.pending.map { it.id }}, behind ${session.behind}"

    /** The last [count] entries of the device's record (what it sent, received and did), for the app log. */
    fun recentRecord(count: Int): List<String> = record.takeLast(count)

    private fun remember(entry: String) {
        record.addLast(entry.take(MAX_ENTRY))
        while (record.size > MAX_RECORD) record.removeAt(0)
    }

    /** The device's reproduction bundle (I-D43) on the engine-state model, with [codec] (the game's definitions). */
    fun repro(codec: EngineJson): GameRepro {
        val base = if (session.drafting) null else session.stateAt(session.lineStart)
        return GameRepro(me = me.value, definitions = codec.encodeDefinitions(), lineStart = session.lineStart,
            base = base?.let(codec::encodeModel), events = session.events.map(codec::encodeEvent),
            pending = session.pending.map(codec::encodeEvent), localHead = session.localHead,
            localHeads = heads.mapKeys { it.key.value }, lines = lines, settings = settings, log = record.toList())
    }

    companion object {
        /** Local-head reports while playback runs go out at most this often. */
        const val SEEN_EVERY_MS = 2_000L
        /** The refusal of an input that would show the player hidden cards while their line is tentative (§5). */
        const val REVEALS_HIDDEN = Authority.REVEALS_HIDDEN
        const val REWIND_REQUESTED = "rewindRequested"
        /** The note of a draft the main line dropped under it (a branch, a rewind, another event at the head). */
        const val DRAFT_DROPPED = "draftDropped"

        /** The object an action of [inputs] was made on, by its first input: the card cast or played, the source
         * activated; null for anything made on the main button (a pass, a declaration, an answer). */
        fun madeOn(inputs: List<Input>): ObjectId? = when (val first = inputs.firstOrNull()) {
            is Input.Cast -> first.card
            is Input.PlayLand -> first.card
            is Input.Activate -> first.source
            else -> null
        }
        /** Events a device keeps before the earlier local head (undo targets and request previews further back come
         * from the server). */
        const val KEPT_EVENTS = 64
        private const val MAX_ORIGINS = 256
        private const val MAX_AUTOMATIC_EVENTS = 200
        private const val MAX_FOLDED = 500
        /** How many entries the device's record keeps (the bundle's [GameRepro.log]; a report's bound drops its oldest first). */
        const val MAX_RECORD = 4000
        private const val MAX_ENTRY = 300
        private const val MAX_PROBES = 64

        /**
         * SYS-011 (owner, 2026-11-04): whether an own action tapped on the board [seen] is still the same decision on
         * [now], the board the device has moved on to (the other player's event arrived, an assumption was confirmed),
         * so it is made there instead of refused. The same when nothing the game state holds differs: the same input
         * count (no input in between), turn and step, priority (its holder and the passes in a row), open decision (its
         * id names what is asked, of whom, after how many inputs) and its options, Stack, combat, every object (zones,
         * what is known of hidden cards, targets) and player; only what no decision depends on may differ (stable
         * decision identity, owner 2026-11-05): the record of what happened ([GameState.log]), the timestamp counter and
         * the order of the libraries' unknown parts (a new line's reshuffle, §9; the places a player knows are compared).
         * Any other change (a new Stack object, another step, a chosen target gone, a hidden card shown) is a different
         * decision: the player never saw that point.
         */
        fun sameDecision(seen: GameState, now: GameState): Boolean = seen === now || decisionPoint(seen) == decisionPoint(now)

        /**
         * HIS-096, TRN-070 (owner 2026-11-06): whether the Assume [setting] of [me] assumes [holder]'s pass on [s] (their
         * priority, never at their own main phase), defined from the response modes, so it changes with them: Never at no
         * point (as Control stops everywhere); Normal wherever Normal mode would pass ([Stops.passes]), and at their pause
         * at [me]'s end step and right after [me]'s attack (nothing of [me]'s follows before their next real answer, so it
         * never takes anything back); When they have no mana as Normal and also while [noMana]; Always at every point.
         * Whatever the setting, the device also assumes a pass [holder]'s own response mode would make ([GameDevice]); the
         * setting counts only at points [me] began ([pointOwner], owner 2026-11-07).
         */
        fun settingAssumes(setting: AssumeResolves, s: GameState, holder: PlayerId, me: PlayerId, noMana: () -> Boolean): Boolean {
            fun normal() = Stops.passes(ResponseMode.Normal, s, holder) { false } || (s.stack.isEmpty() && s.turn.active == me &&
                (s.turn.step == TurnStep.End || s.turn.step == TurnStep.DeclareAttackers))
            return when (setting) {
                AssumeResolves.Never -> false
                AssumeResolves.Normal -> normal()
                AssumeResolves.WhenNoMana -> normal() || noMana()
                AssumeResolves.Always -> true
            }
        }

        /**
         * HIS-096 (owner 2026-11-07): who began the point at [state], the end of [line] (the main line's events up to it,
         * oldest first): the player who made the last real input since the step began. A real input is a cast, a land
         * played, an activation, a declaration of attackers or blockers (an empty one too) or an answer to a decision;
         * an assumed one (assumed blocks) is the input of the player who assumed it; never a pass, a random answer or an
         * engine-automatic answer one device made for the other player. Whose turn it is and who controls the top of the
         * Stack don't matter. With no real input since the step began (a beginning-of-step trigger), or none on [line]
         * (its older events trimmed), the active player.
         */
        fun pointOwner(line: List<GameEvent>, state: GameState): PlayerId {
            for (event in line.asReversed()) {
                val steps = event.engine?.steps ?: continue
                for (step in steps.asReversed()) {
                    // The step began after this step's input (its changes follow it): nothing later was a real input
                    if (step.changes.any { it is arcanum.engine.change.Change.Step && (it.before.step != it.after.step ||
                            it.before.number != it.after.number) }) return state.turn.active
                    val input = step.input ?: continue
                    realInputBy(event, step, input)?.let { return it }
                }
            }
            return state.turn.active
        }

        /** The player whose real input [input] (of [step] in [event]) is ([pointOwner]), or null when it is none. */
        private fun realInputBy(event: GameEvent, step: EngineStep, input: Input): PlayerId? {
            val player = when (input) {
                is Input.Cast -> input.player
                is Input.PlayLand -> input.player
                is Input.Activate -> input.player
                is Input.DeclareAttackers -> input.player
                is Input.DeclareBlockers -> input.player
                is Input.SetupAnswer -> input.player
                // The decision it answered, as the step's first change of the open decision records it
                is Input.Answer -> step.changes.firstNotNullOfOrNull { change ->
                    (change as? arcanum.engine.change.Change.PendingSet)?.before?.decision?.takeIf { it.id == input.decision }
                }?.takeIf { it !is Decision.Random }?.player
                else -> null
            } ?: return null
            // An assumed input (assumed blocks) began the point for the player who pressed Assume: the other player did
            // nothing there. Else only the author's own counts (an answer made by a device for the other player is
            // engine-automatic: it decides nothing)
            return step.assumedBy ?: player.takeIf { it.value == event.author }
        }

        /**
         * HIS-110: the reveals in [event] of a player other than [viewer] ([arcanum.engine.change.GameEvent.Revealed]: shown to
         * every player, CR 701.20), one per reveal in order, with what made each: the source the change carries (a spell's or
         * ability's instructions), else the card cast or the ability's source of the step's input (a cost).
         */
        fun revealsIn(event: GameEvent, viewer: PlayerId): List<Reveal> = event.engine?.steps.orEmpty().flatMap { step ->
            step.changes.mapNotNull { change ->
                val happened = change as? arcanum.engine.change.Change.Happened ?: return@mapNotNull null
                val revealed = happened.event as? arcanum.engine.change.GameEvent.Revealed ?: return@mapNotNull null
                if (revealed.player == viewer) return@mapNotNull null
                val cards = revealed.ids.mapNotNull { (it as? ObjectId.Card)?.card }.distinct()
                if (cards.isEmpty()) return@mapNotNull null
                val by = happened.by?.source ?: when (val input = step.input) {
                    is Input.Cast -> input.card
                    is Input.Activate -> input.source
                    else -> null
                }
                Reveal(event.id, revealed.player, cards, (by as? ObjectId.Card)?.card, happened.by?.name)
            }
        }

        /**
         * HIS-112: the plays [viewer]'s table shows of [event], in the order they were put on the Stack: every spell cast,
         * ability activated (mana abilities never use the Stack) and trigger of the other player's, and [viewer]'s own
         * triggers. Copies of spells are put there by something else, which was shown: they are not.
         */
        fun showsIn(event: GameEvent, viewer: PlayerId): List<PlayShow> = momentsIn(event, viewer).filterIsInstance<PlayShow>()

        /**
         * HIS-112, HIS-113: what holds [viewer]'s view in [event], in the order it happened: the plays shown ([showsIn]) and
         * the combat's moments: attackers declared, and each combat damage step that deals damage.
         */
        fun momentsIn(event: GameEvent, viewer: PlayerId): List<ViewMoment> {
            val moments = ArrayList<ViewMoment>()
            var damageStep = false
            for (change in event.engine?.changes.orEmpty()) when (change) {
                is arcanum.engine.change.Change.StackItemAdded -> showOf(event, change, viewer)?.let(moments::add)
                is arcanum.engine.change.Change.Combat ->
                    if (change.before?.attackers.isNullOrEmpty() && !change.after?.attackers.isNullOrEmpty()) moments += CombatHold(event.id, CombatMoment.Attackers)
                is arcanum.engine.change.Change.Step -> damageStep = change.after.step == arcanum.api.TurnStep.CombatDamage
                is arcanum.engine.change.Change.Damage -> if (damageStep && change.dealt.isNotEmpty()) {
                    moments += CombatHold(event.id, CombatMoment.Damage)
                    damageStep = false
                }
                else -> {}
            }
            return moments
        }

        private fun showOf(event: GameEvent, added: arcanum.engine.change.Change.StackItemAdded, viewer: PlayerId): PlayShow? {
            val kind = when {
                arcanum.engine.change.Meaning.Cast in added.meanings -> PlayKind.Cast
                arcanum.engine.change.Meaning.Activated in added.meanings -> PlayKind.Activated
                arcanum.engine.change.Meaning.Triggered in added.meanings -> PlayKind.Triggered
                else -> return null
            }
            val item = added.item
            if (item.controller == viewer && kind != PlayKind.Triggered) return null
            val card = (item.id as? ObjectId.Card)?.card ?: (item.source as? ObjectId.Card)?.card
            return PlayShow(event.id, item.controller, item.id, kind, card)
        }

        /** [state] without what never makes a decision another ([sameDecision]). */
        private fun decisionPoint(state: GameState): GameState = state.copy(log = mutableListOf(), timestampCounter = 0,
            players = state.players.map { it.copy(library = it.library.sortedBy(ObjectId::toString)) }.toMutableList())

        /**
         * SYS-011 (smoke 2026-11-04): whether End turn tapped on the board [seen] still ends the same turn on [now], the
         * board the device has moved on to: End turn may be made at any priority of the player's own turn, so the board
         * having moved on within it by [me]'s own device (its passes and steps after their earlier action, their own
         * triggers) is the same decision. Both [me]'s turn, the same turn, and every Stack object on [now] already on
         * [seen] or [me]'s own; another turn, or the other player's spell or ability they never saw, is not. Or
         * [sameDecision].
         */
        fun sameEndTurn(seen: GameState, now: GameState, me: PlayerId): Boolean = sameDecision(seen, now) ||
            (seen.turn.active == me && now.turn.active == me && seen.turn.number == now.turn.number &&
                now.stack.all { it in seen.stack || now.stackItems[it]?.controller == me })

        /** A device for [me] from the server's [snapshot] (and the codec of the game's definitions), at their local head
         * with the events past it buffered. With [takenBack] (HIS-106), when another player's branch took the player's
         * local head back ([EngineSnapshot.takenBack]), it is instead where they left it, on the discarded line, and the
         * main line's events after the branch point wait in [branchOnLoad] for the caller to receive as a revision (the
         * branch then shows as it does live: the take-back with its note, then the other player's new events). HIS-108:
         * likewise where the other player's undo took the player's local head back ([EngineSnapshot.undoneBy]): the undo
         * waits in [undoOnLoad], the events re-added after its point in [branchOnLoad]. */
        fun load(snapshot: EngineSnapshot, me: PlayerId, takenBack: Boolean = false, ids: () -> String): Pair<GameDevice, EngineJson> {
            val codec = EngineJson(EngineJson.decodeDefinitions(snapshot.definitions))
            val baseId = snapshot.baseId ?: snapshot.headId
            val base = codec.decodeModel(snapshot.base ?: snapshot.state)
            val events = snapshot.events.map(codec::decodeEvent)
            val heads = snapshot.localHeads.mapKeys { PlayerId(it.key) }
            val local = heads[me]?.takeIf { it == baseId || events.any { e -> e.id == it } } ?: snapshot.headId
            fun device(session: GameSession) =
                GameDevice(session, snapshot.settings ?: PlaySettings(), heads, snapshot.lines, snapshot.rewindRequest, ids)
                    .also { it.modes(snapshot.modes); it.older = OlderHistory(codec); it.rootId = snapshot.rootId }
            val gone = if (takenBack) snapshot.takenBack.map(codec::decodeEvent) else emptyList()
            if (gone.isNotEmpty() && gone.first().parentId == local) {
                val at = if (local == baseId) 0 else events.indexOfFirst { it.id == local } + 1
                return device(GameSession(base, baseId, events.take(at) + gone, gone.last().id, me)).also {
                    it.branchOnLoad = events.drop(at)
                    if (snapshot.undoneBy != null) it.undoOnLoad = EngineUpdate(target = local, localHeads = snapshot.localHeads,
                        lines = snapshot.lines, rewindRequest = snapshot.rewindRequest, rewoundBy = snapshot.undoneBy, modes = snapshot.modes)
                } to codec
            }
            return device(GameSession(base, baseId, events, local, me)) to codec
        }
    }
}
