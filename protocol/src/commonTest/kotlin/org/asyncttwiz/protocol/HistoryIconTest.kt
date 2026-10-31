package org.asyncttwiz.protocol

import kotlin.test.*

class HistoryIconTest {
    private fun id(n: Int) = "00000000-0000-4000-8000-${n.toString().padStart(12, '0')}"

    @Test fun storedGroupingsKeepTheirWireNames() {
        assertEquals(HistoryIcon.entries.size, HistoryIcon.entries.map { it.wire }.toSet().size)
        val stored = """{"significance":"CROSS_ZONE_MOVE","icon":"open_with","key":"a/b"}"""
        val semantic = GameWire.json.decodeFromString(HistorySemantic.serializer(), stored)
        assertEquals(HistorySemantic(HistorySignificance.CROSS_ZONE_MOVE, HistoryIcon.OPEN_WITH, "a/b"), semantic)
        assertEquals(stored, GameWire.json.encodeToString(HistorySemantic.serializer(), semantic))
        for (icon in HistoryIcon.entries) {
            val json = GameWire.json.encodeToString(HistoryIcon.serializer(), icon)
            assertEquals("\"${icon.wire}\"", json)
            assertEquals(icon, GameWire.json.decodeFromString(HistoryIcon.serializer(), json))
        }
    }

    // HIS-074, SYS-034
    @Test fun anUnknownNameDecodesAsUnknownInsteadOfFailing() {
        val semantic = GameWire.json.decodeFromString(HistorySemantic.serializer(),
            """{"significance":"PING","icon":"added_in_a_later_release"}""")
        assertEquals(HistoryIcon.UNKNOWN, semantic.icon)
        assertEquals(HistoryIcon.UNKNOWN, HistoryIcon.fromWire(""))
    }

    // SYS-034
    @Test fun anUnknownSignificanceDecodesAsUnknownWithOrWithoutAKey() {
        for (stored in listOf("""{"significance":"ADDED_LATER","icon":"open_with"}""",
                """{"significance":"ADDED_LATER","icon":"open_with","key":"a/b"}""")) {
            val semantic = GameWire.json.decodeFromString(HistorySemantic.serializer(), stored)
            assertEquals(HistorySignificance.UNKNOWN, semantic.significance)
            assertEquals(HistoryIcon.OPEN_WITH, semantic.icon)
        }
        for (significance in HistorySignificance.entries) {
            val json = GameWire.json.encodeToString(HistorySignificance.serializer(), significance)
            assertEquals("\"${significance.name}\"", json)
            assertEquals(significance, GameWire.json.decodeFromString(HistorySignificance.serializer(), json))
        }
        // An unknown event never represents a group over a known one.
        val known = HistoryRepresentative("a", HistorySemantic(HistorySignificance.PING, HistoryIcon.ADS_CLICK))
        val unknown = HistoryRepresentative("b", HistorySemantic(HistorySignificance.UNKNOWN, HistoryIcon.UNKNOWN))
        assertEquals(known, HistoryGrouping.choose(known, unknown))
        assertEquals(known, HistoryGrouping.choose(unknown, known))
    }

    // SYS-034
    @Test fun anUnknownGroupCategoryContinuesWithoutFailing() {
        val previous = GameplayGroup("g", "me", "n1", "n1", "added-later", null, "1")
        val comment = HistorySemantic(HistorySignificance.EVENT_COMMENT, HistoryIcon.CHAT_BUBBLE)
        val joined = HistoryGrouping.append(previous, "n2", id(2), "me", comment)
        assertEquals("g", joined.id)
        assertEquals("2", joined.count)
        val tap = HistorySemantic(HistorySignificance.ORIENTATION, HistoryIcon.ROTATE_90_DEGREES_CW)
        assertNotEquals("g", HistoryGrouping.append(previous, "n3", id(3), "me", tap).id)
        val other = HistorySemantic(HistorySignificance.SAME_ZONE_PLACEMENT, HistoryIcon.OPEN_WITH, "a/a")
        assertNotEquals("g", HistoryGrouping.append(previous, "n4", id(4), "me", other).id)
        HistoryGrouping.append(previous, "n5", id(5), "me", HistorySemantic(HistorySignificance.UNKNOWN, HistoryIcon.UNKNOWN))
    }
}
