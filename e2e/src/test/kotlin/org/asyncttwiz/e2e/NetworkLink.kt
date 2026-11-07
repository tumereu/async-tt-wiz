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
                pump(client, upstream, incoming = false); pump(upstream, client, incoming = true)
            }
        }, "e2e-link-${listener.localPort}").apply { isDaemon = true }.start()
    }

    private val gate = Object()
    @Volatile private var holding = false
    @Volatile private var holdingOut = false

    /** Every HTTP request this device sent through the link, as "METHOD /path" in the order sent (a test counts the
     * ones it cares about, say the deck service's). */
    val requests: MutableList<String> = Collections.synchronizedList(mutableListOf<String>())
    private val requestLine = Regex("(?:^|\\n)(GET|POST|PUT|DELETE|PATCH) (/\\S*) HTTP/1\\.[01]\\r\\n")

    private fun pump(from: Socket, to: Socket, incoming: Boolean) = Thread({
        try {
            val input = from.getInputStream(); val output = to.getOutputStream()
            val buffer = ByteArray(16 * 1024)
            var carry = ""
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                if (!incoming) {
                    // A request line may be split between reads: the unmatched tail is looked at again with the next read
                    val text = carry + String(buffer, 0, n, Charsets.ISO_8859_1)
                    var end = 0
                    for (match in requestLine.findAll(text)) {
                        requests += "${match.groupValues[1]} ${match.groupValues[2]}"; end = match.range.last + 1
                    }
                    carry = text.substring(maxOf(end, text.length - 512))
                }
                synchronized(gate) { while (if (incoming) holding else holdingOut) gate.wait() }
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

    /** The connection is back. */
    fun restore() { up = true }

    override fun close() { releaseIncoming(); releaseOutgoing(); cut(); runCatching { listener.close() } }
}
