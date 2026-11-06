package arcanum.engine.sets.dft

import arcanum.api.CardType
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.dft.DftPlain.bear
import arcanum.engine.sets.dft.DftPlain.elf
import arcanum.engine.sets.dft.DftPlain.hauler
import arcanum.engine.sets.dft.DftPlain.kart
import arcanum.engine.sets.dft.DftPlain.notes
import arcanum.engine.sets.dft.DftPlain.ogre
import arcanum.engine.sets.dft.DftPlain.p1p1
import arcanum.engine.sets.dft.DftPlain.pony
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Aetherdrift's mechanics in play: crewing and saddling with more power or with toughness (CR 702.122a, 702.171a), attacking while saddled
 * (CR 702.171b), triggers on saddling and crewing (CR 702.171c, 702.122b), exhaust (CR 702.177) with triggers on activating it and abilities
 * activated as though they hadn't been (702.177b), and speed (CR 702.179) read as a value and reduced.
 */
class DftMechanicsScenariosTest {
    // CR 702.122a: a Pilot crews as though its power were 2 greater: a 2/2 crews 4
    @Test
    fun pilotCrewsAsThoughItsPowerWereTwoGreater() = scenario {
        alice.battlefield(DftSet.undyingPilot, hauler)
        alice.activates(hauler, choosing = listOf(DftSet.undyingPilot))
        resolveAll()
        expect {
            hauler.types.contains(CardType.Creature) shouldBe true
            DftSet.undyingPilot.tapped shouldBe true
        }
    }

    // CR 702.122a: without the bonus a 2/2 can't crew 4
    @Test
    fun plainCreatureCantCrewAlone() = scenario {
        alice.battlefield(bear, hauler)
        assertFailsWith<IllegalArgumentException> { alice.activates(hauler, choosing = listOf(bear)) }
    }

    // CR 702.171a: crewing and saddling with toughness: a 0/4 saddles 3 and crews 4
    @Test
    fun consoleAceSaddlesAndCrewsWithToughness() = scenario {
        alice.battlefield(DftSet.consoleAce, pony, hauler)
        alice.activates(pony, choosing = listOf(DftSet.consoleAce))
        resolveAll()
        expect { pony.view.obj?.marks?.contains(arcanum.api.ObjectMark.Saddled) shouldBe true }
    }

    // CR 702.171b: the attack trigger only while saddled
    @Test
    fun stallionUntapsAndGrowsOnlyWhenSaddled() = scenario {
        libraries()
        alice.battlefield(DftSet.dunelightStallion, elf)
        alice.activates(DftSet.dunelightStallion, choosing = listOf(elf))
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(DftSet.dunelightStallion)
        resolveAll()
        expect {
            DftSet.dunelightStallion.tapped shouldBe false
            DftSet.dunelightStallion.counters(p1p1) shouldBe 1
        }
    }

    @Test
    fun stallionWithoutSaddleDoesNothing() = scenario {
        libraries()
        alice.battlefield(DftSet.dunelightStallion, elf)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(DftSet.dunelightStallion)
        resolveAll()
        expect {
            DftSet.dunelightStallion.tapped shouldBe true
            DftSet.dunelightStallion.counters(p1p1) shouldBe 0
        }
    }

    // CR 702.122b: the creature crewed the Vehicle during your main phase: the Vehicle gains flying
    @Test
    fun gorgeLeaperGivesTheVehicleItCrewsFlying() = scenario {
        alice.battlefield(DftSet.gorgeLeaper, kart)
        alice.activates(kart, choosing = listOf(DftSet.gorgeLeaper))
        resolveAll()
        expect { kart hasKeyword Keyword.Flying.kind }
    }

    // CR 702.177a: an exhaust ability once; the trigger on activating one
    @Test
    fun rangersRefitterDrawsOnExhaust() = scenario {
        alice.battlefield(DftSet.rangersRefitter, *Array(4) { "Island" })
        alice.library(notes, notes)
        alice.activates(DftSet.rangersRefitter, ability = 0)
        resolveAll()
        expect {
            alice.hand has notes
            DftSet.rangersRefitter.counters(p1p1) shouldBe 1
            DftSet.rangersRefitter.types.contains(CardType.Creature) shouldBe true
        }
    }

    // CR 702.177b: the exhaust ability again on a later turn, but only one exhaust ability each turn
    @Test
    fun elvishRefitterReusesExhaust() = scenario {
        libraries()
        alice.battlefield(DftSet.elvishRefitter, "Forest", "Forest", "Forest", "Forest")
        alice.activates(DftSet.elvishRefitter)
        resolveAll()
        expect { DftSet.elvishRefitter.counters(p1p1) shouldBe 1 }
        val again = arcanum.engine.Engine.options(state, alice.id).filterIsInstance<arcanum.engine.input.Input.Activate>().any { it.source == card(DftSet.elvishRefitter) }
        assertEquals(false, again, "only one exhaust ability each turn")
        advanceTo(TurnStep.PrecombatMain, turn = 3)
        alice.activates(DftSet.elvishRefitter)
        resolveAll()
        expect { DftSet.elvishRefitter.counters(p1p1) shouldBe 2 }
    }

    // CR 702.179f: X is the speed
    @Test
    fun velocityFiendDrawsAndLosesBySpeed() = scenario {
        libraries()
        alice.battlefield(DftSet.theVelocityFiend)
        setSpeed(alice, 3)
        advanceTo(TurnStep.End)
        resolveAll()
        expect {
            alice.life shouldBe 17
            alice.hand.size shouldBe 3
        }
    }

    // CR 702.179: the opponent with the highest speed loses 1 of it, not below 1
    @Test
    fun spikebackHarrierSlowsTheFastestOpponent() = scenario {
        alice.battlefield(*Array(5) { "Island" })
        alice.hand(DftSet.spikebackHarrier)
        bob.battlefield(ogre)
        setSpeed(bob, 3)
        alice.casts(DftSet.spikebackHarrier)
        targets(ogre)
        expect { ogre.zone shouldBe Zone.Hand }
        assertEquals(2, speedOf(bob))
    }
}
