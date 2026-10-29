package org.asyncttwiz.android

import org.asyncttwiz.fixtures.ScaleGames as F
import org.asyncttwiz.protocol.*
import org.junit.Test
import java.util.concurrent.Executor
import kotlin.test.*

class CatchupPlaybackTest {
    private fun index(tree: GameplayTree) = HistoryTree(tree.initial.root.id, emptyList(), tree.currentNodeId,
        tree.currentNodeId, containers = tree.initial.board.containers, source = tree)

    private fun append(tree: GameplayTree, n: Int, actor: String = F.a) = F.play(tree,
        PlayAction.AdjustLife(actor, "1"), n, actor)

    @Test fun descendantStartsAtExactLastSeenAndUsesNormalGroupPacing() {
        val root = GameplayTree(F.root())
        val seen = append(root, 100)
        val next = append(seen, 101, F.b)
        val head = append(next, 102)
        val player = HistoryPlayback(index(head))
        player.openFrom(seen.currentNodeId)
        assertEquals(seen.currentNodeId, player.position.cursorId)
        assertEquals(HistoryMode.PLAYBACK, player.position.mode)
        assertTrue(player.position.playing)
        assertFalse(player.branched)
        assertEquals(1_500L, player.stepPauseMillis(1_500))
        assertEquals(1_500L, player.stepPauseMillis(1_500, 350))
        player.next(true)
        assertEquals(next.currentNodeId, player.position.cursorId)
        player.next(true)
        assertEquals(head.currentNodeId, player.position.cursorId)
        player.finishAnimation(head.currentNodeId)
        assertEquals(HistoryMode.LIVE, player.position.mode)
        assertFalse(player.position.playing)
    }

    @Test fun branchedRollsBackGroupsToCommonAncestorThenPlaysForwardNormally() {
        val common = append(GameplayTree(F.root()), 100)
        val old1 = append(common, 101, F.b)
        val old2 = append(old1, 102)
        val branch = F.change(old2, F.command(old2, GameAction.Play(PlayAction.AdjustLife(F.b, "2")), 103)
            .copy(continuation = Continuation(common.currentNodeId, true)), F.b)
        val head = append(branch, 104)
        val player = HistoryPlayback(index(head))
        player.openFrom(old2.currentNodeId)
        assertTrue(player.branched)
        assertEquals(100L, player.stepPauseMillis(1_500))
        assertEquals(350L, player.stepPauseMillis(1_500, 350))
        player.acknowledgeBranch()
        player.next(true)
        assertEquals(old1.currentNodeId, player.position.cursorId)
        player.next(true)
        assertEquals(common.currentNodeId, player.position.cursorId)
        assertFalse(player.branched)
        assertEquals(1_500L, player.stepPauseMillis(1_500))
        player.next(true)
        assertEquals(branch.currentNodeId, player.position.cursorId)
        player.next(true)
        player.finishAnimation(head.currentNodeId)
        assertEquals(HistoryMode.LIVE, player.position.mode)
    }

    @Test fun absentOrUnknownLastSeenStartsAtInitialWithoutMovingTheSharedHead() {
        val head = F.build(30)
        for (bookmark in listOf(null, F.id(999999))) {
            val player = HistoryPlayback(index(head))
            player.openFrom(bookmark)
            assertEquals(head.initial.root.id, player.position.cursorId)
            assertEquals(head.currentNodeId, player.tree.sharedHeadId)
            assertFalse(player.branched)
            assertTrue(player.position.playing)
        }
        val seen = HistoryPlayback(index(head))
        seen.openFrom(head.currentNodeId)
        assertEquals(HistoryMode.LIVE, seen.position.mode)
    }

    @Test fun headMovedDuringPlaybackExtendsTargetAndPauseScrubAndExitRemainAvailable() {
        val seen = append(GameplayTree(F.root()), 100)
        val next = append(seen, 101, F.b)
        val player = HistoryPlayback(index(next))
        player.openFrom(seen.currentNodeId)
        val head = append(next, 102)
        player.receive(index(head))
        assertEquals(seen.currentNodeId, player.position.cursorId)
        player.pause(); player.next(true)
        assertEquals(seen.currentNodeId, player.position.cursorId)
        player.play(); player.next(true)
        assertEquals(next.currentNodeId, player.position.cursorId)
        assertEquals(HistoryMode.PLAYBACK, player.position.mode)
        player.next(true)
        assertEquals(head.currentNodeId, player.position.cursorId)
        player.finishAnimation(head.currentNodeId)
        assertEquals(HistoryMode.LIVE, player.position.mode)
        player.scrub(seen.currentNodeId)
        assertFalse(player.position.playing)
        player.continuationApplied()
        assertEquals(head.currentNodeId, player.position.cursorId)
    }

    @Test fun onlyActuallyPresentedLiveEndpointsArePersistedIncludingOnLeaving() {
        val seen = append(GameplayTree(F.root()), 100)
        val head = append(seen, 101, F.b)
        val saved = mutableListOf<Pair<String, String>>()
        val history = TableHistoryController(Executor { it.run() }, { it() }, { _, _ -> false }, { _, _, _ -> false })
        history.openFrom(seen.currentNodeId) { game, node -> saved += game to node }
        history.update(head, "head")
        history.presented(head.stateAt(seen.currentNodeId)!!, head.stateAt(head.currentNodeId), false)
        history.leaving()
        assertTrue(saved.isEmpty())
        history.playback!!.next(true)
        val state = head.stateAt(head.currentNodeId)!!
        history.playback!!.finishAnimation(state.currentNodeId)
        history.presented(state, state, true)
        assertEquals(listOf(state.gameId to state.currentNodeId), saved)
        history.select(seen.currentNodeId)
        history.close()
        assertEquals(listOf(state.gameId to state.currentNodeId, state.gameId to state.currentNodeId), saved)
    }
}
