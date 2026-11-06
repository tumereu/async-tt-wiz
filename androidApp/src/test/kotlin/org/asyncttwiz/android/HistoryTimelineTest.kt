package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

class HistoryTimelineTest {
    private fun id(n: Int) = "00000000-0000-4000-8000-${n.toString().padStart(12, '0')}"

    @Test fun h01h02h04h05CenterAnchoringLongPathEndpointsResizeAndExactDrag() {
        for (width in listOf(240f,480f,1200f)) for (selected in listOf(0,1,500,999)) {
            assertEquals(width / 2, HistoryTimelineLayout.x(selected, selected, width, 24f))
            val visible = HistoryTimelineLayout.visible(1000, selected, width, 24f)
            assertTrue(selected in visible)
            assertTrue(visible.first >= 0 && visible.last <= 999)
            assertTrue(visible.count() <= width / 24f + 3)
            assertEquals(selected, HistoryTimelineLayout.selectedAfterDrag(selected, 0f, 24f, 1000))
        }
        assertEquals(120f, HistoryTimelineLayout.x(0,0,240f,24f)) // No path segment left of first.
        assertEquals(120f, HistoryTimelineLayout.x(999,999,240f,24f)) // No segment right of last.
        assertEquals(0..0, HistoryTimelineLayout.visible(1,0,480f,24f))
        assertEquals(11, HistoryTimelineLayout.selectedAfterDrag(10,-24f,24f,1000))
        assertEquals(0, HistoryTimelineLayout.selectedAfterDrag(0,240f,24f,1000))
        assertEquals(999, HistoryTimelineLayout.selectedAfterDrag(999,-240f,24f,1000))
    }

    // HIS-005: a separator's line sits in the gap's centre before its event; where labels would overlap, the newest
    // change keeps its label (the lines all show)
    @Test fun separatorsSitInTheGapAndTheNewestLabelWins() {
        assertEquals(88f, HistoryTimelineLayout.separatorX(100f, 24f))
        // Lines at 0, 24 and 100 with 40 wide labels: the one at 24 ends before the line at 100 (24 + 2 + 40 + 2 < 100);
        // the one at 0 would run over the line at 24
        assertEquals(setOf(1, 2), HistoryTimelineLayout.shownLabels(listOf(0f to 40f, 24f to 40f, 100f to 40f), 2f))
        assertEquals(setOf(0, 1), HistoryTimelineLayout.shownLabels(listOf(0f to 10f, 24f to 10f), 2f))
        assertEquals(emptySet(), HistoryTimelineLayout.shownLabels(emptyList(), 2f))
    }

}
