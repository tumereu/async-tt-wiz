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

    @Test fun descendantOpensPausedAtExactLastSeenThenPlaysWithNormalGroupPacing() {
        val root = GameplayTree(F.root())
        val seen = append(root, 100)
        val next = append(seen, 101, F.b)
        val head = append(next, 102)
        val player = HistoryPlayback(index(head))
        player.openFrom(seen.currentNodeId)
        assertEquals(seen.currentNodeId, player.position.cursorId)
        assertEquals(HistoryMode.PLAYBACK, player.position.mode)
        assertFalse(player.position.playing)
        assertFalse(player.branched)
        player.next(true) // Nothing plays by itself.
        assertEquals(seen.currentNodeId, player.position.cursorId)
        player.play()
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

    @Test fun branchIsDetectedPausedOnOpenAndAutoRewindsToCommonAncestorThenPlaysForwardNormally() {
        val common = append(GameplayTree(F.root()), 100)
        val old1 = append(common, 101, F.b)
        val old2 = append(old1, 102)
        val branch = F.change(old2, F.command(old2, GameAction.Play(PlayAction.AdjustLife(F.b, "2")), 103)
            .copy(continuation = Continuation(common.currentNodeId, true)), F.b)
        val head = append(branch, 104)
        val player = HistoryPlayback(index(head))
        player.openFrom(old2.currentNodeId)
        assertEquals(old2.currentNodeId, player.position.cursorId)
        assertFalse(player.position.playing)
        assertTrue(player.branched)
        assertFalse(player.transportLocked) // Paused: scrubbing and settings stay available.
        player.next(true)
        assertEquals(old2.currentNodeId, player.position.cursorId)
        player.play()
        assertTrue(player.transportLocked)
        assertEquals(100L, player.stepPauseMillis(1_500))
        assertEquals(350L, player.stepPauseMillis(1_500, 350))
        player.next(true)
        assertEquals(old1.currentNodeId, player.position.cursorId)
        assertTrue(player.branched)
        player.next(true)
        assertEquals(common.currentNodeId, player.position.cursorId)
        assertFalse(player.branched)
        assertFalse(player.transportLocked)
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
            assertFalse(player.position.playing)
        }
        val seen = HistoryPlayback(index(head))
        seen.openFrom(head.currentNodeId)
        assertEquals(HistoryMode.LIVE, seen.position.mode)
    }

    @Test fun headMovedDuringPlaybackExtendsTargetAndPauseScrubAndExitRemainAvailable() {
        val seen = append(GameplayTree(F.root()), 100)
        val next = append(seen, 101, F.b)
        val player = HistoryPlayback(index(next))
        player.openFrom(seen.currentNodeId); player.play()
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
        assertFalse(history.playback!!.position.playing)
        history.playback!!.play(); history.playback!!.next(true)
        val state = head.stateAt(head.currentNodeId)!!
        history.playback!!.finishAnimation(state.currentNodeId)
        history.presented(state, state, true)
        assertEquals(listOf(state.gameId to state.currentNodeId), saved)
        history.select(seen.currentNodeId)
        history.close()
        assertEquals(listOf(state.gameId to state.currentNodeId, state.gameId to state.currentNodeId), saved)
    }

    @Test fun branchArrivingWhilePausedIsDetectedAtOnceAndScrubbingEndsIt() {
        val common = append(GameplayTree(F.root()), 100)
        val old1 = append(common, 101, F.b)
        val old2 = append(old1, 102)
        val player = HistoryPlayback(index(old2))
        player.openFrom(old1.currentNodeId)
        assertFalse(player.branched)
        val branch = F.change(old2, F.command(old2, GameAction.Play(PlayAction.AdjustLife(F.b, "2")), 103)
            .copy(continuation = Continuation(common.currentNodeId, true)), F.b)
        player.receive(index(branch))
        assertEquals(old1.currentNodeId, player.position.cursorId)
        assertFalse(player.position.playing)
        assertTrue(player.branched)
        val head = append(branch, 104)
        player.receive(index(head)) // Re-planned against the newer head, still pending.
        assertTrue(player.branched)
        player.scrub(old1.currentNodeId)
        assertFalse(player.branched)
        player.receive(index(append(head, 105))) // A plain scrub onto the old line is not re-flagged.
        assertFalse(player.branched)
    }

    @Test fun resumeAfterOpponentBranchedWhileAwayPausesAtLastSeenWithBranchPending() {
        val common = append(GameplayTree(F.root()), 100)
        val seen = append(common, 101)
        val player = HistoryPlayback(index(seen))
        player.background()
        val branch = F.change(seen, F.command(seen, GameAction.Play(PlayAction.AdjustLife(F.b, "2")), 102)
            .copy(continuation = Continuation(common.currentNodeId, true)), F.b)
        player.receive(index(branch))
        player.foreground(); player.caughtUp()
        assertEquals(HistoryMode.PLAYBACK, player.position.mode)
        assertEquals(seen.currentNodeId, player.position.cursorId)
        assertFalse(player.position.playing)
        assertTrue(player.branched)
    }

    @Test fun controllerOpensPausedAtLastSeenAndReportsAPendingBranch() {
        val common = append(GameplayTree(F.root()), 100)
        val old = append(common, 101)
        val branch = F.change(old, F.command(old, GameAction.Play(PlayAction.AdjustLife(F.b, "2")), 102)
            .copy(continuation = Continuation(common.currentNodeId, true)), F.b)
        val history = TableHistoryController(Executor { it.run() }, { it() }, { _, _ -> false }, { _, _, _ -> false })
        history.openFrom(old.currentNodeId) { _, _ -> }
        history.update(branch, "head")
        val player = assertNotNull(history.playback)
        assertEquals(HistoryMode.PLAYBACK, player.position.mode)
        assertEquals(old.currentNodeId, player.position.cursorId)
        assertFalse(player.position.playing)
        assertTrue(player.branched)
        history.current() // The Auto action: rewind, then play on.
        assertTrue(player.position.playing)
        assertTrue(player.transportLocked)
        history.close()
    }
}
