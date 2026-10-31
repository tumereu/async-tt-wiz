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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class MenuInvitationTest {
    @get:Rule val compose = createComposeRule()
    private val self = ScreenFixtures.alex
    private val other = ScreenFixtures.riley
    private val game = GameSummary("game", "Incoming game", "2026-10-27T00:00:00Z", other, self,
        "round", "invite", InvitationState.PENDING)
    private val invitation = PodInvitation("pod", "Incoming pod", other, "2026-10-27T00:00:00Z")

    // LOB-016
    @Test fun gameAcceptSendsCapturedInvitationActionWithoutRowNavigation() = gameResponse(true)
    @Test fun gameDeclineSendsCapturedInvitationActionWithoutRowNavigation() = gameResponse(false)

    private fun gameResponse(accept: Boolean) {
        val worker = Worker()
        val wire = Wire()
        val api = proxy<Lobbies> { name, _ -> when (name) {
            "contacts" -> ContactList(emptyList())
            "games" -> GameList(listOf(game))
            "close" -> Unit
            else -> error("Unexpected lobby call: $name")
        } }
        val lobby = LobbyController(api, { "token" }, worker, { it() }, gameConnection = wire, accountId = { self.accountId })
        var destination = ShellDestination.MENU
        try {
            lobby.refresh(); worker.drain()
            wire.listener.opened()
            wire.listener.message(GameWelcome(gameplayRulesVersion = CURRENT_GAMEPLAY_RULES_VERSION))
            val stream = wire.frames.filterIsInstance<OpenGameStream>().single().streamId
            wire.listener.message(GameStreamReady(game.id, stream, "1", emptyList()))
            wire.listener.message(GameHead(game.id, game.revision, game))
            compose.setContent { ScreenTheme {
                ShellContent(ShellDestination.MENU, true, {}, { destination = it }, null, null, null, lobby, self.accountId)
            } }
            compose.onNodeWithContentDescription(if (accept) "Accept invitation" else "Decline invitation")
                .performScrollTo().assertIsEnabled().performTouchInput { click() }
            val command = wire.frames.filterIsInstance<GameCommand>().single()
            assertEquals(if (accept) GameAction.AcceptInvitation else GameAction.DeclineInvitation, command.action)
            assertEquals(GameScope(game.id, game.preparationRound, game.invitationId), command.scope)
            assertNull(lobby.state.selected)
            assertEquals(ShellDestination.MENU, destination)
            compose.onNodeWithContentDescription("Accept invitation").assertIsNotEnabled()
            compose.onNodeWithContentDescription("Decline invitation").assertIsNotEnabled()
        } finally { lobby.close(); worker.drain() }
    }

    // LOB-017
    @Test fun podAcceptUsesControllerResponse() = podResponse(true)
    @Test fun podDeclineUsesControllerResponse() = podResponse(false)

    private fun podResponse(accept: Boolean) {
        val worker = Worker()
        val calls = mutableListOf<Pair<String, String>>()
        val detail = PodDetail(invitation.podId, invitation.name, PodPhase.SETUP, other, DraftSettings(),
            PodSource("Cube", 540), DraftSupply(90, 540), listOf(PodMember(other), PodMember(self)), emptyList(),
            updatedAt = invitation.invitedAt)
        val api = proxy<Pods> { name, args -> when (name) {
            "accept" -> { calls += name to args[1] as String; detail }
            "decline" -> { calls += name to args[1] as String; PodAcknowledged(invitation.podId) }
            "detail" -> detail
            "list" -> PodList(emptyList(), emptyList())
            "close" -> Unit
            else -> error("Unexpected pod call: $name")
        } }
        val pods = PodController(api, proxy<Cubes> { _, _ -> Unit }, { "token" }, worker, { it() })
        pods.seed(PodState(loaded = true, invitations = listOf(invitation)))
        var destination = ShellDestination.MENU
        try {
            compose.setContent { ScreenTheme {
                ShellContent(ShellDestination.MENU, true, {}, { destination = it }, null, null, null, null, self.accountId, pods = pods)
            } }
            compose.onNodeWithContentDescription(if (accept) "Accept invitation" else "Decline invitation")
                .performScrollTo().assertIsEnabled().performTouchInput { click() }
            assertTrue(calls.isEmpty()) // Network work is queued, not performed in the click callback.
            assertEquals(if (accept) ShellDestination.PODS else ShellDestination.MENU, destination)
            assertNull(pods.state.selected)
            compose.onNodeWithContentDescription("Accept invitation").assertIsNotEnabled()
            compose.onNodeWithContentDescription("Decline invitation").assertIsNotEnabled()
            compose.onNodeWithTag("menu-GAMES").assertIsEnabled()
            compose.runOnIdle { worker.drain() }
            assertEquals(listOf((if (accept) "accept" else "decline") to invitation.podId), calls)
            assertEquals(if (accept) invitation.podId else null, pods.state.selected)
        } finally { pods.close(); worker.drain() }
    }

    @Test fun podInvitationRowOpensListButActivePodRowOpensPod() {
        val worker = Worker()
        val pods = PodController(proxy<Pods> { name, _ -> when (name) {
            "list" -> PodList(emptyList(), emptyList())
            "detail" -> throw PodFailure("not_found")
            "close" -> Unit
            else -> error("Unexpected pod call: $name")
        } }, proxy<Cubes> { _, _ -> Unit }, { "token" }, worker, { it() })
        val pod = PodSummary("active", "My pod", PodPhase.SETUP, self, listOf(self, other), PodAttention.NONE, 0, invitation.invitedAt)
        pods.seed(PodState(loaded = true, pods = listOf(pod), invitations = listOf(invitation)))
        var destination = ShellDestination.MENU
        try {
            compose.setContent { ScreenTheme {
                ShellContent(ShellDestination.MENU, true, {}, { destination = it }, null, null, null, null, self.accountId, pods = pods)
            } }
            compose.onNodeWithTag("quick-pod-${invitation.podId}").performScrollTo().performTouchInput { click() }
            assertEquals(ShellDestination.PODS, destination)
            assertNull(pods.state.selected)
            assertEquals(0, pods.state.pending)
            compose.onNodeWithTag("quick-pod-${pod.id}").performScrollTo().performTouchInput { click() }
            assertEquals(pod.id, pods.state.selected)
        } finally { pods.close(); worker.drain() }
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
