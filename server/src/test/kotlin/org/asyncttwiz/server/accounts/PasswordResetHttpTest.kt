package org.asyncttwiz.server.accounts

import java.net.InetAddress
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path
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
import org.junit.jupiter.api.io.TempDir

class PasswordResetHttpTest {
    @TempDir lateinit var directory: Path
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()
    private val json = AccountJson.format
    private fun settings(db: OwnedPostgres, mail: ResetMailSettings) = ServiceSettings(db.settings, URI("https://synthetic.invalid"),
        InetAddress.getByName("127.0.0.1"), SyntheticMailbox.freePort(), InetAddress.getByName("127.0.0.1"), SyntheticMailbox.freePort(), mail)
    private fun request(settings: ServiceSettings, method: String, path: String, body: String? = null,
                        headers: Map<String, String> = emptyMap()): HttpResponse<String> {
        val request = HttpRequest.newBuilder(URI("http://127.0.0.1:${settings.publicPort()}$path"))
            .timeout(Duration.ofSeconds(20)).method(method, body?.let(HttpRequest.BodyPublishers::ofString) ?: HttpRequest.BodyPublishers.noBody())
        headers.forEach { (key, value) -> request.header(key, value) }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString()).also {
            assertEquals("no-store", it.headers().firstValue("Cache-Control").orElseThrow())
            assertFalse(it.body().contains("Exception"))
        }
    }
    private fun post(settings: ServiceSettings, path: String, body: String) = request(settings, "POST", path, body, mapOf("Content-Type" to "application/json"))
    private fun ready(settings: ServiceSettings) {
        val until = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
        while (System.nanoTime() < until) {
            val response = client.send(HttpRequest.newBuilder(URI("http://127.0.0.1:${settings.healthPort()}/health/ready"))
                .timeout(Duration.ofSeconds(2)).build(), HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() == 200) return
            Thread.sleep(20)
        }
        fail<Unit>("Reset worker/service not ready")
    }
    private fun session(settings: ServiceSettings, email: String, password: String): String {
        val response = post(settings, "/sessions", json.encodeToString(CreateSession(email, password)))
        assertEquals(201, response.statusCode())
        return json.decodeFromString<SessionCreated>(response.body()).token
    }
    private fun authorized(settings: ServiceSettings, token: String) =
        request(settings, "GET", "/account", headers = mapOf("Authorization" to "Bearer $token")).statusCode()
    private fun form(vararg fields: Pair<String, String>) = fields.joinToString("&") { (key, value) ->
        key + "=" + URLEncoder.encode(value, Charsets.UTF_8)
    }

    @Test fun `actual HTTP JSON and HTTPS link fallback form use explicit CSRF confirmation not GET`() {
        OwnedPostgres().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            SyntheticMailbox(directory.resolve("mail")).use { box ->
                val settings = settings(db, box.settings())
                SpringService(settings).use { server ->
                    server.start(); ready(settings)
                    val email = "reset@example.invalid"
                    assertEquals(201, post(settings, "/accounts", json.encodeToString(RegisterAccount("Reset wizard", email, "old-password"))).statusCode())
                    val first = session(settings, email, "old-password")
                    val second = session(settings, email, "old-password")
                    val unknown = post(settings, "/password-reset-requests", json.encodeToString(RequestPasswordReset("unknown@example.invalid")))
                    val known = post(settings, "/password-reset-requests", json.encodeToString(RequestPasswordReset(" RESET@EXAMPLE.INVALID ")))
                    assertEquals(202, unknown.statusCode()); assertEquals(known.statusCode(), unknown.statusCode())
                    assertEquals(known.body(), unknown.body()); assertTrue(known.body().isEmpty())
                    assertFalse(known.headers().firstValue("Set-Cookie").isPresent)
                    assertEquals(200, authorized(settings, first)); assertEquals(200, authorized(settings, second))
                    val link = box.awaitLinks(1).single()
                    val path = URI(link).rawPath + "?" + URI(link).rawQuery
                    val token = URI(link).query.substringAfter("token=")
                    val checked = post(settings, "/password-reset-links", json.encodeToString(CheckPasswordResetLink(token)))
                    assertEquals(204, checked.statusCode()); assertTrue(checked.body().isEmpty())
                    assertFalse(checked.headers().firstValue("Set-Cookie").isPresent)
                    assertEquals(400, post(settings, "/password-reset-links", json.encodeToString(CheckPasswordResetLink("A".repeat(43)))).statusCode())
                    assertEquals(405, request(settings, "GET", "/password-reset-links").statusCode())
                    assertEquals(200, authorized(settings, first)); assertEquals(200, authorized(settings, second))
                    val page = request(settings, "GET", path)
                    assertEquals(200, page.statusCode())
                    assertEquals("no-referrer", page.headers().firstValue("Referrer-Policy").orElseThrow())
                    val csp = page.headers().firstValue("Content-Security-Policy").orElseThrow()
                    assertTrue(csp.contains("default-src 'none'") && csp.contains("form-action 'self'"))
                    assertFalse(page.body().contains("<script")); assertFalse(page.body().contains("src=\"http"))
                    assertTrue(page.body().contains("method=\"post\""))
                    assertEquals(200, authorized(settings, first)); assertEquals(200, authorized(settings, second))
                    val cookieHeader = page.headers().firstValue("Set-Cookie").orElseThrow()
                    assertTrue(cookieHeader.contains("Secure; HttpOnly; SameSite=Strict"))
                    val cookie = cookieHeader.substringBefore(';')
                    val csrf = Regex("name=\"csrf\" value=\"([^\"]+)\"").find(page.body())!!.groupValues[1]
                    val password = " \tNew e\u0301😀 password\n"
                    val body = form("token" to token, "csrf" to csrf, "password" to password)
                    val headers = mapOf("Content-Type" to "application/x-www-form-urlencoded", "Cookie" to cookie,
                        "Origin" to "https://reset.synthetic.invalid")
                    assertEquals(403, request(settings, "POST", "/reset-password", body, headers - "Cookie").statusCode())
                    assertEquals(403, request(settings, "POST", "/reset-password", body, headers + ("Origin" to "https://evil.invalid")).statusCode())
                    assertEquals(200, authorized(settings, first))
                    assertEquals(200, request(settings, "POST", "/reset-password", body, headers).statusCode())
                    assertEquals(401, authorized(settings, first)); assertEquals(401, authorized(settings, second))
                    val current = session(settings, email, password)
                    assertEquals(400, post(settings, "/password-reset-links", json.encodeToString(CheckPasswordResetLink(token))).statusCode())
                    assertEquals(400, request(settings, "GET", path).statusCode())
                    assertEquals(400, post(settings, "/password-resets", json.encodeToString(ResetPassword(token, "another-password"))).statusCode())
                    assertEquals(200, authorized(settings, current))
                    val invalid = request(settings, "GET", "/reset-password?token=not-valid")
                    assertEquals(400, invalid.statusCode()); assertTrue(invalid.body().contains("Request another link"))
                    assertFalse(invalid.body().contains("not-valid"))
                    val requestPage = request(settings, "GET", "/reset-password")
                    val requestCookie = requestPage.headers().firstValue("Set-Cookie").orElseThrow().substringBefore(';')
                    val requestCsrf = Regex("name=\"csrf\" value=\"([^\"]+)\"").find(requestPage.body())!!.groupValues[1]
                    assertEquals(202, request(settings, "POST", "/reset-password/request", form("token" to "", "csrf" to requestCsrf, "email" to email),
                        headers + ("Cookie" to requestCookie)).statusCode())
                    val nextToken = URI(box.awaitLinks(2).last()).query.substringAfter("token=")
                    assertEquals(204, post(settings, "/password-resets", json.encodeToString(ResetPassword(nextToken, "api-new-password"))).statusCode())
                    assertEquals(401, authorized(settings, current))
                    assertEquals(201, post(settings, "/sessions", json.encodeToString(CreateSession(email, "api-new-password"))).statusCode())
                    assertEquals(405, request(settings, "GET", "/password-resets").statusCode())
                    assertEquals(400, post(settings, "/password-resets", "{\"token\":\"x\",\"password\":\"abcdefgh\\ud800\"}").statusCode())
                    assertEquals(400, post(settings, "/password-reset-requests", "{\"email\":\"not an email\"}").statusCode())
                    assertEquals(202, post(settings, "/password-reset-requests", json.encodeToString(RequestPasswordReset(email))).statusCode())
                    val limited = post(settings, "/password-reset-requests", json.encodeToString(RequestPasswordReset(email.uppercase())))
                    assertEquals(429, limited.statusCode()); assertEquals("3600", limited.headers().firstValue("Retry-After").orElseThrow())
                    println("PASS actual Spring HTTP JSON reset + worker SMTP, known/unknown generic response, GET nonmutation, strict form CSRF/cookie/origin/CSP/no-referrer/no third parties, exact Unicode-whitespace browser password, no auto-login, all sessions revoked only on success, invalid/reused links safe with request-another route, JSON confirmation/rate limits/malformed scalars")
                }
            }
        }
    }

    @Test fun `actual Spring worker recovers encrypted pending mail and wrong key blocks readiness until corrected restart`() {
        OwnedPostgres().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            SyntheticMailbox(directory.resolve("mail")).use { box ->
                val settings = settings(db, box.settings())
                box.fault("temporary")
                SpringService(settings).use { server ->
                    server.start(); ready(settings)
                    assertEquals(201, post(settings, "/accounts", json.encodeToString(RegisterAccount("Restart wizard", "restart@example.invalid", "password"))).statusCode())
                    assertEquals(202, post(settings, "/password-reset-requests", json.encodeToString(RequestPasswordReset("restart@example.invalid"))).statusCode())
                    val until = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
                    var attempted = false
                    while (!attempted && System.nanoTime() < until) {
                        attempted = db.connection().use { it.createStatement().executeQuery("SELECT count(*) FROM reset_mail WHERE attempts=1 AND status='pending'").use { rows -> rows.next(); rows.getInt(1) == 1 } }
                        Thread.sleep(20)
                    }
                    assertTrue(attempted)
                }
                val mail = box.settings()
                val wrong = ResetMailSettings(mail.host, mail.port, mail.tls, null, null, mail.from, mail.resetOrigin, ByteArray(32) { 99 })
                val bad = ServiceSettings(settings.database(), settings.publicBaseUrl(), settings.publicAddress(), settings.publicPort(), settings.healthAddress(), settings.healthPort(), wrong)
                SpringService(bad).use { server ->
                    server.start()
                    Thread.sleep(1000)
                    val response = client.send(HttpRequest.newBuilder(URI("http://127.0.0.1:${settings.healthPort()}/health/ready")).build(), HttpResponse.BodyHandlers.ofString())
                    assertEquals(503, response.statusCode())
                    assertEquals(503, post(settings, "/password-reset-requests", json.encodeToString(RequestPasswordReset("restart@example.invalid"))).statusCode())
                }
                // Controlled test fixture makes the persisted retry due; no runtime test endpoint or real minute wait.
                db.connection().use { it.createStatement().execute("UPDATE reset_mail SET next_attempt_at=now()") }
                box.fault("ok")
                val started = System.nanoTime()
                SpringService(settings).use { server ->
                    server.start(); ready(settings); box.awaitLinks(1)
                    assertTrue(box.links().size == 1)
                }
                assertTrue(System.nanoTime() - started < TimeUnit.SECONDS.toNanos(30))
                println("PASS actual Spring/service shutdown/restart retains pending encrypted mail, wrong key blocks readiness/admission without data deletion, corrected restart delivers using original token, bounded worker close")
            }
        }
    }
}
