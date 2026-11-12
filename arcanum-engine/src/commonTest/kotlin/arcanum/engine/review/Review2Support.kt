package arcanum.engine.review

import arcanum.engine.Engine
import arcanum.engine.change.Change
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.state.GameState
import arcanum.engine.state.ObjectId
import kotlin.test.fail

/** Helpers for the second adversarial review's scenarios. */

/** Every [Change.Shown] the scenario's moves recorded so far, in order: the cards a player looked at, or everyone saw. */
internal fun Scenario.shownSoFar(): List<Change.Shown> = applied.filterIsInstance<Change.Shown>()

/** The objects every [Change.Shown] so far named. */
internal fun Scenario.shownIds(): List<ObjectId> = shownSoFar().flatMap { it.ids }

/** Plays [input] on a copy of the state up to the next decision of type [T] (answering nothing on the way), passing
 * priority for whoever holds it; returns the copy, its pending decision of that type. */
internal inline fun <reified T : Decision> Scenario.copyUntil(vararg inputs: Input, maxPasses: Int = 20): Pair<GameState, T> {
    val game = state.deepCopy()
    inputs.forEach { input -> Engine.perform(game, input).changes.forEach { game.apply(it) } }
    var guard = maxPasses
    while (game.pending?.decision !is T && guard-- > 0) {
        if (game.pending != null) fail("Waiting for ${game.pending!!.decision} before a ${T::class.simpleName}")
        val holder = game.priority.holder ?: fail("Stuck: ${game.turn}")
        Engine.perform(game, Input.PassPriority(holder)).changes.forEach { game.apply(it) }
    }
    return game to (game.pending?.decision as? T ?: fail("No ${T::class.simpleName} came"))
}
