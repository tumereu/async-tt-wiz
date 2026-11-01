package arcanum.engine.casting

import arcanum.engine.Progress
import arcanum.engine.change.Recorder
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Continuation
import arcanum.engine.input.Input
import arcanum.engine.notYet

/**
 * Playing lands (CR 305), casting spells (CR 601.2) and activating abilities (CR 602.2), special actions (CR 116.2):
 * announcing, modes, X, targets, costs and mana payment ([arcanum.engine.mana]), each choice a decision
 * ([Continuation.Announcing]); cancelling rewinds the whole cast with [arcanum.engine.change.Change.Reverted] (D58).
 */
internal object Casting {
    fun playLand(recorder: Recorder, input: Input.PlayLand): Progress = notYet("playing a land")

    fun cast(recorder: Recorder, input: Input.Cast): Progress = notYet("casting")

    fun activate(recorder: Recorder, input: Input.Activate): Progress = notYet("activating")

    fun resume(recorder: Recorder, continuation: Continuation.Announcing, answer: Answer): Progress = notYet("casting decisions")

    /** [arcanum.engine.input.Input.Cancel]: takes back an open cast or activation (CR 601.2). */
    fun cancel(recorder: Recorder, continuation: Continuation): Progress = notYet("cancelling a cast")
}
