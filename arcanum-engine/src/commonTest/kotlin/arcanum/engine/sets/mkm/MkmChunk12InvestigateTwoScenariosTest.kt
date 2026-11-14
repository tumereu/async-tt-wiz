package arcanum.engine.sets.mkm

import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.tdm.Plain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** MKM chunk 12: investigate (CR 701.16a), part 2: abilities that care about Clues and sacrificed artifacts. */
class MkmChunk12InvestigateTwoScenariosTest {
    private val proof = Cards.invent("Alquist Proof", "{1}{W}{U}", "Legendary Creature — Human Detective", """
        Vigilance
        When Alquist Proof enters, investigate. (Create a Clue token. It's an artifact with "{2}, Sacrifice this token: Draw a card.")
        {X}{W}{U}{U}, {T}, Sacrifice a Clue: You draw X cards and gain X life.
    """, "3/3")
    private val pouch = Cards.invent("Detective's Pouch", "{2}{U}{R}", "Artifact", """
        When this artifact enters, investigate twice. (To investigate, create a Clue token. It's an artifact with "{2}, Sacrifice this token: Draw a card.")
        {T}: Create a 1/1 colorless Thopter artifact creature token with flying. Activate only if you've sacrificed an artifact this turn.
    """)
    private val inspector = Cards.invent("Homicide Inspector", "{1}{B}", "Creature — Human Detective",
        """Whenever one or more nontoken creatures you control die, investigate. This ability triggers only once each turn. (Create a Clue token. It's an artifact with "{2}, Sacrifice this token: Draw a card.")""",
        "2/2")
    private val satchel = Cards.invent("Evidence Satchel", "{1}", "Artifact — Clue", "{2}, Sacrifice this artifact: Draw a card.")

    private fun Scenario.clues(): Int = state.objects.values.count { o -> o.zone == Zone.Battlefield && nameOf(o) == "Clue Token" }

    // CR 107.3a, 601.2f: X is announced and paid with the {W}{U}{U}; CR 118.3: a Clue must be sacrificed as a cost
    @Test
    fun proofSacrificesAClueToDrawAndGainX() = scenario {
        alice.battlefield(proof, satchel, "Plains", "Plains", "Plains", "Island", "Island", "Island")
        alice.library("Island", "Island", "Island", "Island")
        bob.library("Swamp", "Swamp", "Swamp")
        alice.activates(proof, x = 2)
        resolveAll()
        expect {
            alice.hand.size shouldBe 2
            alice.life shouldBe 22
            satchel.zone shouldBe Zone.Graveyard
        }
    }

    // CR 118.3: with no Clue to sacrifice the cost can't be paid
    @Test
    fun proofNeedsAClue() = scenario {
        alice.battlefield(proof, "Plains", "Plains", "Plains", "Island", "Island", "Island")
        alice.library("Island", "Island")
        bob.library("Swamp", "Swamp")
        assertFailsWith<IllegalArgumentException> { alice.activates(proof, x = 2) }
    }

    // CR 602.5b: "activate only if" is checked on activation; CR 701.21 a sacrificed Clue is an artifact sacrificed
    @Test
    fun pouchMakesAThopterOnlyAfterAnArtifactWasSacrificed() = scenario {
        alice.battlefield(pouch, satchel, "Island", "Island")
        alice.library("Island", "Island", "Island")
        bob.library("Swamp", "Swamp", "Swamp")
        assertFailsWith<IllegalArgumentException> { alice.activates(pouch) }
        alice.activates(satchel)
        resolveAll()
        alice.activates(pouch)
        resolveAll()
        expect { alice.battlefield has "Thopter Token" }
    }

    // CR 603.2c, 111.1: a creature's death triggers it once a turn; the second death the same turn adds no Clue
    @Test
    fun inspectorInvestigatesOnlyOnceEachTurn() = scenario {
        alice.battlefield(inspector, Plain.bear, Plain.elf, "Swamp", "Swamp")
        alice.hand(Plain.doom, Plain.doom)
        alice.library("Island", "Island")
        bob.library("Swamp", "Swamp")
        assertEquals(0, clues())
        alice.casts(Plain.doom, target = Plain.bear)
        resolveAll()
        assertEquals(1, clues())
        alice.casts(Plain.doom, target = Plain.elf)
        resolveAll()
        assertEquals(1, clues())
    }
}
