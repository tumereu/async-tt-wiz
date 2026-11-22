package org.asyncttwiz.e2e

import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
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

    // HIS-243, HIS-244, HIS-062, HIS-240 (owner 2026-11-21: event texts name their targets, "very valuable information"): Alice manifests
    // the top card of her library and casts Ember Boon at that face-down creature: her log names the card she may look at,
    // Bob's reads only "Alice's face-down creature", never what it is
    @Test fun aTargetInBothLogs() {
        startGame(TestDeck("Summons", listOf(TestCards.mountain to 20, TestCards.summons to 10, TestCards.boon to 10)),
            deal = { a, _ -> "Ember Summons" in hand(a) && "Ember Boon" in hand(a) })
        bothOpen()
        bothKeep(bobAway = false)
        alice.playFromHand("Ember Summons", this)
        aliceResolves("a face-down creature on Alice's battlefield") { alice.state.battlefield.any { alice.state.obj(it)?.faceDown == true } }
        val manifested = alice.state.battlefield.first { alice.state.obj(it)?.faceDown == true }
        val name = cardName(alice.state, manifested)!!
        alice.playFromHand("Ember Boon", this)
        waitUntil("Alice's target bar or the Boon cast: ${alice.screen()}", along = listOf(bob)) {
            alice.shows(hasContentDescription("Choose targets")) || alice.state.stack.isNotEmpty()
        }
        if (alice.shows(hasContentDescription("Choose targets"))) alice.battlefieldCard("Covered card").performClick()
        aliceResolves("the Boon resolved") { alice.state.obj(manifested)?.counters.orEmpty().isNotEmpty() }
        waitUntil("Bob's table settled after the Boon", along = listOf(alice)) {
            bob.state.obj(manifested)?.counters.orEmpty().isNotEmpty() && bob.settled(this)
        }
        alice.openGameLog(this)
        waitUntil("the Boon's target in Alice's log: ${alice.gameLogLines()}") {
            "You cast Ember Boon targeting your face-down $name" in alice.gameLogLines()
        }
        bob.openGameLog(this)
        waitUntil("the Boon's target in Bob's log: ${bob.gameLogLines()}") {
            "${alice.name} cast Ember Boon targeting ${alice.name}'s face-down creature" in bob.gameLogLines()
        }
        val boon = bob.gameLogLines().single { it.startsWith("${alice.name} cast Ember Boon") }
        assertTrue(name !in boon.removePrefix("${alice.name} cast Ember Boon"), "the face-down card isn't named to Bob: $boon")
    }

    // HIS-246, HIS-249, HIS-062 (owner 2026-11-22, F6): Alice casts Drowsy Pup, then Idle Glimmer and Ember Quake: under the
    // Quake's line both logs say the Pup died and both players lost 1 life, with the totals after; under the Glimmer's, Alice's
    // log names the card she drew and Bob's only counts it; nothing hidden was shown
    @Test fun whatAResolutionCausedInBothLogs() {
        startGame(TestDeck("Quake", listOf(TestCards.mountain to 25, TestCards.drowsy to 5, TestCards.quake to 5, TestCards.glimmer to 5)),
            deal = { a, _ -> listOf("Drowsy Pup", "Ember Quake", "Idle Glimmer").all { it in hand(a) } })
        bothOpen()
        bothKeep(bobAway = false)
        val me = arcanum.engine.state.PlayerId(alice.account.id)
        alice.playFromHand("Drowsy Pup", this)
        aliceResolves("the Pup on Alice's battlefield") { alice.field("Drowsy Pup").isNotEmpty() }
        val before = alice.state.player(me).hand.toSet()
        alice.playFromHand("Idle Glimmer", this)
        aliceResolves("the Glimmer's card drawn") { alice.state.player(me).hand.any { it !in before } }
        val drawn = alice.state.player(me).hand.first { it !in before }.let { cardName(alice.state, it)!! }
        alice.playFromHand("Ember Quake", this)
        aliceResolves("the Quake resolved") { alice.field("Drowsy Pup").isEmpty() && alice.state.player(me).life == 19 }
        waitUntil("Bob's table settled after the Quake", along = listOf(alice)) { bob.state.player(me).life == 19 && bob.settled(this) }
        fun under(rows: List<String>, line: String): Set<String> =
            rows.dropWhile { it != line }.drop(1).takeWhile { it.startsWith("  ") }.map { it.trim() }.toSet()
        alice.openGameLog(this)
        waitUntil("the Quake's lines in Alice's log: ${alice.gameLogRows()}") {
            under(alice.gameLogRows(), "Ember Quake resolved") == setOf("Drowsy Pup died", "You lost 1 life (19)", "${bob.name} lost 1 life (19)")
        }
        assertEquals(setOf("You drew $drawn"), under(alice.gameLogRows(), "Idle Glimmer resolved"), "${alice.gameLogRows()}")
        bob.openGameLog(this)
        waitUntil("the Quake's lines in Bob's log: ${bob.gameLogRows()}") {
            under(bob.gameLogRows(), "Ember Quake resolved") == setOf("Drowsy Pup died", "You lost 1 life (19)", "${alice.name} lost 1 life (19)")
        }
        val glimmer = under(bob.gameLogRows(), "Idle Glimmer resolved")
        assertEquals(setOf("${alice.name} drew a card"), glimmer, "Bob reads only a count of Alice's draw: ${bob.gameLogRows()}")
        assertTrue(alice.leaks.leaks.isEmpty() && bob.leaks.leaks.isEmpty(), "${alice.leaks.leaks} ${bob.leaks.leaks}")
    }

    /** Alice plays on to her Main 1 with an empty Stack, tapping Resolve where her phone stops. */
    private fun aliceResolves(what: String, done: () -> Boolean) = waitUntil(what, along = listOf(bob)) {
        if (alice.stepArrow() == "Resolve") alice.once { alice.tap("Resolve") }
        done() && alice.state.stack.isEmpty() && alice.inOwnMain()
    }

    private fun names() = mapOf(alice.account.id to alice.name, bob.account.id to bob.name)
}
