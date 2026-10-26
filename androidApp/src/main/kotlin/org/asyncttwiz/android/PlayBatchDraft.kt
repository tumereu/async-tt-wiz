package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import java.util.UUID

/** Short-lived input lowering only. Discarded before the single session request is enqueued.
 * The table supplies its retained tree and armed continuation. Standalone board-only adapters
 * use their supplied endpoint as a root; they cannot draft historical continuations. */
internal class PlayBatchDraft(val initial: PlayState, private val actor: String,
    val requestId: String = UUID.randomUUID().toString(),
    tree: GameplayTree = GameplayTree(initial.copy(root = PlayRoot(initial.currentNodeId, initial.currentBranchId))),
    private val continuation: Continuation? = null) {
    private var tree = tree.materializedCopy()
    var state = initial
        private set
    val actions = mutableListOf<PlayAction>()
    var overflow = false
        private set
    private val envelope = GameCommand(requestId, GameScope(initial.gameId, playId = initial.id),
        GameAction.PlayBatch(emptyList()), initial.id, "0", continuation = continuation)

    fun add(action: PlayAction): Boolean {
        if (actions.size >= 100) { overflow = true; return false }
        val member = PlayBatches.member(envelope, actions.size, action).let {
            if (actions.isEmpty()) it.copy(continuation = continuation) else it
        }
        val change = HistoryContinuation.change(tree, actor, member)
        if (change.reason != null) return false
        if (change.tree != null) {
            actions += action
            tree = change.tree!!
            state = requireNotNull(tree.preparedState(tree.currentNodeId))
        }
        return true
    }
}
