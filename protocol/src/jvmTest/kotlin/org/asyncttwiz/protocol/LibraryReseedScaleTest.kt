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
        fun edit(command: GameCommand, actor: String = F.a) {
            val before = tree
            tree = F.change(tree, command, actor)
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
        tree.work.reset()

        // Removing a fork must not make a retained opponent fork's reseed depend on the old input order.
        edit(F.command(tree, GameAction.Play(PlayAction.SetNote(F.card, "inactive")), 800103)
            .copy(continuation = Continuation(target, true)), F.b)
        val inactive = tree.preparedState(tree.currentNodeId)!!
        edit(F.command(tree, GameAction.Play(PlayAction.SetNote(F.card, "continued")), 800104)
            .copy(continuation = Continuation(forked.currentNodeId, true)), F.b)
        val continued = tree.preparedState(tree.currentNodeId)!!
        val continuedNodes = tree.nodes
        edit(F.command(tree, GameAction.Undo, 800105))
        assertFalse(forked.currentNodeId in tree.nodes)
        assertEquals(continued.board.containers, tree.preparedState(tree.currentNodeId)!!.board.containers)
        assertEquals(inactive, tree.prepareReview(inactive.currentNodeId))
        assertNotNull(tree.prepareReview(continued.currentNodeId))
        edit(F.command(tree, GameAction.Redo, 800106))
        assertEquals(continued, tree.preparedState(tree.currentNodeId))
        assertEquals(continuedNodes, tree.nodes)

        // Reuse the same large prefix: a retained inactive reference still blocks the original target.
        edit(F.command(tree, GameAction.Undo, 800107), F.b)
        edit(F.command(tree, GameAction.Play(PlayAction.EventComment(EventReference(root.id,
            forked.currentNodeId, tree.contextVersion(forked.currentNodeId)), "retained dependency")), 800108)
            .copy(continuation = Continuation(target, true)), F.b)
        edit(F.command(tree, GameAction.Play(PlayAction.SetNote(F.card, "referenced continuation")), 800109)
            .copy(continuation = Continuation(forked.currentNodeId, true)), F.b)
        val blocked = HistoryContinuation.change(tree, F.a, F.command(tree, GameAction.Undo, 800110))
        assertEquals("referenceBlocked", blocked.reason)
        assertEquals(forked.currentNodeId, blocked.targetId)
        assertNull(blocked.tree)
        assertEquals(0, tree.work.rootMaterializations)
        assertEquals(0, tree.work.fullHistoryScans)
        assertTrue(tree.work.replayedNodes + tree.work.unappliedNodes <= 100)
        assertTrue(tree.work.ancestrySteps <= 150)
        assertTrue(tree.work.indexedNodes <= 50)
    }
}
