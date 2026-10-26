package org.asyncttwiz.android

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import org.asyncttwiz.protocol.*
import org.asyncttwiz.storage.CatalogMetadata
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class PlaybackSettingsTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Test fun defaultMigrationPreservesNonDefaultsAndFutureExplicitOldDefault() {
        val stored = context.getSharedPreferences("history-playback", Context.MODE_PRIVATE)
        stored.edit().clear().putLong("pause/old-default", 1_500).putLong("pause/custom", 2_300).commit()
        assertEquals(500L, HistoryPlaybackPreferences(context, "new").pauseMillis)
        val migrated = HistoryPlaybackPreferences(context, "old-default")
        assertEquals(500L, migrated.pauseMillis)
        assertEquals(2_300L, HistoryPlaybackPreferences(context, "custom").pauseMillis)
        migrated.pauseMillis = 1_500
        assertEquals(1_500L, HistoryPlaybackPreferences(context, "old-default").pauseMillis)
    }

    @Test fun togglesPersistAndNotifyOnlyTheirAccountAndAnimationSpeedIsAccountScoped() {
        val a = HistoryPlaybackPreferences(context, "options-a")
        val b = HistoryPlaybackPreferences(context, "options-b")
        assertEquals(PlaybackAutoPause.defaults, a.autoPause)
        var aChanges = 0
        var bChanges = 0
        val stopA = a.observe { aChanges++ }
        val stopB = b.observe { bChanges++ }
        a.setAutoPause(PlaybackAutoPause.REVEAL, false)
        a.setAutoPause(PlaybackAutoPause.ATTACKERS, true)
        assertFalse(PlaybackAutoPause.REVEAL in HistoryPlaybackPreferences(context, "options-a").autoPause)
        assertTrue(PlaybackAutoPause.ATTACKERS in a.autoPause)
        assertEquals(PlaybackAutoPause.defaults, b.autoPause)
        assertEquals(2, aChanges)
        assertEquals(0, bChanges)
        stopA(); stopB()
        CardMovePreferences(context, "options-a").multiplier = 2f
        assertEquals(2f, CardMovePreferences(context, "options-a").multiplier)
        assertEquals(DEFAULT_CARD_MOVE_SPEED * 2f, CardMovePreferences(context, "options-a").speed)
        assertEquals(DEFAULT_CARD_MOVE_SPEED, CardMovePreferences(context, "options-b").speed)
        val moves = CardMovePreferences(context, "options-a")
        moves.multiplier = 10f
        assertEquals(3f, CardMovePreferences(context, "options-a").multiplier)
        moves.multiplier = -1f
        assertEquals(.25f, moves.multiplier)
        moves.multiplier = Float.NaN
        assertEquals(1f, moves.multiplier)
    }

    @Test fun timelineGearOpensTheSharedPlaybackSectionAndStopsAuto() {
        val settings = HistoryPlaybackPreferences(context, "dialog")
        val player = TableStatesFixture.playback()
        player.openFrom(player.tree.rootId)
        var open by mutableStateOf(false)
        compose.setContent { MaterialTheme {
            HistoryPlaybackPanel(player, player.tree.sharedHeadId, TableStatesFixture.a, true, 10_000, {},
                animationFinished = false, onSettings = { open = true })
            if (open) PlaybackSettingsDialog(settings, "dialog") { open = false }
        } }
        compose.onNodeWithContentDescription("Playback settings").performClick()
        compose.runOnIdle { assertFalse(player.position.playing) }
        compose.onNode(isDialog()).assertExists()
        compose.onNodeWithText("Playback", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("auto-pause-STACK_ADDED").assertIsOn().performClick().assertIsOff()
        compose.runOnIdle { assertFalse(PlaybackAutoPause.STACK_ADDED in settings.autoPause) }
        for (trigger in listOf(PlaybackAutoPause.STACK_REMOVED, PlaybackAutoPause.LAND_FROM_HAND, PlaybackAutoPause.NONLAND_FROM_HAND)) {
            compose.onNodeWithTag("auto-pause-${trigger.name}").performScrollTo().assertIsOff().performClick().assertIsOn()
            compose.runOnIdle { assertTrue(trigger in settings.autoPause) }
        }
        compose.onNodeWithTag("card-move-speed").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("card-move-speed-value").performScrollTo().performClick()
        compose.onNodeWithTag("card-scale-input").performTextReplacement("0.1")
        compose.onNodeWithTag("card-scale-save").performClick()
        compose.runOnIdle { assertEquals(.25f, CardMovePreferences(context, "dialog").multiplier) }
        compose.onNodeWithTag("card-move-speed-value").assertTextEquals("0.25×")
        compose.onNodeWithText("Done").performScrollTo().performClick()
        compose.onNode(isDialog()).assertDoesNotExist()
    }

    @Test fun settingsShowsSameTogglesAndDelay() {
        val settings = HistoryPlaybackPreferences(context, "settings")
        compose.setContent { MaterialTheme { SettingsContent(null, "settings") {} } }
        compose.onNodeWithText("Playback").assertIsDisplayed()
        compose.onNodeWithTag("auto-pause-ATTACKERS").performScrollTo().assertIsOff().performClick().assertIsOn()
        compose.runOnIdle { assertTrue(PlaybackAutoPause.ATTACKERS in settings.autoPause) }
        compose.onNodeWithTag("history-playback-pause").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("0.5 s after each group").assertExists()
    }

    @Test fun terminalMatchingGroupKeepsAutoOnDuringAnimationThenGoesLiveWithoutAnotherDelay() {
        val event = GameplayMutation("one", "root", "branch", TableStatesFixture.id("request"), "opponent", null,
            PlayAction.RollDie(6, 4), GameplayTransition())
        val player = HistoryPlayback(HistoryTree("root", listOf(event), event.id, "v"))
        player.openFrom("root")
        var animatedCursor by mutableStateOf<String?>("root")
        compose.mainClock.autoAdvance = false
        compose.setContent { MaterialTheme {
            HistoryPlaybackPanel(player, player.tree.sharedHeadId, "me", true, 500, {},
                animationFinished = animatedCursor == player.position.cursorId)
        } }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(600)
        compose.runOnIdle {
            assertEquals(event.id, player.position.cursorId)
            assertTrue(player.position.playing)
        }
        compose.mainClock.advanceTimeBy(2_000)
        compose.runOnIdle { assertTrue(player.position.playing); animatedCursor = event.id }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(48) // Recompose and run the completion effect, well before the 500ms group delay.
        compose.waitForIdle()
        compose.runOnIdle { assertFalse(player.position.playing); assertEquals(HistoryMode.LIVE, player.position.mode) }
    }

    @Test fun legacyStackPreferenceMigratesOptOutAndOtherwiseUsesNewDefaultsOnlyOnce() {
        val stored = context.getSharedPreferences("history-playback", Context.MODE_PRIVATE)
        stored.edit().putBoolean("auto-pause/off/STACK", false).putBoolean("auto-pause/on/STACK", true).commit()
        val off = HistoryPlaybackPreferences(context, "off")
        assertFalse(PlaybackAutoPause.STACK_ADDED in off.autoPause)
        assertFalse(PlaybackAutoPause.STACK_REMOVED in off.autoPause)
        for (account in listOf("on", "unset")) {
            assertTrue(PlaybackAutoPause.STACK_ADDED in HistoryPlaybackPreferences(context, account).autoPause)
            assertFalse(PlaybackAutoPause.STACK_REMOVED in HistoryPlaybackPreferences(context, account).autoPause)
        }
        off.setAutoPause(PlaybackAutoPause.STACK_ADDED, true)
        off.setAutoPause(PlaybackAutoPause.STACK_REMOVED, true)
        assertTrue(PlaybackAutoPause.STACK_ADDED in HistoryPlaybackPreferences(context, "off").autoPause)
        assertTrue(PlaybackAutoPause.STACK_REMOVED in HistoryPlaybackPreferences(context, "off").autoPause)
    }

    @Test fun catalogReservesStableStatusAndActionSlotsInPortrait() = assertCatalogGeometry()

    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi")
    fun catalogReservesStableStatusAndActionSlotsInLandscape() = assertCatalogGeometry()

    private fun assertCatalogGeometry() {
        val models = ViewModelStore()
        val catalog = CatalogViewModel(ApplicationProvider.getApplicationContext()).also {
            models.put("catalog", it)
            it.seed(CatalogUiState(installed = CatalogMetadata("2026-10-25", "1", records = 100), loaded = true))
        }
        try {
            compose.setContent { MaterialTheme { SettingsContent(catalog, "gap") {} } }
            // The reserved catalog slots can put the next section outside the viewport.
            // Compare actual layout bounds, not rectangles clipped by the settings scroller.
            val installed = compose.onNodeWithText("Installed:", substring = true).getUnclippedBoundsInRoot()
            val check = compose.onNodeWithText("Check for updates").getUnclippedBoundsInRoot()
            val refresh = compose.onNodeWithText("Refresh full catalog").getUnclippedBoundsInRoot()
            val playback = compose.onNodeWithText("Playback").getUnclippedBoundsInRoot()
            assertTrue(playback.top - refresh.bottom < 60.dp, "Playback should follow Card records without a blank block: $refresh -> $playback")
            compose.runOnIdle { catalog.seed(catalog.state.copy(busy = true, status = "Checking card records…")) }
            assertEquals(check, compose.onNodeWithText("Check for updates").getUnclippedBoundsInRoot())
            assertEquals(playback, compose.onNodeWithText("Playback").getUnclippedBoundsInRoot())
            compose.runOnIdle { catalog.seed(catalog.state.copy(busy = false, error = true, status = "Synthetic network failure")) }
            assertEquals(check, compose.onNodeWithText("Retry").getUnclippedBoundsInRoot())
            assertEquals(installed, compose.onNodeWithText("Installed:", substring = true).getUnclippedBoundsInRoot())
            assertEquals(playback, compose.onNodeWithText("Playback").getUnclippedBoundsInRoot())
        } finally { models.clear() }
    }
}
