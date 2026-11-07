package org.asyncttwiz.e2e

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import org.asyncttwiz.protocol.PlayZone
import org.asyncttwiz.protocol.game.GameDevice
import org.asyncttwiz.protocol.game.PlaySettings
import org.asyncttwiz.protocol.game.ResponseMode
import org.junit.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Owner problem report WDKHSB (2026-11-07): "asked to resolve an action, had nothing to react with. toggling control
 * mode on and off autoresolved it". His response mode was Normal (recorded on his own events and the other player's
 * assumptions about him), which stops at the other player's spells and abilities (STK-036); playback's quick actions
 * showed only a white "Control" toggle, whose off set Quick, which then passed the stop by itself. Owner decisions: the
 * playback's mode cell is live play's (its name and colour, a tap going on to the next mode), and a new mode applies from
 * the next point on: the stop you stand at stays until you answer it.
 */
class ModeAtStopTest : E2e() {
    private fun E2ePlayer.modeCell(): String? = described("Response mode").fetchSemanticsNode().config
        .getOrNull(SemanticsProperties.StateDescription)

    // HIS-095, TRN-059, STK-036 (report WDKHSB): Bob, in Normal, holds nothing he could respond with while Alice casts
    // an Ember Herald with him away. His playback stops at her spell (Normal stops there) and his quick actions show the
    // mode it stops by, Normal, as live play does. He switches to Quick there: the stop stays, live, until his Resolve;
    // the next point, her Herald's trigger, follows Quick: playback goes on past it to live play with no other stop
    @Test fun aModeSwitchAtAPlaybackStopWaitsForTheAnswer() {
        val cast = aliceCastsAHeraldWhileBobIsAway(endTurn = false, bobCanRespond = false,
            bobSettings = PlaySettings(mode = ResponseMode.Normal))
        bob.back()
        waitUntil("Bob's catch-up") { bob.status == GameDevice.Status.CatchUp }
        assertEquals("Normal", bob.modeCell(), "playback shows his response mode as live play does: ${bob.screen()}")
        bob.tapToWatch()
        waitUntil("Bob's playback stopping at Alice's spell") { bob.status is GameDevice.Status.Stopped }
        val stop = assertIs<GameDevice.Status.Stopped>(bob.status)
        assertEquals(cast.next.id, stop.event.id, "Normal stops before the pass assumed for him at her spell")
        assertEquals("STACK", where(bob.shown.board, cast.herald))
        assertEquals("Resolve", bob.stepArrow())

        // Normal → Quick from the playback's mode cell: the stop stays
        bob.tap("Response mode")
        waitUntil("Bob's mode Quick") { bob.shown.settings.mode == ResponseMode.AutoSkip && bob.modeCell() == "Quick" }
        repeat(3) { run(skip = Duration.ofSeconds(5)) }
        assertEquals(stop.event.id, (bob.status as? GameDevice.Status.Stopped)?.event?.id, "still stopped at her spell: ${bob.status}")
        assertEquals("STACK", where(bob.shown.board, cast.herald))
        assertEquals("Resolve", bob.stepArrow())

        // His Resolve confirms; her Herald's trigger (where Normal would stop) goes by under Quick
        val stops = mutableListOf<String>()
        everyRun += { (bob.game?.shown?.status as? GameDevice.Status.Stopped)?.let { s -> if (stops.lastOrNull() != s.event.id) stops += s.event.id } }
        bob.tap("Resolve")
        waitUntil("Bob live at the newest event") {
            !bob.shown.behind && bob.status !is GameDevice.Status.Playing && bob.status !is GameDevice.Status.Stopped
        }
        assertEquals(PlayZone.BATTLEFIELD.name, where(bob.shown.board, cast.herald))
        assertTrue(stops.all { it == stop.event.id }, "no stop after his answer: $stops")
        assertTrue(alice.engine { device.session.events }.any { it.id == cast.next.id }, "confirmed, not branched")
        assertTrue(bob.leaks.leaks.isEmpty() && alice.leaks.leaks.isEmpty())
    }
}
