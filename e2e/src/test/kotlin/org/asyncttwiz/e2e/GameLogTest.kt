package org.asyncttwiz.e2e

import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import org.junit.Test
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The game log (owner, 2026-11-20: a text log of the game's events, keeping the players' colours): Table actions → Game log
 * lists the timeline's events as lines, the newest at the bottom; a line opens timeline mode at its event.
 */
class GameLogTest : E2e() {
    // HIS-239, HIS-241, TRN-010: on Bob's phone, after Alice played a Mountain: her play is the log's newest line, the same
    // texts the timeline has; tapping it closes the log and opens timeline mode there, its event text saying it (HIS-076)
    @Test fun theLogOnAPhone() {
        startGame(deal = { a, _ -> "Mountain" in hand(a) })
        bothOpen()
        bothKeep(bobAway = false)
        alice.playFromHand("Mountain", this)
        waitUntil("the Mountain on both tables", along = listOf(alice)) {
            alice.field("Mountain").isNotEmpty() && bob.showsOnBattlefield("Mountain", alice) && bob.settled(this)
        }
        bob.openGameLog(this)
        waitUntil("Alice's play in Bob's log") { bob.gameLogLines().lastOrNull() == "${alice.name} played Mountain" }
        val lines = bob.gameLogLines()
        val timeline = bob.game!!.let { g -> org.asyncttwiz.android.EngineTimeline().events(g, names()) }.map { it.description }
        assertEquals(timeline, lines, "the log is the timeline's events, oldest first")
        assertTrue("You kept" in lines && "${alice.name} kept" in lines, "both keeps in Bob's log: $lines")
        bob.control("${alice.name} played Mountain").performClick()
        waitUntil("Bob in timeline mode at Alice's play") {
            !bob.shows(hasTestTag("game-log")) && bob.showsTimelineLook() && bob.showsText("${alice.name} played Mountain")
        }
    }

    // HIS-239, HIS-241, WEB-015: on the desktop table the same: Table actions → Game log over the table, a click on a line
    // opens timeline mode there
    @Config(qualifiers = "w1440dp-h900dp-land-mdpi")
    @Test fun theLogOnTheDesktop() {
        startGame(aliceDesktop = true, deal = { a, _ -> "Mountain" in hand(a) })
        bothOpen()
        assertTrue(alice.shows(hasTestTag("phase-track")), "Alice's table in the desktop layout: ${alice.screen()}")
        alice.control("Keep").performMouseInput { click() }
        waitUntil("Alice's Main 1", along = listOf(bob)) { alice.inOwnMain() }
        waitUntil("Alice's game settled") { alice.settled(this) }
        alice.handCard("Mountain").performMouseInput { click() }
        waitUntil("the Mountain raised in Alice's hand") { alice.raisedInHand() == "Mountain" && alice.settled(this) }
        alice.handCard("Mountain").performMouseInput { click() }
        waitUntil("the Mountain on Alice's battlefield", along = listOf(bob)) { alice.field("Mountain").isNotEmpty() && alice.inOwnMain() }
        alice.openGameLog(this)
        waitUntil("her play in Alice's log") { alice.gameLogLines().lastOrNull() == "You played Mountain" }
        alice.control("You played Mountain").performMouseInput { click() }
        waitUntil("Alice in timeline mode at her play") {
            !alice.shows(hasTestTag("game-log")) && alice.showsTimelineLook() && alice.showsControl("Exit timeline")
        }
    }

    private fun names() = mapOf(alice.account.id to alice.name, bob.account.id to bob.name)
}
