package arcanum.engine.sets.inr

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.engine.cards.Cards
import arcanum.engine.cards.face
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.rtr.resolveAll
import kotlin.test.Test

/** INR chunk 12: investigate and "whenever you sacrifice a Clue" (CR 701.16a, 701.21), a disturb Aura cast from the graveyard (CR 702.146a). */
class InrChunk12InvestigateDisturbScenariosTest {
    private val line = "(Create a Clue token. It's an artifact with \"{2}, Sacrifice this token: Draw a card.\")"
    private val tracker = Cards.invent("Tireless Stalker", "{2}{G}", "Creature — Human Scout", """
        Landfall — Whenever a land you control enters, investigate. $line
        Whenever you sacrifice a Clue, put a +1/+1 counter on this creature.
    """, "3/2")
    private val bobClue = Cards.invent("Bob Clue", null, "Artifact — Clue", "{2}, Sacrifice this artifact: Draw a card.")
    private val forest = Cards.invent("Stalker Forest", null, "Basic Land — Forest", "")

    // CR 207.2c landfall, 701.16a: a land makes a Clue; sacrificing it to its own ability makes the Tracker grow (CR 701.21a)
    @Test
    fun landMakesClueAndSacrificingItGrowsTheTracker() = scenario(players = 3) {
        alice.battlefield(tracker, "Forest", "Forest")
        alice.hand(forest)
        alice.library("Forest", "Forest")
        alice.plays(forest)
        resolveAll()
        expect { alice.battlefield has "Clue Token" }
        alice.activates("Clue Token", choosing = listOf())
        resolveAll()
        expect { "Tireless Stalker".counters(CounterKind.PowerToughness(1, 1)) shouldBe 1 }
    }

    // CR 701.21a: only a Clue its controller sacrifices counts, an opponent's Clue doesn't
    @Test
    fun opponentsSacrificedClueDoesNotCount() = scenario(players = 3) {
        alice.battlefield(tracker)
        bob.battlefield(bobClue, "Forest", "Forest")
        bob.library("Forest", "Forest")
        alice.passes()
        bob.activates(bobClue)
        resolveAll()
        expect {
            bob.battlefield has "Forest"
            bob.graveyard has bobClue
            "Tireless Stalker".counters(CounterKind.PowerToughness(1, 1)) shouldBe 0
        }
    }

    // CR 702.146a, 303.4f: cast transformed from the graveyard, the Aura needs a creature to enchant and gives its bonus
    @Test
    fun disturbAuraCastFromGraveyard() = scenario {
        val bearer = Cards.inventFaces("Transform",
            face("Lantern Carrier", "{U}", "Creature — Spirit",
                "Flying\nDisturb {2}{U} (You may cast this card from your graveyard transformed for its disturb cost.)", "1/1", null, null),
            face("Lanterns' Boost", null, "Enchantment — Aura",
                "Enchant creature\nEnchanted creature gets +1/+1 and has flying.\nIf Lanterns' Boost would be put into a graveyard from anywhere, exile it instead.",
                null, null, "U"))
        alice.battlefield("Island", "Island", "Island", tracker)
        alice.graveyard(bearer)
        alice.casts(bearer, target = tracker)
        resolveAll()
        expect {
            alice.battlefield has bearer
            "Tireless Stalker" hasKeyword Keyword.Flying.kind
        }
    }
}
