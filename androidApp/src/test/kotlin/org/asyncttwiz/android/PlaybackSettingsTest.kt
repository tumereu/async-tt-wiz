package org.asyncttwiz.android

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import org.asyncttwiz.protocol.*
import org.asyncttwiz.storage.CatalogMetadata
import org.asyncttwiz.storage.CatalogPhase
import org.asyncttwiz.storage.CatalogProgress
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.*
import org.asyncttwiz.appui.AppPlatform

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class PlaybackSettingsTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Test fun defaultMigrationPreservesNonDefaultsAndFutureExplicitOldDefault() {
        val stored = context.getSharedPreferences("history-playback", Context.MODE_PRIVATE)
        stored.edit().clear().putLong("pause/old-default", 1_500).putLong("pause/custom", 2_300).commit()
        assertEquals(1_000L, HistoryPlaybackPreferences(AppPlatform.current, "new").pauseMillis)
        val migrated = HistoryPlaybackPreferences(AppPlatform.current, "old-default")
        assertEquals(1_000L, migrated.pauseMillis)
        assertEquals(2_300L, HistoryPlaybackPreferences(AppPlatform.current, "custom").pauseMillis)
        migrated.pauseMillis = 1_500
        assertEquals(1_500L, HistoryPlaybackPreferences(AppPlatform.current, "old-default").pauseMillis)
    }

    // LOB-081
    @Test fun pausesPersistAndNotifyOnlyTheirAccountAndAnimationSpeedIsAccountScoped() {
        val a = HistoryPlaybackPreferences(AppPlatform.current, "options-a")
        val b = HistoryPlaybackPreferences(AppPlatform.current, "options-b")
        assertEquals(HistoryPlaybackPreferences.DEFAULT_PAUSE_MILLIS, a.pauseMillis)
        var aChanges = 0
        var bChanges = 0
        val stopA = a.observe { aChanges++ }
        val stopB = b.observe { bChanges++ }
        a.pauseMillis = 2_000
        a.rewindPauseMillis = 500
        assertEquals(2_000L, HistoryPlaybackPreferences(AppPlatform.current, "options-a").pauseMillis)
        assertEquals(500L, a.rewindPauseMillis)
        assertEquals(HistoryPlaybackPreferences.DEFAULT_PAUSE_MILLIS, b.pauseMillis)
        assertEquals(HistoryPlaybackPreferences.DEFAULT_REWIND_PAUSE_MILLIS, b.rewindPauseMillis)
        assertEquals(2, aChanges)
        assertEquals(0, bChanges)
        stopA(); stopB()
        CardMovePreferences(AppPlatform.current, "options-a").multiplier = 2f
        assertEquals(2f, CardMovePreferences(AppPlatform.current, "options-a").multiplier)
        assertEquals(DEFAULT_CARD_MOVE_SPEED * 2f, CardMovePreferences(AppPlatform.current, "options-a").speed)
        assertEquals(DEFAULT_CARD_MOVE_SPEED, CardMovePreferences(AppPlatform.current, "options-b").speed)
        val moves = CardMovePreferences(AppPlatform.current, "options-a")
        moves.multiplier = 10f
        assertEquals(3f, CardMovePreferences(AppPlatform.current, "options-a").multiplier)
        moves.multiplier = -1f
        assertEquals(.25f, moves.multiplier)
        moves.multiplier = Float.NaN
        assertEquals(1f, moves.multiplier)
    }


    // HIS-028, LOB-082, HIS-112, HIS-113: Timeline playback holds the two pauses and the time a play shows and a combat holds (1.2 s by default, 0 reads
    // "Off"); there are no auto-pause switches
    @Test fun settingsShowsThePlaybackPausesAndThePlaysTime() {
        HistoryPlaybackPreferences(AppPlatform.current, "settings")
        compose.setContent { MaterialTheme { SettingsContent(null, "settings") {} } }
        compose.onNodeWithText("Timeline playback").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Stop Auto after an opponent’s matching action").assertDoesNotExist()
        compose.onAllNodes(isToggleable()).fetchSemanticsNodes().forEach { node ->
            assertTrue(node.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.TestTag)?.startsWith("auto-pause") != true)
        }
        compose.onNodeWithTag("history-playback-pause").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("1.0 s after each group").assertExists()
        compose.onNodeWithTag("history-rewind-pause").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("0.1 s after each rewound group").assertExists()
        compose.onNodeWithTag("show-plays-time").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Show plays and combat").assertExists()
        compose.onNodeWithText("1.2 s each").assertExists()
        compose.runOnIdle { HistoryPlaybackPreferences(AppPlatform.current, "settings").showPlaysMillis = 0 }
        compose.onNode(hasText("Off") and hasAnyAncestor(hasTestTag("playback-settings"))).assertExists()
        compose.runOnIdle { HistoryPlaybackPreferences(AppPlatform.current, "settings").showPlaysMillis = 9_000 }
        compose.onNodeWithText("3.0 s each").assertExists()
    }



    @Test fun catalogSectionReservesNoSpaceAndKeepsPreferencesStableInPortrait() = assertCatalogGeometry()

    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi")
    fun catalogSectionReservesNoSpaceAndKeepsPreferencesStableInLandscape() = assertCatalogGeometry()

    private fun assertCatalogGeometry() {
        val models = ViewModelStore()
        val catalog = CatalogViewModel(AppServices.current.catalogController()).also {
            models.put("catalog", it)
            it.seed(CatalogUiState(installed = CatalogMetadata("2026-10-25", "1", records = 100), ready = true))
        }
        try {
            compose.setContent { MaterialTheme { SettingsContent(catalog, "gap") {} } }
            // Preferences sit above Card records, so catalog changes never move them; About & account follows it.
            val fixedSections = listOf("playback-settings", "card-size")
                .associateWith { compose.onNodeWithTag(it).getUnclippedBoundsInRoot() }
            fun controls() = compose.onNodeWithTag("catalog-controls").getUnclippedBoundsInRoot()
            assertTrue(controls().top > fixedSections.getValue("playback-settings").bottom)
            assertTrue(compose.onNodeWithTag("app-version").getUnclippedBoundsInRoot().top > controls().bottom)
            assertTrue(compose.onNodeWithTag("logout").getUnclippedBoundsInRoot().top > compose.onNodeWithTag("app-version").getUnclippedBoundsInRoot().bottom,
                "Log out is last")
            // Installed: header and installed line only, with no reserved status space.
            compose.onNodeWithTag("catalog-status").assertDoesNotExist()
            assertTrue(controls().bottom - controls().top <= 120.dp, "Header and installed line only: ${controls()}")
            for (state in listOf(
                CatalogUiState(),
                CatalogUiState(progress = CatalogProgress(CatalogPhase.DOWNLOADING, 1, 10)),
                CatalogUiState(failed = true),
                catalog.state,
            )) {
                compose.runOnIdle { catalog.seed(state) }
                fixedSections.forEach { (tag, bounds) -> assertEquals(bounds, compose.onNodeWithTag(tag).getUnclippedBoundsInRoot(), tag) }
                if (!state.ready && !state.failed) compose.onNodeWithTag("catalog-status").assertExists()
                if (state.failed) compose.onNodeWithTag("catalog-retry").assertExists()
            }
        } finally { models.clear() }
    }
}
