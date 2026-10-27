package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import java.util.UUID
import kotlin.test.*

class ResultActionsTest {
    private fun id() = UUID.randomUUID().toString()
    private val a = AccountIdentity(id(), "Alice")
    private val b = AccountIdentity(id(), "Bob")
    private val game = GameSummary(id(), "Result", "2026-10-23T00:00:00Z", a, b, id(), id(), InvitationState.ACCEPTED)
    private val preparation = PreparationRules.empty(game)
    private val initial = PlayState(game.id, id(), game.preparationRound, id(), PlayRoot(id(), id()), id(),
        BoardState(listOf(PlayPlayer(a.accountId), PlayPlayer(b.accountId)), emptyList(), emptyList(), TurnState(a.accountId)),
        emptyList(), emptyList(), InitialStarter(StarterChoice(a.accountId), a.accountId)).let { it.copy(currentNodeId = it.root.id) }
    private val active = game.copy(lifecycle = GameLifecycle.ACTIVE, playId = initial.id, revision = "1")
    private val target = ResultTarget(game.id, initial.id)
    private val proposals = mutableMapOf<String, GameCommand>()
    private fun proposed(outcome: SuggestedResult = SuggestedResult.WIN): PlayState {
        val command = GameCommand(id(), GameScope(game.id, playId = initial.id), GameAction.Play(PlayAction.SuggestResult(outcome)), id(), "1")
        val state = GameplayRules.change(initial, a.accountId, command).state!!
        proposals[state.currentNodeId] = command
        return state
    }

    private class Wire : GameConnection {
        lateinit var listener: GameConnection.Listener
        val frames = mutableListOf<GameClientMessage>()
        override fun connect(token: String, listener: GameConnection.Listener) { this.listener = listener }
        override fun send(message: GameClientMessage): Boolean { frames.add(message); return true }
        override fun disconnect() = Unit
        override fun close() = Unit
    }
    private inner class Fixture(val actor: String = a.accountId) : AutoCloseable {
        val wire = Wire(); var token: String? = "synthetic"; val settled = mutableListOf<GameResult>()
        val session = GameSession(wire, { token }, { it() }, {}, {}, { _, result -> settled.add(result) }, {})
        init { session.watch(listOf(active)); connect(); head(active, play = initial) }
        fun message(message: GameServerMessage) = wire.listener.message(message)
        fun connect(statuses: List<GameRequestStatus> = emptyList(), generation: String = "1") {
            wire.listener.opened(); message(GameWelcome(gameplayRulesVersion = CURRENT_GAMEPLAY_RULES_VERSION))
            val stream = wire.frames.mapNotNull { when (it) { is OpenGameStream -> it.streamId; is ResumeGameStream -> it.streamId; else -> null } }.last()
            message(GameStreamReady(game.id, stream, generation, statuses))
        }
        fun head(summary: GameSummary, preparation: LobbyPreparation? = null, play: PlayState? = null) {
            // The production replica accepts a root or recorded deltas, not an arbitrary non-root board.
            val command = play?.let { state -> commands().find {
                CommandIds.uuidV5(it.requestId, "board-node") == state.currentNodeId } ?: proposals[state.currentNodeId] }
            if (command != null && session.acceptedPlay(active)?.currentNodeId == initial.root.id) {
                val change = GameplayRules.change(initial, a.accountId, command)
                assertEquals(play, change.state)
                val root = GameplayTree(initial)
                val next = assertNotNull(root.play(a.accountId, command).tree)
                message(GameRevision(game.id, "1", summary.revision, command.requestId, a.accountId, command.scope,
                    summary, play = play, mutation = change.mutation, group = play!!.currentGroup, treeDelta = TreeSync.delta(root, next)))
            } else message(GameHead(game.id, summary.revision, summary, preparation = preparation, play = play))
        }
        fun commands() = wire.frames.filterIsInstance<GameCommand>()
        fun result(command: GameCommand, revision: String = "2", outcome: String = "accepted") =
            message(GameResult(command.requestId, command.scope, outcome, ingressOrdinal = "1", commitRevision = revision.takeIf { outcome == "accepted" }))
        override fun close() = session.close()
    }

    @Test fun resultSuggestionPredictsSharedMutationButReceiptOrResultCannotReturnOrResetBoard() = Fixture().use { f ->
        assertTrue(f.session.suggestResult(active, a.accountId, target, SuggestedResult.LOSS))
        val command = f.commands().single()
        assertEquals(GameScope(game.id, playId = initial.id), command.scope); assertTrue(GameWire.valid(command))
        val prediction = f.session.play(active)!!
        assertEquals(SuggestedResult.LOSS, prediction.board.resultSuggestion!!.outcome)
        assertEquals(initial, f.session.acceptedPlay(active)); assertEquals(HistoryCategory.S.key, prediction.currentGroup!!.category)
        assertFalse(f.session.suggestResult(active, a.accountId, target, SuggestedResult.WIN))
        f.message(GameReceipt(command.requestId, command.scope, "1")); f.result(command)
        assertEquals(prediction, f.session.play(active)); assertTrue(f.session.pending(game.id))
        assertEquals(GameLifecycle.ACTIVE, f.session.summary(active).lifecycle)
        f.head(active.copy(revision = "2"), play = prediction)
        assertFalse(f.session.pending(game.id)); assertEquals(prediction, f.session.play(active))
    }

    @Test fun pendingSuggestionBlocksBothClientsAtTheLiveEndpointEvenWhileReviewingAnEarlierBoard() {
        val proposed = proposed()
        assertFalse(ResultActions.blocksGameActions(active, initial))
        assertTrue(ResultActions.blocksGameActions(active, proposed))
        assertTrue(ResultActions.blocksGameActions(active, initial, proposed))
        assertTrue(ResultActions.blocksGameActions(active, proposed, initial))
        assertFalse(ResultActions.blocksGameActions(game, proposed))
        assertFalse(ResultActions.blocksGameActions(active.copy(playId = id()), proposed))
        for (viewer in listOf(a.accountId, b.accountId)) {
            assertNotNull(ResultActions.pendingPresentation(active, proposed, viewer))
            Fixture(viewer).use { f ->
                f.head(active.copy(revision = "2"), play = proposed)
                assertFalse(f.session.gameplay(active, viewer, PlayAction.NextStep))
                assertFalse(f.session.gameplay(active, viewer, PlayAction.SetLife(viewer, "19")))
                assertFalse(f.session.gameplayBatch(active, viewer, listOf(PlayAction.NextStep), id()))
                assertFalse(f.session.historyControl(active, viewer, GameAction.Redo))
                if (viewer == b.accountId) assertFalse(f.session.historyControl(active, viewer, GameAction.Undo))
                assertTrue(f.commands().isEmpty())
                val target = ResultAcceptanceTarget(target, proposed.board.resultSuggestion!!.id)
                assertTrue(f.session.rejectResult(active, viewer, target), "Reject result for $viewer: ${f.session.error}")
                assertNull(f.session.play(active)!!.board.resultSuggestion)
                val action = f.commands().single().action
                if (viewer == a.accountId) {
                    assertEquals(GameAction.Undo, action)
                    assertFalse(f.session.rejectResult(active, viewer, target))
                    assertEquals(1, f.commands().size)
                } else assertEquals(GameAction.Play(PlayAction.DeclineResult(target.suggestionId)), action)
                // Acceptance still owns the lock until the server publishes resolution.
                assertFalse(f.session.gameplay(active, viewer, PlayAction.NextStep))
            }
        }
    }

    @Test fun rejectionRemovesOnlySuggestionOverlayAndGraceWaitNeverRetargetsOldPlay() = Fixture().use { f ->
        assertFalse(f.session.suggestResult(active, a.accountId, target.copy(playId = id()), SuggestedResult.DRAW))
        assertTrue(f.session.suggestResult(active, a.accountId, target, SuggestedResult.DRAW))
        val command = f.commands().single()
        f.result(command, outcome = "rejected")
        assertEquals(initial, f.session.play(active)); assertFalse(f.session.pending(game.id))
        f.wire.listener.disconnected(false)
        assertTrue(f.session.suggestResult(active, a.accountId, target, SuggestedResult.WIN))
        assertEquals(1, f.commands().size)
        assertTrue(f.session.pending(game.id)) // Captured during grace, not sent before recovery.
    }

    @Test fun otherPlayerAcceptsExactCapturedDialogOnceAndOnlyRevisionReturnsBothReadyCleared() = Fixture(b.accountId).use { f ->
        val proposed = proposed(); val suggestion = proposed.board.resultSuggestion!!
        val captured = ResultAcceptanceTarget(target, suggestion.id)
        f.head(active.copy(revision = "2"), play = proposed)
        assertFalse(f.session.acceptResult(active, a.accountId, captured))
        assertFalse(f.session.acceptResult(active, b.accountId, captured.copy(suggestionId = id())))
        assertTrue(f.session.acceptResult(active, b.accountId, captured))
        assertFalse(f.session.acceptResult(active, b.accountId, captured))
        val command = f.commands().single(); assertEquals(GameAction.AcceptResult(suggestion.id), command.action)
        assertEquals(GameScope(game.id, playId = initial.id), command.scope)
        f.result(command, "3"); assertEquals(proposed, f.session.play(active)); assertTrue(f.session.pending(game.id))
        assertFalse(f.session.acceptResult(active, b.accountId, captured))
        val returned = game.copy(preparationRound = id(), revision = "3")
        f.head(returned, preparation.copy(roundId = returned.preparationRound))
        assertNull(f.session.play(returned)); assertFalse(f.session.pending(game.id))
        assertTrue(f.session.preparation(returned)!!.players.none { it.ready })
        assertFalse(f.session.acceptResult(returned, b.accountId, captured))
        f.head(active.copy(revision = "2"), play = proposed) // Stale dialog/head cannot reopen ended play.
        assertEquals(returned, f.session.summary(game)); assertNull(f.session.play(game))
        val fresh = initial.copy(id = id(), preparationRound = returned.preparationRound, root = PlayRoot(id(), id()), board = initial.board)
            .let { it.copy(currentNodeId = it.root.id) }
        val restarted = returned.copy(lifecycle = GameLifecycle.ACTIVE, playId = fresh.id, revision = "4")
        f.head(restarted, play = fresh)
        f.result(command, "3") // A late original result is not a state snapshot.
        assertEquals(fresh, f.session.play(restarted)); assertFalse(f.session.acceptResult(restarted, b.accountId, captured))
        assertFalse(f.session.suggestResult(restarted, b.accountId, target, SuggestedResult.DRAW))
    }

    @Test fun uncertainAcceptanceRecoversOriginalIdentityAcrossReconnectWithoutReapplyingCurrentState() = Fixture(b.accountId).use { f ->
        val proposed = proposed(); f.head(active.copy(revision = "2"), play = proposed)
        val captured = ResultAcceptanceTarget(target, proposed.board.resultSuggestion!!.id)
        assertTrue(f.session.acceptResult(active, b.accountId, captured)); val original = f.commands().single()
        val oldListener = f.wire.listener; oldListener.disconnected(false); f.session.tick()
        f.connect(listOf(GameRequestStatus(original.requestId, "notAdmitted")), "2")
        f.head(active.copy(revision = "2"), play = proposed)
        assertEquals(original.copy(generation = "2"), f.commands().last())
        oldListener.message(GameHead(game.id, "99", active.copy(revision = "99"), play = proposed))
        assertEquals("2", f.session.summary(active).revision)
        f.result(original, "3"); assertEquals(proposed, f.session.play(active))
        f.session.clear(); assertNull(f.session.play(active)); assertFalse(f.session.pending(game.id))
    }

    @Test fun presentationRequiresExplicitPlaybackGateAndCurrentSuggestionNotHistoricalReceipt() {
        for (outcome in SuggestedResult.entries) {
            val proposed = proposed(outcome); val suggestion = proposed.board.resultSuggestion!!
            val gate = ResultPlaybackGate(target, suggestion.id)
            assertNull(ResultActions.presentation(active, proposed, b.accountId, null))
            assertNull(ResultActions.presentation(active, proposed, b.accountId, gate.copy(suggestionId = id())))
            assertNull(ResultActions.presentation(active, proposed, b.accountId, gate.copy(play = target.copy(playId = id()))))
            assertNull(ResultActions.presentation(active, initial, b.accountId, gate))
            assertNull(ResultActions.presentation(game, proposed, b.accountId, gate))
            assertNull(ResultActions.presentation(active, proposed, id(), gate))
            val opponent = ResultActions.presentation(active, proposed, b.accountId, gate)!!
            assertTrue(opponent.canAccept); assertTrue(opponent.text.startsWith("Alice proposed"))
            assertEquals(ResultAcceptanceTarget(target, suggestion.id), opponent.target)
            val author = ResultActions.presentation(active, proposed, a.accountId, gate)!!
            assertFalse(author.canAccept); assertTrue(author.text.startsWith("You proposed"))
            if (outcome == SuggestedResult.WIN) assertTrue(opponent.text.contains("their win"))
            if (outcome == SuggestedResult.LOSS) assertTrue(opponent.text.contains("their loss"))
        }
        // This is gate-validation coverage, not execution of final-board playback (owned by I22/I28b).
    }
}
