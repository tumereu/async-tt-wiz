package org.asyncttwiz.protocol

import kotlin.test.*

class CardVisibilityTest {
    private fun move(state: PlayState, zone: PlayZone, request: Int, faceDown: Boolean? = null,
        position: BoardPosition = BoardPosition(request, 0)): GameplayChange {
        val card = state.board.cards.first()
        val target = state.board.containers.single { it.zone == zone }
        val destination = when (zone) {
            PlayZone.BATTLEFIELD -> MoveDestination(target.id, MoveInsertion.EMPTY, position)
            PlayZone.LIBRARY, PlayZone.STACK -> MoveDestination(target.id, MoveInsertion.TOP)
            else -> MoveDestination(target.id, MoveInsertion.UNORDERED)
        }
        val action = PlayAction.MoveCard(card.id, MoveSource(card.containerId, card.placement), destination,
            if (zone == PlayZone.STACK && card.containerId != target.id) GameplayRules.stackDigest(state) else null,
            faceDown = faceDown)
        val change = gameplayChange(state, action, request)
        assertEquals("accepted", change.outcome)
        assertEquals(change, GameplayHistory.replay(state, change.mutation!!))
        if (change.mutation.transition.combatStack == null)
            assertEquals(state.board, GameplayHistory.reverse(change.state!!, change.mutation))
        else {
            val tree = GameplayTree(state, mapOf(change.mutation.id to change.mutation), change.mutation.id)
            assertEquals(change.state, tree.stateAt(change.mutation.id))
        }
        val encoded = GameWire.json.encodeToString(GameplayMutation.serializer(), change.mutation)
        assertEquals(change.mutation, GameWire.json.decodeFromString(GameplayMutation.serializer(), encoded))
        return change
    }

    @Test fun handToBattlefieldIsFaceUpAndRevealsToOpponent() {
        val root = gameplayFixture()
        val hand = move(root, PlayZone.HAND, 100)
        assertEquals(listOf(gameplayId(7)), hand.mutation!!.transition.disclosures.single().viewers)
        val played = move(hand.state!!, PlayZone.BATTLEFIELD, 101)
        val card = played.state!!.board.cards.first()
        assertFalse(card.covered)
        assertFalse(card.isFaceDown)
        assertEquals(listOf(gameplayId(7), gameplayId(8)), CardVisibility.viewers(played.state.board, card))
        assertTrue(played.mutation!!.revealsInformation)
        assertEquals(GameplayDisclosure(card.id, card.faceId, card.containerId, listOf(gameplayId(8))),
            played.mutation.transition.disclosures.single())
        val tree = GameplayTree(root, listOf(hand.mutation, played.mutation).associateBy { it.id }, played.mutation.id)
        val undo = SelectiveUndo.change(tree, gameplayId(7), GameCommand(gameplayId(199),
            GameScope(root.gameId, playId = root.id), GameAction.Undo, gameplayId(80), "1"))
        assertEquals("revealingTarget", undo.reason)
        assertEquals(played.mutation.id, undo.targetId)
        assertNull(undo.tree)
    }

    @Test fun hiddenZoneEntryResetsExplicitFaceDownBeforePlayingAgain() {
        for (zone in listOf(PlayZone.HAND, PlayZone.LIBRARY)) {
            val played = move(gameplayFixture(), PlayZone.BATTLEFIELD, 100).state!!
            val down = gameplayChange(played, PlayAction.SetCovered(played.board.cards.first().id, true), 101)
            assertTrue(down.state!!.board.cards.first().isFaceDown)
            assertEquals(down, GameplayHistory.replay(played, down.mutation!!))
            assertEquals(played.board, GameplayHistory.reverse(down.state, down.mutation))
            val returned = move(down.state, zone, 102, faceDown = true).state!!
            assertFalse(returned.board.cards.first().explicitlyCovered)
            val replayed = move(returned, PlayZone.BATTLEFIELD, 103).state!!
            assertFalse(replayed.board.cards.first().covered)
            assertFalse(replayed.board.cards.first().isFaceDown)
        }
    }

    @Test fun explicitFaceDownSurvivesBattlefieldMovementAndCanBeTurnedUp() {
        val played = move(gameplayFixture(), PlayZone.BATTLEFIELD, 100).state!!
        val down = gameplayChange(played, PlayAction.SetCovered(played.board.cards.first().id, true), 101).state!!
        val moved = move(down, PlayZone.BATTLEFIELD, 102)
        assertTrue(moved.state!!.board.cards.first().isFaceDown)
        assertTrue(CardVisibility.viewers(moved.state.board, moved.state.board.cards.first()).isEmpty())
        assertFalse(moved.mutation!!.revealsInformation)
        val up = gameplayChange(moved.state, PlayAction.SetCovered(moved.state.board.cards.first().id, false), 103)
        assertFalse(up.state!!.board.cards.first().isFaceDown)
        assertTrue(up.mutation!!.revealsInformation)
        assertEquals(listOf(gameplayId(7), gameplayId(8)), up.mutation.transition.disclosures.single().viewers)
        assertEquals(up, GameplayHistory.replay(moved.state, up.mutation))
        assertEquals(moved.state.board, GameplayHistory.reverse(up.state, up.mutation))
    }

    @Test fun libraryToPublicZonesIsFaceUpUnlessMoveExplicitlyRequestsFaceDown() {
        for (zone in listOf(PlayZone.BATTLEFIELD, PlayZone.GRAVEYARD, PlayZone.EXILE, PlayZone.STACK)) {
            val revealed = move(gameplayFixture(), zone, 100)
            assertFalse(revealed.state!!.board.cards.first().covered)
            assertTrue(revealed.mutation!!.revealsInformation)
            assertEquals(listOf(gameplayId(7), gameplayId(8)), revealed.mutation.transition.disclosures.single().viewers)
            val covered = move(gameplayFixture(), zone, 101, faceDown = true)
            assertTrue(covered.state!!.board.cards.first().isFaceDown)
            assertFalse(covered.mutation!!.revealsInformation)
        }
    }

    @Test fun explicitMoveCoverChangesAtSameBattlefieldCellRetainPileIdentity() {
        val played = move(gameplayFixture(), PlayZone.BATTLEFIELD, 100).state!!
        val placement = played.board.cards.first().placement!!
        val down = move(played, PlayZone.BATTLEFIELD, 101, faceDown = true, position = placement.position).state!!
        assertTrue(down.board.cards.first().isFaceDown)
        assertEquals(placement, down.board.cards.first().placement)
        val up = move(down, PlayZone.BATTLEFIELD, 102, faceDown = false, position = placement.position).state!!
        assertFalse(up.board.cards.first().isFaceDown)
        assertEquals(placement, up.board.cards.first().placement)
    }

    @Test fun leavingBattlefieldForAnotherPublicZoneTurnsExplicitFaceDownCardUp() {
        val covered = move(gameplayFixture(), PlayZone.BATTLEFIELD, 100, faceDown = true).state!!
        val graveyard = move(covered, PlayZone.GRAVEYARD, 101)
        assertFalse(graveyard.state!!.board.cards.first().isFaceDown)
        assertTrue(graveyard.mutation!!.revealsInformation)
    }

    @Test fun legacyDefaultCoverIsNotExplicitAndCanStillBeTurnedFaceDown() {
        val initial = gameplayFixture()
        val legacyCard = GameWire.json.decodeFromString<PlayCard>("""{"id":"${gameplayId(10)}","definition":"legacy","owner":"${gameplayId(7)}","containerId":"${gameplayId(22)}","faceId":"${gameplayId(30)}","covered":true}""")
        assertFalse(legacyCard.isFaceDown)
        assertTrue(CardVisibility.isVisibleFaceUp(legacyCard, PlayZone.BATTLEFIELD, gameplayId(7), gameplayId(8)))
        val legacy = initial.copy(board = initial.board.copy(cards = listOf(legacyCard), containers = initial.board.containers.map {
            it.copy(cards = if (it.zone == PlayZone.BATTLEFIELD) listOf(legacyCard.id) else emptyList())
        }))
        val down = gameplayChange(legacy, PlayAction.SetCovered(legacyCard.id, true))
        assertEquals("accepted", down.outcome)
        assertTrue(down.state!!.board.cards.single().isFaceDown)
        assertEquals(down, GameplayHistory.replay(legacy, down.mutation!!))
        assertEquals(legacy.board, GameplayHistory.reverse(down.state, down.mutation))
    }

    @Test fun persistedMovesFromBeforeZoneVisibilityRulesStillReplayWithoutChangingRecordedHistory() {
        val root = gameplayFixture()
        val current = move(root, PlayZone.BATTLEFIELD, 100)
        val mutation = current.mutation!!
        val legacy = mutation.copy(transition = mutation.transition.copy(fields = emptyList(), disclosures = emptyList(),
            movement = mutation.transition.movement!!.copy(coverRulesVersion = 1)), revealsInformation = false)
        val restored = GameplayHistory.replay(root, legacy)
        assertEquals("accepted", restored.outcome)
        assertTrue(restored.state!!.board.cards.first().covered)
        assertFalse(restored.state.board.cards.first().isFaceDown)
        assertEquals(root.board, GameplayHistory.reverse(restored.state, legacy))
        assertEquals(restored.state, GameplayTree(root, mapOf(legacy.id to legacy), legacy.id).stateAt(legacy.id))
    }

    @Test fun defaultMoveWireIntentDoesNotChangeForPreviouslyAdmittedRequests() {
        val root = gameplayFixture()
        val card = root.board.cards.first()
        val action = PlayAction.MoveCard(card.id, MoveSource(card.containerId),
            MoveDestination(gameplayId(21), MoveInsertion.UNORDERED))
        val command = GameCommand(gameplayId(199), GameScope(root.gameId, playId = root.id),
            GameAction.Play(action), gameplayId(80), "1")
        assertFalse(GameWire.canonical(command).contains("\"faceDown\""))
        val explicit = command.copy(action = GameAction.Play(action.copy(faceDown = true)))
        assertTrue(GameWire.canonical(explicit).contains("\"faceDown\":true"))
        assertNotEquals(GameWire.digest(command), GameWire.digest(explicit))
        assertEquals(explicit, GameWire.decodeClient(GameWire.json.encodeToString<GameClientMessage>(explicit)))
    }
}
