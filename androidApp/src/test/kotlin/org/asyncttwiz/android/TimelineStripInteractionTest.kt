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

    // HIS-005: a turn or step change shows as a labelled line in the gap before the event made after it; there are no
    // branch lines
    @Test fun aSeparatorSitsInTheGapBeforeTheEventAfterTheChange() {
        val events = (0..4).map { n ->
            if (n == 3) event(n).copy(separator = TimelineSeparator("T2 · Main 1", newTurn = true, active = "other")) else event(n) }
        compose.setContent { MaterialTheme { HistoryTimeline(events, "2", "me", {}, {}) } }
        fun chip(n: Int) = compose.onNode(hasContentDescription("Event $n") and hasClickAction(), useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val line = compose.onAllNodesWithTag(TimelineSeparatorTag, useUnmergedTree = true).fetchSemanticsNodes().single()
        assertTrue(line.boundsInRoot.left > chip(2).right && line.boundsInRoot.right < chip(3).left, "in the gap before event 3")
        compose.onNode(hasTestTag(TimelineSeparatorTag) and hasContentDescription("T2 · Main 1"), useUnmergedTree = true).assertExists()
        compose.onAllNodesWithTag("Branch marker", useUnmergedTree = true).assertCountEquals(0)
    }
}
