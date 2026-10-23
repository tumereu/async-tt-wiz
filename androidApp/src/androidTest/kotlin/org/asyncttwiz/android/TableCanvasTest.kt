package org.asyncttwiz.android

import android.content.pm.ActivityInfo
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import org.asyncttwiz.protocol.GameLifecycle
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Requires a retained game created/started by the normal two-player UI journey (I15b/I16).
 * No injected scene, setContent, backend mutation, device reset or substitute acceptance fixture.
 */
class TableCanvasTest {
    @get:Rule val app = createAndroidComposeRule<MainActivity>()

    private fun open(): AuthViewModel {
        lateinit var model: AuthViewModel
        app.runOnUiThread { model = ViewModelProvider(app.activity)[AuthViewModel::class.java] }
        app.waitUntil(30_000) { !model.controller.state.restoring && model.controller.state.account != null }
        if (model.controller.state.destination != ShellDestination.GAMES) app.onNodeWithText("Games").performClick()
        if (model.lobby.state.play == null) {
            app.waitUntil(30_000) { model.lobby.state.games.any { it.lifecycle == GameLifecycle.ACTIVE } }
            val game = model.lobby.state.games.first { it.lifecycle == GameLifecycle.ACTIVE }
            app.onNodeWithText(game.gameName).performClick()
        }
        app.waitUntil(30_000) { model.lobby.state.play != null }
        app.onNodeWithContentDescription("Shared table").assertExists()
        return model
    }

    @Test fun normalCanvasPileHitCancellationAndAccessiblePanAtBothOrientations() {
        val model = open()
        val previous = app.activity.requestedOrientation
        try {
            for (orientation in listOf(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)) {
                app.runOnUiThread { app.activity.requestedOrientation = orientation }
                app.waitForIdle()
                val play = model.lobby.state.play!!
                val owner = model.controller.state.account!!.accountId
                val person = if (model.lobby.state.detail!!.inviter.accountId == owner) model.lobby.state.detail!!.inviter else model.lobby.state.detail!!.recipient
                val library = play.board.containers.single { it.accountId == owner && it.zone.name == "LIBRARY" }
                val label = "${person.username} Library, ${library.cards.size} cards"
                val pile = app.onNodeWithContentDescription(label)
                pile.assertExists()
                val before = model.lobby.state.play
                pile.performTouchInput { down(center); moveBy(Offset(0f, -24f)); cancel() }
                app.waitForIdle(); assertEquals(before, model.lobby.state.play)
                // An actual Canvas coordinate click must reach the SAME item as native Actions semantics.
                pile.performTouchInput { click() }
                app.onNodeWithText(label).assertExists()
                app.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
                val actions = app.onNodeWithContentDescription("Shared table").fetchSemanticsNode().config[SemanticsActions.CustomActions]
                app.runOnUiThread {
                    assertTrue(actions.first { it.label == "Pan right" }.action())
                    assertTrue(actions.first { it.label == "Zoom in" }.action())
                }
                app.waitForIdle(); assertEquals(before, model.lobby.state.play)
            }
        } finally { app.runOnUiThread { app.activity.requestedOrientation = previous } }
    }
}
