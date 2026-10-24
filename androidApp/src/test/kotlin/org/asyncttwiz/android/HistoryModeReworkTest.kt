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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HistoryModeReworkTest {
    @get:Rule val compose = createComposeRule()

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
        assertEquals(1_500L, alice.pauseMillis)
        alice.pauseMillis = 2_300
        assertEquals(2_300L, HistoryPlaybackPreferences(context, "alice").pauseMillis)
        assertEquals(1_500L, bob.pauseMillis)
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
        compose.onNodeWithTag("history-playback-pause").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(2_500f) }
        compose.runOnIdle { assertEquals(2_500L, settings.pauseMillis) }
    }
}
