package arcanum.engine.trigger

import arcanum.api.Ability
import arcanum.api.CardDefinition
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.scenario.scenario
import kotlin.test.Test

/** "The nth … during each of your [step]s" (`Trigger.On.during`): only events in that step count, and only there it triggers. */
class NthDuringStepTest {
    /** "Whenever you draw your second card during each of your draw steps, you gain 2 life." (written by hand from the parsed
     * "each turn" wording: the parser doesn't read "during each of your draw steps" yet) */
    private val ledger = run {
        val base = Cards.definition(Cards.invent("Draw Step Ledger Base", "{1}{W}", "Enchantment",
            "Whenever you draw your second card each turn, you gain 2 life."))
        val face = base.faces.single()
        val t = face.abilities.single() as Ability.Triggered
        val during = t.copy(trigger = (t.trigger as Trigger.On).copy(during = TurnStep.Draw))
        Cards.define("Draw Step Ledger", CardDefinition(base.layout, listOf(face.copy(name = "Draw Step Ledger", abilities = listOf(during)))))
    }
    private val glimpse = Cards.invent("Quick Ledger Glimpse", "{U}", "Instant", "Draw a card.")

    // CR 603.2: the second card drawn during the draw step (the step's draw, then a spell's) triggers it
    @Test
    fun secondDrawDuringTheDrawStep() = scenario {
        turn(number = 3, active = alice, step = TurnStep.Upkeep)
        alice.battlefield(ledger, "Island")
        alice.hand(glimpse)
        alice.library("Island", "Island", "Island")
        advanceTo(TurnStep.Draw)
        alice.casts(glimpse)
        allPass()
        allPass()
        expect { alice.life shouldBe 22 }
    }

    // CR 603.2: draws outside the draw step neither count nor trigger it, though they are the second and third this turn
    @Test
    fun drawsOutsideTheStepDontCount() = scenario {
        turn(number = 3, active = alice, step = TurnStep.Upkeep)
        alice.battlefield(ledger, "Island", "Island")
        alice.hand(glimpse, glimpse)
        alice.library("Island", "Island", "Island", "Island")
        advanceTo(TurnStep.PrecombatMain)
        alice.casts(glimpse)
        allPass()
        alice.casts(glimpse)
        allPass()
        expect { alice.life shouldBe 20 }
    }
}
