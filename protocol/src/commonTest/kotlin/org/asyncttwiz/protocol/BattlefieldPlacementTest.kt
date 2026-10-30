package org.asyncttwiz.protocol

import kotlin.test.*

/** Round 34: a card put onto a Battlefield without a chosen cell takes its normal row's first free cell. */
class BattlefieldPlacementTest {
    private fun id(n: Int) = "00000000-0000-4000-8000-${n.toString().padStart(12, '0')}"
    private val field = id(14)
    private fun definition(digest: String, face: Int, type: String) =
        SubmittedDefinition(digest, CardDefinition(type, listOf(DefinitionFace(id(face), type, typeLine = type))))

    private fun state(cards: List<PlayCard>): PlayState {
        val zones = mapOf(id(10) to PlayZone.LIBRARY, id(12) to PlayZone.GRAVEYARD, id(13) to PlayZone.EXILE, field to PlayZone.BATTLEFIELD)
        return PlayState(id(1), id(2), id(3), id(4), PlayRoot(id(5), id(6)), id(5),
            BoardState(listOf(PlayPlayer(id(7)), PlayPlayer(id(8))), cards,
                zones.map { (container, zone) -> PlayContainer(container, zone, id(7), cards.filter { it.containerId == container }.map { it.id }) } +
                    PlayContainer(id(15), PlayZone.STACK), TurnState(id(7))),
            listOf(definition("pw", 40, "Legendary Planeswalker — Jace"), definition("bear", 41, "Creature — Bear"),
                definition("land", 42, "Basic Land — Forest")),
            emptyList(), InitialStarter(StarterChoice(id(7)), id(7)))
    }
    private fun card(n: Int, digest: String, face: Int, container: Int, at: BoardPosition? = null, faceDown: Boolean = false) =
        PlayCard(id(n), digest, id(7), id(container), id(face), covered = faceDown || container == 10, explicitlyCovered = faceDown,
            placement = at?.let { PlayPlacement(it, id(n + 100)) })

    @Test fun planeswalkersAndCreaturesFromExileGraveyardOrLibraryGoToTheMainRow() {
        // A land already holds (0, 0) and a creature (0, 1): the main row's next free cell is (1, 1).
        val board = listOf(card(30, "land", 42, 14, BoardPosition(0, 0)), card(31, "bear", 41, 14, BoardPosition(0, 1)))
        val walker = card(20, "pw", 40, 13)
        val bear = card(21, "bear", 41, 12)
        val fromLibrary = card(22, "pw", 40, 10)
        val play = state(board + walker + bear + fromLibrary)
        for (card in listOf(walker, bear, fromLibrary))
            assertEquals(BoardPosition(1, 1), BattlefieldPlacement.cell(play, field, card), card.id)
    }

    @Test fun aLandGoesToTheLandRow() {
        val land = card(20, "land", 42, 12)
        val play = state(listOf(card(30, "land", 42, 14, BoardPosition(0, 0)), land))
        assertEquals(BoardPosition(1, 0), BattlefieldPlacement.cell(play, field, land))
        assertEquals(0, BattlefieldPlacement.row(play.definitions, land))
    }

    @Test fun faceDownOrUnknownCardsGoToTheMainRow() {
        val faceDownLand = card(20, "land", 42, 13, faceDown = true)
        val unknown = card(21, "missing", 43, 12)
        val play = state(listOf(faceDownLand, unknown))
        assertEquals(BoardPosition(0, 1), BattlefieldPlacement.cell(play, field, faceDownLand))
        assertEquals(BoardPosition(0, 1), BattlefieldPlacement.cell(play, field, unknown))
    }

    @Test fun aBatchGivesEachCardItsOwnRowAndCell() {
        val cards = listOf(card(20, "land", 42, 12), card(21, "pw", 40, 12), card(22, "land", 42, 12), card(23, "bear", 41, 12))
        val play = state(cards + card(30, "bear", 41, 14, BoardPosition(0, 1)))
        assertEquals(mapOf(id(20) to BoardPosition(0, 0), id(21) to BoardPosition(1, 1),
            id(22) to BoardPosition(1, 0), id(23) to BoardPosition(2, 1)), BattlefieldPlacement.cells(play, field, cards))
    }
}
