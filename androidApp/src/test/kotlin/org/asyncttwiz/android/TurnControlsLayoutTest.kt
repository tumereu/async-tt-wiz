package org.asyncttwiz.android

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import org.asyncttwiz.protocol.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class TurnControlsLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun pickerLabelsFitAt412dp() = picker()
    @Test @Config(qualifiers = "w360dp-h800dp-port-mdpi") fun pickerLabelsFitAt360dp() = picker()
    @Test fun advanceLabelsFitAt412dp() = advance()
    @Test @Config(qualifiers = "w360dp-h800dp-port-mdpi") fun advanceLabelsFitAt360dp() = advance()

    private fun assertFits(label: String): androidx.compose.ui.geometry.Rect {
        val node = compose.onNodeWithText(label, useUnmergedTree = true).assertIsDisplayed()
        val results = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        val text = results.single()
        assertFalse(text.hasVisualOverflow, "Clipped label: $label, size=${text.size}, paragraph=${text.multiParagraph.width}x${text.multiParagraph.height}")
        assertTrue(text.lineCount <= 2, label)
        return node.fetchSemanticsNode().boundsInRoot
    }

    private fun picker() {
        val play = TableToolFixture.play()
        val controller = TableActionController({ play }, { true }, { true }, RecordingRoutes())
        compose.setContent { MaterialTheme {
            PhaseStepPicker(play, play.board.turn.accountId, Color.Blue, controller, {})
        } }
        for (step in TurnPosition.entries) {
            val text = assertFits(stepLabel(step))
            val square = compose.onNodeWithContentDescription(step.label).fetchSemanticsNode().boundsInRoot
            assertEquals(48f, square.width, .5f)
            assertTrue(text.left >= square.left && text.right <= square.right, step.label)
            assertTrue(text.top >= square.top && text.bottom <= square.bottom, step.label)
        }
        assertEquals("Begin combat", stepLabel(TurnPosition.BEGIN_COMBAT))
        assertEquals("End combat", stepLabel(TurnPosition.END_COMBAT))
    }

    private fun advance() {
        val state = mutableStateOf(TableToolFixture.play())
        val controller = TableActionController({ state.value }, { true }, { true }, RecordingRoutes())
        compose.setContent { MaterialTheme {
            QuickActionStrip(state.value, QuickContext(viewer = state.value.board.turn.accountId), controller,
                Color.Blue, Modifier.fillMaxWidth())
        } }
        for (step in TurnPosition.entries) {
            compose.runOnIdle { state.value = TableToolFixture.play(step) }
            val label = nextStepLabel(state.value.board.turn)
            val text = assertFits(label)
            val arrow = compose.onNodeWithContentDescription(label).fetchSemanticsNode().boundsInRoot
            assertTrue(arrow.width in 68f..72f)
            assertTrue(text.left >= arrow.left && text.right <= arrow.right - 12f, label)
            assertTrue(text.top >= arrow.top + 20f && text.bottom <= arrow.bottom, label)
        }
    }
}
