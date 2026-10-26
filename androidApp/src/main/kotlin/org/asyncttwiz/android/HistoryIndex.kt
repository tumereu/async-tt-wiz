package org.asyncttwiz.android

import kotlinx.collections.immutable.*
import org.asyncttwiz.protocol.*

/** Immutable, shared topology pages. A path is an index of IDs, never a list of replayed boards. */
internal data class HistoryBoundary(val path: PersistentList<String>, val group: GameplayGroup?,
    val groupEnds: PersistentList<String>)

internal class HistoryIndex(val boundaries: PersistentMap<String, HistoryBoundary>,
    val children: PersistentMap<String, PersistentSet<String>>, val visited: Int, val affected: Set<String>,
    val layout: HistoryLayoutIndex) {
    companion object {
        fun build(root: String, nodes: Map<String, GameplayMutation>, containers: List<PlayContainer>,
            previous: HistoryIndex? = null, oldNodes: Map<String, GameplayMutation> = emptyMap(),
            changed: Set<String> = nodes.keys, initial: PlayState? = null): HistoryIndex {
            var children = previous?.children ?: persistentMapOf()
            for (id in changed) {
                oldNodes[id]?.parentId?.let { parent -> children = children.put(parent, children[parent].orEmpty().toPersistentSet().remove(id)) }
                nodes[id]?.parentId?.let { parent -> children = children.put(parent, children[parent].orEmpty().toPersistentSet().add(id)) }
            }
            var boundaries = previous?.boundaries ?: persistentMapOf(root to
                HistoryBoundary(persistentListOf(root), null, persistentListOf(root)))
            val affected = mutableSetOf<String>()
            val pending = ArrayDeque<String>(); changed.forEach(pending::addLast)
            while (pending.isNotEmpty()) {
                val id = pending.removeFirst()
                if (!affected.add(id)) continue
                previous?.children?.get(id)?.forEach(pending::addLast)
                children[id]?.forEach(pending::addLast)
            }
            affected.forEach { boundaries = boundaries.remove(it) }
            var layout = previous?.layout?.remove(affected, oldNodes) ?: initial?.let(HistoryLayoutIndex::root)
                ?: HistoryLayoutIndex.root(root, nodes.values.firstOrNull()?.branchId ?: root)
            var visited = 0
            fun insert(id: String) {
                if (id in boundaries || id !in nodes) return
                val chain = ArrayDeque<String>(); val seen = mutableSetOf<String>(); var cursor = id
                while (cursor !in boundaries) {
                    require(seen.add(cursor)) { "Cyclic history" }
                    chain.addLast(cursor); cursor = checkNotNull(nodes[cursor]) { "Unavailable retained ancestry" }.parentId
                }
                while (chain.isNotEmpty()) {
                    val next = chain.removeLast(); val node = nodes.getValue(next)
                    val parent = boundaries.getValue(node.parentId)
                    val group = GameplayHistory.append(parent.group, node, containers)
                    val ends = if (parent.group?.id == group.id) parent.groupEnds.removeAt(parent.groupEnds.lastIndex) else parent.groupEnds
                    boundaries = boundaries.put(next, HistoryBoundary(parent.path.add(next), group, ends.add(next)))
                    layout = layout.append(node, group.id)
                    visited++
                }
            }
            affected.forEach(::insert)
            return HistoryIndex(boundaries, children, visited, affected, layout)
        }
    }
}

/** Compose effect keys must never invoke a retained tree's data-class equality. */
internal class IdentityKey(private val value: Any?) {
    override fun equals(other: Any?) = other is IdentityKey && value === other.value
    override fun hashCode() = System.identityHashCode(value)
}
