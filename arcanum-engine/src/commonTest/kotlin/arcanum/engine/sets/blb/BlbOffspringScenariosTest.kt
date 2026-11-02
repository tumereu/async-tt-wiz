package arcanum.engine.sets.blb

import arcanum.engine.cards.Cards
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals

/** Bloomburrow's offspring (CR 702.175), played with the invented cards of the parser's chunk 01. */
class BlbOffspringScenariosTest {
    private val bruiser = Cards.invent("Tinshield Bruiser", "{3}{G}", "Creature — Raccoon Warrior", """
        Offspring {2} (You may pay an additional {2} as you cast this spell. If you do, when this creature enters, create a 1/1 token copy of it.)
        This creature can't be blocked by creatures with power 2 or less.
    """, "4/4")

    /** Paid: a 1/1 token copy enters too, with the copied abilities but not paid for, so it makes no copy itself. */
    // CR 702.175a: the offspring cost is an optional additional cost; the trigger creates a token copy, except it's 1/1
    // CR 707.9b: the exception is part of the copy's copiable values
    @Test
    fun offspringPaidMakesA1By1Copy() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest", "Forest")
        alice.hand(bruiser)

        alice.casts(bruiser, kicked = 1)
        allPass()
        expect { stack.size shouldBe 1 }
        allPass()
        expect {
            alice.battlefield.names.count { it == bruiser } shouldBe 2
            card(bruiser, 0).power shouldBe 4
            card(bruiser, 1).power shouldBe 1
            card(bruiser, 1).toughness shouldBe 1
            stack.isEmpty()
        }
        val token = state.objOrThrow(card(bruiser, 1))
        assertEquals(true, token.token)
    }

    /** Not paid: the trigger's "if its offspring cost was paid" is false, so it doesn't trigger. */
    // CR 603.4: an intervening "if" is checked as it would trigger
    @Test
    fun offspringNotPaidMakesNothing() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest")
        alice.hand(bruiser)

        alice.casts(bruiser, kicked = 0)
        allPass()
        expect {
            alice.battlefield.names.count { it == bruiser } shouldBe 1
            stack.isEmpty()
        }
    }

    /** The token copy is still made when the creature left the battlefield first: the copy uses its last known
     * information. */
    // CR 608.2h, 707.2
    @Test
    fun offspringCopiesWhatLeft() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest", "Forest")
        alice.hand(bruiser)

        alice.casts(bruiser, kicked = 1)
        allPass()
        move(bruiser, arcanum.api.Zone.Graveyard)
        allPass()
        expect { alice.battlefield.names shouldBe listOf("Forest", "Forest", "Forest", "Forest", "Forest", "Forest", bruiser) }
        val token = card(bruiser, zone = arcanum.api.Zone.Battlefield)
        expect { view[token]?.characteristics?.power shouldBe 1 }
    }
}
