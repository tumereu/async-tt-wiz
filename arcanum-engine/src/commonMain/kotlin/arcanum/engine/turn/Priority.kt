package arcanum.engine.turn

import arcanum.engine.Progress
import arcanum.engine.change.Recorder
import arcanum.engine.input.Input
import arcanum.engine.notYet

/**
 * Priority, steps and turns (CR 117, 500–514, D57): passing priority, resolving the top of the Stack when all pass in
 * a row (CR 117.4, via [arcanum.engine.resolution.Resolution.resolveTop]), ending steps and beginning the next with
 * their turn-based actions (CR 703), mana emptying (CR 500.4), cleanup (CR 514), extra turns and N players.
 */
internal object Priority {
    fun pass(recorder: Recorder, input: Input.PassPriority): Progress = notYet("passing priority")

    /** Gives priority to the player who gets it next (CR 117.3b–d). */
    fun give(recorder: Recorder): Progress = notYet("giving priority")
}
