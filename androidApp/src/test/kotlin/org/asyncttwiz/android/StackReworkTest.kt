package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

class StackReworkTest {
    private fun scene(f: BasicTableFixture) = TablePresentation.scene(f.game, f.play, f.actor, 412f, 892f, false)
    private fun toolkit(f: BasicTableFixture) = TableToolkitController(f.actor, { f.play }, { true }, f.lobby::gameplay, playBatch = f.lobby::gameplayBatch)
    private fun actions(f: BasicTableFixture, from: Int) = f.commands().drop(from).map { (it.action as GameAction.Play).action }

    @Test fun exileBubbleMovesTheSourceToItsOwnerAndIsUndoableForRealCardsAndEffects() {
        for (legacy in listOf(false, true)) {
            val f = BasicTableFixture(); f.connect()
            try {
                assertTrue(f.move(40, TableDestination.Grid(f.container(PlayZone.BATTLEFIELD), 0, 1)))
                val ui = toolkit(f)
                if (legacy) assertTrue(f.legacyStack(40)) else assertTrue(ui.addEffects(listOf(f.id(40))))
                val before = f.play.board
                val top = CombatStackRules.stack(before).cards.first()
                val count = f.commands().size
                assertTrue(ui.handle(TableIntent.StackDestination(top, PlayZone.EXILE, fromBubble = true)))
                assertEquals(f.container(PlayZone.EXILE, f.card(40).owner), f.card(40).containerId)
                assertTrue(CombatStackRules.stack(f.play.board).cards.isEmpty())
                assertEquals(1, actions(f, count).filterIsInstance<PlayAction.MoveCard>().size)
                assertEquals(1, f.commands().drop(count).map { it.invocationId }.distinct().size)
                assertTrue(f.lobby.historyControl(GameAction.Undo))
                assertEquals(before, f.play.board)
            } finally { f.lobby.close(); f.worker.drain() }
        }
    }

    @Test fun onlyTopIsDrawnAndDraggableHoldInspectsAndDoubleTapManagesAllEntries() {
        val f = BasicTableFixture(); f.connect()
        try {
            assertTrue(f.move(40, TableDestination.Grid(f.container(PlayZone.BATTLEFIELD), 0, 1)))
            val ui = toolkit(f)
            repeat(3) { assertTrue(ui.addEffects(listOf(f.id(40)))) }
            val scene = scene(f)
            val top = CombatStackRules.stack(f.play.board).cards.first()
            val entries = scene.items.filter { it.zone == PlayZone.STACK && it.kind == TableItemKind.CARD }
            assertEquals(listOf(top), entries.filter(scene::canDrag).map { it.cardId })
            assertEquals(1, entries.size)
            assertEquals(3, scene.items.single { it.kind == TableItemKind.PILE && it.zone == PlayZone.STACK }.cards.size)
            val card = entries.single()
            val point = card.visible.first().center
            val events = mutableListOf<TableIntent>()
            val gestures = TableGestureCoordinator(events::add)
            gestures.update(scene, TableSelection(ids = entries.mapNotNull { it.cardId }.toSet()), true)
            gestures.down(point, 0); gestures.up(point, 20); gestures.tick(400)
            assertEquals(listOf<TableIntent>(TableIntent.StackBubbles(top)), events)
            events.clear()
            gestures.down(point, 1000); gestures.tick(1400); gestures.up(point, 1420)
            assertEquals(listOf<TableIntent>(TableIntent.Peek(top), TableIntent.Peek(null)), events)
            events.clear()
            gestures.down(point, 1800); gestures.up(point, 1820)
            gestures.down(point, 1900); gestures.up(point, 1920)
            val event = events.filterIsInstance<TableIntent.Actions>().single()
            ui.open(event, card, TableSelection())
            assertEquals("manageStack", ui.panel)
            assertNull(ui.manual.scope)
        } finally { f.lobby.close(); f.worker.drain() }
    }

    @Test fun topDropToAnotherZoneMovesRealCardAndRemovesProjectionWithOneUndoAndRedo() {
        for (zone in listOf(PlayZone.HAND, PlayZone.GRAVEYARD, PlayZone.EXILE, PlayZone.LIBRARY)) {
            val f = BasicTableFixture(); f.connect()
            try {
                assertTrue(f.move(40, TableDestination.Grid(f.container(PlayZone.BATTLEFIELD), 0, 1)))
                val ui = toolkit(f)
                assertTrue(ui.addEffects(listOf(f.id(40))))
                val before = f.play.board
                val id = before.stackEffects.single().id
                val count = f.commands().size
                assertTrue(ui.handle(TableIntent.Move(TableScope(id, listOf(id)),
                    TableDestination.Container(f.container(zone), zone), scene(f).sources)))
                val sent = actions(f, count)
                assertEquals(1, sent.filterIsInstance<PlayAction.MoveCard>().size)
                assertEquals(f.id(40), sent.filterIsInstance<PlayAction.MoveCard>().single().cardId)
                assertEquals(1, sent.size)
                assertEquals(id, sent.filterIsInstance<PlayAction.MoveCard>().single().removeStackEntryId)
                assertEquals(1, f.commands().drop(count).map { it.invocationId }.distinct().size)
                assertEquals(f.container(zone), f.card(40).containerId)
                assertTrue(f.play.board.stackEffects.isEmpty())
                val after = f.play.board
                assertTrue(f.lobby.historyControl(GameAction.Undo))
                assertEquals(before, f.play.board)
                assertTrue(f.lobby.historyControl(GameAction.Redo))
                assertEquals(after, f.play.board)
            } finally { f.lobby.close(); f.worker.drain() }
        }
    }

    @Test fun sameZoneDropsAndShortcutsRemoveOnlyProjectionEvenOnOtherPlayersBattlefield() {
        for (zone in listOf(PlayZone.BATTLEFIELD, PlayZone.GRAVEYARD, PlayZone.EXILE, PlayZone.HAND)) {
            val f = BasicTableFixture(); f.connect()
            try {
                val ui = toolkit(f)
                val owner = if (zone == PlayZone.BATTLEFIELD) f.other else f.actor
                val target = if (zone == PlayZone.BATTLEFIELD) TableDestination.Grid(f.container(zone, owner), 2, 1)
                    else TableDestination.Container(f.container(zone), zone)
                assertTrue(f.move(40, target))
                assertTrue(ui.addEffects(listOf(f.id(40))))
                val id = f.play.board.stackEffects.single().id
                val card = f.card(40)
                if (zone in listOf(PlayZone.BATTLEFIELD, PlayZone.GRAVEYARD))
                    assertEquals(listOf(StackMenuActions.remove(id)), StackMenuActions.destination(f.play, id, zone))
                val count = f.commands().size
                val drop = if (zone == PlayZone.BATTLEFIELD) TableDestination.Grid(f.container(zone), 9, 2) else target
                assertTrue(ui.handle(TableIntent.Move(TableScope(id, listOf(id)), drop, scene(f).sources)))
                assertEquals(listOf(StackMenuActions.remove(id)), actions(f, count))
                assertEquals(card, f.card(40))
            } finally { f.lobby.close(); f.worker.drain() }
        }
    }

    @Test fun stackDropIsANoopAndStaleOrLowerProjectionCannotMoveItsSource() {
        val f = BasicTableFixture(); f.connect()
        try {
            val ui = toolkit(f)
            assertTrue(f.move(40, TableDestination.Container(f.container(PlayZone.HAND), PlayZone.HAND)))
            assertTrue(ui.addEffects(listOf(f.id(40))))
            val lower = f.play.board.stackEffects.single().id
            assertTrue(ui.addEffects(listOf(f.id(40))))
            val top = CombatStackRules.stack(f.play.board).cards.first()
            val before = f.play.board
            val count = f.commands().size
            assertFalse(ui.handle(TableIntent.Move(TableScope(top, listOf(top)),
                TableDestination.Container(f.container(PlayZone.STACK), PlayZone.STACK), scene(f).sources)))
            assertFalse(ui.handle(TableIntent.Move(TableScope(lower, listOf(lower)),
                TableDestination.Grid(f.container(PlayZone.BATTLEFIELD), 0, 1), scene(f).sources)))
            val stale = scene(f).sources.getValue(top).copy(containerId = f.container(PlayZone.EXILE))
            assertFalse(ui.handle(TableIntent.Move(TableScope(top, listOf(top)),
                TableDestination.Grid(f.container(PlayZone.BATTLEFIELD), 0, 1), mapOf(top to stale))))
            assertEquals(before, f.play.board)
            assertEquals(count, f.commands().size)
        } finally { f.lobby.close(); f.worker.drain() }
    }

    @Test fun removeBubbleAndMenuControlsMapToIdentityBoundPlayActions() {
        val f = BasicTableFixture(); f.connect()
        try {
            assertTrue(f.move(40, TableDestination.Grid(f.container(PlayZone.BATTLEFIELD), 0, 1)))
            val ui = toolkit(f)
            repeat(3) { assertTrue(ui.addEffects(listOf(f.id(40)))) }
            val order = CombatStackRules.stack(f.play.board).cards
            val top = order.first()
            assertEquals(PlayAction.Stack(StackAction.RemoveEffect(top)), StackMenuActions.remove(top))
            assertEquals(PlayAction.Stack(StackAction.MarkText(top, 0)), StackMenuActions.mark(top, 0))
            assertEquals(PlayAction.Stack(StackAction.MarkText(top, null)), StackMenuActions.mark(top, null))
            val current = top
            val base = scene(f)
            val bubble = TablePresentation.stackBubbles(base, f.play, current, 412f, 892f)!!
            val gestures = TableGestureCoordinator({ ui.handle(it) })
            gestures.update(base.copy(stackBubbles = bubble), TableSelection(), true)
            val count = f.commands().size
            val card = f.card(40)
            val remove = bubble.controls.single { it.label == "Remove from Stack" }
            gestures.down(remove.bounds.center, 0); gestures.up(remove.bounds.center, 20)
            assertEquals(listOf(StackMenuActions.remove(current)), actions(f, count))
            assertEquals(card, f.card(40))
            ui.stackContents()
            ui.openCard(TableScope(card.id, listOf(card.id))) // Manage Stack's Card actions uses the REAL identity.
            assertNull(ui.panel)
            assertEquals(card.id, ui.capture!!.contextViewId)
            assertEquals(listOf(card.id), ui.capture!!.sourceIds)
        } finally { f.lobby.close(); f.worker.drain() }
    }

    @Test fun fourthBubbleFitsInwardWithoutOverlapsInPortraitLandscapeAndSmallLandscape() {
        val f = TableStatesFixture
        for ((width, height) in listOf(412f to 892f, 892f to 412f, 640f to 360f)) {
            val state = f.combat()
            val scene = TablePresentation.scene(f.game, state, f.a, width, height, width > height)
            val bubble = TablePresentation.stackBubbles(scene, state, CombatStackRules.stack(state.board).cards.first(), width, height)!!
            assertEquals(5, bubble.controls.size)
            for (control in bubble.controls) {
                assertEquals(control.bounds, control.bounds.intersection(TableBounds(0f, 0f, width, height)))
                assertTrue(bubble.controls.filter { it != control }.none { it.bounds.intersection(control.bounds) != null })
            }
            val remove = bubble.controls.single { it.id.endsWith("/remove") }.bounds
            val above = bubble.controls.single { it.id.endsWith("/battlefield") }.bounds
            val inward = bubble.controls.single { it.id.endsWith("/mark") }.bounds
            assertEquals(above.y, remove.y)
            assertEquals(inward.x, remove.x)
        }
    }
}
