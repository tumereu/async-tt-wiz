package org.asyncttwiz.android

import androidx.compose.material3.MaterialTheme
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.asyncttwiz.protocol.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w892dp-h412dp-land-mdpi")
class TableChatButtonTest {
    @get:Rule val compose = createComposeRule()

    private fun drain(f: BasicTableFixture) {
        repeat(4) { compose.runOnIdle { f.worker.drain() }; compose.waitForIdle() }
    }

    private lateinit var view: View

    /** Pixels of the chat button drawn in the unread dot's colour (the dot is a sibling drawn over the button). */
    private fun dotPixels(label: String): Int {
        val bounds = compose.onNodeWithContentDescription(label).fetchSemanticsNode().boundsInRoot
        val image = compose.runOnIdle {
            Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
        }
        val dot = OpponentTableColor.toArgb()
        fun channel(color: Int, shift: Int) = (color shr shift) and 0xff
        var count = 0
        for (x in bounds.left.toInt() until bounds.right.toInt()) for (y in bounds.top.toInt() until bounds.bottom.toInt()) {
            val c = image.getPixel(x, y)
            if (listOf(16, 8, 0).all { abs(channel(c, it) - channel(dot, it)) <= 8 }) count++
        }
        return count
    }

    // LOB-066
    @Test fun chatButtonOpensGameChatSidePanelAndShowsADotOnlyWhileMessagesAreUnread() {
        val f = BasicTableFixture()
        val incoming = ChatMessage("incoming", f.game.id, f.game.playId!!, f.other, "Hello there", "1", "date")
        try {
            // Connect as BasicTableFixture.connect does, with one unread message from the opponent in the head.
            f.lobby.open(f.game.id); f.worker.drain(); f.wire.listener.opened()
            f.message(GameWelcome(gameplayRulesVersion = CURRENT_GAMEPLAY_RULES_VERSION))
            f.message(GameStreamReady(f.game.id, f.wire.frames.filterIsInstance<OpenGameStream>().last().streamId, "1"))
            f.message(f.data.head(f.root).copy(chat = listOf(incoming)))
            assertTrue(f.game.id in f.lobby.state.connectedGames)
            compose.setContent { view = LocalView.current; MaterialTheme { TableScreen(f.lobby, f.actor, {}, true) } }
            drain(f)
            assertTrue(dotPixels("Chat, 1 unread messages") > 0, "Unread messages show a dot on the button")
            compose.onNodeWithText("Game chat").assertDoesNotExist()
            compose.onNodeWithContentDescription("Chat, 1 unread messages")
                .performSemanticsAction(SemanticsActions.OnClick) { assertTrue(it()) }
            drain(f)
            compose.runOnIdle { assertTrue(f.lobby.state.chatOpen) }
            compose.onNodeWithText("Hello there").assertIsDisplayed()
            // A side panel: it hugs the right edge and leaves most of the landscape table visible.
            val panel = compose.onNode(hasText("Game chat")).onParent().fetchSemanticsNode().boundsInWindow
            assertTrue(panel.left > 892f / 2, "Panel starts in the right half: $panel")
            assertTrue(panel.right > 892f - 24, "Panel reaches the right edge: $panel")
            compose.onNodeWithContentDescription("Close chat").performClick()
            drain(f)
            compose.onNodeWithText("Game chat").assertDoesNotExist()
            // Reading the message clears the count, and with it the dot.
            compose.onNodeWithContentDescription("Chat, 0 unread messages").assertIsDisplayed()
            assertEquals(0, dotPixels("Chat, 0 unread messages"), "No unread messages, no dot")
        } finally { f.lobby.close(); f.worker.drain() }
    }
}
