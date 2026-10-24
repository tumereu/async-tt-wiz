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
import org.asyncttwiz.server.activity.ActivityFixture
import org.asyncttwiz.server.activity.ActivityClock
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory

class CrashReportHttpTest {
    @Test fun `authenticated endpoint stores truncates retains and enforces account quota independently of eviction`() {
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
                fun post(body: String = "trace", token: String? = f.tokenA, method: String = "POST"): Int {
                    val input = CrashReport(now.minusSeconds(10).toString(), "1.0 debug commit abc", "Model; Android 15", body)
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

                // Seed historical reports without consuming today's quota.
                f.sql("""INSERT INTO debug_crash_report
                    SELECT md5('old-' || i)::uuid, '${f.b.account.accountId}'::uuid,
                        '${now}'::timestamptz - interval '31 days', '${now}'::timestamptz, 'old', 'old', 'old'
                    FROM generate_series(1,3) i""")
                f.sql("""INSERT INTO debug_crash_report
                    SELECT md5('retained-' || i)::uuid, '${f.b.account.accountId}'::uuid,
                        '${now}'::timestamptz - interval '1 day' - i * interval '1 second', '${now}'::timestamptz, 'old', 'old', 'retained'
                    FROM generate_series(1,510) i""")
                assertEquals(204, post())
                assertEquals(500L, f.number("SELECT count(*) FROM debug_crash_report"))
                assertEquals(0L, f.number("SELECT count(*) FROM debug_crash_report WHERE body='old'"))
                assertEquals(0L, f.number("SELECT count(*) FROM debug_crash_report WHERE id=md5('retained-510')::uuid"))
                repeat(18) { assertEquals(204, post()) }
                assertEquals(429, post())
                assertEquals(204, post(token = f.tokenB))
                // Even body eviction cannot reset the daily counter.
                f.sql("DELETE FROM debug_crash_report WHERE account_id='${f.a.account.accountId}'")
                assertEquals(429, post())
                clock.time = now.plusSeconds(86_400)
                assertEquals(204, post())
                assertEquals(1L, f.number("SELECT sum(reports) FROM debug_crash_daily_count"))
                f.accounts.revoke(f.a)
                assertEquals(401, post())
            } finally { server.stop(); server.destroy() }
        }
    }
}
