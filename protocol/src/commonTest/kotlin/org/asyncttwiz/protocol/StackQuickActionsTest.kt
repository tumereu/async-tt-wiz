package org.asyncttwiz.protocol

import kotlin.test.*

class StackQuickActionsTest {
    private fun id(n: Int) = "00000000-0000-4000-8000-${n.toString().padStart(12, '0')}"
    private val owner = id(7)
    private val actor = id(8)
    private fun initial(projection: Boolean = false, token: Boolean = false, frozen: Boolean = false,
        occupied: Boolean = false): PlayState {
        val face = DefinitionFace(id(11), "Fable", rulesText = "First paragraph\n\nSecond paragraph\nThird paragraph")
        val definition = CardDefinition(face.name, listOf(face))
        val card = PlayCard(id(20), DeckJson.digest(definition), owner, if (projection) id(10) else id(12), face.faceId,
            covered = false, token = token, placement = if (projection) PlayPlacement(BoardPosition(8, 2), id(120)) else null)
        val other = card.copy(id = id(21), containerId = id(10), token = false, placement = PlayPlacement(BoardPosition(3, 1), id(121)))
        val cards = (if (frozen) emptyList() else listOf(card)) + if (occupied) listOf(other) else emptyList()
        return PlayState(id(1), id(2), id(3), id(4), PlayRoot(id(5), id(6)), id(5),
            BoardState(listOf(PlayPlayer(owner), PlayPlayer(actor)), cards,
                listOf(PlayContainer(id(10), PlayZone.BATTLEFIELD, owner, cards.filter { it.containerId == id(10) }.map { it.id }),
                    PlayContainer(id(12), PlayZone.STACK, cards = listOf(if (projection) id(30) else card.id)),
                    PlayContainer(id(13), PlayZone.GRAVEYARD, owner), PlayContainer(id(14), PlayZone.BATTLEFIELD, actor),
                    PlayContainer(id(15), PlayZone.GRAVEYARD, actor)), TurnState(actor),
                stackEffects = if (projection) listOf(StackEffect(id(30), card.id, if (frozen) card else null)) else emptyList()),
            listOf(SubmittedDefinition(card.definition, definition)), emptyList(), InitialStarter(StarterChoice(owner), owner))
    }
    private var request = 1000
    private fun command(state: PlayState, action: GameAction) = GameCommand(id(request++), GameScope(state.gameId, playId = state.id),
        action, id(9), "1", invocationId = id(500))
    private fun append(tree: GameplayTree, action: PlayAction): GameplayTree {
        val state = tree.stateAt(tree.currentNodeId)!!
        val cmd = command(state, GameAction.Play(action))
        assertEquals(cmd, GameWire.decodeClient(GameWire.json.encodeToString<GameClientMessage>(cmd)))
        val result = tree.play(actor, cmd)
        assertNull(result.reason)
        return result.tree ?: tree // An already-default corner reset is an ordinary no-op.
    }
    private fun undo(tree: GameplayTree): GameplayTree {
        val result = SelectiveUndo.change(tree, actor, command(tree.initial, GameAction.Undo))
        assertNull(result.reason)
        return result.tree!!
    }
    private fun resolve(initial: PlayState, zone: PlayZone): GameplayTree {
        var tree = GameplayTree(initial)
        val entry = CombatStackRules.stack(initial.board).cards.single()
        for (action in StackQuickActions.actions(initial, entry, zone)) tree = append(tree, action)
        assertTrue(CombatStackRules.stack(tree.stateAt(tree.currentNodeId)!!.board).cards.isEmpty())
        val final = tree
        while (tree.currentNodeId != tree.initial.root.id) tree = undo(tree)
        assertEquals(initial.board, tree.stateAt(tree.currentNodeId)!!.board)
        return final
    }
    @Test fun battlefieldUsesOwnersNearestMainRowAndAppendsOrUsesFirstSlot() {
        for (occupied in listOf(false, true)) {
            val initial = initial(occupied = occupied)
            val tree = resolve(initial, PlayZone.BATTLEFIELD)
            val card = tree.stateAt(tree.currentNodeId)!!.board.cards.single { it.id == id(20) }
            assertEquals(id(10), card.containerId) // opponent's battlefield, not the acting player's
            assertEquals(BoardPosition(if (occupied) 4 else 0, 1), card.placement!!.position)
            val swapped = initial.copy(board = initial.board.copy(players = initial.board.players.reversed()))
            assertEquals(StackQuickActions.actions(initial, id(20), PlayZone.BATTLEFIELD),
                StackQuickActions.actions(swapped, id(20), PlayZone.BATTLEFIELD))
        }
    }
    @Test fun battlefieldProjectionOnlyRemovesOccurrenceAndFrozenEffectOnlyRemovesItself() {
        for (frozen in listOf(false, true)) for (zone in listOf(PlayZone.BATTLEFIELD, PlayZone.GRAVEYARD)) {
            if (!frozen && zone == PlayZone.GRAVEYARD) continue
            val initial = initial(projection = true, token = frozen, frozen = frozen)
            val tree = resolve(initial, zone)
            assertEquals(initial.board.cards, tree.stateAt(tree.currentNodeId)!!.board.cards)
        }
    }
    @Test fun graveyardMovesRealCardsAndLiteralProjectionSourcesAndDisappearsTokens() {
        for (projection in listOf(false, true)) for (token in listOf(false, true)) {
            val initial = initial(projection, token)
            val tree = resolve(initial, PlayZone.GRAVEYARD)
            val card = tree.stateAt(tree.currentNodeId)!!.board.cards.find { it.id == id(20) }
            if (token) assertNull(card) else assertEquals(id(13), card!!.containerId)
        }
    }
    @Test fun projectionMoveDefaultsRemovalAndMarkAreOneReplayableUndoableMutation() {
        val base = initial(projection = true)
        val initial = base.copy(board = base.board.copy(cards = base.board.cards.map {
            it.copy(corner = "9/9", counters = mapOf(CounterColor.BLUE to "3")) }, stackTextMarks = mapOf(id(30) to 1)))
        val actions = StackQuickActions.actions(initial, id(30), PlayZone.GRAVEYARD)
        val action = assertIs<PlayAction.MoveCard>(actions.single())
        assertEquals(id(30), action.removeStackEntryId)
        val tree = append(GameplayTree(initial), action)
        assertEquals(1, tree.nodes.size)
        val state = tree.stateAt(tree.currentNodeId)!!
        assertTrue(state.board.stackEffects.isEmpty())
        assertTrue(state.board.stackTextMarks.isEmpty())
        val card = state.board.cards.single()
        assertEquals(id(13), card.containerId)
        assertTrue(card.counters.isEmpty())
        assertEquals(initial.definitions.single().definition.faces.single().corner.orEmpty(), card.corner)
        val mutation = tree.nodes.values.single()
        assertEquals(state, GameplayHistory.replay(initial, mutation).state)
        assertEquals(initial.board, undo(tree).stateAt(initial.root.id)!!.board)
    }
    @Test fun projectionMoveChecksTopIdentityAndSourceAndRejectsAnInvalidDestinationWithoutPartialRemoval() {
        val state = initial(projection = true)
        val action = assertIs<PlayAction.MoveCard>(StackQuickActions.actions(state, id(30), PlayZone.GRAVEYARD).single())
        fun execute(state: PlayState, action: PlayAction.MoveCard) = GameplayRules.change(state, actor, command(state, GameAction.Play(action)))
        val buried = state.copy(board = state.board.copy(stackEffects = state.board.stackEffects + StackEffect(id(31), id(20)),
            containers = state.board.containers.map { if (it.zone == PlayZone.STACK) it.copy(cards = listOf(id(31), id(30))) else it }))
        assertEquals("stackChanged", execute(buried, action).reason)
        assertEquals("movementConflict", execute(state, action.copy(source = MoveSource(id(13)))).reason)
        val invalid = execute(state, action.copy(destination = MoveDestination(id(13), MoveInsertion.EMPTY, BoardPosition(1, 1))))
        assertNotNull(invalid.reason)
        assertNull(invalid.state); assertNull(invalid.mutation)
        assertEquals("noOp", execute(state, action.copy(destination = MoveDestination(id(12), MoveInsertion.TOP))).outcome)
        val sameZone = execute(state, action.copy(destination = MoveDestination(id(14), MoveInsertion.EMPTY, BoardPosition(4, 4))))
        assertNull(sameZone.reason)
        assertEquals(state.board.cards, sameZone.state!!.board.cards)
        assertTrue(sameZone.state.board.stackEffects.isEmpty())
    }
    @Test fun markerSetClearNoOpAndLeavingCleanupAreSharedReplayableAndUndoable() {
        for (projection in listOf(false, true)) {
            val initial = initial(projection)
            val entry = CombatStackRules.stack(initial.board).cards.single()
            var tree = append(GameplayTree(initial), PlayAction.Stack(StackAction.MarkText(entry, 1)))
            val marked = tree.stateAt(tree.currentNodeId)!!
            assertEquals(mapOf(entry to 1), marked.board.stackTextMarks)
            assertEquals(GameplayRules.stackDigest(initial), GameplayRules.stackDigest(marked))
            assertEquals("noOp", GameplayRules.change(marked, owner,
                command(marked, GameAction.Play(PlayAction.Stack(StackAction.MarkText(entry, 1))))).outcome)
            assertEquals("invalidInput", GameplayRules.change(marked, owner,
                command(marked, GameAction.Play(PlayAction.Stack(StackAction.MarkText(entry, 3))))).reason)
            tree = append(tree, PlayAction.Stack(StackAction.MarkText(entry, null)))
            assertTrue(tree.stateAt(tree.currentNodeId)!!.board.stackTextMarks.isEmpty())
            tree = undo(tree)
            assertEquals(mapOf(entry to 1), tree.stateAt(tree.currentNodeId)!!.board.stackTextMarks)
            val beforeExit = tree.stateAt(tree.currentNodeId)!!
            for (action in StackQuickActions.actions(beforeExit, entry, PlayZone.BATTLEFIELD)) tree = append(tree, action)
            assertTrue(tree.stateAt(tree.currentNodeId)!!.board.stackTextMarks.isEmpty())
            tree = undo(tree)
            assertEquals(mapOf(entry to 1), tree.stateAt(tree.currentNodeId)!!.board.stackTextMarks)
            tree = undo(tree)
            assertTrue(tree.stateAt(tree.currentNodeId)!!.board.stackTextMarks.isEmpty())
        }
    }
    @Test fun definitionOptionsAreTitlesThenLiteralParagraphsForEveryMultipartLayout() {
        for (layout in listOf("split", "adventure", "modal_dfc", "transform", "flip")) {
            val definition = CardDefinition("Both", listOf(
                DefinitionFace(id(11), "First", rulesText = "One\n\nTwo"),
                DefinitionFace(id(12), "Second", rulesText = "Three\r\nFour")), layout = layout)
            assertEquals(listOf("First", "Second", "One", "Two", "Three", "Four"), StackText.options(definition, id(12)).map { it.text })
            assertEquals(listOf("Three", "Four"), StackText.options(definition.copy(faces = listOf(definition.faces[1])), id(12)).map { it.text })
        }
    }
    @Test fun projectionMarkSurvivesSourceMovesAndTokenDisappearanceUntilThatOccurrenceLeaves() {
        for (token in listOf(false, true)) {
            var tree = append(GameplayTree(initial(projection = true, token = token)), PlayAction.Stack(StackAction.MarkText(id(30), 2)))
            val before = tree.stateAt(tree.currentNodeId)!!
            val source = before.board.cards.single()
            tree = append(tree, PlayAction.MoveCard(source.id, MoveSource(source.containerId, source.placement),
                MoveDestination(id(13), MoveInsertion.UNORDERED)))
            val moved = tree.stateAt(tree.currentNodeId)!!
            assertEquals(mapOf(id(30) to 2), moved.board.stackTextMarks)
            assertEquals(GameplayRules.stackDigest(before), GameplayRules.stackDigest(moved))
            assertEquals(token, moved.board.stackEffects.single().frozen != null)
            tree = append(tree, PlayAction.Stack(StackAction.RemoveEffect(id(30))))
            assertTrue(tree.stateAt(tree.currentNodeId)!!.board.stackTextMarks.isEmpty())
            tree = undo(tree)
            assertEquals(mapOf(id(30) to 2), tree.stateAt(tree.currentNodeId)!!.board.stackTextMarks)
        }
    }
}
