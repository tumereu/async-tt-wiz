package org.asyncttwiz.android

import android.content.Context
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

    @Test fun branchNoticeThenQuickFadeThenFastRollbackLocksTransportUntilCommonAncestor() {
        val common = F.play(GameplayTree(F.root()), PlayAction.AdjustLife(F.a, "1"), 100)
        val old1 = F.play(common, PlayAction.AdjustLife(F.b, "1"), 101, F.b)
        val old2 = F.play(old1, PlayAction.AdjustLife(F.a, "1"), 102)
        val branch = F.change(old2, F.command(old2, GameAction.Play(PlayAction.AdjustLife(F.b, "2")), 103)
            .copy(continuation = Continuation(common.currentNodeId, true)), F.b)
        val player = HistoryPlayback(HistoryTree(branch.initial.root.id, emptyList(), branch.currentNodeId,
            "branch", containers = branch.initial.board.containers, source = branch, lazyPresentation = true))
        player.openFrom(old2.currentNodeId)
        var animatedCursor by mutableStateOf<String?>(null)
        compose.mainClock.autoAdvance = false
        compose.setContent { MaterialTheme {
            HistoryPlaybackPanel(player, old2.currentNodeId, F.a, true, 1_500, {},
                animationFinished = animatedCursor == player.position.cursorId)
        } }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("Opponent branched").assertIsDisplayed()
        for (description in listOf("Auto advance", "Previous", "Next")) compose.onNodeWithContentDescription(description).assertIsNotEnabled()
        compose.runOnIdle {
            player.previous(); player.next(); player.play()
            assertEquals(old2.currentNodeId, player.position.cursorId)
        }
        compose.mainClock.advanceTimeBy(1_400)
        compose.onNodeWithText("Opponent branched").assertIsDisplayed()
        compose.runOnIdle { assertTrue(player.branched); assertEquals(old2.currentNodeId, player.position.cursorId) }
        compose.mainClock.advanceTimeBy(400)
        compose.runOnIdle {
            assertFalse(player.branched)
            assertEquals(old2.currentNodeId, player.position.cursorId)
            assertTrue(player.transportLocked)
            animatedCursor = old2.currentNodeId
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(200)
        compose.runOnIdle { assertEquals(old1.currentNodeId, player.position.cursorId) }
        for (description in listOf("Auto advance", "Previous", "Next")) compose.onNodeWithContentDescription(description).assertIsNotEnabled()
        compose.mainClock.advanceTimeBy(500)
        compose.runOnIdle { assertEquals(old1.currentNodeId, player.position.cursorId); animatedCursor = old1.currentNodeId }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(200)
        compose.runOnIdle { assertEquals(common.currentNodeId, player.position.cursorId); assertFalse(player.transportLocked); player.pause() }
        compose.onNodeWithContentDescription("Auto advance").assertIsEnabled()
    }

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
        compose.onNodeWithText("Tap: toggle · Hold: branch").assertIsDisplayed()
        auto.performClick()
        compose.runOnIdle { assertTrue(player.position.playing) }
        auto.assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "Auto: on"))
        compose.onNodeWithText("Auto: on").assertIsDisplayed()
        auto.performTouchInput { click() }
        compose.runOnIdle { assertFalse(player.position.playing) }
        compose.onNodeWithText("Auto: off").assertIsDisplayed()
    }

    @Test fun runningAutoButtonHoldPausesAndBranchesAtOriginalCursorWithoutToggling() {
        val tree = TableStatesFixture.history()
        val player = TableStatesFixture.playback(tree)
        val cursor = tree.initial.root.id
        player.scrub(cursor)
        player.play()
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

    @Test fun playbackWaitsForAnimationThenFullPauseAndReadsSettingChangesLive() {
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
        compose.runOnIdle { assertEquals(tree.initial.root.id, player.position.cursorId); animatedCursor = player.position.cursorId }
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
        compose.runOnIdle { assertEquals(tree.initial.root.id, player.position.cursorId) }
        compose.mainClock.advanceTimeBy(150)
        val first = compose.runOnIdle { player.position.cursorId }
        assertNotEquals(tree.initial.root.id, first)
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
        compose.mainClock.advanceTimeBy(64)
        compose.runOnIdle { assertEquals(first, player.position.cursorId) }
        compose.mainClock.advanceTimeBy(80)
        compose.runOnIdle { assertNotEquals(first, player.position.cursorId); player.pause() }
    }

    @Test fun pausePreferencePersistsPerAccountAndClampsReadsAndWrites() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("history-playback", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val alice = HistoryPlaybackPreferences(context, "alice")
        val bob = HistoryPlaybackPreferences(context, "bob")
        assertEquals(500L, alice.pauseMillis)
        alice.pauseMillis = 2_300
        assertEquals(2_300L, HistoryPlaybackPreferences(context, "alice").pauseMillis)
        assertEquals(500L, bob.pauseMillis)
        alice.pauseMillis = Long.MIN_VALUE
        assertEquals(100L, alice.pauseMillis)
        alice.pauseMillis = Long.MAX_VALUE
        assertEquals(10_000L, HistoryPlaybackPreferences(context, "alice").pauseMillis)
        prefs.edit().putLong(bob.key, -1).commit()
        assertEquals(100L, bob.pauseMillis)
        prefs.edit().putLong(bob.key, 100_000).commit()
        assertEquals(10_000L, bob.pauseMillis)
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
    }
}
