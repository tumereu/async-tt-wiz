package arcanum.engine.query

import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** What abilities and effects read about an object after it has left its zone (CR 608.2h, 607.2a). */
class LastKnownInformationTest {
    private val dart = Cards.invent("Grave Dart", "{1}{B}", "Instant", "Destroy target creature.")
    private val bear = Cards.invent("Mossback Cub", "{1}{G}", "Creature — Bear", "", "2/2")

    // CR 607.2a: a leaves-the-battlefield ability finds the card its linked enters ability exiled, though the source
    // is a new object in the graveyard by then
    @Test
    fun linkedAbilityReadsWhatTheSourceRememberedAfterItLeft() = scenario {
        alice.hand("Hypnotic Fiend", dart)
        bob.hand("Storm Spark")

        alice.castsWithoutCosts("Hypnotic Fiend")
        resolveStack()
        expect { bob.exile has "Storm Spark" }

        alice.castsWithoutCosts(dart, target = "Hypnotic Fiend")
        resolveStack()
        expect {
            alice.graveyard has "Hypnotic Fiend"
            bob.hand has "Storm Spark"
        }
    }

    // CR 608.2h, 101.2: "spells with the same name as that creature" reads the creature's name after it left the
    // battlefield
    @Test
    fun cantCastSpellsNamedLikeACreatureThatLeft() = scenario {
        alice.hand("Mirrorward Mage")
        bob.battlefield(bear, "Forest", "Forest")
        alice.library("Island")
        bob.library("Forest")

        alice.castsWithoutCosts("Mirrorward Mage")
        resolveStack()
        expect { bob.hand has bear }

        advanceTo(TurnStep.PrecombatMain, turn = 2)
        assertFailsWith<IllegalArgumentException> { bob.casts(bear) }
    }
}
