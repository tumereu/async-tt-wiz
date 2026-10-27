package org.asyncttwiz.server.game

import java.util.UUID
import java.util.concurrent.TimeUnit
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URI
import java.net.http.*
import java.time.Duration
import java.util.concurrent.CompletionStage
import java.util.concurrent.LinkedBlockingQueue
import java.util.zip.GZIPInputStream
import kotlinx.serialization.encodeToString
import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.ServiceRuntime
import org.asyncttwiz.server.ServiceSettings
import org.asyncttwiz.server.SpringService
import org.asyncttwiz.server.accounts.AccountService
import org.asyncttwiz.server.database.FoundationMigrations
import org.asyncttwiz.server.database.OwnedPostgres
import org.asyncttwiz.server.decks.DeckService
import org.asyncttwiz.server.lobby.LobbyService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class GameChatTest {
    private val json = GameWire.json
    private fun id() = UUID.randomUUID().toString()
    private fun ready(runtime: ServiceRuntime) {
        runtime.start(); val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
        while (!runtime.ready()) { assertTrue(System.nanoTime() < end); Thread.sleep(20) }
    }
    private fun account(runtime: ServiceRuntime, name: String): AccountService.Principal {
        val accounts = AccountService(runtime); accounts.register(name, "$name@example.invalid", "synthetic password")
        return accounts.authenticate(accounts.login("$name@example.invalid", "synthetic password")!!.token)!!
    }
    private fun sql(db: OwnedPostgres, text: String) { db.connection().use { it.createStatement().execute(text) } }
    private fun count(db: OwnedPostgres, table: String) = db.connection().use { c ->
        c.createStatement().executeQuery("SELECT count(*) FROM $table").use { it.next(); it.getLong(1) } }
    private class Stream(val games: GameService, val actor: AccountService.Principal, val game: GameSummary) {
        val hello = GameHello(UUID.randomUUID().toString(), UUID.randomUUID().toString())
        val connection = UUID.randomUUID()
        val stream = games.open(actor, hello, connection, game.id, UUID.randomUUID().toString())
        fun command(action: GameAction): GameCommand {
            val scope = if (action is GameAction.SendChat) GameScope(game.id, playId = games.head(actor, game.id).summary.playId)
                else GameScope(game.id, game.preparationRound, if (action == GameAction.AcceptInvitation) game.invitationId else null)
            return GameCommand(UUID.randomUUID().toString(), scope, action, stream.streamId, stream.generation)
        }
        fun admit(c: GameCommand) = games.admit(actor, hello, connection, c)
        fun result(c: GameCommand) = games.results(actor, game.id, listOf(c.requestId)).statuses.single().result!!
        fun execute(c: GameCommand): GameResult { admit(c); assertTrue(games.executeOne()); return result(c) }
        fun execute(a: GameAction) = execute(command(a))
    }
    private inner class Fixture(val runtime: ServiceRuntime) {
        val a = account(runtime, "ChatA"); val b = account(runtime, "ChatB"); val other = account(runtime, "ChatOther")
        val games = GameService(runtime)
        val game: GameSummary
        val owner: Stream; val recipient: Stream
        init {
            val lobby = LobbyService(runtime); lobby.add(a, AddContact(id(), b.account.accountId))
            game = GameWire.json.decodeFromString(lobby.create(a, CreateInvitation(id(), b.account.accountId, "Chat Spark")).body)
            owner = Stream(games, a, game); recipient = Stream(games, b, game)
            recipient.execute(GameAction.AcceptInvitation)
            for ((actor, stream) in listOf(a to owner, b to recipient)) {
                val decks = DeckService(runtime)
                val definition = CardDefinition("Chat fixture", listOf(DefinitionFace(id(), "Retained face")))
                val submitted = SubmittedDefinition(DeckJson.digest(definition), definition)
                assertEquals(200, decks.submit(actor, SubmitDefinitions(id(), listOf(submitted))).status)
                val saved = decks.create(actor, CreateDeck(id(), "Selected deck", listOf(
                    SavedDeckEntry(SavedDeckSection.MAINBOARD, submitted.digest, 1))))
                assertEquals(201, saved.status)
                val deck = GameWire.json.decodeFromString<SavedDeck>(saved.body)
                stream.execute(GameAction.SelectDeck(deck.deck.id, deck.deck.revision))
            }
            owner.execute(GameAction.SetStarter(StarterChoice(a.account.accountId)))
            owner.execute(GameAction.Ready); recipient.execute(GameAction.Ready)
            assertNotNull(games.head(a, game.id).play)
        }
    }
    @Test fun `private read cursor is monotonic durable and counts only incoming messages including beyond page window`() {
        OwnedPostgres().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            lateinit var actor: AccountService.Principal; lateinit var game: String; lateinit var expected: ChatReadState
            ServiceRuntime(db.settings).use { runtime ->
                ready(runtime); val f = Fixture(runtime); actor = f.a; game = f.game.id
                val own = f.owner.command(GameAction.SendChat("own")); f.owner.execute(own)
                val first = f.recipient.command(GameAction.SendChat("first incoming")); f.recipient.execute(first)
                val last = f.recipient.command(GameAction.SendChat("latest incoming")); f.recipient.execute(last)
                val head = f.games.head(f.a, game)
                assertEquals(2, f.games.chatRead(f.a, game).unread)
                assertEquals(1, f.games.chatRead(f.b, game).unread)
                val read = f.games.chatRead(f.a, game, ReadChat(first.requestId))
                assertEquals(1, read.unread)
                assertEquals(f.games.chat(f.a, game).messages[1].revision, read.readThrough)
                assertEquals(read, f.games.chatRead(f.a, game, ReadChat(own.requestId)))
                assertEquals(read, f.games.chatRead(f.a, game, ReadChat(first.requestId)))
                assertEquals(read, f.games.chat(f.a, game).readState)
                assertEquals(1, f.games.chatRead(f.b, game).unread) // other member's cursor is unchanged
                assertEquals("accessDenied", assertThrows(GameProtocolFailure::class.java) { f.games.chatRead(f.other, game) }.code)
                assertEquals("accessDenied", assertThrows(GameProtocolFailure::class.java) {
                    f.games.chatRead(f.other, game, ReadChat(last.requestId)) }.code)
                assertEquals("invalidInput", assertThrows(GameProtocolFailure::class.java) {
                    f.games.chatRead(f.a, game, ReadChat(id())) }.code)
                val differentGame = json.decodeFromString<GameSummary>(LobbyService(runtime).create(f.a,
                    CreateInvitation(id(), f.b.account.accountId, "Separate chat cursor")).body)
                assertEquals("invalidInput", assertThrows(GameProtocolFailure::class.java) {
                    f.games.chatRead(f.a, differentGame.id, ReadChat(first.requestId)) }.code)
                assertEquals(head, f.games.head(f.a, game)) // no gameplay revision/history changes
                expected = f.games.chatRead(f.a, game, ReadChat(last.requestId))
                assertEquals(0, expected.unread)
                assertEquals(1, count(db, "game_chat_read_cursor"))
                // An independently installed client only downloads the latest 100 rows; unread
                // must still include older incoming rows outside that window.
                repeat(ChatPaging.SIZE + 1) { f.recipient.execute(GameAction.SendChat("incoming $it")) }
                expected = f.games.chatRead(f.a, game)
                assertEquals(ChatPaging.SIZE + 1, expected.unread)
                val page = f.games.chat(f.a, game)
                assertEquals(ChatPaging.SIZE, page.messages.size); assertNotNull(page.older)
                assertEquals(expected, page.readState)
            }
            ServiceRuntime(db.settings).use { runtime ->
                ready(runtime)
                assertEquals(expected, GameService(runtime).chatRead(actor, game))
            }
        }
    }
    @Test fun `chat shares durable FIFO and original outcome identity without mutating board or leaking private results`() {
        OwnedPostgres().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            ServiceRuntime(db.settings).use { runtime ->
                ready(runtime); val f = Fixture(runtime); val before = f.games.head(f.a, f.game.id)
                val first = f.owner.command(GameAction.SendChat("  Hello\n😀  "))
                val second = f.recipient.command(GameAction.SendChat("Correction"))
                val r1 = f.owner.admit(first) as GameReceipt; val r2 = f.recipient.admit(second) as GameReceipt
                assertEquals(r1.ingressOrdinal.toLong() + 1, r2.ingressOrdinal.toLong())
                assertEquals(r1, f.owner.admit(first)); assertTrue(f.games.head(f.b, f.game.id).chat.isEmpty())
                assertTrue(f.games.executeOne()); assertTrue(f.games.executeOne()); assertFalse(f.games.executeOne())
                val accepted = f.owner.result(first); assertEquals("accepted", accepted.outcome)
                assertEquals(listOf(first.requestId), accepted.createdIds)
                val head = f.games.head(f.a, f.game.id); assertEquals(head, f.games.head(f.b, f.game.id))
                assertEquals(before.play, head.play); assertEquals(before.preparation, head.preparation)
                assertTrue(head.chat.isEmpty())
                assertEquals(ChatSync(head.revision, second.requestId), head.chatSync)
                val chat = f.games.chat(f.a, f.game.id).messages
                assertEquals(listOf(first.requestId, second.requestId), chat.map { it.id })
                assertEquals(listOf("  Hello\n😀  ", "Correction"), chat.map { it.text })
                assertEquals(listOf(f.a.account.accountId, f.b.account.accountId), chat.map { it.author })
                assertTrue(chat.all { it.playId == before.play!!.id && it.createdAt.endsWith("Z") })
                assertEquals(accepted, f.owner.admit(first))
                assertEquals("requestIdentityMismatch", assertThrows(GameProtocolFailure::class.java) {
                    f.owner.admit(first.copy(action = GameAction.SendChat("changed"))) }.code)
                assertEquals("requestIdentityMismatch", assertThrows(GameProtocolFailure::class.java) {
                    f.games.results(f.b, f.game.id, listOf(first.requestId)) }.code)
                assertEquals("accessDenied", assertThrows(GameProtocolFailure::class.java) { f.games.head(f.other, f.game.id) }.code)
                assertEquals("accessDenied", assertThrows(GameProtocolFailure::class.java) {
                    f.games.revisions(f.other, f.game.id, "0") }.code)
                val revisions = f.games.revisions(f.b, f.game.id, before.revision).revisions
                assertEquals(chat, revisions.map { it.chatMessage }); assertEquals(2, revisions.size)
                assertEquals(chat.drop(1), f.games.chat(f.b, f.game.id, after = chat.first().revision).messages)
                assertEquals(chat.take(1), f.games.chat(f.b, f.game.id, before = chat.last().revision).messages)
                assertEquals("accessDenied", assertThrows(GameProtocolFailure::class.java) { f.games.chat(f.other, f.game.id) }.code)
                assertEquals("invalidInput", assertThrows(GameProtocolFailure::class.java) { f.games.chat(f.a, f.game.id, "1", "1") }.code)
                assertEquals(2, count(db, "game_chat")); assertEquals(1, count(db, "game_play"))
                println("PASS real PostgreSQL durable shared FIFO, two real account principals, exact multiline text, immutable message/correction, duplicate original outcomes, private results/nonparticipant read denial, unchanged play/tree, revision fanout payload")
            }
        }
    }
    @Test fun `atomic rollback keeps admitted chat pending and worker restart recovers once with original binding`() {
        OwnedPostgres().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            lateinit var original: GameCommand; lateinit var actor: AccountService.Principal; lateinit var before: GameHead
            lateinit var hello: GameHello
            ServiceRuntime(db.settings).use { runtime ->
                ready(runtime); val f = Fixture(runtime); actor = f.a; before = f.games.head(f.a, f.game.id)
                original = f.owner.command(GameAction.SendChat("survives admitted worker restart")); hello = f.owner.hello
                f.owner.admit(original)
                sql(db, """CREATE FUNCTION fail_chat_commit() RETURNS trigger LANGUAGE plpgsql AS ${'$'}${'$'}
                    BEGIN RAISE EXCEPTION 'owned chat transaction rollback'; END ${'$'}${'$'};
                    CREATE TRIGGER fail_chat_commit BEFORE INSERT ON game_outbox FOR EACH ROW EXECUTE FUNCTION fail_chat_commit();""")
                assertThrows(Exception::class.java) { f.games.executeOne() }
                // A SQL fault correctly invalidates the writer epoch; inspect committed rows, not a fenced service read.
                assertEquals(0, count(db, "game_chat"))
                assertEquals(1, count(db, "game WHERE id='${f.game.id}' AND revision=${before.revision}"))
                assertEquals(before.revision.toLong(), count(db, "game_revision"))
                assertEquals(before.revision.toLong(), count(db, "game_outbox"))
                assertEquals(1, count(db, "game_inbox WHERE result IS NULL"))
                sql(db, "DROP TRIGGER fail_chat_commit ON game_outbox; DROP FUNCTION fail_chat_commit()")
            }
            ServiceRuntime(db.settings).use { runtime ->
                ready(runtime); val games = GameService(runtime); games.validatePending()
                assertEquals(before, games.head(actor, before.gameId))
                assertEquals("pending", games.results(actor, before.gameId, listOf(original.requestId)).statuses.single().status)
                assertTrue(games.executeOne()); assertFalse(games.executeOne())
                val connection = UUID.randomUUID()
                val resume = games.open(actor, hello, connection, before.gameId, original.streamId, listOf(original.requestId))
                val result = resume.statuses.single().result!!; assertEquals("accepted", result.outcome)
                assertEquals(result, games.admit(actor, hello, connection, original.copy(generation = resume.generation)))
                assertEquals(1, count(db, "game_chat")); assertEquals(before.play, games.head(actor, before.gameId).play)
                assertEquals(original.requestId, games.chat(actor, before.gameId).messages.single().id)
                println("PASS atomic chat/message/revision/result/outbox rollback, durable pending worker restart and original stream result recovery exactly once")
            }
        }
    }
    @Test fun `real chat board and result share one writer while baseline separates immutable chat from tree records`() {
        OwnedPostgres().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            ServiceRuntime(db.settings).use { runtime ->
                ready(runtime); val f = Fixture(runtime); val before = f.games.head(f.a, f.game.id)
                val first = f.owner.command(GameAction.SendChat("before board"))
                val edit = f.owner.command(GameAction.SendChat("scope")).copy(action = GameAction.Play(
                    PlayAction.AdjustLife(f.a.account.accountId, "2")))
                val second = f.recipient.command(GameAction.SendChat("after board"))
                listOf(f.owner to first, f.owner to edit, f.recipient to second).forEach { (stream, command) -> stream.admit(command) }
                repeat(3) { assertTrue(f.games.executeOne()) }; assertFalse(f.games.executeOne())
                var head = f.games.head(f.a, f.game.id)
                assertEquals("22", head.play!!.board.players.first { it.accountId == f.a.account.accountId }.life)
                assertEquals(before.play!!.root, head.play!!.root)
                assertEquals(1, count(db, "gameplay_mutation")); assertEquals(0, count(db, "game_play_mutation"))
                assertEquals(1, count(db, "account_activity WHERE kind='GAME'")) // Chat emits no invented board group.
                val activity = org.asyncttwiz.server.activity.ActivityService(runtime)
                val groupTarget = activity.target(f.b, ActivityKind.GAME, f.game.id, head.play!!.currentGroup!!.id)
                assertEquals(ActivityDestination.GROUP, groupTarget.destination)
                assertEquals(before.play!!.root.id, groupTarget.boundaryNodeId)
                val history = f.games.history(f.a, f.game.id, head.play!!.id, "0")
                assertEquals(listOf(edit.requestId), history.mutations.map { it.requestId })
                val undo = edit.copy(requestId = id(), action = GameAction.Undo)
                val undone = f.owner.execute(undo)
                assertEquals("accepted", undone.outcome)
                assertEquals(f.owner.result(edit).mutation!!.id, undone.targetNodeId)
                val chat = f.games.chat(f.a, f.game.id).messages
                assertEquals(listOf(first.requestId, second.requestId), chat.map { it.id })
                assertEquals("accepted", f.owner.execute(edit.copy(requestId = id(), action = GameAction.Redo)).outcome)
                assertEquals(undone, f.owner.admit(undo))
                head = f.games.head(f.a, f.game.id)
                val manifest = f.games.baseline(f.a, f.game.id, listOf(first.requestId, edit.requestId))
                val bytes = manifest.chunks.flatMap { chunk ->
                    GZIPInputStream(f.games.baselineChunk(f.a, manifest.id, chunk.index).inputStream()).use { it.readAllBytes().toList() }
                }.toByteArray()
                val records = bytes.toString(Charsets.UTF_8).lineSequence().filter { it.isNotBlank() }
                    .map { json.decodeFromString<TreeSyncRecord>(it) }.toList()
                assertEquals(manifest.recordCount, records.size)
                val snapshot = GameplaySnapshot(manifest.head!!, emptyList(), records)
                GameplaySync.validate(snapshot)
                assertEquals(head, snapshot.head); assertTrue(snapshot.records.all { it.chatMessage == null })
                assertTrue(snapshot.head.chat.isEmpty()); assertEquals(head.chatSync, snapshot.head.chatSync)
                val retained = TreeSync.decode(records.asSequence(), head.gameId, head.play!!.id).tree
                assertEquals(listOf(edit.requestId), retained.nodes.values.map { it.requestId })
                assertEquals(head.play, retained.stateAt(retained.currentNodeId))
                val suggest = edit.copy(requestId = id(), action = GameAction.Play(PlayAction.SuggestResult(SuggestedResult.DRAW)))
                assertEquals("accepted", f.owner.execute(suggest).outcome)
                val suggestion = f.games.head(f.b, f.game.id).play!!.board.resultSuggestion!!
                val accept = f.recipient.command(GameAction.SendChat("scope")).copy(action = GameAction.AcceptResult(suggestion.id))
                assertEquals("accepted", f.recipient.execute(accept).outcome)
                val returned = f.games.head(f.a, f.game.id)
                assertEquals(GameLifecycle.LOBBY, returned.summary.lifecycle); assertNull(returned.play)
                assertEquals(chat, f.games.chat(f.a, f.game.id).messages); assertTrue(returned.preparation!!.players.none { it.ready })
                assertEquals(ActivityDestination.CURRENT, activity.target(f.b, ActivityKind.GAME, f.game.id, groupTarget.groupId!!).destination)
                assertEquals(2, count(db, "gameplay_mutation")); assertEquals(0, count(db, "game_play_mutation"))
                assertEquals("accepted", f.owner.admit(first).let { it as GameResult }.outcome)
                println("PASS actual FIFO chat/board/result: sole canonical mutation writer, immutable game-wide chat retained on return, tree records exclude chat payload, coherent baseline and private original")
            }
        }
    }
    @Test fun `stale play and ended lifecycle refuse first delivery but committed retries retain original result`() {
        OwnedPostgres().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            ServiceRuntime(db.settings).use { runtime ->
                ready(runtime); val f = Fixture(runtime)
                val accepted = f.owner.command(GameAction.SendChat("kept across lifecycle")); val result = f.owner.execute(accepted)
                val stale = f.owner.command(GameAction.SendChat("stale")).let { it.copy(scope = it.scope.copy(playId = id())) }
                assertEquals("inapplicable", f.owner.execute(stale).reason)
                val late = f.owner.command(GameAction.SendChat("late")); f.owner.admit(late)
                // I28 owns the actual ending/return path. This is execution-time lifecycle refusal, not its qualification.
                sql(db, "UPDATE game SET lifecycle='LOBBY' WHERE id='${f.game.id}'")
                f.games.executeOne(); assertEquals("lifecycleEnded", f.owner.result(late).reason)
                assertEquals(result, f.owner.admit(accepted)); assertEquals(1, count(db, "game_chat"))
                assertEquals("invalidInput", assertThrows(GameProtocolFailure::class.java) {
                    f.owner.admit(accepted.copy(requestId = id(), scope = accepted.scope.copy(preparationId = f.game.preparationRound))) }.code)
                println("PASS captured play only, stale first delivery rejected, admitted lifecycle change refused at execution, accepted original result recovers unchanged; actual end/rematch integration deferred to I28/I31")
            }
        }
    }
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()
    private fun port() = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1")).use { it.localPort }
    private fun http(settings: ServiceSettings, path: String, token: String? = null, method: String = "GET", body: String? = null): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(URI("http://127.0.0.1:${settings.publicPort()}$path"))
            .timeout(Duration.ofSeconds(10)).method(method, body?.let(HttpRequest.BodyPublishers::ofString) ?: HttpRequest.BodyPublishers.noBody())
        if (body != null) builder.header("Content-Type", "application/json")
        token?.let { builder.header("Authorization", "Bearer $it") }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }
    private inner class Socket(settings: ServiceSettings, token: String) : WebSocket.Listener, AutoCloseable {
        private val queue = LinkedBlockingQueue<GameServerMessage>()
        private val saved = mutableListOf<GameServerMessage>()
        private val text = StringBuilder()
        val hello = GameHello(id(), id())
        private val socket = client.newWebSocketBuilder().header("Authorization", "Bearer $token")
            .buildAsync(URI("ws://127.0.0.1:${settings.publicPort()}/v1/socket"), this).get(10, TimeUnit.SECONDS)
        override fun onOpen(webSocket: WebSocket) { webSocket.request(1) }
        override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*>? {
            text.append(data)
            if (last) { queue.add(json.decodeFromString<GameServerMessage>(text.toString())); text.setLength(0) }
            webSocket.request(1); return null
        }
        fun send(message: GameClientMessage) { socket.sendText(json.encodeToString(message), true).get(5, TimeUnit.SECONDS) }
        fun take(predicate: (GameServerMessage) -> Boolean): GameServerMessage {
            saved.indexOfFirst(predicate).takeIf { it >= 0 }?.let { return saved.removeAt(it) }
            val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
            while (System.nanoTime() < end) {
                val next = queue.poll(100, TimeUnit.MILLISECONDS) ?: continue
                if (predicate(next)) return next
                saved.add(next)
            }
            error("Missing chat socket response; observed ${saved.map { it::class.simpleName }}")
        }
        inline fun <reified T : GameServerMessage> next() = take { it is T } as T
        fun open(game: GameSummary): GameStreamReady {
            send(hello); next<GameWelcome>(); send(OpenGameStream(game.id, id())); val stream = next<GameStreamReady>()
            send(SubscribeGame(game.id)); next<GameHead>(); return stream
        }
        override fun close() { socket.abort() }
    }
    @Test fun `actual authenticated two socket chat publishes both directions and denies private reads and revoked writes`() {
        OwnedPostgres().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            lateinit var game: GameSummary; lateinit var a: SessionCreated; lateinit var b: SessionCreated; lateinit var other: SessionCreated
            ServiceRuntime(db.settings).use { runtime ->
                ready(runtime); val f = Fixture(runtime); game = f.games.head(f.a, f.game.id).summary
                val accounts = AccountService(runtime)
                a = accounts.login("ChatA@example.invalid", "synthetic password")!!
                b = accounts.login("ChatB@example.invalid", "synthetic password")!!
                other = accounts.login("ChatOther@example.invalid", "synthetic password")!!
            }
            val settings = ServiceSettings(db.settings, URI("https://synthetic.invalid"),
                InetAddress.getByName("127.0.0.1"), port(), InetAddress.getByName("127.0.0.1"), port())
            SpringService(settings).use { service ->
                service.start(); val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
                while (client.send(HttpRequest.newBuilder(URI("http://127.0.0.1:${settings.healthPort()}/health/ready")).build(),
                        HttpResponse.BodyHandlers.ofString()).statusCode() != 200) {
                    assertTrue(System.nanoTime() < end); Thread.sleep(20)
                }
                Socket(settings, a.token).use { owner -> Socket(settings, b.token).use { recipient ->
                    val os = owner.open(game); val rs = recipient.open(game)
                    val first = GameCommand(id(), GameScope(game.id, playId = game.playId), GameAction.SendChat("  real socket\nhello 😀  "), os.streamId, os.generation)
                    owner.send(first)
                    val result = owner.take { it is GameResult && it.requestId == first.requestId } as GameResult
                    assertEquals("accepted", result.outcome)
                    val incoming = recipient.take { it is GameRevision && it.requestId == first.requestId } as GameRevision
                    assertEquals((first.action as GameAction.SendChat).text, incoming.chatMessage!!.text)
                    assertEquals(a.account.accountId, incoming.chatMessage!!.author)
                    val reply = GameCommand(id(), first.scope, GameAction.SendChat("reply correction"), rs.streamId, rs.generation)
                    recipient.send(reply)
                    assertEquals("accepted", (recipient.take { it is GameResult && it.requestId == reply.requestId } as GameResult).outcome)
                    val returned = owner.take { it is GameRevision && it.requestId == reply.requestId } as GameRevision
                    assertEquals("reply correction", returned.chatMessage!!.text)
                    owner.send(first)
                    assertEquals(result, owner.take { it is GameResult && it.requestId == first.requestId })
                    val path = "/v1/games/${game.id}/results/${first.requestId}"
                    assertEquals(401, http(settings, path).statusCode())
                    assertEquals(409, http(settings, path, b.token).statusCode())
                    assertEquals(404, http(settings, path, other.token).statusCode())
                    assertEquals(404, http(settings, "/v1/games/${game.id}/revisions?after=0", other.token).statusCode())
                    val reads = http(settings, "/v1/games/${game.id}/revisions?after=${game.revision}", b.token)
                    assertEquals(200, reads.statusCode())
                    assertEquals(listOf(first.requestId, reply.requestId), json.decodeFromString<GameRevisions>(reads.body()).revisions.map { it.chatMessage!!.id })
                    val readPath = "/v1/games/${game.id}/chat/read"
                    fun state(token: String) = json.decodeFromString<ChatReadState>(http(settings, readPath, token).body())
                    assertEquals(401, http(settings, readPath).statusCode())
                    assertEquals(404, http(settings, readPath, other.token).statusCode())
                    assertEquals(1, state(a.token).unread); assertEquals(1, state(b.token).unread)
                    val seen = json.encodeToString(ReadChat(reply.requestId))
                    assertEquals(404, http(settings, readPath, other.token, "PUT", seen).statusCode())
                    val marked = http(settings, readPath, a.token, "PUT", seen)
                    assertEquals(200, marked.statusCode())
                    val cursor = json.decodeFromString<ChatReadState>(marked.body())
                    assertEquals(returned.resultRevision, cursor.readThrough); assertEquals(0, cursor.unread)
                    assertEquals(cursor, state(a.token)); assertEquals(1, state(b.token).unread)
                    assertEquals(cursor, json.decodeFromString<ChatReadState>(http(settings, readPath, a.token, "PUT",
                        json.encodeToString(ReadChat(first.requestId))).body()))
                    assertEquals(400, http(settings, readPath, a.token, "PUT", json.encodeToString(ReadChat(id()))).statusCode())
                    assertEquals(405, http(settings, readPath, a.token, "POST", seen).statusCode())
                    assertEquals(204, http(settings, "/session", a.token, "DELETE").statusCode())
                    assertEquals(401, http(settings, readPath, a.token, "PUT", seen).statusCode())
                    owner.send(first.copy(requestId = id(), action = GameAction.SendChat("revoked")))
                    assertEquals("unauthorized", owner.next<GameAdmissionError>().code)
                    assertEquals(2, count(db, "game_chat"))
                } }
            }
            println("PASS actual bearer-authenticated two-player WebSocket chat and correction both directions, shared revision delivery, original duplicate outcome, authorized HTTP catchup/private-result boundaries, revoked session cannot post; no native/device claim")
        }
    }
}
