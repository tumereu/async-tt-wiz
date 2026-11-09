package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import org.asyncttwiz.android.GAME_END_DIALOG_TAG
import org.asyncttwiz.android.GAME_END_OK_TAG
import org.asyncttwiz.protocol.GameLifecycle
import org.asyncttwiz.protocol.PresenceState
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * TRN-065, TRN-064, LOB-046 (owner playtest 2026-11-07: the result was "auto-dismissed" when the other player pressed OK
 * first; owner 2026-11-09: "I could not press OK because it was saying waiting for X to catch up ... I should not be taken
 * back until I acknowledge it -- one player acknowledging is insufficient"): OK works whenever the dialog shows, after its
 * first greyed moments; a result the rules gave takes both OKs, each can be taken back until then; after a concession or an
 * agreed result each player leaves the table only with their own OK; the player whose own action ended it has given it.
 */
class GameEndDialogTest : E2e() {
    private val dialog = hasTestTag(GAME_END_DIALOG_TAG)
    private fun E2ePlayer.showsDialog(vararg texts: String) =
        shows(dialog) && texts.all { shows(hasText(it) and hasAnyAncestor(dialog)) }
    private fun E2ePlayer.inLobby() = lobby.state.detail?.lifecycle == GameLifecycle.LOBBY && shows(hasTestTag("lobby-history-tab"))
    private fun E2ePlayer.atTable() = !shows(hasTestTag("lobby-history-tab")) && shows(dialog)

    /** Presses OK once it isn't greyed any more (its first moments, TRN-065). */
    private fun E2ePlayer.pressOk(vararg along: E2ePlayer) {
        val armed = hasTestTag(GAME_END_OK_TAG) and isEnabled()
        waitUntil("$name's OK no longer greyed", along = along.toList()) { shows(armed) }
        node(armed).performClick()
    }

    /** Opens [game]'s lobby from the main menu's Games list. */
    private fun E2ePlayer.openLobby() {
        waitUntil("$name's menu") { shows(hasTestTag("menu-GAMES")) }
        tagged("menu-GAMES").performClick()
        waitUntil("the game in $name's Games list") { showsText(this@GameEndDialogTest.game.gameName) }
        text(this@GameEndDialogTest.game.gameName).performScrollTo().performClick()
    }

    /** Alice's Ember Ruin takes Bob's life to 0: the result on both tables. */
    private fun aliceWinsByTheRules() {
        startGame(TestDeck("Ruin", listOf(TestCards.ruin to 1, TestCards.mountain to 39)), deal = { a, _ -> "Ember Ruin" in hand(a) })
        bothOpen()
        bothKeep(bobAway = false)
        alice.playFromHand("Ember Ruin", this)
        waitUntil("the result on both tables", along = listOf(bob)) {
            alice.showsDialog("You won", "${bob.name}'s life reached 0") && bob.showsDialog("You lost", "Your life reached 0")
        }
    }

    private val waitingForBob get() = "Waiting for ${bob.name} to acknowledge"
    private val waitingForAlice get() = "Waiting for ${alice.name} to acknowledge"

    // TRN-065 (owner, 2026-11-09, the report's two bugs): Bob is away and hasn't seen Alice's win: her OK works all the same,
    // and she stays at the table waiting for him; Bob comes back to the result, his dialog not dismissed by her OK, and only
    // his OK takes them both to the lobby, where nobody owes an OK any more
    @Test fun okWorksBeforeTheOtherPlayerHasSeenTheResultAndOnlyBothOksEndIt() {
        startGame(TestDeck("Ruin", listOf(TestCards.ruin to 1, TestCards.mountain to 39)), deal = { a, _ -> "Ember Ruin" in hand(a) })
        bothOpen()
        bothKeep(bobAway = false)
        away(bob, alice)
        waitUntil("Alice seeing Bob away") { alice.lobby.state.opponentPresence?.presence?.state == PresenceState.OFFLINE }
        alice.playFromHand("Ember Ruin", this)
        waitUntil("Alice's result") { alice.showsDialog("You won", "${bob.name}'s life reached 0") }
        alice.pressOk()
        waitUntil("Alice waiting for Bob") { alice.showsDialog("You won", waitingForBob) && alice.showsText("Take back OK") }
        bob.back()
        waitUntil("Bob's result, Alice's OK taking nothing from him", along = listOf(bob)) {
            if (bob.shows(hasTestTag("centre-action"))) bob.tapToWatch()
            bob.showsDialog("You lost", "Your life reached 0")
        }
        assertFalse(bob.shows(hasText(waitingForAlice)), "Bob hasn't pressed his")
        assertTrue(alice.atTable(), "one OK isn't enough")
        bob.pressOk(alice)
        waitUntil("both in the lobby", along = listOf(bob)) { alice.inLobby() && bob.inLobby() }
        assertFalse(alice.shows(dialog) || bob.shows(dialog), "both have pressed OK")
        assertFalse(alice.shows(hasTestTag("result-unseen")) || bob.shows(hasTestTag("result-unseen")), "nobody owes one")
    }

    // TRN-065: Alice takes her OK back before Bob's: Bob's OK then doesn't end it; hers again does. Reopening shows where
    // the OK stands: Bob's app starts again and he sees his own OK waiting for hers, on the table
    @Test fun anOkIsTakenBackUntilTheOtherPlayerPressesTheirs() {
        aliceWinsByTheRules()
        alice.pressOk(bob)
        waitUntil("Alice waiting for Bob", along = listOf(bob)) { alice.showsDialog(waitingForBob) }
        alice.text("Take back OK").performClick()
        waitUntil("Alice's OK taken back", along = listOf(bob)) { !alice.showsText(waitingForBob) && !alice.showsText("Take back OK") }
        bob.pressOk(alice)
        waitUntil("Bob waiting for Alice", along = listOf(alice)) { bob.showsDialog(waitingForAlice) }
        assertTrue(alice.atTable() && bob.atTable(), "her OK is gone, so nothing is final")
        bob = restart(bob)
        bob.openLobby()
        waitUntil("Bob's OK still standing on opening the game again", along = listOf(alice)) { bob.showsDialog(waitingForAlice) }
        alice.pressOk(bob)
        waitUntil("both in the lobby", along = listOf(bob)) { alice.inLobby() && bob.inLobby() && !alice.shows(dialog) && !bob.shows(dialog) }
    }

    // TRN-065, HIS-084: with Bob's OK on the result, Alice's Undo asks him as usual; on his yes the result and every OK go,
    // and the game goes on from before her Ruin
    @Test fun undoBeforeTheResultIsFinalTakesTheOksWithIt() {
        aliceWinsByTheRules()
        bob.pressOk(alice)
        waitUntil("Bob waiting for Alice", along = listOf(alice)) { bob.showsDialog(waitingForAlice) }
        alice.tap("Undo")
        val confirm = hasTestTag("rewind-confirm") and isEnabled()
        waitUntil("Alice's undo request dialog", along = listOf(bob)) { alice.shows(confirm) }
        alice.node(confirm).performClick()
        waitUntil("Bob asked to accept the undo", along = listOf(alice)) { bob.shows(hasTestTag("request-accept")) }
        bob.node(hasTestTag("request-accept")).performClick()
        waitUntil("the result gone from both tables", along = listOf(bob)) { !alice.shows(dialog) && !bob.shows(dialog) }
        assertTrue(alice.hand("Ember Ruin").isNotEmpty(), "back in her hand")
        assertEquals(GameLifecycle.ACTIVE, alice.lobby.state.detail?.lifecycle)
        assertTrue(alice.engine { device.model.table.resultOks.isEmpty() }, "no OK left on the board")
    }

    // TRN-064, TRN-065: Alice concedes: she goes straight to the lobby (her concession is her OK); Bob sees "You won" with
    // "Alice conceded" at the table, final already, and leaves it only with his own OK
    @Test fun aConcederGoesStraightToTheLobbyAndTheOtherPlayerStaysUntilTheirOk() {
        startGame()
        bothOpen()
        bothKeep(bobAway = false)
        alice.tap("Table actions")
        waitUntil("Alice's table actions") { alice.showsText("Concede") }
        alice.text("Concede").performClick()
        waitUntil("the Concede dialog") { alice.showsText("You lose this game at once.") }
        alice.text("Concede").performClick()
        waitUntil("Bob's result") { bob.showsDialog("You won", "${alice.name} conceded") }
        waitUntil("Alice in the lobby") { alice.inLobby() }
        assertFalse(alice.shows(dialog), "the conceder has seen it")
        assertTrue(bob.atTable(), "${bob.name} stays at the table until his OK")
        waitUntil("Alice's lobby waiting for Bob's OK") { alice.shows(hasTestTag("result-unseen")) }
        bob.pressOk()
        waitUntil("Bob in the lobby") { bob.inLobby() && !bob.shows(dialog) }
    }

    // TRN-047, TRN-065: Alice suggests her win ("Infinite combo"); Bob's Accept, greyed at first, ends the game: he goes
    // straight to the lobby, and Alice sees "You won" with "Agreed: Infinite combo" at the table until her OK; on another
    // device (her app started again) the same dialog shows over the lobby
    @Test fun theAcceptorGoesStraightToTheLobbyAndTheSuggesterStaysUntilTheirOk() {
        startGame()
        bothOpen()
        bothKeep(bobAway = false)
        alice.tap("Table actions")
        waitUntil("Alice's table actions") { alice.showsText("Suggest game end") }
        alice.text("Suggest game end").performClick()
        waitUntil("the suggestion chooser") { alice.showsText("My win") }
        alice.text("My win").performClick()
        waitUntil("the confirm dialog") { alice.shows(hasTestTag("resultNote")) }
        alice.tagged("resultNote").performTextInput("Infinite combo")
        alice.text("Confirm").performClick()
        waitUntil("the suggestion on Bob's table") { bob.shows(hasContentDescription("Accept result")) }
        // The icon names the button around it, greyed for its first moments
        val accept = hasTestTag("result-accept") and isEnabled()
        waitUntil("Bob's Accept no longer greyed") { bob.shows(accept) }
        bob.node(accept).performClick()
        waitUntil("Alice's result") { alice.showsDialog("You won", "Agreed: Infinite combo") }
        waitUntil("Bob in the lobby") { bob.inLobby() }
        assertFalse(bob.shows(dialog), "the player who accepted has seen it")
        assertTrue(alice.atTable(), "${alice.name} stays at the table until her OK")
        alice = restart(alice)
        alice.openLobby()
        waitUntil("Alice's dialog on reopening, over the lobby") { alice.showsDialog("You won", "Agreed: Infinite combo") }
        alice.pressOk()
        waitUntil("Alice in the lobby") { alice.inLobby() && !alice.shows(dialog) }
    }
}
