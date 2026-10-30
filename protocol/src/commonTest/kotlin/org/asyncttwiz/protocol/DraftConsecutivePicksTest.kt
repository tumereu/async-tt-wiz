package org.asyncttwiz.protocol

import kotlin.random.Random
import kotlin.test.*

/**
 * A member's pack count is the picks they can make in a row if nobody else acts: their queue, plus the next-round
 * packs they open themselves early, plus held packs that opening releases, as long as it keeps going.
 */
class DraftConsecutivePicksTest {
    private val seed = RandomV1.encodeSeed(ByteArray(32) { (it * 11).toByte() })
    private val librarianCard = CardDefinition("Cogwork Librarian", listOf(DefinitionFace("face", "Cogwork Librarian")))

    private fun start(players: Int, packs: Int, cards: Int, open: Int = 1, keep: Int = 1, burn: Int = 0, discard: Int = 0,
        librarians: Int = 0): DraftState = DraftEngine.startWithPacks(List(players) { "p$it" },
        List(players * packs) { pack -> List(cards) { DraftCopy("c$pack-$it", "d$pack-$it") } },
        DraftSettings(packsPerPlayer = packs, cardsPerPack = cards, keep = keep, burn = burn, discardRemainingAt = discard,
            packsOpenAtOnce = open, colorBalance = false, librarians = librarians,
            librarianDefinition = if (librarians > 0) SubmittedDefinition(DeckJson.digest(librarianCard), librarianCard) else null),
        seed)

    /** Pack opened by [seat] as its [number]th pack (one-based). */
    private fun pack(state: DraftState, seat: Int, number: Int) = "pack-${(number - 1) * state.seating.size + seat}"
    private fun queue(state: DraftState, seat: Int) = state.queues.getValue(state.seating[seat])
    private fun count(state: DraftState, seat: Int) = DraftEngine.consecutivePicks(state, state.seating[seat])

    /** The head pick with the first cards kept and burned, as the count assumes. */
    private fun pick(state: DraftState, seat: Int): DraftState {
        val player = state.seating[seat]
        val id = state.queues.getValue(player).first()
        val cards = state.packs.first { it.id == id }.cards
        val keep = state.settings.keep
        val burn = if (cards.size <= keep + state.settings.burn) emptyList() else cards.drop(keep).take(state.settings.burn)
        val result = DraftEngine.pick(state, DraftPick(player, id, cards.take(keep), burn))
        assertNull(result.reason)
        return assertNotNull(result.state)
    }

    /** The same count through the public pick API, one full state per pick. */
    private fun reference(initial: DraftState, seat: Int): Int {
        var state = initial
        var picks = 0
        while (!state.finished && queue(state, seat).isNotEmpty()) { state = pick(state, seat); picks++ }
        return picks
    }

    @Test fun ownersExampleThreeQueuedPacksPlusTwoOwnNextRoundPacksMakeFive() {
        // Three players, three-card packs, keep one: a pack is picked by its opener and the next seat, then awarded.
        var state = start(players = 3, packs = 4, cards = 3, open = 2)
        state = pick(state, 0)
        state = pick(pick(state, 2), 2)
        assertEquals(listOf(pack(state, 0, 2), pack(state, 2, 1), pack(state, 2, 2)), queue(state, 0))
        assertEquals(0, DraftEngine.privateView(state, state.seating[0]).incoming, "Nothing more of this round comes back")
        assertEquals(0, state.openedRound(state.seating[0]))
        assertEquals(5, count(state, 0))
        assertEquals(reference(state, 0), count(state, 0))
        assertEquals(5, DraftEngine.publicStatus(state).members.first { it.player == state.seating[0] }.packsWaiting)
    }

    @Test fun inTheLastRoundOnlyTheQueueCounts() {
        var state = start(players = 3, packs = 2, cards = 3, open = 2)
        state = pick(state, 0)
        state = pick(pick(state, 2), 2)
        assertEquals(3, queue(state, 0).size)
        assertEquals(3, count(state, 0))
    }

    @Test fun packsThatReturnThroughOtherMembersDoNotCount() {
        val state = start(players = 2, packs = 2, cards = 5)
        assertEquals(1, queue(state, 0).size)
        assertEquals(1, count(state, 0), "The pack comes back only after the other member picks, so round 2 stays closed")
        val after = pick(state, 0)
        assertEquals(2, DraftEngine.privateView(after, after.seating[0]).incoming)
        assertEquals(0, count(after, 0))
    }

    @Test fun heldPacksReleasedByOpeningCount() {
        var state = start(players = 3, packs = 4, cards = 3, open = 2)
        // Seat 1 finishes round 1 and opens round 2 early; its first round-2 pack passes back to seat 0 and is held.
        state = pick(pick(state, 0), 0)
        repeat(4) { state = pick(state, 1) }
        assertEquals(1, state.openedRound(state.seating[1]))
        state = pick(state, 1)
        assertEquals(listOf(pack(state, 1, 3)), state.held[state.seating[0]])
        state = pick(pick(state, 2), 2)
        assertEquals(2, queue(state, 0).size)
        // Two queued, the released held pack and two own round-2 packs.
        assertEquals(5, count(state, 0))
        assertEquals(reference(state, 0), count(state, 0))
    }

    @Test fun nearEndPicksAndAutomaticAwardsAreFollowed() {
        // Five cards, keep one, burn two: the second pick of a pack is near the end and burns the rest.
        var state = start(players = 2, packs = 2, cards = 5, burn = 2)
        state = pick(state, 1)
        assertEquals(2, state.packs.first { it.id == pack(state, 1, 1) }.cards.size)
        assertEquals(listOf(pack(state, 0, 1), pack(state, 1, 1)), queue(state, 0))
        assertEquals(3, count(state, 0))
        assertEquals(reference(state, 0), count(state, 0))
        // Two cards, keep one: a pack's leftover card is awarded on arrival, never a pick, so it never blocks opening.
        var small = start(players = 2, packs = 2, cards = 2)
        assertEquals(2, count(small, 0), "The other member's pack would reach this member only as an award")
        small = pick(small, 1)
        assertEquals(listOf(pack(small, 0, 1)), queue(small, 0))
        assertEquals(2, count(small, 0))
        assertEquals(reference(small, 0), count(small, 0))
    }

    @Test fun finishedDraftsAndNonMembersCountZero() {
        var state = start(players = 2, packs = 1, cards = 2)
        assertEquals(0, DraftEngine.consecutivePicks(state, "stranger"))
        while (!state.finished) state = pick(state, state.seating.indices.first { queue(state, it).isNotEmpty() })
        assertTrue(state.seating.all { DraftEngine.consecutivePicks(state, it) == 0 })
    }

    /**
     * Over whole drafts with random choices and Librarian spends: the count matches the public pick API, is positive
     * exactly when the member holds packs (busy text and badge colour), and drops by exactly one per own pick.
     */
    @Test fun countMatchesRealPicksIsPositiveExactlyWithPacksAndDropsByOnePerOwnPick() {
        val configs = listOf(
            start(players = 2, packs = 3, cards = 6),
            start(players = 3, packs = 4, cards = 5, open = 2, burn = 1),
            start(players = 3, packs = 6, cards = 7, open = 2, keep = 2, burn = 1, discard = 1),
            start(players = 4, packs = 3, cards = 4, librarians = 2),
            start(players = 2, packs = 4, cards = 9, burn = 2, librarians = 1),
        )
        for ((index, initial) in configs.withIndex()) {
            val random = Random(index)
            var state = initial
            var steps = 0
            while (!state.finished) {
                assertTrue(steps++ < 2000, "Draft must make progress")
                val counts = state.seating.indices.map { count(state, it) }
                for (seat in state.seating.indices) {
                    assertEquals(queue(state, seat).isNotEmpty(), counts[seat] > 0, "Config $index step $steps seat $seat")
                    assertEquals(reference(state, seat), counts[seat], "Config $index step $steps seat $seat")
                }
                val seat = state.seating.indices.filter { queue(state, it).isNotEmpty() }.random(random)
                state = randomPick(state, seat, random)
                assertEquals(counts[seat] - 1, count(state, seat), "Config $index step $steps: an own pick lowers the count by one")
            }
        }
    }

    private fun randomPick(state: DraftState, seat: Int, random: Random): DraftState {
        val player = state.seating[seat]
        val id = queue(state, seat).first()
        val owned = state.pools.getValue(player).filter { it.startsWith("librarian:") }
        val librarians = owned.filter { random.nextInt(3) == 0 }
        val cards = (state.packs.first { it.id == id }.cards + librarians).shuffled(random)
        val keep = cards.take(state.settings.keep + librarians.size)
        val rest = cards.drop(keep.size)
        val burn = if (cards.size <= keep.size + state.settings.burn) (if (random.nextBoolean()) rest else emptyList())
            else rest.take(state.settings.burn)
        val result = DraftEngine.pick(state, DraftPick(player, id, keep, burn, librarians))
        assertNull(result.reason)
        return assertNotNull(result.state)
    }

    @Test fun largeDraftCountsStayCheap() {
        for (open in listOf(1, 6, 18)) {
            var state = start(players = 8, packs = 18, cards = 15, open = open)
            // Some progress, so queues, held packs and rounds differ between members.
            val random = Random(open)
            repeat(400) {
                val seats = state.seating.indices.filter { queue(state, it).isNotEmpty() }
                if (seats.isNotEmpty()) state = pick(state, seats.random(random))
            }
            repeat(3) { DraftEngine.publicStatus(state) }
            val started = kotlin.time.TimeSource.Monotonic.markNow()
            val status = DraftEngine.publicStatus(state)
            val elapsed = started.elapsedNow().inWholeMilliseconds
            assertTrue(elapsed < 250, "Counting all eight members took $elapsed ms (open $open)")
            assertTrue(status.members.all { it.packsWaiting >= state.queues.getValue(it.player).size })
        }
    }
}
