package arcanum.engine.sets.dmr

import arcanum.engine.cards.Cards
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * Dominaria Remastered chunk 03 in play: threshold (CR 207.2c) as the conditions of the abilities themselves: an activation that
 * puts cards from the graveyard on the bottom of the library (CR 401.4), a static bonus on top of another (CR 613.4c), "all Squirrels"
 * whoever controls them, and "two target creatures".
 */
class DmrChunk03ThresholdScenariosTest {
    private val fodder = (1..10).map { Cards.invent("Dmr Threshold Fodder $it", "{0}", "Sorcery", "") }

    private val scrounger = Cards.invent("Scrounger of the Battlefield", "{3}{G}{G}", "Creature — Centaur", """
        Threshold — Put three cards from your graveyard on the bottom of your library: This creature gets +3/+3 until end of turn. Activate only once each turn and only if there are seven or more cards in your graveyard.
    """, "3/3")
    private val sacrament = Cards.invent("Hallowed Sacrament", "{1}{W}{W}", "Enchantment", """
        White creatures get +1/+1.
        Threshold — White creatures get an additional +1/+1 as long as there are seven or more cards in your graveyard.
    """)
    private val whiteKnight = Cards.invent("Dmr Threshold Knight", "{1}{W}", "Creature — Human Knight", "", "2/2")
    private val acorn = Cards.invent("Acorn Gatherer", "{5}{G}", "Creature — Human Druid", """
        At the beginning of your upkeep, you may create a 1/1 green Squirrel creature token.
        Threshold — All Squirrels get +2/+2 as long as there are seven or more cards in your graveyard.
    """, "1/1")
    private val squirrel = Cards.invent("Dmr Threshold Squirrel", "{G}", "Creature — Squirrel", "", "1/1")
    private val decoy = Cards.invent("Wandering Decoy", "{2}{W}", "Creature — Human Nomad", """
        {W}, {T}: Tap target creature.
        Threshold — {W}{W}, {T}: Tap two target creatures. Activate only if there are seven or more cards in your graveyard.
    """, "1/2")
    private val ox = Cards.invent("Dmr Threshold Ox", "{G}", "Creature — Ox", "", "2/2")
    private val ram = Cards.invent("Dmr Threshold Ram", "{G}", "Creature — Ram", "", "2/2")

    // CR 602.5b, 401.4: three graveyard cards go to the bottom of the library as the cost; once each turn; not with six cards
    @Test
    fun scroungerPutsThreeOnTheBottomOnceEachTurn() = scenario {
        alice.battlefield(scrounger)
        alice.graveyard(*fodder.toTypedArray())
        alice.library("Forest", "Island")
        bob.library("Forest")
        alice.activates(scrounger, choosing = listOf(fodder[0], fodder[1], fodder[2]))
        alice.chooses(fodder[0], fodder[1], fodder[2])
        allPass()
        expect {
            card(scrounger).power shouldBe 6
            alice.graveyard.size shouldBe 7
            alice.library.names.takeLast(3) shouldBe listOf(fodder[0], fodder[1], fodder[2])
            alice.library.size shouldBe 5
        }
        // seven cards remain, so only the once-each-turn restriction stops a second activation
        assertFailsWith<Throwable> { alice.activates(scrounger, choosing = listOf(fodder[3], fodder[4], fodder[5])) }
    }

    @Test
    fun scroungerNeedsSevenCardsInTheGraveyard() = scenario {
        alice.battlefield(scrounger)
        alice.graveyard(*fodder.take(6).toTypedArray())
        alice.library("Forest")
        bob.library("Forest")
        assertFailsWith<Throwable> { alice.activates(scrounger, choosing = listOf(fodder[0], fodder[1], fodder[2])) }
        expect { card(scrounger).power shouldBe 3 }
    }

    // CR 613.4c, 207.2c: +1/+1 always; with six cards in the graveyard no more
    @Test
    fun sacramentGivesOnlyTheFirstBonusAtSix() = scenario {
        alice.battlefield(sacrament, whiteKnight)
        alice.graveyard(*fodder.take(6).toTypedArray())
        alice.library("Forest")
        bob.library("Forest")
        expect { card(whiteKnight).power shouldBe 3 }
    }

    // CR 613.4c: a second +1/+1 at seven cards, for every white creature, an opponent's too
    @Test
    fun sacramentGivesAnAdditionalBonusAtSeven() = scenario {
        alice.battlefield(sacrament)
        bob.battlefield(whiteKnight)
        alice.graveyard(*fodder.toTypedArray())
        alice.library("Forest")
        bob.library("Forest")
        expect {
            card(whiteKnight).power shouldBe 4
            card(whiteKnight).toughness shouldBe 4
        }
    }

    // CR 205.3m, 207.2c: "all Squirrels" are every Squirrel on the battlefield, each opponent's included
    @Test
    fun allSquirrelsGetTheBonusWhoeverControlsThem() = scenario(players = 3) {
        alice.battlefield(acorn)
        bob.battlefield(squirrel)
        carol.battlefield(squirrel)
        alice.graveyard(*fodder.toTypedArray())
        alice.library("Forest")
        bob.library("Forest")
        carol.library("Forest")
        expect {
            card(squirrel, 0).power shouldBe 3
            card(squirrel, 1).power shouldBe 3
            card(acorn).power shouldBe 1
        }
    }

    // CR 115.1, 602.5b: "two target creatures" are both tapped; the ability needs seven cards in the graveyard
    @Test
    fun decoyTapsTwoTargetCreatures() = scenario {
        alice.battlefield(decoy, "Plains", "Plains")
        bob.battlefield(ox, ram)
        alice.graveyard(*fodder.toTypedArray())
        alice.library("Forest")
        bob.library("Forest")
        alice.activates(decoy, ability = 1, targets = listOf(ox, ram))
        allPass()
        expect {
            card(ox).tapped shouldBe true
            card(ram).tapped shouldBe true
        }
    }
}
