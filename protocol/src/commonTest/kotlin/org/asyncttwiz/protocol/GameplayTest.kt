package org.asyncttwiz.protocol

import kotlin.test.*

class GameplayTest {
    private fun id(n: Int) = "00000000-0000-4000-8000-${n.toString().padStart(12, '0')}"
    private fun state(): PlayState = PlayState(id(1), id(2), id(3), id(4), PlayRoot(id(5), id(6)), id(5),
        BoardState(listOf(PlayPlayer(id(7)), PlayPlayer(id(8))),
            listOf(PlayCard(id(9), "definition", id(7), id(10), id(11), note = "preserved")),
            listOf(PlayContainer(id(10), PlayZone.LIBRARY, id(7), listOf(id(9)))), TurnState(id(7))),
        emptyList(), emptyList(), InitialStarter(StarterChoice(id(7)), id(7)))
    private fun command(action: PlayAction, n: Int = 12) = GameCommand(id(n), GameScope(id(1), playId = id(2)),
        GameAction.Play(action), id(13), "1")

    @Test fun scalarPredictionCodecAndRecordedReplay() {
        val original = state()
        val command = command(PlayAction.SetTapped(id(9), true))
        assertTrue(GameWire.valid(command))
        assertEquals(command, GameWire.decodeClient(GameWire.json.encodeToString<GameClientMessage>(command)))
        val change = GameplayRules.change(original, id(8), command)
        assertEquals("accepted", change.outcome)
        assertEquals(CommandIds.uuidV5(command.requestId, "board-node"), change.state!!.currentNodeId)
        assertEquals("preserved", change.state.board.cards.single().note)
        assertEquals(original.board.containers, change.state.board.containers)
        assertEquals(change, GameplayHistory.replay(original, change.mutation!!))
        assertEquals("noOp", GameplayRules.change(change.state, id(8), command).outcome)
    }

    @Test fun concurrentLifeEditsReadActualInputWithoutPreviousValueLock() {
        val first = GameplayRules.change(state(), id(7), command(PlayAction.AdjustLife(id(7), "1")))
        val second = GameplayRules.change(first.state!!, id(8), command(PlayAction.AdjustLife(id(7), "1"), 14))
        assertEquals("22", second.state!!.board.players.first().life)
        assertEquals("21", second.mutation!!.transition.fields.single().from)
        assertEquals("replayBlocked", GameplayHistory.replay(state(), second.mutation).reason)
        val set = GameplayRules.change(second.state, id(7), command(PlayAction.SetLife(id(7), "10"), 15))
        assertEquals(GameplayFieldChange(id(7), GameplayField.LIFE, "22", "10"), set.mutation!!.transition.fields.single())
        assertEquals("accessDenied", GameplayRules.change(state(), id(99), command(PlayAction.SetLife(id(7), "0"))).reason)
    }

    @Test fun arbitraryIntegerTextArithmeticAndInapplicableText() {
        for ((left, delta, expected) in listOf(Triple("9223372036854775807", "1", "9223372036854775808"),
            Triple("-999999999999999999999", "-2", "-1000000000000000000001"), Triple("+0002", "-3", "-1"),
            Triple("-1", "1", "0"), Triple("0", "-0", "0"))) {
            val state = state().let { it.copy(board = it.board.copy(players = it.board.players.map { p -> p.copy(life = left) })) }
            assertEquals(expected, GameplayRules.change(state, id(7), command(PlayAction.AdjustLife(id(7), delta))).state!!.board.players.first().life)
        }
        val symbolic = GameplayRules.change(state(), id(7), command(PlayAction.SetLife(id(7), "x/2"))).state!!
        assertEquals("inapplicable", GameplayRules.change(symbolic, id(7), command(PlayAction.AdjustLife(id(7), "1"), 14)).reason)
        for (delta in listOf("1.0", "x", "1/2", " 1", "")) assertFalse(GameWire.valid(command(PlayAction.AdjustLife(id(7), delta))))
        for (a in -20..20) for (b in -20..20) assertEquals((a + b).toString(), GameplayNumbers.add(a.toString(), b.toString()))
    }

    @Test fun capturedDrawNeverSubstitutesAndMembershipMoveIgnoresUnrelatedChanges() {
        val state = gameplayFixture()
        val library = state.board.containers[0]; val hand = state.board.containers[1]
        val first = library.cards[0]; val second = library.cards[1]
        val draw = PlayAction.MoveCard(first, MoveSource(library.id, edge = MoveSourceEdge.TOP), MoveDestination(hand.id, MoveInsertion.UNORDERED))
        val change = gameplayChange(state, draw)
        assertEquals("accepted", change.outcome)
        assertEquals(library.cards.drop(1), change.state!!.board.containers[0].cards)
        assertEquals(listOf(first), change.state.board.containers[1].cards)
        assertEquals("movementConflict", gameplayChange(change.state, draw, 91).reason)
        val staleTop = draw.copy(cardId = second)
        assertEquals("movementConflict", gameplayChange(state, staleTop, 92).reason)
        assertEquals(second, gameplayChange(change.state, staleTop, 93).mutation!!.transition.movement!!.cardId)
        val tapped = gameplayChange(state, PlayAction.SetTapped(first, true), 94).state!!
        val accepted = gameplayChange(tapped, draw, 95)
        assertTrue(accepted.state!!.board.cards.single { it.id == first }.tapped)
        assertEquals(state.board.cards.single { it.id == first }.copy(containerId = hand.id, tapped = true),
            accepted.state.board.cards.single { it.id == first })
        val member = draw.copy(cardId = second, source = MoveSource(library.id))
        assertEquals("accepted", gameplayChange(state, member, 96).outcome)
        assertEquals("missingReference", gameplayChange(state, draw.copy(cardId = gameplayId(999)), 97).reason)
    }

    @Test fun explicitEmptyPlacementPileCapacityAndSourcePlacementAreGuarded() {
        var state = gameplayFixture()
        val library = state.board.containers[0]; val field = state.board.containers[2]
        val cards = library.cards
        val empty = PlayAction.MoveCard(cards[0], MoveSource(library.id), MoveDestination(field.id, MoveInsertion.EMPTY, BoardPosition(1024, -2048)))
        val placed = gameplayChange(state, empty, 101); state = placed.state!!
        val placement = state.board.cards.single { it.id == cards[0] }.placement!!
        assertEquals(CommandIds.uuidV5(gameplayId(101), "pile/0"), placement.pileId)
        assertEquals("movementConflict", gameplayChange(state, empty.copy(cardId = cards[1]), 102).reason)
        val join = empty.copy(cardId = cards[1], destination = MoveDestination(field.id, MoveInsertion.PILE_TOP,
            placement.position, placement.pileId))
        state = gameplayChange(state, join, 103).state!!
        state = gameplayChange(state, join.copy(cardId = cards[2]), 104).state!!
        assertEquals(listOf(cards[2], cards[1], cards[0]), state.board.containers[2].cards)
        assertEquals("movementConflict", gameplayChange(state, join.copy(cardId = cards[3]), 105).reason)
        assertEquals("movementConflict", gameplayChange(state, join.copy(cardId = cards[3], destination = join.destination.copy(position = BoardPosition(0, 0))), 106).reason)
        val extraction = PlayAction.MoveCard(cards[0], MoveSource(field.id, placement), MoveDestination(state.board.containers[1].id, MoveInsertion.UNORDERED))
        assertEquals("movementConflict", gameplayChange(state, extraction.copy(source = MoveSource(field.id)), 107).reason)
        val extracted = gameplayChange(state, extraction, 108)
        assertEquals(listOf(cards[2], cards[1]), extracted.state!!.board.containers[2].cards)
        assertNull(extracted.state.board.cards.single { it.id == cards[0] }.placement)
        assertEquals(3, state.board.containers[2].cards.size)
    }

    @Test fun libraryEdgesAndCapturedGapRejectMissingOrReplacedNeighbors() {
        val state = gameplayFixture(); val library = state.board.containers[0]; val ids = library.cards
        val reorder = PlayAction.MoveCard(ids[3], MoveSource(library.id), MoveDestination(library.id, MoveInsertion.GAP, afterId = ids[0], beforeId = ids[1]))
        val change = gameplayChange(state, reorder)
        assertEquals(listOf(ids[0], ids[3], ids[1], ids[2], ids[4]), change.state!!.board.containers[0].cards)
        assertEquals("noOp", gameplayChange(change.state, reorder, 91).outcome)
        val interleaved = gameplayChange(state, reorder.copy(cardId = ids[4]), 98).state!!
        assertEquals("movementConflict", gameplayChange(interleaved, reorder, 99).reason)
        assertEquals("noOp", gameplayChange(state, reorder.copy(cardId = ids[1], destination = reorder.destination.copy(beforeId = ids[2])), 92).outcome)
        assertEquals("movementConflict", gameplayChange(state, reorder.copy(destination = reorder.destination.copy(afterId = gameplayId(999))), 93).reason)
        val bin = state.board.containers[3]
        assertEquals("invalidInput", gameplayChange(state, reorder.copy(destination = MoveDestination(bin.id, MoveInsertion.TOP)), 94).reason)
        assertEquals("accepted", gameplayChange(state, reorder.copy(destination = MoveDestination(bin.id, MoveInsertion.UNORDERED)), 95).outcome)
    }

    @Test fun stackChecksumIsSemanticAndQueuedAdditionsUsePredictedStack() {
        val state = gameplayFixture(); val library = state.board.containers[0]; val stack = state.board.containers.last()
        val action = PlayAction.MoveCard(library.cards[0], MoveSource(library.id), MoveDestination(stack.id, MoveInsertion.TOP), GameplayRules.stackDigest(state))
        val edited = gameplayChange(state, PlayAction.SetTapped(library.cards[1], true), 91).state!!
        assertEquals(GameplayRules.stackDigest(state), GameplayRules.stackDigest(edited))
        val first = gameplayChange(edited, action, 92)
        assertEquals("accepted", first.outcome)
        assertEquals("stackConflict", gameplayChange(first.state!!, action.copy(cardId = library.cards[1]), 93).reason)
        val second = action.copy(cardId = library.cards[1], expectedStackDigest = GameplayRules.stackDigest(first.state))
        assertEquals("accepted", gameplayChange(first.state, second, 94).outcome)
        assertNotEquals(GameplayRules.stackDigest(state), GameplayRules.stackDigest(state.copy(id = gameplayId(999))))
        val alteredStackCard = first.state.let { it.copy(board = it.board.copy(cards = it.board.cards.map { c -> c.copy(tapped = !c.tapped, note = "changed", covered = !c.covered) })) }
        assertEquals(GameplayRules.stackDigest(first.state), GameplayRules.stackDigest(alteredStackCard))
    }

    @Test fun destinationCleanupIsOrderedIndividualRequestsAndReadsLiveFace() {
        val state = gameplayFixture(); val card = state.board.cards.first(); val bin = state.board.containers[3]
        val action = PlayAction.MoveCard(card.id, MoveSource(card.containerId), MoveDestination(bin.id, MoveInsertion.UNORDERED))
        val sequence = GameplayRules.moveActions(state, action)
        assertEquals(listOf(action, PlayAction.ClearCardCounter(card.id, CounterColor.RED), PlayAction.ResetCorner(card.id)), sequence)
        var current = state
        val changes = sequence.mapIndexed { index, constituent -> gameplayChange(current, constituent, 100 + index).also { current = it.state!! } }
        assertEquals(3, changes.map { it.mutation!!.id }.distinct().size)
        val moved = changes[0].state!!.board.cards.first()
        assertEquals(card.counters, moved.counters); assertEquals(card.corner, moved.corner)
        val cleaned = current.board.cards.first()
        assertTrue(cleaned.counters.isEmpty()); assertEquals("3/5", cleaned.corner)
        assertEquals(card.note, cleaned.note); assertEquals(card.owner, cleaned.owner); assertEquals(card.tapped, cleaned.tapped)
        assertEquals(GameplayField.FACE, changes.last().mutation!!.transition.reads.first().field)
        assertEquals("noOp", gameplayChange(current, PlayAction.ClearCardCounter(card.id, CounterColor.RED), 110).outcome)
    }
}

internal fun gameplayId(n: Int) = "00000000-0000-4000-8000-${n.toString().padStart(12, '0')}"
internal fun gameplayFixture(): PlayState {
    val definition = CardDefinition("Retained", listOf(DefinitionFace(gameplayId(30), "Face", corner = "3/5")))
    val submitted = SubmittedDefinition(DeckJson.digest(definition), definition)
    val cards = (10..14).map { PlayCard(gameplayId(it), submitted.digest, gameplayId(7), gameplayId(20), gameplayId(30),
        note = "unchanged", corner = "custom", counters = mapOf(CounterColor.RED to "2")) }
    val containers = listOf(PlayContainer(gameplayId(20), PlayZone.LIBRARY, gameplayId(7), cards.map { it.id }),
        PlayContainer(gameplayId(21), PlayZone.HAND, gameplayId(7)), PlayContainer(gameplayId(22), PlayZone.BATTLEFIELD, gameplayId(7)),
        PlayContainer(gameplayId(23), PlayZone.GRAVEYARD, gameplayId(7)), PlayContainer(gameplayId(24), PlayZone.EXILE, gameplayId(7)),
        PlayContainer(gameplayId(25), PlayZone.STACK))
    return PlayState(gameplayId(1), gameplayId(2), gameplayId(3), gameplayId(4), PlayRoot(gameplayId(5), gameplayId(6)), gameplayId(5),
        BoardState(listOf(PlayPlayer(gameplayId(7)), PlayPlayer(gameplayId(8))), cards, containers, TurnState(gameplayId(7))),
        listOf(submitted), emptyList(), InitialStarter(StarterChoice(gameplayId(7)), gameplayId(7)))
}
internal fun gameplayChange(state: PlayState, action: PlayAction, request: Int = 90, actor: Int = 7) = GameplayRules.change(state, gameplayId(actor),
    GameCommand(gameplayId(request), GameScope(state.gameId, playId = state.id), GameAction.Play(action), gameplayId(80), "1"))
