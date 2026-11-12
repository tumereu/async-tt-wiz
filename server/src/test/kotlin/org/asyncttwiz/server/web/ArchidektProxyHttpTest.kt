package org.asyncttwiz.server.web

import com.sun.net.httpserver.HttpServer
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory

/** The web client's Archidekt deck import through the server (LOB-061, docs/specs/web-client.md §2.3). */
class ArchidektProxyHttpTest {
    // LOB-061 (web)
    @Test fun `a deck is read by its number from Archidekt's API and passed on, nothing else reachable`() {
        withUpstream { upstream, asked ->
            withServer(ArchidektProxyHttp(upstream)) { get ->
                val deck = get("/v1/import/archidekt/123456", "GET")
                assertEquals(200, deck.statusCode())
                assertEquals("""{"name":"Deck 123456","cards":[]}""", deck.body())
                assertEquals("no-store", deck.headers().firstValue("Cache-Control").orElseThrow())
                assertEquals(listOf("/api/decks/123456/"), asked.toList())
                assertEquals(404, get("/v1/import/archidekt/404", "GET").statusCode())
                assertEquals(502, get("/v1/import/archidekt/500", "GET").statusCode())
                assertEquals(502, get("/v1/import/archidekt/302", "GET").statusCode())
                for (other in listOf("/v1/import/archidekt/0", "/v1/import/archidekt/abc", "/v1/import/archidekt/1/2",
                        "/v1/import/archidekt/", "/v1/import/archidekt/1234567890123456789"))
                    assertEquals(404, get(other, "GET").statusCode(), other)
                assertEquals(405, get("/v1/import/archidekt/123", "POST").statusCode())
                assertEquals(listOf("/api/decks/123456/", "/api/decks/404/", "/api/decks/500/", "/api/decks/302/"), asked.toList())
            }
        }
    }

    @Test fun `imports are limited per minute, so the server can't flood Archidekt`() {
        var now = Instant.parse("2026-11-12T10:00:00Z")
        val clock = object : Clock() {
            override fun getZone() = ZoneOffset.UTC
            override fun withZone(zone: java.time.ZoneId) = this
            override fun instant() = now
        }
        withUpstream { upstream, _ ->
            withServer(ArchidektProxyHttp(upstream, clock)) { get ->
                repeat(ArchidektProxyHttp.PER_MINUTE) { assertEquals(200, get("/v1/import/archidekt/${it + 1}", "GET").statusCode()) }
                assertEquals(429, get("/v1/import/archidekt/99", "GET").statusCode())
                now = now.plusSeconds(60)
                assertEquals(200, get("/v1/import/archidekt/99", "GET").statusCode())
            }
        }
    }

    private fun withUpstream(block: (String, List<String>) -> Unit) {
        val asked = CopyOnWriteArrayList<String>()
        val server = HttpServer.create(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0)
        server.createContext("/") { exchange ->
            val path = exchange.requestURI.path
            asked += path
            val id = path.removePrefix("/api/decks/").removeSuffix("/")
            val (status, body) = when (id) {
                "404" -> 404 to "{}"
                "500" -> 500 to "{}"
                "302" -> { exchange.responseHeaders.add("Location", "http://127.0.0.1:1/elsewhere"); 302 to "" }
                else -> 200 to """{"name":"Deck $id","cards":[]}"""
            }
            val bytes = body.toByteArray()
            exchange.sendResponseHeaders(status, if (bytes.isEmpty()) -1 else bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try { block("http://127.0.0.1:${server.address.port}", asked) } finally { server.stop(0) }
    }

    private fun interface Get { operator fun invoke(path: String, method: String): HttpResponse<String> }

    private fun withServer(adapter: ArchidektProxyHttp, block: (Get) -> Unit) {
        val factory = TomcatServletWebServerFactory(0).apply { setAddress(InetAddress.getByName("127.0.0.1")) }
        val server = factory.getWebServer({ context ->
            context.addServlet("import", object : HttpServlet() {
                override fun service(request: HttpServletRequest, response: HttpServletResponse) {
                    if (!adapter.handle(request, response)) response.status = 404
                }
            }).addMapping("/")
        })
        server.start()
        try {
            val client = HttpClient.newHttpClient()
            block(Get { path, method -> client.send(HttpRequest.newBuilder(URI("http://127.0.0.1:${server.port}$path"))
                .method(method, HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString()) })
        } finally { server.stop(); server.destroy() }
    }
}
