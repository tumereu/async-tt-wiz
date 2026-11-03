package arcanum.engine.sets.tla

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.tla.TlaExtras.bear
import arcanum.engine.sets.tla.TlaExtras.notes
import arcanum.engine.sets.tla.TlaExtras.ogre
import arcanum.engine.sets.tla.TlaExtras.p1p1
import arcanum.engine.sets.tla.TlaExtras.quest
import arcanum.engine.sets.tla.TlaExtras.zap
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * Avatar: The Last Airbender chunk 01 cards in play: airbend from triggers, modes, spells and an Equipment; casting airbended cards for {2};
 * the Saga of the second-spell Avatar; the iceberg that exiles until it leaves. Kiro, the Last Windwalker's airbend is played in
 * [TlaMechanicsScenariosTest.airbendedCardCastForTwo], Windweaving Lesson in [TlaMechanicsScenariosTest.allFourBendsTransform].
 */
class TlaAirbendScenariosTest {
    /** Casting a Lesson gives Kiro lifelink until end of turn. */
    // CR 702.15
    @Test
    fun kiroGainsLifelinkFromLessons() = scenario {
        alice.battlefield(TlaSet.kiroTheLastWindwalker, "Plains")
        alice.hand(TlaExtras.lesson)
        alice.casts(TlaExtras.lesson)
        resolveAll()
        expect { TlaSet.kiroTheLastWindwalker hasKeyword Keyword.Lifelink }
    }

    /** The ascension's fourth quest counter, then its end step blink of a creature you control. */
    // CR 603.4, 400.7
    @Test
    fun windwalkerAscensionBlinks() = scenario {
        alice.battlefield(TlaSet.windwalkerAscension, counters = listOf(quest to 3))
        alice.battlefield(ogre, "Forest", "Forest")
        alice.hand(bear)
        alice.casts(bear)
        resolveAll()
        expect { TlaSet.windwalkerAscension.counters(quest) shouldBe 4 }
        advanceTo(TurnStep.End)
        resolveAll()
        targets(ogre)
        expect { ogre.zone shouldBe Zone.Battlefield }
    }

    /** Its enters trigger airbends an opponent's creature. */
    // CR 701.65a
    @Test
    fun windwalkerAscensionAirbends() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.hand(TlaSet.windwalkerAscension)
        bob.battlefield(ogre)
        alice.casts(TlaSet.windwalkerAscension)
        resolveAll()
        targets(ogre)
        expect { ogre.zone shouldBe Zone.Exile }
    }

    /** Mode two airbends your own creature to save it; mode one destroys an attacker. */
    // CR 700.2, 701.65a
    @Test
    fun windwalkersReversalBothModes() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", bear)
        alice.hand(TlaSet.windwalkersReversal)
        alice.casts(TlaSet.windwalkersReversal, modes = listOf(1), target = bear)
        resolveAll()
        expect { bear.zone shouldBe Zone.Exile }
        alice.casts(bear, paying = listOf("Plains#2", "Plains#3"))
        resolveAll()
        expect { bear.zone shouldBe Zone.Battlefield }
    }

    /** Attacking with the Yak: its chosen mode gives a creature flying. */
    // CR 700.2, 603.3c
    @Test
    fun brumbleLoyalSkyYakGivesFlying() = scenario {
        alice.battlefield(TlaSet.brumbleLoyalSkyYak, bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(TlaSet.brumbleLoyalSkyYak)
        resolveAll()
        modes(0)
        targets(bear)
        expect { bear hasKeyword Keyword.Flying }
    }

    /** It airbends your own permanents as it enters; casting one from exile makes an Ally. */
    // CR 701.65a, 601.2a
    @Test
    fun brumbleSteadfastGuardianRecasts() = scenario {
        alice.battlefield(bear, "Plains", "Plains", "Plains", "Plains", "Forest", "Forest")
        alice.hand(TlaSet.brumbleSteadfastGuardian)
        alice.casts(TlaSet.brumbleSteadfastGuardian, paying = listOf("Plains", "Plains", "Plains", "Plains"))
        resolveAll()
        targets(bear)
        expect { bear.zone shouldBe Zone.Exile }
        alice.casts(bear, paying = listOf("Forest", "Forest"))
        resolveAll()
        expect {
            bear.zone shouldBe Zone.Battlefield
            alice.battlefield has "Ally Token"
        }
    }

    /** Every creature but the chosen one is airbended; until your next turn the opponent can't cast from exile; the spell exiles itself. */
    // CR 701.65a, 601.2a (casting bans), 101.2
    @Test
    fun heraldsWrathLeavesOne() = scenario {
        alice.battlefield(bear, "Plains", "Plains", "Plains", "Plains")
        alice.hand(TlaSet.heraldsWrath)
        bob.battlefield(ogre, "Mountain", "Mountain")
        bob.library(notes, notes)
        alice.casts(TlaSet.heraldsWrath, target = bear)
        resolveAll()
        expect {
            bear.zone shouldBe Zone.Battlefield
            ogre.zone shouldBe Zone.Exile
            TlaSet.heraldsWrath.zone shouldBe Zone.Exile
        }
        advanceTo(TurnStep.PrecombatMain, turn = 2)
        assertFailsWith<IllegalArgumentException> { bob.casts(ogre) }
    }

    /** The staff airbends as it enters; equipped, a creature gets +1/+1 and flying. */
    // CR 701.65a, 702.6a
    @Test
    fun kiteStaff() = scenario {
        alice.battlefield(bear, "Plains", "Plains", "Plains", "Plains", "Plains")
        alice.hand(TlaSet.kiteStaff)
        bob.battlefield(ogre)
        alice.casts(TlaSet.kiteStaff, paying = listOf("Plains", "Plains", "Plains"))
        resolveAll()
        targets(ogre)
        alice.activates(TlaSet.kiteStaff, target = bear)
        resolveAll()
        expect {
            ogre.zone shouldBe Zone.Exile
            bear.power shouldBe 3
            bear hasKeyword Keyword.Flying
        }
    }

    /** Chapter I: starting with you, each player picks an opponent's permanent with mana value 3 or more, and they're exiled. */
    // CR 714.2b, 101.4
    @Test
    fun taleOfWynneChapterOne() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains")
        alice.hand(TlaSet.theTaleOfWynne)
        bob.battlefield(ogre, TlaExtras.brute, bear)
        alice.casts(TlaSet.theTaleOfWynne)
        resolveAll()
        choose(ogre)
        choose(TlaExtras.brute)
        expect {
            ogre.zone shouldBe Zone.Exile
            TlaExtras.brute.zone shouldBe Zone.Exile
            bear.zone shouldBe Zone.Battlefield
        }
    }

    /** Chapter III transforms it into the Avatar; the second spell each turn airbends. */
    // CR 714.2b, 701.27, 603.2
    @Test
    fun taleOfWynneBecomesTheAvatar() = scenario {
        turn(step = TurnStep.Upkeep)
        alice.battlefield(TlaSet.theTaleOfWynne, counters = listOf(CounterKind.Lore to 2))
        alice.battlefield("Island", "Island")
        alice.library(notes, notes, notes)
        alice.hand(notes, notes)
        bob.battlefield(ogre)
        advanceTo(TurnStep.PrecombatMain)
        resolveAll()
        expect { "Herald Wynne".power shouldBe 4 }
        alice.casts(notes)
        resolveAll()
        alice.casts(notes)
        resolveAll()
        targets(ogre)
        expect { ogre.zone shouldBe Zone.Exile }
    }

    /** Waterbend {8} transforms Kiro; Kiro and Tide attacking puts a counter on each tapped creature you control. */
    // CR 701.67a, 701.27
    @Test
    fun kiroSwiftSaviorTransformsAndAttacks() = scenario {
        alice.battlefield(TlaSet.kiroSwiftSavior, "Island", "Island", "Island", "Island", "Island", "Island", "Island", "Island")
        alice.battlefield(bear, tapped = true)
        alice.activates(TlaSet.kiroSwiftSavior)
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks("Kiro and Tide, Ocean's Fury")
        resolveAll()
        expect {
            bear.counters(p1p1) shouldBe 1
            "Kiro and Tide, Ocean's Fury".counters(p1p1) shouldBe 1
        }
    }

    /** The glacier exiles a permanent until it leaves; waterbending it away (paid by tapping) scries and returns the permanent. */
    // CR 610.3, 701.67a, 701.22
    @Test
    fun kirosGlacier() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", bear, TlaExtras.brute, TlaExtras.trinket)
        alice.hand(TlaSet.kirosGlacier)
        alice.library(notes, notes)
        bob.battlefield(ogre)
        alice.casts(TlaSet.kirosGlacier)
        resolveAll()
        targets(ogre)
        expect { ogre.zone shouldBe Zone.Exile }
        alice.activates(TlaSet.kirosGlacier, paying = listOf(bear, TlaExtras.brute, TlaExtras.trinket))
        resolveAll()
        if (pending != null) choose()
        expect {
            TlaSet.kirosGlacier.zone shouldBe Zone.Graveyard
            ogre.zone shouldBe Zone.Battlefield
            bear.tapped shouldBe true
        }
    }
}
