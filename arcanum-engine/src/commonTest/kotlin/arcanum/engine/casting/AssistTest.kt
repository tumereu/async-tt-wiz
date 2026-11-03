package arcanum.engine.casting

import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Assist (CR 702.132): another player chosen as the spell is cast pays any amount of its generic mana. */
class AssistTest {
    private val troll = Cards.invent("Assist Bridge Troll", "{4}{G}", "Creature — Troll", """
        Assist (Another player can pay up to {4} of this spell's cost.)
    """.trimIndent(), "4/4")

    private fun Scenario.payWithSuggestion() {
        val d = pending
        assertTrue(d is Decision.ManaPayment, "Expected a mana payment, not $d")
        answer(Answer.ManaPayment(d.suggestion))
    }

    // CR 702.132a: the chosen player pays part of the generic mana with their own mana, the caster the rest
    @Test
    fun anotherPlayerPaysPartOfTheGenericMana() = scenario {
        alice.battlefield("Forest", "Forest")
        alice.hand(troll)
        bob.battlefield("Island", "Island", "Island", "Island")
        alice.casts(troll, option = "bob")
        val amount = pending
        assertTrue(amount is Decision.ChooseNumber && amount.player == bob.id && amount.max == 4, "bob chooses how much: $amount")
        bob.answers(Answer.Number(3))
        val bobPays = pending
        assertTrue(bobPays is Decision.ManaPayment && bobPays.player == bob.id, "bob pays: $bobPays")
        payWithSuggestion()
        val alicePays = pending
        assertTrue(alicePays is Decision.ManaPayment && alicePays.player == alice.id, "alice pays the rest: $alicePays")
        assertEquals("{1}{G}", alicePays.cost.toString())
        payWithSuggestion()
        allPass()
        expect {
            alice.battlefield has troll
            bob.battlefield.size shouldBe 4
        }
        assertEquals(3, state.battlefield.count { state.objOrThrow(it).owner == bob.id && state.objOrThrow(it).tapped })
    }

    // CR 702.132a: "may": choosing nobody, the caster pays it all; without the mana they can't
    @Test
    fun nobodyAssists() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest")
        alice.hand(troll)
        bob.battlefield("Island")
        alice.casts(troll, option = "nobody")
        allPass()
        expect { alice.battlefield has troll }
    }

    // CR 702.132a: the chosen player may also pay nothing
    @Test
    fun theAssistingPlayerPaysNothing() = scenario {
        alice.battlefield("Forest", "Forest")
        alice.hand(troll)
        bob.battlefield("Island", "Island", "Island", "Island")
        alice.casts(troll, option = "bob")
        bob.answers(Answer.Number(0))
        val alicePays = pending
        assertTrue(alicePays is Decision.ManaPayment && alicePays.player == alice.id, "alice pays it all: $alicePays")
        assertFailsWith<IllegalArgumentException> { answer(Answer.ManaPayment(alicePays.suggestion)) }
    }
}
