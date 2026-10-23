package org.asyncttwiz.protocol

import kotlin.test.*

class SelectiveUndoTest {
    private val f = UndoFixture

    @Test fun revealingOpponentHandMoveIsStillBlockedAndCannotBeSkipped() {
        val root = gameplayFixture()
        val hand = f.play(GameplayTree(root), f.a, PlayAction.MoveCard(gameplayId(10),
            MoveSource(gameplayId(20), edge = MoveSourceEdge.TOP),
            MoveDestination(gameplayId(21), MoveInsertion.UNORDERED)), 100)
        val move = PlayAction.MoveCard(gameplayId(10), MoveSource(gameplayId(21)),
            MoveDestination(gameplayId(22), MoveInsertion.EMPTY, BoardPosition(0, 0)))
        val opponent = f.play(hand, f.b, move, 101)
        val blocked = f.undo(opponent, f.b)
        assertEquals("revealingTarget", blocked.reason)
        assertEquals(opponent.currentNodeId, blocked.targetId)
        assertNull(blocked.tree)
        // The same physical move by the Hand's holder reveals nothing new to that actor.
        val own = f.play(hand, f.a, move, 102)
        val removed = assertNotNull(f.undo(own).tree)
        assertEquals(hand.stateAt(hand.currentNodeId), removed.stateAt(removed.currentNodeId))
        assertEquals(own.nodes.getValue(own.currentNodeId), removed.redoByAccount[f.a]!!.single().mutation)
        assertEquals("revealingTarget", f.undo(removed).reason)
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
        assertEquals("revealingTarget", f.undo(tree).reason) // Never skip the last draw.
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
        assertEquals("revealingTarget", HistoryContinuation.change(drawn, f.a, f.command(GameAction.Undo, request++)).reason)
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
        assertEquals("revealingTarget", f.undo(revealing).reason)
        assertEquals(node.id, f.undo(revealing).targetId)
        assertEquals("noCandidate", f.undo(older, f.b).reason)
    }

    @Test fun ownHandPlayExceptionDoesNotPermitRevealingAnOpponentsHand() {
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
        assertEquals("revealingTarget", f.undo(opponent, f.b).reason)
        assertEquals(opponent.currentNodeId, f.undo(opponent, f.b).targetId)
    }

    @Test fun payloadSizeIsPerValueButTotalReconstructionWorkRemainsBounded() {
        assertNotNull(boundedHistory(SelectiveUndoLimits(payloadBytes = 3000)) { work ->
            repeat(100) { work.value(GameplayTransition.serializer(), GameplayTransition()) }
            true
        })
        assertNull(boundedHistory(SelectiveUndoLimits(workUnits = 100)) { work ->
            repeat(100) { work.value(GameplayTransition.serializer(), GameplayTransition()) }
            true
        })
        assertNull(boundedHistory(SelectiveUndoLimits(payloadBytes = 3000)) { work ->
            work.value(GameplayFieldChange.serializer(), GameplayFieldChange(f.a, GameplayField.NOTE, "", "x".repeat(1000)))
            true
        })
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
