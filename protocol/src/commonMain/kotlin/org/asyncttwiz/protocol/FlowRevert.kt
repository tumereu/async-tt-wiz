package org.asyncttwiz.protocol

/**
 * Rules v36 (I-D19, I-D24): [GameAction.RevertFlow] takes an open Automatum flow back to where it began, as one server
 * process. The flow is open while the board holds the continuation (I-D8); it began with the first event of the batch
 * that opened the continuation. Every event on the live line from the newest back to that one is taken back in order,
 * whoever made it (unlike Undo, which takes back only the actor's own events), with each event's exact recorded
 * inverse (SYS-027). The events leave the history like undone ones but go to nobody's redo; redo entries anchored in
 * them are dropped. Either player may send it.
 *
 * Refused when no flow is open ("noFlow"), when the flow began more than [MAX_EVENTS] events ago ("tooFarBack"), when
 * one of the events has another branch after it ("forkBoundary"), or when an event can't be taken back exactly
 * ("replayBlocked"). Then switching Automatum off still closes the continuation (I-D24).
 */
object FlowRevert {
    /** The most events one revert takes back: a flow of several automated batches (I-D14). */
    const val MAX_EVENTS = 5000

    /** The event that opened the continuation (it named none before and one after). */
    fun opens(node: GameplayMutation): Boolean = ((node.action as? PlayAction.Mtg)?.action as? MtgAction.SetRecord)
        ?.let { it.id == Automatum.CONTINUATION_ID && it.before == null && it.after != null } == true

    fun change(tree: GameplayTree, actor: String, command: GameCommand): GameplayTreeChange {
        if (!GameWire.valid(command) || command.action != GameAction.RevertFlow) return GameplayTreeChange(reason = "invalidInput")
        if (command.scope.gameId != tree.initial.gameId || command.scope.playId != tree.initial.id)
            return GameplayTreeChange(reason = "inapplicable")
        if (actor !in tree.initial.board.players.map { it.accountId }) return GameplayTreeChange(reason = "accessDenied")
        val head = tree.stateAt(tree.currentNodeId) ?: return GameplayTreeChange(reason = "historyUnavailable")
        if (!Automatum.flowOpen(head.board)) return GameplayTreeChange(reason = "noFlow")
        // Newest first: back to the opening event, then to the first member of its batch.
        val removed = mutableListOf<GameplayMutation>()
        var cursor = tree.currentNodeId
        while (true) {
            if (removed.size >= MAX_EVENTS) return GameplayTreeChange(reason = "tooFarBack")
            val node = tree.nodes[cursor] ?: return GameplayTreeChange(reason = "historyUnavailable")
            removed += node
            if (opens(node)) break
            cursor = node.parentId
        }
        val invocation = removed.last().invocationId
        if (invocation != null) while (true) {
            val previous = tree.nodes[removed.last().parentId] ?: break
            if (previous.invocationId != invocation || previous.author != removed.last().author) break
            if (removed.size >= MAX_EVENTS) return GameplayTreeChange(reason = "tooFarBack")
            removed += previous
        }
        val children = tree.index().children
        if (removed.drop(1).any { children[it.id].orEmpty().size > 1 } || children[removed.first().id].orEmpty().isNotEmpty())
            return GameplayTreeChange(reason = "forkBoundary")
        var state = head
        for (node in removed) state = tree.unapply(state, node) ?: return GameplayTreeChange(reason = "replayBlocked")
        val ids = removed.map { it.id }.toSet()
        var nodes = tree.nodes
        var removedParents = tree.removedParents
        for (node in removed) { nodes = nodes.removeShared(node.id); removedParents = removedParents.putShared(node.id, node.parentId) }
        val keys = mutableSetOf(GameplayTreeKey("current", "current"), GameplayTreeKey("selected-branch", "current"))
        removed.forEach { keys += GameplayTreeKey("node", it.id); keys += GameplayTreeKey("removed-parent", it.id) }
        var redo = tree.redoByAccount
        for ((account, queue) in tree.redoByAccount) {
            val kept = queue.filterNot { it.mutation.parentId in ids || it.successorId in ids }
            if (kept.size == queue.size) continue
            redo = if (kept.isEmpty()) redo.removeShared(account) else redo.putShared(account, kept)
            keys += GameplayTreeKey("redo", account)
        }
        val start = removed.last()
        val candidate = tree.copy(nodes = nodes, currentNodeId = start.parentId, currentBranchId = state.currentBranchId,
            redoByAccount = redo, removedParents = removedParents)
        candidate.changedFrom(tree, keys)
        return GameplayTreeChange(candidate.checkpoint(state), targetId = start.id)
    }
}
