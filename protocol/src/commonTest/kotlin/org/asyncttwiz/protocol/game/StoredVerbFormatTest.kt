package org.asyncttwiz.protocol.game

import arcanum.api.EventPattern
import arcanum.api.KeywordActionKind
import arcanum.api.ObjectFilter
import arcanum.api.ObjectMark
import arcanum.api.PlayerFilter
import arcanum.engine.change.GameEvent
import arcanum.engine.state.PlayerId
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Stored games hold definitions and events in a strict format (I-D54 §4): the general patterns and events of keyword actions and marks have
 * their stable form.
 */
class StoredVerbFormatTest {
    private val alice = PlayerId("alice")

    private fun pattern(text: String) = EngineJson.json.decodeFromString(EventPattern.serializer(), text)
    private fun event(text: String) = EngineJson.json.decodeFromString(GameEvent.serializer(), text)

    private fun assertPattern(expected: EventPattern, text: String) {
        assertEquals(expected, pattern(text))
        assertEquals(text, EngineJson.json.encodeToString(EventPattern.serializer(), expected))
    }

    private fun assertEvent(expected: GameEvent, text: String) {
        assertEquals(expected, event(text))
        assertEquals(text, EngineJson.json.encodeToString(GameEvent.serializer(), expected))
    }

    @Test
    fun theGeneralOnesHaveTheirForm() {
        assertPattern(EventPattern.KeywordActionDone(KeywordActionKind.Explore, objects = ObjectFilter.Any),
            """{"type":"keyword-action-done","action":{"type":"explore"},"objects":{"type":"any"}}""")
        assertPattern(EventPattern.KeywordActionDone(KeywordActionKind.Scry, PlayerFilter.You),
            """{"type":"keyword-action-done","action":{"type":"scry"},"players":{"type":"you"}}""")
        assertPattern(EventPattern.GotMark(ObjectMark.Monstrous, ObjectFilter.Any),
            """{"type":"got-mark","mark":{"type":"monstrous"},"objects":{"type":"any"}}""")
        assertEvent(GameEvent.KeywordActionDone(KeywordActionKind.Scry, alice, amount = 2),
            """{"type":"keyword-action-done","action":{"type":"scry"},"player":"alice","amount":2}""")
    }
}
