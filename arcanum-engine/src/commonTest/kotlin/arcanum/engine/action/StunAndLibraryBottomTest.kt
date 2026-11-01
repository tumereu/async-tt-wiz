package arcanum.engine.action

import arcanum.api.CounterKind
import arcanum.engine.cards.Cards
import arcanum.engine.scenario.scenario
import kotlin.test.Test

/** Stun counters against untapping by an effect, and cards put on the bottom of the library they are in. */
class StunAndLibraryBottomTest {
    private val ogre = Cards.invent("Hulking Mudogre", "{3}{R}", "Creature — Ogre", "", "4/4")
    private val bear = Cards.invent("Mossback Cub", "{1}{G}", "Creature — Bear", "", "2/2")

    // CR 122.1d: an effect that would untap a permanent with a stun counter removes a stun counter instead
    @Test
    fun stunCounterStopsAnUntapEffect() = scenario {
        alice.battlefield("Canopy Tender")
        alice.battlefield("Forest", tapped = true, counters = listOf(CounterKind.Stun to 1))

        alice.activatesWithoutCosts("Canopy Tender", target = "Forest")
        resolve()

        expect {
            "Forest".tapped shouldBe true
            "Forest".counters(CounterKind.Stun) shouldBe 0
        }
    }

    // CR 401.4: revealed cards put on the bottom of the library they came from, in a random order
    @Test
    fun revealedCardsGoToTheBottomOfTheirOwnLibrary() = scenario {
        alice.battlefield("Thessa, Beastwatcher")
        bob.library(ogre, "Forest", bear)

        alice.activatesWithoutCosts("Thessa, Beastwatcher", target = "bob", x = 2)
        resolve()
        // Neither the Ogre (mana value 4) nor the Forest can be chosen: both go to the bottom

        expect {
            bob.library.size shouldBe 3
            bob.library.names.first() shouldBe bear
        }
    }
}
