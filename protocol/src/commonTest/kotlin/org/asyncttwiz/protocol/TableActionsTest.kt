package org.asyncttwiz.protocol

import kotlin.test.*

class TableActionsTest {
    private fun id(n: Int) = "00000000-0000-4000-8000-${n.toString().padStart(12, '0')}"
    private fun state(turn: TurnState = TurnState(id(7))) = PlayState(id(1), id(2), id(3), id(4),
        PlayRoot(id(5), id(6)), id(5), BoardState(listOf(PlayPlayer(id(7)), PlayPlayer(id(8))),
            listOf(PlayCard(id(9), "definition", id(7), id(10), id(11), tapped = true, note = "preserve",
                placement = PlayPlacement(BoardPosition(0, 0), id(40)))),
            listOf(PlayContainer(id(10), PlayZone.BATTLEFIELD, id(7), listOf(id(9)))), turn),
        emptyList(), emptyList(), InitialStarter(StarterChoice(id(7)), id(7)))
    private fun command(action: PlayAction, n: Int = 12) = GameCommand(id(n), GameScope(id(1), playId = id(2)),
        GameAction.Play(action), id(13), "1")
    private fun change(state: PlayState, action: PlayAction, n: Int = 12) = GameplayRules.change(state, id(8), command(action, n))

    @Test fun everyManualPositionAndCleanupTurnBoundaryHasLiteralReplay() {
        assertEquals(12, TurnPosition.entries.size)
        val initial = state()
        var current = initial
        for ((index, position) in TurnPosition.entries.withIndex()) {
            assertEquals(position, TurnPosition.of(current.board.turn))
            val before = current
            val result = change(before, PlayAction.NextStep, 20 + index)
            assertEquals("accepted", result.outcome)
            current = result.state!!
            assertEquals(result, GameplayHistory.replay(before, result.mutation!!))
            assertEquals(initial.board.cards, current.board.cards)
            assertEquals(initial.board.containers, current.board.containers)
            assertEquals(initial.board.players, current.board.players)
            if (position != TurnPosition.CLEANUP) {
                assertEquals(id(7), current.board.turn.accountId)
                assertEquals("1", current.board.turn.number)
                assertEquals(setOf(GameplayField.PHASE, GameplayField.STEP), result.mutation.transition.fields.map { it.field }.toSet())
            } else {
                assertEquals(TurnState(id(8), "2"), current.board.turn)
                assertEquals(4, result.mutation.transition.fields.size)
            }
        }
        val again = change(current, PlayAction.EndTurn, 40)
        assertEquals(TurnState(id(7), "3"), again.state!!.board.turn)
    }

    @Test fun arbitraryPickerSkipsPreservePlayerNumberAndAllNonTrackingState() {
        for (from in TurnPosition.entries) for (to in TurnPosition.entries) {
            val original = state(TurnState(id(8), "27", from.phase, from.step))
            val result = change(original, PlayAction.ChooseStep(to))
            assertEquals(if (from == to) "noOp" else "accepted", result.outcome)
            assertEquals(original.board.copy(turn = original.board.turn.copy(phase = to.phase, step = to.step),
                attentionHolder = if (from == to) null else if (to == TurnPosition.DECLARE_BLOCKERS) id(7) else id(8)), result.state!!.board)
            if (from != to) assertEquals(result, GameplayHistory.replay(original, result.mutation!!))
        }
    }

    @Test fun endTurnFromEveryPositionUsesCurrentPlayerAndNumberWithNoOtherOperations() {
        for (position in TurnPosition.entries) for (player in listOf(id(7), id(8))) {
            val before = state(TurnState(player, "42", position.phase, position.step))
            val result = change(before, PlayAction.EndTurn)
            val nextPlayer = if (player == id(7)) id(8) else id(7)
            assertEquals(before.board.copy(turn = TurnState(nextPlayer, "43"), attentionHolder = nextPlayer), result.state!!.board)
            assertEquals(result, GameplayHistory.replay(before, result.mutation!!))
        }
    }

    @Test fun relativeTrackingUsesLiveStateAndReplayRejectsChangedRelevantTuples() {
        val initial = state()
        val first = change(initial, PlayAction.NextStep)
        val second = change(first.state!!, PlayAction.NextStep, 14)
        assertEquals(TurnPosition.DRAW, TurnPosition.of(second.state!!.board.turn))
        assertEquals("replayBlocked", GameplayHistory.replay(initial, second.mutation!!).reason)
        val direct = change(initial, PlayAction.ChooseStep(TurnPosition.COMBAT_DAMAGE))
        // The picker now also records the attention destination, which depends on the turn player.
        val otherTurn = initial.copy(board = initial.board.copy(turn = initial.board.turn.copy(accountId = id(8), number = "9")))
        assertEquals("replayBlocked", GameplayHistory.replay(otherTurn, direct.mutation!!).reason)
        val end = change(initial, PlayAction.EndTurn)
        assertEquals("replayBlocked", GameplayHistory.replay(otherTurn, end.mutation!!).reason)
        assertEquals("9223372036854775808", change(state(TurnState(id(7), Long.MAX_VALUE.toString())), PlayAction.EndTurn).state!!.board.turn.number)
    }

    @Test fun literalDieCoinCodecValidationAndReplayNeverMutateBoardOrResample() {
        val actions = listOf(PlayAction.RollDie(6, 4), PlayAction.RollDie(1, 1),
            PlayAction.RollDie(Int.MAX_VALUE, Int.MAX_VALUE), PlayAction.FlipCoin(CoinFace.HEADS), PlayAction.FlipCoin(CoinFace.TAILS))
        for (action in actions) {
            val original = state()
            val command = command(action)
            assertTrue(GameWire.valid(command))
            assertEquals(command, GameWire.decodeClient(GameWire.json.encodeToString<GameClientMessage>(command)))
            val result = change(original, action)
            assertEquals("accepted", result.outcome)
            assertEquals(original.board, result.state!!.board)
            assertNotNull(result.mutation!!.transition.random)
            assertTrue(result.mutation.transition.fields.isEmpty())
            assertEquals(id(8), result.mutation.author)
            repeat(3) { assertEquals(result, GameplayHistory.replay(original, result.mutation)) }
            val corrupt = result.mutation.copy(transition = result.mutation.transition.copy(random = GameplayRandomResult(RandomTool.DIE, "0", "6")))
            assertEquals("replayBlocked", GameplayHistory.replay(original, corrupt).reason)
        }
        for (action in listOf(PlayAction.RollDie(0, 0), PlayAction.RollDie(-1, 1), PlayAction.RollDie(6, 0), PlayAction.RollDie(6, 7))) {
            assertFalse(GameWire.valid(command(action)))
            assertEquals("invalidInput", change(state(), action).reason)
        }
        for (sides in listOf("06", "2147483648", "+6", "6.0")) assertFalse(GameWire.valid(command(PlayAction.RollDie(sides, 1))))
        assertTrue(GameWire.json.encodeToString<GameClientMessage>(command(PlayAction.RollDie(6, 4))).contains("\"sides\":\"6\""))
        val first = change(state(), PlayAction.RollDie(6, 4))
        val repeatedValue = change(first.state!!, PlayAction.RollDie(6, 4), 14)
        assertEquals("accepted", repeatedValue.outcome)
        assertNotEquals(first.mutation!!.id, repeatedValue.mutation!!.id)
        assertEquals(GameWire.digest(command(PlayAction.RollDie(6, 4))),
            GameWire.digest(command(PlayAction.RollDie(6, 4)).copy(generation = "2")))
        assertNotEquals(GameWire.digest(command(PlayAction.RollDie(6, 4))), GameWire.digest(command(PlayAction.RollDie(6, 5))))
    }

    @Test fun playerCounterRelativeEditsAbsentZeroTextAndArbitraryPrecision() {
        val initial = state()
        val first = change(initial, PlayAction.AdjustPlayerCounter(id(7), CounterColor.BLUE, "-2"))
        assertEquals(GameplayFieldChange(id(7), GameplayField.COUNTER, null, "-2", CounterColor.BLUE), first.mutation!!.transition.fields.single())
        val second = change(first.state!!, PlayAction.AdjustPlayerCounter(id(7), CounterColor.BLUE, "3"), 14)
        assertEquals("1", second.state!!.board.players.first().counters[CounterColor.BLUE])
        assertEquals("replayBlocked", GameplayHistory.replay(initial, second.mutation!!).reason)
        assertEquals(second, GameplayHistory.replay(first.state, second.mutation))
        assertEquals("noOp", change(initial, PlayAction.AdjustPlayerCounter(id(7), CounterColor.BLUE, "0")).outcome)
        val text = change(initial, PlayAction.SetPlayerCounter(id(7), CounterColor.BLUE, "  mana  ")).state!!
        assertEquals("mana", text.board.players.first().counters[CounterColor.BLUE])
        assertEquals("inapplicable", change(text, PlayAction.AdjustPlayerCounter(id(7), CounterColor.BLUE, "1")).reason)
        assertFalse(GameplayRules.valid(PlayAction.AdjustPlayerCounter(id(7), CounterColor.BLUE, "1.0")))
        val cleared = change(text, PlayAction.SetPlayerCounter(id(7), CounterColor.BLUE, " \n "))
        assertFalse(CounterColor.BLUE in cleared.state!!.board.players.first().counters)
        val max = change(initial, PlayAction.SetPlayerCounter(id(7), CounterColor.BLUE, Long.MAX_VALUE.toString())).state!!
        assertEquals("9223372036854775808", change(max, PlayAction.AdjustPlayerCounter(id(7), CounterColor.BLUE, "1")).state!!.board.players.first().counters[CounterColor.BLUE])
        assertEquals(initial.board.copy(players = first.state.board.players), first.state.board)
    }

    @Test fun scalarArithmeticUsesUnboundedDecimalGrammarAndPinnedTrimming() {
        val cases = listOf(Triple("+0009", "1", "10"), Triple("-9", "-2", "-11"),
            Triple("-09", "+0009", "0"), Triple("-9", "20", "11"), Triple("9", "-20", "-11"),
            Triple("-0", "-0", "0"), Triple("-1000000000000000000000000000000", "1", "-999999999999999999999999999999"))
        for ((from, delta, expected) in cases) assertEquals(expected, TableActions.addNumber(from, delta))
        for (invalid in listOf("1.0", "1e2", "١", " 1", "1/2", "")) assertFalse(TableActions.numeric(invalid))
        val tooLarge = "9".repeat(DeckValidation.FIELD_BYTES)
        assertNull(TableActions.addNumber(tooLarge, "1"))
        val original = state()
        val large = change(original, PlayAction.SetLife(id(7), "\u0085\u00a0+000999999999999999999999999\u200a\u3000")).state!!
        assertEquals("+000999999999999999999999999", large.board.players.first().life)
        val adjusted = change(large, PlayAction.AdjustLife(id(7), "1"))
        assertEquals("1000000000000000000000000", adjusted.state!!.board.players.first().life)
        assertEquals(adjusted, GameplayHistory.replay(large, adjusted.mutation!!))
        assertEquals("noOp", change(large, PlayAction.AdjustLife(id(7), "-0")).outcome)
    }

    @Test fun trackingAttachesBackwardAndRandomStartsFreshThenAttachesForward() {
        val tap = change(state(), PlayAction.SetTapped(id(9), false))
        val tracking = change(tap.state!!, PlayAction.NextStep, 14)
        assertEquals(tap.state.currentGroup!!.id, tracking.state!!.currentGroup!!.id)
        assertEquals("tap", tracking.state.currentGroup.category)
        // I22's category/prefix contract keeps T pending even after a backward companion.
        // Fill it with a substantive edit before asserting that a new random run starts separately.
        val anchor = change(tracking.state, PlayAction.AdjustLife(id(7), "1"), 15)
        assertEquals(tracking.state.currentGroup.id, anchor.state!!.currentGroup!!.id)
        assertEquals("edit", anchor.state.currentGroup.category)
        val die = change(anchor.state, PlayAction.RollDie(6, 3), 16)
        assertNotEquals(anchor.state.currentGroup.id, die.state!!.currentGroup!!.id)
        val coin = change(die.state, PlayAction.FlipCoin(CoinFace.HEADS), 17)
        val next = change(coin.state!!, PlayAction.NextStep, 18)
        val edit = change(next.state!!, PlayAction.AdjustLife(id(7), "1"), 19)
        assertEquals(die.state.currentGroup.id, edit.state!!.currentGroup!!.id)
        assertEquals("4", edit.state.currentGroup.count)
        val opponent = GameplayRules.change(edit.state, id(7), command(PlayAction.NextStep, 20))
        assertNotEquals(edit.state.currentGroup.id, opponent.state!!.currentGroup!!.id)
    }

    @Test fun ordinaryAuthorityAndIncarnationChecksApplyToAllNewActions() {
        for (action in listOf(PlayAction.EndTurn, PlayAction.NextStep, PlayAction.ChooseStep(TurnPosition.DRAW),
                PlayAction.RollDie(6, 1), PlayAction.FlipCoin(CoinFace.HEADS), PlayAction.AdjustPlayerCounter(id(7), CounterColor.GRAY, "1"))) {
            assertEquals("accessDenied", GameplayRules.change(state(), id(99), command(action)).reason)
            assertEquals("inapplicable", GameplayRules.change(state(), id(7), command(action).copy(scope = GameScope(id(1), playId = id(99)))).reason)
        }
    }
}
