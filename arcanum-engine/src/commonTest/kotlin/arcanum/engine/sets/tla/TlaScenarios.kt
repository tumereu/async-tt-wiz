package arcanum.engine.sets.tla

import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario

/** Passes priority until the Stack is empty or a decision other than ordering triggers is open; triggers put on the Stack at
 * once are ordered as listed (CR 603.3b). */
internal fun Scenario.resolveAll() {
    var guard = 60
    while (guard-- > 0) {
        val open = pending
        if (open is Decision.OrderTriggers) { answer(Answer.Order(open.triggers.indices.toList())); continue }
        if (open != null || state.stack.isEmpty()) return
        allPass()
    }
}

/** Answers an open target decision with the named objects or players. */
internal fun Scenario.targets(vararg names: String) { answer(Answer.Targets(names.map { target(it) })); resolveAll() }
