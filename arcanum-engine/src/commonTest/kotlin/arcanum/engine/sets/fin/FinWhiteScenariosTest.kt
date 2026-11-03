package arcanum.engine.sets.fin

import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.fin.FinExtras.bear
import arcanum.engine.sets.fin.FinExtras.charm
import arcanum.engine.sets.fin.FinExtras.doom
import arcanum.engine.sets.fin.FinExtras.flier
import arcanum.engine.sets.fin.FinExtras.lance
import arcanum.engine.sets.fin.FinExtras.legend
import arcanum.engine.sets.fin.FinExtras.notes
import arcanum.engine.sets.fin.FinExtras.ogre
import arcanum.engine.sets.fin.FinExtras.p1p1
import arcanum.engine.sets.fin.FinExtras.trinket
import arcanum.engine.sets.fin.FinExtras.zap
import kotlin.test.Test

/**
 * Final Fantasy chunks 07 and 08 in play: the white cards and the white-blue legends: Equipment matters, life gain triggers, an until-leaves
 * exile (CR 610.3), giving a permanent away, End the turn (CR 724.1), player protection (CR 702.16b) and the other new shapes.
 * Stonehold Guard's dies token, The Light's Chosen, Moogle's Stand, Ironclad Walker and Skybound Moogle use tested token and counter
 * effects; Clawed Prowler's tap ability, Crested Cat's and Clockwork Infantry's artifact-count statics, Snowe Valiers' power and Ronan's
 * Inspiration's attacker pump are covered templates.
 */
class FinWhiteScenariosTest {
    private fun lands(land: String, n: Int) = Array(n) { land }

    // ---- chunk 07

    // CR 610.3: until this artifact leaves the battlefield
    @Test
    fun paleAuraciteExilesUntilItLeaves() = scenario {
        alice.battlefield(*lands("Plains", 4), "Swamp")
        alice.hand(FinSet.paleAuracite)
        bob.battlefield(ogre)
        alice.casts(FinSet.paleAuracite)
        resolveAll()
        targets(ogre)
        expect { ogre.zone shouldBe Zone.Exile }
    }

    @Test
    fun parcelMoogleFindsACheapArtifactInTheGraveyard() = scenario {
        libraries()
        alice.battlefield(*lands("Plains", 4))
        alice.graveyard(trinket)
        alice.hand(FinSet.parcelMoogle)
        alice.casts(FinSet.parcelMoogle)
        resolveAll()
        choose(trinket)
        expect { alice.hand has trinket }
    }

    @Test
    fun aeraGrowsOnLifeGainAndSharesCountersWhenItDies() = scenario {
        alice.battlefield(FinSet.aeraFlowerSeller, legend, "Swamp", "Plains", "Plains", "Plains", "Plains")
        alice.hand(FinSet.mendingLitany, doom)
        alice.casts(FinSet.mendingLitany, modes = listOf(1), target = legend, paying = listOf("Plains", "Plains"))
        resolveAll()
        expect { FinSet.aeraFlowerSeller.counters(p1p1) shouldBe 1 }
        alice.casts(doom, target = FinSet.aeraFlowerSeller)
        resolveAll()
        expect { legend.counters(p1p1) shouldBe 1 }
    }

    @Test
    fun legacyOfTheAirshipFlashbackPutsTheVehicleOntoTheBattlefield() = scenario {
        alice.library(FinSet.ironcladWalker, notes)
        alice.battlefield(*lands("Plains", 7))
        alice.graveyard(FinSet.legacyOfTheAirship)
        alice.casts(FinSet.legacyOfTheAirship)
        resolveAll()
        choose(FinSet.ironcladWalker)
        expect {
            FinSet.ironcladWalker.zone shouldBe Zone.Battlefield
            FinSet.legacyOfTheAirship.zone shouldBe Zone.Exile
        }
    }

    @Test
    fun zaneSacrificesToPassItsCountersAndEquipment() = scenario {
        alice.battlefield(FinSet.zaneValecourt, counters = listOf(p1p1 to 1))
        alice.battlefield(bear, lance, "Plains")
        attach(lance, FinSet.zaneValecourt)
        alice.activates(FinSet.zaneValecourt, target = bear)
        resolveAll()
        expect {
            bear.counters(p1p1) shouldBe 1
            bear hasKeyword Keyword.Indestructible
            bear.power shouldBe 4
        }
    }

    @Test
    fun zaneEntersWithACounter() = scenario {
        alice.battlefield("Plains")
        alice.hand(FinSet.zaneValecourt)
        alice.casts(FinSet.zaneValecourt)
        resolveAll()
        expect { FinSet.zaneValecourt.power shouldBe 1 }
    }

    // CR 603.12: "when you do"
    @Test
    fun armamentPeddlerPaysToAttach() = scenario {
        libraries()
        alice.battlefield(FinSet.armamentPeddler, bear, lance, "Plains")
        advanceTo(TurnStep.BeginningOfCombat)
        resolveAll()
        yes()
        pay()
        targets(bear, lance)
        expect { bear.power shouldBe 3 }
    }

    @Test
    fun rosalindCountersAndLifelink() = scenario {
        libraries()
        alice.battlefield(FinSet.rosalindResoluteHealer, bear)
        advanceTo(TurnStep.BeginningOfCombat)
        resolveAll()
        targets(bear)
        expect {
            bear.counters(p1p1) shouldBe 1
            bear hasKeyword Keyword.Lifelink
        }
    }

    @Test
    fun stilkinGivesAwayAPermanentAndDraws() = scenario {
        libraries()
        alice.battlefield(FinSet.stilkinMoogleTrader, trinket, "Plains", "Plains")
        alice.activates(FinSet.stilkinMoogleTrader, targets = listOf("bob", trinket))
        resolveAll()
        expect {
            trinket.controller shouldBe bob.id
            alice.hand.size shouldBe 1
        }
    }

    // CR 601.2f: cheaper if it targets a tapped creature
    @Test
    fun crystalsFateCostsLessForATappedCreature() = scenario {
        alice.battlefield(*lands("Plains", 3))
        alice.hand(FinSet.crystalsFate)
        bob.battlefield(ogre, tapped = true)
        alice.casts(FinSet.crystalsFate, target = ogre)
        resolveAll()
        expect { ogre.zone shouldBe Zone.Graveyard }
    }

    @Test
    fun gerraTielDrawsOnceWhenThingsDie() = scenario {
        libraries()
        alice.battlefield(FinSet.gerraTiel, bear, ogre, "Swamp", "Swamp")
        alice.hand(doom, doom)
        alice.casts(doom, target = bear)
        resolveAll()
        alice.casts(doom, target = ogre)
        resolveAll()
        expect { alice.hand.size shouldBe 1 }
    }

    @Test
    fun ambergaleReturnsAPermanentAndGrowsOnLandfall() = scenario {
        alice.battlefield(*lands("Plains", 2), charm)
        alice.hand(FinSet.ambergaleWhiteheart, "Plains")
        alice.casts(FinSet.ambergaleWhiteheart)
        resolveAll()
        choose(charm)
        alice.plays("Plains")
        resolveAll()
        expect {
            alice.hand has charm
            FinSet.ambergaleWhiteheart.power shouldBe 3
        }
    }

    // CR 614.1a: twice that much life
    @Test
    fun theGaleCrystalDoublesLifeGain() = scenario {
        alice.battlefield(FinSet.theGaleCrystal, "Plains", "Plains")
        alice.hand(FinSet.mendingLitany)
        alice.casts(FinSet.mendingLitany, modes = listOf(1), target = FinSet.theGaleCrystal)
        resolveAll()
        expect { alice.life shouldBe 26 }
    }

    @Test
    fun theGaleCrystalGivesFlyingAndLifelink() = scenario {
        alice.battlefield(FinSet.theGaleCrystal, bear, *lands("Plains", 6))
        alice.activates(FinSet.theGaleCrystal)
        resolveAll()
        expect {
            bear hasKeyword Keyword.Flying
            bear hasKeyword Keyword.Lifelink
        }
    }

    // CR 603.2d: an ability that triggers an additional time
    @Test
    fun claudeFindsEquipmentAndDoublesItsTriggers() = scenario {
        alice.library(FinSet.mendersCrook, notes)
        bob.library(notes)
        alice.battlefield("Plains", "Plains")
        alice.hand(FinSet.claudeSellswordOfTheSlums)
        alice.casts(FinSet.claudeSellswordOfTheSlums)
        resolveAll()
        choose(FinSet.mendersCrook)
        expect { alice.hand has FinSet.mendersCrook }
    }

    @Test
    fun claudeEquippedTriggersTwice() = scenario {
        libraries()
        alice.battlefield(FinSet.claudeSellswordOfTheSlums, FinSet.mendersCrook)
        attach(FinSet.mendersCrook, FinSet.claudeSellswordOfTheSlums)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(FinSet.claudeSellswordOfTheSlums)
        resolveAll()
        expect { alice.life shouldBe 22 }
    }

    // CR 724.1: end the turn
    @Test
    fun finalOblivionDestroysAndEndsTheTurn() = scenario {
        libraries()
        alice.battlefield(*lands("Plains", 5), bear, trinket)
        alice.hand(FinSet.finalOblivion)
        bob.battlefield(ogre)
        alice.casts(FinSet.finalOblivion)
        resolveAll()
        expect {
            bear.zone shouldBe Zone.Graveyard
            ogre.zone shouldBe Zone.Graveyard
            trinket.zone shouldBe Zone.Graveyard
            FinSet.finalOblivion.zone shouldBe Zone.Exile
            state.turn.active shouldBe bob.id
        }
    }

    @Test
    fun bellatrixAttachesAllEquipment() = scenario {
        libraries()
        alice.battlefield(FinSet.bellatrixLoyalGeneral, bear, lance, FinSet.mendersCrook)
        advanceTo(TurnStep.BeginningOfCombat)
        resolveAll()
        targets(bear)
        if (pending is arcanum.engine.decision.Decision.May) yes()
        choose(lance, FinSet.mendersCrook)
        expect { bear.power shouldBe 4 }
    }

    // ---- chunk 08

    @Test
    fun stoneholdGuardLeavesAHero() = scenario {
        alice.battlefield(FinSet.stoneholdGuard, "Swamp")
        alice.hand(doom)
        alice.casts(doom, target = FinSet.stoneholdGuard)
        resolveAll()
        expect { alice.battlefield has "Hero Token" }
    }

    @Test
    fun edgeOfDawnCountsCreaturesAndEquipment() = scenario {
        alice.battlefield(bear, flier, lance, "Plains", "Plains")
        alice.hand(FinSet.edgeOfDawn)
        bob.battlefield(ogre)
        alice.casts(FinSet.edgeOfDawn, target = ogre)
        resolveAll()
        expect { ogre.zone shouldBe Zone.Graveyard }
    }

    @Test
    fun adelbaldGrowsWithEquipment() = scenario {
        alice.battlefield(FinSet.adelbaldSteadfast, lance, FinSet.mendersCrook)
        expect { FinSet.adelbaldSteadfast.power shouldBe 4 }
    }

    @Test
    fun hollisMillsByLifeGained() = scenario {
        libraries()
        alice.battlefield(FinSet.hollisEstheim)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(FinSet.hollisEstheim)
        resolveAll()
        advanceTo(TurnStep.End)
        resolveAll()
        expect { bob.graveyard.size shouldBe 2 }
    }

    @Test
    fun ashaFindsAnArtifact() = scenario {
        alice.library(notes, trinket, notes, notes, notes, notes)
        bob.library(notes)
        alice.battlefield(FinSet.ashaPrincessOfTheSands)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(FinSet.ashaPrincessOfTheSands)
        resolveAll()
        choose(trinket)
        expect { alice.hand has trinket }
    }

    @Test
    fun theLightsChosenMakesHeroesAndCountersAll() = scenario {
        alice.battlefield(*lands("Plains", 7), bear)
        alice.hand(FinSet.theLightsChosen)
        alice.casts(FinSet.theLightsChosen)
        resolveAll()
        expect {
            alice.battlefield.names.count { it == "Hero Token" } shouldBe 4
            bear.counters(p1p1) shouldBe 1
        }
    }

    @Test
    fun mooglesStandMakesAMoogleForEachCreature() = scenario {
        alice.battlefield(*lands("Plains", 5), bear, ogre)
        alice.hand(FinSet.mooglesStand)
        alice.casts(FinSet.mooglesStand)
        resolveAll()
        expect {
            alice.battlefield.names.count { it == "Moogle Token" } shouldBe 2
            bear hasKeyword Keyword.Indestructible
        }
    }

    @Test
    fun neverAloneGivesMoreWithThreeCreatures() = scenario {
        alice.battlefield("Plains", bear, ogre, flier)
        alice.hand(FinSet.neverAlone)
        alice.casts(FinSet.neverAlone, target = bear)
        resolveAll()
        expect { bear.power shouldBe 6 }
    }

    @Test
    fun crestedCatWithTwoArtifacts() = scenario {
        alice.battlefield(FinSet.crestedCat, trinket, lance)
        expect { FinSet.crestedCat.power shouldBe 3 }
    }

    @Test
    fun snoweValiersCountsCreatures() = scenario {
        alice.battlefield(FinSet.snoweValiers, bear, ogre)
        expect { FinSet.snoweValiers.power shouldBe 3 }
    }

    @Test
    fun cidelPumpsArtifactCreaturesAndHeroesPerArtificer() = scenario {
        alice.battlefield(FinSet.cidelTimelessTinker, FinSet.clockworkInfantry, FinSet.armamentPeddler)
        alice.graveyard(FinSet.cidelTimelessTinker)
        // Cidel and Armament Peddler on the battlefield, Cidel in the graveyard: +3/+3
        expect { FinSet.clockworkInfantry.toughness shouldBe 4 }
    }

    @Test
    fun ronansInspirationPumpsAttackers() = scenario {
        libraries()
        alice.battlefield(bear, "Plains", "Plains", "Plains")
        alice.hand(FinSet.ronansInspiration)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        resolveAll()
        alice.casts(FinSet.ronansInspiration)
        resolveAll()
        expect { bear.power shouldBe 4 }
    }

    @Test
    fun tydesGrowsOnArtifactsAndTapsOnAttack() = scenario {
        libraries()
        alice.battlefield(FinSet.tydesStarStriker, "Plains")
        alice.hand(trinket)
        bob.battlefield(ogre)
        alice.casts(trinket)
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(FinSet.tydesStarStriker)
        resolveAll()
        expect {
            FinSet.tydesStarStriker.counters(p1p1) shouldBe 1
            ogre.tapped shouldBe true
        }
    }

    @Test
    fun clockworkInfantryFetchesAnother() = scenario {
        alice.library(FinSet.clockworkInfantry, notes)
        alice.battlefield(FinSet.clockworkInfantry, "Plains", "Plains", "Plains")
        alice.activates(FinSet.clockworkInfantry)
        resolveAll()
        choose(FinSet.clockworkInfantry)
        expect { alice.battlefield.names.count { it == FinSet.clockworkInfantry } shouldBe 2 }
    }

    @Test
    fun minaCountersClericsOnLifeGain() = scenario {
        alice.battlefield(FinSet.minaWhiteMage, FinSet.mendersCrook, bear, "Plains", "Plains")
        attach(FinSet.mendersCrook, bear)
        alice.hand(FinSet.mendingLitany)
        alice.casts(FinSet.mendingLitany, modes = listOf(1), target = bear)
        resolveAll()
        expect {
            FinSet.minaWhiteMage.counters(p1p1) shouldBe 1
            bear.counters(p1p1) shouldBe 1
        }
    }

    // CR 702.16b, 702.16e: a player with protection can't be targeted or dealt damage by what they're protected from
    @Test
    fun virtueAbsoluteProtectsYouFromOpponents() = scenario {
        alice.battlefield(FinSet.virtueAbsolute)
        bob.battlefield("Mountain")
        bob.hand(zap)
        bob.battlefield(ogre)
        alice.library(notes)
        bob.library(notes)
        turn(active = bob)
        bob.casts(zap)
        val offered = (pending as arcanum.engine.decision.Decision.Targets).options
        kotlin.test.assertFalse(arcanum.engine.state.TargetRef.Player(alice.id) in offered)
        targets(ogre)
        pay()
        advanceTo(TurnStep.DeclareAttackers)
        bob.attacks(ogre, defender = "alice")
        allPass()
        alice.blocks()
        advanceTo(TurnStep.EndOfCombat)
        expect { alice.life shouldBe 20 }
    }
}
