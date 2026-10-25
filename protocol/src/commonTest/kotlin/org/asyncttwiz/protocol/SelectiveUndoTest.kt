package org.asyncttwiz.protocol

import kotlin.test.*

class SelectiveUndoTest {
    private val f = UndoFixture

    private fun handStackRoot() = f.root().let { root -> root.copy(board = root.board.copy(
        cards = root.board.cards.map { it.copy(owner = f.a) }, containers = listOf(
            root.board.containers.single().copy(zone = PlayZone.HAND), PlayContainer(f.id(14), PlayZone.STACK)))) }

    private fun cold(tree: GameplayTree) = GameWire.json.decodeFromString(GameplayTree.serializer(),
        GameWire.json.encodeToString(GameplayTree.serializer(), tree))

    @Test fun handProjectionsKeepPerMemberDisclosureEligibilityAndReplayOrderWithOrWithoutGrouping() {
        for (grouped in listOf(false, true)) {
            val initial = GameplayTree(handStackRoot())
            val stages = mutableListOf(initial)
            // The final projection shares a source: it reveals nothing new, unlike the first two.
            for ((index, card) in listOf(9, 12, 9).withIndex()) {
                val tree = stages.last()
                val action = PlayAction.Stack(StackAction.AddEffect(f.id(card),
                    GameplayRules.stackDigest(tree.stateAt(tree.currentNodeId)!!)))
                val change = HistoryContinuation.change(tree, f.a, f.command(GameAction.Play(action), 100 + index)
                    .copy(invocationId = if (grouped) f.id(800) else null))
                stages += assertNotNull(change.tree, change.reason)
            }
            val projected = stages.last()
            val mutations = projected.nodes.values.toList()
            assertEquals(listOf(true, true, false), mutations.map { it.revealsInformation })
            assertEquals(listOf(listOf(f.b), listOf(f.b)), mutations.flatMap { it.transition.disclosures }.map { it.viewers })
            var tree = cold(projected)
            val presses = if (grouped) 1 else mutations.size
            repeat(presses) { index ->
                val change = f.undo(tree)
                tree = cold(assertNotNull(change.tree, change.reason))
                val expected = if (grouped) initial else stages[stages.lastIndex - index - 1]
                assertEquals(expected.nodes, tree.nodes)
                assertEquals(expected.stateAt(expected.currentNodeId)!!.board, tree.stateAt(tree.currentNodeId)!!.board)
            }
            // Undo stores exact disclosures, newest first; Redo must restore oldest first.
            assertEquals(mutations.asReversed(), tree.redoByAccount[f.a]!!.map { it.mutation })
            repeat(presses) { index ->
                val change = f.redo(tree)
                tree = cold(assertNotNull(change.tree, change.reason))
                val expected = if (grouped) projected else stages[index + 1]
                assertEquals(expected.nodes, tree.nodes)
                assertEquals(expected.stateAt(expected.currentNodeId)!!.board, tree.stateAt(tree.currentNodeId)!!.board)
            }
            assertTrue(tree.redoByAccount.isEmpty())
            assertEquals(projected.currentNodeId, tree.currentNodeId)
        }
    }

    @Test fun groupedProjectionUndoIncludesEarlierOpponentHandDisclosure() {
        var tree = GameplayTree(handStackRoot())
        val stages = mutableListOf<GameplayTree>()
        repeat(2) { index ->
            val action = PlayAction.Stack(StackAction.AddEffect(f.id(9),
                GameplayRules.stackDigest(tree.stateAt(tree.currentNodeId)!!)))
            val change = HistoryContinuation.change(tree, f.b, f.command(GameAction.Play(action), 100 + index)
                .copy(invocationId = f.id(800)))
            tree = assertNotNull(change.tree, change.reason)
            stages += tree
        }
        assertEquals(listOf(true, false), tree.nodes.values.map { it.revealsInformation })
        val removed = assertNotNull(f.undo(cold(tree), f.b).tree)
        assertTrue(removed.nodes.isEmpty())
        assertEquals(tree.nodes, assertNotNull(f.redo(removed, f.b).tree).nodes)
        assertEquals(2, tree.nodes.size)
        assertTrue(tree.redoByAccount.isEmpty())
    }

    @Test fun consecutiveInvocationConstituentsUndoAndRedoTogetherWithoutChangingNodeGranularity() {
        val initial = GameplayTree(f.root())
        val invocation = f.id(800)
        fun play(tree: GameplayTree, card: Int, n: Int) = assertNotNull(HistoryContinuation.change(tree, f.a,
            f.command(GameAction.Play(PlayAction.SetTapped(f.id(card), true)), n).copy(invocationId = invocation)).tree)
        val first = play(initial, 9, 100)
        val second = play(first, 12, 101)
        assertEquals(2, second.nodes.size)
        val undone = assertNotNull(HistoryContinuation.change(second, f.a, f.command(GameAction.Undo, 900)).tree)
        assertEquals(initial.initial.board, undone.stateAt(undone.currentNodeId)!!.board)
        assertEquals(2, undone.redoByAccount[f.a]!!.size)
        val redone = assertNotNull(HistoryContinuation.change(undone, f.a, f.command(GameAction.Redo, 901)).tree)
        assertEquals(second.nodes, redone.nodes)
        assertEquals(second.currentNodeId, redone.currentNodeId)
        assertEquals(second.branchHeads, redone.branchHeads)
    }

    @Test fun blockedInvocationPublishesNoPartialUndoAndDifferentInvocationsRemainSeparate() {
        val initial = GameplayTree(f.root())
        val invocation = f.id(800)
        val first = assertNotNull(HistoryContinuation.change(initial, f.a,
            f.command(GameAction.Play(PlayAction.SetTapped(f.id(9), true)), 100).copy(invocationId = invocation)).tree)
        val second = assertNotNull(HistoryContinuation.change(first, f.a,
            f.command(GameAction.Play(PlayAction.SetTapped(f.id(12), true)), 101).copy(invocationId = invocation)).tree)
        val original = second.nodes.getValue(first.currentNodeId)
        val corrupted = second.copy(nodes = second.nodes + (first.currentNodeId to
            original.copy(transition = original.transition.copy(delta = original.transition.delta!!.copy(seal = "invalid")))))
        val blocked = f.undo(corrupted)
        assertEquals("historyUnavailable", blocked.reason)
        assertNull(blocked.tree)
        assertEquals(2, corrupted.nodes.size)
        val separate = f.play(second, f.a, PlayAction.AdjustLife(f.a, "1"), 102)
        assertEquals(second.nodes, assertNotNull(f.undo(separate).tree).nodes)
    }

    @Test fun revealingOpponentHandMoveIsReversible() {
        val root = gameplayFixture()
        val hand = f.play(GameplayTree(root), f.a, PlayAction.MoveCard(gameplayId(10),
            MoveSource(gameplayId(20), edge = MoveSourceEdge.TOP),
            MoveDestination(gameplayId(21), MoveInsertion.UNORDERED)), 100)
        val move = PlayAction.MoveCard(gameplayId(10), MoveSource(gameplayId(21)),
            MoveDestination(gameplayId(22), MoveInsertion.EMPTY, BoardPosition(0, 0)))
        val opponent = f.play(hand, f.b, move, 101)
        val opponentRemoved = assertNotNull(f.undo(opponent, f.b).tree)
        assertEquals(hand.stateAt(hand.currentNodeId), opponentRemoved.stateAt(opponentRemoved.currentNodeId))
        assertEquals(opponent.nodes, assertNotNull(f.redo(opponentRemoved, f.b).tree).nodes)
        // The same physical move by the Hand's holder reveals nothing new to that actor.
        val own = f.play(hand, f.a, move, 102)
        val removed = assertNotNull(f.undo(own).tree)
        assertEquals(hand.stateAt(hand.currentNodeId), removed.stateAt(removed.currentNodeId))
        assertEquals(own.nodes.getValue(own.currentNodeId), removed.redoByAccount[f.a]!!.single().mutation)
        assertNotNull(f.undo(removed).tree) // Own Library draws are reproducible and reversible.
    }

    @Test fun realDeckMulliganThenMoveTapUndoUndoRedo() {
        val definition = CardDefinition("Synthetic", listOf(DefinitionFace(f.id(40), "Front", corner = "2/3")))
        val submitted = SubmittedDefinition(DeckJson.digest(definition), definition)
        val cards = listOf(f.a, f.b).flatMapIndexed { player, actor ->
            List(60) { index -> PlayCard(f.id(1000 + player * 60 + index), submitted.digest, actor,
                f.id(20 + player * 3), f.id(40), corner = "2/3") }
        }
        val containers = listOf(f.a, f.b).flatMapIndexed { player, actor ->
            listOf(PlayZone.LIBRARY, PlayZone.HAND, PlayZone.BATTLEFIELD).mapIndexed { zone, kind ->
                PlayContainer(f.id(20 + player * 3 + zone), kind, actor,
                    if (kind == PlayZone.LIBRARY) cards.filter { it.owner == actor }.map { it.id } else emptyList())
            }
        }
        val root = f.root().copy(board = BoardState(listOf(PlayPlayer(f.a), PlayPlayer(f.b)), cards, containers,
            TurnState(f.a)), definitions = listOf(submitted))
        var tree = GameplayTree(root)
        var request = 100
        fun play(action: PlayAction) { tree = f.play(tree, f.a, action, request++) }
        fun current() = assertNotNull(tree.stateAt(tree.currentNodeId))
        // Exercise both an empty starting hand and a genuine Return Hand prefix.
        repeat(2) {
            val plan = LibraryActions.mulligan(current().board, f.id(20), f.id(21),
                current().board.containers.single { it.id == f.id(21) }.cards, RandomV1.encodeSeed(ByteArray(32)))
            plan.prefix().forEach(::play)
            LibraryActions.draw(current().board, f.id(20), f.id(21), plan.drawCount).forEach(::play)
        }
        val drawn = tree
        val cardId = current().board.containers.single { it.id == f.id(21) }.cards.first()
        play(PlayAction.MoveCard(cardId, MoveSource(f.id(21)),
            MoveDestination(f.id(22), MoveInsertion.EMPTY, BoardPosition(1024, 0))))
        val moved = tree
        play(PlayAction.SetTapped(cardId, true))
        val tapped = tree
        // SQL/transfer decoding loses disposable materializations: eligibility must also work cold.
        val undoTap = f.undo(tree.copy())
        assertEquals(tapped.currentNodeId, undoTap.targetId, undoTap.reason)
        tree = assertNotNull(undoTap.tree, undoTap.reason)
        assertEquals(moved.stateAt(moved.currentNodeId)?.board, current().board)
        val undoMove = f.undo(tree.copy())
        assertEquals(moved.currentNodeId, undoMove.targetId, undoMove.reason)
        tree = assertNotNull(undoMove.tree, undoMove.reason)
        assertEquals(drawn.stateAt(drawn.currentNodeId)?.board, current().board)
        assertNotNull(f.undo(tree).tree) // The last own draw is now reversible too.
        val redoMove = f.redo(tree.copy())
        assertEquals(moved.currentNodeId, redoMove.targetId, redoMove.reason)
        tree = assertNotNull(redoMove.tree, redoMove.reason)
        assertEquals(moved.stateAt(moved.currentNodeId)?.board, current().board)
        tree = assertNotNull(f.redo(tree.copy()).tree)
        assertEquals(tapped.stateAt(tapped.currentNodeId)?.board, current().board)
    }

    @Test fun mulliganWithRealSizedDecksThenMoveTapUndoUndoRedo() {
        val definition = CardDefinition("Synthetic card", listOf(DefinitionFace(f.id(11), "Face", corner = "3/5")))
        val submitted = SubmittedDefinition(DeckJson.digest(definition), definition)
        val cards = (0 until 120).map { index -> PlayCard(f.id(1000 + index), submitted.digest,
            if (index < 60) f.a else f.b, f.id(if (index < 60) 20 else 21), f.id(11), covered = true) }
        val root = f.root().copy(board = BoardState(listOf(PlayPlayer(f.a), PlayPlayer(f.b)), cards,
            listOf(PlayContainer(f.id(20), PlayZone.LIBRARY, f.a, cards.take(60).map { it.id }),
                PlayContainer(f.id(21), PlayZone.LIBRARY, f.b, cards.drop(60).map { it.id }),
                PlayContainer(f.id(22), PlayZone.HAND, f.a), PlayContainer(f.id(23), PlayZone.BATTLEFIELD, f.a)),
            TurnState(f.a)), definitions = listOf(submitted))
        var tree = GameplayTree(root)
        var request = 2000
        fun play(action: PlayAction) {
            val change = HistoryContinuation.change(tree, f.a, f.command(GameAction.Play(action), request++))
            tree = assertNotNull(change.tree, change.reason)
        }
        val plan = LibraryActions.mulligan(root.board, f.id(20), f.id(22), emptyList(),
            RandomV1.encodeSeed(ByteArray(32)), count = 7)
        plan.prefix().forEach(::play)
        LibraryActions.draw(tree.stateAt(tree.currentNodeId)!!.board, plan.libraryId, plan.handId, plan.drawCount).forEach(::play)
        val drawn = tree
        val card = tree.stateAt(tree.currentNodeId)!!.board.containers.single { it.id == f.id(22) }.cards.first()
        play(PlayAction.MoveCard(card, MoveSource(f.id(22)), MoveDestination(f.id(23), MoveInsertion.EMPTY, BoardPosition(0, 0))))
        val moved = tree
        play(PlayAction.SetTapped(card, true))
        val tapped = tree.currentNodeId
        fun control(action: GameAction): GameplayTreeChange {
            val change = HistoryContinuation.change(tree, f.a, f.command(action, request++))
            tree = assertNotNull(change.tree, change.reason)
            return change
        }
        assertEquals(tapped, control(GameAction.Undo).targetId)
        assertEquals(moved.stateAt(moved.currentNodeId), tree.stateAt(tree.currentNodeId))
        assertEquals(moved.currentNodeId, control(GameAction.Undo).targetId)
        assertEquals(drawn.stateAt(drawn.currentNodeId), tree.stateAt(tree.currentNodeId))
        assertEquals(moved.currentNodeId, control(GameAction.Redo).targetId)
        assertEquals(moved.stateAt(moved.currentNodeId), tree.stateAt(tree.currentNodeId))
        assertNotNull(HistoryContinuation.change(drawn, f.a, f.command(GameAction.Undo, request++)).tree)
    }

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
        assertNotNull(f.undo(revealing).tree)
        assertEquals(node.id, f.undo(revealing).targetId)
        assertEquals("noCandidate", f.undo(older, f.b).reason)
    }

    @Test fun disclosureDataIsPreservedRegardlessOfWhoseHandWasRevealed() {
        val root = f.root().let { it.copy(board = it.board.copy(containers = listOf(
            it.board.containers.single().copy(zone = PlayZone.HAND),
            PlayContainer(f.id(14), PlayZone.BATTLEFIELD, f.a)))) }
        val action = PlayAction.MoveCard(f.id(9), MoveSource(f.id(10)),
            MoveDestination(f.id(14), MoveInsertion.EMPTY, BoardPosition(0, 0)))
        val own = f.play(GameplayTree(root), f.a, action, 100)
        val mutation = own.nodes.getValue(own.currentNodeId)
        assertTrue(mutation.revealsInformation)
        assertEquals(listOf(f.b), mutation.transition.disclosures.single().viewers)
        val undone = assertNotNull(f.undo(own).tree)
        assertEquals(mutation, undone.redoByAccount[f.a]!!.single().mutation)
        assertEquals(own.nodes, assertNotNull(f.redo(undone).tree).nodes)
        val opponent = f.play(GameplayTree(root), f.b, action, 101)
        assertNotNull(f.undo(opponent, f.b).tree)
        assertEquals(opponent.currentNodeId, f.undo(opponent, f.b).targetId)
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
        repeat(51) { tree = f.play(tree, f.b, PlayAction.AdjustLife(f.b, "1"), 200 + it) }
        assertEquals("tooFarBack", f.undo(tree).reason)
        assertEquals(52, tree.nodes.size)
        val atLimit = tree.copy(nodes = tree.nodes - tree.currentNodeId,
            currentNodeId = tree.nodes.getValue(tree.currentNodeId).parentId)
        assertNotNull(f.undo(atLimit).tree)
        val internal = tree.copy(currentNodeId = a.currentNodeId)
        assertEquals("tooFarBack", f.undo(internal).reason)
    }

    @Test fun largeUnrelatedPayloadDoesNotPreventUndoOrRedo() {
        val root = f.root().let { it.copy(board = it.board.copy(cards = it.board.cards.map { card ->
            card.copy(note = "x".repeat(16000)) })) }
        val tree = f.tap(GameplayTree(root))
        val removed = assertNotNull(f.undo(tree).tree)
        assertEquals(root.board, removed.stateAt(removed.currentNodeId)!!.board)
        assertEquals(tree.nodes, assertNotNull(f.redo(removed).tree).nodes)
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
