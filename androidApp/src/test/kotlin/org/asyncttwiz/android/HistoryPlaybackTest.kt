package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

class HistoryPlaybackTest {
    @Test fun holdInspectPauseAtHeadAfterCatchupOrBranchNeverEntersTimeline() {
        val a = node(1, 0); val b = node(2, 1)
        val player = HistoryPlayback(tree(listOf(a, b)))
        val scene = TableFixture.scene()
        val card = scene.items.first { it.kind == TableItemKind.CARD && it.zone == PlayZone.BATTLEFIELD }
        val gestures = TableGestureCoordinator({ if (it is TableIntent.Peek && it.cardId != null) player.pause() })
        gestures.update(scene, TableSelection(), true)
        player.openFrom(id(0))
        while (player.position.mode == HistoryMode.PLAYBACK) { player.next(true); player.finishAnimation(player.position.cursorId) }
        gestures.down(card.bounds.center, 0); gestures.tick(400)
        assertTrue(gestures.peeking)
        assertEquals(HistoryMode.LIVE, player.position.mode)
        gestures.up(card.bounds.center, 420)
        player.scrub(id(1)); player.continuationApplied()
        gestures.down(card.bounds.center, 1000); gestures.tick(1400)
        assertEquals(HistoryMode.LIVE, player.position.mode)
        player.play() // A stale transport callback cannot reactivate review either.
        assertEquals(HistoryMode.LIVE, player.position.mode)
        assertFalse(player.position.playing)
    }

    @Test fun remoteActionWhileLiveIncludingInspectionAndReconnectStaysLive() {
        val a = node(1, 0); val b = node(2, 1)
        val player = HistoryPlayback(tree(listOf(a)))
        player.pause()
        player.receive(tree(listOf(a, b)), id(71))
        player.foreground() // Reconnect while already open is not a resume.
        assertEquals(HistoryMode.LIVE, player.position.mode)
        assertEquals(id(2), player.position.cursorId)
        assertEquals("v2", player.position.displayVersion)
        // Remote undo/branch removal must not turn a live cursor into personal review.
        player.receive(tree(listOf(a)), id(71))
        assertEquals(HistoryMode.LIVE, player.position.mode)
        assertEquals(id(1), player.position.cursorId)
    }

    @Test fun resumeAfterRevisionBumpWithoutNewNodeStaysLive() {
        val a = node(1, 0); val b = node(2, 1)
        val player = HistoryPlayback(tree(listOf(a)))
        player.background()
        player.receive(tree(listOf(a), version = "v1-chat"))
        player.foreground(); player.caughtUp()
        assertEquals(HistoryMode.LIVE, player.position.mode)
        assertEquals("v1-chat", player.position.displayVersion)
        player.background()
        player.receive(tree(listOf(a, b)))
        player.foreground(); player.caughtUp() // An unseen node still opens timeline mode on resume.
        assertEquals(HistoryMode.PLAYBACK, player.position.mode)
        assertEquals(id(1), player.position.cursorId)
    }

    @Test fun leavingPausesLiveSoEventsArrivingAfterReturnStayInTimeline() {
        val a = node(1, 0); val b = node(2, 1)
        val player = HistoryPlayback(tree(listOf(a)))
        player.background()
        assertEquals(HistoryPosition(id(1), HistoryMode.PLAYBACK, false, "v1"), player.position)
        assertTrue(player.resumeHeld)
        player.foreground() // Returning alone decides nothing: the catch-up has not arrived yet.
        assertEquals(HistoryMode.PLAYBACK, player.position.mode)
        assertTrue(player.resumeHeld, "Still presented as live at an unchanged head")
        player.receive(tree(listOf(a, b))) // Reconnect catch-up delivered after ON_START.
        assertEquals(id(1), player.position.cursorId)
        assertFalse(player.resumeHeld)
        player.caughtUp()
        assertEquals(Triple(id(1), HistoryMode.PLAYBACK, false), player.position.let { Triple(it.cursorId, it.mode, it.playing) })
        player.caughtUp(); player.foreground()
        assertEquals(HistoryMode.PLAYBACK, player.position.mode, "The decision is made once")
    }

    @Test fun returnWithoutNewNodesGoesLiveSilentlyOnlyOnceCaughtUpInForeground() {
        val a = node(1, 0); val b = node(2, 1)
        val player = HistoryPlayback(tree(listOf(a)))
        player.background()
        player.caughtUp() // Still away: no decision.
        assertEquals(HistoryMode.PLAYBACK, player.position.mode)
        player.foreground(); player.caughtUp()
        assertEquals(HistoryPosition(id(1), displayVersion = "v1"), player.position)
        assertFalse(player.resumeHold)
        player.receive(tree(listOf(a, b)))
        assertEquals(HistoryMode.LIVE, player.position.mode)
        assertEquals(id(2), player.position.cursorId)
    }

    @Test fun playerTimelineIsNotAReturnPauseAndPlayerActionsEndThePause() {
        val a = node(1, 0); val b = node(2, 1)
        val reviewing = HistoryPlayback(tree(listOf(a)))
        reviewing.scrub(id(0)); reviewing.background(); reviewing.foreground()
        assertFalse(reviewing.resumeHold)
        reviewing.receive(tree(listOf(a, b))); reviewing.scrub(id(2)); reviewing.caughtUp()
        assertEquals(HistoryMode.PLAYBACK, reviewing.position.mode, "Only the player leaves their own timeline")
        val held = HistoryPlayback(tree(listOf(a)))
        held.background(); held.foreground()
        held.scrub(id(0))
        assertFalse(held.resumeHold)
        held.scrub(id(1)); held.caughtUp()
        assertEquals(HistoryMode.PLAYBACK, held.position.mode)
    }

    private fun id(n: Int) = "00000000-0000-4000-8000-${n.toString().padStart(12, '0')}"
    private fun node(n: Int, parent: Int, kind: HistorySignificance = HistorySignificance.CROSS_ZONE_MOVE, key: String = "a"): GameplayMutation {
        val semantic = HistorySemantic(kind, "icon-$kind", if (kind.category in setOf(HistoryCategory.M, HistoryCategory.H)) key else null)
        return GameplayMutation(id(n), id(parent), id(50), id(n + 100), id(70), null,
            PlayAction.SetLife(id(70), n.toString()), GameplayTransition(), grouping = semantic)
    }
    private fun tree(nodes: List<GameplayMutation>, head: Int = nodes.lastOrNull()?.id?.takeLast(12)?.toInt() ?: 0,
        version: String = "v$head", versions: Map<String, String> = emptyMap()) = HistoryTree(id(0), nodes, id(head), version, versions)

    @Test fun liveImmediateBackgroundProgressAndPausedCursorWhileReceiptContinues() {
        val a = node(1,0); val b = node(2,1); val c = node(3,2, HistorySignificance.ORIENTATION)
        val playback = HistoryPlayback(tree(listOf(a)))
        playback.receive(tree(listOf(a,b)))
        assertEquals(id(2), playback.position.cursorId) // No group closure wait.
        playback.background()
        playback.receive(tree(listOf(a,b,c)))
        assertEquals(id(2), playback.position.cursorId)
        assertEquals(id(3), playback.tree.sharedHeadId) // Background receipt already synchronized.
        playback.foreground()
        assertEquals(HistoryMode.PLAYBACK, playback.position.mode)
        playback.scrub(id(1))
        val d = node(4,3)
        playback.receive(tree(listOf(a,b,c,d)))
        assertEquals(id(1), playback.position.cursorId)
        playback.next()
        assertEquals(id(2), playback.position.cursorId) // Rest of same open group, exact cursor retained until stepping.
        playback.play(); playback.next(automatic = true)
        assertEquals(id(4), playback.position.cursorId) // Tap plus next substantive action is one group.
        playback.finishAnimation(id(4))
        assertEquals(HistoryMode.LIVE, playback.position.mode)
        val e = node(5,4)
        playback.receive(tree(listOf(a,b,c,d,e)))
        assertEquals(id(5), playback.position.cursorId) // Racing continuation not lost in catch-up/live handoff.
    }

    @Test fun onlyRemotePanelRevealsHoldLiveAcrossSeparateOrBatchedReceipts() {
        val open = node(1, 0).copy(action = PlayAction.ShowContents(id(80), ContentsSelection.TOP, 2))
        val reveal = node(2, 1).copy(action = PlayAction.RevealContentsCard(id(81), id(82)))
        val onward = node(3, 2)
        for (batched in listOf(false, true)) {
            val player = HistoryPlayback(tree(listOf(open)))
            if (!batched) {
                player.receive(tree(listOf(open, reveal)), id(71))
                assertTrue(player.livePanelReveal)
            }
            player.receive(tree(listOf(open, reveal, onward)), id(71))
            assertEquals(id(2), player.position.cursorId)
            assertEquals(HistoryMode.LIVE, player.position.mode)
            player.finishPanelReveal()
            assertEquals(id(3), player.position.cursorId)
            assertFalse(player.livePanelReveal)
        }
        val own = HistoryPlayback(tree(listOf(open)))
        own.receive(tree(listOf(open, reveal, onward)), id(70))
        assertEquals(id(3), own.position.cursorId)
        assertFalse(own.livePanelReveal)
        val ordinary = HistoryPlayback(tree(emptyList()))
        ordinary.receive(tree(listOf(open)), id(71))
        assertEquals(id(1), ordinary.position.cursorId)
        assertFalse(ordinary.livePanelReveal)
    }

    @Test fun ownConfirmationsDoNotManufactureCatchupAndHistoricalReviewSurvivesForeground() {
        val a = node(1,0)
        val playback = HistoryPlayback(tree(emptyList(), version = "predicted-a"))
        playback.background()
        playback.receive(tree(listOf(a), version = "predicted-a"))
        playback.foreground(); playback.caughtUp()
        assertEquals(HistoryMode.LIVE, playback.position.mode)
        assertEquals(id(1), playback.position.cursorId)
        playback.scrub(id(0)); playback.background()
        playback.receive(tree(listOf(a,node(2,1))))
        playback.foreground()
        assertEquals(id(0), playback.position.cursorId)
        assertEquals(HistoryMode.PLAYBACK, playback.position.mode)
        playback.play(); playback.background(); playback.next(automatic = true)
        assertEquals(id(0), playback.position.cursorId)
    }

    @Test fun previousNextByGroupsExactScrubAndChangedPathCommonAncestorRollback() {
        val a = node(1,0); val b = node(2,1); val c = node(3,2, HistorySignificance.ORIENTATION)
        val d = node(4,3); val x = node(5,1, HistorySignificance.STACK_ENTRY)
        val y = node(6,5, HistorySignificance.STACK_ENTRY)
        val nodes = listOf(a,b,c,d,x,y)
        val playback = HistoryPlayback(tree(nodes,4))
        playback.previous(); assertEquals(id(2), playback.position.cursorId)
        playback.previous(); assertEquals(id(0), playback.position.cursorId)
        playback.scrub(id(3)); assertEquals(id(3), playback.position.cursorId)
        playback.next(); assertEquals(id(4), playback.position.cursorId)
        playback.background(); playback.receive(tree(nodes,6)); playback.foreground()
        playback.finishAnimation(id(4))
        playback.play(); playback.acknowledgeBranch(); playback.next(true); assertEquals(id(2), playback.position.cursorId)
        playback.next(true); assertEquals(id(1), playback.position.cursorId) // Stop at ancestor INSIDE an old group.
        playback.next(true); assertEquals(id(5), playback.position.cursorId)
        playback.pause(); playback.next(true); assertEquals(id(5), playback.position.cursorId)
        playback.next(); assertEquals(id(6), playback.position.cursorId)
        playback.finishAnimation(id(6))
        assertEquals(HistoryMode.PLAYBACK, playback.position.mode)
        assertFalse(playback.position.playing)
    }

    @Test fun deletedCursorFallsBackThroughParentsWithoutRetargetingCapturedContinuation() {
        val a = node(1,0); val b = node(2,1); val c = node(3,2)
        val playback = HistoryPlayback(tree(listOf(a,b,c)))
        playback.scrub(id(2)); val captured = playback.position.cursorId
        assertTrue(playback.canContinue(captured))
        playback.receive(tree(listOf(c.copy(parentId = id(0))), 3))
        assertEquals(id(0), playback.position.cursorId)
        assertFalse(playback.canContinue(captured))
        assertEquals(HistoryMode.PLAYBACK, playback.position.mode)
        playback.scrub(id(3))
        playback.receive(tree(listOf(c.copy(parentId = id(0))),3, versions = mapOf(id(3) to "revised"), version = "revised"))
        assertEquals(id(3), playback.position.cursorId)
        assertEquals("revised", playback.position.displayVersion)
        playback.play(); assertFalse(playback.canContinue(id(3)))
        assertFails { playback.receive(HistoryTree(id(99), emptyList(), id(99), "new-play")) }
    }

    @Test fun staleScrubResolvesRemovedAncestorsAndUnknownCursorToLiveHead() {
        val a = node(1,0); val b = node(2,1); val c = node(3,2)
        val playback = HistoryPlayback(tree(listOf(a,b,c)))
        playback.receive(tree(listOf(a,b)))
        playback.receive(tree(listOf(a)))
        playback.scrub(id(3))
        assertEquals(id(1), playback.position.cursorId)
        assertEquals("v1", playback.position.displayVersion)
        assertFalse(playback.canContinue(id(3)))

        playback.scrub(id(99)) // Unknown/provisional cursor has no accepted ancestry.
        assertEquals(id(1), playback.position.cursorId)
        playback.previous()
        assertEquals(id(0), playback.position.cursorId)
        playback.next()
        assertEquals(id(1), playback.position.cursorId)
        playback.finishAnimation(id(1))
        assertEquals(HistoryMode.PLAYBACK, playback.position.mode)
    }

    @Test fun gm25EndingSingletonEndpointIsAfterFinalBoardNotNetworkBatch() {
        // Category/transport fixture only: no actual result-suggestion action exists at this pin.
        val board = node(1,0); val ending = node(2,1, HistorySignificance.ENDING_SUGGESTION)
        val playback = HistoryPlayback(tree(emptyList()))
        playback.background(); playback.receive(tree(listOf(board,ending))); playback.foreground()
        playback.play(); playback.next(true)
        assertEquals(id(1), playback.position.cursorId)
        assertEquals(HistoryMode.PLAYBACK, playback.position.mode)
        playback.next(true); assertEquals(id(2), playback.position.cursorId)
    }

    @Test fun endpointNetAnimationRetargetingHitTestingAndDisappearanceShareGeometry() {
        fun scene(x: Float, value: String = "", present: Boolean = true) = TableScene(if (present)
            listOf(TableItem("card", TableBounds(x,0f,10f,20f), "card", value)) else emptyList(), emptyList())
        val animation = HistorySceneAnimation(scene(0f))
        animation.retarget(scene(90f), 1f) // Three same-group moves target the final board only.
        assertEquals(45f, animation.frame(.5f).single().bounds.x)
        animation.retarget(scene(120f, "tapped"), .5f)
        assertEquals(45f, animation.frame(0f).single().bounds.x)
        assertEquals(82.5f, animation.frame(.5f).single().bounds.x)
        assertEquals("tapped", animation.hitTest(85f,5f,.5f)!!.value)
        assertNull(animation.hitTest(5f,5f,.5f))
        animation.retarget(scene(120f, present = false), 1f)
        assertEquals(.5f, animation.frame(.5f).single().alpha)
        assertNull(animation.hitTest(125f,5f,1f))
        animation.retarget(scene(120f), 1f)
        assertEquals(120f, animation.frame(.5f).single().bounds.x) // No invented net-zero motion.
    }

    @Test fun incompleteOrCyclicTreeCannotBecomeACompletedHistory() {
        assertFails { tree(listOf(node(2,1))) }
        assertFails { tree(listOf(node(1,2),node(2,1))) }
        assertFails { tree(listOf(node(1,0),node(1,0))) }
    }

    @Test fun autoAtHeadReturnsLiveSynchronouslyButArrivalWaitsForExactlyOneFinalAnimation() {
        for (automatic in listOf(false, true)) {
            val event = node(1, 0)
            val player = HistoryPlayback(tree(listOf(event)))
            player.scrub(id(1)); player.play()
            assertEquals(HistoryMode.LIVE, player.position.mode)
            player.openFrom(id(0))
            val start = TableScene(listOf(TableItem("card", TableBounds(0f, 0f, 10f, 20f), "card", "")), emptyList())
            val end = start.copy(items = start.items.map { it.copy(bounds = it.bounds.copy(x = 100f)) })
            val animation = HistorySceneAnimation(start)
            player.next(automatic)
            assertEquals(HistoryMode.PLAYBACK, player.position.mode)
            assertTrue(animation.retarget(end, 1f))
            assertEquals(0f, animation.frame(0f).single().bounds.x)
            assertEquals(100f, animation.frame(1f).single().bounds.x)
            assertFalse(player.finishAnimation(id(0))) // stale callback
            assertEquals(automatic, player.finishAnimation(id(1)))
            assertEquals(if (automatic) HistoryMode.LIVE else HistoryMode.PLAYBACK, player.position.mode)
            assertFalse(player.finishAnimation(id(1)))
            assertFalse(animation.retarget(end, 1f)) // Live metadata cannot restart the final motion.
        }
    }

    @Test fun exactManualSelectionAtHeadStaysPausedInTimelineAfterAnimationAndReceipt() {
        val events = listOf(node(1, 0), node(2, 1))
        val player = HistoryPlayback(tree(events))
        for (wasPlaying in listOf(false, true)) {
            player.scrub(id(0))
            if (wasPlaying) player.play()
            player.scrub(id(2)) // Event tap, drag and accessibility scroll share this path.
            assertFalse(player.finishAnimation(id(2)))
            player.receive(tree(events, version = "updated"))
            assertEquals(HistoryMode.PLAYBACK, player.position.mode)
            assertFalse(player.position.playing)
            assertEquals(id(2), player.position.cursorId)
        }
    }
}
