package arcanum.engine.trigger

import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Which triggers that trigger together their controller is asked to order (CR 603.3b). */
class TriggerOrderTest {
    private val warden = Cards.invent("Burrowgate Warden", "{1}{W}", "Creature — Rabbit Soldier",
        "Whenever another creature you control enters, you gain 1 life.", "2/2")
    private val twins = Cards.invent("Twin Burrows", "{1}{G}", "Sorcery", "Create two 1/1 green Squirrel creature tokens.")
    private val herald = Cards.invent("Twinbloom Herald", "{G}", "Creature — Elf Druid",
        "When this creature enters, you gain 1 life.\nWhen this creature enters, draw a card.", "1/1")

    /** Two tokens entering at once trigger the Warden twice: the same ability of the same source, interchangeable, so
     * nothing is asked. */
    // CR 603.3b
    @Test
    fun identicalTriggersAreNotOrdered() = scenario {
        alice.battlefield(warden)
        alice.hand(twins)
        alice.castsWithoutCosts(twins)
        allPass()
        assertTrue(pending !is Decision.OrderTriggers, "not asked: $pending")
        expect { stack.size shouldBe 2 }
        allPass()
        allPass()
        expect { alice.life shouldBe 22 }
    }

    /** The Herald's two different enters abilities: Alice orders them. */
    // CR 603.3b
    @Test
    fun differentTriggersAreOrdered() = scenario {
        alice.hand(herald)
        alice.library("Island")
        alice.castsWithoutCosts(herald)
        allPass()
        val order = decision<Decision.OrderTriggers>()
        assertEquals(2, order.triggers.size)
        answer(Answer.Order(listOf(1, 0)))
        assertNull(pending)
        expect { stack.size shouldBe 2 }
    }
}
