package arcanum.engine.cube

import arcanum.api.CostPart
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

/** Chunk 57 in play: Blast from Before, a burn spell with madness, cycling, kicker, flashback and buyback. */
class Chunk57UnCardsScenariosTest {
    /**
     * Alice casts Blast from Before at [target], paying kicker {2}{R} and buyback {4}{R} as asked (CR 601.2b), and the
     * suggested mana.
     */
    private fun Scenario.castBlast(target: String, kicker: Boolean = false, buyback: Boolean = false) {
        perform(Input.Cast(alice.id, card("Blast from Before")))
        while (true) {
            when (val d = pending) {
                is Decision.Targets -> answer(Answer.Targets(listOf(target(target))))
                is Decision.May -> {
                    val cost = (d.cost?.parts?.single() as? CostPart.Mana)?.cost?.toString() ?: fail("Unexpected $d")
                    answer(Answer.YesNo(if (cost == "{2}{R}") kicker else if (cost == "{4}{R}") buyback else fail("Unexpected $cost")))
                }
                is Decision.ManaPayment -> answer(Answer.ManaPayment(d.suggestion))
                null -> return
                else -> fail("Unexpected $d")
            }
        }
    }

    /** Blast from Before: 2 damage to any target; kicked, also a 1/1 red Goblin. */
    @Test
    fun blastFromBeforeCast() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.hand("Blast from Before")
        castBlast("bob")
        allPass()
        expect {
            bob.life shouldBe 18
            alice.graveyard has "Blast from Before"
            alice.battlefield lacks "Goblin Token"
        }
    }

    // CR 702.33a, 702.33d
    @Test
    fun blastFromBeforeKicked() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand("Blast from Before")
        castBlast("bob", kicker = true)
        expect { (0..5).all { card("Mountain", it).tapped } shouldBe true }
        allPass()
        expect {
            bob.life shouldBe 18
            card("Goblin Token").power shouldBe 1
            card("Goblin Token").colors shouldBe listOf(arcanum.api.Color.Red)
        }
    }

    // CR 702.27a: buyback: back to its owner's hand
    @Test
    fun blastFromBeforeBuyback() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand("Blast from Before")
        castBlast("bob", buyback = true)
        expect { (0..7).all { card("Mountain", it).tapped } shouldBe true }
        allPass()
        expect {
            bob.life shouldBe 18
            alice.hand has "Blast from Before"
        }
    }

    // CR 702.34a: flashback from the graveyard, then exiled
    @Test
    fun blastFromBeforeFlashback() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain")
        alice.graveyard("Blast from Before")
        castBlast("bob")
        expect { (0..3).all { card("Mountain", it).tapped } shouldBe true }
        allPass()
        expect {
            bob.life shouldBe 18
            alice.exile has "Blast from Before"
        }
    }

    // CR 702.29a
    @Test
    fun blastFromBeforeCycling() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.hand("Blast from Before")
        alice.library("Forest")
        alice.activates("Blast from Before")
        expect { alice.graveyard has "Blast from Before" }
        allPass()
        expect { alice.hand.names shouldBe listOf("Forest") }
    }

    /** Hasty Rummage resolves: alice draws two, then discards Blast from Before and Forest. */
    private fun Scenario.rummageAwayBlast() {
        alice.battlefield("Island", "Island", "Island")
        alice.hand("Hasty Rummage", "Blast from Before", "Forest")
        alice.library("Swamp", "Plains")
        alice.casts("Hasty Rummage")
        allPass()
        alice.chooses("Blast from Before", "Forest")
        // Untap up to three lands: the ones it was paid with
        val untap = decision<Decision.ChooseObjects>()
        alice.answers(Answer.Objects(untap.options.filter { state.objOrThrow(it).tapped }.take(untap.max ?: 3)))
    }

    // CR 702.35a: discarded, it's exiled and may be cast for {R}
    @Test
    fun blastFromBeforeMadness() = scenario {
        alice.battlefield("Mountain")
        rummageAwayBlast()
        expect {
            alice.exile has "Blast from Before"
            stack.names shouldBe listOf("ability of Blast from Before")
        }
        allPass()
        alice.answers(Answer.YesNo(true))
        alice.answers(Answer.Targets(listOf(target("bob"))))
        alice.answers(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
        expect {
            "Mountain".tapped shouldBe true
            stack.names shouldBe listOf("Blast from Before")
        }
        assertEqual(arcanum.api.Zone.Exile, state.objOrThrow(card("Blast from Before")).facts.castFrom)
        allPass()
        expect {
            bob.life shouldBe 18
            alice.graveyard has "Blast from Before"
        }
    }

    // CR 702.35a: not cast, it goes to the graveyard
    @Test
    fun blastFromBeforeMadnessDeclined() = scenario {
        rummageAwayBlast()
        allPass()
        alice.answers(Answer.YesNo(false))
        expect {
            alice.graveyard has "Blast from Before"
            alice.exile lacks "Blast from Before"
        }
    }

    // CR 702.35a, 614.1: discarded to pay a cost, it's exiled too
    @Ignore("engine: replacement effects on zone changes paid as costs (Payment moves bypass the event pipeline)")
    @Test
    fun blastFromBeforeMadnessDiscardedAsACost() = scenario {
        alice.battlefield("Rancid Imp")
        alice.hand("Blast from Before")
        alice.activates("Rancid Imp", choosing = listOf("Blast from Before"))
        expect { alice.exile has "Blast from Before" }
    }

    private fun assertEqual(expected: Any?, actual: Any?) = assertEquals(expected, actual)
}
