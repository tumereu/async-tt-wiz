package org.asyncttwiz.protocol

import kotlin.test.*

class HistoryLargePlayTest {
    private val f = UndoFixture

    // Build accepted records with the sole gameplay reducer, without quadratic fixture replay.
    private fun line(count: Int): Pair<GameplayTree, PlayState> {
        val initial = f.root()
        var state = initial
        val nodes = linkedMapOf<String, GameplayMutation>()
        repeat(count) {
            val change = GameplayRules.change(state, f.a,
                f.command(GameAction.Play(PlayAction.AdjustLife(f.a, "1")), 1000 + it))
            state = assertNotNull(change.state)
            val node = assertNotNull(change.mutation)
            nodes[node.id] = node
        }
        return GameplayTree(initial, nodes.toMap(), state.currentNodeId) to state
    }

    private fun branches(): GameplayTree {
        val initial = f.root()
        val nodes = linkedMapOf<String, GameplayMutation>()
        repeat(16385) {
            val node = assertNotNull(GameplayRules.change(initial, f.a,
                f.command(GameAction.Play(PlayAction.SetTapped(f.id(9), true)), 1000 + it)).mutation)
            nodes[node.id] = node
        }
        return GameplayTree(initial, nodes.toMap())
    }

    @Test fun ordinaryPlayBeyondSelectiveAncestorAndPayloadLimits() {
        val (tree, state) = line(2100)
        val command = f.command(GameAction.Play(PlayAction.AdjustLife(f.a, "1")), 9000)
        val expected = GameplayRules.change(state, f.a, command)
        val result = tree.play(f.a, command)
        assertEquals("accepted", result.outcome, "ordinary play rejected: ${result.reason}")
        val next = assertNotNull(result.tree)
        assertEquals(2101, next.nodes.size)
        assertEquals(expected.state, next.stateAt(next.currentNodeId))
        assertEquals(tree.nodes, next.nodes - next.currentNodeId)
    }

    @Test fun retainedStateBeyondSelectiveAncestorAndPayloadLimits() {
        val (tree, state) = line(2100)
        assertEquals(state, tree.stateAt(tree.currentNodeId))
        val middle = tree.nodes.values.elementAt(1049)
        assertEquals("1070", assertNotNull(tree.stateAt(middle.id)).board.players.first().life)
        assertEquals(tree.initial, tree.stateAt(tree.initial.root.id))
    }

    @Test fun ordinaryPlayWithLargeRetainedBranchMetadata() {
        val tree = branches()
        val result = tree.play(f.a, f.command(GameAction.Play(PlayAction.SetTapped(f.id(12), true)), 90000))
        assertEquals("accepted", result.outcome, "ordinary play rejected: ${result.reason}")
        val next = assertNotNull(result.tree)
        assertEquals(tree.nodes, next.nodes - next.currentNodeId)
        assertTrue(assertNotNull(next.stateAt(next.currentNodeId)).board.cards[1].tapped)
    }

    @Test fun retainedReviewWithLargeBranchMetadata() {
        val tree = branches()
        assertEquals(tree.initial, tree.stateAt(tree.currentNodeId))
        for (node in listOf(tree.nodes.values.first(), tree.nodes.values.last())) {
            val state = assertNotNull(tree.stateAt(node.id))
            assertEquals(node.id, state.currentNodeId)
            assertTrue(state.board.cards[0].tapped)
        }
    }

    @Test fun incrementalOrdinaryPlayAndColdRecoveryHaveIdenticalState() {
        var tree = GameplayTree(f.root())
        repeat(2100) { tree = f.play(tree, f.a, PlayAction.AdjustLife(f.a, "1"), 1000 + it) }
        val state = assertNotNull(tree.stateAt(tree.currentNodeId))
        assertEquals("2120", state.board.players.first().life)
        assertSame(state, tree.stateAt(tree.currentNodeId)) // current-only disposable checkpoint
        val encoded = GameWire.json.encodeToString(GameplayTree.serializer(), tree)
        assertEquals(1, Regex("\"board\"").findAll(encoded).count())
        assertFalse(encoded.contains("currentMaterialization"))
        val cold = GameWire.json.decodeFromString(GameplayTree.serializer(), encoded)
        assertEquals(tree, cold)
        assertEquals(state, cold.stateAt(cold.currentNodeId))
        val command = f.command(GameAction.Play(PlayAction.AdjustLife(f.a, "1")), 9000)
        assertEquals(tree.play(f.a, command).tree, cold.play(f.a, command).tree)
    }

    @Test fun longColdReplayChecksTheTailAndReferencesWithoutTruncation() {
        val (tree, state) = line(2100)
        assertEquals(state, tree.stateAt(tree.currentNodeId)) // warm original must not bless a copy
        val last = tree.nodes.getValue(tree.currentNodeId)
        val corruptions = listOf(last.copy(schemaVersion = 3),
            last.copy(parentId = last.id), last.copy(parentId = f.id(999)),
            last.copy(id = f.id(999)),
            last.copy(transition = GameplayTransition()),
            last.copy(references = listOf(GameplayNodeReference(f.id(999), last.transition))),
            last.copy(references = listOf(GameplayNodeReference(last.parentId, GameplayTransition()))))
        for (bad in corruptions) {
            val broken = tree.copy(nodes = tree.nodes + (last.id to bad))
            assertNull(broken.stateAt(last.id))
            val result = broken.play(f.a, f.command(GameAction.Play(PlayAction.SetTapped(f.id(9), true)), 9000))
            assertEquals("historyUnavailable", result.reason)
            assertNull(result.tree)
        }
        val first = tree.nodes.values.first()
        assertNull(tree.copy(nodes = tree.nodes + (first.id to first.copy(schemaVersion = 3))).stateAt(last.id))
        assertEquals(state, tree.stateAt(last.id))
    }

    @Test fun copiesAndActivationInvalidateCurrentCheckpointIncludingScopeAndRootData() {
        val first = f.tap(GameplayTree(f.root()))
        val second = f.tap(first, f.b, 12, n = 101)
        assertTrue(assertNotNull(second.stateAt(second.currentNodeId)).board.cards[1].tapped)
        val switched = assertNotNull(second.activate(first.currentNodeId).tree)
        assertFalse(assertNotNull(switched.stateAt(switched.currentNodeId)).board.cards[1].tapped)
        val root = second.initial
        val changedRoot = root.copy(gameId = f.id(90), id = f.id(91),
            board = root.board.copy(players = root.board.players.map { it.copy(life = "77") }))
        val rescaled = second.copy(initial = changedRoot)
        val state = assertNotNull(rescaled.stateAt(rescaled.currentNodeId))
        assertEquals(changedRoot.gameId, state.gameId)
        assertEquals(changedRoot.id, state.id)
        assertEquals("77", state.board.players.first().life)
        val command = f.command(GameAction.Play(PlayAction.AdjustLife(f.a, "1")), 102)
        assertEquals("inapplicable", rescaled.play(f.a, command).reason)
        val valid = command.copy(scope = GameScope(changedRoot.gameId, playId = changedRoot.id))
        val next = assertNotNull(rescaled.play(f.a, valid).tree)
        assertEquals("78", assertNotNull(next.stateAt(next.currentNodeId)).board.players.first().life)
    }

    @Test fun warmOrdinaryMaterializationSupportsLongGameUndoAndRedoWithoutBudgets() {
        val (large, state) = line(2100)
        assertEquals(state, large.stateAt(large.currentNodeId))
        assertNotNull(f.undo(large).tree)
        assertEquals(state, large.stateAt(large.currentNodeId))
        val first = f.tap(GameplayTree(f.root()))
        val second = f.tap(first, card = 12, n = 101)
        val removed = assertNotNull(f.undo(second).tree)
        assertNotNull(removed.stateAt(removed.currentNodeId))
        assertNotNull(f.undo(second).tree)
        val restored = assertNotNull(f.redo(removed).tree)
        assertEquals(second.stateAt(second.currentNodeId), restored.stateAt(restored.currentNodeId))
    }
}
