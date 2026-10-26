package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

class TableActionWiringTest {
    @Test fun actualSelectedDragSendsOrderedPileRequestsWithoutWaitingForAcknowledgements() {
        val f = BasicTableFixture(); f.connect()
        f.move(40, TableDestination.Grid(f.container(PlayZone.BATTLEFIELD), 0, 1))
        f.move(41, TableDestination.Grid(f.container(PlayZone.BATTLEFIELD), 1, 1))
        f.move(42, TableDestination.Grid(f.container(PlayZone.BATTLEFIELD), 3, 1))
        var selection = TableSelection(setOf(f.id(40), f.id(41)))
        val gestures = TableGestureCoordinator({ intent ->
            if (intent is TableIntent.Selection) selection = intent.selection
            f.basic.handle(intent)
        })
        val scene = TablePresentation.scene(f.game, f.play, f.actor, 900f, 600f, true)
        gestures.update(scene, selection, true)
        val source = scene.items.single { it.cardId == f.id(40) }
        val target = scene.items.single { it.cardId == f.id(42) }
        val count = f.commands().size
        gestures.down(source.visible.first().center, 0)
        gestures.move(target.visible.first().center, 100)
        assertEquals(f.id(42), (gestures.preview.destination as TableDestination.CardPile).cardId)
        assertEquals(count, f.commands().size)
        // Reflow changes rendered geometry/selection, not the captured selected set or target.
        gestures.update(scene.copy(items = scene.items.reversed()), TableSelection(setOf(f.id(43))), true)
        gestures.up(target.visible.first().center, 150)
        val commands = f.commands().drop(count)
        assertEquals(listOf(f.id(40), f.id(41)), commands.map { ((it.action as GameAction.Play).action as PlayAction.MoveCard).cardId })
        assertEquals(1, commands.map { it.invocationId }.distinct().size)
        assertEquals(listOf(f.id(40), f.id(41), f.id(42)), f.play.board.containers.single { it.id == f.container(PlayZone.BATTLEFIELD) }.cards)
        assertEquals(selection.ids, setOf(f.id(40), f.id(41)))
        assertEquals(f.root, f.lobby.state.acceptedPlay)
    }

    @Test fun productionCoordinatorDrawDragAndDoubleTapReachRealLobbyRequestsWhileHoldAndCameraStayPersonal() {
        val f = BasicTableFixture(); f.connect()
        var selection = TableSelection()
        val personal = mutableListOf<TableIntent>()
        val gestures = TableGestureCoordinator({ intent ->
            if (intent is TableIntent.Selection) selection = intent.selection
            if (!f.basic.handle(intent)) personal += intent
        })
        fun scene() = TablePresentation.scene(f.game, f.play, f.actor, 412f, 900f, false)
        fun refresh() = gestures.update(scene(), selection, f.game.id in f.lobby.state.connectedGames)
        refresh()
        val library = scene().items.single { it.id == f.container(PlayZone.LIBRARY) }
        val start = library.visible.first().center
        val hand = scene().items.single { it.id == f.container(PlayZone.HAND) }.visible.first().center
        gestures.down(start, 0); gestures.move(hand, 80)
        assertTrue(f.commands().isEmpty()); gestures.up(hand, 100)
        assertEquals(1, f.commands().size); assertEquals(f.container(PlayZone.HAND), f.card(40).containerId)
        refresh()
        val card = scene().items.single { it.cardId == f.id(40) }.visible.first().center
        gestures.down(card, 200); gestures.up(card, 220); gestures.down(card, 300); gestures.up(card, 320)
        gestures.tick(900); assertEquals(2, f.commands().size); assertFalse(f.card(40).tapped)
        assertEquals(f.id(40), assertIs<StackAction.AddEffect>(assertIs<PlayAction.Stack>(
            (f.commands().last().action as GameAction.Play).action).action).sourceId)
        refresh()
        val point = scene().items.single { it.cardId == f.id(40) }.visible.first().center
        gestures.down(point, 1000); gestures.tick(1500); gestures.up(point, 1520); gestures.tick(1900)
        assertEquals(2, f.commands().size)
        assertEquals(listOf(TableIntent.Peek(f.id(40)), TableIntent.Peek(null)), personal.filterIsInstance<TableIntent.Peek>())
        assertEquals(1, personal.filterIsInstance<TableIntent.Actions>().size)
        assertEquals(1, personal.count { it == TableIntent.DismissTapUi })
        gestures.transform(TablePoint(10f, 0f), 1.2f, scene().fields.last().bounds.center)
        assertEquals(2, f.commands().size)
        assertNotNull(TablePresentation.inspection(f.play, f.actor, f.id(40)))
        // Projecting from Hand reveals the same source face as dragging to the Stack.
        assertEquals("Retained face", TablePresentation.inspection(f.play, f.other, f.id(40))!!.name)
    }

    @Test fun lifeAndZeroCounterEmitOneRelativeRequestOnReleaseOnlyAndRespectCancelAndDisconnect() {
        val f = BasicTableFixture()
        f.connect(f.root.copy(board = f.root.board.copy(players = f.root.board.players.map {
            if (it.accountId == f.actor) it.copy(counters = mapOf(CounterColor.BLUE to "1")) else it
        })))
        val gestures = TableGestureCoordinator({ f.basic.handle(it) })
        fun scene() = TablePresentation.scene(f.game, f.play, f.actor, 412f, 900f, false)
        gestures.update(scene(), TableSelection(), true)
        val life = scene().items.single { it.id == "life/${f.actor}" }.visible.first().center
        gestures.down(life, 0); gestures.move(TablePoint(life.x, life.y - 72), 100)
        assertTrue(f.commands().isEmpty()); assertEquals("20", f.play.board.players.first().life)
        gestures.up(TablePoint(life.x, life.y - 72), 150)
        assertEquals(PlayAction.AdjustLife(f.actor, "-1"), (f.commands().single().action as GameAction.Play).action)
        assertEquals("19", f.play.board.players.first().life)
        gestures.down(life, 200); gestures.move(TablePoint(life.x, life.y - 48), 250); gestures.cancel()
        assertEquals(1, f.commands().size)
        gestures.down(life, 300); gestures.move(TablePoint(life.x, life.y - 48), 350)
        gestures.update(scene(), TableSelection(), false); gestures.up(TablePoint(life.x, life.y - 48), 400)
        assertEquals(1, f.commands().size)
        // A captured landscape counter survives portrait reflow and becomes an empty slot at zero.
        val landscape = TablePresentation.scene(f.game, f.play, f.actor, 900f, 500f, true)
        val counter = landscape.items.single { it.id == "counter/${f.actor}/BLUE" }
        gestures.update(landscape, TableSelection(), true)
        gestures.down(counter.bounds.center, 500); gestures.move(TablePoint(counter.bounds.center.x, counter.bounds.center.y - 48), 550)
        gestures.update(scene(), TableSelection(), true)
        gestures.up(TablePoint(counter.bounds.center.x, counter.bounds.center.y - 48), 600)
        assertEquals(PlayAction.AdjustCounter(CounterTarget.PLAYER, f.actor, CounterColor.BLUE, "-1"),
            (f.commands().last().action as GameAction.Play).action)
        assertEquals("", scene().items.single { it.id == counter.id }.value)
        assertTrue(TablePresentation.scene(f.game, f.play, f.actor, 900f, 500f, true).items.none { it.id == counter.id })
    }

    @Test fun capturedLibraryMenuAndMoveDestinationDoNotRetargetAfterSyncOrReflow() {
        val f = BasicTableFixture(); f.connect()
        val events = mutableListOf<TableIntent>()
        val gestures = TableGestureCoordinator(events::add)
        val initial = TablePresentation.scene(f.game, f.play, f.actor, 412f, 900f, false)
        gestures.update(initial, TableSelection(), true)
        val library = initial.items.single { it.id == f.container(PlayZone.LIBRARY) }
        gestures.actions(library)
        val captured = requireNotNull(gestures.capturedActions)
        assertEquals(f.id(40), captured.topId)
        val scope = TableScope(captured.topId!!, listOf(captured.topId))
        f.move(40, TableDestination.Container(f.container(PlayZone.HAND), PlayZone.HAND), true)
        val count = f.commands().size
        assertFalse(f.basic.handle(TableIntent.Move(scope, TableDestination.Grid(f.container(PlayZone.BATTLEFIELD), 0, 0), captured.sources)))
        assertEquals(count, f.commands().size)
        val scene = TablePresentation.scene(f.game, f.play, f.actor, 412f, 900f, false)
        gestures.update(scene, TableSelection(), true)
        val card = scene.items.single { it.cardId == f.id(40) }.visible.first().center
        val destination = scene.items.single { it.id == f.container(PlayZone.EXILE) }.visible.first().center
        events.clear(); gestures.down(card, 0); gestures.move(destination, 100)
        val preview = gestures.preview.destination
        gestures.update(scene.copy(items = scene.items.reversed()), TableSelection(setOf(f.id(43))), true)
        gestures.up(destination, 150)
        assertEquals(preview, events.filterIsInstance<TableIntent.Move>().single().destination)
        assertEquals(listOf(f.id(40)), events.filterIsInstance<TableIntent.Move>().single().scope.cardIds)
    }
}
