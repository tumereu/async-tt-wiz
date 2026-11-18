@file:OptIn(ExperimentalWasmJsInterop::class)

package org.asyncttwiz.android

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.asyncttwiz.protocol.GameWire
import org.asyncttwiz.storage.GameSocketEvents
import org.asyncttwiz.storage.GameSocketHandle
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The game socket in the browser ([BrowserGameTransport]) over a stand-in WebSocket and fetch: what reaches the shared
 * session (TransportGameConnection, whose reconnecting and recovery GameSessionTest covers on both platforms) from the
 * browser's socket, and how its reads ask the server.
 */
class BrowserGameTransportTest {
    private class Events : GameSocketEvents {
        val seen = mutableListOf<String>()
        override fun opened(socket: GameSocketHandle) { seen += "opened" }
        override fun text(socket: GameSocketHandle, text: String) { seen += "text:$text" }
        override fun binary(socket: GameSocketHandle) { seen += "binary" }
        override fun closing(socket: GameSocketHandle, code: Int, reason: String) { seen += "closing:$code:$reason" }
        override fun failed(socket: GameSocketHandle, unauthorized: Boolean, cause: String) { seen += "failed:$unauthorized:$cause" }
    }

    private val transport = BrowserGameTransport()
    private val events = Events()

    @BeforeTest fun fakes() { installTestBuild(); fakeWebSocket() }
    @AfterTest fun reals() { transport.close(); realWebSocket(); realFetch() }

    private fun sockets(): JsonArray = Json.parseToJsonElement(fakeSocketsJson()).jsonArray
    private fun socket(index: Int = 0): JsonObject = sockets()[index].jsonObject

    // WEB-007, SYS-014: the browser can't put the session token in a header on a WebSocket, so it goes as a subprotocol
    // beside the game protocol; the socket is the page's address's ws/wss
    @Test fun theTokenGoesAsASubprotocolOnTheSocketsAddress() {
        val handle = transport.open("https://atw.example/v1/games/socket", "token-1", events)
        val opened = socket()
        assertEquals("wss://atw.example/v1/games/socket", opened["url"]!!.jsonPrimitive.content)
        assertEquals(listOf(GameWire.SOCKET_PROTOCOL, GameWire.SOCKET_TOKEN_PREFIX + "token-1"),
            opened["protocols"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals("arraybuffer", opened["binaryType"]!!.jsonPrimitive.content)
        handle.cancel()
        transport.open("http://127.0.0.1:8080/v1/games/socket", "token-2", events).cancel()
        assertEquals("ws://127.0.0.1:8080/v1/games/socket", socket(1)["url"]!!.jsonPrimitive.content)
    }

    // WEB-007: the server's messages reach the session in order; a binary one is only noticed
    @Test fun messagesReachTheSessionInOrder() {
        val handle = transport.open("https://atw.example/s", "t", events)
        fakeSocketOpen(0)
        fakeSocketText(0, "{\"type\":\"head\"}")
        fakeSocketBinary(0)
        fakeSocketText(0, "{\"type\":\"revision\"}")
        assertEquals(listOf("opened", "text:{\"type\":\"head\"}", "binary", "text:{\"type\":\"revision\"}"), events.seen)
        handle.cancel()
    }

    // SYS-014, SYS-016: the server's own close is a close; a dropped connection (1006, or any close that wasn't clean) is a
    // failure, which the session answers by reconnecting
    @Test fun aCleanCloseIsTheServersAndAnythingElseIsAFailure() {
        transport.open("https://atw.example/s", "t", events)
        fakeSocketOpen(0)
        fakeSocketClosed(0, 4001, "replaced", true)
        transport.open("https://atw.example/s", "t", events)
        fakeSocketOpen(1)
        fakeSocketClosed(1, 1006, "", true)
        transport.open("https://atw.example/s", "t", events)
        fakeSocketClosed(2, 1000, "", false)
        assertEquals(listOf("opened", "closing:4001:replaced", "opened", "failed:false:socketFailure:WebSocket:close=1006",
            "failed:false:socketFailure:WebSocket:close=1000"), events.seen)
    }

    // SYS-014: a message goes only on an open socket; one the socket can't take, or after it ended, is refused (the session
    // keeps the command and sends it again after the reconnect), never lost silently
    @Test fun messagesGoOnlyOnAnOpenSocket() {
        val handle = transport.open("https://atw.example/s", "t", events)
        assertFalse(handle.send("early"), "connecting: refused")
        fakeSocketOpen(0)
        assertTrue(handle.send("one"))
        fakeSocketState(0, 2)
        assertFalse(handle.send("closing"), "closing: refused")
        fakeSocketState(0, 1)
        assertTrue(handle.send("two"))
        fakeSocketClosed(0, 1006, "", false)
        assertFalse(handle.send("after"), "ended: refused")
        assertEquals(listOf("one", "two"), socket()["sent"]!!.jsonArray.map { it.jsonPrimitive.content })
    }

    // WEB-060: a socket the session drops (a newer one replaces it) reports nothing more; answering the server's close uses its
    // code only where a page may send it
    @Test fun aDroppedSocketIsSilentAndClosesAnswerWithAllowedCodes() {
        val dropped = transport.open("https://atw.example/s", "t", events)
        fakeSocketOpen(0)
        dropped.cancel()
        assertFalse(fakeSocketListened(0))
        assertFalse(dropped.send("after"))
        val answered = transport.open("https://atw.example/s", "t", events)
        answered.close(4000)
        val plain = transport.open("https://atw.example/s", "t", events)
        plain.close(1001)
        val normal = transport.open("https://atw.example/s", "t", events)
        normal.close(1000)
        assertEquals(listOf("plain", "4000", "plain", "1000"), sockets().map { it.jsonObject["closedWith"]!!.jsonPrimitive.content })
        assertEquals(listOf("opened"), events.seen)
    }

    // WEB-007: the session's reads are fetches with the token in the Authorization header, no cookies, no cache and no
    // redirects; a PUT carries its JSON
    @Test fun readsAskTheServerWithTheToken() = runTest {
        fakeFetch(200, "{\"ok\":true}")
        val got = CompletableDeferred<Result<String>>()
        transport.read("https://atw.example/v1/games/g/head", "token-3", null, 1000, { true }) { got.complete(it) }
        assertEquals("{\"ok\":true}", got.await().getOrThrow())
        val put = CompletableDeferred<Result<String>>()
        transport.read("https://atw.example/v1/games/g/seen", "token-3", "{\"seen\":1}", 1000, { true }) { put.complete(it) }
        put.await().getOrThrow()
        val requests = Json.parseToJsonElement(fakeRequestsJson()).jsonArray.map { it.jsonObject }
        assertEquals(listOf("GET", "PUT"), requests.map { it["method"]!!.jsonPrimitive.content })
        for (request in requests) {
            assertEquals("Bearer token-3", request["headers"]!!.jsonObject["authorization"]!!.jsonPrimitive.content)
            assertEquals("manual", request["redirect"]!!.jsonPrimitive.content)
            assertEquals("omit", request["credentials"]!!.jsonPrimitive.content)
            assertEquals("no-store", request["cache"]!!.jsonPrimitive.content)
        }
        assertEquals("{\"seen\":1}", requests[1]["body"]!!.jsonPrimitive.content)
        assertEquals("application/json", requests[1]["headers"]!!.jsonObject["content-type"]!!.jsonPrimitive.content)
    }

    // SYS-016: a refused read, an answer too large or a read no longer wanted fails (the session retries or drops it)
    @Test fun refusedLargeOrStaleReadsFail() = runTest {
        fakeFetch(503, "busy")
        val refused = CompletableDeferred<Result<String>>()
        transport.read("https://atw.example/r", "t", null, 1000, { true }) { refused.complete(it) }
        assertTrue(refused.await().isFailure)
        fakeFetch(200, "x".repeat(20))
        val large = CompletableDeferred<Result<String>>()
        transport.read("https://atw.example/r", "t", null, 10, { true }) { large.complete(it) }
        assertTrue(large.await().isFailure)
        val stale = CompletableDeferred<Result<String>>()
        transport.read("https://atw.example/r", "t", null, 1000, { false }) { stale.complete(it) }
        assertTrue(stale.await().isFailure)
        assertEquals(1, Json.parseToJsonElement(fakeRequestsJson()).jsonArray.size, "the stale read asked nothing")
    }
}
