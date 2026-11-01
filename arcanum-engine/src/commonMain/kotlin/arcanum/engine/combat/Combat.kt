package arcanum.engine.combat

import arcanum.engine.Progress
import arcanum.engine.change.Recorder
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Continuation
import arcanum.engine.input.Input
import arcanum.engine.notYet

/**
 * Combat (CR 506–511): declaring attackers and blockers with their restrictions and costs, combat damage with first
 * and double strike, trample, deathtouch and lifelink, damage assignment among blockers
 * ([arcanum.engine.decision.TurnAction.CombatDamage]). Turn-based actions with a choice resume here.
 */
internal object Combat {
    fun declareAttackers(recorder: Recorder, input: Input.DeclareAttackers): Progress = notYet("declaring attackers")

    fun declareBlockers(recorder: Recorder, input: Input.DeclareBlockers): Progress = notYet("declaring blockers")

    fun resume(recorder: Recorder, continuation: Continuation.TurnBased, answer: Answer): Progress = notYet("turn-based decisions")
}
