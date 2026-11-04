package arcanum.engine.view

import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.input.Input
import arcanum.engine.scenario.scenario
import arcanum.engine.state.GameState
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A host that keeps a state's view (engine-responsiveness R2) gets the same results as one that has the engine work it
 * out each time: [arcanum.engine.Outcome.view] is the view of the state after the outcome's changes, [Engine.perform]
 * given the state's view gives the same outcome, the [Engine.visible] and [Engine.revealed] overloads given views
 * give the same sets, and the option searches given the view ([Engine.options], [Engine.passCheck], [Engine.shouldPassFor],
 * R3) the same results. Each is compared with [Engine.view] worked out afresh, on seeded random games of cube cards (every
 * input listed for any player is tried, then one is played), with [Layers.checkShortcuts] on as in [ShortcutsTest].
 */
class KeptViewsTest {
    private var outcomes = 0
    private var carried = 0

    @Test
    fun keptViewsGiveTheSameResultsAsViewsWorkedOutAfresh() {
        Layers.checkShortcuts = true
        try {
            repeat(GAMES) { game(SEED + it) }
        } finally {
            Layers.checkShortcuts = false
        }
        // Most outcomes carry their view: the engine works out the state after its changes for state-based actions
        assertTrue(carried * 2 > outcomes, "views carried: $carried of $outcomes")
    }

    @Test
    fun theHookCountsEachTimeTheLayersAreWorkedOutInFull() = scenario {
        alice.battlefield("Elvish Hierophant", "Mountain")
        alice.hand("Storm Spark")
        var count = 0
        Engine.layersWorkedOut = { count++ }
        try {
            val view = Engine.view(state)
            assertEquals(1, count, "a host's state: worked out afresh")
            Engine.view(state)
            assertEquals(2, count, "a host's state keeps no view")
            Engine.visible(state, alice.id, view)
            Engine.revealed(state, emptyList(), alice.id, view, view)
            assertEquals(2, count, "given views aren't worked out again")
            Engine.visible(state, alice.id)
            assertEquals(3, count)
        } finally {
            Engine.layersWorkedOut = null
        }
    }

    private fun game(seed: Long) = scenario(seed = seed) {
        val rnd = Random(seed)
        val pool = Cards.cubeNames
        players.forEach { p ->
            p.battlefield(*(List(4) { BASICS.random(rnd) } + List(3) { pool.random(rnd) }).toTypedArray())
            p.hand(*List(5) { pool.random(rnd) }.toTypedArray())
            p.library(*List(12) { (pool + BASICS).random(rnd) }.toTypedArray())
        }
        repeat(STEPS) {
            if (Engine.result(state) != null) return@scenario
            val view = Engine.view(state)
            for (p in players) assertEquals(Engine.visible(state, p.id), Engine.visible(state, p.id, view), "visible with the view")
            // The option searches started from the state's view: the same lists and pass checks
            for (p in players) {
                assertEquals(Engine.options(state, p.id), Engine.options(state, p.id, view), "options with the view")
                assertEquals(Engine.passCheck(state, p.id), Engine.passCheck(state, p.id, view), "passCheck with the view")
                assertEquals(Engine.shouldPassFor(state, p.id), Engine.shouldPassFor(state, p.id, view), "shouldPassFor with the view")
            }
            val listed = players.flatMap { p -> Engine.options(state, p.id) }.distinct()
            if (listed.isEmpty()) return@scenario
            listed.forEach { input -> check(state, view, input) }
            val doing = listed.filterNot { it is Input.PassPriority }
            perform(if (doing.isNotEmpty() && rnd.nextInt(3) > 0) doing.random(rnd) else listed.random(rnd))
        }
    }

    /** [input] performed on [state] (whose view is [view]) without and with the view: the same outcome, and each view
     * carried is the view of the state after it; [Engine.revealed] with the views gives the same cards. */
    private fun arcanum.engine.scenario.Scenario.check(state: GameState, view: View, input: Input) {
        val plain = runCatching { Engine.perform(state, input) }.getOrNull() ?: return
        val given = Engine.perform(state, input, view = view)
        assertEquals(plain, given, "perform with the state's view: $input")
        val after = state.deepCopy().also { s -> plain.changes.forEach(s::apply) }
        val fresh = Engine.view(after)
        outcomes++
        plain.view?.let { carried++; assertEquals(fresh, it, "Outcome.view: $input") }
        given.view?.let { assertEquals(fresh, it, "Outcome.view with the state's view given: $input") }
        for (p in players) assertEquals(Engine.revealed(state, plain.changes, p.id),
            Engine.revealed(state, plain.changes, p.id, view, fresh), "revealed with the views: $input")
    }

    private companion object {
        const val GAMES = 3
        const val STEPS = 30
        const val SEED = 9_200L
        val BASICS = listOf("Plains", "Island", "Swamp", "Mountain", "Forest")
    }
}
