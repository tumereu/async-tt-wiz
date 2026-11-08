package org.asyncttwiz.protocol

import kotlin.test.*

/** The viewer's draft history (DRF-165): each pick's pack as it came, keeps and burns, and what a wheel lost meanwhile. */
class DraftHistoryTest {
    private val seed = RandomV1.encodeSeed(ByteArray(32) { (it * 5 + 2).toByte() })
    private val librarian = CardDefinition("Cogwork Librarian", listOf(DefinitionFace("face", "Cogwork Librarian")))

    private fun start(size: Int = 5, burn: Int = 0, librarians: Int = 0, discard: Int = 0): DraftState =
        DraftEngine.startWithPacks(listOf("a", "b"), List(2) { pack -> List(size) { DraftCopy("$pack-$it", "d$pack-$it") } },
            DraftSettings(packsPerPlayer = 1, cardsPerPack = size, burn = burn, discardRemainingAt = discard, colorBalance = false,
                librarians = librarians, librarianDefinition = if (librarians > 0) SubmittedDefinition(DeckJson.digest(librarian), librarian) else null),
            seed)

    private fun head(state: DraftState, player: String) = DraftEngine.privateView(state, player).head!!
    private fun pick(state: DraftState, player: String, keep: List<String>, burn: List<String> = emptyList(),
        librarians: List<String> = emptyList()): DraftState =
        assertNotNull(DraftEngine.pick(state, DraftPick(player, head(state, player).id, keep, burn, librarians)).state)
    private fun ids(cards: List<DraftCopy>) = cards.map { it.id }.toSet()

    // DRF-165: every pick in order with the pack as it came and the keep; a pack coming round again lists the cards taken
    // since it was passed, and nothing of packs the viewer never saw
    @Test fun picksInOrderWithTheirPacksAndTheCardsTakenSince() {
        var state = start()
        val (x, y) = state.seating
        val first = head(state, x).cards.map { it.id }
        val other = head(state, y).cards.map { it.id }
        state = pick(state, x, listOf(first[0]))
        state = pick(state, y, listOf(other[2]))
        // x gets y's pack, y gets x's: each keeps one
        state = pick(state, x, listOf(other[0]))
        state = pick(state, y, listOf(first[3]))
        // x's own pack comes back with three cards: y took first[3] since x passed it
        val history = DraftEngine.history(state, x)
        assertEquals(listOf(1 to 1, 1 to 2), history.map { it.pack to it.pick })
        assertEquals(first.toSet(), ids(history[0].cards))
        assertEquals(listOf(first[0]), history[0].kept)
        assertTrue(history[0].takenSince.isEmpty())
        assertEquals(other.toSet() - other[2], ids(history[1].cards))
        assertEquals(listOf(other[0]), history[1].kept)
        state = pick(state, x, listOf(first[1]))
        val wheel = DraftEngine.history(state, x).last()
        assertEquals(1 to 3, wheel.pack to wheel.pick)
        assertEquals(setOf(first[1], first[2], first[4]), ids(wheel.cards))
        assertEquals(setOf(first[3]), ids(wheel.takenSince))
        // y's history is y's own: never x's keeps beyond what y's packs show
        val theirs = DraftEngine.history(state, y)
        assertEquals(listOf(listOf(other[2]), listOf(first[3])), theirs.map { it.kept })
        assertEquals(first.toSet() - first[0], ids(theirs[1].cards))
        assertTrue(theirs.all { it.takenSince.isEmpty() })
        assertFailsWith<IllegalArgumentException> { DraftEngine.history(state, "nobody") }
    }

    // DRF-165: burns are listed with the pick; automatic awards are picks too, marked so
    @Test fun burnsAndAutomaticAwards() {
        var state = start(size = 4, burn = 1)
        val (x, y) = state.seating
        val mine = head(state, x).cards.map { it.id }
        val theirs = head(state, y).cards.map { it.id }
        state = pick(state, x, listOf(mine[0]), listOf(mine[1]))
        state = pick(state, y, listOf(theirs[0]), listOf(theirs[1]))
        // Two cards left in each pack: keep 1 + burn 1 covers them, so only the keep is chosen and the rest burns
        state = pick(state, x, listOf(theirs[2]))
        val history = DraftEngine.history(state, x)
        assertEquals(listOf(mine[1]), history[0].burned)
        assertEquals(listOf(theirs[2]), history[1].kept)
        assertEquals(listOf(theirs[3]), history[1].burned)
        assertTrue(history.none { it.automatic })
        // A pack of one card (keep or fewer) is given automatically
        val small = DraftEngine.startWithPacks(listOf("a", "b"), List(2) { pack -> List(3) { DraftCopy("$pack-$it", "d$pack-$it") } },
            DraftSettings(packsPerPlayer = 1, cardsPerPack = 3, colorBalance = false), seed)
        var s = small
        repeat(4) { s = s.seating.first { s.queues.getValue(it).isNotEmpty() }.let { p -> pick(s, p, listOf(head(s, p).cards.first().id)) } }
        val awarded = DraftEngine.history(s, s.seating[0]).last()
        assertTrue(awarded.automatic)
        assertEquals(1, awarded.cards.size)
        assertEquals(awarded.cards.map { it.id }, awarded.kept)
    }

    // DRF-165, DRF-053: a spent Librarian joins the pack it was spent on; the next player sees it as a pack card
    @Test fun spentLibrariansShowInThePackTheyWereSpentOn() {
        var state = start(size = 6, librarians = 1)
        val (x, y) = state.seating
        val pack = head(state, x).cards.map { it.id }
        val spent = state.pools.getValue(x).single { it.startsWith("librarian:") }
        // x spends its Librarian: two keeps, and the Librarian passes on in the pack
        state = pick(state, x, listOf(pack[0], pack[1]), librarians = listOf(spent))
        state = pick(state, y, listOf(head(state, y).cards.first().id))
        val mine = DraftEngine.history(state, x).single()
        assertEquals(pack.toSet() + spent, ids(mine.cards))
        assertEquals(listOf(spent), mine.librarians)
        state = pick(state, y, listOf(pack[2]))
        val theirs = DraftEngine.history(state, y).last()
        assertTrue(spent in ids(theirs.cards))
        assertTrue(theirs.librarians.isEmpty())
    }
}
