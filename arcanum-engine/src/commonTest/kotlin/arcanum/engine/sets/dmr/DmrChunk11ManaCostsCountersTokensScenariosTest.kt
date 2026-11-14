package arcanum.engine.sets.dmr

import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.dgm.ownAlternative
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

/**
 * Dominaria Remastered chunk 11 in play: mana added from the hand and by a trigger, sacrificing lands, an optional flash cost,
 * an alternative cost, attack costs, "can't attack or block alone" and a base power and toughness counted when it resolves.
 */
class DmrChunk11ManaCostsCountersTokensScenariosTest {
    private val cub = Cards.invent("Hedge Cub", "{1}{G}", "Creature — Bear", "", "2/2")
    private val wisp = Cards.invent("Azure Wisp", "{U}", "Creature — Spirit", "", "1/1")
    private val sprite = Cards.invent("Verdant Sprite", "{G}", "Creature — Spirit", "", "1/1")
    private val stoker = Cards.invent("Ash Stoker", "{3}{R}", "Creature — Elemental", "When this creature enters, if you cast it from your hand, add {R}{R}{R}.", "3/3")
    private val guide = Cards.invent("Elvish Soul Guide", "{2}{G}", "Creature — Elf Spirit", "Exile this card from your hand: Add {G}.", "2/2")
    private val moraine = Cards.invent("Final Moraine", null, "Land", """
        {T}: Add {C}.
        {2}, {T}, Sacrifice this land: Search your library for a basic land card, put that card onto the battlefield tapped, then shuffle.
    """)
    private val rotation = Cards.invent("Field Rotation", "{G}", "Instant", """
        As an additional cost to cast this spell, sacrifice a land.
        Search your library for a land card, put that card onto the battlefield, then shuffle.
    """)
    private val symbiosis = Cards.invent("Sapling Symbiosis", "{3}{G}", "Sorcery", """
        You may cast this spell as though it had flash if you pay {2} more to cast it. (You may cast it any time you could cast an instant.)
        Create a 1/1 green Saproling creature token for each creature you control.
    """)
    private val force = Cards.invent("Force of Wit", "{3}{U}{U}", "Instant", """
        You may pay 1 life and exile a blue card from your hand rather than pay this spell's mana cost.
        Counter target spell.
    """)
    private val muse = Cards.invent("Windborne Muse", "{3}{W}", "Creature — Spirit", """
        Flying
        Creatures can't attack you unless their controller pays {2} for each creature they control that's attacking you.
    """, "2/3")
    private val beast = Cards.invent("Embered Beast", "{2}{R}", "Creature — Beast", "This creature can't attack or block alone.", "3/4")
    private val joral = Cards.invent("Joral, Mwonvuli Recluse", "{1}{G}", "Legendary Creature — Human Druid", """
        Whenever you draw your second card each turn, create a 2/2 green Cat creature token.
        {4}{G}{G}: Until end of turn, creatures you control have base power and toughness X/X, where X is the number of cards in your hand.
    """, "1/2")
    private val pair = Cards.invent("Draw Pair", "{U}", "Instant", "Draw two cards.")

    // CR 603.4, 106.4: cast from the hand, so the trigger's condition holds and the mana is added
    @Test
    fun ashStokerAddsManaWhenCastFromTheHand() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(stoker)
        alice.casts(stoker)
        resolveAll()
        expect { alice.pool.size shouldBe 3 }
    }

    // CR 602.2, 605.1a: a mana ability with a cost of exiling the card from the hand, used while paying for a spell (CR 601.2g)
    @Test
    fun elvishSoulGuideMakesManaFromTheHand() = scenario {
        alice.hand(guide)
        alice.activates(guide)
        expect {
            alice.pool.size shouldBe 1
            alice.exile has guide
        }
    }

    // CR 605.3a: mana abilities are activated before the spell's payment, the green mana pays {G}
    @Test
    fun elvishSoulGuidePaysForASpellAsItIsCast() = scenario {
        alice.hand(guide, sprite)
        alice.activates(guide)
        alice.casts(sprite)
        resolveAll()
        expect {
            alice.battlefield has sprite
            alice.pool.size shouldBe 0
        }
    }

    // CR 701.23, 118.8: the Moraine is sacrificed as a cost, then a basic land arrives tapped (CR 701.23b)
    @Test
    fun finalMoraineSacrificesForABasicLand() = scenario {
        alice.battlefield(moraine, "Island", "Island")
        alice.library("Forest", "Swamp")
        alice.activates(moraine, ability = 1)
        resolveAll()
        if (pending is Decision.ChooseObjects) alice.chooses("Forest")
        resolveAll()
        expect {
            alice.graveyard has moraine
            alice.battlefield has "Forest"
            "Forest".tapped shouldBe true
        }
    }

    // CR 118.8, 601.2h: the land is sacrificed as the spell is cast, so the search happens with one land fewer; the land comes untapped
    @Test
    fun fieldRotationSacrificesALandThenFetchesOne() = scenario {
        alice.battlefield("Forest", "Island")
        alice.hand(rotation)
        alice.library("Mountain")
        alice.casts(rotation, choosing = listOf("Island"))
        resolveAll()
        if (pending is Decision.ChooseObjects) alice.chooses("Mountain")
        resolveAll()
        expect {
            alice.graveyard has "Island"
            alice.battlefield has "Mountain"
            "Mountain".tapped shouldBe false
        }
    }

    // CR 702.8a, 601.2f: with {2} more it may be cast in combat, creating a token for each creature (counted as it resolves)
    @Test
    fun saplingSymbiosisCastWithFlashMakesATokenForEachCreature() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest", "Forest", cub, cub)
        alice.hand(symbiosis)
        advanceTo(TurnStep.BeginningOfCombat)
        alice.casts(symbiosis, kicked = 1)
        resolveAll()
        expect { alice.battlefield has "Saproling Token" }
        assertEquals(2, alice.battlefield.names.count { it == "Saproling Token" })
    }

    // CR 307.1: without paying the {2} more it is a sorcery
    @Test
    fun saplingSymbiosisIsASorceryWithoutTheExtraCost() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest", "Forest", cub)
        alice.hand(symbiosis)
        advanceTo(TurnStep.BeginningOfCombat)
        assertFails { alice.casts(symbiosis, kicked = 0) }
    }

    // CR 118.9, 118.3: pay 1 life and exile a blue card from the hand rather than pay the mana cost; the spell is countered
    @Test
    fun forceOfWitCountersForOneLifeAndABlueCard() = scenario {
        alice.hand(force, wisp)
        bob.battlefield("Forest", "Forest")
        bob.hand(cub)
        turn(number = 2, active = bob)
        bob.casts(cub)
        bob.passes()
        alice.casts(force, target = cub, way = ownAlternative(force), choosing = listOf(wisp))
        resolveAll()
        expect {
            alice.life shouldBe 19
            alice.exile has wisp
            bob.graveyard has cub
        }
    }

    // CR 508.1d, 508.1h: bob pays {2} for each creature attacking alice; attacking carol, who has no such enchantment, costs nothing (3 players)
    @Test
    fun windborneMuseTaxesOnlyAttacksAgainstItsController() = scenario(players = 3) {
        alice.battlefield(muse)
        bob.battlefield(cub, wisp, "Forest", "Forest", "Forest", "Forest")
        turn(number = 2, active = bob)
        advanceTo(TurnStep.DeclareAttackers)
        bob.attacks(cub, defender = "carol")
        expect { bob.pool.size shouldBe 0; "Forest".tapped shouldBe false }
    }

    @Test
    fun windborneMuseAttackAgainstItsControllerAsksForMana() = scenario(players = 3) {
        alice.battlefield(muse)
        bob.battlefield(cub, wisp, "Forest", "Forest", "Forest", "Forest")
        turn(number = 2, active = bob)
        advanceTo(TurnStep.DeclareAttackers)
        bob.attacks(cub, wisp, defender = "alice")
        val payment = decision<Decision.ManaPayment>()
        assertEquals(4, payment.suggestion.size)
    }

    // CR 508.1, 509.1: the Beast joins another attacker, and another blocker
    @Test
    fun emberedBeastCantAttackAlone() = scenario {
        alice.battlefield(beast, cub)
        advanceTo(TurnStep.DeclareAttackers)
        assertFails { alice.attacks(beast) }
        alice.attacks(beast, cub)
    }

    @Test
    fun emberedBeastCantBlockAlone() = scenario {
        alice.battlefield(cub, cub)
        bob.battlefield(beast, wisp)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(cub)
        allPass()
        assertFails { bob.blocks(beast to cub) }
        bob.blocks(beast to cub, wisp to cub)
    }

    // CR 608.2h: X (the cards in hand) is found as the ability resolves and stays through the turn, even when the hand changes
    @Test
    fun joralSetsBasePowerAndToughnessToTheHandSizeWhenItResolves() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest", "Forest", joral)
        alice.hand("Island", "Island", "Island")
        alice.activates(joral)
        resolveAll()
        move("Island", Zone.Graveyard)
        expect { joral.power shouldBe 3; joral.toughness shouldBe 3 }
    }

    // CR 121.2, 603.2: the second card drawn in a turn makes a 2/2 Cat
    @Test
    fun joralMakesACatOnTheSecondDraw() = scenario {
        alice.battlefield("Island", joral)
        alice.hand(pair)
        alice.library("Forest", "Forest")
        alice.casts(pair)
        resolveAll()
        expect { alice.battlefield has "Cat Token" }
    }
}
