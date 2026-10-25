package org.asyncttwiz.android

import org.asyncttwiz.storage.*
import org.junit.Test
import kotlin.test.*

class CatalogPromptTest {
    private val old = CatalogMetadata("2026-10-20", "5.2.2")
    private val newer = old.copy(date = "2026-10-21", indexVersion = old.indexVersion + 1)

    @Test fun missingRecordsWaitForMetadataThenPromptOnEitherConnection() {
        val policy = CatalogPromptPolicy()
        assertFalse(policy.shouldPrompt(CatalogUiState()))
        for (metered in listOf(false, true)) {
            val state = CatalogUiState(available = newer, size = 450_000_000, metered = metered)
            assertTrue(policy.shouldPrompt(state))
            assertFalse(policy.shouldPrompt(state.copy(busy = true)))
        }
    }

    @Test fun installedAndAutoInstalledRecordsDoNotPromptButAnUninstalledUpdateDoes() {
        val policy = CatalogPromptPolicy()
        assertFalse(policy.shouldPrompt(CatalogUiState(installed = old)))
        assertFalse(policy.shouldPrompt(CatalogUiState(installed = old, available = old)))
        assertFalse(policy.shouldPrompt(CatalogUiState(installed = newer)))
        assertTrue(policy.shouldPrompt(CatalogUiState(installed = old, available = newer)))
    }

    @Test fun missingDismissalLastsOnlyThisProcessAndUpdateDismissalIsPerVersion() {
        val policy = CatalogPromptPolicy()
        val missing = CatalogUiState(available = old)
        policy.dismiss(missing)
        assertFalse(policy.shouldPrompt(missing))
        assertFalse(policy.shouldPrompt(missing.copy(available = newer)))
        assertTrue(CatalogPromptPolicy().shouldPrompt(missing))
        val update = CatalogUiState(installed = old, available = newer)
        assertTrue(policy.shouldPrompt(update))
        policy.dismiss(update)
        assertFalse(policy.shouldPrompt(update))
        assertTrue(policy.shouldPrompt(update.copy(available = newer.copy(version = "5.2.3"))))
        assertTrue(policy.shouldPrompt(update.copy(available = newer.copy(indexVersion = newer.indexVersion + 1))))
        assertTrue(CatalogPromptPolicy().shouldPrompt(update))
    }

    @Test fun progressLabelsUseCompressedMegabytesAndHandleUnknownTotals() {
        assertEquals("Downloading card records: 120 / 450 MB", CatalogProgress(CatalogPhase.DOWNLOADING, 120_000_000, 450_000_000).label())
        assertEquals("Indexing card records: 160 / 450 MB processed · 45,123 cards",
            CatalogProgress(CatalogPhase.INDEXING, 160_000_000, 450_000_000, 45_123).label())
        assertEquals("Downloading card records: 120 MB", CatalogProgress(CatalogPhase.DOWNLOADING, 120_000_000, null).label())
        assertEquals("Finishing up — verifying catalog…", CatalogProgress(CatalogPhase.FINISHING, 0, null).label())
    }

    @Test fun dailyProviderBuildsDoNotPromptForFullDownloadButPendingSetsDo() {
        val policy = CatalogPromptPolicy()
        val daily = old.copy(date = "2026-10-21")
        assertFalse(policy.shouldPrompt(CatalogUiState(installed = old, available = daily)))
        val plan = CatalogUpdatePlan(daily, listOf(CatalogSet("NEW", "2026-10-21")))
        assertTrue(policy.shouldPrompt(CatalogUiState(installed = old, available = daily, plan = plan)))
    }
}
