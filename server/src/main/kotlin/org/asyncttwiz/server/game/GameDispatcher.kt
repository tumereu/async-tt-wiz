package org.asyncttwiz.server.game

import java.util.ArrayDeque
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Enqueue synchronously in frame order, then do asynchronous DB work. No thread/SQL race is FIFO. */
class GameDispatcher : AutoCloseable {
    private data class Work(val bytes: Int, val run: () -> Unit, val cancel: () -> Unit)
    private class Lane { val queue = ArrayDeque<Work>(); var bytes = 0 }
    private val lanes = mutableMapOf<String, Lane>()
    private val executor = Executors.newVirtualThreadPerTaskExecutor()
    private var count = 0
    @Synchronized fun <T> submit(gameId: String, bytes: Int = 0, action: () -> T): CompletableFuture<T> {
        val result = CompletableFuture<T>()
        val lane = lanes[gameId]
        if (executor.isShutdown || count >= 4096 || (lane?.queue?.size ?: 0) >= 1024 || (lane?.bytes ?: 0) + bytes > 8 * 1024 * 1024) {
            result.completeExceptionally(GameProtocolFailure("busy", true)); return result
        }
        val selected = lane ?: Lane().also { lanes[gameId] = it }
        selected.queue.add(Work(bytes, {
            try { result.complete(action()) } catch (failure: Exception) { result.completeExceptionally(failure) }
        }, { result.completeExceptionally(GameProtocolFailure("storageUnavailable", true)) }))
        selected.bytes += bytes; count++
        if (lane == null) executor.execute { drain(gameId, selected) }
        return result
    }
    private fun drain(gameId: String, lane: Lane) {
        while (true) {
            val work = synchronized(this) { lane.queue.peekFirst() ?: run { lanes.remove(gameId); return } }
            work.run()
            synchronized(this) { lane.queue.removeFirst(); lane.bytes -= work.bytes; count-- }
        }
    }
    fun available() = !executor.isShutdown && !executor.isTerminated
    override fun close() {
        executor.shutdownNow()
        synchronized(this) {
            // A running transaction is fenced/drained by ServiceRuntime. Futures cannot strand a socket.
            lanes.values.forEach { lane -> lane.queue.forEach { it.cancel() } }
        }
        try { executor.awaitTermination(5, TimeUnit.SECONDS) } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
    }
}
