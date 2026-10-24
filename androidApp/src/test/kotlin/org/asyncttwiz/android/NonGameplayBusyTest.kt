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

    @Test fun loadingPillNeverMovesGamesAndFadesAway() {
        val fixtures = ScreenFixtures()
        compose.setContent { ScreenTheme { Surface(Modifier.fillMaxSize()) { GamesContent(fixtures.lobby, "a") } } }
        val before = compose.onNodeWithText("Azure Spark").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle { fixtures.lobby.seed(fixtures.lobby.state.copy(busy = true)) }
        compose.onNodeWithText("Loading games…").assertIsDisplayed()
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
        compose.runOnIdle { total = null }
        compose.onNodeWithText("Indexing card records: 160 MB processed · 45,123 cards").assertIsDisplayed()
        compose.onAllNodes(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertCountEquals(2)
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
