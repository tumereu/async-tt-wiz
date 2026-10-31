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
import org.asyncttwiz.protocol.ProblemReport
import org.asyncttwiz.protocol.ProblemReportReceipt
import org.asyncttwiz.server.accounts.AccountRateLimits
import org.asyncttwiz.server.activity.ActivityClock
import org.asyncttwiz.server.activity.ActivityFixture
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory

class ProblemReportHttpTest {
    @Test fun `public endpoint stores bounded reports, attaches a valid session and keeps only the newest`() {
        ActivityFixture().use { f ->
            val now = Instant.now()
            val clock = ActivityClock(now)
            val adapter = ProblemReportHttp(f.runtime, clock, retained = 5)
            val factory = TomcatServletWebServerFactory(0).apply { setAddress(InetAddress.getByName("127.0.0.1")) }
            val server = factory.getWebServer({ context ->
                context.addServlet("problem-reports", object : HttpServlet() {
                    override fun service(request: HttpServletRequest, response: HttpServletResponse) {
                        if (!adapter.handle(request, response)) response.status = 404
                    }
                }).addMapping("/")
            })
            server.start()
            try {
                val client = HttpClient.newHttpClient()
                val report = ProblemReport(now.minusSeconds(5).toString(), "0.1.3", 4, "release", "Google Pixel 8",
                    "Android 15 (API 35)", "Update stops at 40%", "The download was interrupted.", "line 1\nline 2", "logcat")
                fun post(body: String = AccountJson.format.encodeToString(report), token: String? = null,
                    method: String = "POST"): HttpResponse<String> {
                    val request = HttpRequest.newBuilder(URI("http://127.0.0.1:${server.port}/app/problem-reports"))
                        .header("Content-Type", "application/json; charset=UTF-8")
                        .method(method, HttpRequest.BodyPublishers.ofString(body))
                    if (token != null) request.header("Authorization", "Bearer $token")
                    return client.send(request.build(), HttpResponse.BodyHandlers.ofString()).also {
                        assertEquals("no-store", it.headers().firstValue("Cache-Control").orElseThrow())
                    }
                }

                val first = post()
                assertEquals(201, first.statusCode())
                val code = AccountJson.format.decodeFromString<ProblemReportReceipt>(first.body()).code
                assertTrue(code.matches(Regex("[A-HJ-NP-Z2-9]{6}")), code)
                assertEquals(1L, f.number("""SELECT count(*) FROM problem_report WHERE code='$code' AND account_id IS NULL
                    AND app_version='0.1.3' AND version_code=4 AND build_type='release' AND device='Google Pixel 8'
                    AND description='Update stops at 40%' AND log='line 1' || chr(10) || 'line 2' AND logcat='logcat'"""))

                assertEquals(201, post(token = f.tokenA).statusCode())
                assertEquals(1L, f.number("SELECT count(*) FROM problem_report WHERE account_id='${f.a.account.accountId}'"))
                // An invalid session never blocks a report.
                assertEquals(201, post(token = "x".repeat(43)).statusCode())

                assertEquals(405, post(method = "GET").statusCode())
                assertEquals(413, post(AccountJson.format.encodeToString(report.copy(log = "x".repeat(ProblemReport.MAX_BYTES)))).statusCode())
                assertEquals(400, post(AccountJson.format.encodeToString(report.copy(description = "x".repeat(2_001)))).statusCode())
                assertEquals(400, post(AccountJson.format.encodeToString(report.copy(reportedAt = "yesterday"))).statusCode())
                assertEquals(400, post("{}").statusCode())

                repeat(4) { assertEquals(201, post(AccountJson.format.encodeToString(report.copy(description = "late $it"))).statusCode()) }
                assertEquals(5L, f.number("SELECT count(*) FROM problem_report"))
                assertEquals(0L, f.number("SELECT count(*) FROM problem_report WHERE code='$code'"))
                assertEquals(4L, f.number("SELECT count(*) FROM problem_report WHERE description LIKE 'late %'"))
            } finally { server.stop() }
        }
    }

    // LOB-089
    @Test fun `each account keeps only its newest problem reports and reports before sign-in only count toward the total`() {
        ActivityFixture().use { f ->
            val adapter = ProblemReportHttp(f.runtime, retained = 8, retainedPerAccount = 2)
            val server = TomcatServletWebServerFactory(0).apply { setAddress(InetAddress.getByName("127.0.0.1")) }.getWebServer({ context ->
                context.addServlet("problem-reports", object : HttpServlet() {
                    override fun service(request: HttpServletRequest, response: HttpServletResponse) {
                        if (!adapter.handle(request, response)) response.status = 404
                    }
                }).addMapping("/")
            })
            server.start()
            try {
                val client = HttpClient.newHttpClient()
                fun post(description: String, token: String?) {
                    val body = AccountJson.format.encodeToString(ProblemReport(Instant.now().toString(), "0.1.3", 4, "release", "d", "a", description))
                    val request = HttpRequest.newBuilder(URI("http://127.0.0.1:${server.port}/app/problem-reports"))
                        .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body))
                    if (token != null) request.header("Authorization", "Bearer $token")
                    assertEquals(201, client.send(request.build(), HttpResponse.BodyHandlers.ofString()).statusCode())
                }
                repeat(4) { post("a$it", f.tokenA) }
                repeat(3) { post("anonymous$it", null) }
                post("b0", f.tokenB)
                fun descriptions(where: String) = f.number("SELECT count(*) FROM problem_report WHERE $where")
                assertEquals(2L, descriptions("account_id='${f.a.account.accountId}' AND description IN ('a2', 'a3')"))
                assertEquals(2L, descriptions("account_id='${f.a.account.accountId}'"))
                assertEquals(3L, descriptions("account_id IS NULL"))
                assertEquals(1L, descriptions("account_id='${f.b.account.accountId}'"))
                repeat(5) { post("anonymous-late$it", null) }
                assertEquals(8L, descriptions("true"))
                assertEquals(0L, descriptions("description IN ('a2', 'anonymous0')"))
            } finally { server.stop() }
        }
    }

    @Test fun `a flood from one address is rate limited`() {
        ActivityFixture().use { f ->
            val adapter = ProblemReportHttp(f.runtime, limits = AccountRateLimits { 0 })
            val factory = TomcatServletWebServerFactory(0).apply { setAddress(InetAddress.getByName("127.0.0.1")) }
            val server = factory.getWebServer({ context ->
                context.addServlet("problem-reports", object : HttpServlet() {
                    override fun service(request: HttpServletRequest, response: HttpServletResponse) {
                        if (!adapter.handle(request, response)) response.status = 404
                    }
                }).addMapping("/")
            })
            server.start()
            try {
                val client = HttpClient.newHttpClient()
                val body = AccountJson.format.encodeToString(ProblemReport(Instant.now().toString(), "0.1.3", 4, "release", "d", "a"))
                val statuses = List(21) {
                    client.send(HttpRequest.newBuilder(URI("http://127.0.0.1:${server.port}/app/problem-reports"))
                        .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                        HttpResponse.BodyHandlers.ofString()).statusCode()
                }
                assertEquals(List(20) { 201 } + 429, statuses)
            } finally { server.stop() }
        }
    }
}
