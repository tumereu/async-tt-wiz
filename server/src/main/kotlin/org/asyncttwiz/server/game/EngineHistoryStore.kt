package org.asyncttwiz.server.game

import java.util.UUID
import org.asyncttwiz.protocol.game.EngineJson
import org.asyncttwiz.protocol.game.GameDefinitions
import org.asyncttwiz.protocol.game.GameEvent
import org.asyncttwiz.protocol.game.GameHistory
import org.asyncttwiz.protocol.game.GameModel
import org.asyncttwiz.protocol.game.HistoryNode
import org.asyncttwiz.protocol.game.HistoryStore
import org.asyncttwiz.server.StoredData
import org.asyncttwiz.server.generated.tables.EngineCheckpoint.ENGINE_CHECKPOINT
import org.asyncttwiz.server.generated.tables.EngineEvent.ENGINE_EVENT
import org.asyncttwiz.server.generated.tables.EnginePlay.ENGINE_PLAY
import org.jooq.DSLContext
import org.jooq.impl.DSL

/**
 * A play's history as engine events (I-D54 phase 2; V56): its card definitions stored once, its events, and checkpoints
 * every [GameHistory.CHECKPOINT_EVERY] events of depth. Reads what a [GameHistory] asks for (a line of events up to the
 * nearest checkpoint, or a main line's events back to a depth, in one or two queries) and keeps what it read. Writes go
 * through [create] and [append], inside the caller's game transaction.
 *
 * A [Shared] cache keeps a play's codec and the events decoded before across requests (events are immutable and their
 * ids never reused): events read from the database go into it at once, the request's own appends and deletes only once
 * its transaction committed ([committed]), so a rolled-back request leaves nothing behind.
 */
internal class EngineHistoryStore private constructor(
    private val sql: DSLContext, private val play: UUID, override val rootId: String, val codec: EngineJson,
    private val shared: Shared? = null,
) : HistoryStore {
    private val nodes = mutableMapOf<String, HistoryNode>()
    private val appended = mutableListOf<HistoryNode>()
    private val deleted = mutableSetOf<String>()
    private val checkpoints = mutableMapOf<String, String?>()

    /** What outlives one request for a play: its root, codec (the decoded definitions) and up to [capacity] decoded
     * events, least recently used first out. */
    class Shared(val rootId: String, val codec: EngineJson, private val capacity: Int = 256) {
        private val nodes = object : LinkedHashMap<String, HistoryNode>(64, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, HistoryNode>?) = size > capacity
        }
        fun get(id: String): HistoryNode? = synchronized(this) { nodes[id] }
        fun put(node: HistoryNode) = synchronized(this) { nodes[node.event.id] = node }
        fun remove(ids: Collection<String>) = synchronized(this) { ids.forEach { nodes.remove(it) } }
    }

    /** The shared cache this store reads through (a new one when it had none). */
    val cache: Shared = shared ?: Shared(rootId, codec)

    /** The transaction committed: this request's appends and deletes go into the shared cache. */
    fun committed() {
        cache.remove(deleted)
        appended.filter { it.event.id !in deleted }.forEach(cache::put)
    }

    private fun cached(id: String): HistoryNode? {
        if (id in deleted) return null
        nodes[id]?.let { return it }
        return cache.get(id)?.also { nodes[id] = it }
    }

    override fun node(id: String): HistoryNode? = cached(id) ?: if (id in deleted) null else
        sql.select(ENGINE_EVENT.DEPTH, ENGINE_EVENT.BODY).from(ENGINE_EVENT)
        .where(ENGINE_EVENT.PLAY_ID.eq(play)).and(ENGINE_EVENT.ID.eq(uuid(id) ?: return null)).fetchOne()
        ?.let { HistoryNode(decode(id, it.value2()!!), it.value1()!!) }?.also { nodes[id] = it; cache.put(it) }

    /**
     * [head] and its ancestors deeper than [depth], nearest first: from the events at hand, then the rest in two
     * statements however many there are (the line's ids in one recursive query, then the events not decoded yet).
     */
    override fun ancestors(head: String, depth: Int): List<HistoryNode> {
        val out = mutableListOf<HistoryNode>()
        var cursor = head
        while (cursor != rootId) {
            val node = cached(cursor) ?: break
            if (node.depth <= depth) return out
            out += node
            cursor = node.event.parentId
        }
        if (cursor == rootId || cursor in deleted) return out
        val start = uuid(cursor) ?: return out
        val ids = sql.fetch("""
            WITH RECURSIVE line(id, parent_id, depth) AS (
                SELECT e.id, e.parent_id, e.depth FROM engine_event e WHERE e.play_id = ? AND e.id = ? AND e.depth > ?
                UNION ALL
                SELECT e.id, e.parent_id, e.depth FROM line JOIN engine_event e ON e.play_id = ? AND e.id = line.parent_id
                WHERE e.depth > ?
            )
            SELECT id FROM line ORDER BY depth DESC""", play, start, depth, play, depth).map { it.get(0, UUID::class.java).toString() }
        val missing = ids.filter { cached(it) == null }
        if (missing.isNotEmpty()) sql.select(ENGINE_EVENT.ID, ENGINE_EVENT.DEPTH, ENGINE_EVENT.BODY).from(ENGINE_EVENT)
            .where(ENGINE_EVENT.PLAY_ID.eq(play)).and(ENGINE_EVENT.ID.`in`(missing.map(UUID::fromString))).fetch().forEach { row ->
                val id = row.value1()!!.toString()
                HistoryNode(decode(id, row.value3()!!), row.value2()!!).also { nodes[id] = it; cache.put(it) }
            }
        for (id in ids) out += cached(id) ?: break
        return out
    }

    override fun checkpoint(id: String): String? = if (id in checkpoints) checkpoints[id] else
        sql.select(ENGINE_CHECKPOINT.STATE).from(ENGINE_CHECKPOINT).where(ENGINE_CHECKPOINT.PLAY_ID.eq(play))
            .and(ENGINE_CHECKPOINT.EVENT_ID.eq(uuid(id) ?: return null)).fetchOne(ENGINE_CHECKPOINT.STATE).also { checkpoints[id] = it }

    /** One recursive query: [id]'s event and its ancestors up to the nearest one with a checkpoint (or [limit]). */
    override fun line(id: String, limit: Int): List<HistoryNode> {
        val start = uuid(id) ?: return emptyList()
        if (id == rootId || checkpoint(id) != null) return emptyList()
        val rows = sql.fetch("""
            WITH RECURSIVE line(id, parent_id, depth, body, n) AS (
                SELECT e.id, e.parent_id, e.depth, e.body, 1 FROM engine_event e WHERE e.play_id = ? AND e.id = ?
                UNION ALL
                SELECT e.id, e.parent_id, e.depth, e.body, line.n + 1 FROM line JOIN engine_event e ON e.play_id = ? AND e.id = line.parent_id
                WHERE line.n < ? AND NOT EXISTS (SELECT 1 FROM engine_checkpoint c WHERE c.play_id = ? AND c.event_id = line.parent_id)
            )
            SELECT id, depth, body FROM line ORDER BY n""", play, start, play, limit, play)
        return rows.map { row ->
            val eventId = row.get(0, UUID::class.java).toString()
            cached(eventId) ?: HistoryNode(decode(eventId, row.get(2, String::class.java)), row.get(1, Int::class.java))
                .also { nodes[eventId] = it; cache.put(it) }
        }
    }

    private fun decode(id: String, body: String): GameEvent = StoredData.decode("engine event", id) { codec.decodeEvent(body) }

    companion object {
        private fun uuid(id: String): UUID? = runCatching { UUID.fromString(id) }.getOrNull()

        /** Starts [play]'s engine history: its root [rootId], its definitions and the state before its first event. */
        fun create(sql: DSLContext, play: UUID, rootId: UUID, definitions: GameDefinitions, initial: GameModel): EngineHistoryStore {
            val codec = EngineJson(definitions)
            sql.insertInto(ENGINE_PLAY).set(ENGINE_PLAY.PLAY_ID, play).set(ENGINE_PLAY.ROOT_ID, rootId)
                .set(ENGINE_PLAY.DEFINITIONS, codec.encodeDefinitions()).execute()
            sql.insertInto(ENGINE_CHECKPOINT).set(ENGINE_CHECKPOINT.PLAY_ID, play).set(ENGINE_CHECKPOINT.EVENT_ID, rootId)
                .set(ENGINE_CHECKPOINT.STATE, codec.encodeModel(initial)).execute()
            return EngineHistoryStore(sql, play, rootId.toString(), codec)
        }

        /** [play]'s engine history, or null when it has none; [shared]: what earlier requests kept of it (its
         * definitions are then not read and decoded again). */
        fun open(sql: DSLContext, play: UUID, shared: Shared? = null): EngineHistoryStore? {
            if (shared != null) {
                val root = sql.select(ENGINE_PLAY.ROOT_ID).from(ENGINE_PLAY).where(ENGINE_PLAY.PLAY_ID.eq(play)).fetchOne(ENGINE_PLAY.ROOT_ID)
                    ?: return null
                if (root.toString() == shared.rootId) return EngineHistoryStore(sql, play, shared.rootId, shared.codec, shared)
            }
            val row = sql.select(ENGINE_PLAY.ROOT_ID, ENGINE_PLAY.DEFINITIONS).from(ENGINE_PLAY).where(ENGINE_PLAY.PLAY_ID.eq(play)).fetchOne()
                ?: return null
            val definitions = StoredData.decode("engine definitions", play) { EngineJson.decodeDefinitions(row.value2()!!) }
            return EngineHistoryStore(sql, play, row.value1()!!.toString(), EngineJson(definitions))
        }
    }

    /** Deletes the events [ids] and their checkpoints (a rewind takes the events after its target away, I-D56 §10). */
    fun delete(ids: Collection<String>) {
        if (ids.isEmpty()) return
        val uuids = ids.map(UUID::fromString)
        sql.deleteFrom(ENGINE_CHECKPOINT).where(ENGINE_CHECKPOINT.PLAY_ID.eq(play)).and(ENGINE_CHECKPOINT.EVENT_ID.`in`(uuids)).execute()
        sql.deleteFrom(ENGINE_EVENT).where(ENGINE_EVENT.PLAY_ID.eq(play)).and(ENGINE_EVENT.ID.`in`(uuids)).execute()
        ids.forEach { nodes.remove(it); checkpoints.remove(it) }
        deleted += ids
    }

    /** Makes the stored event [id] the newest, so it is the main line's head (the event with the highest ordinal): a
     * rewind that adds no event leaves its target as the head (I-D56 §10). */
    fun raise(id: String) {
        val ordinal = (sql.select(DSL.max(ENGINE_EVENT.ORDINAL)).from(ENGINE_EVENT).where(ENGINE_EVENT.PLAY_ID.eq(play)).fetchOne(0, Long::class.java) ?: 0L) + 1
        sql.update(ENGINE_EVENT).set(ENGINE_EVENT.ORDINAL, ordinal).where(ENGINE_EVENT.PLAY_ID.eq(play)).and(ENGINE_EVENT.ID.eq(UUID.fromString(id))).execute()
    }

    /**
     * Stores [event] (applied to its parent's state, giving [state]) as the next event the server accepted, with its
     * checkpoint when its depth gets one ([GameHistory.checkpointFor]). Returns its node.
     */
    fun append(history: GameHistory, event: GameEvent, author: UUID, state: GameModel): HistoryNode {
        val parentDepth = history.depthOf(event.parentId)
        val node = HistoryNode(event, parentDepth + 1)
        // The caller holds the game's lock, so the next ordinal is free
        val ordinal = (sql.select(DSL.max(ENGINE_EVENT.ORDINAL)).from(ENGINE_EVENT).where(ENGINE_EVENT.PLAY_ID.eq(play)).fetchOne(0, Long::class.java) ?: 0L) + 1
        sql.insertInto(ENGINE_EVENT).set(ENGINE_EVENT.PLAY_ID, play).set(ENGINE_EVENT.ID, UUID.fromString(event.id))
            .set(ENGINE_EVENT.PARENT_ID, UUID.fromString(event.parentId)).set(ENGINE_EVENT.DEPTH, node.depth)
            .set(ENGINE_EVENT.ORDINAL, ordinal).set(ENGINE_EVENT.AUTHOR_ID, author).set(ENGINE_EVENT.BODY, codec.encodeEvent(event)).execute()
        nodes[event.id] = node
        deleted.remove(event.id)
        appended += node
        history.checkpointFor(node.depth, state)?.let { checkpoint ->
            sql.insertInto(ENGINE_CHECKPOINT).set(ENGINE_CHECKPOINT.PLAY_ID, play).set(ENGINE_CHECKPOINT.EVENT_ID, UUID.fromString(event.id))
                .set(ENGINE_CHECKPOINT.STATE, checkpoint).execute()
            checkpoints[event.id] = checkpoint
        }
        checkpoints.getOrPut(event.id) { null }
        return node
    }
}
