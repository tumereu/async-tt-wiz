package arcanum.engine.sets.otj

import arcanum.api.CounterKind
import arcanum.api.Mechanic
import arcanum.api.Value
import arcanum.engine.cards.Cards
import arcanum.engine.scenario.CardRef
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.ltr.tokens
import kotlin.test.Test

/** OTJ chunk 07: Treasure makers. */
class OtjChunk07TreasureScenariosTest {
    private val defiler = Cards.invent("Ossuary Defiler", "{3}{B}", "Creature — Zombie Mercenary", """
        Menace
        {1}{B}, Sacrifice another creature: Put a +1/+1 counter on this creature. If an outlaw was sacrificed this way, create a Treasure token. (Assassins, Mercenaries, Pirates, Rogues, and Warlocks are outlaws.)
    """, "3/4")
    private val stampede = Cards.invent("Glitter Stampede", "{1}{G}", "Instant",
        "Create a Treasure token. Until end of turn, up to one target creature gets +2/+2 for each Treasure you control.")
    private val hydra = Cards.invent("Nuggetback Hydra", "{X}{G}", "Creature — Hydra", """
        Vigilance, trample, haste
        This creature enters with X +1/+1 counters on it.
        When this creature dies, create a number of tapped Treasure tokens equal to its power.
    """, "0/0")
    private val outrider = Cards.invent("Dustwind Outrider", "{1}{B}", "Creature — Human Rogue", "", "2/2")
    private val lizard = Cards.invent("Mesa Lizard", "{1}", "Creature — Lizard", "", "1/1")
    private val coyote = Cards.invent("Brush Coyote", "{1}{G}", "Creature — Coyote", "", "2/2")
    private val scorch = Cards.invent("Canyon Scorch", "{R}", "Instant", "Canyon Scorch deals 6 damage to target creature.")
    private val p1p1 = CounterKind.PowerToughness(1, 1)

    // CR 608.2h, 700.12: the sacrificed Rogue was an outlaw: a counter and a Treasure
    @Test
    fun sacrificingAnOutlawMakesATreasure() = scenario {
        alice.battlefield(defiler, outrider, lizard, "Swamp", "Swamp")
        alice.activates(defiler, choosing = listOf(outrider))
        resolveAll()
        expect {
            defiler.counters(p1p1) shouldBe 1
            outrider.zone shouldBe arcanum.api.Zone.Graveyard
        }
        tokens("Treasure Token").size shouldBe 1
    }

    // CR 608.2h: a Lizard isn't an outlaw: the counter only
    @Test
    fun sacrificingAnotherCreatureOnlyPutsACounter() = scenario {
        alice.battlefield(defiler, outrider, lizard, "Swamp", "Swamp")
        alice.activates(defiler, choosing = listOf(lizard))
        resolveAll()
        expect { defiler.counters(p1p1) shouldBe 1 }
        tokens("Treasure Token").size shouldBe 0
    }

    // CR 608.2c: the Treasure it creates counts; CR 611.2c: each bonus is fixed as its spell resolves (+2/+2, then +4/+4)
    @Test
    fun theBonusCountsTreasuresAsItResolves() = scenario {
        alice.battlefield(coyote)
        alice.hand(stampede, stampede)
        alice.castsWithoutCosts(stampede, target = coyote)
        resolveAll()
        expect { coyote.power shouldBe 4; coyote.toughness shouldBe 4 }
        alice.castsWithoutCosts(stampede, target = coyote)
        resolveAll()
        expect { coyote.power shouldBe 8; coyote.toughness shouldBe 8 }
        tokens("Treasure Token").size shouldBe 2
    }

    // CR 608.2h, 603.10a: "its power" is its power as it last existed: three counters and +2/+0 make five tapped Treasures
    @Test
    fun diesIntoTappedTreasuresEqualToItsLastPower() = scenario {
        alice.battlefield(hydra, counters = listOf(p1p1 to 3))
        alice.hand(scorch)
        effect(alice, Mechanic.ModifyPowerToughness(Value.Fixed(2), Value.Fixed(0)), on = listOf(hydra))
        alice.castsWithoutCosts(scorch, target = hydra)
        resolveAll()
        val treasures = tokens("Treasure Token")
        treasures.size shouldBe 5
        expect {
            hydra.zone shouldBe arcanum.api.Zone.Graveyard
            treasures.all { CardRef(it, "Treasure").tapped } shouldBe true
        }
    }

    private infix fun <T> T.shouldBe(expected: T) = kotlin.test.assertEquals(expected, this)
}
