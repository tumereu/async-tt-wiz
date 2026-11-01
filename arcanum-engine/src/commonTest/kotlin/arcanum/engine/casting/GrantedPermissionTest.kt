package arcanum.engine.casting

import arcanum.engine.cards.Cards
import arcanum.engine.scenario.scenario
import kotlin.test.Test

/** Permissions granted only where the card is cast from (CR 601.2a: the card moves to the Stack before its costs). */
class GrantedPermissionTest {
    private val bear = Cards.invent("Mossback Cub", "{1}{G}", "Creature — Bear", "", "2/2")

    // CR 702.81a, 601.2f: retrace granted to cards in a graveyard still adds its discard once the card is on the Stack
    @Test
    fun grantedRetraceAddsItsCost() = scenario {
        alice.battlefield("Oakheart", "Forest", "Forest")
        alice.graveyard(bear)
        alice.hand("Island")

        alice.casts(bear)

        expect {
            alice.graveyard has "Island"
            stack.names shouldBe listOf(bear)
        }
    }
}
