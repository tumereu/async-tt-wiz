package org.asyncttwiz.server

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.serialization.encodeToString
import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.accounts.AccountService
import org.asyncttwiz.server.activity.ActivityService
import org.asyncttwiz.server.cubes.CubeService
import org.asyncttwiz.server.database.FreshDatabase
import org.asyncttwiz.server.database.TestPostgres
import org.asyncttwiz.server.database.UnreadableStoredData
import org.asyncttwiz.server.decks.DeckService
import org.asyncttwiz.server.game.GameService
import org.asyncttwiz.server.lobby.LobbyService
import org.asyncttwiz.server.pods.PodPresetService
import org.asyncttwiz.server.pods.PodService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** SYS-052: one stored row the current code can't read (say, after an incompatible upgrade) stays contained
 * to its own game, pod, preset or deck cover: lists, startup, game workers and other players carry on. */
class StoredDataContainmentTest {
    private val json = DeckJson.format
    private fun id() = UUID.randomUUID().toString()
    private fun sql(db: TestPostgres, text: String) { db.connection().use { it.createStatement().execute(text) } }
    private fun text(db: TestPostgres, query: String): String? = db.connection().use { c ->
        c.createStatement().executeQuery(query).use { if (it.next()) it.getString(1) else null } }
    /** A field the current code doesn't know: what a stored row looks like after that field was removed or renamed. */
    private fun retiredField(column: String) = "'{\"retiredField\":true,' || substr($column, 2)"

    private class Seeded(val a: String, val b: String, val goodGame: String, val badGame: String, val pending: String,
        val goodPod: String, val badPod: String, val goodPreset: String, val badPreset: String,
        val coverKept: String, val coverLost: String)

    /** Two of everything for A (games and pods shared with B): one stays readable, the other is made unreadable. */
    private fun seed(db: TestPostgres): Seeded = ServiceRuntime(db.settings).use { runtime ->
        runtime.start(); val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
        while (!runtime.ready()) { assertTrue(System.nanoTime() < end); Thread.sleep(20) }
        val accounts = AccountService(runtime)
        fun account(name: String): AccountService.Principal {
            accounts.register(name, "$name@example.invalid", "synthetic password")
            return accounts.authenticate(accounts.login("$name@example.invalid", "synthetic password")!!.token)!!
        }
        val a = account("ContainA"); val b = account("ContainB")
        val decks = DeckService(runtime); val games = GameService(runtime); val lobby = LobbyService(runtime)
        fun card(name: String) = CardDefinition(name, listOf(DefinitionFace(id(), name))).let { SubmittedDefinition(DeckJson.digest(it), it) }
        fun deck(owner: AccountService.Principal, name: String, card: SubmittedDefinition): SavedDeck {
            assertEquals(200, decks.submit(owner, SubmitDefinitions(id(), listOf(card))).status)
            return json.decodeFromString(decks.create(owner, CreateDeck(id(), name, listOf(SavedDeckEntry(SavedDeckSection.MAINBOARD, card.digest, 7)))).body)
        }
        val shared = card("Readable card"); val lost = card("Card whose stored form changed")
        val aDeck = deck(a, "Cover kept", shared); val bDeck = deck(b, "B deck", shared); val lostDeck = deck(a, "Cover lost", lost)
        lobby.add(a, AddContact(id(), b.account.accountId))
        class Stream(val actor: AccountService.Principal, val game: GameSummary) {
            val hello = GameHello(id(), id()); val connection = UUID.randomUUID()
            val stream = games.open(actor, hello, connection, game.id, id())
            fun command(action: GameAction, playId: String? = null) = GameCommand(id(),
                if (playId != null) GameScope(game.id, playId = playId)
                else GameScope(game.id, game.preparationRound, if (action == GameAction.AcceptInvitation) game.invitationId else null),
                action, stream.streamId, stream.generation)
            fun admit(command: GameCommand) = assertTrue(games.admit(actor, hello, connection, command) is GameReceipt)
            fun execute(action: GameAction, playId: String? = null) {
                val command = command(action, playId); admit(command); assertTrue(games.executeOne())
                assertEquals("accepted", games.results(actor, game.id, listOf(command.requestId)).statuses.single().result!!.outcome)
            }
        }
        fun started(name: String): Pair<GameSummary, Stream> {
            val game = json.decodeFromString<GameSummary>(lobby.create(a, CreateInvitation(id(), b.account.accountId, name)).body)
            val owner = Stream(a, game); val recipient = Stream(b, game)
            recipient.execute(GameAction.AcceptInvitation)
            owner.execute(GameAction.SelectDeck(aDeck.deck.id, aDeck.deck.revision))
            recipient.execute(GameAction.SelectDeck(bDeck.deck.id, bDeck.deck.revision))
            owner.execute(GameAction.SetStarter(StarterChoice(a.account.accountId)))
            owner.execute(GameAction.Ready); recipient.execute(GameAction.Ready)
            val play = games.head(a, game.id).play!!.id
            owner.execute(GameAction.Play(PlayAction.AdjustLife(a.account.accountId, "-2")), play)
            return game to owner
        }
        val (good, goodOwner) = started("Readable game"); val (bad, _) = started("Unreadable game")
        // Left pending for the game workers of the restarted service.
        val pending = goodOwner.command(GameAction.Play(PlayAction.AdjustLife(a.account.accountId, "1")), games.head(a, good.id).play!!.id)
        goodOwner.admit(pending)

        val cards = (0 until 90).map { card("Cube card $it") }
        assertEquals(200, decks.submit(a, SubmitDefinitions(id(), cards)).status)
        val cube = json.decodeFromString<SavedCube>(CubeService(runtime).create(a, CreateCube(id(), "Cube", cards.map { SavedCubeEntry(it.digest, 1) })).body)
        val pods = PodService(runtime)
        fun pod(name: String): String {
            val pod = json.decodeFromString<PodDetail>(pods.create(a, CreatePod(id(), name, cube.cube.id)).body).id
            assertEquals(200, pods.invite(a, pod, InviteToPod(id(), b.account.accountId)).status)
            assertEquals(200, pods.invitation(b, pod, PodRequest(id()), true).status)
            assertEquals(200, pods.start(a, pod, PodRequest(id())).status)
            return pod
        }
        val goodPod = pod("Readable pod"); val badPod = pod("Unreadable pod")
        val presets = PodPresetService(runtime)
        fun preset(name: String) = json.decodeFromString<PodPreset>(presets.save(a, SavePodPreset(name, DraftSettings())).body).id
        val goodPreset = preset("Readable preset"); val badPreset = preset("Unreadable preset")

        sql(db, """UPDATE game_play SET payload = ${retiredField("payload")} WHERE game_id = '${bad.id}';
            UPDATE pod SET draft_state = draft_state || '{"retiredField":true}'::jsonb WHERE id = '$badPod';
            UPDATE pod_preset SET settings = ${retiredField("settings")} WHERE id = '$badPreset';
            UPDATE definition SET payload = ${retiredField("payload")} WHERE digest = '${lost.digest}'""")
        Seeded(a.account.accountId, b.account.accountId, good.id, bad.id, pending.requestId, goodPod, badPod,
            goodPreset, badPreset, aDeck.deck.id, lostDeck.deck.id)
    }

    /** A pending request the current code can't decode, queued ahead of the readable game's pending request. */
    private fun poisonRequest(db: TestPostgres, seeded: Seeded): String {
        val request = id()
        sql(db, """INSERT INTO request_identity VALUES ('$request','${seeded.a}','gameCommand','${seeded.badGame}','${"0".repeat(64)}');
            INSERT INTO game_inbox(request_id,game_id,account_id,ingress_ordinal,stream_id,generation,command)
                SELECT '$request', id, '${seeded.a}', next_ingress, '${id()}', 1, '{"type":"retiredCommand"}' FROM game WHERE id = '${seeded.badGame}';
            UPDATE game SET next_ingress = next_ingress + 1 WHERE id = '${seeded.badGame}'""")
        return request
    }

    // SYS-052
    @Test fun `an unreadable game pod preset or deck cover stays out of lists while its own reads fail as unreadable`() {
        FreshDatabase().start().use { db ->
            val s = seed(db)
            ServiceRuntime(db.settings).use { runtime ->
                runtime.start(); val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
                while (!runtime.ready()) { assertTrue(System.nanoTime() < end); Thread.sleep(20) }
                val accounts = AccountService(runtime)
                fun principal(name: String) = accounts.authenticate(accounts.login("$name@example.invalid", "synthetic password")!!.token)!!
                val a = principal("ContainA"); val b = principal("ContainB")
                val lobby = LobbyService(runtime); val games = GameService(runtime)
                for (player in listOf(a, b)) {
                    val list = lobby.list(player, null, invitations = false)
                    assertEquals(200, list.status, "One unreadable game must not break the games list")
                    assertEquals(listOf(s.goodGame), json.decodeFromString<GameList>(list.body).games.map { it.id })
                }
                assertEquals(200, lobby.head(a, s.goodGame).status)
                assertNotNull(games.head(a, s.goodGame).play)
                val unreadable = assertThrows(UnreadableStoredData::class.java) { games.head(a, s.badGame) }
                assertEquals(s.badGame, unreadable.id())
                assertThrows(UnreadableStoredData::class.java) { lobby.head(b, s.badGame) }

                val pods = PodService(runtime)
                for (player in listOf(a, b)) {
                    val list = pods.list(player)
                    assertEquals(200, list.status, "One unreadable pod must not break the pods list")
                    assertEquals(listOf(s.goodPod), json.decodeFromString<PodList>(list.body).pods.map { it.id })
                    // Notifications never decode a pod's stored draft.
                    ActivityService(runtime).page(player, null)
                }
                assertEquals(200, pods.read(b, s.goodPod).status)
                assertThrows(UnreadableStoredData::class.java) { pods.read(b, s.badPod) }

                val presets = PodPresetService(runtime).list(a)
                assertEquals(200, presets.status, "One unreadable preset must not break the presets list")
                assertEquals(listOf(s.goodPreset), json.decodeFromString<PodPresetList>(presets.body).presets.map { it.id })

                val decks = DeckService(runtime).list(a, null)
                assertEquals(200, decks.status, "One unreadable card must not break the decks list")
                val covers = json.decodeFromString<DeckList>(decks.body).decks.associate { it.id to it.cover?.name }
                assertEquals(mapOf(s.coverKept to "Readable card", s.coverLost to null), covers)
            }
            // Nothing was deleted or rewritten.
            assertEquals("true", text(db, "SELECT (payload LIKE '{\"retiredField\":true,%')::text FROM game_play WHERE game_id = '${s.badGame}'"))
            assertEquals("true", text(db, "SELECT jsonb_exists(draft_state, 'retiredField')::text FROM pod WHERE id = '${s.badPod}'"))
            assertEquals("true", text(db, "SELECT (settings LIKE '{\"retiredField\":true,%')::text FROM pod_preset WHERE id = '${s.badPreset}'"))
        }
    }

    // SYS-052
    @Test fun `startup and the game workers carry on past an unreadable game and an unreadable pending request`() {
        FreshDatabase().start().use { db ->
            val s = seed(db)
            val poison = poisonRequest(db, s)
            val config = EphemeralPorts.settings(db.settings)
            lateinit var settings: ServiceSettings
            val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()
            fun request(path: String, token: String? = null, body: String? = null): HttpResponse<String> {
                val builder = HttpRequest.newBuilder(URI("http://127.0.0.1:${settings.publicPort()}$path")).timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .method(if (body == null) "GET" else "POST", body?.let(HttpRequest.BodyPublishers::ofString) ?: HttpRequest.BodyPublishers.noBody())
                token?.let { builder.header("Authorization", "Bearer $it") }
                return client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
            }
            fun await(what: String, check: () -> Boolean) {
                val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
                while (!check()) { assertTrue(System.nanoTime() < end, what); Thread.sleep(20) }
            }
            SpringService(config).use { service ->
                settings = service.startBound(config)
                await("Startup recovery must not stop at one unreadable game") {
                    client.send(HttpRequest.newBuilder(URI("http://127.0.0.1:${settings.healthPort()}/health/ready")).build(),
                        HttpResponse.BodyHandlers.ofString()).statusCode() == 200
                }
                // The workers reject the unreadable request and still run the readable game's request after it.
                await("The readable game's pending request must still run") {
                    text(db, "SELECT result FROM game_inbox WHERE request_id = '${s.pending}'") != null
                }
                assertEquals("accepted", GameWire.json.decodeFromString<GameResult>(text(db, "SELECT result FROM game_inbox WHERE request_id = '${s.pending}'")!!).outcome)
                val rejected = GameWire.json.decodeFromString<GameResult>(text(db, "SELECT result FROM game_inbox WHERE request_id = '$poison'")!!)
                assertEquals("rejected" to "internalError", rejected.outcome to rejected.reason)
                assertEquals("{\"type\":\"retiredCommand\"}", text(db, "SELECT command FROM game_inbox WHERE request_id = '$poison'"))

                val token = json.decodeFromString<SessionCreated>(request("/sessions",
                    body = json.encodeToString(CreateSession("ContainA@example.invalid", "synthetic password"))).body()).token
                val games = request("/v1/games", token)
                assertEquals(200, games.statusCode(), games.body())
                assertEquals(listOf(s.goodGame), json.decodeFromString<GameList>(games.body()).games.map { it.id })
                assertEquals(200, request("/v1/games/${s.goodGame}/head", token).statusCode())
                assertEquals(500 to "{\"code\":\"internal_error\"}", request("/v1/games/${s.badGame}/head", token).let { it.statusCode() to it.body() })
                val pods = request("/v1/pods", token)
                assertEquals(200, pods.statusCode(), pods.body())
                assertEquals(listOf(s.goodPod), json.decodeFromString<PodList>(pods.body()).pods.map { it.id })
                assertEquals(500, request("/v1/pods/${s.badPod}", token).statusCode())
                assertEquals(200, request("/v1/pod-presets", token).statusCode())
                assertEquals(200, request("/v1/decks", token).statusCode())
                assertEquals(200, request("/activity", token).statusCode())
            }
        }
    }
}
