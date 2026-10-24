package org.asyncttwiz.protocol

import kotlinx.serialization.Serializable

/** Immutable lineage and original displaced selection for return/recovery. Explicit current heads
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
        val change = when (val action = command.action) {
            is GameAction.Play -> tree.play(actor, command)
            GameAction.Undo, GameAction.Redo -> SelectiveUndo.change(tree, actor, command)
            is GameAction.BranchArchive -> BranchArchives.change(tree, tree.archiveCatalog(), actor,
                BranchArchiveRequest(command.requestId, command.scope, action.intent)).let {
                    // Original outcomes live in the existing inbox, not a second mutation journal.
                    if (it.outcome != "accepted") GameplayTreeChange(reason = it.reason)
                    else GameplayTreeChange(it.tree, targetId = it.selection?.nodeId)
                }
            is GameAction.MakeCurrent -> {
                if (tree.stateAt(action.nodeId) == null) GameplayTreeChange(reason = "missingReference")
                else tree.activate(action.nodeId)
            }
            else -> GameplayTreeChange(reason = "invalidInput")
        }
        val next = change.tree ?: return change
        if (command.action == GameAction.Undo || command.action == GameAction.Redo) {
            tree.selectiveReferenceFailure(next)?.let { return GameplayTreeChange(reason = it, targetId = change.targetId) }
        }
        val heads = next.branchHeads.mapValues { (_, node) -> next.resolveCursor(node) ?: next.initial.root.id }.toMutableMap()
        if (command.action == GameAction.Redo) {
            // Undo of an actual branch head records its surviving parent. Reinsertion at that same
            // still-terminal boundary restores the head; it must not leave catalog/head behind the
            // current board or overwrite a newer opponent continuation.
            for (entry in tree.redoByAccount[actor].orEmpty().asReversed().takeWhile { it.mutation.id in next.nodes }) {
                if (entry.successorId == null && heads[entry.mutation.branchId] == entry.mutation.parentId)
                    heads[entry.mutation.branchId] = entry.mutation.id
            }
        }
        val switched = command.action is GameAction.MakeCurrent ||
            (command.action is GameAction.BranchArchive &&
                (next.currentBranchId != tree.currentBranchId || next.currentNodeId != tree.currentNodeId))
        if (heads == next.branchHeads && !switched) return change
        return change.copy(tree = next.preservingCheckpoint(next.copy(branchHeads = heads,
            selectionControlId = if (switched) command.requestId else next.selectionControlId)))
    }
}
