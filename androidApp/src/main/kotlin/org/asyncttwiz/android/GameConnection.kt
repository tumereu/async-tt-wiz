package org.asyncttwiz.android

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okio.ByteString
import org.asyncttwiz.protocol.*
import kotlinx.serialization.encodeToString
import java.net.URI
import java.util.concurrent.TimeUnit
import java.util.concurrent.Executors
import java.io.InputStream
import java.io.ByteArrayOutputStream
import org.asyncttwiz.storage.evictConnectionsInBackground

data class GameplayTreeTransfer(val manifest: GameplayManifest, val plan: TreeDownloadPlan,
    val openChunk: (Int) -> InputStream)

/** One ordered socket. Credentials are headers only; redirects never forward them. */
interface GameConnection : AutoCloseable {
    interface Listener {
        fun opened()
        fun message(message: GameServerMessage)
        fun disconnected(unauthorized: Boolean = false)
        fun disconnected(unauthorized: Boolean, cause: String) = disconnected(unauthorized)
    }
    fun connect(token: String, listener: Listener)
    fun send(message: GameClientMessage): Boolean
    fun disconnect()
    /** The old model's history transfers (only test doubles still answer them): the server has no such routes since
     * I-D56 step D2 and the app's connection starts none (false), so a game that would need one can't be opened. */
    fun baseline(gameId: String, requestIds: List<String>, rejected: (String) -> Unit = {},
        complete: (GameplayBaseline?) -> Unit): Boolean = false
    fun treeTransfer(gameId: String, requestIds: List<String>, rejected: (String) -> Unit = {},
        complete: (GameplayTreeTransfer?) -> Unit): Boolean = false
    fun chatPage(gameId: String, before: String? = null, after: String? = null, complete: (ChatPage?) -> Unit): Boolean = false
    fun chatRead(gameId: String, input: ReadChat? = null, complete: (ChatReadState?) -> Unit): Boolean = false
}

class SocketGameConnection(private val origin: String) : GameConnection {
    private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .connectTimeout(15, TimeUnit.SECONDS).pingInterval(25, TimeUnit.SECONDS).build()
    private var socket: WebSocket? = null
    @Volatile private var token: String? = null
    private val reads = Executors.newSingleThreadExecutor()
    init {
        require(origin.isEmpty() || URI(origin).let { it.host != null && it.rawUserInfo == null &&
            it.rawQuery == null && it.rawFragment == null && it.rawPath.isNullOrEmpty() &&
            (it.scheme == "https" || (BuildConfig.DEBUG && it.scheme == "http" && it.host == "127.0.0.1")) })
    }
    override fun connect(token: String, listener: GameConnection.Listener) {
        disconnect()
        if (origin.isEmpty() || !AccountApi.validToken(token)) { listener.disconnected(false, "invalidConnectionConfiguration"); return }
        this.token = token
        socket = client.newWebSocket(Request.Builder().url(origin + "/v1/socket")
            .header("Authorization", "Bearer $token").build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) = listener.opened()
            override fun onMessage(webSocket: WebSocket, text: String) {
                val message = runCatching {
                    require(text.toByteArray().size <= GameWire.MAX_MESSAGE_BYTES)
                    GameWire.json.decodeFromString<GameServerMessage>(text)
                }.getOrNull()
                if (message == null) { webSocket.cancel(); listener.disconnected(false, "invalidServerMessage") } else listener.message(message)
            }
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) { webSocket.cancel(); listener.disconnected(false, "unexpectedBinaryMessage") }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, null)
                listener.disconnected(false, "socketClose:$code:${reason.take(123)}")
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                AppLog.warn("game", "Socket failed (HTTP ${response?.code})", t)
                listener.disconnected(response?.code == 401,
                    if (t is java.net.SocketTimeoutException && t.message?.contains("ping", ignoreCase = true) == true) "socketPingTimeout"
                    else "socketFailure:${t.javaClass.simpleName}:http=${response?.code}")
            }
        })
    }
    override fun send(message: GameClientMessage) = socket?.send(GameWire.json.encodeToString(message)) == true
    override fun chatRead(gameId: String, input: ReadChat?, complete: (ChatReadState?) -> Unit): Boolean {
        val credential = token ?: return false
        if (!DeckValidation.uuid(gameId) || input?.let { !DeckValidation.uuid(it.messageId) } == true) return false
        reads.execute {
            val state = runCatching {
                check(token == credential)
                val builder = Request.Builder().url("$origin/v1/games/$gameId/chat/read")
                    .header("Authorization", "Bearer $credential")
                if (input != null) builder.put(GameWire.json.encodeToString(input).toRequestBody("application/json".toMediaType()))
                client.newCall(builder.build()).execute().use { response ->
                    require(response.isSuccessful)
                    GameWire.json.decodeFromString<ChatReadState>(requireNotNull(response.body).byteStream()
                        .bounded(4096).toString(Charsets.UTF_8)).also {
                        require(it.gameId == gameId && it.unread >= 0)
                        require(GameWire.ordinal(it.readThrough) <= GameWire.ordinal(it.throughRevision))
                    }
                }
            }.onFailure { AppLog.warn("game", "Chat read state failed", it) }.getOrNull()
            complete(state)
        }
        return true
    }
    override fun chatPage(gameId: String, before: String?, after: String?, complete: (ChatPage?) -> Unit): Boolean {
        val credential = token ?: return false
        if (!DeckValidation.uuid(gameId) || (before != null && after != null)) return false
        val cursor = before ?: after
        if (cursor != null && runCatching { GameWire.ordinal(cursor) }.isFailure) return false
        reads.execute {
            val page = runCatching {
                check(token == credential)
                val query = if (before != null) "?before=$before" else if (after != null) "?after=$after" else ""
                client.newCall(Request.Builder().url("$origin/v1/games/$gameId/chat$query")
                    .header("Authorization", "Bearer $credential").build()).execute().use { response ->
                    require(response.isSuccessful)
                    GameWire.json.decodeFromString<ChatPage>(requireNotNull(response.body).byteStream()
                        .bounded(ChatPaging.MAX_BYTES).toString(Charsets.UTF_8)).also {
                        require(it.gameId == gameId && it.messages.size <= ChatPaging.SIZE)
                    }
                }
            }.onFailure { AppLog.warn("game", "Chat page failed", it) }.getOrNull()
            complete(page)
        }
        return true
    }
    override fun disconnect() { socket?.cancel(); socket = null; token = null }
    override fun close() { disconnect(); reads.shutdown(); client.dispatcher.executorService.shutdown(); client.evictConnectionsInBackground() }
}

private fun InputStream.bounded(limit: Int): ByteArray {
    val output = ByteArrayOutputStream(); val buffer = ByteArray(8192)
    while (true) {
        val count = read(buffer, 0, minOf(buffer.size, limit - output.size() + 1))
        if (count < 0) return output.toByteArray()
        require(output.size() + count <= limit)
        output.write(buffer, 0, count)
    }
}
