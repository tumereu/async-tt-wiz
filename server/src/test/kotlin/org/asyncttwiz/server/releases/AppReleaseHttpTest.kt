package org.asyncttwiz.server.releases

import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.net.InetAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.security.MessageDigest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory
import java.nio.file.Path

class AppReleaseHttpTest {
    @TempDir lateinit var directory: Path

    @Test fun `public metadata and streamed APK require a complete release but no authentication`() {
        withServer(AppReleaseHttp(directory)) { get ->
            assertEquals(404, get("/app/latest").statusCode())
            assertEquals(404, get("/app/apk").statusCode())
            val apk = ByteArray(100_000) { (it % 251).toByte() }
            val hash = MessageDigest.getInstance("SHA-256").digest(apk).joinToString("") { "%02x".format(it) }
            val metadata = """{"versionCode":42,"versionName":"1.2.3","sha256":"$hash","size":${apk.size}}"""
            Files.writeString(directory.resolve("latest.json"), metadata)
            assertEquals(404, get("/app/latest").statusCode())
            Files.write(directory.resolve("$hash.apk"), apk)
            val latest = get("/app/latest")
            assertEquals(200, latest.statusCode())
            assertEquals(metadata, latest.body().toString(Charsets.UTF_8))
            assertEquals("application/json", latest.headers().firstValue("Content-Type").orElseThrow())
            val download = get("/app/apk")
            assertEquals(200, download.statusCode())
            assertArrayEquals(apk, download.body())
            assertEquals(apk.size.toString(), download.headers().firstValue("Content-Length").orElseThrow())
            assertEquals("application/vnd.android.package-archive", download.headers().firstValue("Content-Type").orElseThrow())
            assertEquals("no-store", download.headers().firstValue("Cache-Control").orElseThrow())
            Files.write(directory.resolve("$hash.apk"), byteArrayOf(1))
            assertEquals(404, get("/app/apk").statusCode())
        }
    }

    @Test fun `interrupted downloads resume only the same APK`() {
        val apk = ByteArray(100_000) { (it % 251).toByte() }
        val hash = MessageDigest.getInstance("SHA-256").digest(apk).joinToString("") { "%02x".format(it) }
        Files.write(directory.resolve("$hash.apk"), apk)
        Files.writeString(directory.resolve("latest.json"), """{"versionCode":42,"versionName":"1.2.3","sha256":"$hash","size":${apk.size}}""")
        withServer(AppReleaseHttp(directory)) { get ->
            val full = get("/app/apk")
            assertEquals("\"$hash\"", full.headers().firstValue("ETag").orElseThrow())
            assertEquals("bytes", full.headers().firstValue("Accept-Ranges").orElseThrow())
            assertEquals("attachment; filename=\"async-tabletop-wizards-1.2.3.apk\"",
                full.headers().firstValue("Content-Disposition").orElseThrow())
            val resumed = get("/app/apk", "Range" to "bytes=60000-", "If-Range" to "\"$hash\"")
            assertEquals(206, resumed.statusCode())
            assertEquals("bytes 60000-99999/100000", resumed.headers().firstValue("Content-Range").orElseThrow())
            assertArrayEquals(apk.copyOfRange(60_000, apk.size), resumed.body())
            val replaced = get("/app/apk", "Range" to "bytes=60000-", "If-Range" to "\"${"0".repeat(64)}\"")
            assertEquals(200, replaced.statusCode())
            assertArrayEquals(apk, replaced.body())
            assertEquals(200, get("/app/apk", "Range" to "bytes=0-99").statusCode())
            assertEquals(416, get("/app/apk", "Range" to "bytes=100000-").statusCode())
        }
    }

    @Test fun `unset directory returns not found`() {
        withServer(AppReleaseHttp(null)) { get ->
            assertEquals(404, get("/app/latest").statusCode())
            assertEquals(404, get("/app/apk").statusCode())
        }
    }

    private fun interface Get { operator fun invoke(path: String, vararg headers: Pair<String, String>): HttpResponse<ByteArray> }

    private fun withServer(adapter: AppReleaseHttp, block: (Get) -> Unit) {
        val factory = TomcatServletWebServerFactory(0).apply { setAddress(InetAddress.getByName("127.0.0.1")) }
        val server = factory.getWebServer({ context ->
            context.addServlet("releases", object : HttpServlet() {
                override fun service(request: HttpServletRequest, response: HttpServletResponse) {
                    if (!adapter.handle(request, response)) response.status = 404
                }
            }).addMapping("/")
        })
        server.start()
        try {
            val client = HttpClient.newHttpClient()
            block(Get { path, headers -> client.send(HttpRequest.newBuilder(URI("http://127.0.0.1:${server.port}$path")).GET()
                .apply { headers.forEach { (name, value) -> header(name, value) } }.build(), HttpResponse.BodyHandlers.ofByteArray()) })
        } finally { server.stop(); server.destroy() }
    }
}
