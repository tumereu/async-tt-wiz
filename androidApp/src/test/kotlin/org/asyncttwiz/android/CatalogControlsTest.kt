package org.asyncttwiz.android

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
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

/** Settings → Card records (docs/specs/web-client.md section 3.6). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CatalogControlsTest {
    @get:Rule val compose = createComposeRule()

    // LOB-093: Settings → Card records
    @Test fun settingsShowWhatIsInstalledHowFarAndRetry() {
        var state by androidx.compose.runtime.mutableStateOf(CatalogUiState(progress = CatalogProgress(CatalogPhase.INDEXING, 50, 100)))
        var retries = 0
        compose.setContent { MaterialTheme { CatalogControls(state) { retries++ } } }
        compose.onNodeWithText("Card records updating · 59%").assertIsDisplayed()
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
    }
}
