package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Crash safety: a board [EngineStaging] can't work a move out on refuses it with a reason and reports it, never crashes. */
class EngineStagingFaultsTest {
    private fun reported(block: () -> Unit): List<Fault> {
        val faults = mutableListOf<Fault>()
        val sink = Faults.sink
        Faults.sink = { faults += it }
        try { block() } finally { Faults.sink = sink }
        return faults
    }

    private val REQUEST = "6f1c2b3a-4d5e-4f60-8a7b-9c0d1e2f3a4b"
    private val at = PlayPlacement(BoardPosition(0, 0), "pile-0")
    private val card = PlayCard("a/1", "digest", "a", "a/BATTLEFIELD", "face", covered = false, placement = at)
    private fun board(members: List<String>) = BoardState(listOf(PlayPlayer("a"), PlayPlayer("b")), listOf(card),
        listOf(PlayContainer("a/BATTLEFIELD", PlayZone.BATTLEFIELD, "a", members)), TurnState("a"))
    private val move = PlayAction.MoveCard(card.id, MoveSource(card.containerId, at),
        MoveDestination(card.containerId, MoveInsertion.EMPTY, BoardPosition(2, 0)))

    // A card moved within its own Battlefield to an empty cell lands there (the fallback leaves a sound board alone)
    @Test fun aCardMovesToAnEmptyCell() {
        val after = EngineStaging.apply(board(listOf(card.id)), emptyList(), "a", move, REQUEST)
        assertEquals(BoardPosition(2, 0), after.cards.single().placement?.position)
    }

    // A Battlefield naming a card the board doesn't hold refuses the move as a missing reference, reported (it threw
    // NoSuchElementException before, which the routes don't take as a refusal)
    @Test fun aBattlefieldMemberNotOnTheBoardIsRefusedAndReported() {
        val faults = reported {
            val refused = assertFailsWith<IllegalArgumentException> {
                EngineStaging.apply(board(listOf(card.id, "a/ghost")), emptyList(), "a", move, REQUEST)
            }
            assertEquals("missingReference", refused.message)
        }
        assertEquals(listOf("EngineStaging: battlefield member not on the board"), faults.map { it.key })
        assertEquals("a/ghost", faults.single().details["cards"])
    }
}
