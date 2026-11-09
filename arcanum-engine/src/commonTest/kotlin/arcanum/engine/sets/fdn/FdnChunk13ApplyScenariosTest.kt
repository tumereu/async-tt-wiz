package arcanum.engine.sets.fdn

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** Chunk 13 played: the invented names carry the oracle text of Make a Stand, Massacre Wurm, Moment of Craving, Moment of Triumph, Offer Immortality, Overrun, Preposterous Proportions, Ruby, Daring Tracker, Sower of Chaos, Sure Strike, Surrak, the Hunt Caller, Syr Alin, the Lion's Claw, Terror of Mount Velus, Undying Malice, Valorous Stance and Wildheart Invoker. */
class FdnChunk13ApplyScenariosTest {
    private val bridge = Cards.invent("Hold the Bridge", "{2}{W}", "Instant",
        "Creatures you control get +1/+0 and gain indestructible until end of turn. (Damage and effects that say \"destroy\" don't destroy them.)")
    private val wurm = Cards.invent("Plague Wurm of Ash", "{3}{B}{B}{B}", "Creature — Phyrexian Wurm", """
        When this creature enters, creatures your opponents control get -2/-2 until end of turn.
        Whenever a creature an opponent controls dies, that player loses 2 life.
    """, "6/5")
    private val hunger = Cards.invent("Hour of Hunger", "{1}{B}", "Instant", "Target creature gets -2/-2 until end of turn. You gain 2 life.")
    private val triumph = Cards.invent("Dawnlit Triumph", "{W}", "Instant", "Target creature gets +2/+2 until end of turn. You gain 2 life.")
    private val endurance = Cards.invent("Grant Endurance", "{1}{B}", "Instant",
        "Target creature gains deathtouch and indestructible until end of turn. (Damage and effects that say \"destroy\" don't destroy it.)")
    private val surge = Cards.invent("Stampede Surge", "{2}{G}{G}{G}", "Sorcery",
        "Creatures you control get +3/+3 and gain trample until end of turn. (Each of those creatures can deal excess combat damage to the player or planeswalker it's attacking.)")
    private val dimensions = Cards.invent("Absurd Dimensions", "{5}{G}{G}", "Sorcery", "Creatures you control get +10/+10 and gain vigilance until end of turn.")
    private val tilda = Cards.invent("Tilda, Quick Scout", "{R}{G}", "Legendary Creature — Human Scout", """
        Haste (This creature can attack and {T} as soon as it comes under your control.)
        Whenever Tilda attacks while you control a creature with power 4 or greater, Tilda gets +2/+2 until end of turn.
        {T}: Add {R} or {G}.
    """, "1/2")
    private val sifter = Cards.invent("Sifter of Havoc", "{3}{R}", "Creature — Devil", "{2}{R}: Target creature can't block this turn.", "4/3")
    private val blow = Cards.invent("Certain Blow", "{1}{R}", "Instant",
        "Target creature gets +3/+0 and gains first strike until end of turn. (It deals combat damage before creatures without first strike.)")
    private val vorak = Cards.invent("Vorak, the Hunt Warden", "{2}{G}{G}", "Legendary Creature — Human Warrior",
        "Formidable — At the beginning of combat on your turn, if creatures you control have total power 8 or greater, target creature you control gains haste until end of turn. (It can attack and {T} no matter when it came under your control.)", "5/4")
    private val belen = Cards.invent("Syr Belen, the Tiger's Fang", "{3}{W}{W}", "Legendary Creature — Human Knight", """
        First strike (This creature deals combat damage before creatures without first strike.)
        Whenever Syr Belen attacks, other creatures you control get +1/+1 until end of turn.
    """, "4/4")
    private val terror = Cards.invent("Terror of Mount Orrin", "{5}{R}{R}", "Creature — Dragon", """
        Flying
        Double strike (This creature deals both first-strike and regular combat damage.)
        When this creature enters, creatures you control gain double strike until end of turn.
    """, "5/5")
    private val spite = Cards.invent("Lingering Spite", "{B}", "Instant",
        "Until end of turn, target creature gains \"When this creature dies, return it to the battlefield tapped under its owner's control with a +1/+1 counter on it.\"")
    private val stand = Cards.invent("Resolute Stand", "{1}{W}", "Instant", """
        Choose one —
        • Target creature gains indestructible until end of turn. (Damage and effects that say "destroy" don't destroy it.)
        • Destroy target creature with toughness 4 or greater.
    """)
    private val caller = Cards.invent("Wildmane Caller", "{2}{G}{G}", "Creature — Elf Shaman",
        "{8}: Target creature gets +5/+5 and gains trample until end of turn. (It can deal excess combat damage to the player or planeswalker it's attacking.)", "4/3")
    private val ox = Cards.invent("Plodding Ox", "{1}{G}", "Creature — Ox", "", "2/2")
    private val brute = Cards.invent("Hulking Brute", "{3}{R}", "Creature — Ogre", "", "4/4")
    private val bobBear = Cards.invent("Marsh Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val bobBrute = Cards.invent("Fenwood Brute", "{1}{G}", "Creature — Ogre", "", "3/3")
    private val zap = Cards.invent("Pinprick Zap", "{R}", "Instant", "Pinprick Zap deals 1 damage to any target.")

    private fun Scenario.resolveAll() {
        var guard = 40
        while (state.stack.isNotEmpty() && pending == null && guard-- > 0) allPass()
    }

    // CR 611.2c, 613.4c, 702.12a: only your creatures get +1/+0 and indestructible
    @Test
    fun bridgeProtectsOnlyYourCreatures() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", ox, brute)
        bob.battlefield(bobBear)
        alice.hand(bridge)
        alice.casts(bridge)
        resolveAll()
        expect {
            ox.power shouldBe 3
            brute.power shouldBe 5
            ox hasKeyword Keyword.Indestructible.kind
            brute hasKeyword Keyword.Indestructible.kind
            bobBear.power shouldBe 2
            bobBear lacksKeyword Keyword.Indestructible.kind
        }
    }

    // CR 611.2a, 514.2: the effect ends with the turn
    @Test
    fun bridgeEndsWithTheTurn() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", ox)
        alice.hand(bridge)
        bob.library(bobBear, bobBear, bobBear)
        alice.casts(bridge)
        resolveAll()
        advanceTo(TurnStep.Upkeep, turn = 2)
        expect {
            ox.power shouldBe 2
            ox lacksKeyword Keyword.Indestructible.kind
        }
    }

    // CR 603.6a, 613.4c, 704.5f, 700.4, 603.2, 119.3: -2/-2 kills the 2/2, the opponent loses 2 life; the 3/3 survives as 1/1; mine is untouched
    @Test
    fun wurmKillsSmallCreaturesAndDrainsTheirController() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp", ox)
        bob.battlefield(bobBear, bobBrute)
        alice.hand(wurm)
        alice.casts(wurm)
        resolveAll()
        expect {
            bobBear.zone shouldBe Zone.Graveyard
            bobBrute.zone shouldBe Zone.Battlefield
            bobBrute.power shouldBe 1
            bobBrute.toughness shouldBe 1
            ox.power shouldBe 2
            bob.life shouldBe 18
            alice.life shouldBe 20
        }
    }

    // CR 603.2: an opponent's creature dying later also costs its controller 2 life
    @Test
    fun wurmDrainsAgainForALaterDeath() = scenario {
        alice.battlefield("Swamp", "Swamp", wurm)
        bob.battlefield(bobBear)
        alice.hand(hunger)
        alice.casts(hunger, target = bobBear)
        resolveAll()
        expect {
            bobBear.zone shouldBe Zone.Graveyard
            bob.life shouldBe 18
            alice.life shouldBe 22
        }
    }

    // CR 603.2: my own creature dying doesn't trigger it
    @Test
    fun wurmIgnoresMyOwnCreatureDying() = scenario {
        alice.battlefield("Swamp", "Swamp", wurm, ox)
        alice.hand(hunger)
        alice.casts(hunger, target = ox)
        resolveAll()
        expect {
            ox.zone shouldBe Zone.Graveyard
            bob.life shouldBe 20
            alice.life shouldBe 22
        }
    }

    // CR 613.4c, 119.1: -2/-2 on the target, 2 life for me
    @Test
    fun hungerShrinksAndGainsLife() = scenario {
        alice.battlefield("Swamp", "Swamp")
        bob.battlefield(bobBrute)
        alice.hand(hunger)
        alice.casts(hunger, target = bobBrute)
        resolveAll()
        expect {
            bobBrute.power shouldBe 1
            bobBrute.toughness shouldBe 1
            alice.life shouldBe 22
        }
    }

    // CR 613.4c, 119.1: +2/+2 on the target, 2 life for me
    @Test
    fun triumphGrowsAndGainsLife() = scenario {
        alice.battlefield("Plains", ox)
        alice.hand(triumph)
        alice.casts(triumph, target = ox)
        resolveAll()
        expect {
            ox.power shouldBe 4
            ox.toughness shouldBe 4
            alice.life shouldBe 22
        }
    }

    // CR 702.2a, 702.12a: both keywords on the target only
    @Test
    fun enduranceGivesDeathtouchAndIndestructible() = scenario {
        alice.battlefield("Swamp", "Swamp", ox, brute)
        alice.hand(endurance)
        alice.casts(endurance, target = ox)
        resolveAll()
        expect {
            ox hasKeyword Keyword.Deathtouch.kind
            ox hasKeyword Keyword.Indestructible.kind
            brute lacksKeyword Keyword.Deathtouch.kind
        }
    }

    // CR 702.12b, 120.6: lethal damage doesn't destroy an indestructible creature
    @Test
    fun enduranceSurvivesLethalDamage() = scenario {
        alice.battlefield("Swamp", "Swamp", ox)
        alice.hand(endurance)
        bob.battlefield("Mountain", "Mountain")
        bob.hand(zap, zap)
        alice.casts(endurance, target = ox)
        resolveAll()
        alice.passes()
        bob.casts(zap, target = ox)
        resolveAll()
        alice.passes()
        bob.casts(zap, target = ox)
        resolveAll()
        expect { ox.zone shouldBe Zone.Battlefield }
    }

    // CR 611.2c, 613.4c, 702.19a: my creatures get +3/+3 and trample, the opponent's don't
    @Test
    fun surgeGivesMyCreaturesPlusAndTrample() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest", ox, brute)
        bob.battlefield(bobBear)
        alice.hand(surge)
        alice.casts(surge)
        resolveAll()
        expect {
            ox.power shouldBe 5
            ox.toughness shouldBe 5
            brute.power shouldBe 7
            ox hasKeyword Keyword.Trample.kind
            brute hasKeyword Keyword.Trample.kind
            bobBear.power shouldBe 2
            bobBear lacksKeyword Keyword.Trample.kind
        }
    }

    // CR 702.19b: trample over a blocker, the 3 excess damage of the 5/5 over the 2/2 goes to the player
    @Test
    fun surgeTramplesOverABlocker() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest", ox)
        bob.battlefield(bobBear)
        alice.hand(surge)
        alice.casts(surge)
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(ox)
        advanceTo(TurnStep.DeclareBlockers)
        bob.blocks(bobBear to ox)
        allPass()
        divideAsSuggested()
        allPass()
        expect {
            bobBear.zone shouldBe Zone.Graveyard
            bob.life shouldBe 17
        }
    }

    // CR 613.4c, 702.20b: +10/+10 and vigilance (an attacker stays untapped)
    @Test
    fun dimensionsGiveVigilance() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest", "Forest", "Forest", ox)
        alice.hand(dimensions)
        alice.casts(dimensions)
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(ox)
        expect {
            ox.power shouldBe 12
            ox.toughness shouldBe 12
            ox hasKeyword Keyword.Vigilance.kind
            ox.tapped shouldBe false
        }
    }

    // CR 603.4, 508.1m: attacking with a 4-power creature in play, Tilda gets +2/+2
    @Test
    fun tildaGrowsWithAFourPowerCreature() = scenario {
        alice.battlefield(tilda, brute)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(tilda)
        resolveAll()
        expect {
            tilda.power shouldBe 3
            tilda.toughness shouldBe 4
        }
    }

    // CR 603.4: with no creature of power 4 or greater, nothing happens (her own power is 1)
    @Test
    fun tildaStaysSmallWithoutOne() = scenario {
        alice.battlefield(tilda, bobBear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(tilda)
        resolveAll()
        expect { tilda.power shouldBe 1 }
    }

    // CR 605.1a, 702.10a: haste lets her tap for mana at once, and the mana casts a red spell
    @Test
    fun tildaTapsForRedAtOnce() = scenario {
        alice.battlefield(tilda)
        bob.battlefield(bobBear)
        alice.hand(zap)
        alice.casts(zap, target = "bob")
        resolveAll()
        expect {
            tilda.tapped shouldBe true
            bob.life shouldBe 19
        }
    }

    // CR 602.2, 509.1b: the target creature can't block, another one still can
    @Test
    fun sifterStopsOneBlocker() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", sifter, ox)
        bob.battlefield(bobBear, bobBrute)
        alice.activates(sifter, target = bobBear)
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(ox)
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<Throwable> { bob.blocks(bobBear to ox) }
        bob.blocks(bobBrute to ox)
    }

    // CR 613.4c, 702.7a: +3/+0 and first strike
    @Test
    fun blowGivesPlusAndFirstStrike() = scenario {
        alice.battlefield("Mountain", "Mountain", ox)
        alice.hand(blow)
        alice.casts(blow, target = ox)
        resolveAll()
        expect {
            ox.power shouldBe 5
            ox.toughness shouldBe 2
            ox hasKeyword Keyword.FirstStrike.kind
        }
    }

    // CR 702.7a, 510.4: first strike kills the blocker before it deals damage
    @Test
    fun blowLetsTheAttackerWinWithoutDying() = scenario {
        alice.battlefield("Mountain", "Mountain", ox)
        bob.battlefield(bobBrute)
        alice.hand(blow)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(ox)
        advanceTo(TurnStep.DeclareBlockers)
        bob.blocks(bobBrute to ox)
        alice.casts(blow, target = ox)
        resolveAll()
        advanceTo(TurnStep.CombatDamage)
        allPass()
        expect {
            bobBrute.zone shouldBe Zone.Graveyard
            ox.zone shouldBe Zone.Battlefield
        }
    }

    // CR 207.2c, 603.4, 700.5: total power 9 at the beginning of combat, so the target gains haste
    @Test
    fun vorakGivesHasteWithEightPower() = scenario {
        alice.battlefield(vorak, brute, ox)
        advanceTo(TurnStep.BeginningOfCombat)
        alice.answers(Answer.Targets(listOf(target(ox))))
        resolveAll()
        expect {
            ox hasKeyword Keyword.Haste.kind
            brute lacksKeyword Keyword.Haste.kind
        }
    }

    // CR 603.4: total power 7 is not enough, nothing triggers
    @Test
    fun vorakDoesNothingWithSevenPower() = scenario {
        alice.battlefield(vorak, ox)
        alice.battlefield(bobBear)
        advanceTo(TurnStep.BeginningOfCombat)
        resolveAll()
        expect { ox lacksKeyword Keyword.Haste.kind }
    }

    // CR 603.4: only my creatures count, the opponent's power doesn't
    @Test
    fun vorakCountsOnlyMyCreatures() = scenario {
        alice.battlefield(vorak, ox)
        bob.battlefield(brute)
        advanceTo(TurnStep.BeginningOfCombat)
        resolveAll()
        expect { ox lacksKeyword Keyword.Haste.kind }
    }

    // CR 508.1m, 603.2, 613.4c: the other creatures I control get +1/+1, Belen herself doesn't
    @Test
    fun belenPumpsTheOthers() = scenario {
        alice.battlefield(belen, ox)
        bob.battlefield(bobBear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(belen)
        resolveAll()
        expect {
            belen.power shouldBe 4
            ox.power shouldBe 3
            ox.toughness shouldBe 3
            bobBear.power shouldBe 2
            belen hasKeyword Keyword.FirstStrike.kind
        }
    }

    // CR 603.6a, 702.4a, 611.2c: the dragon and the other creatures gain double strike, the opponent's don't
    @Test
    fun terrorGivesDoubleStrikeToMyCreatures() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain", ox)
        bob.battlefield(bobBear)
        alice.hand(terror)
        alice.casts(terror)
        resolveAll()
        expect {
            terror hasKeyword Keyword.DoubleStrike.kind
            ox hasKeyword Keyword.DoubleStrike.kind
            bobBear lacksKeyword Keyword.DoubleStrike.kind
        }
    }

    // CR 611.2a, 514.2: a creature coming later or the next turn has no double strike
    @Test
    fun terrorsGrantEndsWithTheTurn() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain", ox)
        alice.hand(terror)
        bob.library(bobBear, bobBear, bobBear)
        alice.casts(terror)
        resolveAll()
        advanceTo(TurnStep.Upkeep, turn = 2)
        expect { ox lacksKeyword Keyword.DoubleStrike.kind }
    }

    // CR 700.4, 400.7, 122.1, 614.1c: the creature that dies returns tapped with a +1/+1 counter
    @Test
    fun spiteBringsTheCreatureBackTappedWithACounter() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", ox)
        alice.hand(spite, hunger)
        alice.casts(spite, target = ox)
        resolveAll()
        alice.casts(hunger, target = ox)
        resolveAll()
        expect {
            ox.zone shouldBe Zone.Battlefield
            ox.tapped shouldBe true
            ox.counters(CounterKind.PowerToughness(1, 1)) shouldBe 1
            ox.power shouldBe 3
            ox.toughness shouldBe 3
        }
    }

    // CR 611.2a, 514.2: the granted ability ends with the turn, so a death in the next turn is final
    @Test
    fun spiteEndsWithTheTurn() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", ox)
        alice.hand(spite, hunger)
        bob.library(bobBear, bobBear, bobBear)
        alice.library(brute, brute, brute)
        alice.casts(spite, target = ox)
        resolveAll()
        advanceTo(TurnStep.PrecombatMain, turn = 3)
        alice.casts(hunger, target = ox)
        resolveAll()
        expect { ox.zone shouldBe Zone.Graveyard }
    }

    // CR 700.2, 702.12a: the first mode gives indestructible
    @Test
    fun standFirstModeProtects() = scenario {
        alice.battlefield("Plains", "Plains", ox)
        alice.hand(stand)
        alice.casts(stand, modes = listOf(0), target = ox)
        resolveAll()
        expect { ox hasKeyword Keyword.Indestructible.kind }
    }

    // CR 700.2, 701.8: the second mode destroys a creature with toughness 4 or greater, only those are legal targets
    @Test
    fun standSecondModeDestroysABigCreature() = scenario {
        alice.battlefield("Plains", "Plains")
        bob.battlefield(bobBear, bobBrute, brute)
        alice.hand(stand)
        alice.casts(stand, modes = listOf(1))
        expect { decision<Decision.Targets>().options.toSet() shouldBe setOf(target(brute)) }
        alice.answers(Answer.Targets(listOf(target(brute))))
        if (pending is Decision.ManaPayment) alice.answers(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
        resolveAll()
        expect {
            brute.zone shouldBe Zone.Graveyard
            bobBear.zone shouldBe Zone.Battlefield
            bobBrute.zone shouldBe Zone.Battlefield
        }
    }

    // CR 602.2, 613.4c, 702.19a: eight mana gives +5/+5 and trample to any creature
    @Test
    fun callerPumpsAnyCreature() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest", "Forest", "Forest", "Forest", caller, ox)
        alice.activates(caller, target = ox)
        resolveAll()
        expect {
            ox.power shouldBe 7
            ox.toughness shouldBe 7
            ox hasKeyword Keyword.Trample.kind
            caller lacksKeyword Keyword.Trample.kind
        }
    }

    // CR 602.5b: seven mana can't pay {8}
    @Test
    fun callerNeedsEightMana() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest", "Forest", "Forest", caller, ox)
        alice.activates(caller, target = ox)
        resolveAll()
        expect { ox.power shouldBe 2 }
    }
}
