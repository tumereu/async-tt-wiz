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

    // TRN-007, TRN-008, TRN-017
    @Test fun pickerLabelsFitAt412dp() = picker()
    @Test @Config(qualifiers = "w360dp-h800dp-port-mdpi") fun pickerLabelsFitAt360dp() = picker()
    @Test fun advanceLabelsFitAt412dp() = advance()
    @Test @Config(qualifiers = "w360dp-h800dp-port-mdpi") fun advanceLabelsFitAt360dp() = advance()

    private fun assertFits(label: String, maxLines: Int = 2, within: String? = null): androidx.compose.ui.geometry.Rect {
        // Two squares read "End" (End of combat, End step): each label is found inside its own square.
        val matcher = if (within == null) hasText(label) else hasText(label) and hasAnyAncestor(hasContentDescription(within))
        val node = compose.onNode(matcher, useUnmergedTree = true).assertIsDisplayed()
        val results = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        val text = results.single()
        assertFalse(text.hasVisualOverflow, "Clipped label: $label, size=${text.size}, paragraph=${text.multiParagraph.width}x${text.multiParagraph.height}")
        assertTrue(text.lineCount <= maxLines, "$label: ${text.lineCount} lines")
        return node.fetchSemanticsNode().boundsInRoot
    }

    private fun picker() {
        // In the first-strike damage step (TRN-056) the dialog shows all thirteen squares
        val play = TableToolFixture.play(TurnPosition.FIRST_STRIKE_DAMAGE)
        val controller = TableActionController({ tableBoard(play) }, { true }, RecordingRoutes())
        compose.setContent { MaterialTheme {
            PhaseStepPicker(tableBoard(play), play.board.turn.accountId, Color.Blue, {})
        } }
        // TRN-008: every step square's short name sits on one line.
        for (step in TurnPosition.entries) {
            val text = assertFits(stepLabel(step), maxLines = 1, within = step.label)
            val square = compose.onNodeWithContentDescription(step.label).fetchSemanticsNode().boundsInRoot
            assertEquals(48f, square.width, .5f)
            assertTrue(text.left >= square.left && text.right <= square.right, step.label)
            assertTrue(text.top >= square.top && text.bottom <= square.bottom, step.label)
        }
        assertEquals(listOf("Untap", "Upkeep", "Draw", "Main 1", "Begin", "Attackers", "Blockers", "1st strike", "Damage", "End", "Main 2", "End", "Cleanup"),
            TurnPosition.entries.map(::stepLabel))
    }

    private fun advance() {
        val state = mutableStateOf(TableToolFixture.play())
        val controller = TableActionController({ tableBoard(state.value) }, { true }, RecordingRoutes())
        compose.setContent { MaterialTheme {
            QuickActionStrip(tableBoard(state.value), QuickContext(viewer = state.value.board.turn.accountId), controller,
                Color.Blue, Modifier.fillMaxWidth())
        } }
        for (step in TurnPosition.entries) {
            compose.runOnIdle { state.value = TableToolFixture.play(step) }
            val label = AutomatumStep.of(tableBoard(state.value)).label
            val text = assertFits(label)
            val arrow = compose.onNodeWithContentDescription(label).fetchSemanticsNode().boundsInRoot
            // TRN-017 (owner playtest 2026-11-06): the main button keeps one width, 112 dp, on a small phone too
            assertEquals(112f, arrow.width, .5f, label)
            assertTrue(text.left >= arrow.left && text.right <= arrow.right - 12f, label)
            assertTrue(text.top >= arrow.top + 20f && text.bottom <= arrow.bottom, label)
        }
    }
}
