package arcanum.engine.sets.otj

import arcanum.api.CardType
import arcanum.api.Keyword
import arcanum.api.Subtype
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.ltr.playOut
import arcanum.engine.sets.ltr.tokens
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** OTJ chunk 09: surveil and ward cards, with conditions on what was cast this turn, whose turn it is and what was returned or exiled this way. */
class OtjChunk09SurveilWardScenariosTest {
    private val surveil1 = "(Look at the top card of your library. You may put it into your graveyard.)"
    private val surveil2 = "(Look at the top two cards of your library, then put any number of them into your graveyard and the rest on top of your library in any order.)"
    private val dustgate = Cards.invent("Dustgate Arch", null, "Land — Desert",
        "This land enters tapped.\nWhen this land enters, return a land you control to its owner's hand. If another Desert was returned this way, surveil 1. $surveil1\n{T}: Add {C}{C}.")
    private val spires = Cards.invent("Relay Spires", null, "Land — Desert",
        "When this land enters, surveil 1. $surveil1\n{T}: Add {C}.\n{1}, {T}: Add one mana of any color.")
    private val rite = Cards.invent("Cinderswallow Rite", "{2}{B}{B}", "Instant", "Exile target creature. If it had mana value 3 or less, surveil 2. $surveil2")
    private val hauntcall = Cards.invent("Restless Hauntcall", "{1}{U}", "Enchantment",
        "At the beginning of your end step, if you haven't cast a spell from your hand this turn and this enchantment isn't a creature, it becomes a 3/3 Spirit " +
            "creature with flying in addition to its other types.\n{2}{U}: Surveil 1. $surveil1")
    private val armadillo = Cards.invent("Platehide Armadillo", "{W}", "Creature — Armadillo",
        "Ward {1}\n{3}{W}: This creature gets +X/+0 until end of turn, where X is its toughness.", "0/4")
    private val vessa = Cards.invent("Vessa, the Gravecaller", "{3}{B}{B}", "Legendary Creature — Human Warlock",
        "Ward—{2}, Pay 2 life.\nSkeletons and Zombies you control get +1/+1 and have menace.\nWhenever you commit a crime, create two tapped 2/2 blue and black " +
            "Zombie Rogue creature tokens. This ability triggers only once each turn.", "4/4")
    private val sphinx = Cards.invent("Patient Sphinx", "{2}{U}{U}", "Creature — Sphinx", "Flash\nFlying\nThis creature has hexproof as long as you haven't cast a spell this turn.", "5/3")
    private val fusewick = Cards.invent("Fusewick, the Demolisher", "{1}{U}{R}", "Legendary Creature — Goblin Pirate",
        "Menace\nWhenever you cast your second spell each turn, you may sacrifice an artifact. If you do, flip a coin. When you win the flip, copy that spell. " +
            "You may choose new targets for the copy. When you lose the flip, Fusewick deals damage equal to that spell's mana value to any target.", "3/3")
    private val drake = Cards.invent("Steamvent Drake", "{2}{U}", "Creature — Drake", "Flying\nDuring turns other than yours, spells you cast cost {1} less to cast.", "2/3")

    private val shard = Cards.invent("Dusty Shard", "{1}", "Artifact", "")
    private val jolt = Cards.invent("Canyon Jolt", "{R}", "Instant", "Canyon Jolt deals 1 damage to any target.")
    private val blast = Cards.invent("Mesa Blast", "{2}{R}", "Instant", "Mesa Blast deals 2 damage to any target.")
    private val notes = Cards.invent("Trail Notes", "{1}{U}", "Instant", "Draw a card.")
    private val mule = Cards.invent("Pack Mule of the Mesa", "{2}{W}", "Creature — Ox", "", "2/2")
    private val gila = Cards.invent("Gila Bruiser", "{3}{G}", "Creature — Lizard", "", "4/4")

    // CR 608.2h: another Desert returned this way surveils
    @Test
    fun dustgateArchSurveilsWhenAnotherDesertReturns() = scenario {
        alice.battlefield(spires)
        alice.hand(dustgate)
        alice.library(mule)
        alice.plays(dustgate)
        playOut(spires, mule)
        expect {
            alice.hand has spires
            alice.graveyard has mule
        }
    }

    // CR 608.2h: the arch returning itself is no other Desert: no surveil
    @Test
    fun dustgateArchReturningItselfDoesNotSurveil() = scenario {
        alice.battlefield(spires)
        alice.hand(dustgate)
        alice.library(mule)
        alice.plays(dustgate)
        playOut(dustgate)
        expect {
            alice.hand has dustgate
            alice.battlefield has spires
            pending shouldBe null
            alice.library has mule
        }
    }

    // CR 106.1b, 605.1a: "{1}, {T}" adds a mana of the color chosen; a tapped land can't use its other {T} ability
    @Test
    fun relaySpiresMakesAnyColorAndThenIsTapped() = scenario {
        alice.battlefield(spires, "Plains", "Plains")
        alice.hand(notes)
        alice.casts(notes)
        resolveAll()
        expect {
            alice.graveyard has notes
            spires.tapped shouldBe true
        }
        assertFailsWith<IllegalArgumentException> { alice.activates(spires, ability = 0) }
    }

    // CR 608.2h: the exiled creature's mana value as it last existed: 3 surveils, 4 doesn't
    @Test
    fun cinderswallowRiteSurveilsOnlyForASmallCreature() = scenario {
        bob.battlefield(mule, gila)
        alice.hand(rite, rite)
        alice.library("Island", "Swamp")
        alice.castsWithoutCosts(rite, target = gila)
        resolveAll()
        expect {
            pending shouldBe null
            bob.exile has gila
        }
        alice.castsWithoutCosts(rite, target = mule)
        resolveAll()
        alice.chooses("Island", "Swamp")
        resolveAll()
        expect {
            bob.exile has mule
            alice.graveyard.size shouldBe 4
        }
    }

    // CR 603.4, 611.2a: with no spell cast from the hand this turn it becomes a 3/3 flying Spirit for good; CR 613.1d, 613.1f
    @Test
    fun restlessHauntcallAwakensAtEndStep() = scenario {
        alice.battlefield(hauntcall)
        alice.library("Island", "Island")
        bob.library("Island", "Island")
        advanceTo(TurnStep.End)
        playOut()
        advanceTo(TurnStep.Upkeep, turn = 2)
        expect {
            hauntcall.power shouldBe 3
            hauntcall.toughness shouldBe 3
            hauntcall hasKeyword Keyword.Flying.kind
            assertTrue(hauntcall.view.characteristics!!.types.containsAll(listOf(CardType.Creature, CardType.Enchantment)))
            assertTrue(hauntcall.view.characteristics!!.hasSubtype(Subtype("Spirit")))
        }
    }

    // CR 603.4: a spell cast from the hand this turn: it doesn't trigger
    @Test
    fun restlessHauntcallStaysAnEnchantmentAfterACastFromHand() = scenario {
        alice.battlefield(hauntcall, "Island", "Island")
        alice.hand(notes)
        alice.library("Island", "Island")
        alice.casts(notes)
        resolveAll()
        advanceTo(TurnStep.End)
        playOut()
        expect { assertTrue(CardType.Creature !in hauntcall.view.characteristics!!.types) }
    }

    // CR 613.4c: X is its toughness, 4
    @Test
    fun platehideArmadilloAddsItsToughnessToItsPower() = scenario {
        alice.battlefield(armadillo, "Plains", "Plains", "Plains", "Plains")
        alice.activates(armadillo)
        resolveAll()
        expect { armadillo.power shouldBe 4 }
    }

    // CR 700.13: a crime makes two tapped Zombie Rogues, which get +1/+1 and menace (CR 613.4c, 613.1f)
    @Test
    fun vessaMakesTappedZombieRoguesThatGetBigger() = scenario(players = 3) {
        alice.battlefield(vessa)
        alice.hand(jolt)
        alice.castsWithoutCosts(jolt, target = "carol")
        playOut()
        expect {
            carol.life shouldBe 19
            tokens("Zombie Rogue Token").size shouldBe 2
            tokens("Zombie Rogue Token").all { state.obj(it)?.tapped == true } shouldBe true
            "Zombie Rogue Token".power shouldBe 3
        }
    }

    // CR 611.3a: hexproof until you cast a spell this turn, from anywhere
    @Test
    fun patientSphinxLosesHexproofOnceYouCast() = scenario {
        alice.battlefield(sphinx)
        alice.hand(notes)
        alice.library("Island")
        expect { sphinx hasKeyword Keyword.Hexproof.kind }
        alice.castsWithoutCosts(notes)
        resolveAll()
        expect { sphinx lacksKeyword Keyword.Hexproof.kind }
    }

    // CR 603.12, 705.2, 707.10c: the second spell with an artifact sacrificed and the flip won is copied
    @Test
    fun fusewickCopiesTheSecondSpellOnAWonFlip() = scenario {
        alice.battlefield(fusewick, shard)
        alice.hand(notes, jolt)
        alice.library("Island", "Island", "Island")
        alice.castsWithoutCosts(notes)
        resolveAll()
        random(Answer.CoinFlip(true))
        alice.castsWithoutCosts(jolt, target = "bob")
        playOut("bob")
        expect {
            alice.graveyard has shard
            bob.life shouldBe 18
        }
    }

    // CR 603.12, 705.2, 608.2h: on a lost flip it deals the spell's mana value (3) to any target
    @Test
    fun fusewickDealsTheManaValueOnALostFlip() = scenario(players = 3) {
        alice.battlefield(fusewick, shard)
        alice.hand(notes, blast)
        alice.library("Island", "Island", "Island")
        alice.castsWithoutCosts(notes)
        resolveAll()
        random(Answer.CoinFlip(false))
        alice.castsWithoutCosts(blast, target = "bob")
        playOut("carol")
        expect {
            bob.life shouldBe 18
            carol.life shouldBe 17
        }
    }

    // CR 603.2, 117.1: without an artifact sacrificed, no flip
    @Test
    fun fusewickDoesNothingWhenYouDecline() = scenario {
        alice.battlefield(fusewick, shard)
        alice.hand(notes, jolt)
        alice.library("Island", "Island", "Island")
        alice.castsWithoutCosts(notes)
        resolveAll()
        alice.castsWithoutCosts(jolt, target = "bob")
        playOut("bob", may = false)
        expect {
            alice.battlefield has shard
            bob.life shouldBe 19
        }
    }

    // CR 611.3a, 601.2f: a {1}{U} instant costs {U} during an opponent's turn
    @Test
    fun steamventDrakeMakesSpellsCheaperOnOtherTurns() = scenario {
        alice.battlefield(drake, "Island")
        alice.hand(notes)
        alice.library("Island")
        turn(number = 2, active = bob, priority = alice)
        alice.casts(notes)
        resolveAll()
        expect { alice.graveyard has notes }
    }

    // CR 611.3a: not during your own turn
    @Test
    fun steamventDrakeDoesNotHelpOnYourTurn() = scenario {
        alice.battlefield(drake, "Island")
        alice.hand(notes)
        alice.casts(notes)
        expect { decision<Decision.ManaPayment>().suggestion.isEmpty() shouldBe true }
    }
}
