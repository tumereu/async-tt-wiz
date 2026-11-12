package arcanum.engine.change

import arcanum.api.TurnStep
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

/** Undo (D59) and the event log the host may trim ([arcanum.engine.state.GameState.forgetOlderTurns], D33). */
class LogTrimTest {
    /** After the host drops older turns' events, every change can still be taken back, into those turns too. */
    // D33, D59
    @Test
    fun undoReachesTurnsWhoseEventsWereDropped() = scenario {
        alice.battlefield("Mountain")
        alice.hand("Storm Spark")
        alice.library("Island", "Island", "Island")
        bob.library("Island", "Island", "Island")
        val start = state.deepCopy()
        alice.casts("Storm Spark", target = "bob")
        allPass()
        advanceTo(TurnStep.Upkeep, turn = 3)
        val trimmed = state.deepCopy().also { it.forgetOlderTurns() }
        assertTrue(trimmed.log.size < state.log.size && trimmed.log.isNotEmpty())
        applied.asReversed().forEach { trimmed.revert(it) }
        trimmed.rewindTimestampCounter(start.timestampCounter)
        assertEquals(start, trimmed)
    }

    /** A host that takes back what it did after dropping older turns' events puts them back
     * ([arcanum.engine.state.GameState.rememberOlderTurns]): the log is as before the drop. */
    // D33
    @Test
    fun droppedEventsCanBePutBack() = scenario {
        alice.battlefield("Mountain")
        alice.hand("Storm Spark")
        alice.library("Island", "Island", "Island")
        bob.library("Island", "Island", "Island")
        alice.casts("Storm Spark", target = "bob")
        allPass()
        advanceTo(TurnStep.Upkeep, turn = 3)
        val before = state.deepCopy()
        val dropped = before.log.takeWhile { it.turn < before.turn.number - 1 }
        val trimmed = state.deepCopy().also { it.forgetOlderTurns() }
        assertTrue(dropped.isNotEmpty())
        trimmed.rememberOlderTurns(dropped)
        assertEquals(before, trimmed)
    }

    /** While the log still holds events, a change is taken back only as the last one (last first, D59). */
    // D59
    @Test
    fun undoStaysInOrder() = scenario {
        alice.battlefield("Mountain")
        alice.hand("Storm Spark")
        alice.casts("Storm Spark", target = "bob")
        allPass()
        val firstEvent = applied.first { it.isEvent }
        assertFails { state.deepCopy().revert(firstEvent) }
    }
}
