package arcanum.engine.sets.dft

import arcanum.api.CardType
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.dft.DftPlain.bear
import arcanum.engine.sets.dft.DftPlain.doom
import arcanum.engine.sets.dft.DftPlain.elf
import arcanum.engine.sets.dft.DftPlain.giant
import arcanum.engine.sets.dft.DftPlain.golem
import arcanum.engine.sets.dft.DftPlain.kart
import arcanum.engine.sets.dft.DftPlain.notes
import arcanum.engine.sets.dft.DftPlain.ogre
import arcanum.engine.sets.dft.DftPlain.p1p1
import arcanum.engine.sets.dft.DftPlain.trinket
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Aetherdrift parser chunks 01 and 02 in play: the Vehicles. Skyward Patrol Unit, Clamorous Ironhull and Skybox Barge are keywords with crew
 * (crew in [DftMechanicsScenariosTest]); Spotlight Scouter (scry), Hullskimmer (draw), Ripclaw Rustler (each opponent discards), Sidewalk
 * Rambler (a Thopter), Veloheart Cycle (life, mana), Debris Scarab (drain), Thundering Broodcart (destroy by mana value) and Boosted Schooner
 * (loot when you attack) only add covered enters and attack templates to a Vehicle; Rangers' Aetherhub repeats Rangers' Refitter's exhaust
 * trigger.
 */
class DftVehiclesScenariosTest {
    // CR 610.3: the exiled card comes back when the Vehicle leaves
    @Test
    fun holdingChariotExilesUntilItLeaves() = scenario {
        alice.battlefield(*Array(6) { "Plains" })
        alice.hand(DftSet.holdingChariot)
        bob.battlefield(ogre, "Mountain", "Mountain")
        bob.hand(DftSet.skyshatter)
        alice.casts(DftSet.holdingChariot)
        targets(ogre)
        expect { ogre.zone shouldBe Zone.Exile }
        alice.passes()
        bob.casts(DftSet.skyshatter, target = DftSet.holdingChariot)
        resolveAll()
        expect { ogre.zone shouldBe Zone.Battlefield }
    }

    @Test
    fun rescueEngineLiftsArtifactCreaturesAndReturnsOne() = scenario {
        libraries()
        alice.battlefield(DftSet.rescueEngine, golem, giant, ogre)
        alice.graveyard(trinket)
        expect { golem.power shouldBe 5 }
        alice.activates(DftSet.rescueEngine, choosing = listOf(giant, ogre))
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(DftSet.rescueEngine)
        targets(trinket)
        expect { trinket.zone shouldBe Zone.Battlefield }
    }

    // CR 601.2f: activated abilities of sources with the chosen name cost {2} more
    @Test
    fun seerSkyChariotTaxesTheChosenName() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.hand(DftSet.seerSkyChariot)
        bob.battlefield(DftSet.motorRat, *Array(6) { "Swamp" })
        alice.casts(DftSet.seerSkyChariot)
        resolveAll()
        pending as Decision.ChooseCardName
        answer(Answer.CardName(DftSet.motorRat))
        resolveAll()
        alice.passes()
        bob.activates(DftSet.motorRat)
        val payment = pending as Decision.ManaPayment
        assertEquals("{7}{B}", payment.cost.toString())
    }

    // CR 301.7b: until end of turn it is an artifact creature with flying, with a counter
    @Test
    fun voyagerSkimcarFliesWithThreeTapped() = scenario {
        alice.battlefield(DftSet.voyagerSkimcar, bear, elf, ogre)
        alice.activates(DftSet.voyagerSkimcar, ability = 0, choosing = listOf(bear, elf, ogre))
        resolveAll()
        expect {
            DftSet.voyagerSkimcar.types.contains(CardType.Creature) shouldBe true
            DftSet.voyagerSkimcar hasKeyword Keyword.Flying.kind
            DftSet.voyagerSkimcar.power shouldBe 3
        }
    }

    @Test
    fun moonlitManglerIsACreatureOnOtherTurns() = scenario {
        libraries()
        alice.battlefield(DftSet.moonlitMangler)
        expect { DftSet.moonlitMangler.types.contains(CardType.Creature) shouldBe false }
        advanceTo(TurnStep.Upkeep, turn = 2)
        expect { DftSet.moonlitMangler.types.contains(CardType.Creature) shouldBe true }
    }

    // CR 613.1b: control for as long as you control the Vehicle; it can't attack or block meanwhile
    @Test
    fun seizureEngineTakesACreatureWhileItStays() = scenario {
        alice.battlefield(*Array(5) { "Island" })
        alice.hand(DftSet.seizureEngine)
        bob.battlefield(ogre, "Swamp")
        bob.hand(doom)
        alice.casts(DftSet.seizureEngine)
        targets(ogre)
        expect { ogre.controller shouldBe alice.id }
        change(arcanum.engine.change.Changes.move(state, listOf(card(DftSet.seizureEngine)), Zone.Graveyard))
        expect { ogre.controller shouldBe bob.id }
    }

    @Test
    fun thopterAssemblerOnTheSecondDraw() = scenario {
        alice.battlefield(DftSet.thopterAssembler, "Island", "Island")
        alice.hand(notes, notes)
        alice.library(notes, notes, notes)
        alice.casts(notes)
        resolveAll()
        expect { stack.isEmpty() }
        alice.casts(notes)
        resolveAll()
        expect { "Thopter Token".zone shouldBe Zone.Battlefield }
    }

    @Test
    fun carrionHaulerMillsAndReturnsAVehicle() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.hand(DftSet.carrionHauler)
        alice.library(kart, notes, notes)
        alice.casts(DftSet.carrionHauler)
        resolveAll()
        choose(kart)
        expect { kart.zone shouldBe Zone.Hand }
    }

    @Test
    fun cryptcallChariotMakesZombiesForDiscards() = scenario {
        alice.battlefield(DftSet.cryptcallChariot, DftSet.sharpEyedBuccaneer, "Island", "Island")
        alice.hand(notes)
        alice.library(notes, notes)
        alice.activates(DftSet.sharpEyedBuccaneer)
        resolveAll()
        choose(notes)
        expect {
            "Zombie Token".tapped shouldBe true
            DftSet.sharpEyedBuccaneer.counters(p1p1) shouldBe 1
        }
    }

    // CR 608.2h: a creature you controlled, read as it was destroyed
    @Test
    fun infernalJunkerDestroysOnePerPlayer() = scenario {
        alice.battlefield(*Array(7) { "Swamp" }, elf)
        alice.hand(DftSet.infernalJunker)
        bob.battlefield(ogre)
        alice.casts(DftSet.infernalJunker)
        targets(elf, ogre)
        expect {
            elf.zone shouldBe Zone.Graveyard
            ogre.zone shouldBe Zone.Graveyard
            DftSet.infernalJunker.counters(p1p1) shouldBe 2
        }
    }

    @Test
    fun theFinalLapShrinksByLife() = scenario {
        alice.battlefield(DftSet.theFinalLap, giant)
        change(arcanum.engine.change.Change.Life(alice.id, 20, 10))
        alice.activates(DftSet.theFinalLap, ability = 1, choosing = listOf(giant))
        resolveAll()
        expect {
            DftSet.theFinalLap.power shouldBe 3
            DftSet.theFinalLap.toughness shouldBe 3
        }
    }
}
