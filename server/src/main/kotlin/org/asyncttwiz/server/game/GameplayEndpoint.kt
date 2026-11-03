package org.asyncttwiz.server.game

import java.util.UUID
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.StoredData
import org.asyncttwiz.server.generated.tables.GamePlay.GAME_PLAY
import org.asyncttwiz.server.generated.tables.GameplayMutation.GAMEPLAY_MUTATION
import org.asyncttwiz.server.generated.tables.GameplayTreeControl.GAMEPLAY_TREE_CONTROL
import org.asyncttwiz.server.generated.tables.GameplayEndpointRecord.GAMEPLAY_ENDPOINT_RECORD as R
import org.asyncttwiz.server.generated.tables.GameplayEndpointEdge.GAMEPLAY_ENDPOINT_EDGE as E
import org.jooq.DSLContext
import org.jooq.JSONB
import org.jooq.impl.DSL

/** All reads stay inside the owning game transaction. Only the bounded board is serialized as a
 * checkpoint; history, annotation records, redo slots, and topology indices are independently keyed. */
internal class GameplayEndpoint(private val sql: DSLContext, private val play: String,
    private val cache: EndpointReadCache = EndpointReadCache()) {
    private val id = UUID.fromString(play)
    private val json = GameWire.json
    var recordsRead = 0; private set
    var recordsWritten = 0; private set
    private val pending = linkedMapOf<Pair<String, String>, JsonElement?>()
    private val edgeChanges = linkedMapOf<Triple<String, String, String>, Boolean>()

    private fun get(kind: String, key: String): JsonElement? {
        recordsRead++
        return cache.read("record/$kind/$key") { sql.select(R.PAYLOAD).from(R).where(R.PLAY_ID.eq(id)).and(R.KIND.eq(kind)).and(R.ID.eq(key))
            .fetchOne(R.PAYLOAD)?.let { json.parseToJsonElement(it.data()) } }
    }
    fun put(kind: String, key: String, value: JsonElement?) {
        recordsWritten++
        pending[kind to key] = value
        cache.write("record/$kind/$key", value)
    }
    private fun flush() {
        val removals = pending.filterValues { it == null }.keys
        for (chunk in removals.chunked(1000)) sql.deleteFrom(R).where(R.PLAY_ID.eq(id))
            .and(DSL.row(R.KIND, R.ID).`in`(chunk.map { DSL.row(it.first, it.second) })).execute()
        val upserts = pending.filterValues { it != null }
        for (chunk in upserts.entries.chunked(1000)) {
            val insert = sql.insertInto(R, R.PLAY_ID, R.KIND, R.ID, R.PAYLOAD)
            for ((key, value) in chunk) insert.values(id, key.first, key.second, JSONB.valueOf(value!!.toString()))
            insert.onConflict(R.PLAY_ID, R.KIND, R.ID).doUpdate().set(R.PAYLOAD, DSL.excluded(R.PAYLOAD)).execute()
        }
        pending.clear()
    }
    /** [get] of many [keys] of one [kind] in one read (cached ones as cached). */
    private fun getAll(kind: String, keys: List<String>): Map<String, JsonElement?> = cache.readAll(keys.associateBy { "record/$kind/$it" }) { missing ->
        recordsRead += missing.size
        sql.select(R.ID, R.PAYLOAD).from(R).where(R.PLAY_ID.eq(id)).and(R.KIND.eq(kind)).and(R.ID.`in`(missing))
            .fetch().associate { it.value1()!! to json.parseToJsonElement(it.value2()!!.data()) }
    }

    /* A position's ping-target set (the board's every card id, ~45 KB on a 600-card board) is stored once per play as a
     * "targets" record keyed by the digest of its JSON text, and the position names it by "targetsRef". Positions
     * written before that carry the set inline as "targets"; both read back the same. Target records are never
     * removed (content-addressed, so a reference stays valid; checkpoint maintenance keeps them too). */
    private fun position(stored: JsonElement): RetainedPosition {
        val fields = stored.jsonObject
        val ref = fields[TARGETS_REF]?.jsonPrimitive?.content ?: return json.decodeFromJsonElement(stored)
        val targets = cache.targets(ref) {
            val set = get(TARGETS, ref)
            StoredData.intact("game play", play, "position targets", set != null)
            json.decodeFromJsonElement<Set<PingTarget>>(set!!)
        }
        return json.decodeFromJsonElement<RetainedPosition>(JsonObject(fields - TARGETS_REF)).copy(targets = targets)
    }
    private fun stored(position: RetainedPosition): JsonElement {
        val encoded = json.encodeToJsonElement(position.copy(targets = null)).jsonObject
        val targets = position.targets ?: return encoded
        val ref = cache.targetsRef(targets) ?: run {
            val element = json.encodeToJsonElement(targets)
            val ref = GameplaySync.digest(element.toString().toByteArray(Charsets.UTF_8))
            if (!cache.knowsTargets(ref)) put(TARGETS, ref, element)
            cache.rememberTargets(ref, targets)
            ref
        }
        return JsonObject(encoded + (TARGETS_REF to JsonPrimitive(ref)))
    }
    /** The position records of [keys] (absent ones left out) as [get] reads them, with the target sets they name in the
     * same read: those not decoded yet go to the record cache for [position]. */
    private fun positionRecords(keys: List<String>): Map<String, JsonElement> {
        recordsRead += keys.size
        val p = R.`as`("p"); val t = R.`as`("t")
        return sql.select(p.ID, p.PAYLOAD, t.ID, t.PAYLOAD).from(p).leftJoin(t).on(t.PLAY_ID.eq(p.PLAY_ID)).and(t.KIND.eq(TARGETS))
            .and(t.ID.eq(DSL.field("{0} ->> {1}", String::class.java, p.PAYLOAD, DSL.inline(TARGETS_REF))))
            .where(p.PLAY_ID.eq(id)).and(p.KIND.eq(POSITION)).and(p.ID.`in`(keys)).fetch().associate { row ->
                row.value3()?.takeUnless(cache::knowsTargets)?.let { ref ->
                    cache.write("record/$TARGETS/$ref", json.parseToJsonElement(row.value4()!!.data())) }
                row.value1()!! to json.parseToJsonElement(row.value2()!!.data())
            }
    }
    private fun positions(): Map<String, RetainedPosition> = EndpointMap({ key ->
        recordsRead++
        cache.read("record/$POSITION/$key") { positionRecords(listOf(key))[key] }?.let(::position)
    }, { keys(POSITION) }, bulk = { keys ->
        cache.readAll(keys.associateBy { "record/$POSITION/$it" }, ::positionRecords)
            .mapNotNull { (key, value) -> value?.let { key to position(it) } }.toMap()
    })
    private fun keys(kind: String) = sql.select(R.ID).from(R).where(R.PLAY_ID.eq(id)).and(R.KIND.eq(kind)).fetch(R.ID).filterNotNull().toSet()
    private inline fun <reified T : Any> map(kind: String): Map<String, T> = EndpointMap(
        { key -> get(kind, key)?.let { json.decodeFromJsonElement<T>(it) } }, { keys(kind) },
        bulk = { keys -> getAll(kind, keys).mapNotNull { (key, value) -> value?.let { key to json.decodeFromJsonElement<T>(it) } }.toMap() })

    private inline fun <reified T : Any> controls(kind: String): Map<String, T> = EndpointMap({ key ->
        recordsRead++
        cache.read("control/$kind/$key") { sql.select(GAMEPLAY_TREE_CONTROL.PAYLOAD).from(GAMEPLAY_TREE_CONTROL)
            .where(GAMEPLAY_TREE_CONTROL.PLAY_ID.eq(id)).and(GAMEPLAY_TREE_CONTROL.KIND.eq(kind)).and(GAMEPLAY_TREE_CONTROL.ID.eq(key))
            .fetchOne(GAMEPLAY_TREE_CONTROL.PAYLOAD)?.let { json.parseToJsonElement(it.data()) } }?.let { json.decodeFromJsonElement<T>(it) }
    }, { sql.select(GAMEPLAY_TREE_CONTROL.ID).from(GAMEPLAY_TREE_CONTROL).where(GAMEPLAY_TREE_CONTROL.PLAY_ID.eq(id))
        .and(GAMEPLAY_TREE_CONTROL.KIND.eq(kind)).fetch(GAMEPLAY_TREE_CONTROL.ID).filterNotNull().toSet() },
        bulk = { keys -> cache.readAll(keys.associateBy { "control/$kind/$it" }) { missing ->
            recordsRead += missing.size
            sql.select(GAMEPLAY_TREE_CONTROL.ID, GAMEPLAY_TREE_CONTROL.PAYLOAD).from(GAMEPLAY_TREE_CONTROL)
                .where(GAMEPLAY_TREE_CONTROL.PLAY_ID.eq(id)).and(GAMEPLAY_TREE_CONTROL.KIND.eq(kind)).and(GAMEPLAY_TREE_CONTROL.ID.`in`(missing))
                .fetch().associate { it.value1()!! to json.parseToJsonElement(it.value2()!!.data()) }
        }.mapNotNull { (key, value) -> value?.let { key to json.decodeFromJsonElement<T>(it) } }.toMap() })

    private fun edges(kind: String): Map<String, Set<String>> = EndpointMap({ source ->
        val condition = E.PLAY_ID.eq(id).and(E.KIND.eq(kind)).and(E.SOURCE_ID.eq(source))
        EndpointSet({ target -> recordsRead++; sql.fetchExists(sql.selectOne().from(E).where(condition).and(E.TARGET_ID.eq(target))) },
            { get("degree/$kind", source)?.jsonPrimitive?.int ?: 0 },
            { recordsRead++; sql.select(E.TARGET_ID).from(E).where(condition).fetch(E.TARGET_ID).filterNotNull().toSet() })
    }, { sql.selectDistinct(E.SOURCE_ID).from(E).where(E.PLAY_ID.eq(id)).and(E.KIND.eq(kind)).fetch(E.SOURCE_ID).filterNotNull().toSet() },
        bulk = { sources ->
            // The same answers as the per-source reads above, for many sources in two reads
            val degrees = getAll("degree/$kind", sources)
            recordsRead++
            val targets = sql.select(E.SOURCE_ID, E.TARGET_ID).from(E).where(E.PLAY_ID.eq(id)).and(E.KIND.eq(kind)).and(E.SOURCE_ID.`in`(sources))
                .fetch().groupBy({ it.value1()!! }, { it.value2()!! }).mapValues { it.value.toSet() }
            sources.associateWith { source ->
                val known = targets[source].orEmpty()
                EndpointSet({ target -> target in known }, { degrees[source]?.jsonPrimitive?.int ?: 0 }, { known })
            }
        })

    private fun pingKey(reference: ReferencedEventContext, target: PingTarget) = buildJsonArray {
        add(json.encodeToJsonElement(reference)); add(json.encodeToJsonElement(target)) }.toString()

    private fun annotations(): EventAnnotationState {
        val active = EndpointMap<String, Map<Pair<ReferencedEventContext, PingTarget>, RecordedPingChange>>({ group ->
            EndpointMap<Pair<ReferencedEventContext, PingTarget>, RecordedPingChange>({ key -> get("active/$group", pingKey(key.first, key.second))?.let { json.decodeFromJsonElement<RecordedPingChange>(it) } },
                { keys("active/$group").map { key -> json.parseToJsonElement(key).jsonArray.let {
                    json.decodeFromJsonElement<ReferencedEventContext>(it[0]) to json.decodeFromJsonElement<PingTarget>(it[1]) } }.toSet() })
        }, { emptySet() })
        return EventAnnotationState(map("comment"), map("ping"), map("ping-ordinal")).restoreActive(active)
    }

    fun read(state: PlayState, root: PlayState, checkpoint: Checkpoint = checkpoint(sql, id)): GameplayTree {
        val nodes = EndpointMap<String, GameplayMutation>({ key ->
            recordsRead++
            cache.read("node/$key") { sql.select(GAMEPLAY_MUTATION.PAYLOAD).from(GAMEPLAY_MUTATION).where(GAMEPLAY_MUTATION.PLAY_ID.eq(id))
                .and(GAMEPLAY_MUTATION.ID.eq(UUID.fromString(key))).and(GAMEPLAY_MUTATION.RETAINED.eq(true))
                .fetchOne(GAMEPLAY_MUTATION.PAYLOAD)?.let { json.parseToJsonElement(it.data()) } }?.let { json.decodeFromJsonElement<GameplayMutation>(it) }
        }, { sql.select(GAMEPLAY_MUTATION.ID).from(GAMEPLAY_MUTATION).where(GAMEPLAY_MUTATION.PLAY_ID.eq(id))
            .and(GAMEPLAY_MUTATION.RETAINED.eq(true)).fetch(GAMEPLAY_MUTATION.ID).map { it.toString() }.toSet() },
            bulk = { keys -> cache.readAll(keys.associateBy { "node/$it" }) { missing ->
                recordsRead += missing.size
                sql.select(GAMEPLAY_MUTATION.ID, GAMEPLAY_MUTATION.PAYLOAD).from(GAMEPLAY_MUTATION).where(GAMEPLAY_MUTATION.PLAY_ID.eq(id))
                    .and(GAMEPLAY_MUTATION.ID.`in`(missing.map(UUID::fromString))).and(GAMEPLAY_MUTATION.RETAINED.eq(true))
                    .fetch().associate { it.value1()!!.toString() to json.parseToJsonElement(it.value2()!!.data()) }
            }.mapNotNull { (key, value) -> value?.let { key to json.decodeFromJsonElement<GameplayMutation>(it) } }.toMap() })
        val sizes = controls<Int>("redo-size")
        val redo = EndpointMap<String, List<GameplayRedoEntry>>({ account -> sizes[account]?.takeIf { it > 0 }?.let { size ->
            val slots = controls<GameplayRedoEntry>("redo-entry")
            EndpointList(size) { requireNotNull(slots["$account/$it"]) }
        } }, { sizes.keys.filter { sizes[it]!! > 0 }.toSet() })
        val current = controls<String>("current")["current"]
        val branch = controls<String>("selected-branch")["current"]
        intactPointers(state, current, branch)
        StoredData.intact("game", state.gameId, "checkpoint revision", checkpoint.revision >= 0)
        return GameplayTree(root, nodes, state.currentNodeId, redo, controls("removed-parent"), controls("branch-lineage"),
            state.currentBranchId, controls("branch-head"), controls<String>("selection-control")["current"])
            .restoreEndpoint(state.copy(annotations = annotations()), RetainedIndex(edges("children"), edges("readers"), positions(), true, edges("annotations")))
    }

    fun initialize(tree: GameplayTree, revision: Long) {
        val index = tree.endpointIndex()
        for ((key, value) in index.positions) put(POSITION, key, stored(value))
        for (node in tree.nodes.values) for (key in edgeKeys(node)) edgeChanges[key] = true
        val state = requireNotNull(tree.stateAt(tree.currentNodeId))
        for ((key, value) in state.annotations.commentsById) put("comment", key, json.encodeToJsonElement(value))
        for ((key, value) in state.annotations.pingsById) {
            put("ping", key, json.encodeToJsonElement(value))
            val active = state.annotations.activePing(value.recording.groupId, value.reference, value.target)
            put("active/${value.recording.groupId}", pingKey(value.reference, value.target), active?.let { json.encodeToJsonElement(it) })
        }
        for ((key, value) in state.annotations.lastOrdinals) put("ping-ordinal", key, JsonPrimitive(value))
        flushEdges()
        flush()
        checkpoint(state, revision)
    }

    private fun edgeKeys(node: GameplayMutation) = buildSet {
        add(Triple("children", node.parentId, node.id))
        node.references.forEach { add(Triple("readers", it.nodeId, node.id)) }
        node.transition.annotation?.let { annotation ->
            val key = if (node.transition.concerns == null || node.action is PlayAction.EventComment) annotation.reference.reference.nodeId
                else annotation.target?.takeIf { it.kind != PingTargetKind.LOCATION }?.let { "target/${it.kind}/${it.id}" }
            key?.let { add(Triple("annotations", it, node.id)) }
        }
    }

    private fun flushEdges() {
        val degrees = linkedMapOf<Pair<String, String>, Int>()
        fun changed(rows: org.jooq.Result<out org.jooq.Record>, amount: Int) {
            for (row in rows) {
                val key = row.get(E.KIND)!! to row.get(E.SOURCE_ID)!!
                degrees[key] = (degrees[key] ?: 0) + amount
            }
        }
        recordsWritten += edgeChanges.size
        for (chunk in edgeChanges.filterValues { !it }.keys.chunked(1000)) changed(sql.deleteFrom(E).where(E.PLAY_ID.eq(id))
            .and(DSL.row(E.KIND, E.SOURCE_ID, E.TARGET_ID).`in`(chunk.map { DSL.row(it.first, it.second, it.third) }))
            .returning(E.KIND, E.SOURCE_ID).fetch(), -1)
        for (chunk in edgeChanges.filterValues { it }.keys.chunked(1000)) {
            val insert = sql.insertInto(E, E.PLAY_ID, E.KIND, E.SOURCE_ID, E.TARGET_ID)
            for (key in chunk) insert.values(id, key.first, key.second, key.third)
            changed(insert.onConflictDoNothing().returning(E.KIND, E.SOURCE_ID).fetch(), 1)
        }
        // Each changed source's degree, read for all of them at once (a batch adds a child to each of its events)
        val counted = degrees.filterValues { it != 0 }.keys.groupBy({ it.first }, { it.second })
            .mapValues { (kind, sources) -> sources.chunked(1000).fold(emptyMap<String, JsonElement?>()) { all, chunk -> all + getAll("degree/$kind", chunk) } }
        for ((key, amount) in degrees) if (amount != 0) put("degree/${key.first}", key.second,
            JsonPrimitive((counted.getValue(key.first)[key.second]?.jsonPrimitive?.int ?: 0) + amount))
        edgeChanges.clear()
    }

    fun save(before: GameplayTree, next: GameplayTree, delta: GameplayTreeDelta, revision: Long) {
        val ids = (delta.upserts.filter { it.kind == "node" }.map { it.id } + delta.removals.filter { it.kind == "node" }.map { it.id }).toSet()
        val index = next.endpointIndex()
        for (key in ids) {
            val old = before.nodes[key]; val node = next.nodes[key]
            val oldEdges = old?.let(::edgeKeys).orEmpty(); val newEdges = node?.let(::edgeKeys).orEmpty()
            for (key in oldEdges - newEdges) edgeChanges[key] = false
            for (key in newEdges - oldEdges) edgeChanges[key] = true
        }
        // Reparenting changes every descendant's ancestry identity, even when its recorded
        // transition bytes are unchanged and therefore absent from the wire upserts.
        val positionKeys = (index.positions as? EndpointMap)?.changedKeys ?: ids
        // A batch's events mostly share one target set (the same object): it is encoded and stored once
        for (key in positionKeys) put(POSITION, key, index.positions[key]?.let(::stored))
        val state = requireNotNull(next.stateAt(next.currentNodeId))
        val commentKeys = (state.annotations.commentsById as? EndpointMap)?.changedKeys.orEmpty()
        val pingKeys = (state.annotations.pingsById as? EndpointMap)?.changedKeys.orEmpty()
        val ordinalKeys = (state.annotations.lastOrdinals as? EndpointMap)?.changedKeys.orEmpty()
        for (key in commentKeys) put("comment", key, state.annotations.commentsById[key]?.let { json.encodeToJsonElement(it) })
        for (group in ordinalKeys) put("ping-ordinal", group, state.annotations.lastOrdinals[group]?.let(::JsonPrimitive))
        for (key in pingKeys) {
            val ping = state.annotations.pingsById[key]
            val oldPing = before.stateAt(before.currentNodeId)!!.annotations.pingsById[key]
            if (ping != null || oldPing != null) {
                put("ping", key, ping?.let { json.encodeToJsonElement(it) })
                for (record in listOfNotNull(ping, oldPing).distinctBy { Triple(it.recording.groupId, it.reference, it.target) }) {
                    val group = record.recording.groupId
                    put("ping-ordinal", group, state.annotations.lastOrdinals[group]?.let(::JsonPrimitive))
                    put("active/$group", pingKey(record.reference, record.target), state.annotations.activePing(group, record.reference, record.target)?.let { json.encodeToJsonElement(it) })
                }
            }
        }
        for (record in delta.upserts) cache.write(if (record.kind == "node") "node/${record.id}" else "control/${record.kind}/${record.id}", record.payload)
        for (key in delta.removals) cache.write(if (key.kind == "node") "node/${key.id}" else "control/${key.kind}/${key.id}", null)
        flushEdges()
        flush()
        checkpoint(state, revision)
    }

    private fun checkpoint(state: PlayState, revision: Long) {
        val payload = json.encodeToString(state.copy(annotations = EventAnnotationState()))
        check(sql.update(GAME_PLAY).set(GAME_PLAY.PAYLOAD, payload).set(GAME_PLAY.CHECKPOINT_DIGEST, digest(payload, revision))
            .set(GAME_PLAY.CHECKPOINT_VERSION, 1).set(GAME_PLAY.CHECKPOINT_REVISION, revision).where(GAME_PLAY.ID.eq(id)).execute() == 1)
    }
    data class Checkpoint(val payload: String, val revision: Long)
    companion object {
        private const val POSITION = "position"
        /** Record kind of a stored target set, kept by checkpoint maintenance (positions name them). */
        const val TARGETS = "targets"
        private const val TARGETS_REF = "targetsRef"

        fun checkpoint(sql: DSLContext, id: UUID): Checkpoint {
            val row = sql.select(GAME_PLAY.CHECKPOINT_VERSION, GAME_PLAY.CHECKPOINT_REVISION, GAME_PLAY.CHECKPOINT_DIGEST, GAME_PLAY.PAYLOAD,
                GAME_PLAY.GAME_ID).from(GAME_PLAY).where(GAME_PLAY.ID.eq(id)).fetchSingle()
            intactCheckpoint(row.value5(), row.value1(), row.value2(), row.value3(), row.value4()!!)
            return Checkpoint(row.value4()!!, row.value2()!!)
        }

        /** Integrity check of a stored checkpoint. A game that fails it is contained (SYS-052) until the explicit
         * checkpoint-gameplay maintenance rebuilds it; its damaged state is never served. */
        fun intactCheckpoint(game: Any?, version: Int?, revision: Long?, digest: String?, payload: String) {
            StoredData.intact("game", game, "checkpoint version", version == 1)
            StoredData.intact("game", game, "checkpoint digest", revision != null && digest == digest(payload, revision))
        }

        /** The stored current node and branch pointers must match the checkpoint (SYS-052 when they don't). */
        fun intactPointers(state: PlayState, current: String?, branch: String?) = StoredData.intact("game", state.gameId,
            "tree pointers", current == state.currentNodeId && branch == state.currentBranchId)
        private fun digest(payload: String, revision: Long) = GameplaySync.digest("1/$revision/$payload".toByteArray(Charsets.UTF_8))
    }
}
