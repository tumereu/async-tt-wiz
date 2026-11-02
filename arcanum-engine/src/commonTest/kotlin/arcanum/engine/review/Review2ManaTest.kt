package arcanum.engine.review

import arcanum.api.Color
import arcanum.api.CounterKind
import arcanum.api.TurnStep
import arcanum.engine.Engine
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.decision.ManaUse
import arcanum.engine.input.Input
import arcanum.engine.scenario.scenario
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Review 2: mana (CR 106, 107, 601.2f–h): pools emptying, mana that stays, converge and sunburst counting the colours
 * actually spent, Phyrexian and hybrid choices, X with cost reductions, triggered mana abilities.
 */
class Review2ManaTest {
    /** Mana empties between steps: Grim Offering's {B}{B}{B} added in the upkeep is gone in the draw step. */
    // CR 106.4, 500.4
    @Test
    fun manaEmptiesBetweenSteps() = scenario {
        turn(number = 3, step = TurnStep.Upkeep)
        alice.battlefield("Swamp")
        alice.hand("Grim Offering")
        alice.library("Island")
        alice.casts("Grim Offering")
        allPass()
        assertEquals(3, alice.pool.size)
        advanceTo(TurnStep.Draw)
        assertEquals(0, alice.pool.size)
    }

    /** Skalda's {R} stays through steps until end of turn, then empties. */
    // CR 106.4a
    @Test
    fun skaldasManaStaysUntilEndOfTurn() = scenario {
        alice.battlefield("Skalda, God of Sagas", "Mountain")
        alice.hand("Storm Spark")
        alice.library("Island")
        bob.library("Island")
        alice.casts("Storm Spark", target = "bob")
        allPass()
        assertEquals(1, alice.pool.size)
        advanceTo(TurnStep.End)
        assertEquals(1, alice.pool.size, "kept as steps end")
        advanceTo(TurnStep.Upkeep, turn = 2)
        assertEquals(0, alice.pool.size)
    }

    /** Converge counts the colours actually spent: X 2 paid with {W}{U}{B} is three colours, enough to exile a mana
     * value 3 permanent. */
    // CR 207.2c, 601.2h
    @Test
    fun convergeCountsColoursSpent() = scenario {
        alice.battlefield("Plains", "Island", "Swamp")
        alice.hand("Spectral Finale")
        bob.battlefield("Elvish Hierophant")
        alice.casts("Spectral Finale", x = 2, target = "Elvish Hierophant", paying = listOf("Plains", "Island", "Swamp"))
        allPass()
        expect { bob.exile has "Elvish Hierophant" }
    }

    /** Converge with one colour: X 2 paid with {W}{W}{W} is one colour, not enough for mana value 3. */
    // CR 207.2c, 601.2h
    @Test
    fun convergeWithOneColour() = scenario {
        alice.battlefield("Plains", "Plains", "Plains")
        alice.hand("Spectral Finale")
        bob.battlefield("Elvish Hierophant")
        alice.casts("Spectral Finale", x = 2, target = "Elvish Hierophant")
        allPass()
        expect { bob.battlefield has "Elvish Hierophant" }
    }

    /** Sunburst counts colours spent: Tuned Detonators X 2 paid with {R}{G} enters with two charge counters. */
    // CR 702.44a
    @Test
    fun sunburstCountsColours() = scenario {
        alice.battlefield("Mountain", "Forest")
        alice.hand("Tuned Detonators")
        alice.casts("Tuned Detonators", x = 2, paying = listOf("Mountain", "Forest"))
        allPass()
        expect { "Tuned Detonators".counters(CounterKind.Named("charge")) shouldBe 2 }
    }

    /** Sunburst with colourless mana only: no counters. */
    // CR 702.44a
    @Test
    fun sunburstWithColourlessMana() = scenario {
        alice.battlefield("Sun Band")
        alice.hand("Tuned Detonators")
        alice.casts("Tuned Detonators", x = 2)
        allPass()
        expect { "Tuned Detonators".counters(CounterKind.Named("charge")) shouldBe 0 }
    }

    /** Phyrexian mana paid with life: Sever Limb for {1} and 4 life. */
    // CR 107.4f, 601.2h
    @Test
    fun phyrexianManaPaidWithLife() = scenario {
        alice.battlefield("Mountain")
        alice.hand("Sever Limb")
        bob.battlefield("Brakka, Wild Whelp")
        perform(Input.Cast(alice.id, card("Sever Limb")))
        alice.answers(Answer.Targets(listOf(target("Brakka, Wild Whelp"))))
        val pay = decision<Decision.ManaPayment>()
        val lives = pay.options.filterIsInstance<ManaUse.Life>()
        assertEquals(2, lives.size, "each Phyrexian symbol can be paid with life: ${pay.options}")
        answer(Answer.ManaPayment(lives + pay.options.filterIsInstance<ManaUse.Ability>()))
        allPass()
        expect {
            alice.life shouldBe 16
            bob.graveyard has "Brakka, Wild Whelp"
        }
    }

    /** Hybrid: {1}{G/W} paid with two Plains. */
    // CR 107.4e
    @Test
    fun hybridPaidWithEitherColour() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.hand("Haven Warden")
        alice.casts("Haven Warden")
        allPass()
        expect { alice.battlefield has "Haven Warden" }
    }

    /** A cost reduction reduces X's generic mana too: Procession of Pale Light with X 3, one white card exiled
     * ({2} less), costs {1}{W}. */
    // CR 601.2f, 202.3e
    @Test
    fun costReductionAppliesToX() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.hand("Procession of Pale Light", "Haven Warden")
        bob.battlefield("Elvish Hierophant")
        alice.casts("Procession of Pale Light", x = 3, target = "Elvish Hierophant", choosing = listOf("Haven Warden"))
        allPass()
        expect {
            bob.exile has "Elvish Hierophant"
            alice.exile has "Haven Warden"
        }
    }

    /** Swelling Tide: tapping an Island for mana adds an additional {U} (a triggered mana ability, at once). */
    // CR 605.1b, 605.4a
    @Test
    fun swellingTideAddsMana() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.hand("Swelling Tide", "Hasty Rummage")
        alice.library("Forest", "Forest")
        alice.casts("Swelling Tide")
        allPass()
        // Hasty Rummage {2}{U}: two Islands make four mana
        alice.casts("Hasty Rummage", paying = listOf("Island#1", "Island#2"))
        assertTrue(state.stack.isNotEmpty(), "cast with two Islands")
    }

    /** The mana in the pool can pay for ward's cost while it resolves. */
    // CR 702.21a, 118.3
    @Test
    fun poolManaPaysWard() = scenario {
        alice.battlefield("Mountain", "Swamp")
        alice.hand("Storm Spark", "Grim Offering")
        bob.battlefield("Scrapquilt Automaton")
        alice.casts("Grim Offering", paying = listOf("Swamp"))
        allPass()
        alice.casts("Storm Spark", target = "Scrapquilt Automaton", paying = listOf("Mountain"))
        allPass()
        if (pending is Decision.May) alice.answers(Answer.YesNo(true))
        if (pending is Decision.ManaPayment) answer(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
        allPass()
        expect { bob.graveyard has "Scrapquilt Automaton" }
    }
}
