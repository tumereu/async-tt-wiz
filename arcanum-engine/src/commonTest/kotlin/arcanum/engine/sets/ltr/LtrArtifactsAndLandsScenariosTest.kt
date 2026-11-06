package arcanum.engine.sets.ltr

import arcanum.api.Color
import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The Lord of the Rings cards of parser chunk 14 in play: artifacts and nonbasic lands. Trivial ones not played here: Inherited Parcel (the
 * Ring tempting and any-colour mana), Mirror of the Lady (an activation cost less by a count), Farmstead Scarecrow (mana once each turn), Great
 * Hall of the Tower (mana only for legendary spells), Mines of the Deep, Hidden Valley Haven, The Green Shire and Hillside Terrace (costs and
 * effects of shapes played elsewhere; entering tapped unless you control a legendary creature as Dark Tower Keep's shape in other sets).
 */
class LtrArtifactsAndLandsScenariosTest {
    private val s = LtrSet
    private val p = LtrPlain
    private val p1p1 = LtrPlain.p1p1
    private val burden = CounterKind.Named("burden")

    /** Cast, it gives Alice protection from everything: Bob's spell can't target her. */
    // CR 702.16k
    @Test
    fun rulingRingProtectsItsCaster() = scenario {
        alice.battlefield("Plains", "Plains", "Island", "Island")
        alice.hand(s.theRulingRing)
        bob.battlefield("Mountain")
        bob.hand(p.zap)
        alice.casts(s.theRulingRing)
        playOut()
        alice.passes()
        assertFailsWith<Throwable> { bob.casts(p.zap, target = "alice") }
    }

    /** Each activation adds a burden counter and draws one card per burden counter. */
    @Test
    fun rulingRingDrawsByBurdens() = scenario {
        alice.battlefield(s.theRulingRing, counters = listOf(burden to 1))
        alice.library(p.ogre, p.ogre, p.ogre)
        alice.activates(s.theRulingRing)
        playOut()
        expect {
            s.theRulingRing.counters(burden) shouldBe 2
            alice.hand.size shouldBe 2
        }
    }

    @Test
    fun rulingRingCostsLifeEachUpkeep() = scenario {
        alice.battlefield(s.theRulingRing, counters = listOf(burden to 3))
        aliceUpkeep()
        playOut()
        expect { alice.life shouldBe 17 }
    }

    /** A legendary creature equipped: the two Spirits enter tapped and attacking. */
    // CR 508.4
    @Test
    fun narsilSpiritsAttackWithALegend() = scenario {
        alice.battlefield(s.narsilFlameOfTheWest, p.legend)
        attach(s.narsilFlameOfTheWest, p.legend)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(p.legend)
        playOut("bob")
        val spirits = tokens("Spirit Token")
        assertEquals(2, spirits.size)
        assertTrue(spirits.all { id -> state.combat?.attackers.orEmpty().any { it.id == id } })
    }

    @Test
    fun narsilSpiritsStayHomeOtherwise() = scenario {
        alice.battlefield(s.narsilFlameOfTheWest, p.bear)
        attach(s.narsilFlameOfTheWest, p.bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(p.bear)
        playOut()
        val spirits = tokens("Spirit Token")
        assertEquals(2, spirits.size)
        assertTrue(spirits.all { id -> state.obj(id)!!.tapped && state.combat?.attackers.orEmpty().none { it.id == id } })
    }

    /** The creature blocking the equipped one loses flying. */
    // CR 509.3c
    @Test
    fun barrowDaggerStripsTheBlocker() = scenario {
        alice.battlefield(s.barrowDagger, p.bear)
        attach(s.barrowDagger, p.bear)
        bob.battlefield(p.hawk)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(p.bear)
        playOut()
        advanceTo(TurnStep.DeclareBlockers)
        bob.blocks(p.hawk to p.bear)
        playOut()
        expect { p.hawk lacksKeyword Keyword.Flying }
    }

    /** X is 2: only a creature with power 2 can be the target. */
    // CR 107.3a
    @Test
    fun entWaterBasinGrowsByPower() = scenario {
        alice.battlefield(s.entWaterBasin, p.bear, p.ogre, "Forest", "Forest")
        alice.activates(s.entWaterBasin, x = 2, target = p.bear)
        playOut()
        expect { p.bear.counters(p1p1) shouldBe 1 }
    }

    @Test
    fun entWaterBasinNeedsTheRightPower() = scenario {
        alice.battlefield(s.entWaterBasin, p.bear, p.ogre, "Forest", "Forest")
        assertFailsWith<Throwable> { alice.activates(s.entWaterBasin, x = 2, target = p.ogre) }
    }

    /** Four combat damage: a cheaper instant from hand is cast for free. */
    // CR 601.2
    @Test
    fun orcristCastsAnInstant() = scenario {
        alice.battlefield(s.orcrist, p.bear)
        attach(s.orcrist, p.bear)
        alice.graveyard(p.notes, p.notes)
        alice.hand(p.zap)
        hits(p.bear, picks = listOf(p.zap, "bob"))
        expect {
            bob.life shouldBe 14
            alice.graveyard has p.zap
        }
    }

    @Test
    fun hornOfTheStewardCountsHumans() = scenario {
        alice.battlefield(s.hornOfTheSteward, p.human, p.legend, "Plains", "Plains", "Plains")
        alice.activates(s.hornOfTheSteward)
        playOut()
        assertEquals(2, tokens("Human Soldier Token").size)
    }

    /** Two attackers: a creature card among the top five goes to hand. */
    @Test
    fun hornOfTheRidersFindsACreature() = scenario {
        alice.battlefield(s.hornOfTheRiders, p.bear, p.ogre)
        alice.library(p.notes, p.giant, p.notes, p.notes, p.notes, p.notes)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(p.bear, p.ogre)
        playOut(p.giant)
        expect { alice.hand has p.giant }
    }

    /** Sacrificed, it is shuffled back into Alice's library. */
    // CR 400.7
    @Test
    fun waybreadReturnsToTheLibrary() = scenario {
        alice.battlefield(s.waybread, "Forest", "Forest")
        alice.library(p.ogre)
        alice.activates(s.waybread)
        playOut()
        expect {
            alice.life shouldBe 23
            alice.library has s.waybread
        }
    }

    @Test
    fun mithrilShirtAttachesOnEntering() = scenario {
        alice.battlefield(p.legend, "Plains", "Plains", "Plains")
        alice.hand(s.mithrilShirt)
        alice.casts(s.mithrilShirt)
        playOut(p.legend)
        expect { p.legend hasKeyword Keyword.Indestructible }
    }

    /** Bob declines: Alice mills one card and Bob loses its mana value. */
    // CR 101.4
    @Test
    fun seeingStoneOpponentDeclines() = scenario {
        alice.battlefield(s.seeingStoneOfTheTower)
        alice.library(p.ogre, p.bear, p.bear)
        advanceTo(TurnStep.End)
        playOut("bob", may = false)
        expect {
            s.seeingStoneOfTheTower.counters(CounterKind.Named("influence")) shouldBe 1
            alice.graveyard.size shouldBe 1
            bob.life shouldBe 17
        }
    }

    @Test
    fun seeingStoneOpponentLetsHerDraw() = scenario {
        alice.battlefield(s.seeingStoneOfTheTower)
        alice.library(p.ogre, p.bear, p.bear)
        advanceTo(TurnStep.End)
        playOut("bob", may = true)
        expect {
            alice.hand.size shouldBe 1
            bob.life shouldBe 20
        }
    }

    /** The last card in hand cast: the next draw is two cards. */
    // CR 614.1a
    @Test
    fun phialDrawsTwoFromAnEmptyHand() = scenario {
        alice.battlefield(s.phialOfTheLady, "Island")
        alice.hand(p.notes)
        alice.library(p.ogre, p.ogre, p.ogre)
        alice.casts(p.notes)
        playOut()
        expect { alice.hand.size shouldBe 2 }
    }

    @Test
    fun phialDoublesLifeWhenLow() = scenario {
        alice.battlefield(s.phialOfTheLady, "Plains")
        alice.hand(p.heal)
        alice.life = 5
        alice.casts(p.heal)
        playOut()
        expect { alice.life shouldBe 9 }
    }

    /** Each combat it untaps the equipped creature; blocking an Orc, it strikes first. */
    // CR 702.7b
    @Test
    fun dartbladeUntapsAtCombat() = scenario {
        alice.battlefield(s.dartbladeTheGlintingDagger)
        alice.battlefield(p.bear, tapped = true)
        attach(s.dartbladeTheGlintingDagger, p.bear)
        advanceTo(TurnStep.BeginningOfCombat)
        playOut()
        expect { p.bear.tapped shouldBe false }
    }

    @Test
    fun dartbladeFirstStrikeAgainstOrcs() = scenario {
        alice.battlefield(s.dartbladeTheGlintingDagger, p.bear)
        attach(s.dartbladeTheGlintingDagger, p.bear)
        bob.battlefield(p.orc)
        turn(active = bob, step = TurnStep.DeclareAttackers, priority = null)
        bob.attacks(p.orc)
        playOut()
        advanceTo(TurnStep.DeclareBlockers)
        alice.blocks(p.bear to p.orc)
        expect { p.bear hasKeyword Keyword.FirstStrike }
    }

    /** Bob's creature dying is exiled instead. */
    // CR 614.1a
    @Test
    fun stoneOfTheOathExilesTheDead() = scenario {
        alice.battlefield(s.stoneOfTheOath, "Swamp")
        alice.hand(p.doom)
        bob.battlefield(p.bear)
        alice.casts(p.doom, target = p.bear)
        playOut()
        expect { bob.exile has p.bear }
    }

    /** Attacking alone: a card and 1 life; on Alice's turn it can't be blocked. */
    @Test
    fun bungosRingAttacksAlone() = scenario {
        alice.battlefield(s.bungosRing, p.bear)
        attach(s.bungosRing, p.bear)
        alice.library(p.ogre)
        bob.battlefield(p.ogre)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(p.bear)
        playOut()
        expect {
            alice.hand.size shouldBe 1
            alice.life shouldBe 19
        }
        // no block possible: the declaration passes on its own (CR 509.1)
        advanceTo(TurnStep.CombatDamage)
        playOut()
        expect { bob.life shouldBe 18 }
    }

    /** Bob controls a nonbasic land: the equipped creature can't be blocked. */
    // CR 702.14c
    @Test
    fun pathfindersBootsWalkNonbasicLands() = scenario {
        alice.battlefield(s.pathfindersBoots, p.bear)
        attach(s.pathfindersBoots, p.bear)
        bob.battlefield(p.ogre, p.wastes)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(p.bear)
        playOut()
        // no block possible: the declaration passes on its own (CR 509.1)
        advanceTo(TurnStep.CombatDamage)
        playOut()
        expect { bob.life shouldBe 18 }
    }

    @Test
    fun pathfindersBootsBlockedAmongBasics() = scenario {
        alice.battlefield(s.pathfindersBoots, p.bear)
        attach(s.pathfindersBoots, p.bear)
        bob.battlefield(p.ogre, "Plains")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(p.bear)
        playOut()
        advanceTo(TurnStep.DeclareBlockers)
        bob.blocks(p.ogre to p.bear)
        assertTrue(state.combat?.blockers.orEmpty().isNotEmpty())
    }

    /** Amassing only after a creature died this turn. */
    @Test
    fun darkTowerKeepAmassesAfterADeath() = scenario {
        alice.battlefield(s.darkTowerKeep, "Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(p.doom)
        bob.battlefield(p.bear)
        assertFailsWith<Throwable> { alice.activates(s.darkTowerKeep, ability = 1, x = 1) }
    }

    @Test
    fun darkTowerKeepAmasses() = scenario {
        alice.battlefield(s.darkTowerKeep, "Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(p.doom)
        bob.battlefield(p.bear)
        alice.casts(p.doom, target = p.bear, paying = listOf("Swamp"))
        playOut()
        alice.activates(s.darkTowerKeep, ability = 1, x = 1)
        playOut()
        assertEquals(1, state.obj(tokens("Orc Army Token").single())!!.counters(p1p1))
    }

    /** A white legendary creature card in the graveyard: white mana. */
    // CR 106.7
    @Test
    fun westernHavensMakeAGraveyardColour() = scenario {
        alice.battlefield(s.theWesternHavens)
        alice.graveyard(p.legend)
        alice.activates(s.theWesternHavens, ability = 1, color = Color.White)
        assertEquals(listOf(Color.White), state.player(alice.id).manaPool.map { it.color })
    }

    @Test
    fun westernHavensNoColourWithoutLegends() = scenario {
        alice.battlefield(s.theWesternHavens)
        alice.graveyard(p.bear)
        runCatching { alice.activates(s.theWesternHavens, ability = 1, color = Color.Green) }
        assertTrue(state.player(alice.id).manaPool.none { it.color == Color.Green })
    }

    /** It draws only once two creatures attacked this turn. */
    @Test
    fun whiteTowerCityNeedsTwoAttackers() = scenario {
        alice.battlefield(s.whiteTowerCity, "Plains", "Plains", p.bear)
        alice.library(p.ogre)
        assertFailsWith<Throwable> { alice.activates(s.whiteTowerCity, ability = 1) }
    }

    @Test
    fun whiteTowerCityDrawsAfterTwoAttack() = scenario {
        alice.battlefield(s.whiteTowerCity, "Plains", "Plains", p.bear, p.ogre)
        alice.library(p.giant)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(p.bear, p.ogre)
        playOut()
        alice.activates(s.whiteTowerCity, ability = 1)
        playOut()
        expect { alice.hand has p.giant }
    }

    /** Sacrificed with a legendary artifact: two chosen creatures survive, the rest are destroyed. */
    // CR 701.8
    @Test
    fun mountainOfFireSparesTwo() = scenario {
        alice.battlefield(s.mountainOfFire, s.mirrorOfTheLady, p.bear, p.ogre, "Swamp", "Mountain", "Swamp", "Mountain", "Swamp", "Mountain", "Swamp")
        bob.battlefield(p.giant, p.elf)
        alice.activates(s.mountainOfFire, ability = 2, choosing = listOf(s.mirrorOfTheLady))
        playOut(p.bear, p.giant)
        expect {
            alice.battlefield has p.bear
            bob.battlefield has p.giant
            alice.graveyard has p.ogre
            bob.graveyard has p.elf
            alice.graveyard has s.mountainOfFire
            alice.graveyard has s.mirrorOfTheLady
        }
    }
}
