package org.asyncttwiz.server.game

import java.net.InetAddress
import java.net.ServerSocket
import java.net.URI
import java.net.http.*
import java.time.Duration
import java.util.UUID
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

class GameSocketTest {
    private val json = GameWire.json
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()
    private fun id() = UUID.randomUUID().toString()
    private fun port() = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1")).use { it.localPort }
    private fun settings(db: OwnedPostgres) = ServiceSettings(db.settings, URI("https://synthetic.invalid"),
        InetAddress.getByName("127.0.0.1"), port(), InetAddress.getByName("127.0.0.1"), port())
    private fun ready(settings: ServiceSettings) {
        val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
        do {
            if (client.send(HttpRequest.newBuilder(URI("http://127.0.0.1:${settings.healthPort()}/health/ready"))
                    .timeout(Duration.ofSeconds(2)).build(), HttpResponse.BodyHandlers.ofString()).statusCode() == 200) return
            Thread.sleep(20)
        } while (System.nanoTime() < end)
        fail<Unit>("Service did not become ready")
    }
    private fun request(settings: ServiceSettings, method: String, path: String, token: String? = null, body: String? = null): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(URI("http://127.0.0.1:${settings.publicPort()}$path"))
            .timeout(Duration.ofSeconds(15)).header("Content-Type", "application/json")
            .method(method, body?.let(HttpRequest.BodyPublishers::ofString) ?: HttpRequest.BodyPublishers.noBody())
        if (token != null) builder.header("Authorization", "Bearer $token")
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }
    private inline fun <reified T> post(settings: ServiceSettings, path: String, input: T, token: String? = null) =
        request(settings, "POST", path, token, json.encodeToString(input))
    private fun account(settings: ServiceSettings, name: String): SessionCreated {
        assertEquals(201, post(settings, "/accounts", RegisterAccount(name, "$name@example.invalid", "password😀")).statusCode())
        val response = post(settings, "/sessions", CreateSession("$name@example.invalid", "password😀"))
        assertEquals(201, response.statusCode()); return json.decodeFromString(response.body())
    }
    private fun create(settings: ServiceSettings, a: SessionCreated, b: SessionCreated): Pair<CreateInvitation, GameSummary> {
        assertEquals(200, post(settings, "/v1/contacts", AddContact(id(), b.account.accountId), a.token).statusCode())
        val input = CreateInvitation(id(), b.account.accountId, "Persistent Azure Spark")
        val response = post(settings, "/v1/invitations", input, a.token)
        assertEquals(201, response.statusCode()); return input to json.decodeFromString(response.body())
    }
    private inner class Socket(settings: ServiceSettings, token: String, val hello: GameHello = GameHello(id(), id())) : WebSocket.Listener, AutoCloseable {
        private val queue = LinkedBlockingQueue<GameServerMessage>()
        private val saved = mutableListOf<GameServerMessage>()
        private val text = StringBuilder()
        private val socket = client.newWebSocketBuilder().header("Authorization", "Bearer $token")
            .connectTimeout(Duration.ofSeconds(5)).buildAsync(URI("ws://127.0.0.1:${settings.publicPort()}/v1/socket"), this).get(10, TimeUnit.SECONDS)
        override fun onOpen(webSocket: WebSocket) { webSocket.request(1) }
        override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*>? {
            text.append(data)
            if (last) { queue.add(json.decodeFromString<GameServerMessage>(text.toString())); text.setLength(0) }
            webSocket.request(1); return null
        }
        fun send(message: GameClientMessage) = raw(json.encodeToString<GameClientMessage>(message))
        fun raw(text: String) { socket.sendText(text, true).get(5, TimeUnit.SECONDS) }
        fun message(predicate: (GameServerMessage) -> Boolean): GameServerMessage {
            saved.indexOfFirst(predicate).takeIf { it >= 0 }?.let { return saved.removeAt(it) }
            val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
            while (System.nanoTime() < end) {
                val next = queue.poll(100, TimeUnit.MILLISECONDS) ?: continue
                if (predicate(next)) return next
                saved.add(next)
            }
            fail<Unit>("Socket response missing; observed ${saved.map { it::class.simpleName }}")
            error("unreachable")
        }
        inline fun <reified T : GameServerMessage> next(): T = message { it is T } as T
        fun negotiate() { send(hello); next<GameWelcome>() }
        fun open(game: GameSummary, streamId: String = id()): GameStreamReady {
            send(OpenGameStream(game.id, streamId)); return next()
        }
        fun subscribe(game: GameSummary) { send(SubscribeGame(game.id)); assertEquals(game.id, next<GameHead>().gameId) }
        fun result(id: String) = message { it is GameResult && it.requestId == id } as GameResult
        fun receipt(id: String) = message { it is GameReceipt && it.requestId == id } as GameReceipt
        fun revision(id: String) = message { it is GameRevision && it.requestId == id } as GameRevision
        override fun close() { socket.abort() }
    }
    private fun command(game: GameSummary, stream: GameStreamReady, action: GameAction) =
        GameCommand(id(), GameScope(game.id, game.preparationRound, game.invitationId), action, stream.streamId, stream.generation)

    @Test fun `real authenticated sockets decline reinvite stale decision accept and recover private original outcomes`() {
        OwnedPostgres().start().use { db ->
            FoundationMigrations.migrate(db.settings); val settings = settings(db)
            lateinit var a: SessionCreated; lateinit var b: SessionCreated; lateinit var game: GameSummary
            lateinit var accepted: GameCommand
            SpringService(settings).use { service ->
                service.start(); ready(settings)
                a = account(settings, "SocketOwner"); b = account(settings, "SocketRecipient"); val stranger = account(settings, "SocketStranger")
                val (create, initial) = create(settings, a, b); game = initial
                Socket(settings, a.token).use { owner -> Socket(settings, b.token).use { recipient -> Socket(settings, stranger.token).use { outsider ->
                    recipient.send(OpenGameStream(game.id, id()))
                    assertEquals("upgradeRequired", recipient.next<GameAdmissionError>().code)
                    owner.negotiate(); recipient.negotiate(); outsider.negotiate()
                    val aStream = owner.open(game); val bStream = recipient.open(game)
                    owner.subscribe(game); recipient.subscribe(game)
                    outsider.send(SubscribeGame(game.id)); assertEquals("accessDenied", outsider.next<GameAdmissionError>().code)
                    outsider.send(OpenGameStream(game.id, id())); assertEquals("accessDenied", outsider.next<GameAdmissionError>().code)
                    val wrongActor = command(game, aStream, GameAction.AcceptInvitation)
                    owner.send(wrongActor); assertEquals("accessDenied", owner.result(wrongActor.requestId).reason)
                    // Strict unknown/duplicate semantic frames do not acquire a durable queue position.
                    recipient.raw("""{"type":"command","type":"ping","correlationId":"duplicate"}""")
                    assertEquals("invalidInput", recipient.next<GameAdmissionError>().code)
                    val decline = command(game, bStream, GameAction.DeclineInvitation)
                    recipient.send(decline)
                    assertEquals("2", recipient.receipt(decline.requestId).ingressOrdinal)
                    val declined = recipient.result(decline.requestId); assertEquals("accepted", declined.outcome)
                    game = owner.revision(decline.requestId).summary
                    assertEquals(InvitationState.DECLINED, game.invitationState); assertEquals("1", game.revision)
                    assertEquals(1, json.decodeFromString<GameList>(request(settings, "GET", "/v1/games", a.token).body()).games.size)
                    assertTrue(json.decodeFromString<GameList>(request(settings, "GET", "/v1/invitations", b.token).body()).games.isEmpty())
                    assertEquals(404, request(settings, "GET", "/v1/games/${game.id}/head", b.token).statusCode())
                    val recipientReinvite = command(game, bStream, GameAction.Reinvite)
                    recipient.send(recipientReinvite); assertEquals("accessDenied", recipient.result(recipientReinvite.requestId).reason)
                    val reinvite = command(game, aStream, GameAction.Reinvite)
                    owner.send(reinvite); val reinvited = owner.result(reinvite.requestId)
                    assertEquals("accepted", reinvited.outcome)
                    val next = recipient.revision(reinvite.requestId).summary
                    assertNotEquals(game.invitationId, next.invitationId); assertEquals(listOf(next.invitationId), reinvited.createdIds)
                    assertEquals(initial.id, next.id); assertEquals(initial.preparationRound, next.preparationRound)
                    assertEquals(initial.gameName, next.gameName); assertEquals(initial.createdAt, next.createdAt)
                    val stale = command(game, bStream, GameAction.AcceptInvitation)
                    recipient.send(stale); assertEquals("inapplicable", recipient.result(stale.requestId).reason)
                    accepted = command(next, bStream, GameAction.AcceptInvitation)
                    recipient.send(accepted); assertEquals("accepted", recipient.result(accepted.requestId).outcome)
                    game = owner.revision(accepted.requestId).summary
                    assertEquals(InvitationState.ACCEPTED, game.invitationState); assertEquals(GameLifecycle.LOBBY, game.lifecycle)
                    assertEquals("3", game.revision); assertEquals(initial.preparationRound, game.preparationRound)
                    val wrongPreparation = command(game, bStream, GameAction.DeclineInvitation).let {
                        it.copy(scope = it.scope.copy(preparationId = id())) }
                    val pipelinedTail = command(game, bStream, GameAction.DeclineInvitation)
                    // Write both frames before reading either receipt/result: TCP frame order, no ack gate.
                    recipient.send(wrongPreparation); recipient.send(pipelinedTail)
                    val firstOrdinal = recipient.receipt(wrongPreparation.requestId).ingressOrdinal.toLong()
                    assertEquals(firstOrdinal + 1, recipient.receipt(pipelinedTail.requestId).ingressOrdinal.toLong())
                    assertEquals("inapplicable", recipient.result(wrongPreparation.requestId).reason)
                    assertEquals("inapplicable", recipient.result(pipelinedTail.requestId).reason)
                    assertEquals(initial.createdAt, game.createdAt); assertEquals(initial.gameName, game.gameName)
                    assertEquals(game, json.decodeFromString<GameSummary>(request(settings, "GET", "/v1/games/${game.id}/head", b.token).body()))
                    assertTrue(json.decodeFromString<GameList>(request(settings, "GET", "/v1/invitations", b.token).body()).games.isEmpty())
                    // Same ID returns the past decline, not a new response to the current attempt.
                    recipient.send(decline); assertEquals(declined, recipient.result(decline.requestId))
                    recipient.send(decline.copy(action = GameAction.AcceptInvitation))
                    assertEquals("requestIdentityMismatch", recipient.next<GameAdmissionError>().code)
                    val privateResult = request(settings, "GET", "/v1/games/${game.id}/results/${decline.requestId}", a.token)
                    assertEquals(409, privateResult.statusCode()); assertFalse(privateResult.body().contains(decline.requestId))
                    assertEquals(404, request(settings, "GET", "/v1/games/${game.id}/results/${decline.requestId}", stranger.token).statusCode())
                    val originalCreate = json.decodeFromString<GameSummary>(post(settings, "/v1/invitations", create, a.token).body())
                    assertEquals(initial, originalCreate)
                    val revisionResponse = request(settings, "GET", "/v1/games/${game.id}/revisions?after=0", a.token)
                    assertEquals(200, revisionResponse.statusCode())
                    val revisions = json.decodeFromString<GameRevisions>(revisionResponse.body())
                    assertEquals(listOf("1", "2", "3"), revisions.revisions.map { it.resultRevision })
                    assertEquals(listOf(decline.requestId, reinvite.requestId, accepted.requestId), revisions.revisions.map { it.requestId })
                    assertEquals(405, post(settings, "/v1/games/${game.id}/revisions", LookupGameResults(emptyList()), a.token).statusCode())
                    assertEquals(404, post(settings, "/v1/games/${game.id}/reinvite", LookupGameResults(emptyList()), a.token).statusCode())
                } } }
            }
            SpringService(settings).use { service ->
                service.start(); ready(settings)
                assertEquals(game, json.decodeFromString<GameSummary>(request(settings, "GET", "/v1/games/${game.id}/head", a.token).body()))
                val results = post(settings, "/v1/games/${game.id}/results:lookup", LookupGameResults(listOf(accepted.requestId, id())), b.token)
                assertEquals(200, results.statusCode())
                assertEquals(listOf("final", "unknown"), json.decodeFromString<GameResults>(results.body()).statuses.map { it.status })
                assertFalse(results.body().contains("gameName")) // Results contain identities/revisions, never an old board snapshot.
            }
            println("PASS actual bearer WebSocket upgrade/negotiation and same-socket ordering; recipient Decline retains inviter game, exact-game Reinvite retains preparation/name/date, old attempt rejects, Accept enters same lobby without Ready/start; private statuses, committed revision recovery, original-create retry and real service restart; no HTTP game writes")
        }
    }

    @Test fun `actual socket resume replaces old connection without fencing other installations and revoked credentials stop new requests`() {
        OwnedPostgres().start().use { db ->
            FoundationMigrations.migrate(db.settings); val settings = settings(db)
            SpringService(settings).use { service ->
                service.start(); ready(settings)
                val a = account(settings, "ResumeOwner"); val b = account(settings, "ResumeRecipient")
                val (_, game) = create(settings, a, b)
                Socket(settings, b.token).use { old ->
                    old.negotiate(); val stream = old.open(game)
                    val decline = command(game, stream, GameAction.DeclineInvitation)
                    old.send(decline); val originalResult = old.result(decline.requestId)
                    Socket(settings, b.token, old.hello).use { fresh -> Socket(settings, b.token).use { independent ->
                        fresh.negotiate(); independent.negotiate(); val other = independent.open(game)
                        val missing = id()
                        fresh.send(ResumeGameStream(game.id, stream.streamId, listOf(decline.requestId, missing)))
                        val resumed = fresh.next<GameStreamReady>()
                        assertEquals("2", resumed.generation); assertEquals(listOf("final", "notAdmitted"), resumed.statuses.map { it.status })
                        assertEquals(originalResult, resumed.statuses.first().result)
                        old.send(command(game, stream, GameAction.AcceptInvitation))
                        assertEquals("streamFenced", old.next<GameAdmissionError>().code)
                        val independentCommand = command(game, other, GameAction.AcceptInvitation)
                        independent.send(independentCommand); independent.receipt(independentCommand.requestId)
                        assertEquals("inapplicable", independent.result(independentCommand.requestId).reason)
                        val retry = decline.copy(generation = resumed.generation)
                        fresh.send(retry); assertEquals(originalResult, fresh.result(retry.requestId))
                        request(settings, "DELETE", "/session", b.token)
                        fresh.send(retry.copy(requestId = missing))
                        assertEquals("unauthorized", fresh.next<GameAdmissionError>().code)
                        assertEquals(401, request(settings, "GET", "/v1/games/${game.id}/results/${decline.requestId}", b.token).statusCode())
                        val newLogin = json.decodeFromString<SessionCreated>(post(settings, "/sessions", CreateSession("ResumeRecipient@example.invalid", "password😀")).body())
                        val recovered = json.decodeFromString<GameRequestStatus>(request(settings, "GET", "/v1/games/${game.id}/results/${decline.requestId}", newLogin.token).body())
                        assertEquals(originalResult, recovered.result)
                    } }
                }
                val failure = assertThrows(Exception::class.java) { Socket(settings, "A".repeat(43)) }
                assertNotNull(failure.cause)
                Socket(settings, a.token).use { unsupported ->
                    unsupported.send(unsupported.hello.copy(protocolMajor = 2)); unsupported.next<GameUpgradeRequired>()
                    unsupported.send(OpenGameStream(game.id, id())); assertEquals("upgradeRequired", unsupported.next<GameAdmissionError>().code)
                }
            }
            println("PASS actual socket resume fences old connection before final/notAdmitted statuses, independent installation admits, duplicate original recovers unchanged final, revoked socket rejects new work and same-account new login reads original result; unauthorized upgrade and incompatible negotiation fail")
        }
    }
}
