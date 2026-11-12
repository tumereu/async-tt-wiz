package org.asyncttwiz.storage

import kotlin.concurrent.Volatile
import kotlinx.serialization.encodeToString
import org.asyncttwiz.protocol.ChatPage
import org.asyncttwiz.protocol.ChatPaging
import org.asyncttwiz.protocol.ChatReadState
import org.asyncttwiz.protocol.DeckValidation
import org.asyncttwiz.protocol.EngineHistoryPage
import org.asyncttwiz.protocol.GameClientMessage
import org.asyncttwiz.protocol.GameServerMessage
import org.asyncttwiz.protocol.GameWire
import org.asyncttwiz.protocol.ReadChat

// The game socket's logic both clients share (web client step 3b): one ordered socket per connect, its messages checked
// and decoded, closes and failures turned into causes, and the HTTP reads beside it (chat pages, chat read state, older
// timeline). The socket and HTTP themselves are the platform's [GameSocketTransport] (Android: OkHttp; the browser:
// WebSocket and fetch). Reconnecting, backoff and pings are the game session's, above this.

/** What [GameSocketClient] tells the game session (app-ui's GameConnection.Listener). */
interface GameSocketListener {
    fun opened()
    fun message(message: GameServerMessage)
    fun disconnected(unauthorized: Boolean, cause: String)
}

/** An open socket of a [GameSocketTransport]. */
interface GameSocketHandle {
    /** Queues [text]; false when the socket can't take it (closed). */
    fun send(text: String): Boolean
    /** Drops the connection at once. */
    fun cancel()
    /** Answers the server's close with [code]. */
    fun close(code: Int)
}

/** What a platform's socket reports, each with the socket it happened on. */
interface GameSocketEvents {
    fun opened(socket: GameSocketHandle)
    fun text(socket: GameSocketHandle, text: String)
    fun binary(socket: GameSocketHandle)
    /** The server closes the socket with [code] and [reason]. */
    fun closing(socket: GameSocketHandle, code: Int, reason: String)
    /** The connection failed ([cause]: what the session logs and reports); [unauthorized] when the server refused the
     * credential. The platform logs the failure itself. */
    fun failed(socket: GameSocketHandle, unauthorized: Boolean, cause: String)
}

/** A platform's socket and HTTP. */
interface GameSocketTransport {
    /** Opens a socket to [url] authenticated by [token], reporting to [events]. */
    fun open(url: String, token: String, events: GameSocketEvents): GameSocketHandle
    /**
     * One HTTP read of [url] authenticated by [token]: a GET, or a PUT of the JSON [json] when given. [complete] gets the
     * answer's text (a failure unless the answer is a success of at most [maxBytes] UTF-8 bytes). Reads run and complete
     * one at a time in the order asked, off the caller; [current] is checked just before each request goes out (false:
     * it fails without a request).
     */
    fun read(url: String, token: String, json: String?, maxBytes: Int, current: () -> Boolean, complete: (Result<String>) -> Unit)
    /** Ends the transport's work for good. */
    fun close()
}

/**
 * The game socket at [origin] over [transport] (app-ui's GameConnection). [validToken] checks a session token's shape
 * before it is sent; [warn] logs a failed read.
 */
class GameSocketClient(private val origin: String, private val transport: GameSocketTransport,
    private val validToken: (String) -> Boolean, private val warn: (String, Throwable) -> Unit) {
    private var socket: GameSocketHandle? = null
    @Volatile private var token: String? = null

    fun connect(token: String, listener: GameSocketListener) {
        disconnect()
        if (origin.isEmpty() || !validToken(token)) { listener.disconnected(false, "invalidConnectionConfiguration"); return }
        this.token = token
        socket = transport.open("$origin/v1/socket", token, object : GameSocketEvents {
            override fun opened(socket: GameSocketHandle) = listener.opened()
            override fun text(socket: GameSocketHandle, text: String) {
                val message = decode(text)
                if (message == null) { socket.cancel(); listener.disconnected(false, "invalidServerMessage") } else listener.message(message)
            }
            override fun binary(socket: GameSocketHandle) { socket.cancel(); listener.disconnected(false, "unexpectedBinaryMessage") }
            override fun closing(socket: GameSocketHandle, code: Int, reason: String) {
                socket.close(code)
                listener.disconnected(false, "socketClose:$code:${reason.take(123)}")
            }
            override fun failed(socket: GameSocketHandle, unauthorized: Boolean, cause: String) = listener.disconnected(unauthorized, cause)
        })
    }

    fun send(message: GameClientMessage): Boolean = socket?.send(GameWire.json.encodeToString(message)) == true

    fun chatRead(gameId: String, input: ReadChat?, complete: (ChatReadState?) -> Unit): Boolean {
        val credential = token ?: return false
        if (!DeckValidation.uuid(gameId) || input?.let { !DeckValidation.uuid(it.messageId) } == true) return false
        read("$origin/v1/games/$gameId/chat/read", credential, input?.let { GameWire.json.encodeToString(it) }, 4096,
            "Chat read state failed", complete) { text ->
            GameWire.json.decodeFromString<ChatReadState>(text).also {
                require(it.gameId == gameId && it.unread >= 0)
                require(GameWire.ordinal(it.readThrough) <= GameWire.ordinal(it.throughRevision))
            }
        }
        return true
    }

    fun chatPage(gameId: String, before: String?, after: String?, complete: (ChatPage?) -> Unit): Boolean {
        val credential = token ?: return false
        if (!DeckValidation.uuid(gameId) || (before != null && after != null)) return false
        val cursor = before ?: after
        if (cursor != null && runCatching { GameWire.ordinal(cursor) }.isFailure) return false
        val query = if (before != null) "?before=$before" else if (after != null) "?after=$after" else ""
        read("$origin/v1/games/$gameId/chat$query", credential, null, ChatPaging.MAX_BYTES, "Chat page failed", complete) { text ->
            GameWire.json.decodeFromString<ChatPage>(text).also { require(it.gameId == gameId && it.messages.size <= ChatPaging.SIZE) }
        }
        return true
    }

    fun historyPage(gameId: String, through: String, complete: (EngineHistoryPage?) -> Unit): Boolean {
        val credential = token ?: return false
        if (!DeckValidation.uuid(gameId) || !DeckValidation.uuid(through)) return false
        read("$origin/v1/games/$gameId/timeline?through=$through", credential, null, GameWire.MAX_MESSAGE_BYTES,
            "History page failed", complete) { text ->
            GameWire.json.decodeFromString<EngineHistoryPage>(text).also { require(it.through == through) }
        }
        return true
    }

    fun disconnect() { socket?.cancel(); socket = null; token = null }

    fun close() { disconnect(); transport.close() }

    private fun <T : Any> read(url: String, credential: String, json: String?, maxBytes: Int, failure: String,
        complete: (T?) -> Unit, decode: (String) -> T) {
        transport.read(url, credential, json, maxBytes, current = { token == credential }) { answer ->
            complete(runCatching { decode(answer.getOrThrow()) }.onFailure { warn(failure, it) }.getOrNull())
        }
    }

    companion object {
        /** [text] as a server message, null when it is too large or not one. */
        fun decode(text: String): GameServerMessage? = runCatching {
            require(text.encodeToByteArray().size <= GameWire.MAX_MESSAGE_BYTES)
            GameWire.json.decodeFromString<GameServerMessage>(text)
        }.getOrNull()
    }
}
