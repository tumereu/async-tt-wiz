package org.asyncttwiz.android

import arcanum.engine.input.Input
import arcanum.engine.state.PlayerId
import org.asyncttwiz.protocol.game.EnginePart
import org.asyncttwiz.protocol.game.EngineStep
import org.asyncttwiz.protocol.game.GameEvent
import org.asyncttwiz.protocol.game.MainPress
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** HIS-101, HIS-099, HIS-076 (owner, 2026-11-08): a main button press on the record is an event of its player's timeline, in words. */
class MainExitTextTest {
    private fun pass(press: MainPress? = null) =
        GameEvent("e", "p", "a", EnginePart(0, listOf(EngineStep(Input.PassPriority(PlayerId("a")), emptyList()))), press = press)

    @Test fun aPressIsOnItsPlayersTimelineInWords() {
        assertFalse(EngineEventText.onTimeline(pass(), "a"), "any other pass isn't")
        assertTrue(EngineEventText.onTimeline(pass(MainPress.ToCombat), "a"))
        assertEquals("You went to combat", EngineEventText.timelineText(pass(MainPress.ToCombat), null, "a", emptyMap()))
        assertEquals("You went to the end step", EngineEventText.timelineText(pass(MainPress.ToEndStep), null, "a", emptyMap()))
        assertEquals("You ended the turn", EngineEventText.timelineText(pass(MainPress.EndTurn), null, "a", emptyMap()))
    }

    @Test fun theOtherPlayersPressShowsOnlyAsWhatItLedTo() {
        for (press in listOf(MainPress.ToCombat, MainPress.ToEndStep, MainPress.EndTurn))
            assertFalse(EngineEventText.onTimeline(pass(press), "b"), "$press")
    }

    @Test fun itsUndoSaysSo() {
        assertEquals("Undid: went to combat", EngineEventText.undid(pass(MainPress.ToCombat), null, "a", "a", emptyMap()))
        assertEquals("Alice undid: went to combat", EngineEventText.undid(pass(MainPress.ToCombat), null, "b", "a", mapOf("a" to "Alice")))
        assertEquals("Alice undid: went to the end step", EngineEventText.undid(pass(MainPress.ToEndStep), null, "b", "a", mapOf("a" to "Alice")))
        assertEquals("Undid: End turn", EngineEventText.undid(pass(MainPress.EndTurn), null, "a", "a", emptyMap()))
        assertEquals("Alice undid: End turn", EngineEventText.undid(pass(MainPress.EndTurn), null, "b", "a", mapOf("a" to "Alice")))
    }
}
