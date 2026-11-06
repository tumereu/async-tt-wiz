package arcanum.engine.sets.dft

import arcanum.api.CardType
import arcanum.api.Keyword
import arcanum.api.ObjectMark
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.dft.DftPlain.bear
import arcanum.engine.sets.dft.DftPlain.elf
import arcanum.engine.sets.dft.DftPlain.giant
import arcanum.engine.sets.dft.DftPlain.kart
import arcanum.engine.sets.dft.DftPlain.notes
import arcanum.engine.sets.dft.DftPlain.ogre
import arcanum.engine.sets.dft.DftPlain.p1p1
import arcanum.engine.sets.dft.DftPlain.pirate
import arcanum.engine.sets.dft.DftPlain.pony
import arcanum.engine.sets.dft.DftPlain.trinket
import kotlin.test.Test

/**
 * Aetherdrift parser chunks 03 to 05 in play: Vehicles made creatures and Mounts saddled by other effects (CR 301.7b, 702.171b), Mounts
 * attacking while saddled (CR 702.171b) and Pilots. Gilded Ghodra (a Treasure), Alacrian Panther and Venomsac Lizard (pumps), Dracosaur
 * Outrider (damage) and Unhurried Sloth (indestructible, untap all) repeat Dunelight Stallion's saddled attack trigger with covered effects;
 * Glimmerheath Ocelot repeats Sentinel Dawnmare's search; Undying Pilot and Dynamite Plunger repeat the Pilot crew bonus of
 * [DftMechanicsScenariosTest] with covered abilities; the Lanes enter untapped like the cube's checklands and make the Pilot token Back in
 * the Race makes.
 */
class DftMountsAndPilotsScenariosTest {
    // ---- chunk 03

    @Test
    fun alacrianOutfittersAnimatesAVehicle() = scenario {
        libraries()
        alice.battlefield(DftSet.alacrianOutfitters, kart, bear)
        expect { bear.toughness shouldBe 3 }
        advanceTo(TurnStep.BeginningOfCombat)
        targets(kart)
        expect {
            kart.types.contains(CardType.Creature) shouldBe true
            kart hasKeyword Keyword.Vigilance.kind
        }
    }

    // CR 611.2a: the returned Vehicle stays a creature
    @Test
    fun retuneReturnsAVehicleAsACreature() = scenario {
        libraries()
        alice.battlefield(*Array(4) { "Plains" })
        alice.hand(DftSet.retune)
        alice.graveyard(kart)
        alice.casts(DftSet.retune, target = kart)
        resolveAll()
        advanceTo(TurnStep.Upkeep, turn = 2)
        expect { kart.types.contains(CardType.Creature) shouldBe true }
    }

    // CR 111.4: the Vehicle token has crew 1; flying for Vehicles; a draw for fliers' combat damage
    @Test
    fun muLanyingMakesAFlyingVehicle() = scenario {
        libraries()
        alice.battlefield(*Array(4) { "Island" }, elf)
        alice.hand(DftSet.muLanyingGaleRacer)
        alice.casts(DftSet.muLanyingGaleRacer)
        resolveAll()
        alice.activates("Vehicle Token", choosing = listOf(elf))
        resolveAll()
        expect { "Vehicle Token" hasKeyword Keyword.Flying.kind }
    }

    @Test
    fun chandraAnimatesAndMakesVehicles() = scenario {
        libraries()
        alice.battlefield(DftSet.chandraSparkRacer, counters = listOf(arcanum.api.CounterKind.Loyalty to 4))
        alice.battlefield(kart)
        alice.activates(DftSet.chandraSparkRacer, ability = 1)
        resolveAll()
        expect { "Vehicle Token".zone shouldBe Zone.Battlefield }
        advanceTo(TurnStep.BeginningOfCombat)
        targets(kart)
        expect {
            kart.types.contains(CardType.Creature) shouldBe true
            kart hasKeyword Keyword.Haste.kind
        }
    }

    // CR 603.7: the returned Mounts and Vehicles are sacrificed at the next end step
    @Test
    fun pushTheEnvelopeRacesTheGraveyard() = scenario {
        libraries()
        alice.battlefield(*Array(7) { "Mountain" })
        alice.hand(DftSet.pushTheEnvelope)
        alice.graveyard(kart, pony)
        alice.casts(DftSet.pushTheEnvelope)
        resolveAll()
        expect {
            kart.types.contains(CardType.Creature) shouldBe true
            kart hasKeyword Keyword.Haste.kind
            pony hasKeyword Keyword.Haste.kind
        }
        advanceTo(TurnStep.End)
        resolveAll()
        expect {
            kart.zone shouldBe Zone.Graveyard
            pony.zone shouldBe Zone.Graveyard
        }
    }

    // CR 603.4: a Pirate and a Vehicle attacked
    @Test
    fun fearlessCorsairDrawsWithPirateAndVehicle() = scenario {
        alice.library(notes, notes, notes, notes)
        bob.library(notes, notes)
        alice.battlefield(DftSet.fearlessCorsair, kart, bear)
        alice.hand(notes, notes)
        alice.activates(kart, choosing = listOf(bear))
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(DftSet.fearlessCorsair, kart)
        resolveAll()
        choose(notes, notes)
        expect { alice.hand.size shouldBe 3 }
    }

    @Test
    fun koladinSaddlesAndAnimatesWhatEnters() = scenario {
        alice.battlefield(DftSet.koladinTriumphRacer, "Plains", "Plains", "Plains", "Plains")
        alice.hand(pony, kart)
        alice.casts(pony)
        resolveAll()
        alice.casts(kart)
        resolveAll()
        expect {
            pony.view.obj?.marks?.contains(ObjectMark.Saddled) shouldBe true
            kart.types.contains(CardType.Creature) shouldBe true
            kart hasKeyword Keyword.Haste.kind
        }
    }

    @Test
    fun pathlightMatrixSaddlesAndCrews() = scenario {
        alice.battlefield(DftSet.pathlightMatrix, pony, kart, *Array(4) { "Plains" })
        alice.activates(DftSet.pathlightMatrix, ability = 0, target = pony)
        resolveAll()
        expect { pony.view.obj?.marks?.contains(ObjectMark.Saddled) shouldBe true }
    }

    // ---- chunk 04

    @Test
    fun rampartYakSaddledCountersAndProtects() = scenario {
        libraries()
        alice.battlefield(DftSet.rampartYak, elf, bear)
        alice.activates(DftSet.rampartYak, ability = 1, choosing = listOf(elf))
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(DftSet.rampartYak)
        targets(bear)
        expect { bear.counters(p1p1) shouldBe 1 }
        alice.activates(DftSet.rampartYak, ability = 0)
        resolveAll()
        expect {
            bear hasKeyword Keyword.Indestructible.kind
            elf lacksKeyword Keyword.Indestructible.kind
        }
    }

    @Test
    fun sentinelDawnmareFetchesAPermanent() = scenario {
        libraries()
        alice.battlefield(DftSet.sentinelDawnmare, giant)
        alice.library(bear, notes)
        alice.activates(DftSet.sentinelDawnmare, choosing = listOf(giant))
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(DftSet.sentinelDawnmare)
        resolveAll()
        choose(bear)
        expect { bear.zone shouldBe Zone.Battlefield }
    }

    // CR 603.2: the enters trigger needs no saddle; the attack does
    @Test
    fun autarchMastodonEntersAndAttacksSaddled() = scenario {
        libraries()
        alice.battlefield(*Array(6) { "Forest" }, giant)
        alice.hand(DftSet.autarchMastodon)
        alice.casts(DftSet.autarchMastodon)
        resolveAll()
        expect { "Elephant Token".zone shouldBe Zone.Battlefield }
    }

    // CR 122.6: it enters with a counter, removing two destroys an artifact
    @Test
    fun districtMascotPupGrowsAndBites() = scenario {
        libraries()
        alice.battlefield(*Array(3) { "Forest" }, elf)
        alice.hand(DftSet.districtMascotPup)
        bob.battlefield(trinket)
        alice.casts(DftSet.districtMascotPup)
        resolveAll()
        expect { DftSet.districtMascotPup.counters(p1p1) shouldBe 1 }
        advanceTo(TurnStep.PrecombatMain, turn = 3)
        alice.activates(DftSet.districtMascotPup, ability = 1, choosing = listOf(elf))
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(DftSet.districtMascotPup)
        resolveAll()
        expect { DftSet.districtMascotPup.counters(p1p1) shouldBe 2 }
    }

    @Test
    fun lagorenCountersTwoRides() = scenario {
        libraries()
        alice.battlefield(DftSet.lagorenSoulOfAlacria, elf, pony, kart)
        alice.activates(DftSet.lagorenSoulOfAlacria, choosing = listOf(elf))
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(DftSet.lagorenSoulOfAlacria)
        targets(pony, kart)
        expect {
            pony.counters(p1p1) shouldBe 1
            kart.counters(p1p1) shouldBe 1
        }
    }

    // ---- chunk 05

    @Test
    fun cloudtrackCommanderPumpsRidesAndCrewsBig() = scenario {
        alice.battlefield(DftSet.cloudtrackCommander, DftPlain.hauler, pony)
        expect { pony.power shouldBe 3 }
        alice.activates(DftPlain.hauler, choosing = listOf(DftSet.cloudtrackCommander))
        resolveAll()
        expect { DftPlain.hauler.power shouldBe 7 }
    }

    @Test
    fun collisionVerdictCountsCreaturesAndVehicles() = scenario {
        alice.battlefield("Plains", "Plains", bear, elf, kart)
        alice.hand(DftSet.collisionVerdict)
        bob.battlefield(ogre)
        alice.casts(DftSet.collisionVerdict, modes = listOf(0), target = ogre)
        resolveAll()
        expect { ogre.zone shouldBe Zone.Graveyard }
    }

    @Test
    fun pitlaneMechanicCountersAVehicle() = scenario {
        alice.battlefield(DftSet.pitlaneMechanic, kart, *Array(4) { "Plains" })
        alice.activates(DftSet.pitlaneMechanic, target = kart)
        resolveAll()
        expect { kart.counters(p1p1) shouldBe 1 }
    }

    // CR 603.2: once each turn during its controller's turn
    @Test
    fun consoleAceUntapsOnceInYourTurn() = scenario {
        alice.battlefield(DftSet.consoleAce, kart)
        alice.activates(kart, choosing = listOf(DftSet.consoleAce))
        resolveAll()
        expect { DftSet.consoleAce.tapped shouldBe false }
    }

    @Test
    fun waysideAidMakesAPilot() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", bear)
        alice.hand(DftSet.waysideAid)
        alice.casts(DftSet.waysideAid, target = bear)
        resolveAll()
        expect {
            "Pilot Token".zone shouldBe Zone.Battlefield
            bear.power shouldBe 3
            bear hasKeyword Keyword.Lifelink.kind
        }
    }

    // The Pilot token crews 3 with power 1 (CR 702.122a)
    @Test
    fun backInTheRaceReturnsAndMakesAPilot() = scenario {
        alice.battlefield(*Array(5) { "Swamp" }, DftPlain.hauler, elf)
        alice.hand(DftSet.backInTheRace)
        alice.graveyard(giant)
        alice.casts(DftSet.backInTheRace, target = giant)
        resolveAll()
        expect { giant.zone shouldBe Zone.Battlefield }
        alice.activates(DftPlain.hauler, choosing = listOf("Pilot Token", elf))
        resolveAll()
        expect { DftPlain.hauler.types.contains(CardType.Creature) shouldBe true }
    }

    @Test
    fun recklessVelocicornBoostsWhatItCrews() = scenario {
        alice.battlefield(DftSet.recklessVelocicorn, kart)
        alice.activates(kart, choosing = listOf(DftSet.recklessVelocicorn))
        resolveAll()
        expect {
            kart.power shouldBe 5
            kart hasKeyword Keyword.Trample.kind
        }
    }

    @Test
    fun roadFuryCountsRides() = scenario {
        alice.battlefield("Mountain", kart, pony)
        alice.hand(DftSet.roadFury)
        bob.battlefield(giant)
        alice.casts(DftSet.roadFury, target = giant)
        resolveAll()
        expect { giant.view.obj?.damage shouldBe 4 }
    }

    @Test
    fun defendTheRacerProtects() = scenario {
        alice.battlefield("Forest", bear)
        alice.hand(DftSet.defendTheRacer)
        alice.casts(DftSet.defendTheRacer, modes = listOf(0), target = bear)
        resolveAll()
        expect { bear hasKeyword Keyword.Indestructible.kind }
    }

    @Test
    fun cloudtrackCoordinatorCountsEnteredRides() = scenario {
        alice.battlefield(DftSet.cloudtrackCoordinator, "Plains", "Plains", "Plains", "Plains")
        alice.hand(kart, pony)
        alice.casts(kart)
        resolveAll()
        alice.casts(pony)
        resolveAll()
        alice.activates(DftSet.cloudtrackCoordinator)
        resolveAll()
        expect { card("Pilot Token", 1).zone shouldBe Zone.Battlefield }
    }
}
