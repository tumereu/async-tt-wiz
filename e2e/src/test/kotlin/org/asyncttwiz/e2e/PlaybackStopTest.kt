package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performClick
import arcanum.engine.input.Input
import org.asyncttwiz.android.TableBoard
import org.asyncttwiz.protocol.PlayZone
import org.asyncttwiz.protocol.game.GameDevice
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Problem report SMUJ7M (owner playtest 2026-11-04; moved from the interim EnginePlaybackStopTest): watching the other
 * player's playback, their creature spell must not reach the battlefield before the watcher answered at their decision
 * stop. Alice casts an Ember Herald (an enters trigger, as the report's Squirrel) while Bob is away, her phone assumes
 * Bob lets it resolve and she plays on; Bob's playback stops with the spell on the Stack, nothing after it shown (board,
 * motion steps, local head), and his Resolve there confirms and plays on. (The report itself was Auto-skip working as
 * STK-036 says: the watcher couldn't respond to the spell, so playback confirmed it, and stopped at its enters trigger,
 * where they could.)
 */
class PlaybackStopTest : E2e() {
    private fun tokens(play: TableBoard) = play.board.cards.count { c ->
        c.token && play.board.containers.single { it.id == c.containerId }.zone == PlayZone.BATTLEFIELD }

    // HIS-095, STK-036, I-D56 §7 (SMUJ7M): playback stops before the assumed pass about the watcher with the spell on the
    // Stack and shows nothing of the assumed line after it; answering the same confirms it and playback goes on
    @Test fun playbackStopsWithTheSpellOnTheStackUntilTheWatcherAnswers() {
        val cast = aliceCastsAHeraldWhileBobIsAway()
        val herald = cast.herald
        val line = cast.line
        val castEvent = cast.cast
        val assumed = cast.next
        assertTrue(line.indexOf(assumed) < line.lastIndex, "Alice played on after the assumption")

        // Bob comes back and watches the playback; every board his table shows is recorded
        val boards = mutableListOf<TableBoard>()
        var seen = -1L
        everyRun += {
            bob.game?.shown?.takeIf { it.version != seen }?.let { shown ->
                seen = shown.version
                boards += shown.board
                shown.transition?.steps?.let(boards::addAll)
            }
        }
        val before = bob.leaks.shown.size
        val played = recordShown { bob }
        bob.back()
        waitUntil("Bob's playback stopping") {
            if (bob.status == GameDevice.Status.CatchUp) bob.once { bob.tapToWatch() }
            bob.status is GameDevice.Status.Stopped
        }
        val stop = assertIs<GameDevice.Status.Stopped>(bob.status)
        assertEquals(assumed.id, stop.event.id)
        assertEquals(Input.PassPriority(bob.me), stop.assumed)
        assertEquals(castEvent.id, bob.shown.localHead)
        assertEquals("STACK", where(bob.shown.board, herald), "the spell waits on the Stack for Bob")
        // Nothing of the assumed continuation was drawn: never the Herald on the battlefield, never its token
        assertTrue(boards.isNotEmpty())
        for (board in boards + bob.shown.board) {
            assertTrue(where(board, herald) != PlayZone.BATTLEFIELD.name, "the Herald shown on the battlefield before Bob answered")
            assertEquals(0, tokens(board), "the Herald's token shown before Bob answered")
        }
        val shown = bob.leaks.shown.drop(before).map { it.id } + played.map { it.id }
        assertTrue(line.drop(line.indexOf(assumed)).none { it.id in shown }, "no event from the assumed pass on shown")
        waitUntil("the server holding Bob's local head at the cast") {
            serverGame()?.localHeads?.get(bob.id) == castEvent.id
        }

        // Resolve: the same as assumed, so it confirms and playback goes on to the Herald on the battlefield
        bob.tap("Resolve")
        waitUntil("the server taking Bob's confirmation") {
            serverGame()?.localHeads?.get(bob.id).let { it != null && it != castEvent.id &&
                line.indexOfFirst { e -> e.id == it } >= line.indexOf(assumed) }
        }
        assertTrue(alice.engine { device.session.events }.any { it.id == assumed.id }, "confirmed: the assumed pass stays on the line")
        waitUntil("the Herald on the battlefield on Bob's table") { where(bob.shown.board, herald) == PlayZone.BATTLEFIELD.name }
        assertTrue(bob.leaks.leaks.isEmpty() && alice.leaks.leaks.isEmpty())
    }
}
