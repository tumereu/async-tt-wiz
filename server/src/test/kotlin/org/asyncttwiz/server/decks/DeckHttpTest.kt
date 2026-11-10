package org.asyncttwiz.server.decks

import java.net.Socket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.serialization.encodeToString
import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.ServiceSettings
import org.asyncttwiz.server.SpringService
import org.asyncttwiz.server.EphemeralPorts
import org.asyncttwiz.server.TestPrintings
import org.asyncttwiz.server.startBound
import org.asyncttwiz.server.database.FoundationMigrations
import org.asyncttwiz.server.database.FreshDatabase
import org.asyncttwiz.server.database.TestPostgres
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** Actual normal public handlers, writer fence, generated jOOQ and Testcontainers PostgreSQL17. */
class DeckHttpTest {
    private val json = DeckJson.format
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()
    private fun id() = UUID.randomUUID().toString()
    private fun settings(db: TestPostgres) = EphemeralPorts.settings(db.settings)
    private fun ready(settings: ServiceSettings) {
        val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
        do {
            if (client.send(HttpRequest.newBuilder(URI("http://127.0.0.1:${settings.healthPort()}/health/ready"))
                    .timeout(Duration.ofSeconds(2)).build(), HttpResponse.BodyHandlers.ofString()).statusCode() == 200) return
            Thread.sleep(20)
        } while (System.nanoTime() < end)
        fail<Unit>("Service did not become ready")
    }
    private fun request(settings: ServiceSettings, method: String, path: String, body: String? = null,
                        token: String? = null, media: String = "application/json"): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(URI("http://127.0.0.1:${settings.publicPort()}$path"))
            .timeout(Duration.ofSeconds(25)).header("Content-Type", media)
            .method(method, body?.let(HttpRequest.BodyPublishers::ofString) ?: HttpRequest.BodyPublishers.noBody())
        if (token != null) builder.header("Authorization", "Bearer $token")
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString()).also {
            assertEquals("no-store", it.headers().firstValue("Cache-Control").orElseThrow())
            assertFalse(it.headers().firstValue("Set-Cookie").isPresent)
        }
    }
    private inline fun <reified T> post(settings: ServiceSettings, path: String, input: T, token: String) =
        request(settings, "POST", path, json.encodeToString(input), token)
    private fun account(settings: ServiceSettings, name: String): SessionCreated {
        assertEquals(201, request(settings, "POST", "/accounts", json.encodeToString(RegisterAccount(name, "$name@example.invalid", "password😀"))).statusCode())
        val response = request(settings, "POST", "/sessions", json.encodeToString(CreateSession("$name@example.invalid", "password😀")))
        assertEquals(201, response.statusCode())
        return json.decodeFromString(response.body())
    }
    private fun card(name: String = "Arbitrary unknown card", faceId: String = "00000000-0000-4000-8000-000000000001") =
        CardDefinition(name, listOf(DefinitionFace(faceId, name, rulesText = "<b>Not HTML</b>\n", corner = "")),
            provider = "arbitrary", sourceVersion = "retained v1", printingId = "same-external-id")
    private fun submitted(card: CardDefinition) = SubmittedDefinition(DeckJson.digest(card), card)
    private fun count(db: TestPostgres, table: String): Int = db.connection().use { connection ->
        connection.createStatement().executeQuery("SELECT count(*) FROM $table").use { it.next(); it.getInt(1) }
    }
    private fun entry(name: String, quantity: Int, section: SavedDeckSection = SavedDeckSection.MAINBOARD) = TestPrintings.entry(name, quantity, section)
    private fun saved(name: String, quantity: Int, section: SavedDeckSection = SavedDeckSection.MAINBOARD) =
        SavedDeckEntry(section, TestPrintings.digest(name), quantity)
    /** A card from before the current catalog, as a deck may still hold it: stored in [deck] of [account] by SQL. */
    private fun keep(db: TestPostgres, account: String, deck: String, position: Int, card: SubmittedDefinition, section: SavedDeckSection, quantity: Int) =
        db.connection().use { c ->
            c.prepareStatement("INSERT INTO definition VALUES (?,1,?) ON CONFLICT DO NOTHING").use { it.setString(1, card.digest); it.setString(2, DeckJson.canonical(card.definition)); it.executeUpdate() }
            c.createStatement().execute("INSERT INTO deck_entry VALUES ('$deck','$account',$position,'${section.name}','${card.digest}',$quantity)")
        }

    // Card data from the server's catalog (owner 2026-11-10): a save names printings; the server builds the cards
    @Test fun `HTTP deck saves by printing exact deck concurrent original outcome restart and private readback`() {
        FreshDatabase().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            val config = settings(db)
            val first = "Storm Spark"; val side = "Ember Cub"; val multi = "Ember Path // Ember Crag"
            // The same card twice in Main is one entry; a double-faced card is one card whichever face names it
            val create = CreateDeck(id(), "  Exact é 😀 name  ", listOf(entry(first, 10), entry(multi, 1), entry(side, 3, SavedDeckSection.SIDEBOARD),
                entry(first, 2), CardListEntry(SavedDeckSection.MAINBOARD, TestPrintings.definition(multi).faces[0].otherFaceIds.single(), 1)))
            val entries = listOf(saved(first, 12), saved(multi, 2), saved(side, 3, SavedDeckSection.SIDEBOARD))
            lateinit var owner: SessionCreated
            lateinit var foreign: SessionCreated
            lateinit var saved: SavedDeck
            lateinit var original: String
            SpringService(config).use { service ->
                val settings = service.startBound(config); ready(settings)
                owner = account(settings, "owner"); foreign = account(settings, "foreign")
                val pool = Executors.newFixedThreadPool(6)
                try {
                    val replies = pool.invokeAll(List(6) { Callable { post(settings, "/v1/decks", create, owner.token) } })
                        .map { it.get(30, TimeUnit.SECONDS) }
                    assertTrue(replies.all { it.statusCode() == 201 }, replies.map { it.body() }.toString())
                    original = replies.first().body()
                    assertTrue(replies.all { it.body() == original })
                    saved = json.decodeFromString(original)
                } finally { pool.shutdownNow(); assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS)) }
                assertEquals(create.name, saved.deck.name); assertEquals(entries, saved.entries)
                assertEquals("1", saved.deck.revision); assertEquals(TestPrintings.digest(first), saved.deck.cover!!.definition)
                assertEquals(1, count(db, "saved_deck")); assertEquals(3, count(db, "definition"))
                assertEquals(3, count(db, "deck_entry")); assertEquals(1, count(db, "account_request"))
                assertEquals(409, post(settings, "/v1/decks", create.copy(name = "changed"), owner.token).statusCode())
                val foreignId = post(settings, "/v1/decks", create, foreign.token)
                assertEquals(409, foreignId.statusCode()); assertFalse(foreignId.body().contains(saved.deck.id))
                // A printing the catalog doesn't have, and a card from before in a new deck, are refused
                for (bad in listOf(CardListEntry(SavedDeckSection.MAINBOARD, id(), 1), CardListEntry(SavedDeckSection.MAINBOARD, TestPrintings.digest(first), 1))) {
                    val unavailable = post(settings, "/v1/decks", create.copy(requestId = id(), entries = create.entries + bad), owner.token)
                    assertEquals(400, unavailable.statusCode()); assertEquals("definitions_unavailable", json.decodeFromString<DeckError>(unavailable.body()).code)
                }
                val deckPath = "/v1/decks/${saved.deck.id}"
                assertEquals(401, request(settings, "GET", deckPath).statusCode())
                val denied = request(settings, "GET", deckPath, token = foreign.token)
                val missingDeck = request(settings, "GET", deckPath.replace(saved.deck.id, id()), token = foreign.token)
                assertEquals(404, denied.statusCode()); assertEquals(missingDeck.body(), denied.body())
                assertFalse(denied.body().contains(first))
                val deniedCards = post(settings, "$deckPath/definitions", ReadCubeDefinitions(listOf(TestPrintings.digest(first))), foreign.token)
                assertEquals(404, deniedCards.statusCode()); assertFalse(deniedCards.body().contains(first))
                assertEquals(emptyList<DeckSummary>(), json.decodeFromString<DeckList>(request(settings, "GET", "/v1/decks", token = foreign.token).body()).decks)
                val missing = post(settings, "/v1/decks/${saved.deck.id}/definitions", ReadCubeDefinitions(listOf("sha256:${"0".repeat(64)}")), owner.token)
                assertEquals(404, missing.statusCode())
                // The upload of card data is gone
                assertEquals(404, post(settings, "/v1/definitions", ReadCubeDefinitions(listOf(TestPrintings.digest(first))), owner.token).statusCode())
            }
            // New normal service instance, retained actual PostgreSQL. No public catalog/art service exists.
            SpringService(config).use { service ->
                val settings = service.startBound(config); ready(settings)
                assertEquals(original, post(settings, "/v1/decks", create, owner.token).body()) // lost-response retry
                val list = json.decodeFromString<DeckList>(request(settings, "GET", "/v1/decks", token = owner.token).body())
                assertEquals(listOf(saved.deck), list.decks); assertNull(list.next)
                assertEquals(saved, json.decodeFromString<SavedDeck>(request(settings, "GET", "/v1/decks/${saved.deck.id}", token = owner.token).body()))
                val cards = post(settings, "/v1/decks/${saved.deck.id}/definitions", ReadCubeDefinitions(entries.map { it.definition }), owner.token)
                assertEquals(200, cards.statusCode())
                assertEquals(listOf(first, multi, side).map(TestPrintings::submitted), json.decodeFromString<SubmittedDefinitions>(cards.body()).definitions)
                // Card data is stored once, whoever saves it
                assertEquals(201, post(settings, "/v1/decks", create.copy(requestId = id()), foreign.token).statusCode())
                assertEquals(3, count(db, "definition"))
                assertEquals(204, request(settings, "DELETE", "/session", token = owner.token).statusCode())
                assertEquals(401, post(settings, "/v1/decks", create, owner.token).statusCode())
                assertEquals(1, json.decodeFromString<DeckList>(request(settings, "GET", "/v1/decks", token = foreign.token).body()).decks.size)
                println("PASS authenticated saves by printing with merged entries and whole double-faced cards, exact main/side/name, six concurrent/lost-response original outcomes, identity/account/kind conflicts, unknown printings refused, private reads, actual service restart and revoked-token refusal")
            }
        }
    }

    @Test fun `deck input validation and strict HTTP retain no partial deck`() {
        FreshDatabase().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            val config = settings(db)
            SpringService(config).use { service ->
                val settings = service.startBound(config); ready(settings)
                val owner = account(settings, "owner")
                val create = CreateDeck(id(), "Good", listOf(entry("Storm Spark", 7)))
                // Malformed bodies of a request of their own: one that can be read is kept with its refusal
                val valid = json.encodeToString(create.copy(requestId = id()))
                // Escape the invalid scalar on the wire: Java BodyPublishers otherwise replaces it
                // with a valid '?' during UTF-8 encoding, which is not malformed server input.
                for (body in listOf(valid.dropLast(10), valid.dropLast(1) + ",\"extra\":true}", "{}", valid.replace("Good", "\\ud800")))
                    assertEquals(400, request(settings, "POST", "/v1/decks", body, owner.token).statusCode())
                assertEquals(415, request(settings, "POST", "/v1/decks", valid, owner.token, "text/plain").statusCode())
                assertEquals(413, request(settings, "POST", "/v1/decks", " ".repeat(16 * 1024 * 1024 + 1), owner.token).statusCode())
                assertEquals(401, request(settings, "POST", "/v1/decks", valid).statusCode())
                Socket("127.0.0.1", settings.publicPort()).use { socket ->
                    socket.getOutputStream().write(("POST /v1/decks HTTP/1.1\r\nHost: localhost\r\nAuthorization: Bearer ${owner.token}\r\n" +
                        "Content-Type: application/json\r\nContent-Length: ${valid.length + 100}\r\n\r\n" + valid.take(30)).toByteArray())
                    socket.getOutputStream().flush(); socket.shutdownOutput()
                    socket.soTimeout = 10000; socket.getInputStream().readBytes()
                }
                assertEquals(0, count(db, "definition")); assertEquals(0, count(db, "saved_deck"))
                assertEquals(201, post(settings, "/v1/decks", create, owner.token).statusCode())
                for (bad in listOf(create.copy(requestId = id(), name = " "), create.copy(requestId = id(), entries = emptyList()),
                    create.copy(requestId = id(), entries = create.entries.map { it.copy(quantity = -1) }),
                    create.copy(requestId = id(), entries = create.entries.map { it.copy(quantity = 1001) }),
                    create.copy(requestId = id(), entries = create.entries.map { it.copy(printingId = "not a printing") }),
                    create.copy(requestId = id(), cover = TestPrintings.digest("Ember Cub")))) {
                    val rejected = post(settings, "/v1/decks", bad, owner.token)
                    assertEquals(400, rejected.statusCode()); assertEquals(rejected.body(), post(settings, "/v1/decks", bad, owner.token).body())
                    assertEquals(409, post(settings, "/v1/decks", create.copy(requestId = bad.requestId), owner.token).statusCode())
                }
                assertEquals(1, count(db, "saved_deck")); assertEquals(1, count(db, "deck_entry")); assertEquals(1, count(db, "definition"))
                assertEquals(201, post(settings, "/v1/decks", create, owner.token).statusCode())
                println("PASS malformed/unknown/oversize/media/interrupted HTTP and invalid names/quantities/printings/covers store nothing; bad saves keep the good deck and their rejection outcomes")
            }
        }
    }

    @Test fun `database failure rolls back effects identity and outcome and same request can recover`() {
        FreshDatabase().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            val config = settings(db)
            SpringService(config).use { service ->
                val settings = service.startBound(config); ready(settings)
                val owner = account(settings, "owner")
                val failDigest = listOf("Storm Spark", "Ember Cub").maxOf(TestPrintings::digest)
                db.connection().use { it.createStatement().execute("""
                    CREATE FUNCTION fail_definition() RETURNS trigger LANGUAGE plpgsql AS ${'$'}${'$'}
                    BEGIN IF NEW.digest = '$failDigest' THEN RAISE EXCEPTION 'test-owned interruption'; END IF; RETURN NEW; END ${'$'}${'$'};
                    CREATE TRIGGER fail_definition BEFORE INSERT ON definition FOR EACH ROW EXECUTE FUNCTION fail_definition();
                """.trimIndent()) }
                val create = CreateDeck(id(), "Rollback", listOf(entry("Storm Spark", 7), entry("Ember Cub", 3, SavedDeckSection.SIDEBOARD)))
                assertEquals(503, post(settings, "/v1/decks", create, owner.token).statusCode())
                assertEquals(0, count(db, "definition")); assertEquals(0, count(db, "saved_deck"))
                assertEquals(0, count(db, "request_identity")); assertEquals(0, count(db, "account_request"))
                db.connection().use { it.createStatement().execute("DROP TRIGGER fail_definition ON definition; DROP FUNCTION fail_definition()") }
                ready(settings)
                assertEquals(201, post(settings, "/v1/decks", create, owner.token).statusCode())
                assertEquals(2, count(db, "definition")); assertEquals(1, count(db, "saved_deck"))
                val second = CreateDeck(id(), "Rollback", listOf(entry("Storm Spark", 7), entry("Storm Spark", 3, SavedDeckSection.SIDEBOARD)))
                db.connection().use { it.createStatement().execute("""
                    CREATE FUNCTION fail_second_entry() RETURNS trigger LANGUAGE plpgsql AS ${'$'}${'$'}
                    BEGIN IF NEW.position = 1 THEN RAISE EXCEPTION 'test-owned interruption'; END IF; RETURN NEW; END ${'$'}${'$'};
                    CREATE TRIGGER fail_second BEFORE INSERT ON deck_entry FOR EACH ROW EXECUTE FUNCTION fail_second_entry();
                """.trimIndent()) }
                assertEquals(503, post(settings, "/v1/decks", second, owner.token).statusCode())
                assertEquals(1, count(db, "saved_deck")); assertEquals(2, count(db, "deck_entry"))
                assertEquals(1, count(db, "request_identity")); assertEquals(1, count(db, "account_request"))
                db.connection().use { it.createStatement().execute("DROP TRIGGER fail_second ON deck_entry; DROP FUNCTION fail_second_entry()") }
                ready(settings)
                assertEquals(201, post(settings, "/v1/decks", second, owner.token).statusCode())
                assertEquals(2, count(db, "saved_deck")); assertEquals(4, count(db, "deck_entry"))
                println("PASS real SQL failure storing card data and after deck/first entry rolls back all effects/request/outcome; writer recovery and unchanged identity retries create exactly once")
            }
        }
    }

    @Test fun `owned list keyset pages preserve equal names and maximum quantity without deck legality rules`() {
        FreshDatabase().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            val config = settings(db)
            SpringService(config).use { service ->
                val settings = service.startBound(config); ready(settings)
                val owner = account(settings, "owner")
                val created = (1..101).map {
                    val response = post(settings, "/v1/decks", CreateDeck(id(), "Same name", listOf(
                        entry("Storm Spark", 1000, SavedDeckSection.SIDEBOARD))), owner.token)
                    if (response.statusCode() != 201) println("DIAGNOSTIC pagination create index=$it status=${response.statusCode()} retained=${count(db, "saved_deck")}")
                    assertEquals(201, response.statusCode(), response.body())
                    json.decodeFromString<SavedDeck>(response.body()).deck.id
                }.toSet()
                val page1 = json.decodeFromString<DeckList>(request(settings, "GET", "/v1/decks", token = owner.token).body())
                assertEquals(100, page1.decks.size); assertNotNull(page1.next)
                val page2 = json.decodeFromString<DeckList>(request(settings, "GET", "/v1/decks?after=${page1.next}", token = owner.token).body())
                assertEquals(1, page2.decks.size); assertNull(page2.next)
                assertEquals(created, (page1.decks + page2.decks).map { it.id }.toSet())
                assertEquals(400, request(settings, "GET", "/v1/decks?after=not-an-id", token = owner.token).statusCode())
                println("PASS 101 ordinary HTTP creates with equal names and sideboard-only maximum budget quantities, complete nonoverlapping 100/1 keyset pages, invalid cursor refusal")
            }
        }
    }

    // LOB-055
    @Test fun `draft deck listing and Save as deck copy routes`() {
        FreshDatabase().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            val config = settings(db)
            SpringService(config).use { service ->
                val settings = service.startBound(config); ready(settings)
                val owner = account(settings, "copier"); val other = account(settings, "stranger")
                val source = json.decodeFromString<SavedDeck>(post(settings, "/v1/decks", CreateDeck(id(), "Source", listOf(
                    entry("Storm Spark", 40))), owner.token).body())
                // Ordinary decks never carry the draft marker on the wire.
                assertFalse(request(settings, "GET", "/v1/decks", token = owner.token).body().contains("draft"))
                assertEquals(DeckList(emptyList()), json.decodeFromString<DeckList>(request(settings, "GET", "/v1/decks?drafts=true", token = owner.token).body()))
                assertEquals(400, request(settings, "GET", "/v1/decks?drafts=true&after=${source.deck.id}", token = owner.token).statusCode())
                val copied = post(settings, "/v1/decks/${source.deck.id}/copy", CopyDeck(id()), owner.token)
                assertEquals(201, copied.statusCode(), copied.body())
                val copy = json.decodeFromString<SavedDeck>(copied.body())
                assertNotEquals(source.deck.id, copy.deck.id); assertEquals(source.entries, copy.entries); assertEquals("Source", copy.deck.name)
                assertEquals(setOf(source.deck.id, copy.deck.id), json.decodeFromString<DeckList>(
                    request(settings, "GET", "/v1/decks", token = owner.token).body()).decks.map { it.id }.toSet())
                assertEquals(404, post(settings, "/v1/decks/${source.deck.id}/copy", CopyDeck(id()), other.token).statusCode())
                assertEquals(405, request(settings, "GET", "/v1/decks/${source.deck.id}/copy", token = owner.token).statusCode())
                assertEquals(404, post(settings, "/v1/decks/${source.deck.id}/clone", CopyDeck(id()), owner.token).statusCode())
            }
        }
    }

    // LOB-040 (owner playtest 2026-11-06): a deck's card data is read in one request, not one per card (the deck
    // service's per-account limit); only the owner's deck, only its own cards
    @Test fun `a deck's card data is read in one request`() {
        FreshDatabase().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            val config = settings(db)
            SpringService(config).use { service ->
                val settings = service.startBound(config); ready(settings)
                val owner = account(settings, "batch-owner"); val other = account(settings, "batch-stranger")
                val names = listOf("Storm Spark", "Ember Cub", "Cinder Hound")
                val cards = names.map(TestPrintings::submitted)
                val outside = TestPrintings.submitted("Ember Herald")
                post(settings, "/v1/decks", CreateDeck(id(), "Elsewhere", listOf(entry("Ember Herald", 1))), owner.token)
                val deck = json.decodeFromString<SavedDeck>(post(settings, "/v1/decks", CreateDeck(id(), "Batch", listOf(
                    entry(names[0], 20), entry(names[1], 20), entry(names[1], 2, SavedDeckSection.SIDEBOARD), entry(names[2], 3, SavedDeckSection.SIDEBOARD))),
                    owner.token).body())
                val path = "/v1/decks/${deck.deck.id}/definitions"
                val read = post(settings, path, ReadCubeDefinitions(cards.map { it.digest }), owner.token)
                assertEquals(200, read.statusCode(), read.body())
                assertEquals(SubmittedDefinitions(cards), json.decodeFromString<SubmittedDefinitions>(read.body()))
                val denied = post(settings, path, ReadCubeDefinitions(listOf(cards[0].digest)), other.token)
                assertEquals(404, denied.statusCode()); assertFalse(denied.body().contains(names[0]))
                assertEquals(404, post(settings, path, ReadCubeDefinitions(listOf(cards[0].digest, outside.digest)), owner.token).statusCode())
                assertEquals(400, post(settings, path, ReadCubeDefinitions(emptyList()), owner.token).statusCode())
                assertEquals(404, post(settings, "/v1/decks/${id()}/definitions", ReadCubeDefinitions(listOf(cards[0].digest)), owner.token).statusCode())
                assertEquals(405, request(settings, "GET", path, token = owner.token).statusCode())
            }
        }
    }

    // LOB-058
    @Test fun `owned metadata deletion serialization privacy and original retries never upsert or rewrite definitions`() {
        FreshDatabase().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            val config = settings(db)
            SpringService(config).use { service ->
                val settings = service.startBound(config); ready(settings)
                val owner = account(settings, "owner"); val foreign = account(settings, "foreign")
                val first = TestPrintings.submitted("Storm Spark"); val second = TestPrintings.submitted("Ember Cub")
                val outside = TestPrintings.submitted("Cinder Hound")
                val entries = listOf(saved("Storm Spark", 12), saved("Ember Cub", 3, SavedDeckSection.SIDEBOARD))
                val saved = json.decodeFromString<SavedDeck>(post(settings, "/v1/decks", CreateDeck(id(), "Original",
                    listOf(entry("Storm Spark", 12), entry("Ember Cub", 3, SavedDeckSection.SIDEBOARD))), owner.token).body())
                val path = "/v1/decks/${saved.deck.id}"
                fun update(value: UpdateDeck, token: String = owner.token, target: String = path) =
                    request(settings, "PUT", target, json.encodeToString(value), token)
                fun read() = json.decodeFromString<SavedDeck>(request(settings, "GET", path, token = owner.token).body())
                assertEquals(12L, saved.deck.mainboard); assertEquals(3L, saved.deck.sideboard)
                assertEquals(first.digest, saved.deck.cover!!.definition)
                val rename = UpdateDeck(id(), name = " Exact renamed é 🧙 ")
                assertEquals(401, request(settings, "PUT", path, json.encodeToString(rename)).statusCode())
                assertEquals(404, update(rename.copy(requestId = id()), foreign.token).statusCode())
                assertEquals(update(rename.copy(requestId = id()), foreign.token).body(), update(rename.copy(requestId = id()), target = "/v1/decks/${id()}").body())
                val invalid = update(UpdateDeck(id(), cover = outside.digest)); assertEquals(400, invalid.statusCode())
                assertFalse(invalid.body().contains(outside.definition.name)); assertEquals("1", read().deck.revision)
                val original = update(rename); assertEquals(200, original.statusCode(), original.body())
                val cover = UpdateDeck(id(), cover = second.digest)
                val pool = Executors.newFixedThreadPool(4)
                try {
                    val responses = pool.invokeAll(List(4) { Callable { update(cover) } }).map { it.get(30, TimeUnit.SECONDS) }
                    assertTrue(responses.all { it.statusCode() == 200 }); assertEquals(1, responses.map { it.body() }.distinct().size)
                } finally { pool.shutdownNow(); assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS)) }
                assertEquals("3", read().deck.revision); assertEquals(second.digest, read().deck.cover!!.definition)
                assertEquals(rename.name, read().deck.name); assertEquals(entries, read().entries)
                assertEquals(original.body(), update(rename).body()) // older original, not current state or another mutation
                assertEquals("3", read().deck.revision)
                assertEquals(409, update(rename.copy(name = "changed intent")).statusCode())
                assertEquals(409, update(rename, target = "/v1/decks/${id()}").statusCode())
                val list = json.decodeFromString<DeckList>(request(settings, "GET", "/v1/decks", token = owner.token).body())
                assertEquals(read().deck, list.decks.single())
                assertEquals(2, count(db, "definition")); assertEquals(2, count(db, "deck_entry"))
                val deletion = DeleteDeck(id())
                assertEquals(404, request(settings, "DELETE", path, json.encodeToString(DeleteDeck(id())), foreign.token).statusCode())
                val deleted = request(settings, "DELETE", path, json.encodeToString(deletion), owner.token)
                assertEquals(200, deleted.statusCode()); assertEquals(DeckDeleted(saved.deck.id), json.decodeFromString<DeckDeleted>(deleted.body()))
                assertEquals(deleted.body(), request(settings, "DELETE", path, json.encodeToString(deletion), owner.token).body())
                assertEquals(404, update(UpdateDeck(id(), name = "Do not recreate")).statusCode())
                assertEquals(404, request(settings, "GET", path, token = owner.token).statusCode())
                assertEquals(404, request(settings, "POST", "$path/definitions", json.encodeToString(ReadCubeDefinitions(listOf(first.digest))), owner.token).statusCode())
                assertTrue(json.decodeFromString<DeckList>(request(settings, "GET", "/v1/decks", token = owner.token).body()).decks.isEmpty())
                assertEquals(0, count(db, "saved_deck")); assertEquals(0, count(db, "deck_entry"))
                assertEquals(2, count(db, "definition"))
                assertEquals(original.body(), update(rename).body()); assertEquals(0, count(db, "saved_deck"))
                println("PASS actual HTTP owned rename/sideboard cover/counts, four duplicate updates/revisions, unchanged immutable entries, account/target/intent binding, private refusal, exact old outcomes after deletion and no upsert/cascade to definitions")
            }
        }
    }

    @Test fun `metadata and deletion effects roll back with outcome failure and retain exact retries`() {
        FreshDatabase().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            val config = settings(db)
            SpringService(config).use { service ->
                val settings = service.startBound(config); ready(settings)
                val owner = account(settings, "owner")
                val saved = json.decodeFromString<SavedDeck>(post(settings, "/v1/decks", CreateDeck(id(), "Retained", listOf(
                    entry("Storm Spark", 2))), owner.token).body())
                val path = "/v1/decks/${saved.deck.id}"
                for (method in listOf("PUT", "DELETE")) {
                    val requestId = id()
                    val input = if (method == "PUT") json.encodeToString(UpdateDeck(requestId, name = "Renamed")) else json.encodeToString(DeleteDeck(requestId))
                    db.connection().use { it.createStatement().execute("""
                        CREATE FUNCTION fail_deck_outcome() RETURNS trigger LANGUAGE plpgsql AS ${'$'}${'$'}
                        BEGIN IF NEW.request_id = '$requestId' THEN RAISE EXCEPTION 'test-owned outcome interruption'; END IF; RETURN NEW; END ${'$'}${'$'};
                        CREATE TRIGGER fail_deck_outcome BEFORE INSERT ON account_request FOR EACH ROW EXECUTE FUNCTION fail_deck_outcome();
                    """.trimIndent()) }
                    val before = request(settings, "GET", path, token = owner.token).body()
                    val requests = count(db, "request_identity")
                    assertEquals(503, request(settings, method, path, input, owner.token).statusCode())
                    ready(settings)
                    assertEquals(before, request(settings, "GET", path, token = owner.token).body())
                    assertEquals(requests, count(db, "request_identity")); assertEquals(requests, count(db, "account_request"))
                    db.connection().use { it.createStatement().execute("DROP TRIGGER fail_deck_outcome ON account_request; DROP FUNCTION fail_deck_outcome()") }
                    val success = request(settings, method, path, input, owner.token)
                    assertEquals(200, success.statusCode(), success.body())
                    assertEquals(success.body(), request(settings, method, path, input, owner.token).body())
                }
                assertEquals(0, count(db, "saved_deck")); assertEquals(1, count(db, "definition"))
                println("PASS real PostgreSQL failure after metadata/delete effects rolls back deck/entries/revision/request/outcome atomically; same original identities recover once")
            }
        }
    }

    // An import into an existing deck replaces its whole list, Maybe included (merge import is gone). A card the deck
    // holds from before the current catalog is kept as stored when the save names its digest.
    @Test fun `contextual imports replace the list preserve immutable content and recover original outcomes`() {
        FreshDatabase().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            val config = settings(db)
            lateinit var owner: SessionCreated
            lateinit var saved: SavedDeck
            lateinit var replace: UpdateDeck
            lateinit var original: String
            val first = TestPrintings.submitted("Storm Spark")
            // A card from before: the same name and printing, other card data. Never rewritten.
            val old = submitted(first.definition.copy(faces = first.definition.faces.map { it.copy(rulesText = "Changed external record") }))
            fun oldEntry(quantity: Int) = CardListEntry(SavedDeckSection.MAINBOARD, old.digest, quantity)
            SpringService(config).use { service ->
                val settings = service.startBound(config); ready(settings)
                owner = account(settings, "owner"); val foreign = account(settings, "foreign")
                saved = json.decodeFromString(post(settings, "/v1/decks", CreateDeck(id(), "Retained name",
                    listOf(entry("Storm Spark", 4), entry("Storm Spark", 3, SavedDeckSection.SIDEBOARD))), owner.token).body())
                val path = "/v1/decks/${saved.deck.id}"
                fun put(input: UpdateDeck, token: String = owner.token, target: String = path) =
                    request(settings, "PUT", target, json.encodeToString(input), token)
                fun read() = json.decodeFromString<SavedDeck>(request(settings, "GET", path, token = owner.token).body())
                val add = DeckContentsImport(listOf(entry("Storm Spark", 2), entry("Storm Spark", 1, SavedDeckSection.MAYBEBOARD)))
                val added = listOf(saved("Storm Spark", 2), saved("Storm Spark", 1, SavedDeckSection.MAYBEBOARD))
                replace = UpdateDeck(id(), contents = add)
                assertEquals(401, request(settings, "PUT", path, json.encodeToString(replace)).statusCode())
                assertEquals(404, put(replace.copy(requestId = id()), foreign.token).statusCode())
                val pool = Executors.newFixedThreadPool(4)
                try {
                    val replies = pool.invokeAll(List(4) { Callable { put(replace) } }).map { it.get(30, TimeUnit.SECONDS) }
                    assertTrue(replies.all { it.statusCode() == 200 }, replies.map { it.body() }.toString())
                    original = replies.first().body(); assertTrue(replies.all { it.body() == original })
                    assertEquals(added, read().entries)
                    read().deck.let { assertEquals(listOf(2L, 0L, 1L), listOf(it.mainboard, it.sideboard, it.maybeboard)) }
                    val independent = pool.invokeAll(List(2) { Callable { put(replace.copy(requestId = id())) } })
                        .map { it.get(30, TimeUnit.SECONDS) }
                    assertTrue(independent.all { it.statusCode() == 200 })
                } finally { pool.shutdownNow(); assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS)) }
                assertEquals(added, read().entries)
                assertEquals(409, put(replace.copy(contents = add.copy(entries = add.entries.take(1)))).statusCode())
                assertEquals(409, put(replace, target = "/v1/decks/${id()}").statusCode())
                assertEquals(409, put(replace, foreign.token).statusCode())
                // A card from before that the deck doesn't hold can't be named
                val notHeld = put(UpdateDeck(id(), contents = DeckContentsImport(listOf(oldEntry(4)))))
                assertEquals(400, notHeld.statusCode()); assertEquals("definitions_unavailable", json.decodeFromString<DeckError>(notHeld.body()).code)
                keep(db, owner.account.accountId, saved.deck.id, 2, old, SavedDeckSection.SIDEBOARD, 1)
                val both = put(UpdateDeck(id(), contents = DeckContentsImport(add.entries + oldEntry(4))))
                assertEquals(200, both.statusCode(), both.body()); assertEquals(3, read().entries.size)
                assertEquals(listOf(2, 1, 4), read().entries.map { it.quantity })
                val cards = request(settings, "POST", "$path/definitions", json.encodeToString(ReadCubeDefinitions(listOf(first.digest, old.digest))), owner.token)
                assertEquals(listOf(first, old), json.decodeFromString<SubmittedDefinitions>(cards.body()).definitions)
                put(UpdateDeck(id(), cover = first.digest))
                val overwrite = put(UpdateDeck(id(), contents = DeckContentsImport(listOf(oldEntry(7)))))
                assertEquals(200, overwrite.statusCode(), overwrite.body())
                val current = read(); assertEquals(7L, current.deck.mainboard); assertEquals(0L, current.deck.sideboard)
                assertEquals(0L, current.deck.maybeboard)
                assertEquals(saved.deck.id, current.deck.id); assertEquals(saved.deck.createdAt, current.deck.createdAt)
                assertEquals("Retained name", current.deck.name); assertEquals(old.digest, current.deck.cover!!.definition)
                assertEquals(original, put(replace).body()); assertEquals(current, read())
                assertEquals(2, count(db, "definition"))
                println("PASS four duplicate replacements apply once, independent replacements serialize, Maybe kept, target/contents/account intent binding, a card from before kept only while held and never rewritten, replacing clears sideboard and retains deck metadata")
            }
            SpringService(config).use { service ->
                val settings = service.startBound(config); ready(settings)
                val path = "/v1/decks/${saved.deck.id}"
                assertEquals(original, request(settings, "PUT", path, json.encodeToString(replace), owner.token).body())
                val current = json.decodeFromString<SavedDeck>(request(settings, "GET", path, token = owner.token).body())
                assertEquals(listOf(7), current.entries.map { it.quantity })
                assertEquals(200, request(settings, "DELETE", path, json.encodeToString(DeleteDeck(id())), owner.token).statusCode())
                assertEquals(original, request(settings, "PUT", path, json.encodeToString(replace), owner.token).body())
                assertEquals(404, request(settings, "PUT", path, json.encodeToString(replace.copy(requestId = id())), owner.token).statusCode())
                assertEquals(0, count(db, "saved_deck")); assertEquals(2, count(db, "definition"))
                println("PASS actual service restart and deleted-target old replacement outcome recovery without reapplication/upsert; immutable snapshots retained")
            }
        }
    }

    @Test fun `invalid overflowing and interrupted contextual imports preserve the entire accepted deck`() {
        FreshDatabase().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            val config = settings(db)
            SpringService(config).use { service ->
                val settings = service.startBound(config); ready(settings)
                val owner = account(settings, "owner")
                val entry = entry("Storm Spark", 4)
                val saved = json.decodeFromString<SavedDeck>(post(settings, "/v1/decks", CreateDeck(id(), "Good", listOf(entry)), owner.token).body())
                val path = "/v1/decks/${saved.deck.id}"
                fun put(input: UpdateDeck) = request(settings, "PUT", path, json.encodeToString(input), owner.token)
                fun current() = request(settings, "GET", path, token = owner.token).body()
                val before = current()
                for (entries in listOf(emptyList(), listOf(entry.copy(quantity = -1)), listOf(entry.copy(printingId = id())),
                    listOf(entry.copy(printingId = "sha256:${"0".repeat(64)}")), listOf(entry.copy(quantity = Int.MAX_VALUE)), listOf(entry, entry.copy(quantity = 997)))) {
                    val bad = UpdateDeck(id(), contents = DeckContentsImport(entries))
                    val result = put(bad); assertEquals(400, result.statusCode()); assertEquals(result.body(), put(bad).body())
                    assertEquals(before, current())
                }
                val input = UpdateDeck(id(), contents = DeckContentsImport(listOf(entry, entry.copy(section = SavedDeckSection.SIDEBOARD))))
                assertEquals(400, request(settings, "PUT", path, json.encodeToString(input).dropLast(10), owner.token).statusCode())
                val count = count(db, "request_identity")
                db.connection().use { it.createStatement().execute("""
                    CREATE FUNCTION fail_import_outcome() RETURNS trigger LANGUAGE plpgsql AS ${'$'}${'$'}
                    BEGIN IF NEW.request_id = '${input.requestId}' THEN RAISE EXCEPTION 'test-owned interruption'; END IF; RETURN NEW; END ${'$'}${'$'};
                    CREATE TRIGGER fail_import_outcome BEFORE INSERT ON account_request FOR EACH ROW EXECUTE FUNCTION fail_import_outcome();
                """.trimIndent()) }
                assertEquals(503, put(input).statusCode()); ready(settings)
                assertEquals(before, current()); assertEquals(count, count(db, "request_identity"))
                db.connection().use { it.createStatement().execute("DROP TRIGGER fail_import_outcome ON account_request; DROP FUNCTION fail_import_outcome()") }
                val accepted = put(input); assertEquals(200, accepted.statusCode(), accepted.body())
                assertEquals(accepted.body(), put(input).body())
                assertEquals(listOf(4, 4), json.decodeFromString<SavedDeck>(current()).entries.map { it.quantity })
                println("PASS invalid/unknown/not-held/negative/overflow/truncated imports preserve whole deck; real SQL failure after replacement rolls back entries/revision/identity/outcome; same identity recovers atomically")
            }
        }
    }

    @Test fun `explicit V4 cover upgrade retains existing named deck entries definitions and original outcomes`() {
        FreshDatabase(migrated = false).start().use { db ->
            Flyway.configure().configuration(FoundationMigrations.flyway(db.settings).configuration).target("4").load().migrate()
            val pendingForwards = FoundationMigrations.flyway(db.settings).info().pending().size
            val account = id(); val deck = id(); val request = id(); val definition = submitted(card())
            db.connection().use { c ->
                c.prepareStatement("INSERT INTO account(id,username,username_key,email_delivery,email_key,password_phc) VALUES (?::uuid,'Retained','retained','retained@example.invalid','retained@example.invalid','phc')").use { it.setString(1, account); it.executeUpdate() }
                c.prepareStatement("INSERT INTO definition VALUES (?,1,?)").use { it.setString(1, definition.digest); it.setString(2, DeckJson.canonical(definition.definition)); it.executeUpdate() }
                c.createStatement().execute("""
                    INSERT INTO account_definition VALUES ('$account','${definition.digest}');
                    INSERT INTO saved_deck(id,account_id,name,revision) VALUES ('$deck','$account','Retained deck',9);
                    INSERT INTO deck_entry VALUES ('$deck','$account',0,'MAINBOARD','${definition.digest}',7);
                    INSERT INTO request_identity VALUES ('$request','$account','createDeck','account','${"0".repeat(64)}');
                    INSERT INTO account_request VALUES ('$request',201,'retained original');
                """.trimIndent())
            }
            assertEquals(pendingForwards, FoundationMigrations.migrate(db.settings)); assertEquals(0, FoundationMigrations.migrate(db.settings))
            db.connection().use { c -> c.createStatement().executeQuery("SELECT name,revision,cover_digest FROM saved_deck").use {
                assertTrue(it.next()); assertEquals("Retained deck", it.getString(1)); assertEquals(9, it.getLong(2)); assertNull(it.getString(3))
            } }
            assertEquals(1, count(db, "deck_entry")); assertEquals(1, count(db, "definition")); assertEquals(1, count(db, "account_request"))
            println("PASS explicit forward V4-to-current migration retains populated accepted deck/entries/definitions/revision/original outcome without destructive reset")
        }
    }

    @Test fun `explicit V3 to current upgrade preserves account data and routes never self migrate`() {
        FreshDatabase(migrated = false).start().use { db ->
            val config = settings(db)
            SpringService(config).use { service ->
                val settings = service.startBound(config)
                assertEquals(503, request(settings, "GET", "/v1/decks", token = "A".repeat(43)).statusCode())
                assertEquals(0, count(db, "pg_tables WHERE schemaname='public'"))
            }
            Flyway.configure().configuration(FoundationMigrations.flyway(db.settings).configuration).target("3").load().migrate()
            val pendingForwards = FoundationMigrations.flyway(db.settings).info().pending().size
            db.connection().use { it.createStatement().execute("""
                INSERT INTO account(id,username,username_key,email_delivery,email_key,password_phc)
                VALUES ('00000000-0000-4000-8000-000000000009','Retained','retained','retained@example.invalid','retained@example.invalid','retained-phc')
            """.trimIndent()) }
            val migration = ProcessBuilder(System.getProperty("asyncttwiz.migrationExecutable"), "migrate")
            val secret = java.nio.file.Files.createTempFile("deck-migration-", ".secret")
            try {
                java.nio.file.Files.writeString(secret, db.settings.password())
                val inherited = migration.environment().filterKeys { it in setOf("PATH", "JAVA_HOME", "JAVA_TOOL_OPTIONS", "HOME") }
                migration.environment().clear(); migration.environment().putAll(inherited)
                migration.environment().putAll(mapOf("DB_URL" to db.settings.url(), "DB_USER" to db.settings.user(), "DB_PASSWORD_FILE" to secret.toString()))
                migration.redirectErrorStream(true)
                val process = migration.start()
                val output = process.inputStream.bufferedReader().readText()
                assertTrue(process.waitFor(45, TimeUnit.SECONDS)); assertEquals(0, process.exitValue(), output)
                assertTrue(output.lineSequence().any { it == "Migration successful; applied=$pendingForwards" }); println(output.trim())
                assertEquals(1, count(db, "account")); assertEquals(0, count(db, "saved_deck"))
                assertEquals(0, FoundationMigrations.migrate(db.settings))
                db.connection().use { connection -> connection.createStatement().executeQuery("SELECT password_phc FROM account").use {
                    assertTrue(it.next()); assertEquals("retained-phc", it.getString(1))
                } }
                println("PASS installed explicit V3-to-current migration preserves populated V3 account; clean unsupported routes never migrate; real generated deck bindings exercised by HTTP tests")
            } finally { java.nio.file.Files.deleteIfExists(secret) }
        }
    }
}
