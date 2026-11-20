package org.asyncttwiz.automatum

import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * ZON-136 (owner playtest 2026-11-19, N17): what the other player may know of a player's cards usable from outside their hand:
 * a public card by its id, a hidden one only by its zone.
 */
class OtherPlayableTest {
    init {
        Cards.invent("Other Flare", "{R}", "Sorcery", "You gain 2 life.\nFlashback {R}")
        Cards.invent("Other Scout", "{G}", "Enchantment", "You may play lands from the top of your library.")
    }
    private val table = EngineTable()

    // ZON-136, I-D27: Alice's flashback card in her graveyard is listed for Bob by itself; the land on top of her library she may
    // play only as "the top card of her library" (nothing about which card it is); the page's board carries the same
    @Test fun publicCardsByThemselvesHiddenOnesByTheirZone() {
        val (flare) = table.put(table.alice, Zone.Graveyard, "Other Flare")
        table.put(table.alice, Zone.Battlefield, "Other Scout")
        val (top) = table.put(table.alice, Zone.Library, "Forest")
        val state = table.state()
        val view = Engine.view(state)
        assertEquals(listOf(OtherPlayable(flare, "graveyard"), OtherPlayable(null, "library")),
            OffZoneCards.playableBy(state, view, table.alice, table.bob))
        assertEquals(emptyList(), OffZoneCards.playableBy(state, view, table.bob, table.alice), "Bob has none")
        val board = ComputedBoard.of(table.board(state, table.bob), table.base().definitions, table.definitions, table.bob, state, view)
        assertEquals(mapOf(table.alice to listOf(OtherPlayable(flare, "graveyard"), OtherPlayable(null, "library"))), board.othersPlayable)
        assertEquals(board.othersPlayable, ComputedBoard.restored(board.wire(), state).othersPlayable, "the page receives them")
        assertEquals(top, state.players.single { it.id.value == table.alice }.library.first().let(OffZoneCards::atw), "the Forest is on top")
    }
}
