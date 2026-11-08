package org.asyncttwiz.android

import arcanum.api.TurnStep
import arcanum.engine.input.Input
import arcanum.engine.state.PlayerId
import org.asyncttwiz.protocol.game.EnginePart
import org.asyncttwiz.protocol.game.EngineStep
import org.asyncttwiz.protocol.game.GameEvent
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** HIS-101, HIS-099, HIS-076 (owner, 2026-11-08): a tap leaving your own main phase is an event of the timeline, in words. */
class MainExitTextTest {
    private val pass = GameEvent("e", "p", "a", EnginePart(0, listOf(EngineStep(Input.PassPriority(PlayerId("a")), emptyList()))))

    @Test fun aTapLeavingAMainPhaseIsOnTheTimelineInWords() {
        assertFalse(EngineEventText.onTimeline(pass, emptySet()), "any other pass isn't")
        assertTrue(EngineEventText.onTimeline(pass, emptySet(), mapOf("e" to TurnStep.PrecombatMain)))
        assertEquals("You went to combat", EngineEventText.timelineText(pass, null, "a", emptyMap(), emptySet(), mapOf("e" to TurnStep.PrecombatMain)))
        assertEquals("You went to the end step", EngineEventText.timelineText(pass, null, "a", emptyMap(), emptySet(), mapOf("e" to TurnStep.PostcombatMain)))
        assertEquals("You ended the turn", EngineEventText.timelineText(pass, null, "a", emptyMap(), setOf("e")))
    }

    @Test fun itsUndoSaysSo() {
        assertEquals("Undid: went to combat", EngineEventText.undid(pass, null, "a", "a", emptyMap(), TurnStep.PrecombatMain))
        assertEquals("Alice undid: went to the end step", EngineEventText.undid(pass, null, "b", "a", mapOf("a" to "Alice"), TurnStep.PostcombatMain))
        assertEquals("Undid: End turn", EngineEventText.undid(pass, null, "a", "a", emptyMap()))
    }
}
