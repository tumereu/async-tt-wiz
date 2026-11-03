package arcanum.engine.sets.fin

import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.fin.FinExtras.bear
import arcanum.engine.sets.fin.FinExtras.charm
import arcanum.engine.sets.fin.FinExtras.doom
import arcanum.engine.sets.fin.FinExtras.flier
import arcanum.engine.sets.fin.FinExtras.giant
import arcanum.engine.sets.fin.FinExtras.lance
import arcanum.engine.sets.fin.FinExtras.notes
import arcanum.engine.sets.fin.FinExtras.ogre
import arcanum.engine.sets.fin.FinExtras.p1p1
import arcanum.engine.sets.fin.FinExtras.trinket
import kotlin.test.Test

/**
 * Final Fantasy in play: the cards whose wordings repeat templates played elsewhere, each played once so every card of the set is known to
 * work in the engine (job select bonuses, cycling, affinity, landfall, flashback, plain removal and card draw).
 */
class FinTemplatesScenariosTest {
    private fun lands(land: String, n: Int) = Array(n) { land }

    // CR 702.182a
    @Test
    fun jobSelectBonusesAndKeywords() = scenario {
        libraries()
        alice.battlefield(*lands("Mountain", 3), *lands("Forest", 3), *lands("Island", 3), "Plains", "Plains")
        alice.hand(FinSet.roninsNodachi, FinSet.troubadoursLongbow, FinSet.pickpocketsDirk, FinSet.pugilistsCesti)
        alice.casts(FinSet.roninsNodachi)
        resolveAll()
        alice.casts(FinSet.troubadoursLongbow)
        resolveAll()
        alice.casts(FinSet.pickpocketsDirk)
        resolveAll()
        alice.casts(FinSet.pugilistsCesti, paying = listOf("Plains", "Plains"))
        resolveAll()
        val heroes = state.battlefield.filter { state.obj(it)?.token == true }
        expect {
            heroes.size shouldBe 4
            heroes.mapNotNull { view()[it]?.characteristics?.power }.sorted() shouldBe listOf(2, 2, 3, 3)
            heroes.count { view()[it]!!.characteristics!!.abilities.any { a -> a.ability == Keyword.Reach } } shouldBe 1
            heroes.count { view()[it]!!.characteristics!!.abilities.any { a -> a.ability == Keyword.Haste } } shouldBe 1
        }
    }

    @Test
    fun clawedProwlerTaps() = scenario {
        alice.battlefield(FinSet.clawedProwler, "Plains", "Plains")
        bob.battlefield(ogre)
        alice.activates(FinSet.clawedProwler, target = ogre)
        resolveAll()
        expect { ogre.tapped shouldBe true }
    }

    // CR 702.29a: landcycling
    @Test
    fun skyboundMoogleCountersAndCycles() = scenario {
        alice.library("Plains", notes)
        alice.battlefield(*lands("Plains", 7), bear)
        alice.hand(FinSet.skyboundMoogle, FinSet.skyboundMoogle)
        alice.activates(FinSet.skyboundMoogle)
        resolveAll()
        choose("Plains")
        alice.casts(FinSet.skyboundMoogle)
        resolveAll()
        targets(bear)
        expect {
            bear.counters(p1p1) shouldBe 1
            alice.hand.names.count { it == "Plains" } shouldBe 1
        }
    }

    @Test
    fun dreamOfTheFarawayAndItsFlashback() = scenario {
        libraries()
        alice.battlefield(*lands("Island", 6))
        alice.hand(FinSet.dreamOfTheFaraway)
        alice.casts(FinSet.dreamOfTheFaraway)
        resolveAll()
        choose()
        alice.casts(FinSet.dreamOfTheFaraway)
        resolveAll()
        choose()
        expect {
            alice.hand.size shouldBe 2
            FinSet.dreamOfTheFaraway.zone shouldBe Zone.Exile
        }
    }

    @Test
    fun ejectionSeatBouncesAndDraws() = scenario {
        libraries()
        alice.battlefield(*lands("Island", 4))
        alice.hand(FinSet.ejectionSeat)
        bob.battlefield(giant)
        alice.casts(FinSet.ejectionSeat, target = giant)
        resolveAll()
        expect {
            bob.hand has giant
            alice.hand.size shouldBe 1
        }
    }

    // CR 702.41a: affinity for artifacts
    @Test
    fun valkyrieGunshipCostsLessAndSurveils() = scenario {
        libraries()
        alice.battlefield(trinket, trinket, trinket, lance, *lands("Island", 3))
        alice.hand(FinSet.valkyrieGunship)
        alice.casts(FinSet.valkyrieGunship)
        resolveAll()
        choose()
        expect { FinSet.valkyrieGunship.zone shouldBe Zone.Battlefield }
    }

    @Test
    fun underwayRatLeavesTreasure() = scenario {
        alice.battlefield(FinSet.underwayRat, "Swamp")
        alice.hand(doom)
        alice.casts(doom, target = FinSet.underwayRat)
        resolveAll()
        expect { alice.battlefield has "Treasure Token" }
    }

    @Test
    fun landfallCreaturesGrow() = scenario {
        alice.battlefield(FinSet.saszsChick)
        alice.hand("Forest")
        alice.plays("Forest")
        resolveAll()
        expect { FinSet.saszsChick.counters(p1p1) shouldBe 1 }
    }

    @Test
    fun burrowScoutGrowsWhenAnotherCreatureEnters() = scenario {
        alice.battlefield(FinSet.burrowScout, "Forest", "Forest")
        alice.hand(bear)
        alice.casts(bear)
        resolveAll()
        expect { FinSet.burrowScout.power shouldBe 4 }
    }

    @Test
    fun meadowRexaurGainsLife() = scenario {
        alice.battlefield(*lands("Forest", 6))
        alice.hand(FinSet.meadowRexaur)
        alice.casts(FinSet.meadowRexaur)
        resolveAll()
        expect { alice.life shouldBe 23 }
    }

    @Test
    fun keepFightingReturnsTwoCreatures() = scenario {
        alice.battlefield(*lands("Swamp", 3))
        alice.graveyard(bear, ogre)
        alice.hand(FinSet.keepFighting)
        alice.casts(FinSet.keepFighting, targets = listOf(bear, ogre))
        resolveAll()
        expect { alice.hand.size shouldBe 2 }
    }

    @Test
    fun strikerShotPumpsWithTrample() = scenario {
        alice.battlefield(bear, "Forest", "Forest")
        alice.hand(FinSet.strikerShot)
        alice.casts(FinSet.strikerShot, target = bear)
        resolveAll()
        expect {
            bear.power shouldBe 5
            bear hasKeyword Keyword.Trample
        }
    }

    @Test
    fun runnerGreensMakesABird() = scenario {
        alice.battlefield("Forest", "Forest")
        alice.hand(FinSet.runnerGreens)
        alice.casts(FinSet.runnerGreens)
        resolveAll()
        expect { alice.battlefield has "Bird Token" }
    }

    @Test
    fun cohortReinforcementsMillsAndGains() = scenario {
        alice.library(notes, notes, notes, notes)
        alice.battlefield(*lands("Swamp", 3))
        alice.hand(FinSet.cohortReinforcements)
        alice.casts(FinSet.cohortReinforcements)
        resolveAll()
        expect {
            alice.graveyard.size shouldBe 3
            alice.life shouldBe 23
        }
    }

    @Test
    fun skyshipCrashDestroysAFlier() = scenario {
        alice.battlefield(*lands("Forest", 3))
        alice.hand(FinSet.skyshipCrash)
        bob.battlefield(flier)
        alice.casts(FinSet.skyshipCrash, target = flier)
        resolveAll()
        expect { flier.zone shouldBe Zone.Graveyard }
    }

    @Test
    fun communeWithOttersKeepsACreature() = scenario {
        alice.library(notes, ogre, notes, notes)
        alice.battlefield("Forest")
        alice.hand(FinSet.communeWithOtters)
        alice.casts(FinSet.communeWithOtters)
        resolveAll()
        choose(ogre)
        choose(notes, notes)
        expect { alice.hand has ogre }
    }

    @Test
    fun manyEyedOozeMakesOpponentsDiscard() = scenario {
        bob.hand(ogre)
        alice.battlefield("Swamp", "Swamp")
        alice.hand(FinSet.manyEyedOoze)
        alice.casts(FinSet.manyEyedOoze)
        resolveAll()
        expect { bob.graveyard has ogre }
    }

    @Test
    fun greatToadWithSevenLands() = scenario {
        alice.battlefield(FinSet.greatToad, *lands("Forest", 7))
        expect { FinSet.greatToad.power shouldBe 6 }
    }

    @Test
    fun ahrielEyeSacrificesToDraw() = scenario {
        libraries()
        alice.battlefield(FinSet.ahrielEye, trinket, *lands("Swamp", 3))
        alice.activates(FinSet.ahrielEye, choosing = listOf(trinket))
        resolveAll()
        expect { alice.hand.size shouldBe 1 }
    }

    @Test
    fun lorneColemanLootsOnDamage() = scenario {
        alice.library(notes, notes)
        bob.library(notes)
        alice.battlefield(FinSet.lorneColeman)
        alice.hand(ogre)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(FinSet.lorneColeman)
        resolveAll()
        advanceTo(TurnStep.CombatDamage)
        resolveAll()
        choose(ogre)
        expect {
            alice.graveyard has ogre
            alice.life shouldBe 22
        }
    }

    @Test
    fun lunarPandoraSurveilsAndDestroys() = scenario {
        alice.library(notes, notes)
        alice.battlefield(FinSet.lunarPandora, *lands("Plains", 6))
        bob.battlefield(charm)
        alice.activates(FinSet.lunarPandora, ability = 1, target = charm)
        resolveAll()
        expect {
            charm.zone shouldBe Zone.Graveyard
            FinSet.lunarPandora.zone shouldBe Zone.Graveyard
        }
    }

    @Test
    fun worldAtlasFindsABasicLand() = scenario {
        alice.library("Forest", notes)
        alice.battlefield(FinSet.worldAtlas, "Plains")
        alice.activates(FinSet.worldAtlas)
        resolveAll()
        choose("Forest")
        expect { alice.hand has "Forest" }
    }

    @Test
    fun villainsDemiseDestroysAndGains() = scenario {
        alice.battlefield(*lands("Swamp", 4))
        alice.hand(FinSet.villainsDemise)
        bob.battlefield(giant)
        alice.casts(FinSet.villainsDemise, target = giant)
        resolveAll()
        expect {
            giant.zone shouldBe Zone.Graveyard
            alice.life shouldBe 22
        }
    }

    @Test
    fun judgeMagistrateGavranGrowsWhenThingsDie() = scenario {
        alice.battlefield(FinSet.judgeMagistrateGavran, bear, "Swamp")
        alice.hand(doom)
        alice.casts(doom, target = bear)
        resolveAll()
        expect { FinSet.judgeMagistrateGavran.counters(p1p1) shouldBe 1 }
    }

    @Test
    fun capitalCitadelCycles() = scenario {
        alice.library(notes)
        alice.battlefield("Plains", "Plains")
        alice.hand(FinSet.capitalCitadel)
        alice.activates(FinSet.capitalCitadel, ability = 2)
        resolveAll()
        expect { alice.hand has notes }
    }

    @Test
    fun travelersRestGainsLife() = scenario {
        alice.hand(FinSet.travelersRest)
        alice.plays(FinSet.travelersRest)
        resolveAll()
        expect { alice.life shouldBe 22 }
    }
}
