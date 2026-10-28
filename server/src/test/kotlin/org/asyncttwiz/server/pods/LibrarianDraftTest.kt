package org.asyncttwiz.server.pods

import org.asyncttwiz.protocol.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class LibrarianDraftTest {
    private val card = CardDefinition("Cogwork Librarian", listOf(DefinitionFace("face", "Cogwork Librarian")))
    private val definition = SubmittedDefinition(DeckJson.digest(card), card)
    private val seed = RandomV1.encodeSeed(ByteArray(32))
    private fun start(mode: DraftMode = DraftMode.GLIMPSE, size: Int = 8, burn: Int = 2, librarians: Int = 2,
        discard: Int = 0): DraftState = DraftEngine.startWithPacks(listOf("a", "b"),
        List(2) { pack -> List(size) { DraftCopy("$pack-$it", "ordinary") } },
        DraftSettings(mode = mode, packsPerPlayer = 1, cardsPerPack = size, burn = burn, colorBalance = false,
            librarians = librarians, librarianDefinition = definition, discardRemainingAt = discard), seed)

    @Test fun `spending several removes owned copies and adds them after keeps and burns`() {
        for (mode in listOf(DraftMode.BOOSTER, DraftMode.GLIMPSE)) {
            val state = start(mode)
            val player = state.seating.first()
            val view = DraftEngine.privateView(state, player)
            val spent = view.pool.map { it.id }
            assertEquals(2, spent.size)
            assertTrue(view.head!!.cards.none { it.librarian })
            val pack = view.head!!
            val keep = pack.cards.take(3).map { it.id }
            val burns = pack.cards.drop(3).take(2).map { it.id }
            val result = DraftEngine.pick(state, DraftPick(player, pack.id, keep, burns, spent)).state!!
            val passed = result.packs.first { it.id == pack.id }.cards
            assertEquals(pack.cards.drop(5).map { it.id } + spent, passed)
            assertEquals(keep, result.pools.getValue(player))
            assertEquals(spent, result.log.last().librarians)
            assertTrue(burns.none { it in passed || it in result.pools.values.flatten() })
            val build = PodDeckRules.withDraftPool(PodDeckBuild(spent.map { PodDeckCopy(it, PodDeckCategory.SIDE) }), keep)
            assertEquals(keep, build.copies.map { it.copyId })
        }
    }

    @Test fun `duplicates nonlibrarians unowned librarians and wrong keep counts are rejected`() {
        val state = start()
        val player = state.seating.first()
        val view = DraftEngine.privateView(state, player)
        val pack = view.head!!
        val own = view.pool.first().id
        val other = state.pools.getValue(state.seating.last()).first()
        fun rejected(spent: List<String>, keeps: Int, reason: DraftPickRejection) {
            val result = DraftEngine.pick(state, DraftPick(player, pack.id, pack.cards.take(keeps).map { it.id },
                pack.cards.drop(keeps).take(2).map { it.id }, spent))
            assertEquals(reason, result.reason)
            assertNull(result.state)
        }
        rejected(listOf(own, own), 3, DraftPickRejection.INVALID_LIBRARIANS)
        rejected(listOf(other), 2, DraftPickRejection.INVALID_LIBRARIANS)
        rejected(listOf(pack.cards.first().id), 2, DraftPickRejection.INVALID_LIBRARIANS)
        rejected(listOf("missing"), 2, DraftPickRejection.INVALID_LIBRARIANS)
        rejected(listOf(own), 1, DraftPickRejection.WRONG_COUNTS)
        rejected(emptyList(), 2, DraftPickRejection.WRONG_COUNTS)
        val normal = state.copy(pools = state.pools + (player to (view.pool.map { it.id } + pack.cards.first().id)))
        assertEquals(DraftPickRejection.INVALID_LIBRARIANS, DraftEngine.pick(normal,
            DraftPick(player, pack.id, pack.cards.take(2).map { it.id }, librarians = listOf(pack.cards.first().id))).reason)
    }

    @Test fun `a final pick spend burns the librarians with the rest of the pack`() {
        val state = start(size = 4, burn = 2, librarians = 2)
        val a = state.seating.first()
        val av = DraftEngine.privateView(state, a)
        val pack = av.head!!
        val spent = av.pool.map { it.id }
        // Keep 3 of 4 leaves one card to burn and nothing for anyone else, so the Librarians do not pass on.
        val next = DraftEngine.pick(state, DraftPick(a, pack.id, pack.cards.take(3).map { it.id }, librarians = spent)).state!!
        assertEquals(listOf(pack.cards.last().id) + spent, next.log.last().burned)
        assertTrue(next.packs.first { it.id == pack.id }.cards.isEmpty())
        assertTrue(next.pools.values.flatten().none { it in spent })
    }

    @Test fun `a mid pack spend returns the librarian and the picked up librarian is spendable`() {
        var state = start(size = 8, burn = 2, librarians = 2)
        val a = state.seating.first(); val b = state.seating.last()
        val av = DraftEngine.privateView(state, a)
        val pack = av.head!!
        val spent = av.pool.take(1).map { it.id }
        state = DraftEngine.pick(state, DraftPick(a, pack.id, pack.cards.take(2).map { it.id },
            pack.cards.drop(2).take(2).map { it.id }, spent)).state!!
        assertEquals(pack.cards.drop(4).map { it.id } + spent, state.packs.first { it.id == pack.id }.cards)
        // B first acts on their original pack, then picks A's returned Librarian.
        val bv = DraftEngine.privateView(state, b).head!!
        state = DraftEngine.pick(state, DraftPick(b, bv.id, listOf(bv.cards.first().id), bv.cards.drop(1).take(2).map { it.id })).state!!
        val returned = DraftEngine.privateView(state, b).head!!
        assertEquals(pack.id, returned.id)
        state = DraftEngine.pick(state, DraftPick(b, returned.id, listOf(spent.first()),
            returned.cards.filter { it.id != spent.first() }.take(2).map { it.id })).state!!
        assertTrue(DraftEngine.privateView(state, b).pool.any { it.id == spent.first() && it.librarian })
        // A fresh actionable pack plus the actual newly owned pool exercises ownership, not initial allocation.
        val fresh = start()
        val next = fresh.copy(pools = fresh.pools + (b to state.pools.getValue(b)),
            copies = fresh.copies + state.copies.filter { copy -> fresh.copies.none { it.id == copy.id } })
        val head = DraftEngine.privateView(next, b).head!!
        assertNull(DraftEngine.pick(next, DraftPick(b, head.id, head.cards.take(2).map { it.id },
            head.cards.drop(2).take(2).map { it.id }, listOf(spent.first()))).reason)
    }

    @Test fun `automatic awards do not spend and a spend at the discard threshold burns the librarians`() {
        val automatic = start(size = 1, burn = 0)
        assertTrue(automatic.finished)
        assertTrue(automatic.log.all { it.automatic && it.librarians.isEmpty() })
        assertTrue(automatic.pools.values.all { it.size == 3 })
        val state = start(size = 6, burn = 1, librarians = 2, discard = 3)
        val player = state.seating.first()
        val view = DraftEngine.privateView(state, player)
        val pack = view.head!!
        val spent = view.pool.map { it.id }
        val result = DraftEngine.pick(state, DraftPick(player, pack.id, pack.cards.take(3).map { it.id },
            listOf(pack.cards[3].id), spent)).state!!
        assertTrue(result.packs.first { it.id == pack.id }.cards.isEmpty())
        assertEquals(pack.cards.drop(4).map { it.id }, result.log.last().discarded)
        assertEquals(listOf(pack.cards[3].id) + spent, result.log.last().burned)
        assertTrue(result.pools.values.flatten().none { it in spent })
    }
}
