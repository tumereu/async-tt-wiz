package org.asyncttwiz.android

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** I12b2 runs this against its normal UI-created unstarted game and integrated I12a backend.
 * No business-state API fixture, request injection or chosen server outcome. */
class SetupCancellationTest {
    @get:Rule val app = createAndroidComposeRule<MainActivity>()

    @Test fun productionTitleRowHoldAccessibilityAndLocalDismissalBeforeRealCancellation() {
        lateinit var model: AuthViewModel
        app.runOnUiThread { model = ViewModelProvider(app.activity)[AuthViewModel::class.java] }
        val name = InstrumentationRegistry.getArguments().getString("cancellationGameName") ?: "Native Cancel Spark"
        app.waitUntil(30_000) { model.controller.state.account != null && model.lobby.state.games.any { it.gameName == name } }
        val captured = model.lobby.state.games.single { it.gameName == name }
        fun nativeBack() = InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        fun row() = app.onAllNodes(hasText(name) and hasClickAction()).onLast()
        fun accessibleCancel() {
            val actions = row().fetchSemanticsNode().config[SemanticsActions.CustomActions]
            app.runOnUiThread { assertTrue(actions.single { it.label == "Cancel game" }.action()) }
        }
        fun unchanged() {
            app.runOnIdle {
                assertFalse(captured.id in model.lobby.state.pendingGames)
                assertEquals(captured.revision, model.lobby.state.games.single { it.id == captured.id }.revision)
            }
        }
        for ((orientation, expected) in listOf(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT to Configuration.ORIENTATION_PORTRAIT,
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE to Configuration.ORIENTATION_LANDSCAPE)) {
            app.runOnUiThread { app.activity.requestedOrientation = orientation }
            app.waitUntil(15_000) { app.activity.resources.configuration.orientation == expected }
            app.runOnUiThread { model = ViewModelProvider(app.activity)[AuthViewModel::class.java] }
            if (expected == Configuration.ORIENTATION_PORTRAIT) app.onNodeWithContentDescription("Open navigation").performClick()
            app.onAllNodes(hasText(name) and hasClickAction()).onFirst().performTouchInput { longClick() }
            app.runOnIdle { assertNull(model.lobby.state.selected) }
            app.onNodeWithContentDescription("Close game actions").performClick(); unchanged()
            if (expected == Configuration.ORIENTATION_PORTRAIT) app.onNodeWithContentDescription("Close navigation").performClick()
            // The journey enters Games through normal navigation before this phase.
            row().performTouchInput { longClick() }
            app.runOnIdle { assertNull(model.lobby.state.selected) }
            app.onNodeWithText("Cancel game").performClick()
            app.onNodeWithText("Keep game").performClick(); unchanged()
            accessibleCancel()
            app.onNodeWithContentDescription("Close cancellation").performClick(); unchanged()
            row().performTouchInput { longClick() }
            app.onNodeWithText("Cancel game").performClick(); nativeBack()
            app.onNodeWithText("Keep game").assertDoesNotExist(); unchanged()
            row().performClick()
            app.waitUntil(15_000) { model.lobby.state.detail?.id == captured.id }
            app.onNodeWithText("Cancel", substring = false).performClick()
            app.onNodeWithText("Keep game").performClick(); unchanged()
            nativeBack()
            app.waitUntil(15_000) { model.lobby.state.selected == null }
        }
        accessibleCancel()
        app.runOnIdle {
            assertEquals(captured.id, model.lobby.setupCancellation.confirmation?.id)
            assertEquals(captured.preparationRound, model.lobby.setupCancellation.confirmation?.preparationRound)
        }
        app.onNodeWithText("Cancel game", substring = false).performClick()
        app.onNodeWithText("Keep game").assertDoesNotExist()
        app.waitUntil(30_000) { model.lobby.state.games.none { it.id == captured.id } && captured.id !in model.lobby.state.pendingGames }
        app.runOnIdle { assertNull(model.lobby.setupCancellation.confirmation) }
    }
}
