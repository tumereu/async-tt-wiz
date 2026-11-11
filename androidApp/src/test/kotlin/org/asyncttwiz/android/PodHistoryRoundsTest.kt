package org.asyncttwiz.android

import org.asyncttwiz.protocol.DraftCopy
import org.asyncttwiz.protocol.DraftHistoryPick
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The draft history's rounds (DRF-165, owner 2026-11-10 and 2026-11-11): headers naming where the packs went and the
 * returning filter. */
class PodHistoryRoundsTest {
    private fun pick(round: Int, pick: Int, returning: Boolean = false) =
        DraftHistoryPick(round, pick, emptyList(), emptyList(), round = round, returning = returning)

    private val names = mapOf("alice" to "Alice", "bob" to "Bob", "carol" to "Carol", "dave" to "Dave")

    // DRF-165, DRF-043: round 1 passes to the next seat, the direction flipping each round; with two players the header
    // names only the player, with three or more also the way round
    @Test fun roundHeadersNameWhereThePacksWent() {
        val two = listOf("bob", "alice")
        assertEquals("Round 1 · passed to Bob", podHistoryRoundHeader(1, two, "alice", names::getValue))
        assertEquals("Round 2 · passed to Bob", podHistoryRoundHeader(2, two, "alice", names::getValue))
        val four = listOf("alice", "bob", "carol", "dave")
        assertEquals("Round 1 · passed to Dave · clockwise", podHistoryRoundHeader(1, four, "carol", names::getValue))
        assertEquals("Round 2 · passed to Bob · counter-clockwise", podHistoryRoundHeader(2, four, "carol", names::getValue))
        assertEquals("Round 3 · passed to Alice · clockwise", podHistoryRoundHeader(3, four, "dave", names::getValue))
        // Without the seating, the round alone
        assertEquals("Round 1", podHistoryRoundHeader(1, emptyList(), "alice", names::getValue))
        assertNull(podHistoryPassedTo(four, "eve", 1))
    }

    // DRF-165: the seat ring follows the round of the item at the top of the list
    @Test fun eachListItemBelongsToItsRound() {
        fun copy(n: Int) = DraftCopy("c$n", "d$n")
        val picks = listOf(DraftHistoryPick(1, 1, listOf(copy(1), copy(2)), emptyList()),
            DraftHistoryPick(1, 2, listOf(copy(3)), emptyList(), takenSince = listOf(copy(4), copy(5)), returning = true),
            DraftHistoryPick(2, 1, listOf(copy(6)), emptyList(), round = 2))
        // Round 1: its header, pick 1's header and 2 cards, pick 2's header, 1 card, the Taken since line and 2 cards; round 2:
        // its header, the pick's header and 1 card
        assertEquals(List(9) { 1 } + List(3) { 2 }, podHistoryItemRounds(podHistoryRounds(picks, returningOnly = false)))
    }

    // DRF-165: picks grouped by round in order, keeping their places; Returning packs only leaves only packs that came back,
    // and a round without one isn't shown
    @Test fun picksGroupByRoundAndTheFilterKeepsReturningPacks() {
        val picks = listOf(pick(1, 1), pick(1, 2), pick(1, 3, returning = true), pick(2, 1), pick(2, 2), pick(3, 1), pick(3, 3, returning = true))
        val all = podHistoryRounds(picks, returningOnly = false)
        assertEquals(listOf(1 to listOf(0, 1, 2), 2 to listOf(3, 4), 3 to listOf(5, 6)), all.map { (round, shown) -> round to shown.map { it.index } })
        val returning = podHistoryRounds(picks, returningOnly = true)
        assertEquals(listOf(1 to listOf(2), 3 to listOf(6)), returning.map { (round, shown) -> round to shown.map { it.index } })
        assertEquals(emptyList(), podHistoryRounds(picks.filter { !it.returning }, returningOnly = true))
        // A round's last card given automatically after the next round opened still shows under its own round
        val late = listOf(pick(1, 1), pick(1, 2), pick(2, 1), pick(1, 3, returning = true), pick(2, 2))
        assertEquals(listOf(1 to listOf(0, 1, 3), 2 to listOf(2, 4)),
            podHistoryRounds(late, returningOnly = false).map { (round, shown) -> round to shown.map { it.index } })
    }
}
