package org.asyncttwiz.protocol

import kotlin.test.*

class HandActionsTest {
    private fun id(n: Int) = "00000000-0000-4000-8000-${n.toString().padStart(12, '0')}"
    private val a = id(7)
    private val b = id(8)
    private var request = 100
    private val seed = RandomV1.encodeSeed(ByteArray(32))
    private fun root(): PlayState {
        val cards = (20..26).map { PlayCard(id(it), "definition", a, id(11), id(40)) }
        return PlayState(id(1), id(2), id(3), id(4), PlayRoot(id(5), id(6)), id(5),
            BoardState(listOf(PlayPlayer(a), PlayPlayer(b)), cards,
                listOf(PlayContainer(id(10), PlayZone.LIBRARY, a), PlayContainer(id(11), PlayZone.HAND, a, cards.map { it.id }),
                    PlayContainer(id(12), PlayZone.GRAVEYARD, a), PlayContainer(id(13), PlayZone.EXILE, a)), TurnState(a)),
            listOf(SubmittedDefinition("definition", CardDefinition("Wizard", listOf(DefinitionFace(id(40), "Wizard"))))),
            emptyList(), InitialStarter(StarterChoice(a), a))
    }
    private fun command(state: PlayState, action: PlayAction) = GameCommand(id(request++),
        GameScope(state.gameId, playId = state.id), GameAction.Play(action), id(90), "1")
    private fun exact(state: PlayState, action: PlayAction): GameplayChange {
        val changed = GameplayRules.change(state, a, command(state, action))
        assertEquals("accepted", changed.outcome, changed.reason)
        val next = assertNotNull(changed.state)
        val event = assertNotNull(changed.mutation)
        assertEquals(20, event.transition.rulesVersion)
        val wire = GameWire.json.decodeFromString(GameplayMutation.serializer(), GameWire.json.encodeToString(GameplayMutation.serializer(), event))
        assertEquals(next, GameplayHistory.apply(state, wire))
        assertEquals(state, GameplayHistory.unapply(next, wire))
        assertEquals(changed, GameplayHistory.replay(state, wire))
        return changed
    }
    @Test fun handPickUsesIndependentLiteralVectorsAndCanonicalUnbiasedSampling() {
        val ids = (1..7).map(::id)
        // Independently calculated SHA-256 domain+seed+counter words, not shuffle-derived results.
        val vectors = listOf(ByteArray(32) to (3473534220L to 3), ByteArray(32) { it.toByte() } to (340274204L to 5),
            ByteArray(32) { 255.toByte() } to (662371858L to 2))
        for ((bytes, vector) in vectors) {
            val encoded = RandomV1.encodeSeed(bytes)
            assertEquals(vector.first, RandomV1.Words(encoded, "hand-pick-v1").next())
            assertEquals(id(vector.second), RandomV1.handPick(ids, encoded))
            assertEquals(RandomV1.handPick(ids, encoded), RandomV1.handPick(ids.reversed(), encoded))
            assertNotEquals(RandomV1.Words(encoded).next(), RandomV1.Words(encoded, "hand-pick-v1").next())
            assertNotEquals(RandomV1.Words(encoded, "branch-reseed-v1").next(), RandomV1.Words(encoded, "hand-pick-v1").next())
        }
        assertEquals(0, RandomV1.sample(7, listOf(4294967295L, 7L).iterator()::next))
    }
    @Test fun randomSelectionSubsetAndEmptySelectionWholeHandHaveRecordedDeterministicResults() {
        val state = root()
        val subset = listOf(id(21), id(25))
        for (result in HandRandomResult.entries) for (selected in listOf(emptyList(), subset)) {
            val action = HandActions.random(state.board, id(11), selected, seed, result)
            val candidates = selected.ifEmpty { state.board.containers.single { it.id == id(11) }.cards }
            assertEquals(RandomV1.handPick(candidates, seed), action.cardId)
            assertTrue(action.cardId in candidates)
            val changed = exact(state, action)
            assertEquals(action, changed.mutation!!.action)
            val next = changed.state!!
            assertTrue(state.board.cards.none { CardVisibility.isVisibleFaceUp(state.board, it, b) })
            val public = next.board.cards.filter { CardVisibility.isVisibleFaceUp(next.board, it, b) }.map { it.id }
            assertEquals(if (result == HandRandomResult.LIBRARY_BOTTOM) emptyList() else listOf(action.cardId), public)
            assertTrue(changed.mutation.transition.disclosures.all { it.cardId == action.cardId })
            if (result == HandRandomResult.LIBRARY_BOTTOM) {
                val library = next.board.containers.single { it.id == id(10) }
                assertEquals(listOf(action.cardId), library.cards)
                assertEquals(listOf(action.cardId), library.knownBottom)
            }
        }
        val picked = (0..40).map { byte -> HandActions.random(state.board, id(11), subset,
            RandomV1.encodeSeed(ByteArray(32) { byte.toByte() }), HandRandomResult.DISCARD).cardId }.toSet()
        assertEquals(subset.toSet(), picked)
    }
    @Test fun randomRejectsForeignHandsStaleSelectionForgedPicksAndEmptyHand() {
        val state = root()
        val action = HandActions.random(state.board, id(11), listOf(id(20)), seed, HandRandomResult.DISCARD)
        assertEquals("accessDenied", GameplayRules.change(state, b, command(state, action)).reason)
        assertEquals("invalidInput", GameplayRules.change(state, a, command(state, action.copy(cardId = id(21)))).reason)
        assertEquals("movementConflict", GameplayRules.change(state, a, command(state, action.copy(selectedIds = listOf(id(99))))).reason)
        val empty = state.copy(board = state.board.copy(cards = emptyList(), containers = state.board.containers.map { it.copy(cards = emptyList()) }))
        assertEquals("movementConflict", GameplayRules.change(empty, a, command(empty, action.copy(selectedIds = emptyList()))).reason)
    }
    @Test fun randomMovesUseChosenCardsOwnerZonesAndRecordOwnerDependency() {
        val original = root().let { s -> s.copy(board = s.board.copy(
            cards = s.board.cards.map { if (it.id == id(20)) it.copy(owner = b) else it },
            containers = s.board.containers + listOf(PlayContainer(id(30), PlayZone.GRAVEYARD, b),
                PlayContainer(id(31), PlayZone.EXILE, b), PlayContainer(id(32), PlayZone.LIBRARY, b)))) }
        for ((result, destination) in listOf(HandRandomResult.DISCARD to id(30), HandRandomResult.EXILE to id(31),
            HandRandomResult.LIBRARY_BOTTOM to id(32))) {
            val action = HandActions.random(original.board, id(11), listOf(id(20)), seed, result)
            val changed = exact(original, action)
            assertEquals(destination, changed.state!!.board.cards.single { it.id == action.cardId }.containerId)
            assertEquals(destination, changed.mutation!!.transition.movement!!.to.containerId)
            if (result == HandRandomResult.LIBRARY_BOTTOM)
                assertEquals(listOf(id(20)), changed.state.board.containers.single { it.id == destination }.knownBottom)
            val differentOwner = original.copy(board = original.board.copy(cards = original.board.cards.map {
                if (it.id == action.cardId) it.copy(owner = a) else it }))
            assertNull(GameplayHistory.apply(differentOwner, changed.mutation))
        }
        val reveal = exact(original, HandActions.random(original.board, id(11), listOf(id(20)), seed, HandRandomResult.REVEAL))
        assertEquals(id(11), reveal.state!!.board.cards.single { it.id == id(20) }.containerId)
    }
    @Test fun revealDonePreservesHandOrderHidesAllRemainingCardsAndIsOneUndoableEvent() {
        val state = root()
        val revealed = exact(state, PlayAction.RevealHand(id(11), listOf(id(21), id(23)))).state!!
        val display = revealed.board.contentsDisplays.single()
        assertTrue(display.reveal)
        assertEquals(listOf(id(21), id(23)), display.cardIds)
        assertTrue(display.cardIds.all { id -> CardVisibility.isVisibleFaceUp(revealed.board, revealed.board.cards.single { it.id == id }, b) })
        assertEquals("accessDenied", GameplayRules.change(revealed, b, command(revealed, PlayAction.DismissContents(display.id))).reason)
        val done = exact(revealed, PlayAction.DismissContents(display.id)).state!!
        assertEquals(state.board, done.board)
        assertTrue(done.board.contentsDisplays.isEmpty())
        assertTrue(done.board.cards.none { CardVisibility.isVisibleFaceUp(done.board, it, b) })
    }
    @Test fun movingRevealedCardsIncludingBackToHandExpiresTheirVisibilityAndClosesEmptyPanel() {
        val revealed = exact(root(), PlayAction.RevealHand(id(11), listOf(id(20), id(21)))).state!!
        val returned = exact(revealed, PlayAction.MoveCard(id(20), MoveSource(id(11)), MoveDestination(id(11), MoveInsertion.UNORDERED))).state!!
        assertFalse(CardVisibility.isVisibleFaceUp(returned.board, returned.board.cards.single { it.id == id(20) }, b))
        assertEquals(listOf(id(21)), returned.board.contentsDisplays.single().cardIds)
        val moved = exact(returned, PlayAction.MoveCard(id(21), MoveSource(id(11)), MoveDestination(id(12), MoveInsertion.UNORDERED))).state!!
        assertTrue(moved.board.contentsDisplays.isEmpty())
        val done = exact(returned, PlayAction.DismissContents(returned.board.contentsDisplays.single().id)).state!!
        assertEquals(returned.board.containers, done.board.containers)
    }
    @Test fun randomConcernsWitnessCandidateMembershipWithoutLockingUnselectedFaces() {
        val state = root()
        val selected = listOf(id(20), id(21))
        val action = HandActions.random(state.board, id(11), selected, seed, HandRandomResult.DISCARD)
        val event = exact(state, action).mutation!!
        val unrelated = state.copy(board = state.board.copy(cards = state.board.cards.map {
            if (it.id == id(26)) it.copy(note = "Unrelated") else it }))
        assertNotNull(GameplayHistory.apply(unrelated, event))
        val missingCandidate = state.copy(board = state.board.copy(containers = state.board.containers.map {
            if (it.id == id(11)) it.copy(cards = it.cards - selected.first { id -> id != action.cardId }) else it }))
        assertNull(GameplayHistory.apply(missingCandidate, event))
    }
}
