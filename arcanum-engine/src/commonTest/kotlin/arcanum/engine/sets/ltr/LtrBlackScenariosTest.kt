package arcanum.engine.sets.ltr

import arcanum.api.CardType
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
 * The Lord of the Rings cards of parser chunks 05–06 in play: the black cards. Trivial ones not played here: The Dark Breath, Grasp the
 * Precious, Hamfast's Desperate Rescue and Uruk Berserker (an effect and the Ring tempting, as Ride for Wrath), Fenland Crebain, Southron
 * Vanguard and Dark Land Muster (amass by a trigger or a spell, as the Hobbit's amass scenarios), Orcish Poultice (a keyword chosen, as
 * Aldreth), Spider's Ambush (a pump and a Food), Spider Pass Patrol (a sacrifice outlet drawing and making a Food).
 */
class LtrBlackScenariosTest {
    private val s = LtrSet
    private val p = LtrPlain
    private val p1p1 = LtrPlain.p1p1
    private val swamps = arrayOf("Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
    private val wraith = Cards.invent("Shirefolk Barrow-Wight", "{1}{B}", "Creature — Wraith", "", "1/1")

    // ---- Chunk 05

    /** A creature dealt damage this turn makes it cost {B}: destroyed, and its controller loses 2 life. */
    // CR 601.2f
    @Test
    fun bitterUndoingFinishesADamagedCreature() = scenario {
        alice.battlefield("Swamp", "Mountain")
        alice.hand(p.zap, s.bitterUndoing)
        bob.battlefield(Cards.invent("Shirefolk Troll", "{3}{G}", "Creature — Troll", "", "4/4"))
        alice.casts(p.zap, target = "Shirefolk Troll")
        playOut()
        alice.casts(s.bitterUndoing, target = "Shirefolk Troll")
        playOut()
        expect {
            bob.graveyard has "Shirefolk Troll"
            bob.life shouldBe 18
        }
    }

    /** Choosing a Ring-bearer lets Alice pay 2 life to draw. */
    // CR 701.54a
    @Test
    fun lureOfTheRingDrawsForLife() = scenario {
        alice.battlefield(s.lureOfTheRing, p.bear, "Swamp")
        alice.hand(p.beckon)
        alice.library(p.ogre)
        alice.casts(p.beckon)
        playOut(p.bear)
        expect {
            alice.life shouldBe 18
            alice.hand has p.ogre
        }
    }

    /** It leaving tempts; from the graveyard, a sacrifice brings it back to hand. */
    // CR 113.6
    @Test
    fun smeagorPatientPlotterReturns() = scenario {
        alice.battlefield(s.smeagorPatientPlotter, p.bear, "Swamp", "Swamp")
        alice.hand(p.doom)
        alice.casts(p.doom, target = s.smeagorPatientPlotter)
        playOut(p.bear)
        assertEquals(1, temptations())
        alice.activates(s.smeagorPatientPlotter, choosing = listOf(p.bear))
        playOut()
        expect { alice.hand has s.smeagorPatientPlotter }
    }

    /** Exiled from the graveyard, it makes the Ring tempt Alice. */
    @Test
    fun wretchsBiteFromTheGraveyard() = scenario {
        alice.battlefield(*swamps)
        alice.graveyard(s.wretchsBite)
        alice.activates(s.wretchsBite)
        playOut()
        assertEquals(1, temptations())
        expect { alice.exile has s.wretchsBite }
    }

    /** An Orc's combat damage: sacrificing it draws a card (the first mode). */
    // CR 603.12
    @Test
    fun shagulSacrificesForACard() = scenario {
        alice.battlefield(s.shagulOfTheDeadTower, p.orc)
        alice.library(p.ogre)
        unblocked(p.orc)
        advanceTo(TurnStep.CombatDamage)
        if (pending is Decision.May) answer(Answer.YesNo(true))
        playOut()
        if (pending is Decision.Modes) modes(0)
        playOut()
        expect {
            alice.graveyard has p.orc
            alice.hand has p.ogre
        }
    }

    /** Creature tokens Alice controls have deathtouch. */
    @Test
    fun gorthakGivesTokensDeathtouch() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(s.gorthakMorgulLieutenant)
        alice.casts(s.gorthakMorgulLieutenant)
        playOut()
        val army = tokens("Orc Army Token").single()
        assertTrue(view()[army]!!.characteristics!!.hasKeyword(Keyword.Deathtouch))
    }

    /** Opponents can't gain life; sacrificing a legendary creature amasses Orcs 2. */
    // CR 119.7, 608.2h
    @Test
    fun wormwoodTheWhisperer() = scenario {
        alice.battlefield(s.wormwoodTheWhisperer, p.legend)
        bob.battlefield("Plains")
        bob.hand(p.heal)
        alice.activates(s.wormwoodTheWhisperer, target = "bob", choosing = listOf(p.legend))
        playOut()
        expect { bob.life shouldBe 19 }
        assertEquals(2, state.obj(tokens("Orc Army Token").single())!!.counters(p1p1))
    }

    @Test
    fun wormwoodStopsLifeGain() = scenario {
        turn(active = bob)
        alice.battlefield(s.wormwoodTheWhisperer)
        bob.battlefield("Plains")
        bob.hand(p.heal)
        bob.casts(p.heal)
        playOut()
        expect { bob.life shouldBe 20 }
    }

    /** With an Army on Alice's turn it is a 5/5 artifact creature; not on Bob's turn. */
    // CR 301.7
    @Test
    fun grundorIsACreatureWithAnArmy() = scenario {
        alice.battlefield(s.grundorTheGatebreaker, "Swamp", "Swamp", "Swamp")
        alice.hand(s.southronVanguard, p.doom)
        expect { s.grundorTheGatebreaker.types shouldBe listOf(CardType.Artifact) }
        alice.casts(s.southronVanguard)
        playOut()
        alice.casts(p.doom, target = s.southronVanguard)
        playOut()
        expect { assertTrue(CardType.Creature in s.grundorTheGatebreaker.types) }
    }

    /** With a legendary creature it comes back from the graveyard tapped. */
    // CR 113.6
    @Test
    fun hauntOfTheMereReturns() = scenario {
        alice.battlefield(p.legend, "Swamp", "Swamp", "Swamp")
        alice.graveyard(s.hauntOfTheMereOfGhosts)
        alice.activates(s.hauntOfTheMereOfGhosts)
        playOut()
        expect {
            alice.battlefield has s.hauntOfTheMereOfGhosts
            s.hauntOfTheMereOfGhosts.tapped shouldBe true
        }
    }

    /** Castable with a legendary creature; the controller's hand goes down to four. */
    // CR 205.4e, 402
    @Test
    fun kingsFatefulStrike() = scenario {
        alice.battlefield(p.legend, *swamps)
        alice.hand(s.theKingsFatefulStrike)
        bob.battlefield(p.ogre)
        bob.hand(p.bear, p.bear, p.bear, p.bear, p.bear, p.bear)
        alice.casts(s.theKingsFatefulStrike, target = p.ogre)
        playOut(p.bear, p.bear)
        expect {
            bob.graveyard has p.ogre
            bob.hand.size shouldBe 4
            bob.exile.size shouldBe 2
        }
    }

    @Test
    fun kingsFatefulStrikeNeedsALegend() = scenario {
        alice.battlefield(*swamps)
        alice.hand(s.theKingsFatefulStrike)
        bob.battlefield(p.ogre)
        assertFailsWith<Throwable> { alice.casts(s.theKingsFatefulStrike, target = p.ogre) }
    }

    /** Paying {4} instead of sacrificing. */
    // CR 601.2b
    @Test
    fun lashOfTheFireDemonPaysFour() = scenario {
        alice.battlefield(*swamps)
        alice.hand(s.lashOfTheFireDemon)
        bob.battlefield(p.ogre)
        alice.casts(s.lashOfTheFireDemon, target = p.ogre, option = "{4}")
        playOut()
        expect { bob.graveyard has p.ogre }
    }

    /** A creature card put into Bob's graveyard this turn is exiled: Treasures equal to its power. */
    // CR 400.7, 608.2h
    @Test
    fun hildaExilesTheFallen() = scenario {
        alice.battlefield(*swamps)
        alice.hand(p.doom, s.hildaBracegirdlePott)
        bob.battlefield(p.ogre)
        alice.casts(p.doom, target = p.ogre)
        playOut()
        alice.casts(s.hildaBracegirdlePott)
        playOut(p.ogre)
        expect { bob.exile has p.ogre }
        assertEquals(3, tokens("Treasure Token").size)
    }

    /** It amasses as it enters and whenever an Army attacks. */
    @Test
    fun marchFromTheDarkGate() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.hand(s.marchFromTheDarkGate)
        alice.library(p.ogre, p.ogre)
        bob.library(p.ogre, p.ogre)
        alice.casts(s.marchFromTheDarkGate)
        playOut()
        val army = tokens("Orc Army Token").single()
        advanceTo(TurnStep.DeclareAttackers, turn = 3)
        alice.attacks("Orc Army Token")
        playOut()
        assertEquals(2, state.obj(army)!!.counters(p1p1))
    }

    /** Creating a token, and sacrificing one, each drain 1. */
    @Test
    fun gloomwoodBatsDrain() = scenario {
        alice.battlefield(s.gloomwoodBats, "Forest")
        alice.hand(p.picnic)
        alice.casts(p.picnic)
        playOut()
        expect { bob.life shouldBe 19 }
    }

    /** Attacking with an Orc makes a 2/1 flying Ballistic Boulder attacking, sacrificed at end of combat. */
    // CR 508.4
    @Test
    fun darkLandTrebuchetHurlsABoulder() = scenario {
        alice.battlefield(s.darkLandTrebuchet, p.orc)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(p.orc)
        playOut()
        assertEquals(1, tokens("Ballistic Boulder").size)
        advanceTo(TurnStep.DeclareBlockers)
        if (state.priority.holder == null) bob.blocks()
        advanceTo(TurnStep.EndOfCombat)
        playOut()
        expect { bob.life shouldBe 16 }
        assertTrue(tokens("Ballistic Boulder").isEmpty())
    }

    // ---- Chunk 06

    /** In its upkeep, the enchanted creature is exiled unless its controller pays 2 life. */
    // CR 118.12
    @Test
    fun witchBladeWoundExilesUnlessPaid() = scenario {
        alice.battlefield(s.witchBladeWound)
        bob.battlefield(p.ogre)
        attach(s.witchBladeWound, p.ogre)
        alice.library(p.ogre, p.ogre)
        bob.library(p.ogre, p.ogre)
        expect { p.ogre.power shouldBe 0 }
        advanceTo(TurnStep.Upkeep, turn = 2)
        playOut(may = false)
        if (pending is Decision.May) answer(Answer.YesNo(false))
        playOut()
        expect { bob.exile has p.ogre }
    }

    /** Sacrificing a legendary creature draws three instead of two. */
    // CR 608.2h
    @Test
    fun wretchedEndDrawsThreeForALegend() = scenario {
        alice.battlefield("Swamp", "Swamp", p.legend)
        alice.hand(s.wretchedEnd)
        alice.library(p.ogre, p.ogre, p.ogre, p.ogre)
        alice.casts(s.wretchedEnd, choosing = listOf(p.legend))
        playOut()
        expect { alice.hand.size shouldBe 3 }
    }

    /** The Ring tempting puts a counter on each Wraith. */
    // CR 701.54d
    @Test
    fun ringwightGrowsWraiths() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", wraith)
        alice.hand(s.ringwight)
        alice.casts(s.ringwight)
        playOut(wraith)
        expect {
            wraith.counters(p1p1) shouldBe 1
            s.ringwight.counters(p1p1) shouldBe 1
        }
    }

    /** Chapter I gives Alice and Bob a Food each. */
    @Test
    fun oathOfTheSilentHostFeedsBoth() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(s.oathOfTheSilentHost)
        alice.casts(s.oathOfTheSilentHost)
        playOut("bob")
        val foods = tokens("Food Token")
        assertEquals(setOf(alice.id, bob.id), foods.map { view()[it]!!.controller }.toSet())
    }

    /** Chapter I mills each player by the Ring-bearer's power; III drains per creature card. */
    // CR 701.54e
    @Test
    fun oneRingToBindThemAllMills() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", p.ogre)
        alice.hand(s.oneRingToBindThemAll)
        alice.library(p.bear, p.bear, p.bear, p.bear)
        bob.library(p.bear, p.bear, p.bear, p.bear)
        alice.casts(s.oneRingToBindThemAll)
        playOut(p.ogre)
        expect {
            alice.graveyard.size shouldBe 3
            bob.graveyard.size shouldBe 3
        }
    }

    /** It pings as it enters and amasses; an opponent's second draw of their draw step... any draw but the first pings again. */
    // CR 504.1
    @Test
    fun orcishArrowmastersPunishExtraDraws() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.hand(s.orcishArrowmasters)
        val study = Cards.invent("Shirefolk Quick Study", "{U}", "Instant", "Draw a card.")
        bob.battlefield("Island")
        bob.hand(study)
        bob.library(p.ogre, p.ogre)
        alice.casts(s.orcishArrowmasters)
        playOut("bob")
        expect { bob.life shouldBe 19 }
        assertEquals(1, tokens("Orc Army Token").size)
        alice.passes()
        bob.casts(study)
        playOut("bob")
        expect { bob.life shouldBe 18 }
    }

    /** A 3/3 black Wraith copy attacks; at the end step it is exiled unless Gorthaur is the Ring-bearer. */
    // CR 707.9b, 603.7
    @Test
    fun gorthaurRaisesAWraith() = scenario {
        alice.battlefield(s.gorthaurTheNecromancer)
        alice.graveyard(p.giant)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(s.gorthaurTheNecromancer)
        playOut(p.giant)
        val copy = tokens(p.giant).single()
        val view = view()[copy]!!.characteristics!!
        assertEquals(3, view.power)
        assertEquals(listOf(Subtype("Wraith")), view.subtypes)
        assertTrue(view.hasKeyword(Keyword.Menace))
        advanceTo(TurnStep.End)
        playOut()
        assertTrue(tokens(p.giant).isEmpty())
    }

    /** Exiled creature cards can be cast with any mana. */
    // CR 601.2h
    @Test
    fun shadowOfTheFoeCastsTheirDead() = scenario {
        alice.battlefield(*swamps, "Swamp", "Swamp")
        alice.hand(s.shadowOfTheFoe)
        bob.graveyard(p.bear)
        alice.casts(s.shadowOfTheFoe, target = "bob")
        playOut()
        expect { bob.exile has p.bear }
        alice.casts(p.bear, way = permitted(p.bear))
        playOut()
        expect { p.bear.controller shouldBe alice.id }
    }

    /** It is +1/+0 with an Orc. */
    @Test
    fun snarlingWolfHound() = scenario {
        alice.battlefield(s.snarlingWolfHound, p.orc)
        expect { s.snarlingWolfHound.power shouldBe 4 }
    }

    /** Alice picks the nonland card Bob discards. */
    @Test
    fun tormentOfTheWretch() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(s.theTormentOfTheWretch)
        bob.hand(p.ogre, "Forest")
        alice.casts(s.theTormentOfTheWretch, target = "bob")
        playOut(p.ogre)
        expect { bob.graveyard has p.ogre }
    }

    /** Two blockers aren't enough. */
    // CR 509.1b
    @Test
    fun trollNeedsThreeBlockers() = scenario {
        alice.battlefield(s.trollOfTheDeepHalls)
        bob.battlefield(p.bear, p.elf)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(s.trollOfTheDeepHalls)
        playOut()
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<Throwable> { bob.blocks(p.bear to s.trollOfTheDeepHalls, p.elf to s.trollOfTheDeepHalls) }
    }

    /** Bob sacrifices a creature; Alice gets a Food for it. */
    @Test
    fun ravenousFellSteedFeedsOnTheSacrifice() = scenario {
        alice.battlefield(*swamps)
        alice.hand(s.ravenousFellSteed)
        bob.battlefield(p.bear)
        alice.casts(s.ravenousFellSteed)
        playOut(p.bear)
        expect { bob.graveyard has p.bear }
        assertEquals(1, tokens("Food Token").size)
    }

    /** A creature that dealt combat damage to Alice is sacrificed; the Ring tempts her. */
    // CR 510.1
    @Test
    fun witchLordPunishesAttackers() = scenario {
        alice.battlefield(s.witchLordOfTheNorth, tapped = true)
        bob.battlefield(p.bear, p.ogre)
        turn(active = bob, step = TurnStep.DeclareAttackers, priority = null)
        bob.attacks(p.bear)
        playOut()
        advanceTo(TurnStep.DeclareBlockers)
        if (state.priority.holder == null) alice.blocks()
        advanceTo(TurnStep.CombatDamage)
        playOut(p.bear, s.witchLordOfTheNorth)
        expect {
            bob.graveyard has p.bear
            bob.battlefield has p.ogre
        }
        assertEquals(1, temptations())
    }

    /** Discarding a card makes it indestructible and taps it. */
    @Test
    fun witchLordDiscardsForIndestructible() = scenario {
        alice.battlefield(s.witchLordOfTheNorth)
        alice.hand(p.ogre)
        alice.activates(s.witchLordOfTheNorth, choosing = listOf(p.ogre))
        playOut()
        expect {
            s.witchLordOfTheNorth hasKeyword Keyword.Indestructible
            s.witchLordOfTheNorth.tapped shouldBe true
        }
    }

    /** -3/-3 to a legendary creature costs its controller 3 life; the Ring tempting returns it from the graveyard. */
    @Test
    fun ringboundWraiths() = scenario {
        alice.battlefield(*swamps)
        alice.hand(s.ringboundWraiths)
        bob.battlefield(p.bigLegend)
        alice.casts(s.ringboundWraiths)
        playOut(p.bigLegend)
        expect {
            p.bigLegend.power shouldBe 1
            bob.life shouldBe 17
        }
    }

    @Test
    fun ringboundWraithsReturnWhenTempted() = scenario {
        alice.battlefield("Swamp")
        alice.graveyard(s.ringboundWraiths)
        alice.hand(p.beckon)
        alice.casts(p.beckon)
        playOut()
        expect { alice.hand has s.ringboundWraiths }
    }

    /** Bob guesses right: removed from combat. */
    @Test
    fun smeagorGuessedRightLeavesCombat() = scenario {
        alice.battlefield(s.smeagorSchemingGuide)
        alice.library("Swamp", p.ogre)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(s.smeagorSchemingGuide)
        playOut("Swamp", p.ogre, options = listOf("land"))
        val guess = decision<Decision.ChooseOption>()
        assertEquals(bob.id, guess.player)
        answer(Answer.Option("yes"))
        playOut()
        expect { alice.hand.size shouldBe 0 }
        assertTrue(state.combat?.attackers.orEmpty().none { it.id == card(s.smeagorSchemingGuide) })
    }

    /** A wrong guess draws a card and makes it unblockable. */
    @Test
    fun smeagorGuessedWrongDraws() = scenario {
        alice.battlefield(s.smeagorSchemingGuide)
        alice.library("Swamp", p.ogre)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(s.smeagorSchemingGuide)
        playOut("Swamp", p.ogre, options = listOf("land"))
        answer(Answer.Option("no"))
        playOut()
        expect { alice.hand.size shouldBe 1 }
    }

    /** The defending player sacrifices their smallest creature. */
    @Test
    fun witchLordBringerTakesTheSmallest() = scenario {
        alice.battlefield(s.witchLordBringerOfRuin)
        bob.battlefield(p.bear, p.ogre)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(s.witchLordBringerOfRuin)
        playOut(p.bear)
        expect {
            bob.graveyard has p.bear
            bob.battlefield has p.ogre
        }
    }

    /** Other Orcs have menace; it amasses at the beginning of combat. */
    @Test
    fun wolfRider() = scenario {
        alice.battlefield(s.wolfRider, p.orc)
        expect { p.orc hasKeyword Keyword.Menace }
        advanceTo(TurnStep.BeginningOfCombat)
        playOut()
        assertEquals(1, tokens("Orc Army Token").size)
    }
}
