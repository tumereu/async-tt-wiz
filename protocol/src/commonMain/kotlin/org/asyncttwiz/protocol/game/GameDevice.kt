package org.asyncttwiz.protocol.game

import arcanum.api.TurnStep
import arcanum.engine.Engine
import arcanum.engine.Host
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
import org.asyncttwiz.protocol.Faults
import org.asyncttwiz.protocol.GameAction
import org.asyncttwiz.protocol.replaceEach

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
    fun modes(modes: Map<String, ResponseMode>) {
        modes[opponent.value]?.takeIf { it != opponentMode }?.let { remember("their response mode now ${it::class.simpleName}"); opponentMode = it }
    }

    /** The other player's current "Assume resolves" setting (owner 2026-11-10), as the server last said ([assumes]): at a
     * point they began, where their device assumes this player's pass, the game doesn't wait for this player ([move]).
     * Null until known: no such point is judged by it. */
    var opponentAssume: AssumeResolves? = null
        private set

    /** Each player's current "Assume resolves" setting by account, as the server says it ([EngineSnapshot.assumes],
     * [EngineUpdate.assumes]): the other player's is [opponentAssume]. */
    fun assumes(assumes: Map<String, AssumeResolves>) {
        assumes[opponent.value]?.takeIf { it != opponentAssume }?.let { remember("their Assume resolves now ${it::class.simpleName}"); opponentAssume = it }
    }

    /** The game's assume reveal settings (LOB-135), as the caller last said ([assumeReveals]): the cards the information
     * rule lets through, in this player's assumptions and own actions alike. Until told, nothing ([AssumeReveals.NONE]). */
    var assumeReveals: org.asyncttwiz.protocol.AssumeReveals = org.asyncttwiz.protocol.AssumeReveals.NONE
        private set

    /** The game's assume reveal settings changed (its summary, set by the host): they apply to the points not yet made. */
    fun assumeReveals(value: org.asyncttwiz.protocol.AssumeReveals) {
        if (value == assumeReveals) return
        remember("game settings now: library ${value.library}, hands ${value.hands}, undo past passes needs a request ${value.undoPastPasses}, " +
            "reshuffle on undo ${value.reshuffleOnUndo}, on branch ${value.reshuffleOnBranch}")
        assumeReveals = value
        reveals.clear()
    }

    /** Each player's local head as the server last said. */
    val localHeads: Map<PlayerId, String> get() = heads.toMap()
    private val heads = localHeads.toMutableMap()

    /** The play's line counter: a branch starts line [lines] + 1. */
    var lines: Int = lines
        private set

    /** HIS-042 (owner 2026-11-26): the line counter's line is a branch's (HIS-194: the server took a re-made line back), not an
     * undo's: when it is due at the head, the game's "Reshuffle libraries on branch" decides its reshuffle, else "on undo". */
    var lineBranch: Boolean = false
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
    var catchUp: Boolean = session.buffered.any { it.author != session.me.value && !okOnly(it) }
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
     * The measuring clock (nanoseconds; SYS-030): when set, the time the information rule's
     * work takes (§5: the state kept from before an event and the checks of what it would show this player) adds up in
     * [informationNanos]. Null (the default, and on the server): nothing is measured.
     */
    var clock: (() -> Long)? = null

    /** TRN-057, STK-053 (reports P2XUER, 3WWPA8): told when the device itself makes a pass of its player's at their own main
     * phase, which only a press of theirs may do: a press lost in a race being made again (SYS-011), or anything else (a
     * fault). An End turn going on isn't told: its press is logged, and passing there is what it does. The host logs it. */
    var ownMainPass: ((why: String) -> Unit)? = null

    /** TRN-057, TRN-168 (problem report UJP7DE): told when an End turn begins, ends before its turn does (the other player's action:
     * a spell, an ability, a land or an attack; or taken back by an undo or a branch) and when a change of the line leaves it going on, so the app log says
     * why the device passed (or stopped passing) the player's priority. The host logs it. */
    var endTurnSaid: ((line: String) -> Unit)? = null

    /** SYS-063 (problem report DH9MW5): told whether Quick answers a decision by itself (its player's, or the other player's
     * on Quick) and why, once per judgement. The host logs it. */
    var quickAnswers: ((line: String) -> Unit)? = null

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
    /** TRN-168: the turn of the End turn the other player's action ended, and that action's event: at the head the End turn
     * is over at once, but playback behind it still passes the player's priority at the points before it ([endTurnPasses]). */
    private var endTurnShown: Pair<Int, String>? = null
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
        /** The game's [result] (from the shown state): [confirmable] while this player may press OK on it (TRN-065: from
         * the moment it shows, however far the other player has seen); [final] once both OKs stand, or after a concession or
         * an accepted suggestion. [ending]: how it ended; [endedBy]: the player whose own action made it final (the conceder,
         * the player who accepted), who has seen it already (they go on without a second OK); [ownOk]: this player's OK
         * stands, and may be taken back ([takeBackResult]) until it is final. */
        data class Over(val result: GameResult, val confirmable: Boolean, val final: Boolean,
            val ending: org.asyncttwiz.protocol.MatchEnding = org.asyncttwiz.protocol.MatchEnding.ByRules,
            val endedBy: PlayerId? = null, val ownOk: Boolean = false) : Status
        /** Nothing to do (before the game starts). */
        data object Idle : Status
    }

    /** Why the device waits for the other player (the waiting bar, §14). */
    @kotlinx.serialization.Serializable sealed interface Wait {
        /** They may respond to [to], on top of the Stack (yours, or their own while they hold priority over it). */
        @kotlinx.serialization.Serializable @kotlinx.serialization.SerialName("respond") data class Respond(val to: ObjectId) : Wait
        /** They may act at their priority in [step] with nothing of yours on top of the Stack (a pause, such as after their
         * blocks, or any point where your Assume setting and their mode don't assume their pass, HIS-096). */
        @kotlinx.serialization.Serializable @kotlinx.serialization.SerialName("may-act") data class MayAct(val step: TurnStep) : Wait
        /** They are declaring blockers. */
        @kotlinx.serialization.Serializable @kotlinx.serialization.SerialName("blockers") data object Blockers : Wait
        /** They are deciding on a mulligan. */
        @kotlinx.serialization.Serializable @kotlinx.serialization.SerialName("keep") data object Keep : Wait
        /** They are choosing ([source]: the card it comes from, null when this player can't see it). */
        @kotlinx.serialization.Serializable @kotlinx.serialization.SerialName("choosing") data class Choosing(val source: ObjectId?) : Wait
        /** Their own main phase, or their declaration of attackers: their turn to act. */
        @kotlinx.serialization.Serializable @kotlinx.serialization.SerialName("their-turn") data object TheirTurn : Wait
        /** Going on would show you hidden cards, or a random result (the information rule, §5). */
        @kotlinx.serialization.Serializable @kotlinx.serialization.SerialName("hidden-cards") data object HiddenCards : Wait
        /** Your own events wait for the server. */
        @kotlinx.serialization.Serializable @kotlinx.serialization.SerialName("sending") data object Sending : Wait
        /** An undo request is open. */
        @kotlinx.serialization.Serializable @kotlinx.serialization.SerialName("request") data object Request : Wait
        /** A result suggestion waits for an answer (TRN-049). */
        @kotlinx.serialization.Serializable @kotlinx.serialization.SerialName("suggestion") data object Suggestion : Wait
    }

    /** The waiting bar's Assume buttons (§4). */
    @kotlinx.serialization.Serializable sealed interface Offer {
        @kotlinx.serialization.Serializable @kotlinx.serialization.SerialName("resolves") data object AssumeResolves : Offer
        @kotlinx.serialization.Serializable @kotlinx.serialization.SerialName("blockers") data object AssumeBlockers : Offer
        /** HIS-096, TRN-070: the other player's opening keep (with its stand-in bottom choice when one is owed), only by a press. */
        @kotlinx.serialization.Serializable @kotlinx.serialization.SerialName("keep") data object AssumeKeep : Offer
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
        /** HIS-115: [by]'s opening move (a mulligan when [mulligan]) re-applied your line, and it all came out the same: your
         * turn stands. */
        data class Stands(val by: PlayerId, val mulligan: Boolean) : Note
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
        Engine.result(s)?.let { return Status.Over(it, confirmable(), final(), ending(s, it), endedBy(), me in model.table.resultOks) }
        if (request != null) return Status.Waiting(Wait.Request)
        if (locked) return Status.Waiting(Wait.Suggestion)
        // Worked out once per point (the searches below don't run again while nothing they read changed)
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
     * What the device's decisions at the head read besides the engine state at the tip (the
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

    /** Tests and measurements: how many times the status at the head or the device's own step
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
     * request or result suggestion; else, at a result, this player until their OK stands on it, then the other player
     * while theirs doesn't (TRN-065); else this player when the head is their decision or opening declaration, the other
     * player when it is theirs. So a decision stop of this player's behind the head, while the other player plays on
     * assumptions about them, is the other player's move. Null when the game waits for nobody (both OKs stand on a result,
     * or before the start). A draft of the player's own at the head is their move.
     */
    val move: PlayerId? get() = moveAndWhy().first

    /** [move] with the rule that decided it, in words (owner 2026-11-10: every change of the move colour is logged with
     * its reason). */
    fun moveAndWhy(): Pair<PlayerId?, String> {
        val atHead = moveAtHead()
        if (atHead == null) {
            val (raw, waits) = headWaits()
            // No settled head seen yet (the game just opened mid-chain): the head's own
            val kept = settledMove ?: return raw to "$waits (nothing settled yet)"
            return kept.move to "$waits, not settled (made by itself next): kept ${kept.move?.value?.take(8)}"
        }
        settledMove = Kept(atHead.first)
        return atHead
    }

    /** The move last worked out on a settled head ([moveAtHead]), kept while the head isn't settled. */
    private class Kept(val move: PlayerId?)
    private var settledMove: Kept? = null

    /**
     * [move] on the head with why, or null when the head isn't settled: the game doesn't really wait there, the next step
     * is made by itself (owner playtest 2026-11-10: the move colour flipped back and forth). The other player's device
     * sends what it makes one event at a time, so while those events reach this device (playback behind them, or a board
     * published before this device's own steps followed its action) the head passes through points where the player who
     * holds priority is passed for by their own mode, or assumed about; the move there is neither's.
     */
    private fun moveAtHead(): Pair<PlayerId?, String>? {
        request?.let { return (if (it.by == me.value) opponent else me) to "undo request by ${it.by.take(8)}" }
        // The player's own flow at the head (a cast, a declaration being made)
        if (session.drafting && !session.behind) return me to "own draft at the head"
        val facts = headFacts() ?: return me to "no head facts"
        facts.suggestedBy?.let { return (if (it == me) opponent else me) to "result suggestion by ${it.value.take(8)}" }
        if (facts.result) return (if (me !in facts.resultOks) me else opponent.takeIf { it !in facts.resultOks }) to "result, OKs ${facts.resultOks.map { it.value.take(8) }}"
        if (session.drafting) return headWaits(facts)
        return if (settledAtHead()) headWaits(facts) else null
    }

    private fun headWaits(facts: HeadFacts? = headFacts()): Pair<PlayerId?, String> {
        facts ?: return me to "no head facts"
        val waits = "head ${facts.head.take(8)} waits for ${facts.waiting.map { it.value.take(8) }}"
        return (if (me in facts.waiting) me else opponent.takeIf { it in facts.waiting }) to waits
    }

    /** [settledAtHead] worked out last: at the shown point ([PointKey]) or, while playback is behind, at the head. */
    private var settledShown: Pair<PointKey, Boolean>? = null
    private data class HeadKey(val head: String, val settings: PlaySettings, val opponentMode: ResponseMode, val endTurn: Int?,
        val chain: Boolean, val holdAt: String?, val opponentAssume: AssumeResolves?)
    private var settledHead: Pair<HeadKey, Boolean>? = null

    /**
     * Owner playtest 2026-11-10: whether the game really waits at the main line's head as this device knows it (its own
     * pending events included): nothing there is made by itself next, neither the player's own pass or answer by their
     * response mode (or an End turn, a Resolve chain) nor an automatic answer or an assumption of the other player's
     * (their mode passes, this player's Assume setting, a forced pass), nor, while playback is behind, the other player's
     * device's assumption of this player's pass by its own Assume setting ([assumedByThem]). Whether this device could make
     * that step now (playback behind, a play shown) doesn't matter. Worked out once per point.
     */
    private fun settledAtHead(): Boolean {
        if (!session.behind) {
            val key = pointKey()
            if (key.same(idleAt)) return true
            settledShown?.takeIf { key.same(it.first) }?.let { return it.second }
            return !makesItself(model, session.tip).also { settledShown = key to !it }
        }
        val key = HeadKey(session.head, settings, opponentMode, endTurn, chain, holdAt, opponentAssume)
        settledHead?.takeIf { it.first == key }?.let { return it.second }
        val settled = runCatching { !makesItself(session.headModel(), session.head) }.getOrDefault(true)
        settledHead = key to settled
        return settled
    }

    /**
     * Owner playtest 2026-11-10: whether this player's priority on [s] (state [m], the main line's head while playback is
     * behind it), where their own mode stops, is a point the other player began at which the other player's device
     * assumes their pass by its own "Assume resolves" setting ([opponentAssume], HIS-096: there their setting counts, never
     * at this player's own main phase). That device sends its events one at a time, so its assumption is on its way: the
     * game doesn't wait for this player there. Only while playback is behind the head: at the head this player's device
     * stops there for them, and the move follows it (the other device may still wait, its Assume greyed, TRN-071).
     */
    private fun assumedByThem(s: GameState, m: GameModel): Boolean {
        val setting = opponentAssume ?: return false
        if (s.pending != null || s.priority.holder != me || Stops.stop(s, me)) return false
        if (pointOwner(session.events + session.pending, s) != opponent) return false
        return settingAssumes(setting, s, me, opponent) { noMana(s, me, m) }
    }

    /** Whether the device's own step at [at] (state [m]) would be made by itself ([decideStep] without making it). */
    private fun makesItself(m: GameModel, at: String): Boolean {
        val s = m.engine
        if (Engine.result(s) != null) return false
        val who = Attention.of(s) ?: return false
        if (who == me || NextStop.declares(s, me)) {
            if (at == session.tip && held()) return false
            val input = own(s, m, at) ?: return m !== model && assumedByThem(s, m)
            return reveals["$at:$input"] != true
        }
        return when (val point = point(s, m)) {
            is Point.Automatic, is Point.Assume -> true
            is Point.Wait -> chain && point.offer == Offer.AssumeResolves && point.input != null
        }
    }

    /** What the main line's head says about whose move it is ([move]): its open result suggestion's author, whether it
     * holds the game's result, the players whose decision or opening declaration it is. */
    private class HeadFacts(val head: String, val suggestedBy: PlayerId?, val result: Boolean, val waiting: Set<PlayerId>,
        val resultOks: Set<PlayerId>)
    private var behindFacts: HeadFacts? = null

    /** [HeadFacts] of the head: of the shown state at the head, else of the head's state worked out once per head (the
     * last worked out while a draft behind the head is open). */
    private fun headFacts(): HeadFacts? {
        fun of(head: String, m: GameModel) = HeadFacts(head, m.table.suggestion?.by, Engine.result(m.engine) != null,
            Attention.of(m.engine).let { who -> players.filter { it == who || NextStop.declares(m.engine, it) }.toSet() }, m.table.resultOks)
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
    @kotlinx.serialization.Serializable data class Indicator(val assumed: Int, val since: Int)

    fun indicator(): Indicator? {
        val after = (heads[opponent]?.let(session::after) ?: session.events) + session.pending
        val first = after.indexOfFirst { it.assumed?.assumedBy == me }
        if (first < 0) return null
        return Indicator(after.count { it.assumed?.assumedBy == me }, after.size - first - 1)
    }

    /** Where the other player is ([OpponentAt.turn], [OpponentAt.step], whose turn it is) when they are behind this player. */
    @kotlinx.serialization.Serializable data class OpponentAt(val turn: Int, val step: arcanum.api.TurnStep, val active: PlayerId)

    private var opponentAtKey: Pair<String, String>? = null
    private var opponentAtCached: OpponentAt? = null

    /**
     * TRN-066 (owner, 2026-11-09): where the other player is when they are behind this player, who went ahead on assumptions
     * about them not yet confirmed: the board at their local head as the server last said; null when they aren't behind
     * (or their head isn't on this line, or too far back to reach exactly). Worked out once per pair of heads.
     */
    fun opponentAt(): OpponentAt? {
        val head = heads[opponent] ?: return null
        if (indicator() == null) return null
        val at = session.indexOf(head) ?: return null
        val local = session.indexOf(session.localHead) ?: return null
        if (at >= local) return null
        val key = head to session.localHead
        if (key != opponentAtKey) {
            opponentAtKey = key
            // Still in the opening (before turn 1: "turn 0") says nothing of where they are
            opponentAtCached = runCatching { session.stateAt(head) }.getOrNull()?.engine?.turn?.takeIf { it.number >= 1 }
                ?.let { OpponentAt(it.number, it.step, it.active) }
            remember("the other player behind, at ${head.take(8)}: $opponentAtCached")
        }
        return opponentAtCached
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
    fun play() {
        if (catchUp) remember("catch-up: play, ${session.buffered.size} to show from ${session.localHead.take(8)}, ${viewState()}")
        catchUp = false
    }

    /** HIS-006: the player entered timeline mode while playback ran: it waits paused ([Status.CatchUp]) until [play]. */
    fun holdPlayback() {
        if (session.behind && !opening && status is Status.Playing) { catchUp = true; remember("catch-up: held (timeline), ${viewState()}") }
    }

    /**
     * HIS-035 (owner playtest 2026-11-05): the player left the app. Playback pauses at the shown point at once, and what
     * arrives meanwhile waits: coming back with the other player's events past the local head starts paused
     * ([Status.CatchUp]) until [play], as a fresh load does; [returned] ends it when there is nothing to catch up on.
     */
    fun away() { catchUp = true; remember("catch-up: away, ${viewState()}") }

    /** The player is back and the device has everything the server had: with nothing of the other player's past the
     * local head the game is live again (HIS-006); else it stays paused for Play. */
    fun returned() {
        if (session.buffered.none { it.author != session.me.value && !okOnly(it) }) catchUp = false
        remember("catch-up: back, ${if (catchUp) "paused for Play" else "live"}, ${viewState()}")
    }

    /**
     * Problem report QVERHX: where playback and the view stand, for the record and the problem report: the status (what
     * the main button can hold follows from it: Your move, Drafting and a stop show it; playback, the catch-up's pause
     * and a wait don't), what holds the view (an open reveal, a play shown, a combat's hold), the catch-up pause and a
     * pause of the player's, and how many events wait past the local head.
     */
    fun viewState(): String {
        val s = status
        val what = when (s) {
            is Status.Playing -> "playing, next ${s.next.id.take(8)}"
            is Status.Stopped -> "stopped before ${s.event.id.take(8)} (assumed ${s.assumed::class.simpleName}${if (s.paused) ", paused" else ""})"
            is Status.Waiting -> "waiting: ${s.reason}${s.offer?.let { ", offer $it" } ?: ""}"
            else -> s::class.simpleName ?: "?"
        }
        syncReveals()
        val holds = revealsOpen.map { "reveal ${it.event.take(8)}" } + showsOpen.map { m -> when (m) {
            is PlayShow -> "play ${m.kind::class.simpleName} ${m.event.take(8)}"
            is CombatHold -> "combat ${m.kind::class.simpleName} ${m.event.take(8)}"
        } }
        return "status $what, main button ${if (s == Status.YourMove || s == Status.Drafting || s is Status.Stopped) "shown" else "none"}, " +
            "move ${move?.value?.take(8)}, holds $holds, catch-up $catchUp, playback paused by a tap $pausing, buffered ${session.buffered.size}, ${where()}"
    }

    /** Why [step] last showed nothing while events waited (the record says each reason once, until playback moves on). */
    private var heldWhy: String? = null

    private fun playbackHeld(why: String?) {
        if (why == heldWhy) return
        heldWhy = why
        if (why != null) remember("playback holds before ${session.buffered.firstOrNull()?.id?.take(8)}: $why")
    }

    /** TRN-065: whether [event] only says a player pressed OK on the result or took it back: nothing to watch. */
    private fun okOnly(event: GameEvent): Boolean = event.engine == null && event.table.isNotEmpty() && event.table.all { it is TableChange.ResultOk }

    /** TRN-065: the other player's OK on the result, or its take-back, next in line: shown at once, whatever else waits (no
     * playback, no pause, no catch-up: the result's dialog stays as it is, with their word in it). */
    private fun showResultOks() {
        var shown = false
        while (!needsReload && !holding && !session.drafting && session.pending.isEmpty() && session.behind && okOnly(session.buffered.first())) {
            val event = session.showNext()
            remember("result OK of ${event.author} shown: ${event.table}")
            shown = true
        }
        if (shown && !session.behind) { pausing = false; catchUp = false }
    }

    /**
     * TRN-065 (owner, 2026-11-09): the other player's concession or accepted suggestion ended the game while this player
     * stands at the table, and they stay there until their own OK. Nothing more can be made on the game, so what they
     * hadn't watched yet shows at once (no stops: they could no longer answer one), the result's dialog next; no play shown
     * stands in front of it. A reveal panel does (HIS-110: only the player's own OK closes it), one per reveal, in order.
     * Whether the device could do it (else the game goes to its lobby at once).
     */
    fun showToTheEnd(): Boolean {
        if (needsReload || holding) return false
        session.abandon()
        checkDraft()
        catchUp = false
        pausing = false
        endChain("game over")
        if (session.pending.isNotEmpty()) return false
        while (session.behind) session.showNext()
        syncReveals()
        showsOpen.clear()
        remember("to the end: ${where()}, reveals open ${revealsOpen.size}")
        return Engine.result(model.engine) != null
    }

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
        if (!session.behind) { playbackHeld(null); return null }
        if (needsReload || holding || (catchUp && !opening) || session.drafting) {
            playbackHeld(when { needsReload -> "reload"; holding -> "take-back held"; session.drafting -> "own draft open"; else -> "catch-up pause" })
            return null
        }
        if (viewHolds()) { playbackHeld("view held: ${viewState()}"); return null }
        stopAt(session.buffered.first())?.let { playbackHeld("decision stop at the assumed $it (mode ${settings.mode}${if (pausing) ", paused" else ""}), main button shown"); return null }
        playbackHeld(null)
        var shown: GameEvent
        do shown = session.showNext() while (opening && session.behind && !viewHolds() && stopAt(session.buffered.first()) == null)
        showResultOks()
        if (!session.behind) { pausing = false; catchUp = false }
        return shown
    }

    // ---- Reveal stops (HIS-110)

    /**
     * HIS-110 (owner 2026-11-09): cards revealed in the shown event [event], by the other player or by this one ([by]):
     * [cards] in the order revealed, and what revealed them, [source] (the card of the spell or ability, null when none is
     * known) and its [sourceName] then. Each reveal is one panel; while one is open ([reveal]) playback holds the events
     * after it, as at a playback stop, until the player dismisses it ([dismissReveal]). The player's own choice among cards
     * they already see isn't one for them ([ownRevealSeen]). Personal: nothing is sent, the other player's phone never
     * waits, and the device still makes its own steps at the head.
     */
    @kotlinx.serialization.Serializable data class Reveal(val event: String, val by: PlayerId, val cards: List<arcanum.engine.state.CardId>,
        val source: arcanum.engine.state.CardId?, val sourceName: String?,
        /** HIS-114: not a reveal but [cards] leaving a public pile for a hidden zone, moved by [by]; null for a reveal. */
        val left: LeftPile? = null,
        /** HIS-233: not a reveal but [cards] exiled face up from [LibraryExile.owner]'s library by [by]; null otherwise. */
        val exiled: LibraryExile? = null,
        /** STK-181 (owner 2026-11-25): the spell or ability that made it, as it was on the Stack, for its text under the title;
         * null when none did (a cost) or it isn't known. */
        val item: arcanum.engine.state.StackItem? = null)

    /**
     * HIS-233 (owner 2026-11-19, N18, problem report MDLPM7): cards exiled face up from [owner]'s library ("exile the top N",
     * "exile cards from the top until …"), shown to every player as a reveal is ([Reveal.exiled]), the mover too: they leave a
     * hidden zone for a public one, so nobody saw them before.
     */
    @kotlinx.serialization.Serializable data class LibraryExile(val owner: PlayerId)

    /**
     * HIS-114 (owner playtest 2026-11-09): cards that left [owner]'s public pile [from] (a graveyard, or exile face up) for
     * [to], a hidden zone (a hand, a library), shown to the other player as a reveal is ([Reveal.left]): only the cards
     * that left the pile, nothing else of the zone they went to.
     */
    @kotlinx.serialization.Serializable data class LeftPile(val from: arcanum.api.Zone, val to: arcanum.api.Zone, val owner: PlayerId)

    /** HIS-110: the reveal panel open now (the oldest not dismissed), or null. */
    val reveal: Reveal? get() { syncReveals(); return revealsOpen.firstOrNull() }

    /** HIS-110: every reveal not dismissed yet, oldest first (one panel each, in order). */
    val openReveals: List<Reveal> get() { syncReveals(); return revealsOpen.toList() }

    /** HIS-110: the player dismissed the open reveal panel (the one of [event] when given, else the oldest; when the other
     * player's same event took the place of the player's own meanwhile, SYS-077, that one): the next one opens, or playback
     * goes on. True when it was open. */
    fun dismissReveal(event: String? = null): Boolean {
        syncReveals()
        val ids = listOfNotNull(event, event?.let(::adoptedAs))
        val index = if (event == null) 0.takeIf { revealsOpen.isNotEmpty() } else revealsOpen.indexOfFirst { it.event in ids }.takeIf { it >= 0 }
        if (index == null) { remember("reveal dismissal in ${event ?: "the oldest"}: none open (${revealsOpen.size})"); return false }
        remember("reveal dismissed in ${revealsOpen.removeAt(index).event}")
        return true
    }

    /**
     * HIS-110 (full e2e run 2026-11-09): the player's own event [mine] gave its place to the other player's same event [theirs]
     * ([GameSession.Received.Adopted]: the same board): what it showed is what [theirs] shows, so what the player saw of it
     * and what they dismissed stands: nothing asks them twice, and what is open stays open, now of [theirs].
     */
    private fun adoptMoments(mine: String, theirs: String) {
        if (!revealsSeen.remove(mine)) return
        revealsSeen += theirs
        revealsOpen.replaceEach { if (it.event == mine) it.copy(event = theirs) else it }
        showsOpen.replaceEach { moment -> if (moment.event != mine) moment else when (moment) {
            is PlayShow -> moment.copy(event = theirs)
            is CombatHold -> moment.copy(event = theirs)
        } }
        if (revealsTip == mine) revealsTip = theirs
    }

    /**
     * HIS-110: this device replaces [old] while the player stays in the game (the game loaded again): a panel is closed only
     * by their OK or by its reveal being taken back, so what they dismissed stays dismissed and what is open stays open, when
     * its event is on this line (past the local head, it opens as playback reaches it).
     */
    /**
     * HIS-006 (merge gate 2026-11-20, CoreLoopTest: Alice's turn played back a second time): the game loaded again while the
     * table was live, at the server's local head of this player, which can lag behind what [old] had already shown (its
     * reports refused, or not sent yet, when the device's own events lost a race). The main-line events up to [old]'s local
     * head show again at once, opening no reveal, play shown or combat hold (they were shown before), so they don't play
     * back a second time. Returns how many.
     */
    fun showAlreadySeen(old: GameDevice): Int {
        val upTo = session.indexOf(old.session.localHead) ?: return 0
        var shown = 0
        while (session.behind && !session.drafting && session.pending.isEmpty() && (session.indexOf(session.localHead) ?: -1) < upTo) {
            session.showNext()
            shown++
        }
        if (shown == 0) return 0
        val local = session.indexOf(session.localHead) ?: -1
        session.events.take(local + 1).mapTo(revealsSeen) { it.id }
        revealsTip = session.tip
        if (session.buffered.none { it.author != session.me.value && !okOnly(it) }) catchUp = false
        remember("loaded again: $shown events this device had shown shown again at once, ${viewState()}")
        return shown
    }

    fun carryReveals(old: GameDevice) {
        old.syncReveals()
        syncReveals()
        val local = session.indexOf(session.localHead) ?: -1
        val shown = (session.events.take(local + 1) + session.pending).mapTo(HashSet()) { it.id }
        shown += session.lineStart
        revealsSeen += old.revealsSeen
        for (reveal in old.revealsOpen) {
            if (reveal.event in shown) { if (reveal !in revealsOpen) revealsOpen += reveal }
            else revealsSeen -= reveal.event
        }
        remember("loaded again: reveals still open ${revealsOpen.map { it.event }}")
    }

    private val revealsOpen = ArrayList<Reveal>()
    /** The shown events already looked at for reveals (those shown before the device was made count as looked at). */
    private val revealsSeen = HashSet<String>()
    /** The tip [syncReveals] last looked at: the shown line is the same while the tip is (each event names its parent). */
    private var revealsTip: String? = null

    init {
        // A game opened (or loaded again) opens no panel for what was shown before
        showResultOks()
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
     * refusal) closes, and the events newly shown since add the reveals and the plays shown in them, in order.
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
            // The event the session's line starts after is on the line too (a game loaded at it, HIS-110)
            ids += session.lineStart
            val gone = revealsOpen.filter { it.event !in ids }
            revealsOpen.removeAll(gone)
            gone.forEach { revealsSeen.remove(it.event); remember("reveal closed, taken back: ${it.event}") }
            val goneShows = showsOpen.filter { it.event !in ids }
            showsOpen.removeAll(goneShows)
            goneShows.forEach { revealsSeen.remove(it.event) }
            // HIS-112 (prod report 3LL6UZ, 2026-11-16: a play shown stayed in the middle after the board went past it): a play
            // shown whose entry has left the Stack on the board shown now (resolved, countered: what showed it moved on, say
            // on another device of the player's) is done; a special action (never on the Stack) once the line went on past its
            // event
            val left = showsOpen.filter { it is PlayShow && if (it.kind == PlayKind.Special) it.event != tip else it.entry !in model.engine.stack }
            if (left.isNotEmpty()) {
                showsOpen.removeAll(left)
                remember("plays shown closed, their entries left the Stack at ${tip?.take(8)}: ${left.map { it.event.take(8) }}, holds left ${showsOpen.size}")
            }
        }
        val fresh = ArrayList<GameEvent>()
        for (i in line.indices.reversed()) {
            val e = line[i]
            if (e.id in revealsSeen) break
            fresh += e
        }
        for (e in fresh.asReversed()) {
            revealsSeen += e.id
            for (r in revealsIn(e) { id -> stackItemOf(id, e, line) }) if (r.left != null) {
                // HIS-114: the other player's move out of a public pile into a zone hidden from this player
                val what = "${r.by.value.take(8)} moved ${r.cards.map { it.value }} from ${r.left.owner.value.take(8)}'s ${r.left.from} to ${r.left.to} by ${r.sourceName} in ${e.id}"
                if (r.by == me) remember("own pile move, nothing shown: $what")
                else if (r.left.to == arcanum.api.Zone.Hand && r.left.owner == me) remember("pile move into this player's own hand, nothing shown: $what")
                else { revealsOpen += r; remember("pile move open (left a public pile for a hidden zone): $what") }
            } else {
                val seenIn = if (r.by != me) null else ownRevealSeen(line, e, r)
                if (seenIn == null) {
                    revealsOpen += r
                    remember("reveal open: ${r.by.value.take(8)} ${if (r.exiled != null) "exiled from ${r.exiled.owner.value.take(8)}'s library" else "revealed"} " +
                        "${r.cards.map { it.value }} by ${r.sourceName} in ${e.id}")
                } else remember("own ${if (r.exiled != null) "library exile" else "reveal"} in ${e.id} shows nothing new, no panel: ${r.cards.map { it.value }} by " +
                    "${r.sourceName}, $seenIn")
            }
            if (showPlays) momentsIn(e, me).takeIf { it.isNotEmpty() }?.let { moments ->
                showsOpen += moments
                remember("view holds open in ${e.id.take(8)}: ${moments.map { m -> when (m) {
                    is CombatHold -> "combat ${m.kind::class.simpleName}"
                    is PlayShow -> if (m.kind == PlayKind.Special) "special action ${m.ability?.key} of ${m.entry}" else "play"
                } }}")
            }
        }
    }

    /**
     * STK-181 (owner 2026-11-25): the Stack item [id] that made a reveal in [event] on the shown [line], as it was: in [event]'s
     * changes (it resolved there), on the Stack at the head (it still resolves), else as the line put it there; null when none of
     * them has it (logged).
     */
    private fun stackItemOf(id: ObjectId, event: GameEvent, line: List<GameEvent>): arcanum.engine.state.StackItem? {
        stackItemIn(event, id)?.let { return it }
        model.engine.stackItems[id]?.let { return it }
        val at = line.indexOfFirst { it.id == event.id }.takeIf { it >= 0 } ?: line.size
        for (i in (at - 1) downTo 0) stackItemIn(line[i], id)?.let { return it }
        remember("reveal in ${event.id}: its Stack item $id isn't known, no text for it")
        return null
    }

    /**
     * HIS-110 (owner 2026-11-09), HIS-155: where the player's own reveal [reveal] in [event], on the shown [line], is no news to
     * them, so that it opens no panel of theirs: all of its cards they could see before [event] or were shown in a decision of
     * theirs in it (a search's choice, cards revealed and then chosen among, also the decision [event] leaves open); null when
     * it is news and stops them too. Owner 2026-11-21: a choice among cards revealed, looked at or exiled face up shows the
     * group's cards it can't choose with it ([Decision.ChooseObjects.others]), so those count as shown in it: one panel, the
     * choice's. Their own choice among cards they already see isn't a reveal, and nothing asks them twice.
     */
    private fun ownRevealSeen(line: List<GameEvent>, event: GameEvent, reveal: Reveal): String? {
        val at = line.indexOfFirst { it.id == event.id }
        if (at < 0) return null
        val before = model.copy()
        for (i in line.lastIndex downTo at) before.revert(line[i])
        if (before.stale) return null
        fun cards(ids: Collection<ObjectId>) = ids.mapNotNullTo(HashSet()) { (it as? ObjectId.Card)?.card }
        val seen = cards(Host.visible(before.engine, me))
        val state = before.engine
        val choices = ArrayList<String>()
        fun offered() {
            val d = (state.pending?.decision as? Decision.ChooseObjects)?.takeIf { it.player == me } ?: return
            val shown = cards(d.options + d.others)
            if (reveal.cards.any { it in shown && it !in seen } && choices.none { it.startsWith(d.id.value) })
                choices += "${d.id.value} (${d.options.size} to choose, ${d.others.size} beside them)"
            seen += shown
        }
        for (step in event.engine?.steps.orEmpty()) {
            offered()
            step.changes.forEach { state.apply(it) }
        }
        offered()
        if (reveal.cards.any { it !in seen }) return null
        return if (choices.isEmpty()) "all seen before" else "shown in the choice ${choices.joinToString()}"
    }

    // ---- Plays shown (HIS-112)

    /**
     * HIS-112 (owner 2026-11-07, MTG Arena as the reference): a Stack entry put on the Stack in the shown event [event]
     * that this player's table shows large in the middle for a moment before it moves to the Stack: the other player's
     * spells cast, abilities activated and triggers, and this player's own triggers (never their own casts or
     * activations). [by] controls it, [entry] is the engine's object for it on the Stack (the spell's card object, else its
     * Stack item), [kind] how it got there, [card] the spell's card or the ability's source card (null when it has none),
     * [from] the zone a spell was cast from (HIS-112, owner 2026-11-12: it flies in from there; null for an ability, whose
     * source card is where the table shows it).
     * HIS-112 (owner, 2026-11-20): a special action of the other player's ([PlayKind.Special], CR 116.2: unlocking a door,
     * turning a card face up, foretelling, plotting, suspending …) shows the same way, though it never goes on the Stack: then
     * [entry] is the object it was taken from, [card] that object's card and [ability] which special action it was.
     * While one is open ([show]) the view holds where it is, as at a reveal: the events after it wait and the device makes
     * none of its own; the table ends it ([dismissShow]) when its time is up or the player taps it. Personal: nothing is
     * sent, and the game never waits for it on the other player's phone.
     */
    @kotlinx.serialization.Serializable @kotlinx.serialization.SerialName("play") data class PlayShow(override val event: String, val by: PlayerId, val entry: ObjectId, val kind: PlayKind,
        val card: arcanum.engine.state.CardId?, val from: arcanum.api.Zone? = null,
        val ability: arcanum.engine.view.AbilityOrigin? = null) : ViewMoment

    /** A moment of the shown event [event] that holds this player's view until it is done (HIS-112, HIS-113). */
    @kotlinx.serialization.Serializable sealed interface ViewMoment { val event: String }

    /**
     * HIS-113 (owner, 2026-11-08, 2026-11-25): a combat holds this player's view as a play shown does, for the same time
     * ([showPlays]), whoever's combat it is, live and in catch-up playback: on the damage (each combat damage step that deals
     * any). Declaring attackers holds nothing. The table ends it ([dismissCombatHold]) when its time is up. Personal: nothing is
     * sent, and the game never waits for it.
     */
    @kotlinx.serialization.Serializable @kotlinx.serialization.SerialName("combat") data class CombatHold(override val event: String, val kind: CombatMoment) : ViewMoment

    /** What a combat holds the view on (HIS-113). */
    @kotlinx.serialization.Serializable sealed interface CombatMoment {
        @kotlinx.serialization.Serializable @kotlinx.serialization.SerialName("damage") data object Damage : CombatMoment
    }

    /** How a shown play got onto the Stack (HIS-112). */
    @kotlinx.serialization.Serializable sealed interface PlayKind {
        @kotlinx.serialization.Serializable @kotlinx.serialization.SerialName("cast") data object Cast : PlayKind
        @kotlinx.serialization.Serializable @kotlinx.serialization.SerialName("activated") data object Activated : PlayKind
        @kotlinx.serialization.Serializable @kotlinx.serialization.SerialName("triggered") data object Triggered : PlayKind
        /** D6 (owner 2026-11-19): a copy of a spell or ability put on the Stack by something resolving (CR 707.10), shown to every
         * player, flying in from the copier ([PlayShow.card]). */
        @kotlinx.serialization.Serializable @kotlinx.serialization.SerialName("copied") data object Copied : PlayKind
        /** HIS-112 (owner, 2026-11-20): a special action (CR 116.2), which doesn't use the Stack: it flies back into its own
         * card instead, and the card flashes. */
        @kotlinx.serialization.Serializable @kotlinx.serialization.SerialName("special") data object Special : PlayKind
    }

    /** "Create a copy of the card with the chosen name" (CR 707.12): the player's device answers the engine's
     * [Decision.NamedCard] of their own with the card from its card records, as it answers the randomness. None until the app
     * gives them: the decision then waits. */
    var namedCards: NamedCards = NamedCards.NONE

    /** The answer to [d], the player's own [Decision.NamedCard], from [namedCards]; null (logged) when they have no such card. */
    private fun namedCard(d: Decision.NamedCard): Input.Answer? {
        val definition = runCatching { namedCards.definition(d.name) }.onFailure { remember("named card ${d.name}: the records failed: $it") }.getOrNull()
        if (definition == null) {
            remember("named card ${d.name}: none in the card records, ${d.kind} ${d.id.value} waits")
            return null
        }
        remember("named card ${d.name}: answered from the card records")
        return Input.Answer(d.id, arcanum.engine.decision.Answer.NamedCard(definition))
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

    /** HIS-112, HIS-113: every play shown and combat moment not done yet, oldest first ([show] or [combatHold] the first);
     * none while a reveal panel is open (HIS-110 first). */
    val openMoments: List<ViewMoment> get() { syncReveals(); return if (revealsOpen.isNotEmpty()) emptyList() else showsOpen.toList() }

    /** HIS-112: the event of the last play done ([dismissShow]): playback lets the next event follow it at once (the show
     * took the playback delay's place, HIS-021). */
    var showDone: String? = null
        private set

    /**
     * HIS-112: a play shown is done (its time is up, or the player tapped it): the next one shows, or the view goes on.
     * [which]: that play, while it is still open among the plays shown first, those put on the Stack in the same event as
     * the oldest (owner 2026-11-12: they fly in together and show top first, so they end in any order; a play taken back
     * meanwhile is gone already); null: the oldest. True when one ended.
     */
    fun dismissShow(which: PlayShow? = null): Boolean {
        val open = show ?: return false
        val at = if (which == null) 0 else showsOpen.indexOf(which)
        if (at < 0 || showsOpen.subList(0, at).any { it !is PlayShow || it.event != open.event }) return false
        val done = showsOpen.removeAt(at)
        showDone = done.event
        remember("play shown done in ${done.event.take(8)}${if (at > 0) " ($at after the oldest of its event)" else ""}, holds left ${showsOpen.size}")
        return true
    }

    /** HIS-113: the combat moment holding the view now is done (its time is up, or the player tapped the table): the next
     * moment comes, or the view goes on. [which]: only when it is still the one. True when one ended. */
    fun dismissCombatHold(which: CombatHold? = null): Boolean {
        val open = combatHold ?: return false
        if (which != null && which != open) return false
        showsOpen.removeAt(0)
        showDone = open.event
        remember("combat hold done: ${open.kind::class.simpleName} in ${open.event.take(8)}, holds left ${showsOpen.size}")
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
    private fun stopAt(event: GameEvent, mode: ResponseMode = if (pausing) ResponseMode.FullControl else settings.mode,
        /** The state before [event]: the shown one, or one worked out ahead of it ([rest]). */
        s: GameState = model.engine): Input? {
        val step = event.assumed ?: return null
        if (step.assumedBy == me || Acceptance.assumedAbout(step, event, players) != me) return null
        if (remaking.isNotEmpty() && event.id == lateAnswerAt()) return step.input
        // TRN-017 (owner 2026-11-07): a Resolve chain presses Resolve there: the other player's assumption of that press
        // over the Stack shows (and so is confirmed) instead of stopping
        if (chain && !pausing && step.input is Input.PassPriority && s.stack.isNotEmpty()) return null
        // The stop the player stood at when they changed their mode stays until they answer it ([keptAt])
        if (keeps(event.parentId)) return step.input
        // HIS-058, TRN-057: an End turn passes the player's priority, so the other player's assumption of that pass shows (and
        // so is confirmed) instead of stopping, in every response mode, up to the other player's action that ended it
        // (TRN-168); a pause of the player's still stops
        if (step.input is Input.PassPriority && !(pausing && mode == ResponseMode.FullControl) && endTurnPasses(event, s)) return null
        return step.input?.takeIf { NextStop.stopsAt(s, it, me, mode) }
    }

    /** HIS-058, TRN-168: whether the End turn of [s]'s turn passes the player's priority at [event] in playback: going on, or
     * ended by a later event of the other player's not shown yet ([endTurnShown]). */
    private fun endTurnPasses(event: GameEvent, s: GameState): Boolean {
        if (endTurn == s.turn.number) return true
        val (turn, until) = endTurnShown ?: return false
        if (turn != s.turn.number) return false
        val at = session.indexOf(event.id) ?: return false
        val end = session.indexOf(until) ?: return false
        return at < end
    }

    // ---- Where the view comes to rest (TRN-163 to TRN-166)

    /**
     * TRN-163, TRN-164 (owner 2026-11-19): where this player's view comes to rest once what it still waits on is skipped, while
     * playback is behind the head: [at] is the event it would stand at, [model] the state there (a copy) and [status] what the
     * player is offered there: the stop playback would make before the next event ([stopAt]: their response mode, a pause of
     * theirs, a kept stop, the late answer), or at the head their move, the wait for the other player ([point]) or nothing to
     * press ([Wait.Sending]: a step made by itself comes first, TRN-119; the result, [Status.Idle]). Engine thread.
     */
    class Rest(val at: String, val status: Status, val model: GameModel)

    /**
     * TRN-163, TRN-164: where the view would come to rest ([Rest]) when it isn't the shown point: playback behind the head and
     * not standing at its stop already; null otherwise (and while a take-back is held, a draft or the device's own events are
     * open, in the opening, which is never played back). A reveal on the way isn't foreseen: the skip stops at its panel
     * ([skipToRest]). Worked out anew whenever the shown point, the head or a setting it reads changed.
     */
    fun rest(): Rest? {
        if (!session.behind || needsReload || holding || session.drafting || session.pending.isNotEmpty() || opening) return null
        val key = listOf(session.head, session.lineStart, settings, pausing, chain, endTurn, endTurnShown, opponentMode,
            opponentAssume, request, holdAt, remaking.size)
        val tip = session.tip
        restMemo?.takeIf { it.first == key }?.let { (_, was, from) ->
            if (from == tip) return was
            // Playback stepping toward the same rest point keeps it (the events up to it and their stops are the same)
            val at = was?.let { session.indexOf(it.at) }
            val now = session.indexOf(tip)
            if (was != null && at != null && now != null && now < at && (session.indexOf(from) ?: -2) <= now) {
                restMemo = Triple(key, was, tip)
                return was
            }
        }
        val buffered = session.buffered
        if (stopAt(buffered.first()) != null) return null.also { restMemo = Triple(key, null, tip) }
        val m = model.copy()
        var at = tip
        for (next in buffered) {
            val decision = stopAt(next, settings.mode, m.engine)
            val stop = if (pausing) stopAt(next, s = m.engine) else decision
            if (stop != null) return Rest(at, Status.Stopped(next, stop, paused = decision == null), m).also { restMemo = Triple(key, it, tip) }
            m.apply(next)
            at = next.id
        }
        return Rest(at, restAtHead(m), m).also { restMemo = Triple(key, it, tip) }
    }

    /** [rest] worked out last: what it read besides the shown point, the rest point, and the shown point it was worked out at. */
    private var restMemo: Triple<List<Any?>, Rest?, String>? = null

    /** TRN-164: what the player is offered at the head reached ahead of the shown point, state [m] (the head's part of [status]). */
    private fun restAtHead(m: GameModel): Status {
        val s = m.engine
        if (Engine.result(s) != null) return Status.Idle
        if (request != null) return Status.Waiting(Wait.Request)
        if (m.table.suggestion != null) return Status.Waiting(Wait.Suggestion)
        val who = Attention.of(s) ?: return Status.Idle
        // TRN-119: a step made by itself comes first there (the player's own pass by their mode, an assumption): nothing to press
        if (runCatching { makesItself(m, session.head) }.getOrDefault(false)) return Status.Waiting(Wait.Sending)
        if (who == me || declares(s)) return Status.YourMove
        return when (val point = point(s, m)) {
            is Point.Wait -> Status.Waiting(point.reason, point.offer)
            else -> Status.Waiting(Wait.Sending)
        }
    }

    /**
     * TRN-165 (owner 2026-11-19): the player pressed the main button while their view still waited on something: every play
     * shown and combat hold ends, the catch-up's pause ends, and playback shows at once every event up to where the view comes
     * to rest ([rest]): its next stop, an open reveal (HIS-110: a reveal is never passed unseen, its panel opens) or the head,
     * opening no play shown or combat hold for what it passes. Personal: nothing is sent. Returns what it did, for the log.
     */
    fun skipToRest(): String {
        syncReveals()
        val ended = showsOpen.size
        showsOpen.clear()
        val paused = catchUp && session.behind
        if (!holding) catchUp = false
        var shown = 0
        while (session.behind && !needsReload && !holding && !session.drafting && session.pending.isEmpty() && revealsOpen.isEmpty()) {
            if (stopAt(session.buffered.first()) != null) break
            session.showNext()
            shown++
            // What the skipped events would show (their plays, their combat) isn't shown; their reveals are
            syncReveals()
            showsOpen.clear()
        }
        showResultOks()
        if (!session.behind) { pausing = false; catchUp = false }
        val what = "skipped to rest: $ended view holds ended, ${if (paused) "the catch-up's pause ended, " else ""}$shown events " +
            "shown at once${if (revealsOpen.isNotEmpty()) ", stopped at a reveal" else ""}; ${viewState()}"
        remember(what)
        return what
    }

    // ---- The device's own steps

    /**
     * Makes the events the device makes by itself at the head (its player's passes by their mode, engine-automatic
     * answers, assumptions), until the game waits for a player or the server, and re-makes table-only events refused as
     * stale. [now]: the time.
     */
    fun advance(now: Long) {
        this.now = now
        // HIS-095 (prod report 3UQ9DY): a pause of playback ends once nothing waits to be played back, however the head was
        // reached
        if (pausing && !session.behind) { pausing = false; remember("playback pause ended: nothing left to play back") }
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
        // Nothing to make here was already found at this point (no search again while it lasts)
        val key = pointKey()
        if (key.same(idleAt)) return false
        pointsWorkedOut++
        return decideStep().also { made -> if (!made) idleAt = key }
    }

    /**
     * TRN-063, STK-029 (owner playtest 2026-11-12): whether only a view hold (a play shown or a combat's hold, HIS-112,
     * HIS-113) keeps this player's device from making a step of their own at the head now: their pass by their response
     * mode, an End turn or a Resolve chain going on, an automatic answer. The main button then shows no press meanwhile:
     * the device makes it itself once the hold is done. Worked out once per point while a hold lasts.
     */
    val ownStepHeld: Boolean get() = showHolds() && ownStepComes()

    /**
     * TRN-063, TRN-119 (owner, 2026-11-17): whether the device's next own step at the head ([automaticStep]) is a step of this
     * player's own (their pass by their response mode, an End turn or a Resolve chain going on, an automatic answer), view
     * holds aside: once a hold is done ([ownStepHeld]) or, on the board of the player's own action published before the
     * device's steps after it are made (an Assume's, a Resolve's), at once. The main button shows no press meanwhile.
     */
    val ownStepNext: Boolean get() = ownStepComes()

    /** [ownStepHeld] without the view hold: worked out once per point. */
    private fun ownStepComes(): Boolean {
        if (needsReload || holding || session.drafting || session.behind || request != null || locked || queuedUndo != null) return false
        val key = pointKey()
        stepHeldAt?.let { (at, held) -> if (key.same(at)) return held }
        val s = model.engine
        val held = Engine.result(s) == null && Attention.of(s).let { who -> who != null && (who == me || declares(s)) } &&
            !held() && ownAt(s, key)?.let { input -> reveals["${session.tip}:$input"] != true } == true
        stepHeldAt = key to held
        return held
    }

    /** [own] at the head's point [key], worked out once there: [ownStepComes] and the step made there next ([decideStep]) ask
     * the same (the engine work count, EngineWorkCountTest: the second search it cost). */
    private fun ownAt(s: GameState, key: PointKey = pointKey()): Input? {
        ownFound?.let { (at, input) -> if (key.same(at)) return input }
        return own(s).also { ownFound = key to it }
    }

    /** [own] at its point, worked out last ([ownAt]). */
    private var ownFound: Pair<PointKey, Input?>? = null

    /** [ownStepComes] at its point, worked out last. */
    private var stepHeldAt: Pair<PointKey, Boolean>? = null

    /**
     * TRN-063, STK-029, SYS-025 (owner, 2026-11-16): at the player's own action's performed point ([act]'s performed: its
     * event still open), whether the first thing folded into it next ([fold]) is a pass of theirs: by their response mode,
     * an End turn going on, or one they can't avoid. The action's own board, shown there before that pass is made, then
     * holds no press on the main button (it said "Resolve" for a few ms before the device passed by itself). The pass check
     * it may need is the one [fold] reads right after (kept with the state); after the player's own spell or ability their
     * mode's pass needs none.
     */
    fun ownPassFollows(): Boolean {
        val s = model.engine
        if (Engine.result(s) != null || s.pending?.decision != null) return false
        val holder = s.priority.holder ?: return false
        if (Host.decider(s, holder) != me) return false
        if (endTurn == s.turn.number) return true
        val check by lazy { model.passCheck(holder) }
        return (Stops.passes(settings.mode, s, holder) { check.ownSkipOn } && !actedInStep(s, holder)) ||
            (check.asSet && !Stops.stop(s, holder))
    }

    /** [automaticStep] worked out: whether the device made its own step at the head. */
    private fun decideStep(): Boolean {
        val s = model.engine
        if (Engine.result(s) != null) return false
        if (chain) chainEnds(s)?.let(::endChain)
        val who = Attention.of(s) ?: return false
        if (who == me || declares(s)) {
            if (held()) return false
            val input = ownAt(s) ?: return false
            if (reveals["${session.tip}:$input"] == true) { endChain("press would show hidden cards"); return false }
            if (input is Input.PassPriority && Stops.stop(s, input.player) && endTurn != s.turn.number) ownMainPass?.invoke("by itself")
            // Spec rule 6 (owner 2026-11-06): the device's own pass is exactly a press of Resolve or the step arrow
            return (act(input, emptyList(), { null }, null, automatic = true) is Acted.Sent).also { made -> if (!made) endChain("press not made") }
        }
        return when (val point = point(s)) {
            is Point.Automatic -> {
                if (s.pending?.decision?.let { Host.decider(s, it.player) } == opponent)
                    remember("answering ${opponent.value.take(8)}'s one-answer question for them (their Quick, SYS-063): ${point.input}")
                make(point.input, assumed = false) is Acted.Sent
            }
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
        if (d != null && d !is Decision.Random && Host.decider(s, d.player) == me && WhoActs.automaticAnswer(model, d) == null) return "decision"
        return null
    }

    /** Ends the turn for this player (TRN-057): their device passes their priority until the turn ends, declaring no
     * attackers, or until the other player does anything but pass in it ([endTurnInterrupted], TRN-168); the
     * other player's points are assumed, automatic or waited for as usual. */
    fun endTurn(now: Long) {
        endChain("end turn")
        endTurn = model.engine.turn.number
        endTurnAt = session.tip
        endTurnFirst = null
        endTurnShown = null
        endTurnMade = true
        holdAt = null
        endTurnSaid?.invoke("begins at ${session.tip.take(8)} (turn ${model.engine.turn.number} ${model.engine.turn.step}): the device passes the player's priority until the turn ends")
        advance(now)
        endTurnMade = false
    }

    /**
     * HIS-097, HIS-084: the main line changed after [at] (a branch there, a rewind to it). An End turn going on was taken
     * back with it when the point it was tapped at is gone or is [at] itself, or its own first event is gone: it ends, so
     * the device passes nothing more for it on the new line (before, it carried the new line to the end of the turn by
     * itself, with the player's later actions "taken back"). A change after its start leaves it going on, as live, unless
     * it is the other player's spell or ability ([endTurnInterrupted], TRN-168: that ends it, live or as a branch).
     */
    private fun endTurnTakenBack(at: String) {
        if (endTurn == null) return
        fun gone(id: String) = !session.knows(id) && session.pending.none { it.id == id }
        val tapped = endTurnAt
        if (tapped != null && !gone(tapped) && tapped != at && endTurnFirst?.let(::gone) != true) {
            endTurnSaid?.invoke("goes on (turn $endTurn): the line changed after ${at.take(8)}, after its start at ${tapped.take(8)}")
            return
        }
        endTurnSaid?.invoke("taken back (turn $endTurn): the line changed after ${at.take(8)}, tapped at ${tapped?.take(8)}")
        endTurn = null; endTurnAt = null; endTurnFirst = null
    }

    /**
     * TRN-168, CR 733.2b, CR 733.2c (owner problem report UJP7DE; owner 2026-11-27): the other player's real [event] did
     * anything but pass at their priority in the turn of an End turn going on, whoever's turn it is: cast a spell, activated
     * an ability (mana abilities and special actions included), played a land, or declared attackers (then the player
     * blocks and gets their priority as usual). They shortened the shortcut there, so it is over and the player answers it
     * themselves (before, the device passed their priority over that spell, and over it again each time it was taken back
     * and cast anew, so they could never respond). Their passes, an attack with nothing, and their answers inside what was
     * already going on leave it.
     */
    private fun endTurnInterrupted(event: GameEvent) {
        val turn = endTurn ?: return
        val acted = event.engine?.steps.orEmpty().mapNotNull { it.input }.firstOrNull { input ->
            when (input) {
                is Input.Cast -> input.player == opponent
                is Input.Activate -> input.player == opponent
                is Input.PlayLand -> input.player == opponent
                is Input.DeclareAttackers -> input.player == opponent && input.attacks.isNotEmpty()
                else -> false
            }
        } ?: return
        endTurn = null; endTurnAt = null; endTurnFirst = null; endTurnShown = turn to event.id
        val what = when (acted) {
            is Input.Cast -> "cast ${acted.card}"
            is Input.Activate -> "activated ${acted.source}"
            is Input.PlayLand -> "played the land ${acted.card}"
            else -> "attacked with ${(acted as Input.DeclareAttackers).attacks.map { it.attacker }}"
        }
        remember("End turn of turn $turn ends: ${opponent.value.take(8)} $what in ${event.id}" +
            if (session.behind) "; playback behind it still passes for the player before it" else "")
        // An End turn of an earlier turn passes nothing any more: only that one's end is news for the log
        if (turn == model.engine.turn.number)
            endTurnSaid?.invoke("ends: the other player $what in ${event.id.take(8)} (turn $turn): the player answers it themselves")
    }

    /** The End turn's first event among [dropped] (the server took another event first; it goes on as SYS-076 says): no
     * longer its first event. */
    private fun endTurnDropped(dropped: List<GameEvent>) {
        if (endTurnFirst != null && dropped.any { it.id == endTurnFirst }) endTurnFirst = null
    }

    /** The player's own input the device makes at the head by itself, or null (§6, §7). [m]: the state there (the shown
     * one, else the main line's head while playback is behind it, [settledAtHead]), [at] the event it is at. */
    private fun own(s: GameState, m: GameModel = model, at: String = session.tip): Input? {
        val d = s.pending?.decision
        if (d != null) {
            if (d is Decision.Random || Host.decider(s, d.player) != me) return null
            if (d is Decision.NamedCard) return namedCard(d)
            return WhoActs.automaticAnswer(m, d) ?: oneAnswer(s, d, m)?.takeIf { !keeps(at) }
        }
        val holder = s.priority.holder
        if (holder != null) {
            // An End turn going on passes whatever they could do: no search of their options
            if (endTurn == s.turn.number) return Input.PassPriority(holder)
            // TRN-017: so does a Resolve chain, over the Stack ([chainEnds] ends it once the Stack is empty)
            if (chain) return Input.PassPriority(holder)
            // The stop they stood at when they changed their mode waits for their answer ([keptAt])
            if (keeps(at)) return null
            // One search of the player's options answers both (the engine's own pass and their mode's)
            val check = m.passCheck(holder)
            if (check.asSet && !Stops.stop(s, holder)) return Input.PassPriority(holder)
            if (!modePasses(s, holder, check)) return null
            // STK-213 (owner 2026-11-19, item dea2ae3ac4): after their own cast or activation in this step, the device passes
            // for them only over what they put on top of the Stack; once it has resolved, their next pass here is their press
            return Input.PassPriority(holder).takeIf { !actedInStep(s, holder) }
        }
        if (endTurn == s.turn.number && s.turn.active == me)
            return Engine.options(s, me, m.keptView(), EnginePreferences.of(s)).firstOrNull { it is Input.DeclareAttackers && it.attacks.isEmpty() }
        return null
    }

    /** Under Auto-skip, the only answer to [d] (§2). Never the order of triggers (STK-030, owner 2026-11-06): the engine
     * lists one order of the many there are, and asks only when the triggers differ, so their player always orders them. Nor
     * a selection up to a total (CR 701.59a): the engine lists a working sample of them, and which cards go is theirs to confirm.
     * SYS-209: a mana payment of several ways only with Autotap on (its suggestion). */
    private fun oneAnswer(s: GameState, d: Decision, m: GameModel = model): Input? =
        if (settings.mode != ResponseMode.AutoSkip) null else quickJudged(m, d, "the player's own", autotap = settings.autotap)

    /**
     * SYS-063, item a819b9ffdd (owner 2026-11-19): the only answer to the other player's [d] on [m] while their response mode is
     * Quick ([opponentMode]): this device answers it for them as their own Quick would, the cards only they see judged too
     * (held, never shown), so their wait goes; null otherwise. On a tentative line the information rule still holds: an answer
     * whose outcome would show this player a card it holds back (a discarded card, unless the game allows hands, LOB-135) is
     * refused once ([make]: remembered) and left to their own device.
     */
    private fun theirOneAnswer(d: Decision, m: GameModel = model): Input.Answer? {
        if (opponentMode != ResponseMode.AutoSkip || Host.decider(m.engine, d.player) != opponent) return null
        return quickJudged(m, d, "the other player's")
    }

    /**
     * [WhoActs.quickJudged] of [whose] decision [d] on [m], said once per judgement in the device's record and to [quickAnswers]
     * (problem report DH9MW5: whether Quick answers a choice by itself, and why).
     */
    private fun quickJudged(m: GameModel, d: Decision, whose: String, autotap: Boolean = false): Input.Answer? {
        val judged = WhoActs.quickJudged(m, d, autotap)
        val line = "Quick ${if (judged.answer != null) "answers" else "leaves"} $whose ${d.kind} ${d.id.value}: ${judged.why}" +
            (judged.answer?.let { " (${it.answer})" } ?: "")
        if (line != quickSaid) {
            quickSaid = line
            remember(line)
            quickAnswers?.invoke(line)
        }
        return judged.answer
    }

    /** The judgement [quickJudged] said last. */
    private var quickSaid: String? = null

    /** The players on Quick as this device knows (itself, and the other player's [opponentMode]): any device may answer their
     * one-answer questions (SYS-063). */
    private fun quickPlayers(): Set<PlayerId> = setOfNotNull(me.takeIf { settings.mode == ResponseMode.AutoSkip },
        opponent.takeIf { opponentMode == ResponseMode.AutoSkip })

    /**
     * STK-213 (owner 2026-11-19, item dea2ae3ac4): whether [player] cast a spell or activated an ability in the current step on
     * the line up to the tip ([actedInStep] of the companion), while nothing of theirs is on top of the Stack ([s]): then
     * their mode passes nothing for them in this step (a second trick after a pump), neither on their own device nor as the
     * other player's device assumes their pass by it; with their own spell or ability on top their device still passes it
     * on to the other player as before (TRN-119). An End turn or a Resolve chain still passes. Steps where they did nothing
     * are passed by their mode as before.
     */
    private fun actedInStep(s: GameState, player: PlayerId, building: EventBuilder? = null): Boolean {
        val top = s.stack.lastOrNull()
        if (top != null && (s.stackItems[top]?.controller ?: s.obj(top)?.controller) == player) return false
        return actedInStep(session.events + session.pending, player, building?.engineSteps.orEmpty(), me.value.takeIf { building != null })
    }

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

    /** [m]: the state at the point (the shown one, else the main line's head while playback is behind it, [settledAtHead]: there
     * the information rule's probe, which works at the tip, isn't made). */
    private fun point(s: GameState, m: GameModel = model): Point {
        val d = s.pending?.decision
        if (d != null) {
            if (d !is Decision.Random) WhoActs.automaticAnswer(m, d)?.let { return Point.Automatic(it) }
            // SYS-063 (owner 2026-11-19): their one-answer question while they are on Quick
            if (d !is Decision.Random) theirOneAnswer(d, m)?.takeIf { m !== model || reveals["${session.tip}:$it"] != true }
                ?.let { return Point.Automatic(it) }
            // TRN-027, HIS-096 (owner, 2026-11-15): the other player's opening keep is assumed only by a press of Assume keep
            // (never by the phone itself), once this player's own opening is done
            Reapply.assumedKeep(s, me)?.let { keep -> return Point.Wait(Wait.Keep, Offer.AssumeKeep.takeIf { assumable(keep, m) }, keep) }
            if (Engine.options(s, d.player, m.keptView(), EnginePreferences.of(s)).any { keep(it) }) return Point.Wait(Wait.Keep)
            return Point.Wait(Wait.Choosing(d.source?.takeIf { it in m.visible(me) }))
        }
        val holder = s.priority.holder
        if (holder != null) {
            if (Stops.forcedPass(m, holder)) return Point.Automatic(Input.PassPriority(holder))
            val pass = Input.PassPriority(holder)
            if (Stops.stop(s, holder) || !assumable(pass, m)) return Point.Wait(Wait.TheirTurn)
            if (m === model && revealing(pass)) return Point.Wait(Wait.HiddenCards)
            // HIS-096 (owner, 2026-11-05): never the other player's presence
            if (assumesAtOnce(s, holder, m)) return Point.Assume(pass)
            val top = s.stack.lastOrNull()
            val reason = if (top != null) Wait.Respond(top) else Wait.MayAct(s.turn.step)
            return Point.Wait(reason, Offer.AssumeResolves, pass)
        }
        val blocks = Engine.options(s, opponent, m.keptView(), EnginePreferences.of(s)).filterIsInstance<Input.DeclareBlockers>()
        if (blocks.isNotEmpty()) {
            val none = Input.DeclareBlockers(opponent, emptyList())
            if (!assumable(none, m)) return Point.Wait(Wait.Blockers)
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
    private fun assumesAtOnce(s: GameState, holder: PlayerId, m: GameModel = model): Boolean {
        if (Stops.passes(opponentMode, s, holder) { m.passCheck(holder).ownSkipOn } && !actedInStep(s, holder)) return true
        if (pointOwner(session.events + session.pending, s) != me) return false
        return settingAssumes(settings.assume, s, holder, me) { noMana(s, holder, m) }
    }

    /** Whether [holder] has no mana on [s] as this player's table shows it: nothing in their mana pool and nothing they
     * could activate (no untapped mana source, mana abilities being activations too, and no other ability usable now).
     * The cards in their hand are hidden and don't count. */
    private fun noMana(s: GameState, holder: PlayerId, m: GameModel = model): Boolean =
        s.player(holder).manaPool.isEmpty() && Engine.options(s, holder, m.keptView(), EnginePreferences.of(s)).none { it is Input.Activate }

    private fun keep(input: Input): Boolean = when (input) {
        is Input.SetupAnswer -> input.player == opponent && input.answer == arcanum.engine.decision.Answer.Option("Keep")
        is Input.Answer -> input.answer == arcanum.engine.decision.Answer.Option("Keep")
        else -> false
    }

    /** Whether this player may assume [input] of the other player's here (§3: the server's own rules). */
    private fun assumable(input: Input, m: GameModel = model): Boolean =
        try { WhoActs.kind(m, input, me, assumed = true) is WhoActs.Kind.Assumed } catch (_: Refusal) { false }

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
        // HIS-115: another opening answer at the other player's assumed keep re-applies their line on it: on the same line
        if (stop != null && input !is Input.Concede && Reapply.opening(stop.event, model.engine)) {
            remember("opening answer $input at the assumed ${stop.assumed}: re-applies their line")
            return make(input, assumed = false, follow = follow, table = table, player = true, reapplies = true)
        }
        return make(input, assumed = false, follow = follow, table = table, newLine = lines + 1, player = true)
    }

    /** Confirms the assumption at the stop with [input], then makes what [follow] gives next and [table] (I-D56 §8:
     * "a confirmation followed by new input"). [replay]: [follow]'s queue, when it replays a draft's inputs (the confirmed
     * event's own automatic answers are skipped in it). A declaration's end of its staging among [table] is made at the
     * head after the confirmation ([endStaging]). */
    private fun confirmThen(input: Input, table: List<TableChange>, follow: (GameModel) -> Input?, replay: ArrayDeque<Input>?): Acted {
        val event = session.buffered.first()
        confirm(input)
        if (replay != null) for (folded in event.engine?.steps.orEmpty().drop(1).mapNotNull { it.input })
            if (replay.firstOrNull() == folded) replay.removeAt(0)
        var acted: Acted = Acted.Confirmed
        val next = follow(model)
        if (next != null) acted = goOn(next, follow, replay)
        val (ends, rest) = table.partition(::endsStaging)
        if (rest.isNotEmpty() && acted !is Acted.Refused) {
            val target = if (session.behind) session.headModel() else model
            val again = rebase(target, rest) ?: return Acted.Refused("inapplicable")
            this.table(again).let { if (acted == Acted.Confirmed) acted = it }
        }
        if (ends.isNotEmpty() && acted !is Acted.Refused) endStaging(ends)
        return acted
    }

    /** Whether [change] ends the attack or block staging (a declaration's, I-D54 §8). */
    private fun endsStaging(change: TableChange): Boolean =
        change is TableChange.AttackDraft && change.after == null || change is TableChange.BlockDraft && change.after == null

    /**
     * Follow-up to problem reports WCRF3Q, R8XVPA: a declaration confirmed at a decision stop ends its shared staging in an
     * event of its own at the head (the confirming answer must be the input alone, §8), so no staging is left on the table
     * for a later combat: the staging as it stands at the head ([ends] rebased), nothing when none is left there.
     */
    private fun endStaging(ends: List<TableChange>) {
        val at = if (session.behind) session.headModel() else model
        val again = rebase(at, ends).orEmpty().filterNot { c ->
            c is TableChange.AttackDraft && c.before == null || c is TableChange.BlockDraft && c.before == null }
        remember("declaration at a stop ends the staging at the head: ${again.ifEmpty { "none left" }}")
        if (again.isEmpty()) return
        if (!session.behind) { table(again); return }
        atHead(at, again)
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

    private fun checkDraft() { val open = openDraft; if (open != null && (open.finished || !session.drafting)) dropDraft() }

    private var openDraft: EventBuilder? = null
    private var draftStop: Status.Stopped? = null
    private var draftBefore: InformationRule.Before? = null

    /** Opens a draft at the tip: starting [newLine] (a branch), or the line a rewind left due at the head (§9); [reapplies]:
     * an opening answer at the other player's assumed keep, sent behind the head on the same line (HIS-115). */
    private fun open(newLine: Int? = null, reapplies: Boolean = false): EventBuilder {
        val line = newLine ?: if (session.behind) null else Rewind.lineDue(model, lines)
        // HIS-042 (owner 2026-11-26): a branch's line reshuffles with the game's "on branch" switch, the due one with its kind's
        val branch = newLine != null || lineBranch
        val reshuffle = assumeReveals.reshuffles(branch)
        return session.begin(ids(), line, reapplies, reshuffle).also { builder ->
            // HIS-042: what a new line's reshuffle moved, and what it was made under (report DH3QX6)
            if (line != null) remember("line $line starts (${if (branch) "a branch's" else "an undo's"}, " +
                "${if (reshuffle) "reshuffling" else "the libraries kept: the game's switch is off"}): cards moved per library " + builder.changes
                .filterIsInstance<arcanum.engine.change.Change.Reorder>().map { c -> c.before.indices.count { c.before[it] != c.after[it] } } +
                " at ${where()}, open ${openDecision()}")
        }
    }

    /** Makes [input] (assumed: the other player's, §3) as an event at the tip, or at the local head with [newLine]. */
    private fun make(input: Input, assumed: Boolean, follow: (GameModel) -> Input? = { null }, table: List<TableChange> = emptyList(),
        newLine: Int? = null, player: Boolean = false, performed: (() -> Unit)? = null, reapplies: Boolean = false): Acted {
        // The server's rules (§6): the player's own inputs (or engine-automatic answers), the other player's only assumed
        try {
            val kind = WhoActs.kind(model, input, me, assumed, quickPlayers())
            if (kind == WhoActs.Kind.Random) return Acted.Refused("Not the player's input")
        } catch (e: Refusal) { return Acted.Refused(e.reason) }
        val before = informationRule { if (assumed || tentative()) InformationRule.Before.of(model) else null }
        // HIS-101: the player's own press leaving their main phase (their device's own passes there are an End turn's); a
        // move made again after a lost race (SYS-077) comes here as a press too, and keeps its mark
        val press = model.engine.turn.step.takeIf { player && !assumed && input == Input.PassPriority(me) && model.engine.pending == null && Stops.stop(model.engine, me) }
            ?.let { if (it == arcanum.api.TurnStep.PrecombatMain) MainPress.ToCombat else MainPress.ToEndStep }
        val builder = open(newLine, reapplies)
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
            // Problem report KZBKLR: what the rule held back, for the refusal's log line
            heldBack = heldBackOf(before, builder).also { remember("information rule held back $first: $it") }
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
    /** What the information rule held back of the player's last action it refused ([REVEALS_HIDDEN]): the cards it would have
     * shown them, whether a random result, and the random decisions answered; null before any. For the refusal's log line. */
    var heldBack: String? = null
        private set

    private fun heldBackOf(before: InformationRule.Before, builder: EventBuilder): String = informationRule {
        val cards = InformationRule.revealing(before, builder.changes, me, model.view(), assumeReveals)
        val random = InformationRule.randomResult(builder.engineSteps, before.state, me, assumeReveals)
        val randoms = builder.engineSteps.mapNotNull { step -> (step.input as? Input.Answer)?.takeIf { it.answer is arcanum.engine.decision.Answer.RandomObjects ||
            it.answer is arcanum.engine.decision.Answer.CoinFlip || it.answer is arcanum.engine.decision.Answer.DieRolls }?.decision?.value }
        "cards ${cards.map { it.value }}, random result $random (random answers $randoms)"
    }

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
                // The player's own copy of a named card is answered with their input's event, as the randomness is (CR 707.12)
                if (d is Decision.NamedCard) {
                    builder.perform((if (own && Host.decider(s, d.player) == me) namedCard(d) else null) ?: return)
                    return@repeat
                }
                val answer = WhoActs.automaticAnswer(model, d)
                    ?: if (own && Host.decider(s, d.player) == me) oneAnswer(s, d) else null
                if (answer == null) {
                    // SYS-063 (owner 2026-11-19): the other player's one-answer question while they are on Quick, unless what it
                    // shows is what the information rule holds back on a tentative line
                    val theirs = (if (Host.decider(s, d.player) == opponent) theirOneAnswer(d) else null) ?: return
                    val mark = builder.mark()
                    builder.perform(theirs)
                    builder.answerRandoms()
                    if (guard != null && shows(guard, builder)) { builder.takeBack(mark); return }
                    remember("answered ${opponent.value.take(8)}'s one-answer question for them in the event (their Quick, SYS-063): $theirs")
                    return@repeat
                }
                builder.perform(answer)
                return@repeat
            }
            val holder = s.priority.holder ?: return
            val mine = Host.decider(s, holder) == me
            // After an assumed input only engine-automatic answers fold: the player's own inputs are their own event
            if (mine && !own) return
            // One search of the holder's options answers both the forced pass and their own mode's (playtest 2026-11-04),
            // kept with the state (the device's next point there reads it again)
            val check = model.passCheck(holder)
            val forced = check.asSet && !Stops.stop(s, holder)
            // STK-213: not after their own cast or activation in this step once nothing of theirs is on top
            val yields = mine && !forced && modePasses(s, holder, check) && !actedInStep(s, holder, builder)
            if (!forced && !yields) return
            if (s.turn.number >= start + 2 && Stops.stop(s, s.turn.active)) return
            // TRN-060 (CR 104.4b): a pass nobody could avoid is a forced one, or the player's own where they can do nothing
            // but pass: what they hold can't stop the loop (a hand of only lands, owner 2026-11-05)
            val unavoidable = forced || (yields && check.ownSkipOn)
            val key = if (yields || s.stack.isNotEmpty() && unavoidable) Host.loopKey(s) else null
            if (!unavoidable) forcedSeen.clear()
            else if (key != null && s.stack.isNotEmpty() && !forcedSeen.add(key)) {
                builder.perform(Input.MandatoryLoop)
                return@repeat
            }
            if (yields && key != null && !yielded.add(key)) return
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
        if (point.offer == Offer.AssumeKeep) return assumeKeep()
        val input = when (point.offer) {
            null -> return Acted.Refused("noOffer")
            Offer.AssumeBlockers -> Input.DeclareBlockers(opponent, blocks)
            else -> point.input ?: return Acted.Refused("noOffer").also {
                Faults.report("GameDevice", "assume offer without an input", mapOf("offer" to point.offer.toString(), "wait" to point.reason.toString()))
            }
        }
        return assumed(input).also { acted ->
            if (acted !is Acted.Sent) return@also
            assumeTaps += acted.event.id
            if (assumeTaps.size > MAX_ORIGINS) assumeTaps.remove(assumeTaps.first())
        }
    }

    /**
     * HIS-096, TRN-070: a press of Assume keep: the other player's opening keep assumed, with the stand-in choice of the
     * cards their last mulligan owes to the bottom first when one is owed ([Reapply.assumedKeep]: each its own event). Never
     * made by the device itself; after a re-apply the server makes it again with the line (HIS-115).
     */
    private fun assumeKeep(): Acted {
        var acted: Acted = Acted.Refused("noOffer")
        repeat(2) {
            val input = Reapply.assumedKeep(model.engine, me)?.takeIf { assumable(it) } ?: return acted
            remember("assume keep: $input")
            acted = assumed(input)
            val sent = acted as? Acted.Sent ?: return acted
            assumeTaps += sent.event.id
            if (assumeTaps.size > MAX_ORIGINS) assumeTaps.remove(assumeTaps.first())
        }
        return acted
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
        if (locked && changes.any { it !is TableChange.Suggestion && it !is TableChange.ResultOk }) return Acted.Refused("suggestion")
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
        return atHead(model, again)
    }

    /** Table-only [changes] made as an event at the head, whose model is [model] (their before-values its own), while the
     * player is behind it. */
    private fun atHead(model: GameModel, changes: List<TableChange>): Acted {
        val builder = EventBuilder(model, ids(), session.head, me.value)
        builder.mode = settings.mode
        Rewind.lineDue(model, lines)?.let { builder.startLine(it, assumeReveals.reshuffles(lineBranch)) }
        try { changes.forEach(builder::table) } catch (e: RuntimeException) { return Acted.Refused("inapplicable") }
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
        val options = runCatching { s.players.flatMap { Engine.options(s, it.id, EnginePreferences.of(s)) } }.getOrDefault(emptyList())
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
            is TableChange.ResultOk -> if ((c.player in t.resultOks) == c.ok) return null else c
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

    /**
     * TRN-065 (owner, 2026-11-09): the player's OK on the result the engine reports, whenever it shows to them, however
     * far the other player has seen: the player stays at the table until the other player's OK makes it final.
     */
    fun confirmResult(): Acted {
        val over = status as? Status.Over ?: return Acted.Refused("noResult")
        if (!over.confirmable) return Acted.Refused("noOkToGive")
        remember("result OK pressed at ${where()}, ok so far ${model.table.resultOks.map { it.value }}")
        return table(listOf(TableChange.ResultOk(me, true)))
    }

    /** TRN-065: the player's OK taken back, while the result isn't final (the other player's OK hasn't come). */
    fun takeBackResult(): Acted {
        val over = status as? Status.Over ?: return Acted.Refused("noResult")
        if (!over.ownOk || over.final) return Acted.Refused("noOkToTakeBack")
        remember("result OK taken back at ${where()}, ok so far ${model.table.resultOks.map { it.value }}")
        return table(listOf(TableChange.ResultOk(me, false)))
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
    @kotlinx.serialization.Serializable sealed interface Undo {
        /** Sent: the server takes the line back to [target]. */
        @kotlinx.serialization.Serializable @kotlinx.serialization.SerialName("free") data class Free(val target: String) : Undo
        /** Needs the other player's approval ([reason]): the caller opens the request dialog with [target] chosen. */
        @kotlinx.serialization.Serializable @kotlinx.serialization.SerialName("request") data class Request(val target: String, val reason: String) : Undo
        /** Nothing to undo now ([reason]). */
        @kotlinx.serialization.Serializable @kotlinx.serialization.SerialName("unavailable") data class Unavailable(val reason: String) : Undo
        /** Waits for the player's own action [event] (and whatever else of theirs is on its way) to reach the server,
         * then takes it back ([undoTap]). */
        @kotlinx.serialization.Serializable @kotlinx.serialization.SerialName("queued") data class Queued(val event: String) : Undo
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
        // TRN-065, HIS-084: an OK on the result isn't an action Undo goes back to: it goes with the result
        if (input == null) return e.table.any { it !is TableChange.LineMoved && it !is TableChange.ResultOk }
        if (input is Input.PassPriority) return e.press == MainPress.EndTurn
        // The game's first event is the server's start, never a player's action, though it carries the author whose Ready
        // started the game: a game loaded from before it holds it on the line, and Undo went back to the root (refused)
        if (input is Input.StartGame) return false
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
        undoWhy = null
        return undoTo(target).also { remember("undo asked to $target from $from: $it") }
    }

    private fun undoTo(target: String?): Undo {
        endChain("undo")
        if (holding) return Undo.Unavailable("playback")
        // TRN-065: a final result ended the game: nothing goes back any more
        if (isFinal()) return Undo.Unavailable("final")
        if (target == null) return Undo.Unavailable("nothing")
        if (request != null) return Undo.Unavailable(REWIND_REQUESTED)
        if (session.drafting || session.pending.isNotEmpty()) return Undo.Unavailable("sending")
        val after = lineAfter(target) ?: return Undo.Request(target, "unknown")
        if (after.isEmpty()) return Undo.Unavailable("nothing")
        val at = (reach(target) as? Reach.State)?.model ?: return Undo.Request(target, "unknown")
        val verdict = judged(at, target, after)
        if (verdict.reason != null) return Undo.Request(target, verdict.reason)
        forgetRemakes()
        send(GameAction.EngineRewind(target))
        undoLocally(target)
        return Undo.Free(target)
    }

    /** Why the latest undo this device worked out was free or a request ([Rewind.Verdict.why]: the events after its
     * point, whose, which counted and why; owner rule 2026-11-09, problem report VXSGKH), for the app log; null before
     * any. Also in the device's record. */
    var undoWhy: String? = null
        private set

    /** [Rewind.judge] of undoing to [target] from [at] (the state there, used up) with the main line's events [after]
     * it, remembered ([undoWhy]); the other player's passes count while the game's setting says so (N15, LOB-135). */
    private fun judged(at: GameModel, target: String, after: List<GameEvent>): Rewind.Verdict =
        Rewind.judge(at, target, after, me, heads.filterKeys { it != me }.values.toList(), assumeReveals.undoPastPasses).also {
            undoWhy = it.why
            remember("undo to $target judged: ${it.why}")
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
                lines++; lineBranch = false
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
        undoWhy = null
        return tappedUndo().also { remember("undo tapped from $from: $it") }
    }

    private fun tappedUndo(): Undo {
        endChain("undo")
        if (holding) return Undo.Unavailable("playback")
        if (isFinal()) return Undo.Unavailable("final")
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
        if (judged(at, target, after).reason != null) return null
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
        if (update != null && update.lines > lines) { lines = update.lines; lineBranch = update.lineBranch }
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
            // HIS-115: their rewind inside their opening re-applied this player's line resting on the assumed keep about them
            else if (by != null && rewoundReapplying(target, PlayerId(by), events)) {}
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
        } else if (reapplied(events)) {
            // HIS-115, HIS-116: handled
        } else for (event in events) {
            val r = session.received(event)
            // TRN-017 (owner 2026-11-07): the other player's real input ends a Resolve chain at once, so the chain never
            // presses over it: the device's next step sees it first (their same event taking the place of this device's
            // own, SYS-077, is that press, not a new input)
            if (event.author == opponent.value && event.assumed == null && event.engine?.steps.orEmpty().any { it.input != null } &&
                (r is GameSession.Received.Applied || r is GameSession.Received.Changed)) {
                opponentInputs++
                endChain("their input ${event.id}")
                endTurnInterrupted(event)
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
                    adoptMoments(r.mine.id, event.id)
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
        update?.assumes?.let(::assumes)
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
        showResultOks()
        trim()
        settleQueuedUndo()
    }

    /**
     * HIS-115, HIS-116: whether [events] are the server's re-apply of this player's line on the other player's opening move
     * (its first event, made on an earlier event of the line, without a new line, where this player's assumption followed),
     * and if so follows it: the shown state goes to the event made from the one it was on, without a take-back of what
     * stands ("<name> took a mulligan · your turn stands", [Note.Stands]); what was taken back goes back as a branch
     * ([Note.Branched]). This player's moves on their way meanwhile are made again on the re-made events when they still
     * apply there (the same decision), else dropped with the usual note. Logged in full.
     */
    private fun reapplied(events: List<GameEvent>): Boolean {
        val first = events.firstOrNull() ?: return false
        if (first.author == me.value || first.startsLine != null || session.knows(first.id)) return false
        val at = session.indexOf(first.parentId) ?: return false
        if (first.parentId == session.head) return false
        val next = session.events.getOrNull(at + 1) ?: return false
        if (next.assumed?.assumedBy != me) return false
        return followReapply(first.parentId, events, PlayerId(first.author), first)
    }

    /**
     * HIS-115 (first turn during mulligans §3.6): whether the other player's rewind to [target] ([by], with its new
     * [events]) re-applied this player's line, which rested on their assumed keep about [by] after their opening events the
     * rewind took back ([Rewind.reapplies] on this device's line; the server marks the events made again,
     * [GameEvent.carriedFrom]); if so it is followed as any re-apply ([followReapply]): what stands doesn't go back, what
     * doesn't is taken back as a branch ("<name> responded — your Ember Echo was taken back"). A rewind that made nothing
     * again is an ordinary one.
     */
    private fun rewoundReapplying(target: String, by: PlayerId, events: List<GameEvent>): Boolean {
        if (by == me || events.none { it.carriedFrom != null }) return false
        val after = session.after(target) ?: return false
        if (session.drafting) session.abandon()
        val at = session.stateAt(target)?.let { Rewind.reapplies(it, after, by) } ?: return false
        remember("rewind to ${target.take(8)} by ${by.value.take(8)} took back their ${at} opening event(s) and re-applied this player's line")
        return followReapply(target, events, by, null)
    }

    /**
     * Follows the server's re-apply of this player's line after [parent] ([events]: the other player's opening move [first],
     * or with none their rewind's events, then the events made again): see [reapplied] and [rewoundReapplying].
     */
    private fun followReapply(parent: String, events: List<GameEvent>, by: PlayerId, first: GameEvent?): Boolean {
        // The moves of this player's own on their way, carried to the re-made events (the board they were made on, by place)
        val pending = session.pending.filter { it.id in made && it.author == me.value && it.assumed == null }
        val madeOn = pending.firstOrNull()?.let { p -> session.pending.firstOrNull()?.let { session.stateAt(p.parentId) } }
        val before = where()
        val r = session.reapplied(parent, events)
        if (r == null) { needsReload = true; remember("reload at re-apply by ${by.value.take(8)} on $parent: ${followed()}"); return true }
        val again = events.filter { it.carriedFrom != null || it.assumed != null }
        remember("re-apply by ${by.value.take(8)}: ${first?.let { "${it.id.take(8)} (${it.engine?.steps?.firstNotNullOfOrNull { s -> s.input }})" } ?: "their rewind"} on " +
            "${parent.take(8)}, ${again.size} made again (${again.joinToString { "${it.carriedFrom?.take(8) ?: "-"}→${it.id.take(8)}" }}), " +
            "was $before, now ${where()}, head carried ${r.carried}, taken back ${r.takenBack.map { it.id.take(8) }}, own pending dropped ${r.dropped.size}")
        // What the old events were for this device, the new ones are
        for (e in events) {
            val old = e.carriedFrom ?: continue
            origins[old]?.let { origins[e.id] = it }
            made[old]?.let { made[e.id] = it }
            foldedPress[old]?.let { foldedPress[e.id] = it }
            if (old in assumeTaps) assumeTaps += e.id
            if (endTurnFirst == old) endTurnFirst = e.id
            if (endTurnAt == old) endTurnAt = e.id
        }
        opponentInputs++
        endChain("their input ${first?.id ?: "rewind to $parent"}")
        // Their own opening events a rewind took back aren't this player's to be told about as taken back
        val takenBack = r.takenBack.filter { first != null || it.author != by.value }
        if (takenBack.isNotEmpty()) note(Note.Branched(by, r.discarded.first(), takenBack, takenBack.filter(::realInput)))
        else if (r.carried) {
            val input = first?.engine?.steps?.firstNotNullOfOrNull { it.input }
            val mulligan = (input as? Input.Answer)?.answer == arcanum.engine.decision.Answer.Option("Mulligan") ||
                (input as? Input.SetupAnswer)?.answer == arcanum.engine.decision.Answer.Option("Mulligan")
            note(Note.Stands(by, mulligan))
        }
        endTurnTakenBack(parent)
        // §3.5: the player's moves on their way, made again on the board now when the same decision waits there, by place
        if (madeOn != null) {
            val keyOld = Host.loopKey(madeOn.engine, me)
            val keyNew = Host.loopKey(model.engine, me)
            for (p in pending) {
                val (inputs, table) = made.getValue(p.id)
                val carried = inputs.map { Reapply.carry(it, keyOld, madeOn.engine.pending?.decision?.id?.value, keyNew, model.engine.pending?.decision?.id?.value) }
                if (carried.any { it == null } || keyOld != keyNew) {
                    remember("re-apply: own move ${inputs.firstOrNull()} on its way no longer applies")
                    note(Note.Refused("stale"))
                    break
                }
                lost += p.id
                remaking += Lost(p.id, "", model.engine.deepCopy(), carried.filterNotNull(), table, first = false)
                remember("re-apply: own move ${inputs.firstOrNull()} on its way made again on ${session.tip.take(8)}")
                break
            }
        }
        return true
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
        // TRN-027 (merge gate 2026-11-21): which declarations lost the race and wait to be made again, after which event
        if (kept.isNotEmpty()) remember("opening lost the race on ${dropped.first().parentId}: ${kept.map { "${it.first.id} ${it.second}" }} " +
            "made again after $redeclareAfter, ${dropped.size - kept.size} other dropped")
        redeclaring += kept.mapNotNull { it.second }
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
        // its confirmation too): compared on the board there, before the other player's event, they were dropped.
        // Except (e2e CombatHoldTest, 2026-11-10) where that point is the other player's event that took the place of the
        // device's own one (SYS-077, [adopted]): the moves were sent on the device's own event, which the server refused
        // in the race, so the event the server took instead is that one, here and shown; nothing else is coming for them
        else if ((session.head == first.parent && first.parent !in adopted.values) || session.behind ||
            session.pending.any { it.id == first.parent }) return false
        if (!answered && session.head == first.parent) remember("remake on ${first.parent.take(8)}, which took the place of the device's own event")
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
            // TRN-027 (merge gate 2026-11-21): what the declaration made again was made on, for the problem report
            remember("redeclared $answer: ${(acted as? Acted.Sent)?.event?.let { "sent ${it.id} on ${it.parentId}" } ?: acted}")
            if (acted !is Acted.Sent && acted !is Acted.Confirmed) { note(Note.Refused("stale")); return }
        }
    }

    /** The input declaring the player's own opening [answer] now: the answer to the open decision when it is theirs,
     * else their declaration ahead of it ([Input.SetupAnswer]); null outside the opening or when neither is listed. */
    fun openingInput(answer: arcanum.engine.decision.Answer): Input? {
        val s = model.engine
        if (s.turn.number != 0) return null
        val d = s.pending?.decision
        if (d != null && Host.decider(s, d.player) == me) return Input.Answer(d.id, answer)
        return Input.SetupAnswer(me, answer).takeIf { Engine.options(s, me, EnginePreferences.of(s)).any { it is Input.SetupAnswer && it.player == me } }
    }

    /** The server moved this player's local head ahead of the shown state (a rewind, or a confirmation it took): the
     * device shows up to it. */
    private fun followServer() {
        val server = heads[me] ?: return
        val at = session.indexOf(server) ?: return
        var followed = 0
        while (session.behind && (session.indexOf(session.localHead) ?: Int.MAX_VALUE) < at && !session.drafting) {
            session.showNext()
            catchUp = false
            followed++
        }
        if (followed == 0) return
        // HIS-112, HIS-113, HIS-095 (prod reports 3LL6UZ, 3UQ9DY, 2026-11-16: the player went on in the game on their phone
        // too): what this device held its view on (a play shown, a combat's hold) or paused playback for was watched and
        // answered on another device of the player's: it holds the view no more, and the pause ends
        syncReveals()
        if (showsOpen.isNotEmpty()) {
            remember("view holds closed, the server moved this player's head on by $followed events (another device of theirs): " +
                showsOpen.map { it.event.take(8) })
            showsOpen.clear()
        }
        if (pausing) { pausing = false; remember("playback pause ended: the server moved this player's head on by $followed events") }
    }

    /** Forgets events well before both local heads (the device keeps [KEPT_EVENTS] of them for undo and previews); once
     * older history was loaded ([older]) they join it instead, so what was reached stays reachable. */
    private fun trim() {
        // HIS-110: never past an open reveal's event: a panel is closed only by its OK or by the reveal being taken back
        val earliest = (listOfNotNull(session.localHead, heads[opponent]) + revealsOpen.map { it.event }).mapNotNull(session::indexOf).minOrNull() ?: return
        val cut = earliest - KEPT_EVENTS
        if (cut < 0) return
        val from = session.lineStart
        val moved = session.events.take(cut + 1)
        session.trim(session.events[cut].id)
        older?.let { o ->
            val held = o.events.size
            o.trimmed(from, moved)
            // HIS-111: what the trim did to the older history loaded (a first page on its way joins the line through these)
            if (held > 0 || o.waitingTrimmed > 0) remember("trimmed ${moved.size} events, line start ${from.take(8)} → " +
                "${session.lineStart.take(8)}: older history " +
                if (held > 0) "$held → ${o.events.size} events" else "waits for its first page with ${o.waitingTrimmed} trimmed")
        }
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
    fun olderThrough(): String? = older?.asking(session.lineStart, rootId)

    /** Takes in the server's [page] of older history ([OlderHistory.add]); false when it doesn't fit. */
    fun addPage(page: org.asyncttwiz.protocol.EngineHistoryPage): Boolean {
        val before = older?.events?.size ?: 0
        val ok = older?.add(page, session.lineStart, rootId) == true
        val now = older?.events?.size ?: 0
        // HIS-111: whether it joined the line (the timeline and the Undo dialog show it) or is kept for states only
        remember("older page to ${page.through}: " + when {
            !ok -> "refused"
            now > before -> "taken, older history $before → $now events" +
                (older?.joined?.takeIf { it > 0 && before == 0 }?.let { ", joined through $it trimmed meanwhile" } ?: "")
            else -> "taken, kept for states only (line start ${session.lineStart.take(8)})"
        })
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
        // The server takes assumptions only from a local head it knows (I-D56 §6, §11): the shown head's report goes first
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
            is Note.Stands -> " by ${note.by.value}, mulligan ${note.mulligan}"
        })
        notes += note
    }

    private fun confirmable(): Boolean = !final() && me !in model.table.resultOks

    /** TRN-065: the result is final: both OKs stand on what the rules gave, or a player's own action ended the game. */
    private fun final(): Boolean = endedBy() != null || players.all { it in model.table.resultOks }

    /** Whether the shown state is a final result. */
    private fun isFinal(): Boolean = Engine.result(model.engine) != null && final()

    /** The concession on the line, if any; the player's own still on its way counts (TRN-065, owner 2026-11-09: their table
     * shows them waiting for the other player's OK at once, never an OK of their own to press). */
    private fun concession(): PlayerId? = (session.events + session.pending)
        .firstNotNullOfOrNull { e -> e.engine?.steps.orEmpty().firstNotNullOfOrNull { (it.input as? Input.Concede)?.player } }

    /** The event accepting the other player's suggestion (TRN-047), if any; the player's own still on its way counts. */
    private fun agreement(): GameEvent? =
        (session.events + session.pending).lastOrNull { e -> e.engine?.steps.orEmpty().any { it.input is Input.AgreedResult } }

    /** TRN-065 (owner 2026-11-07): who made the result final by their own action: the conceder, or the player who accepted a
     * suggestion (it ends the game at once, as a concession does). */
    private fun endedBy(): PlayerId? = concession() ?: agreement()?.let { PlayerId(it.author) }

    /** How the game reported [result] on [s] ended (LOB-126's endings). */
    private fun ending(s: GameState, result: GameResult): org.asyncttwiz.protocol.MatchEnding {
        concession()?.let { return org.asyncttwiz.protocol.MatchEnding.Conceded(it.value) }
        agreement()?.let { e -> return org.asyncttwiz.protocol.MatchEnding.Agreed(
            e.table.firstNotNullOfOrNull { (it as? TableChange.Suggestion)?.before?.note }.orEmpty()) }
        if (result is GameResult.Draw) return org.asyncttwiz.protocol.MatchEnding.Draw(
            loop = s.players.any { it.loss == arcanum.engine.setup.LossReason.Loop })
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
         * decision identity, owner 2026-11-05): the record of what happened ([arcanum.engine.Inspect.events]), the timestamp counter and
         * the order of the libraries' unknown parts (a new line's reshuffle, §9; the places a player knows are compared).
         * Any other change (a new Stack object, another step, a chosen target gone, a hidden card shown) is a different
         * decision: the player never saw that point.
         */
        fun sameDecision(seen: GameState, now: GameState): Boolean = seen === now || sameDecisionPoint(seen, now)

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

        /**
         * STK-213: whether [player] cast a spell or activated an ability of their own (their real input, not assumed) since the
         * current step began, on [line] up to its last event: the newest events first, until the step's beginning (a step
         * change among an event's changes) or [ACTED_LOOKBACK] events.
         */
        fun actedInStep(line: List<GameEvent>, player: PlayerId, building: List<EngineStep> = emptyList(), builder: String? = null): Boolean {
            /** True: acted; false: the step began; null: nothing in [steps] decides it. */
            fun scan(steps: List<EngineStep>, author: String?): Boolean? {
                for (step in steps.asReversed()) {
                    if (step.changes.any { it is arcanum.engine.change.Change.Step && (it.before.step != it.after.step ||
                            it.before.number != it.after.number) }) return false
                    val input = step.input ?: continue
                    val by = when (input) { is Input.Cast -> input.player; is Input.Activate -> input.player; else -> null } ?: continue
                    if (by == player && step.assumedBy == null && author == player.value) return true
                }
                return null
            }
            scan(building, builder)?.let { return it }
            for ((n, event) in line.asReversed().withIndex()) {
                if (n >= ACTED_LOOKBACK) return false
                scan(event.engine?.steps ?: continue, event.author)?.let { return it }
            }
            return false
        }

        /** How far back [actedInStep] looks: a step holds few events. */
        private const val ACTED_LOOKBACK = 200

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
         * HIS-110: the reveals in [event] ([arcanum.engine.change.GameEvent.Revealed]: shown to every player, CR 701.20), one
         * per reveal in order, with what made each: the source the change carries (a spell's or ability's instructions), else
         * the card cast or the ability's source of the step's input (a cost). HIS-114: with them, in the same order, the
         * cards that left a public pile for a hidden zone ([leftPiles]), whoever moved them (the caller tells them apart).
         */
        fun revealsIn(event: GameEvent, items: (ObjectId) -> arcanum.engine.state.StackItem? = { stackItemIn(event, it) }): List<Reveal> {
            val all = event.engine?.steps.orEmpty().flatMap { step ->
                step.changes.flatMap { change ->
                    if (change is arcanum.engine.change.Change.ZoneChange) leftPiles(event, step, change, items) + libraryExiles(event, step, change, items)
                    else listOfNotNull(revealOf(event, step, change, items))
                }
            }
            // HIS-233: a card the event also revealed ("reveal cards until …, exile them") shows once, in its reveal
            val revealed = all.filter { it.left == null && it.exiled == null }.flatMap { it.cards }.toSet()
            if (revealed.isEmpty()) return all
            return all.mapNotNull { r -> if (r.exiled == null) r else r.copy(cards = r.cards - revealed).takeIf { it.cards.isNotEmpty() } }
        }

        /**
         * HIS-234 (owner 2026-11-19, N18): what an event's text adds for the cards [event] exiled face up from a library, one line
         * per library, from [viewer]'s side ([names] by account): "Exiled from Bob's library: Plains, Mountain", "Exiled from your
         * library: …". The cards are public once there, so every player reads them (HIS-062). Empty when it exiled none.
         */
        fun libraryExileTexts(event: GameEvent, viewer: String, names: Map<String, String>): List<String> {
            val moves = event.engine?.changes.orEmpty().filterIsInstance<arcanum.engine.change.Change.ZoneChange>().flatMap { it.moves }
                .filter { it.from == arcanum.api.Zone.Library && it.to == arcanum.api.Zone.Exile && !it.after.faceDown && !it.before.token }
            return moves.groupBy { it.before.owner }.map { (owner, ms) ->
                val whose = if (owner.value == viewer) "your" else "${names[owner.value] ?: "the other player"}'s"
                "Exiled from $whose library: " + ms.joinToString { m -> m.after.definition?.faces?.getOrNull(m.after.face)?.name ?: "a card" }
            }
        }

        /**
         * HIS-233 (owner 2026-11-19, N18): the cards [change] of [step] in [event] exiled face up from a library, one [Reveal] per
         * player who moved them and library owner, with [Reveal.exiled]. Who moved them as [leftPiles] says. Tokens are left out.
         */
        private fun libraryExiles(event: GameEvent, step: EngineStep, change: arcanum.engine.change.Change.ZoneChange,
            items: (ObjectId) -> arcanum.engine.state.StackItem?): List<Reveal> {
            val inputPlayer = when (val input = step.input) {
                is Input.Cast -> input.player
                is Input.Activate -> input.player
                else -> null
            }
            return change.moves.filter { m -> m.from == arcanum.api.Zone.Library && m.to == arcanum.api.Zone.Exile && !m.after.faceDown && !m.before.token }
                .groupBy { m -> (change.by?.controller ?: inputPlayer ?: m.before.owner) to m.before.owner }
                .map { (key, moves) ->
                    Reveal(event.id, key.first, moves.map { it.before.card }.distinct(), sourceOf(step, change.by), change.by?.name, exiled = LibraryExile(key.second),
                        item = change.by?.item?.let(items))
                }
        }

        /** HIS-110: the reveal [change] of [step] in [event] is, or null. */
        private fun revealOf(event: GameEvent, step: EngineStep, change: arcanum.engine.change.Change, items: (ObjectId) -> arcanum.engine.state.StackItem?): Reveal? {
            val happened = change as? arcanum.engine.change.Change.Happened ?: return null
            val revealed = happened.event as? arcanum.engine.change.GameEvent.Revealed ?: return null
            val cards = revealed.ids.mapNotNull { (it as? ObjectId.Card)?.card }.distinct()
            if (cards.isEmpty()) return null
            return Reveal(event.id, revealed.player, cards, sourceOf(step, happened.by), happened.by?.name, item = happened.by?.item?.let(items))
        }

        /** STK-181: the Stack item [id] as [event]'s changes have it (put on the Stack, changed or removed there), or null. */
        fun stackItemIn(event: GameEvent, id: ObjectId): arcanum.engine.state.StackItem? =
            event.engine?.changes.orEmpty().asReversed().firstNotNullOfOrNull { c ->
                when (c) {
                    is arcanum.engine.change.Change.StackItemAdded -> c.item.takeIf { it.id == id }
                    is arcanum.engine.change.Change.StackItemRemoved -> c.item.takeIf { it.id == id }
                    is arcanum.engine.change.Change.StackItemUpdated -> c.after.takeIf { it.id == id }
                    else -> null
                }
            }

        /** What made a change of [step] that says [by]: its source card, else the card cast or the ability's source of the
         * step's input (a cost); null when none is a card. */
        private fun sourceOf(step: EngineStep, by: arcanum.engine.change.CausedBy?): arcanum.engine.state.CardId? {
            val source = by?.source ?: when (val input = step.input) {
                is Input.Cast -> input.card
                is Input.Activate -> input.source
                else -> null
            }
            return (source as? ObjectId.Card)?.card
        }

        /**
         * HIS-114 (owner playtest 2026-11-09): the cards [change] of [step] in [event] moved out of a public pile (a graveyard,
         * or exile face up) into a hidden zone (a hand, a library), one [Reveal] per player who moved them, pile and zone,
         * with [Reveal.left]. Who moved them: the controller of what made the move, else the player of the step's input
         * (a cost), else the card's owner. Tokens are left out (they cease to exist there).
         */
        private fun leftPiles(event: GameEvent, step: EngineStep, change: arcanum.engine.change.Change.ZoneChange,
            items: (ObjectId) -> arcanum.engine.state.StackItem?): List<Reveal> {
            val inputPlayer = when (val input = step.input) {
                is Input.Cast -> input.player
                is Input.Activate -> input.player
                else -> null
            }
            return change.moves.filter { m ->
                (m.from == arcanum.api.Zone.Graveyard || m.from == arcanum.api.Zone.Exile) && !m.before.faceDown && !m.before.token &&
                    (m.to == arcanum.api.Zone.Hand || m.to == arcanum.api.Zone.Library)
            }.groupBy { m -> Triple(change.by?.controller ?: inputPlayer ?: m.before.owner, m.from to m.to, m.after.owner) }
                .map { (key, moves) ->
                    val (mover, zones, owner) = key
                    Reveal(event.id, mover, moves.map { it.before.card }.distinct(), sourceOf(step, change.by), change.by?.name,
                        LeftPile(zones.first, zones.second, owner), item = change.by?.item?.let(items))
                }
        }

        /**
         * HIS-112: the plays [viewer]'s table shows of [event], in the order they were put on the Stack: every spell cast,
         * ability activated (mana abilities never use the Stack) and trigger of the other player's, and [viewer]'s own
         * triggers; and every copy of a spell or ability, whoever's (D6, owner 2026-11-19), flying in from what copied it; and
         * every special action of the other player's (owner, 2026-11-20: [PlayKind.Special], in its step's place).
         */
        fun showsIn(event: GameEvent, viewer: PlayerId): List<PlayShow> = momentsIn(event, viewer).filterIsInstance<PlayShow>()

        /**
         * HIS-112, HIS-113: what holds [viewer]'s view in [event], in the order it happened: the plays shown ([showsIn]) and
         * each combat damage step that deals damage (owner 2026-11-25: declaring attackers holds nothing).
         */
        fun momentsIn(event: GameEvent, viewer: PlayerId): List<ViewMoment> {
            val moments = ArrayList<ViewMoment>()
            var damageStep = false
            // HIS-112 (owner 2026-11-12): where each spell was cast from, for its flight in
            val castFrom = HashMap<ObjectId, arcanum.api.Zone>()
            // D6: what copied each copy, for its flight in
            val copiers = event.engine?.changes.orEmpty().mapNotNull { (it as? arcanum.engine.change.Change.Happened)?.event as? arcanum.engine.change.GameEvent.Copied }
                .associate { it.copy to it.by }
            // HIS-112 (owner, 2026-11-20): the activations that were special actions: never put on the Stack, so no activation
            // was recorded for them (CR 116.2; mana abilities record theirs, CR 605)
            val activated = event.engine?.changes.orEmpty().mapNotNullTo(HashSet()) {
                ((it as? arcanum.engine.change.Change.Happened)?.event as? arcanum.engine.change.GameEvent.Activated)?.let { a -> a.source to a.ability }
            }
            val stacked = event.engine?.changes.orEmpty().mapNotNullTo(HashSet()) { c ->
                (c as? arcanum.engine.change.Change.StackItemAdded)?.takeIf { arcanum.engine.change.Meaning.Activated in it.meanings }?.item?.source
            }
            for (step in event.engine?.steps.orEmpty()) {
                val input = step.input as? Input.Activate
                if (input != null && input.player != viewer && (input.source to input.ability.key) !in activated && input.source !in stacked)
                    moments += PlayShow(event.id, input.player, input.source, PlayKind.Special, (input.source as? ObjectId.Card)?.card, ability = input.ability)
                for (change in step.changes) when (change) {
                    is arcanum.engine.change.Change.ZoneChange -> for (move in change.moves) if (move.to == arcanum.api.Zone.Stack)
                        castFrom[move.after.id] = move.from
                    is arcanum.engine.change.Change.StackItemAdded -> showOf(event, change, viewer, castFrom, copiers)?.let(moments::add)
                    is arcanum.engine.change.Change.Step -> damageStep = change.after.step == arcanum.api.TurnStep.CombatDamage
                    is arcanum.engine.change.Change.Damage -> if (damageStep && change.dealt.isNotEmpty()) {
                        moments += CombatHold(event.id, CombatMoment.Damage)
                        damageStep = false
                    }
                    else -> {}
                }
            }
            return moments
        }

        private fun showOf(event: GameEvent, added: arcanum.engine.change.Change.StackItemAdded, viewer: PlayerId,
            castFrom: Map<ObjectId, arcanum.api.Zone>?, copiers: Map<ObjectId, ObjectId?>): PlayShow? {
            if (arcanum.engine.change.Meaning.Copied in added.meanings)
                return PlayShow(event.id, added.item.controller, added.item.id, PlayKind.Copied, (copiers[added.item.id] as? ObjectId.Card)?.card)
            val kind = when {
                arcanum.engine.change.Meaning.Cast in added.meanings -> PlayKind.Cast
                arcanum.engine.change.Meaning.Activated in added.meanings -> PlayKind.Activated
                arcanum.engine.change.Meaning.Triggered in added.meanings -> PlayKind.Triggered
                else -> return null
            }
            val item = added.item
            if (item.controller == viewer && kind != PlayKind.Triggered) return null
            val card = (item.id as? ObjectId.Card)?.card ?: (item.source as? ObjectId.Card)?.card
            return PlayShow(event.id, item.controller, item.id, kind, card, if (kind == PlayKind.Cast) castFrom?.get(item.id) else null)
        }

        /** Whether [a] and [b] are equal but for what never makes a decision another ([sameDecision]): the logged events, the
         * timestamp counter and the order of the libraries ([Host.samePosition]). */
        private fun sameDecisionPoint(a: GameState, b: GameState): Boolean = Host.samePosition(a, b)

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

        /**
         * TRN-027, SYS-135 (merge gate 2026-11-21, e2e CatchUpPlaybackTest): whether [me]'s opening declaration (Keep, Mulligan,
         * the cards a mulligan puts on the bottom) tapped on the board [seen] is still the same decision on [now], the board
         * the device has moved on to: each player's mulligans are their own and both declare at the same time, so the other
         * player's declarations in between (their keep arriving, the device making [me]'s own lost declaration again on it)
         * don't change it. Both in the opening with [me]'s own declaration open on each, and [me]'s opening where it was: the
         * same cards (their library in any order), the same mulligans taken, a bottom choice owed on both or neither. Or
         * [sameDecision].
         */
        fun sameOpening(seen: GameState, now: GameState, me: PlayerId): Boolean = sameDecision(seen, now) ||
            openingOf(seen, me)?.let { it == openingOf(now, me) } == true

        /** [me]'s own opening on [s] as [sameOpening] compares it, or null when [s] asks nothing of [me]'s opening now. */
        private fun openingOf(s: GameState, me: PlayerId): Any? {
            if (s.turn.number != 0) return null
            val pending = s.pending ?: return null
            val stage = (pending.continuation as? arcanum.engine.decision.Continuation.Setup)?.stage as? arcanum.engine.setup.SetupStage.Declare
                ?: return null
            val m = stage.mulligans
            if (me in m.kept) return null
            val open = Host.decider(s, pending.decision.player) == me ||
                Engine.options(s, me, EnginePreferences.of(s)).any { it is Input.SetupAnswer && it.player == me }
            if (!open) return null
            val player = s.player(me).let { it.copy(library = it.library.sortedBy(ObjectId::toString)) }
            return Triple(player, m.taken[me] ?: 0, me in m.taking)
        }

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
                    .also { it.modes(snapshot.modes); it.assumes(snapshot.assumes); it.older = OlderHistory(codec); it.rootId = snapshot.rootId
                        it.lineBranch = snapshot.lineBranch }
            val gone = if (takenBack) snapshot.takenBack.map(codec::decodeEvent) else emptyList()
            if (gone.isNotEmpty() && gone.first().parentId == local) {
                val at = if (local == baseId) 0 else events.indexOfFirst { it.id == local } + 1
                return device(GameSession(base, baseId, events.take(at) + gone, gone.last().id, me)).also {
                    it.branchOnLoad = events.drop(at)
                    if (snapshot.undoneBy != null) it.undoOnLoad = EngineUpdate(target = local, localHeads = snapshot.localHeads,
                        lines = snapshot.lines, rewindRequest = snapshot.rewindRequest, rewoundBy = snapshot.undoneBy, modes = snapshot.modes,
                        lineBranch = snapshot.lineBranch)
                } to codec
            }
            return device(GameSession(base, baseId, events, local, me)) to codec
        }
    }
}
