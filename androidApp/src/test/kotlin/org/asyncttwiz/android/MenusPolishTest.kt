package org.asyncttwiz.android

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import org.asyncttwiz.protocol.GameLifecycle
import org.asyncttwiz.protocol.InvitationState
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
        compose.onNodeWithText("Card records").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Log out").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { fixtures.auth.navigate(ShellDestination.DECKS) }
        compose.onNodeWithText("Card records").assertDoesNotExist()
        compose.onNodeWithText("Refresh saved decks").assertDoesNotExist()
        compose.onNodeWithContentDescription("Open navigation").performClick()
        compose.onNodeWithText("Log out").assertDoesNotExist()
        compose.onNodeWithText("Settings").performClick()
        assertEquals(ShellDestination.SETTINGS, fixtures.auth.state.destination)
        compose.onNodeWithText("Card records").performScrollTo().assertIsDisplayed()
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

    @Test fun incomingInvitationRowOpensGamesListWithoutSelectingUnacceptedLobby() {
        val fixtures = ScreenFixtures()
        val invitation = fixtures.lobby.state.games.first().copy(lifecycle = GameLifecycle.LOBBY,
            inviter = ScreenFixtures.riley, recipient = ScreenFixtures.alex, invitationState = InvitationState.PENDING)
        fixtures.lobby.seed(LobbyState(invitations = listOf(invitation), loaded = true))
        var destination = ShellDestination.MENU
        compose.setContent { ScreenTheme {
            ShellContent(ShellDestination.MENU, true, {}, { destination = it }, null, null, null, fixtures.lobby, "a")
        } }
        compose.onNodeWithTag("quick-continue-${invitation.id}").assert(hasText("Invitation pending", substring = true)).performScrollTo().performClick()
        assertEquals(ShellDestination.GAMES, destination)
        assertNull(fixtures.lobby.state.selected)
    }

    @Test fun outgoingInvitationRowStillOpensWaitingLobbyWithoutResponseButtons() {
        val fixtures = ScreenFixtures()
        val invitation = fixtures.lobby.state.games.first().copy(lifecycle = GameLifecycle.LOBBY,
            invitationState = InvitationState.PENDING)
        fixtures.lobby.seed(LobbyState(games = listOf(invitation), loaded = true))
        var destination = ShellDestination.MENU
        compose.setContent { ScreenTheme {
            ShellContent(ShellDestination.MENU, true, {}, { destination = it }, null, null, null, fixtures.lobby, "a")
        } }
        compose.onNodeWithContentDescription("Accept invitation").assertDoesNotExist()
        compose.onNodeWithContentDescription("Decline invitation").assertDoesNotExist()
        compose.onNodeWithTag("quick-continue-${invitation.id}").performScrollTo().performClick()
        assertEquals(ShellDestination.GAMES, destination)
        assertEquals(invitation.id, fixtures.lobby.state.selected)
    }
}
