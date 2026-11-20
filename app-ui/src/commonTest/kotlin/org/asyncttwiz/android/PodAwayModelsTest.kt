package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** "While you were away" in Rotisserie (DRF-239): what it tells, worked out from the picks and how many you have seen. */
class PodAwayModelsTest {
    private val alice = AccountIdentity("alice", "Alice")
    private val bob = AccountIdentity("bob", "Bob")
    private fun copy(n: Int) = DraftCopy("copy-$n", "def-$n")
    private val definitions = (0..9).associate { "def-$it" to CardDefinition("Card $it", listOf(DefinitionFace("face", "Card $it"))) }
    private fun view(vararg picks: LimitedPickView, mode: DraftMode = DraftMode.ROTISSERIE) =
        LimitedView(mode, "alice", listOf("alice", "bob"), emptyList(), false, picks = picks.toList())

    // DRF-239: the picks after the seen ones, oldest first and numbered in the whole draft; the take that cleared your queue named
    @Test fun thePicksSinceYouLastLookedAndTheClearedQueue() {
        val view = view(LimitedPickView("alice", listOf(copy(0))), LimitedPickView("bob", listOf(copy(1))),
            LimitedPickView("bob", listOf(copy(2)), clearedQueue = true))
        val away = podRotisserieAway(view, 1)!!
        assertEquals(listOf(2, 3), away.picks.map { it.number })
        assertEquals(copy(2), away.clearedBy?.card)
        assertNull(away.ranOutAt)
        assertEquals(listOf("Your queue was cleared: Bob took Card 2"), podAwayQueueTexts(away, "alice", listOf(alice, bob), definitions))
        assertEquals("2 picks since you last looked", podAwayCountText(2))
        assertEquals("1 pick since you last looked", podAwayCountText(1))
        assertEquals("Pick 2 · Bob · Card 1", podAwayPickText(away.picks[0], "alice", listOf(alice, bob), definitions))
    }

    // DRF-239: your own picks taken from your queue are told, "from your queue", and its last card ran it out
    @Test fun yourQueuedPicksAndTheQueueRunningOut() {
        val view = view(LimitedPickView("bob", listOf(copy(0))), LimitedPickView("alice", listOf(copy(1)), fromQueue = true),
            LimitedPickView("alice", listOf(copy(2)), fromQueue = true, queueRanOut = true))
        val away = podRotisserieAway(view, 1)!!
        assertEquals(listOf(true, true), away.picks.map { it.fromQueue })
        assertEquals(copy(2), away.ranOutAt?.card)
        assertEquals(listOf("Your queue ran out: Card 2 was its last card"), podAwayQueueTexts(away, "alice", listOf(alice, bob), definitions))
        assertEquals("Pick 3 · You · Card 2 · from your queue", podAwayPickText(away.picks[1], "alice", listOf(alice, bob), definitions))
    }

    // DRF-239, LOB-090 (owner rule 2026-11-11: a stand-in shown is a fault): a picker without a name reads "Another player" and is
    // reported; so does a table chip's
    @Test fun aPickerWithoutANameIsReported() {
        val faults = mutableListOf<Fault>()
        val sink = Faults.sink
        Faults.sink = { faults += it }
        try {
            val away = podRotisserieAway(view(LimitedPickView("bob", listOf(copy(0)))), 0)!!
            assertEquals("Pick 1 · Another player · Card 0", podAwayPickText(away.picks[0], "alice", listOf(alice), definitions))
            assertEquals("Table 1 · Another player picks", podTableChipText(0, LimitedTableView("bob", 0, 0, emptyList()), "alice", listOf(alice)))
        } finally { Faults.sink = sink }
        assertEquals(listOf("PodAwayView", "PodLimitedView"), faults.map { it.area })
        assertEquals("Another player", faults.first().details["shown"])
    }

    // DRF-240: nothing to tell when every pick is seen, when the only new picks are your own by hand, and outside Rotisserie
    @Test fun nothingToTell() {
        val picks = arrayOf(LimitedPickView("bob", listOf(copy(0))), LimitedPickView("alice", listOf(copy(1))))
        assertNull(podRotisserieAway(view(*picks), 2))
        assertNull(podRotisserieAway(view(*picks), 1))
        assertNull(podRotisserieAway(view(*picks, mode = DraftMode.ROCHESTER), 0))
        assertEquals(2, podRotisserieAway(view(*picks), 0)?.picks?.size)
    }
}
