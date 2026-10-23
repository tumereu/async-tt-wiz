package org.asyncttwiz.protocol

import kotlinx.serialization.encodeToString
import kotlin.test.*

/** Test-only I24 adapter. Product branch/displacement ownership intentionally remains in I24. */
private data class ArchiveCatalog(override val selected: BranchArchiveTarget,
    val heads: Map<String, BranchArchiveTarget>, val displaced: BranchArchiveTarget? = null,
    val internal: List<BranchArchiveTarget> = emptyList(),
    override val selectionControlId: String? = null) : BranchArchiveCatalog {
    override fun head(branchId: String) = heads[branchId]
    override fun resolve(branchId: String, nodeId: String) = (heads.values + internal).firstOrNull {
        it.branchId == branchId && it.nodeId == nodeId
    }
    override fun displacedReturn(branchId: String) = displaced
}

class BranchArchivesTest {
    private val f = UndoFixture
    private val scope = GameScope(f.id(1), playId = f.id(2))
    private val main = f.id(6)
    private val reaction = f.id(50)
    private val child = f.id(51)
    private val prefix = f.tap(GameplayTree(f.root()))
    private val left = f.play(prefix, f.b, PlayAction.AdjustLife(f.b, "1"), 101)
    private val right = f.tap(prefix, card = 12, n = 102)
    private val descendant = f.play(right, f.a, PlayAction.AdjustLife(f.a, "1"), 103)
    private val tree = right.copy(nodes = left.nodes + descendant.nodes)
    private val returnTarget = BranchArchiveTarget(main, left.currentNodeId, "1")
    private val selected = BranchArchiveTarget(reaction, right.currentNodeId, "1")
    private val childTarget = BranchArchiveTarget(child, descendant.currentNodeId, "1")
    private val catalog = ArchiveCatalog(selected, mapOf(main to returnTarget, reaction to selected,
        child to childTarget), returnTarget)
    private fun request(intent: BranchArchiveIntent, n: Int = 700) = BranchArchiveRequest(f.id(n), scope, intent)
    private fun change(input: GameplayTree, intent: BranchArchiveIntent, n: Int = 700,
        directory: ArchiveCatalog = catalog) = BranchArchives.change(input, directory, f.a, request(intent, n))
    private fun queued(input: GameplayTree): GameplayTree {
        val removed = assertNotNull(f.undo(f.tap(GameplayTree(f.root()), n = 400)).tree)
        val entry = removed.redoByAccount.getValue(f.a)
        return input.copy(redoByAccount = mapOf(f.a to entry, f.b to entry))
    }
    private fun accepted(result: BranchArchiveChange) = assertNotNull(result.tree).also {
        assertEquals("accepted", result.outcome, result.reason)
    }

    @Test fun hidingClassifiesOnlySuffixAndRetainsAllData() {
        assertTrue(BranchArchives.hiddenByDefault(tree, prefix.currentNodeId, left.currentNodeId, false, emptySet()))
        // Prefix exposure/comment/other author must not keep the ordinary one-author suffix visible.
        val node = tree.nodes.getValue(prefix.currentNodeId)
        val exposedPrefix = tree.copy(nodes = tree.nodes + (node.id to node.copy(revealsInformation = true)))
        assertTrue(BranchArchives.hiddenByDefault(exposedPrefix, node.id, left.currentNodeId, false, setOf(node.id)))
        assertFalse(BranchArchives.hiddenByDefault(tree, prefix.currentNodeId, left.currentNodeId, true, emptySet()))
        assertFalse(BranchArchives.hiddenByDefault(tree, prefix.currentNodeId, left.currentNodeId, false, setOf(left.currentNodeId)))
        val exposed = tree.nodes.getValue(left.currentNodeId).copy(revealsInformation = true)
        val tail = f.play(left.copy(nodes = left.nodes + (exposed.id to exposed)), f.b,
            PlayAction.AdjustLife(f.b, "1"), 104)
        assertFalse(BranchArchives.hiddenByDefault(tail, prefix.currentNodeId, tail.currentNodeId, false, emptySet()))
        val mixed = f.play(left, f.a, PlayAction.AdjustLife(f.a, "1"), 105)
        assertFalse(BranchArchives.hiddenByDefault(mixed, prefix.currentNodeId, mixed.currentNodeId, false, emptySet()))
        assertFalse(BranchArchives.hiddenByDefault(tree, f.id(999), left.currentNodeId, false, emptySet()))
        assertFalse(BranchArchives.hiddenByDefault(tree, prefix.currentNodeId, f.id(999), false, emptySet()))
        assertNotNull(tree.stateAt(left.currentNodeId)) // personal filtering is not access/activation
        assertEquals(right.currentNodeId, tree.currentNodeId)
        assertEquals(left.nodes.keys + descendant.nodes.keys, tree.nodes.keys)
    }

    @Test fun exactBranchArchiveAndReturnPreservesDescendantsPrefixesAndBoardAndClearsBothQueues() {
        val input = queued(tree)
        val preview = BranchArchives.previewReturn(input, catalog, reaction)
        assertFalse(preview.needsSelection)
        assertEquals(returnTarget, preview.target)
        assertEquals(left.stateAt(left.currentNodeId), preview.board)
        val result = change(input, BranchArchiveIntent.ArchiveAndReturn(reaction, preview.target!!))
        val next = accepted(result)
        assertEquals(returnTarget, result.selection)
        assertEquals(setOf(reaction), next.branchArchives.archivedBranchIds)
        assertFalse(child in next.branchArchives.archivedBranchIds)
        assertEquals(input.nodes, next.nodes)
        assertEquals(input.initial, next.initial)
        assertEquals(left.currentNodeId, next.currentNodeId)
        assertEquals(preview.board, next.stateAt(next.currentNodeId))
        assertNotNull(next.stateAt(childTarget.nodeId))
        assertTrue(next.redoByAccount.isEmpty())
    }

    @Test fun changedMissingAndArchivedDefaultRequireExplicitSelectionOrRestoreNeverSubstitution() {
        val newer = returnTarget.copy(version = "2")
        val changed = catalog.copy(heads = catalog.heads + (main to newer))
        assertEquals("changedReturn", BranchArchives.previewReturn(tree, changed, reaction).reason)
        assertEquals("changedReturn", change(tree, BranchArchiveIntent.ArchiveAndReturn(reaction, returnTarget),
            directory = changed).reason)
        val missing = catalog.copy(heads = catalog.heads - main)
        assertTrue(BranchArchives.previewReturn(tree, missing, reaction).needsSelection)
        assertNull(BranchArchives.previewReturn(tree, missing, reaction).target)
        assertEquals("selectReturn", BranchArchives.previewReturn(tree, catalog.copy(displaced = null), reaction).reason)
        val absent = tree.copy(nodes = tree.nodes - returnTarget.nodeId)
        assertEquals("missingReturn", BranchArchives.previewReturn(absent, catalog, reaction).reason)
        val archived = tree.copy(branchArchives = BranchArchiveState(setOf(main)))
        assertEquals("restoreReturn", BranchArchives.previewReturn(archived, catalog, reaction).reason)
        assertEquals("restoreReturn", change(archived, BranchArchiveIntent.ArchiveAndReturn(reaction, returnTarget)).reason)
        val next = accepted(change(archived, BranchArchiveIntent.ArchiveAndReturn(reaction, returnTarget, true)))
        assertEquals(setOf(reaction), next.branchArchives.archivedBranchIds)
        val recovered = accepted(change(next, BranchArchiveIntent.Recover(f.id(700), true), 701,
            catalog.copy(selected = returnTarget, selectionControlId = f.id(700))))
        assertEquals(setOf(main), recovered.branchArchives.archivedBranchIds)
        assertEquals(right.currentNodeId, recovered.currentNodeId)
        // Explicitly selecting a different valid context is allowed, never an automatic guess.
        assertEquals(childTarget, BranchArchives.previewReturn(tree, catalog, reaction, childTarget).target)
    }

    @Test fun metadataOnlyArchiveRestoreNoOpAndReviewNeverClearNewerStacks() {
        val input = queued(tree)
        val archived = accepted(change(input, BranchArchiveIntent.Archive(main)))
        assertEquals(input.redoByAccount, archived.redoByAccount)
        assertEquals(input.currentNodeId, archived.currentNodeId)
        val noop = change(archived, BranchArchiveIntent.Archive(main), 701)
        assertEquals("noOp", noop.outcome)
        assertEquals(input.redoByAccount, noop.tree!!.redoByAccount)
        val restored = accepted(change(noop.tree, BranchArchiveIntent.Restore(main), 702))
        assertTrue(restored.branchArchives.archivedBranchIds.isEmpty())
        assertEquals(input.redoByAccount, restored.redoByAccount)
        assertEquals("noOp", change(restored, BranchArchiveIntent.Restore(main), 703).outcome)
        assertNotNull(archived.stateAt(returnTarget.nodeId))
        assertEquals(input.redoByAccount, archived.redoByAccount)
    }

    @Test fun retriesRecoverOriginalControlWithoutReactivationOrResetEvenAfterSerialization() {
        val action = BranchArchiveIntent.ArchiveAndReturn(reaction, returnTarget)
        val switched = accepted(change(queued(tree), action))
        val later = f.play(switched, f.a, PlayAction.AdjustLife(f.a, "1"), 500)
        val undone = assertNotNull(f.undo(later).tree)
        assertTrue(undone.redoByAccount.isNotEmpty())
        val decoded = DeckJson.format.decodeFromString<GameplayTree>(DeckJson.format.encodeToString(undone))
        val retry = change(decoded, action, directory = catalog.copy(selected = returnTarget))
        assertTrue(retry.recoveredResult)
        assertEquals("accepted", retry.outcome)
        assertNull(retry.tree)
        assertNull(retry.selection)
        assertEquals(undone.redoByAccount, decoded.redoByAccount)
        assertEquals("requestConflict", change(decoded, BranchArchiveIntent.Restore(reaction)).reason)
        // A recovered original no-op must not become a fresh restore after someone archives the branch.
        val noOp = assertNotNull(change(tree, BranchArchiveIntent.Restore(main)).tree)
        val changed = accepted(change(noOp, BranchArchiveIntent.Archive(main), 701))
        val retried = change(changed, BranchArchiveIntent.Restore(main))
        assertEquals("noOp", retried.outcome)
        assertNull(retried.tree)
    }

    @Test fun recoveryRestoresBothArchiveStateAndCapturedSelectionNotGameplayUndoOrOldQueues() {
        val archived = accepted(change(queued(tree), BranchArchiveIntent.ArchiveAndReturn(reaction, returnTarget)))
        val nextCatalog = catalog.copy(selected = returnTarget, selectionControlId = f.id(700))
        val input = queued(archived)
        val recovered = accepted(change(input, BranchArchiveIntent.Recover(f.id(700), true), 701, nextCatalog))
        assertTrue(recovered.branchArchives.archivedBranchIds.isEmpty())
        assertEquals(right.currentNodeId, recovered.currentNodeId)
        assertTrue(recovered.redoByAccount.isEmpty())
        assertEquals(input.nodes, recovered.nodes)
        val metadataOnly = accepted(change(input, BranchArchiveIntent.Recover(f.id(700), false), 702, nextCatalog))
        assertEquals(left.currentNodeId, metadataOnly.currentNodeId)
        assertEquals(input.redoByAccount, metadataOnly.redoByAccount)
        val retry = change(recovered, BranchArchiveIntent.Recover(f.id(700), true), 701)
        assertTrue(retry.recoveredResult)
        assertNull(retry.tree)
    }

    @Test fun recoveryRejectsChangedMetadataContextOrSelectionWithoutPartialEffects() {
        val archived = accepted(change(tree, BranchArchiveIntent.ArchiveAndReturn(reaction, returnTarget)))
        val nextCatalog = catalog.copy(selected = returnTarget, selectionControlId = f.id(700))
        val selectedElsewhere = nextCatalog.copy(selected = childTarget)
        val switchedElsewhere = archived.copy(currentNodeId = childTarget.nodeId)
        assertEquals("changedSelection", change(switchedElsewhere, BranchArchiveIntent.Recover(f.id(700), true),
            701, selectedElsewhere).reason)
        val restored = accepted(change(archived, BranchArchiveIntent.Restore(reaction), 701, nextCatalog))
        val archivedAgain = accepted(change(restored, BranchArchiveIntent.Archive(reaction), 702, nextCatalog))
        assertEquals("changedArchive", change(archivedAgain, BranchArchiveIntent.Recover(f.id(700), true),
            703, nextCatalog).reason)
        val revised = nextCatalog.copy(heads = nextCatalog.heads + (reaction to selected.copy(version = "2")))
        assertEquals("changedReturn", change(archived, BranchArchiveIntent.Recover(f.id(700), true), 704, revised).reason)
        val missing = nextCatalog.copy(heads = nextCatalog.heads - reaction)
        assertEquals("changedArchive", change(archived, BranchArchiveIntent.Recover(f.id(700), true), 705, missing).reason)
        assertEquals(setOf(reaction), archived.branchArchives.archivedBranchIds)
        assertEquals(left.currentNodeId, archived.currentNodeId)
    }

    @Test fun capturedReturnHeadAndSelectionControlIdentityPreventSilentGrowthAndAbaRecovery() {
        val captured = BranchArchiveIntent.ArchiveAndReturn(reaction, returnTarget,
            expectedReturnHead = returnTarget)
        val grown = catalog.copy(heads = catalog.heads + (main to returnTarget.copy(nodeId = childTarget.nodeId)),
            internal = listOf(returnTarget))
        assertEquals("changedReturn", change(tree, captured, directory = grown).reason)
        val archived = accepted(change(tree, captured))
        // Shared selection moved away and back. Same node/board is not the original control state.
        val backAgain = catalog.copy(selected = returnTarget, selectionControlId = f.id(999))
        assertEquals("changedSelection", change(archived, BranchArchiveIntent.Recover(f.id(700), true),
            701, backAgain).reason)
        // Metadata-only restoration is independent of selection and preserves newer queues.
        val input = queued(archived)
        assertEquals(input.redoByAccount, accepted(change(input, BranchArchiveIntent.Recover(f.id(700), false),
            702, backAgain)).redoByAccount)
    }

    @Test fun rootAndSelectedBranchesCannotBeArchivedWithoutAnotherActiveSelection() {
        assertEquals("returnRequired", change(tree, BranchArchiveIntent.Archive(reaction)).reason)
        val root = GameplayTree(f.root())
        val target = BranchArchiveTarget(main, root.currentNodeId, "1")
        val directory = ArchiveCatalog(target, mapOf(main to target))
        assertEquals("returnRequired", change(root, BranchArchiveIntent.Archive(main), directory = directory).reason)
        assertEquals("selectOtherBranch", change(root, BranchArchiveIntent.ArchiveAndReturn(main, target),
            directory = directory).reason)
        // Archiving the exact root branch IS possible when a different retained branch stays active.
        assertEquals(setOf(main), accepted(change(tree, BranchArchiveIntent.Archive(main))).branchArchives.archivedBranchIds)
    }

    @Test fun sameCurrentReturnIsMetadataOnlyButDifferentBranchAtSameBoundaryIsSharedSwitch() {
        val input = queued(tree)
        val same = accepted(change(input, BranchArchiveIntent.ArchiveAndReturn(main, selected)))
        assertEquals(input.redoByAccount, same.redoByAccount)
        val common = BranchArchiveTarget(main, right.currentNodeId, "1")
        val directory = catalog.copy(internal = listOf(common))
        val differentBranch = accepted(change(input, BranchArchiveIntent.ArchiveAndReturn(reaction, common),
            directory = directory))
        assertEquals(input.currentNodeId, differentBranch.currentNodeId)
        assertTrue(differentBranch.redoByAccount.isEmpty())
        assertEquals(input.nodes, differentBranch.nodes)
    }

    @Test fun scopeAccessValidationAndOriginalRejectedOutcomeArePreserved() {
        val action = request(BranchArchiveIntent.Archive(main))
        assertTrue(BranchArchives.valid(action))
        assertFalse(BranchArchives.valid(action.copy(scope = scope.copy(preparationId = f.id(3)))))
        assertEquals("inapplicable", BranchArchives.change(tree, catalog, f.a,
            action.copy(scope = scope.copy(playId = f.id(999)))).reason)
        assertEquals("accessDenied", BranchArchives.change(tree, catalog, f.id(999), action).reason)
        val rejectedRequest = request(BranchArchiveIntent.Archive(f.id(999)))
        val rejected = BranchArchives.change(tree, catalog, f.a, rejectedRequest)
        assertEquals("missingBranch", rejected.reason)
        val retry = BranchArchives.change(assertNotNull(rejected.tree), catalog, f.a, rejectedRequest)
        assertTrue(retry.recoveredResult)
        assertEquals("rejected", retry.outcome)
        assertNull(retry.tree)
    }
}
