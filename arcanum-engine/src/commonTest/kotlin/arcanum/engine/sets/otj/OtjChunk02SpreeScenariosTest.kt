package arcanum.engine.sets.otj

import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.input.Attack
import arcanum.engine.input.Input
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.ltr.tokens
import arcanum.engine.state.TargetRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** OTJ chunk 02: spree (CR 702.172a): the chosen modes' costs are paid as additional costs and the modes resolve in printed order (CR 700.2). */
class OtjChunk02SpreeScenariosTest {
    private val spree = "Spree (Choose one or more additional costs.)"
    private val crossfire = Cards.invent("Crossfire Reckoning", "{R}{R}", "Instant", """
        $spree
        + {1} — Crossfire Reckoning deals 2 damage to each outlaw creature. (Assassins, Mercenaries, Pirates, Rogues, and Warlocks are outlaws.)
        + {1} — Crossfire Reckoning deals 2 damage to each non-outlaw creature.
    """.trimIndent())
    private val standoff = Cards.invent("High Noon Standoff", "{W}", "Instant", """
        $spree
        + {1} — All creatures lose all abilities until end of turn.
        + {1} — Choose a creature you control. It gains indestructible until end of turn.
        + {3}{W}{W} — Destroy all creatures.
    """.trimIndent())
    private val veil = Cards.invent("Vanishing Veil Trick", "{W}", "Instant", """
        $spree
        + {1} — Exile target nontoken creature. Return it to the battlefield under its owner's control at the beginning of the next end step.
        + {2} — Destroy target creature if no other creature has greater power.
    """.trimIndent())
    private val robbery = Cards.invent("Iron Horse Robbery", "{R}", "Instant", """
        $spree
        + {2}{R} — Untap all creatures you control. If it's your combat phase, there is an additional combat phase after this phase.
        + {2} — Creatures you control get +1/+0 and gain first strike until end of turn.
        + {R} — Choose target opponent. Whenever a creature you control deals combat damage to that player this turn, create a tapped Treasure token.
    """.trimIndent())
    private val jig = Cards.invent("Mournful Jig", "{1}{B}", "Sorcery", """
        $spree
        + {1} — Search your library for a card, put it into your graveyard, then shuffle.
        + {2} — Return up to two creature cards with total mana value 4 or less from your graveyard to the battlefield.
    """.trimIndent())
    private val caper = Cards.invent("Final Score Caper", "{2}{W}", "Sorcery", """
        $spree
        + {2} — Return target creature card from your graveyard to the battlefield.
        + {1} — Return target Mount or Vehicle card from your graveyard to the battlefield.
        + {1} — Return target Aura or Equipment card from your graveyard to the battlefield attached to a creature you control.
    """.trimIndent())
    private val terror = Cards.invent("Wave of Terror Tactics", "{1}{B}{B}", "Sorcery", """
        $spree
        + {1} — Target opponent sacrifices half the creatures they control of their choice, rounded up.
        + {2} — Target opponent discards half the cards in their hand, rounded up.
        + {2} — Target opponent loses half their life, rounded up.
    """.trimIndent())
    private val stampede = Cards.invent("Cattle Thief Stampede", "{W}", "Instant", """
        $spree
        + {1} — Untap all creatures target player controls.
        + {1} — Target creature gains double strike until end of turn.
    """.trimIndent())
    private val swindle = Cards.invent("Swapping Swindle", "{U}{U}", "Sorcery", """
        $spree
        + {2} — Exchange control of two target creatures.
        + {1} — Exchange control of two target artifacts.
        + {1} — Exchange control of two target enchantments.
    """.trimIndent())
    private val repay = Cards.invent("Repay the Kindness", "{R}{R}", "Instant", """
        $spree
        + {1} — Copy target instant spell, sorcery spell, activated ability, or triggered ability. You may choose new targets for the copy.
        + {1} — Change the target of target spell or ability with a single target.
    """.trimIndent())

    private val rogue = Cards.invent("Dustwind Rogue", "{1}{B}", "Creature — Human Rogue", "", "2/2")
    private val drover = Cards.invent("Mesa Drover", "{1}{W}", "Creature — Human", "", "2/2")
    private val sentinel = Cards.invent("Stoneplate Sentinel", "{3}", "Artifact Creature — Golem", "Indestructible", "2/2")
    private val bruiser = Cards.invent("Canyon Bruiser", "{3}{R}", "Creature — Ogre", "", "4/4")
    private val prowler = Cards.invent("Gulch Prowler", "{2}{G}", "Creature — Cat", "", "3/3")
    private val hound = Cards.invent("Rail Yard Hound", "{1}{R}", "Creature — Dog", "", "2/2")
    private val mule = Cards.invent("Sagebrush Mule", "{W}", "Creature — Horse", "", "1/1")
    private val saddle = Cards.invent("Spurred Saddle", "{1}", "Artifact — Equipment", "Equipped creature gets +1/+1.\nEquip {1}")
    private val shackle = Cards.invent("Rustwire Shackle", "{W}", "Enchantment — Aura", "Enchant creature\nEnchanted creature gets +1/+1.")
    private val ledger = Cards.invent("Quiet Ledger", "{U}", "Sorcery", "Draw a card.")

    // CR 700.12: outlaws (here a Rogue) are dealt damage by the first mode, other creatures aren't; CR 702.172a: {R}{R} plus the mode's {1}
    @Test
    fun theOutlawModeDamagesOnlyOutlaws() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", rogue)
        alice.hand(crossfire)
        bob.battlefield(drover)
        alice.casts(crossfire, modes = listOf(0))
        resolveAll()
        expect {
            alice.graveyard has rogue
            bob.battlefield has drover
        }
        assertTrue(state.battlefield.filter { state.objOrThrow(it).controller == alice.id }.all { state.objOrThrow(it).tapped })
    }

    // CR 700.2, 613.7: all creatures lose all abilities (the Sentinel its indestructible), then the chosen creature gains indestructible with a
    // later timestamp, then all creatures are destroyed: only the chosen one survives (CR 702.12b)
    @Test
    fun allModesLeaveOnlyTheChosenCreature() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains", "Plains", "Plains", "Plains", hound, mule)
        alice.hand(standoff)
        bob.battlefield(sentinel)
        alice.casts(standoff, modes = listOf(0, 1, 2))
        resolveAll()
        alice.chooses(hound)
        resolveAll()
        expect {
            alice.battlefield has hound
            alice.graveyard has mule
            bob.graveyard has sentinel
        }
    }

    // CR 608.2c: no other creature has greater power (an equal one doesn't count): the target is destroyed
    @Test
    fun destroysTheTargetWhenNoOtherCreatureIsStronger() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", prowler)
        alice.hand(veil)
        bob.battlefield(hound)
        alice.casts(veil, modes = listOf(1), target = prowler)
        resolveAll()
        expect { alice.graveyard has prowler }
    }

    // CR 608.2c: another creature with greater power: nothing happens
    @Test
    fun sparesTheTargetWhenAnotherCreatureIsStronger() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", prowler)
        alice.hand(veil)
        bob.battlefield(bruiser)
        alice.casts(veil, modes = listOf(1), target = prowler)
        resolveAll()
        expect { alice.battlefield has prowler }
    }

    // CR 500.8, 506.1: cast during your combat phase: the attackers untap and another combat phase follows this one
    @Test
    fun inYourCombatPhaseItUntapsAndAddsACombat() = scenario {
        players.forEach { it.library("Island", "Island", "Island") }
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", hound)
        alice.hand(robbery)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(hound)
        alice.casts(robbery, modes = listOf(0))
        resolveAll()
        expect { hound.tapped shouldBe false }
        advanceTo(TurnStep.EndOfCombat)
        allPass()
        assertEquals(TurnStep.BeginningOfCombat, state.turn.step)
    }

    // CR 506.1: in a main phase it isn't your combat phase: no additional combat
    @Test
    fun inAMainPhaseItAddsNoCombat() = scenario {
        players.forEach { it.library("Island", "Island", "Island") }
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", hound)
        alice.hand(robbery)
        alice.casts(robbery, modes = listOf(0))
        resolveAll()
        advanceTo(TurnStep.EndOfCombat)
        allPass()
        assertEquals(TurnStep.PostcombatMain, state.turn.step)
    }

    // CR 603.7b, c: three players: each creature's combat damage to the chosen opponent makes a tapped Treasure, damage to another opponent doesn't
    @Test
    fun treasuresOnlyForDamageToTheChosenOpponent() = scenario(players = 3) {
        players.forEach { it.library("Island", "Island", "Island") }
        alice.battlefield("Mountain", "Mountain", hound, prowler, mule)
        alice.hand(robbery)
        alice.casts(robbery, modes = listOf(2), target = "bob")
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        perform(Input.DeclareAttackers(alice.id, listOf(Attack(card(hound), TargetRef.Player(bob.id)), Attack(card(mule), TargetRef.Player(bob.id)),
            Attack(card(prowler), TargetRef.Player(carol.id)))))
        advanceTo(TurnStep.CombatDamage)
        resolveAll()
        val treasures = tokens("Treasure Token")
        assertEquals(2, treasures.size)
        assertTrue(treasures.all { state.objOrThrow(it).tapped })
        expect {
            bob.life shouldBe 17
            carol.life shouldBe 17
        }
    }

    // CR 608.2d: chosen as it resolves, not targeted: a 3 and a 2 (total 5) are refused, a 2 and a 1 returned
    @Test
    fun returnsUpToTwoCreatureCardsWithTotalManaValueFourOrLess() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(jig)
        alice.graveyard(prowler, hound, mule)
        alice.casts(jig, modes = listOf(1))
        resolveAll()
        assertFailsWith<IllegalArgumentException> { alice.chooses(prowler, hound) }
        alice.chooses(hound, mule)
        resolveAll()
        expect {
            alice.battlefield has hound
            alice.battlefield has mule
            alice.graveyard has prowler
        }
    }

    // CR 301.5c: the Equipment returns attached to the creature you choose as it resolves
    @Test
    fun returnsAnEquipmentAttachedToAChosenCreature() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", hound, mule)
        alice.hand(caper)
        alice.graveyard(saddle)
        alice.casts(caper, modes = listOf(2), target = saddle)
        resolveAll()
        alice.chooses(hound)
        resolveAll()
        expect {
            alice.battlefield has saddle
            hound.power shouldBe 3
            mule.power shouldBe 1
        }
    }

    // CR 303.4f: with no creature to attach it to, an Aura stays in the graveyard
    @Test
    fun anAuraWithNoCreatureToAttachToStays() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains")
        alice.hand(caper)
        alice.graveyard(shackle)
        bob.battlefield(drover)
        alice.casts(caper, modes = listOf(2), target = shackle)
        resolveAll()
        expect {
            alice.graveyard has caper
            alice.graveyard has shackle
            drover.power shouldBe 2
        }
    }

    // CR 301.5c: with no creature to attach it to, an Equipment enters unattached
    @Test
    fun anEquipmentWithNoCreatureEntersUnattached() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains")
        alice.hand(caper)
        alice.graveyard(saddle)
        alice.casts(caper, modes = listOf(2), target = saddle)
        resolveAll()
        expect { alice.battlefield has saddle }
        assertEquals(null, state.objOrThrow(card(saddle)).attachedTo)
    }

    // CR 107.1a, 115.3 (each mode targets the same opponent): three players: the target opponent sacrifices two of three creatures (their choice), discards two of three cards and loses 8 of
    // 15 life; the other opponent is untouched
    @Test
    fun theTargetOpponentLosesHalfOfEverythingRoundedUp() = scenario(players = 3) {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(terror)
        bob.battlefield(hound, mule, prowler)
        bob.hand("Island", "Forest", "Plains")
        bob.life = 15
        carol.battlefield(drover)
        alice.casts(terror, modes = listOf(0, 1, 2), targets = listOf("bob", "bob", "bob"))
        resolveAll()
        bob.chooses(hound, mule)
        resolveAll()
        bob.chooses("Island", "Forest")
        resolveAll()
        expect {
            bob.battlefield has prowler
            bob.graveyard has hound
            bob.graveyard has mule
            bob.hand.size shouldBe 1
            bob.life shouldBe 7
            carol.battlefield has drover
            carol.life shouldBe 20
        }
    }

    // CR 701.26b: three players: only the target player's creatures untap
    @Test
    fun untapsOnlyTheTargetPlayersCreatures() = scenario(players = 3) {
        alice.battlefield("Plains", "Plains")
        alice.hand(stampede)
        bob.battlefield(hound, tapped = true)
        carol.battlefield(mule, tapped = true)
        alice.casts(stampede, modes = listOf(0), target = "bob")
        resolveAll()
        expect {
            hound.tapped shouldBe false
            mule.tapped shouldBe true
        }
    }

    // CR 701.12a: three players: two other players' creatures change controllers
    @Test
    fun exchangesControlOfTwoOtherPlayersCreatures() = scenario(players = 3) {
        alice.battlefield("Island", "Island", "Island", "Island")
        alice.hand(swindle)
        bob.battlefield(bruiser)
        carol.battlefield(prowler)
        alice.casts(swindle, modes = listOf(0), targets = listOf(bruiser, prowler))
        resolveAll()
        expect {
            bruiser.controller shouldBe carol.id
            prowler.controller shouldBe bob.id
        }
    }

    // CR 707.10: a sorcery spell is a legal target ("instant spell, sorcery spell, …" are alternatives): the copy and the spell each draw a card
    @Test
    fun copiesASorcerySpell() = scenario {
        alice.battlefield("Island", "Mountain", "Mountain", "Mountain")
        alice.hand(ledger, repay)
        alice.library("Plains", "Plains")
        alice.casts(ledger)
        alice.casts(repay, modes = listOf(0), target = ledger)
        resolveAll()
        expect { alice.hand.size shouldBe 2 }
    }
}
