package org.asyncttwiz.protocol.game

import arcanum.api.Cost
import arcanum.api.ManaCost
import arcanum.api.ObjectMark
import arcanum.engine.state.MarkGained
import arcanum.engine.state.ObjectFacts
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Stored games hold states in a strict format (I-D54 §4). The turn fields the generic mark record replaces must keep reading exactly as
 * written until the one approved wipe, and the generic record has its own stable form.
 */
class StoredMarksFormatTest {
    private fun facts(text: String) = EngineJson.json.decodeFromString(ObjectFacts.serializer(), text)
    private fun text(facts: ObjectFacts) = EngineJson.json.encodeToString(ObjectFacts.serializer(), facts)

    @Test
    fun theOldTurnFieldsStillRead() {
        val foretold = """{"foretoldTurn":3}"""
        assertEquals(ObjectFacts(foretoldTurn = 3), facts(foretold))
        assertEquals(foretold, text(facts(foretold)))
        assertEquals(MarkGained(ObjectMark.Foretold, 3), facts(foretold).gained(ObjectMark.Foretold))
        val plotted = """{"plottedTurn":5}"""
        assertEquals(ObjectFacts(plottedTurn = 5), facts(plotted))
        assertEquals(plotted, text(facts(plotted)))
        assertEquals(MarkGained(ObjectMark.Plotted, 5), facts(plotted).gained(ObjectMark.Plotted))
    }

    @Test
    fun theGenericRecordHasItsForm() {
        val cost = Cost.mana(ManaCost.parse("{1}")!!)
        val record = ObjectFacts().gaining(MarkGained(ObjectMark.Foretold, 3, listOf(cost)))
        val written = text(record)
        assertEquals(record, facts(written))
        assertEquals(MarkGained(ObjectMark.Foretold, 3, listOf(cost)), facts(written).gained(ObjectMark.Foretold))
        assertEquals("""{"marksGained":[{"mark":{"type":"plotted"},"turn":7}]}""", text(ObjectFacts().gaining(MarkGained(ObjectMark.Plotted, 7))))
    }

    // Each object keeps one record per mark: the latest
    @Test
    fun theLatestRecordOfAMarkCounts() {
        val facts = ObjectFacts().gaining(MarkGained(ObjectMark.Plotted, 2)).gaining(MarkGained(ObjectMark.Plotted, 4))
        assertEquals(listOf(MarkGained(ObjectMark.Plotted, 4)), facts.marksGained)
    }
}
