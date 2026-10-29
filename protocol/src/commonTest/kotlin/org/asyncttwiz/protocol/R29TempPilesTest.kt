package org.asyncttwiz.protocol

import kotlinx.serialization.json.*
import kotlin.test.*

/** Rules v29: up to four numbered Temp piles per player; 2..4 are turned on/off as shared events. */
class R29TempPilesTest {
    private val a = gameplayId(7)
    private val b = gameplayId(8)
    private val library = gameplayId(20)
    private val graveyard = gameplayId(23)

    private fun command(action: GameAction, n: Int, state: PlayState) =
        GameCommand(gameplayId(n), GameScope(state.gameId, playId = state.id), action, gameplayId(80), "1")

    private fun accepted(state: PlayState, action: PlayAction, n: Int, actor: Int = 7): GameplayChange {
        val change = gameplayChange(state, action, n, actor)
        assertNotNull(change.state, change.reason)
        val event = assertNotNull(change.mutation)
        assertEquals(CURRENT_GAMEPLAY_RULES_VERSION, event.transition.rulesVersion)
        assertEquals(change.state, GameplayHistory.apply(state, event), "recorded replay of $action")
        assertEquals(state, GameplayHistory.unapply(change.state, event), "exact inverse of $action")
        return change
    }

    private fun pile(state: PlayState, number: Int, account: String = a) = TempPiles.containerId(state.id, account, number)

    private fun graveyardState(): PlayState {
        val state = gameplayFixture()
        val ids = listOf(10, 11, 12).map(::gameplayId)
        return state.copy(board = state.board.copy(cards = state.board.cards.map {
            if (it.id in ids) it.copy(containerId = graveyard, covered = false) else it
        }, containers = state.board.containers.map { when (it.id) {
            library -> it.copy(cards = it.cards - ids.toSet())
            graveyard -> it.copy(cards = ids)
            else -> it
        } }))
    }

    private fun toPile(id: Int, target: String) =
        PlayAction.MoveCard(gameplayId(id), MoveSource(graveyard), MoveDestination(target, MoveInsertion.TOP))

    @Test fun rulesVersionIs29() {
        assertEquals(29, TempPiles.RULES_VERSION)
        assertTrue(TempPiles.RULES_VERSION <= CURRENT_GAMEPLAY_RULES_VERSION)
    }

    @Test fun pileOneKeepsItsV23IdAndEveryPileHasItsOwn() {
        val state = gameplayFixture()
        assertEquals(CommandIds.uuidV5(state.id, "container/$a/TEMP"), pile(state, 1))
        assertEquals(TempPiles.containerId(state.id, a), pile(state, 1))
        val ids = TempPiles.NUMBERS.flatMap { n -> listOf(pile(state, n, a), pile(state, n, b)) }
        assertEquals(8, ids.toSet().size)
        assertEquals(TempPiles.Ref(b, 3), TempPiles.refs(state)[pile(state, 3, b)])
        assertEquals(listOf(1), TempPiles.visibleNumbers(state.board, a))
    }

    @Test fun firstMoveIntoPileThreeCreatesItWithoutPileTwoAndItsInverseRemovesIt() {
        val start = graveyardState()
        val next = accepted(start, toPile(10, pile(start, 3)), 100).state!!
        val three = assertNotNull(TempPiles.container(next.board, a, 3))
        assertTrue(three.temp)
        assertEquals(3, three.tempNumber)
        assertEquals(PlayZone.LIBRARY, three.zone)
        assertEquals(listOf(gameplayId(10)), three.cards)
        assertNull(TempPiles.container(next.board, a, 2))
        assertNull(TempPiles.container(next.board, a, 1))
        assertEquals(listOf(1, 3), TempPiles.visibleNumbers(next.board, a))
        assertEquals(library, next.board.zoneContainer(a, PlayZone.LIBRARY)!!.id)
        assertFalse(CardVisibility.isVisibleFaceUp(next.board, next.board.cards.single { it.id == gameplayId(10) }, b))
        // Piles 2 and 4 are independent of pile 3.
        val four = accepted(next, toPile(11, pile(start, 4)), 101).state!!
        assertEquals(listOf(gameplayId(11)), TempPiles.container(four.board, a, 4)!!.cards)
        assertEquals(listOf(gameplayId(10)), TempPiles.container(four.board, a, 3)!!.cards)
    }

    @Test fun togglingPilesOnAndOff() {
        val start = graveyardState()
        val on = accepted(start, PlayAction.SetTempPile(a, 2, true), 100)
        val two = assertNotNull(TempPiles.container(on.state!!.board, a, 2))
        assertTrue(two.cards.isEmpty())
        assertEquals(pile(start, 2), two.id)
        assertEquals(listOf(1, 2), TempPiles.visibleNumbers(on.state.board, a))
        // Turning on an already-on pile, or off an already-off one, changes nothing.
        assertNull(gameplayChange(on.state, PlayAction.SetTempPile(a, 2, true), 101).mutation)
        assertNull(gameplayChange(start, PlayAction.SetTempPile(a, 2, false), 101).mutation)
        // Either player may turn on either player's piles.
        assertNotNull(TempPiles.container(accepted(on.state, PlayAction.SetTempPile(a, 4, true), 102, actor = 8).state!!.board, a, 4))
        val filled = accepted(on.state, toPile(10, pile(start, 2)), 103).state!!
        assertEquals("inapplicable", gameplayChange(filled, PlayAction.SetTempPile(a, 2, false), 104).reason)
        val off = accepted(on.state, PlayAction.SetTempPile(a, 2, false), 105).state!!
        assertEquals(start.board, off.board)
        // Pile 1 is never turned on or off; there is no pile 5.
        for (action in listOf(PlayAction.SetTempPile(a, 1, false), PlayAction.SetTempPile(a, 1, true),
                PlayAction.SetTempPile(a, 5, true), PlayAction.SetTempPile(a, 0, true)))
            assertEquals("invalidInput", gameplayChange(start, action, 106).reason, "$action")
        assertEquals("missingReference", gameplayChange(start, PlayAction.SetTempPile(gameplayId(9), 2, true), 107).reason)
    }

    @Test fun allToAnotherPileMovesTheWholePileInOrderAsOneUndo() {
        val root = graveyardState()
        var tree = GameplayTree(root)
        val toOne = LibraryActions.moveCaptured(TempPiles.ensure(root.board, root.id, a), (10..12).map(::gameplayId),
            MoveDestination(pile(root, 1), MoveInsertion.TOP))
        tree = assertNotNull(HistoryContinuation.change(tree, a, command(GameAction.PlayBatch(toOne), 400, root)).tree)
        val filled = tree.stateAt(tree.currentNodeId)!!
        val order = TempPiles.container(filled.board, a)!!.cards
        assertEquals(3, order.size)
        val all = TempPiles.allToPile(filled.board, filled.id, pile(root, 1), 4)
        assertEquals(3, all.size)
        assertTrue(TempPiles.allToPile(filled.board, filled.id, pile(root, 1), 1).isEmpty())
        tree = assertNotNull(HistoryContinuation.change(tree, a, command(GameAction.PlayBatch(all), 401, root)).tree)
        val moved = tree.stateAt(tree.currentNodeId)!!
        assertEquals(order, TempPiles.container(moved.board, a, 4)!!.cards)
        assertTrue(TempPiles.container(moved.board, a, 1)!!.cards.isEmpty())
        // Pile 1 -> pile 4 is a move between zones, not a placement within one.
        assertEquals(HistorySignificance.CROSS_ZONE_MOVE,
            HistoryGrouping.classify(all.first(), TempPiles.ensure(filled.board, filled.id, a, 4).containers).significance)
        tree = assertNotNull(SelectiveUndo.change(tree, a, command(GameAction.Undo, 900, root)).tree)
        assertEquals(filled.board, tree.stateAt(tree.currentNodeId)!!.board)
        assertNull(TempPiles.container(tree.stateAt(tree.currentNodeId)!!.board, a, 4))
    }

    @Test fun undoingTurnOnIsBlockedByALaterMoveIntoThePileButNotByUnrelatedEvents() {
        val root = graveyardState()
        var tree = GameplayTree(root)
        tree = assertNotNull(tree.play(a, command(GameAction.Play(PlayAction.SetTempPile(a, 2, true)), 500, root)).tree)
        tree = assertNotNull(tree.play(b, command(GameAction.Play(PlayAction.AdjustLife(b, "-1")), 501, root)).tree)
        val undone = assertNotNull(SelectiveUndo.change(tree, a, command(GameAction.Undo, 900, root)).tree)
        val state = undone.stateAt(undone.currentNodeId)!!
        assertNull(TempPiles.container(state.board, a, 2))
        assertEquals("19", state.board.players.single { it.accountId == b }.life)

        tree = assertNotNull(tree.play(b, command(GameAction.Play(toPile(10, pile(root, 2))), 502, root)).tree)
        assertNull(SelectiveUndo.change(tree, a, command(GameAction.Undo, 901, root)).tree, "a later move fills the pile")
    }

    @Test fun undoingTurnOffRestoresTheEmptyPile() {
        val root = graveyardState()
        var tree = GameplayTree(root)
        tree = assertNotNull(tree.play(a, command(GameAction.Play(PlayAction.SetTempPile(a, 3, true)), 600, root)).tree)
        val on = tree.stateAt(tree.currentNodeId)!!
        tree = assertNotNull(tree.play(a, command(GameAction.Play(PlayAction.SetTempPile(a, 3, false)), 601, root)).tree)
        assertNull(TempPiles.container(tree.stateAt(tree.currentNodeId)!!.board, a, 3))
        tree = assertNotNull(SelectiveUndo.change(tree, a, command(GameAction.Undo, 902, root)).tree)
        assertEquals(on.board, tree.stateAt(tree.currentNodeId)!!.board)
    }

    @Test fun oldPileOneSnapshotsAndEventsAreUnchanged() {
        val start = graveyardState()
        val next = accepted(start, toPile(10, pile(start, 1)), 100)
        val temp = TempPiles.container(next.state!!.board, a)!!
        assertEquals(1, temp.tempNumber)
        // Pile 1 is still encoded exactly as under rules v23..v28: no number on the wire.
        val encoded = GameWire.json.encodeToJsonElement(PlayContainer.serializer(), temp).jsonObject
        assertFalse("tempNumber" in encoded)
        val old = """{"id":"${temp.id}","zone":"LIBRARY","accountId":"$a","cards":["${gameplayId(10)}"],"layers":[{"id":"${temp.layers.single().id}","ordered":true,"cards":["${gameplayId(10)}"]}],"temp":true}"""
        assertEquals(temp, GameWire.json.decodeFromString(PlayContainer.serializer(), old))
        // The recorded container delta of a first move into pile 1 carries no number either.
        val created = next.mutation!!.transition.delta!!.entities.single { it.kind == "container" && it.id == temp.id }
        assertFalse(created.values.any { "tempNumber" in it.to.toString() })
    }
}
