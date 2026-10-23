package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import java.util.concurrent.Executor
import kotlin.test.*

class TableHistoryControllerTest {
    private val f = DownloadedTreeFixture

    private class Harness : AutoCloseable {
        val work = ArrayDeque<Runnable>()
        val posts = ArrayDeque<() -> Unit>()
        val reviews = ArrayDeque<(DownloadedHistoryState) -> Unit>()
        val history = TableHistoryController(Executor { work.addLast(it) }, { posts.addLast(it) }, { _, complete ->
            reviews.addLast(complete); true
        }, { _, _, _ -> error("Review must not submit gameplay") }, { error("Review must not send controls") })

        fun publish() { work.removeFirst().run(); posts.removeFirst().invoke() }
        override fun close() = history.close()
    }

    private fun view(tree: GameplayTree) = DownloadedHistoryState(1L, tree.currentNodeId,
        state = tree.stateAt(tree.currentNodeId))

    @Test fun reviewFromNewerSessionTreeIsIgnoredUntilPlaybackIndexCatchesUp() {
        Harness().use { h ->
            val root = f.root()
            val newer = f.tap(root)
            h.history.update(root, "root"); h.publish()
            h.history.displayPosition()

            // GameSession has advanced, but the playback index is still the captured old index.
            h.reviews.removeFirst()(view(newer))
            assertNull(h.history.view)
            assertEquals(root.currentNodeId, h.history.playback!!.position.cursorId)
            assertEquals(HistoryMode.LIVE, h.history.playback!!.position.mode)

            h.history.update(newer, "newer"); h.publish()
            h.history.displayPosition(); h.reviews.removeFirst()(view(newer))
            assertEquals(newer.currentNodeId, h.history.view!!.cursor)
            assertEquals(HistoryMode.LIVE, h.history.playback!!.position.mode)
        }
    }

    @Test fun pendingReviewIsFencedAsSoonAsReplacementIndexIsRequested() {
        Harness().use { h ->
            val root = f.root()
            val removed = f.tap(root)
            h.history.update(root, "root"); h.publish()
            h.history.displayPosition()
            val pending = h.reviews.removeFirst()
            h.history.update(f.tap(root, 101, f.b, 12), "replacement")

            // The result is absent from both the old index and the queued replacement.
            pending(view(removed))
            assertNull(h.history.view)
            h.history.displayPosition()
            assertTrue(h.reviews.isEmpty()) // Do not review against an index still being replaced.
            h.publish()
            assertEquals(HistoryMode.LIVE, h.history.playback!!.position.mode)
        }
    }

    @Test fun treeReplacedWhileReviewPendingDoesNotInstallRemovedCursorOrBoard() {
        Harness().use { h ->
            val original = f.tap(f.root())
            val replacement = f.undo(original)
            h.history.update(original, "original"); h.publish()
            h.history.displayPosition()
            val pending = h.reviews.removeFirst()
            h.history.update(replacement, "replacement"); h.publish()
            assertFalse(h.history.playback!!.tree.contains(original.currentNodeId))

            pending(view(original))
            assertNull(h.history.view)
            assertEquals(replacement.currentNodeId, h.history.playback!!.position.cursorId)
            h.history.displayPosition(); h.reviews.removeFirst()(view(replacement))
            assertEquals(replacement.currentNodeId, h.history.view!!.cursor)
            assertEquals(replacement.initial.board, h.history.view!!.state!!.board)
            assertEquals(HistoryMode.LIVE, h.history.playback!!.position.mode)
        }
    }
}
