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

    // SYS-023
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

    @Test fun messageWithoutProgressKeepsItsSpinner() {
        compose.setContent { ScreenTheme { FloatingBusyPill("Loading games…") } }
        compose.onAllNodes(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertCountEquals(1)
    }

    // LOB-096 (docs/specs/web-client.md section 3.6): while the card records install, controls that read them are greyed
    // with "Card records updating · NN%"; the rest of the screen works and no busy pill covers it
    @Test fun whileCardRecordsInstallTheirControlsAreGreyedWithTheLine() {
        val fixtures = ScreenFixtures()
        fixtures.auth.seed(AuthState(restoring = false, account = ScreenFixtures.alex, destination = ShellDestination.GAMES))
        val catalog = CatalogViewModel(AppServices.current.catalogController()).also { viewModels.put("catalog", it) }
        catalog.seed(CatalogUiState(progress = CatalogProgress(CatalogPhase.INDEXING, 50, 100)))
        compose.setContent { ScreenTheme { AuthenticatedShell(fixtures.auth, false, catalog, lobby = fixtures.lobby) } }
        compose.onNodeWithText("Card records updating · 40%").assertIsDisplayed()
        compose.onNodeWithTag("new-game").assertIsNotEnabled()
        compose.onNodeWithText("Azure Spark").assertIsDisplayed()
        compose.onAllNodesWithTag("floating-busy-pill").assertCountEquals(0)
        val game = fixtures.lobby.state.games.first { it.gameName == "Azure Spark" }
        compose.onNodeWithTag("quick-continue-${game.id}").assertIsNotEnabled()
        // Failed: the line says so with Retry
        compose.runOnIdle { catalog.seed(CatalogUiState(failed = true)) }
        compose.onNodeWithText("Card records couldn't be downloaded").assertIsDisplayed()
        compose.onNodeWithText("Retry").assertIsDisplayed()
        // Installed: everything works
        compose.runOnIdle { catalog.seed(CatalogUiState(ready = true)) }
        compose.onNodeWithTag("card-records-note").assertDoesNotExist()
        compose.onNodeWithTag("new-game").assertIsEnabled()
        compose.onNodeWithTag("quick-continue-${game.id}").assertIsEnabled()
    }

    // SYS-023
    @Test fun onlyInitialForegroundLoadOfEmptyGamesShowsLoading() {
        val fixtures = ScreenFixtures()
        fixtures.lobby.seed(LobbyState(busy = true))
        compose.setContent { ScreenTheme { GamesContent(fixtures.lobby, "a") } }
        compose.onNodeWithText("Loading games…").assertIsDisplayed()
        compose.runOnIdle { fixtures.lobby.seed(LobbyState(busy = true, backgroundLoading = true)) }
        compose.onNodeWithText("Loading games…").assertDoesNotExist()
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
