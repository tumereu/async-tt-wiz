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
     * get priority, or to a decision a turn-based action needs (CR 500.2). [afterPriority]: players had priority in this
     * step, so a cleanup step is followed by another one (CR 514.3a). */
    fun endStep(recorder: Recorder, afterPriority: Boolean = true): Progress {
        var again = afterPriority && recorder.state.turn.step == TurnStep.Cleanup
        while (true) {
            leave(recorder)
            val next = if (again) TurnStep.Cleanup else afterPhase(recorder) ?: nextStep(recorder.state)
            again = false
            if (next == null) beginTurn(recorder) else begin(recorder, next)
            when (val n = turnBasedActions(recorder)) {
                Next.Priority -> return Progress.Done
                is Next.Wait -> return n.progress
                Next.GoOn -> Unit
            }
        }
    }

    // gtc
    /**
     * CR 500.8: as a phase ends (the end of combat step, a main phase), an additional phase begins: the first still to
     * come; once they are all over, the turn's own order goes on where it was left. Null: no additional phase now.
     */
    private fun afterPhase(recorder: Recorder): TurnStep? {
        val s = recorder.state
        val step = s.turn.step
        if (step != TurnStep.EndOfCombat && step != TurnStep.PrecombatMain && step != TurnStep.PostcombatMain) return null
        val extra = s.turn.extraPhases.firstOrNull()
        if (extra != null) {
            val resume = s.turn.resumeAt ?: nextStep(s)
            recorder.record(Changes.step(s, s.turn.copy(extraPhases = s.turn.extraPhases.drop(1), resumeAt = resume)))
            return extra
        }
        val resume = s.turn.resumeAt ?: return null
        recorder.record(Changes.step(s, s.turn.copy(resumeAt = null)))
        return resume
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
        var next = extra ?: Priority.nextPlayer(s, s.turn.active)
        // sos: skipped turns are passed over (CR 614.10); each skip is used up by the turn it replaces
        var skipped = s.turn.skippedTurns
        var guard = s.players.size * (skipped.size + 1)
        while (next in skipped && extra == null && guard-- > 0) {
            skipped = skipped - next
            next = Priority.nextPlayer(s, next)
        }
        val number = s.turn.number + 1
        val turn = TurnState(
            number = number, active = next, step = TurnStep.Untap,
            turnStarted = s.turn.turnStarted + (next to number),
            turnsTaken = s.turn.turnsTaken + (next to (s.turn.turnsTaken[next] ?: 0) + 1),
            extraTurns = if (extra != null) s.turn.extraTurns.drop(s.turn.extraTurns.indexOf(extra) + 1) else s.turn.extraTurns,
            skippedTurns = skipped,
        )
        // CR 800.4m: players who left and whose turn would have begun now (passed over in turn order)
        val passedOver = if (extra == null) leftPlayersBetween(s, s.turn.active, next) else emptyList()
        recorder.record(Changes.step(s, turn, listOf(Meaning.TurnBegan, Meaning.StepBegan)))
        // Their effects lasting until their next turn, or until a point in it, end as that turn would have begun
        passedOver.forEach { left -> endEffects(recorder, onlyController = left) { d, _ -> keyedToYourNextTurn(d) } }
        // "Until your next turn" ends as that player's next turn begins (CR 611.2b); so does their goading (CR 701.15a)
        endEffects(recorder, onlyController = next) { d, _ -> d == Duration.UntilYourNextTurn }
        (passedOver + next).forEach { goader ->
            recorder.state.objects.values.filter { goader in it.goadedBy }.forEach { o ->
                recorder.record(Change.ObjectUpdated(o, o.copy(goadedBy = o.goadedBy - goader)))
            }
        }
        endEffects(recorder) { d, you -> d is Duration.UntilStep && d.step == TurnStep.Untap && untilStepMatches(recorder.state, d, you) }
    }

    /** The players who left the game seated after [from] and before [to] in turn order: the ones a turn going from
     * [from] to [to] passes over. */
    private fun leftPlayersBetween(s: GameState, from: PlayerId, to: PlayerId): List<PlayerId> {
        val order = s.players.map { it.id }
        val start = order.indexOf(from)
        if (start < 0) return emptyList()
        return (1 until order.size).map { order[(start + it) % order.size] }.takeWhile { it != to }.filter { s.player(it).lost }
    }

    /** Durations that last until their controller's next turn or a point in it (CR 611.2b, 800.4m). */
    private fun keyedToYourNextTurn(d: Duration): Boolean = d == Duration.UntilYourNextTurn || d == Duration.UntilEndOfYourNextTurn ||
        (d is Duration.UntilStep && d.whose == arcanum.api.PlayerFilter.You) ||
        (d is Duration.ThroughNextStep && d.whose == Players.You)

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
            // CR 502.3: "untaps during each other player's untap step"
            if (!o.tapped || o.phasedOut) return@forEach
            if (after.board.controller(id) != active && after.view[id]?.has { it == Mechanic.UntapsDuringOtherUntapSteps } != true) return@forEach
            if (after.view[id]?.has { it == Mechanic.DoesntUntap || it == Mechanic.CantBecomeUntapped } == true) return@forEach
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
    private fun cleanup(recorder: Recorder): Next = when (val p = cleanupDiscard(recorder, emptyList())) {
        is Progress.Waiting -> Next.Wait(p)
        Progress.Done -> finishCleanup(recorder)
    }

    /**
     * CR 514.1: the active player chooses the cards over their maximum hand size and discards them as one event through
     * the event pipeline, so replacements apply (madness, CR 702.35a); a decision stops it, and it is redone from its
     * start with the [answers] so far (D29).
     */
    private fun cleanupDiscard(recorder: Recorder, answers: List<Answer>): Progress {
        val s = recorder.state
        val active = s.turn.active
        val excess = s.player(active).hand.size - maxHandSize(s, active)
        if (excess <= 0) return Progress.Done
        val mark = recorder.changes.size
        val act = arcanum.engine.action.Act(recorder, arcanum.engine.action.Asker(answers, decisionId(s, "cleanup-discard").value))
        return try {
            val hand = s.player(active).hand
            val chosen = act.chooseObjects(active, null, hand, excess, excess, "Discard down to ${maxHandSize(s, active)} cards (CR 514.1)")
            require(chosen.size == excess && chosen.distinct().size == excess) { "Discard exactly $excess cards from ${active}'s hand" }
            arcanum.engine.action.Actions.discard(act, arcanum.engine.action.Cause(active, null, key = "cleanup:${s.turn.number}"), chosen)
            Progress.Done
        } catch (n: arcanum.engine.action.NeedDecision) {
            recorder.rollback(mark)
            Progress.Waiting(Pending(n.decision, Continuation.TurnBased(TurnAction.CleanupDiscard(answers))))
        }
    }

    /** CR 402.2: seven, changed by effects; no maximum with [Mechanic.NoMaximumHandSize]. */
    fun maxHandSize(s: GameState, player: PlayerId): Int {
        val mechanics = Snapshot(s).view.players[player]?.mechanics.orEmpty().map { it.mechanic }
        if (mechanics.any { it == Mechanic.NoMaximumHandSize }) return Int.MAX_VALUE
        // "Your maximum hand size is equal to …" sets it instead of seven
        val snap = Snapshot(s)
        val set = snap.view.players[player]?.mechanics.orEmpty().lastOrNull { it.mechanic is Mechanic.MaximumHandSize }?.let { applied ->
            snap.evaluator(applied.controller, self = applied.source, names = applied.names).value((applied.mechanic as Mechanic.MaximumHandSize).value)
        }
        return (set ?: 7) + mechanics.filterIsInstance<Mechanic.MaximumHandSizeChange>().sumOf { it.change }
    }

    /** An answer to the cleanup discard's decisions: the discard is redone with it, then the rest of the cleanup step. */
    fun resumeCleanup(recorder: Recorder, action: TurnAction.CleanupDiscard, answer: Answer): Progress {
        if (action.answers.isEmpty()) require(answer is Answer.Objects) { "Choose the cards to discard" }
        (cleanupDiscard(recorder, action.answers + answer) as? Progress.Waiting)?.let { return it }
        return when (val next = finishCleanup(recorder)) {
            Next.GoOn -> endStep(recorder, afterPriority = false)
            is Next.Wait -> next.progress
            Next.Priority -> Progress.Done
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
        // sos: "until the end of their next turn", for the player named as the effect was created
        recorder.state.effects.values.toList().filter { it.duration is Duration.UntilEndOfNextTurnOf && it.createdTurn < s.turn.number }.forEach { e ->
            val players = Snapshot(recorder.state).evaluator(e.controller, e.source, e.names).players((e.duration as Duration.UntilEndOfNextTurnOf).players)
            if (s.turn.active in players) recorder.record(Change.EffectRemoved(e))
        }
        // CR 702.171b: a permanent stays saddled until the end of the turn
        val saddled = StatusFlag.Marked(arcanum.api.ObjectMark.Saddled)
        s.battlefield.filter { s.obj(it)?.marks?.contains(arcanum.api.ObjectMark.Saddled) == true }
            .forEach { recorder.record(Change.Status(it, saddled, true, false)) }
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
