package arcanum.engine.sets.eoe

import arcanum.api.CardType
import arcanum.api.Keyword
import arcanum.api.Supertype
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.eoe.EoeExtras.bear
import arcanum.engine.sets.eoe.EoeExtras.charge
import arcanum.engine.sets.eoe.EoeExtras.giant
import arcanum.engine.sets.eoe.EoeExtras.gizmo
import arcanum.engine.sets.eoe.EoeExtras.legend
import arcanum.engine.sets.eoe.EoeExtras.notes
import arcanum.engine.sets.eoe.EoeExtras.ogre
import arcanum.engine.sets.eoe.EoeExtras.p1p1
import arcanum.engine.sets.eoe.EoeExtras.trinket
import kotlin.test.Test

/**
 * Edge of Eternities chunks 01 and 02 in play: Spacecraft and Planets with their station abilities (CR 702.184) and striations (CR 721.2).
 * Arc-Charged Bladeship, Survey Probe Cutter's 8+ and the other "N+ | Flying" Spacecraft repeat the creature-at-N template of
 * [EoeMechanicsScenariosTest.stationBecomesACreature]; Coilwall Sweepcraft's surveil 2 as it enters is a covered keyword action.
 */
class EoeStationScenariosTest {
    private fun Scenario.libraries() {
        alice.library(notes, notes, notes, notes)
        bob.library(notes, notes, notes, notes)
    }

    // ---- chunk 01

    @Test
    fun stratosphericArboretumCountersAndFlyingTrampler() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest", bear)
        alice.hand(EoeSet.stratosphericArboretum)
        alice.casts(EoeSet.stratosphericArboretum)
        resolveAll()
        expect {
            bear.counters(p1p1) shouldBe 1
            EoeSet.stratosphericArboretum.types shouldBe listOf(CardType.Artifact)
        }
    }

    /** 10+: whenever you attack, 100 damage to up to one target creature; 20+: a 20/20 flier. */
    // CR 721.2a, b
    @Test
    fun highsunDreadnoughtFiresWhenYouAttack() = scenario {
        libraries()
        alice.battlefield(EoeSet.highsunStarwardDreadnought, counters = listOf(charge to 10))
        alice.battlefield(bear)
        bob.battlefield(giant)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        resolveAll()
        targets(giant)
        expect {
            giant.zone shouldBe Zone.Graveyard
            EoeSet.highsunStarwardDreadnought.types shouldBe listOf(CardType.Artifact)
        }
    }

    @Test
    fun highsunDreadnoughtAtTwentyIsACreature() = scenario {
        alice.battlefield(EoeSet.highsunStarwardDreadnought, counters = listOf(charge to 20))
        expect {
            EoeSet.highsunStarwardDreadnought.power shouldBe 20
            EoeSet.highsunStarwardDreadnought hasKeyword Keyword.Flying
        }
    }

    @Test
    fun wreckageBeltGrinderShootsAndPumps() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(EoeSet.wreckageBeltGrinder)
        alice.casts(EoeSet.wreckageBeltGrinder)
        resolveAll()
        targets("bob")
        expect { bob.life shouldBe 17 }
        alice.activates(EoeSet.wreckageBeltGrinder, ability = 1)
        resolveAll()
        expect { EoeSet.wreckageBeltGrinder.power shouldBe null }
    }

    /** 1+: an opponent who discards loses 3 life; its attack makes each opponent discard, or lose 3 life without a card. */
    @Test
    fun decayingWarcruiserPunishesDiscards() = scenario {
        libraries()
        alice.battlefield(EoeSet.decayingWarcruiser, counters = listOf(charge to 8))
        bob.hand(notes)
        advanceTo(TurnStep.DeclareAttackers, turn = 3)
        alice.attacks(EoeSet.decayingWarcruiser)
        resolveAll()
        if (pending != null) bob.chooses(notes)
        resolveAll()
        expect {
            bob.graveyard has notes
            bob.life shouldBe 17
        }
    }

    @Test
    fun decayingWarcruiserWithAnEmptyHand() = scenario {
        libraries()
        alice.battlefield(EoeSet.decayingWarcruiser, counters = listOf(charge to 8))
        advanceTo(TurnStep.DeclareAttackers, turn = 3)
        alice.attacks(EoeSet.decayingWarcruiser)
        resolveAll()
        expect { bob.life shouldBe 17 }
    }

    @Test
    fun snufferClassWarshipDestroysAndSweeps() = scenario {
        alice.battlefield(*Array(8) { "Plains" })
        alice.hand(EoeSet.snufferClassWarship)
        bob.battlefield(EoeExtras.charm, bear, giant)
        alice.casts(EoeSet.snufferClassWarship)
        resolveAll()
        targets(EoeExtras.charm)
        expect {
            EoeExtras.charm.zone shouldBe Zone.Graveyard
            bear.zone shouldBe Zone.Graveyard
            giant.zone shouldBe Zone.Battlefield
        }
    }

    @Test
    fun grimGravityBargeMillsAndReturns() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.hand(EoeSet.grimGravityBarge)
        alice.library(bear, notes, notes, notes)
        alice.casts(EoeSet.grimGravityBarge)
        resolveAll()
        choose(bear)
        expect { alice.hand has bear }
    }

    /** 2+: other creatures you control get +1/+1; 12+: it's a 3/5 flier with lifelink. */
    // CR 721.2a
    @Test
    fun radianceClassCutterAnthem() = scenario {
        alice.battlefield(EoeSet.radianceClassCutter, bear)
        alice.activates(EoeSet.radianceClassCutter, choosing = listOf(bear))
        resolveAll()
        expect {
            bear.power shouldBe 3
            EoeSet.radianceClassCutter.counters(charge) shouldBe 2
        }
    }

    @Test
    fun apexHunterFrigateDealsTen() = scenario {
        alice.battlefield(*Array(7) { "Plains" })
        alice.hand(EoeSet.apexHunterFrigate)
        bob.battlefield(giant)
        alice.casts(EoeSet.apexHunterFrigate)
        resolveAll()
        targets(giant)
        expect { giant.zone shouldBe Zone.Graveyard }
    }

    @Test
    fun salvageLifeboatReturnsACreature() = scenario {
        alice.battlefield(*Array(6) { "Plains" })
        alice.hand(EoeSet.salvageLifeboat)
        alice.graveyard(giant)
        alice.casts(EoeSet.salvageLifeboat)
        resolveAll()
        targets(giant)
        expect { giant.zone shouldBe Zone.Battlefield }
    }

    /** Tutors a legendary creature; other tapped legendary creatures you control have indestructible. */
    @Test
    fun halcyonWingFindsALegend() = scenario {
        alice.battlefield("Plains", "Plains", "Plains")
        alice.battlefield(legend, tapped = true)
        alice.hand(EoeSet.theHalcyonWing)
        alice.library(notes, EoeExtras.legend, notes)
        alice.casts(EoeSet.theHalcyonWing)
        resolveAll()
        choose(EoeExtras.legend)
        expect {
            alice.hand has EoeExtras.legend
            card(legend, 0) hasKeyword Keyword.Indestructible
        }
    }

    @Test
    fun surveyProbeCutterLoots() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island")
        alice.hand(EoeSet.surveyProbeCutter, notes)
        alice.library(bear, ogre)
        alice.casts(EoeSet.surveyProbeCutter)
        resolveAll()
        choose(notes)
        expect {
            alice.hand has bear
            alice.hand has ogre
            alice.graveyard has notes
        }
    }

    @Test
    fun warforgerGunboatCountsArtifacts() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", trinket)
        alice.hand(EoeSet.warforgerGunboat)
        bob.battlefield(ogre)
        alice.casts(EoeSet.warforgerGunboat)
        resolveAll()
        targets(ogre)
        expect { ogre.zone shouldBe Zone.Battlefield }
        expect { card(ogre).view.obj?.damage shouldBe 2 }
    }

    @Test
    fun prismwedgeRamshipBringsARobot() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains")
        alice.hand(EoeSet.prismwedgeRamship)
        alice.casts(EoeSet.prismwedgeRamship)
        resolveAll()
        expect { alice.battlefield has "Robot Token" }
    }

    // ---- chunk 02

    /** 12+: copy an artifact or enchantment you control, except the copy is legendary. */
    // CR 707.9b
    @Test
    fun corvessaCopiesAnArtifactAsLegendary() = scenario {
        alice.battlefield(EoeSet.corvessaGaleboundBastion, counters = listOf(charge to 12))
        alice.battlefield("Plains", "Plains", "Plains", "Plains", gizmo)
        alice.activates(EoeSet.corvessaGaleboundBastion, ability = 2, target = gizmo)
        resolveAll()
        expect {
            alice.battlefield.names.count { it == gizmo } shouldBe 2
            card(gizmo, 1).view.characteristics!!.supertypes shouldBe listOf(Supertype.Legendary)
        }
    }

    @Test
    fun corvessaEntersTapped() = scenario {
        alice.hand(EoeSet.corvessaGaleboundBastion)
        alice.plays(EoeSet.corvessaGaleboundBastion)
        expect { EoeSet.corvessaGaleboundBastion.tapped shouldBe true }
    }

    /** 12+: {G}, {T}: add {G} for each creature you control. */
    @Test
    fun thessivarAddsGreenPerCreature() = scenario {
        alice.battlefield(EoeSet.thessivarStirringHaven, counters = listOf(charge to 12))
        alice.battlefield("Forest", bear, ogre, giant)
        alice.hand(EoeExtras.warper)
        alice.activates(EoeSet.thessivarStirringHaven, ability = 2)
        resolveAll()
        expect { alice.pool.size shouldBe 3 }
    }

    @Test
    fun endlessWaymarkCitadelRobotsAndCards() = scenario {
        libraries()
        alice.battlefield(*Array(5) { "Plains" }.let { arrayOf("Plains", "Island", "Swamp", "Mountain", "Forest") })
        alice.battlefield(EoeSet.velunirSlumberingNovice)
        alice.hand(EoeSet.endlessWaymarkCitadel)
        alice.casts(EoeSet.endlessWaymarkCitadel)
        resolveAll()
        expect {
            alice.battlefield.names.count { it == "Robot Token" } shouldBe 2
            card("Robot Token").tapped shouldBe true
        }
    }

    /** 12+: a Robot, then creatures you control get +1/+0 and haste. */
    @Test
    fun pyrrhosRemembranceWorldRallies() = scenario {
        alice.battlefield(EoeSet.pyrrhosRemembranceWorld, counters = listOf(charge to 12))
        alice.battlefield("Mountain", "Mountain", "Forest", bear)
        alice.activates(EoeSet.pyrrhosRemembranceWorld, ability = 2, choosing = listOf("Forest"))
        resolveAll()
        expect {
            alice.battlefield has "Robot Token"
            bear.power shouldBe 3
            card("Robot Token").view.characteristics!!.power shouldBe 3
        }
    }

    @Test
    fun grublingPathfinderTradesALander() = scenario {
        alice.battlefield("Forest", "Forest", "Forest")
        alice.hand(EoeSet.grublingPathfinderCraft)
        alice.library("Forest", "Island", notes)
        alice.casts(EoeSet.grublingPathfinderCraft)
        resolveAll()
        yes()
        choose("Forest")
        choose("Forest", "Island")
        expect {
            card("Island").zone shouldBe Zone.Battlefield
            card("Island").tapped shouldBe true
        }
    }

    @Test
    fun maulClassSowingArkDropsACreature() = scenario {
        libraries()
        alice.battlefield(EoeSet.maulClassSowingArk, counters = listOf(charge to 7))
        alice.hand(giant)
        advanceTo(TurnStep.DeclareAttackers, turn = 3)
        alice.attacks(EoeSet.maulClassSowingArk)
        resolveAll()
        choose(giant)
        expect { giant.zone shouldBe Zone.Battlefield }
    }

    @Test
    fun sampleHaulerBouncesAndMills() = scenario {
        libraries()
        alice.battlefield(*Array(6) { "Island" })
        alice.hand(EoeSet.sampleHauler)
        bob.battlefield(bear, ogre)
        alice.casts(EoeSet.sampleHauler)
        resolveAll()
        targets(bear, ogre)
        expect {
            bob.hand has bear
            bob.hand has ogre
        }
    }

    @Test
    fun sampleHaulerAttackMills() = scenario {
        libraries()
        bob.library(notes, notes, notes)
        alice.battlefield(EoeSet.sampleHauler, counters = listOf(charge to 9))
        advanceTo(TurnStep.DeclareAttackers, turn = 3)
        alice.attacks(EoeSet.sampleHauler)
        resolveAll()
        expect { bob.graveyard.size shouldBe 4 }
    }

    /** 12+: pay 2 life and sacrifice a creature: draw cards equal to its power. */
    @Test
    fun nyxaraTertiaDrawsByPower() = scenario {
        alice.battlefield(EoeSet.nyxaraTertiaHollowAltar, counters = listOf(charge to 12))
        alice.battlefield("Swamp", "Swamp", ogre)
        alice.library(notes, notes, notes, notes)
        alice.activates(EoeSet.nyxaraTertiaHollowAltar, ability = 2, choosing = listOf(ogre))
        resolveAll()
        expect {
            alice.hand.size shouldBe 3
            alice.life shouldBe 18
        }
    }

    @Test
    fun murmuringRequiemBargeEdicts() = scenario {
        alice.battlefield(*Array(5) { "Swamp" })
        alice.hand(EoeSet.murmuringRequiemBarge)
        bob.battlefield(bear)
        alice.casts(EoeSet.murmuringRequiemBarge)
        resolveAll()
        expect { bear.zone shouldBe Zone.Graveyard }
    }

    /** 2+: at the beginning of combat, another artifact becomes a 2/2 flier until end of turn. */
    @Test
    fun fabricatorTestbedAnimates() = scenario {
        libraries()
        alice.battlefield(EoeSet.fabricatorTestbed, counters = listOf(charge to 2))
        alice.battlefield(trinket)
        advanceTo(TurnStep.BeginningOfCombat)
        resolveAll()
        targets(trinket)
        expect {
            trinket.power shouldBe 2
            trinket hasKeyword Keyword.Flying
        }
    }

    /** {T}: {C}{C}{C}; 20+: X mana of one color, X the charge counters on it. */
    @Test
    fun perpetualLiftMana() = scenario {
        alice.battlefield(EoeSet.thePerpetualLift, counters = listOf(charge to 21))
        alice.activates(EoeSet.thePerpetualLift, ability = 2, color = arcanum.api.Color.Green)
        expect { alice.pool.size shouldBe 21 }
    }

    @Test
    fun maeltharColossalGodcoreBlueByArtifacts() = scenario {
        alice.battlefield(EoeSet.maeltharColossalGodcore, counters = listOf(charge to 12))
        alice.battlefield("Island", trinket, gizmo)
        alice.activates(EoeSet.maeltharColossalGodcore, ability = 2)
        expect { alice.pool.size shouldBe 2 }
    }
}
