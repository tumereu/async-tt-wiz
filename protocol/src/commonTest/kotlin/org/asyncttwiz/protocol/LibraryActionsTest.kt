package org.asyncttwiz.protocol

import kotlin.test.*

class LibraryActionsTest {
    private fun id(n: Int) = "00000000-0000-4000-8000-${n.toString().padStart(12, '0')}"
    private val seed = RandomV1.encodeSeed(ByteArray(32) { it.toByte() })
    private fun state(library: List<Int> = listOf(20, 21, 22, 23), hand: List<Int> = emptyList()): PlayState {
        val definition = CardDefinition("Example", listOf(DefinitionFace(id(40), "Front", corner = "2/3"),
            DefinitionFace(id(41), "Back", corner = "4/5")))
        return PlayState(id(1), id(2), id(3), id(4), PlayRoot(id(5), id(6)), id(5),
            BoardState(listOf(PlayPlayer(id(7)), PlayPlayer(id(8))),
                (library + hand).map { n -> PlayCard(id(n), "definition", id(7), if (n in library) id(10) else id(11),
                    id(41), corner = "modified", counters = mapOf(CounterColor.RED to "3"), note = "keep") },
                listOf(PlayContainer(id(10), PlayZone.LIBRARY, id(7), library.map(::id)),
                    PlayContainer(id(11), PlayZone.HAND, id(7), hand.map(::id)),
                    PlayContainer(id(12), PlayZone.LIBRARY, id(8)), PlayContainer(id(13), PlayZone.GRAVEYARD, id(8)),
                    PlayContainer(id(14), PlayZone.EXILE, id(7))), TurnState(id(7))),
            listOf(SubmittedDefinition("definition", definition)), emptyList(), InitialStarter(StarterChoice(id(7)), id(7)))
    }
    private var request = 100
    private fun change(state: PlayState, action: PlayAction): GameplayChange {
        val command = GameCommand(id(request++), GameScope(id(1), playId = id(2)), GameAction.Play(action), id(9), "1")
        assertTrue(GameWire.valid(command))
        assertEquals(command, GameWire.decodeClient(GameWire.json.encodeToString<GameClientMessage>(command)))
        return GameplayRules.change(state, id(8), command)
    }
    // Lower the captured plan before sending; never hide entry edits inside the move reducer.
    private fun apply(state: PlayState, actions: List<PlayAction>): PlayState = actions.flatMap {
        if (it is PlayAction.MoveCard) GameplayRules.moveActions(state, it) else listOf(it)
    }.fold(state) { current, action ->
        val result = change(current, action)
        assertNull(result.reason, "$action: ${result.reason}")
        result.mutation?.let {
            assertEquals(CURRENT_GAMEPLAY_RULES_VERSION, it.transition.rulesVersion)
            assertEquals(result, GameplayHistory.replay(current, it))
        }
        result.state!!
    }
    private fun members(state: PlayState, container: Int = 10) = state.board.containers.single { it.id == id(container) }.cards

    // ZON-019
    @Test fun nonOwnerAuthorsLookRevealSearchAndRestTargetsTheOriginalOwnersLibrary() {
        for (selection in ContentsSelection.entries) for (reveal in listOf(false, true)) {
            val initial = state()
            val shown = apply(initial, listOf(PlayAction.ShowContents(id(10), selection, 2, reveal)))
            val panel = shown.board.contentsDisplays.single()
            assertEquals(id(8), panel.author)
            assertEquals(id(10), panel.containerId)
            assertTrue(panel.cardIds.all { card -> initial.board.cards.single { it.id == card }.owner == id(7) })
            assertTrue(panel.facesVisibleTo(id(8)))
            val closed = apply(shown, listOf(PlayAction.DismissContents(panel.id, ContentsRest.BOTTOM, seed)))
            assertTrue(closed.board.contentsDisplays.isEmpty())
            assertEquals(members(initial).toSet(), members(closed).toSet())
            assertTrue(members(closed, 12).isEmpty())
            if (!panel.search) assertEquals(panel.cardIds, members(closed).takeLast(panel.cardIds.size))
        }
    }

    // ZON-017, ZON-019
    @Test fun nonOwnerDrawAndMulliganUseTheLibraryOwnersHand() {
        val initial = state(hand = listOf(24, 25))
        val drawn = apply(initial, LibraryActions.draw(initial.board, id(10), id(11), 1))
        assertEquals(id(11), drawn.board.cards.single { it.id == id(20) }.containerId)
        val plan = LibraryActions.mulligan(drawn.board, id(10), id(11), members(drawn, 11), seed, count = 2)
        val shuffled = apply(drawn, plan.prefix())
        val result = apply(shuffled, LibraryActions.draw(shuffled.board, id(10), id(11), plan.drawCount))
        assertEquals(2, members(result, 11).size)
        assertTrue(result.board.cards.all { it.owner == id(7) })
    }

    @Test fun topAndBottomSlicesClampInSourceOrderAndDrawIndividually() {
        val initial = state()
        assertEquals(listOf(id(22), id(23)), LibraryRules.slice(members(initial), ContentsSelection.BOTTOM, 2))
        val draw = LibraryActions.draw(initial.board, id(10), id(11), 99)
        assertEquals(members(initial), draw.map { it.cardId })
        var current = initial
        draw.forEachIndexed { index, action ->
            current = apply(current, listOf(action))
            assertEquals(index + 1, members(current, 11).size)
        }
        assertTrue(members(current).isEmpty())
        assertEquals(initial.board.cards.map { it.id }, current.board.cards.map { it.id })
        assertEquals(initial.board.cards.map { it.copy(containerId = id(11), note = "") }, current.board.cards)
        assertTrue(LibraryActions.draw(current.board, id(10), id(11), 1).isEmpty())
    }

    @Test fun orderedTopBottomAndSameContainerSubsetLoweringRetainsNoOpAnchors() {
        for (insertion in listOf(MoveInsertion.TOP, MoveInsertion.BOTTOM)) {
            val initial = state(hand = listOf(24, 25))
            val actions = LibraryActions.moveCaptured(initial.board, listOf(id(24), id(25)), MoveDestination(id(10), insertion))
            assertEquals(listOf(id(24), id(25)), actions.map { it.cardId })
            val result = apply(initial, actions)
            assertEquals(if (insertion == MoveInsertion.TOP) listOf(24, 25, 20, 21, 22, 23).map(::id)
                else listOf(20, 21, 22, 23, 24, 25).map(::id), members(result))
        }
        val initial = state()
        val actions = LibraryActions.moveCaptured(initial.board, listOf(id(21), id(23)),
            MoveDestination(id(10), MoveInsertion.GAP, afterId = id(20), beforeId = id(22)))
        assertEquals("noOp", change(initial, actions.first()).outcome)
        assertEquals(listOf(20, 21, 23, 22).map(::id), members(apply(initial, actions)))
        val fixed = apply(initial, LibraryActions.moveSlice(initial.board, id(10), ContentsSelection.TOP, 2,
            MoveDestination(id(10), MoveInsertion.TOP)))
        assertEquals(listOf(id(20), id(21)), fixed.board.containers.single { it.id == id(10) }.knownTop)
        assertTrue(LibraryActions.moveSlice(fixed.board, id(10), ContentsSelection.TOP, 2,
            MoveDestination(id(10), MoveInsertion.TOP)).isEmpty())
    }

    @Test fun bottomFrontierIsNotFalseBottomCardGuardAndDoesNotSubstitute() {
        val initial = state()
        val actions = LibraryActions.moveSlice(initial.board, id(10), ContentsSelection.BOTTOM, 2,
            MoveDestination(id(12), MoveInsertion.TOP))
        assertEquals(listOf(id(22), id(23)), actions.map { it.cardId })
        assertEquals(listOf(id(22), id(23)), members(apply(initial, actions), 12))
        val interleaved = apply(initial, listOf(PlayAction.MoveCard(id(23), MoveSource(id(10)), MoveDestination(id(11), MoveInsertion.UNORDERED))))
        assertEquals("movementConflict", change(interleaved, actions.first()).reason)
        assertEquals(listOf(id(20), id(21), id(22)), members(interleaved))
    }

    @Test fun invalidatedPrefixRejectsLaterMemberWithoutRollbackOrSubstitution() {
        val initial = state(hand = listOf(24, 25))
        val actions = LibraryActions.moveCaptured(initial.board, listOf(id(24), id(25)), MoveDestination(id(10), MoveInsertion.TOP))
        var current = apply(initial, listOf(actions.first()))
        current = apply(current, listOf(PlayAction.MoveCard(id(24), MoveSource(id(10)), MoveDestination(id(14), MoveInsertion.UNORDERED))))
        assertEquals("movementConflict", change(current, actions.last()).reason)
        assertEquals(listOf(id(25)), members(current, 11))
        assertEquals(listOf(id(24)), members(current, 14))
        val draw = LibraryActions.draw(initial.board, id(10), id(11), 2)
        val moved = apply(initial, listOf(draw.first()))
        assertEquals("movementConflict", change(moved, draw.first()).reason)
        assertEquals(id(22), members(apply(moved, listOf(draw.last()))).first())
    }

    @Test fun nonBattlefieldEntryClearsNoteButDoesNotReapplyBattlefieldDefaults() {
        val initial = state(hand = listOf(24))
        val action = LibraryActions.moveCaptured(initial.board, listOf(id(24)), MoveDestination(id(13), MoveInsertion.UNORDERED)).single()
        val sequence = GameplayRules.moveActions(initial, action)
        assertEquals(listOf(action), sequence)
        var current = initial
        val changes = sequence.map { constituent ->
            val before = current
            change(before, constituent).also {
                assertEquals("accepted", it.outcome)
                assertEquals(it, GameplayHistory.replay(before, it.mutation!!))
                current = it.state!!
            }
        }
        assertEquals(1, changes.map { it.mutation!!.id }.distinct().size)
        assertEquals(listOf(GameplayFieldChange(id(24), GameplayField.COVERED, "true", "false"),
            GameplayFieldChange(id(24), GameplayField.NOTE, "keep", "")),
            changes.first().mutation!!.transition.fields)
        assertEquals(initial.board.cards.last().copy(containerId = id(13), covered = false, note = ""), changes.first().state!!.board.cards.last())
        val card = current.board.cards.single { it.id == id(24) }
        assertEquals("modified", card.corner); assertEquals(mapOf(CounterColor.RED to "3"), card.counters)
        assertEquals(initial.board.cards.last().copy(containerId = id(13), covered = false, note = ""), card)
        assertEquals(2, changes.sumOf { it.mutation!!.transition.fields.size })
        assertTrue(changes.last().mutation!!.transition.reads.isEmpty())
        val missing = apply(initial.copy(definitions = emptyList()), listOf(action))
        assertEquals("modified", missing.board.cards.last().corner)
        val reordered = apply(initial, LibraryActions.moveSlice(initial.board, id(10), ContentsSelection.BOTTOM, 1,
            MoveDestination(id(10), MoveInsertion.TOP)))
        assertEquals(initial.board.cards, reordered.board.cards)
    }

    @Test fun shuffleUsesCurrentMembershipCanonicalSeedAndExactReplayTuple() {
        val initial = state(hand = listOf(24))
        val action = PlayAction.ShuffleLibrary(id(10), seed)
        val prediction = change(initial, action)
        assertEquals(RandomV1.shuffle(members(initial), seed), members(prediction.state!!))
        assertEquals(prediction, GameplayHistory.replay(initial, prediction.mutation!!))
        val arrival = apply(initial, LibraryActions.moveCaptured(initial.board, listOf(id(24)), MoveDestination(id(10), MoveInsertion.TOP)))
        val accepted = change(arrival, action)
        assertEquals(RandomV1.shuffle(members(arrival), seed), members(accepted.state!!))
        assertEquals(members(arrival).toSet(), members(accepted.state).toSet())
        assertEquals("replayBlocked", GameplayHistory.replay(arrival, prediction.mutation).reason)
        assertEquals(arrival.board.cards, accepted.state.board.cards)
        assertFalse(GameplayRules.valid(action.copy(seed = "bad")))
        assertFalse(GameplayRules.valid(action.copy(algorithm = "shuffle-v2")))
        assertEquals("accepted", change(state(emptyList()), action).outcome)
    }

    @Test fun panelsCaptureMembersRemoveMovedCardsAndResolveRestOnClose() {
        val initial = state()
        val action = PlayAction.ShowContents(id(10), ContentsSelection.TOP, 3)
        val predicted = apply(initial, listOf(action))
        val drawFirst = apply(initial, LibraryActions.draw(initial.board, id(10), id(11), 1))
        val accepted = apply(drawFirst, listOf(action))
        assertEquals(listOf(21, 22, 23).map(::id), accepted.board.contentsDisplays.single().cardIds)
        val display = predicted.board.contentsDisplays.single()
        assertEquals(listOf(20, 21, 22).map(::id), display.cardIds)
        assertTrue(display.facesVisibleTo(id(8))); assertFalse(display.facesVisibleTo(id(7)))
        var current = apply(predicted, LibraryActions.draw(predicted.board, id(10), id(11), 1))
        current = apply(current, listOf(PlayAction.ShuffleLibrary(id(10), seed)))
        assertEquals(display.cardIds.drop(1), current.board.contentsDisplays.single().cardIds)
        assertEquals(id(11), current.board.cards.single { it.id == display.cardIds.first() }.containerId)
        val dismissed = apply(current, listOf(PlayAction.DismissContents(display.id)))
        assertTrue(dismissed.board.contentsDisplays.isEmpty())
        assertEquals(current.board.cards, dismissed.board.cards)
        assertEquals(display.cardIds.drop(1), members(dismissed).take(2))
        assertEquals("noOp", change(dismissed, PlayAction.DismissContents(display.id)).outcome)
        val reveal = apply(initial, listOf(action.copy(selection = ContentsSelection.BOTTOM, count = 2, reveal = true)))
        assertEquals(listOf(22, 23).map(::id), reveal.board.contentsDisplays.single().cardIds)
        assertTrue(reveal.board.contentsDisplays.single().facesVisibleTo(id(7)))
        val all = apply(initial, listOf(action.copy(selection = ContentsSelection.ALL)))
        val reversed = initial.copy(board = initial.board.copy(containers = initial.board.containers.map {
            if (it.id == id(10)) it.copy(cards = it.cards.reversed()) else it }))
        assertEquals(all.board.contentsDisplays.single().cardIds,
            apply(reversed, listOf(action.copy(selection = ContentsSelection.ALL))).board.contentsDisplays.single().cardIds)
    }

    @Test fun capturedMulligansExecuteEveryBoundaryWithoutAckAndClampZeroOneSix() {
        for (size in listOf(0, 1, 6)) for (minus in listOf(false, true)) {
            val initial = state(hand = (24 until 24 + size).toList())
            val displayed = members(initial, 11).reversed()
            val plan = LibraryActions.mulligan(initial.board, id(10), id(11), displayed, seed, count = 7, minusOne = minus)
            assertEquals(size, plan.preReturnSize); assertEquals(displayed, plan.handIds)
            val prefix = plan.prefix()
            assertEquals(displayed, prefix.filterIsInstance<PlayAction.MoveCard>().map { it.cardId })
            val shuffled = apply(initial, prefix)
            val draws = LibraryActions.draw(shuffled.board, plan.libraryId, plan.handId, plan.drawCount)
            assertEquals(members(shuffled).take(plan.drawCount), draws.map { it.cardId })
            val final = apply(shuffled, draws)
            assertEquals(if (minus) (size - 1).coerceAtLeast(0) else minOf(7, 4 + size), members(final, 11).size)
        }
    }

    @Test fun tokenLoweringDoesNotUseDisappearedMemberAsDestinationAnchor() {
        val initial = state(hand = listOf(24, 25, 26))
        val board = initial.board.copy(cards = initial.board.cards.map { if (it.id == id(25)) it.copy(token = true) else it })
        val moves = LibraryActions.moveCaptured(board, listOf(24, 25, 26).map(::id), MoveDestination(id(10), MoveInsertion.TOP))
        assertEquals(id(24), moves[1].destination.afterId)
        assertEquals(id(24), moves[2].destination.afterId)
        // Actual disappearance/link cleanup remains the I19 processor, not claimed by this lowerer fixture.
    }

    @Test fun capturedHandSizeAndReturnSourcesSurviveInterleavingWithOrdinaryPartialOutcomes() {
        val initial = state(hand = listOf(24, 25, 26, 27, 28, 29))
        val plan = LibraryActions.mulligan(initial.board, id(10), id(11), members(initial, 11), seed, minusOne = true)
        val departed = apply(initial, listOf(PlayAction.MoveCard(id(24), MoveSource(id(11)), MoveDestination(id(14), MoveInsertion.UNORDERED))))
        assertEquals(5, plan.drawCount)
        assertEquals(id(11), plan.returns.first().source.containerId)
        assertEquals("movementConflict", change(departed, plan.returns.first()).reason)
        // The invalid first prefix anchor also rejects dependent returns, but Shuffle still runs.
        assertEquals("movementConflict", change(departed, plan.returns[1]).reason)
        val shuffled = apply(departed, listOf(plan.prefix().last()))
        assertEquals(members(departed).toSet(), members(shuffled).toSet())
        assertEquals(4, LibraryActions.draw(shuffled.board, plan.libraryId, plan.handId, plan.drawCount).size)
    }
}
