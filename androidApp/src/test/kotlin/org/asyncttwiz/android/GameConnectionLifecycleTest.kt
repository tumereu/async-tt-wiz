package org.asyncttwiz.android

import android.os.Looper
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.assertIsEnabled
import org.asyncttwiz.protocol.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class GameConnectionLifecycleTest {
    @get:Rule val compose = createComposeRule()

    private class Wire : GameConnection {
        lateinit var listener: GameConnection.Listener
        val frames = mutableListOf<GameClientMessage>()
        var opens = 0
        var disconnects = 0
        var closes = 0
        override fun connect(token: String, listener: GameConnection.Listener) { opens++; this.listener = listener }
        override fun send(message: GameClientMessage): Boolean { frames.add(message); return true }
        override fun disconnect() { disconnects++ }
        override fun close() { closes++ }
    }

    private class Worker : AbstractExecutorService() {
        override fun execute(command: Runnable) = command.run()
        override fun shutdown() = Unit
        override fun shutdownNow() = mutableListOf<Runnable>()
        override fun isShutdown() = false
        override fun isTerminated() = false
        override fun awaitTermination(timeout: Long, unit: TimeUnit) = false
    }

    private class Owner : AutoCloseable {
        val data = GameplayFixture()
        private val play = data.initial.let { initial ->
            var id = 500
            val regions = initial.board.players.flatMap { player ->
                PlayZone.entries.filter { it != PlayZone.STACK }.map { zone ->
                    initial.board.containers.find { it.accountId == player.accountId && it.zone == zone }
                        ?: PlayContainer(data.id(id++), zone, player.accountId)
                }
            } + PlayContainer(data.id(id), PlayZone.STACK)
            initial.copy(board = initial.board.copy(containers = regions))
        }
        val wire = Wire()
        var signedIn = true
        val api = object : Lobbies {
            override suspend fun search(token: String, query: String) = UserSearch(emptyList())
            override suspend fun contacts(token: String, after: String?) = ContactList(emptyList())
            override suspend fun games(token: String, invitations: Boolean, after: String?) =
                GameList(if (invitations) emptyList() else listOf(data.game))
            override suspend fun head(token: String, id: String) = data.game
            override suspend fun add(token: String, input: AddContact) = data.game.recipient
            override suspend fun create(token: String, input: CreateInvitation) = data.game
            override fun close() = Unit
        }
        val lobby = LobbyController(api, { if (signedIn) "A".repeat(43) else null }, Worker(), { it() },
            gameConnection = wire, accountId = { data.actor })
        // Same ownership boundary as AuthViewModel: neither the screen nor its effects own this.
        val maintenance = GameConnectionMaintenance({ signedIn }, lobby::sync)
        fun accept(generation: String = "1") {
            wire.listener.opened()
            wire.listener.message(GameWelcome())
            val stream = wire.frames.last { it is OpenGameStream || it is ResumeGameStream }.let {
                if (it is OpenGameStream) it.streamId else (it as ResumeGameStream).streamId
            }
            wire.listener.message(GameStreamReady(data.game.id, stream, generation))
            wire.listener.message(data.head(play))
            assertTrue(data.game.id in lobby.state.connectedGames)
        }
        override fun close() { maintenance.close(); lobby.close() }
    }

    @Test fun disposingAndRecreatingTableUiDoesNotCloseReopenOrLoseAcceptedConnection() {
        val owner = Owner()
        var visible by mutableStateOf(true)
        var landscape by mutableStateOf(false)
        var disposed = 0
        compose.runOnIdle { owner.lobby.open(owner.data.game.id); owner.accept() }
        val disconnects = owner.wire.disconnects // Initial credential binding is not a rotation.
        val frames = owner.wire.frames.toList()
        try {
            compose.setContent {
                if (visible) {
                    DisposableEffect(Unit) { onDispose { disposed++ } }
                    MaterialTheme { WaitingLobby(owner.lobby, owner.data.actor, null, landscape) }
                }
            }
            compose.runOnIdle { visible = false }
            compose.runOnIdle {
                assertEquals(1, disposed)
                assertEquals(0, owner.wire.closes)
                landscape = true
                owner.lobby.foreground() // MainActivity.onResume on the replacement UI.
                visible = true
            }
            compose.onNodeWithText("Reconnecting…").assertDoesNotExist()
            compose.runOnIdle {
                assertEquals(1, owner.wire.opens)
                assertEquals(disconnects, owner.wire.disconnects)
                assertEquals(frames, owner.wire.frames, "Rotation must not reopen or resume a stream")
                assertEquals(owner.data.game.id, owner.lobby.state.selected)
                assertNotNull(owner.lobby.state.play)
                assertTrue(owner.data.game.id in owner.lobby.state.connectedGames)
            }
        } finally { compose.runOnIdle { visible = false; owner.close() } }
        assertEquals(1, owner.wire.closes, "Only the retained owner may close the socket")
    }

    @Test fun retainedMaintenanceReconnectsWhileNoUiExistsAndStopsOnOwnerClose() {
        val owner = Owner()
        try {
            owner.lobby.open(owner.data.game.id); owner.accept()
            owner.wire.listener.disconnected()
            assertFalse(owner.data.game.id in owner.lobby.state.connectedGames)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(6))
            assertEquals(2, owner.wire.opens, "Reconnect must not wait for a composable to return")
            owner.accept("2")
            owner.signedIn = false
            owner.lobby.clear()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(6))
            assertEquals(2, owner.wire.opens, "Logout must not reconnect")
        } finally { owner.close() }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(6))
        assertEquals(2, owner.wire.opens)
        assertEquals(1, owner.wire.closes)
    }
    @Test fun resumedDisconnectedTableKeepsNormalControlsWithoutReadOnlyLabelOrReconnectDialog() {
        val owner = Owner()
        compose.runOnIdle { owner.lobby.open(owner.data.game.id); owner.accept() }
        try {
            compose.setContent { MaterialTheme { WaitingLobby(owner.lobby, owner.data.actor, null) } }
            compose.runOnIdle { owner.wire.listener.disconnected(); owner.lobby.foreground() }
            compose.onNodeWithText("Reconnecting…").assertDoesNotExist()
            compose.onNodeWithText("Disconnected · read-only").assertDoesNotExist()
            compose.onNodeWithContentDescription("Game controls").assertIsEnabled()
            compose.runOnIdle { assertNotNull(owner.lobby.state.play); assertNull(owner.lobby.state.connectionMessage) }
        } finally { compose.runOnIdle { owner.close() } }
    }
}
