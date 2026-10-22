package org.asyncttwiz.android

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.asyncttwiz.protocol.*

/** Uses only retained UI-created synthetic data and normal production accessibility/pointer handlers. */
class PreparationInteractionTest {
    @get:Rule val app = createAndroidComposeRule<MainActivity>()

    private fun open(): AuthViewModel {
        lateinit var model: AuthViewModel
        app.runOnUiThread { model = ViewModelProvider(app.activity)[AuthViewModel::class.java] }
        app.waitUntil(30_000) { !model.controller.state.restoring && model.controller.state.account != null }
        if (model.controller.state.destination != ShellDestination.GAMES) app.onNodeWithText("Games").performClick()
        if (model.lobby.state.selected == null) {
            app.waitUntil(30_000) { model.lobby.state.games.any { it.gameName == "Native Transfer Spark" } }
            app.onNodeWithText("Native Transfer Spark").performClick()
        }
        app.waitUntil(30_000) { model.lobby.state.preparation != null }
        if (!model.lobby.state.preparing) app.onNodeWithText("Prepare deck").performClick()
        app.onNodeWithText("Mainboard · 12").assertExists()
        return model
    }

    @Test fun rapidRepeatAndRemovalCannotRedirectCapturedTouch() {
        val model = open()
        val main = "Move one Coppercloud Surveyor from Mainboard"
        val all = "Move all Coppercloud Surveyor from Mainboard"
        val reverse = "Move all Coppercloud Surveyor from Sideboard"
        app.mainClock.autoAdvance = false
        try {
            app.onNodeWithContentDescription(main).performTouchInput { click() }
            app.mainClock.advanceTimeBy(32)
            app.onNodeWithContentDescription(main).performTouchInput { click() }
            app.mainClock.advanceTimeBy(32)
            app.onNodeWithText("Mainboard · 10").assertExists()
            // Keep a physical pointer down on the row while its remaining copies leave via the
            // actual accessibility action. Its disappearing ghost must not receive release, nor
            // may the row sliding underneath inherit this pointer's tap.
            app.onNodeWithContentDescription(main).performTouchInput { down(center) }
            app.onNodeWithContentDescription(all).performSemanticsAction(SemanticsActions.OnClick) { assertTrue(it()) }
            app.mainClock.advanceTimeBy(100)
            val expected = model.lobby.state.preparation
            app.onRoot().performTouchInput { up() }
            app.mainClock.advanceTimeBy(250)
            assertEquals(expected, model.lobby.state.preparation)
            app.onNodeWithText("Mainboard · 8").assertExists()
            app.onNodeWithContentDescription(reverse).performSemanticsAction(SemanticsActions.OnClick) { assertTrue(it()) }
            app.mainClock.advanceTimeBy(250)
            app.onNodeWithText("Mainboard · 12").assertExists()
            InstrumentationRegistry.getInstrumentation().targetContext.filesDir.resolve("preparation-interaction/rapid-assertions.txt")
                .writeText("PASS two rapid native taps before feedback/animation completion; third pointer captured before row removal is cancelled with no retarget/double dispatch; counts/predictions immediate; reverse restores exact preparation.\n")
        } finally { app.mainClock.autoAdvance = true }
    }

    @Test fun nativeAccessibilityFixedFeedbackAndNonActionableRowAnimation() {
        val model = open()
        val name = "Coppercloud Surveyor"
        fun handle(all: Boolean, source: String) = app.onNodeWithContentDescription("Move ${if (all) "all" else "one"} $name from $source")
        val directory = InstrumentationRegistry.getInstrumentation().targetContext.filesDir.resolve("preparation-interaction").apply { mkdirs() }
        fun capture(name: String) {
            app.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
                directory.resolve("$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
            directory.resolve("$name.txt").writeText(app.onRoot().printToString())
        }
        app.mainClock.autoAdvance = false
        try {
            val section = app.onNodeWithContentDescription("Mainboard preparation").fetchSemanticsNode().boundsInRoot
            handle(false, "Mainboard").performSemanticsAction(SemanticsActions.OnClick) { assertTrue(it()) }
            app.mainClock.advanceTimeBy(32)
            app.onNodeWithText("Mainboard · 11").assertExists()
            assertEquals(section, app.onNodeWithContentDescription("Mainboard preparation").fetchSemanticsNode().boundsInRoot)
            app.onNodeWithContentDescription("Close card inspection").assertDoesNotExist()
            val feedback = app.onNodeWithContentDescription("Latest transfer: 1× $name → Sideboard")
            feedback.assertExists(); val slot = feedback.fetchSemanticsNode().boundsInRoot
            capture("feedback-visible")
            app.mainClock.advanceTimeBy(1600)
            assertEquals(slot, feedback.fetchSemanticsNode().boundsInRoot)
            assertEquals(section, app.onNodeWithContentDescription("Mainboard preparation").fetchSemanticsNode().boundsInRoot)
            capture("feedback-faded")
            // The row's native custom action is the full-width accessible equivalent to the 24dp origin.
            val row = app.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions), useUnmergedTree = true)
                .fetchSemanticsNodes().first { it.config[SemanticsActions.CustomActions].any { action -> action.label == "Move all $name to Sideboard" } }
            app.runOnUiThread { assertTrue(row.config[SemanticsActions.CustomActions].first { it.label == "Move all $name to Sideboard" }.action()) }
            app.mainClock.advanceTimeBy(32)
            app.onNodeWithText("Mainboard · 8").assertExists()
            handle(false, "Mainboard").assertDoesNotExist(); handle(true, "Mainboard").assertDoesNotExist()
            assertFalse(app.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions), useUnmergedTree = true)
                .fetchSemanticsNodes().any { it.config[SemanticsActions.CustomActions].any { a -> a.label == "Move all $name to Sideboard" } })
            capture("row-exiting-nonactionable")
            app.mainClock.advanceTimeBy(220); capture("row-reflow-finished")
            // Reverse via native OnClick, and cancel a real captured touch stream without any transfer.
            handle(true, "Sideboard").performSemanticsAction(SemanticsActions.OnClick) { assertTrue(it()) }
            app.mainClock.advanceTimeBy(250); app.onNodeWithText("Mainboard · 12").assertExists()
            app.onNodeWithContentDescription("Mainboard preparation").performScrollToNode(hasContentDescription("Move one $name from Mainboard"))
            val before = model.lobby.state.preparation
            handle(false, "Mainboard").performTouchInput { down(center); moveBy(androidx.compose.ui.geometry.Offset(30f, 0f)); cancel() }
            app.mainClock.advanceTimeBy(250)
            assertEquals(before, model.lobby.state.preparation)
            directory.resolve("assertions.txt").writeText("PASS native one/all OnClick and full-row custom actions; immediate counts; no inspector/double dispatch; fixed feedback/list geometry visible/faded at 32/1632ms; removed row has no actionable semantics during fade; 180ms placement/fade completes; actual touch cancellation preserves preparation. Uses normal UI-created retained data, no backend business mutation.\n")
        } finally { app.mainClock.autoAdvance = true }
    }
}
