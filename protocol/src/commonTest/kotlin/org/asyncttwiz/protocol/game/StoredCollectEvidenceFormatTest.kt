package org.asyncttwiz.protocol.game

import arcanum.api.NumericProperty
import arcanum.api.Total
import arcanum.api.Value
import arcanum.engine.decision.Decision
import arcanum.engine.decision.DecisionId
import arcanum.engine.state.PlayerId
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Stored games hold states and events in a strict format (I-D54 §4). Collecting evidence as one selection (CR 701.59a) only adds to it:
 * [Decision.ChooseObjects] has an optional total, and a choice without one is written and read as before.
 */
class StoredCollectEvidenceFormatTest {
    private fun choice(total: Total?) = Decision.ChooseObjects(DecisionId("d1"), PlayerId("alice"), null, emptyList(), 1, null, "Exile", total)

    @Test
    fun aChoiceWithoutATotalIsWrittenAndReadAsBefore() {
        val stored = """{"type":"objects","id":"d1","player":"alice","source":null,"options":[],"min":1,"max":null,"text":"Exile"}"""
        assertEquals(choice(null), EngineJson.json.decodeFromString(Decision.serializer(), stored))
        assertEquals(stored, EngineJson.json.encodeToString(Decision.serializer(), choice(null)))
    }

    @Test
    fun aTotalHasItsForm() {
        val written = """{"type":"objects","id":"d1","player":"alice","source":null,"options":[],"min":1,"max":null,"text":"Exile",""" +
            """"total":{"property":{"type":"mana-value"},"atLeast":{"type":"fixed","value":4}}}"""
        val decision = choice(Total(NumericProperty.ManaValue, Value.Fixed(4)))
        assertEquals(written, EngineJson.json.encodeToString(Decision.serializer(), decision))
        assertEquals(decision, EngineJson.json.decodeFromString(Decision.serializer(), written))
    }
}
