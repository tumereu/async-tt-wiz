package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Crash safety (2026-11-11 leftovers): the table's lookups on a board that breaks their assumptions fall back and report the
 * fault instead of throwing. */
class TableLookupFaultsTest {
    private fun reported(block: () -> Unit): List<Fault> {
        val faults = mutableListOf<Fault>()
        val sink = Faults.sink
        Faults.sink = { faults += it }
        try { block() } finally { Faults.sink = sink }
        return faults
    }

    private fun move(id: String, source: String, destination: TableDestination, vararg more: String) = TableIntent.Move(
        TableScope(id, listOf(id, *more)), destination, (listOf(id) + more).associateWith { TableMoveSource(source) }, "invocation")

    // Two moved cards whose container the board doesn't have are still ordered (it threw in the sort before) and sent; reported
    @Test fun cardsInNoContainerAreOrderedAndReported() {
        val play = TableFixture.play(hand = 2).let { p -> p.copy(board = p.board.copy(cards = p.board.cards.map {
            if (it.containerId == "a/HAND") it.copy(containerId = "nowhere") else it })) }
        val sent = mutableListOf<PlayAction>()
        val controller = BasicTableController({ tableBoard(play) }, { true }) { action, _ -> sent += action; true }
        val faults = reported {
            assertTrue(controller.handle(move("a/HAND/0", "nowhere", TableDestination.Grid("a/BATTLEFIELD", 3, 1), "a/HAND/1")))
        }
        assertEquals(listOf("a/HAND/0", "a/HAND/1"), sent.map { (it as PlayAction.MoveCard).cardId })
        assertEquals(listOf("BasicTableController: cards in no container"), faults.map { it.key })
    }

    // A card gone from the board shown by the time its move is sent stops the move (it threw before); reported
    @Test fun aCardGoneFromTheNewerBoardStopsTheMove() {
        val play = TableFixture.play(hand = 2)
        val gone = play.copy(board = play.board.copy(cards = play.board.cards.filter { it.id != "a/HAND/0" }))
        var reads = 0
        val sent = mutableListOf<PlayAction>()
        val controller = BasicTableController({ tableBoard(if (reads++ == 0) play else gone) }, { true }) { action, _ -> sent += action; true }
        val faults = reported {
            assertFalse(controller.handle(move("a/HAND/0", "a/HAND",
                TableDestination.Container("a/HAND", PlayZone.HAND, handGap = IdentityGap(afterId = "a/HAND/1")))))
        }
        assertTrue(sent.isEmpty())
        assertEquals(listOf("BasicTableController: card not on the board"), faults.map { it.key })
    }

    // ZON-073's lookup: a card the board doesn't hold has no menu state (null), reported
    @Test fun aCardForActionsNotOnTheBoardIsNullAndReported() {
        val faults = reported { assertNull(TablePresentation.cardForActions(tableBoard(TableFixture.play()), "missing")) }
        assertEquals(listOf("TablePresentation: card for actions not on the board"), faults.map { it.key })
    }

    // The table is drawn for two players: a third player's board still draws (the first other player at the top), and so do a
    // board without the other player or without the viewer, each reported (it threw before)
    @Test fun aBoardWithoutTwoPlayersStillDrawsReported() {
        val play = TableFixture.play(board = 1)
        fun drawn(players: List<String>): List<Fault> = reported {
            val board = play.copy(board = play.board.copy(players = players.map { PlayPlayer(it) }))
            val scene = TablePresentation.scene(TableFixture.game, tableBoard(board), "a", 412f, 820f, false)
            assertTrue(scene.items.any { it.cardId == "a/BATTLEFIELD/0" }, "the viewer's battlefield card drawn")
        }
        assertEquals(emptyList(), drawn(listOf("a", "b")).map { it.key })
        assertEquals(listOf("TablePresentation: table drawn for 2 other players"), drawn(listOf("a", "b", "c")).map { it.key })
        assertEquals(listOf("TablePresentation: table drawn for 0 other players"), drawn(listOf("a")).map { it.key })
        assertEquals(listOf("TablePresentation: table drawn without the viewer on the board"), drawn(listOf("b")).map { it.key })
    }
}
