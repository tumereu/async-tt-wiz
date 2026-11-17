package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import kotlin.test.Test
import kotlin.test.*

class TableChangeTargetsTest {

    private fun assertCardTarget(scene: TableScene, id: String) {
        val items = scene.items.associateBy { it.id }
        val drawing = scene.items.single { it.cardId == id }
        assertTrue(drawing.visible.isNotEmpty())
        for (kind in listOf(TableChangeKind.OWNER, TableChangeKind.NOTE, TableChangeKind.TEXT_MARK))
            assertEquals(drawing, tableChangeTarget(TableChangeKey(id, kind), items::get), "$id: $kind")
        val link = TableChange(TableChangeKey("link", TableChangeKind.LINK), link = PlayLink("link",
            LinkEndpoint(LinkEndpointKind.CARD, id), LinkEndpoint(LinkEndpointKind.PLAYER, "a")))
        assertEquals(requireNotNull(drawing.visibleAnchor) to requireNotNull(items.getValue("life/a").visibleAnchor),
            tableChangeLinkAnchors(link, items::get))
    }

    @Test fun battlefieldUsesItsRenderedCardForHighlightsAndLinks() {
        val scene = ChangeTargetFixture.scene(LiveTableFixture.menuPlay())
        assertCardTarget(scene, scene.items.first { it.zone == PlayZone.BATTLEFIELD && it.kind == TableItemKind.CARD }.cardId!!)
    }

    @Test fun realStackEntryUsesStackDrawingForHighlightsAndLinks() {
        val (play, id) = ChangeTargetFixture.stack(projection = false)
        val scene = ChangeTargetFixture.scene(play)
        assertEquals("stackDrawing/$id", scene.items.single { it.cardId == id }.id)
        assertCardTarget(scene, id)
    }

    @Test fun stackAndCombatProjectionsUseExactOccurrenceForHighlightsAndLinkGlows() {
        val (play, effect) = ChangeTargetFixture.stack(projection = true)
        val source = play.board.stackEffects.single().sourceId
        val occurrence = CombatOccurrence("combat-occurrence", source, note = "combat note")
        val scene = ChangeTargetFixture.scene(play.copy(board = play.board.copy(
            combat = CombatArea("combat", listOf(CombatPair("pair", attackers = listOf(occurrence)))))))
        assertCardTarget(scene, effect)
        assertCardTarget(scene, occurrence.id)
        val items = scene.items.associateBy { it.id }
        val link = TableChange(TableChangeKey("link", TableChangeKind.LINK),
            projectionLink = ProjectionLink("link", effect, occurrence.id))
        assertEquals(requireNotNull(items.getValue("stackDrawing/$effect").visibleAnchor) to requireNotNull(items.getValue("card/${occurrence.id}").visibleAnchor),
            tableChangeLinkAnchors(link, items::get))
        assertNotEquals(source, tableChangeTarget(TableChangeKey(effect, TableChangeKind.OWNER), items::get)?.cardId)
    }

    @Test fun buriedOrClippedStackEntriesNeverBorrowTheTopDrawingOrSource() {
        val (play, id) = ChangeTargetFixture.stack(projection = true)
        val scene = ChangeTargetFixture.scene(play)
        val items = scene.items.associateBy { it.id }
        assertNull(tableChangeTarget(TableChangeKey("buried", TableChangeKind.OWNER), items::get))
        val clipped = items.mapValues { (_, item) -> if (item.cardId == id) item.copy(visible = emptyList()) else item }
        assertNull(tableChangeTarget(TableChangeKey(id, TableChangeKind.OWNER), clipped::get))
        assertNull(tableChangeLinkAnchors(TableChange(TableChangeKey("link", TableChangeKind.LINK),
            projectionLink = ProjectionLink("link", id, "buried")), items::get))
    }


    @Test fun coveredCardsAllowOnlyPublicOwnerPulsesNotHiddenValueEffects() {
        val (play, id) = ChangeTargetFixture.stack(projection = false, covered = true)
        val items = ChangeTargetFixture.scene(play).items.associateBy { it.id }
        assertTrue(assertNotNull(tableChangeTarget(TableChangeKey(id, TableChangeKind.OWNER), items::get)).covered)
        for (kind in listOf(TableChangeKind.NOTE, TableChangeKind.CORNER, TableChangeKind.COUNTER, TableChangeKind.TEXT_MARK))
            assertNull(tableChangeTarget(TableChangeKey(id, kind), items::get), "$kind must not disclose covered values")
    }
}

