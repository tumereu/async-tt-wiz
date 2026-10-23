package org.asyncttwiz.android

enum class TableMode { NORMAL, SELECT, PING }
data class TableScope(val contextId: String, val cardIds: List<String>)
data class TableMoveSource(val containerId: String, val placement: TablePlacement? = null, val libraryTop: Boolean = false,
    val sourceId: String? = null, val bottomSlice: List<String> = emptyList())

/** Personal selection. Scope capture deliberately copies IDs, never indexes or a live selection view. */
data class TableSelection(val ids: Set<String> = emptySet(), val mode: TableMode = TableMode.NORMAL) {
    fun scope(id: String, visualOrder: List<String> = ids.toList()): TableScope = TableScope(id,
        if (id in ids) (visualOrder.filter { it in ids } + ids.filter { it !in visualOrder }).distinct() else listOf(id))
    fun toggle(id: String) = copy(ids = if (id in ids) ids - id else ids + id)
    fun orientation(id: String) = if (id in ids) this else copy(ids = emptySet())
    fun afterMove(id: String) = if (id in ids) this else copy(ids = emptySet())
    fun retain(existing: Set<String>) = copy(ids = ids.intersect(existing))
}

sealed interface TableDestination {
    data class Container(val id: String, val zone: org.asyncttwiz.protocol.PlayZone,
        val bottom: Boolean = false) : TableDestination
    data class CardPile(val cardId: String, val source: TableMoveSource? = null) : TableDestination
    data class Grid(val containerId: String, val column: Int, val row: Int) : TableDestination
    data class Combat(val destination: org.asyncttwiz.protocol.CombatDestination) : TableDestination
}

/** Semantic UI intent, not a shared mutation. I16 routes gameplay through normal requests/prediction. */
sealed interface TableIntent {
    data class StackBubbles(val entryId: String?) : TableIntent
    data class StackDestination(val entryId: String, val zone: org.asyncttwiz.protocol.PlayZone) : TableIntent
    data class Actions(val itemId: String, val scope: TableScope?,
        val sources: Map<String, TableMoveSource> = emptyMap(), val topId: String? = null) : TableIntent
    data class Inspect(val cardId: String) : TableIntent
    data class Rotate(val scope: TableScope, val tapped: Boolean, val sources: Map<String, TableMoveSource> = emptyMap()) : TableIntent
    data class Move(val scope: TableScope, val destination: TableDestination,
        val sources: Map<String, TableMoveSource> = emptyMap(), val invocationId: String? = null) : TableIntent
    data class Ping(val itemId: String?, val point: TablePoint) : TableIntent
    data class PlayerDelta(val accountId: String, val color: org.asyncttwiz.protocol.CounterColor?, val delta: Int) : TableIntent
    data class Submit(val action: org.asyncttwiz.protocol.PlayAction, val invocationId: String?) : TableIntent
    data class Selection(val selection: TableSelection) : TableIntent
    data class Camera(val camera: TableCamera) : TableIntent
    data class Scroll(val viewportId: String, val offset: Float, val vertical: Boolean = false) : TableIntent
}
