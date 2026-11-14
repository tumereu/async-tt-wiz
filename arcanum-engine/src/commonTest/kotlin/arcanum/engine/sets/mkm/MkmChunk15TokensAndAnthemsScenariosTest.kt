package arcanum.engine.sets.mkm

import arcanum.api.Color
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.ltr.resolveAll
import arcanum.engine.sets.tdm.Plain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** MKM chunk 15: a static ability granting a mana ability to creature tokens, a creature anthem. */
class MkmChunk15TokensAndAnthemsScenariosTest {
    private val roots = Cards.invent("Hollow Roots", "{B}{G}", "Enchantment", """
        Creature tokens you control have "{T}: Add one mana of any color."
        Whenever one or more creature cards leave your graveyard, create a 0/1 green Plant creature token, then put a +1/+1 counter on each Plant you control.
    """)
    private val rally = Cards.invent("Rally Call", "{1}{R}{W}", "Enchantment", """
        Creatures you control get +1/+1.
        Whenever a creature you control enters, this enchantment deals 1 damage to each opponent.
    """)
    private val strider = Cards.invent("Graveyard Golem", "{2}", "Artifact Creature — Golem", """
        {2}, Exile this card from your graveyard: Exile target card from a graveyard.
    """, "1/3")

    private fun Scenario.plants() = state.objects.values.filter { it.zone == Zone.Battlefield && nameOf(it) == "Plant Token" }

    // CR 603.2, 700.4: only cards leaving your own graveyard count, once for the batch (CR 603.2c); CR 613.1f: the token gains the mana ability
    @Test
    fun cardsLeavingYourGraveyardMakeAPlantThatMakesMana() = scenario(players = 3) {
        alice.battlefield(roots, Plain.bear, "Island", "Island")
        alice.graveyard(strider)
        bob.graveyard(Plain.ogre)
        alice.library("Swamp", "Swamp")
        bob.library("Swamp", "Swamp")
        carol.library("Swamp", "Swamp")
        alice.activates(strider, target = Plain.ogre)
        resolveAll()
        expect {
            strider.zone shouldBe Zone.Exile
            Plain.ogre.zone shouldBe Zone.Exile
        }
        assertEquals(1, plants().size)
        assertEquals(listOf(1), plants().single().counters.map { it.count })
        advanceTo(TurnStep.PrecombatMain, turn = 4)
        advanceTo(TurnStep.PrecombatMain, turn = 7)
        alice.activates("Plant Token", color = Color.Blue)
        expect { alice.pool.size shouldBe 1 }
    }

    // CR 613.1f: "creature tokens" only; a nontoken creature gets no mana ability
    @Test
    fun nontokenCreaturesGetNoManaAbility() = scenario {
        alice.battlefield(roots, Plain.bear)
        alice.library("Swamp", "Swamp")
        bob.library("Swamp", "Swamp")
        advanceTo(TurnStep.PrecombatMain, turn = 3)
        assertFailsWith<AssertionError> { alice.activates(Plain.bear, color = Color.Blue) }
    }

    // CR 613.4c (+1/+1 for creatures you control only); CR 603.2: each opponent takes 1 for every creature entering under your control
    @Test
    fun rallyCallPumpsYourCreaturesAndPingsEachOpponent() = scenario(players = 3) {
        alice.battlefield(rally, Plain.bear, "Mountain", "Mountain", "Mountain")
        bob.battlefield(Plain.elf)
        alice.hand(Plain.ogre)
        expect {
            Plain.bear.power shouldBe 3
            Plain.elf.power shouldBe 1
        }
        alice.casts(Plain.ogre)
        resolveAll()
        expect {
            Plain.ogre.power shouldBe 4
            alice.life shouldBe 20
            bob.life shouldBe 19
            carol.life shouldBe 19
        }
    }
}
