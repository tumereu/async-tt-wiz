package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import org.asyncttwiz.protocol.game.GameDevice
import org.asyncttwiz.protocol.game.PlaySettings
import org.asyncttwiz.protocol.game.ResponseMode
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * TRN-066, STK-057, HIS-075 (owner, 2026-11-09): the wait says what goes on in one place, a dark band under the other player's
 * hand, the "may respond" line too; the chip on the top bar shows and then fades, even while the state lasts ("X is deciding on
 * a mulligan" used to show for as long as they decided).
 */
class WaitBandTest : E2e() {
    private val band = hasTestTag("wait-band")
    private fun E2ePlayer.bandSays(text: String) = shows(hasText(text) and hasAnyAncestor(band))

    // HIS-075, TRN-066, TRN-027: Alice keeps while Bob decides: the band says so as long as it lasts, the chip goes
    @Test fun theMulliganWaitStaysInTheBandWhileTheChipFades() {
        startGame()
        bothOpen()
        alice.tap("Keep")
        val words = "${bob.name} is deciding on a mulligan"
        waitUntil("the band and the chip say it: ${alice.screen()}") { alice.bandSays(words) && alice.shows(hasTestTag("event-text")) }
        waitUntil("the chip gone, the band still there") { !alice.shows(hasTestTag("event-text")) && alice.bandSays(words) }
        bob.tap("Keep")
        waitUntil("the band gone with the wait", along = listOf(bob)) { !alice.bandSays(words) }
    }

    // TRN-066, HIS-112: Bob away, Alice's spell waits for him: "may respond" in the band, nothing over the middle of the board
    @Test fun mayRespondIsInTheBand() {
        startGame(TestDeck.thirds(TestCards.herald), TestDeck.thirds(TestCards.herald), bobSettings = PlaySettings(mode = ResponseMode.Normal),
            deal = { a, _ -> "Mountain" in hand(a) && "Ember Herald" in hand(a) })
        bothOpen()
        bothKeep(bobAway = true)
        alice.playFromHand("Mountain", this)
        waitUntil("the Mountain on the battlefield") { alice.field("Mountain").isNotEmpty() && alice.inOwnMain() }
        alice.playFromHand("Ember Herald", this)
        waitUntil("Alice waiting for Bob at her spell") { (alice.status as? GameDevice.Status.Waiting)?.offer == GameDevice.Offer.AssumeResolves }
        waitUntil("the band says it: ${alice.screen()}") { alice.bandSays("${bob.name} may respond to Ember Herald") }
        assertTrue(alice.shows(band))
        assertFalse(alice.shows(hasTestTag("centre-action")), "nothing in the board's middle")
    }
}
