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

    private fun pick(state: DraftState, seat: Int, librarians: List<String> = emptyList()): DraftState {
        val player = state.seating[seat]
        val id = state.queues.getValue(player).first()
        val cards = cards(state, id)
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
        }
        assertTrue(state.packs.all { it.cards.isEmpty() } && state.queues.values.all { it.isEmpty() })
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
        state = pick(state, 1) // round 1 passes to the previous seat and queues behind A's round-0 pack
        assertEquals(listOf(pack(state, 1, 1), pack(state, 1, 2)), queue(state, 0))
        assertEquals(1, state.log.last().round)
        state = pick(state, 0) // final round-0 pick: A opens behind the earlier round-1 arrival
        assertEquals(listOf(1, 1), listOf(opened(state, 0), opened(state, 1)))
        assertEquals(1, state.round)
        assertEquals(listOf(pack(state, 1, 2), pack(state, 0, 2)), queue(state, 0))
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

    @Test fun threePlayersOpenIndividuallyAndRoundsMixInFifoQueues() {
        // Three cards, keep 1: each pack gets a manual pick from its opener and the next seat, then an automatic award.
        var state = start(players = 3)
        state = pick(state, 0) // A0 -> B
        state = pick(state, 2) // C0 -> A
        assertEquals(listOf(0, 0, 0), state.seating.indices.map { opened(state, it) })
        state = pick(state, 0) // C0 2 -> 1, awarded to B; nothing can come back to A for a pick
        assertEquals(listOf(1, 0, 0), state.seating.indices.map { opened(state, it) })
        assertEquals(listOf(pack(state, 0, 2)), queue(state, 0))
        state = pick(state, 0) // round 1 passes to the previous seat (C), whose round 0 is not finished
        assertEquals(listOf(pack(state, 0, 2)), queue(state, 2))
        assertEquals(0, opened(state, 2))
        state = pick(state, 1) // B0 -> C queues behind A's round-1 pack
        assertEquals(listOf(pack(state, 0, 2), pack(state, 1, 1)), queue(state, 2))
        state = pick(state, 1) // A0 2 -> 1, awarded to C; B is free
        assertEquals(listOf(1, 1, 0), state.seating.indices.map { opened(state, it) })
        assertEquals(listOf(pack(state, 1, 2)), queue(state, 1))
        assertEquals(0, state.round)
        state = pick(state, 2) // A1 2 -> 1, awarded to B
        state = pick(state, 2) // B0 2 -> 1, awarded to A; C opens last
        assertEquals(listOf(1, 1, 1), state.seating.indices.map { opened(state, it) })
        assertEquals(1, state.round)
        assertEquals(listOf(pack(state, 2, 2)), queue(state, 2))
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

    @Test fun nearEndLibrarianRevivesAPackThatQueuesBehindTheNextRound() {
        // 5 cards, keep 1, burn 1: 5 -> 3 -> 1 (automatic).
        var state = start(cards = 5, burn = 1, librarians = 2)
        state = pick(pick(pick(state, 0), 1), 1)
        assertEquals(1, opened(state, 1)) // A's pick of B's 3-card pack would leave one card for an automatic award
        val spent = state.pools.getValue(state.seating[0]).filter { it.startsWith("librarian:") }
        assertEquals(2, spent.size)
        state = pick(state, 0, spent) // A keeps all three cards; two Librarians make the pack a manual pick for B
        assertEquals(spent, cards(state, pack(state, 1, 1)))
        assertEquals(listOf(pack(state, 1, 2), pack(state, 1, 1)), queue(state, 1))
        assertEquals(1, opened(state, 0)) // B's keep-only pick empties the revived pack
        val final = finish(state)
        val revived = final.log.single { it.packId == pack(state, 1, 1) && it.player == state.seating[1] && it.round == 0 && it.kept.first() in spent }
        assertEquals(spent.toSet(), (revived.kept + revived.burned).toSet())
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
