package org.asyncttwiz.server.game

import java.util.UUID
import java.util.concurrent.TimeUnit
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.WebSocket
import java.time.Duration
import java.util.concurrent.CompletionStage
import java.util.concurrent.LinkedBlockingQueue
import kotlinx.serialization.encodeToString
import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.ServiceRuntime
import org.asyncttwiz.server.ServiceSettings
import org.asyncttwiz.server.SpringService
import org.asyncttwiz.server.accounts.AccountService
import org.asyncttwiz.server.database.FoundationMigrations
import org.asyncttwiz.server.database.FreshDatabase
import org.asyncttwiz.server.database.TestPostgres
import org.asyncttwiz.server.decks.DeckService
import org.asyncttwiz.server.lobby.LobbyService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class GameDeletionTest {
    private val json = GameWire.json
    private fun id() = UUID.randomUUID().toString()
    private fun sql(db: TestPostgres, statement: String) { db.connection().use { it.createStatement().execute(statement) } }
    private fun number(db: TestPostgres, statement: String): Long = db.connection().use { c ->
        c.createStatement().executeQuery(statement).use { it.next(); it.getLong(1) } }
    private fun ready(runtime: ServiceRuntime) {
        val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
        while (!runtime.ready()) { assertTrue(System.nanoTime() < end); Thread.sleep(20) }
    }
    private fun account(runtime: ServiceRuntime, name: String): AccountService.Principal {
        val accounts = AccountService(runtime)
        accounts.register(name, "$name@example.invalid", "synthetic password")
        return accounts.authenticate(accounts.login("$name@example.invalid", "synthetic password")!!.token)!!
    }
    private class Stream(val games: GameService, val actor: AccountService.Principal, val game: GameSummary) {
        val hello = GameHello(UUID.randomUUID().toString(), UUID.randomUUID().toString(),
            gameplayRulesVersion = CURRENT_GAMEPLAY_RULES_VERSION)
        val connection = UUID.randomUUID()
        val stream = games.open(actor, hello, connection, game.id, UUID.randomUUID().toString())
        fun command(action: GameAction, playId: String? = null) = GameCommand(UUID.randomUUID().toString(),
            if (action == GameAction.DeleteGame || action is GameAction.SendChat) GameScope(game.id, playId = playId) else GameScope(game.id, game.preparationRound,
                if (action == GameAction.AcceptInvitation) game.invitationId else null), action, stream.streamId, stream.generation)
        fun admit(command: GameCommand) = games.admit(actor, hello, connection, command)
        fun result(command: GameCommand) = games.results(actor, game.id, listOf(command.requestId)).statuses.single().result!!
        fun execute(command: GameCommand): GameResult { assertTrue(admit(command) is GameReceipt); assertTrue(games.executeOne()); return result(command) }
        fun execute(action: GameAction) = execute(command(action))
    }
    private fun create(runtime: ServiceRuntime, a: AccountService.Principal, b: AccountService.Principal): GameSummary {
        val lobby = LobbyService(runtime); lobby.add(a, AddContact(id(), b.account.accountId))
        return json.decodeFromString(lobby.create(a, CreateInvitation(id(), b.account.accountId, "Shared Spark")).body)
    }
    private fun start(runtime: ServiceRuntime, a: Stream, b: Stream): GameHead {
        b.execute(GameAction.AcceptInvitation)
        val decks = DeckService(runtime)
        val definition = CardDefinition("Retained", listOf(DefinitionFace(id(), "Retained face")))
        val submitted = SubmittedDefinition(DeckJson.digest(definition), definition)
        for (stream in listOf(a, b)) {
            assertEquals(200, decks.submit(stream.actor, SubmitDefinitions(id(), listOf(submitted))).status)
            val source = json.decodeFromString<SavedDeck>(decks.create(stream.actor, CreateDeck(id(), "Source",
                listOf(SavedDeckEntry(SavedDeckSection.MAINBOARD, submitted.digest, 2)))).body)
            assertEquals("accepted", stream.execute(GameAction.SelectDeck(source.deck.id, source.deck.revision)).outcome)
        }
        a.execute(GameAction.SetStarter(StarterChoice(a.actor.account.accountId)))
        a.execute(GameAction.Ready); b.execute(GameAction.Ready)
        return a.games.head(a.actor, a.game.id).also { assertEquals(GameLifecycle.ACTIVE, it.summary.lifecycle) }
    }
    private fun denied(action: () -> Unit) = assertEquals("accessDenied", assertThrows(GameProtocolFailure::class.java, action).code)

    @Test fun `either participant deletes unilaterally with absent opponent and ordered originals stay private`() {
        FreshDatabase().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            ServiceRuntime(db.settings).use { runtime ->
                runtime.start(); ready(runtime)
                val a = account(runtime, "DeleteA"); val b = account(runtime, "DeleteB"); val stranger = account(runtime, "DeleteOther")
                val games = GameService(runtime); val lobby = LobbyService(runtime)
                for (recipientDeletes in listOf(false, true)) {
                    val initial = create(runtime, a, b); val owner = Stream(games, a, initial); val recipient = Stream(games, b, initial)
                    val before = start(runtime, owner, recipient)
                    val actor = if (recipientDeletes) recipient else owner
                    val absent = if (recipientDeletes) owner else recipient
                    // No socket, presence, opponent vote, score or reusable-lobby transition participates.
                    val stale = actor.command(GameAction.DeleteGame, id())
                    val deletion = actor.command(GameAction.DeleteGame, before.play!!.id)
                    val late = absent.command(GameAction.Ready)
                    val first = actor.admit(stale) as GameReceipt
                    val receipt = actor.admit(deletion) as GameReceipt
                    val last = absent.admit(late) as GameReceipt
                    assertEquals(first.ingressOrdinal.toLong() + 1, receipt.ingressOrdinal.toLong())
                    assertEquals(receipt.ingressOrdinal.toLong() + 1, last.ingressOrdinal.toLong())
                    assertEquals(receipt, actor.admit(deletion))
                    assertEquals(before, games.head(a, initial.id))
                    assertTrue(games.executeOne()); assertEquals("inapplicable", actor.result(stale).reason)
                    assertTrue(games.executeOne()); val result = actor.result(deletion)
                    assertEquals("accepted", result.outcome); assertTrue(result.createdIds.isEmpty())
                    assertEquals((before.revision.toLong() + 1).toString(), result.commitRevision)
                    assertTrue(games.executeOne()); assertEquals("lifecycleEnded", absent.result(late).reason)
                    assertFalse(games.executeOne()); assertEquals(result, actor.admit(deletion))
                    val terminal = games.subscriptionHead(absent.actor, initial.id)
                    assertEquals(GameLifecycle.DELETED, terminal.summary.lifecycle)
                    assertEquals(result.commitRevision, terminal.revision); assertNull(terminal.play); assertNull(terminal.preparation)
                    val deletionRevision = games.publications().mapNotNull { it.revision }.map { json.decodeFromString<GameServerMessage>(it) as GameRevision }
                        .single { it.requestId == deletion.requestId }
                    assertNull(deletionRevision.play); assertNull(deletionRevision.preparation)
                    val old = games.publications().mapNotNull { it.revision }.map { json.decodeFromString<GameServerMessage>(it) as GameRevision }
                        .first { it.gameId == initial.id }
                    assertEquals(terminal, games.publicationState(absent.actor, old)) // Old outbox never reopens a board.
                    for (participant in listOf(a, b)) {
                        assertFalse(json.decodeFromString<GameList>(lobby.list(participant, null, false).body).games.any { it.id == initial.id })
                        assertFalse(json.decodeFromString<GameList>(lobby.list(participant, null, true).body).games.any { it.id == initial.id })
                        assertEquals(404, lobby.head(participant, initial.id).status)
                        denied { games.head(participant, initial.id) }; denied { games.revisions(participant, initial.id, "0") }
                    }
                    denied { games.subscriptionHead(stranger, initial.id) }
                    denied { games.results(stranger, initial.id, listOf(deletion.requestId)) }
                    assertEquals("requestIdentityMismatch", assertThrows(GameProtocolFailure::class.java) {
                        games.results(absent.actor, initial.id, listOf(deletion.requestId)) }.code)
                    assertEquals("requestIdentityMismatch", assertThrows(GameProtocolFailure::class.java) {
                        actor.admit(deletion.copy(scope = deletion.scope.copy(playId = id()))) }.code)
                    denied { actor.admit(deletion.copy(requestId = id())) }
                    assertEquals(0, number(db, "SELECT count(*) FROM request_identity WHERE id NOT IN (SELECT request_id FROM game_inbox) AND kind='gameCommand'"))
                    assertEquals("unknown", games.results(actor.actor, initial.id, listOf(id())).statuses.single().status)
                    val replacement = UUID.randomUUID()
                    val resumed = games.open(actor.actor, actor.hello, replacement, initial.id, deletion.streamId, listOf(deletion.requestId, id()))
                    assertEquals(listOf("final", "notAdmitted"), resumed.statuses.map { it.status })
                    assertEquals(result, resumed.statuses.first().result)
                    assertEquals(result, games.admit(actor.actor, actor.hello, replacement, deletion.copy(generation = resumed.generation)))
                    assertEquals("streamFenced", assertThrows(GameProtocolFailure::class.java) { actor.admit(deletion) }.code)
                    assertEquals(1, number(db, "SELECT count(*) FROM game_play WHERE game_id='${initial.id}'"))
                    assertEquals(1, number(db, "SELECT count(*) FROM game_revision WHERE game_id='${initial.id}' AND revision=${result.commitRevision}"))
                    assertEquals(1, number(db, "SELECT count(*) FROM game_outbox WHERE request_id='${deletion.requestId}'"))
                }
            }
            println("PASS either participant removes shared access with absent opponent; FIFO stale/delete/late, one revision/outbox, no content in tombstone, old publication suppression, private original HTTP-service lookup/resume/dedup/fencing, no new identity leak or replacement creation")
        }
    }

    @Test fun `stale confirmation cannot turn into setup cancellation or target a new play`() {
        FreshDatabase().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            ServiceRuntime(db.settings).use { runtime ->
                runtime.start(); ready(runtime)
                val a = account(runtime, "StaleDeleteA"); val b = account(runtime, "StaleDeleteB")
                val game = create(runtime, a, b); val games = GameService(runtime); val owner = Stream(games, a, game); val recipient = Stream(games, b, game)
                val captured = owner.command(GameAction.DeleteGame, id())
                assertEquals("lifecycleEnded", owner.execute(captured).reason)
                assertEquals(GameLifecycle.LOBBY, games.head(a, game.id).summary.lifecycle)
                val active = start(runtime, owner, recipient)
                assertEquals("inapplicable", owner.execute(captured.copy(requestId = id())).reason)
                assertEquals(active, games.head(a, game.id))
                // Simulate the distinct return-to-lobby boundary (I28 owns that lifecycle implementation).
                sql(db, "UPDATE game SET lifecycle='LOBBY', current_play=NULL WHERE id='${game.id}'")
                assertEquals("lifecycleEnded", owner.execute(owner.command(GameAction.DeleteGame, active.play!!.id)).reason)
                assertEquals(GameLifecycle.LOBBY, games.head(a, game.id).summary.lifecycle)
            }
            println("PASS exact active play guard; no whole-head guard; setup and simulated return-to-lobby refuse Delete without CancelSetup or retargeting; actual I28 rematch journey deferred")
        }
    }

    @Test fun `deletion rollback leaves accepted state intact and restart executes retained original exactly once`() {
        FreshDatabase().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            lateinit var a: AccountService.Principal; lateinit var before: GameHead; lateinit var original: GameCommand; lateinit var hello: GameHello
            ServiceRuntime(db.settings).use { runtime ->
                runtime.start(); ready(runtime)
                a = account(runtime, "AtomicDeleteA"); val b = account(runtime, "AtomicDeleteB")
                val game = create(runtime, a, b); val games = GameService(runtime); val owner = Stream(games, a, game); val recipient = Stream(games, b, game)
                before = start(runtime, owner, recipient); original = owner.command(GameAction.DeleteGame, before.play!!.id); hello = owner.hello
                owner.admit(original)
                sql(db, """CREATE FUNCTION fail_delete_commit() RETURNS trigger LANGUAGE plpgsql AS ${'$'}${'$'}
                    BEGIN RAISE EXCEPTION 'owned deletion rollback after state revision and result writes'; END ${'$'}${'$'};
                    CREATE TRIGGER fail_delete_commit BEFORE INSERT ON game_outbox FOR EACH ROW EXECUTE FUNCTION fail_delete_commit();""")
                assertThrows(Exception::class.java) { games.executeOne() }
                ready(runtime) // The writer epoch is reacquired after a database exception.
                assertEquals(before, games.head(a, game.id))
                assertEquals("pending", games.results(a, game.id, listOf(original.requestId)).statuses.single().status)
                assertEquals(0, number(db, "SELECT count(*) FROM game_outbox WHERE request_id='${original.requestId}'"))
                assertEquals(0, number(db, "SELECT count(*) FROM game_revision WHERE revision>${before.revision}"))
                sql(db, "DROP TRIGGER fail_delete_commit ON game_outbox; DROP FUNCTION fail_delete_commit()")
            }
            ServiceRuntime(db.settings).use { runtime ->
                val games = GameService(runtime); runtime.attachWorker(games::validatePending) { true }; runtime.start(); ready(runtime)
                assertTrue(games.executeOne()); assertFalse(games.executeOne())
                val result = games.results(a, before.gameId, listOf(original.requestId)).statuses.single().result!!
                assertEquals("accepted", result.outcome)
                val connection = UUID.randomUUID()
                val resume = games.open(a, hello, connection, before.gameId, original.streamId, listOf(original.requestId))
                assertEquals(result, resume.statuses.single().result)
                assertEquals(result, games.admit(a, hello, connection, original.copy(generation = resume.generation)))
                denied { games.head(a, before.gameId) }
                assertEquals(before.play, json.decodeFromString<PlayState>(db.connection().use { c -> c.createStatement()
                    .executeQuery("SELECT payload FROM game_play").use { it.next(); it.getString(1) } }))
                assertEquals(1, number(db, "SELECT count(*) FROM game_outbox WHERE request_id='${original.requestId}'"))
            }
            assertEquals(0, number(db, "SELECT count(*) FROM pg_locks WHERE locktype='advisory'"))
            println("PASS real PostgreSQL failure rolls back tombstone/revision/result/outbox together; pending original survives runtime restart and validates/executes once; retained private play is not a physical-erasure claim; exact container/advisory cleanup")
        }
    }

    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()
    @Test fun `combined cancellation chat deletion SQL order and socket HTTP recovery expose no removed content`() {
        FreshDatabase().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            lateinit var setup: GameSummary; lateinit var active: GameHead
            lateinit var setupStream: Stream; lateinit var playStream: Stream
            lateinit var cancel: GameCommand; lateinit var chat: GameCommand; lateinit var deletion: GameCommand; lateinit var late: GameCommand
            lateinit var token: String; lateinit var peerToken: String
            lateinit var expected: List<GameResult>
            ServiceRuntime(db.settings).use { runtime ->
                runtime.start(); ready(runtime)
                val a = account(runtime, "CombinedA"); val b = account(runtime, "CombinedB")
                val accounts = AccountService(runtime)
                token = accounts.login("CombinedA@example.invalid", "synthetic password")!!.token
                peerToken = accounts.login("CombinedB@example.invalid", "synthetic password")!!.token
                val games = GameService(runtime)
                setup = create(runtime, a, b); setupStream = Stream(games, a, setup)
                val playing = create(runtime, a, b); playStream = Stream(games, a, playing)
                active = start(runtime, playStream, Stream(games, b, playing))
                // All intents share the same durable processor, not a chat or lifecycle side queue.
                chat = playStream.command(GameAction.SendChat("private retained text"), active.play!!.id)
                deletion = playStream.command(GameAction.DeleteGame, active.play!!.id)
                late = playStream.command(GameAction.SendChat("must not appear"), active.play!!.id)
                val receipts = listOf(chat, deletion, late).map { playStream.admit(it) as GameReceipt }
                assertEquals(listOf(1L, 1L), receipts.zipWithNext { aReceipt, bReceipt ->
                    bReceipt.ingressOrdinal.toLong() - aReceipt.ingressOrdinal.toLong() })
                cancel = setupStream.command(GameAction.CancelSetup)
                assertTrue(setupStream.admit(cancel) is GameReceipt)
                while (games.executeOne()) { /* drain only the admitted originals */ }
                expected = listOf(cancel, chat, deletion, late).map { command ->
                    games.results(a, command.scope.gameId, listOf(command.requestId)).statuses.single().result!! }
                assertEquals(listOf("accepted", "accepted", "accepted", "rejected"), expected.map { it.outcome })
                assertEquals("lifecycleEnded", expected.last().reason)
                assertEquals(listOf(chat.requestId), expected[1].createdIds)
                assertEquals(expected[1], playStream.admit(chat)); assertEquals(expected[2], playStream.admit(deletion))
                assertEquals(expected[0], setupStream.admit(cancel))
                assertEquals(1, number(db, "SELECT count(*) FROM game_chat"))
                assertEquals(1, number(db, "SELECT count(*) FROM game_outbox WHERE request_id='${deletion.requestId}'"))
                // Even an unpublished chat revision is projected through current terminal authority.
                val oldChat = games.publications().single { it.gameId == active.gameId && it.result.contains(chat.requestId) }
                val projected = games.publicationState(b, json.decodeFromString<GameServerMessage>(oldChat.revision!!) as GameRevision) as GameHead
                assertEquals(GameLifecycle.DELETED, projected.summary.lifecycle)
                assertTrue(projected.chat.isEmpty()); assertNull(projected.play); assertNull(projected.preparation)
            }
            val settings = ServiceSettings(db.settings, URI("https://synthetic.invalid"), InetAddress.getByName("127.0.0.1"), port(),
                InetAddress.getByName("127.0.0.1"), port())
            SpringService(settings).use { service ->
                service.start()
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
                while (client.send(HttpRequest.newBuilder(URI("http://127.0.0.1:${settings.healthPort()}/health/ready")).build(),
                        HttpResponse.BodyHandlers.ofString()).statusCode() != 200) {
                    assertTrue(System.nanoTime() < deadline); Thread.sleep(20)
                }
                for ((stream, commands, results) in listOf(
                    Triple(setupStream, listOf(cancel), expected.take(1)),
                    Triple(playStream, listOf(chat, deletion, late), expected.drop(1)))) {
                    val gameId = stream.game.id
                    val terminal = if (gameId == setup.id) GameLifecycle.CANCELLED else GameLifecycle.DELETED
                    for (actorToken in listOf(token, peerToken)) {
                        assertEquals(404, request(settings, "/v1/games/$gameId/head", actorToken).statusCode())
                        assertEquals(404, request(settings, "/v1/games/$gameId/revisions?after=0", actorToken).statusCode())
                        assertTrue(json.decodeFromString<GameList>(request(settings, "/v1/games", actorToken).body()).games.isEmpty())
                    }
                    commands.zip(results).forEach { (command, result) ->
                        assertEquals(result, json.decodeFromString<GameRequestStatus>(request(settings,
                            "/v1/games/$gameId/results/${command.requestId}", token).body()).result)
                        assertEquals(409, request(settings, "/v1/games/$gameId/results/${command.requestId}", peerToken).statusCode())
                    }
                    Socket(settings, token, stream.hello).use { socket ->
                        socket.negotiate()
                        socket.send(ResumeGameStream(gameId, stream.stream.streamId, commands.map { it.requestId }))
                        val resumed = socket.next { it is GameStreamReady } as GameStreamReady
                        assertEquals(results, resumed.statuses.map { it.result }); assertEquals(terminal, resumed.ended!!.lifecycle)
                        commands.zip(results).forEach { (command, result) ->
                            socket.send(command.copy(generation = resumed.generation))
                            assertEquals(result, socket.next { it is GameResult && it.requestId == command.requestId })
                        }
                        socket.send(SubscribeGame(gameId, "0"))
                        val head = socket.next { it is GameHead } as GameHead
                        assertEquals(terminal, head.summary.lifecycle); assertTrue(head.chat.isEmpty())
                        assertNull(head.preparation); assertNull(head.play)
                        socket.send(commands.first().copy(requestId = id(), generation = resumed.generation))
                        assertEquals("accessDenied", (socket.next { it is GameAdmissionError } as GameAdmissionError).code)
                    }
                }
                assertEquals(1, number(db, "SELECT count(*) FROM game_chat"))
            }
            println("PASS combined real SQL chat/delete/late FIFO and setup cancellation; production restart, private HTTP/socket original dedup and one content-free terminal authority for both lifecycles")
        }
    }

    private fun port() = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1")).use { it.localPort }
    private fun request(settings: ServiceSettings, path: String, token: String): HttpResponse<String> = client.send(
        HttpRequest.newBuilder(URI("http://127.0.0.1:${settings.publicPort()}$path")).timeout(Duration.ofSeconds(5))
            .header("Authorization", "Bearer $token").build(), HttpResponse.BodyHandlers.ofString())
    private inner class Socket(settings: ServiceSettings, token: String,
        val hello: GameHello = GameHello(id(), id(), gameplayRulesVersion = CURRENT_GAMEPLAY_RULES_VERSION)) : WebSocket.Listener, AutoCloseable {
        private val queue = LinkedBlockingQueue<GameServerMessage>(); private val text = StringBuilder()
        private val socket = client.newWebSocketBuilder().header("Authorization", "Bearer $token").connectTimeout(Duration.ofSeconds(5))
            .buildAsync(URI("ws://127.0.0.1:${settings.publicPort()}/v1/socket"), this).get(10, TimeUnit.SECONDS)
        override fun onOpen(webSocket: WebSocket) { webSocket.request(1) }
        override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*>? {
            text.append(data)
            if (last) { queue.add(json.decodeFromString<GameServerMessage>(text.toString())); text.setLength(0) }
            webSocket.request(1); return null
        }
        fun send(message: GameClientMessage) { socket.sendText(json.encodeToString(message), true).get(5, TimeUnit.SECONDS) }
        fun next(predicate: (GameServerMessage) -> Boolean): GameServerMessage {
            val observed = mutableListOf<String>()
            val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
            while (System.nanoTime() < end) {
                val next = queue.poll(100, TimeUnit.MILLISECONDS) ?: continue
                if (predicate(next)) return next
                observed.add(if (next is GameAdmissionError) "admission:${next.code}" else next::class.simpleName!!)
            }
            error("Missing expected socket response; observed $observed")
        }
        fun negotiate() { send(hello); next { it is GameWelcome } }
        override fun close() { socket.abort() }
    }

    @Test fun `production socket deletes with absent opponent and tombstone reconnect recovers only private original outcomes`() {
        FreshDatabase().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            lateinit var active: GameHead; lateinit var aToken: String; lateinit var bToken: String; lateinit var outsider: String
            ServiceRuntime(db.settings).use { runtime ->
                runtime.start(); ready(runtime)
                val a = account(runtime, "WireDeleteA"); val b = account(runtime, "WireDeleteB"); account(runtime, "WireDeleteOther")
                val accounts = AccountService(runtime)
                aToken = accounts.login("WireDeleteA@example.invalid", "synthetic password")!!.token
                bToken = accounts.login("WireDeleteB@example.invalid", "synthetic password")!!.token
                outsider = accounts.login("WireDeleteOther@example.invalid", "synthetic password")!!.token
                val game = create(runtime, a, b); val games = GameService(runtime)
                active = start(runtime, Stream(games, a, game), Stream(games, b, game))
            }
            val settings = ServiceSettings(db.settings, URI("https://synthetic.invalid"), InetAddress.getByName("127.0.0.1"), port(),
                InetAddress.getByName("127.0.0.1"), port())
            SpringService(settings).use { service ->
                service.start()
                val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
                while (client.send(HttpRequest.newBuilder(URI("http://127.0.0.1:${settings.healthPort()}/health/ready")).build(),
                        HttpResponse.BodyHandlers.ofString()).statusCode() != 200) {
                    assertTrue(System.nanoTime() < end); Thread.sleep(20)
                }
                val hello = GameHello(id(), id(), gameplayRulesVersion = CURRENT_GAMEPLAY_RULES_VERSION); val streamId = id(); lateinit var command: GameCommand
                Socket(settings, aToken, hello).use { actor ->
                    actor.negotiate(); actor.send(OpenGameStream(active.gameId, streamId))
                    val stream = actor.next { it is GameStreamReady } as GameStreamReady
                    actor.send(SubscribeGame(active.gameId)); actor.next { it is GameHead }
                    command = GameCommand(id(), GameScope(active.gameId, playId = active.play!!.id), GameAction.DeleteGame, streamId, stream.generation)
                    actor.send(command); actor.next { it is GameReceipt && it.requestId == command.requestId }
                    // Ignore the result/publication on this transport; use the retained original on reconnect.
                    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
                    while (json.decodeFromString<GameRequestStatus>(request(settings,
                            "/v1/games/${active.gameId}/results/${command.requestId}", aToken).body()).status != "final") {
                        assertTrue(System.nanoTime() < deadline); Thread.sleep(20)
                    }
                }
                val path = "/v1/games/${active.gameId}"
                val original = json.decodeFromString<GameRequestStatus>(request(settings, "$path/results/${command.requestId}", aToken).body()).result!!
                assertEquals("accepted", original.outcome)
                for (token in listOf(aToken, bToken)) {
                    assertEquals(404, request(settings, "$path/head", token).statusCode())
                    assertEquals(404, request(settings, "$path/revisions?after=0", token).statusCode())
                    assertTrue(json.decodeFromString<GameList>(request(settings, "/v1/games", token).body()).games.isEmpty())
                }
                assertEquals(409, request(settings, "$path/results/${command.requestId}", bToken).statusCode())
                assertEquals(404, request(settings, "$path/results/${command.requestId}", outsider).statusCode())
                Socket(settings, aToken, hello).use { resumed ->
                    resumed.negotiate(); resumed.send(ResumeGameStream(active.gameId, streamId, listOf(command.requestId)))
                    val ready = resumed.next { it is GameStreamReady } as GameStreamReady
                    assertEquals(original, ready.statuses.single().result)
                    resumed.send(command.copy(generation = ready.generation))
                    assertEquals(original, resumed.next { it is GameResult && it.requestId == command.requestId })
                    resumed.send(SubscribeGame(active.gameId, "0"))
                    val head = resumed.next { it is GameHead } as GameHead
                    assertEquals(GameLifecycle.DELETED, head.summary.lifecycle); assertNull(head.play); assertNull(head.preparation)
                }
                // The absent opponent returns in a fresh process: lifecycle only, no old board/revision replay.
                Socket(settings, bToken).use { returned ->
                    returned.negotiate(); returned.send(OpenGameStream(active.gameId, id()))
                    assertEquals("accessDenied", (returned.next { it is GameAdmissionError } as GameAdmissionError).code)
                    returned.send(SubscribeGame(active.gameId, "0"))
                    val head = returned.next { it is GameHead } as GameHead
                    assertEquals(GameLifecycle.DELETED, head.summary.lifecycle); assertNull(head.play); assertNull(head.preparation)
                }
            }
            println("PASS production GameHub WebSocket Delete with absent peer; real HTTP lists/head/history blocked for both, original outcome private; same-stream final resume and duplicate original; fresh opponent receives content-free tombstone, not a historical replica")
        }
    }
}
