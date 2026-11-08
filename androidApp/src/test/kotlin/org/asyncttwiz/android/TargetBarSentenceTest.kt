package org.asyncttwiz.android

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertTrue

/** The target bar's sentence (STK-047): 4 lines, then whole on a tap, scrolling past half the table's height. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class TargetBarSentenceTest {
    @get:Rule val compose = createComposeRule()
    private val short = "Storm Spark deals 3 damage to any target."
    private val long = "Ember Cascade deals 3 damage to any target. (" + "On and on, as the longest printed reminders do, ".repeat(12) + "it goes.)"

    private fun bar(line: String) = compose.setContent {
        ScreenTheme { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            AutomatumDockBar("Choose targets", eye = true) { TargetBarTitle("Choose any target", line) }
        } }
    }
    private fun sentence(line: String) = compose.onNodeWithContentDescription("Question: $line", useUnmergedTree = true)

    // STK-047: a sentence that fits takes no taps
    @Test fun aSentenceThatFitsTakesNoTaps() {
        bar(short)
        compose.onNode(hasClickAction() and hasAnyDescendant(hasContentDescription("Question: $short")), useUnmergedTree = true).assertDoesNotExist()
    }

    // STK-047: cut at 4 lines, a tap shows it whole and another goes back
    @Test fun aLongSentenceStopsAtFourLinesAndOpensOnATap() {
        bar(long)
        val fourLines = sentence(long).fetchSemanticsNode().boundsInRoot.height
        // 13 sp a line at 1 px a dp
        assertTrue(fourLines in 50f..54f, "four lines: $fourLines")
        sentence(long).performClick()
        compose.waitForIdle()
        val whole = sentence(long).fetchSemanticsNode().boundsInRoot.height
        assertTrue(whole > fourLines, "whole: $whole, four lines: $fourLines")
        captureShot("$SCREENSHOTS/choice-source/target-bar-whole-sentence.png", "A spell whose sentence doesn't fit the target " +
            "bar's four lines, after a tap on it: the bar above the hand has grown to show the whole sentence under Choose any target, " +
            "with the eye at its left.")
        sentence(long).performClick()
        compose.waitForIdle()
        assertTrue(sentence(long).fetchSemanticsNode().boundsInRoot.height == fourLines, "back at four lines")
    }
}
