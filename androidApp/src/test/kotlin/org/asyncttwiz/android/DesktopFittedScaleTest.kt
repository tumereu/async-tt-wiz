package org.asyncttwiz.android

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Constraints
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * [DesktopFittedScale] (WEB-019) as the page's semantics mirror reads it (WEB-058): a node's bounds in the root, the layer's
 * scale applied to the node's layout, as it is drawn. At mdpi a dp is a pixel.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1440dp-h900dp-land-mdpi")
class DesktopFittedScaleTest {
    @get:Rule val compose = createComposeRule()

    // WEB-019, WEB-058 (found 2026-11-15: the mirror had the Keep / Bottom overlay drawn at 1.2× with its 1.25× layout, 11 px
    // left of its room's middle): a panel at most 450 wide, filling the height it gets while it is given less than 460 wide,
    // in a room 560 wide and 555 tall (a centred overlay's) is drawn smaller until it fits: at 1.2×, 450 × 300 drawn 540 × 360,
    // in the middle of its room; and again so after its room changes and it tries 1.3× again
    @Test fun aPanelDrawnSmallerToFitIsNarrowerThanItsRoomAndInItsMiddle() {
        val height = mutableIntStateOf(555)
        compose.setContent { Room(height.intValue) { DesktopFittedScale(1.3f) { Panel() } } }
        for (room in listOf(555, 554)) {
            compose.runOnIdle { height.intValue = room }
            val panel = compose.onNodeWithTag("panel").fetchSemanticsNode().boundsInRoot
            assertEquals(280f, panel.center.x, 1f, "in the middle of its room $room tall: $panel")
            assertEquals(450 * 1.2f, panel.width, 1f, "450 wide drawn 1.2× in its room $room tall: $panel")
            assertEquals(300 * 1.2f, panel.height, 1f, "300 tall drawn 1.2× in its room $room tall: $panel")
        }
    }

    // WEB-019, WEB-058 (found 2026-11-15): every frame on the way down to the size that fits draws the panel as it laid it out,
    // in the middle of its room (each step's size with that step's layout, never the next step's size with this one's)
    @Test fun everyFrameOnTheWayDrawsThePanelInTheMiddleOfItsRoom() {
        compose.mainClock.autoAdvance = false
        compose.setContent { Room(555) { DesktopFittedScale(1.3f) { Panel() } } }
        val widths = mutableListOf<Float>()
        repeat(6) { frame ->
            compose.mainClock.advanceTimeByFrame()
            val panel = compose.onNodeWithTag("panel").fetchSemanticsNode().boundsInRoot
            widths += panel.width
            assertEquals(280f, panel.center.x, 1f, "frame $frame in the middle of its room: $panel")
        }
        assertEquals(450 * 1.2f, widths.last(), 1f, "drawn 1.2× once it fits: $widths")
    }

    /** A centred overlay's room as the desktop table measures it ([DesktopPrompt]): 560 wide exactly, at most [height] tall. */
    @Composable
    private fun Room(height: Int, content: @Composable () -> Unit) {
        Layout(content = content) { measurables, constraints ->
            val placeable = measurables.first().measure(Constraints(minWidth = 560, maxWidth = 560, maxHeight = height))
            layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(0, 0) }
        }
    }

    /** A panel at most 450 wide that, given less than 460 wide, fills the height it gets (its cards on more rows than fit),
     * and is 300 tall otherwise. */
    @Composable
    private fun Panel() {
        Layout(content = {}, modifier = Modifier.testTag("panel")) { _, constraints ->
            val width = minOf(constraints.maxWidth, 450)
            layout(width, if (constraints.maxWidth < 460) constraints.maxHeight else 300) {}
        }
    }
}
