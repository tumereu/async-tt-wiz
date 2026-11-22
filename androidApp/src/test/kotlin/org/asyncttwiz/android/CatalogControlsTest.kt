package org.asyncttwiz.android

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import org.asyncttwiz.storage.CatalogMetadata
import org.asyncttwiz.storage.CatalogPhase
import org.asyncttwiz.storage.CatalogProgress
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** Settings → Card records (LOB-096). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CatalogControlsTest {
    @get:Rule val compose = createComposeRule()

    // LOB-093: Settings → Card records
    @Test fun settingsShowWhatIsInstalledHowFarAndRetry() {
        var state by androidx.compose.runtime.mutableStateOf(CatalogUiState(progress = CatalogProgress(CatalogPhase.INDEXING, 50, 100)))
        var retries = 0
        compose.setContent { MaterialTheme { CatalogControls(state) { retries++ } } }
        compose.onNodeWithText("Card records updating · 40%").assertIsDisplayed()
        compose.onNodeWithTag("catalog-status").assertIsDisplayed()
        compose.onNodeWithTag("catalog-retry").assertDoesNotExist()
        compose.runOnIdle { state = CatalogUiState(failed = true) }
        compose.onNodeWithText("Card records couldn't be downloaded").assertIsDisplayed()
        compose.onNodeWithTag("catalog-retry").performClick()
        assertEquals(1, retries)
        compose.runOnIdle { state = CatalogUiState(CatalogMetadata("2026-10-31", "5.3.0", "x", 121853), ready = true) }
        compose.onNodeWithText("Installed: 2026-10-31 · 121,853 records").assertIsDisplayed()
        compose.onNodeWithTag("catalog-status").assertDoesNotExist()
        assertFalse(compose.onAllNodesWithText("Refresh full catalog").fetchSemanticsNodes().isNotEmpty())
        compose.onNodeWithText(CARD_RECORDS_NOT_KEPT).assertDoesNotExist()
    }

    // LOB-093 (owner 2026-11-22): the bar is one even bar, every progress bar of the app ([PlainProgressBar]): no gap after
    // what is done and no dot at its right end
    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    @Test fun theProgressBarIsEvenToItsEnd() {
        compose.setContent { MaterialTheme {
            androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.testTag("bar-box").width(300.dp)
                .background(androidx.compose.ui.graphics.Color.White).padding(16.dp)) {
                CatalogControls(CatalogUiState(progress = CatalogProgress(CatalogPhase.INDEXING, 50, 100))) {}
            }
        } }
        val bar = compose.onNodeWithTag("catalog-status").captureToImage().toPixelMap()
        val y = bar.height / 2
        val track = bar[bar.width * 3 / 4, y]
        val done = (bar.width * .4f).toInt()
        for (x in done + 3 until bar.width - 2) assertEquals(track, bar[x, y], "the track at x $x of ${bar.width}")
        compose.onNodeWithTag("bar-box").captureShot("$SCREENSHOTS/settings/card-records-progress.png",
            "Settings → Card records while they install, at 40%: one even bar, the done part and then the track to its right end, " +
                "with no gap between them and no dot at the end.")
    }

    // WEB-009: a browser that couldn't keep the card records says so under the installed line
    @Test fun recordsTheBrowserCouldntKeepSaySo() {
        compose.setContent { MaterialTheme { CatalogControls(CatalogUiState(CatalogMetadata("2026-10-31", "5.3.0", "x", 121853),
            ready = true, kept = false)) {} } }
        compose.onNodeWithText("Installed: 2026-10-31 · 121,853 records").assertIsDisplayed()
        compose.onNodeWithText("This browser couldn't keep them, so they download again on your next visit").assertIsDisplayed()
    }
}
