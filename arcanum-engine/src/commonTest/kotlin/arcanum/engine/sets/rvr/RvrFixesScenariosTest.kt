package arcanum.engine.sets.rvr

import arcanum.api.TurnStep
import arcanum.engine.input.Input
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.may
import arcanum.engine.sets.blb.resolveAll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Ravnica Remastered fix step: general engine fixes found during the set's chunks, in play. */
class RvrFixesScenariosTest {
    private val dustsweepRite = Cards.invent("Dustsweep Rite", "{2}{W}{W}", "Instant",
        "Exile target artifact or enchantment. If you cast this spell during your main phase, you may exile up to one other target artifact or enchantment.")
    private val idol = Cards.invent("Dustwork Idol", "{2}", "Artifact", "")
    private val chalice = Cards.invent("Dustwork Chalice", "{2}", "Artifact", "")
    private val graftwarden = Cards.invent("Graftwarden Adept", "{G/U}{G/U}", "Creature — Elf Wizard", """
        {1}{U}: Attach target Aura attached to a permanent to another permanent with the same controller.
    """, "2/2")
    private val graftshift = Cards.invent("Graftshift Totem", "{1}", "Artifact", "{1}: Attach target Aura you control to target land.")
    private val thornbind = Cards.invent("Thornbind Mantle", "{G}", "Enchantment — Aura", """
        Enchant creature
        Enchanted creature gets +2/+2.
    """)
    private val mossback = Cards.invent("Mossback Hound", "{1}{G}", "Creature — Dog", "", "2/2")
    private val fernhide = Cards.invent("Fernhide Recluse", "{1}{G}", "Creature — Elf", "", "2/2")
    private val lawbound = Cards.invent("Lawbound Renegade", "{W}{U}", "Legendary Creature — Human Soldier", """
        Each opponent can't cast noncreature spells with mana value greater than the number of lands that player controls.
    """, "2/2")
    private val spiteflare = Cards.invent("Spiteflare", "{X}{R}", "Sorcery", "This spell deals X damage to any target.")
    private val emberPrism = Cards.invent("Ember Prism", "{2}", "Artifact", "{T}: Add {R}.")
    private val duskfen = Cards.invent("Duskfen Stag", "{1}{G}", "Creature — Elk", "", "2/2")
    private val bogstride = Cards.invent("Bogstride Elk", "{2}{G}", "Creature — Elk", "", "3/3")

    // CR 601.2c: the spell's text defines its number of targets as it is cast: in your main phase it has the second target
    @Test
    fun dustsweepRiteInYourMainPhaseExilesTwo() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains")
        alice.hand(dustsweepRite)
        bob.battlefield(idol, chalice)
        alice.casts(dustsweepRite, targets = listOf(idol, chalice))
        assertEquals(listOf(target(idol), target(chalice)), state.stackItems.values.single().targets.values.flatten())
        resolveAll()
        may(true)
        expect {
            bob.exile has idol
            bob.exile has chalice
        }
    }

    // CR 601.2c: cast outside your main phase, the spell doesn't have the second target at all (nothing else becomes its target)
    @Test
    fun dustsweepRiteOutsideYourMainPhaseHasOneTarget() = scenario(players = 3) {
        alice.battlefield("Plains", "Plains", "Plains", "Plains")
        alice.hand(dustsweepRite)
        bob.battlefield(idol)
        carol.battlefield(chalice)
        turn(number = 2, active = bob, step = TurnStep.PostcombatMain, priority = alice)
        alice.casts(dustsweepRite, target = idol)
        assertEquals(listOf(target(idol)), state.stackItems.values.single().targets.values.flatten())
        resolveAll()
        expect {
            bob.exile has idol
            carol.battlefield has chalice
        }
    }

    // CR 701.3b, 303.4d: the other permanent is chosen only among those the Aura can enchant (Bob's creatures, not his land or
    // artifact, nor Carol's creature)
    @Test
    fun graftwardenOffersOnlyPermanentsTheAuraCanEnchant() = scenario(players = 3) {
        alice.battlefield(graftwarden, "Island", "Island")
        bob.battlefield(mossback, fernhide, bogstride, thornbind, "Forest", idol)
        carol.battlefield(duskfen)
        attach(thornbind, mossback)
        alice.activates(graftwarden, ability = 0, target = thornbind)
        allPass()
        assertEquals(setOf(card(fernhide), card(bogstride)), decision<Decision.ChooseObjects>().options.toSet())
        alice.chooses(bogstride)
        settle()
        expect {
            bogstride.power shouldBe 5
            mossback.power shouldBe 2
        }
    }

    // CR 701.3b: with no other permanent it can enchant, the Aura doesn't move (it isn't moved onto the land and then put into the graveyard)
    @Test
    fun graftwardenWithNoLegalPermanentLeavesTheAura() = scenario {
        alice.battlefield(graftwarden, "Island", "Island")
        bob.battlefield(mossback, thornbind, "Forest", idol)
        attach(thornbind, mossback)
        alice.activates(graftwarden, ability = 0, target = thornbind)
        allPass()
        settle()
        expect {
            bob.battlefield has thornbind
            mossback.power shouldBe 4
        }
    }

    // CR 701.3b: an effect attaching an Aura to an object it can't enchant leaves the Aura where it is
    @Test
    fun auraAttachedToALandItCantEnchantDoesntMove() = scenario {
        alice.battlefield(graftshift, mossback, thornbind, "Forest", "Island")
        attach(thornbind, mossback)
        alice.activates(graftshift, ability = 0, targets = listOf(thornbind, "Forest"), paying = listOf("Island"))
        allPass()
        settle()
        expect {
            alice.battlefield has thornbind
            mossback.power shouldBe 4
        }
    }

    // CR 601.2e, 202.3e: with X announced the spell's mana value is 3, more than Alice's two lands: the proposed spell is illegal and
    // the cast is refused (CR 601.3a let her begin it, X = 0 making it legal)
    @Test
    fun manaValueLimitIsCheckedAfterXIsAnnounced() = scenario(players = 3) {
        alice.battlefield("Mountain", "Mountain", emberPrism)
        alice.hand(spiteflare)
        bob.battlefield(lawbound)
        assertFailsWith<IllegalArgumentException> { alice.casts(spiteflare, x = 2, target = "carol") }
        // the refused answer leaves the cast open; taking it back returns the card
        perform(Input.Cancel(alice.id))
        expect { alice.hand has spiteflare }
    }

    // CR 601.2e: with X = 1 its mana value is 2, no more than her two lands
    @Test
    fun manaValueLimitAllowsASmallEnoughX() = scenario(players = 3) {
        alice.battlefield("Mountain", "Mountain", emberPrism)
        alice.hand(spiteflare)
        bob.battlefield(lawbound)
        alice.casts(spiteflare, x = 1, target = "carol")
        resolveAll()
        expect { carol.life shouldBe 19 }
    }
}
