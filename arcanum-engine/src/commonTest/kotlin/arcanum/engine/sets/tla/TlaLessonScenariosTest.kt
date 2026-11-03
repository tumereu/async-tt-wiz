package arcanum.engine.sets.tla

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.Subtype
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.tla.TlaExtras.bear
import arcanum.engine.sets.tla.TlaExtras.brute
import arcanum.engine.sets.tla.TlaExtras.flier
import arcanum.engine.sets.tla.TlaExtras.lesson
import arcanum.engine.sets.tla.TlaExtras.notes
import arcanum.engine.sets.tla.TlaExtras.ogre
import arcanum.engine.sets.tla.TlaExtras.p1p1
import arcanum.engine.sets.tla.TlaExtras.trinket
import arcanum.engine.sets.tla.TlaExtras.zap
import kotlin.test.Test

/**
 * Avatar: The Last Airbender chunks 08 to 10 cards in play: the Lesson instants and sorceries, and the cards that count Lessons in the
 * graveyard, search for them or make mana only for them.
 */
class TlaLessonScenariosTest {
    private val shrine = Cards.invent("Quiet Shrine", "{1}{W}", "Legendary Enchantment — Shrine", "")
    private val villager = Cards.invent("Village Ally", "{W}", "Creature — Human Ally", "", "1/1")
    private val searing = Cards.invent("Searing Lesson", "{R}", "Instant — Lesson", "Searing Lesson deals 2 damage to any target.")
    private val hefty = Cards.invent("Hefty Lesson", "{1}{W}", "Sorcery — Lesson", "You gain 2 life.")
    private val threeLessons = arrayOf(lesson, lesson, lesson)

    // ---- chunk 08

    /** Kicked: a basic land and a Shrine to hand, and 2 life. */
    // CR 702.33d
    @Test
    fun wayfarersPilgrimageKicked() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains")
        alice.hand(TlaSet.wayfarersPilgrimage)
        alice.library(notes, "Plains", shrine)
        alice.casts(TlaSet.wayfarersPilgrimage, kicked = 1)
        resolveAll()
        choose("Plains")
        choose(shrine)
        expect {
            alice.hand has shrine
            alice.hand has "Plains"
            alice.life shouldBe 22
        }
    }

    @Test
    fun vitalweavingGivesEveryBasicType() = scenario {
        alice.battlefield("Forest", "Forest", "Plains")
        alice.hand(TlaSet.vitalweaving)
        alice.library(notes)
        alice.casts(TlaSet.vitalweaving, paying = listOf("Forest", "Forest"))
        resolveAll()
        expect {
            "Plains".subtypes.containsAll(listOf(Subtype("Island"), Subtype("Swamp"), Subtype("Mountain"), Subtype("Forest"))) shouldBe true
            alice.hand.size shouldBe 1
        }
    }

    @Test
    fun banishedHeirsExile() = scenario {
        alice.battlefield(*Array(5) { "Plains" })
        alice.hand(TlaSet.banishedHeirsExile)
        bob.battlefield(ogre)
        alice.casts(TlaSet.banishedHeirsExile, target = ogre)
        resolveAll()
        expect {
            ogre.zone shouldBe Zone.Exile
            bob.battlefield has "Clue Token"
        }
    }

    @Test
    fun enterTheHeraldsTrance() = scenario {
        alice.battlefield(bear, "Plains")
        alice.hand(TlaSet.enterTheHeraldsTrance)
        alice.casts(TlaSet.enterTheHeraldsTrance, target = bear)
        resolveAll()
        expect {
            bear.subtypes.contains(Subtype("Avatar")) shouldBe true
            bear hasKeyword Keyword.Flying
            bear hasKeyword Keyword.FirstStrike
            bear hasKeyword Keyword.Lifelink
            bear hasKeyword Keyword.Hexproof
        }
    }

    @Test
    fun nimbleStepsUntapsTwo() = scenario {
        alice.battlefield("Plains", "Plains", "Plains")
        alice.battlefield(bear, ogre, tapped = true)
        alice.hand(TlaSet.nimbleSteps)
        alice.casts(TlaSet.nimbleSteps, targets = listOf(bear, ogre))
        resolveAll()
        expect {
            bear.tapped shouldBe false
            ogre.tapped shouldBe false
            ogre.power shouldBe 5
        }
    }

    /** An Ally also flies; another creature only gets +2/+2. */
    @Test
    fun hupHup() = scenario {
        alice.battlefield(villager, bear, "Plains", "Plains")
        alice.hand(TlaSet.hupHup, TlaSet.hupHup)
        alice.casts(TlaSet.hupHup, target = villager)
        resolveAll()
        alice.casts(TlaSet.hupHup, target = bear)
        resolveAll()
        expect {
            villager.power shouldBe 3
            villager hasKeyword Keyword.Flying
            bear.power shouldBe 4
            bear lacksKeyword Keyword.Flying
        }
    }

    /** With three Lessons in the graveyard, all three cards go to hand. */
    @Test
    fun gatherInsightWithThreeLessons() = scenario {
        alice.battlefield("Island", "Island")
        alice.graveyard(*threeLessons)
        alice.hand(TlaSet.gatherInsight)
        alice.library(notes, bear, ogre, brute)
        alice.casts(TlaSet.gatherInsight)
        resolveAll()
        choose(bear)
        expect { alice.hand.size shouldBe 3 }
    }

    @Test
    fun gatherInsightTakesOne() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand(TlaSet.gatherInsight)
        alice.library(notes, bear, ogre, brute)
        alice.casts(TlaSet.gatherInsight)
        resolveAll()
        choose(bear)
        choose(notes, ogre)
        expect {
            alice.hand.size shouldBe 1
            alice.hand has bear
        }
    }

    /** Your own permanent bounced: you draw. */
    @Test
    fun returningBladeBasics() = scenario {
        alice.battlefield(bear, "Island")
        alice.hand(TlaSet.returningBladeBasics)
        alice.library(notes)
        alice.casts(TlaSet.returningBladeBasics, target = bear)
        resolveAll()
        expect {
            alice.hand has bear
            alice.hand has notes
        }
    }

    /** The opponent can't pay {2}: their spell is countered. */
    // CR 118.12
    @Test
    fun thatllDouseYa() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand(TlaSet.thatllDouseYa)
        bob.battlefield("Mountain")
        bob.hand(zap)
        turn(active = bob)
        bob.casts(zap, target = "alice")
        bob.passes()
        alice.casts(TlaSet.thatllDouseYa, target = zap)
        resolveAll()
        if (pending is Decision.May) yes(false)
        expect {
            alice.life shouldBe 20
            bob.graveyard has zap
        }
    }

    /** The owner puts it second from the top; you get a Clue. */
    @Test
    fun vanishedHours() = scenario {
        alice.battlefield(*Array(5) { "Island" })
        alice.hand(TlaSet.vanishedHours)
        bob.battlefield(ogre)
        bob.library(notes, notes)
        alice.casts(TlaSet.vanishedHours, target = ogre)
        resolveAll()
        if (pending is Decision.ChooseOption) { answer(Answer.Option((pending as Decision.ChooseOption).options.first())); resolveAll() }
        expect {
            bob.library has ogre
            alice.battlefield has "Clue Token"
        }
    }

    @Test
    fun squidStance() = scenario {
        alice.battlefield("Island")
        alice.battlefield(bear, tapped = true)
        alice.hand(TlaSet.squidStance)
        alice.casts(TlaSet.squidStance, target = bear)
        resolveAll()
        expect {
            bear.tapped shouldBe false
            bear.power shouldBe 3
            bear hasKeyword Keyword.Hexproof
        }
    }

    /** Counter, draw, mill three, untap a land. */
    @Test
    fun wisecrackersVerse() = scenario {
        alice.battlefield(*Array(5) { "Island" })
        alice.hand(TlaSet.wisecrackersVerse)
        alice.library(notes, notes, notes, notes)
        bob.battlefield("Mountain")
        bob.hand(zap)
        turn(active = bob)
        bob.casts(zap, target = "alice")
        bob.passes()
        alice.casts(TlaSet.wisecrackersVerse, targets = listOf(zap, "Island"))
        resolveAll()
        expect {
            alice.life shouldBe 20
            alice.hand.size shouldBe 1
            alice.graveyard.size shouldBe 4
            card("Island", 0).tapped shouldBe false
        }
    }

    @Test
    fun princessAlwaysLiesBothModes() = scenario {
        alice.battlefield(bear, "Swamp", "Swamp")
        alice.hand(TlaSet.thePrincessAlwaysLies)
        bob.battlefield(flier)
        alice.casts(TlaSet.thePrincessAlwaysLies, modes = listOf(0, 1), targets = listOf(flier, bear))
        resolveAll()
        expect {
            flier.zone shouldBe Zone.Graveyard
            bear.counters(p1p1) shouldBe 1
        }
    }

    @Test
    fun tyrantsCruelty() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.hand(TlaSet.tyrantsCruelty)
        bob.hand(notes, zap, ogre)
        alice.casts(TlaSet.tyrantsCruelty, target = "bob")
        resolveAll()
        choose(notes, zap)
        expect {
            bob.life shouldBe 18
            bob.hand.size shouldBe 1
        }
    }

    // ---- chunk 09

    /** 2 plus two Lessons: 4 damage, and the creature dying is exiled. */
    // CR 614.1a
    @Test
    fun detonationTechnique() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.graveyard(lesson, lesson)
        alice.hand(TlaSet.detonationTechnique)
        bob.battlefield(brute)
        alice.casts(TlaSet.detonationTechnique, target = brute)
        resolveAll()
        expect { brute.zone shouldBe Zone.Exile }
    }

    @Test
    fun flamecallingLessonKicked() = scenario {
        alice.battlefield(*Array(5) { "Mountain" })
        alice.hand(TlaSet.flamecallingLesson)
        bob.battlefield(brute)
        alice.casts(TlaSet.flamecallingLesson, target = brute, kicked = 1)
        resolveAll()
        expect { brute.zone shouldBe Zone.Graveyard }
    }

    @Test
    fun howToSparkAnUprising() = scenario {
        alice.battlefield(bear, ogre, "Mountain", "Mountain", "Mountain")
        alice.hand(TlaSet.howToSparkAnUprising)
        alice.casts(TlaSet.howToSparkAnUprising, targets = listOf(bear, "alice"))
        resolveAll()
        expect {
            bear hasKeyword Keyword.Menace
            bear.power shouldBe 4
            ogre.power shouldBe 5
        }
    }

    @Test
    fun oldGeneralsDemonstrationSweeps() = scenario {
        alice.battlefield(villager, "Mountain", "Mountain")
        alice.hand(TlaSet.theOldGeneralsDemonstration)
        bob.battlefield(flier, bear)
        alice.casts(TlaSet.theOldGeneralsDemonstration, modes = listOf(0))
        resolveAll()
        expect {
            flier.zone shouldBe Zone.Graveyard
            villager.zone shouldBe Zone.Battlefield
        }
    }

    /** The opponent's land destroyed; they fetch a basic land tapped; you draw. */
    @Test
    fun costOfLiberty() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.hand(TlaSet.costOfLiberty)
        alice.library(notes)
        bob.battlefield(TlaSet.emberLegionPalace)
        bob.library("Forest")
        alice.casts(TlaSet.costOfLiberty, target = TlaSet.emberLegionPalace)
        resolveAll()
        if (pending is Decision.May) yes()
        choose("Forest")
        expect {
            bob.graveyard has TlaSet.emberLegionPalace
            card("Forest", 0).tapped shouldBe true
            alice.hand has notes
        }
    }

    /** Paying {2} as its additional cost, the opponent's burn spell is pointed at their own face. */
    // CR 115.7
    @Test
    fun rerouteThunderbolt() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.hand(TlaSet.rerouteThunderbolt)
        bob.battlefield("Mountain")
        bob.hand(zap)
        turn(active = bob)
        bob.casts(zap, target = "alice")
        bob.passes()
        alice.casts(TlaSet.rerouteThunderbolt, target = zap, option = "Pay {2}")
        resolveAll()
        targets("bob")
        expect {
            alice.life shouldBe 20
            bob.life shouldBe 18
        }
    }

    /** Lands exiled until a nonland card; its mana value is below the Mountain count, so it's cast free. */
    @Test
    fun equinoxRevelationsCastsFree() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.hand(TlaSet.equinoxRevelations)
        alice.library("Forest", zap, notes)
        alice.casts(TlaSet.equinoxRevelations)
        resolveAll()
        yes()
        targets("bob")
        expect {
            bob.life shouldBe 18
            alice.exile has "Forest"
        }
    }

    @Test
    fun roundOfRenewal() = scenario {
        alice.battlefield("Forest", "Forest", "Forest")
        alice.hand(TlaSet.roundOfRenewal)
        alice.library("Island", "Plains")
        alice.casts(TlaSet.roundOfRenewal)
        resolveAll()
        choose("Forest")
        choose("Island", "Plains")
        expect {
            "Island".tapped shouldBe true
            "Plains".tapped shouldBe true
            alice.graveyard has "Forest"
        }
    }

    /** Four lands found; the opponent sends two to the graveyard; the other two enter tapped. */
    @Test
    fun primalTeachings() = scenario {
        alice.battlefield(*Array(5) { "Forest" })
        alice.hand(TlaSet.primalTeachings)
        alice.library("Island", "Plains", "Swamp", "Mountain")
        alice.casts(TlaSet.primalTeachings)
        resolveAll()
        choose("Island", "Plains", "Swamp", "Mountain")
        choose("Island", "Plains")
        expect {
            alice.graveyard has "Island"
            alice.battlefield has "Swamp"
            "Mountain".tapped shouldBe true
        }
    }

    @Test
    fun dawnOfIronshapingBothWays() = scenario {
        alice.battlefield(bear, "Forest", "Forest", "Forest", "Forest")
        alice.hand(TlaSet.dawnOfIronshaping, TlaSet.dawnOfIronshaping)
        bob.battlefield(trinket)
        alice.casts(TlaSet.dawnOfIronshaping, modes = listOf(0), target = trinket)
        resolveAll()
        alice.casts(TlaSet.dawnOfIronshaping, modes = listOf(1), target = bear)
        resolveAll()
        expect {
            trinket.zone shouldBe Zone.Graveyard
            bear.counters(p1p1) shouldBe 1
            bear hasKeyword Keyword.Indestructible
        }
    }

    @Test
    fun tremorSight() = scenario {
        alice.battlefield("Forest", "Forest")
        alice.hand(TlaSet.tremorSight)
        alice.library(notes, bear, notes)
        alice.casts(TlaSet.tremorSight)
        resolveAll()
        choose(bear)
        expect { alice.hand has bear }
    }

    @Test
    fun commonRoots() = scenario {
        alice.battlefield("Forest", "Forest")
        alice.hand(TlaSet.commonRoots)
        alice.library("Island")
        alice.casts(TlaSet.commonRoots)
        resolveAll()
        choose("Island")
        expect { "Island".tapped shouldBe true }
    }

    @Test
    fun genuineLineage() = scenario {
        alice.battlefield("Forest", "Forest")
        alice.hand(TlaSet.genuineLineage)
        alice.graveyard(bear)
        alice.casts(TlaSet.genuineLineage, target = bear)
        resolveAll()
        expect {
            alice.hand has bear
            alice.battlefield has "Clue Token"
        }
    }

    @Test
    fun releaseAttachments() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand(TlaSet.releaseAttachments, zap)
        alice.library(notes, notes)
        alice.casts(TlaSet.releaseAttachments)
        resolveAll()
        yes()
        choose(zap)
        expect {
            alice.graveyard has zap
            alice.hand.size shouldBe 2
        }
    }

    // ---- chunk 10

    @Test
    fun fledglingGliderWithALesson() = scenario {
        alice.battlefield(TlaSet.fledglingGlider)
        alice.graveyard(lesson)
        expect { TlaSet.fledglingGlider.power shouldBe 2 }
    }

    /** Tapped by attacking, it loots; with three Lessons noncreature spells cost {1} less. */
    @Test
    fun nanaUlla() = scenario {
        alice.battlefield(TlaSet.nanaUlla, "Plains")
        alice.graveyard(*threeLessons)
        alice.hand(hefty, zap)
        alice.library(notes)
        alice.casts(hefty)
        resolveAll()
        expect { alice.life shouldBe 22 }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(TlaSet.nanaUlla)
        resolveAll()
        choose(zap)
        expect {
            alice.hand has notes
            alice.graveyard has zap
        }
    }

    @Test
    fun tidemasterOskarMills() = scenario {
        alice.battlefield(TlaSet.tidemasterOskar)
        alice.graveyard(lesson, lesson)
        bob.library(notes, notes, notes)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(TlaSet.tidemasterOskar)
        resolveAll()
        targets("bob")
        expect { bob.graveyard.size shouldBe 2 }
    }

    /** Three Lessons: flash, and {3} less: cast on the opponent's turn for {2}{U}{U}. */
    // CR 601.2f
    @Test
    fun wyrmOfTheStrait() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island")
        alice.graveyard(*threeLessons)
        alice.hand(TlaSet.wyrmOfTheStrait)
        turn(active = bob)
        bob.passes()
        alice.casts(TlaSet.wyrmOfTheStrait)
        resolveAll()
        expect { TlaSet.wyrmOfTheStrait.zone shouldBe Zone.Battlefield }
    }

    /** It tutors a Lesson; Lesson spells you control have lifelink. */
    // CR 702.15b
    @Test
    fun renAndRei() = scenario {
        alice.battlefield(*Array(6) { "Swamp" }, "Mountain")
        alice.hand(TlaSet.renAndReiTwinMentors)
        alice.library(notes, searing)
        alice.casts(TlaSet.renAndReiTwinMentors, paying = List(5) { "Swamp" })
        resolveAll()
        choose(searing)
        alice.casts(searing, target = "bob")
        resolveAll()
        expect {
            bob.life shouldBe 18
            alice.life shouldBe 22
        }
    }

    @Test
    fun petalsFromTheBough() = scenario {
        alice.battlefield("Forest", "Forest")
        alice.hand(TlaSet.petalsFromTheBough)
        alice.library(notes, notes, notes)
        alice.casts(TlaSet.petalsFromTheBough)
        resolveAll()
        expect {
            alice.graveyard.size shouldBe 3
            alice.battlefield has "Food Token"
        }
    }

    @Test
    fun petalsChapterTwoAndThree() = scenario {
        turn(step = TurnStep.Upkeep)
        alice.battlefield(TlaSet.petalsFromTheBough, counters = listOf(CounterKind.Lore to 1))
        alice.battlefield(bear, ogre)
        alice.graveyard(lesson)
        alice.library(notes, notes)
        bob.library(notes, notes)
        advanceTo(TurnStep.PrecombatMain)
        resolveAll()
        targets(bear, ogre)
        expect {
            bear.counters(p1p1) shouldBe 1
            ogre.counters(p1p1) shouldBe 1
        }
        advanceTo(TurnStep.PrecombatMain, turn = 3)
        resolveAll()
        expect { alice.hand.size shouldBe 2 }
    }

    /** A milled land may go to hand; a milled Lesson gains 2 life. */
    @Test
    fun trainingScarecrow() = scenario {
        alice.battlefield(TlaSet.trainingScarecrow)
        alice.library(lesson)
        alice.activates(TlaSet.trainingScarecrow)
        resolveAll()
        expect { alice.life shouldBe 22 }
    }

    @Test
    fun trainingScarecrowLand() = scenario {
        alice.battlefield(TlaSet.trainingScarecrow)
        alice.library("Forest")
        alice.activates(TlaSet.trainingScarecrow)
        resolveAll()
        if (pending is Decision.May) yes()
        choose("Forest")
        expect { alice.hand has "Forest" }
    }

    @Test
    fun rampartSentriesDie() = scenario {
        alice.battlefield(TlaSet.rampartSentries, "Mountain")
        alice.graveyard(lesson)
        alice.hand(zap, zap)
        alice.battlefield("Mountain")
        alice.casts(zap, target = TlaSet.rampartSentries)
        resolveAll()
        alice.casts(zap, target = TlaSet.rampartSentries)
        resolveAll()
        expect { alice.life shouldBe 22 }
    }

    /** Power counts noncreature, nonland cards in the graveyard; dying with a Lesson there draws. */
    @Test
    fun damselflyCloud() = scenario {
        alice.battlefield(TlaSet.damselflyCloud, "Mountain", "Mountain")
        alice.graveyard(lesson, notes)
        alice.hand(TlaExtras.zap, TlaExtras.zap)
        alice.library(notes)
        expect { TlaSet.damselflyCloud.power shouldBe 2 }
        alice.casts(zap, target = TlaSet.damselflyCloud)
        resolveAll()
        alice.casts(zap, target = TlaSet.damselflyCloud)
        resolveAll()
        expect {
            TlaSet.damselflyCloud.zone shouldBe Zone.Graveyard
            alice.hand has notes
        }
    }

    /** It finds a Lesson; casting it puts a counter on another creature. */
    @Test
    fun sageOmmar() = scenario {
        alice.battlefield(*Array(4) { "Forest" }, "Plains", bear)
        alice.hand(TlaSet.sageOmmar)
        alice.library(notes, lesson, notes, notes, notes)
        alice.casts(TlaSet.sageOmmar, paying = List(4) { "Forest" })
        resolveAll()
        choose(lesson)
        alice.casts(lesson)
        resolveAll()
        targets(bear)
        expect { bear.counters(p1p1) shouldBe 1 }
    }

    /** Its two-mana ability pays only for a Lesson. */
    // CR 106.6
    @Test
    fun reclusiveHerbwifePaysForALesson() = scenario {
        alice.battlefield(TlaSet.reclusiveHerbwife)
        alice.hand(hefty)
        alice.activates(TlaSet.reclusiveHerbwife, ability = 1, color = arcanum.api.Color.White)
        alice.casts(hefty)
        resolveAll()
        expect { alice.life shouldBe 22 }
    }

    /** 3 life; it can attack only with three Lessons in the graveyard. */
    @Test
    fun elderShellbeast() = scenario {
        alice.battlefield("Forest", "Forest", "Island")
        alice.hand(TlaSet.theElderShellbeast)
        alice.casts(TlaSet.theElderShellbeast)
        resolveAll()
        expect { alice.life shouldBe 23 }
    }

    @Test
    fun elderShellbeastAttacksWithLessons() = scenario {
        alice.battlefield(TlaSet.theElderShellbeast)
        alice.graveyard(*threeLessons)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(TlaSet.theElderShellbeast)
        advanceTo(TurnStep.CombatDamage)
        expect { bob.life shouldBe 17 }
    }

    /** It mills two; with a Lesson milled it attacks despite defender. */
    // CR 702.3b
    @Test
    fun beaverBear() = scenario {
        alice.battlefield("Forest", "Forest")
        alice.hand(TlaSet.beaverBear)
        alice.library(lesson, notes, notes, notes)
        bob.library(notes, notes)
        alice.casts(TlaSet.beaverBear)
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers, turn = 3)
        alice.attacks(TlaSet.beaverBear)
        advanceTo(TurnStep.CombatDamage)
        expect { bob.life shouldBe 18 }
    }

    @Test
    fun paleBlossomRefugeForALesson() = scenario {
        alice.battlefield(TlaSet.paleBlossomRefuge)
        alice.hand(lesson)
        alice.activates(TlaSet.paleBlossomRefuge, ability = 1, color = arcanum.api.Color.White)
        alice.casts(lesson)
        resolveAll()
        expect { alice.life shouldBe 21 }
    }

    @Test
    fun swordwrightHalloran() = scenario {
        alice.battlefield(TlaSet.swordwrightHalloran)
        alice.library(notes, villager, notes, notes)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(TlaSet.swordwrightHalloran)
        resolveAll()
        choose(villager)
        expect { alice.hand has villager }
    }
}
