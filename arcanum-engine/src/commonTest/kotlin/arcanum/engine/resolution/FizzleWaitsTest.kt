package arcanum.engine.resolution

import arcanum.api.Destination
import arcanum.api.EventPattern
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.ObjectFilter
import arcanum.api.Zone
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** A spell that doesn't resolve still leaves the Stack through the event pipeline, and may wait there (CR 608.2b). */
class FizzleWaitsTest {
    /** Storm Spark's target left; as the card goes to the graveyard, two replacements apply and its owner chooses which
     * first (CR 616.1): the flow waits for that choice instead of failing. */
    // CR 608.2b, 616.1
    @Test
    fun aFizzlingSpellWaitsForAReplacementChoice() = scenario {
        alice.hand("Storm Spark")
        bob.battlefield("Elvish Seer")
        effect(bob, Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Any, to = Zone.Graveyard),
            modify = listOf(Modification.ChangeDestination(Destination(Zone.Exile)))), affects = arcanum.api.Affected.Objects(arcanum.api.Objects.Self))
        effect(bob, Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Any, to = Zone.Graveyard),
            modify = listOf(Modification.ChangeDestination(Destination(Zone.Hand)))), affects = arcanum.api.Affected.Objects(arcanum.api.Objects.Self))
        alice.castsWithoutCosts("Storm Spark", target = "Elvish Seer")
        move("Elvish Seer", Zone.Hand)
        resolve()
        val choice = assertIs<Decision.ChooseReplacement>(pending)
        assertEquals(alice.id, choice.player)
        answer(Answer.Replacement(choice.options.first().index))
        expect {
            stack.isEmpty()
            bob.life shouldBe 20
            alice.graveyard lacks "Storm Spark"
            (alice.hand.size + alice.exile.size) shouldBe 1
        }
    }
}
