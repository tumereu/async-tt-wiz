@file:OptIn(ExperimentalWasmJsInterop::class)

package org.asyncttwiz.android

import kotlin.js.Promise
import kotlinx.coroutines.await
import org.asyncttwiz.appui.AppBuild
import org.asyncttwiz.appui.pageScope
import org.asyncttwiz.appui.parseUri
import org.asyncttwiz.appui.postToMainDelayed
import org.asyncttwiz.appui.uncaught
import org.asyncttwiz.appui.uptimeMillis
import org.asyncttwiz.protocol.GameWire
import org.asyncttwiz.storage.GameSocketEvents
import org.asyncttwiz.storage.GameSocketHandle
import org.asyncttwiz.storage.GameSocketTransport
import org.asyncttwiz.storage.SerialTasks

// The game socket in the browser (web client step 3b): the shared logic (TransportGameConnection) over the browser's
// WebSocket and fetch.

actual fun platformGameConnection(origin: String): GameConnection {
    require(origin.isEmpty() || parseUri(origin).let { it.host != null && it.rawUserInfo == null &&
        it.rawQuery == null && it.rawFragment == null && it.rawPath.isNullOrEmpty() &&
        (it.scheme == "https" || (AppBuild.LOOPBACK_API && it.scheme == "http" && it.host == "127.0.0.1")) })
    return TransportGameConnection(origin, BrowserGameTransport())
}

/**
 * The browser's WebSocket and fetch. A browser can't put the session token in a header on a WebSocket, so it goes as a
 * subprotocol ([GameWire.SOCKET_TOKEN_PREFIX]) beside [GameWire.SOCKET_PROTOCOL], which the server answers with. The
 * browser hides the handshake's HTTP status, so a refused token shows as an ordinary failure here (the lobby's HTTP
 * reads ask for sign-in instead). Nor can a page send WebSocket pings: a socket silent for [SILENCE_MILLIS] (the session
 * pings every 25 s and the server answers) counts as dead, as OkHttp's missed ping does on Android.
 */
class BrowserGameTransport : GameSocketTransport {
    private val reads = SerialTasks(pageScope) { uncaught("game-reads", it) }

    override fun open(url: String, token: String, events: GameSocketEvents): GameSocketHandle {
        var lastHeard = uptimeMillis()
        var open = true
        var cancelWatch: () -> Unit = {}
        lateinit var handle: GameSocketHandle
        fun ended() { open = false; cancelWatch() }
        val socket = wsOpen("ws" + url.removePrefix("http"), GameWire.SOCKET_PROTOCOL + "," + GameWire.SOCKET_TOKEN_PREFIX + token,
            opened = { lastHeard = uptimeMillis(); events.opened(handle) },
            text = { text -> lastHeard = uptimeMillis(); events.text(handle, text) },
            binary = { events.binary(handle) },
            closed = { code, reason, clean ->
                ended()
                if (clean && code != ABNORMAL_CLOSE) events.closing(handle, code, reason)
                else {
                    AppLog.warn("game", "Socket failed (close $code)")
                    events.failed(handle, false, "socketFailure:WebSocket:close=$code")
                }
            })
        handle = object : GameSocketHandle {
            override fun send(text: String) = open && wsSend(socket, text)
            override fun cancel() { ended(); wsCancel(socket) }
            override fun close(code: Int) { ended(); wsClose(socket, code) }
        }
        fun watch() {
            cancelWatch = postToMainDelayed(WATCH_MILLIS) {
                if (!open) return@postToMainDelayed
                if (uptimeMillis() - lastHeard > SILENCE_MILLIS) {
                    AppLog.warn("game", "Socket silent for ${uptimeMillis() - lastHeard} ms: dropped")
                    handle.cancel()
                    events.failed(handle, false, "socketPingTimeout")
                } else watch()
            }
        }
        watch()
        return handle
    }

    override fun read(url: String, token: String, json: String?, maxBytes: Int, current: () -> Boolean,
        complete: (Result<String>) -> Unit) {
        reads.submit {
            complete(runCatching {
                check(current())
                fetchAuthorized(url, token, json).await<JsString>().toString().also { require(it.encodeToByteArray().size <= maxBytes) }
            })
        }
    }

    override fun close() { reads.shutdown() }

    private companion object {
        const val ABNORMAL_CLOSE = 1006
        const val WATCH_MILLIS = 25_000L
        const val SILENCE_MILLIS = 100_000L
    }
}

/** A WebSocket to [url] asking for the comma-separated [protocols]; text messages as text, binary ones only noticed. */
private fun wsOpen(url: String, protocols: String, opened: () -> Unit, text: (String) -> Unit, binary: () -> Unit,
    closed: (code: Int, reason: String, clean: Boolean) -> Unit): JsAny = js("""(() => {
    const socket = new WebSocket(url, protocols.split(','));
    socket.binaryType = 'arraybuffer';
    socket.onopen = () => opened();
    socket.onmessage = (event) => { if (typeof event.data === 'string') text(event.data); else binary(); };
    socket.onclose = (event) => closed(event.code, event.reason || '', event.wasClean);
    return socket;
})()""")

private fun wsSend(socket: JsAny, text: String): Boolean = js("""(() => {
    if (socket.readyState !== 1) return false;
    socket.send(text);
    return true;
})()""")

/** Drops [socket] at once: nothing more is reported from it. */
private fun wsCancel(socket: JsAny): Unit = js("""{
    socket.onopen = null; socket.onmessage = null; socket.onclose = null;
    try { socket.close(); } catch (e) {}
}""")

/** Answers the server's close with [code] where a page may send it (1000, 3000-4999), else a plain close. */
private fun wsClose(socket: JsAny, code: Int): Unit = js("""{
    socket.onopen = null; socket.onmessage = null; socket.onclose = null;
    try { if (code === 1000 || (code >= 3000 && code <= 4999)) socket.close(code); else socket.close(); } catch (e) {}
}""")

/** GET [url] (PUT of [json] when given) with the session [token]: the answer's text; rejects unless it is a success.
 * No redirects followed, no cookies, no cache. */
private fun fetchAuthorized(url: String, token: String, json: String?): Promise<JsString> = js("""(async () => {
    const init = { method: json === null ? 'GET' : 'PUT', headers: { 'Authorization': 'Bearer ' + token },
        redirect: 'manual', credentials: 'omit', cache: 'no-store' };
    if (json !== null) { init.headers['Content-Type'] = 'application/json'; init.body = json; }
    const response = await fetch(url, init);
    if (!response.ok) throw new Error('HTTP ' + response.status);
    return await response.text();
})()""")
