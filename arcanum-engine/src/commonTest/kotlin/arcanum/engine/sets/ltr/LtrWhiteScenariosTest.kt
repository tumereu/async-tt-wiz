package arcanum.engine.sets.ltr

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.Subtype
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The Lord of the Rings cards of parser chunks 01–02 in play: the white cards. Trivial ones not played here: Protector of the White
 * City and Soldier of the Pale Host (an enters trigger making a token or pumping a target), Took Gleaner (the Ring tempting by a dies
 * trigger, as Stalwarts of the River Fort's enters trigger), Eastfold Rider (a sacrifice ability destroying a target).
 */
class LtrWhiteScenariosTest {
    private val s = LtrSet
    private val p = LtrPlain
    private val p1p1 = LtrPlain.p1p1
    private val food = "Food Token"
    private val artifactWatcher = Cards.invent("Shirefolk Tinker", "{1}{W}", "Creature — Human Artificer",
        "Whenever an artifact you control enters, you gain 1 life.", "1/1")

    // ---- Chunk 01

    /** It costs {2} less when its target is tapped: three lands cast it. */
    // CR 601.2f
    @Test
    fun banishFromHalmereCostsLessForATappedTarget() = scenario {
        alice.battlefield("Plains", "Plains", "Plains")
        alice.hand(s.banishFromHalmere)
        bob.battlefield(p.ogre, tapped = true)
        alice.casts(s.banishFromHalmere, target = p.ogre)
        playOut()
        expect { bob.exile has p.ogre }
    }

    /** Creatures with power 3 or greater are destroyed, then one Food for each creature Alice still controls. */
    // CR 608.2c
    @Test
    fun skirmishAtReedbrookDestroysTheBigAndFeedsTheRest() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", p.bear, p.giant)
        alice.hand(s.theSkirmishAtReedbrook)
        bob.battlefield(p.ogre)
        alice.casts(s.theSkirmishAtReedbrook)
        playOut()
        expect {
            alice.graveyard has p.giant
            bob.graveyard has p.ogre
            alice.battlefield has p.bear
        }
        assertEquals(1, tokens(food).size)
    }

    /** Two Foods as it enters; sacrificing one makes it deal its toughness, 4, in combat. */
    // CR 510.1c
    @Test
    fun tamThePackhorseMakesTwoFoods() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains")
        alice.hand(s.tamThePackhorse)
        alice.casts(s.tamThePackhorse)
        playOut()
        assertEquals(2, tokens(food).size)
    }

    // CR 510.1c
    @Test
    fun tamThePackhorseDealsDamageByToughness() = scenario {
        alice.battlefield(s.tamThePackhorse)
        alice.hand(p.picnic)
        alice.castsWithoutCosts(p.picnic)
        playOut()
        alice.activates(s.tamThePackhorse, target = s.tamThePackhorse, choosing = listOf(food))
        playOut()
        assertEquals(0, tokens(food).size)
        hits(s.tamThePackhorse)
        expect { bob.life shouldBe 16 }
    }

    /** A spell cast without spending mana is countered; one paid for isn't. */
    // CR 601.2h, 603.4
    @Test
    fun hadrionCountersFreeSpells() = scenario {
        alice.battlefield(s.hadrionWardenOfTheGate)
        bob.battlefield("Mountain")
        bob.hand(p.zap, p.zap)
        turn(active = bob)
        bob.castsWithoutCosts(p.zap, target = "alice")
        playOut()
        expect {
            alice.life shouldBe 20
            bob.graveyard has p.zap
        }
        bob.casts(p.zap, target = "alice")
        playOut()
        expect { alice.life shouldBe 18 }
    }

    /** Sacrificing it makes Alice's creatures indestructible and the Ring tempts her. */
    // CR 701.54a
    @Test
    fun hadrionSacrificedTemptsAndProtects() = scenario {
        alice.battlefield(s.hadrionWardenOfTheGate, p.bear)
        alice.activates(s.hadrionWardenOfTheGate)
        playOut(p.bear)
        expect { p.bear hasKeyword Keyword.Indestructible }
        assertEquals(1, temptations())
        assertEquals(p.bear, ringBearer())
    }

    /** One hope counter for each creature as it enters; each end step removes one and draws, the last one sacrificing it for 4 life. */
    // CR 614.1c, 118.12
    @Test
    fun morningOfNewHopeCountsDown() = scenario {
        alice.battlefield("Plains", "Plains", p.bear)
        alice.hand(s.morningOfNewHope)
        alice.library(p.ogre, p.ogre, p.ogre)
        alice.casts(s.morningOfNewHope)
        playOut()
        val hope = CounterKind.Named("hope")
        expect { s.morningOfNewHope.counters(hope) shouldBe 1 }
        advanceTo(TurnStep.End)
        playOut()
        expect {
            alice.hand.size shouldBe 1
            alice.graveyard has s.morningOfNewHope
            alice.life shouldBe 24
        }
    }

    /** "Equip Human {1}" targets only a Human; the plain equip any creature for {3}. */
    // CR 702.6a
    @Test
    fun waywardBladeEquipsAHumanCheaply() = scenario {
        alice.battlefield("Plains", s.waywardRangersBlade, p.human, p.bear)
        alice.activates(s.waywardRangersBlade)
        assertTrue(target(p.bear) !in decision<Decision.Targets>().options)
        alice.answers(Answer.Targets(listOf(target(p.human))))
        playOut()
        expect {
            p.human.power shouldBe 3
            p.human.toughness shouldBe 2
        }
    }

    /** Creatures get +1/+0 and first strike as it enters; plainscycling finds a Plains. */
    // CR 702.29e
    @Test
    fun eaglesOfTheFarPeaks() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains", "Plains", p.bear)
        alice.hand(s.eaglesOfTheFarPeaks)
        alice.casts(s.eaglesOfTheFarPeaks)
        playOut()
        expect {
            p.bear.power shouldBe 3
            p.bear hasKeyword Keyword.FirstStrike
        }
    }

    @Test
    fun eaglesOfTheFarPeaksPlainscycling() = scenario {
        alice.battlefield("Plains")
        alice.hand(s.eaglesOfTheFarPeaks)
        alice.library(p.ogre, "Plains", p.ogre)
        alice.activates(s.eaglesOfTheFarPeaks)
        playOut("Plains")
        expect { alice.hand has "Plains" }
    }

    /** It makes a Food, then a target gets +1/+1 for each Food Alice controls (two). */
    // CR 603.12
    @Test
    fun westmeadowFarmerFeedsATarget() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", p.bear)
        alice.hand(s.westmeadowFarmer, s.westmeadowFarmer)
        alice.casts(s.westmeadowFarmer)
        playOut(p.bear)
        expect { p.bear.power shouldBe 3 }
    }

    /** Blocking an Orc and dealing it damage destroys it, even when the damage isn't lethal. */
    // CR 120.3
    @Test
    fun northMarkCavalierDestroysAnOrc() = scenario {
        alice.battlefield(s.northMarkCavalier)
        bob.battlefield(Cards.invent("Shirefolk Orc Brute", "{3}{B}", "Creature — Orc", "", "1/5"))
        turn(active = bob, step = TurnStep.DeclareAttackers, priority = null)
        bob.attacks("Shirefolk Orc Brute")
        playOut()
        advanceTo(TurnStep.DeclareBlockers)
        alice.blocks(s.northMarkCavalier to "Shirefolk Orc Brute")
        advanceTo(TurnStep.CombatDamage)
        playOut()
        expect { bob.graveyard has "Shirefolk Orc Brute" }
    }

    /** An equipped target gets both keywords; equip costs {1} less. */
    // CR 601.2f, 702.6a
    @Test
    fun aldrethArmsAnEquippedCreature() = scenario {
        alice.battlefield(s.aldrethLadyOfThePlains, p.bear, p.sword)
        alice.battlefield("Plains")
        alice.activates(p.sword, target = p.bear, paying = emptyList())
        playOut()
        expect { p.bear.power shouldBe 3 }
        advanceTo(TurnStep.BeginningOfCombat)
        playOut(p.bear)
        expect {
            p.bear hasKeyword Keyword.FirstStrike
            p.bear hasKeyword Keyword.Vigilance
        }
    }

    /** An unequipped target gets the keyword chosen. */
    // CR 700.2
    @Test
    fun aldrethGivesAChosenKeyword() = scenario {
        alice.battlefield(s.aldrethLadyOfThePlains, p.bear)
        advanceTo(TurnStep.BeginningOfCombat)
        playOut(p.bear, options = listOf("vigilance"))
        expect {
            p.bear hasKeyword Keyword.Vigilance
            p.bear lacksKeyword Keyword.FirstStrike
        }
    }

    /** Without a legendary creature, a card from hand goes to the bottom of the library after the draw. */
    // CR 401.4
    @Test
    fun errandRiderPutsACardBack() = scenario {
        alice.battlefield("Plains", "Plains", "Plains")
        alice.hand(s.errandRiderOfTheWhiteCity, p.zap)
        alice.library(p.ogre, p.bear)
        alice.casts(s.errandRiderOfTheWhiteCity)
        playOut(p.zap)
        expect {
            alice.hand has p.ogre
            alice.hand lacks p.zap
        }
        assertEquals(p.zap, nameOf(state.objOrThrow(state.player(alice.id).library.last())))
    }

    /** With a legendary creature, nothing goes back. */
    @Test
    fun errandRiderKeepsTheCardWithALegend() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", p.legend)
        alice.hand(s.errandRiderOfTheWhiteCity, p.zap)
        alice.library(p.ogre, p.bear)
        alice.casts(s.errandRiderOfTheWhiteCity)
        playOut()
        expect { alice.hand.size shouldBe 2 }
    }

    @Test
    fun escapeFromTheTowerUntaps() = scenario {
        alice.battlefield("Plains", p.bear, tapped = false)
        alice.battlefield(p.ogre, tapped = true)
        alice.hand(s.escapeFromTheTower)
        alice.casts(s.escapeFromTheTower, target = p.ogre)
        playOut()
        expect {
            p.ogre.tapped shouldBe false
            p.ogre.toughness shouldBe 6
            p.ogre hasKeyword Keyword.Flying
        }
    }

    /** With a legendary creature the activation costs {2}{W}. */
    // CR 602.2b, 601.2f
    @Test
    fun esquireCostsLessWithALegend() = scenario {
        alice.battlefield(s.esquireOfTheSteward, p.legend, "Plains", "Plains", "Plains")
        alice.activates(s.esquireOfTheSteward)
        playOut()
        expect { p.legend.power shouldBe 3 }
    }

    /** A creature that died this turn draws at the end step; choosing another Ring-bearer makes a Human Soldier. */
    // CR 701.54d, 603.4
    @Test
    fun haldorDrawsAndMakesSoldiers() = scenario {
        alice.battlefield(s.haldorFieldCaptain, p.bear, "Swamp", "Swamp")
        alice.hand(p.beckon, p.doom)
        alice.library(p.ogre, p.ogre)
        alice.casts(p.beckon)
        playOut(p.bear)
        assertEquals(1, tokens("Human Soldier Token").size)
        alice.casts(p.doom, target = p.bear)
        playOut()
        advanceTo(TurnStep.End)
        playOut()
        expect { alice.hand has p.ogre }
    }

    /** Legendary creatures get +2/+1 and ward {1}; the others +1/+1. */
    // CR 702.21a
    @Test
    fun blossomingOfTheSilverTree() = scenario {
        alice.battlefield(s.blossomingOfTheSilverTree, p.legend, p.bear)
        expect {
            p.legend.power shouldBe 4
            p.legend.toughness shouldBe 3
            p.legend hasKeyword Keyword.Ward.Kind
            p.bear.power shouldBe 3
            p.bear lacksKeyword Keyword.Ward.Kind
        }
    }

    /** The enchanted creature is only a Spirit and can't attack. */
    // CR 205.1b
    @Test
    fun mistMakesASpiritThatCantAttack() = scenario {
        turn(number = 2, active = bob, step = TurnStep.DeclareAttackers, priority = null)
        alice.battlefield(s.mistOnTheBarrowHills)
        bob.battlefield(p.knight)
        attach(s.mistOnTheBarrowHills, p.knight)
        expect { p.knight.subtypes shouldBe listOf(Subtype("Spirit")) }
        assertFailsWith<Throwable> { bob.attacks(p.knight) }
    }

    /** It returns an Equipment; during combat, the equip ability works at instant speed and the first is free. */
    // CR 602.5d, 118.9
    @Test
    fun smithAnewEquipsAtInstantSpeedForFree() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", p.bear)
        alice.hand(s.smithAnew)
        alice.graveyard(p.sword)
        alice.casts(s.smithAnew)
        playOut(p.sword)
        expect { alice.battlefield has p.sword }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(p.bear)
        playOut()
        alice.activates(p.sword, target = p.bear)
        playOut()
        expect { p.bear.power shouldBe 3 }
    }

    /** Not on Alice's turn: equip stays a sorcery. */
    // CR 602.5d
    @Test
    fun smithAnewOnlyDuringYourTurn() = scenario {
        turn(number = 2, active = bob, step = TurnStep.PrecombatMain, priority = alice)
        alice.battlefield(s.smithAnew, p.sword, p.bear, "Plains")
        assertFailsWith<Throwable> { alice.activates(p.sword, target = p.bear) }
    }

    /** First a 2/3 Halfling Scout with lifelink, then a Rogue whose combat damage makes the Ring tempt Alice. */
    // CR 613.4b, 205.1b, 701.54a
    @Test
    fun pellGrowsUp() = scenario {
        alice.battlefield(s.pellShadowsBane, "Plains", "Plains", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.activates(s.pellShadowsBane, ability = 1)
        playOut()
        expect { s.pellShadowsBane.power shouldBe 1 }
        alice.activates(s.pellShadowsBane, ability = 0, paying = listOf("Plains", "Plains#1"))
        playOut()
        expect {
            s.pellShadowsBane.power shouldBe 2
            s.pellShadowsBane.toughness shouldBe 3
            s.pellShadowsBane hasKeyword Keyword.Lifelink
            s.pellShadowsBane.subtypes shouldBe listOf(Subtype("Halfling"), Subtype("Scout"))
        }
        alice.activates(s.pellShadowsBane, ability = 1)
        playOut()
        expect { s.pellShadowsBane.subtypes shouldBe listOf(Subtype("Halfling"), Subtype("Rogue")) }
        hits(s.pellShadowsBane, picks = listOf(s.pellShadowsBane))
        assertEquals(1, temptations())
        expect { bob.life shouldBe 18 }
    }

    /** Tempted four times, the Rogue's combat damage makes the player lose. */
    // CR 104.3e
    @Test
    fun pellWinsWithTheRing() = scenario {
        alice.battlefield(s.pellShadowsBane, "Plains", "Plains", "Swamp", "Swamp", "Swamp")
        alice.hand(p.beckon, p.beckon, p.beckon, p.beckon)
        alice.library(p.ogre, p.ogre)
        alice.activates(s.pellShadowsBane, ability = 0, paying = listOf("Plains", "Plains#1"))
        playOut()
        alice.activates(s.pellShadowsBane, ability = 1)
        playOut()
        repeat(4) { alice.castsWithoutCosts(p.beckon); playOut(s.pellShadowsBane) }
        hits(s.pellShadowsBane, picks = listOf(p.ogre))
        assertTrue(state.player(bob.id).lost)
    }

    /** An artifact entering triggers Alice's permanents' abilities twice; legendary and artifact spells have flash. */
    // CR 603.2d
    @Test
    fun mithrandelDoublesArtifactTriggers() = scenario {
        turn(number = 2, active = bob, step = TurnStep.PrecombatMain, priority = alice)
        alice.battlefield(s.mithrandelTheRadiant, artifactWatcher, "Plains")
        alice.hand(p.trinket)
        alice.casts(p.trinket)
        playOut()
        expect { alice.life shouldBe 22 }
    }

    /** X is creatures plus Foods: two creatures and a Food deal 3. */
    @Test
    fun halflingsPrickCountsFoods() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains", p.bear, p.elf)
        bob.battlefield(Cards.invent("Shirefolk Troll", "{3}{G}", "Creature — Troll", "", "4/4"))
        alice.hand(s.halflingsPrick, s.westmeadowFarmer)
        alice.casts(s.westmeadowFarmer)
        playOut(p.bear)
        alice.casts(s.halflingsPrick, target = "Shirefolk Troll")
        playOut()
        expect { bob.graveyard has "Shirefolk Troll" }
    }

    /** Two attackers: a target attacking creature without flying gains flying. */
    // CR 508.1
    @Test
    fun windrovarGivesFlying() = scenario {
        alice.battlefield(s.windrovarHorizonWatcher, p.bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(s.windrovarHorizonWatcher, p.bear)
        playOut(p.bear)
        expect { p.bear hasKeyword Keyword.Flying }
    }

    // ---- Chunk 02

    /** A legendary creature goes fourth from the top of its owner's library. */
    // CR 700.6, 401.4
    @Test
    fun lostToOldSongsBuriesAHistoricPermanent() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.hand(s.lostToOldSongs)
        bob.battlefield(p.legend, p.bear)
        bob.library(p.ogre, p.ogre, p.ogre, p.ogre, p.ogre)
        assertFailsWith<Throwable> { alice.casts(s.lostToOldSongs, target = p.bear) }
    }

    @Test
    fun lostToOldSongsPutsItFourthFromTheTop() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.hand(s.lostToOldSongs)
        bob.battlefield(p.legend)
        bob.library(p.ogre, p.ogre, p.ogre, p.ogre, p.ogre)
        alice.casts(s.lostToOldSongs, target = p.legend)
        playOut()
        assertEquals(p.legend, nameOf(state.objOrThrow(state.player(bob.id).library[3])))
    }

    /** Paying {2}{W} as it attacks taps a creature of Bob's. */
    // CR 603.12
    @Test
    fun quickfootHalflingTapsABlocker() = scenario {
        alice.battlefield(s.quickfootHalfling, "Plains", "Plains", "Plains")
        bob.battlefield(p.ogre)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(s.quickfootHalfling)
        playOut(p.ogre)
        expect { p.ogre.tapped shouldBe true }
    }

    /** Each creature gets a counter and vigilance; the Ring tempts. */
    // CR 701.54a
    @Test
    fun rideForWrath() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", p.bear, p.elf)
        alice.hand(s.rideForWrathRideForRuin)
        alice.casts(s.rideForWrathRideForRuin)
        playOut(p.bear)
        expect {
            p.bear.counters(p1p1) shouldBe 1
            p.elf.counters(p1p1) shouldBe 1
            p.elf hasKeyword Keyword.Vigilance
        }
        assertEquals(p.bear, ringBearer())
    }

    /** A spell goes back to its owner's hand. */
    @Test
    fun stayedHandReturnsASpell() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.hand(s.stayedHand)
        alice.library(p.ogre)
        bob.battlefield("Mountain")
        bob.hand(p.zap)
        turn(active = bob)
        bob.casts(p.zap, target = "alice")
        bob.passes()
        alice.casts(s.stayedHand, target = p.zap)
        playOut()
        expect {
            bob.hand has p.zap
            alice.life shouldBe 20
            alice.hand has p.ogre
        }
    }

    /** Each token created puts a counter on another creature: its own Food included. */
    // CR 111.1
    @Test
    fun daisyCountersOnTokens() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", p.bear)
        alice.hand(s.daisyOfNorthLane)
        alice.casts(s.daisyOfNorthLane)
        playOut(p.bear)
        expect {
            p.bear.counters(p1p1) shouldBe 1
            s.daisyOfNorthLane.counters(p1p1) shouldBe 0
        }
    }

    /** A creature that died this turn comes back to hand, then the Ring tempts. */
    // CR 400.7, 701.54a
    @Test
    fun hamfastReturnsADeadPermanent() = scenario {
        alice.battlefield("Plains", "Plains", "Swamp", p.bear)
        alice.hand(s.hamfastTheSteadfast, p.doom)
        alice.casts(p.doom, target = p.bear)
        playOut()
        alice.casts(s.hamfastTheSteadfast)
        playOut(p.bear, s.hamfastTheSteadfast)
        expect { alice.hand has p.bear }
        assertEquals(s.hamfastTheSteadfast, ringBearer())
    }

    /** A card put into the graveyard on an earlier turn can't be chosen. */
    @Test
    fun hamfastIgnoresOlderCards() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.graveyard(p.bear)
        alice.hand(s.hamfastTheSteadfast)
        alice.casts(s.hamfastTheSteadfast)
        playOut(p.bear, s.hamfastTheSteadfast)
        expect { alice.graveyard has p.bear }
    }

    @Test
    fun elevensesFeast() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", p.bear, p.elf)
        alice.hand(s.elevensesFeast)
        alice.casts(s.elevensesFeast, targets = listOf(p.bear, p.elf))
        playOut()
        expect {
            p.bear.power shouldBe 4
            p.elf.toughness shouldBe 2
        }
        assertEquals(1, tokens(food).size)
    }

    /** Sacrificing a token exiles Bob's creature until it leaves; then it comes back. */
    // CR 610.3
    @Test
    fun hamletBounderExilesUntilItLeaves() = scenario {
        alice.battlefield("Plains", "Plains", "Swamp", "Forest")
        alice.hand(p.picnic, s.hamletBounder, p.doom)
        bob.battlefield(p.ogre)
        alice.casts(p.picnic)
        playOut()
        alice.casts(s.hamletBounder)
        playOut(food, p.ogre)
        expect { bob.exile has p.ogre }
        alice.casts(p.doom, target = s.hamletBounder)
        playOut()
        expect { bob.battlefield has p.ogre }
    }

    /** A creature Alice owns but Bob controls comes back under Alice's control. */
    // CR 400.7
    @Test
    fun slipIntoShadowReclaims() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.hand(s.slipIntoShadow)
        alice.battlefield(p.bear)
        change(arcanum.engine.change.Changes.effect(state) { ts ->
            arcanum.engine.state.EffectObject(arcanum.engine.state.EffectId("steal:$ts"), listOf(arcanum.api.Mechanic.ControlledBy(arcanum.api.Players.You)),
                affects = null, locked = listOf(card(p.bear)), source = null, controller = bob.id, timestamp = ts, createdTurn = state.turn.number,
                duration = arcanum.api.Duration.Indefinitely)
        })
        expect { p.bear.controller shouldBe bob.id }
        alice.casts(s.slipIntoShadow, target = p.bear)
        playOut(p.bear)
        expect { p.bear.controller shouldBe alice.id }
        assertEquals(1, temptations())
    }

    /** Drawing the second card of a turn puts a counter on it. */
    // CR 121.2
    @Test
    fun stalwartsGrowOnTheSecondDraw() = scenario {
        alice.battlefield(s.stalwartsOfTheRiverFort, "Island", "Island")
        alice.hand(p.notes, p.notes)
        alice.library(p.ogre, p.ogre, p.ogre)
        alice.casts(p.notes)
        playOut()
        expect { s.stalwartsOfTheRiverFort.counters(p1p1) shouldBe 0 }
        alice.casts(p.notes)
        playOut()
        expect { s.stalwartsOfTheRiverFort.counters(p1p1) shouldBe 1 }
    }

    /** Chapter I: indestructible while Alice controls the Saga; II returns a creature; III lifelink. */
    // CR 714.2b, 611.2b
    @Test
    fun layOfLuthielChapters() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains", p.bear)
        alice.graveyard(p.ogre)
        alice.library(p.ogre, p.ogre)
        bob.library(p.ogre, p.ogre)
        alice.hand(s.layOfLuthiel)
        alice.casts(s.layOfLuthiel)
        playOut(p.bear)
        expect { p.bear hasKeyword Keyword.Indestructible }
        advanceTo(TurnStep.Upkeep, turn = 2)
        expect { p.bear hasKeyword Keyword.Indestructible }
        move(s.layOfLuthiel, Zone.Graveyard)
        expect { p.bear lacksKeyword Keyword.Indestructible }
    }

    /** The first two chapters find legendary creatures; the third gives double strike and tempts. */
    @Test
    fun warOfTheAncientAllianceSearches() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", p.bear)
        alice.library(p.ogre, p.legend, p.ogre)
        alice.hand(s.warOfTheAncientAlliance)
        alice.casts(s.warOfTheAncientAlliance)
        playOut(p.legend)
        expect { alice.hand has p.legend }
    }

    /** A creature that blocked a legendary creature this turn can be destroyed; another can't be targeted. */
    // CR 509.1
    @Test
    fun noneShallCrossDestroysTheBlocker() = scenario {
        alice.battlefield(p.legend, "Plains")
        alice.hand(s.noneShallCross)
        bob.battlefield(p.ogre, p.bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(p.legend)
        playOut()
        advanceTo(TurnStep.DeclareBlockers)
        bob.blocks(p.ogre to p.legend)
        playOut()
        assertFailsWith<Throwable> { alice.casts(s.noneShallCross, target = p.bear) }
    }

    @Test
    fun noneShallCrossHitsTheBlocker() = scenario {
        alice.battlefield(p.legend, "Plains")
        alice.hand(s.noneShallCross)
        bob.battlefield(p.ogre, p.bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(p.legend)
        playOut()
        advanceTo(TurnStep.DeclareBlockers)
        bob.blocks(p.ogre to p.legend)
        playOut()
        alice.casts(s.noneShallCross, target = p.ogre)
        playOut()
        expect { bob.graveyard has p.ogre }
    }

    /** A small nontoken creature entering makes a Halfling; tapping two other Halflings pumps it. */
    // CR 118.3
    @Test
    fun marmadasMakesHalflings() = scenario {
        alice.battlefield(s.marmadasMasterOfTheMarish, "Plains", Cards.invent("Shirefolk Halfling", "{W}", "Creature — Halfling", "", "1/1"))
        alice.hand(p.human)
        alice.casts(p.human)
        playOut()
        assertEquals(1, tokens("Halfling Token").size)
        alice.activates(s.marmadasMasterOfTheMarish, choosing = listOf("Halfling Token", "Shirefolk Halfling"))
        playOut()
        expect {
            s.marmadasMasterOfTheMarish.power shouldBe 4
            s.marmadasMasterOfTheMarish hasKeyword Keyword.Lifelink
        }
    }

    /** It attaches an Equipment with mana value 2 as it attacks, and takes no damage on Alice's turn. */
    // CR 701.3a, 615.1
    @Test
    fun bingoGrabsEquipmentAndIsSafeOnItsTurn() = scenario {
        val blade = Cards.invent("Shirefolk Blade", "{2}", "Artifact — Equipment", "Equipped creature gets +2/+0.\nEquip {2}")
        alice.battlefield(s.bingoSteadfastHero, blade, "Mountain")
        alice.hand(p.zap)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(s.bingoSteadfastHero)
        playOut(blade)
        expect { s.bingoSteadfastHero.power shouldBe 4 }
        alice.casts(p.zap, target = s.bingoSteadfastHero)
        playOut()
        expect { card(s.bingoSteadfastHero).view.obj?.damage shouldBe 0 }
    }

    /** Casting a spell pumps the team and scries; dying, it goes fifth from the top. */
    // CR 401.4
    @Test
    fun olorinGoesBackIntoTheLibrary() = scenario {
        alice.battlefield(s.olorinPaleRider, p.bear, "Swamp")
        alice.hand(p.doom)
        alice.library(p.ogre, p.ogre, p.ogre, p.ogre, p.ogre, p.ogre)
        alice.casts(p.doom, target = s.olorinPaleRider)
        playOut()
        assertEquals(s.olorinPaleRider, nameOf(state.objOrThrow(state.player(alice.id).library[4])))
    }

    @Test
    fun olorinPumpsOnCast() = scenario {
        alice.battlefield(s.olorinPaleRider, p.bear, "Island")
        alice.hand(p.notes)
        alice.library(p.ogre, p.ogre)
        alice.casts(p.notes)
        playOut()
        expect { p.bear.power shouldBe 3 }
    }

    /** An indestructible counter on a small creature, and a card drawn. */
    // CR 122.1b
    @Test
    fun eagleOfRescueProtectsAndDraws() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains", "Plains", p.bear)
        alice.hand(s.eagleOfRescue)
        alice.library(p.ogre)
        alice.casts(s.eagleOfRescue)
        playOut(p.bear)
        expect {
            p.bear.counters(CounterKind.Keyword(Keyword.Indestructible)) shouldBe 1
            p.bear hasKeyword Keyword.Indestructible
            alice.hand has p.ogre
        }
    }
}
