package org.asyncttwiz.android

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.asyncttwiz.protocol.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.lang.reflect.Proxy
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit
import kotlin.test.*

/** DRF-181: the pod Games tab's Challenge button accepts the member's open challenge to you; "New challenge" still makes one. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class PodChallengeAcceptTest {
    @get:Rule val compose = createComposeRule()
    private val self = ScreenFixtures.alex
    private val other = ScreenFixtures.riley
    private val open = GameSummary("open", "Ember Gate", "2026-10-27T00:00:00Z", other, self, "round", "invite",
        InvitationState.PENDING, podId = "pod", podName = "Cube night", updatedAt = "2026-10-27T10:00:00Z")
    private val detail = PodDetail("pod", "Cube night", PodPhase.DRAFTED, other, DraftSettings(), PodSource("Cube", 540), DraftSupply(90, 540),
        listOf(PodMember(other, submitted = true), PodMember(self, submitted = true)), emptyList(), updatedAt = "2026-10-27T00:00:00Z")

    // DRF-181: the oldest acceptable challenge from that member in this pod; held ones, other pods' and your own don't count
    @Test fun theOpenChallengeIsTheOldestAcceptableOneFromThatMember() {
        val newer = open.copy(id = "newer", createdAt = "2026-10-28T00:00:00Z")
        val games = listOf(newer, open, open.copy(id = "held", createdAt = "2026-10-20T00:00:00Z", invitationHeld = true),
            open.copy(id = "elsewhere", createdAt = "2026-10-20T00:00:00Z", podId = "other"),
            open.copy(id = "mine", createdAt = "2026-10-20T00:00:00Z", inviter = self, recipient = other),
            open.copy(id = "answered", createdAt = "2026-10-20T00:00:00Z", invitationState = InvitationState.ACCEPTED))
        assertEquals("open", podOpenChallengeFrom(games, "pod", other.accountId, self.accountId)?.id)
        assertNull(podOpenChallengeFrom(games.drop(2), "pod", other.accountId, self.accountId))
        assertEquals("mine", podOpenChallengeFrom(games, "pod", self.accountId, other.accountId)?.id, "Riley's side: alex's challenge to accept")
    }

    // DRF-181, DRF-121: Challenge accepts Riley's open challenge (no new game); New challenge in its choices makes a new one
    @Test fun challengeAcceptsTheirOpenChallengeAndNewChallengeMakesAnother() {
        val worker = Worker(); val wire = Wire(); val challenges = mutableListOf<ChallengeFromPod>()
        val lobby = LobbyController(proxy<Lobbies> { name, args -> when (name) {
            "contacts" -> ContactList(emptyList())
            "games" -> GameList(if (args[1] == true) listOf(open) else emptyList())
            "challengeFromPod" -> { challenges += args[2] as ChallengeFromPod; open.copy(id = "new", inviter = self, recipient = other) }
            "close" -> Unit
            else -> error("Unexpected lobby call: $name")
        } }, { "token" }, worker, { it() }, gameConnection = wire, accountId = { self.accountId })
        try {
            lobby.refresh(); worker.drain()
            wire.listener.opened()
            wire.listener.message(GameWelcome(gameplayRulesVersion = CURRENT_GAMEPLAY_RULES_VERSION))
            val stream = wire.frames.filterIsInstance<OpenGameStream>().single().streamId
            wire.listener.message(GameStreamReady(open.id, stream, "1", emptyList()))
            wire.listener.message(GameHead(open.id, open.revision, open))
            compose.setContent { ScreenTheme { NoRipples { PodGamesTab(detail, self.accountId, lobby) } } }
            compose.onNodeWithContentDescription("Challenge: accept riley's challenge").assertIsEnabled().performClick()
            assertEquals(GameAction.AcceptInvitation, wire.frames.filterIsInstance<GameCommand>().single().action)
            assertTrue(challenges.isEmpty(), "no new game")

            compose.onNodeWithTag("pod-challenge-choices-${other.accountId}").performClick()
            compose.onNodeWithText("New challenge").assertIsDisplayed()
            captureShot("$SCREENSHOTS/draft/pod-challenge-choices.png",
                "riley has challenged alex in the pod: alex's Challenge button beside riley accepts that challenge, and its choices button, tapped, offers New challenge.")
            compose.onNodeWithText("New challenge").performClick()
            worker.drain()
            assertEquals(other.accountId, challenges.single().opponentId)
        } finally { lobby.close(); worker.drain() }
    }

    // DRF-121: with no open challenge from the member, Challenge makes a new one and there are no choices
    @Test fun withoutAnOpenChallengeChallengeMakesANewOne() {
        val fixtures = ScreenFixtures()
        fixtures.lobby.seed(LobbyState(loaded = true))
        compose.setContent { ScreenTheme { PodGamesTab(detail, self.accountId, fixtures.lobby) } }
        compose.onNodeWithContentDescription("Challenge").assertIsEnabled()
        compose.onNodeWithTag("pod-challenge-choices-${other.accountId}").assertDoesNotExist()
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
        override fun awaitTermination(timeout: Long, unit: TimeUnit) = true
    }
}
