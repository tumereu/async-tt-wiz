package org.asyncttwiz.android

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.asyncttwiz.protocol.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit
import kotlin.test.*

/** A pod challenge sent while the recipient still builds is shown to them at once, answerable only by Decline. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class HeldChallengeVisibilityTest {
    @get:Rule val compose = createComposeRule()
    private val self = ScreenFixtures.alex
    private val other = ScreenFixtures.riley
    private val held = GameSummary("held", "Held game", "2026-10-27T00:00:00Z", other, self, "round", "invite",
        InvitationState.PENDING, podId = "pod", podName = "Limited pod", updatedAt = "2026-10-27T10:00:00Z", invitationHeld = true)

    @Test fun recipientStatusAsksForTheirDeckWhileChallengerWaits() {
        assertEquals("Challenged you · submit your deck to accept", gameStatus(held, self.accountId))
        assertEquals("Waiting for alex's deck", gameStatus(held, other.accountId))
        assertEquals("Waiting for alex's deck", gameStatus(held))
        assertEquals("Invitation pending", gameStatus(held.copy(invitationHeld = false), self.accountId))
    }

    @Test fun drawerListsIncomingInvitationsNewestFirstAboveOwnMovesAndSetup() {
        fun game(id: String, hour: Int, inviter: AccountIdentity = self, recipient: AccountIdentity = other,
            state: InvitationState = InvitationState.ACCEPTED) = GameSummary(id, id, "2026-10-20T00:00:00Z", inviter, recipient,
            "round", "invite", state, updatedAt = "2026-10-27T${hour.toString().padStart(2, '0')}:00:00Z")
        val games = listOf(game("game-1", 5), game("game-2", 1), game("outgoing", 3, state = InvitationState.PENDING))
        val invitations = listOf(game("old-invite", 2, other, self, InvitationState.PENDING),
            held.copy(id = "new-held", updatedAt = "2026-10-27T09:00:00Z"))
        // Setup games come after the sent invitation (own latest move), newest first.
        assertEquals(listOf("new-held", "old-invite", "outgoing", "game-1", "game-2"),
            navigationGameRows(games, invitations, self.accountId).map { it.id })
        assertTrue(incomingInvitation(held, self.accountId))
        assertFalse(incomingInvitation(held, other.accountId))
        assertFalse(incomingInvitation(held.copy(lifecycle = GameLifecycle.CANCELLED), self.accountId))
    }

    @Test fun menuShowsHeldChallengeWithDeclineOnlyAndReleaseEnablesAccept() {
        val worker = Worker(); val wire = Wire()
        val lobby = LobbyController(proxy<Lobbies> { name, args -> when (name) {
            "contacts" -> ContactList(emptyList())
            "games" -> GameList(if (args[1] == true) listOf(held) else emptyList())
            "close" -> Unit
            else -> error("Unexpected lobby call: $name")
        } }, { "token" }, worker, { it() }, gameConnection = wire, accountId = { self.accountId })
        try {
            lobby.refresh(); worker.drain()
            wire.listener.opened()
            wire.listener.message(GameWelcome(gameplayRulesVersion = CURRENT_GAMEPLAY_RULES_VERSION))
            val stream = wire.frames.filterIsInstance<OpenGameStream>().single().streamId
            wire.listener.message(GameStreamReady(held.id, stream, "1", emptyList()))
            wire.listener.message(GameHead(held.id, held.revision, held))
            assertEquals(listOf(held.id), lobby.state.invitations.map { it.id })
            compose.setContent { ScreenTheme {
                ShellContent(ShellDestination.MENU, true, {}, {}, null, null, null, lobby, self.accountId)
            } }
            compose.onNodeWithTag("quick-continue-${held.id}").performScrollTo()
                .assert(hasText("Challenged you · submit your deck to accept", substring = true))
            compose.onNodeWithContentDescription("Accept invitation").assertIsNotEnabled()
            lobby.respond(held, true)
            assertTrue(wire.frames.filterIsInstance<GameCommand>().isEmpty())
            // The recipient's deck submission releases it into an ordinary invitation.
            val released = held.copy(invitationHeld = false, revision = "1")
            wire.listener.message(GameRevision(held.id, "0", "1", "00000000-0000-4000-8000-000000000001", other.accountId,
                GameScope(held.id, held.preparationRound), released))
            compose.onNodeWithContentDescription("Accept invitation").assertIsEnabled()
            compose.onNodeWithTag("quick-continue-${held.id}").assert(hasText("Invitation pending", substring = true))
        } finally { lobby.close(); worker.drain() }
    }

    @Test fun heldChallengeCanBeDeclinedFromTheMenu() {
        val worker = Worker(); val wire = Wire()
        val lobby = LobbyController(proxy<Lobbies> { name, args -> when (name) {
            "contacts" -> ContactList(emptyList())
            "games" -> GameList(if (args[1] == true) listOf(held) else emptyList())
            "close" -> Unit
            else -> error("Unexpected lobby call: $name")
        } }, { "token" }, worker, { it() }, gameConnection = wire, accountId = { self.accountId })
        try {
            lobby.refresh(); worker.drain()
            wire.listener.opened()
            wire.listener.message(GameWelcome(gameplayRulesVersion = CURRENT_GAMEPLAY_RULES_VERSION))
            val stream = wire.frames.filterIsInstance<OpenGameStream>().single().streamId
            wire.listener.message(GameStreamReady(held.id, stream, "1", emptyList()))
            wire.listener.message(GameHead(held.id, held.revision, held))
            compose.setContent { ScreenTheme {
                ShellContent(ShellDestination.MENU, true, {}, {}, null, null, null, lobby, self.accountId)
            } }
            compose.onNodeWithContentDescription("Decline invitation").performScrollTo().assertIsEnabled()
                .performTouchInput { click() }
            assertEquals(GameAction.DeclineInvitation, wire.frames.filterIsInstance<GameCommand>().single().action)
        } finally { lobby.close(); worker.drain() }
    }

    @Test fun drawerInvitationRowOpensGamesListWithoutSelectingIt() {
        val fixtures = ScreenFixtures()
        val game = fixtures.lobby.state.games.first().copy(lifecycle = GameLifecycle.ACTIVE)
        fixtures.lobby.seed(LobbyState(loaded = true, games = listOf(game), invitations = listOf(held)))
        fixtures.auth.seed(AuthState(restoring = false, account = self, destination = ShellDestination.DECKS, drawerOpen = true))
        compose.setContent { ScreenTheme { Navigation(fixtures.auth, true, fixtures.lobby) } }
        val rows = compose.onAllNodes(hasTestTagPrefix("navigation-game-"))
        rows[0].assert(hasTestTag("navigation-game-${held.id}"))
            .assert(hasText("Challenged you · submit your deck to accept", substring = true)).assert(hasText("Invitation"))
        rows[1].assert(hasTestTag("navigation-game-${game.id}"))
        rows[0].performClick()
        assertEquals(ShellDestination.GAMES, fixtures.auth.state.destination)
        assertFalse(fixtures.auth.state.drawerOpen)
        assertNull(fixtures.lobby.state.selected)
    }

    @Test fun podGamesTabShowsHeldChallengeWithDeclineOnly() {
        val fixtures = ScreenFixtures()
        fixtures.lobby.seed(LobbyState(loaded = true, invitations = listOf(held), connectedGames = setOf(held.id)))
        val detail = PodDetail("pod", "Limited pod", PodPhase.DRAFTED, other, DraftSettings(), PodSource("Cube", 540), DraftSupply(90, 540),
            listOf(PodMember(other, submitted = true), PodMember(self, building = true)), emptyList(), updatedAt = "2026-10-27T00:00:00Z")
        compose.setContent { ScreenTheme { PodGamesTab(detail, self.accountId, fixtures.lobby) } }
        compose.onNodeWithTag("pod-game-${held.id}").assert(hasAnyDescendant(hasText("Challenged you · submit your deck to accept")))
        compose.onNodeWithContentDescription("Accept invitation").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Decline invitation").assertIsEnabled()
    }

    private fun hasTestTagPrefix(prefix: String) = SemanticsMatcher("test tag starts with $prefix") { node ->
        node.config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.TestTag) { null }?.startsWith(prefix) == true
    }

    private inline fun <reified T> proxy(crossinline call: (String, Array<out Any?>) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, args ->
            call(method.name, args.orEmpty())
        } as T

    private class Wire : GameConnection {
        lateinit var listener: GameConnection.Listener
        val frames = mutableListOf<GameClientMessage>()
        override fun connect(token: String, listener: GameConnection.Listener) { this.listener = listener }
        override fun send(message: GameClientMessage): Boolean { frames += message; return true }
        override fun disconnect() = Unit
        override fun close() = Unit
    }

    private class Worker : AbstractExecutorService() {
        val queue = ArrayDeque<Runnable>()
        override fun execute(command: Runnable) { queue.add(command) }
        fun drain() { while (queue.isNotEmpty()) queue.removeFirst().run() }
        override fun shutdown() = Unit
        override fun shutdownNow() = mutableListOf<Runnable>()
        override fun isShutdown() = false
        override fun isTerminated() = false
        override fun awaitTermination(timeout: Long, unit: TimeUnit) = false
    }
}
