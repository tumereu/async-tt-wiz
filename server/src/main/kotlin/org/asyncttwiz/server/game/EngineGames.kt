package org.asyncttwiz.server.game

import arcanum.engine.input.Input
import arcanum.engine.setup.Seat
import arcanum.engine.setup.StartOptions
import arcanum.engine.Engine
import arcanum.engine.state.PlayerId
import java.security.SecureRandom
import java.util.UUID
import org.asyncttwiz.protocol.EngineSnapshot
import org.asyncttwiz.protocol.game.Acceptance
import org.asyncttwiz.protocol.game.Attention
import org.asyncttwiz.protocol.game.EventBuilder
import org.asyncttwiz.protocol.game.GameDefinitions
import org.asyncttwiz.protocol.game.GameEvent
import org.asyncttwiz.protocol.game.GameHistory
import org.asyncttwiz.protocol.game.GameModel
import org.asyncttwiz.protocol.game.LiveState
import org.asyncttwiz.protocol.game.Submission
import org.asyncttwiz.protocol.game.answerRandoms
import org.asyncttwiz.server.generated.tables.EngineEvent.ENGINE_EVENT
import org.jooq.DSLContext

/**
 * Test seam (I-D54, server tests only; null in the service, so every game starts on today's model): the engine seats of
 * a game starting now, by its game id, when it is to be played as engine state. Phase 5 replaces it with the game's
 * decks parsed on the server.
 */
@Volatile internal var engineSeats: ((gameId: UUID) -> List<Seat>?)? = null

/**
 * The server's engine for plays whose state is the engine's (I-D54 phase 3): it keeps active plays' states warm in
 * memory (the state at the live line's last event; a cold play is one checkpoint plus the events after it), takes
 * submissions in arrival order (the caller holds the game's row lock), refuses a stale parent or an impossible input
 * ([Acceptance], [org.asyncttwiz.protocol.game.Authority]) and stores each accepted event ([EngineHistoryStore]).
 *
 * A warm state is taken out while a submission is worked on and put back only once its transaction committed
 * ([committed]), so a rolled-back transaction leaves no state ahead of the database.
 */
internal class EngineGames(private val capacity: Int = 32) {
    private val warm = object : LinkedHashMap<UUID, LiveState>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<UUID, LiveState>?) = size > capacity
    }
    private val random = SecureRandom()

    /** What a submission did, for the caller's revision and broadcast. */
    class Accepted(val play: UUID, val event: GameEvent, val encoded: String, val live: LiveState) {
        /** Whose move the game is after it (account id), null once it is over. */
        val attention: String? get() = Attention.of(live.model.engine)?.value
    }

    private fun take(play: UUID): LiveState? = synchronized(warm) { warm.remove(play) }

    /** Puts [accepted]'s state back as [accepted.play]'s warm state, once its transaction committed. */
    fun committed(accepted: Accepted) = synchronized(warm) { warm[accepted.play] = accepted.live }

    /** Whether [play] is played as engine state. */
    fun engine(sql: DSLContext, play: UUID): Boolean = EngineHistoryStore.open(sql, play) != null

    /**
     * Starts [play]'s engine history from [seats] (account ids as players): the state before the game is the root's
     * checkpoint, and the first event, by [starter], starts it ([Input.StartGame] with mulligans in any order, §8, and
     * the server's own random results: the starting player as ATW chose, the shuffles).
     */
    fun start(sql: DSLContext, play: UUID, seats: List<Seat>, starter: PlayerId?, author: UUID): Accepted {
        val initial = GameModel(Engine.newGame(seats))
        val rootId = UUID.randomUUID()
        val store = EngineHistoryStore.create(sql, play, rootId, GameDefinitions.of(initial.engine), initial)
        val history = GameHistory(store, store.codec)
        val builder = EventBuilder(initial, UUID.randomUUID().toString(), rootId.toString(), author.toString())
        builder.perform(Input.StartGame(StartOptions(startingPlayer = starter, simultaneousMulligans = true)))
        builder.answerRandoms(initial, kotlin.random.Random(random.nextLong()))
        val event = builder.build()
        store.append(history, event, author, initial)
        return Accepted(play, event, store.codec.encodeEvent(event), LiveState(event.id, initial))
    }

    /** [submission] by [author] on [play]: accepted, or refused ([org.asyncttwiz.protocol.game.Refusal]); null when
     * [play] isn't played as engine state. */
    fun submit(sql: DSLContext, play: UUID, author: UUID, submission: Submission, branch: Boolean): Accepted? {
        val store = EngineHistoryStore.open(sql, play) ?: return null
        val history = GameHistory(store, store.codec)
        val accepted = Acceptance.accept(history, head(sql, play, store.rootId), take(play), submission, PlayerId(author.toString()), branch)
        store.append(history, accepted.event, author, accepted.live.model)
        return Accepted(play, accepted.event, store.codec.encodeEvent(accepted.event), accepted.live)
    }

    /** [play]'s state at the live line's last event, for a device to load (warm when it can be), and whose move it is
     * there (account id; null once the game is over). */
    fun snapshot(sql: DSLContext, play: UUID): Pair<EngineSnapshot, String?>? {
        val store = EngineHistoryStore.open(sql, play) ?: return null
        val head = head(sql, play, store.rootId)
        val live = take(play)
        val model = if (live?.at == head) live.model else GameHistory(store, store.codec).stateAt(head)
        synchronized(warm) { warm[play] = LiveState(head, model) }
        return EngineSnapshot(store.codec.encodeDefinitions(), store.rootId, head, store.codec.encodeModel(model)) to
            Attention.of(model.engine)?.value
    }

    /** The live line's last event: the one the server accepted last (a branch becomes the live line), else the root. */
    private fun head(sql: DSLContext, play: UUID, rootId: String): String =
        sql.select(ENGINE_EVENT.ID).from(ENGINE_EVENT).where(ENGINE_EVENT.PLAY_ID.eq(play)).orderBy(ENGINE_EVENT.ORDINAL.desc()).limit(1)
            .fetchOne(ENGINE_EVENT.ID)?.toString() ?: rootId
}
