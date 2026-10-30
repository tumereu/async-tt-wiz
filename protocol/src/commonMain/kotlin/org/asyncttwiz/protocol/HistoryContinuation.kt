package org.asyncttwiz.protocol

import kotlinx.serialization.Serializable

/** Immutable lineage and original displaced selection. Explicit current heads
 * live in GameplayTree.branchHeads, never an inferred subtree. Original references can later be
 * removed by Undo; consumers must resolve them explicitly, not substitute a representative/group end.
 */
@Serializable data class GameplayBranch(val id: String, val forkNodeId: String, val firstNodeId: String,
    val createdByRequestId: String, val author: String, val displacedNodeId: String, val displacedBranchId: String,
    val displacedVersion: String? = null)

/** Processing-time controls on the existing tree. The single durable FIFO adapter must recover an
 * original inbox outcome BEFORE calling this function; it owns lifecycle, ordering and atomic commit.
 * Like ordinary play, cold reconstruction belongs on a worker, never the UI thread.
 */
object HistoryContinuation {
    fun change(tree: GameplayTree, actor: String, command: GameCommand): GameplayTreeChange {
        if (!GameWire.valid(command)) return GameplayTreeChange(reason = "invalidInput")
        if (command.scope.gameId != tree.initial.gameId || command.scope.playId != tree.initial.id)
            return GameplayTreeChange(reason = "inapplicable")
        if (actor !in tree.initial.board.players.map { it.accountId }) return GameplayTreeChange(reason = "accessDenied")
        // Owner rule 2026-10-30: only a deliberate branch changes the active line. An ordinary action made on a
        // line that a continuation has since replaced is refused as a whole (batches too), never moved over.
        if (command.expectedSelection != null && command.expectedSelection != tree.selectionKey)
            return GameplayTreeChange(reason = "branched")
        val change = when (command.action) {
            is GameAction.Play -> tree.play(actor, command)
            is GameAction.PlayBatch -> PlayBatches.change(tree, actor, command)
            GameAction.Undo, GameAction.Redo -> SelectiveUndo.change(tree, actor, command)
            else -> GameplayTreeChange(reason = "invalidInput")
        }
        val next = change.tree ?: return change
        if (command.action == GameAction.Undo || command.action == GameAction.Redo) {
            tree.selectiveReferenceFailure(next)?.let { return GameplayTreeChange(reason = it, targetId = change.targetId) }
        }
        if (command.action.isPlay) return change
        var heads = next.branchHeads
        val keys = next.editKeys.toMutableSet()
        // Only removed/restored events can change a branch's terminal boundary.
        val affectedBranches = keys.filter { it.kind == "node" }.mapNotNull { tree.nodes[it.id]?.branchId ?: next.nodes[it.id]?.branchId }.toSet()
        for (branch in affectedBranches) {
            val old = heads[branch] ?: continue
            val resolved = next.resolveCursor(old) ?: next.initial.root.id
            if (old != resolved) { heads = heads.putShared(branch, resolved); keys += GameplayTreeKey("branch-head", branch) }
        }
        if (command.action == GameAction.Redo) {
            // Undo of an actual branch head records its surviving parent. Reinsertion at that same
            // still-terminal boundary restores the head; it must not leave catalog/head behind the
            // current board or overwrite a newer opponent continuation.
            for (entry in tree.redoByAccount[actor].orEmpty().asReversed().takeWhile { it.mutation.id in next.nodes }) {
                if (entry.successorId == null && heads[entry.mutation.branchId] == entry.mutation.parentId)
                    { heads = heads.putShared(entry.mutation.branchId, entry.mutation.id)
                        keys += GameplayTreeKey("branch-head", entry.mutation.branchId) }
            }
        }
        if (heads === next.branchHeads) return change
        return change.copy(tree = next.preservingCheckpoint(next.copy(branchHeads = heads)).changedFrom(tree, keys))
    }
}
