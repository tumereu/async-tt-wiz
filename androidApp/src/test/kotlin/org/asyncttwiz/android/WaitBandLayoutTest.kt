package org.asyncttwiz.android

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.style.TextOverflow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * TRN-066, STK-057 (owner gallery review 2026-11-10, gallery 79: "white text ends up on a largely faded area"): the band stays
 * edge to edge with fading sides, but every line of its words, the longest main line and 8 lines of effect text alike, lies on
 * its solid middle, in portrait on a narrow phone and in landscape.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class WaitBandLayoutTest {
    @get:Rule val compose = createComposeRule()

    private val main = "Bob is choosing · Shattered Reason's cycling trigger, and while he does nothing else can happen here at all"
    private val detail = List(30) { "each opponent mills 4 cards, then returns a creature card from their graveyard to their hand" }
        .joinToString(" · ")

    private fun render() {
        compose.setContent { MaterialTheme {
            Box(Modifier.fillMaxSize().testTag("screen"), contentAlignment = Alignment.TopCenter) {
                WaitBandStrip(Modifier) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(main, Modifier.testTag("band-line"), color = Color.White, style = WaitBandText, maxLines = 3,
                            overflow = TextOverflow.Ellipsis)
                        Text(detail, Modifier.testTag("band-line"), color = Color.White, style = WaitBandDetailText, maxLines = 8,
                            overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        } }
        compose.waitForIdle()
    }

    private fun assertOnTheSolidMiddle() {
        val band = compose.onNodeWithTag("wait-band").fetchSemanticsNode().boundsInRoot
        val width = compose.onNodeWithTag("screen").fetchSemanticsNode().boundsInRoot.width
        assertEquals(width, band.width, "edge to edge")
        val lines = compose.onAllNodesWithTag("band-line").fetchSemanticsNodes()
        assertEquals(2, lines.size)
        for (line in lines) {
            val b = line.boundsInRoot
            assertTrue(b.left >= band.left + WAIT_BAND_FADE && b.right <= band.right - WAIT_BAND_FADE, "on the solid middle: $b in $band")
        }
        assertTrue(lines.last().boundsInRoot.height >= 8 * 13f, "8 lines of effect text: ${lines.last().boundsInRoot}")
    }

    @Config(qualifiers = "w320dp-h640dp-port-mdpi")
    @Test fun aNarrowPortraitPhoneKeepsEveryLineOnTheSolidMiddle() {
        render()
        assertOnTheSolidMiddle()
        compose.onRoot().captureShot("$SCREENSHOTS/engine/wait-band-narrow.png",
            "The wait band on a 320 dp phone with its longest words: three lines of who acts and eight lines of effect text, every line on " +
                "the band's solid middle, only the narrow side margins fading out.")
    }

    @Config(qualifiers = "w892dp-h412dp-land-mdpi")
    @Test fun landscapeKeepsEveryLineOnTheSolidMiddle() {
        render()
        assertOnTheSolidMiddle()
    }
}
