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
        }, { _, _, _ -> error("Review must not submit gameplay") })

        fun publish() { work.removeFirst().run(); posts.removeFirst().invoke() }
        override fun close() = history.close()
    }

    private fun view(tree: GameplayTree) = DownloadedHistoryState(1L, tree.currentNodeId,
        state = tree.stateAt(tree.currentNodeId))

    @Test fun livePanelRevealRequestsHistoricalFaceUpEndpointInsteadOfLatestMove() {
        val fixture = BasicTableFixture()
        fixture.connect()
        try {
            assertTrue(fixture.lobby.gameplay(PlayAction.ShowContents(fixture.container(PlayZone.LIBRARY), ContentsSelection.TOP, 2)))
            val opened = fixture.lobby.displayTree()!!.materializedCopy()
            val panel = fixture.play.board.contentsDisplays.single()
            val card = panel.cardIds.first()
            val toolkit = TableToolkitController(fixture.actor, { fixture.lobby.state.play }, { true }, fixture.lobby::gameplay, playBatch = fixture.lobby::gameplayBatch)
            toolkit.cardPanelAction(panel, card, PlayZone.HAND, reveal = true)
            val onward = fixture.lobby.displayTree()!!.materializedCopy()
            val reveal = onward.nodes.values.single { it.action is PlayAction.RevealContentsCard }.id
            val requests = mutableListOf<String?>()
            val history = TableHistoryController(Executor { it.run() }, { it() }, { node, complete ->
                requests += node
                val cursor = node ?: onward.currentNodeId
                complete(DownloadedHistoryState(1L, cursor, state = onward.stateAt(cursor))); true
            }, { _, _, _ -> error("Presentation must not submit gameplay") })
            history.use {
                history.update(opened, "open", viewer = fixture.other)
                history.update(onward, "onward", viewer = fixture.other)
                history.displayPosition()
                assertEquals(listOf<String?>(reveal), requests)
                val shown = history.view!!.state!!
                assertEquals(reveal, shown.currentNodeId)
                assertTrue(shown.board.contentsDisplays.single().faceVisibleTo(card, fixture.other))
                assertEquals(HistoryMode.LIVE, history.playback!!.position.mode)
                val endpoint = TableEndpointPresentation()
                endpoint.presented(opened.stateAt(opened.currentNodeId))
                assertEquals(opened.currentNodeId, endpoint.live(fixture.play, fixture.play, false, reveal)!!.currentNodeId)
                assertEquals(reveal, endpoint.live(fixture.play, shown, false, reveal)!!.currentNodeId)
                history.playback!!.finishPanelReveal()
                history.displayPosition()
                assertNull(requests.last())
                assertEquals(onward.currentNodeId, history.view!!.state!!.currentNodeId)
            }
        } finally { fixture.lobby.close(); fixture.worker.drain() }
    }

    @Test fun continueAtCurrentHeadReturnsLiveWithoutConfirmationOrArming() {
        Harness().use { h ->
            val tree = f.tap(f.root())
            h.history.update(tree, "v1"); h.publish()
            h.history.select(tree.currentNodeId); h.history.displayPosition()
            h.reviews.removeFirst()(view(tree))
            assertTrue(h.history.requestContinuation(tree.currentNodeId))
            assertEquals(HistoryMode.LIVE, h.history.playback!!.position.mode)
            assertNull(h.history.continuationTarget)
            assertNull(h.history.armed)
            assertFalse(h.history.provisional)
        }
    }

    @Test fun staleHeadArmsItsExactBoundaryAfterLivePlayAdvances() {
        Harness().use { h ->
            val root = f.root()
            h.history.update(root, "v1"); h.publish()
            h.history.select(root.currentNodeId); h.history.displayPosition()
            h.reviews.removeFirst()(view(root))
            val next = f.tap(root)
            h.history.update(next, "v2"); h.publish()
            h.history.displayPosition(); h.reviews.removeFirst()(view(root))
            assertTrue(h.history.requestContinuation(root.currentNodeId))
            assertNull(h.history.continuationTarget)
            assertEquals(root.currentNodeId, h.history.armed)
            assertEquals(HistoryMode.PLAYBACK, h.history.playback!!.position.mode)
        }
    }

    @Test fun predictedUndoRedoMoveLiveHistoryPositionWithoutPublishingAcceptedState() {
        val data = GameplayFixture()
        val replica = GameplayReplica()
        assertTrue(replica.root(data.head()))
        Harness().use { h ->
            val actions = listOf(GameAction.Play(PlayAction.AdjustLife(data.actor, "1")), GameAction.Undo,
                GameAction.Play(PlayAction.AdjustLife(data.actor, "2")), GameAction.Undo, GameAction.Redo)
            actions.forEachIndexed { index, action ->
                val command = data.command(PlayAction.AdjustLife(data.actor, "1")).copy(action = action)
                assertTrue(replica.predict(command, data.actor))
                h.history.update(replica.displayTree!!, "prediction-$index"); h.publish()
                assertEquals(HistoryMode.LIVE, h.history.playback!!.position.mode)
                assertEquals(replica.display!!.currentNodeId, h.history.playback!!.position.cursorId)
                assertEquals(data.initial, replica.accepted)
            }
        }
    }

    @Test fun continuationRequestPausesAndImmediatelyArmsTheCapturedBoundary() {
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
            assertNull(h.history.continuationTarget)
            assertEquals(root, h.history.armed)
            assertEquals(tree.currentNodeId, player.tree.sharedHeadId)
            assertEquals(1, player.tree.nodes.size)
        }
    }

    @Test fun returnFromBackgroundDecidesLiveOrTimelineOnlyOnceTheCaughtUpTreeIsIndexed() {
        for (missed in listOf(false, true)) Harness().use { h ->
            val first = f.tap(f.root())
            h.history.update(first, "v1"); h.publish()
            val shown = first.currentNodeId
            h.history.updateForeground(false)
            assertEquals(HistoryMode.PLAYBACK, h.history.playback!!.position.mode)
            h.history.updateForeground(true)
            assertTrue(h.history.resumeHeld)
            val caught = if (missed) f.tap(first, n = 101, actor = f.b, card = 12) else first
            if (missed) {
                h.history.update(caught, "v2")
                h.history.resumeCaughtUp(caught) // Not indexed yet: must not decide on the old tree.
                assertTrue(h.history.resumeHeld)
                h.publish()
            }
            h.history.resumeCaughtUp(f.root()) // A tree the table has not been given is ignored.
            assertEquals(HistoryMode.PLAYBACK, h.history.playback!!.position.mode)
            h.history.resumeCaughtUp(caught)
            val position = h.history.playback!!.position
            if (missed) assertEquals(Triple(shown, HistoryMode.PLAYBACK, false), Triple(position.cursorId, position.mode, position.playing))
            else assertEquals(HistoryPosition(shown, displayVersion = "v1"), position)
            assertFalse(h.history.resumeHeld)
        }
    }

    @Test fun changedCursorOrBackgroundCannotConfirmAnOldContinuation() {
        for (background in listOf(false, true)) Harness().use { h ->
            val tree = f.tap(f.root())
            h.history.update(tree, "v1"); h.publish()
            val root = tree.initial.root.id
            h.history.select(root); h.history.displayPosition()
            h.reviews.removeFirst()(DownloadedHistoryState(1L, root, state = tree.initial))
            h.history.requestExitReview()
            assertEquals(root, h.history.continuationTarget)
            if (background) h.history.updateForeground(false) else h.history.playback!!.scrub(tree.currentNodeId)
            assertFalse(h.history.confirmContinuation())
            assertNull(h.history.armed)
            assertNull(h.history.continuationTarget)
        }
    }

    @Test fun toggleOffAwayFromHeadOffersContinueBackToLatestOrDismissWithoutMutation() {
        for (choice in listOf("continue", "latest", "dismiss")) Harness().use { h ->
            val tree = f.tap(f.root())
            h.history.update(tree, "v1"); h.publish()
            val player = h.history.playback!!
            val root = tree.initial.root.id
            h.history.select(root); h.history.displayPosition()
            h.reviews.removeFirst()(DownloadedHistoryState(1L, root, state = tree.initial))
            player.play()
            h.history.requestExitReview()
            assertEquals(root, h.history.continuationTarget)
            assertNull(h.history.armed)
            assertFalse(player.position.playing)
            when (choice) {
                "continue" -> {
                    assertTrue(h.history.confirmContinuation())
                    assertEquals(root, h.history.armed)
                    assertFalse(player.position.playing)
                }
                "latest" -> {
                    h.history.current()
                    assertNull(h.history.armed)
                    assertTrue(player.position.playing)
                }
                else -> {
                    h.history.dismissContinuation()
                    assertNull(h.history.armed)
                    assertFalse(player.position.playing)
                    assertEquals(HistoryMode.PLAYBACK, player.position.mode)
                }
            }
            assertNull(h.history.continuationTarget)
            assertEquals(root, player.position.cursorId)
            assertEquals(tree.currentNodeId, player.tree.sharedHeadId)
        }
    }

    @Test fun toggleOffAtHeadLeavesReviewWithoutConfirmation() {
        Harness().use { h ->
            val tree = f.tap(f.root())
            h.history.update(tree, "v1"); h.publish()
            h.history.select(tree.currentNodeId)
            h.history.requestExitReview()
            assertNull(h.history.continuationTarget)
            assertNull(h.history.armed)
            assertEquals(HistoryMode.LIVE, h.history.playback!!.position.mode)
        }
    }

    @Test fun toggleOffWhenContinuationUnavailableSkipsConfirmationAndCatchesUp() {
        for (unavailable in listOf("not downloaded", "background", "disconnected")) Harness().use { h ->
            val tree = f.tap(f.root())
            h.history.update(tree, "v1"); h.publish()
            h.history.select(tree.initial.root.id)
            if (unavailable != "not downloaded") {
                h.history.displayPosition()
                h.reviews.removeFirst()(DownloadedHistoryState(1L, tree.initial.root.id, state = tree.initial))
            }
            if (unavailable == "background") h.history.updateForeground(false)
            h.history.requestExitReview(allowContinuation = unavailable != "disconnected")
            assertNull(h.history.continuationTarget)
            assertNull(h.history.armed)
            assertTrue(h.history.playback!!.position.playing)
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
            h.history.playback!!.finishAnimation(tree.currentNodeId)
            assertEquals(HistoryMode.LIVE, h.history.playback!!.position.mode)
        }
    }

    @Test fun cancellingArmedContinuationReturnsToLiveWithoutSendingRequests() {
        for (viaReturnCurrent in listOf(false, true)) Harness().use { h ->
            val tree = f.tap(f.root())
            h.history.update(tree, "v1"); h.publish()
            val player = h.history.playback!!
            val root = tree.initial.root.id
            h.history.select(root); h.history.displayPosition()
            h.reviews.removeFirst()(DownloadedHistoryState(1L, root, state = tree.initial))
            assertTrue(h.history.requestContinuation(root))
            if (viaReturnCurrent) h.history.current() else h.history.cancelContinuation()
            assertNull(h.history.armed)
            assertNull(h.history.continuationTarget)
            assertFalse(h.history.provisional)
            assertEquals(tree.currentNodeId, player.position.cursorId)
            assertEquals(HistoryMode.LIVE, player.position.mode)
            assertFalse(player.position.playing)
            assertEquals(1, player.tree.nodes.size)
        }
    }

    @Test fun publishedTimelineResolvesAuthorNamesWithoutExposingCoveredCards() {
        Harness().use { h ->
            val tree = f.tap(f.root())
            val game = GameSummary(tree.initial.gameId, "game", "now", AccountIdentity(f.a, "alice"),
                AccountIdentity(f.b, "bob"), tree.initial.preparationRound, f.id(500), InvitationState.ACCEPTED)
            h.history.update(tree, "v1", game, f.a); h.publish()
            val timeline = HistoryTimelineLayout.events(h.history.playback!!.tree, tree.currentNodeId)
            assertEquals("A card tapped", timeline.last().description)
            assertEquals("Tap", timeline.last().label)
            assertEquals(mapOf(f.a to "alice", f.b to "bob"), h.history.usernames)
            assertTrue(timeline.none { it.description.contains("00000000-") })
        }
    }

    @Test fun workerIndexIncludesUsernamesAndRefreshesWhenDisplayContextChanges() {
        Harness().use { h ->
            val root = f.root()
            val tree = assertNotNull(root.play(f.b, GameCommand(f.id(100), GameScope(root.initial.gameId, playId = root.initial.id),
                GameAction.Play(PlayAction.SetLife(f.a, "17")), root.initial.id, "0")).tree)
            val game = GameSummary(tree.initial.gameId, "Table", "2026-10-23T00:00:00Z",
                AccountIdentity(f.a, "alice"), AccountIdentity(f.b, "bob"), tree.initial.preparationRound,
                f.id(90), InvitationState.ACCEPTED)
            h.history.update(tree, "v1", game, f.a); h.publish()
            assertEquals("alice's life 20 → 17", h.history.playback!!.tree.texts.getValue(tree.currentNodeId).description)
            h.history.update(tree, "v1", game.copy(inviter = AccountIdentity(f.a, "alicia")), f.a); h.publish()
            assertEquals("alicia's life 20 → 17", h.history.playback!!.tree.texts.getValue(tree.currentNodeId).description)
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
