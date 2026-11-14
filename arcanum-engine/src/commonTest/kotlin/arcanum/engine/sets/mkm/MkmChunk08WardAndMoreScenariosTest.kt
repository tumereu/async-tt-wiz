package arcanum.engine.sets.mkm

import arcanum.api.Keyword
import arcanum.api.ObjectMark
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.ltr.alternativeOf
import arcanum.engine.sets.ltr.playOut
import arcanum.engine.sets.ltr.unblocked
import arcanum.engine.sets.tdm.Plain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** MKM chunk 08: a skipped draw from an empty library (CR 614.11), the last known suspected mark and power (CR 608.2h), nameless face-down
 * blockers (CR 708.2a), face-down spells' costs and mana (CR 601.2f, 106.6), collect evidence X from the targets (CR 601.2c, 701.59a), a
 * spell with lifelink and deathtouch (CR 702.15b, 702.2b) and modal spells. */
class MkmChunk08WardAndMoreScenariosTest {
    private val riddle = Cards.invent("Hollow Riddle", "{4}{U}", "Creature — Elemental", """
        Hexproof
        If you would draw a card while your library has no cards in it, skip that draw instead.
        As long as there are no cards in your library, this creature has base power and toughness 10/10 and has flying and vigilance.
    """.trimIndent(), "2/5")
    private val mortician = Cards.invent("Bureau Mortician", "{4}{B}", "Creature — Ogre Cleric",
        "{2}{B}, Sacrifice another creature: Draw a card. If the sacrificed creature was suspected, draw two cards instead.", "3/6")
    private val cornered = Cards.invent("Cornered Suspect", "{1}{B}", "Creature — Human Rogue", "When this creature enters, suspect it.", "2/2")
    private val promenader = Cards.invent("Haughty Promenader", "{2}{G}", "Creature — Human Citizen", """
        During your turn, this creature has hexproof.
        This creature can't be blocked by creatures that don't have a name.
    """.trimIndent(), "4/2")
    private val facecarver = Cards.invent("Goblin Facecarver", "{R}", "Creature — Goblin Citizen",
        "Whenever this creature attacks, face-down spells you cast this turn cost {1} less to cast.", "1/2")
    private val footpad = Cards.invent("Veiled Footpad", "{2}{R}", "Creature — Human Rogue", "Disguise {1}{R}", "3/2")
    private val reassembly = Cards.invent("Grim Reassembly", "{3}{B}", "Sorcery", """
        This spell costs {2} less to cast if a creature card was put into your graveyard from anywhere this turn.
        Return up to two target creature cards from your graveyard to your hand.
    """.trimIndent(), null)
    private val rumormonger = Cards.invent("Copper Lane Rumormonger", "{2}{R}{G}", "Creature — Lizard Advisor", """
        Vigilance
        {T}: Add {R}{G}. Spend this mana only to cast face-down spells or to turn creatures face up.
    """.trimIndent(), "4/4")
    private val autopsy = Cards.invent("Hasty Autopsy", "{2}{B}{G}", "Instant", """
        As an additional cost to cast this spell, collect evidence X, where X is the total mana value of the permanents this spell targets.
        Destroy up to one target artifact, up to one target creature, up to one target enchantment, and up to one target planeswalker.
    """.trimIndent(), null)
    private val charm = Cards.invent("Elderdruid's Charm", "{G}{G}{G}", "Instant", """
        Choose one —
        • Search your library for a creature or land card and reveal it. Put it onto the battlefield tapped if it's a land card. Otherwise, put it into your hand. Then shuffle.
        • Put a +1/+1 counter on target creature you control. It deals damage equal to its power to target creature you don't control.
        • Exile target artifact or enchantment.
    """.trimIndent(), null)
    private val probe = Cards.invent("Spirit Probe", "{W}{B}", "Sorcery",
        "Target opponent reveals their hand. You choose a nonland card from it. Exile that card. If the card's mana value is 1 or less, " +
            "create a 1/1 white and black Spirit creature token with flying.", null)
    private val kelvox = Cards.invent("Kelvox, Visionary Tinkerer", "{5}{U}{R}", "Legendary Creature — Lizard Artificer", """
        Menace, ward {2}, haste
        Whenever Kelvox attacks, sacrifice any number of other creatures, then exile the top X cards of your library, where X is their total power. You may cast any number of instant and/or sorcery spells from among the exiled cards without paying their mana costs.
    """.trimIndent(), "4/4")
    private val joruna = Cards.invent("Joruna, Carnage Collector", "{3}{B}{R}", "Legendary Creature — Human Shaman", """
        Whenever you cast an instant or sorcery spell, choose one —
        • That spell gains deathtouch and lifelink.
        • Create a 2/2 red Imp creature token with "When this token dies, it deals 2 damage to each opponent."
    """.trimIndent(), "3/4")

    private fun Scenario.pay() {
        while (pending is Decision.ManaPayment) answer(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
    }

    // CR 614.11, 121.4: the draw from an empty library is skipped, so nothing is drawn and no one loses; CR 613.4b: base 10/10 then
    @Test
    fun hollowRiddleSkipsTheDrawFromAnEmptyLibrary() = scenario {
        alice.battlefield(riddle, "Island", "Island")
        alice.hand(Plain.study)
        alice.library("Plains")
        bob.library("Island")
        expect { riddle.power shouldBe 2 }
        alice.casts(Plain.study)
        resolveAll()
        expect {
            alice.hand has "Plains"
            riddle.power shouldBe 10
            riddle.toughness shouldBe 10
            riddle hasKeyword Keyword.Flying.kind
            riddle hasKeyword Keyword.Vigilance.kind
        }
        advanceTo(TurnStep.Draw, turn = 3)
        assertEquals(false, state.player(alice.id).lost)
        assertEquals(false, state.player(bob.id).lost)
    }

    // CR 121.4, 704.5b: without it, the same draw loses the game
    @Test
    fun anEmptyLibraryDrawLosesWithoutHollowRiddle() = scenario {
        alice.battlefield(Plain.bear)
        bob.library("Island")
        advanceTo(TurnStep.Draw, turn = 3)
        assertTrue(state.player(alice.id).lost)
    }

    // CR 608.2h, 701.60a: the sacrificed creature was suspected as it last existed, so two cards
    @Test
    fun bureauMorticianDrawsTwoForASuspectedCreature() = scenario {
        alice.battlefield(mortician, "Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(cornered)
        alice.library("Plains", "Island", "Mountain")
        alice.casts(cornered)
        playOut()
        assertTrue(state.obj(card(cornered))?.marks?.contains(ObjectMark.Suspected) == true)
        alice.activates(mortician, choosing = listOf(cornered))
        resolveAll()
        expect {
            alice.graveyard has cornered
            alice.hand has "Plains"
            alice.hand has "Island"
            alice.hand lacks "Mountain"
        }
    }

    // CR 608.2h: a creature that wasn't suspected draws one
    @Test
    fun bureauMorticianDrawsOneOtherwise() = scenario {
        alice.battlefield(mortician, Plain.bear, "Swamp", "Swamp", "Swamp")
        alice.library("Plains", "Island")
        alice.activates(mortician, choosing = listOf(Plain.bear))
        resolveAll()
        expect {
            alice.hand has "Plains"
            alice.hand lacks "Island"
        }
    }

    // CR 708.2a, 509.1b: a face-down creature has no name, so it can't block; a named one can
    @Test
    fun haughtyPromenaderCantBeBlockedByAFaceDownCreature() = scenario {
        alice.battlefield(promenader)
        bob.battlefield(Plain.ogre, faceDown = true)
        bob.battlefield(Plain.bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(promenader)
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<IllegalArgumentException> { bob.blocks(Plain.ogre to promenader) }
        bob.blocks(Plain.bear to promenader)
        advanceTo(TurnStep.CombatDamage)
        expect { bob.graveyard has Plain.bear }
    }

    // CR 601.2f, 702.168a: after the attack a face-down spell costs {2}
    @Test
    fun goblinFacecarverMakesFaceDownSpellsCheaper() = scenario {
        alice.battlefield(facecarver, "Mountain", "Mountain")
        alice.hand(footpad)
        unblocked(facecarver)
        advanceTo(TurnStep.PostcombatMain)
        alice.casts(footpad, way = alternativeOf(footpad, Keyword.Disguise.Kind))
        pay()
        resolveAll()
        assertTrue(state.objOrThrow(card(footpad)).faceDown)
        expect { bob.life shouldBe 19 }
    }

    // CR 601.2f: without the attack it still costs {3}
    @Test
    fun aFaceDownSpellCostsThreeWithoutTheAttack() = scenario {
        alice.battlefield(facecarver, "Mountain", "Mountain")
        alice.hand(footpad)
        alice.casts(footpad, way = alternativeOf(footpad, Keyword.Disguise.Kind))
        assertTrue(decision<Decision.ManaPayment>().suggestion.isEmpty())
    }

    // CR 601.2f: a creature card put into your graveyard this turn makes it cost {2} less
    @Test
    fun grimReassemblyCostsLessAfterYourCreatureDied() = scenario {
        alice.battlefield(Plain.bear, "Swamp", "Swamp", "Swamp")
        alice.hand(Plain.doom, reassembly)
        alice.casts(Plain.doom, target = Plain.bear)
        resolveAll()
        alice.casts(reassembly, target = Plain.bear)
        pay()
        resolveAll()
        expect { alice.hand has Plain.bear }
    }

    // CR 404.2: an opponent's creature goes to its owner's graveyard, not yours
    @Test
    fun grimReassemblyCostsFullAfterAnOpponentsCreatureDied() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.hand(Plain.doom, reassembly)
        alice.graveyard(Plain.elf)
        bob.battlefield(Plain.bear)
        alice.casts(Plain.doom, target = Plain.bear)
        resolveAll()
        alice.casts(reassembly, target = Plain.elf)
        assertTrue(decision<Decision.ManaPayment>().suggestion.isEmpty())
    }

    // CR 106.6: the mana pays for a face-down spell but not for the same card cast face up
    @Test
    fun copperLaneRumormongerManaCastsFaceDownSpellsOnly() = scenario {
        alice.battlefield(rumormonger, "Mountain")
        alice.hand(footpad)
        alice.casts(footpad)
        assertTrue(decision<Decision.ManaPayment>().suggestion.isEmpty())
    }

    // CR 106.6, 702.168a
    @Test
    fun copperLaneRumormongerManaCastsAFaceDownSpell() = scenario {
        alice.battlefield(rumormonger, "Mountain")
        alice.hand(footpad)
        alice.casts(footpad, way = alternativeOf(footpad, Keyword.Disguise.Kind))
        pay()
        resolveAll()
        assertTrue(state.objOrThrow(card(footpad)).faceDown)
    }

    // CR 601.2c, 601.2f, 701.59a: the target's mana value 2 is the X, so the 3-mana-value card is exiled and the 1 isn't enough
    @Test
    fun hastyAutopsyCollectsEvidenceEqualToItsTargets() = scenario {
        alice.battlefield("Swamp", "Swamp", "Forest", "Forest")
        alice.hand(autopsy)
        alice.graveyard(Plain.elf, Plain.ogre)
        bob.battlefield(Plain.bear)
        alice.casts(autopsy, target = Plain.bear, choosing = listOf(Plain.ogre))
        pay()
        resolveAll()
        expect {
            bob.graveyard has Plain.bear
            alice.exile has Plain.ogre
            alice.graveyard has Plain.elf
        }
    }

    // CR 701.23a: a land found is put onto the battlefield tapped
    @Test
    fun elderdruidsCharmPutsALandOntoTheBattlefieldTapped() = scenario {
        alice.battlefield("Forest", "Forest", "Forest")
        alice.hand(charm)
        alice.library("Island", "Plains", Plain.bear)
        alice.casts(charm, modes = listOf(0))
        resolveAll()
        alice.chooses("Plains")
        resolveAll()
        expect {
            alice.battlefield has "Plains"
            "Plains".tapped shouldBe true
        }
    }

    // CR 701.23a: a creature card found is put into the hand
    @Test
    fun elderdruidsCharmPutsACreatureCardIntoTheHand() = scenario {
        alice.battlefield("Forest", "Forest", "Forest")
        alice.hand(charm)
        alice.library("Island", "Plains", Plain.bear)
        alice.casts(charm, modes = listOf(0))
        resolveAll()
        alice.chooses(Plain.bear)
        resolveAll()
        expect { alice.hand has Plain.bear }
    }

    // CR 608.2h: the exiled card's mana value 1 makes a Spirit; a 3 doesn't
    @Test
    fun spiritProbeMakesASpiritForACheapCard() = scenario {
        alice.battlefield("Plains", "Swamp")
        alice.hand(probe)
        bob.hand(Plain.elf, Plain.ogre, "Island")
        alice.casts(probe, target = "bob")
        resolveAll()
        alice.chooses(Plain.elf)
        resolveAll()
        expect {
            bob.exile has Plain.elf
            alice.battlefield has "Spirit Token"
        }
    }

    @Test
    fun spiritProbeMakesNoSpiritForAnExpensiveCard() = scenario {
        alice.battlefield("Plains", "Swamp")
        alice.hand(probe)
        bob.hand(Plain.elf, Plain.ogre, "Island")
        alice.casts(probe, target = "bob")
        resolveAll()
        alice.chooses(Plain.ogre)
        resolveAll()
        expect {
            bob.exile has Plain.ogre
            alice.battlefield lacks "Spirit Token"
        }
    }

    // CR 608.2h: the sacrificed creatures' power as they last existed (3 with its counter, plus 1) exiles four cards; CR 608.2g: the
    // sorcery among them is cast free while the ability resolves
    @Test
    fun kelvoxExilesByTheSacrificedPowerAndCastsTheSorcery() = scenario {
        alice.battlefield(kelvox, Plain.elf)
        alice.battlefield(Plain.bear, counters = listOf(Plain.p1p1 to 1))
        alice.library(Plain.study, "Island", "Island", "Island", "Plains", "Mountain")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(kelvox)
        resolveAll()
        alice.chooses(Plain.bear, Plain.elf)
        resolveAll()
        alice.chooses(Plain.study)
        resolveAll()
        expect {
            alice.graveyard has Plain.bear
            alice.graveyard has Plain.elf
            alice.graveyard has Plain.study
            alice.hand has "Plains"
            alice.library has "Mountain"
        }
    }

    // CR 702.15b, 702.2b: the spell deals the damage, so its controller gains that much and the creature is destroyed
    @Test
    fun jorunaGivesTheSpellDeathtouchAndLifelink() = scenario {
        alice.battlefield(joruna, "Mountain")
        alice.hand(Plain.shock)
        bob.battlefield(Plain.giant)
        alice.casts(Plain.shock, target = Plain.giant)
        answer(Answer.Modes(listOf(0)))
        resolveAll()
        expect {
            bob.graveyard has Plain.giant
            alice.life shouldBe 22
        }
    }
}
