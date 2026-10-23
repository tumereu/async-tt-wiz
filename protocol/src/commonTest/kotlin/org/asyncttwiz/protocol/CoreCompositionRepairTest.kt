package org.asyncttwiz.protocol

import kotlin.test.*

/** The two assigned full-engine seams; no replacement movement or prediction engine. */
class CoreCompositionRepairTest {
    private val actor = gameplayId(7)
    private fun life(state: PlayState) = state.board.players.first().life
    private fun withLife(value: String): PlayState = gameplayFixture().let { state ->
        state.copy(board = state.board.copy(players = state.board.players.map {
            if (it.accountId == actor) it.copy(life = value) else it
        }))
    }

    @Test fun lifeSetPinsEveryUnicodeWhitespaceEdgeButPreservesInteriorAndOtherCodePoints() {
        val whitespace = (9..13).toList() + listOf(32, 133, 160, 5760) + (8192..8202).toList() +
            listOf(8232, 8233, 8239, 8287, 12288)
        val initial = gameplayFixture()
        for (code in whitespace) {
            val text = "${code.toChar()}+0009${code.toChar()}"
            val changed = gameplayChange(initial, PlayAction.SetLife(actor, text))
            assertEquals("+0009", life(changed.state!!), "U+${code.toString(16)}")
            assertEquals(changed, GameplayHistory.replay(initial, changed.mutation!!))
        }
        for (code in listOf(0x1c, 0x180e, 0x200b, 0xfeff)) {
            val text = "${code.toChar()}9${code.toChar()}"
            assertEquals(text, life(gameplayChange(initial, PlayAction.SetLife(actor, text)).state!!))
        }
        val interior = "a\u0085\u00a0b"
        val symbolic = gameplayChange(initial, PlayAction.SetLife(actor, "\u0085$interior\u00a0")).state!!
        assertEquals(interior, life(symbolic))
        assertEquals("inapplicable", gameplayChange(symbolic, PlayAction.AdjustLife(actor, "1")).reason)
        assertEquals("", life(gameplayChange(initial, PlayAction.SetLife(actor, "\u0085\u00a0")).state!!))
    }

    @Test fun signedZeroIsNoEditAndNeverCanonicalizesAnUnchangedLifeValue() {
        for (value in listOf("+0009", "-0009", "-0", "+000", "0")) {
            val initial = withLife(value)
            for (delta in listOf("0", "+000", "-000")) {
                val changed = gameplayChange(initial, PlayAction.AdjustLife(actor, delta))
                assertEquals("noOp", changed.outcome)
                assertEquals(initial, changed.state)
                assertNull(changed.mutation)
            }
        }
        assertEquals("inapplicable", gameplayChange(withLife("text"), PlayAction.AdjustLife(actor, "-0")).reason)
    }

    @Test fun lifeUsesExactSignedArithmeticAndAcceptedConcurrentTuplesWithoutMachineBounds() {
        val cases = listOf(
            Triple("+000999999999999999999999999", "1", "1000000000000000000000000"),
            Triple("-999999999999999999999999999", "-1", "-1000000000000000000000000000"),
            Triple("-1000000000000000000000000000", "999999999999999999999999999", "-1"),
            Triple("+0009", "-0009", "0"), Triple("-0009", "+0010", "1"))
        for ((from, delta, expected) in cases) {
            val initial = withLife(from)
            val changed = gameplayChange(initial, PlayAction.AdjustLife(actor, delta))
            assertEquals(expected, life(changed.state!!))
            assertEquals(TableActions.addNumber(from, delta), expected)
            assertEquals(listOf(GameplayFieldChange(actor, GameplayField.LIFE, from, expected)), changed.mutation!!.transition.fields)
            assertEquals(initial.board.copy(players = changed.state.board.players), changed.state.board)
            assertEquals(changed, GameplayHistory.replay(initial, changed.mutation))
            val concurrent = gameplayChange(initial, PlayAction.SetLife(actor, "37"), 91).state!!
            assertEquals("replayBlocked", GameplayHistory.replay(concurrent, changed.mutation).reason)
            val accepted = gameplayChange(concurrent, PlayAction.AdjustLife(actor, delta), 92)
            assertEquals("37", accepted.mutation!!.transition.fields.single().from)
            assertEquals(accepted, GameplayHistory.replay(concurrent, accepted.mutation))
        }
        val full = withLife("9".repeat(DeckValidation.FIELD_BYTES))
        val overflow = gameplayChange(full, PlayAction.AdjustLife(actor, "1"))
        assertEquals("invalidInput", overflow.reason)
        assertNull(overflow.state); assertNull(overflow.mutation)
        for (invalid in listOf("١", "1.0", "1e2", " 1", "1/2", ""))
            assertEquals("invalidInput", gameplayChange(withLife("20"), PlayAction.AdjustLife(actor, invalid)).reason)
    }

    @Test fun capturedLibraryEntriesSendIndividualCleanupAndReadFaceWhenResetActuallyRuns() {
        val initial = gameplayFixture()
        val card = initial.board.cards.first()
        val hand = gameplayId(21)
        val inHand = gameplayChange(initial, PlayAction.MoveCard(card.id, MoveSource(card.containerId),
            MoveDestination(hand, MoveInsertion.UNORDERED))).state!!
        val move = LibraryActions.moveCaptured(inHand.board, listOf(card.id),
            MoveDestination(gameplayId(20), MoveInsertion.TOP)).single()
        // Capture all requests now, without waiting for any authoritative acknowledgement.
        val requests = GameplayRules.moveActions(inHand, move)
        assertEquals(listOf(move, PlayAction.ClearCardCounter(card.id, CounterColor.RED), PlayAction.ResetCorner(card.id)), requests)
        var state = inHand
        val accepted = mutableListOf<GameplayMutation>()
        for ((index, request) in requests.withIndex()) {
            if (index == 2) {
                // Concurrent face selection changes the live Reset read, not the captured target.
                val definition = state.definitions.single()
                val other = DefinitionFace(gameplayId(31), "Other", corner = "7/9")
                state = state.copy(definitions = listOf(definition.copy(definition = definition.definition.copy(
                    faces = definition.definition.faces + other))))
                state = gameplayChange(state, PlayAction.SetFace(card.id, other.faceId), 99).state!!
            }
            val before = state
            val changed = gameplayChange(before, request, 100 + index)
            assertEquals("accepted", changed.outcome)
            assertEquals(changed, GameplayHistory.replay(before, changed.mutation!!))
            accepted += changed.mutation
            state = changed.state!!
            if (index == 0) {
                assertTrue(changed.mutation.transition.fields.isEmpty())
                assertEquals(card.copy(containerId = gameplayId(20)), state.board.cards.first())
            }
        }
        assertEquals(3, accepted.map { it.requestId }.distinct().size)
        assertEquals(GameplayRead(card.id, GameplayField.FACE, gameplayId(31)), accepted.last().transition.reads.single())
        assertEquals(card.copy(faceId = gameplayId(31), corner = "7/9", counters = emptyMap()), state.board.cards.first())
        val sameContainer = move.copy(source = MoveSource(gameplayId(20)), destination = MoveDestination(gameplayId(20), MoveInsertion.BOTTOM))
        assertEquals(listOf(sameContainer), GameplayRules.moveActions(state, sameContainer))
        val tokenState = inHand.copy(board = inHand.board.copy(cards = inHand.board.cards.map {
            if (it.id == card.id) it.copy(token = true) else it
        }))
        assertEquals(listOf(move), GameplayRules.moveActions(tokenState, move))
        assertFalse(gameplayChange(tokenState, move).state!!.board.cards.any { it.id == card.id })
    }
}
