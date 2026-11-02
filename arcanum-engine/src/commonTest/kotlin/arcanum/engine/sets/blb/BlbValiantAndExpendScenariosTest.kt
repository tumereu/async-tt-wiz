package arcanum.engine.sets.blb

import arcanum.api.CounterKind
import arcanum.engine.cards.Cards
import arcanum.engine.scenario.scenario
import kotlin.test.Test

/** Bloomburrow's valiant (an ability word, CR 207.2c, on a first-time-each-turn trigger) and expend (CR 700.14). */
class BlbValiantAndExpendScenariosTest {
    private val champion = Cards.invent("Hearthflame Champion", "{R}", "Creature — Mouse Soldier", """
        Valiant — Whenever this creature becomes the target of a spell or ability you control for the first time each turn, put a +1/+1 counter on it.
        When this creature dies, it deals damage equal to its power to each opponent.
    """, "1/1")
    private val boost = Cards.invent("Quick Boost", "{R}", "Instant", "Target creature gets +1/+0 until end of turn.")
    private val hurler = Cards.invent("Kettle Hurler", "{3}{R}", "Creature — Raccoon Warrior", """
        Menace
        Whenever you expend 4, this creature deals 2 damage to each opponent. (You expend 4 as you spend your fourth total mana to cast spells during a turn.)
    """, "3/4")
    private val spark = Cards.invent("Twin Spark", "{1}{R}", "Instant", "Twin Spark deals 1 damage to any target.")

    private val p1p1 = CounterKind.PowerToughness(1, 1)

    /** Only the first time each turn its controller's spell targets it. */
    // CR 603.2, 115.10: "for the first time each turn"
    @Test
    fun valiantOncePerTurn() = scenario {
        alice.battlefield("Mountain", "Mountain", champion)
        alice.hand(boost, boost)

        alice.casts(boost, target = champion)
        allPass()
        allPass()
        alice.casts(boost, target = champion)
        allPass()
        expect {
            card(champion).counters(p1p1) shouldBe 1
            stack.isEmpty()
        }
    }

    /** An opponent's spell targeting it doesn't count. */
    // CR 115.10: "a spell or ability you control"
    @Test
    fun valiantNotByOpponents() = scenario {
        alice.battlefield(champion)
        bob.battlefield("Mountain")
        bob.hand(boost)
        turn(1, active = bob)

        bob.casts(boost, target = champion)
        allPass()
        expect { card(champion).counters(p1p1) shouldBe 0 }
    }

    /** Expend 4 triggers once, as the fourth mana spent on spells this turn is paid. */
    // CR 700.14
    @Test
    fun expendFourTriggersOnce() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain", hurler)
        alice.hand(spark, spark, spark)

        alice.casts(spark, target = "bob")
        allPass()
        expect { bob.life shouldBe 19 }
        alice.casts(spark, target = "bob")
        // the expend trigger goes on the Stack above the spell
        expect { stack.size shouldBe 2 }
        allPass()
        allPass()
        expect { bob.life shouldBe 16 }
        alice.casts(spark, target = "bob")
        allPass()
        expect { bob.life shouldBe 15 }
    }
}
