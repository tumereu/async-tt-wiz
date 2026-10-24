package org.asyncttwiz.protocol

import kotlin.test.*

class HistoryRuntimeIntegrationTest {
    @Test fun preparedSingleReviewEndpointIsDisposableAcrossSelectiveEdits() {
        var tree = GameplayTree(gameplayFixture())
        tree = play(tree, PlayAction.SetTapped(gameplayId(10), true))
        tree = play(tree, PlayAction.SetNote(gameplayId(11), "reviewed"), b)
        val selected = tree.currentNodeId
        tree = play(tree, PlayAction.SetNote(gameplayId(11), "later"), b)
        val review = assertNotNull(tree.prepareReview(selected))
        assertTrue(review.board.cards.single { it.id == gameplayId(10) }.tapped)
        assertSame(review, tree.stateAt(selected))
        val undo = command(tree, GameAction.Undo)
        val edited = assertNotNull(SelectiveUndo.change(tree, a, undo).tree)
        assertFalse(edited.stateAt(selected)!!.board.cards.single { it.id == gameplayId(10) }.tapped)
        assertTrue(tree.stateAt(selected)!!.board.cards.single { it.id == gameplayId(10) }.tapped)
        assertEquals(edited, roundtrip(edited))
    }
    private var request = 10000
    private val a = gameplayId(7)
    private val b = gameplayId(8)
    private fun command(tree: GameplayTree, action: GameAction, at: String? = null) = GameCommand(gameplayId(request++),
        GameScope(tree.initial.gameId, playId = tree.initial.id), action, gameplayId(80), "1",
        continuation = at?.let { Continuation(it, true) })
    private fun step(tree: GameplayTree, action: GameAction, actor: String = a, at: String? = null): GameplayTree =
        HistoryContinuation.change(tree, actor, command(tree, action, at)).let {
            assertEquals("accepted", it.outcome, "$action: ${it.reason}"); assertNotNull(it.tree) }
    private fun play(tree: GameplayTree, action: PlayAction, actor: String = a, at: String? = null) =
        step(tree, GameAction.Play(action), actor, at)
    private fun roundtrip(tree: GameplayTree): GameplayTree = TreeSync.decode(
        TreeSync.records(TreeSync.generation(tree)), tree.initial.gameId, tree.initial.id).tree.also {
            assertEquals(tree, it); assertEquals(tree.stateAt(tree.currentNodeId), it.stateAt(it.currentNodeId)) }

    @Test fun retainedReferencesProtectExactInactiveMaterializationsWithoutWholeBoardLocks() {
        var tree = GameplayTree(gameplayFixture())
        tree = play(tree, PlayAction.SetTapped(gameplayId(10), true))
        val tap = tree.currentNodeId
        tree = play(tree, PlayAction.SetNote(gameplayId(11), "independent"), b)
        val independent = tree.currentNodeId
        tree = play(tree, PlayAction.EventComment(EventReference(tree.initial.id, tap, tree.contextVersion(tap)), "about tap"), b)
        assertNotNull(roundtrip(tree).stateAt(tree.currentNodeId))
        val blocked = HistoryContinuation.change(tree, a, command(tree, GameAction.Undo))
        assertEquals("replayBlocked", blocked.reason); assertNull(blocked.tree)
        // An unrelated root reference preserves its original materialization under selective edits.
        var unrelated = GameplayTree(gameplayFixture())
        unrelated = play(unrelated, PlayAction.SetTapped(gameplayId(10), true))
        unrelated = play(unrelated, PlayAction.EventComment(EventReference(unrelated.initial.id, unrelated.initial.root.id), "root"), b)
        val removed = step(unrelated, GameAction.Undo)
        assertEquals(1, removed.nodes.size); roundtrip(removed)
        // Inactive references are real retained contexts, not the current board relabelled by ID.
        val fork = play(tree, PlayAction.SetLife(a, "99"), at = tree.initial.root.id)
        val annotated = play(fork, PlayAction.Ping(EventReference(fork.initial.id, independent,
            fork.contextVersion(independent)), PingTarget(PingTargetKind.CARD, gameplayId(11))))
        roundtrip(annotated)
        assertEquals("missingReference", HistoryContinuation.change(annotated, a, command(annotated,
            GameAction.Play(PlayAction.EventComment(EventReference(annotated.initial.id, independent, "0"), "stale")))).reason)
        // A reference recorded on another offshoot is not on the target's linear suffix.
        var crossBranch = play(GameplayTree(gameplayFixture()), PlayAction.SetTapped(gameplayId(10), true))
        val referenced = crossBranch.currentNodeId
        crossBranch = play(crossBranch, PlayAction.EventComment(EventReference(crossBranch.initial.id, referenced),
            "inactive offshoot dependency"), b, crossBranch.initial.root.id)
        crossBranch = step(crossBranch, GameAction.MakeCurrent(referenced))
        val refusal = HistoryContinuation.change(crossBranch, a, command(crossBranch, GameAction.Undo))
        assertEquals("referenceBlocked", refusal.reason); assertEquals(referenced, refusal.targetId)
        assertNotNull(roundtrip(crossBranch).stateAt(crossBranch.branches.values.single().firstNodeId))
    }

    @Test fun retainedCatalogTracksHeadsDisplacedVersionsAndEveryDeliberateSwitchIdentity() {
        var tree = GameplayTree(gameplayFixture())
        tree = play(tree, PlayAction.SetLife(a, "21"))
        val oldHead = tree.currentNodeId
        val rootBranch = tree.currentBranchId
        val selectionBeforeUndo = tree.selectionControlId
        tree = step(tree, GameAction.Undo)
        assertEquals(tree.initial.root.id, tree.archiveCatalog().head(rootBranch)!!.nodeId)
        tree = step(tree, GameAction.Redo)
        assertEquals(oldHead, tree.archiveCatalog().head(rootBranch)!!.nodeId)
        assertEquals(selectionBeforeUndo, tree.selectionControlId)
        tree = play(tree, PlayAction.SetNote(gameplayId(11), "alternative"), at = tree.initial.root.id)
        val alternative = tree.currentBranchId
        val forkControl = tree.selectionControlId
        assertNotNull(forkControl)
        assertEquals(oldHead, tree.archiveCatalog().head(rootBranch)!!.nodeId)
        val preview = BranchArchives.previewReturn(tree, tree.archiveCatalog(), alternative)
        assertFalse(preview.needsSelection, preview.reason)
        assertEquals(oldHead, preview.target!!.nodeId)
        val request = command(tree, GameAction.BranchArchive(BranchArchiveIntent.ArchiveAndReturn(alternative,
            preview.target, expectedReturnHead = preview.returnHead)))
        tree = assertNotNull(HistoryContinuation.change(tree, b, request).tree)
        assertEquals(rootBranch, tree.currentBranchId); assertEquals(request.requestId, tree.selectionControlId)
        assertTrue(alternative in tree.branchArchives.archivedBranchIds)
        val archivedHead = tree.branchHeads.getValue(alternative)
        assertEquals("restoreReturn", HistoryContinuation.change(tree, a,
            command(tree, GameAction.MakeCurrent(archivedHead))).reason)
        val archived = roundtrip(tree)
        val metadata = step(tree, GameAction.BranchArchive(BranchArchiveIntent.Restore(alternative)))
        assertEquals(request.requestId, metadata.selectionControlId)
        val away = step(tree, GameAction.MakeCurrent(tree.initial.root.id))
        val back = step(away, GameAction.MakeCurrent(oldHead))
        assertNotEquals(request.requestId, back.selectionControlId)
        val recover = command(back, GameAction.BranchArchive(BranchArchiveIntent.Recover(request.requestId, true)))
        assertEquals("changedSelection", HistoryContinuation.change(back, a, recover).reason)
        assertEquals("noOp", HistoryContinuation.change(back, a, command(back, GameAction.MakeCurrent(oldHead))).outcome)
        assertEquals(archived, roundtrip(archived))
    }

    @Test fun failedAndNoopHistoricalInputsPreserveStacksFirstSuccessForksAndFullDeltaKeepsOffshoots() {
        var tree = GameplayTree(gameplayFixture())
        tree = play(tree, PlayAction.SetLife(a, "21"))
        tree = play(tree, PlayAction.SetNote(gameplayId(11), "B"), b)
        tree = step(tree, GameAction.Undo)
        val before = roundtrip(tree)
        val noOp = command(tree, GameAction.Play(PlayAction.SetTapped(gameplayId(10), false)), tree.initial.root.id)
        assertEquals("noOp", HistoryContinuation.change(tree, b, noOp).outcome)
        val failure = command(tree, GameAction.Play(PlayAction.SetNote(gameplayId(999), "missing")), tree.initial.root.id)
        assertEquals("rejected", HistoryContinuation.change(tree, b, failure).outcome)
        assertEquals(before, tree)
        val next = play(tree, PlayAction.SetTapped(gameplayId(10), true), b, tree.initial.root.id)
        assertTrue(next.redoByAccount.isEmpty()); assertEquals(1, next.branches.size)
        val delta = TreeSync.delta(tree, next)
        assertTrue(delta.upserts.none { it.kind == "root" })
        assertEquals(next, TreeSync.apply(TreeSync.generation(tree), delta).tree)
        assertNotNull(roundtrip(next).stateAt(tree.currentNodeId))
    }

    @Test fun ordinaryRetainedReviewAndUndoHaveNoAncestryOrPayloadBudget() {
        val initial = gameplayFixture()
        var state = initial
        val nodes = linkedMapOf<String, GameplayMutation>()
        repeat(2055) {
            val change = GameplayRules.change(state, a, command(GameplayTree(initial), GameAction.Play(PlayAction.AdjustLife(a, "1"))))
            state = assertNotNull(change.state); val node = assertNotNull(change.mutation); nodes[node.id] = node
        }
        val tree = GameplayTree(initial, nodes, state.currentNodeId,
            branchHeads = mapOf(initial.root.branchId to state.currentNodeId))
        assertEquals(state, roundtrip(tree).stateAt(state.currentNodeId))
        assertNotNull(HistoryContinuation.change(tree, a, command(tree, GameAction.Undo)).tree)
        val small = play(GameplayTree(initial), PlayAction.CloneToken(gameplayId(10)))
        val before = TreeSync.records(TreeSync.generation(small)).toList()
        assertNotNull(SelectiveUndo.change(small, a, command(small, GameAction.Undo)).tree)
        assertEquals(before, TreeSync.records(TreeSync.generation(small)).toList())
    }
}
