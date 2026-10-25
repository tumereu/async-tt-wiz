package org.asyncttwiz.protocol

import kotlin.test.*

class LibraryKnowledgeTest {
    private fun id(n: Int) = "00000000-0000-4000-8000-${n.toString().padStart(12, '0')}"
    private val a = id(7)
    private val b = id(8)
    private val seed = RandomV1.encodeSeed(ByteArray(32) { it.toByte() })
    private var request = 10000
    private fun root(secret: String? = seed): PlayState {
        val definition = CardDefinition("Opt fixture", listOf(DefinitionFace(id(40), "Front")))
        val digest = DeckJson.digest(definition)
        val cards = listOf(a, b).flatMapIndexed { p, owner -> List(20) { n ->
            PlayCard(id(1000 + p * 20 + n), digest, owner, id(20 + p * 5), id(40))
        } }
        return PlayState(id(1), id(2), id(3), id(4), PlayRoot(id(5), id(6)), id(5),
            BoardState(listOf(PlayPlayer(a), PlayPlayer(b)), cards,
                listOf(a, b).flatMapIndexed { p, owner -> listOf(PlayZone.LIBRARY, PlayZone.HAND,
                    PlayZone.GRAVEYARD, PlayZone.BATTLEFIELD, PlayZone.EXILE).mapIndexed { z, zone ->
                    PlayContainer(id(20 + p * 5 + z), zone, owner,
                        if (zone == PlayZone.LIBRARY) cards.filter { it.owner == owner }.map { it.id } else emptyList())
                } } + PlayContainer(id(30), PlayZone.STACK), TurnState(a)),
            listOf(SubmittedDefinition(digest, definition)), emptyList(), InitialStarter(StarterChoice(a), a), librarySecret = secret)
    }
    private fun state(tree: GameplayTree) = assertNotNull(tree.stateAt(tree.currentNodeId))
    private fun library(tree: GameplayTree, owner: String = a) = state(tree).board.containers.single {
        it.zone == PlayZone.LIBRARY && it.accountId == owner }
    private fun command(tree: GameplayTree, action: GameAction, target: String? = null) = GameCommand(id(request++),
        GameScope(tree.initial.gameId, playId = tree.initial.id), action, id(9), "1",
        continuation = target?.let { Continuation(it, true) })
    private fun play(tree: GameplayTree, action: PlayAction, actor: String = a, target: String? = null): GameplayTree {
        val before = state(tree)
        val boundary = assertNotNull(tree.stateAt(target ?: tree.currentNodeId))
        val change = HistoryContinuation.change(tree, actor, command(tree, GameAction.Play(action), target))
        val next = assertNotNull(change.tree, "$action: ${change.reason ?: change.outcome}")
        val mutation = next.nodes.getValue(next.currentNodeId)
        val after = state(next)
        assertEquals(boundary, GameplayHistory.unapply(after, mutation))
        assertEquals(after, GameplayHistory.apply(boundary, mutation))
        assertEquals(before, tree.stateAt(tree.currentNodeId))
        for (container in after.board.containers.filter { it.zone == PlayZone.LIBRARY }) {
            assertEquals(container.knownTop + container.unknownMiddle + container.knownBottom, container.cards)
            assertEquals(container.cards.size, container.cards.distinct().size)
        }
        return next
    }
    private fun control(tree: GameplayTree, action: GameAction, actor: String = a): GameplayTree {
        val change = HistoryContinuation.change(tree, actor, command(tree, action))
        return assertNotNull(change.tree, change.reason)
    }
    private fun place(tree: GameplayTree, card: String, insertion: MoveInsertion, owner: String = a): GameplayTree {
        val source = state(tree).board.cards.single { it.id == card }
        return play(tree, PlayAction.MoveCard(card, MoveSource(source.containerId, source.placement),
            MoveDestination(library(tree, owner).id, insertion)), owner)
    }
    private fun draw(tree: GameplayTree, owner: String = a): GameplayTree {
        val board = state(tree).board
        val hand = board.containers.single { it.zone == PlayZone.HAND && it.accountId == owner }
        return play(tree, LibraryActions.draw(board, library(tree, owner).id, hand.id, 1).single(), owner)
    }

    @Test fun topAndBottomOperationsConsumeFixedEdgesThenUnknownMiddle() {
        for (known in listOf(false, true)) for (end in listOf(ContentsSelection.TOP, ContentsSelection.BOTTOM)) {
            var tree = GameplayTree(root())
            if (known) {
                val members = library(tree).cards
                tree = place(tree, members[3], MoveInsertion.TOP)
                tree = place(tree, members[8], MoveInsertion.BOTTOM)
            }
            val before = library(tree)
            val expected = LibraryRules.slice(before.cards, end, 3)
            for (reveal in listOf(false, true)) {
                val shown = play(tree, PlayAction.ShowContents(before.id, end, 3, reveal))
                assertEquals(expected, state(shown).board.contentsDisplays.single().cardIds)
                assertEquals(before, library(shown)) // Looking doesn't explicitly place anything.
                assertEquals(state(tree).board, state(control(shown, GameAction.Undo)).board)
                assertEquals(state(shown).board, state(control(control(shown, GameAction.Undo), GameAction.Redo)).board)
            }
            for (zone in listOf(PlayZone.HAND, PlayZone.GRAVEYARD, PlayZone.EXILE)) {
                val destination = state(tree).board.containers.single { it.zone == zone && it.accountId == a }
                var moved = tree
                LibraryActions.moveSlice(state(tree).board, before.id, end, 3,
                    MoveDestination(destination.id, MoveInsertion.UNORDERED)).forEach { moved = play(moved, it) }
                assertEquals(before.cards - expected.toSet(), library(moved).cards)
                assertTrue(expected.none { it in library(moved).knownTop || it in library(moved).knownBottom })
            }
        }
    }

    @Test fun everyInsertionPathFixesKnownPositionsIncludingAnAlreadyTopCard() {
        for (insertion in listOf(MoveInsertion.TOP, MoveInsertion.BOTTOM)) {
            var tree = draw(GameplayTree(root()))
            val handCard = state(tree).board.containers.single { it.id == id(21) }.cards.single()
            tree = place(tree, handCard, insertion)
            assertEquals(listOf(handCard), if (insertion == MoveInsertion.TOP) library(tree).knownTop else library(tree).knownBottom)
            val other = library(tree).unknownMiddle[2]
            tree = play(tree, PlayAction.MoveCard(other, MoveSource(id(20)),
                MoveDestination(id(23), MoveInsertion.EMPTY, BoardPosition(0, 0))))
            tree = place(tree, other, insertion)
            assertTrue(other in if (insertion == MoveInsertion.TOP) library(tree).knownTop else library(tree).knownBottom)
            val ids = library(tree).unknownMiddle.take(3)
            LibraryActions.moveCaptured(state(tree).board, ids, MoveDestination(id(20), insertion)).forEach { tree = play(tree, it) }
            assertTrue(ids.all { it in if (insertion == MoveInsertion.TOP) library(tree).knownTop else library(tree).knownBottom })
        }
        val tree = GameplayTree(root())
        val top = library(tree).cards.first()
        assertEquals(listOf(top), library(place(tree, top, MoveInsertion.TOP)).knownTop)
    }

    @Test fun panelTopBottomRandomRestAndRevealTopFixBlocksAndShuffleSearchMulliganClearThem() {
        for (rest in ContentsRest.entries) {
            var tree = GameplayTree(root())
            tree = place(tree, library(tree).cards.last(), MoveInsertion.TOP)
            tree = place(tree, library(tree).unknownMiddle.last(), MoveInsertion.BOTTOM)
            tree = play(tree, PlayAction.ShowContents(id(20), ContentsSelection.TOP, 4))
            val panel = state(tree).board.contentsDisplays.single()
            val ids = panel.cardIds
            tree = play(tree, PlayAction.RevealContentsCard(panel.id, ids.first()))
            tree = play(tree, PlayAction.DismissContents(panel.id, rest, seed))
            when (rest) {
                ContentsRest.TOP -> assertEquals(ids, library(tree).knownTop)
                ContentsRest.BOTTOM -> assertEquals(ids, library(tree).knownBottom.takeLast(4))
                ContentsRest.BOTTOM_RANDOM -> assertEquals(RandomV1.shuffle(ids, seed), library(tree).knownBottom.takeLast(4))
                ContentsRest.SHUFFLE -> { assertTrue(library(tree).knownTop.isEmpty()); assertTrue(library(tree).knownBottom.isEmpty()) }
                ContentsRest.GRAVEYARD, ContentsRest.EXILE -> assertTrue(ids.none { it in library(tree).cards })
            }
        }
        var tree = GameplayTree(root())
        tree = place(tree, library(tree).cards.first(), MoveInsertion.TOP)
        tree = play(tree, PlayAction.ShowContents(id(20), ContentsSelection.TOP, 2, reveal = true))
        val panel = state(tree).board.contentsDisplays.single()
        val card = panel.cardIds.last()
        LibraryActions.moveCaptured(state(tree).board, listOf(card), MoveDestination(id(20), MoveInsertion.TOP))
            .forEach { tree = play(tree, it) }
        assertEquals(card, library(tree).knownTop.first())
        assertTrue(state(tree).board.cards.single { it.id == card }.knownToAll)
        tree = play(tree, PlayAction.DismissContents(panel.id, ContentsRest.BOTTOM, seed))
        val fixed = tree
        for (search in listOf(false, true)) {
            tree = fixed
            if (search) {
                tree = play(tree, PlayAction.ShowContents(id(20), ContentsSelection.ALL))
                tree = play(tree, PlayAction.DismissContents(state(tree).board.contentsDisplays.single().id, ContentsRest.TOP, seed))
            } else tree = play(tree, PlayAction.ShuffleLibrary(id(20), seed))
            assertTrue(library(tree).knownTop.isEmpty()); assertTrue(library(tree).knownBottom.isEmpty())
            assertTrue(state(tree).board.cards.none { it.knownToAll })
        }
        tree = draw(fixed)
        val hand = state(tree).board.containers.single { it.id == id(21) }
        val plan = LibraryActions.mulligan(state(tree).board, id(20), hand.id, hand.cards, seed)
        plan.prefix().forEach { tree = play(tree, it) }
        assertTrue(library(tree).knownTop.isEmpty()); assertTrue(library(tree).knownBottom.isEmpty())
    }

    @Test fun ownDrawUndoOtherActionsDrawAndScryUndoRedoAreReproducible() {
        val tree = GameplayTree(root())
        val drawn = draw(tree)
        val card = state(drawn).board.containers.single { it.id == id(21) }.cards.single()
        val undone = control(drawn, GameAction.Undo)
        assertEquals(state(tree).board, state(undone).board)
        assertEquals(card, state(draw(undone)).board.containers.single { it.id == id(21) }.cards.single())
        val other = play(undone, PlayAction.AdjustLife(b, "1"), b)
        assertEquals(card, state(draw(other)).board.containers.single { it.id == id(21) }.cards.single())
        var scry = play(tree, PlayAction.ShowContents(id(20), ContentsSelection.TOP, 2))
        val display = state(scry).board.contentsDisplays.single()
        scry = play(scry, PlayAction.ReorderContents(display.id, display.cardIds.last(), display.cardIds.first()))
        scry = play(scry, PlayAction.DismissContents(display.id))
        val restored = control(control(scry, GameAction.Undo), GameAction.Redo)
        assertEquals(state(scry), state(restored))
        assertEquals(display.cardIds.reversed(), library(restored).knownTop)
        // The opponent cannot remove A's look; their latest own candidate is still selected normally.
        val look = play(tree, PlayAction.ShowContents(id(20), ContentsSelection.TOP))
        assertEquals("noCandidate", HistoryContinuation.change(look, b, command(look, GameAction.Undo)).reason)
    }

    @Test fun optBranchReseedsBothUnknownMiddlesPreservesKnownEdgesAndRestoresEveryTraversalExactly() {
        var tree = GameplayTree(root())
        for (owner in listOf(a, b)) {
            tree = place(tree, library(tree, owner).cards[3], MoveInsertion.TOP, owner)
            tree = place(tree, library(tree, owner).cards[8], MoveInsertion.BOTTOM, owner)
        }
        // Cast Opt, then consume the known top before looking at the unknown middle.
        tree = play(tree, PlayAction.AdjustLife(a, "-1"))
        tree = draw(tree)
        val cast = tree.currentNodeId
        val atCast = state(tree)
        tree = play(tree, PlayAction.ShowContents(id(20), ContentsSelection.TOP))
        val panel = state(tree).board.contentsDisplays.single()
        val x = panel.cardIds.single()
        tree = play(tree, PlayAction.DismissContents(panel.id, ContentsRest.TOP))
        assertEquals(listOf(x), library(tree).knownTop)
        tree = draw(tree)
        val original = state(tree)
        val oldHead = tree.currentNodeId
        val forked = play(tree, PlayAction.AdjustLife(b, "-1"), b, cast)
        val fork = forked.nodes.getValue(forked.currentNodeId)
        val reseeds = fork.transition.delta!!.libraryReseeds
        assertEquals(2, reseeds.size)
        assertEquals(atCast, forked.stateAt(cast))
        for (owner in listOf(a, b)) {
            val old = atCast.board.containers.single { it.zone == PlayZone.LIBRARY && it.accountId == owner }
            val next = library(forked, owner)
            assertEquals(old.knownTop, next.knownTop); assertEquals(old.knownBottom, next.knownBottom)
            assertEquals(old.unknownMiddle.toSet(), next.unknownMiddle.toSet())
            assertNotEquals(old.unknownMiddle, next.unknownMiddle)
        }
        assertNotEquals(x, library(forked).cards.first()) // Fixed seed/vector, not a probabilistic assertion.
        repeat(3) {
            assertEquals(original, forked.prepareReview(oldHead))
            assertEquals(atCast, forked.prepareReview(cast))
            assertEquals(state(forked), forked.prepareReview(fork.id))
        }
        val removed = control(forked, GameAction.Undo, b)
        assertEquals(atCast, state(removed))
        val again = play(removed, PlayAction.AdjustLife(b, "-1"), b)
        assertEquals(state(forked).board, state(again).board)
        val redone = control(removed, GameAction.Redo, b)
        assertEquals(state(forked), state(redone))
        val samePlayer = play(forked, PlayAction.AdjustLife(b, "-1"), b, cast)
        assertEquals(reseeds, samePlayer.nodes.getValue(samePlayer.currentNodeId).transition.delta!!.libraryReseeds)
        val differentPlayer = play(forked, PlayAction.AdjustLife(b, "-1"), a, cast)
        assertTrue(differentPlayer.nodes.getValue(differentPlayer.currentNodeId).transition.delta!!.libraryReseeds.zip(reseeds)
            .all { (left, right) -> left.to != right.to })
        val decoded = GameWire.json.decodeFromString(GameplayTree.serializer(), GameWire.json.encodeToString(GameplayTree.serializer(), forked))
        assertEquals(state(forked), decoded.stateAt(decoded.currentNodeId))
        assertEquals(original, decoded.stateAt(oldHead))
    }

    @Test fun forkDrawUsesReseededTopAndRepeatedForkUndoDrawNeverRerollsIncludingLegacyGames() {
        for (secret in listOf(seed, null)) {
            var tree = play(GameplayTree(root(secret)), PlayAction.AdjustLife(b, "1"), b)
            val target = tree.initial.root.id
            val action = LibraryActions.draw(tree.initial.board, id(20), id(21), 1).single()
            val forked = play(tree, action, a, target)
            val first = state(forked).board.containers.single { it.id == id(21) }.cards.single()
            assertEquals(forked.nodes.getValue(forked.currentNodeId).transition.delta!!.libraryReseeds.first().to.first(), first)
            tree = control(forked, GameAction.Undo)
            val repeated = play(tree, action)
            assertEquals(first, state(repeated).board.containers.single { it.id == id(21) }.cards.single())
            // Edge-intent constituents captured before the fork must consume consecutive reseeded cards.
            val plan = LibraryActions.draw(forked.initial.board, id(20), id(21), 4)
                .map { it.copy(source = it.source.copy(takeFromEdge = true)) }
            var batch = play(forked, plan.first(), a, target)
            val order = batch.nodes.getValue(batch.currentNodeId).transition.delta!!.libraryReseeds.first().to
            plan.drop(1).forEach { batch = play(batch, it) }
            assertEquals(order.take(4), state(batch).board.containers.single { it.id == id(21) }.cards)
        }
    }

    @Test fun reseedAlgorithmHasStableDomainSeparatedGoldenVectorAndLegacyWireDefaultsStayAbsent() {
        val state = root()
        assertEquals("gAA_601XteHDpTgtPQkj6AvrGXCTHsGtRaDEFwggtdg", LibraryKnowledge.seed(state, a, id(20)))
        assertEquals(listOf(1006, 1010, 1008, 1000, 1001, 1002, 1004, 1014, 1019, 1011,
            1017, 1018, 1012, 1007, 1003, 1009, 1005, 1016, 1015, 1013).map(::id),
            LibraryKnowledge.reseeds(state, a).first().to)
        assertNotEquals(LibraryKnowledge.seed(state, a, id(20)), LibraryKnowledge.seed(state, b, id(20)))
        assertNotEquals(LibraryKnowledge.seed(state, a, id(20)), LibraryKnowledge.seed(state, a, id(25)))
        assertNotEquals(RandomV1.shuffle(state.board.containers.first().cards, seed),
            RandomV1.shuffle(state.board.containers.first().cards, seed, "branch-reseed-v1"))
        val move = LibraryActions.draw(state.board, id(20), id(21), 1).single()
        val json = GameWire.json.encodeToString(PlayAction.serializer(), move)
        assertFalse(json.contains("takeFromEdge")); assertFalse(json.contains("libraryInsertion"))
        assertFalse(GameWire.json.encodeToString(GameplayDelta.serializer(), GameplayDelta(emptyList())).contains("libraryReseeds"))
    }

    @Test fun reseededBranchesCanTraverseRetainedAnnotationsAndContinueAnInactiveHead() {
        var tree = draw(GameplayTree(root()))
        val event = tree.currentNodeId
        tree = play(tree, PlayAction.Ping(EventReference(tree.initial.id, event,
            tree.contextVersion(event)), PingTarget(PingTargetKind.CARD, id(1000))))
        repeat(32) { tree = play(tree, PlayAction.AdjustLife(a, if (it % 2 == 0) "1" else "-1")) }
        val displaced = tree.currentNodeId
        tree = play(tree, PlayAction.AdjustLife(a, "1"), target = tree.initial.root.id)
        assertNotNull(tree.prepareReview(displaced), "Traverse fork -> retained annotated path")
        tree = play(tree, PlayAction.AdjustLife(a, "1"), target = displaced)
        val target = tree.currentNodeId
        tree = play(tree, PlayAction.EventComment(EventReference(tree.initial.id, target, tree.contextVersion(target)),
            "inactive branch"), b, tree.initial.root.id)
        assertNotNull(tree.prepareReview(target), "Traverse inverse annotated fork")
        play(tree, PlayAction.SetNote(id(1000), "continued"), b, target)
    }

    @Test fun selectiveUndoRedoAcrossRetainedForkReseedsPreservesRecordedOrderAndTraversal() {
        var base = GameplayTree(root())
        base = place(base, library(base).cards[3], MoveInsertion.TOP)
        base = place(base, library(base).cards[8], MoveInsertion.BOTTOM)
        val anchor = base.currentNodeId
        val displaced = play(base, PlayAction.SetNote(id(1000), "displaced"), b)
        val ownFork = play(displaced, PlayAction.AdjustLife(a, "1"), target = anchor)
        val own = ownFork.currentNodeId
        val otherFork = play(ownFork, PlayAction.SetNote(id(1000), "other branch"), b, anchor)
        val continued = play(otherFork, PlayAction.SetNote(id(1001), "retained"), b, own)
        val suffix = continued.currentNodeId
        val before = state(continued)
        val originalReseeds = continued.nodes.getValue(suffix).transition.delta!!.libraryReseeds
        val undone = control(continued, GameAction.Undo)
        assertFalse(own in undone.nodes)
        assertEquals(anchor, undone.nodes.getValue(suffix).parentId)
        assertEquals(state(base).board.players, state(undone).board.players)
        assertEquals(before.board.containers, state(undone).board.containers)
        val rebased = undone.nodes.getValue(suffix).transition.delta!!.libraryReseeds
        assertEquals(originalReseeds.map { it.to }, rebased.map { it.to })
        assertEquals(state(base).board.containers.filter { it.zone == PlayZone.LIBRARY }.map { it.unknownMiddle },
            rebased.map { it.from })
        assertEquals(state(base), undone.prepareReview(anchor))
        assertEquals(state(otherFork), undone.prepareReview(otherFork.currentNodeId))
        assertEquals(state(undone), undone.prepareReview(suffix))
        val redone = control(undone, GameAction.Redo)
        assertEquals(continued.nodes, redone.nodes)
        assertEquals(before, state(redone))
        val decoded = TreeSync.decode(TreeSync.records(TreeSync.generation(undone)),
            undone.initial.gameId, undone.initial.id).tree
        assertEquals(state(undone), state(decoded))
        assertEquals(before, state(control(decoded, GameAction.Redo)))
    }

    @Test fun retainedReferenceStillBlocksUndoBeforeAnIndependentReseededContinuation() {
        var tree = play(GameplayTree(root()), PlayAction.AdjustLife(b, "1"), b)
        tree = play(tree, PlayAction.AdjustLife(a, "1"), target = tree.initial.root.id)
        val own = tree.currentNodeId
        tree = play(tree, PlayAction.EventComment(EventReference(tree.initial.id, own, tree.contextVersion(own)),
            "inactive reference"), b, tree.initial.root.id)
        tree = play(tree, PlayAction.SetNote(id(1000), "continued"), b, own)
        val before = state(tree)
        val blocked = HistoryContinuation.change(tree, a, command(tree, GameAction.Undo))
        assertEquals("referenceBlocked", blocked.reason)
        assertEquals(own, blocked.targetId)
        assertNull(blocked.tree)
        assertEquals(before, state(tree))
        assertTrue(tree.redoByAccount.isEmpty())
    }
}
