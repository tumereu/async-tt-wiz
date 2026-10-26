package org.asyncttwiz.android

import kotlinx.collections.immutable.toPersistentMap
import kotlinx.serialization.json.*
import org.asyncttwiz.protocol.*
import org.asyncttwiz.storage.*

/** Local materialized entities, not history records. A deterministic Merkle treap binds every
 * key/value (and absence) to the revision-bound endpoint. An edit hashes/writes only O(log n)
 * index nodes / the changed entities; internal index nodes need not be persisted. */
internal class TreeAnnotationProjection private constructor(private val root: Node?) {
    private class Node(val record: AcceptedRecord, val left: Node? = null, val right: Node? = null,
        val priority: String = TreeSync.digest((record.key.kind + "/" + record.key.id).toByteArray()),
        val entry: String = TreeSync.digest(DeckJson.canonicalElement(buildJsonObject {
            put("kind", record.key.kind); put("id", record.key.id); put("version", record.payload.version)
            put("value", GameWire.json.parseToJsonElement(record.payload.json))
        }).toByteArray())) {
        val key = record.key.kind + "/" + record.key.id
        val digest: String = TreeSync.digest("${left?.digest ?: EMPTY}|$entry|${right?.digest ?: EMPTY}".toByteArray())
        val count: Int = 1 + (left?.count ?: 0) + (right?.count ?: 0)
        fun children(left: Node? = this.left, right: Node? = this.right) = Node(record, left, right, priority, entry)
    }

    val identity: JsonObject get() = buildJsonObject {
        put("digest", root?.digest ?: EMPTY); put("count", root?.count ?: 0)
    }

    fun records(): Sequence<AcceptedRecord> = sequence {
        suspend fun SequenceScope<AcceptedRecord>.visit(node: Node?) {
            if (node == null) return
            visit(node.left); yield(node.record); visit(node.right)
        }
        visit(root)
    }

    fun state(): EventAnnotationState {
        val comments = mutableMapOf<String, RecordedEventComment>()
        val pings = mutableMapOf<String, RecordedPingChange>()
        val ordinals = mutableMapOf<String, Long>()
        for (record in records()) {
            val value = GameWire.json.parseToJsonElement(record.payload.json)
            when (record.key.kind.removePrefix(PREFIX)) {
                "comment" -> comments[record.key.id] = GameWire.json.decodeFromJsonElement<RecordedEventComment>(value)
                    .also { require(it.recording.nodeId == record.key.id) }
                "ping" -> pings[record.key.id] = GameWire.json.decodeFromJsonElement<RecordedPingChange>(value)
                    .also { require(it.recording.nodeId == record.key.id) }
                "ping-ordinal" -> ordinals[record.key.id] = value.jsonPrimitive.long
                else -> error("Unsupported annotation projection entity")
            }
        }
        return EventAnnotationState(comments.toPersistentMap(), pings.toPersistentMap(), ordinals.toPersistentMap())
    }

    data class Edit(val projection: TreeAnnotationProjection, val upserts: List<AcceptedRecord>, val removals: List<RecordKey>)

    fun update(state: EventAnnotationState, keys: Set<GameplayTreeKey>): Edit {
        var next = root
        val upserts = mutableListOf<AcceptedRecord>()
        val removals = mutableListOf<RecordKey>()
        for (key in keys) {
            val value = value(state, key)
            val storedKey = RecordKey(PREFIX + key.kind, key.id)
            if (value == null) {
                removals += storedKey
                next = remove(next, storedKey.kind + "/" + storedKey.id)
            } else {
                val record = AcceptedRecord(storedKey, StoredPayload(1, value.toString()))
                upserts += record
                next = put(next, Node(record))
            }
        }
        return Edit(TreeAnnotationProjection(next), upserts, removals)
    }

    companion object {
        private const val PREFIX = "tree-cache-annotation-"
        private val EMPTY = TreeSync.digest("annotation-projection-v1".toByteArray())
        fun owns(record: AcceptedRecord) = record.key.kind.startsWith(PREFIX)
        fun read(records: Sequence<AcceptedRecord>): TreeAnnotationProjection {
            var root: Node? = null
            val seen = mutableSetOf<RecordKey>()
            for (record in records) {
                require(record.payload.version == 1L && owns(record) && seen.add(record.key))
                root = put(root, Node(record))
            }
            return TreeAnnotationProjection(root)
        }
        fun from(state: EventAnnotationState): TreeAnnotationProjection {
            val keys = state.commentsById.keys.map { GameplayTreeKey("comment", it) } +
                state.pingsById.keys.map { GameplayTreeKey("ping", it) } +
                state.lastOrdinals.keys.map { GameplayTreeKey("ping-ordinal", it) }
            return TreeAnnotationProjection(null).update(state, keys.toSet()).projection
        }
        private fun value(state: EventAnnotationState, key: GameplayTreeKey): JsonElement? = when (key.kind) {
            "comment" -> state.commentsById[key.id]?.let { GameWire.json.encodeToJsonElement(it) }
            "ping" -> state.pingsById[key.id]?.let { GameWire.json.encodeToJsonElement(it) }
            "ping-ordinal" -> state.lastOrdinals[key.id]?.let(::JsonPrimitive)
            else -> error("Unsupported annotation projection entity")
        }
        private fun higher(a: Node, b: Node) = a.priority < b.priority || (a.priority == b.priority && a.key < b.key)
        private fun put(root: Node?, node: Node): Node {
            if (root == null) return node
            if (node.key == root.key) return node.children(root.left, root.right)
            if (node.key < root.key) {
                val left = put(root.left, node)
                return if (higher(left, root)) left.children(right = root.children(left = left.right))
                    else root.children(left = left)
            }
            val right = put(root.right, node)
            return if (higher(right, root)) right.children(left = root.children(right = right.left))
                else root.children(right = right)
        }
        private fun merge(left: Node?, right: Node?): Node? = when {
            left == null -> right
            right == null -> left
            higher(left, right) -> left.children(right = merge(left.right, right))
            else -> right.children(left = merge(left, right.left))
        }
        private fun remove(root: Node?, key: String): Node? = when {
            root == null -> null
            key == root.key -> merge(root.left, root.right)
            key < root.key -> root.children(left = remove(root.left, key))
            else -> root.children(right = remove(root.right, key))
        }
    }
}
