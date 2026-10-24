package org.asyncttwiz.android

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import org.asyncttwiz.protocol.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Actual MainActivity normal route, not an unmounted component. Authoring/compilation only here;
 * the native resource owner supplies a signed-in UI-created active game before executing. */
class HistoryUiSpineTest {
    @get:Rule val app = createAndroidComposeRule<MainActivity>()
    @Test fun normalRouteReviewArmCancelTreeAndCurrentRemainPersonalUntilAnOrdinaryMutation() {
        lateinit var model: AuthViewModel
        app.runOnUiThread { model = ViewModelProvider(app.activity)[AuthViewModel::class.java] }
        app.waitUntil(30_000) { model.controller.state.account != null && !model.controller.state.restoring }
        if (model.lobby.state.play == null) {
            app.waitUntil(30_000) { model.lobby.state.games.any { it.lifecycle == GameLifecycle.ACTIVE } }
            app.onNodeWithText(model.lobby.state.games.first { it.lifecycle == GameLifecycle.ACTIVE }.gameName).performClick()
        }
        val lobby = model.lobby
        app.waitUntil(30_000) { lobby.retainedTree() != null && lobby.history.playback != null }
        val accepted = lobby.state.acceptedPlay!!
        app.onNodeWithContentDescription("History", substring = false).performClick()
        app.onNodeWithContentDescription("Previous").performClick()
        app.onNodeWithText("Auto: off").assertExists()
        app.waitUntil(10_000) { lobby.history.playback?.position?.cursorId?.let(lobby.history::canArm) == true }
        app.onNodeWithContentDescription("Auto advance").performTouchInput { longClick() }
        app.onNodeWithContentDescription("Cancel continuation").assertExists().performClick()
        assertEquals(accepted, lobby.state.acceptedPlay)
        app.onNodeWithContentDescription("History", substring = false).performClick()
        app.onNodeWithContentDescription("Open game tree").performClick()
        app.onNodeWithContentDescription("Game history group tree").assertExists()
        app.onNodeWithText("Make current").assertDoesNotExist()
        app.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        app.onNodeWithContentDescription("Return to current").performClick()
        app.onNodeWithText("Auto: off").assertDoesNotExist()
        app.onNodeWithContentDescription("Undo").assertExists()
        app.onNodeWithContentDescription("Interaction mode").performClick()
        app.onNodeWithText("Redo").assertExists()
        assertEquals(accepted, lobby.state.acceptedPlay)
    }
}
