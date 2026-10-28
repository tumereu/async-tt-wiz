package org.asyncttwiz.protocol

import kotlin.test.*

/** Queues run fresh packs first, never reorder a visible head, and views tell where each pack came from. */
class DraftQueueOrderTest {
    private val seed = RandomV1.encodeSeed(ByteArray(32) { (it * 11).toByte() })

    private fun start(players: Int, packs: Int, cards: Int, keep: Int, burn: Int, open: Int = 1): DraftState =
        DraftEngine.startWithPacks(List(players) { "p$it" },
            List(players * packs) { pack -> List(cards) { DraftCopy("c$pack-$it", "d$pack-$it") } },
            DraftSettings(packsPerPlayer = packs, cardsPerPack = cards, keep = keep, burn = burn, packsOpenAtOnce = open,
                colorBalance = false), seed)

    private fun pick(state: DraftState, player: String): DraftState {
        val id = state.queues.getValue(player).first()
        val cards = state.packs.first { it.id == id }.cards
        val keep = state.settings.keep
        val burn = if (cards.size <= keep + state.settings.burn) emptyList() else cards.drop(keep).take(state.settings.burn)
        val result = DraftEngine.pick(state, DraftPick(player, id, cards.take(keep), burn))
        assertNull(result.reason)
        return assertNotNull(result.state)
    }

    private fun picksTaken(state: DraftState, id: String) = state.log.count { it.packId == id && !it.automatic }
    private fun rank(state: DraftState, id: String) = state.packs.first { it.id == id }.round to picksTaken(state, id)
    private val order = compareBy<Pair<Int, Int>>({ it.first }, { it.second })

    @Test fun ownFreshPacksComeBeforeHeldHalfPickedPacksWhenARoundOpens() {
        // 18 packs per player, 3 open at once (6 rounds), 15 cards, keep 1 burn 4: every pack sees 15 -> 10 -> 5 -> 0.
        var state = start(players = 2, packs = 18, cards = 15, keep = 1, burn = 4, open = 3)
        val (a, b) = state.seating
        var steps = 0
        // A picks whenever possible, so A opens the last round first and its packs wait, held, for B.
        while (state.openedRound(b) < 5) {
            assertTrue(steps++ < 1000)
            state = pick(state, if (state.queues.getValue(a).isNotEmpty()) a else b)
        }
        assertEquals(5, state.openedRound(a))
        val queue = state.queues.getValue(b)
        val packs = queue.map { id -> state.packs.first { it.id == id } }
        assertEquals(List(3) { b }, packs.take(3).map { it.initialPlayer }, "B's own fresh packs come first")
        assertTrue(packs.take(3).all { it.cards.size == 15 })
        assertEquals(List(3) { a }, packs.drop(3).map { it.initialPlayer })
        assertTrue(packs.drop(3).all { it.cards.size == 10 && it.round == 5 })

        val view = DraftEngine.privateView(state, b)
        assertEquals(1, view.head!!.pick)
        assertNull(view.head!!.passedBy)
        assertEquals(List(3) { 2 }, view.queuedPacks.drop(2).map { it.pick })
        assertEquals(List(3) { a }, view.queuedPacks.drop(2).map { it.passedBy })
        assertEquals(6 to 6, view.round to view.rounds)
    }

    @Test fun arrivalsTakeTheirSortedPlaceButNeverGoAheadOfTheHead() {
        // One round, 2 packs each, keep 1 burn 0. b's head has three picks taken, the pack behind it two.
        val settings = DraftSettings(packsPerPlayer = 2, cardsPerPack = 6, keep = 1, burn = 0, packsOpenAtOnce = 2, colorBalance = false)
        val cards = List(4) { pack -> List(6) { "c$pack-$it" } }
        val copies = cards.flatten().map { DraftCopy(it, "d-$it") }
        val packs = listOf(
            DraftPack("pack-0", 0, "a", 1, cards[0].drop(3)),
            DraftPack("pack-1", 0, "b", 1, cards[1].drop(2)),
            DraftPack("pack-2", 0, "a", 2, cards[2]),
            DraftPack("pack-3", 0, "b", 2, emptyList()),
        )
        val log = listOf(
            DraftPickLog("a", "pack-0", 0, listOf("c0-0")), DraftPickLog("b", "pack-0", 0, listOf("c0-1")),
            DraftPickLog("a", "pack-0", 0, listOf("c0-2")),
            DraftPickLog("b", "pack-1", 0, listOf("c1-0")), DraftPickLog("a", "pack-1", 0, listOf("c1-1")),
            DraftPickLog("b", "pack-3", 0, cards[3], automatic = true),
        )
        var state = DraftState(settings, listOf("a", "b"), copies, packs, 0,
            mapOf("a" to listOf("pack-2"), "b" to listOf("pack-0", "pack-1")),
            mapOf("a" to listOf("c0-0", "c0-2", "c1-1"), "b" to listOf("c0-1", "c1-0") + cards[3]), log, mapOf("a" to 0, "b" to 0))
        state = pick(state, "a") // fresh pack-2 now has one pick: behind the head, ahead of the two-pick pack-1
        assertEquals(listOf("pack-0", "pack-2", "pack-1"), state.queues.getValue("b"))
        val view = DraftEngine.privateView(state, "b")
        assertEquals(4, view.head!!.pick)
        assertEquals("a", view.head!!.passedBy)
        assertEquals(listOf(2 to "a", 3 to "a"), view.queuedPacks.map { it.pick to it.passedBy })
    }

    @Test fun visibleQueuesStaySortedBehindAnUnchangedHead() {
        val players = 3
        var state = start(players = players, packs = 4, cards = 9, keep = 1, burn = 1, open = 2)
        var steps = 0
        while (!state.finished) {
            assertTrue(steps++ < 1000)
            val ready = state.seating.filter { state.queues.getValue(it).isNotEmpty() }
            val picker = ready[(steps * 7) % ready.size]
            val before = state
            state = pick(state, picker)
            for (player in state.seating) {
                val queue = state.queues.getValue(player)
                val old = before.queues.getValue(player)
                if (player != picker && old.isNotEmpty()) assertEquals(old.first(), queue.first(), "An arrival moved the head")
                // A non-empty queue that just opened a round is sorted throughout; otherwise everything behind the head is.
                val sorted = if (old.isEmpty() || player == picker && old.size == 1) queue else queue.drop(1)
                assertEquals(sorted.sortedWith(compareBy(order) { rank(state, it) }), sorted)
            }
        }
    }

    @Test fun incomingCountsOnlyPacksThatCanStillComeBackForAManualPick() {
        // 3 cards, keep 1: 3 -> 2 -> 1 (automatic award), so each pack is picked by its opener and then the other player.
        var state = start(players = 2, packs = 1, cards = 3, keep = 1, burn = 0)
        val (a, b) = state.seating
        assertEquals(1, DraftEngine.privateView(state, a).incoming) // b's pack comes to a after b's pick
        state = pick(state, a)
        // a's own pack is with b for its final manual pick; only b's pack still comes back to a.
        assertEquals(1, DraftEngine.privateView(state, a).incoming)
        assertEquals(0, DraftEngine.privateView(state, b).incoming) // both packs are already in b's queue
        state = pick(state, b)
        assertEquals(0, DraftEngine.privateView(state, a).incoming) // it arrived: it is in a's queue now
        state = pick(state, b)
        assertEquals(0, DraftEngine.privateView(state, b).incoming)
        // 5 cards, keep 1: 5 -> 4 -> 3 -> 2 -> 1, so a's own pack comes back to a too.
        state = pick(start(players = 2, packs = 1, cards = 5, keep = 1, burn = 0), a)
        assertEquals(2, DraftEngine.privateView(state, a).incoming)
    }
}
