package arcanum.engine.sets.lci

import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.rtr.resolveAll
import kotlin.test.Test

/** LCI chunk 12 played: an attack trigger with an artifact-count condition, a tap-two-permanents cost that only reaches your own permanents,
 * and a +3/-3 pump that kills. Invented names carry the oracle text. */
class LciChunk12ApplyScenariosTest {
    private val blademaster = Cards.invent("Brass Blademaster", "{2}{R}", "Creature — Orc Pirate",
        "Whenever this creature attacks while you control two or more artifacts, it gets +2/+1 until end of turn.", "2/3")
    private val strider = Cards.invent("Ironfury Strider", "{4}{R}", "Artifact Creature — Golem", """
        Trample
        Tap two untapped artifacts and/or creatures you control: Target creature gets +2/+0 until end of turn. Activate only as a sorcery.
    """, "3/5")
    private val nautilus = Cards.invent("Hermit Nautilus", "{1}{U}", "Artifact Creature — Nautilus", """
        Vigilance
        {1}{U}: This creature gets +3/-3 until end of turn.
    """, "1/4")
    private val trinket = Cards.invent("Gilt Cog", "{1}", "Artifact", "")
    private val trinket2 = Cards.invent("Gilt Spring", "{1}", "Artifact", "")
    private val bear = Cards.invent("Meadow Cub", "{1}{G}", "Creature — Bear", "", "2/2")

    // CR 603.4: the Blademaster's own artifacts count; with two it gets +2/+1, itself an artifact-free creature
    @Test
    fun blademasterWithTwoArtifactsGetsThePump() = scenario {
        alice.battlefield(blademaster, trinket, trinket2)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(blademaster)
        resolveAll()
        expect {
            blademaster.power shouldBe 4
            blademaster.toughness shouldBe 4
        }
    }

    // CR 603.4: one artifact is not enough
    @Test
    fun blademasterWithOneArtifactStaysSmall() = scenario {
        alice.battlefield(blademaster, trinket)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(blademaster)
        resolveAll()
        expect {
            blademaster.power shouldBe 2
            blademaster.toughness shouldBe 3
        }
    }

    // CR 118.3, 701.26a: Alice taps her own Cub and Trinket; sorcery speed
    @Test
    fun striderTapsTwoOfYourOwnToPump() = scenario {
        alice.battlefield(strider, bear, trinket)
        alice.activates(strider, target = bear, choosing = listOf(bear, trinket))
        resolveAll()
        expect {
            bear.power shouldBe 4
            bear.tapped shouldBe true
            trinket.tapped shouldBe true
        }
    }

    // CR 118.3: an opponent's creature is never offered for the cost; Alice's Strider and Cog pay it
    @Test
    fun striderCannotTapAnOpponentsCreature() = scenario {
        alice.battlefield(strider, trinket)
        bob.battlefield(bear)
        alice.activates(strider, target = strider, choosing = listOf(trinket, strider))
        resolveAll()
        expect {
            bear.tapped shouldBe false
            trinket.tapped shouldBe true
            strider.power shouldBe 5
        }
    }

    // CR 704.5f: +3/-3 twice leaves toughness -2, so the Nautilus dies
    @Test
    fun nautilusPumpedTwiceDies() = scenario {
        alice.battlefield(nautilus, "Island", "Island", "Island", "Island")
        alice.activates(nautilus)
        resolveAll()
        expect {
            nautilus.power shouldBe 4
            nautilus.toughness shouldBe 1
        }
        alice.activates(nautilus)
        resolveAll()
        expect { alice.graveyard has nautilus }
    }
}
