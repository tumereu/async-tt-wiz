package org.asyncttwiz.e2e

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import org.asyncttwiz.android.ChatTab
import org.asyncttwiz.android.ShellDestination
import org.junit.Test
import java.util.UUID
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

/**
 * Pod chat (owner, 2026-11-08), the overall flow: Alice writes in her pod's chat; Bob's Pods screen and pod screen show the
 * dot, and reading the chat clears it; in their pod game a pod message lights Bob's chat button without a bubble, the chat
 * opens on Pod, and the Pod tab is highlighted while he is on Game; a game message's bubble he taps counts as seen. The chat
 * opens on the tab last used in the game (owner, 2026-11-10), and a pod message sends no notification.
 */
class PodChatTest : E2e() {
    private val newMessages = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "New messages")
    private val podTab = hasTestTag("chat-tab-pod")
    private val gameTab = hasTestTag("chat-tab-game")

    // DRF-170, DRF-012, DRF-171, LOB-145, LOB-066, LOB-136
    @Test fun aPodMessageShowsOnThePodScreensAndInAPodGamesChat() {
        launchPlayers()
        val name = "E2E pod chat ${UUID.randomUUID().toString().take(6)}"
        val game = ServerSetup.readyBoth(alice.account, bob.account, ServerSetup.podGame(alice.account, bob.account, name))
        val pod = assertNotNull(game.podId)

        // Alice writes from her pod screen's chat
        alice.auth.navigate(ShellDestination.PODS)
        alice.pods.open(pod)
        waitUntil("Alice's pod open") { alice.shows(hasTestTag("pod-chat-button")) }
        alice.tagged("pod-chat-button").performClick()
        waitUntil("Alice's pod chat") { alice.shows(hasContentDescription("Pod chat message draft")) }
        alice.described("Pod chat message draft").performTextInput("Good draft! Ready for our match?")
        waitUntil("Alice's draft") { alice.pods.chat.view(pod).draft.isNotEmpty() }
        alice.described("Send").performClick()
        waitUntil("Alice's message stored") { alice.pods.chat.view(pod).messages.any { it.text == "Good draft! Ready for our match?" } }
        alice.described("Close chat").performClick()

        // Bob: the dot on the pod's row and on its chat button; seeing the message in the chat reads it
        bob.auth.navigate(ShellDestination.PODS)
        waitUntil("the dot on Bob's pod row") { bob.shows(hasTestTag("pod-chat-unread-$pod")) }
        bob.text("$name pod").performScrollTo().performClick()
        waitUntil("the dot on Bob's pod chat button") { bob.shows(hasTestTag("pod-chat-unread")) }
        bob.tagged("pod-chat-button").performClick()
        waitUntil("Alice's message in Bob's pod chat, read") {
            bob.showsText("Good draft! Ready for our match?") && bob.pods.chat.view(pod).unread(bob.id) == 0 &&
                bob.pods.chat.view(pod).read?.unread == 0
        }
        bob.described("Close chat").performClick()
        waitUntil("the dot gone from Bob's chat button") { !bob.shows(hasTestTag("pod-chat-unread")) }

        // The pod game: a pod message lights the table's chat button and shows no bubble
        bob.auth.navigate(ShellDestination.MENU)
        bob.openGame(game, this)
        ServerSetup.podChat(alice.account, pod, "Take your time")
        waitUntil("the chat dot on Bob's table") { bob.shows(hasContentDescription("Chat, 1 unread messages")) }
        assertFalse(bob.shows(hasTestTag("chat-bubble")), "a pod message shows no bubble: ${bob.screen()}")
        // Only the pod's chat holds an unread message: the chat opens on Pod and reads it
        bob.described("Chat, ", substring = true).performClick()
        waitUntil("Bob's chat on the Pod tab with Alice's message, read") {
            bob.lobby.state.chatTab == ChatTab.POD && bob.showsText("Take your time") && bob.pods.chat.view(pod).unread(bob.id) == 0
        }
        // On the Game tab, a new pod message highlights Pod
        bob.node(gameTab).performClick()
        waitUntil("Bob's Game tab") { bob.lobby.state.chatTab == ChatTab.GAME }
        ServerSetup.podChat(alice.account, pod, "One more thing")
        waitUntil("the Pod tab highlighted while Bob is on Game") { bob.shows(podTab and newMessages) }
        bob.node(podTab).performClick()
        waitUntil("the message on the Pod tab, no longer highlighted") {
            bob.showsText("One more thing") && !bob.shows(podTab and newMessages) && bob.pods.chat.view(pod).unread(bob.id) == 0
        }
        bob.described("Close chat").performClick()

        // A game message's bubble: tapping it is seeing it, so the Game tab isn't highlighted afterwards
        ServerSetup.gameChat(alice.account, game, "Your move")
        waitUntil("Alice's bubble on Bob's table") { bob.shows(hasTestTag("chat-bubble")) }
        bob.node(hasTestTag("chat-bubble")).performClick()
        waitUntil("Bob's chat dot cleared") { bob.shows(hasContentDescription("Chat, 0 unread messages")) }
        bob.described("Chat, ", substring = true).performClick()
        // LOB-145 (owner, 2026-11-10): it opens on Pod, the tab he used last in this game
        waitUntil("Bob's chat open on Pod") { bob.lobby.state.chatTab == ChatTab.POD && bob.shows(gameTab) }
        assertFalse(bob.shows(gameTab and newMessages), "the tapped bubble's message counts as seen: ${bob.screen()}")
    }

    // DRF-170, DRF-012 (owner, 2026-11-10): a pod message sends no notification: the server records no notice for the other
    // member, so nothing reaches their phone's notifications, while the in-app dot on the Pods list still shows it
    @Test fun aPodMessageSendsNoNotification() {
        launchPlayers()
        val name = "E2E pod chat quiet ${UUID.randomUUID().toString().take(6)}"
        val game = ServerSetup.readyBoth(alice.account, bob.account, ServerSetup.podGame(alice.account, bob.account, name))
        val pod = assertNotNull(game.podId)
        val before = ServerSetup.notices(bob.account, pod)
        val hers = ServerSetup.notices(alice.account, pod)
        ServerSetup.podChat(alice.account, pod, "Anyone up for a rematch?")
        bob.auth.navigate(ShellDestination.PODS)
        waitUntil("the dot on Bob's pod row") { bob.shows(hasTestTag("pod-chat-unread-$pod")) }
        kotlin.test.assertEquals(before, ServerSetup.notices(bob.account, pod), "no notice of the message for Bob")
        kotlin.test.assertEquals(hers, ServerSetup.notices(alice.account, pod), "none for Alice either")
    }

    // LOB-145 (owner, 2026-11-10): a pod game's chat opens on the tab last used in that game, the other tab keeping its dot;
    // Write and holding a bubble still open Game, which is then the tab last used
    @Test fun aPodGamesChatOpensOnTheTabLastUsed() {
        launchPlayers()
        val name = "E2E chat tab ${UUID.randomUUID().toString().take(6)}"
        val game = ServerSetup.readyBoth(alice.account, bob.account, ServerSetup.podGame(alice.account, bob.account, name))
        bob.openGame(game, this)
        // Nothing unread: it opens on Game; Bob goes to Pod and closes the chat there
        bob.described("Chat, ", substring = true).performClick()
        waitUntil("Bob's chat on Game") { bob.lobby.state.chatTab == ChatTab.GAME && bob.shows(podTab) }
        bob.node(podTab).performClick()
        waitUntil("Bob's Pod tab") { bob.lobby.state.chatTab == ChatTab.POD }
        bob.described("Close chat").performClick()
        // A game message: the chat opens on Pod all the same, the Game tab with its dot
        ServerSetup.gameChat(alice.account, game, "Ready when you are")
        waitUntil("Alice's bubble on Bob's table") { bob.shows(hasTestTag("chat-bubble")) }
        bob.described("Chat, ", substring = true).performClick()
        waitUntil("Bob's chat on Pod, Game with its dot") { bob.lobby.state.chatTab == ChatTab.POD && bob.shows(gameTab and newMessages) }
        bob.described("Close chat").performClick()
        waitUntil("Bob's chat closed") { !bob.lobby.state.chatOpen }
        // Holding the bubble opens Game (its long press, as the semantics action: the bubble goes as the chat reads it, before a
        // finger could lift)
        bob.node(hasTestTag("chat-bubble")).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnLongClick)
        waitUntil("Bob's chat on Game from the bubble") { bob.lobby.state.chatOpen && bob.lobby.state.chatTab == ChatTab.GAME }
        bob.described("Close chat").performClick()
        waitUntil("Bob's chat closed again") { !bob.lobby.state.chatOpen }
        // Game is now the tab last used: the chat button opens it
        bob.described("Chat, ", substring = true).performClick()
        waitUntil("Bob's chat on Game, last used") { bob.lobby.state.chatOpen && bob.lobby.state.chatTab == ChatTab.GAME }
        bob.node(podTab).performClick()
        waitUntil("Bob's Pod tab again") { bob.lobby.state.chatTab == ChatTab.POD }
        bob.described("Close chat").performClick()
        waitUntil("Bob's chat closed a third time") { !bob.lobby.state.chatOpen }
        // Write in the Life panel opens Game whatever was used last
        bob.node(hasContentDescription("${bob.name}, life 20", substring = true)).performClick()
        waitUntil("Bob's Life panel") { bob.shows(hasContentDescription("Quick messages")) }
        pastPopupGuard()
        bob.described("Write a message").performClick()
        waitUntil("Bob's chat on Game from Write") { bob.lobby.state.chatOpen && bob.lobby.state.chatTab == ChatTab.GAME }
    }
}
