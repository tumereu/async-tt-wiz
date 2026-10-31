package org.asyncttwiz.protocol

import kotlin.test.*

/** Rules v23: public face-down Battlefield corner and the lazily-created per-player Temp pile. */
class R23RulesTest {
    private val a = gameplayId(7)
    private val b = gameplayId(8)
    private val library = gameplayId(20)
    private val hand = gameplayId(21)
    private val field = gameplayId(22)
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

    private fun card(state: PlayState, n: Int) = state.board.cards.single { it.id == gameplayId(n) }

    @Test fun rulesVersionIsAtLeast23() = assertTrue(CURRENT_GAMEPLAY_RULES_VERSION >= 23)

    // TBL-029
    @Test fun coveringABattlefieldCardMakesItAPublic22AndUncoveringRestoresPrinted() {
        val start = gameplayFixture().withBattlefieldCards(gameplayId(10))
        val covered = accepted(start, PlayAction.SetCovered(gameplayId(10), true), 100).state!!
        val down = card(covered, 10)
        assertTrue(down.isFaceDown)
        assertEquals("2/2", down.corner)
        assertEquals(mapOf(CounterColor.RED to "2"), down.counters)
        // Counters, notes and edits on the face-down card are ordinary Battlefield edits.
        val noted = accepted(accepted(covered, PlayAction.AdjustCorner(gameplayId(10), CornerPart.BOTH, 1), 101).state!!,
            PlayAction.SetNote(gameplayId(10), "morph"), 102).state!!
        assertEquals("3/3", card(noted, 10).corner)
        val up = accepted(noted, PlayAction.SetCovered(gameplayId(10), false), 103).state!!
        assertFalse(card(up, 10).isFaceDown)
        assertEquals("3/5", card(up, 10).corner)
        assertEquals("morph", card(up, 10).note)
        assertEquals(mapOf(CounterColor.RED to "2"), card(up, 10).counters)
    }

    @Test fun coveringOutsideTheBattlefieldKeepsTheCorner() {
        val start = gameplayFixture()
        val graveyardCard = accepted(start, PlayAction.MoveCard(gameplayId(10), MoveSource(library),
            MoveDestination(graveyard, MoveInsertion.UNORDERED)), 100).state!!
        val before = card(graveyardCard, 10).corner
        val covered = gameplayChange(graveyardCard, PlayAction.SetCovered(gameplayId(10), true), 101).state!!
        assertEquals(before, card(covered, 10).corner)
    }

    // TBL-029
    @Test fun enteringTheBattlefieldFaceDownIs22AndLeavingResetsToPrinted() {
        val start = gameplayFixture()
        val inHand = accepted(start, PlayAction.MoveCard(gameplayId(10), MoveSource(library, edge = MoveSourceEdge.TOP),
            MoveDestination(hand, MoveInsertion.UNORDERED)), 100).state!!
        val played = accepted(inHand, PlayAction.MoveCard(gameplayId(10), MoveSource(hand),
            MoveDestination(field, MoveInsertion.EMPTY, BoardPosition(0, 1)), faceDown = true), 101).state!!
        assertTrue(card(played, 10).isFaceDown)
        assertEquals("2/2", card(played, 10).corner)
        val placement = card(played, 10).placement
        // Moving within the Battlefield keeps the public corner; an explicit face-up move resets it.
        val moved = accepted(played, PlayAction.MoveCard(gameplayId(10), MoveSource(field, placement),
            MoveDestination(field, MoveInsertion.EMPTY, BoardPosition(2, 1))), 102).state!!
        assertEquals("2/2", card(moved, 10).corner)
        val shown = accepted(moved, PlayAction.MoveCard(gameplayId(10), MoveSource(field, card(moved, 10).placement),
            MoveDestination(field, MoveInsertion.EMPTY, BoardPosition(3, 1)), faceDown = false), 103).state!!
        assertEquals("3/5", card(shown, 10).corner)
        val dead = accepted(moved, PlayAction.MoveCard(gameplayId(10), MoveSource(field, card(moved, 10).placement),
            MoveDestination(graveyard, MoveInsertion.UNORDERED)), 104).state!!
        assertEquals("3/5", card(dead, 10).corner)
    }

    @Test fun undoingTheCoverIsBlockedByALaterEditOfThePublicCorner() {
        val root = gameplayFixture().withBattlefieldCards(gameplayId(10))
        var tree = GameplayTree(root)
        tree = assertNotNull(tree.play(a, command(GameAction.Play(PlayAction.SetCovered(gameplayId(10), true)), 100, root)).tree)
        tree = assertNotNull(tree.play(b, command(GameAction.Play(PlayAction.AdjustCorner(gameplayId(10), CornerPart.BOTH, 1)), 101, root)).tree)
        assertEquals("3/3", card(tree.stateAt(tree.currentNodeId)!!, 10).corner)
        val undo = SelectiveUndo.change(tree, a, command(GameAction.Undo, 900, root))
        assertNull(undo.tree, "covering assigned the corner the later edit depends on")
        // Without the dependent edit the same undo restores the printed corner exactly.
        val alone = assertNotNull(GameplayTree(root).play(a, command(GameAction.Play(PlayAction.SetCovered(gameplayId(10), true)), 100, root)).tree)
        val undone = assertNotNull(SelectiveUndo.change(alone, a, command(GameAction.Undo, 900, root)).tree)
        assertEquals(root.board, undone.stateAt(undone.currentNodeId)!!.board)
    }

    private fun tempId(state: PlayState, account: String = a) = TempPiles.containerId(state.id, account)

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

    // ZON-039, ZON-042
    @Test fun firstMoveIntoTempCreatesTheHiddenPileAndItsInverseRemovesIt() {
        val start = graveyardState()
        assertNull(TempPiles.container(start.board, a))
        val change = accepted(start, PlayAction.MoveCard(gameplayId(10), MoveSource(graveyard),
            MoveDestination(tempId(start), MoveInsertion.TOP)), 100)
        val next = change.state!!
        val temp = assertNotNull(TempPiles.container(next.board, a))
        assertTrue(temp.temp)
        assertEquals(PlayZone.LIBRARY, temp.zone)
        assertEquals(listOf(gameplayId(10)), temp.cards)
        assertEquals(library, next.board.zoneContainer(a, PlayZone.LIBRARY)!!.id)
        val moved = card(next, 10)
        assertTrue(moved.covered)
        assertEquals("", moved.note)
        assertFalse(CardVisibility.isVisibleFaceUp(next.board, moved, a))
        assertFalse(CardVisibility.isVisibleFaceUp(next.board, moved, b))
        // The second card joins the existing pile; the opponent's pile is independent.
        val second = accepted(next, PlayAction.MoveCard(gameplayId(11), MoveSource(graveyard),
            MoveDestination(tempId(start), MoveInsertion.TOP)), 101).state!!
        assertEquals(listOf(gameplayId(11), gameplayId(10)), TempPiles.container(second.board, a)!!.cards)
        assertNull(TempPiles.container(second.board, b))
        assertNotEquals(tempId(start, a), tempId(start, b))
    }

    @Test fun battlefieldCardEnteringTempResetsLikeAnyHiddenZone() {
        val start = gameplayFixture().withBattlefieldCards(gameplayId(10))
        val covered = accepted(start, PlayAction.SetCovered(gameplayId(10), true), 99).state!!
        val next = accepted(covered, PlayAction.MoveCard(gameplayId(10), MoveSource(field, card(covered, 10).placement),
            MoveDestination(tempId(start), MoveInsertion.TOP)), 100).state!!
        val moved = card(next, 10)
        assertEquals("3/5", moved.corner)
        assertTrue(moved.counters.isEmpty())
        assertEquals("", moved.note)
        assertFalse(moved.tapped)
        assertFalse(moved.explicitlyCovered)
        assertNull(moved.placement)
    }

    @Test fun graveyardToTempShuffleAndAllToLibraryBottomIsUndoable() {
        val root = graveyardState()
        var tree = GameplayTree(root)
        val board = TempPiles.ensure(root.board, root.id, a)
        val toTemp = LibraryActions.moveCaptured(board, listOf(gameplayId(10), gameplayId(11), gameplayId(12)),
            MoveDestination(tempId(root), MoveInsertion.TOP))
        tree = assertNotNull(HistoryContinuation.change(tree, a, command(GameAction.PlayBatch(toTemp), 400, root)).tree)
        var state = tree.stateAt(tree.currentNodeId)!!
        assertEquals(setOf(gameplayId(10), gameplayId(11), gameplayId(12)), TempPiles.container(state.board, a)!!.cards.toSet())
        assertTrue(state.board.containers.single { it.id == graveyard }.cards.isEmpty())
        val seed = RandomV1.encodeSeed(ByteArray(32) { 3 })
        tree = assertNotNull(tree.play(a, command(GameAction.Play(PlayAction.ShuffleLibrary(tempId(root), seed)), 401, root)).tree)
        state = tree.stateAt(tree.currentNodeId)!!
        val shuffled = TempPiles.container(state.board, a)!!
        assertTrue(shuffled.knownTop.isEmpty() && shuffled.knownBottom.isEmpty())
        val all = TempPiles.allToLibrary(state.board, shuffled.id, bottom = true)
        assertEquals(3, all.size)
        tree = assertNotNull(HistoryContinuation.change(tree, a, command(GameAction.PlayBatch(all), 402, root)).tree)
        state = tree.stateAt(tree.currentNodeId)!!
        val lib = state.board.containers.single { it.id == library }
        assertEquals(shuffled.cards, lib.cards.takeLast(3))
        assertTrue(TempPiles.container(state.board, a)!!.cards.isEmpty())
        // Each batch is one Undo; the first one also removes the lazily-created pile.
        repeat(3) { n -> tree = assertNotNull(SelectiveUndo.change(tree, a, command(GameAction.Undo, 900 + n, root)).tree, "undo $n") }
        assertEquals(root.board, tree.stateAt(tree.currentNodeId)!!.board)
    }

    @Test fun opponentUndoAroundTempCreationKeepsUnrelatedLaterEvents() {
        val root = graveyardState()
        var tree = GameplayTree(root)
        tree = assertNotNull(tree.play(a, command(GameAction.Play(PlayAction.MoveCard(gameplayId(10), MoveSource(graveyard),
            MoveDestination(tempId(root), MoveInsertion.TOP))), 500, root)).tree)
        tree = assertNotNull(tree.play(b, command(GameAction.Play(PlayAction.AdjustLife(b, "-1")), 501, root)).tree)
        val undone = assertNotNull(SelectiveUndo.change(tree, a, command(GameAction.Undo, 902, root)).tree)
        val state = undone.stateAt(undone.currentNodeId)!!
        assertNull(TempPiles.container(state.board, a))
        assertEquals("19", state.board.players.single { it.accountId == b }.life)
        assertEquals(root.board.cards, state.board.cards)
    }

    @Test fun tempMovesClassifyAsCrossZoneAndHandRandomStillFindsTheLibrary() {
        val start = graveyardState()
        val change = accepted(start, PlayAction.MoveCard(gameplayId(10), MoveSource(graveyard),
            MoveDestination(tempId(start), MoveInsertion.TOP)), 100)
        val next = change.state!!
        val fromTemp = PlayAction.MoveCard(gameplayId(10), MoveSource(tempId(start), edge = MoveSourceEdge.TOP),
            MoveDestination(library, MoveInsertion.TOP))
        assertEquals(HistorySignificance.CROSS_ZONE_MOVE, HistoryGrouping.classify(fromTemp, next.board.containers).significance)
        assertEquals(HistorySignificance.CROSS_ZONE_MOVE, change.mutation!!.grouping!!.significance)
        val inHand = accepted(next, PlayAction.MoveCard(gameplayId(13), MoveSource(library, edge = MoveSourceEdge.TOP),
            MoveDestination(hand, MoveInsertion.UNORDERED)), 101).state!!
        val random = HandActions.random(inHand.board, hand, emptyList(), RandomV1.encodeSeed(ByteArray(32)), HandRandomResult.LIBRARY_BOTTOM)
        val bottom = accepted(inHand, random, 102).state!!
        assertEquals(gameplayId(13), bottom.board.containers.single { it.id == library }.cards.last())
    }

    // ZON-042
    @Test fun tokensAreNotPutIntoAMissingTempPile() {
        val start = gameplayFixture()
        val token = accepted(start, PlayAction.CreateToken(TokenCatalog.blank), 100).state!!
        val id = token.board.cards.single { it.token }
        val change = gameplayChange(token, PlayAction.MoveCard(id.id, MoveSource(id.containerId, id.placement),
            MoveDestination(tempId(start), MoveInsertion.TOP)), 101)
        assertEquals("missingReference", change.reason)
    }
}
