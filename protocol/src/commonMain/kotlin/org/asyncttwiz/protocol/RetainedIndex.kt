package org.asyncttwiz.protocol

import kotlinx.collections.immutable.toPersistentMap
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf

/** Preserve insertion order and share all untouched trie pages. Wire properties remain ordinary maps. */
internal fun <K, V> Map<K, V>.putShared(key: K, value: V): Map<K, V> = if (this is IndexedMap<K, V>) putting(key, value) else toPersistentMap().put(key, value)
internal fun <K, V> Map<K, V>.removeShared(key: K): Map<K, V> = if (this is IndexedMap<K, V>) removing(key) else toPersistentMap().remove(key)
internal fun <T> Set<T>.addShared(value: T): Set<T> = if (this is IndexedSet<T>) adding(value) else toPersistentSet().add(value)
internal fun <T> Set<T>.removeShared(value: T): Set<T> = if (this is IndexedSet<T>) removing(value) else toPersistentSet().remove(value)

/** Disposable topology metadata. Built once at cold import, then edited only along changed edges.
 * No boards or caller-provided identities are stored here. */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@kotlinx.serialization.Serializable data class RetainedPosition(val depth: Int, val identity: String,
    /** Persistent ID-only witness, shared across events that do not create/remove targets.
     * Null only on pre-concern persisted endpoints, never a reason for root replay. */
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val targets: Set<PingTarget>? = null)

internal fun GameplayMutation.annotationIndexKey(): String? = transition.annotation?.let { annotation ->
    if (transition.concerns == null || action is PlayAction.EventComment) annotation.reference.reference.nodeId
    else annotation.target?.takeIf { it.kind != PingTargetKind.LOCATION }?.let { "target/${it.kind}/${it.id}" }
}
class RetainedIndex(
    var children: Map<String, Set<String>> = emptyMap(),
    var readers: Map<String, Set<String>> = emptyMap(),
    var positions: Map<String, RetainedPosition> = emptyMap(),
    val incremental: Boolean = true,
    var annotationReaders: Map<String, Set<String>> = emptyMap(),
    /** Shared ID-only vectors, built at cold import and edited with topology deltas. Timeline
     * windows/forward steps can index any depth without walking from the head or indexing on open. */
    val paths: Map<String, PersistentList<String>> = emptyMap(),
) {
    private fun edge(node: GameplayMutation, add: Boolean) {
        fun update(map: Map<String, Set<String>>, key: String): Map<String, Set<String>> {
            val members = map[key].orEmpty().let { if (add) it.addShared(node.id) else it.removeShared(node.id) }
            return if (members.isEmpty()) map.removeShared(key) else map.putShared(key, members)
        }
        children = update(children, node.parentId)
        for (reference in node.references) readers = update(readers, reference.nodeId)
        node.annotationIndexKey()?.let { annotationReaders = update(annotationReaders, it) }
    }

    fun descendants(ids: Collection<String>): Set<String> {
        val result = ids.toMutableSet()
        val pending = ArrayDeque(ids.toList())
        while (pending.isNotEmpty()) for (id in children[pending.removeFirst()].orEmpty())
            if (result.add(id)) pending.add(id)
        return result
    }

    fun edited(old: Map<String, GameplayMutation>, next: Map<String, GameplayMutation>, ids: Set<String>): RetainedIndex {
        var nextPaths = paths.toPersistentMap()
        val result = RetainedIndex(children, readers, positions, incremental && ids.all { id -> next[id]?.let {
            it.schemaVersion == 2 && it.transition.delta != null && it.bookkeeping != null } != false }, annotationReaders)
        for (id in ids) { old[id]?.let { result.edge(it, false) }; next[id]?.let { result.edge(it, true) } }
        val affected = result.descendants(ids)
        for (id in affected) { result.positions = result.positions.removeShared(id); nextPaths = nextPaths.remove(id) }
        // Parent-first, even when a delta's records arrive in UUID rather than ancestry order.
        val pending = ArrayDeque(affected.filter { next[it]?.parentId in result.positions })
        while (pending.isNotEmpty()) {
            val id = pending.removeFirst()
            val node = next[id] ?: continue
            val parent = result.positions[node.parentId] ?: continue
            result.positions = result.positions.putShared(id, RetainedPosition(parent.depth + 1,
                CommandIds.uuidV5(parent.identity, "retained/$id"), targets(parent.targets, node)))
            nextPaths[node.parentId]?.let { nextPaths = nextPaths.put(id, it.add(id)) }
            result.children[id].orEmpty().filterTo(pending) { it in affected }
        }
        return RetainedIndex(result.children, result.readers, result.positions, result.incremental,
            result.annotationReaders, nextPaths)
    }

    companion object {
        private fun targets(parent: Set<PingTarget>?, node: GameplayMutation): Set<PingTarget>? {
            var result = parent ?: return null
            fun change(kind: PingTargetKind, id: String, present: Boolean) {
                val target = PingTarget(kind, id)
                result = if (present) result.addShared(target) else result.removeShared(target)
            }
            for (token in node.transition.tokens) {
                val id = token.to?.card?.id ?: token.from!!.card.id
                change(PingTargetKind.CARD, id, token.to != null)
            }
            node.transition.combatStack?.let { effects ->
                effects.area?.let { area ->
                    area.from?.let { change(PingTargetKind.AREA, it, false) }
                    area.to?.let { change(PingTargetKind.AREA, it, true) }
                }
                effects.occurrences.forEach { change(PingTargetKind.OCCURRENCE, it.id, it.to != null) }
                effects.effects.forEach { change(PingTargetKind.OCCURRENCE, it.id, it.to != null) }
            }
            return result
        }

        fun build(tree: GameplayTree): RetainedIndex {
            var paths = mapOf(tree.initial.root.id to persistentListOf(tree.initial.root.id)).toPersistentMap()
            val result = RetainedIndex(positions = mapOf(tree.initial.root.id to RetainedPosition(0, tree.initial.root.id,
                RetainedAnnotationContext.from(tree.initial, tree.initial.root.id).targets.toPersistentSet())),
                incremental = tree.nodes.values.all { it.schemaVersion == 2 && it.transition.delta != null && it.bookkeeping != null })
            for (node in tree.nodes.values) result.edge(node, true)
            val pending = ArrayDeque(result.children[tree.initial.root.id].orEmpty().toList())
            while (pending.isNotEmpty()) {
                val id = pending.removeFirst()
                if (id in result.positions) continue
                val node = tree.nodes[id] ?: continue
                val parent = result.positions[node.parentId] ?: continue
                result.positions = result.positions.putShared(id, RetainedPosition(parent.depth + 1,
                    CommandIds.uuidV5(parent.identity, "retained/$id"), targets(parent.targets, node)))
                paths = paths.put(id, paths.getValue(node.parentId).add(id))
                result.children[id].orEmpty().forEach(pending::add)
            }
            return RetainedIndex(result.children, result.readers, result.positions, result.incremental,
                result.annotationReaders, paths)
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
