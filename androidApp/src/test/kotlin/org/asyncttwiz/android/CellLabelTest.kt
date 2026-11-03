package org.asyncttwiz.android

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class CellLabelTest {
    @get:Rule val compose = createComposeRule()

    private fun layout(text: String, fontScale: Float): TextLayoutResult {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                // A 48 dp menu cell: the 24 dp icon above, the label below it (2 dp side padding)
                Column(Modifier.size(48.dp)) {
                    Spacer(Modifier.size(24.dp))
                    CellLabel(text, Color.Black, Modifier.size(44.dp, 24.dp))
                }
            }
        }
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.single()
    }

    // TBL-064: at 1.3× system text a label never breaks inside a word ("Graveyar d"); it is drawn smaller instead
    @Test fun aLongWordStaysOnOneLineAtLargeText() {
        val result = layout("Graveyard", 1.3f)
        assertEquals(1, result.lineCount)
        assertTrue(result.getLineRight(0) - result.getLineLeft(0) <= result.layoutInput.constraints.maxWidth)
    }

    // TBL-064: two words wrap between the words and both lines fit the cell
    @Test fun twoWordsWrapBetweenThemAndFit() {
        val result = layout("Shuffle into", 1.3f)
        assertFalse(result.didOverflowHeight)
        if (result.lineCount == 2) assertEquals("Shuffle ", "Shuffle into".substring(0, result.getLineEnd(0)))
    }
}
