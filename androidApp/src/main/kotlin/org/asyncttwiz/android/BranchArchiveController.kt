package org.asyncttwiz.android

import org.asyncttwiz.protocol.*

/** The session supplies one coherent accepted/predicted snapshot. Never reconstruct another tree. */
data class BranchArchiveView(val scope: GameScope, val tree: GameplayTree, val catalog: BranchArchiveCatalog)
data class CapturedBranchArchiveAction(val scope: GameScope, val intent: BranchArchiveIntent)
data class BranchArchiveConfirmation(val action: CapturedBranchArchiveAction?, val preview: BranchArchivePreview)
data class BranchReturnChoice(val target: BranchArchiveTarget, val label: String, val archived: Boolean)

/** Mount through the existing session/FIFO path in I25b. submit allocates ONE original request identity,
 * predicts with BranchArchives and retains that intent through recovery. No local authoritative edit,
 * independent transport, fake readiness or default no-op callback. Rotation/menu dismissal is not recall.
 */
class BranchArchiveController(private val view: () -> BranchArchiveView?, private val connected: () -> Boolean,
    private val submit: (CapturedBranchArchiveAction) -> Boolean) {
    fun capture(intent: BranchArchiveIntent): CapturedBranchArchiveAction? {
        val current = view() ?: return null
        if (!playScope(current)) return null
        return CapturedBranchArchiveAction(current.scope, intent)
    }

    /** Prepare on the session's history worker, not a latency-sensitive Compose callback: cold retained
     * reconstruction has no selective-Undo ceiling. The dialog keeps these exact board/target bytes.
     */
    fun prepareReturn(branchId: String, chosen: BranchArchiveTarget? = null,
        restoreTarget: Boolean = false): BranchArchiveConfirmation? {
        val current = view() ?: return null
        if (!playScope(current)) return null
        val preview = BranchArchives.previewReturn(current.tree, current.catalog, branchId, chosen, restoreTarget)
        val action = preview.target?.let { CapturedBranchArchiveAction(current.scope,
            BranchArchiveIntent.ArchiveAndReturn(branchId, it, restoreTarget, preview.returnHead)) }
        return BranchArchiveConfirmation(action, preview)
    }

    fun request(captured: CapturedBranchArchiveAction): Boolean {
        if (!connected()) return false
        val current = view() ?: return false
        if (!playScope(current) || current.scope != captured.scope) return false
        // Do not replace a captured branch/return/control identity with the latest menu selection.
        return submit(captured)
    }

    private fun playScope(current: BranchArchiveView) = current.scope.playId != null &&
        current.scope.preparationId == null && current.scope.invitationId == null &&
        current.scope.gameId == current.tree.initial.gameId && current.scope.playId == current.tree.initial.id
}
