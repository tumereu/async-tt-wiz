package org.asyncttwiz.server.accounts

import java.net.InetAddress
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.Base64
import java.util.Properties
import java.util.concurrent.TimeUnit
import jakarta.mail.Session
import jakarta.mail.internet.MimeMessage
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*

/** The existing real synthetic SMTP sink, not an injected delivery implementation. Never print messages. */
class SyntheticMailbox(private val directory: Path) : AutoCloseable {
    val port = freePort()
    private val adminPort = freePort()
    private val token = java.util.HexFormat.of().formatHex(ByteArray(32).also(java.security.SecureRandom()::nextBytes))
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()
    private val process: Process
    init {
        Files.createDirectories(directory)
        Files.writeString(directory.resolve("admin-token"), token)
        val root = Path.of(System.getProperty("asyncttwiz.root"))
        process = ProcessBuilder("python3", root.resolve("tests/support/synthetic-adapter.py").toString(),
            "--fixtures", root.resolve("tests/fixtures/catalogs").toString(), "--origin", "http://127.0.0.1:18080",
            "--state-dir", directory.resolve("state").toString(), "--instance", "reset-tests", "--token-file",
            directory.resolve("admin-token").toString(), "--http-port", freePort().toString(),
            "--admin-port", adminPort.toString(), "--smtp-port", port.toString())
            .redirectErrorStream(true).redirectOutput(directory.resolve("adapter.log").toFile()).start()
        val until = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
        while (true) {
            try { if (admin("/health/live").statusCode() == 200) break } catch (_: Exception) { }
            if (!process.isAlive || System.nanoTime() > until) {
                process.destroy(); process.waitFor(10, TimeUnit.SECONDS)
                fail<Unit>("Synthetic mailbox failed to start: " + Files.readString(directory.resolve("adapter.log")))
            }
            Thread.sleep(20)
        }
    }
    fun settings(tls: String = "local-plaintext") = ResetMailSettings("127.0.0.1", port, tls, null, null,
        "reset@synthetic.invalid", URI("https://reset.synthetic.invalid"), ByteArray(32) { (it + 1).toByte() })
    fun admin(path: String, body: String? = null): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(URI("http://127.0.0.1:$adminPort$path"))
            .timeout(Duration.ofSeconds(3)).header("Authorization", "Bearer $token")
        if (body != null) builder.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body))
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }
    fun fault(value: String) {
        assertEquals(200, admin("/admin/control", """{"instance":"reset-tests","mailFault":"$value"}""").statusCode())
    }
    fun links(): List<String> = Json.parseToJsonElement(admin("/admin/messages").body()).jsonArray.map { row ->
        val bytes = Base64.getDecoder().decode(row.jsonObject["bodyBase64"]!!.jsonPrimitive.content)
        val message = MimeMessage(Session.getInstance(Properties()), bytes.inputStream())
        Regex("https://reset.synthetic.invalid/reset-password\\?token=[A-Za-z0-9_-]{43}")
            .find(message.content as String)?.value ?: fail("Missing configured reset link")
    }
    fun awaitLinks(count: Int): List<String> {
        val until = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
        while (System.nanoTime() < until) {
            val links = links()
            if (links.size == count) return links
            Thread.sleep(20)
        }
        return fail("Expected synthetic delivery count not reached")
    }
    override fun close() {
        process.destroy()
        if (!process.waitFor(10, TimeUnit.SECONDS)) process.destroyForcibly().waitFor()
        assertEquals(0, process.exitValue())
        println("CLEANUP owned synthetic SMTP process terminated; messages/tokens not logged")
    }
    companion object {
        fun freePort() = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1")).use { it.localPort }
        /** Synthetic file inputs for existing installed-service tests, never production credentials. */
        fun environment(directory: Path): Map<String, String> {
            val key = directory.resolve("reset-mail-key")
            Files.writeString(key, Base64.getEncoder().encodeToString(ByteArray(32) { 11 }))
            return mapOf("SMTP_HOST" to "127.0.0.1", "SMTP_PORT" to "9", "SMTP_TLS" to "local-plaintext",
                "SMTP_FROM" to "reset@synthetic.invalid", "RESET_BASE_URL" to "https://reset.synthetic.invalid",
                "RESET_MAIL_KEY_FILE" to key.toString())
        }
    }
}
