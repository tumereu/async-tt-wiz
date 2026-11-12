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
 * Every engine reader of a board ([org.asyncttwiz.protocol.BoardState.engine]) takes its state from here ([copy]): the
 * full state, or as a player sees it, every object they can't see an unknown object (D60), exactly what those readers
 * saw when the state was built from the board.
 */
class EngineView(state: GameState, val table: TableState, val viewer: PlayerId, visible: Set<ObjectId>? = null) {
    /** The full information state (a snapshot: never changed). */
    val state: GameState = state.deepCopy()
    private var seen: GameState? = null

    /** A copy of the state: full information ([player] null), or as [player] sees it. */
    fun copy(player: PlayerId? = null): GameState {
        if (player == null) return state.deepCopy()
        if (player == viewer) seen?.let { return it.deepCopy() }
        val visible = if (player == viewer) visibleIds else Host.visible(state, player)
        val shown = Host.seenBy(state, player, visible)
        if (player == viewer) seen = shown
        return shown.deepCopy()
    }

    /** Whether [viewer] may see [id] (I-D27 display rule). */
    fun visible(id: ObjectId): Boolean = id in visibleIds
    private val visibleIds: Set<ObjectId> by lazy { visible ?: Host.visible(state, viewer) }
}
