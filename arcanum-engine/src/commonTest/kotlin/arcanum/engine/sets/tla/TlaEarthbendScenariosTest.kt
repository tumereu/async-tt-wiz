package arcanum.engine.sets.tla

import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.Subtype
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.tla.TlaExtras.bear
import arcanum.engine.sets.tla.TlaExtras.brute
import arcanum.engine.sets.tla.TlaExtras.lesson
import arcanum.engine.sets.tla.TlaExtras.notes
import arcanum.engine.sets.tla.TlaExtras.ogre
import arcanum.engine.sets.tla.TlaExtras.p1p1
import arcanum.engine.sets.tla.TlaExtras.quest
import arcanum.engine.sets.tla.TlaExtras.zap
import kotlin.test.Test

/**
 * Avatar: The Last Airbender chunks 02 and 03 cards in play: earthbend (CR 701.66) from enters, attack, Ally, Landfall, dies, cast and
 * end step triggers, activated and exhaust abilities, spells, modes, Sagas and a delayed trigger; and the cards built around it.
 * Stoneshaping Lesson and Stoneholm's earthbend are played in [TlaMechanicsScenariosTest].
 */
class TlaEarthbendScenariosTest {
    private val elf = Cards.invent("Grove Tender", "{G}", "Creature — Elf Druid", "{T}: Add {G}.", "1/1")
    private val forest = "Forest"
    private fun land(i: Int = 0) = "Forest#$i"

    @Test
    fun burrowmoleGivesTrample() = scenario {
        alice.battlefield(forest, forest, forest, forest, forest)
        alice.hand(TlaSet.burrowmole)
        alice.casts(TlaSet.burrowmole)
        resolveAll()
        targets(forest)
        expect {
            card(forest, 0).power shouldBe 2
            card(forest, 0) hasKeyword Keyword.Trample
            TlaSet.burrowmole lacksKeyword Keyword.Trample
        }
    }

    /** Tapping a creature for mana adds an additional {G}. */
    // CR 605.1b
    @Test
    fun burrowmoleCubAddsMana() = scenario {
        alice.battlefield(TlaSet.burrowmoleCub, elf)
        alice.activates(elf)
        expect { alice.pool.count { it.color == Color.Green } shouldBe 2 }
    }

    /** X counts creatures with power 4 or greater as it resolves. */
    // CR 107.3c
    @Test
    fun theCragEarthbendsX() = scenario {
        alice.battlefield(TlaSet.theCragReadyToBrawl, brute, forest)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(TlaSet.theCragReadyToBrawl)
        resolveAll()
        targets(forest)
        expect { card(forest, 0).counters(p1p1) shouldBe 2 }
    }

    /** With two Lessons in the graveyard, two modes: counters on Odric and an earthbend. */
    // CR 700.2
    @Test
    fun odricChoosesUpToX() = scenario {
        alice.battlefield(forest, forest, forest, forest, forest, forest, "Island")
        alice.graveyard(lesson, lesson)
        alice.hand(TlaSet.odricKingOfThreeTrials)
        alice.casts(TlaSet.odricKingOfThreeTrials, paying = listOf(land(0), land(1), land(2), land(3), land(4), land(5)))
        resolveAll()
        modes(0, 2)
        targets("Island")
        expect {
            TlaSet.odricKingOfThreeTrials.counters(p1p1) shouldBe 3
            "Island".counters(p1p1) shouldBe 3
        }
    }

    /** Counters put by you: you may gain that much life, once each turn. */
    // CR 603.2h
    @Test
    fun stoneRealmGeneralGainsLife() = scenario {
        alice.battlefield(forest, forest, forest, forest, "Island")
        alice.hand(TlaSet.stoneRealmGeneral)
        alice.casts(TlaSet.stoneRealmGeneral)
        resolveAll()
        targets("Island")
        yes()
        expect { alice.life shouldBe 22 }
    }

    /** Earthbend, then the reflexive fight. */
    // CR 603.12, 701.14
    @Test
    fun stoneBrawlFights() = scenario {
        alice.battlefield(forest, forest, forest, forest, brute)
        alice.hand(TlaSet.stoneBrawl)
        bob.battlefield(ogre)
        alice.casts(TlaSet.stoneBrawl, target = forest)
        resolveAll()
        targets(brute, ogre)
        expect {
            ogre.zone shouldBe Zone.Graveyard
            card(forest, 0).counters(p1p1) shouldBe 2
        }
    }

    /** Earthbend and a basic land as it enters; the fourth quest counter's reflexive trigger pumps a creature. */
    // CR 603.12, 603.4
    @Test
    fun stoneshaperAscension() = scenario {
        alice.battlefield(forest, forest, forest, bear)
        alice.hand(TlaSet.stoneshaperAscension, forest)
        alice.library(forest)
        alice.casts(TlaSet.stoneshaperAscension)
        resolveAll()
        targets(forest)
        if (pending != null) choose(forest)
        resolveAll()
        expect { TlaSet.stoneshaperAscension.counters(quest) shouldBe 1 }
    }

    /** One Ally of each of two colours: +2/+0. */
    // CR 105.2
    @Test
    fun earthboundComradeCountsColors() = scenario {
        alice.battlefield(TlaSet.earthboundComrade, TlaSet.kiroTheLastWindwalker)
        expect { TlaSet.earthboundComrade.power shouldBe 2 }
    }

    @Test
    fun tobinEarthbendsForAllies() = scenario {
        alice.battlefield(TlaSet.tobinHiddenTalent, forest, "Plains", "Plains")
        alice.hand(TlaSet.siegeReinforcements)
        alice.casts(TlaSet.siegeReinforcements, paying = listOf("Plains", "Plains"))
        resolveAll()
        // the Reinforcements and its Ally token: two earthbends
        targets(forest)
        targets(forest)
        expect { card(forest, 0).counters(p1p1) shouldBe 2 }
    }

    /** Exhaust: counters on itself, then earthbend 2; only once. */
    // CR 702.177a
    @Test
    fun mutinousPrisonersExhaust() = scenario {
        alice.battlefield(TlaSet.mutinousPrisoners, forest, forest, forest, forest, forest, forest, "Island")
        alice.activates(TlaSet.mutinousPrisoners, target = "Island")
        resolveAll()
        expect {
            TlaSet.mutinousPrisoners.counters(p1p1) shouldBe 2
            "Island".counters(p1p1) shouldBe 2
        }
    }

    @Test
    fun stoneslideCountsForests() = scenario {
        alice.battlefield(forest, forest, forest)
        alice.hand(TlaSet.stoneslide)
        alice.casts(TlaSet.stoneslide, target = forest)
        resolveAll()
        expect { card(forest, 0).counters(p1p1) shouldBe 3 }
    }

    /** Power is the number of +1/+1 counters on lands you control. */
    // CR 604.3
    @Test
    fun miraCountsLandCounters() = scenario {
        alice.battlefield(forest, forest, forest)
        alice.hand(TlaSet.miraTheSightlessBrawler)
        alice.casts(TlaSet.miraTheSightlessBrawler)
        resolveAll()
        targets(forest)
        expect { TlaSet.miraTheSightlessBrawler.power shouldBe 2 }
    }

    @Test
    fun dunecallersStormEarthbends() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", forest)
        alice.hand(TlaSet.dunecallersStorm)
        alice.casts(TlaSet.dunecallersStorm, modes = listOf(1), target = forest)
        resolveAll()
        expect { card(forest, 0).counters(p1p1) shouldBe 3 }
    }

    /** The opponent reveals; you choose a nonland permanent card; they discard it. */
    // CR 701.20a
    @Test
    fun stoneGuardConditioningDiscards() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.hand(TlaSet.stoneGuardConditioning)
        bob.hand(ogre, notes)
        alice.casts(TlaSet.stoneGuardConditioning, modes = listOf(0), target = "bob")
        resolveAll()
        choose(ogre)
        expect { bob.graveyard has ogre }
    }

    /** When the chosen creature dies this turn, earthbend 4. */
    // CR 603.7
    @Test
    fun deadlyCrevasse() = scenario {
        alice.battlefield("Swamp", "Swamp", "Mountain", forest)
        alice.hand(TlaSet.deadlyCrevasse, zap)
        bob.battlefield(bear)
        alice.casts(TlaSet.deadlyCrevasse, target = bear)
        resolveAll()
        alice.casts(zap, target = bear)
        resolveAll()
        targets(forest)
        expect { card(forest, 0).counters(p1p1) shouldBe 4 }
    }

    /** Chapter III earthbends 3. */
    // CR 714.2b
    @Test
    fun grottoChapterThree() = scenario {
        turn(step = TurnStep.Upkeep)
        alice.battlefield(TlaSet.theGrottoOfTwoSweethearts, counters = listOf(CounterKind.Lore to 2))
        alice.battlefield(forest)
        alice.library(notes, notes)
        advanceTo(TurnStep.PrecombatMain)
        resolveAll()
        targets(forest)
        expect { card(forest, 0).counters(p1p1) shouldBe 3 }
    }

    /** Chapter I makes two Allies. */
    @Test
    fun grottoChapterOne() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(TlaSet.theGrottoOfTwoSweethearts)
        alice.casts(TlaSet.theGrottoOfTwoSweethearts)
        resolveAll()
        expect { alice.battlefield.names.count { it == "Ally Token" } shouldBe 2 }
    }

    /** Chapter II: X is the hand size, and the land becomes an Island too. */
    // CR 701.66a, 305.7
    @Test
    fun taleOfMaelisChapterTwo() = scenario {
        turn(step = TurnStep.Upkeep)
        alice.battlefield(TlaSet.theTaleOfMaelis, counters = listOf(CounterKind.Lore to 1))
        alice.battlefield(forest)
        alice.library(notes, notes)
        alice.hand(notes, notes)
        advanceTo(TurnStep.PrecombatMain)
        resolveAll()
        targets(forest)
        expect {
            // two cards in hand: the first turn's draw is skipped (CR 103.8a)
            card(forest, 0).counters(p1p1) shouldBe 2
            card(forest, 0).subtypes.contains(Subtype("Island")) shouldBe true
        }
    }

    /** The Avatar: lands have trample and hexproof; it taps for mana equal to the greatest power. */
    @Test
    fun heraldMaelisMana() = scenario {
        turn(step = TurnStep.Upkeep)
        alice.battlefield(TlaSet.theTaleOfMaelis, counters = listOf(CounterKind.Lore to 2))
        alice.battlefield(forest, brute)
        alice.library(notes, notes)
        bob.library(notes, notes)
        advanceTo(TurnStep.PrecombatMain)
        resolveAll()
        expect { card(forest, 0) hasKeyword Keyword.Hexproof }
        // a new object since it returned: it can tap on Alice's next turn
        advanceTo(TurnStep.PrecombatMain, turn = 3)
        resolveAll()
        alice.activates("Herald Maelis", color = Color.Green)
        expect { alice.pool.size shouldBe 5 }
    }

    /** It puts a creature from the top five onto the battlefield; another leaving transforms it next upkeep. */
    // CR 603.7, 701.27
    @Test
    fun kiroAtTheFork() = scenario {
        alice.battlefield(forest, forest, "Plains", "Island", "Island")
        alice.hand(TlaSet.kiroAtTheFork, zap)
        alice.battlefield("Mountain")
        alice.library(bear, notes, notes, notes, notes, notes, notes)
        alice.casts(TlaSet.kiroAtTheFork, paying = listOf(land(0), land(1), "Plains", "Island", "Island"))
        resolveAll()
        choose(bear)
        expect { bear.zone shouldBe Zone.Battlefield }
        alice.casts(zap, target = bear)
        resolveAll()
        advanceTo(TurnStep.Upkeep, turn = 2)
        resolveAll()
        expect { "Kiro, Fated Savior".power shouldBe 4 }
    }

    /** Fated Savior: earthbend 2 at the beginning of combat; land creatures have vigilance. */
    @Test
    fun kiroFatedSavior() = scenario {
        alice.battlefield(TlaSet.kiroAtTheFork, forest, bear, "Mountain")
        alice.hand(zap)
        alice.library(notes, notes)
        bob.library(notes, notes)
        alice.casts(zap, target = bear)
        resolveAll()
        advanceTo(TurnStep.Upkeep, turn = 2)
        resolveAll()
        advanceTo(TurnStep.BeginningOfCombat, turn = 3)
        resolveAll()
        targets(forest)
        expect {
            "Kiro, Fated Savior".power shouldBe 4
            card(forest, 0).counters(p1p1) shouldBe 2
            card(forest, 0) hasKeyword Keyword.Vigilance
        }
    }

    /** X is the power of the creature that died. */
    // CR 603.10a
    @Test
    fun ironclanBountyHunters() = scenario {
        alice.battlefield(TlaSet.ironclanBountyHunters, bear, forest, "Mountain")
        alice.hand(zap)
        alice.casts(zap, target = bear)
        resolveAll()
        targets(forest)
        expect { card(forest, 0).counters(p1p1) shouldBe 2 }
    }

    /** Attacking with a 4-power creature draws; exhaust earthbends 4. */
    @Test
    fun gruelingToil() = scenario {
        alice.battlefield(TlaSet.gruelingToil, brute, forest, forest, forest, forest, "Island")
        alice.library(notes, notes)
        alice.activates(TlaSet.gruelingToil, target = "Island")
        resolveAll()
        expect { "Island".counters(p1p1) shouldBe 4 }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(brute)
        resolveAll()
        expect { alice.hand.size shouldBe 1 }
    }

    /** Combat damage: lands untap and an additional combat phase follows, in which only land creatures attack. */
    // CR 500.8
    @Test
    fun odricUnleashedExtraCombat() = scenario {
        alice.battlefield(TlaSet.odricUnleashed, bear, forest)
        alice.battlefield(forest, tapped = true)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(TlaSet.odricUnleashed)
        advanceTo(TurnStep.CombatDamage)
        resolveAll()
        expect {
            card(forest, 1).tapped shouldBe false
            state.turn.extraPhases.contains(TurnStep.BeginningOfCombat) shouldBe true
        }
    }

    /** Two earthbends, two targets; the attack drains for each creature with counters. */
    @Test
    fun stoneGuardAgents() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", forest, forest, "Island", "Plains")
        alice.hand(TlaSet.stoneGuardAgents)
        alice.casts(TlaSet.stoneGuardAgents, paying = listOf("Swamp", "Swamp", "Swamp", land(0), land(1)))
        resolveAll()
        targets("Island", "Plains")
        expect {
            "Island".counters(p1p1) shouldBe 1
            "Plains".counters(p1p1) shouldBe 1
        }
    }

    @Test
    fun stoneVillageRuffiansDie() = scenario {
        alice.battlefield(TlaSet.stoneVillageRuffians, forest, "Mountain")
        alice.hand(zap)
        alice.casts(zap, target = TlaSet.stoneVillageRuffians)
        resolveAll()
        targets(forest)
        expect { card(forest, 0).counters(p1p1) shouldBe 2 }
    }

    /** A Lesson cast: earthbend 1 and an additional counter on that land. */
    @Test
    fun miraHardheadedLesson() = scenario {
        alice.battlefield(TlaSet.miraHardheadedTeacher, forest, "Plains")
        alice.hand(lesson)
        alice.casts(lesson)
        resolveAll()
        targets(forest)
        expect { card(forest, 0).counters(p1p1) shouldBe 2 }
    }

    /** Nontoken artifacts are lands, so the end step earthbend can make one a creature. */
    // CR 205.1b
    @Test
    fun miraFirstIronshaper() = scenario {
        alice.battlefield(TlaSet.miraTheFirstIronshaper, TlaExtras.trinket)
        expect { TlaExtras.trinket.types.contains(CardType.Land) shouldBe true }
        advanceTo(TurnStep.End)
        resolveAll()
        targets(TlaExtras.trinket)
        expect { TlaExtras.trinket.power shouldBe 2 }
    }
}
