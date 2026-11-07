package org.asyncttwiz.server.game

import arcanum.engine.Engine
import arcanum.engine.change.Changes
import arcanum.engine.input.Input
import arcanum.engine.setup.Seat
import arcanum.engine.setup.StartOptions
import arcanum.engine.state.PlayerId
import java.security.SecureRandom
import java.util.UUID
import kotlinx.serialization.builtins.serializer
import org.asyncttwiz.protocol.EngineRewindRequest
import org.asyncttwiz.protocol.EngineSnapshot
import org.asyncttwiz.protocol.EngineUpdate
import org.asyncttwiz.protocol.GameWire
import org.asyncttwiz.protocol.game.Acceptance
import org.asyncttwiz.protocol.game.EventBuilder
import org.asyncttwiz.protocol.game.GameDefinitions
import org.asyncttwiz.protocol.game.GameDevice
import org.asyncttwiz.protocol.game.GameEvent
import org.asyncttwiz.protocol.game.GameHistory
import org.asyncttwiz.protocol.game.GameModel
import org.asyncttwiz.protocol.game.LineRandom
import org.asyncttwiz.protocol.game.LiveState
import org.asyncttwiz.protocol.game.MainLine
import org.asyncttwiz.protocol.game.NextStop
import org.asyncttwiz.protocol.game.PlaySettings
import org.asyncttwiz.protocol.game.Refusal
import org.asyncttwiz.protocol.game.Rewind
import org.asyncttwiz.protocol.game.Submission
import org.asyncttwiz.protocol.game.TableChange
import org.asyncttwiz.protocol.game.TableState
import org.asyncttwiz.server.generated.tables.EngineAsyncRound.ENGINE_ASYNC_ROUND
import org.asyncttwiz.server.generated.tables.EngineEvent.ENGINE_EVENT
import org.asyncttwiz.server.generated.tables.EnginePlay.ENGINE_PLAY
import org.asyncttwiz.server.generated.tables.EngineSeat.ENGINE_SEAT
import org.jooq.DSLContext
import org.jooq.impl.DSL

/**
 * Test seam (I-D54, server tests only; null in the service): the engine seats of a game starting now, by its game id,
 * instead of the ones its decks make ([EngineDecks]).
 */
@Volatile internal var engineSeats: ((gameId: UUID) -> List<Seat>?)? = null

/**
 * How many plays [EngineGames] keeps warm (R6), for a heap of [maxHeap]. Measured (WarmGameHeapTest, 2026-11-05): a kept
 * play holds about 1.8 MiB at turn 40 (state 0.5, its decoded definitions 0.7, 256 cached events 0.6) and up to 3.5 MiB
 * early on (turn 10: the start's events, which carry every card, still cached). Counted at 4 MiB, an eighth of the heap
 * keeps 128 plays (512 MiB) at the Pi's -Xmx4g; never fewer than 32 (a smaller heap, the tests' 512 MiB).
 */
internal fun warmCapacity(maxHeap: Long = Runtime.getRuntime().maxMemory()): Int =
    Math.round(maxHeap / 8.0 / (4L shl 20)).toInt().coerceIn(32, 128)

/** Tests only (R6): a seat a refresh leaves as stored is worked out anyway and checked to be the same. */
@Volatile internal var checkSkipped: Boolean = false

/** The refusals of an engine play's actions that a device tells apart (the rest are "refusedByEngine"). */
internal val ENGINE_REASONS = setOf("stale", "revealsHidden", "rewindRequested", "requestNeeded", "newLine", "noChange",
    "inapplicable", "otherPlayerRequired", "accessDenied")

/**
 * The server's engine for plays whose state is the engine's (I-D54 phase 3, I-D56 step A): it keeps active plays' states
 * warm in memory (the state at the main line's head; a cold play is one checkpoint plus the events after it), takes
 * submissions in arrival order (the caller holds the game's row lock) by [Acceptance] (at the head, a confirmation or a
 * branch at an assumed input about the author, else stale), stores each accepted event ([EngineHistoryStore]), keeps
 * each player's local head, private settings and next stop ([NextStop]) in `engine_seat`, rewinds (free, or approved
 * by the other player: an undo request) and logs async rounds (I-D56 §15).
 *
 * A warm state is taken out while a change is worked on and put back only once its transaction committed ([committed]),
 * so a rolled-back transaction leaves no state ahead of the database; a refused change puts it back at once (nothing
 * changed). Each play's decoded definitions and recent events are kept too ([EngineHistoryStore.Shared]), so the work
 * of a request doesn't grow with how far a player is behind (the main line between two events is read in one go).
 */
/** HIS-106, HIS-108: the most events of a discarded line, or of what the other player's undo took back, a snapshot
 * carries for its viewer's take-back (a longer one opens at the point the game went back to: a plain catch-up after a
 * branch, the board after an undo without its animation). */
private const val MAX_TAKEN_BACK = 200

/** How many of a play's duplicate submissions [EngineGames] keeps (the latest ones: a device's next submissions follow). */
private const val MAX_ALIASES = 16

internal class EngineGames(private val capacity: Int = warmCapacity()) {
    private val warm = object : LinkedHashMap<UUID, LiveState>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<UUID, LiveState>?) = size > capacity
    }
    private val random = SecureRandom()
    /** End-to-end tests only (e2e/): a file holding the next games' random secret (64 hex digits), so a test game deals
     * the hands its test chose. Unset in every deployment: each game's secret is random. */
    private val testSecretFile: java.nio.file.Path? = System.getenv("TEST_GAME_SECRET_FILE")?.takeIf(String::isNotBlank)
        ?.let { java.nio.file.Path.of(it) }

    private fun secret(): ByteArray {
        val fixed = testSecretFile?.takeIf(java.nio.file.Files::isRegularFile)?.let { java.nio.file.Files.readString(it).trim() }
        if (fixed != null) return java.util.HexFormat.of().parseHex(fixed).also { require(it.size == 32) { "TEST_GAME_SECRET_FILE: 32 bytes" } }
        return ByteArray(32).also(random::nextBytes)
    }
    /** Seats whose next stop, wait and resume point were worked out so far (R6: tests count the work of a refresh). */
    internal val seatsWorkedOut = java.util.concurrent.atomic.AtomicLong()
    private val plays = object : LinkedHashMap<UUID, EngineHistoryStore.Shared>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<UUID, EngineHistoryStore.Shared>?) = size > capacity
    }
    /**
     * Owner 2026-11-07 (SYS-077): each play's recent submissions dropped as duplicates of main-line events
     * ([Acceptance.Confirmed.alias]: a player's answer that crossed the other player's assumption of it on the wire), by
     * submission id: their author's later submissions made on one are taken on its event. Kept in memory only (a device's
     * next submissions follow at once); after a restart such a submission is stale, as before.
     */
    private val aliases = object : LinkedHashMap<UUID, LinkedHashMap<String, String>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<UUID, LinkedHashMap<String, String>>?) = size > capacity
    }

    private fun aliases(play: UUID): Map<String, String> = synchronized(aliases) { aliases[play]?.toMap().orEmpty() }

    /** [play]'s history store, reading through what earlier requests kept of it. */
    internal fun open(sql: DSLContext, play: UUID): EngineHistoryStore? {
        val store = EngineHistoryStore.open(sql, play, synchronized(plays) { plays[play] }) ?: return null
        synchronized(plays) { plays[play] = store.cache }
        return store
    }

    /** One player's seat: their local head, private settings, next stop and its time, whether the game waits for them
     * (I-D57, [NextStop.waitsFor]), the point they would resume at (the encoded [org.asyncttwiz.protocol.ResumePoint] of
     * their local head) and, when another player's branch took their local head back to the branch point, where it stood
     * on the discarded line ([takenBackFrom], HIS-106); when the other player's undo took back events they had been
     * shown, those events (encoded, from the undo's point on: [undone], HIS-108) and who undid ([undoneBy]); both cleared
     * by any later move of it. */
    data class SeatRow(val account: UUID, val head: String, val settings: PlaySettings, val nextStop: String?,
        val nextStopAt: java.time.OffsetDateTime? = null, val waited: Boolean = false, val resumeAt: String? = null,
        val takenBackFrom: String? = null, val mulligans: Int = 0, val undone: List<String> = emptyList(), val undoneBy: String? = null)

    /**
     * What a finished match's record takes from its play (game history spec §1.2): the turn number at the end, each
     * player's final life, mulligans and result, how it ended, and why each loser lost when the rules decided.
     */
    class MatchFacts(val turns: Int, val life: Map<String, Int>, val mulligans: Map<String, Int>,
        val results: Map<String, org.asyncttwiz.protocol.SuggestedResult>, val ending: org.asyncttwiz.protocol.MatchEnding,
        val causes: Map<String, org.asyncttwiz.protocol.LossCause>) {
        companion object {
            /** The facts of a match that ended on [engine] with [result]: [conceded] by that player, else [agreement] (who agreed,
             * the reason of the suggestion), else by the rules; [mulligans] per player. */
            fun of(engine: arcanum.engine.state.GameState, result: arcanum.engine.setup.GameResult, conceded: PlayerId?,
                agreement: Pair<String, String>?, mulligans: Map<String, Int>): MatchFacts {
                val winner = (result as? arcanum.engine.setup.GameResult.Won)?.winner
                val results = engine.players.associate { p -> p.id.value to when (winner) {
                    null -> org.asyncttwiz.protocol.SuggestedResult.DRAW
                    p.id -> org.asyncttwiz.protocol.SuggestedResult.WIN
                    else -> org.asyncttwiz.protocol.SuggestedResult.LOSS
                } }
                val reasons = engine.players.mapNotNull { p -> p.lossReason?.let { p.id.value to it } }.toMap()
                val ending = when {
                    conceded != null -> org.asyncttwiz.protocol.MatchEnding.Conceded(conceded.value)
                    agreement != null -> org.asyncttwiz.protocol.MatchEnding.Agreed(agreement.second)
                    winner == null -> org.asyncttwiz.protocol.MatchEnding.Draw(loop = reasons.values.any { it == arcanum.engine.setup.LossReason.Loop })
                    else -> org.asyncttwiz.protocol.MatchEnding.ByRules
                }
                // CR 104.3b-e: why each player lost by the rules (a concession or an agreement says it itself)
                val causes = reasons.mapNotNull { (player, reason) -> when (reason) {
                    arcanum.engine.setup.LossReason.ZeroLife -> org.asyncttwiz.protocol.LossCause.ZeroLife
                    arcanum.engine.setup.LossReason.EmptyLibrary -> org.asyncttwiz.protocol.LossCause.EmptyLibrary
                    arcanum.engine.setup.LossReason.Poison -> org.asyncttwiz.protocol.LossCause.Poison
                    is arcanum.engine.setup.LossReason.Effect -> org.asyncttwiz.protocol.LossCause.CardEffect(reason.card)
                    else -> null
                }?.let { player to it } }.toMap()
                return MatchFacts(engine.turn.number, engine.players.associate { it.id.value to it.life }, mulligans, results, ending, causes)
            }
        }
    }

    /**
     * What a change did, for the caller's revision, notifications and broadcast: the new [events] (encoded, in order:
     * a rewind's new line first), a rewind's [target], the assumed event a submission [confirmed], whether the game's
     * result is now final ([ended]: the play ends), each player's local head, whether the game waits for each (their
     * move, TRN-058: [moves]), the players whose next stop is new ([newStops]: their "your move" notification, timed from their stop's event,
     * [stopTimes]), each player's last-activity time ([activity]: their next stop's time, else their local head's, §7),
     * the open undo request.
     */
    class Outcome(
        val play: UUID, val events: List<String>, val live: LiveState?, val target: String? = null, val confirmed: String? = null,
        val ended: Boolean = false, val heads: Map<String, String> = emptyMap(), val moves: Map<String, Boolean> = emptyMap(),
        val newStops: Set<String> = emptySet(), val request: EngineRewindRequest? = null, val lines: Int = 0,
        val stopTimes: Map<String, java.time.OffsetDateTime> = emptyMap(), val activity: Map<String, String> = emptyMap(),
        internal val store: EngineHistoryStore? = null,
        /** A submission's real answer differed from an assumed one: the main line branched (I-D56 §8). */
        val branched: Boolean = false,
        /** How the play ended when [ended] (LOB-111: the game-over notice reads it from the ended play). */
        val result: org.asyncttwiz.protocol.ResultSuggestion? = null,
        /** The players whose stored next stop went away with this change (their move is over: LOB-075's stretch ends). */
        val endedStops: Set<String> = emptySet(),
        /** The players whose opening is still open while the other player kept in this change (LOB-114). */
        val ready: Set<String> = emptySet(),
        /** I-D57: whether the game waits for each player ([NextStop.waitsFor]): only they are told "Your move" and reminded. */
        val waits: Map<String, Boolean> = emptyMap(),
        /** The players the game waits for since this change, or at a new next stop of theirs: their "Your move" notice. */
        val newWaits: Set<String> = emptySet(),
        /** The players the game waited for until this change. */
        val endedWaits: Set<String> = emptySet(),
        /** Whose undo a rewind is (the requester's when approved): their device waits for them at [target]. */
        val rewoundBy: String? = null,
        /** Each player's current response mode (owner 2026-11-06: the other player's device learns it at once). */
        val modes: Map<String, org.asyncttwiz.protocol.game.ResponseMode> = emptyMap(),
        /** When [ended]: what the match's record takes from the play. */
        val facts: MatchFacts? = null,
        /** A submission dropped as the duplicate of a main-line event: its id and the event's ([Acceptance.Confirmed.alias]). */
        val alias: Pair<String, String>? = null,
    ) {
        /** Whose move the game is for the stored summary: a player the game waits for (each viewer sees their own first). */
        val attention: String? get() = moves.entries.firstOrNull { it.value }?.key
        val update: EngineUpdate get() = EngineUpdate(events.drop(1), target, heads, lines, request, confirmed, moves, activity, rewoundBy, modes)
    }

    private fun take(play: UUID): LiveState? = synchronized(warm) { warm.remove(play) }

    /** Whether any play may be engine state: found once (no query per head while the seam starts none), then set by
     * [start]. Until the switch-over, reads of today's plays pay nothing for engine plays. */
    @Volatile private var any: Boolean? = null

    fun mayExist(sql: DSLContext): Boolean = any ?: sql.fetchExists(ENGINE_PLAY).also { any = it }

    /** Puts [outcome]'s state back as its play's warm state, once its transaction committed. */
    fun committed(outcome: Outcome) {
        outcome.store?.let { store -> store.committed(); synchronized(plays) { plays[outcome.play] = store.cache } }
        outcome.alias?.let { (submission, event) -> synchronized(aliases) {
            val play = aliases.getOrPut(outcome.play) { LinkedHashMap() }
            play[submission] = event
            while (play.size > MAX_ALIASES) play.remove(play.keys.first())
        } }
        outcome.live?.let { synchronized(warm) { warm[outcome.play] = it } }
    }

    /** Puts [state] (refused: nothing changed) back as [play]'s warm state, moved to the head (abreview #10). */
    private fun keepWarm(play: UUID, history: GameHistory, state: LiveState?, head: String) {
        if (state == null) return
        val model = history.move(state.model, state.at, head)
        synchronized(warm) { warm[play] = LiveState(head, model) }
    }

    /** Whether [play] is played as engine state. */
    fun engine(sql: DSLContext, play: UUID): Boolean = open(sql, play) != null

    /**
     * Starts [play]'s engine history from [seats] (account ids as players): the state before the game is the root's
     * checkpoint (every player's "Skip when I have no choice" off in the shared state, I-D56 §13; the game's random
     * secret in its table state, §9), and the first event, by [author], starts it ([Input.StartGame] with mulligans in
     * any order and line 0's random answers). Both players' local heads are at it.
     */
    fun start(sql: DSLContext, play: UUID, seats: List<Seat>, starter: PlayerId?, author: UUID, live: (UUID) -> Boolean): Outcome {
        any = true
        val engine = Engine.newGame(seats)
        for (p in engine.players) engine.apply(Changes.skipWhenNoChoice(engine, p.id, false))
        val initial = GameModel(engine, TableState(secret = LineRandom.secret(secret())))
        val rootId = UUID.randomUUID()
        val store = EngineHistoryStore.create(sql, play, rootId, GameDefinitions.of(initial.engine), initial)
        val history = GameHistory(store, store.codec)
        val builder = EventBuilder(initial, UUID.randomUUID().toString(), rootId.toString(), author.toString())
        // TRN-062: the game's rules give the first mulligan free (GameRules.STANDARD)
        builder.perform(Input.StartGame(StartOptions(startingPlayer = starter, simultaneousMulligans = true, freeFirstMulligan = true)))
        builder.answerRandoms()
        val event = builder.build()
        store.append(history, event, author, initial)
        // LOB-113: each player's seat starts with their account's defaults for new games
        for (seat in seats) {
            val account = UUID.fromString(seat.player.value)
            sql.insertInto(ENGINE_SEAT).set(ENGINE_SEAT.PLAY_ID, play).set(ENGINE_SEAT.ACCOUNT_ID, account)
                .set(ENGINE_SEAT.LOCAL_HEAD, UUID.fromString(event.id))
                .set(ENGINE_SEAT.SETTINGS, GameWire.json.encodeToString(PlaySettings.serializer(),
                    org.asyncttwiz.server.accounts.PlayDefaultsStore.read(sql, account))).execute()
        }
        val state = LiveState(event.id, initial)
        return refresh(sql, play, history, state, listOf(event), live)
    }

    /** [submission] by [author] on [play] (I-D56 §8): accepted (appended, a branch, or a confirmation), or refused
     * ([Refusal]); null when [play] isn't played as engine state. [reveals]: the game's assume reveal settings, which the
     * information rule's check honours (LOB-135). */
    fun submit(sql: DSLContext, play: UUID, author: UUID, submission: Submission, live: (UUID) -> Boolean,
        reveals: org.asyncttwiz.protocol.AssumeReveals = org.asyncttwiz.protocol.AssumeReveals.NONE): Outcome? {
        val store = open(sql, play) ?: return null
        val history = GameHistory(store, store.codec)
        val row = playRow(sql, play)
        val seats = seats(sql, play)
        val head = head(sql, play, store.rootId)
        val warm = take(play)
        val me = PlayerId(author.toString())
        val result = try {
            Acceptance.accept(history, head, warm, submission, me, seats.associate { PlayerId(it.account.toString()) to it.head },
                row.lines + 1, rewindOpen = row.request != null, aliases = aliases(play), reveals = reveals)
        } catch (refusal: Refusal) {
            keepWarm(play, history, refusal.state, head)
            throw refusal
        }
        return when (result) {
            is Acceptance.Confirmed -> {
                setHead(sql, play, author, result.event)
                // The warm state wasn't used: it is still the head's
                val state = warm?.takeIf { it.at == head } ?: LiveState(head, history.stateAt(head))
                refresh(sql, play, history, state, emptyList(), live, confirmed = result.event, alias = result.alias?.let { it to result.event })
            }
            is Acceptance.Accepted -> {
                val event = result.event
                if (result.discarded != null) {
                    // The discarded line stays in storage, shown to nobody; local heads on it fall back to the branch point,
                    // remembering where they stood (HIS-106: a device opening the game later shows the take-back)
                    val gone = MainLine.after(history, head, event.parentId).orEmpty().map { it.id }.toSet()
                    for (seat in seats) if (seat.head in gone) takeBack(sql, play, seat.account, event.parentId, seat.head)
                }
                event.startsLine?.let { sql.update(ENGINE_PLAY).set(ENGINE_PLAY.LINES, it).where(ENGINE_PLAY.PLAY_ID.eq(play)).execute() }
                store.append(history, event, author, result.live.model)
                if (result.authorHead) setHead(sql, play, author, event.id)
                // A concession ends an open undo request (I-D56 §10)
                if (row.request != null) clearRequest(sql, play)
                refresh(sql, play, history, result.live, listOf(event), live, branched = result.discarded != null)
            }
        }
    }

    /** [account]'s device showed the main line up to [eventId] (I-D56 §7): their local head moves forward to it. */
    fun seen(sql: DSLContext, play: UUID, account: UUID, eventId: String, live: (UUID) -> Boolean): Outcome? {
        val store = open(sql, play) ?: return null
        val history = GameHistory(store, store.codec)
        val head = head(sql, play, store.rootId)
        val seat = seats(sql, play).single { it.account == account }
        if (eventId == seat.head) {
            // HIS-108: the device showed the other player's undo at its local head: a later opening shows it no more
            if (seat.undoneBy == null) throw Refusal("noChange")
            setHead(sql, play, account, eventId)
            return refresh(sql, play, history, warmAt(play, history, head), emptyList(), live, only = account)
        }
        val after = MainLine.after(history, head, seat.head) ?: throw Refusal("stale")
        if (after.none { it.id == eventId }) throw Refusal("stale")
        setHead(sql, play, account, eventId)
        return refresh(sql, play, history, warmAt(play, history, head), emptyList(), live, only = account)
    }

    /** [account]'s private play settings for [play] (I-D56 §13). */
    fun settings(sql: DSLContext, play: UUID, account: UUID, settings: PlaySettings, live: (UUID) -> Boolean): Outcome? {
        val store = open(sql, play) ?: return null
        sql.update(ENGINE_SEAT).set(ENGINE_SEAT.SETTINGS, GameWire.json.encodeToString(PlaySettings.serializer(), settings))
            .where(ENGINE_SEAT.PLAY_ID.eq(play)).and(ENGINE_SEAT.ACCOUNT_ID.eq(account)).execute().also { if (it != 1) throw Refusal("inapplicable") }
        val history = GameHistory(store, store.codec)
        return refresh(sql, play, history, warmAt(play, history, head(sql, play, store.rootId)), emptyList(), live, only = account)
    }

    /**
     * Takes [play]'s main line back to just after [target] by [account] (I-D56 §10): at once when that is free for them
     * ([Rewind.requestNeeded]), or [approved] (the other player accepted their request); else refused
     * ("requestNeeded"). The events after the target are deleted (popped off the main line: nothing is added for the
     * rewind itself); a new line is due (the line counter moves on; the next event made there starts it with its
     * reshuffle, [Rewind.lineDue]); the other player's table-only events that may stand are added again after it while
     * they apply, the first starting the line; local heads past the target move to the new head.
     */
    fun rewind(sql: DSLContext, play: UUID, account: UUID, target: String, live: (UUID) -> Boolean, approved: Boolean = false): Outcome? {
        val store = open(sql, play) ?: return null
        val history = GameHistory(store, store.codec)
        val head = head(sql, play, store.rootId)
        val after = MainLine.after(history, head, target) ?: throw Refusal("inapplicable")
        if (after.isEmpty()) throw Refusal("noChange")
        // The game's start is never taken back (the head is always a stored event)
        if (target == store.rootId) throw Refusal("inapplicable")
        val row = playRow(sql, play)
        if (row.request != null && !approved) throw Refusal("rewindRequested")
        val seats = seats(sql, play)
        val me = PlayerId(account.toString())
        val warm = take(play)
        val model = if (warm == null) history.stateAt(target) else history.move(warm.model, warm.at, target)
        if (!approved) Rewind.requestNeeded(model.copy(), target, after, me, seats.filter { it.account != account }.map { it.head })
            ?.let { keepWarm(play, history, LiveState(target, model), head); throw Refusal("requestNeeded") }
        store.delete(after.map { it.id })
        // A new line (§9), kept as the line counter: no event of the rewind's own (owner's rule 2026-11-04)
        val line = row.lines + 1
        sql.update(ENGINE_PLAY).set(ENGINE_PLAY.LINES, line).where(ENGINE_PLAY.PLAY_ID.eq(play)).execute()
        val events = Rewind.again(model, target, after, me, line, { UUID.randomUUID().toString() }) { event ->
            store.append(history, event, UUID.fromString(event.author), model)
        }
        val gone = after.map { it.id }.toSet() + target
        val newHead = events.lastOrNull()?.id ?: target.also(store::raise)
        for (seat in seats) if (seat.head in gone) {
            // HIS-108: what another player had been shown of the deleted events stays with their seat (its local head at
            // the undo's point), so a device opening the game later shows the undo instead of only its result
            val seen = if (seat.account == account) emptyList() else after.take(after.indexOfFirst { it.id == seat.head } + 1)
            if (seen.isNotEmpty() && seen.size <= MAX_TAKEN_BACK) undone(sql, play, seat.account, target, account, seen.map(store.codec::encodeEvent))
            else setHead(sql, play, seat.account, newHead)
        }
        if (approved || row.request != null) clearRequest(sql, play)
        return refresh(sql, play, history, LiveState(newHead, model), events, live, target = target, rewoundBy = account.toString())
    }

    /** [account] asks the other player to approve taking the main line back to just after [target] (I-D56 §10). */
    fun requestRewind(sql: DSLContext, play: UUID, account: UUID, target: String, message: String, live: (UUID) -> Boolean): Outcome? {
        val store = open(sql, play) ?: return null
        val history = GameHistory(store, store.codec)
        val head = head(sql, play, store.rootId)
        if (playRow(sql, play).request != null) throw Refusal("rewindRequested")
        if (MainLine.after(history, head, target)?.isNotEmpty() != true) throw Refusal("inapplicable")
        sql.update(ENGINE_PLAY).set(ENGINE_PLAY.REWIND_BY, account).set(ENGINE_PLAY.REWIND_TARGET, UUID.fromString(target))
            .set(ENGINE_PLAY.REWIND_MESSAGE, message).where(ENGINE_PLAY.PLAY_ID.eq(play)).execute()
        return refresh(sql, play, history, warmAt(play, history, head), emptyList(), live)
    }

    /** The other player's answer to [play]'s open undo request: accepted, it rewinds; declined, it closes. */
    fun answerRewind(sql: DSLContext, play: UUID, account: UUID, accept: Boolean, live: (UUID) -> Boolean): Outcome? {
        val store = open(sql, play) ?: return null
        val request = playRow(sql, play).request ?: throw Refusal("inapplicable")
        if (request.by == account.toString()) throw Refusal("otherPlayerRequired")
        if (accept) return rewind(sql, play, UUID.fromString(request.by), request.target, live, approved = true)
        clearRequest(sql, play)
        val history = GameHistory(store, store.codec)
        return refresh(sql, play, history, warmAt(play, history, head(sql, play, store.rootId)), emptyList(), live)
    }

    /** The requester takes their open undo request back. */
    fun cancelRewind(sql: DSLContext, play: UUID, account: UUID, live: (UUID) -> Boolean): Outcome? {
        val store = open(sql, play) ?: return null
        val request = playRow(sql, play).request ?: throw Refusal("inapplicable")
        if (request.by != account.toString()) throw Refusal("accessDenied")
        clearRequest(sql, play)
        val history = GameHistory(store, store.codec)
        return refresh(sql, play, history, warmAt(play, history, head(sql, play, store.rootId)), emptyList(), live)
    }

    /** [play]'s state at the main line's head for [viewer]'s device to load (warm when it can be), with the local heads,
     * the viewer's own settings, each player's response mode, the open undo request and the line counter; and whose move it is for the viewer
     * (account id: theirs when they have a next stop, else the other player's when they have one; null once over). */
    fun snapshot(sql: DSLContext, play: UUID, viewer: UUID): Pair<EngineSnapshot, String?>? {
        val (store, head, row, seats) = opened(sql, play) ?: return null
        val history = GameHistory(store, store.codec)
        val state = warmAt(play, history, head)
        synchronized(warm) { warm[play] = state }
        // I-D56 §7: from the earliest local head (behind the head, else the head), its state and the events after it; and,
        // as a device that kept the game open holds them (GameDevice.KEPT_EVENTS), the line's events before it (owner
        // problem report KWKB3Y, HIS-084: else a device that opened the game knew none of the player's earlier actions,
        // and Undo said there was nothing to undo)
        val earliest = seats.mapNotNull { seat -> MainLine.after(history, head, seat.head)?.let { seat.head to it.size } }
            .maxByOrNull { it.second }?.first ?: head
        val depth = if (earliest == store.rootId) 0 else store.node(earliest)?.depth ?: 0
        // Never further back than the game's first event (e2e OpeningKeepTest): a snapshot based at the root gave a device
        // opening the game in its opening a wrong board (turn 1 with its keep decision still open, its Keep doing nothing)
        val start = store.ancestors(earliest, (depth - GameDevice.KEPT_EVENTS).coerceAtLeast(1)).lastOrNull()?.event?.parentId ?: earliest
        val after = MainLine.after(history, head, start).orEmpty()
        val base = if (after.isEmpty()) null else store.codec.encodeModel(stateAtLocalHead(history, state, start, after))
        val seat = seats.singleOrNull { it.account == viewer }
        // HIS-106: what of the discarded line the viewer had been shown, when another player's branch took it back
        val takenBack = seat?.takenBackFrom?.let { from -> MainLine.after(history, from, seat.head) }
            ?.takeIf { it.isNotEmpty() && it.size <= MAX_TAKEN_BACK && seat.head != head }.orEmpty().map(store.codec::encodeEvent)
        // HIS-108: or what of it the other player's undo took back (deleted from the line, kept with the seat)
        val undone = seat?.undone?.takeIf { seat.undoneBy != null }.orEmpty()
        return EngineSnapshot(store.codec.encodeDefinitions(), store.rootId, head, store.codec.encodeModel(state.model),
            seats.associate { it.account.toString() to it.head }, seat?.settings, row.request, row.lines,
            start.takeIf { after.isNotEmpty() }, base, after.map(store.codec::encodeEvent), takenBack.ifEmpty { undone },
            seats.associate { it.account.toString() to it.settings.mode },
            undoneBy = seat?.undoneBy?.takeIf { takenBack.isEmpty() && undone.isNotEmpty() }) to
            attention(seats, viewer)
    }

    /**
     * HIS-111, HIS-099 (owner 2026-11-07: the timeline and the Undo dialog reach the game's first event): the page of
     * [play]'s main line ending at its event [through] ([GameHistory.page]: the checkpoint above it and at most
     * [GameHistory.CHECKPOINT_EVERY] events, however long the game); null when [play] isn't played as engine state;
     * refused ("inapplicable") when [through] isn't on the main line (a discarded line is shown to nobody). Nothing past
     * the head is read, and a device holds everything up to the head anyway (I-D56 §7, I-D27).
     */
    fun historyPage(sql: DSLContext, play: UUID, through: String): org.asyncttwiz.protocol.EngineHistoryPage? {
        val store = open(sql, play) ?: return null
        val history = GameHistory(store, store.codec)
        return history.page(head(sql, play, store.rootId), through) ?: throw Refusal("inapplicable")
    }

    /** Whose move [play] is for [viewer] (TRN-058, I-D57): null when it isn't an engine play or it waits for nobody. */
    fun attentionFor(sql: DSLContext, play: UUID, viewer: UUID): String? =
        if (!mayExist(sql) || open(sql, play) == null) null else attention(seats(sql, play), viewer)

    /**
     * Whether each player of [play] has the move (the game waits for them, TRN-058) and their last-activity time
     * (I-D56 §7), as stored after its last change: for a revision that changes nothing of the engine's (chat, the start) to carry as an engine revision does; null
     * when [play] isn't played as engine state.
     */
    fun moves(sql: DSLContext, play: UUID): EngineUpdate? {
        if (!mayExist(sql)) return null
        val seats = seats(sql, play).takeIf { it.isNotEmpty() } ?: return null
        val heads = seats.filter { it.nextStopAt == null }.map { UUID.fromString(it.head) }
        val times = if (heads.isEmpty()) emptyMap() else sql.select(ENGINE_EVENT.ID, ENGINE_EVENT.CREATED_AT).from(ENGINE_EVENT)
            .where(ENGINE_EVENT.PLAY_ID.eq(play)).and(ENGINE_EVENT.ID.`in`(heads)).fetch().associate { it.value1()!!.toString() to it.value2()!! }
        return EngineUpdate(moves = seats.associate { it.account.toString() to it.waited },
            activity = seats.mapNotNull { seat -> (seat.nextStopAt ?: times[seat.head])?.let { seat.account.toString() to it.toInstant().toString() } }.toMap())
    }

    /** Whether the game waits for each player of [play] (I-D57), as stored after its last change; null when [play] isn't
     * played as engine state. */
    fun waits(sql: DSLContext, play: UUID): Map<String, Boolean>? {
        if (!mayExist(sql)) return null
        return seats(sql, play).takeIf { it.isNotEmpty() }?.associate { it.account.toString() to it.waited }
    }

    /** TRN-058 (I-D57): the viewer when the game waits for them, else the other player when it waits for them. */
    private fun attention(seats: List<SeatRow>, viewer: UUID): String? =
        seats.firstOrNull { it.account == viewer && it.waited }?.account?.toString()
            ?: seats.firstOrNull { it.waited }?.account?.toString()

    /** The warm state at [head], or the state there from a checkpoint (the warm one taken out). */
    private fun warmAt(play: UUID, history: GameHistory, head: String): LiveState {
        val live = take(play)
        return if (live?.at == head) live else LiveState(head, history.stateAt(head))
    }

    /**
     * After every change: each player's next stop with their own mode (I-D56 §7), stored with its time (the event at
     * their stop: the assumed input waiting for them, or the head's event for a real wait); a real wait going on across
     * table-only events (the other player arranging cards, early answers) is the same wait, kept with its time and not
     * notified again (§18.11); a new real wait at the head of a player who isn't live counts as an async round (§15);
     * each player's last-activity time (their next stop's, else their local head's: nothing past it, §7); the outcome for
     * the caller. [only] (R6): the change was that player's own (their local head or settings), so only their seat is
     * worked out again.
     */
    private fun refresh(sql: DSLContext, play: UUID, history: GameHistory, state: LiveState, events: List<GameEvent>, live: (UUID) -> Boolean,
        target: String? = null, confirmed: String? = null, branched: Boolean = false, rewoundBy: String? = null, only: UUID? = null,
        alias: Pair<String, String>? = null): Outcome {
        val head = state.at
        val seats = seats(sql, play)
        val row = playRow(sql, play)
        val newStops = mutableSetOf<String>()
        val moves = mutableMapOf<String, Boolean>()
        val stops = mutableMapOf<SeatRow, String?>()
        val found = mutableMapOf<SeatRow, NextStop.Stop?>()
        val waits = mutableMapOf<SeatRow, Boolean>()
        val resume = mutableMapOf<SeatRow, String?>()
        // The seats whose only stop is answering the open undo request or result suggestion
        val requested = mutableSetOf<SeatRow>()
        // I-D57 (TRN-058): the player whose open undo request (switchreview #12) or result suggestion (TRN-047, TRN-049:
        // it locks the game for both) waits for the other player's answer
        val asker = row.request?.by ?: state.model.table.suggestion?.by?.value
        for (seat in seats) {
            // R6: a change of [only]'s own (their local head, their settings) can't move the other player's next stop, wait
            // or resume point (each is worked out from the head, that player's own local head and settings, and the open
            // request, none of which changed): theirs stay as stored. A seat stored before resume points were (V60) is
            // worked out anyway.
            if (only != null && seat.account != only && seat.resumeAt != null) {
                if (checkSkipped) work(history, state, seat, asker, row.request).let { w ->
                    check(w.stopId == seat.nextStop && w.waits == seat.waited && w.resume == seat.resumeAt) {
                        "The seat of ${seat.account} left as stored differs from working it out" }
                }
                stops[seat] = seat.nextStop
                waits[seat] = seat.waited
                resume[seat] = seat.resumeAt
                moves[seat.account.toString()] = seat.waited
                continue
            }
            seatsWorkedOut.incrementAndGet()
            val work = work(history, state, seat, asker, row.request)
            if (work.requested) requested += seat
            resume[seat] = work.resume
            found[seat] = work.stop
            waits[seat] = work.waits
            stops[seat] = work.stopId
            if (work.stopId != seat.nextStop && work.stop != null) newStops += seat.account.toString()
            // TRN-058, LOB-022, LOB-024 (I-D57): the move is the player the game really waits for, as notifications have it
            moves[seat.account.toString()] = work.waits
        }
        // The times of the new stops and of the local heads, in one read
        val wanted = (stops.filter { (seat, id) -> id != seat.nextStop }.values.filterNotNull() + seats.map { it.head })
            .filter { it != history.store.rootId }.map(UUID::fromString).distinct()
        val times = if (wanted.isEmpty()) emptyMap() else sql.select(ENGINE_EVENT.ID, ENGINE_EVENT.CREATED_AT).from(ENGINE_EVENT)
            .where(ENGINE_EVENT.PLAY_ID.eq(play)).and(ENGINE_EVENT.ID.`in`(wanted)).fetch().associate { it.value1()!!.toString() to it.value2()!! }
        val stopTimes = mutableMapOf<String, java.time.OffsetDateTime>()
        val activity = mutableMapOf<String, String>()
        for (seat in seats) {
            val stopId = stops[seat]
            val time = if (stopId == seat.nextStop) seat.nextStopAt
                else if (seat in requested) java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).truncatedTo(java.time.temporal.ChronoUnit.MICROS)
                else stopId?.let { times[it] }
            // One statement for whatever of the seat changed (I-D57: whether the game waits for them, their resume point)
            if (stopId != seat.nextStop || waits[seat] != seat.waited || resume[seat] != seat.resumeAt)
                sql.update(ENGINE_SEAT).set(ENGINE_SEAT.NEXT_STOP, stopId?.let(UUID::fromString)).set(ENGINE_SEAT.NEXT_STOP_AT, time)
                    .set(ENGINE_SEAT.WAITED, waits[seat]).set(ENGINE_SEAT.RESUME_AT, resume[seat])
                    .where(ENGINE_SEAT.PLAY_ID.eq(play)).and(ENGINE_SEAT.ACCOUNT_ID.eq(seat.account)).execute()
            if (stopId != seat.nextStop) {
                // §15: a wait for this player's real input begins at the head while they aren't live (not one going on
                // across the other player's table-only events)
                val stop = found[seat]
                val waited = seat.nextStop?.let { history.store.node(it)?.event?.assumed == null } == true
                if (stop != null && stop.event == null && !waited && seat !in requested && !live(seat.account))
                    round(sql, play, seat.account, stop.at, kind(state.model, PlayerId(seat.account.toString())))
            }
            time?.let { stopTimes[seat.account.toString()] = it }
            (time ?: times[seat.head])?.let { activity[seat.account.toString()] = it.toInstant().toString() }
        }
        // The game history's mulligans (§1.2): kept while the engine still logs them (its first two turns)
        val engine = state.model.engine
        val mulligans = if (engine.turn.number <= 1) loggedMulligans(engine) else seats.associate { it.account.toString() to it.mulligans }
        for (seat in seats) {
            val count = mulligans[seat.account.toString()] ?: 0
            if (count != seat.mulligans) sql.update(ENGINE_SEAT).set(ENGINE_SEAT.MULLIGANS, count)
                .where(ENGINE_SEAT.PLAY_ID.eq(play)).and(ENGINE_SEAT.ACCOUNT_ID.eq(seat.account)).execute()
        }
        val result = Engine.result(engine)
        val conceded = events.flatMap { e -> e.engine?.steps.orEmpty().mapNotNull { (it.input as? Input.Concede)?.player } }.firstOrNull()
        val ended = result != null && (state.model.table.resultConfirmedBy != null || conceded != null)
        val agreement = if (ended && conceded == null) agreement(history, head) else null
        return Outcome(play, events.map(history.codec::encodeEvent), state, target, confirmed, ended,
            seats.associate { it.account.toString() to it.head }, moves, newStops, row.request, row.lines, stopTimes, activity,
            history.store as? EngineHistoryStore, branched,
            if (ended) record(result!!, conceded, agreement?.first, seats.map { it.account.toString() }) else null,
            seats.filter { it.nextStop != null && stops[it] == null }.map { it.account.toString() }.toSet(),
            ready(state.model.engine, events, seats),
            seats.associate { it.account.toString() to (waits[it] == true) },
            // A wait that goes on across the other player's table-only events keeps its stop: not new (§18.11)
            seats.filter { waits[it] == true && (!it.waited || stops[it] != it.nextStop) }.map { it.account.toString() }.toSet(),
            seats.filter { it.waited && waits[it] != true }.map { it.account.toString() }.toSet(), rewoundBy,
            seats.associate { it.account.toString() to it.settings.mode },
            if (ended) MatchFacts.of(engine, result!!, conceded, agreement, mulligans) else null, alias)
    }

    /** Each player's mulligans taken in the opening (CR 103.5), from the engine's log of this and last turn. */
    private fun loggedMulligans(engine: arcanum.engine.state.GameState): Map<String, Int> = engine.players.associate { p ->
        p.id.value to (engine.log.mapNotNull { ((it.change as? arcanum.engine.change.Change.Happened)?.event as?
            arcanum.engine.change.GameEvent.Mulliganed)?.takeIf { m -> m.player == p.id }?.count }.maxOrNull() ?: 0)
    }

    /** What [refresh] works out for one seat: its next stop (and whether answering the open undo request or result
     * suggestion is all of it: [requested]), whether the game waits for the player, the stop to store and the resume point. */
    private class SeatWork(val stop: NextStop.Stop?, val requested: Boolean, val waits: Boolean, val stopId: String?, val resume: String)

    private fun work(history: GameHistory, state: LiveState, seat: SeatRow, asker: String?, request: EngineRewindRequest?): SeatWork {
        val head = state.at
        val player = PlayerId(seat.account.toString())
        val after = MainLine.after(history, head, seat.head) ?: emptyList()
        val model = stateAtLocalHead(history, state, seat.head, after)
        // I-D57: the point a notification names: the player's own local head, never the head (before NextStop uses it up)
        val resume = GameWire.json.encodeToString(org.asyncttwiz.protocol.ResumePoint.serializer(),
            org.asyncttwiz.protocol.game.ResumePoints.of(model.engine, player))
        var requested = false
        // switchreview #12 (owner to confirm): an open undo request is the other player's move, notified like one;
        // the requester waits (or cancels) meanwhile (§10)
        val stop = when {
            request?.by == seat.account.toString() -> null
            asker != null && asker != seat.account.toString() -> NextStop.of(model, seat.head, after, player, seat.settings.mode)
                ?: NextStop.Stop(head, null).also { requested = true }
            else -> NextStop.of(model, seat.head, after, player, seat.settings.mode)
        }
        val stored = seat.nextStop
        val same = stop != null && stop.event == null && stored != null &&
            MainLine.after(history, head, stored)?.all { it.engine == null } == true
        // I-D57: the asker of an open undo request or result suggestion waits for the other player's answer
        val waits = stop != null && (if (asker != null) asker != seat.account.toString()
            else NextStop.waitsFor(state.model.engine, player, stop))
        return SeatWork(stop, requested, waits, if (same) stored else stop?.let { it.event ?: it.at }, resume)
    }

    /**
     * Who agreed the result at the main line's [head] (I-D56 §11), and the reason of the suggestion they accepted: the author of the result's own event when it holds the
     * agreement ([Input.AgreedResult]). An agreed result is final only in a later table-only event (both local heads
     * reach it first), so the line is read back from the head to its last engine event (a few table-only events).
     */
    private fun agreement(history: GameHistory, head: String): Pair<String, String>? {
        var at = head
        while (at != history.store.rootId) {
            val event = history.store.node(at)?.event ?: return null
            val engine = event.engine
            // The accepting event also clears the suggestion it answered: its reason (TRN-046) is the agreement's
            if (engine != null) return if (engine.steps.none { it.input is Input.AgreedResult }) null else event.author to
                event.table.firstNotNullOfOrNull { (it as? TableChange.Suggestion)?.before?.note }.orEmpty()
            at = event.parentId
        }
        return null
    }

    /**
     * LOB-114: the players whose own opening declaration is still open on [state] (turn 0) while the other player kept by
     * their own real input in [events] ("<opponent> is ready").
     */
    private fun ready(state: arcanum.engine.state.GameState, events: List<GameEvent>, seats: List<SeatRow>): Set<String> {
        if (state.turn.number != 0) return emptySet()
        val keep = arcanum.engine.decision.Answer.Option("Keep")
        return seats.map { it.account.toString() }.filter { account ->
            val player = PlayerId(account)
            val open = state.pending?.decision?.player == player || NextStop.declares(state, player)
            open && events.any { e -> e.author != account && e.engine?.steps.orEmpty().any { step -> step.assumedBy == null &&
                ((step.input as? Input.SetupAnswer)?.answer == keep || (step.input as? Input.Answer)?.answer == keep) } }
        }.toSet()
    }

    /**
     * The ended play's result as the game-over notice reads it (LOB-111), in the form an accepted suggestion had: a
     * concession is its author's own LOSS; an agreed result is the suggestion's author's (the other player than the one
     * who agreed, I-D56 §11) from their side; the rules' result is byRules, told from each recipient's side.
     */
    private fun record(result: arcanum.engine.setup.GameResult, conceded: PlayerId?, agreedBy: String?, players: List<String>):
        org.asyncttwiz.protocol.ResultSuggestion {
        val id = UUID.randomUUID().toString()
        val winner = (result as? arcanum.engine.setup.GameResult.Won)?.winner?.value
        fun from(author: String) = when (winner) { null -> org.asyncttwiz.protocol.SuggestedResult.DRAW; author -> org.asyncttwiz.protocol.SuggestedResult.WIN
            else -> org.asyncttwiz.protocol.SuggestedResult.LOSS }
        if (conceded != null) return org.asyncttwiz.protocol.ResultSuggestion(id, conceded.value, org.asyncttwiz.protocol.SuggestedResult.LOSS)
        if (agreedBy != null) players.firstOrNull { it != agreedBy }?.let { author ->
            return org.asyncttwiz.protocol.ResultSuggestion(id, author, from(author)) }
        val author = winner ?: players.first()
        return org.asyncttwiz.protocol.ResultSuggestion(id, author, from(author), byRules = true)
    }

    /** The state at a local head [localHead] behind the head ([after] the events between): the head's state copied and
     * the events taken back, or from a checkpoint. At the head, the head's own state ([NextStop.of] applies nothing to it
     * then). */
    private fun stateAtLocalHead(history: GameHistory, state: LiveState, localHead: String, after: List<GameEvent>): GameModel {
        if (after.isEmpty()) return state.model
        // Table-only events are cheap to take back; only engine events count against the bound
        if (after.count { it.engine != null } > history.every) return history.stateAt(localHead)
        val model = state.model.copy()
        for (event in after.asReversed()) {
            model.revert(event)
            if (model.stale) return history.stateAt(localHead)
        }
        return model
    }

    /** The kind of the wait for [player] at the head (§15). */
    private fun kind(model: GameModel, player: PlayerId): String {
        val s = model.engine
        val d = s.pending?.decision
        return when {
            d != null && s.turn.number == 0 -> "keep"
            d != null -> "choice"
            org.asyncttwiz.protocol.game.Stops.stop(s, player) -> "stop"
            Engine.options(s, player).any { it is Input.DeclareBlockers } -> "blocks"
            org.asyncttwiz.protocol.game.Stops.pause(s, player) -> "pause"
            else -> "priority"
        }
    }

    private fun round(sql: DSLContext, play: UUID, account: UUID, at: String, kind: String) {
        val ordinal = (sql.select(DSL.max(ENGINE_ASYNC_ROUND.ORDINAL)).from(ENGINE_ASYNC_ROUND).where(ENGINE_ASYNC_ROUND.PLAY_ID.eq(play))
            .fetchOne(0, Long::class.java) ?: 0L) + 1
        sql.insertInto(ENGINE_ASYNC_ROUND).set(ENGINE_ASYNC_ROUND.PLAY_ID, play).set(ENGINE_ASYNC_ROUND.ORDINAL, ordinal)
            .set(ENGINE_ASYNC_ROUND.ACCOUNT_ID, account).set(ENGINE_ASYNC_ROUND.EVENT_ID, UUID.fromString(at))
            .set(ENGINE_ASYNC_ROUND.KIND, kind).execute()
    }

    /** The async rounds logged for [play] (§15): account and kind, in order. */
    fun rounds(sql: DSLContext, play: UUID): List<Pair<String, String>> =
        sql.select(ENGINE_ASYNC_ROUND.ACCOUNT_ID, ENGINE_ASYNC_ROUND.KIND).from(ENGINE_ASYNC_ROUND).where(ENGINE_ASYNC_ROUND.PLAY_ID.eq(play))
            .orderBy(ENGINE_ASYNC_ROUND.ORDINAL).fetch { it.value1().toString() to it.value2()!! }

    internal data class PlayRow(val lines: Int, val request: EngineRewindRequest?)

    /** What reading [play]'s head needs: its history store, the main line's head, its row and its seats. */
    internal data class Opened(val store: EngineHistoryStore, val head: String, val row: PlayRow, val seats: List<SeatRow>)

    /**
     * R6: [open], [head], [playRow] and [seats] in one statement (opening a game reads them all): the play's row (its
     * definitions only when no earlier request kept them), its newest event and its seats; null when [play] isn't played
     * as engine state.
     */
    internal fun opened(sql: DSLContext, play: UUID): Opened? {
        val shared = synchronized(plays) { plays[play] }
        val definitions = if (shared == null) ENGINE_PLAY.DEFINITIONS else DSL.inline(null, String::class.java).`as`("definitions")
        val newest = DSL.field(DSL.select(ENGINE_EVENT.ID).from(ENGINE_EVENT).where(ENGINE_EVENT.PLAY_ID.eq(ENGINE_PLAY.PLAY_ID))
            .orderBy(ENGINE_EVENT.ORDINAL.desc()).limit(1)).`as`("head")
        val rows = sql.select(ENGINE_PLAY.ROOT_ID, definitions, ENGINE_PLAY.LINES, ENGINE_PLAY.REWIND_BY, ENGINE_PLAY.REWIND_TARGET,
            ENGINE_PLAY.REWIND_MESSAGE, newest, ENGINE_SEAT.ACCOUNT_ID, ENGINE_SEAT.LOCAL_HEAD, ENGINE_SEAT.SETTINGS, ENGINE_SEAT.NEXT_STOP,
            ENGINE_SEAT.NEXT_STOP_AT, ENGINE_SEAT.WAITED, ENGINE_SEAT.RESUME_AT, ENGINE_SEAT.TAKEN_BACK_FROM, ENGINE_SEAT.MULLIGANS,
            ENGINE_SEAT.UNDONE_EVENTS, ENGINE_SEAT.UNDONE_BY)
            .from(ENGINE_PLAY).leftJoin(ENGINE_SEAT).on(ENGINE_SEAT.PLAY_ID.eq(ENGINE_PLAY.PLAY_ID))
            .where(ENGINE_PLAY.PLAY_ID.eq(play)).orderBy(ENGINE_SEAT.ACCOUNT_ID).fetch()
        val first = rows.firstOrNull() ?: return null
        val store = EngineHistoryStore.of(sql, play, first[ENGINE_PLAY.ROOT_ID]!!.toString(), first[definitions], shared)
        synchronized(plays) { plays[play] = store.cache }
        val row = PlayRow(first[ENGINE_PLAY.LINES]!!, first[ENGINE_PLAY.REWIND_BY]?.let {
            EngineRewindRequest(it.toString(), first[ENGINE_PLAY.REWIND_TARGET]!!.toString(), first[ENGINE_PLAY.REWIND_MESSAGE].orEmpty()) })
        val seats = rows.filter { it[ENGINE_SEAT.ACCOUNT_ID] != null }.map { r ->
            SeatRow(r[ENGINE_SEAT.ACCOUNT_ID]!!, r[ENGINE_SEAT.LOCAL_HEAD]!!.toString(),
                GameWire.json.decodeFromString(PlaySettings.serializer(), r[ENGINE_SEAT.SETTINGS]!!), r[ENGINE_SEAT.NEXT_STOP]?.toString(),
                r[ENGINE_SEAT.NEXT_STOP_AT], r[ENGINE_SEAT.WAITED]!!, r[ENGINE_SEAT.RESUME_AT], r[ENGINE_SEAT.TAKEN_BACK_FROM]?.toString(),
                r[ENGINE_SEAT.MULLIGANS]!!, undoneEvents(r[ENGINE_SEAT.UNDONE_EVENTS]), r[ENGINE_SEAT.UNDONE_BY]?.toString())
        }
        return Opened(store, first[newest]?.toString() ?: store.rootId, row, seats)
    }

    internal fun playRow(sql: DSLContext, play: UUID): PlayRow {
        val row = sql.select(ENGINE_PLAY.LINES, ENGINE_PLAY.REWIND_BY, ENGINE_PLAY.REWIND_TARGET, ENGINE_PLAY.REWIND_MESSAGE).from(ENGINE_PLAY)
            .where(ENGINE_PLAY.PLAY_ID.eq(play)).fetchSingle()
        return PlayRow(row.value1()!!, row.value2()?.let { EngineRewindRequest(it.toString(), row.value3()!!.toString(), row.value4().orEmpty()) })
    }

    private fun clearRequest(sql: DSLContext, play: UUID) {
        sql.update(ENGINE_PLAY).setNull(ENGINE_PLAY.REWIND_BY).setNull(ENGINE_PLAY.REWIND_TARGET).setNull(ENGINE_PLAY.REWIND_MESSAGE)
            .where(ENGINE_PLAY.PLAY_ID.eq(play)).execute()
    }

    internal fun seats(sql: DSLContext, play: UUID): List<SeatRow> =
        sql.select(ENGINE_SEAT.ACCOUNT_ID, ENGINE_SEAT.LOCAL_HEAD, ENGINE_SEAT.SETTINGS, ENGINE_SEAT.NEXT_STOP, ENGINE_SEAT.NEXT_STOP_AT,
            ENGINE_SEAT.WAITED, ENGINE_SEAT.RESUME_AT, ENGINE_SEAT.TAKEN_BACK_FROM, ENGINE_SEAT.MULLIGANS, ENGINE_SEAT.UNDONE_EVENTS,
            ENGINE_SEAT.UNDONE_BY)
            .from(ENGINE_SEAT).where(ENGINE_SEAT.PLAY_ID.eq(play)).orderBy(ENGINE_SEAT.ACCOUNT_ID).fetch { r ->
                SeatRow(r.value1()!!, r.value2()!!.toString(), GameWire.json.decodeFromString(PlaySettings.serializer(), r.value3()!!),
                    r.value4()?.toString(), r.value5(), r.value6()!!, r.value7(), r.value8()?.toString(), r.value9()!!,
                    undoneEvents(r.value10()), r.value11()?.toString())
            }

    private val undoneList = kotlinx.serialization.builtins.ListSerializer(String.serializer())
    private fun undoneEvents(json: String?): List<String> = json?.let { GameWire.json.decodeFromString(undoneList, it) }.orEmpty()

    private fun setHead(sql: DSLContext, play: UUID, account: UUID, event: String) {
        sql.update(ENGINE_SEAT).set(ENGINE_SEAT.LOCAL_HEAD, UUID.fromString(event)).setNull(ENGINE_SEAT.TAKEN_BACK_FROM)
            .setNull(ENGINE_SEAT.UNDONE_EVENTS).setNull(ENGINE_SEAT.UNDONE_BY)
            .where(ENGINE_SEAT.PLAY_ID.eq(play)).and(ENGINE_SEAT.ACCOUNT_ID.eq(account)).execute()
    }

    /** HIS-106: another player's branch at [branchPoint] discarded [account]'s local head [from]: it falls back to the
     * branch point and remembers [from] ([SeatRow.takenBackFrom]). */
    private fun takeBack(sql: DSLContext, play: UUID, account: UUID, branchPoint: String, from: String) {
        sql.update(ENGINE_SEAT).set(ENGINE_SEAT.LOCAL_HEAD, UUID.fromString(branchPoint)).set(ENGINE_SEAT.TAKEN_BACK_FROM, UUID.fromString(from))
            .setNull(ENGINE_SEAT.UNDONE_EVENTS).setNull(ENGINE_SEAT.UNDONE_BY)
            .where(ENGINE_SEAT.PLAY_ID.eq(play)).and(ENGINE_SEAT.ACCOUNT_ID.eq(account)).execute()
    }

    /** HIS-108: [by]'s undo to just after [target] deleted [seen] (encoded, oldest first), which [account] had been shown:
     * their local head goes to the undo's point, and the seat keeps [seen] and [by] for a device opening the game later
     * ([SeatRow.undone]). */
    private fun undone(sql: DSLContext, play: UUID, account: UUID, target: String, by: UUID, seen: List<String>) {
        sql.update(ENGINE_SEAT).set(ENGINE_SEAT.LOCAL_HEAD, UUID.fromString(target)).setNull(ENGINE_SEAT.TAKEN_BACK_FROM)
            .set(ENGINE_SEAT.UNDONE_EVENTS, GameWire.json.encodeToString(undoneList, seen)).set(ENGINE_SEAT.UNDONE_BY, by)
            .where(ENGINE_SEAT.PLAY_ID.eq(play)).and(ENGINE_SEAT.ACCOUNT_ID.eq(account)).execute()
    }

    /** The main line's head: the event the server accepted last (a branch or a rewind's new line becomes the main
     * line), else the root. */
    internal fun head(sql: DSLContext, play: UUID, rootId: String): String =
        sql.select(ENGINE_EVENT.ID).from(ENGINE_EVENT).where(ENGINE_EVENT.PLAY_ID.eq(play)).orderBy(ENGINE_EVENT.ORDINAL.desc()).limit(1)
            .fetchOne(ENGINE_EVENT.ID)?.toString() ?: rootId
}
