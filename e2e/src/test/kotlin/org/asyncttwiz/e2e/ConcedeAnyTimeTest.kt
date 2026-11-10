package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.performClick
import org.asyncttwiz.android.GAME_END_DIALOG_TAG
import org.asyncttwiz.protocol.game.GameDevice
import org.junit.Test
import kotlin.test.assertTrue

/**
 * TRN-064, CR 104.3a (found 2026-11-09): conceding needs nobody's agreement, any time; Concede in Table actions was greyed
 * while it wasn't your move.
 */
class ConcedeAnyTimeTest : E2e() {
    private val dialog = hasTestTag(GAME_END_DIALOG_TAG)

    // TRN-064: in Alice's first turn (her move, Bob waiting) Bob opens Table actions: Concede isn't greyed, and confirming it
    // ends the game as his loss on both tables
    @Test fun concedeWorksWhileItIsTheOtherPlayersMove() {
        startGame()
        bothOpen()
        bothKeep(bobAway = false)
        waitUntil("Alice's move, Bob waiting") { alice.status == GameDevice.Status.YourMove && bob.status is GameDevice.Status.Waiting }
        bob.tap("Table actions")
        waitUntil("Bob's table actions") { bob.showsText("Concede") }
        assertTrue(bob.shows(hasText("Concede") and isEnabled()), "Concede not greyed: ${bob.screen()}")
        bob.text("Concede").performClick()
        waitUntil("the Concede dialog") { bob.showsText("You lose this game at once.") }
        bob.text("Concede").performClick()
        waitUntil("the result on both tables") {
            bob.shows(hasText("You lost") and hasAnyAncestor(dialog)) && alice.shows(hasText("You won") and hasAnyAncestor(dialog))
        }
    }
}
