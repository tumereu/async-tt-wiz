package org.asyncttwiz.protocol

import kotlin.test.*

class HistoryIconTest {
    @Test fun iconsKeepTheirWireNames() {
        assertEquals(HistoryIcon.entries.size, HistoryIcon.entries.map { it.wire }.toSet().size)
        for (icon in HistoryIcon.entries) {
            val json = GameWire.json.encodeToString(HistoryIcon.serializer(), icon)
            assertEquals("\"${icon.wire}\"", json)
            assertEquals(icon, GameWire.json.decodeFromString(HistoryIcon.serializer(), json))
        }
    }

    // SYS-034
    @Test fun anUnknownNameDecodesAsUnknownInsteadOfFailing() {
        assertEquals(HistoryIcon.UNKNOWN, GameWire.json.decodeFromString(HistoryIcon.serializer(), "\"added_in_a_later_release\""))
        assertEquals(HistoryIcon.UNKNOWN, HistoryIcon.fromWire(""))
    }
}
