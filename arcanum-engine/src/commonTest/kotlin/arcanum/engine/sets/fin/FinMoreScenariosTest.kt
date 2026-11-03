package arcanum.engine.sets.fin

import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.input.CastWay
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.fin.FinExtras.bear
import arcanum.engine.sets.fin.FinExtras.charm
import arcanum.engine.sets.fin.FinExtras.doom
import arcanum.engine.sets.fin.FinExtras.flier
import arcanum.engine.sets.fin.FinExtras.giant
import arcanum.engine.sets.fin.FinExtras.lance
import arcanum.engine.sets.fin.FinExtras.notes
import arcanum.engine.sets.fin.FinExtras.ogre
import arcanum.engine.sets.fin.FinExtras.p1p1
import arcanum.engine.sets.fin.FinExtras.trinket
import arcanum.engine.sets.fin.FinExtras.zap
import kotlin.test.Test

/**
 * Final Fantasy chunks 16 and 17 in play: the remaining black, red, green, gold and colorless cards, the Town Adventures (CR 715) and the
 * Town lands. Templates already played elsewhere: Treacherous Envoy's Plot (kicker like Bird Kick), Saszs Chick, Spiny Plant and Runner
 * Racetrack (landfall), Salvage Crew (Gerra Tiel's death trigger shape), Manic Laughter and Runner Greens (flashback), Burrow Scout, Meadow
 * Rexaur, Strider Shot, Keep Fighting, Villain's Demise, Cohort Reinforcements, Many-Eyed Ooze, Great Toad (Stinger Sentry's lands), Ahriel
 * Eye, Skyship Crash, Commune with Otters (Asha's look), Lorne Coleman, Lunar Pandora, World Atlas, Severin, Judge Magistrate Gavran, Ring
 * of the Kings, Traveler's Rest, Capital Citadel and the single-colour Towns' mana.
 */
class FinMoreScenariosTest {
    private fun lands(land: String, n: Int) = Array(n) { land }

    /** The way to cast the Adventure half ([face] 1) of an adventurer card (CR 715.3). */
    private val adventure = CastWay(face = 1)

    // ---- chunk 16

    @Test
    fun catfishTraderPaysAndSurveils() = scenario {
        libraries()
        alice.battlefield(*lands("Swamp", 4), trinket)
        alice.hand(FinSet.catfishTrader)
        alice.casts(FinSet.catfishTrader)
        resolveAll()
        expect {
            alice.life shouldBe 19
            alice.battlefield has "Treasure Token"
        }
    }

    @Test
    fun aWorldRebornMakesPermanentsTapForMana() = scenario {
        alice.battlefield(FinSet.aWorldReborn, trinket)
        alice.hand(zap)
        alice.casts(zap, target = "bob", paying = listOf("$trinket:R"))
        resolveAll()
        expect { bob.life shouldBe 18 }
    }

    @Test
    fun treacherousEnvoysPlotKickedShrinksMore() = scenario {
        alice.battlefield("Swamp", "Swamp", trinket)
        alice.hand(FinSet.treacherousEnvoysPlot)
        bob.battlefield(giant)
        alice.casts(FinSet.treacherousEnvoysPlot, target = giant, kicked = 1, choosing = listOf(trinket))
        resolveAll()
        expect { giant.zone shouldBe Zone.Graveyard }
    }

    @Test
    fun runnerRacetrackMakesBirdsOnLandfall() = scenario {
        alice.battlefield(FinSet.runnerRacetrack)
        alice.hand("Forest")
        alice.plays("Forest")
        resolveAll()
        expect { card("Bird Token").view.characteristics!!.power shouldBe 2 }
    }

    // CR 301.5c: attach as it enters (flash)
    @Test
    fun reefSwordAttachesAndGivesFirstStrike() = scenario {
        alice.battlefield(bear, "Mountain")
        alice.hand(FinSet.reefSword)
        alice.casts(FinSet.reefSword)
        resolveAll()
        targets(bear)
        expect {
            bear.power shouldBe 3
            bear hasKeyword Keyword.FirstStrike
        }
    }

    @Test
    fun salvageCrewDrainsWhenThingsDie() = scenario {
        alice.battlefield(FinSet.salvageCrew, bear, "Swamp")
        alice.hand(doom)
        alice.casts(doom, target = bear)
        resolveAll()
        targets("bob")
        expect { bob.life shouldBe 19 }
    }

    @Test
    fun manicLaughterDiscardsToDraw() = scenario {
        libraries()
        alice.battlefield(*lands("Mountain", 3))
        alice.hand(FinSet.manicLaughter, ogre)
        alice.casts(FinSet.manicLaughter, choosing = listOf(ogre))
        resolveAll()
        expect { alice.hand.size shouldBe 2 }
    }

    @Test
    fun shamblingHuskReturnsForB() = scenario {
        alice.graveyard(FinSet.shamblingHusk)
        alice.battlefield("Mountain", "Swamp")
        alice.hand(zap)
        alice.casts(zap, target = "bob", paying = listOf("Mountain"))
        resolveAll()
        yes()
        pay()
        expect { alice.hand has FinSet.shamblingHusk }
    }

    @Test
    fun callTheRidgeRunnerFetchesAMountainAndABird() = scenario {
        alice.library("Mountain", notes)
        alice.battlefield(*lands("Mountain", 4))
        alice.hand(FinSet.callTheRidgeRunner)
        alice.casts(FinSet.callTheRidgeRunner)
        resolveAll()
        choose("Mountain")
        expect {
            alice.hand.size shouldBe 1
            alice.battlefield has "Bird Token"
        }
    }

    @Test
    fun spinyPlantPingsOnLandfall() = scenario {
        alice.battlefield(FinSet.spinyPlant)
        alice.hand("Forest")
        alice.plays("Forest")
        resolveAll()
        expect { bob.life shouldBe 19 }
    }

    @Test
    fun dunesWormDestroysALandAndItsControllerSearches() = scenario {
        bob.library("Forest", notes)
        alice.battlefield(*lands("Mountain", 5))
        alice.hand(FinSet.dunesWorm)
        bob.battlefield("Island")
        alice.casts(FinSet.dunesWorm)
        resolveAll()
        targets("Island")
        yes()
        choose("Forest")
        expect {
            card("Island").zone shouldBe Zone.Graveyard
            card("Forest").zone shouldBe Zone.Battlefield
        }
    }

    @Test
    fun crushingGravityKillsAnything() = scenario {
        alice.battlefield(*lands("Swamp", 3))
        alice.hand(FinSet.crushingGravity)
        bob.battlefield(giant)
        alice.casts(FinSet.crushingGravity, target = giant)
        resolveAll()
        expect { giant.zone shouldBe Zone.Graveyard }
    }

    @Test
    fun shadowConfidantDrawsAndPays() = scenario {
        alice.library(ogre, notes)
        bob.library(notes)
        alice.battlefield(FinSet.shadowConfidant)
        turn(step = TurnStep.Untap)
        advanceTo(TurnStep.Upkeep)
        resolveAll()
        expect {
            alice.hand has ogre
            alice.life shouldBe 17
        }
    }

    @Test
    fun runnerCometBurnsAndMakesABird() = scenario {
        alice.battlefield(*lands("Mountain", 5))
        alice.hand(FinSet.runnerComet)
        alice.casts(FinSet.runnerComet, target = "bob", x = 3)
        resolveAll()
        expect {
            bob.life shouldBe 17
            alice.battlefield has "Bird Token"
        }
    }

    @Test
    fun colossalCactusGetsHugeOnAttack() = scenario {
        libraries()
        alice.battlefield(FinSet.colossalCactus)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(FinSet.colossalCactus)
        resolveAll()
        expect { FinSet.colossalCactus.power shouldBe 10000 }
    }

    // CR 506.5: attacks alone
    @Test
    fun saiferGivesALoneAttackerDoubleStrikeAndRecasts() = scenario {
        alice.library(notes)
        bob.library(notes)
        alice.battlefield(FinSet.saiferAlmase)
        alice.graveyard(zap)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(FinSet.saiferAlmase)
        resolveAll()
        expect { FinSet.saiferAlmase hasKeyword Keyword.DoubleStrike }
        advanceTo(TurnStep.CombatDamage)
        resolveAll()
        targets(zap)
        yes()
        targets("bob")
        resolveAll()
        while (pending is arcanum.engine.decision.Decision.Targets) targets(zap)
        while (pending is arcanum.engine.decision.Decision.May) yes(false)
        expect { zap.zone shouldBe Zone.Exile }
    }

    @Test
    fun rancorousRevelationKeepsOne() = scenario {
        alice.library(ogre, bear, giant, notes)
        alice.battlefield("Swamp", "Swamp")
        alice.hand(FinSet.rancorousRevelation)
        alice.casts(FinSet.rancorousRevelation)
        resolveAll()
        choose(ogre)
        expect {
            alice.hand has ogre
            alice.graveyard has bear
            alice.graveyard has giant
        }
    }

    @Test
    fun theBlazeCrystalHastesAndCopies() = scenario {
        libraries()
        alice.battlefield(FinSet.theBlazeCrystal, ogre, *lands("Mountain", 6))
        alice.activates(FinSet.theBlazeCrystal, target = ogre)
        resolveAll()
        expect {
            alice.battlefield.names.count { it == ogre } shouldBe 2
            ogre hasKeyword Keyword.Haste
        }
        advanceTo(TurnStep.End)
        resolveAll()
        expect { alice.battlefield.names.count { it == ogre } shouldBe 1 }
    }

    @Test
    fun villageAflameFromTheGraveyardRefillsTheHand() = scenario {
        alice.library(notes, notes, notes, notes)
        alice.battlefield(giant, *lands("Mountain", 7))
        alice.graveyard(FinSet.villageAflame)
        alice.hand(ogre)
        bob.battlefield(bear)
        alice.casts(FinSet.villageAflame, target = giant)
        resolveAll()
        expect {
            bear.zone shouldBe Zone.Graveyard
            alice.graveyard has ogre
            alice.hand.size shouldBe 4
        }
    }

    // CR 701.10e: doubling the counters
    @Test
    fun saszKatroyFindsABirdAndDoublesCounters() = scenario {
        alice.library(flier, notes)
        bob.library(notes)
        alice.battlefield(*lands("Forest", 4), bear)
        alice.hand(FinSet.saszKatroy)
        alice.casts(FinSet.saszKatroy)
        resolveAll()
        yes()
        choose(flier)
        expect { alice.hand has flier }
        advanceTo(TurnStep.DeclareAttackers, turn = 3)
        alice.attacks(FinSet.saszKatroy)
        resolveAll()
        targets(bear)
        expect { bear.counters(p1p1) shouldBe 2 }
    }

    @Test
    fun sevmarVoidPaysLifeToDraw() = scenario {
        libraries()
        alice.battlefield(FinSet.sevmarVoid)
        turn(step = TurnStep.Untap)
        advanceTo(TurnStep.Upkeep)
        resolveAll()
        yes()
        expect {
            alice.life shouldBe 19
            alice.hand.size shouldBe 1
            FinSet.sevmarVoid.counters(p1p1) shouldBe 1
        }
    }

    @Test
    fun zellerDinchPlaysTwoLandsAndReturnsOne() = scenario {
        libraries()
        alice.battlefield(FinSet.zellerDinch)
        alice.hand("Forest", "Mountain")
        alice.plays("Forest")
        alice.plays("Mountain")
        expect { FinSet.zellerDinch.power shouldBe 2 }
        advanceTo(TurnStep.End)
        resolveAll()
        choose("Forest")
        expect { alice.hand.size shouldBe 1 }
    }

    // ---- chunk 17

    @Test
    fun cupNoodlesDrawsAndFeeds() = scenario {
        libraries()
        alice.battlefield(*lands("Plains", 4))
        alice.hand(FinSet.cupNoodles)
        alice.casts(FinSet.cupNoodles)
        resolveAll()
        alice.activates(FinSet.cupNoodles)
        resolveAll()
        expect {
            alice.hand.size shouldBe 1
            alice.life shouldBe 23
        }
    }

    // CR 603.12: "when you do"
    @Test
    fun edenSanctumSeatMillsAndReturns() = scenario {
        alice.library(notes, notes)
        alice.graveyard(ogre)
        alice.battlefield(FinSet.edenSanctumSeat, *lands("Forest", 5))
        alice.activates(FinSet.edenSanctumSeat, ability = 1)
        resolveAll()
        yes()
        targets(ogre)
        expect {
            alice.hand has ogre
            FinSet.edenSanctumSeat.zone shouldBe Zone.Graveyard
        }
    }

    @Test
    fun rhyaSummonsASagaFromTheGraveyard() = scenario {
        libraries()
        alice.battlefield(FinSet.rhyaSummonerOfMist, *lands("Forest", 3))
        alice.graveyard(FinSet.conjureMoonhowlWolf)
        alice.activates(FinSet.rhyaSummonerOfMist, x = 3, target = FinSet.conjureMoonhowlWolf)
        resolveAll()
        choose()
        expect {
            FinSet.conjureMoonhowlWolf.zone shouldBe Zone.Battlefield
            FinSet.conjureMoonhowlWolf.counters(FinExtras.finality) shouldBe 1
            FinSet.conjureMoonhowlWolf hasKeyword Keyword.Haste
        }
    }

    @Test
    fun gauntletOfGenshiGivesAnotherCombat() = scenario {
        libraries()
        alice.battlefield(FinSet.gauntletOfGenshi, bear)
        attach(FinSet.gauntletOfGenshi, bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        resolveAll()
        expect {
            bear.tapped shouldBe false
            bear hasKeyword Keyword.DoubleStrike
            state.turn.extraPhases shouldBe listOf(TurnStep.BeginningOfCombat)
        }
    }

    // CR 715.3: casting the Adventure, then the card from exile
    @Test
    fun forgeRegencyAdventureThenTheTown() = scenario {
        alice.battlefield(*lands("Mountain", 3))
        alice.hand(FinSet.forgeRegency)
        alice.casts(FinSet.forgeRegency, way = adventure)
        resolveAll()
        expect {
            alice.battlefield has "Wizard Token"
            FinSet.forgeRegency.zone shouldBe Zone.Exile
        }
        alice.plays(FinSet.forgeRegency)
        expect { FinSet.forgeRegency.zone shouldBe Zone.Battlefield }
    }

    @Test
    fun squalReturnsACheapPermanent() = scenario {
        alice.library(notes)
        bob.library(notes)
        alice.battlefield(FinSet.squalSeedMercenary)
        alice.graveyard(ogre)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(FinSet.squalSeedMercenary)
        resolveAll()
        advanceTo(TurnStep.CombatDamage)
        resolveAll()
        targets(ogre)
        advanceTo(TurnStep.EndOfCombat)
        resolveAll()
        expect {
            ogre.zone shouldBe Zone.Battlefield
            bob.life shouldBe 14
        }
    }

    @Test
    fun miracleUrnExilesAndLeavesTreasure() = scenario {
        alice.battlefield(FinSet.miracleUrn, "Swamp", "Swamp", "Swamp")
        alice.hand(doom)
        bob.graveyard(ogre)
        alice.activates(FinSet.miracleUrn, target = ogre)
        resolveAll()
        alice.casts(doom, target = FinSet.miracleUrn)
        resolveAll()
        expect {
            ogre.zone shouldBe Zone.Exile
            alice.battlefield has "Treasure Token"
        }
    }

    @Test
    fun brightbladeChargesOnLifeGain() = scenario {
        alice.battlefield(FinSet.brightbladeIi, bear, "Plains", "Plains")
        attach(FinSet.brightbladeIi, bear)
        alice.hand(FinSet.mendingLitany)
        alice.casts(FinSet.mendingLitany, modes = listOf(1), target = bear)
        resolveAll()
        expect { bear.power shouldBe 3 }
    }

    @Test
    fun rexShinralMakesNightshadeOnce() = scenario {
        libraries()
        alice.battlefield(FinSet.rexShinral)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(FinSet.rexShinral)
        resolveAll()
        expect { alice.battlefield has "Nightshade" }
    }

    @Test
    fun boltPlaysTheExiledCardWhileYouControlIt() = scenario {
        alice.library(ogre, notes)
        bob.library(notes)
        alice.battlefield(FinSet.boltSecuritySergeant, *lands("Mountain", 3))
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(FinSet.boltSecuritySergeant)
        resolveAll()
        advanceTo(TurnStep.PostcombatMain)
        alice.casts(ogre)
        resolveAll()
        expect { ogre.zone shouldBe Zone.Battlefield }
    }

    @Test
    fun holySeeAdventureReturnsArtifactsAndEnchantments() = scenario {
        alice.battlefield(*lands("Plains", 5))
        alice.graveyard(trinket, charm)
        alice.hand(FinSet.holySeeOfIshvar)
        alice.casts(FinSet.holySeeOfIshvar, way = adventure, targets = listOf(trinket, charm))
        resolveAll()
        expect {
            alice.hand has trinket
            alice.hand has charm
        }
    }

    @Test
    fun lionmanePendantShootsOnEntering() = scenario {
        alice.battlefield(*lands("Plains", 4))
        alice.hand(FinSet.lionmanePendant)
        alice.casts(FinSet.lionmanePendant)
        resolveAll()
        targets("bob")
        expect { bob.life shouldBe 18 }
    }

    @Test
    fun ancientMetropolisAdventureMakesABigHero() = scenario {
        alice.battlefield(*lands("Forest", 6))
        alice.hand(FinSet.ancientMetropolisOfZan)
        alice.casts(FinSet.ancientMetropolisOfZan, way = adventure)
        resolveAll()
        expect { card("Hero Token").counters(p1p1) shouldBe 6 }
    }

    @Test
    fun ultimateArmamentDestroysOnAttack() = scenario {
        libraries()
        alice.battlefield(FinSet.ultimateArmament, bear)
        attach(FinSet.ultimateArmament, bear)
        bob.battlefield(giant)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        resolveAll()
        expect {
            giant.zone shouldBe Zone.Graveyard
            bear.power shouldBe 9
        }
    }

    @Test
    fun claudEquipsCheaplyAndGetsDoubleStrike() = scenario {
        alice.battlefield(FinSet.claudPlanetsChampion, FinSet.broadswordOfTheSoldier)
        alice.activates(FinSet.broadswordOfTheSoldier, target = FinSet.claudPlanetsChampion)
        resolveAll()
        expect {
            FinSet.claudPlanetsChampion hasKeyword Keyword.DoubleStrike
            FinSet.claudPlanetsChampion hasKeyword Keyword.Indestructible
        }
    }

    @Test
    fun theRoyalRoadsterFindsALand() = scenario {
        alice.library(notes, "Forest", notes)
        bob.library(notes)
        alice.battlefield(FinSet.theRoyalRoadster, bear)
        alice.activates(FinSet.theRoyalRoadster, choosing = listOf(bear))
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(FinSet.theRoyalRoadster)
        resolveAll()
        expect { card("Forest").zone shouldBe Zone.Battlefield }
    }

    @Test
    fun aristocratsCapitalAdventureMillsHalf() = scenario {
        bob.library(*Array(9) { notes })
        alice.battlefield(*lands("Island", 6))
        alice.hand(FinSet.aristocratsCapital)
        alice.casts(FinSet.aristocratsCapital, way = adventure, target = "bob")
        resolveAll()
        expect { bob.graveyard.size shouldBe 4 }
    }

    // CR 614.12: a colour chosen as it enters
    @Test
    fun crossingVillageMakesTheChosenColor() = scenario {
        alice.hand(FinSet.crossingVillage)
        alice.plays(FinSet.crossingVillage)
        answer(arcanum.engine.decision.Answer.ColorChoice(arcanum.api.Color.Green))
        resolveAll()
        expect { FinSet.crossingVillage.tapped shouldBe true }
    }

    @Test
    fun gerrotLootsOnEquipment() = scenario {
        libraries()
        alice.battlefield(FinSet.gerrotKingOfTheDwarves, "Plains")
        alice.hand(lance, ogre)
        alice.casts(lance)
        resolveAll()
        yes()
        choose(ogre)
        expect { alice.graveyard has ogre }
    }

    @Test
    fun severinShrinksOpponentsAndGrows() = scenario {
        alice.battlefield(*lands("Island", 3), *lands("Swamp", 3))
        alice.hand(FinSet.severinPlanetsHeir)
        bob.battlefield(bear)
        alice.casts(FinSet.severinPlanetsHeir)
        resolveAll()
        expect {
            bear.zone shouldBe Zone.Graveyard
            FinSet.severinPlanetsHeir.counters(p1p1) shouldBe 1
        }
    }

    @Test
    fun cityOfGlowingMakoAdventureSacrificesToDraw() = scenario {
        libraries()
        alice.battlefield(*lands("Swamp", 3), trinket)
        alice.hand(FinSet.cityOfGlowingMako)
        alice.casts(FinSet.cityOfGlowingMako, way = adventure)
        resolveAll()
        yes()
        choose(trinket)
        expect { alice.hand.size shouldBe 2 }
    }

    @Test
    fun theGoldenArcadeFlipsForTreasure() = scenario {
        alice.battlefield(FinSet.theGoldenArcade, "Plains", "Plains")
        random(arcanum.engine.decision.Answer.CoinFlip(true))
        alice.activates(FinSet.theGoldenArcade, ability = 1)
        resolveAll()
        expect { alice.battlefield has "Treasure Token" }
    }

    @Test
    fun rinaMakesBiscuitAndPumps() = scenario {
        libraries()
        alice.battlefield(*lands("Forest", 3), "Plains", "Plains", bear)
        alice.hand(FinSet.rinaHeartsong)
        alice.casts(FinSet.rinaHeartsong)
        resolveAll()
        expect { alice.battlefield has "Biscuit" }
        advanceTo(TurnStep.DeclareAttackers, turn = 3)
        alice.attacks(FinSet.rinaHeartsong)
        resolveAll()
        targets(bear)
        expect { bear.power shouldBe 5 }
    }

    @Test
    fun ringOfTheKingsTaps() = scenario {
        alice.battlefield(FinSet.ringOfTheKings, "Plains", "Plains")
        bob.battlefield(ogre)
        alice.activates(FinSet.ringOfTheKings, ability = 1, target = ogre)
        resolveAll()
        expect {
            ogre.tapped shouldBe true
            alice.life shouldBe 19
        }
    }
}
