package arcanum.engine.view

import arcanum.api.CounterKind
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.scenario.scenario
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The work the engine saves (review 3 #6) changes no result. With [Layers.checkShortcuts], every shortcut is checked
 * against working it out in full: a view kept across changes (a perform's working copy, a cast's provisional view, the
 * view [Engine.options] performs its candidates from), an effect's resolution and a layer's effects kept while the
 * layer is applied, and every dependency not tried (CR 613.8a).
 */
class ShortcutsTest {
    private fun checking(block: () -> Unit) {
        Layers.checkShortcuts = true
        try {
            block()
        } finally {
            Layers.checkShortcuts = false
        }
    }

    // CR 613.4c, 613.7, 613.8a: lords and counters in layer 7c, a characteristic-defining ability in 7a
    @Test
    fun lordsAndCountersCastResolveAndOptions() = checking {
        scenario {
            alice.battlefield("Elvish Hierophant", "Bough-Crowned Oracle", "Lord of the Aetherforge", "Tinkered Mite", "Mountain")
            alice.battlefield("Elvish Seer", counters = listOf(CounterKind.PowerToughness(1, 1) to 1))
            alice.hand("Storm Spark")
            bob.battlefield("Thicketwood Elves", "Iron Foreman")

            expect {
                "Elvish Seer".power shouldBe 4
                "Elvish Hierophant".toughness shouldBe 3
                "Bough-Crowned Oracle".power shouldBe 2
                "Tinkered Mite".power shouldBe 2
                "Lord of the Aetherforge".power shouldBe 2
                "Thicketwood Elves".power shouldBe 1
            }
            assertTrue(Engine.options(state, alice.id).size > 1)

            alice.casts("Storm Spark", target = "Elvish Hierophant")
            allPass()

            expect {
                alice.graveyard has "Elvish Hierophant"
                "Elvish Seer".power shouldBe 3
                "Bough-Crowned Oracle".power shouldBe 1
            }
            assertTrue(Engine.options(state, alice.id).isNotEmpty())
        }
    }

    /** Seeded random games of cube cards: every input listed for any player is performed, then one is played. */
    @Test
    fun randomGames() = checking {
        repeat(GAMES) { game(SEED + it) }
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
            val listed = players.flatMap { p -> Engine.options(state, p.id) }.distinct()
            if (listed.isEmpty()) return@scenario
            listed.forEach { Engine.perform(state, it) }
            val doing = listed.filterNot { it is arcanum.engine.input.Input.PassPriority }
            perform(if (doing.isNotEmpty() && rnd.nextInt(3) > 0) doing.random(rnd) else listed.random(rnd))
        }
    }

    private companion object {
        const val GAMES = 3
        const val STEPS = 30
        const val SEED = 9_100L
        val BASICS = listOf("Plains", "Island", "Swamp", "Mountain", "Forest")
    }
}
