package arcanum.engine.sets.fra

import arcanum.api.CounterKind
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Reality Fracture's empower Jace (CR 701.71a) and the cards around Jace tokens and loyalty abilities. */
class FraEmpowerJaceScenariosTest {
    private val orb = Cards.invent("Thoughtseeker Orb", "{2}{U}", "Creature — Homunculus",
        "When this creature enters, empower Jace 4. (Put four loyalty counters on a Jace token you control. If you don't control one, first create a blue Jace planeswalker token with \"[−1]: Surveil 1\" and \"[−3]: Draw a card.\")", "2/1")
    private val rewrite = Cards.invent("Rewrite the Cosmos", "{4}{B}{B}", "Sorcery",
        "Exile all creatures. Empower Jace X, where X is the number of creatures exiled this way.")
    private val echoes = Cards.invent("Brutal Reverberation", "{2}{R}{R}", "Instant",
        "Brutal Reverberation deals 6 damage to target creature or planeswalker. If excess damage was dealt to that permanent this way, empower Jace X, where X is that excess damage.")
    private val machinations = Cards.invent("Sculptor's Stratagem", "{2}{U}", "Instant", """
        Until end of turn, you may activate loyalty abilities of Jace planeswalkers you control on any player's turn any time you could cast an instant.
        Empower Jace 8.
    """)
    private val lurker = Cards.invent("Shrine Prowler", "{2}{B}", "Creature — Horror", """
        When this creature enters, empower Jace 1.
        Planeswalkers you control aren't put into their owners' graveyards for having 0 loyalty.
        Planeswalkers you control have "[+2]: This planeswalker deals 1 damage to each opponent and you gain 1 life."
    """, "3/2")
    private val tetherer = Cards.invent("Kindled Tetherer", "{2}{G}", "Creature — Elf Warrior", """
        Whenever you put one or more loyalty counters on a planeswalker, put a +1/+1 counter on this creature.
        {6}: Empower Jace 2.
    """, "3/2")
    private val sculptor = Cards.invent("Path of the Thoughtshaper", "{4}{U}", "Legendary Enchantment", """
        When Path of the Thoughtshaper enters, empower Jace 5.
        Whenever you activate a loyalty ability, if you removed two or more loyalty counters to activate it, draw a card.
    """)
    private val countershape = Cards.invent("Countershape", "{U}{U}", "Instant", """
        As an additional cost to cast this spell, behold a Jace or pay {1}. (To behold a Jace, choose a Jace you control or reveal a Jace card from your hand.)
        Counter target spell. Empower Jace 1.
    """)
    private val refuge = Cards.invent("Scholar's Refuge", null, "Land — Island", """
        ({T}: Add {U}.)
        As this land enters, you may behold a Jace. If you don't, this land enters tapped. (To behold a Jace, choose a Jace you control or reveal a Jace card from your hand.)
        {2}{U}, {T}: Empower Jace 2.
    """)
    private val contradiction = Cards.invent("Path of the Contradiction", "{2}{G}", "Legendary Enchantment", """
        When Path of the Contradiction enters, empower Jace 5.
        Whenever you activate a loyalty ability, you gain 1 life. You may play an additional land this turn.
    """)
    private val forsworn = Cards.invent("Bastien the Forsworn", "{2}{B}", "Legendary Creature — Human Mercenary", """
        Ward—Discard a card.
        Whenever a creature an opponent controls enters, Bastien deals 1 damage to that player.
        Whenever an opponent activates a loyalty ability, Bastien deals 1 damage to that player.
    """, "3/3")
    private val iren = Cards.invent("Iren, Planeswalker of Proofs", "{1}{U}{U}", "Legendary Planeswalker — Jace", "[+1]: Draw a card.", loyalty = "3")
    private val bear = Cards.invent("Market Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val study = Cards.invent("Deep Study", "{2}{U}", "Sorcery", "Draw two cards.")

    private val jace = "Jace Token"
    private fun Scenario.loyaltyOf(name: String): Int = state.obj(card(name, zone = Zone.Battlefield))!!.counters(CounterKind.Loyalty)

    /** The first empower creates a blue Jace token and puts the counters on it; the next puts more on the same token. */
    // CR 701.71a
    @Test
    fun empowerCreatesThenGrowsTheToken() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island", "Island")
        alice.hand(orb, orb)
        alice.casts(orb, paying = listOf("Island", "Island", "Island"))
        allPass()
        allPass()
        assertEquals(4, loyaltyOf(jace))
        alice.casts(orb, paying = listOf("Island#3", "Island#4", "Island#5"))
        allPass()
        allPass()
        assertEquals(8, loyaltyOf(jace))
        expect {
            jace.subtypes.map { it.name } shouldBe listOf("Jace")
            jace.colors shouldBe listOf(arcanum.api.Color.Blue)
        }
    }

    /** The Jace token's loyalty abilities: [−3]: Draw a card. */
    // CR 701.71a, 606.3
    @Test
    fun jaceTokenDraws() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.library(bear)
        alice.hand(orb)
        alice.casts(orb)
        allPass()
        allPass()
        alice.activates(jace, ability = 1)
        allPass()
        expect { alice.hand has "Market Bear" }
        assertEquals(1, loyaltyOf(jace))
        // CR 606.3: once each turn
        assertFailsWith<IllegalArgumentException> { alice.activates(jace, ability = 0) }
    }

    /** Empower Jace 0 creates a token with 0 loyalty, which state-based actions put into the graveyard (it ceases to exist). */
    // CR 701.71a, 704.5i, 704.5d
    @Test
    fun empowerZeroLeavesNoToken() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(rewrite)
        alice.casts(rewrite)
        allPass()
        expect { battlefieldNames().none { it == jace } shouldBe true }
    }

    /** "Empower Jace X, where X is the number of creatures exiled this way" and "… that excess damage". */
    // CR 701.71a, 120.4a
    @Test
    fun empowerCountsWhatWasDone() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Mountain", "Mountain", "Mountain", "Mountain")
        bob.battlefield(bear, bear)
        alice.battlefield(bear)
        alice.hand(rewrite, echoes)
        bob.hand(bear)
        alice.casts(rewrite, paying = listOf("Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp"))
        allPass()
        assertEquals(3, loyaltyOf(jace))
        change(arcanum.engine.change.Changes.move(state, listOf(card("Market Bear", zone = Zone.Hand)), Zone.Battlefield,
            meanings = listOf(arcanum.engine.change.Meaning.Manual)))
        alice.casts(echoes, target = "Market Bear", paying = listOf("Mountain", "Mountain", "Mountain", "Mountain"))
        allPass()
        assertEquals(7, loyaltyOf(jace))
    }

    /** Loyalty abilities of Jaces at instant speed on another player's turn, once each turn still. */
    // CR 606.3
    @Test
    fun machinationsLetsJaceActOnAnyTurn() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.library(bear, bear)
        bob.library(bear)
        alice.hand(machinations)
        advanceTo(TurnStep.Upkeep, turn = 2)
        passPriority()
        alice.casts(machinations)
        allPass()
        assertEquals(8, loyaltyOf(jace))
        bob.passes()
        assertTrue(Engine.options(state, alice.id).any { it is Input.Activate })
        alice.activates(jace, ability = 1)
        allPass()
        assertEquals(5, loyaltyOf(jace))
        expect { alice.hand has "Market Bear" }
    }

    /** Planeswalkers stay with 0 loyalty, and have the granted [+2] ability. */
    // CR 704.5i, 613.1f
    @Test
    fun lurkerKeepsJaceAtZero() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.library(bear)
        alice.hand(lurker)
        alice.casts(lurker)
        allPass()
        allPass()
        assertEquals(1, loyaltyOf(jace))
        alice.activates(jace, ability = 0)
        allPass()
        assertEquals(0, loyaltyOf(jace))
        alice.chooses()
        expect { battlefieldNames().contains(jace) shouldBe true }
    }

    /** The granted [+2]: 1 damage to each opponent and 1 life. */
    // CR 613.1f, 606.4
    @Test
    fun lurkerGrantsPlusTwo() = scenario {
        alice.battlefield(lurker)
        alice.battlefield(iren, counters = listOf(CounterKind.Loyalty to 3))
        alice.activates(iren, ability = 1)
        allPass()
        expect {
            bob.life shouldBe 19
            alice.life shouldBe 21
        }
        assertEquals(5, loyaltyOf(iren))
    }

    /** "Whenever you put one or more loyalty counters on a planeswalker": empowering and a + loyalty cost both count. */
    // CR 606.4, 122.6
    @Test
    fun tethererCountsLoyaltyCounters() = scenario {
        alice.battlefield(tetherer, "Forest", "Forest", "Forest", "Forest", "Forest", "Forest")
        alice.battlefield(iren, counters = listOf(CounterKind.Loyalty to 3))
        alice.library(bear)
        alice.activates(iren, ability = 0)
        allPass()
        expect { tetherer.counters(CounterKind.PowerToughness(1, 1)) shouldBe 1 }
        alice.activates(tetherer)
        while (state.stack.isNotEmpty()) allPass()
        expect { tetherer.counters(CounterKind.PowerToughness(1, 1)) shouldBe 2 }
    }

    /** "If you removed two or more loyalty counters to activate it, draw a card." */
    // CR 606.4
    @Test
    fun thoughtshaperDrawsOnBigMinus() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island")
        alice.library(bear, bear, bear)
        alice.hand(sculptor)
        alice.casts(sculptor)
        allPass()
        allPass()
        assertEquals(5, loyaltyOf(jace))
        alice.activates(jace, ability = 1)
        while (state.stack.isNotEmpty()) allPass()
        expect { alice.hand.size shouldBe 2 }
    }

    /** Behold a Jace or pay {1}: with a Jace on the battlefield the spell costs {U}{U}. */
    // CR 701.4a, 601.2f
    @Test
    fun countershapeBeholdsAJace() = scenario {
        alice.battlefield("Island", "Island")
        alice.battlefield(iren, counters = listOf(CounterKind.Loyalty to 3))
        bob.battlefield("Island", "Island", "Island")
        alice.hand(countershape)
        bob.hand(study)
        bob.library(bear, bear)
        turn(active = bob)
        bob.casts(study)
        bob.passes()
        alice.casts(countershape, target = "Deep Study", option = "Behold", choosing = listOf("Iren, Planeswalker of Proofs"))
        allPass()
        expect {
            bob.graveyard has "Deep Study"
            alice.graveyard has "Countershape"
            bob.hand.size shouldBe 0
        }
        assertEquals(1, loyaltyOf(jace))
    }

    /** "As this land enters, you may behold a Jace. If you don't, this land enters tapped." */
    // CR 614.12, 701.4a
    @Test
    fun refugeEntersUntappedWithAJace() = scenario {
        alice.hand(refuge, iren)
        alice.plays(refuge)
        if (pending is arcanum.engine.decision.Decision.May) alice.answers(arcanum.engine.decision.Answer.YesNo(true))
        if (pending != null) alice.chooses("Iren, Planeswalker of Proofs")
        expect { "Scholar's Refuge".tapped shouldBe false }
    }

    /** Without a Jace it enters tapped. */
    // CR 614.12
    @Test
    fun refugeEntersTappedWithoutAJace() = scenario {
        alice.hand(refuge)
        alice.plays(refuge)
        expect { "Scholar's Refuge".tapped shouldBe true }
    }

    /** Activating a loyalty ability: 1 life and an additional land play this turn. */
    // CR 305.2
    @Test
    fun contradictionGivesALandPlay() = scenario {
        alice.battlefield(contradiction)
        alice.battlefield(iren, counters = listOf(CounterKind.Loyalty to 3))
        alice.library(bear)
        alice.hand("Forest", "Island")
        alice.plays("Forest")
        alice.activates(iren, ability = 0)
        while (state.stack.isNotEmpty()) allPass()
        expect { alice.life shouldBe 21 }
        alice.plays("Island")
        expect { card("Island").zone shouldBe Zone.Battlefield }
    }

    /** An opponent activating a loyalty ability, or a creature of theirs entering, is dealt 1 damage. */
    // CR 606, 603.2
    @Test
    fun forswornPunishesLoyaltyAndCreatures() = scenario {
        bob.battlefield(forsworn)
        alice.battlefield("Forest", "Forest")
        alice.battlefield(iren, counters = listOf(CounterKind.Loyalty to 3))
        alice.library(bear)
        alice.hand(bear)
        alice.activates(iren, ability = 0)
        while (state.stack.isNotEmpty()) allPass()
        expect { alice.life shouldBe 19 }
        alice.casts(bear)
        allPass()
        allPass()
        expect { alice.life shouldBe 18 }
    }

    private fun Scenario.battlefieldNames(): List<String> = state.battlefield.mapNotNull { id -> state.obj(id)?.let { nameOf(it) } }
}
