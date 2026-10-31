package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

/** Round 34: "Battlefield" from a menu puts each card on its normal row: lands on the land row, the rest on the main row. */
class BattlefieldMenuPlacementTest {
    private val land = CardDefinition("Forest", listOf(DefinitionFace("00000000-0000-4000-8000-000000000501", "Forest", typeLine = "Basic Land — Forest")))
    private val walker = CardDefinition("Jace", listOf(DefinitionFace("00000000-0000-4000-8000-000000000502", "Jace",
        typeLine = "Legendary Planeswalker — Jace")))

    /** 40: planeswalker and 41: land in the actor's Exile, 43: the other player's land there too; 42 holds (0, 1). */
    private fun exiled(f: BasicTableFixture): PlayState {
        val exile = f.container(PlayZone.EXILE)
        val field = f.container(PlayZone.BATTLEFIELD)
        fun kind(n: Int) = when (n) { 40 -> walker; 41, 43 -> land; else -> f.definition }
        return f.root.copy(definitions = f.root.definitions + listOf(land, walker).map { SubmittedDefinition(DeckJson.digest(it), it) },
            board = f.root.board.copy(cards = f.root.board.cards.map { card ->
                val n = (40..43).single { card.id == f.id(it) }
                card.copy(definition = DeckJson.digest(kind(n)), faceId = kind(n).faces.first().faceId, covered = false,
                    owner = if (n == 43) f.other else f.actor, containerId = if (n == 42) field else exile,
                    placement = if (n == 42) PlayPlacement(BoardPosition(0, 1), f.id(842)) else null)
            }, containers = f.root.board.containers.map { container -> container.copy(layers = emptyList(), cards = when (container.id) {
                exile -> listOf(40, 41, 43).map(f::id); field -> listOf(f.id(42)); else -> emptyList() }) }))
    }

    // TBL-007
    @Test fun menuBattlefieldPutsAPlaneswalkerOnTheMainRowAndLandsOnTheLandRowOfTheirOwnersBattlefields() {
        val f = BasicTableFixture(); f.connect(exiled(f))
        try {
            val scope = TableScope(f.id(40), listOf(40, 41, 43).map(f::id))
            basicMoveTools(f.play, emptyMap(), true, scope, BasicTableController.sources(f.play, scope.cardIds), f.basic::handle, {})
                .single { it.name == "Battlefield" }.click()
            assertEquals(f.container(PlayZone.BATTLEFIELD), f.card(40).containerId)
            assertEquals(BoardPosition(1, 1), f.card(40).placement?.position)
            assertEquals(BoardPosition(0, 0), f.card(41).placement?.position)
            assertEquals(f.container(PlayZone.BATTLEFIELD, f.other), f.card(43).containerId)
            assertEquals(BoardPosition(0, 0), f.card(43).placement?.position)
        } finally { f.lobby.close(); f.worker.drain() }
    }

    // TBL-007, TBL-008
    @Test fun libraryDestinationsAndStackResolutionUseTheCardsRow() {
        val f = BasicTableFixture()
        val play = exiled(f)
        val field = f.container(PlayZone.BATTLEFIELD)
        fun battlefield(id: String) = libraryDestinations(play.board, sourceIds = listOf(id), definitions = play.definitions)
            .single { (label, _) -> label == "Battlefield" }.second.position
        assertEquals(BoardPosition(1, 1), battlefield(f.id(40)))
        assertEquals(BoardPosition(0, 0), battlefield(f.id(41)))
        val faceDown = play.board.cards.single { it.id == f.id(41) }.copy(covered = true, explicitlyCovered = true)
        assertEquals(BoardPosition(1, 1), BattlefieldPlacement.cell(play, field, faceDown))
    }
}
