package arcanum.engine.sets.tla

import arcanum.api.CardType
import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.Subtype
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.tla.TlaExtras.bear
import arcanum.engine.sets.tla.TlaExtras.brute
import arcanum.engine.sets.tla.TlaExtras.flier
import arcanum.engine.sets.tla.TlaExtras.notes
import arcanum.engine.sets.tla.TlaExtras.ogre
import arcanum.engine.sets.tla.TlaExtras.p1p1
import arcanum.engine.sets.tla.TlaExtras.trinket
import arcanum.engine.sets.tla.TlaExtras.zap
import kotlin.test.Test

/**
 * Avatar: The Last Airbender chunks 13 and 14 cards in play: the blue and black cards (second-draw triggers, copies with changes,
 * an Aura that makes a 1/1 merchant, sacrifice outlets, a mask-stealing Spirit). Omen-Reading Seer (a Clue as it enters), Ravine Creeper
 * (a Food as it enters, swampcycling) and Crooked Palace Clerk (a discard as it enters) repeat tested templates; Gecko Macaw has only
 * covered keywords.
 */
class TlaBlueBlackScenariosTest {
    private val hero = Cards.invent("Tide Hero", "{1}{U}", "Legendary Creature — Human Ally", "", "1/1")
    private val looter = Cards.invent("Card Sage", "{U}", "Instant", "Draw two cards.")

    // ---- chunk 13

    /** A copy of your creature that isn't legendary and is a 4/4 Hero too. */
    // CR 707.9b
    @Test
    fun cinderIslePageantHeroCopy() = scenario {
        alice.battlefield(hero, *Array(5) { "Island" })
        alice.hand(TlaSet.cinderIslePageant)
        alice.casts(TlaSet.cinderIslePageant, modes = listOf(0), target = hero)
        resolveAll()
        expect {
            card(hero, 1).power shouldBe 4
            card(hero, 1).subtypes.contains(Subtype("Hero")) shouldBe true
            card(hero, 0).zone shouldBe Zone.Battlefield
        }
    }

    /** Tapped, counters removed; it's a 1/1 Citizen named Humble Merchant that taps for {C}. */
    // CR 613.1
    @Test
    fun humbleLabor() = scenario {
        alice.battlefield("Island")
        alice.hand(TlaSet.humbleLabor)
        bob.battlefield(TlaExtras.brute, counters = listOf(p1p1 to 2))
        alice.casts(TlaSet.humbleLabor, target = brute)
        resolveAll()
        expect {
            brute.view.characteristics?.name shouldBe "Humble Merchant"
            brute.power shouldBe 1
            brute.tapped shouldBe true
            brute.counters(p1p1) shouldBe 0
            brute.subtypes shouldBe listOf(Subtype("Citizen"))
        }
    }

    /** The second card drawn this turn: a counter; dying, a Clue. */
    @Test
    fun loreHunter() = scenario {
        alice.battlefield(TlaSet.loreHunter, "Island")
        alice.hand(looter)
        alice.library(notes, notes)
        alice.casts(looter)
        resolveAll()
        expect { TlaSet.loreHunter.counters(p1p1) shouldBe 1 }
    }

    /** A noncreature spell makes a Clue; the Clue becomes a 3/1 flier. */
    @Test
    fun tinkererSkyArtisan() = scenario {
        alice.battlefield(TlaSet.theTinkererSkyArtisan, "Island")
        alice.hand(notes)
        alice.library(notes)
        alice.casts(notes)
        resolveAll()
        alice.activates(TlaSet.theTinkererSkyArtisan, target = "Clue Token")
        resolveAll()
        expect {
            "Clue Token".power shouldBe 3
            "Clue Token" hasKeyword Keyword.Flying
        }
    }

    @Test
    fun ferretPuffin() = scenario {
        alice.battlefield(TlaSet.ferretPuffin, "Island")
        alice.hand(looter)
        alice.library(notes, notes)
        alice.casts(looter)
        resolveAll()
        expect {
            TlaSet.ferretPuffin.toughness shouldBe 3
            TlaSet.ferretPuffin hasMechanic { it == arcanum.api.Mechanic.CantBeBlocked }
        }
    }

    @Test
    fun rambunctiousSnowpelters() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.hand(TlaSet.rambunctiousSnowpelters)
        bob.battlefield(ogre)
        alice.casts(TlaSet.rambunctiousSnowpelters)
        resolveAll()
        targets(ogre)
        expect {
            ogre.tapped shouldBe true
            ogre.counters(CounterKind.Stun) shouldBe 1
        }
    }

    /** Fliers attacking: loot; discarding a nonland card puts a counter on a creature. */
    // CR 603.12
    @Test
    fun pellSpiritedGlider() = scenario {
        alice.battlefield(TlaSet.pellSpiritedGlider, bear)
        alice.hand(zap)
        alice.library(notes)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(TlaSet.pellSpiritedGlider)
        resolveAll()
        choose(zap)
        targets(bear)
        expect { bear.counters(p1p1) shouldBe 1 }
    }

    /** Tapped at upkeep; the second draw untaps it. */
    @Test
    fun lynxWalrus() = scenario {
        alice.battlefield(TlaSet.lynxWalrus, "Island")
        alice.hand(notes)
        alice.library(notes, notes, notes)
        bob.library(notes, notes)
        advanceTo(TurnStep.PrecombatMain, turn = 3)
        resolveAll()
        expect { TlaSet.lynxWalrus.tapped shouldBe true }
        alice.casts(notes)
        resolveAll()
        expect { TlaSet.lynxWalrus.tapped shouldBe false }
    }

    /** Tapped, it stays tapped while you control Juna. */
    @Test
    fun junaNerveStriker() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.hand(TlaSet.junaNerveStriker)
        alice.library(notes, notes)
        bob.battlefield(ogre)
        bob.library(notes, notes)
        alice.casts(TlaSet.junaNerveStriker)
        resolveAll()
        targets(ogre)
        advanceTo(TurnStep.PrecombatMain, turn = 2)
        expect { ogre.tapped shouldBe true }
    }

    /** X = 4: four counters, two cards; an opponent searching adds one of each. */
    @Test
    fun orvathArchivist() = scenario {
        alice.battlefield(*Array(6) { "Island" })
        alice.hand(TlaSet.orvathArchivist)
        alice.library(notes, notes, notes)
        bob.battlefield("Forest", "Forest")
        bob.hand(TlaSet.commonRoots)
        bob.library(notes, "Forest")
        alice.casts(TlaSet.orvathArchivist, x = 4)
        resolveAll()
        expect {
            TlaSet.orvathArchivist.counters(p1p1) shouldBe 4
            alice.hand.size shouldBe 2
        }
        advanceTo(TurnStep.PrecombatMain, turn = 2)
        bob.casts(TlaSet.commonRoots)
        resolveAll()
        choose("Forest")
        expect {
            TlaSet.orvathArchivist.counters(p1p1) shouldBe 5
            alice.hand.size shouldBe 3
        }
    }

    /** Four Islands: {2}, {T} draws. */
    @Test
    fun tideshapingScroll() = scenario {
        alice.battlefield(TlaSet.tideshapingScroll, "Island", "Island", "Island", "Island")
        alice.library(notes)
        alice.activates(TlaSet.tideshapingScroll, paying = listOf("Island", "Island"))
        resolveAll()
        expect { alice.hand has notes }
    }

    // ---- chunk 14

    @Test
    fun mantisMaskedTraders() = scenario {
        alice.battlefield(TlaSet.mantisMaskedTraders, trinket)
        alice.library(notes)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(TlaSet.mantisMaskedTraders)
        resolveAll()
        yes()
        choose(trinket)
        expect {
            alice.hand has notes
            TlaSet.mantisMaskedTraders.counters(p1p1) shouldBe 1
        }
    }

    /** It takes the counters of a creature of yours that dies. */
    @Test
    fun vultureHornetSwarm() = scenario {
        alice.battlefield(TlaSet.vultureHornetSwarm, "Mountain")
        alice.battlefield(bear, counters = listOf(p1p1 to 2))
        alice.hand(zap, zap)
        alice.battlefield("Mountain")
        alice.casts(zap, target = bear)
        resolveAll()
        alice.casts(zap, target = bear)
        resolveAll()
        expect { TlaSet.vultureHornetSwarm.counters(p1p1) shouldBe 2 }
    }

    @Test
    fun vultureHornetSwarmSacrifices() = scenario {
        alice.battlefield(trinket, *Array(4) { "Swamp" })
        alice.hand(TlaSet.vultureHornetSwarm)
        alice.library(notes)
        alice.casts(TlaSet.vultureHornetSwarm)
        resolveAll()
        yes()
        choose(trinket)
        expect { alice.hand has notes }
    }

    @Test
    fun heartlessExaminerDies() = scenario {
        alice.battlefield(TlaSet.heartlessExaminer, "Mountain")
        alice.hand(zap)
        alice.casts(zap, target = TlaSet.heartlessExaminer)
        resolveAll()
        expect {
            alice.life shouldBe 19
            alice.battlefield has "Clue Token"
        }
    }

    /** Damage equal to the Swamp count; lifelink gains it. */
    @Test
    fun lynxCaiman() = scenario {
        alice.battlefield(*Array(7) { "Swamp" })
        alice.hand(TlaSet.lynxCaiman)
        alice.casts(TlaSet.lynxCaiman)
        resolveAll()
        targets("bob")
        expect {
            bob.life shouldBe 13
            alice.life shouldBe 27
        }
    }

    /** X = 2: creatures with mana value 2 or less lose their abilities and are destroyed. */
    @Test
    fun dayOfDarkenedSun() = scenario {
        alice.battlefield(*Array(4) { "Swamp" })
        alice.hand(TlaSet.dayOfDarkenedSun)
        bob.battlefield(bear, ogre, TlaSet.permafrostTank)
        alice.casts(TlaSet.dayOfDarkenedSun, x = 2)
        resolveAll()
        expect {
            bear.zone shouldBe Zone.Graveyard
            ogre.zone shouldBe Zone.Battlefield
        }
    }

    /** Its additional cost paid by sacrificing a creature. */
    @Test
    fun lethalExactnessSacrificing() = scenario {
        alice.battlefield(bear, "Swamp")
        alice.hand(TlaSet.lethalExactness)
        bob.battlefield(ogre)
        alice.casts(TlaSet.lethalExactness, target = ogre, option = "Sacrifice", choosing = listOf(bear))
        resolveAll()
        expect {
            ogre.zone shouldBe Zone.Graveyard
            bear.zone shouldBe Zone.Graveyard
        }
    }

    @Test
    fun grandCollapse() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.hand(TlaSet.grandCollapse)
        bob.battlefield(ogre)
        alice.casts(TlaSet.grandCollapse, target = ogre)
        resolveAll()
        expect { ogre.zone shouldBe Zone.Exile }
    }

    /** It may tap itself as it enters to destroy a creature with power 4 or less. */
    @Test
    fun emberLegionDrill() = scenario {
        alice.battlefield(*Array(4) { "Swamp" })
        alice.hand(TlaSet.theEmberLegionDrill)
        bob.battlefield(ogre)
        alice.casts(TlaSet.theEmberLegionDrill)
        resolveAll()
        yes()
        targets(ogre)
        expect {
            ogre.zone shouldBe Zone.Graveyard
            TlaSet.theEmberLegionDrill.tapped shouldBe true
        }
    }

    @Test
    fun emberLegionDrillStripsIndestructible() = scenario {
        alice.battlefield(TlaSet.theEmberLegionDrill, "Swamp")
        bob.battlefield(ogre)
        effect(bob, arcanum.api.Mechanic.GainAbilities(listOf(Keyword.Indestructible)), on = listOf(ogre))
        alice.activates(TlaSet.theEmberLegionDrill)
        resolveAll()
        expect { ogre lacksKeyword Keyword.Indestructible }
    }

    @Test
    fun emberLegionEngineerRaid() = scenario {
        alice.battlefield(TlaSet.emberLegionEngineer, bear, ogre)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        advanceTo(TurnStep.End)
        resolveAll()
        targets(ogre)
        expect { ogre.counters(p1p1) shouldBe 1 }
    }

    /** Attacking makes a tapped and attacking 2/1 flying Ballistic Boulder, sacrificed at the end step. */
    // CR 508.4
    @Test
    fun emberFleetCatapult() = scenario {
        alice.battlefield(TlaSet.emberFleetCatapult, bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        resolveAll()
        expect {
            "Ballistic Boulder".tapped shouldBe true
            "Ballistic Boulder".power shouldBe 2
        }
        advanceTo(TurnStep.CombatDamage)
        expect { bob.life shouldBe 16 }
        advanceTo(TurnStep.End)
        resolveAll()
        expect { alice.battlefield lacks "Ballistic Boulder" }
    }

    @Test
    fun mistyBogHunters() = scenario {
        alice.battlefield(TlaSet.mistyBogHunters, "Island")
        alice.hand(looter)
        alice.library(notes, notes)
        alice.casts(looter)
        resolveAll()
        expect {
            TlaSet.mistyBogHunters hasKeyword Keyword.Lifelink
            TlaSet.mistyBogHunters hasKeyword Keyword.Menace
        }
    }

    @Test
    fun coldHeartedDeedBothModes() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(TlaSet.coldHeartedDeed, TlaSet.coldHeartedDeed)
        bob.battlefield(bear)
        bob.battlefield(ogre, counters = listOf(p1p1 to 3))
        alice.casts(TlaSet.coldHeartedDeed, modes = listOf(1), target = ogre)
        resolveAll()
        if (pending is Decision.ChooseNumber) { answer(arcanum.engine.decision.Answer.Number(3)); resolveAll() }
        alice.casts(TlaSet.coldHeartedDeed, modes = listOf(0), target = bear)
        resolveAll()
        expect {
            ogre.counters(p1p1) shouldBe 0
            bear.zone shouldBe Zone.Graveyard
        }
    }

    @Test
    fun boarBaboon() = scenario {
        alice.battlefield(TlaSet.boarBaboon, *Array(5) { "Swamp" })
        alice.activates(TlaSet.boarBaboon)
        resolveAll()
        advanceTo(TurnStep.BeginningOfCombat)
        resolveAll()
        expect { TlaSet.boarBaboon hasKeyword Keyword.Menace }
    }

    /** Surveil 1, a token copy, then a sacrifice. */
    @Test
    fun smilingClerkCopiesItself() = scenario {
        alice.battlefield(TlaSet.smilingClerkOneOfMany, trinket, "Swamp")
        alice.library(notes)
        alice.activates(TlaSet.smilingClerkOneOfMany)
        resolveAll()
        choose()
        choose(trinket)
        expect {
            alice.battlefield.names.count { it == "Smiling Clerk, One of Many" } shouldBe 2
            trinket.zone shouldBe Zone.Graveyard
        }
    }

    @Test
    fun saskaBountyHunter() = scenario {
        alice.battlefield(TlaSet.saskaBountyHunter, bear, "Island", "Swamp")
        alice.hand(looter)
        alice.library(notes, notes)
        alice.casts(looter, paying = listOf("Island"))
        resolveAll()
        expect { TlaSet.saskaBountyHunter hasMechanic { it == arcanum.api.Mechanic.CantBeBlocked } }
        alice.activates(TlaSet.saskaBountyHunter, choosing = listOf(bear))
        resolveAll()
        expect { alice.battlefield has "Clue Token" }
    }

    /** It exiles a creature, chooses that card for 1 life and gains its triggered abilities. */
    // CR 613.1f
    @Test
    fun vyrrTakesAMask() = scenario {
        alice.battlefield(*Array(6) { "Swamp" }, "Island")
        alice.hand(TlaSet.vyrrTheMaskTaker, looter)
        alice.library(notes, notes)
        bob.battlefield(TlaSet.loreHunter)
        alice.casts(TlaSet.vyrrTheMaskTaker, paying = List(6) { "Swamp" })
        resolveAll()
        targets(TlaSet.loreHunter)
        expect { TlaSet.loreHunter.zone shouldBe Zone.Exile }
        alice.activates(TlaSet.vyrrTheMaskTaker)
        resolveAll()
        choose(TlaSet.loreHunter)
        expect { alice.life shouldBe 19 }
        // Lore Hunter's second-draw trigger, now Vyrr's (not its vigilance: only activated and triggered abilities)
        alice.casts(looter)
        resolveAll()
        expect {
            TlaSet.vyrrTheMaskTaker.counters(p1p1) shouldBe 1
            TlaSet.vyrrTheMaskTaker lacksKeyword Keyword.Vigilance
        }
    }

    @Test
    fun ilsaScornfulStriker() = scenario {
        alice.battlefield(TlaSet.ilsaScornfulStriker)
        bob.battlefield("Mountain")
        bob.hand(zap)
        turn(active = bob)
        bob.casts(zap, target = "alice")
        resolveAll()
        expect {
            bob.life shouldBe 18
            alice.life shouldBe 18
        }
    }

    @Test
    fun peddlerOfManyMasks() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.graveyard(TlaSet.peddlerOfManyMasks)
        alice.activates(TlaSet.peddlerOfManyMasks)
        resolveAll()
        expect { alice.hand has TlaSet.peddlerOfManyMasks }
    }
}
