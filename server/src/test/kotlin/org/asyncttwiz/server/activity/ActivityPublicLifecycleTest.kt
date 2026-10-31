package org.asyncttwiz.server.activity

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.serialization.encodeToString
import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.ServiceSettings
import org.asyncttwiz.server.SpringService
import org.asyncttwiz.server.EphemeralPorts
import org.asyncttwiz.server.startBound
import org.asyncttwiz.server.database.FoundationMigrations
import org.asyncttwiz.server.database.FreshDatabase
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** Real normal entry point, both listeners, owned PostgreSQL and scheduled work. No provider network. */
class ActivityPublicLifecycleTest {
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()
    private val json = ActivityWire.json
    private fun id() = UUID.randomUUID().toString()
    private fun await(message: String, condition: () -> Boolean) {
        val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
        while (!condition()) { check(System.nanoTime() < end) { message }; Thread.sleep(25) }
    }
    private fun request(port: Int, method: String, path: String, token: String? = null, body: String? = null): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(URI("http://127.0.0.1:$port$path")).timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/json").method(method,
                body?.let(HttpRequest.BodyPublishers::ofString) ?: HttpRequest.BodyPublishers.noBody())
        if (token != null) builder.header("Authorization", "Bearer $token")
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString()).also {
            assertEquals("no-store", it.headers().firstValue("Cache-Control").orElseThrow())
            assertFalse(it.headers().firstValue("Set-Cookie").isPresent)
        }
    }
    private fun account(port: Int, name: String): SessionCreated {
        val email = "$name@example.invalid"
        assertEquals(201, request(port, "POST", "/accounts", body = json.encodeToString(
            RegisterAccount(name, email, "synthetic password"))).statusCode())
        val login = request(port, "POST", "/sessions", body = json.encodeToString(CreateSession(email, "synthetic password")))
        assertEquals(201, login.statusCode()); return json.decodeFromString(login.body())
    }
    private fun workers() = Thread.getAllStackTraces().keys.filter { it.name == "activity-hints" && it.isAlive }.toSet()

    @Test fun `public authenticated activity and provider independent scheduled maintenance stop with real service`() {
        FreshDatabase().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            val config = EphemeralPorts.settings(db.settings)
            lateinit var settings: ServiceSettings
            fun ready() = await("public service readiness") {
                request(settings.healthPort(), "GET", "/health/ready").statusCode() == 200
            }
            fun number(sql: String) = db.connection().use { c -> c.createStatement().executeQuery(sql).use { it.next(); it.getLong(1) } }
            fun sql(sql: String) { db.connection().use { it.createStatement().execute(sql) } }
            val before = workers()
            lateinit var scheduled: Thread
            lateinit var inviter: SessionCreated
            lateinit var recipient: SessionCreated
            lateinit var registration: ActivityRegistration
            fun invite(): GameSummary {
                val result = request(settings.publicPort(), "POST", "/v1/invitations", inviter.token,
                    json.encodeToString(CreateInvitation(id(), recipient.account.accountId, "Private game name")))
                assertEquals(201, result.statusCode()); return json.decodeFromString(result.body())
            }
            SpringService(config).use { service ->
                settings = service.startBound(config); ready()
                await("maintenance worker started without Firebase") { (workers() - before).size == 1 }
                scheduled = (workers() - before).single()
                assertEquals(401, request(settings.publicPort(), "GET", "/activity").statusCode())
                assertEquals(404, request(settings.healthPort(), "GET", "/activity").statusCode())
                inviter = account(settings.publicPort(), "Inviter")
                recipient = account(settings.publicPort(), "Recipient")
                assertEquals(200, request(settings.publicPort(), "POST", "/v1/contacts", inviter.token,
                    json.encodeToString(AddContact(id(), recipient.account.accountId))).statusCode())
                val put = request(settings.publicPort(), "PUT", "/activity/installations", recipient.token,
                    json.encodeToString(RegisterInstallation(id(), id(), "synthetic-push")))
                assertEquals(200, put.statusCode()); registration = json.decodeFromString(put.body())
                val game = invite()
                val page = request(settings.publicPort(), "GET", "/activity?after=0", recipient.token)
                assertEquals(200, page.statusCode())
                val hint = json.decodeFromString<ActivityPage>(page.body()).entries.single()
                assertEquals(game.id, hint.gameId); assertEquals(game.invitationId, hint.identity)
                assertFalse(page.body().contains("Private")); assertFalse(page.body().contains("@example"))
                assertFalse(page.body().contains(recipient.token))
                val own = request(settings.publicPort(), "GET", "/activity?after=0", inviter.token)
                assertTrue(json.decodeFromString<ActivityPage>(own.body()).entries.isEmpty())
                assertEquals(1L, number("SELECT count(*) FROM activity_push"))
                sql("UPDATE account_activity SET created_at=now()-interval '31 days'; UPDATE activity_push SET expires_at=now()-interval '1 second'")
                // No account HTTP reads after expiry: only the real scheduled lifecycle can prune.
                await("provider-free scheduled expiry") {
                    number("SELECT count(*) FROM account_activity") == 0L && number("SELECT count(*) FROM activity_push") == 0L
                }
                assertEquals(1L, number("SELECT retention_floor FROM activity_cursor WHERE account_id='${recipient.account.accountId}'"))
            }
            assertFalse(scheduled.isAlive, "close must join its maintenance scheduler")
            assertEquals(before, workers())
            assertThrows(java.io.IOException::class.java) { request(settings.publicPort(), "GET", "/activity", recipient.token) }

            val deliveries = CopyOnWriteArrayList<Pair<String, ActivityPushHint>>()
            val pause = AtomicBoolean()
            val sending = CountDownLatch(1)
            val release = CountDownLatch(1)
            SpringService(config, ActivityPushAdapter { token, hint ->
                deliveries += token to hint
                if (pause.get()) {
                    sending.countDown()
                    check(release.await(10, TimeUnit.SECONDS))
                    // A provider call can consume a later queued hint's remaining TTL.
                    sql("UPDATE activity_push SET expires_at=now()-interval '1 second'")
                }
                PushResult(PushStatus.DELIVERED)
            }).use { service ->
                settings = service.startBound(config); ready()
                val game = invite()
                await("explicit adapter scheduled delivery") { deliveries.size == 1 && number("SELECT count(*) FROM activity_push") == 0L }
                assertEquals("synthetic-push", deliveries.single().first)
                assertEquals(registration.generation, deliveries.single().second.generation)
                assertEquals(game.id, deliveries.single().second.gameId)
                pause.set(true)
                try {
                    invite()
                    assertTrue(sending.await(10, TimeUnit.SECONDS))
                    invite() // Queued while the preceding adapter call owns no DB transaction.
                } finally { release.countDown() }
                await("expiry rechecked between adapter calls") { number("SELECT count(*) FROM activity_push") == 0L }
                assertEquals(2, deliveries.size, "must not deliver the hint expired during the preceding send")
                assertEquals(204, request(settings.publicPort(), "DELETE", "/session", recipient.token).statusCode())
                assertEquals(401, request(settings.publicPort(), "GET", "/activity", recipient.token).statusCode())
                assertEquals(0L, number("SELECT count(*) FROM activity_installation"))
                assertEquals(200, request(settings.publicPort(), "GET", "/activity?after=0", inviter.token).statusCode())
            }
            assertEquals(before, workers(), "restart must not leak a second scheduler")
            println("PASS normal public SpringService authenticated activity/privacy/account isolation, provider-free scheduled event+push expiry, explicit optional adapter routing, TTL recheck after in-flight send without holding a DB transaction, logout cleanup, close/join/listener shutdown and restart; no real FCM/native/gameplay-group claim")
        }
    }
}
