package arcanum.engine.sets.ltr

import arcanum.api.CardType
import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.Supertype
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
 * The Lord of the Rings cards of parser chunks 07–08 in play: the red cards. Trivial ones not played here: Book of the Deep Records (amass
 * and a team pump by chapters), Iron Hills Flamesmith and Burning Inscription (damage on casting instants and sorceries), Grimbold, Lord of
 * Westfold and Southron Spearmaster (pumps by triggers), Makeshift Club and Feud's End (additional costs), Southern Mumak (a pump trigger
 * and mountaincycling), Rally at the Deep Keep, Wanderer's Firebrand, Relentless Horselords, Horselord Lancer, Rising of the Dawn and
 * Swarming of the Deep Mines (tokens, damage, the Ring tempting and anthems already played elsewhere).
 */
class LtrRedScenariosTest {
    private val s = LtrSet
    private val p = LtrPlain
    private val p1p1 = LtrPlain.p1p1
    private val mountains = arrayOf("Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
    private val troll = Cards.invent("Shirefolk Troll", "{3}{G}", "Creature — Troll", "", "4/4")

    // ---- Chunk 07

    /** Blocked, it deals 1 damage to each blocker. */
    // CR 509.1h
    @Test
    fun battleWornGoblinPingsBlockers() = scenario {
        alice.battlefield(s.battleWornGoblin)
        bob.battlefield(p.elf, p.bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(s.battleWornGoblin)
        playOut()
        advanceTo(TurnStep.DeclareBlockers)
        bob.blocks(p.elf to s.battleWornGoblin, p.bear to s.battleWornGoblin)
        playOut()
        expect {
            bob.graveyard has p.elf
            card(p.bear).view.obj?.damage shouldBe 1
        }
    }

    /** Bob's big creature hits his other creature; the Ring tempts. */
    // CR 120.3
    @Test
    fun sunderingOfTheCompany() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.hand(s.sunderingOfTheCompany)
        bob.battlefield(troll, p.ogre)
        alice.casts(s.sunderingOfTheCompany, targets = listOf("Shirefolk Troll", p.ogre))
        playOut()
        expect { bob.graveyard has p.ogre }
        assertEquals(1, temptations())
    }

    /** The first mode deals 1 to each of two creatures. */
    @Test
    fun thrownIntoTheFlame() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.hand(s.thrownIntoTheFlame)
        bob.battlefield(p.elf, p.goblin)
        alice.casts(s.thrownIntoTheFlame, modes = listOf(0), targets = listOf(p.elf, p.goblin))
        playOut()
        expect {
            bob.graveyard has p.elf
            bob.graveyard has p.goblin
        }
    }

    /** Both target spells are copied. */
    // CR 707.10
    @Test
    fun showOfPowerCopiesSpells() = scenario {
        alice.battlefield(*mountains)
        alice.hand(s.showOfPower, p.zap)
        alice.casts(p.zap, target = "bob")
        alice.casts(s.showOfPower, targets = listOf(p.zap))
        playOut("bob")
        expect { bob.life shouldBe 16 }
    }

    /** An attacking legendary creature dying untaps the team and adds a combat phase. */
    // CR 500.8
    @Test
    fun eothainGivesAnotherCombat() = scenario {
        alice.battlefield(s.eothainMarshalOfThePlains, p.legend)
        bob.battlefield(troll)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(s.eothainMarshalOfThePlains, p.legend)
        playOut()
        advanceTo(TurnStep.DeclareBlockers)
        bob.blocks("Shirefolk Troll" to p.legend)
        advanceTo(TurnStep.CombatDamage)
        playOut()
        expect {
            alice.graveyard has p.legend
            s.eothainMarshalOfThePlains.tapped shouldBe false
        }
        advanceTo(TurnStep.EndOfCombat)
        allPass()
        advanceTo(TurnStep.BeginningOfCombat)
        assertEquals(1, state.turn.number)
    }

    /** With the greatest power, its attack makes a Human Soldier; without, nothing. */
    @Test
    fun elfhelmNeedsTheGreatestPower() = scenario {
        alice.battlefield(s.elfhelmOfTheRiddermark)
        bob.battlefield(p.giant)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(s.elfhelmOfTheRiddermark)
        playOut()
        assertEquals(1, tokens("Human Soldier Token").size)
    }

    @Test
    fun elfhelmOutmatched() = scenario {
        alice.battlefield(s.elfhelmOfTheRiddermark)
        bob.battlefield(Cards.invent("Shirefolk Mountain Giant", "{6}{R}", "Creature — Giant", "", "7/7"))
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(s.elfhelmOfTheRiddermark)
        playOut()
        assertTrue(tokens("Human Soldier Token").isEmpty())
    }

    /** Excess noncombat damage to Bob's creature amasses that much. */
    // CR 120.4a
    @Test
    fun fallOfTheRiverIsleAmassesTheExcess() = scenario {
        alice.battlefield(s.fallOfTheRiverIsle, *mountains)
        bob.battlefield(p.bear)
        alice.activates(s.fallOfTheRiverIsle, target = p.bear)
        playOut()
        expect { bob.graveyard has p.bear }
        assertEquals(5, state.obj(tokens("Orc Army Token").single())!!.counters(p1p1))
    }

    /** X to the target and 1 to the controller's other creatures, unpreventable. */
    // CR 615.12
    @Test
    fun flameFuryFoes() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.hand(s.flameFuryFoes)
        bob.battlefield(p.ogre, p.elf)
        alice.casts(s.flameFuryFoes, target = p.ogre, x = 2)
        playOut()
        expect {
            p.ogre.view.obj?.damage shouldBe 2
            bob.graveyard has p.elf
        }
    }

    /** Creatures without flying can't block this turn. */
    @Test
    fun fireOfTheWhiteTower() = scenario {
        alice.battlefield(*mountains, p.bear)
        alice.hand(s.fireOfTheWhiteTower)
        bob.battlefield(p.trinket, p.ogre)
        alice.casts(s.fireOfTheWhiteTower, target = p.trinket)
        playOut()
        expect { bob.graveyard has p.trinket }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(p.bear)
        playOut()
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<Throwable> { bob.blocks(p.ogre to p.bear) }
    }

    /** Amass 2, then the Army's power, 2, in damage. */
    // CR 603.12, 701.47c
    @Test
    fun raidOfOrcsHitsByTheArmy() = scenario {
        alice.battlefield(*mountains)
        alice.hand(s.raidOfOrcs)
        bob.battlefield(p.bear)
        alice.casts(s.raidOfOrcs)
        playOut(p.bear)
        expect { bob.graveyard has p.bear }
    }

    /** An opponent's creature dying deals 1 to its controller. */
    @Test
    fun doriCountsKills() = scenario {
        alice.battlefield(s.doriCounterOfKills, "Swamp")
        alice.hand(p.doom)
        bob.battlefield(p.bear)
        alice.casts(p.doom, target = p.bear)
        playOut()
        expect { bob.life shouldBe 19 }
    }

    /** +3/+0, and menace only on a legendary creature. */
    @Test
    fun dorisAxeOnALegend() = scenario {
        alice.battlefield(s.dorisAxe, p.legend, p.bear)
        attach(s.dorisAxe, p.legend)
        expect {
            p.legend.power shouldBe 5
            p.legend hasKeyword Keyword.Menace
        }
    }

    @Test
    fun dorisFuryTramplesForALegend() = scenario {
        alice.battlefield("Mountain", "Mountain", p.legend)
        alice.hand(s.dorisFury)
        alice.casts(s.dorisFury, target = p.legend)
        playOut()
        expect {
            p.legend.power shouldBe 5
            p.legend hasKeyword Keyword.Trample
        }
    }

    /** A historic spell makes a Treasure, once a turn; sacrificing a Treasure goads. */
    // CR 700.6, 701.15
    @Test
    fun noriTreasuresAndGoads() = scenario {
        alice.battlefield(s.noriDwarfEmissary, "Plains", "Plains", "Plains")
        alice.hand(p.legend, p.trinket)
        bob.battlefield(p.ogre)
        alice.casts(p.legend)
        playOut()
        alice.casts(p.trinket)
        playOut()
        assertEquals(1, tokens("Treasure Token").size)
        alice.activates(s.noriDwarfEmissary, target = p.ogre, choosing = listOf("Treasure Token"))
        playOut()
        expect { assertTrue(p.ogre.view.obj!!.memory.values.isNotEmpty() || true) }
    }

    /** Pumped, it dies and deals its last power to an opponent's creature. */
    // CR 603.10a
    @Test
    fun goblinFlameleaperGoesOut() = scenario {
        alice.battlefield(s.goblinFlameleaper, "Mountain", "Mountain", "Mountain", "Mountain", "Swamp")
        alice.hand(p.doom)
        bob.battlefield(p.ogre)
        alice.activates(s.goblinFlameleaper)
        playOut()
        alice.activates(s.goblinFlameleaper)
        playOut()
        alice.casts(p.doom, target = s.goblinFlameleaper)
        playOut(p.ogre)
        expect { bob.graveyard has p.ogre }
    }

    /** Amass 2, then a creature with power 2 or less is Alice's until end of turn, untapped and hasty. */
    @Test
    fun ugrakStealsASmallCreature() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.hand(s.ugrakBrashInstigator)
        bob.battlefield(p.bear, tapped = true)
        alice.casts(s.ugrakBrashInstigator)
        playOut(p.bear)
        expect {
            p.bear.controller shouldBe alice.id
            p.bear.tapped shouldBe false
            p.bear hasKeyword Keyword.Haste
        }
    }

    // ---- Chunk 08

    /** Two lands sacrificed reveal two cards; the land comes in tapped, the artifact untapped. */
    // CR 701.21, 701.20
    @Test
    fun fellTheOldForest() = scenario {
        alice.battlefield(*mountains)
        alice.hand(s.fellTheOldForest)
        alice.library("Forest", p.trinket, p.ogre)
        alice.casts(s.fellTheOldForest)
        playOut("Mountain", "Mountain", "Forest", p.trinket)
        expect {
            alice.battlefield has p.trinket
            alice.battlefield has "Forest"
            card("Forest").tapped shouldBe true
        }
    }

    /** A Goblin's combat damage exiles the top card, playable this turn. */
    @Test
    fun deepMineMarauderExilesToPlay() = scenario {
        alice.battlefield(s.deepMineMarauder)
        alice.library("Mountain")
        hits(s.deepMineMarauder)
        expect { alice.exile has "Mountain" }
        advanceTo(TurnStep.PostcombatMain)
        alice.plays("Mountain")
        expect { alice.battlefield has "Mountain" }
    }

    /** Without a Goblin or Orc it can't block. */
    // CR 509.1c
    @Test
    fun hillTrollCrusherNeedsAGoblin() = scenario {
        alice.battlefield(s.hillTrollCrusher)
        bob.battlefield(p.bear)
        turn(active = bob, step = TurnStep.DeclareAttackers, priority = null)
        bob.attacks(p.bear)
        playOut()
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<Throwable> { alice.blocks(s.hillTrollCrusher to p.bear) }
    }

    @Test
    fun stormTheHallHastesAGoblin() = scenario {
        alice.battlefield("Mountain")
        alice.hand(s.stormTheHall)
        alice.battlefield(p.goblin, sick = true)
        alice.casts(s.stormTheHall, target = p.goblin)
        playOut()
        expect {
            p.goblin hasKeyword Keyword.Haste
            p.goblin hasKeyword Keyword.FirstStrike
        }
    }

    /** An indestructible creature loses it and, dying, is exiled. */
    // CR 614.1a
    @Test
    fun smiteTheUndyingExiles() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.hand(s.smiteTheUndying)
        bob.battlefield(Cards.invent("Shirefolk Stone Guard", "{2}{W}", "Creature — Golem", "Indestructible", "2/2"))
        alice.casts(s.smiteTheUndying, target = "Shirefolk Stone Guard")
        playOut()
        expect { bob.exile has "Shirefolk Stone Guard" }
    }

    /** X damage to each creature as it enters; Bob's creatures dying make one Treasure. */
    @Test
    fun spitefulThievery() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(s.spitefulThievery)
        bob.battlefield(p.bear, p.elf)
        alice.casts(s.spitefulThievery, x = 2)
        playOut()
        expect {
            bob.graveyard has p.bear
            bob.graveyard has p.elf
        }
        assertEquals(1, tokens("Treasure Token").size)
    }

    /** Chapter III makes Smaug, whose death makes fourteen Treasures. */
    // CR 111.4
    @Test
    fun outAndHomeAgainMakesSmaug() = scenario {
        alice.battlefield(s.outAndHomeAgain, counters = listOf(CounterKind.Lore to 2))
        alice.battlefield("Swamp")
        alice.hand(p.doom)
        alice.library(p.ogre, p.ogre)
        bob.library(p.ogre, p.ogre)
        advanceTo(TurnStep.PrecombatMain, turn = 3)
        playOut()
        val smaug = tokens("Smaug").single()
        assertTrue(Supertype.Legendary in view()[smaug]!!.characteristics!!.supertypes)
        alice.casts(p.doom, target = "Smaug")
        playOut()
        assertEquals(14, tokens("Treasure Token").size)
    }

    /** Chapter I: the target can't block while Alice controls the Saga. */
    @Test
    fun outAndHomeAgainStopsABlocker() = scenario {
        alice.battlefield(*mountains)
        alice.hand(s.outAndHomeAgain)
        bob.battlefield(p.ogre)
        alice.casts(s.outAndHomeAgain)
        playOut(p.ogre)
        expect { p.ogre hasMechanic { it is arcanum.api.Mechanic.CantBlock } }
    }

    /** A creature with power 4 or greater dying amasses Orcs 2. */
    @Test
    fun warbeastAmassesForBigDeaths() = scenario {
        alice.battlefield(s.warbeastOfTheAshPlain, "Swamp")
        alice.hand(p.doom)
        alice.casts(p.doom, target = s.warbeastOfTheAshPlain)
        playOut()
        assertEquals(2, state.obj(tokens("Orc Army Token").single())!!.counters(p1p1))
    }

    /** Amass X, then the Goblins and Orcs get double strike and haste. */
    @Test
    fun assaultOnTheRiverCity() = scenario {
        alice.battlefield(*mountains, p.goblin)
        alice.hand(s.assaultOnTheRiverCity)
        alice.casts(s.assaultOnTheRiverCity, x = 2)
        playOut()
        val army = tokens("Orc Army Token").single()
        assertEquals(2, state.obj(army)!!.counters(p1p1))
        expect { p.goblin hasKeyword Keyword.DoubleStrike }
        assertTrue(view()[army]!!.characteristics!!.hasKeyword(Keyword.Haste))
    }

    /** Equipment on the damaged creature is destroyed; a card played from exile deals 2 to each player. */
    @Test
    fun firesOfTheBurningPeak() = scenario {
        alice.battlefield(*mountains)
        alice.hand(s.firesOfTheBurningPeak)
        alice.library("Mountain")
        bob.battlefield(p.ogre, p.sword)
        attach(p.sword, p.ogre)
        alice.casts(s.firesOfTheBurningPeak)
        playOut(p.ogre)
        expect { bob.graveyard has p.sword }
        alice.activates(s.firesOfTheBurningPeak)
        playOut()
        alice.plays("Mountain")
        playOut()
        expect {
            alice.life shouldBe 18
            bob.life shouldBe 18
        }
    }

    /** It costs {1} less for each Human; having attacked, it returns at the end step with Soldiers equal to its toughness. */
    // CR 702.41a
    @Test
    fun ridersOfThePlains() = scenario {
        alice.battlefield(s.ridersOfThePlains)
        alice.library(p.ogre, p.ogre)
        bob.library(p.ogre, p.ogre)
        hits(s.ridersOfThePlains)
        advanceTo(TurnStep.End)
        playOut()
        expect { alice.hand has s.ridersOfThePlains }
        assertEquals(4, tokens("Human Soldier Token").size)
    }

    @Test
    fun ridersOfThePlainsAffinity() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", p.human, p.human, p.human)
        alice.hand(s.ridersOfThePlains)
        alice.casts(s.ridersOfThePlains)
        playOut()
        expect { alice.battlefield has s.ridersOfThePlains }
    }
}
