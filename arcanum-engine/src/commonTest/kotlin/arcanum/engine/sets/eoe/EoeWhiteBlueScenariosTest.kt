package arcanum.engine.sets.eoe

import arcanum.api.CardType
import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.eoe.EoeExtras.bear
import arcanum.engine.sets.eoe.EoeExtras.charm
import arcanum.engine.sets.eoe.EoeExtras.doom
import arcanum.engine.sets.eoe.EoeExtras.flier
import arcanum.engine.sets.eoe.EoeExtras.giant
import arcanum.engine.sets.eoe.EoeExtras.gizmo
import arcanum.engine.sets.eoe.EoeExtras.notes
import arcanum.engine.sets.eoe.EoeExtras.ogre
import arcanum.engine.sets.eoe.EoeExtras.p1p1
import arcanum.engine.sets.eoe.EoeExtras.shuttle
import arcanum.engine.sets.eoe.EoeExtras.trinket
import arcanum.engine.sets.eoe.EoeExtras.zap
import kotlin.test.Test

/**
 * Edge of Eternities chunks 07 to 10 in play: the white and blue cards. Exiling Radiance and Rift Tear repeat Every-Path Prowler's
 * exile-until-it-leaves ([EoeWarpScenariosTest.everyPathProwlerExilesUntilItLeaves]); Launch-Bay Marshal and Vanguard Battle-Brute
 * repeat Daybreak Spearhead's "two or more tapped creatures" end step; Gleaming Blow (destroy, gain life) and Drifter Windedge (flash,
 * flying, sacrifice to draw) use covered effects; Mistshaper Mechanic and Drifter Agent repeat tested artifact-static and second-spell
 * templates.
 */
class EoeWhiteBlueScenariosTest {
    private fun Scenario.libraries() {
        alice.library(notes, notes, notes, notes, notes)
        bob.library(notes, notes, notes, notes, notes)
    }

    // ---- chunk 07

    @Test
    fun backupThrustersCarryARobot() = scenario {
        alice.battlefield(*Array(5) { "Plains" })
        alice.hand(EoeSet.backupThrusters)
        alice.casts(EoeSet.backupThrusters)
        resolveAll()
        expect {
            "Robot Token".power shouldBe 3
            "Robot Token" hasKeyword Keyword.Flying
        }
    }

    @Test
    fun pastTheSilenceExilesCreaturesAndSpacecraft() = scenario {
        alice.battlefield(*Array(5) { "Plains" }, shuttle, bear)
        alice.hand(EoeSet.pastTheSilence)
        bob.battlefield(giant, gizmo)
        alice.casts(EoeSet.pastTheSilence)
        resolveAll()
        expect {
            shuttle.zone shouldBe Zone.Exile
            bear.zone shouldBe Zone.Exile
            giant.zone shouldBe Zone.Exile
            gizmo.zone shouldBe Zone.Battlefield
        }
    }

    /** +2/+0 as long as you've cast two or more spells this turn. */
    @Test
    fun glowlanceDevoteeAfterTwoSpells() = scenario {
        alice.battlefield(EoeSet.glowlanceDevotee, "Island", "Island")
        alice.hand(notes, notes)
        alice.library(notes, notes)
        alice.casts(notes)
        resolveAll()
        expect { EoeSet.glowlanceDevotee.power shouldBe 2 }
        alice.casts(notes)
        resolveAll()
        expect { EoeSet.glowlanceDevotee.power shouldBe 4 }
    }

    @Test
    fun starhighApexSecondSpellModes() = scenario {
        alice.battlefield(EoeSet.starhighApex, "Island", "Island", bear)
        alice.hand(notes, notes)
        alice.library(notes, notes)
        alice.casts(notes)
        resolveAll()
        alice.casts(notes)
        resolveAll()
        modes(1)
        expect { bear.counters(p1p1) shouldBe 1 }
    }

    @Test
    fun daybreakSpearheadWithTappedCreatures() = scenario {
        libraries()
        alice.battlefield(EoeSet.daybreakSpearhead, bear, ogre)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear, ogre)
        advanceTo(TurnStep.End)
        resolveAll()
        expect {
            bear.counters(p1p1) shouldBe 1
            EoeSet.daybreakSpearhead.counters(p1p1) shouldBe 0
        }
    }

    @Test
    fun hangarLoaderDroneMovesItsCounters() = scenario {
        alice.battlefield("Plains", "Plains", bear)
        alice.hand(EoeSet.hangarLoaderDrone)
        bob.battlefield("Swamp")
        bob.hand(doom)
        alice.casts(EoeSet.hangarLoaderDrone)
        resolveAll()
        expect { EoeSet.hangarLoaderDrone.power shouldBe 2 }
        alice.passes()
        bob.casts(doom, target = EoeSet.hangarLoaderDrone)
        resolveAll()
        targets(bear)
        expect { bear.counters(p1p1) shouldBe 1 }
    }

    /** Double strike; a card if it has a +1/+1 counter. */
    @Test
    fun twinStarFormDrawsWithACounter() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.battlefield(bear, counters = listOf(p1p1 to 1))
        alice.hand(EoeSet.twinStarForm)
        alice.library(notes)
        alice.casts(EoeSet.twinStarForm, target = bear)
        resolveAll()
        expect {
            bear hasKeyword Keyword.DoubleStrike
            alice.hand has notes
        }
    }

    @Test
    fun twinStarFormNoCardWithoutACounter() = scenario {
        alice.battlefield("Plains", "Plains", bear)
        alice.hand(EoeSet.twinStarForm)
        alice.library(notes)
        alice.casts(EoeSet.twinStarForm, target = bear)
        resolveAll()
        expect { alice.hand lacks notes }
    }

    @Test
    fun powersuitRescuerBouncesYourPermanent() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", bear)
        alice.hand(EoeSet.powersuitRescuer)
        alice.casts(EoeSet.powersuitRescuer)
        resolveAll()
        targets(bear)
        expect { alice.hand has bear }
    }

    /** X is 2 plus your creatures and Spacecraft, to an attacking or blocking creature. */
    @Test
    fun concentratedBarrageCountsCreaturesAndSpacecraft() = scenario {
        libraries()
        alice.battlefield("Plains", bear, ogre, shuttle)
        alice.hand(EoeSet.concentratedBarrage)
        bob.battlefield(giant)
        turn(active = bob, step = TurnStep.DeclareAttackers)
        bob.attacks(giant)
        bob.passes()
        alice.casts(EoeSet.concentratedBarrage, target = giant)
        resolveAll()
        expect { giant.zone shouldBe Zone.Graveyard }
    }

    /** Exiles a creature until it leaves; the enchanted artifact has ward {1}. */
    @Test
    fun solidLightPrisonOnAnArtifact() = scenario {
        alice.battlefield("Plains", trinket)
        alice.hand(EoeSet.solidLightPrison)
        bob.battlefield(giant)
        alice.casts(EoeSet.solidLightPrison, targets = listOf(trinket))
        resolveAll()
        targets(giant)
        expect {
            giant.zone shouldBe Zone.Exile
            trinket hasKeyword Keyword.Ward.Kind
        }
    }

    @Test
    fun decoratedKnightMarshalFetchesEquipment() = scenario {
        alice.battlefield(*Array(6) { "Plains" }, EoeSet.decoratedKnightMarshal)
        alice.library(notes, EoeSet.pagesGlowblade, notes)
        alice.activates(EoeSet.decoratedKnightMarshal)
        resolveAll()
        choose(EoeSet.pagesGlowblade)
        expect { EoeSet.pagesGlowblade.zone shouldBe Zone.Battlefield }
    }

    // ---- chunk 08

    /** Each opponent exiles a card from hand and may play it; spells cost {1} more, lands enter tapped. */
    @Test
    fun glowstallInterrogatorLetsTheOpponentPlayIt() = scenario {
        libraries()
        alice.battlefield("Plains")
        alice.hand(EoeSet.glowstallInterrogator)
        bob.battlefield("Mountain", "Mountain")
        bob.hand(zap)
        alice.casts(EoeSet.glowstallInterrogator)
        resolveAll()
        expect { zap.zone shouldBe Zone.Exile }
        advanceTo(TurnStep.PrecombatMain, turn = 2)
        bob.casts(zap, target = "alice")
        resolveAll()
        expect {
            alice.life shouldBe 18
            card("Mountain", 1).tapped shouldBe true
        }
    }

    @Test
    fun glowstallInterrogatorLandEntersTapped() = scenario {
        libraries()
        alice.battlefield("Plains")
        alice.hand(EoeSet.glowstallInterrogator)
        bob.hand("Swamp")
        alice.casts(EoeSet.glowstallInterrogator)
        resolveAll()
        advanceTo(TurnStep.PrecombatMain, turn = 2)
        bob.plays("Swamp")
        expect { card("Swamp").tapped shouldBe true }
    }

    @Test
    fun lumenknightVanguardCountsCreaturesAndArtifacts() = scenario {
        alice.battlefield(*Array(4) { "Plains" }, bear, trinket)
        alice.hand(EoeSet.lumenknightVanguard)
        alice.casts(EoeSet.lumenknightVanguard)
        resolveAll()
        expect { EoeSet.lumenknightVanguard.counters(p1p1) shouldBe 2 }
    }

    /** Exiles cheap artifacts and creatures until it leaves; its ability buries them and makes Robots. */
    @Test
    fun summitStarprisonCagesAndBuries() = scenario {
        alice.battlefield(*Array(11) { "Plains" })
        alice.hand(EoeSet.summitStarprison)
        bob.battlefield(bear, trinket, giant)
        alice.casts(EoeSet.summitStarprison)
        resolveAll()
        expect {
            bear.zone shouldBe Zone.Exile
            trinket.zone shouldBe Zone.Exile
            giant.zone shouldBe Zone.Battlefield
        }
        alice.activates(EoeSet.summitStarprison)
        resolveAll()
        expect {
            bear.zone shouldBe Zone.Graveyard
            alice.battlefield.names.count { it == "Robot Token" } shouldBe 2
            EoeSet.summitStarprison.zone shouldBe Zone.Graveyard
        }
    }

    @Test
    fun quasarWingAceCounterWithoutASpacecraft() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.hand(EoeSet.quasarWingAce)
        alice.library(notes, notes, notes, notes, notes)
        alice.casts(EoeSet.quasarWingAce)
        resolveAll()
        choose()
        expect { EoeSet.quasarWingAce.counters(p1p1) shouldBe 1 }
    }

    @Test
    fun quasarWingAceFindsASpacecraft() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.hand(EoeSet.quasarWingAce)
        alice.library(notes, shuttle, notes, notes, notes)
        alice.casts(EoeSet.quasarWingAce)
        resolveAll()
        choose(shuttle)
        expect {
            alice.hand has shuttle
            EoeSet.quasarWingAce.counters(p1p1) shouldBe 0
        }
    }

    @Test
    fun redirectCircuitsDamagesATappedCreature() = scenario {
        alice.battlefield("Plains")
        alice.hand(EoeSet.redirectCircuits)
        bob.battlefield(bear, tapped = true)
        alice.casts(EoeSet.redirectCircuits, modes = listOf(1), target = bear)
        resolveAll()
        expect { bear.zone shouldBe Zone.Graveyard }
    }

    /** Up to three creature cards with total mana value 3 or less return with a counter each. */
    @Test
    fun searchTheWreckageReturnsCheapCreatures() = scenario {
        alice.battlefield("Plains", "Plains", "Plains")
        alice.hand(EoeSet.searchTheWreckage)
        alice.graveyard(bear, flier)
        alice.casts(EoeSet.searchTheWreckage, targets = listOf(bear))
        resolveAll()
        expect {
            bear.zone shouldBe Zone.Battlefield
            bear.counters(p1p1) shouldBe 1
        }
    }

    @Test
    fun pagesGlowbladeAttachesAndStrikesFirst() = scenario {
        alice.battlefield("Plains", bear)
        alice.hand(EoeSet.pagesGlowblade)
        alice.casts(EoeSet.pagesGlowblade)
        resolveAll()
        targets(bear)
        expect {
            bear.power shouldBe 3
            bear hasKeyword Keyword.FirstStrike
        }
    }

    @Test
    fun dogfighterAceSurveilsWhenTapped() = scenario {
        libraries()
        alice.battlefield(EoeSet.dogfighterAce)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(EoeSet.dogfighterAce)
        resolveAll()
        choose(notes)
        expect { alice.graveyard has notes }
    }

    /** Costs {2} less with a creature with a +1/+1 counter. */
    // CR 601.2f
    @Test
    fun spacedockGuardCheaperWithACounter() = scenario {
        alice.battlefield(EoeSet.spacedockGuard, "Plains", "Plains")
        alice.battlefield(bear, counters = listOf(p1p1 to 1))
        bob.battlefield(giant)
        alice.activates(EoeSet.spacedockGuard, target = giant)
        resolveAll()
        expect { giant.tapped shouldBe true }
    }

    @Test
    fun daystarClericTapsWithACounter() = scenario {
        alice.battlefield(EoeSet.daystarCleric, "Plains", "Plains")
        alice.battlefield(bear, counters = listOf(p1p1 to 1))
        bob.battlefield(giant)
        alice.activates(EoeSet.daystarCleric, target = giant, choosing = listOf(bear))
        resolveAll()
        expect {
            giant.tapped shouldBe true
            bear.counters(p1p1) shouldBe 0
        }
    }

    @Test
    fun daystarGlowsmithSecondSpell() = scenario {
        alice.battlefield(EoeSet.daystarGlowsmith, "Island", "Island")
        alice.hand(notes, notes)
        alice.library(notes, notes, notes)
        alice.casts(notes)
        resolveAll()
        alice.casts(notes)
        resolveAll()
        expect {
            EoeSet.daystarGlowsmith.counters(p1p1) shouldBe 1
            alice.hand.size shouldBe 3
        }
    }

    /** On an opponent's turn, the pumped creatures untap too. */
    @Test
    fun ferventShowingUntapsOffTurn() = scenario {
        alice.battlefield("Plains", "Plains", "Plains")
        alice.battlefield(bear, tapped = true)
        alice.hand(EoeSet.ferventShowing)
        turn(active = bob, priority = alice)
        alice.casts(EoeSet.ferventShowing)
        resolveAll()
        expect {
            bear.power shouldBe 4
            bear.tapped shouldBe false
        }
    }

    // ---- chunk 09

    @Test
    fun subatomicShrinkerShrinksAndUnblocks() = scenario {
        libraries()
        alice.battlefield(EoeSet.subatomicShrinker, bear)
        attach(EoeSet.subatomicShrinker, bear)
        bob.battlefield(giant)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        resolveAll()
        targets(giant)
        expect {
            giant.power shouldBe 1
            giant.toughness shouldBe 1
        }
    }

    @Test
    fun neuralUploadSurveilsByArtifacts() = scenario {
        alice.battlefield(*Array(5) { "Island" }, trinket, gizmo)
        alice.hand(EoeSet.neuralUpload)
        alice.library(notes, notes, notes, notes, notes)
        alice.casts(EoeSet.neuralUpload)
        resolveAll()
        choose(notes, notes)
        expect {
            alice.graveyard.names.count { it == notes } shouldBe 2
            alice.hand.size shouldBe 3
        }
    }

    @Test
    fun readTheConstellationsKickedTakesTwo() = scenario {
        alice.battlefield(*Array(4) { "Island" })
        alice.hand(EoeSet.readTheConstellations)
        alice.library(bear, ogre, giant, notes)
        alice.casts(EoeSet.readTheConstellations, kicked = 1)
        resolveAll()
        choose(bear, ogre)
        expect {
            alice.hand has bear
            alice.hand has ogre
        }
    }

    @Test
    fun stasisRelicDrawsAndStuns() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island")
        alice.hand(EoeSet.stasisRelic)
        alice.library(notes, notes)
        bob.battlefield(giant, tapped = true)
        alice.casts(EoeSet.stasisRelic)
        resolveAll()
        expect { alice.hand.size shouldBe 1 }
        alice.activates(EoeSet.stasisRelic, target = giant)
        resolveAll()
        expect {
            giant.counters(CounterKind.Stun) shouldBe 1
            alice.hand.size shouldBe 2
        }
    }

    /** -5/-0; destroyed when it becomes tapped or is dealt damage. */
    @Test
    fun frostfractureDestroysWhenTapped() = scenario {
        libraries()
        alice.battlefield("Island")
        alice.hand(EoeSet.frostfracture)
        bob.battlefield(giant)
        alice.casts(EoeSet.frostfracture, target = giant)
        resolveAll()
        expect { giant.power shouldBe 0 }
        advanceTo(TurnStep.DeclareAttackers, turn = 2)
        bob.attacks(giant)
        resolveAll()
        expect { giant.zone shouldBe Zone.Graveyard }
    }

    @Test
    fun unmakingBurstOnAnAttacker() = scenario {
        libraries()
        alice.battlefield("Island", "Island")
        alice.hand(EoeSet.unmakingBurst)
        bob.battlefield(giant)
        turn(active = bob, step = TurnStep.DeclareAttackers)
        bob.attacks(giant)
        bob.passes()
        alice.casts(EoeSet.unmakingBurst, target = giant)
        resolveAll()
        expect {
            bob.hand has giant
            alice.battlefield has "Drone Token"
        }
    }

    @Test
    fun envoyBodyguardGreatestManaValue() = scenario {
        alice.battlefield(EoeSet.envoyBodyguard, trinket, gizmo)
        expect { EoeSet.envoyBodyguard.power shouldBe 3 }
    }

    @Test
    fun megatempestColossusCheaperAfterASpell() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.hand(notes, EoeSet.megatempestColossus)
        alice.library(notes)
        alice.casts(notes)
        resolveAll()
        alice.casts(EoeSet.megatempestColossus)
        resolveAll()
        expect { EoeSet.megatempestColossus.zone shouldBe Zone.Battlefield }
    }

    @Test
    fun drifterSneakUnblockableAfterTwoSpells() = scenario {
        alice.battlefield(EoeSet.drifterSneak, "Island", "Island")
        alice.hand(notes, notes)
        alice.library(notes, notes, notes)
        alice.casts(notes)
        resolveAll()
        alice.casts(notes)
        resolveAll()
        expect { EoeSet.drifterSneak hasMechanic { it == arcanum.api.Mechanic.CantBeBlocked } }
    }

    @Test
    fun drifterGlareScramblerHexproof() = scenario {
        alice.battlefield("Island", "Island", bear)
        alice.hand(EoeSet.drifterGlareScrambler)
        alice.casts(EoeSet.drifterGlareScrambler)
        resolveAll()
        targets(bear)
        expect {
            bear.toughness shouldBe 4
            bear hasKeyword Keyword.Hexproof
        }
    }

    @Test
    fun adriftInTheDarkTopOrBottom() = scenario {
        alice.battlefield(*Array(4) { "Island" })
        alice.hand(EoeSet.adriftInTheDark)
        alice.library(notes)
        bob.battlefield(giant)
        bob.library(notes)
        alice.casts(EoeSet.adriftInTheDark, target = giant)
        resolveAll()
        answer(arcanum.engine.decision.Answer.Option("bottom"))
        resolveAll()
        choose()
        expect { giant.zone shouldBe Zone.Library }
    }

    @Test
    fun voidOutCountersAnArtifactSpell() = scenario {
        alice.battlefield("Island")
        alice.hand(EoeSet.voidOut)
        bob.battlefield("Plains")
        bob.hand(trinket)
        turn(active = bob)
        bob.casts(trinket)
        bob.passes()
        alice.casts(EoeSet.voidOut, target = trinket)
        resolveAll()
        expect { trinket.zone shouldBe Zone.Graveyard }
    }

    // ---- chunk 10

    @Test
    fun automatonFabricatorOncePerTurn() = scenario {
        alice.battlefield(EoeSet.automatonFabricator, "Island", "Island")
        alice.hand(trinket, trinket)
        alice.casts(trinket)
        resolveAll()
        alice.casts(trinket)
        resolveAll()
        expect { alice.battlefield.names.count { it == "Robot Token" } shouldBe 1 }
    }

    @Test
    fun automatonHelmsmanLootsWhenTapped() = scenario {
        libraries()
        alice.battlefield(EoeSet.automatonHelmsman)
        alice.hand(giant)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(EoeSet.automatonHelmsman)
        resolveAll()
        choose(giant)
        expect { alice.graveyard has giant }
    }

    @Test
    fun automatonBulwarkAttacksAfterAnArtifact() = scenario {
        libraries()
        alice.battlefield(EoeSet.automatonBulwark, "Island")
        alice.hand(trinket)
        alice.casts(trinket)
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(EoeSet.automatonBulwark)
        advanceTo(TurnStep.End)
        expect { bob.life shouldBe 17 }
    }

    @Test
    fun mindTuningCheaperOnYourTurn() = scenario {
        alice.battlefield("Island")
        alice.hand(EoeSet.mindTuning)
        alice.library(notes)
        bob.battlefield(giant)
        alice.casts(EoeSet.mindTuning, target = giant)
        resolveAll()
        expect {
            giant.tapped shouldBe true
            alice.hand has notes
        }
    }

    /** Casts artifact spells from the top of the library; its artifacts tap for {U} for such spells. */
    @Test
    fun quellanCastsArtifactsFromTheTop() = scenario {
        alice.battlefield(EoeSet.quellanTheTrustedHand, "Island", "Island", "Island")
        alice.library(gizmo, notes)
        alice.casts(gizmo)
        resolveAll()
        expect { gizmo.zone shouldBe Zone.Battlefield }
    }

    /** The first token creation each turn may make copies of the enchanted permanent instead. */
    @Test
    fun lunarContemplationCopiesTheEnchanted() = scenario {
        alice.battlefield(EoeSet.lunarContemplation, giant, "Plains", "Plains", "Plains", "Plains")
        attach(EoeSet.lunarContemplation, giant)
        alice.hand(EoeSet.radiantSquireCommander)
        alice.casts(EoeSet.radiantSquireCommander)
        resolveAll()
        yes()
        expect { alice.battlefield.names.count { it == giant } shouldBe 2 }
    }

    @Test
    fun mawOfTheTempestWeakensUntilYourNextTurn() = scenario {
        libraries()
        alice.battlefield(*Array(7) { "Island" })
        alice.hand(EoeSet.mawOfTheTempest)
        bob.battlefield(giant)
        alice.casts(EoeSet.mawOfTheTempest)
        resolveAll()
        advanceTo(TurnStep.PrecombatMain, turn = 2)
        expect { giant.power shouldBe 2 }
        advanceTo(TurnStep.PrecombatMain, turn = 3)
        expect { giant.power shouldBe 5 }
    }

    @Test
    fun microframeWatcherUntapsAnother() = scenario {
        libraries()
        alice.battlefield(EoeSet.microframeWatcher)
        alice.battlefield("Island", tapped = true)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(EoeSet.microframeWatcher)
        resolveAll()
        targets("Island")
        expect { card("Island").tapped shouldBe false }
    }

    @Test
    fun siftForSalvageBothModes() = scenario {
        alice.battlefield(*Array(4) { "Island" })
        alice.hand(EoeSet.siftForSalvage)
        alice.library(notes, gizmo)
        alice.graveyard(trinket)
        alice.casts(EoeSet.siftForSalvage, modes = listOf(0, 1), target = trinket)
        resolveAll()
        choose(gizmo)
        expect {
            alice.hand has gizmo
            alice.hand has trinket
        }
    }

    @Test
    fun selfbuiltAutomatonReflexiveTrigger() = scenario {
        alice.battlefield(*Array(4) { "Island" }, trinket, bear)
        alice.hand(EoeSet.selfbuiltAutomaton)
        alice.library(notes)
        alice.casts(EoeSet.selfbuiltAutomaton)
        resolveAll()
        yes()
        choose(trinket)
        targets(bear)
        expect {
            bear.counters(p1p1) shouldBe 1
            alice.hand has notes
        }
    }

    /** {U}{U} only for abilities of artifact sources. */
    @Test
    fun ironflockControllerPaysAnArtifactAbility() = scenario {
        alice.battlefield(EoeSet.ironflockController, EoeSet.stasisRelic)
        alice.library(notes)
        bob.battlefield(giant, tapped = true)
        alice.activates(EoeSet.stasisRelic, target = giant)
        resolveAll()
        expect { giant.counters(CounterKind.Stun) shouldBe 1 }
    }

    /** Taps and steals a creature or Spacecraft; it doesn't untap. */
    @Test
    fun gravityTetherSteals() = scenario {
        alice.battlefield(*Array(4) { "Island" })
        alice.hand(EoeSet.gravityTether)
        bob.battlefield(giant)
        alice.casts(EoeSet.gravityTether, target = giant)
        resolveAll()
        expect {
            giant.controller shouldBe alice.id
            giant.tapped shouldBe true
        }
    }

    /** Draws when the countered spell's spent mana was less than its mana value. */
    @Test
    fun unspoolNoCardAgainstAPaidSpell() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.hand(EoeSet.unspool)
        alice.library(notes)
        bob.battlefield("Mountain")
        bob.hand(zap)
        turn(active = bob)
        bob.casts(zap, target = "alice")
        bob.passes()
        alice.casts(EoeSet.unspool, target = zap)
        resolveAll()
        expect {
            zap.zone shouldBe Zone.Graveyard
            alice.hand lacks notes
            alice.life shouldBe 20
        }
    }

    @Test
    fun maeltharMindbenderSecondSpellCheaper() = scenario {
        alice.battlefield(EoeSet.maeltharMindbender, "Island", "Mountain")
        alice.hand(notes, EoeExtras.ogre)
        alice.library(notes)
        alice.casts(notes)
        resolveAll()
        alice.casts(EoeExtras.ogre)
        resolveAll()
        expect { EoeExtras.ogre.zone shouldBe Zone.Battlefield }
    }

    /** A spell cast without paying its mana cost (spent less than its mana value) countered: a card. */
    @Test
    fun unspoolDrawsAgainstAFreeSpell() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.hand(EoeSet.unspool)
        alice.library(notes)
        bob.battlefield(EoeSet.loomstriding)
        bob.hand(zap)
        turn(active = bob)
        bob.casts(zap, target = "alice", way = permissionWay(bob, zap))
        bob.passes()
        alice.casts(EoeSet.unspool, target = zap)
        resolveAll()
        expect {
            alice.hand has notes
            alice.life shouldBe 20
        }
    }

    /** The first spell each player casts during their own turn is free. */
    @Test
    fun loomstridingFreeFirstSpell() = scenario {
        alice.battlefield(EoeSet.loomstriding)
        alice.hand(giant)
        alice.casts(giant, way = permissionWay(alice, giant))
        resolveAll()
        expect { giant.zone shouldBe Zone.Battlefield }
    }

    @Test
    fun loomstridingShufflesAndDrawsSeven() = scenario {
        alice.battlefield(*Array(6) { "Island" })
        alice.hand(EoeSet.loomstriding, bear)
        alice.graveyard(ogre)
        alice.library(*Array(8) { notes })
        alice.casts(EoeSet.loomstriding)
        resolveAll()
        expect { alice.hand.size shouldBe 7 }
    }
}
