package org.asyncttwiz.server.diagnostics

import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.net.InetAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant
import kotlinx.serialization.encodeToString
import org.asyncttwiz.protocol.AccountJson
import org.asyncttwiz.protocol.CrashReport
import org.asyncttwiz.protocol.ActionLatency
import org.asyncttwiz.protocol.LatencyBatch
import org.asyncttwiz.protocol.ReconnectEpisode
import org.asyncttwiz.protocol.ReconnectStep
import org.asyncttwiz.protocol.JankBatch
import org.asyncttwiz.protocol.JankEpisode
import org.asyncttwiz.server.activity.ActivityFixture
import org.asyncttwiz.server.activity.ActivityClock
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory

class CrashReportHttpTest {
    @Test fun `authenticated endpoint stores truncates retains both kinds and enforces independent daily quotas`() {
        ActivityFixture().use { f ->
            val now = Instant.now()
            val clock = ActivityClock(now)
            val adapter = CrashReportHttp(f.runtime, clock)
            val factory = TomcatServletWebServerFactory(0).apply { setAddress(InetAddress.getByName("127.0.0.1")) }
            val server = factory.getWebServer({ context ->
                context.addServlet("crash-reports", object : HttpServlet() {
                    override fun service(request: HttpServletRequest, response: HttpServletResponse) {
                        if (!adapter.handle(request, response)) response.status = 404
                    }
                }).addMapping("/")
            })
            server.start()
            try {
                val client = HttpClient.newHttpClient()
                fun post(body: String = "trace", token: String? = f.tokenA, method: String = "POST", kind: String = "crash"): Int {
                    val input = CrashReport(now.minusSeconds(10).toString(), "1.0 debug commit abc", "Model; Android 15", body, kind)
                    val request = HttpRequest.newBuilder(URI("http://127.0.0.1:${server.port}/debug/crash-reports"))
                        .header("Content-Type", "application/json")
                        .method(method, HttpRequest.BodyPublishers.ofString(AccountJson.format.encodeToString(input)))
                    if (token != null) request.header("Authorization", "Bearer $token")
                    val response = client.send(request.build(), HttpResponse.BodyHandlers.ofString())
                    assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow())
                    return response.statusCode()
                }
                assertEquals(401, post(token = null))
                assertEquals(405, post(method = "GET"))
                assertEquals(204, post("x".repeat(65_535) + "€".repeat(10_000)))
                assertEquals(65_535L, f.number("SELECT octet_length(body) FROM debug_crash_report"))
                assertEquals(1L, f.number("""SELECT count(*) FROM debug_crash_report
                    WHERE account_id='${f.a.account.accountId}' AND reported_at='${now.minusSeconds(10)}'
                    AND app_version='1.0 debug commit abc' AND device='Model; Android 15'"""))

                val batch = LatencyBatch(listOf(
                    ActionLatency(now.toString(), "game", "Play.MoveCard", 321, true, 22.5, 35.0),
                    ActionLatency(now.toString(), "game", "Undo", 321, false, serverResultMs = 99.0)))
                val body = AccountJson.format.encodeToString(batch)
                assertEquals(204, post(body, kind = "latency"))
                assertEquals(1L, f.number("SELECT count(*) FROM debug_crash_report WHERE kind='latency' AND body='$body'"))
                assertEquals(400, post("{}", kind = "latency"))
                assertEquals(400, post(body, kind = "other"))
                assertEquals(413, post(" ".repeat(65_537), kind = "latency"))

                val jank = JankEpisode(now.toString(), 800.0, 3, 2, 710.0, 8.0, 710.0, 120f,
                    mapOf("screen" to "table", "popup" to "library"), listOf("Actions", "Play.MoveCard"), true, false, true)
                val jankBody = AccountJson.format.encodeToString(JankBatch(listOf(jank)))
                assertEquals(204, post(jankBody, kind = "jank"))
                assertEquals(1L, f.number("SELECT count(*) FROM debug_crash_report WHERE kind='jank' AND body='$jankBody'"))
                assertEquals(400, post("{}", kind = "jank"))
                assertEquals(400, post(AccountJson.format.encodeToString(JankBatch(listOf(jank.copy(jankyCount = 4)))), kind = "jank"))
                assertEquals(413, post(" ".repeat(65_537), kind = "jank"))
                repeat(2) { assertEquals(204, post(jankBody, kind = "jank")) }
                assertEquals(429, post(jankBody, kind = "jank"))
                assertEquals(3L, f.number("SELECT reports FROM debug_crash_daily_count WHERE account_id='${f.a.account.accountId}' AND kind='jank'"))

                val episode = ReconnectEpisode("episode", "game", now.toString(), "socketClose:1001:Reconnect",
                    listOf(ReconnectStep("socketConnectedHttpAuthenticated", 20, 20), ReconnectStep("ready", 70, 50)), 70, true)
                val reconnectBody = AccountJson.format.encodeToString(LatencyBatch(emptyList(), listOf(episode)))
                assertEquals(204, post(reconnectBody, kind = "latency"))
                assertEquals(1L, f.number("SELECT count(*) FROM debug_crash_report WHERE kind='latency' AND body='$reconnectBody'"))
                assertEquals(400, post(AccountJson.format.encodeToString(LatencyBatch(emptyList(), listOf(episode.copy(durationMs = -1)))), kind = "latency"))
                assertEquals(400, post(AccountJson.format.encodeToString(LatencyBatch(emptyList(), listOf(episode.copy(steps = List(33) { ReconnectStep("x", 0, 0) })))), kind = "latency"))
                repeat(58) { assertEquals(204, post(body, kind = "latency")) }
                assertEquals(429, post(body, kind = "latency"))
                assertEquals(60L, f.number("SELECT reports FROM debug_crash_daily_count WHERE account_id='${f.a.account.accountId}' AND kind='latency'"))
                assertEquals(1L, f.number("SELECT reports FROM debug_crash_daily_count WHERE account_id='${f.a.account.accountId}' AND kind='crash'"))
                assertEquals(204, post(body, token = f.tokenB, kind = "latency"))

                // Seed historical reports without consuming today's quota.
                f.sql("""INSERT INTO debug_crash_report(id, account_id, received_at, reported_at, app_version, device, body, kind)
                    SELECT md5('old-' || i)::uuid, '${f.b.account.accountId}'::uuid,
                        '${now}'::timestamptz - interval '31 days', '${now}'::timestamptz, 'old', 'old', 'old',
                        CASE WHEN i % 2 = 0 THEN 'latency' ELSE 'crash' END
                    FROM generate_series(1,6) i""")
                f.sql("""INSERT INTO debug_crash_report(id, account_id, received_at, reported_at, app_version, device, body, kind)
                    SELECT md5('retained-' || i)::uuid, '${f.b.account.accountId}'::uuid,
                        '${now}'::timestamptz - interval '1 day' - i * interval '1 second', '${now}'::timestamptz, 'old', 'old', 'retained', 'latency'
                    FROM generate_series(1,510) i""")
                assertEquals(204, post()) // Exhausting latency must not block a real crash report.
                assertEquals(500L, f.number("SELECT count(*) FROM debug_crash_report"))
                assertEquals(0L, f.number("SELECT count(*) FROM debug_crash_report WHERE body='old'"))
                assertEquals(0L, f.number("SELECT count(*) FROM debug_crash_report WHERE id=md5('retained-510')::uuid"))
                repeat(18) { assertEquals(204, post()) }
                assertEquals(429, post())
                assertEquals(20L, f.number("SELECT reports FROM debug_crash_daily_count WHERE account_id='${f.a.account.accountId}' AND kind='crash'"))
                assertEquals(204, post(token = f.tokenB))
                // Even body eviction cannot reset the daily counter.
                f.sql("DELETE FROM debug_crash_report WHERE account_id='${f.a.account.accountId}'")
                assertEquals(429, post())
                assertEquals(429, post(body, kind = "latency"))
                clock.time = now.plusSeconds(86_400)
                assertEquals(204, post())
                assertEquals(1L, f.number("SELECT sum(reports) FROM debug_crash_daily_count"))
                assertEquals(204, post(body, kind = "latency"))
                assertEquals(2L, f.number("SELECT sum(reports) FROM debug_crash_daily_count"))
                f.accounts.revoke(f.a)
                assertEquals(401, post())
            } finally { server.stop(); server.destroy() }
        }
    }
}
