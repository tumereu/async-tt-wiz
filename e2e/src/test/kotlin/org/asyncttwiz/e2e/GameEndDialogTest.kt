package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import org.asyncttwiz.android.GAME_END_DIALOG_TAG
import org.asyncttwiz.protocol.GameLifecycle
import org.asyncttwiz.protocol.game.GameDevice
import org.junit.Test
import kotlin.test.assertFalse

/**
 * TRN-065, TRN-064, LOB-046 (owner playtest 2026-11-07: the result was "auto-dismissed" when the other player pressed OK
 * first): every ending shows each player a dialog in the middle of the screen that stays until that player's own OK, on
 * every reopen; the player whose own action ended the game goes straight on to the lobby.
 */
class GameEndDialogTest : E2e() {
    private val dialog = hasTestTag(GAME_END_DIALOG_TAG)
    private fun E2ePlayer.showsDialog(vararg texts: String) =
        shows(dialog) && texts.all { shows(hasText(it) and hasAnyAncestor(dialog)) }
    private fun E2ePlayer.ok() = node(hasText("OK") and hasAnyAncestor(dialog))
    private fun E2ePlayer.inLobby() = lobby.state.detail?.lifecycle == GameLifecycle.LOBBY && shows(hasTestTag("lobby-history-tab"))

    /** Opens [game]'s lobby from the main menu's Games list. */
    private fun E2ePlayer.openLobby() {
        waitUntil("$name's menu") { shows(hasTestTag("menu-GAMES")) }
        tagged("menu-GAMES").performClick()
        waitUntil("the game in $name's Games list") { showsText(this@GameEndDialogTest.game.gameName) }
        text(this@GameEndDialogTest.game.gameName).performScrollTo().performClick()
    }

    // TRN-065 (owner playtest 2026-11-07, "auto-dismissed"): Alice's Ember Ruin takes Bob's life to 0; both see the result
    // ("You won" / "You lost" with the reason); Alice's OK ends it and takes her to the lobby, which says Bob hasn't seen
    // it; Bob's dialog stays, also after his app starts again and he opens the game; his OK takes him to the lobby too
    @Test fun aResultStaysUntilYourOwnOkWhenTheOtherPlayerTapsOkFirst() {
        startGame(TestDeck("Ruin", listOf(TestCards.ruin to 1, TestCards.mountain to 39)), deal = { a, _ -> "Ember Ruin" in hand(a) })
        bothOpen()
        bothKeep(bobAway = false)
        alice.playFromHand("Ember Ruin", this)
        waitUntil("the result on both tables", along = listOf(bob)) {
            alice.showsDialog("You won", "${bob.name}'s life reached 0") && bob.showsDialog("You lost", "Your life reached 0")
        }
        waitUntil("Alice's OK once Bob has seen it") { (alice.game?.shown?.status as? GameDevice.Status.Over)?.confirmable == true }
        alice.ok().performClick()
        waitUntil("Alice in the lobby, told Bob hasn't seen the result") {
            alice.inLobby() && !alice.shows(dialog) && alice.shows(hasTestTag("result-unseen"))
        }
        waitUntil("Bob's dialog still there") { bob.showsDialog("You lost", "Your life reached 0") }
        bob = restart(bob)
        bob.openLobby()
        waitUntil("Bob's dialog on reopening") { bob.showsDialog("You lost", "Your life reached 0") }
        bob.ok().performClick()
        waitUntil("Bob in the lobby") { bob.inLobby() && !bob.shows(dialog) }
        waitUntil("Alice's lobby no longer waiting for Bob") { !alice.shows(hasTestTag("result-unseen")) }
    }

    // TRN-064, TRN-065: Alice concedes: she goes straight to the lobby (her concession is her OK); Bob sees "You won" with
    // "Alice conceded" until his OK
    @Test fun aConcederGoesStraightToTheLobbyAndTheOtherPlayerSeesTheDialog() {
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
        bob.ok().performClick()
        waitUntil("Bob in the lobby") { bob.inLobby() && !bob.shows(dialog) }
    }

    // TRN-047, TRN-065: Alice suggests her win ("Infinite combo"); Bob accepts, which ends the game: he goes straight to
    // the lobby, and Alice sees "You won" with "Agreed: Infinite combo" until her OK
    @Test fun theAcceptorGoesStraightToTheLobbyAndTheSuggesterSeesTheDialog() {
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
        // The icon names the button around it
        bob.described("Accept result").performClick()
        waitUntil("Alice's result") { alice.showsDialog("You won", "Agreed: Infinite combo") }
        waitUntil("Bob in the lobby") { bob.inLobby() }
        assertFalse(bob.shows(dialog), "the player who accepted has seen it")
        alice.ok().performClick()
        waitUntil("Alice in the lobby") { alice.inLobby() && !alice.shows(dialog) }
    }
}
