package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.asyncttwiz.storage.GameSocketClient
import org.asyncttwiz.storage.GameSocketListener
import org.asyncttwiz.storage.GameSocketTransport

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
    fun chatPage(gameId: String, before: String? = null, after: String? = null, complete: (ChatPage?) -> Unit): Boolean = false
    fun chatRead(gameId: String, input: ReadChat? = null, complete: (ChatReadState?) -> Unit): Boolean = false
    /** HIS-111, HIS-099: the page of [gameId]'s main line ending at its event [through] (older history), or null when
     * it can't be read; [complete] runs off the main thread. False when nothing was asked. */
    fun historyPage(gameId: String, through: String, complete: (EngineHistoryPage?) -> Unit): Boolean = false
}

/** A socket to the game server at [origin] (Android: [SocketGameConnection], on OkHttp; the browser: WebSocket). */
expect fun platformGameConnection(origin: String): GameConnection

/** The game socket at [origin] over a platform's [transport]: client-storage's [GameSocketClient] (web client step 3b),
 * the same logic on Android and in the browser. */
open class TransportGameConnection(origin: String, transport: GameSocketTransport) : GameConnection {
    private val client = GameSocketClient(origin, transport, { AccountApi.validToken(it) }) { message, failure ->
        AppLog.warn("game", message, failure)
    }
    override fun connect(token: String, listener: GameConnection.Listener) = client.connect(token, object : GameSocketListener {
        override fun opened() = listener.opened()
        override fun message(message: GameServerMessage) = listener.message(message)
        override fun disconnected(unauthorized: Boolean, cause: String) = listener.disconnected(unauthorized, cause)
    })
    override fun send(message: GameClientMessage) = client.send(message)
    override fun chatRead(gameId: String, input: ReadChat?, complete: (ChatReadState?) -> Unit) = client.chatRead(gameId, input, complete)
    override fun chatPage(gameId: String, before: String?, after: String?, complete: (ChatPage?) -> Unit) =
        client.chatPage(gameId, before, after, complete)
    override fun historyPage(gameId: String, through: String, complete: (EngineHistoryPage?) -> Unit) =
        client.historyPage(gameId, through, complete)
    override fun disconnect() = client.disconnect()
    override fun close() = client.close()
}
