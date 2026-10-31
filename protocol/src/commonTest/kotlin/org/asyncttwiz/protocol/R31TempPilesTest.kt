package org.asyncttwiz.protocol

import kotlin.test.*

/** Rules v31: a Temp pile Search closes without shuffling; pile menu mass moves (Graveyard, Exile,
 * Shuffle into library, Top -> N) are ordinary moves in ONE invocation. */
class R31TempPilesTest {
    private val a = gameplayId(7)
    private val b = gameplayId(8)
    private val library = gameplayId(20)
    private val graveyard = gameplayId(23)
    private val exile = gameplayId(24)
    private fun seed(n: Int) = RandomV1.encodeSeed(ByteArray(32) { (n + it).toByte() })
    private var request = 31000

    private fun command(state: PlayState, action: GameAction) =
        GameCommand(gameplayId(request++), GameScope(state.gameId, playId = state.id), action, gameplayId(80), "1")

    private fun pile(state: PlayState, number: Int = 1) = TempPiles.containerId(state.id, a, number)

    /** a's Temp pile 1 holds 10, 11, 12 (top first, order unknown); the Library keeps 13, 14. */
    private fun root(): PlayState {
        val state = gameplayFixture()
        val temp = (10..12).map(::gameplayId)
        val rest = (13..14).map(::gameplayId)
        val tempId = pile(state)
        return state.copy(board = state.board.copy(cards = state.board.cards.map {
            if (it.id in temp) it.copy(containerId = tempId) else it
        }, containers = state.board.containers.map {
            if (it.id == library) it.copy(cards = rest, layers = LibraryLayers.initial(gameplayId(29), rest)) else it
        } + PlayContainer(tempId, PlayZone.LIBRARY, a, temp, layers = LibraryLayers.initial(gameplayId(27), temp), temp = true)))
    }

    /** Accepted, exactly applicable, reversible and replayable from its recorded event. */
    private fun exact(state: PlayState, action: PlayAction, actor: String = a): PlayState {
        val change = GameplayRules.change(state, actor, command(state, GameAction.Play(action)))
        val mutation = assertNotNull(change.mutation, "$action: ${change.reason ?: change.outcome}")
        val next = assertNotNull(change.state)
        assertEquals(CURRENT_GAMEPLAY_RULES_VERSION, mutation.transition.rulesVersion)
        val decoded = GameWire.json.decodeFromString<GameplayMutation>(GameWire.json.encodeToString(mutation))
        assertEquals(next, GameplayHistory.apply(state, decoded))
        assertEquals(state, GameplayHistory.unapply(next, decoded))
        next.board.containers.forEach { assertTrue(LibraryLayers.valid(it), "$it") }
        return next
    }

    private fun batch(tree: GameplayTree, actions: List<PlayAction>, actor: String = a): GameplayTree {
        val state = assertNotNull(tree.stateAt(tree.currentNodeId))
        val change = HistoryContinuation.change(tree, actor, command(state, GameAction.PlayBatch(actions)))
        return assertNotNull(change.tree, change.reason)
    }
    private fun undo(tree: GameplayTree, actor: String = a): GameplayTree {
        val state = assertNotNull(tree.stateAt(tree.currentNodeId))
        val change = SelectiveUndo.change(tree, actor, command(state, GameAction.Undo))
        return assertNotNull(change.tree, change.reason)
    }
    private fun board(tree: GameplayTree) = assertNotNull(tree.stateAt(tree.currentNodeId)).board
    private fun cards(board: BoardState, id: String) = board.containers.single { it.id == id }.cards

    @Test fun temporarySearchRulesAreVersionThirtyOne() {
        assertEquals(31, TempPiles.SEARCH_RULES_VERSION)
        assertTrue(CURRENT_GAMEPLAY_RULES_VERSION >= TempPiles.SEARCH_RULES_VERSION)
    }

    // ZON-043
    @Test fun closingATempPileSearchKeepsItsOrderAndKnowledge() {
        val start = root()
        // Either player searches and closes; the pile stays exactly as it was.
        for (actor in listOf(a, b)) {
            val open = exact(start, PlayAction.ShowContents(pile(start), ContentsSelection.ALL), actor)
            assertTrue(open.board.contentsDisplays.single().search)
            val closed = exact(open, PlayAction.DismissContents(open.board.contentsDisplays.single().id, ContentsRest.SHUFFLE, seed(3)), actor)
            assertTrue(closed.board.contentsDisplays.isEmpty())
            val before = start.board.containers.single { it.id == pile(start) }
            assertEquals(before, closed.board.containers.single { it.id == pile(start) })
            assertEquals(start.board.containers, closed.board.containers)
        }
    }

    // SYS-031
    @Test fun olderRulesStillShuffleATempPileOnSearchClose() {
        val start = root()
        val open = exact(start, PlayAction.ShowContents(pile(start), ContentsSelection.ALL))
        val dismiss = PlayAction.DismissContents(open.board.contentsDisplays.single().id, ContentsRest.SHUFFLE, seed(3))
        val old = assertNotNull(LibraryRules.change(open, a, command(open, GameAction.Play(dismiss)), dismiss,
            rulesVersion = TempPiles.SEARCH_RULES_VERSION - 1))
        val shuffled = assertNotNull(old.state)
        assertEquals(RandomV1.shuffle(cards(open.board, pile(start)), seed(3)), cards(shuffled.board, pile(start)))
        assertEquals(TempPiles.SEARCH_RULES_VERSION - 1, old.mutation!!.transition.rulesVersion)
        // A Library Search still shuffles under the current rules.
        val search = exact(start, PlayAction.ShowContents(library, ContentsSelection.ALL))
        val closed = exact(search, PlayAction.DismissContents(search.board.contentsDisplays.single().id, ContentsRest.SHUFFLE, seed(5)))
        assertEquals(RandomV1.shuffle(cards(start.board, library), seed(5)), cards(closed.board, library))
    }

    @Test fun shuffleIntoLibraryIsOneUndoableInvocation() {
        val start = root()
        val actions = TempPiles.shuffleIntoLibrary(start.board, pile(start), seed(7))
        assertEquals(4, actions.size)
        assertIs<PlayAction.ShuffleLibrary>(actions.last())
        var tree = batch(GameplayTree(start), actions)
        val after = board(tree)
        assertTrue(cards(after, pile(start)).isEmpty())
        assertEquals((10..14).map(::gameplayId).toSet(), cards(after, library).toSet())
        assertTrue(after.containers.single { it.id == library }.layers.none { it.ordered }, "a shuffled Library is unknown")
        // The moves and the shuffle read as ONE history entry.
        assertEquals("4", assertNotNull(tree.stateAt(tree.currentNodeId)!!.currentGroup).count)
        assertTrue(TempPiles.shuffleIntoLibrary(after, pile(start), seed(8)).isEmpty())
        tree = undo(tree)
        assertEquals(start.board, board(tree))
    }

    // ZON-041
    @Test fun topToAnotherPileMovesOnlyTheTopCardOntoItsTopAndCreatesThePile() {
        val start = root()
        assertTrue(TempPiles.topToPile(start.board, start.id, pile(start), 1).isEmpty())
        val actions = TempPiles.topToPile(start.board, start.id, pile(start), 3)
        assertEquals(1, actions.size)
        var tree = batch(GameplayTree(start), actions)
        val first = board(tree)
        assertEquals(listOf(gameplayId(10)), cards(first, pile(start, 3)))
        assertEquals(listOf(gameplayId(11), gameplayId(12)), cards(first, pile(start)))
        // A second Top -> 3 lands on top of the first.
        tree = batch(tree, TempPiles.topToPile(first, start.id, pile(start), 3))
        assertEquals(listOf(gameplayId(11), gameplayId(10)), cards(board(tree), pile(start, 3)))
        tree = undo(undo(tree))
        assertEquals(start.board, board(tree))
        assertNull(TempPiles.container(board(tree), a, 3))
    }

    // ZON-041
    @Test fun allToGraveyardAndExileEmptyThePile() {
        val start = root()
        for ((zone, target) in listOf(PlayZone.GRAVEYARD to graveyard, PlayZone.EXILE to exile)) {
            val actions = TempPiles.allToZone(start.board, pile(start), zone)
            assertEquals(3, actions.size)
            var tree = batch(GameplayTree(start), actions)
            assertTrue(cards(board(tree), pile(start)).isEmpty())
            assertEquals((10..12).map(::gameplayId).toSet(), cards(board(tree), target).toSet())
            assertTrue(board(tree).cards.filter { it.containerId == target }.all { CardVisibility.isVisibleFaceUp(board(tree), it, b) })
            tree = undo(tree)
            assertEquals(start.board, board(tree))
        }
    }

    // ZON-044
    @Test fun aLibrarySearchPutsACardFaceDownOnTopOfAnyTempPile() {
        val start = root()
        val open = exact(start, PlayAction.ShowContents(library, ContentsSelection.ALL))
        for (number in TempPiles.NUMBERS) {
            val planned = TempPiles.ensure(open.board, open.id, a, number)
            val move = LibraryActions.moveCaptured(planned, listOf(gameplayId(13)),
                MoveDestination(pile(open, number), MoveInsertion.TOP)).single()
            val moved = exact(open, move)
            val pile = assertNotNull(TempPiles.container(moved.board, a, number))
            assertEquals(gameplayId(13), pile.cards.first())
            val card = moved.board.cards.single { it.id == gameplayId(13) }
            assertFalse(CardVisibility.isVisibleFaceUp(moved.board, card, a))
            assertFalse(CardVisibility.isVisibleFaceUp(moved.board, card, b))
            assertEquals(listOf(gameplayId(14)), moved.board.contentsDisplays.single().cardIds)
        }
    }

    private fun lookReorderAndAdd(start: PlayState): PlayState {
        var state = exact(start, PlayAction.ShowContents(pile(start), ContentsSelection.TOP, 3))
        val display = state.board.contentsDisplays.single()
        assertEquals((10..12).map(::gameplayId), display.cardIds)
        state = exact(state, PlayAction.ReorderContents(display.id, gameplayId(12), gameplayId(10)))
        // The other player puts a Library card on the pile while it is being looked at.
        val add = LibraryActions.moveCaptured(state.board, listOf(gameplayId(13)), MoveDestination(pile(start), MoveInsertion.TOP)).single()
        return exact(state, add, actor = b)
    }

    // ZON-043
    @Test fun closingATempLookKeepsCardsAddedMeanwhileOnTopAndTheShownOrderBelow() {
        val open = lookReorderAndAdd(root())
        assertEquals(gameplayId(13), cards(open.board, pile(open)).first())
        val dismiss = PlayAction.DismissContents(open.board.contentsDisplays.single().id, ContentsRest.TOP)
        val closed = exact(open, dismiss)
        assertEquals(listOf(13, 12, 10, 11).map(::gameplayId), cards(closed.board, pile(open)))
        // Everyone saw where each card went: the whole pile is now known.
        assertTrue(closed.board.containers.single { it.id == pile(open) }.layers.all { it.ordered })
        // Rules v30 put the looked-at cards above the added one.
        val old = assertNotNull(LibraryRules.change(open, a, command(open, GameAction.Play(dismiss)), dismiss,
            rulesVersion = TempPiles.SEARCH_RULES_VERSION - 1).state)
        assertEquals(listOf(12, 10, 11, 13).map(::gameplayId), cards(old.board, pile(open)))
        // A Library Look still puts the shown cards on top.
        val look = exact(root(), PlayAction.ShowContents(library, ContentsSelection.TOP, 1))
        val added = exact(look, LibraryActions.moveCaptured(look.board, listOf(gameplayId(10)),
            MoveDestination(library, MoveInsertion.TOP)).single())
        val back = exact(added, PlayAction.DismissContents(added.board.contentsDisplays.single().id, ContentsRest.TOP))
        assertEquals(listOf(13, 10, 14).map(::gameplayId), cards(back.board, library))
    }

    @Test fun theInPlaceCloseUndoesExactly() {
        val start = root()
        var tree = GameplayTree(start)
        tree = batch(tree, listOf(PlayAction.ShowContents(pile(start), ContentsSelection.TOP, 3)))
        val display = board(tree).contentsDisplays.single()
        tree = batch(tree, listOf(PlayAction.ReorderContents(display.id, gameplayId(12), gameplayId(10))))
        tree = batch(tree, LibraryActions.moveCaptured(board(tree), listOf(gameplayId(13)), MoveDestination(pile(start), MoveInsertion.TOP)), actor = b)
        val open = board(tree)
        tree = batch(tree, listOf(PlayAction.DismissContents(display.id, ContentsRest.TOP)))
        assertEquals(listOf(13, 12, 10, 11).map(::gameplayId), cards(board(tree), pile(start)))
        tree = undo(tree)
        assertEquals(open, board(tree))
    }
}
