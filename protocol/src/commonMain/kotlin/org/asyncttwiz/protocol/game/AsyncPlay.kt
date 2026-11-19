package org.asyncttwiz.protocol.game

import arcanum.engine.Engine
import arcanum.engine.Host
import arcanum.engine.input.Input
import arcanum.engine.state.GameState
import arcanum.engine.state.PlayerId

/**
 * Where a player's playback stops next (I-D56 §7): the first point after their local head where it would stop, worked
 * out with their response mode. Where playback stops and what notifications name come from their local head and
 * this; nothing about the line beyond it. Whose move it is (the game list, the move pill and bar colours, notifications
 * and reminders) is [waitsFor]: the player the game really waits for (TRN-058, I-D57).
 */
object NextStop {
    /** The stop at event [at] (the point the table is live at); [event]: the assumed input about the player that waits
     * there for their answer, null for a real wait at the head. */
    data class Stop(val at: String, val event: String?)

    /**
     * [player]'s next stop: [model] is the state at their local head [localHead] (used up), [after] the main line's
     * events after it. Playback stops at an assumed input about them where [mode] stops (assumed blocks and keeps
     * always), at the game's result until their OK stands on it (it can't be final without it, TRN-065: so it is their
     * move and they are told and reminded), and at the head when the game waits for them there (in the opening also their
     * own mulligan declared alongside the other player's, TRN-027).
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
            if (Engine.result(model.engine) != null && player !in model.table.resultOks) return Stop(at, null)
        }
        if (Engine.result(model.engine) != null) return if (player in model.table.resultOks) null else Stop(at, null)
        return if (Attention.of(model.engine) == player || declares(model.engine, player)) Stop(at, null) else null
    }

    /**
     * Whether the game waits for [player] (I-D57, async-play.md §24), with [stop] their next stop: the game can't go on
     * without them, so it is their move (TRN-058: listed and shown as theirs), they are told "Your move" and reminded.
     * (The server also gives the move to the other player than the asker of an open undo request or result suggestion.) That is when [head] (the main line's head) is their
     * decision (their turn's stops, a choice only they make, an information-rule stop, a wait instead of an assumption,
     * or the other player's line running out without them: all leave the head at their decision), their opening
     * declaration, or the game's result they haven't pressed OK on. Not while the other player is still playing on
     * assumptions about them (the head is then the other player's), however the head came about (play, branch, rewind).
     */
    fun waitsFor(head: GameState, player: PlayerId, stop: Stop?): Boolean =
        stop != null && (Engine.result(head) != null || Attention.of(head) == player || declares(head, player))

    /** Whether [player] may declare their opening mulligan (or bottom choice) on [state] while the other player's is
     * the open decision (simultaneous mulligans, I-D54 §8): their move too. */
    fun declares(state: GameState, player: PlayerId): Boolean =
        state.turn.number == 0 && state.pending != null && Engine.options(state, player, EnginePreferences.of(state)).any { it is Input.SetupAnswer && it.player == player }

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

    /** Whether [player]'s own device passes for them on [state] under Auto-skip: [Host.shouldPassFor] with their
     * "Skip when I have no choice" on (the shared state keeps it off). */
    fun passesWithOwnCards(state: GameState, player: PlayerId): Boolean = Host.passCheck(state, player, EnginePreferences.of(state)).ownSkipOn
}

/**
 * Undo and rewinds (I-D56 §10): taking the main line back to a target is free unless something after it is another
 * player's real input (anything but a pass: their passes never count, whether they tapped them, their device made them or
 * they were assumed and confirmed; owner problem report VXSGKH) other than their table-only events the undo rule lets
 * stand, or showed the player something new; otherwise it needs the other player's approval (an undo request). Every
 * rewind deletes the events after the target and starts a new line (§9). With the game setting "Undo past the other player's
 * passes needs a request" (LOB-135, owner 2026-11-19, N15) their passes count as their real input too.
 */
object Rewind {
    /** Whether a rewind needs a request: [reason] (null: free), with [why], a compact account of the events after the
     * target (whose, which counted and why) for the logs. */
    data class Verdict(val reason: String?, val why: String)

    /**
     * Why rewinding to the point after [target] needs the other player's approval, or null when it is free: [model] is
     * the state at [target] (used up), [after] the main line's events after it, [others] the other players' local
     * heads. With [passes] (the game setting "Undo past the other player's passes needs a request", LOB-135) the other
     * players' passes count as their real input.
     */
    fun requestNeeded(model: GameModel, target: String, after: List<GameEvent>, player: PlayerId, others: List<String>,
        passes: Boolean = false): String? =
        judge(model, target, after, player, others, passes).reason

    /**
     * [requestNeeded] with its account ([Verdict.why]). A rewind of [player]'s inside their opening to before the other
     * player's assumed keep about them ([reapplies]) is judged by the events before that keep alone: the other player's line
     * after it isn't taken back but made again on the point the rewind lands on (HIS-115).
     */
    fun judge(model: GameModel, target: String, after: List<GameEvent>, player: PlayerId, others: List<String>,
        passes: Boolean = false): Verdict {
        val at = if (model.engine.turn.number == 0) reapplies(model.copy(), after, player) else null
        if (at == null) return judgeAll(model, target, after, player, others, passes)
        val before = after.take(at)
        val ids = before.map { it.id }.toSet()
        // A local head on the line made again has seen everything before it
        val seen = others.map { if (it == target || it in ids) it else before.last().id }
        val verdict = judgeAll(model, target, before, player, seen, passes)
        return Verdict(verdict.reason, verdict.why + "; the other player's line of ${after.size - at} event(s) after ${before.last().id.take(8)} " +
            "rests on their assumed keep about you: made again, not taken back (HIS-115)")
    }

    /**
     * HIS-115 (first turn during mulligans §3.6): whether [player]'s rewind to the point [model] is at (used up) re-applies
     * the other player's line instead of taking it back: the index in [after] of the other player's assumed opening answer
     * about [player] (their assumed keep, or its stand-in bottom choice, [Reapply.opening]) that the line rests on, when only
     * [player]'s own events and other players' table-only events come before it (so the other player's own opening, their
     * keep included, isn't taken back) and at least one does; else null: an ordinary rewind.
     */
    fun reapplies(model: GameModel, after: List<GameEvent>, player: PlayerId): Int? {
        if (model.engine.turn.number != 0) return null
        for ((i, event) in after.withIndex()) {
            val by = event.assumed?.assumedBy
            if (by != null) return i.takeIf { i > 0 && by != player && Reapply.opening(event, model.engine) }
            if (event.engine != null && event.author != player.value) return null
            model.apply(event)
        }
        return null
    }

    /**
     * HIS-115: what a rewind of [player]'s to [target] that [reapplies] (at [at]) adds: the other players' table-only events
     * before the assumed keep that may stand ([again], the first starting the new [line]), then the other player's line
     * from the assumed keep made again on top of them ([Reapply.run]: the assumed keep for the opening as it is there, then
     * each event while it comes out the same; the first event made starts [line] when [again] added none). [model] is the
     * state at [target] (used up: it ends as the state after the last event made); [after] the main line's events after
     * [target].
     */
    fun reapply(model: GameModel, target: String, after: List<GameEvent>, at: Int, player: PlayerId, line: Int, ids: () -> String,
        dropped: (GameEvent, String) -> Unit = { _, _ -> }, added: (GameEvent) -> Unit = {}): Reapplied {
        val before = after.take(at)
        val old = after.drop(at)
        // The state the old line rests on (its base P)
        val oldBase = model.copy().also { m -> before.forEach(m::apply) }
        val standing = again(model, target, before, player, line, ids, dropped, added)
        val base = standing.lastOrNull()?.id ?: target
        val atBase = model.copy()
        val assumer = checkNotNull(old.first().assumed?.assumedBy) { "No assumption at ${old.first().id}" }
        val result = Reapply.run(oldBase, model, base, old, assumer, ids, line = line.takeIf { standing.isEmpty() })
        return Reapplied(standing, result, base, atBase)
    }

    /** What [reapply] added: [standing] (the other players' table-only events made again first), then [reapply]'s events
     * after [base] (the last of [standing], or the target); [atBase] is the state at [base]. */
    class Reapplied(val standing: List<GameEvent>, val reapply: Reapply.Result, val base: String, val atBase: GameModel)

    private fun judgeAll(model: GameModel, target: String, after: List<GameEvent>, player: PlayerId, others: List<String>,
        passes: Boolean): Verdict {
        // The index in [after] of the last event another player has seen (−1: none)
        val seen = others.maxOfOrNull { head -> if (head == target) -1 else after.indexOfFirst { it.id == head } } ?: -1
        val undone = mutableListOf<GameEvent>()
        var own = 0
        var assumedPasses = 0
        var othersPasses = 0
        var kept = 0
        fun counts() = "yours $own (your assumed passes for them $assumedPasses), others' passes $othersPasses " +
            "(${if (passes) "counted: the game's setting" else "never counted"}), others' table-only kept $kept"
        fun request(reason: String, event: GameEvent, what: String) = Verdict(reason,
            "request ($reason) at ${event.id.take(8)} by ${event.author.take(8)}: $what; ${after.size} event(s) after ${target.take(8)}, before it ${counts()}")
        for ((i, event) in after.withIndex()) {
            val engine = event.engine
            if (event.author != player.value) {
                if (engine == null) {
                    if (undone.any { Concerns.of(it).blockedBy(Concerns.of(event)) })
                        return request("otherPlayersAction", event, "their table-only event on what the undo takes back")
                    model.apply(event)
                    kept++
                    continue
                }
                // Owner problem report VXSGKH: their passes (tapped, by their device, assumed and confirmed) never count
                othersInput(model.engine, engine)?.let { return request("otherPlayersInput", event, "their ${it.toString().substringBefore('(')}") }
                // N15 (owner 2026-11-19): unless the game's setting counts them as their input (LOB-135)
                if (passes) return request("otherPlayersPass", event, "their pass (the game's setting: passes need a request)")
                othersPasses++
            } else {
                // Your assumption of another player's real input (a block, a keep) is theirs once they have seen it
                // (their pass too while the game's setting counts their passes, N15)
                val assumed = engine?.steps?.firstNotNullOfOrNull { s -> s.input?.takeIf { s.assumedBy != null && (passes || it !is Input.PassPriority) } }
                if (assumed != null && i <= seen) return request("confirmed", event, "your assumed ${assumed.toString().substringBefore('(')} for them, seen")
                own++
                if (event.assumed?.input is Input.PassPriority) assumedPasses++
            }
            if (engine != null) {
                // Your own library counts here: a draw, a look, a scry, a search are something new; so does what a
                // resolution another player's pass let through shows you
                if (Host.revealed(model.engine, engine.changes, player, model.keptView(), null).isNotEmpty())
                    return request("shown", event, "it showed you a card")
                // Also in an event that starts a line (a branch whose outcome flips a coin): every rewind starts a new
                // line, so a free undo past a seen result would roll it again (§9, §10)
                if (event.table.any { it is TableChange.LineMoved } && InformationRule.randomResult(event, model.engine))
                    return request("random", event, "a random result")
            }
            model.apply(event)
            // A wait for the other player's decision whose source you can't see (§5's hidden-source tell)
            val d = model.engine.pending?.decision
            if (d != null && Host.decider(model.engine, d.player) != player && d.source != null &&
                d.source !in model.visible(player)) return request("hiddenSource", event, "a wait for a choice from a source you can't see")
            undone += event
        }
        return Verdict(null, "free: ${after.size} event(s) after ${target.take(8)}: ${counts()}")
    }

    /**
     * The first input of another player's [engine] part, made on [before], that is neither a pass ([Input.PassPriority],
     * whoever's) nor the host's answer to a random decision (no player's choice; [judge] takes its result as something
     * shown), or null: the part is only passes.
     */
    private fun othersInput(before: GameState, engine: EnginePart): Input? {
        val inputs = engine.steps.mapNotNull { it.input }
        inputs.firstOrNull { it !is Input.PassPriority && it !is Input.Answer }?.let { return it }
        if (inputs.none { it is Input.Answer }) return null
        val state = before.deepCopy()
        for (step in engine.steps) {
            val input = step.input
            if (input is Input.Answer) {
                val d = state.pending?.decision
                if (d !is arcanum.engine.decision.Decision.Random || d.id != input.decision) return input
            }
            step.changes.forEach { state.apply(it) }
        }
        return null
    }

    /** The other players' table-only events among [after] that a rewind by [player] keeps: added again at the new head
     * while they still apply there. */
    fun standing(after: List<GameEvent>, player: PlayerId): List<GameEvent> =
        // TRN-065: an OK stood on the result the rewind takes back: it never stands
        after.filter { it.author != player.value && it.engine == null && it.table.none { c -> c is TableChange.LineMoved || c is TableChange.ResultOk } }

    /**
     * What a rewind by [player] to [target] adds (owner's rule 2026-11-04: an undo only pops events, it adds none of its
     * own): the other players' table-only events among [after] that may stand ([standing]), made again on [model] (the
     * state at [target], moved along) while they still apply, each handed to [added] as it is made. The first of them
     * starts the new [line] (its reshuffle, §9); with none, the next event made at [target] starts it ([lineDue]). Combat
     * staging (an attack or block draft, CMB-043) stands only while [model] still awaits the declaration it stages (owner notes
     * 2026-11-14 evening: a rewind back out of the declare attackers step left the attacker's staging on the table for when the
     * game came back there); what doesn't stand is handed to [dropped] with why, for the logs.
     */
    fun again(model: GameModel, target: String, after: List<GameEvent>, player: PlayerId, line: Int, ids: () -> String,
        dropped: (GameEvent, String) -> Unit = { _, _ -> }, added: (GameEvent) -> Unit): List<GameEvent> {
        val events = mutableListOf<GameEvent>()
        for (kept in standing(after, player)) {
            val (table, out) = kept.table.partition { awaited(model, it) }
            if (out.isNotEmpty()) dropped(kept, "its combat staging (${out.joinToString { if (it is TableChange.AttackDraft) "attackers" else "blockers" }}) stages a " +
                "declaration the game no longer awaits at turn ${model.engine.turn.number} ${model.engine.turn.step}")
            if (table.isEmpty()) continue
            val builder = EventBuilder(model, ids(), events.lastOrNull()?.id ?: target, kept.author)
            if (events.isEmpty()) builder.startLine(line)
            try { table.forEach(builder::table) } catch (_: IllegalStateException) { builder.abandon(); dropped(kept, "it no longer applies"); continue }
            events += builder.build().also(added)
        }
        return events
    }

    /** Whether table change [change] may stand on [model]: an attack or block draft (other than one ending the staging) only
     * while some player may declare attackers (blockers) there; anything else always. */
    private fun awaited(model: GameModel, change: TableChange): Boolean {
        val attacks = change is TableChange.AttackDraft && change.after != null
        val blocks = change is TableChange.BlockDraft && change.after != null
        if (!attacks && !blocks) return true
        val s = model.engine
        val options = runCatching { s.players.flatMap { Engine.options(s, it.id, EnginePreferences.of(s)) } }.getOrDefault(emptyList())
        return if (attacks) options.any { it is Input.DeclareAttackers } else options.any { it is Input.DeclareBlockers }
    }

    /** The line the next event made on [model] (the main line's head) starts, or null: after a rewind that added no
     * event, the line counter ([lines]) is ahead of the head's line, and the new line starts with the next event (§9). */
    fun lineDue(model: GameModel, lines: Int): Int? = lines.takeIf { model.table.line.number < it }

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
