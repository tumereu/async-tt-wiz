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
        return run {
            if (command.action == GameAction.Undo) {
                val path = tree.path(tree.currentNodeId) ?: return@run GameplayTreeChange(reason = "missingReference")
                val target = path.lastOrNull { it.author == actor }
                    ?: return@run GameplayTreeChange(reason = "noCandidate")
                val members = if (target.invocationId == null) listOf(target) else
                    path.takeWhile { it.id != target.id }.asReversed()
                        .takeWhile { it.author == actor && it.invocationId == target.invocationId && it.branchId == target.branchId }
                        .let { listOf(target) + it }
                var candidate = tree
                // Each constituent keeps the same replay/disclosure/fork checks. Failure publishes
                // none of the candidate edits, rather than undoing only the tail of a user action.
                repeat(members.size) {
                    val change = undo(candidate, actor)
                    if (change.reason != null) return@run change
                    candidate = change.tree ?: return@run change
                }
                GameplayTreeChange(candidate, targetId = target.id)
            } else {
                val queue = tree.redoByAccount[actor].orEmpty()
                val target = queue.lastOrNull()?.mutation
                    ?: return@run GameplayTreeChange(reason = "noCandidate")
                val members = if (target.invocationId == null) 1 else queue.asReversed()
                    .takeWhile { it.mutation.invocationId == target.invocationId && it.mutation.branchId == target.branchId }.size
                var candidate = tree
                repeat(members) {
                    val change = redo(candidate, actor)
                    if (change.reason != null) return@run change
                    candidate = change.tree ?: return@run change
                }
                GameplayTreeChange(candidate, targetId = target.id)
            }
        }
    }

    private fun children(tree: GameplayTree): Map<String, List<GameplayMutation>> = tree.nodes.values.groupBy { it.parentId }

    /** Complete retained linear future, not just the active path. A fork fails before any edit. */
    private fun suffix(start: GameplayMutation?, children: Map<String, List<GameplayMutation>>,
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
            node = next.singleOrNull()
        }
        return result
    }

    private fun undo(tree: GameplayTree, actor: String): GameplayTreeChange {
        val path = tree.path(tree.currentNodeId) ?: return GameplayTreeChange(reason = "missingReference")
        val target = path.lastOrNull { it.author == actor } ?: return GameplayTreeChange(reason = "noCandidate")
        if (target.revealsInformation && !ownHandDisclosure(tree, target, actor))
            return GameplayTreeChange(reason = "revealingTarget", targetId = target.id)
        val children = children(tree)
        if (children[target.id].orEmpty().size > 1) return GameplayTreeChange(reason = "forkBoundary", targetId = target.id)
        val later = suffix(children[target.id]?.singleOrNull(), children)
            ?: return GameplayTreeChange(reason = "suffixUnavailable", targetId = target.id)
        if (later.size > 50) return GameplayTreeChange(reason = "tooFarBack", targetId = target.id)
        var state = beforeSuffix(tree, later, target.id) ?: return GameplayTreeChange(reason = "historyUnavailable")
        if (!tree.referencesMatch(target))
            return GameplayTreeChange(reason = "targetTransitionBlocked", targetId = target.id)
        state = if (target.schemaVersion == 2) GameplayHistory.unapply(state, target)
            ?: return GameplayTreeChange(reason = "targetTransitionBlocked", targetId = target.id)
        else tree.stateAt(target.parentId)?.takeIf { tree.replay(it, target) != null }
            ?: return GameplayTreeChange(reason = "targetTransitionBlocked", targetId = target.id)
        val successor = later.firstOrNull()
        var nodes = (tree.nodes - target.id).let { nodes ->
            if (successor == null) nodes else nodes + (successor.id to successor.copy(parentId = target.parentId))
        }
        val entry = GameplayRedoEntry(target, successor?.id,
            children[target.parentId].orEmpty().filter { it.id != target.id }.map { it.id }.toSet())
        var candidate = tree.copy(nodes = nodes,
            currentNodeId = if (tree.currentNodeId == target.id) target.parentId else tree.currentNodeId,
            currentBranchId = if (tree.currentNodeId == target.id) state.currentBranchId else tree.currentBranchId,
            redoByAccount = tree.redoByAccount + (actor to (tree.redoByAccount[actor].orEmpty() + entry)),
            removedParents = tree.removedParents + (target.id to target.parentId))
        var current = state.takeIf { it.currentNodeId == candidate.currentNodeId }
        for (node in later) {
            if (!candidate.referencesMatch(node)) return GameplayTreeChange(reason = "referenceBlocked", targetId = target.id)
            val retained = if (node.schemaVersion == 2) GameplayHistory.recontextualize(state, nodes.getValue(node.id)) else nodes.getValue(node.id)
            state = candidate.replay(state, retained)
                ?: return GameplayTreeChange(reason = "replayBlocked", targetId = target.id)
            nodes = nodes + (node.id to retained)
            if (node.id == candidate.currentNodeId) current = state
        }
        candidate = candidate.copy(nodes = nodes).checkpoint(current ?: return GameplayTreeChange(reason = "historyUnavailable"))
        return GameplayTreeChange(candidate, targetId = target.id)
    }

    /** Starts at the known head. New-schema traversal never reconstructs the root. */
    private fun beforeSuffix(tree: GameplayTree, later: List<GameplayMutation>, anchor: String): PlayState? {
        var state = tree.stateAt(later.lastOrNull()?.id ?: anchor) ?: return null
        for (node in later.asReversed()) {
            state = if (node.schemaVersion == 2) GameplayHistory.unapply(state, node) ?: return null
            else tree.stateAt(node.parentId) ?: return null
        }
        return state
    }

    /** Playing/projecting an already-known own Hand card must be reversible. Keep its disclosure on the
     * recorded mutation (including in Redo); this exception does not make draws, inspections,
     * flips or moves that reveal an opponent's Hand to the actor removable. */
    private fun ownHandDisclosure(tree: GameplayTree, target: GameplayMutation, actor: String): Boolean {
        val effect = ((target.action as? PlayAction.Stack)?.action as? StackAction.AddEffect)
        if (effect != null) return target.transition.disclosures.isNotEmpty() && target.transition.disclosures.all { disclosure ->
            actor !in disclosure.viewers && tree.initial.board.containers.any {
                it.id == disclosure.containerId && it.zone == PlayZone.HAND && it.accountId == actor }
        }
        if (target.action !is PlayAction.MoveCard || target.transition.disclosures.any { actor in it.viewers }) return false
        val move = target.transition.movement ?: return false
        val containers = tree.initial.board.containers
        return containers.any { it.id == move.from.containerId && it.zone == PlayZone.HAND && it.accountId == actor } &&
            containers.any { it.id == move.to.containerId && it.zone in setOf(PlayZone.BATTLEFIELD, PlayZone.GRAVEYARD) }
    }

    private fun redo(tree: GameplayTree, actor: String): GameplayTreeChange {
        val queue = tree.redoByAccount[actor].orEmpty()
        val entry = queue.lastOrNull() ?: return GameplayTreeChange(reason = "noCandidate")
        val target = entry.mutation
        val path = tree.path(tree.currentNodeId) ?: return GameplayTreeChange(reason = "missingReference")
        if (target.id in tree.nodes || (target.parentId != tree.initial.root.id && path.none { it.id == target.parentId }))
            return GameplayTreeChange(reason = "missingAnchor", targetId = target.id)
        val children = children(tree)
        val continuation = children[target.parentId].orEmpty().filter { it.id !in entry.otherChildIds }
        if (continuation.size > 1) return GameplayTreeChange(reason = "forkBoundary", targetId = target.id)
        val successor = continuation.singleOrNull()
        if (entry.successorId != null && successor?.id != entry.successorId)
            return GameplayTreeChange(reason = "missingAnchor", targetId = target.id)
        val later = suffix(successor, children)
            ?: return GameplayTreeChange(reason = "suffixUnavailable", targetId = target.id)
        if (later.size > 50) return GameplayTreeChange(reason = "tooFarBack", targetId = target.id)
        var state = beforeSuffix(tree, later, target.parentId) ?: return GameplayTreeChange(reason = "historyUnavailable")
        if (!tree.referencesMatch(target)) return GameplayTreeChange(reason = "referenceBlocked", targetId = target.id)
        // Exact target transition FIRST, even if there is no suffix. No fresh arithmetic/defaults/targets.
        val restored = if (target.schemaVersion == 2) GameplayHistory.recontextualize(state, target) else target
        state = tree.replay(state, restored) ?: return GameplayTreeChange(reason = "targetTransitionBlocked", targetId = target.id)
        var nodes = tree.nodes + (target.id to restored)
        if (successor != null) nodes = nodes + (successor.id to successor.copy(parentId = target.id))
        var candidate = tree.copy(nodes = nodes,
            currentNodeId = if (tree.currentNodeId == target.parentId) target.id else tree.currentNodeId,
            currentBranchId = if (tree.currentNodeId == target.parentId) target.branchId else tree.currentBranchId,
            redoByAccount = if (queue.size == 1) tree.redoByAccount - actor else tree.redoByAccount + (actor to queue.dropLast(1)),
            removedParents = tree.removedParents - target.id)
        var current = state.takeIf { it.currentNodeId == candidate.currentNodeId }
        for (node in later) {
            if (!candidate.referencesMatch(node)) return GameplayTreeChange(reason = "referenceBlocked", targetId = target.id)
            val retained = if (node.schemaVersion == 2) GameplayHistory.recontextualize(state, nodes.getValue(node.id)) else nodes.getValue(node.id)
            state = candidate.replay(state, retained)
                ?: return GameplayTreeChange(reason = "replayBlocked", targetId = target.id)
            nodes = nodes + (node.id to retained)
            if (node.id == candidate.currentNodeId) current = state
        }
        candidate = candidate.copy(nodes = nodes).checkpoint(current ?: return GameplayTreeChange(reason = "historyUnavailable"))
        return GameplayTreeChange(candidate, targetId = target.id)
    }
}
