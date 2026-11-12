package org.asyncttwiz.protocol.game

import arcanum.engine.Host
import arcanum.engine.state.GameState
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId

/**
 * The engine state behind a board shown for a game played on engine state (I-D54 item 2, I-D56 step C): a snapshot of
 * the full information [state] and the [table] state at the shown point, and who looks at it ([viewer]; I-D27: what is
 * shown comes from [Host.visible]; [visible], when given, is what it gives [viewer] on [state] as it is now, so it isn't
 * worked out again). Equal only to itself.
 *
 * Every engine reader of a board ([org.asyncttwiz.protocol.BoardState.engine]) takes its state from here: the full
 * state, of which a player is shown only what [visible] allows (hidden information is display).
 */
class EngineView(state: GameState, val table: TableState, val viewer: PlayerId, visible: Set<ObjectId>? = null) {
    /** The full information state (a snapshot: never changed). */
    val state: GameState = state.deepCopy()

    /** A copy of the full information state. */
    fun copy(): GameState = state.deepCopy()

    /** Whether [viewer] may see [id] (I-D27 display rule). */
    fun visible(id: ObjectId): Boolean = id in visibleIds
    private val visibleIds: Set<ObjectId> by lazy { visible ?: Host.visible(state, viewer) }
}
