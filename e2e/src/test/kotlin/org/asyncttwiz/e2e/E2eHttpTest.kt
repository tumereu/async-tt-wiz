package org.asyncttwiz.e2e

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import org.asyncttwiz.android.PodApi
import org.asyncttwiz.android.PodFailure
import org.asyncttwiz.protocol.DeckJson
import org.asyncttwiz.protocol.DraftSettings
import org.asyncttwiz.protocol.PodPreset
import org.asyncttwiz.protocol.SavePodPreset
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** [E2eHttp]'s client against a stand-in server on 127.0.0.1 (no stack needed). */
class E2eHttpTest {
    private val token = "A".repeat(43)
    // A write (a POST with a body): the JVM's HttpURLConnection never sends one again itself, so each attempt is one connection
    private val save = SavePodPreset("Preset", DraftSettings())
    private val saved = DeckJson.format.encodeToString(PodPreset("p1", "Preset", DraftSettings(), updatedAt = "2026-11-27T00:00:00Z"))

    /** A server that reads each request and then answers it with [answer] (null: closes the connection, no answer). */
    private fun server(answer: (Int) -> String?): Pair<String, AtomicInteger> {
        val socket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val requests = AtomicInteger()
        thread(isDaemon = true) {
            while (true) socket.accept().use { connection ->
                val input = connection.getInputStream()
                val head = StringBuilder()
                while (!head.endsWith("\r\n\r\n")) head.append(input.read().toChar())
                val length = head.lines().firstOrNull { it.startsWith("Content-Length:", true) }?.substringAfter(':')?.trim()?.toInt() ?: 0
                repeat(length) { input.read() }
                val body = answer(requests.getAndIncrement()) ?: return@use
                val status = if ("\"code\"" in body) "503 Service Unavailable" else "201 Created"
                connection.getOutputStream().write(("HTTP/1.1 $status\r\nContent-Type: application/json\r\n" +
                    "Content-Length: ${body.length}\r\nConnection: close\r\n\r\n$body").toByteArray())
            }
        }
        return "http://127.0.0.1:${socket.localPort}" to requests
    }

    // A connection that ends before any answer (what a port broken by WSL's loopback gives) is sent again, on a new one
    @Test fun aConnectionClosedBeforeAnAnswerIsSentAgain() {
        val (origin, requests) = server { if (it < 2) null else saved }
        val preset = runBlocking { PodApi(origin, E2eHttp.client()).use { it.savePreset(token, save) } }
        assertEquals("Preset", preset.name)
        assertEquals(3, requests.get())
    }

    // An answer is the server's, a refusal or an error included: never sent again
    @Test fun anAnswerIsNotSentAgain() {
        val (origin, requests) = server { """{"code":"unavailable"}""" }
        val failure = assertFailsWith<PodFailure> { runBlocking { PodApi(origin, E2eHttp.client()).use { it.savePreset(token, save) } } }
        assertEquals("unavailable", failure.code)
        assertEquals(1, requests.get())
    }

    // A server that never answers fails after the attempts, the cause kept
    @Test fun theAttemptsEnd() {
        val (origin, requests) = server { null }
        val failure = assertFailsWith<PodFailure> { runBlocking { PodApi(origin, E2eHttp.client()).use { it.savePreset(token, save) } } }
        assertEquals("network", failure.code)
        assertTrue(failure.cause is java.net.SocketException, "the cause: ${failure.cause}")
        assertEquals(5, requests.get())
    }
}
