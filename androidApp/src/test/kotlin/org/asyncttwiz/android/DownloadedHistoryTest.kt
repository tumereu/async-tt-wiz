package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import kotlin.test.*
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class DownloadedHistoryTest {
    @Test fun lateSelectionWithoutAcceptedHistoryOrMissingBoundaryIsIgnoredAndPublishWithoutStageRequestsRetry() {
        Harness(TreeStoreFixture()).use { h ->
            h.history.load(); h.settle()
            val previous = h.view
            h.history.select(f.id(999)); h.settle()
            assertEquals(previous, h.view)
            h.history.publish(emptyList(), 10); h.settle()
            assertTrue(h.view.downloadIncomplete)
            assertNull(h.view.error)
            h.store.seed(f.generation(f.root()))
            h.history.load(); h.settle()
            val loaded = h.view
            h.history.select(f.id(999)); h.settle()
            assertEquals(loaded, h.view)
            assertNull(h.store.reviewCursor())
        }
    }
    private val f = DownloadedTreeFixture
    private class Harness(val store: TreeStoreFixture) : AutoCloseable {
        val uiThread: Thread = Thread.currentThread()
        val worker = Executors.newSingleThreadExecutor()
        val posts = ConcurrentLinkedQueue<() -> Unit>()
        val views = mutableListOf<DownloadedHistoryState>()
        var authorized = true
        val history = DownloadedHistory(store, { posts.add(it) }, { authorized }, {
            assertSame(uiThread, Thread.currentThread()); views.add(it)
        }, worker, uiThread)
        fun settle(display: Boolean = true) {
            worker.submit {}.get(5, TimeUnit.SECONDS)
            if (display) while (true) (posts.poll() ?: break).invoke()
        }
        val view get() = views.last()
        override fun close() { history.close(); assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS)) }
    }

    @Test fun completeOfflineReviewReplaysAllRetainedStatesWithoutNetworkCommandsOrLoggedInspection() {
        val generation = f.branched()
        Harness(TreeStoreFixture().apply { seed(generation) }).use { h ->
            h.history.load(); h.settle()
            assertTrue(h.view.hasCompleteGeneration)
            assertTrue(h.view.readOnly)
            for (node in generation.tree.nodes.keys + generation.tree.initial.root.id) {
                h.history.select(node); h.settle()
                assertEquals(node, h.view.cursor)
                assertTrue(h.view.reviewing)
                assertEquals(generation.tree.stateAt(node), h.view.state)
            }
            assertNotNull(h.view.holdFace(f.a, f.id(9)))
            assertNull(h.view.holdFace(f.a, f.id(12)), "opponent hidden hold must not expose cached faces")
            assertTrue(h.store.threads.all { it !== h.uiThread })
            assertEquals(10L, h.store.saved!!.version.revision)
            assertEquals(generation.tree.currentNodeId, TreeSync.decode(h.store.saved!!.records.asSequence()
                .filter { it.key.kind != "tree-cache-endpoint" }.map(TreeDownload::decode),
                f.id(1), f.id(2)).tree.currentNodeId)
            assertNull(h.store.transfer(), "offline traversal has no fetch or inspection journal")
        }
    }

    @Test fun deletedReviewCursorFallsBackButSurvivingPausedCursorStaysAtSameRevisedBoundary() {
        val first = f.tap(f.root())
        val second = f.tap(first, 101, f.b, 12)
        val before = f.generation(second)
        val after = f.generation(f.undo(second))
        for (selected in listOf(first.currentNodeId, second.currentNodeId)) {
            Harness(TreeStoreFixture().apply { seed(before) }).use { h ->
                h.history.load(); h.history.select(selected); h.settle()
                h.history.apply(f.delta(before, after)); h.settle()
                val expected = if (selected == first.currentNodeId) first.initial.root.id else second.currentNodeId
                assertEquals(expected, h.view.cursor)
                assertEquals(expected, h.store.reviewCursor())
                assertTrue(h.view.reviewing)
                assertEquals(after.tree.stateAt(expected), h.view.state)
                assertFalse(h.view.state!!.board.cards.first().tapped, "surviving node must not keep obsolete same-ID board cache")
                assertEquals(11L, h.view.revision)
            }
        }
    }

    @Test fun multipleRemovedAncestorsAndFullRecoveryPreservePersonalReviewInsteadOfFollowingSharedHead() {
        val first = f.tap(f.root())
        val second = f.tap(first, 101, f.b, 12)
        val third = f.tap(second, 102, f.a, 9, false)
        val before = f.generation(third)
        val removedThird = f.undo(third)
        val afterTree = f.undo(removedThird)
        val after = f.generation(afterTree)
        val transfer = f.transfer(after, 15)
        Harness(TreeStoreFixture().apply { seed(before) }).use { h ->
            h.history.load(); h.history.select(third.currentNodeId); h.settle()
            h.history.stage(transfer.plan) { transfer.compressed[it].inputStream() }; h.settle()
            assertTrue(h.view.downloadIncomplete)
            assertEquals(10L, h.view.revision)
            assertEquals(third.currentNodeId, h.view.cursor)
            h.history.publish(emptyList(), 15); h.settle()
            assertEquals(second.currentNodeId, h.view.cursor, "nearest surviving parent, not root/shared-current default")
            assertEquals(afterTree.stateAt(second.currentNodeId), h.view.state)
            assertTrue(h.view.reviewing)
            assertFalse(h.view.downloadIncomplete)
            assertEquals(15L, h.view.revision)
        }
    }

    @Test fun sharedPointerSwitchAndNewOffshootDoNotMoveSurvivingPausedReview() {
        val before = f.branched()
        val selected = before.tree.currentNodeId
        val offshoot = before.tree.nodes.values.single { it.branchId == f.id(30) }
        val switched = f.generation(before.tree.copy(currentNodeId = offshoot.id))
        Harness(TreeStoreFixture().apply { seed(before) }).use { h ->
            h.history.load(); h.history.select(selected); h.settle()
            h.history.apply(f.delta(before, switched)); h.settle()
            assertEquals(selected, h.view.cursor)
            assertEquals(before.tree.stateAt(selected), h.view.state)
            h.history.followCurrent(); h.settle()
            assertEquals(offshoot.id, h.view.cursor)
            assertFalse(h.view.reviewing)
            assertNull(h.store.reviewCursor())
        }
    }

    @Test fun failedInterruptedDownloadKeepsCompleteOfflineReviewAndShowsExplicitIncompleteStatus() {
        val before = f.generation(f.root())
        val transfer = f.transfer(f.branched(), 11)
        Harness(TreeStoreFixture().apply { seed(before) }).use { h ->
            h.history.load(); h.settle()
            h.history.stage(transfer.plan) { index -> if (index == 1) throw java.io.IOException("interrupted") else transfer.compressed[index].inputStream() }
            h.settle()
            assertTrue(h.view.downloadIncomplete)
            assertTrue(h.view.hasCompleteGeneration)
            assertEquals(10L, h.view.revision)
            assertEquals(before.tree.initial, h.view.state)
            assertNotNull(h.view.error)
            h.history.select(before.tree.initial.root.id); h.settle()
            assertNotNull(h.view.state)
        }
    }

    @Test fun restoredReviewAndQueuedUiCallbacksRespectAccountIncarnationAndCloseFences() {
        val before = f.branched()
        val store = TreeStoreFixture().apply { seed(before); reviewCursor(before.tree.nodes.keys.last()) }
        Harness(store).use { h ->
            h.history.load(); h.settle(false)
            assertTrue(h.views.isEmpty())
            h.authorized = false
            while (true) (h.posts.poll() ?: break).invoke()
            assertTrue(h.views.isEmpty(), "known account/incarnation deletion change blocks queued display")
            h.authorized = true
            h.history.load(); h.settle()
            assertTrue(h.view.reviewing)
            assertEquals(before.tree.nodes.keys.last(), h.view.cursor)
            h.history.select(before.tree.initial.root.id); h.settle(false)
            val count = h.views.size
            h.history.close()
            while (true) (h.posts.poll() ?: break).invoke()
            assertEquals(count, h.views.size)
            assertFalse(store.current)
        }
    }
}
