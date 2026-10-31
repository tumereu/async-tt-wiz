package org.asyncttwiz.server.pods

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID
import kotlinx.serialization.encodeToString
import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.EphemeralPorts
import org.asyncttwiz.server.ServiceSettings
import org.asyncttwiz.server.SpringService
import org.asyncttwiz.server.startBound
import org.asyncttwiz.server.database.FoundationMigrations
import org.asyncttwiz.server.database.FreshDatabase
import org.asyncttwiz.server.database.TestPostgres
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** Rochester, Rotisserie, Winston, Winchester and Sealed over HTTP, and every engine's player limits. */
class LimitedDraftHttpTest {
    private val json = DeckJson.format
    private fun id() = UUID.randomUUID().toString()

    private class Fixture(val db: TestPostgres) : AutoCloseable {
        private val json = DeckJson.format
        private val config = EphemeralPorts.settings(db.settings)
        val service = SpringService(config)
        val settings: ServiceSettings
        val client: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()
        val players: List<SessionCreated>
        val a get() = players[0]
        val b get() = players[1]
        val c get() = players[2]
        val cube: SavedCube
        init {
            settings = service.startBound(config)
            val deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos()
            while (client.send(HttpRequest.newBuilder(URI("http://127.0.0.1:${settings.healthPort()}/health/ready")).build(),
                    HttpResponse.BodyHandlers.ofString()).statusCode() != 200) {
                check(System.nanoTime() < deadline); Thread.sleep(20)
            }
            players = List(5) { account("limited-$it") }
            val cards = (0 until 120).map { n ->
                val card = CardDefinition("Limited fixture $n", listOf(DefinitionFace(UUID.randomUUID().toString(), "Limited fixture $n", manaCost = "{G}")))
                SubmittedDefinition(DeckJson.digest(card), card)
            }
            send(a, "POST", "/v1/definitions", SubmitDefinitions(UUID.randomUUID().toString(), cards))
            cube = json.decodeFromString(send(a, "POST", "/v1/cubes",
                CreateCube(UUID.randomUUID().toString(), "Limited cube", cards.map { SavedCubeEntry(it.digest, 1) }), 201))
        }
        private fun account(name: String): SessionCreated {
            send(null, "POST", "/accounts", RegisterAccount(name, "$name@example.invalid", "password😀"), 201)
            return json.decodeFromString(send(null, "POST", "/sessions", CreateSession("$name@example.invalid", "password😀"), 201))
        }
        fun request(who: SessionCreated?, method: String, path: String, body: String? = null): HttpResponse<String> {
            val builder = HttpRequest.newBuilder(URI("http://127.0.0.1:${settings.publicPort()}$path"))
                .timeout(Duration.ofSeconds(30)).header("Content-Type", "application/json")
                .method(method, body?.let(HttpRequest.BodyPublishers::ofString) ?: HttpRequest.BodyPublishers.noBody())
            who?.let { builder.header("Authorization", "Bearer ${it.token}") }
            return client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
        }
        inline fun <reified T> send(who: SessionCreated?, method: String, path: String, input: T, status: Int = 200): String {
            val response = request(who, method, path, json.encodeToString(input))
            assertEquals(status, response.statusCode(), "$method $path ${response.body()}")
            return response.body()
        }
        fun code(body: String) = json.decodeFromString<PodError>(body).code
        fun get(who: SessionCreated, pod: String): PodDetail {
            val response = request(who, "GET", "/v1/pods/$pod")
            assertEquals(200, response.statusCode(), response.body())
            return json.decodeFromString(response.body())
        }
        fun list(who: SessionCreated): PodList = json.decodeFromString(request(who, "GET", "/v1/pods").body())
        fun create(settings: DraftSettings): String = json.decodeFromString<PodDetail>(send(a, "POST", "/v1/pods",
            CreatePod(UUID.randomUUID().toString(), "Limited pod", cube.cube.id, settings.mode, settings = settings), 201)).id
        fun invite(pod: String, who: SessionCreated) =
            send(a, "POST", "/v1/pods/$pod/invitations", InviteToPod(UUID.randomUUID().toString(), who.account.accountId))
        fun accept(pod: String, who: SessionCreated) = send(who, "POST", "/v1/pods/$pod/accept", PodRequest(UUID.randomUUID().toString()))
        fun join(pod: String, vararg who: SessionCreated) = who.forEach { invite(pod, it); accept(pod, it) }
        fun configure(pod: String, settings: DraftSettings, status: Int = 200) =
            send(a, "PUT", "/v1/pods/$pod", UpdatePod(UUID.randomUUID().toString(), settings = settings), status)
        fun autoStart(pod: String, enabled: Boolean, status: Int = 200) =
            send(a, "POST", "/v1/pods/$pod/auto-start", SetPodAutoStart(UUID.randomUUID().toString(), enabled), status)
        fun start(pod: String, status: Int = 200) = send(a, "POST", "/v1/pods/$pod/start", PodRequest(UUID.randomUUID().toString()), status)
        fun turn(who: SessionCreated, pod: String, input: PodTurn, status: Int = 200) = send(who, "POST", "/v1/pods/$pod/turn", input, status)
        fun session(account: String) = players.single { it.account.accountId == account }
        fun count(kind: ActivityKind, pod: String, who: SessionCreated): Int = db.connection().use { connection ->
            connection.prepareStatement("select count(*) from account_activity where game_id = ? and kind = ? and account_id = ?").use { statement ->
                statement.setObject(1, UUID.fromString(pod)); statement.setString(2, kind.name)
                statement.setObject(3, UUID.fromString(who.account.accountId))
                statement.executeQuery().use { rows -> rows.next(); rows.getInt(1) }
            }
        }
        fun waiting(who: SessionCreated): List<ActivityHint> {
            val response = request(who, "GET", "/activity/waiting")
            assertEquals(200, response.statusCode(), response.body())
            return ActivityWire.json.decodeFromString<WaitingGames>(response.body()).games
        }
        override fun close() = service.close()
    }

    private fun fixture(test: (Fixture) -> Unit) = FreshDatabase().start().use { db ->
        FoundationMigrations.migrate(db.settings)
        Fixture(db).use(test)
    }

    // DRF-035, DRF-036, DRF-037, DRF-141: Start, arming and settings changes follow the engine's player limit.
    @Test fun `player limits refuse start and arming and a settings change over the limit disarms auto start`() = fixture { f ->
        val pod = f.create(DraftSettings.booster().copy(packsPerPlayer = 1, cardsPerPack = 10))
        f.join(pod, f.b, f.c, f.players[3], f.players[4])
        // Five members: Grid, Winston and Winchester take at most four; the settings still save.
        for (settings in listOf(DraftSettings.grid().copy(grids = 2), DraftSettings.winston().copy(packsPerPlayer = 1),
                DraftSettings.winchester().copy(packsPerPlayer = 1))) {
            f.configure(pod, settings)
            assertEquals("too_many_members", f.code(f.start(pod, 409)))
            assertEquals(PodPhase.SETUP, f.get(f.a, pod).phase)
        }
        f.configure(pod, DraftSettings.rotisserie().copy(cardsPerPlayer = 10))
        assertEquals(PodPhase.DRAFTING, json.decodeFromString<PodDetail>(f.start(pod)).phase)

        // Arming counts members plus pending invitations.
        val armed = f.create(DraftSettings.booster().copy(packsPerPlayer = 1, cardsPerPack = 10))
        f.join(armed, f.b, f.c)
        f.invite(armed, f.players[3]); f.invite(armed, f.players[4])
        f.configure(armed, DraftSettings.winchester().copy(packsPerPlayer = 1, cardsPerPack = 5))
        assertEquals("too_many_players", f.code(f.autoStart(armed, true, 409)))
        f.configure(armed, DraftSettings.booster().copy(packsPerPlayer = 1, cardsPerPack = 10))
        assertTrue(json.decodeFromString<PodDetail>(f.autoStart(armed, true)).autoStart)
        // A change within the limit keeps it armed; one over the new engine's limit switches it off and tells the creator.
        f.configure(armed, DraftSettings.rochester().copy(packsPerPlayer = 1, cardsPerPack = 10))
        assertTrue(f.get(f.a, armed).autoStart)
        f.configure(armed, DraftSettings.winston().copy(packsPerPlayer = 1, cardsPerPack = 5))
        val off = f.get(f.a, armed)
        assertFalse(off.autoStart)
        assertEquals(PodAutoStartOff("too_many_players"), off.autoStartOff)
        assertEquals(1, f.count(ActivityKind.POD_AUTO_START_OFF, armed, f.a))
        // Members still see the setup; accepting no longer starts it.
        f.accept(armed, f.players[3])
        assertEquals(PodPhase.SETUP, f.get(f.a, armed).phase)
        // Inviting past the limit while armed switches it off too.
        val invited = f.create(DraftSettings.grid().copy(grids = 2))
        f.join(invited, f.b)
        f.invite(invited, f.c)
        f.autoStart(invited, true)
        f.invite(invited, f.players[3])
        assertTrue(f.get(f.a, invited).autoStart)
        f.invite(invited, f.players[4])
        assertEquals(PodAutoStartOff("too_many_players"), f.get(f.a, invited).autoStartOff)
    }
}
