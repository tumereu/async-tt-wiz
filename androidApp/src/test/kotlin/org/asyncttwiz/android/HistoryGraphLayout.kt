package org.asyncttwiz.android

import org.asyncttwiz.protocol.*

/** Cold fixture adapter only. Production dialogs receive the index from history ingestion. */
object HistoryGraphLayout {
    @Suppress("UNUSED_PARAMETER")
    fun build(tree: GameplayTree, membership: Map<String, String> = emptyMap(),
        visibleNodeIds: Set<String> = tree.nodes.keys): HistoryGraph {
        val index = HistoryIndex.build(tree.initial.root.id, tree.nodes, tree.initial.board.containers, initial = tree.initial)
        val layout = if (membership.isEmpty()) index.layout else {
            var result = HistoryLayoutIndex.root(tree.initial)
            for (id in index.boundaries.keys) if (id != tree.initial.root.id) {
                val chain = ArrayDeque<String>(); var cursor = id
                while (cursor !in result.boundaries) { chain.addLast(cursor); cursor = tree.nodes.getValue(cursor).parentId }
                while (chain.isNotEmpty()) {
                    val next = chain.removeLast()
                    result = result.append(tree.nodes.getValue(next), membership[next] ?: index.boundaries.getValue(next).group!!.id)
                }
            }
            result
        }
        return HistoryGraph(tree, layout)
    }
}
