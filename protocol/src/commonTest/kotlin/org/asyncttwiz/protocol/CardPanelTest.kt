package org.asyncttwiz.protocol

import kotlin.test.*

class CardPanelTest {
    private fun id(n: Int) = "00000000-0000-4000-8000-${n.toString().padStart(12, '0')}"
    private val a = id(7)
    private val b = id(8)
    private val seed = RandomV1.encodeSeed(ByteArray(32) { it.toByte() })
    private var request = 100
    private fun root(): PlayState {
        val definition = CardDefinition("Wizard", listOf(DefinitionFace(id(40), "Wizard", corner = "2/2")))
        val cards = (20..24).map { PlayCard(id(it), "definition", a, id(10), id(40)) }
        return PlayState(id(1), id(2), id(3), id(4), PlayRoot(id(5), id(6)), id(5),
            BoardState(listOf(PlayPlayer(a), PlayPlayer(b)), cards,
                listOf(PlayContainer(id(10), PlayZone.LIBRARY, a, cards.map { it.id }), PlayContainer(id(11), PlayZone.HAND, a),
                    PlayContainer(id(12), PlayZone.GRAVEYARD, a), PlayContainer(id(13), PlayZone.EXILE, a),
                    PlayContainer(id(14), PlayZone.BATTLEFIELD, a), PlayContainer(id(15), PlayZone.LIBRARY, b)), TurnState(a)),
            listOf(SubmittedDefinition("definition", definition)), emptyList(), InitialStarter(StarterChoice(a), a))
    }
    private fun change(state: PlayState, action: PlayAction, actor: String = a) = GameplayRules.change(state, actor,
        GameCommand(id(request++), GameScope(state.gameId, playId = state.id), GameAction.Play(action), id(90), "1"))
    private fun step(state: PlayState, action: PlayAction, actor: String = a): PlayState {
        val result = change(state, action, actor)
        assertEquals("accepted", result.outcome, "$action: ${result.reason}")
        val mutation = assertNotNull(result.mutation)
        val next = assertNotNull(result.state)
        val wire = GameWire.json.decodeFromString(GameplayMutation.serializer(), GameWire.json.encodeToString(GameplayMutation.serializer(), mutation))
        assertEquals(next, GameplayHistory.apply(state, wire), action.toString())
        assertEquals(state, GameplayHistory.unapply(next, wire), action.toString())
        assertEquals(result, GameplayHistory.replay(state, wire))
        return next
    }
    @Test fun undoThatWouldRestoreAPanelUnderTheOpponentsLaterPanelIsRefused() {
        fun play(n: Int, action: PlayAction) = GameCommand(id(n), GameScope(id(1), playId = id(2)), GameAction.Play(action), id(90), "1")
        var tree = assertNotNull(GameplayTree(root()).play(a, play(200, PlayAction.ShowContents(id(10), ContentsSelection.TOP, 1))).tree)
        val top = tree.stateAt(tree.currentNodeId)!!.board.contentsDisplays.single().cardIds.single()
        tree = assertNotNull(tree.play(a, play(201, PlayAction.MoveCard(top, MoveSource(id(10)),
            MoveDestination(id(11), MoveInsertion.UNORDERED)))).tree)
        assertTrue(tree.stateAt(tree.currentNodeId)!!.board.contentsDisplays.isEmpty())
        tree = assertNotNull(tree.play(b, play(202, PlayAction.ShowContents(id(10), ContentsSelection.TOP, 1))).tree)
        val undo = SelectiveUndo.change(tree, a, GameCommand(id(203), GameScope(id(1), playId = id(2)), GameAction.Undo, id(90), "1"))
        assertNull(undo.tree)
        assertEquals("replayBlocked", undo.reason)
    }
    @Test fun privatePanelRevealsOneCardThenMovesInASeparateGroupAndKnowledgeExpires() {
        val opened = step(root(), PlayAction.ShowContents(id(10), ContentsSelection.TOP, 3))
        val display = opened.board.contentsDisplays.single()
        assertTrue(display.cardIds.all { display.faceVisibleTo(it, a) })
        assertTrue(display.cardIds.none { display.faceVisibleTo(it, b) })
        val card = opened.board.cards.first()
        assertFalse(CardVisibility.isVisibleFaceUp(opened.board, card, b))
        val revealed = step(opened, PlayAction.RevealContentsCard(display.id, card.id))
        assertNotEquals(opened.currentGroup!!.id, revealed.currentGroup!!.id)
        assertTrue(CardVisibility.isVisibleFaceUp(revealed.board, revealed.board.cards.first(), b))
        assertEquals(setOf(card.id), revealed.board.contentsDisplays.single().revealedIds)
        val moved = step(revealed, PlayAction.MoveCard(card.id, MoveSource(id(10)), MoveDestination(id(11), MoveInsertion.UNORDERED)))
        assertNotEquals(revealed.currentGroup.id, moved.currentGroup!!.id)
        assertEquals(display.cardIds.drop(1), moved.board.contentsDisplays.single().cardIds)
        assertFalse(moved.board.cards.first().knownToAll)
        assertFalse(CardVisibility.isVisibleFaceUp(moved.board, moved.board.cards.first(), b))
        val returned = step(moved, PlayAction.MoveCard(card.id, MoveSource(id(11)), MoveDestination(id(10), MoveInsertion.TOP)))
        assertFalse(returned.board.cards.first().knownToAll)
        assertFalse(CardVisibility.isVisibleFaceUp(returned.board, returned.board.cards.first(), b))
    }
    @Test fun searchOpenMovesAndCloseShuffleHaveOneGroupAndNoRevealedKnowledgeSurvivesShuffle() {
        val opened = step(root(), PlayAction.ShowContents(id(10), ContentsSelection.ALL))
        val display = opened.board.contentsDisplays.single()
        val moved = step(opened, PlayAction.MoveCard(id(20), MoveSource(id(10)), MoveDestination(id(11), MoveInsertion.UNORDERED)))
        val closed = step(moved, PlayAction.DismissContents(display.id, seed = seed))
        assertEquals(opened.currentGroup!!.id, moved.currentGroup!!.id)
        assertEquals(opened.currentGroup.id, closed.currentGroup!!.id)
        assertTrue(closed.board.contentsDisplays.isEmpty())
        assertEquals(RandomV1.shuffle(moved.board.containers.first().cards, seed), closed.board.containers.first().cards)
        val reveal = step(root(), PlayAction.ShowContents(id(10), ContentsSelection.TOP, 2, reveal = true))
        val shuffled = step(reveal, PlayAction.DismissContents(reveal.board.contentsDisplays.single().id, ContentsRest.SHUFFLE, seed))
        assertTrue(shuffled.board.cards.none { it.knownToAll })
    }
    @Test fun searchBattlefieldEntryDefaultsJoinTheSameGroupAndReorderDoesNotDisclose() {
        val opened = step(root(), PlayAction.ShowContents(id(10), ContentsSelection.ALL))
        val panel = opened.board.contentsDisplays.single()
        val moved = step(opened, PlayAction.MoveCard(id(20), MoveSource(id(10)),
            MoveDestination(id(14), MoveInsertion.EMPTY, BoardPosition(0, 0))))
        val tapped = step(moved, PlayAction.SetTapped(id(20), true))
        val closed = step(tapped, PlayAction.DismissContents(panel.id, seed = seed))
        assertEquals(opened.currentGroup!!.id, tapped.currentGroup!!.id)
        assertEquals(opened.currentGroup.id, closed.currentGroup!!.id)
        val look = step(root(), PlayAction.ShowContents(id(10), ContentsSelection.TOP, 2))
        val reordered = change(look, PlayAction.ReorderContents(look.board.contentsDisplays.single().id, id(21), id(20)))
        assertEquals("accepted", reordered.outcome)
        assertFalse(reordered.mutation!!.revealsInformation)
        assertTrue(reordered.mutation.transition.disclosures.isEmpty())
    }
    @Test fun revealTopExpiresEvenForASameContainerMoveAndAutoClosesTheLastCard() {
        val opened = step(root(), PlayAction.ShowContents(id(10), ContentsSelection.TOP, 1))
        val panel = opened.board.contentsDisplays.single()
        val revealed = step(opened, PlayAction.RevealContentsCard(panel.id, id(20)))
        val placed = step(revealed, PlayAction.MoveCard(id(20), MoveSource(id(10)), MoveDestination(id(10), MoveInsertion.TOP)))
        assertTrue(placed.board.contentsDisplays.isEmpty())
        assertFalse(placed.board.cards.first().knownToAll)
        assertFalse(CardVisibility.isVisibleFaceUp(placed.board, placed.board.cards.first(), b))
        assertEquals("noOp", change(placed, PlayAction.DismissContents(panel.id)).outcome)
        val drawn = step(placed, PlayAction.MoveCard(id(20), MoveSource(id(10)), MoveDestination(id(11), MoveInsertion.UNORDERED)))
        assertFalse(drawn.board.cards.first().knownToAll)
        assertFalse(CardVisibility.isVisibleFaceUp(drawn.board, drawn.board.cards.first(), b))
    }
    @Test fun everyPanelAutoClosesInTheLastMoveAndAllowsAnImmediateNewPanel() {
        for ((selection, reveal) in listOf(ContentsSelection.TOP to false, ContentsSelection.TOP to true, ContentsSelection.ALL to false)) {
            var state = step(root(), PlayAction.ShowContents(id(10), selection, 2, reveal))
            val ids = state.board.contentsDisplays.single().cardIds
            for ((index, card) in ids.withIndex()) {
                val before = state
                val result = change(before, PlayAction.MoveCard(card, MoveSource(id(10)), MoveDestination(id(11), MoveInsertion.UNORDERED)))
                state = assertNotNull(result.state)
                val event = assertNotNull(result.mutation)
                assertEquals(2, event.transition.contentsRulesVersion)
                assertEquals(6, event.transition.movement!!.coverRulesVersion)
                assertEquals(before, GameplayHistory.unapply(state, event))
                assertEquals(state, GameplayHistory.apply(before, event))
                assertFalse(CardVisibility.isVisibleFaceUp(state.board, state.board.cards.single { it.id == card }, b))
                assertEquals(index == ids.lastIndex, state.board.contentsDisplays.isEmpty())
                if (index == ids.lastIndex) assertNull(event.transition.contents!!.to)
            }
            step(state, PlayAction.ShowContents(id(10), ContentsSelection.TOP))
        }
    }
    @Test fun revealedRestInEveryHiddenLibraryPositionExpiresAndUndoRestoresTheWindow() {
        for (reveal in listOf(false, true)) for (rest in listOf(ContentsRest.TOP, ContentsRest.BOTTOM, ContentsRest.BOTTOM_RANDOM, ContentsRest.SHUFFLE)) {
            val opened = step(root(), PlayAction.ShowContents(id(10), ContentsSelection.TOP, 2, reveal))
            val panel = opened.board.contentsDisplays.single()
            val visible = if (reveal) opened else step(opened, PlayAction.RevealContentsCard(panel.id, panel.cardIds.first()))
            val card = visible.board.cards.first()
            assertTrue(CardVisibility.isVisibleFaceUp(visible.board, card, b))
            val closed = step(visible, PlayAction.DismissContents(panel.id, rest, seed))
            assertFalse(CardVisibility.isVisibleFaceUp(closed.board, closed.board.cards.first(), b))
            assertTrue(closed.board.cards.none { it.knownToAll })
        }
    }
    @Test fun legacyContentsEffectsRetainPersistentKnowledgeAndAnEmptyPanelOnRecordedTraversal() {
        fun command(state: PlayState, action: PlayAction) = GameCommand(id(request++),
            GameScope(state.gameId, playId = state.id), GameAction.Play(action), id(90), "1")
        fun retained(before: PlayState, raw: GameplayChange): PlayState {
            val next = assertNotNull(raw.state)
            val event = assertNotNull(raw.mutation).let { it.copy(schemaVersion = 2,
                transition = it.transition.copy(delta = GameplayDeltas.capture(before, next, it.id)),
                bookkeeping = GameplayBookkeeping(before.currentGroup, before.currentBranchId, next.currentGroup)) }
            val sealed = event.copy(transition = event.transition.copy(delta = event.transition.delta!!.copy(seal = GameplayDeltas.seal(event))))
            val encoded = GameWire.json.encodeToString(GameplayMutation.serializer(), sealed)
            assertFalse(encoded.contains("contentsRulesVersion"))
            val wire = GameWire.json.decodeFromString(GameplayMutation.serializer(), encoded)
            assertEquals(next, GameplayHistory.apply(before, wire))
            assertEquals(before, GameplayHistory.unapply(next, wire))
            assertEquals(next, GameplayHistory.replay(before, wire).state)
            return next
        }
        val root = root()
        val show = PlayAction.ShowContents(id(10), ContentsSelection.TOP, 1, reveal = true)
        val opened = retained(root, LibraryRules.change(root, a, command(root, show), show, contentsRulesVersion = 1))
        assertEquals(2, opened.board.contentsDisplays.single().panelVersion)
        val close = PlayAction.DismissContents(opened.board.contentsDisplays.single().id)
        val closed = retained(opened, LibraryRules.change(opened, a, command(opened, close), close, contentsRulesVersion = 1))
        assertTrue(CardVisibility.isVisibleFaceUp(closed.board, closed.board.cards.first(), b))
        val card = opened.board.cards.first()
        val move = PlayAction.MoveCard(card.id, MoveSource(id(10)), MoveDestination(id(11), MoveInsertion.UNORDERED))
        val board = opened.board.copy(cards = opened.board.cards.map { if (it.id == card.id) it.copy(containerId = id(11)) else it },
            containers = opened.board.containers.map { when (it.id) {
                id(10) -> it.copy(cards = it.cards - card.id)
                id(11) -> it.copy(cards = listOf(card.id))
                else -> it
            } })
        val moved = retained(opened, GameplayRules.record(opened, a, command(opened, move), board,
            GameplayTransition(movement = GameplayMoveChange(card.id, GameplayRules.location(opened.board, card.id, true),
                GameplayRules.location(board, card.id, true), coverRulesVersion = 4)), contentsRulesVersion = 1))
        assertTrue(moved.board.contentsDisplays.single().cardIds.isEmpty())
        assertTrue(CardVisibility.isVisibleFaceUp(moved.board, moved.board.cards.first(), b))
    }
    @Test fun restResolvesInPanelOrderForEveryDestinationAndReordersAreReversible() {
        for (resolution in ContentsRest.entries) {
            val opened = step(root(), PlayAction.ShowContents(id(10), ContentsSelection.TOP, 3, reveal = true))
            val display = opened.board.contentsDisplays.single()
            assertTrue(display.cardIds.all { display.faceVisibleTo(it, b) })
            val reordered = step(opened, PlayAction.ReorderContents(display.id, id(22), id(20)))
            val order = listOf(22, 20, 21).map(::id)
            assertEquals(order, reordered.board.contentsDisplays.single().cardIds)
            val closed = step(reordered, PlayAction.DismissContents(display.id, resolution, seed))
            val untouched = listOf(23, 24).map(::id)
            val expected = when (resolution) {
                ContentsRest.TOP -> order + untouched
                ContentsRest.BOTTOM -> untouched + order
                ContentsRest.BOTTOM_RANDOM -> untouched + RandomV1.shuffle(order, seed)
                ContentsRest.GRAVEYARD, ContentsRest.EXILE -> untouched
                ContentsRest.SHUFFLE -> RandomV1.shuffle((20..24).map(::id), seed)
            }
            assertEquals(expected, closed.board.containers.first().cards)
            if (resolution == ContentsRest.GRAVEYARD || resolution == ContentsRest.EXILE) {
                val zone = if (resolution == ContentsRest.EXILE) PlayZone.EXILE else PlayZone.GRAVEYARD
                assertEquals(order.toSet(), closed.board.containers.single { it.zone == zone }.cards.toSet())
                assertTrue(closed.board.cards.filter { it.id in order }.all { !it.covered && !it.knownToAll })
                assertTrue(closed.board.cards.filter { it.id in order }.all { it.containerId == closed.board.containers.single { it.zone == zone }.id })
            }
        }
    }
    @Test fun wholeGameHasOnePanelAndOnlyTheAuthorMayChangeItAndLegacyPanelsCanClose() {
        val opened = step(root(), PlayAction.ShowContents(id(10), ContentsSelection.TOP, 2))
        val display = opened.board.contentsDisplays.single()
        for (selection in ContentsSelection.entries) assertEquals("panelAlreadyOpen",
            change(opened, PlayAction.ShowContents(id(10), selection)).reason)
        for (action in listOf(PlayAction.DismissContents(display.id), PlayAction.RevealContentsCard(display.id, id(20)),
            PlayAction.ReorderContents(display.id, id(20)),
            PlayAction.MoveCard(id(20), MoveSource(id(10)), MoveDestination(id(11), MoveInsertion.UNORDERED))))
            assertEquals("accessDenied", change(opened, action, b).reason)
        assertEquals("panelAlreadyOpen", change(opened, PlayAction.ShowContents(id(15), ContentsSelection.TOP), b).reason)
        val legacy = opened.copy(board = opened.board.copy(contentsDisplays = listOf(display,
            display.copy(id = id(99), author = b, containerId = id(15), cardIds = emptyList()))))
        val remaining = step(legacy, PlayAction.DismissContents(display.id))
        assertEquals(1, remaining.board.contentsDisplays.size)
        assertTrue(step(remaining, PlayAction.DismissContents(id(99)), b).board.contentsDisplays.isEmpty())
        assertFalse(GameplayRules.valid(PlayAction.ReorderContents("bad", id(20))))
        assertFalse(GameplayRules.valid(PlayAction.RevealContentsCard(display.id, "bad")))
        assertFalse(GameplayRules.valid(PlayAction.DismissContents(display.id, seed = "bad")))
    }
}
