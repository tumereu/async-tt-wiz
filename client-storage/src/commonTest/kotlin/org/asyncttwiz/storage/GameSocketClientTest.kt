package org.asyncttwiz.storage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.encodeToString
import org.asyncttwiz.protocol.ChatPage
import org.asyncttwiz.protocol.ChatReadState
import org.asyncttwiz.protocol.ChatSync
import org.asyncttwiz.protocol.EngineHistoryPage
import org.asyncttwiz.protocol.GamePing
import org.asyncttwiz.protocol.GamePong
import org.asyncttwiz.protocol.GameServerMessage
import org.asyncttwiz.protocol.GameWire
import org.asyncttwiz.protocol.ReadChat

// The game socket's shared logic (web client step 3b) over a fake socket: what Android's OkHttp and the browser's
// WebSocket both run under.
class GameSocketClientTest {
    private val token = "a".repeat(43)
    private val game = "11111111-1111-4111-8111-111111111111"
    private val event = "22222222-2222-4222-8222-222222222222"

    private class FakeSocket(val url: String, val token: String, val events: GameSocketEvents) : GameSocketHandle {
        val sent = mutableListOf<String>()
        var cancelled = false
        var closedWith: Int? = null
        override fun send(text: String): Boolean { if (cancelled) return false; sent += text; return true }
        override fun cancel() { cancelled = true }
        override fun close(code: Int) { closedWith = code }
    }

    private class Read(val url: String, val json: String?, val maxBytes: Int, val current: () -> Boolean,
        val complete: (Result<String>) -> Unit)

    private class FakeTransport : GameSocketTransport {
        val sockets = mutableListOf<FakeSocket>()
        val reads = ArrayDeque<Read>()
        var closed = false
        override fun open(url: String, token: String, events: GameSocketEvents) = FakeSocket(url, token, events).also { sockets += it }
        override fun read(url: String, token: String, json: String?, maxBytes: Int, current: () -> Boolean,
            complete: (Result<String>) -> Unit) { reads.addLast(Read(url, json, maxBytes, current, complete)) }
        override fun close() { closed = true }
        /** Answers the oldest read as a transport does: refused without a request when no longer current. */
        fun answer(body: String) {
            val read = reads.removeFirst()
            read.complete(if (read.current()) Result.success(body) else Result.failure(IllegalStateException("Check failed.")))
        }
    }

    private class Recorder : GameSocketListener {
        val calls = mutableListOf<String>()
        val messages = mutableListOf<GameServerMessage>()
        override fun opened() { calls += "opened" }
        override fun message(message: GameServerMessage) { calls += "message"; messages += message }
        override fun disconnected(unauthorized: Boolean, cause: String) { calls += "disconnected:$unauthorized:$cause" }
    }

    private val warnings = mutableListOf<String>()
    private val transport = FakeTransport()
    private val client = GameSocketClient("https://atw.example", transport, { it.length == 43 }) { message, _ -> warnings += message }

    private fun pong(id: String) = GameWire.json.encodeToString<GameServerMessage>(GamePong(id))

    @Test
    fun socketMessagesArriveInOrderAndSendsGoToTheOpenSocket() {
        val listener = Recorder()
        client.connect(token, listener)
        val socket = transport.sockets.single()
        assertEquals("https://atw.example/v1/socket", socket.url)
        assertEquals(token, socket.token)
        socket.events.opened(socket)
        socket.events.text(socket, pong("1"))
        socket.events.text(socket, pong("2"))
        assertEquals(listOf("opened", "message", "message"), listener.calls)
        assertEquals(listOf<GameServerMessage>(GamePong("1"), GamePong("2")), listener.messages)
        assertTrue(client.send(GamePing("p")))
        assertEquals(listOf(GameWire.json.encodeToString<org.asyncttwiz.protocol.GameClientMessage>(GamePing("p"))), socket.sent)
    }

    @Test
    fun reconnectingDropsTheOldSocketAndSendsOnlyOnTheNewOne() {
        val first = Recorder()
        client.connect(token, first)
        val old = transport.sockets.single()
        val second = Recorder()
        client.connect("b".repeat(43), second)
        assertTrue(old.cancelled)
        val current = transport.sockets.last()
        assertEquals(2, transport.sockets.size)
        assertEquals("b".repeat(43), current.token)
        client.send(GamePing("p"))
        assertEquals(emptyList(), old.sent)
        assertEquals(1, current.sent.size)
        // What the old socket still reports reaches only its own listener (the session fences it by its generation).
        old.events.text(old, pong("late"))
        assertEquals(listOf("message"), first.calls)
        assertEquals(emptyList(), second.calls)
    }

    @Test
    fun aBadTokenOrNoOriginNeverOpensASocket() {
        val listener = Recorder()
        client.connect("short", listener)
        GameSocketClient("", transport, { true }) { _, _ -> }.connect(token, listener)
        assertEquals(emptyList(), transport.sockets)
        assertEquals(List(2) { "disconnected:false:invalidConnectionConfiguration" }, listener.calls)
        assertFalse(client.send(GamePing("p")))
    }

    @Test
    fun badMessagesBinaryAndClosesEndTheSocketWithTheirCauses() {
        val listener = Recorder()
        client.connect(token, listener)
        val socket = transport.sockets.single()
        socket.events.text(socket, "{\"type\":\"nonsense\"}")
        assertTrue(socket.cancelled)
        socket.events.binary(socket)
        socket.events.closing(socket, 1001, "going away " + "x".repeat(200))
        assertEquals(1001, socket.closedWith)
        socket.events.failed(socket, true, "socketFailure:test")
        assertEquals(listOf("disconnected:false:invalidServerMessage", "disconnected:false:unexpectedBinaryMessage",
            "disconnected:false:socketClose:1001:${("going away " + "x".repeat(200)).take(123)}", "disconnected:true:socketFailure:test"),
            listener.calls)
    }

    @Test
    fun aMessageOverTheLimitIsRefused() {
        assertNull(GameSocketClient.decode("\"" + "x".repeat(GameWire.MAX_MESSAGE_BYTES) + "\""))
        assertEquals(GamePong("1"), GameSocketClient.decode(pong("1")))
    }

    @Test
    fun readsCompleteInTheOrderAskedAndAreDecodedAndChecked() {
        client.connect(token, Recorder())
        val results = mutableListOf<Any?>()
        assertTrue(client.chatPage(game, null, null) { results += it })
        assertTrue(client.historyPage(game, event) { results += it })
        assertTrue(client.chatRead(game, ReadChat(event)) { results += it })
        assertEquals("https://atw.example/v1/games/$game/chat", transport.reads[0].url)
        assertEquals("https://atw.example/v1/games/$game/timeline?through=$event", transport.reads[1].url)
        assertEquals("https://atw.example/v1/games/$game/chat/read", transport.reads[2].url)
        assertEquals("{\"messageId\":\"$event\"}", transport.reads[2].json)
        val page = ChatPage(game, emptyList(), ChatSync("0"))
        val history = EngineHistoryPage(event, "base", "{}", emptyList(), true)
        val read = ChatReadState(game, "0", "0", 0)
        transport.answer(GameWire.json.encodeToString(page))
        transport.answer(GameWire.json.encodeToString(history))
        transport.answer(GameWire.json.encodeToString(read))
        assertEquals(listOf<Any?>(page, history, read), results)
        // An answer that doesn't match what was asked is dropped and logged
        client.historyPage(game, event) { results += it }
        transport.answer(GameWire.json.encodeToString(history.copy(through = game)))
        assertNull(results.last())
        assertEquals(listOf("History page failed"), warnings)
    }

    @Test
    fun aReadAskedBeforeADisconnectFailsWithoutARequest() {
        client.connect(token, Recorder())
        var result: ChatPage? = ChatPage(game, emptyList(), ChatSync("0"))
        client.chatPage(game, "5", null) { result = it }
        assertEquals("https://atw.example/v1/games/$game/chat?before=5", transport.reads.single().url)
        client.disconnect()
        transport.answer("unused")
        assertNull(result)
        assertEquals(listOf("Chat page failed"), warnings)
        // Nothing is asked without a session or with malformed ids
        assertFalse(client.chatPage(game, null, null) {})
        client.connect(token, Recorder())
        assertFalse(client.chatPage("not-a-uuid", null, null) {})
        assertFalse(client.chatPage(game, "1", "2") {})
        assertFalse(client.historyPage(game, "not-a-uuid") {})
        assertTrue(transport.reads.isEmpty())
        client.close()
        assertTrue(transport.closed)
    }
}
