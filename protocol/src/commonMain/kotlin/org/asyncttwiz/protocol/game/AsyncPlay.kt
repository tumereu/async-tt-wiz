package org.asyncttwiz.protocol.game

import arcanum.engine.Engine
import arcanum.engine.change.Changes
import arcanum.engine.input.Input
import arcanum.engine.state.GameState
import arcanum.engine.state.PlayerId

/**
 * Where a player's playback stops next (I-D56 §7): the first point after their local head where it would stop, worked
 * out with their private response mode. Everything shown to a player about the game's progress (their move, the move
 * pill, the game list, notifications and reminders) comes from their local head and this; nothing about the line
 * beyond it.
 */
object NextStop {
    /** The stop at event [at] (the point the table is live at); [event]: the assumed input about the player that waits
     * there for their answer, null for a real wait at the head. */
    data class Stop(val at: String, val event: String?)

    /**
     * [player]'s next stop: [model] is the state at their local head [localHead] (used up), [after] the main line's
     * events after it. Playback stops at an assumed input about them where [mode] stops (assumed blocks and keeps
     * always), at the game's result when it lies past their local head (they must see it before it is final, §11,
     * TRN-065: so it is their move and they are told), and at the head when the game waits for them there (in the
     * opening also their own mulligan declared alongside the other player's, TRN-027).
     */
    fun of(model: GameModel, localHead: String, after: List<GameEvent>, player: PlayerId, mode: ResponseMode): Stop? {
        var at = localHead
        for (event in after) {
            val step = event.assumed
            val input = step?.input
            if (input != null && step.assumedBy != player && aboutPlayer(model.engine, input, step.assumedBy, player) &&
                stopsAt(model.engine, input, player, mode)) return Stop(at, event.id)
            model.apply(event)
            at = event.id
            if (Engine.result(model.engine) != null) return Stop(at, null)
        }
        return if (Attention.of(model.engine) == player || declares(model.engine, player)) Stop(at, null) else null
    }

    /**
     * Whether the game waits for [player] (I-D57, async-play.md §24), with [stop] their next stop: the game can't go on
     * without them, so they are told "Your move" and reminded. That is when [head] (the main line's head) is their
     * decision (their turn's stops, a choice only they make, an information-rule stop, a wait instead of an assumption,
     * or the other player's line running out without them: all leave the head at their decision), their opening
     * declaration, or the game's result they haven't seen yet. Not while the other player is still playing on
     * assumptions about them (the head is then the other player's), however the head came about (play, branch, rewind).
     */
    fun waitsFor(head: GameState, player: PlayerId, stop: Stop?): Boolean =
        stop != null && (Engine.result(head) != null || Attention.of(head) == player || declares(head, player))

    /** Whether [player] may declare their opening mulligan (or bottom choice) on [state] while the other player's is
     * the open decision (simultaneous mulligans, I-D54 §8): their move too. */
    fun declares(state: GameState, player: PlayerId): Boolean =
        state.turn.number == 0 && state.pending != null && Engine.options(state, player).any { it is Input.SetupAnswer && it.player == player }

    private fun aboutPlayer(state: GameState, input: Input, assumer: PlayerId?, player: PlayerId): Boolean =
        (WhoActs.playerOf(state, input) ?: state.players.firstOrNull { it.id != assumer }?.id) == player

    /**
     * Whether [player]'s playback stops at their assumed [input] on [state] in [mode] (I-D56 §7): assumed blocks and
     * keeps always; an assumed pass with Full control always, with Normal at their pauses, with Auto-skip at the pauses
     * where they could respond, judged with their own cards (their device's judgement: "Skip when I have no choice" on).
     */
    fun stopsAt(state: GameState, input: Input, player: PlayerId, mode: ResponseMode): Boolean {
        if (input !is Input.PassPriority) return true
        return when (mode) {
            ResponseMode.FullControl -> true
            ResponseMode.Normal -> Stops.pause(state, player)
            ResponseMode.AutoSkip -> Stops.pause(state, player) && !passesWithOwnCards(state, player)
        }
    }

    /** Whether [player]'s own device passes for them on [state] under Auto-skip: [Engine.shouldPassFor] with their
     * "Skip when I have no choice" on (the shared state keeps it off). */
    fun passesWithOwnCards(state: GameState, player: PlayerId): Boolean {
        val on = Changes.skipWhenNoChoice(state, player, true)
        state.apply(on)
        try { return Engine.shouldPassFor(state, player) } finally { state.revert(on) }
    }
}

/**
 * Undo and rewinds (I-D56 §10): taking the main line back to a target is free when everything after it is the player's
 * own (real inputs, or assumptions not yet confirmed) or the other player's table-only events the undo rule lets stand,
 * and none of it showed the player something new; otherwise it needs the other player's approval (an undo request).
 * Every rewind deletes the events after the target and starts a new line (§9).
 */
object Rewind {
    /**
     * Why rewinding to the point after [target] needs the other player's approval, or null when it is free: [model] is
     * the state at [target] (used up), [after] the main line's events after it, [others] the other players' local
     * heads.
     */
    fun requestNeeded(model: GameModel, target: String, after: List<GameEvent>, player: PlayerId, others: List<String>): String? {
        // The index in [after] of the last event another player has seen (−1: none)
        val seen = others.maxOfOrNull { head -> if (head == target) -1 else after.indexOfFirst { it.id == head } } ?: -1
        val undone = mutableListOf<GameEvent>()
        for ((i, event) in after.withIndex()) {
            if (event.author != player.value) {
                if (event.engine != null) return "otherPlayersInput"
                if (undone.any { Concerns.of(it).blockedBy(Concerns.of(event)) }) return "otherPlayersAction"
                model.apply(event)
                continue
            }
            if (event.assumed != null && i <= seen) return "confirmed"
            val engine = event.engine
            if (engine != null) {
                // Your own library counts here: a draw, a look, a scry, a search are something new
                if (Engine.revealed(model.engine, engine.changes, player).isNotEmpty()) return "shown"
                // Also in an event that starts a line (a branch whose outcome flips a coin): every rewind starts a new
                // line, so a free undo past a seen result would roll it again (§9, §10)
                if (event.table.any { it is TableChange.LineMoved } && InformationRule.randomResult(event, model.engine)) return "random"
            }
            model.apply(event)
            // A wait for the other player's decision whose source you can't see (§5's hidden-source tell)
            val d = model.engine.pending?.decision
            if (d != null && Engine.decider(model.engine, d.player) != player && d.source != null &&
                d.source !in Engine.visible(model.engine, player)) return "hiddenSource"
            undone += event
        }
        return null
    }

    /** The other players' table-only events among [after] that a rewind by [player] keeps: added again at the new head
     * while they still apply there. */
    fun standing(after: List<GameEvent>, player: PlayerId): List<GameEvent> =
        after.filter { it.author != player.value && it.engine == null && it.table.none { c -> c is TableChange.LineMoved } }

}

/** The point a player would resume a game at, as a notification names it (I-D57): the turn and step of [state], the state
 * at their own local head. */
object ResumePoints {
    fun of(state: GameState, viewer: PlayerId): org.asyncttwiz.protocol.ResumePoint =
        if (state.turn.number == 0) org.asyncttwiz.protocol.ResumePoint(0, false)
        else org.asyncttwiz.protocol.ResumePoint(state.turn.number, state.turn.active == viewer, position(state.turn.step))

    private fun position(step: arcanum.api.TurnStep): org.asyncttwiz.protocol.TurnPosition = when (step) {
        arcanum.api.TurnStep.Untap -> org.asyncttwiz.protocol.TurnPosition.UNTAP
        arcanum.api.TurnStep.Upkeep -> org.asyncttwiz.protocol.TurnPosition.UPKEEP
        arcanum.api.TurnStep.Draw -> org.asyncttwiz.protocol.TurnPosition.DRAW
        arcanum.api.TurnStep.PrecombatMain -> org.asyncttwiz.protocol.TurnPosition.PRECOMBAT_MAIN
        arcanum.api.TurnStep.BeginningOfCombat -> org.asyncttwiz.protocol.TurnPosition.BEGIN_COMBAT
        arcanum.api.TurnStep.DeclareAttackers -> org.asyncttwiz.protocol.TurnPosition.DECLARE_ATTACKERS
        arcanum.api.TurnStep.DeclareBlockers -> org.asyncttwiz.protocol.TurnPosition.DECLARE_BLOCKERS
        arcanum.api.TurnStep.CombatDamage -> org.asyncttwiz.protocol.TurnPosition.COMBAT_DAMAGE
        arcanum.api.TurnStep.EndOfCombat -> org.asyncttwiz.protocol.TurnPosition.END_COMBAT
        arcanum.api.TurnStep.PostcombatMain -> org.asyncttwiz.protocol.TurnPosition.POSTCOMBAT_MAIN
        arcanum.api.TurnStep.End -> org.asyncttwiz.protocol.TurnPosition.END_STEP
        arcanum.api.TurnStep.Cleanup -> org.asyncttwiz.protocol.TurnPosition.CLEANUP
    }
}
