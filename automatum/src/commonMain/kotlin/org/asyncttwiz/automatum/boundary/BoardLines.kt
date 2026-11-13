package org.asyncttwiz.automatum.boundary

import org.asyncttwiz.protocol.game.GameEvent

/**
 * The page's record of the line each board of a game stands on ([record]: the events from the line's start up to the
 * board), so a board goes to the engine side as the events from the board it holds ([since], [BoardTransfer.since])
 * instead of as a snapshot. Only a board whose state is exactly its line's (every event whole, nothing drafted) is
 * recorded: an own action's early board, a motion step or a view-only board never is, and goes as a snapshot.
 *
 * The [keep] newest boards are remembered. Not thread-safe: the page's one thread uses it (the browser).
 */
class BoardLines(private val keep: Int = KEEP, private val most: Int = MOST_EVENTS) {
    /** The events of a board's line after its start ([start]: the event before the first of them). */
    private class Line(val start: String, val events: List<GameEvent>)
    private val lines = LinkedHashMap<Long, Line>()

    /** Board [board] stands on [events] after [start]. A board already recorded keeps its first line (a board published
     * again is the same state). */
    fun record(board: Long, start: String, events: List<GameEvent>) {
        if (board in lines) return
        lines[board] = Line(start, events)
        while (lines.size > keep) lines.remove(lines.keys.first())
    }

    /** Whether [board]'s line is known. */
    fun knows(board: Long): Boolean = board in lines

    /** What changed from board [from] to board [to]: [revert] (the latest first) and then [apply] (in order). */
    class Between(val revert: List<GameEvent>, val apply: List<GameEvent>)

    /**
     * The events from board [from] to board [to] along their lines: those of [from]'s line after the part both share,
     * reverted, then those of [to]'s, applied. Null when either line is unknown, when they share no point (the device
     * loaded the game again, or one line's start was trimmed past the other's) or when more than [most] events would go
     * (a snapshot is then the cheaper transfer).
     */
    fun between(from: Long, to: Long): Between? {
        val a = lines[from] ?: return null
        val b = lines[to] ?: return null
        // Where both lines are at the same point: their starts, or one's start on the other's line (trimmed at another event)
        val (aAt, bAt) = when {
            a.start == b.start -> 0 to 0
            else -> a.events.indexOfFirst { it.id == b.start }.takeIf { it >= 0 }?.let { (it + 1) to 0 }
                ?: b.events.indexOfFirst { it.id == a.start }.takeIf { it >= 0 }?.let { 0 to (it + 1) }
                ?: return null
        }
        var shared = 0
        while (aAt + shared < a.events.size && bAt + shared < b.events.size && a.events[aAt + shared].id == b.events[bAt + shared].id) shared++
        val revert = a.events.subList(aAt + shared, a.events.size).asReversed()
        val apply = b.events.subList(bAt + shared, b.events.size)
        if (revert.size + apply.size > most) return null
        return Between(revert.toList(), apply.toList())
    }

    /** [between] as a transfer's events, each written by [encode] (the engine's stored format, with the codec the engine
     * side's snapshot of the game was written with). */
    fun since(from: Long, to: Long, encode: (GameEvent) -> String): BoardTransfer.Delta? =
        between(from, to)?.let { BoardTransfer.Delta(it.apply.map(encode), it.revert.map(encode)) }

    /** Forgets every board (the game closed). */
    fun clear() = lines.clear()

    companion object {
        /** The boards of a game remembered: the engine side holds one, and a few published since it are asked about. */
        const val KEEP = 64
        /** More events than this between two boards go as a snapshot. */
        const val MOST_EVENTS = 200
    }
}
