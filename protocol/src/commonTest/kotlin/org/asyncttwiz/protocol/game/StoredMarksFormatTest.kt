package org.asyncttwiz.protocol.game

import arcanum.api.Cost
import arcanum.api.ManaCost
import arcanum.api.ObjectMark
import arcanum.engine.state.MarkGained
import arcanum.engine.state.ObjectFacts
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Stored games hold states in a strict format (I-D54 §4): the generic mark record has its stable form.
 */
class StoredMarksFormatTest {
    private fun facts(text: String) = EngineJson.json.decodeFromString(ObjectFacts.serializer(), text)
    private fun text(facts: ObjectFacts) = EngineJson.json.encodeToString(ObjectFacts.serializer(), facts)

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
