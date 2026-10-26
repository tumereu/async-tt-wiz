package org.asyncttwiz.server.game

import java.net.InetAddress
import java.net.ServerSocket
import java.net.URI
import java.net.http.*
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CompletionStage
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlinx.serialization.encodeToString
import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.ServiceSettings
import org.asyncttwiz.server.SpringService
import org.asyncttwiz.server.database.FoundationMigrations
import org.asyncttwiz.server.database.OwnedPostgres
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class GameplayTest {
    private val json = GameWire.json
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()
    private fun port() = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1")).use { it.localPort }
    private fun request(settings: ServiceSettings, method: String, path: String, token: String? = null, body: String? = null): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(URI("http://127.0.0.1:${settings.publicPort()}$path")).timeout(Duration.ofSeconds(15))
            .header("Content-Type", "application/json").method(method, body?.let(HttpRequest.BodyPublishers::ofString) ?: HttpRequest.BodyPublishers.noBody())
        if (token != null) builder.header("Authorization", "Bearer $token")
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }
    private inline fun <reified T> post(settings: ServiceSettings, path: String, input: T, token: String? = null) =
        request(settings, "POST", path, token, json.encodeToString(input))
    private fun account(settings: ServiceSettings, name: String): SessionCreated {
        assertEquals(201, post(settings, "/accounts", RegisterAccount(name, "$name@example.invalid", "synthetic password")).statusCode())
        return json.decodeFromString(post(settings, "/sessions", CreateSession("$name@example.invalid", "synthetic password")).body())
    }
    private inner class Socket(settings: ServiceSettings, token: String) : WebSocket.Listener, AutoCloseable {
        private val queue = LinkedBlockingQueue<GameServerMessage>(); private val saved = mutableListOf<GameServerMessage>()
        private val text = StringBuilder()
        private val socket = client.newWebSocketBuilder().header("Authorization", "Bearer $token").buildAsync(
            URI("ws://127.0.0.1:${settings.publicPort()}/v1/socket"), this).get(10, TimeUnit.SECONDS)
        override fun onOpen(webSocket: WebSocket) { webSocket.request(1) }
        override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*>? {
            text.append(data)
            if (last) { queue.add(json.decodeFromString<GameServerMessage>(text.toString())); text.setLength(0) }
            webSocket.request(1); return null
        }
        fun send(message: GameClientMessage) { socket.sendText(json.encodeToString<GameClientMessage>(message), true).get(5, TimeUnit.SECONDS) }
        fun message(predicate: (GameServerMessage) -> Boolean): GameServerMessage {
            saved.indexOfFirst(predicate).takeIf { it >= 0 }?.let { return saved.removeAt(it) }
            val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
            while (System.nanoTime() < end) {
                val next = queue.poll(100, TimeUnit.MILLISECONDS) ?: continue
                if (predicate(next)) return next
                saved.add(next)
            }
            error("Missing socket response, observed ${saved.map { it::class.simpleName }}")
        }
        inline fun <reified T : GameServerMessage> next(): T = message { it is T } as T
        fun result(id: String) = message { it is GameResult && it.requestId == id } as GameResult
        fun revision(id: String) = message { it is GameRevision && it.requestId == id } as GameRevision
        fun open(game: GameSummary): GameStreamReady {
            send(GameHello(gameplayId(), gameplayId())); next<GameWelcome>()
            send(OpenGameStream(game.id, gameplayId())); val stream = next<GameStreamReady>()
            send(SubscribeGame(game.id)); next<GameHead>(); return stream
        }
        override fun close() { socket.abort() }
    }

    @Test fun `real decks mulligan move tap undo undo redo through authenticated sockets and durable Postgres`() {
        OwnedPostgres().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            val settings = ServiceSettings(db.settings, URI("https://synthetic.invalid"), InetAddress.getByName("127.0.0.1"), port(),
                InetAddress.getByName("127.0.0.1"), port())
            SpringService(settings).use { service ->
                service.start()
                gameplayAwait { client.send(HttpRequest.newBuilder(URI("http://127.0.0.1:${settings.healthPort()}/health/ready")).build(),
                    HttpResponse.BodyHandlers.ofString()).statusCode() == 200 }
                val a = account(settings, "UndoAlice"); val b = account(settings, "UndoBob")
                assertEquals(200, post(settings, "/v1/contacts", AddContact(gameplayId(), b.account.accountId), a.token).statusCode())
                val game = json.decodeFromString<GameSummary>(post(settings, "/v1/invitations",
                    CreateInvitation(gameplayId(), b.account.accountId, "Undo regression"), a.token).body())
                fun deck(actor: SessionCreated): SavedDeck {
                    val definition = CardDefinition("Undo synthetic", listOf(DefinitionFace(gameplayId(), "Face", corner = "2/3")))
                    val submitted = SubmittedDefinition(DeckJson.digest(definition), definition)
                    assertEquals(200, post(settings, "/v1/definitions", SubmitDefinitions(gameplayId(), listOf(submitted)), actor.token).statusCode())
                    return json.decodeFromString(post(settings, "/v1/decks", CreateDeck(gameplayId(), "Sixty cards",
                        listOf(SavedDeckEntry(SavedDeckSection.MAINBOARD, submitted.digest, 60))), actor.token).body())
                }
                val ad = deck(a); val bd = deck(b)
                Socket(settings, a.token).use { alice -> Socket(settings, b.token).use { bob ->
                    val ast = alice.open(game); val bst = bob.open(game)
                    fun setup(socket: Socket, stream: GameStreamReady, action: GameAction): GameRevision {
                        val command = GameCommand(gameplayId(), GameScope(game.id, game.preparationRound,
                            if (action == GameAction.AcceptInvitation) game.invitationId else null), action, stream.streamId, stream.generation)
                        socket.send(command)
                        val result = socket.result(command.requestId)
                        assertEquals("accepted", result.outcome, result.reason)
                        return socket.revision(command.requestId)
                    }
                    setup(bob, bst, GameAction.AcceptInvitation)
                    setup(alice, ast, GameAction.SelectDeck(ad.deck.id, ad.deck.revision))
                    setup(bob, bst, GameAction.SelectDeck(bd.deck.id, bd.deck.revision))
                    setup(bob, bst, GameAction.SetStarter(StarterChoice(b.account.accountId)))
                    setup(alice, ast, GameAction.Ready)
                    val started = setup(bob, bst, GameAction.Ready)
                    val root = started.play!!
                    fun listed(actor: SessionCreated) = json.decodeFromString<GameList>(
                        request(settings, "GET", "/v1/games", actor.token).body()).games.single { it.id == game.id }
                    assertEquals(started.summary, listed(a))
                    assertEquals(b.account.accountId, listed(b).attentionHolder)
                    assertTrue(Instant.parse(started.summary.updatedAt) > Instant.parse(game.updatedAt))
                    assertEquals(120, root.board.cards.size)
                    assertEquals(b.account.accountId, root.board.turn.accountId)
                    val library = root.board.containers.single { it.zone == PlayZone.LIBRARY && it.accountId == b.account.accountId }
                    val hand = root.board.containers.single { it.zone == PlayZone.HAND && it.accountId == b.account.accountId }
                    val field = root.board.containers.single { it.zone == PlayZone.BATTLEFIELD && it.accountId == b.account.accountId }
                    var predicted = root
                    val originals = mutableListOf<Pair<GameCommand, PlayState>>()
                    fun send(action: PlayAction) {
                        val command = GameCommand(gameplayId(), GameScope(game.id, playId = root.id),
                            GameAction.Play(action), bst.streamId, bst.generation)
                        predicted = requireNotNull(GameplayRules.change(predicted, b.account.accountId, command).state)
                        originals.add(command to predicted)
                        bob.send(command)
                    }
                    // One prediction/request/event per mulligan, without acknowledgement barriers.
                    repeat(2) {
                        send(PlayAction.Mulligan(library.id, hand.id,
                            predicted.board.containers.single { it.id == hand.id }.cards, 7, RandomV1.encodeSeed(ByteArray(32) { it.toByte() })))
                        if (it == 0) {
                            val source = predicted.board.containers.single { it.id == hand.id }.cards.first()
                            send(PlayAction.Stack(StackAction.AddEffect(source, GameplayRules.stackDigest(predicted))))
                            val effect = predicted.board.stackEffects.single().id
                            send(PlayAction.SetProjectionNote(effect, "X=3; target opponent"))
                            assertEquals("X=3; target opponent", predicted.board.stackEffects.single().note)
                            assertEquals("", predicted.board.cards.single { card -> card.id == source }.note)
                            send(PlayAction.Stack(StackAction.RemoveEffect(effect)))
                        }
                    }
                    val drawn = predicted
                    val card = drawn.board.containers.single { it.id == hand.id }.cards.first()
                    send(PlayAction.MoveCard(card, MoveSource(hand.id), MoveDestination(field.id, MoveInsertion.EMPTY, BoardPosition(1024, 0))))
                    val moved = predicted
                    send(PlayAction.SetTapped(card, true))
                    val tapped = predicted
                    fun control(action: GameAction): GameCommand = GameCommand(gameplayId(), GameScope(game.id, playId = root.id),
                        action, bst.streamId, bst.generation).also(bob::send)
                    val undoTap = control(GameAction.Undo)
                    val undoMove = control(GameAction.Undo)
                    val redoMove = control(GameAction.Redo)
                    var accepted = TreeSync.generation(GameplayTree(root))
                    fun apply(command: GameCommand): PlayState {
                        val update = alice.revision(command.requestId)
                        assertEquals(update, bob.revision(command.requestId))
                        accepted = TreeSync.apply(accepted, update.treeDelta!!)
                        return requireNotNull(accepted.tree.stateAt(accepted.tree.currentNodeId))
                    }
                    for ((command, state) in originals) {
                        val result = bob.result(command.requestId)
                        assertEquals("accepted", result.outcome, result.reason)
                        assertEquals(state, apply(command))
                    }
                    fun verify(command: GameCommand, target: String, expected: PlayState) {
                        val result = bob.result(command.requestId)
                        assertEquals("accepted", result.outcome, result.reason)
                        assertEquals(target, result.targetNodeId)
                        assertEquals(expected, apply(command))
                        val recovered = json.decodeFromString<GameRequestStatus>(request(settings, "GET",
                            "/v1/games/${game.id}/results/${command.requestId}", b.token).body()).result
                        assertEquals(result, recovered)
                    }
                    verify(undoTap, tapped.currentNodeId, moved)
                    verify(undoMove, moved.currentNodeId, drawn)
                    assertEquals(listOf(tapped.currentNodeId, moved.currentNodeId),
                        accepted.tree.redoByAccount[b.account.accountId]!!.map { it.mutation.id })
                    verify(redoMove, moved.currentNodeId, moved)
                    assertEquals(listOf(tapped.currentNodeId), accepted.tree.redoByAccount[b.account.accountId]!!.map { it.mutation.id })
                    val beforeRetry = accepted.tree
                    bob.send(undoTap)
                    assertEquals(tapped.currentNodeId, bob.result(undoTap.requestId).targetNodeId)
                    val redoTap = control(GameAction.Redo)
                    verify(redoTap, tapped.currentNodeId, tapped)
                    assertTrue(accepted.tree.redoByAccount.isEmpty())
                    val undoAgain = control(GameAction.Undo)
                    verify(undoAgain, tapped.currentNodeId, moved)
                    assertEquals(beforeRetry.nodes, accepted.tree.nodes)
                    // A non-holder may take attention, then flip it away again. Both sockets,
                    // durable HTTP history and cold replay observe the same individual events.
                    var attentionState = moved
                    var previousUpdate = Instant.parse(listed(a).updatedAt)
                    for ((socket, stream, actor) in listOf(Triple(alice, ast, a), Triple(alice, ast, a), Triple(bob, bst, b))) {
                        val pass = GameCommand(gameplayId(), GameScope(game.id, playId = root.id),
                            GameAction.Play(PlayAction.PassAttention), stream.streamId, stream.generation)
                        val expected = GameplayRules.change(attentionState, actor.account.accountId, pass)
                        socket.send(pass)
                        val result = socket.result(pass.requestId)
                        assertEquals("accepted", result.outcome, result.reason)
                        assertEquals(expected.mutation, result.mutation)
                        attentionState = apply(pass)
                        assertEquals(expected.state, attentionState)
                        val summary = listed(a)
                        assertEquals(summary, listed(b))
                        assertEquals(attentionState.board.effectiveAttentionHolder, summary.attentionHolder)
                        assertTrue(Instant.parse(summary.updatedAt) > previousUpdate)
                        previousUpdate = Instant.parse(summary.updatedAt)
                        val history = json.decodeFromString<GameplayHistoryPage>(request(settings, "GET",
                            "/v1/games/${game.id}/plays/${root.id}/mutations?after=0", a.token).body())
                        assertEquals(expected.mutation, history.mutations.last())
                        assertEquals(attentionState, accepted.tree.copy().stateAt(accepted.tree.currentNodeId))
                    }
                } }
            }
        }
    }

    @Test fun `authenticated two socket gameplay predicts same reducer and HTTP retains concrete mutations and guarded Stack outcomes`() {
        OwnedPostgres().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            val settings = ServiceSettings(db.settings, URI("https://synthetic.invalid"), InetAddress.getByName("127.0.0.1"), port(),
                InetAddress.getByName("127.0.0.1"), port())
            SpringService(settings).use { service ->
                service.start()
                gameplayAwait { client.send(HttpRequest.newBuilder(URI("http://127.0.0.1:${settings.healthPort()}/health/ready")).build(),
                    HttpResponse.BodyHandlers.ofString()).statusCode() == 200 }
                val a = account(settings, "GameplaySocketA"); val b = account(settings, "GameplaySocketB"); val other = account(settings, "GameplayOther")
                post(settings, "/v1/contacts", AddContact(gameplayId(), b.account.accountId), a.token)
                val game = json.decodeFromString<GameSummary>(post(settings, "/v1/invitations", CreateInvitation(gameplayId(), b.account.accountId, "Socket Gameplay"), a.token).body())
                fun deck(actor: SessionCreated): SavedDeck {
                    val definition = CardDefinition("Socket retained", listOf(DefinitionFace(gameplayId(), "Face", corner = "4/6")))
                    val submitted = SubmittedDefinition(DeckJson.digest(definition), definition)
                    assertEquals(200, post(settings, "/v1/definitions", SubmitDefinitions(gameplayId(), listOf(submitted)), actor.token).statusCode())
                    return json.decodeFromString(post(settings, "/v1/decks", CreateDeck(gameplayId(), "Source", listOf(
                        SavedDeckEntry(SavedDeckSection.MAINBOARD, submitted.digest, 5))), actor.token).body())
                }
                val ad = deck(a); val bd = deck(b)
                Socket(settings, a.token).use { owner -> Socket(settings, b.token).use { recipient ->
                    val ast = owner.open(game); val bst = recipient.open(game)
                    fun setup(socket: Socket, stream: GameStreamReady, action: GameAction): GameRevision {
                        val command = GameCommand(gameplayId(), GameScope(game.id, game.preparationRound,
                            if (action == GameAction.AcceptInvitation) game.invitationId else null), action, stream.streamId, stream.generation)
                        socket.send(command); assertEquals("accepted", socket.result(command.requestId).outcome)
                        return socket.revision(command.requestId)
                    }
                    setup(owner, ast, GameAction.SelectDeck(ad.deck.id, ad.deck.revision)); setup(owner, ast, GameAction.SetStarter(StarterChoice(a.account.accountId)))
                    setup(recipient, bst, GameAction.AcceptInvitation); setup(recipient, bst, GameAction.SelectDeck(bd.deck.id, bd.deck.revision))
                    setup(owner, ast, GameAction.Ready); val started = setup(recipient, bst, GameAction.Ready)
                    val root = started.play!!; val library = root.board.containers.single { it.zone == PlayZone.LIBRARY && it.accountId == a.account.accountId }
                    val hand = root.board.containers.single { it.zone == PlayZone.HAND && it.accountId == a.account.accountId }
                    var liveTree = TreeSync.generation(GameplayTree(root))
                    fun apply(revision: GameRevision): PlayState {
                        liveTree = TreeSync.apply(liveTree, revision.treeDelta!!)
                        return liveTree.tree.stateAt(liveTree.tree.currentNodeId)!!
                    }
                    fun command(action: PlayAction, stream: GameStreamReady = ast) = GameCommand(gameplayId(), GameScope(game.id, playId = root.id),
                        GameAction.Play(action), stream.streamId, stream.generation)
                    val field = root.board.containers.single { it.zone == PlayZone.BATTLEFIELD && it.accountId == a.account.accountId }
                    val draw = command(PlayAction.MoveCard(library.cards.first(), MoveSource(library.id, edge = MoveSourceEdge.TOP),
                        MoveDestination(field.id, MoveInsertion.EMPTY, BoardPosition(0, 0))))
                    val tap = command(PlayAction.SetTapped(library.cards.first(), true))
                    val life = command(PlayAction.AdjustLife(a.account.accountId, "1"))
                    listOf(draw, tap, life).forEach(owner::send) // All sent before reading any acknowledgement.
                    var prediction = root
                    for (input in listOf(draw, tap, life)) {
                        val change = GameplayRules.change(prediction, a.account.accountId, input)
                        prediction = change.state!!
                        val result = owner.result(input.requestId); assertEquals("accepted", result.outcome)
                        assertEquals(change.mutation, result.mutation)
                        val revision = recipient.revision(input.requestId)
                        assertEquals(prediction, apply(revision)); assertEquals(change.mutation, revision.mutation)
                        assertEquals(prediction.currentGroup, revision.group)
                    }
                    val secondLife = command(PlayAction.AdjustLife(a.account.accountId, "1"), bst)
                    recipient.send(secondLife)
                    assertEquals("21", recipient.result(secondLife.requestId).mutation!!.transition.fields.single().from)
                    var accepted = apply(owner.revision(secondLife.requestId))
                    val stack = root.board.containers.single { it.zone == PlayZone.STACK }
                    val oldDigest = GameplayRules.stackDigest(accepted)
                    val rotate = command(PlayAction.SetPlayerCounter(a.account.accountId, CounterColor.PURPLE, "unrelated Stack guard edit"))
                    val entry = command(PlayAction.MoveCard(library.cards[1], MoveSource(library.id), MoveDestination(stack.id, MoveInsertion.TOP), oldDigest))
                    owner.send(rotate); owner.send(entry)
                    assertEquals("accepted", owner.result(rotate.requestId).outcome); assertEquals("accepted", owner.result(entry.requestId).outcome)
                    apply(recipient.revision(rotate.requestId))
                    accepted = apply(recipient.revision(entry.requestId))
                    val stale = command(PlayAction.MoveCard(library.cards[2], MoveSource(library.id), MoveDestination(stack.id, MoveInsertion.TOP), oldDigest))
                    val next = command((stale.action as GameAction.Play).action.let { (it as PlayAction.MoveCard).copy(expectedStackDigest = GameplayRules.stackDigest(accepted)) })
                    owner.send(stale); owner.send(next)
                    assertEquals("stackConflict", owner.result(stale.requestId).reason); assertEquals("accepted", owner.result(next.requestId).outcome)
                    val final = recipient.revision(next.requestId)
                    val finalPlay = apply(final)
                    val historyResponse = request(settings, "GET", "/v1/games/${game.id}/plays/${root.id}/mutations?after=0", b.token)
                    assertEquals(200, historyResponse.statusCode())
                    val history = json.decodeFromString<GameplayHistoryPage>(historyResponse.body())
                    assertEquals(7, history.mutations.size); assertEquals(final.resultRevision, history.throughRevision)
                    var replay = root
                    history.mutations.forEach { replay = GameplayHistory.replay(replay, it).state!! }
                    assertEquals(finalPlay, replay)
                    val revisions = json.decodeFromString<GameRevisions>(request(settings, "GET", "/v1/games/${game.id}/revisions?after=${started.resultRevision}", a.token).body())
                    assertEquals(history.mutations, revisions.revisions.map { it.mutation })
                    var httpTree = TreeSync.generation(GameplayTree(root))
                    revisions.revisions.forEach { httpTree = TreeSync.apply(httpTree, it.treeDelta!!) }
                    assertEquals(liveTree, httpTree)
                    val original = json.decodeFromString<GameRequestStatus>(request(settings, "GET", "/v1/games/${game.id}/results/${draw.requestId}", a.token).body()).result!!
                    owner.send(draw); assertEquals(original, owner.result(draw.requestId))
                    assertEquals(original, json.decodeFromString<GameRequestStatus>(request(settings, "GET", "/v1/games/${game.id}/results/${draw.requestId}", a.token).body()).result)
                    assertEquals(404, request(settings, "GET", "/v1/games/${game.id}/plays/${root.id}/mutations", other.token).statusCode())
                    assertEquals(405, post(settings, "/v1/games/${game.id}/plays/${root.id}/mutations", LookupGameResults(emptyList()), a.token).statusCode())
                    assertEquals(404, post(settings, "/v1/games/${game.id}/moveCard", LookupGameResults(emptyList()), a.token).statusCode())
                    recipient.send(SubscribeGame(game.id, final.resultRevision)); assertEquals(finalPlay, recipient.next<GameHead>().play)

                    var beforeReorder = finalPlay
                    val effectSource = beforeReorder.board.containers.single { it.id == field.id }.cards.first()
                    repeat(2) {
                        val add = command(PlayAction.Stack(StackAction.AddEffect(effectSource,
                            CombatStackRules.stackDigest(beforeReorder))))
                        owner.send(add)
                        assertEquals("accepted", owner.result(add.requestId).outcome)
                        beforeReorder = apply(recipient.revision(add.requestId))
                    }
                    val order = CombatStackRules.stack(beforeReorder.board).cards
                    val reorder = command(PlayAction.Stack(StackAction.ReorderEffect(order[1],
                        IdentityGap(order[0], order[2]), IdentityGap(null, order[0]))))
                    // This would be applicable under the retained legacy reducer, not a bad reference/gap.
                    assertEquals("accepted", GameplayRules.change(beforeReorder, a.account.accountId, reorder).outcome)
                    owner.send(reorder)
                    val rejected = owner.result(reorder.requestId)
                    assertEquals("rejected", rejected.outcome)
                    assertEquals("stackReorderingRemoved", rejected.reason)
                    assertNull(rejected.commitRevision)
                    assertNull(rejected.mutation)
                    owner.send(reorder)
                    assertEquals(rejected, owner.result(reorder.requestId))
                    recipient.send(SubscribeGame(game.id))
                    assertEquals(beforeReorder, recipient.next<GameHead>().play)
                    val afterRejection = json.decodeFromString<GameplayHistoryPage>(request(settings, "GET",
                        "/v1/games/${game.id}/plays/${root.id}/mutations?after=0", b.token).body())
                    assertEquals(history.mutations.size + 2, afterRejection.mutations.size)
                    assertTrue(afterRejection.mutations.none { it.action == (reorder.action as GameAction.Play).action })
                } }
            }
            println("PASS real HTTP accounts/decks and two authenticated gameplay sockets, no-ack draw/tap/life pipeline equals shared prediction, compatible opponent life uses actual input, Stack ignores orientation but rejects stale ordered entries, concrete revision/result/history HTTP codec and replay agree, original retry no duplication and no alternate write ingress")
        }
    }
}
