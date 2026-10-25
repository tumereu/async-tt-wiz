package org.asyncttwiz.protocol

/** Pure processing-time engine. The caller owns admission order, deduplication, lifecycle and atomic SQL.
 * Never call this again for a completed request: recover its original inbox result instead.
 */
object SelectiveUndo {
    fun change(tree: GameplayTree, actor: String, command: GameCommand): GameplayTreeChange {
        if (!GameWire.valid(command) || command.action !in listOf(GameAction.Undo, GameAction.Redo))
            return GameplayTreeChange(reason = "invalidInput")
        if (command.scope.gameId != tree.initial.gameId || command.scope.playId != tree.initial.id)
            return GameplayTreeChange(reason = "inapplicable")
        if (actor !in tree.initial.board.players.map { it.accountId }) return GameplayTreeChange(reason = "accessDenied")
        val budget = Budget()
        return run {
            if (command.action == GameAction.Undo) {
                val target = target(tree, actor) ?: return@run GameplayTreeChange(reason = candidateFailure(tree))
                val members = mutableListOf(target)
                if (target.invocationId != null) {
                    var previous = tree.nodes[target.parentId]
                    while (previous != null && previous.author == actor && previous.invocationId == target.invocationId && previous.branchId == target.branchId) {
                        if (members.size >= 51) return@run GameplayTreeChange(reason = "tooFarBack", targetId = target.id)
                        members += previous; previous = tree.nodes[previous.parentId]
                    }
                }
                var candidate = tree
                val keys = mutableSetOf<GameplayTreeKey>()
                // Each constituent keeps the same tuple/fork checks and shares the work budget. Failure publishes
                // none of the candidate edits, rather than undoing only the tail of a user action.
                repeat(members.size) {
                    val change = undo(candidate, actor, budget)
                    if (change.reason != null) return@run change
                    candidate = change.tree ?: return@run change
                    keys += candidate.editKeys
                }
                GameplayTreeChange(candidate.changedFrom(tree, keys), targetId = target.id)
            } else {
                val queue = tree.redoByAccount[actor].orEmpty()
                val target = queue.lastOrNull()?.mutation
                    ?: return@run GameplayTreeChange(reason = "noCandidate")
                val members = if (target.invocationId == null) 1 else queue.asReversed()
                    .take(52).takeWhile { it.mutation.invocationId == target.invocationId && it.mutation.branchId == target.branchId }.size
                if (members > 51) return@run GameplayTreeChange(reason = "tooFarBack", targetId = target.id)
                var candidate = tree
                val keys = mutableSetOf<GameplayTreeKey>()
                repeat(members) {
                    val change = redo(candidate, actor, budget)
                    if (change.reason != null) return@run change
                    candidate = change.tree ?: return@run change
                    keys += candidate.editKeys
                }
                GameplayTreeChange(candidate.changedFrom(tree, keys), targetId = target.id)
            }
        }
    }

    private fun target(tree: GameplayTree, actor: String): GameplayMutation? {
        var cursor = tree.currentNodeId
        repeat(51) {
            val node = tree.nodes[cursor] ?: return null
            tree.work.ancestrySteps++
            if (node.author == actor) return node
            cursor = node.parentId
        }
        return null
    }
    private fun candidateFailure(tree: GameplayTree) = if ((tree.index().positions[tree.currentNodeId]?.depth ?: 0) > 50) "tooFarBack" else "noCandidate"

    /** Complete retained linear future, not just the active path. A fork fails before any edit. */
    private fun suffix(tree: GameplayTree, start: GameplayMutation?, children: Map<String, Set<String>>,
        limit: Int = 51): List<GameplayMutation>? {
        val result = mutableListOf<GameplayMutation>()
        val seen = mutableSetOf<String>()
        var node = start
        while (node != null) {
            if (result.size >= limit) return result
            if (!seen.add(node.id)) return null
            result.add(node)
            val next = children[node.id].orEmpty()
            if (next.size > 1) return null
            node = next.singleOrNull()?.let(tree.nodes::get)
        }
        return result
    }

    private class Budget(var remaining: Int = 51) {
        fun spend(events: Int): Boolean { remaining -= events; return remaining >= 0 }
    }

    private fun undo(tree: GameplayTree, actor: String, budget: Budget): GameplayTreeChange {
        val target = target(tree, actor) ?: return GameplayTreeChange(reason = candidateFailure(tree))
        val children = tree.index().children
        if (children[target.id].orEmpty().size > 1) return GameplayTreeChange(reason = "forkBoundary", targetId = target.id)
        val later = suffix(tree, children[target.id]?.singleOrNull()?.let(tree.nodes::get), children)
            ?: return GameplayTreeChange(reason = "suffixUnavailable", targetId = target.id)
        if (later.size > 50 || !budget.spend(later.size + 1)) return GameplayTreeChange(reason = "tooFarBack", targetId = target.id)
        var state = beforeSuffix(tree, later, target.id) ?: return GameplayTreeChange(reason = "historyUnavailable")
        if (!tree.referencesMatch(target))
            return GameplayTreeChange(reason = "targetTransitionBlocked", targetId = target.id)
        state = tree.unapply(state, target)
            ?: return GameplayTreeChange(reason = "targetTransitionBlocked", targetId = target.id)
        val successor = later.firstOrNull()
        var nodes = tree.nodes.removeShared(target.id).let { nodes ->
            if (successor == null) nodes else nodes.putShared(successor.id, successor.copy(parentId = target.parentId))
        }
        val entry = GameplayRedoEntry(target, successor?.id,
            emptySet(), otherChildCount = children[target.parentId].orEmpty().size - 1)
        var candidate = tree.copy(nodes = nodes,
            currentNodeId = if (tree.currentNodeId == target.id) target.parentId else tree.currentNodeId,
            currentBranchId = if (tree.currentNodeId == target.id) state.currentBranchId else tree.currentBranchId,
            redoByAccount = tree.redoByAccount.putShared(actor, tree.redoByAccount[actor].orEmpty().appendShared(entry)),
            removedParents = tree.removedParents.putShared(target.id, target.parentId))
        candidate.changedFrom(tree, editKeys(target, later, actor))
        candidate.nearby(state)
        var current = state.takeIf { it.currentNodeId == candidate.currentNodeId }
        for (node in later) {
            if (!candidate.referencesMatch(node)) return GameplayTreeChange(reason = "referenceBlocked", targetId = target.id)
            val retained = GameplayHistory.recontextualizeRetained(state, nodes.getValue(node.id))
                ?: return GameplayTreeChange(reason = "replayBlocked", targetId = target.id)
            state = candidate.replay(state, retained)
                ?: return GameplayTreeChange(reason = "replayBlocked", targetId = target.id)
            nodes = nodes.putShared(node.id, retained)
            if (retained.transition != node.transition) candidate = candidate.copy(nodes = nodes)
                .changedFrom(candidate, setOf(GameplayTreeKey("node", node.id)))
            candidate.nearby(state)
            if (node.id == candidate.currentNodeId) current = state
        }
        candidate = candidate.copy(nodes = nodes).checkpoint(current ?: return GameplayTreeChange(reason = "historyUnavailable"))
            .changedFrom(tree, editKeys(target, later, actor))
        return GameplayTreeChange(candidate, targetId = target.id)
    }

    /** Starts at the known head. New-schema traversal never reconstructs the root. */
    private fun beforeSuffix(tree: GameplayTree, later: List<GameplayMutation>, anchor: String): PlayState? {
        var state = tree.stateAt(later.lastOrNull()?.id ?: anchor) ?: return null
        for (node in later.asReversed()) {
            state = tree.unapply(state, node) ?: return null
        }
        return state
    }

    private fun redo(tree: GameplayTree, actor: String, budget: Budget): GameplayTreeChange {
        val queue = tree.redoByAccount[actor].orEmpty()
        val entry = queue.lastOrNull() ?: return GameplayTreeChange(reason = "noCandidate")
        val target = entry.mutation
        if (target.id in tree.nodes || (target.parentId != tree.initial.root.id && target.parentId !in tree.nodes))
            return GameplayTreeChange(reason = "missingAnchor", targetId = target.id)
        val children = tree.index().children
        // Original siblings cannot be edited while this queue survives: activating/forking clears
        // queues. Find the new continuation backwards from the active head, not by scanning every
        // inactive child at a popular historical fork.
        var cursor = tree.currentNodeId
        var activeChild: GameplayMutation? = null
        var remaining = 51
        while (cursor != target.parentId && remaining-- > 0) {
            val node = tree.nodes[cursor] ?: return GameplayTreeChange(reason = "missingAnchor", targetId = target.id)
            tree.work.ancestrySteps++
            activeChild = node; cursor = node.parentId
        }
        if (cursor != target.parentId) return GameplayTreeChange(reason = "tooFarBack", targetId = target.id)
        val childIds = children[target.parentId].orEmpty()
        // Siblings are frozen while this queue survives: any explicit continuation/fork clears
        // all queues. A count is sufficient, and avoids copying/encoding thousands of siblings
        // when undoing one terminal child of a popular historical fork.
        val continuationCount = childIds.size - (entry.otherChildCount ?: entry.otherChildIds.size)
        if (continuationCount > 1) return GameplayTreeChange(reason = "forkBoundary", targetId = target.id)
        val successor = activeChild?.takeIf { entry.otherChildCount != null || it.id !in entry.otherChildIds } ?: entry.successorId
            ?.takeIf { it in childIds && it !in entry.otherChildIds }?.let(tree.nodes::get)
        if (continuationCount != (if (successor == null) 0 else 1)) return GameplayTreeChange(reason = "missingAnchor", targetId = target.id)
        if (entry.successorId != null && successor?.id != entry.successorId)
            return GameplayTreeChange(reason = "missingAnchor", targetId = target.id)
        val later = suffix(tree, successor, children)
            ?: return GameplayTreeChange(reason = "suffixUnavailable", targetId = target.id)
        if (later.size > 50 || !budget.spend(later.size + 1)) return GameplayTreeChange(reason = "tooFarBack", targetId = target.id)
        var state = beforeSuffix(tree, later, target.parentId) ?: return GameplayTreeChange(reason = "historyUnavailable")
        if (!tree.referencesMatch(target)) return GameplayTreeChange(reason = "referenceBlocked", targetId = target.id)
        // Exact target transition FIRST, even if there is no suffix. No fresh arithmetic/defaults/targets.
        val restored = GameplayHistory.recontextualize(state, target)
        state = tree.replay(state, restored) ?: return GameplayTreeChange(reason = "targetTransitionBlocked", targetId = target.id)
        var nodes = tree.nodes.putShared(target.id, restored)
        if (successor != null) nodes = nodes.putShared(successor.id, successor.copy(parentId = target.id))
        var candidate = tree.copy(nodes = nodes,
            currentNodeId = if (tree.currentNodeId == target.parentId) target.id else tree.currentNodeId,
            currentBranchId = if (tree.currentNodeId == target.parentId) target.branchId else tree.currentBranchId,
            redoByAccount = if (queue.size == 1) tree.redoByAccount.removeShared(actor) else tree.redoByAccount.putShared(actor, queue.dropLastShared()),
            removedParents = tree.removedParents.removeShared(target.id))
        candidate.changedFrom(tree, editKeys(target, later, actor))
        candidate.nearby(state)
        var current = state.takeIf { it.currentNodeId == candidate.currentNodeId }
        for (node in later) {
            if (!candidate.referencesMatch(node)) return GameplayTreeChange(reason = "referenceBlocked", targetId = target.id)
            val retained = GameplayHistory.recontextualizeRetained(state, nodes.getValue(node.id))
                ?: return GameplayTreeChange(reason = "replayBlocked", targetId = target.id)
            state = candidate.replay(state, retained)
                ?: return GameplayTreeChange(reason = "replayBlocked", targetId = target.id)
            nodes = nodes.putShared(node.id, retained)
            if (retained.transition != node.transition) candidate = candidate.copy(nodes = nodes)
                .changedFrom(candidate, setOf(GameplayTreeKey("node", node.id)))
            candidate.nearby(state)
            if (node.id == candidate.currentNodeId) current = state
        }
        candidate = candidate.copy(nodes = nodes).checkpoint(current ?: return GameplayTreeChange(reason = "historyUnavailable"))
            .changedFrom(tree, editKeys(target, later, actor))
        return GameplayTreeChange(candidate, targetId = target.id)
    }

    private fun editKeys(target: GameplayMutation, later: List<GameplayMutation>, actor: String): Set<GameplayTreeKey> =
        (listOf(target) + later).map { GameplayTreeKey("node", it.id) }.toSet() + setOf(
            GameplayTreeKey("current", "current"), GameplayTreeKey("selected-branch", "current"),
            GameplayTreeKey("redo", actor), GameplayTreeKey("removed-parent", target.id))
}
