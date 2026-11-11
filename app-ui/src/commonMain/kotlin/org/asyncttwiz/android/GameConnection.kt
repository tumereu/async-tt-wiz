package org.asyncttwiz.android

import org.asyncttwiz.protocol.*

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

/** A socket to the game server at [origin] (Android: [SocketGameConnection], on OkHttp). */
expect fun platformGameConnection(origin: String): GameConnection
