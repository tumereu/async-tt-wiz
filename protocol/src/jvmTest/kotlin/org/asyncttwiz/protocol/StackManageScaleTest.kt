package org.asyncttwiz.protocol

import org.asyncttwiz.fixtures.ScaleGames as F
import kotlin.test.*

class StackManageScaleTest {
    @Test fun copyControllerUndoRedoAndSyncStayIncrementalAfterLongHistories() {
        for (size in listOf(1000, 10000)) {
            var tree = F.build(size)
            tree = F.play(tree, PlayAction.Stack(StackAction.AddEffect(F.card,
                GameplayRules.stackDigest(tree.stateAt(tree.currentNodeId)!!))), 800000)
            val before = tree
            val source = tree.stateAt(tree.currentNodeId)!!.board.stackEffects.last().id
            tree.work.reset()
            tree = F.play(tree, PlayAction.Stack(StackAction.CopyEffect(source,
                GameplayRules.stackDigest(tree.stateAt(tree.currentNodeId)!!))), 800001)
            val copied = tree.stateAt(tree.currentNodeId)!!.board.stackEffects.last().id
            val delta = TreeSync.delta(before, tree)
            assertEquals(1, delta.upserts.count { it.kind == "node" })
            tree = F.play(tree, PlayAction.Stack(StackAction.SetController(copied, F.b)), 800002)
            val controlled = tree.stateAt(tree.currentNodeId)!!.board
            tree = F.change(tree, F.command(tree, GameAction.Undo, 800003))
            tree = F.change(tree, F.command(tree, GameAction.Redo, 800004))
            assertEquals(controlled, tree.stateAt(tree.currentNodeId)!!.board)
            assertEquals(0, tree.work.fullHistoryScans)
            assertEquals(0, tree.work.rootMaterializations)
            assertTrue(tree.work.indexedNodes <= 16, "indexed=${tree.work.indexedNodes}")
            assertTrue(tree.work.replayedNodes + tree.work.unappliedNodes <= 16)
        }
    }
}
