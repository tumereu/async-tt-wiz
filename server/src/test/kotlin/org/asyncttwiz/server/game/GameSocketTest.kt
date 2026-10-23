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

    @Test fun `actual socket prepared selection publishes coherent private copies and accepted recipient receives retained definitions`() {
        OwnedPostgres().start().use { db ->
            FoundationMigrations.migrate(db.settings); val settings = settings(db)
            SpringService(settings).use { service ->
                service.start(); ready(settings)
                val a = account(settings, "PickerOwner"); val b = account(settings, "PickerRecipient")
                val stranger = account(settings, "PickerOther"); val (_, game) = create(settings, a, b)
                val front = id(); val back = id()
                val definition = CardDefinition("Prepared multiface", listOf(DefinitionFace(front, "Front", listOf(back), corner = "3/5"),
                    DefinitionFace(back, "Back", listOf(front), rulesText = "Retained rules")), sourceVersion = "synthetic-old")
                val submitted = SubmittedDefinition(DeckJson.digest(definition), definition)
                assertEquals(200, post(settings, "/v1/definitions", SubmitDefinitions(id(), listOf(submitted)), a.token).statusCode())
                val entries = listOf(SavedDeckEntry(SavedDeckSection.MAINBOARD, submitted.digest, 4),
                    SavedDeckEntry(SavedDeckSection.SIDEBOARD, submitted.digest, 1))
                val response = post(settings, "/v1/decks", CreateDeck(id(), "Chosen source", entries), a.token)
                assertEquals(201, response.statusCode()); val source = json.decodeFromString<SavedDeck>(response.body())
                Socket(settings, a.token).use { owner -> Socket(settings, b.token).use { recipient ->
                    owner.negotiate(); recipient.negotiate()
                    val aStream = owner.open(game); val bStream = recipient.open(game)
                    owner.send(SubscribeGame(game.id)); assertNotNull(owner.next<GameHead>().preparation)
                    recipient.send(SubscribeGame(game.id)); assertNull(recipient.next<GameHead>().preparation)
                    val select = GameCommand(id(), GameScope(game.id, game.preparationRound),
                        GameAction.SelectDeck(source.deck.id, source.deck.revision), aStream.streamId, aStream.generation)
                    owner.send(select); owner.receipt(select.requestId)
                    val result = owner.result(select.requestId); assertEquals("accepted", result.outcome)
                    val accepted = owner.revision(select.requestId)
                    val preparation = accepted.preparation!!; val selected = preparation.players[0].deck!!
                    assertEquals(4L, selected.count(SavedDeckSection.MAINBOARD)); assertEquals(1L, selected.count(SavedDeckSection.SIDEBOARD))
                    assertEquals(listOf(submitted), preparation.definitions)
                    val predicted = PreparationRules.select(game, PreparationRules.empty(game), a.account.accountId,
                        select.requestId, select.scope, select.action as GameAction.SelectDeck, source, listOf(submitted)).state
                    assertEquals(predicted, preparation)
                    assertNull(recipient.revision(select.requestId).preparation)
                    val privateRead = request(settings, "GET", "/v1/games/${game.id}/revisions?after=0", b.token)
                    assertEquals(200, privateRead.statusCode())
                    assertNull(json.decodeFromString<GameRevisions>(privateRead.body()).revisions.single().preparation)
                    assertFalse(privateRead.body().contains(source.deck.id)); assertFalse(privateRead.body().contains(definition.name))
                    val denied = select.copy(requestId = id(), streamId = bStream.streamId, generation = bStream.generation)
                    recipient.send(denied); assertEquals("accessDenied", recipient.result(denied.requestId).reason)
                    val accept = command(game, bStream, GameAction.AcceptInvitation)
                    recipient.send(accept); assertEquals("accepted", recipient.result(accept.requestId).outcome)
                    val shared = recipient.revision(accept.requestId).preparation!!
                    assertEquals(preparation, shared)
                    assertEquals(emptyList<DeckSummary>(), json.decodeFromString<DeckList>(request(settings, "GET", "/v1/decks", b.token).body()).decks)
                    assertEquals(404, request(settings, "GET", "/v1/decks/${source.deck.id}", b.token).statusCode())
                    recipient.send(SubscribeGame(game.id, "2")); assertEquals(preparation, recipient.next<GameHead>().preparation)
                    owner.send(select); assertEquals(result, owner.result(select.requestId))
                    assertEquals(404, post(settings, "/v1/games/${game.id}/selectDeck", LookupGameResults(emptyList()), a.token).statusCode())
                    assertEquals(404, request(settings, "GET", "/v1/games/${game.id}/revisions?after=0", stranger.token).statusCode())
                    assertFalse(json.encodeToString(result).contains("deck")) // Correlation, not an old preparation snapshot.
                    val ownRead = json.decodeFromString<GameRevisions>(request(settings, "GET", "/v1/games/${game.id}/revisions?after=0", a.token).body())
                    assertEquals(listOf("1", "2"), ownRead.revisions.map { it.resultRevision })
                    assertEquals(preparation, ownRead.revisions[0].preparation)
                    val captured = PreparationRules.capture(preparation, a.account.accountId, submitted.digest,
                        SavedDeckSection.MAINBOARD, true)!!
                    val moves = (0..3).map { offset -> GameCommand(id(), GameScope(game.id, game.preparationRound),
                        captured.action(captured.entries.single(), offset), aStream.streamId, aStream.generation) }
                    // Actual authenticated socket pipeline: release sends all constituents before any acknowledgement.
                    moves.forEach(owner::send)
                    var expected = preparation
                    for ((index, move) in moves.withIndex()) {
                        owner.receipt(move.requestId)
                        assertEquals("accepted", owner.result(move.requestId).outcome)
                        expected = PreparationRules.transfer(game, expected, a.account.accountId, move.scope,
                            move.action as GameAction.TransferPreparedCard).state!!
                        assertEquals(expected, owner.revision(move.requestId).preparation)
                        assertEquals(expected, recipient.revision(move.requestId).preparation)
                        assertEquals((index + 3).toString(), owner.resultFromRead(settings, a.token, game.id, move.requestId).commitRevision)
                    }
                    assertEquals(0L, expected.players[0].deck!!.count(SavedDeckSection.MAINBOARD))
                    assertEquals(5L, expected.players[0].deck!!.count(SavedDeckSection.SIDEBOARD))
                    val reverse = PreparationRules.capture(expected, a.account.accountId, submitted.digest, SavedDeckSection.SIDEBOARD, false)!!
                    val back = GameCommand(id(), GameScope(game.id, game.preparationRound), reverse.action(reverse.entries.single(), 0),
                        aStream.streamId, aStream.generation)
                    owner.send(back); assertEquals("accepted", owner.result(back.requestId).outcome)
                    val backState = owner.revision(back.requestId).preparation!!
                    assertEquals(1L, backState.players[0].deck!!.count(SavedDeckSection.MAINBOARD))
                    owner.send(moves[0]); assertEquals("3", owner.result(moves[0].requestId).commitRevision)
                    owner.send(SubscribeGame(game.id, "7")); assertEquals(backState, owner.next<GameHead>().preparation)
                    assertEquals(source, json.decodeFromString<SavedDeck>(request(settings, "GET", "/v1/decks/${source.deck.id}", a.token).body()))
                    assertEquals(404, post(settings, "/v1/games/${game.id}/transferPreparedCard", LookupGameResults(emptyList()), a.token).statusCode())
                } }
            }
            println("PASS actual authenticated HTTP saved source plus ordered socket select/receipt/result/revision, exact shared predicted equivalence and copy counts/definitions; pending-recipient socket and HTTP preparation redacted, accepted recipient receives retained game definitions without catalog/saved-deck access; stranger denied and no competing write endpoint")
        }
    }

    @Test fun `authenticated Ready sockets publish one actual initial board and restart resume retains root orders and original result`() {
        OwnedPostgres().start().use { db ->
            FoundationMigrations.migrate(db.settings); val settings = settings(db)
            lateinit var a: SessionCreated; lateinit var b: SessionCreated; lateinit var game: GameSummary
            lateinit var start: GameCommand; lateinit var hello: GameHello; lateinit var accepted: GameHead; lateinit var original: GameResult
            SpringService(settings).use { service ->
                service.start(); ready(settings)
                a = account(settings, "InitialSocketA"); b = account(settings, "InitialSocketB"); game = create(settings, a, b).second
                fun source(actor: SessionCreated): SavedDeck {
                    val definition = CardDefinition("Start text fallback", listOf(DefinitionFace(id(), "Start face", corner = "4/6")), sourceVersion = "synthetic-start")
                    val submitted = SubmittedDefinition(DeckJson.digest(definition), definition)
                    assertEquals(200, post(settings, "/v1/definitions", SubmitDefinitions(id(), listOf(submitted)), actor.token).statusCode())
                    val response = post(settings, "/v1/decks", CreateDeck(id(), "Start source", listOf(
                        SavedDeckEntry(SavedDeckSection.MAINBOARD, submitted.digest, 7), SavedDeckEntry(SavedDeckSection.SIDEBOARD, submitted.digest, 1))), actor.token)
                    assertEquals(201, response.statusCode()); return json.decodeFromString(response.body())
                }
                val aSource = source(a); val bSource = source(b)
                Socket(settings, a.token).use { owner -> Socket(settings, b.token).use { recipient ->
                    owner.negotiate(); recipient.negotiate(); val aStream = owner.open(game); val bStream = recipient.open(game)
                    owner.subscribe(game); recipient.subscribe(game)
                    fun setup(socket: Socket, stream: GameStreamReady, action: GameAction): GameRevision {
                        val input = GameCommand(id(), GameScope(game.id, game.preparationRound), action, stream.streamId, stream.generation)
                        socket.send(input); socket.receipt(input.requestId); assertEquals("accepted", socket.result(input.requestId).outcome)
                        return socket.revision(input.requestId)
                    }
                    setup(owner, aStream, GameAction.SelectDeck(aSource.deck.id, aSource.deck.revision))
                    setup(owner, aStream, GameAction.SetStarter(StarterChoice()))
                    val waiting = setup(owner, aStream, GameAction.Ready)
                    assertNull(waiting.play); assertTrue(waiting.preparation!!.players[0].ready)
                    val accept = command(game, bStream, GameAction.AcceptInvitation)
                    recipient.send(accept); assertEquals("accepted", recipient.result(accept.requestId).outcome)
                    val afterAccept = recipient.revision(accept.requestId)
                    assertEquals(GameLifecycle.LOBBY, afterAccept.summary.lifecycle); assertNull(afterAccept.play)
                    assertTrue(afterAccept.preparation!!.players[0].ready); assertFalse(afterAccept.preparation!!.players[1].ready)
                    val prepared = setup(recipient, bStream, GameAction.SelectDeck(bSource.deck.id, bSource.deck.revision)).preparation!!
                    owner.close() // The first Ready survives absence; no extra confirmation/presence required.
                    start = GameCommand(id(), GameScope(game.id, game.preparationRound), GameAction.Ready, bStream.streamId, bStream.generation)
                    hello = recipient.hello
                    recipient.send(start); recipient.receipt(start.requestId); original = recipient.result(start.requestId)
                    assertEquals("accepted", original.outcome)
                    val revision = recipient.revision(start.requestId); val state = revision.play!!
                    assertEquals(GameLifecycle.ACTIVE, revision.summary.lifecycle); assertNull(revision.preparation)
                    assertEquals(state.id, revision.summary.playId); assertEquals(listOf(state.id, state.root.id), original.createdIds)
                    assertEquals(14, state.board.cards.size); assertEquals(listOf("20", "20"), state.board.players.map { it.life })
                    assertEquals(TurnState(state.starter.accountId), state.board.turn)
                    assertTrue(state.board.containers.filter { it.zone != PlayZone.LIBRARY }.all { it.cards.isEmpty() })
                    for (player in prepared.players) {
                        val deck = player.deck!!; val members = deck.entries.filter { it.section == SavedDeckSection.MAINBOARD }.flatMap { entry ->
                            List(entry.quantity) { deck.card(entry, it).id } }.toSet()
                        assertEquals(members, state.shuffles.single { it.accountId == player.accountId }.order.toSet())
                    }
                    recipient.send(SubscribeGame(game.id, revision.resultRevision)); accepted = recipient.next()
                    assertEquals(state, accepted.play); assertNull(accepted.preparation)
                    recipient.send(start); assertEquals(original, recipient.result(start.requestId))
                    val late = start.copy(requestId = id(), action = GameAction.WithdrawReady)
                    recipient.send(late); assertEquals("lifecycleEnded", recipient.result(late.requestId).reason)
                    assertEquals(404, post(settings, "/v1/games/${game.id}/ready", LookupGameResults(emptyList()), b.token).statusCode())
                } }
                Socket(settings, a.token).use { returning ->
                    returning.negotiate(); returning.send(SubscribeGame(game.id, accepted.revision))
                    assertEquals(accepted, returning.next<GameHead>())
                }
            }
            SpringService(settings).use { service ->
                service.start(); ready(settings)
                Socket(settings, b.token, hello).use { resumed ->
                    resumed.negotiate(); resumed.send(ResumeGameStream(game.id, start.streamId, listOf(start.requestId)))
                    val ready = resumed.next<GameStreamReady>(); assertEquals(original, ready.statuses.single().result)
                    resumed.send(start.copy(generation = ready.generation)); assertEquals(original, resumed.result(start.requestId))
                    resumed.send(SubscribeGame(game.id, accepted.revision)); assertEquals(accepted, resumed.next<GameHead>())
                }
                assertEquals(accepted.summary, json.decodeFromString<GameSummary>(request(settings, "GET", "/v1/games/${game.id}/head", a.token).body()))
                assertEquals(accepted.play, json.decodeFromString<GameRevisions>(request(settings, "GET", "/v1/games/${game.id}/revisions?after=${accepted.revision.toLong()-1}", a.token).body()).revisions.single().play)
            }
            db.connection().use { c -> c.createStatement().executeQuery("SELECT (SELECT count(*) FROM game_play), (SELECT count(*) FROM game_inbox WHERE result IS NULL)").use {
                assertTrue(it.next()); assertEquals(1, it.getInt(1)); assertEquals(0, it.getInt(2))
            } }
            println("PASS actual authenticated HTTP definitions/decks and ordered two-socket starter/Ready/acceptance/start; first Ready survives disconnected owner, accepted mainboard IDs/retained definitions/no Sideboard/20 life/turn1 Untap/empty other areas published coherently; owner return and full Spring restart/resume recover identical root/play/seeds/orders/original result, one durable initialization and no HTTP start endpoint")
        }
    }

    private fun Socket.resultFromRead(settings: ServiceSettings, token: String, gameId: String, requestId: String) =
        json.decodeFromString<GameRequestStatus>(request(settings, "GET", "/v1/games/$gameId/results/$requestId", token).body()).result!!
}
