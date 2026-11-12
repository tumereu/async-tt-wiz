package org.asyncttwiz.android

data class TableScope(val contextId: String, val cardIds: List<String>)
data class TableMoveSource(val containerId: String, val placement: TablePlacement? = null, val libraryTop: Boolean = false,
    val sourceId: String? = null, val bottomSlice: List<String> = emptyList(), val takeFromEdge: Boolean = false)

sealed interface TableDestination {
    data class Container(val id: String, val zone: org.asyncttwiz.protocol.PlayZone,
        val bottom: Boolean = false, val handGap: org.asyncttwiz.protocol.IdentityGap? = null) : TableDestination
    data class CardPile(val cardId: String, val source: TableMoveSource? = null) : TableDestination
    /** [auto]: a menu move without a chosen cell; each card takes the first free cell of its own normal row
     * ([org.asyncttwiz.protocol.BattlefieldPlacement]) and [column]/[row] are ignored. */
    data class Grid(val containerId: String, val column: Int, val row: Int, val ownerOnly: Boolean = false,
        val auto: Boolean = false) : TableDestination {
        companion object {
            /** "Battlefield" from a menu: the card owners' Battlefields, each card on its normal row. */
            fun auto(containerId: String) = Grid(containerId, 0, 0, ownerOnly = true, auto = true)
        }
    }
    data class Combat(val destination: org.asyncttwiz.protocol.CombatDestination) : TableDestination
}

/** Semantic UI intent, not a shared mutation. I16 routes gameplay through normal requests/prediction. */
sealed interface TableIntent {
    data class Batch(val intents: List<TableIntent>, val invocationId: String) : TableIntent
    data class Actions(val itemId: String, val scope: TableScope?,
        val sources: Map<String, TableMoveSource> = emptyMap(), val topId: String? = null) : TableIntent
    /** Inspect [cardId] while held; null closes it. [released]: closed by the holding finger lifting (a pinned Inspect,
     * TBL-104, stays open then), not by a cancel. */
    data class Peek(val cardId: String?, val released: Boolean = false) : TableIntent
    data object DismissTapUi : TableIntent
    /** Personal: open the search panel of a Graveyard/Exile pile (its double-tap). */
    data class SearchPile(val containerId: String) : TableIntent
    data class Move(val scope: TableScope, val destination: TableDestination,
        val sources: Map<String, TableMoveSource> = emptyMap(), val invocationId: String? = null,
        val handDoubleTap: Boolean = false,
        /** Made by dragging (or its accessibility action): with Automatum on it means an engine action (I-D36). */
        val drag: Boolean = false,
        /** ZON-079: dragged from a card of the hand row from another zone (not its pile or panel). */
        val fromHandRow: Boolean = false) : TableIntent
    data class Submit(val action: org.asyncttwiz.protocol.PlayAction, val invocationId: String?) : TableIntent
    /** A tap on empty table or hand space: lowers a raised hand card and drops the combat selection. */
    data object EmptyTap : TableIntent
    data class Camera(val camera: TableCamera) : TableIntent
    data class Scroll(val viewportId: String, val offset: Float, val vertical: Boolean = false) : TableIntent
    /** ZON-081 (I-D50): a hand shown [offset] dp past its end (elastic; 0 once it is back). */
    data class Overscroll(val viewportId: String, val offset: Float) : TableIntent
}
