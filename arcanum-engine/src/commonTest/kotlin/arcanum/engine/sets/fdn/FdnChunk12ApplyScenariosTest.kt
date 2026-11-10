package arcanum.engine.sets.fdn

import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** Chunk 12 played: the invented names carry the oracle text of Adamant Will, Axgard Cavalry, Balmor, Battlesong Berserker, Burrog Befuddler, Courageous Goblin, Crackling Cyclops, Crash Through, Dauntless Veteran, Fleeting Distraction, Frenzied Goblin, Gateway Sneak, Goblin Smuggler, Goblin Surprise, Jazal Goldmane, Kindled Fury and Leonin Vanguard. */
class FdnChunk12ApplyScenariosTest {
    private val resolve = Cards.invent("Steadfast Resolve", "{1}{W}", "Instant",
        "Target creature gets +2/+2 and gains indestructible until end of turn. (Damage and effects that say \"destroy\" don't destroy it.)")
    private val rider = Cards.invent("Forgeward Rider", "{1}{R}", "Creature — Dwarf Berserker",
        "{T}: Target creature gains haste until end of turn. (It can attack and {T} this turn.)", "2/2")
    private val battlemage = Cards.invent("Stormcrest Battlemage", "{U}{R}", "Legendary Creature — Bird Wizard", """
        Flying
        Whenever you cast an instant or sorcery spell, creatures you control get +1/+0 and gain trample until end of turn.
    """, "1/3")
    private val raider = Cards.invent("Warchant Raider", "{3}{R}", "Creature — Human Berserker",
        "Whenever you attack, target creature you control gets +1/+0 and gains menace until end of turn. (It can't be blocked except by two or more creatures.)", "3/4")
    private val dazzler = Cards.invent("Puddle Dazzler", "{1}{U}", "Creature — Frog Wizard", """
        Flash (You may cast this spell any time you could cast an instant.)
        When this creature enters, target creature an opponent controls gets -1/-0 until end of turn.
    """, "2/1")
    private val boldGoblin = Cards.invent("Brazen Goblin", "{1}{R}", "Creature — Goblin",
        "Whenever this creature attacks while you control a creature with power 4 or greater, this creature gets +1/+0 and gains menace until end of turn. (It can't be blocked except by two or more creatures.)", "2/2")
    private val cyclops = Cards.invent("Sparking Cyclops", "{2}{R}", "Creature — Cyclops Wizard",
        "Whenever you cast a noncreature spell, this creature gets +3/+0 until end of turn.", "0/4")
    private val charge = Cards.invent("Charge Ahead", "{R}", "Sorcery", """
        Creatures you control gain trample until end of turn. (Each of those creatures can deal excess combat damage to the player or planeswalker it's attacking.)
        Draw a card.
    """)
    private val veteran = Cards.invent("Steadfast Veteran", "{1}{W}{W}", "Creature — Human Soldier",
        "Whenever this creature attacks, creatures you control get +1/+1 until end of turn.", "2/2")
    private val distraction = Cards.invent("Momentary Distraction", "{U}", "Instant", """
        Target creature gets -1/-0 until end of turn.
        Draw a card.
    """)
    private val frenetic = Cards.invent("Frenetic Goblin", "{R}", "Creature — Goblin Berserker",
        "Whenever this creature attacks, you may pay {R}. If you do, target creature can't block this turn.", "1/1")
    private val sneak = Cards.invent("Doorway Sneak", "{2}{U}", "Creature — Vedalken Rogue", """
        Whenever a Gate you control enters, this creature can't be blocked this turn.
        Whenever this creature deals combat damage to a player, draw a card.
    """, "1/3")
    private val smuggler = Cards.invent("Alleyway Smuggler", "{2}{R}", "Creature — Goblin Rogue", """
        Haste (This creature can attack and {T} as soon as it comes under your control.)
        {T}: Another target creature with power 2 or less can't be blocked this turn.
    """, "2/2")
    private val gambit = Cards.invent("Horde Gambit", "{2}{R}", "Instant", """
        Choose one —
        • Creatures you control get +2/+0 until end of turn.
        • Create two 1/1 red Goblin creature tokens.
    """)
    private val commander = Cards.invent("Gilded Commander", "{2}{W}{W}", "Legendary Creature — Cat Warrior", """
        First strike (This creature deals combat damage before creatures without first strike.)
        {3}{W}{W}: Attacking creatures you control get +X/+X until end of turn, where X is the number of attacking creatures.
    """, "4/4")
    private val fury = Cards.invent("Smoldering Fury", "{R}", "Instant",
        "Target creature gets +1/+0 and gains first strike until end of turn. (It deals combat damage before creatures without first strike.)")
    private val vanguard = Cards.invent("Pridekeep Vanguard", "{W}", "Creature — Cat Soldier",
        "At the beginning of combat on your turn, if you control three or more creatures, this creature gets +1/+1 until end of turn and you gain 1 life.", "1/1")
    private val gate = Cards.invent("Sunken Gateway", null, "Land — Gate", "")
    private val ox = Cards.invent("Plodding Ox", "{1}{G}", "Creature — Ox", "", "2/2")
    private val mule = Cards.invent("Stubborn Mule", "{1}{G}", "Creature — Beast", "", "3/3")
    private val brute = Cards.invent("Hulking Brute", "{3}{R}", "Creature — Ogre", "", "4/4")
    private val bobBear = Cards.invent("Marsh Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val bobBrute = Cards.invent("Fenwood Brute", "{1}{G}", "Creature — Ogre", "", "3/3")
    private val zap = Cards.invent("Pinprick Zap", "{R}", "Instant", "Pinprick Zap deals 1 damage to any target.")
    private val grunt = Cards.invent("Dusty Grunt", "{1}{R}", "Creature — Goblin", "", "2/2")

    private fun Scenario.resolveAll() {
        var guard = 40
        while (state.stack.isNotEmpty() && pending == null && guard-- > 0) allPass()
    }

    // CR 611.2a, 613.4c, 702.12a: +2/+2 and indestructible, the lethal damage then doesn't destroy it
    @Test
    fun resolveGivesPlusAndIndestructible() = scenario {
        alice.battlefield("Plains", "Plains", "Mountain", ox)
        alice.hand(resolve, zap)
        advanceTo(TurnStep.PrecombatMain)
        alice.casts(resolve, target = ox)
        resolveAll()
        expect {
            ox.power shouldBe 4
            ox.toughness shouldBe 4
            ox hasKeyword Keyword.Indestructible.kind
        }
    }

    // CR 611.2a: the effect ends at the cleanup step (CR 514.2)
    @Test
    fun resolveEndsWithTheTurn() = scenario {
        alice.battlefield("Plains", "Plains", ox)
        alice.hand(resolve)
        bob.library(bobBear, bobBear, bobBear)
        alice.casts(resolve, target = ox)
        resolveAll()
        advanceTo(TurnStep.Upkeep, turn = 2)
        expect {
            ox.power shouldBe 2
            ox lacksKeyword Keyword.Indestructible.kind
        }
    }

    // CR 602.2, 702.10a: the activation gives haste to the target until end of turn
    @Test
    fun riderGivesHasteToTheTarget() = scenario {
        alice.battlefield(rider, ox)
        alice.activates(rider, target = ox)
        resolveAll()
        expect {
            ox hasKeyword Keyword.Haste.kind
            rider lacksKeyword Keyword.Haste.kind
        }
    }

    // CR 603.2, 601.2, 702.19a: casting an instant pumps and tramples only your creatures
    @Test
    fun battlemageRewardsAnInstant() = scenario {
        alice.battlefield("Mountain", battlemage, ox)
        bob.battlefield(bobBear)
        alice.hand(zap)
        alice.casts(zap, target = bobBear)
        resolveAll()
        expect {
            battlemage.power shouldBe 2
            ox.power shouldBe 3
            ox hasKeyword Keyword.Trample.kind
            bobBear.power shouldBe 2
            bobBear lacksKeyword Keyword.Trample.kind
        }
    }

    // CR 601.2, 603.2: a creature spell is neither an instant nor a sorcery spell
    @Test
    fun battlemageIgnoresACreatureSpell() = scenario {
        alice.battlefield("Plains", "Plains", battlemage, ox)
        alice.hand(grunt)
        alice.casts(grunt)
        resolveAll()
        expect {
            battlemage.power shouldBe 1
            ox lacksKeyword Keyword.Trample.kind
        }
    }

    // CR 506.2, 603.2, 702.110a: one trigger however many attack, on the chosen creature only
    @Test
    fun raiderGivesTheChosenAttackerMenace() = scenario {
        alice.battlefield(raider, ox)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(ox)
        alice.answers(Answer.Targets(listOf(target(ox))))
        resolveAll()
        expect {
            ox.power shouldBe 3
            ox hasKeyword Keyword.Menace.kind
            raider lacksKeyword Keyword.Menace.kind
        }
    }

    // CR 603.6a, 613.4c: only an opponent's creature can be the target; it gets -1/-0
    @Test
    fun dazzlerShrinksAnOpponentsCreature() = scenario {
        alice.battlefield("Island", "Island", ox)
        bob.battlefield(bobBear)
        alice.hand(dazzler)
        alice.casts(dazzler)
        resolveAll()
        expect {
            bobBear.power shouldBe 1
            ox.power shouldBe 2
        }
    }

    // CR 603.4: the condition is checked as the creature attacks; a 4-power creature is controlled
    @Test
    fun boldGoblinGrowsWithAFourPowerCreature() = scenario {
        alice.battlefield(boldGoblin, brute)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(boldGoblin)
        resolveAll()
        expect {
            boldGoblin.power shouldBe 3
            boldGoblin hasKeyword Keyword.Menace.kind
        }
    }

    // CR 603.4: without a creature of power 4 or greater the trigger doesn't trigger
    @Test
    fun boldGoblinStaysSmallWithoutOne() = scenario {
        alice.battlefield(boldGoblin, mule)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(boldGoblin)
        resolveAll()
        expect {
            boldGoblin.power shouldBe 2
            boldGoblin lacksKeyword Keyword.Menace.kind
        }
    }

    // CR 601.2, 603.2: a noncreature spell gives +3/+0 to the cyclops
    @Test
    fun cyclopsGrowsOnANoncreatureSpell() = scenario {
        alice.battlefield("Mountain", cyclops)
        bob.battlefield(bobBear)
        alice.hand(zap)
        alice.casts(zap, target = bobBear)
        resolveAll()
        expect { cyclops.power shouldBe 3 }
    }

    // CR 603.2: a creature spell doesn't trigger it
    @Test
    fun cyclopsIgnoresACreatureSpell() = scenario {
        alice.battlefield("Mountain", "Mountain", cyclops)
        alice.hand(grunt)
        alice.casts(grunt)
        resolveAll()
        expect { cyclops.power shouldBe 0 }
    }

    // CR 611.2c, 702.19a, 121.1: your creatures, not the opponent's, gain trample; then draw
    @Test
    fun chargeGivesTrampleAndDraws() = scenario {
        alice.battlefield("Mountain", ox, mule)
        bob.battlefield(bobBear)
        alice.hand(charge)
        alice.library(grunt)
        alice.casts(charge)
        resolveAll()
        expect {
            ox hasKeyword Keyword.Trample.kind
            mule hasKeyword Keyword.Trample.kind
            bobBear lacksKeyword Keyword.Trample.kind
            alice.hand.size shouldBe 1
        }
    }

    // CR 611.2c: a creature that enters after the spell resolved doesn't gain trample
    @Test
    fun chargeDoesNotReachLaterCreatures() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", ox)
        alice.hand(charge, grunt)
        alice.library(mule)
        alice.casts(charge)
        resolveAll()
        alice.casts(grunt)
        resolveAll()
        expect {
            ox hasKeyword Keyword.Trample.kind
            grunt lacksKeyword Keyword.Trample.kind
        }
    }

    // CR 508.1m, 603.2, 613.4c: every creature you control gets +1/+1 when the veteran attacks
    @Test
    fun veteranPumpsEveryCreatureOfTheAttacker() = scenario {
        alice.battlefield(veteran, ox)
        bob.battlefield(bobBear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(veteran)
        resolveAll()
        expect {
            veteran.power shouldBe 3
            ox.power shouldBe 3
            ox.toughness shouldBe 3
            bobBear.power shouldBe 2
        }
    }

    // CR 613.4c, 121.1: -1/-0 and a card drawn
    @Test
    fun distractionShrinksAndDraws() = scenario {
        alice.battlefield("Island")
        bob.battlefield(bobBear)
        alice.hand(distraction)
        alice.library(ox)
        alice.casts(distraction, target = bobBear)
        resolveAll()
        expect {
            bobBear.power shouldBe 1
            bobBear.toughness shouldBe 2
            alice.hand has ox
        }
    }

    // CR 117.12, 608.2c, 509.1b: pay {R} and the target can't block
    @Test
    fun franticGoblinPaidStopsABlocker() = scenario {
        alice.battlefield(frenetic, "Mountain")
        bob.battlefield(bobBear, bobBrute)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(frenetic)
        alice.answers(Answer.Targets(listOf(target(bobBear))))
        allPass()
        alice.answers(Answer.YesNo(true))
        alice.answers(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
        resolveAll()
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<Throwable> { bob.blocks(bobBear to frenetic) }
    }

    // CR 608.2c: nothing paid, so nothing happens and the creature can block
    @Test
    fun franticGoblinUnpaidChangesNothing() = scenario {
        alice.battlefield(frenetic, "Mountain")
        bob.battlefield(bobBear, bobBrute)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(frenetic)
        alice.answers(Answer.Targets(listOf(target(bobBear))))
        allPass()
        alice.answers(Answer.YesNo(false))
        resolveAll()
        advanceTo(TurnStep.DeclareBlockers)
        bob.blocks(bobBear to frenetic)
    }

    // CR 205.3i (Gate), 603.2, 509.1b: a Gate entering makes the sneak unblockable this turn only
    @Test
    fun sneakIsUnblockableAfterAGate() = scenario {
        alice.battlefield(sneak)
        alice.hand(gate)
        bob.battlefield(bobBear)
        alice.plays(gate)
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(sneak)
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<Throwable> { bob.blocks(bobBear to sneak) }
    }

    // CR 509.1b: with no Gate entering it can be blocked
    @Test
    fun sneakCanBeBlockedWithoutAGate() = scenario {
        alice.battlefield(sneak)
        bob.battlefield(bobBear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(sneak)
        advanceTo(TurnStep.DeclareBlockers)
        bob.blocks(bobBear to sneak)
    }

    // CR 700.2, 509.1b: another creature with power 2 or less, not the smuggler and not a bigger one
    @Test
    fun smugglerChoosesAnotherSmallCreature() = scenario {
        alice.battlefield(smuggler, ox, mule)
        alice.activates(smuggler)
        expect {
            val options = decision<Decision.Targets>().options
            (target(ox) in options) shouldBe true
            (target(smuggler) in options) shouldBe false
            (target(mule) in options) shouldBe false
        }
    }

    // CR 509.1b: the chosen creature can't be blocked this turn
    @Test
    fun smugglerMakesItsTargetUnblockable() = scenario {
        alice.battlefield(smuggler, ox)
        bob.battlefield(bobBear)
        alice.activates(smuggler, target = ox)
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(ox)
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<Throwable> { bob.blocks(bobBear to ox) }
    }

    // CR 700.2a: the first mode gives +2/+0 to the creatures you control
    @Test
    fun gambitFirstModePumps() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", ox, mule)
        bob.battlefield(bobBear)
        alice.hand(gambit)
        alice.casts(gambit, modes = listOf(0))
        resolveAll()
        expect {
            ox.power shouldBe 4
            mule.power shouldBe 5
            bobBear.power shouldBe 2
        }
    }

    // CR 700.2a, 111.1: the second mode creates two 1/1 red Goblin tokens
    @Test
    fun gambitSecondModeMakesTokens() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.hand(gambit)
        alice.casts(gambit, modes = listOf(1))
        resolveAll()
        expect {
            card("Goblin Token", 0).power shouldBe 1
            card("Goblin Token", 1).toughness shouldBe 1
        }
    }

    // CR 107.3, 508.1k: X is the number of attacking creatures (two), all attackers get +2/+2
    @Test
    fun commanderPumpsAttackersByTheirNumber() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains", commander, ox)
        bob.battlefield(bobBear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(commander, ox)
        resolveAll()
        alice.activates(commander)
        resolveAll()
        expect {
            commander.power shouldBe 6
            ox.power shouldBe 4
            ox.toughness shouldBe 4
            bobBear.power shouldBe 2
        }
    }

    // CR 508.1k: a creature that isn't attacking isn't affected
    @Test
    fun commanderLeavesNonAttackersAlone() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains", commander, ox)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(commander)
        resolveAll()
        alice.activates(commander)
        resolveAll()
        expect {
            commander.power shouldBe 5
            ox.power shouldBe 2
        }
    }

    // CR 613.4c, 702.7a: +1/+0 and first strike
    @Test
    fun furyGivesPlusAndFirstStrike() = scenario {
        alice.battlefield("Mountain", ox)
        alice.hand(fury)
        alice.casts(fury, target = ox)
        resolveAll()
        expect {
            ox.power shouldBe 3
            ox.toughness shouldBe 2
            ox hasKeyword Keyword.FirstStrike.kind
        }
    }

    // CR 603.4, 506.1: with three creatures at the beginning of combat the vanguard grows and its controller gains 1 life
    @Test
    fun vanguardGrowsWithThreeCreatures() = scenario {
        alice.battlefield(vanguard, ox, mule)
        advanceTo(TurnStep.BeginningOfCombat)
        resolveAll()
        expect {
            vanguard.power shouldBe 2
            vanguard.toughness shouldBe 2
            alice.life shouldBe 21
        }
    }

    // CR 603.4: with only two creatures the trigger doesn't trigger, no life is gained
    @Test
    fun vanguardStaysWithTwoCreatures() = scenario {
        alice.battlefield(vanguard, ox)
        advanceTo(TurnStep.BeginningOfCombat)
        resolveAll()
        expect {
            vanguard.power shouldBe 1
            alice.life shouldBe 20
        }
    }
}
