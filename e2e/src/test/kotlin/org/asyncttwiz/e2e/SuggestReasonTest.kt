package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Test

/**
 * Owner, 2026-11-05 (TRN-046): the reason typed into Suggest game end reaches the other player.
 */
class SuggestReasonTest : E2e() {
    // TRN-046, TRN-047: Alice suggests a draw with the reason "Infinite combo"; Bob's suggestion panel shows the reason
    // under "<Alice> proposed a draw.", and so does Alice's own
    @Test fun theReasonOfASuggestionReachesTheOtherPlayer() {
        startGame()
        bothOpen()
        bothKeep(bobAway = false)
        alice.tap("Table actions")
        waitUntil("Alice's table actions") { alice.showsText("Suggest game end") }
        alice.text("Suggest game end").performClick()
        waitUntil("the suggestion chooser") { alice.showsText("Draw game") }
        alice.text("Draw game").performClick()
        waitUntil("the confirm dialog") { alice.shows(hasTestTag("resultNote")) }
        alice.tagged("resultNote").performTextInput("Infinite combo")
        alice.text("Confirm").performClick()
        waitUntil("the suggestion on Bob's table", along = listOf(bob)) { bob.showsText("${alice.name} proposed a draw.") }
        waitUntil("its reason on Bob's table") { bob.showsText("Infinite combo", substring = true) }
        waitUntil("its reason on Alice's table") { alice.showsText("You proposed a draw.") && alice.showsText("Infinite combo", substring = true) }
    }
}
