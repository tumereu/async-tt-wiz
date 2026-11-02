package arcanum.engine.review

import arcanum.api.Color
import arcanum.api.CounterKind
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Test

/**
 * Review 2: decisions the earlier reviews didn't send through JSON (D58): each is answered on the state after a JSON
 * round trip as well, and must give the same changes.
 */
class Review2ContinuationTest {
    private fun Scenario.answerThroughJson(answer: Answer) {
        Review1.sameAfterJson(state, Input.Answer(pending!!.id, answer))
        answer(answer)
    }

    /** A resolution choosing an option, then a colour (Bestower of Sigils). */
    // CR 608.2d, D58
    @Test
    fun optionThenColour() = scenario {
        alice.battlefield("Bestower of Sigils", "Elvish Seer")
        alice.activates("Bestower of Sigils", target = "Elvish Seer")
        allPass()
        answerThroughJson(Answer.Option("a color"))
        answerThroughJson(Answer.ColorChoice(Color.Red))
        expect { "Elvish Seer" hasMechanic { it is arcanum.api.Mechanic.Protection } }
    }

    /** Cascade's "you may cast it" inside a triggered ability's resolution. */
    // CR 702.85a, D58
    @Test
    fun cascadesMay() = scenario {
        alice.battlefield("Forest", "Island", "Island")
        alice.hand("Splintered Envoy")
        alice.library("Forest", "Tinkered Mite", "Island")
        alice.casts("Splintered Envoy")
        allPass()
        decision<Decision.May>()
        answerThroughJson(Answer.YesNo(true))
        expect { stack.names shouldBe listOf("Splintered Envoy", "Tinkered Mite") }
    }

    /** A loyalty ability's two targets (Orla's [−2]) asked while activating. */
    // CR 602.2b, 601.2c, D58
    @Test
    fun twoTargetsWhileActivating() = scenario {
        alice.battlefield("Orla, Gilded Artificer", counters = listOf(CounterKind.Loyalty to 5))
        alice.battlefield("Sun Band", "Elvish Seer")
        val origin = view()[card("Orla, Gilded Artificer")]!!.characteristics!!.abilities.first { it.ability is arcanum.api.Ability.Activated }.origin
        perform(Input.Activate(alice.id, card("Orla, Gilded Artificer"), origin))
        answerThroughJson(Answer.Targets(listOf(target("Sun Band"))))
        answerThroughJson(Answer.Targets(listOf(target("Elvish Seer"))))
        allPass()
        expect { card("Sun Band").view.name shouldBe "Elvish Seer" }
    }

    /** Persist and Endless Return: ordering triggers from a mana ability's cost (Renderer's Altar). */
    // CR 603.3b, 605.3a, D58
    @Test
    fun orderingTriggersFromAManaAbilitysCost() = scenario {
        alice.battlefield("Endless Return", "Festering Gremlin", "Renderer's Altar")
        alice.activates("Renderer's Altar", choosing = listOf("Festering Gremlin"))
        val d = decision<Decision.OrderTriggers>()
        answerThroughJson(Answer.Order(d.triggers.map { it.index }.reversed()))
        expect { stack.size shouldBe 2 }
    }
}
