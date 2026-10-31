package org.asyncttwiz.protocol

import kotlin.test.*

/** Rules v32: a card move's history icon names where the card went; grouping is unchanged and
 * events recorded under older rules keep the icons they were recorded with. */
class R32MoveIconsTest {
    private fun id(n: Int) = "00000000-0000-4000-8000-${n.toString().padStart(12, '0')}"
    private val a = id(1); private val b = id(2)
    private val containers = PlayZone.entries.mapIndexed { i, zone -> PlayContainer(id(20 + i), zone, a.takeIf { zone != PlayZone.STACK }) } +
        listOf(PlayContainer(id(40), PlayZone.LIBRARY, a, temp = true), PlayContainer(id(41), PlayZone.LIBRARY, a, temp = true, tempNumber = 2),
            PlayContainer(id(42), PlayZone.BATTLEFIELD, b))
    private fun zone(zone: PlayZone) = containers.first { it.zone == zone && !it.temp }.id
    private fun move(from: String, to: String, edge: MoveSourceEdge = MoveSourceEdge.MEMBER, removeStackEntryId: String? = null) =
        PlayAction.MoveCard(id(10), MoveSource(from, edge = edge), MoveDestination(to, MoveInsertion.TOP), removeStackEntryId = removeStackEntryId)
    private fun v(rules: Int) = GameplayTransition(rulesVersion = rules)
    private fun icon(action: PlayAction, rules: Int = 32) = HistoryGrouping.classify(action, containers, v(rules)).icon

    // HIS-070
    @Test fun currentRulesRecordTargetIcons() {
        assertEquals(32, HistoryGrouping.MOVE_TARGET_ICONS_RULES_VERSION)
        assertTrue(CURRENT_GAMEPLAY_RULES_VERSION >= HistoryGrouping.MOVE_TARGET_ICONS_RULES_VERSION)
    }

    // HIS-070
    @Test fun everyCrossZoneMoveTakesItsTargetIcon() {
        val expected = mapOf(PlayZone.HAND to HistoryIcon.DOWNLOAD, PlayZone.BATTLEFIELD to HistoryIcon.SPACE_DASHBOARD,
            PlayZone.STACK to HistoryIcon.LAYERS, PlayZone.GRAVEYARD to HistoryIcon.DELETE_SWEEP, PlayZone.EXILE to HistoryIcon.BLOCK,
            PlayZone.LIBRARY to HistoryIcon.LIBRARY_BOOKS)
        for (from in PlayZone.entries) for (to in PlayZone.entries) for (edge in MoveSourceEdge.entries) {
            val action = move(zone(from), zone(to), edge)
            val icon = icon(action)
            assertEquals(if (from == to) HistoryIcon.OPEN_WITH else expected.getValue(to), icon, "$from/$edge -> $to")
            // Only the icon changes: significance, key and every grouping input stay as recorded under v31.
            assertEquals(HistoryGrouping.classify(action, containers, v(31)).copy(icon = icon), HistoryGrouping.classify(action, containers, v(32)))
        }
        assertEquals(HistoryIcon.INBOX, icon(move(zone(PlayZone.GRAVEYARD), id(40))))
        assertEquals(HistoryIcon.INBOX, icon(move(zone(PlayZone.LIBRARY), id(40), MoveSourceEdge.TOP)))
        assertEquals(HistoryIcon.INBOX, icon(move(id(40), id(41))), "another pile is another place")
        assertEquals(HistoryIcon.OPEN_WITH, icon(move(id(40), id(40))))
        assertEquals(HistoryIcon.LIBRARY_BOOKS, icon(move(id(40), zone(PlayZone.LIBRARY))))
        assertEquals(HistoryIcon.DELETE_SWEEP, icon(move(id(40), zone(PlayZone.GRAVEYARD))))
        // Gaining control stays within the Battlefield zone: a placement, drawn as a move.
        assertEquals(HistoryIcon.OPEN_WITH, icon(move(zone(PlayZone.BATTLEFIELD), id(42))))
        assertEquals(HistoryIcon.SPACE_DASHBOARD, icon(move(zone(PlayZone.HAND), id(42))))
        // Pulling a top Stack projection keeps its own icon.
        assertEquals(HistoryIcon.LAYERS_CLEAR, icon(move(zone(PlayZone.BATTLEFIELD), zone(PlayZone.HAND), removeStackEntryId = id(50))))
    }

    // HIS-070
    @Test fun randomMovesTakeTheirTargetIcons() {
        val seed = RandomV1.encodeSeed(ByteArray(32))
        assertEquals(HistoryIcon.LIBRARY_BOOKS, icon(PlayAction.MoveToLibraryRandom(zone(PlayZone.LIBRARY), listOf(id(10)), true, seed)))
        fun random(result: HandRandomResult) = PlayAction.RandomHand(zone(PlayZone.HAND), listOf(id(10)), seed, result, id(10))
        assertEquals(mapOf(HandRandomResult.DISCARD to HistoryIcon.DELETE_SWEEP, HandRandomResult.EXILE to HistoryIcon.BLOCK,
            HandRandomResult.LIBRARY_BOTTOM to HistoryIcon.LIBRARY_BOOKS, HandRandomResult.REVEAL to HistoryIcon.VISIBILITY),
            HandRandomResult.entries.associateWith { icon(random(it)) })
        for (result in HandRandomResult.entries) assertEquals(if (result == HandRandomResult.REVEAL) HistoryIcon.VISIBILITY
            else HistoryIcon.OPEN_WITH, icon(random(result), 31))
    }

    // HIS-074, SYS-031
    @Test fun olderEventsKeepTheirRecordedIcons() {
        assertEquals(HistoryIcon.OPEN_WITH, icon(move(zone(PlayZone.HAND), zone(PlayZone.BATTLEFIELD)), 31))
        assertEquals(HistoryIcon.OPEN_WITH, icon(move(zone(PlayZone.HAND), zone(PlayZone.GRAVEYARD)), 31))
        assertEquals(HistoryIcon.DOWNLOAD, icon(move(zone(PlayZone.LIBRARY), zone(PlayZone.HAND), MoveSourceEdge.TOP), 31))
        assertEquals(HistoryIcon.OPEN_WITH, icon(move(zone(PlayZone.LIBRARY), zone(PlayZone.HAND)), 31))
        assertEquals(HistoryIcon.LAYERS, icon(move(zone(PlayZone.STACK), zone(PlayZone.BATTLEFIELD)), 31))
        assertEquals(HistoryIcon.OPEN_WITH, icon(PlayAction.MoveToLibraryRandom(zone(PlayZone.LIBRARY), listOf(id(10)), false,
            RandomV1.encodeSeed(ByteArray(32))), 31))
    }

    @Test fun aStoredOldIconStillMatchesItsReplayedClassification() {
        // readsMatch compares the recorded grouping with a fresh classification under the event's own rules version.
        val action = move(zone(PlayZone.HAND), zone(PlayZone.GRAVEYARD))
        val stored = GameWire.json.decodeFromString(HistorySemantic.serializer(),
            GameWire.json.encodeToString(HistorySemantic.serializer(), HistoryGrouping.classify(action, containers, v(31))))
        assertEquals(stored, HistoryGrouping.classify(action, containers, v(31)))
        assertEquals("delete_sweep", HistoryGrouping.classify(action, containers, v(32)).icon.wire)
    }
}
