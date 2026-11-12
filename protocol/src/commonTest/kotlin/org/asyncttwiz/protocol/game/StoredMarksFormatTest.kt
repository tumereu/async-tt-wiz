package org.asyncttwiz.protocol.game

import arcanum.api.Cost
import arcanum.api.ManaCost
import arcanum.api.ObjectMark
import arcanum.engine.state.MarkGained
import arcanum.engine.state.ObjectStatus
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Stored games hold states in a strict format (I-D54 §4): the generic mark record has its stable form.
 */
class StoredMarksFormatTest {
    private fun status(text: String) = EngineJson.json.decodeFromString(ObjectStatus.serializer(), text)
    private fun text(status: ObjectStatus) = EngineJson.json.encodeToString(ObjectStatus.serializer(), status)

    @Test
    fun theGenericRecordHasItsForm() {
        val cost = Cost.mana(ManaCost.parse("{1}")!!)
        val record: ObjectStatus = ObjectStatus.MarksGained(listOf(MarkGained(ObjectMark.Foretold, 3, listOf(cost))))
        val written = text(record)
        assertEquals(record, status(written))
        assertEquals("""{"type":"marks-gained","records":[{"mark":{"type":"plotted"},"turn":7}]}""",
            text(ObjectStatus.MarksGained(listOf(MarkGained(ObjectMark.Plotted, 7)))))
    }
}
