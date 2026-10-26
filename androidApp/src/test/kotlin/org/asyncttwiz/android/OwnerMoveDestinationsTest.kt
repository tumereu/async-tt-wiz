package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

class OwnerMoveDestinationsTest {
    @Test fun opponentControlledCardsMoveToTheirOwnersZonesViaMenuAndDrag() {
        for (zone in ownerOnlyZones) for (menu in listOf(true, false)) {
            val f = BasicTableFixture()
            val battlefield = f.container(PlayZone.BATTLEFIELD, f.other)
            f.connect(f.root.copy(board = f.root.board.copy(
                cards = f.root.board.cards.mapIndexed { index, card -> card.copy(containerId = battlefield,
                    placement = PlayPlacement(BoardPosition(index, 1), f.id(800 + index))) },
                containers = f.root.board.containers.map { it.copy(cards =
                    if (it.id == battlefield) (40..43).map(f::id) else emptyList()) })))
            try {
                assertEquals(f.actor, f.card(40).owner)
                assertEquals(f.other, f.play.board.containers.single { it.id == f.card(40).containerId }.accountId)
                if (menu) {
                    val scope = TableScope(f.id(40), listOf(f.id(40)))
                    val tools = basicMoveTools(f.play, emptyMap(), true, scope,
                        BasicTableController.sources(f.play, scope.cardIds), f.basic::handle, {})
                    tools.single { it.name == moveZoneLabel(zone) }.click()
                } else {
                    val scene = TablePresentation.scene(f.game, f.play, f.actor, 900f, 600f, true)
                    val gestures = TableGestureCoordinator({ f.basic.handle(it) })
                    gestures.update(scene, TableSelection(), true)
                    val start = scene.items.single { it.cardId == f.id(40) }.visible.first().center
                    val target = scene.items.single { it.id == f.container(zone, f.other) }.bounds.center
                    gestures.down(start, 0); gestures.move(target, 80)
                    assertEquals(f.container(zone), assertIs<TableDestination.Container>(gestures.preview.destination).id)
                    gestures.up(target, 100)
                }
                assertEquals(f.container(zone, f.actor), f.card(40).containerId, "$zone via menu=$menu")
                assertEquals(f.actor, f.card(40).owner)
                val move = f.commands().mapNotNull { (it.action as? GameAction.Play)?.action as? PlayAction.MoveCard }.single()
                assertEquals(f.id(40), move.cardId)
                assertEquals(f.container(zone, f.actor), move.destination.containerId)
            } finally { f.lobby.close(); f.worker.drain() }
        }
    }

    private fun mixed(f: BasicTableFixture): PlayState = f.root.copy(board = f.root.board.copy(
        cards = f.root.board.cards.mapIndexed { index, card -> card.copy(owner = if (index % 2 == 1) f.other else f.actor,
            containerId = f.container(PlayZone.BATTLEFIELD), placement = PlayPlacement(BoardPosition(index, 1), f.id(800 + index))) },
        containers = f.root.board.containers.map { container -> container.copy(cards =
            if (container.id == f.container(PlayZone.BATTLEFIELD)) (40..43).map(f::id) else emptyList()) }))

    @Test fun menuOffersOneOwnerRelativeChoicePerZoneAndMixedSelectionsRoutePerOwner() {
        for (label in listOf("Hand", "Battlefield", "Library top", "Library bottom", "Graveyard", "Exile")) {
            val f = BasicTableFixture(); f.connect(mixed(f))
            try {
                val scope = TableScope(f.id(40), (40..43).map(f::id))
                val tools = basicMoveTools(f.play, mapOf(f.actor to "Alice", f.other to "Bob"), true, scope,
                    BasicTableController.sources(f.play, scope.cardIds), f.basic::handle, {})
                assertEquals(listOf("Hand", "Battlefield", "Library top", "Library bottom", "Graveyard", "Exile"), tools.take(6).map { it.name })
                assertTrue(tools.none { "Alice" in it.name || "Bob" in it.name || "owner" in it.name })
                tools.single { it.name == label }.click()
                val zone = when (label) {
                    "Hand" -> PlayZone.HAND
                    "Battlefield" -> PlayZone.BATTLEFIELD
                    "Graveyard" -> PlayZone.GRAVEYARD
                    "Exile" -> PlayZone.EXILE
                    else -> PlayZone.LIBRARY
                }
                for (card in f.play.board.cards) assertEquals(f.container(zone, card.owner), card.containerId)
                if (zone == PlayZone.LIBRARY) for (owner in listOf(f.actor, f.other)) assertEquals(
                    (40..43).map(f::id).filter { id -> f.play.board.cards.single { it.id == id }.owner == owner },
                    f.play.board.containers.single { it.id == f.container(zone, owner) }.cards)
                assertEquals(1, f.commands().map { it.invocationId }.distinct().size)
            } finally { f.lobby.close(); f.worker.drain() }
        }
    }

    @Test fun dragRedirectsEveryPersonalZoneAndLandingToOwnerButNotBattlefields() {
        for (zone in ownerOnlyZones) {
            val f = BasicTableFixture(); f.connect(mixed(f))
            try {
                val scene = TablePresentation.scene(f.game, f.play, f.actor, 900f, 600f, true)
                val gestures = TableGestureCoordinator({ f.basic.handle(it) })
                gestures.update(scene, TableSelection(setOf(f.id(40), f.id(42))), true)
                val start = scene.items.single { it.cardId == f.id(40) }.visible.first().center
                val target = scene.items.single { it.id == f.container(zone, f.other) }
                gestures.down(start, 0); gestures.move(target.bounds.center, 80)
                val destination = assertIs<TableDestination.Container>(gestures.preview.destination)
                assertEquals(f.container(zone), destination.id)
                val landing = tableDragLanding(scene, gestures.preview)
                val own = scene.items.single { it.id == f.container(zone) }
                if (zone == PlayZone.HAND) assertEquals(own.bounds, landing.hand)
                else assertTrue(landing.outlines.all { it.intersection(own.bounds) != null })
                gestures.up(target.bounds.center, 100)
                assertEquals(f.container(zone), f.card(40).containerId)
                assertEquals(f.container(zone), f.card(42).containerId)
            } finally { f.lobby.close(); f.worker.drain() }
        }
        val f = BasicTableFixture(); f.connect(mixed(f))
        try {
            assertTrue(f.move(40, TableDestination.Grid(f.container(PlayZone.BATTLEFIELD, f.other), 5, 1)))
            assertEquals(f.container(PlayZone.BATTLEFIELD, f.other), f.card(40).containerId)
        } finally { f.lobby.close(); f.worker.drain() }
    }

    @Test fun mixedOwnerHandDragHighlightsBothHandsAndDiscardsForeignGap() {
        val f = BasicTableFixture(); f.connect(mixed(f))
        try {
            val scene = TablePresentation.scene(f.game, f.play, f.actor, 412f, 900f, false)
            val gestures = TableGestureCoordinator({ f.basic.handle(it) })
            gestures.update(scene, TableSelection(setOf(f.id(40), f.id(41))), true)
            val start = scene.items.single { it.cardId == f.id(40) }.visible.first().center
            val otherHand = scene.items.single { it.id == f.container(PlayZone.HAND, f.other) }
            gestures.down(start, 0); gestures.move(otherHand.bounds.center, 80)
            val landing = tableDragLanding(scene, gestures.preview)
            assertEquals(setOf(f.container(PlayZone.HAND), otherHand.id).map { id -> scene.items.single { it.id == id }.bounds }.toSet(),
                (listOfNotNull(landing.hand) + landing.additionalHands.map { it.first }).toSet())
            gestures.up(otherHand.bounds.center, 100)
            assertEquals(f.container(PlayZone.HAND), f.card(40).containerId)
            assertEquals(otherHand.id, f.card(41).containerId)
        } finally { f.lobby.close(); f.worker.drain() }
    }

    @Test fun libraryContentsMovesRouteMixedOwnersAndKeepTopSliceGuardsInOriginalOrder() {
        val f = BasicTableFixture()
        val initial = f.root.copy(board = f.root.board.copy(cards = f.root.board.cards.mapIndexed { index, card ->
            card.copy(owner = if (index % 2 == 1) f.other else f.actor)
        }))
        f.connect(initial)
        try {
            val toolkit = TableToolkitController(f.actor, { f.play }, { true }, f.lobby::gameplay, playBatch = f.lobby::gameplayBatch)
            val library = toolkit.libraryController(f.container(PlayZone.LIBRARY), f.container(PlayZone.HAND))!!
            val choices = libraryDestinations(f.play.board, mapOf(f.actor to "Alice", f.other to "Bob"),
                sourceIds = listOf(f.id(40), f.id(41)))
            assertEquals(setOf("Hand", "Battlefield", "Library top", "Library bottom", "Graveyard", "Exile"), choices.map { it.first }.toSet())
            assertTrue(choices.all { (_, destination) -> f.play.board.containers.single { it.id == destination.containerId }.accountId == f.actor })
            assertEquals(0, library.move(ContentsSelection.TOP, 4, choices.single { it.first == "Exile" }.second).refused)
            for (card in f.play.board.cards) assertEquals(f.container(PlayZone.EXILE, card.owner), card.containerId)
            assertIs<GameAction.PlayBatch>(f.commands().single().action)
            val moves = f.commands().flatMap { it.playActions() }.filterIsInstance<PlayAction.MoveCard>()
            assertEquals((40..43).map(f::id), moves.map { it.cardId })
            assertTrue(moves.all { it.source.edge == MoveSourceEdge.TOP })
            assertEquals(0, library.moveCards((40..43).map(f::id),
                MoveDestination(f.container(PlayZone.LIBRARY), MoveInsertion.BOTTOM)).refused)
            for (owner in listOf(f.actor, f.other)) assertEquals(
                (40..43).map(f::id).filter { id -> f.play.board.cards.single { it.id == id }.owner == owner },
                f.play.board.containers.single { it.id == f.container(PlayZone.LIBRARY, owner) }.cards)
        } finally { f.lobby.close(); f.worker.drain() }
    }
}
