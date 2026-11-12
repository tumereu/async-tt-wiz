package arcanum.engine.mutate

import arcanum.engine.state.mergedAbove
import arcanum.engine.change.Change
import arcanum.api.Ability
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.CastWay
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Mutate (CR 702.140) and merged permanents (CR 730): merging on top or under, characteristics and abilities, splitting as it leaves. */
class MutateTest {
    private val hulk = Cards.invent("Mutate Hulking Brute", "{3}{G}{G}", "Creature — Beast", """
        Mutate {2}{G}
        Trample
        Whenever this creature mutates, draw a card.
    """.trimIndent(), "6/6")
    private val wolf = Cards.invent("Mutate Plain Wolf", "{1}{G}", "Creature — Wolf", "Vigilance", "2/2")
    private val squire = Cards.invent("Mutate Plain Squire", "{1}{W}", "Creature — Human Soldier", "", "2/2")

    private fun Scenario.mutate(name: String): CastWay = CastWay(alternative = view()[card(name)]!!.characteristics!!.abilities.first { inst ->
        (inst.ability as? Ability.Static)?.mechanics?.any { it is Mechanic.AlternativeCost } == true
    }.origin)

    private fun Scenario.wolfAndBrute() {
        alice.battlefield("Forest", "Forest", "Forest", wolf)
        alice.hand(hulk)
        alice.library("Forest", "Forest")
    }

    private fun Scenario.place(where: String) {
        val d = pending
        assertTrue(d is Decision.ChooseOption, "Expected the on top / under choice, not $d")
        answer(Answer.Option(where))
    }

    // CR 702.140a, c, e, 730.2a: on top, the merged permanent has the top card's characteristics and every card's abilities;
    // CR 702.140d: "whenever this creature mutates"; CR 730.2b: it didn't enter
    @Test
    fun mergesOnTop() = scenario {
        wolfAndBrute()
        alice.casts(hulk, way = mutate(hulk), target = wolf)
        allPass()
        place(Mutate.ON_TOP)
        allPass()
        expect {
            alice.battlefield.size shouldBe 4
            hulk.power shouldBe 6
            hulk hasKeyword Keyword.Trample
            hulk hasKeyword Keyword.Vigilance
            alice.hand has "Forest"
        }
        val merged = state.objOrThrow(card(hulk))
        assertEquals(card(wolf, zone = Zone.Battlefield), merged.id)
        assertEquals(1, merged.mergedAbove.size)
    }

    // CR 702.140c, e: under, the characteristics stay the creature's, with the abilities of both
    @Test
    fun mergesUnder() = scenario {
        wolfAndBrute()
        alice.casts(hulk, way = mutate(hulk), target = wolf)
        allPass()
        place(Mutate.UNDER)
        allPass()
        expect {
            wolf.power shouldBe 2
            wolf hasKeyword Keyword.Trample
            wolf hasKeyword Keyword.Vigilance
        }
    }

    // CR 730.3: as the merged permanent dies, each of its cards is put into the graveyard
    @Test
    fun splitsAsItDies() = scenario {
        wolfAndBrute()
        alice.battlefield("Mountain")
        alice.hand("Storm Spark")
        alice.casts(hulk, way = mutate(hulk), target = wolf)
        allPass()
        place(Mutate.UNDER)
        allPass()
        alice.casts("Storm Spark", target = wolf)
        allPass()
        expect {
            alice.graveyard has wolf
            alice.graveyard has hulk
            alice.battlefield lacks wolf
        }
    }

    // CR 730.3: returned to its owner's hand, both cards go
    @Test
    fun splitsAsItIsReturned() = scenario {
        wolfAndBrute()
        bob.battlefield("Island", "Island")
        bob.hand("Click Back")
        alice.casts(hulk, way = mutate(hulk), target = wolf)
        allPass()
        place(Mutate.ON_TOP)
        allPass()
        alice.passes()
        bob.casts("Click Back", target = hulk)
        allPass()
        expect {
            alice.hand has wolf
            alice.hand has hulk
        }
    }

    // CR 702.140b: with its target gone, the mutating creature spell resolves as a creature spell and enters
    @Test
    fun illegalTargetEntersAsACreature() = scenario {
        wolfAndBrute()
        bob.battlefield("Mountain")
        bob.hand("Storm Spark")
        alice.casts(hulk, way = mutate(hulk), target = wolf)
        alice.passes()
        bob.casts("Storm Spark", target = wolf)
        allPass()
        allPass()
        expect {
            alice.battlefield has hulk
            alice.graveyard has wolf
            hulk.power shouldBe 6
        }
    }

    // CR 702.140a: only a non-Human creature with the same owner
    @Test
    fun targetsOnlyNonHumansOfTheSameOwner() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", squire)
        bob.battlefield(wolf)
        alice.hand(hulk)
        assertFailsWith<IllegalArgumentException> { alice.casts(hulk, way = mutate(hulk), target = squire) }
    }

    // CR 730.2a, 702.140e: mutating again on top: the newest top card's characteristics, all three cards' abilities
    @Test
    fun mutatesTwice() = scenario {
        val beetle = Cards.invent("Mutate Horned Beetle", "{1}{G}", "Creature — Insect", """
            Mutate {G}
            Reach
        """.trimIndent(), "1/5")
        wolfAndBrute()
        alice.battlefield("Forest")
        alice.hand(beetle)
        alice.casts(hulk, way = mutate(hulk), target = wolf)
        allPass()
        place(Mutate.ON_TOP)
        allPass()
        alice.casts(beetle, way = mutate(beetle), target = hulk)
        allPass()
        place(Mutate.ON_TOP)
        allPass()
        expect {
            beetle.power shouldBe 1
            beetle.toughness shouldBe 5
            beetle hasKeyword Keyword.Reach
            beetle hasKeyword Keyword.Trample
            beetle hasKeyword Keyword.Vigilance
            alice.hand.size shouldBe 2
        }
    }

    // CR 730.2e, 708.2a: merged on top of a face-down creature, the permanent is face up and everyone sees its card. Merged
    // under, it stays a face-down 2/2.
    @Test
    fun onTopOfAFaceDownCreature() = listOf(Mutate.ON_TOP, Mutate.UNDER).forEach { where ->
        scenario {
            alice.battlefield("Forest", "Forest", "Forest")
            alice.battlefield(wolf, faceDown = true)
            alice.hand(hulk)
            alice.library("Forest")
            val morph = card(wolf)
            // Its mutate keyword is its first printed ability
            val mutate = CastWay(alternative = arcanum.engine.view.AbilityOrigin.Expansion(arcanum.engine.view.AbilityOrigin.Printed(0, 0), 0))
            alice.casts(hulk, way = mutate, target = wolf)
            allPass()
            place(where)
            allPass()
            val merged = state.objOrThrow(morph)
            if (where == Mutate.ON_TOP) {
                assertTrue(!merged.faceDown)
                assertTrue(applied.any { it is Change.Shown && it.to == null && morph in it.ids }, "everyone sees the card")
                expect {
                    hulk.power shouldBe 6
                    hulk hasKeyword Keyword.Vigilance
                }
            } else {
                assertTrue(merged.faceDown)
                assertEquals(2, view()[morph]!!.characteristics!!.power)
            }
        }
    }
}
