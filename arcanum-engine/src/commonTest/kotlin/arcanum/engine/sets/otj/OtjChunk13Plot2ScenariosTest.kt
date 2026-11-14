package arcanum.engine.sets.otj

import arcanum.api.CounterKind
import arcanum.engine.cards.Cards
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.ltr.playOut
import kotlin.test.Test

/** OTJ chunk 13: plot cards whose other abilities this chunk plays out (plot itself is played in chunk 01). */
class OtjChunk13Plot2ScenariosTest {
    private val plotText = "Plot {3}{G} (You may pay {3}{G} and exile this card from your hand. Cast it as a sorcery on a later turn without paying its mana cost. Plot only as a sorcery.)"
    private val rhino = Cards.invent("Ridgeback Rhino", "{3}{G}{G}", "Creature — Rhino Warrior",
        "Reach, trample\nWhenever another creature you control enters, put X +1/+1 counters on it, where X is its power.\n$plotText", "5/5")
    private val ox = Cards.invent("Gladeside Ox", "{1}{G}", "Creature — Ox", "", "3/3")
    private val contractor = Cards.invent("Shady Contractor", "{2}{B}", "Creature — Human Assassin",
        "When this creature enters, you may sacrifice a creature. When you do, target player draws two cards and loses 2 life.\n" +
            "Plot {2}{B} (You may pay {2}{B} and exile this card from your hand. Cast it as a sorcery on a later turn without paying its mana cost. Plot only as a sorcery.)", "3/2")
    private val fodder = Cards.invent("Roadside Fodder", "{B}", "Creature — Rat", "", "1/1")

    private val p1p1 = CounterKind.PowerToughness(1, 1)

    // CR 603.2, 122.1: "its power" is the entering creature's (3), not the Rhino's (5); the Rhino itself gets none
    @Test
    fun anotherCreatureEntersWithCountersEqualToItsOwnPower() = scenario {
        alice.battlefield("Forest", "Forest", rhino)
        alice.hand(ox)
        alice.casts(ox)
        allPass()
        playOut()
        expect {
            ox.power shouldBe 6
            ox.counters(p1p1) shouldBe 3
            rhino.power shouldBe 5
        }
    }

    // CR 603.12: the reflexive trigger happens only if the sacrifice was paid, and its player is chosen then; three players
    @Test
    fun sacrificeThenTheChosenPlayerDrawsAndLoses() = scenario(players = 3) {
        players.forEach { it.library("Island", "Island", "Island", "Island") }
        alice.battlefield("Swamp", "Swamp", "Swamp", fodder)
        alice.hand(contractor)
        alice.casts(contractor)
        allPass()
        playOut("Roadside Fodder", "carol")
        expect {
            alice.graveyard has fodder
            carol.life shouldBe 18
            carol.hand.size shouldBe 2
            bob.life shouldBe 20
        }
    }

    // CR 603.12: declining the sacrifice means no reflexive trigger
    @Test
    fun decliningTheSacrificeDoesNothing() = scenario {
        players.forEach { it.library("Island", "Island", "Island", "Island") }
        alice.battlefield("Swamp", "Swamp", "Swamp", fodder)
        alice.hand(contractor)
        alice.casts(contractor)
        allPass()
        playOut("bob", may = false)
        expect {
            alice.battlefield has fodder
            bob.life shouldBe 20
            bob.hand.size shouldBe 0
        }
    }
}
