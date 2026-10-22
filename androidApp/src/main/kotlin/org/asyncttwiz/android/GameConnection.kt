package org.asyncttwiz.android

import okhttp3.*
import okio.ByteString
import org.asyncttwiz.protocol.*
import kotlinx.serialization.encodeToString
import java.net.URI
import java.util.concurrent.TimeUnit

/** One ordered socket. Credentials are headers only; redirects never forward them. */
interface GameConnection : AutoCloseable {
    interface Listener {
        fun opened()
        fun message(message: GameServerMessage)
        fun disconnected(unauthorized: Boolean = false)
    }
    fun connect(token: String, listener: Listener)
    fun send(message: GameClientMessage): Boolean
    fun disconnect()
}

class SocketGameConnection(private val origin: String) : GameConnection {
    private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .connectTimeout(15, TimeUnit.SECONDS).pingInterval(25, TimeUnit.SECONDS).build()
    private var socket: WebSocket? = null
    init {
        require(origin.isEmpty() || URI(origin).let { it.host != null && it.rawUserInfo == null &&
            it.rawQuery == null && it.rawFragment == null && it.rawPath.isNullOrEmpty() &&
            (it.scheme == "https" || (BuildConfig.DEBUG && it.scheme == "http" && it.host == "127.0.0.1")) })
    }
    override fun connect(token: String, listener: GameConnection.Listener) {
        disconnect()
        if (origin.isEmpty() || !AccountApi.validToken(token)) { listener.disconnected(); return }
        socket = client.newWebSocket(Request.Builder().url(origin + "/v1/socket")
            .header("Authorization", "Bearer $token").build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) = listener.opened()
            override fun onMessage(webSocket: WebSocket, text: String) {
                val message = runCatching {
                    require(text.toByteArray().size <= 1048576)
                    GameWire.json.decodeFromString<GameServerMessage>(text)
                }.getOrNull()
                if (message == null) { webSocket.cancel(); listener.disconnected() } else listener.message(message)
            }
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) { webSocket.cancel(); listener.disconnected() }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(code, null); listener.disconnected() }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = listener.disconnected(response?.code == 401)
        })
    }
    override fun send(message: GameClientMessage) = socket?.send(GameWire.json.encodeToString(message)) == true
    override fun disconnect() { socket?.cancel(); socket = null }
    override fun close() { disconnect(); client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll() }
}
