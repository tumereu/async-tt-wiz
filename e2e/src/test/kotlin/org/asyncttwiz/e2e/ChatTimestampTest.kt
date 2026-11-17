package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import org.asyncttwiz.android.ChatTab
import org.asyncttwiz.android.chatDay
import org.asyncttwiz.android.chatTime
import org.junit.Test
import java.util.UUID
import kotlin.test.assertNotNull

/** Owner, 2026-11-14 (LOB-067, LOB-164): game and pod chat messages show a small time, with a date line between days. */
class ChatTimestampTest : E2e() {
    // LOB-067, LOB-164, LOB-145, DRF-170: Alice writes in their pod game's chat and in the pod's; in Bob's chat each message shows its
    // time, under its day's line, on the Game tab and on the Pod tab
    @Test fun gameAndPodMessagesShowTheirTimeUnderTheirDay() {
        launchPlayers()
        val name = "E2E chat times ${UUID.randomUUID().toString().take(6)}"
        val game = ServerSetup.readyBoth(alice.account, bob.account, ServerSetup.podGame(alice.account, bob.account, name))
        val pod = assertNotNull(game.podId)
        ServerSetup.gameChat(alice.account, game, "Good luck!")
        val podMessage = ServerSetup.podChat(alice.account, pod, "Rematch after this one?")
        bob.openGame(game, this)
        waitUntil("the chat dot on Bob's table") { bob.lobby.state.chat.messages.any { it.text == "Good luck!" } }
        val gameMessage = bob.lobby.state.chat.messages.single { it.text == "Good luck!" }
        bob.described("Chat, ", substring = true).performClick()
        if (bob.lobby.state.chatTab != ChatTab.GAME) bob.tagged("chat-tab-game").performClick()
        waitUntil("Alice's game message with its time under its day") {
            bob.showsText("Good luck!") && bob.shows(hasTestTag("chat-time") and hasText(chatTime(gameMessage.createdAt))) &&
                bob.shows(hasTestTag("chat-day") and androidx.compose.ui.test.hasAnyDescendant(hasText(chatDay(gameMessage.createdAt))))
        }
        bob.tagged("chat-tab-pod").performClick()
        waitUntil("Alice's pod message with its time under its day") {
            bob.showsText("Rematch after this one?") && bob.shows(hasTestTag("chat-time") and hasText(chatTime(podMessage.createdAt))) &&
                bob.shows(hasTestTag("chat-day") and androidx.compose.ui.test.hasAnyDescendant(hasText(chatDay(podMessage.createdAt))))
        }
    }
}
