package org.asyncttwiz.android

import androidx.compose.runtime.mutableStateMapOf
import org.asyncttwiz.protocol.*
import java.math.BigInteger

enum class TableChangeKind { LIFE, PLAYER_COUNTER, CORNER, COUNTER, NOTE, OWNER, FACE, LINK, TEXT_MARK, STEP }
data class TableChangeKey(val target: String, val kind: TableChangeKind, val color: CounterColor? = null,
    val line: Int? = null)
data class TableChange(val key: TableChangeKey, val delta: List<BigInteger> = emptyList(), val owner: String? = null,
    val link: PlayLink? = null, val projectionLink: ProjectionLink? = null)
data class TableAppliedEvent(val mutation: GameplayMutation, val reverse: Boolean = false)
data class TableChangeBatch(val events: List<TableAppliedEvent> = emptyList(), val jump: Boolean = false,
    val cursor: String? = null)

/** Only recorded tuples are inspected. No board comparisons or history-wide iteration. */
object TableChangeExtraction {
    fun extract(event: TableAppliedEvent): List<TableChange> = buildList {
        val transition = event.mutation.transition
        fun numbers(from: String?, to: String?, pair: Boolean = false): List<BigInteger> {
            fun parse(value: String?): List<BigInteger>? = (value ?: if (pair) "0/0" else "0")
                .split('/').map { it.toBigIntegerOrNull() ?: return null }
            val a = parse(if (event.reverse) to else from) ?: return emptyList()
            val b = parse(if (event.reverse) from else to) ?: return emptyList()
            return if (a.size == b.size && a.size == if (pair) 2 else 1) b.zip(a) { x, y -> x - y } else emptyList()
        }
        for (field in transition.fields) {
            if (field.from == field.to) continue
            val kind = when (field.field) {
                GameplayField.LIFE -> TableChangeKind.LIFE
                GameplayField.PLAYER_COUNTER -> TableChangeKind.PLAYER_COUNTER
                GameplayField.CORNER -> TableChangeKind.CORNER
                GameplayField.COUNTER -> TableChangeKind.COUNTER
                GameplayField.NOTE -> TableChangeKind.NOTE
                GameplayField.OWNER -> TableChangeKind.OWNER
                GameplayField.FACE, GameplayField.COVERED, GameplayField.EXPLICITLY_COVERED -> TableChangeKind.FACE
                GameplayField.PHASE, GameplayField.STEP -> TableChangeKind.STEP
                else -> continue
            }
            val numeric = kind in setOf(TableChangeKind.LIFE, TableChangeKind.PLAYER_COUNTER, TableChangeKind.CORNER, TableChangeKind.COUNTER)
            add(TableChange(TableChangeKey(if (kind == TableChangeKind.STEP) "turn" else field.targetId, kind, field.color),
                if (numeric) numbers(field.from, field.to, kind == TableChangeKind.CORNER) else emptyList(),
                owner = if (kind == TableChangeKind.OWNER) (if (event.reverse) field.from else field.to) else null))
        }
        for (link in transition.links) (if (event.reverse) link.from.takeIf { link.to == null } else link.to.takeIf { link.from == null })?.let {
            add(TableChange(TableChangeKey(it.id, TableChangeKind.LINK), link = it))
        }
        transition.combatStack?.let { combat ->
            for (link in if (event.reverse) combat.removedLinks else combat.addedLinks)
                add(TableChange(TableChangeKey(link.id, TableChangeKind.LINK), projectionLink = link))
            for (effect in combat.effects) {
                if (effect.from != null && effect.to != null) {
                    if (effect.from!!.controller != effect.to!!.controller) add(TableChange(TableChangeKey(effect.id, TableChangeKind.OWNER),
                        owner = (if (event.reverse) effect.from else effect.to)?.controller))
                    if (effect.from!!.note != effect.to!!.note) add(TableChange(TableChangeKey(effect.id, TableChangeKind.NOTE)))
                }
            }
            for (occurrence in combat.occurrences) if (occurrence.from != null && occurrence.to != null &&
                occurrence.from!!.occurrence.note != occurrence.to!!.occurrence.note)
                add(TableChange(TableChangeKey(occurrence.id, TableChangeKind.NOTE)))
            for (mark in combat.textMarks) for (line in mark.from.orEmpty() union mark.to.orEmpty())
                if ((line in mark.from.orEmpty()) != (line in mark.to.orEmpty()))
                    add(TableChange(TableChangeKey(mark.entryId, TableChangeKind.TEXT_MARK, line = line)))
        }
    }.distinctBy { it.key }

    /** A fixed bound also covers coalesced live publications. Bulk inverse paths are jumps. */
    fun between(from: String?, to: String, nodes: Map<String, GameplayMutation>,
        previousNodes: Map<String, GameplayMutation> = nodes): TableChangeBatch {
        if (from == to) return TableChangeBatch()
        if (from == null) return TableChangeBatch(jump = true)
        previousNodes[from]?.takeIf { it.parentId == to }?.let { return TableChangeBatch(listOf(TableAppliedEvent(it, true))) }
        var cursor = to
        val events = ArrayList<TableAppliedEvent>()
        repeat(256) {
            val node = nodes[cursor] ?: return TableChangeBatch(jump = true)
            events.add(TableAppliedEvent(node)); cursor = node.parentId
            if (cursor == from) return TableChangeBatch(events.asReversed())
        }
        return TableChangeBatch(jump = true)
    }
}

data class ActiveTableChange(val change: TableChange, val started: Long, val serial: Long) {
    val tag: String get() = change.delta.joinToString("/") {
        when { it.signum() < 0 -> it.toString()
            it.signum() == 0 && change.delta.any { n -> n.signum() < 0 } -> "-0"
            else -> "+$it" }
    }
}

/** Table-lifetime UI state; the clock is sampled on events, never by board composition. */
class TableChanges(private val now: () -> Long = { System.nanoTime() / 1_000_000 }) {
    internal val feedback = TableEventFeedback(now)
    val active = mutableStateMapOf<TableChangeKey, ActiveTableChange>()
    private var motionCursor: String? = null
    internal fun animates(cursor: String): Boolean = motionCursor == cursor
    private var serial = 0L
    fun apply(batch: TableChangeBatch) {
        feedback.apply(batch)
        if (batch.jump) { clear(); return }
        batch.events.lastOrNull()?.let { motionCursor = if (it.reverse) it.mutation.parentId else it.mutation.id }
        val time = now()
        active.entries.removeAll { time - it.value.started >= WINDOW_MS }
        for (event in batch.events) for (change in TableChangeExtraction.extract(event)) {
            val old = active[change.key]?.takeIf { time - it.started < WINDOW_MS }
            val delta = if (old != null && old.change.delta.size == change.delta.size)
                old.change.delta.zip(change.delta) { a, b -> a + b } else change.delta
            active[change.key] = ActiveTableChange(change.copy(delta = delta), time, ++serial)
        }
    }
    fun expire(key: TableChangeKey, serial: Long) { if (active[key]?.serial == serial) active.remove(key) }
    fun clear() { active.clear(); motionCursor = null; feedback.clear() }
    companion object {
        const val HOLD_MS = 1_000L
        const val WINDOW_MS = 1_500L
    }
}
