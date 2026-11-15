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
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** CR 103.5 with [StartOptions.simultaneousMulligans] (a host variant): each player's declarations and bottom choices are
 * made in any order ([Input.SetupAnswer]) and taken as soon as they are made, so each player's mulligans are their own. */
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

    // CR 103.5 (host variant): a mulligan is taken as soon as it is declared, without waiting for the other player's
    // declaration; its bottom choice follows at once, and the next declaration after it
    @Test
    fun aMulliganIsTakenAsSoonAsItIsDeclared() = scenario(start = table(swamps, mountains)) {
        random(inOrder, inOrder, cutting(7), cutting(7))
        startGame(StartOptions(startingPlayer = alice.id, simultaneousMulligans = true))
        early(bob, Answer.Option(Setup.MULLIGAN))
        // Taken at once: Bob's new hand, while Alice's declaration is still open
        assertEquals(mountains.drop(7).take(7), handNames(bob))
        assertEquals(swamps.take(7), handNames(alice))
        assertEquals(alice.id, decision<Decision.ChooseOption>().player)
        assertEquals(listOf(GameEvent.Mulliganed(bob.id, 1)), events<GameEvent.Mulliganed>())
        // His bottom choice is his to make ahead of Alice's declaration
        val bottom = Engine.options(state, bob.id).filterIsInstance<Input.SetupAnswer>().single()
        assertEquals(1, (bottom.answer as Answer.Objects).chosen.size)
        perform(bottom)
        expect { bob.hand.size shouldBe 6 }
        // Then his next declaration, still ahead of Alice's
        assertEquals(setOf(Setup.KEEP, Setup.MULLIGAN),
            Engine.options(state, bob.id).filterIsInstance<Input.SetupAnswer>().map { (it.answer as Answer.Option).chosen }.toSet())
        mulligan(alice)
        assertEquals(listOf(GameEvent.Mulliganed(bob.id, 1), GameEvent.Mulliganed(alice.id, 1)), events<GameEvent.Mulliganed>())
        assertEquals(swamps.drop(7).take(7), handNames(alice))
        // Alice's bottom choice is open now (the first player in turn order who hasn't kept)
        assertEquals(alice.id, decision<Decision.ChooseObjects>().player)
        early(bob, Answer.Option(Setup.KEEP))
        bottom(alice, "Swamp")
        keep(alice)
        expect {
            alice.hand.size shouldBe 6
            bob.hand.size shouldBe 6
        }
        assertTrue(state.started)
    }

    // CR 103.5 (host variant): each player's mulligans are their own: one player mulligans twice while the other's
    // declaration stays open; the events come in the order made
    @Test
    fun onePlayerMulligansTwiceWhileTheOtherHasNotDeclared() = scenario(start = table(swamps, mountains)) {
        random(inOrder, inOrder, cutting(7), cutting(7))
        startGame(StartOptions(startingPlayer = alice.id, simultaneousMulligans = true, freeFirstMulligan = true))
        early(bob, Answer.Option(Setup.MULLIGAN))
        // The first is free (CR 103.5c as an option): nothing to the bottom, his next declaration at once
        expect { bob.hand.size shouldBe 7 }
        early(bob, Answer.Option(Setup.MULLIGAN))
        assertEquals(listOf(GameEvent.Mulliganed(bob.id, 1), GameEvent.Mulliganed(bob.id, 2)), events<GameEvent.Mulliganed>())
        perform(Engine.options(state, bob.id).filterIsInstance<Input.SetupAnswer>().single())
        expect { bob.hand.size shouldBe 6 }
        early(bob, Answer.Option(Setup.KEEP))
        assertEquals(alice.id, decision<Decision.ChooseOption>().player)
        expect { alice.hand.size shouldBe 7 }
        keep(alice)
        assertTrue(state.started)
    }

    // CR 103.5 (host variant), with three players: each declaration is taken as made, in any order; turn 1 begins once
    // every player has kept
    @Test
    fun threePlayersEachTakeTheirOwnMulligans() = scenario(players = 3, start = table(swamps, mountains, swamps)) {
        startGame(StartOptions(startingPlayer = alice.id, simultaneousMulligans = true))
        early(carol, Answer.Option(Setup.MULLIGAN))
        // CR 103.5c: the first mulligan is free in multiplayer
        expect { carol.hand.size shouldBe 7 }
        early(bob, Answer.Option(Setup.KEEP))
        early(carol, Answer.Option(Setup.MULLIGAN))
        // Carol owes one card; Alice's declaration is still the open one
        assertEquals(alice.id, decision<Decision.ChooseOption>().player)
        perform(Engine.options(state, carol.id).filterIsInstance<Input.SetupAnswer>().single())
        expect { carol.hand.size shouldBe 6 }
        keep(alice)
        // Carol's declaration is the open one now; Bob has kept and declares nothing more
        assertEquals(carol.id, decision<Decision.ChooseOption>().player)
        assertTrue(Engine.options(state, bob.id).none { it is Input.SetupAnswer })
        assertTrue(!state.started)
        keep(carol)
        assertEquals(listOf(GameEvent.Mulliganed(carol.id, 1), GameEvent.Mulliganed(carol.id, 2)), events<GameEvent.Mulliganed>())
        assertEquals(listOf(GameEvent.Kept(bob.id, 7), GameEvent.Kept(alice.id, 7), GameEvent.Kept(carol.id, 6)), events<GameEvent.Kept>())
        assertTrue(state.started)
    }

    // CR 103.5: without the variant, declarations go in turn order only
    @Test
    fun withoutTheVariantDeclarationsGoInTurnOrder() = scenario(start = table(swamps, mountains)) {
        startGame(StartOptions(startingPlayer = alice.id))
        assertFailsWith<IllegalArgumentException> { Engine.perform(state, Input.SetupAnswer(bob.id, Answer.Option(Setup.KEEP))) }
        assertTrue(Engine.options(state, bob.id).none { it is Input.SetupAnswer })
    }

    // CR 103.5: a player who kept declares no more, and puts on the bottom exactly as many cards as their mulligans count
    @Test
    fun earlyAnswersFollowThePlayersOwnProcess() = scenario(start = table(swamps, mountains)) {
        startGame(StartOptions(startingPlayer = alice.id, simultaneousMulligans = true))
        early(bob, Answer.Option(Setup.MULLIGAN))
        // He owes one card: no declaration before it, and not two cards
        assertFailsWith<IllegalArgumentException> { Engine.perform(state, Input.SetupAnswer(bob.id, Answer.Option(Setup.KEEP))) }
        assertFailsWith<IllegalArgumentException> {
            Engine.perform(state, Input.SetupAnswer(bob.id, Answer.Objects(state.player(bob.id).hand.take(2))))
        }
        keep(alice)
        // His bottom choice is the open decision once Alice kept
        assertEquals(bob.id, decision<Decision.ChooseObjects>().player)
        bottom(bob, handNames(bob).last()!!)
        keep(bob)
        assertFails { Engine.perform(state, Input.SetupAnswer(alice.id, Answer.Option(Setup.KEEP))) }
        assertTrue(state.started)
    }
}
