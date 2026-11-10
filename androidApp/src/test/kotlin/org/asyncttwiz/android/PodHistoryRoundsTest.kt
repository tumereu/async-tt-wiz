package org.asyncttwiz.android

import org.asyncttwiz.protocol.DraftHistoryPick
import org.junit.Test
import kotlin.test.assertEquals

/** The draft history's rounds (DRF-165, owner 2026-11-10): headers with the passing direction and the returning filter. */
class PodHistoryRoundsTest {
    private fun pick(round: Int, pick: Int, returning: Boolean = false) =
        DraftHistoryPick(round, pick, emptyList(), emptyList(), round = round, returning = returning)

    // DRF-165, DRF-043: round 1 passes left, and the direction flips each round
    @Test fun roundHeadersSayWhichWayThePacksPassed() {
        assertEquals("Round 1 · passed left", podHistoryRoundHeader(1))
        assertEquals("Round 2 · passed right", podHistoryRoundHeader(2))
        assertEquals("Round 3 · passed left", podHistoryRoundHeader(3))
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
