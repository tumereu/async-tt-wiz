package org.asyncttwiz.android

import androidx.compose.runtime.*
import org.asyncttwiz.protocol.*

internal data class TableRandomResult(val nodeId: String, val author: String, val tool: RandomTool, val label: String)
internal data class TimedTableResult(val result: TableRandomResult, val started: Long, val serial: Long)
internal data class LingeringTablePing(val record: RecordedPingChange, val started: Long, val serial: Long)

/** Previous-group transport is a normal step, not a jump, even when it unapplies several nodes. */
internal fun tablePlaybackChanges(from: String, to: String, index: HistoryTree): TableChangeBatch {
    if (index.depth(from) <= index.depth(to)) return TableChangeExtraction.between(from, to, index.nodes)
    val events = ArrayList<TableAppliedEvent>()
    var cursor = from
    repeat(256) {
        val node = index.nodes[cursor] ?: return TableChangeBatch(jump = true)
        events.add(TableAppliedEvent(node, reverse = true))
        cursor = node.parentId
        if (cursor == to) return TableChangeBatch(events)
    }
    return TableChangeBatch(jump = true)
}

/** The coin result everywhere it is shown: which face, and what it means for the flipper. */
internal fun coinResultText(face: CoinFace) = when (face) {
    CoinFace.HEADS -> "Heads / win"
    CoinFace.TAILS -> "Tails / loss"
}

internal fun tableRandomResult(node: GameplayMutation): TableRandomResult? = when (val action = node.action) {
    is PlayAction.RollDie -> TableRandomResult(node.id, node.author, RandomTool.DIE, "d${action.sides} → ${action.value}")
    is PlayAction.FlipCoin -> TableRandomResult(node.id, node.author, RandomTool.COIN, coinResultText(action.value))
    else -> null
}

/** Display-only windows. Neither these records nor their ordinals are used by annotation input. */
internal class TableEventFeedback(private val now: () -> Long = { System.nanoTime() / 1_000_000 }) {
    val rolls = mutableStateMapOf<Long, TimedTableResult>()
    val pings = mutableStateMapOf<Long, LingeringTablePing>()
    var stoppedRolls by mutableStateOf<List<TableRandomResult>>(emptyList()); private set
    var isStopped by mutableStateOf(false); private set
    private val projection = ActivePingProjection()
    private var group: String? = null
    private var previousPings = emptyList<RecordedPingChange>()
    private var stoppedNode: GameplayMutation? = null
    private var presentedNode: String? = null
    /** The endpoint shown when a jump arrived; it may be presented again while the target loads. */
    private var staleNode: String? = null
    private var serial = 0L

    fun elapsed(started: Long) = (now() - started).coerceAtLeast(0)
    fun apply(batch: TableChangeBatch) {
        if (batch.jump) { clear(); return }
        prune()
        for (event in batch.events) if (!event.reverse) tableRandomResult(event.mutation)?.let {
            rolls[++serial] = TimedTableResult(it, now(), serial)
        }
        // A fixed visible window, even if publications are unusually dense.
        while (rolls.size > MAX_RESULTS) rolls.remove(rolls.keys.min())
    }

    /** Called at the same displayed endpoint as change animations, after draining applied batches.
     * A jump clears the previous group snapshot, so it can never create a lingering ring. */
    fun present(play: PlayState, stopped: Boolean, nodes: Map<String, GameplayMutation>) {
        prune()
        isStopped = stopped
        val nextGroup = play.currentGroup?.id
        val stale = staleNode != null && staleNode == play.currentNodeId
        if (!stale) staleNode = null
        if (group != nextGroup && !stale) {
            val time = now()
            for (record in previousPings) pings[++serial] = LingeringTablePing(record, time, serial)
        }
        group = nextGroup
        previousPings = if (stale) emptyList() else projection.records(play)
        presentedNode = play.currentNodeId
        // Re-entering a group restores its real, numbered input markers; don't draw a ghost too.
        pings.entries.removeAll { it.value.record.recording.groupId == nextGroup }
        val endpoint = if (stopped) nodes[play.currentNodeId] else null
        if (endpoint !== stoppedNode) {
            stoppedNode = endpoint
            stoppedRolls = if (endpoint == null) emptyList() else groupRolls(play, nodes)
        }
    }

    /** Only the visible tail of this group is read. Never walk a path to root or scan the tree. */
    private fun groupRolls(play: PlayState, nodes: Map<String, GameplayMutation>): List<TableRandomResult> {
        val current = play.currentGroup ?: return emptyList()
        val results = ArrayList<TableRandomResult>()
        var cursor = play.currentNodeId
        repeat(MAX_GROUP_READS) {
            val node = nodes[cursor] ?: return results.asReversed()
            tableRandomResult(node)?.let { results.add(it) }
            if (node.id == current.firstNodeId || results.size == MAX_RESULTS) return results.asReversed()
            cursor = node.parentId
        }
        return results.asReversed()
    }

    private fun prune() {
        rolls.entries.removeAll { elapsed(it.value.started) >= WINDOW_MS }
        pings.entries.removeAll { elapsed(it.value.started) >= WINDOW_MS }
    }
    fun expireRoll(serial: Long) { rolls.remove(serial) }
    fun expirePing(serial: Long) { pings.remove(serial) }
    fun clear() {
        rolls.clear(); pings.clear(); stoppedRolls = emptyList(); isStopped = false
        group = null; previousPings = emptyList(); stoppedNode = null
        staleNode = presentedNode
    }
    companion object {
        const val HOLD_MS = 2_500L
        const val WINDOW_MS = 3_000L
        const val MAX_RESULTS = 32
        const val MAX_GROUP_READS = 256
        fun alpha(elapsed: Float): Float = ((WINDOW_MS - elapsed) / (WINDOW_MS - HOLD_MS)).coerceIn(0f, 1f)
    }
}
