package arcanum.engine.sets.tla

import arcanum.api.Color
import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.Subtype
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Attack
import arcanum.engine.input.Input
import arcanum.engine.state.TargetRef
import arcanum.engine.scenario.Scenario
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
 * Avatar: The Last Airbender chunks 06 and 07 cards in play: firebending (CR 702.189) on attackers and what its red mana pays for during
 * combat, mana kept or turned red, copies of attack triggers and spells, casting from exile and graveyards. Vexa, On the Hunt's
 * firebending is played in [TlaMechanicsScenariosTest.firebendingManaUntilEndOfCombat]; Flame Elders (firebending and a counter
 * activation) repeats tested templates; Permafrost Tank's crew is a covered keyword.
 */
class TlaFirebendingScenariosTest {
    private val emberAlly = Cards.invent("Ember Scout", "{R}", "Creature — Human Ally", "", "1/1")
    private val heftyLesson = Cards.invent("Hefty Lesson", "{1}{W}", "Sorcery — Lesson", "You gain 2 life.")
    private val drake = Cards.invent("Ember Drake", "{1}{R}", "Creature — Dragon", "Flying", "2/2")
    private val stone = Cards.invent("Offering Stone", "{1}", "Artifact", "{T}, Sacrifice this artifact: You gain 1 life.")
    private val outpost = Cards.invent("Ridge Outpost", null, "Land", "{T}: Add {R}.")

    private fun Scenario.red(): Int = alice.pool.count { it.color == Color.Red }

    private fun Scenario.attackWith(vararg names: String) {
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(*names)
        resolveAll()
    }

    @Test
    fun vexaOnTheHuntMakesAClue() = scenario {
        alice.battlefield(TlaSet.vexaOnTheHunt)
        attackWith(TlaSet.vexaOnTheHunt)
        expect {
            alice.life shouldBe 19
            alice.battlefield has "Clue Token"
            red() shouldBe 2
        }
    }

    /** An Ally taps to exile a card of yours; attacking, the firebending {R} casts that Ally from exile. */
    // CR 702.189a, 601.2
    @Test
    fun scaldingPitRioterRecastsAnAlly() = scenario {
        alice.battlefield(TlaSet.scaldingPitRioter, TlaSet.kiroTheLastWindwalker)
        alice.graveyard(emberAlly)
        alice.activates(TlaSet.scaldingPitRioter, target = emberAlly, choosing = listOf(TlaSet.kiroTheLastWindwalker))
        resolveAll()
        expect { emberAlly.zone shouldBe Zone.Exile }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(TlaSet.scaldingPitRioter)
        // the cast trigger first on the Stack, so the firebending mana is there when it resolves (CR 603.3b)
        val order = decision<Decision.OrderTriggers>()
        answer(Answer.Order(order.triggers.indices.reversed().toList()))
        resolveAll()
        choose(emberAlly)
        // the firebending {R} pays for it
        pay()
        expect { emberAlly.zone shouldBe Zone.Battlefield }
    }

    @Test
    fun permafrostTankGivesIndestructible() = scenario {
        alice.battlefield(bear, "Swamp", "Swamp", "Swamp")
        alice.hand(TlaSet.permafrostTank)
        alice.casts(TlaSet.permafrostTank)
        resolveAll()
        expect { bear hasKeyword Keyword.Indestructible }
    }

    /** Two Soldiers with firebending 1; attacking next turn, they add {R}{R}. */
    @Test
    fun emberLegionStrikesSoldiersFirebend() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(TlaSet.theEmberLegionStrikes)
        alice.library(notes, notes)
        bob.library(notes, notes)
        alice.casts(TlaSet.theEmberLegionStrikes)
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers, turn = 3)
        perform(Input.DeclareAttackers(alice.id, (0..1).map { Attack(card("Soldier Token", it), TargetRef.Player(bob.id)) }))
        resolveAll()
        expect { red() shouldBe 2 }
    }

    /** With a Lesson in the graveyard it firebends 2; the red mana pays its own pump. */
    // CR 702.189a, 106.4
    @Test
    fun emberLegionCadetsPumpWithFirebendingMana() = scenario {
        alice.battlefield(TlaSet.emberLegionCadets)
        alice.graveyard(lesson)
        attackWith(TlaSet.emberLegionCadets)
        expect { red() shouldBe 2 }
        alice.activates(TlaSet.emberLegionCadets)
        resolveAll()
        expect {
            TlaSet.emberLegionCadets.power shouldBe 2
            red() shouldBe 0
        }
    }

    /** Fourth quest counter: the attacker's firebending ability is copied, so {R}{R}. */
    // CR 707.10
    @Test
    fun flamecallerAscensionCopiesTheAttackTrigger() = scenario {
        alice.battlefield(TlaSet.flamecallerAscension, counters = listOf(quest to 3))
        alice.battlefield(TlaSet.flameElders)
        attackWith(TlaSet.flameElders)
        yes()
        expect {
            TlaSet.flamecallerAscension.counters(quest) shouldBe 4
            red() shouldBe 2
        }
    }

    @Test
    fun flamecallerAscensionMakesASoldier() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.hand(TlaSet.flamecallerAscension)
        alice.casts(TlaSet.flamecallerAscension)
        resolveAll()
        expect { "Soldier Token" hasKeyword Keyword.Firebending }
    }

    /** Prowess first, then firebending X counts the pumped power. */
    // CR 702.108a
    @Test
    fun flamecallingStudentFirebendsItsPower() = scenario {
        alice.battlefield(TlaSet.flamecallingStudent, "Island")
        alice.hand(notes)
        alice.library(notes)
        alice.casts(notes)
        resolveAll()
        attackWith(TlaSet.flamecallingStudent)
        expect { red() shouldBe 2 }
    }

    /** Exhaust: a counter, and the next Lesson this turn is copied. */
    // CR 707.10
    @Test
    fun corvaneCopiesTheNextLesson() = scenario {
        alice.battlefield(TlaSet.corvaneTheDeserter, "Mountain", "Mountain", "Mountain", "Plains")
        alice.hand(lesson)
        alice.activates(TlaSet.corvaneTheDeserter)
        resolveAll()
        alice.casts(lesson)
        resolveAll()
        expect {
            TlaSet.corvaneTheDeserter.counters(p1p1) shouldBe 1
            alice.life shouldBe 22
        }
    }

    /** Chapter I: the top three exiled, playable until the end of the next turn. */
    // CR 714.2b
    @Test
    fun taleOfArdanChapterOne() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(TlaSet.theTaleOfArdan)
        alice.library(outpost, notes, notes, notes)
        alice.casts(TlaSet.theTaleOfArdan)
        resolveAll()
        expect { outpost.zone shouldBe Zone.Exile }
        alice.plays(outpost)
        expect { outpost.zone shouldBe Zone.Battlefield }
    }

    /** Herald Ardan: {8} makes a 4/4 flying Dragon with firebending 4. */
    @Test
    fun heraldArdanMakesADragon() = scenario {
        alice.battlefield(TlaSet.theTaleOfArdan, *Array(8) { "Mountain" })
        backFaceUp(TlaSet.theTaleOfArdan)
        alice.activates("Herald Ardan")
        resolveAll()
        expect {
            "Dragon Token".power shouldBe 4
            "Dragon Token" hasKeyword Keyword.Flying
        }
    }

    /** Cast with three Dragon and Lesson cards in the graveyard: a nonlegendary token copy; Dragons get +2/+0. */
    // CR 707.9b
    @Test
    fun velAndSorrCopyThemselves() = scenario {
        alice.battlefield(*Array(9) { "Mountain" })
        alice.graveyard(lesson, lesson, drake)
        alice.hand(TlaSet.velAndSorr)
        alice.casts(TlaSet.velAndSorr, paying = List(5) { "Mountain#$it" })
        resolveAll()
        expect { alice.battlefield.names.count { it == "Vel and Sorr" } shouldBe 2 }
        alice.activates(TlaSet.velAndSorr, paying = List(4) { "Mountain#${it + 5}" })
        resolveAll()
        expect { card(TlaSet.velAndSorr, 1).power shouldBe 6 }
    }

    @Test
    fun ruggedRhinoLancersExhaust() = scenario {
        alice.battlefield(TlaSet.ruggedRhinoLancers, *Array(8) { "Mountain" })
        alice.activates(TlaSet.ruggedRhinoLancers)
        resolveAll()
        expect {
            TlaSet.ruggedRhinoLancers.power shouldBe 7
            TlaSet.ruggedRhinoLancers hasKeyword Keyword.Trample
        }
    }

    @Test
    fun vardansCometGrantsFirebendingFive() = scenario {
        alice.battlefield(bear, *Array(5) { "Mountain" })
        alice.hand(TlaSet.vardansComet)
        alice.casts(TlaSet.vardansComet)
        resolveAll()
        attackWith(bear)
        expect { red() shouldBe 5 }
    }

    /** {3}: the top card exiled and playable this turn. */
    @Test
    fun darrowExiledPrincePlaysFromExile() = scenario {
        alice.battlefield(TlaSet.darrowExiledPrince, "Mountain", "Mountain", "Mountain")
        alice.library(outpost)
        alice.activates(TlaSet.darrowExiledPrince)
        resolveAll()
        alice.plays(outpost)
        expect { outpost.zone shouldBe Zone.Battlefield }
    }

    /** It enters tapped without a basic land; {1}{R},{T} gives a creature firebending 4. */
    // CR 614.1c
    @Test
    fun emberLegionPalace() = scenario {
        alice.hand(TlaSet.emberLegionPalace)
        alice.plays(TlaSet.emberLegionPalace)
        expect { TlaSet.emberLegionPalace.tapped shouldBe true }
    }

    @Test
    fun emberLegionPalaceGrantsFirebending() = scenario {
        alice.battlefield(TlaSet.emberLegionPalace, bear, "Mountain", "Mountain")
        alice.activates(TlaSet.emberLegionPalace, ability = 1, target = bear, paying = listOf("Mountain", "Mountain"))
        resolveAll()
        attackWith(bear)
        expect { red() shouldBe 4 }
    }

    // ---- chunk 07

    /** Chapter I destroys all creatures. */
    @Test
    fun riseOfVardanChapterOne() = scenario {
        alice.battlefield(bear, *Array(6) { "Swamp" })
        alice.hand(TlaSet.theRiseOfVardan)
        bob.battlefield(ogre)
        alice.casts(TlaSet.theRiseOfVardan)
        resolveAll()
        expect {
            bear.zone shouldBe Zone.Graveyard
            ogre.zone shouldBe Zone.Graveyard
        }
    }

    /** Chapter II: a name chosen, up to four cards with it exiled from the opponent's graveyard, hand and library. */
    // CR 701.19
    @Test
    fun riseOfVardanChapterTwo() = scenario {
        turn(step = TurnStep.Upkeep)
        alice.battlefield(TlaSet.theRiseOfVardan, counters = listOf(CounterKind.Lore to 1))
        alice.library(notes, notes)
        bob.hand(zap)
        bob.graveyard(zap)
        bob.library(zap, notes)
        advanceTo(TurnStep.PrecombatMain)
        resolveAll()
        if (pending is arcanum.engine.decision.Decision.Targets) targets("bob")
        answer(arcanum.engine.decision.Answer.CardName(zap))
        resolveAll()
        choose(zap, zap, zap)
        expect {
            bob.hand lacks zap
            bob.graveyard lacks zap
            bob.library lacks zap
        }
    }

    /** Flame Sovereign Vardan's combat damage: pay {X} to return creature cards with total mana value X or less. */
    // CR 603.12
    @Test
    fun flameSovereignVardanReanimates() = scenario {
        alice.battlefield(TlaSet.theRiseOfVardan, "Swamp", "Swamp", "Swamp")
        backFaceUp(TlaSet.theRiseOfVardan)
        bob.graveyard(ogre, brute)
        attackWith("Flame Sovereign Vardan")
        advanceTo(TurnStep.CombatDamage)
        resolveAll()
        answer(Answer.Number(3))
        resolveAll()
        yes()
        pay()
        targets(ogre)
        expect {
            bob.life shouldBe 15
            alice.battlefield has ogre
        }
    }

    /** The fight's excess damage adds that much {R}, and unspent red mana is kept as steps end. */
    // CR 702.19c, 106.4, 500.4
    @Test
    fun finalFlameDuelExcessMana() = scenario {
        alice.battlefield(brute, "Mountain", "Mountain")
        alice.hand(TlaSet.theFinalFlameDuel)
        bob.battlefield(bear)
        alice.casts(TlaSet.theFinalFlameDuel, targets = listOf(brute, bear))
        resolveAll()
        expect {
            bear.zone shouldBe Zone.Graveyard
            red() shouldBe 2
        }
        advanceTo(TurnStep.DeclareAttackers)
        expect { red() shouldBe 2 }
    }

    /** The opponent exiles a creature and a graveyard card; you cast that creature with any mana. */
    // CR 601.2f
    @Test
    fun vexaCunningUsurperCastsTheirCreature() = scenario {
        alice.battlefield("Island", "Island", "Swamp", "Swamp", "Swamp", "Plains", "Plains", "Plains")
        alice.hand(TlaSet.vexaCunningUsurper)
        bob.battlefield(ogre)
        bob.graveyard(zap)
        alice.casts(TlaSet.vexaCunningUsurper, paying = listOf("Island", "Island", "Swamp", "Swamp", "Swamp"))
        resolveAll()
        choose(ogre)
        choose(zap)
        expect {
            ogre.zone shouldBe Zone.Exile
            zap.zone shouldBe Zone.Exile
        }
        alice.casts(ogre, paying = listOf("Plains", "Plains", "Plains"))
        resolveAll()
        expect { alice.battlefield has ogre }
    }

    /** Raid counter, and an attacking Soldier token each attack. */
    // CR 702.189a
    @Test
    fun ruthlessOverseer() = scenario {
        alice.battlefield(TlaSet.ruthlessOverseer)
        attackWith(TlaSet.ruthlessOverseer)
        expect { alice.battlefield has "Soldier Token" }
    }

    /** A spell cast while it attacks is copied: the firebending {R}{R} casts two burns' worth. */
    // CR 707.10
    @Test
    fun flameSovereignVexaCopies() = scenario {
        alice.battlefield(TlaSet.flameSovereignVexa)
        alice.hand(zap)
        attackWith(TlaSet.flameSovereignVexa)
        alice.casts(zap, target = "bob")
        resolveAll()
        if (pending != null) yes(false)
        expect { bob.life shouldBe 16 }
    }

    /** A spell cast from exile puts a counter on each creature you control; firebending X then counts the new power. */
    // CR 701.65a
    @Test
    fun flameSovereignDarrow() = scenario {
        alice.battlefield(TlaSet.flameSovereignDarrow, *Array(5) { "Plains" })
        alice.hand(TlaSet.windweavingLesson)
        alice.library(notes)
        alice.battlefield(bear)
        alice.casts(TlaSet.windweavingLesson, target = bear, paying = listOf("Plains#0", "Plains#1", "Plains#2"))
        resolveAll()
        alice.casts(bear, paying = listOf("Plains#3", "Plains#4"))
        resolveAll()
        expect { TlaSet.flameSovereignDarrow.counters(p1p1) shouldBe 1 }
        attackWith(TlaSet.flameSovereignDarrow)
        expect { red() shouldBe 3 }
    }

    /** During your turn: an instant in the graveyard has flashback for its mana cost; a Lesson for {1}. */
    // CR 702.34a
    @Test
    fun baldricGrantsFlashback() = scenario {
        alice.battlefield(TlaSet.baldricGrandBlossom, "Mountain", "Island")
        alice.graveyard(zap, lesson)
        alice.casts(zap, target = "bob", paying = listOf("Mountain"))
        resolveAll()
        alice.casts(lesson, paying = listOf("Island"))
        resolveAll()
        expect {
            bob.life shouldBe 18
            alice.life shouldBe 21
            alice.exile has zap
            alice.exile has lesson
        }
    }

    /** Mana it would lose turns red; with six or more unspent it flies and is indestructible. */
    // CR 106.4, 500.4
    @Test
    fun malgorKeepsManaRed() = scenario {
        alice.battlefield(TlaSet.malgorTheCinderbirdKing, "Island", "Swamp")
        attackWith(TlaSet.malgorTheCinderbirdKing)
        alice.activates("Island")
        alice.activates("Swamp")
        expect { TlaSet.malgorTheCinderbirdKing hasKeyword Keyword.Flying }
        advanceTo(TurnStep.PostcombatMain)
        expect {
            red() shouldBe 6
            TlaSet.malgorTheCinderbirdKing hasKeyword Keyword.Indestructible
        }
    }

    @Test
    fun solarGuardiansCountCreatures() = scenario {
        alice.battlefield(TlaSet.solarGuardians, bear, *Array(5) { "Plains" })
        attackWith(TlaSet.solarGuardians)
        expect { red() shouldBe 2 }
        alice.activates(TlaSet.solarGuardians, paying = List(5) { "Plains#$it" })
        resolveAll()
        expect { alice.battlefield has "Ally Token" }
    }

    /** Lesson spells cost {1} less. */
    // CR 601.2f
    @Test
    fun elderBaldricReducesLessons() = scenario {
        alice.battlefield(TlaSet.elderBaldric, "Plains")
        alice.hand(heftyLesson)
        alice.casts(heftyLesson)
        resolveAll()
        expect { alice.life shouldBe 22 }
    }

    @Test
    fun spitefulWardenPings() = scenario {
        alice.battlefield(TlaSet.spitefulWarden, "Mountain", "Mountain", "Mountain")
        alice.activates(TlaSet.spitefulWarden)
        resolveAll()
        expect { bob.life shouldBe 19 }
    }

    /** Sacrificing another permanent: creatures +1/+0. */
    @Test
    fun krellRuthlessAdmiral() = scenario {
        alice.battlefield(TlaSet.krellRuthlessAdmiral, bear, stone)
        alice.activates(stone)
        resolveAll()
        expect {
            bear.power shouldBe 3
            TlaSet.krellRuthlessAdmiral.power shouldBe 4
        }
    }

    /** Nonbasic lands enter tapped; with a conqueror counter they are Mountains. */
    // CR 305.7
    @Test
    fun krellTheMoonSlayer() = scenario {
        alice.battlefield(TlaSet.krellTheMoonSlayer, *Array(7) { "Mountain" })
        alice.hand(outpost)
        alice.plays(outpost)
        expect { outpost.tapped shouldBe true }
        alice.activates(TlaSet.krellTheMoonSlayer)
        resolveAll()
        expect {
            TlaSet.krellTheMoonSlayer.counters(CounterKind.Named("conqueror")) shouldBe 1
            outpost.subtypes.contains(Subtype("Mountain")) shouldBe true
        }
    }
}
