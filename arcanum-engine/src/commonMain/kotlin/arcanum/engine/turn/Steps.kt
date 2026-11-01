package arcanum.engine.turn

import arcanum.api.CounterKind
import arcanum.api.Duration
import arcanum.api.Mechanic
import arcanum.api.Players
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.Pending
import arcanum.engine.Perform
import arcanum.engine.Progress
import arcanum.engine.casting.Snapshot
import arcanum.engine.casting.decisionId
import arcanum.engine.change.Change
import arcanum.engine.change.Changes
import arcanum.engine.change.Meaning
import arcanum.engine.change.Recorder
import arcanum.engine.combat.Combat
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Continuation
import arcanum.engine.decision.Decision
import arcanum.engine.decision.TurnAction
import arcanum.engine.state.DelayedTrigger
import arcanum.engine.state.EffectObject
import arcanum.engine.state.GameState
import arcanum.engine.state.PlayerId
import arcanum.engine.state.StatusFlag
import arcanum.engine.state.TargetRef
import arcanum.engine.state.TurnState

/**
 * Steps and turns (CR 500–514): ending a step (mana empties, CR 500.4, 106.4; "until end of combat" ends, CR 511.2–3),
 * beginning the next (effects lasting until it end, CR 500.4) with its turn-based actions (CR 703): untap (CR 502),
 * draw (CR 504, not on the starting player's first turn when [GameState.skipFirstDraw], CR 103.8a), the combat steps
 * ([Combat]) and cleanup (CR 514). Steps without priority (untap, cleanup) go straight on to the next. A new turn goes to
 * the next player still in the game, or to whoever takes an extra turn (CR 500.7, 101.4).
 */
internal object Steps {
    val ORDER: List<TurnStep> = listOf(
        TurnStep.Untap, TurnStep.Upkeep, TurnStep.Draw, TurnStep.PrecombatMain, TurnStep.BeginningOfCombat,
        TurnStep.DeclareAttackers, TurnStep.DeclareBlockers, TurnStep.CombatDamage, TurnStep.EndOfCombat,
        TurnStep.PostcombatMain, TurnStep.End, TurnStep.Cleanup,
    )

    /** The player ledger key set when a player had to draw from an empty library; they lose at the next state-based
     * action check (CR 704.5b), which clears it. */
    const val EMPTY_LIBRARY_DRAW = "\$drew-from-empty-library"

    /** What a step's turn-based actions lead to. */
    private sealed interface Next {
        data object Priority : Next
        data object GoOn : Next
        data class Wait(val progress: Progress.Waiting) : Next
    }

    /** All players passed with an empty Stack: the step ends and the game goes on to the next step in which players
     * get priority, or to a decision a turn-based action needs (CR 500.2). */
    fun endStep(recorder: Recorder): Progress {
        while (true) {
            leave(recorder)
            val next = nextStep(recorder.state)
            if (next == null) beginTurn(recorder) else begin(recorder, next)
            when (val n = turnBasedActions(recorder)) {
                Next.Priority -> return Progress.Done
                is Next.Wait -> return n.progress
                Next.GoOn -> Unit
            }
        }
    }

    /** The step after this one (null: the next turn), skipping the declare blockers and combat damage steps without
     * attackers (CR 508.8), adding a second combat damage step after a first-strike one (CR 510.4), and skipping steps
     * the active player skips. */
    private fun nextStep(s: GameState): TurnStep? {
        var step: TurnStep? = s.turn.step
        val skipped = Snapshot(s).view.players[s.turn.active]?.mechanics.orEmpty().mapNotNull { (it.mechanic as? Mechanic.SkipStep)?.step }.toSet()
        while (true) {
            step = when (step) {
                TurnStep.Cleanup -> return null
                TurnStep.DeclareAttackers -> if (s.combat?.attackers.isNullOrEmpty()) TurnStep.EndOfCombat else TurnStep.DeclareBlockers
                TurnStep.CombatDamage -> if (s.combat?.firstStrikeStep == true && step == s.turn.step) TurnStep.CombatDamage else TurnStep.EndOfCombat
                else -> ORDER[ORDER.indexOf(step) + 1]
            }
            if (step !in skipped || step == TurnStep.Cleanup) return step
        }
    }

    /** The current step ends: mana empties (CR 500.4, 106.4), effects through this step end, combat ends after the
     * end of combat step (CR 511.3). */
    private fun leave(recorder: Recorder) {
        val s = recorder.state
        val step = s.turn.step
        s.players.forEach { p ->
            val kept = p.manaPool.filter { it.keptUntil != null && !(step == TurnStep.Cleanup && it.keptUntil == Duration.EndOfTurn) }
            if (kept.size != p.manaPool.size) recorder.record(Changes.manaPool(s, p.id, kept, listOf(Meaning.ManaEmptied)))
        }
        endEffects(recorder) { d, you -> d is Duration.ThroughNextStep && d.step == step && whose(recorder.state, d.whose, you) }
        if (step == TurnStep.EndOfCombat) {
            // CR 511.2: "until end of combat" effects end; CR 511.3: everything is removed from combat
            endEffects(recorder) { d, _ -> d == Duration.EndOfCombat }
            if (recorder.state.combat != null) recorder.record(Change.Combat(recorder.state.combat, null, listOf(Meaning.CombatEnded)))
        }
    }

    /** Whether the active player is one of [players] for the effect's controller. */
    private fun whose(s: GameState, players: Players, you: PlayerId): Boolean =
        s.turn.active in Snapshot(s).evaluator(you).players(players)

    private fun begin(recorder: Recorder, step: TurnStep) {
        val s = recorder.state
        recorder.record(Changes.step(s, s.turn.copy(step = step), listOf(Meaning.StepBegan)))
        // CR 500.4: effects that last until this step end as it begins
        endEffects(recorder) { d, you -> d is Duration.UntilStep && d.step == step && untilStepMatches(recorder.state, d, you) }
    }

    private fun untilStepMatches(s: GameState, d: Duration.UntilStep, you: PlayerId): Boolean =
        Snapshot(s).evaluator(you).matches(d.whose, s.turn.active)

    /** A new turn: the next player in turn order (or the first extra turn), its untap step (CR 500.1, 500.7). */
    private fun beginTurn(recorder: Recorder) {
        val s = recorder.state
        val extra = s.turn.extraTurns.firstOrNull { p -> !s.player(p).lost }
        val next = extra ?: Priority.nextPlayer(s, s.turn.active)
        val number = s.turn.number + 1
        val turn = TurnState(
            number = number, active = next, step = TurnStep.Untap,
            turnStarted = s.turn.turnStarted + (next to number),
            extraTurns = if (extra != null) s.turn.extraTurns.drop(s.turn.extraTurns.indexOf(extra) + 1) else s.turn.extraTurns,
        )
        recorder.record(Changes.step(s, turn, listOf(Meaning.TurnBegan, Meaning.StepBegan)))
        // "Until your next turn" ends as that player's next turn begins (CR 611.2b)
        endEffects(recorder, onlyController = next) { d, _ -> d == Duration.UntilYourNextTurn }
        endEffects(recorder) { d, you -> d is Duration.UntilStep && d.step == TurnStep.Untap && untilStepMatches(recorder.state, d, you) }
    }

    private fun turnBasedActions(recorder: Recorder): Next {
        val s = recorder.state
        return when (s.turn.step) {
            TurnStep.Untap -> { untap(recorder); Next.GoOn }
            TurnStep.Draw -> {
                // CR 103.8a: the starting player skips the draw of their first turn in a two-player game
                if (s.turn.number == 1 && s.skipFirstDraw) Next.Priority
                // Through the event pipeline, so replacements of draws apply (CR 121.6, 614)
                else when (val p = arcanum.engine.action.Events.run(recorder, arcanum.engine.action.PlannedEvent.Draw(s.turn.active, 1))) {
                    is Progress.Waiting -> Next.Wait(p)
                    Progress.Done -> Next.Priority
                }
            }
            // CR 714.3b: as the precombat main phase begins, the active player puts a lore counter on each of their Sagas
            TurnStep.PrecombatMain -> {
                val snap = Snapshot(s)
                val sagas = s.battlefield.filter { id ->
                    snap.board.controller(id) == s.turn.active && snap.view[id]?.characteristics?.subtypes?.any { it.name == "Saga" } == true
                }
                if (sagas.isEmpty()) Next.Priority
                else when (val p = arcanum.engine.action.Events.run(recorder,
                    arcanum.engine.action.PlannedEvent.Counters(sagas, arcanum.api.CounterKind.Lore, 1, s.turn.active))) {
                    is Progress.Waiting -> Next.Wait(p)
                    Progress.Done -> Next.Priority
                }
            }
            TurnStep.DeclareAttackers -> { Combat.beginDeclareAttackers(recorder); Next.Priority }
            TurnStep.DeclareBlockers -> { Combat.beginDeclareBlockers(recorder); Next.Priority }
            TurnStep.CombatDamage -> when (val p = Combat.damageStep(recorder)) {
                is Progress.Waiting -> Next.Wait(p)
                Progress.Done -> Next.Priority
            }
            TurnStep.Cleanup -> cleanup(recorder)
            else -> Next.Priority
        }
    }

    /** CR 502.1, 502.3: phasing, then the active player's permanents untap, except those that don't (a stun counter is
     * removed instead, CR 122.1d). */
    private fun untap(recorder: Recorder) {
        val s = recorder.state
        val active = s.turn.active
        val snap = Snapshot(s)
        // CR 502.1, 702.26: phasing
        s.battlefield.toList().forEach { id ->
            val o = s.obj(id) ?: return@forEach
            val controller = o.controller ?: o.owner
            if (o.phasedOut && controller == active) {
                recorder.record(Change.Status(id, StatusFlag.PhasedOut, true, false, listOf(Meaning.PhasedIn)))
            } else if (!o.phasedOut && snap.board.controller(id) == active && hasPhasing(snap, id)) {
                recorder.record(Change.Status(id, StatusFlag.PhasedOut, false, true, listOf(Meaning.PhasedOut)))
            }
        }
        val after = Snapshot(recorder.state)
        recorder.state.battlefield.toList().forEach { id ->
            val o = recorder.state.obj(id) ?: return@forEach
            if (!o.tapped || o.phasedOut || after.board.controller(id) != active) return@forEach
            if (after.view[id]?.has { it == Mechanic.DoesntUntap } == true) return@forEach
            if (o.counters(CounterKind.Stun) > 0) {
                recorder.record(Changes.counters(recorder.state, TargetRef.Object(id), CounterKind.Stun, -1))
            } else {
                recorder.record(Changes.tap(recorder.state, id, tapped = false))
            }
        }
    }

    /** CR 702.26a: phasing, the keyword or its meaning. */
    private fun hasPhasing(snap: Snapshot, id: arcanum.engine.state.ObjectId): Boolean =
        snap.view[id]?.characteristics?.hasKeyword(arcanum.api.Keyword.Phasing) == true || snap.view[id]?.has { it == Mechanic.Phasing } == true

    // ---- Cleanup (CR 514)

    /** CR 514.1: the active player discards down to their maximum hand size, then the rest of the cleanup step. */
    private fun cleanup(recorder: Recorder): Next {
        val s = recorder.state
        val active = s.turn.active
        val excess = s.player(active).hand.size - maxHandSize(s, active)
        if (excess > 0) {
            val decision = Decision.ChooseObjects(decisionId(s, "cleanup-discard"), active, null, s.player(active).hand,
                excess, excess, "Discard down to ${maxHandSize(s, active)} cards (CR 514.1)")
            return Next.Wait(Progress.Waiting(Pending(decision, Continuation.TurnBased(TurnAction.CleanupDiscard))))
        }
        return finishCleanup(recorder)
    }

    /** CR 402.2: seven, changed by effects; no maximum with [Mechanic.NoMaximumHandSize]. */
    fun maxHandSize(s: GameState, player: PlayerId): Int {
        val mechanics = Snapshot(s).view.players[player]?.mechanics.orEmpty().map { it.mechanic }
        if (mechanics.any { it == Mechanic.NoMaximumHandSize }) return Int.MAX_VALUE
        return 7 + mechanics.filterIsInstance<Mechanic.MaximumHandSizeChange>().sumOf { it.change }
    }

    /** The answer to the cleanup discard: discard those cards, then the rest of the cleanup step. */
    fun resumeCleanup(recorder: Recorder, answer: Answer): Progress {
        val s = recorder.state
        val active = s.turn.active
        val chosen = (answer as? Answer.Objects)?.chosen ?: throw IllegalArgumentException("Choose the cards to discard")
        val excess = s.player(active).hand.size - maxHandSize(s, active)
        require(chosen.size == excess && chosen.distinct().size == excess && chosen.all { it in s.player(active).hand }) {
            "Discard exactly $excess cards from ${active}'s hand"
        }
        recorder.record(Changes.move(s, chosen, Zone.Graveyard, meanings = listOf(Meaning.Discarded)))
        return when (finishCleanup(recorder)) {
            Next.GoOn -> endStep(recorder)
            else -> Progress.Done
        }
    }

    /** CR 514.2: damage is removed and "until end of turn" effects end, at once; CR 514.3a: if that leads to
     * state-based actions or triggers, players get priority in this cleanup step and another one follows. */
    private fun finishCleanup(recorder: Recorder): Next {
        val s = recorder.state
        s.battlefield.toList().forEach { id ->
            val o = s.obj(id) ?: return@forEach
            if (o.damage != 0 || o.deathtouchDamage) recorder.record(Change.DamageMarked(id, o.damage, 0, o.deathtouchDamage, false))
        }
        endEffects(recorder) { d, _ -> d == Duration.EndOfTurn }
        endEffects(recorder, onlyController = s.turn.active, createdBefore = s.turn.number) { d, _ -> d == Duration.UntilEndOfYourNextTurn }
        val before = recorder.changes.size
        return when (val p = Perform.checks(recorder)) {
            is Progress.Waiting -> Next.Wait(p)
            Progress.Done -> if (recorder.changes.size > before) Next.Priority else Next.GoOn
        }
    }

    // ---- Effects that end with time

    /** Ends effect objects and delayed triggers whose duration [ends] says is over now. */
    private fun endEffects(
        recorder: Recorder, onlyController: PlayerId? = null, createdBefore: Int? = null, ends: (Duration, PlayerId) -> Boolean,
    ) {
        recorder.state.effects.values.toList().forEach { e: EffectObject ->
            if (onlyController != null && e.controller != onlyController) return@forEach
            if (createdBefore != null && e.createdTurn >= createdBefore) return@forEach
            if (ends(e.duration, e.controller)) recorder.record(Change.EffectRemoved(e))
        }
        recorder.state.delayedTriggers.values.toList().forEach { t: DelayedTrigger ->
            val d = t.duration ?: return@forEach
            if (onlyController != null && t.controller != onlyController) return@forEach
            if (createdBefore != null && t.createdTurn >= createdBefore) return@forEach
            if (ends(d, t.controller)) recorder.record(Change.DelayedTriggerRemoved(t, listOf(Meaning.EffectEnded)))
        }
    }
}
