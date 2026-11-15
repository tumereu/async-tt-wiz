package arcanum.engine.sets.trk

import arcanum.engine.cards.Cards
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.rtr.may
import kotlin.test.Test

/** Star Trek chunk 13 in play: a Planet land entering tapped and gaining life, a shock land paid for or not with three players, and a draw replacement. */
class TrkChunk13LandsScenariosTest {
    private val planet = Cards.invent("Tarsus Outpost", null, "Land — Planet", """
        This land enters tapped.
        When this land enters, you gain 1 life.
        {T}: Add {U} or {R}.
    """.trimIndent())
    private val shock = Cards.invent("Vulcan Hot Springs", null, "Land — Island Mountain", """
        ({T}: Add {U} or {R}.)
        As this land enters, you may pay 2 life. If you don't, it enters tapped.
    """.trimIndent())
    private val spock = Cards.invent("Mr. Tuvok, Science Officer", "{2}{U}{U}", "Legendary Creature — Vulcan Human Scientist", """
        If you would draw a card, draw two cards instead.
    """.trimIndent(), "2/4")

    private val cantrip = Cards.invent("Tarsus Insight", "{U}", "Sorcery", "Draw a card.")

    // CR 614.11: its controller's draw is doubled; with three players the opponents' libraries are untouched
    @Test
    fun drawIsDoubled() = scenario(players = 3) {
        alice.battlefield(spock, "Island")
        alice.hand(cantrip)
        alice.library("Island", "Island", "Island")
        bob.library("Island", "Island", "Island")
        alice.casts(cantrip)
        resolveAll()
        expect {
            alice.hand.size shouldBe 2
            bob.hand.size shouldBe 0
        }
    }

    // CR 614.1c (enters tapped), 603.2 (the life trigger)
    @Test
    fun planetEntersTappedAndGainsLife() = scenario(players = 3) {
        alice.hand(planet)
        alice.plays(planet)
        resolveAll()
        expect {
            planet.tapped shouldBe true
            alice.life shouldBe 21
            bob.life shouldBe 20
            carol.life shouldBe 20
        }
    }

    // CR 614.12: as it enters, pay 2 life or enter tapped
    @Test
    fun shockLandPaidEntersUntapped() = scenario(players = 3) {
        alice.hand(shock)
        alice.plays(shock)
        may(true)
        expect {
            shock.tapped shouldBe false
            alice.life shouldBe 18
            bob.life shouldBe 20
        }
    }

    @Test
    fun shockLandUnpaidEntersTapped() = scenario(players = 3) {
        alice.hand(shock)
        alice.plays(shock)
        may(false)
        expect {
            shock.tapped shouldBe true
            alice.life shouldBe 20
        }
    }
}
