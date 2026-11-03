package arcanum.engine.sets.tla

import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.CounterKind
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.input.CastWay
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Avatar: The Last Airbender mechanics in play: airbend (CR 701.65), earthbend (CR 701.66), waterbend costs paid by tapping (CR 701.67)
 * and firebending's mana kept until end of combat (CR 702.189), and the triggers on each.
 */
class TlaMechanicsScenariosTest {
    private val bear = Cards.invent("Meadow Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val ogre = Cards.invent("Hill Ogre", "{2}{R}", "Creature — Ogre", "", "3/3")
    private val trinket = Cards.invent("Brass Trinket", "{1}", "Artifact", "")
    private val zap = Cards.invent("Spark Zap", "{R}", "Instant", "Spark Zap deals 2 damage to any target.")
    private val notes = Cards.invent("Scribbled Notes", "{U}", "Sorcery", "Draw a card.")
    private val p1p1 = CounterKind.PowerToughness(1, 1)

    /** Airbend: the opponent's creature is exiled; its owner may cast it from exile for {2} rather than its mana cost. */
    // CR 701.65a
    @Test
    fun airbendedCardCastForTwo() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains")
        alice.hand(TlaSet.kiroTheLastWindwalker)
        bob.battlefield(ogre, "Forest", "Forest")
        bob.library(notes, notes)
        alice.casts(TlaSet.kiroTheLastWindwalker)
        resolveAll()
        alice.answers(Answer.Targets(listOf(target(ogre))))
        resolveAll()
        expect { ogre.zone shouldBe Zone.Exile }
        advanceTo(TurnStep.PrecombatMain, turn = 2)
        bob.casts(ogre)
        resolveAll()
        expect {
            ogre.zone shouldBe Zone.Battlefield
            card("Forest", 0).tapped shouldBe true
            card("Forest", 1).tapped shouldBe true
        }
    }

    /** Airbend a spell: it leaves the Stack for exile and doesn't resolve; its owner may cast it again for {2}. */
    // CR 701.65a
    @Test
    fun airbendASpell() = scenario {
        alice.battlefield("Plains", "Island", "Island")
        alice.hand(TlaSet.kiroSwiftSavior)
        bob.battlefield("Mountain")
        bob.hand(zap)
        turn(active = bob)
        bob.casts(zap, target = "alice")
        bob.passes()
        alice.casts(TlaSet.kiroSwiftSavior)
        resolveAll()
        alice.answers(Answer.Targets(listOf(target(zap))))
        resolveAll()
        expect {
            zap.zone shouldBe Zone.Exile
            alice.life shouldBe 20
        }
    }

    /** Earthbend: the land becomes a 0/0 creature with haste and counters; when it dies it returns tapped. */
    // CR 701.66a
    @Test
    fun earthbentLandFightsAndReturns() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest")
        alice.hand(TlaSet.stoneshapingLesson)
        bob.battlefield("Mountain")
        bob.hand(zap)
        alice.casts(TlaSet.stoneshapingLesson, target = "Forest")
        resolveAll()
        val land = card("Forest", 0)
        expect {
            card("Forest", 0).power shouldBe 4
            card("Forest", 0).toughness shouldBe 4
            card("Forest", 0).types.containsAll(listOf(CardType.Land, CardType.Creature)) shouldBe true
            "Forest" hasKeyword arcanum.api.Keyword.Haste
        }
        assertTrue(state.obj(land)!!.zone == Zone.Battlefield)
        alice.passes()
        bob.casts(zap, target = "Forest")
        resolveAll()
        // 2 damage on a 4/4 doesn't kill it
        expect { card("Forest", 0).zone shouldBe Zone.Battlefield }
    }

    /** The earthbent land dies and returns tapped as a plain land. */
    // CR 701.66a
    @Test
    fun earthbentLandReturnsWhenItDies() = scenario {
        alice.battlefield("Forest", "Forest")
        alice.hand(TlaSet.burrowmoleCub)
        bob.battlefield("Mountain")
        bob.hand(zap)
        alice.casts(TlaSet.burrowmoleCub)
        resolveAll()
        targets("Forest")
        expect { card("Forest", 0).counters(p1p1) shouldBe 1 }
        alice.passes()
        bob.casts(zap, target = "Forest")
        resolveAll()
        expect {
            card("Forest", 0).zone shouldBe Zone.Battlefield
            card("Forest", 0).tapped shouldBe true
            card("Forest", 0).types shouldBe listOf(CardType.Land)
        }
    }

    /** Waterbend: an activated ability's generic mana paid by tapping creatures and artifacts. */
    // CR 701.67a
    @Test
    fun waterbendByTapping() = scenario {
        alice.battlefield(TlaSet.spoutJumper, bear, trinket, "Island", "Island")
        alice.library(notes, notes)
        alice.hand(zap)
        alice.activates(TlaSet.spoutJumper, paying = listOf(bear, trinket, "Island", "Island"))
        resolveAll()
        alice.chooses(zap)
        resolveAll()
        expect {
            bear.tapped shouldBe true
            trinket.tapped shouldBe true
            alice.graveyard has zap
        }
    }

    /** Only the waterbend cost's generic mana may be paid by tapping, not the spell's other mana. */
    // CR 701.67b
    @Test
    fun waterbendPaysOnlyItsOwnGeneric() = scenario {
        alice.battlefield(bear, ogre, trinket, "Island", "Island")
        alice.hand(TlaSet.kindlyBrookSpirit)
        // {U}{U} by Islands, the waterbend {5} by three tapped objects is not enough
        assertFailsWith<IllegalArgumentException> {
            alice.casts(TlaSet.kindlyBrookSpirit, paying = listOf("Island", "Island", bear, ogre, trinket))
        }
    }

    /** "Unless you waterbend {2}": tapping two creatures pays it, so no card is discarded. */
    // CR 118.12a, 701.67a
    @Test
    fun unlessYouWaterbend() = scenario {
        alice.battlefield(bear, ogre, "Island", "Island", "Island", "Island")
        alice.hand(TlaSet.tideshapingLesson)
        alice.library(notes, notes, notes)
        alice.casts(TlaSet.tideshapingLesson)
        resolveAll()
        // pay the waterbend cost: with every Island tapped, both creatures must be tapped (no choice to make)
        alice.answers(Answer.YesNo(true))
        resolveAll()
        expect {
            bear.tapped shouldBe true
            ogre.tapped shouldBe true
            alice.hand.size shouldBe 3
        }
    }

    /** Firebending: attacking adds {R}{R} that stays through combat and is lost as combat ends. */
    // CR 702.189a
    @Test
    fun firebendingManaUntilEndOfCombat() = scenario {
        alice.battlefield(TlaSet.vexaOnTheHunt)
        alice.library(notes)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(TlaSet.vexaOnTheHunt)
        resolveAll()
        expect { alice.pool.count { it.color == Color.Red } shouldBe 2 }
        advanceTo(TurnStep.CombatDamage)
        expect { alice.pool.count { it.color == Color.Red } shouldBe 2 }
        advanceTo(TurnStep.PostcombatMain)
        expect { alice.pool.size shouldBe 0 }
    }

    /** Waterbend, earthbend, airbend and firebend in one turn: Herald Kiro draws for each and transforms after the fourth. */
    // CR 701.65b, 701.66b, 701.67c, 702.189b, 701.27
    @Test
    fun allFourBendsTransform() = scenario {
        alice.battlefield(TlaSet.heraldKiro, TlaSet.spoutJumper, TlaSet.stoneholm, "Island", "Island", "Island", "Island",
            "Forest", "Forest", "Forest", "Plains", "Plains", "Plains")
        alice.library(notes, notes, notes, notes, notes, notes, notes, notes)
        alice.hand(TlaSet.windweavingLesson)
        bob.battlefield(ogre)
        // waterbend: Spout Jumper's loot, and Kiro draws
        alice.activates(TlaSet.spoutJumper, paying = listOf("Island", "Island", "Island", "Island"))
        resolveAll()
        alice.chooses(notes)
        resolveAll()
        expect { alice.hand.size shouldBe 2 }
        // earthbend
        alice.activates(TlaSet.stoneholm, ability = 1, target = "Forest", paying = listOf("Forest", "Forest", "Forest"))
        resolveAll()
        expect { alice.hand.size shouldBe 3 }
        // airbend
        alice.casts(TlaSet.windweavingLesson, target = ogre, paying = listOf("Plains", "Plains", "Plains"))
        resolveAll()
        expect {
            ogre.zone shouldBe Zone.Exile
            alice.hand.size shouldBe 4
            TlaSet.heraldKiro.power shouldBe 4
        }
        // firebend: the fourth, so it transforms
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(TlaSet.heraldKiro)
        resolveAll()
        expect { "Kiro, Master of the Four".power shouldBe 6 }
    }
}
