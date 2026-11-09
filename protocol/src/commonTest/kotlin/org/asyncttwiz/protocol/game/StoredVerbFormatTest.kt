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
 * Stored games hold definitions and events in a strict format (I-D54 §4). The per-verb patterns and events that general ones replaced must
 * keep reading exactly as written until the one approved wipe, and the general ones have their own stable form.
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
    fun theOldPatternsStillRead() {
        assertPattern(EventPattern.Scry(PlayerFilter.You), """{"type":"scry","players":{"type":"you"}}""")
        assertPattern(EventPattern.Surveil(PlayerFilter.You), """{"type":"surveil","players":{"type":"you"}}""")
        assertPattern(EventPattern.Forage(PlayerFilter.You), """{"type":"forage","players":{"type":"you"}}""")
        assertPattern(EventPattern.GiveGift(PlayerFilter.You), """{"type":"give-gift","players":{"type":"you"}}""")
        assertPattern(EventPattern.Airbend(PlayerFilter.You), """{"type":"airbend","players":{"type":"you"}}""")
        assertPattern(EventPattern.Earthbend(PlayerFilter.You), """{"type":"earthbend","players":{"type":"you"}}""")
        assertPattern(EventPattern.Firebend(PlayerFilter.You), """{"type":"firebend","players":{"type":"you"}}""")
        assertPattern(EventPattern.Waterbend(PlayerFilter.You), """{"type":"waterbend","players":{"type":"you"}}""")
        assertPattern(EventPattern.Saddled(ObjectFilter.Any), """{"type":"saddled","objects":{"type":"any"}}""")
    }

    @Test
    fun theOldEventsStillRead() {
        assertEvent(GameEvent.Scried(alice, 2), """{"type":"scried","player":"alice","count":2}""")
        assertEvent(GameEvent.Surveilled(alice, 2), """{"type":"surveilled","player":"alice","count":2}""")
        assertEvent(GameEvent.Foraged(alice), """{"type":"foraged","player":"alice"}""")
        assertEvent(GameEvent.GaveGift(alice), """{"type":"gave-gift","player":"alice"}""")
        assertEvent(GameEvent.Airbended(alice), """{"type":"airbended","player":"alice"}""")
        assertEvent(GameEvent.Earthbended(alice), """{"type":"earthbended","player":"alice"}""")
        assertEvent(GameEvent.Firebended(alice), """{"type":"firebended","player":"alice"}""")
        assertEvent(GameEvent.Waterbended(alice), """{"type":"waterbended","player":"alice"}""")
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
