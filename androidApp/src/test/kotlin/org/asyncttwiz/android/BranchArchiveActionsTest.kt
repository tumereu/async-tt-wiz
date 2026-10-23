package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import kotlin.test.*
import org.junit.Test

class BranchArchiveActionsTest {
    private fun id(n: Int) = "00000000-0000-4000-8000-${n.toString().padStart(12, '0')}"
    private val actor = id(7)
    private val root = PlayState(id(1), id(2), id(3), id(4), PlayRoot(id(5), id(6)), id(5),
        BoardState(listOf(PlayPlayer(actor), PlayPlayer(id(8))), emptyList(), emptyList(), TurnState(actor)),
        emptyList(), emptyList(), InitialStarter(StarterChoice(actor), actor))
    private val tree = GameplayTree(root)
    private val scope = GameScope(root.gameId, playId = root.id)
    private val target = BranchArchiveTarget(root.root.branchId, root.root.id, "1")
    private val reaction = BranchArchiveTarget(id(50), root.root.id, "1")
    private fun catalog(returnTarget: BranchArchiveTarget? = target) = object : BranchArchiveCatalog {
        override val selected = reaction
        override val selectionControlId: String? = null
        override fun head(branchId: String) = listOf(target, reaction).firstOrNull { it.branchId == branchId }
        override fun resolve(branchId: String, nodeId: String) = head(branchId)?.takeIf { it.nodeId == nodeId }
        override fun displacedReturn(branchId: String) = returnTarget
    }

    @Test fun exactPreviewAndIntentSurviveMenuSelectionAndSyncChangesWithoutRecapture() {
        var current: BranchArchiveView? = BranchArchiveView(scope, tree, catalog())
        val sent = mutableListOf<CapturedBranchArchiveAction>()
        val controller = BranchArchiveController({ current }, { true }) { sent.add(it); true }
        val preview = assertNotNull(controller.prepareReturn(reaction.branchId))
        assertEquals(root, preview.preview.board)
        val captured = assertNotNull(preview.action)
        assertEquals(BranchArchiveIntent.ArchiveAndReturn(reaction.branchId, target,
            expectedReturnHead = target), captured.intent)
        current = current!!.copy(catalog = catalog(target.copy(version = "2")))
        assertTrue(controller.request(captured))
        assertEquals(listOf(captured), sent) // server, not a new UI guess, decides applicability
        assertEquals(root, preview.preview.board)
        assertEquals(target, preview.preview.target)
    }

    @Test fun changedOrMissingDefaultNeedsSelectionArchivedDefaultNeedsExplicitRestore() {
        var current = BranchArchiveView(scope, tree, catalog(target.copy(version = "2")))
        val controller = BranchArchiveController({ current }, { true }) { true }
        val changed = assertNotNull(controller.prepareReturn(reaction.branchId))
        assertTrue(changed.preview.needsSelection)
        assertNull(changed.action)
        current = current.copy(catalog = catalog(null))
        assertNull(controller.prepareReturn(reaction.branchId)!!.action)
        current = current.copy(tree = tree.copy(branchArchives = BranchArchiveState(setOf(target.branchId))),
            catalog = catalog())
        assertEquals("restoreReturn", controller.prepareReturn(reaction.branchId)!!.preview.reason)
        val explicit = controller.prepareReturn(reaction.branchId, target, true)!!
        assertEquals(root, explicit.preview.board)
        assertEquals(BranchArchiveIntent.ArchiveAndReturn(reaction.branchId, target, true, target), explicit.action!!.intent)
    }

    @Test fun disconnectedOrChangedPlayScopeNeverSubmitsCapturedSharedControl() {
        var connected = true
        var current: BranchArchiveView? = BranchArchiveView(scope, tree, catalog())
        var count = 0
        val controller = BranchArchiveController({ current }, { connected }) { count++; true }
        val captured = assertNotNull(controller.capture(BranchArchiveIntent.Archive(target.branchId)))
        connected = false
        assertFalse(controller.request(captured))
        connected = true
        current = current!!.copy(scope = scope.copy(playId = id(99)))
        assertFalse(controller.request(captured))
        assertNull(controller.capture(BranchArchiveIntent.Restore(target.branchId)))
        current = BranchArchiveView(scope.copy(preparationId = id(3)), tree, catalog())
        assertFalse(controller.request(captured))
        current = null
        assertFalse(controller.request(captured))
        assertEquals(0, count)
    }

    @Test fun metadataAndRecoveryRoutesCaptureOriginalControlWithoutLocalUndoOrBusyGate() {
        val sent = mutableListOf<CapturedBranchArchiveAction>()
        val controller = BranchArchiveController({ BranchArchiveView(scope, tree, catalog()) }, { true }) {
            sent.add(it); true
        }
        val intents = listOf(BranchArchiveIntent.Archive(target.branchId), BranchArchiveIntent.Restore(target.branchId),
            BranchArchiveIntent.Recover(id(70), false), BranchArchiveIntent.Recover(id(70), true))
        for (intent in intents) assertTrue(controller.request(assertNotNull(controller.capture(intent))))
        assertEquals(intents, sent.map { it.intent })
        assertTrue(sent.all { it.scope == scope })
        assertTrue(tree.branchArchives.records.isEmpty()) // session callback owns prediction/request identity
        assertEquals(root.root.id, tree.currentNodeId)
        val rejected = BranchArchiveController({ BranchArchiveView(scope, tree, catalog()) }, { true }) { false }
        assertFalse(rejected.request(assertNotNull(rejected.capture(intents.first()))))
    }
}
