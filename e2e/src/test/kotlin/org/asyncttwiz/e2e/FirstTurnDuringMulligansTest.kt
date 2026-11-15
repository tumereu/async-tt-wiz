package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.performClick
import org.asyncttwiz.protocol.game.AssumeResolves
import org.asyncttwiz.protocol.game.PlaySettings
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The first turn during the other player's mulligans (owner, 2026-11-15; TRN-027, HIS-115, HIS-116, HIS-117): Alice keeps
 * while Bob hasn't opened the game, presses Assume keep and plays her turn 1. Bob's game stays in his opening, showing
 * nothing of her turn, until he keeps; his Keep confirms her turn, his Mulligan makes it again on top of his new hand,
 * standing as far as it comes out the same for her and taken back from her first action that doesn't.
 */
class FirstTurnDuringMulligansTest : E2e() {
    /** HIS-117: until Bob keeps, nothing of Alice's turn reaches his table (checked at every round of the phones). */
    private var bobKept = false
    private val lag = mutableListOf<String>()

    private fun watchBobsLag() {
        everyRun += {
            val shown = runCatching { bob.game?.shown }.getOrNull()
            if (!bobKept && shown != null) {
                val s = shown.engine
                if (s.turn.number != 0) lag += "turn ${s.turn.number} shown"
                if (s.battlefield.isNotEmpty()) lag += "a battlefield card shown: ${s.battlefield}"
                if (s.player(alice.me).life != 20 || s.player(bob.me).life != 20) lag += "a life change shown"
            }
        }
    }

    /** Alice opens the game alone, keeps, presses Assume keep (her phone never assumes it by itself, even with "Always
     * assume resolves") and is in her Main 1; Bob's app is up, the game not opened. */
    private fun aliceAssumesBobsKeep(aliceDeck: TestDeck, deal: Deal.(TestAccount, TestAccount) -> Boolean) {
        startGame(aliceDeck, TestDeck.thirds(), aliceSettings = PlaySettings(assume = AssumeResolves.Always), deal = deal)
        watchBobsLag()
        alice.openGame(game, this)
        alice.tap("Keep")
        waitUntil("Assume keep on Alice's main button") { alice.showsControl("Assume keep") }
        assertEquals(0, alice.state.turn.number, "nothing assumed by itself")
        alice.tap("Assume keep")
        waitUntil("Alice's Main 1 on her Assume keep") { alice.inOwnMain() }
    }

    private fun alicePlaysMountainAndCub() {
        alice.playFromHand("Mountain", this)
        waitUntil("the Mountain on the battlefield") { alice.field("Mountain").isNotEmpty() && alice.inOwnMain() }
        alice.playFromHand("Ember Cub", this)
        waitUntil("the Cub on Alice's battlefield") { alice.field("Ember Cub").isNotEmpty() && alice.inOwnMain() }
        waitUntil("all of Alice's on the server") { alice.engine { device.session.pending.isEmpty() } }
    }

    /** Bob opens the game: his opening, with his Keep and Mulligan; nothing of Alice's turn. */
    private fun bobOpens() {
        bob.openGame(game, this)
        waitUntil("Bob's opening quick actions") { bob.showsControl("Mulligan") && bob.showsControl("Keep") }
        assertEquals(0, bob.state.turn.number)
        assertTrue(bob.state.player(alice.me).hand.size == 7 && bob.state.battlefield.isEmpty(), "Bob sees nothing of her turn")
    }

    // TRN-027, TRN-070, HIS-096, HIS-117: Bob keeps after Alice played: nothing is made again, her events stay, and her turn
    // reaches him only after his Keep
    @Test fun bobsKeepConfirmsTheTurnPlayedOnIt() {
        aliceAssumesBobsKeep(TestDeck.thirds()) { a, _ -> "Mountain" in hand(a) && "Ember Cub" in hand(a) }
        alicePlaysMountainAndCub()
        val line = alice.engine { device.session.events.map { it.id } }
        bobOpens()
        bobKept = true
        bob.tap("Keep")
        waitUntil("Alice's turn on Bob's table", along = listOf(bob)) {
            if (bob.showsWatch()) bob.tapToWatch()
            bob.state.battlefield.any { cardName(bob.state, it) == "Ember Cub" }
        }
        assertEquals(line, alice.engine { device.session.events.map { it.id } }.take(line.size), "her events stand as they were")
        assertTrue(alice.engine { device.session.events }.none { it.carriedFrom != null }, "nothing made again")
        assertTrue(lag.isEmpty(), "Bob saw her turn before keeping: $lag")
    }

    // HIS-115, HIS-116, HIS-117: Bob mulligans (the first is free): Alice's turn comes out the same and stands: her table
    // doesn't go back, "Bob took a mulligan · your turn stands"; Bob is in his opening again with a new hand and sees
    // nothing until he keeps
    @Test fun bobsMulliganLeavesTheTurnStanding() {
        aliceAssumesBobsKeep(TestDeck.thirds()) { a, _ -> "Mountain" in hand(a) && "Ember Cub" in hand(a) }
        alicePlaysMountainAndCub()
        val bobsHand = alice.state.player(bob.me).hand.toList()
        bobOpens()
        bob.tap("Mulligan")
        waitUntil("Alice told her turn stands") { alice.told("${bob.name} took a mulligan · your turn stands") }
        assertTrue(alice.field("Mountain").isNotEmpty() && alice.field("Ember Cub").isNotEmpty(), "her turn stays on her table")
        assertTrue(alice.engine { device.session.events }.any { it.carriedFrom != null }, "made again")
        assertTrue(!alice.refusalSeen, "nothing refused: ${alice.shownTexts}")
        waitUntil("Bob in his opening with a new hand") {
            bob.showsControl("Keep") && bob.state.player(bob.me).hand.toList() != bobsHand && bob.state.turn.number == 0
        }
        bobKept = true
        bob.tap("Keep")
        waitUntil("Alice's turn on Bob's table", along = listOf(bob)) {
            if (bob.showsWatch()) bob.tapToWatch()
            bob.state.battlefield.any { cardName(bob.state, it) == "Ember Cub" }
        }
        assertTrue(lag.isEmpty(), "Bob saw her turn before keeping: $lag")
    }

    // HIS-115, HIS-116, HIS-097, HIS-117: Alice plays a Mountain, then Ember Augury at Bob (she sees his top two cards). Bob
    // mulligans: her table goes back only to the Augury ("Bob took a mulligan — your Ember Augury was taken back"), the
    // Mountain stays, the Augury is back in her hand; Bob never sees it
    @Test fun aRevealThatComesOutDifferentlyIsTakenBack() {
        aliceAssumesBobsKeep(TestDeck("Auguries", listOf(TestCards.mountain to 20, TestCards.augury to 20))) { a, _ ->
            "Mountain" in hand(a) && "Ember Augury" in hand(a) }
        alice.playFromHand("Mountain", this)
        waitUntil("the Mountain on the battlefield") { alice.field("Mountain").isNotEmpty() && alice.inOwnMain() }
        val auguries = alice.hand("Ember Augury").size
        alice.playFromHand("Ember Augury", this)
        waitUntil("Alice's target bar") { alice.shows(hasText("Choose target player") and isHeading()) }
        alice.node(hasContentDescription("${bob.name}, life 20", substring = true)).performClick()
        waitUntil("the Augury resolved: Bob's top two cards shown to Alice") {
            if (alice.showsRevealOk()) alice.revealOk()
            alice.state.player(alice.me).graveyard.any { cardName(alice.state, it) == "Ember Augury" } && alice.inOwnMain() && !alice.showsRevealOk()
        }
        waitUntil("all of Alice's on the server") { alice.engine { device.session.pending.isEmpty() } }
        bobOpens()
        bob.tap("Mulligan")
        waitUntil("Alice told her Augury was taken back") { alice.told("${bob.name} took a mulligan — your Ember Augury was taken back") }
        waitUntil("Alice back before the Augury") {
            alice.state.player(alice.me).graveyard.none { cardName(alice.state, it) == "Ember Augury" } && alice.hand("Ember Augury").size == auguries
        }
        assertTrue(alice.field("Mountain").isNotEmpty(), "the Mountain stands")
        bobKept = true
        bob.tap("Keep")
        waitUntil("Bob caught up", along = listOf(bob)) {
            if (bob.showsWatch()) bob.tapToWatch()
            bob.state.battlefield.any { cardName(bob.state, it) == "Mountain" }
        }
        assertTrue(bob.leaks.shown.none { e -> e.engine?.steps.orEmpty().any { (it.input as? arcanum.engine.input.Input.Cast) != null } },
            "Bob never saw the Augury cast")
        assertTrue(lag.isEmpty(), "Bob saw her turn before keeping: $lag")
    }
}
