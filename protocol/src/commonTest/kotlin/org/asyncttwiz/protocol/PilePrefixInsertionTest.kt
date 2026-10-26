package org.asyncttwiz.protocol

import kotlin.test.*

class PilePrefixInsertionTest {
    private val library = gameplayId(20)
    private val field = gameplayId(22)
    private val x = gameplayId(10)
    private val a = gameplayId(11)
    private val b = gameplayId(12)
    private fun root(): PlayState = gameplayChange(gameplayFixture(), PlayAction.MoveCard(x, MoveSource(library),
        MoveDestination(field, MoveInsertion.EMPTY, BoardPosition(2, 1))), 100).state!!
    private fun placement(state: PlayState) = state.board.cards.single { it.id == x }.placement!!
    private fun action(state: PlayState, card: String, after: String? = null, pending: List<String> = emptyList(),
        source: MoveSource = MoveSource(library)): PlayAction.MoveCard {
        val p = placement(state)
        return PlayAction.MoveCard(card, source, MoveDestination(field, MoveInsertion.PILE_GAP, p.position, p.pileId,
            afterId = after, beforeId = x, targetCardId = x, pendingIds = pending))
    }
    private fun members(state: PlayState) = state.board.containers.single { it.id == field }.cards

    @Test fun orderedIndividualBoundariesKeepExactLocationsCodecReplayAndInverse() {
        val root = root()
        val first = action(root, a, pending = listOf(b))
        val second = action(root, b, after = a)
        var current = root
        val changes = listOf(first, second).mapIndexed { index, move ->
            val command = GameCommand(gameplayId(101 + index), GameScope(root.gameId, playId = root.id),
                GameAction.Play(move), gameplayId(80), "1", invocationId = gameplayId(81))
            assertTrue(GameWire.valid(command))
            assertEquals(command, GameWire.decodeClient(GameWire.json.encodeToString<GameClientMessage>(command)))
            GameplayRules.change(current, gameplayId(7), command).also { change ->
                assertEquals("accepted", change.outcome)
                assertEquals(current.board, GameplayHistory.reverse(change.state!!, change.mutation!!))
                assertEquals(change, GameplayHistory.replay(current, change.mutation))
                assertEquals(change.mutation, GameWire.json.decodeFromString<GameplayMutation>(DeckJson.canonical(change.mutation)))
                current = change.state
            }
        }
        assertEquals(listOf(a, x), members(changes[0].state!!))
        assertEquals(listOf(a, b, x), members(current))
        assertEquals(CardLocation(library, beforeId = b), changes[0].mutation!!.transition.movement!!.from)
        assertEquals(CardLocation(field, placement(root), beforeId = x), changes[0].mutation!!.transition.movement!!.to)
        assertEquals(CardLocation(field, placement(root), afterId = a, beforeId = x), changes[1].mutation!!.transition.movement!!.to)
        assertEquals(listOf(1, 2), changes.map { it.mutation!!.transition.movement!!.destinationSize })
        assertTrue(current.board.cards.filter { it.id in listOf(a, b, x) }.all { it.placement == placement(root) })
        assertEquals(listOf(gameplayId(101), gameplayId(102)), changes.map { it.mutation!!.requestId })
        assertEquals(1, changes.map { it.mutation!!.invocationId }.distinct().size)
        assertEquals("replayBlocked", GameplayHistory.replay(root, changes[1].mutation!!).reason)
        assertNull(GameplayHistory.reverse(root, changes[1].mutation!!))
    }

    @Test fun samePilePendingMembersAreNotUntouchedNeighborsAndAlreadyPlacedIsANoOp() {
        var state = root()
        val p = placement(state)
        for ((index, card) in listOf(a, b).withIndex()) state = gameplayChange(state,
            PlayAction.MoveCard(card, MoveSource(library), MoveDestination(field, MoveInsertion.PILE_BOTTOM, p.position, p.pileId)), 110 + index).state!!
        assertEquals(listOf(x, a, b), members(state))
        val first = action(state, a, pending = listOf(b), source = MoveSource(field, p))
        val second = action(state, b, after = a, source = MoveSource(field, p))
        val before = state
        val movedA = gameplayChange(state, first, 112); state = movedA.state!!
        assertEquals(listOf(a, x, b), members(state)) // B remains in place until its own operation.
        val movedB = gameplayChange(state, second, 113); state = movedB.state!!
        assertEquals(listOf(a, b, x), members(state))
        assertEquals(before.board, GameplayHistory.reverse(movedA.state!!, movedA.mutation!!))
        assertEquals(movedA.state.board, GameplayHistory.reverse(state, movedB.mutation!!))
        assertEquals("noOp", gameplayChange(state, first, 114).outcome)
        assertEquals("noOp", gameplayChange(state, second, 115).outcome)
        assertEquals("movementConflict", gameplayChange(state, second.copy(source = MoveSource(field, p.copy(position = BoardPosition(9, 9)))), 116).reason)
    }

    @Test fun sourcePrefixRightNeighborAddressedTargetAndCapacityNeverUseReplacementReferences() {
        val root = root(); val first = action(root, a, pending = listOf(b))
        val placed = gameplayChange(root, first, 120).state!!
        val second = action(root, b, after = a)
        val extractedA = gameplayChange(placed, PlayAction.MoveCard(a, MoveSource(field, placement(root)),
            MoveDestination(gameplayId(21), MoveInsertion.UNORDERED)), 121).state!!
        assertEquals("movementConflict", gameplayChange(extractedA, second, 122).reason)
        assertEquals("movementConflict", gameplayChange(root, first.copy(destination = first.destination.copy(beforeId = gameplayId(999))), 123).reason)
        assertEquals("movementConflict", gameplayChange(root, first.copy(destination = first.destination.copy(position = BoardPosition(3, 1))), 124).reason)
        assertEquals("movementConflict", gameplayChange(root, first.copy(destination = first.destination.copy(pileId = gameplayId(999))), 125).reason)
        val departedB = gameplayChange(placed, PlayAction.MoveCard(b, MoveSource(library), MoveDestination(gameplayId(21), MoveInsertion.UNORDERED)), 126).state!!
        assertEquals("movementConflict", gameplayChange(departedB, second, 127).reason)
        // Even when its pile survives, losing the specifically addressed X cannot retarget to A.
        val lostX = gameplayChange(placed, PlayAction.MoveCard(x, MoveSource(field, placement(root)),
            MoveDestination(gameplayId(21), MoveInsertion.UNORDERED)), 128).state!!
        assertEquals("movementConflict", gameplayChange(lostX, second.copy(destination = second.destination.copy(beforeId = null)), 129).reason)
        val full = gameplayChange(placed, action(root, gameplayId(13), after = a), 130).state!!
        assertEquals("movementConflict", gameplayChange(full, second, 131).reason)
        // Pending exclusions are not capacity exclusions or permission to ignore an unrelated arrival.
        assertEquals("movementConflict", gameplayChange(full, second.copy(destination = second.destination.copy(pendingIds = listOf(gameplayId(13)))), 132).reason)
        val arrival = gameplayChange(root, action(root, gameplayId(13)), 133).state!!
        assertEquals("movementConflict", gameplayChange(arrival, first, 134).reason)
    }

    @Test fun unrelatedOrdinaryEditsStayLiveAndStackEqualityRemainsADistinctGuard() {
        val root = root(); val first = action(root, a, pending = listOf(b))
        var edited = gameplayChange(root, PlayAction.SetLife(gameplayId(7), "19"), 140).state!!
        edited = gameplayChange(edited, PlayAction.SetNote(x, "current target note"), 141).state!!
        val accepted = gameplayChange(edited, first, 142)
        assertEquals("accepted", accepted.outcome)
        assertEquals("", accepted.state!!.board.cards.single { it.id == a }.note)
        assertEquals("current target note", accepted.state.board.cards.single { it.id == x }.note)
        assertEquals(edited.board.cards.single { it.id == a }.copy(containerId = field, placement = placement(root), covered = false, note = ""),
            accepted.state.board.cards.single { it.id == a })
        assertNull(accepted.mutation!!.transition.movement!!.stackFrom)
        assertEquals("invalidInput", gameplayChange(root, first.copy(expectedStackDigest = GameplayRules.stackDigest(root)), 143).reason)
        val stack = PlayAction.MoveCard(a, MoveSource(library), MoveDestination(gameplayId(25), MoveInsertion.TOP), GameplayRules.stackDigest(root))
        val onStack = gameplayChange(root, stack, 144).state!!
        assertEquals("stackConflict", gameplayChange(onStack, stack.copy(cardId = b), 145).reason)
        assertEquals("accepted", gameplayChange(onStack, first.copy(cardId = b,
            destination = first.destination.copy(pendingIds = emptyList())), 146).outcome)
    }

    @Test fun prefixOperandsAreTypedBoundedAndNotAcceptedOnOtherDestinations() {
        val root = root(); val first = action(root, a, pending = listOf(b))
        fun valid(destination: MoveDestination) = GameplayRules.valid(first.copy(destination = destination))
        assertFalse(valid(first.destination.copy(pendingIds = listOf(b, b))))
        assertFalse(valid(first.destination.copy(pendingIds = listOf(a))))
        assertFalse(valid(first.destination.copy(pendingIds = listOf(x))))
        assertFalse(valid(first.destination.copy(pendingIds = listOf("not-an-id"))))
        assertFalse(valid(first.destination.copy(insertion = MoveInsertion.PILE_TOP)))
        assertEquals("invalidInput", gameplayChange(root, first.copy(destination = first.destination.copy(targetCardId = null)), 150).reason)
        assertEquals("invalidInput", gameplayChange(root, first.copy(destination = first.destination.copy(containerId = gameplayId(25)),
            expectedStackDigest = GameplayRules.stackDigest(root)), 151).reason)
    }
}
