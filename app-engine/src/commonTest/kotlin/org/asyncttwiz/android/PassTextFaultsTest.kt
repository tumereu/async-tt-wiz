package org.asyncttwiz.android

import arcanum.api.TurnStep
import arcanum.engine.change.Change
import arcanum.engine.input.Input
import arcanum.engine.state.PlayerId
import arcanum.engine.state.TurnState
import org.asyncttwiz.protocol.Fault
import org.asyncttwiz.protocol.Faults
import org.asyncttwiz.protocol.game.EnginePart
import org.asyncttwiz.protocol.game.EngineStep
import org.asyncttwiz.protocol.game.GameEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** §29.2: whether a pass shows anywhere is worked out without its words, which name players (LOB-090). */
class PassTextFaultsTest {
    private val alice = PlayerId("alice")
    private val bob = PlayerId("bob")
    private val names = mapOf("alice" to "Alice", "bob" to "Bob")

    private fun pass(by: PlayerId, vararg changes: Change) =
        GameEvent("e", "p", by.value, EnginePart(0, listOf(EngineStep(Input.PassPriority(by), changes.toList()))))

    private fun reported(block: () -> Unit): List<Fault> {
        val faults = mutableListOf<Fault>()
        val sink = Faults.sink
        Faults.sink = { faults += it }
        try { block() } finally { Faults.sink = sink }
        return faults
    }

    // LOB-090 (prod faults DBNFDN, GBWJEV, NYH934, 4RQSYT: "EngineEventText: player without a name; named=; shown=the other
    // player" at every turn passed to): a pass leading to a new turn is not hidden, and finding that out names nobody
    @Test fun aPassToANewTurnIsShownWithoutAStandInReported() {
        val newTurn = pass(bob, Change.Step(TurnState(3, bob, TurnStep.Cleanup), TurnState(4, alice, TurnStep.Untap)))
        val faults = reported {
            assertFalse(EngineEventText.hidden(newTurn))
            assertEquals("Turn 4 → you", EngineEventText.of(newTurn, null, "alice", names))
            assertEquals("Turn 4 → Alice", EngineEventText.of(newTurn, null, "bob", names))
        }
        assertEquals(emptyList(), faults)
    }

    // §29.2: a pass that led to a new step is shown; one that led to nothing is hidden
    @Test fun aPassThatLedNowhereIsHidden() {
        val newStep = pass(alice, Change.Step(TurnState(4, alice, TurnStep.Upkeep), TurnState(4, alice, TurnStep.Draw)))
        val faults = reported {
            assertFalse(EngineEventText.hidden(newStep))
            assertTrue(EngineEventText.hidden(pass(alice)))
        }
        assertEquals(emptyList(), faults)
    }
}
