package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.*

/**
 * Scenario: a pending result suggestion does not affect the timeline. Two complete client stacks
 * (LobbyController -> GameSession -> GameplayReplica -> TableHistoryController, with the table toolkit built by the
 * production [tableToolkit] wiring) talk over a fake socket to an in-process server that applies every command with
 * HistoryContinuation.change, the same function GameService runs. Not covered here: Compose/gestures (the test calls
 * the toolkit entry points the gestures and menus call, and repeats TableScreen's history update effects), and the
 * real PostgreSQL server (see the server's GameResultLifecycleTest for that side).
 */
class ResultLockContinuationScenarioTest {
    private val data = GameplayFixture()
    private fun id(n: Int) = data.id(n)
    private val a = data.actor
    private val b = data.other
    private val game = data.game
    private val definition = data.definition.copy(faces = data.definition.faces.map { it.copy(corner = "2/3") })
    private fun container(zone: PlayZone, owner: String) = id(200 + zone.ordinal + if (owner == b) 10 else 0)
    private val libraries = mapOf(a to (40..42).map(::id), b to (50..52).map(::id))
    private val root = data.initial.copy(definitions = listOf(SubmittedDefinition(DeckJson.digest(definition), definition)),
        board = data.initial.board.copy(
            cards = libraries.flatMap { (owner, ids) -> ids.map { card -> data.initial.board.cards.single().copy(id = card,
                owner = owner, definition = DeckJson.digest(definition), containerId = container(PlayZone.LIBRARY, owner), placement = null) } },
            containers = listOf(a, b).flatMap { owner -> PlayZone.entries.filter { it != PlayZone.STACK }.map { zone ->
                PlayContainer(container(zone, owner), zone, owner, if (zone == PlayZone.LIBRARY) libraries.getValue(owner) else emptyList())
            } } + PlayContainer(id(299), PlayZone.STACK)))

    private class Wire : GameConnection {
        var listener: GameConnection.Listener? = null
        val frames = mutableListOf<GameClientMessage>()
        override fun connect(token: String, listener: GameConnection.Listener) { this.listener = listener }
        override fun send(message: GameClientMessage): Boolean { frames.add(message); return true }
        override fun disconnect() = Unit
        override fun close() = Unit
    }

    /** Authoritative tree + revision counter; delivers each accepted change to every client, then the author's result. */
    private inner class Server : AutoCloseable {
        var tree = GameplayTree(root)
        var revision = 1L
        val clients = mutableListOf<Client>()
        val outcomes = mutableMapOf<String, String>()
        private val handled = mutableSetOf<String>()
        fun summary() = game.copy(revision = revision.toString())
        fun join(account: String) = Client(this, account).also { clients += it; it.connect() }
        /** Process every command the clients have sent, in send order, until none is left ([first]'s commands arrive first). */
        fun flush(first: Client? = null) {
            while (true) {
                val next = (listOfNotNull(first) + clients.filter { it !== first }).firstNotNullOfOrNull { client ->
                    client.commands().firstOrNull { it.requestId !in handled }?.let { client to it } }
                    ?: break
                val (author, command) = next
                handled += command.requestId
                val change = HistoryContinuation.change(tree, author.account, command)
                val result = change.tree
                if (result == null) {
                    outcomes[command.requestId] = change.reason ?: "noOp"
                    author.message(GameResult(command.requestId, command.scope, if (change.reason == null) "noOp" else "rejected",
                        change.reason, "1"))
                } else {
                    val before = tree; tree = result
                    val base = revision++
                    outcomes[command.requestId] = "accepted"
                    val update = GameRevision(game.id, base.toString(), revision.toString(), command.requestId, author.account,
                        command.scope, summary(), treeDelta = TreeSync.delta(before, result))
                    clients.forEach { it.message(update) }
                    author.message(GameResult(command.requestId, command.scope, "accepted", null, "1", revision.toString()))
                }
                clients.forEach { it.settle() }
            }
            clients.forEach { it.sync() }
        }
        fun board(nodeId: String = tree.currentNodeId) = assertNotNull(tree.stateAt(nodeId)).board
        override fun close() = clients.forEach { it.close() }
    }

    private inner class Client(private val server: Server, val account: String) : AutoCloseable {
        val wire = Wire()
        private val posts = LinkedBlockingQueue<() -> Unit>()
        private val worker = Executors.newSingleThreadExecutor()
        private val api = object : Lobbies {
            override suspend fun contacts(token: String, after: String?) = ContactList(emptyList())
            override suspend fun games(token: String, invitations: Boolean, after: String?) =
                GameList(if (invitations) emptyList() else listOf(game))
            override suspend fun head(token: String, id: String) = game
            override suspend fun search(token: String, query: String) = UserSearch(emptyList())
            override suspend fun add(token: String, input: AddContact): AccountIdentity = error("not used")
            override suspend fun create(token: String, input: CreateInvitation): GameSummary = error("not used")
            override fun close() = Unit
        }
        val lobby = LobbyController(api, { "A".repeat(43) }, worker, { posts.add(it) }, gameConnection = wire, accountId = { account })
        val history get() = lobby.history
        lateinit var toolkit: TableToolkitController
        fun commands() = wire.frames.filterIsInstance<GameCommand>()
        fun message(message: GameServerMessage) { requireNotNull(wire.listener).message(message) }
        val play get() = assertNotNull(toolkit.current())

        /** Run posted main-thread work until [done] holds and nothing more arrives for a moment. */
        fun settle(done: () -> Boolean = { true }) {
            val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
            var quiet = 0
            while (true) {
                val task = posts.poll(10, TimeUnit.MILLISECONDS)
                if (task != null) { task(); quiet = 0; continue }
                if (done() && ++quiet >= 5) return
                assertTrue(System.nanoTime() < end, "Timed out waiting for the client")
            }
        }
        fun connect() {
            lobby.open(game.id); settle { wire.listener != null }
            requireNotNull(wire.listener).opened(); settle()
            message(GameWelcome(minor = GAME_PROTOCOL_MINOR, gameplayRulesVersion = CURRENT_GAMEPLAY_RULES_VERSION))
            settle { wire.frames.any { it is OpenGameStream } }
            val stream = wire.frames.filterIsInstance<OpenGameStream>().last().streamId
            message(GameStreamReady(game.id, stream, "1"))
            message(GameHead(game.id, server.revision.toString(), server.summary(), play = server.tree.stateAt(server.tree.currentNodeId)))
            settle { game.id in lobby.state.connectedGames && lobby.state.play != null && lobby.displayTree() != null }
            sync()
            toolkit = tableToolkit(lobby, lobby.history, assertNotNull(lobby.state.detail), account, root.id)
            assertTrue(toolkit.enabled())
        }
        /** What TableScreen's effects do on every accepted change: index the tree, then show the position. */
        fun sync() {
            settle { lobby.state.acceptedPlay?.currentNodeId == server.tree.currentNodeId }
            val display = assertNotNull(lobby.displayTree())
            history.update(display, "${lobby.state.detail?.revision}/${lobby.state.play?.currentNodeId}", lobby.state.detail, account,
                accepted = lobby.retainedTree() ?: display, indexed = lobby.retainedHistory())
            settle { history.playback?.tree?.sharedHeadId == display.currentNodeId }
            history.playback?.takeIf { it.livePanelReveal }?.finishPanelReveal()
            history.displayPosition(); settle()
        }
        /** Open the timeline at [node], as tapping it in the strip does. */
        fun review(node: String) {
            history.select(node); history.displayPosition()
            settle { history.view?.cursor == node }
            assertEquals(HistoryMode.PLAYBACK, history.playback?.position?.mode)
        }
        /** Press Rewind and let Auto run (rewind to the split point, then the new line) until live. */
        fun rewind() {
            history.current()
            val playback = assertNotNull(history.playback)
            repeat(1_000) {
                if (playback.position.mode == HistoryMode.LIVE) return@repeat
                playback.next(automatic = true); playback.finishAnimation(playback.position.cursorId)
            }
            assertEquals(HistoryMode.LIVE, playback.position.mode)
            history.displayPosition(); settle()
        }
        fun draw(advance: Boolean): Boolean {
            val play = play
            val targets = assertNotNull(toolkit.table.targets(play, account))
            val top = play.board.containers.single { it.id == targets.libraryId }.cards.first()
            return toolkit.perform(TableActionIntent.Draw(TableActionScope(play.gameId, play.id), targets, top, advance))
        }
        fun drag(card: String, column: Int): Boolean = toolkit.handle(TableIntent.Move(TableScope(card, listOf(card)),
            TableDestination.Grid(container(PlayZone.BATTLEFIELD, account), column, 0), BasicTableController.sources(play, listOf(card))))
        fun tap(card: String): Boolean = toolkit.handle(TableIntent.Rotate(TableScope(card, listOf(card)), true, toggle = true))
        fun menu(action: PlayAction): Boolean = toolkit.handle(TableIntent.Submit(action, null))
        override fun close() { lobby.close(); worker.shutdown(); assertTrue(worker.awaitTermination(10, TimeUnit.SECONDS)) }
    }

    @Test fun continuingBeforeASuggestionBranchesAwayAndBothPlayersKeepPlayingWhileTheOldBranchStaysLocked() = Server().use { server ->
        val alice = server.join(a)
        val bob = server.join(b)
        // Alice draws (table action), plays the card (drag) and suggests her win (table menu Ending).
        assertTrue(alice.draw(advance = false)); server.flush()
        assertTrue(alice.drag(id(40), 0)); server.flush()
        val beforeSuggestion = server.tree.currentNodeId
        alice.toolkit.ending(TableActionIntent.Ending(TableActionScope(game.id, root.id), EndingTool.SUGGEST_WIN))
        assertNull(alice.toolkit.error); server.flush()
        val suggestion = server.tree.currentNodeId
        assertNotNull(server.board(suggestion).resultSuggestion)

        // At the live end both players are locked: toolkit, drag, tap and menu actions are refused and nothing is sent.
        for (client in listOf(alice, bob)) {
            assertTrue(client.lobby.resultLocked()); assertFalse(client.toolkit.enabled())
            val sent = client.commands().size
            assertFalse(client.draw(advance = false))
            assertFalse(client.menu(PlayAction.AdjustLife(client.account, "-1")))
            assertFalse(client.lobby.gameplay(PlayAction.AdjustLife(client.account, "-1")), "the session enforces the lock too")
            assertEquals(sent, client.commands().size)
        }
        assertFalse(alice.tap(id(40)))

        // In the timeline: the suggestion's own point stays locked; a point before it does not.
        bob.review(suggestion)
        assertTrue(bob.lobby.resultLocked())
        bob.review(beforeSuggestion)
        assertFalse(bob.lobby.resultLocked())
        assertFalse(bob.toolkit.enabled(), "a reviewed point is read-only until Continue here")
        assertTrue(bob.history.requestContinuation(beforeSuggestion))
        assertFalse(bob.lobby.resultLocked()); assertTrue(bob.toolkit.enabled())

        // Bob plays several actions before the server answers the first (the new branch is still only predicted):
        // a table action batch (draw + next step), a drag, a tap and a menu action.
        val sentBefore = bob.commands().size
        assertTrue(bob.draw(advance = true))
        assertTrue(bob.history.provisional)
        assertFalse(bob.lobby.resultLocked(), "the predicted new branch has no suggestion")
        assertTrue(bob.toolkit.enabled())
        assertTrue(bob.drag(id(50), 0))
        assertTrue(bob.tap(id(50)))
        assertTrue(bob.menu(PlayAction.AdjustLife(b, "-1")))
        val branchCommands = bob.commands().drop(sentBefore)
        assertEquals(4, branchCommands.size)
        assertEquals(Continuation(beforeSuggestion, true), branchCommands.first().continuation)
        server.flush()
        assertEquals(listOf("accepted"), branchCommands.map { server.outcomes[it.requestId] }.distinct())

        // The new branch is the shared current board: no suggestion, Bob's moves are there, nobody is locked.
        val branch = server.board()
        assertNull(branch.resultSuggestion)
        assertTrue(branch.cards.single { it.id == id(50) }.tapped)
        assertEquals(container(PlayZone.BATTLEFIELD, b), branch.cards.single { it.id == id(50) }.containerId)
        assertNotEquals(branch.players.single { it.accountId == b }.life, server.board(beforeSuggestion).players.single { it.accountId == b }.life)
        // The suggestion simply stays on the old branch.
        assertNotNull(server.board(suggestion).resultSuggestion)
        assertFalse(generateSequence(server.tree.nodes[server.tree.currentNodeId]) { server.tree.nodes[it.parentId] }
            .any { it.id == suggestion }, "the new branch does not contain the suggestion")
        // Bob's own branch keeps him live; Alice, live at the suggestion, is stopped there to rewind.
        assertEquals(HistoryMode.LIVE, bob.history.playback?.position?.mode)
        val alicePlayback = assertNotNull(alice.history.playback)
        assertEquals(HistoryPosition(suggestion, HistoryMode.PLAYBACK, false, suggestion), alicePlayback.position)
        assertTrue(alicePlayback.branched)
        assertFalse(alice.toolkit.enabled(), "read-only until the rewind reaches the live game")
        alice.rewind()
        for (client in listOf(alice, bob)) {
            assertNull(client.history.armed)
            assertEquals(server.tree.currentNodeId, client.lobby.state.acceptedPlay?.currentNodeId)
            assertFalse(client.lobby.resultLocked()); assertTrue(client.toolkit.enabled())
        }

        // Both keep playing normally on the new branch.
        val sentAfter = alice.commands().size + bob.commands().size
        assertTrue(alice.tap(id(40)))
        assertTrue(alice.menu(PlayAction.AdjustLife(a, "-2")))
        server.flush()
        assertTrue(bob.draw(advance = false))
        assertTrue(bob.drag(id(51), 1))
        server.flush()
        val later = alice.commands().size + bob.commands().size
        assertEquals(4, later - sentAfter)
        assertTrue(server.outcomes.values.all { it == "accepted" }, server.outcomes.toString())
        assertTrue(server.board().cards.single { it.id == id(40) }.tapped)
        assertEquals(container(PlayZone.BATTLEFIELD, b), server.board().cards.single { it.id == id(51) }.containerId)
        assertNull(server.board().resultSuggestion)

        // The old branch's end still includes the suggestion, so it stays locked in the timeline,
        // and the session refuses a continuation from it even if the UI gate were bypassed.
        bob.review(suggestion)
        assertTrue(bob.lobby.resultLocked())
        assertFalse(bob.toolkit.enabled())
        val sent = bob.commands().size
        assertFalse(bob.lobby.gameplay(PlayAction.AdjustLife(b, "-1"), continuation = Continuation(suggestion, true)))
        assertEquals(sent, bob.commands().size)
    }

    /** Owner rule 2026-10-30: only a deliberate branch changes the active line; a move that raced with one is refused. */
    @Test fun aMoveMadeJustAsTheOpponentContinuesFromAnEarlierPointIsRefusedNotMovedToTheirNewLine() = Server().use { server ->
        val alice = server.join(a)
        val bob = server.join(b)
        fun life(board: BoardState) = board.players.single { it.accountId == a }.life
        assertTrue(alice.menu(PlayAction.AdjustLife(a, "-1"))); server.flush()
        val split = server.tree.currentNodeId
        assertTrue(bob.menu(PlayAction.SetLife(a, "30"))); server.flush()
        val oldHead = server.tree.currentNodeId

        // Bob deliberately continues from the split point; before Alice hears of it she acts on what she sees.
        bob.review(split)
        assertTrue(bob.history.requestContinuation(split))
        assertTrue(bob.menu(PlayAction.AdjustLife(b, "-1")))
        assertTrue(bob.menu(PlayAction.AdjustLife(b, "-1")), "his next action on his still-unconfirmed branch")
        assertTrue(alice.menu(PlayAction.AdjustLife(a, "-1")))
        val move = alice.commands().last()
        assertEquals(server.tree.selectionKey, move.expectedSelection)
        alice.sync()
        assertEquals("29", life(alice.play.board), "her prediction on the line she sees")
        server.flush(first = bob)

        // Bob's branch is the live game; Alice's move was refused, not played there or anywhere else.
        assertEquals("branched", server.outcomes[move.requestId])
        assertEquals(listOf("accepted"), bob.commands().takeLast(2).map { server.outcomes[it.requestId] }.distinct())
        assertTrue(server.tree.nodes.values.none { it.requestId == move.requestId })
        val branchHead = server.tree.currentNodeId
        assertNotEquals(oldHead, branchHead)
        assertEquals("19", life(server.board()))
        // Her prediction is gone from the live board, she gets a calm note, and she is paused where she was.
        assertEquals(branchHead, alice.lobby.state.acceptedPlay?.currentNodeId)
        assertEquals("19", life(assertNotNull(alice.lobby.state.play).board))
        assertEquals("30", life(alice.play.board), "the table shows the paused point, without her refused move")
        assertEquals("Your opponent went back to an earlier point — your last action wasn't applied.", alice.lobby.state.connectionMessage)
        val alicePlayback = assertNotNull(alice.history.playback)
        assertEquals(HistoryPosition(oldHead, HistoryMode.PLAYBACK, false, oldHead), alicePlayback.position)
        assertTrue(alicePlayback.branched)
        assertEquals(HistoryMode.LIVE, bob.history.playback?.position?.mode)
        assertEquals("19", life(bob.play.board))
        alice.rewind()
        assertEquals(branchHead, alice.history.playback?.position?.cursorId)

        // Both keep playing on the live line.
        assertTrue(alice.menu(PlayAction.AdjustLife(a, "-2")))
        assertEquals(server.tree.selectionKey, alice.commands().last().expectedSelection)
        server.flush()
        assertEquals("accepted", server.outcomes[alice.commands().last().requestId])
        assertEquals(branchHead, server.tree.nodes.getValue(server.tree.currentNodeId).parentId)
        assertEquals("17", life(server.board()))
        for (client in listOf(alice, bob)) assertEquals("17", life(client.play.board))
    }

}
