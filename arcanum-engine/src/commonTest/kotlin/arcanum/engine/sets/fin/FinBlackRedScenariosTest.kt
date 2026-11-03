package arcanum.engine.sets.fin

import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
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
import arcanum.engine.sets.fin.FinExtras.stun
import arcanum.engine.sets.fin.FinExtras.trinket
import arcanum.engine.sets.fin.FinExtras.zap
import kotlin.test.Test

/**
 * Final Fantasy chunks 11 and 12 in play: the black and red cards. Underway Rat's dies Treasure, Rotmaw's discard-and-drain and
 * Warding Wall's counters use tested templates; Judgment Strike's and Barrick Wallace's Equipment counts repeat Edge of Dawn's.
 */
class FinBlackRedScenariosTest {
    private fun lands(land: String, n: Int) = Array(n) { land }

    // ---- chunk 11

    @Test
    fun wickedReawakeningReturnsWithTwoCounters() = scenario {
        alice.battlefield(*lands("Swamp", 5))
        alice.graveyard(ogre)
        alice.hand(FinSet.wickedReawakening)
        alice.casts(FinSet.wickedReawakening, target = ogre)
        resolveAll()
        expect {
            ogre.zone shouldBe Zone.Battlefield
            ogre.counters(p1p1) shouldBe 2
        }
    }

    // CR 613.1b: a player gains control by its trigger
    @Test
    fun kaelTraitorousLancerChangesSidesAndPays() = scenario {
        libraries()
        alice.battlefield(FinSet.kaelTraitorousLancer)
        advanceTo(TurnStep.DeclareAttackers)
        expect { FinSet.kaelTraitorousLancer hasKeyword Keyword.Flying }
        alice.attacks(FinSet.kaelTraitorousLancer)
        resolveAll()
        advanceTo(TurnStep.CombatDamage)
        resolveAll()
        expect {
            FinSet.kaelTraitorousLancer.controller shouldBe bob.id
            alice.hand.size shouldBe 2
            alice.battlefield.names.count { it == "Treasure Token" } shouldBe 2
            alice.life shouldBe 18
        }
    }

    @Test
    fun ardaanCopiesAGraveyardCreatureAsADemon() = scenario {
        libraries()
        alice.battlefield(FinSet.ardaanTheUsurper)
        bob.graveyard(bear)
        advanceTo(TurnStep.BeginningOfCombat)
        resolveAll()
        targets(bear)
        expect {
            alice.battlefield has bear
            card(bear, 1).power shouldBe 5
            bob.graveyard.isEmpty()
        }
    }

    @Test
    fun knifeGoblinEntersStunnedAndStrikesFirstOnYourTurn() = scenario {
        alice.battlefield("Swamp")
        alice.hand(FinSet.knifeGoblin)
        alice.casts(FinSet.knifeGoblin)
        resolveAll()
        expect {
            FinSet.knifeGoblin.tapped shouldBe true
            FinSet.knifeGoblin.counters(stun) shouldBe 1
            FinSet.knifeGoblin hasKeyword Keyword.FirstStrike
            FinSet.knifeGoblin hasKeyword Keyword.Deathtouch
        }
    }

    @Test
    fun wardingWallAttacksWithACounter() = scenario {
        libraries()
        alice.battlefield(FinSet.wardingWall, *lands("Swamp", 6))
        alice.activates(FinSet.wardingWall)
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(FinSet.wardingWall)
        resolveAll()
        expect { FinSet.wardingWall.tapped shouldBe true }
    }

    @Test
    fun theLastDaysFromTheGraveyardCountsCreatures() = scenario {
        alice.battlefield(*lands("Swamp", 6))
        alice.graveyard(FinSet.theLastDays, bear, ogre, giant)
        alice.casts(FinSet.theLastDays)
        resolveAll()
        expect { alice.battlefield.names.count { it == "Horror Token" } shouldBe 3 }
    }

    @Test
    fun theLastDaysMakesTwoHorrors() = scenario {
        alice.battlefield(*lands("Swamp", 4))
        alice.hand(FinSet.theLastDays)
        alice.casts(FinSet.theLastDays)
        resolveAll()
        expect { alice.battlefield.names.count { it == "Horror Token" } shouldBe 2 }
    }

    @Test
    fun ghostlyLocomotiveBecomesASpirit() = scenario {
        alice.battlefield(FinSet.ghostlyLocomotive, trinket)
        alice.activates(FinSet.ghostlyLocomotive, choosing = listOf(trinket))
        resolveAll()
        expect {
            FinSet.ghostlyLocomotive.counters(p1p1) shouldBe 1
            (arcanum.api.CardType.Creature in FinSet.ghostlyLocomotive.types) shouldBe true
            FinSet.ghostlyLocomotive.power shouldBe 5
        }
    }

    @Test
    fun rotmawPunishesOpponents() = scenario {
        bob.library(notes, notes, notes, notes)
        bob.hand(ogre)
        alice.battlefield(*lands("Swamp", 6))
        alice.hand(FinSet.rotmaw)
        alice.casts(FinSet.rotmaw)
        resolveAll()
        expect {
            bob.graveyard has ogre
            bob.life shouldBe 18
            bob.exile.size shouldBe 3
        }
    }

    @Test
    fun renzAndRookPlaysTheExiledCardWithAnyMana() = scenario {
        alice.library(notes)
        bob.library(ogre, notes)
        alice.battlefield(FinSet.renzAndRook, bear, "Swamp", "Swamp", "Swamp")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(FinSet.renzAndRook)
        resolveAll()
        advanceTo(TurnStep.CombatDamage)
        resolveAll()
        yes()
        choose(bear)
        advanceTo(TurnStep.PostcombatMain)
        alice.casts(ogre)
        resolveAll()
        expect { ogre.zone shouldBe Zone.Battlefield }
    }

    @Test
    fun fatalEmbraceDrawsForEachDeath() = scenario {
        libraries()
        alice.battlefield(*lands("Swamp", 6), bear)
        alice.hand(FinSet.fatalEmbrace, doom)
        bob.battlefield(ogre)
        alice.casts(doom, target = bear)
        resolveAll()
        alice.casts(FinSet.fatalEmbrace, target = ogre)
        resolveAll()
        expect { alice.hand.size shouldBe 2 }
    }

    // CR 614.1a: instead exile it and you gain 2 life
    @Test
    fun theShadowCrystalExilesDyingCreaturesAndBringsThemBack() = scenario {
        alice.battlefield(FinSet.theShadowCrystal, *lands("Swamp", 7))
        alice.hand(doom)
        bob.battlefield(ogre)
        alice.casts(doom, target = ogre)
        resolveAll()
        expect {
            ogre.zone shouldBe Zone.Exile
            alice.life shouldBe 22
        }
        alice.activates(FinSet.theShadowCrystal, target = ogre)
        resolveAll()
        expect {
            ogre.controller shouldBe alice.id
            ogre.counters(p1p1) shouldBe 2
            ogre.tapped shouldBe true
        }
    }

    @Test
    fun zodrakHalvesEveryoneAndGrowsOnSacrifices() = scenario {
        alice.battlefield(*lands("Swamp", 5), bear, flier)
        alice.hand(FinSet.zodrakUmbralGod)
        bob.battlefield(ogre, giant, charm)
        alice.casts(FinSet.zodrakUmbralGod)
        resolveAll()
        choose(bear)
        choose(ogre)
        expect {
            bear.zone shouldBe Zone.Graveyard
            ogre.zone shouldBe Zone.Graveyard
            FinSet.zodrakUmbralGod.counters(p1p1) shouldBe 2
        }
    }

    // ---- chunk 12

    @Test
    fun frejaFliesOnYourTurnAndPaysForEquipment() = scenario {
        alice.battlefield(FinSet.frejaCrescentia)
        alice.hand(lance)
        alice.casts(lance)
        resolveAll()
        expect {
            lance.zone shouldBe Zone.Battlefield
            FinSet.frejaCrescentia hasKeyword Keyword.Flying
        }
    }

    @Test
    fun gearShopkeeperGivesMenace() = scenario {
        libraries()
        alice.battlefield(FinSet.gearShopkeeper, bear, lance)
        attach(lance, bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        resolveAll()
        expect { bear hasKeyword Keyword.Menace }
    }

    @Test
    fun selfDetonateHitsBothWays() = scenario {
        alice.battlefield(ogre, "Mountain", "Mountain")
        alice.hand(FinSet.selfDetonate)
        bob.battlefield(bear)
        alice.casts(FinSet.selfDetonate, targets = listOf(ogre, bear))
        resolveAll()
        expect {
            bear.zone shouldBe Zone.Graveyard
            ogre.zone shouldBe Zone.Graveyard
        }
    }

    @Test
    fun gildemarPutsEquipmentOntoTheBattlefieldAndAttaches() = scenario {
        alice.library(lance, notes, notes, notes, notes, notes)
        bob.library(notes)
        alice.battlefield(*lands("Mountain", 6))
        alice.hand(FinSet.gildemarMasterAtArms)
        alice.casts(FinSet.gildemarMasterAtArms)
        resolveAll()
        choose(lance)
        choose(lance)
        if (pending is arcanum.engine.decision.Decision.ChooseObjects) choose(FinSet.gildemarMasterAtArms)
        if (pending is arcanum.engine.decision.Decision.Targets) targets(FinSet.gildemarMasterAtArms)
        expect {
            lance.zone shouldBe Zone.Battlefield
            FinSet.gildemarMasterAtArms.power shouldBe 7
        }
    }

    @Test
    fun quickeningSpellPumpsAndExilesToPlay() = scenario {
        alice.library(ogre, notes)
        alice.battlefield(bear, *lands("Mountain", 5))
        alice.hand(FinSet.quickeningSpell)
        alice.casts(FinSet.quickeningSpell, target = bear)
        resolveAll()
        alice.casts(ogre)
        resolveAll()
        expect {
            bear.power shouldBe 5
            bear hasKeyword Keyword.Haste
            ogre.zone shouldBe Zone.Battlefield
        }
    }

    @Test
    fun unexpectedSummonsBorrowsACreatureAndLendsAnEquipment() = scenario {
        libraries()
        alice.battlefield(lance, *lands("Mountain", 3))
        alice.hand(FinSet.unexpectedSummons)
        bob.battlefield(ogre, tapped = true)
        alice.casts(FinSet.unexpectedSummons, target = ogre)
        resolveAll()
        choose(lance)
        expect {
            ogre.controller shouldBe alice.id
            ogre.tapped shouldBe false
            ogre.power shouldBe 4
        }
        advanceTo(TurnStep.End)
        resolveAll()
        expect { ogre.power shouldBe 3 }
    }

    // CR 702.21a: ward with a life cost
    @Test
    fun rauvanWardCostsLife() = scenario {
        alice.battlefield(FinSet.rauvanBullOfTheHighlands)
        bob.battlefield("Mountain")
        bob.hand(zap)
        turn(active = bob)
        bob.casts(zap, target = FinSet.rauvanBullOfTheHighlands)
        resolveAll()
        yes()
        expect {
            bob.life shouldBe 18
            FinSet.rauvanBullOfTheHighlands.zone shouldBe Zone.Graveyard
        }
    }

    @Test
    fun rauvanAttachesEquipmentToAnAttacker() = scenario {
        libraries()
        alice.battlefield(FinSet.rauvanBullOfTheHighlands, bear, lance)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(FinSet.rauvanBullOfTheHighlands, bear)
        resolveAll()
        targets(lance, bear)
        expect { bear.power shouldBe 3 }
    }

    @Test
    fun sorceresssPlotsReturnsASpellAndAddsMana() = scenario {
        alice.battlefield(*lands("Mountain", 4))
        alice.graveyard(zap)
        alice.hand(FinSet.sorceresssPlots)
        alice.casts(FinSet.sorceresssPlots, target = zap)
        resolveAll()
        expect {
            alice.hand has zap
            alice.pool.size shouldBe 1
        }
    }

    @Test
    fun ferrionCopiesEquipmentCheaply() = scenario {
        libraries()
        alice.battlefield(FinSet.ferrionWildRoseRebel, bear, "Mountain")
        alice.hand(lance)
        alice.casts(lance)
        resolveAll()
        expect { alice.battlefield.names.count { it == lance } shouldBe 2 }
        advanceTo(TurnStep.Upkeep, turn = 2)
        resolveAll()
        expect { alice.battlefield.names.count { it == lance } shouldBe 1 }
    }

    @Test
    fun tripleTriumphPlaysExiledCardsFree() = scenario {
        alice.library(giant, notes)
        bob.library(bear, notes)
        alice.battlefield(FinSet.tripleTriumph)
        turn(step = TurnStep.Untap)
        advanceTo(TurnStep.Upkeep)
        resolveAll()
        advanceTo(TurnStep.PrecombatMain)
        alice.casts(giant)
        resolveAll()
        alice.casts(bear)
        resolveAll()
        expect {
            giant.zone shouldBe Zone.Battlefield
            bear.zone shouldBe Zone.Battlefield
            bear.controller shouldBe alice.id
        }
    }

    @Test
    fun judgmentStrikeHitsTheController() = scenario {
        alice.battlefield(lance, FinSet.mendersCrook, *lands("Mountain", 4))
        alice.hand(FinSet.judgmentStrike)
        bob.battlefield(giant)
        alice.casts(FinSet.judgmentStrike, target = giant)
        resolveAll()
        expect {
            giant.zone shouldBe Zone.Graveyard
            bob.life shouldBe 18
        }
    }

    @Test
    fun barrickWallaceBurnsForEquippedCreatures() = scenario {
        libraries()
        alice.battlefield(FinSet.barrickWallace, bear, lance)
        attach(lance, bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(FinSet.barrickWallace)
        resolveAll()
        expect { bob.life shouldBe 19 }
    }

    @Test
    fun suddenEncounterBringsMilledCreaturesForATurn() = scenario {
        alice.library(ogre, bear, notes, notes)
        bob.library(notes)
        alice.battlefield(*lands("Mountain", 6))
        alice.hand(FinSet.suddenEncounter)
        random({ kind -> arcanum.engine.decision.Answer.RandomObjects((kind as arcanum.engine.decision.RandomKind.Objects).among) })
        alice.casts(FinSet.suddenEncounter)
        resolveAll()
        expect {
            ogre.zone shouldBe Zone.Battlefield
            ogre hasKeyword Keyword.Haste
        }
        advanceTo(TurnStep.End)
        resolveAll()
        expect { alice.hand has ogre }
    }

    @Test
    fun judgmentsLightDestroysAnAttachedEquipment() = scenario {
        alice.battlefield(*lands("Mountain", 5))
        alice.hand(FinSet.judgmentsLight)
        bob.battlefield(bear, lance)
        attach(lance, bear)
        alice.casts(FinSet.judgmentsLight, target = bear)
        resolveAll()
        choose(lance)
        expect {
            bear.zone shouldBe Zone.Graveyard
            lance.zone shouldBe Zone.Graveyard
        }
    }

    @Test
    fun vanoStealsTheTopCardAndGrowsScouts() = scenario {
        alice.library(notes)
        bob.library(ogre, notes)
        alice.battlefield(FinSet.vanoStreetThief, "Mountain", "Mountain", "Mountain")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(FinSet.vanoStreetThief)
        resolveAll()
        advanceTo(TurnStep.CombatDamage)
        resolveAll()
        yes()
        pay()
        expect {
            ogre.zone shouldBe Zone.Battlefield
            FinSet.vanoStreetThief.counters(p1p1) shouldBe 1
        }
    }
}
