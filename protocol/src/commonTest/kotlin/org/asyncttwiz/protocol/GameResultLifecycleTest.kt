package org.asyncttwiz.protocol

import kotlin.test.*

class GameResultLifecycleTest {
    private fun id(n: Int) = "00000000-0000-4000-8000-${n.toString().padStart(12, '0')}"
    private val a = AccountIdentity(id(1), "A")
    private val b = AccountIdentity(id(2), "B")
    private val game = GameSummary(id(3), "Result", "2026-10-23T00:00:00Z", a, b, id(4), id(5), InvitationState.ACCEPTED,
        lifecycle = GameLifecycle.ACTIVE, playId = id(6))
    private fun play() = PlayState(game.id, game.playId!!, game.preparationRound, id(7), PlayRoot(id(8), id(9)), id(8),
        BoardState(listOf(PlayPlayer(a.accountId), PlayPlayer(b.accountId)), emptyList(), emptyList(), TurnState(a.accountId)),
        emptyList(), emptyList(), InitialStarter(StarterChoice(a.accountId), a.accountId))
    private fun command(action: GameAction, n: Int = 10) = GameCommand(id(n), GameScope(game.id, playId = game.playId), action, id(11), "1")
    private fun suggest(outcome: SuggestedResult = SuggestedResult.WIN, actor: String = a.accountId, n: Int = 10) =
        GameplayRules.change(play(), actor, command(GameAction.Play(PlayAction.SuggestResult(outcome)), n))

    @Test fun declineIsExactOpponentOnlyVersion22StateAndItsUndoProtectsTheSuggestionTuple() {
        val proposalCommand = command(GameAction.Play(PlayAction.SuggestResult(SuggestedResult.DRAW)))
        val proposedTree = GameplayTree(play()).play(a.accountId, proposalCommand).tree!!
        val proposed = proposedTree.stateAt(proposedTree.currentNodeId)!!
        val suggestion = proposed.board.resultSuggestion!!
        val decline = command(GameAction.Play(PlayAction.DeclineResult(suggestion.id)), 12)
        assertEquals(decline, GameWire.decodeClient(GameWire.json.encodeToString(GameClientMessage.serializer(), decline)))
        assertEquals("otherPlayerRequired", GameplayRules.change(proposed, a.accountId, decline).reason)
        assertEquals("inapplicable", GameplayRules.change(proposed, b.accountId,
            decline.copy(action = GameAction.Play(PlayAction.DeclineResult(id(99))))).reason)
        val change = GameplayRules.change(proposed, b.accountId, decline)
        val event = change.mutation!!
        assertEquals(22, event.transition.rulesVersion)
        assertEquals(ResultSuggestionChange(suggestion, null), event.transition.resultSuggestion)
        assertNotNull(event.transition.concerns)
        assertEquals(proposed.board.copy(resultSuggestion = null), change.state!!.board)
        assertEquals(change.state, GameplayHistory.apply(proposed, event))
        assertEquals(proposed, GameplayHistory.unapply(change.state!!, event))
        assertEquals(change, GameplayHistory.replay(proposed, event))
        val old = event.copy(transition = event.transition.copy(rulesVersion = 21))
        val sealedOld = old.copy(transition = old.transition.copy(delta = old.transition.delta!!.copy(seal = GameplayDeltas.seal(old))))
        assertNull(GameplayHistory.apply(proposed, sealedOld))
        val declinedTree = proposedTree.play(b.accountId, decline).tree!!
        assertNotNull(SelectiveUndo.change(declinedTree, a.accountId, command(GameAction.Undo, 13)).reason)
        val restored = SelectiveUndo.change(declinedTree, b.accountId, command(GameAction.Undo, 14)).tree!!
        assertEquals(proposed.board, restored.stateAt(restored.currentNodeId)!!.board)
        assertNull(SelectiveUndo.change(restored, a.accountId, command(GameAction.Undo, 15)).tree!!
            .stateAt(play().root.id)!!.board.resultSuggestion)
    }

    @Test fun allOutcomesHaveSharedExactHistoryTuplesAndReplayNotUnilateralEnd() {
        for (outcome in SuggestedResult.entries) {
            val change = suggest(outcome)
            val state = change.state!!; val mutation = change.mutation!!
            val suggestion = state.board.resultSuggestion!!
            assertEquals(a.accountId, suggestion.author); assertEquals(outcome, suggestion.outcome)
            assertEquals(ResultSuggestionChange(null, suggestion), mutation.transition.resultSuggestion)
            assertEquals(play().board.copy(resultSuggestion = suggestion), state.board)
            assertEquals(change, GameplayHistory.replay(play(), mutation))
            assertEquals("replayBlocked", GameplayHistory.replay(play(), mutation.copy(transition =
                mutation.transition.copy(resultSuggestion = ResultSuggestionChange(null, suggestion.copy(author = b.accountId))))).reason)
            assertEquals("replayBlocked", GameplayHistory.replay(state, mutation).reason)
            assertEquals("inapplicable", GameplayRules.change(state, b.accountId,
                command(GameAction.Play(PlayAction.SuggestResult(SuggestedResult.DRAW)), 12)).reason)
        }
    }

    @Test fun strictSingletonHasBothBoundariesIncludingTapAndSameAuthorEdit() {
        val initial = play().let { it.copy(board = it.board.copy(
            cards = listOf(PlayCard(id(20), "retained", a.accountId, id(21), id(22),
                placement = PlayPlacement(BoardPosition(0, 0), id(40)))),
            containers = listOf(PlayContainer(id(21), PlayZone.BATTLEFIELD, a.accountId, listOf(id(20)))))) }
        val tap = GameplayRules.change(initial, a.accountId, command(GameAction.Play(PlayAction.SetTapped(id(20), true)), 13))
        val previous = tap.state!!
        assertEquals("tap", previous.currentGroup!!.category)
        val proposal = GameplayRules.change(previous, a.accountId, command(GameAction.Play(PlayAction.SuggestResult(SuggestedResult.DRAW))))
        assertEquals(HistoryCategory.S.key, proposal.state!!.currentGroup!!.category)
        assertEquals("1", proposal.state.currentGroup!!.count)
        assertNotEquals(previous.currentGroup!!.id, proposal.state.currentGroup!!.id)
        val following = GameplayRules.change(proposal.state, a.accountId, command(GameAction.Play(PlayAction.SetLife(a.accountId, "18")), 14))
        assertEquals("1", following.state!!.currentGroup!!.count)
        assertNotEquals(proposal.state.currentGroup!!.id, following.state.currentGroup!!.id)
        // Other shared edits remain compatible; a pending result is not a whole-board freeze.
        assertEquals(proposal.state.board.resultSuggestion, following.state.board.resultSuggestion)
    }

    @Test fun exactOtherPlayerAcceptanceReturnsLastPreparationWithBothReadyClearedAndNewRound() {
        val play = suggest().state!!
        val suggestion = play.board.resultSuggestion!!
        val prepared = PreparationRules.empty(game).copy(players = listOf(PlayerPreparation(a.accountId, true), PlayerPreparation(b.accountId, true)))
        val accept = command(GameAction.AcceptResult(suggestion.id), 15)
        assertEquals(accept, GameWire.decodeClient(GameWire.json.encodeToString<GameClientMessage>(accept)))
        assertEquals("otherPlayerRequired", GameResultRules.accept(game, play, prepared, a.accountId, accept).reason)
        assertEquals("inapplicable", GameResultRules.accept(game, play, prepared, b.accountId,
            accept.copy(action = GameAction.AcceptResult(id(99)))).reason)
        assertEquals("accessDenied", GameResultRules.accept(game, play, prepared, id(99), accept).reason)
        val returned = GameResultRules.accept(game, play, prepared, b.accountId, accept).preparation!!
        assertEquals(prepared.copy(roundId = returned.roundId, players = prepared.players.map { it.copy(ready = false) }), returned)
        assertNotEquals(prepared.roundId, returned.roundId)
        assertEquals("inapplicable", StartRules.change(game.copy(lifecycle = GameLifecycle.LOBBY, playId = null, preparationRound = returned.roundId),
            returned, a.accountId, GameScope(game.id, game.preparationRound), GameAction.Ready).reason)
        assertEquals("lifecycleEnded", GameResultRules.accept(game.copy(lifecycle = GameLifecycle.LOBBY, playId = null), null, returned, b.accountId, accept).reason)
    }

    @Test fun removedSuggestionAndOldIncarnationCannotBeAcceptedAndScopesCannotMix() {
        val proposed = suggest().state!!
        val accept = command(GameAction.AcceptResult(proposed.board.resultSuggestion!!.id))
        val prepared = PreparationRules.empty(game)
        // This models the board input after removal, NOT an executed selective Undo command.
        assertEquals("inapplicable", GameResultRules.accept(game, proposed.copy(board = proposed.board.copy(resultSuggestion = null)), prepared, b.accountId, accept).reason)
        assertEquals("inapplicable", GameResultRules.accept(game, proposed, prepared, b.accountId,
            accept.copy(scope = GameScope(game.id, playId = id(99)))).reason)
        for (scope in listOf(GameScope(game.id, game.preparationRound), GameScope(game.id, game.preparationRound, playId = game.playId),
            GameScope(game.id, invitationId = game.invitationId, playId = game.playId))) assertFalse(GameWire.valid(accept.copy(scope = scope)))
        assertFalse(GameWire.valid(accept.copy(action = GameAction.AcceptResult("bad"))))
    }
}
