package org.asyncttwiz.protocol

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.*

/** A member opens their next-round packs once no pack of their opened rounds can come back to them for a pick. */
class DraftEarlyOpeningTest {
    private val seed = RandomV1.encodeSeed(ByteArray(32) { (it * 7).toByte() })
    private val librarianCard = CardDefinition("Cogwork Librarian", listOf(DefinitionFace("face", "Cogwork Librarian")))

    private fun start(players: Int = 2, packs: Int = 2, cards: Int = 3, keep: Int = 1, burn: Int = 0, discard: Int = 0,
        librarians: Int = 0): DraftState = DraftEngine.startWithPacks(List(players) { "p$it" },
        List(players * packs) { pack -> List(cards) { DraftCopy("c$pack-$it", "d$pack-$it") } },
        DraftSettings(packsPerPlayer = packs, cardsPerPack = cards, keep = keep, burn = burn, discardRemainingAt = discard,
            colorBalance = false, librarians = librarians,
            librarianDefinition = if (librarians > 0) SubmittedDefinition(DeckJson.digest(librarianCard), librarianCard) else null),
        seed)

    /** Pack opened by [seat] as its [number]th pack (one-based). */
    private fun pack(state: DraftState, seat: Int, number: Int) = "pack-${(number - 1) * state.seating.size + seat}"
    private fun cards(state: DraftState, id: String) = state.packs.first { it.id == id }.cards
    private fun queue(state: DraftState, seat: Int) = state.queues.getValue(state.seating[seat])
    private fun opened(state: DraftState, seat: Int) = state.openedRound(state.seating[seat])
    private fun held(state: DraftState, seat: Int) = state.held[state.seating[seat]].orEmpty()

    /** Visible queues hold only the member's opened rounds; held packs are exactly the later-round arrivals. */
    private fun assertRoundOrder(state: DraftState) {
        for (seat in state.seating.indices) {
            for (id in queue(state, seat)) assertTrue(state.packs.first { it.id == id }.round <= opened(state, seat),
                "A visible pack belongs to a round the member has not opened")
            for (id in held(state, seat)) {
                val pack = state.packs.first { it.id == id }
                assertTrue(pack.round > opened(state, seat) && pack.cards.isNotEmpty())
            }
        }
    }

    private fun pick(state: DraftState, seat: Int, librarians: List<String> = emptyList()): DraftState {
        val player = state.seating[seat]
        val id = state.queues.getValue(player).first()
        // Spent Librarians join the pack: pack cards are kept first, so Librarians are kept back only on small packs.
        val cards = cards(state, id) + librarians
        val keep = state.settings.keep + librarians.size
        val burn = if (cards.size <= keep + state.settings.burn) emptyList() else cards.drop(keep).take(state.settings.burn)
        val result = DraftEngine.pick(state, DraftPick(player, id, cards.take(keep), burn, librarians))
        assertNull(result.reason)
        return assertNotNull(result.state)
    }

    private fun finish(initial: DraftState): DraftState {
        var state = initial
        var steps = 0
        while (!state.finished) {
            assertTrue(steps++ < 1000, "Draft must make progress")
            state = pick(state, state.seating.indices.first { queue(state, it).isNotEmpty() })
            assertRoundOrder(state)
        }
        assertTrue(state.packs.all { it.cards.isEmpty() } && state.queues.values.all { it.isEmpty() } && state.held.isEmpty())
        assertEquals(state, Json.decodeFromString<DraftState>(Json.encodeToString(state)))
        return state
    }

    @Test fun twoPlayersOpenEarlyWhenOnlyTheOpponentsFinalPickRemains() {
        var state = start()
        assertEquals(listOf(0, 0), listOf(opened(state, 0), opened(state, 1)))
        state = pick(state, 0) // A's pack 3 -> 2, to B behind B's own pack
        assertEquals(listOf(pack(state, 1, 1), pack(state, 0, 1)), queue(state, 1))
        assertEquals(0, opened(state, 0)) // B's own pack still comes to A for a pick
        state = pick(state, 1) // B's pack 3 -> 2, to A
        assertEquals(listOf(0, 0), listOf(opened(state, 0), opened(state, 1)))
        state = pick(state, 1) // A's pack 2 -> 1, automatically awarded to A
        // All that remains is A's final pick of B's pack, whose last card is awarded to B automatically.
        assertEquals(1, opened(state, 1))
        assertEquals(0, opened(state, 0))
        assertEquals(0, state.round)
        assertEquals(listOf(pack(state, 1, 2)), queue(state, 1))
        assertEquals(listOf(pack(state, 1, 1)), queue(state, 0))
        assertEquals(3, cards(state, pack(state, 0, 2)).size) // A's next pack stays closed
        state = pick(state, 1) // round 1 passes to the previous seat and waits unseen until A finishes round 0
        assertEquals(listOf(pack(state, 1, 1)), queue(state, 0))
        assertEquals(listOf(pack(state, 1, 2)), held(state, 0))
        assertEquals(1, state.log.last().round)
        state = pick(state, 0) // final round-0 pick: A opens, own fresh pack before the half-picked round-1 arrival
        assertEquals(listOf(1, 1), listOf(opened(state, 0), opened(state, 1)))
        assertEquals(1, state.round)
        assertEquals(listOf(pack(state, 0, 2), pack(state, 1, 2)), queue(state, 0))
        assertTrue(state.log.filter { it.packId == pack(state, 1, 1) }.all { it.round == 0 })
        val final = finish(state)
        assertTrue(final.pools.values.all { it.size == 6 })
        assertEquals(2, DraftEngine.publicStatus(final).round)
    }

    @Test fun burnsKeepOnlyPicksAndDiscardThresholdDecideWhetherAPackReturns() {
        // 7 cards, keep 1, burn 2: 7 -> 4 -> 1 (automatic). B's pack cannot return to B after A's single pick.
        var state = start(cards = 7, burn = 2)
        state = pick(pick(pick(state, 0), 1), 1)
        assertEquals(1, opened(state, 1))
        assertEquals(0, opened(state, 0))
        assertEquals(finish(state).pools.values.map { it.size }, listOf(6, 6))
        // 8 cards: 8 -> 5 -> 2, and a two-card pack is a keep-only pick, not an automatic award, so it blocks B.
        state = start(cards = 8, burn = 2)
        state = pick(pick(pick(state, 0), 1), 1)
        assertEquals(listOf(0, 0), listOf(opened(state, 0), opened(state, 1)))
        state = pick(state, 0) // B's pack 5 -> 2 returns to B for a keep-only pick
        assertEquals(0, opened(state, 0)) // A's own two-card pack is still waiting for A
        state = pick(state, 0) // keep-only pick empties A's pack; nothing can reach A any more
        assertEquals(1, opened(state, 0))
        assertEquals(0, opened(state, 1))
        finish(state)
        // 5 cards, keep 1, discard at 2: 5 -> 4 -> 3 -> discarded. Without the threshold the 2-card pack returns.
        for (discard in listOf(2, 0)) {
            state = start(cards = 5, discard = discard)
            state = pick(pick(pick(pick(pick(state, 0), 1), 1), 0), 0)
            assertEquals(pack(state, 1, 1), queue(state, 1).first())
            assertEquals(3, cards(state, pack(state, 1, 1)).size)
            assertEquals(if (discard == 2) 1 else 0, opened(state, 0))
            finish(state)
        }
    }

    @Test fun threePlayersOpenIndividuallyAndANextRoundPackStaysHiddenUntilTheReceiversRoundIsDone() {
        // Three cards, keep 1: each pack gets a manual pick from its opener and the next seat, then an automatic award.
        var state = start(players = 3)
        val c = state.seating[2]
        state = pick(state, 0) // A0 -> B
        state = pick(state, 2) // C0 -> A
        assertEquals(listOf(0, 0, 0), state.seating.indices.map { opened(state, it) })
        state = pick(state, 0) // C0 2 -> 1, awarded to B; nothing can come back to A for a pick
        assertEquals(listOf(1, 0, 0), state.seating.indices.map { opened(state, it) })
        assertEquals(listOf(pack(state, 0, 2)), queue(state, 0))
        state = pick(state, 0) // round 1 passes to the previous seat (C), whose round 0 is not finished
        assertEquals(0, opened(state, 2))
        assertEquals(listOf(pack(state, 0, 2)), held(state, 2))
        assertTrue(queue(state, 2).isEmpty()) // nothing visible, so no your-pick hint for C
        val view = DraftEngine.privateView(state, c)
        assertNull(view.head); assertEquals(0, view.packsBehind); assertTrue(view.queuedPacks.isEmpty())
        assertEquals(0, DraftEngine.publicStatus(state).members.single { it.player == c }.packsWaiting)
        assertEquals(DraftPickRejection.NOT_YOUR_PACK,
            DraftEngine.pick(state, DraftPick(c, pack(state, 0, 2), cards(state, pack(state, 0, 2)).take(1))).reason)
        state = pick(state, 1) // B0 -> C, the only pack C sees
        assertEquals(listOf(pack(state, 1, 1)), queue(state, 2))
        assertEquals(listOf(pack(state, 0, 2)), held(state, 2))
        state = pick(state, 1) // A0 2 -> 1, awarded to C; B is free
        assertEquals(listOf(1, 1, 0), state.seating.indices.map { opened(state, it) })
        assertEquals(listOf(pack(state, 1, 2)), queue(state, 1))
        assertEquals(0, state.round)
        state = pick(state, 2) // B0 2 -> 1, awarded to A; C opens last: own fresh pack first, then the held, picked A1
        assertEquals(listOf(1, 1, 1), state.seating.indices.map { opened(state, it) })
        assertEquals(1, state.round)
        assertTrue(held(state, 2).isEmpty())
        assertEquals(listOf(pack(state, 2, 2), pack(state, 0, 2)), queue(state, 2))
        assertEquals(1, DraftEngine.privateView(state, c).packsBehind)
        val final = finish(state)
        assertTrue(final.pools.values.all { it.size == 6 })
        // Every pick and award is logged with the round of its own pack.
        assertTrue(final.log.all { log -> log.round == final.packs.first { it.id == log.packId }.round })
    }

    @Test fun membersCanRunSeveralRoundsAheadOnlyWhenNothingCanReachThem() {
        // Two cards, keep 1, burn 1: every pack is emptied by its opener, so nothing ever passes.
        var state = start(packs = 3, cards = 2, burn = 1)
        state = pick(state, 0)
        assertEquals(1, opened(state, 0))
        state = pick(state, 0)
        assertEquals(2, opened(state, 0))
        assertEquals(0, opened(state, 1))
        assertEquals(0, state.round)
        state = pick(state, 0)
        assertFalse(state.finished)
        assertTrue(queue(state, 0).isEmpty())
        state = pick(pick(pick(state, 1), 1), 1)
        assertTrue(state.finished)
    }

    @Test fun oldSnapshotsWithoutPerMemberRoundsResumeAndOpenEarlyFromTheNextDecision() {
        var state = start()
        state = pick(pick(state, 0), 1)
        val legacy = Json.decodeFromString<DraftState>(Json.encodeToString(state.copy(opened = emptyMap())))
        assertEquals(0, legacy.openedRound(legacy.seating[1]))
        state = pick(legacy, 1)
        assertEquals(1, opened(state, 1))
        assertEquals(mapOf(state.seating[0] to 0, state.seating[1] to 1), state.opened)
        finish(state)
    }

    private fun librarians(state: DraftState, seat: Int) = state.pools.getValue(state.seating[seat]).filter { it.startsWith("librarian:") }
    private fun manualLog(state: DraftState, seat: Int) = state.log.last { it.player == state.seating[seat] && !it.automatic }

    @Test fun finalPickLibrarianLeftInThePackGoesWithTheAutomaticAward() {
        // 5 cards, keep 1, burn 1: 5 -> 3 -> 1 (automatic).
        var state = start(cards = 5, burn = 1, librarians = 2)
        state = pick(pick(pick(state, 0), 1), 1)
        assertEquals(1, opened(state, 1)) // A's pick of B's 3-card pack would leave one card for an automatic award
        val spent = librarians(state, 0)
        assertEquals(2, spent.size)
        // The pack shows 3 cards + 2 Librarians: A keeps the three cards, burns one Librarian and leaves the other.
        state = pick(state, 0, spent)
        assertTrue(cards(state, pack(state, 1, 1)).isEmpty())
        assertEquals(spent, manualLog(state, 0).librarians)
        assertEquals(spent.take(1), manualLog(state, 0).burned)
        assertTrue(librarians(state, 0).isEmpty())
        // The leftover Librarian is the next member's automatic award, like any last card.
        assertEquals(spent.drop(1), state.log.single { it.automatic && it.packId == pack(state, 1, 1) }.kept)
        assertTrue(spent[1] in state.pools.getValue(state.seating[1]))
        assertEquals(listOf(pack(state, 1, 2)), queue(state, 1)) // no revived pack queues behind B's next round
        assertEquals(1, opened(state, 0))
        assertEquals(listOf(pack(state, 0, 2)), queue(state, 0))
        finish(state)
    }

    @Test fun librarianLeftInAPackPassesOnAsAnOrdinaryCard() {
        // 4 cards, keep 1: A spends one Librarian on a 3-card pack and keeps 2 cards; 1 card + the Librarian pass to B.
        var state = start(cards = 4, librarians = 1)
        state = pick(pick(pick(state, 0), 1), 1)
        assertEquals(listOf(pack(state, 1, 1), pack(state, 0, 1)), queue(state, 0))
        assertEquals(0, opened(state, 1)) // the 3-card pack was always predicted to come back to B
        val left = cards(state, pack(state, 1, 1)).last()
        val spent = librarians(state, 0)
        state = pick(state, 0, spent)
        assertTrue(manualLog(state, 0).burned.isEmpty())
        assertEquals(listOf(left) + spent, cards(state, pack(state, 1, 1)))
        val b = state.seating[1]
        assertEquals(pack(state, 1, 1), queue(state, 1).first())
        // B keeps the Librarian; A gets the last card automatically.
        state = DraftEngine.pick(state, DraftPick(b, pack(state, 1, 1), spent)).state!!
        assertTrue(spent.single() in state.pools.getValue(b))
        assertEquals(listOf(left), state.log.single { it.automatic && it.packId == pack(state, 1, 1) }.kept)
        finish(state)
    }

    @Test fun librarianLeftAtTheDiscardThresholdIsDiscardedWithTheRest() {
        // 6 cards, keep 1, burn 2, discard at 3: A spends one Librarian, keeps 2, burns 2; 2 cards + the Librarian are discarded.
        var state = start(packs = 1, cards = 6, burn = 2, discard = 3, librarians = 1)
        val spent = librarians(state, 0)
        state = pick(state, 0, spent)
        val log = manualLog(state, 0)
        assertEquals(3, log.discarded.size)
        assertTrue(log.discarded.containsAll(spent))
        assertTrue(log.burned.none { it in spent })
        assertTrue(cards(state, pack(state, 0, 1)).isEmpty())
        assertTrue(state.pools.values.flatten().none { it in spent })
    }

    @Test fun midPackLibrarianSpendStillReturnsTheLibrarianToThePassedPack() {
        // 6 cards, keep 1, burn 1: A spends one Librarian, keeps 2, burns 1; 3 cards plus the Librarian pass to B.
        var state = start(packs = 1, cards = 6, burn = 1, librarians = 1)
        val spent = librarians(state, 0)
        state = pick(state, 0, spent)
        assertEquals(spent, cards(state, pack(state, 0, 1)).takeLast(1))
        assertEquals(4, cards(state, pack(state, 0, 1)).size)
        assertTrue(manualLog(state, 0).burned.none { it in spent })
        assertTrue(librarians(state, 0).isEmpty())
        assertEquals(listOf(pack(state, 1, 1), pack(state, 0, 1)), queue(state, 1))
        finish(state)
    }

    @Test fun librarianSpendsNeverBringBackAPackAfterTheReceiverOpenedALaterRound() {
        for (players in 2..4) for (size in 3..7) for (burn in 0..2) for (discard in 0..2) {
            var state = start(players = players, packs = 3, cards = size, burn = burn, discard = discard, librarians = 2)
            val total = state.copies.size
            var steps = 0
            while (!state.finished) {
                assertTrue(steps++ < 1000, "Draft must make progress")
                val seat = state.seating.indices.first { queue(state, it).isNotEmpty() }
                state = pick(state, seat, librarians(state, seat))
                for (member in state.seating.indices) for (id in queue(state, member))
                    assertTrue(state.packs.first { it.id == id }.round >= opened(state, member),
                        "A pack of an earlier round reached a member who already opened a later one")
                assertRoundOrder(state)
                val removed = state.log.sumOf { it.burned.size + it.discarded.size }
                assertEquals(total, state.pools.values.sumOf { it.size } + removed + state.packs.sumOf { it.cards.size })
            }
        }
    }

    @Test fun heldPacksNeverBlockTheDraft() {
        var sawHeld = false
        for (players in 3..6) for (size in 2..8) for (burn in 0..minOf(2, size - 1)) for (discard in 0..1) {
            var state = start(players = players, packs = 3, cards = size, burn = burn, discard = discard)
            var steps = 0
            while (!state.finished) {
                assertTrue(steps++ < 2000, "Draft must make progress")
                // Always serve the last seat with a visible pack, which lets later seats run ahead.
                state = pick(state, state.seating.indices.last { queue(state, it).isNotEmpty() })
                assertRoundOrder(state)
                sawHeld = sawHeld || state.held.isNotEmpty()
            }
            assertTrue(state.packs.all { it.cards.isEmpty() } && state.held.isEmpty() && state.queues.values.all { it.isEmpty() })
        }
        assertTrue(sawHeld)
    }

    @Test fun nonNearEndLibrarianSpendDoesNotChangeOpeningDecisions() {
        val plain = pick(pick(start(cards = 4, librarians = 1), 0), 1)
        val a = plain.seating[0]
        val withLibrarian = run {
            val initial = start(cards = 4, librarians = 1)
            val after = pick(initial, 0, initial.pools.getValue(a).filter { it.startsWith("librarian:") })
            pick(after, 1)
        }
        assertEquals(cards(plain, pack(plain, 0, 1)).size, cards(withLibrarian, pack(withLibrarian, 0, 1)).size)
        var x = plain; var y = withLibrarian
        while (!x.finished) {
            val seat = x.seating.indices.first { queue(x, it).isNotEmpty() }
            x = pick(x, seat); y = pick(y, seat)
            assertEquals(x.seating.indices.map { opened(x, it) }, y.seating.indices.map { opened(y, it) })
        }
        assertTrue(y.finished)
    }
}
