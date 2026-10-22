package org.asyncttwiz.server.decks

import java.net.InetAddress
import java.net.ServerSocket
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
import org.asyncttwiz.server.database.FoundationMigrations
import org.asyncttwiz.server.database.OwnedPostgres
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** Actual normal public handlers, writer fence, generated jOOQ and Testcontainers PostgreSQL17. */
class DeckHttpTest {
    private val json = DeckJson.format
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()
    private fun id() = UUID.randomUUID().toString()
    private fun port() = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1")).use { it.localPort }
    private fun settings(db: OwnedPostgres) = ServiceSettings(db.settings, URI("https://synthetic.invalid"),
        InetAddress.getByName("127.0.0.1"), port(), InetAddress.getByName("127.0.0.1"), port())
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
    private fun count(db: OwnedPostgres, table: String): Int = db.connection().use { connection ->
        connection.createStatement().executeQuery("SELECT count(*) FROM $table").use { it.next(); it.getInt(1) }
    }

    @Test fun `HTTP complete definitions exact deck concurrent original outcome restart and private readback`() {
        OwnedPostgres().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            val settings = settings(db)
            val first = submitted(card())
            val changed = submitted(card().copy(faces = card().faces.map { it.copy(corner = "3/4") }))
            val frontId = id(); val backId = id()
            val multi = submitted(CardDefinition("Front // Back", listOf(
                DefinitionFace(frontId, "Front", listOf(backId), corner = "2/3"),
                DefinitionFace(backId, "Back", listOf(frontId), corner = ""))))
            val definitions = SubmitDefinitions(id(), listOf(first, changed, multi))
            val entries = listOf(SavedDeckEntry(SavedDeckSection.MAINBOARD, first.digest, 12),
                SavedDeckEntry(SavedDeckSection.MAINBOARD, multi.digest, 2),
                SavedDeckEntry(SavedDeckSection.SIDEBOARD, changed.digest, 3))
            val create = CreateDeck(id(), "  Exact é 😀 name  ", entries)
            lateinit var owner: SessionCreated
            lateinit var foreign: SessionCreated
            lateinit var saved: SavedDeck
            lateinit var original: String
            SpringService(settings).use { service ->
                service.start(); ready(settings)
                owner = account(settings, "owner"); foreign = account(settings, "foreign")
                val accepted = post(settings, "/v1/definitions", definitions, owner.token)
                assertEquals(200, accepted.statusCode(), accepted.body())
                assertEquals(definitions.definitions.map { it.digest }.sorted(), json.decodeFromString<DefinitionsAccepted>(accepted.body()).digests)
                assertEquals(accepted.body(), post(settings, "/v1/definitions", definitions.copy(definitions = definitions.definitions.reversed()), owner.token).body())
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
                assertEquals("1", saved.deck.revision)
                assertEquals(1, count(db, "saved_deck")); assertEquals(3, count(db, "definition"))
                assertEquals(3, count(db, "deck_entry")); assertEquals(2, count(db, "account_request"))
                assertEquals(409, post(settings, "/v1/decks", create.copy(name = "changed"), owner.token).statusCode())
                val foreignId = post(settings, "/v1/decks", create, foreign.token)
                assertEquals(409, foreignId.statusCode()); assertFalse(foreignId.body().contains(saved.deck.id))
                assertEquals(409, post(settings, "/v1/decks", create.copy(requestId = definitions.requestId), owner.token).statusCode())
                val unavailable = post(settings, "/v1/decks", create.copy(requestId = id()), foreign.token)
                assertEquals(400, unavailable.statusCode()); assertEquals("definitions_unavailable", json.decodeFromString<DeckError>(unavailable.body()).code)
                for (path in listOf("/v1/decks/${saved.deck.id}", "/v1/decks/${saved.deck.id}/definitions/${first.digest}")) {
                    assertEquals(401, request(settings, "GET", path).statusCode())
                    val denied = request(settings, "GET", path, token = foreign.token)
                    val missing = request(settings, "GET", path.replace(saved.deck.id, id()), token = foreign.token)
                    assertEquals(404, denied.statusCode()); assertEquals(missing.body(), denied.body())
                    assertFalse(denied.body().contains(first.definition.name))
                }
                assertEquals(emptyList<DeckSummary>(), json.decodeFromString<DeckList>(request(settings, "GET", "/v1/decks", token = foreign.token).body()).decks)
                val missing = request(settings, "GET", "/v1/decks/${saved.deck.id}/definitions/sha256:${"0".repeat(64)}", token = owner.token)
                assertEquals(404, missing.statusCode())
            }
            // New normal service instance, retained actual PostgreSQL. No public catalog/art service exists.
            SpringService(settings).use { service ->
                service.start(); ready(settings)
                assertEquals(original, post(settings, "/v1/decks", create, owner.token).body()) // lost-response retry
                val list = json.decodeFromString<DeckList>(request(settings, "GET", "/v1/decks", token = owner.token).body())
                assertEquals(listOf(saved.deck), list.decks); assertNull(list.next)
                assertEquals(saved, json.decodeFromString<SavedDeck>(request(settings, "GET", "/v1/decks/${saved.deck.id}", token = owner.token).body()))
                for (definition in listOf(first, changed, multi)) {
                    val response = request(settings, "GET", "/v1/decks/${saved.deck.id}/definitions/${definition.digest}", token = owner.token)
                    assertEquals(200, response.statusCode()); assertEquals(definition, json.decodeFromString<SubmittedDefinition>(response.body()))
                }
                // Content deduplicates, access never deduplicates across accounts without submission.
                assertEquals(200, post(settings, "/v1/definitions", definitions.copy(requestId = id()), foreign.token).statusCode())
                assertEquals(3, count(db, "definition")); assertEquals(6, count(db, "account_definition"))
                assertEquals(204, request(settings, "DELETE", "/session", token = owner.token).statusCode())
                assertEquals(401, post(settings, "/v1/decks", create, owner.token).statusCode())
                assertEquals(1, count(db, "saved_deck"))
                println("PASS authenticated complete arbitrary and multiface snapshots, exact main/side/name, six concurrent/lost-response original outcomes, identity/account/kind conflicts, private reads/attachments, no provider/art dependency, actual service restart and revoked-token refusal")
            }
        }
    }

    @Test fun `whole set validation strict HTTP and interrupted body retain no partial definitions or deck`() {
        OwnedPostgres().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            val settings = settings(db)
            SpringService(settings).use { service ->
                service.start(); ready(settings)
                val owner = account(settings, "owner")
                val good = submitted(card())
                val badFace = card().copy(faces = card().faces.map { it.copy(otherFaceIds = listOf(id())) })
                val invalid = listOf(good.copy(digest = "sha256:${"0".repeat(64)}"), submitted(badFace),
                    submitted(card().copy(schemaVersion = 2)),
                    submitted(card().copy(faces = card().faces.map { it.copy(rulesText = "x".repeat(65537)) })))
                for (bad in invalid) {
                    assertEquals(400, post(settings, "/v1/definitions", SubmitDefinitions(id(), listOf(good, bad)), owner.token).statusCode())
                    assertEquals(0, count(db, "definition")); assertEquals(0, count(db, "account_definition"))
                }
                val valid = json.encodeToString(SubmitDefinitions(id(), listOf(good)))
                // Escape the invalid scalar on the wire: Java BodyPublishers otherwise replaces it
                // with a valid '?' during UTF-8 encoding, which is not malformed server input.
                for (body in listOf(valid.dropLast(10), valid.dropLast(1) + ",\"extra\":true}", "{}",
                    valid.replace("Arbitrary unknown card", "\\ud800")))
                    assertEquals(400, request(settings, "POST", "/v1/definitions", body, owner.token).statusCode())
                assertEquals(415, request(settings, "POST", "/v1/definitions", valid, owner.token, "text/plain").statusCode())
                assertEquals(413, request(settings, "POST", "/v1/definitions", " ".repeat(16 * 1024 * 1024 + 1), owner.token).statusCode())
                assertEquals(401, request(settings, "POST", "/v1/definitions", valid).statusCode())
                assertEquals(405, request(settings, "GET", "/v1/definitions", token = owner.token).statusCode())
                Socket("127.0.0.1", settings.publicPort()).use { socket ->
                    socket.getOutputStream().write(("POST /v1/definitions HTTP/1.1\r\nHost: localhost\r\nAuthorization: Bearer ${owner.token}\r\n" +
                        "Content-Type: application/json\r\nContent-Length: ${valid.length + 100}\r\n\r\n" + valid.take(30)).toByteArray())
                    socket.getOutputStream().flush(); socket.shutdownOutput()
                    socket.soTimeout = 10000; socket.getInputStream().readBytes()
                }
                assertEquals(0, count(db, "definition")); assertEquals(0, count(db, "saved_deck"))
                assertEquals(200, post(settings, "/v1/definitions", SubmitDefinitions(id(), listOf(good)), owner.token).statusCode())
                val create = CreateDeck(id(), "Good", listOf(SavedDeckEntry(SavedDeckSection.MAINBOARD, good.digest, 7)))
                assertEquals(201, post(settings, "/v1/decks", create, owner.token).statusCode())
                for (bad in listOf(create.copy(requestId = id(), name = " "), create.copy(requestId = id(), entries = emptyList()),
                    create.copy(requestId = id(), entries = create.entries.map { it.copy(quantity = -1) }),
                    create.copy(requestId = id(), entries = create.entries + create.entries),
                    create.copy(requestId = id(), entries = create.entries.map { it.copy(definition = "sha256:${"0".repeat(64)}") }))) {
                    val rejected = post(settings, "/v1/decks", bad, owner.token)
                    assertEquals(400, rejected.statusCode()); assertEquals(rejected.body(), post(settings, "/v1/decks", bad, owner.token).body())
                    assertEquals(409, post(settings, "/v1/decks", create.copy(requestId = bad.requestId), owner.token).statusCode())
                }
                assertEquals(1, count(db, "saved_deck")); assertEquals(1, count(db, "deck_entry"))
                assertEquals(201, post(settings, "/v1/decks", create, owner.token).statusCode())
                println("PASS invalid digest/schema/reference/Unicode/field limits and malformed/unknown/oversize/media/interrupted HTTP install no partial set; bad imports preserve good saved deck and retained rejection outcomes")
            }
        }
    }

    @Test fun `database failure rolls back effects identity and outcome and same request can recover`() {
        OwnedPostgres().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            val settings = settings(db)
            SpringService(settings).use { service ->
                service.start(); ready(settings)
                val owner = account(settings, "owner")
                val good = submitted(card())
                val alternate = submitted(card("Different content"))
                val set = SubmitDefinitions(id(), listOf(good, alternate))
                val failDigest = set.definitions.maxOf { it.digest }
                db.connection().use { it.createStatement().execute("""
                    CREATE FUNCTION fail_definition() RETURNS trigger LANGUAGE plpgsql AS ${'$'}${'$'}
                    BEGIN IF NEW.digest = '$failDigest' THEN RAISE EXCEPTION 'test-owned interruption'; END IF; RETURN NEW; END ${'$'}${'$'};
                    CREATE TRIGGER fail_definition BEFORE INSERT ON definition FOR EACH ROW EXECUTE FUNCTION fail_definition();
                """.trimIndent()) }
                assertEquals(503, post(settings, "/v1/definitions", set, owner.token).statusCode())
                assertEquals(0, count(db, "definition")); assertEquals(0, count(db, "account_definition"))
                assertEquals(0, count(db, "request_identity")); assertEquals(0, count(db, "account_request"))
                db.connection().use { it.createStatement().execute("DROP TRIGGER fail_definition ON definition; DROP FUNCTION fail_definition()") }
                ready(settings)
                assertEquals(200, post(settings, "/v1/definitions", set, owner.token).statusCode())
                assertEquals(2, count(db, "definition")); assertEquals(2, count(db, "account_definition"))
                val create = CreateDeck(id(), "Rollback", listOf(SavedDeckEntry(SavedDeckSection.MAINBOARD, good.digest, 7),
                    SavedDeckEntry(SavedDeckSection.SIDEBOARD, good.digest, 3)))
                db.connection().use { it.createStatement().execute("""
                    CREATE FUNCTION fail_second_entry() RETURNS trigger LANGUAGE plpgsql AS ${'$'}${'$'}
                    BEGIN IF NEW.position = 1 THEN RAISE EXCEPTION 'test-owned interruption'; END IF; RETURN NEW; END ${'$'}${'$'};
                    CREATE TRIGGER fail_second BEFORE INSERT ON deck_entry FOR EACH ROW EXECUTE FUNCTION fail_second_entry();
                """.trimIndent()) }
                assertEquals(503, post(settings, "/v1/decks", create, owner.token).statusCode())
                assertEquals(0, count(db, "saved_deck")); assertEquals(0, count(db, "deck_entry"))
                assertEquals(1, count(db, "request_identity")); assertEquals(1, count(db, "account_request"))
                db.connection().use { it.createStatement().execute("DROP TRIGGER fail_second ON deck_entry; DROP FUNCTION fail_second_entry()") }
                ready(settings)
                assertEquals(201, post(settings, "/v1/decks", create, owner.token).statusCode())
                assertEquals(1, count(db, "saved_deck")); assertEquals(2, count(db, "deck_entry"))
                println("PASS real SQL failure after first definition/grant and after deck/first entry rolls back all effects/request/outcome; writer recovery and unchanged identity retries retain/create exactly once")
            }
        }
    }

    @Test fun `owned list keyset pages preserve equal names and maximum quantity without deck legality rules`() {
        OwnedPostgres().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            val settings = settings(db)
            SpringService(settings).use { service ->
                service.start(); ready(settings)
                val owner = account(settings, "owner")
                val good = submitted(card())
                assertEquals(200, post(settings, "/v1/definitions", SubmitDefinitions(id(), listOf(good)), owner.token).statusCode())
                val created = (1..101).map {
                    val response = post(settings, "/v1/decks", CreateDeck(id(), "Same name", listOf(
                        SavedDeckEntry(SavedDeckSection.SIDEBOARD, good.digest, Int.MAX_VALUE))), owner.token)
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
                println("PASS 101 ordinary HTTP creates with equal names and sideboard-only Int.MAX_VALUE quantities, complete nonoverlapping 100/1 keyset pages, invalid cursor refusal; no invented deck count/legality cap")
            }
        }
    }

    @Test fun `owned metadata deletion serialization privacy and original retries never upsert or rewrite definitions`() {
        OwnedPostgres().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            val settings = settings(db)
            SpringService(settings).use { service ->
                service.start(); ready(settings)
                val owner = account(settings, "owner"); val foreign = account(settings, "foreign")
                val first = submitted(card()); val second = submitted(card("Sideboard cover")); val outside = submitted(card("Not in deck"))
                assertEquals(200, post(settings, "/v1/definitions", SubmitDefinitions(id(), listOf(first, second, outside)), owner.token).statusCode())
                val entries = listOf(SavedDeckEntry(SavedDeckSection.MAINBOARD, first.digest, 12),
                    SavedDeckEntry(SavedDeckSection.SIDEBOARD, second.digest, 3))
                val saved = json.decodeFromString<SavedDeck>(post(settings, "/v1/decks", CreateDeck(id(), "Original", entries), owner.token).body())
                val path = "/v1/decks/${saved.deck.id}"
                fun update(value: UpdateDeck, token: String = owner.token, target: String = path) =
                    request(settings, "PUT", target, json.encodeToString(value), token)
                fun read() = json.decodeFromString<SavedDeck>(request(settings, "GET", path, token = owner.token).body())
                assertEquals(12L, saved.deck.mainboard); assertEquals(3L, saved.deck.sideboard)
                assertEquals(first.digest, saved.deck.cover!!.definition)
                val rename = UpdateDeck(id(), name = " Exact renamed é 🧙 ")
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
                assertEquals(3, count(db, "definition")); assertEquals(2, count(db, "deck_entry"))
                val deletion = DeleteDeck(id())
                assertEquals(404, request(settings, "DELETE", path, json.encodeToString(DeleteDeck(id())), foreign.token).statusCode())
                val deleted = request(settings, "DELETE", path, json.encodeToString(deletion), owner.token)
                assertEquals(200, deleted.statusCode()); assertEquals(DeckDeleted(saved.deck.id), json.decodeFromString<DeckDeleted>(deleted.body()))
                assertEquals(deleted.body(), request(settings, "DELETE", path, json.encodeToString(deletion), owner.token).body())
                assertEquals(404, update(UpdateDeck(id(), name = "Do not recreate")).statusCode())
                assertEquals(404, request(settings, "GET", path, token = owner.token).statusCode())
                assertEquals(404, request(settings, "GET", "$path/definitions/${first.digest}", token = owner.token).statusCode())
                assertTrue(json.decodeFromString<DeckList>(request(settings, "GET", "/v1/decks", token = owner.token).body()).decks.isEmpty())
                assertEquals(0, count(db, "saved_deck")); assertEquals(0, count(db, "deck_entry"))
                assertEquals(3, count(db, "definition")); assertEquals(3, count(db, "account_definition"))
                assertEquals(original.body(), update(rename).body()); assertEquals(0, count(db, "saved_deck"))
                println("PASS actual HTTP owned rename/sideboard cover/counts, four duplicate updates/revisions, unchanged immutable entries, account/target/intent binding, private refusal, exact old outcomes after deletion and no upsert/cascade to definitions")
            }
        }
    }

    @Test fun `metadata and deletion effects roll back with outcome failure and retain exact retries`() {
        OwnedPostgres().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            val settings = settings(db)
            SpringService(settings).use { service ->
                service.start(); ready(settings)
                val owner = account(settings, "owner"); val definition = submitted(card())
                post(settings, "/v1/definitions", SubmitDefinitions(id(), listOf(definition)), owner.token)
                val saved = json.decodeFromString<SavedDeck>(post(settings, "/v1/decks", CreateDeck(id(), "Retained", listOf(
                    SavedDeckEntry(SavedDeckSection.MAINBOARD, definition.digest, 2))), owner.token).body())
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

    @Test fun `contextual imports serialize additive sections preserve immutable content and recover original outcomes`() {
        OwnedPostgres().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            val settings = settings(db)
            lateinit var owner: SessionCreated
            lateinit var saved: SavedDeck
            lateinit var merge: UpdateDeck
            lateinit var original: String
            val first = submitted(card())
            // Same claimed name, printing and face IDs, distinct actual content. Never rewrite the old snapshot.
            val changed = submitted(card().copy(faces = card().faces.map { it.copy(rulesText = "Changed external record") }))
            SpringService(settings).use { service ->
                service.start(); ready(settings)
                owner = account(settings, "owner"); val foreign = account(settings, "foreign")
                assertEquals(200, post(settings, "/v1/definitions", SubmitDefinitions(id(), listOf(first)), owner.token).statusCode())
                val initial = listOf(SavedDeckEntry(SavedDeckSection.MAINBOARD, first.digest, 4),
                    SavedDeckEntry(SavedDeckSection.SIDEBOARD, first.digest, 3))
                saved = json.decodeFromString(post(settings, "/v1/decks", CreateDeck(id(), "Retained name", initial), owner.token).body())
                val path = "/v1/decks/${saved.deck.id}"
                fun put(input: UpdateDeck, token: String = owner.token, target: String = path) =
                    request(settings, "PUT", target, json.encodeToString(input), token)
                fun read() = json.decodeFromString<SavedDeck>(request(settings, "GET", path, token = owner.token).body())
                val add = DeckContentsImport(listOf(initial.first().copy(quantity = 2)), DeckImportMode.MERGE)
                merge = UpdateDeck(id(), contents = add)
                assertEquals(401, request(settings, "PUT", path, json.encodeToString(merge)).statusCode())
                assertEquals(404, put(merge.copy(requestId = id()), foreign.token).statusCode())
                val pool = Executors.newFixedThreadPool(4)
                try {
                    val replies = pool.invokeAll(List(4) { Callable { put(merge) } }).map { it.get(30, TimeUnit.SECONDS) }
                    assertTrue(replies.all { it.statusCode() == 200 }, replies.map { it.body() }.toString())
                    original = replies.first().body(); assertTrue(replies.all { it.body() == original })
                    assertEquals(listOf(6, 3), read().entries.map { it.quantity })
                    val independent = pool.invokeAll(List(2) { Callable { put(merge.copy(requestId = id())) } })
                        .map { it.get(30, TimeUnit.SECONDS) }
                    assertTrue(independent.all { it.statusCode() == 200 })
                } finally { pool.shutdownNow(); assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS)) }
                assertEquals(listOf(10, 3), read().entries.map { it.quantity })
                assertEquals(409, put(merge.copy(contents = add.copy(mode = DeckImportMode.OVERWRITE))).statusCode())
                assertEquals(409, put(merge, target = "/v1/decks/${id()}").statusCode())
                assertEquals(409, put(merge, foreign.token).statusCode())
                assertEquals(400, put(UpdateDeck(id(), contents = DeckContentsImport(listOf(initial.first().copy(definition = changed.digest))))).statusCode())
                assertEquals(200, post(settings, "/v1/definitions", SubmitDefinitions(id(), listOf(changed)), foreign.token).statusCode())
                assertEquals(400, put(UpdateDeck(id(), contents = DeckContentsImport(listOf(initial.first().copy(definition = changed.digest))))).statusCode())
                assertEquals(200, post(settings, "/v1/definitions", SubmitDefinitions(id(), listOf(changed)), owner.token).statusCode())
                val both = put(UpdateDeck(id(), contents = DeckContentsImport(listOf(initial.first().copy(definition = changed.digest)), DeckImportMode.MERGE)))
                assertEquals(200, both.statusCode()); assertEquals(3, read().entries.size)
                assertEquals(listOf(10, 3, 4), read().entries.map { it.quantity })
                val firstRead = request(settings, "GET", "$path/definitions/${first.digest}", token = owner.token)
                assertEquals(first, json.decodeFromString<SubmittedDefinition>(firstRead.body()))
                assertEquals(changed, json.decodeFromString<SubmittedDefinition>(request(settings, "GET", "$path/definitions/${changed.digest}", token = owner.token).body()))
                put(UpdateDeck(id(), cover = first.digest))
                val overwrite = put(UpdateDeck(id(), contents = DeckContentsImport(listOf(initial.first().copy(definition = changed.digest, quantity = 7)))))
                assertEquals(200, overwrite.statusCode(), overwrite.body())
                val current = read(); assertEquals(7L, current.deck.mainboard); assertEquals(0L, current.deck.sideboard)
                assertEquals(saved.deck.id, current.deck.id); assertEquals(saved.deck.createdAt, current.deck.createdAt)
                assertEquals("Retained name", current.deck.name); assertEquals(changed.digest, current.deck.cover!!.definition)
                assertEquals(original, put(merge).body()); assertEquals(current, read())
                assertEquals(2, count(db, "definition"))
                // Pre-I08 metadata canonical identity is unchanged by the new optional contents field.
                val renameId = id()
                val oldCanonical = "{\"deckId\":\"${saved.deck.id}\",\"input\":{\"cover\":null,\"name\":\"Old metadata\",\"requestId\":\"$renameId\"}}"
                db.connection().use { c -> c.createStatement().execute("""
                    INSERT INTO request_identity VALUES ('$renameId','${owner.account.accountId}','updateDeck','account','${DeckJson.intent("updateDeck", oldCanonical)}');
                    INSERT INTO account_request VALUES ('$renameId',200,'original pre-I08 outcome');
                """.trimIndent()) }
                assertEquals("original pre-I08 outcome", put(UpdateDeck(renameId, name = "Old metadata")).body())
                assertEquals(current, read())
                println("PASS four duplicate Merges add once, concurrent independent Merges serialize, section/untouched retention, target/mode/account intent binding, private attachment, conflicting external names/IDs retain both snapshots, default Overwrite clears sideboard and retains deck metadata, legacy retry identity")
            }
            SpringService(settings).use { service ->
                service.start(); ready(settings)
                val path = "/v1/decks/${saved.deck.id}"
                assertEquals(original, request(settings, "PUT", path, json.encodeToString(merge), owner.token).body())
                val current = json.decodeFromString<SavedDeck>(request(settings, "GET", path, token = owner.token).body())
                assertEquals(listOf(7), current.entries.map { it.quantity })
                assertEquals(200, request(settings, "DELETE", path, json.encodeToString(DeleteDeck(id())), owner.token).statusCode())
                assertEquals(original, request(settings, "PUT", path, json.encodeToString(merge), owner.token).body())
                assertEquals(404, request(settings, "PUT", path, json.encodeToString(merge.copy(requestId = id())), owner.token).statusCode())
                assertEquals(0, count(db, "saved_deck")); assertEquals(2, count(db, "definition"))
                println("PASS actual service restart and deleted-target old Merge outcome recovery without reapplication/upsert; immutable snapshots retained")
            }
        }
    }

    @Test fun `invalid overflowing and interrupted contextual imports preserve the entire accepted deck`() {
        OwnedPostgres().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            val settings = settings(db)
            SpringService(settings).use { service ->
                service.start(); ready(settings)
                val owner = account(settings, "owner"); val definition = submitted(card())
                post(settings, "/v1/definitions", SubmitDefinitions(id(), listOf(definition)), owner.token)
                val entry = SavedDeckEntry(SavedDeckSection.MAINBOARD, definition.digest, 4)
                val saved = json.decodeFromString<SavedDeck>(post(settings, "/v1/decks", CreateDeck(id(), "Good", listOf(entry)), owner.token).body())
                val path = "/v1/decks/${saved.deck.id}"
                fun put(input: UpdateDeck) = request(settings, "PUT", path, json.encodeToString(input), owner.token)
                fun current() = request(settings, "GET", path, token = owner.token).body()
                val before = current()
                for (entries in listOf(emptyList(), listOf(entry, entry), listOf(entry.copy(quantity = -1)),
                    listOf(entry.copy(definition = "sha256:${"0".repeat(64)}")), listOf(entry.copy(quantity = Int.MAX_VALUE)))) {
                    val bad = UpdateDeck(id(), contents = DeckContentsImport(entries, DeckImportMode.MERGE))
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
                println("PASS invalid/unavailable/duplicate/negative/overflow/truncated imports preserve whole deck; real SQL failure after replacement rolls back entries/revision/identity/outcome; same identity recovers atomically")
            }
        }
    }

    @Test fun `explicit V4 cover upgrade retains existing named deck entries definitions and original outcomes`() {
        OwnedPostgres().start().use { db ->
            Flyway.configure().configuration(FoundationMigrations.flyway(db.settings).configuration).target("4").load().migrate()
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
            assertEquals(1, FoundationMigrations.migrate(db.settings)); assertEquals(0, FoundationMigrations.migrate(db.settings))
            db.connection().use { c -> c.createStatement().executeQuery("SELECT name,revision,cover_digest FROM saved_deck").use {
                assertTrue(it.next()); assertEquals("Retained deck", it.getString(1)); assertEquals(9, it.getLong(2)); assertNull(it.getString(3))
            } }
            assertEquals(1, count(db, "deck_entry")); assertEquals(1, count(db, "definition")); assertEquals(1, count(db, "account_request"))
            println("PASS explicit forward V4-to-V5 migration retains populated accepted deck/entries/definitions/revision/original outcome without destructive reset")
        }
    }

    @Test fun `explicit V3 to current upgrade preserves account data and routes never self migrate`() {
        OwnedPostgres().start().use { db ->
            val settings = settings(db)
            SpringService(settings).use { service ->
                service.start()
                assertEquals(503, request(settings, "GET", "/v1/decks", token = "A".repeat(43)).statusCode())
                assertEquals(0, count(db, "pg_tables WHERE schemaname='public'"))
            }
            Flyway.configure().configuration(FoundationMigrations.flyway(db.settings).configuration).target("3").load().migrate()
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
                assertTrue(output.contains("applied=2")); println(output.trim())
                assertEquals(1, count(db, "account")); assertEquals(0, count(db, "saved_deck"))
                assertEquals(0, FoundationMigrations.migrate(db.settings))
                db.connection().use { connection -> connection.createStatement().executeQuery("SELECT password_phc FROM account").use {
                    assertTrue(it.next()); assertEquals("retained-phc", it.getString(1))
                } }
                println("PASS installed explicit V4 migration preserves populated V3 account; clean unsupported routes never migrate; real generated deck bindings exercised by HTTP tests")
            } finally { java.nio.file.Files.deleteIfExists(secret) }
        }
    }
}
