package arcanum.engine.review

import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.change.Change
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.CastWay
import arcanum.engine.input.Input
import arcanum.engine.scenario.scenario
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Review 3: one player's view (D60) following the other player's game, as a host that keeps each player's state
 * separately must ([FakeHost]): plays and casts from a hand the viewer can't see, effects that move many hidden cards,
 * and a look only the other player may see.
 */
class Review3ViewsTest {
    /** In alice's view bob casts Storm Spark from his hand: the card becomes public as it goes on the Stack (CR 601.2a),
     * so the view asks the host to fill it (public) and goes on; bob is offered the cast in alice's view as in his. */
    // CR 601.2a, 400.2, D60
    @Ignore("review3 #4: a viewer's state refuses the other player's cast from a hidden hand (\"unknown card\")")
    @Test
    fun theOpponentCastsFromAHiddenHandInTheViewersState() = scenario {
        viewer(alice)
        bob.battlefield("Mountain")
        bob.hand("Storm Spark")
        alice.passes()
        assertTrue(castOptions(bob, "Storm Spark").isNotEmpty(), "bob's cast is offered in alice's view")
        bob.casts("Storm Spark", target = "alice")
        allPass()
        expect {
            alice.life shouldBe 17
            bob.graveyard has "Storm Spark"
        }
        assertTrue(hostFillsAsked().single().public)
    }

    /** In alice's view bob plays a land from his hidden hand: it goes onto the battlefield face up, filled for everyone. */
    // CR 305.1, 400.2, D60
    @Ignore("review3 #4: a viewer's state refuses the other player's land play from a hidden hand (\"unknown card\")")
    @Test
    fun theOpponentPlaysALandFromAHiddenHandInTheViewersState() = scenario {
        viewer(alice)
        turn(number = 2, active = bob)
        bob.hand("Forest")
        bob.plays("Forest")
        expect { bob.battlefield has "Forest" }
        assertEquals(false, state.objOrThrow(card("Forest")).unknown)
    }

    /** Echo of Ages in alice's view: both hands and graveyards are shuffled in (new ids), seven cards drawn each; alice's
     * draws are filled for her, bob's stay unknown. */
    // CR 701.24a, 121.1, D60
    @Test
    fun echoOfAgesInTheCastersView() = scenario {
        viewer(alice)
        alice.battlefield("Island", "Island", "Island", "Island", "Island", "Island")
        alice.hand("Echo of Ages", "Forest")
        alice.library("Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        bob.hand("Mountain", "Storm Spark")
        bob.graveyard("Denial")
        bob.library("Plains", "Plains", "Plains", "Plains", "Plains", "Plains")
        alice.casts("Echo of Ages")
        allPass()
        val aliceHand = state.player(alice.id).hand.map { state.objOrThrow(it) }
        val bobHand = state.player(bob.id).hand.map { state.objOrThrow(it) }
        assertEquals(7, aliceHand.size)
        assertEquals(7, bobHand.size)
        assertTrue(aliceHand.none { it.unknown }, "alice knows her new hand")
        assertTrue(bobHand.all { it.unknown }, "alice doesn't know bob's new hand")
        expect { bob.graveyard.size shouldBe 0 }
    }

    /** Spinning Fate in alice's view: bob's hidden hand is discarded, every card filled publicly as it goes to the
     * graveyard (one fill), then each player draws seven. */
    // CR 701.9a, 400.2, D60
    @Test
    fun aDiscardedHiddenHandIsFilledInOnce() = scenario {
        viewer(alice)
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.hand("Spinning Fate")
        alice.library("Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        bob.hand("Mountain", "Storm Spark", "Denial")
        bob.library("Plains", "Plains", "Plains", "Plains", "Plains", "Plains", "Plains")
        alice.casts("Spinning Fate")
        allPass()
        expect {
            bob.graveyard has "Storm Spark"
            bob.graveyard has "Denial"
        }
        assertTrue(state.player(bob.id).graveyard.none { state.objOrThrow(it).unknown })
        val bobsFills = hostFillsAsked().filter { it.public }
        assertEquals(1, bobsFills.size, "one public fill for the discarded hand: ${hostFillsAsked()}")
    }

    /** bob's Shadow Informant in alice's view: the revealed top card is filled publicly and bob loses life equal to its
     * mana value. */
    // CR 701.20a, D60
    @Test
    fun anOpponentsRevealInTheViewersState() = scenario {
        viewer(alice)
        bob.battlefield("Shadow Informant")
        bob.library("Flash Surge", "Plains")
        beginStep(TurnStep.Upkeep, active = bob)
        resolve()
        expect {
            bob.life shouldBe 19
            bob.hand.size shouldBe 1
        }
        assertTrue(hostFillsAsked().single().public)
    }

    /** In bob's view, alice's Oracle's Spinning Top looks at her top three cards: only alice may see them (D60). bob's
     * state never learns their identities: no host fill for alice's eyes is answered in bob's view, and bob's outcome
     * carries none of those cards known. A host with one state per player has no other way on than answering it (D31's
     * "done in that player's view" has no API to bring the result into bob's state). */
    // D60, D31, CR 701.16a
    @Ignore("review3 #5: a look only the other player may see can be completed in a view only by filling it there")
    @Test
    fun aLookByTheOtherPlayerLeavesTheViewIgnorant() = scenario {
        viewer(bob)
        alice.battlefield("Oracle's Spinning Top", "Forest")
        alice.library("Storm Spark", "Denial", "Forest", "Island", "Plains")
        alice.activates("Oracle's Spinning Top", ability = 0)
        allPass()
        val private = hostFillsAsked().filter { !it.public && it.player == alice.id }
        assertTrue(private.isEmpty(), "bob's view asked to fill ${private.flatMap { it.objects }.size} of alice's cards for her")
        val known = applied.filterIsInstance<Change.Filled>().filter { it.after.owner == alice.id && it.after.zone == Zone.Library }
        assertTrue(known.isEmpty(), "bob's state learned ${known.map { it.after.definition?.faces?.first()?.name }}")
    }

    /** The other player's cards can't be cast in a view even when the host knows what they are: the input names a card
     * the view only knows by id, and the engine refuses it rather than asking for it. (The host workaround: apply a
     * hand-made [Change.Filled] first.) */
    // D60
    @Ignore("review3 #4: a viewer's state refuses the other player's cast from a hidden hand (\"unknown card\")")
    @Test
    fun theOpponentsCastIsAcceptedAsInput() = scenario {
        viewer(alice)
        bob.battlefield("Mountain")
        bob.hand("Storm Spark")
        alice.passes()
        assertEquals(false, refuses(Input.Cast(bob.id, card("Storm Spark"), CastWay())))
    }
}
