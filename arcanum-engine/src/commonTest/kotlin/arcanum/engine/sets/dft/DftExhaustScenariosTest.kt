package arcanum.engine.sets.dft

import arcanum.api.CardType
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.dft.DftPlain.bear
import arcanum.engine.sets.dft.DftPlain.counterspell
import arcanum.engine.sets.dft.DftPlain.elf
import arcanum.engine.sets.dft.DftPlain.giant
import arcanum.engine.sets.dft.DftPlain.gizmo
import arcanum.engine.sets.dft.DftPlain.golem
import arcanum.engine.sets.dft.DftPlain.kart
import arcanum.engine.sets.dft.DftPlain.notes
import arcanum.engine.sets.dft.DftPlain.ogre
import arcanum.engine.sets.dft.DftPlain.p1p1
import arcanum.engine.sets.dft.DftPlain.trinket
import arcanum.engine.sets.dft.DftPlain.zap
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Aetherdrift parser chunks 08 and 09 in play: exhaust (CR 702.177) and the blue cards. Skydash Engineer, Prowcatcher Expert, Peril of the
 * Dunes, Greenbelt Warden and Sharp-Eyed Buccaneer (in [DftVehiclesScenariosTest]) only put counters with an exhaust ability like Stampeding
 * Scurrier; Pacesetter Champion and Draconautic Engineer add covered effects to it; Bounce Away, Spell Puncture, Spectral Obstruction (as
 * Distraction Unit), Gearhunter Serpent, Recall Guardian and Nimble Thoptersmith use covered templates; Lotuswing Disciple is a covered
 * enters pump.
 */
class DftExhaustScenariosTest {
    @Test
    fun pathlightSynergistCountsArtifacts() = scenario {
        alice.battlefield(DftSet.pathlightSynergist, trinket, gizmo)
        expect { DftSet.pathlightSynergist.power shouldBe 3 }
    }

    // CR 601.2f: {3} less with a tapped target
    @Test
    fun journeysHaltIsCheapAgainstATappedPermanent() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.hand(DftSet.journeysHalt)
        bob.battlefield(ogre)
        change(arcanum.engine.change.Changes.tap(state, card(ogre)))
        alice.casts(DftSet.journeysHalt, target = ogre)
        resolveAll()
        expect { ogre.zone shouldBe Zone.Exile }
    }

    @Test
    fun voyagerSteelwelderDiscountsArtifacts() = scenario {
        alice.battlefield(DftSet.voyagerSteelwelder, "Plains")
        alice.hand(gizmo)
        alice.casts(gizmo)
        resolveAll()
        expect { gizmo.zone shouldBe Zone.Battlefield }
    }

    // CR 107.3a: X cards and a counter on each Merfolk
    @Test
    fun mindbrookMerfolkDrawsX() = scenario {
        alice.battlefield(DftSet.mindbrookMerfolk, "Island", "Island", "Island", "Island")
        alice.library(notes, notes, notes)
        alice.activates(DftSet.mindbrookMerfolk, x = 2)
        resolveAll()
        expect {
            alice.hand.size shouldBe 2
            DftSet.mindbrookMerfolk.counters(p1p1) shouldBe 1
        }
    }

    @Test
    fun riverwashMonumentMillsTargets() = scenario {
        alice.battlefield(DftSet.riverwashMonument, "Island", "Island", "Island", "Island", "Island")
        bob.library(notes, notes, notes, notes, notes)
        alice.library(notes, notes)
        alice.activates(DftSet.riverwashMonument, ability = 0, targets = listOf("bob"))
        resolveAll()
        expect { bob.graveyard.size shouldBe 2 }
        advanceTo(TurnStep.PrecombatMain, turn = 3)
        alice.activates(DftSet.riverwashMonument, ability = 1, targets = listOf("bob"))
        resolveAll()
        expect { bob.graveyard.size shouldBe 4 }
    }

    @Test
    fun sabotageTacticianShrinksAttackers() = scenario {
        libraries()
        alice.battlefield(ogre)
        bob.battlefield(DftSet.sabotageTactician)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(ogre)
        resolveAll()
        expect { ogre.power shouldBe 2 }
    }

    // CR 603.4: only for a spell cast in another player's turn
    @Test
    fun adrenalineRiderPunishesOffTurnSpells() = scenario {
        alice.battlefield(DftSet.adrenalineRider)
        bob.battlefield("Mountain")
        bob.hand(zap)
        alice.passes()
        bob.casts(zap, target = "alice")
        resolveAll()
        expect { bob.life shouldBe 16 }
    }

    @Test
    fun adrenalineRiderGrowsOnExhaust() = scenario {
        alice.battlefield(DftSet.adrenalineRider, DftSet.stampedingScurrier, *Array(4) { "Forest" })
        alice.activates(DftSet.stampedingScurrier)
        resolveAll()
        expect {
            DftSet.adrenalineRider.counters(p1p1) shouldBe 1
            DftSet.stampedingScurrier.counters(p1p1) shouldBe 1
            "Elephant Token".zone shouldBe Zone.Battlefield
        }
    }

    // CR 113.6c: the trigger returns it from the graveyard
    @Test
    fun afterburnerAdeptReturnsOnExhaust() = scenario {
        alice.battlefield(DftSet.stampedingScurrier, *Array(4) { "Forest" })
        alice.graveyard(DftSet.afterburnerAdept)
        alice.activates(DftSet.stampedingScurrier)
        resolveAll()
        expect { DftSet.afterburnerAdept.zone shouldBe Zone.Battlefield }
    }

    // CR 601.2f, 602.2b: other permanents' exhaust abilities cost {2} less
    @Test
    fun boomProfessorDiscountsExhaust() = scenario {
        alice.battlefield(DftSet.boomProfessor, DftSet.stampedingScurrier, "Forest", "Forest")
        alice.activates(DftSet.stampedingScurrier)
        resolveAll()
        expect { DftSet.stampedingScurrier.counters(p1p1) shouldBe 1 }
    }

    @Test
    fun shutterLauncherMakesAThopter() = scenario {
        alice.battlefield(DftSet.shutterLauncher, "Plains", "Plains", "Plains")
        alice.activates(DftSet.shutterLauncher)
        resolveAll()
        expect { "Thopter Token" hasKeyword Keyword.Flying.kind }
    }

    // CR 603.7, 707.10: the next exhaust ability this turn is copied
    @Test
    fun pitAutomataCopiesTheNextExhaust() = scenario {
        alice.battlefield(DftSet.pitAutomata, DftSet.stampedingScurrier, *Array(6) { "Forest" })
        alice.activates(DftSet.pitAutomata, ability = 1)
        resolveAll()
        alice.activates(DftSet.stampedingScurrier)
        resolveAll()
        expect { DftSet.stampedingScurrier.counters(p1p1) shouldBe 2 }
    }

    @Test
    fun greasegripGoblinRummages() = scenario {
        alice.battlefield(DftSet.greasegripGoblin, "Mountain", "Mountain", "Mountain")
        alice.hand(bear, ogre)
        alice.library(notes, notes)
        alice.activates(DftSet.greasegripGoblin)
        resolveAll()
        choose(bear, ogre)
        expect {
            alice.hand.size shouldBe 2
            alice.hand has notes
            DftSet.greasegripGoblin.counters(p1p1) shouldBe 1
        }
    }

    // CR 118.12a: countered unless its controller pays {3}
    @Test
    fun distractionUnitCountersUnlessPaid() = scenario {
        alice.battlefield(DftSet.distractionUnit, "Island")
        bob.battlefield("Mountain")
        bob.hand(zap)
        alice.passes()
        bob.casts(zap, target = "alice")
        bob.passes()
        alice.activates(DftSet.distractionUnit, target = zap)
        resolveAll()
        (pending as? Decision.May)?.let { answer(Answer.YesNo(false)); resolveAll() }
        expect { alice.life shouldBe 20 }
    }

    // CR 613.1f: it loses its abilities and stays tapped
    @Test
    fun drownTheEngineLocksAPermanent() = scenario {
        libraries()
        alice.battlefield("Island", "Island", "Island")
        alice.hand(DftSet.drownTheEngine)
        bob.battlefield(DftPlain.hawk)
        alice.casts(DftSet.drownTheEngine, target = DftPlain.hawk)
        resolveAll()
        expect {
            DftPlain.hawk.tapped shouldBe true
            DftPlain.hawk lacksKeyword Keyword.Flying.kind
        }
        advanceTo(TurnStep.Draw, turn = 2)
        expect { DftPlain.hawk.tapped shouldBe true }
    }

    // CR 106.6: the mana pays for an artifact spell
    @Test
    fun pathlightOptimizerPaysForArtifacts() = scenario {
        alice.battlefield(DftSet.pathlightOptimizer)
        alice.hand(trinket)
        alice.casts(trinket)
        resolveAll()
        expect { trinket.zone shouldBe Zone.Battlefield }
    }

    // CR 608.2h: one more than the sacrificed artifact's mana value
    @Test
    fun recyclingBayUpgradesAnArtifact() = scenario {
        alice.battlefield(DftSet.recyclingBay, trinket, "Island", "Island")
        alice.library(gizmo, notes)
        alice.activates(DftSet.recyclingBay, choosing = listOf(trinket))
        resolveAll()
        choose(gizmo)
        expect { gizmo.zone shouldBe Zone.Battlefield }
    }

    @Test
    fun waysideBlowoutIsCheapAgainstAOneDrop() = scenario {
        alice.battlefield("Island")
        alice.hand(DftSet.waysideBlowout)
        alice.library(notes)
        bob.battlefield(elf)
        alice.casts(DftSet.waysideBlowout, target = elf)
        resolveAll()
        expect {
            elf.zone shouldBe Zone.Hand
            alice.hand has notes
        }
    }

    @Test
    fun stockPileKeepsTwo() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.hand(DftSet.stockPile)
        alice.library(bear, ogre, giant, elf, notes)
        alice.casts(DftSet.stockPile)
        resolveAll()
        choose(giant, ogre)
        expect {
            alice.hand has giant
            alice.hand has ogre
        }
    }

    @Test
    fun transitAdeptFindsAFourDropArtifact() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.hand(DftSet.transitAdept)
        alice.library(DftSet.racersLeaderboard, notes)
        alice.casts(DftSet.transitAdept)
        resolveAll()
        yes()
        choose(DftSet.racersLeaderboard)
        expect { alice.hand has DftSet.racersLeaderboard }
    }

    @Test
    fun unrelentingPlanUntapsAtTheEndStep() = scenario {
        libraries()
        alice.battlefield(DftSet.unrelentingPlan, golem)
        change(arcanum.engine.change.Changes.tap(state, card(golem)))
        advanceTo(TurnStep.End)
        resolveAll()
        expect { golem.tapped shouldBe false }
    }

    // CR 702.177a: three different exhaust abilities, each once
    @Test
    fun luteUsesEachExhaust() = scenario {
        alice.battlefield(DftSet.luteTheWayfinder, "Island", "Mountain")
        alice.library(notes, notes, notes)
        alice.activates(DftSet.luteTheWayfinder, ability = 1)
        resolveAll()
        expect { alice.hand.size shouldBe 3 }
    }

    // CR 106.6: mana by its power, for abilities only; then permanents from the hand
    @Test
    fun redstreakPutsPermanentsFromHand() = scenario {
        alice.battlefield(DftSet.redstreakRocketeerBoss, *Array(10) { "Mountain" }, "Forest")
        alice.hand(giant, ogre)
        alice.activates(DftSet.redstreakRocketeerBoss, ability = 1)
        resolveAll()
        choose(giant, ogre)
        expect {
            giant.zone shouldBe Zone.Battlefield
            ogre.zone shouldBe Zone.Battlefield
        }
    }

    // CR 611.2c: the others' base power and toughness fixed at its power as the ability resolves
    @Test
    fun sitaraVarmaSetsOthersToItsPower() = scenario {
        alice.battlefield(DftSet.sitaraVarmaMaskedRacer, elf, "Forest", "Forest", "Forest", "Island")
        alice.activates(DftSet.sitaraVarmaMaskedRacer, x = 1)
        resolveAll()
        yes()
        expect {
            DftSet.sitaraVarmaMaskedRacer.power shouldBe 3
            elf.power shouldBe 3
        }
    }

    // CR 701.20: lands found go onto the battlefield tapped, the rest to the bottom
    @Test
    fun skyserpentSearcherFindsTwoLands() = scenario {
        alice.battlefield(DftSet.skyserpentSearcher, *Array(4) { "Forest" })
        alice.library(notes, "Island", bear, "Island", notes)
        alice.activates(DftSet.skyserpentSearcher)
        resolveAll()
        expect {
            card("Island", 0).zone shouldBe Zone.Battlefield
            card("Island", 1).tapped shouldBe true
            DftSet.skyserpentSearcher.counters(p1p1) shouldBe 1
        }
    }

    // CR 702.21a: ward that costs life; CR 107.3a: X artifact cards exiled
    @Test
    fun wintraShrinksByExiledArtifacts() = scenario {
        alice.battlefield(DftSet.wintraCursedRider, "Island", "Swamp", "Island", "Swamp")
        alice.graveyard(trinket, gizmo)
        bob.battlefield(ogre, golem)
        alice.activates(DftSet.wintraCursedRider, x = 2, choosing = listOf(trinket, gizmo))
        resolveAll()
        expect {
            ogre.power shouldBe 1
            golem.power shouldBe 3
        }
    }
}
