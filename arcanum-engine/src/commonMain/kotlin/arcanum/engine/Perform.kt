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
import arcanum.engine.turn.Interim
import arcanum.engine.turn.Priority
import arcanum.engine.turn.Steps
import arcanum.engine.decision.TurnAction

/** How far a flow got: finished, or waiting for a decision (D58). */
internal sealed interface Progress {
    data object Done : Progress
    data class Waiting(val pending: Pending) : Progress
}

/**
 * [Engine.perform]: works on a copy of the state (D56), records every change as it is made, and after the input runs
 * the checks a player's priority waits for (CR 117.5): state-based actions (CR 704.3) and putting triggered abilities on
 * the Stack (CR 603.3), repeated until neither does anything, then priority (CR 117.3). A flow that needs a decision
 * stops with [Progress.Waiting]; its pending decision is recorded as the last change.
 *
 * Each concern lives in its own object, filled in by its part of the engine: [Casting] (cast, activate, play a land,
 * mana payment), [Resolution], [Triggers], [StateBasedActions], [Combat] and [Priority] (steps and turns).
 */
internal object Perform {
    fun run(state: GameState, input: Input): Outcome {
        val recorder = Recorder(state.deepCopy())
        val open = recorder.state.pending
        val progress = if (open != null) {
            when (input) {
                is Input.Answer -> {
                    require(input.decision == open.decision.id) { "The open decision is ${open.decision.id}, not ${input.decision}" }
                    recorder.record(Changes.pending(recorder.state, null))
                    resume(recorder, open.continuation, input.answer)
                }
                Input.Cancel -> {
                    recorder.record(Changes.pending(recorder.state, null))
                    Casting.cancel(recorder, open.continuation)
                }
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
                is Input.Answer -> throw IllegalArgumentException("No decision is open")
                Input.Cancel -> throw IllegalArgumentException("Nothing to cancel")
            }
        }
        val final = if (progress is Progress.Done) afterAction(recorder) else progress
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
                TurnAction.CleanupDiscard -> Steps.resumeCleanup(recorder, answer)
            }
        }
        return progress
    }

    /** CR 117.5: before a player gets priority, state-based actions and triggered abilities, until nothing happens. */
    fun afterAction(recorder: Recorder): Progress {
        (checks(recorder) as? Progress.Waiting)?.let { return it }
        return Priority.give(recorder)
    }

    /** State-based actions (CR 704.3) and putting triggered abilities on the Stack (CR 603.3), repeated until neither
     * does anything; also the cleanup step's check (CR 514.3a). Until those parts exist, [Interim]'s basic state-based
     * actions stand in and no abilities trigger. */
    fun checks(recorder: Recorder): Progress {
        while (true) {
            val before = recorder.changes.size
            val sba = try { StateBasedActions.check(recorder) } catch (e: NotImplementedError) { Interim.stateBased(recorder) }
            (sba as? Progress.Waiting)?.let { return it }
            val triggers = try { Triggers.place(recorder) } catch (e: NotImplementedError) { Progress.Done }
            (triggers as? Progress.Waiting)?.let { return it }
            if (recorder.changes.size == before) return Progress.Done
        }
    }
}

/** Thrown by the parts of the engine that later parts fill in. */
internal fun notYet(what: String): Nothing =
    throw NotImplementedError("Arcanum engine: $what is not implemented yet")
