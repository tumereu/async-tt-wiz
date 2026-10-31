package org.asyncttwiz.android

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.asyncttwiz.protocol.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/** The Games screen, Menu quick list and drawer all list games in the one shared order. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class GameListOrderScreensTest {
    @get:Rule val compose = createComposeRule()
    private val self = ScreenFixtures.alex
    private val other = ScreenFixtures.riley
    private fun game(id: String, hour: Int, holder: String? = null, lifecycle: GameLifecycle = GameLifecycle.ACTIVE,
        state: InvitationState = InvitationState.ACCEPTED, inviter: AccountIdentity = self, recipient: AccountIdentity = other) =
        GameSummary(id, "Table $id", "2026-10-20T00:00:00Z", inviter, recipient, "round", "invite-$id", state, lifecycle,
            attentionHolder = holder, updatedAt = "2026-10-29T${hour.toString().padStart(2, '0')}:00:00Z")
    private val games = listOf(game("own", 9, other.accountId), game("their", 2, self.accountId),
        game("sent", 5, lifecycle = GameLifecycle.LOBBY, state = InvitationState.PENDING), game("setup", 8, lifecycle = GameLifecycle.LOBBY))
    private val invitation = game("invite", 1, lifecycle = GameLifecycle.LOBBY, state = InvitationState.PENDING, inviter = other, recipient = self)
    private val expected = listOf("their", "invite", "own", "sent", "setup")

    private fun fixtures() = ScreenFixtures().also {
        it.lobby.seed(LobbyState(loaded = true, games = games, invitations = listOf(invitation), connectedGames = setOf(invitation.id)))
    }

    private fun rows(prefix: String) = compose.onAllNodes(SemanticsMatcher("tag starts with $prefix") { node ->
        node.config.getOrElseNullable(SemanticsProperties.TestTag) { null }?.startsWith(prefix) == true
    }).fetchSemanticsNodes().map { it.config[SemanticsProperties.TestTag].removePrefix(prefix) }

    // LOB-022, LOB-023
    @Test fun gamesScreenShowsOneListWithTheInvitationAnsweredInPlace() {
        val fixtures = fixtures()
        compose.setContent { ScreenTheme { GamesContent(fixtures.lobby, self.accountId) } }
        assertEquals(expected, rows("quick-continue-"))
        compose.onNodeWithText("Invitations").assertDoesNotExist()
        compose.onNodeWithTag("quick-continue-invite").assert(hasAnyDescendant(hasContentDescription("Accept invitation")))
        compose.onNodeWithContentDescription("Accept invitation").assertIsEnabled()
        compose.onNodeWithTag("quick-continue-invite").performClick()
        assertEquals(null, fixtures.lobby.state.selected)
    }

    // LOB-016, LOB-022
    @Test fun menuQuickListShowsTheFirstThreeOfTheSameOrder() {
        val fixtures = fixtures()
        compose.setContent { ScreenTheme {
            ShellContent(ShellDestination.MENU, true, {}, {}, null, null, null, fixtures.lobby, self.accountId)
        } }
        assertEquals(expected.take(3), rows("quick-continue-"))
    }

    // LOB-022
    @Test fun drawerListsTheSameOrder() {
        val fixtures = fixtures()
        fixtures.auth.seed(AuthState(restoring = false, account = self, destination = ShellDestination.DECKS, drawerOpen = true))
        compose.setContent { ScreenTheme { Navigation(fixtures.auth, true, fixtures.lobby) } }
        assertEquals(expected, rows("navigation-game-"))
    }
}
