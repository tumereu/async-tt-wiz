package org.asyncttwiz.android

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w320dp-h700dp-port-mdpi")
class PodTabLabelTest {
    @get:Rule val compose = createComposeRule()

    // DRF-309 (owner-reported 2026-11-25): with four tabs (Games joins while others still draft, DRF-076) on a narrow phone
    // and large system text, every tab label stays on one line, drawn smaller instead of wrapping
    @Test fun fourTabLabelsStayOnOneLine() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.3f)) {
                PodDraftTabs(listOf(PodTab.DRAFT, PodTab.BUILD, PodTab.STATS, PodTab.GAMES), PodTab.DRAFT, {}) {}
            }
        }
        for (label in listOf("Draft", "Build", "Stats", "Games")) {
            val results = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(label).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
            val layout = results.single()
            assertEquals(1, layout.lineCount, "$label is on one line")
            assertFalse(layout.didOverflowWidth, "$label fits its tab")
        }
    }
}
