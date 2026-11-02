package arcanum.engine.sets.blb

import arcanum.api.CounterKind
import arcanum.engine.cards.Cards
import arcanum.engine.scenario.scenario
import kotlin.test.Test

/** Bloomburrow's life-gained-and-lost wordings, played with invented cards of the parser's chunk 07. */
class BlbLifeMattersScenariosTest {
    private val slinger = Cards.invent("Crested Boltslinger", "{3}{R}", "Creature — Lizard Archer", """
        Reach, menace
        This creature enters with a +1/+1 counter on it if an opponent lost life this turn.
    """, "3/3")
    private val watcher = Cards.invent("Moonphase Watcher", "{3}{W}", "Creature — Bat Cleric", """
        Flying, vigilance
        Whenever you gain or lose life during your turn, this creature gets +1/+0 until end of turn.
    """, "2/4")
    private val spark = Cards.invent("Twin Spark", "{1}{R}", "Instant", "Twin Spark deals 1 damage to any target.")
    private val salve = Cards.invent("Small Salve", "{W}", "Instant", "You gain 2 life.")

    private val p1p1 = CounterKind.PowerToughness(1, 1)

    /** It enters with the counter only once an opponent has lost life this turn. */
    // CR 614.1c, 614.12: checked as it enters
    @Test
    fun entersWithCounterAfterOpponentLostLife() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(spark, slinger)

        alice.casts(spark, target = "bob")
        allPass()
        alice.casts(slinger)
        allPass()
        expect { card(slinger).counters(p1p1) shouldBe 1 }
    }

    @Test
    fun entersWithoutCounterOtherwise() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(slinger)

        alice.casts(slinger)
        allPass()
        expect { card(slinger).counters(p1p1) shouldBe 0 }
    }

    /** Gaining life on your turn triggers it; on another player's turn it doesn't. */
    // CR 603.2: "during your turn" is checked as it would trigger
    @Test
    fun gainOrLoseOnlyDuringYourTurn() = scenario {
        alice.battlefield(watcher, "Plains", "Plains")
        alice.hand(salve)

        alice.casts(salve)
        allPass()
        expect { stack.size shouldBe 1 }
        allPass()
        expect { watcher.power shouldBe 3 }
    }

    @Test
    fun notOnAnotherPlayersTurn() = scenario {
        alice.battlefield(watcher, "Plains")
        alice.hand(salve)
        turn(1, active = bob, priority = alice)

        alice.casts(salve)
        allPass()
        expect { stack.isEmpty() }
    }
}
