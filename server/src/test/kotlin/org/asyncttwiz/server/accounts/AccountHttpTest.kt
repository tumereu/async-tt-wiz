package org.asyncttwiz.server.accounts

import java.net.InetAddress
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlinx.serialization.encodeToString
import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.ServiceSettings
import org.asyncttwiz.server.SpringService
import org.asyncttwiz.server.database.FoundationMigrations
import org.asyncttwiz.server.database.OwnedPostgres
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** Actual HTTP through normal Spring/Tomcat/Security handlers, real PostgreSQL and shared JSON. */
class AccountHttpTest {
    @Test fun `profile updates validate names and avatars and project current identities everywhere`() {
        OwnedPostgres().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            val settings = settings(db)
            SpringService(settings).use { service ->
                service.start(); ready(settings)
                fun account(name: String): SessionCreated {
                    assertEquals(201, post(settings, "/accounts", json.encodeToString(RegisterAccount(name, "$name@example.invalid", "password"))).statusCode())
                    return json.decodeFromString(post(settings, "/sessions", json.encodeToString(CreateSession("$name@example.invalid", "password"))).body())
                }
                val alice = account("Alice")
                val bob = account("Bob")
                fun put(path: String, body: String, token: String? = alice.token) = request(settings, "PUT", path, body.toByteArray(), token)
                assertEquals(401, put("/account/username", """{"username":"New"}""", null).statusCode())
                assertEquals(401, put("/account/avatar", "{}", null).statusCode())
                val taken = put("/account/username", json.encodeToString(ChangeUsername("BOB")))
                assertEquals(409, taken.statusCode())
                assertEquals("username_taken", json.decodeFromString<AccountError>(taken.body()).code)
                for (name in listOf(" ", "x".repeat(65), "bad\nname"))
                    assertEquals(400, put("/account/username", json.encodeToString(ChangeUsername(name))).statusCode())
                val changed = put("/account/username", json.encodeToString(ChangeUsername("  Alicia  ")))
                assertEquals(200, changed.statusCode())
                assertEquals("Alicia", json.decodeFromString<AccountIdentity>(changed.body()).username)
                val art = Avatar(java.util.UUID.randomUUID().toString(), 1)
                for (avatar in listOf(art.copy(scryfallId = "bad"), art.copy(face = -1), art.copy(face = 16)))
                    assertEquals(400, put("/account/avatar", json.encodeToString(ChangeAvatar(avatar))).statusCode())
                val set = put("/account/avatar", json.encodeToString(ChangeAvatar(art)))
                assertEquals(200, set.statusCode())
                val identity = json.decodeFromString<AccountIdentity>(set.body())
                assertEquals(art, identity.avatar)
                assertEquals(identity, json.decodeFromString<AccountIdentity>(request(settings, "GET", "/account", token = alice.token).body()))
                // Email credentials do not change when the public name changes.
                val login = post(settings, "/sessions", json.encodeToString(CreateSession("Alice@example.invalid", "password")))
                assertEquals(identity, json.decodeFromString<SessionCreated>(login.body()).account)
                assertEquals(listOf(identity), json.decodeFromString<UserSearch>(request(settings, "GET", "/v1/users?username=Alicia", token = bob.token).body()).users)
                val add = AddContact(java.util.UUID.randomUUID().toString(), identity.accountId)
                assertEquals(200, request(settings, "POST", "/v1/contacts", json.encodeToString(add).toByteArray(), bob.token).statusCode())
                assertEquals(listOf(identity), json.decodeFromString<ContactList>(request(settings, "GET", "/v1/contacts", token = bob.token).body()).contacts)
                val invitation = CreateInvitation(java.util.UUID.randomUUID().toString(), identity.accountId, "Avatar game")
                val created = request(settings, "POST", "/v1/invitations", json.encodeToString(invitation).toByteArray(), bob.token)
                assertEquals(201, created.statusCode())
                val game = json.decodeFromString<GameSummary>(created.body())
                assertEquals(identity, game.recipient)
                assertEquals(identity, json.decodeFromString<GameList>(request(settings, "GET", "/v1/games", token = bob.token).body()).games.single().recipient)
                assertEquals(identity, json.decodeFromString<GameList>(request(settings, "GET", "/v1/invitations", token = alice.token).body()).games.single().recipient)
                val clear = put("/account/avatar", json.encodeToString(ChangeAvatar()))
                assertEquals(200, clear.statusCode())
                assertNull(json.decodeFromString<AccountIdentity>(clear.body()).avatar)
                assertNull(json.decodeFromString<GameSummary>(request(settings, "GET", "/v1/games/${game.id}/head", token = bob.token).body()).recipient.avatar)
            }
        }
    }
    @Test fun `token favorites are authenticated account scoped durable and distinguish defaults from empty`() {
        OwnedPostgres().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            val settings = settings(db)
            val path = "/account/token-favorites"
            lateinit var token: String
            lateinit var secondDevice: String
            fun get(session: String) = request(settings, "GET", path, token = session)
            fun put(session: String?, favorites: TokenFavorites) = request(settings, "PUT", path,
                json.encodeToString(favorites).toByteArray(), token = session)
            val key = TokenIdentity.key(TokenCatalog.blank.definition)
            SpringService(settings).use { service ->
                service.start(); ready(settings)
                for (method in listOf("GET", "PUT")) assertEquals(401, request(settings, method, path, "{}".toByteArray()).statusCode())
                assertEquals(201, post(settings, "/accounts", json.encodeToString(RegisterAccount("Favorites", "favorites@example.invalid", "password"))).statusCode())
                fun login(email: String) = json.decodeFromString<SessionCreated>(post(settings, "/sessions",
                    json.encodeToString(CreateSession(email, "password"))).body()).token
                token = login("favorites@example.invalid")
                secondDevice = login("favorites@example.invalid")
                val initial = get(token)
                assertEquals(200, initial.statusCode())
                assertNull(json.decodeFromString<TokenFavorites>(initial.body()).keys)
                assertEquals(200, put(token, TokenFavorites(listOf(key, key))).statusCode())
                assertEquals(TokenFavorites(listOf(key)), json.decodeFromString<TokenFavorites>(get(secondDevice).body()))
                assertEquals(400, put(token, TokenFavorites(listOf("printing-digest"))).statusCode())
                assertEquals(400, put(token, TokenFavorites(List(101) { key })).statusCode())
                assertEquals(400, put(token, TokenFavorites()).statusCode())
                assertEquals(TokenFavorites(listOf(key)), json.decodeFromString<TokenFavorites>(get(token).body()))
                assertEquals(201, post(settings, "/accounts", json.encodeToString(RegisterAccount("OtherFavorites", "other-favorites@example.invalid", "password"))).statusCode())
                val other = login("other-favorites@example.invalid")
                assertNull(json.decodeFromString<TokenFavorites>(get(other).body()).keys)
                assertEquals(200, put(token, TokenFavorites(emptyList())).statusCode())
                assertNull(json.decodeFromString<TokenFavorites>(get(other).body()).keys)
                assertEquals(405, request(settings, "POST", path, token = token).statusCode())
            }
            SpringService(settings).use { service ->
                service.start(); ready(settings)
                assertEquals(TokenFavorites(emptyList()), json.decodeFromString<TokenFavorites>(get(secondDevice).body()))
                assertEquals(204, request(settings, "DELETE", "/session", token = token).statusCode())
                assertEquals(401, get(token).statusCode())
                assertEquals(401, put(token, TokenFavorites(listOf(key))).statusCode())
            }
        }
    }
    @Test fun `account settings default to show after first game and are authenticated account scoped and shared by devices`() {
        OwnedPostgres().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            val settings = settings(db)
            val path = "/account/settings"
            fun get(session: String) = request(settings, "GET", path, token = session)
            fun put(session: String?, body: String) = request(settings, "PUT", path, body.toByteArray(), token = session)
            SpringService(settings).use { service ->
                service.start(); ready(settings)
                for (method in listOf("GET", "PUT")) assertEquals(401, request(settings, method, path, "{}".toByteArray()).statusCode())
                fun login(email: String) = json.decodeFromString<SessionCreated>(post(settings, "/sessions",
                    json.encodeToString(CreateSession(email, "password"))).body()).token
                assertEquals(201, post(settings, "/accounts", json.encodeToString(RegisterAccount("Prefs", "prefs@example.invalid", "password"))).statusCode())
                assertEquals(201, post(settings, "/accounts", json.encodeToString(RegisterAccount("OtherPrefs", "other-prefs@example.invalid", "password"))).statusCode())
                val token = login("prefs@example.invalid"); val secondDevice = login("prefs@example.invalid")
                val other = login("other-prefs@example.invalid")
                assertEquals(AccountSettings(DeckVisibility.AFTER_FIRST_GAME), json.decodeFromString<AccountSettings>(get(token).body()))
                for (option in DeckVisibility.entries) {
                    val reply = put(token, json.encodeToString(AccountSettings(option)))
                    assertEquals(200, reply.statusCode())
                    assertEquals(AccountSettings(option), json.decodeFromString<AccountSettings>(reply.body()))
                    assertEquals(AccountSettings(option), json.decodeFromString<AccountSettings>(get(secondDevice).body()))
                }
                assertEquals(400, put(token, """{"deckVisibility":"SOMETIMES"}""").statusCode())
                assertEquals(AccountSettings(DeckVisibility.AFTER_FIRST_GAME), json.decodeFromString<AccountSettings>(get(token).body()))
                assertEquals(AccountSettings(), json.decodeFromString<AccountSettings>(get(other).body()))
                assertEquals(405, request(settings, "POST", path, token = token).statusCode())
            }
        }
    }
    private val json = AccountJson.format
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()
    private fun port() = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1")).use { it.localPort }
    private fun settings(db: OwnedPostgres) = ServiceSettings(db.settings, URI("https://synthetic.invalid"),
        InetAddress.getByName("127.0.0.1"), port(), InetAddress.getByName("127.0.0.1"), port())
    private fun request(settings: ServiceSettings, method: String, path: String, body: ByteArray? = null,
                        token: String? = null, contentType: String = "application/json", xff: String? = null): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(URI("http://127.0.0.1:${settings.publicPort()}$path"))
            .timeout(Duration.ofSeconds(20)).header("Content-Type", contentType)
            .method(method, body?.let(HttpRequest.BodyPublishers::ofByteArray) ?: HttpRequest.BodyPublishers.noBody())
        if (token != null) builder.header("Authorization", "Bearer $token")
        if (xff != null) builder.header("X-Forwarded-For", xff)
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString()).also {
            assertEquals("no-store", it.headers().firstValue("Cache-Control").orElseThrow())
            assertFalse(it.headers().firstValue("Set-Cookie").isPresent, "Must not create cookie sessions")
        }
    }
    private fun post(settings: ServiceSettings, path: String, body: String) = request(settings, "POST", path, body.toByteArray())
    private fun ready(settings: ServiceSettings) {
        val until = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
        do {
            val response = client.send(HttpRequest.newBuilder(URI("http://127.0.0.1:${settings.healthPort()}/health/ready"))
                .timeout(Duration.ofSeconds(2)).build(), HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() == 200) return
            Thread.sleep(20)
        } while (System.nanoTime() < until)
        fail<Unit>("Service did not become ready")
    }

    @Test fun `real entry privacy exact credentials independent sessions restart and idempotent logout`() {
        OwnedPostgres().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            val settings = settings(db)
            val exact = " \tPäSS e\u0301😀\n"
            lateinit var token: String
            lateinit var otherToken: String
            lateinit var identity: AccountIdentity
            SpringService(settings).use { service ->
                service.start(); ready(settings)
                val registration = post(settings, "/accounts", json.encodeToString(RegisterAccount("  Cafe\u0301  ", " Private@BÜCHER.example ", exact)))
                assertEquals(201, registration.statusCode())
                identity = json.decodeFromString(registration.body())
                assertEquals("Café", identity.username)
                java.util.UUID.fromString(identity.accountId)
                assertFalse(registration.body().contains("email", true))
                assertFalse(registration.body().contains("Private", true))
                assertEquals(409, post(settings, "/accounts", json.encodeToString(RegisterAccount("Other", "private@xn--bcher-kva.example", exact))).statusCode())
                assertEquals(409, post(settings, "/accounts", json.encodeToString(RegisterAccount("CAFÉ", "other@example.invalid", exact))).statusCode())
                val wrong = post(settings, "/sessions", json.encodeToString(CreateSession("private@xn--bcher-kva.example", exact.trim())))
                assertEquals(401, wrong.statusCode())
                assertEquals("invalid_credentials", json.decodeFromString<AccountError>(wrong.body()).code)
                val normalized = java.text.Normalizer.normalize(exact, java.text.Normalizer.Form.NFC)
                assertEquals(401, post(settings, "/sessions", json.encodeToString(CreateSession("private@xn--bcher-kva.example", normalized))).statusCode())
                val login = post(settings, "/sessions", json.encodeToString(CreateSession("PRIVATE@XN--BCHER-KVA.EXAMPLE", exact)))
                assertEquals(201, login.statusCode())
                token = json.decodeFromString<SessionCreated>(login.body()).token
                assertEquals(identity, json.decodeFromString<SessionCreated>(login.body()).account)
                val second = post(settings, "/sessions", json.encodeToString(CreateSession("private@bücher.example", exact)))
                assertEquals(201, second.statusCode())
                otherToken = json.decodeFromString<SessionCreated>(second.body()).token
                assertNotEquals(token, otherToken)
                for (invalid in listOf(null, "wrong", token + "=", "A".repeat(43)))
                    assertEquals(401, request(settings, "GET", "/account", token = invalid).statusCode())
                assertEquals(401, request(settings, "GET", "/account?token=$token").statusCode())
                val read = request(settings, "GET", "/account", token = token)
                assertEquals(200, read.statusCode())
                assertEquals(identity, json.decodeFromString<AccountIdentity>(read.body()))
                assertFalse(read.body().contains("email", true))
                assertEquals(404, request(settings, "GET", "/accounts/${identity.accountId}", token = token).statusCode())
                assertEquals(405, request(settings, "GET", "/sessions").statusCode())
                db.connection().use { it.createStatement().execute("UPDATE account_session SET created_at='1900-01-01T00:00:00Z'") }
            }
            SpringService(settings).use { service ->
                service.start(); ready(settings)
                assertEquals(200, request(settings, "GET", "/account", token = token).statusCode())
                assertEquals(204, request(settings, "DELETE", "/session", token = token).statusCode())
                assertEquals(204, request(settings, "DELETE", "/session", token = token).statusCode())
                assertEquals(401, request(settings, "GET", "/account", token = token).statusCode())
                assertEquals(200, request(settings, "GET", "/account", token = otherToken).statusCode())
                assertEquals(401, request(settings, "DELETE", "/session").statusCode())
                println("PASS actual HTTP register/login/read/revoke, exact boundary-whitespace/decomposed Unicode credentials, private identity only, two independent sessions and retained nonexpiring 126-year-old token across normal service restart")
            }
        }
    }

    @Test fun `strict malformed and bounded JSON failures never reflect secrets or create accounts`() {
        OwnedPostgres().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            val settings = settings(db)
            SpringService(settings).use { service ->
                service.start(); ready(settings)
                val inputs = listOf(
                    byteArrayOf(0xc3.toByte(), 0x28),
                    "{\"email\":\"secret@example.invalid\",\"password\":\"abcdefgh\\ud800\"}".toByteArray(),
                    "{\"email\":\"secret@example.invalid\",\"password\":\"abcdefgh\\udc00\"}".toByteArray(),
                    "{\"email\":\"secret@example.invalid\",\"password\":\"abcdefgh\",\"extra\":1}".toByteArray(),
                    "{\"email\":\"secret@example.invalid\",\"password\":123}".toByteArray(),
                    "{\"email\":\"secret@example.invalid\"}".toByteArray(),
                )
                for (body in inputs) {
                    val response = request(settings, "POST", "/sessions", body)
                    assertEquals(400, response.statusCode())
                    assertFalse(response.body().contains("secret")); assertFalse(response.body().contains("abcdefgh"))
                }
                assertEquals(413, request(settings, "POST", "/sessions", ByteArray(8193) { 32 }).statusCode())
                assertEquals(415, request(settings, "POST", "/sessions", "{}".toByteArray(), contentType = "text/plain").statusCode())
                assertEquals(415, request(settings, "POST", "/sessions", "{}".toByteArray(), contentType = "application/json;charset=ISO-8859-1").statusCode())
                db.connection().use { it.createStatement().executeQuery("SELECT count(*) FROM account").use { rows ->
                    assertTrue(rows.next()); assertEquals(0, rows.getLong(1))
                } }
                println("PASS actual HTTP malformed UTF8/unpaired surrogates/schema/body/media limits safely rejected without writes or secret reflection")
            }
        }
    }

    @Test fun `HTTP rate limits use actual peer and normalized email and keep responses generic`() {
        OwnedPostgres().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            val settings = settings(db)
            SpringService(settings).use { service ->
                service.start(); ready(settings)
                repeat(5) {
                    assertEquals(400, post(settings, "/accounts", json.encodeToString(RegisterAccount("", "x@example.invalid", "password"))).statusCode())
                    val email = if (it % 2 == 0) "UNKNOWN@example.invalid" else " unknown@EXAMPLE.INVALID "
                    val response = post(settings, "/sessions", json.encodeToString(CreateSession(email, "wrong-password")))
                    assertEquals(401, response.statusCode())
                    assertEquals(AccountError("invalid_credentials"), json.decodeFromString<AccountError>(response.body()))
                }
                assertEquals(429, request(settings, "POST", "/accounts", "{}".toByteArray(), xff = "192.0.2.99").statusCode())
                val limited = post(settings, "/sessions", json.encodeToString(CreateSession("unknown@example.invalid", "wrong-password")))
                assertEquals(429, limited.statusCode())
                assertEquals("60", limited.headers().firstValue("Retry-After").orElseThrow())
                repeat(4) { assertEquals(401, post(settings, "/sessions", json.encodeToString(CreateSession("other$it@example.invalid", "wrong-password"))).statusCode()) }
                assertEquals(429, request(settings, "POST", "/sessions", "{}".toByteArray(), xff = "192.0.2.100").statusCode())
                println("PASS actual HTTP registration 5/hour/peer, login 5/min/normalized-email and 10/min/peer; untrusted forwarded IP cannot bypass")
            }
        }
    }

    @Test fun `account routes never migrate or bypass writer admission`() {
        OwnedPostgres().start().use { db ->
            val settings = settings(db)
            SpringService(settings).use { service ->
                service.start()
                val registration = post(settings, "/accounts", json.encodeToString(RegisterAccount("name", "user@example.invalid", "password")))
                assertEquals(503, registration.statusCode())
                assertEquals(503, request(settings, "GET", "/account", token = "A".repeat(43)).statusCode())
                db.connection().use { it.createStatement().executeQuery("SELECT count(*) FROM pg_tables WHERE schemaname='public'").use { rows ->
                    assertTrue(rows.next()); assertEquals(0, rows.getInt(1))
                } }
                FoundationMigrations.migrate(db.settings)
                ready(settings)
                assertEquals(201, post(settings, "/accounts", json.encodeToString(RegisterAccount("name", "user@example.invalid", "password"))).statusCode())
                println("PASS real account handlers unavailable on unsupported schema, no automatic migration, ordinary explicit migration/readiness admits requests")
            }
        }
    }
}
