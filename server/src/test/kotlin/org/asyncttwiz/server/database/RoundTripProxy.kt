package org.asyncttwiz.server.database

import java.io.DataInputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * A TCP proxy in front of PostgreSQL that counts the server's database round trips, as seen on the wire.
 * Each PostgreSQL frontend Sync or simple Query message is one round trip: the client waits for its answer.
 * Counts are kept per connection application name, so the work pool and the ownership session are told
 * apart. [latencyMillis] delays every round trip, to imitate a slower database host. Test-only.
 */
class RoundTripProxy(target: DatabaseSettings, private val latencyMillis: Long = 0) : AutoCloseable {
    private val upstream = URI(target.url().removePrefix("jdbc:"))
    private val server = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
    private val sockets = CopyOnWriteArrayList<Socket>()
    private val counts = ConcurrentHashMap<String, AtomicInteger>()
    private val connects = ConcurrentHashMap<String, AtomicInteger>()
    private val texts = ConcurrentHashMap<String, MutableList<String>>()
    @Volatile private var closed = false
    // Unnamed statements only (same round trips), so every statement's text is seen on the wire.
    val settings = DatabaseSettings("jdbc:postgresql://127.0.0.1:${server.localPort}${upstream.rawPath}?" +
        (upstream.rawQuery?.let { "$it&" } ?: "") + "prepareThreshold=0", target.user(), target.password())

    init {
        Thread({
            while (!closed) {
                val client = try { server.accept() } catch (_: Exception) { break }
                sockets.add(client)
                Thread({ relay(client) }, "round-trip-proxy").apply { isDaemon = true }.start()
            }
        }, "round-trip-proxy-accept").apply { isDaemon = true }.start()
    }

    /** Round trips so far on connections named [application]. */
    fun roundTrips(application: String) = counts[application]?.get() ?: 0
    /** The SQL text sent so far on connections named [application] (parsed or simple queries), oldest first. */
    fun statements(application: String): List<String> = texts[application]?.let { synchronized(it) { it.toList() } }.orEmpty()
    /** Every application name seen so far, with its round trips. */
    fun all(): Map<String, Int> = counts.mapValues { it.value.get() }
    /** New connections opened so far by [application]. */
    fun connections(application: String) = connects[application]?.get() ?: 0

    private fun relay(client: Socket) {
        val database = try { Socket(upstream.host, upstream.port) } catch (_: Exception) { client.close(); return }
        sockets.add(database)
        client.tcpNoDelay = true; database.tcpNoDelay = true
        Thread({ pump(database.getInputStream(), client.getOutputStream(), client, database) }, "round-trip-proxy-down")
            .apply { isDaemon = true }.start()
        pump(client, database)
    }

    private fun pump(source: InputStream, sink: OutputStream, vararg close: Socket) {
        try { source.copyTo(sink, 8192) } catch (_: Exception) { } finally { close.forEach { runCatching { it.close() } } }
    }

    /** Client to database: parse frontend messages to name the connection and count Sync/Query. */
    private fun pump(client: Socket, database: Socket) {
        val input = DataInputStream(client.getInputStream().buffered())
        val output = database.getOutputStream().buffered()
        var application = "unknown"
        try {
            // Untyped start: optional SSL/GSS requests (answered by the database), then the StartupMessage.
            while (true) {
                val length = input.readInt(); val code = input.readInt()
                val body = ByteArray(length - 8).also(input::readFully)
                output.write(int(length)); output.write(int(code)); output.write(body); output.flush()
                if (code == 80877103 || code == 80877104) continue
                if (code == 196608) {
                    val fields = String(body, Charsets.UTF_8).split('\u0000')
                    fields.withIndex().firstOrNull { it.value == "application_name" }?.let { application = fields[it.index + 1] }
                    connects.computeIfAbsent(application) { AtomicInteger() }.incrementAndGet()
                }
                break
            }
            var counter = counts.computeIfAbsent(application) { AtomicInteger() }
            while (true) {
                val type = try { input.readUnsignedByte() } catch (_: EOFException) { break }
                val length = input.readInt()
                val body = ByteArray(length - 4).also(input::readFully)
                if (application == "unknown" && (type == 'P'.code || type == 'Q'.code)) {
                    // pgjdbc names the connection with a SET right after connecting.
                    Regex("application_name\\s*=\\s*'([^']*)'").find(String(body, Charsets.UTF_8))?.let { match ->
                        application = match.groupValues[1]
                        counter = counts.computeIfAbsent(application) { AtomicInteger() }
                        connects.computeIfAbsent(application) { AtomicInteger() }.incrementAndGet()
                    }
                }
                if (type == 'Q'.code || type == 'P'.code) {
                    val fields = String(body, Charsets.UTF_8).split('\u0000')
                    val text = if (type == 'Q'.code) fields[0] else fields.getOrElse(1) { "" }
                    texts.computeIfAbsent(application) { mutableListOf() }.let { synchronized(it) { it.add(text) } }
                }
                if (type == 'S'.code || type == 'Q'.code) {
                    counter.incrementAndGet()
                    if (latencyMillis > 0) Thread.sleep(latencyMillis)
                }
                output.write(type); output.write(int(length)); output.write(body)
                // Flush once the client stops writing: it is waiting for an answer.
                if (input.available() == 0) output.flush()
            }
        } catch (_: Exception) {
        } finally { runCatching { client.close() }; runCatching { database.close() } }
    }

    private fun int(value: Int) = byteArrayOf((value ushr 24).toByte(), (value ushr 16).toByte(), (value ushr 8).toByte(), value.toByte())

    override fun close() {
        closed = true
        runCatching { server.close() }
        sockets.forEach { runCatching { it.close() } }
    }
}
