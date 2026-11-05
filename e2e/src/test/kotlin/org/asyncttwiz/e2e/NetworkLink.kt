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

    private fun pump(from: Socket, to: Socket, incoming: Boolean) = Thread({
        try {
            val input = from.getInputStream(); val output = to.getOutputStream()
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                if (incoming) synchronized(gate) { while (holding) gate.wait() }
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

    /** The connection is back. */
    fun restore() { up = true }

    override fun close() { releaseIncoming(); cut(); runCatching { listener.close() } }
}
