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

    @Test fun `unset directory returns not found`() {
        withServer(AppReleaseHttp(null)) { get ->
            assertEquals(404, get("/app/latest").statusCode())
            assertEquals(404, get("/app/apk").statusCode())
        }
    }

    private fun withServer(adapter: AppReleaseHttp, block: ((String) -> HttpResponse<ByteArray>) -> Unit) {
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
            block { path -> client.send(HttpRequest.newBuilder(URI("http://127.0.0.1:${server.port}$path")).GET().build(), HttpResponse.BodyHandlers.ofByteArray()) }
        } finally { server.stop(); server.destroy() }
    }
}
