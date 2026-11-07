package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** ZON-093 (owner, 2026-11-07): View deck's lists: your deck as played, and the deck without the cards you have seen leave
 * your library. */
class DeckViewTest {
    private val f = TableStatesFixture

    /** [board] with [card] moved into the container [to], changed by [change]. */
    private fun moved(board: BoardState, card: PlayCard, to: String, change: (PlayCard) -> PlayCard = { it }): BoardState =
        board.copy(cards = board.cards.map { if (it.id == card.id) change(it.copy(containerId = to)) else it },
            containers = board.containers.map { when (it.id) {
                to -> it.copy(cards = listOf(card.id) + it.cards)
                card.containerId -> it.copy(cards = it.cards - card.id)
                else -> it
            } })

    private fun total(list: List<Pair<String, Long>>) = list.sumOf { it.second }
    private fun count(list: List<Pair<String, Long>>, digest: String) = list.firstOrNull { it.first == digest }?.second ?: 0L

    // ZON-093: the deck is every card of yours in any zone, without tokens or the other player's cards
    @Test fun theDeckIsYourOwnCardsWithoutTokens() {
        val board = f.initial.board
        val zones = board.containers.associate { it.id to it.zone }
        val own = board.cards.filter { it.owner == f.a && !it.token && zones[it.containerId] != PlayZone.STACK }
        val deck = DeckView.deck(tableBoard(f.initial), f.a)
        assertEquals(own.size.toLong(), total(deck))
        val token = own.first()
        val withToken = tableBoard(f.initial.copy(board = board.copy(cards = board.cards.map { if (it.id == token.id) it.copy(token = true) else it })))
        assertEquals(own.size - 1L, total(DeckView.deck(withToken, f.a)))
    }

    // ZON-093: a card drawn leaves "Still in library"; a card exiled face down from the library (seen by nobody) stays in it,
    // so the list never tells which card it was
    @Test fun stillInLibraryLeavesOutTheCardsYouSawLeave() {
        val board = f.initial.board
        val library = board.containers.single { it.zone == PlayZone.LIBRARY && it.accountId == f.a }
        assertTrue(library.cards.size >= 2, "the fixture's library holds cards")
        val start = DeckView.stillInLibrary(tableBoard(f.initial), f.a)
        val drawn = board.cards.single { it.id == library.cards[0] }
        val afterDraw = moved(board, drawn, f.id("a/HAND"))
        val left = DeckView.stillInLibrary(tableBoard(f.initial.copy(board = afterDraw)), f.a)
        assertEquals(total(start) - 1, total(left))
        assertEquals(count(start, drawn.definition) - 1, count(left, drawn.definition))
        assertEquals(total(DeckView.deck(tableBoard(f.initial), f.a)), total(DeckView.deck(tableBoard(f.initial.copy(board = afterDraw)), f.a)),
            "All keeps the whole deck")
        val hidden = afterDraw.cards.single { it.id == library.cards[1] }
        val exiled = moved(afterDraw, hidden, f.id("a/EXILE")) { it.copy(covered = true, explicitlyCovered = true) }
        assertEquals(left.toSet(), DeckView.stillInLibrary(tableBoard(f.initial.copy(board = exiled)), f.a).toSet())
    }
}
