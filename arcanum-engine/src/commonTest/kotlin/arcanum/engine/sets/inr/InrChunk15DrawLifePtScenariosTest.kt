package arcanum.engine.sets.inr

import arcanum.engine.cards.Cards
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.rtr.resolveAll
import kotlin.test.Test

/** INR chunk 15: tap-creatures costs with a lord, gaining control for good with an added creature type, and draining each opponent.
 * Invented names carry the oracle text. */
class InrChunk15DrawLifePtScenariosTest {
    private val vampireLord = Cards.invent("Captive Noble", "{1}{B}{B}", "Creature — Vampire", """
        Other Vampire creatures you control get +1/+1.
        Tap five untapped Vampires you control: Gain control of target creature. It becomes a Vampire in addition to its other types.
    """, "2/2")
    private val vampire = Cards.invent("Dusk Courtier", "{1}{B}", "Creature — Vampire", "", "2/2")
    private val ox = Cards.invent("Plodding Steer", "{2}", "Creature — Ox", "", "2/2")
    private val drainer = Cards.invent("Siege Corpse", "{1}{B}", "Creature — Zombie", """
        Tap three untapped creatures you control: Each opponent loses 1 life.
    """, "2/2")
    private val zombie = Cards.invent("Shambling Hulk", "{1}{B}", "Creature — Zombie", "", "2/2")

    // CR 613.1b: control change; CR 613.1d, 205.3: the added subtype makes the stolen creature a Vampire, so the lord's +1/+1
    // applies to it (CR 613.4c); CR 611.2a: the effect has no end
    @Test
    fun vampireLord_stealsForGoodAndGainsTheType() = scenario {
        alice.battlefield(vampireLord, vampire, vampire, vampire, vampire)
        bob.battlefield(ox)
        alice.activates(vampireLord, target = ox, choosing = listOf(vampireLord, vampire, vampire, vampire, vampire))
        resolveAll()
        expect {
            ox.controller shouldBe alice.id
            ox.power shouldBe 3
            vampire.power shouldBe 3
        }
    }

    // CR 119.3, 102.2: each opponent loses 1 life, with three players both do; CR 602.2b: the three tapped creatures are the cost
    @Test
    fun drainer_eachOpponentLosesLife() = scenario(players = 3) {
        alice.battlefield(drainer, zombie, zombie)
        alice.activates(drainer, choosing = listOf(drainer, zombie, zombie))
        resolveAll()
        expect {
            alice.life shouldBe 20
            bob.life shouldBe 19
            players[2].life shouldBe 19
        }
    }
}
