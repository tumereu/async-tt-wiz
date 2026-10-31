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
    // LOB-090
    @Test fun `authenticated endpoint retains latest jank per account and enforces crash and latency daily quotas`() {
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
                    ActionLatency(now.toString(), "game", "Undo", 321, false, serverResultMs = 99.0)),
                    taps = listOf(org.asyncttwiz.protocol.CardTapDiagnostic(now.toString(), "game", "rotated", "predictedAndQueued",
                        firstAnimationFrameMs = 21.0, droppedFrames = 2, popupOpen = true, popupClosing = true)))
                val body = AccountJson.format.encodeToString(batch)
                assertEquals(204, post(body, kind = "latency"))
                assertEquals(1L, f.number("SELECT count(*) FROM debug_crash_report WHERE kind='latency' AND body='$body'"))
                assertEquals(400, post("{}", kind = "latency"))
                assertEquals(400, post(AccountJson.format.encodeToString(batch.copy(taps = batch.taps.map { it.copy(droppedFrames = -1) })), kind = "latency"))
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
                repeat(25) { index ->
                    clock.time = now.plusSeconds(index + 1L)
                    assertEquals(204, post(jankBody, kind = "jank"))
                }
                assertEquals(20L, f.number("SELECT count(*) FROM debug_crash_report WHERE account_id='${f.a.account.accountId}' AND kind='jank'"))
                assertEquals(20L, f.number("""SELECT count(*) FROM debug_crash_report
                    WHERE account_id='${f.a.account.accountId}' AND kind='jank'
                    AND received_at BETWEEN '${now.plusSeconds(6)}' AND '${now.plusSeconds(25)}'"""))
                assertEquals(0L, f.number("SELECT count(*) FROM debug_crash_daily_count WHERE kind='jank'"))

                // Equal timestamps use id as the tiebreaker, without pruning another account.
                f.sql("""INSERT INTO debug_crash_report(id, account_id, received_at, reported_at, app_version, device, body, kind)
                    SELECT lpad(to_hex(i), 32, '0')::uuid, '${f.b.account.accountId}'::uuid,
                        '${now}'::timestamptz, '${now}'::timestamptz, 'debug', 'test', 'tied', 'jank'
                    FROM generate_series(1,22) i""")
                assertEquals(204, post(jankBody, token = f.tokenB, kind = "jank"))
                assertEquals(20L, f.number("SELECT count(*) FROM debug_crash_report WHERE account_id='${f.b.account.accountId}' AND kind='jank'"))
                assertEquals(19L, f.number("""SELECT count(*) FROM debug_crash_report
                    WHERE account_id='${f.b.account.accountId}' AND kind='jank' AND body='tied'
                    AND id >= lpad(to_hex(4), 32, '0')::uuid"""))
                assertEquals(20L, f.number("SELECT count(*) FROM debug_crash_report WHERE account_id='${f.a.account.accountId}' AND kind='jank'"))

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

    // LOB-090
    @Test fun `every report kind keeps only its newest per account and all kinds together keep only the newest in total`() {
        ActivityFixture().use { f ->
            val now = Instant.now()
            val clock = ActivityClock(now)
            val adapter = CrashReportHttp(f.runtime, clock, ReportLimits(crashPerAccount = 3, latencyPerAccount = 4,
                stutterPerAccount = 2, total = 12, maxAgeDays = 5))
            val server = TomcatServletWebServerFactory(0).apply { setAddress(InetAddress.getByName("127.0.0.1")) }.getWebServer({ context ->
                context.addServlet("crash-reports", object : HttpServlet() {
                    override fun service(request: HttpServletRequest, response: HttpServletResponse) {
                        if (!adapter.handle(request, response)) response.status = 404
                    }
                }).addMapping("/")
            })
            server.start()
            try {
                val client = HttpClient.newHttpClient()
                val latency = AccountJson.format.encodeToString(LatencyBatch(listOf(ActionLatency(now.toString(), "game", "Undo", 1, false, serverResultMs = 30.0))))
                val jank = AccountJson.format.encodeToString(JankBatch(listOf(JankEpisode(now.toString(), 800.0, 3, 2, 710.0, 8.0, 710.0, 120f,
                    emptyMap(), emptyList(), true, false, false))))
                var second = 0L
                fun post(kind: String, token: String) {
                    clock.time = now.plusSeconds(++second)
                    val body = when (kind) { "latency" -> latency; "jank" -> jank; else -> "trace $second" }
                    val input = CrashReport(now.toString(), "1.0 release", "Model; Android 15", body, kind)
                    val request = HttpRequest.newBuilder(URI("http://127.0.0.1:${server.port}/debug/crash-reports"))
                        .header("Content-Type", "application/json").header("Authorization", "Bearer $token")
                        .POST(HttpRequest.BodyPublishers.ofString(AccountJson.format.encodeToString(input))).build()
                    assertEquals(204, client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode())
                }
                fun count(account: String, kind: String) =
                    f.number("SELECT count(*) FROM debug_crash_report WHERE account_id='$account' AND kind='$kind'")
                val a = f.a.account.accountId; val b = f.b.account.accountId
                repeat(5) { post("crash", f.tokenA) }
                repeat(6) { post("latency", f.tokenA) }
                repeat(4) { post("jank", f.tokenA) }
                assertEquals(listOf(3L, 4L, 2L), listOf("crash", "latency", "jank").map { count(a, it) })
                // The newest are kept: crashes 3..5 of the five sent.
                assertEquals(1L, f.number("SELECT count(*) FROM debug_crash_report WHERE account_id='$a' AND kind='crash' AND body='trace 3'"))
                assertEquals(0L, f.number("SELECT count(*) FROM debug_crash_report WHERE account_id='$a' AND kind='crash' AND body='trace 2'"))
                // Another account's reports fill the total; the oldest go first, whoever sent them.
                repeat(3) { post("crash", f.tokenB) }
                repeat(4) { post("latency", f.tokenB) }
                assertEquals(12L, f.number("SELECT count(*) FROM debug_crash_report"))
                assertEquals(listOf(0L, 3L, 2L), listOf("crash", "latency", "jank").map { count(a, it) })
                assertEquals(listOf(3L, 4L), listOf("crash", "latency").map { count(b, it) })
                // Older than the age limit goes as well.
                second = 5L * 86_400 + 30
                post("crash", f.tokenB)
                assertEquals(1L, f.number("SELECT count(*) FROM debug_crash_report"))
            } finally { server.stop(); server.destroy() }
        }
    }

    // LOB-090
    @Test fun `report bounds come from the environment with defaults for missing or invalid values`() {
        assertEquals(ReportLimits(20, 60, 20, 500, 30), ReportLimits.fromEnvironment(emptyMap()))
        assertEquals(ReportLimits(5, 60, 7, 1000, 30), ReportLimits.fromEnvironment(mapOf("CRASH_REPORT_LIMIT_PER_ACCOUNT" to "5",
            "LATENCY_REPORT_LIMIT_PER_ACCOUNT" to "lots", "STUTTER_REPORT_LIMIT_PER_ACCOUNT" to " 7 ", "DIAGNOSTIC_REPORT_LIMIT" to "1000",
            "DIAGNOSTIC_REPORT_MAX_AGE_DAYS" to "0")))
        assertEquals(ProblemReportLimits(20, 200), ProblemReportLimits.fromEnvironment(emptyMap()))
        assertEquals(ProblemReportLimits(3, 50), ProblemReportLimits.fromEnvironment(mapOf("PROBLEM_REPORT_LIMIT_PER_ACCOUNT" to "3",
            "PROBLEM_REPORT_LIMIT" to "50")))
    }
}
