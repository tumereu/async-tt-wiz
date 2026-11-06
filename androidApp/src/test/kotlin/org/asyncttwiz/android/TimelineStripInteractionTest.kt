package org.asyncttwiz.android

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.asyncttwiz.protocol.HistoryIcon
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class TimelineStripInteractionTest {
    @get:Rule val compose = createComposeRule()
    private fun event(n: Int, author: String = "me") = HistoryTimelineEvent("$n", HistoryIcon.FLAG, "Event $n", author, null)

    // HIS-011
    @Test fun reviewTapStillSelectsAndHoldStillDoesNotMoveCursor() {
        var selected by mutableStateOf("1")
        var pauses = 0
        compose.setContent { MaterialTheme {
            HistoryTimeline((0..2).map { event(it) }, selected, "me", { pauses++ }, { selected = it })
        } }
        compose.onNodeWithContentDescription("Event 0", useUnmergedTree = true).performTouchInput { longClick() }
        assertEquals("1", selected)
        compose.onNodeWithContentDescription("Event 0", useUnmergedTree = true).performTouchInput { click() }
        assertEquals("0", selected)
        assertTrue(pauses > 0)
    }

    // HIS-005, old-model timeline branching
    @Test fun branchMarkerSitsInTheGapWhereTheBranchSplitsAndAnArmedPointShowsTheComingSplit() {
        var armed by mutableStateOf<String?>(null)
        val events = (0..4).map { if (it == 1) event(it).copy(alternatives = 1) else event(it) }
        compose.setContent { MaterialTheme {
            HistoryTimeline(events, "2", "me", {}, {}, armedSplit = armed)
        } }
        fun chip(n: Int) = compose.onNode(hasContentDescription("Event $n") and hasClickAction(), useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        fun markers() = compose.onAllNodesWithTag(BranchMarkerTag, useUnmergedTree = true).fetchSemanticsNodes().map { it.boundsInRoot }
        fun assertInGapAfter(n: Int, marker: androidx.compose.ui.geometry.Rect) {
            assertTrue(marker.left > chip(n).right && marker.left < chip(n + 1).left, "Marker starts in the gap after event $n")
            assertTrue(marker.right > chip(n + 1).left, "Marker turns right over the next event")
        }
        assertInGapAfter(1, markers().single())
        compose.runOnIdle { armed = "3" }
        val both = markers().sortedBy { it.left }
        assertEquals(2, both.size)
        assertInGapAfter(1, both[0]); assertInGapAfter(3, both[1])
        compose.runOnIdle { armed = "1" }
        assertEquals(1, markers().size, "An armed point that already splits shows one marker")
    }
}
