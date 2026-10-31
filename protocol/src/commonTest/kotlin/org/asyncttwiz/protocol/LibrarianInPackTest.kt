package org.asyncttwiz.protocol

import kotlin.test.*

/** A spent Cogwork Librarian joins the pack for that pick and is an ordinary card there: keep it back, burn it or pass it. */
class LibrarianInPackTest {
    private val seed = RandomV1.encodeSeed(ByteArray(32) { (it * 3).toByte() })
    private val card = CardDefinition("Cogwork Librarian", listOf(DefinitionFace("face", "Cogwork Librarian")))

    private fun start(mode: DraftMode = DraftMode.GLIMPSE, size: Int = 8, burn: Int = 2, librarians: Int = 2): DraftState =
        DraftEngine.startWithPacks(listOf("a", "b"), List(2) { pack -> List(size) { DraftCopy("$pack-$it", "d$pack-$it") } },
            DraftSettings(mode = mode, packsPerPlayer = 1, cardsPerPack = size, burn = burn, colorBalance = false,
                librarians = librarians, librarianDefinition = SubmittedDefinition(DeckJson.digest(card), card)), seed)

    private fun head(state: DraftState, player: String) = DraftEngine.privateView(state, player).head!!
    private fun owned(state: DraftState, player: String) = state.pools.getValue(player).filter { it.startsWith("librarian:") }

    // DRF-053
    @Test fun keepingTheSpentLibrarianBackIsANoOpThatStillGrantsTheExtraSlot() {
        for (mode in listOf(DraftMode.BOOSTER, DraftMode.GLIMPSE)) {
            val burn = if (mode == DraftMode.GLIMPSE) 2 else 0
            val state = start(mode, burn = burn)
            val player = state.seating.first()
            val id = head(state, player).id
            val pack = head(state, player).cards.map { it.id }
            val spent = owned(state, player).take(1)
            // Two keep slots: the Librarian itself and one pack card.
            val next = DraftEngine.pick(state, DraftPick(player, id, listOf(spent.single(), pack[0]), pack.drop(1).take(burn), spent)).state!!
            assertEquals(owned(state, player).toSet(), owned(next, player).toSet())
            assertTrue(pack[0] in next.pools.getValue(player))
            val passed = next.packs.first { it.id == id }.cards
            assertEquals(pack.drop(1 + burn), passed)
            assertTrue(spent.single() !in passed)
        }
    }

    // DRF-053
    @Test fun burningTheSpentLibrarianCountsAsOneOfTheBurns() {
        val state = start()
        val player = state.seating.first()
        val pack = head(state, player)
        val ids = pack.cards.map { it.id }
        val spent = owned(state, player).take(1)
        // Keep 2 pack cards, burn the Librarian and one card: exactly two burns.
        val next = DraftEngine.pick(state, DraftPick(player, pack.id, ids.take(2), listOf(spent.single(), ids[2]), spent)).state!!
        assertEquals(listOf(spent.single(), ids[2]), next.log.last().burned)
        assertEquals(ids.drop(3), next.packs.first { it.id == pack.id }.cards)
        assertTrue(next.pools.values.flatten().none { it == spent.single() })
        // Burning the Librarian on top of two cards is one burn too many.
        assertEquals(DraftPickRejection.WRONG_COUNTS, DraftEngine.pick(state,
            DraftPick(player, pack.id, ids.take(2), listOf(spent.single(), ids[2], ids[3]), spent)).reason)
        // An unspent Librarian is not in the pack.
        val other = owned(state, player).last()
        assertEquals(DraftPickRejection.UNKNOWN_CARD, DraftEngine.pick(state,
            DraftPick(player, pack.id, ids.take(2), listOf(other, ids[2]), spent)).reason)
    }

    // DRF-055
    @Test fun aLibrarianLeftInThePackPassesOnAndTheReceiverCanPickIt() {
        val state = start(DraftMode.BOOSTER, burn = 0)
        val (a, b) = state.seating
        val pack = head(state, a)
        val spent = owned(state, a)
        val next = DraftEngine.pick(state, DraftPick(a, pack.id, pack.cards.take(3).map { it.id }, librarians = spent)).state!!
        assertEquals(pack.cards.drop(3).map { it.id } + spent, next.packs.first { it.id == pack.id }.cards)
        assertTrue(owned(next, a).isEmpty())
        assertEquals(listOf(pack.id), next.queues.getValue(b).drop(1))
    }

    // DRF-053
    @Test fun aFinalPickOffersTheSameChoicesAndLeftoversBurnAsBefore() {
        // 3 cards left, keep 1, burn 2: near the end with or without a spend.
        val state = start(size = 3)
        val player = state.seating.first()
        val pack = head(state, player)
        val ids = pack.cards.map { it.id }
        val spent = owned(state, player).take(1)
        // Keep the Librarian back and one card: the other two burn automatically.
        val back = DraftEngine.pick(state, DraftPick(player, pack.id, listOf(spent.single(), ids[0]), librarians = spent)).state!!
        assertEquals(ids.drop(1), back.log.last().burned)
        assertTrue(spent.single() in back.pools.getValue(player))
        // Keep two cards: the Librarian burns with the leftover card, explicitly or automatically.
        for (burn in listOf(emptyList(), listOf(ids[2], spent.single()))) {
            val burned = DraftEngine.pick(state, DraftPick(player, pack.id, ids.take(2), burn, spent)).state!!
            assertEquals(setOf(ids[2], spent.single()), burned.log.last().burned.toSet())
            assertTrue(burned.packs.first { it.id == pack.id }.cards.isEmpty())
            assertTrue(burned.pools.values.flatten().none { it == spent.single() })
        }
        // A partial burn near the end is refused, as without a spend.
        assertEquals(DraftPickRejection.WRONG_COUNTS, DraftEngine.pick(state,
            DraftPick(player, pack.id, ids.take(2), listOf(spent.single()), spent)).reason)
    }

    @Test fun aSpendNeverChangesThePassedPackSize() {
        for (size in 2..8) for (burn in 0..minOf(2, size - 1)) for (count in 0..2) {
            val state = start(size = size, burn = burn)
            val player = state.seating.first()
            val pack = head(state, player)
            val spent = owned(state, player).take(count)
            val cards = (pack.cards.map { it.id } + spent).reversed() // keep Librarians back first
            val keep = cards.take(1 + count)
            val burns = if (size <= 1 + burn) emptyList() else cards.drop(1 + count).take(burn)
            val next = DraftEngine.pick(state, DraftPick(player, pack.id, keep, burns, spent)).state!!
            val expected = if (size <= 1 + burn) 0 else size - 1 - burn
            val left = next.packs.first { it.id == pack.id }.cards.size +
                next.log.filter { it.automatic && it.packId == pack.id }.sumOf { it.kept.size }
            assertEquals(expected, left, "size=$size burn=$burn librarians=$count")
        }
    }
}
