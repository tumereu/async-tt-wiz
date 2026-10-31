package org.asyncttwiz.protocol

import kotlin.test.*

class HandReorderTest {
    private fun handState(): PlayState = gameplayFixture().let { state ->
        val hand = state.board.containers.single { it.zone == PlayZone.HAND }
        state.copy(board = state.board.copy(cards = state.board.cards.map { it.copy(containerId = hand.id, covered = true) },
            containers = state.board.containers.map { it.copy(cards = if (it.id == hand.id) state.board.cards.map { c -> c.id } else emptyList()) }))
    }

    // ZON-002
    @Test fun gapReorderCodecReplayInverseAndNoDisclosure() {
        val state = handState()
        val hand = state.board.containers.single { it.zone == PlayZone.HAND }
        val ids = hand.cards
        for (index in 0..3) {
            val remaining = ids - ids[3]
            val action = PlayAction.MoveCard(ids[3], MoveSource(hand.id), MoveDestination(hand.id, MoveInsertion.GAP,
                afterId = remaining.getOrNull(index - 1), beforeId = remaining.getOrNull(index)))
            val command = GameCommand(gameplayId(100 + index), GameScope(state.gameId, playId = state.id),
                GameAction.Play(action), gameplayId(80), "1")
            assertEquals(command, GameWire.decodeClient(GameWire.json.encodeToString<GameClientMessage>(command)))
            val change = GameplayRules.change(state, gameplayId(7), command)
            if (index == 3) { assertEquals("noOp", change.outcome); continue }
            assertEquals(remaining.toMutableList().also { it.add(index, ids[3]) }, change.state!!.board.containers.single { it.id == hand.id }.cards)
            assertEquals(state.board.cards, change.state.board.cards)
            assertTrue(change.mutation!!.transition.disclosures.isEmpty())
            assertFalse(change.mutation.revealsInformation)
            assertEquals(change, GameplayHistory.replay(state, change.mutation))
            assertEquals(state.board, GameplayHistory.reverse(change.state, change.mutation))
            assertEquals("noOp", gameplayChange(change.state, action, 110).outcome)
            // Rules v30: the other player may reorder this Hand too, blind; older rules refused it.
            val other = gameplayChange(state, action, 111, actor = 8)
            assertEquals(change.state.board.containers, other.state!!.board.containers)
            assertTrue(other.mutation!!.transition.disclosures.isEmpty())
            assertEquals("inapplicable", GameplayRules.move(state, gameplayId(8), command.copy(requestId = gameplayId(112)), action,
                rulesVersion = AnyPlayer.RULES_VERSION - 1).reason)
        }
    }

    // ZON-002
    @Test fun staleGapRejectsAndNewDrawPreservesPersonalOrder() {
        val state = handState()
        val hand = state.board.containers.single { it.zone == PlayZone.HAND }
        val ids = hand.cards
        val action = PlayAction.MoveCard(ids[3], MoveSource(hand.id), MoveDestination(hand.id, MoveInsertion.GAP,
            afterId = ids[0], beforeId = ids[1]))
        val intervening = gameplayChange(state, action.copy(cardId = ids[4])).state!!
        assertEquals("movementConflict", gameplayChange(intervening, action, 91).reason)
        val library = state.board.containers.single { it.zone == PlayZone.LIBRARY }
        val drawn = gameplayChange(intervening, PlayAction.MoveCard(ids[2], MoveSource(hand.id), MoveDestination(library.id, MoveInsertion.TOP)), 92).state!!
        val returned = gameplayChange(drawn, PlayAction.MoveCard(ids[2], MoveSource(library.id), MoveDestination(hand.id, MoveInsertion.UNORDERED)), 93).state!!
        assertEquals(listOf(ids[0], ids[4], ids[1], ids[3], ids[2]), returned.board.containers.single { it.id == hand.id }.cards)
    }
}
