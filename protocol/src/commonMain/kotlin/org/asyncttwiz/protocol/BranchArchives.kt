package org.asyncttwiz.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Exact retained context, including a version that changes when tree editing changes its board.
 * I24 owns branch identity/topology/selection; this is an adapter value, not another branch model.
 */
@Serializable data class BranchArchiveTarget(val branchId: String, val nodeId: String, val version: String)

/** Read from the SAME current GameplayTree and I24 retained metadata. Never infer a branch by subtree.
 * resolve includes internal/root boundaries; displacedReturn resolves retained displacement metadata,
 * not a guessed sibling. Missing/incomplete downloaded metadata must return null.
 * Commit a returned selection with the returned tree in one server transaction/revision.
 */
interface BranchArchiveCatalog {
    val selected: BranchArchiveTarget
    /** Identity of the latest actual shared switch, including non-archive I24 controls. Metadata-only
     * changes/same-current no-ops/retries preserve it. Commit a new switch's request ID atomically.
     */
    val selectionControlId: String?
    fun resolve(branchId: String, nodeId: String): BranchArchiveTarget?
    fun head(branchId: String): BranchArchiveTarget?
    fun displacedReturn(branchId: String): BranchArchiveTarget?
}

@Serializable sealed interface BranchArchiveIntent {
    @Serializable @SerialName("archive") data class Archive(val branchId: String) : BranchArchiveIntent
    @Serializable @SerialName("restore") data class Restore(val branchId: String) : BranchArchiveIntent
    @Serializable @SerialName("archiveAndReturn") data class ArchiveAndReturn(val branchId: String,
        val target: BranchArchiveTarget, val restoreTarget: Boolean = false,
        val expectedReturnHead: BranchArchiveTarget? = null) : BranchArchiveIntent
    /** Recovery is a captured non-node control, never selective gameplay Undo. */
    @Serializable @SerialName("recoverArchive") data class Recover(val controlId: String,
        val restoreSelection: Boolean) : BranchArchiveIntent
}

/** Payload carried by GameAction.BranchArchive on the existing FIFO/session transport, NOT a
 * second outgoing queue or endpoint. Its inbox outcome commits with tree + shared selection.
 */
@Serializable data class BranchArchiveRequest(val requestId: String, val scope: GameScope,
    val intent: BranchArchiveIntent)
@Serializable data class BranchArchiveRecovery(val beforeArchived: Map<String, Boolean>,
    val afterArchived: Map<String, Boolean>, val beforeSelection: BranchArchiveTarget,
    val afterSelection: BranchArchiveTarget, val afterSelectionControlId: String?)
@Serializable data class BranchArchiveRecord(val actor: String, val request: BranchArchiveRequest,
    val outcome: String, val reason: String? = null, val recovery: BranchArchiveRecovery? = null)
@Serializable data class BranchArchiveState(val archivedBranchIds: Set<String> = emptySet(),
    val lastControlByBranch: Map<String, String> = emptyMap(),
    val records: Map<String, BranchArchiveRecord> = emptyMap())
data class BranchArchiveChange(val tree: GameplayTree? = null, val selection: BranchArchiveTarget? = null,
    val outcome: String, val reason: String? = null, val controlId: String? = null, val recoveredResult: Boolean = false)
data class BranchArchivePreview(val branchId: String, val target: BranchArchiveTarget? = null,
    val board: PlayState? = null, val reason: String? = null, val returnHead: BranchArchiveTarget? = null) {
    val needsSelection: Boolean get() = reason != null
}

object BranchArchives {
    /** Personal filtering only. The fork boundary/shared prefix is deliberately excluded. Missing
     * data stays visible. Supply actual event-comment IDs, not incoming viewed-context references.
     * All records remain in the tree/download regardless of this result or archive metadata.
     */
    fun hiddenByDefault(tree: GameplayTree, forkNodeId: String, headNodeId: String,
        active: Boolean, eventCommentNodeIds: Set<String>): Boolean {
        if (active || headNodeId == forkNodeId) return false
        val path = tree.path(headNodeId) ?: return false
        val start = if (forkNodeId == tree.initial.root.id) 0 else {
            val index = path.indexOfFirst { it.id == forkNodeId }
            if (index < 0) return false
            index + 1
        }
        val suffix = path.drop(start)
        return suffix.isNotEmpty() && suffix.map { it.author }.distinct().size == 1 &&
            suffix.none { it.revealsInformation || it.id in eventCommentNodeIds }
    }

    fun previewReturn(tree: GameplayTree, catalog: BranchArchiveCatalog, branchId: String,
        chosen: BranchArchiveTarget? = null, restoreTarget: Boolean = false): BranchArchivePreview {
        fun select(reason: String) = BranchArchivePreview(branchId, reason = reason)
        if (catalog.head(branchId) == null) return select("missingBranch")
        val target = chosen ?: catalog.displacedReturn(branchId) ?: return select("selectReturn")
        if (target.branchId == branchId) return select("selectOtherBranch")
        if (catalog.resolve(target.branchId, target.nodeId) != target) return select("changedReturn")
        // An automatic default is a retained HEAD, never a nearest surviving node or sibling.
        if (chosen == null && catalog.head(target.branchId) != target) return select("changedReturn")
        if (target.branchId in tree.branchArchives.archivedBranchIds && !restoreTarget) return select("restoreReturn")
        val board = tree.stateAt(target.nodeId) ?: return select("missingReturn")
        val head = catalog.head(target.branchId) ?: return select("missingReturn")
        return BranchArchivePreview(branchId, target, board, returnHead = head)
    }

    /** Shared pure processor, also usable for provisional prediction. No gameplay nodes are edited.
     * records preserve original outcomes on retry, including no-op/rejection: never replay an old
     * selection/reset over newer play. Server auth/lifecycle/FIFO/inbox persistence remain I25b.
     */
    fun change(tree: GameplayTree, catalog: BranchArchiveCatalog, actor: String,
        request: BranchArchiveRequest): BranchArchiveChange {
        fun reject(reason: String) = BranchArchiveChange(outcome = "rejected", reason = reason)
        if (!valid(request)) return reject("invalidInput")
        if (request.scope.gameId != tree.initial.gameId || request.scope.playId != tree.initial.id)
            return reject("inapplicable")
        if (actor !in tree.initial.board.players.map { it.accountId }) return reject("accessDenied")
        val archives = tree.branchArchives
        archives.records[request.requestId]?.let {
            if (it.request != request || it.actor != actor) return reject("requestConflict")
            return BranchArchiveChange(outcome = it.outcome, reason = it.reason,
                controlId = request.requestId, recoveredResult = true)
        }
        fun finish(outcome: String, reason: String? = null, changed: GameplayTree = tree,
            selection: BranchArchiveTarget? = null, recovery: BranchArchiveRecovery? = null): BranchArchiveChange {
            val state = changed.branchArchives
            val record = BranchArchiveRecord(actor, request, outcome, reason, recovery)
            return BranchArchiveChange(changed.copy(branchArchives = state.copy(
                records = state.records + (request.requestId to record))), selection, outcome, reason, request.requestId)
        }
        fun failed(reason: String) = finish("rejected", reason)
        if (catalog.selected.nodeId != tree.currentNodeId ||
            catalog.resolve(catalog.selected.branchId, catalog.selected.nodeId) != catalog.selected)
            return failed("selectionUnavailable")
        val before = catalog.selected
        var selection = before
        val desired = mutableMapOf<String, Boolean>()
        when (val intent = request.intent) {
            is BranchArchiveIntent.Archive -> {
                if (catalog.head(intent.branchId) == null) return failed("missingBranch")
                if (intent.branchId == before.branchId) return failed("returnRequired")
                desired[intent.branchId] = true
            }
            is BranchArchiveIntent.Restore -> {
                if (catalog.head(intent.branchId) == null) return failed("missingBranch")
                desired[intent.branchId] = false
            }
            is BranchArchiveIntent.ArchiveAndReturn -> {
                if (intent.expectedReturnHead != null && catalog.head(intent.target.branchId) != intent.expectedReturnHead)
                    return failed("changedReturn")
                val preview = previewReturn(tree, catalog, intent.branchId, intent.target, intent.restoreTarget)
                if (preview.needsSelection) return failed(preview.reason!!)
                desired[intent.branchId] = true
                if (intent.restoreTarget) desired[intent.target.branchId] = false
                selection = intent.target
            }
            is BranchArchiveIntent.Recover -> {
                val record = archives.records[intent.controlId] ?: return failed("missingControl")
                val recovery = record.recovery ?: return failed("noRecovery")
                for ((id, archived) in recovery.afterArchived) {
                    if (catalog.head(id) == null || archives.lastControlByBranch[id] != intent.controlId ||
                        (id in archives.archivedBranchIds) != archived) return failed("changedArchive")
                }
                desired.putAll(recovery.beforeArchived)
                if (intent.restoreSelection) {
                    // Do not commandeer a newer selection, even if its board happens to look equal.
                    if (before != recovery.afterSelection || catalog.selectionControlId != recovery.afterSelectionControlId)
                        return failed("changedSelection")
                    selection = recovery.beforeSelection
                    if (catalog.resolve(selection.branchId, selection.nodeId) != selection ||
                        tree.stateAt(selection.nodeId) == null) return failed("changedReturn")
                }
            }
        }
        val afterArchived = archives.archivedBranchIds.toMutableSet()
        for ((id, archived) in desired) if (archived) afterArchived.add(id) else afterArchived.remove(id)
        // Covers the root branch as well: never leave the selected branch archived/no active branch.
        if (selection.branchId in afterArchived) return failed("returnRequired")
        val changedIds = desired.keys.filter { (it in archives.archivedBranchIds) != (it in afterArchived) }
        val switched = selection.branchId != before.branchId || selection.nodeId != before.nodeId
        if (changedIds.isEmpty() && !switched) return finish("noOp")
        val recovery = BranchArchiveRecovery(changedIds.associateWith { it in archives.archivedBranchIds },
            changedIds.associateWith { it in afterArchived }, before, selection,
            if (switched) request.requestId else catalog.selectionControlId)
        val updated = archives.copy(archivedBranchIds = afterArchived,
            lastControlByBranch = archives.lastControlByBranch + changedIds.associateWith { request.requestId })
        // Branch identity is part of shared selection, even at a common node with equal board values.
        val next = tree.copy(currentNodeId = selection.nodeId,
            currentBranchId = selection.branchId,
            redoByAccount = if (switched) emptyMap() else tree.redoByAccount, branchArchives = updated)
        return finish("accepted", changed = next, selection = selection, recovery = recovery)
    }

    fun valid(request: BranchArchiveRequest): Boolean {
        if (!DeckValidation.uuid(request.requestId) || !DeckValidation.uuid(request.scope.gameId) ||
            request.scope.playId?.let(DeckValidation::uuid) != true ||
            request.scope.preparationId != null || request.scope.invitationId != null) return false
        return when (val intent = request.intent) {
            is BranchArchiveIntent.Archive -> DeckValidation.uuid(intent.branchId)
            is BranchArchiveIntent.Restore -> DeckValidation.uuid(intent.branchId)
            is BranchArchiveIntent.Recover -> DeckValidation.uuid(intent.controlId)
            is BranchArchiveIntent.ArchiveAndReturn -> DeckValidation.uuid(intent.branchId) &&
                DeckValidation.uuid(intent.target.branchId) && DeckValidation.uuid(intent.target.nodeId) &&
                intent.target.version.isNotEmpty() && intent.target.version.length <= 200 &&
                (intent.expectedReturnHead == null || (intent.expectedReturnHead.branchId == intent.target.branchId &&
                    DeckValidation.uuid(intent.expectedReturnHead.nodeId) &&
                    intent.expectedReturnHead.version.isNotEmpty() && intent.expectedReturnHead.version.length <= 200))
        }
    }
}
