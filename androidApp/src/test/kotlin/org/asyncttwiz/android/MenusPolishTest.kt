package org.asyncttwiz.android

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import org.asyncttwiz.protocol.GameLifecycle
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class MenusPolishTest {
    @get:Rule val compose = createComposeRule()
    private val viewModels = ViewModelStore()
    @After fun clearViewModels() { viewModels.clear() }

    @Test fun settingsIsReachableFromMainMenuAndDrawerAndOwnsCardRecordsAndLogout() {
        val fixtures = ScreenFixtures()
        fixtures.auth.seed(AuthState(restoring = false, account = ScreenFixtures.alex))
        val catalog = CatalogViewModel(ApplicationProvider.getApplicationContext()).also {
            viewModels.put("catalog", it)
            it.seed(CatalogUiState(loaded = true))
        }
        compose.setContent { ScreenTheme { AuthenticatedShell(fixtures.auth, false, catalog, decks = fixtures.library) } }
        compose.onNodeWithTag("menu-SETTINGS").performScrollTo().performClick()
        compose.onNodeWithText("Card records").assertIsDisplayed()
        compose.onNodeWithText("Log out").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { fixtures.auth.navigate(ShellDestination.DECKS) }
        compose.onNodeWithText("Card records").assertDoesNotExist()
        compose.onNodeWithText("Refresh saved decks").assertDoesNotExist()
        compose.onNodeWithContentDescription("Open navigation").performClick()
        compose.onNodeWithText("Log out").assertDoesNotExist()
        compose.onNodeWithText("Settings").performClick()
        assertEquals(ShellDestination.SETTINGS, fixtures.auth.state.destination)
        compose.onNodeWithText("Card records").assertIsDisplayed()
    }

    @Test fun quickContinueEntryShowsOpponentAndActivityAndOpensItsGame() {
        val fixtures = ScreenFixtures()
        val game = fixtures.lobby.state.games.first().copy(lifecycle = GameLifecycle.ACTIVE, attentionHolder = "a")
        fixtures.lobby.seed(LobbyState(games = listOf(game), loaded = true))
        compose.setContent { ScreenTheme {
            ShellContent(ShellDestination.MENU, true, {}, {}, null, null, null, fixtures.lobby, "a")
        } }
        compose.onNodeWithTag("quick-continue-${game.id}").assert(hasText(game.gameName))
            .assert(hasText("vs ${game.opponent("a").username} · In play · ${gameLastActivity(game)}"))
            .assert(hasText("Your move")).performClick()
        assertEquals(game.id, fixtures.lobby.state.selected)
    }

    @Test fun settingsLogoutUsesProvidedAccountAction() {
        var logouts = 0
        compose.setContent { ScreenTheme { SettingsContent(null) { logouts++ } } }
        compose.onNodeWithText("Log out").performScrollTo().performClick()
        assertEquals(1, logouts)
    }
}
