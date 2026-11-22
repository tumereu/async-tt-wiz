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

/** Bot seats (owner 2026-11-21): pods with draft bots over HTTP, the bots moving on the server. */
class BotSeatsHttpTest {
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
            players = List(3) { account("bots-$it") }
            val cards = org.asyncttwiz.server.TestPrintings.supported().take(120)
            check(cards.size == 120) { "the test cards' catalog has ${cards.size} supported cards" }
            cube = DeckJson.format.decodeFromString(send(a, "POST", "/v1/cubes", CreateCube(UUID.randomUUID().toString(), "Bot cube",
                cards.map { org.asyncttwiz.server.TestPrintings.entry(it, 1) }), 201))
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
        fun create(settings: DraftSettings, status: Int = 201): String = send(a, "POST", "/v1/pods",
            CreatePod(UUID.randomUUID().toString(), "Bot pod", cube.cube.id, settings.mode, settings = settings), status)
        fun pod(settings: DraftSettings): String = json.decodeFromString<PodDetail>(create(settings)).id
        fun join(pod: String, vararg who: SessionCreated) = who.forEach {
            send(a, "POST", "/v1/pods/$pod/invitations", InviteToPod(UUID.randomUUID().toString(), it.account.accountId))
            send(it, "POST", "/v1/pods/$pod/accept", PodRequest(UUID.randomUUID().toString()))
        }
        fun invite(pod: String, who: SessionCreated) =
            send(a, "POST", "/v1/pods/$pod/invitations", InviteToPod(UUID.randomUUID().toString(), who.account.accountId))
        fun configure(pod: String, settings: DraftSettings, status: Int = 200) =
            send(a, "PUT", "/v1/pods/$pod", UpdatePod(UUID.randomUUID().toString(), settings = settings), status)
        fun start(pod: String, status: Int = 200) = send(a, "POST", "/v1/pods/$pod/start", PodRequest(UUID.randomUUID().toString()), status)
        fun autoStart(pod: String, enabled: Boolean, status: Int = 200) =
            send(a, "POST", "/v1/pods/$pod/auto-start", SetPodAutoStart(UUID.randomUUID().toString(), enabled), status)

        /** [who]'s move if they have one (the first cards, line or pile they can take); false when they have none. */
        fun act(who: SessionCreated, pod: String): Boolean {
            val detail = get(who, pod)
            val me = who.account.accountId
            val attempt = detail.draftAttemptId ?: return false
            detail.draft?.head?.let { head ->
                val keep = detail.settings.keepAt(head.pick - 1); val burn = detail.settings.burnAt(head.pick - 1)
                send(who, "POST", "/v1/pods/$pod/pick", PickPod(UUID.randomUUID().toString(), attempt, head.id, head.cards.take(keep).map { it.id },
                    if (detail.settings.keepsOnly(head.cards.size, keep.toLong(), head.pick - 1)) emptyList()
                    else head.cards.drop(keep).take(burn).map { it.id }))
                return true
            }
            detail.grid?.let { grid ->
                val head = grid.mine.headGridId ?: return false
                val board = grid.publicView.grids.single { it.id == head }
                val line = GridLineType.entries.flatMap { type -> (0..2).map { GridLine(type, it) } }
                    .first { line -> line.positions().any { board.positions[it] != null } }
                send(who, "POST", "/v1/pods/$pod/grid/select", SelectPodGrid(UUID.randomUUID().toString(), attempt, head, line.type, line.index))
                return true
            }
            val view = detail.limited ?: return false
            val turn = when (view.mode) {
                DraftMode.ROTISSERIE -> view.takeIf { it.currentPlayer == me }?.let {
                    PodTurn(UUID.randomUUID().toString(), attempt, LimitedAction.TAKE_CARD, it.turn, card = it.board.first { slot -> slot.pickedBy == null }.copy.id) }
                DraftMode.ROCHESTER -> view.packs.firstOrNull { it.currentPlayer == me }?.let { pack ->
                    PodTurn(UUID.randomUUID().toString(), attempt, LimitedAction.TAKE_CARD, pack.picks,
                        card = pack.slots.first { it.pickedBy == null }.copy.id, packId = pack.id) }
                DraftMode.WINSTON -> view.tables.withIndex().firstOrNull { it.value.currentPlayer == me }?.let { (index, table) ->
                    PodTurn(UUID.randomUUID().toString(), attempt, LimitedAction.TAKE_PILE, table.turn, pile = table.pile, table = index) }
                DraftMode.WINCHESTER -> view.tables.withIndex().firstOrNull { it.value.currentPlayer == me }?.let { (index, table) ->
                    PodTurn(UUID.randomUUID().toString(), attempt, LimitedAction.TAKE_PILE, table.turn,
                        pile = table.piles.indexOfFirst { it.isNotEmpty() }, table = index) }
                else -> null
            } ?: return false
            send(who, "POST", "/v1/pods/$pod/turn", turn)
            return true
        }

        /** The two people draft [pod] to its end, failing if the draft ever waits with neither of them able to move. */
        fun draftToTheEnd(pod: String, humans: List<SessionCreated> = listOf(a, b)) {
            var moves = 0
            while (get(a, pod).phase == PodPhase.DRAFTING) {
                check(moves++ < 500) { "the draft didn't end" }
                val detail = get(a, pod)
                // DRF-298: no bot is ever waited for
                assertTrue(detail.members.filter { it.bot }.all { it.packsWaiting == 0 }, "a bot holds a pick: ${detail.members}")
                assertTrue(humans.map { act(it, pod) }.any { it }, "the draft waits and no person can move: ${detail.members}")
            }
        }
        override fun close() = service.close()
    }

    private fun fixture(test: (Fixture) -> Unit) = FreshDatabase().start().use { db ->
        FoundationMigrations.migrate(db.settings)
        Fixture(db).use(test)
    }

    // DRF-035, DRF-296, DRF-297, DRF-298, DRF-301: a Booster pod with three bot seats doesn't start with its creator alone; with a
    // second person it starts, the bots pick at once on the server, everyone sees them as "Bot 1"… after the people, and the
    // two people draft it to its end; the Pods list names only people and the bots never build
    @Test fun `a pod with bot seats needs a second person and its bots draft on the server`() = fixture { f ->
        val pod = f.pod(DraftSettings(packsPerPlayer = 2, cardsPerPack = 5, keep = 1, burn = 1, bots = 3))
        assertEquals(3, f.get(f.a, pod).settings.bots)
        // Setup lists no bot as a member: the bots are the setting
        assertEquals(listOf(f.a.account.accountId), f.get(f.a, pod).members.map { it.account.accountId })
        assertEquals("not_enough_members", f.code(f.start(pod, 409)))
        f.join(pod, f.b)
        val started = json.decodeFromString<PodDetail>(f.start(pod))
        assertEquals(PodPhase.DRAFTING, started.phase)
        val people = setOf(f.a.account.accountId, f.b.account.accountId)
        for (who in listOf(f.a, f.b)) {
            val detail = f.get(who, pod)
            assertEquals(people, detail.members.take(2).map { it.account.accountId }.toSet())
            assertEquals(listOf(PodBots.identity(1), PodBots.identity(2), PodBots.identity(3)), detail.members.drop(2).map { it.account })
            assertTrue(detail.members.drop(2).all { it.bot && !it.building && !it.submitted })
            assertTrue(detail.members.take(2).none { it.bot })
            // The bots picked at once, every pack that reached them (also the next round's they opened early); each person
            // sees their own pack first
            assertTrue(detail.members.drop(2).all { it.picksMade >= 1 && it.packsWaiting == 0 }, "${detail.members}")
            assertEquals(5, detail.draft!!.head!!.cards.size)
            assertTrue(detail.members.take(2).all { it.packsWaiting > 0 })
        }
        // DRF-297: the Pods list's avatars are the people
        assertEquals(people, f.list(f.b).pods.single { it.id == pod }.members.map { it.accountId }.toSet())
        f.draftToTheEnd(pod)
        val done = f.get(f.a, pod)
        assertEquals(PodPhase.DRAFTED, done.phase)
        // Five seats, two packs of five, keep 1 and burn 1: three cards from each pack's five, the last card given on
        assertEquals(6, done.draft!!.pool.size)
        assertTrue(done.members.filter { it.bot }.all { it.picksMade > 0 && !it.building && !it.submitted })
        // DRF-301: the people build their decks; the bots don't
        assertTrue(done.members.filter { !it.bot }.all { it.building })
    }

    // DRF-150, DRF-163, DRF-296: bot seats count as players; Sealed and Arena take none; a mode's most is its limit less two
    @Test fun `bot seats count against the engine's limit and only modes with a draft take them`() = fixture { f ->
        assertEquals("invalid_settings", f.code(f.create(DraftSettings.booster().copy(bots = 7), 400)))
        assertEquals("invalid_settings", f.code(f.create(DraftSettings.arena().copy(bots = 1), 400)))
        assertEquals("invalid_settings", f.code(f.create(DraftSettings.sealed().copy(bots = 1), 400)))
        val pod = f.pod(DraftSettings.booster().copy(packsPerPlayer = 1, cardsPerPack = 5, bots = 6))
        assertEquals("invalid_settings", f.code(f.configure(pod, DraftSettings.grid().copy(grids = 2, bots = 3), 400)))
        f.configure(pod, DraftSettings.grid().copy(grids = 2, bots = 2))
        // Three people and two bots are five: one too many for Grid
        f.join(pod, f.b, f.c)
        assertEquals("too_many_members", f.code(f.start(pod, 409)))
        f.configure(pod, DraftSettings.grid().copy(grids = 2, bots = 1))
        assertEquals(PodPhase.DRAFTING, json.decodeFromString<PodDetail>(f.start(pod)).phase)

        // Arming counts the bots too
        val armed = f.pod(DraftSettings.winston().copy(packsPerPlayer = 1, cardsPerPack = 5, bots = 2))
        f.join(armed, f.b)
        f.invite(armed, f.c)
        assertEquals("too_many_players", f.code(f.autoStart(armed, true, 409)))
        f.configure(armed, DraftSettings.winston().copy(packsPerPlayer = 1, cardsPerPack = 5, bots = 1))
        assertTrue(json.decodeFromString<PodDetail>(f.autoStart(armed, true)).autoStart)
        // A change that puts the pod over the limit switches it off
        f.configure(armed, DraftSettings.winston().copy(packsPerPlayer = 1, cardsPerPack = 5, bots = 2))
        assertFalse(f.get(f.a, armed).autoStart)
    }

    // DRF-043, DRF-297 (owner, 2026-11-22): the server seats the bots spread between the people: two people and four bots always
    // sit person, bot, bot, person, bot, bot, which the draft history's seating (its seat ring) shows with the bots in it;
    // two people and three bots in Rochester person, bot, person, bot, bot; the people's places stay random
    @Test fun `the server spreads the bot seats evenly between the people`() = fixture { f ->
        val people = setOf(f.a.account.accountId, f.b.account.accountId)
        /** The bot groups after each person round the ring. */
        fun groups(seating: List<String>): List<Int> {
            val seats = seating.indices.filter { seating[it] in people }
            return seats.indices.map { i -> (seats[(i + 1) % seats.size] - seats[i] - 1).mod(seating.size) }
        }
        val firsts = mutableSetOf<Int>()
        repeat(6) {
            val pod = f.pod(DraftSettings(packsPerPlayer = 1, cardsPerPack = 5, keep = 1, burn = 0, bots = 4))
            f.join(pod, f.b)
            f.start(pod)
            val response = f.request(f.a, "GET", "/v1/pods/$pod/draft/history")
            assertEquals(200, response.statusCode(), response.body())
            val seating = json.decodeFromString<PodDraftHistory>(response.body()).seating
            assertEquals(people + PodBots.ids(4), seating.toSet())
            assertEquals(listOf(2, 2), groups(seating), "$seating")
            firsts += seating.indexOf(f.a.account.accountId)
        }
        // Six pods seat Alice in more than one place: the seating stays random
        assertTrue(firsts.size > 1, "Alice always sat in seat $firsts")
        val rochester = f.pod(DraftSettings.rochester().copy(packsPerPlayer = 1, cardsPerPack = 5, bots = 3))
        f.join(rochester, f.b)
        f.start(rochester)
        assertEquals(listOf(1, 2), groups(f.get(f.a, rochester).limited!!.seating).sorted())
        val grid = f.pod(DraftSettings.grid().copy(grids = 2, bots = 2))
        f.join(grid, f.b)
        f.start(grid)
        assertEquals(listOf(1, 1), groups(f.get(f.a, grid).grid!!.publicView.seating))
    }

    // DRF-298, DRF-299, DRF-300: every mode with bot seats drafts to its end with two people, the bots moving on the server
    // whenever the draft reaches them
    @Test fun `every mode with bot seats drafts to its end without waiting for a bot`() = fixture { f ->
        val modes = listOf(
            DraftSettings.customBooster().copy(packsPerPlayer = 1, cardsPerPack = 6, passes = listOf(DraftPass(2, 1), DraftPass(1, 0)), bots = 1),
            DraftSettings.grid().copy(grids = 2, bots = 2),
            DraftSettings.rochester().copy(packsPerPlayer = 1, cardsPerPack = 5, bots = 2),
            DraftSettings.rotisserie().copy(cardsPerPlayer = 5, bots = 6),
            DraftSettings.winston().copy(packsPerPlayer = 1, cardsPerPack = 5, bots = 2),
            DraftSettings.winchester().copy(packsPerPlayer = 1, cardsPerPack = 5, bots = 2))
        for (settings in modes) {
            val pod = f.pod(settings)
            f.join(pod, f.b)
            f.start(pod)
            f.draftToTheEnd(pod)
            val done = f.get(f.a, pod)
            assertEquals(PodPhase.DRAFTED, done.phase, "${settings.mode}")
            assertEquals(settings.bots, done.members.count { it.bot }, "${settings.mode}")
            assertTrue(done.members.filter { it.bot }.all { it.picksMade > 0 }, "${settings.mode}: ${done.members}")
            assertTrue(done.draft?.pool.orEmpty().isNotEmpty() || done.grid?.mine?.pool.orEmpty().isNotEmpty() ||
                done.limited?.pool.orEmpty().isNotEmpty(), "${settings.mode}: Alice drafted nothing")
        }
    }
}
