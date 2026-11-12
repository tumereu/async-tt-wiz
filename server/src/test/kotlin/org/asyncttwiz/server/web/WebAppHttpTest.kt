package org.asyncttwiz.server.web

import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.net.InetAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory

/** The web client served beside the API (docs/specs/web-client.md §2.1). */
class WebAppHttpTest {
    @TempDir lateinit var directory: Path
    private val build = "0123456789abcdef"

    private fun publish() {
        Files.writeString(directory.resolve("index.html"), "<html>ATW $build</html>")
        Files.writeString(directory.resolve("build"), "$build\n")
        Files.createDirectories(directory.resolve("$build/fonts"))
        Files.write(directory.resolve("$build/atw.wasm"), byteArrayOf(0, 97, 115, 109))
        Files.writeString(directory.resolve("$build/atw.js"), "console.log('atw')")
        Files.writeString(directory.resolve("$build/fonts/codes.txt"), "codes")
        Files.writeString(directory.resolve("secret.txt"), "not served")
    }

    // WEB-001
    @Test fun `every app address answers with the page, never cached, and other paths stay unknown`() {
        publish()
        withServer(WebAppHttp(directory)) { get ->
            for (address in listOf("/", "/games", "/games/3f2a9c1e-0b5d-4a8e-9c7f-1d2e3f4a5b6c", "/pods", "/pods/abc/draft",
                    "/pods/abc", "/decks", "/decks/import", "/decks/abc", "/decks/abc/edit", "/cubes/new", "/cubes/abc",
                    "/contacts", "/settings", "/stats", "/games/")) {
                val page = get(address, "GET")
                assertEquals(200, page.statusCode(), address)
                assertEquals("<html>ATW $build</html>", page.body().decodeToString(), address)
                assertEquals("no-store", page.headers().firstValue("Cache-Control").orElseThrow(), address)
                assertTrue(page.headers().firstValue("Content-Type").orElseThrow().startsWith("text/html"), address)
            }
            for (unknown in listOf("/v1/unknown", "/games/a/b", "/pods/abc/table", "/decks/abc/edit/more", "/cubes", "/index.html",
                    "/secret.txt", "/web/$build/../secret.txt", "/games/a%2Fb"))
                assertTrue(get(unknown, "GET").statusCode() in setOf(400, 404), unknown) // Tomcat refuses encoded slashes and dots itself
            assertEquals(404, get("/", "POST").statusCode())
        }
    }

    // WEB-005
    @Test fun `the build's files are served by their content's name and cached for good, nothing outside its folder`() {
        publish()
        withServer(WebAppHttp(directory)) { get ->
            val wasm = get("/web/$build/atw.wasm", "GET")
            assertEquals(200, wasm.statusCode())
            assertArrayEquals(byteArrayOf(0, 97, 115, 109), wasm.body())
            assertEquals("application/wasm", wasm.headers().firstValue("Content-Type").orElseThrow())
            assertEquals("public, max-age=31536000, immutable", wasm.headers().firstValue("Cache-Control").orElseThrow())
            assertTrue(get("/web/$build/atw.js", "GET").headers().firstValue("Content-Type").orElseThrow().startsWith("text/javascript"))
            assertEquals("codes", get("/web/$build/fonts/codes.txt", "GET").body().decodeToString())
            val head = get("/web/$build/atw.js", "HEAD")
            assertEquals(200, head.statusCode())
            assertEquals(0, head.body().size)
            for (missing in listOf("/web/$build/missing.js", "/web/fedcba9876543210/atw.js", "/web/$build", "/web/secret.txt",
                    "/web/$build/..", "/web/$build/%2e%2e/secret.txt", "/web/notabuild/atw.js")) {
                val response = get(missing, "GET")
                assertTrue(response.statusCode() in setOf(400, 404), missing)
            }
        }
    }

    // WEB-006
    @Test fun `the current build is told uncached, for open tabs to compare theirs with`() {
        publish()
        withServer(WebAppHttp(directory)) { get ->
            val current = get("/web/build", "GET")
            assertEquals(200, current.statusCode())
            assertEquals(build, current.body().decodeToString())
            assertEquals("no-store", current.headers().firstValue("Cache-Control").orElseThrow())
        }
    }

    @Test fun `without a build the server serves no web app`() {
        withServer(WebAppHttp(directory)) { get ->
            assertEquals(404, get("/", "GET").statusCode())
            assertEquals(404, get("/web/build", "GET").statusCode())
        }
        withServer(WebAppHttp(null)) { get -> assertEquals(404, get("/games", "GET").statusCode()) }
    }

    private fun interface Get { operator fun invoke(path: String, method: String): HttpResponse<ByteArray> }

    private fun withServer(adapter: WebAppHttp, block: (Get) -> Unit) {
        val factory = TomcatServletWebServerFactory(0).apply { setAddress(InetAddress.getByName("127.0.0.1")) }
        val server = factory.getWebServer({ context ->
            context.addServlet("web", object : HttpServlet() {
                override fun service(request: HttpServletRequest, response: HttpServletResponse) {
                    if (!adapter.handle(request, response)) response.status = 404
                }
            }).addMapping("/")
        })
        server.start()
        try {
            val client = HttpClient.newHttpClient()
            block(Get { path, method -> client.send(HttpRequest.newBuilder(URI("http://127.0.0.1:${server.port}$path"))
                .method(method, HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofByteArray()) })
        } finally { server.stop(); server.destroy() }
    }
}
