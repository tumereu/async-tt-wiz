package arcanum.engine.casting

import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.CostChange
import arcanum.api.ManaCost
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.Value
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** "The next … spell you cast costs {1} less": the effect waits for its spells and each one it applies to uses it up
 * (CR 611.2f, 601.2f). */
class NextSpellsTest {
    private fun Scenario.nextInstantCostsOneLess(count: Int) = effect(alice,
        Mechanic.CostChange(ObjectFilter.Any, CostChange.Less(ManaCost.parse("{1}")!!, Value.Fixed(1))),
        affects = Affected.NextSpells(ObjectFilter.Type(CardType.Instant), count))

    /** The mana cost the next Mind Chill (an instant, {1}{U}) is asked to pay. */
    private fun Scenario.costOfCastingMindChill(index: Int): String {
        perform(Input.Cast(alice.id, card("Mind Chill", index)))
        alice.answers(Answer.Targets(listOf(target("bob"))))
        return decision<Decision.ManaPayment>().cost.toString()
    }

    /** The first instant costs {1} less, the second its full cost: the effect is used up and gone. */
    // CR 611.2f, 601.2f
    @Test
    fun theNextSpellUsesTheReductionUp() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.hand("Mind Chill", "Mind Chill")
        nextInstantCostsOneLess(1)
        assertEquals("{U}", costOfCastingMindChill(0))
        answer(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
        assertTrue(state.effects.isEmpty(), "used up: ${state.effects}")
        alice.passes()
        bob.passes()
        assertEquals("{1}{U}", costOfCastingMindChill(1))
    }

    /** A sorcery isn't one of the spells it waits for: it neither gets the reduction nor uses it up. */
    // CR 611.2f
    @Test
    fun anotherSpellDoesntUseItUp() = scenario {
        alice.battlefield("Swamp", "Swamp", "Island")
        alice.hand("Midnight Murmur", "Mind Chill")
        alice.library("Forest", "Forest")
        nextInstantCostsOneLess(1)
        alice.casts("Midnight Murmur", paying = listOf("Swamp", "Swamp#1"))
        assertEquals(1, state.effects.size)
        allPass()
        assertEquals("{U}", costOfCastingMindChill(0))
    }

    /** "The next two": two spells get it, each using up one. */
    // CR 611.2f
    @Test
    fun theNextTwoSpells() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island")
        alice.hand("Mind Chill", "Mind Chill", "Mind Chill")
        nextInstantCostsOneLess(2)
        assertEquals("{U}", costOfCastingMindChill(0))
        answer(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
        assertEquals(1, state.effects.values.single().usesLeft)
        assertEquals("{U}", costOfCastingMindChill(1))
        answer(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
        assertTrue(state.effects.isEmpty())
        assertEquals("{1}{U}", costOfCastingMindChill(2))
    }
}
