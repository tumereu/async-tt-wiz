package org.asyncttwiz.protocol

import kotlin.test.*

class GameplayHistoryTest {
    @Test fun versionOneMovementIsRejectedWithoutRulesReplay() {
        val root = gameplayFixture()
        val library = root.board.containers[0]; val hand = root.board.containers[1]
        val draw = gameplayChange(root, PlayAction.MoveCard(library.cards.first(),
            MoveSource(library.id, edge = MoveSourceEdge.TOP), MoveDestination(hand.id, MoveInsertion.UNORDERED)), 100)
        val legacy = draw.mutation!!.let { it.copy(schemaVersion = 1, grouping = null, bookkeeping = null,
            transition = it.transition.copy(delta = null)) }
        val replay = GameplayHistory.replay(root, legacy)
        assertEquals("replayBlocked", replay.reason)
        val cold = GameplayTree(root, mapOf(legacy.id to legacy), legacy.id)
        assertNull(cold.stateAt(legacy.id))
    }

    @Test fun multiplePilesRetainCanonicalInventoryAndExactInverseWithoutUnrelatedOrderLocks() {
        val root = gameplayFixture(); val cards = root.board.cards; val library = root.board.containers[0]; val field = root.board.containers[2]
        fun place(index: Int, x: Int) = PlayAction.MoveCard(cards[index].id, MoveSource(library.id),
            MoveDestination(field.id, MoveInsertion.EMPTY, BoardPosition(x, 0)))
        val first = gameplayChange(root, place(0, 0), 100).state!!
        val second = gameplayChange(first, place(1, 1024), 101)
        assertEquals(first.board, GameplayHistory.reverse(second.state!!, second.mutation!!))
        val third = gameplayChange(second.state, place(2, 2048), 102)
        assertEquals(second.state.board, GameplayHistory.reverse(third.state!!, third.mutation!!))
        val source = third.state.board.cards.first().placement!!
        val extracted = gameplayChange(third.state, PlayAction.MoveCard(cards[0].id, MoveSource(field.id, source),
            MoveDestination(root.board.containers[3].id, MoveInsertion.UNORDERED)), 103)
        assertEquals(third.state.board, GameplayHistory.reverse(extracted.state!!, extracted.mutation!!))
    }

    @Test fun resetRetainsManualConcreteTupleAndLiveFaceReadAcrossMovementIntegration() {
        val root = gameplayFixture().withBattlefieldCards(gameplayId(10)); val card = root.board.cards.first()
        val reset = gameplayChange(root, PlayAction.ResetCorner(card.id), 100)
        val changedDefault = root.copy(definitions = root.definitions.map { it.copy(definition = it.definition.copy(
            faces = it.definition.faces.map { face -> face.copy(corner = "different") })) })
        // I17's accepted tuple semantics: replay/inverse do not recompute a default from metadata.
        assertEquals(reset.state!!.board, GameplayHistory.replay(changedDefault, reset.mutation!!).state!!.board)
        assertEquals(root.board, GameplayHistory.reverse(changedDefault.copy(board = reset.state.board), reset.mutation))
        assertEquals(root.board, GameplayHistory.reverse(reset.state, reset.mutation))
        val changedFace = root.copy(board = root.board.copy(cards = root.board.cards.map {
            if (it.id == card.id) it.copy(faceId = gameplayId(999)) else it }))
        assertEquals("replayBlocked", GameplayHistory.replay(changedFace, reset.mutation).reason)
        assertNull(GameplayHistory.reverse(changedFace.copy(board = changedFace.board.copy(cards = changedFace.board.cards.map {
            if (it.id == card.id) it.copy(corner = reset.state.board.cards.first().corner) else it })), reset.mutation))
    }
    @Test fun forwardReplayInverseAndMutationCodecRetainExactIndividualBoundaries() {
        val root = gameplayFixture(); var state = root
        val library = root.board.containers[0]; val hand = root.board.containers[1]
        val actions = listOf(PlayAction.MoveCard(library.cards[0], MoveSource(library.id, edge = MoveSourceEdge.TOP),
            MoveDestination(root.board.containers[2].id, MoveInsertion.EMPTY, BoardPosition(1, 0))),
            PlayAction.SetTapped(library.cards[0], true), PlayAction.AdjustLife(gameplayId(8), "-4"),
            PlayAction.MoveCard(library.cards[1], MoveSource(library.id), MoveDestination(root.board.containers[2].id, MoveInsertion.EMPTY, BoardPosition(0, 0))))
        val boundaries = mutableListOf(root)
        val mutations = actions.mapIndexed { index, action ->
            val change = gameplayChange(state, action, 100 + index)
            state = change.state!!; boundaries.add(state)
            val mutation = change.mutation!!
            assertEquals(mutation, GameWire.json.decodeFromString<GameplayMutation>(DeckJson.canonical(mutation)))
            mutation
        }
        var replay = root
        mutations.forEachIndexed { index, mutation ->
            replay = GameplayHistory.replay(replay, mutation).state!!
            assertEquals(boundaries[index + 1], replay)
            assertEquals(boundaries[index].board, GameplayHistory.reverse(replay, mutation))
        }
        assertEquals(state, replay)
        assertEquals(4, mutations.map { it.id }.distinct().size)
    }

    @Test fun tapRelevantTupleExcludesPositionButLifeTupleRetainsActualInput() {
        val root = gameplayFixture().withBattlefieldCards(gameplayId(10)); val card = root.board.cards.first()
        val moved = gameplayChange(root, PlayAction.MoveCard(card.id, MoveSource(card.containerId, card.placement),
            MoveDestination(card.containerId, MoveInsertion.EMPTY, BoardPosition(10, 0))), 100)
        val tapped = gameplayChange(moved.state!!, PlayAction.SetTapped(card.id, true), 101)
        val rebasedTap = GameplayHistory.replay(root, tapped.mutation!!)
        assertEquals(tapped.mutation.transition, rebasedTap.mutation!!.transition)
        assertTrue(rebasedTap.state!!.board.cards.first().tapped)
        assertEquals(card.containerId, rebasedTap.state.board.cards.first().containerId)
        val first = gameplayChange(root, PlayAction.AdjustLife(gameplayId(7), "1"), 102)
        val second = gameplayChange(first.state!!, PlayAction.AdjustLife(gameplayId(7), "1"), 103)
        assertEquals("replayBlocked", GameplayHistory.replay(root, second.mutation!!).reason)
        assertEquals("replayBlocked", GameplayHistory.replay(root, tapped.mutation.copy(id = gameplayId(999))).reason)
    }

    @Test fun independentUnorderedArrivalsDoNotPolluteMovementRelevantState() {
        val root = gameplayFixture(); val library = root.board.containers[0]; val bin = root.board.containers[3]
        fun move(card: String) = PlayAction.MoveCard(card, MoveSource(library.id), MoveDestination(bin.id, MoveInsertion.UNORDERED))
        // Graveyard was an unordered bin before rules v27 (see PileOrderTest for the ordered pile).
        // Use nonadjacent source members so the inverse source-neighbor dependency also remains identical.
        val (firstState, firstEvent) = legacyPileMove(root, move(library.cards[0]), 100)
        val (_, secondEvent) = legacyPileMove(firstState, move(library.cards[3]), 101)
        val independent = GameplayHistory.replay(root, secondEvent)
        assertEquals(secondEvent.transition, independent.mutation!!.transition)
        assertEquals(listOf(library.cards[3]), independent.state!!.board.containers[3].cards)
        assertEquals(root.board, GameplayHistory.reverse(firstState, firstEvent))
    }

    @Test fun authoritativePrefixGroupingTapBoundaryAuthorsAndIsolatedStackMoves() {
        var state = gameplayFixture(); val library = state.board.containers[0]; val hand = state.board.containers[1]
        fun apply(action: PlayAction, request: Int, actor: Int = 7): GameplayGroup {
            state = gameplayChange(state, action, request, actor).state!!
            return state.currentGroup!!
        }
        fun move(card: String) = PlayAction.MoveCard(card, MoveSource(library.id),
            MoveDestination(state.board.containers[2].id, MoveInsertion.EMPTY, BoardPosition(library.cards.indexOf(card), 0)))
        val m1 = apply(move(library.cards[0]), 100)
        assertEquals(m1.id, apply(move(library.cards[1]), 101).id)
        val tap = apply(PlayAction.SetTapped(library.cards[0], true), 102)
        assertNotEquals(m1.id, tap.id)
        assertEquals(tap.id, apply(PlayAction.SetTapped(library.cards[1], true), 103).id)
        assertEquals(tap.id, apply(move(library.cards[2]), 104).id)
        assertEquals("move", state.currentGroup!!.category)
        val edit = apply(PlayAction.AdjustLife(gameplayId(7), "1"), 105)
        assertNotEquals(tap.id, edit.id)
        assertEquals(edit.id, apply(PlayAction.SetLife(gameplayId(8), "10"), 106).id)
        assertNotEquals(edit.id, apply(PlayAction.AdjustLife(gameplayId(7), "1"), 107, actor = 8).id)
        val stack = state.board.containers.last()
        fun stack(card: String) = PlayAction.MoveCard(card, MoveSource(library.id), MoveDestination(stack.id, MoveInsertion.TOP), GameplayRules.stackDigest(state))
        val s1 = apply(stack(library.cards[3]), 108)
        assertNotEquals(s1.id, apply(stack(library.cards[4]), 109).id)
        assertEquals("stack", state.currentGroup!!.category)
    }
}
