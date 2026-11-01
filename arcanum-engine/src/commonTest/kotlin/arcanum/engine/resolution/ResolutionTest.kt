package arcanum.engine.resolution

import arcanum.api.Zone
import arcanum.engine.scenario.scenario
import kotlin.test.Test

/** Resolving spells and abilities (CR 608): targets, fizzling, where the card goes, permanents entering. */
class ResolutionTest {
    // CR 120.3a, 608.2n: damage to a player is life loss; the instant goes to its owner's graveyard
    @Test
    fun burnToAPlayer() = scenario {
        alice.hand("Storm Spark")

        alice.castsWithoutCosts("Storm Spark", target = "bob")
        resolve()

        expect {
            bob.life shouldBe 17
            alice.graveyard has "Storm Spark"
            stack.isEmpty()
        }
    }

    // CR 704.5g, 120.3e: lethal damage destroys the creature as a state-based action
    @Test
    fun burnKillsACreature() = scenario {
        alice.hand("Storm Spark")
        bob.battlefield("Umbral Elves")

        alice.castsWithoutCosts("Storm Spark", target = "Umbral Elves")
        resolve()

        expect {
            bob.graveyard has "Umbral Elves"
            log mentions "Died"
        }
    }

    // CR 608.2b: a spell whose only target is gone doesn't resolve and goes to the graveyard
    @Test
    fun fizzlesWhenItsTargetIsGone() = scenario {
        alice.hand("Storm Spark")
        bob.battlefield("Umbral Elves")

        alice.castsWithoutCosts("Storm Spark", target = "Umbral Elves")
        move("Umbral Elves", to = Zone.Hand)
        resolve()

        expect {
            alice.graveyard has "Storm Spark"
            bob.hand has "Umbral Elves"
            log mentions "Fizzled"
        }
    }

    // CR 701.6a: a countered spell goes to its owner's graveyard without resolving
    @Test
    fun counterspell() = scenario {
        alice.hand("Storm Spark")
        bob.hand("Denial")

        alice.castsWithoutCosts("Storm Spark", target = "bob")
        bob.castsWithoutCosts("Denial", target = "Storm Spark")
        resolve()

        expect {
            alice.graveyard has "Storm Spark"
            bob.graveyard has "Denial"
            bob.life shouldBe 20
            stack.isEmpty()
        }
    }

    // CR 608.3: a permanent spell enters the battlefield; its enters trigger goes on the Stack (CR 603.3)
    @Test
    fun creatureEntersAndItsTriggerDraws() = scenario {
        alice.hand("Canopy Visionary")
        alice.library("Forest")

        alice.castsWithoutCosts("Canopy Visionary")
        resolve()

        expect {
            alice.battlefield has "Canopy Visionary"
            stack.names shouldBe listOf("ability of Canopy Visionary")
        }
        resolve()
        expect {
            alice.hand has "Forest"
            stack.isEmpty()
        }
    }
}
