package arcanum.engine.setup

import arcanum.api.TurnStep
import arcanum.engine.Engine
import arcanum.engine.change.Change
import arcanum.engine.change.GameEvent
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.ScenarioPlayer
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** CR 103.5 with [StartOptions.simultaneousMulligans]: declarations and bottom choices made in any order
 * ([Input.SetupAnswer]), the mulligans still taken at the same time. */
class SimultaneousMulligansTest {
    private val swamps = cards(listOf("Swamp", "Swamp", "Undead Soothsayer", "Undead Soothsayer", "Swamp", "Undead Soothsayer", "Swamp"),
        "Swamp", "Undead Soothsayer")
    private val mountains = cards(listOf("Mountain", "Glint-Tusk Corsair", "Mountain", "Mountain", "Glint-Tusk Corsair", "Mountain", "Mountain"),
        "Mountain", "Glint-Tusk Corsair")

    private inline fun <reified T : GameEvent> Scenario.events(): List<T> =
        applied.filterIsInstance<Change.Happened>().map { it.event }.filterIsInstance<T>()

    private fun Scenario.early(player: ScenarioPlayer, answer: Answer) = perform(Input.SetupAnswer(player.id, answer))

    // CR 103.5 (host variant): the second player in turn order declares first; the starting player's declaration stays open
    @Test
    fun theSecondPlayerMayDeclareFirst() = scenario(start = table(swamps, mountains)) {
        startGame(StartOptions(startingPlayer = alice.id, simultaneousMulligans = true))
        assertEquals(alice.id, decision<Decision.ChooseOption>().player)
        assertTrue(Input.SetupAnswer(bob.id, Answer.Option(Setup.KEEP)) in Engine.options(state, bob.id))
        early(bob, Answer.Option(Setup.KEEP))
        // Alice's declaration is asked again, and Bob has nothing more to declare
        assertEquals(alice.id, decision<Decision.ChooseOption>().player)
        assertTrue(Engine.options(state, bob.id).none { it is Input.SetupAnswer })
        keep(alice)
        assertEquals(listOf(GameEvent.Kept(bob.id, 7), GameEvent.Kept(alice.id, 7)), events<GameEvent.Kept>())
        assertEquals(TurnStep.Upkeep, state.turn.step)
    }

    // CR 103.5: mulligans declared in any order are still taken at the same time; bottom choices are made in any order
    @Test
    fun mulligansDeclaredInAnyOrderAreTakenTogether() = scenario(start = table(swamps, mountains)) {
        random(inOrder, inOrder, cutting(7), cutting(7))
        startGame(StartOptions(startingPlayer = alice.id, simultaneousMulligans = true))
        early(bob, Answer.Option(Setup.MULLIGAN))
        // Nothing is taken before Alice has declared
        expect { bob.hand.size shouldBe 7 }
        assertEquals(mountains.take(7), handNames(bob))
        mulligan(alice)
        assertEquals(swamps.drop(7).take(7), handNames(alice))
        assertEquals(mountains.drop(7).take(7), handNames(bob))
        // Alice's bottom choice is open; Bob makes his first
        assertEquals(alice.id, decision<Decision.ChooseObjects>().player)
        perform(Engine.options(state, bob.id).filterIsInstance<Input.SetupAnswer>().single())
        bottom(alice, "Swamp")
        // The second round: again in either order
        early(bob, Answer.Option(Setup.KEEP))
        keep(alice)
        expect {
            alice.hand.size shouldBe 6
            bob.hand.size shouldBe 6
        }
        assertTrue(state.started)
    }

    // CR 103.5: without the variant, declarations go in turn order only
    @Test
    fun withoutTheVariantDeclarationsGoInTurnOrder() = scenario(start = table(swamps, mountains)) {
        startGame(StartOptions(startingPlayer = alice.id))
        assertFailsWith<IllegalArgumentException> { Engine.perform(state, Input.SetupAnswer(bob.id, Answer.Option(Setup.KEEP))) }
        assertTrue(Engine.options(state, bob.id).none { it is Input.SetupAnswer })
    }

    // CR 103.5: a player declares once a round and puts as many cards on the bottom as mulligans taken
    @Test
    fun earlyAnswersFollowTheRound() = scenario(start = table(swamps, mountains)) {
        startGame(StartOptions(startingPlayer = alice.id, simultaneousMulligans = true))
        early(bob, Answer.Option(Setup.MULLIGAN))
        assertFailsWith<IllegalArgumentException> { Engine.perform(state, Input.SetupAnswer(bob.id, Answer.Option(Setup.KEEP))) }
        assertFailsWith<IllegalArgumentException> { Engine.perform(state, Input.SetupAnswer(bob.id, Answer.Objects(emptyList()))) }
        keep(alice)
        assertEquals(bob.id, decision<Decision.ChooseObjects>().player)
        assertFailsWith<IllegalArgumentException> {
            Engine.perform(state, Input.SetupAnswer(bob.id, Answer.Objects(state.player(bob.id).hand.take(2))))
        }
    }
}
