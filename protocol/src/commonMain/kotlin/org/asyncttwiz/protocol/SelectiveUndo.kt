package org.asyncttwiz.protocol

/** Pure processing-time engine. The caller owns admission order, deduplication, lifecycle and atomic SQL.
 * Never call this again for a completed request: recover its original inbox result instead.
 */
object SelectiveUndo {
    fun change(tree: GameplayTree, actor: String, command: GameCommand,
        limits: SelectiveUndoLimits = SelectiveUndoLimits()): GameplayTreeChange {
        if (!GameWire.valid(command) || command.action !in listOf(GameAction.Undo, GameAction.Redo))
            return GameplayTreeChange(reason = "invalidInput")
        if (command.scope.gameId != tree.initial.gameId || command.scope.playId != tree.initial.id)
            return GameplayTreeChange(reason = "inapplicable")
        if (actor !in tree.initial.board.players.map { it.accountId }) return GameplayTreeChange(reason = "accessDenied")
        return boundedHistory(limits) { work ->
            tree.checkMetadata(work)
            if (command.action == GameAction.Undo) undo(tree, actor, limits, work) else redo(tree, actor, limits, work)
        } ?: GameplayTreeChange(reason = "workLimit")
    }

    private fun children(tree: GameplayTree): Map<String, List<GameplayMutation>> = tree.nodes.values.groupBy { it.parentId }

    /** Complete retained linear future, not just the active path. A fork fails before any edit. */
    private fun suffix(start: GameplayMutation?, children: Map<String, List<GameplayMutation>>,
        limit: Int): List<GameplayMutation>? {
        val result = mutableListOf<GameplayMutation>()
        val seen = mutableSetOf<String>()
        var node = start
        while (node != null) {
            if (result.size >= limit || !seen.add(node.id)) return null
            result.add(node)
            val next = children[node.id].orEmpty()
            if (next.size > 1) return null
            node = next.singleOrNull()
        }
        return result
    }

    private fun undo(tree: GameplayTree, actor: String, limits: SelectiveUndoLimits, work: HistoryWork): GameplayTreeChange {
        val path = tree.path(tree.currentNodeId, work) ?: return GameplayTreeChange(reason = "missingReference")
        val target = path.lastOrNull { it.author == actor } ?: return GameplayTreeChange(reason = "noCandidate")
        if (target.revealsInformation && !ownHandDisclosure(tree, target, actor))
            return GameplayTreeChange(reason = "revealingTarget", targetId = target.id)
        val children = children(tree)
        if (children[target.id].orEmpty().size > 1) return GameplayTreeChange(reason = "forkBoundary", targetId = target.id)
        val later = suffix(children[target.id]?.singleOrNull(), children, limits.suffixNodes)
            ?: return GameplayTreeChange(reason = "suffixUnavailable", targetId = target.id)
        var state = tree.materialize(target.parentId, work) ?: return GameplayTreeChange(reason = "historyUnavailable")
        if (!tree.referencesMatch(target, work) || tree.replay(state, target, work) == null)
            return GameplayTreeChange(reason = "targetTransitionBlocked", targetId = target.id)
        val successor = later.firstOrNull()
        val nodes = (tree.nodes - target.id).let { nodes ->
            if (successor == null) nodes else nodes + (successor.id to successor.copy(parentId = target.parentId))
        }
        val entry = GameplayRedoEntry(target, successor?.id,
            children[target.parentId].orEmpty().filter { it.id != target.id }.map { it.id }.toSet())
        val candidate = tree.copy(nodes = nodes,
            currentNodeId = if (tree.currentNodeId == target.id) target.parentId else tree.currentNodeId,
            redoByAccount = tree.redoByAccount + (actor to (tree.redoByAccount[actor].orEmpty() + entry)),
            removedParents = tree.removedParents + (target.id to target.parentId))
        for (node in later) {
            if (!candidate.referencesMatch(node, work)) return GameplayTreeChange(reason = "referenceBlocked", targetId = target.id)
            state = candidate.replay(state, nodes.getValue(node.id), work)
                ?: return GameplayTreeChange(reason = "replayBlocked", targetId = target.id)
        }
        return GameplayTreeChange(candidate, targetId = target.id)
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

    private fun redo(tree: GameplayTree, actor: String, limits: SelectiveUndoLimits, work: HistoryWork): GameplayTreeChange {
        val queue = tree.redoByAccount[actor].orEmpty()
        val entry = queue.lastOrNull() ?: return GameplayTreeChange(reason = "noCandidate")
        val target = entry.mutation
        val path = tree.path(tree.currentNodeId, work) ?: return GameplayTreeChange(reason = "missingReference")
        if (target.id in tree.nodes || (target.parentId != tree.initial.root.id && path.none { it.id == target.parentId }))
            return GameplayTreeChange(reason = "missingAnchor", targetId = target.id)
        val children = children(tree)
        val continuation = children[target.parentId].orEmpty().filter { it.id !in entry.otherChildIds }
        if (continuation.size > 1) return GameplayTreeChange(reason = "forkBoundary", targetId = target.id)
        val successor = continuation.singleOrNull()
        if (entry.successorId != null && successor?.id != entry.successorId)
            return GameplayTreeChange(reason = "missingAnchor", targetId = target.id)
        var state = tree.materialize(target.parentId, work) ?: return GameplayTreeChange(reason = "historyUnavailable")
        if (!tree.referencesMatch(target, work)) return GameplayTreeChange(reason = "referenceBlocked", targetId = target.id)
        // Exact target transition FIRST, even if there is no suffix. No fresh arithmetic/defaults/targets.
        state = tree.replay(state, target, work) ?: return GameplayTreeChange(reason = "targetTransitionBlocked", targetId = target.id)
        val later = suffix(successor, children, limits.suffixNodes)
            ?: return GameplayTreeChange(reason = "suffixUnavailable", targetId = target.id)
        var nodes = tree.nodes + (target.id to target)
        if (successor != null) nodes = nodes + (successor.id to successor.copy(parentId = target.id))
        val candidate = tree.copy(nodes = nodes,
            currentNodeId = if (tree.currentNodeId == target.parentId) target.id else tree.currentNodeId,
            redoByAccount = if (queue.size == 1) tree.redoByAccount - actor else tree.redoByAccount + (actor to queue.dropLast(1)),
            removedParents = tree.removedParents - target.id)
        for (node in later) {
            if (!candidate.referencesMatch(node, work)) return GameplayTreeChange(reason = "referenceBlocked", targetId = target.id)
            state = candidate.replay(state, nodes.getValue(node.id), work)
                ?: return GameplayTreeChange(reason = "replayBlocked", targetId = target.id)
        }
        return GameplayTreeChange(candidate, targetId = target.id)
    }
}
