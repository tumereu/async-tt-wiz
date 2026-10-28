package org.asyncttwiz.android

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.net.UnknownHostException
import java.nio.file.Files
import java.security.MessageDigest
import okhttp3.OkHttpClient
import org.junit.Test
import org.junit.Assert.*

class AppUpdateTest {
    private val release = AppRelease(42, "1.2.3", "a".repeat(64), 100)

    @Test fun `only newer releases or a rules incompatibility block entry`() {
        assertFalse(appUpdateRequired(42, null))
        assertFalse(appUpdateRequired(42, release))
        assertFalse(appUpdateRequired(43, release))
        assertTrue(appUpdateRequired(41, release))
        assertTrue(appUpdateRequired(42, null, incompatibleRules = true))
        assertTrue(appUpdateRequired(42, release, incompatibleRules = true))
    }

    @Test fun `offline missing and rollback checks retain cached newer metadata`() {
        assertEquals(release, newestAppRelease(release, null))
        assertEquals(release, newestAppRelease(release, release.copy(versionCode = 41)))
        assertTrue(appUpdateRequired(41, newestAppRelease(release, null)))
        assertNull(newestAppRelease(null, null))
        assertEquals(release.copy(versionCode = 43), newestAppRelease(release, release.copy(versionCode = 43)))
    }

    @Test fun `metadata validates download limits and digest`() {
        val text = """{"versionCode":42,"versionName":"1.2.3","sha256":"${release.sha256}","size":100}"""
        assertEquals(release, AppRelease.parse(text))
        listOf(text.replace(":100", ":0"), text.replace(":42", ":0"),
            text.replace(release.sha256, "../apk"), text.replace(":100", ":536870913")).forEach {
            assertTrue(runCatching { AppRelease.parse(it) }.isFailure)
        }
    }

    @Test fun `APK requires exact length and SHA256 including cache hits`() {
        val file = File.createTempFile("atw-update", ".apk")
        try {
            val bytes = ByteArray(100_000) { (it % 251).toByte() }
            file.writeBytes(bytes)
            val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            val target = release.copy(sha256 = hash, size = bytes.size.toLong())
            assertTrue(verifyAppApk(file, target))
            assertFalse(verifyAppApk(file, target.copy(size = target.size + 1)))
            file.writeBytes(bytes.apply { this[1] = 0 })
            assertFalse(verifyAppApk(file, target))
            file.delete()
            assertFalse(verifyAppApk(file, target))
        } finally { file.delete() }
    }

    private val apk = ByteArray(300_000) { (it % 251).toByte() }
    private val target = AppRelease(42, "1.2.3", MessageDigest.getInstance("SHA-256").digest(apk)
        .joinToString("") { "%02x".format(it) }, apk.size.toLong())

    /** [respond] gets the request number and the requested range start, if any. */
    private fun withApkServer(respond: (Int, Long?, HttpExchange) -> Unit, block: (String) -> Unit) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var requests = 0
        server.createContext("/app/apk") { exchange ->
            val start = exchange.requestHeaders.getFirst("Range")?.removePrefix("bytes=")?.removeSuffix("-")?.toLong()
            try { respond(requests++, start, exchange) } catch (_: IOException) {} finally { exchange.close() }
        }
        server.start()
        try { block("http://127.0.0.1:${server.address.port}/app/apk") } finally { server.stop(0) }
    }

    private fun sendFrom(exchange: HttpExchange, start: Long, count: Int = apk.size - start.toInt()) {
        if (start > 0) exchange.responseHeaders.add("Content-Range", "bytes $start-${apk.size - 1}/${apk.size}")
        exchange.sendResponseHeaders(if (start > 0) 206 else 200, apk.size - start)
        exchange.responseBody.write(apk, start.toInt(), count)
    }

    @Test fun `a dropped download resumes from the saved part and verifies the whole APK`() {
        val directory = Files.createTempDirectory("atw-update").toFile()
        val ranges = mutableListOf<Long?>()
        val sleeps = mutableListOf<Long>()
        try {
            withApkServer({ request, start, exchange ->
                ranges += start
                // The first response is cut off halfway, like a mobile connection dropping.
                if (request == 0) sendFrom(exchange, 0, 120_000) else sendFrom(exchange, start ?: 0)
            }) { url ->
                val file = downloadAppApk(OkHttpClient(), url, target, directory, sleeps::add) {}
                assertArrayEquals(apk, file.readBytes())
                assertEquals(File(directory, "${target.sha256}.apk"), file)
            }
            assertEquals(listOf(null, 120_000L), ranges)
            assertEquals(listOf(2_000L), sleeps)
            assertEquals(listOf("${target.sha256}.apk"), directory.list()!!.toList())
        } finally { directory.deleteRecursively() }
    }

    @Test fun `a server without range support restarts, cuts keep resuming while they progress, stalls give up`() {
        val directory = Files.createTempDirectory("atw-update").toFile()
        try {
            File(directory, "${target.sha256}.part").writeBytes(apk.copyOf(50_000))
            File(directory, "download.part").writeBytes(byteArrayOf(1))
            withApkServer({ _, _, exchange -> sendFrom(exchange, 0) }) { url ->
                assertArrayEquals(apk, downloadAppApk(OkHttpClient(), url, target, directory, {}) {}.readBytes())
            }
            assertEquals(listOf("${target.sha256}.apk"), directory.list()!!.toList())
            File(directory, "${target.sha256}.apk").delete()
            var requests = 0
            // Each cut still makes progress, so the download keeps resuming until it completes.
            withApkServer({ _, start, exchange -> requests++; sendFrom(exchange, start ?: 0, minOf(40_000, apk.size - (start ?: 0).toInt())) }) { url ->
                assertArrayEquals(apk, downloadAppApk(OkHttpClient(), url, target, directory, {}) {}.readBytes())
            }
            assertEquals(8, requests)
            File(directory, "${target.sha256}.apk").delete()
            File(directory, "${target.sha256}.part").writeBytes(apk.copyOf(30_000))
            requests = 0
            // A server that never gets further gives up after three attempts in a row, keeping the part.
            withApkServer({ _, start, exchange -> requests++; sendFrom(exchange, start ?: 0, 0) }) { url ->
                val failure = runCatching { downloadAppApk(OkHttpClient(), url, target, directory, {}) {} }.exceptionOrNull()
                assertTrue(failure is IOException)
                assertEquals("The download was interrupted. Tap Update to continue where it stopped.", appUpdateProblem(failure!!).message)
            }
            assertEquals(3, requests)
            // Kept for the next tap of Update.
            assertEquals(30_000L, File(directory, "${target.sha256}.part").length())
        } finally { directory.deleteRecursively() }
    }

    @Test fun `failures explain themselves and only real download failures offer the browser`() {
        val offline = appUpdateProblem(UnknownHostException("pi5.example"))
        assertEquals("Can't reach the update server. Check your internet connection and try again.", offline.message)
        assertEquals("UnknownHostException: pi5.example", offline.detail)
        assertTrue(offline.browserFallback)
        assertEquals("Not enough free storage for the update. Free some space and try again.",
            appUpdateProblem(IOException("write failed: ENOSPC (No space left on device)")).message)
        assertFalse(appUpdateProblem(AppUpdateFailure("Update not available yet.", browserFallback = false)).browserFallback)
        assertEquals(AppUpdateProblem("Update failed.", "IllegalStateException: boom"), appUpdateProblem(IllegalStateException("boom")))
    }
}
