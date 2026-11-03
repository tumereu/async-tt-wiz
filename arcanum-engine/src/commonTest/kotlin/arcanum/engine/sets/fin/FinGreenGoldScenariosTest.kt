package arcanum.engine.sets.fin

import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.fin.FinExtras.bear
import arcanum.engine.sets.fin.FinExtras.charm
import arcanum.engine.sets.fin.FinExtras.doom
import arcanum.engine.sets.fin.FinExtras.finality
import arcanum.engine.sets.fin.FinExtras.flier
import arcanum.engine.sets.fin.FinExtras.giant
import arcanum.engine.sets.fin.FinExtras.lore
import arcanum.engine.sets.fin.FinExtras.notes
import arcanum.engine.sets.fin.FinExtras.ogre
import arcanum.engine.sets.fin.FinExtras.p1p1
import arcanum.engine.sets.fin.FinExtras.stun
import arcanum.engine.sets.fin.FinExtras.trinket
import arcanum.engine.sets.fin.FinExtras.zap
import kotlin.test.Test

/**
 * Final Fantasy chunks 13 and 14 in play: the green and the multicoloured cards. Beyond the Horizon and Wanderer's Path fetch lands as
 * tested searches do; Grove-Pulse Ochu's graveyard count repeats Crystal Weapon's.
 */
class FinGreenGoldScenariosTest {
    private fun lands(land: String, n: Int) = Array(n) { land }

    // ---- chunk 13

    // CR 603.12: "when you search your library this way"
    @Test
    fun wanderersPathFetchesAndCounters() = scenario {
        alice.library(FinSet.harborTown, notes)
        alice.battlefield(bear, *lands("Forest", 3))
        alice.hand(FinSet.wanderersPath)
        alice.casts(FinSet.wanderersPath)
        resolveAll()
        choose(FinSet.harborTown)
        targets(bear)
        expect {
            FinSet.harborTown.zone shouldBe Zone.Battlefield
            bear.counters(p1p1) shouldBe 1
        }
    }

    @Test
    fun villageGreeterKeepsATownAndGainsLife() = scenario {
        alice.library(notes, FinSet.harborTown, notes, notes, notes)
        alice.battlefield("Forest", "Forest")
        alice.hand(FinSet.villageGreeter)
        alice.casts(FinSet.villageGreeter)
        resolveAll()
        choose(FinSet.harborTown)
        expect {
            alice.hand has FinSet.harborTown
            alice.life shouldBe 22
        }
    }

    // CR 603.2d: a Bird entering triggers twice
    @Test
    fun wanderingStriderPlaysBirdsFromTheTopAndDoublesTheirTriggers() = scenario {
        alice.library(flier, notes)
        alice.battlefield(FinSet.wanderingStrider, FinSet.rideTheBehemoth, "Plains", "Plains")
        alice.hand("Forest")
        alice.casts(flier)
        resolveAll()
        alice.plays("Forest")
        resolveAll()
        targets(flier)
        targets(flier)
        expect {
            flier.zone shouldBe Zone.Battlefield
            flier.counters(p1p1) shouldBe 2
        }
    }

    @Test
    fun rideTheBehemothCountersAndBecomesABeast() = scenario {
        alice.battlefield(FinSet.rideTheBehemoth, *lands("Forest", 7))
        alice.activates(FinSet.rideTheBehemoth)
        resolveAll()
        expect { FinSet.rideTheBehemoth.power shouldBe 7 }
    }

    // CR 614.1a: twice that many counters
    @Test
    fun theGroveCrystalDoublesCountersAndDistributes() = scenario {
        alice.battlefield(FinSet.theGroveCrystal, bear, *lands("Forest", 6))
        alice.activates(FinSet.theGroveCrystal, target = bear)
        resolveAll()
        expect { bear.counters(p1p1) shouldBe 4 }
    }

    @Test
    fun gaelsFinalStandPassesPowerOnDeath() = scenario {
        alice.battlefield(ogre, bear, "Forest", "Forest", "Swamp")
        alice.hand(FinSet.gaelsFinalStand, doom)
        alice.casts(FinSet.gaelsFinalStand, target = ogre)
        resolveAll()
        alice.casts(doom, target = ogre)
        resolveAll()
        targets(bear)
        expect { bear.counters(p1p1) shouldBe 4 }
    }

    // CR 614.1a: the tokens plus a Frog
    @Test
    fun quennaAddsAFrogAndEatsIt() = scenario {
        alice.battlefield(FinSet.quennaGourmet, "Plains", "Plains", "Plains", "Plains")
        alice.hand(FinSet.tacticsMenu)
        alice.casts(FinSet.tacticsMenu, modes = listOf(0))
        resolveAll()
        expect { alice.battlefield has "Frog Token" }
        alice.activates(FinSet.quennaGourmet, choosing = listOf("Frog Token"))
        resolveAll()
        expect { FinSet.quennaGourmet.counters(p1p1) shouldBe 1 }
    }

    @Test
    fun torvalCountersTheFirstHuman() = scenario {
        alice.battlefield(FinSet.torvalAFineHound, "Plains", "Plains")
        alice.hand(FinExtras.legend)
        alice.casts(FinExtras.legend)
        resolveAll()
        expect { FinExtras.legend.counters(p1p1) shouldBe 1 }
    }

    @Test
    fun cactusSprinterReturnsToHand() = scenario {
        libraries()
        alice.battlefield(FinSet.cactusSprinter)
        advanceTo(TurnStep.End)
        resolveAll()
        expect { alice.hand has FinSet.cactusSprinter }
    }

    // CR 514.2: damage stays; CR 614.1a: damage redirected
    @Test
    fun primevalTortoiseKeepsDamageAndTakesYours() = scenario {
        libraries()
        alice.battlefield(FinSet.primevalTortoise, bear)
        bob.battlefield("Mountain")
        bob.hand(zap)
        turn(active = bob)
        bob.casts(zap, target = bear)
        resolveAll()
        expect {
            bear.zone shouldBe Zone.Battlefield
            card(FinSet.primevalTortoise).view.obj?.damage shouldBe 2
        }
        advanceTo(TurnStep.Upkeep, turn = 2)
        expect { card(FinSet.primevalTortoise).view.obj?.damage shouldBe 2 }
    }

    @Test
    fun crystalWeaponPreventsCombatDamageAndCostsLess() = scenario {
        alice.graveyard(bear, ogre, giant, charm, trinket)
        alice.battlefield(*lands("Forest", 4))
        alice.hand(FinSet.crystalWeapon)
        alice.casts(FinSet.crystalWeapon)
        resolveAll()
        expect { FinSet.crystalWeapon.zone shouldBe Zone.Battlefield }
    }

    // CR 702.33a: kicker by returning a land
    @Test
    fun birdKickKickedDealsTwice() = scenario {
        alice.battlefield(bear, "Forest", "Forest", "Forest")
        alice.hand(FinSet.birdKick)
        bob.battlefield(ogre)
        alice.casts(FinSet.birdKick, targets = listOf(bear, ogre), kicked = 1, choosing = listOf("Forest"))
        resolveAll()
        expect { ogre.zone shouldBe Zone.Graveyard }
    }

    @Test
    fun grovePulseOchuGrowsByTheGraveyard() = scenario {
        alice.graveyard(bear, ogre)
        alice.battlefield(FinSet.grovePulseOchu, *lands("Forest", 8))
        alice.activates(FinSet.grovePulseOchu)
        resolveAll()
        expect { FinSet.grovePulseOchu.power shouldBe 3 }
    }

    @Test
    fun tiraDoublesPowerOnLandfall() = scenario {
        alice.battlefield(FinSet.tiraLockheart, counters = listOf(p1p1 to 1))
        alice.hand("Forest")
        alice.plays("Forest")
        resolveAll()
        expect { FinSet.tiraLockheart.power shouldBe 4 }
    }

    @Test
    fun bardAndBirdieBirdsShoot() = scenario {
        alice.battlefield(flier, FinSet.rideTheBehemoth, *lands("Forest", 5))
        alice.hand(FinSet.bardAndBirdie)
        bob.battlefield(bear)
        alice.casts(FinSet.bardAndBirdie)
        resolveAll()
        targets(bear)
        // only the Crystal Sky Hawk (1 power) is another Bird
        expect { bear.zone shouldBe Zone.Battlefield }
    }

    @Test
    fun beyondTheHorizonFetchesTwoDifferentLands() = scenario {
        alice.library("Forest", FinSet.harborTown, notes)
        alice.battlefield(*lands("Forest", 4))
        alice.hand(FinSet.beyondTheHorizon)
        alice.casts(FinSet.beyondTheHorizon)
        resolveAll()
        choose("Forest", FinSet.harborTown)
        expect {
            FinSet.harborTown.zone shouldBe Zone.Battlefield
            alice.battlefield.names.count { it == "Forest" } shouldBe 5
        }
    }

    // ---- chunk 14

    // CR 614.1a: double damage until your next turn
    @Test
    fun lightfallDoublesDamageToThatPlayer() = scenario {
        libraries()
        alice.battlefield(FinSet.lightfallLegionOfOne, "Mountain")
        alice.hand(zap)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(FinSet.lightfallLegionOfOne)
        resolveAll()
        advanceTo(TurnStep.PostcombatMain)
        resolveAll()
        alice.casts(zap, target = "bob")
        resolveAll()
        // 3 combat damage, then Crystal Zap's 2 doubled
        expect { bob.life shouldBe 13 }
    }

    @Test
    fun synnCopiesPermanentsFromTheGraveyard() = scenario {
        libraries()
        alice.graveyard(giant)
        alice.battlefield(*lands("Forest", 4), "Swamp", "Island", "Island")
        alice.hand(FinSet.synnScourgeOfTheSea)
        random({ kind -> arcanum.engine.decision.Answer.RandomObjects((kind as arcanum.engine.decision.RandomKind.Objects).among.take(1)) })
        alice.casts(FinSet.synnScourgeOfTheSea)
        resolveAll()
        expect {
            alice.exile has giant
            alice.battlefield has giant
        }
    }

    @Test
    fun garnaEatsLoreCounters() = scenario {
        libraries()
        alice.battlefield(FinSet.garnaPrincessOfTheMist)
        alice.battlefield(FinSet.conjureFeatherfootStampede, counters = listOf(lore to 2))
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(FinSet.garnaPrincessOfTheMist)
        resolveAll()
        choose(FinSet.conjureFeatherfootStampede)
        expect {
            FinSet.garnaPrincessOfTheMist.counters(p1p1) shouldBe 1
            FinSet.conjureFeatherfootStampede.counters(lore) shouldBe 1
        }
    }

    // CR 702.122b: crewed by; CR 500.8: an additional combat phase
    @Test
    fun baltherAndFrinGiveVehiclesAnotherCombat() = scenario {
        libraries()
        alice.battlefield(FinSet.baltherAndFrin, FinSet.ironcladWalker, "Mountain", "Forest", "Forest")
        advanceTo(TurnStep.BeginningOfCombat)
        alice.activates(FinSet.ironcladWalker, choosing = listOf(FinSet.baltherAndFrin))
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(FinSet.ironcladWalker)
        resolveAll()
        yes()
        pay()
        expect { state.turn.extraPhases shouldBe listOf(TurnStep.BeginningOfCombat) }
    }

    @Test
    fun ignatiusFindsALandAndCooksCreatures() = scenario {
        alice.library("Forest", notes, notes, notes, notes, notes)
        alice.battlefield(*lands("Island", 3), *lands("Forest", 3))
        alice.hand(FinSet.ignatiusCampChef)
        bob.graveyard(ogre)
        alice.casts(FinSet.ignatiusCampChef)
        resolveAll()
        choose("Forest")
        expect { alice.battlefield.names.count { it == "Forest" } shouldBe 4 }
    }

    @Test
    fun noctiaCastsArtifactsFromTheGraveyardForLife() = scenario {
        alice.battlefield(FinSet.noctiaPrinceOfLight, "Plains")
        alice.graveyard(trinket)
        alice.casts(trinket)
        resolveAll()
        expect {
            trinket.zone shouldBe Zone.Battlefield
            trinket.counters(finality) shouldBe 1
            alice.life shouldBe 17
        }
    }

    @Test
    fun golbarSurveilsAndReturnsACreature() = scenario {
        libraries()
        alice.battlefield(FinSet.golbarCrystalHoarder, trinket, trinket, trinket, FinExtras.lance)
        alice.graveyard(ogre)
        advanceTo(TurnStep.End)
        resolveAll()
        targets(ogre)
        expect { alice.hand has ogre }
    }

    @Test
    fun zidrenStealsAndTreasuresWhenGivingAway() = scenario {
        libraries()
        alice.battlefield(*lands("Mountain", 3), "Plains", "Plains", FinSet.stilkinMoogleTrader, trinket)
        alice.hand(FinSet.zidrenTroupeThief)
        bob.battlefield(ogre, tapped = true)
        alice.casts(FinSet.zidrenTroupeThief)
        resolveAll()
        expect {
            ogre.controller shouldBe alice.id
            ogre.tapped shouldBe false
            ogre hasKeyword Keyword.Haste
        }
    }

    // CR 613.1b: an opponent gains control of a permanent from you
    @Test
    fun zidrenMakesTreasureWhenAnOpponentGetsAPermanent() = scenario {
        libraries()
        alice.battlefield(FinSet.zidrenTroupeThief, FinSet.stilkinMoogleTrader, trinket, "Plains", "Plains")
        alice.activates(FinSet.stilkinMoogleTrader, targets = listOf("bob", trinket))
        resolveAll()
        expect { alice.battlefield has "Treasure Token" }
    }

    @Test
    fun chocoboDigsWhenBirdsAttack() = scenario {
        alice.library("Forest", notes, notes)
        bob.library(notes)
        alice.battlefield(FinSet.chocoboSeekerOfHavens, flier)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(FinSet.chocoboSeekerOfHavens, flier)
        resolveAll()
        choose(notes)
        choose("Forest")
        expect {
            alice.hand.size shouldBe 1
            card("Forest").zone shouldBe Zone.Battlefield
        }
    }

    @Test
    fun jenaraMutatesAndDrawsOnMutantDeaths() = scenario {
        libraries()
        alice.battlefield(FinSet.jenaraAncientCalamity, bear, "Swamp")
        alice.hand(doom)
        advanceTo(TurnStep.BeginningOfCombat)
        resolveAll()
        targets(bear)
        expect { bear.counters(p1p1) shouldBe 1 }
        advanceTo(TurnStep.PostcombatMain)
        alice.casts(doom, target = bear)
        resolveAll()
        expect { alice.hand.size shouldBe 3 }
    }

    @Test
    fun yunelleReturnsAnEnchantmentWithFinality() = scenario {
        libraries()
        alice.battlefield(FinSet.yunelleHopeOfTheIsles)
        alice.graveyard(charm)
        expect { FinSet.yunelleHopeOfTheIsles hasKeyword Keyword.Trample }
        advanceTo(TurnStep.End)
        resolveAll()
        targets(charm)
        expect {
            charm.zone shouldBe Zone.Battlefield
            charm.counters(finality) shouldBe 1
        }
    }

    @Test
    fun omicronStunsByNonbasicLands() = scenario {
        alice.battlefield(FinSet.harborTown, FinSet.firstVillage, *lands("Forest", 4), "Island", "Island")
        alice.hand(FinSet.omicronHeartlessEvolution)
        bob.battlefield(ogre)
        alice.casts(FinSet.omicronHeartlessEvolution)
        resolveAll()
        targets(ogre)
        expect {
            ogre.tapped shouldBe true
            ogre.counters(stun) shouldBe 2
            alice.life shouldBe 22
        }
    }

    @Test
    fun theWanderingBardLetsLandsEnterUntapped() = scenario {
        alice.battlefield(FinSet.theWanderingBard)
        alice.hand(FinSet.harborTown)
        alice.plays(FinSet.harborTown)
        resolveAll()
        expect { FinSet.harborTown.tapped shouldBe false }
    }

    // CR 701.42a: meld
    @Test
    fun vallaMeldsWithFennaIntoRagnaroth() = scenario {
        libraries()
        alice.battlefield(FinSet.vallaCheerfulPulsebearer, FinSet.fennaFearlessPulsebearer, "Forest", "Forest", "Forest", "Swamp", "Swamp")
        turn(step = TurnStep.Upkeep)
        advanceTo(TurnStep.PrecombatMain)
        resolveAll()
        yes()
        pay()
        expect {
            alice.battlefield.size shouldBe 6
            card(FinSet.vallaCheerfulPulsebearer).view.characteristics?.name shouldBe "Ragnaroth, Divine Deliverance"
            FinSet.vallaCheerfulPulsebearer.power shouldBe 7
            FinSet.vallaCheerfulPulsebearer hasKeyword Keyword.Haste
        }
    }

    // CR 712.21: both cards leave together
    @Test
    fun ragnarothDiesAsTwoCards() = scenario {
        libraries()
        alice.battlefield(FinSet.vallaCheerfulPulsebearer, FinSet.fennaFearlessPulsebearer, "Forest", "Forest", "Forest", "Swamp", "Swamp", "Swamp")
        alice.hand(doom)
        alice.graveyard(ogre)
        bob.battlefield(charm)
        turn(step = TurnStep.Upkeep)
        advanceTo(TurnStep.PrecombatMain)
        resolveAll()
        yes()
        pay()
        alice.casts(doom, target = FinSet.vallaCheerfulPulsebearer)
        resolveAll()
        targets(charm, ogre)
        expect {
            charm.zone shouldBe Zone.Graveyard
            ogre.zone shouldBe Zone.Battlefield
            alice.graveyard has FinSet.fennaFearlessPulsebearer
            alice.graveyard has FinSet.vallaCheerfulPulsebearer
        }
    }

    @Test
    fun vallaMillsAndReturnsAPermanent() = scenario {
        alice.library(ogre, notes, notes)
        alice.battlefield(*lands("Forest", 4))
        alice.hand(FinSet.vallaCheerfulPulsebearer)
        alice.casts(FinSet.vallaCheerfulPulsebearer)
        resolveAll()
        choose(ogre)
        expect { alice.hand has ogre }
    }

    @Test
    fun fennaDrawsWhenCardsLeaveTheGraveyard() = scenario {
        libraries()
        alice.battlefield(FinSet.fennaFearlessPulsebearer, *lands("Swamp", 5))
        alice.graveyard(ogre)
        alice.hand(FinSet.wickedReawakening)
        alice.casts(FinSet.wickedReawakening, target = ogre)
        resolveAll()
        expect {
            alice.hand.size shouldBe 1
            alice.life shouldBe 19
        }
    }
}
