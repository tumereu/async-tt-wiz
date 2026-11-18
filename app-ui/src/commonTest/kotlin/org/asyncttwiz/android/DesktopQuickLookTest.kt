package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** WEB-026: the quick look's pictures, worked out without what Inspect lists under them (web frame rate, 2026-11-15). */
class DesktopQuickLookTest {
    private val f = TableStatesFixture

    // WEB-026, TBL-104: the quick look of a changed battlefield card (damage, counters: Inspect lists them under it) shows the
    // pictures Inspect shows of it, and only them
    @Test fun theQuickLookShowsTheCardsPicturesAsInspectDoes() {
        val creature = f.source(f.a, "1/3")
        val board = f.initial.board
        val cards = board.cards.map { if (it.id == creature.id) it.copy(damage = "1", typedCounters = listOf(TypedCounter("+1/+1", 2, 1))) else it }
        val play = tableBoard(f.initial.copy(board = board.copy(cards = cards)))
        val card = cards.single { it.id == creature.id }
        val inspection = ManualInspection(play.gameId, play.playId, card.id,
            ManualDisclosure.permittedFaces(play.board, play.definitions, card, f.a, inspecting = true).map { it.faceId })
        val inspect = gameInspectGroups(play, f.a, inspection, card, emptyMap())
        assertTrue(inspect.first().changes.isNotEmpty(), "Inspect lists the card's changes: ${inspect.first().changes}")
        val tiles = gameQuickLookTiles(play, f.a, inspection, card)
        assertTrue(tiles.isNotEmpty(), "the quick look shows the card")
        assertEquals(inspect.first().tiles, tiles)
    }
}
