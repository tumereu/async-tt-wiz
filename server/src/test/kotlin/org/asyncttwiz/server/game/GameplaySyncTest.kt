package org.asyncttwiz.server.game

import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URI
import java.net.http.*
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.TimeUnit
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
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class GameplaySyncTest {
    @Test fun `unchanged baselines reuse the same pinned export and compressed arrays`() {
        OwnedPostgres().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            ServiceRuntime(db.settings).use { runtime ->
                runtime.start()
                val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
                while (!runtime.ready()) { assertTrue(System.nanoTime() < end); Thread.sleep(20) }
                val a = account(runtime, "ReuseA"); val b = account(runtime, "ReuseB")
                val started = start(runtime, a, b)
                val store = GameplayStore(accountCreationLimit = 12)
                val account = UUID.fromString(a.account.accountId)
                val manifests = db.connection().use { connection ->
                    List(8) { store.create(DSL.using(connection, SQLDialect.POSTGRES), account, started.head, emptyList()) }
                }
                assertEquals(1, manifests.map { it.id }.distinct().size)
                assertTrue(manifests.all { it == manifests.first() })
                assertTrue(manifests.all { store.chunk(account, it.id, 0) === store.chunk(account, manifests.first().id, 0) })
                // Cache hits must not read/checkpoint/serialize the retained SQL history again.
                val noSql = DSL.using(SQLDialect.POSTGRES)
                assertEquals(manifests.first(), store.create(noSql, account, started.head, emptyList()))
                val statuses = listOf(GameRequestStatus(id(), "unknown"))
                val variant = store.create(noSql, account, started.head, statuses)
                GameplaySync.validate(variant)
                assertEquals(statuses, variant.statuses)
                assertSame(store.chunk(account, manifests.first().id, 0), store.chunk(account, variant.id, 0))
                assertEquals(manifests.first(), store.manifest(account, manifests.first().id))
                repeat(10) { store.create(noSql, account, started.head, listOf(GameRequestStatus(id(), "unknown"))) }
                val limited = assertThrows(GameProtocolFailure::class.java) {
                    store.create(noSql, account, started.head.copy(revision = "999"), emptyList())
                }
                assertEquals("baselineRateLimited", limited.code); assertTrue(limited.retryable)
                assertEquals(manifests.first(), store.create(noSql, account, started.head, emptyList()))
            }
        }
    }

    @Test fun `account and global byte budgets evict whole exports and expiry needs no incoming request`() {
        OwnedPostgres().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            ServiceRuntime(db.settings).use { runtime ->
                runtime.start(); val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
                while (!runtime.ready()) { assertTrue(System.nanoTime() < end); Thread.sleep(20) }
                val a = account(runtime, "ByteA"); val b = account(runtime, "ByteB")
                val head = start(runtime, a, b).head
                val owner = UUID.fromString(a.account.accountId); val other = UUID.fromString(b.account.accountId)
                class MutableClock(var now: Instant) : Clock() {
                    override fun instant() = now
                    override fun getZone() = ZoneOffset.UTC
                    override fun withZone(zone: java.time.ZoneId): Clock = this
                }
                val clock = MutableClock(Instant.parse("2026-10-23T00:00:00Z"))
                db.connection().use { connection ->
                    val sql = DSL.using(connection, SQLDialect.POSTGRES)
                    val probe = GameplayStore(clock)
                    val sample = probe.create(sql, owner, head, emptyList())
                    val size = json.encodeToString(sample).toByteArray().size.toLong() + sample.chunks.sumOf { probe.chunk(owner, sample.id, it.index).size.toLong() }
                    val store = GameplayStore(clock, accountByteBudget = size + 1024, globalByteBudget = size * 2 + 2048)
                    val first = store.create(sql, owner, head, emptyList())
                    val replacement = store.create(sql, owner, head, listOf(GameRequestStatus(id(), "unknown")))
                    assertThrows(GameProtocolFailure::class.java) { store.chunk(owner, first.id, 0) }
                    assertNotNull(store.chunk(owner, replacement.id, 0))
                    val second = store.create(sql, other, head, emptyList())
                    assertNotNull(store.manifest(other, second.id))
                    val third = store.create(sql, UUID.randomUUID(), head, emptyList())
                    assertThrows(GameProtocolFailure::class.java) { store.manifest(owner, replacement.id) }
                    assertNotNull(third)
                    clock.now = clock.now.plusSeconds(601)
                    store.evictExpiredExports()
                    // No access/create call did the cleanup: a missing expired entry is already gone.
                    assertEquals("accessDenied", assertThrows(GameProtocolFailure::class.java) { store.manifest(other, second.id) }.code)
                    val tooSmall = GameplayStore(clock, accountByteBudget = 1, globalByteBudget = 1)
                    assertEquals("baselineTooLarge", assertThrows(GameProtocolFailure::class.java) { tooSmall.create(sql, owner, head, emptyList()) }.code)
                    assertNotNull(store.create(sql, owner, head, emptyList())) // Expiry also resets creation limits.
                    val globallyLimited = GameplayStore(clock, globalCreationLimit = 2)
                    globallyLimited.create(sql, owner, head, emptyList())
                    globallyLimited.create(sql, other, head, emptyList())
                    assertEquals("baselineRateLimited", assertThrows(GameProtocolFailure::class.java) {
                        globallyLimited.create(DSL.using(SQLDialect.POSTGRES), UUID.randomUUID(), head, emptyList())
                    }.code)
                    clock.now = clock.now.plusSeconds(61)
                    assertNotNull(globallyLimited.create(sql, UUID.randomUUID(), head, emptyList()))
                }
            }
        }
    }

    private val json = GameWire.json
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()
    private fun id() = UUID.randomUUID().toString()
    private fun port() = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1")).use { it.localPort }
    private fun request(settings: ServiceSettings, path: String, token: String? = null, body: String? = null): HttpResponse<ByteArray> {
        val builder = HttpRequest.newBuilder(URI("http://127.0.0.1:${settings.publicPort()}$path"))
            .timeout(Duration.ofSeconds(20))
        if (token != null) builder.header("Authorization", "Bearer $token")
        if (body != null) builder.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body))
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray())
    }
    private fun ready(settings: ServiceSettings) {
        val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
        while (true) {
            val response = client.send(HttpRequest.newBuilder(URI("http://127.0.0.1:${settings.healthPort()}/health/ready"))
                .timeout(Duration.ofSeconds(2)).build(), HttpResponse.BodyHandlers.discarding())
            if (response.statusCode() == 200) return
            assertTrue(System.nanoTime() < end); Thread.sleep(20)
        }
    }
    private fun account(runtime: ServiceRuntime, name: String): SessionCreated {
        val accounts = AccountService(runtime)
        accounts.register(name, "$name@example.invalid", "synthetic password")
        return accounts.login("$name@example.invalid", "synthetic password")!!
    }
    private data class Started(val head: GameHead, val last: GameCommand)
    private fun start(runtime: ServiceRuntime, a: SessionCreated, b: SessionCreated): Started {
        val accounts = AccountService(runtime); val pa = accounts.authenticate(a.token)!!; val pb = accounts.authenticate(b.token)!!
        val lobby = LobbyService(runtime); lobby.add(pa, AddContact(id(), b.account.accountId))
        val game = json.decodeFromString<GameSummary>(lobby.create(pa, CreateInvitation(id(), b.account.accountId, "Staged tree")).body)
        val games = GameService(runtime)
        val hellos = listOf(GameHello(id(), id()), GameHello(id(), id())); val connections = listOf(UUID.randomUUID(), UUID.randomUUID())
        val principals = listOf(pa, pb)
        val streams = principals.mapIndexed { index, principal -> games.open(principal, hellos[index], connections[index], game.id, id()) }
        fun execute(index: Int, action: GameAction): GameCommand {
            val command = GameCommand(id(), GameScope(game.id, game.preparationRound,
                if (action == GameAction.AcceptInvitation) game.invitationId else null), action, streams[index].streamId, streams[index].generation)
            assertTrue(games.admit(principals[index], hellos[index], connections[index], command) is GameReceipt)
            assertTrue(games.executeOne()); assertEquals("accepted", games.results(principals[index], game.id, listOf(command.requestId)).statuses.single().result!!.outcome)
            return command
        }
        execute(1, GameAction.AcceptInvitation)
        // Keep multi-chunk coverage within the shared 1000-card deck budget using valid metadata.
        val definition = CardDefinition("Retained", listOf(DefinitionFace(id(), "Face",
            rulesText = "Definition stays in the baseline\n" + "r".repeat(60_000), typeLine = "t".repeat(60_000),
            manaCost = "m".repeat(60_000), sourceData = "s".repeat(60_000))))
        val submitted = SubmittedDefinition(DeckJson.digest(definition), definition)
        principals.forEachIndexed { index, principal ->
            val decks = DeckService(runtime); assertEquals(200, decks.submit(principal, SubmitDefinitions(id(), listOf(submitted))).status)
            // Large ordinary prepared deck makes the JSON span independently hashed gzip chunks.
            val source = json.decodeFromString<SavedDeck>(decks.create(principal, CreateDeck(id(), "Source",
                listOf(SavedDeckEntry(SavedDeckSection.MAINBOARD, submitted.digest, 1000)))).body)
            execute(index, GameAction.SelectDeck(source.deck.id, source.deck.revision))
        }
        execute(0, GameAction.SetStarter(StarterChoice(a.account.accountId))); execute(0, GameAction.Ready)
        val last = execute(1, GameAction.Ready)
        return Started(games.head(pb, game.id), last)
    }
    private fun download(settings: ServiceSettings, token: String, manifest: GameplayManifest): GameplaySnapshot {
        GameplaySync.validate(manifest); val output = ByteArrayOutputStream()
        for (chunk in manifest.chunks) {
            val response = request(settings, "/v1/baselines/${manifest.id}/chunks/${chunk.index}", token)
            assertEquals(200, response.statusCode()); assertEquals("no-store", response.headers().firstValue("Cache-Control").orElse(""))
            val bytes = GZIPInputStream(response.body().inputStream()).use { it.readAllBytes() }
            assertEquals(chunk.bytes, bytes.size); assertEquals(chunk.digest, GameplaySync.digest(bytes)); output.write(bytes)
        }
        val records = output.toString(Charsets.UTF_8).lineSequence().filter { it.isNotEmpty() }
            .map { json.decodeFromString<TreeSyncRecord>(it) }.toList()
        assertEquals(manifest.recordCount, records.size)
        return GameplaySnapshot(manifest.head!!, emptyList(), records).also(GameplaySync::validate)
    }
    /** Actual production ingress/worker, not a materialized-board-only SQL fixture. */
    private fun append(settings: ServiceSettings, session: SessionCreated, head: GameHead): GameRevision {
        val play = head.play!!
        val messages = java.util.concurrent.LinkedBlockingQueue<GameServerMessage>()
        val socket = client.newWebSocketBuilder().header("Authorization", "Bearer ${session.token}")
            .buildAsync(URI("ws://127.0.0.1:${settings.publicPort()}/v1/socket"), object : WebSocket.Listener {
                private val text = StringBuilder()
                override fun onOpen(webSocket: WebSocket) { webSocket.request(1) }
                override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): java.util.concurrent.CompletionStage<*>? {
                    text.append(data)
                    if (last) { messages.add(json.decodeFromString<GameServerMessage>(text.toString())); text.setLength(0) }
                    webSocket.request(1); return null
                }
            }).get(15, TimeUnit.SECONDS)
        fun send(message: GameClientMessage) { socket.sendText(json.encodeToString(message), true).get(15, TimeUnit.SECONDS) }
        fun <T : GameServerMessage> await(type: Class<T>): T {
            val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
            while (true) {
                val message = messages.poll(1, TimeUnit.SECONDS)
                if (type.isInstance(message)) return type.cast(message)
                assertTrue(System.nanoTime() < end, "Timed out waiting for ${type.simpleName}")
            }
        }
        try {
            send(GameHello(id(), id(), gameplayRulesVersion = CURRENT_GAMEPLAY_RULES_VERSION)); await(GameWelcome::class.java)
            send(OpenGameStream(head.gameId, id())); val stream = await(GameStreamReady::class.java)
            send(SubscribeGame(head.gameId)); await(GameHead::class.java)
            send(GameCommand(id(), GameScope(head.gameId, playId = play.id),
                GameAction.Play(PlayAction.AdjustLife(play.board.players.first().accountId, "3")), stream.streamId, stream.generation))
            return await(GameRevision::class.java)
        } finally { socket.sendClose(WebSocket.NORMAL_CLOSURE, "done").get(15, TimeUnit.SECONDS) }
    }
    @Test fun `authorized route exports coherent retained current records with pinned chunks while deltas advance`() {
        OwnedPostgres().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            lateinit var a: SessionCreated; lateinit var b: SessionCreated; lateinit var other: SessionCreated; lateinit var started: Started
            ServiceRuntime(db.settings).use { runtime ->
                runtime.start(); val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
                while (!runtime.ready()) { assertTrue(System.nanoTime() < end); Thread.sleep(20) }
                a = account(runtime, "SyncA"); b = account(runtime, "SyncB"); other = account(runtime, "SyncOther")
                started = start(runtime, a, b)
            }
            val settings = ServiceSettings(db.settings, URI("https://synthetic.invalid"),
                InetAddress.getByName("127.0.0.1"), port(), InetAddress.getByName("127.0.0.1"), port())
            SpringService(settings).use { service ->
                service.start(); ready(settings)
                val path = "/v1/games/${started.head.gameId}/baselines"
                val input = json.encodeToString(LookupGameResults(listOf(started.last.requestId, id())))
                assertEquals(401, request(settings, path, body = input).statusCode())
                assertEquals(404, request(settings, path, other.token, input).statusCode())
                val created = request(settings, path, b.token, input); assertEquals(200, created.statusCode())
                val manifest = json.decodeFromString<GameplayManifest>(created.body().toString(Charsets.UTF_8))
                assertEquals(listOf("final", "unknown"), manifest.statuses.map { it.status })
                assertEquals(started.head.revision, manifest.statuses.first().result!!.commitRevision)
                assertTrue(manifest.chunks.size > 1)
                val update = append(settings, b, started.head)
                val snapshot = download(settings, b.token, manifest)
                assertEquals(started.head, snapshot.head); assertTrue(snapshot.treeRecords.none { it.kind == "node" })
                assertEquals(manifest, json.decodeFromString<GameplayManifest>(request(settings,
                    "/v1/baselines/${manifest.id}/manifest", b.token).body().toString(Charsets.UTF_8)))
                assertEquals(404, request(settings, "/v1/baselines/${manifest.id}/manifest", a.token).statusCode())
                assertEquals(404, request(settings, "/v1/baselines/${manifest.id}/chunks/0", other.token).statusCode())
                assertEquals(400, request(settings, "/v1/baselines/${manifest.id}/chunks/99999", b.token).statusCode())
                val deltas = json.decodeFromString<GameRevisions>(request(settings,
                    "/v1/games/${started.head.gameId}/revisions?after=${manifest.revision}", b.token).body().toString(Charsets.UTF_8))
                assertEquals(listOf(update), deltas.revisions)
                val latestManifest = json.decodeFromString<GameplayManifest>(request(settings, path, b.token,
                    json.encodeToString(LookupGameResults(emptyList()))).body().toString(Charsets.UTF_8))
                val latest = download(settings, b.token, latestManifest)
                assertNull(update.play, "ordinary delta must not retain a full board snapshot per node")
                val baselineTree = TreeSync.decode(snapshot.treeRecords.asSequence(), manifest.gameId, manifest.playId)
                val nextTree = TreeSync.apply(baselineTree, update.treeDelta!!).tree
                assertEquals(nextTree.stateAt(nextTree.currentNodeId), latest.head.play)
                assertEquals(1, latest.treeRecords.count { it.kind == "node" })
                assertEquals(started.head.play!!.definitions, latest.head.play!!.definitions)
            }
            println("PASS real PostgreSQL + HTTP authorized retained-tree baseline, retained definitions, multi-chunk hashes/gzip, immutable R under actual socket/FIFO advancement, incremental suffix, original outcomes/unknown-not-negative, account-scoped manifest/chunks")
        }
    }
    @Test fun `expired export retains no usable partial transfer`() {
        OwnedPostgres().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            ServiceRuntime(db.settings).use { runtime ->
                runtime.start(); val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
                while (!runtime.ready()) { assertTrue(System.nanoTime() < end); Thread.sleep(20) }
                val a = account(runtime, "LeaseA"); val b = account(runtime, "LeaseB"); val started = start(runtime, a, b)
                class MutableClock(var now: Instant) : Clock() {
                    override fun instant() = now
                    override fun getZone() = ZoneOffset.UTC
                    override fun withZone(zone: java.time.ZoneId): Clock = this
                }
                val clock = MutableClock(Instant.parse("2026-10-23T00:00:00Z")); val store = GameplayStore(clock)
                val account = UUID.fromString(a.account.accountId)
                val manifest = db.connection().use { store.create(DSL.using(it, SQLDialect.POSTGRES), account, started.head, emptyList()) }
                clock.now = clock.now.plusSeconds(601)
                assertEquals("baselineExpired", assertThrows(GameProtocolFailure::class.java) { store.chunk(account, manifest.id, 0) }.code)
                assertThrows(GameProtocolFailure::class.java) { store.manifest(account, manifest.id) }
            }
        }
    }
}
