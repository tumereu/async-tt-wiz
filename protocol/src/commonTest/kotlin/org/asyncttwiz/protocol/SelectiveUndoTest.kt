package org.asyncttwiz.protocol

import kotlin.test.*

class SelectiveUndoTest {
    private val f = UndoFixture
    @Test fun latestOwnAcrossDeviceStreamsAndStrictLastUndoneFirst() {
        val first = f.tap(GameplayTree(f.root()))
        val second = f.tap(first, card = 12, n = 101)
        val firstUndo = f.undo(second)
        assertEquals(second.currentNodeId, firstUndo.targetId)
        val next = assertNotNull(firstUndo.tree)
        // A different device supplies the same account, not a client-selected/captured node.
        val deviceCommand = f.command(GameAction.Undo, 902).copy(streamId = f.id(50), generation = "2")
        val secondUndo = SelectiveUndo.change(next, f.a, deviceCommand)
        assertEquals(first.currentNodeId, secondUndo.targetId)
        val twice = assertNotNull(secondUndo.tree)
        assertEquals(listOf(second.currentNodeId, first.currentNodeId), twice.redoByAccount[f.a]!!.map { it.mutation.id })
        val firstRedo = f.redo(twice)
        assertEquals(first.currentNodeId, firstRedo.targetId)
        val secondRedo = f.redo(assertNotNull(firstRedo.tree))
        assertEquals(second.currentNodeId, secondRedo.targetId)
        assertEquals(second.nodes, assertNotNull(secondRedo.tree).nodes)
        assertTrue(secondRedo.tree.redoByAccount.isEmpty())
    }

    @Test fun scalarSameDeltaAndAbsoluteOverwriteStillRequireExactRelevantFromTo() {
        val first = f.play(GameplayTree(f.root()), f.a, PlayAction.AdjustLife(f.a, "1"), 100)
        for (action in listOf(PlayAction.AdjustLife(f.a, "1"), PlayAction.SetLife(f.a, "25"))) {
            val second = f.play(first, f.b, action, 101)
            val result = f.undo(second)
            assertEquals(first.currentNodeId, result.targetId)
            assertEquals("replayBlocked", result.reason)
            assertNull(result.tree)
            assertTrue(second.redoByAccount.isEmpty())
        }
    }

    @Test fun noSkippingBlockedOrRevealingLatestOwn() {
        val older = f.tap(GameplayTree(f.root()))
        val own = f.play(older, f.a, PlayAction.AdjustLife(f.a, "1"), 101)
        val blocked = f.play(own, f.b, PlayAction.AdjustLife(f.a, "1"), 102)
        assertEquals(own.currentNodeId, f.undo(blocked).targetId)
        assertEquals("replayBlocked", f.undo(blocked).reason)
        val node = own.nodes.getValue(own.currentNodeId)
        val revealing = own.copy(nodes = own.nodes + (node.id to node.copy(revealsInformation = true)))
        assertEquals("revealingTarget", f.undo(revealing).reason)
        assertEquals(node.id, f.undo(revealing).targetId)
        assertEquals("noCandidate", f.undo(older, f.b).reason)
    }

    @Test fun redoRequiresExactTargetInputEvenWithEmptySuffixAndKeepsBlockedEntry() {
        // R-06: B:P 20→21, A:Q independent tap, A:R 21→22.
        val p = f.play(GameplayTree(f.root()), f.b, PlayAction.AdjustLife(f.a, "1"), 100)
        val q = f.tap(p, n = 101)
        val r = f.play(q, f.a, PlayAction.AdjustLife(f.a, "1"), 102)
        val withoutR = assertNotNull(f.undo(r).tree)
        val withoutP = assertNotNull(f.undo(withoutR, f.b).tree)
        val result = f.redo(withoutP)
        assertEquals("targetTransitionBlocked", result.reason)
        assertEquals(r.currentNodeId, result.targetId)
        assertNull(result.tree)
        assertEquals(listOf(r.nodes.getValue(r.currentNodeId)), withoutP.redoByAccount[f.a]!!.map { it.mutation })
    }

    @Test fun wholeRetainedFutureBeyondInternalCurrentIsValidatedAndNotActivated() {
        val a = f.tap(GameplayTree(f.root()))
        val dependent = f.tap(a, f.b, value = false, n = 101)
        val internal = dependent.copy(currentNodeId = a.currentNodeId)
        assertEquals("replayBlocked", f.undo(internal).reason)
        val independent = f.tap(a, f.b, 12, n = 102).copy(currentNodeId = a.currentNodeId)
        val removed = assertNotNull(f.undo(independent).tree)
        assertEquals(f.root().root.id, removed.currentNodeId)
        assertEquals(independent.nodes.keys - a.currentNodeId, removed.nodes.keys)
        assertEquals(independent.nodes.keys.last(), removed.resolveCursor(independent.nodes.keys.last()))
        val restored = assertNotNull(f.redo(removed).tree)
        assertEquals(a.currentNodeId, restored.currentNodeId)
        assertEquals(independent.nodes, restored.nodes)
    }

    @Test fun forkAtTargetOrAnyFutureDescendantBlocksButNotStrictlyAfterFork() {
        val a = f.tap(GameplayTree(f.root()))
        val b = f.tap(a, f.b, 12, n = 101)
        val c = f.play(a, f.b, PlayAction.AdjustLife(f.b, "1"), 102)
        val fork = b.copy(nodes = b.nodes + c.nodes)
        assertEquals("forkBoundary", f.undo(fork).reason)
        val futureFork = fork.copy(currentNodeId = a.currentNodeId)
        assertEquals("forkBoundary", f.undo(futureFork).reason)
        val afterFork = f.play(fork, f.a, PlayAction.AdjustLife(f.a, "1"), 103)
        val removed = assertNotNull(f.undo(afterFork).tree)
        assertEquals(fork.nodes, removed.nodes)
        assertEquals(afterFork.nodes, assertNotNull(f.redo(removed).tree).nodes)
        // Removing the new interruption tail keeps the displaced sibling, with no autoactivation.
        val branch = fork.copy(currentNodeId = c.currentNodeId)
        val removedC = assertNotNull(f.undo(branch, f.b).tree)
        assertEquals(a.currentNodeId, removedC.currentNodeId)
        assertTrue(b.currentNodeId in removedC.nodes)
        assertEquals(branch.nodes, assertNotNull(f.redo(removedC, f.b).tree).nodes)
    }

    @Test fun laterForkCannotBeHiddenByCurrentPointerAndRemovedForkIsNotPermanentLock() {
        val a = f.tap(GameplayTree(f.root()))
        val future = f.tap(a, f.b, 12, n = 101)
        val left = f.play(future, f.b, PlayAction.AdjustLife(f.b, "1"), 102)
        val right = f.play(future, f.b, PlayAction.AdjustLife(f.b, "2"), 103)
        val fork = left.copy(nodes = left.nodes + right.nodes, currentNodeId = a.currentNodeId)
        assertEquals("suffixUnavailable", f.undo(fork).reason)
        val alternative = f.play(a, f.b, PlayAction.AdjustLife(f.b, "1"), 104)
        val two = future.copy(nodes = future.nodes + alternative.nodes, currentNodeId = alternative.currentNodeId)
        val one = assertNotNull(f.undo(two, f.b).tree)
        assertNotNull(f.undo(one).tree) // actual fork eliminated; replay the independent retained future
    }

    @Test fun allSuccessorsCountTowardNAndNoPartialEditOnOverflow() {
        val a = f.tap(GameplayTree(f.root()))
        var tree = a
        repeat(129) { tree = f.play(tree, f.b, PlayAction.AdjustLife(f.b, "1"), 200 + it) }
        assertEquals("suffixUnavailable", f.undo(tree).reason)
        assertEquals(130, tree.nodes.size)
        val atLimit = tree.copy(nodes = tree.nodes - tree.currentNodeId,
            currentNodeId = tree.nodes.getValue(tree.currentNodeId).parentId)
        assertNotNull(f.undo(atLimit).tree)
        val internal = atLimit.copy(currentNodeId = a.currentNodeId)
        assertNull(f.undo(internal, limits = SelectiveUndoLimits(suffixNodes = 127)).tree)
    }

    @Test fun targetPayloadMandatoryEffectsAndReconstructionHaveIndependentAtomicWorkLimits() {
        val tree = f.tap(GameplayTree(f.root()))
        for (limits in listOf(SelectiveUndoLimits(payloadBytes = 1), SelectiveUndoLimits(effectUnits = 0),
            SelectiveUndoLimits(workUnits = 1), SelectiveUndoLimits(metadataEntries = 0))) {
            assertEquals("workLimit", f.undo(tree, limits = limits).reason)
            assertEquals(1, tree.nodes.size)
            assertTrue(tree.redoByAccount.isEmpty())
        }
        val first = f.tap(tree, f.b, 12, n = 101)
        assertEquals("workLimit", f.undo(first, f.b, limits = SelectiveUndoLimits(loadNodes = 1)).reason)
        val node = tree.nodes.values.single()
        val large = node.copy(transition = GameplayTransition(List(32769) { node.transition.fields.single() }))
        val big = tree.copy(nodes = mapOf(node.id to large))
        assertEquals("workLimit", f.undo(big).reason) // zero suffix; no unchecked huge target
        val noTarget = assertNotNull(f.undo(tree).tree)
        assertEquals("workLimit", f.redo(noTarget, limits = SelectiveUndoLimits(effectUnits = 0)).reason)
        assertFailsWith<IllegalArgumentException> { SelectiveUndoLimits(suffixNodes = 129) }
    }

    @Test fun dependentEventReferenceBlocksRemovalUnrelatedLaterEventDoesNot() {
        val a = f.tap(GameplayTree(f.root()))
        val b = f.tap(a, f.b, 12, n = 101)
        val referenced = b.nodes.getValue(b.currentNodeId).copy(references = listOf(
            GameplayNodeReference(a.currentNodeId, a.nodes.getValue(a.currentNodeId).transition)))
        val tree = b.copy(nodes = b.nodes + (referenced.id to referenced))
        assertEquals("referenceBlocked", f.undo(tree).reason)
        assertNotNull(f.undo(b).tree)
    }

    @Test fun ownNewPlayClearsOwnQueueOnlyAndNoopRejectOpponentPlayPreserveIt() {
        val a = f.tap(GameplayTree(f.root()))
        val undone = assertNotNull(f.undo(a).tree)
        val noop = undone.play(f.a, f.command(GameAction.Play(PlayAction.SetTapped(f.id(9), false)), 101))
        assertEquals("noOp", noop.outcome); assertNull(noop.tree)
        val rejected = undone.play(f.a, f.command(GameAction.Play(PlayAction.SetTapped(f.id(999), true)), 102))
        assertEquals("missingReference", rejected.reason)
        val other = f.play(undone, f.b, PlayAction.AdjustLife(f.b, "1"), 103)
        assertEquals(undone.redoByAccount, other.redoByAccount)
        val restored = assertNotNull(f.redo(other).tree)
        assertEquals(a.currentNodeId, restored.nodes.getValue(other.currentNodeId).parentId)
        val newOwn = f.tap(other, card = 12, n = 104)
        assertTrue(newOwn.redoByAccount.isEmpty())
    }

    @Test fun missingRedoAnchorAndNextBlockedEntryAreNeverReplacedOrSkipped() {
        val a = f.tap(GameplayTree(f.root()), f.b)
        val b = f.tap(a, card = 12, n = 101)
        val removedB = assertNotNull(f.undo(b).tree)
        val removedA = assertNotNull(f.undo(removedB, f.b).tree)
        assertEquals("missingAnchor", f.redo(removedA).reason)
        assertEquals(b.currentNodeId, f.redo(removedA).targetId)
    }

    @Test fun typedWireHasNoCapturedTargetAndScopeAndAccessStayBound() {
        val tree = f.tap(GameplayTree(f.root()))
        for (action in listOf(GameAction.Undo, GameAction.Redo)) {
            val command = f.command(action, 900)
            assertTrue(GameWire.valid(command))
            assertEquals(command, GameWire.decodeClient(GameWire.json.encodeToString<GameClientMessage>(command)))
            assertFalse(GameWire.valid(command.copy(scope = command.scope.copy(preparationId = f.id(3)))))
            assertFalse(GameWire.valid(command.copy(continuation = Continuation(tree.currentNodeId, true))))
            assertEquals("inapplicable", SelectiveUndo.change(tree, f.a,
                command.copy(scope = command.scope.copy(playId = f.id(99)))).reason)
            assertEquals("accessDenied", SelectiveUndo.change(tree, f.id(99), command).reason)
        }
        val command = f.command(GameAction.Undo, 900)
        val captured = GameWire.json.encodeToString<GameClientMessage>(command).replace("\"type\":\"undo\"", "\"type\":\"undo\",\"nodeId\":\"${tree.currentNodeId}\"")
        assertFails { GameWire.decodeClient(captured) }
    }
}
