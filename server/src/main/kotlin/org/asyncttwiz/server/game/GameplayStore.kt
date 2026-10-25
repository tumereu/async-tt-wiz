package org.asyncttwiz.server.game

import java.io.ByteArrayOutputStream
import java.time.Clock
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.GZIPOutputStream
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.generated.tables.GamePlay.GAME_PLAY
import org.asyncttwiz.server.generated.tables.GameplayGroup.GAMEPLAY_GROUP
import org.asyncttwiz.server.generated.tables.GameplayMutation.GAMEPLAY_MUTATION
import org.asyncttwiz.server.generated.tables.GameplayTreeControl.GAMEPLAY_TREE_CONTROL
import org.jooq.DSLContext
import org.jooq.JSONB

/** Relational mutations and coherent exports share the existing game-locked execution domain. */
internal class GameplayStore(private val clock: Clock = Clock.systemUTC()) {
    private data class Export(val account: UUID, val manifest: GameplayManifest,
        val chunks: List<ByteArray>, val expires: java.time.Instant)
    private val exports = ConcurrentHashMap<String, Export>()
    private data class Head(val revision: Long, val root: PlayState, val tree: GameplayTree?, val records: EndpointReadCache?)
    private val transactionRecords = ThreadLocal<Pair<java.lang.ref.WeakReference<GameplayTree>, EndpointReadCache>?>()
    private val heads = object : LinkedHashMap<String, Head>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Head>?) = size > 16
    }

    /** Only committed generations enter the bounded cache. Every caller owns its cache slots. */
    fun committed(revision: Long, tree: GameplayTree) = synchronized(heads) {
        // Never retain transaction-bound lazy SQL collections/connections past commit.
        heads[tree.initial.id] = Head(revision, tree.initial,
            tree.takeUnless { it.nodes is IndexedMap<*, *> }?.materializedCopy(),
            transactionRecords.get()?.takeIf { it.first.get() === tree }?.second)
        transactionRecords.remove()
    }

    /** Caller holds the game's admission/execution lock: state, record coverage and results share one R. */
    fun create(sql: DSLContext, account: UUID, head: GameHead, statuses: List<GameRequestStatus>): GameplayManifest {
        val play = head.play ?: throw GameProtocolFailure("inapplicable")
        read(sql, play, GameWire.ordinal(head.revision)) // Verify the durable checkpoint, not replay history.
        // A full transfer necessarily reads the requested records. They were validated when
        // accepted; do not decode/replay the entire tree merely to export those same bytes.
        val records = storedRecords(sql, play)
        val bytes = records.joinToString("\n", postfix = "\n") { GameWire.json.encodeToString(it) }.toByteArray(Charsets.UTF_8)
        val decoded = (bytes.indices step GameplaySync.CHUNK_BYTES).map { start ->
            bytes.copyOfRange(start, minOf(start + GameplaySync.CHUNK_BYTES, bytes.size)) }
        val chunks = decoded.map { chunk -> ByteArrayOutputStream().also { output ->
            GZIPOutputStream(output).use { it.write(chunk) }
        }.toByteArray() }
        val manifest = GameplayManifest(UUID.randomUUID().toString(), head.gameId, play.id, head.revision,
            decoded.mapIndexed { index, chunk -> GameplayChunk(index, chunk.size, GameplaySync.digest(chunk)) }, statuses,
            recordCount = records.size, head = head)
            .let { it.copy(digest = GameplaySync.manifestDigest(it)) }
        exports.entries.removeIf { !it.value.expires.isAfter(clock.instant()) }
        exports[manifest.id] = Export(account, manifest, chunks, clock.instant().plus(Duration.ofMinutes(10)))
        return manifest
    }
    private fun export(account: UUID, id: String): Export {
        val export = exports[id]?.takeIf { it.account == account } ?: throw GameProtocolFailure("accessDenied")
        if (!export.expires.isAfter(clock.instant())) {
            exports.remove(id, export); throw GameProtocolFailure("baselineExpired")
        }
        return export
    }
    fun manifest(account: UUID, id: String) = export(account, id).manifest
    fun chunk(account: UUID, id: String, index: Int): ByteArray = export(account, id).chunks.getOrNull(index)
        ?: throw GameProtocolFailure("invalidInput")

    /** Called inside the execution transaction, before outcome/revision/outbox. */
    private val json = GameWire.json
    private fun id(value: String) = UUID.fromString(value)
    fun initialize(sql: DSLContext, state: PlayState) {
        for (record in TreeSync.records(TreeSync.generation(GameplayTree(state)))) {
            if (record.kind !in setOf("root", "definition", "node")) control(sql, state.id, record)
        }
        GameplayEndpoint(sql, state.id).initialize(GameplayTree(state), 0)
    }

    /** Versioned endpoint plus transaction-scoped keyed reads, including on a cache miss. */
    fun read(sql: DSLContext, state: PlayState, revision: Long? = null): GameplayTree {
        val cached = synchronized(heads) { heads[state.id]?.takeIf { it.revision == revision } }
        cached?.tree?.takeIf { it.preparedState(it.currentNodeId) == state }?.let { return it.materializedCopy() }
        val root = cached?.root ?: json.decodeFromString<PlayState>(sql.select(GAME_PLAY.INITIAL_PAYLOAD).from(GAME_PLAY)
            .where(GAME_PLAY.ID.eq(id(state.id))).fetchSingle(GAME_PLAY.INITIAL_PAYLOAD)!!)
        val records = cached?.records?.copy() ?: EndpointReadCache()
        return GameplayEndpoint(sql, state.id, records).read(state, root).also { transactionRecords.set(java.lang.ref.WeakReference(it) to records) }
    }

    /** Explicit exhaustive maintenance only. Never called by exports, commands, or recovery. */
    fun readFull(sql: DSLContext, state: PlayState): GameplayTree {
        return TreeSync.decode(storedRecords(sql, state).asSequence(), state.gameId, state.id).tree
    }

    private fun storedRecords(sql: DSLContext, state: PlayState): List<TreeSyncRecord> {
        val root = json.decodeFromString<PlayState>(sql.select(GAME_PLAY.INITIAL_PAYLOAD).from(GAME_PLAY)
            .where(GAME_PLAY.ID.eq(id(state.id))).fetchSingle(GAME_PLAY.INITIAL_PAYLOAD)!!)
        val records = mutableListOf(TreeSyncRecord("root", root.root.id, json.encodeToJsonElement(root.copy(definitions = emptyList()))))
        root.definitions.forEach { records.add(TreeSyncRecord("definition", it.digest, json.encodeToJsonElement(it))) }
        sql.selectFrom(GAMEPLAY_MUTATION).where(GAMEPLAY_MUTATION.PLAY_ID.eq(id(state.id)))
            .and(GAMEPLAY_MUTATION.RETAINED.eq(true)).fetch().forEach {
                records.add(TreeSyncRecord("node", it.id.toString(), json.parseToJsonElement(it.payload!!.data()))) }
        sql.selectFrom(GAMEPLAY_TREE_CONTROL).where(GAMEPLAY_TREE_CONTROL.PLAY_ID.eq(id(state.id))).fetch().forEach {
            records.add(TreeSyncRecord(it.kind!!, it.id!!, json.parseToJsonElement(it.payload!!.data()))) }
        return records
    }

    /** Atomic endpoint/record commits have no unapplied transitions. Recovery verifies their
     * version, digest, and pointers; exhaustive validation is an explicit maintenance operation. */
    fun recover(sql: DSLContext, checkpoint: PlayState) {
        read(sql, checkpoint) // Version, pointer, and indexed endpoint integrity; no root replay.
    }

    fun rebuild(sql: DSLContext, state: PlayState, revision: Long) {
        val tree = readFull(sql, state)
        // One-off maintenance is deliberately exhaustive, unlike readiness or normal reads.
        sql.deleteFrom(org.asyncttwiz.server.generated.tables.GameplayEndpointRecord.GAMEPLAY_ENDPOINT_RECORD)
            .where(org.asyncttwiz.server.generated.tables.GameplayEndpointRecord.GAMEPLAY_ENDPOINT_RECORD.PLAY_ID.eq(id(state.id))).execute()
        sql.deleteFrom(org.asyncttwiz.server.generated.tables.GameplayEndpointEdge.GAMEPLAY_ENDPOINT_EDGE)
            .where(org.asyncttwiz.server.generated.tables.GameplayEndpointEdge.GAMEPLAY_ENDPOINT_EDGE.PLAY_ID.eq(id(state.id))).execute()
        for (record in TreeSync.records(TreeSync.generation(tree))) if (record.kind.startsWith("redo-")) control(sql, state.id, record)
        sql.deleteFrom(GAMEPLAY_TREE_CONTROL).where(GAMEPLAY_TREE_CONTROL.PLAY_ID.eq(id(state.id))).and(GAMEPLAY_TREE_CONTROL.KIND.eq("redo")).execute()
        GameplayEndpoint(sql, state.id).initialize(tree, revision)
    }

    private fun control(sql: DSLContext, play: String, record: TreeSyncRecord) {
        sql.insertInto(GAMEPLAY_TREE_CONTROL).set(GAMEPLAY_TREE_CONTROL.PLAY_ID, id(play))
            .set(GAMEPLAY_TREE_CONTROL.KIND, record.kind).set(GAMEPLAY_TREE_CONTROL.ID, record.id)
            .set(GAMEPLAY_TREE_CONTROL.PAYLOAD, JSONB.valueOf(record.payload.toString()))
            .onConflict(GAMEPLAY_TREE_CONTROL.PLAY_ID, GAMEPLAY_TREE_CONTROL.KIND, GAMEPLAY_TREE_CONTROL.ID).doUpdate()
            .set(GAMEPLAY_TREE_CONTROL.PAYLOAD, JSONB.valueOf(record.payload.toString())).execute()
    }

    /** Same transaction as the original inbox/result/revision/outbox. Only changed records are written. */
    fun save(sql: DSLContext, before: GameplayTree, next: GameplayTree, revision: Long): GameplayTreeDelta {
        val delta = TreeSync.delta(before, next)
        for (key in delta.removals) {
            if (key.kind == "node") sql.update(GAMEPLAY_MUTATION).set(GAMEPLAY_MUTATION.RETAINED, false)
                .where(GAMEPLAY_MUTATION.PLAY_ID.eq(id(next.initial.id))).and(GAMEPLAY_MUTATION.ID.eq(id(key.id))).execute()
            else if (key.kind !in setOf("root", "definition")) sql.deleteFrom(GAMEPLAY_TREE_CONTROL)
                .where(GAMEPLAY_TREE_CONTROL.PLAY_ID.eq(id(next.initial.id))).and(GAMEPLAY_TREE_CONTROL.KIND.eq(key.kind))
                .and(GAMEPLAY_TREE_CONTROL.ID.eq(key.id)).execute()
        }
        for (record in delta.upserts) when (record.kind) {
            "node" -> {
                val mutation = next.nodes.getValue(record.id)
                val state = requireNotNull(next.stateAt(mutation.id))
                val group = requireNotNull(state.currentGroup)
                sql.insertInto(GAMEPLAY_GROUP).set(GAMEPLAY_GROUP.ID, id(group.id)).set(GAMEPLAY_GROUP.GAME_ID, id(state.gameId))
                    .set(GAMEPLAY_GROUP.PLAY_ID, id(state.id)).set(GAMEPLAY_GROUP.AUTHOR_ID, id(group.author))
                    .set(GAMEPLAY_GROUP.FIRST_NODE_ID, id(group.firstNodeId)).set(GAMEPLAY_GROUP.LAST_NODE_ID, id(group.lastNodeId))
                    .set(GAMEPLAY_GROUP.PAYLOAD, JSONB.valueOf(json.encodeToString(group)))
                    .onConflict(GAMEPLAY_GROUP.ID).doUpdate().set(GAMEPLAY_GROUP.LAST_NODE_ID, id(group.lastNodeId))
                    .set(GAMEPLAY_GROUP.PAYLOAD, JSONB.valueOf(json.encodeToString(group))).execute()
                sql.insertInto(GAMEPLAY_MUTATION).set(GAMEPLAY_MUTATION.ID, id(mutation.id))
                    .set(GAMEPLAY_MUTATION.GAME_ID, id(state.gameId)).set(GAMEPLAY_MUTATION.PLAY_ID, id(state.id))
                    .set(GAMEPLAY_MUTATION.PARENT_ID, id(mutation.parentId)).set(GAMEPLAY_MUTATION.BRANCH_ID, id(mutation.branchId))
                    .set(GAMEPLAY_MUTATION.AUTHOR_ID, id(mutation.author)).set(GAMEPLAY_MUTATION.REQUEST_ID, id(mutation.requestId))
                    .set(GAMEPLAY_MUTATION.GROUP_ID, id(group.id)).set(GAMEPLAY_MUTATION.EFFECTIVE_ORDINAL, revision)
                    .set(GAMEPLAY_MUTATION.PAYLOAD, JSONB.valueOf(json.encodeToString(mutation)))
                    .onConflict(GAMEPLAY_MUTATION.ID).doUpdate().set(GAMEPLAY_MUTATION.PARENT_ID, id(mutation.parentId))
                    .set(GAMEPLAY_MUTATION.RETAINED, true).set(GAMEPLAY_MUTATION.GROUP_ID, id(group.id))
                    .set(GAMEPLAY_MUTATION.NODE_VERSION, GAMEPLAY_MUTATION.NODE_VERSION.plus(1))
                    .set(GAMEPLAY_MUTATION.PAYLOAD, JSONB.valueOf(json.encodeToString(mutation))).execute()
            }
            "root", "definition" -> Unit
            else -> control(sql, next.initial.id, record)
        }
        val records = transactionRecords.get()?.takeIf { it.first.get() === before }?.second ?: EndpointReadCache()
        GameplayEndpoint(sql, next.initial.id, records).save(before, next, delta, revision)
        transactionRecords.set(java.lang.ref.WeakReference(next) to records)
        if (before.initial !== next.initial) check(sql.update(GAME_PLAY).set(GAME_PLAY.INITIAL_PAYLOAD, json.encodeToString(next.initial))
            .where(GAME_PLAY.ID.eq(id(next.initial.id))).execute() == 1)
        return delta
    }
    fun history(sql: DSLContext, state: PlayState, revision: Long, after: Long): GameplayHistoryPage {
        val rows = sql.selectFrom(GAMEPLAY_MUTATION).where(GAMEPLAY_MUTATION.PLAY_ID.eq(id(state.id)))
            .and(GAMEPLAY_MUTATION.RETAINED.eq(true))
            .and(GAMEPLAY_MUTATION.EFFECTIVE_ORDINAL.gt(after)).orderBy(GAMEPLAY_MUTATION.EFFECTIVE_ORDINAL).limit(101).fetch()
        val page = rows.take(100)
        val groups = if (page.isEmpty()) emptyList() else sql.selectFrom(GAMEPLAY_GROUP)
            .where(GAMEPLAY_GROUP.PLAY_ID.eq(id(state.id))).and(GAMEPLAY_GROUP.ID.`in`(page.map { it.groupId }))
            .orderBy(GAMEPLAY_GROUP.ID).fetch().map { json.decodeFromString<GameplayGroup>(it.payload!!.data()) }
        return GameplayHistoryPage(state.id, page.map { json.decodeFromString(it.payload!!.data()) }, groups,
            revision.toString(), if (rows.size > 100) page.last().effectiveOrdinal.toString() else null)
    }
}
