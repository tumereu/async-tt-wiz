package org.asyncttwiz.server.activity

import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.net.InetAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlinx.serialization.encodeToString
import org.asyncttwiz.protocol.*
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ActivityHttpTest {
    @Test fun `real HTTP adapter authorizes bounds cursor validates registration and refuses revoked session`() {
        ActivityFixture().use { f ->
            val adapter = ActivityHttp(f.runtime)
            val factory = TomcatServletWebServerFactory(0).apply { setAddress(InetAddress.getByName("127.0.0.1")) }
            val server = factory.getWebServer({ context ->
                context.addServlet("activity", object : HttpServlet() {
                    override fun service(request: HttpServletRequest, response: HttpServletResponse) {
                        if (!adapter.handle(request, response)) response.status = 404
                    }
                }).addMapping("/")
            })
            server.start()
            try {
                val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()
                fun request(method: String, path: String, token: String? = f.tokenB, body: String? = null, media: String = "application/json"):
                    HttpResponse<String> {
                    val request = HttpRequest.newBuilder(URI("http://127.0.0.1:${server.port}$path")).timeout(Duration.ofSeconds(10))
                        .header("Content-Type", media).method(method, body?.let(HttpRequest.BodyPublishers::ofString) ?: HttpRequest.BodyPublishers.noBody())
                    if (token != null) request.header("Authorization", "Bearer $token")
                    return client.send(request.build(), HttpResponse.BodyHandlers.ofString()).also {
                        assertEquals("no-store", it.headers().firstValue("Cache-Control").orElseThrow())
                    }
                }
                assertEquals(401, request("GET", "/activity", null).statusCode())
                val reset = request("GET", "/activity")
                assertEquals(200, reset.statusCode()); assertTrue(ActivityWire.json.decodeFromString<ActivityPage>(reset.body()).summaryReset)
                val game = f.create()
                val presence = GamePresence(game.id, ActivityFixture.id(), true)
                assertEquals(401, request("PUT", "/activity/presence", null, ActivityWire.json.encodeToString(presence)).statusCode())
                assertEquals(204, request("PUT", "/activity/presence", body = ActivityWire.json.encodeToString(presence)).statusCode())
                assertEquals(204, request("PUT", "/activity/presence", body = ActivityWire.json.encodeToString(presence.copy(left = true))).statusCode())
                assertEquals(404, request("PUT", "/activity/presence", body = ActivityWire.json.encodeToString(presence.copy(gameId = ActivityFixture.id()))).statusCode())
                assertEquals(400, request("PUT", "/activity/presence", body = ActivityWire.json.encodeToString(presence.copy(presenceId = "bad"))).statusCode())
                assertEquals(1, ActivityWire.json.decodeFromString<ActivityPage>(request("GET", "/activity?after=0").body()).entries.size)
                assertEquals(400, request("GET", "/activity?after=bad").statusCode())
                assertEquals(405, request("POST", "/activity", body = "{}").statusCode())
                val input = RegisterInstallation(ActivityFixture.id(), ActivityFixture.id())
                val registration = request("PUT", "/activity/installations", body = ActivityWire.json.encodeToString(input))
                assertEquals(200, registration.statusCode()); assertTrue(DeckValidation.uuid(ActivityWire.json.decodeFromString<ActivityRegistration>(registration.body()).generation))
                assertEquals(415, request("PUT", "/activity/installations", body = "{}", media = "text/plain").statusCode())
                assertEquals(400, request("PUT", "/activity/installations", body = "{}").statusCode())
                assertEquals(413, request("PUT", "/activity/installations", body = " ".repeat(8193)).statusCode())
                assertEquals(204, request("DELETE", "/activity/installations?id=${input.installationId}").statusCode())
                assertEquals(401, request("GET", "/activity/waiting", null).statusCode())
                val waiting = request("GET", "/activity/waiting")
                assertEquals(200, waiting.statusCode()); assertTrue(ActivityWire.json.decodeFromString<WaitingGames>(waiting.body()).games.isEmpty())
                assertEquals(405, request("POST", "/activity/waiting", body = "{}").statusCode())
                f.accounts.revoke(f.b)
                assertEquals(401, request("GET", "/activity?after=0").statusCode())
                println("PASS actual isolated HTTP adapter with real PostgreSQL bearer auth/cursor/reset/installation bounds/no-store/revocation; public SpringService router wiring remains I30b")
            } finally { server.stop(); server.destroy() }
        }
    }
}
