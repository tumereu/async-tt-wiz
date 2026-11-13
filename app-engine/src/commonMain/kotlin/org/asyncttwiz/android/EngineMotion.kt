package org.asyncttwiz.android

import arcanum.engine.state.GameState
import org.asyncttwiz.protocol.BoardState

/**
 * TBL-040 on engine state (owner playtest 2026-11-04): what one published board change shows in order. An own action
 * and what follows it by itself (the passes, the spell resolving, its triggers and their tokens) arrive as one board;
 * its [EngineGame.Transition.steps] are the boards in between, which the table plays one after another, each as long as
 * its own animation (the card move speed, TBL-038) and no playback pause, so every card goes to its place in its turn.
 */
object EngineMotion {
    /** At most this many boards in between (the last ones): a long chain shows its end. */
    const val MAX_STEPS = 8
    /** Steps are looked for in the last this many events only (each pass is an event of its own). */
    const val MAX_EVENTS = 64

    /** What the table draws of [state]'s objects (where each is, tapped, face down, its face, controller), the Stack and
     * the attackers (HIS-113): a step that changes none of it shows nothing new. */
    fun objects(state: GameState): Any = Triple(state.objects.values.mapTo(HashSet()) {
        listOf(it.id, it.zone, it.tapped, it.faceDown, it.face, it.controller)
    }, state.stack.toList(), state.combat?.attackers?.map { it.id })

    /** What moves on [board]: each card's container, place, tapped, covered and face, the Stack's entries and the attackers
     * in the combat area. */
    fun key(board: BoardState): List<Any?> = listOf(board.cards.associate {
        it.id to listOf(it.containerId, it.placement?.position, it.tapped, it.covered, it.faceId)
    }, board.stackEffects.map { it.id }, attackers(board))

    /**
     * HIS-113 (owner playtest 2026-11-09): whether [change], made after a combat damage step's damage event, is no longer
     * that damage's result (damage marked, life, counters, what the damage remembers) but what follows it: state-based
     * actions moving or removing objects (the creatures it destroys dying), a player losing, triggers, the next step or
     * priority. The damage's step shows before it.
     */
    fun afterDamage(change: arcanum.engine.change.Change): Boolean = when (change) {
        is arcanum.engine.change.Change.ZoneChange, is arcanum.engine.change.Change.Removed, is arcanum.engine.change.Change.Created,
        is arcanum.engine.change.Change.Lost, is arcanum.engine.change.Change.StackItemAdded, is arcanum.engine.change.Change.Step,
        is arcanum.engine.change.Change.Priority, is arcanum.engine.change.Change.PendingSet, is arcanum.engine.change.Change.Combat -> true
        else -> false
    }

    /** The attackers in [board]'s combat area. */
    fun attackers(board: BoardState): List<String> = board.combat?.pairs.orEmpty().flatMap { pair -> pair.attackers.map { it.sourceId } }

    /**
     * The [steps] worth showing between [before] (shown before) and [after] (the last): none that looks like the board
     * before it, none that looks like the last. With [shownFirst] (the board the action already published at once,
     * [EngineGame]'s preview) the steps start with it (drawn already, it settles at once; else it shows first) and go on
     * after the step that looks like it (only it when none does: never back to an older look). A combat's damage step
     * ([TableBoard.damageDealt], HIS-113) stays even when it moves nothing, unless it looks like the last; it always comes
     * after [shownFirst] (the caller leaves out one the action's own step dealt), so the step looking like it is looked for
     * only before it, and with none the steps go on from it.
     */
    fun trim(steps: List<TableBoard>, before: TableBoard?, after: TableBoard, shownFirst: TableBoard?): List<TableBoard> {
        val keys = steps.map { key(it.board) }
        val candidates = if (shownFirst == null) steps else {
            val damage = steps.indexOfFirst { it.damageDealt }.let { if (it < 0) steps.size else it }
            val at = keys.subList(0, damage).lastIndexOf(key(shownFirst.board))
            listOf(shownFirst) + steps.drop(if (at < 0) damage else at + 1)
        }
        val kept = ArrayList<TableBoard>()
        var last = before?.let { key(it.board) }
        val end = key(after.board)
        for (step in candidates) {
            val k = key(step.board)
            if (k == end || (k == last && !step.damageDealt)) continue
            kept += step
            last = k
        }
        return kept
    }
}
