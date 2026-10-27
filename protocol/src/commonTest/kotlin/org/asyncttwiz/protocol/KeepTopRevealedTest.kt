package org.asyncttwiz.protocol

import kotlin.test.*

class KeepTopRevealedTest {
    private fun id(n: Int) = "00000000-0000-4000-8000-${n.toString().padStart(12, '0')}"
    private val a = id(7)
    private val b = id(8)
    private val libraryId = id(10)
    private val handId = id(11)
    private val seed = RandomV1.encodeSeed(ByteArray(32) { it.toByte() })
    private var request = 100
    private fun root(): PlayState {
        val definition = CardDefinition("Fixture", listOf(DefinitionFace(id(40), "Front")))
        val digest = DeckJson.digest(definition)
        val cards = (20..25).map { PlayCard(id(it), digest, a, libraryId, id(40)) }
        return PlayState(id(1), id(2), id(3), id(4), PlayRoot(id(5), id(6)), id(5),
            BoardState(listOf(PlayPlayer(a), PlayPlayer(b)), cards,
                listOf(PlayContainer(libraryId, PlayZone.LIBRARY, a, cards.map { it.id }),
                    PlayContainer(handId, PlayZone.HAND, a), PlayContainer(id(12), PlayZone.GRAVEYARD, a)), TurnState(a)),
            listOf(SubmittedDefinition(digest, definition)), emptyList(), InitialStarter(StarterChoice(a), a), librarySecret = seed)
    }
    private fun command(state: PlayState, action: GameAction) = GameCommand(id(request++),
        GameScope(state.gameId, playId = state.id), action, id(9), "1")
    private fun change(state: PlayState, action: PlayAction, actor: String = a): GameplayChange =
        GameplayRules.change(state, actor, command(state, GameAction.Play(action)))
    private fun exact(state: PlayState, action: PlayAction, actor: String = a): GameplayChange {
        val changed = change(state, action, actor)
        assertNull(changed.reason, "$action")
        val next = assertNotNull(changed.state)
        val mutation = assertNotNull(changed.mutation)
        val decoded = GameWire.json.decodeFromString<GameplayMutation>(GameWire.json.encodeToString(mutation))
        assertEquals(next, GameplayHistory.apply(state, decoded))
        assertEquals(state, GameplayHistory.unapply(next, decoded))
        assertEquals(next, GameplayHistory.replay(state, decoded).state)
        return changed
    }
    private fun library(state: PlayState) = state.board.containers.single { it.id == libraryId }
    private fun assertTop(state: PlayState) {
        val library = library(state)
        val top = library.cards.first()
        assertTrue(library.keepTopRevealed)
        assertEquals(top, library.knownTop.first())
        assertFalse(top in library.unknownMiddle)
        assertFalse(top in library.knownBottom)
        for (viewer in listOf(a, b)) for (card in state.board.cards.filter { it.containerId == libraryId }) {
            assertEquals(card.id == top, CardVisibility.isVisibleFaceUp(state.board, card, viewer))
            if (card.id == top) assertEquals(listOf(card.faceId),
                ManualDisclosure.permittedFaces(state, card, viewer, inspecting = true).map { it.faceId })
        }
    }

    private fun state(tree: GameplayTree) = assertNotNull(tree.stateAt(tree.currentNodeId))
    private fun play(tree: GameplayTree, action: PlayAction, actor: String = a, target: String? = null): GameplayTree {
        val result = HistoryContinuation.change(tree, actor, command(state(tree), GameAction.Play(action)).copy(
            continuation = target?.let { Continuation(it, true) }))
        return assertNotNull(result.tree, "$action: ${result.reason}")
    }
    private fun control(tree: GameplayTree, action: GameAction, actor: String = a): GameplayTree {
        val result = HistoryContinuation.change(tree, actor, command(state(tree), action))
        return assertNotNull(result.tree, "$action: ${result.reason}")
    }
    private fun movingSeed(cards: List<String>, oldTop: String, drawn: Int = 0): String =
        (0..255).map { n -> RandomV1.encodeSeed(ByteArray(32) { n.toByte() }) }.first {
            RandomV1.shuffle(cards, it).drop(drawn).first() != oldTop
        }

    @Test fun explicitShuffleIncludesTheKnownTopInTheFullPermutation() {
        val before = exact(root(), PlayAction.SetKeepTopRevealed(libraryId, true)).state!!
        val cards = library(before).cards
        val shuffleSeed = movingSeed(cards, cards.first())
        val expected = RandomV1.shuffle(cards, shuffleSeed)
        assertNotEquals(cards.first(), expected.first()) // Deterministic witness, not a probabilistic shuffle assertion.
        val after = exact(before, PlayAction.ShuffleLibrary(libraryId, shuffleSeed)).state!!
        assertEquals(expected, library(after).cards)
        assertEquals(expected.first(), library(after).knownTop.single())
        assertTrue(library(after).knownBottom.isEmpty())
        assertTop(after)
    }

    @Test fun searchDismissalShufflesTheFullLibraryIncludingTheKnownTop() {
        // Search forces a shuffle even if an older caller asks to put the rest on top.
        for (rest in listOf(ContentsRest.SHUFFLE, ContentsRest.TOP)) {
            var before = exact(root(), PlayAction.SetKeepTopRevealed(libraryId, true)).state!!
            before = exact(before, PlayAction.ShowContents(libraryId, ContentsSelection.ALL)).state!!
            val display = before.board.contentsDisplays.single()
            assertTrue(display.search)
            val cards = library(before).cards
            val shuffleSeed = movingSeed(cards, cards.first())
            val expected = RandomV1.shuffle(cards, shuffleSeed)
            assertNotEquals(cards.first(), expected.first())
            val after = exact(before, PlayAction.DismissContents(display.id, rest, shuffleSeed)).state!!
            assertTrue(after.board.contentsDisplays.isEmpty())
            assertEquals(expected, library(after).cards)
            assertEquals(expected.first(), library(after).knownTop.single())
            assertTop(after)
        }
    }

    @Test fun mulliganShufflesTheKnownTopWithTheReturnedHandBeforeDrawingAndRevealingTheNewTop() {
        var before = exact(root(), PlayAction.SetKeepTopRevealed(libraryId, true)).state!!
        repeat(2) { before = exact(before, LibraryActions.draw(before.board, libraryId, handId, 1).single()).state!! }
        val hand = before.board.containers.single { it.id == handId }.cards
        val oldTop = library(before).cards.first()
        val fullLibrary = hand + library(before).cards
        val shuffleSeed = movingSeed(fullLibrary, oldTop, drawn = hand.size)
        val expected = RandomV1.shuffle(fullLibrary, shuffleSeed)
        assertNotEquals(oldTop, expected[hand.size])
        val after = exact(before, PlayAction.Mulligan(libraryId, handId, hand, hand.size, shuffleSeed)).state!!
        assertEquals(expected.take(hand.size), after.board.containers.single { it.id == handId }.cards)
        assertEquals(expected.drop(hand.size), library(after).cards)
        assertEquals(expected[hand.size], library(after).knownTop.single())
        assertTop(after)
    }

    @Test fun bothActorsReseedOnlyTheUnknownMiddleAfterEachDrawIncludingASingleUnknownCard() {
        var before = exact(root(), PlayAction.SetKeepTopRevealed(libraryId, true)).state!!
        repeat(5) { draws ->
            val old = library(before)
            assertEquals(5 - draws, old.unknownMiddle.size)
            assertTop(before)
            for (actor in listOf(a, b)) {
                val reseeds = LibraryKnowledge.reseeds(before, actor)
                val reseed = reseeds.single()
                assertEquals(old.unknownMiddle, reseed.from)
                assertEquals(RandomV1.shuffle(old.unknownMiddle, LibraryKnowledge.seed(before, actor, libraryId),
                    "branch-reseed-v1"), reseed.to)
                assertFalse(old.cards.first() in reseed.to)
                val after = assertNotNull(LibraryKnowledge.applyReseeds(before, reseeds, false))
                assertEquals(old.cards.first(), library(after).cards.first())
                assertEquals(old.knownTop, library(after).knownTop)
                assertEquals(old.knownBottom, library(after).knownBottom)
                assertTop(after)
                assertEquals(before, LibraryKnowledge.applyReseeds(after, reseeds, true))
                // Rebase a retained result against a different incoming unknown order, never the known edge.
                val reordered = before.copy(board = before.board.copy(containers = before.board.containers.map {
                    if (it.id == libraryId) it.copy(cards = it.knownTop + it.unknownMiddle.reversed() + it.knownBottom) else it
                }))
                val rebased = assertNotNull(LibraryKnowledge.rebaseReseeds(reordered, reseeds))
                assertEquals(old.unknownMiddle.reversed(), rebased.single().from)
                assertEquals(reseed.to, rebased.single().to)
                assertEquals(after, LibraryKnowledge.applyReseeds(reordered, rebased, false))
            }
            before = exact(before, LibraryActions.draw(before.board, libraryId, handId, 1).single()).state!!
        }
    }

    @Test fun selectiveUndoRedoOfUnrelatedLifeAcrossARetainedReseedPreservesTheRevealedTop() {
        for (legacy in listOf(false, true)) {
            var base = play(GameplayTree(root()), PlayAction.SetKeepTopRevealed(libraryId, true), b)
            repeat(2) { base = play(base, LibraryActions.draw(state(base).board, libraryId, handId, 1).single(), b) }
            val anchor = base.currentNodeId
            val life = PlayAction.AdjustLife(a, "1")
            val own = if (legacy) {
                // Compatibility reseeds must really rebase their incoming order when this fork is removed.
                val displaced = play(base, PlayAction.AdjustLife(b, "1"), b)
                play(displaced, life, target = anchor)
            } else play(base, life)
            val other = play(own, PlayAction.AdjustLife(b, "1"), b, anchor)
            var continued = play(other, PlayAction.SetPlayerCounter(b, CounterColor.RED, "retained"), b, own.currentNodeId)
            if (legacy) continued = continued.copy(nodes = continued.nodes.mapValues { (_, node) ->
                val old = node.copy(transition = node.transition.copy(concerns = null))
                old.copy(transition = old.transition.copy(delta = old.transition.delta!!.copy(seal = GameplayDeltas.seal(old))))
            })
            val before = state(continued)
            val suffix = continued.currentNodeId
            val reseeds = continued.nodes.getValue(suffix).transition.delta!!.libraryReseeds
            assertFalse(reseeds.isEmpty())
            val undone = control(continued, GameAction.Undo)
            assertFalse(own.currentNodeId in undone.nodes)
            assertEquals(anchor, undone.nodes.getValue(suffix).parentId)
            assertEquals(state(base).board.players.single { it.accountId == a }, state(undone).board.players.single { it.accountId == a })
            assertEquals(before.board.containers, state(undone).board.containers)
            assertTop(state(undone))
            val rebased = undone.nodes.getValue(suffix).transition.delta!!.libraryReseeds
            assertEquals(reseeds.map { it.to }, rebased.map { it.to })
            if (legacy) assertNotEquals(reseeds.map { it.from }, rebased.map { it.from })
            val redone = control(undone, GameAction.Redo)
            assertEquals(before, state(redone))
            assertEquals(continued.nodes, redone.nodes)
            assertTop(state(redone))
        }
    }

    @Test fun disabledRevealLeavesTheTopUnknownAndAllowsBranchReseedsToChangeIt() {
        val before = root()
        assertFalse(library(before).keepTopRevealed)
        assertTrue(library(before).knownTop.isEmpty())
        val after = listOf(a, b).map { actor ->
            assertNotNull(LibraryKnowledge.applyReseeds(before, LibraryKnowledge.reseeds(before, actor), false))
        }
        assertTrue(after.any { library(it).cards.first() != library(before).cards.first() })
        after.forEach { state ->
            assertTrue(library(state).knownTop.isEmpty())
            assertTrue(state.board.cards.none { CardVisibility.isVisibleFaceUp(state.board, it, b) })
        }
    }

    @Test fun realBranchesFromEarlierAndInternalCurrentNodesPreserveTheTopUntilExplicitShuffle() {
        var tree = play(GameplayTree(root()), PlayAction.SetKeepTopRevealed(libraryId, true))
        val points = mutableListOf<PlayState>()
        repeat(3) {
            tree = play(tree, LibraryActions.draw(state(tree).board, libraryId, handId, 1).single())
            points += state(tree)
        }
        val original = tree
        for ((index, actor) in listOf(a, b).withIndex()) {
            val atFork = points[index]
            val source = if (index == 0) original else assertNotNull(original.activate(atFork.currentNodeId).tree)
                .restoreEndpoint(atFork, original.endpointIndex())
            if (index == 1) assertEquals(atFork.currentNodeId, source.currentNodeId)
            val rootMaterializations = source.work.rootMaterializations
            val fullHistoryScans = source.work.fullHistoryScans
            val forked = play(source, PlayAction.AdjustLife(actor, "1"), actor, atFork.currentNodeId)
            val fork = forked.nodes.getValue(forked.currentNodeId)
            assertEquals(atFork.currentNodeId, fork.parentId)
            assertNotEquals(atFork.currentBranchId, fork.branchId)
            assertEquals(atFork.currentNodeId, forked.branches.getValue(fork.branchId).forkNodeId)
            val reseed = fork.transition.delta!!.libraryReseeds.single()
            assertEquals(library(atFork).unknownMiddle, reseed.from)
            val branched = state(forked)
            assertEquals(library(atFork).cards.first(), library(branched).cards.first())
            assertTop(branched)
            assertEquals(branched, GameplayHistory.apply(atFork, fork))
            assertEquals(branched, GameplayHistory.replay(atFork, fork).state)
            assertEquals(atFork, GameplayHistory.unapply(branched, fork))
            assertEquals(state(original), forked.prepareReview(original.currentNodeId))
            assertEquals(branched, forked.prepareReview(fork.id))
            val cards = library(branched).cards
            val shuffleSeed = movingSeed(cards, cards.first())
            val shuffled = play(forked, PlayAction.ShuffleLibrary(libraryId, shuffleSeed), actor)
            assertEquals(RandomV1.shuffle(cards, shuffleSeed), library(state(shuffled)).cards)
            assertNotEquals(cards.first(), library(state(shuffled)).cards.first())
            assertTop(state(shuffled))
            // All normal traversal above uses nearby materializations, not root reconstruction or tree scans.
            assertEquals(rootMaterializations, shuffled.work.rootMaterializations)
            assertEquals(fullHistoryScans, shuffled.work.fullHistoryScans)
        }
    }

    @Test fun eitherPlayerCanToggleAndBothDirectionsUndoRedoAcrossUnrelatedOtherPlayerEdit() {
        for (actor in listOf(a, b)) {
            val other = if (actor == a) b else a
            var state = root()
            assertEquals("missingReference", change(state, PlayAction.SetKeepTopRevealed(handId, true), actor).reason)
            assertEquals("missingReference", change(state, PlayAction.SetKeepTopRevealed(id(99), true), actor).reason)
            val noOp = change(state, PlayAction.SetKeepTopRevealed(libraryId, false), actor)
            assertEquals(state, noOp.state)
            assertNull(noOp.mutation)
            assertNull(noOp.reason)
            var tree = GameplayTree(state)
            for (enabled in listOf(true, false)) {
                val before = state.board
                val result = assertNotNull(tree.play(actor, command(state, GameAction.Play(PlayAction.SetKeepTopRevealed(libraryId, enabled)))).tree)
                val toggled = assertNotNull(result.stateAt(result.currentNodeId))
                assertEquals(18, result.nodes.getValue(result.currentNodeId).transition.rulesVersion)
                if (enabled) assertTop(toggled) else assertTrue(toggled.board.cards.none { CardVisibility.isVisibleFaceUp(toggled.board, it, b) })
                val later = assertNotNull(result.play(other, command(toggled, GameAction.Play(PlayAction.AdjustLife(other, "1")))).tree)
                val laterState = assertNotNull(later.stateAt(later.currentNodeId))
                val undone = assertNotNull(SelectiveUndo.change(later, actor, command(laterState, GameAction.Undo)).tree)
                val undoneState = assertNotNull(undone.stateAt(undone.currentNodeId))
                assertEquals(before.copy(players = laterState.board.players), undoneState.board)
                val redone = assertNotNull(SelectiveUndo.change(undone, actor, command(undoneState, GameAction.Redo)).tree)
                state = assertNotNull(redone.stateAt(redone.currentNodeId))
                assertEquals(laterState, state)
                tree = redone
            }
        }
    }

    @Test fun currentTopTracksDrawMillShuffleAndMoveToTopWithExactDisclosuresAndInverse() {
        var state = exact(root(), PlayAction.SetKeepTopRevealed(libraryId, true)).let {
            assertFalse(it.mutation!!.transition.disclosures.single().automatic)
            it.state!!
        }
        assertTop(state)
        for (destination in listOf(handId, id(12))) {
            val oldTop = library(state).cards.first()
            state = exact(state, PlayAction.MoveCard(oldTop, MoveSource(libraryId, edge = MoveSourceEdge.TOP),
                MoveDestination(destination, MoveInsertion.UNORDERED))).let {
                val disclosure = it.mutation!!.transition.disclosures.single { d -> d.containerId == libraryId }
                assertTrue(disclosure.automatic)
                assertEquals(listOf(a, b), disclosure.viewers)
                assertEquals(library(it.state!!).cards.first(), disclosure.cardId)
                it.state!!
            }
            assertTop(state)
            if (destination == handId) assertFalse(CardVisibility.isVisibleFaceUp(state.board, state.board.cards.single { it.id == oldTop }, b))
        }
        state = exact(state, PlayAction.ShuffleLibrary(libraryId, seed)).state!!
        assertTop(state)
        val handCard = state.board.containers.single { it.id == handId }.cards.single()
        state = exact(state, PlayAction.MoveCard(handCard, MoveSource(handId), MoveDestination(libraryId, MoveInsertion.TOP))).state!!
        assertEquals(handCard, library(state).cards.first())
        assertTop(state)
        val hiddenAgain = exact(state, PlayAction.SetKeepTopRevealed(libraryId, false)).state!!
        assertEquals(library(state).knownTop, library(hiddenAgain).knownTop) // Turning off does not unlearn the identity.
        assertFalse(CardVisibility.isVisibleFaceUp(hiddenAgain.board, hiddenAgain.board.cards.single { it.id == handCard }, b))
    }

    @Test fun selectiveUndoRedoRestoresEachExposedTopAcrossUnrelatedOpponentActions() {
        val initial = root()
        var tree = assertNotNull(GameplayTree(initial).play(a, command(initial,
            GameAction.Play(PlayAction.SetKeepTopRevealed(libraryId, true)))).tree)
        fun state() = assertNotNull(tree.stateAt(tree.currentNodeId))
        repeat(3) { step ->
            val before = state()
            val action = when (step) {
                0 -> LibraryActions.draw(before.board, libraryId, handId, 1).single()
                1 -> PlayAction.ShuffleLibrary(libraryId, seed)
                else -> PlayAction.MoveCard(before.board.containers.single { it.id == handId }.cards.single(),
                    MoveSource(handId), MoveDestination(libraryId, MoveInsertion.TOP))
            }
            tree = assertNotNull(tree.play(a, command(before, GameAction.Play(action))).tree)
            val after = state()
            assertTop(after)
            tree = assertNotNull(tree.play(b, command(after, GameAction.Play(PlayAction.AdjustLife(b, "1")))).tree)
            val later = state()
            tree = assertNotNull(SelectiveUndo.change(tree, a, command(later, GameAction.Undo)).tree)
            assertEquals(before.board.containers, state().board.containers)
            assertTop(state())
            tree = assertNotNull(SelectiveUndo.change(tree, a, command(state(), GameAction.Redo)).tree)
            assertEquals(later, state())
        }
    }

    @Test fun emptyLibraryAndRefillAndBranchReseedKeepTheExposedIdentityFixed() {
        var state = exact(root(), PlayAction.SetKeepTopRevealed(libraryId, true)).state!!
        repeat(6) {
            state = exact(state, LibraryActions.draw(state.board, libraryId, handId, 1).single()).state!!
        }
        assertTrue(library(state).keepTopRevealed)
        assertTrue(library(state).knownTop.isEmpty())
        val card = state.board.containers.single { it.id == handId }.cards.first()
        state = exact(state, PlayAction.MoveCard(card, MoveSource(handId), MoveDestination(libraryId, MoveInsertion.TOP))).state!!
        assertTop(state)
        state = exact(root(), PlayAction.SetKeepTopRevealed(libraryId, true)).state!!
        val top = library(state).cards.first()
        val reseeded = assertNotNull(LibraryKnowledge.applyReseeds(state, LibraryKnowledge.reseeds(state, b), false))
        assertEquals(top, library(reseeded).cards.first())
        assertTop(reseeded)
        val initial = root()
        val tree = assertNotNull(GameplayTree(initial).play(a, command(initial,
            GameAction.Play(PlayAction.SetKeepTopRevealed(libraryId, true)))).tree)
        state = assertNotNull(tree.stateAt(tree.currentNodeId))
        val moved = assertNotNull(tree.play(b, command(state, GameAction.Play(
            LibraryActions.draw(state.board, libraryId, handId, 1).single()))).tree)
        // An opponent draw depends on the toggle; selective undo must not leave its exposure behind.
        assertEquals("replayBlocked", SelectiveUndo.change(moved, a, command(state, GameAction.Undo)).reason)
    }

    @Test fun explicitFaceChangeOnTheRevealedTopRemainsAnExplicitReveal() {
        val initial = root()
        val definition = initial.definitions.single().definition.let {
            it.copy(faces = it.faces + DefinitionFace(id(41), "Back"))
        }
        val digest = DeckJson.digest(definition)
        val state = exact(initial.copy(definitions = listOf(SubmittedDefinition(digest, definition)),
            board = initial.board.copy(cards = initial.board.cards.map { it.copy(definition = digest) })),
            PlayAction.SetKeepTopRevealed(libraryId, true)).state!!
        val changed = exact(state, PlayAction.SetFace(library(state).cards.first(), id(41)))
        val disclosure = changed.mutation!!.transition.disclosures.single()
        assertFalse(disclosure.automatic)
        assertEquals(listOf(a, b), disclosure.viewers)
        assertEquals(id(41), disclosure.faceId)
    }

    @Test fun legacyVersion13ShuffleReplaysWithoutNewFieldsOrDisclosures() {
        val state = root()
        val action = PlayAction.ShuffleLibrary(libraryId, seed)
        val accepted = change(state, action)
        val next = accepted.state!!
        val raw = accepted.mutation!!.let {
            val transition = it.transition.copy(rulesVersion = 13, concerns = null, delta = null)
            it.copy(transition = transition, grouping = HistoryGrouping.classify(it.action, state.board.containers, transition))
        }
        val delta = GameplayDeltas.capture(state, next, raw.id)
        val recorded = GameplayHistory.recontextualize(state, raw.copy(transition = raw.transition.copy(delta = delta)))
        val legacy = recorded.copy(transition = recorded.transition.copy(delta = delta.copy(seal = GameplayDeltas.seal(recorded))))
        val bytes = GameWire.json.encodeToString(legacy)
        assertFalse("keepTopRevealed" in bytes)
        assertFalse("automatic" in bytes)
        val decoded = GameWire.json.decodeFromString<GameplayMutation>(bytes)
        val replayed = assertNotNull(GameplayHistory.apply(state, decoded))
        assertEquals(next.copy(currentGroup = legacy.bookkeeping!!.toGroup), replayed)
        assertEquals(state, GameplayHistory.unapply(replayed, decoded))
        assertTrue(library(replayed).knownTop.isEmpty())
        assertTrue(replayed.board.cards.none { CardVisibility.isVisibleFaceUp(replayed.board, it, b) })
        assertEquals(bytes, GameWire.json.encodeToString(decoded))
    }
}
