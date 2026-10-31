package org.asyncttwiz.server.game

import java.io.ByteArrayOutputStream
import java.time.Clock
import java.time.Duration
import java.util.UUID
import java.util.zip.GZIPOutputStream
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.StoredData
import org.asyncttwiz.server.generated.tables.GamePlay.GAME_PLAY
import org.asyncttwiz.server.generated.tables.GameplayGroup.GAMEPLAY_GROUP
import org.asyncttwiz.server.generated.tables.GameplayMutation.GAMEPLAY_MUTATION
import org.asyncttwiz.server.generated.tables.GameplayTreeControl.GAMEPLAY_TREE_CONTROL
import org.jooq.DSLContext
import org.jooq.JSONB
import org.jooq.impl.DSL

/** Relational mutations and coherent exports share the existing game-locked execution domain. */
internal class GameplayStore(private val clock: Clock = Clock.systemUTC(),
    private val accountByteBudget: Long = 32L * 1024 * 1024,
    private val globalByteBudget: Long = 128L * 1024 * 1024,
    private val accountCreationLimit: Int = 60,
    private val globalCreationLimit: Int = 240) {
    init { require(accountByteBudget > 0 && globalByteBudget > 0 && accountCreationLimit > 0 && globalCreationLimit > 0) }
    private data class Export(val account: UUID, val manifest: GameplayManifest,
        val chunks: List<ByteArray>, val expires: java.time.Instant, val bytes: Long)
    private val exports = linkedMapOf<String, Export>()
    private data class CreationWindow(val start: java.time.Instant, var count: Int = 0)
    private val accountCreations = mutableMapOf<UUID, CreationWindow>()
    private var globalCreations = CreationWindow(clock.instant())

    /** Runs periodically even without baseline traffic, and also before capacity decisions. */
    @Synchronized fun evictExpiredExports() {
        val now = clock.instant()
        exports.entries.removeIf { !it.value.expires.isAfter(now) }
        accountCreations.entries.removeIf { !it.value.start.plusSeconds(60).isAfter(now) }
    }

    private fun admitCreation(account: UUID) {
        val now = clock.instant()
        if (!globalCreations.start.plusSeconds(60).isAfter(now)) globalCreations = CreationWindow(now)
        if (globalCreations.count >= globalCreationLimit) throw GameProtocolFailure("baselineRateLimited", true)
        val window = accountCreations.getOrPut(account) { CreationWindow(now) }
        if (window.count >= accountCreationLimit) throw GameProtocolFailure("baselineRateLimited", true)
        window.count++; globalCreations.count++
    }

    private fun retain(account: UUID, manifest: GameplayManifest, chunks: List<ByteArray>): GameplayManifest {
        // Include the retained endpoint/status metadata, not just highly compressible tree bytes.
        // Shared arrays are charged conservatively for each distinct immutable manifest.
        val bytes = chunks.sumOf { it.size.toLong() } + GameWire.json.encodeToString(manifest).toByteArray().size
        if (bytes > accountByteBudget || bytes > globalByteBudget) throw GameProtocolFailure("baselineTooLarge")
        while (exports.values.filter { it.account == account }.sumOf { it.bytes } + bytes > accountByteBudget) {
            exports.remove(exports.entries.first { it.value.account == account }.key)
        }
        while (exports.values.sumOf { it.bytes } + bytes > globalByteBudget) exports.remove(exports.keys.first())
        exports[manifest.id] = Export(account, manifest, chunks, clock.instant().plus(Duration.ofMinutes(10)), bytes)
        return manifest
    }
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
    @Synchronized fun create(sql: DSLContext, account: UUID, head: GameHead, statuses: List<GameRequestStatus>): GameplayManifest {
        val play = head.play ?: throw GameProtocolFailure("inapplicable")
        evictExpiredExports()
        val retained = exports.values.filter { it.account == account && it.manifest.gameId == head.gameId &&
            it.manifest.playId == play.id && it.manifest.revision == head.revision }
        retained.firstOrNull { it.manifest.statuses == statuses }?.let { return it.manifest }
        // Admission precedes checkpoint reads, full-history SQL, serialization and compression.
        admitCreation(account)
        retained.firstOrNull()?.let { export ->
            // Request-status variants stay immutable, but share the same pinned tree and endpoint.
            val manifest = export.manifest.copy(id = UUID.randomUUID().toString(), statuses = statuses, digest = "")
                .let { it.copy(digest = GameplaySync.manifestDigest(it)) }
            return retain(account, manifest, export.chunks)
        }
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
        return retain(account, manifest, chunks)
    }
    private fun export(account: UUID, id: String): Export {
        val export = exports[id]?.takeIf { it.account == account } ?: throw GameProtocolFailure("accessDenied")
        if (!export.expires.isAfter(clock.instant())) {
            exports.remove(id, export); throw GameProtocolFailure("baselineExpired")
        }
        return export
    }
    @Synchronized fun manifest(account: UUID, id: String) = export(account, id).manifest
    @Synchronized fun chunk(account: UUID, id: String, index: Int): ByteArray = export(account, id).chunks.getOrNull(index)
        ?: throw GameProtocolFailure("invalidInput")

    /** Called inside the execution transaction, before outcome/revision/outbox. */
    private val json = GameWire.json
    private fun id(value: String) = UUID.fromString(value)
    fun initialize(sql: DSLContext, state: PlayState) {
        controls(sql, state.id, TreeSync.records(TreeSync.generation(GameplayTree(state))).filter { it.kind !in setOf("root", "definition", "node") }.toList())
        GameplayEndpoint(sql, state.id).initialize(GameplayTree(state), 0)
    }

    /** Versioned endpoint plus transaction-scoped keyed reads, including on a cache miss. */
    fun readCurrent(sql: DSLContext, game: org.asyncttwiz.server.generated.tables.records.GameRecord): GameplayTree? {
        val play = game.currentPlay ?: return null
        val checkpoint = GameplayEndpoint.checkpoint(sql, play)
        val state = StoredData.decode("game play", game.id) { json.decodeFromString<PlayState>(checkpoint.payload) }
        check(state.gameId == game.id.toString() && state.id == play.toString())
        return read(sql, state, game.revision, checkpoint)
    }

    fun read(sql: DSLContext, state: PlayState, revision: Long? = null,
        checkpoint: GameplayEndpoint.Checkpoint? = null): GameplayTree {
        val cached = synchronized(heads) { heads[state.id]?.takeIf { it.revision == revision } }
        cached?.tree?.takeIf { it.preparedState(it.currentNodeId) == state }?.let { return it.materializedCopy() }
        val root = cached?.root ?: sql.select(GAME_PLAY.INITIAL_PAYLOAD).from(GAME_PLAY).where(GAME_PLAY.ID.eq(id(state.id)))
            .fetchSingle(GAME_PLAY.INITIAL_PAYLOAD)!!.let { StoredData.decode("game play start", state.gameId) {
                json.decodeFromString<PlayState>(it) } }
        val records = cached?.records?.copy() ?: EndpointReadCache()
        val endpoint = GameplayEndpoint(sql, state.id, records)
        return endpoint.read(state, root, checkpoint ?: GameplayEndpoint.checkpoint(sql, id(state.id)))
            .also { transactionRecords.set(java.lang.ref.WeakReference(it) to records) }
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
        controls(sql, state.id, TreeSync.records(TreeSync.generation(tree)).filter { it.kind.startsWith("redo-") }.toList())
        sql.deleteFrom(GAMEPLAY_TREE_CONTROL).where(GAMEPLAY_TREE_CONTROL.PLAY_ID.eq(id(state.id))).and(GAMEPLAY_TREE_CONTROL.KIND.eq("redo")).execute()
        GameplayEndpoint(sql, state.id).initialize(tree, revision)
    }

    private fun controls(sql: DSLContext, play: String, records: List<TreeSyncRecord>) {
        for (chunk in records.chunked(1000)) {
            val insert = sql.insertInto(GAMEPLAY_TREE_CONTROL, GAMEPLAY_TREE_CONTROL.PLAY_ID,
                GAMEPLAY_TREE_CONTROL.KIND, GAMEPLAY_TREE_CONTROL.ID, GAMEPLAY_TREE_CONTROL.PAYLOAD)
            for (record in chunk) insert.values(id(play), record.kind, record.id, JSONB.valueOf(record.payload.toString()))
            insert.onConflict(GAMEPLAY_TREE_CONTROL.PLAY_ID, GAMEPLAY_TREE_CONTROL.KIND, GAMEPLAY_TREE_CONTROL.ID).doUpdate()
                .set(GAMEPLAY_TREE_CONTROL.PAYLOAD, DSL.excluded(GAMEPLAY_TREE_CONTROL.PAYLOAD)).execute()
        }
    }

    /** Same transaction as the original inbox/result/revision/outbox. Only changed records are written. */
    fun save(sql: DSLContext, before: GameplayTree, next: GameplayTree, revision: Long, requestId: String? = null): GameplayTreeDelta {
        val delta = TreeSync.delta(before, next)
        val removedNodes = delta.removals.filter { it.kind == "node" }.map { id(it.id) }
        if (removedNodes.isNotEmpty()) sql.update(GAMEPLAY_MUTATION).set(GAMEPLAY_MUTATION.RETAINED, false)
            .where(GAMEPLAY_MUTATION.PLAY_ID.eq(id(next.initial.id))).and(GAMEPLAY_MUTATION.ID.`in`(removedNodes)).execute()
        val removedControls = delta.removals.filter { it.kind !in setOf("root", "definition", "node") }
        if (removedControls.isNotEmpty()) sql.deleteFrom(GAMEPLAY_TREE_CONTROL)
            .where(GAMEPLAY_TREE_CONTROL.PLAY_ID.eq(id(next.initial.id)))
            .and(DSL.row(GAMEPLAY_TREE_CONTROL.KIND, GAMEPLAY_TREE_CONTROL.ID).`in`(removedControls.map { DSL.row(it.kind, it.id) })).execute()
        var memberIndex = 0
        for (record in delta.upserts) when (record.kind) {
            "node" -> {
                val mutation = next.nodes.getValue(record.id)
                val state = requireNotNull(next.stateAt(mutation.id))
                val group = requireNotNull(state.currentGroup)
                val groupPayload = JSONB.valueOf(json.encodeToString(group))
                val mutationPayload = JSONB.valueOf(record.payload.toString())
                sql.insertInto(GAMEPLAY_GROUP).set(GAMEPLAY_GROUP.ID, id(group.id)).set(GAMEPLAY_GROUP.GAME_ID, id(state.gameId))
                    .set(GAMEPLAY_GROUP.PLAY_ID, id(state.id)).set(GAMEPLAY_GROUP.AUTHOR_ID, id(group.author))
                    .set(GAMEPLAY_GROUP.FIRST_NODE_ID, id(group.firstNodeId)).set(GAMEPLAY_GROUP.LAST_NODE_ID, id(group.lastNodeId))
                    .set(GAMEPLAY_GROUP.PAYLOAD, groupPayload)
                    .onConflict(GAMEPLAY_GROUP.ID).doUpdate().set(GAMEPLAY_GROUP.LAST_NODE_ID, id(group.lastNodeId))
                    .set(GAMEPLAY_GROUP.PAYLOAD, groupPayload).execute()
                sql.insertInto(GAMEPLAY_MUTATION).set(GAMEPLAY_MUTATION.ID, id(mutation.id))
                    .set(GAMEPLAY_MUTATION.GAME_ID, id(state.gameId)).set(GAMEPLAY_MUTATION.PLAY_ID, id(state.id))
                    .set(GAMEPLAY_MUTATION.PARENT_ID, id(mutation.parentId)).set(GAMEPLAY_MUTATION.BRANCH_ID, id(mutation.branchId))
                    .set(GAMEPLAY_MUTATION.AUTHOR_ID, id(mutation.author)).set(GAMEPLAY_MUTATION.REQUEST_ID, id(mutation.requestId))
                    .set(GAMEPLAY_MUTATION.COMMAND_REQUEST_ID, id(requestId ?: mutation.requestId))
                    .set(GAMEPLAY_MUTATION.MEMBER_INDEX, if (mutation.invocationId == requestId) memberIndex++ else 0)
                    .set(GAMEPLAY_MUTATION.GROUP_ID, id(group.id)).set(GAMEPLAY_MUTATION.EFFECTIVE_ORDINAL, revision)
                    .set(GAMEPLAY_MUTATION.PAYLOAD, mutationPayload)
                    .onConflict(GAMEPLAY_MUTATION.ID).doUpdate().set(GAMEPLAY_MUTATION.PARENT_ID, id(mutation.parentId))
                    .set(GAMEPLAY_MUTATION.RETAINED, true).set(GAMEPLAY_MUTATION.GROUP_ID, id(group.id))
                    .set(GAMEPLAY_MUTATION.NODE_VERSION, GAMEPLAY_MUTATION.NODE_VERSION.plus(1))
                    .set(GAMEPLAY_MUTATION.PAYLOAD, mutationPayload).execute()
            }
            "root", "definition" -> Unit
            else -> Unit
        }
        controls(sql, next.initial.id, delta.upserts.filter { it.kind !in setOf("root", "definition", "node") })
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
            .and(GAMEPLAY_MUTATION.EFFECTIVE_ORDINAL.gt(after)).orderBy(GAMEPLAY_MUTATION.EFFECTIVE_ORDINAL, GAMEPLAY_MUTATION.MEMBER_INDEX).limit(101).fetch()
        // A revision cursor cannot represent a partial batch. Include its entire boundary revision.
        val boundary = rows.take(100).lastOrNull()?.effectiveOrdinal
        val page = if (rows.size > 100 && rows[100].effectiveOrdinal == boundary) rows.takeWhile { it.effectiveOrdinal != boundary } +
            sql.selectFrom(GAMEPLAY_MUTATION).where(GAMEPLAY_MUTATION.PLAY_ID.eq(id(state.id)))
                .and(GAMEPLAY_MUTATION.RETAINED.eq(true)).and(GAMEPLAY_MUTATION.EFFECTIVE_ORDINAL.eq(boundary))
                .orderBy(GAMEPLAY_MUTATION.MEMBER_INDEX).fetch()
            else rows.take(100)
        val groups = if (page.isEmpty()) emptyList() else sql.selectFrom(GAMEPLAY_GROUP)
            .where(GAMEPLAY_GROUP.PLAY_ID.eq(id(state.id))).and(GAMEPLAY_GROUP.ID.`in`(page.map { it.groupId }))
            .orderBy(GAMEPLAY_GROUP.ID).fetch().map { json.decodeFromString<GameplayGroup>(it.payload!!.data()) }
        return GameplayHistoryPage(state.id, page.map { json.decodeFromString(it.payload!!.data()) }, groups,
            revision.toString(), if (rows.size > 100) page.last().effectiveOrdinal.toString() else null)
    }
}
