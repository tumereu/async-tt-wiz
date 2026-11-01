package arcanum.engine.scenario

import arcanum.api.CounterKind
import arcanum.api.Zone
import arcanum.engine.change.Changes
import arcanum.engine.change.Meaning
import arcanum.engine.state.TargetRef
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Sample scenarios of the harness with what part 1 of the engine does: the view, and changes made by hand (D42). Each
 * scenario also checks D59 at its end: reverting its changes gives back its start.
 */
class SampleScenarioTest {
    // CR 613.4c, 400.7: the anthem stops when its source leaves the battlefield; the moved card is a new object
    @Test
    fun anthemEndsWhenItsSourceDies() = scenario {
        alice.battlefield("Elvish Hierophant", "Elvish Seer")
        val before = card("Elvish Hierophant")

        move("Elvish Hierophant", to = Zone.Graveyard)

        expect {
            "Elvish Seer".power shouldBe 1
            alice.graveyard has "Elvish Hierophant"
            alice.battlefield lacks "Elvish Hierophant"
            // CR 700.4: put into a graveyard from the battlefield is "dies"
            log mentions "Died"
        }
        assertTrue(card("Elvish Hierophant") != before)
    }

    // CR 122.1a, 613.4c: counters put on by hand count in layer 7c; taking them off undoes it
    @Test
    fun countersByHand() = scenario {
        alice.battlefield("Elvish Seer")

        change(Changes.counters(state, TargetRef.Object(card("Elvish Seer")), CounterKind.PowerToughness(1, 1), 2, listOf(Meaning.Manual)))
        change(Changes.tap(state, card("Elvish Seer")))
        change(Changes.life(state, bob.id, -3, listOf(Meaning.Manual)))

        expect {
            "Elvish Seer".power shouldBe 3
            "Elvish Seer".tapped shouldBe true
            bob.life shouldBe 17
        }
    }

    // D59: the harness fails a scenario whose changes don't revert to its start
    @Test
    fun harnessCatchesAnIrreversibleChange() {
        assertFailsWith<AssertionError> {
            scenario {
                alice.battlefield("Elvish Seer")
                change(Changes.tap(state, card("Elvish Seer")))
                // A change outside the engine's changes: not recorded, so reverting can't restore the start.
                state.updatePlayer(bob.id) { it.copy(life = 3) }
            }
        }
    }

    // D61: N players in turn order from the start (CR 101.4)
    @Test
    fun fourPlayers() = scenario(players = 4) {
        turn(2, active = bob)
        dave.battlefield("Elvish Hierophant", "Elvish Seer")
        carol.battlefield("Elvish Seer")
        expect {
            card("Elvish Seer", 0).power shouldBe 2
            card("Elvish Seer", 1).power shouldBe 1
        }
        assertTrue(state.apnap().map { it.value } == listOf("bob", "carol", "dave", "alice"))
    }
}
