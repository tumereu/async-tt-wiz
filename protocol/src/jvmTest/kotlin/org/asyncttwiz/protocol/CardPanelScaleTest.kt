package org.asyncttwiz.protocol

import org.asyncttwiz.fixtures.ScaleGames as F
import kotlin.test.*

class CardPanelScaleTest {
    @Test fun panelEventsSyncAndNearbyTraversalStayIncrementalAtTenThousandActions() {
        var tree = F.build(10000)
        val head = tree.preparedState(tree.currentNodeId)!!
        val card = head.board.cards.single { it.id == F.id(12) }
        tree = F.play(tree, PlayAction.MoveCard(card.id, MoveSource(card.containerId, card.placement),
            MoveDestination(F.id(13), MoveInsertion.UNORDERED)), 800001)
        val second = tree.preparedState(tree.currentNodeId)!!.board.cards.single { it.id == F.card }
        tree = F.play(tree, PlayAction.MoveCard(second.id, MoveSource(second.containerId, second.placement),
            MoveDestination(F.id(13), MoveInsertion.UNORDERED)), 800002)
        val baseline = TreeSync.generation(tree)
        var replica = baseline
        fun event(action: PlayAction, n: Int) {
            tree.work.reset()
            val before = tree
            tree = F.play(before, action, n)
            replica = TreeSync.apply(replica, TreeSync.delta(before, tree))
            val mutation = tree.nodes.getValue(tree.currentNodeId)
            val state = tree.preparedState(tree.currentNodeId)!!
            assertEquals(state, replica.tree.preparedState(replica.tree.currentNodeId))
            assertEquals(before.preparedState(before.currentNodeId), GameplayHistory.unapply(state, mutation))
            assertNotNull(tree.prepareReview(mutation.parentId))
            assertNotNull(tree.prepareReview(mutation.id))
            assertEquals(0, tree.work.rootMaterializations)
            assertEquals(0, tree.work.fullHistoryScans)
            assertTrue(tree.work.replayedNodes + tree.work.unappliedNodes <= 12)
            assertTrue(tree.work.indexedNodes <= 12)
            assertTrue(tree.work.ancestrySteps <= 24)
        }
        event(PlayAction.ShowContents(F.id(13), ContentsSelection.ALL, reveal = true), 800003)
        val revealPanel = tree.preparedState(tree.currentNodeId)!!.board.contentsDisplays.single()
        event(PlayAction.ReorderContents(revealPanel.id, revealPanel.cardIds.last(), revealPanel.cardIds.first()), 800004)
        event(PlayAction.DismissContents(revealPanel.id), 800005)
        event(PlayAction.ShowContents(F.id(13), ContentsSelection.ALL), 800006)
        val panel = tree.preparedState(tree.currentNodeId)!!.board.contentsDisplays.single()
        event(PlayAction.RevealContentsCard(panel.id, card.id), 800007)
        event(PlayAction.MoveCard(card.id, MoveSource(F.id(13)), MoveDestination(F.id(10), MoveInsertion.EMPTY, BoardPosition(1024, 0))), 800008)
        event(PlayAction.DismissContents(panel.id, seed = RandomV1.encodeSeed(ByteArray(32))), 800009)
    }
}
