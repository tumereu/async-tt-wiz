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
    private fun advance(millis: Long) {
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(millis)
        compose.waitForIdle()
    }

    // HIS-003
    @Test fun tapDescriptionFadesAfterShortDelayAndHoldPersistsUntilReleaseWithoutScrubbing() {
        var pauses = 0
        var selections = 0
        compose.setContent { MaterialTheme {
            HistoryTimeline(listOf(event(0)), "0", "me", { pauses++ }, { selections++ }, readOnly = true)
        } }
        compose.mainClock.autoAdvance = false
        val icon = compose.onNodeWithContentDescription("Event 0", useUnmergedTree = true)
        icon.performTouchInput { click() }
        advance(200)
        compose.onNodeWithText("Event 0").assertIsDisplayed()
        advance(1_300)
        compose.onNodeWithText("Event 0").assertIsDisplayed()
        advance(500)
        compose.onNodeWithText("Event 0").assertDoesNotExist()

        icon.performTouchInput { down(center) }
        advance(800)
        compose.onNodeWithText("Event 0").assertIsDisplayed()
        advance(3_000)
        compose.onNodeWithText("Event 0").assertIsDisplayed()
        icon.performTouchInput { up() }
        advance(250)
        compose.onNodeWithText("Event 0").assertDoesNotExist()
        assertEquals(0, pauses)
        assertEquals(0, selections)
    }

    // HIS-002
    @Test fun livePanningOnlyChangesWindowAndEitherAuthorsNewEventAnimatesBackToEnd() {
        var events by mutableStateOf((0..200).map { event(it) })
        var pauses = 0
        var selections = 0
        compose.setContent { MaterialTheme {
            HistoryTimeline(events, events.last().id, "me", { pauses++ }, { selections++ }, readOnly = true)
        } }
        val strip = compose.onNodeWithContentDescription("Event strip")
        val initial = compose.onNodeWithContentDescription("Event 200", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        for (author in listOf("me", "opponent")) {
            strip.performTouchInput { swipe(centerLeft + androidx.compose.ui.geometry.Offset(20f, 0f), centerRight - androidx.compose.ui.geometry.Offset(20f, 0f), 250) }
            compose.waitForIdle()
            val end = events.lastIndex
            compose.onNodeWithContentDescription("Event ${end - 12}", useUnmergedTree = true).assertIsDisplayed()
            compose.onNodeWithContentDescription("Event $end", useUnmergedTree = true).assertDoesNotExist()
            val nearEnd = compose.onNodeWithContentDescription("Event ${end - 10}", useUnmergedTree = true)
            val pannedX = nearEnd.fetchSemanticsNode().boundsInRoot.center.x
            compose.mainClock.autoAdvance = false
            compose.runOnIdle { events = events + event(end + 1, author) }
            advance(60)
            val duringX = nearEnd.assertIsDisplayed().fetchSemanticsNode().boundsInRoot.center.x
            assertTrue(duringX < pannedX, "Return to latest must animate, not jump or stay panned")
            advance(250)
            val latest = compose.onNodeWithContentDescription("Event ${end + 1}", useUnmergedTree = true).assertIsSelected().assertIsDisplayed()
            assertEquals(initial, latest.fetchSemanticsNode().boundsInRoot)
            compose.mainClock.autoAdvance = true
        }
        assertEquals(0, pauses)
        assertEquals(0, selections)
    }

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

    // HIS-005, HIS-036
    @Test fun branchMarkerSitsInTheGapWhereTheBranchSplitsAndAnArmedPointShowsTheComingSplit() {
        var armed by mutableStateOf<String?>(null)
        val events = (0..4).map { if (it == 1) event(it).copy(alternatives = 1) else event(it) }
        compose.setContent { MaterialTheme {
            HistoryTimeline(events, "2", "me", {}, {}, readOnly = true, armedSplit = armed)
        } }
        fun chip(n: Int) = compose.onNodeWithContentDescription("Event $n", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
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
