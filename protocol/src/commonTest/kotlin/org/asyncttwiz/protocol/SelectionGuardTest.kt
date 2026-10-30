package org.asyncttwiz.protocol

import kotlin.test.*

/** Owner rule 2026-10-30: only a deliberate branch changes the active line. An ordinary action that raced
 * with the opponent's deliberate branch is refused, never moved to another line; everything else is as before. */
class SelectionGuardTest {
    private val f = UndoFixture
    private fun play(n: Int, action: PlayAction, seen: GameplayTree?) =
        f.command(GameAction.Play(action), n).copy(expectedSelection = seen?.selectionKey)
    private fun continuation(n: Int, action: PlayAction, at: String) =
        f.command(GameAction.Play(action), n).copy(continuation = Continuation(at, true))
    private fun life(tree: GameplayTree, account: String = f.a) =
        assertNotNull(tree.stateAt(tree.currentNodeId)).board.players.single { it.accountId == account }.life

    @Test fun opponentsConcurrentActionOnTheSameLineStillComesFirstAndMineAppliesAfterIt() {
        val seen = f.tap(GameplayTree(f.root()))
        val opponent = f.play(seen, f.b, PlayAction.SetLife(f.a, "30"), 101)
        val mine = HistoryContinuation.change(opponent, f.a, play(102, PlayAction.AdjustLife(f.a, "-1"), seen))
        val next = assertNotNull(mine.tree, mine.reason)
        assertEquals(opponent.currentNodeId, next.nodes.getValue(next.currentNodeId).parentId)
        assertEquals("29", life(next))
        // Exactly the tree an older client (no expected selection) gets.
        val legacy = assertNotNull(HistoryContinuation.change(opponent, f.a, play(102, PlayAction.AdjustLife(f.a, "-1"), null)).tree)
        assertEquals(legacy.nodes, next.nodes); assertEquals(legacy.currentNodeId, next.currentNodeId)
    }

    @Test fun opponentsUndoAndRedoBeforeMineDoNotRefuseIt() {
        val first = f.tap(GameplayTree(f.root()))
        val seen = f.tap(first, f.b, 12, n = 101)
        val undone = assertNotNull(f.undo(seen, f.b).tree)
        assertEquals(seen.selectionKey, undone.selectionKey, "Undo never changes the active line")
        val afterUndo = assertNotNull(HistoryContinuation.change(undone, f.a, play(102, PlayAction.AdjustLife(f.a, "-1"), seen)).tree)
        assertEquals(undone.currentNodeId, afterUndo.nodes.getValue(afterUndo.currentNodeId).parentId)
        val redone = assertNotNull(f.redo(undone, f.b).tree)
        assertEquals(seen.selectionKey, redone.selectionKey, "Redo never changes the active line")
        val afterRedo = assertNotNull(HistoryContinuation.change(redone, f.a, play(103, PlayAction.AdjustLife(f.a, "-1"), seen)).tree)
        assertEquals(redone.currentNodeId, afterRedo.nodes.getValue(afterRedo.currentNodeId).parentId)
    }

    @Test fun ownActionQueuedRightAfterOwnBranchApplies() {
        val split = f.tap(GameplayTree(f.root()))
        val live = f.play(split, f.b, PlayAction.SetLife(f.a, "30"), 101)
        val branch = continuation(102, PlayAction.AdjustLife(f.a, "1"), split.currentNodeId)
        // The client predicts its own continuation and bases the next action on that predicted line.
        val predicted = assertNotNull(HistoryContinuation.change(live, f.a, branch).tree)
        assertEquals(branch.requestId, predicted.selectionKey)
        val queued = play(103, PlayAction.AdjustLife(f.a, "1"), predicted)
        val server = assertNotNull(HistoryContinuation.change(live, f.a, branch).tree)
        val next = assertNotNull(HistoryContinuation.change(server, f.a, queued).tree)
        assertEquals(server.currentNodeId, next.nodes.getValue(next.currentNodeId).parentId)
        assertEquals("22", life(next))
        // Later same-line play on the new branch keeps the same selection.
        assertEquals(server.selectionKey, next.selectionKey)
    }

    @Test fun opponentsBranchBeforeMineArrivesRefusesMineWithoutAnyEdit() {
        val split = f.tap(GameplayTree(f.root()))
        val seen = f.play(split, f.b, PlayAction.SetLife(f.a, "30"), 101)
        val branched = assertNotNull(HistoryContinuation.change(seen, f.b,
            continuation(102, PlayAction.AdjustLife(f.b, "1"), split.currentNodeId)).tree)
        assertNotEquals(seen.selectionKey, branched.selectionKey)
        val mine = HistoryContinuation.change(branched, f.a, play(103, PlayAction.AdjustLife(f.a, "-1"), seen))
        assertEquals("branched", mine.reason); assertNull(mine.tree)
        assertEquals("rejected", mine.outcome)
        // A batch is refused as a whole.
        val batch = f.command(GameAction.PlayBatch(listOf(PlayAction.AdjustLife(f.a, "-1"), PlayAction.SetTapped(f.id(12), true))), 700)
            .copy(expectedSelection = seen.selectionKey)
        assertEquals("branched", HistoryContinuation.change(branched, f.a, batch).reason)
        // Having seen the branch, the same action applies on it.
        assertNotNull(HistoryContinuation.change(branched, f.a, play(104, PlayAction.AdjustLife(f.a, "-1"), branched)).tree)
        assertNotNull(HistoryContinuation.change(branched, f.a, batch.copy(expectedSelection = branched.selectionKey)).tree)
        // An older client without the field keeps today's behaviour: applied at the new head.
        val legacy = assertNotNull(HistoryContinuation.change(branched, f.a, play(105, PlayAction.AdjustLife(f.a, "-1"), null)).tree)
        assertEquals(branched.currentNodeId, legacy.nodes.getValue(legacy.currentNodeId).parentId)
    }

    @Test fun wireRules() {
        val command = play(103, PlayAction.AdjustLife(f.a, "-1"), GameplayTree(f.root()))
        assertEquals(f.root().root.id, command.expectedSelection)
        assertTrue(GameWire.valid(command))
        assertEquals(command, GameWire.decodeClient(GameWire.json.encodeToString(GameClientMessage.serializer(), command)))
        assertFalse(GameWire.valid(command.copy(expectedSelection = "not-an-id")))
        assertFalse(GameWire.valid(command.copy(continuation = Continuation(f.id(5), true))), "a continuation is the deliberate branch itself")
        assertFalse(GameWire.valid(f.command(GameAction.Undo, 104).copy(expectedSelection = f.id(5))))
        // Older servers reject unknown keys: without the field a command encodes and digests exactly as before.
        val legacy = command.copy(expectedSelection = null)
        assertFalse("expectedSelection" in GameWire.json.encodeToString(GameClientMessage.serializer(), legacy))
        assertEquals(GameWire.digest(legacy), GameWire.digest(f.command(GameAction.Play(PlayAction.AdjustLife(f.a, "-1")), 103)))
    }
}
