package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

class CompleteManualFixVerificationTest {
    private fun id(n: Int) = "00000000-0000-4000-8000-${n.toString().padStart(12, '0')}"

    @Test fun renamedManualAndBooleanQuickPressesKeepDistinctTerminalApis() {
        val compatibility: ManualActionPress = ActionPress()
        val production = ManualActionPress()
        for (manual in listOf(compatibility, production)) {
            var holds = 0
            manual.start()
            manual.hold { holds++ }
            manual.cancel()
            manual.hold { holds++ }
            assertFalse(manual.release())
            assertEquals(1, holds)
            manual.start()
            assertTrue(manual.release())
            manual.hold { holds++ }
            assertEquals(1, holds)
            manual.start()
            manual.hold(null)
            assertTrue(manual.tooltip)
            manual.cancel()
            assertFalse(manual.tooltip)
            assertFalse(manual.release())
            manual.start()
            assertTrue(manual.release())
        }
        for (assigned in listOf(false, true)) {
            val quick: ActionPress = ActionPress(assigned)
            assertEquals(if (assigned) PressAction.HOLD else PressAction.TOOLTIP, quick.hold())
            quick.cancel()
            assertNull(quick.release())
            assertNull(quick.hold())
            val tap = ActionPress(assigned)
            assertEquals(PressAction.TAP, tap.release())
            assertNull(tap.hold())
            assertNull(tap.release())
        }
    }

    @Test fun repairedLiteralActionIconsHaveExplicitGlyphsRatherThanFallbacks() {
        val cases = listOf(
            Triple(PlayAction.ResetCorner(id(1)), "settings_backup_restore", "↺"),
            Triple(PlayAction.SetFace(id(1), id(2)), "flip_camera_android", "▣"),
            Triple(PlayAction.SetCovered(id(1), true), "flip", "▧"),
            Triple(PlayAction.SetCovered(id(1), false), "flip_to_front", "▤"),
            Triple(PlayAction.Link(LinkEndpoint(LinkEndpointKind.CARD, id(1)),
                LinkEndpoint(LinkEndpointKind.PLAYER, id(2))), "link", "∞"),
            Triple(PlayAction.RemoveLink(id(1)), "link_off", "∦"),
            Triple(PlayAction.Combat(CombatAction.Remove(id(1), id(2))), "remove_circle_outline", "⊖"),
            Triple(PlayAction.InspectFace(id(1), id(2), id(3)), "visibility", "◉"),
            Triple(PlayAction.CloneToken(id(1)), "control_point_duplicate", "⊕"),
            Triple(PlayAction.Stack(StackAction.RemoveEffect(id(1))), "layers_clear", "⊠"))
        for ((action, icon, glyph) in cases) {
            assertEquals(icon, HistoryGrouping.classify(action).icon)
            assertEquals(glyph, historyIconGlyph(icon))
        }
        assertFailsWith<IllegalStateException> { historyIconGlyph("unmapped-manual-type") }
    }

    @Test fun nativeTimelineKeepsTapViewAndEditBoundariesWithLiteralIcons() {
        fun node(n: Int, parent: Int, action: PlayAction) = GameplayMutation(id(n), id(parent), id(10),
            id(100 + n), id(7), null, action, GameplayTransition(), grouping = HistoryGrouping.classify(action))
        // Presentation-only fixture; accepted disclosure/replay is exercised in the protocol verifier.
        val tap = node(1, 0, PlayAction.SetTapped(id(9), true))
        val view = node(2, 1, PlayAction.InspectFace(id(9), id(12), id(11)))
        val edit = node(3, 2, PlayAction.SetNote(id(9), "after view"))
        val tree = HistoryTree(id(0), listOf(tap, view, edit), edit.id, "verified-prefix")
        val events = HistoryTimelineLayout.events(tree, edit.id)
        assertEquals(listOf(id(0), tap.id, view.id, edit.id), events.map { it.id })
        assertEquals(events[1].groupId, events[2].groupId)
        assertNotEquals(events[2].groupId, events[3].groupId)
        assertEquals(listOf("flag", "rotate_90_degrees_cw", "visibility", "sticky_note_2"), events.map { it.icon })
        assertEquals(listOf("⚑", "↻", "◉", "▧"), events.map { historyIconGlyph(it.icon) })
    }
}
