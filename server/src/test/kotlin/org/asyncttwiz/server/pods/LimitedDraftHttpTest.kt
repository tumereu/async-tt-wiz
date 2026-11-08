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

    // DRF-035, DRF-036, DRF-037, DRF-150, DRF-163: Start, arming and settings changes follow the engine's player limit.
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

    /** An approximate-booster set (no recipe): packs take the configured size. */
    private fun setUpload(): PodSetSource {
        val definitions = List(12) { index ->
            val card = CardDefinition("Set fixture $index", listOf(DefinitionFace(id(), "Set fixture $index")))
            SubmittedDefinition(DeckJson.digest(card), card)
        }
        val cards = definitions.mapIndexed { index, definition ->
            SetSourceCard(definition.digest, "00000000-0000-4000-8000-${index.toString().padStart(12, '0')}",
                listOf(SetCardRarity.COMMON, SetCardRarity.UNCOMMON, SetCardRarity.RARE)[index % 3])
        }
        return PodSetSource(SetSourceSnapshot("TST", "Uploaded test set", cards), definitions)
    }

    // DRF-151, DRF-139: Sealed has no draft phase; every pool is private and goes straight to Build.
    @Test fun `Sealed deals private pools from a cube or boosters and goes straight to Build`() = fixture { f ->
        val pod = f.create(DraftSettings.sealed().copy(cardsPerPlayer = 40))
        f.join(pod, f.b, f.c)
        val started = json.decodeFromString<PodDetail>(f.start(pod))
        assertEquals(PodPhase.DRAFTED, started.phase)
        val details = listOf(f.a, f.b, f.c).map { f.get(it, pod) }
        for (detail in details) {
            val limited = detail.limited!!
            assertEquals(DraftMode.SEALED, limited.mode)
            assertEquals(40, limited.pool.size)
            assertEquals(limited.pool.map { it.id }.toSet(), detail.build!!.copies.map { it.copyId }.toSet())
            assertNull(detail.draft); assertNull(detail.grid)
            // Only the caller's own cards are named.
            assertEquals(limited.pool.map { it.definition }.toSet(), detail.definitions.map { it.digest }.toSet())
        }
        assertEquals(120, details.flatMap { it.limited!!.pool }.map { it.id }.distinct().size)
        for (who in listOf(f.a, f.b, f.c)) {
            assertEquals(PodAttention.BUILD, f.list(who).pods.single { it.id == pod }.attention)
            assertEquals(1, f.count(ActivityKind.POD_DRAFT_COMPLETE, pod, who))
            assertEquals(0, f.count(ActivityKind.POD_YOUR_PICK, pod, who))
        }
        val attempt = details[1].draftAttemptId!!
        assertTrue(json.decodeFromString<PodDetail>(f.send(f.b, "POST", "/v1/pods/$pod/submit", SubmitPodDeck(id(), attempt))).submitted)
        // From a set: each player opens their own boosters.
        val set = json.decodeFromString<PodDetail>(f.send(f.a, "POST", "/v1/pods", CreatePod(id(), "Set sealed", setSource = setUpload(),
            mode = DraftMode.SEALED, settings = DraftSettings.sealed().copy(packsPerPlayer = 2, cardsPerPack = 10)), 201)).id
        f.join(set, f.b)
        assertEquals(PodPhase.DRAFTED, json.decodeFromString<PodDetail>(f.start(set)).phase)
        assertEquals(listOf(20, 20), listOf(f.a, f.b).map { f.get(it, set).limited!!.pool.size })
    }

    // DRF-152, DRF-136, DRF-087: snake turns over HTTP, "Your pick" when a turn begins, a public board, private pools.
    @Test fun `Rotisserie HTTP snake turns notifications retries and end`() = fixture { f ->
        val pod = f.create(DraftSettings.rotisserie().copy(cardsPerPlayer = 5))
        f.join(pod, f.b)
        val started = json.decodeFromString<PodDetail>(f.start(pod))
        assertEquals(PodPhase.DRAFTING, started.phase)
        val seating = started.limited!!.seating.map(f::session)
        val (first, second) = seating
        val attempt = started.draftAttemptId!!
        fun turn(who: SessionCreated, status: Int = 200, card: String? = null, at: Int? = null): String {
            val view = f.get(who, pod).limited!!
            val free = card ?: view.board.first { it.pickedBy == null }.copy.id
            return f.turn(who, pod, PodTurn(id(), attempt, LimitedAction.TAKE_CARD, at ?: view.turn, free), status)
        }
        assertEquals("not_your_turn", f.code(turn(second, 409, at = 0)))
        assertEquals("stale_turn", f.code(turn(first, 409, at = 1)))
        assertEquals(PodAttention.YOUR_PICK, f.list(first.let { it }).pods.single().attention)
        assertEquals(DraftMode.ROTISSERIE, f.list(first).pods.single().mode)
        assertEquals(1, f.waiting(first).count { it.gameId == pod })
        assertTrue(f.waiting(second).none { it.gameId == pod })
        // A retried request is answered once.
        val view = f.get(first, pod).limited!!
        val retry = PodTurn(id(), attempt, LimitedAction.TAKE_CARD, 0, view.board.first().copy.id)
        val answer = f.turn(first, pod, retry)
        assertEquals(answer, f.turn(first, pod, retry))
        val order = mutableListOf(first)
        while (f.get(f.a, pod).phase == PodPhase.DRAFTING) {
            val current = f.session(f.get(f.a, pod).limited!!.currentPlayer!!)
            order += current
            turn(current)
        }
        assertEquals(listOf(first, second, second, first, first, second, second, first, first, second), order)
        // Each turn that begins notifies once; the second of two picks in a row does not.
        assertEquals(3, f.count(ActivityKind.POD_YOUR_PICK, pod, first))
        assertEquals(3, f.count(ActivityKind.POD_YOUR_PICK, pod, second))
        for (who in seating) {
            val detail = f.get(who, pod)
            assertEquals(PodPhase.DRAFTED, detail.phase)
            assertEquals(5, detail.limited!!.pool.size)
            assertEquals(5, detail.build!!.copies.size)
            // The board is public: everyone sees all ten cards and who took them.
            assertEquals(10, detail.limited!!.board.count { it.pickedBy != null })
            assertEquals(1, f.count(ActivityKind.POD_DRAFT_COMPLETE, pod, who))
        }
        val booster = f.create(DraftSettings.booster().copy(packsPerPlayer = 1, cardsPerPack = 3))
        f.join(booster, f.b)
        val draft = json.decodeFromString<PodDetail>(f.start(booster))
        assertEquals("wrong_draft_mode", f.code(f.turn(f.a, booster, PodTurn(id(), draft.draftAttemptId!!, LimitedAction.TAKE_CARD, 0, "x"), 409)))
    }

    // DRF-154, DRF-155, DRF-139: open packs are public, later rounds stay hidden, picks follow each pack's snake order.
    @Test fun `Rochester HTTP open packs hidden rounds parallel picks and end`() = fixture { f ->
        val pod = f.create(DraftSettings.rochester().copy(packsPerPlayer = 2, cardsPerPack = 6))
        f.join(pod, f.b)
        val started = json.decodeFromString<PodDetail>(f.start(pod))
        val attempt = started.draftAttemptId!!
        val seating = started.limited!!.seating.map(f::session)
        for (who in seating) assertEquals(1, f.count(ActivityKind.POD_YOUR_PICK, pod, who))
        val first = f.get(f.a, pod).limited!!
        assertEquals(2, first.packs.size)
        assertEquals(first.packs, f.get(f.b, pod).limited!!.packs)
        // Only round one's cards are named to anyone.
        val shown = first.packs.flatMap { pack -> pack.slots.map { it.copy.definition } }.toSet()
        assertEquals(shown, f.get(f.a, pod).definitions.map { it.digest }.toSet())
        val pack = first.packs.single { it.currentPlayer == seating[0].account.accountId }
        val other = first.packs.single { it != pack }
        assertEquals("not_your_turn", f.code(f.turn(seating[1], pod, PodTurn(id(), attempt, LimitedAction.TAKE_CARD, 0,
            pack.slots[0].copy.id, pack.id), 409)))
        assertEquals("unknown_card", f.code(f.turn(seating[0], pod, PodTurn(id(), attempt, LimitedAction.TAKE_CARD, 0,
            other.slots[0].copy.id, pack.id), 409)))
        var moves = 0
        while (f.get(f.a, pod).phase == PodPhase.DRAFTING) {
            for (who in seating) {
                val view = f.get(who, pod).limited ?: continue
                val mine = view.packs.firstOrNull { it.currentPlayer == who.account.accountId } ?: continue
                f.turn(who, pod, PodTurn(id(), attempt, LimitedAction.TAKE_CARD, mine.picks,
                    mine.slots.first { it.pickedBy == null }.copy.id, mine.id))
                assertTrue(++moves <= 24)
            }
        }
        assertEquals(24, moves)
        for (who in seating) {
            val detail = f.get(who, pod)
            assertEquals(PodPhase.DRAFTED, detail.phase)
            assertEquals(12, detail.limited!!.pool.size)
            assertEquals(1, f.count(ActivityKind.POD_DRAFT_COMPLETE, pod, who))
        }
    }

    // DRF-156, DRF-157, DRF-139: Winston piles are hidden until looked at and stay visible to whoever looked.
    @Test fun `Winston HTTP hides unseen piles keeps seen cards face up and plays to the end`() = fixture { f ->
        val pod = f.create(DraftSettings.winston().copy(packsPerPlayer = 1, cardsPerPack = 10))
        f.join(pod, f.b)
        val started = json.decodeFromString<PodDetail>(f.start(pod))
        val attempt = started.draftAttemptId!!
        val (x, y) = started.limited!!.seating.map(f::session)
        val looking = f.get(x, pod)
        assertEquals(listOf(1, 0, 0), looking.limited!!.piles.map { pile -> pile.count { it != null } })
        assertEquals(looking.limited!!.piles[0].map { it!!.definition }.toSet(), looking.definitions.map { it.digest }.toSet())
        val waiting = f.get(y, pod)
        assertTrue(waiting.limited!!.piles.flatten().all { it == null })
        assertTrue(waiting.definitions.isEmpty()) // nothing face down is named
        assertEquals(17, waiting.limited!!.stack)
        assertEquals(1, f.count(ActivityKind.POD_YOUR_PICK, pod, x))
        assertEquals(0, f.count(ActivityKind.POD_YOUR_PICK, pod, y))
        fun act(who: SessionCreated, action: LimitedAction, pile: Int, turn: Int, status: Int = 200) =
            f.turn(who, pod, PodTurn(id(), attempt, action, turn, pile = pile), status)
        assertEquals("wrong_pile", f.code(act(x, LimitedAction.TAKE_PILE, 1, 0, 409)))
        assertEquals("not_your_turn", f.code(act(y, LimitedAction.TAKE_PILE, 0, 0, 409)))
        act(x, LimitedAction.PASS_PILE, 0, 0)
        val after = f.get(x, pod).limited!!
        assertEquals(1, after.pile)
        assertEquals(listOf(true, false), after.piles[0].map { it != null }) // the card added face down stays a back
        assertEquals(listOf(true), after.piles[1].map { it != null })
        assertEquals(0, f.count(ActivityKind.POD_YOUR_PICK, pod, y))
        act(x, LimitedAction.TAKE_PILE, 1, 0)
        assertEquals(1, f.count(ActivityKind.POD_YOUR_PICK, pod, y))
        assertEquals(listOf(null), f.get(y, pod).limited!!.picks.single().cards)
        var guard = 0
        while (f.get(f.a, pod).phase == PodPhase.DRAFTING) {
            val current = f.session(f.get(f.a, pod).limited!!.currentPlayer!!)
            val view = f.get(current, pod).limited!!
            val canPass = view.stack > 0 || view.piles.drop(view.pile + 1).any { it.isNotEmpty() }
            act(current, if (view.piles[view.pile].size >= 2 || !canPass) LimitedAction.TAKE_PILE else LimitedAction.PASS_PILE,
                view.pile, view.turn)
            assertTrue(++guard < 200)
        }
        assertEquals(20, listOf(x, y).sumOf { f.get(it, pod).limited!!.pool.size })
        for (who in listOf(x, y)) assertEquals(1, f.count(ActivityKind.POD_DRAFT_COMPLETE, pod, who))
    }

    // DRF-158, DRF-159, DRF-139: face-up piles for everyone, whole piles taken in seat order, the stack only counted.
    @Test fun `Winchester HTTP public piles turn order notifications and end`() = fixture { f ->
        val pod = f.create(DraftSettings.winchester().copy(packsPerPlayer = 1, cardsPerPack = 6))
        f.join(pod, f.b, f.c)
        val started = json.decodeFromString<PodDetail>(f.start(pod))
        val attempt = started.draftAttemptId!!
        val seating = started.limited!!.seating.map(f::session)
        val views = seating.map { f.get(it, pod) }
        assertTrue(views.all { it.limited!!.piles == views[0].limited!!.piles && it.limited!!.piles.flatten().all { card -> card != null } })
        assertEquals(14, views[0].limited!!.stack)
        // Only the face-up piles are named; the stack's cards are not.
        assertEquals(views[0].limited!!.piles.flatten().map { it!!.definition }.toSet(), views[0].definitions.map { it.digest }.toSet())
        assertEquals("not_your_turn", f.code(f.turn(seating[1], pod, PodTurn(id(), attempt, LimitedAction.TAKE_PILE, 0, pile = 0), 409)))
        val order = mutableListOf<SessionCreated>()
        while (f.get(f.a, pod).phase == PodPhase.DRAFTING) {
            val view = f.get(f.a, pod).limited!!
            val current = f.session(view.currentPlayer!!)
            order += current
            f.turn(current, pod, PodTurn(id(), attempt, LimitedAction.TAKE_PILE, view.turn, pile = view.piles.indexOfFirst { it.isNotEmpty() }))
            assertTrue(order.size < 60)
        }
        assertEquals(order.indices.map { seating[it % 3] }, order)
        assertEquals(18, seating.sumOf { f.get(it, pod).limited!!.pool.size })
        // A member's turn notifies them each time it comes round.
        for ((index, who) in seating.withIndex()) assertEquals(order.indices.count { it % 3 == index }, f.count(ActivityKind.POD_YOUR_PICK, pod, who))
        // The Picks view shows every take face up.
        assertTrue(f.get(seating[0], pod).limited!!.picks.all { take -> take.cards.all { it != null } })
    }

    // DRF-158, DRF-023: Winchester tables, each with its own stack, piles and turn order one seat later; both players pick
    // at once, a move names its table, and the draft ends when every table is empty.
    @Test fun `Winchester HTTP tables split the cards and let both players pick at once`() = fixture { f ->
        assertEquals("invalid_settings", f.code(f.send(f.a, "POST", "/v1/pods", CreatePod(id(), "Limited pod", f.cube.cube.id,
            DraftMode.WINCHESTER, settings = DraftSettings.winchester().copy(tables = 5)), 400)))
        val pod = f.create(DraftSettings.winchester().copy(packsPerPlayer = 1, cardsPerPack = 10, tables = 2))
        f.join(pod, f.b)
        val started = json.decodeFromString<PodDetail>(f.start(pod))
        val attempt = started.draftAttemptId!!
        val (x, y) = started.limited!!.seating.map(f::session)
        val view = f.get(y, pod)
        val tables = view.limited!!.tables
        assertEquals(listOf(x.account.accountId, y.account.accountId), tables.map { it.currentPlayer })
        assertEquals(listOf(6, 6), tables.map { it.stack })
        // Every face-up pile of every table is named.
        assertEquals(tables.flatMap { it.piles.flatten() }.map { it.definition }.toSet(), view.definitions.map { it.digest }.toSet())
        // Both have a pick waiting (and a notification) at once.
        for (who in listOf(x, y)) assertEquals(1, f.count(ActivityKind.POD_YOUR_PICK, pod, who))
        assertEquals(listOf(1, 1), listOf(x, y).map { who -> f.get(who, pod).members.single { it.account.accountId == who.account.accountId }.packsWaiting })
        fun take(who: SessionCreated, table: Int, turn: Int, pile: Int = 0, status: Int = 200) =
            f.turn(who, pod, PodTurn(id(), attempt, LimitedAction.TAKE_PILE, turn, pile = pile, table = table), status)
        assertEquals("not_your_turn", f.code(take(x, 1, 0, status = 409)))
        assertEquals("wrong_table", f.code(take(y, 2, 0, status = 409)))
        take(y, 1, 0)
        take(x, 0, 0)
        val after = f.get(x, pod).limited!!
        assertEquals(listOf(y.account.accountId, x.account.accountId), after.tables.map { it.currentPlayer })
        assertEquals(listOf(1, 1), after.tables.map { it.turn })
        var guard = 0
        while (f.get(f.a, pod).phase == PodPhase.DRAFTING) {
            val board = f.get(f.a, pod).limited!!
            val (index, table) = board.tables.withIndex().first { it.value.currentPlayer != null }
            take(f.session(table.currentPlayer!!), index, table.turn, table.piles.indexOfFirst { it.isNotEmpty() })
            assertTrue(++guard < 60)
        }
        assertEquals(20, listOf(x, y).sumOf { f.get(it, pod).limited!!.pool.size })
        for (who in listOf(x, y)) assertEquals(1, f.count(ActivityKind.POD_DRAFT_COMPLETE, pod, who))
    }
}
