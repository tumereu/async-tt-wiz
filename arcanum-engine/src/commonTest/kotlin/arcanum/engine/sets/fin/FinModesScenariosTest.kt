package arcanum.engine.sets.fin

import arcanum.api.Keyword
import arcanum.api.Zone
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.fin.FinExtras.bear
import arcanum.engine.sets.fin.FinExtras.charm
import arcanum.engine.sets.fin.FinExtras.doom
import arcanum.engine.sets.fin.FinExtras.giant
import arcanum.engine.sets.fin.FinExtras.notes
import arcanum.engine.sets.fin.FinExtras.ogre
import arcanum.engine.sets.fin.FinExtras.stun
import arcanum.engine.sets.fin.FinExtras.trinket
import kotlin.test.Test

/**
 * Final Fantasy chunk 02 in play: tiered spells (CR 702.183a: one additional cost, its mode) and modal spells and triggers (CR 700.2).
 * Ember Litany's and Storm Litany's tiers repeat the tested damage tiers of each other; Tactics Menu, Serenade of the Stage, Caller's
 * Homecoming and Tainted Spring choose among covered effects (one mode each tested where it is new).
 */
class FinModesScenariosTest {
    // CR 702.183a, 601.2f: the chosen tier's cost is added
    @Test
    fun frostLitanyBouncesForFree() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.hand(FinSet.frostLitany)
        bob.battlefield(ogre)
        alice.casts(FinSet.frostLitany, modes = listOf(0), target = ogre)
        resolveAll()
        expect {
            bob.hand has ogre
            card("Island", 2).tapped shouldBe false
        }
    }

    @Test
    fun frostLitanyShufflesAtTheTopTier() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island", "Island", "Island", "Island")
        alice.hand(FinSet.frostLitany)
        bob.battlefield(ogre)
        alice.casts(FinSet.frostLitany, modes = listOf(2), target = ogre)
        resolveAll()
        expect { bob.library has ogre }
    }

    @Test
    fun frostLitanyOwnerChoosesTopOrBottom() = scenario {
        bob.library(notes, notes)
        alice.battlefield("Island", "Island", "Island", "Island")
        alice.hand(FinSet.frostLitany)
        bob.battlefield(ogre)
        alice.casts(FinSet.frostLitany, modes = listOf(1), target = ogre)
        resolveAll()
        option(pending.let { (it as arcanum.engine.decision.Decision.ChooseOption).options.last() })
        expect { bob.library has ogre }
    }

    @Test
    fun emberLitanyDamagesEveryCreature() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", bear)
        alice.hand(FinSet.emberLitany)
        bob.battlefield(ogre)
        alice.casts(FinSet.emberLitany, modes = listOf(1))
        resolveAll()
        expect {
            bear.zone shouldBe Zone.Graveyard
            ogre.zone shouldBe Zone.Battlefield
        }
    }

    @Test
    fun stormLitanyHitsHarderForMore() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(FinSet.stormLitany)
        bob.battlefield(ogre)
        alice.casts(FinSet.stormLitany, modes = listOf(1), target = ogre)
        resolveAll()
        expect { ogre.zone shouldBe Zone.Graveyard }
    }

    @Test
    fun arenaColossusDestroysAnArtifact() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest", "Forest", "Forest")
        alice.hand(FinSet.arenaColossus)
        bob.battlefield(trinket)
        alice.casts(FinSet.arenaColossus)
        resolveAll()
        modes(0)
        targets(trinket)
        expect { trinket.zone shouldBe Zone.Graveyard }
    }

    @Test
    fun clashOfTheTitansFightsAndAddsLore() = scenario {
        alice.battlefield("Forest", giant, FinSet.conjureMoonhowlWolf)
        alice.hand(FinSet.clashOfTheTitans)
        bob.battlefield(ogre)
        alice.casts(FinSet.clashOfTheTitans, modes = listOf(0, 2), targets = listOf(giant, ogre, FinSet.conjureMoonhowlWolf))
        resolveAll()
        expect {
            ogre.zone shouldBe Zone.Graveyard
            FinSet.conjureMoonhowlWolf.counters(FinExtras.lore) shouldBe 1
        }
    }

    @Test
    fun angelFeatherReturnsACreature() = scenario {
        alice.battlefield(FinSet.angelFeather, "Plains", "Plains")
        alice.graveyard(ogre)
        alice.activates(FinSet.angelFeather, modes = listOf(0), target = ogre)
        resolveAll()
        expect {
            ogre.zone shouldBe Zone.Battlefield
            ogre.tapped shouldBe true
            FinSet.angelFeather.zone shouldBe Zone.Exile
        }
    }

    // CR 613.4b: base power and toughness; CR 603.6c: the granted dies trigger returns it
    @Test
    fun beastformCrescendoSetsBaseStatsAndReturns() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", bear)
        alice.hand(FinSet.beastformCrescendo, doom)
        alice.casts(FinSet.beastformCrescendo, modes = listOf(1), target = bear)
        resolveAll()
        expect {
            bear.power shouldBe 5
            bear.toughness shouldBe 2
        }
        alice.casts(doom, target = bear)
        resolveAll()
        expect {
            bear.zone shouldBe Zone.Battlefield
            bear.tapped shouldBe true
            bear.power shouldBe 2
        }
    }

    // CR 701.10: doubling and tripling power and toughness
    @Test
    fun brawlersCrescendoDoubles() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", ogre)
        alice.hand(FinSet.brawlersCrescendo)
        alice.casts(FinSet.brawlersCrescendo, modes = listOf(1), target = ogre)
        resolveAll()
        expect {
            ogre.power shouldBe 6
            ogre.toughness shouldBe 6
        }
    }

    @Test
    fun tacticsMenuDestroysABigCreature() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.hand(FinSet.tacticsMenu)
        bob.battlefield(giant)
        alice.casts(FinSet.tacticsMenu, modes = listOf(2), target = giant)
        resolveAll()
        expect { giant.zone shouldBe Zone.Graveyard }
    }

    // CR 122.1d: a stun counter on one of the tapped creatures
    @Test
    fun rescueByTheStairsTapsAndStuns() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains")
        alice.hand(FinSet.rescueByTheStairs)
        bob.battlefield(ogre, bear)
        alice.casts(FinSet.rescueByTheStairs, modes = listOf(1), targets = listOf(ogre, bear))
        resolveAll()
        choose(ogre)
        expect {
            ogre.tapped shouldBe true
            bear.tapped shouldBe true
            ogre.counters(stun) shouldBe 1
        }
    }

    @Test
    fun rescueByTheStairsMakesHeroes() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains")
        alice.hand(FinSet.rescueByTheStairs)
        alice.casts(FinSet.rescueByTheStairs, modes = listOf(0))
        resolveAll()
        expect { alice.battlefield.names.count { it == "Hero Token" } shouldBe 3 }
    }

    @Test
    fun ironfistedJudgeMakesEachPlayerSacrificeANontokenCreature() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", bear)
        alice.hand(FinSet.ironfistedJudge)
        bob.battlefield(ogre)
        alice.casts(FinSet.ironfistedJudge)
        resolveAll()
        modes(1)
        choose(bear)
        choose(ogre)
        expect {
            ogre.zone shouldBe Zone.Graveyard
            FinSet.ironfistedJudge.zone shouldBe Zone.Battlefield
        }
    }

    @Test
    fun serenadeOfTheStageExilesToPlay() = scenario {
        alice.library(ogre, notes)
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(FinSet.serenadeOfTheStage)
        alice.casts(FinSet.serenadeOfTheStage, modes = listOf(0))
        resolveAll()
        alice.casts(ogre)
        resolveAll()
        expect { ogre.zone shouldBe Zone.Battlefield }
    }

    // CR 614.1a: "if that creature would die this turn, exile it instead"
    @Test
    fun ropeSlamExilesWhatItKills() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.hand(FinSet.ropeSlam)
        bob.battlefield(ogre)
        alice.casts(FinSet.ropeSlam, modes = listOf(0), target = ogre)
        resolveAll()
        expect { ogre.zone shouldBe Zone.Exile }
    }

    @Test
    fun callersHomecomingReturnsTwoPermanentCards() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest")
        alice.hand(FinSet.callersHomecoming)
        alice.graveyard(ogre, charm)
        alice.casts(FinSet.callersHomecoming, modes = listOf(1), targets = listOf(ogre, charm))
        resolveAll()
        expect {
            alice.hand has ogre
            alice.hand has charm
        }
    }

    // CR 120.6: dealt damage this turn
    @Test
    fun graveGnawerDestroysADamagedCreature() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Mountain")
        alice.hand(FinSet.graveGnawer, FinExtras.zap)
        bob.battlefield(giant)
        alice.casts(FinExtras.zap, target = giant)
        resolveAll()
        alice.casts(FinSet.graveGnawer)
        resolveAll()
        modes(0)
        targets(giant)
        expect { giant.zone shouldBe Zone.Graveyard }
    }

    @Test
    fun graveGnawerExilesFromOneGraveyard() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.hand(FinSet.graveGnawer)
        bob.graveyard(ogre, bear)
        alice.casts(FinSet.graveGnawer)
        resolveAll()
        modes(1)
        targets(ogre, bear)
        expect {
            ogre.zone shouldBe Zone.Exile
            bear.zone shouldBe Zone.Exile
        }
    }

    @Test
    fun mendingLitanyProtectsEverythingAtTheTop() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains", bear)
        alice.hand(FinSet.mendingLitany)
        alice.casts(FinSet.mendingLitany, modes = listOf(2))
        resolveAll()
        expect {
            alice.life shouldBe 26
            bear hasKeyword Keyword.Hexproof
            bear hasKeyword Keyword.Indestructible
        }
    }

    @Test
    fun taintedSpringTakesAnArtifactOrCreature() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.hand(FinSet.taintedSpring)
        bob.hand(ogre, notes)
        alice.casts(FinSet.taintedSpring, modes = listOf(1), target = "bob")
        resolveAll()
        choose(ogre)
        expect {
            bob.graveyard has ogre
            bob.hand has notes
        }
    }

    @Test
    fun taintedSpringShrinksEveryone() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.hand(FinSet.taintedSpring)
        bob.battlefield(FinExtras.flier)
        alice.casts(FinSet.taintedSpring, modes = listOf(0))
        resolveAll()
        expect { FinExtras.flier.zone shouldBe Zone.Graveyard }
    }

    @Test
    fun serenadeOfTheStagePumpsTwo() = scenario {
        alice.battlefield("Mountain", "Mountain", bear, ogre)
        alice.hand(FinSet.serenadeOfTheStage)
        alice.casts(FinSet.serenadeOfTheStage, modes = listOf(1), targets = listOf(bear, ogre))
        resolveAll()
        expect {
            bear.power shouldBe 4
            ogre.power shouldBe 5
        }
    }

    @Test
    fun tacticsMenuMakesAKnight() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.hand(FinSet.tacticsMenu)
        alice.casts(FinSet.tacticsMenu, modes = listOf(0))
        resolveAll()
        expect { alice.battlefield has "Knight Token" }
    }
}
