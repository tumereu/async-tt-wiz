package org.asyncttwiz.android

import org.asyncttwiz.protocol.Fault
import org.asyncttwiz.protocol.Faults
import org.asyncttwiz.protocol.MatchEnding
import kotlin.test.Test
import kotlin.test.assertEquals

/** LOB-090 (owner, 2026-11-17: "Report"): the stand-ins still shown silently are reported as errors like every other one. */
class StandInReportsTest {
    private fun reported(block: () -> Unit): List<Fault> {
        val faults = mutableListOf<Fault>()
        val sink = Faults.sink
        Faults.sink = { faults += it }
        try { block() } finally { Faults.sink = sink }
        return faults
    }

    // LOB-090: a player whose name is missing reads "The other player", reported; a named one is no stand-in
    @Test fun aPlayerWithoutANameIsReported() {
        val faults = reported {
            assertEquals("The other player conceded", GameEndText.of("a", MatchEnding.Conceded("b"), emptyMap(), "a", mapOf("a" to "Alice")).reason)
        }
        assertEquals(listOf("GameEndText: player without a name"), faults.map { it.key })
        assertEquals("b", faults.single().details["player"])
        assertEquals("The other player", faults.single().details["shown"])
        assertEquals(emptyList(), reported {
            assertEquals("Bob conceded", GameEndText.of("a", MatchEnding.Conceded("b"), emptyMap(), "a", mapOf("a" to "Alice", "b" to "Bob")).reason)
        })
        val undo = reported { assertEquals("The other player undid", EngineEventText.undid(null, null, "a", "b", emptyMap())) }
        assertEquals(listOf("EngineEventText: player without a name"), undo.map { it.key })
    }

    // LOB-090: a draft card whose card data the pod didn't send reads "Card data unavailable", reported with its digest
    @Test fun aDraftCardWithoutItsCardDataIsReported() {
        val faults = reported { assertEquals("Card data unavailable", cardDataUnavailable("PodGridView", "digest-1")) }
        assertEquals(listOf("PodGridView: draft card without its card data"), faults.map { it.key })
        assertEquals("digest-1", faults.single().details["definition"])
    }
}
