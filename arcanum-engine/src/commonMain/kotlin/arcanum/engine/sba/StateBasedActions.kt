package arcanum.engine.sba

import arcanum.engine.Progress
import arcanum.engine.change.Recorder
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Continuation
import arcanum.engine.notYet

/**
 * State-based actions (CR 704): checked whenever a player would get priority, all at once, repeated until none apply
 * (CR 704.3); the legend rule asks which to keep ([Continuation.StateBased]).
 */
internal object StateBasedActions {
    /** Performs every state-based action that applies, once (CR 704.3); [Progress.Done] when none did. */
    fun check(recorder: Recorder): Progress = notYet("state-based actions")

    fun resume(recorder: Recorder, continuation: Continuation.StateBased, answer: Answer): Progress = notYet("legend rule")
}
