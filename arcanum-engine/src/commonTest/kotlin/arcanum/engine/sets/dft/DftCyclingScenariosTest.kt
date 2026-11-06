package arcanum.engine.sets.dft

import arcanum.api.Color
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
import arcanum.engine.sets.dft.DftPlain.hawk
import arcanum.engine.sets.dft.DftPlain.notes
import arcanum.engine.sets.dft.DftPlain.ogre
import arcanum.engine.sets.dft.DftPlain.p1p1
import arcanum.engine.sets.dft.DftPlain.trinket
import arcanum.engine.sets.dft.DftPlain.zap
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Aetherdrift parser chunks 10 and 11 in play: cycling (CR 702.29) and its triggers (CR 702.29c), with the X of cycling {X} (CR 107.3a).
 * Valiant Takedown, Gleamshield Parry, Locust Mist, Skyshatter, Stoke the Flames Anew and Wandering Ketradon are covered effects with
 * cycling; Marauding Shark repeats Scavenging Skyray's discard trigger.
 */
class DftCyclingScenariosTest {
    @Test
    fun basraExertsForCatsAndProtectsThemWhenCycled() = scenario {
        alice.battlefield(DftSet.basraRiderOfDawn, "Plains", "Plains", "Plains", "Plains")
        alice.hand(DftSet.basraRiderOfDawn)
        alice.library(notes)
        activateCopy(alice, DftSet.basraRiderOfDawn, 0, ability = 0)
        resolveAll()
        expect { "Cat Token" hasKeyword Keyword.Lifelink.kind }
        activateCopy(alice, DftSet.basraRiderOfDawn, 1, ability = 1)
        resolveAll()
        expect { "Cat Token" hasKeyword Keyword.Indestructible.kind }
    }

    // CR 613.1f: indestructible is lost before the destruction
    @Test
    fun spectacularWreckageDestroysIndestructibles() = scenario {
        alice.battlefield(*Array(5) { "Plains" }, DftSet.hazoraGodracer)
        alice.hand(DftSet.spectacularWreckage)
        bob.battlefield(DftPlain.kart, ogre)
        alice.casts(DftSet.spectacularWreckage)
        resolveAll()
        expect {
            DftSet.hazoraGodracer.zone shouldBe Zone.Graveyard
            DftPlain.kart.zone shouldBe Zone.Graveyard
            ogre.zone shouldBe Zone.Graveyard
        }
    }

    // CR 107.3a, 702.29c: X Pilots for X paid
    @Test
    fun valorsDreadnoughtCyclesForPilots() = scenario {
        alice.battlefield(*Array(5) { "Plains" })
        alice.hand(DftSet.valorsDreadnought)
        alice.library(notes)
        alice.activates(DftSet.valorsDreadnought, ability = 1, x = 2)
        resolveAll()
        expect {
            card("Pilot Token", 1).zone shouldBe Zone.Battlefield
            alice.hand has notes
        }
    }

    @Test
    fun howlersHaulerShrinksWhenCycled() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand(DftSet.howlersHauler)
        alice.library(notes)
        bob.battlefield(ogre)
        alice.activates(DftSet.howlersHauler)
        targets(ogre)
        expect { ogre.power shouldBe 0 }
    }

    @Test
    fun scavengingSkyrayGrowsForEachDiscard() = scenario {
        alice.battlefield(DftSet.scavengingSkyray, "Island", "Island")
        alice.hand(DftSet.valiantTakedown)
        alice.library(notes)
        alice.activates(DftSet.valiantTakedown)
        resolveAll()
        expect { DftSet.scavengingSkyray.counters(p1p1) shouldBe 1 }
    }

    // CR 122.1d: a stun counter is removed instead of untapping
    @Test
    fun stalledEngineStuns() = scenario {
        libraries()
        alice.battlefield("Island", "Island")
        alice.hand(DftSet.stalledEngine)
        bob.battlefield(ogre)
        alice.casts(DftSet.stalledEngine, target = ogre)
        resolveAll()
        expect {
            ogre.tapped shouldBe true
            ogre.counters(CounterKind.Stun) shouldBe 3
        }
        advanceTo(TurnStep.Draw, turn = 2)
        expect {
            ogre.tapped shouldBe true
            ogre.counters(CounterKind.Stun) shouldBe 2
        }
    }

    // CR 701.12a: both change controllers
    @Test
    fun swapTheHelmExchanges() = scenario {
        alice.battlefield(*Array(5) { "Island" }, elf)
        alice.hand(DftSet.swapTheHelm)
        bob.battlefield(giant)
        alice.casts(DftSet.swapTheHelm, targets = listOf(elf, giant))
        resolveAll()
        expect {
            giant.controller shouldBe alice.id
            elf.controller shouldBe bob.id
        }
    }

    @Test
    fun tumbleUpPutsOnTop() = scenario {
        alice.battlefield(*Array(4) { "Island" })
        alice.hand(DftSet.tumbleUp)
        bob.battlefield(giant)
        bob.library(notes)
        alice.casts(DftSet.tumbleUp, target = giant)
        resolveAll()
        option("top")
        expect { giant.zone shouldBe Zone.Library }
        assertEquals(card(giant), state.player(bob.id).library.first())
    }

    // CR 707.9: it may enter as a copy
    @Test
    fun tallowShapethiefCopiesAnArtifactOrCreature() = scenario {
        alice.battlefield(*Array(4) { "Island" }, giant)
        alice.hand(DftSet.tallowShapethief)
        alice.casts(DftSet.tallowShapethief)
        resolveAll()
        choose(giant)
        expect { card(DftSet.tallowShapethief).power shouldBe 5 }
    }

    // CR 701.23: the cards with that name from three zones
    @Test
    fun ancestralVendettaExilesANameEverywhere() = scenario {
        alice.battlefield(*Array(4) { "Swamp" })
        alice.hand(DftSet.ancestralVendetta)
        bob.hand(ogre)
        bob.graveyard(ogre)
        bob.library(ogre, notes)
        alice.casts(DftSet.ancestralVendetta, target = "bob")
        resolveAll()
        answer(Answer.CardName(ogre))
        resolveAll()
        choose(ogre, ogre, ogre)
        assertEquals(3, cards(ogre).count { it.zone == Zone.Exile })
    }

    // CR 614.12: the colour chosen as it enters
    @Test
    fun nightBazaarMakesTheChosenColor() = scenario {
        alice.hand(DftSet.nightBazaar)
        alice.plays(DftSet.nightBazaar)
        resolveAll()
        answer(Answer.ColorChoice(Color.Blue))
        resolveAll()
        expect { DftSet.nightBazaar.tapped shouldBe true }
    }

    // CR 601.2f: {1} less for each artifact and creature card in your graveyard
    @Test
    fun chitinTombstalkerCostsLessWithAFullGraveyard() = scenario {
        alice.battlefield("Swamp")
        alice.hand(DftSet.chitinTombstalker)
        alice.graveyard(bear, elf, ogre, golem, trinket)
        alice.casts(DftSet.chitinTombstalker)
        resolveAll()
        expect { DftSet.chitinTombstalker.zone shouldBe Zone.Battlefield }
    }

    @Test
    fun intimidatingTacticsExilesACreature() = scenario {
        alice.battlefield("Swamp")
        alice.hand(DftSet.intimidatingTactics)
        bob.hand(giant, notes)
        alice.casts(DftSet.intimidatingTactics, target = "bob")
        resolveAll()
        choose(giant)
        expect { giant.zone shouldBe Zone.Exile }
    }

    @Test
    fun shefetHellfiendShrinksOthers() = scenario {
        alice.battlefield(*Array(7) { "Swamp" }, ogre)
        alice.hand(DftSet.shefetHellfiend)
        bob.battlefield(bear)
        alice.casts(DftSet.shefetHellfiend)
        resolveAll()
        expect {
            bear.zone shouldBe Zone.Graveyard
            ogre.power shouldBe 1
        }
    }

    // CR 702.29c: the cycle trigger from the graveyard
    @Test
    fun magmakinGunnerBurnsForDiscardsAndCycling() = scenario {
        alice.battlefield(DftSet.magmakinGunner, "Mountain", "Mountain")
        alice.hand(DftSet.magmakinGunner)
        alice.library(notes)
        activateCopy(alice, DftSet.magmakinGunner, 1, ability = 0)
        resolveAll()
        expect { bob.life shouldBe 18 }
    }

    @Test
    fun agonodonRexCountersWhenCycled() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", bear)
        alice.hand(DftSet.agonodonRex)
        alice.library(notes)
        alice.activates(DftSet.agonodonRex)
        targets(bear)
        expect {
            bear.counters(p1p1) shouldBe 2
            bear hasKeyword Keyword.Indestructible.kind
        }
    }

    // CR 107.3a: mana value X for the X paid
    @Test
    fun webshotEliteDestroysByCycledX() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest")
        alice.hand(DftSet.webshotElite)
        alice.library(notes)
        bob.battlefield(gizmo, trinket)
        alice.activates(DftSet.webshotElite, x = 2)
        targets(gizmo)
        expect { gizmo.zone shouldBe Zone.Graveyard }
    }

    @Test
    fun wreckCollectorExilesAndGains() = scenario {
        alice.battlefield(*Array(4) { "Plains" })
        alice.hand(DftSet.wreckCollector)
        bob.graveyard(ogre)
        alice.casts(DftSet.wreckCollector)
        targets(ogre)
        expect {
            ogre.zone shouldBe Zone.Exile
            alice.life shouldBe 21
        }
    }
}
