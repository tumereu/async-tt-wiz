package org.asyncttwiz.protocol

/**
 * Rules v36 (I-D19, I-D24): [GameAction.RevertFlow] takes an open Automatum flow back to where it began, as one server
 * process. The flow is open while the board holds the continuation (I-D8); it began with the first event of the batch
 * (or the single event) on whose board before it no flow was open (finalreview-sync #3). Every event on the live line from the newest back to that one is taken back in order,
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

    /**
     * [GameAction.ExitFlow] (finalreview-sync #6, #7; owner decision 2026-11-03: switching off always goes through): the
     * open flow is taken back as [change] does when it can be; when it can't (or no flow is open) nothing is taken back
     * and the switch event closes the flow where it is ([Automatum.closeFlow]). Then [GameAction.ExitFlow.then] is
     * recorded as the actor's own event. Refused only as an ordinary play of it would be.
     */
    fun exit(tree: GameplayTree, actor: String, command: GameCommand): GameplayTreeChange {
        val action = command.action as? GameAction.ExitFlow ?: return GameplayTreeChange(reason = "invalidInput")
        if (!GameWire.valid(command)) return GameplayTreeChange(reason = "invalidInput")
        if (command.scope.gameId != tree.initial.gameId || command.scope.playId != tree.initial.id)
            return GameplayTreeChange(reason = "inapplicable")
        if (actor !in tree.initial.board.players.map { it.accountId }) return GameplayTreeChange(reason = "accessDenied")
        val head = tree.stateAt(tree.currentNodeId) ?: return GameplayTreeChange(reason = "historyUnavailable")
        var base = tree
        val keys = mutableSetOf<GameplayTreeKey>()
        if (Automatum.flowOpen(head.board)) {
            change(tree, actor, command.copy(action = GameAction.RevertFlow)).tree
                ?.takeIf { tree.selectiveReferenceFailure(it) == null }
                ?.let { base = it; keys += it.editKeys }
        }
        val played = base.play(actor, command.copy(action = GameAction.Play(PlayAction.Mtg(action.then)), continuation = null,
            expectedSelection = null, invocationId = null))
        if (played.reason != null) return played
        val next = played.tree ?: return if (base === tree) GameplayTreeChange() else GameplayTreeChange(base.changedFrom(tree, keys))
        keys += next.editKeys
        return GameplayTreeChange(next.changedFrom(tree, keys), targetId = played.targetId)
    }

    fun change(tree: GameplayTree, actor: String, command: GameCommand): GameplayTreeChange {
        if (!GameWire.valid(command) || command.action != GameAction.RevertFlow) return GameplayTreeChange(reason = "invalidInput")
        if (command.scope.gameId != tree.initial.gameId || command.scope.playId != tree.initial.id)
            return GameplayTreeChange(reason = "inapplicable")
        if (actor !in tree.initial.board.players.map { it.accountId }) return GameplayTreeChange(reason = "accessDenied")
        val head = tree.stateAt(tree.currentNodeId) ?: return GameplayTreeChange(reason = "historyUnavailable")
        if (!Automatum.flowOpen(head.board)) return GameplayTreeChange(reason = "noFlow")
        // Newest first, taking each event back as it goes, to the first event of the batch (or the single event) on whose
        // board before it no flow was open (finalreview-sync #3: a batch that answers a flow closes and opens the
        // continuation again, so the flow began where it was first opened, not where its latest decision was).
        val removed = mutableListOf<GameplayMutation>()
        var state = head
        var cursor = tree.currentNodeId
        while (true) {
            if (removed.size >= MAX_EVENTS) return GameplayTreeChange(reason = "tooFarBack")
            val node = tree.nodes[cursor] ?: return GameplayTreeChange(reason = "historyUnavailable")
            state = tree.unapply(state, node) ?: return GameplayTreeChange(reason = "replayBlocked")
            removed += node
            val previous = tree.nodes[node.parentId]
            val sameBatch = node.invocationId != null && previous != null && previous.invocationId == node.invocationId &&
                previous.author == node.author
            if (!sameBatch && !Automatum.flowOpen(state.board)) break
            cursor = node.parentId
        }
        val children = tree.index().children
        if (removed.drop(1).any { children[it.id].orEmpty().size > 1 } || children[removed.first().id].orEmpty().isNotEmpty())
            return GameplayTreeChange(reason = "forkBoundary")
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
