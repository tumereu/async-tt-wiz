package arcanum.engine.sets.ecl

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.Subtype
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.ecl.Ecl.bear
import arcanum.engine.sets.ecl.Ecl.charm
import arcanum.engine.sets.ecl.Ecl.elf
import arcanum.engine.sets.ecl.Ecl.faerie
import arcanum.engine.sets.ecl.Ecl.jolt
import arcanum.engine.sets.ecl.Ecl.kithkin
import arcanum.engine.sets.ecl.Ecl.m1m1
import arcanum.engine.sets.ecl.Ecl.merfolk
import arcanum.engine.sets.ecl.Ecl.ogre
import arcanum.engine.sets.ecl.Ecl.redBear
import kotlin.test.Test
import kotlin.test.assertEquals

/** Lorwyn Eclipsed's Merfolk that care about becoming tapped, and its white cards, in play (chunks 9–10). */
class EclMerfolkAndWhiteScenariosTest {
    private val brookleapVaulter = Cards.invent("Brookleap Vaulter", "{2}{W}", "Creature — Merfolk Warrior", """
        Flying
        Whenever this creature becomes tapped, another target Merfolk you control gets +2/+0 until end of turn.
    """, "1/3")
    private val driftbrineSermonist = Cards.invent("Driftbrine Sermonist", "{1}{W}", "Creature — Merfolk Cleric", "Whenever this creature becomes tapped, you gain 2 life.", "2/2")
    private val harriedWellkeeper = Cards.invent("Harried Wellkeeper", "{3}{U}", "Creature — Merfolk Soldier", "Whenever this creature becomes tapped, create a 1/1 blue and black Faerie creature token with flying.", "3/2")
    private val silverscaleHawker = Cards.invent("Silverscale Hawker", "{2}{U}", "Creature — Merfolk Citizen", "Whenever this creature becomes tapped, draw a card, then discard a card.", "2/3")
    private val driftwineDiverter = Cards.invent("Driftwine Diverter", "{3}{U}", "Creature — Merfolk Wizard", "Whenever this creature becomes tapped, target creature an opponent controls gets -3/-0 until end of turn.", "4/3")
    private val driftbrineSnarer = Cards.invent("Driftbrine Snarer", "{W}", "Creature — Merfolk Scout", "{1}, {T}, Tap another untapped creature you control: Tap target creature an opponent controls.", "2/1")
    private val oxbowPathfinder = Cards.invent("Oxbow Pathfinder", "{2}{W}", "Creature — Merfolk Scout", "Whenever this creature attacks, you may tap another untapped Merfolk you control. When you do, return target creature card with mana value 3 or less from your graveyard to the battlefield.", "3/2")
    private val pebblefinRascal = Cards.invent("Pebblefin Rascal", "{1}{U}", "Creature — Merfolk Rogue", """
        Vigilance
        Whenever this creature attacks, you may tap another untapped creature you control. If you do, this creature can't be blocked this turn.
    """, "1/3")
    private val deepcurrentFencer = Cards.invent("Deepcurrent Fencer", "{W}{U}", "Creature — Merfolk Soldier", """
        At the beginning of your end step, untap target Merfolk you control.
        Other Merfolk you control get +1/+1.
    """, "2/2")
    private val deeprunPilot = Cards.invent("Deeprun Pilot", "{W}{U}", "Creature — Merfolk Wizard", """
        Flash
        When this creature enters, untap each other Merfolk you control.
        As long as you attacked with three or more Merfolk this turn, Merfolk you control get +1/+0.
    """, "2/2")
    private val skilledTideshaper = Cards.invent("Skilled Tideshaper", "{2}{W}", "Creature — Merfolk Cleric", "Other tapped creatures you control have indestructible.", "3/4")
    private val varro = Cards.invent("Varro, Frontier Escort", "{1}{W}{W}", "Legendary Planeswalker — Ajani", """
        [+1]: Create a 1/1 green and white Kithkin creature token.
        [−2]: Varro deals 4 damage to target tapped creature.
        [−8]: Look at the top X cards of your library, where X is your life total. You may put any number of nonland permanent cards with mana value 3 or less from among them onto the battlefield. Then shuffle.
    """, null, "3")
    private val pleaToSolvanne = Cards.invent("Plea to Solvanne", "{3}{W}", "Instant", """
        Convoke (Your creatures can help cast this spell. Each creature you tap while casting this spell pays for {1} or one mana of that creature's color.)
        One or two target creatures each get +2/+1 until end of turn.
    """)
    private val rindOfTheOak = Cards.invent("Rind of the Oak", "{1}{W}", "Artifact — Equipment", """
        Equipped creature gets +0/+1.
        As long as equipped creature's toughness is greater than its power, it assigns combat damage equal to its toughness rather than its power.
        Equip {1} ({1}: Attach to target creature you control. Equip only as a sorcery.)
    """)
    private val inquisitiveTitan = Cards.invent("Inquisitive Titan", "{5}{W}{W}", "Creature — Giant Warrior", "When this creature enters, each creature target opponent controls loses all abilities, becomes a Coward in addition to its other types, and has base power and toughness 1/1.", "7/7")
    private val valiantHenrider = Cards.invent("Valiant Henrider", "{3}{W}", "Creature — Kithkin Knight", "When this creature enters, creatures you control get +1/+0 until end of turn. Kithkin creatures you control also gain first strike until end of turn.", "3/4")
    private val gildfieldRover = Cards.invent("Gildfield Rover", "{W}", "Creature — Kithkin Scout", "{W}, Exile this card from your graveyard: Create a 1/1 green and white Kithkin creature token. Activate only as a sorcery.", "1/2")
    private val stayBack = Cards.invent("Stay Back", "{1}{W}", "Instant", """
        Choose one —
        • Stay Back deals 4 damage to target tapped creature.
        • Destroy target enchantment.
    """)
    private val kinweave = Cards.invent("Kinweave", "{3}{W}{W}", "Enchantment", """
        Creatures you control get +X/+X, where X is the number of creatures that entered the battlefield under your control this turn.
        At the beginning of combat on your turn, create a 1/1 green and white Kithkin creature token.
    """)
    private val hearthscarLookout = Cards.invent("Hearthscar Lookout", "{1}{W}", "Creature — Kithkin Soldier", """
        First strike, lifelink
        Whenever this creature attacks, you may put a creature card with mana value X or less from your hand onto the battlefield tapped and attacking, where X is the number of attacking creatures you control.
    """, "2/2")
    private val thresholdGrasp = Cards.invent("Threshold Grasp", "{3}{W}", "Enchantment", "When this enchantment enters, exile up to one target nonland permanent an opponent controls until this enchantment leaves the battlefield. You gain 2 life.")
    private val guardingReply = Cards.invent("Guarding Reply", "{2}{W}", "Instant", """
        Convoke (Your creatures can help cast this spell. Each creature you tap while casting this spell pays for {1} or one mana of that creature's color.)
        Destroy target attacking or blocking creature.
    """)
    private val caelin = Cards.invent("Caelin, the Undying", "{1}{W}", "Legendary Creature — Elf Warrior", """
        Flash
        When Caelin enters, another target creature you control gains persist until end of turn. (When it dies, if it had no -1/-1 counters on it, return it to the battlefield under its owner's control with a -1/-1 counter on it.)
        {W}, {T}: Remove any number of counters from target creature you control. Activate only as a sorcery.
    """, "2/2")
    private val streamwardensInstincts = Cards.invent("Streamwarden's Instincts", "{1}{W}", "Instant", "Target creature gets +2/+2 and gains first strike until end of turn. Untap it.")
    private val mindweaveEnchanter = Cards.invent("Mindweave Enchanter", "{3}{W}", "Creature — Kithkin Advisor", "Whenever a creature you control attacks alone, it gets +X/+X until end of turn, where X is the number of Kithkin you control.", "0/5")
    private val bashfulBulwark = Cards.invent("Bashful Bulwark", "{1}{W}", "Creature — Kithkin Soldier", "{4}{W}: Creatures you control get +1/+1 until end of turn.", "2/2")

    private fun Scenario.targets(vararg names: String) {
        if (pending is Decision.Targets) answer(Answer.Targets(names.map { target(it) }))
    }

    /** Alice's libraries are stocked and she attacks with [attackers] in turn 1. */
    private fun Scenario.attackWith(vararg attackers: String) {
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(*attackers)
    }

    // CR 603.2, 508.1f: attacking taps it: another Merfolk gets +2/+0
    @Test
    fun brookleapVaulterPumpsAMerfolk() = scenario {
        alice.battlefield(brookleapVaulter, merfolk)
        attackWith(brookleapVaulter)
        targets(merfolk)
        resolveAll()
        expect { merfolk.power shouldBe 3 }
    }

    @Test
    fun driftbrineSermonistGainsWhenTapped() = scenario {
        alice.battlefield(driftbrineSermonist)
        attackWith(driftbrineSermonist)
        resolveAll()
        assertEquals(22, alice.life)
    }

    @Test
    fun harriedWellkeeperMakesAFaerie() = scenario {
        alice.battlefield(harriedWellkeeper)
        attackWith(harriedWellkeeper)
        resolveAll()
        expect { alice.battlefield has "Faerie Token" }
    }

    @Test
    fun silverscaleHawkerLoots() = scenario {
        alice.battlefield(silverscaleHawker)
        alice.hand(ogre)
        alice.library(bear)
        attackWith(silverscaleHawker)
        resolveAll()
        if (pending is Decision.ChooseObjects) alice.chooses(ogre)
        resolveAll()
        expect {
            alice.hand has bear
            alice.graveyard has ogre
        }
    }

    @Test
    fun driftwineDiverterWeakensABlocker() = scenario {
        alice.battlefield(driftwineDiverter)
        bob.battlefield(ogre)
        attackWith(driftwineDiverter)
        targets(ogre)
        resolveAll()
        expect { ogre.power shouldBe 0 }
    }

    // CR 602.2: tapping another creature is part of the cost
    @Test
    fun driftbrineSnarerTapsAnOpponentsCreature() = scenario {
        alice.battlefield("Plains", driftbrineSnarer, bear)
        bob.battlefield(ogre)
        alice.activates(driftbrineSnarer, target = ogre, choosing = listOf(bear))
        resolveAll()
        expect {
            ogre.tapped shouldBe true
            bear.tapped shouldBe true
        }
    }

    // CR 603.12: tapping a Merfolk makes the reflexive trigger
    @Test
    fun oxbowPathfinderReturnsACreature() = scenario {
        alice.battlefield(oxbowPathfinder, merfolk)
        alice.graveyard(ogre)
        attackWith(oxbowPathfinder)
        resolveAll()
        say(true)
        if (pending is Decision.ChooseObjects) alice.chooses(merfolk)
        resolveAll()
        targets(ogre)
        resolveAll()
        expect {
            alice.battlefield has ogre
            merfolk.tapped shouldBe true
        }
    }

    // CR 509.1b: can't be blocked once another creature is tapped
    @Test
    fun pebblefinRascalBecomesUnblockable() = scenario {
        alice.battlefield(pebblefinRascal, bear)
        attackWith(pebblefinRascal)
        resolveAll()
        say(true)
        if (pending is Decision.ChooseObjects) alice.chooses(bear)
        resolveAll()
        expect {
            pebblefinRascal hasMechanic { it is Mechanic.CantBeBlocked }
            pebblefinRascal.tapped shouldBe false
        }
    }

    @Test
    fun deepcurrentFencerPumpsAndUntaps() = scenario {
        alice.library(bear)
        bob.library(bear)
        alice.battlefield(deepcurrentFencer, merfolk)
        expect { merfolk.power shouldBe 2 }
        attackWith(merfolk)
        advanceTo(TurnStep.End)
        resolveAll()
        targets(merfolk)
        resolveAll()
        expect { merfolk.tapped shouldBe false }
    }

    // CR 611.3a: after attacking with three Merfolk, Merfolk get +1/+0; the Pilot's entering untaps them
    @Test
    fun deeprunPilotAfterAnAttackOfThree() = scenario {
        alice.battlefield("Plains", "Island", merfolk, harriedWellkeeper, driftbrineSermonist)
        alice.hand(deeprunPilot)
        attackWith(merfolk, harriedWellkeeper, driftbrineSermonist)
        resolveAll()
        alice.casts(deeprunPilot)
        resolveAll()
        expect {
            merfolk.tapped shouldBe false
            merfolk.power shouldBe 2
            deeprunPilot.power shouldBe 3
        }
    }

    @Test
    fun skilledTideshaperProtectsTappedCreatures() = scenario {
        alice.battlefield(skilledTideshaper, ogre)
        alice.battlefield(bear, tapped = true)
        expect {
            bear hasKeyword Keyword.Indestructible
            ogre lacksKeyword Keyword.Indestructible
        }
    }

    @Test
    fun varroDamagesATappedCreature() = scenario {
        alice.battlefield(varro, counters = listOf(CounterKind.Loyalty to 3))
        bob.battlefield(ogre, tapped = true)
        alice.activates(varro, ability = 1, target = ogre)
        resolveAll()
        expect { bob.graveyard has ogre }
    }

    @Test
    fun varroMakesAKithkin() = scenario {
        alice.battlefield(varro, counters = listOf(CounterKind.Loyalty to 3))
        alice.activates(varro)
        resolveAll()
        expect {
            alice.battlefield has "Kithkin Token"
            varro.loyalty shouldBe 4
        }
    }

    @Test
    fun pleaToSolvannePumpsTwo() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", bear, ogre)
        alice.hand(pleaToSolvanne)
        alice.casts(pleaToSolvanne, targets = listOf(bear, ogre), paying = listOf("Plains", "Plains", "Plains", "Plains"))
        resolveAll()
        expect {
            bear.power shouldBe 4
            ogre.toughness shouldBe 4
        }
    }

    // CR 510.1c: toughness greater than power: combat damage equal to its toughness
    @Test
    fun rindOfTheOakAssignsToughness() = scenario {
        alice.battlefield(rindOfTheOak, bear)
        attach(rindOfTheOak, bear)
        attackWith(bear)
        advanceTo(TurnStep.EndOfCombat)
        assertEquals(17, bob.life)
    }

    // CR 613.1d, f, 613.4b: no abilities, a Coward too, base 1/1
    @Test
    fun inquisitiveTitanDiminishesAnArmy() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains", "Plains", "Plains")
        alice.hand(inquisitiveTitan)
        bob.battlefield(faerie, ogre)
        alice.casts(inquisitiveTitan)
        resolveAll()
        targets("bob")
        resolveAll()
        expect {
            ogre.power shouldBe 1
            ogre.toughness shouldBe 1
            faerie lacksKeyword Keyword.Flying
            ogre.subtypes shouldBe listOf(Subtype("Ogre"), Subtype("Coward"))
        }
    }

    @Test
    fun valiantHenriderPumpsAndGivesKithkinFirstStrike() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", kithkin, bear)
        alice.hand(valiantHenrider)
        alice.casts(valiantHenrider)
        resolveAll()
        expect {
            bear.power shouldBe 3
            kithkin.power shouldBe 2
            kithkin hasKeyword Keyword.FirstStrike
            bear lacksKeyword Keyword.FirstStrike
        }
    }

    @Test
    fun gildfieldRoverFromTheGraveyard() = scenario {
        alice.battlefield("Plains")
        alice.graveyard(gildfieldRover)
        alice.activates(gildfieldRover)
        resolveAll()
        expect {
            alice.exile has gildfieldRover
            alice.battlefield has "Kithkin Token"
        }
    }

    @Test
    fun stayBackDamagesATappedCreature() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.hand(stayBack)
        bob.battlefield(ogre, tapped = true)
        alice.casts(stayBack, modes = listOf(0), target = ogre)
        resolveAll()
        expect { bob.graveyard has ogre }
    }

    // X counts creatures that entered under your control this turn
    @Test
    fun kinweaveGrowsWithEachArrival() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains", "Forest")
        alice.hand(kinweave, elf)
        alice.casts(kinweave)
        resolveAll()
        alice.casts(elf)
        resolveAll()
        expect { elf.power shouldBe 2 }
        advanceTo(TurnStep.BeginningOfCombat)
        resolveAll()
        expect {
            alice.battlefield has "Kithkin Token"
            elf.power shouldBe 3
        }
    }

    // CR 508.4: put onto the battlefield tapped and attacking (never declared)
    @Test
    fun hearthscarLookoutBringsAnAttacker() = scenario {
        alice.battlefield(hearthscarLookout)
        alice.hand(elf, ogre)
        attackWith(hearthscarLookout)
        resolveAll()
        say(true)
        if (pending is Decision.ChooseObjects) alice.chooses(elf)
        resolveAll()
        expect {
            elf.tapped shouldBe true
            alice.hand has ogre
        }
        advanceTo(TurnStep.EndOfCombat)
        assertEquals(17, bob.life)
        assertEquals(22, alice.life)
    }

    // CR 610.3: exiled until the enchantment leaves; then it returns
    @Test
    fun thresholdGraspExilesUntilItLeaves() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains", "Plains")
        alice.hand(thresholdGrasp, stayBack)
        bob.battlefield(ogre)
        alice.casts(thresholdGrasp, paying = listOf("Plains", "Plains", "Plains", "Plains"))
        resolveAll()
        targets(ogre)
        resolveAll()
        expect { bob.exile has ogre }
        assertEquals(22, alice.life)
        alice.casts(stayBack, modes = listOf(1), target = thresholdGrasp)
        resolveAll()
        expect { bob.battlefield has ogre }
    }

    @Test
    fun guardingReplyDestroysAnAttacker() = scenario {
        alice.battlefield("Plains", "Plains", "Plains")
        alice.hand(guardingReply)
        bob.battlefield(ogre)
        bob.library(bear)
        alice.library(bear)
        turn(active = bob)
        advanceTo(TurnStep.DeclareAttackers)
        bob.attacks(ogre)
        bob.passes()
        alice.casts(guardingReply, target = ogre)
        resolveAll()
        expect { bob.graveyard has ogre }
    }

    // CR 702.79a: persist given until end of turn
    @Test
    fun caelinGivesPersist() = scenario {
        alice.battlefield("Plains", "Plains", "Mountain", bear)
        alice.hand(caelin, jolt)
        alice.casts(caelin, paying = listOf("Plains", "Plains"))
        resolveAll()
        targets(bear)
        resolveAll()
        alice.casts(jolt, target = bear)
        resolveAll()
        expect {
            alice.battlefield has bear
            bear.counters(m1m1) shouldBe 1
        }
    }

    // "any number of counters": all of them here
    @Test
    fun caelinRemovesCounters() = scenario {
        alice.battlefield("Plains", caelin)
        alice.battlefield(ogre, counters = listOf(m1m1 to 2))
        alice.activates(caelin, target = ogre)
        resolveAll()
        when (val d = pending) {
            is Decision.ChooseNumber -> alice.answers(Answer.Number(2))
            is Decision.Announce -> alice.answers(Answer.Number(2))
            else -> Unit
        }
        resolveAll()
        expect { ogre.counters(m1m1) shouldBe 0 }
    }

    @Test
    fun streamwardensInstinctsUntaps() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.battlefield(bear, tapped = true)
        alice.hand(streamwardensInstincts)
        alice.casts(streamwardensInstincts, target = bear)
        resolveAll()
        expect {
            bear.tapped shouldBe false
            bear.power shouldBe 4
            bear hasKeyword Keyword.FirstStrike
        }
    }

    // CR 506.5: attacking alone: +X/+X for each Kithkin
    @Test
    fun mindweaveEnchanterPumpsALoneAttacker() = scenario {
        alice.battlefield(mindweaveEnchanter, kithkin, bear)
        attackWith(bear)
        resolveAll()
        expect { bear.power shouldBe 4 }
    }

    @Test
    fun bashfulBulwarkPumpsTheTeam() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains", bashfulBulwark, bear)
        alice.activates(bashfulBulwark)
        resolveAll()
        expect {
            bear.power shouldBe 3
            bashfulBulwark.toughness shouldBe 3
        }
    }
}
