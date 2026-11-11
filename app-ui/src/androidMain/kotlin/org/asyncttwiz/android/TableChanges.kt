package org.asyncttwiz.android

import androidx.compose.runtime.mutableStateMapOf
import org.asyncttwiz.protocol.*
import java.math.BigInteger

enum class TableChangeKind { LIFE, PLAYER_COUNTER, CORNER, COUNTER, NOTE, OWNER, FACE, LINK, TEXT_MARK, STEP }
data class TableChangeKey(val target: String, val kind: TableChangeKind, val color: CounterColor? = null,
    val line: Int? = null)
data class TableChange(val key: TableChangeKey, val delta: List<BigInteger> = emptyList(), val owner: String? = null,
    val link: PlayLink? = null, val projectionLink: ProjectionLink? = null)
data class ActiveTableChange(val change: TableChange, val started: Long, val serial: Long) {
    val tag: String get() = change.delta.joinToString("/") {
        when { it.signum() < 0 -> it.toString()
            it.signum() == 0 && change.delta.any { n -> n.signum() < 0 } -> "-0"
            else -> "+$it" }
    }
}

/** Table-lifetime UI state; the clock is sampled on events, never by board composition. */
class TableChanges(private val now: () -> Long = { System.nanoTime() / 1_000_000 }) {
    val active = mutableStateMapOf<TableChangeKey, ActiveTableChange>()
    private var motionCursor: String? = null
    private var shuffledLibraries: Set<String> = emptySet()
    fun shuffles(cursor: String): Set<String> = if (animates(cursor)) shuffledLibraries else emptySet()
    fun animates(cursor: String): Boolean = motionCursor == cursor
    private var serial = 0L
    /** On engine state (I-D56 step D): [changes] the shown events made, worked out from the engine's own changes; the
     * board at [cursor] animates (its card motion, and the [shuffled] libraries); a [jump] (a load) clears instead. */
    fun applyEngine(cursor: String, jump: Boolean, changes: List<TableChange>, shuffled: Set<String>) {
        if (jump) { clear(); motionCursor = null; return }
        motionCursor = cursor
        shuffledLibraries = shuffled
        val time = now()
        active.entries.removeAll { time - it.value.started >= WINDOW_MS }
        for (change in changes) {
            val old = active[change.key]?.takeIf { time - it.started < WINDOW_MS }
            val delta = if (old != null && old.change.delta.size == change.delta.size)
                old.change.delta.zip(change.delta) { a, b -> a + b } else change.delta
            active[change.key] = ActiveTableChange(change.copy(delta = delta), time, ++serial)
        }
    }
    fun expire(key: TableChangeKey, serial: Long) { if (active[key]?.serial == serial) active.remove(key) }
    fun clear() { active.clear(); motionCursor = null; shuffledLibraries = emptySet() }
    companion object {
        const val HOLD_MS = 1_000L
        const val WINDOW_MS = 1_500L
    }
}
