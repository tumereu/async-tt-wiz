package org.asyncttwiz.server.game

import java.net.InetAddress
import java.net.ServerSocket
import java.net.URI
import java.net.http.*
import java.time.Duration
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
                    val draw = command(PlayAction.MoveCard(library.cards.first(), MoveSource(library.id, edge = MoveSourceEdge.TOP), MoveDestination(hand.id, MoveInsertion.UNORDERED)))
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
                    val rotate = command(PlayAction.SetTapped(library.cards[1], true))
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
                } }
            }
            println("PASS real HTTP accounts/decks and two authenticated gameplay sockets, no-ack draw/tap/life pipeline equals shared prediction, compatible opponent life uses actual input, Stack ignores orientation but rejects stale ordered entries, concrete revision/result/history HTTP codec and replay agree, original retry no duplication and no alternate write ingress")
        }
    }
}
