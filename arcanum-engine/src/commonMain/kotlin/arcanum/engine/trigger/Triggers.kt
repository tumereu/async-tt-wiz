package arcanum.engine.trigger

import arcanum.engine.Progress
import arcanum.engine.change.Recorder
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Continuation
import arcanum.engine.notYet

/**
 * Triggered abilities (CR 603): which abilities the recorded events trigger (looking back in time for leaves-the-
 * battlefield triggers, CR 603.10), delayed triggers (CR 603.7), and putting them on the Stack in APNAP order with
 * their choices (CR 603.3, [Continuation.PlacingTriggers]). Also answers "this turn" questions over the tagged events
 * ([arcanum.engine.query.History]).
 */
internal object Triggers {
    /** Puts the triggered abilities waiting since the last check on the Stack (CR 603.3b); [Progress.Done] when there
     * were none. */
    fun place(recorder: Recorder): Progress = notYet("triggered abilities")

    fun resume(recorder: Recorder, continuation: Continuation.PlacingTriggers, answer: Answer): Progress = notYet("trigger decisions")
}
