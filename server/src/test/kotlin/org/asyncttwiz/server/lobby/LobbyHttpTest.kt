package org.asyncttwiz.server.lobby

import java.net.InetAddress
import java.net.ServerSocket
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
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

class LobbyHttpTest {
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
        }
    }
    private inline fun <reified T> post(settings: ServiceSettings, path: String, input: T, token: String) =
        request(settings, "POST", path, json.encodeToString(input), token)
    private fun account(settings: ServiceSettings, name: String, email: String): SessionCreated {
        assertEquals(201, request(settings, "POST", "/accounts", json.encodeToString(RegisterAccount(name, email, "password😀"))).statusCode())
        val response = request(settings, "POST", "/sessions", json.encodeToString(CreateSession(email, "password😀")))
        assertEquals(201, response.statusCode()); return json.decodeFromString(response.body())
    }
    private fun count(db: OwnedPostgres, table: String) = db.connection().use { c -> c.createStatement().executeQuery("SELECT count(*) FROM $table").use { it.next(); it.getInt(1) } }

    @Test fun `username search unilateral synchronized contacts atomic duplicate creation privacy and restart`() {
        OwnedPostgres().start().use { db ->
            FoundationMigrations.migrate(db.settings); val settings = settings(db)
            lateinit var owner: SessionCreated; lateinit var recipient: SessionCreated; lateinit var stranger: SessionCreated
            lateinit var create: CreateInvitation; lateinit var game: GameSummary; lateinit var original: String
            SpringService(settings).use { service ->
                service.start(); ready(settings)
                owner = account(settings, "Inviter", "private-owner@example.invalid")
                recipient = account(settings, "Straße Élan", "private-recipient@example.invalid")
                stranger = account(settings, "Stranger", "private-stranger@example.invalid")
                fun search(query: String) = request(settings, "GET", "/v1/users?username=" + URLEncoder.encode(query, Charsets.UTF_8), token = owner.token)
                val found = search(" STRASSE É ")
                assertEquals(200, found.statusCode()); assertEquals(listOf(recipient.account), json.decodeFromString<UserSearch>(found.body()).users)
                assertFalse(found.body().contains("email")); assertFalse(found.body().contains("private-"))
                assertTrue(json.decodeFromString<UserSearch>(search("Inviter").body()).users.isEmpty())
                assertTrue(json.decodeFromString<UserSearch>(search("private-recipient@example.invalid").body()).users.isEmpty())
                assertTrue(json.decodeFromString<UserSearch>(search("%").body()).users.isEmpty())
                assertEquals(400, search(" ").statusCode()); assertEquals(400, search("x".repeat(65)).statusCode())
                val add = AddContact(id(), recipient.account.accountId)
                val added = post(settings, "/v1/contacts", add, owner.token)
                assertEquals(200, added.statusCode()); assertEquals(added.body(), post(settings, "/v1/contacts", add, owner.token).body())
                assertEquals(200, post(settings, "/v1/contacts", add.copy(requestId = id()), owner.token).statusCode())
                assertEquals(1, count(db, "contact"))
                assertTrue(json.decodeFromString<ContactList>(request(settings, "GET", "/v1/contacts", token = recipient.token).body()).contacts.isEmpty())
                val second = json.decodeFromString<SessionCreated>(request(settings, "POST", "/sessions",
                    json.encodeToString(CreateSession("private-owner@example.invalid", "password😀"))).body())
                assertEquals(listOf(recipient.account), json.decodeFromString<ContactList>(request(settings, "GET", "/v1/contacts", token = second.token).body()).contacts)
                create = CreateInvitation(id(), recipient.account.accountId, " Same name é 🧙 ")
                val pool = Executors.newFixedThreadPool(6)
                try {
                    val replies = pool.invokeAll(List(6) { Callable { post(settings, "/v1/invitations", create, owner.token) } }).map { it.get(30, TimeUnit.SECONDS) }
                    assertTrue(replies.all { it.statusCode() == 201 }, replies.map { it.body() }.toString())
                    original = replies.first().body(); assertTrue(replies.all { it.body() == original }); game = json.decodeFromString(original)
                } finally { pool.shutdownNow(); assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS)) }
                assertEquals(create.gameName, game.gameName); assertEquals(recipient.account, game.recipient)
                assertEquals(InvitationState.PENDING, game.invitationState); assertEquals(GameLifecycle.LOBBY, game.lifecycle)
                assertTrue(Instant.parse(game.createdAt).isBefore(Instant.now().plusSeconds(1)))
                assertEquals(1, count(db, "game")); assertEquals(1, count(db, "invitation"))
                assertEquals(original, request(settings, "GET", "/v1/games/${game.id}/head", token = second.token).body())
                assertEquals(listOf(game), json.decodeFromString<GameList>(request(settings, "GET", "/v1/games", token = second.token).body()).games)
                assertEquals(listOf(game), json.decodeFromString<GameList>(request(settings, "GET", "/v1/invitations", token = recipient.token).body()).games)
                assertTrue(json.decodeFromString<GameList>(request(settings, "GET", "/v1/games", token = recipient.token).body()).games.isEmpty())
                for (who in listOf(recipient, stranger)) assertEquals(404, request(settings, "GET", "/v1/games/${game.id}/head", token = who.token).statusCode())
                assertTrue(json.decodeFromString<GameList>(request(settings, "GET", "/v1/invitations", token = stranger.token).body()).games.isEmpty())
                for (changed in listOf(create.copy(gameName = "Different"), create.copy(recipient = stranger.account.accountId), create.copy(requestId = add.requestId)))
                    assertEquals(409, post(settings, "/v1/invitations", changed, owner.token).statusCode())
                val foreign = post(settings, "/v1/invitations", create, stranger.token)
                assertEquals(409, foreign.statusCode()); assertFalse(foreign.body().contains(game.id))
                val equalName = json.decodeFromString<GameSummary>(post(settings, "/v1/invitations", create.copy(requestId = id()), owner.token).body())
                assertNotEquals(game.id, equalName.id); assertNotEquals(game.invitationId, equalName.invitationId)
                assertNotEquals(game.preparationRound, equalName.preparationRound); assertEquals(game.gameName, equalName.gameName)
                assertEquals(405, post(settings, "/v1/games/${game.id}/head", create, owner.token).statusCode())
            }
            SpringService(settings).use { service ->
                service.start(); ready(settings)
                assertEquals(original, post(settings, "/v1/invitations", create, owner.token).body())
                assertEquals(original, request(settings, "GET", "/v1/games/${game.id}/head", token = owner.token).body())
                assertEquals(2, count(db, "game")); assertEquals(2, count(db, "invitation"))
                request(settings, "DELETE", "/session", token = owner.token)
                assertEquals(401, post(settings, "/v1/invitations", create, owner.token).statusCode())
                println("PASS Unicode username-key literal prefix/private projection; unilateral idempotent multi-session contacts; six concurrent and retained restart original creation outcomes; exact identities/name/server date; participant-private lists/head; account/kind/recipient/name conflicts and equal-name distinct games; revoked credential refusal")
            }
        }
    }

    @Test fun `invalid inputs and failed invitation or outcome roll back all creation effects`() {
        OwnedPostgres().start().use { db ->
            FoundationMigrations.migrate(db.settings); val settings = settings(db)
            SpringService(settings).use { service ->
                service.start(); ready(settings)
                val owner = account(settings, "Owner", "owner@example.invalid"); val recipient = account(settings, "Recipient", "recipient@example.invalid")
                val input = CreateInvitation(id(), recipient.account.accountId, "Atomic Spark")
                assertEquals(400, post(settings, "/v1/invitations", input, owner.token).statusCode())
                assertEquals(400, post(settings, "/v1/contacts", AddContact(id(), owner.account.accountId), owner.token).statusCode())
                post(settings, "/v1/contacts", AddContact(id(), recipient.account.accountId), owner.token)
                for (name in listOf(" \n\t", "bad\u0000", "x".repeat(65537))) {
                    val invalid = input.copy(requestId = id(), gameName = name)
                    val reply = post(settings, "/v1/invitations", invalid, owner.token)
                    assertEquals(400, reply.statusCode()); assertEquals(reply.body(), post(settings, "/v1/invitations", invalid, owner.token).body())
                }
                assertEquals(400, request(settings, "POST", "/v1/invitations", json.encodeToString(input).dropLast(5), owner.token).statusCode())
                assertEquals(415, request(settings, "POST", "/v1/invitations", "{}", owner.token, "text/plain").statusCode())
                assertEquals(401, post(settings, "/v1/invitations", input, "A".repeat(43)).statusCode())
                val retry = input.copy(requestId = id())
                for (table in listOf("invitation", "account_request")) {
                    val before = count(db, "request_identity")
                    db.connection().use { it.createStatement().execute("""
                        CREATE FUNCTION fail_creation() RETURNS trigger LANGUAGE plpgsql AS ${'$'}${'$'}
                        BEGIN RAISE EXCEPTION 'test-owned transaction failure'; END ${'$'}${'$'};
                        CREATE TRIGGER fail_creation BEFORE INSERT ON $table FOR EACH ROW EXECUTE FUNCTION fail_creation();
                    """.trimIndent()) }
                    assertEquals(503, post(settings, "/v1/invitations", retry, owner.token).statusCode())
                    assertEquals(0, count(db, "game")); assertEquals(0, count(db, "invitation")); assertEquals(before, count(db, "request_identity"))
                    db.connection().use { it.createStatement().execute("DROP TRIGGER fail_creation ON $table; DROP FUNCTION fail_creation()") }
                    ready(settings)
                }
                assertEquals(201, post(settings, "/v1/invitations", retry, owner.token).statusCode())
                assertEquals(1, count(db, "game")); assertEquals(1, count(db, "invitation"))
                println("PASS contact/name/media/auth/truncated validation, retained rejections and actual SQL failures after game/invitation effects roll back identities/outcomes/all state; no partial creation")
            }
        }
    }

    @Test fun `search is bounded and literal while contact list pages preserve all identities`() {
        OwnedPostgres().start().use { db ->
            FoundationMigrations.migrate(db.settings); val settings = settings(db)
            SpringService(settings).use { service ->
                service.start(); ready(settings)
                val owner = account(settings, "Owner", "owner@example.invalid")
                val ids = (1..101).map { index ->
                    val id = id(); val name = "Bounded${index.toString().padStart(3, '0')}"
                    db.connection().use { it.createStatement().execute("""
                        INSERT INTO account(id,username,username_key,email_delivery,email_key,password_phc)
                        VALUES ('$id','$name','${name.lowercase()}','fixture$index@example.invalid','fixture$index@example.invalid','test-only');
                        INSERT INTO contact VALUES ('${owner.account.accountId}','$id');
                    """.trimIndent()) }; id
                }.toSet()
                val search = request(settings, "GET", "/v1/users?username=BOUNDED", token = owner.token)
                assertEquals(50, json.decodeFromString<UserSearch>(search.body()).users.size)
                assertFalse(search.body().contains("email")); assertFalse(search.body().contains("fixture"))
                val first = json.decodeFromString<ContactList>(request(settings, "GET", "/v1/contacts", token = owner.token).body())
                assertEquals(100, first.contacts.size); assertNotNull(first.next)
                val last = json.decodeFromString<ContactList>(request(settings, "GET", "/v1/contacts?after=${first.next}", token = owner.token).body())
                assertEquals(1, last.contacts.size); assertNull(last.next)
                assertEquals(ids, (first.contacts + last.contacts).map { it.accountId }.toSet())
                assertEquals(400, request(settings, "GET", "/v1/contacts?after=bad", token = owner.token).statusCode())
                println("PASS bounded 50 username-only prefix results, complete 100/1 contact keyset pages and invalid-cursor rejection")
            }
        }
    }

    @Test fun `explicit V5 forward migration retains account deck definition and original outcome`() {
        OwnedPostgres().start().use { db ->
            Flyway.configure().configuration(FoundationMigrations.flyway(db.settings).configuration).target("5").load().migrate()
            val account = id(); val deck = id(); val request = id()
            db.connection().use { it.createStatement().execute("""
                INSERT INTO account(id,username,username_key,email_delivery,email_key,password_phc) VALUES ('$account','Retained','retained','retained@example.invalid','retained@example.invalid','retained-phc');
                INSERT INTO definition VALUES ('sha256:${"0".repeat(64)}',1,'retained-definition');
                INSERT INTO account_definition VALUES ('$account','sha256:${"0".repeat(64)}');
                INSERT INTO saved_deck(id,account_id,name,revision) VALUES ('$deck','$account','Retained deck',7);
                INSERT INTO deck_entry VALUES ('$deck','$account',0,'MAINBOARD','sha256:${"0".repeat(64)}',3);
                INSERT INTO request_identity VALUES ('$request','$account','createDeck','account','${"0".repeat(64)}');
                INSERT INTO account_request VALUES ('$request',201,'retained original');
            """.trimIndent()) }
            assertEquals(1, FoundationMigrations.migrate(db.settings)); assertEquals(0, FoundationMigrations.migrate(db.settings))
            for (table in listOf("account", "saved_deck", "deck_entry", "definition", "account_request")) assertEquals(1, count(db, table))
            for (table in listOf("contact", "game", "invitation")) assertEquals(0, count(db, table))
            db.connection().use { c -> c.createStatement().executeQuery("SELECT password_phc,name,revision,response FROM account,saved_deck,account_request").use {
                assertTrue(it.next()); assertEquals("retained-phc", it.getString(1)); assertEquals("Retained deck", it.getString(2)); assertEquals(7L, it.getLong(3)); assertEquals("retained original", it.getString(4))
            } }
            println("PASS explicit non-destructive V5-to-V6 migration retains accepted account/deck/definition/entry/revision/original outcome")
        }
    }
}
