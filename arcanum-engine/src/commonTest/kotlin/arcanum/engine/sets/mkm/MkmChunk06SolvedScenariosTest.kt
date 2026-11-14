package arcanum.engine.sets.mkm

import arcanum.api.CardType
import arcanum.api.Keyword
import arcanum.api.ObjectMark
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.ltr.playOut
import arcanum.engine.sets.ltr.resolveAll
import arcanum.engine.sets.ltr.unblocked
import arcanum.engine.sets.tdm.Plain
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** MKM chunk 06: Cases solved (CR 719.3a) by distinct damage sources, attacking creatures, creature cards put into graveyards (not tokens),
 * a suspected token gone, life gained; their solved abilities (CR 702.169b, d): a 4/4 creature, casting from the top of the library, and
 * creature cards in the graveyard castable until end of turn (only those there as it resolved, CR 611.2c). */
class MkmChunk06SolvedScenariosTest {
    private val veils = Cards.invent("Case of the Smoldering Veils", "{1}{R}{R}", "Enchantment — Case", """
        When this Case enters, it deals 3 damage to target creature an opponent controls.
        To solve — Three or more sources you controlled dealt damage this turn. (If unsolved, solve at the beginning of your end step.)
        Solved — Sacrifice this Case: Exile the top three cards of your library. Choose one of them. You may play that card this turn.
    """)
    private val tram = Cards.invent("Case of the Midnight Tram", "{1}{W}", "Enchantment — Case", """
        When this Case enters, choose target creature you don't control. Each creature you control deals 1 damage to that creature.
        To solve — Three or more creatures attacked this turn. (If unsolved, solve at the beginning of your end step.)
        Solved — Creatures you control get +1/+0.
    """)
    private val basilisk = Cards.invent("Case of the Basilisk Glance", "{B}", "Enchantment — Case", """
        When this Case enters, destroy up to one target creature that was dealt damage this turn.
        To solve — Three or more creature cards were put into graveyards from anywhere this turn. (If unsolved, solve at the beginning of your end step.)
        Solved — This Case is a 4/4 Gorgon creature with deathtouch and lifelink in addition to its other types.
    """)
    private val orangery = Cards.invent("Case of the Sealed Orangery", "{3}{G}", "Enchantment — Case", """
        You may play an additional land on each of your turns.
        To solve — You control seven or more lands. (If unsolved, solve at the beginning of your end step.)
        Solved — You may look at the top card of your library any time, and you may play lands and cast creature and enchantment spells from the top of your library.
    """)
    private val bones = Cards.invent("Case of the Hidden Bones", "{1}{B}", "Enchantment — Case", """
        When this Case enters, create a 2/1 black Skeleton creature token and suspect it. (It has menace and can't block.)
        To solve — You control no suspected Skeletons. (If unsolved, solve at the beginning of your end step.)
        Solved — {1}{B}, Sacrifice this Case: Search your library for a card, put it into your hand, then shuffle. Activate only as a sorcery.
    """)
    private val supper = Cards.invent("Case of the Untouched Supper", "{W}", "Enchantment — Case", """
        Whenever a creature you control enters, you gain 1 life.
        To solve — You've gained 5 or more life this turn. (If unsolved, solve at the beginning of your end step.)
        Solved — Sacrifice this Case: Creature cards in your graveyard gain "You may cast this card from your graveyard" until end of turn.
    """)
    private val duelist = Cards.invent("Two-Stroke Duelist", "{1}{R}", "Creature — Human Warrior", "Double strike", "1/1")
    private val sifting = Cards.invent("Grave Sifting", "{U}", "Sorcery", "Mill three cards.")
    private val charge = Cards.invent("Rallying Second Charge", "{R}", "Instant",
        "Untap one or two target creatures. After this phase, there is an additional combat phase.")

    private fun Scenario.solved(name: String): Boolean = state.obj(card(name))?.marks?.contains(ObjectMark.Solved) == true

    private fun Scenario.toEndStep() {
        advanceTo(TurnStep.End)
        resolveAll()
    }

    // CR 719.3a, 120.1: a spell and two attacking creatures are three sources; the spell counts though it is in the graveyard now
    @Test
    fun threeSourcesSolveTheCase() = scenario {
        alice.battlefield(veils, Plain.bear, Plain.ogre, "Mountain")
        bob.battlefield(Plain.giant)
        alice.hand(Plain.shock)
        alice.library("Mountain", "Mountain", "Mountain")
        bob.library("Swamp", "Swamp")
        alice.casts(Plain.shock, target = Plain.giant)
        resolveAll()
        unblocked(Plain.bear, Plain.ogre)
        advanceTo(TurnStep.CombatDamage)
        toEndStep()
        expect { bob.life shouldBe 15 }
        assertTrue(solved(veils))
    }

    // CR 719.3a, 702.4b: a double striker deals damage twice but is one source, so it and a second creature are only two
    @Test
    fun oneSourceDealingDamageTwiceCountsOnce() = scenario {
        alice.battlefield(veils, duelist, Plain.bear)
        alice.library("Mountain", "Mountain", "Mountain")
        bob.library("Swamp", "Swamp")
        unblocked(duelist, Plain.bear)
        advanceTo(TurnStep.CombatDamage)
        toEndStep()
        expect { bob.life shouldBe 16 }
        assertFalse(solved(veils))
        assertFailsWith<IllegalArgumentException> { alice.activates(veils) }
    }

    // CR 120.2: each creature you control deals 1 damage to the chosen creature; CR 719.3a: three creatures attacked; CR 702.169b, 613.4c:
    // solved, creatures you control get +1/+0
    @Test
    fun eachCreatureDealsOneDamageAndThreeAttackersSolve() = scenario {
        alice.battlefield(Plain.bear, Plain.ogre, Plain.elf, "Plains", "Plains")
        bob.battlefield(Plain.giant)
        alice.hand(tram)
        alice.library("Plains", "Plains", "Plains")
        bob.library("Swamp", "Swamp")
        alice.casts(tram)
        playOut(Plain.giant)
        expect { Plain.giant.view.obj!!.damage shouldBe 3 }
        unblocked(Plain.bear, Plain.ogre, Plain.elf)
        toEndStep()
        assertTrue(solved(tram))
        expect {
            Plain.bear.power shouldBe 3
            Plain.elf.power shouldBe 2
            Plain.giant.power shouldBe 5
        }
    }

    // CR 719.3a, 506.1: two creatures attacking in each of two combats are still two creatures that attacked, not four
    @Test
    fun aCreatureAttackingTwiceCountsOnce() = scenario {
        alice.battlefield(tram, Plain.bear, Plain.ogre, "Mountain")
        alice.hand(charge)
        alice.library("Mountain", "Mountain", "Mountain")
        bob.library("Swamp", "Swamp")
        unblocked(Plain.bear, Plain.ogre)
        advanceTo(TurnStep.CombatDamage)
        alice.casts(charge, targets = listOf(Plain.bear, Plain.ogre))
        resolveAll()
        unblocked(Plain.bear, Plain.ogre)
        toEndStep()
        expect { bob.life shouldBe 10 }
        assertFalse(solved(tram))
    }

    // CR 701.60a: the token the Case created is suspected; CR 111.7, 108.2: the token dying isn't a creature card put into a graveyard, so two
    // milled creature cards and the token are not three; the suspected Skeleton is gone, so the other Case is solved
    @Test
    fun aTokenIsNotACreatureCardButItsLossSolvesTheOtherCase() = scenario {
        alice.battlefield(basilisk, "Swamp", "Swamp", "Island", "Mountain")
        alice.hand(bones, sifting, Plain.shock)
        alice.library(Plain.bear, Plain.ogre, "Island", "Island", "Island")
        bob.library("Swamp", "Swamp")
        alice.casts(bones, paying = listOf("Swamp", "Swamp#1"))
        resolveAll()
        assertTrue(state.objects.values.any { it.zone == Zone.Battlefield && it.marks.contains(ObjectMark.Suspected) && nameOf(it) == "Skeleton Token" })
        alice.casts(Plain.shock, target = "Skeleton Token")
        resolveAll()
        alice.casts(sifting)
        resolveAll()
        toEndStep()
        assertFalse(solved(basilisk))
        assertTrue(solved(bones))
        expect { basilisk.types.contains(CardType.Creature) shouldBe false }
    }

    // CR 719.3a: three creature cards milled; CR 702.169b, 613.1d, f: solved, the Case is a 4/4 Gorgon creature with deathtouch and lifelink
    @Test
    fun threeCreatureCardsMilledMakeTheCaseAGorgon() = scenario {
        alice.battlefield(basilisk, "Island")
        alice.hand(sifting)
        alice.library(Plain.bear, Plain.ogre, Plain.elf, "Island", "Island")
        bob.library("Swamp", "Swamp")
        alice.casts(sifting)
        resolveAll()
        toEndStep()
        assertTrue(solved(basilisk))
        expect {
            basilisk.types.contains(CardType.Creature) shouldBe true
            basilisk.subtypes.map { it.name }.contains("Gorgon") shouldBe true
            basilisk.power shouldBe 4
            basilisk.toughness shouldBe 4
            basilisk hasKeyword Keyword.Deathtouch.kind
            basilisk hasKeyword Keyword.Lifelink.kind
        }
    }

    // CR 719.3a: seven lands; CR 702.169b, 401.5, 305.1, 601.3: solved, a land is played and an enchantment cast from the top of the library
    @Test
    fun solvedOrangeryPlaysAndCastsFromTheTopOfTheLibrary() = scenario {
        alice.battlefield(orangery, "Plains", "Plains", "Plains", "Plains", "Island", "Island", "Island")
        alice.library("Swamp", "Forest", Plain.charm, "Swamp")
        bob.library("Swamp", "Swamp")
        toEndStep()
        assertTrue(solved(orangery))
        advanceTo(TurnStep.PrecombatMain, turn = 3)
        alice.plays("Forest")
        alice.casts(Plain.charm)
        resolveAll()
        expect { Plain.charm.zone shouldBe Zone.Battlefield }
    }

    // CR 719.3a: 6 life gained; CR 702.169d, 611.2c, 113.6f: solved, the creature card in the graveyard can be cast from there this turn, the one
    // put there after the ability resolved can't
    @Test
    fun solvedSupperLetsCreatureCardsAlreadyInTheGraveyardBeCast() = scenario {
        alice.battlefield(supper, "Plains", "Plains", "Plains", "Mountain", "Forest", "Forest", "Forest")
        alice.hand(Plain.heal, Plain.heal, Plain.heal, Plain.shock)
        alice.graveyard(Plain.bear)
        alice.library("Plains", "Plains", "Plains")
        bob.library("Swamp", "Swamp")
        repeat(3) { alice.casts(Plain.heal); resolveAll() }
        toEndStep()
        assertTrue(solved(supper))
        advanceTo(TurnStep.PrecombatMain, turn = 3)
        alice.activates(supper)
        resolveAll()
        alice.casts(Plain.bear)
        resolveAll()
        expect { Plain.bear.zone shouldBe Zone.Battlefield }
        alice.casts(Plain.shock, target = Plain.bear)
        resolveAll()
        expect { Plain.bear.zone shouldBe Zone.Graveyard }
        assertFailsWith<IllegalArgumentException> { alice.casts(Plain.bear) }
    }
}
