package arcanum.engine.sets.ltr

import arcanum.api.Color
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
 * The Lord of the Rings cards of parser chunks 09–10 in play: the green cards. Trivial ones not played here: Old Singer's Song (a pump and
 * the Ring tempting), Wrathful Huorn, Still-Lake Guardian (the Ring tempting by triggers), Woodland Elf Guide, Golden Wood Lookout
 * (scry), Kindly Ent and Gift of Golden Strands (Food, an Aura's pump, forestcycling as plainscycling), Many Farewells and Restore the
 * Shire (a search or return and a Food), Gloomwood Spider (a keyword granted by an attack trigger), Volley of Arrows (a destroy),
 * Woodwose Pathfinder (a mana ability and a pump).
 */
class LtrGreenScenariosTest {
    private val s = LtrSet
    private val p = LtrPlain
    private val p1p1 = LtrPlain.p1p1
    private val forests = arrayOf("Forest", "Forest", "Forest", "Forest", "Forest", "Forest", "Forest", "Forest")
    private val treefolk = Cards.invent("Shirefolk Treefolk", "{2}{G}", "Creature — Treefolk", "", "2/3")
    private val halfling = Cards.invent("Shirefolk Halfling", "{W}", "Creature — Halfling", "", "1/1")

    // ---- Chunk 09

    @Test
    fun hillHolePorterGrowsWithLegends() = scenario {
        alice.battlefield(s.hillHolePorter, p.legend, p.bigLegend)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(s.hillHolePorter)
        playOut()
        expect { s.hillHolePorter.power shouldBe 6 }
    }

    /** A Food as it enters and another as it leaves. */
    // CR 603.6
    @Test
    fun riverbankFarmerFoodTwice() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Swamp")
        alice.hand(s.riverbankFarmer, p.doom)
        alice.casts(s.riverbankFarmer)
        playOut()
        alice.casts(p.doom, target = s.riverbankFarmer)
        playOut()
        assertEquals(2, tokens("Food Token").size)
    }

    /** Scrying two cards makes it +2/+2. */
    // CR 701.22
    @Test
    fun amrothGrowsByCardsScried() = scenario {
        alice.battlefield(s.amrothTheWise, *forests)
        alice.hand(s.woodlandElfGuide)
        alice.library(p.ogre, p.ogre, p.ogre)
        alice.casts(s.woodlandElfGuide)
        playOut()
        expect { s.amrothTheWise.power shouldBe 5 }
    }

    /** Only the first scry of a turn puts a counter on it. */
    @Test
    fun wanderingCompanyElvesOncePerTurn() = scenario {
        alice.battlefield(s.wanderingCompanyElves, *forests)
        alice.hand(s.woodlandElfGuide, s.woodlandElfGuide)
        alice.library(p.ogre, p.ogre, p.ogre, p.ogre)
        alice.casts(s.woodlandElfGuide)
        playOut()
        alice.casts(s.woodlandElfGuide)
        playOut()
        expect { s.wanderingCompanyElves.counters(p1p1) shouldBe 1 }
    }

    /** Its coloured mana pays for a legendary spell only, which then can't be countered. */
    // CR 106.6
    @Test
    fun merryHeartedHalflingManaForLegends() = scenario {
        alice.battlefield(s.merryHeartedHalfling, "Plains")
        alice.hand(p.legend, p.human)
        assertFailsWith<Throwable> { alice.casts(p.human, paying = listOf(s.merryHeartedHalfling + ":W")) }
    }

    @Test
    fun merryHeartedHalflingLegendCantBeCountered() = scenario {
        alice.battlefield(s.merryHeartedHalfling, "Plains")
        alice.hand(p.legend)
        bob.battlefield("Island", "Island")
        bob.hand(p.counterspell)
        alice.casts(p.legend, paying = listOf(s.merryHeartedHalfling + ":W", "Plains"))
        alice.passes()
        bob.casts(p.counterspell, target = p.legend)
        playOut()
        expect { alice.battlefield has p.legend }
    }

    /** A land entering tempts only while Alice has no Ring-bearer. */
    @Test
    fun northernRangersTemptWithoutABearer() = scenario {
        alice.battlefield(s.northernRangers)
        alice.hand("Forest")
        alice.plays("Forest")
        playOut(s.northernRangers)
        assertEquals(1, temptations())
    }

    /** Creature spells from the top of the library; creatures tap for any colour. */
    // CR 601.3
    @Test
    fun elvenChoirCastsFromTheTop() = scenario {
        alice.battlefield(s.elvenChoir, p.bear, p.elf, "Forest")
        alice.library(p.ogre, p.giant)
        alice.casts(p.ogre, way = permitted(p.ogre), paying = listOf(p.bear + ":R", p.elf + ":R", "Forest"))
        playOut()
        expect { alice.battlefield has p.ogre }
    }

    /** A revealed creature card draws a card. */
    @Test
    fun elvenForesightDrawsACreature() = scenario {
        alice.battlefield("Forest")
        alice.hand(s.elvenForesight)
        alice.library(p.ogre, "Forest", "Forest", "Forest")
        alice.casts(s.elvenForesight)
        playOut()
        expect { alice.hand has p.ogre }
    }

    /** With a creature of power 4 or greater, three basic lands. */
    @Test
    fun entishRenewalFindsThreeWithABigCreature() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", p.giant)
        alice.hand(s.entishRenewal)
        alice.library("Forest", "Forest", "Forest", "Forest")
        alice.casts(s.entishRenewal)
        playOut("Forest", "Forest", "Forest", "Forest")
        expect { alice.battlefield.size shouldBe 6 }
    }

    /** A big creature gets a counter, +1/+1, and fights. */
    // CR 701.14
    @Test
    fun entsWrathFights() = scenario {
        alice.battlefield("Forest", "Forest", p.giant)
        alice.hand(s.entsWrath)
        bob.battlefield(troll())
        alice.casts(s.entsWrath, targets = listOf(p.giant, "Shirefolk Troll"))
        playOut()
        expect {
            p.giant.counters(p1p1) shouldBe 1
            bob.graveyard has "Shirefolk Troll"
        }
    }

    /** Chapter III: the creature fights, and its death draws two. */
    @Test
    fun fallOfTheElvenKingDiesAndDraws() = scenario {
        alice.battlefield(s.fallOfTheElvenKing, counters = listOf(CounterKind.Lore to 2))
        alice.battlefield(p.elf)
        alice.library(p.ogre, p.ogre, p.ogre, p.ogre)
        bob.battlefield(p.ogre)
        bob.library(p.ogre, p.ogre)
        advanceTo(TurnStep.PrecombatMain, turn = 3)
        playOut(p.elf, p.ogre)
        expect {
            alice.graveyard has p.elf
            alice.hand.size shouldBe 3
        }
    }

    /** Two attacking Treefolk add four green mana, kept through the step. */
    // CR 500.4
    @Test
    fun bregaladAddsGreenForTreefolk() = scenario {
        alice.battlefield(s.bregaladTreeShepherd, treefolk)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(s.bregaladTreeShepherd, treefolk)
        playOut()
        assertEquals(4, alice.pool.size)
        advanceTo(TurnStep.CombatDamage)
        assertEquals(4, alice.pool.size)
        expect { treefolk hasKeyword Keyword.Vigilance }
    }

    /** With flash, it attaches to a creature and untaps it. */
    @Test
    fun woodlandElfBowAttachesAndUntaps() = scenario {
        alice.battlefield("Forest", "Forest", "Forest")
        alice.battlefield(p.bear, tapped = true)
        alice.hand(s.woodlandElfBow)
        alice.casts(s.woodlandElfBow)
        playOut(p.bear)
        expect {
            p.bear.tapped shouldBe false
            p.bear.toughness shouldBe 4
            p.bear hasKeyword Keyword.Reach
        }
    }

    /** A scry: +1/+1 and the chosen mode (can't be blocked by more than one). */
    @Test
    fun erestorPumpsWithAMode() = scenario {
        alice.battlefield(s.erestorDauntlessRescuer, *forests)
        alice.hand(s.woodlandElfGuide)
        alice.library(p.ogre, p.ogre)
        alice.casts(s.woodlandElfGuide)
        playOut()
        modes(1)
        playOut()
        expect {
            s.erestorDauntlessRescuer.power shouldBe 4
            s.erestorDauntlessRescuer hasMechanic { it is arcanum.api.Mechanic.MaximumBlockers }
        }
    }

    /** It draws by the greatest toughness and puts creature cards onto the battlefield. */
    @Test
    fun finalMarchOfTheEnts() = scenario {
        alice.battlefield(*forests, p.ogre)
        alice.hand(s.finalMarchOfTheEnts, p.giant)
        alice.library(p.bear, p.bear, p.bear, p.bear)
        alice.casts(s.finalMarchOfTheEnts)
        playOut(p.giant, p.bear)
        expect {
            alice.battlefield has p.giant
            alice.battlefield has p.bear
        }
    }

    /** A spell targeting it gives a counter; a spell targeting Bob's creature makes it shoot. */
    // CR 601.2c
    @Test
    fun haldirShootsWhenTargetingTheirs() = scenario {
        alice.battlefield(s.haldirMasterArcher, "Mountain", "Forest")
        alice.hand(p.ping, p.growth)
        bob.battlefield(p.bear)
        alice.casts(p.growth, target = s.haldirMasterArcher)
        playOut()
        expect { s.haldirMasterArcher.counters(p1p1) shouldBe 2 }
        alice.casts(p.ping, target = p.bear)
        playOut(p.bear)
        expect { bob.graveyard has p.bear }
    }

    // ---- Chunk 10

    /** Chapter I notes Bear; the next Bear spell this turn enters with a counter. */
    // CR 603.7
    @Test
    fun longRollOfTheEnts() = scenario {
        alice.battlefield("Forest", "Forest", "Forest")
        alice.hand(s.longRollOfTheEnts, p.bear)
        alice.casts(s.longRollOfTheEnts)
        allPass()
        playOut()
        val d = decision<Decision.ChooseSubtype>()
        assertEquals(alice.id, d.player)
        answer(Answer.SubtypeChoice(Subtype("Bear")))
        playOut()
        alice.casts(p.bear)
        playOut()
        expect { p.bear.counters(p1p1) shouldBe 1 }
    }

    /** Halflings attacking make a Food. */
    @Test
    fun fredegarFeedsAttackers() = scenario {
        alice.battlefield(s.fredegarTheSteady, halfling)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(halfling)
        playOut()
        assertEquals(1, tokens("Food Token").size)
    }

    @Test
    fun mushroomHoundsEatFood() = scenario {
        alice.battlefield(s.mushroomHounds, "Forest")
        alice.hand(p.picnic)
        alice.casts(p.picnic)
        playOut()
        alice.activates(s.mushroomHounds, choosing = listOf("Food Token"))
        playOut()
        expect {
            s.mushroomHounds.counters(p1p1) shouldBe 1
            s.mushroomHounds hasKeyword Keyword.Vigilance
        }
    }

    /** Each token creation comes with a Food. */
    // CR 614.1a
    @Test
    fun paladinTookAddsFood() = scenario {
        alice.battlefield(s.paladinTook, "Mountain", "Mountain")
        alice.hand(s.rallyAtTheDeepKeep)
        alice.casts(s.rallyAtTheDeepKeep)
        playOut()
        assertEquals(2, tokens("Human Soldier Token").size)
        assertEquals(1, tokens("Food Token").size)
    }

    /** Sacrificing a Food makes it +4/+4; otherwise +2/+2. */
    @Test
    fun halflingsBraveryWithFood() = scenario {
        alice.battlefield("Forest", "Forest", p.bear)
        alice.hand(p.picnic, s.halflingsBravery)
        alice.casts(p.picnic)
        playOut()
        alice.casts(s.halflingsBravery, target = p.bear)
        playOut("Food Token")
        expect { p.bear.power shouldBe 6 }
    }

    @Test
    fun leaflockPumpsOnTreefolk() = scenario {
        alice.battlefield(s.leaflockUpstartEnt, p.bear, "Forest", "Forest", "Forest")
        alice.hand(treefolk)
        alice.casts(treefolk)
        playOut(p.bear)
        expect {
            p.bear.power shouldBe 4
            p.bear hasKeyword Keyword.Trample
        }
    }

    /** A creature entering looks at cards by its mana value; a creature card sharing no type goes to hand. */
    // CR 700.8
    @Test
    fun aiwendilFindsANewKind() = scenario {
        alice.battlefield(s.aiwendilTheBrown, "Forest", "Forest", "Forest")
        alice.hand(p.bear)
        alice.library(troll(), p.elf, p.ogre, p.ogre)
        alice.casts(p.bear)
        playOut("Shirefolk Troll")
        expect { alice.hand has "Shirefolk Troll" }
    }

    /** It tempts, then reveals until two lands (two legends), putting them onto the battlefield tapped. */
    @Test
    fun theRingGoesEast() = scenario {
        alice.battlefield(p.legend, p.bigLegend, *forests)
        alice.hand(s.theRingGoesEast)
        alice.library(p.ogre, "Forest", p.bear, "Forest", p.elf)
        alice.casts(s.theRingGoesEast)
        playOut(p.legend)
        expect { alice.battlefield.size shouldBe 12 }
    }

    /** A permanent having left this turn: a counter at the end step. */
    @Test
    fun shortcutToTheFerry() = scenario {
        alice.battlefield(s.shortcutToTheFerry, p.bear, "Swamp")
        alice.hand(p.doom)
        alice.battlefield(p.elf)
        alice.casts(p.doom, target = p.elf)
        playOut()
        advanceTo(TurnStep.End)
        playOut(p.bear)
        expect { p.bear.counters(p1p1) shouldBe 1 }
    }

    @Test
    fun roastTheConeys() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", p.ogre)
        alice.hand(s.roastTheConeys)
        bob.battlefield(p.bear)
        alice.casts(s.roastTheConeys, targets = listOf(p.ogre, p.bear))
        playOut()
        expect { bob.graveyard has p.bear }
        assertEquals(1, tokens("Food Token").size)
    }

    /** Having sacrificed a Food this turn, the end step fetches a basic land. */
    @Test
    fun rosamundFetchesAfterAFood() = scenario {
        alice.battlefield(s.rosamundGardner, *forests)
        alice.library("Forest", p.ogre)
        alice.hand(p.picnic)
        alice.casts(p.picnic)
        playOut()
        alice.activates("Food Token")
        playOut()
        advanceTo(TurnStep.End)
        playOut("Forest")
        expect { alice.battlefield.size shouldBe 10 }
    }

    /** Entering, the Treasure mode. */
    @Test
    fun nimrodelGivesATreasure() = scenario {
        alice.battlefield(*forests)
        alice.hand(s.nimrodelGiftGiver)
        alice.casts(s.nimrodelGiftGiver)
        allPass()
        modes(2)
        playOut()
        assertEquals(1, tokens("Treasure Token").size)
    }

    /** An Elf gets +X/+X and trample, X the Forests. */
    @Test
    fun greenwoodChanneler() = scenario {
        alice.battlefield(s.greenwoodChanneler, p.elf, "Forest", "Forest", "Forest")
        advanceTo(TurnStep.BeginningOfCombat)
        playOut(p.elf)
        expect {
            p.elf.power shouldBe 4
            p.elf hasKeyword Keyword.Trample
        }
    }

    private fun troll() = Cards.invent("Shirefolk Troll", "{3}{G}", "Creature — Troll", "", "4/4")
    @Suppress("unused") private val unused = listOf(Color.Green, Zone.Hand)
}
