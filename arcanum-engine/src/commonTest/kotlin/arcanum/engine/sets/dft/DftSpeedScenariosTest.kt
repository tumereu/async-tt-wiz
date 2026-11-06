package arcanum.engine.sets.dft

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.dft.DftPlain.bear
import arcanum.engine.sets.dft.DftPlain.elf
import arcanum.engine.sets.dft.DftPlain.giant
import arcanum.engine.sets.dft.DftPlain.goblin
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
 * Aetherdrift parser chunks 06 and 07 in play: start your engines! (CR 702.179) and max speed (CR 702.178). Leonin, Glitchwraith, Mutant,
 * Goblin and Loxodon Pathfinder share the Surveyor graveyard draw (played by Leonin Pathfinder); Roosting Bot, Swiftfeather Assailant,
 * Streaking Oilguzzler, Burnout Bashrider, Gastal Thrillchaser and Walking Sepulcher are max speed statics like Gastal Plunderer's; Risen
 * Necrolord repeats Embalmed Ascender's max speed trigger shape; Endrider Catalyst and Muraganda Speedway repeat Starting Grid's max speed
 * activated ability with a mana ability; Avishkar and Amonkhet Speedway are covered draws and haste behind max speed.
 */
class DftSpeedScenariosTest {
    // CR 702.179a: a permanent with start your engines! gives speed 1
    @Test
    fun startYourEnginesGivesSpeedOne() = scenario {
        alice.battlefield(DftSet.leoninPathfinder)
        settle()
        assertEquals(1, speedOf(alice))
    }

    // CR 702.178a, b: the graveyard draw only at max speed
    @Test
    fun leoninPathfinderDrawsFromTheGraveyardAtMaxSpeed() = scenario {
        alice.battlefield("Plains", "Plains", "Plains")
        alice.graveyard(DftSet.leoninPathfinder)
        alice.library(notes)
        setSpeed(alice, 4)
        alice.activates(DftSet.leoninPathfinder, ability = 0)
        resolveAll()
        expect { alice.hand has notes }
    }

    @Test
    fun leoninPathfinderStrikesFirstOnYourTurn() = scenario {
        alice.battlefield(DftSet.leoninPathfinder)
        expect { DftSet.leoninPathfinder hasKeyword Keyword.FirstStrike.kind }
    }

    // CR 702.178b: cast from the graveyard at max speed
    @Test
    fun gleamwheelUpgradesCastFromTheGraveyard() = scenario {
        alice.battlefield("Plains", bear)
        alice.graveyard(DftSet.gleamwheelUpgrades)
        setSpeed(alice, 4)
        alice.casts(DftSet.gleamwheelUpgrades, target = bear)
        resolveAll()
        expect {
            bear.power shouldBe 3
            bear hasKeyword Keyword.Vigilance.kind
        }
    }

    @Test
    fun hazardousSnareCountersAtMaxSpeed() = scenario {
        alice.battlefield(*Array(3) { "Plains" }, bear)
        alice.hand(DftSet.hazardousSnare)
        bob.battlefield(golem)
        alice.casts(DftSet.hazardousSnare)
        targets(golem)
        expect { golem.zone shouldBe Zone.Exile }
        setSpeed(alice, 4)
        alice.activates(DftSet.hazardousSnare, target = bear)
        resolveAll()
        expect { bear.counters(p1p1) shouldBe 1 }
    }

    @Test
    fun honorOfTheHighwayGivesDoubleStrikeAtMaxSpeed() = scenario {
        libraries()
        alice.battlefield(DftSet.honorOfTheHighway, bear)
        setSpeed(alice, 4)
        advanceTo(TurnStep.BeginningOfCombat)
        targets(bear)
        expect { bear hasKeyword Keyword.DoubleStrike.kind }
    }

    @Test
    fun aetherSiphonerMillsAtMaxSpeed() = scenario {
        alice.battlefield(DftSet.aetherSiphoner, "Island", "Island")
        alice.library(notes, notes)
        bob.library(notes, notes, notes)
        setSpeed(alice, 4)
        alice.activates(DftSet.aetherSiphoner)
        resolveAll()
        expect { bob.graveyard has notes }
    }

    // CR 707.10: the copy of a spell you control, new targets allowed
    @Test
    fun slickMimicCopiesASpell() = scenario {
        alice.battlefield(DftSet.slickMimic, "Mountain", "Mountain")
        alice.hand(zap)
        bob.battlefield(bear, elf)
        setSpeed(alice, 4)
        alice.casts(zap, target = bear)
        alice.activates(DftSet.slickMimic, target = zap)
        resolveAll()
        yes()
        targets(elf)
        expect {
            bear.zone shouldBe Zone.Graveyard
            elf.zone shouldBe Zone.Graveyard
        }
    }

    // CR 614.1a: each draw becomes two at max speed
    @Test
    fun wxxnvDrawsTwiceAtMaxSpeed() = scenario {
        alice.battlefield(DftSet.wxxnvWordyHost, "Island")
        alice.hand(notes)
        alice.library(notes, notes, notes)
        setSpeed(alice, 4)
        alice.casts(notes)
        resolveAll()
        expect { alice.hand.size shouldBe 2 }
    }

    @Test
    fun fuelGuzzlerEntersTappedAndSacrificesForCards() = scenario {
        alice.battlefield("Swamp", "Swamp", bear)
        alice.hand(DftSet.fuelGuzzler)
        alice.library(notes)
        alice.casts(DftSet.fuelGuzzler)
        resolveAll()
        expect { DftSet.fuelGuzzler.tapped shouldBe true }
        setSpeed(alice, 4)
        alice.activates(DftSet.fuelGuzzler, choosing = listOf(bear))
        resolveAll()
        expect { alice.hand has notes }
    }

    @Test
    fun gastalPlundererTakesAnInstantAndGrows() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.hand(DftSet.gastalPlunderer)
        bob.hand(zap, bear)
        alice.casts(DftSet.gastalPlunderer)
        targets("bob")
        choose(zap)
        expect { bob.graveyard has zap }
        setSpeed(alice, 4)
        expect {
            DftSet.gastalPlunderer.power shouldBe 3
            DftSet.gastalPlunderer hasKeyword Keyword.Menace.kind
        }
    }

    @Test
    fun hourOfTriumphTutorsAtMaxSpeed() = scenario {
        alice.battlefield(*Array(5) { "Swamp" })
        alice.hand(DftSet.hourOfTriumph)
        alice.library(notes, giant)
        alice.casts(DftSet.hourOfTriumph)
        resolveAll()
        expect { "Zombie Token".zone shouldBe Zone.Battlefield }
        setSpeed(alice, 4)
        alice.activates(DftSet.hourOfTriumph)
        resolveAll()
        choose(giant)
        expect { alice.hand has giant }
    }

    // CR 118.12: the opponent without a creature or Vehicle discards; life by speed
    @Test
    fun momentumCrusherEdictsAndGainsBySpeed() = scenario {
        alice.battlefield(*Array(4) { "Swamp" })
        alice.hand(DftSet.momentumCrusher)
        bob.hand(notes)
        alice.casts(DftSet.momentumCrusher)
        resolveAll()
        expect { bob.graveyard has notes }
        setSpeed(alice, 3)
        alice.activates(DftSet.momentumCrusher)
        resolveAll()
        expect { alice.life shouldBe 23 }
    }

    @Test
    fun embalmedAscenderDrainsAtMaxSpeed() = scenario {
        alice.battlefield(*Array(3) { "Swamp" }, "Plains", bear)
        alice.hand(DftSet.embalmedAscender)
        bob.battlefield("Mountain")
        bob.hand(zap)
        alice.casts(DftSet.embalmedAscender)
        resolveAll()
        setSpeed(alice, 4)
        alice.passes()
        bob.casts(zap, target = bear)
        resolveAll()
        expect {
            bob.life shouldBe 19
            alice.life shouldBe 21
        }
    }

    // CR 508.1d, 509.1a: it can't attack or block below max speed
    @Test
    fun hazoraAttacksOnlyAtMaxSpeed() = scenario {
        libraries()
        alice.battlefield(DftSet.hazoraGodracer)
        advanceTo(TurnStep.DeclareAttackers)
        val canAttack = arcanum.engine.Engine.options(state, alice.id).any { it is arcanum.engine.input.Input.DeclareAttackers && it.attacks.isNotEmpty() }
        assertEquals(false, canAttack)
    }

    @Test
    fun howlpackHeavyMakesAttackingGoblins() = scenario {
        libraries()
        alice.battlefield(DftSet.howlpackHeavy, goblin)
        expect { goblin hasKeyword Keyword.Haste.kind }
        advanceTo(TurnStep.BeginningOfCombat)
        resolveAll()
        expect { "Goblin Token" hasKeyword Keyword.Haste.kind }
        setSpeed(alice, 4)
        alice.activates(DftSet.howlpackHeavy)
        resolveAll()
        expect { alice.pool.size shouldBe 3 }
    }

    @Test
    fun kickoffFestivitiesRummagesAndHastes() = scenario {
        alice.battlefield("Mountain", "Mountain", bear)
        alice.hand(DftSet.kickoffFestivities, notes)
        alice.library(notes, notes)
        alice.casts(DftSet.kickoffFestivities)
        resolveAll()
        yes()
        choose(notes)
        expect { alice.hand.size shouldBe 2 }
        setSpeed(alice, 4)
        alice.activates(DftSet.kickoffFestivities)
        resolveAll()
        expect { bear hasKeyword Keyword.Haste.kind }
    }

    // CR 702.179e, f: each player without max speed is dealt damage
    @Test
    fun outrunOblivionHitsPlayersWithoutMaxSpeed() = scenario {
        alice.battlefield(*Array(5) { "Mountain" })
        alice.hand(DftSet.outrunOblivion)
        bob.battlefield(giant)
        alice.casts(DftSet.outrunOblivion)
        targets(giant)
        expect { giant.zone shouldBe Zone.Graveyard }
        setSpeed(alice, 4)
        alice.activates(DftSet.outrunOblivion)
        resolveAll()
        expect {
            bob.life shouldBe 18
            alice.life shouldBe 20
        }
    }

    @Test
    fun markTheWaySearchesBySpeed() = scenario {
        alice.battlefield(*Array(5) { "Forest" })
        alice.hand(DftSet.markTheWay)
        alice.library("Forest", "Forest", "Forest", notes)
        alice.casts(DftSet.markTheWay)
        resolveAll()
        setSpeed(alice, 2)
        alice.activates(DftSet.markTheWay)
        resolveAll()
        choose("Forest", "Forest")
        assertEquals(7, cards("Forest").count { it.zone == Zone.Battlefield })
    }

    // CR 614.1a: one more damage to opponents and their permanents
    @Test
    fun farFortunaAddsDamage() = scenario {
        libraries()
        alice.battlefield(DftSet.farFortunaFinalBoss, "Mountain")
        alice.hand(zap)
        setSpeed(alice, 4)
        alice.casts(zap, target = "bob")
        resolveAll()
        expect { bob.life shouldBe 17 }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(DftSet.farFortunaFinalBoss)
        resolveAll()
        expect { bob.life shouldBe 15 }
    }

    @Test
    fun mendicantHeartCopiesArtifactSpells() = scenario {
        alice.battlefield(DftSet.mendicantHeartPathlight, "Island", "Island", "Plains")
        alice.hand(trinket)
        expect { DftSet.mendicantHeartPathlight.power shouldBe 1 }
        setSpeed(alice, 4)
        alice.casts(trinket)
        resolveAll()
        payYes()
        expect {
            DftSet.mendicantHeartPathlight.power shouldBe 3
        }
    }

    // CR 601.2f: noncreature spells cost {X} less by speed; others get +X/+0
    @Test
    fun samraBoostsBySpeed() = scenario {
        alice.battlefield(DftSet.samraTheDrivingForce, bear, "Mountain")
        alice.hand(DftSet.lightningJolt)
        setSpeed(alice, 2)
        expect { bear.power shouldBe 4 }
        alice.casts(DftSet.lightningJolt, target = "bob")
        resolveAll()
        expect { bob.life shouldBe 17 }
    }

    @Test
    fun zahraSurveilsAndMakesZombiesAtMaxSpeed() = scenario {
        alice.battlefield(DftSet.zahraGlorysPast, bear)
        alice.library(notes, notes)
        setSpeed(alice, 4)
        alice.activates(DftSet.zahraGlorysPast, choosing = listOf(bear))
        resolveAll()
        expect {
            "Zombie Token".tapped shouldBe true
            bear.zone shouldBe Zone.Graveyard
        }
    }

    @Test
    fun racersLeaderboardDrawsAndDiscounts() = scenario {
        alice.battlefield(*Array(4) { "Plains" }, "Mountain")
        alice.hand(DftSet.racersLeaderboard, notes, DftSet.lightningJolt)
        alice.library(notes, notes)
        alice.casts(DftSet.racersLeaderboard, paying = List(4) { "Plains" })
        resolveAll()
        choose(notes)
        setSpeed(alice, 4)
        alice.casts(DftSet.lightningJolt, target = "bob")
        resolveAll()
        expect { bob.life shouldBe 17 }
    }

    @Test
    fun startingGridLootsAtMaxSpeed() = scenario {
        alice.battlefield(DftSet.startingGrid)
        alice.hand(notes)
        alice.library(notes, notes)
        setSpeed(alice, 4)
        alice.activates(DftSet.startingGrid, ability = 1)
        resolveAll()
        choose(notes)
        expect { DftSet.startingGrid.zone shouldBe Zone.Graveyard }
    }
}
