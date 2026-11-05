package org.asyncttwiz.android

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertTrue

/**
 * TRN-063, TRN-070 (owner playtest 2026-11-05 evening): the main button has room for two lines, and every name it shows
 * reads whole on at most two lines under its icon, at the width it has on a phone in portrait (and a narrow one), also with
 * large system text.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class MainButtonLabelsTest {
    @get:Rule val compose = createComposeRule()

    /** Every name the main button shows: the step arrow's, a combat division's OK, the waits' (TRN-063, TRN-070). */
    private val labels = listOf("To combat", "End turn", "Next", "Pass", "Attack (2)", "No attack", "Block (1)", "No blocks",
        "Resolve", "OK", "Assume resolves", "Assume no response", "Assume no blockers", "Assume blockers", "Cancel request")

    @OptIn(ExperimentalLayoutApi::class)
    private fun render(width: Int, fontScale: Float) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                MaterialTheme {
                    FlowRow(Modifier.testTag("buttons").width(412.dp).background(Color(0xfff3f5f5)).padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (label in labels) Column(Modifier.width(width.dp)) {
                            if (label.startsWith("Assume") || label == "Cancel request")
                                EngineWaitArrow(EngineTableRoutes.WaitAction(label, Icons.Outlined.DoneAll) {}, Modifier.width(width.dp))
                            else MainActionArrow(MainAction(label, Icons.Outlined.DoneAll) {}, true, Modifier.width(width.dp))
                        }
                    }
                }
            }
        }
    }

    /** The label [text]'s layout: on at most two lines, none of it cut off. */
    private fun readsWhole(text: String) {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNode(hasText(text) and !hasContentDescription(text), useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action!!(results)
        val layout = results.single()
        assertTrue(layout.lineCount <= 2 && !layout.hasVisualOverflow, "\"$text\" reads whole: ${layout.lineCount} lines, overflow ${layout.hasVisualOverflow}")
        // Wrapped only between words
        for (line in 0 until layout.lineCount - 1) {
            val end = layout.getLineEnd(line)
            assertTrue(text[end - 1] == ' ' || text.getOrNull(end) == ' ', "\"$text\" breaks between words")
        }
    }

    // TRN-063, TRN-070: at the portrait phone's main button width every name reads whole
    @Test fun everyNameReadsOnTwoLines() {
        render(width = 128, fontScale = 1f)
        labels.forEach(::readsWhole)
        compose.onNodeWithTag("buttons").captureShot("$SCREENSHOTS/main-button/names.png",
            "Every name the main button can show, each on a button as wide as on a phone in portrait, its words whole under the icon (the room has two lines).")
    }

    // TRN-063: on a narrow phone with large system text the names still read whole, drawn smaller where two lines don't fit
    @Test fun namesReadWholeWhenNarrowWithLargeText() {
        render(width = 108, fontScale = 1.3f)
        labels.forEach(::readsWhole)
        compose.onNodeWithTag("buttons").captureShot("$SCREENSHOTS/main-button/names-narrow-large-text.png",
            "The main button's names on a narrow phone with large system text: each reads whole on at most two lines (Assume no response and Assume no blockers on two), drawn a little smaller where needed.")
    }
}
