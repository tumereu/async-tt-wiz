package org.asyncttwiz.e2e

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import arcanum.api.TurnStep
import arcanum.engine.Engine
import org.asyncttwiz.android.UndoPoint
import org.asyncttwiz.android.timeline
import org.asyncttwiz.android.undoPoints
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Owner notes 2026-11-14 evening: after the other player's rewind back to an earlier step, when the game came back to
 * declare attackers the attacker's earlier staged attackers were still there. Alice stages her Ember Cub as an attacker in
 * her turn 3; Bob takes the game back to before his own Mountain in his turn 2 (asking Alice when it needs her approval);
 * the staging goes with it, on both tables, and when Alice reaches her declare attackers step again nothing is staged.
 */
class RewindClearsStagingTest : E2e() {
    private fun points(player: E2ePlayer): List<UndoPoint> {
        val game = player.game!!
        return undoPoints(game.shown!!, game.timeline.events(game, names()))
    }

    private fun E2ePlayer.chosen(): String? = runCatching {
        node(hasTestTag("undo-point")).fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString()
    }.getOrNull()

    private fun drag(player: E2ePlayer, earlier: Boolean) {
        player.node(hasTestTag("undo-scroller")).performTouchInput {
            val distance = (viewConfiguration.touchSlop + 24.dp.toPx()) * (if (earlier) 1 else -1)
            swipe(center, center + Offset(distance, 0f), 200)
        }
        run()
    }

    private fun dragTo(player: E2ePlayer, point: UndoPoint, points: List<UndoPoint>) {
        val from = points.indexOfLast { it.target == player.shown.undoTarget }
        val to = points.indexOf(point)
        if (from < 0 || to < 0) fail("no such points: $from → $to in ${points.map { it.shown.description }}")
        repeat(kotlin.math.abs(to - from)) { drag(player, earlier = to < from) }
    }

    private fun E2ePlayer.staged(): Int = shown.board.engine?.table?.attackDraft.orEmpty().size

    private fun readyCub() = alice.state.let { s ->
        val view = Engine.view(s)
        alice.field("Ember Cub").any { s.obj(it)?.tapped == false && view[it]?.summoningSick == false }
    }

    // CMB-043, SYS-058, HIS-099
    @Test fun aRewindBackOutOfTheDeclarationClearsTheStaging() {
        startGame(TestDeck.thirds(TestCards.cub), TestDeck.thirds(TestCards.cub),
            deal = { a, b -> "Mountain" in hand(a) && "Ember Cub" in hand(a) && "Mountain" in hand(b) })
        bothOpen()
        bothKeep(bobAway = false)
        alice.playFromHand("Mountain", this)
        waitUntil("Alice's Mountain", along = listOf(bob)) { alice.field("Mountain").isNotEmpty() && alice.inOwnMain() }
        alice.playFromHand("Ember Cub", this)
        waitUntil("Alice's Cub", along = listOf(bob)) { alice.field("Ember Cub").isNotEmpty() && alice.inOwnMain() }
        alice.hold("To combat")
        waitUntil("Bob's Main 1", along = listOf(alice)) { bob.inOwnMain() }
        bob.playFromHand("Mountain", this)
        waitUntil("Bob's Mountain", along = listOf(alice)) { bob.field("Mountain").isNotEmpty() && bob.inOwnMain() }
        bob.hold("To combat")
        waitUntil("Alice's turn 3 with a ready Cub", along = listOf(bob)) { alice.inOwnMain() && alice.state.turn.number == 3 && readyCub() }
        alice.tap("To combat")
        waitUntil("Alice declaring attackers", along = listOf(bob)) { alice.state.turn.step == TurnStep.DeclareAttackers && alice.stepArrow() == "No attack" }
        alice.stageAttacker("Ember Cub", this)
        waitUntil("the Cub staged, on Bob's table too") {
            alice.staged() == 1 && bob.staged() == 1 && alice.engine { device.session.pending.isEmpty() } && bob.game?.shown?.behind == false &&
                points(bob).any { it.shown.description == "You played Mountain" }
        }
        // Bob takes the game back to before his Mountain
        bob.hold("Undo")
        waitUntil("Bob's Undo dialog") { bob.shows(hasTestTag("undo-scroller")) }
        val points = points(bob)
        val mountain = points.last { it.shown.description == "You played Mountain" }
        dragTo(bob, mountain, points)
        waitUntil("his Mountain chosen") {
            bob.chosen() == "You played Mountain" && runCatching { bob.node(hasTestTag("rewind-confirm")).assertIsEnabled() }.isSuccess
        }
        val asks = bob.showsText("Ask ${alice.name}")
        bob.node(hasTestTag("rewind-confirm")).performClick()
        if (asks) {
            waitUntil("Alice asked to accept the rewind", along = listOf(bob)) { alice.shows(hasTestTag("request-accept")) }
            alice.node(hasTestTag("request-accept")).performClick()
        }
        // Before the fix the server added Alice's staging again after the rewind's target, so its head moved past it
        waitUntil("the server back before Bob's Mountain, nothing added after it") { serverGame()?.headId == mountain.target }
        waitUntil("Bob's Mountain back in his hand on both tables, the staging gone") {
            bob.field("Mountain").isEmpty() && alice.field("Mountain", owner = bob.me).isEmpty() && bob.inOwnMain() &&
                alice.engine { device.session.pending.isEmpty() }
        }
        assertEquals(0, alice.staged(), "no staging left on Alice's table after the rewind")
        assertEquals(0, bob.staged(), "no staging left on Bob's table after the rewind")
        // The game comes back to Alice's declare attackers step (Bob plays his Mountain again): nothing staged there
        bob.playFromHand("Mountain", this)
        waitUntil("Bob's Mountain again", along = listOf(alice)) { bob.field("Mountain").isNotEmpty() && bob.inOwnMain() }
        bob.hold("To combat")
        waitUntil("Alice's turn 3 again", along = listOf(bob)) { alice.inOwnMain() && alice.state.turn.number == 3 }
        alice.tap("To combat")
        waitUntil("Alice declaring attackers again", along = listOf(bob)) { alice.state.turn.step == TurnStep.DeclareAttackers && alice.current() }
        run()
        assertEquals(0, alice.staged(), "nothing staged at the new declare attackers step")
        assertEquals("No attack", alice.stepArrow())
        assertFalse(alice.bandOpen(), "the band collapsed: no staged attacker")
        assertTrue(alice.leaks.leaks.isEmpty() && bob.leaks.leaks.isEmpty(), "${alice.leaks.leaks} ${bob.leaks.leaks}")
    }
}
