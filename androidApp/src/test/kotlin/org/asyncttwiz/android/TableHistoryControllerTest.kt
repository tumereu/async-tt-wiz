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

    @Test fun continuationRequestPausesButOnlyConfirmationArmsTheCapturedBoundary() {
        Harness().use { h ->
            val tree = f.tap(f.root())
            h.history.update(tree, "v1"); h.publish()
            val player = h.history.playback!!
            val root = tree.initial.root.id
            h.history.select(root)
            h.history.displayPosition()
            h.reviews.removeFirst()(DownloadedHistoryState(1L, root, state = tree.initial))
            player.play()
            assertTrue(h.history.canRequestContinuation(root))
            assertTrue(h.history.requestContinuation(root))
            assertFalse(player.position.playing)
            assertNull(h.history.armed)
            assertEquals(root, h.history.continuationTarget)
            h.history.dismissContinuation()
            assertNull(h.history.armed)
            assertTrue(h.history.requestContinuation(root))
            assertTrue(h.history.confirmContinuation())
            assertEquals(root, h.history.armed)
            assertEquals(tree.currentNodeId, player.tree.sharedHeadId)
            assertEquals(1, player.tree.nodes.size)
        }
    }

    @Test fun changedCursorOrBackgroundCannotConfirmAnOldContinuation() {
        for (background in listOf(false, true)) Harness().use { h ->
            val tree = f.tap(f.root())
            h.history.update(tree, "v1"); h.publish()
            val root = tree.initial.root.id
            h.history.select(root); h.history.displayPosition()
            h.reviews.removeFirst()(DownloadedHistoryState(1L, root, state = tree.initial))
            assertTrue(h.history.requestContinuation(root))
            if (background) h.history.updateForeground(false) else h.history.playback!!.scrub(tree.currentNodeId)
            assertFalse(h.history.confirmContinuation())
            assertNull(h.history.armed)
            assertNull(h.history.continuationTarget)
        }
    }

    @Test fun returnCurrentStartsCatchupRatherThanJumpingTheReviewCursor() {
        Harness().use { h ->
            val tree = f.tap(f.root())
            h.history.update(tree, "v1"); h.publish()
            h.history.select(tree.initial.root.id)
            h.history.current()
            assertEquals(tree.initial.root.id, h.history.playback!!.position.cursorId)
            assertTrue(h.history.playback!!.position.playing)
            h.history.playback!!.next(automatic = true)
            assertEquals(tree.currentNodeId, h.history.playback!!.position.cursorId)
            assertEquals(HistoryMode.LIVE, h.history.playback!!.position.mode)
        }
    }

    @Test fun publishedTimelineResolvesAuthorNamesWithoutExposingCoveredCards() {
        Harness().use { h ->
            val tree = f.tap(f.root())
            val game = GameSummary(tree.initial.gameId, "game", "now", AccountIdentity(f.a, "alice"),
                AccountIdentity(f.b, "bob"), tree.initial.preparationRound, f.id(500), InvitationState.ACCEPTED)
            h.history.update(tree, "v1", game, f.a); h.publish()
            val timeline = HistoryTimelineLayout.events(h.history.playback!!.tree, tree.currentNodeId)
            assertEquals("alice tapped a card", timeline.last().description)
            assertEquals("Tap", timeline.last().label)
            assertEquals(mapOf(f.a to "alice", f.b to "bob"), h.history.usernames)
            assertTrue(timeline.none { it.description.contains("00000000-") })
        }
    }

    @Test fun workerIndexIncludesUsernamesAndRefreshesWhenDisplayContextChanges() {
        Harness().use { h ->
            val tree = f.tap(f.root())
            val game = GameSummary(tree.initial.gameId, "Table", "2026-10-23T00:00:00Z",
                AccountIdentity(f.a, "alice"), AccountIdentity(f.b, "bob"), tree.initial.preparationRound,
                f.id(90), InvitationState.ACCEPTED)
            h.history.update(tree, "v1", game, f.a); h.publish()
            assertEquals("alice tapped a card", h.history.playback!!.tree.texts.getValue(tree.currentNodeId).description)
            h.history.update(tree, "v1", game.copy(inviter = AccountIdentity(f.a, "alicia")), f.a); h.publish()
            assertEquals("alicia tapped a card", h.history.playback!!.tree.texts.getValue(tree.currentNodeId).description)
        }
    }

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
