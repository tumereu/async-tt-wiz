package org.asyncttwiz.android

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import org.asyncttwiz.storage.*
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class NonGameplayBusyTest {
    @get:Rule val compose = createComposeRule()
    private val viewModels = ViewModelStore()
    @After fun clearViewModels() { viewModels.clear() }

    @Test fun refreshKeepsGamesStillAndShowsNoLoadingIndicator() {
        val fixtures = ScreenFixtures()
        compose.setContent { ScreenTheme { Surface(Modifier.fillMaxSize()) { GamesContent(fixtures.lobby, "a") } } }
        val before = compose.onNodeWithText("Azure Spark").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle { fixtures.lobby.seed(fixtures.lobby.state.copy(busy = true)) }
        compose.onNodeWithText("Loading games…").assertDoesNotExist()
        compose.onAllNodesWithTag("floating-busy-pill").assertCountEquals(0)
        assertEquals(before, compose.onNodeWithText("Azure Spark").fetchSemanticsNode().boundsInRoot)
        compose.runOnIdle { fixtures.lobby.seed(fixtures.lobby.state.copy(busy = false)) }
        compose.onNodeWithText("Loading games…").assertDoesNotExist()
        assertEquals(before, compose.onNodeWithText("Azure Spark").fetchSemanticsNode().boundsInRoot)
    }

    @Test fun catalogPillUsesStructuredDeterminateProgressAndUnknownTotalFallback() {
        var total by mutableStateOf<Long?>(450_000_000)
        compose.setContent { ScreenTheme {
            val progress = org.asyncttwiz.storage.CatalogProgress(org.asyncttwiz.storage.CatalogPhase.INDEXING,
                160_000_000, total, 45_123)
            ScreenBusyOverlay(progress.label(), progress) { Text("Content remains usable") }
        } }
        compose.onNodeWithText("Indexing card records: 160 / 450 MB processed · 45,123 cards").assertIsDisplayed()
        compose.onAllNodes(hasProgressBarRangeInfo(ProgressBarRangeInfo(160f / 450f, 0f..1f))).assertCountEquals(1)
        compose.onAllNodes(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertCountEquals(0)
        compose.runOnIdle { total = null }
        compose.onNodeWithText("Indexing card records: 160 MB processed · 45,123 cards").assertIsDisplayed()
        compose.onAllNodes(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertCountEquals(2)
        compose.runOnIdle { total = 0 }
        compose.onAllNodes(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertCountEquals(2)
        compose.runOnIdle { total = 450_000_000 }
        compose.onAllNodes(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertCountEquals(0)
    }

    @Test fun messageWithoutProgressKeepsItsSpinner() {
        compose.setContent { ScreenTheme { FloatingBusyPill("Loading games…") } }
        compose.onAllNodes(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertCountEquals(1)
    }

    @Test fun shellCatalogChecksStaySilentAndOnlyTransfersSuppressScreenPills() {
        val fixtures = ScreenFixtures()
        fixtures.auth.seed(AuthState(restoring = false, account = ScreenFixtures.alex, destination = ShellDestination.GAMES))
        fixtures.lobby.seed(fixtures.lobby.state.copy(busy = true))
        val catalog = CatalogViewModel(ApplicationProvider.getApplicationContext()).also { viewModels.put("catalog", it) }
        val checking = CatalogUiState(busy = true, status = "Checking card records…")
        catalog.seed(checking)
        compose.setContent { ScreenTheme { AuthenticatedShell(fixtures.auth, false, catalog, lobby = fixtures.lobby) } }
        compose.onNodeWithText("Checking card records…").assertDoesNotExist()
        compose.onNodeWithText("Loading games…").assertDoesNotExist()
        compose.onAllNodesWithTag("floating-busy-pill").assertCountEquals(0)
        val before = compose.onNodeWithText("Azure Spark").fetchSemanticsNode().boundsInRoot
        for (phase in CatalogPhase.entries) {
            val progress = CatalogProgress(phase, 160_000_000, 450_000_000, 45_123)
            compose.runOnIdle { catalog.seed(checking.copy(status = progress.label(), progress = progress)) }
            compose.onNodeWithText(progress.label()).assertIsDisplayed()
            compose.onNodeWithText("Loading games…").assertDoesNotExist()
            compose.onAllNodesWithTag("floating-busy-pill").assertCountEquals(1)
            assertEquals(before, compose.onNodeWithText("Azure Spark").fetchSemanticsNode().boundsInRoot)
        }
        compose.runOnIdle { catalog.seed(checking) }
        compose.onNodeWithText("Checking card records…").assertDoesNotExist()
        compose.onNodeWithText("Loading games…").assertDoesNotExist()
        compose.runOnIdle { fixtures.lobby.seed(fixtures.lobby.state.copy(busy = false)) }
        compose.onAllNodesWithTag("floating-busy-pill").assertCountEquals(0)
    }

    @Test fun onlyInitialForegroundLoadOfEmptyGamesShowsLoading() {
        val fixtures = ScreenFixtures()
        fixtures.lobby.seed(LobbyState(busy = true))
        compose.setContent { ScreenTheme { GamesContent(fixtures.lobby, "a") } }
        compose.onNodeWithText("Loading games…").assertIsDisplayed()
        compose.runOnIdle { fixtures.lobby.seed(LobbyState(busy = true, backgroundLoading = true)) }
        compose.onNodeWithText("Loading games…").assertDoesNotExist()
    }

    @Test fun automaticCatalogTransfersStaySilentButErrorsRemainVisible() {
        val fixtures = ScreenFixtures()
        fixtures.auth.seed(AuthState(restoring = false, account = ScreenFixtures.alex, destination = ShellDestination.SETTINGS))
        val catalog = CatalogViewModel(ApplicationProvider.getApplicationContext()).also { viewModels.put("catalog", it) }
        val progress = CatalogProgress(CatalogPhase.DOWNLOADING, 10, 100)
        catalog.seed(CatalogUiState(busy = true, status = progress.label(), progress = progress, background = true))
        compose.setContent { ScreenTheme { AuthenticatedShell(fixtures.auth, false, catalog) } }
        compose.onNodeWithText(progress.label()).assertDoesNotExist()
        compose.onAllNodesWithTag("floating-busy-pill").assertCountEquals(0)
        compose.onAllNodes(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertCountEquals(0)
        compose.runOnIdle { catalog.seed(CatalogUiState(background = true, error = true, status = "Download failed")) }
        compose.onNodeWithText("Download failed").assertIsDisplayed()
    }

    @Test fun startupConsentHasOnlyTheTwoButtonActions() {
        var downloads = 0
        var dismissals = 0
        compose.setContent { ScreenTheme {
            CatalogStartupDialog(CatalogUiState(), download = { downloads++ }, dismiss = { dismissals++ })
        } }
        compose.onAllNodes(hasClickAction()).assertCountEquals(2)
        compose.onNodeWithContentDescription("Not now").assertDoesNotExist()
        compose.onNodeWithText("Not now").performClick()
        assertEquals(1, dismissals)
        assertEquals(0, downloads)
        compose.onNodeWithText("Download").performClick()
        assertEquals(1, downloads)
        assertEquals(1, dismissals)
    }

    @Test fun drawerRetainsControllerTruthAndItsPaneDuringExitAnimation() {
        val fixtures = ScreenFixtures()
        fixtures.auth.seed(AuthState(restoring = false, account = ScreenFixtures.alex))
        compose.setContent { ScreenTheme { AuthenticatedShell(fixtures.auth, false) } }
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { fixtures.auth.drawer(true) }
        // Let AnimatedVisibility measure its off-screen panel before advancing the timed slide.
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(300)
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Close navigation").assertIsDisplayed()
        assertTrue(fixtures.auth.state.drawerOpen)
        compose.onNodeWithContentDescription("Close navigation").performClick()
        assertFalse(fixtures.auth.state.drawerOpen)
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(100)
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Navigation menu")).assertExists()
        compose.mainClock.advanceTimeBy(200)
        compose.onNodeWithContentDescription("Close navigation").assertDoesNotExist()
    }

    @Test fun drawerBackTakesPriorityOverImportDraftAndScrimAlsoDismisses() {
        val fixtures = ScreenFixtures()
        fixtures.auth.seed(AuthState(restoring = false, account = ScreenFixtures.alex, destination = ShellDestination.DECKS))
        fixtures.importer.open()
        lateinit var back: OnBackPressedDispatcher
        compose.setContent { ScreenTheme {
            back = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            AuthenticatedShell(fixtures.auth, false, deckImport = fixtures.importer)
        } }
        compose.runOnIdle { fixtures.auth.drawer(true) }
        compose.onNodeWithContentDescription("Close navigation").assertIsDisplayed()
        compose.runOnIdle { back.onBackPressed() }
        compose.waitForIdle()
        assertFalse(fixtures.auth.state.drawerOpen)
        assertTrue(fixtures.importer.state.active)
        compose.runOnIdle { fixtures.auth.drawer(true) }
        // Tap the scrim outside the drawer panel, not its covered center.
        compose.onNodeWithContentDescription("Close navigation menu").performTouchInput { click(percentOffset(.95f, .5f)) }
        compose.waitForIdle()
        assertFalse(fixtures.auth.state.drawerOpen)
        assertTrue(fixtures.importer.state.active)
    }
}
