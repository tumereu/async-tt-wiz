package org.asyncttwiz.protocol

import kotlinx.collections.immutable.toPersistentMap
import kotlinx.collections.immutable.toPersistentSet

/** Preserve insertion order and share all untouched trie pages. Wire properties remain ordinary maps. */
internal fun <K, V> Map<K, V>.putShared(key: K, value: V): Map<K, V> = toPersistentMap().put(key, value)
internal fun <K, V> Map<K, V>.removeShared(key: K): Map<K, V> = toPersistentMap().remove(key)
internal fun <T> Set<T>.addShared(value: T): Set<T> = toPersistentSet().add(value)
internal fun <T> Set<T>.removeShared(value: T): Set<T> = toPersistentSet().remove(value)

/** Disposable topology metadata. Built once at cold import, then edited only along changed edges.
 * No boards or caller-provided identities are stored here. */
internal data class RetainedPosition(val depth: Int, val identity: String)
internal class RetainedIndex(
    var children: Map<String, Set<String>> = emptyMap(),
    var readers: Map<String, Set<String>> = emptyMap(),
    var positions: Map<String, RetainedPosition> = emptyMap(),
    val incremental: Boolean = true,
    var annotationReaders: Map<String, Set<String>> = emptyMap(),
) {
    private fun edge(node: GameplayMutation, add: Boolean) {
        fun update(map: Map<String, Set<String>>, key: String): Map<String, Set<String>> {
            val members = map[key].orEmpty().let { if (add) it.addShared(node.id) else it.removeShared(node.id) }
            return if (members.isEmpty()) map.removeShared(key) else map.putShared(key, members)
        }
        children = update(children, node.parentId)
        for (reference in node.references) readers = update(readers, reference.nodeId)
        node.transition.annotation?.reference?.reference?.nodeId?.let { annotationReaders = update(annotationReaders, it) }
    }

    fun descendants(ids: Collection<String>): Set<String> {
        val result = ids.toMutableSet()
        val pending = ArrayDeque(ids.toList())
        while (pending.isNotEmpty()) for (id in children[pending.removeFirst()].orEmpty())
            if (result.add(id)) pending.add(id)
        return result
    }

    fun edited(old: Map<String, GameplayMutation>, next: Map<String, GameplayMutation>, ids: Set<String>): RetainedIndex {
        val result = RetainedIndex(children, readers, positions, incremental && ids.all { id -> next[id]?.let {
            it.schemaVersion == 2 && it.transition.delta != null && it.bookkeeping != null } != false }, annotationReaders)
        for (id in ids) { old[id]?.let { result.edge(it, false) }; next[id]?.let { result.edge(it, true) } }
        val affected = result.descendants(ids)
        for (id in affected) result.positions = result.positions.removeShared(id)
        // Parent-first, even when a delta's records arrive in UUID rather than ancestry order.
        val pending = ArrayDeque(affected.filter { next[it]?.parentId in result.positions })
        while (pending.isNotEmpty()) {
            val id = pending.removeFirst()
            val node = next[id] ?: continue
            val parent = result.positions[node.parentId] ?: continue
            result.positions = result.positions.putShared(id, RetainedPosition(parent.depth + 1,
                CommandIds.uuidV5(parent.identity, "retained/$id")))
            result.children[id].orEmpty().filterTo(pending) { it in affected }
        }
        return result
    }

    companion object {
        fun build(tree: GameplayTree): RetainedIndex {
            val result = RetainedIndex(positions = mapOf(tree.initial.root.id to RetainedPosition(0, tree.initial.root.id)),
                incremental = tree.nodes.values.all { it.schemaVersion == 2 && it.transition.delta != null && it.bookkeeping != null })
            for (node in tree.nodes.values) result.edge(node, true)
            val pending = ArrayDeque(result.children[tree.initial.root.id].orEmpty().toList())
            while (pending.isNotEmpty()) {
                val id = pending.removeFirst()
                if (id in result.positions) continue
                val node = tree.nodes[id] ?: continue
                val parent = result.positions[node.parentId] ?: continue
                result.positions = result.positions.putShared(id, RetainedPosition(parent.depth + 1,
                    CommandIds.uuidV5(parent.identity, "retained/$id")))
                result.children[id].orEmpty().forEach(pending::add)
            }
            return result
        }
    }
}

/** Per-execution-domain diagnostic counters; never serialized or used as authority. */
class GameplayWork {
    var replayedNodes: Int = 0; internal set
    var unappliedNodes: Int = 0; internal set
    var ancestrySteps: Int = 0; internal set
    var rootMaterializations: Int = 0; internal set
    var fullHistoryScans: Int = 0; internal set
    var indexedNodes: Int = 0; internal set
    fun reset() { replayedNodes = 0; unappliedNodes = 0; ancestrySteps = 0; rootMaterializations = 0; fullHistoryScans = 0; indexedNodes = 0 }
}
