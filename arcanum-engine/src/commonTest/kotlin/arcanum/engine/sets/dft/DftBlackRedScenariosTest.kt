package arcanum.engine.sets.dft

import arcanum.api.CounterKind
import arcanum.api.Keyword
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
import arcanum.engine.sets.dft.DftPlain.trinket
import arcanum.engine.sets.dft.DftPlain.zap
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Aetherdrift parser chunks 12 and 13 in play: the black and red cards. Motor Rat (drain), Grim Trinket (shrink, surveil), Maximum Throttle,
 * Reckless Shortcut, Spin Off, Siphon Fuel, Wreckage Wickerkin, Wretched Dolly, Crash and Blaze, Pedal to the Floor and Lightning Jolt are
 * covered effects; Count on Fortune repeats Endrider Spikeshooter's upkeep exile ([DftSpeedScenariosTest] covers the max speed version).
 */
class DftBlackRedScenariosTest {
    // CR 113.6c: it returns from the graveyard on landfall
    @Test
    fun gravehauntSpecterReturnsOnLandfall() = scenario {
        alice.hand("Swamp")
        alice.graveyard(DftSet.gravehauntSpecter)
        alice.plays("Swamp")
        resolveAll()
        yes()
        expect { DftSet.gravehauntSpecter.zone shouldBe Zone.Battlefield }
    }

    @Test
    fun gravehauntSpecterHasHasteAgainstALowOpponent() = scenario {
        alice.battlefield(DftSet.gravehauntSpecter)
        expect { DftSet.gravehauntSpecter lacksKeyword Keyword.Haste.kind }
        change(arcanum.engine.change.Change.Life(bob.id, 20, 10))
        expect { DftSet.gravehauntSpecter hasKeyword Keyword.Haste.kind }
    }

    // CR 702.128a: embalm for the card's own mana cost
    @Test
    fun shroudclothWrappingsGrantsEmbalm() = scenario {
        alice.battlefield(DftSet.shroudclothWrappings, "Forest", "Forest", "Swamp")
        alice.graveyard(bear)
        alice.activates(DftSet.shroudclothWrappings, target = bear)
        resolveAll()
        alice.activates(bear)
        resolveAll()
        expect { card(bear, 0).zone shouldBe Zone.Exile }
        assertEquals(true, cards(bear).any { it.zone == Zone.Battlefield && it.token })
    }

    @Test
    fun gontraGivesTreasureToAThief() = scenario {
        alice.library(notes, notes)
        alice.battlefield(DftSet.gontraNightEnvoy, bear, "Mountain", "Mountain", "Mountain")
        bob.library(ogre, notes)
        hits(bear)
        expect { card(ogre).zone shouldBe Zone.Exile }
        advanceTo(TurnStep.PostcombatMain)
        alice.casts(ogre)
        resolveAll()
        expect {
            ogre.controller shouldBe alice.id
            "Treasure Token".controller shouldBe alice.id
        }
    }

    // CR 603.7c: surveil when that creature dies this turn
    @Test
    fun grimSpearthrowerSurveilsWhenTheAttackerDies() = scenario {
        alice.battlefield(DftSet.grimSpearthrower, bear)
        alice.library(notes, notes)
        bob.library(notes, notes)
        bob.battlefield(giant)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        targets(bear)
        expect { bear.power shouldBe 3 }
        advanceTo(TurnStep.DeclareBlockers)
        bob.blocks(giant to bear)
        advanceTo(TurnStep.CombatDamage)
        resolveAll()
        (pending as? Decision.ChooseObjects)?.let { answer(Answer.Objects(it.options)) }
        resolveAll()
        expect { alice.graveyard has notes }
    }

    // CR 608.2h: the sacrificed permanent was a Vehicle: draw
    @Test
    fun infernalSideswipeDrawsForASacrificedVehicle() = scenario {
        alice.battlefield("Swamp", kart)
        alice.hand(DftSet.infernalSideswipe)
        alice.library(notes)
        bob.battlefield(ogre)
        alice.casts(DftSet.infernalSideswipe, target = ogre, choosing = listOf(kart))
        resolveAll()
        expect {
            ogre.zone shouldBe Zone.Graveyard
            alice.hand has notes
        }
    }

    @Test
    fun pactdollHorrorDrainsForArtifacts() = scenario {
        alice.battlefield(DftSet.pactdollHorror, "Plains")
        alice.hand(trinket)
        alice.casts(trinket)
        resolveAll()
        expect {
            bob.life shouldBe 19
            alice.life shouldBe 21
        }
    }

    // CR 608.2b: compared after the mill
    @Test
    fun bogFeastDestroysByGraveyardSize() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.hand(DftSet.bogFeast)
        alice.library(notes, notes)
        alice.graveyard(notes)
        bob.battlefield(ogre)
        alice.casts(DftSet.bogFeast, target = ogre)
        resolveAll()
        expect { ogre.zone shouldBe Zone.Graveyard }
    }

    // CR 601.2f: cast from the graveyard paying life and a sacrifice
    @Test
    fun wickerkinIndomitableReturnsFromTheGraveyard() = scenario {
        alice.battlefield(*Array(4) { "Swamp" }, trinket)
        alice.graveyard(DftSet.wickerkinIndomitable)
        alice.casts(DftSet.wickerkinIndomitable, choosing = listOf(trinket))
        resolveAll()
        expect {
            DftSet.wickerkinIndomitable.zone shouldBe Zone.Battlefield
            alice.life shouldBe 18
            trinket.zone shouldBe Zone.Graveyard
        }
    }

    // ---- chunk 13

    @Test
    fun countOnFortuneExilesAPlayableCard() = scenario {
        alice.battlefield(DftSet.countOnFortune)
        alice.library(notes, ogre, notes)
        bob.library(notes, notes)
        advanceTo(TurnStep.Upkeep, turn = 3)
        resolveAll()
        advanceTo(TurnStep.Draw)
        assertEquals(true, cards(notes).any { it.zone == Zone.Exile && it.owner == alice.id })
    }

    @Test
    fun crashAndBlazeDestroysAVehicle() = scenario {
        alice.battlefield(*Array(4) { "Mountain" })
        alice.hand(DftSet.crashAndBlaze)
        bob.battlefield(kart)
        alice.casts(DftSet.crashAndBlaze, modes = listOf(0), target = kart)
        resolveAll()
        expect { kart.zone shouldBe Zone.Graveyard }
    }

    // CR 604.3: power by the greatest mana value among artifacts; the chosen card comes back if an artifact is sacrificed
    @Test
    fun darettoSwapsArtifacts() = scenario {
        alice.battlefield(*Array(5) { "Mountain" }, trinket)
        alice.hand(DftSet.darettoRocketEngineer)
        alice.graveyard(DftSet.racersLeaderboard)
        alice.library(notes, notes, notes)
        alice.casts(DftSet.darettoRocketEngineer)
        targets(DftSet.racersLeaderboard)
        payYesOrChoose(trinket)
        expect {
            DftSet.racersLeaderboard.zone shouldBe Zone.Battlefield
            DftSet.darettoRocketEngineer.power shouldBe 4
        }
    }

    // CR 500.8: two more combats; creatures that attacked untap at each
    @Test
    fun wideOpenThrottleAddsTwoCombats() = scenario {
        libraries()
        alice.battlefield(*Array(6) { "Mountain" }, bear)
        alice.hand(DftSet.wideOpenThrottle)
        alice.casts(DftSet.wideOpenThrottle)
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        resolveAll()
        advanceTo(TurnStep.EndOfCombat)
        allPass()
        resolveAll()
        expect { bear.tapped shouldBe false }
    }

    // CR 603.12: the target once the sacrifice is made
    @Test
    fun gastalWreckerTradesARideForAnArtifact() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", kart)
        alice.hand(DftSet.gastalWrecker)
        bob.battlefield(gizmo)
        alice.casts(DftSet.gastalWrecker)
        resolveAll()
        payYesOrChoose(kart)
        targets(gizmo)
        expect { gizmo.zone shouldBe Zone.Graveyard }
    }

    @Test
    fun thunderheadCannoneerRummagesOnce() = scenario {
        alice.battlefield(DftSet.thunderheadCannoneer)
        alice.hand(bear)
        alice.library(notes)
        alice.activates(DftSet.thunderheadCannoneer, choosing = listOf(bear))
        resolveAll()
        expect { alice.hand has notes }
    }
}
