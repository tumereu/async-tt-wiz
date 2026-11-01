package arcanum.engine.resolution

import arcanum.engine.Progress
import arcanum.engine.change.Recorder
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Continuation
import arcanum.engine.notYet

/**
 * Resolving the top of the Stack (CR 608): checking targets (CR 608.2b), running its steps one committed step at a
 * time ([Continuation.Resolving], D31, D58), effects and their replacements (CR 614, 616; [Continuation.Event]), and
 * the spell's card leaving the Stack (CR 608.2n).
 */
internal object Resolution {
    /** Resolves the top object of the Stack (CR 117.4, 608.1). */
    fun resolveTop(recorder: Recorder): Progress = notYet("resolving")

    fun resume(recorder: Recorder, continuation: Continuation.Resolving, answer: Answer): Progress = notYet("resolution decisions")

    fun resumeEvent(recorder: Recorder, continuation: Continuation.Event, answer: Answer): Progress = notYet("replacement decisions")
}
