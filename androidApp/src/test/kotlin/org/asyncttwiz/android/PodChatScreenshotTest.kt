package org.asyncttwiz.android

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.asyncttwiz.protocol.*
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.lang.reflect.Proxy
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pod chat (owner, 2026-11-08): the pod screens' chat button and panel, and a pod game's chat with its Game and Pod tabs.
 * The pod's chat comes from a fake server read through the real controller; nothing else is sent.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-xhdpi")
class PodChatScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private val idle = object : AbstractExecutorService() {
        override fun execute(command: Runnable) = Unit // Pod requests stay pending.
        override fun shutdown() = Unit
        override fun shutdownNow() = mutableListOf<Runnable>()
        override fun isShutdown() = false
        override fun isTerminated() = false
        override fun awaitTermination(timeout: Long, unit: TimeUnit) = true
    }
    private val direct = object : AbstractExecutorService() {
        override fun execute(command: Runnable) = command.run()
        override fun shutdown() = Unit
        override fun shutdownNow() = mutableListOf<Runnable>()
        override fun isShutdown() = false
        override fun isTerminated() = false
        override fun awaitTermination(timeout: Long, unit: TimeUnit) = true
    }
    private inline fun <reified T> unused(): T = Proxy.newProxyInstance(T::class.java.classLoader,
        arrayOf(T::class.java)) { _, method, _ -> error("Unexpected network call: ${method.name}") } as T

    private val pod = "10000000-0000-4000-8000-0000000000a1"
    private val alice = AccountIdentity("a", "Alice")
    private val bob = AccountIdentity("b", "Bob")
    private val carol = AccountIdentity("c", "Carol")
    private val messages = listOf(
        PodChatMessage("p1", pod, "b", "Good draft, everyone!", "1", "2026-11-08T18:00:00Z"),
        PodChatMessage("p2", pod, "a", "Thanks! Who wants the first match?", "2", "2026-11-08T18:01:00Z"),
        PodChatMessage("p3", pod, "c", "I'll play the winner of Alice and Bob.", "3", "2026-11-08T18:02:00Z"))
    /** The pod's chat as the server keeps it, Alice having read through [readThrough]. */
    private inner class Server(val readThrough: Int) : Pods by unused<Pods>() {
        override suspend fun chat(token: String, id: String, after: String?) = PodChatPage(pod,
            messages.filter { after == null || it.ordinal.toLong() > after.toLong() }, listOf(alice, bob, carol),
            PodChatRead(pod, readThrough.toString(), "3", messages.count { it.author != "a" && it.ordinal.toInt() > readThrough }))
        override suspend fun readChat(token: String, id: String, input: ReadChat) = PodChatRead(pod, "3", "3", 0)
        override fun close() = Unit
    }
    private fun pods(readThrough: Int) = PodController(Server(readThrough), unused<Cubes>(), { "token" }, idle, { it() },
        chatWorker = direct).also { it.chat.refresh(pod) }
    private var controller: PodController? = null
    @After fun close() { controller?.close() }

    private fun settle() { compose.mainClock.advanceTimeBy(500); compose.waitForIdle() }

    private fun showPod(detail: PodDetail, tab: PodTab, chatOpen: Boolean, readThrough: Int) {
        val pods = pods(readThrough).also { controller = it }
        pods.seed(PodState(loaded = true, selected = detail.id, detail = detail, tab = tab, chatOpen = chatOpen))
        compose.setContent { ScreenTheme { NoRipples { Surface(Modifier.fillMaxSize(), color = ScreenBackground) {
            PodsDestination(pods, alice.accountId, {}, null, {}, mainMenu = {})
        } } } }
        settle()
    }

    private fun detail(phase: PodPhase) = PodDetail(pod, "Cube night", phase, alice, DraftSettings.booster(),
        PodSource("Runes", 540), DraftSupply(135, 540),
        listOf(PodMember(alice, submitted = true), PodMember(bob, submitted = true), PodMember(carol, building = true)),
        emptyList(), draftAttemptId = "attempt", updatedAt = "2026-11-08T00:00:00Z", submitted = true)

    // DRF-170: a started pod's header has the chat button, with a dot while other members' messages wait unread
    @Test fun podHeaderChatButton() {
        showPod(detail(PodPhase.DRAFTED), PodTab.GAMES, chatOpen = false, readThrough = 1)
        compose.onNodeWithTag("pod-chat-unread", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("pod-chat-button").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "1 unread messages"))
        captureShot("$SCREENSHOTS/draft/pod-chat-button.png", "A drafted pod opened on its Games tab: the header's chat button, " +
            "before the view settings cogwheel, has a dot because Carol's message in the pod's chat is unread.")
    }

    // DRF-170: the chat button opens "Pod chat" as a side panel: each member's messages with their names, Alice's own tinted;
    // seeing them reads them, and the dot goes
    @Test fun podChatPanel() {
        showPod(detail(PodPhase.DRAFTED), PodTab.GAMES, chatOpen = false, readThrough = 1)
        compose.onNodeWithTag("pod-chat-button").performClick()
        settle()
        compose.onNodeWithText("I'll play the winner of Alice and Bob.").assertIsDisplayed()
        assertEquals(0, controller!!.chat.view(pod).unread("a"))
        captureShot("$SCREENSHOTS/draft/pod-chat.png", "Tapping the chat button of a drafted pod opens \"Pod chat\" as a side " +
            "panel: Bob's, Alice's (tinted) and Carol's messages with their names, newest at the bottom, and the message field with Send.")
    }

    private fun showTable(tab: ChatTab, gameUnread: Int, readThrough: Int) {
        val chat = pods(readThrough).also { controller = it }.chat
        val lobby = ScreenFixtures().lobby
        val game = LiveTableFixture.game.copy(podId = pod)
        val gameMessages = listOf(
            ChatMessage("g1", game.id, game.playId!!, "b", "Your move, I think.", "1", "2026-11-08T19:00:00Z"),
            ChatMessage("g2", game.id, game.playId!!, "a", "One sec", "2", "2026-11-08T19:01:00Z"))
        lobby.seed(lobby.state.copy(selected = game.id, detail = game, chatOpen = true, chatLoaded = true, connectedGames = setOf(game.id),
            chatTab = tab, chat = ChatPresentation(messages = gameMessages, unread = gameUnread)))
        compose.setContent { ScreenTheme { Surface(Modifier.fillMaxSize(), color = ScreenBackground) {
            CompositionLocalProvider(LocalPodChat provides chat) {
                MaterialTheme {
                    TableSurface(game, tableBoard(LiveTableFixture.play()), "a", false, Modifier.fillMaxSize(), connected = true)
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) { ChatPanel(lobby, "a", Modifier.fillMaxWidth()) }
                }
            }
        } } }
        settle()
    }

    // LOB-145: in a pod game the chat has Game and Pod tabs; on Game, the Pod tab has a dot while the pod's chat holds a
    // message Alice hasn't seen
    @Test fun podGameChatOnTheGameTab() {
        showTable(ChatTab.GAME, gameUnread = 0, readThrough = 2)
        compose.onNodeWithText("Your move, I think.").assertIsDisplayed()
        compose.onNodeWithTag("chat-tab-pod").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "New messages"))
        compose.onNodeWithTag("chat-tab-game").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, ""))
        captureShot("$SCREENSHOTS/chat/pod-game-chat-game-tab.png", "The chat of a pod game opened on its Game tab: the game's " +
            "messages, and a dot after Pod because Carol's message in the pod's chat is unread.")
    }

    // LOB-145: on the Pod tab the pod's messages show with their authors (reading them), and Game has a dot while a game message
    // waits unseen
    @Test fun podGameChatOnThePodTab() {
        showTable(ChatTab.POD, gameUnread = 1, readThrough = 2)
        compose.onNodeWithText("I'll play the winner of Alice and Bob.").assertIsDisplayed()
        compose.onNodeWithTag("chat-tab-game").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "New messages"))
        compose.onNodeWithTag("chat-tab-pod").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, ""))
        assertEquals(0, controller!!.chat.view(pod).unread("a"))
        captureShot("$SCREENSHOTS/chat/pod-game-chat-pod-tab.png", "Tapping Pod in a pod game's chat shows the pod's messages " +
            "with each member's name; Game has a dot because a game message is still unread.")
        compose.onNodeWithTag("chat-tab-game").performClick()
        settle()
        compose.onNodeWithText("Your move, I think.").assertIsDisplayed()
        assertTrue(compose.onAllNodesWithText("I'll play the winner of Alice and Bob.").fetchSemanticsNodes().isEmpty())
    }
}
