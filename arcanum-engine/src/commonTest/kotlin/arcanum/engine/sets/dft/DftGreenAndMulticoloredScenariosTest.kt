package arcanum.engine.sets.dft

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
import arcanum.engine.sets.dft.DftPlain.insect
import arcanum.engine.sets.dft.DftPlain.kart
import arcanum.engine.sets.dft.DftPlain.notes
import arcanum.engine.sets.dft.DftPlain.ogre
import arcanum.engine.sets.dft.DftPlain.p1p1
import arcanum.engine.sets.dft.DftPlain.pony
import arcanum.engine.sets.dft.DftPlain.trinket
import arcanum.engine.sets.dft.DftPlain.zap
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Aetherdrift parser chunks 14 and 15 in play: the green and multicoloured cards. Bestow Grandeur, Snapped Pinions, Regal Imperiodon,
 * Silken Vigor, Broadside Volley, Broodheart Device, Veteran Beastrunner and Voyage Homeward use covered effects; Pothole Vole repeats Ooze
 * Sentry's mill and Dredger's Foresight's pick from the graveyard.
 */
class DftGreenAndMulticoloredScenariosTest {
    @Test
    fun beastriderOutriderRevealsAPermanent() = scenario {
        alice.battlefield(DftSet.beastriderOutrider, *Array(5) { "Forest" })
        alice.library(notes, bear, notes)
        alice.activates(DftSet.beastriderOutrider)
        resolveAll()
        choose(bear)
        expect { alice.hand has bear }
    }

    // CR 603.10a: the cards that left the graveyard, once for the batch
    @Test
    fun dredgersForesightMillsAndGains() = scenario {
        alice.battlefield("Forest", "Forest")
        alice.hand(DftSet.dredgersForesight)
        alice.library(bear, notes, notes, notes, notes)
        alice.casts(DftSet.dredgersForesight)
        resolveAll()
        choose(bear)
        expect { alice.hand has bear }
    }

    @Test
    fun fangProtectorPumpsAnother() = scenario {
        alice.battlefield(*Array(4) { "Forest" }, bear)
        alice.hand(DftSet.fangProtector)
        alice.casts(DftSet.fangProtector)
        targets(bear)
        expect { bear.power shouldBe 4 }
    }

    // CR 113.1: a creature card with no abilities, from the graveyard (no shuffle needed)
    @Test
    fun fangDruidCallerFindsAVanillaCreature() = scenario {
        alice.battlefield(*Array(4) { "Forest" })
        alice.hand(DftSet.fangDruidCaller)
        alice.graveyard(bear, DftPlain.hawk)
        alice.casts(DftSet.fangDruidCaller)
        resolveAll()
        yes()
        // "and/or": the searcher chooses the zones (CR 701.23a)
        answer(Answer.Option("library and graveyard"))
        choose(bear)
        expect { alice.hand has bear }
    }

    // CR 613.4b, 613.1d: 6/6 Oozes; an Elephant for each spell cast in another player's turn
    @Test
    fun marchOfTheEndlessOozeMakesOozesAndElephants() = scenario {
        alice.battlefield(DftSet.marchOfTheEndlessOoze, elf)
        bob.battlefield("Mountain")
        bob.hand(zap)
        expect {
            elf.power shouldBe 6
            elf.subtypes.contains(Subtype("Ooze")) shouldBe true
        }
        alice.passes()
        bob.casts(zap, target = "alice")
        resolveAll()
        expect { "Elephant Token".power shouldBe 6 }
    }

    @Test
    fun moltKeeperMillsAndMakesMana() = scenario {
        alice.battlefield(DftSet.moltKeeper)
        alice.library(notes, notes)
        alice.activates(DftSet.moltKeeper, ability = 0)
        resolveAll()
        expect { alice.graveyard has notes }
    }

    // CR 608.2c: counted after the mill
    @Test
    fun oozeSentryGrowsWithTheGraveyard() = scenario {
        alice.battlefield(*Array(4) { "Forest" })
        alice.hand(DftSet.oozeSentry)
        alice.library(bear, golem, notes)
        alice.graveyard(ogre)
        alice.casts(DftSet.oozeSentry)
        resolveAll()
        expect { DftSet.oozeSentry.counters(p1p1) shouldBe 3 }
    }

    // CR 506.4: trample while attacking an opponent; a Vehicle from the hand with counters
    @Test
    fun oviraPutsAVehicleWithCounters() = scenario {
        alice.battlefield(DftSet.oviraAutomechArtisan, "Forest")
        alice.hand(kart)
        alice.activates(DftSet.oviraAutomechArtisan)
        resolveAll()
        choose(kart)
        expect { kart.counters(p1p1) shouldBe 2 }
    }

    @Test
    fun oviraGivesAttackersTrample() = scenario {
        libraries()
        alice.battlefield(DftSet.oviraAutomechArtisan, bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        resolveAll()
        expect { bear hasKeyword Keyword.Trample.kind }
    }

    // CR 701.14: each deals damage equal to its power to the other
    @Test
    fun plowUnderFoeFights() = scenario {
        alice.battlefield("Forest", giant)
        alice.hand(DftSet.plowUnderFoe)
        bob.battlefield(ogre)
        alice.casts(DftSet.plowUnderFoe, modes = listOf(0), targets = listOf(giant, ogre))
        resolveAll()
        expect {
            ogre.zone shouldBe Zone.Graveyard
            giant.view.obj?.damage shouldBe 3
        }
    }

    // CR 115.3: four targets; a card may be the target of more than one
    @Test
    fun riseFromTheRubbleReturnsEachKind() = scenario {
        alice.battlefield("Forest", "Forest", "Forest")
        alice.hand(DftSet.riseFromTheRubble)
        alice.graveyard(ogre, pony, kart, bear)
        alice.casts(DftSet.riseFromTheRubble, targets = listOf(ogre, pony, kart, bear))
        resolveAll()
        expect {
            alice.hand has ogre
            alice.hand has pony
            alice.hand has kart
            alice.hand has bear
        }
    }

    // CR 601.2f: {1} less targeting a Mount or Vehicle you control
    @Test
    fun runDownWithAVehicle() = scenario {
        alice.battlefield("Forest", kart, giant)
        alice.hand(DftSet.runDown)
        bob.battlefield(ogre)
        alice.activates(kart, choosing = listOf(giant))
        resolveAll()
        alice.casts(DftSet.runDown, targets = listOf(kart, ogre))
        resolveAll()
        expect { ogre.zone shouldBe Zone.Graveyard }
    }

    // ---- chunk 15

    @Test
    fun aatcheekMakesInsectsAndGrows() = scenario {
        alice.battlefield(*Array(5) { "Swamp" }, "Forest", insect)
        alice.hand(DftSet.aatcheekEmeraldHerald)
        alice.graveyard(bear, trinket)
        bob.battlefield("Mountain")
        bob.hand(zap)
        alice.casts(DftSet.aatcheekEmeraldHerald)
        resolveAll()
        expect { card("Insect Token", 1).zone shouldBe Zone.Battlefield }
        alice.passes()
        bob.casts(zap, target = insect)
        resolveAll()
        expect {
            DftSet.aatcheekEmeraldHerald.counters(p1p1) shouldBe 1
            bob.life shouldBe 19
        }
    }

    @Test
    fun brightglassColossusFindsTwoCheapCards() = scenario {
        alice.battlefield("Forest", "Forest", "Plains", "Plains")
        alice.hand(DftSet.brightglassColossus)
        alice.library(elf, trinket, notes)
        alice.casts(DftSet.brightglassColossus)
        resolveAll()
        yes()
        choose(elf, trinket)
        expect {
            alice.hand has elf
            alice.hand has trinket
        }
    }

    // CR 603.7b: +2/+0 per discarded card; draws for its combat damage this turn
    @Test
    fun captainYowlerPumpsOnDiscards() = scenario {
        alice.battlefield(DftSet.captainYowlerSeaTerror, bear, "Island", "Island")
        alice.hand(DftSet.valiantTakedown)
        alice.library(notes, notes, notes)
        bob.library(notes, notes)
        alice.activates(DftSet.valiantTakedown)
        targets(bear)
        expect { bear.power shouldBe 4 }
        hits(bear)
        expect { alice.hand.size shouldBe 2 }
    }

    // CR 614.1a: one more +1/+1 counter
    @Test
    fun caradoreAddsACounter() = scenario {
        alice.battlefield(DftSet.caradoreHeartOfAlacria, bear, "Forest")
        alice.hand(DftPlain.growth)
        alice.casts(DftPlain.growth, target = bear)
        resolveAll()
        expect { bear.counters(p1p1) shouldBe 2 }
    }

    // CR 603.7: the reanimated creature is exiled at your next end step
    @Test
    fun coalstokeColossusBorrowsACreature() = scenario {
        libraries()
        alice.battlefield("Swamp", "Swamp", "Mountain", "Mountain", "Mountain")
        alice.hand(DftSet.coalstokeColossus)
        bob.graveyard(ogre)
        alice.casts(DftSet.coalstokeColossus)
        targets(ogre)
        expect {
            ogre.controller shouldBe alice.id
            ogre hasKeyword Keyword.Haste.kind
            ogre.counters(CounterKind.Finality) shouldBe 1
        }
        advanceTo(TurnStep.End)
        resolveAll()
        expect { ogre.zone shouldBe Zone.Exile }
    }

    // CR 603.7: returned at the next end step, after the damage
    @Test
    fun explosiveEscapeSavesOneFromTheBlast() = scenario {
        libraries()
        alice.battlefield(*Array(4) { "Mountain" }, "Plains", ogre)
        alice.hand(DftSet.explosiveEscape)
        bob.battlefield(giant)
        alice.casts(DftSet.explosiveEscape, target = ogre)
        resolveAll()
        expect {
            ogre.zone shouldBe Zone.Exile
            giant.view.obj?.damage shouldBe 4
        }
        advanceTo(TurnStep.End)
        resolveAll()
        expect { ogre.zone shouldBe Zone.Battlefield }
    }

    @Test
    fun hauntTheGridDrainsByArtifacts() = scenario {
        alice.battlefield("Island", "Island", "Island", "Swamp", "Swamp", trinket)
        alice.hand(DftSet.hauntTheGrid)
        alice.casts(DftSet.hauntTheGrid, target = "bob")
        resolveAll()
        expect {
            bob.life shouldBe 17
            alice.life shouldBe 23
        }
    }

    // CR 508.1d: it can't attack until seven cards are exiled; draws for exiles in your turn
    @Test
    fun ketramosDrawsForExiles() = scenario {
        alice.battlefield(DftSet.ketramosTheBrightDawn, "Plains", "Plains", "Plains", "Plains")
        alice.hand(DftSet.wreckCollector)
        alice.library(notes)
        bob.graveyard(ogre)
        alice.casts(DftSet.wreckCollector)
        targets(ogre)
        expect {
            alice.hand has notes
            alice.life shouldBe 20
        }
    }

    // CR 614.12, 707.9b: cards exiled as it enters and a copy of one of them
    @Test
    fun mimeoplastBecomesAnExiledCreature() = scenario {
        alice.battlefield("Swamp", "Forest", "Island", "Swamp", "Swamp")
        alice.hand(DftSet.mimeoplastExaltedOne)
        alice.graveyard(giant, ogre)
        alice.casts(DftSet.mimeoplastExaltedOne, x = 2)
        resolveAll()
        choose(giant, ogre)
        expect { DftSet.mimeoplastExaltedOne.counters(p1p1) shouldBe 6 }
    }

    // CR 607.2a, 707.9b: the activated ability targets a card it exiled (in exile) and becomes a 0/0 copy of it with six counters
    @Test
    fun mimeoplastBecomesACopyOfACardExiledWithIt() = scenario {
        alice.battlefield("Swamp", "Forest", "Island", "Swamp", "Swamp", "Forest", "Forest")
        alice.hand(DftSet.mimeoplastExaltedOne)
        alice.graveyard(giant, ogre)
        alice.casts(DftSet.mimeoplastExaltedOne, x = 2, paying = listOf("Swamp", "Forest", "Island", "Swamp", "Swamp"))
        resolveAll()
        choose(giant, ogre)
        val mimeoplast = arcanum.engine.scenario.CardRef(card(DftSet.mimeoplastExaltedOne), "Mimeoplast")
        alice.activates(DftSet.mimeoplastExaltedOne, target = giant)
        resolveAll()
        expect {
            mimeoplast.subtypes shouldBe listOf(arcanum.api.Subtype("Giant"))
            mimeoplast.power shouldBe 6
        }
    }

    @Test
    fun oildeepColossusSwapsACard() = scenario {
        alice.battlefield("Island", "Island", "Swamp", "Swamp")
        alice.hand(DftSet.oildeepColossus)
        bob.hand(giant)
        bob.library(notes)
        alice.casts(DftSet.oildeepColossus)
        targets("bob")
        choose(giant)
        expect {
            bob.graveyard has giant
            bob.hand has notes
        }
    }

    // CR 615.12: no damage can be prevented this turn
    @Test
    fun pyrewoodColossusRallies() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Mountain", "Mountain", bear)
        alice.hand(DftSet.pyrewoodColossus)
        alice.casts(DftSet.pyrewoodColossus)
        resolveAll()
        expect {
            bear.power shouldBe 4
            bear hasKeyword Keyword.Menace.kind
        }
    }

    // CR 601.2c: third from the top of its owner's library
    @Test
    fun riptideColossusTucksAPermanent() = scenario {
        alice.battlefield("Island", "Island", "Island", "Plains", "Plains")
        alice.hand(DftSet.riptideColossus)
        bob.battlefield(giant)
        bob.library(notes, notes, notes)
        alice.casts(DftSet.riptideColossus)
        targets(giant)
        assertEquals(card(giant), state.player(bob.id).library[2])
    }

    // zero is even: it can attack with no counters; the counter makes it odd and draws
    @Test
    fun sabSunarDrawsOnOddCounters() = scenario {
        alice.battlefield(DftSet.sabSunarLuxaIncarnate)
        alice.library(notes, notes, notes, notes)
        bob.library(notes, notes)
        advanceTo(TurnStep.PrecombatMain, turn = 3)
        resolveAll()
        expect {
            DftSet.sabSunarLuxaIncarnate.counters(p1p1) shouldBe 1
            alice.hand.size shouldBe 3
        }
    }
}
