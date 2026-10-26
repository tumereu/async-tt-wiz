package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

class CardMenuMovesTest {
    @Test fun currentZoneMovesAreDisabledAndTokensHaveNoHandOrLibraryMoves() {
        val f = BasicTableFixture()
        for (zone in listOf(PlayZone.BATTLEFIELD, PlayZone.HAND, PlayZone.STACK)) for (token in listOf(false, true)) {
            val card = f.root.board.cards.first().copy(containerId = f.container(zone), token = token)
            val play = f.root.copy(board = f.root.board.copy(cards = f.root.board.cards.map { if (it.id == card.id) card else it },
                containers = f.root.board.containers.map { container -> container.copy(cards = container.cards.filterNot { it == card.id } +
                    if (container.id == card.containerId) listOf(card.id) else emptyList()) }))
            val scope = TableScope(card.id, listOf(card.id))
            val controls = basicMoveTools(play, emptyMap(), true, scope, BasicTableController.sources(play, scope.cardIds), { true }, {})
                .associateBy { it.name }
            if (token) {
                assertFalse("Hand" in controls)
                assertFalse("Library top" in controls)
                assertFalse("Library bottom" in controls)
                assertFalse("Shuffle into library" in controls)
            } else {
                assertEquals(zone != PlayZone.HAND, controls.getValue("Hand").enabled)
                assertTrue(controls.getValue("Library top").enabled)
                assertTrue(controls.getValue("Shuffle into library").enabled)
            }
            assertEquals(zone != PlayZone.BATTLEFIELD, controls.getValue("Battlefield").enabled)
        }
    }

    @Test fun shuffleIntoLibraryMovesThenShufflesOwnersLibraryWithOneInvocation() {
        val f = BasicTableFixture(); f.connect()
        try {
            assertTrue(f.move(40, TableDestination.Grid(f.container(PlayZone.BATTLEFIELD), 0, 0)))
            val scope = TableScope(f.id(40), listOf(f.id(40)))
            val before = f.commands().size
            var dismissed = false
            basicMoveTools(f.play, emptyMap(), true, scope, BasicTableController.sources(f.play, scope.cardIds),
                f.basic::handle, { dismissed = true }).single { it.name == "Shuffle into library" }.click()
            val commands = f.commands().drop(before)
            val actions = commands.map { (it.action as GameAction.Play).action }
            assertEquals(f.container(PlayZone.LIBRARY), assertIs<PlayAction.MoveCard>(actions.first()).destination.containerId)
            assertEquals(f.container(PlayZone.LIBRARY), assertIs<PlayAction.ShuffleLibrary>(actions.last()).containerId)
            assertEquals(1, commands.map { it.invocationId }.distinct().size)
            assertTrue(dismissed)
        } finally { f.lobby.close(); f.worker.drain() }
    }
}
