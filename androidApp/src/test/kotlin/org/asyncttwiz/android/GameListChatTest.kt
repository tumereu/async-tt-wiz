package org.asyncttwiz.android

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
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

/** LOB-148, LOB-173: a game's chat opens from the games list's row menu, which counts its unread messages. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class GameListChatTest {
    @get:Rule val compose = createComposeRule()
    private val self = ScreenFixtures.alex
    private val other = ScreenFixtures.riley
    private val game = GameSummary("game", "Azure Spark", "2026-10-27T00:00:00Z", self, other, "round", "invite",
        InvitationState.ACCEPTED, GameLifecycle.ACTIVE, revision = "4", playId = "play", updatedAt = "2026-10-27T10:00:00Z")
    private val messages = listOf(
        ChatMessage("m1", game.id, "play", self.accountId, "Your move?", "2", "2026-10-30T18:00:00Z"),
        ChatMessage("m2", game.id, "play", other.accountId, "Thinking, one sec", "3", "2026-10-31T09:01:00Z"),
        ChatMessage("m3", game.id, "play", other.accountId, "Ok, done. Your turn", "4", "2026-10-31T09:12:00Z"))

    private inner class Fixture(val game: GameSummary = this@GameListChatTest.game, chat: List<ChatMessage> = messages) : AutoCloseable {
        val worker = Worker(); val wire = Wire()
        val lobby = LobbyController(proxy<Lobbies> { name, args -> when (name) {
            "contacts" -> ContactList(emptyList())
            "games" -> GameList(if (args[1] == true) emptyList() else listOf(game))
            "close" -> Unit
            else -> error("Unexpected lobby call: $name")
        } }, { "token" }, worker, { it() }, gameConnection = wire, accountId = { self.accountId })
        init {
            lobby.refresh(); worker.drain()
            wire.listener.opened()
            wire.listener.message(GameWelcome(gameplayRulesVersion = CURRENT_GAMEPLAY_RULES_VERSION))
            val stream = wire.frames.filterIsInstance<OpenGameStream>().single().streamId
            wire.listener.message(GameStreamReady(game.id, stream, "1", emptyList()))
            wire.listener.message(GameHead(game.id, game.revision, game, chat = chat))
        }
        override fun close() { lobby.close(); worker.drain() }
    }

    // LOB-148, LOB-173, LOB-026, LOB-067: a game not open asks no read state until its row's menu opens; then the menu counts the other player's
    // unread messages, and its Chat opens the game's chat without opening the game, reading what shows
    @Test fun theRowMenusChatCountsUnreadAndOpensTheChatWithoutTheGame() = Fixture().use { f ->
        assertTrue(f.wire.reads.isEmpty(), "a game not open asks nothing")
        assertNull(f.lobby.state.chatUnread[game.id])
        f.wire.state = ChatReadState(game.id, readThrough = "2", throughRevision = "4", unread = 2)
        compose.setContent { ScreenTheme { NoRipples {
            GameDeletionDialogs(f.lobby)
            GameListChat(f.lobby, self.accountId)
        } } }
        f.lobby.gameContext(game)
        assertEquals(listOf(null), f.wire.reads.map { it.second })
        assertEquals(mapOf(game.id to 2), f.lobby.state.chatUnread)
        compose.onNodeWithText("Chat · 2 unread").assertIsDisplayed()
        captureShot("$SCREENSHOTS/chat/games-list-row-menu.png",
            "alex held the Azure Spark row of the games list: its menu offers the game's chat with riley's 2 unread messages, and Delete game.")

        f.wire.state = ChatReadState(game.id, readThrough = "4", throughRevision = "4", unread = 0)
        compose.onNodeWithText("Chat · 2 unread").performClick()
        compose.waitForIdle()
        assertEquals(game.id, f.lobby.state.listChat)
        assertNull(f.lobby.state.selected, "the game isn't opened")
        compose.onNodeWithText("Ok, done. Your turn").assertIsDisplayed()
        assertEquals(ReadChat("m3"), f.wire.reads.last().second, "the messages showing are read")
        assertNull(f.lobby.state.chatUnread[game.id])
        // LOB-067, LOB-164: each message's time, and a date line above the first of each day (the tests' clock: 31 Oct, noon UTC)
        compose.onNodeWithText("Yesterday").assertIsDisplayed()
        compose.onNodeWithText("Today").assertIsDisplayed()
        compose.onNodeWithText(chatTime("2026-10-31T09:12:00Z")).assertIsDisplayed()
        captureShot("$SCREENSHOTS/chat/games-list-chat.png",
            "alex opened Azure Spark's chat from its row menu on the games list: the game chat with riley shows over the list, the game itself not opened; each message has its small time, under a Yesterday and a Today line.")

        // Sending from it goes to that game
        f.lobby.composeChat("Thanks!")
        f.lobby.sendChat()
        val sent = f.wire.frames.filterIsInstance<GameCommand>().single()
        assertEquals(GameAction.SendChat("Thanks!"), sent.action)
        assertEquals(game.id, sent.scope.gameId)
        compose.onNodeWithContentDescription("Close chat").performClick()
        compose.waitForIdle()
        assertNull(f.lobby.state.listChat)
        assertFalse(f.lobby.state.chatOpen)
        compose.onNodeWithText("Thanks!").assertDoesNotExist()
    }

    private val setup = game.copy(lifecycle = GameLifecycle.LOBBY, playId = null)
    private val lobbyMessages = listOf(ChatMessage("l1", setup.id, null, other.accountId, "Cube or constructed?", "4", "2026-10-31T09:12:00Z"))

    // LOB-148, LOB-026 (owner, 2026-11-22): a game in setup or preparing offers its chat in its row's menu beside Cancel game (no
    // Delete game), counting its unread messages; the chat opens over the list and sends with no play
    @Test fun aGameInSetupOffersItsChatBesideCancelGame() = Fixture(setup, lobbyMessages).use { f ->
        f.wire.state = ChatReadState(setup.id, readThrough = "0", throughRevision = "4", unread = 1)
        compose.setContent { ScreenTheme { NoRipples {
            SetupCancellationDialogs(f.lobby)
            GameDeletionDialogs(f.lobby)
            GameListChat(f.lobby, self.accountId)
        } } }
        assertTrue(f.lobby.offersListChat(setup))
        assertFalse(f.lobby.offersGameDeletion(setup))
        f.lobby.showGameContext(setup)
        compose.onNodeWithText("Chat · 1 unread").assertIsDisplayed()
        compose.onNodeWithText("Cancel game").assertIsDisplayed()
        compose.onNodeWithText("Delete game").assertDoesNotExist()
        captureShot("$SCREENSHOTS/chat/games-list-setup-row-menu.png",
            "alex held the row of Azure Spark, a game still being set up: its menu offers the game's chat with riley's unread message, and Cancel game.")
        f.wire.state = ChatReadState(setup.id, readThrough = "4", throughRevision = "4", unread = 0)
        compose.onNodeWithText("Chat · 1 unread").performClick()
        compose.waitForIdle()
        assertEquals(setup.id, f.lobby.state.listChat)
        assertNull(f.lobby.setupCancellation.context, "the menu closed")
        compose.onNodeWithText("Cube or constructed?").assertIsDisplayed()
        f.lobby.composeChat("Cube!")
        f.lobby.sendChat()
        val sent = f.wire.frames.filterIsInstance<GameCommand>().single()
        assertEquals(GameAction.SendChat("Cube!"), sent.action)
        assertEquals(GameScope(setup.id), sent.scope, "a message before the game starts has no play")
    }

    // LOB-148: with nothing unread the menu says just "Chat"
    @Test fun withNothingUnreadTheMenuSaysChat() {
        assertEquals("Chat", gameRowChatLabel(0))
        assertEquals("Chat · 1 unread", gameRowChatLabel(1))
    }

    private inline fun <reified T> proxy(crossinline call: (String, Array<out Any?>) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, args ->
            call(method.name, args.orEmpty())
        } as T

    private class Wire : GameConnection {
        lateinit var listener: GameConnection.Listener
        val frames = mutableListOf<GameClientMessage>()
        val reads = mutableListOf<Pair<String, ReadChat?>>()
        var state: ChatReadState? = null
        override fun connect(token: String, listener: GameConnection.Listener) { this.listener = listener }
        override fun send(message: GameClientMessage): Boolean { frames += message; return true }
        override fun chatRead(gameId: String, input: ReadChat?, complete: (ChatReadState?) -> Unit): Boolean {
            reads += gameId to input; complete(state); return true
        }
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
