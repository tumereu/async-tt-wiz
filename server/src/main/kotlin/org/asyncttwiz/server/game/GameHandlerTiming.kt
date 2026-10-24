package org.asyncttwiz.server.game

import org.slf4j.LoggerFactory

/** Includes dispatcher queue wait separately from authentication/SQL/serialization handling. */
internal class GameHandlerTiming(private val now: () -> Long = System::nanoTime,
    private val slow: (String, String, Double, Double, String) -> Unit = { game, handler, queue, handling, outcome ->
        LoggerFactory.getLogger(GameHub::class.java).warn(
            "Slow game session game={} handler={} queueMs={} handlingMs={} outcome={}", game, handler, queue, handling, outcome)
    }) {
    fun queued(gameId: String, handler: String): (() -> Unit) -> Unit {
        val queued = now()
        return { work ->
            val started = now()
            var outcome = "ok"
            try { work() } catch (failure: Exception) { outcome = failure.javaClass.simpleName; throw failure }
            finally {
                val queue = (started - queued).coerceAtLeast(0) / 1_000_000.0
                val handling = (now() - started).coerceAtLeast(0) / 1_000_000.0
                if (queue + handling >= 1_000) slow(gameId, handler, queue, handling, outcome)
            }
        }
    }
}
