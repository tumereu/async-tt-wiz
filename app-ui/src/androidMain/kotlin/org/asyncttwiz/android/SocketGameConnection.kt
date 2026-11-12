package org.asyncttwiz.android

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okio.ByteString
import java.net.URI
import java.util.concurrent.TimeUnit
import java.util.concurrent.Executors
import java.io.InputStream
import java.io.ByteArrayOutputStream
import org.asyncttwiz.storage.GameSocketEvents
import org.asyncttwiz.storage.GameSocketHandle
import org.asyncttwiz.storage.GameSocketTransport
import org.asyncttwiz.storage.evictConnectionsInBackground
import org.asyncttwiz.appui.AppBuild


actual fun platformGameConnection(origin: String): GameConnection = SocketGameConnection(origin)

/** The game socket on OkHttp: [OkHttpGameTransport] under the shared logic ([TransportGameConnection]). */
class SocketGameConnection(origin: String) : TransportGameConnection(origin.also(::requireGameOrigin), OkHttpGameTransport())

private fun requireGameOrigin(origin: String) {
    require(origin.isEmpty() || URI(origin).let { it.host != null && it.rawUserInfo == null &&
        it.rawQuery == null && it.rawFragment == null && it.rawPath.isNullOrEmpty() &&
        (it.scheme == "https" || (AppBuild.LOOPBACK_API && it.scheme == "http" && it.host == "127.0.0.1")) })
}

/** OkHttp's socket (pinged every 25 s; no redirects) and its HTTP reads, one at a time on their own thread. */
class OkHttpGameTransport : GameSocketTransport {
    private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .connectTimeout(15, TimeUnit.SECONDS).pingInterval(25, TimeUnit.SECONDS).build()
    private val reads = Executors.newSingleThreadExecutor()

    override fun open(url: String, token: String, events: GameSocketEvents): GameSocketHandle {
        val socket = client.newWebSocket(Request.Builder().url(url)
            .header("Authorization", "Bearer $token").build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) = events.opened(handle(webSocket))
            override fun onMessage(webSocket: WebSocket, text: String) = events.text(handle(webSocket), text)
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) = events.binary(handle(webSocket))
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) = events.closing(handle(webSocket), code, reason)
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                AppLog.warn("game", "Socket failed (HTTP ${response?.code})", t)
                events.failed(handle(webSocket), response?.code == 401,
                    if (t is java.net.SocketTimeoutException && t.message?.contains("ping", ignoreCase = true) == true) "socketPingTimeout"
                    else "socketFailure:${t.javaClass.simpleName}:http=${response?.code}")
            }
        })
        return handle(socket)
    }

    private fun handle(socket: WebSocket) = object : GameSocketHandle {
        override fun send(text: String) = socket.send(text)
        override fun cancel() = socket.cancel()
        override fun close(code: Int) { socket.close(code, null) }
    }

    override fun read(url: String, token: String, json: String?, maxBytes: Int, current: () -> Boolean,
        complete: (Result<String>) -> Unit) {
        reads.execute {
            complete(runCatching {
                check(current())
                val builder = Request.Builder().url(url).header("Authorization", "Bearer $token")
                if (json != null) builder.put(json.toRequestBody("application/json".toMediaType()))
                client.newCall(builder.build()).execute().use { response ->
                    require(response.isSuccessful) { "HTTP ${response.code}" }
                    requireNotNull(response.body).byteStream().bounded(maxBytes).toString(Charsets.UTF_8)
                }
            })
        }
    }

    override fun close() { reads.shutdown(); client.dispatcher.executorService.shutdown(); client.evictConnectionsInBackground() }
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
