package org.asyncttwiz.android

import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.*
import org.asyncttwiz.fixtures.ScaleGames as F
import org.asyncttwiz.protocol.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HistoryModeReworkTest {
    @get:Rule val compose = createComposeRule()

    // HIS-047
    @Test fun treePinchAndPanKeepTurnTagsAttachedWithoutSelectingOrClosing() {
        val tree = F.play(GameplayTree(F.root()), PlayAction.EndTurn, 401)
        val controller = HistoryExplorerController(java.util.concurrent.Executor { it.run() }, { it() })
        controller.update(tree, openingNodeId = tree.initial.root.id, showHidden = true)
        var dismissed = 0
        try {
            compose.setContent { MaterialTheme {
                HistoryExplorer(controller, mapOf(F.a to OwnTableColor, F.b to OpponentTableColor), true, {}, { dismissed++ }, {})
            } }
            compose.onNodeWithText("T1").assertIsDisplayed()
            compose.onNodeWithText("T2").assertIsDisplayed()
            val canvas = compose.onNodeWithContentDescription("Game history group tree")
            fun zoom() = canvas.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.StateDescription]
                .removePrefix("Zoom ").toFloat()
            assertEquals(1f, zoom())
            canvas.performTouchInput { pinch(
                start0 = center + androidx.compose.ui.geometry.Offset(-40f, 0f), start1 = center + androidx.compose.ui.geometry.Offset(40f, 0f),
                end0 = center + androidx.compose.ui.geometry.Offset(-80f, 0f), end1 = center + androidx.compose.ui.geometry.Offset(80f, 0f)) }
            val enlarged = zoom()
            assertTrue(enlarged > 1f, "Pinch out must zoom in (after touch slop): $enlarged")
            compose.onNodeWithText("T1").assertIsDisplayed()
            compose.onNodeWithText("T2").assertIsDisplayed()
            canvas.performTouchInput { pinch(
                start0 = center + androidx.compose.ui.geometry.Offset(-80f, 0f), start1 = center + androidx.compose.ui.geometry.Offset(80f, 0f),
                end0 = center + androidx.compose.ui.geometry.Offset(-40f, 0f), end1 = center + androidx.compose.ui.geometry.Offset(40f, 0f)) }
            assertTrue(zoom() < enlarged)
            compose.onNodeWithText("T1").assertIsDisplayed()
            compose.onNodeWithText("T2").assertIsDisplayed()
            val before = compose.onNodeWithText("T1").fetchSemanticsNode().boundsInRoot.center
            canvas.performTouchInput { swipe(center, center + androidx.compose.ui.geometry.Offset(50f, 40f), 200) }
            val after = compose.onNodeWithText("T1").fetchSemanticsNode().boundsInRoot.center
            assertTrue(after.x > before.x && after.y > before.y)
            compose.onNodeWithText("T1").assertIsDisplayed()
            compose.onNodeWithText("T2").assertIsDisplayed()
            assertEquals(tree.initial.root.id, controller.state.selectedNodeId)
            assertEquals(0, dismissed)
        } finally { controller.close() }
    }

    // HIS-013, HIS-014, HIS-039
    @Test fun pendingBranchOffersRewindAndPlayWithoutHoldToBranchThenFastRollbackLocksTransportUntilCommonAncestor() {
        val common = F.play(GameplayTree(F.root()), PlayAction.AdjustLife(F.a, "1"), 100)
        val old1 = F.play(common, PlayAction.AdjustLife(F.b, "1"), 101, F.b)
        val old2 = F.play(old1, PlayAction.AdjustLife(F.a, "1"), 102)
        val branch = F.change(old2, F.command(old2, GameAction.Play(PlayAction.AdjustLife(F.b, "2")), 103)
            .copy(continuation = Continuation(common.currentNodeId, true)), F.b)
        val player = HistoryPlayback(HistoryTree(branch.initial.root.id, emptyList(), branch.currentNodeId,
            "branch", containers = branch.initial.board.containers, source = branch, lazyPresentation = true))
        player.openFrom(old2.currentNodeId)
        var animatedCursor by mutableStateOf<String?>(old2.currentNodeId)
        val continued = mutableListOf<String>()
        compose.setContent { MaterialTheme {
            HistoryPlaybackPanel(player, old2.currentNodeId, F.a, true, 1_500, { continued += it },
                animationFinished = animatedCursor == player.position.cursorId)
        } }
        // Paused and persistent: no flash, nothing plays until asked.
        compose.mainClock.advanceTimeBy(3_000)
        compose.runOnIdle { assertTrue(player.branched); assertFalse(player.position.playing); assertEquals(old2.currentNodeId, player.position.cursorId) }
        val auto = compose.onNodeWithContentDescription("Auto advance")
        auto.assertIsEnabled().assert(SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "Rewind to the branch point, then play"))
        auto.assert(SemanticsMatcher.keyNotDefined(androidx.compose.ui.semantics.SemanticsActions.CustomActions))
        compose.onNodeWithTag("Rewind and play", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Rewind", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Hold to branch", useUnmergedTree = true).assertDoesNotExist() // HIS-013
        // The rewind is the only way on: the steppers wait until it has passed the branch point.
        for (description in listOf("Previous", "Next")) compose.onNodeWithContentDescription(description).assertIsNotEnabled()
        compose.onNodeWithText("Opponent branched").assertDoesNotExist() // The table centre text carries it.
        auto.performTouchInput { longClick() }
        compose.runOnIdle { assertTrue(continued.isEmpty()); assertTrue(player.branched) }
        compose.mainClock.autoAdvance = false
        auto.performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { assertTrue(player.position.playing); assertTrue(player.transportLocked) }
        compose.mainClock.advanceTimeBy(200)
        compose.runOnIdle { assertEquals(old1.currentNodeId, player.position.cursorId); assertTrue(player.branched) }
        for (description in listOf("Auto advance", "Previous", "Next")) compose.onNodeWithContentDescription(description).assertIsNotEnabled()
        compose.mainClock.advanceTimeBy(500)
        compose.runOnIdle { assertEquals(old1.currentNodeId, player.position.cursorId); animatedCursor = old1.currentNodeId }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(200)
        compose.runOnIdle { assertEquals(common.currentNodeId, player.position.cursorId); assertFalse(player.transportLocked)
            assertFalse(player.branched); player.pause() }
        compose.onNodeWithContentDescription("Auto advance").assertIsEnabled()
        compose.onNodeWithTag("Rewind and play", useUnmergedTree = true).assertDoesNotExist()
        compose.mainClock.autoAdvance = true
    }

    // HIS-012, HIS-013, HIS-014, HIS-040
    @Test fun anOldBranchPickedByThePlayerStepsAlongItsOwnLineAndOffersRewind() {
        val common = F.play(GameplayTree(F.root()), PlayAction.AdjustLife(F.a, "1"), 100)
        val old1 = F.play(common, PlayAction.AdjustLife(F.b, "1"), 101, F.b)
        val old2 = F.play(old1, PlayAction.AdjustLife(F.a, "1"), 102)
        val branch = F.change(old2, F.command(old2, GameAction.Play(PlayAction.AdjustLife(F.b, "2")), 103)
            .copy(continuation = Continuation(common.currentNodeId, true)), F.b)
        val player = HistoryPlayback(HistoryTree(branch.initial.root.id, emptyList(), branch.currentNodeId,
            "branch", containers = branch.initial.board.containers, source = branch, lazyPresentation = true))
        player.scrub(old1.currentNodeId) // Picked in the game tree.
        compose.setContent { MaterialTheme {
            HistoryPlaybackPanel(player, branch.currentNodeId, F.a, true, 1_500, {})
        } }
        fun stripEnd() = compose.onNode(SemanticsMatcher.keyIsDefined(
            androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo)).fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo].range.endInclusive
        compose.runOnIdle { assertTrue(player.oldBranch); assertFalse(player.branched) }
        val auto = compose.onNodeWithContentDescription("Auto advance")
        auto.assertIsEnabled().assert(SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "Rewind to the branch point, then play"))
        compose.onNodeWithTag("Rewind and play", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Rewind", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Hold to branch", useUnmergedTree = true).assertDoesNotExist()
        // Not locked: hold-to-branch from here stays offered.
        auto.assert(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsActions.CustomActions))
        assertEquals(3f, stripEnd()) // root, common, old1, old2: the whole old branch.
        compose.onNodeWithContentDescription("Next").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(old2.currentNodeId, player.position.cursorId); assertFalse(player.position.playing) }
        compose.onNodeWithContentDescription("Next").assertIsNotEnabled() // The old branch's tip.
        compose.onNodeWithContentDescription("Previous").performClick()
        compose.onNodeWithContentDescription("Previous").performClick()
        compose.runOnIdle { assertEquals(common.currentNodeId, player.position.cursorId); assertFalse(player.offLiveLine)
            assertTrue(player.oldBranch) } // The label follows the strip, not the cursor.
        assertEquals(3f, stripEnd()) // Still the chosen line on the shared part.
        compose.onNodeWithContentDescription("Auto advance").assert(SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "Auto: off"))
        compose.onNodeWithTag("Rewind and play", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("Hold to branch", useUnmergedTree = true).assertExists() // HIS-013
    }

    // HIS-008, HIS-038
    @Test fun tableCentreTextNamesAPendingBranchAndThePlaybackStatus() {
        var branched by mutableStateOf(true)
        var status by mutableStateOf(TimelinePlaybackStatus.PAUSED)
        compose.setContent { MaterialTheme {
            TableSurface(TableFixture.game, TableFixture.play(board = 1), "a", false, androidx.compose.ui.Modifier.fillMaxSize(),
                runtime = TableRuntimeHooks(reviewing = true, opponentBranched = branched, timelinePlayback = status))
        } }
        compose.onNodeWithText("Opponent branched").assertIsDisplayed()
        compose.onNodeWithText("Timeline mode").assertDoesNotExist()
        compose.onNodeWithText("Paused").assertIsDisplayed()
        compose.runOnIdle { status = TimelinePlaybackStatus.REWINDING }
        compose.onNodeWithText("Rewinding").assertIsDisplayed()
        compose.onNodeWithContentDescription("Rewinding").assertIsDisplayed() // The corner holds the rewind icon.
        compose.runOnIdle { branched = false; status = TimelinePlaybackStatus.PLAYING }
        compose.onNodeWithText("Timeline mode").assertIsDisplayed()
        compose.onNodeWithText("Playing").assertIsDisplayed()
        compose.onNodeWithText("Opponent branched").assertDoesNotExist()
    }

    // HIS-013
    @Test fun autoButtonShowsStateAndTogglesFromBothStates() {
        val tree = TableStatesFixture.history()
        val player = TableStatesFixture.playback(tree)
        player.scrub(tree.initial.root.id)
        compose.setContent { MaterialTheme {
            HistoryTransport(player.position, false, true, true, player::previous, { player.next() }, player::play, player::pause, {})
        } }
        val auto = compose.onNodeWithContentDescription("Auto advance")
        auto.assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "Auto: off"))
        compose.onNodeWithText("Auto: off").assertIsDisplayed()
        compose.onNodeWithText("Hold to branch").assertIsDisplayed()
        auto.performClick()
        compose.runOnIdle { assertTrue(player.position.playing) }
        auto.assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "Auto: on"))
        compose.onNodeWithText("Auto: on").assertIsDisplayed()
        compose.onNodeWithText("Hold to branch").assertIsDisplayed()
        auto.performTouchInput { click() }
        compose.runOnIdle { assertFalse(player.position.playing) }
        compose.onNodeWithText("Auto: off").assertIsDisplayed()
    }

    // HIS-036
    @Test fun runningAutoButtonHoldPausesAndBranchesAtOriginalCursorWithoutToggling() {
        val tree = TableStatesFixture.history()
        val player = TableStatesFixture.playback(tree)
        player.scrub(tree.initial.root.id)
        player.play()
        player.next(automatic = true) // The immediate first step: now mid-playback, waiting the pause.
        val cursor = player.position.cursorId
        val continued = mutableListOf<String>()
        compose.setContent { MaterialTheme {
            HistoryPlaybackPanel(player, tree.currentNodeId, TableStatesFixture.a, true, 60_000, { continued += it })
        } }
        compose.onNodeWithContentDescription("Auto advance").performTouchInput { longClick() }
        compose.runOnIdle {
            assertEquals(listOf(cursor), continued)
            assertFalse(player.position.playing)
        }
        compose.onNode(isDialog()).assertDoesNotExist()
        compose.onNodeWithText("Auto: off").assertIsDisplayed()
    }

    // HIS-021, HIS-023
    @Test fun manualStartStepsAtOnceThenWaitsForAnimationAndFullPauseAndReadsSettingChangesLive() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val settings = HistoryPlaybackPreferences(context, "timing")
        settings.pauseMillis = 1_500
        val tree = TableStatesFixture.history()
        val player = TableStatesFixture.playback(tree)
        player.scrub(tree.initial.root.id)
        var animatedCursor by mutableStateOf<String?>(null)
        var observedPause = 0L
        compose.mainClock.autoAdvance = false
        compose.setContent { MaterialTheme {
            val pause by settings.pauseState()
            SideEffect { observedPause = pause }
            HistoryPlaybackPanel(player, tree.currentNodeId, TableStatesFixture.a, true, pause, {},
                animationFinished = animatedCursor == player.position.cursorId)
        } }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { player.play() }
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(3_000)
        // Even the immediate first step waits for the shown point's animation.
        compose.runOnIdle { assertEquals(tree.initial.root.id, player.position.cursorId); animatedCursor = player.position.cursorId }
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        val first = compose.runOnIdle { player.position.cursorId }
        assertNotEquals(tree.initial.root.id, first) // No pause before the first step of a manual start.
        // The next group cannot start until its own animation finishes, even after a long delay.
        compose.mainClock.advanceTimeBy(3_000)
        compose.runOnIdle {
            assertEquals(first, player.position.cursorId)
            assertTrue(player.position.playing)
            animatedCursor = first
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(700)
        // A live change must not shorten/restart the 1.5s pause already underway.
        compose.runOnIdle { settings.pauseMillis = 100 }
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { assertEquals(100L, observedPause) }
        compose.mainClock.advanceTimeBy(700)
        compose.runOnIdle { assertEquals(first, player.position.cursorId) }
        compose.mainClock.advanceTimeBy(150)
        val second = compose.runOnIdle { player.position.cursorId }
        assertNotEquals(first, second)
        compose.runOnIdle { animatedCursor = second }
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(64)
        compose.runOnIdle { assertEquals(second, player.position.cursorId) }
        compose.mainClock.advanceTimeBy(80)
        compose.runOnIdle { assertNotEquals(second, player.position.cursorId); player.pause() }
    }

    // HIS-021
    @Test fun pausePreferencePersistsPerAccountAndClampsReadsAndWrites() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("history-playback", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val alice = HistoryPlaybackPreferences(context, "alice")
        val bob = HistoryPlaybackPreferences(context, "bob")
        assertEquals(1_000L, alice.pauseMillis)
        alice.pauseMillis = 2_300
        assertEquals(2_300L, HistoryPlaybackPreferences(context, "alice").pauseMillis)
        assertEquals(1_000L, bob.pauseMillis)
        alice.pauseMillis = Long.MIN_VALUE
        assertEquals(100L, alice.pauseMillis)
        alice.pauseMillis = Long.MAX_VALUE
        assertEquals(10_000L, HistoryPlaybackPreferences(context, "alice").pauseMillis)
        prefs.edit().putLong(bob.key, -1).commit()
        assertEquals(100L, bob.pauseMillis)
        prefs.edit().putLong(bob.key, 100_000).commit()
        assertEquals(10_000L, bob.pauseMillis)
    }

    // HIS-022
    @Test fun rewindPausePreferenceIsSeparatePerAccountAndClamped() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("history-playback", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val alice = HistoryPlaybackPreferences(context, "alice")
        val bob = HistoryPlaybackPreferences(context, "bob")
        assertEquals(100L, alice.rewindPauseMillis)
        var changes = 0
        val stop = alice.observe { changes++ }
        alice.rewindPauseMillis = 400
        stop()
        assertEquals(1, changes)
        assertEquals(400L, HistoryPlaybackPreferences(context, "alice").rewindPauseMillis)
        assertEquals(1_000L, alice.pauseMillis)
        assertEquals(100L, bob.rewindPauseMillis)
        alice.rewindPauseMillis = 0
        assertEquals(20L, alice.rewindPauseMillis)
        alice.rewindPauseMillis = Long.MAX_VALUE
        assertEquals(2_000L, HistoryPlaybackPreferences(context, "alice").rewindPauseMillis)
    }

    @Test fun reviewLeavesBoardViewportCameraRailsAndCardBoundsUnchangedInBothOrientations() {
        val f = TableStatesFixture
        val ui = TableLayoutState(camera = TableCamera(-20f, 15f, 1.3f))
        for (landscape in listOf(false, true)) {
            val width = if (landscape) 892f else 412f
            val height = if (landscape) 412f else 892f
            val live = TablePresentation.scene(f.game, f.initial, f.a, width, height, landscape, ui)
            val review = TablePresentation.scene(f.game, f.initial, f.a, width, height, landscape, ui, reviewing = true)
            assertEquals(live.world, review.world)
            assertEquals(live.camera, review.camera)
            assertEquals(live.fields, review.fields)
            assertEquals(live.viewports, review.viewports)
            assertEquals(live.items.map { it.bounds }, review.items.map { it.bounds })
        }
    }

    @Test fun settingsSliderWritesTheSignedInAccountsPreference() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val settings = HistoryPlaybackPreferences(context, "slider-account")
        settings.pauseMillis = 1_500
        compose.setContent { MaterialTheme { SettingsContent(null, "slider-account") {} } }
        compose.onNodeWithText("History playback pause").assertExists()
        compose.onNodeWithTag("history-playback-pause").performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(2_500f) }
        compose.runOnIdle { assertEquals(2_500L, settings.pauseMillis) }
        compose.onNodeWithText("Rewind pause").assertExists()
        compose.onNodeWithTag("history-rewind-pause").performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(300f) }
        compose.runOnIdle { assertEquals(300L, settings.rewindPauseMillis); assertEquals(2_500L, settings.pauseMillis) }
    }
}
