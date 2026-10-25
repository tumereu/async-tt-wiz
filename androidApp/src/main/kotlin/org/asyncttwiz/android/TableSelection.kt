package org.asyncttwiz.android

enum class TableMode { NORMAL, SELECT, PING;
    fun next(pingAvailable: Boolean) = when (this) {
        NORMAL -> SELECT
        SELECT -> if (pingAvailable) PING else NORMAL
        PING -> NORMAL
    }
}
data class TableScope(val contextId: String, val cardIds: List<String>)
data class TableMoveSource(val containerId: String, val placement: TablePlacement? = null, val libraryTop: Boolean = false,
    val sourceId: String? = null, val bottomSlice: List<String> = emptyList())

/** Personal selection. Scope capture deliberately copies IDs, never indexes or a live selection view. */
data class TableSelection(val ids: Set<String> = emptySet(), val mode: TableMode = TableMode.NORMAL,
    val attackSelection: Boolean = false) {
    fun scope(id: String, visualOrder: List<String> = ids.toList()): TableScope = TableScope(id,
        if (id in ids) (visualOrder.filter { it in ids } + ids.filter { it !in visualOrder }).distinct() else listOf(id))
    fun toggle(id: String) = copy(ids = if (id in ids) ids - id else ids + id)
    fun orientation(id: String) = if (id in ids) this else copy(ids = emptySet())
    fun afterDoubleTap(id: String) = if (mode != TableMode.SELECT) orientation(id)
        else if (id in ids) copy(ids = emptySet(), mode = TableMode.NORMAL, attackSelection = false) else this
    fun cycleMode(pingAvailable: Boolean) = copy(mode = mode.next(pingAvailable), attackSelection = false)
    fun selectAttackers() = copy(mode = TableMode.SELECT, attackSelection = attackSelection || mode != TableMode.SELECT)
    fun afterDeclare() = copy(ids = emptySet(), mode = TableMode.NORMAL, attackSelection = false)
    fun atStep(position: org.asyncttwiz.protocol.TurnPosition?) =
        if (attackSelection && position != org.asyncttwiz.protocol.TurnPosition.DECLARE_ATTACKERS)
            copy(mode = TableMode.NORMAL, attackSelection = false) else this
    fun afterMove(id: String) = if (id in ids) this else copy(ids = emptySet())
    fun retain(existing: Set<String>) = copy(ids = ids.intersect(existing))
}

sealed interface TableDestination {
    data class Container(val id: String, val zone: org.asyncttwiz.protocol.PlayZone,
        val bottom: Boolean = false, val handGap: org.asyncttwiz.protocol.IdentityGap? = null) : TableDestination
    data class CardPile(val cardId: String, val source: TableMoveSource? = null) : TableDestination
    data class Grid(val containerId: String, val column: Int, val row: Int, val ownerOnly: Boolean = false) : TableDestination
    data class Combat(val destination: org.asyncttwiz.protocol.CombatDestination) : TableDestination
}

/** Semantic UI intent, not a shared mutation. I16 routes gameplay through normal requests/prediction. */
sealed interface TableIntent {
    data class CombatBubbles(val occurrenceId: String?) : TableIntent
    data class StackBubbles(val entryId: String?) : TableIntent
    data class StackDestination(val entryId: String, val zone: org.asyncttwiz.protocol.PlayZone,
        val fromBubble: Boolean = false) : TableIntent
    data class StackBubbleRemove(val entryId: String) : TableIntent
    data class Actions(val itemId: String, val scope: TableScope?,
        val sources: Map<String, TableMoveSource> = emptyMap(), val topId: String? = null) : TableIntent
    data class Inspect(val cardId: String) : TableIntent
    data class Peek(val cardId: String?) : TableIntent
    data object DismissTapUi : TableIntent
    data class Draw(val libraryId: String, val handId: String, val topId: String) : TableIntent
    data class Rotate(val scope: TableScope, val tapped: Boolean, val sources: Map<String, TableMoveSource> = emptyMap(),
        val toggle: Boolean = false) : TableIntent
    data class Move(val scope: TableScope, val destination: TableDestination,
        val sources: Map<String, TableMoveSource> = emptyMap(), val invocationId: String? = null) : TableIntent
    data class Ping(val itemId: String?, val point: TablePoint) : TableIntent
    data class PlayerDelta(val accountId: String, val color: org.asyncttwiz.protocol.CounterColor?, val delta: Int) : TableIntent
    data class Submit(val action: org.asyncttwiz.protocol.PlayAction, val invocationId: String?) : TableIntent
    data class Selection(val selection: TableSelection) : TableIntent
    data class Camera(val camera: TableCamera) : TableIntent
    data class Scroll(val viewportId: String, val offset: Float, val vertical: Boolean = false) : TableIntent
}

/** Only explicit bubble actions on the captured top may advance personal focus. */
internal fun stackBubbleAdvanceEntry(intent: TableIntent, board: org.asyncttwiz.protocol.BoardState): String? {
    val entry = when (intent) {
        is TableIntent.StackDestination -> intent.entryId.takeIf { intent.fromBubble }
        is TableIntent.StackBubbleRemove -> intent.entryId
        else -> null
    }
    return entry?.takeIf { it == org.asyncttwiz.protocol.CombatStackRules.stack(board).cards.firstOrNull() }
}
