package arcanum.engine.change

import arcanum.engine.Pending
import arcanum.engine.scenario.StateDiff
import arcanum.engine.state.GameState
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/** D59: every change is reversible; apply then revert gives back the state exactly (the timestamp counter aside, D34). */
class ChangeRevertTest {
    private val samples = ChangeSamples.all().second

    @Test
    fun applyThenRevertRestoresTheStateForEveryChangeKind() {
        samples.forEach { (state, change) ->
            val copy = state.deepCopy()
            copy.apply(change)
            // Change.Shown only says what a player looked at: it changes nothing by design
            if (change !is Change.Shown) assertNotEquals(state, copy, "${change::class.simpleName} changed nothing")
            copy.revert(change)
            copy._timestampCounter = state.timestampCounter
            if (copy != state) fail("${change::class.simpleName} didn't revert exactly:\n${StateDiff.between(state, copy)}")
        }
    }

    @Test
    fun applyingTheInverseUndoesTheStateChange() {
        samples.filter { it.second.isEvent }.forEach { (state, change) ->
            val copy = state.deepCopy()
            copy.apply(change)
            copy.apply(change.inverse())
            copy._timestampCounter = state.timestampCounter
            // Applying the inverse logs it as a new event; the state otherwise matches.
            copy._log = state.log.toMutableList()
            assertEquals(state, copy, "${change::class.simpleName}.inverse()")
        }
    }

    // D34: the timestamp counter never goes back, also not on revert.
    @Test
    fun revertKeepsTheTimestampCounter() {
        val (state, change) = samples.first { it.second is Change.ZoneChange }
        val copy = state.deepCopy()
        copy.apply(change)
        val raised = copy.timestampCounter
        assertTrue(raised > state.timestampCounter)
        copy.revert(change)
        assertEquals(raised, copy.timestampCounter)
    }

    // D33: events are logged with their turn; bookkeeping isn't.
    @Test
    fun eventsAreLoggedAndBookkeepingIsNot() {
        samples.forEach { (state, change) ->
            val copy = state.deepCopy()
            copy.apply(change)
            val logged = copy.log.size - state.log.size
            val expected = when {
                change is Change.Reverted -> -1
                change.isEvent -> 1
                else -> 0
            }
            assertEquals(expected, logged, "${change::class.simpleName} logged $logged entries")
        }
    }

    @Test
    fun aChangeMadeForAnotherStateIsRefused() {
        val (state, change) = samples.first { it.second is Change.Life }
        val copy = state.deepCopy()
        copy.apply(change)
        assertFailsWith<IllegalStateException> { copy.apply(change) }
    }

    @Test
    fun statesAndChangesRoundTripThroughJson() {
        val json = Json
        samples.forEach { (state, change) ->
            val text = json.encodeToString(Change.serializer(), change)
            assertEquals(change, json.decodeFromString(Change.serializer(), text))
            val after = state.deepCopy().also { it.apply(change) }
            assertEquals(after, json.decodeFromString(GameState.serializer(), json.encodeToString(GameState.serializer(), after)))
        }
        val pending = samples.mapNotNull { (it.second as? Change.PendingSet)?.after }.single()
        assertEquals(pending, json.decodeFromString(Pending.serializer(), json.encodeToString(Pending.serializer(), pending)))
    }
}
