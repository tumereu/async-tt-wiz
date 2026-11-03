package arcanum.engine.sets.eoe

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.eoe.EoeExtras.bear
import arcanum.engine.sets.eoe.EoeExtras.charge
import arcanum.engine.sets.eoe.EoeExtras.charm
import arcanum.engine.sets.eoe.EoeExtras.doom
import arcanum.engine.sets.eoe.EoeExtras.flier
import arcanum.engine.sets.eoe.EoeExtras.giant
import arcanum.engine.sets.eoe.EoeExtras.gizmo
import arcanum.engine.sets.eoe.EoeExtras.legend
import arcanum.engine.sets.eoe.EoeExtras.notes
import arcanum.engine.sets.eoe.EoeExtras.ogre
import arcanum.engine.sets.eoe.EoeExtras.p1p1
import arcanum.engine.sets.eoe.EoeExtras.shuttle
import arcanum.engine.sets.eoe.EoeExtras.trinket
import arcanum.engine.sets.eoe.EoeExtras.wall
import arcanum.engine.sets.eoe.EoeExtras.zap
import kotlin.test.Test

/**
 * Edge of Eternities chunks 13 to 15 in play: the green, multicolored and artifact cards and the lands. Verdani Groundgardener, Residue
 * Elemental, Frostgrotto Smasher and Icefield Worldmaw repeat tested landfall templates; Meltcaller's surveil and Plucky Greenhorn's
 * counter use covered effects; Spawning Lagoon, Faithless Chapel, Hallowed Smeltery, Trampling Hollow and Sunken Ossuary are dual lands
 * with "As this land enters, you may pay 2 life" (CR 614.12, tested in the engine's replacement tests); Outpost Overseer repeats a tested
 * second-spell Drone; Sir Harvell, the Luminous repeats a tested enters-or-attacks pump.
 */
class EoeGreenGoldArtifactScenariosTest {
    private fun Scenario.libraries() {
        alice.library(notes, notes, notes, notes, notes)
        bob.library(notes, notes, notes, notes, notes)
    }

    // ---- chunk 13

    @Test
    fun lifeforgeSurgeUntapsAndProtects() = scenario {
        alice.battlefield("Forest", "Forest")
        alice.battlefield(bear, tapped = true)
        alice.hand(EoeSet.lifeforgeSurge)
        alice.casts(EoeSet.lifeforgeSurge, target = bear)
        resolveAll()
        expect {
            bear.tapped shouldBe false
            bear.counters(p1p1) shouldBe 1
            bear hasKeyword Keyword.Indestructible
            bear hasKeyword Keyword.Trample
        }
    }

    @Test
    fun floweringBarbtailSharesDeathtouch() = scenario {
        alice.battlefield("Forest", "Forest", bear)
        alice.hand(EoeSet.floweringBarbtail)
        alice.casts(EoeSet.floweringBarbtail)
        resolveAll()
        targets(bear)
        expect { bear hasKeyword Keyword.Deathtouch }
    }

    /** +1/+0 and vigilance, then it deals damage equal to its power to an opponent's creature (not a fight). */
    @Test
    fun treatyNegotiationsBites() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", ogre)
        alice.hand(EoeSet.treatyNegotiations)
        bob.battlefield(giant)
        alice.casts(EoeSet.treatyNegotiations, targets = listOf(ogre, giant))
        resolveAll()
        expect {
            card(giant).view.obj?.damage shouldBe 4
            card(ogre).view.obj?.damage shouldBe 0
        }
    }

    /** Devour land 3: sacrifice lands as it enters, three counters each; then lands from the top by its power. */
    // CR 702.82c
    @Test
    fun starvingPlanetmawDevoursLands() = scenario {
        alice.battlefield(*Array(8) { "Forest" }, "Island", "Island")
        alice.hand(EoeSet.starvingPlanetmaw)
        alice.library("Plains", "Swamp", notes, notes, notes, notes)
        alice.casts(EoeSet.starvingPlanetmaw)
        resolveAll()
        choose("Island", "Island")
        expect { EoeSet.starvingPlanetmaw.power shouldBe 6 }
        choose("Plains", "Swamp")
        expect {
            card("Plains").zone shouldBe Zone.Battlefield
            card("Plains").tapped shouldBe true
        }
    }

    @Test
    fun rampagingHornbeastCantBeCountered() = scenario {
        alice.battlefield("Forest", "Forest", EoeSet.rampagingHornbeast)
        alice.hand(bear)
        bob.battlefield("Island", "Island", "Island")
        bob.hand(EoeSet.unspool)
        alice.casts(bear)
        alice.passes()
        bob.casts(EoeSet.unspool, target = bear)
        resolveAll()
        expect { bear.zone shouldBe Zone.Battlefield }
    }

    @Test
    fun sporecapBehemothByLandNames() = scenario {
        alice.battlefield("Forest", "Forest", "Island", "Swamp", "Mountain")
        alice.hand(EoeSet.sporecapBehemoth)
        alice.casts(EoeSet.sporecapBehemoth)
        resolveAll()
        expect { EoeSet.sporecapBehemoth.zone shouldBe Zone.Battlefield }
    }

    @Test
    fun genomePollenDroneTapsAPermanent() = scenario {
        alice.battlefield(EoeSet.genomePollenDrone, trinket)
        alice.activates(EoeSet.genomePollenDrone, choosing = listOf(trinket), color = arcanum.api.Color.Blue)
        expect {
            alice.pool.size shouldBe 1
            trinket.tapped shouldBe true
        }
    }

    @Test
    fun concordantGladeWalkerCountsLands() = scenario {
        alice.battlefield(EoeSet.concordantGladeWalker, "Forest", "Forest", "Island")
        expect { EoeSet.concordantGladeWalker.power shouldBe 3 }
    }

    @Test
    fun bloodbondTickLendsPower() = scenario {
        libraries()
        alice.battlefield(EoeSet.bloodbondTick, bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(EoeSet.bloodbondTick)
        resolveAll()
        targets(bear)
        expect { bear.power shouldBe 3 }
    }

    @Test
    fun frostplowScoutPlaysFromTheGraveyard() = scenario {
        alice.battlefield(EoeSet.frostplowScout)
        alice.graveyard("Forest", "Island")
        alice.library(notes, notes)
        alice.plays("Forest")
        resolveAll()
        alice.plays("Island")
        resolveAll()
        expect {
            card("Island").zone shouldBe Zone.Battlefield
            alice.graveyard.size shouldBe 2
        }
    }

    /** Costs {2} less if your opponents control three or more creatures. */
    @Test
    fun whiptailStalkerCheaper() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest")
        alice.hand(EoeSet.whiptailStalker)
        bob.battlefield(bear, ogre, giant)
        alice.casts(EoeSet.whiptailStalker)
        resolveAll()
        expect { EoeSet.whiptailStalker.zone shouldBe Zone.Battlefield }
    }

    @Test
    fun thawwalkerMournerDrawsForCounteredDeaths() = scenario {
        alice.battlefield(EoeSet.thawwalkerMourner, "Swamp")
        alice.battlefield(bear, counters = listOf(p1p1 to 1))
        alice.hand(doom)
        alice.library(notes)
        alice.casts(doom, target = bear)
        resolveAll()
        expect { alice.hand has notes }
    }

    @Test
    fun thawwalkersKitAttachesAndReaches() = scenario {
        alice.battlefield("Forest", bear)
        alice.hand(EoeSet.thawwalkersKit)
        alice.casts(EoeSet.thawwalkersKit)
        resolveAll()
        targets(bear)
        expect {
            bear.power shouldBe 4
            bear hasKeyword Keyword.Reach
        }
    }

    @Test
    fun thawwalkersGritFights() = scenario {
        alice.battlefield("Forest", giant)
        alice.hand(EoeSet.thawwalkersGrit)
        bob.battlefield(ogre)
        alice.casts(EoeSet.thawwalkersGrit, target = giant)
        resolveAll()
        targets(ogre)
        expect {
            ogre.zone shouldBe Zone.Graveyard
            giant.toughness shouldBe 7
        }
    }

    @Test
    fun coilselfDevourerCountersByPower() = scenario {
        libraries()
        alice.battlefield(EoeSet.coilselfDevourer, bear)
        advanceTo(TurnStep.BeginningOfCombat)
        resolveAll()
        expect {
            bear.counters(p1p1) shouldBe 1
            EoeSet.coilselfDevourer.counters(p1p1) shouldBe 1
        }
    }

    @Test
    fun drawBackThroughTheLoomReturnsCardsAndLands() = scenario {
        alice.battlefield(*Array(5) { "Forest" })
        alice.hand(EoeSet.drawBackThroughTheLoom)
        alice.graveyard(gizmo, "Island")
        alice.casts(EoeSet.drawBackThroughTheLoom)
        answer(arcanum.engine.decision.Answer.Targets(listOf(target(gizmo))))
        answer(arcanum.engine.decision.Answer.Targets(listOf(target("Island"))))
        pay()
        expect {
            alice.hand has gizmo
            card("Island").zone shouldBe Zone.Battlefield
            card("Island").tapped shouldBe true
        }
    }

    @Test
    fun brokenPinionsDestroysAFlier() = scenario {
        alice.battlefield("Forest", "Forest", "Forest")
        alice.hand(EoeSet.brokenPinions)
        alice.library(notes)
        bob.battlefield(flier)
        alice.casts(EoeSet.brokenPinions, target = flier)
        resolveAll()
        choose()
        expect { flier.zone shouldBe Zone.Graveyard }
    }

    @Test
    fun cloudbarbBlocksAFlier() = scenario {
        libraries()
        alice.battlefield(EoeSet.cloudbarb)
        bob.battlefield(flier)
        turn(active = bob, step = TurnStep.DeclareAttackers)
        bob.attacks(flier)
        allPass()
        alice.blocks(EoeSet.cloudbarb to flier)
        resolveAll()
        expect { EoeSet.cloudbarb.power shouldBe 8 }
    }

    @Test
    fun loomworkSentinelDamageByToughness() = scenario {
        libraries()
        alice.battlefield(EoeSet.loomworkSentinel, wall)
        advanceTo(TurnStep.End)
        expect { wall.power shouldBe 0 }
    }

    /** You put +1/+1 counters on a creature you control: you may draw that many, once each turn. */
    @Test
    fun groundbondingDrawsOnce() = scenario {
        alice.battlefield(EoeSet.groundbonding, bear, "Plains", "Plains")
        alice.hand(EoeSet.laurelOfMerit, EoeSet.laurelOfMerit)
        alice.library(notes, notes, notes, notes)
        alice.casts(EoeSet.laurelOfMerit, target = bear)
        resolveAll()
        yes()
        expect { alice.hand.size shouldBe 3 }
        alice.casts(EoeSet.laurelOfMerit, target = bear)
        resolveAll()
        expect { alice.hand.size shouldBe 3 }
    }

    // ---- chunk 14

    @Test
    fun velunirSlumberingNoviceDiscardsAnArtifact() = scenario {
        alice.battlefield("Island", "Swamp", "Swamp")
        alice.hand(EoeSet.velunirSlumberingNovice, trinket, giant)
        alice.library(notes, notes)
        alice.casts(EoeSet.velunirSlumberingNovice)
        resolveAll()
        yes()
        choose(trinket)
        expect {
            alice.graveyard has trinket
            alice.hand.size shouldBe 3
            EoeSet.velunirSlumberingNovice hasKeyword Keyword.Deathtouch
        }
    }

    @Test
    fun starwastegoyfCountsExile() = scenario {
        alice.battlefield(EoeSet.starwastegoyf)
        alice.exile(bear, ogre)
        expect {
            EoeSet.starwastegoyf.power shouldBe 2
            EoeSet.starwastegoyf.toughness shouldBe 3
        }
    }

    @Test
    fun twinnoraEntersByManaSpent() = scenario {
        alice.battlefield("Forest", "Plains", "Island", "Island")
        alice.hand(EoeSet.twinnoraFusionComposite)
        alice.casts(EoeSet.twinnoraFusionComposite, x = 2)
        resolveAll()
        expect { EoeSet.twinnoraFusionComposite.counters(p1p1) shouldBe 4 }
    }

    @Test
    fun twinnoraRemovesCountersToDraw() = scenario {
        libraries()
        alice.battlefield(EoeSet.twinnoraFusionComposite, counters = listOf(p1p1 to 2))
        alice.battlefield(bear, counters = listOf(p1p1 to 1))
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        resolveAll()
        yes()
        choose(EoeSet.twinnoraFusionComposite, bear)
        expect {
            alice.hand.size shouldBe 1
            alice.battlefield has "Robot Token"
            bear.counters(p1p1) shouldBe 0
        }
    }

    @Test
    fun geneshiftChrysalidWithSixLands() = scenario {
        alice.battlefield(EoeSet.geneshiftChrysalid, *Array(5) { "Island" }, bear)
        alice.hand("Forest")
        alice.plays("Forest")
        resolveAll()
        targets(bear)
        expect { bear.power shouldBe 6 }
    }

    @Test
    fun orlenaRisingRecruitCountersAndCards() = scenario {
        libraries()
        alice.battlefield(EoeSet.orlenaRisingRecruit, bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(EoeSet.orlenaRisingRecruit, bear)
        resolveAll()
        targets(bear)
        advanceTo(TurnStep.End)
        resolveAll()
        expect {
            bear.counters(p1p1) shouldBe 1
            alice.hand.size shouldBe 1
        }
    }

    @Test
    fun quellanMaeltharExileCountersOnArtifacts() = scenario {
        alice.battlefield(EoeSet.quellanMaeltharExile, "Island", bear)
        alice.hand(trinket)
        alice.casts(trinket)
        resolveAll()
        targets(bear)
        expect { bear.counters(p1p1) shouldBe 1 }
    }

    @Test
    fun rebelliousSlaughterOddKillsAndSteals() = scenario {
        libraries()
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Swamp", "Swamp")
        alice.hand(EoeSet.rebelliousSlaughter)
        bob.battlefield(ogre, bear)
        alice.casts(EoeSet.rebelliousSlaughter)
        resolveAll()
        answer(arcanum.engine.decision.Answer.Option("odd"))
        resolveAll()
        expect {
            ogre.zone shouldBe Zone.Graveyard
            bear.controller shouldBe alice.id
            bear hasKeyword Keyword.Haste
        }
    }

    /** Artifacts are Foods with a life ability; sacrificing a Food deals 3 to each opponent; untaps after life gain. */
    @Test
    fun brisquelFeedsOnArtifacts() = scenario {
        alice.battlefield(EoeSet.brisquelNimbleGalleychef, trinket, "Plains", "Plains", "Plains")
        expect { assert(arcanum.api.Subtype("Food") in trinket.subtypes) }
        alice.activates(EoeSet.brisquelNimbleGalleychef, choosing = listOf(trinket))
        resolveAll()
        expect { bob.life shouldBe 17 }
    }

    @Test
    fun orloVesselsMechanicMakesARobot() = scenario {
        libraries()
        alice.battlefield(EoeSet.orloVesselsMechanic, bear, ogre)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear, ogre)
        advanceTo(TurnStep.End)
        resolveAll()
        expect { alice.battlefield has "Robot Token" }
    }

    @Test
    fun orloFreewheelingSkipperAffinity() = scenario {
        alice.battlefield(EoeSet.orloFreewheelingSkipper, trinket, gizmo, "Forest", "Forest")
        alice.hand(EoeSet.drixDestinyweaver)
        alice.casts(EoeSet.drixDestinyweaver)
        resolveAll()
        expect { EoeSet.drixDestinyweaver.zone shouldBe Zone.Battlefield }
    }

    @Test
    fun sowingArkNursemaidReanimates() = scenario {
        alice.battlefield(EoeSet.sowingArkNursemaid, "Swamp", "Forest", "Forest", "Forest", "Forest")
        alice.graveyard(giant)
        alice.activates(EoeSet.sowingArkNursemaid, target = giant)
        resolveAll()
        expect { giant.zone shouldBe Zone.Battlefield }
    }

    @Test
    fun collapseOfTheHorizonMillsHalf() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Swamp", "Swamp", bear)
        alice.hand(EoeSet.collapseOfTheHorizon)
        bob.library(notes, notes, notes, notes, notes)
        alice.casts(EoeSet.collapseOfTheHorizon, targets = listOf("bob"))
        resolveAll()
        expect {
            bear.zone shouldBe Zone.Graveyard
            bob.graveyard.size shouldBe 2
        }
    }

    @Test
    fun chronoSpatialRiftMillsByLife() = scenario {
        alice.battlefield("Plains", "Plains", "Island", "Island")
        alice.hand(EoeSet.chronoSpatialRift)
        alice.life = 3
        bob.library(notes, notes, notes, notes, notes)
        alice.casts(EoeSet.chronoSpatialRift, target = "bob")
        resolveAll()
        expect { bob.graveyard.size shouldBe 3 }
    }

    /** Another creature dies or is exiled: a counter and 1 life; it dies with power 4 or more: destroy a nonland permanent. */
    @Test
    fun sirHarvellParagonGrowsAndAvenges() = scenario {
        alice.battlefield(EoeSet.sirHarvellDaystarParagon, counters = listOf(p1p1 to 1))
        alice.battlefield("Swamp", "Swamp", bear)
        alice.hand(doom, doom)
        bob.battlefield(charm)
        alice.casts(doom, target = bear)
        resolveAll()
        expect {
            alice.life shouldBe 21
            EoeSet.sirHarvellDaystarParagon.power shouldBe 4
        }
        alice.casts(doom, target = EoeSet.sirHarvellDaystarParagon)
        resolveAll()
        targets(charm)
        expect { charm.zone shouldBe Zone.Graveyard }
    }

    @Test
    fun brakkosRemembranceStandardSecondTimeDraws() = scenario {
        alice.battlefield(EoeSet.brakkosRemembranceStandard, EoeSet.frostplowScout)
        alice.hand("Forest", "Forest")
        alice.library(notes, notes, notes)
        alice.plays("Forest")
        resolveAll()
        expect { bob.life shouldBe 19 }
        alice.plays("Forest")
        resolveAll()
        expect {
            bob.life shouldBe 18
            alice.hand.size shouldBe 1
        }
    }

    @Test
    fun rationBrickDrawsWhenSacrificed() = scenario {
        alice.battlefield(EoeSet.rationBrick, "Plains", "Plains")
        alice.library(notes)
        alice.activates(EoeSet.rationBrick)
        resolveAll()
        expect {
            alice.life shouldBe 23
            alice.hand has notes
        }
    }

    // ---- chunk 15

    @Test
    fun everyPathCodexDrawsByLandNames() = scenario {
        alice.battlefield(EoeSet.everyPathCodex, *Array(7) { "Forest" }, "Island")
        alice.library(notes, notes, notes)
        alice.activates(EoeSet.everyPathCodex, ability = 1)
        resolveAll()
        expect { alice.hand.size shouldBe 2 }
    }

    @Test
    fun silversteelPalBuriesAGraveyardCard() = scenario {
        alice.battlefield(EoeSet.silversteelPal, "Plains", "Plains")
        bob.graveyard(giant)
        alice.activates(EoeSet.silversteelPal, target = giant)
        resolveAll()
        expect {
            giant.zone shouldBe Zone.Library
            alice.life shouldBe 21
        }
    }

    /** Sacrificed unless you tap an untapped permanent you control. */
    @Test
    fun helmDeckNeedsATap() = scenario {
        alice.battlefield(trinket)
        alice.hand(EoeSet.helmDeck)
        alice.plays(EoeSet.helmDeck)
        resolveAll()
        yes()
        choose(trinket)
        expect {
            EoeSet.helmDeck.zone shouldBe Zone.Battlefield
            trinket.tapped shouldBe true
        }
    }

    @Test
    fun remoteStarsmithyTapsArtifactsForPower() = scenario {
        alice.battlefield(EoeSet.remoteStarsmithy, "Plains", "Plains", trinket, gizmo, bear)
        alice.activates(EoeSet.remoteStarsmithy, ability = 1, x = 2, target = bear, choosing = listOf(trinket, gizmo))
        resolveAll()
        expect { bear.power shouldBe 4 }
    }

    @Test
    fun scoutingAutomatonCheaperByLandNames() = scenario {
        alice.battlefield(EoeSet.scoutingAutomaton, *Array(5) { "Plains" }, "Island", "Swamp", "Mountain", "Forest")
        alice.library(notes, notes, notes)
        alice.activates(EoeSet.scoutingAutomaton, targets = listOf("bob", "alice"))
        resolveAll()
        expect {
            bob.life shouldBe 17
            alice.hand.size shouldBe 3
            alice.life shouldBe 23
        }
    }

    @Test
    fun vexmorUntapsAndGrowsArtifactCreatures() = scenario {
        alice.battlefield(EoeSet.vexmorRuthlessCommander, counters = listOf(CounterKind.Loyalty to 4))
        alice.battlefield(EoeSet.automatonHelmsman, tapped = true)
        alice.activates(EoeSet.vexmorRuthlessCommander, ability = 0, target = EoeSet.automatonHelmsman)
        resolveAll()
        expect {
            EoeSet.automatonHelmsman.tapped shouldBe false
            EoeSet.automatonHelmsman.counters(p1p1) shouldBe 1
        }
    }

    @Test
    fun vexmorLoyaltyOnArtifacts() = scenario {
        alice.battlefield(EoeSet.vexmorRuthlessCommander, counters = listOf(CounterKind.Loyalty to 4))
        alice.battlefield("Plains")
        alice.hand(trinket)
        alice.casts(trinket)
        resolveAll()
        expect { EoeSet.vexmorRuthlessCommander.counters(CounterKind.Loyalty) shouldBe 5 }
    }

    @Test
    fun vexmorEmblemAnimatesArtifacts() = scenario {
        libraries()
        alice.battlefield(EoeSet.vexmorRuthlessCommander, counters = listOf(CounterKind.Loyalty to 7))
        alice.battlefield(trinket)
        alice.activates(EoeSet.vexmorRuthlessCommander, ability = 2)
        resolveAll()
        advanceTo(TurnStep.BeginningOfCombat)
        resolveAll()
        targets(trinket)
        expect {
            trinket.power shouldBe 3
            assert(arcanum.api.Subtype("Robot") in trinket.subtypes)
        }
    }

    @Test
    fun wondersteelMissileCheaperAfterASpacecraftAttack() = scenario {
        libraries()
        alice.battlefield(EoeSet.wondersteelMissile, shuttle, "Plains", "Plains", "Plains")
        alice.battlefield(giant)
        bob.battlefield(charm)
        alice.activates(shuttle, choosing = listOf(giant))
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers, turn = 3)
        alice.attacks(shuttle)
        advanceTo(TurnStep.PostcombatMain, turn = 3)
        alice.activates(EoeSet.wondersteelMissile, target = charm)
        resolveAll()
        expect { charm.zone shouldBe Zone.Graveyard }
    }

    /** The equipped creature's ability costs {X} less (its power): exile the Equipment, control the opponent's next turn. */
    // CR 723
    @Test
    fun sovereignBangleControlsATurn() = scenario {
        libraries()
        alice.battlefield(EoeSet.theSovereignBangle, giant, *Array(10) { "Plains" })
        attach(EoeSet.theSovereignBangle, giant)
        alice.activates(giant, target = "bob")
        resolveAll()
        expect {
            EoeSet.theSovereignBangle.zone shouldBe Zone.Exile
            giant.power shouldBe 5
        }
    }

    @Test
    fun finalityStoneDrawsAndHalvesLife() = scenario {
        libraries()
        alice.battlefield(EoeSet.theFinalityStone)
        alice.hand("Forest")
        alice.plays("Forest")
        resolveAll()
        expect { alice.hand.size shouldBe 1 }
        advanceTo(TurnStep.End)
        resolveAll()
        expect { alice.life shouldBe 10 }
    }

    @Test
    fun hummingBroodvatMakesSlivers() = scenario {
        libraries()
        alice.battlefield(EoeSet.hummingBroodvat)
        advanceTo(TurnStep.Upkeep, turn = 3)
        resolveAll()
        expect {
            alice.battlefield.names.count { it == "Sliver Token" } shouldBe 2
            "Sliver Token" hasKeyword Keyword.DoubleStrike
        }
    }

    @Test
    fun toxicHushBotPoisons() = scenario {
        libraries()
        alice.battlefield(EoeSet.toxicHushBot)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(EoeSet.toxicHushBot)
        advanceTo(TurnStep.End)
        resolveAll()
        expect { bob.counters(CounterKind.Poison) shouldBe 2 }
    }
}
