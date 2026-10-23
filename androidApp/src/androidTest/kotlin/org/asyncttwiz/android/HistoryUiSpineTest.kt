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
        app.onNodeWithText("‹ Previous").performClick()
        app.onNodeWithText("Paused").assertExists()
        app.waitUntil(10_000) { lobby.history.playback?.position?.cursorId?.let(lobby.history::canArm) == true }
        app.onNodeWithText("Continue playing from here").assertIsEnabled().performClick()
        app.onNodeWithText("Cancel continuation").assertExists().performClick()
        assertEquals(accepted, lobby.state.acceptedPlay)
        app.onNodeWithContentDescription("History").performClick()
        app.onNodeWithText("Make current").assertExists()
        app.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        app.onNodeWithText("Return to current").performClick()
        app.onNodeWithText("Live").assertExists()
        app.onNodeWithContentDescription("Undo").assertExists()
        app.onNodeWithContentDescription("Interaction mode").performClick()
        app.onNodeWithText("Redo").assertExists()
        assertEquals(accepted, lobby.state.acceptedPlay)
    }
}
