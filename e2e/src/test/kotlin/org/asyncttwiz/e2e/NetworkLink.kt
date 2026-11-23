package org.asyncttwiz.e2e

import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.util.Collections

/**
 * One device's network: a TCP relay on its own loopback port to the server ([origin] is what the device's app uses).
 * [cut] drops every open connection and fails new ones at once (the phone's connection gone, as the Pi tunnel dropping);
 * [restore] lets them through again. Everything else is the real sockets.
 */
class NetworkLink(target: String) : AutoCloseable {
    private val server = URI(target)
    private val listener = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
    private val open = Collections.synchronizedSet(mutableSetOf<Socket>())
    @Volatile var up = true
        private set
    val origin = "http://127.0.0.1:${listener.localPort}"

    init {
        Thread({
            while (!listener.isClosed) {
                val client = runCatching { listener.accept() }.getOrNull() ?: break
                if (!up) { runCatching { client.close() }; continue }
                val upstream = runCatching { Socket(server.host, server.port) }.getOrNull()
                if (upstream == null) { runCatching { client.close() }; continue }
                open += client; open += upstream
                // A cut while this connection was being made went past it: its sockets weren't open yet
                if (!up) { runCatching { client.close() }; runCatching { upstream.close() }; open -= client; open -= upstream; continue }
                // The path of this connection's latest request: its answers wait while [holdAnswers] holds it
                val lastPath = java.util.concurrent.atomic.AtomicReference("")
                pump(client, upstream, incoming = false, lastPath); pump(upstream, client, incoming = true, lastPath)
            }
        }, "e2e-link-${listener.localPort}").apply { isDaemon = true }.start()
    }

    private val gate = Object()
    @Volatile private var holding = false
    @Volatile private var holdingOut = false

    /** Every HTTP request this device sent through the link, as "METHOD /path" in the order sent (a test counts the
     * ones it cares about, say the deck service's). */
    val requests: MutableList<String> = Collections.synchronizedList(mutableListOf<String>())
    private val requestLine = Regex("(GET|POST|PUT|DELETE|PATCH) (/\\S*) HTTP/1\\.[01]")
    private val contentLength = Regex("(?im)^content-length:\\s*(\\d+)\\s*$")

    /**
     * The requests of one connection, read as HTTP frames them: a request's head up to its blank line, then as many bytes
     * of body as its Content-Length says. A request that follows one with a body on the same kept-alive connection starts
     * right after that body, not on a line of its own (a deck submit after an activity PUT went unseen, so unheld). Once a
     * connection is upgraded (the game socket) the rest is its messages, not requests.
     */
    private inner class Requests(private val lastPath: java.util.concurrent.atomic.AtomicReference<String>) {
        private val head = StringBuilder()
        private var body = 0L
        private var upgraded = false

        fun read(bytes: ByteArray, count: Int) {
            var text = String(bytes, 0, count, Charsets.ISO_8859_1)
            while (text.isNotEmpty() && !upgraded) {
                if (body > 0) {
                    val skipped = minOf(body, text.length.toLong()).toInt()
                    body -= skipped; text = text.substring(skipped); continue
                }
                head.append(text); text = ""
                val end = head.indexOf("\r\n\r\n")
                if (end < 0) { if (head.length > 64 * 1024) head.setLength(0); break }
                val lines = head.substring(0, end)
                text = head.substring(end + 4); head.setLength(0)
                val match = requestLine.matchEntire(lines.substringBefore("\r\n")) ?: continue
                requests += "${match.groupValues[1]} ${match.groupValues[2]}"
                lastPath.set(match.groupValues[2])
                body = contentLength.find(lines)?.groupValues?.get(1)?.toLong() ?: 0L
                upgraded = Regex("(?im)^upgrade:").containsMatchIn(lines)
            }
        }
    }

    private fun pump(from: Socket, to: Socket, incoming: Boolean, lastPath: java.util.concurrent.atomic.AtomicReference<String>) = Thread({
        try {
            val input = from.getInputStream(); val output = to.getOutputStream()
            val buffer = ByteArray(16 * 1024)
            val requests = if (incoming) null else Requests(lastPath)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                requests?.read(buffer, n)
                synchronized(gate) { while (if (incoming) holding || heldAnswers.any { lastPath.get().startsWith(it) } else holdingOut) gate.wait() }
                output.write(buffer, 0, n); output.flush()
            }
        } catch (_: Exception) {}
        runCatching { from.close() }; runCatching { to.close() }
        open -= from; open -= to
    }, "e2e-link-pump").apply { isDaemon = true }.start()

    /** The connection drops: open sockets close, new ones fail. */
    fun cut() {
        up = false
        synchronized(open) { open.toList() }.forEach { runCatching { it.close() } }
    }

    /** What the server sends this device waits in the network (the device's own messages still go out), until
     * [releaseIncoming]: a slow downlink, so the server's answers reach the device later than its own moves go out. */
    fun holdIncoming() { holding = true }

    fun releaseIncoming() = synchronized(gate) { holding = false; gate.notifyAll() }

    /** What this device sends waits in the network (the server's messages still come in), until [releaseOutgoing]: a slow
     * uplink, so the other phone's moves reach the server before this device's own. */
    fun holdOutgoing() { holdingOut = true }

    fun releaseOutgoing() = synchronized(gate) { holdingOut = false; gate.notifyAll() }

    @Volatile private var heldAnswers = emptySet<String>()

    /** What the server sends on this device's connections whose latest request's path starts with one of [paths] waits in
     * the network (everything else goes through), until [releaseAnswers]: a slow read, say the lists' ("/v1/games?"), or
     * the game socket's messages ("/v1/socket"). */
    fun holdAnswers(vararg paths: String) = synchronized(gate) { heldAnswers = heldAnswers + paths }

    /** Lets through what [holdAnswers] held for [paths] (all of it without any). */
    fun releaseAnswers(vararg paths: String) = synchronized(gate) {
        heldAnswers = if (paths.isEmpty()) emptySet() else heldAnswers - paths.toSet(); gate.notifyAll()
    }

    /** The connection is back. */
    fun restore() { up = true }

    override fun close() { releaseIncoming(); releaseOutgoing(); releaseAnswers(); cut(); runCatching { listener.close() } }
}
