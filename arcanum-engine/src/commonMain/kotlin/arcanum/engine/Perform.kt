package arcanum.engine

import arcanum.engine.casting.Casting
import arcanum.engine.change.Changes
import arcanum.engine.change.Recorder
import arcanum.engine.combat.Combat
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Continuation
import arcanum.engine.input.Input
import arcanum.engine.resolution.Resolution
import arcanum.engine.sba.StateBasedActions
import arcanum.engine.state.GameState
import arcanum.engine.trigger.Triggers
import arcanum.engine.turn.Priority
import arcanum.engine.turn.Loops
import arcanum.engine.turn.Steps
import arcanum.engine.decision.TurnAction
import arcanum.engine.setup.GameEnd
import arcanum.engine.setup.Setup
import kotlin.time.TimeSource

/** How far a flow got: finished, or waiting for a decision (D58). */
internal sealed interface Progress {
    data object Done : Progress
    data class Waiting(val pending: Pending) : Progress
}

/**
 * [Engine.perform]: works on a copy of the state (D56), records every change as it is made, and after the input runs
 * the checks a player's priority waits for (CR 117.5): state-based actions (CR 704.3) and putting triggered abilities on
 * the Stack (CR 603.3), repeated until neither does anything, then priority (CR 117.3). A flow that needs a decision
 * stops with [Progress.Waiting]; its pending decision is recorded as the last change. A call that would go on without
 * end stops with "possible loop" ([Loops.capped]); a host reports a loop of mandatory actions ([Input.MandatoryLoop]).
 *
 * Each concern lives in its own object, filled in by its part of the engine: [Casting] (cast, activate, play a land,
 * mana payment), [Resolution], [Triggers], [StateBasedActions], [Combat] and [Priority] (steps and turns).
 */
internal object Perform {
    /** [view], when given, is [state]'s view (worked out once by a caller that performs several inputs on it). [trace]
     * gets one entry for the call ([EngineTrace]); with [EngineTrace.None] nothing is timed or made. */
    fun run(state: GameState, input: Input, view: arcanum.engine.view.View? = null, trace: EngineTrace = EngineTrace.None): Outcome {
        if (trace === EngineTrace.None) return untraced(state, input, view) { }
        val mark = TimeSource.Monotonic.markNow()
        var recorder: Recorder? = null
        val outcome = try {
            untraced(state, input, view) { recorder = it }
        } catch (refused: Throwable) {
            trace.record(TraceEntry.Refused(input, refused.message.orEmpty().take(MAX_REASON), refused::class.simpleName ?: "Throwable",
                mark.elapsedNow().inWholeMicroseconds))
            throw refused
        }
        val micros = mark.elapsedNow().inWholeMicroseconds
        val pending = outcome.pending
        trace.record(TraceEntry.Performed(input, outcome.changes.size, recorder?.rounds ?: 0, pending?.let(PendingShape::of),
            (pending?.continuation as? Continuation.Loop)?.stop, recorder?.state?.let(GameEnd::over) ?: false, micros))
        return outcome
    }

    private inline fun untraced(state: GameState, input: Input, view: arcanum.engine.view.View?, made: (Recorder) -> Unit): Outcome {
        require(!GameEnd.over(state)) { "The game is over (CR 104.1)" }
        if (input is Input.Concede) return GameEnd.concede(state, input)
        if (input is Input.AgreedResult) return GameEnd.agree(state, input)
        return runOn(Recorder(state.deepCopy(), view).also(made), input)
    }

    /** The longest refusal reason a trace keeps. */
    private const val MAX_REASON = 500

    /** [run] on [recorder]'s working state, which is left as the input leaves it ([Options] looks at it). Not for
     * [Input.Concede]. */
    fun runOn(recorder: Recorder, input: Input): Outcome {
        require(!GameEnd.over(recorder.state)) { "The game is over (CR 104.1)" }
        require(input !is Input.Concede && input !is Input.AgreedResult) { "Ending the game works on its own copy" }
        val open = recorder.state.pending
        val progress = if (open != null) {
            when (input) {
                is Input.Answer -> {
                    require(input.decision == open.decision.id) { "The open decision is ${open.decision.id}, not ${input.decision}" }
                    // The one answer-validation path: refused unless it answers what was asked
                    arcanum.engine.decision.AnswerCheck.check(recorder.state, open.decision, input.answer)
                    recorder.record(Changes.pending(recorder.state, null))
                    resume(recorder, open.continuation, input.answer)
                }
                is Input.Cancel -> {
                    recorder.record(Changes.pending(recorder.state, null))
                    Casting.cancel(recorder, open.continuation, input.player)
                }
                is Input.SetupAnswer -> Setup.early(recorder, open, input)
                else -> throw IllegalArgumentException("Waiting for ${open.decision::class.simpleName}: only its answer is accepted (D58)")
            }
        } else {
            when (input) {
                is Input.PlayLand -> Casting.playLand(recorder, input)
                is Input.Cast -> Casting.cast(recorder, input)
                is Input.Activate -> Casting.activate(recorder, input)
                is Input.PassPriority -> Priority.pass(recorder, input)
                is Input.DeclareAttackers -> Combat.declareAttackers(recorder, input)
                is Input.DeclareBlockers -> Combat.declareBlockers(recorder, input)
                is Input.StartGame -> Setup.start(recorder, input)
                is Input.MandatoryLoop -> Loops.report(recorder)
                is Input.Concede, is Input.AgreedResult -> error("Ending the game is handled above")
                is Input.SetupAnswer -> throw IllegalArgumentException("No mulligan decision is open")
                is Input.Answer -> throw IllegalArgumentException("No decision is open")
                is Input.Cancel -> throw IllegalArgumentException("Nothing to cancel")
            }
        }
        val final = if (progress is Progress.Done) afterAction(recorder) else progress
        // CR 104.1: the game ends at once; nothing waits any more
        if (GameEnd.over(recorder.state)) {
            GameEnd.close(recorder)
            return Outcome(recorder.changes.toList(), null)
        }
        if (final is Progress.Waiting) recorder.record(Changes.pending(recorder.state, final.pending))
        return Outcome(recorder.changes.toList(), (final as? Progress.Waiting)?.pending)
    }

    /** Goes on with [continuation] given [answer]; each part goes on with [Continuation.then] itself when its flow ends. */
    fun resume(recorder: Recorder, continuation: Continuation, answer: Answer): Progress {
        val progress = when (continuation) {
            is Continuation.Announcing -> Casting.resume(recorder, continuation, answer)
            is Continuation.Resolving -> Resolution.resume(recorder, continuation, answer)
            is Continuation.Event -> Resolution.resumeEvent(recorder, continuation, answer)
            is Continuation.PlacingTriggers -> Triggers.resume(recorder, continuation, answer)
            is Continuation.StateBased -> StateBasedActions.resume(recorder, continuation, answer)
            is Continuation.TurnBased -> when (continuation.action) {
                is TurnAction.CombatDamage -> Combat.resume(recorder, continuation, answer)
                is TurnAction.CleanupDiscard -> Steps.resumeCleanup(recorder, continuation.action, answer)
            }
            is Continuation.Setup -> Setup.resume(recorder, continuation, answer)
            is Continuation.Played -> Casting.resumePlayed(recorder, continuation, answer)
            is Continuation.Loop -> Loops.resume(recorder, answer)
        }
        return progress
    }

    /** CR 117.5: before a player gets priority, state-based actions and triggered abilities, until nothing happens. */
    fun afterAction(recorder: Recorder): Progress {
        (checks(recorder) as? Progress.Waiting)?.let { return it }
        if (GameEnd.over(recorder.state)) return Progress.Done
        return Priority.give(recorder)
    }

    /** State-based actions (CR 704.3) and putting triggered abilities on the Stack (CR 603.3), repeated until neither
     * does anything; also the cleanup step's check (CR 514.3a). A call that goes round too often stops with "possible
     * loop" ([Loops.capped]). */
    fun checks(recorder: Recorder): Progress {
        while (true) {
            Loops.capped(recorder)?.let { return it }
            recorder.rounds++
            val before = recorder.changes.size
            // CR 506.4: also after changes made by hand (D42), which don't pass through the engine's events
            Combat.removeWhatLeft(recorder)
            (StateBasedActions.check(recorder) as? Progress.Waiting)?.let { return it }
            // CR 104.2a: one player left wins at once; CR 800.4a: in a game that goes on, players who lost leave it
            if (GameEnd.over(recorder.state)) return Progress.Done
            GameEnd.leaveLost(recorder)
            (Triggers.place(recorder) as? Progress.Waiting)?.let { return it }
            if (recorder.changes.size == before) return Progress.Done
        }
    }

    /** [checks], giving the decision one of them waits for, if any. */
    fun settle(recorder: Recorder): Progress.Waiting? = checks(recorder) as? Progress.Waiting
}

/** Thrown by the parts of the engine that later parts fill in. */
internal fun notYet(what: String): Nothing =
    throw NotImplementedError("Arcanum engine: $what is not implemented yet")
