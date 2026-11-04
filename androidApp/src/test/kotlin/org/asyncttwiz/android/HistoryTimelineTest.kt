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


    @Test fun mergedManualAndOccurrenceIconsRenderWithoutGenericFallback() {
        val actions = listOf(PlayAction.ResetCorner(id(1)), PlayAction.SetFace(id(1), id(2)),
            PlayAction.SetCovered(id(1), true), PlayAction.SetCovered(id(1), false),
            PlayAction.Link(LinkEndpoint(LinkEndpointKind.CARD, id(1)), LinkEndpoint(LinkEndpointKind.PLAYER, id(2))),
            PlayAction.RemoveLink(id(1)), PlayAction.Combat(CombatAction.Remove(id(1), id(2))),
            PlayAction.InspectFace(id(1), id(2), id(3)), PlayAction.CloneToken(id(1)),
            PlayAction.Stack(StackAction.RemoveEffect(id(1))))
        for (action in actions) assertTrue(historyIconGlyph(HistoryGrouping.classify(action).icon).isNotBlank(), "$action")
        assertNotEquals(historyIconGlyph(HistoryIcon.LINK), historyIconGlyph(HistoryIcon.LINK_OFF))
        assertEquals(HistoryIcon.UNKNOWN, HistoryIcon.fromWire("unknown-manual-action"))
    }


    // HIS-005, old-model timeline branching
    @Test fun branchSplitsAfterTheSharedEventOrTheArmedPointInTheGapCentre() {
        val plain = HistoryTimelineEvent("a", HistoryIcon.FLAG, "", null, null)
        assertFalse(HistoryTimelineLayout.splitsAfter(plain, null))
        assertTrue(HistoryTimelineLayout.splitsAfter(plain.copy(alternatives = 2), null))
        assertTrue(HistoryTimelineLayout.splitsAfter(plain, "a"))
        assertFalse(HistoryTimelineLayout.splitsAfter(plain, "b"))
        assertEquals(112f, HistoryTimelineLayout.splitGapX(100f, 24f))
    }

}
