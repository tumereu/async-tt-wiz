package org.asyncttwiz.android

/**
 * SYS-025, SYS-030 (engine-responsiveness R1): one own action's probe, its time split into parts. Up to its board:
 * [WAIT] (queued behind other work on the engine thread, e.g. the 1-second tick), [ENGINE] (the engine's work: the
 * action's inputs and what follows them before its board), [INFO] (the information rule's work on a tentative line) and
 * [BOARD] (building the board), adding up to [TOTAL]. After the board is published: [FRAME] (the main thread's next frame
 * drawn), [COMPUTED] and [PLAYS] (the table's computed board and the player's hand plays arriving, for this board or a
 * later one: since engine-responsiveness R3 made by the engine thread with the board, the hand plays with the board
 * published after an early one, [EngineBoardValues]) and [AFTER] (the engine thread's work after the board: the rest of the event, the
 * device's own steps and the board again; [AFTER_INFO] of it the information rule's).
 *
 * Times are nanoseconds of the game's clock. The engine thread writes the parts up to [AFTER]; the main thread the rest
 * and reads them all once the action is [complete] (or a newer action's board comes first).
 */
internal class OwnActionTiming(val what: String, private val clock: () -> Long, val trace: LatencyRecorder.Trace?) {
    val asked: Long = clock()

    // ---- Engine thread
    @Volatile var started: Long? = null
    /** The engine's work up to the board done (the information rule's [info] of it included). */
    @Volatile var worked: Long? = null
    @Volatile var info = 0L
    @Volatile var boardStart: Long? = null
    @Volatile var boardEnd: Long? = null
    /** The version of the board published for the action. */
    @Volatile var version = 0L
    /** Its board was published at its performed point, before its event was finished (`EngineGame.early`). */
    @Volatile var early = false
    @Volatile private var afterNanos = 0L
    @Volatile private var afterInfoNanos = 0L
    @Volatile var afterDone = false
        private set

    /** Engine thread: [from]–[to] was the action's work after its board, [infoNanos] of it the information rule's. */
    fun after(from: Long, to: Long, infoNanos: Long) {
        afterNanos += (to - from).coerceAtLeast(0)
        afterInfoNanos += infoNanos.coerceAtLeast(0)
    }

    /** Engine thread: the action's work after its board is all done. */
    fun afterDone() { afterDone = true }

    // ---- Main thread
    var frame: Long? = null
    var computed: Long? = null
    var plays: Long? = null
    /** The boards published from the action's board on (by identity): the computed values of any of them count. */
    val boards: MutableSet<Any> = java.util.Collections.newSetFromMap(java.util.IdentityHashMap())
    var reported = false

    fun now(): Long = clock()

    /** Every part has arrived. */
    val complete: Boolean get() = boardEnd != null && afterDone && frame != null && computed != null && plays != null

    /** The parts that have arrived, in milliseconds by their labels ([LABELS] order). */
    fun parts(): Map<String, Double> {
        val parts = LinkedHashMap<String, Double>()
        val started = started ?: return parts
        val worked = worked ?: return parts
        val boardStart = boardStart ?: return parts
        val shown = boardEnd ?: return parts
        parts[TOTAL] = ms(shown - asked)
        parts[WAIT] = ms((started - asked) + (boardStart - worked))
        parts[ENGINE] = ms(worked - started - info)
        parts[INFO] = ms(info)
        parts[BOARD] = ms(shown - boardStart)
        frame?.let { parts[FRAME] = ms(it - shown) }
        computed?.let { parts[COMPUTED] = ms(it - shown) }
        plays?.let { parts[PLAYS] = ms(it - shown) }
        if (afterDone) { parts[AFTER] = ms(afterNanos); parts[AFTER_INFO] = ms(afterInfoNanos) }
        return parts
    }

    /** The log line's fields: `what=… path=early|whole` and every label `=` milliseconds, or `-` when it never arrived. */
    fun line(parts: Map<String, Double> = parts()): String =
        "what=$what path=${if (early) "early" else "whole"} " + LABELS.joinToString(" ") { label -> "$label=${parts[label] ?: "-"}" }

    companion object {
        const val TOTAL = "totalMs"
        const val WAIT = "waitMs"
        const val ENGINE = "engineMs"
        const val INFO = "infoMs"
        const val BOARD = "boardMs"
        const val FRAME = "frameMs"
        const val COMPUTED = "computedMs"
        const val PLAYS = "playsMs"
        const val AFTER = "afterMs"
        const val AFTER_INFO = "afterInfoMs"
        val LABELS = listOf(TOTAL, WAIT, ENGINE, INFO, BOARD, FRAME, COMPUTED, PLAYS, AFTER, AFTER_INFO)

        /** Nanoseconds as milliseconds to a tenth (never negative). */
        private fun ms(nanos: Long): Double = Math.round(nanos.coerceAtLeast(0) / 1e5) / 10.0
    }
}

/**
 * The own actions still waiting for their computed board and hand plays (main thread): the table tells of each board's
 * computed values and plays as they arrive ([rememberComputedBoard]). Nothing is done while no action waits.
 */
internal object OwnActionProbes {
    private val waiting = ArrayList<Pair<OwnActionTiming, (OwnActionTiming) -> Unit>>()

    /** [timing] waits; [arrived] is told on each part that arrives. */
    fun add(timing: OwnActionTiming, arrived: (OwnActionTiming) -> Unit) {
        waiting += timing to arrived
        // A game closed while its action waited is forgotten
        while (waiting.size > MAX_WAITING) waiting.removeAt(0)
    }
    fun remove(timing: OwnActionTiming) { waiting.removeAll { it.first === timing } }

    /** [board]'s computed values (`ComputedBoard`) are ready. */
    fun computed(board: Any) = arrive(board, { it.computed == null }) { it.computed = it.now() }

    /** The player's hand plays on [board] are ready. */
    fun plays(board: Any) = arrive(board, { it.plays == null }) { it.plays = it.now() }

    private inline fun arrive(board: Any, open: (OwnActionTiming) -> Boolean, mark: (OwnActionTiming) -> Unit) {
        if (waiting.isEmpty()) return
        for ((timing, arrived) in waiting.toList()) if (open(timing) && board in timing.boards) { mark(timing); arrived(timing) }
    }

    private const val MAX_WAITING = 4
}
