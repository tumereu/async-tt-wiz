package arcanum.engine.sets.blb

import arcanum.engine.cards.Cards
import arcanum.engine.scenario.scenario
import kotlin.test.Test

/** Bloomburrow's comparisons with an opponent and land wordings, played with invented cards of the parser's chunk 12. */
class BlbLegendsAndLandsScenariosTest {
    private val vessa = Cards.invent("Vessa, the Leaping Spring", "{2}{W}{W}", "Legendary Creature — Elemental Elk", """
        When Vessa enters, create a Treasure token if an opponent controls more lands than you. You gain 4 life if an opponent has more life than you. Create two 1/1 blue Fish creature tokens if an opponent controls more creatures than you. Draw a card if an opponent has more cards in hand than you.
    """, "4/5")
    private val pathway = Cards.invent("Storied Pathway", null, "Land",
        "{T}, Sacrifice this land: Search your library for a basic land card, put it onto the battlefield tapped, then shuffle. Then if you control four or more lands, untap that land.")
    private val vole = Cards.invent("Mossback Vole", "{2}{G}", "Creature — Mouse", "", "3/3")

    /** Behind on lands and life only: a Treasure and 4 life, no Fish and no card. */
    // CR 102.3
    @Test
    fun catchesUpWhereAnOpponentIsAhead() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains")
        alice.hand(vessa)
        alice.library("Island")
        alice.life = 15
        bob.battlefield("Forest", "Forest", "Forest", "Forest", "Forest")

        alice.casts(vessa)
        allPass()
        allPass()
        expect {
            alice.battlefield has "Treasure Token"
            alice.life shouldBe 19
            alice.battlefield.names.count { it == "Fish Token" } shouldBe 0
            alice.hand.size shouldBe 0
        }
    }

    /** With four lands after the search, the fetched land is untapped; with fewer it stays tapped. */
    // CR 608.2c
    @Test
    fun untapsWithFourLands() = scenario {
        alice.battlefield(pathway, "Plains", "Plains", "Plains")
        alice.library("Forest")

        alice.activates(pathway)
        allPass()
        alice.chooses("Forest")
        expect { card("Forest").tapped shouldBe false }
    }

    @Test
    fun staysTappedWithFewerLands() = scenario {
        alice.battlefield(pathway, vole)
        alice.library("Forest")

        alice.activates(pathway)
        allPass()
        alice.chooses("Forest")
        expect { card("Forest").tapped shouldBe true }
    }
}
