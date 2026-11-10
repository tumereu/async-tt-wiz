package org.asyncttwiz.server

import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.net.InetAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID
import kotlinx.serialization.encodeToString
import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.accounts.AccountService
import org.asyncttwiz.server.activity.ActivityHttp
import org.asyncttwiz.server.database.FoundationMigrations
import org.asyncttwiz.server.database.FreshDatabase
import org.asyncttwiz.server.database.RoundTripProxy
import org.asyncttwiz.server.decks.DeckHttp
import org.asyncttwiz.server.decks.DeckService
import org.asyncttwiz.server.game.GameService
import org.asyncttwiz.server.lobby.LobbyHttp
import org.asyncttwiz.server.lobby.LobbyService
import org.asyncttwiz.server.pods.PodHttp
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory

/**
 * SYS-025: the fixed database cost of the requests the app makes most. Round trips are counted on the
 * PostgreSQL wire (see [RoundTripProxy]); times are only printed, never asserted.
 */
class RequestCostTest {
    private val json = DeckJson.format
    private fun id() = UUID.randomUUID().toString()

    // SYS-025
    @Test fun `typical requests stay within their database round trips`() {
        FreshDatabase().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            RoundTripProxy(db.settings, latencyMillis = System.getProperty("asyncttwiz.roundTripLatency")?.toLong() ?: 0).use { proxy ->
            ServiceRuntime(proxy.settings).use { runtime ->
                runtime.start()
                val end = System.nanoTime() + Duration.ofSeconds(20).toNanos()
                while (!runtime.ready()) { assertTrue(System.nanoTime() < end); Thread.sleep(20) }
                val adapters = listOf<(HttpServletRequest, HttpServletResponse) -> Boolean>(DeckHttp(runtime)::handle,
                    PodHttp(runtime)::handle, LobbyHttp(runtime)::handle, ActivityHttp(runtime) { }::handle)
                val server = TomcatServletWebServerFactory(0).apply { setAddress(InetAddress.getLoopbackAddress()) }.getWebServer({ context ->
                    context.addServlet("routes", object : HttpServlet() {
                        override fun service(request: HttpServletRequest, response: HttpServletResponse) {
                            if (adapters.none { it(request, response) }) response.status = 404
                        }
                    }).addMapping("/")
                })
                server.start()
                try {
                    val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()
                    val accounts = AccountService(runtime)
                    fun account(name: String): SessionCreated {
                        accounts.register(name, "$name@example.invalid", "synthetic password")
                        return accounts.login("$name@example.invalid", "synthetic password")!!
                    }
                    val a = account("CostA"); val b = account("CostB")
                    val pa = accounts.authenticate(a.token)!!; val pb = accounts.authenticate(b.token)!!
                    fun get(who: SessionCreated, path: String): String {
                        val response = client.send(HttpRequest.newBuilder(URI("http://127.0.0.1:${server.port}$path"))
                            .header("Authorization", "Bearer ${who.token}").timeout(Duration.ofSeconds(30)).build(),
                            HttpResponse.BodyHandlers.ofString())
                        assertEquals(200, response.statusCode(), "$path ${response.body()}")
                        return response.body()
                    }
                    fun send(who: SessionCreated, method: String, path: String, input: String): String {
                        val response = client.send(HttpRequest.newBuilder(URI("http://127.0.0.1:${server.port}$path"))
                            .header("Authorization", "Bearer ${who.token}").header("Content-Type", "application/json")
                            .method(method, HttpRequest.BodyPublishers.ofString(input)).timeout(Duration.ofSeconds(30)).build(),
                            HttpResponse.BodyHandlers.ofString())
                        assertTrue(response.statusCode() in 200..299, "$path ${response.statusCode()} ${response.body()}")
                        return response.body()
                    }

                    // A drafting two-player pod from a 90-card cube.
                    val cards = TestPrintings.names("E2E").take(90)
                    val cube = json.decodeFromString<SavedCube>(send(a, "POST", "/v1/cubes",
                        json.encodeToString(CreateCube(id(), "Cost cube", cards.map { TestPrintings.entry(it, 1) }))))
                    val pod = json.decodeFromString<PodDetail>(send(a, "POST", "/v1/pods",
                        json.encodeToString(CreatePod(id(), "Cost pod", cube.cube.id)))).id
                    send(a, "POST", "/v1/pods/$pod/invitations", json.encodeToString(InviteToPod(id(), b.account.accountId)))
                    send(b, "POST", "/v1/pods/$pod/accept", json.encodeToString(PodRequest(id())))
                    send(a, "POST", "/v1/pods/$pod/start", json.encodeToString(PodRequest(id())))

                    // An active game between the same players.
                    val games = GameService(runtime)
                    val lobby = LobbyService(runtime)
                    lobby.add(pa, AddContact(id(), pb.account.accountId))
                    val game = GameWire.json.decodeFromString<GameSummary>(lobby.create(pa, CreateInvitation(id(), pb.account.accountId, "Cost game")).body)
                    val hello = GameHello(id(), id(), gameplayRulesVersion = CURRENT_GAMEPLAY_RULES_VERSION)
                    val connection = UUID.randomUUID()
                    val streamA = games.open(pa, hello, connection, game.id, id())
                    val helloB = GameHello(id(), id(), gameplayRulesVersion = CURRENT_GAMEPLAY_RULES_VERSION)
                    val connectionB = UUID.randomUUID()
                    val streamB = games.open(pb, helloB, connectionB, game.id, id())
                    fun execute(who: AccountService.Principal, h: GameHello, c: UUID, stream: GameStreamReady, action: GameAction, scope: GameScope) {
                        assertTrue(games.admit(who, h, c, GameCommand(id(), scope, action, stream.streamId, stream.generation)) is GameReceipt)
                        assertTrue(games.executeOne())
                    }
                    fun deck(who: AccountService.Principal): SavedDeck {
                        val decks = DeckService(runtime)
                        return GameWire.json.decodeFromString(decks.create(who, CreateDeck(id(), "Cost deck", listOf(TestPrintings.entry("Forest", 40)))).body)
                    }
                    val lobbyScope = GameScope(game.id, game.preparationRound)
                    val da = deck(pa); val dbk = deck(pb)
                    execute(pa, hello, connection, streamA, GameAction.SelectDeck(da.deck.id, da.deck.revision), lobbyScope)
                    execute(pa, hello, connection, streamA, GameAction.SetStarter(StarterChoice(pa.account.accountId)), lobbyScope)
                    execute(pb, helloB, connectionB, streamB, GameAction.AcceptInvitation, lobbyScope.copy(invitationId = game.invitationId))
                    execute(pb, helloB, connectionB, streamB, GameAction.SelectDeck(dbk.deck.id, dbk.deck.revision), lobbyScope)
                    execute(pa, hello, connection, streamA, GameAction.Ready, lobbyScope)
                    execute(pb, helloB, connectionB, streamB, GameAction.Ready, lobbyScope)
                    val play = games.head(pa, game.id).play!!
                    while (games.publications().isNotEmpty()) games.publications().forEach { games.published(it.id) }

                    data class Cost(val work: Int, val ownership: Int, val connections: Int)
                    val samples = mutableMapOf<String, MutableList<Cost>>()
                    val times = mutableMapOf<String, MutableList<Long>>()
                    val bodies = mutableMapOf<String, MutableList<String>>()
                    fun <T> measure(name: String, block: () -> T): T {
                        val work = proxy.roundTrips("async-tt-wiz-work"); val owner = proxy.roundTrips("async-tt-wiz-ownership")
                        val opened = proxy.connections("async-tt-wiz-work")
                        val start = System.nanoTime()
                        val result = block()
                        if (result is String) bodies.getOrPut(name) { mutableListOf() }.add(result)
                        times.getOrPut(name) { mutableListOf() }.add(System.nanoTime() - start)
                        samples.getOrPut(name) { mutableListOf() }.add(Cost(proxy.roundTrips("async-tt-wiz-work") - work,
                            proxy.roundTrips("async-tt-wiz-ownership") - owner, proxy.connections("async-tt-wiz-work") - opened))
                        return result
                    }
                    /** What the game socket does for one message: admission, the bearer check, then the work. */
                    fun <T> socket(token: String, work: (AccountService.Principal) -> T): T {
                        check(runtime.admitting()); return work(accounts.authenticate(token)!!)
                    }
                    repeat(10) {
                        // Game action: socket admission, execution, then publication to both players' open tables.
                        // An engine play's private setting (I-D56 §7): a revision without engine work
                        val command = GameCommand(id(), GameScope(game.id, playId = play.id), GameAction.EngineSettings(
                            org.asyncttwiz.protocol.game.PlaySettings(assume = if (it % 2 == 0) org.asyncttwiz.protocol.game.AssumeResolves.Always else org.asyncttwiz.protocol.game.AssumeResolves.Normal)), streamA.streamId, streamA.generation)
                        measure("game action: admit") { socket(a.token) { games.admit(it, hello, connection, command) } }
                        measure("game action: execute") { assertTrue(games.executeOne()) }
                        measure("game action: publish") {
                            for (publication in games.publications()) {
                                for (who in listOf(a, b)) socket(who.token) { principal ->
                                    publication.revision?.let { games.publicationState(principal,
                                        GameWire.json.decodeFromString<GameServerMessage>(it) as GameRevision) }
                                }
                                games.published(publication.id)
                            }
                        }
                        // Opening a game (socket subscribe at the current revision) and its 30 s head refresh.
                        measure("game open: subscribe") { socket(a.token) { principal ->
                            games.subscriptionHead(principal, game.id)
                            val head = games.subscriptionHead(principal, game.id)
                            games.revisions(principal, game.id, head.revision)
                            games.subscriptionHead(principal, game.id, presence = true)
                        } }
                        measure("game open: head refresh") { socket(a.token) { games.subscriptionHead(it, game.id, presence = true) } }
                        measure("GET /v1/games/{id}/head") { get(a, "/v1/games/${game.id}/head") }
                        measure("GET /v1/pods/{id}") { get(a, "/v1/pods/$pod") }
                        measure("GET /v1/games") { get(a, "/v1/games") }
                        measure("GET /v1/pods") { get(a, "/v1/pods") }
                        measure("GET /v1/decks") { get(a, "/v1/decks") }
                        measure("GET /activity") { get(a, "/activity") }
                        // A deck save: one request, its cards' card data built from the catalog (owner 2026-11-10); B's deck,
                        // so A's deck list stays the same bytes
                        measure("PUT /v1/decks/{id}") { send(b, "PUT", "/v1/decks/${dbk.deck.id}", json.encodeToString(UpdateDeck(id(),
                            contents = DeckContentsImport(listOf(TestPrintings.entry("Forest", 30 + it), TestPrintings.entry(cards[1], 3),
                                TestPrintings.entry(cards[2], 2, SavedDeckSection.SIDEBOARD)))))) }
                    }
                    for ((name, costs) in samples) {
                        val sorted = times.getValue(name).sorted()
                        println("COST $name: work=${costs.map { it.work }} ownership=${costs.map { it.ownership }} " +
                            "newConnections=${costs.sumOf { it.connections }} medianMs=${sorted[sorted.size / 2] / 1_000_000.0}")
                    }
                    // Work-pool round trips per request. Before (master): admit 21, execute 32-39, publish 49,
                    // subscribe 61, head refresh 24, game head 18, pod 25, games 19, pods 20, decks 18, activity 27,
                    // each counting the ownership-session probes that every transaction then made. Engine plays (I-D56 step D2)
                    // read the engine snapshot with every head: subscribe 31 -> 43 (three heads), head refresh 10 -> 14.
                    // R6 (engine responsiveness): a head reads the engine play's row, head and seats in one statement (4 -> 1)
                    // and the play, chat sync point and own pod deck name in another (3 -> 1): subscribe 43 -> 28, head
                    // refresh 14 -> 9.
                    val expected = mapOf("game action: admit" to 10, "game action: execute" to 18, "game action: publish" to 18,
                        "game open: subscribe" to 28, "game open: head refresh" to 9, "GET /v1/games/{id}/head" to 7, "GET /v1/pods/{id}" to 10,
                        // The games list reads its change token first (switch-review #11), and its summaries in two queries
                        // whatever the number of games (one per game before, and each play's row decoded)
                        "GET /v1/games" to 8, "GET /v1/pods" to 8, "GET /v1/decks" to 6, "GET /activity" to 15,
                        // Its rows in one statement (one per row before); no card data is stored (owner 2026-11-10)
                        "PUT /v1/decks/{id}" to 11)
                    for ((name, trips) in expected) assertEquals(List(10) { trips }, samples.getValue(name).map { it.work }, name)
                    // Requests never touch the ownership session: only its own 500 ms check does, far fewer times.
                    val requests = samples.values.sumOf { it.size }
                    assertTrue(samples.values.flatten().sumOf { it.ownership } < requests / 2, "$samples")
                    // Repeated reads of unchanged data answer the same bytes, cached card definitions included.
                    for (name in listOf("GET /v1/pods/{id}", "GET /v1/pods", "GET /v1/decks"))
                        assertEquals(1, bodies.getValue(name).distinct().size, name)
                } finally { server.stop(); server.destroy() }
            }
            }
        }
    }
}
