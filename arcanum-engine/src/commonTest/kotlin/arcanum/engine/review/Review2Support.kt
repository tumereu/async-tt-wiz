package arcanum.engine.review

import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.change.Change
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.ScenarioPlayer
import arcanum.engine.state.GameState
import arcanum.engine.state.ObjectId
import kotlin.test.fail

/** Helpers for the second adversarial review's scenarios. */

/** Every host fill (D60) the scenario's moves asked for so far, in order. */
internal fun Scenario.hostFillsAsked(): List<Decision.HostFill> =
    applied.filterIsInstance<Change.PendingSet>().mapNotNull { it.after?.decision as? Decision.HostFill }

/** The objects every host fill asked for so far. */
internal fun Scenario.filledIds(): List<ObjectId> = hostFillsAsked().flatMap { it.objects }

/** Whether the object now in [zone] named [name] is unknown in the state (its definition not filled in, D60). */
internal fun Scenario.unknownIn(name: String, zone: Zone, index: Int? = null): Boolean =
    state.objOrThrow(card(name, index = index, zone = zone)).unknown

/** Cards on the battlefield the caller can't see (D60), face down when [faceDown]: an opponent's morph. */
internal fun Scenario.hiddenBattlefield(owner: ScenarioPlayer, vararg names: String, faceDown: Boolean = true): List<ObjectId> =
    put(owner, names.toList(), Zone.Battlefield, hidden = true, faceDown = faceDown)

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
