package org.asyncttwiz.e2e

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import org.asyncttwiz.android.UndoPoint
import org.asyncttwiz.android.undoPoints
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Owner, 2026-11-06 (timeline separators and undo points): the Undo dialog's points are exactly the timeline's events,
 * chosen by dragging the timeline's strip under the board at the chosen point; going back past the other player's event
 * is an undo request.
 */
class UndoDialogTest : E2e() {
    /** The Undo dialog's points on [player]'s table now (the timeline's events up to their local head). */
    private fun points(player: E2ePlayer): List<UndoPoint> {
        val game = player.game!!
        return undoPoints(game.shown!!, game.timeline.events(game, names()))
    }

    /** The text of the point the open Undo dialog has chosen. */
    private fun E2ePlayer.chosen(): String? = runCatching {
        node(hasTestTag("undo-point")).fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString()
    }.getOrNull()

    /** Drags [player]'s Undo strip by one event: right ([earlier]) chooses the point before, left the one after. */
    private fun drag(player: E2ePlayer, earlier: Boolean) {
        player.node(hasTestTag("undo-scroller")).performTouchInput {
            val distance = (viewConfiguration.touchSlop + 24.dp.toPx()) * (if (earlier) 1 else -1)
            swipe(center, center + Offset(distance, 0f), 200)
        }
        run()
    }

    /** Drags [player]'s Undo strip one event at a time from the point it opened at (before their latest own action,
     * HIS-084) to [point]. */
    private fun dragTo(player: E2ePlayer, point: UndoPoint, points: List<UndoPoint>) {
        val from = points.indexOfLast { it.target == player.shown.undoTarget }
        val to = points.indexOf(point)
        if (from < 0 || to < 0) fail("no such points: $from → $to in ${points.map { it.shown.description }}")
        repeat(kotlin.math.abs(to - from)) { drag(player, earlier = to < from) }
    }

    // HIS-099, HIS-083, HIS-087 (owner 2026-11-06): Alice plays a Mountain and casts an Ember Herald (Bob away, her phone
    // assuming he lets it resolve); holding Undo opens the dialog at the point before her cast; dragging the strip one
    // event right chooses her Mountain, and Undo takes both back at once, free (nothing after it is Bob's)
    @Test fun dragTheUndoStripToAnOlderOwnEvent() {
        val cast = aliceCastsAHeraldWhileBobIsAway(endTurn = false)
        alice.hold("Undo")
        waitUntil("Alice's Undo dialog") { alice.shows(hasTestTag("undo-scroller")) }
        assertEquals("You cast Ember Herald", alice.chosen(), "opened at the point before her latest action")
        val points = points(alice)
        assertEquals(cast.cast.id, points.last { it.shown.description == "You cast Ember Herald" }.event.id)
        val land = points.last { it.shown.description == "You played Mountain" }
        drag(alice, earlier = true)
        waitUntil("her Mountain chosen, its board worked out") {
            alice.chosen() == "You played Mountain" && runCatching { alice.node(hasTestTag("rewind-confirm")).assertIsEnabled() }.isSuccess
        }
        assertTrue(alice.showsText("Undo") && !alice.showsText("Ask ${bob.name}"), "free: ${alice.screen()}")
        alice.node(hasTestTag("rewind-confirm")).performClick()
        waitUntil("the server back before her Mountain") { serverGame()?.headId == land.target }
        waitUntil("her Mountain and Herald back in her hand") {
            alice.field("Mountain").isEmpty() && alice.hand("Mountain").isNotEmpty() && alice.hand("Ember Herald").isNotEmpty() &&
                alice.engine { device.session.pending.isEmpty() }
        }
        assertTrue(!alice.refusalSeen, "no refusal: ${alice.shownTexts}")
    }

    // HIS-099, HIS-084, SYS-038 (owner 2026-11-06): Alice plays a Mountain and ends her turn; Bob plays his Mountain;
    // Alice holds Undo and drags the strip to Bob's land: going back to just before it asks Bob ("Ask Bob", with a
    // message), and when he accepts his Mountain is back in his hand
    @Test fun undoBeforeTheOtherPlayersEventIsARequest() {
        startGame(deal = { a, b -> "Mountain" in hand(a) && "Mountain" in hand(b) })
        bothOpen()
        bothKeep(bobAway = false)
        alice.playFromHand("Mountain", this)
        waitUntil("Alice's Mountain", along = listOf(bob)) { alice.field("Mountain").size == 1 && alice.inOwnMain() }
        alice.hold("To combat")
        // Bob plays along: in his Main 1 he plays a Mountain first
        waitUntil("Bob's Mountain on Alice's table, all of it on the server", along = listOf(alice, bob)) {
            alice.field("Mountain", owner = bob.me).size == 1 && bob.engine { device.session.pending.isEmpty() } &&
                alice.engine { device.session.pending.isEmpty() } && alice.game?.shown?.behind == false &&
                points(alice).any { it.shown.description == "${bob.name} played Mountain" }
        }
        alice.hold("Undo")
        waitUntil("Alice's Undo dialog") { alice.shows(hasTestTag("undo-scroller")) }
        val points = points(alice)
        val bobs = points.last { it.shown.description == "${bob.name} played Mountain" }
        dragTo(alice, bobs, points)
        waitUntil("Bob's land chosen, asking him") {
            alice.chosen() == "${bob.name} played Mountain" && alice.showsText("Ask ${bob.name}") &&
                runCatching { alice.node(hasTestTag("rewind-confirm")).assertIsEnabled() }.isSuccess
        }
        alice.node(hasTestTag("rewind-message")).performTextInput("Wait, I misclicked")
        alice.node(hasTestTag("rewind-confirm")).performClick()
        waitUntil("Bob asked to accept the undo", along = listOf(alice)) { bob.shows(hasTestTag("request-accept")) }
        assertTrue(bob.showsText("“Wait, I misclicked”"), bob.screen())
        bob.node(hasTestTag("request-accept")).performClick()
        waitUntil("the server back before Bob's land") { serverGame()?.headId == bobs.target }
        waitUntil("Bob's Mountain back in his hand on both tables") {
            alice.field("Mountain", owner = bob.me).isEmpty() && bob.field("Mountain").isEmpty() && bob.hand("Mountain").isNotEmpty()
        }
    }
}
