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
 *   playback stops before an assumed input about the player where their private response mode stops ([Status.Stopped],
 *   [NextStop.stopsAt]): the table is live there, and their answer ([act]) confirms the assumption (its first input the
 *   same, [Acceptance.confirms]: the rest is their next action after it) or branches the game (anything else, a new
 *   line). Elsewhere showing the assumed pass confirms it. Coming back with the other player's events buffered starts
 *   with the catch-up screen ([Status.CatchUp], [play]).
 * - **Local-head reports** ([GameAction.EngineSeen]) go out throttled, at once where playback stops or reaches the head.
 * - **The device's own passes** (§6, §7): at the head, where the player gets priority and their mode doesn't stop
 *   there (or an "End turn" goes on), the device passes for them; such passes after the player's own input fold into its
 *   event, as do engine-automatic answers ([WhoActs.automaticAnswer], public forced passes). Under Auto-skip it also
 *   answers questions with only one answer.
 * - **Assume or wait** (§3, §4) at the other player's points: engine-automatic answers at once; their pauses (judged as
 *   if their Auto-skip were off) assumed at once while they are async, a wait while they are live (green presence, with
 *   [GRACE_MS] grace) unless "Always assume resolves"; every other priority point assumed at once; never at their own
 *   main phase; blockers (async: the Assume blockers screen with "No blocks", OK assumes) and the opening keep (async:
 *   at once); anything else a real wait. An assumption whose outcome would show the player a hidden card, or a random
 *   result (whether it would show one can't be told before the result), is not sent (the information rule, §5): a
 *   wait with no button.
 * - **Branches and rewinds** (§8, §10): when the server's main line changes behind the shown state, the device goes
 *   back to the point and says so ([Note.Branched], [Note.TakenBack]); events on the discarded line are gone.
 * - **Undo** ([undo]): free undo is sent at once, else the caller opens the request dialog ([requestUndo]); the other
 *   player answers ([answerRequest], [requestPreview]); while a request is open the game is frozen for both (no inputs
 *   but a concession; playback goes on).
 * - **Table-only events** ([table]) are made at the head while they apply there, re-made after a stale refusal, else
 *   dropped; staging behind the head stays on the device. Early answers only at the head. A waiting suggestion locks
 *   every other game action once the shown state includes it (TRN-049). The other player's table-only events never
 *   make the player's own events or open draft stale ([GameSession.received]).
 * - **Refusals and drafts:** a refused action of the player's own is told ([Note.Refused]; the device's own passes and
 *   assumptions refused in the expected races are not); a draft dropped because the main line changed under it is told
 *   once ([Note.Refused] with [DRAFT_DROPPED]) and [send] then answers "noDraft".
 *
 * Times are the caller's ([advance], [presence], [take] take `now` in milliseconds), so simulations are deterministic.
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

    /** The player's own private settings for this game (I-D56 §13). */
    var settings: PlaySettings = settings
        private set

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

    /** Whether the catch-up screen shows before playback ([play] leaves it). */
    var catchUp: Boolean = session.buffered.any { it.author != session.me.value }
        private set

    private var live = false
    private var lastLive: Long? = null
    private var now = 0L
    private var wait: WaitState? = null
    private var endTurn: Int? = null
    private var blockersClosed: String? = null
    private val reveals = mutableMapOf<String, Boolean>()
    private val retry = mutableListOf<Retry>()
    /** The player's own opening declarations (Keep, Mulligan, a bottom choice) the server took the other player's
     * declaration before (both declare at the same time, TRN-027): made again on it ([redeclare]). */
    private val redeclaring = mutableListOf<arcanum.engine.decision.Answer>()
    /** The events of those declarations: their "stale" refusal, still to come, isn't told. */
    private val redeclared = mutableSetOf<String>()
    /** The player's own inputs (and table changes) of each of their recent events, as their action made them, before
     * what followed by itself: made again when the server took the other player's event first ([lostMoves]). */
    private val made = LinkedHashMap<String, Pair<List<Input>, List<TableChange>>>()
    /** The player's own moves the server took the other player's event before, in order, to be made again on it when
     * the same decision still waits for them there ([remake]). */
    private val remaking = mutableListOf<Lost>()
    /** The events of those moves: their "stale" refusal isn't told ([remake] tells it when it can't make one again). */
    private val lost = mutableSetOf<String>()
    private val confirming = mutableSetOf<String>()
    /** The submissions made by the player's own actions (a refusal of one is told, even "stale"). */
    private val byPlayer = mutableSetOf<String>()
    /** Who made this device's own recent events (I-D56 §10, report C9PVLM): true for the player's own action (an End
     * turn's first pass included), false for the device's automatic steps; Undo goes back past the automatic ones. */
    private val origins = LinkedHashMap<String, Boolean>()
    /** An End turn waits for its first event: the player's action. */
    private var endTurnMade = false
    /** The head a free undo of this player's landed on: the device makes none of their own inputs there by itself
     * (their mode's passes would carry the game straight past the point again); they act there themselves. */
    private var holdAt: String? = null
    private val outbox = mutableListOf<GameAction>()
    private val notes = mutableListOf<Note>()
    private var reported: String? = null
    private var reportedAt = Long.MIN_VALUE / 2
    private val record = ArrayDeque<String>()

    private class WaitState(val key: String, val beganLive: Boolean)
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
        /** Waiting for the other player: [reason] for the waiting bar, [offer] its Assume button (null: none);
         * [blockersScreen]: the Assume blockers screen is open (async, "No blocks" pre-filled). */
        data class Waiting(val reason: Wait, val offer: Offer? = null, val blockersScreen: Boolean = false) : Status
        /** The game's [result] (from the shown state): [confirmable] once both local heads reached it (OK, TRN-065);
         * [final] once confirmed, or after a concession. */
        data class Over(val result: GameResult, val confirmable: Boolean, val final: Boolean) : Status
        /** Nothing to do (before the game starts). */
        data object Idle : Status
    }

    /** Why the device waits for the other player (the waiting bar, §14). */
    sealed interface Wait {
        /** They may respond to [to] (yours, on top of the Stack). */
        data class Respond(val to: ObjectId) : Wait
        /** They may act at their pause in [step] (your attack, blocks, your end step). */
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
        data object AssumeKeep : Offer
    }

    /** Something to tell the player once. */
    sealed interface Note {
        /** [by] answered differently than assumed ([discarded]'s first event): your later actions were taken back. */
        data class Branched(val by: PlayerId, val discarded: GameEvent, val takenBack: List<GameEvent>) : Note
        /** [by] took the main line back past what you were shown ([takenBack]). */
        data class TakenBack(val by: PlayerId, val takenBack: List<GameEvent>) : Note
        /** Taking back to [target] needs the other player's approval (an undo request). */
        data class RequestNeeded(val target: String) : Note
        /** The server refused something for [reason]. */
        data class Refused(val reason: String) : Note
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
        if (session.drafting) return Status.Drafting
        if (session.behind) {
            if (catchUp) return Status.CatchUp
            val next = session.buffered.first()
            return stopAt(next)?.let { Status.Stopped(next, it, paused = stopAt(next, settings.mode) == null) } ?: Status.Playing(next)
        }
        val s = model.engine
        Engine.result(s)?.let { return Status.Over(it, confirmable(), final()) }
        if (request != null) return Status.Waiting(Wait.Request)
        if (locked) return Status.Waiting(Wait.Suggestion)
        val who = Attention.of(s) ?: return Status.Idle
        if (who == me || declares(s)) return Status.YourMove
        return when (val point = point(s)) {
            is Point.Wait -> Status.Waiting(point.reason, point.offer, point.screen)
            else -> Status.Waiting(Wait.Sending)
        }
    }

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

    /** Leaves the catch-up screen: playback starts. */
    fun play() { catchUp = false }

    /**
     * The player paused playback (§27, the play/pause button): it goes on to the next point where Full control would stop
     * (an assumption about them where they could have acted) and stops there ([Status.Stopped.paused]); their answer
     * there, or playing again, ends the pause. Personal: nothing is sent.
     */
    var pausing: Boolean = false
        set(value) { field = value && session.behind }

    /** Shows the next buffered event unless playback stops before it: the event shown, or null. */
    fun step(): GameEvent? {
        if (needsReload || catchUp || session.drafting || !session.behind) return null
        if (stopAt(session.buffered.first()) != null) return null
        return session.showNext().also { if (!session.behind) pausing = false }
    }

    /** "Return to current" and Back while behind (§7): plays on to the next decision point, never past it. */
    fun playToStop(): List<GameEvent> {
        catchUp = false
        pausing = false
        return generateSequence { step() }.toList()
    }

    /** The assumed input of [event] where this player's playback stops before it under [mode], else null (§7); while
     * paused ([pausing]) wherever Full control stops (§27). */
    private fun stopAt(event: GameEvent, mode: ResponseMode = if (pausing) ResponseMode.FullControl else settings.mode): Input? {
        val step = event.assumed ?: return null
        if (step.assumedBy == me || Acceptance.assumedAbout(step, event, players) != me) return null
        return step.input?.takeIf { NextStop.stopsAt(model.engine, it, me, mode) }
    }

    // ---- The device's own steps

    /**
     * Makes the events the device makes by itself at the head (its player's passes by their mode, engine-automatic
     * answers, assumptions), until the game waits for a player or the server, and re-makes table-only events refused as
     * stale. [now]: the time, for the live wait's grace.
     */
    fun advance(now: Long) {
        this.now = now
        retryTable()
        redeclare()
        steps()
        // The player's own moves lost in a race, compared where the device's own steps leave them, then what follows them
        if (remake()) steps()
    }

    private fun steps() { repeat(MAX_AUTOMATIC_EVENTS) { if (!automaticStep()) return } }

    private fun automaticStep(): Boolean {
        if (needsReload || session.drafting || session.behind || request != null || locked) return false
        val s = model.engine
        if (Engine.result(s) != null) return false
        val who = Attention.of(s) ?: return false
        if (who == me || declares(s)) {
            if (holdAt == session.tip) return false
            val input = own(s) ?: return false
            if (reveals["${session.tip}:$input"] == true) return false
            return make(input, assumed = false) is Acted.Sent
        }
        return when (val point = point(s)) {
            is Point.Automatic -> make(point.input, assumed = false) is Acted.Sent
            is Point.Assume -> make(point.input, assumed = true) is Acted.Sent
            is Point.Wait -> false
        }
    }

    /** Ends the turn for this player (TRN-057): their device passes their priority until the turn ends, declaring no
     * attackers; the other player's points are assumed, automatic or waited for as usual. */
    fun endTurn(now: Long) {
        endTurn = model.engine.turn.number
        endTurnMade = true
        holdAt = null
        advance(now)
        endTurnMade = false
    }

    /** The player's own input the device makes at the head by itself, or null (§6, §7). */
    private fun own(s: GameState): Input? {
        val d = s.pending?.decision
        if (d != null) {
            if (d is Decision.Random || Engine.decider(s, d.player) != me) return null
            return WhoActs.automaticAnswer(model, d) ?: oneAnswer(s, d)
        }
        val holder = s.priority.holder
        if (holder != null) {
            // One search of the player's options answers both (the engine's own pass and their mode's)
            val check = Engine.passCheck(s, holder)
            return Input.PassPriority(holder).takeIf { (check.asSet && !Stops.stop(s, holder)) || modePasses(s, holder, check) }
        }
        if (endTurn == s.turn.number && s.turn.active == me)
            return Engine.options(s, me).firstOrNull { it is Input.DeclareAttackers && it.attacks.isEmpty() }
        return null
    }

    /** Under Auto-skip, the only answer to [d] (§2). */
    private fun oneAnswer(s: GameState, d: Decision): Input? = if (settings.mode != ResponseMode.AutoSkip) null
        else Engine.options(s, d.player).filterIsInstance<Input.Answer>().filter { it.decision == d.id }.singleOrNull()

    /** Whether this player's device passes for [player] (theirs to decide) on [s] by their mode (§2) or an End turn. */
    private fun modePasses(s: GameState, player: PlayerId, check: arcanum.engine.PassCheck? = null): Boolean {
        if (endTurn == s.turn.number) return true
        val point = Stops.stop(s, player) || Stops.pause(s, player)
        return when (settings.mode) {
            ResponseMode.FullControl -> false
            ResponseMode.Normal -> !point
            // Never at their own main phases (owner, smoke 2026-11-04: the turn is never passed away without them, STK-036)
            ResponseMode.AutoSkip -> !point || (!Stops.stop(s, player) && (check?.ownSkipOn ?: NextStop.passesWithOwnCards(s, player)))
        }
    }

    /** What the device does at the other player's point (§4). */
    private sealed interface Point {
        data class Automatic(val input: Input) : Point
        data class Assume(val input: Input) : Point
        data class Wait(val reason: GameDevice.Wait, val offer: Offer? = null, val input: Input? = null, val screen: Boolean = false) : Point
    }

    private fun point(s: GameState): Point {
        val tip = session.tip
        val d = s.pending?.decision
        if (d != null) {
            if (d !is Decision.Random) WhoActs.automaticAnswer(model, d)?.let { return Point.Automatic(it) }
            val keep = Engine.options(s, d.player).firstOrNull { keep(it) }
            if (keep != null && assumable(keep)) {
                if (revealing(keep)) return Point.Wait(Wait.HiddenCards)
                return if (liveWait("$tip:keep")) Point.Wait(Wait.Keep, Offer.AssumeKeep, keep) else Point.Assume(keep)
            }
            return Point.Wait(Wait.Choosing(d.source?.takeIf { it in Engine.visible(s, me) }))
        }
        val holder = s.priority.holder
        if (holder != null) {
            if (Stops.forcedPass(s, holder)) return Point.Automatic(Input.PassPriority(holder))
            val pass = Input.PassPriority(holder)
            if (Stops.stop(s, holder) || !assumable(pass)) return Point.Wait(Wait.TheirTurn)
            if (revealing(pass)) return Point.Wait(Wait.HiddenCards)
            if (!Stops.pause(s, holder)) return Point.Assume(pass)
            val top = s.stack.lastOrNull()
            val reason = if (top != null) Wait.Respond(top) else Wait.MayAct(s.turn.step)
            return if (!settings.alwaysAssumeResolves && liveWait("$tip:pass")) Point.Wait(reason, Offer.AssumeResolves, pass) else Point.Assume(pass)
        }
        val blocks = Engine.options(s, opponent).filterIsInstance<Input.DeclareBlockers>()
        if (blocks.isNotEmpty()) {
            val none = Input.DeclareBlockers(opponent, emptyList())
            if (!assumable(none)) return Point.Wait(Wait.Blockers)
            val live = liveWait("$tip:blocks")
            return Point.Wait(Wait.Blockers, Offer.AssumeBlockers, none, screen = !live && blockersClosed != tip)
        }
        return Point.Wait(Wait.TheirTurn)
    }

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
        val before = model.engine.deepCopy()
        val builder = open()
        val shows = try {
            builder.perform(input, assumedBy = me)
            builder.answerRandoms()
            fold(builder, own = false)
            shows(before, builder)
        } catch (_: RuntimeException) {
            false
        } finally {
            session.abandon()
        }
        reveals[key] = shows
        return shows
    }

    /**
     * Whether a wait for the live other player at point [key] goes on (§4): it began while they were live and they are
     * still live, or not live for less than [GRACE_MS]; a point first met while they aren't live is never a wait.
     */
    private fun liveWait(key: String): Boolean {
        val w = wait
        if (w?.key == key) return w.beganLive && (live || (lastLive?.let { now - it < GRACE_MS } ?: false))
        wait = WaitState(key, live)
        return live
    }

    /** The other player's presence (TRN-042's green dot: [live]) at [now]. */
    fun presence(live: Boolean, now: Long) {
        if (live || this.live) lastLive = now
        this.live = live
        this.now = now
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
        follow: (GameModel) -> Input? = { null }): Acted = act(input, table, follow, null, performed)

    private fun act(input: Input, table: List<TableChange>, follow: (GameModel) -> Input?, replay: ArrayDeque<Input>?,
        performed: (() -> Unit)? = null): Acted {
        refusal(input)?.let { return Acted.Refused(it) }
        if (!session.behind) return make(input, assumed = false, follow = follow, table = table, player = true, performed = performed)
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
        if (refusal(null) != null) return null
        val stop = if (session.behind) status as? Status.Stopped ?: return null else null
        draftBefore = if (tentative()) model.engine.deepCopy() else null
        val builder = open()
        openDraft = builder
        draftStop = stop
        return builder
    }

    /** Sends the open draft with what follows it by itself (the player's passes by their mode, automatic answers). */
    fun send(): Acted {
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
    private var draftBefore: GameState? = null

    /** Opens a draft at the tip: starting [newLine] (a branch), or the line a rewind left due at the head (§9). */
    private fun open(newLine: Int? = null): EventBuilder =
        session.begin(ids(), newLine ?: if (session.behind) null else Rewind.lineDue(model, lines))

    /** Makes [input] (assumed: the other player's, §3) as an event at the tip, or at the local head with [newLine]. */
    private fun make(input: Input, assumed: Boolean, follow: (GameModel) -> Input? = { null }, table: List<TableChange> = emptyList(),
        newLine: Int? = null, player: Boolean = false, performed: (() -> Unit)? = null): Acted {
        // The server's rules (§6): the player's own inputs (or engine-automatic answers), the other player's only assumed
        try {
            val kind = WhoActs.kind(model, input, me, assumed)
            if (kind == WhoActs.Kind.Random) return Acted.Refused("Not the player's input")
        } catch (e: Refusal) { return Acted.Refused(e.reason) }
        val before = if (assumed || tentative()) model.engine.deepCopy() else null
        val builder = open(newLine)
        try {
            builder.perform(input, assumedBy = if (assumed) me else null)
            builder.answerRandoms()
            if (!assumed) while (true) {
                val next = follow(model) ?: break
                builder.perform(next)
                builder.answerRandoms()
            }
            table.forEach(builder::table)
        } catch (e: RuntimeException) {
            session.abandon()
            return Acted.Refused(e.message ?: "refusedByEngine")
        }
        // SYS-025: what is made so far may be shown now, unless it alone would show this player something the information
        // rule holds back (then the whole event is refused, before anything shows). What follows by itself can't refuse
        // it by that rule any more: the player's own passes stop before one that would show something ([fold])
        if (performed != null && (before == null || !shows(before, builder))) performed()
        return finish(builder, own = !assumed, before = before, player = player)
    }

    /** Folds what follows by itself into [builder]'s event and sends it, unless it would show this player a hidden card
     * or a random result while [before] is given (the state before it: their line is tentative, or the event assumes):
     * then the draft is taken back unsent (§5). [player]: made by the player's own action (a refusal is told). */
    private fun finish(builder: EventBuilder, own: Boolean, before: GameState?, player: Boolean = false): Acted {
        val inputs = if (player) builder.playerInputs to builder.tableChanges else null
        try { fold(builder, own, guard = before) } catch (e: RuntimeException) { abandon(); return Acted.Refused(e.message ?: "refusedByEngine") }
        // I-D56 §5: while the line is tentative (or this assumes), nothing may show this player a hidden card
        if (before != null && shows(before, builder)) {
            val first = builder.inputs.firstOrNull()
            abandon()
            reveals["${session.tip}:$first"] = true
            return Acted.Refused(REVEALS_HIDDEN)
        }
        openDraft = null; draftStop = null; draftBefore = null
        val event = session.sendEvent()
        if (player) byPlayer += event.id
        if (inputs != null && inputs.first.isNotEmpty()) {
            made[event.id] = inputs
            if (made.size > MAX_ORIGINS) made.remove(made.keys.first())
        }
        if (own) {
            val first = event.engine?.steps?.firstNotNullOfOrNull { it.input }
            val ended = endTurnMade && (first == Input.PassPriority(me) || (first as? Input.DeclareAttackers)?.player == me)
            origins[event.id] = player || ended
            if (ended) endTurnMade = false
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
    private fun shows(before: GameState, builder: EventBuilder): Boolean =
        InformationRule.revealing(before, builder.changes, me).isNotEmpty() || InformationRule.randomResult(builder.engineSteps, before)

    /**
     * Folds what follows the event's input by itself into it (I-D35, §6): random answers, engine-automatic answers,
     * public forced passes of either player, and with [own] the player's own passes by their mode and (Auto-skip) their
     * one-answer questions. Stops at anything else, at the game's end, after a whole round of turns at the next stop,
     * at a loop of the player's own yields, and turns a loop of forced passes into the engine's loop choice (CR 104.4b).
     * With [guard] (the state before the event, on a tentative line) it also stops before a pass of the player's own that
     * would show them something the information rule holds back (§5): the pass is taken back and the player holds
     * priority there, as their device's own steps leave it ([automaticStep]), instead of the whole action being refused.
     */
    private fun fold(builder: EventBuilder, own: Boolean, guard: GameState? = null) {
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
            // One search of the holder's options answers both the forced pass and their own mode's (playtest 2026-11-04)
            val check = Engine.passCheck(s, holder)
            val forced = check.asSet && !Stops.stop(s, holder)
            val yields = mine && !forced && modePasses(s, holder, check)
            if (!forced && !yields) return
            if (s.turn.number >= start + 2 && Stops.stop(s, s.turn.active)) return
            if (yields && !yielded.add(Engine.loopKey(s))) return
            if (!forced) forcedSeen.clear()
            else if (s.stack.isNotEmpty() && !forcedSeen.add(Engine.loopKey(s))) {
                builder.perform(Input.MandatoryLoop)
                return@repeat
            }
            val mark = builder.mark()
            builder.perform(Input.PassPriority(holder))
            builder.answerRandoms()
            if (yields && guard != null && shows(guard, builder)) {
                builder.takeBack(mark)
                return
            }
        }
    }

    /** Assumes the other player's answer offered on the waiting bar (§4): their pass, their keep, or [blocks] (the
     * Assume blockers screen's, "No blocks" when empty). */
    fun assume(blocks: List<Block> = emptyList()): Acted {
        refusal(null)?.let { return Acted.Refused(it) }
        if (session.behind) return Acted.Refused("playback")
        val point = point(model.engine) as? Point.Wait ?: return Acted.Refused("noOffer")
        val input = when (point.offer) {
            null -> return Acted.Refused("noOffer")
            Offer.AssumeBlockers -> Input.DeclareBlockers(opponent, blocks)
            else -> point.input!!
        }
        return make(input, assumed = true)
    }

    /** Back on the Assume blockers screen: the point stays a wait (the waiting bar still offers Assume blockers). */
    fun closeBlockersScreen() { blockersClosed = session.tip }

    /**
     * Table-only [changes] (placing your own cards, early answers, suggestions, confirming the result): made at the head
     * while they apply there (§6), shown at once when the player is at the head, else when playback reaches them;
     * staging (the attack and block drafts) behind the head stays on the device ([Acted.Kept]).
     */
    fun table(changes: List<TableChange>): Acted {
        if (needsReload) return Acted.Refused("reload")
        if (request != null) return Acted.Refused(REWIND_REQUESTED)
        if (locked && changes.any { it !is TableChange.Suggestion && it !is TableChange.ResultConfirmed }) return Acted.Refused("suggestion")
        if (session.behind && changes.any { it is TableChange.AttackDraft || it is TableChange.BlockDraft }) return Acted.Kept
        if (session.behind && changes.any { it is TableChange.EarlyAnswered }) return Acted.Refused("behind")
        if (session.drafting) return Acted.Refused("drafting")
        if (!session.behind) {
            val builder = open()
            try { changes.forEach(builder::table) } catch (e: RuntimeException) { session.abandon(); return Acted.Refused("inapplicable") }
            val event = session.sendEvent()
            send(GameAction.Submit(Submission.of(event)))
            return Acted.Sent(event)
        }
        // Made at the head, with the values there as the before-values (the player sees the board at their local head)
        val model = session.headModel()
        val again = rebase(model, changes)
        if (again == null || !applies(model, again)) return Acted.Refused("inapplicable")
        val builder = EventBuilder(model, ids(), session.head, me.value)
        Rewind.lineDue(model, lines)?.let(builder::startLine)
        try { again.forEach(builder::table) } catch (e: RuntimeException) { return Acted.Refused("inapplicable") }
        val event = builder.build()
        send(GameAction.Submit(Submission.of(event)))
        return Acted.Sent(event)
    }

    /** Whether [changes] still apply on [model] (a placed card still on the battlefield). */
    private fun applies(model: GameModel, changes: List<TableChange>): Boolean =
        changes.all { it !is TableChange.Placed || it.after == null || it.obj in model.engine.battlefield }

    private fun retryTable() {
        if (retry.isEmpty() || session.drafting || needsReload) return
        val ready = retry.filter { it.parent != session.head }
        retry.removeAll(ready)
        for (r in ready) {
            val at = if (session.behind) session.headModel() else model.copy()
            val fixed = rebase(at, r.table) ?: continue
            if (applies(at, fixed)) table(fixed)
        }
    }

    /** [changes] made again on [model]'s table: each with the value there as its before-value, or null when one no
     * longer applies (an early answer's decision gone: its before-value is whatever is there now). */
    private fun rebase(model: GameModel, changes: List<TableChange>): List<TableChange>? = changes.map { c ->
        val t = model.table
        when (c) {
            is TableChange.Placed -> c.copy(before = t.placements[c.obj])
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

    /** Suggests ending the game with [result] (TRN-046), at the head. */
    fun suggest(result: GameResult): Acted = table(listOf(TableChange.Suggestion(model.table.suggestion, ResultSuggestion(me, result))))

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

    /** The player's private settings for this game (§7): they apply to the points not yet played back. */
    fun settings(new: PlaySettings) {
        if (new == settings) return
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
    }

    /** The point just before the player's latest own real input shown (their later assumed inputs go with it): never
     * the device's automatic steps (its passes by the player's mode, engine-automatic answers it made) nor a rewind's
     * line start, so a second Undo goes on back past the player's previous action (report C9PVLM). */
    fun undoTarget(): String? {
        val shown = session.indexOf(session.localHead) ?: return null
        return session.events.take(shown + 1).lastOrNull(::realInput)?.parentId
    }

    /** Whether [e] is one of this player's own real inputs (an action of theirs, or a table-only event of theirs). Events
     * made before this device was loaded are judged by their input: the player's own passes and inputs for the other
     * player there are taken as automatic. */
    private fun realInput(e: GameEvent): Boolean {
        if (e.author != me.value || e.assumed != null) return false
        val input = e.engine?.steps?.firstNotNullOfOrNull { it.input }
        if (input == null) return e.table.any { it !is TableChange.LineMoved }
        origins[e.id]?.let { return it }
        return when (input) {
            is Input.PassPriority -> false
            is Input.PlayLand -> input.player == me
            is Input.Cast -> input.player == me
            is Input.Activate -> input.player == me
            is Input.DeclareAttackers -> input.player == me
            is Input.DeclareBlockers -> input.player == me
            is Input.SetupAnswer -> input.player == me
            else -> true
        }
    }

    /** Undo (a tap) to [target] (by default [undoTarget]): sent when free, else [Undo.Request]. */
    fun undo(target: String? = undoTarget()): Undo {
        if (target == null) return Undo.Unavailable("nothing")
        if (request != null) return Undo.Unavailable(REWIND_REQUESTED)
        if (session.drafting || session.pending.isNotEmpty()) return Undo.Unavailable("sending")
        val after = session.after(target) ?: return Undo.Request(target, "unknown")
        if (after.isEmpty()) return Undo.Unavailable("nothing")
        val at = session.stateAt(target) ?: return Undo.Request(target, "unknown")
        val reason = Rewind.requestNeeded(at, target, after, me, heads.filterKeys { it != me }.values.toList())
            ?: return Undo.Free(target).also { send(GameAction.EngineRewind(target)) }
        return Undo.Request(target, reason)
    }

    /** Asks the other player to approve taking the line back to just after [target], with [message]. */
    fun requestUndo(target: String, message: String = "") = send(GameAction.EngineRequestRewind(target, message))

    /** The other player's request answered: [accept] takes the line back, else it closes. */
    fun answerRequest(accept: Boolean) = send(GameAction.EngineAnswerRewind(accept))

    /** Takes the player's own open request back. */
    fun cancelRequest() = send(GameAction.EngineCancelRewind)

    /** What the request dialog shows the player asked (§10): the board at the target when they have seen it, else at
     * their local head with [unseen] ("and actions you haven't seen yet"); never how many events go. */
    data class Preview(val board: GameModel, val unseen: Boolean, val message: String)

    fun requestPreview(): Preview? {
        val r = request?.takeIf { it.by != me.value } ?: return null
        if (session.drafting) return null
        val target = session.indexOf(r.target)
        val local = session.indexOf(session.localHead) ?: return null
        val board = if (target != null && target <= local) session.stateAt(r.target) else null
        return Preview(board ?: session.stateAt(session.localHead) ?: return null, board == null, r.message)
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
            when (val r = session.rewound(target, events)) {
                GameSession.Received.Reload -> needsReload = true
                is GameSession.Received.Changed -> if (r.takenBack.isNotEmpty() && by != me.value && by != null)
                    note(Note.TakenBack(PlayerId(by), r.takenBack))
                else -> {}
            }
            // The player's own undo lands where they act next: their device waits for them there (§10)
            holdAt = if (by == me.value) session.head else null
        } else for (event in events) {
            when (val r = session.received(event)) {
                GameSession.Received.Reload -> { needsReload = true; break }
                is GameSession.Received.Changed -> if (r.takenBack.isNotEmpty() && r.discarded.isNotEmpty())
                    note(Note.Branched(PlayerId(event.author), r.discarded.first(), r.takenBack))
                is GameSession.Received.Applied -> lostOpening(r.dropped) || lostMoves(r.dropped) { it in byPlayer }
                else -> {}
            }
        }
        if (update != null) {
            for ((account, head) in update.localHeads) heads[PlayerId(account)] = head
            request = update.rewindRequest
            // The server confirmed where this device branched: it can't follow (the rules agree, so only after a fault)
            if (update.confirmed?.let(session::supersedes) == true) needsReload = true
        }
        events.forEach { byPlayer.remove(it.id) }
        checkDraft()
        if (needsReload) return
        followServer()
        trim()
    }

    /** The server refused [action] for [reason]. */
    fun refused(action: GameAction, reason: String) {
        remember("refused ${action::class.simpleName} $reason")
        when (action) {
            is GameAction.Submit -> {
                val submission = action.submission
                if (confirming.remove(submission.id)) return
                val mine = byPlayer.remove(submission.id)
                val refused = session.refused(submission.id)
                if (refused == GameSession.Received.Reload) needsReload = true
                val again = reason == "stale" &&
                    ((refused is GameSession.Received.Applied && (lostOpening(refused.dropped) ||
                        lostMoves(refused.dropped) { it == submission.id && mine || it in byPlayer })) ||
                        submission.id in redeclared || submission.id in lost)
                redeclared.remove(submission.id)
                lost.remove(submission.id)
                if (submission.acts.isEmpty() && reason == "stale") retry += Retry(submission.table, submission.parentId)
                // The player's own action is never dropped silently (an opening declaration is made again instead); the
                // device's own passes and assumptions lost in the expected races are (I-D56 §8)
                else if ((reason != "stale" || mine) && !again) note(Note.Refused(reason))
                checkDraft()
            }
            is GameAction.EngineRewind -> note(if (reason == "requestNeeded") Note.RequestNeeded(action.target) else Note.Refused(reason))
            is GameAction.EngineSeen -> if (reported == action.eventId) reported = null
            else -> note(Note.Refused(reason))
        }
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
        redeclaring += kept.map { it.second!! }
        kept.forEach { redeclared += it.first.id }
        if (redeclared.size > MAX_ORIGINS) redeclared.clear()
        return kept.isNotEmpty()
    }

    /** A move of the player's own ([inputs], [table]) made on [madeOn] (the engine state shown when they made it) on
     * [parent], which the server didn't take: another event came first. */
    private class Lost(val event: String, val parent: String, val madeOn: GameState, val inputs: List<Input>, val table: List<TableChange>)

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
                remaking += Lost(e.id, dropped.first().parentId, walk.engine.deepCopy(), inputs, table)
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
        if (remaking.isEmpty() || needsReload || session.drafting || request != null) return false
        val first = remaking.first()
        // Wait for the event the server took instead (a refusal can come before it) and for it to be shown
        if (session.head == first.parent || session.behind) return false
        val moves = remaking.toList()
        remaking.clear()
        for ((n, move) in moves.withIndex()) {
            val again = if (!sameDecision(move.madeOn, model.engine)) null else {
                remember("remake ${move.inputs.firstOrNull()}")
                val rest = ArrayDeque(move.inputs.drop(1))
                act(move.inputs.first(), move.table, { rest.removeFirstOrNull() }, null)
            }
            if (again !is Acted.Sent) {
                note(Note.Refused("stale"))
                if (moves.size > n + 1) remember("remake dropped ${moves.size - n - 1} more")
                return n > 0
            }
        }
        return true
    }

    /**
     * Makes the player's opening declarations the server took the other player's before ([lostOpening]) again: the other
     * player's opening declarations shown at once (both declare at the same time, nothing to play back), then each of
     * the player's own made on them as if just tapped (at a stop where the other player assumed it, it confirms it).
     * Dropped when the opening no longer asks it.
     */
    private fun redeclare() {
        if (redeclaring.isEmpty() || needsReload || session.drafting || request != null) return
        while (session.behind && !catchUp && model.engine.turn.number == 0 && stopAt(session.buffered.first()) == null) session.showNext()
        if (session.behind && status !is Status.Stopped) return
        val answers = redeclaring.toList()
        redeclaring.clear()
        for (answer in answers) {
            val input = openingInput(answer) ?: return
            remember("redeclare $answer")
            val acted = act(input)
            if (acted !is Acted.Sent && acted !is Acted.Confirmed) return
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

    /** Forgets events well before both local heads (the device keeps [KEPT_EVENTS] of them for undo and previews). */
    private fun trim() {
        val earliest = listOfNotNull(session.localHead, heads[opponent]).mapNotNull(session::indexOf).minOrNull() ?: return
        val cut = earliest - KEPT_EVENTS
        if (cut >= 0) session.trim(session.events[cut].id)
    }

    /** What the device sends now (drained): its actions in order, and a local-head report when the shown head moved
     * (at once where playback stops or reaches the head, else at most every [SEEN_EVERY_MS]). */
    fun take(now: Long): List<GameAction> {
        this.now = now
        report(now, urgent = !session.behind || status is Status.Stopped)
        return outbox.toList().also { outbox.clear() }
    }

    /** Reports the shown head when it is ahead of the server's (throttled unless [urgent]). */
    private fun report(now: Long, urgent: Boolean) {
        val local = session.localHead
        val server = heads[me]
        val ahead = server == null || (session.indexOf(local) ?: -1) > (session.indexOf(server) ?: -1)
        if (local != reported && local != server && ahead && (urgent || now - reportedAt >= SEEN_EVERY_MS)) {
            remember("out EngineSeen")
            outbox += GameAction.EngineSeen(local)
            reported = local; reportedAt = now
        }
    }

    /** What happened that the player is told once (drained). */
    fun notes(): List<Note> = notes.toList().also { notes.clear() }

    private fun send(action: GameAction) {
        // The server takes assumptions and result confirmations only from a local head it knows (I-D56 §6, §11): the
        // shown head's report goes first
        if (action is GameAction.Submit) report(now, urgent = true)
        remember("out ${action::class.simpleName}${(action as? GameAction.Submit)?.submission?.let { " ${it.id} on ${it.parentId}" } ?: ""}")
        outbox += action
    }

    private fun note(note: Note) {
        remember("note ${note::class.simpleName}")
        notes += note
    }

    private fun confirmable(): Boolean {
        if (session.behind || session.pending.isNotEmpty() || model.table.resultConfirmedBy != null) return false
        return heads.filterKeys { it != me }.values.all { h -> session.after(h)?.all { it.engine == null } == true }
    }

    private fun final(): Boolean = model.table.resultConfirmedBy != null ||
        session.events.any { e -> e.engine?.steps.orEmpty().any { it.input is Input.Concede } }

    // ---- Reproduction (I-D43)

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
        /** The other player's live wait turns async after they have not been live this long (§4). */
        const val GRACE_MS = 60_000L
        /** Local-head reports while playback runs go out at most this often. */
        const val SEEN_EVERY_MS = 2_000L
        /** The refusal of an input that would show the player hidden cards while their line is tentative (§5). */
        const val REVEALS_HIDDEN = Authority.REVEALS_HIDDEN
        const val REWIND_REQUESTED = "rewindRequested"
        /** The note of a draft the main line dropped under it (a branch, a rewind, another event at the head). */
        const val DRAFT_DROPPED = "draftDropped"
        /** Events a device keeps before the earlier local head (undo targets and request previews further back come
         * from the server). */
        const val KEPT_EVENTS = 64
        private const val MAX_ORIGINS = 256
        private const val MAX_AUTOMATIC_EVENTS = 200
        private const val MAX_FOLDED = 500
        private const val MAX_RECORD = 400
        private const val MAX_ENTRY = 300
        private const val MAX_PROBES = 64

        /**
         * SYS-011 (owner, 2026-11-04): whether an own action tapped on the board [seen] is still the same decision on
         * [now], the board the device has moved on to (the other player's event arrived, an assumption was confirmed),
         * so it is made there instead of refused. The same when nothing the game state holds differs: the same turn and
         * step, priority (its holder and the passes in a row), open decision and its options, Stack, combat, every
         * object (zones, what is known of hidden cards, targets) and player; only the record of what happened
         * ([GameState.log]) and the timestamp counter may differ. Any other change (a new Stack object, another step, a
         * chosen target gone, a hidden card shown) is a different decision: the player never saw that point.
         */
        fun sameDecision(seen: GameState, now: GameState): Boolean =
            seen === now || seen.copy(log = mutableListOf(), timestampCounter = 0) == now.copy(log = mutableListOf(), timestampCounter = 0)

        /** A device for [me] from the server's [snapshot] (and the codec of the game's definitions), at their local head
         * with the events past it buffered. */
        fun load(snapshot: EngineSnapshot, me: PlayerId, ids: () -> String): Pair<GameDevice, EngineJson> {
            val codec = EngineJson(EngineJson.decodeDefinitions(snapshot.definitions))
            val baseId = snapshot.baseId ?: snapshot.headId
            val base = codec.decodeModel(snapshot.base ?: snapshot.state)
            val events = snapshot.events.map(codec::decodeEvent)
            val heads = snapshot.localHeads.mapKeys { PlayerId(it.key) }
            val local = heads[me]?.takeIf { it == baseId || events.any { e -> e.id == it } } ?: snapshot.headId
            val session = GameSession(base, baseId, events, local, me)
            return GameDevice(session, snapshot.settings ?: PlaySettings(), heads, snapshot.lines, snapshot.rewindRequest, ids) to codec
        }
    }
}
