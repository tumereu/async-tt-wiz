package org.asyncttwiz.protocol

import kotlin.test.*

class HistoryContinuationTest {
    private val f = UndoFixture
    private fun command(n: Int, action: PlayAction, at: String? = null) =
        f.command(GameAction.Play(action), n).copy(invocationId = f.id(800),
            continuation = at?.let { Continuation(it, true) })

    @Test fun firstSuccessfulMutationOnlyAndImmutableSharedPrefix() {
        val prefix = f.tap(GameplayTree(f.root()))
        val live = f.tap(prefix, f.b, 12, n = 101)
        val noOp = command(102, PlayAction.SetTapped(f.id(9), true), prefix.currentNodeId)
        assertEquals("noOp", live.play(f.a, noOp).outcome)
        assertEquals("missingReference", live.play(f.a,
            command(103, PlayAction.SetTapped(f.id(999), true), prefix.currentNodeId)).reason)
        assertTrue(live.branches.isEmpty())
        val first = assertNotNull(live.play(f.a,
            command(104, PlayAction.AdjustLife(f.a, "1"), prefix.currentNodeId)).tree)
        assertEquals(3, first.nodes.size)
        assertEquals(live.initial, first.initial)
        for ((id, node) in live.nodes) assertEquals(node, first.nodes[id])
        val branch = first.branches.values.single()
        assertEquals(prefix.currentNodeId, branch.forkNodeId)
        assertEquals(live.currentNodeId, branch.displacedNodeId)
        assertEquals(live.currentBranchId, branch.displacedBranchId)
        assertEquals(branch.id, first.nodes.getValue(first.currentNodeId).branchId)
        val next = assertNotNull(first.play(f.a,
            command(105, PlayAction.AdjustLife(f.a, "1"), first.currentNodeId)).tree)
        assertEquals(first.branches, next.branches)
        assertEquals(first.currentBranchId, next.currentBranchId)
        assertEquals("22", next.stateAt(next.currentNodeId)!!.board.players[0].life)
        assertEquals("20", next.stateAt(live.currentNodeId)!!.board.players[0].life)
        val restored = GameWire.json.decodeFromString(GameplayTree.serializer(),
            GameWire.json.encodeToString(GameplayTree.serializer(), next))
        assertEquals(next, restored)
        assertEquals(next.stateAt(next.currentNodeId), restored.stateAt(restored.currentNodeId))
    }

    @Test fun exactBoundaryNoFallbackAndOrdinaryLateProcessing() {
        val live = f.play(GameplayTree(f.root()), f.b, PlayAction.SetLife(f.a, "30"), 100)
        val ordinary = assertNotNull(live.play(f.a, command(101, PlayAction.AdjustLife(f.a, "1"))).tree)
        assertEquals("31", ordinary.stateAt(ordinary.currentNodeId)!!.board.players[0].life)
        val historical = assertNotNull(live.play(f.a,
            command(102, PlayAction.AdjustLife(f.a, "1"), live.initial.root.id)).tree)
        assertEquals("21", historical.stateAt(historical.currentNodeId)!!.board.players[0].life)
        assertEquals("historyUnavailable", historical.play(f.a,
            command(103, PlayAction.AdjustLife(f.a, "1"), f.id(999))).reason)
        assertEquals("invalidInput", historical.play(f.a,
            command(104, PlayAction.AdjustLife(f.a, "1")).copy(continuation = Continuation(f.id(5), false))).reason)
        val removed = assertNotNull(f.undo(live, f.b).tree)
        assertEquals("historyUnavailable", removed.play(f.a,
            command(105, PlayAction.AdjustLife(f.a, "1"), live.currentNodeId)).reason)
    }

    @Test fun historicalPlayStillRequiresMatchingScopeAndParticipant() {
        val tree = f.tap(GameplayTree(f.root()))
        val input = command(120, PlayAction.AdjustLife(f.a, "1"), tree.initial.root.id)
        assertEquals("accessDenied", HistoryContinuation.change(tree, f.id(999), input).reason)
        assertEquals("inapplicable", HistoryContinuation.change(tree, f.a,
            input.copy(scope = GameScope(f.id(999), playId = f.id(2)))).reason)
    }

    @Test fun historicalSuccessClearsBothQueuesButFailureDoesNotAndUndoRestoresPriorBranchContext() {
        val first = f.tap(GameplayTree(f.root()))
        val second = f.tap(first, f.b, 12, n = 101)
        val undoA = assertNotNull(f.undo(second).tree)
        val both = assertNotNull(f.undo(undoA, f.b).tree)
        val root = both.currentNodeId
        // Retained current future supplies a real displacement, while both stacks remain current control state.
        val live = f.play(both, f.a, PlayAction.AdjustLife(f.a, "1"), 102)
            .copy(redoByAccount = both.redoByAccount)
        val noOp = live.play(f.a, command(103, PlayAction.SetTapped(f.id(9), false), root))
        assertEquals("noOp", noOp.outcome)
        assertEquals(2, live.redoByAccount.size)
        val branch = assertNotNull(live.play(f.a, command(104, PlayAction.AdjustLife(f.b, "1"), root)).tree)
        assertTrue(branch.redoByAccount.isEmpty())
        val undone = assertNotNull(f.undo(branch).tree)
        assertEquals(root, undone.currentNodeId)
        assertEquals(branch.initial.root.branchId, undone.currentBranchId)
        assertEquals(branch.initial.root.branchId, undone.stateAt(root)!!.currentBranchId)
        assertEquals(branch.initial.root, undone.initial.root)
        val restored = assertNotNull(f.redo(undone).tree)
        assertEquals(branch.nodes, restored.nodes)
        assertEquals(branch.currentBranchId, restored.stateAt(restored.currentNodeId)!!.currentBranchId)
    }
}
