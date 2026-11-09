package org.asyncttwiz.protocol.game

import arcanum.api.KeywordAction
import arcanum.engine.decision.Decision
import arcanum.engine.decision.DecisionId
import arcanum.engine.state.PlayerId
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Stored games hold states and events in a strict format (I-D54 §4). Special actions running through the step runner only adds to it: a
 * stored [Decision.Manual] still reads as written, and foretelling as a keyword action has its own stable form.
 */
class StoredSpecialActionsFormatTest {
    @Test
    fun aStoredManualDecisionStillReads() {
        val stored = """{"type":"manual","id":"d1","player":"alice","source":null,"text":"Name a planet"}"""
        val decision = EngineJson.json.decodeFromString(Decision.serializer(), stored)
        assertEquals(Decision.Manual(DecisionId("d1"), PlayerId("alice"), null, "Name a planet"), decision)
    }

    @Test
    fun foretellingHasItsForm() {
        val written = EngineJson.json.encodeToString(KeywordAction.serializer(), KeywordAction.Foretell)
        assertEquals("""{"type":"foretell"}""", written)
        assertEquals(KeywordAction.Foretell, EngineJson.json.decodeFromString(KeywordAction.serializer(), written))
    }
}
