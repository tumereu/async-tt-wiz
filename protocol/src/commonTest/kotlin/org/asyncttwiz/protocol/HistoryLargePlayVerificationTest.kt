package org.asyncttwiz.protocol

import kotlin.test.*

/** Independent IG-I23-01 verification; uses the shared reducer, never a second board implementation. */
class HistoryLargePlayVerificationTest {
    private val f = UndoFixture

    private fun line(count: Int): Pair<GameplayTree, PlayState> {
        val root = f.root()
        var state = root
        val nodes = linkedMapOf<String, GameplayMutation>()
        repeat(count) {
            val change = GameplayRules.change(state, f.b,
                f.command(GameAction.Play(PlayAction.AdjustLife(f.b, "1")), 10000 + it))
            state = assertNotNull(change.state)
            val node = assertNotNull(change.mutation)
            nodes[node.id] = node
        }
        return GameplayTree(root, nodes.toMap(), state.currentNodeId) to state
    }

    private fun encoded(tree: GameplayTree) = GameWire.json.encodeToString(GameplayTree.serializer(), tree)

    private fun rejectedUnchanged(tree: GameplayTree, reason: String, action: GameAction = GameAction.Undo,
        actor: String = f.a) {
        val before = encoded(tree)
        val current = assertNotNull(tree.stateAt(tree.currentNodeId))
        val result = SelectiveUndo.change(tree, actor, f.command(action, 90000))
        assertEquals(reason, result.reason)
        assertNull(result.tree)
        assertEquals(before, encoded(tree))
        assertSame(current, tree.stateAt(tree.currentNodeId))
    }

    @Test fun combinedLongPathAndLargeOffshootsRemainReadableAndAppendableColdAndWarm() {
        val (line, expected) = line(2113)
        val offshoots = (0 until 16391).associate {
            val change = GameplayRules.change(line.initial, f.a,
                f.command(GameAction.Play(PlayAction.SetTapped(f.id(9), true)), 20000 + it))
            val node = assertNotNull(change.mutation)
            node.id to node
        }
        val tree = line.copy(nodes = line.nodes + offshoots)
        assertEquals(expected, tree.stateAt(tree.currentNodeId))
        for (id in listOf(offshoots.keys.first(), offshoots.keys.last())) {
            val state = assertNotNull(tree.stateAt(id))
            assertTrue(state.board.cards.first().tapped)
            assertEquals("20", state.board.players.last().life)
        }
        assertEquals(line.currentNodeId, tree.currentNodeId)
        val cold = GameWire.json.decodeFromString(GameplayTree.serializer(), encoded(tree))
        val command = f.command(GameAction.Play(PlayAction.SetTapped(f.id(12), true)), 80000)
        val expectedAppend = GameplayRules.change(expected, f.a, command)
        for (input in listOf(cold, tree)) {
            val appended = assertNotNull(input.play(f.a, command).tree)
            assertEquals(18505, appended.nodes.size)
            assertEquals(input.nodes, appended.nodes - appended.currentNodeId)
            assertEquals(expectedAppend.state, appended.stateAt(appended.currentNodeId))
            assertEquals(expectedAppend.state, appended.copy().stateAt(appended.currentNodeId))
            assertNotNull(f.undo(appended).tree)
        }
        assertNotNull(f.undo(line, f.b).tree)
        assertEquals(1, Regex("\"board\"").findAll(encoded(tree)).count())
        assertFalse(encoded(tree).contains("currentMaterialization"))
    }

    @Test fun fullColdReplayRejectsEarlyMiddleTailCyclesAndOffPathReferenceCorruption() {
        val (tree, expected) = line(2113)
        assertEquals(expected, tree.stateAt(tree.currentNodeId))
        val ordered = tree.nodes.values.toList()
        for (node in listOf(ordered.first(), ordered[1056], ordered.last())) {
            val badNodes = listOf(node.copy(schemaVersion = 3), node.copy(id = f.id(999)),
                node.copy(parentId = f.id(999)), node.copy(author = f.id(999)),
                node.copy(transition = GameplayTransition()),
                node.copy(references = listOf(GameplayNodeReference(f.id(999), node.transition))))
            for (bad in badNodes) {
                val copy = tree.copy(nodes = tree.nodes + (node.id to bad))
                assertNull(copy.stateAt(copy.currentNodeId), "accepted corrupt ancestor ${node.id}")
                assertEquals("historyUnavailable", copy.play(f.a,
                    f.command(GameAction.Play(PlayAction.SetTapped(f.id(9), true)), 80000)).reason)
            }
        }
        val middle = ordered[1056]
        val tail = ordered.last()
        assertNull(tree.copy(nodes = tree.nodes + (middle.id to middle.copy(parentId = tail.id)))
            .stateAt(tail.id)) // a multi-node cycle, not only a self-parent
        val external = assertNotNull(GameplayRules.change(tree.initial, f.a,
            f.command(GameAction.Play(PlayAction.SetTapped(f.id(9), true)), 80001)).mutation)
        val referencedTail = tail.copy(references = listOf(GameplayNodeReference(external.id, external.transition)))
        val withReference = tree.copy(nodes = tree.nodes + (external.id to external) + (tail.id to referencedTail))
        assertEquals(expected, withReference.stateAt(tail.id))
        val changedReference = external.copy(transition = GameplayTransition())
        assertNull(withReference.copy(nodes = withReference.nodes + (external.id to changedReference)).stateAt(tail.id))
        assertEquals(expected, tree.stateAt(tail.id))
    }

    @Test fun sameIdTopologyRootScopeActivationUndoAndRedoNeverReuseAnOldBoard() {
        val first = f.tap(GameplayTree(f.root()))
        val second = f.tap(first, f.b, 12, n = 101)
        val warm = assertNotNull(second.stateAt(second.currentNodeId))
        assertTrue(warm.board.cards.all { it.tapped })
        val tail = second.nodes.getValue(second.currentNodeId)
        val rewired = second.copy(nodes = mapOf(tail.id to tail.copy(parentId = second.initial.root.id)))
        val state = assertNotNull(rewired.stateAt(tail.id))
        assertFalse(state.board.cards.first().tapped)
        assertTrue(state.board.cards.last().tapped)
        val initial = second.initial
        val newRoot = initial.copy(gameId = f.id(80), id = f.id(81),
            root = initial.root.copy(id = f.id(82)), currentNodeId = f.id(82),
            board = initial.board.copy(players = initial.board.players.map { it.copy(life = "71") }))
        val changed = rewired.copy(initial = newRoot,
            nodes = mapOf(tail.id to tail.copy(parentId = newRoot.root.id)))
        val changedState = assertNotNull(changed.stateAt(tail.id))
        assertEquals(newRoot.root, changedState.root)
        assertEquals(newRoot.id, changedState.id)
        assertEquals(newRoot.gameId, changedState.gameId)
        assertEquals("71", changedState.board.players.first().life)
        val command = f.command(GameAction.Play(PlayAction.AdjustLife(f.a, "1")), 80000)
        assertEquals("inapplicable", changed.play(f.a, command).reason)
        assertEquals("accessDenied", second.play(f.id(999), command).reason)
        val switched = assertNotNull(second.activate(first.currentNodeId).tree)
        assertFalse(assertNotNull(switched.stateAt(switched.currentNodeId)).board.cards.last().tapped)
        val back = assertNotNull(switched.activate(second.currentNodeId).tree)
        assertEquals(warm, back.stateAt(back.currentNodeId))
        val undone = assertNotNull(f.undo(second).tree)
        assertFalse(assertNotNull(undone.stateAt(undone.currentNodeId)).board.cards.first().tapped)
        val redone = assertNotNull(f.redo(undone).tree)
        assertEquals(warm, redone.stateAt(redone.currentNodeId))
        assertEquals(warm, second.stateAt(second.currentNodeId))
    }

    // HIS-084
    @Test fun warmInternalCurrentCannotHide50Versus51RetainedSuccessorsForUndoOrRedo() {
        val own = f.tap(GameplayTree(f.root()))
        var future = own
        repeat(50) { future = f.play(future, f.b, PlayAction.AdjustLife(f.b, "1"), 1000 + it) }
        val internal = future.copy(currentNodeId = own.currentNodeId)
        assertNotNull(internal.stateAt(internal.currentNodeId))
        val removed = assertNotNull(f.undo(internal).tree)
        assertEquals(50, removed.nodes.size)
        assertEquals(f.root().root.id, removed.currentNodeId)
        assertNotNull(removed.stateAt(removed.currentNodeId))
        assertEquals(internal.nodes, assertNotNull(f.redo(removed).tree).nodes)
        val tooLong = f.play(future, f.b, PlayAction.AdjustLife(f.b, "1"), 2000)
            .copy(currentNodeId = own.currentNodeId)
        rejectedUnchanged(tooLong, "tooFarBack")
        val removedAtEnd = removed.copy(currentNodeId = future.currentNodeId)
        val extra = f.play(removedAtEnd, f.b, PlayAction.AdjustLife(f.b, "1"), 2001)
        rejectedUnchanged(extra, "tooFarBack", GameAction.Redo)
        assertEquals(1, extra.redoByAccount.getValue(f.a).size)
    }

    @Test fun warmCacheCannotBlessChangedExactTargetSuffixReferencesOrFork() {
        val p = f.play(GameplayTree(f.root()), f.b, PlayAction.AdjustLife(f.a, "1"), 100)
        val q = f.tap(p, n = 101)
        val r = f.play(q, f.a, PlayAction.AdjustLife(f.a, "1"), 102)
        val withoutR = assertNotNull(f.undo(r).tree)
        assertNotNull(withoutR.stateAt(withoutR.currentNodeId))
        val withoutP = assertNotNull(f.undo(withoutR, f.b).tree)
        rejectedUnchanged(withoutP, "targetTransitionBlocked", GameAction.Redo)
        val own = f.tap(GameplayTree(f.root()))
        val dependent = f.tap(own, f.b, value = false, n = 103)
            .copy(currentNodeId = own.currentNodeId)
        rejectedUnchanged(dependent, "replayBlocked")
        val independent = f.tap(own, f.b, 12, n = 104)
        val tail = independent.nodes.getValue(independent.currentNodeId)
        val reference = tail.copy(references = listOf(GameplayNodeReference(own.currentNodeId,
            own.nodes.getValue(own.currentNodeId).transition)))
        rejectedUnchanged(independent.copy(nodes = independent.nodes + (tail.id to reference)), "referenceBlocked")
        val sibling = f.play(own, f.b, PlayAction.AdjustLife(f.b, "1"), 105)
        rejectedUnchanged(independent.copy(nodes = independent.nodes + sibling.nodes), "forkBoundary")
    }

    @Test fun warmUndoAndRedoAreIndependentOfAncestryAndPayloadBudgets() {
        val first = f.tap(GameplayTree(f.root()))
        val second = f.tap(first, card = 12, n = 101)
        val removed = assertNotNull(f.undo(second).tree)
        assertNotNull(f.undo(second).tree)
        assertEquals(second.nodes, assertNotNull(f.redo(removed).tree).nodes)
    }

    @Test fun ordinaryLargeBoardRemainsPlayableAndUndoableWithoutSerializingUnchangedPayload() {
        val initial = f.root()
        val cards = (0 until 96).map { initial.board.cards.first().copy(id = f.id(1000 + it), note = "n".repeat(4096)) }
        val board = initial.board.copy(cards = cards,
            containers = initial.board.containers.map { it.copy(cards = cards.map { card -> card.id }) })
        val tree = f.play(GameplayTree(initial.copy(board = board)), f.a,
            PlayAction.SetTapped(cards.first().id, true), 80000)
        assertTrue(assertNotNull(tree.stateAt(tree.currentNodeId)).board.cards.first().tapped)
        assertEquals(tree.stateAt(tree.currentNodeId), tree.copy().stateAt(tree.currentNodeId))
        assertNotNull(f.undo(tree).tree)
        val next = f.play(tree, f.b, PlayAction.SetTapped(cards.last().id, true), 80001)
        assertTrue(assertNotNull(next.stateAt(next.currentNodeId)).board.cards.last().tapped)
    }
}
