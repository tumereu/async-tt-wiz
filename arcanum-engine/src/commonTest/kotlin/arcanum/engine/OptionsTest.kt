package arcanum.engine

import arcanum.api.CounterKind
import arcanum.api.TurnStep
import arcanum.engine.decision.Answer
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** [Engine.options]: what a player may do now (CR 116, 117.1). */
class OptionsTest {
    private fun Scenario.options(player: arcanum.engine.scenario.ScenarioPlayer) = Engine.options(state, player.id)

    private fun Scenario.casts(player: arcanum.engine.scenario.ScenarioPlayer) =
        options(player).filterIsInstance<Input.Cast>().map { nameOf(state.objOrThrow(it.card)) }

    /** Every listed option is accepted by [Engine.perform] as the game stands (it may open a decision). */
    private fun Scenario.allAccepted(player: arcanum.engine.scenario.ScenarioPlayer) {
        options(player).forEach { input ->
            runCatching { Engine.perform(state, input) }.onFailure { throw AssertionError("$input was listed but refused: ${it.message}", it) }
        }
    }

    // CR 117.1, 305.1, 601.3: with priority in a main phase: pass, play a land, cast what can be paid, activate abilities
    @Test
    fun mainPhaseOptions() = scenario {
        alice.battlefield("Forest", "Lotus Frond")
        alice.hand("Island", "Elvish Seer", "Old Enmity", "Elvish Hierophant")
        val options = options(alice)
        assertTrue(Input.PassPriority(alice.id) in options)
        assertTrue(Input.PlayLand(alice.id, card("Island")) in options)
        assertTrue("Elvish Seer" in casts(alice))
        // {1}{G}{G} can't be paid with a Forest and the Lotus
        assertTrue("Elvish Hierophant" !in casts(alice))
        assertTrue(options.any { it is Input.Activate && it.source == card("Lotus Frond") })
        assertTrue(options.any { it is Input.Activate && it.source == card("Forest") })
        allAccepted(alice)
    }

    // CR 117.1: a player without priority has nothing to do
    @Test
    fun noPriorityNoOptions() = scenario {
        bob.battlefield("Mountain")
        bob.hand("Storm Spark")
        assertTrue(options(bob).isEmpty())
    }

    // CR 307.1, 117.1a: on another player's turn: instants, not sorceries or lands
    @Test
    fun opponentsTurnOptions() = scenario {
        bob.battlefield("Mountain", "Mountain")
        bob.hand("Storm Spark", "Cinder Bolt", "Mountain")
        alice.passes()
        assertEquals(listOf("Storm Spark"), casts(bob))
        assertTrue(options(bob).none { it is Input.PlayLand })
        allAccepted(bob)
    }

    // CR 601.3a, 702.34a: flashback from the graveyard is offered
    @Test
    fun flashbackIsOffered() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.graveyard("Cinder Bolt")
        val cast = options(alice).filterIsInstance<Input.Cast>().single()
        assertEquals(card("Cinder Bolt"), cast.card)
        assertTrue(cast.way.permission != null)
        allAccepted(alice)
    }

    // CR 118.9: an alternative cost whose condition holds is offered as its own way to cast
    @Test
    fun alternativeCostIsOffered() = scenario {
        alice.battlefield("Swamp")
        bob.battlefield("Thicketwood Elves")
        alice.hand("Wink Out")
        val ways = options(alice).filterIsInstance<Input.Cast>().filter { it.card == card("Wink Out") }
        assertEquals(1, ways.size)
        assertTrue(ways.single().way.alternative != null)
        allAccepted(alice)
    }

    // CR 606.3: a loyalty ability isn't offered a second time in a turn
    @Test
    fun loyaltyAbilitiesOncePerTurn() = scenario {
        alice.battlefield("Renji Kagehara", counters = listOf(CounterKind.Loyalty to 3))
        alice.library("Forest", "Forest")
        assertEquals(2, options(alice).count { it is Input.Activate })
        alice.activates("Renji Kagehara", ability = 0)
        allPass()
        assertEquals(0, options(alice).count { it is Input.Activate })
    }

    // D58: with a decision open, its answers and (for a cast) Cancel
    @Test
    fun openDecisionOptions() = scenario {
        alice.battlefield("Mountain")
        bob.battlefield("Elvish Seer")
        alice.hand("Storm Spark")
        perform(Input.Cast(alice.id, card("Storm Spark")))
        val options = options(alice)
        assertTrue(Input.Cancel in options)
        assertEquals(3, options.count { it is Input.Answer })
        // P7: anyone may answer
        assertEquals(options, options(bob))
        allAccepted(alice)
    }

    // §7.8: the mana payment's listed answer is the suggestion
    @Test
    fun paymentOptionIsTheSuggestion() = scenario {
        alice.battlefield("Mountain")
        alice.hand("Storm Spark")
        perform(Input.Cast(alice.id, card("Storm Spark")))
        answer(Answer.Targets(listOf(target("bob"))))
        val answer = options(alice).filterIsInstance<Input.Answer>().single()
        perform(answer)
        expect { stack.names shouldBe listOf("Storm Spark") }
    }

    // CR 509.1: the defending player's options are block declarations
    @Test
    fun blockOptions() = scenario {
        alice.battlefield("Faerie Augur", "Thicketwood Elves")
        bob.battlefield("Elvish Seer")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks("Faerie Augur", "Thicketwood Elves")
        allPass()
        val options = options(bob)
        assertTrue(options.all { it is Input.DeclareBlockers })
        // The Seer can block the Elves but not the flier
        assertEquals(2, options.size)
        assertTrue(options(alice).isEmpty())
        allAccepted(bob)
    }

    // D56: a broad board: everything listed is accepted
    @Test
    fun everythingListedIsAccepted() = scenario {
        alice.battlefield("Forest", "Mountain", "Island", "Swamp", "Plains", "Elvish Seer", "Lotus Frond", "Eden Cloak", "Velis, Mender of the Lost Way")
        alice.hand("Storm Spark", "Grindstone Rasp", "Doomfire", "Flash Surge", "Bone Splinters", "Thicketwood Elves", "Forest", "Hallowed Mire Triad")
        alice.graveyard("Cinder Bolt", "Magma Needle")
        bob.battlefield("Thicketwood Elves", "Lotus Frond")
        allAccepted(alice)
        assertTrue(options(alice).size > 15)
    }
}
