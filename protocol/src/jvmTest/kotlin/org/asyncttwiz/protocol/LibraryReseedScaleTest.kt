package org.asyncttwiz.protocol

import org.asyncttwiz.fixtures.ScaleGames as F
import kotlin.test.*

class LibraryReseedScaleTest {
    @Test fun tenThousandActionsForkSyncUndoRedoAndTimelineUseOnlyNearbyDeltaWork() {
        val root = F.root()
        val libraries = listOf(F.a, F.b).mapIndexed { p, owner ->
            PlayContainer(F.id(40 + p), PlayZone.LIBRARY, owner, List(60) { F.id(1000000 + p * 60 + it) })
        }
        val cards = libraries.flatMap { library -> library.cards.map { id -> root.board.cards.first().copy(
            id = id, owner = library.accountId!!, containerId = library.id, placement = null) } }
        var tree = F.build(10000, root.copy(board = root.board.copy(cards = root.board.cards + cards,
            containers = root.board.containers + libraries)))
        var replica = TreeSync.generation(tree)
        val target = tree.nodes.getValue(tree.currentNodeId).parentId
        tree.prepareReview(target)
        val original = tree.preparedState(tree.currentNodeId)!!
        tree.work.reset()
        fun edit(command: GameCommand) {
            val before = tree
            tree = F.change(tree, command)
            replica = TreeSync.apply(replica, TreeSync.delta(before, tree))
            assertEquals(tree.preparedState(tree.currentNodeId), replica.tree.preparedState(replica.tree.currentNodeId))
        }
        edit(F.command(tree, GameAction.Play(PlayAction.AdjustLife(F.a, "1")), 800100)
            .copy(continuation = Continuation(target, true)))
        val mutation = tree.nodes.getValue(tree.currentNodeId)
        val reseeds = mutation.transition.delta!!.libraryReseeds
        assertEquals(2, reseeds.size)
        assertEquals(240, reseeds.sumOf { it.from.size + it.to.size })
        val forked = tree.preparedState(tree.currentNodeId)!!
        repeat(5) {
            assertEquals(original, tree.prepareReview(original.currentNodeId))
            assertEquals(forked, tree.prepareReview(forked.currentNodeId))
        }
        edit(F.command(tree, GameAction.Undo, 800101))
        edit(F.command(tree, GameAction.Redo, 800102))
        assertEquals(forked, tree.preparedState(tree.currentNodeId))
        assertEquals(0, tree.work.rootMaterializations)
        assertEquals(0, tree.work.fullHistoryScans)
        assertTrue(tree.work.replayedNodes + tree.work.unappliedNodes <= 50)
        assertTrue(tree.work.ancestrySteps <= 100)
        assertTrue(tree.work.indexedNodes <= 20)
    }
}
