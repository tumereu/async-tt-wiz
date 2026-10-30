package org.asyncttwiz.protocol

import kotlin.test.*

class HistoryIconTest {
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

    @Test fun anUnknownNameDecodesAsUnknownInsteadOfFailing() {
        val semantic = GameWire.json.decodeFromString(HistorySemantic.serializer(),
            """{"significance":"PING","icon":"added_in_a_later_release"}""")
        assertEquals(HistoryIcon.UNKNOWN, semantic.icon)
        assertEquals(HistoryIcon.UNKNOWN, HistoryIcon.fromWire(""))
    }
}
