package arcanum.engine.sets.eoe

import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.eoe.EoeExtras.bear
import arcanum.engine.sets.eoe.EoeExtras.charge
import arcanum.engine.sets.eoe.EoeExtras.charm
import arcanum.engine.sets.eoe.EoeExtras.flier
import arcanum.engine.sets.eoe.EoeExtras.giant
import arcanum.engine.sets.eoe.EoeExtras.gizmo
import arcanum.engine.sets.eoe.EoeExtras.kavu
import arcanum.engine.sets.eoe.EoeExtras.notes
import arcanum.engine.sets.eoe.EoeExtras.ogre
import arcanum.engine.sets.eoe.EoeExtras.p1p1
import arcanum.engine.sets.eoe.EoeExtras.shuttle
import arcanum.engine.sets.eoe.EoeExtras.trinket
import arcanum.engine.sets.eoe.EoeExtras.warper
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Edge of Eternities chunks 03 and 04 in play: the warp cards (CR 702.185) and what they do as they enter, cast for their warp costs
 * where it matters. Radiant Squire-Commander's Soldier, Riftsurge Leviathan's surveil 2, Sprouting Crawler's 2 life, Supernova Wyrm's 1
 * damage and Loombalde Augmenter's counters as they enter repeat tested enters templates; Forgotten Juggernaut and Crimson Panther
 * Automaton are warp and haste only (warp is tested in [EoeMechanicsScenariosTest]).
 */
class EoeWarpScenariosTest {
    private fun Scenario.libraries() {
        alice.library(notes, notes, notes, notes)
        bob.library(notes, notes, notes, notes)
    }

    // ---- chunk 03

    /** Exiles up to one non-Assassin creature until it leaves: cast for warp, the creature comes back at the end step. */
    // CR 610.3, 702.185a
    @Test
    fun everyPathProwlerExilesUntilItLeaves() = scenario {
        libraries()
        alice.battlefield("Plains", "Plains")
        alice.hand(EoeSet.everyPathProwler)
        bob.battlefield(giant)
        alice.casts(EoeSet.everyPathProwler, way = warpWay(EoeSet.everyPathProwler))
        resolveAll()
        targets(giant)
        expect { giant.zone shouldBe Zone.Exile }
        advanceTo(TurnStep.End)
        resolveAll()
        expect {
            EoeSet.everyPathProwler.zone shouldBe Zone.Exile
            giant.zone shouldBe Zone.Battlefield
        }
    }

    /** Returns a noncreature, nonland permanent card with mana value up to the mana spent (warp: 3). */
    // CR 601.2h
    @Test
    fun celestineRetrieverByManaSpent() = scenario {
        alice.battlefield("Plains", "Plains", "Plains")
        alice.hand(EoeSet.celestineRetriever)
        alice.graveyard(gizmo)
        alice.casts(EoeSet.celestineRetriever, way = warpWay(EoeSet.celestineRetriever))
        resolveAll()
        targets(gizmo)
        expect { gizmo.zone shouldBe Zone.Battlefield }
    }

    @Test
    fun cipherbreakerMuttOneOfTwo() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.hand(EoeSet.cipherbreakerMutt)
        alice.library(bear, ogre)
        alice.casts(EoeSet.cipherbreakerMutt)
        resolveAll()
        choose(ogre)
        expect {
            alice.hand has ogre
            alice.graveyard has bear
        }
    }

    /** Twice as many tokens are created under your control. */
    // CR 614.1a
    @Test
    fun glorifiedDawnchildDoublesTokens() = scenario {
        alice.battlefield(EoeSet.glorifiedDawnchild, "Plains", "Plains", "Plains", "Plains")
        alice.hand(EoeSet.radiantSquireCommander)
        alice.casts(EoeSet.radiantSquireCommander)
        resolveAll()
        expect { alice.battlefield.names.count { it == "Human Soldier Token" } shouldBe 2 }
    }

    /** Life for each creature or artifact entering (itself too); a card at the end step only after 3 or more life this turn. */
    @Test
    fun orlenaGainsAndDraws() = scenario {
        libraries()
        alice.battlefield(*Array(7) { "Plains" })
        alice.hand(EoeSet.orlenaLedByStarlight, trinket, gizmo)
        alice.casts(EoeSet.orlenaLedByStarlight)
        resolveAll()
        alice.casts(trinket)
        resolveAll()
        alice.casts(gizmo)
        resolveAll()
        expect { alice.life shouldBe 23 }
        advanceTo(TurnStep.End)
        resolveAll()
        expect { alice.hand.size shouldBe 1 }
    }

    @Test
    fun orlenaNoCardBelowThreeLife() = scenario {
        libraries()
        alice.battlefield(EoeSet.orlenaLedByStarlight, "Plains", "Plains", "Plains", "Plains")
        alice.hand(trinket, gizmo)
        alice.casts(trinket)
        resolveAll()
        alice.casts(gizmo)
        resolveAll()
        expect { alice.life shouldBe 22 }
        advanceTo(TurnStep.End)
        resolveAll()
        expect { alice.hand.size shouldBe 0 }
    }

    @Test
    fun clockworkMedusoidStuns() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.hand(EoeSet.clockworkMedusoid)
        bob.battlefield(giant)
        alice.casts(EoeSet.clockworkMedusoid, way = warpWay(EoeSet.clockworkMedusoid))
        resolveAll()
        targets(giant)
        expect {
            giant.tapped shouldBe true
            giant.counters(arcanum.api.CounterKind.Stun) shouldBe 1
        }
    }

    /** With one or fewer cards in hand, a draw instruction draws one more. */
    // CR 121.2a, 614.11
    @Test
    fun probabilitySphinxDrawsOneMore() = scenario {
        alice.battlefield(*Array(5) { "Island" })
        alice.hand(EoeSet.probabilitySphinx)
        alice.library(notes, notes, notes, notes)
        alice.casts(EoeSet.probabilitySphinx)
        resolveAll()
        expect { alice.hand.size shouldBe 2 }
    }

    /** A counter as it enters; a Soldier when a nontoken creature with a +1/+1 counter dies. */
    @Test
    fun beamswordTrooperReplacesTheFallen() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", bear)
        alice.hand(EoeSet.beamswordTrooper)
        bob.battlefield("Swamp")
        bob.hand(EoeExtras.doom)
        alice.casts(EoeSet.beamswordTrooper)
        resolveAll()
        targets(bear)
        expect { bear.counters(p1p1) shouldBe 1 }
        alice.passes()
        bob.casts(EoeExtras.doom, target = bear)
        resolveAll()
        expect { alice.battlefield has "Human Soldier Token" }
    }

    @Test
    fun malignFrostwrightShrinks() = scenario {
        alice.battlefield("Island")
        alice.hand(EoeSet.malignFrostwright)
        bob.battlefield(giant)
        alice.casts(EoeSet.malignFrostwright, way = warpWay(EoeSet.malignFrostwright))
        resolveAll()
        targets(giant)
        expect { giant.power shouldBe 2 }
    }

    @Test
    fun nebulaHerdwardenFindsAPlains() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.hand(EoeSet.nebulaHerdwarden)
        alice.library(notes, "Plains", bear)
        alice.casts(EoeSet.nebulaHerdwarden, way = warpWay(EoeSet.nebulaHerdwarden))
        resolveAll()
        choose("Plains")
        expect { assertTrue(alice.hand.names.contains("Plains")) }
    }

    /** A permanent entering makes a triggered ability of yours trigger an additional time. */
    // CR 603.2d
    @Test
    fun nebulaChoristerDoublesEntersTriggers() = scenario {
        alice.battlefield(EoeSet.nebulaChorister, "Plains", "Plains", "Plains", "Plains")
        alice.hand(EoeSet.radiantSquireCommander)
        alice.casts(EoeSet.radiantSquireCommander)
        resolveAll()
        expect { alice.battlefield.names.count { it == "Human Soldier Token" } shouldBe 2 }
    }

    @Test
    fun cometcoilerDrawsThatMany() = scenario {
        libraries()
        alice.library(notes, notes, notes, notes)
        alice.battlefield(EoeSet.cometcoiler, giant)
        advanceTo(TurnStep.DeclareAttackers, turn = 3)
        alice.attacks(giant)
        advanceTo(TurnStep.CombatDamage, turn = 3)
        resolveAll()
        yes()
        expect { alice.hand.size shouldBe 6 }
    }

    // ---- chunk 04

    /** As it leaves: draw, then put a permanent card with mana value up to your lands onto the battlefield tapped. */
    @Test
    fun reverseTimeRemnantLeavesAndDeploys() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains")
        alice.battlefield(EoeSet.reverseTimeRemnant)
        alice.hand(ogre)
        alice.library(notes)
        bob.battlefield("Swamp")
        bob.hand(EoeExtras.doom)
        alice.passes()
        bob.casts(EoeExtras.doom, target = EoeSet.reverseTimeRemnant)
        resolveAll()
        choose(ogre)
        expect {
            ogre.zone shouldBe Zone.Battlefield
            ogre.tapped shouldBe true
            alice.hand has notes
        }
    }

    /** Mode 2: an exiled card with warp goes to the bottom of its owner's library. */
    @Test
    fun edgeOfTheHiveBuriesAWarpCard() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(EoeSet.edgeOfTheHive)
        bob.exile(warper)
        alice.casts(EoeSet.edgeOfTheHive)
        resolveAll()
        modes(1)
        targets(warper)
        expect { warper.zone shouldBe Zone.Library }
    }

    @Test
    fun edgeOfTheHiveGrows() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(EoeSet.edgeOfTheHive)
        alice.casts(EoeSet.edgeOfTheHive)
        resolveAll()
        modes(0)
        expect { EoeSet.edgeOfTheHive.power shouldBe 5 }
    }

    /** X counters as it enters; as it leaves, its counters move to a creature you control. */
    @Test
    fun hivewatchVanguardPassesItsCounters() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", bear)
        alice.hand(EoeSet.hivewatchVanguard)
        bob.battlefield("Swamp")
        bob.hand(EoeExtras.doom)
        alice.casts(EoeSet.hivewatchVanguard, x = 2)
        resolveAll()
        expect { EoeSet.hivewatchVanguard.counters(p1p1) shouldBe 2 }
        alice.passes()
        bob.casts(EoeExtras.doom, target = EoeSet.hivewatchVanguard)
        resolveAll()
        targets(bear)
        expect { bear.counters(p1p1) shouldBe 2 }
    }

    /** The additional cost chooses a creature you control; damage equal to its power. */
    // CR 601.2f
    @Test
    fun nearCollisionWithAChosenCreature() = scenario {
        alice.battlefield("Forest", "Forest", giant)
        alice.hand(EoeSet.nearCollision)
        bob.battlefield(ogre)
        alice.casts(EoeSet.nearCollision, target = ogre, choosing = listOf(giant))
        resolveAll()
        expect { ogre.zone shouldBe Zone.Graveyard }
    }

    /** …or a warped creature card you own in exile. */
    // CR 702.185b
    @Test
    fun nearCollisionWithAWarpedCard() = scenario {
        libraries()
        alice.battlefield("Forest", "Forest", "Forest")
        alice.hand(warper, EoeSet.nearCollision)
        bob.battlefield(ogre)
        alice.casts(warper, way = warpWay(warper))
        resolveAll()
        advanceTo(TurnStep.End)
        resolveAll()
        advanceTo(TurnStep.PrecombatMain, turn = 3)
        alice.casts(EoeSet.nearCollision, target = ogre, choosing = listOf(warper))
        resolveAll()
        expect { ogre.zone shouldBe Zone.Graveyard }
    }

    @Test
    fun drixDestinyweaverTramplers() = scenario {
        alice.battlefield("Forest", "Forest", bear)
        alice.hand(EoeSet.drixDestinyweaver)
        alice.casts(EoeSet.drixDestinyweaver, way = warpWay(EoeSet.drixDestinyweaver))
        resolveAll()
        targets(bear)
        expect {
            bear hasKeyword Keyword.Trample
            EoeSet.drixDestinyweaver lacksKeyword Keyword.Trample
        }
    }

    @Test
    fun colonyArchitectureRobotPerLand() = scenario {
        alice.battlefield(EoeSet.colonyArchitecture)
        alice.hand("Forest")
        alice.plays("Forest")
        resolveAll()
        expect { alice.battlefield has "Robot Token" }
    }

    /** Cast for its warp cost, the creature also gains trample and haste. */
    // CR 702.185a
    @Test
    fun maximumThrustOnAWarpedCreature() = scenario {
        alice.battlefield("Forest", "Mountain")
        alice.hand(warper, EoeSet.maximumThrust)
        alice.casts(warper, way = warpWay(warper))
        resolveAll()
        alice.casts(EoeSet.maximumThrust, target = warper)
        resolveAll()
        expect {
            warper.power shouldBe 6
            warper hasKeyword Keyword.Haste
            warper hasKeyword Keyword.Trample
        }
    }

    @Test
    fun maximumThrustOnANormalCreature() = scenario {
        alice.battlefield("Mountain", bear)
        alice.hand(EoeSet.maximumThrust)
        alice.casts(EoeSet.maximumThrust, target = bear)
        resolveAll()
        expect {
            bear.power shouldBe 5
            bear lacksKeyword Keyword.Haste
        }
    }

    /** Twice the counters on a creature, Spacecraft or Planet you control. */
    // CR 614.1a
    @Test
    fun cargoBayStagingDoublesCounters() = scenario {
        alice.battlefield(EoeSet.cargoBayStaging, shuttle, giant)
        alice.activates(shuttle, choosing = listOf(giant))
        resolveAll()
        expect { shuttle.counters(charge) shouldBe 10 }
    }

    @Test
    fun fallenSquadCaptainOnYourTurn() = scenario {
        libraries()
        alice.battlefield(EoeSet.fallenSquadCaptain, bear)
        expect { bear.power shouldBe 3 }
        advanceTo(TurnStep.Upkeep, turn = 2)
        expect { bear.power shouldBe 2 }
    }

    /** Landfall: target creature's power is doubled (+X/+0, CR 701.10b). */
    @Test
    fun strengthshapeAttunerDoubles() = scenario {
        alice.battlefield(EoeSet.strengthshapeAttuner, giant)
        alice.hand("Forest")
        alice.plays("Forest")
        resolveAll()
        targets(giant)
        expect { giant.power shouldBe 10 }
    }

    /** Until end of turn +2/+0 and "When this creature dies, return it to the battlefield tapped". */
    @Test
    fun apogeeSummonerSavesACreature() = scenario {
        alice.battlefield("Swamp", "Swamp", bear)
        alice.hand(EoeSet.apogeeSummoner)
        bob.battlefield("Swamp")
        bob.hand(EoeExtras.doom)
        alice.casts(EoeSet.apogeeSummoner, way = warpWay(EoeSet.apogeeSummoner))
        resolveAll()
        targets(bear)
        expect { bear.power shouldBe 4 }
        alice.passes()
        bob.casts(EoeExtras.doom, target = bear)
        resolveAll()
        expect {
            bear.zone shouldBe Zone.Battlefield
            bear.tapped shouldBe true
        }
    }

    @Test
    fun summitEnvoyDronesForArtifacts() = scenario {
        alice.battlefield(EoeSet.summitEnvoy, "Plains")
        alice.hand(trinket)
        alice.casts(trinket)
        resolveAll()
        expect {
            alice.battlefield has "Drone Token"
            "Drone Token" hasKeyword Keyword.Flying
        }
    }

    /** Exiles the top card; it may be played while you control a Kavu. */
    @Test
    fun probabilityMechanicPlaysFromExile() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(EoeSet.probabilityMechanic)
        alice.library(kavu)
        alice.casts(EoeSet.probabilityMechanic, way = warpWay(EoeSet.probabilityMechanic))
        resolveAll()
        expect { kavu.zone shouldBe Zone.Exile }
        alice.casts(kavu)
        resolveAll()
        expect { kavu.zone shouldBe Zone.Battlefield }
    }

    /** Whenever it or another creature or artifact you control dies: drain 1. */
    @Test
    fun murmuringNullspawnDrains() = scenario {
        alice.battlefield(EoeSet.murmuringNullspawn, bear)
        bob.battlefield("Swamp")
        bob.hand(EoeExtras.doom)
        alice.passes()
        bob.casts(EoeExtras.doom, target = bear)
        resolveAll()
        targets("bob")
        expect {
            bob.life shouldBe 19
            alice.life shouldBe 21
        }
    }

    /** Artifact cards and red creature cards in your hand have warp {2}{R}; other creatures have haste. */
    // CR 702.185a
    @Test
    fun brakkosGivesWarp() = scenario {
        libraries()
        alice.battlefield(EoeSet.brakkosStalwartLieutenant, "Mountain", "Mountain", "Mountain", bear)
        alice.hand(EoeSet.snufferClassWarship)
        bob.battlefield(charm)
        expect { bear hasKeyword Keyword.Haste }
        alice.casts(EoeSet.snufferClassWarship, way = warpWay(EoeSet.snufferClassWarship))
        resolveAll()
        targets(charm)
        advanceTo(TurnStep.End)
        resolveAll()
        expect { EoeSet.snufferClassWarship.zone shouldBe Zone.Exile }
    }

    /** Cast from the graveyard with its warp ability ({B} and 2 life); exiled at the end step. */
    // CR 702.185a
    @Test
    fun chronolineReaperFromTheGraveyard() = scenario {
        libraries()
        alice.battlefield("Swamp")
        alice.graveyard(EoeSet.chronolineReaper)
        alice.casts(EoeSet.chronolineReaper, way = warpWay(EoeSet.chronolineReaper))
        resolveAll()
        expect {
            EoeSet.chronolineReaper.zone shouldBe Zone.Battlefield
            alice.life shouldBe 18
        }
        advanceTo(TurnStep.End)
        resolveAll()
        expect { EoeSet.chronolineReaper.zone shouldBe Zone.Exile }
    }

    @Test
    fun loomstalkerZealotPings() = scenario {
        alice.battlefield(EoeSet.loomstalkerZealot, "Plains")
        alice.hand(trinket)
        alice.casts(trinket)
        resolveAll()
        expect { bob.life shouldBe 19 }
    }
}
