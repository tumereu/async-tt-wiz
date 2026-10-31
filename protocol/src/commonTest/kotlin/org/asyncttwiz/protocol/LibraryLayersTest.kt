package org.asyncttwiz.protocol

import kotlin.test.*

/** Rules v29: a library is an ordered list of layers; random-order moves add one hidden layer at an edge. */
class LibraryLayersTest {
    private fun id(n: Int) = "00000000-0000-4000-8000-${n.toString().padStart(12, '0')}"
    private val a = id(7)
    private val b = id(8)
    private val libraryId = id(20)
    private val handId = id(21)
    private val graveyardId = id(22)
    private val fieldId = id(23)
    private val secret = RandomV1.encodeSeed(ByteArray(32) { it.toByte() })
    private fun seed(n: Int) = RandomV1.encodeSeed(ByteArray(32) { (n + it).toByte() })
    private var request = 20000

    /** a: Library 1000..1009, Graveyard 1100..1104, a Battlefield token. b: Library 2000..2004. */
    private fun root(): PlayState {
        val definition = CardDefinition("Layer fixture", listOf(DefinitionFace(id(40), "Front", corner = "1/1")))
        val digest = DeckJson.digest(definition)
        val libraryA = (1000..1009).map(::id); val graveyard = (1100..1104).map(::id); val libraryB = (2000..2004).map(::id)
        val cards = libraryA.map { PlayCard(it, digest, a, libraryId, id(40)) } +
            graveyard.map { PlayCard(it, digest, a, graveyardId, id(40), covered = false) } +
            libraryB.map { PlayCard(it, digest, b, id(30), id(40)) } +
            PlayCard(id(1200), digest, a, fieldId, id(40), covered = false, token = true,
                placement = PlayPlacement(BoardPosition(0, 0), id(1201)))
        val containers = listOf(
            PlayContainer(libraryId, PlayZone.LIBRARY, a, libraryA, layers = LibraryLayers.initial(id(60), libraryA)),
            PlayContainer(handId, PlayZone.HAND, a), PlayContainer(graveyardId, PlayZone.GRAVEYARD, a, graveyard.reversed()),
            PlayContainer(fieldId, PlayZone.BATTLEFIELD, a, listOf(id(1200))), PlayContainer(id(24), PlayZone.EXILE, a),
            PlayContainer(id(30), PlayZone.LIBRARY, b, libraryB, layers = LibraryLayers.initial(id(61), libraryB)),
            PlayContainer(id(31), PlayZone.HAND, b), PlayContainer(id(32), PlayZone.GRAVEYARD, b),
            PlayContainer(id(33), PlayZone.BATTLEFIELD, b), PlayContainer(id(34), PlayZone.EXILE, b),
            PlayContainer(id(35), PlayZone.STACK))
        return PlayState(id(1), id(2), id(3), id(4), PlayRoot(id(5), id(6)), id(5),
            BoardState(listOf(PlayPlayer(a), PlayPlayer(b)), cards.sortedBy { it.id }, containers, TurnState(a)),
            listOf(SubmittedDefinition(digest, definition)), emptyList(), InitialStarter(StarterChoice(a), a), librarySecret = secret)
    }

    private fun command(state: PlayState, action: PlayAction, continuation: String? = null) = GameCommand(id(request++),
        GameScope(state.gameId, playId = state.id), GameAction.Play(action), id(9), "1",
        continuation = continuation?.let { Continuation(it, true) })
    /** Accepted, exactly applicable, reversible and replayable from its recorded event. */
    private fun exact(state: PlayState, action: PlayAction, actor: String = a): PlayState {
        val change = GameplayRules.change(state, actor, command(state, action))
        val mutation = assertNotNull(change.mutation, "$action: ${change.reason ?: change.outcome}")
        val next = assertNotNull(change.state)
        val decoded = GameWire.json.decodeFromString<GameplayMutation>(GameWire.json.encodeToString(mutation))
        assertEquals(next, GameplayHistory.apply(state, decoded))
        assertEquals(state, GameplayHistory.unapply(next, decoded))
        assertEquals(next, GameplayHistory.replay(state, decoded).state)
        next.board.containers.forEach { assertTrue(LibraryLayers.valid(it), "$it") }
        return next
    }
    private fun library(state: PlayState, containerId: String = libraryId) = state.board.containers.single { it.id == containerId }
    private fun shape(state: PlayState, containerId: String = libraryId) =
        library(state, containerId).layers.map { (if (it.ordered) "O" else "U") + it.size }
    private fun random(ids: List<Int>, bottom: Boolean, seed: Int = 1) =
        PlayAction.MoveToLibraryRandom(libraryId, ids.map(::id), bottom, seed(seed))
    private fun move(state: PlayState, card: Int, destination: MoveDestination): PlayAction.MoveCard {
        val c = state.board.cards.single { it.id == id(card) }
        return PlayAction.MoveCard(c.id, MoveSource(c.containerId, c.placement), destination)
    }
    private fun top(state: PlayState, card: Int) = exact(state, move(state, card, MoveDestination(libraryId, MoveInsertion.TOP)))

    // ZON-023
    @Test fun newGamesStartWithOneHiddenLayerAndShuffleCollapsesEverythingIntoOne() {
        val start = root()
        assertEquals(listOf("U10"), shape(start))
        val known = top(start, 1100)
        assertEquals(listOf("O1", "U10"), shape(known))
        val shuffled = exact(known, PlayAction.ShuffleLibrary(libraryId, seed(3)))
        assertEquals(listOf("U11"), shape(shuffled))
        assertEquals(RandomV1.shuffle(library(known).cards, seed(3)), library(shuffled).cards)
    }

    // ZON-025
    @Test fun randomTopAndBottomAddOneHiddenLayerEachAroundKnownCards() {
        var state = top(root(), 1100)
        val topLayer = library(state).layers.first()
        state = exact(state, random(listOf(1101, 1102, 1103), bottom = false, seed = 5))
        assertEquals(listOf("U3", "O1", "U10"), shape(state))
        assertEquals(RandomV1.shuffle((1101..1103).map(::id), seed(5)), library(state).cards.take(3))
        assertTrue(library(state).cards.drop(3).first() == id(1100))
        // The known card keeps its layer (and ID); the new layer is hidden and separate.
        assertEquals(topLayer, library(state).layers[1])
        val hiddenTop = library(state).layers.first()
        state = exact(state, random(listOf(1104), bottom = true))
        // A single card's place is known.
        assertEquals(listOf("U3", "O1", "U10", "O1"), shape(state))
        assertEquals(id(1104), library(state).cards.last())
        assertEquals(hiddenTop, library(state).layers.first())
        assertTrue(state.board.containers.single { it.id == graveyardId }.cards.isEmpty())
        assertTrue(state.board.cards.filter { it.id in (1101..1104).map(::id) }.all { it.covered && it.containerId == libraryId })
    }

    // ZON-025
    @Test fun randomMoveIsOneEventWithOrdinaryResetsAndValidatesItsInputs() {
        val start = root()
        val change = GameplayRules.change(start, a, command(start, random(listOf(1100, 1101), bottom = true)))
        val mutation = assertNotNull(change.mutation)
        assertEquals(HistorySignificance.CROSS_ZONE_MOVE, mutation.grouping!!.significance)
        assertTrue(mutation.transition.fields.any { it.targetId == id(1100) && it.field == GameplayField.COVERED })
        val token = GameplayRules.change(start, a, command(start, PlayAction.MoveToLibraryRandom(libraryId, listOf(id(1200)), false, seed(1))))
        assertEquals("inapplicable", token.reason)
        val wrongOwner = GameplayRules.change(start, a, command(start, PlayAction.MoveToLibraryRandom(id(30), listOf(id(1100)), false, seed(1))))
        assertEquals("inapplicable", wrongOwner.reason)
        assertFalse(GameplayRules.valid(PlayAction.MoveToLibraryRandom(libraryId, emptyList(), false, seed(1))))
        assertFalse(GameplayRules.valid(PlayAction.MoveToLibraryRandom(libraryId, listOf(id(1100), id(1100)), false, seed(1))))
        assertFalse(GameplayRules.valid(PlayAction.MoveToLibraryRandom(libraryId, listOf(id(1100)), false, "not a seed")))
        // Library cards already there are taken out and put back into the new hidden layer.
        val back = exact(start, PlayAction.MoveToLibraryRandom(libraryId, listOf(id(1000), id(1001), id(1100)), true, seed(2)))
        assertEquals(listOf("U8", "U3"), shape(back))
        assertEquals(setOf(1000, 1001, 1100).map(::id).toSet(), library(back).cards.takeLast(3).toSet())
    }

    // ZON-072
    @Test fun plannerSendsEachOwnersCardsToTheirOwnLibraryAndSkipsTokens() {
        val start = root()
        val board = start.board.copy(cards = start.board.cards.map { if (it.id == id(1103)) it.copy(owner = b) else it })
        var n = 0
        val actions = LibraryActions.toLibraryRandom(board, listOf(1100, 1103, 1101, 1200).map(::id), bottom = false) { seed(n++) }
        assertEquals(listOf(PlayAction.MoveToLibraryRandom(libraryId, listOf(id(1100), id(1101)), false, seed(0)),
            PlayAction.MoveToLibraryRandom(id(30), listOf(id(1103)), false, seed(1))), actions)
    }

    // ZON-024
    @Test fun reseedsEachHiddenLayerWithItsOwnStableDeterministicSeed() {
        var state = top(root(), 1100)
        state = exact(state, random(listOf(1101, 1102, 1103), bottom = false))
        val hidden = library(state).layers.filter { !it.ordered }
        val reseeds = LibraryKnowledge.reseeds(state, a).filter { it.containerId == libraryId }
        assertEquals(hidden.map { it.id }, reseeds.map { it.layerId })
        assertEquals(reseeds, LibraryKnowledge.reseeds(state, a).filter { it.containerId == libraryId })
        assertNotEquals(LibraryKnowledge.seed(state, a, libraryId, hidden[0].id), LibraryKnowledge.seed(state, a, libraryId, hidden[1].id))
        assertNotEquals(LibraryKnowledge.reseeds(state, a), LibraryKnowledge.reseeds(state, b))
        val reseeded = assertNotNull(LibraryKnowledge.applyReseeds(state, reseeds, false))
        // Members never leave their layer; the known card stays exactly in place.
        for ((old, new) in library(state).layers.zip(library(reseeded).layers)) {
            assertEquals(old.id, new.id)
            assertEquals(old.cards.toSet(), new.cards.toSet())
            if (old.ordered) assertEquals(old.cards, new.cards)
        }
        assertEquals(library(reseeded).layers.flatMap { it.cards }, library(reseeded).cards)
        assertEquals(state, LibraryKnowledge.applyReseeds(reseeded, reseeds, true))
        // A wrong layer, ordered layer or stale input is refused.
        assertNull(LibraryKnowledge.applyReseeds(state, listOf(reseeds[0].copy(layerId = library(state).layers[1].id)), false))
        assertNull(LibraryKnowledge.applyReseeds(reseeded, reseeds, false))
    }

    // ZON-024
    @Test fun forkingReseedsEveryHiddenLayerSameResultFromTheSamePointDifferentFromAnother() {
        var tree = GameplayTree(root())
        fun state() = assertNotNull(tree.stateAt(tree.currentNodeId))
        fun play(action: PlayAction, at: String? = null) {
            val change = HistoryContinuation.change(tree, a, command(state(), action, at))
            tree = assertNotNull(change.tree, change.reason)
        }
        play(move(state(), 1100, MoveDestination(libraryId, MoveInsertion.TOP)))
        play(random(listOf(1101, 1102, 1103), bottom = false))
        val point = tree.currentNodeId
        play(PlayAction.AdjustLife(a, "1"))
        val draw = LibraryActions.draw(state().board, libraryId, handId, 1).single().let { it.copy(source = it.source.copy(takeFromEdge = true)) }
        play(draw, point)
        val event = tree.nodes.getValue(tree.currentNodeId)
        assertEquals(2, event.transition.delta!!.libraryReseeds.count { it.containerId == libraryId })
        assertEquals(1, event.transition.delta.libraryReseeds.count { it.containerId == id(30) })
        val first = state().board.containers.single { it.id == handId }.cards.single()
        // The drawn card comes from the reseeded hidden top layer of three.
        assertTrue(first in (1101..1103).map(::id))
        assertTrue(event.transition.concerns!!.fields.any { it.field is ConcernField.LayerMembers })
        play(draw, point)
        assertEquals(first, state().board.containers.single { it.id == handId }.cards.single())
    }

    @Test fun drawingFromAHiddenTopLayerTakesTheConcreteTopCard() {
        var state = exact(root(), random(listOf(1101, 1102, 1103), bottom = false))
        val expected = library(state).cards.first()
        state = exact(state, LibraryActions.draw(state.board, libraryId, handId, 1).single())
        assertEquals(listOf(expected), state.board.containers.single { it.id == handId }.cards)
        assertEquals(listOf("U2", "U10"), shape(state))
        state = exact(state, LibraryActions.draw(state.board, libraryId, handId, 1).single())
        // One hidden card left of that layer: its place is known now.
        assertEquals(listOf("O1", "U10"), shape(state))
    }

    @Test fun gapInsertionInsideAHiddenLayerSplitsItAndTheUpperPartKeepsItsId() {
        val start = root()
        val layer = library(start).layers.single()
        val cards = library(start).cards
        val state = exact(start, move(start, 1100, MoveDestination(libraryId, MoveInsertion.GAP, afterId = cards[3], beforeId = cards[4])))
        assertEquals(listOf("U4", "O1", "U6"), shape(state))
        assertEquals(layer.id, library(state).layers.first().id)
        assertEquals(id(1100), library(state).cards[4])
    }

    // ZON-023
    @Test fun placingALookedAtBlockMakesItKnownLookingAloneDoesNot() {
        var state = exact(root(), PlayAction.ShowContents(libraryId, ContentsSelection.TOP, 3))
        assertEquals(listOf("U10"), shape(state))
        val panel = state.board.contentsDisplays.single()
        state = exact(state, PlayAction.ReorderContents(panel.id, panel.cardIds.last(), panel.cardIds.first()))
        val order = state.board.contentsDisplays.single().cardIds
        state = exact(state, PlayAction.DismissContents(panel.id, ContentsRest.TOP))
        assertEquals(listOf("O3", "U7"), shape(state))
        assertEquals(order, library(state).cards.take(3))
        var bottom = exact(root(), PlayAction.ShowContents(libraryId, ContentsSelection.TOP, 2))
        bottom = exact(bottom, PlayAction.DismissContents(bottom.board.contentsDisplays.single().id, ContentsRest.BOTTOM_RANDOM, seed(4)))
        assertEquals(listOf("U8", "U2"), shape(bottom))
        var search = exact(top(root(), 1100), PlayAction.ShowContents(libraryId, ContentsSelection.ALL))
        search = exact(search, PlayAction.DismissContents(search.board.contentsDisplays.single().id, seed = seed(6)))
        assertEquals(listOf("U11"), shape(search))
    }

    @Test fun keepTopRevealedSplitsTheExposedCardOffAHiddenLayer() {
        var state = exact(root(), PlayAction.SetKeepTopRevealed(libraryId, true))
        assertEquals(listOf("O1", "U9"), shape(state))
        state = exact(state, random(listOf(1100, 1101, 1102), bottom = false))
        assertEquals(listOf("O1", "U2", "O1", "U9"), shape(state))
    }

    @Test fun randomMoveUndoAndRedoAreExact() {
        var tree = GameplayTree(root())
        val before = assertNotNull(tree.stateAt(tree.currentNodeId))
        val played = HistoryContinuation.change(tree, a, command(before, random(listOf(1100, 1101, 1102), bottom = false)))
        tree = assertNotNull(played.tree)
        val after = assertNotNull(tree.stateAt(tree.currentNodeId))
        val undone = assertNotNull(HistoryContinuation.change(tree, a, command(after, PlayAction.AdjustLife(a, "0"))
            .copy(action = GameAction.Undo)).tree)
        assertEquals(before.board, assertNotNull(undone.stateAt(undone.currentNodeId)).board)
        val redone = assertNotNull(HistoryContinuation.change(undone, a, command(after, PlayAction.AdjustLife(a, "0"))
            .copy(action = GameAction.Redo)).tree)
        assertEquals(after.board, assertNotNull(redone.stateAt(redone.currentNodeId)).board)
    }

    // HIS-087
    @Test fun undoOnlyRefusedWhenALaterEventDependedOnTheSameHiddenLayer() {
        fun play(t: GameplayTree, actor: String, action: PlayAction): GameplayTree {
            val state = assertNotNull(t.stateAt(t.currentNodeId))
            return assertNotNull(HistoryContinuation.change(t, actor, command(state, action)).tree)
        }
        fun tree() = play(GameplayTree(root()), b, random(listOf(1100, 1101, 1102), bottom = true))
        fun board(t: GameplayTree) = assertNotNull(t.stateAt(t.currentNodeId)).board
        fun undo(t: GameplayTree) = HistoryContinuation.change(t, a,
            command(assertNotNull(t.stateAt(t.currentNodeId)), PlayAction.AdjustLife(a, "0")).copy(action = GameAction.Undo))
        // a draws from the top hidden layer; b then mills from the bottom hidden layer: independent layers.
        val setup = tree()
        var t = play(setup, a, LibraryActions.draw(board(setup), libraryId, handId, 1).single())
        val bottom = assertNotNull(t.stateAt(t.currentNodeId)).board
        t = play(t, b, LibraryActions.moveSlice(bottom, libraryId, ContentsSelection.BOTTOM, 1,
            MoveDestination(graveyardId, MoveInsertion.UNORDERED)).single())
        assertNotNull(undo(t).tree, undo(t).reason)
        // b's later draw from the same hidden top layer depended on its membership: refused.
        var same = play(setup, a, LibraryActions.draw(board(setup), libraryId, handId, 1).single())
        same = play(same, b, LibraryActions.draw(assertNotNull(same.stateAt(same.currentNodeId)).board, libraryId, handId, 1).single())
        assertEquals("replayBlocked", undo(same).reason)
    }

    @Test fun undoRefusedWhenItWouldChangeHowALaterRemovalNormalizedItsLayer() {
        fun play(t: GameplayTree, actor: String, action: PlayAction): GameplayTree {
            val state = assertNotNull(t.stateAt(t.currentNodeId))
            return assertNotNull(HistoryContinuation.change(t, actor, command(state, action)).tree)
        }
        fun board(t: GameplayTree) = assertNotNull(t.stateAt(t.currentNodeId)).board
        var t = play(GameplayTree(root()), b, random(listOf(1100, 1101, 1102), bottom = true))
        val (p, _, r) = board(t).containers.single { it.id == libraryId }.cards.takeLast(3)
        fun out(t: GameplayTree, card: String) = PlayAction.MoveCard(card, MoveSource(libraryId), MoveDestination(graveyardId, MoveInsertion.UNORDERED))
        t = play(t, a, out(t, r))
        t = play(t, b, out(t, p))
        // b's removal left one card, whose place thereby became known; with a's card back it would not.
        assertEquals(listOf("U10", "O1"), board(t).containers.single { it.id == libraryId }.layers.map { (if (it.ordered) "O" else "U") + it.size })
        val undo = HistoryContinuation.change(t, a, command(assertNotNull(t.stateAt(t.currentNodeId)), PlayAction.AdjustLife(a, "0"))
            .copy(action = GameAction.Undo))
        assertEquals("replayBlocked", undo.reason)
    }

    @Test fun tempPilesAreLayeredToo() {
        val start = root()
        val tempId = TempPiles.containerId(start.id, a)
        var state = exact(start, move(start, 1100, MoveDestination(tempId, MoveInsertion.TOP)))
        assertEquals(listOf("O1"), shape(state, tempId))
        state = exact(state, move(state, 1101, MoveDestination(tempId, MoveInsertion.TOP)))
        state = exact(state, PlayAction.ShuffleLibrary(tempId, seed(8)))
        assertEquals(listOf("U2"), shape(state, tempId))
        state = exact(state, PlayAction.MoveToLibraryRandom(libraryId, library(state, tempId).cards, false, seed(9)))
        assertTrue(library(state, tempId).cards.isEmpty() && library(state, tempId).layers.isEmpty())
        assertEquals(listOf("U2", "U10"), shape(state))
        val back = TempPiles.allToLibrary(state.board, tempId, bottom = true)
        assertTrue(back.isEmpty())
    }

    @Test fun startedPlaysAndSnapshotsValidateTheirLayers() {
        val start = root()
        start.board.containers.forEach { assertTrue(LibraryLayers.valid(it)) }
        val broken = start.board.containers.single { it.id == libraryId }.let { it.copy(layers = it.layers + LibraryLayer(id(62), false, emptyList())) }
        assertFalse(LibraryLayers.valid(broken))
        assertFalse(LibraryLayers.valid(start.board.containers.single { it.id == handId }.copy(layers = listOf(LibraryLayer(id(63), true, emptyList())))))
    }
}
