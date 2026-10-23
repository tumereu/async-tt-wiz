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

    @Test fun projectionRetainsEveryBoundaryActualTypeAuthorGroupAndBranchStubs() {
        fun node(n: Int, parent: Int, tapped: Boolean, author: String = id(70)) = GameplayMutation(id(n), id(parent), id(50),
            id(100+n), author, null, PlayAction.SetTapped(id(80), tapped), GameplayTransition(),
            grouping = HistoryGrouping.classify(PlayAction.SetTapped(id(80), tapped)))
        val a = node(1,0,true); val b = node(2,1,false); val c = node(3,2,true,id(71)); val fork = node(4,1,false)
        val events = HistoryTimelineLayout.events(HistoryTree(id(0), listOf(a,b,c,fork),id(3),"v3"), id(3))
        assertEquals(listOf(id(0),id(1),id(2),id(3)), events.map { it.id })
        assertEquals(1, events[1].alternatives)
        assertEquals(events[1].groupId, events[2].groupId)
        assertNotEquals(events[2].groupId, events[3].groupId)
        assertEquals(listOf(null,id(70),id(70),id(71)), events.map { it.author })
        assertEquals("flag", events.first().icon)
        assertEquals(listOf("rotate_90_degrees_cw"), events.drop(1).map { it.icon }.distinct())
        assertEquals("A player untapped a card", events[2].description)
        assertTrue(events.none { it.description.contains("00000000-") })
        assertNotEquals(historyIconGlyph("favorite"), historyIconGlyph("ads_click"))
        assertFails { historyIconGlyph("invented-cast") }
    }

    @Test fun commentDraftKeepsCapturedReferenceAndExactMessageWhitespaceSeparateFromChat() {
        val reference = EventReference(id(1), id(2)); val draft = EventCommentDraft(reference)
        assertNull(draft.action())
        draft.text = "  one\n two  "
        assertEquals(PlayAction.EventComment(reference, "  one\n two  "), draft.action())
        assertEquals(reference, draft.reference)
        draft.text = "  "; assertNull(draft.action())
    }

    @Test fun mergedManualAndOccurrenceIconsRenderWithoutGenericFallback() {
        val actions = listOf(PlayAction.ResetCorner(id(1)), PlayAction.SetFace(id(1), id(2)),
            PlayAction.SetCovered(id(1), true), PlayAction.SetCovered(id(1), false),
            PlayAction.Link(LinkEndpoint(LinkEndpointKind.CARD, id(1)), LinkEndpoint(LinkEndpointKind.PLAYER, id(2))),
            PlayAction.RemoveLink(id(1)), PlayAction.Combat(CombatAction.Remove(id(1), id(2))),
            PlayAction.InspectFace(id(1), id(2), id(3)), PlayAction.CloneToken(id(1)),
            PlayAction.Stack(StackAction.RemoveEffect(id(1))))
        for (action in actions) assertTrue(historyIconGlyph(HistoryGrouping.classify(action).icon).isNotBlank(), "$action")
        assertNotEquals(historyIconGlyph("link"), historyIconGlyph("link_off"))
        assertFails { historyIconGlyph("unknown-manual-action") }
    }

    @Test fun pingUniformSelectionOrderExplicitUnpingAndModeLifetime() {
        val reference = EventReference(id(1), id(2))
        val x = PingTarget(PingTargetKind.CARD,id(3)); val y = PingTarget(PingTargetKind.CARD,id(4))
        val intent = EventPingIntent(reference,listOf(y,x,y),true)
        assertEquals(listOf(PlayAction.Ping(reference,y),PlayAction.Ping(reference,x)), intent.actions())
        assertEquals(listOf(PlayAction.Unping(reference,y),PlayAction.Unping(reference,x)), intent.copy(add=false).actions())
        val mode = EventPingMode(contextId = "group-a").enablePing()
        assertEquals(EventInputMode.PING, mode.visit("group-a").mode) // Anchor fill preserves mode.
        assertEquals(EventInputMode.NONE, mode.visit("group-b").mode)
        assertEquals(EventInputMode.NONE, mode.visit("group-b").visit("group-a").mode) // Revisit doesn't enable mode.
        assertEquals(EventInputMode.SELECTION, mode.enableSelection().mode)
    }
}
