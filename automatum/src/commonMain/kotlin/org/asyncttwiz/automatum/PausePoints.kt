package org.asyncttwiz.automatum

import arcanum.engine.state.GameState
import arcanum.engine.state.PlayerId
import org.asyncttwiz.protocol.Automatum
import org.asyncttwiz.protocol.BoardState
import org.asyncttwiz.protocol.CombatStackRules
import org.asyncttwiz.protocol.TurnPosition

/**
 * Where Automatum stops for a player holding priority (integration spec I-D41, review item B): the rest of the time the
 * host passes for them by itself, so a turn has a few acting points instead of one per step for each player.
 *
 * - A **stop** ([Point.Stop]) is the player's own main phase on an empty Stack: they act in their own time.
 * - A **pause** ([Point.Pause]) is something they could react to: a spell or ability of another player on top of the
 *   Stack, the other player's attack (the defending player in the declare attackers step), blocks (both players in the
 *   declare blockers step). The player is shown it for a short time (the app's response time) and the game goes on
 *   unless they react; their own spells and every other step with priority are pauses only with their "Full control"
 *   ([org.asyncttwiz.protocol.PlayPlayer.fullControl]).
 * - Anywhere else ([of] is null) the host passes for them.
 *
 * None of this reads hidden cards (I-D27): a pause is offered whenever the other player's thing could be answered in
 * principle. Whether the player can do anything at all stays with `Engine.shouldPassFor` and their "Skip when I have no
 * choice" (I-D18), which the host asks first. Declarations and choices aren't priority and always stop.
 */
object PausePoints {
    /** What a priority holder's point is. */
    sealed interface Point {
        /** Their own main phase on an empty Stack: they act in their own time. */
        data object Stop : Point
        /** Something they could react to: shown for the response time, then passed unless they react. */
        data object Pause : Point
    }

    /**
     * What it is for [holder] to hold priority with [stackTop] (the controller of the top of the Stack; null: empty) in
     * [position] of [active]'s turn, [attacking] whether creatures attack in this combat; [fullControl] the holder's.
     * Null: the host passes for them.
     */
    fun of(holder: String, active: String, position: TurnPosition?, stackTop: String?, attacking: Boolean,
        fullControl: Boolean): Point? = when {
        stackTop != null -> if (stackTop != holder || fullControl) Point.Pause else null
        holder == active && (position == TurnPosition.PRECOMBAT_MAIN || position == TurnPosition.POSTCOMBAT_MAIN) -> Point.Stop
        // Combat that involves them: the defending player answers an attack, both players the blocks
        attacking && position == TurnPosition.DECLARE_ATTACKERS && holder != active -> Point.Pause
        attacking && position == TurnPosition.DECLARE_BLOCKERS -> Point.Pause
        fullControl -> Point.Pause
        else -> null
    }

    /** The point of [holder], who holds priority in the engine state [s]; [fullControl] the holder's. */
    fun of(s: GameState, holder: PlayerId, fullControl: Boolean): Point? = of(holder.value, s.turn.active.value,
        EngineCodec.position(s.turn.step, s.combat?.firstStrikeStep == true),
        s.stack.lastOrNull()?.let { top -> s.stackItems[top]?.controller?.value ?: "" },
        s.combat?.attackers?.isNotEmpty() == true, fullControl)

    /** A player holding priority on a board and what their point is. */
    data class At(val holder: String, val point: Point)

    /**
     * The point of whoever holds priority on [board] as Automatum left it: null when Automatum is off, a choice is open
     * (the continuation, I-D8), nobody holds priority or it's a point where the host would have passed. The same rule
     * as the host's ([of]), read from ATW's board alone (cheap: no engine state), so a client can tell a pause on any
     * board it shows, live or in the timeline.
     */
    fun at(board: BoardState): At? {
        if (!board.automatum || Automatum.flowOpen(board)) return null
        val holder = board.priority?.holder ?: return null
        val top = CombatStackRules.stack(board).cards.firstOrNull()?.let { entry ->
            board.stackEffects.firstOrNull { it.id == entry }?.let { it.item?.controller ?: it.controller ?: "" } ?: ""
        }
        val attacking = board.combat?.pairs?.any { it.attackers.isNotEmpty() } == true
        val fullControl = board.players.firstOrNull { it.accountId == holder }?.fullControl == true
        return of(holder, board.turn.accountId, TurnPosition.of(board.turn), top, attacking, fullControl)?.let { At(holder, it) }
    }

    /** The pause of [viewer] on [board] ([at]): they hold priority there at a [Point.Pause]. */
    fun pauseFor(board: BoardState, viewer: String): Boolean = at(board)?.let { it.holder == viewer && it.point == Point.Pause } == true
}
