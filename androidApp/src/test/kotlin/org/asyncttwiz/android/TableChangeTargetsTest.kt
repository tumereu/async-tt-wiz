package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

class TableChangeTargetsTest {
    @Test fun tagTextUsesTheHigherContrastInkForEveryCounterColour() {
        for (color in CounterColor.entries)
            assertEquals(androidx.compose.ui.graphics.Color.Black, tableChangeTagInk(counterColor(color)))
        assertEquals(androidx.compose.ui.graphics.Color.White, tableChangeTagInk(TableChangeTagFill))
    }

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

    @Test fun zeroedPortraitCounterKeepsItsOutlineSlotAndChangeTarget() {
        val initial = LiveTableFixture.menuPlay()
        val before = initial.copy(board = initial.board.copy(players = initial.board.players.map {
            it.copy(counters = if (it.accountId == "a") mapOf(CounterColor.BLUE to "1") else emptyMap())
        }))
        val after = before.copy(board = before.board.copy(players = before.board.players.map { it.copy(counters = emptyMap()) }))
        val key = TableChangeKey("a", TableChangeKind.PLAYER_COUNTER, CounterColor.BLUE)
        val oldItems = ChangeTargetFixture.scene(before).items.associateBy { it.id }
        assertEquals("counter/a/BLUE", tableChangeTarget(key, oldItems::get)?.id)
        val scene = ChangeTargetFixture.scene(after)
        val items = scene.items.associateBy { it.id }
        assertEquals(12, scene.items.count { it.kind == TableItemKind.COUNTER })
        val hud = assertNotNull(tableChangeTarget(key, items::get))
        assertEquals("counter/a/BLUE", hud.id)
        assertEquals("", hud.value)
        assertEquals(TableItemKind.COUNTER, scene.hit(hud.bounds.center)?.kind)
    }

    @Test fun coveredCardsAllowOnlyPublicOwnerPulsesNotHiddenValueEffects() {
        val (play, id) = ChangeTargetFixture.stack(projection = false, covered = true)
        val items = ChangeTargetFixture.scene(play).items.associateBy { it.id }
        assertTrue(assertNotNull(tableChangeTarget(TableChangeKey(id, TableChangeKind.OWNER), items::get)).covered)
        for (kind in listOf(TableChangeKind.NOTE, TableChangeKind.CORNER, TableChangeKind.COUNTER, TableChangeKind.TEXT_MARK))
            assertNull(tableChangeTarget(TableChangeKey(id, kind), items::get), "$kind must not disclose covered values")
    }
}

internal object ChangeTargetFixture {
    fun scene(play: PlayState) = TablePresentation.scene(LiveTableFixture.game, play, "a", 412f, 892f, false)

    fun stack(projection: Boolean, covered: Boolean = false): Pair<PlayState, String> {
        val play = LiveTableFixture.menuPlay()
        val card = play.board.cards.first { it.containerId == "a/BATTLEFIELD" }
        val id = if (projection) "stack-effect" else card.id
        return play.copy(board = play.board.copy(
            cards = play.board.cards.map { if (it.id == card.id) it.copy(covered = covered,
                explicitlyCovered = covered, containerId = if (projection) it.containerId else "stack") else it },
            containers = play.board.containers.map {
                when { it.zone == PlayZone.STACK -> it.copy(cards = listOf(id))
                    !projection -> it.copy(cards = it.cards - card.id)
                    else -> it }
            },
            stackEffects = if (projection) listOf(StackEffect(id, card.id, controller = "b", note = "effect note")) else emptyList()
        )) to id
    }
}
