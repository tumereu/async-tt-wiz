package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import java.util.UUID
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit
import kotlin.test.*

class GameChatTest {
    private fun id() = UUID.randomUUID().toString()
    private val a = AccountIdentity(id(), "A")
    private val b = AccountIdentity(id(), "B")
    private val game = GameSummary(id(), "Chat game", "2026-10-23T00:00:00Z", a, b, id(), id(),
        InvitationState.ACCEPTED, GameLifecycle.ACTIVE, playId = id())
    private val play = PlayState(game.id, game.playId!!, game.preparationRound, id(), PlayRoot(id(), id()), id(),
        BoardState(listOf(PlayPlayer(a.accountId), PlayPlayer(b.accountId)), emptyList(), emptyList(), TurnState(a.accountId)),
        emptyList(), emptyList(), InitialStarter(StarterChoice(a.accountId), a.accountId)).let { it.copy(currentNodeId = it.root.id) }
    private class Wire : GameConnection {
        lateinit var listener: GameConnection.Listener
        val frames = mutableListOf<GameClientMessage>()
        val reads = mutableListOf<Triple<String, ReadChat?, (ChatReadState?) -> Unit>>()
        override fun chatRead(gameId: String, input: ReadChat?, complete: (ChatReadState?) -> Unit): Boolean {
            reads.add(Triple(gameId, input, complete)); return true
        }
        override fun connect(token: String, listener: GameConnection.Listener) { this.listener = listener }
        override fun send(message: GameClientMessage): Boolean { frames.add(message); return true }
        override fun disconnect() = Unit
        override fun close() = Unit
    }
    private inner class Fixture {
        val wire = Wire(); var token: String? = "token"
        val posts = ArrayDeque<() -> Unit>()
        val personal = GameChat()
        var opened: String? = game.id
        var time = 0L
        val session = GameSession(wire, { token }, { posts.add(it) }, {}, {}, { _, _ -> }, {},
            now = { time }, personalChat = personal, openedGame = { opened })
        fun drain() { while (posts.isNotEmpty()) posts.removeFirst()() }
        fun receive(message: GameServerMessage) { wire.listener.message(message); drain() }
        fun connect(statuses: List<GameRequestStatus> = emptyList(), generation: String = "1", chat: List<ChatMessage> = emptyList(), revision: String = "0",
            current: GameSummary = game, headPlay: PlayState = play) {
            session.watch(listOf(game)); wire.listener.opened(); drain(); receive(GameWelcome(gameplayRulesVersion = CURRENT_GAMEPLAY_RULES_VERSION))
            val stream = wire.frames.last { it is OpenGameStream || it is ResumeGameStream }.let {
                if (it is OpenGameStream) it.streamId else (it as ResumeGameStream).streamId }
            receive(GameStreamReady(game.id, stream, generation, statuses))
            receive(GameHead(game.id, revision, current.copy(revision = revision), play = headPlay, chat = chat))
        }
        fun command() = wire.frames.filterIsInstance<GameCommand>().last()
        fun result(c: GameCommand, accepted: Boolean = true) = GameResult(c.requestId, c.scope,
            if (accepted) "accepted" else "rejected", if (accepted) null else "inapplicable", "1", if (accepted) "1" else null)
        fun message(c: GameCommand, revision: String = "1") = ChatMessage(c.requestId, game.id, game.playId!!, a.accountId,
            (c.action as GameAction.SendChat).text, revision, "2026-10-23T00:00:00Z")
    }
    @Test fun hundredUnopenedGamesNeverPollCursorsAndOnlyOpenedGameOrPendingWritesArePolled() {
        val f = Fixture(); f.opened = null
        val games = listOf(game) + (1..99).map { game.copy(id = id()) }
        try {
            f.session.watch(games); f.wire.listener.opened(); f.drain()
            f.receive(GameWelcome(gameplayRulesVersion = CURRENT_GAMEPLAY_RULES_VERSION))
            for (current in games) {
                val stream = f.wire.frames.filterIsInstance<OpenGameStream>().single { it.gameId == current.id }
                f.receive(GameStreamReady(current.id, stream.streamId, "1"))
                f.receive(GameHead(current.id, "0", current))
            }
            repeat(3) { f.time += 10_000; f.session.tick() }
            assertTrue(f.wire.reads.isEmpty())
            f.opened = game.id; f.session.tick()
            assertEquals(listOf(game.id), f.wire.reads.map { it.first })
            f.wire.reads.last().third(ChatReadState(game.id, "0", "0", 0)); f.drain()
            f.opened = games[1].id; f.time += 10_000; f.session.tick()
            assertEquals(listOf(game.id, games[1].id), f.wire.reads.map { it.first })
            f.wire.reads.last().third(ChatReadState(games[1].id, "0", "0", 0)); f.drain()
            f.opened = null; f.time += 10_000; f.session.tick()
            assertEquals(2, f.wire.reads.size)
        } finally { f.session.close() }
    }
    @Test fun failedCursorWriteCannotBeRetriedByVisibilityOrHeadUntilDeadlineAndKeepsNewestCursor() {
        val f = Fixture()
        val messages = (1..3).map { ChatMessage(id(), game.id, game.playId!!, b.accountId, "$it", "$it", "date") }
        try {
            f.connect(chat = messages, revision = "3")
            f.wire.reads.single().third(ChatReadState(game.id, "0", "3", 3)); f.drain()
            f.session.presentedChat(game.id, setOf(messages[0].id))
            assertEquals(ReadChat(messages[0].id), f.wire.reads.last().second)
            f.wire.reads.last().third(null); f.drain()
            f.time = 999
            repeat(5) {
                f.session.presentedChat(game.id, setOf(messages[2].id))
                f.session.tick()
                f.receive(GameHead(game.id, "3", game.copy(revision = "3"), play = play, chat = messages))
            }
            assertEquals(2, f.wire.reads.size)
            // Navigating away must retain and retry the pending write, not poll other games.
            f.opened = null; f.time = 1_000; f.session.tick()
            assertEquals(3, f.wire.reads.size)
            assertEquals(ReadChat(messages[2].id), f.wire.reads.last().second)
            f.wire.reads.last().third(ChatReadState(game.id, "3", "3", 0)); f.drain()
            f.time = 20_000; f.session.tick()
            assertEquals(3, f.wire.reads.size)
            assertNull(f.personal.pendingRead(game.id))
        } finally { f.session.close() }
    }
    @Test fun visibilityReportsCoalesceRetryOnReconnectAndFenceOldAccountCallbacks() {
        val f = Fixture()
        val messages = (1..3).map { ChatMessage(id(), game.id, game.playId!!, b.accountId, "$it", "$it", "date") }
        fun unread() = f.personal.presentation(game.id, a.accountId, f.session.chat(game.id), emptyList()).unread
        f.connect(chat = messages, revision = "3")
        assertEquals(1, f.wire.reads.size); assertNull(f.wire.reads.single().second)
        f.session.presentedChat(game.id, setOf(messages[0].id))
        f.session.presentedChat(game.id, setOf(messages[2].id))
        assertEquals(1, f.wire.reads.size) // one read in flight, only latest intent retained
        f.wire.reads[0].third(ChatReadState(game.id, "0", "3", 3)); f.drain()
        assertEquals(ReadChat(messages[2].id), f.wire.reads[1].second)
        assertEquals(0, unread())
        f.wire.reads[1].third(null); f.drain()
        f.time = 1_001; f.session.tick()
        assertEquals(ReadChat(messages[2].id), f.wire.reads[2].second)
        val oldCallback = f.wire.reads[2].third
        f.wire.listener.disconnected(); f.drain()
        f.connect(generation = "2", chat = messages, revision = "3")
        assertEquals(ReadChat(messages[2].id), f.wire.reads.last().second)
        f.wire.reads.last().third(ChatReadState(game.id, "3", "3", 0)); f.drain()
        assertNull(f.personal.pendingRead(game.id)); assertEquals(0, unread())
        f.session.clear(); f.token = "other-account"
        oldCallback(ChatReadState(game.id, "3", "3", 0)); f.drain()
        assertEquals(3, f.personal.presentation(game.id, b.accountId, messages.map { it.copy(author = a.accountId) }, emptyList()).unread)
    }
    @Test fun anotherDevicesReadCursorRefreshesDotWithoutAnyGameplayRevision() {
        val f = Fixture()
        val message = ChatMessage(id(), game.id, game.playId!!, b.accountId, "incoming", "1", "date")
        f.connect(chat = listOf(message), revision = "1")
        f.wire.reads.single().third(ChatReadState(game.id, "0", "1", 1)); f.drain()
        assertEquals(1, f.personal.presentation(game.id, a.accountId, f.session.chat(game.id), emptyList()).unread)
        f.time = 10_001; f.session.tick()
        assertNull(f.wire.reads.last().second)
        f.wire.reads.last().third(ChatReadState(game.id, "1", "1", 0)); f.drain()
        assertEquals(0, f.personal.presentation(game.id, a.accountId, f.session.chat(game.id), emptyList()).unread)
        assertNull(f.personal.pendingRead(game.id))
    }
    @Test fun incompatibleReconnectCannotCaptureGameplayAgainstRetainedBoard() {
        val f = Fixture()
        try {
            f.connect()
            assertNotNull(f.session.acceptedPlay(game))
            f.wire.listener.disconnected(); f.drain(); f.session.tick()
            f.wire.listener.opened(); f.drain()
            f.receive(GameWelcome(gameplayRulesVersion = CURRENT_GAMEPLAY_RULES_VERSION - 1))
            val frames = f.wire.frames.toList()
            assertFalse(f.session.gameplay(game, a.accountId, PlayAction.AdjustLife(a.accountId, "1")))
            assertFalse(f.session.historyControl(game, a.accountId, GameAction.Undo))
            assertFalse(f.session.sendChat(game, "must not queue"))
            assertFalse(f.session.pending(game.id))
            assertEquals(frames, f.wire.frames)
            assertEquals("Server update pending", f.session.error)
        } finally { f.session.close() }
    }
    @Test fun pipelinedChatUsesExistingFifoAndUncertaintyRetainsOriginalWithoutDuplicateEcho() {
        val f = Fixture(); assertFalse(f.session.sendChat(game, "offline")); f.connect()
        assertTrue(f.session.sendChat(game, " first\n ")); val first = f.command()
        assertTrue(f.session.sendChat(game, "correction")); val second = f.command()
        assertEquals(GameScope(game.id, playId = game.playId), first.scope)
        assertTrue(GameWire.valid(first)); assertEquals(2, f.session.outgoingChat(game.id).size)
        assertTrue(f.session.chat(game.id).isEmpty())
        f.receive(GameReceipt(first.requestId, first.scope, "1"))
        f.wire.listener.disconnected(); f.drain()
        assertFalse(f.session.sendChat(game, "not queued offline"))
        f.connect(listOf(GameRequestStatus(first.requestId, "pending"), GameRequestStatus(second.requestId, "notAdmitted")), "2")
        assertEquals(listOf(first, second, second.copy(generation = "2")), f.wire.frames.filterIsInstance<GameCommand>())
        f.receive(f.result(first)); assertTrue(f.session.chat(game.id).isEmpty())
        assertEquals(2, f.session.outgoingChat(game.id).size) // Accepted result alone is not an accepted read.
        val message = f.message(first)
        f.receive(GameRevision(game.id, "0", "1", first.requestId, a.accountId, first.scope, game.copy(revision = "1"), chatMessage = message))
        f.receive(GameRevision(game.id, "0", "1", first.requestId, a.accountId, first.scope, game.copy(revision = "1"), chatMessage = message))
        assertEquals(listOf(message), f.session.chat(game.id)); assertEquals(listOf(second), f.session.outgoingChat(game.id).map { it.command })
        f.receive(f.result(second, false)); assertNotNull(f.session.outgoingChat(game.id).single().failure)
        assertEquals(listOf(message), f.session.chat(game.id))
    }
    @Test fun reconnectAfterLostResultUsesAcceptedHeadAndNoResendAndOldHeadCannotEraseChat() {
        val f = Fixture(); f.connect(); f.session.sendChat(game, "original"); val original = f.command()
        f.wire.listener.disconnected(); f.drain()
        val message = f.message(original)
        f.connect(listOf(GameRequestStatus(original.requestId, "final", f.result(original))), "2", listOf(message), "1")
        assertEquals(listOf(original), f.wire.frames.filterIsInstance<GameCommand>())
        assertEquals(listOf(message), f.session.chat(game.id)); assertTrue(f.session.outgoingChat(game.id).isEmpty())
        f.receive(GameHead(game.id, "0", game)); assertEquals(listOf(message), f.session.chat(game.id))
        // Personal UI clears and old socket deliveries cannot install or submit under another account.
        val old = f.wire.listener; f.session.clear(); f.token = "other-account"
        old.message(GameHead(game.id, "1", game.copy(revision = "1"), chat = listOf(message))); f.drain()
        assertTrue(f.session.chat(game.id).isEmpty()); assertTrue(f.session.outgoingChat(game.id).isEmpty())
        assertFalse(f.session.sendChat(game, "old"))
    }
    @Test fun headBeforeResultRetiresPendingPresentationAndStaleIncarnationNeverRetargets() {
        val f = Fixture(); f.connect(); f.session.sendChat(game, "captured"); val original = f.command()
        val message = f.message(original)
        f.receive(GameHead(game.id, "1", game.copy(revision = "1"), chat = listOf(message)))
        assertTrue(f.session.outgoingChat(game.id).isEmpty()); assertTrue(f.session.pending(game.id))
        f.receive(f.result(original))
        assertTrue(f.session.sendChat(game, "second unresolved request")); val unresolved = f.command()
        val returned = game.copy(lifecycle = GameLifecycle.LOBBY, playId = null, preparationRound = id(), revision = "2")
        f.receive(GameHead(game.id, "2", returned, preparation = PreparationRules.empty(returned), chat = listOf(message)))
        val next = returned.copy(lifecycle = GameLifecycle.ACTIVE, playId = id(), revision = "3")
        val nextPlay = play.copy(id = next.playId!!, preparationRound = next.preparationRound)
        f.receive(GameHead(game.id, "3", next, play = nextPlay, chat = listOf(message)))
        assertEquals(nextPlay, f.session.acceptedPlay(next))
        assertFalse(f.session.sendChat(game, "must not silently rebind"))
        f.wire.listener.disconnected(); f.drain()
        f.connect(listOf(GameRequestStatus(unresolved.requestId, "notAdmitted")), "2", listOf(message), "3", next, nextPlay)
        assertEquals(unresolved.copy(generation = "2"), f.command()); assertEquals(game.playId, f.command().scope.playId)
        f.receive(f.result(unresolved, false)); assertEquals(listOf(message), f.session.chat(game.id))
    }
    @Test fun productionControllerPreservesTableNavigationDraftAndUnreadAndFencesClearedPanelCallbacks() {
        val wire = Wire(); var actor = a.accountId; var token = "owner-token"
        val worker = object : AbstractExecutorService() {
            override fun execute(command: Runnable) = command.run()
            override fun shutdown() = Unit
            override fun shutdownNow() = mutableListOf<Runnable>()
            override fun isShutdown() = false
            override fun isTerminated() = false
            override fun awaitTermination(timeout: Long, unit: TimeUnit) = false
        }
        val api = object : Lobbies {
            override suspend fun search(token: String, query: String) = UserSearch(emptyList())
            override suspend fun contacts(token: String, after: String?) = ContactList(listOf(b))
            override suspend fun games(token: String, invitations: Boolean, after: String?) = GameList(if (invitations) emptyList() else listOf(game))
            override suspend fun head(token: String, id: String) = game
            override suspend fun add(token: String, input: AddContact) = b
            override suspend fun create(token: String, input: CreateInvitation) = game
            override fun close() = Unit
        }
        val controller = LobbyController(api, { token }, worker, { it() }, gameConnection = wire, accountId = { actor })
        fun connect(chat: List<ChatMessage>) {
            wire.listener.opened(); wire.listener.message(GameWelcome(gameplayRulesVersion = CURRENT_GAMEPLAY_RULES_VERSION))
            val stream = wire.frames.filterIsInstance<OpenGameStream>().last()
            wire.listener.message(GameStreamReady(game.id, stream.streamId, "1"))
            wire.listener.message(GameHead(game.id, "1", game.copy(revision = "1"), play = play, chat = chat))
        }
        val incoming = ChatMessage(id(), game.id, game.playId!!, b.accountId, "incoming", "1", "date")
        try {
            controller.open(game.id); connect(listOf(incoming)); val viewEpoch = controller.chatViewEpoch
            assertEquals(1, controller.state.chat.unread)
            val beforeViewFrames = wire.frames.toList()
            controller.openChat(); assertEquals(1, controller.state.chat.unread)
            controller.composeChat("draft\ntext"); controller.back()
            assertFalse(controller.state.chatOpen); assertEquals(game.id, controller.state.selected)
            assertEquals("draft\ntext", controller.state.chat.draft!!.text)
            assertEquals(beforeViewFrames, wire.frames) // Viewing and draft edits send no frames.
            controller.openChat(); controller.presentedChat(game.id, viewEpoch, setOf(incoming.id))
            assertEquals(0, controller.state.chat.unread)
            controller.sendChat(); val original = wire.frames.filterIsInstance<GameCommand>().single()
            assertEquals("draft\ntext", (original.action as GameAction.SendChat).text)
            assertNull(controller.state.chat.draft); assertEquals(1, controller.state.chat.outgoing.size)
            controller.composeChat("next draft"); val old = wire.listener
            controller.clear(); actor = b.accountId; token = "other-token"
            old.message(GameHead(game.id, "3", game.copy(revision = "3"), chat = listOf(incoming)))
            assertEquals(LobbyState(), controller.state)
            controller.open(game.id)
            val nowIncoming = incoming.copy(id = id(), author = a.accountId)
            connect(listOf(nowIncoming)); controller.openChat()
            controller.presentedChat(game.id, viewEpoch, setOf(nowIncoming.id))
            assertEquals(1, controller.state.chat.unread); assertNull(controller.state.chat.draft)
            assertTrue(controller.state.chat.outgoing.isEmpty())
            controller.back(); assertEquals(game.id, controller.state.selected)
            controller.back(); assertNull(controller.state.selected); assertEquals(listOf(game.id), controller.state.games.map { it.id })
        } finally { controller.close() }
    }
}
