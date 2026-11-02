package arcanum.engine

import arcanum.engine.cards.Cards
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.state.GameState
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * The contract of [Engine.options] tried on many games: seeded random games of cube cards (lands and permanents on the
 * battlefield, cards in hand and library), where at every state each input listed for any player is performed on the
 * state as it is and must be accepted (no refusal, no crash) with an outcome that reverts exactly (D59); then one of
 * them, at random, is played. A game with nothing listed is stuck and fails, and so does an open player decision that
 * lists no answer (cancelling the cast doesn't count: every decision reached has an answer). Random results and host
 * fills are the host's (D25, D60) and answered by the harness.
 */
class OptionsFuzzTest {
    @Test
    fun everyListedInputIsAcceptedAndNoGameGetsStuck() {
        repeat(GAMES) { game(SEED + it) }
    }

    private fun game(seed: Long) = scenario(seed = seed) {
        val rnd = Random(seed)
        val pool = Cards.cubeNames
        players.forEach { p ->
            p.battlefield(*(List(4) { BASICS.random(rnd) } + List(2) { pool.random(rnd) }).toTypedArray())
            p.hand(*List(5) { pool.random(rnd) }.toTypedArray())
            p.library(*List(12) { (pool + BASICS).random(rnd) }.toTypedArray())
        }
        repeat(STEPS) { step ->
            if (Engine.result(state) != null) return@scenario
            val listed = players.flatMap { p -> Engine.options(state, p.id) }.distinct()
            if (listed.isEmpty()) fail("Game $seed, step $step: stuck at ${pending ?: state.turn} with nothing listed")
            val open = pending
            if (open != null && open !is Decision.Random && open !is Decision.HostFill && open !is Decision.OtherView &&
                listed.none { it is Input.Answer }) {
                fail("Game $seed, step $step: no answer listed for $open (only $listed)")
            }
            listed.forEach { tryOn(state, it, "Game $seed, step $step") }
            play(listed, rnd)
        }
    }

    /** [input] is accepted on [state] and its outcome reverts to [state] (D56, D59). */
    private fun tryOn(state: GameState, input: Input, where: String) {
        val outcome = try {
            Engine.perform(state, input)
        } catch (e: Throwable) {
            throw AssertionError("$where: listed $input was refused: ${e::class.simpleName}: ${e.message}", e)
        }
        val copy = state.deepCopy()
        outcome.changes.forEach { copy.apply(it) }
        outcome.changes.asReversed().forEach { copy.revert(it) }
        copy.timestampCounter = state.timestampCounter
        assertEquals(state, copy, "$where: reverting the outcome of $input didn't restore the state (D59)")
    }

    /** Plays one listed input, passing less often than doing something so games move on. */
    private fun Scenario.play(listed: List<Input>, rnd: Random) {
        val doing = listed.filterNot { it is Input.PassPriority }
        val input = if (doing.isNotEmpty() && rnd.nextInt(3) > 0) doing.random(rnd) else listed.random(rnd)
        perform(input)
        check(pending !is Decision.Random && pending !is Decision.HostFill) { "The harness answers these" }
    }

    companion object {
        private const val GAMES = 60
        private const val STEPS = 50
        private const val SEED = 7_000L
        private val BASICS = listOf("Plains", "Island", "Swamp", "Mountain", "Forest")
    }
}
