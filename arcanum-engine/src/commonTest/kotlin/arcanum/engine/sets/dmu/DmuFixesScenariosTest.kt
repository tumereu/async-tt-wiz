package arcanum.engine.sets.dmu

import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.input.Input
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.decision.ManaUse
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The DMU fix step, played: a divided amount among "up to"/"any number of" targets (Ajani, Sleeper Agent's ruling), Karn's Sylex and Vesuvan
 * Duplimancy's copy of a target that has left the battlefield. The invented names carry the oracle text of Karn's Sylex and Vesuvan Duplimancy.
 */
class DmuFixesScenariosTest {
    private val bear = Cards.invent("Chunk Fix Test Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val ogre = Cards.invent("Chunk Fix Test Ogre", "{2}{R}", "Creature — Ogre", "", "3/3")
    private val cub = Cards.invent("Chunk Fix Test Cub", "{G}", "Creature — Bear", "", "1/1")

    // ---- Dividing among a variable number of targets

    private val volley = Cards.invent("Volley of Embers", "{X}{R}", "Instant",
        "Volley of Embers deals X damage divided as you choose among any number of target creatures.")
    private val warden = Cards.invent("Emberfield Warden", "{G}", "Creature — Elemental",
        "When this creature enters, it deals X damage divided as you choose among any number of target creatures and/or planeswalkers your opponents control, where X is the number of lands you control.", "1/1")

    private fun Scenario.announceX(x: Int) {
        while (pending is Decision.Announce) answer(Answer.Number(x))
    }

    // CR 601.2d: each target of a division gets at least one, so 1 damage can't be divided among two targets
    @Test
    fun aDividedAmountAllowsNoMoreTargetsThanItHas() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.hand(volley)
        bob.battlefield(bear, ogre)
        perform(Input.Cast(alice.id, card(volley)))
        announceX(1)
        assertEquals(1, decision<Decision.Targets>().max)
        assertFailsWith<IllegalArgumentException> { answer(Answer.Targets(listOf(target(bear), target(ogre)))) }
    }

    // CR 601.2d, 115.6: X = 0 damage can't go to any target; "any number of" allows choosing none
    @Test
    fun zeroToDivideMeansNoTargets() = scenario {
        alice.battlefield("Mountain")
        alice.hand(volley)
        bob.battlefield(bear)
        perform(Input.Cast(alice.id, card(volley)))
        announceX(0)
        assertEquals(0, decision<Decision.Targets>().max)
        assertFailsWith<IllegalArgumentException> { answer(Answer.Targets(listOf(target(bear)))) }
    }

    // CR 603.3d, 601.2d: a triggered ability's division too: one land, so one target at most
    @Test
    fun aTriggeredDivisionAllowsNoMoreTargetsThanItHas() = scenario {
        alice.battlefield("Forest")
        alice.hand(warden)
        bob.battlefield(bear, ogre)
        alice.casts(warden)
        resolve()
        assertEquals(1, decision<Decision.Targets>().max)
        answer(Answer.Targets(listOf(target(bear))))
        resolveAll()
        expect { bob.battlefield has bear }
        assertEquals(1, view()[card(bear)]?.obj?.damage)
    }

    // ---- Karn's Sylex

    private val sylex = Cards.invent("Sylex of Ending Echoes", "{3}", "Legendary Artifact", """
        Sylex of Ending Echoes enters tapped.
        Players can't pay life to cast spells or to activate abilities that aren't mana abilities.
        {X}, {T}, Exile Sylex of Ending Echoes: Destroy each nonland permanent with mana value X or less. Activate only as a sorcery.
    """)
    private val vow = Cards.invent("Vow of Bloodless Costs", "{3}", "Artifact", "Players can't pay life to cast spells or activate abilities.")
    private val tithe = Cards.invent("Ashen Pact Tithe", "{B/P}", "Sorcery", "Draw a card.")
    private val toll = Cards.invent("Toll of the Hollow Vow", "{1}", "Artifact", "{T}, Pay 2 life: Draw a card.")
    private val glyph = Cards.invent("Gilded Bloodtap Glyph", "{2}", "Artifact", "{T}, Pay 1 life: Add one mana of any color.")

    private fun Scenario.canCast(name: String): Boolean = Engine.options(state, alice.id).any { it is Input.Cast && it.card == card(name) }
    private fun Scenario.canActivate(name: String): Boolean = Engine.options(state, alice.id).any { it is Input.Activate && it.source == card(name) }

    // CR 107.4f, 119.4: a Phyrexian symbol is paid with mana or 2 life; the Sylex leaves only mana
    @Test
    fun sylexStopsPhyrexianLife() = scenario(players = 3) {
        alice.battlefield("Plains")
        alice.hand(tithe)
        assertTrue(canCast(tithe))
        carol.battlefield(sylex)
        assertFalse(canCast(tithe))
    }

    // CR 119.4, 601.2h: the payment offers no life for the symbol, and life given is refused
    @Test
    fun sylexOffersNoLifeForASymbol() = scenario {
        alice.battlefield(sylex, "Swamp")
        alice.hand(tithe)
        perform(Input.Cast(alice.id, card(tithe)))
        val pay = decision<Decision.ManaPayment>()
        assertTrue(pay.options.none { it is ManaUse.Life })
        assertFailsWith<IllegalArgumentException> { answer(Answer.ManaPayment(listOf(ManaUse.Life(0)))) }
    }

    // CR 602.2b, 119.4: a life cost of a non-mana ability can't be paid, under any player's Sylex
    @Test
    fun sylexStopsLifeForANonManaAbility() = scenario(players = 3) {
        alice.battlefield(toll)
        alice.library("Island")
        assertTrue(canActivate(toll))
        bob.battlefield(sylex)
        assertFalse(canActivate(toll))
        assertFailsWith<IllegalArgumentException> { alice.activates(toll) }
        expect { alice.life shouldBe 20 }
    }

    // CR 605.1a, 119.4: a mana ability's life cost still may be paid under the Sylex
    @Test
    fun sylexLetsAManaAbilityPayLife() = scenario {
        alice.battlefield(sylex, glyph)
        alice.hand(cub)
        alice.casts(cub, paying = listOf("$glyph:G"))
        resolveAll()
        expect {
            alice.battlefield has cub
            alice.life shouldBe 19
        }
    }

    // CR 605.1a, 119.4: "activate abilities" without the mana-ability exception includes mana abilities
    @Test
    fun withoutTheExceptionAManaAbilityCantPayLife() = scenario {
        alice.battlefield(vow, glyph)
        alice.hand(cub)
        assertFalse(canCast(cub))
        expect { alice.life shouldBe 20 }
    }

    // CR 614.1c: it enters tapped
    @Test
    fun sylexEntersTapped() = scenario {
        alice.battlefield("Plains", "Plains", "Plains")
        alice.hand(sylex)
        alice.casts(sylex)
        resolveAll()
        expect { sylex.tapped shouldBe true }
    }

    // CR 107.3, 602.2b: {X} with X = 2 destroys each nonland permanent of mana value 2 or less (lands stay), the Sylex exiled as a cost
    // (CR 118.3); CR 307.5: in a main phase with an empty Stack
    @Test
    fun sylexDestroysEachNonlandPermanentWithManaValueXOrLess() = scenario {
        alice.battlefield(sylex, "Plains", "Plains", cub)
        bob.battlefield(bear, ogre, "Forest")
        alice.activates(sylex, x = 2)
        resolveAll()
        expect {
            alice.exile has sylex
            alice.graveyard has cub
            bob.graveyard has bear
            bob.battlefield has ogre
            bob.battlefield has "Forest"
        }
    }

    // ---- Vesuvan Duplimancy

    private val duplication = Cards.invent("Mirrorcast Duplication", "{3}{U}", "Enchantment",
        "Whenever you cast a spell that targets only a single artifact or creature you control, create a token that's a copy of that artifact or creature, except it's not legendary.")
    private val paladin = Cards.invent("Chunk Fix Test Paladin", "{1}{W}", "Legendary Creature — Human Knight", "", "2/2")
    private val growth = Cards.invent("Chunk Fix Test Growth", "{G}", "Instant", "Target creature gets +2/+2 until end of turn.")
    private val murder = Cards.invent("Chunk Fix Test Murder", "{1}{B}{B}", "Instant", "Destroy target creature.")

    // CR 608.2h: the target has died before the trigger resolves, so the token copies it as it last existed on the battlefield
    @Test
    fun duplicationCopiesATargetThatHasLeft() = scenario {
        alice.battlefield(duplication, paladin, "Forest")
        alice.hand(growth)
        bob.hand(murder)
        alice.casts(growth, target = paladin)
        bob.castsWithoutCosts(murder, target = paladin)
        resolve()
        expect { alice.graveyard has paladin }
        resolveAll()
        val tokens = view().objects.values.filter { it.zone == Zone.Battlefield && it.obj?.isToken == true }
        assertEquals(1, tokens.size)
        assertEquals("Chunk Fix Test Paladin", tokens.single().characteristics!!.name)
        assertTrue(arcanum.api.Supertype.Legendary !in tokens.single().characteristics!!.supertypes)
    }
}
