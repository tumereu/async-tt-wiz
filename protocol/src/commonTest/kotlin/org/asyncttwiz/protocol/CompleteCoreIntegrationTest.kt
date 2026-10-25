package org.asyncttwiz.protocol

import kotlin.test.*

/** Regression seams introduced by the three pinned normal merges; not history/server qualification. */
class CompleteCoreIntegrationTest {
    private val actor = gameplayId(7)
    private fun command(state: PlayState, action: PlayAction, n: Int) = GameCommand(gameplayId(n),
        GameScope(state.gameId, playId = state.id), GameAction.Play(action), gameplayId(80), "1")

    @Test fun guardedMoveManualAndBothCleanupFamiliesReplayWithoutChangingUnrelatedFields() {
        var state = gameplayFixture()
        var request = 100
        fun step(action: PlayAction): GameplayMutation {
            val before = state
            val change = GameplayRules.change(before, actor, command(before, action, request++))
            assertEquals("accepted", change.outcome, "$action: ${change.reason}")
            val mutation = assertNotNull(change.mutation)
            assertEquals(change, GameplayHistory.replay(before, mutation))
            assertEquals(mutation, GameWire.json.decodeFromString(GameplayMutation.serializer(),
                GameWire.json.encodeToString(GameplayMutation.serializer(), mutation)))
            // An unrelated family must not be silently dropped by replay of ANY accepted action.
            assertEquals("replayBlocked", GameplayHistory.replay(before, mutation.copy(transition =
                mutation.transition.copy(random = GameplayRandomResult(RandomTool.DIE, "2", "6")))).reason)
            state = assertNotNull(change.state)
            return mutation
        }
        val cardId = state.board.cards.first().id
        step(PlayAction.MoveCard(cardId, MoveSource(gameplayId(20)),
            MoveDestination(gameplayId(22), MoveInsertion.EMPTY, BoardPosition(2, -1))))
        step(PlayAction.SetNote(cardId, " last live "))
        step(PlayAction.SetOwner(cardId, gameplayId(8)))
        step(PlayAction.Combat(CombatAction.Open))
        val area = state.board.combat!!.id
        step(PlayAction.Combat(CombatAction.Declare(area, cardId)))
        step(PlayAction.Stack(StackAction.AddEffect(cardId, GameplayRules.stackDigest(state))))
        step(PlayAction.CloneToken(cardId))
        val token = state.board.cards.single { it.token }
        step(PlayAction.Combat(CombatAction.Declare(area, token.id)))
        step(PlayAction.Stack(StackAction.AddEffect(token.id, GameplayRules.stackDigest(state))))
        step(PlayAction.Link(LinkEndpoint(LinkEndpointKind.CARD, token.id), LinkEndpoint(LinkEndpointKind.PLAYER, actor)))
        val beforeMove = state
        val real = state.board.cards.single { it.id == cardId }
        val move = PlayAction.MoveCard(cardId, MoveSource(real.containerId, real.placement),
            MoveDestination(gameplayId(23), MoveInsertion.UNORDERED))
        val sequence = GameplayRules.moveActions(state, move)
        val moved = step(move)
        assertTrue(moved.transition.fields.isEmpty(), "ordinary move does not hide Reset/counter requests")
        assertEquals(real.copy(containerId = gameplayId(23), placement = null), state.board.cards.single { it.id == cardId })
        assertEquals(1, moved.transition.combatStack!!.occurrences.size)
        assertEquals(beforeMove.board.links, state.board.links)
        assertEquals(beforeMove.board.stackEffects, state.board.stackEffects)
        sequence.drop(1).forEach(::step)
        val cleaned = state.board.cards.single { it.id == cardId }
        assertEquals("3/5", cleaned.corner)
        assertTrue(cleaned.counters.isEmpty())
        assertEquals(real.note, cleaned.note)
        assertEquals(real.owner, cleaned.owner)
        step(PlayAction.SetNote(token.id, "final token snapshot"))
        val lastLive = state.board.cards.single { it.id == token.id }
        val removed = step(PlayAction.MoveCard(token.id, MoveSource(token.containerId, token.placement),
            MoveDestination(gameplayId(24), MoveInsertion.UNORDERED)))
        assertEquals(lastLive, removed.transition.tokens.single().from!!.card)
        assertTrue(state.board.combat!!.pairs.isEmpty())
        assertTrue(state.board.links.isEmpty())
        assertEquals(lastLive, state.board.stackEffects.single { it.sourceId == token.id }.frozen)
        assertNull(state.board.stackEffects.single { it.sourceId == cardId }.frozen)
        assertNotNull(GameplayHistory.unapply(state, removed), "compound cleanup has an exact complete inverse")
    }

    @Test fun mixedColdReviewBeyondUndoLimitsAndFirstSuccessfulHistoricalContinuation() {
        val initial = gameplayFixture()
        var state = initial
        val nodes = linkedMapOf<String, GameplayMutation>()
        val first = PlayAction.MoveCard(state.board.cards.first().id, MoveSource(gameplayId(20)),
            MoveDestination(gameplayId(22), MoveInsertion.EMPTY, BoardPosition(1, 2)))
        val actions = listOf(first, PlayAction.SetNote(state.board.cards.first().id, "retained"),
            PlayAction.ShuffleLibrary(gameplayId(20), RandomV1.encodeSeed(ByteArray(32))), PlayAction.NextStep)
        repeat(2055) { index ->
            val action = actions.getOrNull(index) ?: PlayAction.AdjustLife(actor, "1")
            val change = GameplayRules.change(state, actor, command(state, action, 1000 + index))
            state = assertNotNull(change.state)
            val node = assertNotNull(change.mutation)
            nodes[node.id] = node
        }
        val tree = GameplayTree(initial, nodes, state.currentNodeId)
        assertEquals(state, tree.stateAt(tree.currentNodeId))
        assertEquals(initial, tree.stateAt(initial.root.id))
        val missing = command(state, PlayAction.SetNote(gameplayId(999), "missing"), 5000)
            .copy(continuation = Continuation(initial.root.id, true))
        assertEquals("inapplicable", tree.play(actor, missing).reason)
        val noOp = command(state, PlayAction.SetNote(initial.board.cards.first().id, initial.board.cards.first().note), 5001)
            .copy(continuation = Continuation(initial.root.id, true))
        assertEquals("noOp", tree.play(actor, noOp).outcome)
        assertTrue(tree.branches.isEmpty())
        val historical = command(state, first, 5002).copy(continuation = Continuation(initial.root.id, true))
        val fork = assertNotNull(tree.play(actor, historical).tree)
        val branch = fork.branches.values.single()
        assertEquals(tree.currentNodeId, branch.displacedNodeId)
        assertEquals(tree.currentBranchId, branch.displacedBranchId)
        assertEquals(branch.id, fork.nodes.getValue(fork.currentNodeId).branchId)
        val decoded = GameWire.json.decodeFromString(GameplayTree.serializer(), GameWire.json.encodeToString(GameplayTree.serializer(), fork))
        assertEquals(fork.stateAt(fork.currentNodeId), decoded.stateAt(decoded.currentNodeId))
        assertEquals(state, decoded.stateAt(tree.currentNodeId))
        assertEquals(nodes, decoded.nodes - decoded.currentNodeId)
    }

    @Test fun librarySliceAndNormalizedPendingGapUseTheFullMovementEngine() {
        val initial = gameplayFixture()
        val library = initial.board.containers.first()
        val ids = library.cards
        val plans = LibraryActions.moveCaptured(initial.board, listOf(ids[1], ids[3]),
            MoveDestination(library.id, MoveInsertion.TOP))
        var state = initial
        plans.forEachIndexed { index, action ->
            val change = GameplayRules.change(state, actor, command(state, action, 100 + index))
            assertEquals("accepted", change.outcome)
            assertEquals(change, GameplayHistory.replay(state, change.mutation!!))
            state = change.state!!
        }
        assertEquals(listOf(ids[1], ids[3], ids[0], ids[2], ids[4]), state.board.containers.first().cards)
        val bottom = LibraryActions.moveSlice(initial.board, library.id, ContentsSelection.BOTTOM, 2,
            MoveDestination(gameplayId(21), MoveInsertion.UNORDERED))
        val changed = GameplayRules.change(initial, actor, command(initial,
            PlayAction.MoveCard(ids.last(), MoveSource(library.id), MoveDestination(library.id, MoveInsertion.TOP)), 110)).state!!
        assertEquals("movementConflict", GameplayRules.change(changed, actor, command(changed, bottom.first(), 111)).reason)
    }
}
