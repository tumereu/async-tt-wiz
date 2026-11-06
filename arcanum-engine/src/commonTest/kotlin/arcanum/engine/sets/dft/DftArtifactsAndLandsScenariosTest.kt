package arcanum.engine.sets.dft

import arcanum.api.Color
import arcanum.api.CounterKind
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.dft.DftPlain.bear
import arcanum.engine.sets.dft.DftPlain.gizmo
import arcanum.engine.sets.dft.DftPlain.golem
import arcanum.engine.sets.dft.DftPlain.notes
import arcanum.engine.sets.dft.DftPlain.ogre
import arcanum.engine.sets.dft.DftPlain.p1p1
import arcanum.engine.sets.dft.DftPlain.trinket
import arcanum.engine.sets.dft.DftPlain.zap
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Aetherdrift parser chunks 16 and 17 in play: the colourless artifacts and the lands. Aetherhull Jacket and Scrap Crusher are covered
 * sacrifice-to-destroy and damage templates; the gain lands enter tapped and gain 1 life like Bloodfell Grotto; the Verges repeat
 * Bleachbone Brink's conditional second colour.
 */
class DftArtifactsAndLandsScenariosTest {
    // CR 508.1b: it can't be attacked while attached; it gains loyalty from the creature's combat damage
    @Test
    fun theAetherflareRidesACreature() = scenario {
        alice.library(notes, notes)
        bob.library(notes, notes)
        alice.battlefield(DftSet.theAetherflare, counters = listOf(CounterKind.Loyalty to 4))
        alice.battlefield(bear)
        alice.activates(DftSet.theAetherflare, ability = 0, target = bear)
        resolveAll()
        expect {
            bear.counters(p1p1) shouldBe 1
            DftSet.theAetherflare.counters(CounterKind.Loyalty) shouldBe 5
        }
        hits(bear)
        expect { DftSet.theAetherflare.counters(CounterKind.Loyalty) shouldBe 8 }
    }

    @Test
    fun theAetherflareCantBeAttackedWhileAttached() = scenario {
        alice.library(notes, notes)
        bob.library(notes, notes)
        alice.battlefield(DftSet.theAetherflare, counters = listOf(CounterKind.Loyalty to 4))
        alice.battlefield(bear)
        bob.battlefield(ogre)
        alice.activates(DftSet.theAetherflare, ability = 0, target = bear)
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers, turn = 2)
        kotlin.test.assertFails("it can't be attacked while attached") { bob.attacks(ogre, defender = DftSet.theAetherflare) }
    }

    // CR 107.3m: X counters for the X paid; CR 603.10a: cards for its counters as it died
    @Test
    fun marketbackStriderGrowsAndDraws() = scenario {
        alice.battlefield(*Array(4) { "Plains" })
        alice.hand(DftSet.marketbackStrider)
        alice.library(notes, notes, notes)
        bob.battlefield("Swamp")
        bob.hand(DftPlain.doom)
        alice.casts(DftSet.marketbackStrider, x = 2)
        resolveAll()
        expect { DftSet.marketbackStrider.counters(p1p1) shouldBe 2 }
        alice.passes()
        bob.casts(DftPlain.doom, target = DftSet.marketbackStrider)
        resolveAll()
        expect { alice.hand.size shouldBe 2 }
    }

    // CR 700.2: a mode chosen earlier this turn isn't offered again
    @Test
    fun monumentToPersistenceChoosesNewModes() = scenario {
        alice.battlefield(DftSet.monumentToPersistence, "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(DftSet.skyshatter, DftSet.locustMist)
        alice.library(notes, notes, notes)
        alice.activates(DftSet.skyshatter)
        resolveAll()
        answer(Answer.Modes(listOf(2)))
        resolveAll()
        expect { bob.life shouldBe 17 }
        val modes = (pending as? Decision.Modes)
        assertEquals(null, modes)
    }

    // CR 605.1a: it targets, so it uses the Stack; three mana for each artifact sacrificed
    @Test
    fun radiantLotusBloomMakesManaForAPlayer() = scenario {
        alice.battlefield(DftSet.radiantLotusBloom, trinket, gizmo)
        alice.activates(DftSet.radiantLotusBloom, x = 2, targets = listOf("alice"), choosing = listOf(trinket, gizmo))
        resolveAll()
        (pending as? Decision.ChooseColor)?.let { answer(Answer.ColorChoice(Color.Red)); resolveAll() }
        expect { alice.pool.size shouldBe 6 }
    }

    @Test
    fun scrapCrusherDestroysAVehicle() = scenario {
        alice.battlefield(DftSet.scrapCrusher, *Array(6) { "Plains" })
        bob.battlefield(DftPlain.kart)
        alice.activates(DftSet.scrapCrusher, ability = 1, target = DftPlain.kart)
        resolveAll()
        expect { DftPlain.kart.zone shouldBe Zone.Graveyard }
    }

    // CR 603.4: only if an opponent controls more lands
    @Test
    fun turnstileTortoiseCatchesUp() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.hand(DftSet.turnstileTortoise)
        bob.battlefield("Swamp", "Swamp", "Swamp")
        alice.casts(DftSet.turnstileTortoise)
        resolveAll()
        expect { "Treasure Token".zone shouldBe Zone.Battlefield }
    }

    // CR 602.5b: the second colour only with one of the two basic land types
    @Test
    fun bleachboneBrinkNeedsAPlainsOrSwamp() = scenario {
        alice.battlefield(DftSet.bleachboneBrink)
        val second = view()[card(DftSet.bleachboneBrink)]!!.characteristics!!.abilities.filter { it.ability is arcanum.api.Ability.Activated }[1].origin
        val refused = arcanum.engine.Engine.refusal(state, arcanum.engine.input.Input.Activate(alice.id, card(DftSet.bleachboneBrink), second))
        assertEquals(true, refused != null)
    }

    @Test
    fun bloodfellGrottoEntersTappedAndGains() = scenario {
        alice.hand(DftSet.bloodfellGrotto)
        alice.plays(DftSet.bloodfellGrotto)
        resolveAll()
        expect {
            DftSet.bloodfellGrotto.tapped shouldBe true
            alice.life shouldBe 21
        }
    }

    // CR 614.1c: untapped with a Mount or Vehicle; the Pilot crews as 3
    @Test
    fun countryLanesEntersUntappedWithAVehicle() = scenario {
        alice.battlefield(DftPlain.kart, "Plains", "Plains")
        alice.hand(DftSet.countryLanes)
        alice.plays(DftSet.countryLanes)
        resolveAll()
        expect { DftSet.countryLanes.tapped shouldBe false }
        alice.activates(DftSet.countryLanes, ability = 1)
        resolveAll()
        expect { "Pilot Token".zone shouldBe Zone.Battlefield }
    }
}
