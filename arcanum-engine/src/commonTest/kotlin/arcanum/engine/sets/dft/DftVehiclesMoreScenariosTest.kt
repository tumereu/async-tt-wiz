package arcanum.engine.sets.dft

import arcanum.api.CardType
import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.Subtype
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.dft.DftPlain.bear
import arcanum.engine.sets.dft.DftPlain.elf
import arcanum.engine.sets.dft.DftPlain.giant
import arcanum.engine.sets.dft.DftPlain.gizmo
import arcanum.engine.sets.dft.DftPlain.golem
import arcanum.engine.sets.dft.DftPlain.kart
import arcanum.engine.sets.dft.DftPlain.notes
import arcanum.engine.sets.dft.DftPlain.ogre
import arcanum.engine.sets.dft.DftPlain.p1p1
import arcanum.engine.sets.dft.DftPlain.pony
import arcanum.engine.sets.dft.DftPlain.trinket
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Aetherdrift parser chunk 02 in play: more Vehicles, exhaust abilities that make them creatures for good (CR 611.2a), mana only for
 * abilities (CR 106.6), counters by mana value as permanents enter (CR 614.1c), a power defined by lands (CR 604.3), divided counters
 * (CR 601.2d), a chosen creature type for Vehicle creatures (CR 607.2d) and an Equipment Vehicle (CR 301.5).
 */
class DftVehiclesMoreScenariosTest {
    // CR 106.6: the four mana pays for the exhaust ability, X with it
    @Test
    fun boomcruiserFuelsItsOwnExhaust() = scenario {
        alice.battlefield(*Array(4) { "Mountain" })
        alice.hand(DftSet.boomcruiser)
        bob.battlefield(giant)
        alice.casts(DftSet.boomcruiser)
        resolveAll()
        answer(Answer.ColorChoice(arcanum.api.Color.Red))
        resolveAll()
        expect { alice.pool.size shouldBe 4 }
        alice.activates(DftSet.boomcruiser, ability = 0, target = giant, x = 1)
        resolveAll()
        expect {
            giant.view.obj?.damage shouldBe 1
            DftSet.boomcruiser.counters(p1p1) shouldBe 1
        }
    }

    @Test
    fun burnerMissileBoostsACreature() = scenario {
        alice.battlefield("Mountain", "Mountain", bear)
        alice.hand(DftSet.burnerMissile)
        alice.casts(DftSet.burnerMissile)
        targets(bear)
        expect {
            bear.power shouldBe 4
            bear hasKeyword Keyword.Trample.kind
        }
    }

    // CR 400.7: it returns as a new object with a finality counter
    @Test
    fun gastalThrillriderAttacksAtOnceAndComesBack() = scenario {
        libraries()
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.hand(DftSet.gastalThrillrider, notes)
        alice.casts(DftSet.gastalThrillrider)
        resolveAll()
        expect { DftSet.gastalThrillrider.types.contains(CardType.Creature) shouldBe true }
        change(arcanum.engine.change.Changes.move(state, listOf(card(DftSet.gastalThrillrider)), Zone.Graveyard))
        advanceTo(TurnStep.PrecombatMain, turn = 3)
        alice.activates(DftSet.gastalThrillrider, ability = 1, choosing = listOf(notes))
        resolveAll()
        expect {
            DftSet.gastalThrillrider.zone shouldBe Zone.Battlefield
            DftSet.gastalThrillrider.counters(CounterKind.Finality) shouldBe 1
        }
    }

    // CR 611.2a: for good; a counter for each other Mount and Vehicle
    @Test
    fun spireSpeedcycleGrowsWithOtherRides() = scenario {
        libraries()
        alice.battlefield(DftSet.spireSpeedcycle, kart, pony)
        alice.activates(DftSet.spireSpeedcycle, ability = 0, choosing = listOf(kart))
        resolveAll()
        expect {
            DftSet.spireSpeedcycle.counters(p1p1) shouldBe 2
            DftSet.spireSpeedcycle.types.contains(CardType.Creature) shouldBe true
        }
        advanceTo(TurnStep.Upkeep, turn = 2)
        expect { DftSet.spireSpeedcycle.types.contains(CardType.Creature) shouldBe true }
    }

    // CR 604.3: its power counts lands; entering it fetches one more
    @Test
    fun trundlingWorldwagonRamps() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", giant)
        alice.hand(DftSet.trundlingWorldwagon)
        alice.library("Forest", notes)
        alice.casts(DftSet.trundlingWorldwagon)
        resolveAll()
        yes()
        choose("Forest")
        alice.activates(DftSet.trundlingWorldwagon, choosing = listOf(giant))
        resolveAll()
        expect { DftSet.trundlingWorldwagon.power shouldBe 4 }
    }

    // CR 614.1c: one counter at mana value 4 or less, three above
    @Test
    fun thunderousVelocycleAddsCountersByManaValue() = scenario {
        alice.battlefield(DftSet.thunderousVelocycle, *Array(7) { "Forest" })
        alice.hand(bear, giant)
        alice.casts(bear)
        resolveAll()
        alice.casts(giant)
        resolveAll()
        expect {
            bear.counters(p1p1) shouldBe 1
            giant.counters(p1p1) shouldBe 3
        }
    }

    @Test
    fun apocalypseRiderSneaksASmallCreature() = scenario {
        alice.battlefield(DftSet.apocalypseRider, bear)
        alice.activates(DftSet.apocalypseRider, ability = 0, target = bear)
        resolveAll()
        expect {
            bear hasKeyword Keyword.Lifelink.kind
            bear hasMechanic { it == arcanum.api.Mechanic.CantBeBlocked }
        }
    }

    // CR 601.2d: two counters divided between two targets
    @Test
    fun cloudtrackSkycycleDividesCounters() = scenario {
        alice.battlefield("Mountain", "Mountain", "Plains", "Plains", bear, kart)
        alice.hand(DftSet.cloudtrackSkycycle)
        alice.casts(DftSet.cloudtrackSkycycle)
        targets(bear, kart)
        (pending as? Decision.Divide)?.let { d -> answer(Answer.Division(d.among.map { arcanum.engine.decision.Share(it, 1) })) }
        resolveAll()
        expect {
            bear.counters(p1p1) shouldBe 1
            kart.counters(p1p1) shouldBe 1
        }
    }

    // CR 107.3m: X is the X it was cast with
    @Test
    fun duneWandererReturnsByX() = scenario {
        alice.battlefield("Plains", "Swamp", "Plains", "Swamp")
        alice.hand(DftSet.duneWanderer)
        alice.graveyard(bear)
        alice.casts(DftSet.duneWanderer, x = 2)
        targets(bear)
        expect { bear.zone shouldBe Zone.Battlefield }
    }

    @Test
    fun pathlightPathmakerPutsACheapArtifactIntoPlay() = scenario {
        alice.battlefield(*Array(5) { "Island" }, "Plains")
        alice.hand(DftSet.pathlightPathmaker)
        alice.library(trinket, notes)
        alice.casts(DftSet.pathlightPathmaker)
        resolveAll()
        yes()
        choose(trinket)
        expect { trinket.zone shouldBe Zone.Battlefield }
    }

    @Test
    fun hauntedHotrodUntapsTheAttacker() = scenario {
        libraries()
        alice.battlefield(DftSet.hauntedHotrod, bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        targets(bear)
        expect {
            bear.tapped shouldBe false
            bear.power shouldBe 3
            bear hasKeyword Keyword.Deathtouch.kind
        }
    }

    @Test
    fun rocketeerBoostcartMakesTreasureAndBecomesACreature() = scenario {
        libraries()
        alice.battlefield(DftSet.rocketeerBoostcart, "Mountain", "Mountain", "Mountain")
        alice.activates(DftSet.rocketeerBoostcart, ability = 0)
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers, turn = 3)
        alice.attacks(DftSet.rocketeerBoostcart)
        resolveAll()
        expect {
            "Treasure Token".zone shouldBe Zone.Battlefield
            DftSet.rocketeerBoostcart.counters(p1p1) shouldBe 1
        }
    }

    // CR 607.2d, 613.1d: Vehicle creatures get the chosen type, and creatures of it +1/+1
    @Test
    fun lifeforgeEngineNamesAType() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", elf, kart, giant)
        alice.hand(DftSet.lifeforgeEngine)
        alice.casts(DftSet.lifeforgeEngine)
        resolveAll()
        answer(Answer.SubtypeChoice(Subtype("Elf")))
        resolveAll()
        alice.activates(kart, choosing = listOf(giant))
        resolveAll()
        expect {
            elf.power shouldBe 2
            kart.subtypes.contains(Subtype("Elf")) shouldBe true
            kart.power shouldBe 4
        }
    }

    @Test
    fun marshalsPathrunnerFindsALandAndBecomesACreature() = scenario {
        alice.battlefield("Plains", "Island", "Swamp", "Mountain", "Forest", "Plains", "Island", "Swamp")
        alice.hand(DftSet.marshalsPathrunner)
        alice.library("Forest", notes)
        alice.casts(DftSet.marshalsPathrunner, paying = listOf("Plains#1", "Island#1", "Swamp#1"))
        resolveAll()
        choose("Forest")
        expect { alice.hand has "Forest" }
        alice.activates(DftSet.marshalsPathrunner, ability = 0)
        resolveAll()
        expect {
            DftSet.marshalsPathrunner.counters(p1p1) shouldBe 2
            DftSet.marshalsPathrunner.types.contains(CardType.Creature) shouldBe true
        }
    }

    // CR 702.6a: the equipped creature has double strike
    @Test
    fun roverEdgesGivesDoubleStrike() = scenario {
        alice.battlefield(DftSet.roverEdges, bear, "Plains", "Plains", "Plains", "Plains")
        alice.activates(DftSet.roverEdges, ability = 0, target = bear)
        resolveAll()
        expect { bear hasKeyword Keyword.DoubleStrike.kind }
    }

    @Test
    fun grumblerumblerExilesACardToRumble() = scenario {
        alice.battlefield(DftSet.grumblerumbler)
        alice.graveyard(bear)
        alice.activates(DftSet.grumblerumbler, ability = 0, choosing = listOf(bear))
        resolveAll()
        expect {
            bear.zone shouldBe Zone.Exile
            DftSet.grumblerumbler.types.contains(CardType.Creature) shouldBe true
        }
    }
}
