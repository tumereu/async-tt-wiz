package org.asyncttwiz.e2e

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import org.asyncttwiz.android.ShellDestination
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Docs/specs/web-client.md sections 3.5 and 3.6: an app whose built-in catalog checksum differs from its installed card
 * records (here: none) downloads the server's card catalog on start. Meanwhile Alice's menu, Settings and Games list work,
 * opening her game is greyed with "Card records updating", and Bob's chat goes on; once installed her table opens and
 * works, with his message there.
 */
class CardRecordsDownloadTest : E2e() {
    // LOB-091, LOB-093, LOB-095, LOB-096
    @Test fun theAppDownloadsItsCardRecordsOnStartAndStaysUsableMeanwhile() {
        startGame(aliceCatalog = true, holdAliceCatalog = true)
        waitUntil("Alice's menu") { alice.shows(hasTestTag("menu-SETTINGS")) }
        assertFalse(alice.catalog!!.state.ready)
        // Settings work and say how far the card records are
        alice.tagged("menu-SETTINGS").performScrollTo().performClick()
        waitUntil("Alice's Settings with the card records' line") { alice.showsText("Card records updating", substring = true) }
        // The Games list works; opening the game and New game are greyed with the line
        alice.auth.navigate(ShellDestination.GAMES)
        waitUntil("the game in Alice's Games list") { alice.showsText(game.gameName) }
        alice.tagged("card-records-note").assertExists()
        alice.tagged("quick-continue-${game.id}").assertIsNotEnabled()
        alice.tagged("new-game").assertIsNotEnabled()
        // Bob opens the game and writes in its chat meanwhile
        bob.openGame(game, this)
        bob.described("Chat, ", substring = true).performClick()
        waitUntil("Bob's chat open") { bob.shows(hasContentDescription("Chat message draft")) }
        bob.described("Chat message draft").performTextInput("Waiting for your cards")
        waitUntil("Bob's draft") { bob.lobby.state.chat.draft?.text == "Waiting for your cards" }
        bob.described("Send").performClick()
        waitUntil("Bob's message accepted") { bob.lobby.state.chat.messages.size == 1 }
        // The card catalog comes through: the records are the app's catalog, and the game opens
        alice.catalogGate.countDown()
        waitUntil("Alice's card records installed") { alice.catalog!!.state.ready }
        assertEquals(E2eStack.catalogChecksum, alice.catalog!!.state.installed!!.checksum)
        waitUntil("the line gone") { !alice.shows(hasTestTag("card-records-note")) }
        alice.tagged("quick-continue-${game.id}").assertIsEnabled()
        alice.nodes(hasText(game.gameName)).onLast().performScrollTo().performClick()
        waitUntil("Alice's table") { alice.game?.shown != null }
        waitUntil("Bob's message on Alice's phone") { alice.lobby.state.chat.messages.any { it.text == "Waiting for your cards" } }
    }
}
