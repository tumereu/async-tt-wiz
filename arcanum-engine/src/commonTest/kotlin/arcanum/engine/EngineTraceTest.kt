package arcanum.engine

import arcanum.engine.cards.Cards
import arcanum.engine.decision.LoopStop
import arcanum.engine.input.Input
import arcanum.engine.scenario.scenario
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** [EngineTrace]: one entry per `perform` call, for a host's logs and bug reports (I-D43). */
class EngineTraceTest {
    private val bolt = Cards.invent("Trace Spark", "{R}", "Instant", "This spell deals 2 damage to any target.")

    @Test
    fun anAcceptedCallIsRecordedWithItsShape() = scenario {
        turn()
        alice.battlefield("Mountain")
        alice.hand(bolt)
        val entries = mutableListOf<TraceEntry>()
        val input = Engine.options(state, alice.id).filterIsInstance<Input.Cast>().first()
        val outcome = Engine.perform(state, input) { entries += it }
        val entry = assertIs<TraceEntry.Performed>(entries.single())
        assertEquals(input, entry.input)
        assertEquals(outcome.changes.size, entry.changes)
        assertEquals(outcome.pending?.decision?.id, entry.pending?.id, "the cast waits on its first decision")
        assertEquals(null as LoopStop?, entry.loopStop)
        assertTrue(entry.micros >= 0)
        // Entries go into reports as JSON and come back the same
        val json = Json.encodeToString(TraceEntry.serializer(), entry)
        assertEquals(entry, Json.decodeFromString(TraceEntry.serializer(), json))
    }

    @Test
    fun aRefusedCallIsRecordedWithItsReasonAndStillThrows() = scenario {
        turn()
        val entries = mutableListOf<TraceEntry>()
        val input = Input.PassPriority(bob.id)
        assertFailsWith<IllegalArgumentException> { Engine.perform(state, input) { entries += it } }
        val entry = assertIs<TraceEntry.Refused>(entries.single())
        assertEquals(input, entry.input)
        assertTrue(entry.reason.isNotBlank())
        assertEquals("IllegalArgumentException", entry.error)
    }

    @Test
    fun theDefaultRecordsNothing() = scenario {
        turn()
        // EngineTrace.None is the default; a call without a trace behaves as before
        val outcome = Engine.perform(state, Input.PassPriority(alice.id))
        assertTrue(outcome.changes.isNotEmpty())
    }
}
