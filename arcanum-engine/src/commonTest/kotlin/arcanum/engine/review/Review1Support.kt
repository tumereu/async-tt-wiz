package arcanum.engine.review

import arcanum.engine.Engine
import arcanum.engine.Outcome
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.state.GameState
import kotlinx.serialization.json.Json
import kotlin.test.assertEquals
import kotlin.test.fail

/** Helpers for the first adversarial review's scenarios. */
internal object Review1 {
    /** Every input [Engine.options] lists for any player now is accepted by [Engine.perform] (README: "every listed
     * input accepted"); returns the refused ones with their messages. */
    fun refusedOptions(state: GameState): List<String> = state.players.flatMap { p ->
        Engine.options(state, p.id).mapNotNull { input ->
            try {
                Engine.perform(state.deepCopy(), input)
                null
            } catch (e: Throwable) {
                "$input -> ${e::class.simpleName}: ${e.message}"
            }
        }
    }

    /** A state that went to JSON and back (D58: the host stores the state and its pending continuation opaquely). */
    fun throughJson(state: GameState): GameState =
        Json.decodeFromString(GameState.serializer(), Json.encodeToString(GameState.serializer(), state))

    /** Performing [input] on the state as it is and on the state after a JSON round trip gives the same changes. */
    fun sameAfterJson(state: GameState, input: Input) {
        val restored = throughJson(state)
        assertEquals(state, restored, "The state changed through JSON")
        val direct = Engine.perform(state, input)
        val resumed = Engine.perform(restored, input)
        assertEquals(direct, resumed, "Resuming from the deserialized state gave other changes (D58)")
    }
}

/** Fails with every option that [Engine.perform] refuses. */
internal fun Scenario.expectEveryOptionAccepted() {
    val refused = Review1.refusedOptions(state)
    if (refused.isNotEmpty()) fail("Options refused by perform:\n" + refused.joinToString("\n"))
}

/** Performs [input] and checks that reverting just this outcome's changes gives back the state before it (D59). */
internal fun Scenario.performReverting(input: Input): Outcome {
    val before = state.deepCopy()
    val outcome = Engine.perform(state, input)
    val after = state.deepCopy()
    outcome.changes.forEach { after.apply(it) }
    outcome.changes.asReversed().forEach { after.revert(it) }
    after._timestampCounter = before.timestampCounter
    assertEquals(before, after, "Reverting one outcome didn't restore the state before it (D59)")
    return perform(input)
}
