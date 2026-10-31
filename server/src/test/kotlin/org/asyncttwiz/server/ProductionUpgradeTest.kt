package org.asyncttwiz.server

import java.net.InetAddress
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.accounts.AccountService
import org.asyncttwiz.server.cubes.CubeService
import org.asyncttwiz.server.database.FoundationMigrations
import org.asyncttwiz.server.database.FreshDatabase
import org.asyncttwiz.server.database.TestPostgres
import org.asyncttwiz.server.decks.DeckService
import org.asyncttwiz.server.game.GameService
import org.asyncttwiz.server.lobby.LobbyService
import org.asyncttwiz.server.pods.PodPresetService
import org.asyncttwiz.server.pods.PodService
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

/**
 * Upgrading the production database keeps every account, deck, cube, preset, game (started, finished, waiting,
 * invited), chat and pod (drafting, grid, finished, with its challenge game) readable and correct.
 *
 * `upgrade/production.json` is a frozen copy of the rows a production server at schema [PRODUCTION_SCHEMA] stores,
 * recorded through the real services. The test loads it at that schema, runs every later migration, then reads and
 * continues everything through the current server. It fails when a migration loses or breaks stored data, or when
 * a code change can no longer read data production already holds. Re-record the fixture only when production moves
 * to a newer schema, or for a change the owner approved as invalidating stored data:
 * `./scripts/gradle.sh :server:test -PupgradeFixture=record --tests org.asyncttwiz.server.ProductionUpgradeTest`
 */
class ProductionUpgradeTest {
    private companion object {
        /** Flyway version the production server runs (0.1.8 is V45); bump it, and re-record, when production upgrades. */
        const val PRODUCTION_SCHEMA = "45"
        const val PASSWORD = "fixture password"
        /** Bookkeeping that every database has its own copy of. */
        val OWN_TABLES = setOf("flyway_schema_history", "service_writer")
    }
    private val json = DeckJson.format
    private fun id() = UUID.randomUUID().toString()

    /** What the recorded services returned, by name; compared as plain values so later additive protocol fields don't matter. */
    private class Expect(private val values: JsonObject, private val json: Json) {
        inline operator fun <reified T> invoke(key: String): T = json.decodeFromJsonElement(values.getValue(key))
    }
    private inline fun <reified T> value(value: T): JsonElement = json.encodeToJsonElement(value)

    private fun ready(runtime: ServiceRuntime) {
        runtime.start(); val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
        while (!runtime.ready()) { assertTrue(System.nanoTime() < end); Thread.sleep(20) }
    }
    private fun <T> query(db: TestPostgres, sql: String, read: (java.sql.ResultSet) -> T): List<T> = db.connection().use { c ->
        c.createStatement().executeQuery(sql).use { rows -> buildList { while (rows.next()) add(read(rows)) } } }

    private class Stream(val games: GameService, val actor: AccountService.Principal, val gameId: String) {
        val hello = GameHello(UUID.randomUUID().toString(), UUID.randomUUID().toString()); val connection = UUID.randomUUID()
        val stream = games.open(actor, hello, connection, gameId, UUID.randomUUID().toString())
        fun execute(action: GameAction): GameResult {
            val summary = games.head(actor, gameId).summary
            val scope = if (action is GameAction.Play || action is GameAction.AcceptResult || action is GameAction.SendChat)
                GameScope(gameId, playId = summary.playId)
                else GameScope(gameId, summary.preparationRound, if (action == GameAction.AcceptInvitation) summary.invitationId else null)
            val command = GameCommand(UUID.randomUUID().toString(), scope, action, stream.streamId, stream.generation)
            assertTrue(games.admit(actor, hello, connection, command) is GameReceipt); assertTrue(games.executeOne())
            return games.results(actor, gameId, listOf(command.requestId)).statuses.single().result!!
                .also { assertEquals("accepted", it.outcome, "$action: ${it.reason}") }
        }
    }

    // ---- Recording (only with -PupgradeFixture=record) ----

    @Test fun `record the production fixture`() {
        assumeTrue(System.getProperty("asyncttwiz.recordUpgradeFixture") == "true", "Only with -PupgradeFixture=record")
        val file = Path.of(System.getProperty("asyncttwiz.root"), "server/src/test/resources/upgrade/production.json")
        Files.createDirectories(file.parent); Files.writeString(file, record())
        println("Recorded $file at schema $PRODUCTION_SCHEMA")
    }

    private fun record(): String = FreshDatabase().start().use { db ->
        val expect = ServiceRuntime(db.settings).use { runtime -> ready(runtime); dataset(runtime) }
        // The production schema predates V46, which shows lobby decks that already finished a game against the same
        // opponent: production still holds them hidden.
        db.connection().use { it.createStatement().execute(
            "UPDATE player_preparation SET deck_shown = false WHERE game_id = '${expect.getValue("waiting").jsonPrimitive.content}' AND account_id = '${expect.getValue("ann").jsonPrimitive.content}'") }
        val columns = FreshDatabase(migrated = false).start().use { production ->
            Flyway.configure().configuration(FoundationMigrations.flyway(production.settings).configuration).target(PRODUCTION_SCHEMA).load().migrate()
            query(production, "SELECT table_name, column_name FROM information_schema.columns WHERE table_schema = 'public'") {
                it.getString(1) to it.getString(2) }.groupBy({ it.first }, { it.second })
        }
        val current = query(db, "SELECT tablename FROM pg_tables WHERE schemaname = 'public' ORDER BY tablename") { it.getString(1) }
        val tables = (current - OWN_TABLES).associateWith { table ->
            val rows = json.parseToJsonElement(query(db, "SELECT coalesce(json_agg(r ORDER BY r::text), '[]') FROM (SELECT row_to_json(t) AS r FROM \"$table\" t) s") {
                it.getString(1) }.single()).jsonArray
            val kept = columns[table]
            assertTrue(kept != null || rows.isEmpty(), "$table has rows but no table at schema $PRODUCTION_SCHEMA")
            JsonArray(rows.map { row -> JsonObject(row.jsonObject.filterKeys { it in kept!! }) })
        }.filterValues { it.isNotEmpty() }
        // One row per line keeps re-recorded fixtures reviewable.
        buildString {
            append("{\"schema\":").append(JsonPrimitive(PRODUCTION_SCHEMA)).append(",\n\"expect\":").append(expect.toString())
            append(",\n\"tables\":{")
            append(tables.entries.joinToString(",") { (table, rows) -> "\n\"$table\":[" + rows.joinToString(",") { "\n$it" } + "]" })
            append("}}\n")
        }
    }

    private val basics = BasicLand.entries.zip(listOf("W", "U", "B", "R", "G")).map { (land, mana) ->
        val card = CardDefinition(land.cardName, listOf(DefinitionFace(id(), land.cardName,
            typeLine = "Basic Land — ${land.cardName}", rulesText = "({T}: Add {$mana}.)")), provider = "mtgjson")
        SubmittedDefinition(DeckJson.digest(card), card)
    }

    /** Everything a pair of players builds up, through the real services. */
    private fun dataset(runtime: ServiceRuntime): JsonObject {
        val accounts = AccountService(runtime)
        fun account(name: String): AccountService.Principal {
            accounts.register(name, "$name@example.invalid", PASSWORD)
            return accounts.authenticate(accounts.login("$name@example.invalid", PASSWORD)!!.token)!!
        }
        val ann = account("Ann"); val ben = account("Ben")
        val decks = DeckService(runtime); val games = GameService(runtime); val lobby = LobbyService(runtime)
        val pods = PodService(runtime); val presets = PodPresetService(runtime)
        fun card(name: String, type: String = "Creature — Bear") = CardDefinition(name, listOf(DefinitionFace(id(), name,
            manaCost = "{1}{G}", typeLine = type, rulesText = "Fixture text.", corner = "2/2"))).let { SubmittedDefinition(DeckJson.digest(it), it) }
        fun deck(owner: AccountService.Principal, name: String, cards: List<SubmittedDefinition>): SavedDeck {
            assertEquals(200, decks.submit(owner, SubmitDefinitions(id(), cards)).status)
            return json.decodeFromString(decks.create(owner, CreateDeck(id(), name, cards.mapIndexed { index, card ->
                SavedDeckEntry(if (index == cards.lastIndex) SavedDeckSection.SIDEBOARD else SavedDeckSection.MAINBOARD, card.digest, 4) })).body)
        }
        val annDeck = deck(ann, "Ann's deck", (1..4).map { card("Ann card $it") })
        deck(ann, "Ann's spare deck", listOf(card("Spare card", "Instant"), card("Spare land", "Land")))
        val benDeck = deck(ben, "Ben's deck", (1..4).map { card("Ben card $it") })
        lobby.add(ann, AddContact(id(), ben.account.accountId))
        fun game(name: String): Pair<Stream, Stream> {
            val game = json.decodeFromString<GameSummary>(lobby.create(ann, CreateInvitation(id(), ben.account.accountId, name)).body)
            return Stream(games, ann, game.id) to Stream(games, ben, game.id)
        }
        fun prepare(a: Stream, b: Stream) {
            b.execute(GameAction.AcceptInvitation)
            a.execute(GameAction.SelectDeck(annDeck.deck.id, annDeck.deck.revision)); b.execute(GameAction.SelectDeck(benDeck.deck.id, benDeck.deck.revision))
        }

        // A started game with moves by both players and chat.
        val (s1, s2) = game("Started game"); prepare(s1, s2)
        s1.execute(GameAction.SetStarter(StarterChoice(ann.account.accountId)))
        s1.execute(GameAction.Ready); s2.execute(GameAction.Ready)
        s1.execute(GameAction.Play(PlayAction.AdjustLife(ann.account.accountId, "-3")))
        s2.execute(GameAction.Play(PlayAction.AdjustLife(ben.account.accountId, "2")))
        s1.execute(GameAction.Play(PlayAction.PassAttention))
        val chat = listOf("Good luck", "You too", "Your move")
        for ((stream, text) in listOf(s1, s2, s1).zip(chat)) stream.execute(GameAction.SendChat(text))
        games.chatRead(ben, s1.gameId, ReadChat(games.chat(ben, s1.gameId).messages.first().id))
        // A game played to a result and back in its lobby, and another lobby game already holding Ann's deck.
        val (f1, f2) = game("Finished game"); prepare(f1, f2)
        f1.execute(GameAction.Ready); f2.execute(GameAction.Ready)
        f1.execute(GameAction.Play(PlayAction.SuggestResult(SuggestedResult.WIN)))
        f2.execute(GameAction.AcceptResult(games.head(ann, f1.gameId).play!!.board.resultSuggestion!!.id))
        val (w1, w2) = game("Waiting game"); w2.execute(GameAction.AcceptInvitation)
        w1.execute(GameAction.SelectDeck(annDeck.deck.id, annDeck.deck.revision))
        val (pending, _) = game("Pending invitation")

        val cubeCards = (1..30).map { card("Cube card $it", if (it % 3 == 0) "Instant" else "Creature — Elf") }
        assertEquals(200, decks.submit(ann, SubmitDefinitions(id(), cubeCards + basics)).status)
        val cube = json.decodeFromString<SavedCube>(CubeService(runtime).create(ann, CreateCube(id(), "Fixture cube",
            cubeCards.map { SavedCubeEntry(it.digest, 1) })).body)
        fun detail(who: AccountService.Principal, pod: String) = json.decodeFromString<PodDetail>(pods.read(who, pod).body)
        fun pod(name: String, settings: DraftSettings): String {
            val pod = json.decodeFromString<PodDetail>(pods.create(ann, CreatePod(id(), name, cube.cube.id, settings.mode)).body).id
            assertEquals(200, pods.invite(ann, pod, InviteToPod(id(), ben.account.accountId)).status)
            assertEquals(200, pods.invitation(ben, pod, PodRequest(id()), true).status)
            assertEquals(200, pods.update(ann, pod, UpdatePod(id(), settings = settings)).status)
            assertEquals(200, pods.start(ann, pod, PodRequest(id())).status, name)
            return pod
        }
        // Mid-draft: Ann picked once, Ben marked a card, Ann keeps notes.
        val drafting = pod("Drafting pod", DraftSettings(packsPerPlayer = 2, cardsPerPack = 4))
        detail(ann, drafting).let { d -> val head = d.draft!!.head!!
            assertEquals(200, pods.pick(ann, drafting, PickPod(id(), d.draftAttemptId!!, head.id, head.cards.take(d.settings.keep).map { it.id })).status) }
        val benHead = detail(ben, drafting).let { d -> d.draft!!.head!!.also { head ->
            assertEquals(200, pods.saveMarks(ben, drafting, SetPodDraftMarks(d.draftAttemptId!!, head.id, listOf(head.cards.first().id))).status) } }
        assertEquals(200, pods.saveNotes(ann, drafting, SetPodDeckNotes(detail(ann, drafting).draftAttemptId!!,
            PodDeckNotes("Go wide", listOf(1, 0, 0, 0, 0, 0, 0, 2)))).status)
        // A grid draft with one selection made.
        val grid = pod("Grid pod", DraftSettings(mode = DraftMode.GRID, grids = 2))
        val gridMover = listOf(ann, ben).first { detail(it, grid).grid!!.mine.headGridId != null }
        detail(gridMover, grid).let { d -> val board = d.grid!!.publicView.grids.single { it.id == d.grid!!.mine.headGridId }
            val row = (0..2).first { row -> (0..2).any { board.positions[row * 3 + it] != null } }
            assertEquals(200, pods.selectGrid(gridMover, grid, SelectPodGrid(id(), d.draftAttemptId!!, board.id, GridLineType.ROW, row)).status) }
        // A finished draft (one-card packs complete at once), both decks submitted, and a challenge game from it.
        val finishedPod = pod("Finished pod", DraftSettings(packsPerPlayer = 3, cardsPerPack = 1, sideboardLimit = 1))
        for (who in listOf(ann, ben)) {
            val d = detail(who, finishedPod); val attempt = d.draftAttemptId!!
            assertEquals(200, pods.move(who, finishedPod, MovePodCopy(id(), attempt, d.build!!.copies[1].copyId, PodDeckCategory.SIDE)).status)
            assertEquals(200, pods.basics(who, finishedPod, SetPodBasics(id(), attempt, PodDeckBasics(8, 8, 8, 8, 8))).status)
            assertEquals(200, pods.submit(who, finishedPod, SubmitPodDeck(id(), attempt)).status)
        }
        val challenge = pods.challenge(ann, finishedPod, ChallengeFromPod(id(), ben.account.accountId, "Pod game", basics))
        assertEquals(201, challenge.status, challenge.body)
        val podGame = json.decodeFromString<GameSummary>(challenge.body).id

        for (preset in listOf(SavePodPreset("Cube night", DraftSettings(cardsPerPack = 10), cubeId = cube.cube.id),
                SavePodPreset("Set night", DraftSettings(packsPerPlayer = 4), setCode = "FIX", setName = "Fixture set")))
            assertTrue(presets.save(ann, preset).status in 200..201)

        val started = games.head(ann, s1.gameId).play!!
        val drafted = detail(ann, drafting); val submitted = detail(ann, finishedPod).deck!!
        fun deckNames(who: AccountService.Principal, drafts: Boolean) =
            json.decodeFromString<DeckList>(decks.list(who, null, drafts).body).decks.map { it.name }.sorted()
        val expect = JsonObject(mapOf(
            "ann" to value(ann.account.accountId),
            "ben" to value(ben.account.accountId),
            "annGames" to value(json.decodeFromString<GameList>(lobby.list(ann, null, false).body).games.map { it.id }.sorted()),
            "benInvitations" to value(json.decodeFromString<GameList>(lobby.list(ben, null, true).body).games.map { it.id }.sorted()),
            "started" to value(s1.gameId),
            "startedPlay" to value(started.id),
            "life" to value(started.board.players.associate { it.accountId to it.life }),
            "mutations" to value(games.history(ann, s1.gameId, started.id, "0").mutations.size),
            "chat" to value(games.chat(ann, s1.gameId).messages.map { it.text }),
            "benUnread" to value(games.chatRead(ben, s1.gameId).unread),
            "finished" to value(f1.gameId),
            "waiting" to value(w1.gameId),
            "podGame" to value(podGame),
            "pods" to value(json.decodeFromString<PodList>(pods.list(ann).body).pods.associate { it.id to it.phase.name }),
            "drafting" to value(drafting),
            "annPool" to value(drafted.draft!!.pool.size),
            "benMarkKeeps" to value(listOf(benHead.cards.first().id)),
            "annNote" to value(drafted.notes!!.text),
            "annCounters" to value(drafted.notes!!.counters),
            "grid" to value(grid),
            "gridLog" to value(detail(ann, grid).grid!!.publicView.log.size),
            "finishedPod" to value(finishedPod),
            "annSubmittedCopies" to value(submitted.copies.size),
            "annSubmittedBasics" to value(submitted.basics.total),
            "decks" to value(deckNames(ann, false)),
            "annDeckEntries" to value(json.decodeFromString<SavedDeck>(decks.read(ann, annDeck.deck.id).body).entries.size),
            "draftDecks" to value(deckNames(ann, true)),
            "cubes" to value(json.decodeFromString<CubeList>(CubeService(runtime).list(ann, null).body).cubes.map { it.name }),
            "presets" to value(json.decodeFromString<PodPresetList>(presets.list(ann).body).presets.associate { it.name to it.settings.cardsPerPack })))
        assertEquals(listOf(pending.gameId, podGame).sorted(), json.decodeFromJsonElement<List<String>>(expect.getValue("benInvitations")))
        return expect
    }

    // ---- The upgrade ----

    private fun load(db: TestPostgres, tables: Map<String, JsonArray>) = db.connection().use { c ->
        c.autoCommit = false
        // Rows are inserted table by table; foreign keys hold in the recorded database already.
        c.createStatement().execute("SET session_replication_role = replica")
        for ((table, rows) in tables) c.prepareStatement(
            "INSERT INTO \"$table\" OVERRIDING SYSTEM VALUE SELECT * FROM json_populate_recordset(null::\"$table\", ?::json)").use {
            it.setString(1, rows.toString()); assertEquals(rows.size, it.executeUpdate(), table)
        }
        c.createStatement().executeQuery("SELECT table_name, column_name FROM information_schema.columns " +
            "WHERE table_schema = 'public' AND is_identity = 'YES'").use { rows -> buildList { while (rows.next()) add(rows.getString(1) to rows.getString(2)) } }
            .forEach { (table, column) -> c.createStatement().execute(
                "SELECT setval(pg_get_serial_sequence('$table', '$column'), coalesce((SELECT max($column) FROM $table), 0) + 1, false)") }
        c.commit()
    }

    @Test fun `production data stays readable and correct after upgrading to the latest schema`() {
        assumeTrue(System.getProperty("asyncttwiz.recordUpgradeFixture") != "true", "Recording")
        val text = javaClass.getResource("/upgrade/production.json")?.readText()
        assertNotNull(text, "Record the fixture: -PupgradeFixture=record"); text!!
        val fixture = json.parseToJsonElement(text).jsonObject
        val schema = fixture.getValue("schema").jsonPrimitive.content
        val e = Expect(fixture.getValue("expect").jsonObject, json)
        val tables = fixture.getValue("tables").jsonObject.mapValues { it.value.jsonArray }
        val started = e<String>("started"); val startedPlay = e<String>("startedPlay"); val drafting = e<String>("drafting")
        FreshDatabase(migrated = false).start().use { db ->
            val flyway = Flyway.configure().configuration(FoundationMigrations.flyway(db.settings).configuration)
            flyway.target(schema).load().migrate()
            load(db, tables)
            val pending = FoundationMigrations.flyway(db.settings).info().pending().size
            assertEquals(pending, FoundationMigrations.migrate(db.settings)); assertEquals(0, FoundationMigrations.migrate(db.settings))
            // No migration deleted anything.
            for ((table, rows) in tables)
                assertEquals(rows.size, query(db, "SELECT count(*) FROM \"$table\"") { it.getInt(1) }.single(), "$table rows after upgrade")

            fun port() = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1")).use { it.localPort }
            val settings = ServiceSettings(db.settings, URI("https://synthetic.invalid"), InetAddress.getByName("127.0.0.1"), port(),
                InetAddress.getByName("127.0.0.1"), port())
            val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()
            fun request(path: String, token: String? = null, body: String? = null): HttpResponse<String> {
                val builder = HttpRequest.newBuilder(URI("http://127.0.0.1:${settings.publicPort()}$path")).timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .method(if (body == null) "GET" else "POST", body?.let(HttpRequest.BodyPublishers::ofString) ?: HttpRequest.BodyPublishers.noBody())
                token?.let { builder.header("Authorization", "Bearer $it") }
                return client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
            }
            SpringService(settings).use { service ->
                service.start()
                // Readiness includes startup recovery, which reads every started game.
                val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
                while (client.send(HttpRequest.newBuilder(URI("http://127.0.0.1:${settings.healthPort()}/health/ready")).build(),
                        HttpResponse.BodyHandlers.ofString()).statusCode() != 200) { assertTrue(System.nanoTime() < end, "Not ready"); Thread.sleep(20) }
                fun login(name: String) = json.decodeFromString<SessionCreated>(request("/sessions",
                    body = json.encodeToString(CreateSession("$name@example.invalid", PASSWORD))).body()).token
                val ann = login("Ann"); val ben = login("Ben")
                fun get(path: String, token: String): String = request(path, token).let {
                    assertEquals(200, it.statusCode(), "$path ${it.body()}"); it.body() }

                assertEquals(e<List<String>>("annGames"), json.decodeFromString<GameList>(get("/v1/games", ann)).games.map { it.id }.sorted())
                assertEquals(e<List<String>>("benInvitations"), json.decodeFromString<GameList>(get("/v1/invitations", ben)).games.map { it.id }.sorted())
                for (game in e<List<String>>("annGames")) get("/v1/games/$game/head", ann)
                assertEquals(e<Int>("mutations"), json.decodeFromString<GameplayHistoryPage>(
                    get("/v1/games/${started}/plays/${startedPlay}/mutations?after=0", ben)).mutations.size)
                assertEquals(e<List<String>>("chat"), json.decodeFromString<ChatPage>(get("/v1/games/${started}/chat", ben)).messages.map { it.text })

                assertEquals(e<Map<String, String>>("pods"), json.decodeFromString<PodList>(get("/v1/pods", ann)).pods.associate { it.id to it.phase.name })
                for (pod in e<Map<String, String>>("pods").keys) for (token in listOf(ann, ben)) get("/v1/pods/$pod", token)
                assertEquals(e<List<String>>("decks"), json.decodeFromString<DeckList>(get("/v1/decks", ann)).decks.map { it.name }.sorted())
                assertEquals(e<List<String>>("draftDecks"), json.decodeFromString<DeckList>(get("/v1/decks?drafts=true", ann)).decks.map { it.name }.sorted())
                assertEquals(e<List<String>>("cubes"), json.decodeFromString<CubeList>(get("/v1/cubes", ann)).cubes.map { it.name })
                assertEquals(e<Map<String, Int>>("presets"), json.decodeFromString<PodPresetList>(get("/v1/pod-presets", ann)).presets.associate { it.name to it.settings.cardsPerPack })
                get("/activity", ann); get("/activity", ben)
            }

            // Services: the same values in detail, and every kind of stored game and pod carries on.
            ServiceRuntime(db.settings).use { runtime ->
                ready(runtime)
                val accounts = AccountService(runtime)
                fun principal(name: String) = accounts.authenticate(accounts.login("$name@example.invalid", PASSWORD)!!.token)!!
                val ann = principal("Ann"); val ben = principal("Ben")
                val games = GameService(runtime); val pods = PodService(runtime); val decks = DeckService(runtime)
                val play = games.head(ann, started).play!!
                assertEquals(startedPlay, play.id)
                assertEquals(e<Map<String, String>>("life"), play.board.players.associate { it.accountId to it.life })
                assertEquals(e<Int>("benUnread"), games.chatRead(ben, started).unread)
                Stream(games, ben, started).execute(GameAction.Play(PlayAction.AdjustLife(e<String>("ben"), "-1")))
                Stream(games, ann, started).execute(GameAction.SendChat("Still here after the upgrade"))
                assertEquals(e<Int>("mutations") + 1, games.history(ann, started, startedPlay, "0").mutations.size)
                // V46: Ann's deck finished a game against Ben, so the waiting game now shows it.
                assertTrue(games.head(ann, e<String>("waiting")).preparation!!.players.single { it.accountId == e<String>("ann") }.deckShown)
                val benDeck = json.decodeFromString<DeckList>(decks.list(ben, null).body).decks.single()
                Stream(games, ben, e<String>("waiting")).execute(GameAction.SelectDeck(benDeck.id, benDeck.revision))
                Stream(games, ann, e<String>("finished")).execute(GameAction.Ready)
                Stream(games, ben, e<String>("podGame")).execute(GameAction.AcceptInvitation)

                fun detail(who: AccountService.Principal, pod: String) = json.decodeFromString<PodDetail>(pods.read(who, pod).body)
                val annDraft = detail(ann, drafting)
                assertEquals(e<Int>("annPool"), annDraft.draft!!.pool.size)
                assertEquals(e<String>("annNote") to e<List<Int>>("annCounters"), annDraft.notes!!.text to annDraft.notes!!.counters)
                val benDraft = detail(ben, drafting)
                assertEquals(e<List<String>>("benMarkKeeps"), benDraft.draft!!.marks!!.keeps)
                val head = benDraft.draft!!.head!!
                assertEquals(200, pods.pick(ben, drafting, PickPod(id(), benDraft.draftAttemptId!!, head.id,
                    head.cards.take(benDraft.settings.keep).map { it.id })).status)
                assertEquals(e<Int>("gridLog"), detail(ann, e<String>("grid")).grid!!.publicView.log.size)
                val submitted = detail(ann, e<String>("finishedPod")).deck!!
                assertEquals(e<Int>("annSubmittedCopies") to e<Long>("annSubmittedBasics"), submitted.copies.size to submitted.basics.total)
                assertEquals(e<Int>("annDeckEntries"), json.decodeFromString<DeckList>(decks.list(ann, null).body).decks
                    .single { it.name == "Ann's deck" }.let { json.decodeFromString<SavedDeck>(decks.read(ann, it.id).body).entries.size })
            }
        }
    }
}
